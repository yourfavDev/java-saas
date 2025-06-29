package com.libraries.saas.dto;

import java.time.Instant;

public class RunRecord {
    private String jobId;
    private String snippetName;
    private Instant timestamp;
    private String status;
    private String output;
    private String error;

    public RunRecord() {}

    public RunRecord(String jobId, String snippetName, Instant timestamp, String status, String output, String error) {
        this.jobId = jobId;
        this.snippetName = snippetName;
        this.timestamp = timestamp;
        this.status = status;
        this.output = output;
        this.error = error;
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getSnippetName() { return snippetName; }
    public void setSnippetName(String snippetName) { this.snippetName = snippetName; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
