<script setup lang="ts">
  import { computed } from 'vue'
  const p = defineProps<{ status: string }>()
  const tone = computed(() =>
    ['进行中', '已发布', '在职', '已回复', '已完成', '已通过', '启用'].includes(p.status)
      ? 'green'
      : ['草稿', '未开始', '待处理'].includes(p.status)
        ? 'amber'
        : ['已停用', '停用', '已下架', '未通过'].includes(p.status)
          ? 'red'
          : 'neutral'
  )
</script>
<template>
  <span class="status-badge" :class="tone"><i />{{ status }}</span>
</template>
