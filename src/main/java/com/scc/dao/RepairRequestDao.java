package com.scc.dao;

import com.scc.model.AssignResult;
import com.scc.model.RepairRequest;
import com.scc.model.RequestResult;
import com.scc.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for repair requests (table repair_request). Every method checks the role first (Permissions),
 * and the customer and technician methods only reach the user's OWN requests / assigned jobs.
 * Requests are never deleted, except by the admin (invalid records only).
 * customer_id and technician_id on a request are the IDs of the customer / technician tables
 * (User.customerId() / User.technicianId()), not the login user_id.
 */
public class RepairRequestDao {
    private static final String SELECT_REQUEST =
            "SELECT r.request_id, r.customer_id, (c.first_name || ' ' || c.last_name) AS customer_name, "
                    + "c.contact_number AS customer_contact, r.technician_id, "
                    + "(t.first_name || ' ' || t.last_name) AS technician_name, r.device_type, r.brand_model, "
                    + "r.problem_description, r.repair_status, r.diagnosis, r.repair_notes, r.date_received, "
                    + "r.date_completed "
                    + "FROM repair_request r JOIN customer c ON r.customer_id = c.customer_id "
                    + "LEFT JOIN technician t ON r.technician_id = t.technician_id";

    private final ActivityLogDao logDao = new ActivityLogDao();
    private final UserDao userDao = new UserDao();

    // =====================================================================
    // Customer
    // =====================================================================

    // A customer submits a new repair request. It always starts as Pending.
    public int create(User customer, String deviceType, String brandModel, String problem) throws SQLException {
        Permissions.require(customer, User.ROLE_CUSTOMER);
        String sql = "INSERT INTO repair_request (customer_id, device_type, brand_model, problem_description, "
                + "repair_status, date_received) VALUES (?, ?, ?, ?, 'Pending', ?)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, customer.customerId());
                    ps.setString(2, deviceType);
                    ps.setString(3, brandModel);
                    ps.setString(4, problem);
                    ps.setString(5, DatabaseConnection.now());
                    ps.executeUpdate();
                }
                int requestId = DatabaseConnection.lastInsertId(conn);
                logDao.addLog(conn, customer.userId(),
                        "Repair request submitted: " + deviceType + " (" + brandModel + ")", requestId);
                conn.commit();
                return requestId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // The customer's requests that are still open (Pending or Ongoing).
    public List<RepairRequest> findOpenByCustomer(User customer) throws SQLException {
        Permissions.require(customer, User.ROLE_CUSTOMER);
        return query(" WHERE r.customer_id = ? AND r.repair_status IN ('Pending', 'Ongoing') "
                + "ORDER BY r.request_id DESC", customer.customerId());
    }

    // The customer's full repair history (every status).
    public List<RepairRequest> findAllByCustomer(User customer) throws SQLException {
        Permissions.require(customer, User.ROLE_CUSTOMER);
        return query(" WHERE r.customer_id = ? ORDER BY r.request_id DESC", customer.customerId());
    }

    // Returns the request only if it belongs to this customer.
    public RepairRequest findCustomerRequest(User customer, int requestId) throws SQLException {
        Permissions.require(customer, User.ROLE_CUSTOMER);
        return first(query(" WHERE r.request_id = ? AND r.customer_id = ?", requestId, customer.customerId()));
    }

    // A customer cancels their OWN request, only while it is still Pending (checked by the database).
    public boolean cancelOwn(User customer, int requestId) throws SQLException {
        Permissions.require(customer, User.ROLE_CUSTOMER);
        String sql = "UPDATE repair_request SET repair_status = 'Cancelled' "
                + "WHERE request_id = ? AND customer_id = ? AND repair_status = 'Pending'";
        return updateAndLog(customer.userId(), requestId, sql, new Object[] {requestId, customer.customerId()},
                "Repair request cancelled by customer");
    }

    // =====================================================================
    // Technician (only the jobs assigned to them)
    // =====================================================================

    // Assigned jobs that are still open (Pending or Ongoing).
    public List<RepairRequest> findAssigned(User tech) throws SQLException {
        Permissions.require(tech, User.ROLE_TECHNICIAN);
        return query(" WHERE r.technician_id = ? AND r.repair_status IN ('Pending', 'Ongoing') "
                + "ORDER BY r.request_id", tech.technicianId());
    }

