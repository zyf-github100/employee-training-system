# 学习时长与完成判定（2026-11-09）

- V16 为 `study_progress` 增加 `watched_seconds`、`last_reported_at`。
- `PUT /api/v1/employee/courses/{courseId}/chapters/{chapterId}/progress` 现在是心跳接口：客户端上报 `progressPercent/lastPositionSeconds/completed/playing`，服务端按两次请求之间的**服务器时间**累计学习时长；客户端不能提交“已学秒数”。
- 完成条件：`watched_seconds >= requiredSeconds`，且本次请求 `completed=true` 或视频进度到 100%。`requiredSeconds = max(60, round(章节预计时长 × 60 × 0.8))`。
- 防刷规则：间隔小于 3 秒不累计；大于 30 秒不累计（后台标签页、断点恢复不刷时长）；视频前进跳转按“墙钟时间 × 2 + 5 秒”钳制；同一课程的并发心跳用报名行锁串行化。
- 响应新增 `watchedSeconds/requiredSeconds/remainingSeconds`，章节详情新增 `requiredSeconds`；历史已完成记录保留 `completed_at`，不回退，历史未完成记录从 0 秒开始累计。
- 配置：`zhixing.study.completion-ratio`（默认 0.8）、`zhixing.study.min-seconds`（默认 60）。

# 反馈、公告与平台设置（2026-11-02）

Flyway V14 增加平台设置、反馈、公告和收件人。已有数据库通过正常 Flyway migrate 升级，不运行 clean。累计 71 项测试通过（新增 11 项真实 MySQL 测试）。

| 方法 | 路径 | 行为 |
| --- | --- | --- |
| GET/POST | `/api/v1/employee/feedback` | 本人分页查询/提交反馈，UUID 请求编号防重 |
| GET | `/api/v1/admin/feedback` | 当前人员数据范围内分页筛选 |
| PUT | `/api/v1/admin/feedback/{id}/reply` | 回复或更新回复，须携带当前 version |
| GET | `/api/v1/employee/announcements` | 本人收件列表，仅显示有效公告 |
| GET | `/api/v1/employee/announcements/unread-count` | 本人未读数量 |
| POST | `/api/v1/employee/announcements/{id}/read` | 本人标为已读，重试不改变首次阅读时间 |
| GET/POST | `/api/v1/admin/announcements` | 可管理公告列表/向明确员工名单发布 |
| POST | `/api/v1/admin/announcements/{id}/withdraw` | 按创建者/ALL 内容权限撤回，携带 version |
| GET | `/api/v1/employee/platform` | 所有已登录账号读取企业和培训联系信息 |
| GET/PUT | `/api/v1/admin/settings` | 管理员读取，超级管理员按 version 保存 |

反馈筛选为 `pageNo/pageSize/status/keyword`，状态支持空、`PENDING`、`REPLIED`。反馈请求为 `requestId/title/type/content`，类型为课程建议、课程提问、学习反馈、系统问题。公告请求为 `requestId/title/category/content/employeeIds`，分类为培训通知、课程上新、平台公告，名单 1–5000 人、去重、固化；当前范围外、停用、未激活或非员工账号会使整次发布失败。相同作者和 UUID 的相同规范化内容重试返回原记录，复用编号修改内容返回 409。

平台设置字段为 `companyName/contactName/contactEmail/unreadReminders/version`，不存凭据、不改变账号姓名或权限、不接入邮件/短信发送。管理写操作沿用现有审计；列表与修改均从数据库读取权限。详细验证及范围限制见 `../frontend/docs/communication-acceptance-2026-11-02.md`。

# 管理端页面配套接口（2026-11-02）

主入口管理员工作台现为 `/#/admin/dashboard`，旧 `connected.html` 兼容跳转。新增 `GET /api/v1/admin/employees/{id}/archive`，按数据库实时人员数据范围校验后返回目标员工的培训档案；读取在只读事务中完成。员工、匿名及范围外账号不能调用。累计 60 项后端测试通过，页面与浏览器证据见 `../frontend/docs/admin-workspace-acceptance-2026-11-02.md`。

# 组织批量更新（2026-10-09）

V13 增加 `org_department.leader_user_id` 和 `organization_batch`。新增接口：

| 方法 | 路径 | 权限与用途 |
| --- | --- | --- |
| POST | `/api/v1/admin/employees/batch/transfer` | 管理员在授权范围内批量调岗，普通管理员只可修改员工账号 |
| POST | `/api/v1/admin/employees/batch/roles` | 超级管理员批量设置角色和人员数据范围 |
| PUT | `/api/v1/admin/departments/{id}/leader` | 超级管理员任命或清空部门负责人 |

