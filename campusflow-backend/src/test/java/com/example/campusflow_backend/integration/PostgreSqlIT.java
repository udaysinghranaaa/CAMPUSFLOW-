package com.example.campusflow_backend.integration;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.repository.*;
import com.example.campusflow_backend.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.postgresql.util.PSQLException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicitly run with -Ppostgres-it. Uses actual DB_* configuration and rolls back test rows. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@org.springframework.security.test.context.support.WithMockUser(roles="ADMIN")
class PostgreSqlIT extends com.example.campusflow_backend.support.SecurityIntegrationSupport {
    @Autowired private DepartmentRepository departments;
    @Autowired private FacultyRepository faculty;
    @Autowired private DepartmentService departmentService;
    @Autowired private FacultyService facultyService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @PersistenceContext private EntityManager entityManager;

    private String unique() { return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(); }

    private Department department() {
        return departments.saveAndFlush(new Department("Integration Department", unique(), null));
    }

    private Faculty member(Department department) {
        String token = unique();
        return faculty.saveAndFlush(new Faculty("Integration Asha", token + "@example.edu", token, "Professor", department));
    }

    @Test
    void flywayAndHibernateUseRealPostgres() {
        assertThat(jdbc.queryForObject("select current_database()", String.class))
                .isEqualTo(System.getenv().getOrDefault("DB_NAME", "campusflow_db"));
        assertThat(jdbc.queryForObject("select version()", String.class)).contains("PostgreSQL");
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '1' and success", Long.class))
                .isEqualTo(1L);
        Department saved = department();
        assertThat(saved.getId()).isPositive();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void departmentCodeIsUniqueInDatabase() {
        Department saved = department();
        assertThatThrownBy(() -> departments.saveAndFlush(new Department("Duplicate", saved.getCode(), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void facultyEmailIsUniqueInDatabase() {
        Department department = department();
        Faculty saved = member(department);
        assertThatThrownBy(() -> faculty.saveAndFlush(new Faculty("Duplicate", saved.getEmail(), unique(), null, department)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void employeeCodeIsUniqueInDatabase() {
        Department department = department();
        Faculty saved = member(department);
        assertThatThrownBy(() -> faculty.saveAndFlush(new Faculty("Duplicate", unique() + "@example.edu",
                saved.getEmployeeCode(), null, department))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeyPreventsDepartmentDeletion() {
        Department saved = department();
        member(saved);
        Long departmentId = saved.getId();
        // Persist the fixture, then detach the faculty graph before testing the database FK.
        // Otherwise Hibernate rejects the managed faculty's reference to the removed parent
        // before a SQL DELETE reaches PostgreSQL.
        entityManager.flush();
        entityManager.clear();
        Throwable failure = catchThrowable(() -> {
            departments.deleteById(departmentId);
            departments.flush();
        });
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        Throwable cause = ((DataIntegrityViolationException) failure).getMostSpecificCause();
        assertThat(cause).isInstanceOf(PSQLException.class);
        PSQLException postgresFailure = (PSQLException) cause;
        // V1 uses ON DELETE RESTRICT: require this specific restriction and FK.
        assertThat(postgresFailure.getSQLState()).isEqualTo("23001");
        assertThat(postgresFailure.getServerErrorMessage()).isNotNull();
        assertThat(postgresFailure.getServerErrorMessage().getConstraint()).isEqualTo("fk_faculty_department");
    }

    @Test
    void repositorySearchCombinesFilters() {
        Department department = department();
        Department otherDepartment = department();
        String name = "Integration Asha " + unique();
        Faculty active = member(department);
        Faculty inactive = member(department);
        Faculty other = member(otherDepartment);
        for (Faculty record : new Faculty[] {active, inactive, other}) {
            record.update(name, record.getEmail(), record.getEmployeeCode(), record.getDesignation(), record.getDepartment());
        }
        inactive.setActive(false);
        faculty.flush();
        entityManager.clear();
        var pageable = PageRequest.of(0, 10, Sort.by("id"));

        // No nullable search parameter should be bound when filters are absent.
        var unfiltered = faculty.search(null, null, null, PageRequest.of(0, 1, Sort.by("id")));
        assertThat(unfiltered.getTotalElements()).isEqualTo(faculty.count());
        assertThat(unfiltered.getContent()).hasSize(1);
        assertThat(faculty.search(department.getId(), null, null, pageable).getContent())
                .extracting(Faculty::getId).containsExactly(active.getId(), inactive.getId());
        assertThat(faculty.search(null, false, null, PageRequest.of(0, 1)).getTotalElements())
                .isEqualTo(jdbc.queryForObject("select count(*) from faculty where active = false", Long.class));
        assertThat(faculty.search(null, null, name.toUpperCase(java.util.Locale.ROOT), pageable).getContent())
                .extracting(Faculty::getId).containsExactly(active.getId(), inactive.getId(), other.getId());
        assertThat(faculty.search(department.getId(), true, name, pageable).getContent())
                .extracting(Faculty::getId).containsExactly(active.getId());
        assertThat(faculty.search(department.getId(), false, name, pageable).getContent())
                .extracting(Faculty::getId).containsExactly(inactive.getId());
        assertThat(faculty.search(department.getId(), null, null,
                PageRequest.of(1, 1, Sort.by("id"))).getContent())
                .extracting(Faculty::getId).containsExactly(inactive.getId());
        assertThat(faculty.search(department.getId(), null, null,
                PageRequest.of(2, 1, Sort.by("id"))).getContent()).isEmpty();
        assertThat(faculty.search(department.getId(), null, "missing", pageable).isEmpty()).isTrue();
        assertThat(departments.findByCode(department.getCode())).isPresent();
        assertThat(facultyService.list(department.getId(), null, "   ", pageable).totalElements()).isEqualTo(2);

        Faculty literal = faculty.findById(active.getId()).orElseThrow();
        literal.update("Asha_%! " + name, literal.getEmail(), literal.getEmployeeCode(),
                literal.getDesignation(), literal.getDepartment());
        faculty.flush();
        assertThat(facultyService.list(null, null, "ASHA_%! " + name, pageable).content())
                .extracting(FacultyResponse::id).containsExactly(active.getId());
    }
    @Test
    void servicesPersistUpdatesAndStatus() {
        Department saved = department();
        var department = departmentService.update(saved.getId(), new DepartmentRequest("Updated", saved.getCode(), "New"));
        assertThat(department.name()).isEqualTo("Updated");
        String token = unique();
        var request = new FacultyRequest("Asha", token + "@example.edu", token, "Lecturer", saved.getId());
        var created = facultyService.create(request);
        var updated = facultyService.update(created.id(), new FacultyRequest("Asha Updated", request.email(),
                token, "Professor", saved.getId()));
        assertThat(updated.fullName()).isEqualTo("Asha Updated");
        assertThat(facultyService.updateStatus(created.id(), new FacultyStatusRequest(false)).active()).isFalse();
        facultyService.delete(created.id());
        departmentService.delete(saved.getId());
        assertThat(departments.findById(saved.getId())).isEmpty();
    }

    @Test
    void httpCrudAndValidationUseRealPersistence() throws Exception {
        String code = unique();
        String body = "{\"name\":\"HTTP Department\",\"code\":\"" + code + "\"}";
        mvc.perform(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(code));
        Department saved = departments.findByCode(code).orElseThrow();
        mvc.perform(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/departments/" + saved.getId())).andExpect(status().isOk());
        mvc.perform(put("/api/v1/departments/" + saved.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("HTTP Department", "Updated Department")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated Department"));
        String token = unique();
        String facultyBody = "{\"fullName\":\"HTTP Faculty\",\"email\":\"" + token +
                "@example.edu\",\"employeeCode\":\"" + token + "\",\"departmentId\":" + saved.getId() + "}";
        mvc.perform(post("/api/v1/faculty").contentType(MediaType.APPLICATION_JSON).content(facultyBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true));
        Long facultyId = faculty.search(saved.getId(), null, null, PageRequest.of(0, 10)).getContent().getFirst().getId();
        mvc.perform(post("/api/v1/faculty").contentType(MediaType.APPLICATION_JSON).content(facultyBody))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/faculty/" + facultyId)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/faculty/" + facultyId).contentType(MediaType.APPLICATION_JSON)
                .content(facultyBody.replace("HTTP Faculty", "Updated Faculty")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Updated Faculty"));
        mvc.perform(patch("/api/v1/faculty/" + facultyId + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/v1/faculty?departmentId=" + saved.getId() + "&active=false&name=Updated"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(delete("/api/v1/departments/" + saved.getId())).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/faculty").contentType(MediaType.APPLICATION_JSON)
                .content(facultyBody.replace(token + "@example.edu", "bad"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/faculty").contentType(MediaType.APPLICATION_JSON)
                .content(facultyBody.replace("\"departmentId\":" + saved.getId(), "\"departmentId\":9223372036854775807")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/faculty/" + facultyId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/departments/" + saved.getId())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/departments/" + saved.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/campus/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
