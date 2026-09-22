package com.example.gymtracker.repository;

import com.example.gymtracker.model.TrainingSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<TrainingSchedule, Long> {
    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    List<TrainingSchedule> findAllByOwnerIdOrderByIdAsc(Long ownerId);

    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    Optional<TrainingSchedule> findByIdAndOwnerId(Long id, Long ownerId);
    long countByOwnerId(Long ownerId);
}
