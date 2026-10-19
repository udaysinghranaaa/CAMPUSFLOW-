package com.example.campusflow_backend.timetable;
import jakarta.persistence.*;
import java.time.*;
import com.example.campusflow_backend.entity.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@Entity @Table(name="timetable_sessions")
public class TimetableSession extends AuditedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="academic_term_id",nullable=false) private AcademicTerm term;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="section_id",nullable=false) private AcademicSection section;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="subject_id",nullable=false) private Subject subject;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="faculty_id",nullable=false) private Faculty faculty;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="room_id",nullable=false) private Room room;
 @Column(name="day_of_week",nullable=false) private int dayOfWeek;
 @Column(name="start_time",nullable=false) private LocalTime startTime;
 @Column(name="end_time",nullable=false) private LocalTime endTime;
 @Column(name="effective_start_date",nullable=false) private LocalDate effectiveStartDate;
 @Column(name="effective_end_date",nullable=false) private LocalDate effectiveEndDate;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private SessionStatus status;
 @Enumerated(EnumType.STRING) @Column(name="session_type",nullable=false,length=20) private SessionType sessionType;
 protected TimetableSession() { }
 public TimetableSession(AcademicTerm term, AcademicSection section, Subject subject, Faculty faculty, Room room, int dayOfWeek, LocalTime startTime, LocalTime endTime, LocalDate effectiveStartDate, LocalDate effectiveEndDate, SessionStatus status, SessionType sessionType) { revise(term, section, subject, faculty, room, dayOfWeek, startTime, endTime, effectiveStartDate, effectiveEndDate, status, sessionType); }
 public void revise(AcademicTerm term, AcademicSection section, Subject subject, Faculty faculty, Room room, int dayOfWeek, LocalTime startTime, LocalTime endTime, LocalDate effectiveStartDate, LocalDate effectiveEndDate, SessionStatus status, SessionType sessionType) { this.term=term; this.section=section; this.subject=subject; this.faculty=faculty; this.room=room; this.dayOfWeek=dayOfWeek; this.startTime=startTime; this.endTime=endTime; this.effectiveStartDate=effectiveStartDate; this.effectiveEndDate=effectiveEndDate; this.status=status; this.sessionType=sessionType; }
 public Long getId() { return id; }
 public AcademicTerm getTerm() { return term; }
 public AcademicSection getSection() { return section; }
 public Subject getSubject() { return subject; }
 public Faculty getFaculty() { return faculty; }
 public Room getRoom() { return room; }
 public int getDayOfWeek() { return dayOfWeek; }
 public LocalTime getStartTime() { return startTime; }
 public LocalTime getEndTime() { return endTime; }
 public LocalDate getEffectiveStartDate() { return effectiveStartDate; }
 public LocalDate getEffectiveEndDate() { return effectiveEndDate; }
 public SessionStatus getStatus() { return status; }
 public SessionType getSessionType() { return sessionType; }
}

