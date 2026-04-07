<template>
  <div id="userProfilePage">
    <a-card title="个人设置" style="max-width: 600px; margin: 0 auto;">
      <a-form
        :model="formState"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 18 }"
        @finish="handleSubmit"
      >
        <!-- 头像上传 -->
        <a-form-item label="头像">
          <AvatarUpload 
            v-model:avatar-url="formState.userAvatar"
            @update:avatar-url="formState.userAvatar = $event"
          />
        </a-form-item>

        <!-- 用户名 -->
        <a-form-item 
          label="用户名" 
          name="userName"
          :rules="[{ required: true, message: '请输入用户名' }]"
        >
          <a-input v-model:value="formState.userName" placeholder="请输入用户名" />
        </a-form-item>

        <!-- 个人简介 -->
        <a-form-item label="个人简介">
          <a-textarea 
            v-model:value="formState.userProfile" 
            placeholder="请输入个人简介"
            :rows="4"
            :maxlength="500"
            show-count
          />
        </a-form-item>

        <!-- 账号信息（只读） -->
        <a-form-item label="账号">
          <a-input v-model:value="loginUser.userAccount" disabled />
        </a-form-item>

        <a-form-item label="角色">
          <a-tag :color="loginUser.userRole === 'admin' ? 'green' : 'blue'">
            {{ loginUser.userRole === 'admin' ? '管理员' : '普通用户' }}
          </a-tag>
        </a-form-item>

        <!-- 提交按钮 -->
        <a-form-item :wrapper-col="{ offset: 6, span: 18 }">
          <a-space>
            <a-button type="primary" html-type="submit" :loading="loading">
              保存修改
            </a-button>
            <a-button @click="resetForm">
              重置
            </a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script lang="ts" setup>
import { reactive, ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { updateMyUserUsingPost } from '@/api/userController.ts'
import AvatarUpload from '@/components/AvatarUpload.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser
const loading = ref(false)

// 表单数据
const formState = reactive({
  userName: '',
  userAvatar: '',
  userProfile: '',
})

// 原始数据（用于重置）
const originalData = reactive({
  userName: '',
  userAvatar: '',
  userProfile: '',
})

/**
 * 初始化表单数据
 */
const initForm = () => {
  formState.userName = loginUser.userName || ''
  formState.userAvatar = loginUser.userAvatar || ''
  formState.userProfile = loginUser.userProfile || ''
  
  // 保存原始数据
  originalData.userName = formState.userName
  originalData.userAvatar = formState.userAvatar
  originalData.userProfile = formState.userProfile
}

/**
 * 提交表单
 */
const handleSubmit = async () => {
  loading.value = true
  try {
    const res = await updateMyUserUsingPost({
      userName: formState.userName,
      userAvatar: formState.userAvatar,
      userProfile: formState.userProfile,
    })
    
    if (res.data.code === 0) {
      message.success('保存成功')
      // 更新本地用户信息
      await loginUserStore.fetchLoginUser()
      // 更新原始数据
      originalData.userName = formState.userName
      originalData.userAvatar = formState.userAvatar
      originalData.userProfile = formState.userProfile
    } else {
      message.error('保存失败，' + res.data.message)
    }
  } catch (error) {
    console.error('保存失败', error)
    message.error('保存失败')
  }
  loading.value = false
}

/**
 * 重置表单
 */
const resetForm = () => {
  formState.userName = originalData.userName
  formState.userAvatar = originalData.userAvatar
  formState.userProfile = originalData.userProfile
  message.info('已重置为原始数据')
}

// 页面加载时初始化
onMounted(() => {
  if (!loginUser.id) {
    message.error('请先登录')
    router.push('/user/login')
    return
  }
  initForm()
})
</script>

<style scoped>
#userProfilePage {
  padding: 24px;
  min-height: calc(100vh - 64px);
  background-color: #f5f5f5;
}

.ant-card {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
</style>
