package com.acme;

import java.util.Map;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.task.TaskRejectedException;

@RestController
public class JobController {

	private final JobStore jobStore;
	private final JobWorker jobWorker;
	private final ReportDesigns designs;
	
	public JobController(JobStore jobStore, JobWorker jobWorker, ReportDesigns designs) {
	    this.jobStore = jobStore;
	    this.jobWorker = jobWorker;
	    this.designs = designs;
	}

	@PostMapping("/jobs")
	public ResponseEntity<?> createJob(@RequestBody JobRequest request) {
	    ReportFormat format;
	    try {
	        designs.resolve(request.design());          // checks the name and that the file exists
	        format = ReportFormat.from(request.format());
	    } catch (IllegalArgumentException e) {
	        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
	    }

	    String jobId = jobStore.create();
	    try {
	        jobWorker.run(jobId, request.design(), format, request.parameters());
	    } catch (TaskRejectedException e) {
	        jobStore.delete(jobId);                       // nobody will run it, so don't leave it behind
	        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
	                .header(HttpHeaders.RETRY_AFTER, "30")
	                .body(Map.of("error", "Server is busy, please try again later"));
	    }
	    return ResponseEntity.accepted().body(Map.of("jobId", jobId));
	}
	
    @GetMapping("/jobs/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable String jobId) {
        Map<Object, Object> job = jobStore.find(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();                       // 404: no such job
        }
        if (!JobStatus.COMPLETED.name().equals(job.get("status"))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();      // 409: not finished yet
        }

        Path file = Path.of((String) job.get("result"));
        if (!Files.exists(file)) {
            return ResponseEntity.status(HttpStatus.GONE).build();          // 410: file was deleted
        }
        
        String fileName = file.getFileName().toString();
        ReportFormat format = ReportFormat.from(fileName.substring(fileName.lastIndexOf('.') + 1));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(format.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(new FileSystemResource(file));
//
//        return ResponseEntity.ok()
//                .contentType(MediaType.APPLICATION_PDF)
//                .header(HttpHeaders.CONTENT_DISPOSITION,
//                        "attachment; filename=\"" + file.getFileName() + "\"")
//                .body(new FileSystemResource(file));
    }
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJob(@PathVariable String jobId) {
        Map<Object, Object> job = jobStore.find(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(job);
    }
}