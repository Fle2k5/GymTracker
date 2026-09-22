package com.example.gymtracker.service;

import com.example.gymtracker.model.Exercise;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AssistantConversationService {
    private static final Pattern ADD = Pattern.compile("(?iu)^(?:(?:hãy|vui lòng|please)\\s+)?(?:thêm|add|追加|種目追加)\\s+(?:(?:giúp tôi|cho tôi|bài tập|bài|exercise|種目)\\s+)*(.+?)\\s+(\\d+)\\s*kg\\s*(?:x|×)?\\s*(\\d+)\\s*(?:reps?|lần|回)?$");
    private static final Pattern DELETE = Pattern.compile("(?iu)^(?:(?:hãy|vui lòng|please)\\s+)?(?:xóa|xoá|delete|remove|削除|種目削除)\\s+(?:(?:giúp tôi|cho tôi|bài tập|bài|exercise|種目)\\s+)*(.+?)$");
    private final GymTrackerService gym;
    private final AiCoachService ai;
    private final MessageSource messages;
    private final CurrentUserService currentUser;

    public AssistantConversationService(GymTrackerService gym, AiCoachService ai, MessageSource messages,
                                        CurrentUserService currentUser) {
        this.gym = gym; this.ai = ai; this.messages = messages; this.currentUser = currentUser;
    }

    public AssistantReply reply(String raw, Locale locale) {
        return reply(raw, locale, List.of());
    }

    public AssistantReply reply(String raw, Locale locale, List<ChatMessage> history) {
        String message = raw == null ? "" : raw.trim();
        if (message.isEmpty()) throw new AppException("error.assistant.message.required");
        if (message.length() > 1000) throw new AppException("error.assistant.message.long");
        if (isListRequest(message)) return new AssistantReply(exerciseList(locale), false, null);

        Matcher add = ADD.matcher(message);
        if (add.matches()) {
            int weight = parseInt(add.group(2)); int reps = parseInt(add.group(3));
            String name = add.group(1).trim();
            if (name.length() > 120 || weight > 1000 || reps < 1 || reps > 1000)
                throw new AppException("error.invalidInput");
            PendingAction action = new PendingAction(currentUser.require().getId(), UUID.randomUUID().toString(), "add", null, name,
                    weight, reps, Instant.now().plusSeconds(300));
            return new AssistantReply(msg("assistant.pending.add", locale, name, weight, reps), false, action);
        }

        Matcher delete = DELETE.matcher(message);
        if (delete.matches()) {
            String name = delete.group(1).trim();
            Exercise exercise = gym.exercises().stream().filter(e -> e.getName().equalsIgnoreCase(name)).findFirst()
                    .orElseThrow(() -> new AppException("error.exercise.notFound"));
            PendingAction action = new PendingAction(currentUser.require().getId(), UUID.randomUUID().toString(), "delete", exercise.getId(),
                    exercise.getName(), exercise.getWeight(), exercise.getReps(), Instant.now().plusSeconds(300));
            return new AssistantReply(msg("assistant.pending.delete", locale, exercise.getName()), false, action);
        }
        String context = history.stream().skip(Math.max(0, history.size() - 8L))
                .map(item -> item.role() + ": " + item.text()).reduce((a,b) -> a + "\n" + b).orElse("");
        var answer = ai.chat(message, context, locale);
        return new AssistantReply(answer.text(), answer.aiPowered(), null);
    }

    public String execute(PendingAction action, String token, Locale locale) {
        if (action == null || !currentUser.require().getId().equals(action.ownerId()) || token == null
                || !token.equals(action.token()) || Instant.now().isAfter(action.expiresAt()))
            throw new AppException("error.assistant.action.expired");
        if ("add".equals(action.type())) {
            gym.addExercise(action.name(), action.weight(), action.reps(), LocalDate.now());
            return msg("assistant.action.added", locale, action.name());
        }
        if ("delete".equals(action.type())) {
            Exercise current = gym.exercises().stream().filter(e -> e.getId().equals(action.exerciseId())).findFirst()
                    .orElseThrow(() -> new AppException("error.exercise.notFound"));
            if (!current.getName().equals(action.name())) throw new AppException("error.assistant.action.changed");
            gym.deleteExercise(current.getId());
            return msg("assistant.action.deleted", locale, current.getName());
        }
        throw new AppException("error.invalidInput");
    }

    private boolean isListRequest(String value) {
        String s = value.toLowerCase(Locale.ROOT);
        return s.contains("danh sách bài") || s.contains("liệt kê bài") || s.contains("list exercise")
                || s.contains("show exercise") || s.contains("種目一覧");
    }

    private String exerciseList(Locale locale) {
        List<Exercise> values = gym.exercises();
        if (values.isEmpty()) return msg("assistant.exercise.empty", locale);
        return msg("assistant.exercise.list", locale) + "\n• " + values.stream()
                .map(e -> e.getName() + " — " + e.getWeight() + " kg × " + e.getReps()).reduce((a,b) -> a + "\n• " + b).orElse("");
    }

    private int parseInt(String value) { try { return Integer.parseInt(value); } catch (NumberFormatException e) { throw new AppException("error.invalidInput"); } }
    private String msg(String code, Locale locale, Object... args) { return messages.getMessage(code, args, locale); }

    public record AssistantReply(String text, boolean aiPowered, PendingAction pendingAction) {}
    public record PendingAction(Long ownerId, String token, String type, Long exerciseId, String name, int weight, int reps,
                                Instant expiresAt) implements Serializable {}
    public record ChatMessage(String role, String text) implements Serializable {}
}
