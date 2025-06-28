package com.libraries.saas.controller;

import com.libraries.saas.dto.DashboardMetrics;
import com.libraries.saas.services.DataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DataService dataService;

    public DashboardController(DataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping("/dashboard")
    public DashboardMetrics metrics() {
        return dataService.fetchMetrics();
    }
}
