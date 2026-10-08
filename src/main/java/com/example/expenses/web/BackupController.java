package com.example.expenses.web;

import com.example.expenses.security.AppUserDetails;
import com.example.expenses.service.BackupService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.time.LocalDate;

@Controller
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    /** Downloads the signed-in user's data as a ZIP of CSV files. */
    @GetMapping("/backup")
    public void backup(@AuthenticationPrincipal AppUserDetails user, HttpServletResponse response) throws IOException {
        // Usernames only contain letters, digits, dots, dashes and underscores, so this is a safe file name
        String filename = "expense-tracker-" + user.getUsername() + "-" + LocalDate.now() + ".zip";
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString());
        backupService.writeZip(user.getId(), filename, response.getOutputStream());
    }
}
