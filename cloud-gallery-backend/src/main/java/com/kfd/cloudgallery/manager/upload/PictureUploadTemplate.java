package com.kfd.cloudgallery.manager.upload;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.kfd.cloudgallery.exception.BusinessException;
import com.kfd.cloudgallery.exception.ErrorCode;
import com.kfd.cloudgallery.manager.LocalFileManager;
import com.kfd.cloudgallery.model.dto.file.UploadPictureResult;
import com.kfd.cloudgallery.utils.ImageProcessUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.Map;

/**
 * 图片上传模板
 */
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    private LocalFileManager localFileManager;

    /**
     * 上传图片到本地存储
     *
     * @param inputSource      文件
     * @param uploadPathPrefix 上传路径前缀
     * @return
     */
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1. 校验图片
        validPicture(inputSource);
        
        // 2. 生成文件名
        String uuid = RandomUtil.randomString(16);
        String originalFilename = getOriginFilename(inputSource);
        String extension = FileUtil.getSuffix(originalFilename);
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid, extension);
        
        File tempFile = null;
        try {
            // 3. 创建临时文件，获取文件到服务器
            tempFile = File.createTempFile("upload_", "." + extension);
            // 处理文件来源
            processFile(inputSource, tempFile);
            
            // 4. 获取图片信息
            Map<String, Object> imageInfo = ImageProcessUtils.getImageInfo(tempFile);
            if (imageInfo.isEmpty()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法读取图片信息");
            }
            
            // 5. 保存原图到本地
            String originalUrl = localFileManager.saveFile(tempFile, uploadPathPrefix, uploadFilename);
            
            // 6. 生成缩略图
            String thumbnailFilename = "thumb_" + uploadFilename;
            File thumbnailFile = new File(System.getProperty("java.io.tmpdir"), thumbnailFilename);
            boolean thumbnailGenerated = ImageProcessUtils.generateThumbnail(tempFile, thumbnailFile, 300, 300);
            String thumbnailUrl = originalUrl; // 默认使用原图
            if (thumbnailGenerated) {
                thumbnailUrl = localFileManager.saveFile(thumbnailFile, uploadPathPrefix, thumbnailFilename);
                // 清理临时缩略图文件
                thumbnailFile.delete();
            }
            
            // 7. 封装返回结果
            return buildResult(originalFilename, originalUrl, thumbnailUrl, imageInfo);
            
        } catch (Exception e) {
            log.error("图片上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败: " + e.getMessage());
        } finally {
            // 8. 清理临时文件
            this.deleteTempFile(tempFile);
        }
    }

    /**
     * 校验输入源（本地文件或 URL）
     */
    protected abstract void validPicture(Object inputSource);

    /**
     * 获取输入源的原始文件名
     */
    protected abstract String getOriginFilename(Object inputSource);

    /**
     * 处理输入源并生成本地临时文件
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;

    /**
     * 封装返回结果
     *
     * @param originalFilename 原始文件名
     * @param originalUrl      原图URL
     * @param thumbnailUrl     缩略图URL
     * @param imageInfo        图片信息
     * @return
     */
    private UploadPictureResult buildResult(String originalFilename, String originalUrl, String thumbnailUrl, Map<String, Object> imageInfo) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        
        // 设置URL
        uploadPictureResult.setUrl(originalUrl);
        uploadPictureResult.setThumbnailUrl(thumbnailUrl);
        
        // 设置基本信息
        uploadPictureResult.setPicName(FileUtil.mainName(originalFilename));
        
        // 设置图片信息
        uploadPictureResult.setPicWidth((Integer) imageInfo.getOrDefault("width", 0));
        uploadPictureResult.setPicHeight((Integer) imageInfo.getOrDefault("height", 0));
        uploadPictureResult.setPicScale((Double) imageInfo.getOrDefault("scale", 1.0));
        uploadPictureResult.setPicSize((Long) imageInfo.getOrDefault("fileSize", 0L));
        uploadPictureResult.setPicFormat((String) imageInfo.getOrDefault("format", "jpg"));
        uploadPictureResult.setPicColor((String) imageInfo.getOrDefault("dominantColor", "#FFFFFF"));
        
        log.info("图片上传成功: {} -> {}", originalFilename, originalUrl);
        return uploadPictureResult;
    }

    /**
     * 清理临时文件
     *
     * @param file
     */
    public void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        // 删除临时文件
        boolean deleteResult = file.delete();
        if (!deleteResult) {
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }
    }
}













