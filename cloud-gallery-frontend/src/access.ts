import router from '@/router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { message } from 'ant-design-vue'

// 是否为首次获取登录用户
let firstFetchLoginUser = true

/**
 * 全局权限校验，每次切换页面时都会执行
 */
router.beforeEach(async (to, from, next) => {
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser
  // 确保页面刷新时，首次加载时，能等待后端返回用户信息后再校验权限
  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser()
    loginUser = loginUserStore.loginUser
    firstFetchLoginUser = false
  }
  const toUrl = to.fullPath
  // 可以自己定义权限校验逻辑，比如管理员才能访问 /admin 开头的页面
  if (toUrl.startsWith('/admin')) {
    if (!loginUser || loginUser.userRole !== 'admin') {
      message.error('没有权限')
      next(`/user/login?redirect=${to.fullPath}`)
      return
    }
  }
  
  // 创建空间页面需要管理员权限
  if (toUrl === '/add_space') {
    if (!loginUser || loginUser.userRole !== 'admin') {
      message.error('只有管理员才能创建空间')
      next('/')
      return
    }
  }
  
  // 创建图片页面需要登录用户权限（团队空间成员也可以创建）
  if (toUrl.startsWith('/add_picture')) {
    if (!loginUser) {
      message.error('请先登录')
      next(`/user/login?redirect=${to.fullPath}`)
      return
    }
  }
  next()
})
