package com.libraries.saas.controller;

import com.libraries.auth.dto.UserInfoDto;
import com.libraries.auth.repository.UserRepository;
import com.libraries.saas.dto.RunRecord;
import com.libraries.saas.services.RunHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/history")
public class HistoryController {
    private final RunHistoryService historyService;
    private final UserRepository userRepository;

    public HistoryController(RunHistoryService historyService, UserRepository userRepository) {
        this.historyService = historyService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<RunRecord>> history(@RequestParam("token") String token) {
        try {
            UserInfoDto info = userRepository.getUserInfo(token);
            if (info == null) return ResponseEntity.badRequest().build();
            return ResponseEntity.ok(historyService.list(info.id()));
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
