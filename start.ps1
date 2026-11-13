# AI Terminal 启动脚本：自动读取根目录 .env（密钥放 .env，不进仓库）
# 用法：右键"使用 PowerShell 运行"，或 .\start.ps1
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

# 1. 加载 .env 为进程环境变量
if (Test-Path "$root\.env") {
    Get-Content "$root\.env" | ForEach-Object {
        if ($_ -match '^\s*([^#=\s]+)\s*=\s*(.*?)\s*$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
} else {
    Write-Host '未找到 .env，请复制 .env.example 为 .env 并填写 OPENAI_API_KEY' -ForegroundColor Yellow
}

# 2. 定位 Java：优先用工作区内置 JDK，其次系统 PATH
$java = if (Test-Path "$root\.tools\jdk21\jdk-21.0.2\bin\java.exe") {
    "$root\.tools\jdk21\jdk-21.0.2\bin\java.exe"
} else { 'java' }

# 3. 定位 jar：没有就现场打包
$jar = "$root\target\ai-terminal-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path $jar)) {
    Write-Host '未找到 jar，开始打包...'
    $mvn = if (Test-Path "$root\.tools\apache-maven-3.9.9\bin\mvn.cmd") {
        "$root\.tools\apache-maven-3.9.9\bin\mvn.cmd"
    } else { 'mvn' }
    & $mvn -q -B -s "$root\.tools\settings.xml" package -DskipTests
}

# 4. 启动
Write-Host "启动 AI Terminal: http://localhost:8080 (Ctrl+C 停止)" -ForegroundColor Green
& $java -jar $jar
