package com.example.gymtracker.repository;

import com.example.gymtracker.model.WorkoutHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HistoryRepository extends JpaRepository<WorkoutHistory, Long> {
    List<WorkoutHistory> findAllByOwnerIdOrderByDateDescIdDesc(Long ownerId);
    Optional<WorkoutHistory> findFirstByOwnerIdAndExerciseNameIgnoreCaseOrderByDateDescIdDesc(Long ownerId, String exerciseName);
    boolean existsByIdAndOwnerId(Long id, Long ownerId);
    void deleteByIdAndOwnerId(Long id, Long ownerId);
    long countByOwnerIdAndDateGreaterThanEqual(Long ownerId, LocalDate date);

    @Query("select coalesce(max(h.weight), 0) from WorkoutHistory h where h.owner.id=:ownerId and lower(h.exerciseName)=lower(:name)")
    int maxWeightFor(@Param("ownerId") Long ownerId, @Param("name") String name);

    @Query("select coalesce(max(h.reps), 0) from WorkoutHistory h where h.owner.id=:ownerId and lower(h.exerciseName)=lower(:name)")
    int maxRepsFor(@Param("ownerId") Long ownerId, @Param("name") String name);

    @Query("select coalesce(max(h.weight), 0) from WorkoutHistory h where h.owner.id=:ownerId")
    int maxWeightOverall(@Param("ownerId") Long ownerId);

    @Query("select coalesce(max(h.reps), 0) from WorkoutHistory h where h.owner.id=:ownerId")
    int maxRepsOverall(@Param("ownerId") Long ownerId);
}
