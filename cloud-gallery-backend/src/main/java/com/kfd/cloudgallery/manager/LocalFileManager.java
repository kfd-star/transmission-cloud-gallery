package com.kfd.cloudgallery.manager;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.kfd.cloudgallery.exception.BusinessException;
import com.kfd.cloudgallery.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地文件管理器
 */
@Slf4j
@Component
public class LocalFileManager {

    @Value("${local.upload.path:./uploads}")
    private String uploadPath;

    @Value("${local.upload.url:http://localhost:8123/api/file}")
    private String baseUrl;

    @PostConstruct
    public void init() {
        // 确保上传目录存在
        try {
            Path path = Paths.get(uploadPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("创建上传目录: {}", uploadPath);
            }
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件系统初始化失败");
        }
    }

    /**
     * 保存文件到本地
     *
     * @param file         上传的文件
     * @param relativePath 相对路径（不包含文件名）
     * @return 文件访问URL
     */
    public String saveFile(MultipartFile file, String relativePath) {
        try {
            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = FileUtil.getSuffix(originalFilename);
            String filename = UUID.randomUUID().toString() + "." + extension;

            // 构建完整路径
            String fullPath = uploadPath + "/" + relativePath + "/" + filename;
            File targetFile = new File(fullPath);

            // 确保目录存在
            File parentDir = targetFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 保存文件
            file.transferTo(targetFile);

            // 返回访问URL
            String accessUrl = baseUrl + "/" + relativePath + "/" + filename;
            log.info("文件保存成功: {} -> {}", fullPath, accessUrl);
            return accessUrl;

        } catch (IOException e) {
            log.error("文件保存失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败");
        }
    }

    /**
     * 保存文件到本地（重载方法，支持直接文件对象）
     *
     * @param sourceFile   源文件
     * @param relativePath 相对路径（不包含文件名）
     * @param filename     目标文件名
     * @return 文件访问URL
     */
    public String saveFile(File sourceFile, String relativePath, String filename) {
        try {
            // 构建完整路径
            String fullPath = uploadPath + "/" + relativePath + "/" + filename;
            File targetFile = new File(fullPath);

            // 确保目录存在
            File parentDir = targetFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 复制文件
            FileUtil.copy(sourceFile, targetFile, true);

            // 返回访问URL
            String accessUrl = baseUrl + "/" + relativePath + "/" + filename;
            log.info("文件保存成功: {} -> {}", fullPath, accessUrl);
            return accessUrl;

        } catch (Exception e) {
            log.error("文件保存失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件保存失败");
        }
    }

    /**
     * 删除文件
     *
     * @param relativePath 相对路径
     * @param filename     文件名
     * @return 是否删除成功
     */
    public boolean deleteFile(String relativePath, String filename) {
        try {
            String fullPath = uploadPath + "/" + relativePath + "/" + filename;
            File file = new File(fullPath);
            if (file.exists()) {
                boolean deleted = file.delete();
                log.info("文件删除: {} -> {}", fullPath, deleted ? "成功" : "失败");
                return deleted;
            }
            return true; // 文件不存在，认为删除成功
        } catch (Exception e) {
            log.error("文件删除失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取文件大小
     *
     * @param relativePath 相对路径
     * @param filename     文件名
     * @return 文件大小（字节）
     */
    public long getFileSize(String relativePath, String filename) {
        try {
            String fullPath = uploadPath + File.separator + relativePath + File.separator + filename;
            File file = new File(fullPath);
            return file.exists() ? file.length() : 0;
        } catch (Exception e) {
            log.error("获取文件大小失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 检查文件是否存在
     *
     * @param relativePath 相对路径
     * @param filename     文件名
     * @return 文件是否存在
     */
    public boolean fileExists(String relativePath, String filename) {
        try {
            String fullPath = uploadPath + File.separator + relativePath + File.separator + filename;
            File file = new File(fullPath);
            return file.exists();
        } catch (Exception e) {
            log.error("检查文件存在性失败: {}", e.getMessage());
            return false;
        }
    }
}
