package com.libraries.saas.controller;

import com.libraries.saas.dto.Snippet;
import com.libraries.saas.services.SnippetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/snippets")
public class SnippetController {
    private final SnippetService snippetService;

    public SnippetController(SnippetService snippetService) {
        this.snippetService = snippetService;
    }

    @PostMapping("/save")
    public ResponseEntity<Void> save(@RequestParam("token") String token,
                                     @RequestBody Snippet snippet) {
        try {
            snippetService.saveSnippet(token, snippet);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<Snippet>> list(@RequestParam("token") String token) {
        try {
            return ResponseEntity.ok(snippetService.listSnippets(token));
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/run")
    public ResponseEntity<Void> run(@RequestParam("token") String token,
                                    @RequestParam("name") String name) {
        try {
            snippetService.runSnippet(token, name);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
