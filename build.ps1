# Build ProbeGuard with the toolchain installed on this machine.
# Paper 26.3 is compiled for Java 25, so javac 21 cannot read its classes.
$ErrorActionPreference = 'Stop'

$jdk = 'C:\Users\ADMIN\tools\jdk-25.0.5+8'
$maven = 'C:\Users\ADMIN\tools\apache-maven-3.9.9'

if (-not (Test-Path $jdk)) { throw "JDK 25 not found at $jdk (https://adoptium.net)" }
if (-not (Test-Path $maven)) { throw "Maven not found at $maven" }

$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$maven\bin;$env:Path"

Push-Location $PSScriptRoot
try {
    & mvn -B -DskipTests clean package
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed (exit $LASTEXITCODE)" }
    Write-Host ""
    Write-Host "Built: $PSScriptRoot\target\ProbeGuard-1.0.0.jar"
} finally {
    Pop-Location
}
