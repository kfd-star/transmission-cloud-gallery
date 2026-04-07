package com.kfd.cloudgallery.controller;

import com.kfd.cloudgallery.annotation.AuthCheck;
import com.kfd.cloudgallery.common.BaseResponse;
import com.kfd.cloudgallery.common.ResultUtils;
import com.kfd.cloudgallery.constant.UserConstant;
import com.kfd.cloudgallery.model.entity.ImageDataSync;
import com.kfd.cloudgallery.service.ImageDataSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 图像数据同步控制器
 */
@Slf4j
@RestController
@RequestMapping("/sync")
// 移除条件注解，始终注册控制器
public class ImageDataSyncController {

    @Resource
    private ImageDataSyncService imageDataSyncService;

    /**
     * 同步PostgreSQL数据到MySQL
     */
    @PostMapping("/image-data")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Map<String, Object>> syncImageData() {
        try {
            log.info("开始同步PostgreSQL数据到MySQL");
            int syncCount = imageDataSyncService.syncDataFromPostgres();
            
            Map<String, Object> result = new HashMap<>();
            result.put("syncCount", syncCount);
            result.put("message", "同步完成，共同步 " + syncCount + " 条记录");
            
            log.info("数据同步完成，共同步 {} 条记录", syncCount);
            return ResultUtils.success(result);
            
        } catch (Exception e) {
            log.error("数据同步失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("syncCount", 0);
            errorResult.put("message", "同步失败: " + e.getMessage());
            return new BaseResponse<>(500, errorResult, "同步失败: " + e.getMessage());
        }
    }

    /**
     * 获取同步状态
     */
    @GetMapping("/status")
    public BaseResponse<Map<String, Object>> getSyncStatus() {
        try {
            Integer lastSyncId = imageDataSyncService.getLastSyncId();
            Long totalSyncCount = imageDataSyncService.getTotalSyncCount();
            
            Map<String, Object> result = new HashMap<>();
            result.put("lastSyncId", lastSyncId);
            result.put("totalSyncCount", totalSyncCount);
            result.put("message", "最后同步ID: " + lastSyncId + ", 总同步数量: " + totalSyncCount);
            
            return ResultUtils.success(result);
            
        } catch (Exception e) {
            log.error("获取同步状态失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("lastSyncId", 0);
            errorResult.put("totalSyncCount", 0);
            errorResult.put("message", "获取状态失败: " + e.getMessage());
            return new BaseResponse<>(500, errorResult, "获取状态失败: " + e.getMessage());
        }
    }
}
