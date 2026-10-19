package com.example.campusflow_backend.auth;
import com.example.campusflow_backend.dto.*;
public record OwnProfileResponse(AccountResponse account,StudentResponse student,FacultyResponse faculty) { }
