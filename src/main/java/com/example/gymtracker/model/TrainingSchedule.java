package com.example.gymtracker.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "schedules")
public class TrainingSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(name = "schedule_day", nullable = false, length = 80)
    private String day;

    @Column(nullable = false, length = 160)
    private String muscleGroup;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ScheduleExercise> exercises = new ArrayList<>();

    protected TrainingSchedule() {}

    public TrainingSchedule(AppUser owner, String day, String muscleGroup) {
        this.owner = owner;
        this.day = day;
        this.muscleGroup = muscleGroup;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getDay() { return day; }
    public String getMuscleGroup() { return muscleGroup; }
    public List<ScheduleExercise> getExercises() { return exercises; }
    public void setDay(String day) { this.day = day; }
    public void setMuscleGroup(String muscleGroup) { this.muscleGroup = muscleGroup; }
}
