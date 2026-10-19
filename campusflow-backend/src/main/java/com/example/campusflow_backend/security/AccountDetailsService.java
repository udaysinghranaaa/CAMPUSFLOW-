package com.example.campusflow_backend.security;
import com.example.campusflow_backend.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AccountDetailsService implements UserDetailsService {
    private final UserAccountRepository accounts;
    public AccountDetailsService(UserAccountRepository accounts) { this.accounts=accounts; }
    @Override @Transactional(readOnly=true)
    public UserDetails loadUserByUsername(String email) {
        var account=accounts.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));
        return User.withUsername(account.getEmail()).password(account.getPasswordHash()).roles(account.getRole().name())
                .disabled(!account.isEnabled()).build();
    }
}