    // Jobs this technician finished (their repair history).
    public List<RepairRequest> findHandled(User tech) throws SQLException {
        Permissions.require(tech, User.ROLE_TECHNICIAN);
        return query(" WHERE r.technician_id = ? AND r.repair_status = 'Completed' "
                + "ORDER BY r.date_completed DESC, r.request_id DESC", tech.technicianId());
    }

    // Returns the job only if it is assigned to this technician.
    public RepairRequest findAssignedJob(User tech, int requestId) throws SQLException {
        Permissions.require(tech, User.ROLE_TECHNICIAN);
        return first(query(" WHERE r.request_id = ? AND r.technician_id = ?", requestId, tech.technicianId()));
    }

    // Saves the diagnosis (only while the job is open). Returns false if nothing was changed.
    public boolean recordDiagnosis(User tech, int requestId, String diagnosis) throws SQLException {
        return updateTechText(tech, requestId, "diagnosis", diagnosis, "Diagnosis recorded");
    }

    // Saves the repair notes (only while the job is open). Returns false if nothing was changed.
    public boolean recordNotes(User tech, int requestId, String notes) throws SQLException {
        return updateTechText(tech, requestId, "repair_notes", notes, "Repair notes recorded");
    }

    // The column name is always written in the two methods above, never typed by a user.
    private boolean updateTechText(User tech, int requestId, String column, String value, String logText)
            throws SQLException {
        Permissions.require(tech, User.ROLE_TECHNICIAN);
        String sql = "UPDATE repair_request SET " + column + " = ? "
                + "WHERE request_id = ? AND technician_id = ? AND repair_status IN ('Pending', 'Ongoing')";
        return updateAndLog(tech.userId(), requestId, sql,
                new Object[] {value, requestId, tech.technicianId()}, logText);
    }

    // Pending -> Ongoing, Ongoing -> Completed (a diagnosis must be recorded before Completed).
    // Completed also saves the date_completed.
    public RequestResult updateStatus(User tech, int requestId, String newStatus) throws SQLException {
        Permissions.require(tech, User.ROLE_TECHNICIAN);
        RepairRequest job = findAssignedJob(tech, requestId);
        if (job == null) {
            return RequestResult.NOT_FOUND;
        }
        if (!RepairRequest.isValidTechnicianChange(job.status(), newStatus)) {
            return RequestResult.INVALID_CHANGE;
        }
        boolean completing = newStatus.equals(RepairRequest.STATUS_COMPLETED);
        if (completing && (job.diagnosis() == null || job.diagnosis().isBlank())) {
            return RequestResult.DIAGNOSIS_REQUIRED;
        }
        // The old status is part of the WHERE, so a change made by someone else in the meantime is not overwritten.
        String sql;
        Object[] params;
        if (completing) {
            sql = "UPDATE repair_request SET repair_status = ?, date_completed = ? "
                    + "WHERE request_id = ? AND technician_id = ? AND repair_status = ?";
            params = new Object[] {newStatus, DatabaseConnection.now(), requestId, tech.technicianId(), job.status()};
        } else {
            sql = "UPDATE repair_request SET repair_status = ? "
                    + "WHERE request_id = ? AND technician_id = ? AND repair_status = ?";
            params = new Object[] {newStatus, requestId, tech.technicianId(), job.status()};
        }
        boolean saved = updateAndLog(tech.userId(), requestId, sql, params,
                "Repair status changed: " + job.status() + " -> " + newStatus);
        return saved ? RequestResult.CHANGED : RequestResult.ALREADY_CHANGED;
    }

    // =====================================================================
    // Admin (everything)
    // =====================================================================

    public List<RepairRequest> findAll(User admin) throws SQLException {
        return find(admin, null, null, false, null);
    }

    // View, search, and filter. Any filter can be null / false.
    //   status          = Pending / Ongoing / Completed / Cancelled
    //   technicianId    = only that technician's requests (technician_id)
    //   unassignedOnly  = only requests without a technician
    //   keyword         = customer name, device type, brand/model, or problem
    public List<RepairRequest> find(User admin, String status, Integer technicianId, boolean unassignedOnly,
                                    String keyword) throws SQLException {
        Permissions.require(admin, User.ROLE_ADMIN);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (status != null) {
            where.append(" AND r.repair_status = ?");
            params.add(status);
        }
        if (technicianId != null) {
            where.append(" AND r.technician_id = ?");
            params.add(technicianId);
        }
        if (unassignedOnly) {
            where.append(" AND r.technician_id IS NULL");
        }
        if (keyword != null) {
            where.append(" AND ((c.first_name || ' ' || c.last_name) LIKE ? OR r.device_type LIKE ? "
                    + "OR r.brand_model LIKE ? OR r.problem_description LIKE ?)");
            for (int i = 0; i < 4; i++) {
                params.add("%" + keyword + "%");
            }
        }
        where.append(" ORDER BY r.request_id DESC");
        return query(where.toString(), params.toArray());
    }

