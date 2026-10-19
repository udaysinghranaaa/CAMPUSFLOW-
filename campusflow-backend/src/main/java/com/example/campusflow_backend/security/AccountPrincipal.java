package com.example.campusflow_backend.security;

import com.example.campusflow_backend.entity.AccountRole;
public record AccountPrincipal(Long id,AccountRole role,long version) { }
