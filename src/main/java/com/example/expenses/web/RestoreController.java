package com.example.expenses.web;

import com.example.expenses.security.AppUserDetails;
import com.example.expenses.service.RestoreService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Restore in three pages: upload, preview (nothing saved yet), and a report after confirming.
 * The checked file waits in the session between preview and confirm.
 */
@Controller
public class RestoreController {

    static final String PLAN = "restorePlan";

    private final RestoreService restoreService;

    public RestoreController(RestoreService restoreService) {
        this.restoreService = restoreService;
    }

    @GetMapping("/restore")
    public String upload() {
        return "restore/upload";
    }

    @PostMapping("/restore")
    public String preview(@AuthenticationPrincipal AppUserDetails user, @RequestParam("file") MultipartFile file,
                          HttpSession session, Model model) {
        if (file.isEmpty()) {
            model.addAttribute("error", "Choose a backup ZIP or an expenses CSV file.");
            return "restore/upload";
        }
        RestoreService.Plan plan;
        try {
            plan = restoreService.preview(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            model.addAttribute("error", "That file couldn't be read: " + e.getMessage());
            return "restore/upload";
        }
        session.setAttribute(PLAN, plan);
        return showPreview(user, plan, model);
    }

    @PostMapping("/restore/confirm")
    public String confirm(@AuthenticationPrincipal AppUserDetails user,
                          @RequestParam(defaultValue = "add") String mode,
                          @RequestParam(defaultValue = "false") boolean confirmReplace,
                          HttpSession session, Model model) {
        if (!(session.getAttribute(PLAN) instanceof RestoreService.Plan plan)) {
            model.addAttribute("error", "Nothing to restore any more. Upload the file again.");
            return "restore/upload";
        }
        boolean replace = "replace".equals(mode);
        if (replace && !confirmReplace) {
            model.addAttribute("error", "To replace your data, tick the box that confirms you want your current "
                    + "expenses and budgets deleted. Or choose \"Add to my data\".");
            model.addAttribute("mode", mode);
            return showPreview(user, plan, model);
        }
        RestoreService.Report report = restoreService.apply(user.getId(), plan, replace);
        session.removeAttribute(PLAN);
        model.addAttribute("report", report);
        model.addAttribute("filename", plan.filename());
        return "restore/report";
    }

    private String showPreview(AppUserDetails user, RestoreService.Plan plan, Model model) {
        model.addAttribute("plan", plan);
        model.addAttribute("current", restoreService.currentData(user.getId()));
        return "restore/preview";
    }
}
