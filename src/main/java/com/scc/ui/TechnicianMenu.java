package com.scc.ui;

import com.scc.dao.RepairRequestDao;
import com.scc.model.RepairRequest;
import com.scc.model.RequestResult;
import com.scc.model.User;
import java.sql.SQLException;

/**
 * The Technician dashboard: technicians only see and update the jobs assigned to them.
 * They cannot delete records or see other technicians' accounts.
 */
public class TechnicianMenu {
    private final InputHelper input;
    private final RepairRequestDao requestDao = new RepairRequestDao();

    public TechnicianMenu(InputHelper input) {
        this.input = input;
    }

    public void run(User user) {
        boolean loggedIn = true;
        while (loggedIn) {
            InputHelper.header("TECHNICIAN DASHBOARD");
            System.out.println("Welcome, " + user.fullName() + "!");
            System.out.println();
            System.out.println("1. View Assigned Jobs");
            System.out.println("2. Record / Update Diagnosis and Repair Notes");
            System.out.println("3. Update Repair Status");
            System.out.println("4. My Repair History");
            System.out.println("5. Logout");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 5);

            try {
                switch (choice) {
                    case 1:
                        InputHelper.header("ASSIGNED JOBS");
                        RequestView.printForTechnician(requestDao.findAssigned(user));
                        break;
                    case 2:
                        recordDiagnosis(user);
                        break;
                    case 3:
                        updateStatus(user);
                        break;
                    case 4:
                        InputHelper.header("MY REPAIR HISTORY");
                        RequestView.printForTechnician(requestDao.findHandled(user));
                        break;
                    default:
                        loggedIn = false;
                        break;
                }
            } catch (SQLException e) {
                InputHelper.showDbError(e);
            } catch (SecurityException e) {
                InputHelper.showAccessDenied(e);
            }
        }
    }

    // Diagnosis and repair notes of an assigned job that is still open.
    private void recordDiagnosis(User tech) throws SQLException {
        InputHelper.header("DIAGNOSIS AND REPAIR NOTES");
        int id = input.readInt("Enter Request ID: ");
        RepairRequest job = requestDao.findAssignedJob(tech, id);
        if (job == null) {
            System.out.println("Job not found. You can only update jobs assigned to you.");
            return;
        }
        if (!job.isOpen()) {
            System.out.println("This job is " + job.status() + " and can no longer be changed.");
            return;
        }
        boolean done = false;
        while (!done) {
            RepairRequest current = requestDao.findAssignedJob(tech, id); // reload to show the saved values
            RequestView.printDetails(current);
            System.out.println();
            System.out.println("1. Record / Update Diagnosis");
            System.out.println("2. Record / Update Repair Notes");
            System.out.println("3. Done");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 3);
            if (choice == 1) {
                String diagnosis = input.readNonEmpty("Diagnosis: ");
                System.out.println(requestDao.recordDiagnosis(tech, id, diagnosis)
                        ? "Saved: diagnosis updated." : "Nothing was changed. The job is no longer open.");
            } else if (choice == 2) {
                String notes = input.readNonEmpty("Repair Notes: ");
                System.out.println(requestDao.recordNotes(tech, id, notes)
                        ? "Saved: repair notes updated." : "Nothing was changed. The job is no longer open.");
            } else {
                done = true;
            }
        }
    }

    // Pending -> Ongoing -> Completed.
    private void updateStatus(User tech) throws SQLException {
        InputHelper.header("UPDATE REPAIR STATUS");
        int id = input.readInt("Enter Request ID: ");
        RepairRequest job = requestDao.findAssignedJob(tech, id);
        if (job == null) {
            System.out.println("Job not found. You can only update jobs assigned to you.");
            return;
        }
        System.out.println("Device: " + job.deviceType() + " - " + job.brandModel());
        System.out.println("Current status: " + job.status());
        System.out.println();
        System.out.println("Change status to:");
        System.out.println("1. Ongoing");
        System.out.println("2. Completed");
        System.out.println("3. Back");
        int choice = input.readMenuChoice("Enter your choice: ", 1, 3);
        if (choice == 3) {
            return;
        }
        String newStatus = choice == 1 ? RepairRequest.STATUS_ONGOING : RepairRequest.STATUS_COMPLETED;
        RequestResult result = requestDao.updateStatus(tech, id, newStatus);
        switch (result) {
            case CHANGED:
                System.out.println("Status changed to " + newStatus + ".");
                break;
            case NOT_FOUND:
                System.out.println("Job not found.");
                break;
            case INVALID_CHANGE:
                System.out.println("This status change is not allowed. (Pending -> Ongoing -> Completed)");
                break;
            case DIAGNOSIS_REQUIRED:
                System.out.println("Record a diagnosis first, then mark the job as Completed.");
                break;
            default:
                System.out.println("Nothing was changed. The request was updated by someone else.");
                break;
        }
    }
}
