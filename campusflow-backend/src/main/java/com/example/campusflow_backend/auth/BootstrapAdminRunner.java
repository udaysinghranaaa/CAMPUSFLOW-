package com.example.campusflow_backend.auth;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="campusflow.bootstrap-admin",havingValue="true")
public class BootstrapAdminRunner implements ApplicationRunner {
    private final BootstrapAdminService service;
    public BootstrapAdminRunner(BootstrapAdminService service) { this.service=service; }
    @Override public void run(ApplicationArguments arguments) {
        if(!"none".equals(System.getProperty("spring.main.web-application-type")))
            throw new IllegalStateException("Bootstrap requires an explicit non-web JVM launch.");
        service.bootstrap(System.getenv("BOOTSTRAP_ADMIN_EMAIL"),System.getenv("BOOTSTRAP_ADMIN_PASSWORD"));
        System.out.println("Administrator bootstrap completed.");
        System.exit(0); // Explicit non-web administrative command only; commit has completed.
    }
}
