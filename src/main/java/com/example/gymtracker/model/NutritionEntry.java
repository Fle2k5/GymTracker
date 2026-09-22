package com.example.gymtracker.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "nutrition_entries")
public class NutritionEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;
    @Column(name = "entry_date", nullable = false) private LocalDate date;
    @Column(name = "meal_type", nullable = false, length = 40) private String mealType;
    @Column(name = "food_name", nullable = false, length = 160) private String foodName;
    @Column(nullable = false) private int calories;
    @Column(nullable = false) private int protein;
    @Column(nullable = false) private int carbs;
    @Column(nullable = false) private int fat;
    @Column(length = 400) private String notes;

    protected NutritionEntry() {}

    public NutritionEntry(AppUser owner, LocalDate date, String mealType, String foodName, int calories,
                          int protein, int carbs, int fat, String notes) {
        this.owner = owner;
        this.date = date; this.mealType = mealType; this.foodName = foodName;
        this.calories = calories; this.protein = protein; this.carbs = carbs; this.fat = fat; this.notes = notes;
    }

    public Long getId() { return id; }
    public AppUser getOwner() { return owner; }
    public LocalDate getDate() { return date; }
    public String getMealType() { return mealType; }
    public String getFoodName() { return foodName; }
    public int getCalories() { return calories; }
    public int getProtein() { return protein; }
    public int getCarbs() { return carbs; }
    public int getFat() { return fat; }
    public String getNotes() { return notes; }
}