调岗请求示例（ID 和版本须来自当前员工列表）：

```json
{
  "requestId": "26b8a7df-3f41-44c6-823b-67fb20dc1099",
  "targets": [{ "id": 2, "version": 0 }, { "id": 3, "version": 1 }],
  "departmentId": 4,
  "updateJobTitle": true,
  "jobTitle": "培训专员"
}
```

`updateJobTitle=false` 保留每人原岗位；为 `true` 时按 `jobTitle` 更新，空值清空。来源员工与目标部门均按操作人的当前数据范围校验，目标部门必须启用。

角色请求示例：

```json
{
  "requestId": "c17ca6a9-f540-47dc-9a07-949f2b0447c5",
  "targets": [{ "id": 2, "version": 1 }],
  "roleCode": "ADMIN",
  "dataScope": "CUSTOM",
  "customDepartmentIds": [4]
}
```

`dataScope` 支持 `SELF/DEPARTMENT/DEPARTMENT_TREE/CUSTOM/ALL`；`CUSTOM` 必须提供启用部门，其余范围必须传空数组。`EMPLOYEE` 仅允许 `SELF`，降级会删除原自定义部门授权。两种批量操作都不包含操作人自己或超级管理员账号，也不授予超级管理员权限。

批量名单为 1–200 个唯一账号，携带当前 `version`。完整校验后在单个事务中写入，冲突或越权整批回滚。成功 `data` 为 `{ requestId, requested, changed, clearedLeaders }`，无变化的账号不递增版本、不撤销会话；真正改变的账号递增版本并撤销会话，旧激活码也因版本不符而失效。请求记录与业务同事务提交。同一操作人使用相同 UUID 和相同规范化配置重试返回原结果；不同操作人或配置复用编号返回 409。名单顺序及重复自定义部门不影响规范化结果。修改配置后必须生成新编号。

负责人请求为 `{ "leaderUserId": 2, "version": 0 }`，清空时 `leaderUserId=null`；版本来自部门列表。`GET /api/v1/admin/departments/tree` 新增 `leaderUserId/leaderName/leaderEmployeeNo`。负责人必须是该部门启用账号，未激活账号也可任命；组织任命不改变账号角色、数据范围或登录状态。负责人被调离或停用时自动解除任命并递增部门版本；重置密码不解除任命。

批量变更、单人组织变更和负责人设置共享权限变更锁，以串行验证角色、范围和任命。请求结果进入已有管理审计；`organization_batch` 另保存目标 ID/版本、请求配置和结果用于幂等排查，尚无历史查询接口、字段级前后快照、自动撤销或保留期限。

本轮累计 58 项真实 MySQL 后端测试通过，含 13 项新增批量/负责人测试；详细浏览器验收见 `../frontend/docs/organization-batch-acceptance-2026-10-09.md`。

# 此前更新（2026-10-08）

员工主界面现为 `frontend/index.html`，管理/联调入口仍为 `connected.html`。已修复考试解析提前返回、并发交卷覆盖、超时答卷无人访问时不收卷的问题，并补齐课程/考试到计划及证书的自动闭环。

真实 MySQL 回归测试可在项目根目录执行 `pwsh -NoProfile -File backend/scripts/isolated-mysql.ps1`。该命令在系统临时目录创建新的 MySQL 实例、随机口令和隔离库，运行后自动关闭，数据目录保留供诊断。脚本需要 PowerShell 7、本机 MySQL 可执行文件、Java 17 和 Maven；路径可通过参数覆盖。`-Mode serve` 可启动隔离 API（18081）。数据库脚本与任何正式库配置无关。

新增 `GET /api/v1/admin/plans/eligible-employees` 和 `GET /api/v1/employee/exam-attempts`。计划发布必须传 `employeeIds`；不再默认分配全员。必修项决定结业，可选项不阻塞。超时任务配置为 `zhixing.exam.expiry-enabled`、`zhixing.exam.expiry-delay-ms`（默认启用、15000ms）。

详细证据与未完成范围见根目录交接文档。

---

# 知行企业培训系统 API（阶段 4）

这一目录是前端演示工作区之后的真实后端切片，采用 Spring Boot 3.5、Java 17、Spring Security Resource Server JWT、Flyway、MySQL 8.4 和 JDBC。当前已完成认证、课程学习、资源上传/播放和题库考试最小闭环。

## 已完成边界

