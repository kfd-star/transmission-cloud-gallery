<template>
  <div id="homePage">
    <!-- 搜索框 -->
    <div class="search-bar">
      <a-input-search
        v-model:value="searchParams.searchText"
        placeholder="从海量图片中搜索"
        enter-button="搜索"
        size="large"
        @search="doSearch"
      />
    </div>
    <!-- 分类和标签筛选 -->
    <a-tabs v-model:active-key="selectedCategory" @change="onCategoryChange">
      <a-tab-pane key="all" tab="全部" />
      <a-tab-pane v-for="category in categoryList" :tab="category" :key="category">
        <!-- 在Tab内容区域显示同步按钮，只在无人机影像分类下显示 -->
        <div v-if="category === '无人机影像'" style="margin-bottom: 16px; padding: 12px; background: #f5f5f5; border-radius: 4px; border: 1px solid #d9d9d9;">
          <div style="display: flex; align-items: center; gap: 16px; flex-wrap: nowrap;">
            <a-button 
              type="primary" 
              @click="handleSyncData"
              :loading="syncLoading"
              title="同步数据"
            >
              同步数据
            </a-button>
            <span style="color: #666; font-size: 12px; white-space: nowrap;">
              从PostgreSQL同步图像数据到MySQL
            </span>
            <span v-if="syncStats.lastSyncCount !== undefined" style="font-size: 12px; color: #666; white-space: nowrap;">
              本次同步：<span style="color: #1890ff; font-weight: bold;">{{ syncStats.lastSyncCount }}</span> 条数据
            </span>
            <span v-if="syncStats.totalSyncCount !== undefined" style="font-size: 12px; color: #666; white-space: nowrap;">
              共同步：<span style="color: #52c41a; font-weight: bold;">{{ syncStats.totalSyncCount }}</span> 条数据
            </span>
          </div>
        </div>
      </a-tab-pane>
    </a-tabs>
    <div class="tag-bar">
      <span style="margin-right: 8px">标签：</span>
      <a-space :size="[0, 8]" wrap>
        <a-checkable-tag
          v-for="(tag, index) in currentTagList"
          :key="tag"
          v-model:checked="selectedTagList[index]"
          @change="doSearch"
        >
          {{ tag }}
        </a-checkable-tag>
      </a-space>
    </div>
    <!-- 图片列表 -->
    <PictureList
      v-if="!showSyncImages"
      :dataList="dataList"
      :loading="loading"
    />
    
    <!-- 无人机影像分类：只显示同步图像，使用PictureList组件保持一致性 -->
    <div v-if="selectedCategory === '无人机影像' && showSyncImages">
      <PictureList 
        :dataList="syncImageList"
        :loading="loading"
      />
      
      <!-- 分页 -->
      <a-pagination
        style="text-align: right; margin-top: 16px;"
        v-model:current="searchParams.current"
        v-model:page-size="searchParams.pageSize"
        :total="syncImageTotal"
        :show-size-changer="true"
        :show-quick-jumper="true"
        :page-size-options="['12', '20', '50', '100']"
        @change="fetchSyncImageData"
      />
    </div>
    <!-- 分页 -->
    <a-pagination
      v-if="!showSyncImages"
      style="text-align: right"
      v-model:current="searchParams.current"
      v-model:page-size="searchParams.pageSize"
      :total="total"
      :show-size-changer="true"
      :show-quick-jumper="true"
      :page-size-options="['12', '20', '50', '100']"
      @change="onPageChange"
    />
    
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  listPictureTagCategoryUsingGet,
  listPictureVoByPageUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import PictureList from '@/components/PictureList.vue'
import { syncImageDataUsingPost, getSyncStatusUsingGet } from '@/api/imageDataSyncController.ts'
import request from '@/request'

const router = useRouter()

// 定义数据
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(true)
const syncLoading = ref(false)

// 同步统计数据
const syncStats = ref({
  lastSyncCount: undefined as number | undefined,
  totalSyncCount: undefined as number | undefined
})

// 同步图像数据
const syncImageList = ref<API.PictureVO[]>([])
const syncImageTotal = ref(0)
const showSyncImages = ref(false)

