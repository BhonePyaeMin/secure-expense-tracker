package com.example.expenses.web;

import com.example.expenses.dto.BudgetForm;
import com.example.expenses.dto.MonthlySummary;
import com.example.expenses.model.Category;
import com.example.expenses.security.AppUserDetails;
import com.example.expenses.service.BudgetService;
import com.example.expenses.service.SummaryService;
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

import java.time.LocalDate;
import java.time.YearMonth;

@Controller
public class SummaryController {

    private static final String VIEW = "summary";

    private final SummaryService summaryService;
    private final BudgetService budgetService;

    public SummaryController(SummaryService summaryService, BudgetService budgetService) {
        this.summaryService = summaryService;
        this.budgetService = budgetService;
    }

    @ModelAttribute("categories")
    Category[] categories() {
        return Category.values();
    }

    @GetMapping("/summary")
    public String summary(@AuthenticationPrincipal AppUserDetails user,
                          @RequestParam(required = false) YearMonth month, Model model) {
        addSummary(model, user.getId(), month);
        model.addAttribute("budgetForm", new BudgetForm());
        return VIEW;
    }

    @PostMapping("/budgets")
    public String saveBudget(@AuthenticationPrincipal AppUserDetails user,
                             @Valid @ModelAttribute("budgetForm") BudgetForm form, BindingResult result,
                             @RequestParam(required = false) YearMonth month, Model model,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            addSummary(model, user.getId(), month);
            return VIEW;
        }
        budgetService.setLimit(user.getId(), form.getCategory(), form.getMonthlyLimit());
        redirectAttributes.addFlashAttribute("message", form.getCategory().getLabel() + " budget saved.");
        return redirectToSummary(month, redirectAttributes);
    }

    @PostMapping("/budgets/{category}/delete")
    public String removeBudget(@AuthenticationPrincipal AppUserDetails user, @PathVariable Category category,
                               @RequestParam(required = false) YearMonth month,
                               RedirectAttributes redirectAttributes) {
        budgetService.remove(user.getId(), category);
        redirectAttributes.addFlashAttribute("message", category.getLabel() + " budget removed.");
        return redirectToSummary(month, redirectAttributes);
    }

    private void addSummary(Model model, Long userId, YearMonth month) {
        YearMonth selected = month != null ? month : YearMonth.now();
        MonthlySummary summary = summaryService.summarize(userId, selected);
        model.addAttribute("summary", summary);
        model.addAttribute("balance", summaryService.balance(userId, summary));
        model.addAttribute("allowance", summaryService.dailyAllowance(summary, LocalDate.now()).orElse(null));
        model.addAttribute("daily", summaryService.dailySpending(userId, selected));
        model.addAttribute("payments", summaryService.paymentTotals(userId, selected));
        model.addAttribute("budgets", budgetService.findAll(userId));
    }

    private static String redirectToSummary(YearMonth month, RedirectAttributes redirectAttributes) {
        if (month != null) {
            redirectAttributes.addAttribute("month", month);
        }
        return "redirect:/summary";
    }
}
