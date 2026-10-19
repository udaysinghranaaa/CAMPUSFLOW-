package com.example.campusflow_backend.controller;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.entity.StudentStatus;
import com.example.campusflow_backend.service.StudentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/students")
public class StudentController {
    private final StudentService students;
    public StudentController(StudentService students) { this.students = students; }
    @PostMapping
    public ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentRequest request) {
        var response = students.create(request);
        return ResponseEntity.created(URI.create("/api/v1/students/" + response.id())).body(response);
    }
    @GetMapping
    public PageResponse<StudentResponse> list(
            @RequestParam(required = false) @Size(max = 150) String name,
            @RequestParam(required = false) @Size(max = 40) String enrollmentNumber,
            @RequestParam(required = false) @Positive Long departmentId,
            @RequestParam(required = false) @Positive Long batchId,
            @RequestParam(required = false) @Positive Long sectionId,
            @RequestParam(required = false) @Positive Integer semester,
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return students.list(name, enrollmentNumber, departmentId, batchId, sectionId, semester, status,
                PageRequest.of(page, size, Sort.by("id")));
    }
    @GetMapping("/{id}")
    public StudentResponse get(@PathVariable @Positive Long id) { return students.get(id); }
    @PutMapping("/{id}")
    public StudentResponse update(@PathVariable @Positive Long id, @Valid @RequestBody StudentRequest request) {
        return students.update(id, request);
    }
    @PatchMapping("/{id}/status")
    public StudentResponse status(@PathVariable @Positive Long id, @Valid @RequestBody StudentStatusRequest request) {
        return students.updateStatus(id, request);
    }
}
