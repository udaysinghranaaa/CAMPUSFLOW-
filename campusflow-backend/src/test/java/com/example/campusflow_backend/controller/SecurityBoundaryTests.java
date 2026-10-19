package com.example.campusflow_backend.controller;
import com.example.campusflow_backend.auth.*;
import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.service.*;
import com.example.campusflow_backend.support.SecurityWebSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({DepartmentController.class,FacultyController.class,StudentController.class,AuthController.class,
        AccountController.class,FacultyDirectoryController.class})
@SecurityWebSupport
class SecurityBoundaryTests {
    @Autowired MockMvc mvc; @Autowired JwtDecoder decoder;
    @MockitoBean DepartmentService departments; @MockitoBean FacultyService faculty; @MockitoBean StudentService students;
    @MockitoBean AuthService auth; @MockitoBean OwnProfileService profiles; @MockitoBean AccountService accounts;
    @MockitoBean FacultyDirectoryService directory;
    @Test void unauthenticatedPrivateReadsAndWritesAre401() throws Exception {
        mvc.perform(get("/api/v1/students/1")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(post("/api/v1/departments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());verifyNoInteractions(departments,students);
    }
    @Test void studentsCannotReadOtherPrivateRecordsOrManageAccounts() throws Exception {
        for(String path:List.of("/api/v1/students/2","/api/v1/faculty/2","/api/v1/admin/accounts"))
            mvc.perform(get(path).with(user("student").roles("STUDENT"))).andExpect(status().isForbidden());
    }
    @Test void facultyCannotModifyAcademicDataOrReadPrivateStudents() throws Exception {
        mvc.perform(post("/api/v1/departments").with(user("faculty").roles("FACULTY")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/students/2").with(user("faculty").roles("FACULTY"))).andExpect(status().isForbidden());
    }
    @Test void adminCanCreateDepartmentWithSecurityEnabled() throws Exception {
        when(departments.create(any())).thenReturn(new DepartmentResponse(1L,"Computing","CS",null,Instant.now(),Instant.now()));
        mvc.perform(post("/api/v1/departments").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Computing\",\"code\":\"CS\"}"))
                .andExpect(status().isCreated());
    }
    @Test void academicReadAndMinimizedDirectoryAreAuthenticated() throws Exception {
        when(directory.list(any())).thenReturn(new PageResponse<>(List.of(new FacultyDirectoryResponse(1L,"Teacher","Professor","CS",true)),0,20,1,1,true));
        for(String role:List.of("STUDENT","FACULTY","ADMIN")) {
            mvc.perform(get("/api/v1/departments/1").with(user("reader").roles(role))).andExpect(status().isOk());
            mvc.perform(get("/api/v1/directory/faculty").with(user("reader").roles(role)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].email").doesNotExist())
                    .andExpect(jsonPath("$.content[0].employeeCode").doesNotExist());
        }
    }
    @Test void invalidBearerReturnsSafe401() throws Exception {
        when(decoder.decode(anyString())).thenThrow(new BadJwtException("Internal cryptographic details"));
        mvc.perform(get("/api/v1/students/1").header("Authorization","Bearer invalid-token"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication required or invalid credentials."));
    }
    @Test void loginErrorsDoNotEnumerateAccounts() throws Exception {
        when(auth.login(any())).thenThrow(new BadCredentialsException("private detail"));
        String random=UUID.randomUUID().toString();
        for(String email:List.of("known@example.test","unknown@example.test")) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\""+email+"\",\"password\":\""+random+"\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication required or invalid credentials."));
        }
    }
    @Test void publicRegistrationAndUnknownRoutesAreDenied() throws Exception {
        mvc.perform(post("/api/v1/auth/register")).andExpect(status().isUnauthorized());
        mvc.perform(get("/unexpected").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void corsOnlyAllowsConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/login").header("Origin","http://localhost:5173")
                .header("Access-Control-Request-Method","POST")).andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
        mvc.perform(options("/api/v1/auth/login").header("Origin","https://untrusted.example")
                .header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());
    }
}
