import { shallowRef } from 'vue'

export type Role = 'ADMIN' | 'EMPLOYEE'
export type User = {
  id: number
  employeeNo: string
  name: string
  departmentName?: string
  jobTitle?: string
  email?: string
  phone?: string
  roleCode: Role
  version: number
}
export type Department = {
  id: number
  parentId: number | null
  code: string
  name: string
  status: string
  sortNo: number
  version: number
  leaderUserId: number | null
  leaderName: string | null
  leaderEmployeeNo: string | null
}
export type DataScope = 'SELF' | 'DEPARTMENT' | 'DEPARTMENT_TREE' | 'CUSTOM' | 'ALL'
export type Access = { superAdmin: boolean; dataScope: DataScope; departmentIds: number[] }
export type Employee = {
  id: number
  employeeNo: string
  name: string
  departmentId: number
  departmentName?: string
  jobTitle?: string
  email?: string
  phone?: string
  roleCode: Role
  superAdmin: boolean
  dataScope: DataScope
  customDepartmentIds: number[]
  status: string
  activated: boolean
  lockedUntil?: string
  version: number
}
export type ImportRow = {
  rowNumber: number
  fields: {
    employeeNo: string
    name: string
    departmentCode: string
    jobTitle: string
    email: string
    phone: string
  }
  errors: string[]
}
export type ImportPreview = {
  id: string
  status: string
  totalRows: number
  validRows: number
  errorRows: number
  expiresAt: string
  committedAt?: string
  pageNo: number
  pageSize: number
  rows: ImportRow[]
}
export type ImportSummary = {
  id: string
  status: string
  totalRows: number
  createdAt: string
  expiresAt: string
  committedAt?: string
}
export type SetupLink = {
  userId: number
  employeeNo: string
  token: string
  purpose: string
  expiresAt: string
}
export type BatchTarget = { id: number; version: number }
export type BatchResult = {
  requestId: string
  requested: number
  changed: number
  clearedLeaders: number
}
export type TransferRequest = {
  requestId: string
  targets: BatchTarget[]
  departmentId: number
  updateJobTitle: boolean
  jobTitle: string
}
export type BatchRoleRequest = {
  requestId: string
  targets: BatchTarget[]
  roleCode: Role
  dataScope: DataScope
  customDepartmentIds: number[]
}
export type EmployeeForm = Omit<Employee, 'id' | 'departmentName' | 'lockedUntil'> & {
  initialPassword: string
}
export type AuditEntry = {
  id: number
  actorId: number | null
  actorName: string | null
  action: string
  target: string
  httpStatus: number
  outcome: string
  occurredAt: string
}
export type Course = {
  id: number
  title: string
  summary?: string
  coverUrl?: string
  category?: string
  difficulty: string
  status: 'DRAFT' | 'PUBLISHED'
  durationMinutes: number
  chapterCount: number
  publishedAt?: string
  version: number
}
export type Resource = {
  id: number
  chapterId: number
  title: string
  resourceType: string
  resourceUrl?: string
  durationSeconds?: number
  sizeBytes?: number
  sortNo: number
  originalName?: string
  mediaType?: string
  status?: string
}
export type Chapter = {
  id: number
  courseId: number
  title: string
  sortNo: number
  contentMarkdown?: string
  durationMinutes: number
  resources: Resource[]
}
export type CourseDetail = {
  course: Course
  chapters: Chapter[]
  progressPercent: number
  completedChapters: number
  enrolled: boolean
}
export type Progress = {
  chapterId: number
  progressPercent: number
  lastPositionSeconds: number
  completed: boolean
  completedAt?: string
}
export type BankQuestion = {
  id: number
  type: 'single' | 'multiple' | 'boolean'
  stem: string
  options: string[]
  answer: number[]
  analysis?: string
  category: string
  difficulty: string
  status: string
  version: number
}
export type Exam = {
  id: number
  title: string
  category: string
  durationMinutes: number
  passScore: number
  deadline: string
  status: string
  questionCount: number
  totalPoints: number
  attemptCount: number
  version: number
}
export type PaperQuestion = {
  id: number
  questionId: number
  type: string
  stem: string
  options: string[]
  points: number
  analysis?: string
}
export type ExamDetail = { exam: Exam; questions: PaperQuestion[] }
export type Attempt = {
  id: number
  paperId: number
  title: string
  status: string
  score?: number
  passed?: boolean
  startedAt: string
  submittedAt?: string
}
export type AttemptDetail = {
  attempt: Attempt
  questions: PaperQuestion[]
  remainingSeconds: number
  answers: Record<number, number[]>
}
export type TrainingPlan = {
  id: number
  title: string
  description?: string
  category: string
  startDate: string
  endDate: string
  status: string
  totalItems: number
  completedItems: number
  progressPercent: number
  assigneeCount: number
  version: number
}
export type PlanItem = {
  itemType: 'COURSE' | 'EXAM'
  itemId: number
  title: string
  required: boolean
  progressPercent: number
  status: string
}
export type PlanDetail = { plan: TrainingPlan; items: PlanItem[] }
export type EmployeeArchive = {
  employeeNo: string
  name: string
  departmentName?: string
  courses: {
    enrolled: number
    completed: number
    averageProgressPercent: number
    completedMinutes: number
  }
  exams: { attempts: number; submitted: number; passed: number; averageScore: number }
  plans: { assigned: number; completed: number; averageProgressPercent: number }
  recentRecords: Array<{
    recordType: string
    recordId: number
    title: string
    status: string
    progressPercent: number
    score?: number
    updatedAt?: string
  }>
}
export type AnalyticsSummary = {
  activeEmployees: number
  publishedCourses: number
  enrollments: number
  completedEnrollments: number
  courseCompletionRate: number
  publishedExams: number
  submittedAttempts: number
  passedAttempts: number
  examPassRate: number
  publishedPlans: number
  planAssignments: number
  completedPlanAssignments: number
  planCompletionRate: number
}
export type Certificate = {
  id: number
  certificateNo: string
  planId: number
  planTitle: string
  recipientName: string
  employeeNo: string
  title: string
  issuedAt: string
  revokedAt?: string
  status: string
}
type Envelope<T> = { code: string; message: string; data: T; traceId: string }
export type Page<T> = { list: T[]; pageNo: number; pageSize: number; total: number }

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public traceId?: string
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

