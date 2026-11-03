<script setup lang="ts">
  import { onMounted, onBeforeUnmount, reactive, ref } from 'vue'
  import { useRoute, onBeforeRouteLeave } from 'vue-router'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import { api, type Chapter, type CourseDetail, type Resource } from '../../../connected/api'
  import { errorMessage } from '../session'
  const id = Number(useRoute().params.id)
  const detail = ref<CourseDetail | null>(null),
    loading = ref(true),
    busy = ref(false),
    error = ref('')
  const editor = ref(false),
    chapterId = ref<number | null>(null)
  const chapter = reactive({ title: '', sortNo: 1, contentMarkdown: '', durationMinutes: 10 })
  const uploadChapter = ref<number | null>(null),
    uploadFile = ref<File | null>(null),
    uploadTitle = ref(''),
    uploadKey = ref(0)
  const preview = ref<Resource | null>(null),
    previewUrl = ref(''),
    previewLoading = ref(false)
  let previewRequest = 0
  async function load() {
    loading.value = true
    error.value = ''
    try {
      detail.value = await api.courseDetail(id, true)
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }
  function edit(value?: Chapter) {
    chapterId.value = value?.id ?? null
    Object.assign(chapter, {
      title: value?.title ?? '',
      sortNo:
        value?.sortNo ?? Math.max(0, ...(detail.value?.chapters.map((c) => c.sortNo) ?? [])) + 1,
      contentMarkdown: value?.contentMarkdown ?? '',
      durationMinutes: value?.durationMinutes ?? 10
    })
    editor.value = true
  }
  async function saveChapter() {
    if (busy.value) return
    busy.value = true
    try {
      detail.value = await api.saveChapter(id, chapterId.value, chapter)
      editor.value = false
      ElMessage.success('章节已保存')
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      busy.value = false
    }
  }
  function selectFile(event: Event) {
    uploadFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
    uploadTitle.value = uploadFile.value?.name.replace(/\.[^.]+$/, '') ?? ''
  }
  function openUpload(id: number) {
    uploadChapter.value = id
    uploadFile.value = null
    uploadTitle.value = ''
    uploadKey.value++
  }
  async function upload() {
    if (!uploadChapter.value || !uploadFile.value || busy.value) return
    busy.value = true
    try {
      await api.uploadResource(uploadChapter.value, uploadFile.value, uploadTitle.value)
      uploadFile.value = null
      uploadKey.value++
      uploadChapter.value = null
      await load()
      ElMessage.success('资源上传成功')
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      busy.value = false
    }
  }
  async function remove(resource: Resource) {
    try {
      await ElMessageBox.confirm(
        `确定删除“${resource.title}”？删除后员工将无法访问该资源。`,
        '删除资源',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
    busy.value = true
    try {
      await api.deleteResource(resource.id)
      if (preview.value?.id === resource.id) closePreview()
      await load()
      ElMessage.success('资源已删除')
    } catch (e) {
      ElMessage.error(errorMessage(e))
    } finally {
      busy.value = false
    }
  }
  function closePreview() {
    previewRequest++
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
    preview.value = null
    previewLoading.value = false
  }
  async function openPreview(resource: Resource) {
    closePreview()
    const ticket = previewRequest
    preview.value = resource
    previewLoading.value = true
    try {
      const blob = await api.resourceBlob(resource.id)
      if (ticket === previewRequest) previewUrl.value = URL.createObjectURL(blob)
    } catch (e) {
      if (ticket === previewRequest) {
        closePreview()
        ElMessage.error(errorMessage(e))
      }
    } finally {
      if (ticket === previewRequest) previewLoading.value = false
    }
  }
  onBeforeRouteLeave(() => !busy.value)
  onBeforeUnmount(closePreview)
  onMounted(load)
</script>
<template>
  <section
    ><router-link class="text-button admin-back" to="/admin/courses">← 返回课程管理</router-link>
    <div v-if="error" class="api-alert" role="alert"
      ><span>{{ error }}</span
      ><button @click="load">重试</button></div
    >
    <div v-if="loading && !detail" class="empty-state">正在读取课程…</div>
    <template v-if="detail"
      ><div class="section-heading"
        ><div
          ><span class="eyebrow">CHAPTERS & RESOURCES</span><h1>{{ detail.course.title }}</h1
          ><p
            >{{ detail.course.category || '通用培训' }} · {{ detail.chapters.length }} 个章节 ·
            {{ detail.course.status === 'PUBLISHED' ? '已发布' : '草稿' }}</p
          ></div
        ><button class="primary-button" :disabled="busy" @click="edit()">添加章节</button></div
      >
      <div class="admin-info-note"
        ><AppIcon name="book" /><span
          >章节正文按纯文本展示。视频、PDF
          和图片可在线预览，其他附件提供下载。已发布课程的修改会影响员工当前访问的内容。</span
        ></div
      >
      <div v-if="!detail.chapters.length" class="panel empty-state"
        >还没有章节，添加第一章，开始编排学习内容。</div
      >
      <div class="admin-chapters"
        ><article v-for="(item, index) in detail.chapters" :key="item.id" class="panel"
          ><div class="admin-chapter-heading"
            ><span class="admin-chapter-number">{{ String(index + 1).padStart(2, '0') }}</span
            ><div
              ><h2>{{ item.title }}</h2
              ><small
                >排序 {{ item.sortNo }} · {{ item.durationMinutes }} 分钟 ·
                {{ item.resources.length }} 个资源</small
              ></div
            ><button class="text-button" :disabled="busy" @click="edit(item)">编辑章节</button></div
          ><p v-if="item.contentMarkdown" class="admin-chapter-copy">{{ item.contentMarkdown }}</p
          ><div class="admin-resource-list"
            ><div v-for="resource in item.resources" :key="resource.id" class="admin-resource-row"
              ><span class="admin-resource-type">{{ resource.resourceType }}</span
              ><div
                ><b>{{ resource.title }}</b
                ><small
                  >{{ resource.originalName }} ·
                  {{ ((resource.sizeBytes ?? 0) / 1024 / 1024).toFixed(2) }} MB</small
                ></div
              ><button class="text-button" :disabled="busy" @click="openPreview(resource)"
                >查看</button
              ><button class="text-button danger" :disabled="busy" @click="remove(resource)"
                >删除</button
              ></div
            ><p v-if="!item.resources.length" class="muted">本章还没有附加资源。</p></div
          ><button class="outline-button" :disabled="busy" @click="openUpload(item.id)"
            >上传学习资源</button
          ></article
        ></div
      >
    </template>
    <el-dialog
      v-model="editor"
      :title="chapterId ? '编辑章节' : '添加章节'"
      width="680px"
      class="responsive-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="!busy"
      :show-close="!busy"
      ><form class="admin-dialog-form" @submit.prevent="saveChapter"
        ><label>章节标题<input v-model.trim="chapter.title" required maxlength="160" /></label
        ><div class="admin-form-grid"
          ><label
            >排序<input v-model.number="chapter.sortNo" type="number" min="1" required /></label
          ><label
            >预计时长（分钟）<input
              v-model.number="chapter.durationMinutes"
              type="number"
              min="0"
              max="1440"
              required /></label></div
        ><label
          >章节正文<textarea
            v-model="chapter.contentMarkdown"
            rows="9"
            maxlength="100000"
            placeholder="输入学习内容，换行会保留"
          /></label
        ><div class="admin-form-actions"
          ><button type="button" class="outline-button" :disabled="busy" @click="editor = false"
            >取消</button
          ><button class="primary-button" :disabled="busy">{{
            busy ? '保存中…' : '保存章节'
          }}</button></div
        ></form
      ></el-dialog
    >
    <el-dialog
      :model-value="uploadChapter !== null"
      title="上传学习资源"
      width="560px"
      class="responsive-dialog"
      :close-on-click-modal="false"
      :close-on-press-escape="!busy"
      :show-close="!busy"
      @update:model-value="!$event && (uploadChapter = null)"
      ><form class="admin-dialog-form" @submit.prevent="upload"
        ><label
          >资源文件<input
            :key="uploadKey"
            type="file"
            required
            :disabled="busy"
            @change="selectFile" /></label
        ><label
          >资源标题<input
            v-model.trim="uploadTitle"
            required
            maxlength="160"
            :disabled="busy" /></label
        ><p class="muted">支持视频、PDF、图片及常用文档附件，格式与大小由服务器校验。</p
        ><div class="admin-form-actions"
          ><button
            type="button"
            class="outline-button"
            :disabled="busy"
            @click="uploadChapter = null"
            >取消</button
          ><button class="primary-button" :disabled="busy || !uploadFile">{{
            busy ? '上传中…' : '上传资源'
          }}</button></div
        ></form
      ></el-dialog
    >
    <el-dialog
      :model-value="!!preview"
      :title="preview?.title || '资源预览'"
      width="900px"
      class="responsive-dialog"
      @update:model-value="!$event && closePreview()"
      ><div v-if="previewLoading" class="empty-state">正在加载资源…</div
      ><div v-else-if="preview && previewUrl" class="admin-preview"
        ><video v-if="preview.resourceType === 'VIDEO'" :src="previewUrl" controls /><iframe
          v-else-if="preview.resourceType === 'PDF'"
          :src="previewUrl"
          :title="preview.title"
        /><img
          v-else-if="preview.resourceType === 'IMAGE'"
          :src="previewUrl"
          :alt="preview.title"
        /><div v-else class="empty-state"
          ><p>此格式提供原文件下载。</p
          ><a
            class="primary-button"
            :href="previewUrl"
            :download="preview.originalName || preview.title"
            >下载附件</a
          ></div
        ></div
      ></el-dialog
    >
  </section>
</template>
