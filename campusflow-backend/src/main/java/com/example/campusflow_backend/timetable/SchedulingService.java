package com.example.campusflow_backend.timetable;
import com.example.campusflow_backend.entity.*;
import com.example.campusflow_backend.repository.*;
import com.example.campusflow_backend.dto.PageResponse;
import com.example.campusflow_backend.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
import java.util.function.Supplier;
import java.util.*;
import java.time.*;
import static com.example.campusflow_backend.timetable.SchedulingDtos.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@Service @Transactional(readOnly=true) @PreAuthorize("hasRole('ADMIN')")
public class SchedulingService {
 private final CatalogService catalog;private final TimetableSessionRepository sessions;private final SessionExceptionRepository exceptions;
 private final FacultyRepository faculty;private final AcademicSectionRepository sections;private final FacultyUnavailableRepository blocks;
 private final ScheduleCoverageRepository coverage;private final ScheduleActor actor;private final JdbcTemplate jdbc;
 public SchedulingService(CatalogService catalog,TimetableSessionRepository sessions,SessionExceptionRepository exceptions,FacultyRepository faculty,AcademicSectionRepository sections,FacultyUnavailableRepository blocks,ScheduleCoverageRepository coverage,ScheduleActor actor,JdbcTemplate jdbc){this.catalog=catalog;this.sessions=sessions;this.exceptions=exceptions;this.faculty=faculty;this.sections=sections;this.blocks=blocks;this.coverage=coverage;this.actor=actor;this.jdbc=jdbc;}
 private Faculty faculty(Long id){return faculty.findById(id).orElseThrow(()->new ResourceNotFoundException("Faculty not found."));}
 private TimetableSession lock(Long id){return sessions.lock(id).orElseThrow(()->new ResourceNotFoundException("Session not found."));}
 static <T>T conflictAware(Supplier<T> action){try{return action.get();}catch(DataIntegrityViolationException failure){for(Throwable cause=failure;cause!=null;cause=cause.getCause()){if(cause instanceof SQLException sql&&"23P01".equals(sql.getSQLState())){String detail=String.valueOf(sql.getMessage());String resource=detail.contains("ex_schedule_room")?"Room":detail.contains("ex_schedule_section")?"Section":"Faculty";throw new ConflictException(resource+" scheduling conflict. Choose a different date, time or resource; drafts also reserve slots.");}}throw failure;}}
 public SessionResponse get(Long id){return SessionResponse.from(sessions.findById(id).orElseThrow(()->new ResourceNotFoundException("Session not found.")));} public PageResponse<SessionResponse> list(Long termId,Pageable page){Specification<TimetableSession> spec=(root,query,cb)->termId==null?cb.conjunction():cb.equal(root.get("term").get("id"),termId);return PageResponse.from(sessions.findAll(spec,page).map(SessionResponse::from));}
 @Transactional public SessionResponse save(Long id,SessionRequest r){
 ScheduleRules.times(r.startTime(),r.endTime());ScheduleRules.dates(r.effectiveStartDate(),r.effectiveEndDate());
 var term=catalog.requireTerm(r.academicTermId());var subject=catalog.requireSubject(r.subjectId());var member=faculty(r.facultyId());var room=catalog.requireRoom(r.roomId());var section=sections.findById(r.sectionId()).orElseThrow(()->new ResourceNotFoundException("Section not found."));
 if(r.status()==SessionStatus.CANCELLED)throw new InvalidRequestException("Use the cancel operation to cancel a session.");
 if(!term.getActive()||!subject.getActive()||!member.isActive()||!room.getActive()||!section.isActive()||!section.getBatch().isActive())throw new InvalidRequestException("Active term, subject, faculty, room, section and batch are required.");
 Long department=term.getDepartment().getId();if(!department.equals(subject.getDepartment().getId())||!department.equals(member.getDepartment().getId())||!department.equals(section.getBatch().getDepartment().getId()))throw new InvalidRequestException("Term, subject, faculty and section must belong to the same department.");
 if(r.effectiveStartDate().getYear()<section.getBatch().getStartYear()||r.effectiveEndDate().getYear()>section.getBatch().getEndYear())throw new InvalidRequestException("Session dates must fall within the batch academic years.");
 if(r.effectiveStartDate().isBefore(term.getStartDate())||r.effectiveEndDate().isAfter(term.getEndDate()))throw new InvalidRequestException("Session dates must fall within the term.");
 long enrolled=jdbc.queryForObject("select count(*) from students where section_id=? and status='ACTIVE'",Long.class,section.getId());if(room.getCapacity()<Math.max(section.getCapacity()==null?0:section.getCapacity(),enrolled))throw new InvalidRequestException("Room capacity is smaller than section capacity/enrollment.");
 if(r.effectiveStartDate().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.of(r.dayOfWeek()))).isAfter(r.effectiveEndDate()))throw new InvalidRequestException("Date range contains no occurrence on the selected weekday.");
 TimetableSession s=id==null?new TimetableSession(term,section,subject,member,room,r.dayOfWeek(),r.startTime(),r.endTime(),r.effectiveStartDate(),r.effectiveEndDate(),r.status(),r.sessionType()):lock(id);
 if(id!=null&&s.getStatus()==SessionStatus.CANCELLED)throw new ConflictException("Cancelled series cannot be edited; create a new series."); if(id!=null&&exceptions.existsBySessionId(id))throw new ConflictException("This series has date exceptions. Cancel it and create a new series to preserve history.");
 s.revise(term,section,subject,member,room,r.dayOfWeek(),r.startTime(),r.endTime(),r.effectiveStartDate(),r.effectiveEndDate(),r.status(),r.sessionType());
 return conflictAware(()->SessionResponse.from(sessions.saveAndFlush(s)));
 }
 @Transactional public SessionResponse status(Long id,SessionStatus status){var s=lock(id);if(s.getStatus()==SessionStatus.CANCELLED&&status!=SessionStatus.CANCELLED)throw new ConflictException("Cancelled series cannot be republished; create a new series.");s.revise(s.getTerm(),s.getSection(),s.getSubject(),s.getFaculty(),s.getRoom(),s.getDayOfWeek(),s.getStartTime(),s.getEndTime(),s.getEffectiveStartDate(),s.getEffectiveEndDate(),status,s.getSessionType());return conflictAware(()->SessionResponse.from(sessions.saveAndFlush(s)));}
 @Transactional public ExceptionResponse exception(Long id,ExceptionRequest r){var s=lock(id);if(s.getStatus()==SessionStatus.CANCELLED)throw new ConflictException("The series is already cancelled.");if(r.exceptionDate().isBefore(s.getEffectiveStartDate())||r.exceptionDate().isAfter(s.getEffectiveEndDate())||r.exceptionDate().getDayOfWeek().getValue()!=s.getDayOfWeek())throw new InvalidRequestException("Exception date must be an occurrence of this series.");Faculty member=null;Room room=null;if(r.type()==ExceptionType.RESCHEDULED){ScheduleRules.times(r.startTime(),r.endTime());if(r.facultyId()==null||r.roomId()==null)throw new InvalidRequestException("Replacement faculty and room are required.");member=faculty(r.facultyId());room=catalog.requireRoom(r.roomId());if(!member.isActive()||!room.getActive()||!member.getDepartment().getId().equals(s.getTerm().getDepartment().getId()))throw new InvalidRequestException("Invalid replacement faculty or room.");}else if(r.startTime()!=null||r.endTime()!=null||r.facultyId()!=null||r.roomId()!=null)throw new InvalidRequestException("A cancellation cannot have replacements.");
 var e=exceptions.findBySessionIdAndExceptionDate(id,r.exceptionDate()).orElse(new SessionException(s,r.exceptionDate(),r.type(),r.startTime(),r.endTime(),member,room,r.reason()));e.revise(s,r.exceptionDate(),r.type(),r.startTime(),r.endTime(),member,room,r.reason());return conflictAware(()->{var saved=exceptions.saveAndFlush(e);return new ExceptionResponse(saved.getId(),id,r.exceptionDate(),r.type());});}
 @Transactional public CoverageResponse coverage(Long termId,CoverageRequest r){ScheduleRules.times(r.startTime(),r.endTime());var term=catalog.requireTerm(termId);var member=faculty(r.facultyId());if(!term.getDepartment().getId().equals(member.getDepartment().getId()))throw new InvalidRequestException("Coverage faculty must belong to the term department.");var c=coverage.findByTermIdAndFacultyId(termId,r.facultyId()).orElse(new ScheduleCoverage(term,member,r.dayMask(),r.startTime(),r.endTime()));c.revise(term,member,r.dayMask(),r.startTime(),r.endTime());coverage.saveAndFlush(c);return new CoverageResponse(c.getId(),termId,r.facultyId(),r.dayMask(),r.startTime(),r.endTime());}
 @PreAuthorize("hasAnyRole('ADMIN','FACULTY')") public PageResponse<BlockResponse> blocks(Long facultyId,Pageable page){actor.facultyOwner(facultyId);Specification<FacultyUnavailable> spec=(root,q,cb)->cb.equal(root.get("faculty").get("id"),facultyId);return PageResponse.from(blocks.findAll(spec,page).map(SchedulingService::blockDto));}
 static BlockResponse blockDto(FacultyUnavailable b){return new BlockResponse(b.getId(),b.getFaculty().getId(),b.getBlockDate(),b.getStartTime(),b.getEndTime(),b.getReason(),b.getActive());}
 @Transactional @PreAuthorize("hasAnyRole('ADMIN','FACULTY')") public BlockResponse block(BlockRequest r){actor.facultyOwner(r.facultyId());ScheduleRules.times(r.startTime(),r.endTime());var member=faculty(r.facultyId());if(!member.isActive())throw new InvalidRequestException("Faculty is inactive.");return conflictAware(()->blockDto(blocks.saveAndFlush(new FacultyUnavailable(member,r.date(),r.startTime(),r.endTime(),r.reason(),true))));}
 @Transactional @PreAuthorize("hasAnyRole('ADMIN','FACULTY')") public BlockResponse cancelBlock(Long id){var b=blocks.findById(id).orElseThrow(()->new ResourceNotFoundException("Block not found."));actor.facultyOwner(b.getFaculty().getId());b.revise(b.getFaculty(),b.getBlockDate(),b.getStartTime(),b.getEndTime(),b.getReason(),false);return blockDto(blocks.saveAndFlush(b));}
}
