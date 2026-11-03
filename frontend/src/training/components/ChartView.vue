<script setup lang="ts">
  import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
  import * as echarts from 'echarts/core'
  import { LineChart, BarChart, PieChart } from 'echarts/charts'
  import {
    TooltipComponent,
    GridComponent,
    LegendComponent,
    GraphicComponent
  } from 'echarts/components'
  import { CanvasRenderer } from 'echarts/renderers'
  import type { EChartsCoreOption } from 'echarts/core'
  echarts.use([
    LineChart,
    BarChart,
    PieChart,
    TooltipComponent,
    GridComponent,
    LegendComponent,
    GraphicComponent,
    CanvasRenderer
  ])
  const props = withDefaults(
    defineProps<{ option: EChartsCoreOption; height?: number; label?: string }>(),
    { height: 240, label: '统计图表' }
  )
  const host = ref<HTMLElement>()
  let chart: echarts.ECharts | undefined
  let observer: ResizeObserver | undefined
  onMounted(() => {
    if (host.value) {
      chart = echarts.init(host.value)
      chart.setOption(props.option)
      observer = new ResizeObserver(() => chart?.resize())
      observer.observe(host.value)
    }
  })
  watch(
    () => props.option,
    (v) => chart?.setOption(v, true),
    { deep: true }
  )
  onBeforeUnmount(() => {
    observer?.disconnect()
    chart?.dispose()
  })
</script>
<template>
  <div
    ref="host"
    class="chart-host"
    :style="{ height: height + 'px' }"
    role="img"
    :aria-label="label"
  />
</template>
