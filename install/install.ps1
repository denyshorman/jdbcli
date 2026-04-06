# jdbcli installer — Windows PowerShell
#
# Usage (run in PowerShell as your normal user — no admin required):
#   irm https://raw.githubusercontent.com/denyshorman/jdbcli/main/install/install.ps1 | iex
#
# Environment overrides (optional):
#   $env:JDBCLI_INSTALL_DIR  — directory for the jdbcli.cmd wrapper  (default: ~\.local\bin)
#   $env:JDBCLI_JAR_DIR      — directory where jdbcli.jar is stored  (default: ~\.local\share\jdbcli)

$ErrorActionPreference = "Stop"

$GithubRepo  = "denyshorman/jdbcli"
$AssetName   = "jdbcli.jar"
$InstallDir  = if ($env:JDBCLI_INSTALL_DIR) { $env:JDBCLI_INSTALL_DIR } else { "$env:USERPROFILE\.local\bin" }
$JarDir      = if ($env:JDBCLI_JAR_DIR) { $env:JDBCLI_JAR_DIR } else { "$env:USERPROFILE\.local\share\jdbcli" }
$JarPath     = Join-Path $JarDir $AssetName
$WrapperPath = Join-Path $InstallDir "jdbcli.cmd"

function Write-Info { param($msg) Write-Host "[jdbcli] $msg" -ForegroundColor Green }
function Write-Warn { param($msg) Write-Host "[jdbcli] $msg" -ForegroundColor Yellow }
function Fail       { param($msg) Write-Host "[jdbcli] ERROR: $msg" -ForegroundColor Red; exit 1 }

Write-Info "Checking Java..."

try {
    $javaOut = java -version 2>&1 | Out-String

    if ($javaOut -match '"(\d+)') {
        $javaVersion = [int]$Matches[1]
    } else {
        Fail "Could not determine Java version."
    }
} catch {
    Fail "Java not found. jdbcli requires Java 21+. Install from https://adoptium.net"
}

if ($javaVersion -lt 21) {
    Fail "Java $javaVersion found, but jdbcli requires Java 21+. Install from https://adoptium.net"
}

Write-Info "Java $javaVersion detected."
Write-Info "Fetching latest release..."

$ApiUrl  = "https://api.github.com/repos/$GithubRepo/releases/latest"
$Release = Invoke-RestMethod -Uri $ApiUrl -Headers @{ Accept = "application/vnd.github+json" }
$TagName = $Release.tag_name

if (-not $TagName) { Fail "Could not determine latest release tag." }

$DownloadUrl = "https://github.com/$GithubRepo/releases/download/$TagName/$AssetName"

Write-Info "Downloading $TagName..."

New-Item -ItemType Directory -Force -Path $JarDir     | Out-Null
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null

Invoke-WebRequest -Uri $DownloadUrl -OutFile $JarPath -UseBasicParsing

$WrapperContent = "@echo off`r`njava -XX:TieredStopAtLevel=1 -Xss256k -Xms8m -jar `"$JarPath`" %*"
Set-Content -Path $WrapperPath -Value $WrapperContent -Encoding ASCII

Write-Info "jdbcli $TagName installed."
Write-Info "  JAR:     $JarPath"
Write-Info "  Wrapper: $WrapperPath"

$UserPath = [System.Environment]::GetEnvironmentVariable("Path", "User")

if ($UserPath -notlike "*$InstallDir*") {
    Write-Warn ""
    Write-Warn "$InstallDir is not in your PATH."
    Write-Warn "Run the following to add it permanently, then restart your terminal:"
    Write-Warn ""
    Write-Warn "  [System.Environment]::SetEnvironmentVariable('Path', `$env:Path + ';$InstallDir', 'User')"
    Write-Warn ""
} else {
    Write-Info ""
    Write-Info "Run:  jdbcli --help"
}
