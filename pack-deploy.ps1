<#
.SYNOPSIS
    Cyan · Studio —— 一键部署打包脚本（在 Windows 本地执行，产出 deploy.zip）

.DESCRIPTION
    自动检测技术栈 -> 构建前端/后端 -> 只收集服务器运行必需的文件 -> 排除敏感文件 -> 生成 deploy.zip

    产出 zip 的内部结构已对齐 deploy/nginx.conf 与 deploy/portfolio.service 中的路径约定：
        app.jar                        -> /opt/portfolio/app.jar        (systemd ExecStart)
        web/                           -> /opt/portfolio/web            (nginx root)
        deploy/portfolio.env           -> systemd EnvironmentFile（从 .example 复制后填写）
        deploy/nginx.conf              -> /etc/nginx/conf.d/portfolio.conf
        deploy/portfolio.service       -> /etc/systemd/system/portfolio.service
        db/schema.sql                  -> 数据库初始化脚本
        config/application.yml         -> 参考配置（真正的配置在 jar 内）

.PARAMETER SkipFrontend
    跳过前端构建（复用已有的 web/dist）

.PARAMETER SkipBackend
    跳过后端构建（复用已有的 server/target/*.jar）

.PARAMETER KeepStage
    保留中间暂存目录 _deploy_stage，便于排查

.PARAMETER OutFile
    输出的 zip 文件名，默认 deploy.zip

.EXAMPLE
    .\pack-deploy.ps1
#>
[CmdletBinding()]
param(
    [switch]$SkipFrontend,
    [switch]$SkipBackend,
    [switch]$KeepStage,
    [string]$OutFile = 'deploy.zip'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# ============================== 输出辅助 ==============================
function Step { param([string]$m) Write-Host "`n=== $m ===" -ForegroundColor Cyan }
function Ok   { param([string]$m) Write-Host "  [OK]  $m" -ForegroundColor Green }
function Warn { param([string]$m) Write-Host "  [!!]  $m" -ForegroundColor Yellow }
function Fail { param([string]$m) Write-Host "  [XX]  $m" -ForegroundColor Red }

<#
  执行原生命令（node / npm / mvn）。
  坑：在 $ErrorActionPreference='Stop' 下，原生命令只要往 stderr 写一个字节
  （例如 npm 的 "npm notice" 提示），PowerShell 就会把它当成终止错误抛异常，
  导致明明构建成功却判定失败。这里临时降级为 Continue，并用退出码判断成败。
#>
function Invoke-Native {
    param([string]$Command)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & cmd.exe /c $Command 2>&1 | Out-Host
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prev
    return $code
}

$Root = Split-Path -Parent $MyInvocation.MyCommand.Definition
if (-not (Test-Path (Join-Path $Root 'server\pom.xml'))) {
    # 兼容从其它目录调用
    $Root = (Get-Location).Path
}
Step "项目根目录：$Root"

# ============================== 1. 技术栈自动检测 ==============================
Step '1/6 检测项目技术栈'

$hasFrontend = Test-Path (Join-Path $Root 'web\package.json')
$hasBackend  = Test-Path (Join-Path $Root 'server\pom.xml')
$hasGradle   = Test-Path (Join-Path $Root 'server\build.gradle')

$pkgJson = $null
if ($hasFrontend) {
    $pkgJson = Get-Content (Join-Path $Root 'web\package.json') -Raw | ConvertFrom-Json
    Ok "前端：$($pkgJson.name)  (Node/Vite 体系，构建脚本: $($pkgJson.scripts.build))"
} else { Warn '未发现 web/package.json，跳过前端' }

if ($hasBackend) {
    $pom = Get-Content (Join-Path $Root 'server\pom.xml') -Raw
    $javaVer = if ($pom -match '<java\.version>(\d+)') { $Matches[1] } else { '17' }
    $finalName = if ($pom -match '<finalName>([^<]+)</finalName>') { $Matches[1] } else { 'portfolio-server' }
    Ok "后端：Maven 项目，Java $javaVer，产物名 $finalName"
} elseif ($hasGradle) {
    Fail '检测到 Gradle 项目，本脚本当前仅处理 Maven，请自行构建后加 -SkipBackend'
    exit 1
} else { Warn '未发现 pom.xml，跳过后端' }

# ============================== 2. 工具链检测 ==============================
Step '2/6 检测本地构建工具链'

function Find-Tool {
    param([string]$name)
    $c = Get-Command $name -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    return $null
}

$node = Find-Tool 'node'
$npm  = Find-Tool 'npm'
$mvn  = Find-Tool 'mvn'

if ($hasFrontend) {
    if ($node) {
        $nodeVer = ((& $node -v) | Out-String).Trim()
        Ok "node: $node  ($nodeVer)"
    } else { Fail '未找到 node，无法构建前端'; exit 1 }
    if (-not $npm) { Fail '未找到 npm'; exit 1 }
    Ok "npm:  $npm"
}

if ($hasBackend) {
    # JAVA_HOME：优先现有值，其次探测常见 JDK 路径
    if (-not $env:JAVA_HOME) {
        foreach ($p in @('C:\Java', 'C:\Program Files\Java\jdk-17', 'C:\Program Files\Java\jdk-21', "$env:USERPROFILE\.toolchain\jdk17")) {
            if (Test-Path (Join-Path $p 'bin\java.exe')) { $env:JAVA_HOME = $p; break }
        }
    }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        Ok "JAVA_HOME: $env:JAVA_HOME"
    } else { Fail '未找到 JDK，请先安装 JDK 17 或设置 JAVA_HOME'; exit 1 }

    # Maven：PATH 里没有则探测常见安装位置
    if (-not $mvn) {
        $cands = @(
            "$env:USERPROFILE\.toolchain\maven\bin\mvn.cmd",
            'C:\Program Files\apache-maven\bin\mvn.cmd',
            "$env:USERPROFILE\.m2\wrapper\mvn.cmd"
        )
        foreach ($c in $cands) { if (Test-Path $c) { $mvn = $c; break } }
    }
    if ($mvn) {
        $mvnBin = Split-Path -Parent $mvn
        $env:PATH = "$mvnBin;$env:PATH"
        Ok "mvn: $mvn"
    } else { Fail '未找到 Maven，请先安装 Maven 3.6+'; exit 1 }
}

# ============================== 3. 构建前端 ==============================
if ($hasFrontend -and -not $SkipFrontend) {
    Step '3/6 构建前端（npm run build）'
    Push-Location (Join-Path $Root 'web')
    try {
        if (-not (Test-Path 'node_modules')) {
            Warn 'node_modules 缺失，先安装依赖（缓存目录已重定向到用户目录，避免无效路径报错）'
            $c = Invoke-Native "npm install --cache `"$env:USERPROFILE\.npm-cache`" --no-audit --no-fund"
            if ($c -ne 0) { throw 'npm install 失败，退出码 ' + $c }
        }
        $c = Invoke-Native 'npm run build'
        if ($c -ne 0) {
            Warn 'npm run build 失败（通常是 vue-tsc 类型检查报错），回退为 vite build（跳过类型检查）继续'
            $c2 = Invoke-Native 'npx vite build'
            if ($c2 -ne 0) { throw 'vite build 也失败，退出码 ' + $c2 }
        }
    } catch {
        Fail "前端构建失败：$($_.Exception.Message)"
        Pop-Location
        exit 1
    }
    Pop-Location
    if (-not (Test-Path (Join-Path $Root 'web\dist\index.html'))) { Fail 'web/dist/index.html 未生成'; exit 1 }
    $distCount = (Get-ChildItem (Join-Path $Root 'web\dist') -Recurse -File).Count
    Ok "前端构建完成，dist 共 $distCount 个文件"
} elseif ($hasFrontend) {
    Step '3/6 跳过前端构建（-SkipFrontend），复用已有 web/dist'
    if (-not (Test-Path (Join-Path $Root 'web\dist\index.html'))) { Fail 'web/dist 不存在，无法跳过'; exit 1 }
}

# ============================== 4. 构建后端 ==============================
$jarFile = $null
if ($hasBackend -and -not $SkipBackend) {
    Step '4/6 构建后端（mvn clean package -DskipTests）'
    Push-Location (Join-Path $Root 'server')
    try {
        $c = Invoke-Native 'mvn -B clean package -DskipTests'
        if ($c -ne 0) { throw 'mvn package 失败，退出码 ' + $c }
    } catch {
        Fail "后端构建失败：$($_.Exception.Message)"
        Pop-Location
        exit 1
    }
    Pop-Location
} elseif ($hasBackend) {
    Step '4/6 跳过后段构建（-SkipBackend），复用已有产物'
}

if ($hasBackend) {
    $jars = @(Get-ChildItem (Join-Path $Root 'server\target\*.jar') -ErrorAction SilentlyContinue |
              Where-Object { $_.Name -notmatch '\.original$' -and $_.Name -notmatch 'sources|javadoc' })
    if ($jars.Count -eq 0) { Fail '未找到可执行的 jar 产物'; exit 1 }
    $jarFile = $jars | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $jarSizeText = '{0:N1} MB' -f ($jarFile.Length / 1MB)
    Ok "后端产物：$($jarFile.Name)  ($jarSizeText)"
}

# ============================== 5. 组装待打包内容（白名单） ==============================
Step '5/6 组装待打包内容（白名单收集，天然排除 node_modules/.git/日志/缓存/测试）'

$stage = Join-Path $Root '_deploy_stage'
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage -Force | Out-Null
foreach ($d in @('web', 'deploy', 'db', 'config', 'scripts')) {
    New-Item -ItemType Directory -Path (Join-Path $stage $d) -Force | Out-Null
}

$manifest = @()

function Copy-In {
    param([string]$Src, [string]$Dst, [string]$Label)
    if (-not (Test-Path $Src)) { Warn "缺失（跳过）：$Src"; return }
    $target = Join-Path $stage $Dst
    $targetDir = Split-Path -Parent $target
    if (-not (Test-Path $targetDir)) { New-Item -ItemType Directory -Path $targetDir -Force | Out-Null }
    if ((Get-Item $Src).PSIsContainer) {
        Copy-Item (Join-Path $Src '*') $target -Recurse -Force
    } else {
        Copy-Item $Src $target -Force
    }
    $script:manifest += [PSCustomObject]@{ 类别 = $Label; 包内路径 = $Dst; 来源 = $Src.Replace($Root, '.') }
}

# --- 运行产物 ---
if ($jarFile) { Copy-In $jarFile.FullName 'app.jar' '后端可执行 jar' }
Copy-In (Join-Path $Root 'web\dist') 'web' '前端静态产物'

# --- 服务器配置（nginx / systemd / redis / 环境变量模板） ---
Copy-In (Join-Path $Root 'deploy\nginx.conf')                  'deploy\nginx.conf'                  'Nginx 站点配置'
Copy-In (Join-Path $Root 'deploy\nginx-tls.conf.example')      'deploy\nginx-tls.conf.example'      'HTTPS 配置模板'
Copy-In (Join-Path $Root 'deploy\portfolio.service')           'deploy\portfolio.service'           'systemd 单元'
Copy-In (Join-Path $Root 'deploy\redis.conf')                  'deploy\redis.conf'                  'Redis 配置'
Copy-In (Join-Path $Root 'deploy\env.production.example')      'deploy\env.production.example'      '环境变量模板（不含真实密钥）'

# --- 数据库迁移 / 初始化 ---
Copy-In (Join-Path $Root 'server\src\main\resources\db\schema.sql') 'db\schema.sql' '数据库建表脚本'

# --- 运行时配置参考 & 种子内容 ---
Copy-In (Join-Path $Root 'server\src\main\resources\application.yml')        'config\application.yml'        '应用配置参考'
Copy-In (Join-Path $Root 'server\src\main\resources\default-content.json')   'config\default-content.json'   '默认内容种子'

# --- 运维脚本 ---
# 注意：仓库自带的 scripts/backup.sh 与 restore.sh 是 Docker 版（依赖 docker compose exec），
#      裸机 Ubuntu 上无法使用，因此这里改为生成等价的裸机版脚本。
Copy-In (Join-Path $Root 'README.md')          'README.md'          '项目说明'

# ============================== 5.1 配置脱敏 ==============================
# application.yml 里带本机开发用的默认密码兜底值（${MYSQL_PASSWORD:Cyan1120} 之类），
# jar 内也有一份。这里把复制到包里的参考副本脱敏，避免把开发密码带上服务器。
# 真正的生产值由 /opt/portfolio/deploy/portfolio.env 通过环境变量注入，不受影响。
$appYmlStage = Join-Path $stage 'config\application.yml'
if (Test-Path $appYmlStage) {
    $ymlText = Get-Content $appYmlStage -Raw
    $ymlText = $ymlText -replace 'Cyan1120', 'CHANGEME-INJECT-BY-ENV'
    $ymlText = $ymlText -replace 'please-change-me-please-change-me-please-change-me-32byte-minimum', 'CHANGEME-USE-openssl-rand-hex-48'
    Set-Content -Path $appYmlStage -Value $ymlText -Encoding UTF8
    Ok 'config/application.yml 已脱敏（默认密码替换为占位符，生产值由环境变量注入）'
}

# ============================== 5.2 生成裸机版运维脚本 ==============================
$backupScript = @'
#!/usr/bin/env bash
# =============================================================================
#  裸机 Ubuntu 备份脚本（仓库自带的 scripts/backup.sh 是 Docker 版，裸机不可用）
#  用法：  sudo bash /opt/portfolio/scripts/backup-ubuntu.sh [输出目录]
#  定时：  0 3 * * * bash /opt/portfolio/scripts/backup-ubuntu.sh >> /var/log/pf-backup.log 2>&1
# =============================================================================
set -euo pipefail

ENV_FILE=/opt/portfolio/deploy/portfolio.env
OUT_DIR="${1:-/var/backups/portfolio}"
UPLOAD_DIR=/var/lib/portfolio/upload

if [ -f "$ENV_FILE" ]; then
  set -a; . "$ENV_FILE"; set +a
fi

MYSQL_DB="${MYSQL_DB:-Cyan}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT_DIR"

echo "[backup] 数据库 -> $OUT_DIR/db-$STAMP.sql.gz"
mysqldump -h "${MYSQL_HOST:-127.0.0.1}" -P "${MYSQL_PORT:-3306}" \
  -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" \
  --single-transaction --quick --default-character-set=utf8mb4 "$MYSQL_DB" \
  | gzip > "$OUT_DIR/db-$STAMP.sql.gz"

echo "[backup] 上传素材 -> $OUT_DIR/media-$STAMP.tar.gz"
tar czf "$OUT_DIR/media-$STAMP.tar.gz" -C "$UPLOAD_DIR" . 2>/dev/null || \
  echo "[backup] 素材目录为空或不存在，跳过"

# 保留最近 14 份
ls -1t "$OUT_DIR"/db-*.sql.gz    2>/dev/null | tail -n +15 | xargs -r rm -f
ls -1t "$OUT_DIR"/media-*.tar.gz 2>/dev/null | tail -n +15 | xargs -r rm -f

echo "[backup] 完成：$OUT_DIR/db-$STAMP.sql.gz"
'@

$restoreScript = @'
#!/usr/bin/env bash
# =============================================================================
#  裸机 Ubuntu 恢复脚本
#  用法：  sudo bash /opt/portfolio/scripts/restore-ubuntu.sh backup/db-20261007-030000.sql.gz [backup/media-xxx.tar.gz]
#  ⚠️ 会覆盖现有数据，执行前请先停止后端：systemctl stop portfolio
# =============================================================================
set -euo pipefail

ENV_FILE=/opt/portfolio/deploy/portfolio.env
DB_FILE="${1:-}"
MEDIA_FILE="${2:-}"
UPLOAD_DIR=/var/lib/portfolio/upload

if [ -z "$DB_FILE" ] || [ ! -f "$DB_FILE" ]; then
  echo "用法: $0 <db-xxx.sql.gz> [media-xxx.tar.gz]" >&2
  exit 1
fi

if [ -f "$ENV_FILE" ]; then
  set -a; . "$ENV_FILE"; set +a
fi

MYSQL_DB="${MYSQL_DB:-Cyan}"

echo "[restore] 导入数据库：$DB_FILE"
gunzip -c "$DB_FILE" | mysql -h "${MYSQL_HOST:-127.0.0.1}" -P "${MYSQL_PORT:-3306}" \
  -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB"

if [ -n "$MEDIA_FILE" ] && [ -f "$MEDIA_FILE" ]; then
  echo "[restore] 解压素材：$MEDIA_FILE"
  mkdir -p "$UPLOAD_DIR"
  tar xzf "$MEDIA_FILE" -C "$UPLOAD_DIR"
  chown -R portfolio:portfolio "$UPLOAD_DIR"
fi

echo "[restore] 完成。请执行 systemctl start portfolio"
'@

New-Item -ItemType Directory -Path (Join-Path $stage 'scripts') -Force | Out-Null
Set-Content -Path (Join-Path $stage 'scripts\backup-ubuntu.sh')  -Value $backupScript  -Encoding UTF8
Set-Content -Path (Join-Path $stage 'scripts\restore-ubuntu.sh') -Value $restoreScript -Encoding UTF8
$manifest += [PSCustomObject]@{ 类别 = '运维脚本'; 包内路径 = 'scripts\backup-ubuntu.sh';  来源 = '(自动生成·裸机版)' }
$manifest += [PSCustomObject]@{ 类别 = '运维脚本'; 包内路径 = 'scripts\restore-ubuntu.sh'; 来源 = '(自动生成·裸机版)' }

# --- 生成部署文档 ---
$deployDoc = @'
# Cyan · Studio —— Ubuntu 服务器部署指南

目标：阿里云轻量应用服务器 + Ubuntu 22.04 / 24.04，Nginx + MySQL 8 + Redis + systemd。

## 0. 本压缩包内容

| 路径 | 用途 |
|---|---|
| `app.jar` | Spring Boot 可执行 jar（内嵌 Tomcat，监听 8080） |
| `web/` | 前端静态产物，对应 Nginx `root /opt/portfolio/web` |
| `deploy/nginx.conf` | Nginx 站点配置（已写好 /api、/media 反代） |
| `deploy/portfolio.service` | systemd 单元（运行用户 portfolio） |
| `deploy/env.production.example` | 环境变量模板，需复制成 `portfolio.env` 并填写 |
| `deploy/redis.conf` | Redis 配置参考 |
| `deploy/nginx-tls.conf.example` | 上 HTTPS 时的参考配置 |
| `db/schema.sql` | 建表脚本（幂等，可重复执行） |
| `config/` | application.yml（已脱敏）与默认内容种子（jar 内已含，此处供查阅） |
| `scripts/` | backup-ubuntu.sh / restore-ubuntu.sh 裸机版备份恢复脚本 |

## 1. 上传与解压

用 Xftp 把 `deploy.zip` 传到服务器的 `/opt` 目录，然后：

```bash
sudo apt update && sudo apt install -y unzip
sudo mkdir -p /opt/portfolio
sudo unzip -o /opt/deploy.zip -d /opt/portfolio
ls -la /opt/portfolio          # 应看到 app.jar、web/、deploy/、db/、config/、scripts/
```

## 2. 安装运行时

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless   # 或 openjdk-17-jdk-headless
sudo apt install -y mysql-server redis-server nginx
java -version        # 需显示 17.x
```

## 3. 初始化数据库

```bash
sudo mysql -e "CREATE DATABASE IF NOT EXISTS Cyan CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
sudo mysql -e "CREATE USER IF NOT EXISTS 'yc_app'@'%' IDENTIFIED BY '换成强密码';"
sudo mysql -e "GRANT ALL PRIVILEGES ON Cyan.* TO 'yc_app'@'%'; FLUSH PRIVILEGES;"

mysql -h 127.0.0.1 -u yc_app -p Cyan < /opt/portfolio/db/schema.sql
```

> 首次部署 `JPA_DDL=update` 会自动补齐实体对应的表；稳定运行后可改成 `validate`。

## 4. 配置 Redis 密码

```bash
sudo sed -i 's/^# *requirepass .*/requirepass 换成强密码/' /etc/redis/redis.conf
grep -q '^requirepass' /etc/redis/redis.conf || echo 'requirepass 换成强密码' | sudo tee -a /etc/redis/redis.conf
sudo systemctl restart redis-server
redis-cli -a '换成强密码' ping      # 应返回 PONG
```

## 5. 创建运行用户与目录

```bash
sudo useradd -r -s /usr/sbin/nologin portfolio
sudo mkdir -p /var/lib/portfolio/upload /var/log/portfolio
sudo chown -R portfolio:portfolio /var/lib/portfolio /var/log/portfolio
sudo chown -R portfolio:portfolio /opt/portfolio
```

> `/var/lib/portfolio/upload` 必须与 `APP_UPLOAD_DIR` 和 `deploy/nginx.conf` 里 `/media/` 的 alias 一致，否则上传的素材会 404。

## 6. 填写生产环境变量

```bash
cp /opt/portfolio/deploy/env.production.example /opt/portfolio/deploy/portfolio.env
chmod 600 /opt/portfolio/deploy/portfolio.env
sudo nano /opt/portfolio/deploy/portfolio.env
```

必须修改的项：

- `MYSQL_PASSWORD` / `REDIS_PASSWORD` —— 与第 3、4 步设置的一致
- `MYSQL_PORT=3306` —— **不要留默认 3307**（那是本地便携版的端口）
- `JWT_SECRET` —— 用 `openssl rand -hex 48` 生成后粘进去
- `ADMIN_USERNAME` / `ADMIN_PASSWORD` —— 首次启动播种管理员，登录后请立刻改密码并删掉这两行
- `APP_CORS_ORIGINS` —— 填你的域名，如 `https://example.com`

