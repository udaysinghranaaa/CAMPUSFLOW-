package com.example.campusflow_backend.service;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.exception.*;
import com.example.campusflow_backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceTests {
    @Mock private StudentRepository students;
    @Mock private DepartmentRepository departments;
    @Mock private AcademicBatchRepository batches;
    @Mock private AcademicSectionRepository sections;
    private StudentService service;
    private Department department;
    private AcademicBatch batch;
    private AcademicSection section;
    private Student student;
    private StudentRequest request(Long sectionId) {
        return new StudentRequest(" en001 ", " r001 ", " Asha ", " ASHA@example.edu ", null, 1L, 2L, sectionId, 1, 2023);
    }
    private void assignments(boolean includeSection) {
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(batches.findById(2L)).thenReturn(Optional.of(batch));
        if (includeSection) when(sections.findById(3L)).thenReturn(Optional.of(section));
    }
    @BeforeEach
    void setUp() {
        service = new StudentService(students, departments, batches, sections);
        department = new Department("Computing", "CS", null);
        ReflectionTestUtils.setField(department, "id", 1L);
        batch = new AcademicBatch("CSE2023", 2023, 2027, department, true);
        ReflectionTestUtils.setField(batch, "id", 2L);
        section = new AcademicSection("A", batch, 60, true);
        ReflectionTestUtils.setField(section, "id", 3L);
        student = new Student("EN001", "R001", "Asha", "asha@example.edu", null, department, batch, section, 1, 2023);
        ReflectionTestUtils.setField(student, "id", 4L);
    }
    @Test void createsNormalizedStudent() {
        assignments(true);
        when(students.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.create(request(3L));
        assertThat(result.enrollmentNumber()).isEqualTo("EN001");
        assertThat(result.email()).isEqualTo("asha@example.edu");
        assertThat(result.sectionId()).isEqualTo(3L);
        assertThat(result.status()).isEqualTo(StudentStatus.ACTIVE);
    }
    @Test void createsStudentWithoutSection() {
        assignments(false);
        when(students.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(service.create(request(null)).sectionId()).isNull();
        verifyNoInteractions(sections);
    }
    @Test void duplicateEnrollmentRejected() {
        when(students.existsByEnrollmentNumber("EN001")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request(3L))).isInstanceOf(ConflictException.class);
        verify(students, never()).saveAndFlush(any());
    }
    @Test void duplicateEmailRejected() {
        when(students.existsByEmail("asha@example.edu")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request(3L))).isInstanceOf(ConflictException.class);
    }
    @Test void duplicateRollInBatchRejected() {
        when(students.existsByBatchIdAndRollNumber(2L, "R001")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request(3L))).isInstanceOf(ConflictException.class);
    }
    @Test void missingDepartmentRejected() {
        assertThatThrownBy(() -> service.create(request(3L))).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void missingBatchRejected() {
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        assertThatThrownBy(() -> service.create(request(3L))).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void incompatibleBatchDepartmentRejected() {
        Department other = new Department("Other", "OT", null);
        ReflectionTestUtils.setField(other, "id", 8L);
        batch.update("CSE2023", 2023, 2027, other, true);
        assignments(false);
        assertThatThrownBy(() -> service.create(request(null))).isInstanceOf(ConflictException.class);
    }
    @Test void missingSectionRejected() {
        assignments(false);
        assertThatThrownBy(() -> service.create(request(3L))).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void incompatibleSectionBatchRejected() {
        AcademicBatch other = new AcademicBatch("Other", 2023, 2027, department, true);
        ReflectionTestUtils.setField(other, "id", 8L);
        section.update("A", other, 60, true);
        assignments(true);
        assertThatThrownBy(() -> service.create(request(3L))).isInstanceOf(ConflictException.class);
    }
    @Test void updatesStudentAndUnassignsSectionWithoutChangingStatus() {
        student.setStatus(StudentStatus.SUSPENDED);
        when(students.findById(4L)).thenReturn(Optional.of(student));
        assignments(false);
        when(students.saveAndFlush(student)).thenReturn(student);
        var result = service.update(4L, request(null));
        assertThat(result.sectionId()).isNull();
        assertThat(result.status()).isEqualTo(StudentStatus.SUSPENDED);
    }
    @Test void enrollmentCannotChange() {
        when(students.findById(4L)).thenReturn(Optional.of(student));
        var change = new StudentRequest("OTHER", "R001", "Asha", "asha@example.edu", null, 1L, 2L, 3L, 1, 2023);
        assertThatThrownBy(() -> service.update(4L, change)).isInstanceOf(ConflictException.class);
        verify(students, never()).saveAndFlush(any());
    }
    @Test void duplicateEmailOnUpdateRejected() {
        when(students.findById(4L)).thenReturn(Optional.of(student));
        when(students.existsByEmailAndIdNot("asha@example.edu", 4L)).thenReturn(true);
        assertThatThrownBy(() -> service.update(4L, request(3L))).isInstanceOf(ConflictException.class);
    }
    @Test void updatesStatus() {
        when(students.findById(4L)).thenReturn(Optional.of(student));
        when(students.saveAndFlush(student)).thenReturn(student);
        assertThat(service.updateStatus(4L, new StudentStatusRequest(StudentStatus.GRADUATED)).status())
                .isEqualTo(StudentStatus.GRADUATED);
    }
    @Test void missingStudentRejected() {
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateStatus(99L, new StudentStatusRequest(StudentStatus.INACTIVE)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void forwardsCombinedFiltersAndPagination() {
        var pageable = PageRequest.of(1, 10, Sort.by("id"));
        when(students.search("Asha", "EN001", 1L, 2L, 3L, 1, StudentStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(student), pageable, 11));
        var result = service.list("Asha", "EN001", 1L, 2L, 3L, 1, StudentStatus.ACTIVE, pageable);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(11);
        assertThat(result.content()).extracting(StudentResponse::id).containsExactly(4L);
    }
}
