package com.example.campusflow_backend.auth;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.repository.UserAccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.Locale;
@Service
public class BootstrapAdminService {
    private final UserAccountRepository accounts;private final JdbcTemplate jdbc;private final PasswordEncoder encoder;private final Clock clock;
    public BootstrapAdminService(UserAccountRepository accounts,JdbcTemplate jdbc,PasswordEncoder encoder,Clock clock) {
        this.accounts=accounts;this.jdbc=jdbc;this.encoder=encoder;this.clock=clock;
    }
    @Transactional
    public void bootstrap(String email,String password) {
        PasswordPolicy.validate(password);
        if(email==null || email.length()>254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalStateException("A valid bootstrap email is required.");
        jdbc.execute("select pg_advisory_xact_lock(70704001)");
        if(accounts.existsByRole(AccountRole.ADMIN)) throw new IllegalStateException("Bootstrap refused: an administrator already exists.");
        String normalized=email.strip().toLowerCase(Locale.ROOT);
        if(accounts.existsByEmail(normalized)) throw new IllegalStateException("Bootstrap refused: account email already exists.");
        accounts.saveAndFlush(new UserAccount(normalized,encoder.encode(password),AccountRole.ADMIN,null,null,clock.instant()));
    }
}
