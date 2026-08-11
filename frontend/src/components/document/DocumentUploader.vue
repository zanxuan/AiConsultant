<template>
  <div
    class="doc-uploader"
    :class="{ 'is-disabled': uploadDisabled }"
    @click.capture="onAreaClick"
  >
    <el-upload
      drag
      :auto-upload="false"
      :show-file-list="false"
      :disabled="uploading || !isLoggedIn"
      accept=".pdf,.md,.txt"
      class="doc-uploader__upload"
      @change="onChange"
    >
      <div class="doc-uploader__inner">
        <div class="doc-uploader__icon-wrap">
          <el-icon class="doc-uploader__icon"><UploadFilled /></el-icon>
        </div>
        <p class="doc-uploader__title">
          拖拽文件到此处，或 <em>点击上传</em>
        </p>
        <p class="doc-uploader__hint">
          支持 PDF / Markdown / TXT 格式
          <span v-if="!isLoggedIn"> · 登录后可上传</span>
          <span v-else-if="!knowledgeId"> · 请先选择知识库</span>
          <span v-else-if="uploading"> · 上传进度 {{ percent }}%</span>
        </p>
        <el-button
          type="primary"
          class="doc-uploader__btn"
          :loading="uploading"
          :disabled="isLoggedIn && !knowledgeId"
        >
          <el-icon class="el-icon--left"><Upload /></el-icon>
          选择文件
        </el-button>
      </div>
    </el-upload>

    <el-progress
      v-if="uploading"
      :percentage="percent"
      :stroke-width="6"
      class="doc-uploader__progress"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Upload, UploadFilled } from '@element-plus/icons-vue'
import type { UploadFile } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useUpload } from '@/composables/useUpload'
import { useAuth } from '@/composables/useAuth'

const props = defineProps<{
  knowledgeId: number | string | null
}>()

const emit = defineEmits<{
  success: []
}>()

const { percent, uploading, isAllowedFile, upload } = useUpload()
const { isLoggedIn, hasToken } = useAuth()

const uploadDisabled = computed(
  () => uploading.value || !isLoggedIn.value || !props.knowledgeId,
)

function onAreaClick(event: MouseEvent) {
  if (hasToken()) return
  event.preventDefault()
  event.stopPropagation()
  ElMessage.warning('请先登录后再上传文档')
}

async function onChange(uploadFile: UploadFile) {
  if (!hasToken()) {
    ElMessage.warning('请先登录后再上传文档')
    return
  }
  if (!props.knowledgeId) {
    ElMessage.warning('请先选择知识库')
    return
  }
  if (!uploadFile.raw) return

  if (!isAllowedFile(uploadFile.raw)) {
    ElMessage.warning('仅支持 pdf / md / txt')
    return
  }

  const result = await upload(props.knowledgeId, uploadFile.raw)
  if (result) {
    ElMessage.success('上传成功，等待解析')
    emit('success')
  }
}
</script>

<style scoped lang="scss">
.doc-uploader {
  &.is-disabled {
    :deep(.el-upload-dragger) {
      opacity: 0.72;
    }
  }

  &__upload {
    width: 100%;

    :deep(.el-upload) {
      width: 100%;
    }

    :deep(.el-upload-dragger) {
      width: 100%;
      padding: 36px 24px 28px;
      border: 1.5px dashed rgba(15, 76, 92, 0.35);
      border-radius: 14px;
      background: linear-gradient(180deg, #f7fbfc 0%, #ffffff 70%);
      transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;

      &:hover {
        border-color: #0f4c5c;
        background: linear-gradient(180deg, #eef7f9 0%, #ffffff 70%);
        box-shadow: 0 8px 24px rgba(15, 76, 92, 0.06);
      }
    }
  }

  &__inner {
    display: flex;
    flex-direction: column;
    align-items: center;
    pointer-events: none;
  }

  &__icon-wrap {
    width: 64px;
    height: 64px;
    border-radius: 18px;
    display: grid;
    place-items: center;
    background: rgba(15, 76, 92, 0.08);
    margin-bottom: 14px;
  }

  &__icon {
    font-size: 32px;
    color: #0f4c5c;
  }

  &__title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: #1f2d3d;

    em {
      font-style: normal;
      color: #0f4c5c;
    }
  }

  &__hint {
    margin: 8px 0 0;
    font-size: 13px;
    color: #94a3b8;
  }

  &__btn {
    margin-top: 18px;
    --el-button-bg-color: #0f4c5c;
    --el-button-border-color: #0f4c5c;
    --el-button-hover-bg-color: #163f4c;
    --el-button-hover-border-color: #163f4c;
    border-radius: 8px;
    padding: 10px 18px;
  }

  &__progress {
    margin-top: 14px;
  }
}
</style>
