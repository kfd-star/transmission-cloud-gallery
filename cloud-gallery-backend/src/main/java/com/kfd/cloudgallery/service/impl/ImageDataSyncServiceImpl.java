package com.kfd.cloudgallery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kfd.cloudgallery.mapper.ImageDataSyncMapper;
import com.kfd.cloudgallery.model.dto.postgres.PostgresImageData;
import com.kfd.cloudgallery.model.entity.ImageDataSync;
import com.kfd.cloudgallery.service.ImageDataSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * 图像数据同步服务实现
 */
@Slf4j
@Service
// 移除条件注解，始终注册服务，但只在需要时创建PostgreSQL连接
public class ImageDataSyncServiceImpl extends ServiceImpl<ImageDataSyncMapper, ImageDataSync> implements ImageDataSyncService {

    // 动态创建PostgreSQL连接，避免启动时依赖注入冲突
    private JdbcTemplate postgresJdbcTemplate;

    /**
     * 动态创建PostgreSQL连接
     * 只在需要时创建，避免启动时依赖注入冲突
     */
    private JdbcTemplate getPostgresJdbcTemplate() {
        if (postgresJdbcTemplate == null) {
            try {
                log.info("动态创建PostgreSQL连接...");
                DriverManagerDataSource dataSource = new DriverManagerDataSource();
                dataSource.setDriverClassName("org.postgresql.Driver");
                dataSource.setUrl("jdbc:postgresql://localhost:5433/sensor_data_system");
                dataSource.setUsername("postgres");
                dataSource.setPassword("postgres");
                
                postgresJdbcTemplate = new JdbcTemplate(dataSource);
                log.info("PostgreSQL连接创建成功");
            } catch (Exception e) {
                log.error("创建PostgreSQL连接失败: {}", e.getMessage());
                throw new RuntimeException("PostgreSQL连接失败", e);
            }
        }
        return postgresJdbcTemplate;
    }

    @Override
    @Transactional
    public int syncDataFromPostgres() {
        try {
            log.info("开始同步PostgreSQL数据...");
            
            // 获取最后同步的ID
            Integer lastSyncId = getLastSyncId();
            log.info("最后同步ID: {}", lastSyncId);
            
            // 查询PostgreSQL中新增的数据
            String sql = "SELECT id, device_token, image_name, image_url, width, height, file_size, " +
                        "detections, metadata, geo_boundary, mars3d_data, timestamp " +
                        "FROM image_data WHERE id > ? ORDER BY id";
            
            log.info("执行PostgreSQL查询: {}", sql);
            List<PostgresImageData> postgresDataList = getPostgresJdbcTemplate().query(
                sql, 
                new BeanPropertyRowMapper<>(PostgresImageData.class), 
                lastSyncId != null ? lastSyncId : 0
            );
            
            log.info("PostgreSQL查询结果: {} 条记录", postgresDataList.size());
            
            if (postgresDataList.isEmpty()) {
                log.info("没有新的数据需要同步");
                return 0;
            }
            
            int syncCount = 0;
            for (PostgresImageData postgresData : postgresDataList) {
                try {
                    // 检查是否已经同步过
                    QueryWrapper<ImageDataSync> queryWrapper = new QueryWrapper<>();
                    queryWrapper.eq("original_id", postgresData.getId());
                    ImageDataSync existingSync = this.getOne(queryWrapper);
                    
                    if (existingSync != null) {
                        log.debug("数据已存在，跳过同步: original_id={}", postgresData.getId());
                        continue;
                    }
                    
                    // 创建同步记录
                    ImageDataSync syncRecord = new ImageDataSync();
                    syncRecord.setOriginalId(postgresData.getId());
                    syncRecord.setDeviceToken(postgresData.getDeviceToken());
                    syncRecord.setImageName(postgresData.getImageName());
                    syncRecord.setImageUrl(postgresData.getImageUrl());
                    syncRecord.setWidth(postgresData.getWidth());
                    syncRecord.setHeight(postgresData.getHeight());
                    syncRecord.setFileSize(postgresData.getFileSize());
                    syncRecord.setDetections(postgresData.getDetections());
                    syncRecord.setMetadata(postgresData.getMetadata());
                    syncRecord.setGeoBoundary(postgresData.getGeoBoundary());
                    syncRecord.setMars3dData(postgresData.getMars3dData());
                    syncRecord.setSyncTime(new Date());
                    syncRecord.setSyncStatus(1);
                    syncRecord.setSyncError(null);
                    
                    // 保存到MySQL
                    boolean saved = this.save(syncRecord);
                    if (saved) {
                        syncCount++;
                        log.debug("成功同步数据: original_id={}, image_name={}", 
                                postgresData.getId(), postgresData.getImageName());
                    } else {
                        log.error("保存同步记录失败: original_id={}", postgresData.getId());
                    }
                    
                } catch (Exception e) {
                    log.error("同步单条数据失败: original_id={}, error={}", 
                            postgresData.getId(), e.getMessage(), e);
                    
                    // 记录同步失败的记录
                    try {
                        ImageDataSync errorRecord = new ImageDataSync();
                        errorRecord.setOriginalId(postgresData.getId());
                        errorRecord.setDeviceToken(postgresData.getDeviceToken());
                        errorRecord.setImageName(postgresData.getImageName());
                        errorRecord.setImageUrl(postgresData.getImageUrl());
                        errorRecord.setSyncTime(new Date());
                        errorRecord.setSyncStatus(0);
                        errorRecord.setSyncError(e.getMessage());
                        this.save(errorRecord);
                    } catch (Exception ex) {
                        log.error("保存错误记录失败: original_id={}", postgresData.getId(), ex);
                    }
                }
            }
            
            log.info("数据同步完成，共同步 {} 条记录", syncCount);
            return syncCount;
            
        } catch (Exception e) {
            log.error("数据同步失败", e);
            throw new RuntimeException("数据同步失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Integer getLastSyncId() {
        try {
            QueryWrapper<ImageDataSync> queryWrapper = new QueryWrapper<>();
            queryWrapper.orderByDesc("original_id").last("LIMIT 1");
            ImageDataSync lastSync = this.getOne(queryWrapper);
            return lastSync != null ? lastSync.getOriginalId() : 0;
        } catch (Exception e) {
            log.error("获取最后同步ID失败", e);
            return 0;
        }
    }

    @Override
    public Long getTotalSyncCount() {
        try {
            QueryWrapper<ImageDataSync> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("sync_status", 1); // 只统计成功的同步记录
            return this.count(queryWrapper);
        } catch (Exception e) {
            log.error("获取总同步数量失败", e);
            return 0L;
        }
    }
}
