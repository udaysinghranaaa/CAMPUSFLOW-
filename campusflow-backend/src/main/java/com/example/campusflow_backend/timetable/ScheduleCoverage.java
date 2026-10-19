package com.example.campusflow_backend.timetable;
import jakarta.persistence.*;
import java.time.*;
import com.example.campusflow_backend.entity.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@Entity @Table(name="faculty_schedule_coverage")
public class ScheduleCoverage extends AuditedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="academic_term_id",nullable=false) private AcademicTerm term;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="faculty_id",nullable=false) private Faculty faculty;
 @Column(name="day_mask",nullable=false) private int dayMask;
 @Column(name="start_time",nullable=false) private LocalTime startTime;
 @Column(name="end_time",nullable=false) private LocalTime endTime;
 protected ScheduleCoverage() { }
 public ScheduleCoverage(AcademicTerm term, Faculty faculty, int dayMask, LocalTime startTime, LocalTime endTime) { revise(term, faculty, dayMask, startTime, endTime); }
 public void revise(AcademicTerm term, Faculty faculty, int dayMask, LocalTime startTime, LocalTime endTime) { this.term=term; this.faculty=faculty; this.dayMask=dayMask; this.startTime=startTime; this.endTime=endTime; }
 public Long getId() { return id; }
 public AcademicTerm getTerm() { return term; }
 public Faculty getFaculty() { return faculty; }
 public int getDayMask() { return dayMask; }
 public LocalTime getStartTime() { return startTime; }
 public LocalTime getEndTime() { return endTime; }
}

