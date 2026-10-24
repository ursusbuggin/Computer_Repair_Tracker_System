package com.scc.ui;

import com.scc.dao.UserDao;
import com.scc.model.User;
import java.sql.SQLException;

/** The Admin dashboard: repair requests, users, status report, and activity logs. */
public class AdminMenu {
    private final InputHelper input;
    private final UserDao userDao;
    private final RequestMenu requestMenu;
    private final UserMenu userMenu;
    private final ReportMenu reportMenu;
    private final LogMenu logMenu;

    public AdminMenu(InputHelper input, UserDao userDao) {
        this.input = input;
        this.userDao = userDao;
        this.requestMenu = new RequestMenu(input, userDao);
        this.userMenu = new UserMenu(input, userDao);
        this.reportMenu = new ReportMenu(input);
        this.logMenu = new LogMenu(input);
    }

    public void run(User user) {
        boolean loggedIn = true;
        while (loggedIn) {
            InputHelper.header("ADMIN DASHBOARD");
            System.out.println("Welcome, " + user.fullName() + "!");
            showPendingNotice(user);
            System.out.println();
            System.out.println("1. Manage Repair Requests");
            System.out.println("2. Manage Users");
            System.out.println("3. Repair Status Report");
            System.out.println("4. Activity Logs");
            System.out.println("5. Logout");
            int choice = input.readMenuChoice("Enter your choice: ", 1, 5);

            try {
                switch (choice) {
                    case 1:
                        requestMenu.run(user);
                        break;
                    case 2:
                        userMenu.run(user);
                        break;
                    case 3:
                        reportMenu.viewReport(user);
                        break;
                    case 4:
                        logMenu.run(user);
                        break;
                    default:
                        loggedIn = false;
                        break;
                }
            } catch (SecurityException e) {
                InputHelper.showAccessDenied(e);
            }
        }
    }

    // Reminds the admin when new accounts are waiting for approval.
    private void showPendingNotice(User admin) {
        try {
            int pending = userDao.countPendingAccounts(admin);
            if (pending > 0) {
                System.out.println("Notice: " + pending + " account(s) are waiting for your approval.");
            }
        } catch (SQLException e) {
            InputHelper.showDbError(e);
        }
    }
}
