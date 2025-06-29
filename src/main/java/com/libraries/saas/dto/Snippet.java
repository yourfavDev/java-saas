package com.libraries.saas.dto;

import java.util.List;

public class Snippet {
    private String name;
    private String code;
    private List<String> dependencies;
    private String cron; // cron expression

    public Snippet() {}

    public Snippet(String name, String code, List<String> dependencies, String cron) {
        this.name = name;
        this.code = code;
        this.dependencies = dependencies;
        this.cron = cron;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }

    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
}
