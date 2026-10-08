package com.example.expenses.web;

import com.example.expenses.security.AppUserDetails;
import com.example.expenses.service.InsightsService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.YearMonth;

@Controller
public class InsightsController {

    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping("/insights")
    public String insights(@AuthenticationPrincipal AppUserDetails user,
                           @RequestParam(required = false) YearMonth month, Model model) {
        LocalDate today = LocalDate.now();
        YearMonth selected = month != null ? month : YearMonth.from(today);
        model.addAttribute("month", selected);
        model.addAttribute("comparison", insightsService.compareWithPreviousMonth(user.getId(), selected, today));
        return "insights";
    }
}
