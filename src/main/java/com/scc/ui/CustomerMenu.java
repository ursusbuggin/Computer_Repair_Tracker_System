package com.scc.ui;

import com.scc.dao.PasswordHasher;
import com.scc.dao.RepairRequestDao;
import com.scc.dao.UserDao;
import com.scc.model.RepairRequest;
import com.scc.model.User;
import java.sql.SQLException;
import java.util.List;

/** The Customer dashboard: customers only see and change their own requests and profile. */
public class CustomerMenu {
    private static final String[] DEVICE_TYPES = {
            "Laptop", "Desktop Computer", "Smartphone", "Tablet", "Printer", "Other"
    };

    private final InputHelper input;
    private final UserDao userDao;
    private final RepairRequestDao requestDao = new RepairRequestDao();
    private final AccountForm form;

    public CustomerMenu(InputHelper input, UserDao userDao) {
        this.input = input;
        this.userDao = userDao;
        this.form = new AccountForm(input, userDao);
    }

    public void run(User customer) {
        User user = customer;
        boolean loggedIn = true;
        while (loggedIn) {
            InputHelper.header("CUSTOMER DASHBOARD");
            System.out.println("Welcome, " + user.fullName() + "!");
            System.out.println();
            System.out.println("1. Submit Repair Request");
            System.out.println("2. My Repair Requests");
            System.out.println("3. Repair History");
            System.out.println("4. Edit My Profile");
            System.out.println("5. Logout");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 5);

            try {
                switch (choice) {
                    case 1:
                        submitRequest(user);
                        break;
                    case 2:
                        myRequests(user);
                        break;
                    case 3:
                        InputHelper.header("REPAIR HISTORY");
                        RequestView.printForCustomer(requestDao.findAllByCustomer(user));
                        break;
                    case 4:
                        user = editProfile(user);
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

    // The customer describes the computer problem. The new request is Pending until the admin assigns a technician.
    private void submitRequest(User user) throws SQLException {
        InputHelper.header("SUBMIT REPAIR REQUEST");
        String deviceType = input.chooseFromList("Device type:", DEVICE_TYPES);
        if (deviceType.equals("Other")) {
            deviceType = input.readNonEmpty("Please type the device type: ");
        }
        String brandModel = input.readNonEmpty("Brand / Model (example: Lenovo IdeaPad 3): ");
        String problem = input.readNonEmpty("Describe the problem: ");

        System.out.println();
        System.out.println("Device: " + deviceType);
        System.out.println("Brand / Model: " + brandModel);
        System.out.println("Problem: " + problem);
        System.out.println();
        if (!input.readConfirm("Submit this repair request? (Y/N): ")) {
            System.out.println("Repair request was not submitted.");
            return;
        }
        int requestId = requestDao.create(user, deviceType, brandModel, problem);
        System.out.println();
        System.out.println("Repair request submitted!");
        System.out.println("Request ID: " + requestId);
        System.out.println("Status: " + RepairRequest.STATUS_PENDING);
    }

    // Lists the open requests (status and diagnosis) and offers to cancel a Pending one.
    private void myRequests(User user) throws SQLException {
        InputHelper.header("MY REPAIR REQUESTS");
        List<RepairRequest> list = requestDao.findOpenByCustomer(user);
        RequestView.printForCustomer(list);
        if (list.isEmpty() || !input.readConfirm("Do you want to cancel a request? (Y/N): ")) {
            return;
        }
        int id = input.readInt("Enter Request ID to cancel: ");
        RepairRequest request = requestDao.findCustomerRequest(user, id); // only the customer's own requests
        if (request == null) {
            System.out.println("Request not found.");
            return;
        }
        if (!request.status().equals(RepairRequest.STATUS_PENDING)) {
            System.out.println("This request can no longer be cancelled. Current status: " + request.status());
            return;
        }
        System.out.println("Device: " + request.deviceType() + " - " + request.brandModel());
        if (!input.readConfirm("Cancel this request? (Y/N): ")) {
            System.out.println("Request was not cancelled.");
            return;
        }
        if (requestDao.cancelOwn(user, id)) {
            System.out.println("Request cancelled. The record is kept in your history.");
        } else {
            System.out.println("The request could not be cancelled. Its status may have changed.");
        }
    }

    // A customer can change first name, last name, contact number, and password (not role or status).
    private User editProfile(User start) throws SQLException {
        User user = start;
        boolean back = false;
        while (!back) {
            InputHelper.header("MY PROFILE");
            System.out.println("Username: " + user.username());
            System.out.println("Name: " + user.fullName());
            System.out.println("Contact: " + user.contactNumber());
            System.out.println();
            System.out.println("1. Change First Name");
            System.out.println("2. Change Last Name");
            System.out.println("3. Change Contact Number");
            System.out.println("4. Change Password");
            System.out.println("5. Back");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 5);
            switch (choice) {
                case 1:
                    userDao.updateOwnField(user, "first_name", input.readNonEmpty("New First Name: "));
                    System.out.println("Saved: first name updated.");
                    break;
                case 2:
                    userDao.updateOwnField(user, "last_name", input.readNonEmpty("New Last Name: "));
                    System.out.println("Saved: last name updated.");
                    break;
                case 3:
                    userDao.updateOwnField(user, "contact_number", form.readContact("New Contact Number: "));
                    System.out.println("Saved: contact number updated.");
                    break;
                case 4:
                    String current = input.readNonEmpty("Current Password: ");
                    if (!PasswordHasher.verify(current, user.passwordHash())) {
                        System.out.println("Wrong current password. Password not changed.");
                    } else {
                        userDao.updateOwnField(user, "password_hash", PasswordHasher.hash(form.readNewPassword()));
                        System.out.println("Saved: password updated.");
                    }
                    break;
                default:
                    back = true;
                    break;
            }
            user = userDao.findById(user.userId()); // reload so the screen shows the saved values
        }
        return user;
    }
}
