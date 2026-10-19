package com.example.campusflow_backend.controller;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.service.AcademicSectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/sections")
public class AcademicSectionController {
    private final AcademicSectionService service;
    public AcademicSectionController(AcademicSectionService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<SectionResponse> create(@Valid @RequestBody SectionRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/sections/" + response.id())).body(response);
    }
    @GetMapping
    public PageResponse<SectionResponse> list(@RequestParam(required = false) @Positive Long batchId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(batchId, PageRequest.of(page, size, Sort.by("id")));
    }
    @GetMapping("/{id}")
    public SectionResponse get(@PathVariable @Positive Long id) { return service.get(id); }
    @PutMapping("/{id}")
    public SectionResponse update(@PathVariable @Positive Long id, @Valid @RequestBody SectionRequest request) {
        return service.update(id, request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id); return ResponseEntity.noContent().build();
    }
}
