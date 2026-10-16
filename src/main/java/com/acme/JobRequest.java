package com.acme;

import java.util.Map;

public record JobRequest(
        String design,
        String format,
        Map<String, Object> parameters) {
}