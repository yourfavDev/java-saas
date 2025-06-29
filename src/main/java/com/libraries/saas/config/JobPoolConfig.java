package com.libraries.saas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configuration for a virtual thread pool to execute code jobs.
 */
@Configuration
public class JobPoolConfig {

    /**
     * ExecutorService backed by virtual threads for running code execution jobs.
     *
     * @return an ExecutorService that creates a new virtual thread per task
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService jobExecutor() {
        // JDK 19+ Virtual Thread-per-Task Executor
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().factory()
        );
    }
}
