package com.libraries.saas.rest;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class TestRest {
    public String getExample() {
        return new RestTemplate()
                .getForObject("http://example.com", String.class);
    }
}
