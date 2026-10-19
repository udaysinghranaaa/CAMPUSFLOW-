package com.example.campusflow_backend.service;

import com.example.campusflow_backend.dto.*;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.exception.*;
import com.example.campusflow_backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicGroupingServiceTests {
    @Mock private DepartmentRepository departments;
    @Mock private AcademicBatchRepository batches;
    @Mock private AcademicSectionRepository sections;
    @Mock private StudentRepository students;
    private AcademicBatchService batchService;
    private AcademicSectionService sectionService;
    private Department department;
    private AcademicBatch batch;
    private AcademicSection section;
    @BeforeEach void setUp() {
        batchService = new AcademicBatchService(batches, departments, sections, students);
        sectionService = new AcademicSectionService(sections, batches, students);
        department = new Department("Computing", "CS", null); ReflectionTestUtils.setField(department, "id", 1L);
        batch = new AcademicBatch("CSE2023", 2023, 2027, department, true); ReflectionTestUtils.setField(batch, "id", 2L);
        section = new AcademicSection("A", batch, 60, true); ReflectionTestUtils.setField(section, "id", 3L);
    }
    @Test void createsBatch() {
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(batches.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(batchService.create(new BatchRequest("CSE2023", 2023, 2027, 1L, true)).name()).isEqualTo("CSE2023");
    }
    @Test void duplicateBatchRejected() {
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(batches.existsByDepartmentIdAndNameIgnoreCase(1L, "CSE2023")).thenReturn(true);
        assertThatThrownBy(() -> batchService.create(new BatchRequest("CSE2023", 2023, 2027, 1L, true)))
                .isInstanceOf(ConflictException.class);
    }
    @Test void createsSection() {
        when(batches.findById(2L)).thenReturn(Optional.of(batch));
        when(sections.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        assertThat(sectionService.create(new SectionRequest(" a ", 2L, 60, true)).name()).isEqualTo("A");
    }
    @Test void duplicateSectionRejected() {
        when(batches.findById(2L)).thenReturn(Optional.of(batch));
        when(sections.existsByBatchIdAndNameIgnoreCase(2L, "A")).thenReturn(true);
        assertThatThrownBy(() -> sectionService.create(new SectionRequest("A", 2L, null, true)))
                .isInstanceOf(ConflictException.class);
    }
    @Test void studentPreventsBatchDeletion() {
        when(batches.findById(2L)).thenReturn(Optional.of(batch));
        when(students.existsByBatchId(2L)).thenReturn(true);
        assertThatThrownBy(() -> batchService.delete(2L)).isInstanceOf(ConflictException.class);
        verify(batches, never()).delete(any());
    }
    @Test void sectionPreventsBatchDeletion() {
        when(batches.findById(2L)).thenReturn(Optional.of(batch));
        when(sections.existsByBatchId(2L)).thenReturn(true);
        assertThatThrownBy(() -> batchService.delete(2L)).isInstanceOf(ConflictException.class);
    }
    @Test void studentPreventsSectionDeletion() {
        when(sections.findById(3L)).thenReturn(Optional.of(section));
        when(students.existsBySectionId(3L)).thenReturn(true);
        assertThatThrownBy(() -> sectionService.delete(3L)).isInstanceOf(ConflictException.class);
        verify(sections, never()).delete(any());
    }
    @Test void deletesEmptyGroups() {
        when(batches.findById(2L)).thenReturn(Optional.of(batch));
        when(sections.findById(3L)).thenReturn(Optional.of(section));
        sectionService.delete(3L); batchService.delete(2L);
        verify(sections).flush(); verify(batches).flush();
    }
    @Test void updatesBatchAndSectionMetadata() {
        when(batches.findById(2L)).thenReturn(Optional.of(batch));
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(batches.saveAndFlush(batch)).thenReturn(batch);
        assertThat(batchService.update(2L, new BatchRequest("New", 2023, 2028, 1L, false)).active()).isFalse();
        when(sections.findById(3L)).thenReturn(Optional.of(section));
        when(sections.saveAndFlush(section)).thenReturn(section);
        assertThat(sectionService.update(3L, new SectionRequest("B", 2L, 50, true)).capacity()).isEqualTo(50);
    }
    @Test void cannotMovePopulatedBatch() {
        when(batches.findById(2L)).thenReturn(Optional.of(batch));
        Department other = new Department("Other", "OT", null); ReflectionTestUtils.setField(other, "id", 9L);
        when(departments.findById(9L)).thenReturn(Optional.of(other));
        when(students.existsByBatchId(2L)).thenReturn(true);
        assertThatThrownBy(() -> batchService.update(2L, new BatchRequest("New", 2023, 2027, 9L, true)))
                .isInstanceOf(ConflictException.class);
    }
    @Test void cannotMovePopulatedSection() {
        when(sections.findById(3L)).thenReturn(Optional.of(section));
        AcademicBatch other = new AcademicBatch("Other", 2023, 2027, department, true);
        ReflectionTestUtils.setField(other, "id", 9L);
        when(batches.findById(9L)).thenReturn(Optional.of(other));
        when(students.existsBySectionId(3L)).thenReturn(true);
        assertThatThrownBy(() -> sectionService.update(3L, new SectionRequest("A", 9L, 60, true)))
                .isInstanceOf(ConflictException.class);
    }
    @Test void missingGroupsReturnNotFound() {
        assertThatThrownBy(() -> batchService.get(999L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> sectionService.get(999L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
