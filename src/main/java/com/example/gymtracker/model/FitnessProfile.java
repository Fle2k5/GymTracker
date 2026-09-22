package com.example.gymtracker.model;

import jakarta.persistence.*;

@Entity
@Table(name = "fitness_profiles")
public class FitnessProfile {
    @Id private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private AppUser owner;
    @Column(nullable = false, length = 16) private String sex;
    @Column(name = "height_cm", nullable = false) private double heightCm;
    @Column(name = "weight_kg", nullable = false) private double weightKg;

    protected FitnessProfile() {}

    public FitnessProfile(AppUser owner, String sex, double heightCm, double weightKg) {
        this.id = owner.getId(); this.owner = owner; this.sex = sex; this.heightCm = heightCm; this.weightKg = weightKg;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getSex() { return sex; }
    public double getHeightCm() { return heightCm; }
    public double getWeightKg() { return weightKg; }
    public void update(String sex, double heightCm, double weightKg) {
        this.sex = sex; this.heightCm = heightCm; this.weightKg = weightKg;
    }
}
