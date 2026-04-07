<template>
  <div class="mars3d-viewer-container">
    <div ref="mapContainer" class="mars3d-map-container"></div>
    <div v-if="loading" class="loading-overlay">
      <a-spin size="large" tip="正在加载3D地球..." />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'

// 声明全局类型
declare global {
  interface Window {
    mars3d: any
    Cesium: any
  }
}

interface Props {
  imageUrl: string
  geoBoundary: string | null
  imageName?: string
  showTransition?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showTransition: true,
})

const mapContainer = ref<HTMLElement>()
const loading = ref(true)
let mapInstance: any = null
let imageryLayer: any = null

// 初始化Mars3D地图
const initMars3D = () => {
  if (!mapContainer.value || mapInstance) return

  try {
    // 检查Mars3D是否已加载
    if (typeof window.mars3d === 'undefined') {
      console.error('Mars3D库未加载，请检查script标签')
      loading.value = false
      return
    }

    const mars3d = window.mars3d
    const Cesium = window.Cesium

    console.log('开始初始化Mars3D地图...')

    // 创建地图实例
    mapInstance = new mars3d.Map(mapContainer.value, {
      scene: {
        center: { lat: 38.942, lng: 116.292, alt: 2000, heading: 0, pitch: -45 },
        contextOptions: {
          requestWebgl2: false,
          alpha: false,
          antialias: true,
          premultipliedAlpha: true,
          stencil: false,
          preserveDrawingBuffer: false,
          failIfMajorPerformanceCaveat: false,
          depth: true,
          logarithmicDepthBuffer: false,
          powerPreference: 'high-performance',
        },
      },
      basemaps: [
        {
          name: '天地图影像',
          type: 'tdt',
          layer: 'img_d',
          show: true,
        },
      ],
      terrain: false,
    })

    // 地图加载完成
    mapInstance.on('load', () => {
      console.log('Mars3D地图加载完成')
      loading.value = false

      // 如果有图片数据，添加图片图层
      if (props.imageUrl && props.geoBoundary) {
        addImageLayer()
      }
    })

    // 底图加载错误处理
    mapInstance.on('basemapError', (error: any) => {
      console.warn('天地图加载失败，切换到OpenStreetMap底图:', error)
      try {
        const Cesium = window.Cesium
        mapInstance.viewer.imageryLayers.removeAll()
        mapInstance.viewer.imageryLayers.addImageryProvider(
          new Cesium.OpenStreetMapImageryProvider({
            url: 'https://a.tile.openstreetmap.org/',
          })
        )
        console.log('✅ 已自动切换到OpenStreetMap底图')
      } catch (fallbackError) {
        console.error('备用底图也加载失败:', fallbackError)
      }
    })
  } catch (error) {
    console.error('初始化Mars3D失败:', error)
    loading.value = false
  }
}

// 获取代理图片URL（如果是外部URL，使用后端代理）
const getProxyImageUrl = (imageUrl: string): string => {
  // 如果是外部URL（不是同源的），使用后端代理
  try {
    const url = new URL(imageUrl)
    const currentOrigin = window.location.origin
    
    // 如果是外部URL，使用代理
    if (url.origin !== currentOrigin && !imageUrl.includes('/api/file/proxy')) {
      // 后端服务器地址（与request.ts中的DEV_BASE_URL保持一致）
      const backendBaseUrl = 'http://localhost:8123'
      // 构建完整的代理URL（指向后端服务器）
      const proxyUrl = `${backendBaseUrl}/api/file/proxy?imageUrl=${encodeURIComponent(imageUrl)}`
      console.log('使用图片代理:', { original: imageUrl, proxy: proxyUrl })
      return proxyUrl
    }
  } catch (e) {
    console.warn('解析图片URL失败，使用原始URL:', e)
  }
  
  return imageUrl
}

