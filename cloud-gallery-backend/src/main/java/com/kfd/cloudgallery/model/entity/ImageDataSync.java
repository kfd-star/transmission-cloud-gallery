package com.kfd.cloudgallery.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * PostgreSQL image_data表同步数据
 * @TableName image_data_sync
 */
@TableName(value = "image_data_sync")
@Data
public class ImageDataSync implements Serializable {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * PostgreSQL中的原始ID
     */
    @TableField("original_id")
    private Integer originalId;

    /**
     * 设备令牌
     */
    @TableField("device_token")
    private String deviceToken;

    /**
     * 图像名称
     */
    @TableField("image_name")
    private String imageName;

    /**
     * 图像URL
     */
    @TableField("image_url")
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
    @TableField("file_size")
    private Long fileSize;

    /**
     * 检测结果JSON
     */
    private String detections;

    /**
     * 元数据JSON
     */
    private String metadata;

    /**
     * 地理边界JSON
     */
    @TableField("geo_boundary")
    private String geoBoundary;

    /**
     * Mars3D数据JSON
     */
    @TableField("mars3d_data")
    private String mars3dData;

    /**
     * 同步时间
     */
    @TableField("sync_time")
    private Date syncTime;

    /**
     * 同步状态：1-成功，0-失败
     */
    @TableField("sync_status")
    private Integer syncStatus;

    /**
     * 同步错误信息
     */
    @TableField("sync_error")
    private String syncError;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
