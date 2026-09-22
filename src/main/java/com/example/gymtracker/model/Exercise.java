package com.example.gymtracker.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Entity
@Table(name = "exercises", uniqueConstraints = @UniqueConstraint(name = "uk_exercise_owner_name", columnNames = {"owner_id", "name"}))
public class Exercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @NotBlank(message = "Tên bài tập không được để trống")
    @Column(nullable = false, length = 120)
    private String name;

    @Min(value = 0, message = "Khối lượng phải từ 0 trở lên")
    @Column(nullable = false)
    private int weight;

    @Min(value = 1, message = "Số reps phải từ 1 trở lên")
    @Column(nullable = false)
    private int reps;

    @NotNull(message = "Ngày tập không được để trống")
    @Column(name = "workout_date", nullable = false)
    private LocalDate date;

    protected Exercise() {}

    public Exercise(AppUser owner, String name, int weight, int reps, LocalDate date) {
        this.owner = owner;
        this.name = name;
        this.weight = weight;
        this.reps = reps;
        this.date = date;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getName() { return name; }
    public int getWeight() { return weight; }
    public int getReps() { return reps; }
    public LocalDate getDate() { return date; }
    public void setName(String name) { this.name = name; }
    public void setWeight(int weight) { this.weight = weight; }
    public void setReps(int reps) { this.reps = reps; }
    public void setDate(LocalDate date) { this.date = date; }
}