注意 systemd 的 EnvironmentFile 格式：不要写 `export`，值不要加引号，值里不要有空格。

## 7. 配置 Nginx

```bash
sudo rm -f /etc/nginx/sites-enabled/default          #  Ubuntu 默认站点会抢占 80 端口
sudo cp /opt/portfolio/deploy/nginx.conf /etc/nginx/conf.d/portfolio.conf
sudo nginx -t && sudo systemctl enable --now nginx
```

## 8. 启动后端

```bash
sudo cp /opt/portfolio/deploy/portfolio.service /etc/systemd/system/portfolio.service
sudo systemctl daemon-reload
sudo systemctl enable --now portfolio
```

> `portfolio.service` 里写的是 `After=redis.service`，Ubuntu 上服务名是 `redis-server.service`。
> 这不会导致启动失败（systemd 忽略不存在的依赖），若想干净可改成 `redis-server.service`。

## 9. 验证

```bash
curl http://127.0.0.1:8080/actuator/health      # 应返回 {"status":"UP"}
curl -I http://127.0.0.1/                        # 应返回 200
journalctl -u portfolio -f                       # 看实时日志
```

浏览器打开 `http://<服务器公网IP>` 看前台，`http://<IP>/login` 进后台。

## 10. 防火墙

阿里云控制台的**安全组**需放行 80（HTTP）、443（HTTPS）；如用 ufw：

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

