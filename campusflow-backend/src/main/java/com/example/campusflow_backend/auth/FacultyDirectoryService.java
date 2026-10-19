package com.example.campusflow_backend.auth;
import com.example.campusflow_backend.dto.PageResponse;
import com.example.campusflow_backend.repository.FacultyRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class FacultyDirectoryService {
    private final FacultyRepository faculty;
    public FacultyDirectoryService(FacultyRepository faculty) { this.faculty=faculty; }
    @PreAuthorize("isAuthenticated()") @Transactional(readOnly=true)
    public PageResponse<FacultyDirectoryResponse> list(Pageable page) {
        return PageResponse.from(faculty.search(null,true,null,page).map(FacultyDirectoryResponse::from));
    }
}
