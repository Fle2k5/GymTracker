package com.example.gymtracker.web;

import com.example.gymtracker.service.GymTrackerService;
import com.example.gymtracker.service.AiCoachService;
import com.example.gymtracker.service.AppException;
import com.example.gymtracker.service.WellnessService;
import com.example.gymtracker.service.AssistantConversationService;
import com.example.gymtracker.service.FitnessProfileService;
import com.example.gymtracker.service.MealAnalysisService;
import com.example.gymtracker.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Controller
public class DashboardController {
    private static final Set<String> TABS = Set.of("overview", "exercises", "schedules", "history", "nutrition", "milestones");
    private final GymTrackerService service;
    private final AiCoachService aiCoach;
    private final MessageSource messages;
    private final WellnessService wellness;
    private final AssistantConversationService assistant;
    private final MealAnalysisService mealAnalysis;
    private final FitnessProfileService profiles;
    private final CurrentUserService currentUser;

    public DashboardController(GymTrackerService service, AiCoachService aiCoach,
                               MessageSource messages, WellnessService wellness,
                               AssistantConversationService assistant, MealAnalysisService mealAnalysis,
                               FitnessProfileService profiles, CurrentUserService currentUser) {
        this.service = service;
        this.aiCoach = aiCoach;
        this.messages = messages;
        this.wellness = wellness;
        this.assistant = assistant;
        this.mealAnalysis = mealAnalysis;
        this.profiles = profiles;
        this.currentUser = currentUser;
    }

