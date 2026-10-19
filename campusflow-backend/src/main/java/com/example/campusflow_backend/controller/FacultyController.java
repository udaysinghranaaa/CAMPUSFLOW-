package com.example.campusflow_backend.controller;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.service.FacultyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/faculty")
public class FacultyController {
    private final FacultyService faculty;

    public FacultyController(FacultyService faculty) { this.faculty = faculty; }

    @PostMapping
    public ResponseEntity<FacultyResponse> create(@Valid @RequestBody FacultyRequest request) {
        FacultyResponse response = faculty.create(request);
        return ResponseEntity.created(URI.create("/api/v1/faculty/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<FacultyResponse> list(
            @RequestParam(required = false) @Positive Long departmentId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @Size(max = 150) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return faculty.list(departmentId, active, name, PageRequest.of(page, size, Sort.by("id")));
    }

    @GetMapping("/{id}")
    public FacultyResponse get(@PathVariable @Positive Long id) { return faculty.get(id); }

    @PutMapping("/{id}")
    public FacultyResponse update(@PathVariable @Positive Long id, @Valid @RequestBody FacultyRequest request) {
        return faculty.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public FacultyResponse updateStatus(@PathVariable @Positive Long id,
                                        @Valid @RequestBody FacultyStatusRequest request) {
        return faculty.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        faculty.delete(id);
        return ResponseEntity.noContent().build();
    }
}
