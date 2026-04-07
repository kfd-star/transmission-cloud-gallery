<template>
  <div class="avatar-upload">
    <a-upload
      :show-upload-list="false"
      :custom-request="handleUpload"
      :before-upload="beforeUpload"
      accept="image/*"
    >
      <div class="avatar-wrapper">
        <a-avatar :size="120" :src="avatarUrl">
          <template v-if="!avatarUrl">
            <UserOutlined />
          </template>
        </a-avatar>
        <div class="upload-text">
          <CameraOutlined />
          <div>点击上传头像</div>
        </div>
      </div>
    </a-upload>
  </div>
</template>

<script lang="ts" setup>
import { ref } from 'vue'
import { UserOutlined, CameraOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'

interface Props {
  avatarUrl?: string
}

interface Emits {
  (e: 'update:avatarUrl', url: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const loading = ref(false)

/**
 * 上传头像
 */
const handleUpload = async ({ file }: any) => {
  loading.value = true
  try {
    const res = await uploadPictureUsingPost({}, {}, file)
    if (res.data?.code === 0 && res.data?.data) {
      const avatarUrl = res.data.data.url
      emit('update:avatarUrl', avatarUrl)
      message.success('头像上传成功')
    } else {
      message.error('头像上传失败，' + (res.data?.message || '未知错误'))
    }
  } catch (error) {
    console.error('头像上传失败', error)
    message.error('头像上传失败')
  }
  loading.value = false
}

/**
 * 上传前的校验
 */
const beforeUpload = (file: any) => {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    message.error('只能上传图片文件!')
    return false
  }
  const isLt2M = file.size / 1024 / 1024 < 2
  if (!isLt2M) {
    message.error('图片大小不能超过 2MB!')
    return false
  }
  return true
}
</script>

<style scoped>
.avatar-upload {
  display: flex;
  justify-content: center;
}

.avatar-wrapper {
  position: relative;
  cursor: pointer;
  transition: all 0.3s;
  text-align: center;
}

.avatar-wrapper:hover {
  opacity: 0.8;
}

.upload-text {
  margin-top: 8px;
  text-align: center;
  font-size: 12px;
  color: #666;
  white-space: nowrap;
}

.upload-text .anticon {
  display: block;
  margin-bottom: 4px;
  font-size: 16px;
}
</style>
