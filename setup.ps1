# fieldarena2 bootstrap script (Windows / PowerShell)
#
# Bootstraps frontend (Next.js) and backend (Spring Boot) skeletons.
# Cloud services (Supabase Postgres for both data and sessions, Render BE, Vercel FE)
# are configured separately via .env. See README.md for signup steps.
#
# Usage:
#   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#   .\setup.ps1
#
# Idempotent: safe to re-run after partial failure.
# Output is intentionally English-only to avoid PowerShell 5.1 encoding issues.

$ErrorActionPreference = "Stop"

function Write-Step($msg) {
  Write-Host ""
  Write-Host "==> $msg" -ForegroundColor Cyan
}

function Test-Command($cmd) {
  $null = Get-Command $cmd -ErrorAction SilentlyContinue
  return $?
}

function Get-JavaMajorVersion {
  if (-not (Test-Command "java")) { return 0 }

  $prevEAP = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  try {
    $output = (& java -version 2>&1 | Out-String)
  } catch {
    $output = ""
  } finally {
    $ErrorActionPreference = $prevEAP
  }

  if ($output -match 'version\s+"(\d+)(?:\.(\d+))?') {
    $major = [int]$matches[1]
    if ($major -eq 1 -and $matches[2]) { return [int]$matches[2] }
    return $major
  }
  return 0
}

# Download with detailed error reporting.
# Returns $true on success, $false on failure. Prints response body if HTTP error.
function Invoke-Download($url, $outFile) {
  try {
    Invoke-WebRequest -Uri $url -OutFile $outFile -UseBasicParsing -ErrorAction Stop
    return $true
  } catch {
    Write-Host "  Request failed: $($_.Exception.Message)" -ForegroundColor Red
    try {
      $errResponse = $_.Exception.Response
      if ($null -ne $errResponse) {
        $stream = $errResponse.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        if ($body) {
          Write-Host "  Server response body:" -ForegroundColor Yellow
          Write-Host "  $body" -ForegroundColor Yellow
        }
      }
    } catch { }
    return $false
  }
}

# ----- 1. Check prerequisites -----
Write-Step "Checking prerequisites..."

$missing = @()
if (-not (Test-Command "node")) { $missing += "Node.js 20+   (https://nodejs.org/)" }
if (-not (Test-Command "pnpm")) { $missing += "pnpm          (run: corepack enable; corepack prepare pnpm@latest --activate)" }
if (-not (Test-Command "git"))  { $missing += "Git           (https://git-scm.com/)" }

$javaMajor = Get-JavaMajorVersion
if ($javaMajor -eq 0) {
  $missing += "Java 21 LTS   (https://adoptium.net/)"
} elseif ($javaMajor -lt 21) {
  $missing += "Java 21 LTS   (currently Java $javaMajor; need 21+. Install: winget install EclipseAdoptium.Temurin.21.JDK -s winget)"
}

if ($missing.Count -gt 0) {
  Write-Host ""
  Write-Host "Missing or wrong-version tools:" -ForegroundColor Red
  $missing | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
  Write-Host ""
  Write-Host "Install the missing tools, open a NEW PowerShell window, and run setup.ps1 again." -ForegroundColor Yellow
  exit 1
}

$nodeVer = node -v
$pnpmVer = pnpm -v
Write-Host "OK: node=$nodeVer, pnpm=$pnpmVer, java=$javaMajor"

# ----- 2. Create frontend (if missing) -----
Write-Step "Frontend (Next.js) - create if missing..."

if (Test-Path "frontend\package.json") {
  Write-Host "frontend/ already exists. Skipping create."
} else {
  pnpm create next-app frontend `
    --typescript `
    --tailwind `
    --app `
    --eslint `
    --src-dir `
    --import-alias "@/*" `
    --use-pnpm `
    --no-turbopack

  if ($LASTEXITCODE -ne 0) {
    Write-Host "Next.js create failed." -ForegroundColor Red
    exit 1
  }
  Write-Host "OK"
}

# ----- 3. Frontend additional deps (always, idempotent) -----
Write-Step "Ensuring frontend additional dependencies..."

if (-not (Test-Path "frontend\package.json")) {
  Write-Host "frontend/ not found. Cannot install additional deps." -ForegroundColor Red
  exit 1
}

