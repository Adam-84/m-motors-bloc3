param([switch]$SkipBuild)
$ErrorActionPreference='Stop'
$projectRoot=$PSScriptRoot
$portableRoot=Join-Path (Split-Path $projectRoot) '.tools'
$portableJdk=Get-ChildItem -LiteralPath $portableRoot -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($portableJdk) { $env:JAVA_HOME=$portableJdk.FullName }
$javaCommand=if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$mavenCommand=if (Test-Path (Join-Path $portableRoot 'apache-maven-3.9.11\bin\mvn.cmd')) { Join-Path $portableRoot 'apache-maven-3.9.11\bin\mvn.cmd' } else { 'mvn' }
if (-not $env:ADMIN_PASSWORD) { $env:ADMIN_PASSWORD='DemoAdmin2026!' }
if (-not $env:USER_PASSWORD) { $env:USER_PASSWORD='DemoClient2026!' }
$env:DEMO_MODE='true'
if (-not $SkipBuild) {
    Push-Location (Join-Path $projectRoot 'frontend')
    try { npm.cmd ci --ignore-scripts; if ($LASTEXITCODE) { throw 'Échec npm ci' }; npm.cmd run build; if ($LASTEXITCODE) { throw 'Échec build front' } } finally { Pop-Location }
    Push-Location (Join-Path $projectRoot 'backend')
    try {
        $mavenArgs=@('-B','-ntp','verify')
        if (Test-Path (Join-Path $portableRoot 'm2')) { $mavenArgs+=('-Dmaven.repo.local='+(Join-Path $portableRoot 'm2')) }
        & $mavenCommand @mavenArgs
        if ($LASTEXITCODE) { throw 'Échec tests backend' }
    } finally { Pop-Location }
}
Write-Host 'Démo locale : http://localhost:8080 - comptes : voir README.md'
Push-Location (Join-Path $projectRoot 'backend')
try { & $javaCommand -jar 'target\m-motors-1.0.0.jar' } finally { Pop-Location }
