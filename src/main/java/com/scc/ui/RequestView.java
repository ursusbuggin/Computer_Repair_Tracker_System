package com.scc.ui;

import com.scc.model.RepairRequest;
import java.util.List;

/** Prints repair requests for customers, technicians, admins, and as a detail card. */
public class RequestView {

    private static String text(String value) {
        return value == null || value.isBlank() ? "(none yet)" : value;
    }

    private static String technician(RepairRequest r) {
        return r.technicianName() == null ? "Not yet assigned" : r.technicianName();
    }

    // Customer view: status and diagnosis of their own requests.
    public static void printForCustomer(List<RepairRequest> list) {
        if (list.isEmpty()) {
            System.out.println("No repair requests found.");
            return;
        }
        for (RepairRequest r : list) {
            System.out.println("Request ID: " + r.requestId());
            System.out.println("Device: " + r.deviceType() + " - " + r.brandModel());
            System.out.println("Problem: " + r.problemDescription());
            System.out.println("Status: " + r.status());
            System.out.println("Technician: " + technician(r));
            System.out.println("Diagnosis: " + text(r.diagnosis()));
            System.out.println("Date Received: " + r.dateReceived());
            InputHelper.printDivider();
        }
        System.out.println("Requests shown: " + list.size());
    }

    // Technician view: the job and what the technician has recorded.
    public static void printForTechnician(List<RepairRequest> list) {
        if (list.isEmpty()) {
            System.out.println("No repair jobs found.");
            return;
        }
        for (RepairRequest r : list) {
            System.out.println("Request ID: " + r.requestId());
            System.out.println("Customer: " + r.customerName() + " (" + r.customerContact() + ")");
            System.out.println("Device: " + r.deviceType() + " - " + r.brandModel());
            System.out.println("Problem: " + r.problemDescription());
            System.out.println("Status: " + r.status());
            System.out.println("Diagnosis: " + text(r.diagnosis()));
            System.out.println("Repair Notes: " + text(r.repairNotes()));
            InputHelper.printDivider();
        }
        System.out.println("Jobs shown: " + list.size());
    }

    // Admin list: one short block per request.
    public static void printForAdmin(List<RepairRequest> list) {
        if (list.isEmpty()) {
            System.out.println("No repair requests found.");
            return;
        }
        for (RepairRequest r : list) {
            System.out.println("Request ID: " + r.requestId());
            System.out.println("Customer: " + r.customerName());
            System.out.println("Device: " + r.deviceType() + " - " + r.brandModel());
            System.out.println("Status: " + r.status());
            System.out.println("Technician: " + technician(r));
            InputHelper.printDivider();
        }
        System.out.println("Requests shown: " + list.size());
    }

    public static void printDetails(RepairRequest r) {
        InputHelper.header("REPAIR REQUEST DETAILS");
        System.out.println("Request ID: " + r.requestId());
        System.out.println("Customer: " + r.customerName() + " (" + r.customerContact() + ")");
        System.out.println("Device Type: " + r.deviceType());
        System.out.println("Brand / Model: " + r.brandModel());
        System.out.println("Problem: " + r.problemDescription());
        System.out.println("Status: " + r.status());
        System.out.println("Technician: " + technician(r));
        System.out.println("Diagnosis: " + text(r.diagnosis()));
        System.out.println("Repair Notes: " + text(r.repairNotes()));
        System.out.println("Date Received: " + r.dateReceived());
        System.out.println("Date Completed: " + (r.dateCompleted() == null ? "(not yet)" : r.dateCompleted()));
    }
}
