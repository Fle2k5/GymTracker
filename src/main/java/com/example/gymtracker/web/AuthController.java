package com.example.gymtracker.web;

import com.example.gymtracker.service.AppException;
import com.example.gymtracker.service.AuthService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.dao.DataIntegrityViolationException;

@Controller
public class AuthController {
    private final AuthService auth;
    private final MessageSource messages;
    public AuthController(AuthService auth, MessageSource messages) { this.auth = auth; this.messages = messages; }

    @GetMapping("/login")
    public String login(Authentication authentication) { return loggedIn(authentication) ? "redirect:/" : "login"; }

    @GetMapping("/register")
    public String register(Authentication authentication) { return loggedIn(authentication) ? "redirect:/" : "register"; }

    @PostMapping("/register")
    public String register(@RequestParam String email, @RequestParam String displayName,
                           @RequestParam String password, @RequestParam String passwordConfirm,
                           @RequestParam(required = false) String legacyClaimToken,
                           RedirectAttributes flash) {
        try {
            auth.register(email, displayName, password, passwordConfirm, legacyClaimToken);
            flash.addFlashAttribute("success", msg("auth.register.success"));
            return "redirect:/login";
        } catch (AppException ex) {
            flash.addFlashAttribute("error", messages.getMessage(ex.getCode(), ex.getArguments(), LocaleContextHolder.getLocale()));
            flash.addFlashAttribute("email", email);
            flash.addFlashAttribute("displayName", displayName);
            return "redirect:/register";
        } catch (DataIntegrityViolationException ex) {
            flash.addFlashAttribute("error", msg("error.auth.emailExists"));
            flash.addFlashAttribute("email", email);
            flash.addFlashAttribute("displayName", displayName);
            return "redirect:/register";
        }
    }

    private boolean loggedIn(Authentication value) {
        return value != null && value.isAuthenticated() && !(value instanceof AnonymousAuthenticationToken);
    }
    private String msg(String code) { return messages.getMessage(code, null, LocaleContextHolder.getLocale()); }
}
