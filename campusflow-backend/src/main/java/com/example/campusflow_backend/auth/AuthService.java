package com.example.campusflow_backend.auth;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.repository.*;
import com.example.campusflow_backend.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.nio.charset.StandardCharsets;
@Service
public class AuthService {
    private final UserAccountRepository accounts;private final RefreshTokenRepository refreshTokens;
    private final AuthenticationManager authentication;private final PasswordEncoder encoder;
    private final RefreshTokenCodec codec;private final JwtService jwt;private final Clock clock;private final long refreshDays;
    public AuthService(UserAccountRepository accounts,RefreshTokenRepository refreshTokens,AuthenticationManager authentication,
            PasswordEncoder encoder,RefreshTokenCodec codec,JwtService jwt,Clock clock,
            @Value("${campusflow.security.refresh-days}") long days) {
        if(days<1 || days>30) throw new IllegalStateException("Refresh lifetime must be 1..30 days.");
        this.accounts=accounts;this.refreshTokens=refreshTokens;this.authentication=authentication;this.encoder=encoder;
        this.codec=codec;this.jwt=jwt;this.clock=clock;this.refreshDays=days;
    }
    @Transactional
    public TokenResponse login(LoginRequest request) {
        if(request.password()==null || request.password().getBytes(StandardCharsets.UTF_8).length>72) throw invalidLogin();
        // Acquire the same lock used by password/status updates before checking credentials.
        var account=accounts.lockByEmail(request.email());
        try { authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(request.email(),request.password())); }
        catch(AuthenticationException ex) { throw invalidLogin(); }
        var user=account.orElseThrow(AuthService::invalidLogin);
        if(!user.isEnabled()) throw invalidLogin();
        return issue(user);
    }
    @Transactional(noRollbackFor=BadCredentialsException.class)
    public TokenResponse refresh(RefreshRequest request) {
        String hash=codec.hash(request.refreshToken());
        Long userId=refreshTokens.findAccountIdByHash(hash).orElseThrow(AuthService::invalidRefresh);
        UserAccount user=accounts.lockById(userId).orElseThrow(AuthService::invalidRefresh);
        // Read the token after obtaining the account lock: a waiting request sees committed revocation.
        RefreshToken token=refreshTokens.findByTokenHash(hash).orElseThrow(AuthService::invalidRefresh);
        if(token.getRevokedAt()!=null) {
            refreshTokens.revokeAll(userId,clock.instant());user.invalidateAccess();accounts.saveAndFlush(user);
            // Deliberately commit replay revocation even though the caller receives 401.
            throw invalidRefresh();
        }
        if(!user.isEnabled() || !token.getExpiresAt().isAfter(clock.instant())) {
            token.revoke(clock.instant());refreshTokens.saveAndFlush(token);throw invalidRefresh();
        }
        token.revoke(clock.instant());refreshTokens.saveAndFlush(token);
        return issue(user);
    }
    @PreAuthorize("#accountId == authentication.principal.id")
    @Transactional
    public void logout(Long accountId,RefreshRequest request) {
        accounts.lockById(accountId).orElseThrow(AuthService::invalidRefresh);
        var token=refreshTokens.findByTokenHash(codec.hash(request.refreshToken())).orElseThrow(AuthService::invalidRefresh);
        if(!token.getUserAccount().getId().equals(accountId)) throw invalidRefresh();
        token.revoke(clock.instant());refreshTokens.saveAndFlush(token);
    }
    @PreAuthorize("#accountId == authentication.principal.id")
    @Transactional
    public void changePassword(Long accountId,PasswordChangeRequest request) {
        PasswordPolicy.validate(request.newPassword());
        var account=accounts.lockById(accountId).orElseThrow(AuthService::invalidLogin);
        if(request.currentPassword()==null || request.currentPassword().getBytes(StandardCharsets.UTF_8).length>72
                || !encoder.matches(request.currentPassword(),account.getPasswordHash())) throw invalidLogin();
        account.changePassword(encoder.encode(request.newPassword()),clock.instant());
        accounts.saveAndFlush(account);refreshTokens.revokeAll(accountId,clock.instant());
    }
    private TokenResponse issue(UserAccount account) {
        String raw=codec.generate();
        refreshTokens.saveAndFlush(new RefreshToken(account,codec.hash(raw),clock.instant().plus(Duration.ofDays(refreshDays))));
        return new TokenResponse("Bearer",jwt.issue(account),jwt.accessSeconds(),raw);
    }
    private static BadCredentialsException invalidLogin() { return new BadCredentialsException("Invalid credentials."); }
    private static BadCredentialsException invalidRefresh() { return new BadCredentialsException("Invalid refresh credential."); }
}