- `GET /api/v1/auth/csrf`：获取 CSRF 令牌；前端写请求统一携带 `X-XSRF-TOKEN`，刷新会话前重新获取当前值。登录、刷新、匿名退出等不携带 Bearer 的写请求受 CSRF 校验；Resource Server 默认豁免有效 Bearer 请求的 CSRF 检查，权限仍由会话、角色及人员范围校验。
- `POST /api/v1/auth/login`：工号登录，访问令牌只放在 JSON，刷新令牌只放在 HttpOnly Cookie。
- `POST /api/v1/auth/refresh`：刷新令牌轮换，旧会话立即撤销。
- `POST /api/v1/auth/logout`、`POST /api/v1/auth/logout-all`：撤销会话。
- `GET /api/v1/me`、`PUT /api/v1/me/profile`、`PUT /api/v1/me/password`：资料、密码和版本冲突保护。
- `GET /api/v1/employee/courses`：员工只能看到 `PUBLISHED` 课程。
- `GET /api/v1/employee/courses/{courseId}`：读取课程详情、章节、资源摘要和当前员工学习进度。
- `POST /api/v1/employee/courses/{courseId}/enrollment`：员工报名课程，重复提交保持幂等。
- `PUT /api/v1/employee/courses/{courseId}/chapters/{chapterId}/progress`：幂等写入章节进度并更新课程汇总进度。
- `GET /api/v1/employee/courses/{courseId}/progress`：读取当前员工的章节学习进度。
- `GET/POST/PUT /api/v1/admin/courses`：管理员查看、创建和编辑课程元数据。
- `GET /api/v1/admin/courses/{courseId}`、`POST/PUT /api/v1/admin/courses/{courseId}/chapters`：管理员读取和维护课程章节。
- `POST /api/v1/admin/course-chapters/{chapterId}/resources`：管理员上传视频、PDF、图片或附件；服务端校验扩展名、文件头和大小后保存到本机隔离目录。
- `DELETE /api/v1/admin/course-resources/{resourceId}`：管理员删除资源并清理对应文件。
- `GET /api/v1/resources/{resourceId}/content`：经过课程发布状态、报名关系和角色校验后读取资源内容，视频/PDF/图片以内联方式返回，其他附件以下载方式返回。
- `GET/POST/PUT /api/v1/admin/questions`：管理员查询、新增和带版本号更新题目，支持单选、多选、判断三种题型。
- `GET/POST /api/v1/admin/exams`、`GET /api/v1/admin/exams/{id}`：创建草稿试卷并查看题目快照。
- `POST /api/v1/admin/exams/{id}/publish`：校验题目和 100 分总分后发布，并为启用员工生成去重考试任务。
- `GET /api/v1/employee/exams`、`GET /api/v1/employee/exams/{id}`：员工读取已发布且已分配的考试。
- `POST /api/v1/employee/exams/{id}/attempts`：校验截止日期和次数后创建答卷；同一员工已有进行中答卷时返回原答卷，避免重复开始。
- `GET /api/v1/employee/exam-attempts/active`：读取当前员工的进行中答卷，用于刷新页面恢复考试。
- `GET /api/v1/employee/exam-attempts/{id}`：读取答卷、已保存答案和服务端计算的剩余时间；超时读取会自动交卷。
- `PUT /api/v1/employee/exam-attempts/{id}/answers`：幂等保存当前答案快照，适合浏览器定时或变更后自动保存。
- `POST /api/v1/employee/exam-attempts/{id}/submit`：按不可变试卷快照判分并提交；重复提交幂等，服务端强制执行考试时限。
- `GET/POST /api/v1/admin/plans`、`GET /api/v1/admin/plans/{id}`：管理员创建和查看培训计划草稿。
- `POST /api/v1/admin/plans/{id}/publish`：接收 `{ "employeeIds": [2, 3] }`，校验绑定对象和名单后发布；相同名单重试幂等。
- `GET /api/v1/employee/plans`、`GET /api/v1/employee/plans/{id}`：员工查看本人培训计划、课程和考试完成情况。
- `POST /api/v1/employee/plans/{id}/progress`：按课程完成和考试通过事实刷新计划进度。
- `GET /api/v1/employee/archive`：读取员工个人课程、考试、培训计划汇总及最近培训记录。
- `GET /api/v1/admin/analytics`：读取启用员工、课程完成率、考试通过率和培训计划完成率等汇总指标。
- `GET /api/v1/employee/certificates`、`GET /api/v1/admin/certificates`：分别读取本人或管理员可见的结业证书；课程完成或考试提交时自动更新计划并签发唯一证书，手动刷新仅用于核对。
- Flyway V1 基础表：部门、用户、刷新会话、课程、课程章节；V3 增加课程资源、报名和学习进度表，V4/V5 同步章节计数和本地示例文案，V6/V7 增加文件元数据并规范媒体类型，V8 增加题目、试卷快照、考试分配、答卷和答案表，V9 增加培训计划和员工任务表，本地 MySQL 会自动迁移。

