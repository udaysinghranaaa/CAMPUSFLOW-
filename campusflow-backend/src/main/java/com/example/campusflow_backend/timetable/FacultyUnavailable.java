package com.example.campusflow_backend.timetable;
import jakarta.persistence.*;
import java.time.*;
import com.example.campusflow_backend.entity.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@Entity @Table(name="faculty_unavailability")
public class FacultyUnavailable extends AuditedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="faculty_id",nullable=false) private Faculty faculty;
 @Column(name="block_date",nullable=false) private LocalDate blockDate;
 @Column(name="start_time",nullable=false) private LocalTime startTime;
 @Column(name="end_time",nullable=false) private LocalTime endTime;
 @Column(length=500) private String reason;
 @Column(nullable=false) private boolean active;
 protected FacultyUnavailable() { }
 public FacultyUnavailable(Faculty faculty, LocalDate blockDate, LocalTime startTime, LocalTime endTime, String reason, boolean active) { revise(faculty, blockDate, startTime, endTime, reason, active); }
 public void revise(Faculty faculty, LocalDate blockDate, LocalTime startTime, LocalTime endTime, String reason, boolean active) { this.faculty=faculty; this.blockDate=blockDate; this.startTime=startTime; this.endTime=endTime; this.reason=reason; this.active=active; }
 public Long getId() { return id; }
 public Faculty getFaculty() { return faculty; }
 public LocalDate getBlockDate() { return blockDate; }
 public LocalTime getStartTime() { return startTime; }
 public LocalTime getEndTime() { return endTime; }
 public String getReason() { return reason; }
 public boolean getActive() { return active; }
}

