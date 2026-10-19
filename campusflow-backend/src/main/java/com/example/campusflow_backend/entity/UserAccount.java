package com.example.campusflow_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="user_accounts")
public class UserAccount extends AuditedEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=254) private String email;
    @Column(name="password_hash",nullable=false,length=100) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private AccountRole role;
    @Column(nullable=false) private boolean enabled=true;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="faculty_id",unique=true) private Faculty faculty;
    @OneToOne(fetch=FetchType.LAZY) @JoinColumn(name="student_id",unique=true) private Student student;
    @Column(name="security_version",nullable=false) private long securityVersion;
    @Column(name="password_changed_at",nullable=false) private Instant passwordChangedAt;
    protected UserAccount() { }
    public UserAccount(String email,String passwordHash,AccountRole role,Faculty faculty,Student student,Instant now) {
        this.email=email; this.passwordHash=passwordHash; this.role=role; this.faculty=faculty; this.student=student;
        this.passwordChangedAt=now;
    }
    public void changePassword(String hash,Instant now) { passwordHash=hash; passwordChangedAt=now; invalidateAccess(); }
    public void setEnabled(boolean enabled) { this.enabled=enabled; invalidateAccess(); }
    public void invalidateAccess() { securityVersion++; }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public AccountRole getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public Faculty getFaculty() { return faculty; }
    public Student getStudent() { return student; }
    public long getSecurityVersion() { return securityVersion; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
}
