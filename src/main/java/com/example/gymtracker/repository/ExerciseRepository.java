package com.example.gymtracker.repository;

import com.example.gymtracker.model.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {
    List<Exercise> findAllByOwnerIdOrderByDateDescIdDesc(Long ownerId);
    Optional<Exercise> findByIdAndOwnerId(Long id, Long ownerId);
    Optional<Exercise> findByOwnerIdAndNameIgnoreCase(Long ownerId, String name);
    boolean existsByOwnerIdAndNameIgnoreCase(Long ownerId, String name);
    long countByOwnerId(Long ownerId);
}
