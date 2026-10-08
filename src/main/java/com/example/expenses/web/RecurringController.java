package com.example.expenses.web;

import com.example.expenses.dto.RecurringForm;
import com.example.expenses.model.Category;
import com.example.expenses.security.AppUserDetails;
import com.example.expenses.service.RecurringExpenseService;
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

import java.time.LocalDate;

@Controller
public class RecurringController {

    private static final String VIEW = "recurring";
    private static final String REDIRECT = "redirect:/recurring";

    private final RecurringExpenseService recurringExpenseService;

    public RecurringController(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    @ModelAttribute("categories")
    Category[] categories() {
        return Category.values();
    }

    @GetMapping("/recurring")
    public String list(@AuthenticationPrincipal AppUserDetails user, Model model) {
        RecurringForm form = new RecurringForm();
        form.setFirstDate(LocalDate.now());
        model.addAttribute("recurringForm", form);
        model.addAttribute("recurring", recurringExpenseService.findAll(user.getId()));
        return VIEW;
    }

    @PostMapping("/recurring")
    public String create(@AuthenticationPrincipal AppUserDetails user,
                         @Valid @ModelAttribute("recurringForm") RecurringForm form, BindingResult result,
                         Model model, RedirectAttributes redirectAttributes) {
        LocalDate today = LocalDate.now();
        if (form.getFirstDate() != null && form.getFirstDate().isBefore(today.minusYears(1))) {
            result.rejectValue("firstDate", "tooOld", "First date can be at most one year ago");
        }
        if (form.getFirstDate() != null && form.getFirstDate().isAfter(today.plusYears(1))) {
            result.rejectValue("firstDate", "tooFar", "First date can be at most one year ahead");
        }
        if (result.hasErrors()) {
            model.addAttribute("recurring", recurringExpenseService.findAll(user.getId()));
            return VIEW;
        }
        int added = recurringExpenseService.create(user.getId(), form, today);
        redirectAttributes.addFlashAttribute("message", "Recurring expense saved." + addedMessage(added));
        return REDIRECT;
    }

    @PostMapping("/recurring/{id}/pause")
    public String pause(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                        RedirectAttributes redirectAttributes) {
        recurringExpenseService.pause(user.getId(), id);
        redirectAttributes.addFlashAttribute("message", "Paused.");
        return REDIRECT;
    }

    @PostMapping("/recurring/{id}/resume")
    public String resume(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        int added = recurringExpenseService.resume(user.getId(), id, LocalDate.now());
        redirectAttributes.addFlashAttribute("message", "Resumed." + addedMessage(added));
        return REDIRECT;
    }

    @PostMapping("/recurring/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        recurringExpenseService.delete(user.getId(), id);
        redirectAttributes.addFlashAttribute("message", "Recurring expense deleted. Expenses it already added are kept.");
        return REDIRECT;
    }

    private static String addedMessage(int added) {
        if (added == 0) {
            return "";
        }
        return " Added " + added + (added == 1 ? " expense" : " expenses") + " that " + (added == 1 ? "was" : "were")
                + " already due.";
    }
}
