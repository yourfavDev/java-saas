package com.libraries.saas.dto;

import java.time.Instant;

public record DashboardMetrics(
        long itemCount,
        long tableSizeKbytes,
        Instant lastProcessedTimestamp,
        int errors
) {}
