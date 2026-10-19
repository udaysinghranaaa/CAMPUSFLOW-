package com.example.campusflow_backend.timetable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import com.example.campusflow_backend.support.SecurityWebSupport;
import com.example.campusflow_backend.dto.PageResponse;
import java.util.*;import java.time.*;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest({CatalogController.class,TimetableController.class,FacultyFinderController.class}) @SecurityWebSupport
class Phase5WebTests {
 @Autowired MockMvc mvc;@MockitoBean CatalogService catalog;@MockitoBean SchedulingService scheduling;@MockitoBean TimetableReadService reads;@MockitoBean FacultyFinderService finder;
 @Test void anonymousFinderIs401() throws Exception{mvc.perform(get("/api/v1/faculty-finder")).andExpect(status().isUnauthorized());verifyNoInteractions(finder);}
 @Test void studentsCannotWriteSchedulesOrMasters() throws Exception{for(String path:List.of("/api/v1/subjects","/api/v1/rooms","/api/v1/timetable/sessions","/api/v1/timetable/terms"))mvc.perform(post(path).with(user("student").roles("STUDENT")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());verifyNoInteractions(catalog,scheduling);}
 @Test void studentCannotUseFacultyTimetableRoute() throws Exception{mvc.perform(get("/api/v1/timetable/faculty/me").with(user("s").roles("STUDENT"))).andExpect(status().isForbidden());}
 @Test void facultyCannotUseStudentOrOtherSectionRoutes() throws Exception{mvc.perform(get("/api/v1/timetable/student/me").with(user("f").roles("FACULTY"))).andExpect(status().isForbidden());mvc.perform(get("/api/v1/timetable/sections/2").with(user("f").roles("FACULTY"))).andExpect(status().isForbidden());}
 @Test void adminInvalidSubjectAndRoomBodiesAre400() throws Exception{for(String path:List.of("/api/v1/subjects","/api/v1/rooms"))mvc.perform(post(path).with(user("a").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isMap());}
 @Test void finderHasSafePublicDto() throws Exception{when(finder.find(any(),any(),any(),any(),any(),any(),any(),anyInt(),anyInt())).thenReturn(new PageResponse<>(List.of(new SchedulingDtos.FinderFaculty(1L,"Teacher","CS","Lecturer",List.of("Algorithms"),TimetableTypes.AvailabilityState.UNKNOWN,"Coverage incomplete",LocalDate.of(2026,10,19),LocalTime.of(9,0),LocalTime.of(10,0))),0,20,1,1,true));mvc.perform(get("/api/v1/faculty-finder").with(user("s").roles("STUDENT"))).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].state").value("UNKNOWN")).andExpect(jsonPath("$.content[0].email").doesNotExist()).andExpect(jsonPath("$.content[0].reason").doesNotExist());}
 @Test void unauthorizedBlockReadsDenied() throws Exception{mvc.perform(get("/api/v1/timetable/unavailability?facultyId=1").with(user("s").roles("STUDENT"))).andExpect(status().isForbidden());}
}
