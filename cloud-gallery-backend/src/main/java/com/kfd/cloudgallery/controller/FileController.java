package com.kfd.cloudgallery.controller;

import com.kfd.cloudgallery.manager.LocalFileManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件访问控制器
 */
@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    @Value("${local.upload.path:./uploads}")
    private String uploadPath;

    @Resource
    private LocalFileManager localFileManager;

    /**
     * 获取文件
     *
     * @param request HTTP请求
     * @return 文件资源
     */
    @GetMapping("/**")
    public ResponseEntity<org.springframework.core.io.Resource> getFile(HttpServletRequest request) {
        try {
            // 从请求路径中提取文件路径
            String requestPath = request.getRequestURI();
            String contextPath = request.getContextPath();
            
            // 移除context-path和/file前缀
            String filePath = requestPath;
            if (contextPath != null && !contextPath.isEmpty()) {
                filePath = filePath.substring(contextPath.length());
            }
            if (filePath.startsWith("/file/")) {
                filePath = filePath.substring("/file/".length());
            } else if (filePath.startsWith("/file")) {
                filePath = filePath.substring("/file".length());
            }
            
            // 构建完整文件路径
            String fullPath = uploadPath + "/" + filePath;
            
            log.info("文件访问请求: requestPath={}, contextPath={}, filePath={}, fullPath={}", 
                requestPath, contextPath, filePath, fullPath);
            
            File file = new File(fullPath);
            if (!file.exists() || !file.isFile()) {
                log.warn("文件不存在: {}", fullPath);
                return ResponseEntity.notFound().build();
            }

            // 检查文件是否在允许的目录内（安全校验）
            Path filePathObj = file.toPath().normalize();
            Path uploadDir = Paths.get(uploadPath).normalize();
            if (!filePathObj.startsWith(uploadDir)) {
                log.warn("非法文件访问: {}", fullPath);
                return ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource resource = new FileSystemResource(file);
            
            // 根据文件扩展名设置Content-Type
            String contentType = Files.probeContentType(filePathObj);
            if (contentType == null) {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (fileName.endsWith(".png")) {
                    contentType = "image/png";
                } else if (fileName.endsWith(".gif")) {
                    contentType = "image/gif";
                } else if (fileName.endsWith(".webp")) {
                    contentType = "image/webp";
                } else {
                    contentType = "application/octet-stream";
                }
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600") // 缓存1小时
                    .body(resource);

        } catch (Exception e) {
            log.error("获取文件失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除文件
     *
     * @param path 文件路径
     * @return 删除结果
     */
    @DeleteMapping("/**")
    public ResponseEntity<String> deleteFile(@RequestParam String path) {
        try {
            if (path == null || path.isEmpty()) {
                return ResponseEntity.badRequest().body("文件路径不能为空");
            }

            // 安全校验
            Path filePath = Paths.get(uploadPath, path).normalize();
            Path uploadDir = Paths.get(uploadPath).normalize();
            if (!filePath.startsWith(uploadDir)) {
                return ResponseEntity.badRequest().body("非法文件路径");
            }

            File file = filePath.toFile();
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }

            boolean deleted = file.delete();
            if (deleted) {
                log.info("文件删除成功: {}", path);
                return ResponseEntity.ok("文件删除成功");
            } else {
                log.error("文件删除失败: {}", path);
                return ResponseEntity.internalServerError().body("文件删除失败");
            }

        } catch (Exception e) {
            log.error("删除文件失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("删除文件失败: " + e.getMessage());
        }
    }

    /**
     * 图片代理接口 - 用于解决CORS跨域问题
     * 代理外部图片URL，由后端服务器获取图片并返回给前端
     *
     * @param imageUrl 外部图片URL
     * @return 图片资源
     */
    @GetMapping("/proxy")
    public ResponseEntity<byte[]> proxyImage(@RequestParam String imageUrl) {
        try {
            if (imageUrl == null || imageUrl.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            log.info("图片代理请求: {}", imageUrl);

            // 创建URL连接
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000); // 10秒连接超时
            connection.setReadTimeout(30000); // 30秒读取超时
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            // 获取响应码
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                log.warn("图片代理失败，响应码: {}, URL: {}", responseCode, imageUrl);
                return ResponseEntity.status(responseCode).build();
            }

            // 读取图片数据
            InputStream inputStream = connection.getInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            byte[] imageData = outputStream.toByteArray();
            inputStream.close();
            outputStream.close();
            connection.disconnect();

            // 根据URL确定Content-Type
            String contentType = "image/jpeg"; // 默认
            String urlLower = imageUrl.toLowerCase();
            if (urlLower.endsWith(".png")) {
                contentType = "image/png";
            } else if (urlLower.endsWith(".gif")) {
                contentType = "image/gif";
            } else if (urlLower.endsWith(".webp")) {
                contentType = "image/webp";
            } else if (urlLower.endsWith(".jpg") || urlLower.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            }

            log.info("图片代理成功: {}, 大小: {} bytes", imageUrl, imageData.length);

            // 返回图片数据
            // 注意：不要在这里设置CORS头，由全局CorsConfig统一处理
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600") // 缓存1小时
                    .body(imageData);

        } catch (Exception e) {
            log.error("图片代理失败: {}, 错误: {}", imageUrl, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}