package com.libraries.saas.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libraries.auth.dto.UserInfoDto;
import com.libraries.auth.repository.UserRepository;
import com.libraries.saas.dto.CodeRequest;
import com.libraries.saas.dto.Snippet;
import com.libraries.saas.dto.RunRecord;
import com.libraries.saas.dto.ScheduledSnippet;
import com.libraries.saas.services.RunHistoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Service
public class SnippetService {
    private final S3Client s3;
    private final String bucket;
    private final ObjectMapper mapper = new ObjectMapper();
    private final JobService jobService;
    private final UserRepository userRepository;
    private final TaskScheduler scheduler;
    private final RunHistoryService historyService;
    private final List<ScheduledFuture<?>> tasks = new ArrayList<>();

    public SnippetService(S3Client s3,
                          @Value("${app.s3.bucket-name}") String bucket,
                          JobService jobService,
                          UserRepository userRepository,
                          TaskScheduler scheduler,
                          RunHistoryService historyService) {
        this.s3 = s3;
        this.bucket = bucket;
        this.jobService = jobService;
        this.userRepository = userRepository;
        this.scheduler = scheduler;
        this.historyService = historyService;
    }

    public void saveSnippet(String token, Snippet snippet) throws IOException {
        UserInfoDto info = userRepository.getUserInfo(token);
        if (info == null) throw new IllegalArgumentException("Invalid token");
        String key = info.id() + "/snippets/" + snippet.getName() + ".json";
        byte[] data = mapper.writeValueAsBytes(snippet);
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(data));
        // ensure history folder exists
        String historyKey = info.id() + "/history/";
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(historyKey).build(),
                software.amazon.awssdk.core.sync.RequestBody.empty());
        if (snippet.getCron() != null && !snippet.getCron().isBlank()) {
            scheduleSnippet(snippet, token);
        }
    }

    public List<Snippet> listSnippets(String token) throws IOException {
        UserInfoDto info = userRepository.getUserInfo(token);
        if (info == null) throw new IllegalArgumentException("Invalid token");
        String prefix = info.id() + "/snippets/";
        List<Snippet> snippets = new ArrayList<>();
        try {
            var req = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build();
            var res = s3.listObjectsV2(req);
            for (var obj : res.contents()) {
                var get = GetObjectRequest.builder().bucket(bucket).key(obj.key()).build();
                var bytes = s3.getObjectAsBytes(get).asByteArray();
                snippets.add(mapper.readValue(bytes, Snippet.class));
            }
        } catch (Exception ignored) {
        }
        return snippets;
    }

    public void runSnippet(String token, String name) throws IOException {
        UserInfoDto info = userRepository.getUserInfo(token);
        if (info == null) throw new IllegalArgumentException("Invalid token");
        String key = info.id() + "/snippets/" + name + ".json";
        var get = GetObjectRequest.builder().bucket(bucket).key(key).build();
        var bytes = s3.getObjectAsBytes(get).asByteArray();
        Snippet snippet = mapper.readValue(bytes, Snippet.class);
        jobService.submitJob(new CodeRequest(snippet.getCode(), snippet.getDependencies()), info.id(), name);
    }

    private void scheduleInternal(Snippet snippet, String userId) {
        Runnable task = () -> {
            try {
                jobService.submitJob(new CodeRequest(snippet.getCode(), snippet.getDependencies()), userId, snippet.getName());
            } catch (Exception ignored) {}
        };
        String cron = snippet.getCron();
        String[] parts = cron.trim().split(" ");
        if (parts.length == 4) {
            cron = "0 " + cron + " *";        // add seconds and day-of-week
        } else if (parts.length == 5) {
            cron = "0 " + cron;               // add seconds
        }
        CronTrigger trigger = new CronTrigger(cron);
        ScheduledFuture<?> future = scheduler.schedule(task, trigger);
        tasks.add(future);
    }

    private void scheduleSnippet(Snippet snippet, String token) {
        UserInfoDto info = userRepository.getUserInfo(token);
        if (info == null) return;
        scheduleInternal(snippet, info.id());
    }

    @PostConstruct
    private void loadScheduledOnStartup() {
        try {
            var req = ListObjectsV2Request.builder().bucket(bucket).prefix("").build();
            var res = s3.listObjectsV2(req);
            for (var obj : res.contents()) {
                String key = obj.key();
                if (!key.contains("/snippets/")) continue;
                var bytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
                Snippet snip = mapper.readValue(bytes, Snippet.class);
                if (snip.getCron() != null && !snip.getCron().isBlank()) {
                    String userId = key.substring(0, key.indexOf('/'));
                    scheduleInternal(snip, userId);
                }
            }
        } catch (Exception ignored) {}
    }

    public List<ScheduledSnippet> scheduledWithHistory(String token) throws IOException {
        UserInfoDto info = userRepository.getUserInfo(token);
        if (info == null) throw new IllegalArgumentException("Invalid token");
        List<Snippet> snippets = listSnippets(token);
        List<RunRecord> history = historyService.list(info.id());
        List<ScheduledSnippet> result = new ArrayList<>();
        for (Snippet s : snippets) {
            if (s.getCron() != null && !s.getCron().isBlank()) {
                List<RunRecord> h = new ArrayList<>();
                for (RunRecord r : history) {
                    if (s.getName().equals(r.getSnippetName())) h.add(r);
                }
                result.add(new ScheduledSnippet(s, h));
            }
        }
        return result;
    }
}
