<script setup lang="ts">
  import type { Course } from '../types'
  import { computed } from 'vue'
  import { useTraining } from '../store'
  const s = useTraining()
  const props = withDefaults(defineProps<{ course: Course; employee?: boolean }>(), {
    employee: false
  })
  const displayedRating = computed(() => {
    const reviews = s.data.reviews.filter((r) => r.courseId === props.course.id)
    return reviews.length
      ? (reviews.reduce((sum, r) => sum + r.rating, 0) / reviews.length).toFixed(1)
      : props.course.rating || '新课'
  })
  defineEmits<{ open: [course: Course]; edit: [course: Course] }>()
</script>
<template>
  <article class="course-card"
    ><button
      class="cover-button"
      :aria-label="'查看课程 ' + course.title"
      @click="$emit('open', course)"
      ><CourseCover :theme="course.theme" /><span v-if="course.required" class="required-flag"
        >必修</span
      ><span v-if="course.status !== '已发布'" class="draft-flag">{{ course.status }}</span></button
    ><div class="course-body"
      ><div class="course-meta"
        ><span class="category-label">{{ course.category }}</span
        ><span class="rating"><AppIcon name="star" :size="13" />{{ displayedRating }}</span></div
      ><button class="course-title" @click="$emit('open', course)">{{ course.title }}</button
      ><p>{{ course.subtitle }}</p
      ><div class="course-details"
        ><span><AppIcon name="user" :size="13" />{{ course.instructor }}</span
        ><span><AppIcon name="clock" :size="13" />{{ course.duration }} 分钟</span
        ><span>{{ course.lessons }} 课时</span></div
      ><div v-if="employee" class="course-bottom"
        ><div v-if="s.myLearning[course.id]" class="mini-progress"
          ><span>学习进度</span><strong>{{ course.progress }}%</strong
          ><el-progress :percentage="course.progress" :show-text="false" :stroke-width="4" /></div
        ><span v-else class="muted small">尚未报名</span
        ><button class="text-link" @click="$emit('open', course)"
          >{{
            !s.myLearning[course.id]
              ? '了解并报名'
              : course.progress === 100
                ? '回顾课程'
                : course.progress
                  ? '继续学习'
                  : '开始学习'
          }}<AppIcon name="right" :size="13" /></button></div
      ><div v-else class="course-bottom"
        ><span class="muted small"
          ><AppIcon name="user" :size="13" /> {{ course.learners }} 人学习</span
        ><button class="text-link" @click="$emit('edit', course)"
          >管理课程<AppIcon name="right" :size="13" /></button></div></div
  ></article>
</template>
