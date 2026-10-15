# 追风者 · 公网部署指南

> 目标：`https://你的域名/` 访问这套平台，数据仍在你自己电脑的 MySQL 里，
> 全程不把 3306 暴露到公网。
>
> 本文档是**操作手册**，按顺序执行即可。全程约 40–60 分钟，其中等证书签发最久。

---

## 0. 先看这一段：三个必须先想清楚的决定

### 0.1 为什么"数据库留在本机"值得多绕一道隧道

你选的方案是"服务器上的应用远程连我这台开发机的 MySQL"。技术上可行，但直接做法
（把开发机 3306 加进防火墙白名单、只放行服务器 IP）有三个问题：

1. **扫描器不是等几个月才来的**。Shodan / Censys 这类全网测绘在扫到你的新开端口后，
   通常几小时内就会出现在数据库里。你的 MySQL 是 `root` 账号 + MySQL 8 默认的
   `caching_sha2_password` 插件，虽然连上后还要口令，但暴力破解是常态化的。
2. **你的家宽大概率没有公网 IP**。国内家庭宽带（含移动/联通/电信）普遍是
   **CGNAT 大内网**，路由器 WAN 口拿到的可能是 100.64.x.x 这种运营商内网地址，
   入站连接根本打不进来。所以"开放 3306"这条路很可能**根本走不通**，
   你会卡在"配置都对但连不上"。
3. **断电/断网 = 全站不可用**。公网演示期间你家网络一抖，页面就 500。

**SSH 反向隧道把这三个问题全绕开了**：

```
浏览器 --HTTPS--> Nginx(:443) --> Tomcat(:8080, 仅本机) --> SSH 隧道 --> 你电脑:3306
                                                            加密、单端口、仅服务器本机可见
```

服务器上出现的是一个 `127.0.0.1:13306` 的监听（`bind-address` 写死 127.0.0.1，
连服务器自己的公网 IP 都连不上）。你的 3306 全程绑定在 `127.0.0.1`，
**对公网零暴露**。隧道断开会话立刻失效，行为明确，不会静默连错库。

> 如果你的机器确实有公网 IP 且不想用隧道，第 6 节给了直连方案，
> 但请务必照那一节的账号与防火墙清单逐条做，不要图省事跳过。

### 0.2 域名与证书

- 域名必须已解析（A 记录或 AAAA 记录）到服务器公网 IP，**解析生效后再申请证书**。
  Let's Encrypt 校验失败是这一环节最常见的失败原因。
- 用 **certbot** 申请，自动续期。90 天有效期，但 certbot 会装 systemd timer 自动续。

### 0.3 本项目在公网部署下的 5 个特有注意点

| 问题 | 原因 | 本文档对应章节 |
|---|---|---|
| 登录后刷新又变未登录 | Tomcat 发的会话 Cookie 的 Path 来自 contextPath，公网 URL 少了前缀就对不上 | §3 Nginx 保留 `/typhoon_platform` 前缀 |
| 部分接口被浏览器拦 | `CorsFilter` 白名单只有 localhost | §3 同源部署，无需改代码 |
| 定时同步任务反复刷日志 | `sync.enabled=true`，30 分钟一次 | §6.4 公网建议先关 |
| 数据库口令随 WAR 外流 | `db.properties` 打在 WAR 里 | §5 用 `-Djdbc.*` 系统属性覆盖 |
| 问卷里的联系方式解不开显示为空 | `contact.cipher.key` 换了值 | §5 注入与本机一致的值 |

---

## 1. 服务器准备

### 1.1 规格

| 项 | 最低 | 建议 | 说明 |
|---|---|---|---|
| CPU | 1 核 | 2 核 | 11 个页面 + 同步任务，1 核够用 |
| 内存 | 2 GB | 4 GB | 给 Tomcat `-Xmx1024m` 留足余量 |
| 系统盘 | 20 GB | 40 GB | |
| 带宽 | 1 Mbps | 5 Mbps | 首屏约 350KB，1 Mbps 也能用，只是首屏慢 |

操作系统以下均以 **Ubuntu 22.04 LTS** 为例。CentOS/RHEL 把 `apt` 换成 `dnf`、
`systemctl` 用法一致。

### 1.2 安装基础软件

