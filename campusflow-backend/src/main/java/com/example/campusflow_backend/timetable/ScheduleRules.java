package com.example.campusflow_backend.timetable;
import java.time.*;
import com.example.campusflow_backend.exception.InvalidRequestException;
public final class ScheduleRules {
 public static final ZoneId CAMPUS=ZoneId.of("Asia/Kolkata");
 private ScheduleRules() { }
 public static void times(LocalTime start,LocalTime end){if(start==null||end==null||!start.isBefore(end))throw new InvalidRequestException("Start time must be earlier than end time.");}
 public static void dates(LocalDate start,LocalDate end){if(start==null||end==null||end.isBefore(start)||end.toEpochDay()-start.toEpochDay()>366)throw new InvalidRequestException("Date range must be ordered and at most 366 days.");}
 public static boolean overlaps(LocalTime a,LocalTime b,LocalTime c,LocalTime d){return a.isBefore(d)&&c.isBefore(b);}
 public static LocalDate monday(LocalDate date){return date.minusDays(date.getDayOfWeek().getValue()-1L);}
 public static boolean covered(int mask,LocalDate date,LocalTime from,LocalTime to,LocalTime start,LocalTime end){return (mask & (1<<(date.getDayOfWeek().getValue()-1)))!=0&&!start.isBefore(from)&&!end.isAfter(to);}
}
