[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleUserHome = $env:GRADLE_USER_HOME
)

$ErrorActionPreference = 'Stop'
$previousJavaHome = $env:JAVA_HOME
$previousGradleUserHome = $env:GRADLE_USER_HOME
$buildExitCode = 1
Push-Location -LiteralPath $PSScriptRoot
try {
    $gradleArgs = @('test', 'build', '--no-daemon', '--console=plain')
    if ($JavaHome) {
        $javaExecutable = Join-Path $JavaHome 'bin/java.exe'
        if (-not (Test-Path -LiteralPath $javaExecutable)) {
            throw "JDK not found: $javaExecutable"
        }
        $env:JAVA_HOME = $JavaHome
        $gradleArgs += "-Porg.gradle.java.installations.paths=$($JavaHome.Replace('\', '/'))"
    }
    if ($GradleUserHome) { $env:GRADLE_USER_HOME = $GradleUserHome }
    & .\gradlew.bat @gradleArgs
    $buildExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
    $env:GRADLE_USER_HOME = $previousGradleUserHome
}
exit $buildExitCode
