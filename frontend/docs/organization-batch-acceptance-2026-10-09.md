# 组织批量变更与部门负责人验收

验收日期：2026-10-09（Pacific/Kiritimati）；截图文件时间使用 UTC。环境为 Windows、Java 17、MySQL 8.4 独立临时实例、真实 Spring API 和 Chromium。使用虚构员工 `BATCH_UI01`–`BATCH_UI25`，未连接生产库。

## 已实现行为

- 管理入口 `/connected.html` → 组织与员工 → 员工账号：20 人分页，选择跨页保留，支持移除名单成员或清空选择，最多 200 人。
- 批量调岗：选择启用目标部门，默认保留原岗位，也可统一修改或清空岗位。普通管理员只能调整范围内员工，超级管理员可调整普通管理员。
- 批量角色：仅超级管理员可操作；可配置员工/管理员和五种数据范围。指定部门不能为空，降为员工会清除旧部门授权。批量操作不包含本人、超级管理员账号，也不授予超级管理员权限。
- 完整校验、版本冲突和权限检查均在写入前完成；单事务保存，失败整批回滚。请求编号、操作人及规范化配置共同保证重试幂等，真正发生变更才增加账号版本和撤销会话。
- 部门树提供负责人检索、分页、设置与清空。候选人必须是本部门启用账号，允许尚未激活的账号；任命不自动授权。负责人被调离或停用时解除任命并增加部门版本。
- 保存批量操作期间禁止修改名单、切换组织页签；成功后等待列表刷新，避免从旧列表继续选择。修复全局输入框样式造成复选框、单选框宽度异常。

数据库迁移为 V13，API 请求示例及具体约束见 `../../backend/README.md`。

## 自动化验证

项目根目录执行：

```powershell
pwsh -NoProfile -File backend/scripts/isolated-mysql.ps1
```

最终结果 58 项通过，失败、错误和跳过均为 0：

| 测试类 | 数量 |
| --- | ---: |
| TrainingApplicationTests | 1 |
| TrainingWorkflowTests | 13 |
| OrganizationAccessTests | 12 |
| EmployeeWorkbookTests | 4 |
| EmployeeImportTests | 15 |
| OrganizationBatchTests | 13 |

新增测试覆盖：来源/目标越权、自己和超级管理员保护、重复目标、旧版本整批回滚、重复与并发请求幂等、角色提升/降级与自定义范围、停用部门、真实 JWT 和刷新会话撤销、激活码版本失效、负责人资格/版本/权限保持、调岗与停用自动解除任命、并发任命与调岗、200 人成功与超过上限拒绝、HTTP 角色保护及成功审计。无变化调岗保留版本；保留岗位、修改岗位、清空岗位均有覆盖。

前端目录执行：

```powershell
npm run test:training
npm run test:session
npm run build
```

结果：23 项演示业务检查、4 项真实入口会话客户端回归通过；Vue/TypeScript 检查及 Vite 生产构建通过。会话测试通过模拟 HTTP 响应检查 CSRF 更新、JSON 与文件并发共用一次刷新、撤销会话清空身份及网络失败可重试，不代表替代真实后端鉴权测试。构建仍有原图表分包超过 500 kB 的提示。

## 浏览器实际操作

1. 通过 Excel 页面预览、确认导入 25 个技术部门员工；创建“批量验收运营部”。
2. 在技术部门设置负责人，搜索并选择 `BATCH_UI25`，保存成功，账号角色保持员工。
3. 员工第一页全选 20 人，第二页再选 `BATCH_UI01`；页面保留 21 人，确认目标运营部门和统一岗位“运营培训岗”。
4. 提交后显示“选择 21 人，实际修改 21 人，解除负责人任命 1 个”；按运营部门筛选共 21 人，岗位已更新；技术部门负责人恢复未设置。
5. 再次跨页选择这 21 人，切为批量分配角色，管理员 → 指定部门。未选择授权部门时显示“请至少选择一个授权部门”，选择运营部门后提交成功，显示实际修改 21 人。列表显示“管理员 · 自定义部门”。
6. 在运营部门重新设置 `BATCH_UI25` 为负责人，再清空并保存，部门显示未设置。手机视口也完成保存操作。
7. 审计页显示调岗、角色分配及三次负责人操作的 200 成功记录。
8. 检查 1440×1000 桌面及 390×844 手机视口；批量表单和负责人表单 document.scrollWidth=390，无页面横向溢出。

浏览器停留超过 15 分钟时，发现缓存 CSRF 值已与 Cookie 状态不同，访问令牌到期后 `/auth/refresh` 返回 403。已在刷新前重新请求 CSRF，并保持现有并发刷新合并及匿名刷新规则。修复后浏览器复验使用一次 401 响应模拟访问令牌到期，其余请求走真实 API，观察到：

```text
GET audit-logs    401（测试注入一次）
GET auth/csrf     200
POST auth/refresh 200（真实 HttpOnly Cookie 刷新）
GET auth/csrf     200
GET audit-logs    200（真实重试，审计列表正常）
```

## 本地证据

- 后端日志：`backend/.data/qa-org-batch-tests.log`；各测试结果：`backend/target/surefire-reports/`。
- 前端：`output/organization-batch-build.log`、`output/organization-batch-demo-tests.log`、`output/organization-session-tests.log`。
- 截图位于 `frontend/output/playwright/`：`organization-batch-selection-desktop.png`、`organization-batch-selection-mobile.png`、`organization-batch-roles-desktop.png`、`organization-batch-roles-mobile.png`、`organization-batch-success.png`、`organization-batch-roles-success.png`、`organization-leader.png`、`organization-leader-mobile.png`、`organization-batch-audit.png`。
- selection 截图是修复样式后重新选择 21 人的表单展示，未再次执行调回技术部门的配置；首次调岗结果见 success 截图和审计记录。日志、截图和虚构工作簿均为本地忽略文件。

## 限制与后续

- 批量为同步 200 人上限，200 人测试仅证明该批次功能正确；尚未进行生产容量或多批并发压力验收。
- 操作事务失败时整批回滚；已提交操作没有一键撤销。前端在同一表单中保留请求编号用于未知结果重试，刷新整页后不恢复未提交选择。
- 负责人只表达组织关系；如需管理权限仍要配置账号角色和数据范围。密码重置仅影响登录，不解除负责人。
- 账号变更会使旧激活链接失效，需在新组织/权限配置后重新生成。导入历史、批量请求历史的保留期限及批量变更历史页面尚未实现。
- 批量表保存请求配置和目标版本用于幂等，管理审计仍为请求结果日志，没有完整字段级前后快照或独立防篡改存储。
- 邮件/短信邀请、旧账号强制首次改密、正式证书 PDF、生产部署和多浏览器验收仍按根目录交接清单推进。
