<script setup lang="ts">
  withDefaults(defineProps<{ theme: string; compact?: boolean }>(), { compact: false })
  const captions: Record<string, string[]> = {
    communication: ['COMMUNICATION', '连接，让协作发生'],
    ai: ['AI AT WORK', '工作方式，向新而行'],
    leadership: ['LEADERSHIP', '成为团队的引领者'],
    security: ['INFORMATION SECURITY', '安全，源于每个细节'],
    onboarding: ['A NEW BEGINNING', '每一段成长，从这里开始'],
    data: ['DATA INSIGHTS', '让数据，讲述答案']
  }
</script>
<template>
  <div class="course-cover" :class="[theme, { compact }]"
    ><div class="cover-text"
      ><span>{{ captions[theme]?.[0] || 'KEEP LEARNING' }}</span
      ><strong>{{ captions[theme]?.[1] || '开启新的成长旅程' }}</strong></div
    ><svg viewBox="0 0 240 150" class="cover-art" aria-hidden="true">
      <template v-if="theme === 'communication'">
        <rect x="80" y="22" width="100" height="70" rx="18" fill="currentColor" opacity=".9" />
        <path d="M99 85v23l27-21" fill="currentColor" />
        <rect x="125" y="64" width="87" height="58" rx="16" fill="#fff" opacity=".88" />
        <path d="M195 115v18l-20-18" fill="#fff" opacity=".88" />
        <circle v-for="x in [105, 130, 155]" :key="x" :cx="x" cy="55" r="4" fill="white" />
        <path d="M144 87h49m-49 13h34" stroke="currentColor" stroke-width="4" opacity=".65" />
      </template>
      <template v-else-if="theme === 'ai'">
        <rect x="101" y="33" width="93" height="88" rx="22" fill="currentColor" opacity=".8" />
        <rect x="117" y="49" width="61" height="53" rx="11" fill="white" opacity=".8" />
        <circle cx="134" cy="70" r="5" fill="currentColor" />
        <circle cx="162" cy="70" r="5" fill="currentColor" />
        <path
          d="M136 87h24m-12-54V17m-47 60H88m105 0h15"
          stroke="currentColor"
          stroke-width="7"
          stroke-linecap="round"
        />
        <circle cx="148" cy="15" r="7" fill="currentColor" />
        <path d="m210 24 4 9 10 4-10 4-4 10-4-10-9-4 9-4Z" fill="white" />
      </template>
      <template v-else-if="theme === 'leadership'">
        <path d="M90 125V93h40v32m0 0V65h40v60m0 0V33h40v92" fill="currentColor" opacity=".7" />
        <path
          d="m82 83 49-36 32 6 41-38m-28 0h29v27"
          fill="none"
          stroke="white"
          stroke-width="7"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </template>
      <template v-else-if="theme === 'security'">
        <path
          d="m151 17 57 21v43c0 31-57 53-57 53S96 112 96 81V38Z"
          fill="currentColor"
          opacity=".8"
        />
        <path
          d="m151 33 42 16v31c0 22-42 40-42 40s-41-18-41-40V49Z"
          fill="none"
          stroke="white"
          opacity=".55"
          stroke-width="2"
        />
        <path
          d="m130 74 14 15 29-32"
          stroke="white"
          stroke-width="8"
          fill="none"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </template>
      <template v-else-if="theme === 'data'">
        <rect x="91" y="27" width="120" height="99" rx="12" fill="white" opacity=".75" />
        <path d="M111 105V73h18v32m13 0V48h18v57m13 0V62h18v43" fill="currentColor" opacity=".8" />
        <path d="M110 112h82" stroke="currentColor" opacity=".35" stroke-width="2" />
      </template>
      <template v-else>
        <path d="m86 102 62-26 64 26-64 29Z" fill="currentColor" opacity=".5" />
        <path d="m86 82 62-26 64 26-64 29Z" fill="currentColor" opacity=".8" />
        <path d="m86 62 62-26 64 26-64 29Z" fill="white" opacity=".85" />
        <path
          d="M148 61V17m-15 14 15-15 15 15"
          stroke="currentColor"
          stroke-width="7"
          fill="none"
          stroke-linecap="round"
        />
      </template></svg
    ><span class="cover-brand">ZHIXING LEARNING</span></div
  >
</template>
