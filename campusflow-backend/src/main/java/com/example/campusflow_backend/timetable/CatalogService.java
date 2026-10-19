package com.example.campusflow_backend.timetable;
import com.example.campusflow_backend.repository.DepartmentRepository;
import com.example.campusflow_backend.dto.PageResponse;
import com.example.campusflow_backend.entity.Department;
import com.example.campusflow_backend.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.*;
import static com.example.campusflow_backend.timetable.SchedulingDtos.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@Service @Transactional(readOnly=true) @PreAuthorize("hasRole('ADMIN')")
public class CatalogService {
 private final SubjectRepository subjects; private final RoomRepository rooms; private final AcademicTermRepository terms;
 private final DepartmentRepository departments; private final TimetableSessionRepository sessions;
 public CatalogService(SubjectRepository subjects,RoomRepository rooms,AcademicTermRepository terms,DepartmentRepository departments,TimetableSessionRepository sessions){this.subjects=subjects;this.rooms=rooms;this.terms=terms;this.departments=departments;this.sessions=sessions;}
 private Department department(Long id){return departments.findById(id).orElseThrow(()->new ResourceNotFoundException("Department not found."));}
 static String literal(String value){return value.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");}
 @PreAuthorize("isAuthenticated()") public PageResponse<SubjectResponse> subjects(String name,Long departmentId,Boolean active,Pageable page){
 Specification<Subject> spec=(root,query,cb)->{List<Predicate> p=new ArrayList<>();if(name!=null&&!name.isBlank())p.add(cb.or(cb.like(cb.lower(root.get("name")),"%"+literal(name)+"%",'!'),cb.like(cb.lower(root.get("code")),"%"+literal(name)+"%",'!')));if(departmentId!=null)p.add(cb.equal(root.get("department").get("id"),departmentId));if(active!=null)p.add(cb.equal(root.get("active"),active));return cb.and(p.toArray(Predicate[]::new));};
 return PageResponse.from(subjects.findAll(spec,page).map(SubjectResponse::from));}
 @PreAuthorize("isAuthenticated()") public SubjectResponse subject(Long id){return SubjectResponse.from(requireSubject(id));}
 Subject requireSubject(Long id){return subjects.findById(id).orElseThrow(()->new ResourceNotFoundException("Subject not found."));}
 @Transactional public SubjectResponse saveSubject(Long id,SubjectRequest r){Subject s=id==null?new Subject(r.code(),r.name(),department(r.departmentId()),r.description(),r.active()):requireSubject(id);if(id!=null&&!s.getDepartment().getId().equals(r.departmentId())&&sessions.existsBySubjectId(id))throw new ConflictException("A scheduled subject cannot move departments.");s.revise(r.code(),r.name(),department(r.departmentId()),r.description(),r.active());return SubjectResponse.from(subjects.saveAndFlush(s));}
 @Transactional public SubjectResponse subjectStatus(Long id,boolean active){Subject s=requireSubject(id);s.revise(s.getCode(),s.getName(),s.getDepartment(),s.getDescription(),active);return SubjectResponse.from(subjects.saveAndFlush(s));}
 @PreAuthorize("isAuthenticated()") public PageResponse<RoomResponse> rooms(String name,String building,RoomType type,Boolean active,Pageable page){Specification<Room> spec=(root,query,cb)->{List<Predicate> p=new ArrayList<>();if(name!=null&&!name.isBlank())p.add(cb.or(cb.like(cb.lower(root.get("name")),"%"+literal(name)+"%",'!'),cb.like(cb.lower(root.get("code")),"%"+literal(name)+"%",'!')));if(building!=null&&!building.isBlank())p.add(cb.like(cb.lower(root.get("building")),"%"+literal(building)+"%",'!'));if(type!=null)p.add(cb.equal(root.get("type"),type));if(active!=null)p.add(cb.equal(root.get("active"),active));return cb.and(p.toArray(Predicate[]::new));};return PageResponse.from(rooms.findAll(spec,page).map(RoomResponse::from));}
 Room requireRoom(Long id){return rooms.findById(id).orElseThrow(()->new ResourceNotFoundException("Room not found."));}
 @PreAuthorize("isAuthenticated()") public RoomResponse room(Long id){return RoomResponse.from(requireRoom(id));}
 @Transactional public RoomResponse saveRoom(Long id,RoomRequest r){Room room=id==null?new Room(r.code(),r.name(),r.building(),r.floor(),r.type(),r.capacity(),r.active()):requireRoom(id);room.revise(r.code(),r.name(),r.building(),r.floor(),r.type(),r.capacity(),r.active());return RoomResponse.from(rooms.saveAndFlush(room));}
 @Transactional public RoomResponse roomStatus(Long id,boolean active){Room r=requireRoom(id);r.revise(r.getCode(),r.getName(),r.getBuilding(),r.getFloor(),r.getType(),r.getCapacity(),active);return RoomResponse.from(rooms.saveAndFlush(r));}
 @PreAuthorize("isAuthenticated()") public PageResponse<TermResponse> terms(Long departmentId,Boolean active,Pageable page){Specification<AcademicTerm> spec=(root,query,cb)->{List<Predicate> p=new ArrayList<>();if(departmentId!=null)p.add(cb.equal(root.get("department").get("id"),departmentId));if(active!=null)p.add(cb.equal(root.get("active"),active));return cb.and(p.toArray(Predicate[]::new));};return PageResponse.from(terms.findAll(spec,page).map(TermResponse::from));}
 @PreAuthorize("isAuthenticated()") public TermResponse term(Long id){return TermResponse.from(requireTerm(id));} AcademicTerm requireTerm(Long id){return terms.findById(id).orElseThrow(()->new ResourceNotFoundException("Term not found."));}
 @Transactional public TermResponse createTerm(TermRequest r){ScheduleRules.dates(r.startDate(),r.endDate());return TermResponse.from(terms.saveAndFlush(new AcademicTerm(r.name().strip(),department(r.departmentId()),r.semester(),r.startDate(),r.endDate(),r.active())));}
}
