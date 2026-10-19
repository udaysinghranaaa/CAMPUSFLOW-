package com.example.campusflow_backend.timetable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import com.example.campusflow_backend.exception.ResourceNotFoundException;
import com.example.campusflow_backend.entity.AccountRole;
import java.time.*;
import static com.example.campusflow_backend.timetable.SchedulingDtos.*;
@Service @Transactional(readOnly=true)
public class TimetableReadService {
 private final ScheduleActor actor;private final ScheduleReadRepository reads;private final Clock clock;
 public TimetableReadService(ScheduleActor actor,ScheduleReadRepository reads,Clock clock){this.actor=actor;this.reads=reads;this.clock=clock;}
 @PreAuthorize("hasRole('ADMIN')") public WeeklyTimetable admin(Long term,LocalDate date){var start=week(date);return new WeeklyTimetable(start,"Asia/Kolkata",reads.adminWeek(term,start));} public LocalDate today(){return LocalDate.now(clock.withZone(ScheduleRules.CAMPUS));}
 LocalDate week(LocalDate date){return ScheduleRules.monday(date==null?today():date);}
 @PreAuthorize("hasRole('FACULTY')") public WeeklyTimetable faculty(LocalDate date){var start=week(date);return new WeeklyTimetable(start,"Asia/Kolkata",reads.week(actor.facultyId(),null,null,start));}
 @PreAuthorize("hasRole('STUDENT')") public WeeklyTimetable student(LocalDate date){var s=actor.student();var start=week(date);return new WeeklyTimetable(start,"Asia/Kolkata",s.getSection()==null?java.util.List.of():reads.week(null,s.getSection().getId(),s.getCurrentSemester(),start));}
 @PreAuthorize("hasAnyRole('ADMIN','STUDENT')") public WeeklyTimetable section(Long id,LocalDate date){var account=actor.account();Integer semester=null;if(account.getRole()==AccountRole.STUDENT){var s=actor.student();if(s.getSection()==null||!s.getSection().getId().equals(id))throw new AccessDeniedException("Only your enrolled section is permitted.");semester=s.getCurrentSemester();}var start=week(date);return new WeeklyTimetable(start,"Asia/Kolkata",reads.week(null,id,semester,start));}
}
