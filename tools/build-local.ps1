# CyberServer local-only build; no GitHub Actions or changes to main.
$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$logged = $false
$exitCode = 0

try {
    Start-Transcript -Path (Join-Path $root "build-local.log") -Force | Out-Null
    $logged = $true
    Write-Host "=== CyberServer Forge 1.20.1 / JDK 17 ==="

    if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
        throw "JDK 17 is not on PATH."
    }
    $compiler = (& javac -version | Out-String).Trim()
    if ($compiler -notmatch "^javac 17(\.|$)") {
        throw "CyberServer requires JDK 17; current compiler: $compiler"
    }
    if (-not $env:LOCALAPPDATA) {
        throw "LOCALAPPDATA is missing; use Windows PowerShell."
    }

    $cache = Join-Path $env:LOCALAPPDATA "CyberMods\Gradle"
    New-Item -ItemType Directory -Path $cache -Force | Out-Null
    $version = "8.8"
    $gradle = Join-Path $cache "gradle-$version\bin\gradle.bat"
    if (-not (Test-Path -LiteralPath $gradle)) {
        $zipName = "gradle-$version-bin.zip"
        $url = "https://services.gradle.org/distributions/$zipName"
        $archive = Join-Path $cache $zipName
        $sha = Join-Path $cache "$zipName.sha256"
        Invoke-WebRequest -Uri ($url + ".sha256") -OutFile $sha -UseBasicParsing
        $expected = [regex]::Match((Get-Content -LiteralPath $sha -Raw), "[a-fA-F0-9]{64}")
        if (-not $expected.Success) {
            throw "Could not read Gradle SHA-256 checksum."
        }
        if (-not (Test-Path -LiteralPath $archive)) {
            Invoke-WebRequest -Uri $url -OutFile $archive -UseBasicParsing
        }
        if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected.Value.ToUpperInvariant()) {
            Remove-Item -LiteralPath $archive -Force -ErrorAction SilentlyContinue
            throw "Gradle download checksum mismatch."
        }
        Expand-Archive -LiteralPath $archive -DestinationPath $cache -Force
        if (-not (Test-Path -LiteralPath $gradle)) {
            throw "Gradle extraction failed."
        }
    }

    Push-Location $root
    try {
        & $gradle clean build --no-daemon --stacktrace
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed. Inspect build-local.log."
        }
    } finally {
        Pop-Location
    }

    $libs = Join-Path $root "build\libs"
    $files = @(Get-ChildItem -Path $libs -Filter "CyberServer-*.jar" -File | Where-Object {
        $_.Name -notmatch "-(sources|javadoc|dev|plain)\.jar$"
    })
    if ($files.Count -ne 1) {
        throw "Expected one compiled mod jar; found $($files.Count)."
    }
    Copy-Item -LiteralPath $files[0].FullName -Destination (Join-Path $root "CyberServer.jar") -Force
    Write-Host "SUCCESS: CyberServer.jar"
} catch {
    Write-Host "BUILD FAILED: $($_.Exception.Message)" -ForegroundColor Red
    $exitCode = 1
} finally {
    if ($logged) { Stop-Transcript | Out-Null }
}
exit $exitCode
