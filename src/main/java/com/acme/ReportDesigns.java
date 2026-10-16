package com.acme;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReportDesigns {

    private static final Logger log = LoggerFactory.getLogger(ReportDesigns.class);
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_-]{1,60}");

    private final Path dir;

    public ReportDesigns(@Value("${report.designs-dir:report-designs}") String dir) {
        this.dir = Path.of(dir).toAbsolutePath();
        log.info("Report designs folder: {}", this.dir);
    }

    // Turns a design name like "products_report_filtered" into the file in the folder
    public Path resolve(String design) {
        if (design == null || !VALID_NAME.matcher(design).matches()) {
            throw new IllegalArgumentException(
                    "Invalid design name. Use only letters, digits, - and _ (max 60 characters)");
        }
        Path file = dir.resolve(design + ".rptdesign");
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Design '" + design + "' not found");
        }
        return file;
    }
}