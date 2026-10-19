package com.example.campusflow_backend.controller;
import com.example.campusflow_backend.auth.*;
import com.example.campusflow_backend.dto.PageResponse;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/directory/faculty")
public class FacultyDirectoryController {
    private final FacultyDirectoryService directory;
    public FacultyDirectoryController(FacultyDirectoryService directory) { this.directory=directory; }
    @GetMapping public PageResponse<FacultyDirectoryResponse> list(@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return directory.list(PageRequest.of(page,size,Sort.by("id")));
    }
}