```bash
# 以 root 或 sudo 执行
apt update && apt install -y openjdk-17-jdk-headless maven nginx mysql-client git curl certbot python3-certbot-nginx

java -version    # 期望 17 或更高
mvn -version
nginx -v
```

> **JDK 版本说明**：本项目 `pom.xml` 里 `source/target = 1.8`，编译产物是 Java 8 字节码，
> 所以**用 JDK 17 运行 Tomcat 完全没问题**（向后兼容）。不必为了"贴合课程要求"
> 专门装 JDK 8。JDK 21 也可以。

### 1.3 Tomcat 9 —— 注意不是 Tomcat 10

```bash
cd /opt
# Tomcat 9 最后一个版本，javax.servlet 体系
wget https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.98/bin/apache-tomcat-9.0.98.tar.gz
tar xzf apache-tomcat-9.0.98.tar.gz
mv apache-tomcat-9.0.98 tomcat9
ln -sfn /opt/tomcat9 /opt/tomcat          # 软链，方便以后换版本
```

> ⚠️ **绝对不要用 Tomcat 10/11**。Tomcat 10 起把 `javax.servlet.*` 整体换成
> `jakarta.servlet.*`，而本项目（课程要求"禁用框架、Java 原生 Servlet"）
> 全部按 `javax` 编写。在 Tomcat 10 上部署的表现是**每一个页面都 404**，
> 因为 `@WebServlet` 注解根本不被识别。日志里也不会有明显报错，最容易误判。

创建专用系统用户（不要用 root 跑 Tomcat）：

```bash
useradd -r -m -d /opt/tomcat -s /bin/false tomcat
chown -R tomcat:tomcat /opt/tomcat

# 让启动脚本找得到
cat > /etc/profile.d/tomcat.sh <<'EOF'
export CATALINA_HOME=/opt/tomcat
export CATALINA_BASE=/opt/tomcat
export PATH=$CATALINA_HOME/bin:$PATH
EOF
```

### 1.4 systemd 单元

`/etc/systemd/system/tomcat.service`：

```ini
[Unit]
Description=Apache Tomcat 9 (追风者)
After=network.target

[Service]
Type=simple
User=tomcat
Group=tomcat

Environment="CATALINA_HOME=/opt/tomcat"
Environment="CATALINA_BASE=/opt/tomcat"
Environment="JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64"

# 启动前先装配置（口令在这里，不进 WAR）
EnvironmentFile=-/opt/tomcat/bin/setenv.sh

ExecStart=/opt/tomcat/bin/catalina.sh run
ExecStop=/opt/tomcat/bin/catalina.sh stop 30

Restart=on-failure
RestartSec=10

# 加固：Tomcat 不需要这些
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ProtectHome=true
ReadWritePaths=/opt/tomcat/logs /opt/tomcat/temp /opt/tomcat/work

StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now tomcat
systemctl status tomcat
curl -s http://127.0.0.1:8080/typhoon_platform/api/health | head -c 300
```

此时还访问不到接口（数据库还没连上），但**只要 Tomcat 起来了、`catalina.out` 里
没有 `SEVERE: Failed to start`，说明 Servlet 注册是成功的**——这一步就能提前
验证"没用 Tomcat 10"。

### 1.5 关掉 Tomcat 默认的 AJP 与示例应用

```bash
# 1) 注释 AJP Connector（若存在）
sed -i 's/^<!--\(<Connector port="8009".*\)-->$/\1/' /opt/tomcat/conf/server.xml
sed -i 's/^\( *\)<Connector port="8009"/\1<!--<Connector port="8009"/;s|^\(.*\)protocol="AJP/1.3"\(.*\)/>|\1protocol="AJP/1.3"\2/> -->|' /opt/tomcat/conf/server.xml

# 2) 删掉示例与默认站点（它们是常见的默认口令扫描目标）
rm -rf /opt/tomcat/webapps/{docs,examples,host-manager,manager}

# 3) 8005 关闭端口只监听本地
sed -i 's/address="0.0.0.0" port="8005"/address="127.0.0.1" port="8005"\nsecure="true"/' /opt/tomcat/conf/server.xml
```

### 1.6 让 Tomcat 只对本机监听（关键）

