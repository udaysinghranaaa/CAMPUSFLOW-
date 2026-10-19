package com.example.campusflow_backend.auth;
import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.repository.UserAccountRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class OwnProfileService {
    private final UserAccountRepository accounts;
    public OwnProfileService(UserAccountRepository accounts) { this.accounts=accounts; }
    @PreAuthorize("#accountId == authentication.principal.id")
    @Transactional(readOnly=true)
    public OwnProfileResponse me(Long accountId) {
        var user=accounts.findById(accountId).orElseThrow(() -> new BadCredentialsException("Invalid credentials."));
        return new OwnProfileResponse(AccountResponse.from(user),user.getStudent()==null?null:StudentResponse.from(user.getStudent()),
                user.getFaculty()==null?null:FacultyResponse.from(user.getFaculty()));
    }
}
