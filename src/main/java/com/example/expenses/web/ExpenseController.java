package com.example.expenses.web;

import com.example.expenses.dto.ExpenseFilter;
import com.example.expenses.dto.ExpenseForm;
import com.example.expenses.model.Category;
import com.example.expenses.security.AppUserDetails;
import com.example.expenses.service.CategorySuggester;
import com.example.expenses.service.CsvExportService;
import com.example.expenses.service.ExpenseService;
import com.example.expenses.service.QuickEntryParser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

@Controller
public class ExpenseController {

    static final int PAGE_SIZE = 10;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"));
    private static final String FORM_VIEW = "expenses/form";

    private final ExpenseService expenseService;
    private final CsvExportService csvExportService;
    private final CategorySuggester categorySuggester;
    private final QuickEntryParser quickEntryParser;

    public ExpenseController(ExpenseService expenseService, CsvExportService csvExportService,
                             CategorySuggester categorySuggester, QuickEntryParser quickEntryParser) {
        this.expenseService = expenseService;
        this.csvExportService = csvExportService;
        this.categorySuggester = categorySuggester;
        this.quickEntryParser = quickEntryParser;
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
    public String list(@AuthenticationPrincipal AppUserDetails user,
                       @RequestParam(required = false) YearMonth month,
                       @RequestParam(required = false) Category category,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        ExpenseFilter filter = new ExpenseFilter(month, category, q);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), PAGE_SIZE, NEWEST_FIRST);
        model.addAttribute("filter", filter);
        model.addAttribute("expenses", expenseService.search(user.getId(), filter, pageRequest));
        return "expenses/list";
    }

    @GetMapping("/expenses/export")
    public void export(@AuthenticationPrincipal AppUserDetails user,
                       @RequestParam(required = false) YearMonth month,
                       @RequestParam(required = false) Category category,
                       @RequestParam(required = false) String q,
                       HttpServletResponse response) throws IOException {
        ExpenseFilter filter = new ExpenseFilter(month, category, q);
        String filename = "expenses" + (month != null ? "-" + month : "") + ".csv";
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString());
        csvExportService.write(expenseService.findAll(user.getId(), filter), response.getWriter());
    }

    /** Used by app.js while typing a title: {"category":"TRANSPORT","label":"Transport"}, or 204 if no guess. */
    @GetMapping(value = "/expenses/suggest-category", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, String>> suggestCategory(@RequestParam(defaultValue = "") String title) {
        String text = title.length() > 200 ? title.substring(0, 200) : title;
        return categorySuggester.suggest(text)
                .map(c -> ResponseEntity.ok(Map.of("category", c.name(), "label", c.getLabel())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** The add form. With ?quick=lunch+85+yesterday it comes pre-filled from that text; nothing is saved yet. */
    @GetMapping("/expenses/new")
    public String newForm(@RequestParam(required = false) String quick, Model model) {
        ExpenseForm form;
        if (StringUtils.hasText(quick)) {
            String text = quick.length() > 200 ? quick.substring(0, 200) : quick;
            form = quickEntryParser.parse(text, LocalDate.now());
            model.addAttribute("quickText", text);
        } else {
            form = new ExpenseForm();
            form.setDate(LocalDate.now());
        }
        model.addAttribute("expenseForm", form);
        return FORM_VIEW;
    }

    @PostMapping("/expenses")
    public String create(@AuthenticationPrincipal AppUserDetails user,
                         @Valid @ModelAttribute("expenseForm") ExpenseForm form, BindingResult result,
                         RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return FORM_VIEW;
        }
        expenseService.create(user.getId(), form);
        redirectAttributes.addFlashAttribute("message", "Expense added.");
        return "redirect:/expenses";
    }

    @GetMapping("/expenses/{id}/edit")
    public String editForm(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id, Model model) {
        model.addAttribute("expenseForm", expenseService.formFor(user.getId(), id));
        model.addAttribute("expenseId", id);
        return FORM_VIEW;
    }

    @PostMapping("/expenses/{id}")
    public String update(@AuthenticationPrincipal AppUserDetails user,
                         @PathVariable Long id, @Valid @ModelAttribute("expenseForm") ExpenseForm form,
                         BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("expenseId", id);
            return FORM_VIEW;
        }
        expenseService.update(user.getId(), id, form);
        redirectAttributes.addFlashAttribute("message", "Expense updated.");
        return "redirect:/expenses";
    }

    @PostMapping("/expenses/{id}/delete")
    public String delete(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        expenseService.delete(user.getId(), id);
        redirectAttributes.addFlashAttribute("message", "Expense deleted.");
        return "redirect:/expenses";
    }
}
