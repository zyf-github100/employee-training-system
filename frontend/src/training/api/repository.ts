import { createSeed } from './seed'
import { upgradeTrainingData } from './migrate'
import type { TrainingData } from '../types'
export const STORAGE_KEY = 'zhixing-training-v1'
// Local demo persistence. Production must use authenticated asynchronous services, not browser state.
export const trainingRepository = {
  load(): TrainingData {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) {
        const data = JSON.parse(raw)
        if (
          Array.isArray(data.courses) &&
          Array.isArray(data.employees) &&
          Array.isArray(data.plans) &&
          Array.isArray(data.exams) &&
          Array.isArray(data.feedback) &&
          Array.isArray(data.notices) &&
          data.settings &&
          data.attempts
        )
          return upgradeTrainingData(data)
      }
    } catch {
      /* A malformed saved demo must not prevent the application from opening. */
    }
    return upgradeTrainingData(createSeed())
  },
  save(data: TrainingData): boolean {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
      return true
    } catch {
      return false
    }
  }
}
export function exportCsv(filename: string, headers: string[], rows: unknown[][]) {
  const cell = (value: unknown) => {
    let s = String(value ?? '')
    if (/^[=+@\-\t\r]/.test(s)) s = "'" + s
    return '"' + s.replaceAll('"', '""') + '"'
  }
  const data = '\uFEFF' + [headers, ...rows].map((row) => row.map(cell).join(',')).join('\r\n')
  download(filename, new Blob([data], { type: 'text/csv;charset=utf-8' }))
}
export function download(filename: string, blob: Blob) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