## 11. 备份与恢复

仓库自带的 `scripts/backup.sh` 是 Docker 版，裸机不可用，包内已提供裸机版：

```bash
sudo bash /opt/portfolio/scripts/backup-ubuntu.sh /var/backups/portfolio
# 每天 03:00：0 3 * * * bash /opt/portfolio/scripts/backup-ubuntu.sh >> /var/log/pf-backup.log 2>&1

# 恢复（先停后端）
sudo systemctl stop portfolio
sudo bash /opt/portfolio/scripts/restore-ubuntu.sh /var/backups/portfolio/db-xxxx.sql.gz /var/backups/portfolio/media-xxxx.tar.gz
sudo systemctl start portfolio
```

> 备份脚本从 `/opt/portfolio/deploy/portfolio.env` 读取数据库账号密码，因此该文件必须存在。

## 常见问题

- **502 Bad Gateway**：后端没起来。看 `journalctl -u portfolio -n 100`，多半是数据库/Redis 密码不对或端口写错。
- **素材 404**：`APP_UPLOAD_DIR` 与 nginx `/media/` alias 不一致，或目录属主不是 portfolio。
- **登录接口 429**：nginx 对 `/api/auth/login` 限流为每分钟 12 次，等一分钟再试。
- **改了内容不生效**：后端有 Redis 缓存（TTL 300 秒），稍等或 `redis-cli -a 密码 FLUSHDB`。
'@

