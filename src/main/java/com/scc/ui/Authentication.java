package com.scc.ui;

import com.scc.dao.UserDao;
import com.scc.model.User;
import java.sql.SQLException;

/**
 * Handles login, registration, and role routing (1. Login  2. Register  3. Exit).
 * After a successful login it opens the dashboard that matches the user's role.
 * The UserDao and the input (Scanner) are created once in Main and shared with every menu.
 * Only customers can register here. Technician and admin accounts are created by an admin.
 */
public class Authentication {
    private final InputHelper input;
    private final UserDao userDao;
    private final AccountForm form;

    public Authentication(InputHelper input, UserDao userDao) {
        this.input = input;
        this.userDao = userDao;
        this.form = new AccountForm(input, userDao);
    }

    public void start() {
        boolean running = true;
        while (running) {
            InputHelper.header("REPAIR SHOP SYSTEM");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Exit");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 3);

            try {
                switch (choice) {
                    case 1:
                        login();
                        break;
                    case 2:
                        register();
                        break;
                    default:
                        running = false;
                        break;
                }
            } catch (SQLException e) {
                InputHelper.showDbError(e);
            }
        }
        System.out.println("Thank you for using the Repair Shop System. Goodbye!");
    }

    // ---------- Register ----------

    // The new customer account is PENDING until an admin approves it.
    private void register() throws SQLException {
        InputHelper.header("CUSTOMER REGISTRATION");
        String firstName = input.readNonEmpty("First Name: ");
        String lastName = input.readNonEmpty("Last Name: ");
        String contact = form.readContact("Contact Number: ");
        String username = form.readNewUsername();
        String password = form.readNewPassword();

        User created = userDao.register(firstName, lastName, contact, username, password, User.ROLE_CUSTOMER);
        System.out.println();
        System.out.println("Registration successful! Account status: " + created.accountStatus());
        System.out.println("An admin must approve your account before you can log in.");
    }

    // ---------- Login and logout ----------

    private void login() throws SQLException {
        InputHelper.header("LOGIN");
        String username = input.readNonEmpty("Username: ");
        String password = input.readNonEmpty("Password: ");

        User user = userDao.authenticate(username, password);
        // The same message is used for a wrong username and a wrong password.
        if (user == null) {
            System.out.println("Invalid username or password.");
            return;
        }
        if (user.isPending()) {
            System.out.println("Your account is still PENDING. An admin must approve it before you can log in.");
            return;
        }
        if (!user.isActive()) {
            System.out.println("This account is inactive. Please contact the administrator.");
            return;
        }

        userDao.recordLogin(user);
        System.out.println("Login successful.");
        openDashboard(user);

        userDao.recordLogout(user);
        System.out.println("You have been logged out.");
    }

    // Each role has its own dashboard with different features.
    private void openDashboard(User user) {
        if (user.isAdmin()) {
            new AdminMenu(input, userDao).run(user);
        } else if (user.isTechnician()) {
            new TechnicianMenu(input).run(user);
        } else {
            new CustomerMenu(input, userDao).run(user);
        }
    }
}
