package com.example.campusflow_backend.security;
import com.example.campusflow_backend.entity.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
@Service
public class JwtService {
    private final JwtEncoder encoder; private final Clock clock; private final String issuer,audience;
    private final long accessSeconds;
    public JwtService(JwtEncoder encoder,Clock clock,@Value("${campusflow.security.issuer}") String issuer,
            @Value("${campusflow.security.audience}") String audience,@Value("${campusflow.security.access-seconds}") long seconds) {
        if(seconds<60 || seconds>900) throw new IllegalStateException("Access lifetime must be 60..900 seconds.");
        this.encoder=encoder;this.clock=clock;this.issuer=issuer;this.audience=audience;this.accessSeconds=seconds;
    }
    public String issue(UserAccount account) {
        var now=clock.instant();
        var claims=JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject(account.getId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(accessSeconds)).id(UUID.randomUUID().toString())
                .claim("role",account.getRole().name()).claim("ver",account.getSecurityVersion()).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    public long accessSeconds() { return accessSeconds; }
}