编辑 `/opt/tomcat/conf/server.xml`，把 8080 Connector 加上 `address`：

```xml
<Connector port="8080" protocol="HTTP/1.1"
           connectionTimeout="20000"
           address="127.0.0.1"
           maxThreads="200"
           URIEncoding="UTF-8"
           redirectPort="8443" />
```

加上 `address="127.0.0.1"` 后，**公网无法直连 8080**，只能经 Nginx 进入。
这样你就不需要在防火墙里为 8080 开任何规则（防火墙里也干脆不开 8080）。

---

## 2. 在你的电脑上打包

在你**本地**项目目录执行（不是服务器）：

```bash
cd /path/to/web课程项目/typhoon_platform

# 1) 交付级构建：跑全量测试 + 覆盖率闸门 + 打包
#    期望：Tests run: 944, Failures: 0, Errors: 0, Skipped: 1
#          BUILD SUCCESS + "All coverage checks have been met."
mvn clean verify
```

Windows 上如果你用的是 IDE 自带 Maven 或别的 JDK，记得 `JAVA_HOME` 指向可用 JDK：

```bash
export JAVA_HOME=/d/Java/jdk-21.0.5+11
/d/apache-maven-3.9.6/bin/mvn clean verify
```

> ⚠️ `mvn clean` 会删掉 `target/`。如果你之前在 `target/` 里放过什么手工准备的
> 文件（记忆里就有 `target/` 被 `mvn clean` 抹掉导致脚本失效的坑），先备份到
> `_backup/`。

产物：`target/typhoon_platform.war`（包名即 contextPath）。

### 2.1 传到服务器

```bash
# Windows Git Bash / macOS / Linux 通用
scp target/typhoon_platform.war root@<服务器IP>:/tmp/

# 服务器上
mv /tmp/typhoon_platform.war /opt/tomcat/webapps/
systemctl restart tomcat

# 等 20~30 秒解压，然后验证
curl -s http://127.0.0.1:8080/typhoon_platform/api/health
```

期望（此时 `database.ok` 仍为 false，因为还没配数据库）：

```json
{"code":200,"message":"success","data":{
  "app":"typhoon_platform","status":"UP",
  "database":{"ok":false,"message":"..."},"servletVersion":"4.0"},
 "success":true}
```

**只要这个 JSON 返回了，说明 WAR 部署成功、Servlet 注册成功。**

---

## 3. Nginx + HTTPS

### 3.1 申请证书

```bash
# 装 webroot 插件（用于续期时无需停 Nginx）
certbot certonly --webroot -w /var/www/certbot -d typhoon.example.com

# 自动续期（certbot 包已自带 systemd timer，确认一下）
systemctl list-timers | grep certbot
```

### 3.2 应用配置

```bash
cp deploy/nginx.conf.example /etc/nginx/conf.d/typhoon.conf
# 编辑：改 server_name、ssl_certificate 两个路径
vi /etc/nginx/conf.d/typhoon.conf

nginx -t && systemctl reload nginx
```

### 3.3 这一步最容易踩的坑：会话 Cookie 的 Path

**现象**：登录接口返回成功，页面显示已登录，但**刷新一下又变成未登录**，
或者换个页面就掉登录态。

**原因**：Tomcat 发出的 `JSESSIONID` Cookie 的 `Path` 取自应用上下文，
即 `/typhoon_platform`。而如果 Nginx 把公网的 `https://typhoon.example.com/`
映射到 WAR 根，浏览器请求的路径是 `/`、`/api/typhoons`……，
**和 Cookie 的 Path 完全不匹配，浏览器根本不会把 Cookie 回传**。

**本方案的做法**：公网 URL 保留前缀，即用户访问
`https://typhoon.example.com/typhoon_platform/`。
Nginx 里是：

```nginx
location /typhoon_platform/ {
    proxy_pass http://tomcat;
}
```

这样 URL 里带上下文，Cookie Path 与请求路径天然一致，**零额外配置**。

如果你确实想要干净域名（`https://typhoon.example.com/` 直接是首页），
必须**同时**改两处，只改一处就会掉进上面的坑：

```nginx
# ① 转发时补上下文
location / {
    proxy_pass http://tomcat/typhoon_platform/;
}
# ② 改写 Cookie 的 Path，让它从 /typhoon_platform 变成 /
proxy_cookie_path /typhoon_platform /;
```

