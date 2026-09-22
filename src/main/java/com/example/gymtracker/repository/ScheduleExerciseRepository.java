package com.example.gymtracker.repository;

import com.example.gymtracker.model.ScheduleExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ScheduleExerciseRepository extends JpaRepository<ScheduleExercise, Long> {
    boolean existsByOwnerIdAndScheduleIdAndExerciseId(Long ownerId, Long scheduleId, Long exerciseId);
    Optional<ScheduleExercise> findByIdAndOwnerId(Long id, Long ownerId);
    boolean existsByIdAndOwnerId(Long id, Long ownerId);
    void deleteByIdAndOwnerId(Long id, Long ownerId);
}
