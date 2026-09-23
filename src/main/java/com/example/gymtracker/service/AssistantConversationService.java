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
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AssistantConversationService {
    private static final Pattern ADD_PREFIX = Pattern.compile(
            "(?iu)^\\s*(?:(?:hãy|vui\\s+lòng|please|tôi\\s+muốn|mình\\s+muốn|can\\s+you|could\\s+you)\\s+)*" +
                    "(?:(?:thêm|add)(?:\\s+|\\s*[,;:\\-]\\s*)|(?:種目追加|追加)(?:\\s+|\\s*[,;:\\-]\\s*))(.+?)\\s*$");
    private static final Pattern ADD_FILLER = Pattern.compile(
            "(?iu)^(?:(?:giúp|cho|hộ)\\s+(?:tôi|mình)(?:\\s+(?:bài\\s+tập|bài|exercise|種目))?|" +
                    "bài\\s+tập|bài|exercise|種目|を)(?:\\s+|\\s*[,;:\\-]\\s*)");
    private static final Pattern ADD_DETAILS = Pattern.compile(
            "(?iu)^(.+?)[\\s,、;:\\-]+(\\d{1,6})\\s*(?:kg|キロ)\\s*[,、;:/\\-]?\\s*(?:[x×]\\s*)?" +
                    "(\\d{1,6})\\s*(?:reps?|lần|回)?\\s*[,;]*\\s*" +
                    "(?:(?:nhé|nha|ạ|please|giúp\\s+(?:tôi|mình)|cho\\s+(?:tôi|mình)|してください|お願いします)\\s*)*[.!。！]*$");
    private static final Pattern DELETE = Pattern.compile("(?iu)^(?:(?:hãy|vui lòng|please)\\s+)?(?:xóa|xoá|delete|remove|削除|種目削除)\\s+(?:(?:giúp tôi|cho tôi|bài tập|bài|exercise|種目)\\s+)*(.+?)$");
    private static final Pattern LIST = Pattern.compile(
            "(?iu)^\\s*(?:(?:hãy|vui\\s+lòng|please|cho\\s+(?:tôi|mình)\\s+xem)\\s+)*" +
                    "(?:danh\\s+sách\\s+(?:các\\s+)?bài(?:\\s+tập)?|liệt\\s+kê\\s+(?:các\\s+)?bài(?:\\s+tập)?|" +
                    "list\\s+exercises?|show\\s+exercises?|種目一覧)" +
                    "(?:\\s+(?:của\\s+(?:tôi|mình)|hiện\\s+tại|please|を表示(?:してください)?))?\\s*[.!。！]*$");
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
        String message = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFKC).trim();
        if (message.isEmpty()) throw new AppException("error.assistant.message.required");
        if (message.length() > 1000) throw new AppException("error.assistant.message.long");
        if (isListRequest(message)) return new AssistantReply(exerciseList(locale), false, null);

        AddCommand add = parseAdd(message);
        if (add != null) {
            int weight = add.weight(); int reps = add.reps();
            String name = add.name();
            if (name.length() > 120 || weight > 1000 || reps < 1 || reps > 1000)
                throw new AppException("error.invalidInput");
            Exercise existing = gym.exercises().stream().filter(e -> e.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
            String actionType = existing == null ? "add" : "update";
            Long exerciseId = existing == null ? null : existing.getId();
            String savedName = existing == null ? name : existing.getName();
            PendingAction action = new PendingAction(currentUser.require().getId(), UUID.randomUUID().toString(), actionType, exerciseId, savedName,
                    weight, reps, Instant.now().plusSeconds(300));
            String messageCode = existing == null ? "assistant.pending.add" : "assistant.pending.update";
            return new AssistantReply(msg(messageCode, locale, savedName, weight, reps), false, action);
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
        if ("update".equals(action.type())) {
            Exercise current = gym.exercises().stream().filter(e -> e.getId().equals(action.exerciseId())).findFirst()
                    .orElseThrow(() -> new AppException("error.exercise.notFound"));
            if (!current.getName().equals(action.name())) throw new AppException("error.assistant.action.changed");
            gym.updateExercise(current.getId(), current.getName(), action.weight(), action.reps(), LocalDate.now());
            return msg("assistant.action.updated", locale, current.getName(), action.weight(), action.reps());
        }
        throw new AppException("error.invalidInput");
    }

    private AddCommand parseAdd(String message) {
        // A question is advice, not permission to mutate data. Only imperative statements reach confirmation.
        if (message.indexOf('?') >= 0 || message.indexOf('？') >= 0) return null;
        Matcher prefix = ADD_PREFIX.matcher(message);
        if (!prefix.matches()) return null;
        String payload = prefix.group(1).trim();
        Matcher filler = ADD_FILLER.matcher(payload);
        if (filler.find()) {
            payload = payload.substring(filler.end()).trim();
        }
        Matcher details = ADD_DETAILS.matcher(payload);
        if (!details.matches()) return null;
        String name = details.group(1).trim().replaceAll("\\s+", " ");
        if (name.isBlank()) return null;
        return new AddCommand(name, parseInt(details.group(2)), parseInt(details.group(3)));
    }

    private boolean isListRequest(String value) {
        return LIST.matcher(value).matches();
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
    private record AddCommand(String name, int weight, int reps) {}
    public record PendingAction(Long ownerId, String token, String type, Long exerciseId, String name, int weight, int reps,
                                Instant expiresAt) implements Serializable {}
    public record ChatMessage(String role, String text) implements Serializable {}
}
