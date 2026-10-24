package com.scc.model;

/** One line of the Repair Status Report: the request counts of one technician (or "Unassigned"). */
public record StatusReportRow(String technician, int pending, int ongoing, int completed, int cancelled) {

    public int total() {
        return pending + ongoing + completed + cancelled;
    }
}
