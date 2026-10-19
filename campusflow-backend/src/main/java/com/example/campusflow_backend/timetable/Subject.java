package com.example.campusflow_backend.timetable;
import jakarta.persistence.*;
import java.time.*;
import com.example.campusflow_backend.entity.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@Entity @Table(name="subjects")
public class Subject extends AuditedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=30) private String code;
 @Column(nullable=false,length=150) private String name;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="department_id",nullable=false) private Department department;
 @Column(length=1000) private String description;
 @Column(nullable=false) private boolean active;
 protected Subject() { }
 public Subject(String code, String name, Department department, String description, boolean active) { revise(code, name, department, description, active); }
 public void revise(String code, String name, Department department, String description, boolean active) { this.code=code; this.name=name; this.department=department; this.description=description; this.active=active; }
 public Long getId() { return id; }
 public String getCode() { return code; }
 public String getName() { return name; }
 public Department getDepartment() { return department; }
 public String getDescription() { return description; }
 public boolean getActive() { return active; }
}

