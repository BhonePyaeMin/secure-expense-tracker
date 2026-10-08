package com.example.expenses.web;

import com.example.expenses.dto.ExpenseFilter;
import com.example.expenses.dto.ExpenseForm;
import com.example.expenses.model.Category;
import com.example.expenses.service.ExpenseService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
public class ExpenseController {

    static final int PAGE_SIZE = 10;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"));
    private static final String FORM_VIEW = "expenses/form";

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @ModelAttribute("categories")
    Category[] categories() {
        return Category.values();
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/expenses";
    }

    @GetMapping("/expenses")
    public String list(@RequestParam(required = false) YearMonth month,
                       @RequestParam(required = false) Category category,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        ExpenseFilter filter = new ExpenseFilter(month, category);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), PAGE_SIZE, NEWEST_FIRST);
        model.addAttribute("filter", filter);
        model.addAttribute("expenses", expenseService.search(filter, pageRequest));
        return "expenses/list";
    }

    @GetMapping("/expenses/new")
    public String newForm(Model model) {
        ExpenseForm form = new ExpenseForm();
        form.setDate(LocalDate.now());
        model.addAttribute("expenseForm", form);
        return FORM_VIEW;
    }

    @PostMapping("/expenses")
    public String create(@Valid @ModelAttribute("expenseForm") ExpenseForm form, BindingResult result,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return FORM_VIEW;
        }
        expenseService.create(form);
        redirectAttributes.addFlashAttribute("message", "Expense added.");
        return "redirect:/expenses";
    }

    @GetMapping("/expenses/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("expenseForm", expenseService.formFor(id));
        model.addAttribute("expenseId", id);
        return FORM_VIEW;
    }

    @PostMapping("/expenses/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("expenseForm") ExpenseForm form,
                         BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("expenseId", id);
            return FORM_VIEW;
        }
        expenseService.update(id, form);
        redirectAttributes.addFlashAttribute("message", "Expense updated.");
        return "redirect:/expenses";
    }

    @PostMapping("/expenses/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        expenseService.delete(id);
        redirectAttributes.addFlashAttribute("message", "Expense deleted.");
        return "redirect:/expenses";
    }
}
