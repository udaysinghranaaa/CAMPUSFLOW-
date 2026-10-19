package com.example.campusflow_backend.timetable;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.repository.UserAccountRepository;
import com.example.campusflow_backend.security.AccountPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
@Component
public class ScheduleActor {
 private final UserAccountRepository accounts;
 public ScheduleActor(UserAccountRepository accounts){this.accounts=accounts;}
 public UserAccount account(){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||!(auth.getPrincipal() instanceof AccountPrincipal p))throw new AccessDeniedException("Authenticated account required.");return accounts.findById(p.id()).orElseThrow(()->new AccessDeniedException("Account unavailable."));}
 public Long facultyId(){var a=account();if(a.getRole()!=AccountRole.FACULTY||a.getFaculty()==null)throw new AccessDeniedException("Faculty profile required.");return a.getFaculty().getId();}
 public Student student(){var a=account();if(a.getRole()!=AccountRole.STUDENT||a.getStudent()==null)throw new AccessDeniedException("Student profile required.");return a.getStudent();}
 public void facultyOwner(Long id){var a=account();if(a.getRole()!=AccountRole.ADMIN&&(a.getRole()!=AccountRole.FACULTY||a.getFaculty()==null||!a.getFaculty().getId().equals(id)))throw new AccessDeniedException("Only the owner or administrator can manage this block.");}
}
