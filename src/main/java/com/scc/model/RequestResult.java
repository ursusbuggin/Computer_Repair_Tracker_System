package com.scc.model;

/** The result of a technician changing the status of an assigned repair job. */
public enum RequestResult {
    CHANGED,            // the change was saved
    NOT_FOUND,          // no job with that ID is assigned to this technician
    INVALID_CHANGE,     // the status rules do not allow this change
    DIAGNOSIS_REQUIRED, // a job cannot be Completed before a diagnosis is recorded
    ALREADY_CHANGED     // someone else changed the request first
}
