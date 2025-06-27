package com.libraries.saas.controller;

import com.libraries.saas.rest.TestRest;
import com.libraries.saas.services.DataService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

    private final DataService dataService;
    private final TestRest testRest;

    public IndexController(DataService dataService, TestRest testRest) {
        this.dataService = dataService;
        this.testRest = testRest;
    }

    @GetMapping("/")
    public String index(HttpSession session) {
        // existing session data
        dataService.populateData(session);

        // fetch example.com HTML and put into session
        String exampleHtml = testRest.getExample();
        session.setAttribute("exampleHtml", exampleHtml);

        return "index";
    }
}
