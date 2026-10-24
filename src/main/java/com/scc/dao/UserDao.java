package com.scc.dao;

import com.scc.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for accounts: register, login check, and account management.
 * A login account (user_account) is linked to a customer or technician profile row.
 * Accounts are never deleted. Their status is changed instead (PENDING, ACTIVE, INACTIVE),
 * and the profile status (Active / Inactive) is kept in step with it.
 */
public class UserDao {
    // The account together with its profile (names, contact, specialization).
    private static final String SELECT_USER =
            "SELECT u.user_id, u.username, u.password_hash, u.role, u.account_status, u.customer_id, "
                    + "u.technician_id, COALESCE(c.first_name, t.first_name) AS first_name, "
                    + "COALESCE(c.last_name, t.last_name) AS last_name, "
                    + "COALESCE(c.contact_number, t.contact_number) AS contact_number, "
                    + "t.specialization AS specialization "
                    + "FROM user_account u LEFT JOIN customer c ON u.customer_id = c.customer_id "
                    + "LEFT JOIN technician t ON u.technician_id = t.technician_id";

    private static final String FULL_NAME_SQL =
            "(COALESCE(c.first_name, t.first_name, '') || ' ' || COALESCE(c.last_name, t.last_name, ''))";

    private final ActivityLogDao logDao = new ActivityLogDao();

    // ---------- Finding users ----------

    public User findByUsername(String username) throws SQLException {
        return findOne(" WHERE u.username = ?", username);
    }

    public User findById(int userId) throws SQLException {
        return findOne(" WHERE u.user_id = ?", userId);
    }

    // Finds the account of a technician by the technician_id (the ID used on repair requests).
    public User findByTechnicianId(int technicianId) throws SQLException {
        return findOne(" WHERE u.technician_id = ?", technicianId);
    }

    public boolean usernameExists(String username) throws SQLException {
        return findByUsername(username) != null;
    }

