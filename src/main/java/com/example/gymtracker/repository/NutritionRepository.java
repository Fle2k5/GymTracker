package com.example.gymtracker.repository;

import com.example.gymtracker.model.NutritionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface NutritionRepository extends JpaRepository<NutritionEntry, Long> {
    List<NutritionEntry> findAllByOwnerIdOrderByDateDescIdDesc(Long ownerId);
    List<NutritionEntry> findByOwnerIdAndDateOrderByIdDesc(Long ownerId, LocalDate date);
    boolean existsByIdAndOwnerId(Long id, Long ownerId);
    void deleteByIdAndOwnerId(Long id, Long ownerId);
}