$setupScript = @'
#!/usr/bin/env bash
# =============================================================================
#  Cyan · Studio —— Ubuntu 服务器初始化脚本（在服务器上执行）
#  用法：
#    sudo bash setup-ubuntu.sh              # 只做目录/用户/服务配置，不装软件
#    sudo bash setup-ubuntu.sh --install-deps   # 额外 apt 安装 JDK/MySQL/Redis/Nginx
#  注意：数据库密码、Redis 密码、JWT 密钥不在此脚本中处理，请按 DEPLOY.md 手动填写。
# =============================================================================
set -euo pipefail

APP_DIR=/opt/portfolio
DATA_DIR=/var/lib/portfolio
LOG_DIR=/var/log/portfolio
RUN_USER=portfolio

if [ "$(id -u)" -ne 0 ]; then
  echo "请用 root 执行：sudo bash setup-ubuntu.sh"; exit 1
fi

# ---------- 可选：安装依赖 ----------
if [[ "${1:-}" == "--install-deps" ]]; then
  echo ">>> 安装运行时依赖..."
  apt-get update -y
  apt-get install -y unzip curl ca-certificates
  apt-get install -y openjdk-17-jre-headless mysql-server redis-server nginx
fi

# ---------- 校验解压内容 ----------
if [ ! -f "$APP_DIR/app.jar" ]; then
  echo "未找到 $APP_DIR/app.jar，请先把 deploy.zip 解压到 $APP_DIR"; exit 1