// 搜索条件
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 20, // 适中的默认页面大小，平衡性能和用户体验
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const params = {
    ...searchParams,
    tags: [] as string[],
  }
  if (selectedCategory.value !== 'all') {
    params.category = selectedCategory.value
  }
  // [true, false, false] => ['java']
  selectedTagList.value.forEach((useTag, index) => {
    if (useTag) {
      params.tags.push(currentTagList.value[index])
    }
  })
  const res = await listPictureVoByPageUsingPost(params)
  if (res.data.code === 0 && res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = Number(res.data.data.total) ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
  loading.value = false
}

// 页面加载时获取数据，请求一次
onMounted(() => {
  // 如果默认分类是'all'，使用混合数据API
  if (selectedCategory.value === 'all') {
    fetchMixedData()
  } else {
    fetchData()
  }
})

// 分页参数
const onPageChange = (page: number, pageSize: number) => {
  searchParams.current = page
  searchParams.pageSize = pageSize
  
  // 根据当前分类决定使用哪种数据获取方式
  if (selectedCategory.value === 'all') {
    fetchMixedData()
  } else if (selectedCategory.value === '无人机影像') {
    fetchSyncImageData()
  } else {
    fetchData()
  }
}

// 搜索
const doSearch = () => {
  // 重置搜索条件
  searchParams.current = 1
  
  // 根据当前分类决定使用哪种数据获取方式
  if (selectedCategory.value === 'all') {
    fetchMixedData()
  } else if (selectedCategory.value === '无人机影像') {
    fetchSyncImageData()
  } else {
    fetchData()
  }
}

// 标签和分类列表
const categoryList = ref<string[]>(['无人机影像', '卫星影像'])
const selectedCategory = ref<string>('all')
const tagList = ref<string[]>([])
const selectedTagList = ref<boolean[]>([])
const currentTagList = ref<string[]>([])

// 定义分类对应的标签
const categoryTagMap = {
  '无人机影像': ['鸟巢', '异常绝缘子', '气球'],
  '卫星影像': ['农田', '建筑', '电线塔']
}

// 分类变化时的处理
const onCategoryChange = (category: string) => {
  selectedCategory.value = category
  // 重置标签选择
  selectedTagList.value = []
  
  // 根据分类更新当前显示的标签
  if (category === 'all') {
    // 全部分类显示所有标签
    currentTagList.value = ['鸟巢', '异常绝缘子', '气球', '农田', '建筑', '电线塔']
  } else {
    currentTagList.value = categoryTagMap[category] || []
  }
  
  // 根据分类决定显示内容
  if (category === 'all') {
    // 全部：显示混合图像（普通图像 + 同步图像）
    showSyncImages.value = false
    fetchMixedData()
  } else if (category === '无人机影像') {
    // 无人机影像：只显示同步图像
    showSyncImages.value = true
    fetchSyncImageData()
  } else {
    // 其他分类：只显示普通图像
    showSyncImages.value = false
    doSearch()
  }
}

/**
 * 获取标签和分类选项
 * @param values
 */
const getTagCategoryOptions = async () => {
  // 不再从后端获取，使用前端定义的分类和标签
  // const res = await listPictureTagCategoryUsingGet()
  // if (res.data.code === 0 && res.data.data) {
  //   tagList.value = res.data.data.tagList ?? []
  //   categoryList.value = res.data.data.categoryList ?? []
  // } else {
  //   message.error('获取标签分类列表失败，' + res.data.message)
  // }
}

/**
 * 同步数据
 */
const handleSyncData = async () => {
  try {
    syncLoading.value = true
    message.loading('正在同步数据...', 0)

    const res = await syncImageDataUsingPost()
    if (res.data.code === 0) {
      const syncCount = res.data.data?.syncCount || 0
      message.destroy()
      message.success(`同步完成，共同步 ${syncCount} 条记录`)

      // 更新同步统计数据
      syncStats.value.lastSyncCount = syncCount
      
      // 重新获取总同步数量（真实数据）
      await getSyncStatus()

      // 同步完成后刷新数据
      fetchData()
    } else {
      message.destroy()
      message.error('同步失败：' + res.data.message)
    }
  } catch (error) {
    message.destroy()
    message.error('同步失败：' + (error as Error).message)
  } finally {
    syncLoading.value = false
  }
}

/**
 * 获取同步状态
 */
const getSyncStatus = async () => {
  try {
    const res = await getSyncStatusUsingGet()
    if (res.data.code === 0) {
      // 设置真实的统计数据
      syncStats.value.totalSyncCount = res.data.data?.totalSyncCount || 0
      console.log('获取同步状态成功:', res.data.data)
    }
  } catch (error) {
    console.log('获取同步状态失败:', error)
  }
}

