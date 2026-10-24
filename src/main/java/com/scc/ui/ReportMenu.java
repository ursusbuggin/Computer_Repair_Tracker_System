package com.scc.ui;

import com.scc.dao.ActivityLogDao;
import com.scc.dao.ReportDao;
import com.scc.model.StatusReportRow;
import com.scc.model.User;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/** Admin screen: the Repair Status Report (with CSV export). */
public class ReportMenu {
    private final InputHelper input;
    private final ReportDao reportDao = new ReportDao();
    private final ActivityLogDao logDao = new ActivityLogDao();
    private final ReportExporter exporter = new ReportExporter();

    public ReportMenu(InputHelper input) {
        this.input = input;
    }

    public void viewReport(User actor) {
        InputHelper.header("REPAIR STATUS REPORT");
        try {
            List<StatusReportRow> rows = reportDao.buildStatusReport(actor);
            if (rows.isEmpty()) {
                System.out.println("No repair requests to report.");
            } else {
                int pending = 0;
                int ongoing = 0;
                int completed = 0;
                int cancelled = 0;
                System.out.println(String.format("%-22s %-8s %-8s %-10s %-10s %s",
                        "Technician", "Pending", "Ongoing", "Completed", "Cancelled", "Total"));
                InputHelper.printDivider();
                for (StatusReportRow r : rows) {
                    System.out.println(String.format("%-22s %-8d %-8d %-10d %-10d %d",
                            InputHelper.cut(r.technician(), 22), r.pending(), r.ongoing(), r.completed(),
                            r.cancelled(), r.total()));
                    pending += r.pending();
                    ongoing += r.ongoing();
                    completed += r.completed();
                    cancelled += r.cancelled();
                }
                InputHelper.printDivider();
                System.out.println(String.format("%-22s %-8d %-8d %-10d %-10d %d", "ALL REQUESTS", pending, ongoing,
                        completed, cancelled, pending + ongoing + completed + cancelled));
            }
            logDao.addLog(actor.userId(), "Report generated: Repair Status Report", null);

            if (!rows.isEmpty() && input.readConfirm("Export this report to a CSV file? (Y/N): ")) {
                File file = exporter.exportCsv(rows);
                System.out.println("Report exported successfully.");
                System.out.println("File: " + file.getAbsolutePath());
                logDao.addLog(actor.userId(), "Report exported: Repair Status Report (CSV)", null);
            }
        } catch (SQLException e) {
            InputHelper.showDbError(e);
        } catch (SecurityException e) {
            InputHelper.showAccessDenied(e);
        } catch (IOException e) {
            System.out.println("The report could not be exported: " + e.getMessage());
        }
    }
}
