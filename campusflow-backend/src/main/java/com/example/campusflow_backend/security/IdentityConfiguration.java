package com.example.campusflow_backend.security;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Clock;
@Configuration
public class IdentityConfiguration {
    @Bean public Clock clock() { return Clock.systemUTC(); }
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean public AuthenticationManager authenticationManager(AccountDetailsService details,PasswordEncoder encoder) {
        var provider=new DaoAuthenticationProvider(details);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
}
