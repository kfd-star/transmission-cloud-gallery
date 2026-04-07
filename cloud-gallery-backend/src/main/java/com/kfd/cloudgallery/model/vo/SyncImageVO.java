package com.kfd.cloudgallery.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 同步图像VO
 */
@Data
public class SyncImageVO implements Serializable {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * PostgreSQL中的原始ID
     */
    private Integer originalId;

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
    private String geoBoundary;

    /**
     * Mars3D数据JSON
     */
    private String mars3dData;

    /**
     * 同步时间
     */
    private Date syncTime;

    /**
     * 同步状态：1-成功，0-失败
     */
    private Integer syncStatus;

    private static final long serialVersionUID = 1L;
}
