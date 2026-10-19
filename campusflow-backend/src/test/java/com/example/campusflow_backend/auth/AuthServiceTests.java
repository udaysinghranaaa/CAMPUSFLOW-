package com.example.campusflow_backend.auth;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.repository.*;
import com.example.campusflow_backend.security.JwtService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class AuthServiceTests {
    @Mock UserAccountRepository accounts; @Mock RefreshTokenRepository tokens;
    @Mock AuthenticationManager authentication; @Mock JwtService jwt;
    final RefreshTokenCodec codec=new RefreshTokenCodec(); final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
    final Clock clock=Clock.systemUTC(); AuthService service; UserAccount user; String password;
    @BeforeEach void setup() {
        password=UUID.randomUUID().toString();
        user=new UserAccount("admin@example.test",encoder.encode(password),AccountRole.ADMIN,null,null,clock.instant());
        ReflectionTestUtils.setField(user,"id",1L);
        service=new AuthService(accounts,tokens,authentication,encoder,codec,jwt,clock,7);
    }
    void issueStubs() { when(jwt.issue(user)).thenReturn(codec.generate());when(jwt.accessSeconds()).thenReturn(900L); }
    @Test void loginIssuesOnlyHashedRefreshPersistence() {
        when(accounts.lockByEmail(user.getEmail())).thenReturn(Optional.of(user));issueStubs();
        var response=service.login(new LoginRequest(user.getEmail(),password));
        var saved=org.mockito.ArgumentCaptor.forClass(RefreshToken.class);verify(tokens).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getTokenHash().equals(codec.hash(response.refreshToken()))).isTrue();
        assertThat(saved.getValue().getTokenHash().equals(response.refreshToken())).isFalse();
        assertThat(response.toString()).isEqualTo("TokenResponse[REDACTED]");
    }
    @Test void wrongPasswordAndUnknownEmailAreGeneric() {
        when(authentication.authenticate(any())).thenThrow(new BadCredentialsException("internal detail"));
        assertThatThrownBy(() -> service.login(new LoginRequest(user.getEmail(),password))).isInstanceOf(BadCredentialsException.class).hasMessage("Invalid credentials.");
        assertThatThrownBy(() -> service.login(new LoginRequest("unknown@example.test",password))).isInstanceOf(BadCredentialsException.class).hasMessage("Invalid credentials.");
        verifyNoInteractions(tokens);
    }
    @Test void disabledAccountCannotLogin() {
        user.setEnabled(false);when(accounts.lockByEmail(user.getEmail())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.login(new LoginRequest(user.getEmail(),password))).isInstanceOf(BadCredentialsException.class);
    }
    @Test void refreshRotatesAndRevokesOldToken() {
        String raw=codec.generate();var old=new RefreshToken(user,codec.hash(raw),clock.instant().plusSeconds(600));
        when(tokens.findAccountIdByHash(codec.hash(raw))).thenReturn(Optional.of(1L));
        when(accounts.lockById(1L)).thenReturn(Optional.of(user));when(tokens.findByTokenHash(codec.hash(raw))).thenReturn(Optional.of(old));issueStubs();
        var result=service.refresh(new RefreshRequest(raw));
        assertThat(old.getRevokedAt()).isNotNull();assertThat(result.refreshToken().equals(raw)).isFalse();
        verify(tokens,times(2)).saveAndFlush(any());
    }
    @Test void replayRevokesAllSessionsAndInvalidatesAccess() {
        String raw=codec.generate();var old=new RefreshToken(user,codec.hash(raw),clock.instant().plusSeconds(600));old.revoke(clock.instant());
        when(tokens.findAccountIdByHash(codec.hash(raw))).thenReturn(Optional.of(1L));when(accounts.lockById(1L)).thenReturn(Optional.of(user));
        when(tokens.findByTokenHash(codec.hash(raw))).thenReturn(Optional.of(old));
        assertThatThrownBy(() -> service.refresh(new RefreshRequest(raw))).isInstanceOf(BadCredentialsException.class);
        verify(tokens).revokeAll(eq(1L),any());verify(accounts).saveAndFlush(user);assertThat(user.getSecurityVersion()).isEqualTo(1);
    }
    @Test void expiredRefreshIsRevokedAndRejected() {
        String raw=codec.generate();var old=new RefreshToken(user,codec.hash(raw),clock.instant().minusSeconds(1));
        when(tokens.findAccountIdByHash(codec.hash(raw))).thenReturn(Optional.of(1L));when(accounts.lockById(1L)).thenReturn(Optional.of(user));
        when(tokens.findByTokenHash(codec.hash(raw))).thenReturn(Optional.of(old));
        assertThatThrownBy(() -> service.refresh(new RefreshRequest(raw))).isInstanceOf(BadCredentialsException.class);
        assertThat(old.getRevokedAt()).isNotNull();
    }
    @Test void logoutCannotRevokeAnotherAccountsToken() {
        String raw=codec.generate();UserAccount other=new UserAccount("other@example.test",encoder.encode(password),AccountRole.ADMIN,null,null,clock.instant());
        ReflectionTestUtils.setField(other,"id",2L);var token=new RefreshToken(other,codec.hash(raw),clock.instant().plusSeconds(60));
        when(accounts.lockById(1L)).thenReturn(Optional.of(user));when(tokens.findByTokenHash(codec.hash(raw))).thenReturn(Optional.of(token));
        assertThatThrownBy(() -> service.logout(1L,new RefreshRequest(raw))).isInstanceOf(BadCredentialsException.class);
        assertThat(token.getRevokedAt()).isNull();
    }
    @Test void logoutRevokesSuppliedSession() {
        String raw=codec.generate();var token=new RefreshToken(user,codec.hash(raw),clock.instant().plusSeconds(60));
        when(accounts.lockById(1L)).thenReturn(Optional.of(user));when(tokens.findByTokenHash(codec.hash(raw))).thenReturn(Optional.of(token));
        service.logout(1L,new RefreshRequest(raw));assertThat(token.getRevokedAt()).isNotNull();
    }
    @Test void passwordChangeRevokesSessionsAndVersionsAccess() {
        when(accounts.lockById(1L)).thenReturn(Optional.of(user));String replacement=UUID.randomUUID().toString();
        service.changePassword(1L,new PasswordChangeRequest(password,replacement));
        assertThat(encoder.matches(replacement,user.getPasswordHash())).isTrue();assertThat(user.getSecurityVersion()).isEqualTo(1);
        verify(tokens).revokeAll(eq(1L),any());
    }
    @Test void wrongCurrentPasswordDoesNotChangeAccount() {
        when(accounts.lockById(1L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.changePassword(1L,new PasswordChangeRequest(UUID.randomUUID().toString(),password)))
                .isInstanceOf(BadCredentialsException.class);verifyNoInteractions(tokens);assertThat(user.getSecurityVersion()).isZero();
    }
    @Test void passwordsRespectBcryptByteLimit() {
        assertThatThrownBy(() -> PasswordPolicy.validate("a".repeat(11))).isInstanceOf(com.example.campusflow_backend.exception.InvalidRequestException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("\u20ac".repeat(25))).isInstanceOf(com.example.campusflow_backend.exception.InvalidRequestException.class);
    }
    @Test void refreshValuesAreRandomAndNeverEqualStoredHash() {
        String first=codec.generate(),second=codec.generate();assertThat(first.equals(second)).isFalse();
        assertThat(first.length()).isEqualTo(43);assertThat(codec.hash(first).length()).isEqualTo(64);
        assertThat(new LoginRequest("one@example.test",password).toString()).isEqualTo("LoginRequest[REDACTED]");
    }
}
