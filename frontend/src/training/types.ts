export type Role = 'admin' | 'employee'
export type CourseCategory = '通用能力' | '专业技能' | '管理能力' | '合规安全' | '新人必修'
export interface Course {
  chapters?: CourseChapter[]
  id: number
  title: string
  subtitle: string
  category: CourseCategory
  instructor: string
  duration: number
  lessons: number
  learners: number
  rating: number
  status: '已发布' | '草稿' | '已下架'
  theme: string
  required: boolean
  description: string
  progress: number
  completedLessons?: number[]
}
export interface Plan {
  participantIds?: number[]
  rosterUpdatedAt?: string
  id: number
  title: string
  department: string
  start: string
  end: string
  participants: number
  completed: number
  status: '进行中' | '未开始' | '已完成' | '草稿'
  courseIds: number[]
  description: string
}
export interface Employee {
  phone?: string
  bio?: string
  id: number
  no: string
  name: string
  department: string
  job: string
  email: string
  status: '在职' | '已停用'
  progress: number
  hours: number
  avatar: string
}
export interface Question {
  type?: QuestionType
  points?: number
  id: number
  text: string
  options: string[]
  answer: Answer
  analysis: string
}
export interface Exam {
  audienceDepartment?: string
  participantIds?: number[]
  assemblyMode?: 'manual' | 'random'
  id: number
  title: string
  category: string
  duration: number
  passScore: number
  questions: Question[]
  status: '进行中' | '未开始' | '已结束' | '草稿'
  deadline: string
  participants: number
}
export interface Attempt {
  questionSnapshot?: Question[]
  passScore?: number
  examTitle?: string
  employeeId?: number
  examId: number
  answers: Record<number, Answer>
  deadline: number
  submitted: boolean
  score: number
  submittedAt?: string
}
export interface Feedback {
  employeeId?: number
  id: number
  title: string
  content: string
  type: string
  author: string
  status: '待处理' | '已回复'
  date: string
  reply: string
}
export interface Notice {
  id: number
  title: string
  category: string
  date: string
  content: string
  read: boolean
}
export interface Settings {
  company: string
  admin: string
  email: string
  reminder: boolean
  autoSave: boolean
  theme: string
}
export interface TrainingData {
  questionBank: BankQuestion[]
  schemaVersion: number
  accounts: DemoAccount[]
  learning: Record<number, Record<number, LearningRecord>>
  employeeAttempts: Record<number, Record<number, Attempt>>
  reviews: CourseReview[]
  courses: Course[]
  plans: Plan[]
  employees: Employee[]
  exams: Exam[]
  feedback: Feedback[]
  notices: Notice[]
  attempts: Record<number, Attempt>
  settings: Settings
}

export interface DemoAccount {
  no: string
  role: Role
  employeeId: number | null
  active: boolean
  passwordHash: string
  salt: string
  passwordChangedAt?: string
}
export interface LearningRecord {
  courseId: number
  enrolledAt: string
  lastStudiedAt: string | null
  completedAt: string | null
  completedLessons: number[]
  progress: number
  seconds: number
  lastChapter: number
  note: string
  videoPositions: Record<string, number>
}
export interface CourseReview {
  id: string
  courseId: number
  employeeId: number
  rating: number
  content: string
  createdAt: string
  updatedAt: string
}

export type Answer = number | number[]
export type QuestionType = 'single' | 'multiple' | 'boolean'
export interface BankQuestion extends Question {
  type: QuestionType
  category: string
  difficulty: '基础' | '进阶' | '挑战'
  status: '启用' | '停用'
}
export interface CourseResource {
  id: string
  name: string
  type: 'video' | 'pdf' | 'ppt'
  size: number
  mime: string
  lastModified: number
  createdAt: string
}
export interface CourseChapter {
  id: string
  title: string
  duration: number
  content: string
  resources: CourseResource[]
}
