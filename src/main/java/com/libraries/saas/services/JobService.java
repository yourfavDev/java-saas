package com.libraries.saas.services;

import com.libraries.saas.dto.CodeRequest;
import com.libraries.saas.dto.StatusResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
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
    private final ExecutorService jobExecutor;
    private final Map<String, Future<StatusResponse>> jobs = new ConcurrentHashMap<>();

    @Autowired
    public JobService(ExecutorService jobExecutor) { this.jobExecutor = jobExecutor; }

    /* ------------------------------------------------------------------ */
    public String submitJob(CodeRequest req) {
        String id = UUID.randomUUID().toString();
        jobs.put(id, jobExecutor.submit(() -> executeInDocker(req, id)));
        return id;
    }

    public StatusResponse getJobStatus(String id) {
        Future<StatusResponse> fut = jobs.get(id);
        if (fut == null)          return new StatusResponse("not_found",  null, "Job ID not found");
        if (!fut.isDone())        return new StatusResponse("job_pending", null, null);
        try { return fut.get(); } catch (Exception e) {
            return new StatusResponse("error", null, e.getMessage());
        } finally { jobs.remove(id); }
    }

    /* ---------------- docker runner ---------------------------------- */
    private StatusResponse executeInDocker(CodeRequest req, String id) {

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

            if (!proc.waitFor(120, TimeUnit.SECONDS)) {
                new ProcessBuilder("docker","kill",container).start().waitFor();
                proc.destroyForcibly();
                return new StatusResponse("error", null, "Execution timed out");
            }

            String out = new String(proc.getInputStream().readAllBytes());
            return new StatusResponse("success", "<pre>" + out + "</pre>", null);

        } catch (Exception e) {
            e.printStackTrace();
            return new StatusResponse("error", null, e.getMessage());
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
}
