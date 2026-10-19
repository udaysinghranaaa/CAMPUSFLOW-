package com.example.campusflow_backend.service;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.entity.Department;
import com.example.campusflow_backend.exception.*;
import com.example.campusflow_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTests {
    @Mock private DepartmentRepository departments;
    @Mock private FacultyRepository faculty;
    private DepartmentService service;
    private Department department;

    @BeforeEach
    void setUp() {
        service = new DepartmentService(departments, faculty);
        department = new Department("Computer Science", "CSE", "Engineering");
        ReflectionTestUtils.setField(department, "id", 1L);
    }

    @Test
    void createsDepartmentWithNormalizedCode() {
        when(departments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.create(new DepartmentRequest(" Computer Science ", " cse ", null));
        assertThat(response.name()).isEqualTo("Computer Science");
        assertThat(response.code()).isEqualTo("CSE");
        verify(departments).saveAndFlush(any(Department.class));
    }

    @Test
    void rejectsDuplicateCode() {
        when(departments.existsByCode("CSE")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new DepartmentRequest("CS", "CSE", null)))
                .isInstanceOf(ConflictException.class);
        verify(departments, never()).saveAndFlush(any());
    }

    @Test
    void listsByNormalizedCodeWithPagination() {
        Pageable pageable = PageRequest.of(0, 20);
        when(departments.findByCode("CSE", pageable)).thenReturn(new PageImpl<>(List.of(department)));
        assertThat(service.list("cse", pageable).content()).hasSize(1);
    }

    @Test
    void updatesExistingDepartment() {
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(departments.saveAndFlush(department)).thenReturn(department);
        var response = service.update(1L, new DepartmentRequest("Computing", "CS", "Updated"));
        assertThat(response.name()).isEqualTo("Computing");
        assertThat(response.code()).isEqualTo("CS");
    }

    @Test
    void rejectsDuplicateCodeOnUpdate() {
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(departments.existsByCodeAndIdNot("ECE", 1L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(1L, new DepartmentRequest("Electronics", "ECE", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void preventsDeletingDepartmentWithFaculty() {
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(faculty.existsByDepartmentId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ConflictException.class);
        verify(departments, never()).delete(any());
    }

    @Test
    void deletesEmptyDepartment() {
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        service.delete(1L);
        verify(departments).delete(department);
        verify(departments).flush();
    }

    @Test
    void missingDepartmentReturnsNotFound() {
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
