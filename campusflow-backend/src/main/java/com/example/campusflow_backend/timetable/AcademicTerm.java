package com.example.campusflow_backend.timetable;
import jakarta.persistence.*;
import java.time.*;
import com.example.campusflow_backend.entity.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@Entity @Table(name="academic_terms")
public class AcademicTerm extends AuditedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=150) private String name;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="department_id",nullable=false) private Department department;
 @Column(nullable=false) private int semester;
 @Column(name="start_date",nullable=false) private LocalDate startDate;
 @Column(name="end_date",nullable=false) private LocalDate endDate;
 @Column(nullable=false) private boolean active;
 protected AcademicTerm() { }
 public AcademicTerm(String name, Department department, int semester, LocalDate startDate, LocalDate endDate, boolean active) { revise(name, department, semester, startDate, endDate, active); }
 public void revise(String name, Department department, int semester, LocalDate startDate, LocalDate endDate, boolean active) { this.name=name; this.department=department; this.semester=semester; this.startDate=startDate; this.endDate=endDate; this.active=active; }
 public Long getId() { return id; }
 public String getName() { return name; }
 public Department getDepartment() { return department; }
 public int getSemester() { return semester; }
 public LocalDate getStartDate() { return startDate; }
 public LocalDate getEndDate() { return endDate; }
 public boolean getActive() { return active; }
}

