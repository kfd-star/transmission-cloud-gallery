package com.kfd.cloudgallery.config;

import com.kfd.cloudgallery.service.ImageDataSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL异步初始化器
 * 在应用启动完成后异步初始化PostgreSQL连接
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "postgres.enabled", havingValue = "true", matchIfMissing = false)
public class PostgresInitializer {

    @Autowired(required = false)
    private ImageDataSyncService imageDataSyncService;

    /**
     * 应用启动完成后异步初始化PostgreSQL连接
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (imageDataSyncService != null) {
            log.info("应用启动完成，开始异步初始化PostgreSQL连接...");
            // 这里可以调用异步初始化方法
            // imageDataSyncService.initializePostgresConnection();
        }
    }
}