证书打印模板、更细粒度的统计图表和更完整的阅卷/防作弊能力仍待后续切片；资源生产环境应将 `zhixing.storage.root` 替换为对象存储适配层，并接入病毒扫描、转码和临时签名地址。

## 本地启动

要求 Java 17+、Maven 3.9+、MySQL 8.4+。Windows PowerShell：

```powershell
$env:JAVA_HOME = 'C:\D\tools\jdk17'
$env:SPRING_PROFILES_ACTIVE = 'local'
$env:TRAINING_DB_USER = 'root'
$env:TRAINING_DB_PASSWORD = '<只在本机设置>'
mvn spring-boot:run
```

默认监听 `127.0.0.1:8081`，连接隔离库 `zhixing_training`。首次本地启动会创建两个仅限本地的演示账号：

本地上传文件默认落在后端工作目录下的 `.data/uploads`，该目录已加入忽略规则；可用 `TRAINING_STORAGE_ROOT` 指向其他隔离目录。单文件默认上限为 200 MB，可用 `TRAINING_UPLOAD_MAX_FILE_SIZE` 和 `TRAINING_UPLOAD_MAX_REQUEST_SIZE` 调整网关与 Spring Multipart 限制。

| 工号 | 密码 | 角色 |
| --- | --- | --- |
| `E20260001` | `Zhixing@123` | 员工 |
| `ADMIN001` | `Zhixing@123` | 管理员 |

共享环境禁止沿用这些密码；它们只由 `local` / `test` profile 的 BCrypt Seeder 创建。不要把数据库密码写入 `application*.yml`、`.env` 或 Git。

## MySQL profile

不要猜测或覆盖现有数据库。请先准备隔离库和专用账号，然后在本机环境变量中设置：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'mysql'
$env:TRAINING_DB_URL = 'jdbc:mysql://127.0.0.1:3306/zhixing_training?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true'
$env:TRAINING_DB_USER = 'training_app'
$env:TRAINING_DB_PASSWORD = '<只在本机设置>'
$env:TRAINING_JWT_SECRET = '<至少32字节随机Base64>'
mvn spring-boot:run
```

`mysql` profile 不植入演示用户，也不允许缺少 JWT 密钥；Flyway 只执行迁移，不执行 destructive clean。开发库和测试库分别为 `zhixing_training`、`zhixing_training_test`，不读取或改动其他已有业务库。

## 响应与安全约定

成功响应统一为 `{ code: "OK", message, data, traceId, timestamp }`。错误不会把 SQL、JWT 或堆栈回传客户端；可用 `traceId` 关联服务日志。账号失败会使用通用错误文案，密码采用 BCrypt，密码 UTF-8 长度限制为 72 字节，登录 IP 还有单实例有界速率限制。

访问令牌默认 15 分钟，刷新会话默认 8 小时并落库哈希。服务端每次鉴权会检查用户状态、角色和会话是否仍有效，因此禁用用户或退出会立即阻断旧访问令牌。单实例限流和数据库会话表是当前阶段方案，水平扩展前需替换为共享限流/会话基础设施（Redis 等）。

## 验证

```powershell
$env:JAVA_HOME = 'C:\D\tools\jdk17'
$env:TRAINING_DB_USER = 'root'
$env:TRAINING_DB_PASSWORD = '<只在本机设置>'
mvn test
mvn -DskipTests package
```

测试 profile 使用隔离 MySQL 测试库、Flyway 和本地 fixture，确认上下文、迁移、用户和课程基础数据可启动。浏览器联调入口为 `http://127.0.0.1:5173/connected.html`。


## 组织与权限增量（2026-10-08）

Flyway V11 增加部门层级、账号范围与管理操作审计。现有 ADMIN 升级时保留全局权限；local/test 新库 ADMIN001 为超级管理员。普通管理员只能维护授权范围内的 EMPLOYEE，超级管理员可维护部门、管理员和范围。

- `GET /api/v1/admin/access`：当前超级管理员状态和数据范围。
- `GET /api/v1/admin/departments/tree`：授权范围内的部门平铺节点（parentId 表示层级）。
- `POST /api/v1/admin/departments`、`PUT /api/v1/admin/departments/{id}`：超级管理员维护部门，更新携带 version。
- `GET /api/v1/admin/employees`：pageNo/pageSize（最大 100）、keyword、departmentId、status、role。
- `GET /api/v1/admin/employees/{id}`、`POST /api/v1/admin/employees`、`PUT /api/v1/admin/employees/{id}`：范围内账号查询/新增/编辑。新增需要 initialPassword，更新不接受通过此字段修改密码。
- `POST /api/v1/admin/employees/{id}/unlock`：携带 version，解除失败登录锁定。
- `GET /api/v1/admin/audit-logs`：超级管理员分页查询，可按 outcome=SUCCESS/DENIED/FAILED 筛选。

