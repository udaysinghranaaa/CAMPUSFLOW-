package com.example.campusflow_backend.controller;

import com.example.campusflow_backend.dto.CampusHealthResponse;
import com.example.campusflow_backend.service.CampusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campus")
public class CampusController {

    private final CampusService campusService;

    public CampusController(CampusService campusService) {
        this.campusService = campusService;
    }

    @GetMapping("/health")
    public CampusHealthResponse getHealth() {
        return campusService.getHealth();
    }
}
