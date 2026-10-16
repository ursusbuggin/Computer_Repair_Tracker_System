package com.scc.model;

public record User(int id, String username, String password, String roles, String status) {
}