权限调整、人员移动和计划/考试分配共用数据库互斥行，以避免分配时目标被并发移出范围。部署多实例仍通过数据库协调；大名单分配及高并发吞吐尚未压测。

人员范围覆盖管理名单、发布分配、证书及人员统计。课程、题库、已发布考试为共享内容，受限管理员的计划和待发布试卷按创建人管理。审计记录 HTTP 管理写请求结果，不存请求正文或凭据；当前不含字段级变更快照/独立审计存储。

完整回归使用根目录的 `backend/scripts/isolated-mysql.ps1`。运行测试和 `spring-boot:run` 应串行启动编译，避免并发写同一 target 目录。

## 员工导入与账号设置（2026-10-08）

Flyway V12 增加导入预览、批次成员和一次性设置密码令牌。管理入口为“组织与员工 → 批量导入”；员工使用 `/#/account-setup` 设置密码。

| 接口 | 行为 |
| --- | --- |
| `GET /api/v1/admin/employee-imports/template` | 下载文本格式的 XLSX 模板，包含当前授权范围内的有效部门参考 |
| `POST /api/v1/admin/employee-imports` | multipart 字段 `file`，保存逐行校验结果，尚不创建账号 |
| `GET /api/v1/admin/employee-imports` | 本人最近 10 个未过期预览或已提交批次 |
| `GET /api/v1/admin/employee-imports/{id}` | 本人批次详情，pageNo/pageSize（最大 100） |
| `GET /api/v1/admin/employee-imports/{id}/report` | 下载逐行校验报告，可修正后重新上传 |
| `POST /api/v1/admin/employee-imports/{id}/commit` | 重新校验后整批创建；同一批次重复请求幂等 |
| `POST /api/v1/admin/employee-imports/{id}/activation-links` | 为仍未激活的有效账号重新生成设置码并下载名单 |
| `POST /api/v1/admin/employees/{id}/setup-link` | 携带 version，生成激活或重置链接所需的设置码 |
| `POST /api/v1/auth/account-setup` | 无需登录，需要 CSRF；接收 employeeNo/token/newPassword |

仅支持 .xlsx、最多 5,000 行、原文件 5 MB、累计解压内容 20 MB。要求文本单元格，不执行公式；重复工号、无效字段、已停用或越权部门均逐行提示。使用 Apache POI 5.5.1（[官方发布说明](https://poi.apache.org/download.cgi)）。

预览 30 分钟有效，任何错误均不写入员工。提交时出现新的重复或权限变化，返回 HTTP 200 和 `status=REVIEW_REQUIRED`，前端须读取状态而非只判断 HTTP 成功。导入结果只有创建该批次的管理员可读；已提交批次还会检查所有成员是否仍在其授权范围。

导入账号默认为 EMPLOYEE / SELF / 未激活，无可用默认密码。设置码仅存 SHA-256 哈希，有效期 24 小时、单次使用；重新生成使旧码失效。重置立即撤销旧会话和旧密码，员工完成设置后才能重新登录。账号版本、状态或签发人的当前权限不满足条件时，旧链接也会被拒绝。最后一个可用超级管理员不能通过重置流程停用自己的登录能力。

生产部署应设置 `TRAINING_PUBLIC_WEB_URL=https://training.example.com`，用于激活名单内的完整地址。不设置时取受信 CORS Origin 或第一个配置的 CORS 地址。地址中的设置码放在 `#` 后的片段中，员工页面读取后立即清除地址栏中的设置码；下载响应使用 no-store。所有写接口仍需 CSRF，审计不保存密码和设置码正文。

本轮不发送邮件或短信，管理员须逐人交付链接。批次原始字段与已消耗令牌暂无自动清理策略；上线前需结合人员信息保留要求配置归档/清理和导入频率限制。已有单个账号的 initialPassword 创建流程继续保留，不代表旧账号已统一强制首次改密。

## 正式证书（2026-11-02）

V15 接入中文 PDF、可点击验真二维码、公开验真、按权限撤销与审计，并固化签发快照。配置、接口与历史升级说明见 [证书交付与运维](docs/certificates.md)。生产签发前配置 `TRAINING_PUBLIC_WEB_URL`；PDF 日期时区通过 `TRAINING_CERTIFICATE_TIME_ZONE` 设置。
