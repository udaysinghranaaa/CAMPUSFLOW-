package com.example.campusflow_backend.timetable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import com.example.campusflow_backend.dto.PageResponse;
import java.net.URI;
import java.time.*;
import static com.example.campusflow_backend.timetable.SchedulingDtos.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@RestController @RequestMapping("/api/v1/timetable")
public class TimetableController {
 private final SchedulingService service;private final TimetableReadService reads;
 public TimetableController(SchedulingService service,TimetableReadService reads){this.service=service;this.reads=reads;}
 @GetMapping("/admin/week") public WeeklyTimetable admin(@RequestParam @Positive Long academicTermId,@RequestParam(required=false) LocalDate weekStart){return reads.admin(academicTermId,weekStart);} @GetMapping("/sessions") public PageResponse<SessionResponse> list(@RequestParam(required=false) @Positive Long academicTermId,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.list(academicTermId,CatalogController.page(page,size));}
 @GetMapping("/sessions/{id}") public SessionResponse get(@PathVariable @Positive Long id){return service.get(id);} @PostMapping("/sessions") public ResponseEntity<SessionResponse> create(@Valid @RequestBody SessionRequest r){var s=service.save(null,r);return ResponseEntity.created(URI.create("/api/v1/timetable/sessions/"+s.id())).body(s);}
 @PutMapping("/sessions/{id}") public SessionResponse edit(@PathVariable @Positive Long id,@Valid @RequestBody SessionRequest r){return service.save(id,r);}
 @PatchMapping("/sessions/{id}/publish") public SessionResponse publish(@PathVariable @Positive Long id){return service.status(id,SessionStatus.PUBLISHED);}
 @PatchMapping("/sessions/{id}/cancel") public SessionResponse cancel(@PathVariable @Positive Long id){return service.status(id,SessionStatus.CANCELLED);}
 @PostMapping("/sessions/{id}/exceptions") public ResponseEntity<ExceptionResponse> exception(@PathVariable @Positive Long id,@Valid @RequestBody ExceptionRequest r){var result=service.exception(id,r);return ResponseEntity.created(URI.create("/api/v1/timetable/sessions/"+id+"/exceptions")).body(result);}
 @PutMapping("/terms/{id}/coverage") public CoverageResponse coverage(@PathVariable @Positive Long id,@Valid @RequestBody CoverageRequest r){return service.coverage(id,r);}
 @GetMapping("/faculty/me") public WeeklyTimetable faculty(@RequestParam(required=false) LocalDate weekStart){return reads.faculty(weekStart);}
 @GetMapping("/student/me") public WeeklyTimetable student(@RequestParam(required=false) LocalDate weekStart){return reads.student(weekStart);}
 @GetMapping("/sections/{id}") public WeeklyTimetable section(@PathVariable @Positive Long id,@RequestParam(required=false) LocalDate weekStart){return reads.section(id,weekStart);}
 @GetMapping("/unavailability") public PageResponse<BlockResponse> blocks(@RequestParam @Positive Long facultyId,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.blocks(facultyId,CatalogController.page(page,size));}
 @PostMapping("/unavailability") public ResponseEntity<BlockResponse> block(@Valid @RequestBody BlockRequest r){var result=service.block(r);return ResponseEntity.created(URI.create("/api/v1/timetable/unavailability/"+result.id())).body(result);}
 @PatchMapping("/unavailability/{id}/cancel") public BlockResponse cancelBlock(@PathVariable @Positive Long id){return service.cancelBlock(id);}
}
