package com.libraries.saas.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libraries.auth.dto.UserInfoDto;
import com.libraries.auth.repository.UserRepository;
import com.libraries.saas.dto.CodeRequest;
import com.libraries.saas.dto.Snippet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
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
    private final List<ScheduledFuture<?>> tasks = new ArrayList<>();

    public SnippetService(S3Client s3,
                          @Value("${app.s3.bucket-name}") String bucket,
                          JobService jobService,
                          UserRepository userRepository,
                          TaskScheduler scheduler) {
        this.s3 = s3;
        this.bucket = bucket;
        this.jobService = jobService;
        this.userRepository = userRepository;
        this.scheduler = scheduler;
    }

    public void saveSnippet(String token, Snippet snippet) throws IOException {
        UserInfoDto info = userRepository.getUserInfo(token);
        if (info == null) throw new IllegalArgumentException("Invalid token");
        String key = info.id() + "/snippets/" + snippet.getName() + ".json";
        byte[] data = mapper.writeValueAsBytes(snippet);
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                new ByteArrayInputStream(data), data.length);
        if (snippet.getCron() != null && !snippet.getCron().isBlank()) {
            scheduleSnippet(snippet, token);
        }
    }

    public List<Snippet> listSnippets(String token) throws IOException {
        UserInfoDto info = userRepository.getUserInfo(token);
        if (info == null) throw new IllegalArgumentException("Invalid token");
        String prefix = info.id() + "/snippets/";
        var req = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build();
        var res = s3.listObjectsV2(req);
        List<Snippet> snippets = new ArrayList<>();
        for (var obj : res.contents()) {
            var get = GetObjectRequest.builder().bucket(bucket).key(obj.key()).build();
            var bytes = s3.getObjectAsBytes(get).asByteArray();
            snippets.add(mapper.readValue(bytes, Snippet.class));
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
        jobService.submitJob(new CodeRequest(snippet.getCode(), snippet.getDependencies()));
    }

    private void scheduleSnippet(Snippet snippet, String token) {
        Runnable task = () -> {
            try {
                jobService.submitJob(new CodeRequest(snippet.getCode(), snippet.getDependencies()));
            } catch (Exception ignored) {}
        };
        CronTrigger trigger = new CronTrigger(snippet.getCron());
        ScheduledFuture<?> future = scheduler.schedule(task, trigger);
        tasks.add(future);
    }
}