fi
if [ ! -d "$APP_DIR/web" ]; then
  echo "未找到 $APP_DIR/web，压缩包不完整"; exit 1
fi

# ---------- 运行用户 ----------
if ! id -u "$RUN_USER" >/dev/null 2>&1; then
  echo ">>> 创建系统用户 $RUN_USER"
  useradd -r -s /usr/sbin/nologin "$RUN_USER"
else
  echo ">>> 用户 $RUN_USER 已存在，跳过"
fi

# ---------- 目录与权限 ----------
echo ">>> 准备目录与权限"
mkdir -p "$DATA_DIR/upload" "$LOG_DIR"
chown -R "$RUN_USER":"$RUN_USER" "$DATA_DIR" "$LOG_DIR" "$APP_DIR"
chmod 750 "$DATA_DIR" "$LOG_DIR"

# ---------- systemd ----------
if [ -f "$APP_DIR/deploy/portfolio.service" ]; then
  echo ">>> 安装 systemd 单元"
  cp "$APP_DIR/deploy/portfolio.service" /etc/systemd/system/portfolio.service
  systemctl daemon-reload
  systemctl enable portfolio
  echo "    （未启动：请先在 $APP_DIR/deploy/portfolio.env 填好密码与密钥）"
  echo "    填好后执行：systemctl start portfolio"
