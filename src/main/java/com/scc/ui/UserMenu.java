package com.scc.ui;

import com.scc.dao.UserDao;
import com.scc.model.User;
import java.sql.SQLException;
import java.util.List;

/**
 * Admin screens for accounts: create, view, search, edit, approve pending accounts, and activate / deactivate.
 * Accounts are never deleted. Their status is changed instead.
 */
public class UserMenu {
    private final InputHelper input;
    private final UserDao userDao;
    private final AccountForm form;

    public UserMenu(InputHelper input, UserDao userDao) {
        this.input = input;
        this.userDao = userDao;
        this.form = new AccountForm(input, userDao);
    }

    public void run(User admin) {
        boolean back = false;
        while (!back) {
            InputHelper.header("MANAGE USERS");
            System.out.println("1. Add Account (Customer, Technician or Admin)");
            System.out.println("2. View Users");
            System.out.println("3. Search Users");
            System.out.println("4. Edit User");
            System.out.println("5. Approve Pending Accounts");
            System.out.println("6. Activate / Deactivate Account");
            System.out.println("7. Back");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 7);
            try {
                switch (choice) {
                    case 1:
                        createAccount(admin);
                        break;
                    case 2:
                        viewUsers(admin);
                        break;
                    case 3:
                        String keyword = input.readNonEmpty("Search name or username: ");
                        showUsers(userDao.findAll(admin, null, null, keyword));
                        break;
                    case 4:
                        editUser(admin);
                        break;
                    case 5:
                        approvePending(admin);
                        break;
                    case 6:
                        changeStatus(admin);
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

    // Admin creates a Customer, Technician, or Admin account. It is ACTIVE immediately.
    private void createAccount(User admin) throws SQLException {
        InputHelper.header("ADD ACCOUNT");
        System.out.println("1. Customer");
        System.out.println("2. Technician");
        System.out.println("3. Admin");
        System.out.println("4. Back");
        int choice = input.readMenuChoice("Enter your choice: ", 1, 4);
        if (choice == 4) {
            return;
        }
        String role = choice == 1 ? User.ROLE_CUSTOMER : (choice == 2 ? User.ROLE_TECHNICIAN : User.ROLE_ADMIN);
        String firstName = null;
        String lastName = null;
        String contact = null;
        String specialization = null;
        if (!role.equals(User.ROLE_ADMIN)) { // an admin account only needs a username and password
            firstName = input.readNonEmpty("First Name: ");
            lastName = input.readNonEmpty("Last Name: ");
            contact = form.readContact("Contact Number: ");
        }
        if (role.equals(User.ROLE_TECHNICIAN)) {
            specialization = input.readNonEmpty("Specialization (example: Laptop Repair): ");
        }
        String username = form.readNewUsername();
        String password = form.readNewPassword();
        int newId = userDao.createAccount(admin, role, firstName, lastName, contact, specialization,
                username, password);
        System.out.println("Account created successfully (status ACTIVE). User ID: " + newId);
    }

    private void viewUsers(User admin) throws SQLException {
        System.out.println("1. All Users");
        System.out.println("2. Customers");
        System.out.println("3. Technicians");
        System.out.println("4. Admins");
        int choice = input.readMenuChoice("Show which users? ", 1, 4);
        String role = choice == 1 ? null
                : (choice == 2 ? User.ROLE_CUSTOMER : (choice == 3 ? User.ROLE_TECHNICIAN : User.ROLE_ADMIN));
        showUsers(userDao.findAll(admin, role, null, null));
    }

    private void showUsers(List<User> users) {
        System.out.println();
        if (users.isEmpty()) {
            System.out.println("No accounts found.");
            return;
        }
        System.out.println(String.format("%-5s %-14s %-22s %-12s %-9s %s",
                "ID", "Username", "Name", "Role", "Status", "Contact / Specialization"));
        InputHelper.printDivider();
        for (User u : users) { // password hashes are never shown
            String details = u.isAdmin() ? "-"
                    : (u.isTechnician() ? u.specialization() + " | " + u.contactNumber() : u.contactNumber());
            System.out.println(String.format("%-5d %-14s %-22s %-12s %-9s %s",
                    u.userId(), u.username(), InputHelper.cut(u.fullName(), 22), u.roleLabel(),
                    u.accountStatus(), details));
        }
        InputHelper.printDivider();
        System.out.println("Accounts shown: " + users.size());
    }

    // Edits Customer and Technician accounts (admins manage themselves).
    private void editUser(User admin) throws SQLException {
        InputHelper.header("EDIT USER");
        int userId = input.readInt("Enter User ID: ");
        User target = userDao.findById(userId);
        if (target == null || target.isAdmin()) {
            System.out.println("Customer or technician account not found.");
            return;
        }
        boolean done = false;
        while (!done) {
            target = userDao.findById(userId); // reload to show the saved values
            System.out.println();
            System.out.println("Editing: " + target.username() + " (" + target.roleLabel() + ")");
            System.out.println("Name: " + target.fullName());
            System.out.println("Contact: " + target.contactNumber());
            if (target.isTechnician()) {
                System.out.println("Specialization: " + target.specialization());
            }
            System.out.println("1. Change First Name");
            System.out.println("2. Change Last Name");
            System.out.println("3. Change Contact Number");
            System.out.println("4. Change Specialization (technicians only)");
            System.out.println("5. Reset Password");
            System.out.println("6. Done");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 6);
            if (choice == 4 && !target.isTechnician()) {
                System.out.println("This option is only for technician accounts.");
                continue;
            }
            switch (choice) {
                case 1:
                    userDao.updateField(admin, target, "first_name", input.readNonEmpty("New First Name: "));
                    System.out.println("Saved: first name updated.");
                    break;
                case 2:
                    userDao.updateField(admin, target, "last_name", input.readNonEmpty("New Last Name: "));
                    System.out.println("Saved: last name updated.");
                    break;
                case 3:
                    userDao.updateField(admin, target, "contact_number", form.readContact("New Contact Number: "));
                    System.out.println("Saved: contact number updated.");
                    break;
                case 4:
                    userDao.updateField(admin, target, "specialization", input.readNonEmpty("New Specialization: "));
                    System.out.println("Saved: specialization updated.");
                    break;
                case 5:
                    userDao.resetPassword(admin, target, form.readNewPassword());
                    System.out.println("Saved: password updated.");
                    break;
                default:
                    done = true;
                    break;
            }
        }
    }

    // Accounts that registered themselves are PENDING. The admin approves or rejects them here.
    private void approvePending(User admin) throws SQLException {
        InputHelper.header("PENDING ACCOUNTS");
        List<User> pending = userDao.findAll(admin, null, User.STATUS_PENDING, null);
        if (pending.isEmpty()) {
            System.out.println("There are no pending accounts.");
            return;
        }
        showUsers(pending);
        int userId = input.readInt("Enter the User ID to review (0 to go back): ");
        if (userId == 0) {
            return;
        }
        User target = userDao.findById(userId);
        if (target == null || !target.isPending()) {
            System.out.println("No pending account found with that ID.");
            return;
        }
        System.out.println("Account: " + target.username() + " (" + target.fullName() + ", " + target.roleLabel() + ")");
        System.out.println("1. Approve (status becomes ACTIVE)");
        System.out.println("2. Reject (status becomes INACTIVE)");
        System.out.println("3. Back");
        int choice = input.readMenuChoice("Enter your choice: ", 1, 3);
        if (choice == 1) {
            userDao.setStatus(admin, target, User.STATUS_ACTIVE, "User account approved: " + target.username());
            System.out.println("Account approved. The user can now log in.");
        } else if (choice == 2) {
            userDao.setStatus(admin, target, User.STATUS_INACTIVE, "User account rejected: " + target.username());
            System.out.println("Account rejected. The record is kept.");
        }
    }

    // ACTIVE <-> INACTIVE. Pending accounts must be approved or rejected instead.
    private void changeStatus(User admin) throws SQLException {
        InputHelper.header("ACTIVATE / DEACTIVATE ACCOUNT");
        int userId = input.readInt("Enter User ID: ");
        User target = userDao.findById(userId);
        if (target == null) {
            System.out.println("Account not found.");
            return;
        }
        System.out.println("Account: " + target.username() + " (" + target.fullName() + ", " + target.roleLabel() + ")");
        System.out.println("Current status: " + target.accountStatus());
        if (target.isPending()) {
            System.out.println("This account is PENDING. Use \"Approve Pending Accounts\" instead.");
            return;
        }
        boolean deactivate = target.isActive();
        String question = deactivate ? "Deactivate this account? (Y/N): " : "Activate this account? (Y/N): ";
        if (!input.readConfirm(question)) {
            System.out.println("No changes were made.");
            return;
        }
        String newStatus = deactivate ? User.STATUS_INACTIVE : User.STATUS_ACTIVE;
        String logText = (deactivate ? "User account deactivated: " : "User account activated: ") + target.username();
        if (userDao.setStatus(admin, target, newStatus, logText)) {
            System.out.println(deactivate
                    ? "Account deactivated. The record is kept, but the user can no longer log in."
                    : "Account activated.");
        } else {
            System.out.println("Cannot deactivate this account. The system needs at least one active admin.");
        }
    }
}
