<script setup lang="ts">
  import { computed, onMounted, onUnmounted, ref } from 'vue'
  import { onBeforeRouteLeave, useRoute } from 'vue-router'
  import {
    api,
    currentUser,
    type CourseDiscussion,
    type CourseDetail,
    type CourseReviewSummary,
    type Progress,
    type Resource
  } from '../../connected/api'
  import { errorMessage } from './session'

  const id = Number(useRoute().params.id),
    detail = ref<CourseDetail>(),
    progress = ref<Progress[]>([]),
    reviews = ref<CourseReviewSummary>(),
    discussions = ref<CourseDiscussion[]>([]),
    reviewRating = ref(0),
    reviewContent = ref(''),
    discussionContent = ref(''),
    selected = ref(0)
  const loading = ref(true),
    busy = ref(false),
    error = ref(''),
    notice = ref(''),
    resource = ref<Resource>(),
    resourceUrl = ref(''),
    resourceLoading = ref(false),
    videoEl = ref<HTMLVideoElement>(),
    visible = ref(document.visibilityState === 'visible'),
    videoPlaying = ref(false)
  const chapter = computed(() => detail.value?.chapters.find((c) => c.id === selected.value))
  const currentProgress = computed(() => progress.value.find((p) => p.chapterId === selected.value))
  // Reading a text/PDF chapter counts as active while the tab is visible; a
  // video only counts while it is actually playing.
  const activity = computed(() => visible.value && (!videoEl.value || videoPlaying.value))
  const watchedSeconds = computed(() => currentProgress.value?.watchedSeconds ?? 0)
  const requiredSeconds = computed(
    () => currentProgress.value?.requiredSeconds ?? chapter.value?.requiredSeconds ?? 0
  )
  const remainingSeconds = computed(() =>
    currentProgress.value?.completed
      ? 0
      : Math.max(0, requiredSeconds.value - watchedSeconds.value)
  )
  const studyPercent = computed(() =>
    requiredSeconds.value === 0
      ? 0
      : Math.min(100, Math.round((watchedSeconds.value * 100) / requiredSeconds.value))
  )
  let resourceRequest = 0,
    disposed = false,
    position = 0,
    inFlight: Promise<Progress | undefined> | null = null

  function minutes(seconds: number) {
    return `${Math.max(0, Math.ceil(seconds / 60))} 分钟`
  }
  function currentPercent() {
    const video = videoEl.value
    if (video && Number.isFinite(video.duration) && video.duration > 0)
      return Math.min(100, Math.floor((video.currentTime / video.duration) * 100))
    return currentProgress.value?.progressPercent ?? 0
  }
  async function load() {
    loading.value = true
    error.value = ''
    try {
      ;[detail.value, progress.value, reviews.value, discussions.value] = await Promise.all([
        api.courseDetail(id),
        api.courseProgress(id),
        api.courseReviews(id),
        api.courseDiscussions(id)
      ])
      reviewRating.value = reviews.value.mine?.rating ?? 0
      reviewContent.value = reviews.value.mine?.content ?? ''
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
  async function saveReview() {
    if (!reviewRating.value) {
      error.value = '请选择课程评分'
      return
    }
    busy.value = true
    error.value = ''
    try {
      await api.saveCourseReview(id, {
        rating: reviewRating.value,
        content: reviewContent.value.trim(),
        version: reviews.value?.mine?.version ?? 0
      })
      reviews.value = await api.courseReviews(id)
      reviewRating.value = reviews.value.mine?.rating ?? reviewRating.value
      reviewContent.value = reviews.value.mine?.content ?? reviewContent.value
      notice.value = '课程评价已保存'
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      busy.value = false
    }
  }
  async function postDiscussion() {
    const content = discussionContent.value.trim()
    if (!content) return
    busy.value = true
    error.value = ''
    try {
      await api.createCourseDiscussion(id, content)
      discussionContent.value = ''
      discussions.value = await api.courseDiscussions(id)
      notice.value = '讨论已发布'
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      busy.value = false
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
  async function save(completed: boolean, playing = activity.value): Promise<Progress | undefined> {
    if (!detail.value?.enrolled || disposed) return
    if (inFlight) await inFlight
    if (!chapter.value) return
    const courseId = id,
      chapterId = chapter.value.id,
      seconds = position,
      pct = completed ? 100 : currentPercent()
    inFlight = (async () => {
      const value = await api.saveProgress(courseId, chapterId, pct, seconds, completed, playing)
      updateProgress(value)
      return value
    })()
    try {
      return await inFlight
    } finally {
      inFlight = null
    }
  }
  async function finish() {
    busy.value = true
    error.value = ''
    try {
      const result = await save(true)
      if (!result) return
      if (!result.completed) {
        notice.value = `还需学习 ${minutes(result.remainingSeconds)}，系统按实际学习时长判定`
        return
      }
      detail.value = await api.courseDetail(id)
      notice.value =
        detail.value.progressPercent === 100
          ? '课程已完成，相关培训进度已自动更新'
          : '章节已完成'
    } catch (e) {
      error.value = errorMessage(e)
    } finally {
      busy.value = false
    }
  }
  function clearResource() {
    resourceRequest++
    videoPlaying.value = false
    if (resourceUrl.value) URL.revokeObjectURL(resourceUrl.value)
    resourceUrl.value = ''
    resource.value = undefined
    resourceLoading.value = false
  }
  async function selectChapter(chapterId: number) {
    try {
      await save(false)
    } catch {
      return
    }
    clearResource()
    selected.value = chapterId
    position = currentProgress.value?.lastPositionSeconds ?? 0
    notice.value = ''
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
  }
  function videoStarted() {
    videoPlaying.value = true
  }
  function videoPaused() {
    void save(false, true).catch(() => {})
    videoPlaying.value = false
  }
  function onVisibility() {
    if (document.visibilityState !== 'visible') {
      void save(false, activity.value).catch(() => {})
      visible.value = false
    } else {
      visible.value = true
    }
  }
  const timer = window.setInterval(() => {
    if (!currentProgress.value?.completed && activity.value) void save(false).catch(() => {})
  }, 10000)
  onBeforeRouteLeave(async () => {
    if (!currentUser.value) return true
    try {
      await save(false)
      return true
    } catch {
      return false
    }
  })
  onMounted(() => {
    document.addEventListener('visibilitychange', onVisibility)
    void load()
  })
  onUnmounted(() => {
    disposed = true
    window.clearInterval(timer)
    document.removeEventListener('visibilitychange', onVisibility)
    clearResource()
  })
</script>
<template>
  <section
    ><router-link class="text-button" to="/courses">← 返回课程中心</router-link
    ><div v-if="error" class="api-alert" role="alert"
      >{{ error
      }}<button :disabled="busy" @click="load">重试</button></div
    ><div v-if="notice" class="toast" role="status">{{ notice }}</div
    ><div v-if="loading" class="empty-state">正在读取课程…</div
    ><template v-else-if="detail"
      ><div class="section-heading"
        ><div
          ><span class="eyebrow">{{ detail.course.category }}</span
          ><h1>{{ detail.course.title }}</h1
          ><p>{{ detail.course.summary }}</p></div
        ><span class="status-pill">{{ detail.progressPercent }}% 完成</span></div
      ><div class="progress-track"><i :style="{ width: `${detail.progressPercent}%` }" /></div>
      <p class="muted">已完成 {{ detail.completedChapters }} / {{ detail.chapters.length }} 个章节</p>
      <button v-if="!detail.enrolled" class="primary-button" :disabled="busy" @click="enroll"
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
                ref="videoEl"
                :src="resourceUrl"
                controls
                playsinline
                @loadedmetadata="restoreVideo"
                @timeupdate="videoProgress"
                @play="videoStarted"
                @pause="videoPaused"
                @ended="videoPaused"
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
            ><div v-if="!currentProgress?.completed" class="chapter-study"
              ><div class="progress-track"
                ><i :style="{ width: `${studyPercent}%` }" /></div
              ><p class="muted"
                >已学习 {{ minutes(watchedSeconds) }} / 需 {{ minutes(requiredSeconds)
                }}<span v-if="remainingSeconds > 0"
                  >，还需 {{ minutes(remainingSeconds) }}</span
                ></p
              ></div
            ><p v-else class="muted">本章已完成</p
            ><button
              class="primary-button"
              :disabled="busy || currentProgress?.completed"
              @click="finish"
              >{{
                currentProgress?.completed ? '本章已完成' : busy ? '保存中…' : '完成本章学习'
              }}</button
            ><p class="muted"
              >系统按页面停留和视频播放时长累计学习时间，达到本章预计时长的 80% 后才能标记完成。</p
            ></template
          ><p v-else class="empty-state">请先报名课程，再开始本章学习。</p></article
        ><div v-else class="empty-state">课程暂未添加章节，请联系培训管理员。</div></div
      ><section v-if="reviews" class="panel course-engagement"
        ><div class="section-heading compact-heading"
          ><div><span class="eyebrow">COURSE FEEDBACK</span><h2>课程评价与讨论</h2></div
          ><strong>{{ reviews.averageRating.toFixed(1) }} 分 · {{ reviews.reviewCount }} 条评价</strong></div
        ><div class="engagement-grid"
          ><form class="engagement-form" @submit.prevent="saveReview"
            ><h3>留下你的评价</h3
            ><div class="rating-picker" aria-label="课程评分"
              ><button
                v-for="star in 5"
                :key="star"
                type="button"
                class="rating-star"
                :class="{ active: star <= reviewRating }"
                :aria-label="`${star} 分`"
                @click="reviewRating = star"
                >★</button
              ></div
            ><textarea v-model="reviewContent" maxlength="1000" placeholder="说说这门课程对你的帮助（可选）" />
            ><button class="primary-button" :disabled="busy">保存评价</button></form
          ><div class="review-list"
            ><article v-for="review in reviews.reviews" :key="review.id" class="review-item"
              ><div class="review-meta"><strong>{{ review.authorName }}</strong><span>{{ '★'.repeat(review.rating) }}</span></div
              ><p v-if="review.content">{{ review.content }}</p></article
            ><p v-if="!reviews.reviews.length" class="muted">还没有评价，来写第一条吧。</p></div
        ></div
        ><div class="discussion-area"
          ><h3>课程讨论</h3
          ><form class="discussion-compose" @submit.prevent="postDiscussion"
            ><textarea v-model="discussionContent" maxlength="2000" placeholder="提问、分享学习心得或回复课程内容" />
            ><button class="outline-button" :disabled="busy || !discussionContent.trim()">发布讨论</button></form
          ><article v-for="item in discussions" :key="item.id" class="discussion-item"
            ><div class="review-meta"><strong>{{ item.authorName }}</strong><time>{{ new Date(item.createdAt).toLocaleString() }}</time></div
            ><p>{{ item.content }}</p></article
          ><p v-if="!discussions.length" class="muted">暂时没有讨论。</p></div
      ></section
      ></template
    ></section
  >
</template>
