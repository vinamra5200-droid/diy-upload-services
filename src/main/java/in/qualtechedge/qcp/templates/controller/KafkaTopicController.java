package in.qualtechedge.qcp.templates.controller;

import in.qualtechedge.qcp.templates.dto.request.KafkaTopicRequest;
import in.qualtechedge.qcp.templates.dto.response.APIResponse;
import in.qualtechedge.qcp.templates.dto.response.KafkaTopicResponse;
import in.qualtechedge.qcp.templates.openapi.KafkaTopicDocumentation;
import in.qualtechedge.qcp.templates.service.KafkaTopicAdminService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/kafka-topics")
@RequiredArgsConstructor
@Slf4j
public class KafkaTopicController implements KafkaTopicDocumentation {

    private final KafkaTopicAdminService kafkaTopicAdminService;

    @Override
    @GetMapping
    public ResponseEntity<APIResponse<List<KafkaTopicResponse>>> listTopics() {
        log.info("List Kafka topics request");
        List<KafkaTopicResponse> response = kafkaTopicAdminService.listTopics();
        log.info("Kafka topics listed: count={}", response.size());
        return ResponseEntity.ok(APIResponse.success(HttpStatus.OK.value(), "OK", response));
    }

    @Override
    @PostMapping
    public ResponseEntity<APIResponse<KafkaTopicResponse>> createTopic(@Valid @RequestBody KafkaTopicRequest request) {
        log.info("Create Kafka topic request: name={}", request.name());
        KafkaTopicResponse response = kafkaTopicAdminService.createTopic(request);
        log.info("Kafka topic created: name={}", request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(APIResponse.success(HttpStatus.CREATED.value(), "Created", response));
    }
}
