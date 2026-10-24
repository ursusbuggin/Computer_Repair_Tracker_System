package com.scc.model;

/**
 * A login account (table user_account) together with its profile.
 * A CUSTOMER account points to a row of the customer table (customerId),
 * a TECHNICIAN account points to a row of the technician table (technicianId),
 * and an ADMIN account has neither (names are null, so the username is shown instead).
 * role:   CUSTOMER, TECHNICIAN, or ADMIN
 * status: PENDING (new account, waiting for approval), ACTIVE, or INACTIVE
 * specialization is only used by technicians.
 */
public record User(int userId, String firstName, String lastName, String contactNumber, String username,
                   String passwordHash, String role, String accountStatus, Integer customerId,
                   Integer technicianId, String specialization) {

    public static final String ROLE_CUSTOMER = "CUSTOMER";
    public static final String ROLE_TECHNICIAN = "TECHNICIAN";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    public boolean isCustomer() { return role.equals(ROLE_CUSTOMER); }
    public boolean isTechnician() { return role.equals(ROLE_TECHNICIAN); }
    public boolean isAdmin() { return role.equals(ROLE_ADMIN); }
    public boolean isActive() { return accountStatus.equals(STATUS_ACTIVE); }
    public boolean isPending() { return accountStatus.equals(STATUS_PENDING); }

    // The name shown on screens: "First Last", or the username for an admin.
    public String fullName() {
        return firstName == null ? username : firstName + " " + lastName;
    }

    // A friendly role name for screens.
    public String roleLabel() {
        switch (role) {
            case ROLE_ADMIN:
                return "Admin";
            case ROLE_TECHNICIAN:
                return "Technician";
            default:
                return "Customer";
        }
    }

    // The password hash is never printed.
    @Override
    public String toString() {
        return "User[" + userId + ", " + username + ", " + role + ", " + accountStatus + "]";
    }
}
