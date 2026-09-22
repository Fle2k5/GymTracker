package com.example.gymtracker.service;

import com.example.gymtracker.model.FitnessProfile;
import com.example.gymtracker.repository.FitnessProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
public class FitnessProfileService {
    private final FitnessProfileRepository repository;
    private final CurrentUserService currentUser;

    public FitnessProfileService(FitnessProfileRepository repository, CurrentUserService currentUser) {
        this.repository = repository; this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public Optional<ProfileResult> current() { return repository.findByOwnerId(currentUser.require().getId()).map(this::result); }

    public ProfileResult save(String sex, double heightCm, double weightKg) {
        String normalizedSex = sex == null ? "" : sex.toLowerCase();
        if (!normalizedSex.equals("male") && !normalizedSex.equals("female"))
            throw new AppException("error.profile.sex");
        if (!Double.isFinite(heightCm) || heightCm < 100 || heightCm > 250) throw new AppException("error.profile.height");
        if (!Double.isFinite(weightKg) || weightKg < 25 || weightKg > 400) throw new AppException("error.profile.weight");
        var owner = currentUser.require();
        FitnessProfile profile = repository.findByOwnerId(owner.getId())
                .orElse(new FitnessProfile(owner, normalizedSex, heightCm, weightKg));
        profile.update(normalizedSex, heightCm, weightKg);
        return result(repository.save(profile));
    }

    private ProfileResult result(FitnessProfile profile) {
        double rawBmi = profile.getWeightKg() / Math.pow(profile.getHeightCm() / 100.0, 2);
        double bmi = Math.round(rawBmi * 10.0) / 10.0;
        String category = bmi < 18.5 ? "underweight" : bmi < 25 ? "healthy" : bmi < 30 ? "overweight" : "obesity";
        return new ProfileResult(profile.getSex(), profile.getHeightCm(), profile.getWeightKg(), bmi, category);
    }

    public record ProfileResult(String sex, double heightCm, double weightKg, double bmi, String category) {}
}
