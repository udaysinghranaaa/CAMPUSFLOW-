package com.example.campusflow_backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "academic_sections")
public class AcademicSection extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 30) private String name;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false) private AcademicBatch batch;
    @Column private Integer capacity;
    @Column(nullable = false) private boolean active;

    protected AcademicSection() { }
    public AcademicSection(String name, AcademicBatch batch, Integer capacity, boolean active) {
        update(name, batch, capacity, active);
    }
    public void update(String name, AcademicBatch batch, Integer capacity, boolean active) {
        this.name = name; this.batch = batch; this.capacity = capacity; this.active = active;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public AcademicBatch getBatch() { return batch; }
    public Integer getCapacity() { return capacity; }
    public boolean isActive() { return active; }
}
