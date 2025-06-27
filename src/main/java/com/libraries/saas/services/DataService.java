package com.libraries.saas.services;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.time.Instant;
import java.util.Map;

@Service
public class DataService {
    private final DynamoDbEnhancedClient enhanced;
    private final DynamoDbClient dynamo;
    private final SqsClient sqs;

    @Value("${app.sqs.error-queue-url}")
    public String errorQueueUrl;

    @Value("${app.dynamo.table-name}")
    private String tableName;

    public DataService(DynamoDbEnhancedClient enhanced, DynamoDbClient dynamo, SqsClient sqs) {
        this.enhanced = enhanced;
        this.dynamo = dynamo;
        this.sqs = sqs;
    }

    public void populateData(HttpSession session) {
        // 1) table stats
        var desc = dynamo.describeTable(DescribeTableRequest.builder()
                .tableName(tableName)
                .build());
        var tbl = desc.table();
        session.setAttribute("itemCount", tbl.itemCount());
        session.setAttribute("tableSizeKbytes", tbl.tableSizeBytes()/1000);

        // 2) last “Processed” timestamp via GSI, aliasing both status and timestamp
        var qry = QueryRequest.builder()
                .tableName(tableName)
                .indexName("status-timestamp-index")
                .keyConditionExpression("#st = :st")
                .expressionAttributeNames(Map.of(
                        "#st", "status",
                        "#ts", "timestamp"
                ))
                .expressionAttributeValues(Map.of(
                        ":st", AttributeValue.builder().s("Processed").build()
                ))
                .projectionExpression("#ts")
                .scanIndexForward(false)  // DESC
                .limit(1)
                .build();

        var result = dynamo.query(qry);
        long lastProcessedTs = result.items().stream()
                .findFirst()
                .map(item -> item.get("timestamp"))             // ← use the actual name
                .map(AttributeValue::n)
                .map(Long::parseLong)
                .orElse(0L);
        // 3) approximate number of messages in the error queue
        var attrReq = GetQueueAttributesRequest.builder()
                .queueUrl(errorQueueUrl)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build();

        var attrRes = sqs.getQueueAttributes(attrReq);
        int errorMsgCount = Integer.parseInt(
                attrRes.attributes()
                        .get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
        );

        session.setAttribute("errors", errorMsgCount);
        Instant instantSec = Instant.ofEpochSecond(lastProcessedTs);
        session.setAttribute("lastProcessedTimestamp", instantSec);
    }

}
