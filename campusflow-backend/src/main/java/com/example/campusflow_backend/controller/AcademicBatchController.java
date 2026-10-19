package com.example.campusflow_backend.controller;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.service.AcademicBatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/batches")
public class AcademicBatchController {
    private final AcademicBatchService service;
    public AcademicBatchController(AcademicBatchService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<BatchResponse> create(@Valid @RequestBody BatchRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/batches/" + response.id())).body(response);
    }
    @GetMapping
    public PageResponse<BatchResponse> list(@RequestParam(required = false) @Positive Long departmentId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(departmentId, PageRequest.of(page, size, Sort.by("id")));
    }
    @GetMapping("/{id}")
    public BatchResponse get(@PathVariable @Positive Long id) { return service.get(id); }
    @PutMapping("/{id}")
    public BatchResponse update(@PathVariable @Positive Long id, @Valid @RequestBody BatchRequest request) {
        return service.update(id, request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id); return ResponseEntity.noContent().build();
    }
}