Push-Location frontend
try {
  pnpm add pretendard "@tanstack/react-query" react-hook-form zod "@hookform/resolvers"
  if ($LASTEXITCODE -ne 0) { throw "pnpm add (runtime) failed." }

  pnpm add -D openapi-typescript
  if ($LASTEXITCODE -ne 0) { throw "pnpm add (dev) failed." }

  Write-Host "OK"
} finally {
  Pop-Location
}

# ----- 4. Create backend (if missing) -----
Write-Step "Backend (Spring Boot) - download if missing..."

if ((Test-Path "backend\build.gradle.kts") -or (Test-Path "backend\build.gradle")) {
  Write-Host "backend/ already exists. Skipping."
} else {
  # Dependency IDs verified against Spring Initializr metadata:
  # web, security, validation, postgresql, flyway, lombok, actuator
  # NOTE: spring-session-jdbc must be added manually to build.gradle.kts (uses Postgres for sessions).
  $deps = "web,security,validation,postgresql,flyway,lombok,actuator"
  $params = "type=gradle-project-kotlin" +
            "&language=java" +
            "&javaVersion=21" +
            "&groupId=com.agentsupport" +
            "&artifactId=backend" +
            "&name=backend" +
            "&packageName=com.agentsupport" +
            "&dependencies=$deps"

  $starterUrl = "https://start.spring.io/starter.zip?$params"
  $uiUrl = "https://start.spring.io/#!$params"

  Write-Host "Downloading starter from Spring Initializr..."
  Write-Host "  URL: $starterUrl" -ForegroundColor DarkGray

  $ok = Invoke-Download $starterUrl "starter.zip"
  if ($ok) {
    Write-Host "Extracting..."
    Expand-Archive -Path "starter.zip" -DestinationPath "backend"
    Remove-Item "starter.zip"
    Write-Host "OK"
  } else {
    Write-Host ""
    Write-Host "Auto-download failed. Use the web UI as fallback:" -ForegroundColor Yellow
    Write-Host "  1. Open in browser:" -ForegroundColor Yellow
    Write-Host "     $uiUrl" -ForegroundColor Cyan
    Write-Host "  2. Verify the form (Java 21, Gradle-Kotlin, deps listed below)" -ForegroundColor Yellow
    Write-Host "  3. Click 'Generate' -> downloads backend.zip" -ForegroundColor Yellow
    Write-Host "  4. Extract contents to D:\fieldarena2\backend\" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  Expected dependencies in UI:" -ForegroundColor Yellow
    $deps.Split(',') | ForEach-Object { Write-Host "    - $_" -ForegroundColor Yellow }
    Write-Host ""
    Write-Host "After manual download, re-run .\setup.ps1 to continue with .env setup." -ForegroundColor Yellow
    exit 1
  }
}

# ----- 5. Prepare .env -----
Write-Step "Preparing .env..."

if (Test-Path ".env") {
  Write-Host ".env already exists. Skipping."
} elseif (Test-Path ".env.example") {
  Copy-Item ".env.example" ".env"
  Write-Host ".env created from .env.example."
  Write-Host "NEXT: Edit .env with your Supabase credentials." -ForegroundColor Yellow
} else {
  Write-Host "WARNING: .env.example not found. Create .env manually." -ForegroundColor Yellow
}

# ----- Done -----
Write-Step "Bootstrap complete"
Write-Host ""
Write-Host "If you saw 'ERR_PNPM_IGNORED_BUILDS' (sharp, unrs-resolver) during install:" -ForegroundColor Yellow
Write-Host "  cd frontend; pnpm approve-builds" -ForegroundColor Yellow
Write-Host ""
Write-Host "Remember: spring-session-jdbc must be added manually to backend/build.gradle.kts:" -ForegroundColor Yellow
Write-Host "  implementation(\"org.springframework.session:spring-session-jdbc\")" -ForegroundColor Yellow
Write-Host "  (Sessions are stored in Supabase Postgres, not Redis.)" -ForegroundColor Yellow
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Green
Write-Host "  1. Sign up Supabase (https://supabase.com/) -> create free Postgres project (Seoul)"
Write-Host "  2. Edit .env with connection details"
Write-Host "  3. Install VS Code recommended extensions (Ctrl+Shift+P -> 'Show Recommended Extensions')"
Write-Host "  4. Terminal 1:  cd backend;  .\gradlew.bat bootRun"
Write-Host "  5. Terminal 2:  cd frontend; pnpm dev"
Write-Host "  6. First task:  point your dev LLM to docs/exec-plans/active/01-login-mvp.md"
Write-Host ""
