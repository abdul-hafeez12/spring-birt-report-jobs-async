package com.acme;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    private final JobStore jobStore;
    private final ReportService reportService;
    private final int delaySeconds;

    public JobWorker(JobStore jobStore,
                     ReportService reportService,
                     @Value("${job.simulated-delay-seconds:70}") int delaySeconds) {
        this.jobStore = jobStore;
        this.reportService = reportService;
        this.delaySeconds = delaySeconds;
    }

    @Async("jobExecutor")
    public void run(String jobId, String design, ReportFormat format, Map<String, Object> parameters) {
        try {
            log.info("Job {} started: design={}, format={}", jobId, design, format);
            jobStore.update(jobId, JobStatus.RUNNING, 0);

            simulateSlowWork(jobId);

            String filePath = reportService.generate(jobId, design, format, parameters);

            jobStore.complete(jobId, filePath);
            log.info("Job {} completed: {}", jobId, filePath);

        } catch (Exception e) {
            log.error("Job {} failed", jobId, e);
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            jobStore.fail(jobId, reason);
        }
    }

    // Pretend the job is slow: 10 steps, progress goes up to 90%
    private void simulateSlowWork(String jobId) throws InterruptedException {
        int steps = 10;
        for (int i = 1; i <= steps; i++) {
            Thread.sleep(delaySeconds * 1000L / steps);
            jobStore.update(jobId, JobStatus.RUNNING, i * 90 / steps);
        }
    }
}