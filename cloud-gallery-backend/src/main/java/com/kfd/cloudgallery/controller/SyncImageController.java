package com.kfd.cloudgallery.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kfd.cloudgallery.common.BaseResponse;
import com.kfd.cloudgallery.common.ResultUtils;
import com.kfd.cloudgallery.model.entity.ImageDataSync;
import com.kfd.cloudgallery.model.vo.SyncImageVO;
import com.kfd.cloudgallery.service.ImageDataSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 同步图像数据控制器
 */
@Slf4j
@RestController
@RequestMapping("/sync-image")
public class SyncImageController {

    @Resource
    private ImageDataSyncService imageDataSyncService;

    /**
     * 分页获取同步的图像列表
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SyncImageVO>> listSyncImageVOByPage(
            @RequestBody SyncImageQueryRequest syncImageQueryRequest,
            HttpServletRequest request) {
        
        long current = syncImageQueryRequest.getCurrent();
        long size = syncImageQueryRequest.getPageSize();
        
        // 限制爬虫
        if (size > 20) {
            return new BaseResponse<>(400, null, "页面大小不能超过20");
        }
        
        // 构建查询条件
        QueryWrapper<ImageDataSync> queryWrapper = new QueryWrapper<>();
        
        // 只查询同步成功的记录
        queryWrapper.eq("sync_status", 1);
        
        // 按同步时间倒序排列
        queryWrapper.orderByDesc("sync_time");
        
        // 分页查询
        Page<ImageDataSync> syncImagePage = imageDataSyncService.page(
            new Page<>(current, size), queryWrapper);
        
        // 转换为VO
        List<SyncImageVO> syncImageVOList = syncImagePage.getRecords().stream()
            .map(this::convertToSyncImageVO)
            .collect(Collectors.toList());
        
        // 构建返回结果
        Page<SyncImageVO> resultPage = new Page<>(current, size, syncImagePage.getTotal());
        resultPage.setRecords(syncImageVOList);
        
        return ResultUtils.success(resultPage);
    }

    /**
     * 根据ID获取同步图像详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<SyncImageVO> getSyncImageVOById(@RequestParam Long id) {
        if (id <= 0) {
            return new BaseResponse<>(400, null, "参数错误");
        }
        
        ImageDataSync syncImage = imageDataSyncService.getById(id);
        if (syncImage == null || syncImage.getSyncStatus() != 1) {
            return new BaseResponse<>(404, null, "图像不存在");
        }
        
        SyncImageVO syncImageVO = convertToSyncImageVO(syncImage);
        return ResultUtils.success(syncImageVO);
    }

    /**
     * 转换为VO对象
     */
    private SyncImageVO convertToSyncImageVO(ImageDataSync syncImage) {
        SyncImageVO vo = new SyncImageVO();
        vo.setId(syncImage.getId());
        vo.setOriginalId(syncImage.getOriginalId());
        vo.setDeviceToken(syncImage.getDeviceToken());
        vo.setImageName(syncImage.getImageName());
        vo.setImageUrl(syncImage.getImageUrl());
        vo.setWidth(syncImage.getWidth());
        vo.setHeight(syncImage.getHeight());
        vo.setFileSize(syncImage.getFileSize());
        vo.setDetections(syncImage.getDetections());
        vo.setMetadata(syncImage.getMetadata());
        vo.setGeoBoundary(syncImage.getGeoBoundary());
        vo.setMars3dData(syncImage.getMars3dData());
        vo.setSyncTime(syncImage.getSyncTime());
        vo.setSyncStatus(syncImage.getSyncStatus());
        return vo;
    }

    /**
     * 同步图像查询请求
     */
    public static class SyncImageQueryRequest {
        private long current = 1;
        private long pageSize = 12;
        private String deviceToken;
        private String imageName;
        private Integer width;
        private Integer height;
        private Long fileSize;
        private String searchText;

        // Getters and Setters
        public long getCurrent() { return current; }
        public void setCurrent(long current) { this.current = current; }
        
        public long getPageSize() { return pageSize; }
        public void setPageSize(long pageSize) { this.pageSize = pageSize; }
        
        public String getDeviceToken() { return deviceToken; }
        public void setDeviceToken(String deviceToken) { this.deviceToken = deviceToken; }
        
        public String getImageName() { return imageName; }
        public void setImageName(String imageName) { this.imageName = imageName; }
        
        public Integer getWidth() { return width; }
        public void setWidth(Integer width) { this.width = width; }
        
        public Integer getHeight() { return height; }
        public void setHeight(Integer height) { this.height = height; }
        
        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
        
        public String getSearchText() { return searchText; }
        public void setSearchText(String searchText) { this.searchText = searchText; }
    }
}
