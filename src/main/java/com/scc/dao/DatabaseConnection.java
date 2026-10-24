package com.scc.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Opens SQLite connections. (The tables are created by DatabaseInitializer.)
 * Every DAO uses this one class, so the whole system uses ONE database file.
 */
public class DatabaseConnection {

    private static final String URL = "jdbc:sqlite:repairshop.db";

    private DatabaseConnection() {
    }

    // Opens a new connection and turns on foreign-key checking (SQLite has it OFF by default).
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(URL);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
        return conn;
    }

    // Current date and time as text, for example 2026-10-11 14:30:05
    public static String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // Returns the ID of the row that was just inserted on this same connection.
    public static int lastInsertId(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
