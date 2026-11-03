<script setup lang="ts">
  import { onMounted, ref } from 'vue'
  import { api, type Course } from '../../connected/api'
  import { errorMessage } from './session'
  const courses = ref<Course[]>([]),
    keyword = ref(''),
    page = ref(1),
    total = ref(0),
    loading = ref(true),
    error = ref('')
  let revision = 0
  async function load(reset = false) {
    if (reset) page.value = 1
    const request = ++revision
    loading.value = true
    error.value = ''
    try {
      const data = await api.courses(false, page.value, 12, keyword.value)
      if (request === revision) {
        courses.value = data.list
        total.value = data.total
      }
    } catch (e) {
      if (request === revision) error.value = errorMessage(e)
    } finally {
      if (request === revision) loading.value = false
    }
  }
  function changePage(delta: number) {
    page.value += delta
    void load()
  }
  onMounted(() => load())
</script>
<template>
  <section
    ><div class="section-heading"
      ><div
        ><span class="eyebrow">EXPLORE / KEEP LEARNING</span><h1>发现下一段成长</h1
        ><p>探索课程，让学习成为每天的一点积累。</p></div
      ></div
    ><form class="toolbar" @submit.prevent="load(true)"
      ><input v-model="keyword" aria-label="搜索课程" placeholder="搜索课程名称或内容" /><button
        class="primary-button"
        :disabled="loading"
        >搜索</button
      ><span>共 {{ total }} 门课程</span></form
    ><div v-if="error" class="api-alert" role="alert"
      >{{ error }}<button @click="load()">重试</button></div
    ><div v-if="loading" class="empty-state">正在读取课程…</div
    ><div v-else-if="!courses.length && !error" class="empty-state">暂无符合条件的课程</div
    ><div v-else class="live-course-grid"
      ><article v-for="(course, index) in courses" :key="course.id" class="live-course-card"
        ><CourseCover
          :theme="['security', 'communication', 'onboarding', 'data'][index % 4]!"
        /><div class="live-course-body"
          ><span class="course-meta"
            >{{ course.category || '通用课程' }} · {{ course.durationMinutes }} 分钟</span
          ><h2>{{ course.title }}</h2
          ><p>{{ course.summary || '暂无课程简介' }}</p
          ><div class="course-footer"
            ><small>{{ course.chapterCount }} 个章节</small
            ><router-link :to="`/learn/${course.id}`" class="text-button"
              >查看课程 →</router-link
            ></div
          ></div
        ></article
      ></div
    ><div class="live-pagination"
      ><button class="outline-button" :disabled="page === 1 || loading" @click="changePage(-1)"
        >上一页</button
      ><span>第 {{ page }} / {{ Math.max(1, Math.ceil(total / 12)) }} 页</span
      ><button
        class="outline-button"
        :disabled="page * 12 >= total || loading"
        @click="changePage(1)"
        >下一页</button
      ></div
    ></section
  >
</template>
