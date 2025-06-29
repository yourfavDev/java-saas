package com.libraries.saas.dto;

import java.util.List;

/**
 * DTO representing the code submission payload for execution.
 * `dependencies` uses the canonical Maven coordinate format:
 *     groupId:artifactId:version
 */
public class CodeRequest {

    private String code;
    private List<String> dependencies;   // NEW

    public CodeRequest() { }

    public CodeRequest(String code, List<String> dependencies) {
        this.code = code;
        this.dependencies = dependencies;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
}
