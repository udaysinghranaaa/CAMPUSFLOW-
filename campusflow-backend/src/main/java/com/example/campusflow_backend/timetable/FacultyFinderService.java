package com.example.campusflow_backend.timetable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.*;
import com.example.campusflow_backend.dto.PageResponse;
import com.example.campusflow_backend.exception.ResourceNotFoundException;
import static com.example.campusflow_backend.timetable.SchedulingDtos.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@Service @Transactional(readOnly=true) @PreAuthorize("isAuthenticated()")
public class FacultyFinderService {
 private final ScheduleReadRepository reads;private final Clock clock;
 public FacultyFinderService(ScheduleReadRepository reads,Clock clock){this.reads=reads;this.clock=clock;}
 public PageResponse<FinderFaculty> find(String name,Long departmentId,Long subjectId,AvailabilityState state,LocalDate date,LocalTime start,LocalTime end,int page,int size){ScheduleRules.times(start,end);return reads.finder(name,departmentId,subjectId,state,date==null?LocalDate.now(clock.withZone(ScheduleRules.CAMPUS)):date,start,end,null,page,size);}
 public FinderFaculty detail(Long id,LocalDate date,LocalTime start,LocalTime end){ScheduleRules.times(start,end);return reads.finder(null,null,null,null,date==null?LocalDate.now(clock.withZone(ScheduleRules.CAMPUS)):date,start,end,id,0,1).content().stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Active faculty not found."));}
}
