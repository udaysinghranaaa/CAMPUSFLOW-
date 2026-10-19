package com.example.campusflow_backend.controller;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.service.DepartmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {
    private final DepartmentService departments;

    public DepartmentController(DepartmentService departments) { this.departments = departments; }

    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentRequest request) {
        DepartmentResponse response = departments.create(request);
        return ResponseEntity.created(URI.create("/api/v1/departments/" + response.id())).body(response);
    }

    @GetMapping
    public PageResponse<DepartmentResponse> list(
            @RequestParam(required = false) @Size(max = 20) String code,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return departments.list(code, PageRequest.of(page, size, Sort.by("id")));
    }

    @GetMapping("/{id}")
    public DepartmentResponse get(@PathVariable @Positive Long id) { return departments.get(id); }

    @PutMapping("/{id}")
    public DepartmentResponse update(@PathVariable @Positive Long id,
                                     @Valid @RequestBody DepartmentRequest request) {
        return departments.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        departments.delete(id);
        return ResponseEntity.noContent().build();
    }
}
