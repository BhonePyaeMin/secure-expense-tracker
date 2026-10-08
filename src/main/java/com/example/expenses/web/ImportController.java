package com.example.expenses.web;

import com.example.expenses.security.AppUserDetails;
import com.example.expenses.service.CsvImportService;
import com.example.expenses.service.ExpenseService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
public class ImportController {

    private static final String VIEW = "expenses/import";

    private final CsvImportService csvImportService;
    private final ExpenseService expenseService;

    public ImportController(CsvImportService csvImportService, ExpenseService expenseService) {
        this.csvImportService = csvImportService;
        this.expenseService = expenseService;
    }

    @GetMapping("/expenses/import")
    public String importForm() {
        return VIEW;
    }

    @PostMapping("/expenses/import")
    public String importCsv(@AuthenticationPrincipal AppUserDetails user, @RequestParam("file") MultipartFile file,
                            Model model, RedirectAttributes redirectAttributes) throws IOException {
        if (file.isEmpty()) {
            model.addAttribute("errors", List.of("Choose a CSV file to import."));
            return VIEW;
        }
        CsvImportService.Result result = csvImportService.read(new String(file.getBytes(), StandardCharsets.UTF_8));
        if (!result.isValid()) {
            model.addAttribute("errors", result.errors());
            return VIEW;
        }
        int count = expenseService.importAll(user.getId(), result.expenses(), file.getOriginalFilename());
        redirectAttributes.addFlashAttribute("message",
                "Imported " + count + (count == 1 ? " expense." : " expenses."));
        return "redirect:/expenses";
    }
}
