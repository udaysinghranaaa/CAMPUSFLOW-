package com.example.campusflow_backend.auth;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.exception.*;
import com.example.campusflow_backend.repository.*;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.campusflow_backend.dto.PageResponse;
import java.time.Clock;
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly=true)
public class AccountService {
    private final UserAccountRepository accounts;private final StudentRepository students;private final FacultyRepository faculty;
    private final RefreshTokenRepository refreshTokens;private final PasswordEncoder encoder;private final Clock clock;
    public AccountService(UserAccountRepository accounts,StudentRepository students,FacultyRepository faculty,
            RefreshTokenRepository refreshTokens,PasswordEncoder encoder,Clock clock) {
        this.accounts=accounts;this.students=students;this.faculty=faculty;this.refreshTokens=refreshTokens;this.encoder=encoder;this.clock=clock;
    }
    @Transactional
    public AccountResponse provision(ProvisionAccountRequest request) {
        PasswordPolicy.validate(request.initialPassword());
        if(request.role()==null || request.role()==AccountRole.ADMIN) throw new InvalidRequestException("ADMIN accounts require manual bootstrap.");
        if(accounts.existsByEmail(request.email())) throw new ConflictException("Account email already exists.");
        Student student=null;Faculty member=null;
        if(request.role()==AccountRole.STUDENT) {
            if(request.studentId()==null || request.facultyId()!=null) throw new InvalidRequestException("STUDENT requires only a student link.");
            student=students.findById(request.studentId()).orElseThrow(() -> new ResourceNotFoundException("Student not found."));
            if(accounts.existsByStudentId(request.studentId())) throw new ConflictException("Student already has an account.");
        } else {
            if(request.facultyId()==null || request.studentId()!=null) throw new InvalidRequestException("FACULTY requires only a faculty link.");
            member=faculty.findById(request.facultyId()).orElseThrow(() -> new ResourceNotFoundException("Faculty not found."));
            if(accounts.existsByFacultyId(request.facultyId())) throw new ConflictException("Faculty already has an account.");
        }
        return AccountResponse.from(accounts.saveAndFlush(new UserAccount(request.email(),encoder.encode(request.initialPassword()),
                request.role(),member,student,clock.instant())));
    }
    public AccountResponse get(Long id) { return AccountResponse.from(accounts.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found."))); }
    public PageResponse<AccountResponse> list(Pageable page) { return PageResponse.from(accounts.findAll(page).map(AccountResponse::from)); }
    @Transactional
    public AccountResponse status(Long id,Long actorId,AccountStatusRequest request) {
        if(id.equals(actorId)) throw new ConflictException("Administrators cannot change their own account status.");
        var account=accounts.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found."));
        account.setEnabled(request.enabled());accounts.saveAndFlush(account);refreshTokens.revokeAll(id,clock.instant());
        return AccountResponse.from(account);
    }
}
