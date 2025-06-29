package com.libraries.saas.services;

import com.libraries.saas.dto.CodeRequest;
import com.libraries.saas.dto.StatusResponse;
import com.libraries.saas.dto.RunRecord;
import com.libraries.saas.services.RunHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Service
public class JobService {

    /* ------------------------------------------------------------------ */
    private static class JobInfo {
        Future<?> future;
        final StringBuilder log = new StringBuilder();
        volatile String status = "job_pending";
        volatile String error;
        String container;
        Process process;
    }

    private final ExecutorService jobExecutor;
    private final RunHistoryService historyService;
    private final Map<String, JobInfo> jobs = new ConcurrentHashMap<>();
    private final Map<String, String> jobUsers = new ConcurrentHashMap<>();
    private final Map<String, String> jobNames = new ConcurrentHashMap<>();

    @Autowired
    public JobService(ExecutorService jobExecutor, RunHistoryService historyService) {
        this.jobExecutor = jobExecutor;
        this.historyService = historyService;
    }

    /* ------------------------------------------------------------------ */
    public String submitJob(CodeRequest req) {
        return submitJob(req, null, null);
    }

    public String submitJob(CodeRequest req, String userId, String snippetName) {
        String id = UUID.randomUUID().toString();
        JobInfo info = new JobInfo();
        jobs.put(id, info);
        if (userId != null) jobUsers.put(id, userId);
        if (snippetName != null) jobNames.put(id, snippetName);
        info.future = jobExecutor.submit(() -> executeInDocker(req, id, info));
        return id;
    }

    public StatusResponse getJobStatus(String id) {
        JobInfo info = jobs.get(id);
        if (info == null) {
            return new StatusResponse("not_found", null, "Job ID not found");
        }

        if (info.future.isDone() && ("running".equals(info.status) || "job_pending".equals(info.status))) {
            try { info.future.get(); } catch (Exception ignored) { }
        }

        String out = "<pre>" + escape(info.log.toString()) + "</pre>";

        if ("success".equals(info.status) || "error".equals(info.status)) {
            jobs.remove(id);
            String user = jobUsers.remove(id);
            String name = jobNames.remove(id);
            if (user != null) {
                RunRecord rec = historyService.newRecord(id, name, info.status, info.log.toString(), info.error);
                historyService.record(user, rec);
            }
        }

        return new StatusResponse(info.status, out, info.error);
    }

    public void cancelJob(String id) {
        JobInfo info = jobs.get(id);
        if (info == null) return;
        if (info.future != null) info.future.cancel(true);
        if (info.process != null && info.process.isAlive()) info.process.destroyForcibly();
        if (info.container != null) {
            try { new ProcessBuilder("docker","kill",info.container).start().waitFor(); } catch (Exception ignored) {}
        }
        info.status = "error";
        info.error = "Cancelled by user";
    }

    /* ---------------- docker runner ---------------------------------- */
    private void executeInDocker(CodeRequest req, String id, JobInfo info) {

        String container = "job-" + id;
        Path   tmpDir    = null;
        Process proc     = null;
        
        try {
            /* 1 ─ prepare tmp project */
            tmpDir = Files.createTempDirectory("job");
            Path srcDir = Files.createDirectories(tmpDir.resolve("src/main/java"));
            Path jobSrc = srcDir.resolve("Job.java");
            Files.writeString(jobSrc, req.getCode());

            String pom = pomXml(req.getDependencies());
            Files.writeString(tmpDir.resolve("pom.xml"), pom);

            /* 2 ─ detect fully-qualified main class */
            String fqcn = detectFqcn(req.getCode());

            /* 3 ─ run inside Docker */
            String[] cmd = {
                    "docker","run","--rm","--name",container,
                    "-v", tmpDir + ":/workspace",
                    "-w","/workspace",
                    "maven:3.9-eclipse-temurin-21",
                    "bash","-c","mvn -q package exec:java -Dexec.mainClass=" + fqcn
            };
            proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            info.status = "running";
            info.container = container;
            info.process = proc;

            Process finalProc = proc;
            Thread gobbler = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(finalProc.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        synchronized (info.log) { info.log.append(line).append('\n'); }
                    }
                } catch (IOException ignored) {}
            });
            gobbler.start();

            if (!proc.waitFor(120, TimeUnit.SECONDS)) {
                new ProcessBuilder("docker","kill",container).start().waitFor();
                proc.destroyForcibly();
                info.status = "error";
                info.error = "Execution timed out";
            } else {
                gobbler.join();
                if (proc.exitValue() == 0) {
                    info.status = "success";
                } else {
                    info.status = "error";
                    info.error = "Process exited with code " + proc.exitValue();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            info.status = "error";
            info.error = e.getMessage();
        } finally {
            if (proc != null && proc.isAlive()) proc.destroyForcibly();
            if (tmpDir != null) deleteDirectory(tmpDir.toFile());
        }
    }

    /* helper: derive package-aware main class name */
    private static String detectFqcn(String code) {
        var m = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;",
                Pattern.MULTILINE).matcher(code);
        return m.find() ? m.group(1) + ".Job" : "Job";
    }

    /* ---------------- pom generator ---------------------------------- */
    private String pomXml(List<String> deps) {

        boolean needBootBom = false;
        StringBuilder sb = new StringBuilder("""
        <project xmlns="http://maven.apache.org/POM/4.0.0"
                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                                     http://maven.apache.org/maven-v4_0_0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>tmp</groupId><artifactId>job</artifactId><version>1.0</version>
          <properties><maven.compiler.release>21</maven.compiler.release></properties>
          <dependencies>
        """);

        if (deps != null) {
            for (String d : deps) {
                String[] p = d.split(":");
                if (p.length < 2) continue;
                String g = p[0], a = p[1], v = p.length > 2 ? p[2] : null;
                if ((v == null || v.isBlank()) && "org.springframework.boot".equals(g)) needBootBom = true;
                sb.append("    <dependency><groupId>").append(g)
                        .append("</groupId><artifactId>").append(a).append("</artifactId>");
                if (v != null && !v.isBlank()) sb.append("<version>").append(v).append("</version>");
                sb.append("</dependency>\n");
            }
        }
        sb.append("  </dependencies>\n");

        if (needBootBom) {
            sb.append("""
          <dependencyManagement>
            <dependencies>
              <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>3.2.5</version>
                <type>pom</type>
                <scope>import</scope>
              </dependency>
            </dependencies>
          </dependencyManagement>
        """);
        }

        sb.append("""
          <build>
            <plugins>
              <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
              </plugin>
            </plugins>
          </build>
        </project>""");

        return sb.toString();
    }

    /* ---------------- util ------------------------------------------- */
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) for (File f : dir.listFiles()) deleteDirectory(f);
        dir.delete();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
