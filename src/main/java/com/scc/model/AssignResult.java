package com.scc.model;

/** The result of an admin assigning or reassigning a technician. */
public enum AssignResult {
    ASSIGNED,
    REQUEST_NOT_FOUND,
    REQUEST_CLOSED,      // Completed or Cancelled requests cannot be assigned
    INVALID_TECHNICIAN,  // not found, not a technician, or not ACTIVE
    ALREADY_ASSIGNED     // this technician already has the request
}
