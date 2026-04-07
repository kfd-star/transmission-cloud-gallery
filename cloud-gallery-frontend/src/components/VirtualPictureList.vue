<template>
  <div class="virtual-picture-list" ref="containerRef" @scroll="handleScroll">
    <div :style="{ height: totalHeight + 'px', position: 'relative' }">
      <div
        v-for="item in visibleItems"
        :key="item.picture.id"
        :style="{
          position: 'absolute',
          top: item.top + 'px',
          left: item.left + 'px',
          width: itemWidth + 'px',
          height: itemHeight + 'px'
        }"
      >
        <a-card hoverable @click="doClickPicture(item.picture)">
          <template #cover>
            <img
              :alt="item.picture.name"
              :src="item.picture.thumbnailUrl ?? item.picture.url"
              loading="lazy"
              style="height: 220px; object-fit: contain; background-color: #f5f5f5;"
              @error="handleImageError"
            />
          </template>
          <a-card-meta :title="item.picture.name">
            <template #description>
              <a-flex>
                <a-tag color="green">
                  {{ item.picture.category ?? '默认' }}
                </a-tag>
                <a-tag v-for="tag in item.picture.tags" :key="tag">
                  {{ tag }}
                </a-tag>
              </a-flex>
            </template>
          </a-card-meta>
        </a-card>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

interface Props {
  dataList?: API.PictureVO[]
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  dataList: () => [],
  loading: false,
})

const router = useRouter()
const containerRef = ref<HTMLElement>()
const scrollTop = ref(0)

// 虚拟滚动配置
const itemWidth = 240
const itemHeight = 280
const itemsPerRow = computed(() => Math.floor((containerRef.value?.clientWidth || 1200) / itemWidth))
const totalHeight = computed(() => Math.ceil(props.dataList.length / itemsPerRow.value) * itemHeight)

// 计算可见项目
const visibleItems = computed(() => {
  if (!containerRef.value) return []
  
  const containerHeight = containerRef.value.clientHeight
  const startRow = Math.floor(scrollTop.value / itemHeight)
  const endRow = Math.ceil((scrollTop.value + containerHeight) / itemHeight)
  
  const items = []
  for (let row = startRow; row < endRow; row++) {
    for (let col = 0; col < itemsPerRow.value; col++) {
      const index = row * itemsPerRow.value + col
      if (index < props.dataList.length) {
        items.push({
          picture: props.dataList[index],
          top: row * itemHeight,
          left: col * itemWidth
        })
      }
    }
  }
  return items
})

// 滚动处理
const handleScroll = (event: Event) => {
  const target = event.target as HTMLElement
  scrollTop.value = target.scrollTop
}

// 点击图片
const doClickPicture = (picture: API.PictureVO) => {
  router.push({
    path: `/picture/${picture.id}`,
  })
}

// 图片错误处理
const handleImageError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSIjZjVmNWY1Ii8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJBcmlhbCIgZm9udC1zaXplPSIxNCIgZmlsbD0iIzk5OSIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZHk9Ii4zZW0iPuWbvueJh+WKoOi9veWksei0pTwvdGV4dD48L3N2Zz4='
}
</script>

<style scoped>
.virtual-picture-list {
  height: 600px;
  overflow-y: auto;
  overflow-x: hidden;
}
</style>
