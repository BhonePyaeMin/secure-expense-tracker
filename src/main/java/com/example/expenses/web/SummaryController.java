package com.example.expenses.web;

import com.example.expenses.dto.BudgetForm;
import com.example.expenses.model.Category;
import com.example.expenses.service.BudgetService;
import com.example.expenses.service.SummaryService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    public String summary(@RequestParam(required = false) YearMonth month, Model model) {
        addSummary(model, month);
        model.addAttribute("budgetForm", new BudgetForm());
        return VIEW;
    }

    @PostMapping("/budgets")
    public String saveBudget(@Valid @ModelAttribute("budgetForm") BudgetForm form, BindingResult result,
                             @RequestParam(required = false) YearMonth month, Model model,
                             RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            addSummary(model, month);
            return VIEW;
        }
        budgetService.setLimit(form.getCategory(), form.getMonthlyLimit());
        redirectAttributes.addFlashAttribute("message", form.getCategory().getLabel() + " budget saved.");
        return redirectToSummary(month, redirectAttributes);
    }

    @PostMapping("/budgets/{category}/delete")
    public String removeBudget(@PathVariable Category category, @RequestParam(required = false) YearMonth month,
                               RedirectAttributes redirectAttributes) {
        budgetService.remove(category);
        redirectAttributes.addFlashAttribute("message", category.getLabel() + " budget removed.");
        return redirectToSummary(month, redirectAttributes);
    }

    private void addSummary(Model model, YearMonth month) {
        YearMonth selected = month != null ? month : YearMonth.now();
        model.addAttribute("summary", summaryService.summarize(selected));
        model.addAttribute("budgets", budgetService.findAll());
    }

    private static String redirectToSummary(YearMonth month, RedirectAttributes redirectAttributes) {
        if (month != null) {
            redirectAttributes.addAttribute("month", month);
        }
        return "redirect:/summary";
    }
}
