package com.example.campusflow_backend.controller;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.entity.StudentStatus;
import com.example.campusflow_backend.exception.*;
import com.example.campusflow_backend.service.*;
import org.junit.jupiter.api.Test;
import com.example.campusflow_backend.support.SecurityWebSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({StudentController.class, AcademicBatchController.class, AcademicSectionController.class})
@org.springframework.security.test.context.support.WithMockUser(roles="ADMIN")
@SecurityWebSupport
class AcademicControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private StudentService students;
    @MockitoBean private AcademicBatchService batches;
    @MockitoBean private AcademicSectionService sections;
    private static final String BATCH = """
            {"name":"CSE2023","startYear":2023,"endYear":2027,"departmentId":1,"active":true}
            """;
    private static final String SECTION = """
            {"name":"A","batchId":2,"capacity":60,"active":true}
            """;
    private static final String STUDENT = """
            {"enrollmentNumber":"EN001","rollNumber":"R1","fullName":"Asha","email":"asha@example.edu",
             "departmentId":1,"batchId":2,"sectionId":3,"currentSemester":1,"admissionYear":2023}
            """;
    private StudentResponse student() {
        return new StudentResponse(4L, "EN001", "R1", "Asha", "asha@example.edu", null, 1L, 2L, 3L,
                1, 2023, StudentStatus.ACTIVE, Instant.now(), Instant.now());
    }
    private BatchResponse batch() { return new BatchResponse(2L,"CSE2023",2023,2027,1L,true,Instant.now(),Instant.now()); }
    private SectionResponse section() { return new SectionResponse(3L,"A",2L,60,true,Instant.now(),Instant.now()); }
    @Test void createsStudentWithLocation() throws Exception {
        when(students.create(any())).thenReturn(student());
        mvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content(STUDENT))
                .andExpect(status().isCreated()).andExpect(header().string("Location","/api/v1/students/4"))
                .andExpect(jsonPath("$.enrollmentNumber").value("EN001"));
    }
    @Test void createsGroupsWithLocation() throws Exception {
        when(batches.create(any())).thenReturn(batch()); when(sections.create(any())).thenReturn(section());
        mvc.perform(post("/api/v1/batches").contentType(MediaType.APPLICATION_JSON).content(BATCH))
                .andExpect(status().isCreated()).andExpect(header().string("Location","/api/v1/batches/2"));
        mvc.perform(post("/api/v1/sections").contentType(MediaType.APPLICATION_JSON).content(SECTION))
                .andExpect(status().isCreated()).andExpect(header().string("Location","/api/v1/sections/3"));
    }
    @Test void readsAndUpdatesAllResources() throws Exception {
        when(students.get(4L)).thenReturn(student()); when(students.update(eq(4L),any())).thenReturn(student());
        when(batches.get(2L)).thenReturn(batch()); when(batches.update(eq(2L),any())).thenReturn(batch());
        when(sections.get(3L)).thenReturn(section()); when(sections.update(eq(3L),any())).thenReturn(section());
        mvc.perform(get("/api/v1/students/4")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/batches/2")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/sections/3")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/students/4").contentType(MediaType.APPLICATION_JSON).content(STUDENT)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/batches/2").contentType(MediaType.APPLICATION_JSON).content(BATCH)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/sections/3").contentType(MediaType.APPLICATION_JSON).content(SECTION)).andExpect(status().isOk());
    }
    @Test void listsGroupsWithPagination() throws Exception {
        when(batches.list(eq(1L),any())).thenReturn(new PageResponse<>(List.of(batch()),0,20,1,1,true));
        when(sections.list(eq(2L),any())).thenReturn(new PageResponse<>(List.of(section()),0,20,1,1,true));
        mvc.perform(get("/api/v1/batches?departmentId=1")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(2));
        mvc.perform(get("/api/v1/sections?batchId=2")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(3));
    }
    @Test void rejectsInvalidYearRange() throws Exception {
        mvc.perform(post("/api/v1/batches").contentType(MediaType.APPLICATION_JSON).content(BATCH.replace("2027","2022")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.yearRangeValid").exists());
        verifyNoInteractions(batches);
    }
    @Test void rejectsInvalidCapacity() throws Exception {
        mvc.perform(post("/api/v1/sections").contentType(MediaType.APPLICATION_JSON).content(SECTION.replace("60","0")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.capacity").exists());
    }
    @Test void rejectsInvalidStudentFields() throws Exception {
        mvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content(STUDENT.replace("asha@example.edu","bad")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
        mvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content(STUDENT.replace("\"currentSemester\":1","\"currentSemester\":0")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.currentSemester").exists());
        mvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(students);
    }
    @Test void rejectsInvalidStatus() throws Exception {
        mvc.perform(patch("/api/v1/students/4/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/students/4/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void statusUpdateDelegates() throws Exception {
        when(students.updateStatus(eq(4L),any())).thenReturn(student());
        mvc.perform(patch("/api/v1/students/4/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    }
    @Test void rejectsInvalidPaginationAndFilters() throws Exception {
        for (String path : List.of("/api/v1/students?page=-1", "/api/v1/students?size=101", "/api/v1/students?semester=0",
                "/api/v1/students?status=UNKNOWN", "/api/v1/batches?departmentId=0", "/api/v1/sections?size=0")) {
            mvc.perform(get(path)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(students,batches,sections);
    }
    @Test void combinedFiltersAreForwarded() throws Exception {
        when(students.list(eq("Asha"),eq("EN001"),eq(1L),eq(2L),eq(3L),eq(1),eq(StudentStatus.ACTIVE),any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(student()),0,10,1,1,true));
        mvc.perform(get("/api/v1/students?name=Asha&enrollmentNumber=EN001&departmentId=1&batchId=2&sectionId=3&semester=1&status=ACTIVE&size=10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        verify(students).list(eq("Asha"),eq("EN001"),eq(1L),eq(2L),eq(3L),eq(1),eq(StudentStatus.ACTIVE),
                argThat(page -> page.getPageSize()==10 && page.getSort().getOrderFor("id")!=null));
    }
    @Test void notFoundAndConflictResponses() throws Exception {
        when(students.get(99L)).thenThrow(new ResourceNotFoundException("Student not found."));
        when(students.create(any())).thenThrow(new ConflictException("Enrollment number already exists."));
        mvc.perform(get("/api/v1/students/99")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/students").contentType(MediaType.APPLICATION_JSON).content(STUDENT))
                .andExpect(status().isConflict());
    }
    @Test void groupDeletionAndRestrictions() throws Exception {
        mvc.perform(delete("/api/v1/batches/2")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/sections/3")).andExpect(status().isNoContent());
        doThrow(new ConflictException("Section has students.")).when(sections).delete(9L);
        doThrow(new ConflictException("Batch has students.")).when(batches).delete(9L);
        mvc.perform(delete("/api/v1/sections/9")).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/batches/9")).andExpect(status().isConflict());
    }
    @Test void studentHardDeletionIsNotExposed() throws Exception {
        mvc.perform(delete("/api/v1/students/4")).andExpect(status().isMethodNotAllowed());
    }
}
