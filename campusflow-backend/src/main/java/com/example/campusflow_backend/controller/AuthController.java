package com.example.campusflow_backend.controller;
import com.example.campusflow_backend.auth.*;
import com.example.campusflow_backend.security.AccountPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;private final OwnProfileService profiles;
    public AuthController(AuthService auth,OwnProfileService profiles) { this.auth=auth;this.profiles=profiles; }
    @PostMapping("/login") public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.login(request));
    }
    @PostMapping("/refresh") public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.refresh(request));
    }
    @GetMapping("/me") public OwnProfileResponse me(@AuthenticationPrincipal AccountPrincipal principal) { return profiles.me(principal.id()); }
    @PostMapping("/logout") public ResponseEntity<Void> logout(@AuthenticationPrincipal AccountPrincipal principal,@Valid @RequestBody RefreshRequest request) {
        auth.logout(principal.id(),request);return ResponseEntity.noContent().build();
    }
    @PostMapping("/change-password") public ResponseEntity<Void> password(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest request) {
        auth.changePassword(principal.id(),request);return ResponseEntity.noContent().build();
    }
}
