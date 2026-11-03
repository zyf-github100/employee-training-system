<script setup lang="ts">
  import type { Question } from '../types'
  import { answerIndices, questionTypeNames } from '../services/assessment'
  defineProps<{ question: Question; index: number; showAnswer?: boolean }>()
</script>
<template>
  <article class="bank-question question-preview"
    ><div
      ><span class="category-label">{{ questionTypeNames[question.type || 'single'] }}</span
      ><span class="muted">第 {{ index + 1 }} 题</span
      ><span v-if="question.points" class="question-points">{{ question.points }} 分</span></div
    ><h3>{{ question.text }}</h3
    ><div class="bank-options"
      ><span
        v-for="(o, i) in question.options"
        :key="i"
        :class="{ correct: showAnswer && answerIndices(question.answer).includes(i) }"
        >{{ String.fromCharCode(65 + i) }}. {{ o
        }}<AppIcon
          v-if="showAnswer && answerIndices(question.answer).includes(i)"
          name="check"
          :size="14" /></span></div
    ><p v-if="showAnswer" class="question-analysis"
      ><b>解析</b>{{ question.analysis || '暂未填写解析' }}</p
    ></article
  >
</template>
