package com.libraries.saas.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libraries.saas.dto.RunRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class RunHistoryService {
    private final S3Client s3;
    private final String bucket;
    private final ObjectMapper mapper = new ObjectMapper();

    public RunHistoryService(S3Client s3, @Value("${app.s3.bucket-name}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    public void record(String userId, RunRecord record) {
        try {
            byte[] data = mapper.writeValueAsBytes(record);
            String key = userId + "/history/" + record.getJobId() + ".json";
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(data));
        } catch (Exception ignored) {
        }
    }

    public List<RunRecord> list(String userId) throws IOException {
        List<RunRecord> list = new ArrayList<>();
        String prefix = userId + "/history/";
        var res = s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build());
        for (var obj : res.contents()) {
            var get = GetObjectRequest.builder().bucket(bucket).key(obj.key()).build();
            var bytes = s3.getObjectAsBytes(get).asByteArray();
            list.add(mapper.readValue(bytes, RunRecord.class));
        }
        list.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        return list;
    }

    public RunRecord newRecord(String jobId, String snippetName, String status, String output, String error) {
        return new RunRecord(jobId, snippetName, Instant.now(), status, output, error);
    }
}
