package com.scc.ui;

import com.scc.dao.RepairRequestDao;
import com.scc.dao.UserDao;
import com.scc.model.AssignResult;
import com.scc.model.RepairRequest;
import com.scc.model.User;
import java.sql.SQLException;
import java.util.List;

/** Admin screens for repair requests: view, search / filter, assign, cancel, and remove invalid records. */
public class RequestMenu {
    private static final String[] STATUSES = {
            RepairRequest.STATUS_PENDING, RepairRequest.STATUS_ONGOING,
            RepairRequest.STATUS_COMPLETED, RepairRequest.STATUS_CANCELLED
    };

    private final InputHelper input;
    private final UserDao userDao;
    private final RepairRequestDao requestDao = new RepairRequestDao();

    public RequestMenu(InputHelper input, UserDao userDao) {
        this.input = input;
        this.userDao = userDao;
    }

    public void run(User admin) {
        boolean back = false;
        while (!back) {
            InputHelper.header("MANAGE REPAIR REQUESTS");
            System.out.println("1. View All Requests");
            System.out.println("2. Search / Filter Requests");
            System.out.println("3. View Request Details");
            System.out.println("4. Assign / Reassign Technician");
            System.out.println("5. Cancel Request");
            System.out.println("6. Remove Invalid Request");
            System.out.println("7. Back");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 7);
            try {
                switch (choice) {
                    case 1:
                        RequestView.printForAdmin(requestDao.findAll(admin));
                        break;
                    case 2:
                        searchFilter(admin);
                        break;
                    case 3:
                        viewDetails(admin);
                        break;
                    case 4:
                        assign(admin);
                        break;
                    case 5:
                        cancel(admin);
                        break;
                    case 6:
                        remove(admin);
                        break;
                    default:
                        back = true;
                        break;
                }
            } catch (SQLException e) {
                InputHelper.showDbError(e);
            } catch (SecurityException e) {
                InputHelper.showAccessDenied(e);
            }
        }
    }

    private void searchFilter(User admin) throws SQLException {
        InputHelper.header("SEARCH / FILTER REQUESTS");
        System.out.println("1. Filter by Status");
        System.out.println("2. Filter by Technician ID");
        System.out.println("3. Unassigned Requests Only");
        System.out.println("4. Search by Keyword (customer, device, brand, or problem)");
        System.out.println("5. Back");
        int choice = input.readMenuChoice("Enter your choice: ", 1, 5);
        List<RepairRequest> result;
        switch (choice) {
            case 1:
                String status = input.chooseFromList("Show which status?", STATUSES);
                result = requestDao.find(admin, status, null, false, null);
                break;
            case 2:
                showTechnicians(admin, null);
                int techId = input.readInt("Enter Technician ID: ");
                result = requestDao.find(admin, null, techId, false, null);
                break;
            case 3:
                result = requestDao.find(admin, null, null, true, null);
                break;
            case 4:
                String keyword = input.readNonEmpty("Enter keyword: ");
                result = requestDao.find(admin, null, null, false, keyword);
                break;
            default:
                return;
        }
        System.out.println();
        RequestView.printForAdmin(result);
    }

    // Lists technicians with their Technician ID (the ID used on repair requests).
    private void showTechnicians(User admin, String status) throws SQLException {
        System.out.println(status == null ? "Technicians:" : "Active technicians:");
        for (User t : userDao.findAll(admin, User.ROLE_TECHNICIAN, status, null)) {
            System.out.println("  Technician ID " + t.technicianId() + " - " + t.fullName()
                    + " (" + t.specialization() + ")");
        }
        System.out.println();
    }

    private void viewDetails(User admin) throws SQLException {
        int id = input.readInt("Enter Request ID: ");
        RepairRequest request = requestDao.findById(admin, id);
        if (request == null) {
            System.out.println("Request not found.");
            return;
        }
        RequestView.printDetails(request);
    }

    // Shows the active technicians, then assigns (or reassigns) the chosen one.
    private void assign(User admin) throws SQLException {
        InputHelper.header("ASSIGN / REASSIGN TECHNICIAN");
        int id = input.readInt("Enter Request ID: ");
        RepairRequest request = requestDao.findById(admin, id);
        if (request == null) {
            System.out.println("Request not found.");
            return;
        }
        RequestView.printDetails(request);
        if (!request.isOpen()) {
            System.out.println();
            System.out.println("This request is " + request.status() + ", so a technician cannot be assigned.");
            return;
        }
        List<User> technicians = userDao.findAll(admin, User.ROLE_TECHNICIAN, User.STATUS_ACTIVE, null);
        if (technicians.isEmpty()) {
            System.out.println();
            System.out.println("There are no active technicians. Create one in Manage Users first.");
            return;
        }
        System.out.println();
        showTechnicians(admin, User.STATUS_ACTIVE);
        int techId = input.readInt("Enter Technician ID (0 to go back): ");
        if (techId == 0) {
            return;
        }
        AssignResult result = requestDao.assign(admin, id, techId);
        switch (result) {
            case ASSIGNED:
                System.out.println("Technician assigned to request " + id + ".");
                break;
            case REQUEST_NOT_FOUND:
                System.out.println("Request not found.");
                break;
            case REQUEST_CLOSED:
                System.out.println("This request is no longer open, so it cannot be assigned.");
                break;
            case INVALID_TECHNICIAN:
                System.out.println("That is not an active technician.");
                break;
            default:
                System.out.println("That technician already has this request.");
                break;
        }
    }

    private void cancel(User admin) throws SQLException {
        InputHelper.header("CANCEL REQUEST");
        int id = input.readInt("Enter Request ID: ");
        RepairRequest request = requestDao.findById(admin, id);
        if (request == null) {
            System.out.println("Request not found.");
            return;
        }
        if (!request.isOpen()) {
            System.out.println("Only Pending or Ongoing requests can be cancelled. Current status: " + request.status());
            return;
        }
        System.out.println("Customer: " + request.customerName() + " | Device: " + request.deviceType()
                + " - " + request.brandModel() + " | Status: " + request.status());
        if (!input.readConfirm("Cancel this request? (Y/N): ")) {
            System.out.println("Request was not cancelled.");
            return;
        }
        System.out.println(requestDao.cancelByAdmin(admin, id)
                ? "Request cancelled. The record is kept." : "The request could not be cancelled.");
    }

    // The only real delete in the system.
    private void remove(User admin) throws SQLException {
        InputHelper.header("REMOVE INVALID REQUEST");
        int id = input.readInt("Enter Request ID to remove: ");
        RepairRequest request = requestDao.findById(admin, id);
        if (request == null) {
            System.out.println("Request not found.");
            return;
        }
        System.out.println("Customer: " + request.customerName() + " | Device: " + request.deviceType()
                + " - " + request.brandModel() + " | Status: " + request.status());
        if (!input.readConfirm("Permanently remove this invalid request record? (Y/N): ")) {
            System.out.println("Request was not removed.");
            return;
        }
        System.out.println(requestDao.remove(admin, id)
                ? "Request record removed." : "The request could not be removed.");
    }
}
