# 企业员工培训系统业务增强实施计划

> **For agentic workers:** Use the host's available task-by-task implementation workflow. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有员工培训主链路上补齐课程互动、增强考试、邀请与首次登录、组织变更追溯、内容版本治理和考试完整性保护。

**Architecture:** 继续沿用 Spring Boot + JDBC + Flyway 的后端边界，新增业务表与服务按领域拆分，所有写操作保留现有会话、数据范围、版本号和审计约定。Vue 真实入口只消费后端接口；本地环境提供可观测的通知投递适配器，生产环境通过同一接口接入邮件/SMS 服务。

**Tech Stack:** Java 17、Spring Boot 3.5、MySQL 8.4、Flyway、JUnit/Spring MockMvc、Vue 3、TypeScript、Element Plus。

## Global Constraints

- 既有员工/管理员权限、数据范围、CSRF、幂等和乐观锁约定必须继续生效。
- 已发布试卷的题目快照不可被题库编辑影响；主观题阅卷只能由有权限的管理员操作。
- 课程、章节和组织批量变更不能破坏已有报名、学习进度、考试答卷和证书事实。
- 所有新增业务写请求必须有稳定错误码，并在需要时记录管理审计。

---

### Task 1: 课程互动与内容版本治理

**Files:**
- Create: `backend/src/main/resources/db/migration/V18__course_engagement_and_versions.sql`
- Create: `backend/src/main/java/com/zhixing/training/course/EngagementController.java`
- Create: `backend/src/main/java/com/zhixing/training/course/EngagementRepository.java`
- Modify: `backend/src/main/java/com/zhixing/training/course/CourseDtos.java`, `CourseRepository.java`, `LearningRepository.java`
- Modify: `frontend/src/connected/api.ts`, `frontend/src/training/live/Learning.vue`, `frontend/src/training/live/Courses.vue`, `frontend/src/training/live/admin/CourseManagement.vue`
- Test: `backend/src/test/java/com/zhixing/training/CourseEngagementTests.java`

**Interfaces:**
- Produces `GET/POST/PUT /api/v1/employee/courses/{courseId}/reviews`, `GET/POST /api/v1/employee/courses/{courseId}/discussions`, and admin moderation endpoints.
- Produces course/chapter revision metadata and publish/restore endpoints with version conflict protection.

- [x] Add one review per employee/course with 1–5 rating, update semantics, aggregate score/count, and discussion threads with soft moderation.
- [x] Freeze a published course revision and chapter/resource manifest; editing creates a draft revision and publishing records the prior revision.
- [x] Preserve existing progress against the revision first enrolled; expose current and enrolled revision IDs.
- [x] Add focused tests for duplicate review updates, unauthorized discussion access, concurrent publish conflicts, and progress preservation.

### Task 2: 题库导入、随机组卷与主观题阅卷

**Files:**
- Create: `backend/src/main/resources/db/migration/V19__assessment_enhancements.sql`
- Modify: `backend/src/main/java/com/zhixing/training/exam/ExamDtos.java`, `ExamRepository.java`, `ExamController.java`
- Create: `backend/src/main/java/com/zhixing/training/exam/QuestionImportService.java`
- Modify: `frontend/src/connected/api.ts`, `frontend/src/training/live/admin/QuestionBank.vue`, `frontend/src/training/live/admin/Exams.vue`, `frontend/src/training/live/ExamRoom.vue`
- Test: `backend/src/test/java/com/zhixing/training/AssessmentEnhancementTests.java`

**Interfaces:**
- Produces question template/preview/commit endpoints, random blueprint creation, subjective answer submission, admin grading, and result publication endpoints.

- [x] Add XLSX/CSV question import with preview, row errors, idempotent commit, and duplicate handling before any write.
- [x] Add random assembly rules by category/type/difficulty/count/points; freeze the generated paper and reject insufficient pools.
- [x] Add `SUBJECTIVE` questions, pending/manual scores, grader/version fields, and recompute final score only after all required subjective items are graded.
- [x] Add tests for malformed imports, insufficient random pools, concurrent grading, score bounds, and employee result visibility.

### Task 3: 邀请投递、首次登录改密与组织变更追溯

**Files:**
- Create: `backend/src/main/resources/db/migration/V20__identity_delivery_and_org_history.sql`
- Create: `backend/src/main/java/com/zhixing/training/communication/InvitationService.java`
- Modify: `backend/src/main/java/com/zhixing/training/auth/AuthService.java`, `AccountSetupService.java`, `UserRepository.java`
- Modify: `backend/src/main/java/com/zhixing/training/organization/OrganizationBatchService.java`, `OrganizationBatchController.java`
- Modify: `frontend/src/connected/api.ts`, `frontend/src/training/live/Login.vue`, `frontend/src/training/live/AccountSetup.vue`, `frontend/src/training/live/admin/OrganizationPanel.vue`, `EmployeeBatchPanel.vue`
- Test: `backend/src/test/java/com/zhixing/training/IdentityAndOrganizationHistoryTests.java`

**Interfaces:**
- Produces invitation send/retry/status endpoints for email/SMS, forced first-login password change, batch history with before/after snapshots, and guarded rollback endpoints.

- [x] Mark imported/administrator-created accounts as `MUST_CHANGE_PASSWORD`; block normal APIs until the password is changed while allowing setup/login routes.
- [x] Persist invitation outbox attempts with deduplication, delivery status, retry, and a provider-neutral email/SMS adapter.
- [x] Persist organization batch before/after snapshots and expose paginated history; rollback only unchanged targets with explicit conflict results.
- [x] Add tests for first-login blocking, expired/revoked invitations, delivery retry idempotency, partial rollback refusal, and audit records.

### Task 4: 考试完整性与防作弊增强

**Files:**
- Create: `backend/src/main/resources/db/migration/V21__exam_integrity.sql`
- Modify: `backend/src/main/java/com/zhixing/training/exam/ExamRepository.java`, `ExamController.java`, `ExamDtos.java`
- Create: `backend/src/main/java/com/zhixing/training/exam/ExamIntegrityService.java`
- Modify: `frontend/src/training/live/ExamRoom.vue`, `frontend/src/connected/api.ts`
- Test: `backend/src/test/java/com/zhixing/training/ExamIntegrityTests.java`

**Interfaces:**
- Produces attempt integrity events, heartbeat/visibility/focus reporting, suspicious-attempt review data, and administrator disposition endpoints.

- [x] Record server-timestamped start, heartbeat, visibility, focus, answer-save, submit, and reconnect events without trusting client time.
- [x] Detect impossible timing, excessive focus loss, concurrent sessions, answer bursts, and late writes; keep automatic grading but mark attempts for review.
- [x] Add employee warnings and admin review/disposition with immutable evidence and audit records.
- [x] Add tests proving late answers are rejected, concurrent sessions are flagged, event ordering is monotonic, and review decisions cannot alter immutable answers.

## Unresolved product decisions

- 当前邮件/SMS 只做模拟投递：记录渠道、目标地址、状态和初始化链接，不调用真实供应商；后续如有需要可沿同一接口接入供应商。
- The default rollback policy is guarded rollback of unchanged targets; product may later choose a stricter approval workflow without changing the stored snapshots.
