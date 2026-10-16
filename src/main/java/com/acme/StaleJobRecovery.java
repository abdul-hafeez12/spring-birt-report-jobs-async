package com.acme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class StaleJobRecovery {

    private static final Logger log = LoggerFactory.getLogger(StaleJobRecovery.class);

    private final JobStore jobStore;

    public StaleJobRecovery(JobStore jobStore) {
        this.jobStore = jobStore;
    }

    @PostConstruct
    public void markLeftoverJobsAsFailed() {
        try {
            int count = jobStore.failUnfinishedJobs("Server restarted before the job finished");
            log.info("Startup check: marked {} unfinished job(s) as FAILED", count);
        } catch (Exception e) {
            log.warn("Startup check could not run (is Redis reachable?)", e);
        }
    }
}