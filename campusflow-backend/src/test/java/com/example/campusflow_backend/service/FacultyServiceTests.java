package com.example.campusflow_backend.service;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.entity.*;
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
class FacultyServiceTests {
    @Mock private FacultyRepository faculty;
    @Mock private DepartmentRepository departments;
    private FacultyService service;
    private Department department;
    private Faculty record;

    private FacultyRequest request() {
        return new FacultyRequest(" Asha Sharma ", " ASHA@example.edu ", " emp001 ", "Professor", 1L);
    }

    @BeforeEach
    void setUp() {
        service = new FacultyService(faculty, departments);
        department = new Department("Computer Science", "CSE", null);
        ReflectionTestUtils.setField(department, "id", 1L);
        record = new Faculty("Asha Sharma", "asha@example.edu", "EMP001", "Professor", department);
        ReflectionTestUtils.setField(record, "id", 2L);
    }

    @Test
    void createsFacultyInExistingDepartment() {
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(faculty.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.create(request());
        assertThat(response.fullName()).isEqualTo("Asha Sharma");
        assertThat(response.email()).isEqualTo("asha@example.edu");
        assertThat(response.employeeCode()).isEqualTo("EMP001");
        assertThat(response.departmentId()).isEqualTo(1L);
        assertThat(response.active()).isTrue();
    }

    @Test
    void rejectsInvalidDepartment() {
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(ResourceNotFoundException.class);
        verify(faculty, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateEmail() {
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(faculty.existsByEmail("asha@example.edu")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectsDuplicateEmployeeCode() {
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(faculty.existsByEmployeeCode("EMP001")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request())).isInstanceOf(ConflictException.class);
    }

    @Test
    void filtersByDepartmentStatusAndName() {
        Pageable pageable = PageRequest.of(0, 20);
        when(faculty.search(1L, true, "Asha", pageable)).thenReturn(new PageImpl<>(List.of(record)));
        assertThat(service.list(1L, true, " Asha ", pageable).content()).hasSize(1);
        verify(faculty).search(1L, true, "Asha", pageable);
    }

    @Test
    void escapesNameSearchWildcards() {
        Pageable pageable = PageRequest.of(0, 20);
        when(faculty.search(null, null, "A!_!%!!", pageable)).thenReturn(Page.empty(pageable));
        assertThat(service.list(null, null, "A_%!", pageable).content()).isEmpty();
    }

    @Test
    void updatesFacultyWithoutChangingActiveStatus() {
        record.setActive(false);
        when(faculty.findById(2L)).thenReturn(Optional.of(record));
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(faculty.saveAndFlush(record)).thenReturn(record);
        var response = service.update(2L, request());
        assertThat(response.active()).isFalse();
        assertThat(response.designation()).isEqualTo("Professor");
    }

    @Test
    void updatesActiveStatus() {
        when(faculty.findById(2L)).thenReturn(Optional.of(record));
        when(faculty.saveAndFlush(record)).thenReturn(record);
        assertThat(service.updateStatus(2L, new FacultyStatusRequest(false)).active()).isFalse();
    }

    @Test
    void deletesFaculty() {
        when(faculty.findById(2L)).thenReturn(Optional.of(record));
        service.delete(2L);
        verify(faculty).delete(record);
        verify(faculty).flush();
    }

    @Test
    void missingFacultyReturnsNotFound() {
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
