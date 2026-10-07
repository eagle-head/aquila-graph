<#
.SYNOPSIS
Runs the repository build targets from PowerShell.

.DESCRIPTION
Provides the same build entry points as the repository Makefile while using the
Windows Maven wrapper. Maven remains responsible for the build lifecycle and
the script propagates its process exit code.

.PARAMETER Target
The build target to run. The default target is compile, matching the Makefile.

.PARAMETER MavenCommand
The Maven executable or wrapper to invoke. Defaults to mvnw.cmd in the
repository root.

.PARAMETER MavenFlags
Arguments included in every Maven invocation.

.PARAMETER SkipTestFlags
Arguments included in targets that skip test compilation and execution.

.EXAMPLE
.\Invoke-Build.ps1 compile

.EXAMPLE
.\Invoke-Build.ps1 test

.EXAMPLE
.\Invoke-Build.ps1 integration-test

.EXAMPLE
.\Invoke-Build.ps1 integration-test-neo4j

.EXAMPLE
.\Invoke-Build.ps1 coverage

.EXAMPLE
.\Invoke-Build.ps1 supply-chain-check
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('compile', 'test', 'integration-test', 'integration-test-neo4j', 'coverage', 'format', 'format-check', 'line-endings-check', 'quality', 'supply-chain-check')]
    [string] $Target = 'compile',

    [string] $MavenCommand,

    [string[]] $MavenFlags = @('-B', '--no-transfer-progress'),

    [string[]] $SkipTestFlags = @('-Dmaven.test.skip=true')
)

Set-StrictMode -Version Latest

if ($Target -in @('line-endings-check', 'quality')) {
    Push-Location -LiteralPath $PSScriptRoot
    try {
        $indexLines = @(& git ls-files --eol)
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to inspect Git index line endings.'
        }

        $violations = @($indexLines | Where-Object { $_ -match '^i/(crlf|mixed)\s' })
        if ($violations.Count -gt 0) {
            throw "Non-LF text in Git index:`n$($violations -join "`n")"
        }

        $stagedCarriageReturns = @(& git grep --cached -I -l "`r")
        if ($LASTEXITCODE -eq 0) {
            throw "Carriage returns in staged text:`n$($stagedCarriageReturns -join "`n")"
        }
        if ($LASTEXITCODE -ne 1) {
            throw 'Unable to inspect staged text for carriage returns.'
        }

        $commits = @(& git rev-list HEAD)
        if ($LASTEXITCODE -ne 0) {
            throw 'Unable to list reachable commits.'
        }

        foreach ($commit in $commits) {
            $matchingPaths = @(& git grep -I -l "`r" $commit)
            if ($LASTEXITCODE -eq 0) {
                throw "Non-LF text in commit ${commit}:`n$($matchingPaths -join "`n")"
            }
            if ($LASTEXITCODE -ne 1) {
                throw "Unable to inspect commit $commit for carriage returns."
            }
        }
        Write-Output 'LF-only Git text verified in the index and reachable commits.'
    }
    finally {
        Pop-Location
    }

    if ($Target -eq 'line-endings-check') {
        exit 0
    }
}

if ([string]::IsNullOrWhiteSpace($MavenCommand)) {
    $MavenCommand = Join-Path -Path $PSScriptRoot -ChildPath 'mvnw.cmd'
}

if (Test-Path -LiteralPath $MavenCommand -PathType Leaf) {
    $resolvedMavenCommand = (Resolve-Path -LiteralPath $MavenCommand).Path
}
else {
    $resolvedMavenCommand = (
        Get-Command -Name $MavenCommand -CommandType Application, ExternalScript -ErrorAction Stop |
            Select-Object -First 1
    ).Source
}

[string[]] $mavenArguments = switch ($Target) {
    'compile' {
        $MavenFlags + $SkipTestFlags + @('compile')
        break
    }
    'test' {
        $MavenFlags + @('test')
        break
    }
    'integration-test' {
        $MavenFlags + @('-DskipTests=true', 'verify')
        break
    }
    'integration-test-neo4j' {
        $MavenFlags + @('-DskipTests=true', '-Pneo4j-integration', 'verify')
        break
    }
    'coverage' {
        $MavenFlags + @('jacoco:prepare-agent', 'test', 'jacoco:report')
        break
    }
    'format' {
        $MavenFlags + @('spotless:apply')
        break
    }
    'format-check' {
        $MavenFlags + @('spotless:check')
        break
    }
    'quality' {
        $MavenFlags + $SkipTestFlags + @('verify')
        break
    }
    'supply-chain-check' {
        $MavenFlags + $SkipTestFlags + @('-Psupply-chain', 'verify')
        break
    }
}

Push-Location -LiteralPath $PSScriptRoot
try {
    $buildRepositoryArgument = '-Dmaven.repo.local=' + (Join-Path $PSScriptRoot 'target/maven-repository')
    & $resolvedMavenCommand -B --no-transfer-progress $buildRepositoryArgument -f tools/maven-pmd-plugin/pom.xml install
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
    & $resolvedMavenCommand $buildRepositoryArgument @mavenArguments
    $mavenExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

exit $mavenExitCode
