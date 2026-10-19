package com.example.campusflow_backend.auth;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Component
public class RefreshTokenCodec {
    private final SecureRandom random=new SecureRandom();
    public String generate() { byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public String hash(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw new BadCredentialsException("Invalid refresh credential.");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable."); }
    }
}
