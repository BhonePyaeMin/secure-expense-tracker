package com.example.expenses.web;

import com.example.expenses.model.Category;
import com.example.expenses.model.Expense;
import com.example.expenses.service.ExpenseService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
public class ExpenseController {

    private static final String FORM_VIEW = "expenses/form";

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    // The id always comes from the URL, never from the submitted form
    @InitBinder("expense")
    void disallowId(WebDataBinder binder) {
        binder.setDisallowedFields("id");
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
    public String list(Model model) {
        model.addAttribute("expenses", expenseService.findAll());
        return "expenses/list";
    }

    @GetMapping("/expenses/new")
    public String newForm(Model model) {
        Expense expense = new Expense();
        expense.setDate(LocalDate.now());
        model.addAttribute("expense", expense);
        return FORM_VIEW;
    }

    @PostMapping("/expenses")
    public String create(@ModelAttribute("expense") Expense expense, BindingResult result,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return FORM_VIEW;
        }
        expenseService.create(expense);
        redirectAttributes.addFlashAttribute("message", "Expense added.");
        return "redirect:/expenses";
    }

    @GetMapping("/expenses/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("expense", expenseService.findById(id));
        model.addAttribute("expenseId", id);
        return FORM_VIEW;
    }

    @PostMapping("/expenses/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("expense") Expense expense, BindingResult result,
                         Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("expenseId", id);
            return FORM_VIEW;
        }
        expenseService.update(id, expense);
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
