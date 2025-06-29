package com.libraries.saas.controller;

import com.libraries.auth.dto.UserInfoDto;
import com.libraries.auth.repository.UserRepository;
import com.libraries.saas.dto.CodeRequest;
import com.libraries.saas.dto.StatusResponse;
import com.libraries.saas.services.JobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/code-execution")
public class JobController {

    private final JobService jobService;
    private final UserRepository userRepository;

    @Autowired
    public JobController(JobService jobService, UserRepository userRepository) {
        this.jobService = jobService;
        this.userRepository = userRepository;
    }

    /**
     * Submit code for execution.
     * @param token Session token for authentication
     * @param request Code payload
     * @return JSON containing jobId identifying the submitted job
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> submitCode(
            @RequestParam("token") String token,
            @RequestBody CodeRequest request) {
        try {
            UserInfoDto userInfo = userRepository.getUserInfo(token);
            if  (userInfo == null || !userInfo.roles().map(list -> list.contains("code-execution")).orElse(false)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            String jobId = jobService.submitJob(new CodeRequest(request.getCode(), request.getDependencies()), userInfo.id(), null);
            return ResponseEntity.ok(Collections.singletonMap("jobId", jobId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

    }

    /**
     * Poll job status.
     * @param token Session token for authentication
     * @param id The jobId returned from submitCode
     * @return StatusResponse containing status, output or error
     */
    @GetMapping
    public ResponseEntity<StatusResponse> getJobStatus(
            @RequestParam("token") String token,
            @RequestParam("id") String id) {
        try {
            UserInfoDto userInfo = userRepository.getUserInfo(token);

            if  (userInfo == null || !userInfo.roles().map(list -> list.contains("code-execution")).orElse(false)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            StatusResponse statusResponse = jobService.getJobStatus(id);
            return ResponseEntity.ok(statusResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

    }

    @PostMapping("/cancel")
    public ResponseEntity<Void> cancelJob(
            @RequestParam("token") String token,
            @RequestParam("id") String id) {
        try {
            UserInfoDto userInfo = userRepository.getUserInfo(token);

            if  (userInfo == null || !userInfo.roles().map(list -> list.contains("code-execution")).orElse(false)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            jobService.cancelJob(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

    }

}
