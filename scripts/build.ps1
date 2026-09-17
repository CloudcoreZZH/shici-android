param([string[]]$Tasks = @(':core:test', ':app:testDebugUnitTest', ':app:lintRelease', ':app:assembleRelease'), [switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
# Java's Windows argument-file parser can misread non-ASCII paths. A temporary
# drive alias points to the same workspace; nothing is copied elsewhere.
$alias = $null
if ($projectRoot -match '[^\x00-\x7F]') {
    $occupied = [System.IO.DriveInfo]::GetDrives().Name
    foreach ($letter in @('R','S','T','U','V','W')) {
        if ($occupied -notcontains ($letter + ':\')) { $alias = $letter + ':'; break }
    }
    if (!$alias) { throw '没有可用的临时盘符，请释放一个盘符后重试。' }
    & subst.exe $alias $projectRoot
    if ($LASTEXITCODE -ne 0) { throw '无法建立当前项目的临时路径别名。' }
    $projectRoot = $alias + '\'
}
$env:JAVA_HOME = Join-Path $projectRoot '.tooling\jdk-21'
$env:ANDROID_HOME = Join-Path $projectRoot '.tooling\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.cache\android'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.cache\gradle'
$env:TEMP = Join-Path $projectRoot '.cache\tmp'
$env:TMP = $env:TEMP
$env:JAVA_TOOL_OPTIONS = '-Duser.home="' + (Join-Path $projectRoot '.cache\java-home') + '" -Djava.io.tmpdir="' + $env:TEMP + '"'
foreach($folder in @($env:TEMP, $env:ANDROID_USER_HOME, $env:GRADLE_USER_HOME, (Join-Path $projectRoot '.cache\java-home'))) {
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
}
$gradle = Join-Path $projectRoot '.tooling\gradle-8.13\bin\gradle.bat'
if (!(Test-Path -LiteralPath $gradle)) { throw '构建工具未准备好；请先运行已授权的 scripts/bootstrap.py。' }
Push-Location $projectRoot
try {
    if ($Tasks -match 'Release') {
        & 'D:\anaconda3\envs\NLP\python.exe' (Join-Path $projectRoot 'scripts\prepare_signing.py')
        if ($LASTEXITCODE -ne 0) { throw '签名文件准备失败。' }
    }
    $extra = @('--console=plain', '--no-daemon', '-Dorg.gradle.java.installations.auto-download=false', "-Dorg.gradle.java.installations.paths=$env:JAVA_HOME")
    if ($Offline) { $extra += '--offline' }
    & $gradle @Tasks @extra
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
    if ($Tasks -contains ':app:assembleRelease') {
        & 'D:\anaconda3\envs\NLP\python.exe' (Join-Path $projectRoot 'scripts\verify_apk.py')
        if ($LASTEXITCODE -ne 0) { throw 'APK 交付验证未通过。' }
    }
} finally {
    Pop-Location
    if ($alias) { & subst.exe $alias /D }
}
