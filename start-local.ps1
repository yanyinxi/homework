# start-local.ps1 — 一键本地启动（Windows）
# 自动检测并安装缺失依赖（需要 winget，Windows 10 1709+ 自带）
# 用法：右键"以管理员身份运行 PowerShell"，再执行 .\start-local.ps1
param()

$ErrorActionPreference = "Stop"
$ROOT = Split-Path -Parent $MyInvocation.MyCommand.Path

# ── 颜色输出 ──────────────────────────────────────────────────────────────────
function Log   { param($msg) Write-Host "[INFO]  $msg" -ForegroundColor Cyan }
function Ok    { param($msg) Write-Host "[OK]    $msg" -ForegroundColor Green }
function Warn  { param($msg) Write-Host "[WARN]  $msg" -ForegroundColor Yellow }
function Err   { param($msg) Write-Host "[ERROR] $msg" -ForegroundColor Red; exit 1 }
function Step  { param($msg) Write-Host "`n══ $msg ══" -ForegroundColor Magenta }

# ── 刷新当前进程 PATH（winget 安装后立即生效） ────────────────────────────────
function Refresh-Path {
    $machinePath = [System.Environment]::GetEnvironmentVariable("PATH", "Machine")
    $userPath    = [System.Environment]::GetEnvironmentVariable("PATH", "User")
    $env:PATH = "$machinePath;$userPath"
}

# ── 端口冲突释放 ──────────────────────────────────────────────────────────────
function Free-Port {
    param([int]$Port, [string]$Desc)
    $connections = netstat -ano 2>$null | Select-String ":$Port\s" | Select-String "LISTENING"
    if ($connections) {
        $pid = ($connections | Select-Object -First 1).ToString().Trim() -split '\s+' | Select-Object -Last 1
        Warn "端口 $Port ($Desc) 被占用（PID: $pid），正在释放..."
        Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 1
        Ok "端口 $Port 已释放"
    }
}

Write-Host ""
Write-Host "╔══════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║     视频素材查询服务 — 本地一键启动      ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# ── 检查 winget ───────────────────────────────────────────────────────────────
if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    Err "未检测到 winget。请升级到 Windows 10 1709+ 或从 Microsoft Store 安装『应用安装程序』后重试。"
}

# ── 安装 Java 17+ ─────────────────────────────────────────────────────────────
Step "检查 Java 17+"
$javaOk = $false
if (Get-Command java -ErrorAction SilentlyContinue) {
    $verLine = java -version 2>&1 | Select-String '"(\d+)'
    if ($verLine -and [int]($verLine.Matches[0].Groups[1].Value) -ge 17) {
        Ok "Java 已满足要求: $(java -version 2>&1 | Select-Object -First 1)"
        $javaOk = $true
    }
}
if (-not $javaOk) {
    Warn "未检测到 Java 17+，正在通过 winget 安装 Microsoft OpenJDK 17..."
    winget install --id Microsoft.OpenJDK.17 --silent --accept-source-agreements --accept-package-agreements
    Refresh-Path
    Ok "Java 17 安装完成"
}

# ── 安装 Maven 3.9+ ───────────────────────────────────────────────────────────
Step "检查 Maven 3.9+"
$mvnOk = $false
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    $mvnMatch = (mvn -v 2>&1 | Select-String 'Apache Maven (\d+)\.(\d+)').Matches
    if ($mvnMatch.Count -gt 0) {
        $major = [int]$mvnMatch[0].Groups[1].Value
        $minor = [int]$mvnMatch[0].Groups[2].Value
        if ($major -gt 3 -or ($major -eq 3 -and $minor -ge 9)) {
            Ok "Maven 已满足要求: $(mvn -v | Select-Object -First 1)"
            $mvnOk = $true
        }
    }
}
if (-not $mvnOk) {
    Warn "未检测到 Maven 3.9+，正在通过 winget 安装..."
    winget install --id Apache.Maven --silent --accept-source-agreements --accept-package-agreements
    Refresh-Path
    Ok "Maven 安装完成"
}

# ── 安装 Node.js 20+ ──────────────────────────────────────────────────────────
Step "检查 Node.js 20+"
$nodeOk = $false
if (Get-Command node -ErrorAction SilentlyContinue) {
    $nodeVer = [int]((node -v) -replace 'v(\d+).*', '$1')
    if ($nodeVer -ge 20) {
        Ok "Node.js 已满足要求: $(node -v)"
        $nodeOk = $true
    }
}
if (-not $nodeOk) {
    Warn "未检测到 Node.js 20+，正在通过 winget 安装..."
    winget install --id OpenJS.NodeJS.LTS --silent --accept-source-agreements --accept-package-agreements
    Refresh-Path
    Ok "Node.js 安装完成: $(node -v)"
}