let csrfToken = ''
let accessToken = ''
let refreshPromise: Promise<void> | null = null

async function parse<T>(response: Response): Promise<T> {
  const body = (await response.json().catch(() => ({}))) as Partial<Envelope<T>>
  if (!response.ok || body.code !== 'OK')
    throw new ApiError(
      response.status,
      body.code ?? 'HTTP_ERROR',
      body.message ?? '请求失败',
      body.traceId
    )
  return body.data as T
}

export async function csrf(): Promise<string> {
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'include' })
  csrfToken = await parse<string>(response)
  return csrfToken
}

function mutating(method: string) {
  return !['GET', 'HEAD', 'OPTIONS'].includes(method)
}

async function raw<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  if (mutating(method) && !csrfToken) await csrf()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type'))
    headers.set('Content-Type', 'application/json')
  if (mutating(method) && csrfToken) headers.set('X-XSRF-TOKEN', csrfToken)
  // Refresh is intentionally anonymous: an expired bearer token must not be allowed to
  // short-circuit the refresh endpoint before the HttpOnly cookie is evaluated.
  if (
    accessToken &&
    !['/api/v1/auth/refresh', '/api/v1/auth/login', '/api/v1/auth/account-setup'].includes(path)
  )
    headers.set('Authorization', `Bearer ${accessToken}`)
  const response = await fetch(path, { ...init, headers, credentials: 'include' })
  if (
    response.status === 401 &&
    retry &&
    path !== '/api/v1/auth/login' &&
    path !== '/api/v1/auth/refresh'
  ) {
    await refresh()
    return raw<T>(path, init, false)
  }
  return parse<T>(response)
}

