package com.scc.model;

/** One row of the Activity Log. requestId is null when it does not apply. */
public record ActivityLog(int logId, String username, String userRole, String action,
                          Integer requestId, String timestamp) {
}
