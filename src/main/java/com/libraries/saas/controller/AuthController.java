package com.libraries.saas.controller;

import com.libraries.auth.dto.LoginRequest;
import com.libraries.saas.services.AuthService;
import io.sentry.Sentry;
import io.sentry.spring.jakarta.tracing.SentrySpan;
import io.sentry.spring.tracing.SentryTransaction;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.setAttribute("token", null);
        session.invalidate();
        return "index";
    }

    @GetMapping("/login")
    public String login(HttpSession session) {
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@RequestParam("username") String email, @RequestParam("password") String password, HttpSession session, Model model) {
        LoginRequest loginRequest = new LoginRequest(email, password);
        try {
            authService.login(loginRequest, session);
            if (session.getAttribute("token") != null) {
                return "redirect:/";
            } else {
                model.addAttribute("error", "Invalid username and/or password");
            }
        } catch (Exception e) {
            session.invalidate();
            model.addAttribute("error", e.getMessage());
        }
        return "auth/login";
    }
}
