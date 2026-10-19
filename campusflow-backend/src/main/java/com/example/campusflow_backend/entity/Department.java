package com.example.campusflow_backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "departments", uniqueConstraints =
        @UniqueConstraint(name = "uk_departments_code", columnNames = "code"))
public class Department extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 20)
    private String code;
    @Column(length = 1000)
    private String description;

    protected Department() { }

    public Department(String name, String code, String description) {
        update(name, code, description);
    }

    public void update(String name, String code, String description) {
        this.name = name;
        this.code = code;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
}
