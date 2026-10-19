package com.example.campusflow_backend.security;
import com.example.campusflow_backend.dto.ApiError;
import jakarta.servlet.http.*;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
@Component
public class SecurityErrors {
    private final ObjectMapper mapper;
    public SecurityErrors(ObjectMapper mapper) { this.mapper=mapper; }
    public AuthenticationEntryPoint unauthorized() { return (request,response,error) -> {
        response.setHeader("WWW-Authenticate","Bearer"); write(request,response,401,"Unauthorized","Authentication required or invalid credentials.");
    }; }
    public AccessDeniedHandler forbidden() { return (request,response,error) ->
            write(request,response,403,"Forbidden","Access denied."); }
    private void write(HttpServletRequest request,HttpServletResponse response,int status,String title,String message) throws IOException {
        response.setStatus(status);response.setContentType("application/json");response.setHeader("Cache-Control","no-store");
        mapper.writeValue(response.getOutputStream(),new ApiError(Instant.now(),status,title,message,request.getRequestURI(),Map.of()));
    }
}