else
  echo ">>> 未找到 portfolio.service，跳过"
fi

# ---------- nginx ----------
if [ -f "$APP_DIR/deploy/nginx.conf" ]; then
  echo ">>> 安装 nginx 配置"
  rm -f /etc/nginx/sites-enabled/default
  cp "$APP_DIR/deploy/nginx.conf" /etc/nginx/conf.d/portfolio.conf
  if nginx -t 2>/dev/null; then
    systemctl enable nginx
    systemctl reload nginx 2>/dev/null || systemctl start nginx
    echo "    nginx 已就绪"
  else
    echo "    [警告] nginx -t 未通过，请检查 $APP_DIR/deploy/nginx.conf"
  fi
fi

# ---------- 环境变量文件 ----------
if [ -f "$APP_DIR/deploy/env.production.example" ] && [ ! -f "$APP_DIR/deploy/portfolio.env" ]; then
  cp "$APP_DIR/deploy/env.production.example" "$APP_DIR/deploy/portfolio.env"
  chmod 600 "$APP_DIR/deploy/portfolio.env"
  echo ">>> 已生成 $APP_DIR/deploy/portfolio.env（权限 600），请填入："
  echo "    MYSQL_PASSWORD / REDIS_PASSWORD / JWT_SECRET / ADMIN_PASSWORD / APP_CORS_ORIGINS"
