package com.scc.dao;

import com.scc.model.ActivityLog;
import com.scc.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL for the Activity Log. Logs can only be added or read:
 * there is no method to edit or delete them.
 */
public class ActivityLogDao {
    private static final int MAX_ROWS = 100;

    // Adds a log inside a transaction that is already open (so the data and its log are saved together).
    public void addLog(Connection conn, int userId, String action, Integer requestId) throws SQLException {
        String sql = "INSERT INTO activity_log (user_id, action, request_id, log_datetime) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, action);
            if (requestId == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, requestId);
            }
            ps.setString(4, DatabaseConnection.now());
            ps.executeUpdate();
        }
    }

    // Adds a log using its own connection (for actions like login, logout, and reports).
    public void addLog(int userId, String action, Integer requestId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            addLog(conn, userId, action, requestId);
        }
    }

    // ---------- Admin viewer ----------

    public List<ActivityLog> findRecent(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return query(" ORDER BY l.log_id DESC LIMIT " + MAX_ROWS);
    }

    public List<ActivityLog> findByUsername(User actor, String keyword) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return query(" WHERE u.username LIKE ? ORDER BY l.log_id DESC LIMIT " + MAX_ROWS, "%" + keyword + "%");
    }

    public List<ActivityLog> findByRequestId(User actor, int requestId) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return query(" WHERE l.request_id = ? ORDER BY l.log_id DESC LIMIT " + MAX_ROWS, requestId);
    }

    public List<ActivityLog> findByAction(User actor, String keyword) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        return query(" WHERE l.action LIKE ? ORDER BY l.log_id DESC LIMIT " + MAX_ROWS, "%" + keyword + "%");
    }

    // The part after "suffix" is written in this file; user text is only passed as a parameter.
    private List<ActivityLog> query(String suffix, Object... params) throws SQLException {
        String sql = "SELECT l.log_id, l.log_datetime AS timestamp, u.username, u.role, l.action, l.request_id "
                + "FROM activity_log l LEFT JOIN user_account u ON l.user_id = u.user_id" + suffix;
        List<ActivityLog> logs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof Integer) {
                    ps.setInt(i + 1, (Integer) params[i]);
                } else {
                    ps.setString(i + 1, params[i].toString());
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int requestValue = rs.getInt("request_id");
                    Integer requestId = rs.wasNull() ? null : requestValue;
                    logs.add(new ActivityLog(rs.getInt("log_id"), rs.getString("username"), rs.getString("role"),
                            rs.getString("action"), requestId, rs.getString("timestamp")));
                }
            }
        }
        return logs;
    }
}
