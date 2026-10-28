<#
.SYNOPSIS
Runs one local build operation through the shared JDK 25 execution owner.
.PARAMETER Target
The operation to run; compile is the default.
test runs development tests; coverage runs the same scope with unit coverage.
Unit selectors use explicit *Test class names, optionally followed by #method.
quality runs static gates without tests.
Real-environment integration commands are deferred until a new explicit user decision.
check-process, check-persistence and check-lifecycle are manual integration commands.
prepare-consumers packages and prepares the independent query module without running tests.
verify-all and integration-test-neo4j include real integrations; they are not development acceptance commands.
.PARAMETER ProjectRoot
The canonical project to build; the script directory is the default.
.PARAMETER MavenCommand
An explicitly selected Maven executable or wrapper; its version remains enforced.
.PARAMETER MavenFlags
Literal supported Maven logging, offline, profile, and operation-specific flags.
.PARAMETER SkipTestFlags
The existing test-skip setting, validated by the shared operation owner.
.EXAMPLE
.\Invoke-Build.ps1 coverage
.EXAMPLE
.\Invoke-Build.ps1 test -MavenFlags @('-B', '--no-transfer-progress', '-Dtest=BuildRequestTest')
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string] $Target = 'compile',
    [string] $ProjectRoot = $PSScriptRoot,
    [string] $MavenCommand,
    [string[]] $MavenFlags = @('-B', '--no-transfer-progress'),
    [string[]] $SkipTestFlags = @('-Dmaven.test.skip=true')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$buildArguments = @('--root', $ProjectRoot, '--tool-root', $PSScriptRoot)
if (-not [string]::IsNullOrWhiteSpace($MavenCommand)) {
    $buildArguments += @('--maven', $MavenCommand)
}
foreach ($flag in $SkipTestFlags) {
    $buildArguments += @('--skip-test-flag', $flag)
}
$buildArguments += @($Target) + $MavenFlags
$argumentPrefix = 'AQUILA_BUILD_ARGUMENT_'
$previousArguments = @{}
foreach ($name in [Environment]::GetEnvironmentVariables().Keys) {
    if ([string]$name -like ($argumentPrefix + '*')) {
        $previousArguments[$name] = [Environment]::GetEnvironmentVariable($name)
    }
}
try {
    foreach ($name in $previousArguments.Keys) {
        [Environment]::SetEnvironmentVariable($name, $null)
    }
    [Environment]::SetEnvironmentVariable($argumentPrefix + 'COUNT', [string]$buildArguments.Count)
    for ($index = 0; $index -lt $buildArguments.Count; $index++) {
        $units = ([string]$buildArguments[$index]).ToCharArray()
        $bytes = [byte[]]::new($units.Length * 2)
        [Buffer]::BlockCopy($units, 0, $bytes, 0, $bytes.Length)
        $encoded = 'u16:' + [Convert]::ToBase64String($bytes)
        [Environment]::SetEnvironmentVariable($argumentPrefix + $index, $encoded)
    }
    & java --enable-native-access=ALL-UNNAMED (Join-Path $PSScriptRoot 'config/build/org/aquilagraph/build/BuildExecution.java') --adapter-environment
    $buildExitCode = $LASTEXITCODE
}
finally {
    foreach ($name in @([Environment]::GetEnvironmentVariables().Keys)) {
        if ([string]$name -like ($argumentPrefix + '*')) {
            [Environment]::SetEnvironmentVariable($name, $null)
        }
    }
    foreach ($name in $previousArguments.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousArguments[$name])
    }
}
exit $buildExitCode
