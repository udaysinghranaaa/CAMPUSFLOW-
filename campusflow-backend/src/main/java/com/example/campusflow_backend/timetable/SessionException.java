package com.example.campusflow_backend.timetable;
import jakarta.persistence.*;
import java.time.*;
import com.example.campusflow_backend.entity.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@Entity @Table(name="session_exceptions")
public class SessionException extends AuditedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="timetable_session_id",nullable=false) private TimetableSession session;
 @Column(name="exception_date",nullable=false) private LocalDate exceptionDate;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ExceptionType type;
 @Column(name="start_time") private LocalTime startTime;
 @Column(name="end_time") private LocalTime endTime;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="faculty_id") private Faculty faculty;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="room_id") private Room room;
 @Column(length=500) private String reason;
 protected SessionException() { }
 public SessionException(TimetableSession session, LocalDate exceptionDate, ExceptionType type, LocalTime startTime, LocalTime endTime, Faculty faculty, Room room, String reason) { revise(session, exceptionDate, type, startTime, endTime, faculty, room, reason); }
 public void revise(TimetableSession session, LocalDate exceptionDate, ExceptionType type, LocalTime startTime, LocalTime endTime, Faculty faculty, Room room, String reason) { this.session=session; this.exceptionDate=exceptionDate; this.type=type; this.startTime=startTime; this.endTime=endTime; this.faculty=faculty; this.room=room; this.reason=reason; }
 public Long getId() { return id; }
 public TimetableSession getSession() { return session; }
 public LocalDate getExceptionDate() { return exceptionDate; }
 public ExceptionType getType() { return type; }
 public LocalTime getStartTime() { return startTime; }
 public LocalTime getEndTime() { return endTime; }
 public Faculty getFaculty() { return faculty; }
 public Room getRoom() { return room; }
 public String getReason() { return reason; }
}