> 前端 `js/api.js` 的 `detectContext()` 会自动按当前 URL 首段推导上下文
> （末尾段含 `.` 才当作文件名弹掉），所以这两种部署形态前端代码都不用改。

### 3.4 关于 CORS：同源部署不需要改代码

`CorsFilter` 里有一份只含 `localhost:8080` 之类的白名单。**公网部署下不需要动它**：

- Nginx 转发后，页面与接口的 Origin 完全相同（都是 `https://你的域名`），
  浏览器**不会发起 CORS 预检**，过滤器的白名单根本不参与判断。
- 全部是相对路径（`js/api.js`、`css/tokens.css`），代码里没有硬编码
  `http://localhost`，所以没有"混合内容"（HTTPS 页面加载 HTTP 资源被浏览器拦）问题。
- `vendor/` 下的 jQuery/Bootstrap/ECharts 都是本地文件，不是 CDN，也就不受
  外网 CDN 可达性影响。

### 3.5 防火墙

```bash
# 只放行 80/443。8080、3306、8005 全部不对外。
ufw allow 80/tcp && ufw allow 443/tcp
ufw enable && ufw status verbose

# 云服务器控制台的安全组也要同步只放 80/443
```

---

## 4. 数据库：数据仍在自己电脑（隧道方案）

### 4.1 建立隧道

在你**自己电脑**上：

```bash
bash deploy/tunnel.sh ubuntu@<服务器IP>
```

它会在服务器上创建一个**只对服务器本机可见**的 `127.0.0.1:13306`，
把流量加密转发回你电脑的 `3306`。

在另一个终端验证：

```bash
ssh ubuntu@<服务器IP> 'mysql -h127.0.0.1 -P13306 -uzfz_app -p -e "SELECT COUNT(*) FROM typhoon_monitor.typhoons;"'
```

### 4.2 本机 MySQL 侧设置（一次性）

**不要改 `bind-address`**，保持 `127.0.0.1` 即可 —— 隧道是从服务器发起连接的，
你的 3306 本来就只需对本机监听。

建一个受限账号：

```sql
-- 在你电脑上连到 MySQL 执行
CREATE USER 'zfz_app'@'localhost' IDENTIFIED BY '你的强口令';
GRANT SELECT, INSERT, UPDATE, DELETE ON typhoon_monitor.* TO 'zfz_app'@'localhost';
FLUSH PRIVILEGES;
```

> ⚠️ 别用 root。`db/typhoon_monitor.sql` 里预置的演示账号
> （`admin01/password`、`dataadmin01/123456`）口令是公开的，
> 公网部署前请全部改掉（§6.3）。

### 4.3 让隧道常驻

```bash
# macOS
brew install autossh
autossh -M 0 -N -o "ServerAliveInterval=30" -o "ServerAliveCountMax=3" \
        -R 127.0.0.1:13306:127.0.0.1:3306 ubuntu@<服务器IP> &
```

Windows 上建议用 nssm 或"任务计划程序"注册为开机自启服务。
更稳的做法是让一台 VPS 用 `autossh` 反向连你的机器，避免你关机后演示中断。

### 4.4 隧道断开会怎样

服务器上的应用会立刻连不上库（接口报错、登录态丢失）。
这是**期望行为**：明确的失败好过静默连错库。演示前先确认隧道还活着：

```bash
ssh ubuntu@<服务器IP> 'ss -tlnp | grep 13306'
```

---

## 5. 让 Tomcat 用上正确的配置（口令不进 WAR）

### 5.1 为什么用系统属性而不是改 db.properties

`DBUtil.getConfig()` 的优先级是**系统属性 > `WEB-INF/classes/db.properties`**：

```java
String sys = System.getProperty(key);   // -Djdbc.url=... 优先
if (sys != null && !sys.trim().isEmpty()) { return sys.trim(); }
return PROPS.getProperty(key);
```

所以用启动参数注入有两个好处：
1. **不用重新 `mvn package`**，改口令只改一个文本文件 + `systemctl restart tomcat`；
2. **WAR 文件本身不含明文口令**，可以安全地在多处拷贝。

### 5.2 装配置

