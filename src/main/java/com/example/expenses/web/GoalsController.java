package com.example.expenses.web;

import com.example.expenses.dto.GoalForm;
import com.example.expenses.dto.GoalMoneyForm;
import com.example.expenses.model.SavingsGoal;
import com.example.expenses.security.AppUserDetails;
import com.example.expenses.service.SavingsGoalService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;

@Controller
public class GoalsController {

    private static final String VIEW = "goals";
    private static final String REDIRECT = "redirect:/goals";

    private final SavingsGoalService goalService;
    private final MoneyFormatter money;
    private final Clock clock;

    public GoalsController(SavingsGoalService goalService, MoneyFormatter money, Clock clock) {
        this.goalService = goalService;
        this.money = money;
        this.clock = clock;
    }

    @GetMapping("/goals")
    public String goals(@AuthenticationPrincipal AppUserDetails user, Model model) {
        model.addAttribute("goalForm", new GoalForm());
        addGoals(model, user.getId());
        return VIEW;
    }

    @PostMapping("/goals")
    public String create(@AuthenticationPrincipal AppUserDetails user,
                         @Valid @ModelAttribute("goalForm") GoalForm form, BindingResult result,
                         Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            addGoals(model, user.getId());
            return VIEW;
        }
        SavingsGoal goal = goalService.create(user.getId(), form);
        redirectAttributes.addFlashAttribute("message", "Goal \"" + goal.getName() + "\" added.");
        return REDIRECT;
    }

    @PostMapping("/goals/{id}/deposit")
    public String deposit(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                          @Valid @ModelAttribute("moneyForm") GoalMoneyForm form, BindingResult result,
                          RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return withError(result, redirectAttributes);
        }
        SavingsGoal goal = goalService.deposit(user.getId(), id, form.getAmount());
        redirectAttributes.addFlashAttribute("message",
                "Added " + money.format(form.getAmount()) + " to \"" + goal.getName() + "\".");
        return REDIRECT;
    }

    @PostMapping("/goals/{id}/withdraw")
    public String withdraw(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                           @Valid @ModelAttribute("moneyForm") GoalMoneyForm form, BindingResult result,
                           RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return withError(result, redirectAttributes);
        }
        try {
            SavingsGoal goal = goalService.withdraw(user.getId(), id, form.getAmount());
            redirectAttributes.addFlashAttribute("message",
                    "Took " + money.format(form.getAmount()) + " out of \"" + goal.getName() + "\".");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "You can't take out more than is saved.");
        }
        return REDIRECT;
    }

    @PostMapping("/goals/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        goalService.delete(user.getId(), id);
        redirectAttributes.addFlashAttribute("message", "Goal deleted.");
        return REDIRECT;
    }

    private void addGoals(Model model, Long userId) {
        model.addAttribute("goals", goalService.findAll(userId));
        model.addAttribute("today", LocalDate.now(clock));
    }

    private static String withError(BindingResult result, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", result.getAllErrors().get(0).getDefaultMessage());
        return REDIRECT;
    }
}
