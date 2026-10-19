package com.example.campusflow_backend.timetable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.*;
import com.example.campusflow_backend.dto.PageResponse;
import java.net.URI;
import static com.example.campusflow_backend.timetable.SchedulingDtos.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@RestController @RequestMapping("/api/v1")
public class CatalogController {
 private final CatalogService service;
 public CatalogController(CatalogService service){this.service=service;}
 static Pageable page(int page,int size){return PageRequest.of(page,size,Sort.by("id"));}
 @GetMapping("/subjects") public PageResponse<SubjectResponse> subjects(@RequestParam(required=false) @Size(max=150) String name,@RequestParam(required=false) @Positive Long departmentId,@RequestParam(required=false) Boolean active,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.subjects(name,departmentId,active,page(page,size));}
 @GetMapping("/subjects/{id}") public SubjectResponse subject(@PathVariable @Positive Long id){return service.subject(id);}
 @PostMapping("/subjects") public ResponseEntity<SubjectResponse> createSubject(@Valid @RequestBody SubjectRequest r){var s=service.saveSubject(null,r);return ResponseEntity.created(URI.create("/api/v1/subjects/"+s.id())).body(s);}
 @PutMapping("/subjects/{id}") public SubjectResponse updateSubject(@PathVariable @Positive Long id,@Valid @RequestBody SubjectRequest r){return service.saveSubject(id,r);}
 @PatchMapping("/subjects/{id}/status") public SubjectResponse subjectStatus(@PathVariable @Positive Long id,@Valid @RequestBody ActiveRequest r){return service.subjectStatus(id,r.active());}
 @GetMapping("/rooms") public PageResponse<RoomResponse> rooms(@RequestParam(required=false) @Size(max=150) String name,@RequestParam(required=false) @Size(max=100) String building,@RequestParam(required=false) RoomType type,@RequestParam(required=false) Boolean active,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.rooms(name,building,type,active,page(page,size));}
 @GetMapping("/rooms/{id}") public RoomResponse room(@PathVariable @Positive Long id){return service.room(id);}
 @PostMapping("/rooms") public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomRequest r){var room=service.saveRoom(null,r);return ResponseEntity.created(URI.create("/api/v1/rooms/"+room.id())).body(room);}
 @PutMapping("/rooms/{id}") public RoomResponse updateRoom(@PathVariable @Positive Long id,@Valid @RequestBody RoomRequest r){return service.saveRoom(id,r);}
 @PatchMapping("/rooms/{id}/status") public RoomResponse roomStatus(@PathVariable @Positive Long id,@Valid @RequestBody ActiveRequest r){return service.roomStatus(id,r.active());}
 @GetMapping("/timetable/terms") public PageResponse<TermResponse> terms(@RequestParam(required=false) @Positive Long departmentId,@RequestParam(required=false) Boolean active,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.terms(departmentId,active,page(page,size));}
 @GetMapping("/timetable/terms/{id}") public TermResponse term(@PathVariable @Positive Long id){return service.term(id);} @PostMapping("/timetable/terms") public ResponseEntity<TermResponse> term(@Valid @RequestBody TermRequest r){var t=service.createTerm(r);return ResponseEntity.created(URI.create("/api/v1/timetable/terms/"+t.id())).body(t);}
}
