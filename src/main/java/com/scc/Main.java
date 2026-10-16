package com.scc;

import com.scc.dao.DatabaseConnection;
import com.scc.ui.MainMenu;

public class Main {
    public static void main(String[] args) {
        DatabaseConnection.initialize();
        new MainMenu().start();
    }
}