async function binary(path: string, method: 'GET' | 'POST' = 'GET', retry = true): Promise<Blob> {
  if (mutating(method) && !csrfToken) await csrf()
  const headers = new Headers({ Accept: '*/*' })
  if (mutating(method)) headers.set('X-XSRF-TOKEN', csrfToken)
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  const response = await fetch(path, { method, headers, credentials: 'include' })
  if (response.status === 401 && retry) {
    await refresh()
    return binary(path, method, false)
  }
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as Partial<Envelope<unknown>>
    throw new ApiError(
      response.status,
      body.code ?? 'FILE_DOWNLOAD_FAILED',
      body.message ?? '资源读取失败',
      body.traceId
    )
  }
  return response.blob()
}

export async function refresh(): Promise<void> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      // Cookie-backed CSRF state may rotate while this tab keeps an access token.
      // Fetch its current value before the anonymous, cookie-authenticated refresh.
      await csrf()
      const data = await raw<{ accessToken: string; user: User }>(
        '/api/v1/auth/refresh',
        { method: 'POST' },
        false
      )
      accessToken = data.accessToken
      currentUser.value = data.user
      await csrf()
    })()
      .catch((error: unknown) => {
        if (error instanceof ApiError && error.status === 401) clearSession()
        throw error
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

export async function login(employeeNo: string, password: string) {
  await csrf()
  const data = await raw<{ accessToken: string; user: User }>(
    '/api/v1/auth/login',
    { method: 'POST', body: JSON.stringify({ employeeNo, password }) },
    false
  )
  accessToken = data.accessToken
  currentUser.value = data.user
  await csrf()
  return data.user
}

export async function logout() {
  await raw<void>('/api/v1/auth/logout', { method: 'POST' })
  clearSession()
}

export function clearSession() {
  accessToken = ''
  csrfToken = ''
  currentUser.value = null
}
export const currentUser = shallowRef<User | null>(null)
export const api = {
  importTemplate: () => binary('/api/v1/admin/employee-imports/template'),
  importPreview: (file: File) => {
    const body = new FormData()
    body.append('file', file)
    return raw<ImportPreview>('/api/v1/admin/employee-imports', { method: 'POST', body })
  },
  importRecent: () => raw<ImportSummary[]>('/api/v1/admin/employee-imports'),
  importRead: (id: string, pageNo = 1) =>
    raw<ImportPreview>(`/api/v1/admin/employee-imports/${id}?pageNo=${pageNo}`),
  importCommit: (id: string) =>
    raw<ImportPreview>(`/api/v1/admin/employee-imports/${id}/commit`, { method: 'POST' }),
  importReport: (id: string) => binary(`/api/v1/admin/employee-imports/${id}/report`),
  importActivations: (id: string) =>
    binary(`/api/v1/admin/employee-imports/${id}/activation-links`, 'POST'),
  issueSetupLink: (id: number, version: number) =>
    raw<SetupLink>(`/api/v1/admin/employees/${id}/setup-link`, {
      method: 'POST',
      body: JSON.stringify({ version })
    }),
  completeAccountSetup: (employeeNo: string, token: string, newPassword: string) =>
    raw<void>(
      '/api/v1/auth/account-setup',
      { method: 'POST', body: JSON.stringify({ employeeNo, token, newPassword }) },
      false
    ),
  access: () => raw<Access>('/api/v1/admin/access'),
  departments: () => raw<Department[]>('/api/v1/admin/departments/tree'),
  setDepartmentLeader: (id: number, leaderUserId: number | null, version: number) =>
    raw<void>(`/api/v1/admin/departments/${id}/leader`, {
      method: 'PUT',
      body: JSON.stringify({ leaderUserId, version })
    }),
  batchTransfer: (body: TransferRequest) =>
    raw<BatchResult>('/api/v1/admin/employees/batch/transfer', {
      method: 'POST',
      body: JSON.stringify(body)
    }),
  batchRoles: (body: BatchRoleRequest) =>
    raw<BatchResult>('/api/v1/admin/employees/batch/roles', {
      method: 'POST',
      body: JSON.stringify(body)
    }),
  saveDepartment: (id: number | null, body: object) =>
    raw<number>(`/api/v1/admin/departments${id ? `/${id}` : ''}`, {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(body)
    }),
  employees: (pageNo = 1, keyword = '', departmentId = '', status = '', role = '') =>
    raw<Page<Employee>>(
      `/api/v1/admin/employees?${new URLSearchParams({ pageNo: String(pageNo), pageSize: '20', keyword, departmentId, status, role })}`
    ),
  saveEmployee: (id: number | null, body: EmployeeForm) =>
    raw<Employee>(`/api/v1/admin/employees${id ? `/${id}` : ''}`, {
      method: id ? 'PUT' : 'POST',
      body: JSON.stringify(body)
    }),
  unlockEmployee: (id: number, version: number) =>
    raw<Employee>(`/api/v1/admin/employees/${id}/unlock`, {
      method: 'POST',
      body: JSON.stringify({ version })
    }),
  auditLogs: (pageNo = 1, outcome = '') =>
    raw<Page<AuditEntry>>(
      `/api/v1/admin/audit-logs?${new URLSearchParams({ pageNo: String(pageNo), pageSize: '20', outcome })}`
    ),
  me: () => raw<User>('/api/v1/me'),
  courses: (admin: boolean, pageNo = 1, pageSize = 20, keyword = '') =>
    raw<Page<Course>>(
      `/api/v1/${admin ? 'admin' : 'employee'}/courses?pageNo=${pageNo}&pageSize=${pageSize}&keyword=${encodeURIComponent(keyword)}`
    ),
  updateProfile: (body: {
    name: string
    email?: string
    phone?: string
    jobTitle?: string
    version: number
  }) => raw<User>('/api/v1/me/profile', { method: 'PUT', body: JSON.stringify(body) }),
  changePassword: (currentPassword: string, newPassword: string) =>
    raw<void>('/api/v1/me/password', {
      method: 'PUT',
      body: JSON.stringify({ currentPassword, newPassword })
    }),
  createCourse: (body: object) =>
    raw<Course>('/api/v1/admin/courses', { method: 'POST', body: JSON.stringify(body) }),
  updateCourse: (id: number, body: object) =>
    raw<Course>(`/api/v1/admin/courses/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  courseDetail: (id: number, admin = false) =>
    raw<CourseDetail>(`/api/v1/${admin ? 'admin' : 'employee'}/courses/${id}`),
  enroll: (id: number) =>
    raw<CourseDetail>(`/api/v1/employee/courses/${id}/enrollment`, { method: 'POST', body: '{}' }),
  completeChapter: (courseId: number, chapterId: number, progressPercent = 100) =>
    raw<Progress>(`/api/v1/employee/courses/${courseId}/chapters/${chapterId}/progress`, {
      method: 'PUT',
      body: JSON.stringify({
        progressPercent,
        lastPositionSeconds: 0,
        completed: progressPercent === 100
      })
    }),
  courseProgress: (courseId: number) =>
    raw<Progress[]>(`/api/v1/employee/courses/${courseId}/progress`),
  saveProgress: (
    courseId: number,
    chapterId: number,
    progressPercent: number,
    lastPositionSeconds: number,
    completed = false
  ) =>
    raw<Progress>(`/api/v1/employee/courses/${courseId}/chapters/${chapterId}/progress`, {
      method: 'PUT',
      body: JSON.stringify({ progressPercent, lastPositionSeconds, completed })
    }),
  uploadResource: (chapterId: number, file: File, title: string, sortNo = 1) => {
    const body = new FormData()
    body.append('file', file)
    body.append('title', title)
    body.append('sortNo', String(sortNo))
    return raw<Resource>(`/api/v1/admin/course-chapters/${chapterId}/resources`, {
      method: 'POST',
      body
    })
  },
  deleteResource: (resourceId: number) =>
    raw<void>(`/api/v1/admin/course-resources/${resourceId}`, { method: 'DELETE' }),
  resourceBlob: (resourceId: number) => binary(`/api/v1/resources/${resourceId}/content`),
  questions: (keyword = '', status = '') =>
    raw<BankQuestion[]>(
      `/api/v1/admin/questions?keyword=${encodeURIComponent(keyword)}${status ? `&status=${encodeURIComponent(status)}` : ''}`
    ),
  createQuestion: (body: object) =>
    raw<BankQuestion>('/api/v1/admin/questions', { method: 'POST', body: JSON.stringify(body) }),
  exams: (admin = false) => raw<Exam[]>(`/api/v1/${admin ? 'admin' : 'employee'}/exams`),
  examDetail: (id: number, admin = false) =>
    raw<ExamDetail>(`/api/v1/${admin ? 'admin' : 'employee'}/exams/${id}`),
  createExam: (body: object) =>
    raw<ExamDetail>('/api/v1/admin/exams', { method: 'POST', body: JSON.stringify(body) }),
  publishExam: (id: number) =>
    raw<ExamDetail>(`/api/v1/admin/exams/${id}/publish`, { method: 'POST', body: '{}' }),
  beginExam: (id: number) =>
    raw<AttemptDetail>(`/api/v1/employee/exams/${id}/attempts`, { method: 'POST', body: '{}' }),
  submitAttempt: (id: number, answers: Array<{ paperQuestionId: number; answer: number[] }>) =>
    raw<Attempt>(`/api/v1/employee/exam-attempts/${id}/submit`, {
      method: 'POST',
      body: JSON.stringify({ answers })
    }),
  attemptDetail: (id: number) => raw<AttemptDetail>(`/api/v1/employee/exam-attempts/${id}`),
  activeAttempts: () => raw<Attempt[]>('/api/v1/employee/exam-attempts/active'),
  myAttempts: () => raw<Attempt[]>('/api/v1/employee/exam-attempts'),
  saveAttemptAnswers: (id: number, answers: Array<{ paperQuestionId: number; answer: number[] }>) =>
    raw<AttemptDetail>(`/api/v1/employee/exam-attempts/${id}/answers`, {
      method: 'PUT',
      body: JSON.stringify({ answers })
    }),
  plans: (admin = false) => raw<TrainingPlan[]>(`/api/v1/${admin ? 'admin' : 'employee'}/plans`),
  planDetail: (id: number, admin = false) =>
    raw<PlanDetail>(`/api/v1/${admin ? 'admin' : 'employee'}/plans/${id}`),
  createPlan: (body: object) =>
    raw<PlanDetail>('/api/v1/admin/plans', { method: 'POST', body: JSON.stringify(body) }),
  eligibleEmployees: () =>
    raw<Array<{ id: number; employeeNo: string; name: string; departmentName?: string }>>(
      '/api/v1/admin/plans/eligible-employees'
    ),
  publishPlan: (id: number, employeeIds: number[]) =>
    raw<PlanDetail>(`/api/v1/admin/plans/${id}/publish`, {
      method: 'POST',
      body: JSON.stringify({ employeeIds })
    }),
  refreshPlan: (id: number) =>
    raw<PlanDetail>(`/api/v1/employee/plans/${id}/progress`, { method: 'POST', body: '{}' }),
  employeeArchive: () => raw<EmployeeArchive>('/api/v1/employee/archive'),
  analytics: () => raw<AnalyticsSummary>('/api/v1/admin/analytics'),
  certificates: (admin = false) =>
    raw<Certificate[]>(`/api/v1/${admin ? 'admin' : 'employee'}/certificates`)
}
