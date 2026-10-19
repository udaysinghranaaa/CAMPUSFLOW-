package com.example.campusflow_backend.auth;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.repository.UserAccountRepository;
import com.example.campusflow_backend.security.AccountPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringJUnitConfig(OwnershipTests.Config.class)
class OwnershipTests {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean UserAccountRepository accounts() { return mock(UserAccountRepository.class); }
        @Bean OwnProfileService profiles(UserAccountRepository accounts) { return new OwnProfileService(accounts); }
    }
    @Autowired OwnProfileService profiles; @Autowired UserAccountRepository accounts;
    @BeforeEach void authenticate() {
        reset(accounts);
        var authentication=UsernamePasswordAuthenticationToken.authenticated(new AccountPrincipal(1L,AccountRole.ADMIN,0),null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void ownProfileAllowed() {
        var account=new UserAccount("one@example.test","unused",AccountRole.ADMIN,null,null,Instant.now());ReflectionTestUtils.setField(account,"id",1L);
        when(accounts.findById(1L)).thenReturn(Optional.of(account));assertThat(profiles.me(1L).account().id()).isEqualTo(1L);
    }
    @Test void evenAdminCannotUseOwnProfileServiceForAnotherUser() {
        assertThatThrownBy(() -> profiles.me(2L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(accounts);
    }
}
