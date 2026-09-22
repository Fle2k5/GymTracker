package com.example.gymtracker.repository;

import com.example.gymtracker.model.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findAllByOwnerIdOrderByDateDescIdDesc(Long ownerId);
    Optional<Milestone> findByIdAndOwnerId(Long id, Long ownerId);
    boolean existsByIdAndOwnerId(Long id, Long ownerId);
    void deleteByIdAndOwnerId(Long id, Long ownerId);
}
