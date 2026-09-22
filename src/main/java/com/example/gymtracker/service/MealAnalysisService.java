package com.example.gymtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MealAnalysisService {
    private final ObjectMapper mapper;
    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final CurrentUserService currentUser;

    public MealAnalysisService(ObjectMapper mapper, @Value("${openai.api-key:}") String apiKey,
                               @Value("${openai.model:gpt-5.6-luna}") String model,
                               CurrentUserService currentUser) {
        this.mapper = mapper; this.apiKey = apiKey == null ? "" : apiKey.trim(); this.model = model;
        this.currentUser = currentUser;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8_000); factory.setReadTimeout(25_000);
        this.client = RestClient.builder().baseUrl("https://api.openai.com/v1").requestFactory(factory).build();
    }

    public PendingMeal analyze(String mealType, String description, Locale locale) {
        String text = description == null ? "" : description.trim();
        if (text.length() < 3) throw new AppException("error.meal.description.required");
        if (text.length() > 1200) throw new AppException("error.meal.description.long");
        MealEstimate estimate = null;
        if (!apiKey.isBlank()) {
            try { estimate = aiEstimate(text, locale); } catch (RuntimeException ignored) { }
        }
        if (estimate == null) estimate = localEstimate(text, locale);
        validate(estimate);
        return new PendingMeal(currentUser.require().getId(), UUID.randomUUID().toString(), normalizeMeal(mealType), text, estimate,
                Instant.now().plusSeconds(600));
    }

    private MealEstimate aiEstimate(String description, Locale locale) {
        String prompt = """
                Estimate nutrition for this meal from ingredients, quantities and cooking method. Language: %s.
                Return ONLY one JSON object with integer fields calories, protein, carbs, fat and string explanation.
                State assumptions in explanation. Never use markdown. Meal: %s
                """.formatted(locale.toLanguageTag(), description);
        Map<String,Object> body = Map.of("model", model, "store", false, "max_output_tokens", 350,
                "instructions", "You estimate meal nutrition conservatively. Output valid JSON only.", "input", prompt);
        String raw = client.post().uri("/responses").header("Authorization", "Bearer " + apiKey)
                .body(body).retrieve().body(String.class);
        try {
            JsonNode root = mapper.readTree(raw); String output = root.path("output_text").asText();
            if (output.isBlank()) for (JsonNode o : root.path("output")) for (JsonNode c : o.path("content"))
                if (c.hasNonNull("text")) { output = c.get("text").asText(); break; }
            JsonNode data = mapper.readTree(output.replace("```json", "").replace("```", "").trim());
            return new MealEstimate(data.path("calories").asInt(-1), data.path("protein").asInt(-1),
                    data.path("carbs").asInt(-1), data.path("fat").asInt(-1),
                    data.path("explanation").asText("AI estimate"), true);
        } catch (Exception ex) { return null; }
    }

    private MealEstimate localEstimate(String description, Locale locale) {
        String lower = description.toLowerCase(Locale.ROOT);
        double[] total = new double[4]; List<String> found = new ArrayList<>();
        List<Food> foods = List.of(
                new Food("chicken", List.of("ức gà","thịt gà","chicken breast","鶏むね","鶏胸"),165,31,0,3.6),
                new Food("rice", List.of("cơm","rice","ご飯"),130,2.7,28,0.3),
                new Food("cabbage", List.of("bắp cải","cabbage","キャベツ"),25,1.3,6,0.1),
                new Food("beef", List.of("thịt bò","beef","牛肉"),250,26,0,15),
                new Food("pork", List.of("thịt heo","thịt lợn","pork","豚肉"),242,27,0,14),
                new Food("salmon", List.of("cá hồi","salmon","サーモン","鮭"),208,20,0,13),
                new Food("tofu", List.of("đậu phụ","đậu hũ","tofu","豆腐"),76,8,1.9,4.8),
                new Food("oil", List.of("dầu ăn","olive oil","cooking oil","油"),884,0,0,100)
        );
        for (Food food : foods) for (String alias : food.aliases()) {
            Double grams = quantity(lower, alias);
            if (grams != null) { add(total, food, grams / 100.0); found.add(alias + " " + Math.round(grams) + "g"); break; }
        }
        Matcher eggs = Pattern.compile("(?iu)(\\d+)\\s*(?:quả\\s*)?(?:trứng|eggs?|卵|個)").matcher(lower);
        if (eggs.find()) { int count = Integer.parseInt(eggs.group(1)); total[0]+=72*count; total[1]+=6.3*count; total[2]+=.4*count; total[3]+=4.8*count;
            String eggLabel = locale.getLanguage().equals("vi") ? " quả trứng" : locale.getLanguage().equals("ja") ? "個の卵" : " eggs"; found.add(count+eggLabel); }
        if (found.isEmpty()) throw new AppException("error.meal.analysis.unavailable");
        String prefix = locale.getLanguage().equals("vi") ? "Ước tính nội bộ: "
                : locale.getLanguage().equals("ja") ? "ローカル推定：" : "Local estimate: ";
        String explanation = prefix + String.join(", ", found) + ".";
        return new MealEstimate(round(total[0]),round(total[1]),round(total[2]),round(total[3]),explanation,false);
    }

    private Double quantity(String text, String alias) {
        String q = Pattern.quote(alias);
        for (Pattern p : List.of(Pattern.compile("(?iu)(\\d+(?:[.,]\\d+)?)\\s*g(?:ram)?\\s*(?:of\\s+)?"+q),
                Pattern.compile("(?iu)"+q+"\\s*(\\d+(?:[.,]\\d+)?)\\s*g(?:ram)?"))) {
            Matcher m=p.matcher(text); if(m.find()) return Double.parseDouble(m.group(1).replace(',','.'));
        }
        return null;
    }
    private void add(double[] t, Food f, double factor) { t[0]+=f.kcal()*factor;t[1]+=f.p()*factor;t[2]+=f.c()*factor;t[3]+=f.f()*factor; }
    private int round(double n) { return (int)Math.round(n); }
    private void validate(MealEstimate e) {
        if(e.calories()<0||e.calories()>10000||e.protein()<0||e.protein()>1000||e.carbs()<0||e.carbs()>1500||e.fat()<0||e.fat()>1000)
            throw new AppException("error.meal.analysis.invalid");
        int macroCalories = e.protein()*4 + e.carbs()*4 + e.fat()*9;
        if (e.calories() > 100 && macroCalories == 0) throw new AppException("error.meal.analysis.invalid");
        if (macroCalories > 0 && Math.abs(e.calories()-macroCalories) > Math.max(250, e.calories() * .45))
            throw new AppException("error.meal.analysis.invalid");
    }
    private String normalizeMeal(String m) {
        if (m == null || !Set.of("breakfast","lunch","dinner","snack").contains(m))
            throw new AppException("error.meal.required");
        return m;
    }

    private record Food(String name,List<String> aliases,double kcal,double p,double c,double f) {}
    public record MealEstimate(int calories,int protein,int carbs,int fat,String explanation,boolean aiPowered) implements Serializable {}
    public record PendingMeal(Long ownerId,String token,String mealType,String description,MealEstimate estimate,Instant expiresAt) implements Serializable {}
}
