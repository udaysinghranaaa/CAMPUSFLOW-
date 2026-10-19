package com.example.campusflow_backend.timetable;
import jakarta.persistence.*;
import java.time.*;
import com.example.campusflow_backend.entity.*;
import static com.example.campusflow_backend.timetable.TimetableTypes.*;
@Entity @Table(name="rooms")
public class Room extends AuditedEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=30) private String code;
 @Column(nullable=false,length=150) private String name;
 @Column(nullable=false,length=100) private String building;
 @Column(length=30) private String floor;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private RoomType type;
 @Column(nullable=false) private int capacity;
 @Column(nullable=false) private boolean active;
 protected Room() { }
 public Room(String code, String name, String building, String floor, RoomType type, int capacity, boolean active) { revise(code, name, building, floor, type, capacity, active); }
 public void revise(String code, String name, String building, String floor, RoomType type, int capacity, boolean active) { this.code=code; this.name=name; this.building=building; this.floor=floor; this.type=type; this.capacity=capacity; this.active=active; }
 public Long getId() { return id; }
 public String getCode() { return code; }
 public String getName() { return name; }
 public String getBuilding() { return building; }
 public String getFloor() { return floor; }
 public RoomType getType() { return type; }
 public int getCapacity() { return capacity; }
 public boolean getActive() { return active; }
}