    @GetMapping("/")
    public String dashboard(@RequestParam(defaultValue = "overview") String tab,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false) LocalDate from,
                            @RequestParam(required = false) LocalDate to,
                            Model model, HttpSession session) {
        model.addAttribute("activeTab", TABS.contains(tab) ? tab : "overview");
        model.addAttribute("accountName", currentUser.require().getDisplayName());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("exercises", service.exercises());
        model.addAttribute("schedules", service.schedules());
        model.addAttribute("history", service.history(q, from, to));
        model.addAttribute("historyQuery", q == null ? "" : q);
        model.addAttribute("historyFrom", from);
        model.addAttribute("historyTo", to);
        model.addAttribute("latestHistory", service.latestHistoryByExercise());
        model.addAttribute("dashboardStats", service.dashboardStats());
        model.addAttribute("nutritionEntries", wellness.nutritionEntries());
        model.addAttribute("nutritionToday", wellness.nutritionSummary(LocalDate.now()));
        model.addAttribute("milestones", wellness.milestones());
        model.addAttribute("assistantMessages", chatMessages(session));
        model.addAttribute("assistantPending", session.getAttribute("assistantPending"));
        model.addAttribute("mealPending", session.getAttribute("mealPending"));
        model.addAttribute("fitnessProfile", profiles.current().orElse(null));
        return "dashboard";
    }

    @PostMapping("/assistant/chat")
    public String assistantChat(@RequestParam String message, RedirectAttributes flash, HttpSession session) {
        try {
            var reply = assistant.reply(message, LocaleContextHolder.getLocale(), chatMessages(session));
            addChat(session, new AssistantConversationService.ChatMessage("user", message.trim()));
            addChat(session, new AssistantConversationService.ChatMessage("assistant", reply.text()));
            session.removeAttribute("assistantPending");
            session.setAttribute("assistantPending", reply.pendingAction());
        } catch (AppException ex) {
            flash.addFlashAttribute("error", messages.getMessage(ex.getCode(), ex.getArguments(), LocaleContextHolder.getLocale()));
        }
        return "redirect:/?tab=overview";
    }

    @PostMapping("/assistant/action/confirm")
    public String confirmAssistantAction(@RequestParam String token, RedirectAttributes flash, HttpSession session) {
        var pending = (AssistantConversationService.PendingAction) session.getAttribute("assistantPending");
        try {
            if (pending == null || !pending.ownerId().equals(currentUser.require().getId()) || !pending.token().equals(token))
                throw new AppException("error.assistant.action.expired");
            session.removeAttribute("assistantPending");
            String result = assistant.execute(pending, token, LocaleContextHolder.getLocale());
            addChat(session, new AssistantConversationService.ChatMessage("assistant", result));
            flash.addFlashAttribute("success", result);
        } catch (AppException ex) {
            flash.addFlashAttribute("error", messages.getMessage(ex.getCode(), ex.getArguments(), LocaleContextHolder.getLocale()));
        }
        return "redirect:/?tab=overview";
    }

    @PostMapping("/assistant/action/cancel")
    public String cancelAssistantAction(@RequestParam String token, HttpSession session) {
        var pending = (AssistantConversationService.PendingAction) session.getAttribute("assistantPending");
        if (pending != null && pending.ownerId().equals(currentUser.require().getId()) && pending.token().equals(token))
            session.removeAttribute("assistantPending");
        return "redirect:/?tab=overview";
    }

    @PostMapping("/nutrition/analyze")
    public String analyzeMeal(@RequestParam String mealType, @RequestParam String description,
                              RedirectAttributes flash, HttpSession session) {
        try {
            var analyzed = mealAnalysis.analyze(mealType, description, LocaleContextHolder.getLocale());
            session.setAttribute("mealPending", analyzed);
        }
        catch (AppException ex) { flash.addFlashAttribute("error", messages.getMessage(ex.getCode(), ex.getArguments(), LocaleContextHolder.getLocale())); }
        return "redirect:/?tab=nutrition";
    }

    @PostMapping("/nutrition/analyze/save")
    public String saveAnalyzedMeal(@RequestParam String token, RedirectAttributes flash, HttpSession session) {
        var pending = (MealAnalysisService.PendingMeal) session.getAttribute("mealPending");
        if (pending == null || !pending.ownerId().equals(currentUser.require().getId()) || !pending.token().equals(token)) {
            flash.addFlashAttribute("error", message("error.meal.analysis.expired"));
        } else {
            session.removeAttribute("mealPending");
            if (java.time.Instant.now().isAfter(pending.expiresAt())) {
                flash.addFlashAttribute("error", message("error.meal.analysis.expired"));
            } else {
                var e = pending.estimate();
                try {
                    wellness.addNutrition(LocalDate.now(), pending.mealType(), abbreviate(pending.description(), 160),
                            e.calories(), e.protein(), e.carbs(), e.fat(), abbreviate(e.explanation(), 400));
                    flash.addFlashAttribute("success", message("success.nutrition.analyzed"));
                } catch (RuntimeException ex) {
                    flash.addFlashAttribute("error", message("error.invalidInput"));
                }
            }
        }
        return "redirect:/?tab=nutrition";
    }

    @PostMapping("/nutrition/analyze/cancel")
    public String cancelAnalyzedMeal(@RequestParam String token, HttpSession session) {
        var pending = (MealAnalysisService.PendingMeal) session.getAttribute("mealPending");
        if (pending != null && pending.ownerId().equals(currentUser.require().getId()) && pending.token().equals(token))
            session.removeAttribute("mealPending");
        return "redirect:/?tab=nutrition";
    }

    @PostMapping("/profile")
    public String saveProfile(@RequestParam String sex, @RequestParam double heightCm, @RequestParam double weightKg,
                              RedirectAttributes flash) {
        return run("overview", flash, "success.profile.saved", () -> profiles.save(sex, heightCm, weightKg));
    }

    @PostMapping("/exercises")
    public String addExercise(@RequestParam String name, @RequestParam int weight, @RequestParam int reps,
                              @RequestParam(required = false) LocalDate date, RedirectAttributes flash) {
        return run("exercises", flash, "success.exercise.added",
                () -> service.addExercise(name, weight, reps, date));
    }

    @PostMapping("/exercises/{id}/update")
    public String updateExercise(@PathVariable long id, @RequestParam String name, @RequestParam int weight,
                                 @RequestParam int reps, @RequestParam(required = false) LocalDate date,
                                 RedirectAttributes flash) {
        return runAchievement("exercises", flash, "success.exercise.updated",
                () -> service.updateExercise(id, name, weight, reps, date));
    }

    @PostMapping("/exercises/{id}/delete")
    public String deleteExercise(@PathVariable long id, RedirectAttributes flash) {
        return run("exercises", flash, "success.exercise.deleted", () -> service.deleteExercise(id));
    }

    @PostMapping("/exercises/{id}/history")
    public String saveHistory(@PathVariable long id, @RequestParam(required = false) LocalDate date,
                              RedirectAttributes flash) {
        return runAchievement("exercises", flash, "success.history.saved", () -> service.saveExerciseToHistory(id, date));
    }

    @PostMapping("/history/{id}/delete")
    public String deleteHistory(@PathVariable long id, RedirectAttributes flash) {
        return run("history", flash, "success.history.deleted", () -> service.deleteHistory(id));
    }

    @PostMapping("/schedules")
    public String addSchedule(@RequestParam String day, @RequestParam String muscleGroup, RedirectAttributes flash) {
        return run("schedules", flash, "success.schedule.added", () -> service.addSchedule(day, muscleGroup));
    }

    @PostMapping("/schedules/{id}/update")
    public String updateSchedule(@PathVariable long id, @RequestParam String day, @RequestParam String muscleGroup,
                                 RedirectAttributes flash) {
        return run("schedules", flash, "success.schedule.updated", () -> service.updateSchedule(id, day, muscleGroup));
    }

    @PostMapping("/schedules/{id}/delete")
    public String deleteSchedule(@PathVariable long id, RedirectAttributes flash) {
        return run("schedules", flash, "success.schedule.deleted", () -> service.deleteSchedule(id));
    }

    @PostMapping("/schedules/{id}/exercises")
    public String addScheduleExercise(@PathVariable long id, @RequestParam long exerciseId, RedirectAttributes flash) {
        return run("schedules", flash, "success.scheduleExercise.added", () -> service.addScheduleExercise(id, exerciseId));
    }

    @PostMapping("/schedule-exercises/{id}/delete")
    public String deleteScheduleExercise(@PathVariable long id, RedirectAttributes flash) {
        return run("schedules", flash, "success.scheduleExercise.deleted", () -> service.deleteScheduleExercise(id));
    }

    @PostMapping("/schedule-exercises/{id}/record")
    public String recordWorkout(@PathVariable long id, @RequestParam int weight, @RequestParam int reps,
                                @RequestParam(required = false) LocalDate date, RedirectAttributes flash) {
        return runAchievement("schedules", flash, "success.workout.saved",
                () -> service.recordScheduledWorkout(id, weight, reps, date));
    }

    @PostMapping("/nutrition")
    public String addNutrition(@RequestParam(required = false) LocalDate date,
                               @RequestParam String mealType, @RequestParam String foodName,
                               @RequestParam int calories, @RequestParam int protein,
                               @RequestParam int carbs, @RequestParam int fat,
                               @RequestParam(required = false) String notes, RedirectAttributes flash) {
        return run("nutrition", flash, "success.nutrition.added",
                () -> wellness.addNutrition(date, mealType, foodName, calories, protein, carbs, fat, notes));
    }

    @PostMapping("/nutrition/{id}/delete")
    public String deleteNutrition(@PathVariable long id, RedirectAttributes flash) {
        return run("nutrition", flash, "success.nutrition.deleted", () -> wellness.deleteNutrition(id));
    }

    @PostMapping("/milestones")
    public String addMilestone(@RequestParam String title, @RequestParam(required = false) LocalDate date,
                               @RequestParam(required = false) String description,
                               @RequestParam("image") MultipartFile image, RedirectAttributes flash) {
        return run("milestones", flash, "success.milestone.added",
                () -> wellness.addMilestone(title, date, description, image));
    }

    @GetMapping("/milestones/{id}/image")
    public ResponseEntity<byte[]> milestoneImage(@PathVariable long id) {
        try {
            var milestone = wellness.milestone(id);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(milestone.getImageType()))
                    .cacheControl(CacheControl.noStore())
                    .body(milestone.getImageData());
        } catch (AppException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/milestones/{id}/delete")
    public String deleteMilestone(@PathVariable long id, RedirectAttributes flash) {
        return run("milestones", flash, "success.milestone.deleted", () -> wellness.deleteMilestone(id));
    }

    @PostMapping("/coach/recommend")
    public String coach(@RequestParam(defaultValue = "muscle") String goal,
                        @RequestParam(defaultValue = "45") int duration,
                        @RequestParam(defaultValue = "home") String equipment,
                        @RequestParam(required = false) String limitations,
                        RedirectAttributes flash) {
        var advice = aiCoach.recommend(goal, Math.max(15, Math.min(duration, 180)), equipment,
                limitations, LocaleContextHolder.getLocale());
        flash.addFlashAttribute("coachResult", advice.text());
        flash.addFlashAttribute("coachAiPowered", advice.aiPowered());
        return "redirect:/?tab=overview";
    }

    private String run(String tab, RedirectAttributes flash, String success, Runnable action) {
        try {
            action.run();
            flash.addFlashAttribute("success", message(success));
        } catch (AppException ex) {
            flash.addFlashAttribute("error", messages.getMessage(ex.getCode(), ex.getArguments(), LocaleContextHolder.getLocale()));
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", message("error.invalidInput"));
        }
        return "redirect:/?tab=" + tab;
    }

    private String runAchievement(String tab, RedirectAttributes flash, String success,
                                  Supplier<GymTrackerService.WorkoutAchievement> action) {
        try {
            var achievement = action.get();
            flash.addFlashAttribute("success", message(success));
            if (achievement.any()) {
                String code = achievement.newWeightRecord() && achievement.newRepRecord()
                        ? "achievement.both" : achievement.newWeightRecord() ? "achievement.weight" : "achievement.reps";
                flash.addFlashAttribute("personalRecord", true);
                flash.addFlashAttribute("recordMessage", messages.getMessage(code,
                        new Object[]{achievement.exerciseName(), achievement.weight(), achievement.reps()},
                        LocaleContextHolder.getLocale()));
            }
        } catch (AppException ex) {
            flash.addFlashAttribute("error", messages.getMessage(ex.getCode(), ex.getArguments(), LocaleContextHolder.getLocale()));
        }
        return "redirect:/?tab=" + tab;
    }

    private String message(String code) {
        return messages.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    @SuppressWarnings("unchecked")
    private List<AssistantConversationService.ChatMessage> chatMessages(HttpSession session) {
        Object current = session.getAttribute("assistantMessages");
        if (current instanceof List<?>) return (List<AssistantConversationService.ChatMessage>) current;
        List<AssistantConversationService.ChatMessage> created = new ArrayList<>();
        session.setAttribute("assistantMessages", created);
        return created;
    }

    private void addChat(HttpSession session, AssistantConversationService.ChatMessage message) {
        List<AssistantConversationService.ChatMessage> values = chatMessages(session);
        values.add(message);
        while (values.size() > 20) values.remove(0);
        session.setAttribute("assistantMessages", values);
    }

    private String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max - 1) + "…";
    }
}