// 添加图片图层
const addImageLayer = () => {
  if (!mapInstance || !props.imageUrl || !props.geoBoundary) return

  try {
    const Cesium = window.Cesium

    // 解析地理边界数据
    let geoBoundaryData: any
    try {
      geoBoundaryData =
        typeof props.geoBoundary === 'string'
          ? JSON.parse(props.geoBoundary)
          : props.geoBoundary
    } catch (e) {
      console.error('解析地理边界数据失败:', e)
      return
    }

    if (!geoBoundaryData.xmin || !geoBoundaryData.ymin || !geoBoundaryData.xmax || !geoBoundaryData.ymax) {
      console.error('地理边界数据不完整')
      return
    }

    // 创建矩形区域
    const rectangle = Cesium.Rectangle.fromDegrees(
      geoBoundaryData.xmin,
      geoBoundaryData.ymin,
      geoBoundaryData.xmax,
      geoBoundaryData.ymax
    )

    // 获取代理图片URL（解决CORS问题）
    const proxyImageUrl = getProxyImageUrl(props.imageUrl)

    console.log('添加图像图层:', {
      originalUrl: props.imageUrl,
      proxyUrl: proxyImageUrl,
      bounds: {
        west: geoBoundaryData.xmin,
        south: geoBoundaryData.ymin,
        east: geoBoundaryData.xmax,
        north: geoBoundaryData.ymax,
      },
    })

    // 创建影像图层
    const imageryProvider = new Cesium.SingleTileImageryProvider({
      url: proxyImageUrl,
      rectangle: rectangle,
    })

    imageryLayer = mapInstance.viewer.imageryLayers.addImageryProvider(imageryProvider)
    imageryLayer.alpha = props.showTransition ? 0 : 0.8 // 初始透明度为0，用于过渡效果
    imageryLayer.name = props.imageName || 'image'

    // 添加错误处理
    imageryProvider.errorEvent.addEventListener((error: any) => {
      console.error('图像加载失败:', error)
      console.error('图片URL:', proxyImageUrl)
    })

    // 监听图片加载完成
    if (imageryProvider.readyPromise) {
      imageryProvider.readyPromise
        .then(() => {
          console.log('✅ 图片加载成功:', proxyImageUrl)
          // 如果有过渡效果，逐渐显示图片
          if (props.showTransition) {
            setTimeout(() => {
              if (imageryLayer) {
                animateImageAppearance()
              }
            }, 500)
          }
        })
        .catch((error: any) => {
          console.error('❌ 图片加载失败:', error)
          console.error('图片URL:', proxyImageUrl)
        })
    } else {
      // 如果没有readyPromise，使用延迟方式启动动画
      if (props.showTransition) {
        setTimeout(() => {
          if (imageryLayer) {
            animateImageAppearance()
          }
        }, 1000)
      }
    }

    // 添加图像边框
    if (geoBoundaryData.corners && geoBoundaryData.corners.length >= 4) {
      const positions = geoBoundaryData.corners.map((corner: any) =>
        Cesium.Cartesian3.fromDegrees(corner.lon, corner.lat)
      )
      positions.push(positions[0]) // 闭合多边形

      mapInstance.viewer.entities.add({
        name: `border_${props.imageName || 'image'}`,
        polyline: {
          positions: positions,
          width: 3,
          material: new Cesium.PolylineDashMaterialProperty({
            color: Cesium.Color.CYAN,
            dashLength: 16,
          }),
          clampToGround: true,
          zIndex: 1,
        },
      })
    }

    // 添加图像信息标记
    const centerLon = (geoBoundaryData.xmin + geoBoundaryData.xmax) / 2
    const centerLat = (geoBoundaryData.ymin + geoBoundaryData.ymax) / 2

    mapInstance.viewer.entities.add({
      name: `image_${props.imageName || 'image'}`,
      position: Cesium.Cartesian3.fromDegrees(centerLon, centerLat, 100),
      point: {
        pixelSize: 10,
        color: Cesium.Color.fromCssColorString('#4facfe'),
        outlineColor: Cesium.Color.WHITE,
        outlineWidth: 2,
        heightReference: Cesium.HeightReference.CLAMP_TO_GROUND,
      },
      label: {
        text: props.imageName || '图片位置',
        font: '14pt sans-serif',
        fillColor: Cesium.Color.WHITE,
        outlineColor: Cesium.Color.BLACK,
        outlineWidth: 2,
        pixelOffset: new Cesium.Cartesian2(0, 30),
        horizontalOrigin: Cesium.HorizontalOrigin.CENTER,
        verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
      },
    })

    // 飞行到图片区域
    flyToImageArea(rectangle)
  } catch (error) {
    console.error('添加图片图层失败:', error)
  }
}

// 动画显示图片（过渡效果）
const animateImageAppearance = () => {
  if (!imageryLayer) return

  let alpha = 0
  const targetAlpha = 0.8
  const duration = 2000 // 2秒
  const startTime = Date.now()

  const animate = () => {
    const elapsed = Date.now() - startTime
    const progress = Math.min(elapsed / duration, 1)

    // 使用缓动函数（ease-out）
    const easeOut = 1 - Math.pow(1 - progress, 3)
    alpha = easeOut * targetAlpha

    if (imageryLayer) {
      imageryLayer.alpha = alpha
    }

    if (progress < 1) {
      requestAnimationFrame(animate)
    } else {
      if (imageryLayer) {
        imageryLayer.alpha = targetAlpha
      }
    }
  }

  animate()
}

// 飞行到图片区域
const flyToImageArea = (rectangle: any) => {
  if (!mapInstance) return

  try {
    mapInstance.viewer.camera.flyTo({
      destination: rectangle,
      duration: 2.0,
    })
  } catch (error) {
    console.error('飞行到图片区域失败:', error)
  }
}

// 监听props变化
watch(
  () => [props.imageUrl, props.geoBoundary],
  () => {
    if (mapInstance && props.imageUrl && props.geoBoundary) {
      // 移除旧的图层
      if (imageryLayer) {
        mapInstance.viewer.imageryLayers.remove(imageryLayer)
        imageryLayer = null
      }
      // 清除实体
      mapInstance.viewer.entities.removeAll()
      // 添加新图层
      addImageLayer()
    }
  }
)

onMounted(() => {
  // 延迟初始化，确保Mars3D脚本完全加载
  const timer = setTimeout(() => {
    initMars3D()
  }, 500)

  return () => clearTimeout(timer)
})

onUnmounted(() => {
  if (mapInstance) {
    try {
      mapInstance.destroy()
    } catch (error) {
      console.error('销毁Mars3D地图失败:', error)
    }
    mapInstance = null
  }
})
</script>

<style scoped>
.mars3d-viewer-container {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 600px;
}

.mars3d-map-container {
  width: 100%;
  height: 100%;
  min-height: 600px;
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.8);
  z-index: 1000;
}
</style>

