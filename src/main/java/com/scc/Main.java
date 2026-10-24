package com.scc;

import com.scc.dao.DatabaseInitializer;
import com.scc.dao.UserDao;
import com.scc.ui.Authentication;
import com.scc.ui.InputHelper;
import java.util.Scanner;

/**
 * Repair Shop System - console application (entry point).
 * Main only starts the program: Initialize Database -> create the shared DAO and input
 * -> hand control to Authentication.start(). Login, registration, and role routing live there.
 */
public class Main {
    public static void main(String[] args) {
        // 1. Create the tables (and the default admin / technician accounts) if they do not exist yet.
        if (!DatabaseInitializer.initialize()) {
            return;
        }

        // 2. Create the shared objects once (one Scanner, one UserDao for the whole program).
        Scanner scanner = new Scanner(System.in);
        InputHelper input = new InputHelper(scanner);
        UserDao userDao = new UserDao();

        // 3. Hand over to the login / register screen.
        new Authentication(input, userDao).start();
        scanner.close();
    }
}
