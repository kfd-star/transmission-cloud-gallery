<template>
  <div id="globalSider">
    <a-layout-sider
      v-if="loginUserStore.loginUser.id"
      width="200"
      breakpoint="lg"
      collapsed-width="0"
    >
      <a-menu
        v-model:selectedKeys="current"
        mode="inline"
        :items="menuItems"
        @click="doMenuClick"
      />
    </a-layout-sider>
  </div>
</template>
<script lang="ts" setup>
import { computed, h, ref, watchEffect } from 'vue'
import { PictureOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'
import { listMyTeamSpaceUsingPost } from '@/api/spaceUserController.ts'
import { message } from 'ant-design-vue'

const loginUserStore = useLoginUserStore()

// 固定的菜单列表
const fixedMenuItems = computed(() => {
  const baseItems = [
    {
      key: '/',
      icon: () => h(PictureOutlined),
      label: '公共图库',
    },
    {
      key: '/my_space',
      label: '我的空间',
      icon: () => h(UserOutlined),
    },
  ]
  
  // 所有用户都能看到创建团队选项
  baseItems.push({
    key: 'create_team',
    label: '创建团队',
    icon: () => h(TeamOutlined),
  })
  
  return baseItems
})

const teamSpaceList = ref<API.SpaceUserVO[]>([])
const menuItems = computed(() => {
  // 如果用户没有团队空间，则只展示固定菜单
  if (teamSpaceList.value.length < 1) {
    return fixedMenuItems.value
  }
  // 如果用户有团队空间，则展示固定菜单和团队空间菜单
  // 展示团队空间分组
  const teamSpaceSubMenus = teamSpaceList.value.map((spaceUser) => {
    const space = spaceUser.space
    return {
      key: '/space/' + spaceUser.spaceId,
      label: space?.spaceName,
    }
  })
  const teamSpaceMenuGroup = {
    type: 'group',
    label: '我的团队',
    key: 'teamSpace',
    children: teamSpaceSubMenus,
  }
  return [...fixedMenuItems.value, teamSpaceMenuGroup]
})

// 加载团队空间列表
const fetchTeamSpaceList = async () => {
  try {
    const res = await listMyTeamSpaceUsingPost()
    if (res.data.code === 0 && res.data.data) {
      // 过滤掉已删除的空间，只保留有效的团队空间
      teamSpaceList.value = res.data.data.filter(spaceUser => {
        return spaceUser.space && spaceUser.space.spaceName && spaceUser.space.spaceName.trim() !== ''
      })
    } else {
      // 如果请求失败，清空列表
      teamSpaceList.value = []
    }
  } catch (error) {
    console.error('加载团队空间失败:', error)
    teamSpaceList.value = []
  }
}

/**
 * 监听变量，改变时触发数据的重新加载
 */
watchEffect(() => {
  // 登录才加载
  if (loginUserStore.loginUser.id) {
    fetchTeamSpaceList()
  }
})

const router = useRouter()
// 当前要高亮的菜单项
const current = ref<string[]>([])
// 监听路由变化，更新高亮菜单项
router.afterEach((to, from, next) => {
  current.value = [to.path]
})

// 路由跳转事件
const doMenuClick = ({ key }) => {
  // 处理创建团队的特殊逻辑
  if (key === 'create_team') {
    // 检查用户权限
    if (loginUserStore.loginUser.userRole === 'admin') {
      // 管理员直接跳转到创建团队页面
      router.push('/add_space?type=' + SPACE_TYPE_ENUM.TEAM)
    } else {
      // 普通用户显示权限提示
      message.warning('您还没有权限，请联系管理员创建')
    }
    return
  }
  
  // 处理我的空间的特殊逻辑
  if (key === '/my_space') {
    // 检查用户权限
    if (loginUserStore.loginUser.userRole === 'admin') {
      // 管理员正常跳转到我的空间页面
      router.push(key)
    } else {
      // 普通用户显示权限提示
      message.warning('您还没有权限，请联系管理员创建')
    }
    return
  }
  
  // 其他菜单项正常跳转
  router.push(key)
}

// 监听空间删除和创建事件，自动刷新团队空间列表
window.addEventListener('spaceDeleted', () => {
  fetchTeamSpaceList()
})

window.addEventListener('spaceCreated', () => {
  fetchTeamSpaceList()
})

// 手动刷新团队空间列表（用于调试）
const refreshTeamSpaceList = () => {
  fetchTeamSpaceList()
}

// 暴露刷新函数给外部调用
defineExpose({
  refreshTeamSpaceList,
})

// 在开发环境下，将刷新函数挂载到全局对象，方便调试
if (import.meta.env.DEV) {
  (window as any).refreshTeamSpaceList = refreshTeamSpaceList
}
</script>

<style scoped>
#globalSider .ant-layout-sider {
  background: none;
}
</style>
