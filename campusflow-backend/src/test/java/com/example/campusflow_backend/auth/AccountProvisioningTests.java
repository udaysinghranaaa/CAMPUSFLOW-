package com.example.campusflow_backend.auth;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.exception.*;
import com.example.campusflow_backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class AccountProvisioningTests {
    @Mock UserAccountRepository accounts;@Mock StudentRepository students;@Mock FacultyRepository faculty;
    @Mock RefreshTokenRepository refresh;@Mock JdbcTemplate jdbc;
    final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);final Clock clock=Clock.systemUTC();
    AccountService service;String password;
    @BeforeEach void setup() { password=UUID.randomUUID().toString();service=new AccountService(accounts,students,faculty,refresh,encoder,clock); }
    @Test void studentProvisioningLinksProfileAndHashesPassword() {
        Student student=mock(Student.class);when(student.getId()).thenReturn(2L);when(students.findById(2L)).thenReturn(Optional.of(student));
        when(accounts.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result=service.provision(new ProvisionAccountRequest(" LOGIN@example.test ",password,AccountRole.STUDENT,2L,null));
        assertThat(result.email()).isEqualTo("login@example.test");assertThat(result.studentId()).isEqualTo(2L);
        var saved=org.mockito.ArgumentCaptor.forClass(UserAccount.class);verify(accounts).saveAndFlush(saved.capture());
        assertThat(encoder.matches(password,saved.getValue().getPasswordHash())).isTrue();
    }
    @Test void administratorsCannotBeProvisionedThroughApi() {
        assertThatThrownBy(() -> service.provision(new ProvisionAccountRequest("admin@example.test",password,AccountRole.ADMIN,null,null)))
                .isInstanceOf(InvalidRequestException.class);verifyNoInteractions(accounts);
    }
    @Test void inconsistentRoleLinksAreRejected() {
        assertThatThrownBy(() -> service.provision(new ProvisionAccountRequest("one@example.test",password,AccountRole.STUDENT,2L,3L)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.provision(new ProvisionAccountRequest("one@example.test",password,AccountRole.FACULTY,null,null)))
                .isInstanceOf(InvalidRequestException.class);
    }
    @Test void missingAndAlreadyLinkedProfilesRejected() {
        var request=new ProvisionAccountRequest("one@example.test",password,AccountRole.STUDENT,2L,null);
        assertThatThrownBy(() -> service.provision(request)).isInstanceOf(ResourceNotFoundException.class);
        when(students.findById(2L)).thenReturn(Optional.of(mock(Student.class)));when(accounts.existsByStudentId(2L)).thenReturn(true);
        assertThatThrownBy(() -> service.provision(request)).isInstanceOf(ConflictException.class);
    }
    @Test void duplicateEmailRejectedBeforeSaving() {
        when(accounts.existsByEmail("one@example.test")).thenReturn(true);
        assertThatThrownBy(() -> service.provision(new ProvisionAccountRequest("one@example.test",password,AccountRole.FACULTY,null,2L)))
                .isInstanceOf(ConflictException.class);verify(accounts,never()).saveAndFlush(any());
    }
    @Test void accountStatusInvalidatesSecurityVersionAndRefresh() {
        var target=new UserAccount("one@example.test",encoder.encode(password),AccountRole.ADMIN,null,null,clock.instant());
        ReflectionTestUtils.setField(target,"id",2L);when(accounts.lockById(2L)).thenReturn(Optional.of(target));
        assertThat(service.status(2L,1L,new AccountStatusRequest(false)).enabled()).isFalse();
        assertThat(target.getSecurityVersion()).isEqualTo(1);verify(refresh).revokeAll(eq(2L),any());
        assertThatThrownBy(() -> service.status(1L,1L,new AccountStatusRequest(false))).isInstanceOf(ConflictException.class);
    }
    @Test void bootstrapRefusesToOverwriteAnExistingAdministrator() {
        when(accounts.existsByRole(AccountRole.ADMIN)).thenReturn(true);
        var bootstrap=new BootstrapAdminService(accounts,jdbc,encoder,clock);
        assertThatThrownBy(() -> bootstrap.bootstrap("admin@example.test",password)).isInstanceOf(IllegalStateException.class);
        verify(accounts,never()).saveAndFlush(any());
    }
}
