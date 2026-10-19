package com.example.campusflow_backend.timetable;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.*;
import java.time.*;
import java.util.*;
import com.example.campusflow_backend.dto.PageResponse;
import static com.example.campusflow_backend.timetable.SchedulingDtos.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@Repository
public class ScheduleReadRepository {
 private final NamedParameterJdbcTemplate jdbc;
 public ScheduleReadRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Occurrence> week(Long facultyId,Long sectionId,Integer semester,LocalDate monday){return occurrences(facultyId,sectionId,semester,monday,false,null);}
 public List<Occurrence> adminWeek(Long termId,LocalDate monday){return occurrences(null,null,null,monday,true,termId);}
 private List<Occurrence> occurrences(Long facultyId,Long sectionId,Integer semester,LocalDate monday,boolean drafts,Long termId){
 String sql="""
 select s.id,a.occurrence_date,a.start_time,a.end_time,sb.name subject_name,f.full_name,r.code room_code,
 a.section_id,sec.name section_name,s.session_type,s.status
 from schedule_allocations a join timetable_sessions s on s.id=a.session_id
 join academic_terms t on t.id=s.academic_term_id join subjects sb on sb.id=s.subject_id
 join faculty f on f.id=a.faculty_id join rooms r on r.id=a.room_id join academic_sections sec on sec.id=a.section_id
 where s.status<>'CANCELLED' and t.active and a.occurrence_date between :from and :to
 """;
 MapSqlParameterSource p=new MapSqlParameterSource().addValue("from",monday).addValue("to",monday.plusDays(6));
 if(!drafts)sql+=" and s.status='PUBLISHED'";if(termId!=null){sql+=" and t.id=:term";p.addValue("term",termId);} if(facultyId!=null){sql+=" and a.faculty_id=:faculty";p.addValue("faculty",facultyId);}if(sectionId!=null){sql+=" and a.section_id=:section";p.addValue("section",sectionId);}if(semester!=null){sql+=" and t.semester=:semester";p.addValue("semester",semester);}
 return jdbc.query(sql+" order by a.occurrence_date,a.start_time,s.id",p,(r,n)->new Occurrence(r.getLong("id"),r.getObject("occurrence_date",LocalDate.class),r.getObject("start_time",LocalTime.class),r.getObject("end_time",LocalTime.class),r.getString("subject_name"),r.getString("full_name"),r.getString("room_code"),r.getLong("section_id"),r.getString("section_name"),SessionType.valueOf(r.getString("session_type")),SessionStatus.valueOf(r.getString("status"))));
 }
 private static final String FINDER="""
 with evaluated as (
 select f.id,f.full_name,d.code department_code,f.designation,
 array(select distinct su.name from schedule_allocations sa join timetable_sessions ts on ts.id=sa.session_id join subjects su on su.id=ts.subject_id
 where sa.faculty_id=f.id and ts.status='PUBLISHED' order by su.name) subjects,
 case
 when exists(select 1 from schedule_allocations a where a.faculty_id=f.id and a.block_id is not null
 and a.occurrence_date=:date and a.start_time<:end and :start<a.end_time) then 'UNAVAILABLE'
 when exists(select 1 from schedule_allocations a join timetable_sessions s on s.id=a.session_id
 where a.faculty_id=f.id and s.status='PUBLISHED' and a.occurrence_date=:date and a.start_time<:end and :start<a.end_time) then 'BUSY'
 when exists(select 1 from schedule_allocations a join timetable_sessions s on s.id=a.session_id
 where a.faculty_id=f.id and s.status='DRAFT' and a.occurrence_date=:date and a.start_time<:end and :start<a.end_time) then 'UNKNOWN'
 when exists(select 1 from faculty_schedule_coverage c join academic_terms t on t.id=c.academic_term_id
 where c.faculty_id=f.id and t.active and :date between t.start_date and t.end_date
 and (c.day_mask & :mask)<>0 and c.start_time<=:start and c.end_time>=:end) then 'FREE'
 else 'UNKNOWN' end state
 from faculty f join departments d on d.id=f.department_id where f.active
 """;
 public PageResponse<FinderFaculty> finder(String name,Long departmentId,Long subjectId,AvailabilityState state,LocalDate date,LocalTime start,LocalTime end,Long id,int page,int size){
 var p=new MapSqlParameterSource().addValue("date",date).addValue("start",start).addValue("end",end).addValue("mask",1<<(date.getDayOfWeek().getValue()-1));String sql=FINDER;
 if(name!=null&&!name.isBlank()){sql+=" and lower(f.full_name) like :name escape '!'";p.addValue("name","%"+CatalogService.literal(name)+"%");}
 if(departmentId!=null){sql+=" and f.department_id=:department";p.addValue("department",departmentId);}
 if(subjectId!=null){sql+=" and exists(select 1 from schedule_allocations sa join timetable_sessions ss on ss.id=sa.session_id where sa.faculty_id=f.id and ss.subject_id=:subject and ss.status='PUBLISHED')";p.addValue("subject",subjectId);}
 if(id!=null){sql+=" and f.id=:id";p.addValue("id",id);}sql+=") ";String where=state==null?"":" where state=:state";if(state!=null)p.addValue("state",state.name());
 Long count=jdbc.queryForObject(sql+"select count(*) from evaluated"+where,p,Long.class);
 var rows=jdbc.query(sql+"select * from evaluated"+where+" order by full_name,id limit :limit offset :offset",p.addValue("limit",size).addValue("offset",(long)page*size),(r,n)->{
 var a=r.getArray("subjects");List<String> subjects=a==null?List.of():List.of((String[])a.getArray());if(a!=null)a.free();var value=AvailabilityState.valueOf(r.getString("state"));return new FinderFaculty(r.getLong("id"),r.getString("full_name"),r.getString("department_code"),r.getString("designation"),subjects,value,explanation(value),date,start,end);});
 long total=count==null?0:count;return new PageResponse<>(rows,page,size,total,(int)((total+size-1)/size),(long)(page+1)*size>=total);
 }
 public static String explanation(AvailabilityState state){return switch(state){case BUSY->"Overlapping published teaching session.";case UNAVAILABLE->"An explicit unavailable block overlaps this time.";case UNKNOWN->"Schedule coverage is incomplete or a draft allocation needs confirmation.";case FREE->"No known conflict within explicitly confirmed schedule coverage. This does not guarantee physical presence or availability on campus.";};}
}
