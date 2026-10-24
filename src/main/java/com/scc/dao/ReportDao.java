package com.scc.dao;

import com.scc.model.StatusReportRow;
import com.scc.model.User;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL for the Repair Status Report. Admin only. */
public class ReportDao {

    // One row per technician (plus "Unassigned"). The database counts the requests of each status.
    public List<StatusReportRow> buildStatusReport(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        String sql = "SELECT COALESCE(t.first_name || ' ' || t.last_name, 'Unassigned') AS technician, "
                + "COALESCE(SUM(CASE WHEN r.repair_status = 'Pending' THEN 1 ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN r.repair_status = 'Ongoing' THEN 1 ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN r.repair_status = 'Completed' THEN 1 ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN r.repair_status = 'Cancelled' THEN 1 ELSE 0 END), 0) "
                + "FROM repair_request r LEFT JOIN technician t ON r.technician_id = t.technician_id "
                + "GROUP BY r.technician_id ORDER BY (r.technician_id IS NULL), technician";
        List<StatusReportRow> rows = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new StatusReportRow(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)));
            }
        }
        return rows;
    }
}
