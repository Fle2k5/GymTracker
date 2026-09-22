package com.example.gymtracker;

import com.example.gymtracker.model.TrainingSchedule;
import com.example.gymtracker.repository.ExerciseRepository;
import com.example.gymtracker.repository.HistoryRepository;
import com.example.gymtracker.repository.AppUserRepository;
import com.example.gymtracker.model.AppUser;
import com.example.gymtracker.service.GymTrackerService;
import com.example.gymtracker.service.WellnessService;
import com.example.gymtracker.service.AssistantConversationService;
import com.example.gymtracker.service.FitnessProfileService;
import com.example.gymtracker.service.MealAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gymtest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "openai.api-key="
})
@ActiveProfiles("test")
@Transactional
class GymTrackerApplicationTests {
    @Autowired GymTrackerService service;
    @Autowired ExerciseRepository exerciseRepository;
    @Autowired HistoryRepository historyRepository;
    @Autowired WellnessService wellnessService;
    @Autowired AssistantConversationService assistantService;
    @Autowired FitnessProfileService profileService;
    @Autowired MealAnalysisService mealAnalysisService;
    @Autowired AppUserRepository userRepository;
    private AppUser owner;

    @BeforeEach
    void authenticate() {
        owner = userRepository.save(new AppUser("service-test@example.com", "Service Test", "!"));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(owner.getEmail(), "",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void addingExerciseAlsoCreatesHistory() {
        service.addExercise("Deadlift", 100, 5, LocalDate.of(2026, 9, 22));

        assertThat(exerciseRepository.findByOwnerIdAndNameIgnoreCase(owner.getId(), "deadlift")).isPresent();
        assertThat(historyRepository.findFirstByOwnerIdAndExerciseNameIgnoreCaseOrderByDateDescIdDesc(owner.getId(), "DEADLIFT"))
                .hasValueSatisfying(entry -> assertThat(entry.getWeight()).isEqualTo(100));
    }

    @Test
    void scheduledWorkoutCreatesHistoryAndUpdatesMasterExercise() {
        service.addExercise("Squat", 60, 8, LocalDate.now().minusDays(2));
        service.addSchedule("Thứ Hai", "Chân");
        TrainingSchedule schedule = service.schedules().get(0);
        Long squatId = exerciseRepository.findByOwnerIdAndNameIgnoreCase(owner.getId(), "Squat").orElseThrow().getId();
        service.addScheduleExercise(schedule.getId(), squatId);
        Long scheduledExerciseId = service.schedules().get(0).getExercises().get(0).getId();

        service.recordScheduledWorkout(scheduledExerciseId, 80, 6, LocalDate.now());

        assertThat(exerciseRepository.findByOwnerIdAndNameIgnoreCase(owner.getId(), "Squat")).get()
                .extracting("weight", "reps").containsExactly(80, 6);
        assertThat(service.history()).hasSize(2);
    }

    @Test
    void scheduledWorkoutDetectsWeightAndRepRecords() {
        service.addExercise("Bench Press", 60, 8, LocalDate.now().minusDays(2));
        service.addSchedule("Monday", "Chest");
        Long exerciseId = exerciseRepository.findByOwnerIdAndNameIgnoreCase(owner.getId(), "Bench Press").orElseThrow().getId();
        service.addScheduleExercise(service.schedules().get(0).getId(), exerciseId);
        Long scheduledExerciseId = service.schedules().get(0).getExercises().get(0).getId();

        var achievement = service.recordScheduledWorkout(scheduledExerciseId, 65, 10, LocalDate.now());

        assertThat(achievement.newWeightRecord()).isTrue();
        assertThat(achievement.newRepRecord()).isTrue();
        assertThat(achievement.any()).isTrue();
    }

    @Test
    void rejectsZeroReps() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.addExercise("Invalid", 20, 0, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reps");
    }

    @Test
    void historyFilterUsesNameAndDateRange() {
        service.addExercise("Bench Press", 60, 8, LocalDate.of(2026, 9, 20));
        service.addExercise("Squat", 80, 5, LocalDate.of(2026, 9, 22));

        assertThat(service.history("bench", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .singleElement()
                .extracting("exerciseName")
                .isEqualTo("Bench Press");
    }

    @Test
    void nutritionSummaryAddsDailyMacros() {
        LocalDate today = LocalDate.of(2026, 9, 22);
        wellnessService.addNutrition(today, "breakfast", "Eggs", 300, 24, 10, 18, "");
        wellnessService.addNutrition(today, "lunch", "Chicken rice", 700, 50, 85, 16, "");

        assertThat(wellnessService.nutritionSummary(today))
                .extracting("calories", "protein", "carbs", "fat")
                .containsExactly(1000, 74, 95, 34);
    }

    @Test
    void milestoneStoresAnImage() {
        MockMultipartFile image = new MockMultipartFile("image", "progress.png", "image/png", new byte[]{1, 2, 3});
        wellnessService.addMilestone("Month one", LocalDate.of(2026, 9, 22), "Progress", image);

        assertThat(wellnessService.milestones()).singleElement()
                .satisfies(item -> {
                    assertThat(item.getTitle()).isEqualTo("Month one");
                    assertThat(item.getImageData()).containsExactly(1, 2, 3);
                });
    }

    @Test
    void assistantRequiresConfirmationBeforeAddingExercise() {
        var reply = assistantService.reply("thêm bài Romanian Deadlift 70kg 8 reps", Locale.forLanguageTag("vi"));
        assertThat(exerciseRepository.findByOwnerIdAndNameIgnoreCase(owner.getId(), "Romanian Deadlift")).isEmpty();

        assistantService.execute(reply.pendingAction(), reply.pendingAction().token(), Locale.forLanguageTag("vi"));

        assertThat(exerciseRepository.findByOwnerIdAndNameIgnoreCase(owner.getId(), "Romanian Deadlift")).isPresent();
    }

    @Test
    void localMealAnalysisCalculatesKnownIngredients() {
        var pending = mealAnalysisService.analyze("lunch",
                "200g ức gà, 300g cơm, 100g bắp cải và 10g dầu ăn", Locale.forLanguageTag("vi"));

        assertThat(pending.estimate().calories()).isBetween(830, 840);
        assertThat(pending.estimate().protein()).isGreaterThan(70);
        assertThat(pending.estimate().carbs()).isGreaterThan(80);
    }

    @Test
    void bmiUsesAdultThresholdsForBothSexes() {
        var male = profileService.save("male", 174, 56);
        var female = profileService.save("female", 174, 56);

        assertThat(male.bmi()).isEqualTo(18.5);
        assertThat(female.bmi()).isEqualTo(male.bmi());
        assertThat(female.category()).isEqualTo("healthy");
    }

    @Test
    void japaneseDisplayedAddCommandCreatesPendingAction() {
        var reply = assistantService.reply("種目追加 Front Squat 40kg 10回", Locale.JAPANESE);
        assertThat(reply.pendingAction()).isNotNull();
        assertThat(reply.pendingAction().name()).isEqualTo("Front Squat");
    }

    @Test
    void naturalVietnameseAddCommandRemovesFillersFromName() {
        var reply = assistantService.reply("hãy thêm giúp tôi bài tập Squat Pause 55kg 6 reps", Locale.forLanguageTag("vi"));
        assertThat(reply.pendingAction()).isNotNull();
        assertThat(reply.pendingAction().name()).isEqualTo("Squat Pause");
    }

    @Test
    void profileRejectsNonFiniteNumbers() {
        assertThatThrownBy(() -> profileService.save("male", Double.NaN, 60))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("error.profile.height");
        assertThatThrownBy(() -> profileService.save("female", 165, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("error.profile.weight");
    }

    @Test
    void mealAnalysisRejectsUnknownMealType() {
        assertThatThrownBy(() -> mealAnalysisService.analyze("late-night", "200g rice", Locale.ENGLISH))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("error.meal.required");
    }

    @Test
    void usersCannotSeeOrChangeEachOthersExercises() {
        service.addExercise("Private Bench", 40, 10, LocalDate.now());
        Long firstId = service.exercises().get(0).getId();
        AppUser second = userRepository.save(new AppUser("second@example.com", "Second User", "!"));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(second.getEmail(), "",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(context);

        assertThat(service.exercises()).isEmpty();
        assertThatThrownBy(() -> service.deleteExercise(firstId))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("error.exercise.notFound");
        service.addExercise("Private Bench", 20, 12, LocalDate.now());
        assertThat(service.exercises()).singleElement();
    }

    @Test
    void anotherUserCannotConfirmPendingAssistantAction() {
        var pending = assistantService.reply("thêm bài Owner Only Row 25kg 10 reps", Locale.forLanguageTag("vi"))
                .pendingAction();
        AppUser second = userRepository.save(new AppUser("pending-second@example.com", "Pending Second", "!"));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(second.getEmail(), "",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(context);

        assertThatThrownBy(() -> assistantService.execute(pending, pending.token(), Locale.forLanguageTag("vi")))
                .isInstanceOf(com.example.gymtracker.service.AppException.class)
                .hasMessage("error.assistant.action.expired");
        assertThat(exerciseRepository.findByOwnerIdAndNameIgnoreCase(owner.getId(), "Owner Only Row")).isEmpty();
        assertThat(exerciseRepository.findByOwnerIdAndNameIgnoreCase(second.getId(), "Owner Only Row")).isEmpty();
    }
}
