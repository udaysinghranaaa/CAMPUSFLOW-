package com.example.campusflow_backend.timetable;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.repository.*;
import com.example.campusflow_backend.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import java.time.*;import java.util.*;import java.sql.SQLException;
import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.assertj.core.api.Assertions.*;
import static com.example.campusflow_backend.timetable.SchedulingDtos.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
class SchedulingServiceTests {
 CatalogService catalog;TimetableSessionRepository sessions;SessionExceptionRepository exceptions;FacultyRepository faculty;AcademicSectionRepository sections;FacultyUnavailableRepository blocks;ScheduleCoverageRepository coverage;ScheduleActor actor;JdbcTemplate jdbc;SchedulingService service;
 AcademicTerm term;Subject subject;Faculty member;Room room;AcademicSection section;Department department;
 static <T>T id(T object,long value){ReflectionTestUtils.setField(object,"id",value);return object;}
 @BeforeEach void setup(){catalog=mock(CatalogService.class);sessions=mock(TimetableSessionRepository.class);exceptions=mock(SessionExceptionRepository.class);faculty=mock(FacultyRepository.class);sections=mock(AcademicSectionRepository.class);blocks=mock(FacultyUnavailableRepository.class);coverage=mock(ScheduleCoverageRepository.class);actor=mock(ScheduleActor.class);jdbc=mock(JdbcTemplate.class);service=new SchedulingService(catalog,sessions,exceptions,faculty,sections,blocks,coverage,actor,jdbc);
 department=id(new Department("CS","CS",null),1);member=id(new Faculty("Teacher","a@example.edu","F1",null,department),1);subject=id(new Subject("CS1","Algorithms",department,null,true),1);term=id(new AcademicTerm("Autumn",department,1,LocalDate.of(2026,10,1),LocalDate.of(2026,12,31),true),1);room=id(new Room("R1","Room","Main",null,RoomType.CLASSROOM,60,true),1);section=id(new AcademicSection("A",id(new AcademicBatch("2026",2026,2030,department,true),1),30,true),1);
 when(catalog.requireTerm(1L)).thenReturn(term);when(catalog.requireSubject(1L)).thenReturn(subject);when(catalog.requireRoom(1L)).thenReturn(room);when(faculty.findById(1L)).thenReturn(Optional.of(member));when(sections.findById(1L)).thenReturn(Optional.of(section));when(jdbc.queryForObject(anyString(),eq(Long.class),eq(1L))).thenReturn(20L);when(sessions.saveAndFlush(any())).thenAnswer(invocation->id(invocation.getArgument(0),1));}
 SessionRequest request(){return new SessionRequest(1L,1L,1L,1L,1L,1,LocalTime.of(9,0),LocalTime.of(10,0),LocalDate.of(2026,10,19),LocalDate.of(2026,11,2),SessionStatus.DRAFT,SessionType.LECTURE);}
 @Test void validDraftUsesAllActualReferences(){assertThat(service.save(null,request()).status()).isEqualTo(SessionStatus.DRAFT);verify(sessions).saveAndFlush(any());}
 @Test void inactiveFacultyRejected(){member.setActive(false);assertThatThrownBy(()->service.save(null,request())).isInstanceOf(InvalidRequestException.class);verifyNoInteractions(sessions);}
 @Test void mismatchedDepartmentRejected(){member.update("Teacher","a@example.edu","F1",null,id(new Department("Other","OT",null),2));assertThatThrownBy(()->service.save(null,request())).isInstanceOf(InvalidRequestException.class);}
 @Test void undersizedRoomRejected(){when(jdbc.queryForObject(anyString(),eq(Long.class),eq(1L))).thenReturn(61L);assertThatThrownBy(()->service.save(null,request())).hasMessageContaining("capacity");}
 @Test void databaseRoomExclusionMapsToSafeActionableConflict(){doThrow(new DataIntegrityViolationException("internal",new SQLException("constraint ex_schedule_room","23P01"))).when(sessions).saveAndFlush(any(TimetableSession.class));assertThatThrownBy(()->service.save(null,request())).isInstanceOf(ConflictException.class).hasMessageContaining("Room scheduling conflict").hasMessageNotContaining("internal");}
 @Test void genericIntegrityFailureIsNotFalselyClassifiedAsOverlap(){var failure=new DataIntegrityViolationException("constraint",new SQLException("check","23514"));assertThatThrownBy(()->SchedulingService.conflictAware(()->{throw failure;})).isSameAs(failure);}
 @Test void cancelReleasesViaDatabaseTriggerWithoutDeletingHistory(){var r=request();var session=id(new TimetableSession(term,section,subject,member,room,1,r.startTime(),r.endTime(),r.effectiveStartDate(),r.effectiveEndDate(),r.status(),r.sessionType()),1);when(sessions.lock(1L)).thenReturn(Optional.of(session));assertThat(service.status(1L,SessionStatus.CANCELLED).status()).isEqualTo(SessionStatus.CANCELLED);verify(sessions,never()).delete(any(TimetableSession.class));}
 @Test void blockRequiresOwnershipBeforeWriting(){doThrow(new AccessDeniedException("owner")).when(actor).facultyOwner(1L);assertThatThrownBy(()->service.block(new BlockRequest(1L,LocalDate.of(2026,10,19),LocalTime.of(9,0),LocalTime.of(10,0),"private"))).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(blocks);}
}
