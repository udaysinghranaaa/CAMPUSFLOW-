package com.example.campusflow_backend.support;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.Base64;
/** Ephemeral test-only key, never a tracked credential or JVM system property. Filters stay enabled. */

public abstract class SecurityIntegrationSupport {
    private static final String KEY=createKey();
    private static String createKey() { byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes); }
    @DynamicPropertySource static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("campusflow.security.jwt-secret",() -> KEY);
        registry.add("spring.test.mockmvc.print",() -> "none");
    }
}
