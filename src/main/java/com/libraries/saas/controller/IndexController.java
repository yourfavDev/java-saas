package com.libraries.saas.controller;

import com.libraries.saas.rest.TestRest;
import com.libraries.saas.services.DataService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

    private final DataService dataService;

    public IndexController(DataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping("/")
    public String index(HttpSession session) {
        // existing session data
        dataService.populateData(session);


        return "index";
    }
}
