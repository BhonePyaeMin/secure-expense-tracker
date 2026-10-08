package com.example.expenses.web;

import com.example.expenses.security.AppUserDetails;
import com.example.expenses.service.AuditService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Shows the logged-in user's own audit log. */
@Controller
public class ActivityController {

    private static final int PAGE_SIZE = 20;

    private final AuditService auditService;

    public ActivityController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/activity")
    public String activity(@AuthenticationPrincipal AppUserDetails user,
                           @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("entries",
                auditService.findForUser(user.getId(), PageRequest.of(Math.max(page, 0), PAGE_SIZE)));
        return "activity";
    }
}
