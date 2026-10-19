package com.example.campusflow_backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("database")
@SpringBootTest
@org.springframework.security.test.context.support.WithMockUser(roles="ADMIN")
class CampusflowBackendApplicationTests extends com.example.campusflow_backend.support.SecurityIntegrationSupport {

	@Test
	void contextLoads() {
	}

}
