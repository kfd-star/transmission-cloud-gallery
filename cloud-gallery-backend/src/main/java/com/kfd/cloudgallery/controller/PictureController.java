package com.kfd.cloudgallery.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kfd.cloudgallery.annotation.AuthCheck;
import com.kfd.cloudgallery.common.BaseResponse;
import com.kfd.cloudgallery.common.DeleteRequest;
import com.kfd.cloudgallery.common.ResultUtils;
import com.kfd.cloudgallery.constant.UserConstant;
import com.kfd.cloudgallery.exception.BusinessException;
import com.kfd.cloudgallery.exception.ErrorCode;
import com.kfd.cloudgallery.exception.ThrowUtils;
import com.kfd.cloudgallery.manager.auth.SpaceUserAuthManager;
import com.kfd.cloudgallery.manager.auth.StpKit;
import com.kfd.cloudgallery.manager.auth.annotation.SaSpaceCheckPermission;
import com.kfd.cloudgallery.manager.auth.model.SpaceUserPermissionConstant;
import com.kfd.cloudgallery.model.dto.picture.*;
import com.kfd.cloudgallery.model.entity.Picture;
import com.kfd.cloudgallery.model.entity.Space;
import com.kfd.cloudgallery.model.entity.User;
import com.kfd.cloudgallery.model.enums.PictureReviewStatusEnum;
import com.kfd.cloudgallery.model.vo.PictureTagCategory;
import com.kfd.cloudgallery.model.vo.PictureVO;
import com.kfd.cloudgallery.model.entity.ImageDataSync;
import com.kfd.cloudgallery.service.PictureService;
import com.kfd.cloudgallery.service.SpaceService;
import com.kfd.cloudgallery.service.UserService;
import com.kfd.cloudgallery.service.ImageDataSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 */
@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private ImageDataSyncService imageDataSyncService;

    /**
     * 本地缓存
     */
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1024)
            .maximumSize(10_000L) // 最大 10000 条
            // 缓存 5 分钟后移除
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    /**
     * 上传图片（可重新上传）
     */
    @PostMapping("/upload")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过 URL 上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    @PostMapping("/delete")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest
            , HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.deletePicture(deleteRequest.getId(), loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 更新图片（仅管理员可用）
     *
     * @param pictureUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest,
                                               HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        // 判断是否存在
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 补充审核参数
        User loginUser = userService.getLoginUser(request);
        pictureService.fillReviewParams(oldPicture, loginUser);
        // 操作数据库
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        
        // 先查询普通图片
        Picture picture = pictureService.getById(id);
        if (picture != null) {
            // 普通图片存在，处理权限和返回
            User loginUser = userService.getLoginUser(request);
            Long spaceId = picture.getSpaceId();
            Space space = null;
            
            // 只有图片属于某个空间时，才进行空间权限校验
            if (spaceId != null) {
                space = spaceService.getById(spaceId);
                ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
                
                // 空间权限校验：检查用户是否有权限访问该空间
                boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
                ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
            }
            // 公开图库的图片（spaceId为null）不需要空间权限校验
            
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
            PictureVO pictureVO = pictureService.getPictureVO(picture, request);
            pictureVO.setPermissionList(permissionList);
            return ResultUtils.success(pictureVO);
        }
        
        // 普通图片不存在，尝试查询同步图像
        ImageDataSync syncImage = imageDataSyncService.getById(id);
        if (syncImage != null) {
            // 找到同步图像，转换为PictureVO
            PictureVO pictureVO = convertSyncImageToPictureVO(syncImage);
            List<String> permissionList = new ArrayList<>();
            pictureVO.setPermissionList(permissionList);
            return ResultUtils.success(pictureVO);
        }
        
        // 都不存在
        throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图像不存在");
    }

    /**
     * 分页获取图片列表（仅管理员可用）
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图片列表（封装类）
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 100, ErrorCode.PARAMS_ERROR);
        // 空间权限校验
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公开图库
            // 普通用户默认只能看到审核通过的数据
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, ErrorCode.NO_AUTH_ERROR);
            // 已经改为使用注解鉴权
//            // 私有空间
//            User loginUser = userService.getLoginUser(request);
//            Space space = spaceService.getById(spaceId);
//            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
        }
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取封装类
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 分页获取混合图片列表（包含普通图像和同步图像）
     */
    @PostMapping("/list/page/vo/mixed")
    public BaseResponse<Page<PictureVO>> listMixedPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                  HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 100, ErrorCode.PARAMS_ERROR);
        
        // 查询普通图像（移除审核状态限制）
        PictureQueryRequest modifiedRequest = new PictureQueryRequest();
        BeanUtils.copyProperties(pictureQueryRequest, modifiedRequest);
        modifiedRequest.setReviewStatus(null); // 移除审核状态限制
        modifiedRequest.setNullSpaceId(true);   // 只查询公共图库
        
        // 获取所有普通图像（不分页）
        List<Picture> allPictures = pictureService.list(pictureService.getQueryWrapper(modifiedRequest));
        
        // 获取所有同步图像（不分页）
        List<ImageDataSync> allSyncImages = imageDataSyncService.list(
            new QueryWrapper<ImageDataSync>().eq("sync_status", 1).orderByDesc("sync_time")
        );
        
        // 合并结果
        List<PictureVO> mixedList = new ArrayList<>();
        
        // 添加普通图像
        if (allPictures != null && !allPictures.isEmpty()) {
            // 创建临时分页对象来获取VO
            Page<Picture> tempPage = new Page<>(1, allPictures.size());
            tempPage.setRecords(allPictures);
            mixedList.addAll(pictureService.getPictureVOPage(tempPage, request).getRecords());
        }
        
        // 添加同步图像（转换为PictureVO格式）
        if (allSyncImages != null && !allSyncImages.isEmpty()) {
            List<PictureVO> syncPictureVOs = allSyncImages.stream()
                .map(this::convertSyncImageToPictureVO)
                .collect(Collectors.toList());
            mixedList.addAll(syncPictureVOs);
        }
        
        // 应用搜索文本筛选
        if (StrUtil.isNotBlank(pictureQueryRequest.getSearchText())) {
            String searchText = pictureQueryRequest.getSearchText().toLowerCase();
            mixedList = mixedList.stream()
                .filter(image -> {
                    // 搜索图片名称和简介
                    String name = image.getName() != null ? image.getName().toLowerCase() : "";
                    String introduction = image.getIntroduction() != null ? image.getIntroduction().toLowerCase() : "";
                    return name.contains(searchText) || introduction.contains(searchText);
                })
                .collect(Collectors.toList());
        }
        
        // 应用标签筛选
        if (pictureQueryRequest.getTags() != null && !pictureQueryRequest.getTags().isEmpty()) {
            mixedList = mixedList.stream()
                .filter(image -> {
                    if (image.getTags() == null || image.getTags().isEmpty()) return false;
                    // 修改逻辑：只要图片包含任意一个选中的标签就通过筛选
                    return pictureQueryRequest.getTags().stream()
                        .anyMatch(tag -> image.getTags().contains(tag));
                })
                .collect(Collectors.toList());
        }
        
        // 检查是否只返回同步图像（通过category参数判断）
        if (pictureQueryRequest.getCategory() != null && "无人机影像".equals(pictureQueryRequest.getCategory())) {
            mixedList = mixedList.stream()
                .filter(image -> "无人机影像".equals(image.getCategory()))
                .collect(Collectors.toList());
        }
        
        // 按创建时间排序
        mixedList.sort((a, b) -> {
            Date dateA = a.getCreateTime() != null ? a.getCreateTime() : new Date(0);
            Date dateB = b.getCreateTime() != null ? b.getCreateTime() : new Date(0);
            return dateB.compareTo(dateA); // 降序
        });
        
        // 手动分页
        int totalSize = mixedList.size();
        int start = (int) ((current - 1) * size);
        int end = Math.min(start + (int) size, totalSize);
        
        List<PictureVO> pagedList = start < totalSize ? mixedList.subList(start, end) : new ArrayList<>();
        
        // 构建分页结果
        Page<PictureVO> resultPage = new Page<>(current, size, totalSize);
        resultPage.setRecords(pagedList);
        
        return ResultUtils.success(resultPage);
    }

    /**
     * 将同步图像转换为PictureVO
     */
    private PictureVO convertSyncImageToPictureVO(ImageDataSync syncImage) {
        PictureVO vo = new PictureVO();
        vo.setId(syncImage.getId()); // 使用原始ID，不会冲突
        vo.setUrl(syncImage.getImageUrl());
        vo.setThumbnailUrl(syncImage.getImageUrl()); // 使用原图作为缩略图
        vo.setName(syncImage.getImageName());
        
        // 优化简介内容格式，让检测结果和GPS坐标更美观
        StringBuilder intro = new StringBuilder();
        if (syncImage.getDetections() != null && !syncImage.getDetections().isEmpty()) {
            String formattedDetections = formatDetections(syncImage.getDetections());
            intro.append("🔍 检测结果:\n").append(formattedDetections).append("\n\n");
        }
        if (syncImage.getMetadata() != null && !syncImage.getMetadata().isEmpty()) {
            String gpsInfo = extractGPSInfo(syncImage.getMetadata());
            if (!gpsInfo.isEmpty()) {
                intro.append("📍 GPS坐标:\n").append(gpsInfo);
            }
        }
        vo.setIntroduction(intro.length() > 0 ? intro.toString().trim() : "无人机影像数据");
        
        // 映射尺寸信息
        vo.setPicWidth(syncImage.getWidth());
        vo.setPicHeight(syncImage.getHeight());
        vo.setPicSize(syncImage.getFileSize());
        
        // 计算比例
        if (syncImage.getWidth() != null && syncImage.getHeight() != null && syncImage.getHeight() > 0) {
            double scale = Math.round((syncImage.getWidth() * 1.0 / syncImage.getHeight()) * 100.0) / 100.0;
            vo.setPicScale(scale);
        }
        
        // 设置格式（从文件扩展名推断）
        String imageName = syncImage.getImageName();
        if (imageName != null && imageName.contains(".")) {
            String extension = imageName.substring(imageName.lastIndexOf(".") + 1).toLowerCase();
            vo.setPicFormat(extension);
        } else {
            vo.setPicFormat("jpg");
        }
        
        // 设置默认值
        vo.setPicColor("#FFFFFF");
        vo.setCategory("无人机影像");
        
        // 从检测结果中提取默认标签
        List<String> tags = extractTagsFromDetections(syncImage.getDetections());
        vo.setTags(tags);
        
        // 设置地理边界数据（用于3D地球展示）
        vo.setGeoBoundary(syncImage.getGeoBoundary());
        
        vo.setSpaceId(null);
        vo.setUserId(0L); // 同步图像没有用户
        
        // 设置时间
        vo.setCreateTime(syncImage.getSyncTime());
        vo.setEditTime(syncImage.getSyncTime());
        vo.setUpdateTime(syncImage.getSyncTime());
        
        return vo;
    }
    
    /**
     * 从检测结果中提取标签
     */
    private List<String> extractTagsFromDetections(String detections) {
        List<String> tags = new ArrayList<>();
        if (detections == null || detections.isEmpty()) {
            return tags;
        }
        
        // 转换为小写进行匹配
        String lowerDetections = detections.toLowerCase();
        
        // 如果包含"null"，则不提取任何标签（留空）
        if (lowerDetections.contains("null")) {
            return tags; // 返回空列表
        }
        
        // 检查关键词并添加对应标签
        if (lowerDetections.contains("nest")) {
            tags.add("鸟巢");
        }
        if (lowerDetections.contains("insulator defect")) {
            tags.add("绝缘子");
        }
        if (lowerDetections.contains("balloon")) {
            tags.add("气球");
        }
        
        return tags;
    }
    
    /**
     * 格式化检测结果，让显示更美观
     */
    private String formatDetections(String detections) {
        if (detections == null || detections.isEmpty()) {
            return "";
        }
        
        try {
            // 尝试解析JSON数组
            cn.hutool.json.JSONArray jsonArray = cn.hutool.json.JSONUtil.parseArray(detections);
            StringBuilder result = new StringBuilder();
            
            for (int i = 0; i < jsonArray.size(); i++) {
                cn.hutool.json.JSONObject detection = jsonArray.getJSONObject(i);
                if (detection != null) {
                    result.append("  • ");
                    
                    // 获取类别名称
                    String className = detection.getStr("class_name");
                    if (className != null) {
                        // 转换类别名称为中文
                        String chineseName = convertClassNameToChinese(className);
                        result.append(chineseName);
                    }
                    
                    // 获取置信度
                    Double confidence = detection.getDouble("confidence");
                    if (confidence != null) {
                        result.append(" (置信度: ").append(String.format("%.1f%%", confidence * 100)).append(")");
                    }
                    
                    // 获取位置信息
                    Double xCenter = detection.getDouble("x_center");
                    Double yCenter = detection.getDouble("y_center");
                    Double width = detection.getDouble("width");
                    Double height = detection.getDouble("height");
                    
                    if (xCenter != null && yCenter != null && width != null && height != null) {
                        result.append("\n    位置: (").append(String.format("%.3f", xCenter))
                              .append(", ").append(String.format("%.3f", yCenter)).append(")")
                              .append(" 大小: ").append(String.format("%.3f", width))
                              .append(" × ").append(String.format("%.3f", height));
                    }
                    
                    if (i < jsonArray.size() - 1) {
                        result.append("\n");
                    }
                }
            }
            
            return result.toString();
        } catch (Exception e) {
            // 如果JSON解析失败，返回原始格式
            return detections.replace(",", "\n  • ");
        }
    }
    
    /**
     * 将英文类别名称转换为中文
     */
    private String convertClassNameToChinese(String className) {
        if (className == null) return "";
        
        switch (className.toLowerCase()) {
            case "nest":
                return "鸟巢";
            case "insulator":
            case "insulator defect":
                return "绝缘子";
            case "balloon":
                return "气球";
            case "null":
                return "无检测目标";
            default:
                return className;
        }
    }
    
    /**
     * 从元数据中提取GPS坐标信息
     */
    private String extractGPSInfo(String metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }
        
        StringBuilder gpsInfo = new StringBuilder();
        
        // 查找GPSLatitude
        String latitudePattern = "\"GPSLatitude\"\\s*:\\s*\"?([^\",}]+)\"?";
        java.util.regex.Pattern latPattern = java.util.regex.Pattern.compile(latitudePattern, java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher latMatcher = latPattern.matcher(metadata);
        if (latMatcher.find()) {
            String latValue = latMatcher.group(1).trim();
            gpsInfo.append("  • 纬度: ").append(formatCoordinate(latValue)).append("\n");
        }
        
        // 查找GPSLongitude
        String longitudePattern = "\"GPSLongitude\"\\s*:\\s*\"?([^\",}]+)\"?";
        java.util.regex.Pattern lonPattern = java.util.regex.Pattern.compile(longitudePattern, java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher lonMatcher = lonPattern.matcher(metadata);
        if (lonMatcher.find()) {
            String lonValue = lonMatcher.group(1).trim();
            gpsInfo.append("  • 经度: ").append(formatCoordinate(lonValue));
        }
        
        return gpsInfo.toString().trim();
    }

    /**
     * 格式化坐标显示
     */
    private String formatCoordinate(String coordinate) {
        if (coordinate == null || coordinate.isEmpty()) {
            return "";
        }
        
        try {
            double value = Double.parseDouble(coordinate);
            return String.format("%.6f°", value);
        } catch (NumberFormatException e) {
            return coordinate;
        }
    }

    /**
     * 分页获取图片列表（封装类，有缓存）
     */
    @Deprecated
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                      HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 100, ErrorCode.PARAMS_ERROR);
        // 普通用户默认只能看到审核通过的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 查询缓存，缓存中没有，再查询数据库
        // 构建缓存的 key
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = String.format("cloudgallery:listPictureVOByPage:%s", hashKey);
        // 1. 先从本地缓存中查询
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue != null) {
            // 如果缓存命中，返回结果
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }
        // 2. 本地缓存未命中，查询 Redis 分布式缓存
        ValueOperations<String, String> opsForValue = stringRedisTemplate.opsForValue();
        cachedValue = opsForValue.get(cacheKey);
        if (cachedValue != null) {
            // 如果缓存命中，更新本地缓存，返回结果
            LOCAL_CACHE.put(cacheKey, cachedValue);
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }
        // 3. 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 4. 更新缓存
        // 更新 Redis 缓存
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 设置缓存的过期时间，5 - 10 分钟过期，防止缓存雪崩
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        opsForValue.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);
        // 写入本地缓存
        LOCAL_CACHE.put(cacheKey, cacheValue);
        // 获取封装类
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 编辑图片（给用户使用）
     */
    @PostMapping("/edit")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureService.editPicture(pictureEditRequest, loginUser);
        return ResultUtils.success(true);
    }

    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("鸟巢", "异常绝缘子", "气球", "农田", "建筑", "电线塔");
        List<String> categoryList = Arrays.asList("无人机影像", "卫星影像");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 审核图片
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 批量抓取并创建图片
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadPictureByBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                                      HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }

    /**
     * 按照颜色搜索
     */
    @PostMapping("/search/color")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        String picColor = searchPictureByColorRequest.getPicColor();
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> pictureVOList = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(pictureVOList);
    }

    /**
     * 批量编辑图片
     */
    @PostMapping("/edit/batch")
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }

}
