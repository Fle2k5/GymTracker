package com.example.gymtracker.service;

import com.example.gymtracker.model.AppUser;
import com.example.gymtracker.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AuthService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final JdbcTemplate jdbc;
    private final String legacyClaimToken;
    private static final String LEGACY_EMAIL = "legacy-disabled@gymtracker.invalid";

    public AuthService(AppUserRepository users, PasswordEncoder passwords, JdbcTemplate jdbc,
                       @Value("${app.legacy-claim-token:}") String legacyClaimToken) {
        this.users = users; this.passwords = passwords; this.jdbc = jdbc;
        this.legacyClaimToken = legacyClaimToken == null ? "" : legacyClaimToken;
    }

    @Transactional
    public AppUser register(String rawEmail, String rawName, String password, String confirmation) {
        return register(rawEmail, rawName, password, confirmation, "");
    }

    @Transactional
    public AppUser register(String rawEmail, String rawName, String password, String confirmation, String claimToken) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
        String name = rawName == null ? "" : rawName.trim();
        if (email.length() > 190 || !EMAIL.matcher(email).matches()) throw new AppException("error.auth.email");
        if (name.length() < 2 || name.length() > 80) throw new AppException("error.auth.name");
        int passwordBytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (passwordBytes < 8 || passwordBytes > 72) throw new AppException("error.auth.password");
        if (password == null || !password.equals(confirmation)) throw new AppException("error.auth.passwordConfirm");
        if (users.existsByEmailIgnoreCase(email)) throw new AppException("error.auth.emailExists");
        AppUser created = users.saveAndFlush(new AppUser(email, name, passwords.encode(password)));
        if (claimToken != null && !claimToken.isBlank()) {
            if (legacyClaimToken.isBlank() || !MessageDigest.isEqual(legacyClaimToken.getBytes(StandardCharsets.UTF_8),
                    claimToken.getBytes(StandardCharsets.UTF_8))) throw new AppException("error.auth.legacyToken");
            claimLegacyData(created);
        }
        return created;
    }

    private void claimLegacyData(AppUser created) {
        users.findLockedByEmail(LEGACY_EMAIL).ifPresent(legacy -> {
            jdbc.update("UPDATE fitness_profiles SET id=?, owner_id=? WHERE owner_id=?",
                    created.getId(), created.getId(), legacy.getId());
            for (String table : new String[]{"schedule_exercises", "workout_history", "nutrition_entries",
                    "milestones", "exercises", "schedules"}) {
                jdbc.update("UPDATE " + table + " SET owner_id=? WHERE owner_id=?", created.getId(), legacy.getId());
            }
            users.delete(legacy);
        });
    }
}
