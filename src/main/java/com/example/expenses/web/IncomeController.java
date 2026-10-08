package com.example.expenses.web;

import com.example.expenses.dto.IncomeForm;
import com.example.expenses.security.AppUserDetails;
import com.example.expenses.service.IncomeService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

@Controller
public class IncomeController {

    private static final String VIEW = "income";

    private final IncomeService incomeService;
    private final Clock clock;

    public IncomeController(IncomeService incomeService, Clock clock) {
        this.incomeService = incomeService;
        this.clock = clock;
    }

    @GetMapping("/income")
    public String list(@AuthenticationPrincipal AppUserDetails user,
                       @RequestParam(required = false) YearMonth month, Model model) {
        IncomeForm form = new IncomeForm();
        form.setDate(LocalDate.now(clock));
        model.addAttribute("incomeForm", form);
        addMonth(model, user.getId(), month != null ? month : YearMonth.now(clock));
        return VIEW;
    }

    @PostMapping("/income")
    public String add(@AuthenticationPrincipal AppUserDetails user,
                      @Valid @ModelAttribute("incomeForm") IncomeForm form, BindingResult result,
                      Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            addMonth(model, user.getId(), form.getDate() != null ? YearMonth.from(form.getDate()) : YearMonth.now(clock));
            return VIEW;
        }
        incomeService.add(user.getId(), form);
        redirectAttributes.addFlashAttribute("message", "Income added.");
        redirectAttributes.addAttribute("month", YearMonth.from(form.getDate()));
        return "redirect:/income";
    }

    @PostMapping("/income/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                         @RequestParam(required = false) YearMonth month, RedirectAttributes redirectAttributes) {
        incomeService.delete(user.getId(), id);
        redirectAttributes.addFlashAttribute("message", "Income deleted.");
        if (month != null) {
            redirectAttributes.addAttribute("month", month);
        }
        return "redirect:/income";
    }

    private void addMonth(Model model, Long userId, YearMonth month) {
        model.addAttribute("month", month);
        model.addAttribute("incomes", incomeService.findForMonth(userId, month));
        model.addAttribute("total", incomeService.totalForMonth(userId, month));
        model.addAttribute("sources", incomeService.previousSources(userId));
    }
}
