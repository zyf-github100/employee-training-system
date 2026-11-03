<script setup lang="ts">
  import { computed, onMounted, onUnmounted, ref } from 'vue'
  import { onBeforeRouteLeave, useRoute } from 'vue-router'
  import {
    api,
    currentUser,
    type CourseDetail,
    type Progress,
    type Resource
  } from '../../connected/api'
  import { errorMessage } from './session'
  const id = Number(useRoute().params.id),
    detail = ref<CourseDetail>(),
    progress = ref<Progress[]>([]),
    selected = ref(0)
  const loading = ref(true),
    busy = ref(false),
    error = ref(''),
    notice = ref(''),
    resource = ref<Resource>(),
    resourceUrl = ref(''),
    resourceLoading = ref(false)
  const chapter = computed(() => detail.value?.chapters.find((c) => c.id === selected.value))
  const currentProgress = computed(() => progress.value.find((p) => p.chapterId === selected.value))
  let resourceRequest = 0,
    disposed = false,
    position = 0,
    dirty = false,
    saving: Promise<void> | undefined
  async function load() {
    loading.value = true
    error.value = ''
    try {
      ;[detail.value, progress.value] = await Promise.all([
        api.courseDetail(id),
        api.courseProgress(id)
      ])
      selected.value ||=
        detail.value.chapters.find(
          (c) => !progress.value.some((p) => p.chapterId === c.id && p.completed)
        )?.id ??
        detail.value.chapters[0]?.id ??
        0
      position = currentProgress.value?.lastPositionSeconds ?? 0
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      loading.value = false
    }
  }
  async function enroll() {
    busy.value = true
    error.value = ''
    try {
      detail.value = await api.enroll(id)
      notice.value = '报名成功，可以开始学习了'
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      busy.value = false
    }
  }
  function updateProgress(value: Progress) {
    progress.value = [...progress.value.filter((p) => p.chapterId !== value.chapterId), value]
  }
  async function flush() {
    if (saving) await saving
    if (!dirty || !detail.value?.enrolled || !chapter.value) return
    const chapterId = chapter.value.id,
      seconds = position,
      pct = currentProgress.value?.progressPercent ?? 0
    saving = (async () => {
      updateProgress(await api.saveProgress(id, chapterId, pct, seconds))
      if (position === seconds) dirty = false
      notice.value = '学习位置已保存'
    })()
    try {
      await saving
    } catch (e) {
      error.value = errorMessage(e)
      throw e
    } finally {
      saving = undefined
    }
  }
  async function finish() {
    busy.value = true
    error.value = ''
    try {
      await flush()
      if (!chapter.value) return
      updateProgress(await api.saveProgress(id, chapter.value.id, 100, position, true))
      detail.value = await api.courseDetail(id)
      notice.value =
        detail.value.progressPercent === 100 ? '课程已完成，相关培训进度已自动更新' : '章节已完成'
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      busy.value = false
    }
  }
  function clearResource() {
    resourceRequest++
    if (resourceUrl.value) URL.revokeObjectURL(resourceUrl.value)
    resourceUrl.value = ''
    resource.value = undefined
    resourceLoading.value = false
  }
  async function selectChapter(chapterId: number) {
    try {
      await flush()
      clearResource()
      selected.value = chapterId
      position = currentProgress.value?.lastPositionSeconds ?? 0
      notice.value = ''
    } catch {
      /* The failed save is shown before leaving the chapter. */
    }
  }
  async function openResource(item: Resource) {
    clearResource()
    const request = resourceRequest
    resourceLoading.value = true
    error.value = ''
    try {
      const blob = await api.resourceBlob(item.id)
      if (disposed || request !== resourceRequest) return
      resource.value = item
      resourceUrl.value = URL.createObjectURL(blob)
    } catch (e) {
      if (request === resourceRequest) error.value = errorMessage(e)
    } finally {
      if (request === resourceRequest) resourceLoading.value = false
    }
  }
  function restoreVideo(event: Event) {
    const video = event.target as HTMLVideoElement
    if (position > 0 && position < video.duration) video.currentTime = position
  }
  function videoProgress(event: Event) {
    position = Math.floor((event.target as HTMLVideoElement).currentTime)
    dirty = true
  }
  const timer = window.setInterval(() => {
    if (dirty && !saving) void flush().catch(() => {})
  }, 10000)
  onBeforeRouteLeave(async () => {
    if (!currentUser.value) return true
    try {
      await flush()
      return true
    } catch {
      return false
    }
  })
  onMounted(load)
  onUnmounted(() => {
    disposed = true
    window.clearInterval(timer)
    clearResource()
  })
