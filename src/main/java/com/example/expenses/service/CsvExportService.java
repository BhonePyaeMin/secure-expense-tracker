package com.example.expenses.service;

import com.example.expenses.model.Expense;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

@Service
public class CsvExportService {

    static final String HEADER = "id,title,amount,category,date,note";
    private static final String LINE_END = "\r\n";

    /** Writes one row per expense. Starts with a UTF-8 byte order mark so Excel shows ฿ and Thai text correctly. */
    public void write(List<Expense> expenses, Writer writer) throws IOException {
        writer.write('\uFEFF');
        writer.write(HEADER);
        writer.write(LINE_END);
        for (Expense expense : expenses) {
            writer.write(String.join(",",
                    String.valueOf(expense.getId()),
                    Csv.escape(expense.getTitle()),
                    expense.getAmount().toPlainString(),
                    expense.getCategory().name(),
                    expense.getDate().toString(),
                    Csv.escape(expense.getNote())));
            writer.write(LINE_END);
        }
        writer.flush();
    }
}
