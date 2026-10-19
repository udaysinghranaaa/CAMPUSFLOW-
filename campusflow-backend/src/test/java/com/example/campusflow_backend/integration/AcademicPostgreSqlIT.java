package com.example.campusflow_backend.integration;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.repository.*;
import com.example.campusflow_backend.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@org.springframework.security.test.context.support.WithMockUser(roles="ADMIN")
class AcademicPostgreSqlIT extends com.example.campusflow_backend.support.SecurityIntegrationSupport {
    @Autowired private DepartmentRepository departments;
    @Autowired private AcademicBatchRepository batches;
    @Autowired private AcademicSectionRepository sections;
    @Autowired private StudentRepository students;
    @Autowired private StudentService studentService;
    @Autowired private AcademicBatchService batchService;
    @Autowired private AcademicSectionService sectionService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @PersistenceContext private EntityManager entityManager;
    private record Group(Department department, AcademicBatch batch, AcademicSection section) { }
    private String token() { return UUID.randomUUID().toString().replace("-", "").substring(0,16).toUpperCase(Locale.ROOT); }
    private Group group(boolean withSection) {
        Department department = departments.saveAndFlush(new Department("Academic IT", token(), null));
        AcademicBatch batch = batches.saveAndFlush(new AcademicBatch("CSE " + token(),2023,2027,department,true));
        AcademicSection section = withSection ? sections.saveAndFlush(new AcademicSection("A",batch,60,true)) : null;
        return new Group(department,batch,section);
    }
    private Student member(Group group) {
        String token = token();
        return students.saveAndFlush(new Student(token,token,"Student " + token,token.toLowerCase(Locale.ROOT)+"@example.edu",
                null,group.department(),group.batch(),group.section(),1,2023));
    }
    private void violation(String state, String constraint, ThrowingCallable operation) {
        Throwable failure = catchThrowable(operation);
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        Throwable root = ((DataIntegrityViolationException)failure).getMostSpecificCause();
        assertThat(root).isInstanceOf(PSQLException.class);
        PSQLException postgres = (PSQLException)root;
        assertThat(postgres.getSQLState()).isEqualTo(state);
        assertThat(postgres.getServerErrorMessage()).isNotNull();
        assertThat(postgres.getServerErrorMessage().getConstraint()).isEqualTo(constraint);
    }
    @Test void v2MigrationAndMappingsAreValid() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version='2' and success",Long.class)).isEqualTo(1L);
        Student student = member(group(true));
        Long id = student.getId();
        entityManager.clear();
        Student loaded = students.findById(id).orElseThrow();
        assertThat(loaded.getSection().getBatch().getDepartment().getId()).isEqualTo(loaded.getDepartment().getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
    }
    @Test void duplicateEnrollmentFailsInPostgres() {
        Group group = group(true); Student saved = member(group); String other = token();
        violation("23505","uk_student_enrollment",() -> students.saveAndFlush(new Student(saved.getEnrollmentNumber(),other,
                "Other",other.toLowerCase(Locale.ROOT)+"@example.edu",null,group.department(),group.batch(),group.section(),1,2023)));
    }
    @Test void duplicateEmailFailsInPostgres() {
        Group group = group(true); Student saved = member(group); String other=token();
        violation("23505","uk_student_email",() -> students.saveAndFlush(new Student(other,other,"Other",saved.getEmail(),
                null,group.department(),group.batch(),group.section(),1,2023)));
    }
    @Test void rollIsUniqueWithinBatch() {
        Group group=group(true); Student saved=member(group); String other=token();
        violation("23505","uk_student_batch_roll",() -> students.saveAndFlush(new Student(other,saved.getRollNumber(),"Other",
                other.toLowerCase(Locale.ROOT)+"@example.edu",null,group.department(),group.batch(),group.section(),1,2023)));
    }
    @Test void rollCanRepeatInAnotherBatch() {
        Group first=group(false); Group second=group(false); Student saved=member(first); String other=token();
        Student repeated=students.saveAndFlush(new Student(other,saved.getRollNumber(),"Other",other.toLowerCase(Locale.ROOT)+"@example.edu",
                null,second.department(),second.batch(),null,1,2023));
        assertThat(repeated.getId()).isNotEqualTo(saved.getId());
    }
    @Test void batchDepartmentMismatchFailsInDatabase() {
        Student saved=member(group(true)); Group other=group(false);
        violation("23503","fk_student_batch_department",() -> jdbc.update("update students set department_id=? where id=?",
                other.department().getId(),saved.getId()));
    }
    @Test void sectionBatchMismatchFailsInDatabase() {
        Student saved=member(group(true)); Group other=group(true);
        violation("23503","fk_student_section_batch",() -> jdbc.update("update students set section_id=? where id=?",
                other.section().getId(),saved.getId()));
    }
    @Test void enrollmentIsImmutableEvenThroughSql() {
        Student saved=member(group(false));
        violation("23514","ck_student_enrollment_immutable",() -> jdbc.update("update students set enrollment_number=? where id=?",token(),saved.getId()));
    }
    @Test void sectionDeletionIsRestrictedByStudents() {
        Group group=group(true); member(group); Long id=group.section().getId();
        entityManager.flush(); entityManager.clear();
        violation("23001","fk_student_section_batch",() -> { sections.deleteById(id); sections.flush(); });
    }
    @Test void batchDeletionIsRestrictedByStudents() {
        Group group=group(false); member(group); Long id=group.batch().getId();
        entityManager.flush(); entityManager.clear();
        violation("23001","fk_student_batch_department",() -> { batches.deleteById(id); batches.flush(); });
    }
    @Test void batchNamesAreUniqueWithinDepartmentIgnoringCase() {
        Group group=group(false);
        violation("23505","uk_batch_department_name",() -> batches.saveAndFlush(new AcademicBatch(
                group.batch().getName().toLowerCase(Locale.ROOT),2023,2027,group.department(),true)));
    }
    @Test void sectionNamesAreUniqueWithinBatchIgnoringCase() {
        Group group=group(true);
        violation("23505","uk_section_batch_name",() -> sections.saveAndFlush(new AcademicSection("a",group.batch(),null,true)));
    }
    @Test void positiveCapacityAndYearChecksAreEnforced() {
        Group group=group(true);
        violation("23514","academic_sections_capacity_check",() -> jdbc.update("update academic_sections set capacity=0 where id=?",group.section().getId()));
    }
    @Test void yearRangeIsEnforcedInPostgres() {
        Group group=group(false);
        // Verify the applied constraint's definition instead of assuming its generated name.
        String yearCheck = jdbc.queryForObject("""
                select pg_get_constraintdef(oid) from pg_constraint
                where conrelid = 'academic_batches'::regclass and conname = ? and contype = 'c'
                """, String.class, "academic_batches_check");
        assertThat(yearCheck).contains("end_year > start_year");
        violation("23514","academic_batches_check",() -> jdbc.update("update academic_batches set end_year=start_year where id=?",group.batch().getId()));
    }
    @Test void searchHandlesOptionalFiltersAndPagination() {
        Group group=group(true); Student first=member(group); Student second=member(group);
        second.setStatus(StudentStatus.INACTIVE); students.flush(); entityManager.clear();
        var page=PageRequest.of(0,10,Sort.by("id"));
        assertThat(students.search(null,null,null,null,null,null,null,PageRequest.of(0,1)).getTotalElements()).isEqualTo(students.count());
        assertThat(students.search(null,null,group.department().getId(),null,null,null,null,page).getTotalElements()).isEqualTo(2);
        assertThat(students.search(null,null,null,group.batch().getId(),null,null,null,page).getTotalElements()).isEqualTo(2);
        assertThat(students.search(null,null,null,null,group.section().getId(),null,null,page).getTotalElements()).isEqualTo(2);
        assertThat(students.search(first.getFullName().toUpperCase(Locale.ROOT),first.getEnrollmentNumber().toLowerCase(Locale.ROOT),
                group.department().getId(),group.batch().getId(),group.section().getId(),1,StudentStatus.ACTIVE,page).getContent())
                .extracting(Student::getId).containsExactly(first.getId());
        assertThat(students.search(null,null,null,group.batch().getId(),null,null,null,
                PageRequest.of(1,1,Sort.by("id"))).getContent()).extracting(Student::getId).containsExactly(second.getId());
        assertThat(students.search(null,null,null,group.batch().getId(),null,2,null,page).getContent()).isEmpty();
        Student literal=students.findById(first.getId()).orElseThrow();
        String originalRollNumber = literal.getRollNumber();
        // update accepts rollNumber before fullName; keep identifiers unchanged.
        literal.update(originalRollNumber,"Literal_%! "+first.getEnrollmentNumber(),literal.getEmail(),null,
                literal.getDepartment(),literal.getBatch(),literal.getSection(),1,2023);
        students.flush();
        entityManager.clear();
        assertThat(students.findById(first.getId()).orElseThrow().getRollNumber()).isEqualTo(originalRollNumber);
        assertThat(students.search("LITERAL_%! "+first.getEnrollmentNumber(),null,null,null,null,null,null,page).getContent())
                .extracting(Student::getId).containsExactly(first.getId());
    }
    @Test void persistentServiceAndHttpFlow() throws Exception {
        Group group=group(true); String token=token();
        StudentRequest request=new StudentRequest(token,token,"Academic Student",token+"@example.edu",null,
                group.department().getId(),group.batch().getId(),group.section().getId(),1,2023);
        var created=studentService.create(request);
        assertThat(studentService.get(created.id()).email()).isEqualTo(token.toLowerCase(Locale.ROOT)+"@example.edu");
        var updated=studentService.update(created.id(),new StudentRequest(token,token,"Updated Student",token+"@example.edu",null,
                request.departmentId(),request.batchId(),null,2,2023));
        assertThat(updated.sectionId()).isNull(); assertThat(updated.currentSemester()).isEqualTo(2);
        mvc.perform(get("/api/v1/students/"+created.id())).andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Updated Student"));
        mvc.perform(patch("/api/v1/students/"+created.id()+"/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"GRADUATED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("GRADUATED"));
        mvc.perform(get("/api/v1/students?batchId="+request.batchId()+"&semester=2&status=GRADUATED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(delete("/api/v1/batches/"+request.batchId())).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/students/"+created.id())).andExpect(status().isMethodNotAllowed());
        // MockMvc request cleanup clears the thread context; restore this test's @WithMockUser ADMIN.
        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());
        // No student references this section after unassignment; delete only this test's section.
        sectionService.delete(request.sectionId());
        batchService.update(request.batchId(),new BatchRequest("Updated batch",2023,2028,request.departmentId(),false));
        mvc.perform(get("/api/campus/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