</script>
<template>
  <section
    ><router-link class="text-button" to="/courses">← 返回课程中心</router-link
    ><div v-if="error" class="api-alert" role="alert"
      >{{ error
      }}<button :disabled="busy" @click="dirty ? flush().catch(() => {}) : load()"
        >重试</button
      ></div
    ><div v-if="notice" class="toast" role="status">{{ notice }}</div
    ><div v-if="loading" class="empty-state">正在读取课程…</div
    ><template v-else-if="detail"
      ><div class="section-heading"
        ><div
          ><span class="eyebrow">{{ detail.course.category }}</span
          ><h1>{{ detail.course.title }}</h1
          ><p>{{ detail.course.summary }}</p></div
        ><span class="status-pill">{{ detail.progressPercent }}% 完成</span></div
      ><div class="progress-track"><i :style="{ width: `${detail.progressPercent}%` }" /></div
      ><p class="muted"
        >已完成 {{ detail.completedChapters }} / {{ detail.chapters.length }} 个章节</p
      ><button v-if="!detail.enrolled" class="primary-button" :disabled="busy" @click="enroll"
        >报名并开始学习</button
      ><div class="live-learning-grid"
        ><aside class="panel"
          ><h2>课程目录</h2
          ><button
            v-for="item in detail.chapters"
            :key="item.id"
            class="live-chapter"
            :class="{ active: item.id === selected }"
            :disabled="busy"
            @click="selectChapter(item.id)"
            ><span>{{ String(item.sortNo).padStart(2, '0') }}</span
            ><b>{{ item.title }}</b
            ><small>{{
              progress.some((p) => p.chapterId === item.id && p.completed)
                ? '已完成'
                : `${item.durationMinutes} 分钟`
            }}</small></button
          ></aside
        ><article v-if="chapter" class="panel"
          ><h2>{{ chapter.title }}</h2
          ><template v-if="detail.enrolled"
            ><div class="lesson-copy">{{ chapter.contentMarkdown || '请阅读下方学习资源。' }}</div
            ><div class="resource-list"
              ><button
                v-for="item in chapter.resources"
                :key="item.id"
                class="resource-chip"
                @click="openResource(item)"
                >{{ item.resourceType }} · {{ item.title }}</button
              ></div
            ><div v-if="resourceLoading" class="empty-state">正在加载资源…</div
            ><div v-if="resource && resourceUrl" class="resource-viewer"
              ><h3>{{ resource.title }}</h3
              ><video
                v-if="resource.resourceType === 'VIDEO'"
                :src="resourceUrl"
                controls
                playsinline
                @loadedmetadata="restoreVideo"
                @timeupdate="videoProgress"
                @pause="flush().catch(() => {})"
              /><iframe
                v-else-if="resource.resourceType === 'PDF'"
                :src="resourceUrl"
                :title="resource.title"
              /><img
                v-else-if="resource.resourceType === 'IMAGE'"
                :src="resourceUrl"
                :alt="resource.title"
              /><a
                :href="resourceUrl"
                :download="resource.originalName || resource.title"
                class="text-button"
                >下载资源</a
              ></div
            ><button
              class="primary-button"
              :disabled="busy || currentProgress?.completed"
              @click="finish"
              >{{
                currentProgress?.completed ? '本章已完成' : busy ? '保存中…' : '完成本章学习'
              }}</button
            ><p class="muted">阅读章节并完成学习资源后，确认本章完成。</p></template
          ><p v-else class="empty-state">请先报名课程，再开始本章学习。</p></article
        ><div v-else class="empty-state">课程暂未添加章节，请联系培训管理员。</div></div
      ></template
    ></section
  >
</template>