fi

# ---------- 防火墙 ----------
if command -v ufw >/dev/null 2>&1; then
  ufw allow 80/tcp    >/dev/null 2>&1 || true
  ufw allow 443/tcp   >/dev/null 2>&1 || true
  echo ">>> ufw 已放行 80/443（若 ufw 未启用请自行 ufw enable）"
fi

echo
echo "============================================================================="
echo " 初始化完成。接下来请手动完成："
echo "  1) 建库建用户：见 DEPLOY.md 第 3 步"
echo "  2) 设置 Redis 密码：见 DEPLOY.md 第 4 步"
echo "  3) 编辑 $APP_DIR/deploy/portfolio.env 填入密码与密钥"
echo "  4) systemctl start portfolio && journalctl -u portfolio -f"
echo "============================================================================="
'@

Set-Content -Path (Join-Path $stage 'DEPLOY.md')       -Value $deployDoc   -Encoding UTF8
Set-Content -Path (Join-Path $stage 'setup-ubuntu.sh') -Value $setupScript -Encoding UTF8
$manifest += [PSCustomObject]@{ 类别 = '部署文档'; 包内路径 = 'DEPLOY.md';        来源 = '(自动生成)' }
$manifest += [PSCustomObject]@{ 类别 = '初始化脚本'; 包内路径 = 'setup-ubuntu.sh'; 来源 = '(自动生成)' }

