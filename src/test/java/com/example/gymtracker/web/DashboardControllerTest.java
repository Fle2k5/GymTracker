package com.example.gymtracker.web;

import com.example.gymtracker.repository.ExerciseRepository;
import com.example.gymtracker.repository.AppUserRepository;
import com.example.gymtracker.model.AppUser;
import com.example.gymtracker.model.Milestone;
import com.example.gymtracker.repository.MilestoneRepository;
import com.example.gymtracker.service.AssistantConversationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:webtest;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "openai.api-key="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ExerciseRepository exercises;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MilestoneRepository milestones;
    private AppUser owner;

    @BeforeEach
    void account() {
        owner = users.findByEmailIgnoreCase("web-test@example.com")
                .orElseGet(() -> users.save(new AppUser("web-test@example.com", "Web Test", passwordEncoder.encode("Test123!"))));
    }

    @Test
    void dashboardRendersAllMainAreas() throws Exception {
        mvc.perform(get("/").with(user(owner.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bài tập hiện tại")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Lịch sử tập luyện")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nhật ký dinh dưỡng")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cột mốc của tôi")));
    }

    @Test
    void unauthenticatedDashboardRedirectsToLogin() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void registrationStoresHashedPassword() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("displayName", "New User")
                        .param("email", "new-user@example.com")
                        .param("password", "Strong123!")
                        .param("passwordConfirm", "Strong123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        var account = users.findByEmailIgnoreCase("new-user@example.com").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(account.getPasswordHash()).isNotEqualTo("Strong123!");
        org.assertj.core.api.Assertions.assertThat(passwordEncoder.matches("Strong123!", account.getPasswordHash())).isTrue();
    }

    @Test
    void invalidLegacyTokenRollsBackRegistration() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("displayName", "Invalid Claim")
                        .param("email", "invalid-claim@example.com")
                        .param("password", "Strong123!")
                        .param("passwordConfirm", "Strong123!")
                        .param("legacyClaimToken", "wrong-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/register"));
        org.assertj.core.api.Assertions.assertThat(users.findByEmailIgnoreCase("invalid-claim@example.com")).isEmpty();
    }

    @Test
    void postWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/exercises").with(user(owner.getEmail()).roles("USER"))
                        .param("name", "No CSRF").param("weight", "10").param("reps", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherUserCannotFetchMilestoneImage() throws Exception {
        Milestone image = milestones.save(new Milestone(owner, "Private image", LocalDate.now(), "",
                "private.png", "image/png", new byte[]{1, 2, 3}));
        AppUser other = users.findByEmailIgnoreCase("image-other@example.com")
                .orElseGet(() -> users.save(new AppUser("image-other@example.com", "Other", passwordEncoder.encode("Test123!"))));

        mvc.perform(get("/milestones/{id}/image", image.getId()).with(user(other.getEmail()).roles("USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/milestones/{id}/image", image.getId()).with(user(owner.getEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void loginClearsPreviousAssistantSessionData() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("assistantMessages", java.util.List.of("private chat"));
        session.setAttribute("assistantPending", "pending");
        session.setAttribute("mealPending", "meal");

        mvc.perform(post("/login").session(session).with(csrf())
                        .param("username", owner.getEmail()).param("password", "Test123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
        org.assertj.core.api.Assertions.assertThat(session.getAttribute("assistantMessages")).isNull();
        org.assertj.core.api.Assertions.assertThat(session.getAttribute("assistantPending")).isNull();
        org.assertj.core.api.Assertions.assertThat(session.getAttribute("mealPending")).isNull();
    }

    @Test
    void languageCanChangeToEnglishAndJapanese() throws Exception {
        mvc.perform(get("/").with(user(owner.getEmail()).roles("USER")).param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Current exercises")));

        mvc.perform(get("/").with(user(owner.getEmail()).roles("USER")).param("lang", "ja"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("今日は何を鍛える？")));
    }

    @Test
    void vietnameseBundleRendersUtf8Characters() throws Exception {
        mvc.perform(get("/").with(user(owner.getEmail()).roles("USER")).param("lang", "vi"))
                .andExpect(status().isOk())
                .andExpect(content().encoding("UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Toàn cảnh hành trình của bạn")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nhật ký dinh dưỡng")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cột mốc của tôi")));
    }

    @Test
    void coachProvidesLocalFallbackWithoutApiKey() throws Exception {
        mvc.perform(post("/coach/recommend").with(user(owner.getEmail()).roles("USER")).with(csrf())
                        .param("goal", "muscle")
                        .param("duration", "45")
                        .param("equipment", "home"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?tab=overview"))
                .andExpect(flash().attributeExists("coachResult"))
                .andExpect(flash().attribute("coachAiPowered", false));
    }

    @Test
    void assistantMutationRequiresSessionConfirmation() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/assistant/chat").with(user(owner.getEmail()).roles("USER")).with(csrf()).session(session)
                        .param("message", "thêm bài AI Safety Test 10kg 12 reps"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?tab=overview"));

        org.assertj.core.api.Assertions.assertThat(exercises.findByOwnerIdAndNameIgnoreCase(owner.getId(), "AI Safety Test")).isEmpty();
        var pending = (AssistantConversationService.PendingAction) session.getAttribute("assistantPending");

        mvc.perform(post("/assistant/action/confirm").with(user(owner.getEmail()).roles("USER")).with(csrf()).session(session).param("token", pending.token()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));
        org.assertj.core.api.Assertions.assertThat(exercises.findByOwnerIdAndNameIgnoreCase(owner.getId(), "AI Safety Test")).isPresent();
        org.assertj.core.api.Assertions.assertThat(session.getAttribute("assistantPending")).isNull();
    }

    @Test
    void invalidAssistantMessageClearsOlderPendingProposal() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/assistant/chat").with(user(owner.getEmail()).roles("USER")).with(csrf()).session(session)
                        .param("message", "thêm bài Stale Proposal 10kg 12 reps"))
                .andExpect(status().is3xxRedirection());
        org.assertj.core.api.Assertions.assertThat(session.getAttribute("assistantPending")).isNotNull();

        mvc.perform(post("/assistant/chat").with(user(owner.getEmail()).roles("USER")).with(csrf()).session(session)
                        .param("message", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
        org.assertj.core.api.Assertions.assertThat(session.getAttribute("assistantPending")).isNull();
    }
}
