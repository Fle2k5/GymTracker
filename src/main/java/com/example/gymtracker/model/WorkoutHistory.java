package com.example.gymtracker.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "workout_history")
public class WorkoutHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 120)
    private String exerciseName;

    @Column(nullable = false)
    private int weight;

    @Column(nullable = false)
    private int reps;

    @Column(name = "workout_date", nullable = false)
    private LocalDate date;

    protected WorkoutHistory() {}

    public WorkoutHistory(AppUser owner, String exerciseName, int weight, int reps, LocalDate date) {
        this.owner = owner;
        this.exerciseName = exerciseName;
        this.weight = weight;
        this.reps = reps;
        this.date = date;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getExerciseName() { return exerciseName; }
    public int getWeight() { return weight; }
    public int getReps() { return reps; }
    public LocalDate getDate() { return date; }
}