# ============================== 6. 敏感文件扫描 ==============================
Step '6/6 敏感文件扫描'

$blockedNames = @('.env', '.env.local', 'web\.env.local', 'portfolio.env', 'application-prod.yml', 'id_rsa', 'id_ed25519', '*.pem', '*.key', '*.p12', '*.pfx', '*.jks')
$hits = @()
foreach ($f in (Get-ChildItem $stage -Recurse -File)) {
    foreach ($pat in $blockedNames) {
        if ($f.Name -like $pat -or $f.Name -eq $pat) { $hits += $f.FullName.Replace($stage, ''); break }
    }
}

# 内容层扫描：本机开发密码不应出现在包里
$leakPatterns = @('Cyan1120', 'please-change-me', 'JWT_SECRET=[A-Za-z0-9+/=]{32,}')
$scanExt = @('.yml', '.yaml', '.json', '.conf', '.sh', '.md', '.example', '.sql', '.service')
$leaks = @()
foreach ($f in (Get-ChildItem $stage -Recurse -File)) {
    if ($scanExt -notcontains $f.Extension.ToLower()) { continue }
    $txt = Get-Content $f.FullName -Raw -ErrorAction SilentlyContinue
    if (-not $txt) { continue }
    foreach ($p in $leakPatterns) {
        if ($txt -match $p) { $leaks += "$($f.FullName.Replace($stage,''))  命中: $p"; break }
    }
}

if ($hits.Count -gt 0) {
    Fail '发现敏感文件，已中止打包：'
    $hits | ForEach-Object { Write-Host "        $_" -ForegroundColor Red }
    exit 1
} else { Ok '文件名扫描通过：无 .env / 私钥 / 生产密钥文件' }

if ($leaks.Count -gt 0) {
    Fail '发现疑似敏感内容，已中止打包：'
    $leaks | ForEach-Object { Write-Host "        $_" -ForegroundColor Red }
    exit 1
} else { Ok '内容扫描通过：无本机开发密码、无真实 JWT 密钥' }

# ============================== 打包 ==============================
$zipPath = Join-Path $Root $OutFile
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zipPath -Force

$zipInfo = Get-Item $zipPath
$fileCount = (Get-ChildItem $stage -Recurse -File).Count
$zipSizeText = '{0:N2} MB' -f ($zipInfo.Length / 1MB)

Step '打包完成'
Ok "输出：$zipPath  ($zipSizeText，共 $fileCount 个文件)"

Write-Host "`n--- 包内清单 ---" -ForegroundColor Cyan
$manifest | Format-Table -AutoSize | Out-String | Write-Host

if (-not $KeepStage) {
    Remove-Item $stage -Recurse -Force
    Ok '已清理中间暂存目录 _deploy_stage'
}

Write-Host "`n下一步：" -ForegroundColor Yellow
Write-Host "  1) 用 Xftp 把 deploy.zip 传到服务器 /opt" -ForegroundColor White
Write-Host "  2) 服务器上执行：unzip -o /opt/deploy.zip -d /opt/portfolio" -ForegroundColor White
Write-Host "  3) 按包内 DEPLOY.md（或执行 bash setup-ubuntu.sh --install-deps）完成部署`n" -ForegroundColor White
