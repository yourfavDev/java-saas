package com.libraries.saas.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.UUID;

@Controller
public class IndexController {
    @GetMapping
    public String index(HttpSession session) {
        return "index";
    }
}
