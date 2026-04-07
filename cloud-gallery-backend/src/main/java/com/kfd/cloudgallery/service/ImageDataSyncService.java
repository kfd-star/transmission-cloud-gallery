package com.kfd.cloudgallery.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kfd.cloudgallery.model.entity.ImageDataSync;

/**
 * 图像数据同步服务
 */
public interface ImageDataSyncService extends IService<ImageDataSync> {

    /**
     * 同步PostgreSQL数据到MySQL
     * @return 同步的数据条数
     */
    int syncDataFromPostgres();

    /**
     * 获取最后同步的ID
     * @return 最后同步的ID
     */
    Integer getLastSyncId();

    /**
     * 获取总同步数量
     * @return 总同步数量
     */
    Long getTotalSyncCount();
}
