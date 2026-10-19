package com.example.campusflow_backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="refresh_tokens")
public class RefreshToken extends AuditedEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="user_account_id",nullable=false) private UserAccount userAccount;
    @Column(name="token_hash",nullable=false,length=64) private String tokenHash;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="revoked_at") private Instant revokedAt;
    protected RefreshToken() { }
    public RefreshToken(UserAccount account,String hash,Instant expires) { userAccount=account;tokenHash=hash;expiresAt=expires; }
    public void revoke(Instant now) { if(revokedAt==null) revokedAt=now; }
    public Long getId() { return id; }
    public UserAccount getUserAccount() { return userAccount; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
