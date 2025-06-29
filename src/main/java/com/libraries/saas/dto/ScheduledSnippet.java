package com.libraries.saas.dto;

import java.util.List;

public class ScheduledSnippet {
    private Snippet snippet;
    private List<RunRecord> history;

    public ScheduledSnippet() {}

    public ScheduledSnippet(Snippet snippet, List<RunRecord> history) {
        this.snippet = snippet;
        this.history = history;
    }

    public Snippet getSnippet() { return snippet; }
    public void setSnippet(Snippet snippet) { this.snippet = snippet; }

    public List<RunRecord> getHistory() { return history; }
    public void setHistory(List<RunRecord> history) { this.history = history; }
}