    public RepairRequest findById(User admin, int requestId) throws SQLException {
        Permissions.require(admin, User.ROLE_ADMIN);
        return first(query(" WHERE r.request_id = ?", requestId));
    }

    // Assigns a technician, or reassigns one. technicianId is the technician_id.
    // Only open requests and ACTIVE technicians are allowed.
    public AssignResult assign(User admin, int requestId, int technicianId) throws SQLException {
        Permissions.require(admin, User.ROLE_ADMIN);
        RepairRequest request = findById(admin, requestId);
        if (request == null) {
            return AssignResult.REQUEST_NOT_FOUND;
        }
        if (!request.isOpen()) {
            return AssignResult.REQUEST_CLOSED;
        }
        User tech = userDao.findByTechnicianId(technicianId);
        if (tech == null || !tech.isTechnician() || !tech.isActive()) {
            return AssignResult.INVALID_TECHNICIAN;
        }
        if (request.technicianId() != null && request.technicianId() == technicianId) {
            return AssignResult.ALREADY_ASSIGNED;
        }
        String logText = request.technicianId() == null
                ? "Technician assigned: " + tech.fullName()
                : "Technician reassigned: " + request.technicianName() + " -> " + tech.fullName();
        String sql = "UPDATE repair_request SET technician_id = ? "
                + "WHERE request_id = ? AND repair_status IN ('Pending', 'Ongoing')";
        boolean saved = updateAndLog(admin.userId(), requestId, sql, new Object[] {technicianId, requestId}, logText);
        return saved ? AssignResult.ASSIGNED : AssignResult.REQUEST_CLOSED;
    }

    // Admin cancels an open request. The record is kept.
    public boolean cancelByAdmin(User admin, int requestId) throws SQLException {
        Permissions.require(admin, User.ROLE_ADMIN);
        String sql = "UPDATE repair_request SET repair_status = 'Cancelled' "
                + "WHERE request_id = ? AND repair_status IN ('Pending', 'Ongoing')";
        return updateAndLog(admin.userId(), requestId, sql, new Object[] {requestId},
                "Repair request cancelled by admin");
    }

    // Admin permanently removes an invalid request (the only real delete in the system).
    // The log is not linked to the removed request (the request ID is written in the log text instead).
    public boolean remove(User admin, int requestId) throws SQLException {
        Permissions.require(admin, User.ROLE_ADMIN);
        String sql = "DELETE FROM repair_request WHERE request_id = ?";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, requestId);
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    conn.rollback();
                    return false;
                }
                logDao.addLog(conn, admin.userId(), "Invalid repair request removed: #" + requestId, null);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    // Runs one UPDATE and writes its log in one transaction. Returns false if no row was changed.
    private boolean updateAndLog(int actorId, int requestId, String sql, Object[] params, String logText)
            throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    bind(ps, params);
                    rows = ps.executeUpdate();
                }
                if (rows != 1) {
                    conn.rollback();
                    return false;
                }
                logDao.addLog(conn, actorId, logText, requestId);
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private RepairRequest first(List<RepairRequest> list) {
        return list.isEmpty() ? null : list.get(0);
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

    // "suffix" is written in this file; user values are only passed as parameters.
    private List<RepairRequest> query(String suffix, Object... params) throws SQLException {
        List<RepairRequest> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_REQUEST + suffix)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int techValue = rs.getInt("technician_id");
                    Integer technicianId = rs.wasNull() ? null : techValue;
                    list.add(new RepairRequest(rs.getInt("request_id"), rs.getInt("customer_id"),
                            rs.getString("customer_name"), rs.getString("customer_contact"),
                            technicianId, rs.getString("technician_name"), rs.getString("device_type"),
                            rs.getString("brand_model"), rs.getString("problem_description"),
                            rs.getString("repair_status"), rs.getString("diagnosis"), rs.getString("repair_notes"),
                            rs.getString("date_received"), rs.getString("date_completed")));
                }
            }
        }
        return list;
    }
}
