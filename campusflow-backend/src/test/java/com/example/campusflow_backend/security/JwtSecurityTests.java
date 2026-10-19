package com.example.campusflow_backend.security;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.repository.UserAccountRepository;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class JwtSecurityTests {
    final Clock clock=Clock.systemUTC(); JwtConfiguration configuration=new JwtConfiguration();
    SecretKey key;JwtEncoder encoder;JwtDecoder decoder;UserAccount account;
    @BeforeEach void setup() {
        byte[] bytes=new byte[64];new SecureRandom().nextBytes(bytes);key=configuration.jwtSigningKey(Base64.getEncoder().encodeToString(bytes));
        encoder=configuration.jwtEncoder(key);decoder=configuration.jwtDecoder(key,clock,"campusflow","campusflow-api");
        account=new UserAccount("one@example.test","not-used",AccountRole.ADMIN,null,null,clock.instant());ReflectionTestUtils.setField(account,"id",1L);
    }
    String token(String issuer,String audience,Instant expiry,MacAlgorithm algorithm) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(algorithm).build(),JwtClaimsSet.builder()
                .issuer(issuer).audience(List.of(audience)).subject("1").issuedAt(expiry.minusSeconds(600)).expiresAt(expiry)
                .claim("role","ADMIN").claim("ver",0).build())).getTokenValue();
    }
    @Test void validAccessHasStableMinimalClaims() {
        String raw=new JwtService(encoder,clock,"campusflow","campusflow-api",900).issue(account);
        Jwt decoded=decoder.decode(raw);assertThat(decoded.getSubject()).isEqualTo("1");
        assertThat(decoded.getClaims()).doesNotContainKeys("email","password","passwordHash","student");
    }
    @Test void expiredAccessRejected() {
        assertThatThrownBy(() -> decoder.decode(token("campusflow","campusflow-api",clock.instant().minusSeconds(120),MacAlgorithm.HS256)))
                .isInstanceOf(JwtException.class);
    }
    @Test void wrongIssuerAndAudienceRejected() {
        assertThatThrownBy(() -> decoder.decode(token("other","campusflow-api",clock.instant().plusSeconds(120),MacAlgorithm.HS256))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode(token("campusflow","other",clock.instant().plusSeconds(120),MacAlgorithm.HS256))).isInstanceOf(JwtException.class);
    }
    @Test void unexpectedAlgorithmRejected() {
        assertThatThrownBy(() -> decoder.decode(token("campusflow","campusflow-api",clock.instant().plusSeconds(120),MacAlgorithm.HS512)))
                .isInstanceOf(JwtException.class);
    }
    @Test void wrongSigningKeyRejected() {
        String raw=token("campusflow","campusflow-api",clock.instant().plusSeconds(120),MacAlgorithm.HS256);
        byte[] bytes=new byte[64];new SecureRandom().nextBytes(bytes);
        var other=configuration.jwtDecoder(configuration.jwtSigningKey(Base64.getEncoder().encodeToString(bytes)),clock,"campusflow","campusflow-api");
        assertThatThrownBy(() -> other.decode(raw)).isInstanceOf(JwtException.class);
    }
    @Test void unsignedAndMalformedRejected() {
        assertThatThrownBy(() -> decoder.decode("eyJhbGciOiJub25lIn0.e30.")).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> decoder.decode("invalid")).isInstanceOf(JwtException.class);
    }
    @Test void missingExpiryRejected() {
        String raw=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder()
                .issuer("campusflow").audience(List.of("campusflow-api")).subject("1").issuedAt(clock.instant()).build())).getTokenValue();
        assertThatThrownBy(() -> decoder.decode(raw)).isInstanceOf(JwtException.class);
    }
    @Test void enabledVersionAndRoleAreCheckedAgainstDatabase() {
        var repository=mock(UserAccountRepository.class);when(repository.findById(1L)).thenReturn(Optional.of(account));
        var converter=new AccountJwtConverter(repository);Jwt jwt=decoder.decode(token("campusflow","campusflow-api",clock.instant().plusSeconds(120),MacAlgorithm.HS256));
        assertThat(converter.convert(jwt).isAuthenticated()).isTrue();account.setEnabled(false);
        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
        account.setEnabled(true);
        assertThatThrownBy(() -> converter.convert(jwt)).isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
    }
    @Test void shortSigningKeyFailsSafely() {
        assertThatThrownBy(() -> configuration.jwtSigningKey(Base64.getEncoder().encodeToString(new byte[16]))).isInstanceOf(IllegalStateException.class);
    }
    @Test void signatureTamperingIsRejected() {
        String raw=token("campusflow","campusflow-api",clock.instant().plusSeconds(120),MacAlgorithm.HS256);
        String[] parts=raw.split("\\.");
        String first=parts[2].startsWith("A")?"B":"A";
        String tampered=parts[0]+"."+parts[1]+"."+first+parts[2].substring(1);
        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
    }
    @Test void signedRoleMismatchDoesNotElevatePermissions() {
        var repository=mock(UserAccountRepository.class);when(repository.findById(1L)).thenReturn(Optional.of(account));
        String raw=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder()
                .issuer("campusflow").audience(List.of("campusflow-api")).subject("1").issuedAt(clock.instant())
                .expiresAt(clock.instant().plusSeconds(120)).claim("role","STUDENT").claim("ver",0).build())).getTokenValue();
        assertThatThrownBy(() -> new AccountJwtConverter(repository).convert(decoder.decode(raw)))
                .isInstanceOf(org.springframework.security.oauth2.core.OAuth2AuthenticationException.class);
    }
}