package com.example.campusflow_backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "academic_batches")
public class AcademicBatch extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150) private String name;
    @Column(name = "start_year", nullable = false) private int startYear;
    @Column(name = "end_year", nullable = false) private int endYear;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false) private Department department;
    @Column(nullable = false) private boolean active;

    protected AcademicBatch() { }
    public AcademicBatch(String name, int startYear, int endYear, Department department, boolean active) {
        update(name, startYear, endYear, department, active);
    }
    public void update(String name, int startYear, int endYear, Department department, boolean active) {
        this.name = name; this.startYear = startYear; this.endYear = endYear;
        this.department = department; this.active = active;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public int getStartYear() { return startYear; }
    public int getEndYear() { return endYear; }
    public Department getDepartment() { return department; }
    public boolean isActive() { return active; }
}
