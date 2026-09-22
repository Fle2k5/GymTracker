package com.example.gymtracker.config;

import com.example.gymtracker.repository.ExerciseRepository;
import com.example.gymtracker.repository.AppUserRepository;
import com.example.gymtracker.model.AppUser;
import com.example.gymtracker.service.GymTrackerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@Configuration
public class DemoDataConfig {
    @Bean
    @Profile("demo")
    CommandLineRunner demoData(ExerciseRepository exercises, AppUserRepository users,
                               PasswordEncoder encoder, GymTrackerService service) {
        return args -> {
            AppUser demo = users.findByEmailIgnoreCase("demo@gymtracker.local")
                    .orElseGet(() -> users.save(new AppUser("demo@gymtracker.local", "Demo", encoder.encode("Demo123!"))));
            if (exercises.countByOwnerId(demo.getId()) > 0) return;
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    demo.getEmail(), "", java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            SecurityContextHolder.setContext(context);
            try {
                service.addExercise("Bench Press", 60, 8, LocalDate.now().minusDays(2));
                service.addExercise("Squat", 80, 6, LocalDate.now().minusDays(1));
                service.addSchedule("Thứ Hai", "Ngực, vai, tay sau");
                service.addSchedule("Thứ Năm", "Chân và cơ lõi");
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }
}