```bash
cp deploy/setenv.sh.example /opt/tomcat/bin/setenv.sh
vi /opt/tomcat/bin/setenv.sh     # 填 jdbc.url / username / password / cipher.key
chown tomcat:tomcat /opt/tomcat/bin/setenv.sh
chmod 600 /opt/tomcat/bin/setenv.sh    # 含口令，限制读权限
systemctl restart tomcat
```

### 5.3 三个必须填对的值

| 键 | 填什么 | 填错的后果 |
|---|---|---|
| `jdbc.url` | 隧道方案填 `jdbc:mysql://127.0.0.1:13306/typhoon_monitor?...` | 库名写错 → 所有查询报 `Unknown database` |
| `jdbc.password` | `zfz_app` 的口令 | `Access denied` |
| `contact.cipher.key` | **必须与你本机 `db.properties` 里的完全一致** | 问卷/反馈里已加密的手机号、邮箱**解不开，列表静默显示为空**（不报错，最难查） |

`jdbc.url` 的参数串照抄本机现有的，只把主机部分换掉：

```
jdbc:mysql://127.0.0.1:13306/typhoon_monitor?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&cachePrepStmts=true&prepStmtCacheSize=500&prepStmtCacheSqlLimit=2048
```

其中 `useSSL=false` 在走 SSH 隧道时是对的（流量已在 SSH 层加密）。
`allowPublicKeyRetrieval=true` 是 MySQL 8 默认加密插件所必需，不要删。

### 5.4 验证

```bash
curl -s http://127.0.0.1:8080/typhoon_platform/api/health
```

期望 `"database":{"ok":true,"message":"连接正常"}`。
`message` 会写明具体原因（读配置失败 / 驱动缺失 / 连接被拒 / 库不存在），
照提示排查即可。

> ⚠️ `/api/health` 是公开接口（`AuthFilter` 不拦它），会返回数据库 URL、
> JDK 版本、操作系统。`safeUrl()` 已经把 `password=xxx` 打了码，
> 但主机名和库名是可见的。**公网部署建议**：在 Nginx 里把它也限制一下，
> 或者改成只在服务器本机 `curl` 自检、不对外暴露。

---

## 6. 公网上线前的检查清单

### 6.1 定时同步

`web.xml` 里默认：

| 参数 | 默认 | 公网建议 |
|---|---|---|
| `sync.enabled` | `true` | **改成 `false`**（除非你确认服务器能稳定访问 `typhoon.nmc.cn`） |
| `sync.intervalMinutes` | `30` | 保持 |
| `sync.initialDelaySeconds` | `20` | 保持 |

改法二选一：编辑 WAR 解压后的 `WEB-INF/web.xml`（重启生效，不必重新编译），
或在 `sync.enabled=false` 时确认页面上"30 分钟自动同步"的文案也一并调了
——`overview.js` 里的文案是写死的，两处不一致会被看出来。

关闭同步不影响任何功能：数据库里已有全量历史数据，页面照常展示；
只是不会有新的活跃台风自动入库。要恢复随时把参数改回 `true`。

服务器若要开着同步，需要能出网访问 `typhoon.nmc.cn`。WAR 内已带
`WEB-INF/classes/certs/cfca-nmc-roots.pem`（CFCA 根证书，不在 JDK cacerts 里），
代码走"默认 ∪ 附加"并集 TrustManager，**不是关闭校验**，无需额外配置。

### 6.2 端口暴露面自查

```bash
# 服务器上执行，输出应只有 sshd / nginx
ss -tlnp | grep -v 127.0.0.1
```

| 端口 | 应否对外 |
|---|---|
| 22 | 对外（或限 IP） |
| 80 / 443 | 对外 |
| 8080 | ❌ 绑 127.0.0.1 |
| 3306 | ❌ 不启动（我们用隧道） |
| 8005 / 8009 | ❌ 本地 / 已注释 |

### 6.3 改掉所有演示口令

`db/typhoon_monitor.sql` 里预置了 10 个用户，其中两个是 **ADMIN**：

| 账号 | 口令 | 处理 |
|---|---|---|
| `admin01` | `password` | **必须改**（或停用） |
| `dataadmin01` | `123456` | **必须改**（或停用） |
| `user01`…`user08` | 弱口令 | 公网演示建议一并改或停用 |