    // Admin: accounts filtered by role, status, and/or a name/username keyword (any of them can be null).
    public List<User> findAll(User actor, String role, String status, String keyword) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        StringBuilder sql = new StringBuilder(SELECT_USER + " WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (role != null) {
            sql.append(" AND u.role = ?");
            params.add(role);
        }
        if (status != null) {
            sql.append(" AND u.account_status = ?");
            params.add(status);
        }
        if (keyword != null) {
            sql.append(" AND (" + FULL_NAME_SQL + " LIKE ? OR u.username LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        sql.append(" ORDER BY u.user_id");

        List<User> users = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bind(ps, params.toArray());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapUser(rs));
                }
            }
        }
        return users;
    }

    private User findOne(String where, Object... params) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_USER + where)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        }
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof Integer) {
                ps.setInt(i + 1, (Integer) params[i]);
            } else {
                ps.setString(i + 1, params[i].toString());
            }
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        int customerValue = rs.getInt("customer_id");
        Integer customerId = rs.wasNull() ? null : customerValue;
        int technicianValue = rs.getInt("technician_id");
        Integer technicianId = rs.wasNull() ? null : technicianValue;
        return new User(rs.getInt("user_id"), rs.getString("first_name"), rs.getString("last_name"),
                rs.getString("contact_number"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("role"), rs.getString("account_status"), customerId, technicianId,
                rs.getString("specialization"));
    }

    // ---------- Login and logout ----------

    // Checks the username and password against the database.
    // Returns the user if the password is correct (any status), or null if the login details are wrong.
    public User authenticate(String username, String password) throws SQLException {
        User user = findByUsername(username);
        if (user == null || !PasswordHasher.verify(password, user.passwordHash())) {
            return null;
        }
        return user;
    }

    public void recordLogin(User user) throws SQLException {
        logDao.addLog(user.userId(), "Login", null);
    }

    public void recordLogout(User user) throws SQLException {
        logDao.addLog(user.userId(), "Logout", null);
    }

    // ---------- Register ----------

    // Saves a new CUSTOMER (profile row + login account). The account is PENDING until an admin approves it,
    // and the customer profile stays Inactive until then.
    // Technician and admin accounts can only be created by an admin (see createAccount).
    public User register(String firstName, String lastName, String contact, String username, String password,
                         String role) throws SQLException {
        if (!User.ROLE_CUSTOMER.equals(role)) {
            throw new IllegalArgumentException("Only customer accounts can be registered here. Ask an admin for other accounts.");
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int customerId = insertCustomer(conn, firstName, lastName, contact, "Inactive");
                int newId = insertAccount(conn, username, password, role, User.STATUS_PENDING, customerId, null);
                logDao.addLog(conn, newId, "Account registered: " + username + " (" + role + ")", null);
                conn.commit();
                return findById(newId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private int insertCustomer(Connection conn, String firstName, String lastName, String contact, String status)
            throws SQLException {
        String sql = "INSERT INTO customer (first_name, last_name, contact_number, status) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, firstName);
            ps.setString(2, lastName);
            ps.setString(3, contact);
            ps.setString(4, status);
            ps.executeUpdate();
        }
        return DatabaseConnection.lastInsertId(conn);
    }

    private int insertTechnician(Connection conn, String firstName, String lastName, String specialization,
                                 String contact, String status) throws SQLException {
        String sql = "INSERT INTO technician (first_name, last_name, specialization, contact_number, status) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, firstName);
            ps.setString(2, lastName);
            ps.setString(3, specialization);
            ps.setString(4, contact);
            ps.setString(5, status);
            ps.executeUpdate();
        }
        return DatabaseConnection.lastInsertId(conn);
    }

    // Saves a login account and returns the new user ID. Only the password HASH is saved.
    private int insertAccount(Connection conn, String username, String password, String role, String status,
                              Integer customerId, Integer technicianId) throws SQLException {
        String sql = "INSERT INTO user_account (username, password_hash, role, customer_id, technician_id, "
                + "account_status) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, PasswordHasher.hash(password));
            ps.setString(3, role);
            if (customerId == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, customerId);
            }
            if (technicianId == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, technicianId);
            }
            ps.setString(6, status);
            ps.executeUpdate();
        }
        return DatabaseConnection.lastInsertId(conn);
    }

    // ---------- Admin: account management ----------

    // Admin creates a Customer, Technician, or Admin account. These are ACTIVE immediately.
    // firstName, lastName, and contact are not used for an Admin; specialization is only used for a Technician.
    // Returns the new user ID.
    public int createAccount(User actor, String role, String firstName, String lastName, String contact,
                             String specialization, String username, String password) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        if (!role.equals(User.ROLE_CUSTOMER) && !role.equals(User.ROLE_TECHNICIAN) && !role.equals(User.ROLE_ADMIN)) {
            throw new IllegalArgumentException("Unknown role.");
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer customerId = null;
                Integer technicianId = null;
                if (role.equals(User.ROLE_CUSTOMER)) {
                    customerId = insertCustomer(conn, firstName, lastName, contact, "Active");
                } else if (role.equals(User.ROLE_TECHNICIAN)) {
                    technicianId = insertTechnician(conn, firstName, lastName, specialization, contact, "Active");
                }
                int newId = insertAccount(conn, username, password, role, User.STATUS_ACTIVE, customerId, technicianId);
                logDao.addLog(conn, actor.userId(), "User account created: " + username + " (" + role + ")", null);
                conn.commit();
                return newId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Admin edits one profile field of a customer or technician.
    // field must be one of: first_name, last_name, contact_number, specialization (technicians only).
    public void updateField(User actor, User target, String field, String value) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        boolean allowed = field.equals("first_name") || field.equals("last_name")
                || field.equals("contact_number") || field.equals("specialization");
        if (!allowed) {
            throw new IllegalArgumentException("This field cannot be edited.");
        }
        String log = "User account edited: " + target.username() + " (" + field.replace('_', ' ') + " updated)";
        if (target.isCustomer() && !field.equals("specialization")) {
            saveProfileField(actor.userId(), "customer", "customer_id", target.customerId(), field, value, log);
        } else if (target.isTechnician()) {
            saveProfileField(actor.userId(), "technician", "technician_id", target.technicianId(), field, value, log);
        } else {
            throw new IllegalArgumentException("This field cannot be edited for this account.");
        }
    }

    // Admin resets the password of an account.
    public void resetPassword(User actor, User target, String newPassword) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        saveAccountField(actor.userId(), target.userId(), "password_hash", PasswordHasher.hash(newPassword),
                "User account edited: " + target.username() + " (password reset)");
    }

    // A customer changes their OWN first name, last name, contact number, or password.
    // Role and status cannot be changed here. For "password_hash" the value must already be hashed.
    public void updateOwnField(User customer, String field, String value) throws SQLException {
        Permissions.require(customer, User.ROLE_CUSTOMER);
        String what = field.equals("password_hash") ? "password" : field.replace('_', ' ');
        String log = "Customer profile updated (" + what + ")";
        if (field.equals("password_hash")) {
            saveAccountField(customer.userId(), customer.userId(), field, value, log);
        } else if (field.equals("first_name") || field.equals("last_name") || field.equals("contact_number")) {
            saveProfileField(customer.userId(), "customer", "customer_id", customer.customerId(), field, value, log);
        } else {
            throw new IllegalArgumentException("This field cannot be edited.");
        }
    }

    // Updates ONE column of the customer or technician table and writes the log in the same transaction.
    // The table and column names are always written in this file (and checked above), never typed by a user.
    private void saveProfileField(int actorId, String table, String idColumn, int id, String column, String value,
                                  String logText) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String sql = "UPDATE " + table + " SET " + column + " = ? WHERE " + idColumn + " = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, value);
                    ps.setInt(2, id);
                    ps.executeUpdate();
                }
                logDao.addLog(conn, actorId, logText, null);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Updates ONE column of user_account (the column name is always written in this file).
    private void saveAccountField(int actorId, int targetId, String column, String value, String logText)
            throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE user_account SET " + column + " = ? WHERE user_id = ?")) {
                    ps.setString(1, value);
                    ps.setInt(2, targetId);
                    ps.executeUpdate();
                }
                logDao.addLog(conn, actorId, logText, null);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // Admin approves, rejects, activates, or deactivates an account. The customer / technician profile
    // status becomes Active (account ACTIVE) or Inactive (anything else).
    // Returns false (and changes nothing) when it would leave the system without an active admin.
    public boolean setStatus(User actor, User target, String newStatus, String logText) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (target.isAdmin() && !newStatus.equals(User.STATUS_ACTIVE)
                    && (target.userId() == actor.userId() || countActiveAdmins(conn) <= 1)) {
                return false; // safeguard: keep at least one active admin
            }
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE user_account SET account_status = ? WHERE user_id = ?")) {
                    ps.setString(1, newStatus);
                    ps.setInt(2, target.userId());
                    ps.executeUpdate();
                }
                String profileStatus = newStatus.equals(User.STATUS_ACTIVE) ? "Active" : "Inactive";
                if (target.customerId() != null) {
                    updateProfileStatus(conn, "customer", "customer_id", target.customerId(), profileStatus);
                }
                if (target.technicianId() != null) {
                    updateProfileStatus(conn, "technician", "technician_id", target.technicianId(), profileStatus);
                }
                logDao.addLog(conn, actor.userId(), logText, null);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void updateProfileStatus(Connection conn, String table, String idColumn, int id, String status)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE " + table + " SET status = ? WHERE " + idColumn + " = ?")) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    // Admin: how many accounts are waiting for approval.
    public int countPendingAccounts(User actor) throws SQLException {
        Permissions.require(actor, User.ROLE_ADMIN);
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM user_account WHERE account_status = 'PENDING'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private int countActiveAdmins(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM user_account WHERE role = 'ADMIN' AND account_status = 'ACTIVE'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
