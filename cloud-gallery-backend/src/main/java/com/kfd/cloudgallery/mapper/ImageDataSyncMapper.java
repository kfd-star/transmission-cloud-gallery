package com.kfd.cloudgallery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kfd.cloudgallery.model.entity.ImageDataSync;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图像数据同步Mapper
 */
@Mapper
public interface ImageDataSyncMapper extends BaseMapper<ImageDataSync> {
}