密码是 SHA-256 存储（`sha256$<hex>`），应用里没有"改密后强制登出"机制，
改密后旧会话仍然有效。若要彻底失效，重启 Tomcat。

另外注意：登录失败 5 次会锁 15 分钟，落 `login_attempts` 表。
**公网开放注册 = 有人可以故意锁死管理员账号**。建议在
`AuthServlet` 里对注册接口加限流，或直接关闭注册
（让管理员在后台建号）。

### 6.4 备份

```bash
# 服务器端（每天 3:17 导出，保留 14 天）
cat >/usr/local/bin/backup-zfz.sh <<'EOF'
#!/bin/bash
# 走隧道导出，dump 的是你电脑上的真库
mysqldump -h127.0.0.1 -P13306 -uzfz_app -p"$ZFZ_DB_PASS" \
  --single-transaction --routines --triggers \
  typhoon_monitor | gzip > /var/backups/typhoon_monitor_$(date +\%F).sql.gz
find /var/backups -name 'typhoon_monitor_*.sql.gz' -mtime +14 -delete
EOF
chmod +x /usr/local/bin/backup-zfz.sh
echo '17 3 * * * root /usr/local/bin/backup-zfz.sh' > /etc/cron.d/zfz-backup
```

`--single-transaction` 对 InnoDB 表能拿到一致性快照而不锁表。

### 6.5 端到端验证清单

部署完成后逐项确认：

```bash
# 1) 健康检查
curl -s https://typhoon.example.com/typhoon_platform/api/health | python3 -m json.tool

# 2) 首页可访问（HTTPS 有效证书，不是"不安全"）
curl -sI https://typhoon.example.com/typhoon_platform/ | head -1

# 3) HTTP 应 301 到 HTTPS
curl -sI http://typhoon.example.com/typhoon_platform/ | head -1

# 4) 静态资源有缓存头
curl -sI https://typhoon.example.com/typhoon_platform/vendor/jquery.min.js | grep -i cache-control

# 5) 接口 200 且数据真实
curl -s "https://typhoon.example.com/typhoon_platform/api/typhoons" | head -c 200
```

浏览器里逐个点开 11 个页面，确认无控制台报错、无 404：

```
index.html               监测总览（地图 + 台风列表 + 风圈）
typhoon-detail.html      台风档案详情
archive.html             历史档案（三个筛选下拉）
risk-assessment.html     风险评估（三个下拉 + 时次重演 + 存档）
forecast-disagreement.html 机构路径分歧
similar-cases.html       相似案例
sandbox.html             灾害沙盘
survey.html              问卷
login.html               登录 / 注册
admin.html               管理后台（需 ADMIN）
help-api.html            接口说明（需 ADMIN）
```

重点验证**登录**：注册一个账号 → 登录 → 刷新页面 → 确认仍是登录态
（这一条专门用来抓 §3.3 的 Cookie Path 问题）。

### 6.6 已有回归脚本（本地跑，不是服务器跑）

仓库 `tools/` 下有全套回归脚本，在**本地**起好 Tomcat 后执行：

| 脚本 | 断言数 | 覆盖 |
|---|---|---|
| `tools/verify_api.py` | 123 | 后端接口契约 |
| `tools/zfz-mockfree-verify.cjs` | 140 | 无 mock / 无演示数据 |
| `tools/probe-map-paths.cjs` | — | 地图数据是否全部入画 |
| `tools/probe-overview-focus.cjs` | 37 | 切台风 + 缩放 |
| `tools/probe-archive-filters.cjs` | 8 | 档案页筛选 |
| `tools/probe-risk-filters.cjs` | 14 | 风险评估三下拉 + 重演 |
| `tools/probe-runtime-errors.cjs` | — | 全页控制台巡检 |

> 这些脚本会**真实提交问卷**且数据不可删（每跑一次 +1 行），
> 所以别对生产库跑。它们连的是 localhost，跑之前确认你本地 Tomcat 是干净的。

---

## 7. 日常运维

### 更新代码

