package com.scc.ui;

import java.sql.SQLException;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * Reusable console input and display helpers. Every read method keeps asking until the
 * user types something valid, so bad input never crashes the program.
 */
public class InputHelper {
    private final Scanner scanner;

    public InputHelper(Scanner scanner) {
        this.scanner = scanner;
    }

    // Reads one line of text and removes extra spaces.
    public String readLine(String prompt) {
        System.out.print(prompt);
        try {
            return scanner.nextLine().trim();
        } catch (NoSuchElementException e) {
            // No more input (for example, the input stream was closed).
            System.out.println();
            System.out.println("No more input. Exiting program.");
            System.exit(0);
            return "";
        }
    }

    public String readNonEmpty(String prompt) {
        String value = readLine(prompt);
        while (value.isEmpty()) {
            System.out.println("This field cannot be empty. Please try again.");
            value = readLine(prompt);
        }
        return value;
    }

    public int readInt(String prompt) {
        while (true) {
            String text = readLine(prompt);
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a whole number.");
            }
        }
    }

    // Keeps asking until the number is between min and max (inclusive).
    public int readMenuChoice(String prompt, int min, int max) {
        int choice = readInt(prompt);
        while (choice < min || choice > max) {
            System.out.println("Invalid choice. Please enter a number from " + min + " to " + max + ".");
            choice = readInt(prompt);
        }
        return choice;
    }

    public boolean readConfirm(String prompt) {
        while (true) {
            String answer = readLine(prompt);
            if (answer.equalsIgnoreCase("Y") || answer.equalsIgnoreCase("YES")) {
                return true;
            } else if (answer.equalsIgnoreCase("N") || answer.equalsIgnoreCase("NO")) {
                return false;
            }
            System.out.println("Please type Y or N.");
        }
    }

    // Shows a numbered list and returns the option the user picked.
    public String chooseFromList(String title, String[] options) {
        System.out.println(title);
        for (int i = 0; i < options.length; i++) {
            System.out.println((i + 1) + ". " + options[i]);
        }
        int choice = readMenuChoice("Enter your choice: ", 1, options.length);
        return options[choice - 1];
    }

    // ---------- Display ----------

    public static void printLine() {
        System.out.println("============================================================");
    }

    public static void printDivider() {
        System.out.println("------------------------------------------------------------");
    }

    public static void header(String title) {
        System.out.println();
        printLine();
        System.out.println(title);
        printLine();
    }

    // Short message for ordinary users. Technical details only appear with -Drepairshop.debug=true
    public static void showDbError(SQLException e) {
        System.out.println("A database problem occurred. Please try again or contact the administrator.");
        if (Boolean.getBoolean("repairshop.debug")) {
            e.printStackTrace();
        }
    }

    public static void showAccessDenied(SecurityException e) {
        System.out.println("Access denied. " + e.getMessage());
    }

    public static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + ".";
    }
}
