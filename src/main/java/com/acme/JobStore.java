package com.acme;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

@Component
public class JobStore {

    private static final String PREFIX = "job:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    public JobStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // Create a new job and return its id
    public String create() {
        String jobId = UUID.randomUUID().toString();
        String key = PREFIX + jobId;

        redis.opsForHash().putAll(key, Map.of(
                "status", JobStatus.PENDING.name(),
                "progress", "0"));
        redis.expire(key, TTL);

        return jobId;
    }

    // Update status and progress of an existing job
    public void update(String jobId, JobStatus status, int progress) {
        String key = PREFIX + jobId;
        redis.opsForHash().putAll(key, Map.of(
                "status", status.name(),
                "progress", String.valueOf(progress)));
        redis.expire(key, TTL);   // restart the 1-hour countdown
    }

 // Mark a job as finished and store its result
    public void complete(String jobId, String result) {
        String key = PREFIX + jobId;
        redis.opsForHash().putAll(key, Map.of(
                "status", JobStatus.COMPLETED.name(),
                "progress", "100",
                "result", result));
        redis.expire(key, TTL);
    }
    
 // Mark a job as failed and store the reason
    public void fail(String jobId, String error) {
        String key = PREFIX + jobId;
        redis.opsForHash().putAll(key, Map.of(
                "status", JobStatus.FAILED.name(),
                "progress", "0",
                "error", error));
        redis.expire(key, TTL);
    }
    
  // Remove a job completely
    public void delete(String jobId) {
        redis.delete(PREFIX + jobId);
    }
    
 // Marks every job that is still PENDING or RUNNING as FAILED; returns how many it changed
    public int failUnfinishedJobs(String reason) {
        int count = 0;
        ScanOptions options = ScanOptions.scanOptions().match(PREFIX + "*").count(100).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Object status = redis.opsForHash().get(key, "status");
                if (JobStatus.PENDING.name().equals(status) || JobStatus.RUNNING.name().equals(status)) {
                    fail(key.substring(PREFIX.length()), reason);
                    count++;
                }
            }
        }
        return count;
    }
    
    // Read a job; returns null if it doesn't exist
    public Map<Object, Object> find(String jobId) {
        Map<Object, Object> job = redis.opsForHash().entries(PREFIX + jobId);
        return job.isEmpty() ? null : job;
    }
}