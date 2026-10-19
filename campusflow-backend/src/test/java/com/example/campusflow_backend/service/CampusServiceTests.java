package com.example.campusflow_backend.service;

import com.example.campusflow_backend.dto.CampusHealthResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CampusServiceTests {

    private final CampusService campusService = new CampusService();

    @Test
    void getHealthReturnsExpectedCampusStatus() {
        assertThat(campusService.getHealth()).isEqualTo(new CampusHealthResponse(
                "UP",
                "CampusFlow",
                "CampusFlow Backend is Running!"
        ));
    }
}
