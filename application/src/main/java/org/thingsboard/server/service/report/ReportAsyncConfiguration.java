/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package org.thingsboard.server.service.report;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ReportAsyncConfiguration {

    @Bean(name = "reportTaskExecutor")
    public ThreadPoolTaskExecutor reportTaskExecutor(
            ReportAsyncProperties properties) {

        int corePoolSize =
                Math.max(
                        1,
                        properties.getCorePoolSize()
                );

        int maxPoolSize =
                Math.max(
                        corePoolSize,
                        properties.getMaxPoolSize()
                );

        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setThreadNamePrefix(
                "report-worker-"
        );

        executor.setCorePoolSize(
                corePoolSize
        );

        executor.setMaxPoolSize(
                maxPoolSize
        );

        executor.setQueueCapacity(
                Math.max(
                        0,
                        properties.getQueueCapacity()
                )
        );

        executor.setKeepAliveSeconds(
                Math.max(
                        1,
                        properties.getKeepAliveSeconds()
                )
        );

        executor.setAllowCoreThreadTimeOut(
                false
        );

        executor.setWaitForTasksToCompleteOnShutdown(
                true
        );

        executor.setAwaitTerminationSeconds(
                Math.max(
                        1,
                        properties
                                .getShutdownAwaitTerminationSeconds()
                )
        );

        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.AbortPolicy()
        );

        executor.initialize();

        return executor;
    }
}