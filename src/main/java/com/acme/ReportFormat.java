package com.acme;

public enum ReportFormat {

    PDF("pdf", "application/pdf"),
    DOC("doc", "application/msword"),
    XLS("xls", "application/vnd.ms-excel");

    private final String extension;
    private final String contentType;

    ReportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    // Turns the client's text into a format; no text means PDF
    public static ReportFormat from(String text) {
        if (text == null || text.isBlank()) {
            return PDF;
        }
        for (ReportFormat format : values()) {
            if (format.extension.equalsIgnoreCase(text.trim())) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown format '" + text + "'. Allowed: pdf, doc, xls");
    }
}