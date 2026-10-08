package com.example.expenses.web;

import com.example.expenses.model.Expense;
import com.example.expenses.security.AppUserDetails;
import com.example.expenses.service.ExpenseService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Deleted expenses wait here until they're restored or deleted forever. */
@Controller
public class TrashController {

    private final ExpenseService expenseService;

    public TrashController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping("/trash")
    public String trash(@AuthenticationPrincipal AppUserDetails user, @RequestParam(defaultValue = "0") int page,
                        Model model) {
        model.addAttribute("expenses", expenseService.trash(user.getId(), page));
        return "trash";
    }

    /** {@code from=list} is the Undo link on the expense list, which should return there. */
    @PostMapping("/trash/{id}/restore")
    public String restore(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                          @RequestParam(required = false) String from, RedirectAttributes redirectAttributes) {
        Expense expense = expenseService.restore(user.getId(), id);
        redirectAttributes.addFlashAttribute("message", "Restored \"" + expense.getTitle() + "\".");
        return "list".equals(from) ? "redirect:/expenses" : "redirect:/trash";
    }

    @PostMapping("/trash/{id}/delete")
    public String deleteForever(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                                RedirectAttributes redirectAttributes) {
        expenseService.deleteForever(user.getId(), id);
        redirectAttributes.addFlashAttribute("message", "Deleted forever.");
        return "redirect:/trash";
    }
}
