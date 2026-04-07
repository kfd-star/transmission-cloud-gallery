package com.kfd.cloudgallery.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 图片处理工具类
 */
@Slf4j
public class ImageProcessUtils {

    /**
     * 获取图片信息
     *
     * @param imageFile 图片文件
     * @return 图片信息Map
     */
    public static Map<String, Object> getImageInfo(File imageFile) {
        Map<String, Object> imageInfo = new HashMap<>();
        
        try {
            BufferedImage bufferedImage = ImageIO.read(imageFile);
            if (bufferedImage == null) {
                log.warn("无法读取图片: {}", imageFile.getAbsolutePath());
                return imageInfo;
            }

            // 获取基本信息
            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            double scale = NumberUtil.round(width * 1.0 / height, 2).doubleValue();
            long fileSize = FileUtil.size(imageFile);
            String format = FileUtil.getSuffix(imageFile.getName()).toLowerCase();

            imageInfo.put("width", width);
            imageInfo.put("height", height);
            imageInfo.put("scale", scale);
            imageInfo.put("fileSize", fileSize);
            imageInfo.put("format", format);

            // 获取主色调
            String dominantColor = getDominantColor(bufferedImage);
            imageInfo.put("dominantColor", dominantColor);

            log.info("图片信息提取成功: {}x{}, 格式: {}, 大小: {}KB", 
                width, height, format, fileSize / 1024);

        } catch (IOException e) {
            log.error("获取图片信息失败: {}", e.getMessage());
        }

        return imageInfo;
    }

    /**
     * 生成缩略图
     *
     * @param sourceFile 源文件
     * @param targetFile 目标文件
     * @param maxWidth   最大宽度
     * @param maxHeight  最大高度
     * @return 是否成功
     */
    public static boolean generateThumbnail(File sourceFile, File targetFile, int maxWidth, int maxHeight) {
        try {
            BufferedImage sourceImage = ImageIO.read(sourceFile);
            if (sourceImage == null) {
                return false;
            }

            // 计算缩略图尺寸
            int sourceWidth = sourceImage.getWidth();
            int sourceHeight = sourceImage.getHeight();
            
            int thumbnailWidth = sourceWidth;
            int thumbnailHeight = sourceHeight;

            // 按比例缩放
            if (sourceWidth > maxWidth || sourceHeight > maxHeight) {
                double widthRatio = (double) maxWidth / sourceWidth;
                double heightRatio = (double) maxHeight / sourceHeight;
                double ratio = Math.min(widthRatio, heightRatio);

                thumbnailWidth = (int) (sourceWidth * ratio);
                thumbnailHeight = (int) (sourceHeight * ratio);
            }

            // 创建缩略图
            BufferedImage thumbnail = new BufferedImage(thumbnailWidth, thumbnailHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = thumbnail.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(sourceImage, 0, 0, thumbnailWidth, thumbnailHeight, null);
            g2d.dispose();

            // 保存缩略图
            String format = FileUtil.getSuffix(targetFile.getName());
            ImageIO.write(thumbnail, format, targetFile);

            log.info("缩略图生成成功: {}x{} -> {}x{}", 
                sourceWidth, sourceHeight, thumbnailWidth, thumbnailHeight);
            return true;

        } catch (IOException e) {
            log.error("生成缩略图失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 压缩图片
     *
     * @param sourceFile 源文件
     * @param targetFile 目标文件
     * @param quality    压缩质量 (0.0-1.0)
     * @return 是否成功
     */
    public static boolean compressImage(File sourceFile, File targetFile, float quality) {
        try {
            BufferedImage sourceImage = ImageIO.read(sourceFile);
            if (sourceImage == null) {
                return false;
            }

            // 创建压缩后的图片
            BufferedImage compressedImage = new BufferedImage(
                sourceImage.getWidth(), 
                sourceImage.getHeight(), 
                BufferedImage.TYPE_INT_RGB
            );

            Graphics2D g2d = compressedImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(sourceImage, 0, 0, null);
            g2d.dispose();

            // 保存压缩图片
            String format = FileUtil.getSuffix(targetFile.getName());
            ImageIO.write(compressedImage, format, targetFile);

            log.info("图片压缩成功: 质量 {}", quality);
            return true;

        } catch (IOException e) {
            log.error("图片压缩失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取图片主色调
     *
     * @param bufferedImage 图片对象
     * @return 主色调十六进制值
     */
    private static String getDominantColor(BufferedImage bufferedImage) {
        try {
            int width = bufferedImage.getWidth();
            int height = bufferedImage.getHeight();
            
            // 采样间隔，避免处理所有像素
            int sampleStep = Math.max(1, Math.max(width, height) / 100);
            
            long totalR = 0, totalG = 0, totalB = 0;
            int sampleCount = 0;

            for (int x = 0; x < width; x += sampleStep) {
                for (int y = 0; y < height; y += sampleStep) {
                    Color color = new Color(bufferedImage.getRGB(x, y));
                    totalR += color.getRed();
                    totalG += color.getGreen();
                    totalB += color.getBlue();
                    sampleCount++;
                }
            }

            if (sampleCount > 0) {
                int avgR = (int) (totalR / sampleCount);
                int avgG = (int) (totalG / sampleCount);
                int avgB = (int) (totalB / sampleCount);
                
                return String.format("#%02X%02X%02X", avgR, avgG, avgB);
            }

        } catch (Exception e) {
            log.error("获取主色调失败: {}", e.getMessage());
        }

        return "#FFFFFF"; // 默认白色
    }

    /**
     * 验证图片格式
     *
     * @param filename 文件名
     * @return 是否为支持的图片格式
     */
    public static boolean isValidImageFormat(String filename) {
        if (filename == null) {
            return false;
        }
        
        String extension = FileUtil.getSuffix(filename).toLowerCase();
        return "jpg".equals(extension) || "jpeg".equals(extension) || 
               "png".equals(extension) || "gif".equals(extension) || 
               "webp".equals(extension) || "bmp".equals(extension);
    }
}
