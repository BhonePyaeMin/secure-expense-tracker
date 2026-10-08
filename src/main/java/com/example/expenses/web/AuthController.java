package com.example.expenses.web;

import com.example.expenses.dto.RegistrationForm;
import com.example.expenses.service.UserService;
import com.example.expenses.service.UsernameTakenException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.Objects;

/** The login and register pages. Spring Security itself handles POST /login and POST /logout. */
@Controller
public class AuthController {

    private final UserService userService;
    private final Duration lockoutDuration;

    public AuthController(UserService userService,
                          @Value("${app.security.lockout-duration:15m}") Duration lockoutDuration) {
        this.userService = userService;
        this.lockoutDuration = lockoutDuration;
    }

    @GetMapping("/login")
    public String login(Authentication authentication, Model model) {
        if (isSignedIn(authentication)) {
            return "redirect:/expenses";
        }
        model.addAttribute("lockoutMinutes", lockoutDuration.toMinutes());
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Authentication authentication, Model model) {
        if (isSignedIn(authentication)) {
            return "redirect:/expenses";
        }
        model.addAttribute("registrationForm", new RegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form, BindingResult result,
                           RedirectAttributes redirectAttributes) {
        if (!result.hasFieldErrors("password")
                && !Objects.equals(form.getPassword(), form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "mismatch", "Passwords don't match");
        }
        if (result.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.register(form.getUsername(), form.getPassword());
        } catch (UsernameTakenException e) {
            result.rejectValue("username", "taken", "That username is already taken");
            return "auth/register";
        }
        redirectAttributes.addFlashAttribute("message", "Account created. Sign in to continue.");
        return "redirect:/login";
    }

    private static boolean isSignedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
