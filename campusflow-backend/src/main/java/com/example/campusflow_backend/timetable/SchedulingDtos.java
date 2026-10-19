package com.example.campusflow_backend.timetable;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
public final class SchedulingDtos {
 private SchedulingDtos() { }
 public record ActiveRequest(@NotNull Boolean active) { }
 public record SubjectRequest(@NotBlank @Size(max=30) String code,@NotBlank @Size(max=150) String name,
 @NotNull @Positive Long departmentId,@Size(max=1000) String description,@NotNull Boolean active) {
 public SubjectRequest {code=code==null?null:code.strip().toUpperCase(Locale.ROOT);name=name==null?null:name.strip();} }
 public record SubjectResponse(Long id,String code,String name,Long departmentId,String description,boolean active,Instant createdAt,Instant updatedAt) {
 static SubjectResponse from(Subject s){return new SubjectResponse(s.getId(),s.getCode(),s.getName(),s.getDepartment().getId(),s.getDescription(),s.getActive(),s.getCreatedAt(),s.getUpdatedAt());} }
 public record RoomRequest(@NotBlank @Size(max=30) String code,@NotBlank @Size(max=150) String name,
 @NotBlank @Size(max=100) String building,@Size(max=30) String floor,@NotNull RoomType type,@NotNull @Positive Integer capacity,@NotNull Boolean active) {
 public RoomRequest {code=code==null?null:code.strip().toUpperCase(Locale.ROOT);name=name==null?null:name.strip();building=building==null?null:building.strip();} }
 public record RoomResponse(Long id,String code,String name,String building,String floor,RoomType type,int capacity,boolean active,Instant createdAt,Instant updatedAt) {
 static RoomResponse from(Room r){return new RoomResponse(r.getId(),r.getCode(),r.getName(),r.getBuilding(),r.getFloor(),r.getType(),r.getCapacity(),r.getActive(),r.getCreatedAt(),r.getUpdatedAt());} }
 public record TermRequest(@NotBlank @Size(max=150) String name,@NotNull @Positive Long departmentId,@NotNull @Positive Integer semester,
 @NotNull LocalDate startDate,@NotNull LocalDate endDate,@NotNull Boolean active) { }
 public record TermResponse(Long id,String name,Long departmentId,int semester,LocalDate startDate,LocalDate endDate,boolean active) {
 static TermResponse from(AcademicTerm t){return new TermResponse(t.getId(),t.getName(),t.getDepartment().getId(),t.getSemester(),t.getStartDate(),t.getEndDate(),t.getActive());} }
 public record SessionRequest(@NotNull @Positive Long academicTermId,@NotNull @Positive Long sectionId,@NotNull @Positive Long subjectId,
 @NotNull @Positive Long facultyId,@NotNull @Positive Long roomId,@NotNull @Min(1) @Max(7) Integer dayOfWeek,
 @NotNull LocalTime startTime,@NotNull LocalTime endTime,@NotNull LocalDate effectiveStartDate,@NotNull LocalDate effectiveEndDate,
 @NotNull SessionStatus status,@NotNull SessionType sessionType) { }
 public record SessionResponse(Long id,Long academicTermId,Long sectionId,String sectionName,Long subjectId,String subjectName,
 Long facultyId,String facultyName,Long roomId,String roomCode,int dayOfWeek,LocalTime startTime,LocalTime endTime,
 LocalDate effectiveStartDate,LocalDate effectiveEndDate,SessionStatus status,SessionType sessionType) {
 static SessionResponse from(TimetableSession s){return new SessionResponse(s.getId(),s.getTerm().getId(),s.getSection().getId(),s.getSection().getName(),s.getSubject().getId(),s.getSubject().getName(),s.getFaculty().getId(),s.getFaculty().getFullName(),s.getRoom().getId(),s.getRoom().getCode(),s.getDayOfWeek(),s.getStartTime(),s.getEndTime(),s.getEffectiveStartDate(),s.getEffectiveEndDate(),s.getStatus(),s.getSessionType());} }
 public record ExceptionRequest(@NotNull LocalDate exceptionDate,@NotNull ExceptionType type,LocalTime startTime,LocalTime endTime,
 @Positive Long facultyId,@Positive Long roomId,@Size(max=500) String reason) { }
 public record ExceptionResponse(Long id,Long sessionId,LocalDate exceptionDate,ExceptionType type) { }
 public record CoverageRequest(@NotNull @Positive Long facultyId,@NotNull @Min(1) @Max(127) Integer dayMask,@NotNull LocalTime startTime,@NotNull LocalTime endTime) { }
 public record CoverageResponse(Long id,Long academicTermId,Long facultyId,int dayMask,LocalTime startTime,LocalTime endTime) { }
 public record BlockRequest(@NotNull @Positive Long facultyId,@NotNull LocalDate date,@NotNull LocalTime startTime,@NotNull LocalTime endTime,@Size(max=500) String reason) { }
 public record BlockResponse(Long id,Long facultyId,LocalDate date,LocalTime startTime,LocalTime endTime,String reason,boolean active) { }
 public record Occurrence(Long sessionId,LocalDate date,LocalTime startTime,LocalTime endTime,String subjectName,String facultyName,
 String roomCode,Long sectionId,String sectionName,SessionType sessionType,SessionStatus status) { }
 public record WeeklyTimetable(LocalDate weekStart,String timezone,List<Occurrence> sessions) { }
 public record FinderFaculty(Long id,String fullName,String departmentCode,String designation,List<String> subjects,AvailabilityState state,
 String explanation,LocalDate date,LocalTime startTime,LocalTime endTime) { }
}
