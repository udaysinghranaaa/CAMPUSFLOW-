package com.example.campusflow_backend.timetable;
public final class TimetableTypes {
 private TimetableTypes() { }
 public enum RoomType { CLASSROOM, LAB, SEMINAR }
 public enum SessionStatus { DRAFT, PUBLISHED, CANCELLED }
 public enum SessionType { LECTURE, PRACTICAL, TUTORIAL }
 public enum ExceptionType { CANCELLED, RESCHEDULED }
 public enum AvailabilityState { FREE, BUSY, UNKNOWN, UNAVAILABLE }
}
