package com.acme;

public enum JobStatus {
    PENDING,    // job created, not started yet
    RUNNING,    // work in progress
    COMPLETED,  // finished successfully
    FAILED      // finished with an error
}