# ── 安装并启动 PostgreSQL 15 ──────────────────────────────────────────────────
Step "检查 PostgreSQL 15"
if (-not (Get-Command psql -ErrorAction SilentlyContinue)) {
    Warn "未检测到 PostgreSQL，正在通过 winget 安装..."
    winget install --id PostgreSQL.PostgreSQL.15 --silent --accept-source-agreements --accept-package-agreements
    Refresh-Path
    Ok "PostgreSQL 15 安装完成"
} else {
    Ok "PostgreSQL 已安装: $(psql --version)"
}

# 启动 PG Windows 服务（名称因安装方式不同而异）
Log "启动 PostgreSQL 服务..."
$pgSvc = Get-Service -Name "postgresql*" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($pgSvc) {
    if ($pgSvc.Status -ne "Running") {
        Start-Service $pgSvc.Name
        Start-Sleep -Seconds 3
    }
    Ok "PostgreSQL 服务运行中: $($pgSvc.Name)"
} else {
    Warn "未找到 PostgreSQL Windows 服务，假设已在 localhost:5432 运行"
}

# 配置数据库角色和数据库
Log "配置数据库用户 'asset' 和数据库 'asset_db'..."
$env:PGPASSWORD = "postgres"
$pgArgs = @("-U", "postgres", "-h", "localhost", "postgres", "-t", "-A")

psql @pgArgs -c @"
DO `$`$ BEGIN
  CREATE ROLE asset LOGIN PASSWORD 'asset123';
EXCEPTION WHEN duplicate_object THEN NULL;
END `$`$;
"@ 2>$null | Out-Null

$dbExists = (psql @pgArgs -c "SELECT 1 FROM pg_database WHERE datname='asset_db'" 2>$null).Trim()
if ($dbExists -ne "1") {
    psql @pgArgs -c "CREATE DATABASE asset_db OWNER asset;" 2>$null | Out-Null
    Log "数据库 asset_db 创建成功"
}
psql @pgArgs -c "GRANT ALL PRIVILEGES ON DATABASE asset_db TO asset;" 2>$null | Out-Null
Ok "数据库 'asset_db' 就绪（Flyway 将在后端启动时自动建表迁移）"

# ── 释放端口冲突 ──────────────────────────────────────────────────────────────
Step "检查端口占用"
Free-Port -Port 8080 -Desc "后端"
Free-Port -Port 5173 -Desc "前端"

# ── 启动后端（新窗口，显示实时日志） ─────────────────────────────────────────
Step "启动后端 (Spring Boot + --ingest=all)"
Log "后端在新窗口启动，日志实时显示，请勿关闭该窗口..."
$backendCmd = "cd '$ROOT\main\backend'; mvn spring-boot:run '-Dspring-boot.run.arguments=--ingest=all'"
$backendProc = Start-Process powershell -ArgumentList "-NoExit", "-Command", $backendCmd -PassThru

# 等待后端就绪（最多 120 秒）
Log "等待后端 /actuator/health 就绪（最多 120 秒）..."
$waited = 0
$ready = $false
while ($waited -lt 120) {
    Start-Sleep -Seconds 2; $waited += 2
    try {
        $resp = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($resp.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
    if ($waited % 10 -eq 0) { Log "  已等待 ${waited}s..." }
    if ($backendProc.HasExited) { Err "后端进程意外退出，请检查后端窗口日志" }
}
if (-not $ready) { Err "后端启动超时（120s），请检查后端窗口日志" }
Ok "后端已就绪 → http://localhost:8080"

# ── 启动前端（新窗口） ────────────────────────────────────────────────────────
Step "启动前端 (Vite + Vue 3)"
Log "前端在新窗口启动，请勿关闭该窗口..."
$frontendCmd = "cd '$ROOT\main\frontend'; npm install; npm run dev"
$frontendProc = Start-Process powershell -ArgumentList "-NoExit", "-Command", $frontendCmd -PassThru

# 等待前端就绪（最多 30 秒）
$waited = 0
do {
    Start-Sleep -Seconds 1; $waited++
    try { Invoke-WebRequest -Uri "http://localhost:5173" -UseBasicParsing -TimeoutSec 1 -ErrorAction Stop | Out-Null; break } catch {}
} while ($waited -lt 30)
Ok "前端已就绪 → http://localhost:5173"

# ── 就绪摘要 ──────────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "╔══════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║              所有服务已启动！            ║" -ForegroundColor Green
Write-Host "╠══════════════════════════════════════════╣" -ForegroundColor Green
Write-Host "║  前端管理后台  http://localhost:5173      ║" -ForegroundColor Green
Write-Host "║  Swagger UI   http://localhost:8080/     ║" -ForegroundColor Green
Write-Host "║               swagger-ui.html            ║" -ForegroundColor Green
Write-Host "║  健康检查     http://localhost:8080/     ║" -ForegroundColor Green
Write-Host "║               actuator/health            ║" -ForegroundColor Green
Write-Host "╠══════════════════════════════════════════╣" -ForegroundColor Green
Write-Host "║  关闭服务：关闭后端/前端两个 PS 窗口     ║" -ForegroundColor Green
Write-Host "╚══════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""

# 自动在浏览器打开前端
Start-Process "http://localhost:5173"
