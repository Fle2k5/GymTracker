package com.example.gymtracker.repository;

import com.example.gymtracker.model.FitnessProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FitnessProfileRepository extends JpaRepository<FitnessProfile, Long> {
    Optional<FitnessProfile> findByOwnerId(Long ownerId);
}
