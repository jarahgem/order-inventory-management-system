$ErrorActionPreference = "Stop"

$wrapperJar = Join-Path $PSScriptRoot "gradle-wrapper.jar"
$temporaryJar = "$wrapperJar.tmp.$PID"
$defaultUrl = "https://raw.githubusercontent.com/gradle/gradle/v8.14.5/gradle/wrapper/gradle-wrapper.jar"
$wrapperUrl = if ($env:GRADLE_WRAPPER_JAR_URL) { $env:GRADLE_WRAPPER_JAR_URL } else { $defaultUrl }
$expectedChecksum = "7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172"

function Test-WrapperJar {
    if (-not (Test-Path -LiteralPath $wrapperJar -PathType Leaf)) {
        return $false
    }

    $actual = (Get-FileHash -LiteralPath $wrapperJar -Algorithm SHA256).Hash.ToLowerInvariant()
    return $actual -eq $expectedChecksum
}

if (-not (Test-WrapperJar)) {
    New-Item -ItemType Directory -Path $PSScriptRoot -Force | Out-Null
    Remove-Item -LiteralPath $temporaryJar -Force -ErrorAction SilentlyContinue

    try {
        Write-Host "Downloading the official Gradle 8.14.5 Wrapper JAR..."
        Invoke-WebRequest -Uri $wrapperUrl -OutFile $temporaryJar -UseBasicParsing

        $actual = (Get-FileHash -LiteralPath $temporaryJar -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $expectedChecksum) {
            throw "Gradle Wrapper JAR checksum verification failed. Expected $expectedChecksum; got $actual."
        }

        Move-Item -LiteralPath $temporaryJar -Destination $wrapperJar -Force
    }
    finally {
        Remove-Item -LiteralPath $temporaryJar -Force -ErrorAction SilentlyContinue
    }
}
