package com.example.gymtracker.service;

import com.example.gymtracker.model.Milestone;
import com.example.gymtracker.model.NutritionEntry;
import com.example.gymtracker.repository.MilestoneRepository;
import com.example.gymtracker.repository.NutritionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class WellnessService {
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private final NutritionRepository nutritionRepository;
    private final MilestoneRepository milestoneRepository;
    private final CurrentUserService currentUser;

    public WellnessService(NutritionRepository nutritionRepository, MilestoneRepository milestoneRepository,
                           CurrentUserService currentUser) {
        this.nutritionRepository = nutritionRepository;
        this.milestoneRepository = milestoneRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<NutritionEntry> nutritionEntries() { return nutritionRepository.findAllByOwnerIdOrderByDateDescIdDesc(currentUser.require().getId()); }

    @Transactional(readOnly = true)
    public NutritionSummary nutritionSummary(LocalDate date) {
        List<NutritionEntry> entries = nutritionRepository.findByOwnerIdAndDateOrderByIdDesc(currentUser.require().getId(), date);
        return new NutritionSummary(entries.stream().mapToInt(NutritionEntry::getCalories).sum(),
                entries.stream().mapToInt(NutritionEntry::getProtein).sum(),
                entries.stream().mapToInt(NutritionEntry::getCarbs).sum(),
                entries.stream().mapToInt(NutritionEntry::getFat).sum());
    }

    public void addNutrition(LocalDate date, String mealType, String foodName, int calories,
                             int protein, int carbs, int fat, String notes) {
        if (foodName == null || foodName.isBlank()) throw new AppException("error.food.required");
        if (mealType == null || mealType.isBlank()) throw new AppException("error.meal.required");
        if (calories < 0 || protein < 0 || carbs < 0 || fat < 0) throw new AppException("error.nutrition.invalid");
        nutritionRepository.save(new NutritionEntry(currentUser.require(), date == null ? LocalDate.now() : date, mealType.trim(),
                foodName.trim(), calories, protein, carbs, fat, notes == null ? "" : notes.trim()));
    }

    public void deleteNutrition(long id) {
        Long ownerId = currentUser.require().getId();
        if (!nutritionRepository.existsByIdAndOwnerId(id, ownerId)) throw new AppException("error.nutrition.notFound");
        nutritionRepository.deleteByIdAndOwnerId(id, ownerId);
    }

    @Transactional(readOnly = true)
    public List<Milestone> milestones() { return milestoneRepository.findAllByOwnerIdOrderByDateDescIdDesc(currentUser.require().getId()); }

    @Transactional(readOnly = true)
    public Milestone milestone(long id) {
        return milestoneRepository.findByIdAndOwnerId(id, currentUser.require().getId()).orElseThrow(() -> new AppException("error.milestone.notFound"));
    }

    public void addMilestone(String title, LocalDate date, String description, MultipartFile image) {
        if (title == null || title.isBlank()) throw new AppException("error.milestone.title.required");
        if (image == null || image.isEmpty()) throw new AppException("error.milestone.image.required");
        if (image.getSize() > MAX_IMAGE_BYTES) throw new AppException("error.milestone.image.size");
        String type = image.getContentType() == null ? "" : image.getContentType().toLowerCase();
        if (!IMAGE_TYPES.contains(type)) throw new AppException("error.milestone.image.type");
        try {
            milestoneRepository.save(new Milestone(currentUser.require(), title.trim(), date == null ? LocalDate.now() : date,
                    description == null ? "" : description.trim(), safeFilename(image.getOriginalFilename()), type, image.getBytes()));
        } catch (IOException ex) {
            throw new AppException("error.milestone.image.read");
        }
    }

    public void deleteMilestone(long id) {
        Long ownerId = currentUser.require().getId();
        if (!milestoneRepository.existsByIdAndOwnerId(id, ownerId)) throw new AppException("error.milestone.notFound");
        milestoneRepository.deleteByIdAndOwnerId(id, ownerId);
    }

    private String safeFilename(String value) {
        if (value == null || value.isBlank()) return "milestone-image";
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record NutritionSummary(int calories, int protein, int carbs, int fat) {}
}
