package com.kfd.cloudgallery.model.dto.postgres;

import lombok.Data;

import java.io.Serializable;

/**
 * PostgreSQL image_data表数据模型
 */
@Data
public class PostgresImageData implements Serializable {
    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 设备令牌
     */
    private String deviceToken;

    /**
     * 图像名称
     */
    private String imageName;

    /**
     * 图像URL
     */
    private String imageUrl;

    /**
     * 图像宽度
     */
    private Integer width;

    /**
     * 图像高度
     */
    private Integer height;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 检测结果JSON字符串
     */
    private String detections;

    /**
     * 元数据JSON字符串
     */
    private String metadata;

    /**
     * 地理边界JSON字符串
     */
    private String geoBoundary;

    /**
     * Mars3D数据JSON字符串
     */
    private String mars3dData;

    /**
     * 时间戳
     */
    private String timestamp;

    private static final long serialVersionUID = 1L;
}
