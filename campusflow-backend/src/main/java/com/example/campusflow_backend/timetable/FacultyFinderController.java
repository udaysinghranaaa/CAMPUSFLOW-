package com.example.campusflow_backend.timetable;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import com.example.campusflow_backend.dto.PageResponse;
import static com.example.campusflow_backend.timetable.SchedulingDtos.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@RestController @RequestMapping("/api/v1/faculty-finder")
public class FacultyFinderController {
 private final FacultyFinderService service;
 public FacultyFinderController(FacultyFinderService service){this.service=service;}
 @GetMapping public PageResponse<FinderFaculty> list(@RequestParam(required=false) @Size(max=150) String name,@RequestParam(required=false) @Positive Long departmentId,@RequestParam(required=false) @Positive Long subjectId,@RequestParam(required=false) AvailabilityState state,@RequestParam(required=false) LocalDate date,@RequestParam(defaultValue="09:00") LocalTime startTime,@RequestParam(defaultValue="10:00") LocalTime endTime,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.find(name,departmentId,subjectId,state,date,startTime,endTime,page,size);}
 @GetMapping({"/{id}","/{id}/availability"}) public FinderFaculty detail(@PathVariable @Positive Long id,@RequestParam(required=false) LocalDate date,@RequestParam(defaultValue="09:00") LocalTime startTime,@RequestParam(defaultValue="10:00") LocalTime endTime){return service.detail(id,date,startTime,endTime);}
}
