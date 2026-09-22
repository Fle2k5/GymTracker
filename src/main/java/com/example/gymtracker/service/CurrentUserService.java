package com.example.gymtracker.service;

import com.example.gymtracker.model.AppUser;
import com.example.gymtracker.repository.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final AppUserRepository users;

    public CurrentUserService(AppUserRepository users) { this.users = users; }

    public AppUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal()))
            throw new AppException("error.auth.required");
        return users.findByEmailIgnoreCase(authentication.getName())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new AppException("error.auth.required"));
    }
}
