package com.scc.dao;

import com.scc.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Makes sure all database tables exist before the menus start, and creates the ready-made
 * Admin and Technician accounts. Every statement is safe to run on every launch.
 *
 * Tables (from the system proposal): customer, technician, repair_request, user_account, activity_log.
 */
public class DatabaseInitializer {
    // Default accounts that always exist (created only if the username is not in the database yet).
    // Customers can only self-register; more technicians and admins are added by an admin.
    private static final String DEFAULT_PASSWORD = "1234";

    // Returns true when the database is ready.
    public static boolean initialize() {
        try {
            createTables();
            seedDefaultAccounts();
            return true;
        } catch (SQLException e) {
            System.out.println("The database could not be prepared.");
            System.out.println("Make sure the sqlite-jdbc dependency is in pom.xml and Maven has finished loading.");
            System.out.println("Detail: " + e.getMessage());
            return false;
        }
    }

    private static void createTables() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection(); Statement st = conn.createStatement()) {
            // TABLE 1: Customer (contact_number is an extra column so staff can reach the customer)
            st.execute("CREATE TABLE IF NOT EXISTS customer ("
                    + "customer_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "first_name TEXT NOT NULL, "
                    + "last_name TEXT NOT NULL, "
                    + "contact_number TEXT NOT NULL, "
                    + "status TEXT NOT NULL DEFAULT 'Active' CHECK (status IN ('Active', 'Inactive')))");

            // TABLE 2: Technician
            st.execute("CREATE TABLE IF NOT EXISTS technician ("
                    + "technician_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "first_name TEXT NOT NULL, "
                    + "last_name TEXT NOT NULL, "
                    + "specialization TEXT NOT NULL, "
                    + "contact_number TEXT NOT NULL, "
                    + "status TEXT NOT NULL DEFAULT 'Active' CHECK (status IN ('Active', 'Inactive')))");

            // TABLE 3: RepairRequest (technician_id stays empty until a technician is assigned)
            st.execute("CREATE TABLE IF NOT EXISTS repair_request ("
                    + "request_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "customer_id INTEGER NOT NULL REFERENCES customer(customer_id), "
                    + "technician_id INTEGER REFERENCES technician(technician_id), "
                    + "device_type TEXT NOT NULL, "
                    + "brand_model TEXT NOT NULL, "
                    + "problem_description TEXT NOT NULL, "
                    + "diagnosis TEXT, "
                    + "repair_notes TEXT, "
                    + "repair_status TEXT NOT NULL DEFAULT 'Pending' "
                    + "CHECK (repair_status IN ('Pending', 'Ongoing', 'Completed', 'Cancelled')), "
                    + "date_received TEXT NOT NULL, "
                    + "date_completed TEXT)");

            // TABLE 4: UserAccount (customer_id for a Customer, technician_id for a Technician, neither for an Admin)
            st.execute("CREATE TABLE IF NOT EXISTS user_account ("
                    + "user_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "username TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                    + "password_hash TEXT NOT NULL, "
                    + "role TEXT NOT NULL CHECK (role IN ('CUSTOMER', 'TECHNICIAN', 'ADMIN')), "
                    + "customer_id INTEGER REFERENCES customer(customer_id), "
                    + "technician_id INTEGER REFERENCES technician(technician_id), "
                    + "account_status TEXT NOT NULL DEFAULT 'PENDING' "
                    + "CHECK (account_status IN ('PENDING', 'ACTIVE', 'INACTIVE')), "
                    + "CHECK ((role = 'CUSTOMER' AND customer_id IS NOT NULL AND technician_id IS NULL) "
                    + "OR (role = 'TECHNICIAN' AND technician_id IS NOT NULL AND customer_id IS NULL) "
                    + "OR (role = 'ADMIN' AND customer_id IS NULL AND technician_id IS NULL)))");

            // TABLE 5: ActivityLog (request_id is empty for login / logout and account actions)
            st.execute("CREATE TABLE IF NOT EXISTS activity_log ("
                    + "log_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "user_id INTEGER NOT NULL REFERENCES user_account(user_id), "
                    + "request_id INTEGER REFERENCES repair_request(request_id) ON DELETE SET NULL, "
                    + "action TEXT NOT NULL, "
                    + "log_datetime TEXT NOT NULL)");
        }
    }

    // Creates the ready-made Admin and Technician accounts (ACTIVE) if they do not exist yet.
    private static void seedDefaultAccounts() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (!usernameExists(conn, "admin")) {
                insertAccount(conn, "admin", User.ROLE_ADMIN, null, null);
            }
            if (!usernameExists(conn, "technician")) {
                int technicianId;
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO technician "
                        + "(first_name, last_name, specialization, contact_number, status) "
                        + "VALUES ('Repair', 'Technician', 'General Repair', '09000000002', 'Active')")) {
                    ps.executeUpdate();
                }
                technicianId = DatabaseConnection.lastInsertId(conn);
                insertAccount(conn, "technician", User.ROLE_TECHNICIAN, null, technicianId);
            }
        }
    }

    private static boolean usernameExists(Connection conn, String username) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM user_account WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void insertAccount(Connection conn, String username, String role, Integer customerId,
                                      Integer technicianId) throws SQLException {
        String sql = "INSERT INTO user_account (username, password_hash, role, customer_id, technician_id, "
                + "account_status) VALUES (?, ?, ?, ?, ?, 'ACTIVE')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, PasswordHasher.hash(DEFAULT_PASSWORD));
            ps.setString(3, role);
            if (customerId == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, customerId);
            }
            if (technicianId == null) {
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(5, technicianId);
            }
            ps.executeUpdate();
        }
    }
}