/**
 * 调用混合图像API
 */
const fetchMixedImages = async (params: any) => {
  return request<API.BaseResponsePagePictureVO_>('/api/picture/list/page/vo/mixed', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: params,
  })
}

/**
 * 获取混合图像数据（普通图像 + 同步图像）
 */
const fetchMixedData = async () => {
  try {
    loading.value = true
    const params = {
      current: searchParams.current,
      pageSize: searchParams.pageSize,
      searchText: searchParams.searchText,
      tags: [] as string[]
    }
    
    // 添加标签筛选参数
    selectedTagList.value.forEach((useTag, index) => {
      if (useTag) {
        params.tags.push(currentTagList.value[index])
      }
    })
    
    // 使用混合API获取所有图像
    const res = await fetchMixedImages(params)
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data.records ?? []
      total.value = Number(res.data.data.total) ?? 0
    } else {
      message.error('获取图像数据失败，' + res.data.message)
    }
  } catch (error) {
    message.error('获取图像数据失败：' + (error as Error).message)
  } finally {
    loading.value = false
  }
}

/**
 * 获取同步图像数据（使用混合API，但只显示同步图像部分）
 */
const fetchSyncImageData = async () => {
  try {
    loading.value = true
    const params = {
      current: searchParams.current,
      pageSize: searchParams.pageSize,
      searchText: searchParams.searchText,
      tags: [] as string[],
      category: '无人机影像' // 指定只返回无人机影像
    }
    
    // 添加标签筛选参数
    selectedTagList.value.forEach((useTag, index) => {
      if (useTag) {
        params.tags.push(currentTagList.value[index])
      }
    })
    
    // 使用混合API获取所有图像
    const res = await fetchMixedImages(params)
    if (res.data.code === 0 && res.data.data) {
      // 后端已经过滤了同步图像，直接使用
      syncImageList.value = res.data.data.records ?? []
      syncImageTotal.value = Number(res.data.data.total) ?? 0
    } else {
      message.error('获取同步图像数据失败，' + res.data.message)
    }
  } catch (error) {
    message.error('获取同步图像数据失败：' + (error as Error).message)
  } finally {
    loading.value = false
  }
}

/**
 * 格式化文件大小
 */
const formatFileSize = (size: number | undefined) => {
  if (!size) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let unitIndex = 0
  let fileSize = size
  
  while (fileSize >= 1024 && unitIndex < units.length - 1) {
    fileSize /= 1024
    unitIndex++
  }
  
  return `${fileSize.toFixed(1)} ${units[unitIndex]}`
}

/**
 * 格式化日期
 */
const formatDate = (date: Date | string | undefined) => {
  if (!date) return '-'
  const d = new Date(date)
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

/**
 * 处理图片加载错误
 */
const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSIjZjVmNWY1Ii8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJBcmlhbCIgZm9udC1zaXplPSIxNCIgZmlsbD0iIzk5OSIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPuWbvueJh+WKoOi9veWksei0pTwvdGV4dD48L3N2Zz4='
}

/**
 * 点击同步图像跳转到详情页
 */
const doClickSyncImage = (picture: API.PictureVO) => {
  // 直接使用原始ID，因为picture表和image_data_sync表的ID不会冲突
  router.push({
    path: `/picture/${picture.id}`,
  })
}

onMounted(() => {
  getTagCategoryOptions()
  // 初始化时显示所有标签
  currentTagList.value = ['鸟巢', '异常绝缘子', '气球', '农田', '建筑', '电线塔']
  // 根据默认分类决定使用哪种数据获取方式
  if (selectedCategory.value === 'all') {
    fetchMixedData()
  } else {
    fetchData()
  }
  // 获取同步状态
  getSyncStatus()
})
</script>

<style scoped>
#homePage {
  margin-bottom: 16px;
}

.sync-image-card {
  height: 100%;
}

.sync-image-card .ant-card-body {
  padding: 8px;
}

.sync-image-card .ant-card-meta-title {
  margin-bottom: 4px;
}

.sync-image-card .ant-card-meta-description {
  font-size: 11px;
  color: #666;
}

#homePage .search-bar {
  max-width: 480px;
  margin: 0 auto 16px;
}

#homePage .tag-bar {
  margin-bottom: 16px;
}
</style>
