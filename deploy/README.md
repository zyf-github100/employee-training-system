# 生产部署（Docker Compose）

面向单机部署：MySQL 8.4、Spring Boot API、Nginx 静态站点 + `/api` 反向代理。
API 与 MySQL 不映射宿主机端口，只有 Nginx 对外监听（默认 80）。

## 目录布局

```
/opt/zhixing/
├─ docker-compose.yml
├─ .env                  # 密钥，chmod 600
├─ app/training-api.jar  # backend 构建产物
├─ web/                  # frontend/dist 内容
├─ nginx/default.conf
└─ data/
   ├─ mysql/             # 数据库文件
   └─ uploads/           # 课程资源
```

## 构建与上传

```powershell
# backend
mvn -DskipTests package
# frontend
cd frontend; pnpm build
```

把 `backend/target/training-api-0.1.0-SNAPSHOT.jar` 上传为 `app/training-api.jar`，
把 `frontend/dist/` 的全部内容上传到 `web/`。

## 启动

```bash
cd /opt/zhixing
cp .env.example .env && chmod 600 .env   # 填入真实密钥
docker compose up -d
docker compose ps
curl -fsS http://127.0.0.1/api/v1/auth/csrf
```

首次启动时 Flyway 自动建表；`mysql` profile 不创建任何演示账号，需要手工创建第一个超级管理员。

### 80/443 被云厂商拦截时

国内主机未完成 ICP 备案时，云厂商通常会在边缘拦截 80/443，即使安全组已放行。
编排额外把容器 80 映射到 `ALT_HTTP_PORT`（默认 8091），可直接通过
`http://服务器IP:8091` 访问；备案和证书就绪后再切回 80/443。

```bash
# 确认云安全组与主机防火墙都放行该端口
ufw allow 8091/tcp
curl -I http://服务器IP:8091/
```

## 创建第一个管理员

在部署机生成 BCrypt 哈希（cost 12），然后写入数据库：

```bash
cd /opt/zhixing
set -a; . ./.env; set +a
HASH=$(python3 -c "import bcrypt;print(bcrypt.hashpw(b'替换成强密码', bcrypt.gensalt(12)).decode())")
docker compose exec -T mysql mysql --default-character-set=utf8mb4 \
  -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" <<SQL
INSERT INTO org_department(name, code, status) VALUES ('总部', 'HQ', 'ACTIVE')
  ON DUPLICATE KEY UPDATE name = VALUES(name);
INSERT INTO sys_user(employee_no, password_hash, name, department_id, role_code, super_admin, data_scope)
SELECT 'ADMIN001', '$HASH', '系统管理员', id, 'ADMIN', TRUE, 'ALL' FROM org_department WHERE code = 'HQ';
SQL
```

管理员登录后应立即在「账号与资料」修改密码。`--default-character-set=utf8mb4`
不能省略，否则中文姓名会按 latin1 写入数据库。

## HTTPS

当前配置按 HTTP 部署，`COOKIE_SECURE=false`。正式使用前必须配置域名和证书：

1. 域名解析到服务器，Nginx 增加 443 server 块（或在外层负载均衡终止 TLS）。
2. 将 `PUBLIC_ORIGIN` 改为 `https://你的域名`，`COOKIE_SECURE=true`。
3. `docker compose up -d` 重建 api 与 web。

纯 IP 无法申请受信任证书；HTTP 下密码和令牌是明文传输的，只适合临时验收。

## 备份与升级

- 数据库：`docker compose exec -T mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction zhixing_training | gzip > backup-$(date +%F).sql.gz`
- 上传文件：备份 `data/uploads/`。
- 升级：替换 jar / `web/` 后 `docker compose up -d api web`；Flyway 只前进，回滚需用备份恢复。

## 反向代理注意

`application.yml` 已启用 `server.forward-headers-strategy=native`，Nginx 透传的
`X-Forwarded-For` 会用于登录限流和审计。若在 Nginx 前再加一层代理，需确保只信任
该代理传来的转发头。
