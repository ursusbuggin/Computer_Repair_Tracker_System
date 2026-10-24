package com.scc.dao;

import com.scc.model.User;

/**
 * Role check used inside the DAO methods, so permissions do not depend only on hidden menu options.
 * It throws a SecurityException when the user's role is not allowed.
 */
public class Permissions {
    public static void require(User actor, String... allowedRoles) {
        if (actor != null) {
            for (String role : allowedRoles) {
                if (actor.role().equals(role)) {
                    return;
                }
            }
        }
        throw new SecurityException("You are not allowed to do this action.");
    }
}