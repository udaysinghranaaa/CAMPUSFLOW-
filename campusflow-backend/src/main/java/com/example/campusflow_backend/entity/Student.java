package com.example.campusflow_backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "students")
public class Student extends AuditedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "enrollment_number", nullable = false, length = 40, updatable = false)
    private String enrollmentNumber;
    @Column(name = "roll_number", nullable = false, length = 30) private String rollNumber;
    @Column(name = "full_name", nullable = false, length = 150) private String fullName;
    @Column(nullable = false, length = 254) private String email;
    @Column(name = "phone_number", length = 25) private String phoneNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false) private Department department;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false) private AcademicBatch batch;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id") private AcademicSection section;
    @Column(name = "current_semester", nullable = false) private int currentSemester;
    @Column(name = "admission_year", nullable = false) private int admissionYear;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private StudentStatus status = StudentStatus.ACTIVE;

    protected Student() { }
    public Student(String enrollmentNumber, String rollNumber, String fullName, String email, String phoneNumber,
                   Department department, AcademicBatch batch, AcademicSection section, int semester, int admissionYear) {
        this.enrollmentNumber = enrollmentNumber;
        update(rollNumber, fullName, email, phoneNumber, department, batch, section, semester, admissionYear);
    }
    public void update(String rollNumber, String fullName, String email, String phoneNumber, Department department,
                       AcademicBatch batch, AcademicSection section, int semester, int admissionYear) {
        this.rollNumber = rollNumber; this.fullName = fullName; this.email = email; this.phoneNumber = phoneNumber;
        this.department = department; this.batch = batch; this.section = section;
        this.currentSemester = semester; this.admissionYear = admissionYear;
    }
    public void setStatus(StudentStatus status) { this.status = status; }
    public Long getId() { return id; }
    public String getEnrollmentNumber() { return enrollmentNumber; }
    public String getRollNumber() { return rollNumber; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public Department getDepartment() { return department; }
    public AcademicBatch getBatch() { return batch; }
    public AcademicSection getSection() { return section; }
    public int getCurrentSemester() { return currentSemester; }
    public int getAdmissionYear() { return admissionYear; }
    public StudentStatus getStatus() { return status; }
}
