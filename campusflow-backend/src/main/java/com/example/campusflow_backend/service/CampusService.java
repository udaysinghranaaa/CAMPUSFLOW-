package com.example.campusflow_backend.service;

import com.example.campusflow_backend.dto.CampusHealthResponse;
import org.springframework.stereotype.Service;

@Service
public class CampusService {

    public CampusHealthResponse getHealth() {
        return new CampusHealthResponse(
                "UP",
                "CampusFlow",
                "CampusFlow Backend is Running!"
        );
    }
}
