package com.example.campusflow_backend.security;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import java.util.Arrays;
import java.util.List;
@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http,JwtDecoder decoder,
            AccountJwtConverter converter,SecurityErrors errors) throws Exception {
        return http.csrf(csrf -> csrf.disable()).cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable()).formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .exceptionHandling(handler -> handler.authenticationEntryPoint(errors.unauthorized()).accessDeniedHandler(errors.forbidden()))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET,"/api/campus/health").permitAll()
                    .requestMatchers(HttpMethod.POST,"/api/v1/auth/login","/api/v1/auth/refresh").permitAll()
                    .requestMatchers("/api/v1/auth/me","/api/v1/auth/logout","/api/v1/auth/change-password").authenticated()
                    .requestMatchers(HttpMethod.GET,"/api/v1/subjects","/api/v1/subjects/*","/api/v1/rooms","/api/v1/rooms/*",
                            "/api/v1/timetable/terms","/api/v1/timetable/terms/*","/api/v1/faculty-finder","/api/v1/faculty-finder/**").authenticated()
                    .requestMatchers(HttpMethod.GET,"/api/v1/timetable/faculty/me").hasRole("FACULTY")
                    .requestMatchers(HttpMethod.GET,"/api/v1/timetable/student/me").hasRole("STUDENT")
                    .requestMatchers(HttpMethod.GET,"/api/v1/timetable/sections/*").hasAnyRole("ADMIN","STUDENT")
                    .requestMatchers("/api/v1/timetable/unavailability","/api/v1/timetable/unavailability/*/cancel").hasAnyRole("ADMIN","FACULTY")
                    .requestMatchers("/api/v1/timetable/**","/api/v1/subjects/**","/api/v1/rooms/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET,"/api/v1/directory/faculty","/api/v1/departments","/api/v1/departments/*",
                            "/api/v1/batches","/api/v1/batches/*","/api/v1/sections","/api/v1/sections/*").authenticated()
                    .requestMatchers("/api/v1/admin/accounts/**","/api/v1/departments/**","/api/v1/faculty/**",
                            "/api/v1/batches/**","/api/v1/sections/**","/api/v1/students/**").hasRole("ADMIN")
                    .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.authenticationEntryPoint(errors.unauthorized()).accessDeniedHandler(errors.forbidden())
                    .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter))).build();
    }
    @Bean public CorsConfigurationSource corsConfigurationSource(
            @Value("${campusflow.security.cors-origins:http://localhost:5173}") String origins) {
        if(origins.contains("*")) throw new IllegalStateException("CORS requires explicit origins.");
        var config=new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::strip).toList());
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type"));config.setAllowCredentials(false);config.setMaxAge(3600L);
        var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",config);return source;
    }
}
