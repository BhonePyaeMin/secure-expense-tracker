package com.example.expenses.web;

import com.example.expenses.service.ExpenseNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ExpenseNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String expenseNotFound() {
        return "expenses/not-found";
    }

    // e.g. /expenses?month=banana
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badParameter(MethodArgumentTypeMismatchException ex, Model model) {
        model.addAttribute("status", HttpStatus.BAD_REQUEST.value());
        model.addAttribute("error", "Bad request");
        model.addAttribute("message", "\"" + ex.getValue() + "\" is not a valid value for " + ex.getName() + ".");
        return "error";
    }
}
