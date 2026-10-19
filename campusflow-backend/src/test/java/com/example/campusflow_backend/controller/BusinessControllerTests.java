package com.example.campusflow_backend.controller;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.exception.*;
import com.example.campusflow_backend.service.*;
import org.junit.jupiter.api.Test;
import com.example.campusflow_backend.support.SecurityWebSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({DepartmentController.class, FacultyController.class})
@org.springframework.security.test.context.support.WithMockUser(roles="ADMIN")
@SecurityWebSupport
class BusinessControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private DepartmentService departments;
    @MockitoBean private FacultyService faculty;

    private static final String DEPARTMENT = """
            {"name":"Computer Science","code":"CSE","description":"Engineering"}
            """;
    private static final String FACULTY = """
            {"fullName":"Asha Sharma","email":"asha@example.edu","employeeCode":"EMP001",
             "designation":"Professor","departmentId":1}
            """;

    private DepartmentResponse department() {
        return new DepartmentResponse(1L, "Computer Science", "CSE", "Engineering", Instant.now(), Instant.now());
    }

    private FacultyResponse member(boolean active) {
        return new FacultyResponse(2L, "Asha Sharma", "asha@example.edu", "EMP001", "Professor",
                1L, "CSE", active, Instant.now(), Instant.now());
    }

    @Test
    void departmentCreationReturnsCreatedAndLocation() throws Exception {
        when(departments.create(any())).thenReturn(department());
        mvc.perform(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content(DEPARTMENT))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/departments/1"))
                .andExpect(jsonPath("$.code").value("CSE"));
    }

    @Test
    void facultyCreationReturnsCreatedAndLocation() throws Exception {
        when(faculty.create(any())).thenReturn(member(true));
        mvc.perform(post("/api/v1/faculty").contentType(MediaType.APPLICATION_JSON).content(FACULTY))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/faculty/2"))
                .andExpect(jsonPath("$.departmentId").value(1));
    }

    @Test
    void blankDepartmentNameReturnsFieldError() throws Exception {
        mvc.perform(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"code\":\"CSE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/departments"));
        verifyNoInteractions(departments);
    }

    @Test
    void invalidFacultyEmailReturnsFieldError() throws Exception {
        mvc.perform(post("/api/v1/faculty").contentType(MediaType.APPLICATION_JSON)
                        .content(FACULTY.replace("asha@example.edu", "invalid")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
        verifyNoInteractions(faculty);
    }

    @Test
    void oversizedCodeReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON)
                        .content(DEPARTMENT.replace("CSE", "X".repeat(21))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingStatusReturnsBadRequest() throws Exception {
        mvc.perform(patch("/api/v1/faculty/2/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.active").exists());
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paginationBoundsReturnBadRequest() throws Exception {
        mvc.perform(get("/api/v1/departments?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/faculty?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/faculty?departmentId=0")).andExpect(status().isBadRequest());
        verifyNoInteractions(departments, faculty);
    }

    @Test
    void missingDepartmentReturnsNotFound() throws Exception {
        when(departments.get(999L)).thenThrow(new ResourceNotFoundException("Department not found."));
        mvc.perform(get("/api/v1/departments/999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void duplicateCodeReturnsConflict() throws Exception {
        when(departments.create(any())).thenThrow(new ConflictException("Department code already exists."));
        mvc.perform(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content(DEPARTMENT))
                .andExpect(status().isConflict());
    }

    @Test
    void databaseConstraintConflictDoesNotExposeDetails() throws Exception {
        when(faculty.create(any())).thenThrow(new DataIntegrityViolationException("private SQL detail"));
        mvc.perform(post("/api/v1/faculty").contentType(MediaType.APPLICATION_JSON).content(FACULTY))
                .andExpect(status().isConflict()).andExpect(content().string(not(containsString("private SQL"))));
    }

    @Test
    void unexpectedErrorReturnsSafe500() throws Exception {
        when(departments.get(1L)).thenThrow(new IllegalStateException("private server detail"));
        mvc.perform(get("/api/v1/departments/1")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected server error occurred."))
                .andExpect(content().string(not(containsString("private server"))));
    }

    @Test
    void deletionReturnsNoContentAndReferenceConflictReturns409() throws Exception {
        mvc.perform(delete("/api/v1/departments/1")).andExpect(status().isNoContent());
        doThrow(new ConflictException("Department has faculty records.")).when(departments).delete(2L);
        mvc.perform(delete("/api/v1/departments/2")).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/faculty/3")).andExpect(status().isNoContent());
    }

    @Test
    void facultyFiltersAndPaginationAreForwarded() throws Exception {
        when(faculty.list(eq(1L), eq(false), eq("Asha"), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(member(false)), 0, 10, 1, 1, true));
        mvc.perform(get("/api/v1/faculty?departmentId=1&active=false&name=Asha&size=10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].active").value(false))
                .andExpect(jsonPath("$.totalElements").value(1));
        verify(faculty).list(eq(1L), eq(false), eq("Asha"),
                argThat(page -> page.getPageSize() == 10 && page.getSort().getOrderFor("id") != null));
    }

    @Test
    void updatesDepartmentFacultyAndStatus() throws Exception {
        when(departments.update(eq(1L), any())).thenReturn(department());
        mvc.perform(put("/api/v1/departments/1").contentType(MediaType.APPLICATION_JSON).content(DEPARTMENT))
                .andExpect(status().isOk());
        when(faculty.update(eq(2L), any())).thenReturn(member(true));
        mvc.perform(put("/api/v1/faculty/2").contentType(MediaType.APPLICATION_JSON).content(FACULTY))
                .andExpect(status().isOk());
        when(faculty.updateStatus(eq(2L), any())).thenReturn(member(false));
        mvc.perform(patch("/api/v1/faculty/2/status").contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }
}
