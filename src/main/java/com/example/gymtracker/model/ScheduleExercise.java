package com.example.gymtracker.model;

import jakarta.persistence.*;

@Entity
@Table(name = "schedule_exercises", uniqueConstraints =
        @UniqueConstraint(name = "uk_schedule_exercise", columnNames = {"owner_id", "schedule_id", "exercise_id"}))
public class ScheduleExercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private TrainingSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    protected ScheduleExercise() {}

    public ScheduleExercise(AppUser owner, TrainingSchedule schedule, Exercise exercise) {
        this.owner = owner;
        this.schedule = schedule;
        this.exercise = exercise;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public TrainingSchedule getSchedule() { return schedule; }
    public Exercise getExercise() { return exercise; }
    public String getExerciseName() { return exercise.getName(); }
}
