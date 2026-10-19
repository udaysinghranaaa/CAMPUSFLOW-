package com.example.campusflow_backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "faculty", uniqueConstraints = {
        @UniqueConstraint(name = "uk_faculty_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_faculty_employee_code", columnNames = "employee_code")
})
public class Faculty extends AuditedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "employee_code", nullable = false, length = 30)
    private String employeeCode;
    @Column(length = 100)
    private String designation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_faculty_department"))
    private Department department;
    @Column(nullable = false)
    private boolean active = true;

    protected Faculty() { }

    public Faculty(String fullName, String email, String employeeCode,
                   String designation, Department department) {
        update(fullName, email, employeeCode, designation, department);
    }

    public void update(String fullName, String email, String employeeCode,
                       String designation, Department department) {
        this.fullName = fullName;
        this.email = email;
        this.employeeCode = employeeCode;
        this.designation = designation;
        this.department = department;
    }

    public void setActive(boolean active) { this.active = active; }
    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getEmployeeCode() { return employeeCode; }
    public String getDesignation() { return designation; }
    public Department getDepartment() { return department; }
    public boolean isActive() { return active; }
}
