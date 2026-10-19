package com.example.campusflow_backend.security;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.*;
import java.util.*;
@Configuration
public class JwtConfiguration {
    @Bean public SecretKey jwtSigningKey(@Value("${campusflow.security.jwt-secret}") String encoded) {
        byte[] bytes;
        try { bytes=Base64.getDecoder().decode(encoded); }
        catch(IllegalArgumentException ex) { throw new IllegalStateException("JWT_SECRET must be valid Base64 random key material."); }
        if(bytes.length<32) throw new IllegalStateException("JWT_SECRET requires at least 32 random bytes.");
        return new SecretKeySpec(bytes,"HmacSHA256");
    }
    @Bean public JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean public JwtDecoder jwtDecoder(SecretKey key,Clock clock,
            @Value("${campusflow.security.issuer}") String issuer,@Value("${campusflow.security.audience}") String audience) {
        var decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var time=new JwtTimestampValidator(Duration.ofSeconds(30)); time.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time,new JwtIssuerValidator(issuer),
                new JwtClaimValidator<List<String>>("aud",values -> values!=null && values.contains(audience)),
                new JwtClaimValidator<Instant>("exp",Objects::nonNull),
                new JwtClaimValidator<Instant>("iat",value -> value!=null && !value.isAfter(clock.instant().plusSeconds(30)))));
        return decoder;
    }
}
