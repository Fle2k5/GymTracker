package com.example.gymtracker.service;

import com.example.gymtracker.model.*;
import com.example.gymtracker.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class GymTrackerService {
    private final ExerciseRepository exerciseRepository;
    private final HistoryRepository historyRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleExerciseRepository scheduleExerciseRepository;
    private final CurrentUserService currentUser;

    public GymTrackerService(ExerciseRepository exerciseRepository,
                             HistoryRepository historyRepository,
                             ScheduleRepository scheduleRepository,
                             ScheduleExerciseRepository scheduleExerciseRepository, CurrentUserService currentUser) {
        this.exerciseRepository = exerciseRepository;
        this.historyRepository = historyRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduleExerciseRepository = scheduleExerciseRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<Exercise> exercises() {
        return exerciseRepository.findAllByOwnerIdOrderByDateDescIdDesc(currentUser.require().getId());
    }

    public void addExercise(String name, int weight, int reps, LocalDate date) {
        String cleanedName = requireText(name, "error.exerciseName.required");
        validateWorkout(weight, reps);
        AppUser owner = currentUser.require();
        if (exerciseRepository.existsByOwnerIdAndNameIgnoreCase(owner.getId(), cleanedName)) {
            throw new AppException("error.exercise.exists", cleanedName);
        }
        LocalDate workoutDate = date == null ? LocalDate.now() : date;
        exerciseRepository.save(new Exercise(owner, cleanedName, weight, reps, workoutDate));
        historyRepository.save(new WorkoutHistory(owner, cleanedName, weight, reps, workoutDate));
    }

    public WorkoutAchievement updateExercise(long id, String name, int weight, int reps, LocalDate date) {
        Exercise exercise = getExercise(id);
        String cleanedName = requireText(name, "error.exerciseName.required");
        validateWorkout(weight, reps);
        Long ownerId = currentUser.require().getId();
        exerciseRepository.findByOwnerIdAndNameIgnoreCase(ownerId, cleanedName)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> { throw new AppException("error.exercise.exists", cleanedName); });
        exercise.setName(cleanedName);
        exercise.setWeight(weight);
        exercise.setReps(reps);
        exercise.setDate(date == null ? exercise.getDate() : date);
        return achievementFor(ownerId, cleanedName, weight, reps);
    }

    public void deleteExercise(long id) {
        exerciseRepository.delete(getExercise(id));
    }

    public WorkoutAchievement saveExerciseToHistory(long id, LocalDate date) {
        Exercise exercise = getExercise(id);
        AppUser owner = currentUser.require();
        WorkoutAchievement achievement = achievementFor(owner.getId(), exercise.getName(), exercise.getWeight(), exercise.getReps());
        historyRepository.save(new WorkoutHistory(owner, exercise.getName(), exercise.getWeight(), exercise.getReps(),
                date == null ? LocalDate.now() : date));
        return achievement;
    }

    @Transactional(readOnly = true)
    public List<WorkoutHistory> history() {
        return historyRepository.findAllByOwnerIdOrderByDateDescIdDesc(currentUser.require().getId());
    }

    @Transactional(readOnly = true)
    public List<WorkoutHistory> history(String query, LocalDate from, LocalDate to) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return history().stream()
                .filter(entry -> normalized.isEmpty()
                        || entry.getExerciseName().toLowerCase(Locale.ROOT).contains(normalized))
                .filter(entry -> from == null || !entry.getDate().isBefore(from))
                .filter(entry -> to == null || !entry.getDate().isAfter(to))
                .toList();
    }

    public void deleteHistory(long id) {
        Long ownerId = currentUser.require().getId();
        if (!historyRepository.existsByIdAndOwnerId(id, ownerId)) throw new AppException("error.history.notFound");
        historyRepository.deleteByIdAndOwnerId(id, ownerId);
    }

    @Transactional(readOnly = true)
    public List<TrainingSchedule> schedules() {
        return scheduleRepository.findAllByOwnerIdOrderByIdAsc(currentUser.require().getId());
    }

    public void addSchedule(String day, String muscleGroup) {
        scheduleRepository.save(new TrainingSchedule(currentUser.require(), requireText(day, "error.day.required"), requireText(muscleGroup, "error.muscle.required")));
    }

    public void updateSchedule(long id, String day, String muscleGroup) {
        TrainingSchedule schedule = getSchedule(id);
        schedule.setDay(requireText(day, "error.day.required"));
        schedule.setMuscleGroup(requireText(muscleGroup, "error.muscle.required"));
    }

    public void deleteSchedule(long id) {
        scheduleRepository.delete(getSchedule(id));
    }

    public void addScheduleExercise(long scheduleId, long exerciseId) {
        TrainingSchedule schedule = getSchedule(scheduleId);
        Exercise exercise = getExercise(exerciseId);
        AppUser owner = currentUser.require();
        if (scheduleExerciseRepository.existsByOwnerIdAndScheduleIdAndExerciseId(owner.getId(), scheduleId, exerciseId)) {
            throw new AppException("error.schedule.duplicateExercise");
        }
        ScheduleExercise item = new ScheduleExercise(owner, schedule, exercise);
        schedule.addExercise(item);
        scheduleExerciseRepository.save(item);
    }

    public void deleteScheduleExercise(long id) {
        Long ownerId = currentUser.require().getId();
        ScheduleExercise item = scheduleExerciseRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new AppException("error.scheduleExercise.notFound"));
        item.getSchedule().removeExercise(item);
        // orphanRemoval deletes the owning row at flush while keeping the in-memory graph consistent.
    }

    public WorkoutAchievement recordScheduledWorkout(long scheduledExerciseId, int weight, int reps, LocalDate date) {
        validateWorkout(weight, reps);
        AppUser owner = currentUser.require();
        ScheduleExercise scheduled = scheduleExerciseRepository.findByIdAndOwnerId(scheduledExerciseId, owner.getId())
                .orElseThrow(() -> new AppException("error.scheduleExercise.notFound"));
        if (!scheduled.getSchedule().getOwner().getId().equals(owner.getId())
                || !scheduled.getExercise().getOwner().getId().equals(owner.getId())) {
            throw new AppException("error.scheduleExercise.notFound");
        }
        LocalDate workoutDate = date == null ? LocalDate.now() : date;
        Exercise exercise = scheduled.getExercise();
        WorkoutAchievement achievement = achievementFor(owner.getId(), exercise.getName(), weight, reps);
        historyRepository.save(new WorkoutHistory(owner, exercise.getName(), weight, reps, workoutDate));
        exercise.setWeight(weight);
        exercise.setReps(reps);
        exercise.setDate(workoutDate);
        return achievement;
    }

    @Transactional(readOnly = true)
    public Map<String, WorkoutHistory> latestHistoryByExercise() {
        return historyRepository.findAllByOwnerIdOrderByDateDescIdDesc(currentUser.require().getId()).stream()
                .collect(Collectors.toMap(h -> h.getExerciseName().toLowerCase(Locale.ROOT), h -> h, (first, ignored) -> first));
    }

    @Transactional(readOnly = true)
    public DashboardStats dashboardStats() {
        Long ownerId = currentUser.require().getId();
        return new DashboardStats(exerciseRepository.countByOwnerId(ownerId), scheduleRepository.countByOwnerId(ownerId),
                historyRepository.countByOwnerIdAndDateGreaterThanEqual(ownerId, LocalDate.now().minusDays(6)),
                historyRepository.maxWeightOverall(ownerId), historyRepository.maxRepsOverall(ownerId));
    }

    private WorkoutAchievement achievementFor(Long ownerId, String name, int weight, int reps) {
        int previousWeight = historyRepository.maxWeightFor(ownerId, name);
        int previousReps = historyRepository.maxRepsFor(ownerId, name);
        return new WorkoutAchievement(weight > previousWeight, reps > previousReps, weight, reps, name);
    }

    private Exercise getExercise(long id) {
        return exerciseRepository.findByIdAndOwnerId(id, currentUser.require().getId()).orElseThrow(() -> new AppException("error.exercise.notFound"));
    }

    private TrainingSchedule getSchedule(long id) {
        return scheduleRepository.findByIdAndOwnerId(id, currentUser.require().getId()).orElseThrow(() -> new AppException("error.schedule.notFound"));
    }

    private static String requireText(String value, String errorCode) {
        if (value == null || value.isBlank()) throw new AppException(errorCode);
        return value.trim();
    }

    private static void validateWorkout(int weight, int reps) {
        if (weight < 0) throw new AppException("error.weight.invalid");
        if (reps <= 0) throw new AppException("error.reps.invalid");
    }

    public record WorkoutAchievement(boolean newWeightRecord, boolean newRepRecord, int weight, int reps, String exerciseName) {
        public boolean any() { return newWeightRecord || newRepRecord; }
    }

    public record DashboardStats(long exerciseCount, long scheduleCount, long workoutsThisWeek, int maxWeight, int maxReps) {}
}
