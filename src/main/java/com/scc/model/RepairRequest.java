package com.scc.model;

/**
 * A customer's repair request (table repair_request). A new request always starts as Pending.
 * Customer and technician names come from the database (joined tables), so they are always up to date.
 * technicianId / technicianName are null until an admin assigns a technician.
 * dateCompleted is null until the technician marks the job Completed.
 */
public record RepairRequest(int requestId, int customerId, String customerName, String customerContact,
                            Integer technicianId, String technicianName, String deviceType, String brandModel,
                            String problemDescription, String status, String diagnosis, String repairNotes,
                            String dateReceived, String dateCompleted) {

    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_ONGOING = "Ongoing";
    public static final String STATUS_COMPLETED = "Completed";
    public static final String STATUS_CANCELLED = "Cancelled";

    // A request is "open" while it can still be worked on (not Completed or Cancelled).
    public boolean isOpen() {
        return status.equals(STATUS_PENDING) || status.equals(STATUS_ONGOING);
    }

    // Technician status changes: Pending -> Ongoing, Ongoing -> Completed. Completed and Cancelled are final.
    public static boolean isValidTechnicianChange(String from, String to) {
        return (from.equals(STATUS_PENDING) && to.equals(STATUS_ONGOING))
                || (from.equals(STATUS_ONGOING) && to.equals(STATUS_COMPLETED));
    }
}
