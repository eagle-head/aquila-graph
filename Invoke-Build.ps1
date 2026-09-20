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
.\Invoke-Build.ps1 coverage

.EXAMPLE
.\Invoke-Build.ps1 supply-chain-check
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('compile', 'test', 'coverage', 'format', 'format-check', 'quality', 'supply-chain-check')]
    [string] $Target = 'compile',

    [string] $MavenCommand,

    [string[]] $MavenFlags = @('-B', '--no-transfer-progress'),

    [string[]] $SkipTestFlags = @('-Dmaven.test.skip=true')
)

Set-StrictMode -Version Latest

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
    & $resolvedMavenCommand @mavenArguments
    $mavenExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

exit $mavenExitCode
