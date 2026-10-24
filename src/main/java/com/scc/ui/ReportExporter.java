package com.scc.ui;

import com.scc.model.StatusReportRow;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Saves the Repair Status Report as a CSV file in the "reports" folder.
 * A CSV file opens in Excel. It is written with plain Java (no extra library).
 * The file is returned ONLY if it was really created.
 */
public class ReportExporter {
    public static final String REPORT_FOLDER = "reports";

    public File exportCsv(List<StatusReportRow> rows) throws IOException {
        File folder = new File(REPORT_FOLDER);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create the folder '" + REPORT_FOLDER + "'.");
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File file = new File(folder, "Repair_Status_Report_" + stamp + ".csv");
        int counter = 2;
        while (file.exists()) { // never overwrite an older export
            file = new File(folder, "Repair_Status_Report_" + stamp + "_" + counter + ".csv");
            counter++;
        }

        int pending = 0;
        int ongoing = 0;
        int completed = 0;
        int cancelled = 0;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("Technician,Pending,Ongoing,Completed,Cancelled,Total");
            writer.newLine();
            for (StatusReportRow r : rows) {
                writer.write(csv(r.technician()) + "," + r.pending() + "," + r.ongoing() + ","
                        + r.completed() + "," + r.cancelled() + "," + r.total());
                writer.newLine();
                pending += r.pending();
                ongoing += r.ongoing();
                completed += r.completed();
                cancelled += r.cancelled();
            }
            writer.write("ALL REQUESTS," + pending + "," + ongoing + "," + completed + "," + cancelled + ","
                    + (pending + ongoing + completed + cancelled));
            writer.newLine();
        }
        if (!file.exists() || file.length() == 0) {
            throw new IOException("The file was not created correctly.");
        }
        return file;
    }

    // Puts quotes around a CSV value when it contains a comma, quote, or new line.
    private String csv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