```bash
# 本地
mvn clean verify
scp target/typhoon_platform.war root@<服务器IP>:/opt/tomcat/webapps/

# 服务器（先删旧解压目录，否则残留的旧 class 可能被优先加载）
systemctl stop tomcat
rm -rf /opt/tomcat/webapps/typhoon_platform /opt/tomcat/webapps/typhoon_platform.war
mv /opt/tomcat/webapps/../typhoon_platform.war /opt/tomcat/webapps/ 2>/dev/null
# 更稳妥的做法：scp 到 /tmp 再移动，避免"删了还没传完"的空窗
systemctl start tomcat
```

> ⚠️ **换包前一定要先 `rm -rf` 旧的解压目录**。
> Tomcat 只在目录不存在时才重新解压 WAR；若目录在，它会**继续用旧目录**，
> 新 WAR 里的 class 不会生效。你会看到"cp 成功了但线上没变"的现象。

### 看日志

```bash
journalctl -u tomcat -f              # systemd 标准输出
tail -f /opt/tomcat/logs/catalina.out
tail -f /opt/tomcat/logs/localhost.$(date +%F).log   # 应用级错误
```

### 证书续期

```bash
certbot renew --dry-run              # 先演练
systemctl list-timers | grep certbot # 确认自动续期已启用
```

### 回滚

保留上一个 WAR：`typhoon_platform.war.bak`，
出问题时停 Tomcat → 换回 `.bak` → 解压目录删掉 → 启动。

---

## 8. 故障速查

| 现象 | 原因 | 处理 |
|---|---|---|
| **所有页面 404** | 用了 Tomcat 10/11（jakarta） | 换 Tomcat 9。日志无明显报错，极易误判 |
| 登录成功、刷新就掉 | Cookie Path 与 URL 不匹配 | 见 §3.3，两处配置要一起改 |
| 首页能开、接口全 404 | contextPath 不对，Nginx 转发路径写错 | `curl -sI http://127.0.0.1:8080/typhoon_platform/api/health` 先确认应用本身正常 |
| `"database":{"ok":false}` | 口令/库名/隧道 | `message` 里有具体原因；隧道用 `ss -tlnp \| grep 13306` 验证 |
| `Unknown database 'xxx'` | 库名与实际不一致 | 库名只允许在 `jdbc.url` 与 `db/typhoon_monitor.sql` 两处出现且必须逐字一致 |
| `Access denied` | 账号不对或未授权该库 | `GRANT SELECT,INSERT,UPDATE,DELETE ON typhoon_monitor.* TO ...` |
| 问卷里的联系方式全空 | `contact.cipher.key` 变了 | 必须与本机一致，见 §5.3 |
| 页面样式全乱 | 静态资源被强缓存 / 路径 404 | `CacheFilter` 对本项目自身资源下发 `no-cache`，若仍异常先 Ctrl+F5 强刷 |
| 证书 `NET::ERR_CERT_AUTHORITY_INVALID` | DNS 未生效就申请 | 等解析生效，用 `dig +short 你的域名` 确认返回服务器 IP |
| 502 Bad Gateway | Tomcat 没起 / setenv.sh 语法错 | `systemctl status tomcat`、`nginx -t` |
| 同步任务刷屏或失败 | 服务器访问不了 `typhoon.nmc.cn` | `sync.enabled=false`（§6.1） |
| 管理员账号被锁 15 分钟 | 有人暴力尝试 | 正常保护机制。`DELETE FROM login_attempts WHERE username='admin01';` 解锁 |

---

## 附：部署文件清单

| 文件 | 位置 | 作用 |
|---|---|---|
| `deploy/nginx.conf.example` | → `/etc/nginx/conf.d/typhoon.conf` | HTTPS + 反代 + Cookie 加固 |
| `deploy/setenv.sh.example` | → `/opt/tomcat/bin/setenv.sh` | 启动参数（口令不进 WAR） |
| `deploy/tunnel.sh` | 在你自己电脑上执行 | SSH 反向隧道，替代暴露 3306 |
| `db/typhoon_monitor.sql` | 备用 | 完整建库脚本（**含 `DROP TABLE`，勿在已有数据的库上重跑**） |
| `db/migration_*.sql` | 需要时 | 幂等增量：`risk_obs_time` / `login_attempts` / `contact_encryption` |
| `tools/verify_api.py` | 本地 | 接口回归 123 项 |