package com.example.campusflow_backend.integration;
import com.example.campusflow_backend.auth.*;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.repository.*;
import com.example.campusflow_backend.security.*;
import com.example.campusflow_backend.support.SecurityIntegrationSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.*;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Committed per-test fixtures are needed for locking/concurrency. Cleanup deletes only captured IDs. */
@SpringBootTest
@AutoConfigureMockMvc
@WithAnonymousUser
class SecurityPostgreSqlIT extends SecurityIntegrationSupport {
    @Autowired UserAccountRepository accounts; @Autowired RefreshTokenRepository tokens;
    @Autowired DepartmentRepository departments; @Autowired FacultyRepository faculty;
    @Autowired StudentRepository students; @Autowired AcademicBatchRepository batches;
    @Autowired AuthService auth; @Autowired AccountService administration;
    @Autowired OwnProfileService profiles; @Autowired JwtService jwt; @Autowired RefreshTokenCodec codec;
    @Autowired PasswordEncoder encoder; @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper; @Autowired PlatformTransactionManager transactionManager;
    UserAccount admin,studentAccount,facultyAccount; Department department;Faculty member;AcademicBatch batch;Student student;
    String password; final List<Long> accountIds=new ArrayList<>();
    @BeforeEach void setup() {
        password=UUID.randomUUID().toString();String hash=encoder.encode(password);String name=UUID.randomUUID().toString().replace("-","").substring(0,14);
        department=departments.saveAndFlush(new Department("Security IT",name.toUpperCase(Locale.ROOT),null));
        member=faculty.saveAndFlush(new Faculty("Teacher",name+"f@example.test",name.toUpperCase(Locale.ROOT),"Professor",department));
        batch=batches.saveAndFlush(new AcademicBatch("Security IT",2023,2027,department,true));
        student=students.saveAndFlush(new Student(name.toUpperCase(Locale.ROOT),name.toUpperCase(Locale.ROOT),"Student",name+"s@example.test",
                null,department,batch,null,1,2023));
        admin=save(new UserAccount(name+"a@example.test",hash,AccountRole.ADMIN,null,null,Instant.now()));
        facultyAccount=save(new UserAccount(name+"f@example.test",hash,AccountRole.FACULTY,member,null,Instant.now()));
        studentAccount=save(new UserAccount(name+"s@example.test",hash,AccountRole.STUDENT,null,student,Instant.now()));
    }
    UserAccount save(UserAccount user) { var saved=accounts.saveAndFlush(user);accountIds.add(saved.getId());return saved; }
    TokenResponse login(UserAccount account) { return auth.login(new LoginRequest(account.getEmail(),password)); }
    void asAdmin() { SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            new AccountPrincipal(admin.getId(),AccountRole.ADMIN,admin.getSecurityVersion()),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))); }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        // Exact owned IDs only, child rows first. Never truncate or delete by broad role/email patterns.
        for(Long id:accountIds) { jdbc.update("delete from refresh_tokens where user_account_id=?",id);jdbc.update("delete from user_accounts where id=?",id); }
        if(student!=null) jdbc.update("delete from students where id=?",student.getId());
        if(batch!=null) jdbc.update("delete from academic_batches where id=?",batch.getId());
        if(member!=null) jdbc.update("delete from faculty where id=?",member.getId());
        if(department!=null) jdbc.update("delete from departments where id=?",department.getId());
    }
    @Test void v3AndPasswordHashesUseRealDatabase() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version='3' and success",Long.class)).isEqualTo(1);
        assertThat(encoder.matches(password,accounts.findById(admin.getId()).orElseThrow().getPasswordHash())).isTrue();
    }
    @Test void validLoginAndMeUseActualJwtAndOwnLink() throws Exception {
        String body=mapper.writeValueAsString(new LoginRequest(studentAccount.getEmail(),password));
        var result=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
        TokenResponse credential=mapper.readValue(result.getResponse().getContentAsString(),TokenResponse.class);
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+credential.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.student.id").value(student.getId()))
                .andExpect(jsonPath("$.account.passwordHash").doesNotExist());
    }
    @Test void wrongUnknownAndDisabledCredentialsAreGeneric() {
        String wrong=UUID.randomUUID().toString();
        assertThatThrownBy(() -> auth.login(new LoginRequest(admin.getEmail(),wrong))).isInstanceOf(BadCredentialsException.class).hasMessage("Invalid credentials.");
        assertThatThrownBy(() -> auth.login(new LoginRequest(UUID.randomUUID()+"@example.test",wrong))).isInstanceOf(BadCredentialsException.class).hasMessage("Invalid credentials.");
        asAdmin();administration.status(studentAccount.getId(),admin.getId(),new AccountStatusRequest(false));
        assertThatThrownBy(() -> login(studentAccount)).isInstanceOf(BadCredentialsException.class).hasMessage("Invalid credentials.");
    }
    @Test void rotationStoresOnlyHashesAndOldTokenIsRevoked() {
        var first=login(admin);var second=auth.refresh(new RefreshRequest(first.refreshToken()));
        var old=tokens.findByTokenHash(codec.hash(first.refreshToken())).orElseThrow();assertThat(old.getRevokedAt()).isNotNull();
        assertThat(second.refreshToken().equals(first.refreshToken())).isFalse();
        assertThat(tokens.findByTokenHash(codec.hash(second.refreshToken())).isPresent()).isTrue();
    }
    @Test void replayRevocationCommitsAndRejectsSuccessor() throws Exception {
        var first=login(admin);var second=auth.refresh(new RefreshRequest(first.refreshToken()));
        assertThatThrownBy(() -> auth.refresh(new RefreshRequest(first.refreshToken()))).isInstanceOf(BadCredentialsException.class);
        assertThat(tokens.findByTokenHash(codec.hash(second.refreshToken())).orElseThrow().getRevokedAt()).isNotNull();
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+second.accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(new RefreshRequest(second.refreshToken()))).isInstanceOf(BadCredentialsException.class);
    }
    @Test void concurrentRefreshHasOneWinnerAndStrictReplayRevokesIt() throws Exception {
        var first=login(admin);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> call=() -> { start.await();try { auth.refresh(new RefreshRequest(first.refreshToken()));return true; }
                catch(BadCredentialsException expected) { return false; } };
            var one=pool.submit(call);var two=pool.submit(call);start.countDown();
            assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_account_id=? and revoked_at is null",Long.class,admin.getId())).isZero();
        } finally { pool.shutdownNow(); }
    }
    @Test void logoutRevokesSessionAndCannotRevokeSomeoneElses() throws Exception {
        var first=login(admin);var other=login(studentAccount);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+first.accessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RefreshRequest(other.refreshToken())))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+first.accessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new RefreshRequest(first.refreshToken())))).andExpect(status().isNoContent());
        assertThatThrownBy(() -> auth.refresh(new RefreshRequest(first.refreshToken()))).isInstanceOf(BadCredentialsException.class);
    }
    @Test void passwordChangeRevokesAllRefreshAndAccess() throws Exception {
        var first=login(studentAccount);var second=login(studentAccount);String replacement=UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization","Bearer "+first.accessToken()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new PasswordChangeRequest(password,replacement)))).andExpect(status().isNoContent());
        assertThat(tokens.findByTokenHash(codec.hash(first.refreshToken())).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(tokens.findByTokenHash(codec.hash(second.refreshToken())).orElseThrow().getRevokedAt()).isNotNull();
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+second.accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> login(studentAccount)).isInstanceOf(BadCredentialsException.class);
        assertThat(auth.login(new LoginRequest(studentAccount.getEmail(),replacement)).expiresIn()).isEqualTo(900);
    }
    @Test void disablingAndReenablingCannotRestoreOldAccess() throws Exception {
        var first=login(studentAccount);asAdmin();administration.status(studentAccount.getId(),admin.getId(),new AccountStatusRequest(false));
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+first.accessToken())).andExpect(status().isUnauthorized());
        asAdmin();administration.status(studentAccount.getId(),admin.getId(),new AccountStatusRequest(true));
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+first.accessToken())).andExpect(status().isUnauthorized());
    }
    @Test void profileOwnershipAndRoleBoundaries() throws Exception {
        for(UserAccount user:List.of(studentAccount,facultyAccount)) {
            var token=login(user);
            mvc.perform(get("/api/v1/students/"+student.getId()).header("Authorization","Bearer "+token.accessToken())).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token.accessToken())).andExpect(status().isOk());
            mvc.perform(get("/api/v1/admin/accounts").header("Authorization","Bearer "+token.accessToken())).andExpect(status().isForbidden());
        }
        asAdmin();assertThatThrownBy(() -> profiles.me(studentAccount.getId())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void databaseRejectsInconsistentRoleLinks() {
        var bad=new UserAccount(UUID.randomUUID()+"@example.test",encoder.encode(password),AccountRole.STUDENT,null,null,Instant.now());
        var transaction=new TransactionTemplate(transactionManager);
        Throwable failure=catchThrowable(() -> transaction.execute(status -> accounts.saveAndFlush(bad)));
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        var root=((DataIntegrityViolationException)failure).getMostSpecificCause();assertThat(root).isInstanceOf(PSQLException.class);
        assertThat(((PSQLException)root).getServerErrorMessage().getConstraint()).isEqualTo("ck_account_role_link");
    }
    @Test void databaseRejectsDuplicateEmailAndDuplicateProfile() {
        var tx=new TransactionTemplate(transactionManager);
        var duplicate=new UserAccount(admin.getEmail(),encoder.encode(password),AccountRole.ADMIN,null,null,Instant.now());
        assertThatThrownBy(() -> tx.execute(status -> accounts.saveAndFlush(duplicate))).isInstanceOf(DataIntegrityViolationException.class);
        var duplicateLink=new UserAccount(UUID.randomUUID()+"@example.test",encoder.encode(password),AccountRole.STUDENT,null,student,Instant.now());
        assertThatThrownBy(() -> tx.execute(status -> accounts.saveAndFlush(duplicateLink))).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void adminAcademicOperationsStillWork() throws Exception {
        var adminToken=login(admin);
        mvc.perform(get("/api/v1/students/"+student.getId()).header("Authorization","Bearer "+adminToken.accessToken())).andExpect(status().isOk());
        mvc.perform(get("/api/campus/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/students/"+student.getId())).andExpect(status().isUnauthorized());
    }
    @Test void expiredPersistedRefreshIsRejectedAndRevoked() {
        var first=login(studentAccount);
        jdbc.update("update refresh_tokens set created_at=?, expires_at=? where token_hash=?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(8))),
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)),codec.hash(first.refreshToken()));
        assertThatThrownBy(() -> auth.refresh(new RefreshRequest(first.refreshToken()))).isInstanceOf(BadCredentialsException.class);
        assertThat(tokens.findByTokenHash(codec.hash(first.refreshToken())).orElseThrow().getRevokedAt()).isNotNull();
    }
}