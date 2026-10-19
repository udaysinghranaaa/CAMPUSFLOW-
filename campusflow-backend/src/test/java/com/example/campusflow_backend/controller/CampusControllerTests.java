package com.example.campusflow_backend.controller;

import com.example.campusflow_backend.service.CampusService;
import org.junit.jupiter.api.Test;
import com.example.campusflow_backend.support.SecurityWebSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CampusController.class)
@Import(CampusService.class)
@SecurityWebSupport
class CampusControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsOkWithExpectedJson() throws Exception {
        mockMvc.perform(get("/api/campus/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(3)))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("CampusFlow"))
                .andExpect(jsonPath("$.message").value("CampusFlow Backend is Running!"));
    }
}
