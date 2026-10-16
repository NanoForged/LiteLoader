<#
.SYNOPSIS
    Manages the LaunchRedirector bootstrap agent in the game's vmparams file.

.DESCRIPTION
    This mod hooks the game by attaching a java agent to the FIRST JVM, the one that starts
    com.fs.starfarer.StarfarerLauncher. Starsector takes that JVM's command line from the
    "vmparams" file, which always lives in the folder that contains "mods", one level above
    this mod. The agent to attach is the LaunchRedirector.jar shipped in the "bootstrap"
    folder next to this script, and it must be passed as -javaagent:<path>. The build
    normalizes that file name, so the ref it was resolved from is recorded in
    LaunchRedirector.version beside it.

    The path written into vmparams is RELATIVE (..\mods\<mod>\bootstrap\<jar>) on purpose:
    the game already resolves its own -classpath, -Djava.library.path and the saves / mods
    settings relative to the folder it starts in (starsector-core), and a relative path
    cannot contain the spaces that an absolute install path may contain, which this
    single-line, unquoted file cannot represent.

    status      reports the bundled agent, what vmparams currently points at, whether that
                path still exists, which version it is, and the verdict with its reason.
                It never writes.
    install     attaches this mod's agent, but refuses to downgrade: when the installed
                agent is a comparable version greater than or equal to the bundled one,
                nothing is written. Every other state - not installed, pointing at a file
                that was deleted without uninstalling, an older version, an unorderable
                ref, or several agents at once - is repaired. -Force overrides the refusal.
    uninstall   removes every LaunchRedirector agent and leaves the rest of the line alone.
                -Restore puts the pristine vmparams.bak back instead.
    help        prints the usage summary and exits. Running the script with no argument at
                all runs status, and says so first.

    Version comparison
      Only 1, 1.2 and 1.2.3 style versions, with an optional -suffix, are comparable.
      Segments are compared numerically; when the numbers tie, the version without a
      suffix wins, then the suffix decides. A commit ref such as aeb1f171c7 is NOT
      comparable - a hash has no ordering - so the comparison is skipped and the bundled
      agent is installed. A version that cannot be read at all is treated the same way.

    The first time this script changes vmparams it copies the current file to vmparams.bak,
    so -Restore puts back the state that existed before this script ever touched it.

    Only the Windows launcher is covered here: starsector.exe reads vmparams, while the
    Unix-like launchers are shell scripts that build the JVM command line themselves and
    are edited directly.

.EXAMPLE
    .\bootstrap-agent.ps1 status
.EXAMPLE
    .\bootstrap-agent.ps1 install
.EXAMPLE
    .\bootstrap-agent.ps1 install -Force
.EXAMPLE
    .\bootstrap-agent.ps1 uninstall -Restore
.EXAMPLE
    .\bootstrap-agent.ps1 help
#>
# Nothing may precede the help block above, not a shebang and not a #Requires directive:
# either one stops PowerShell from treating it as comment based help, and Get-Help falls
# back to the bare parameter syntax. So the version requirement lives down here instead,
# and the script has no #! line - it is driven through pwsh -File or by double click.
#Requires -Version 5.1
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('install', 'uninstall', 'status', 'help')]
    [string]$Command = 'status',

    # Override the vmparams path. Defaults to the folder above "mods".
    [string]$VmParams,

    # With uninstall: restore the pristine vmparams.bak instead of editing in place.
    [switch]$Restore,

    # With install: attach the bundled agent even when the installed one is not older.
    [switch]$Force,

    # Print the usage summary and exit. (-? is taken by PowerShell itself.)
    [Alias('h')]
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Say  { param([string]$Message) Write-Host $Message }
function Warn { param([string]$Message) Write-Host "warning: $Message" -ForegroundColor Yellow }
function Die  { param([string]$Message) Write-Host "error: $Message" -ForegroundColor Red; exit 1 }

function Show-Usage {
    Say 'bootstrap-agent.ps1 - manage the LaunchRedirector bootstrap agent in the game''s vmparams'
    Say ''
    Say 'usage'
    Say '  .\bootstrap-agent.ps1 [install|uninstall|status|help] [options]'
    Say ''
    Say 'commands'
    Say '  status      report the bundled agent, the installed one, the verdict and why (default)'
    Say '  install     attach this mod''s agent unless the installed one is not older'
    Say '  uninstall   remove every LaunchRedirector agent'
    Say '  help        print this message'
    Say ''
    Say 'options'
    Say '  -VmParams <path>   override the vmparams path (default: the folder above "mods")'
    Say '  -Force             with install: attach even when the installed one is not older'
    Say '  -Restore           with uninstall: put vmparams.bak back as it was before'
    Say '  -Help              print this message'
    Say ''
    Say 'examples'
    Say '  .\bootstrap-agent.ps1 status'
    Say '  .\bootstrap-agent.ps1 install'
    Say '  .\bootstrap-agent.ps1 install -Force'
    Say '  .\bootstrap-agent.ps1 uninstall -Restore'
    Say ''
    Say 'The agent is bootstrap\LaunchRedirector.jar; its version is read from the'
    Say 'LaunchRedirector.version file beside it. Only 1.2.3 style versions are comparable,'
    Say 'so a commit ref skips the version check and the bundled agent wins.'
    Say ''
    Say 'Get-Help .\bootstrap-agent.ps1 -Full prints the long description.'
}

# A bare invocation is the common case for a shipped mod, so say what it is doing rather
# than silently reporting. help is answered before anything on disk is looked at.
$noArguments = ($PSBoundParameters.Count -eq 0)

if ($Help -or $Command -eq 'help') {
    Show-Usage
    exit 0
}

# ------------------------------------------------------------ version helpers

# -1 / 0 / 1, comparing the numeric value of two digit strings without parsing them
function Compare-NumericSegment {
    param([string]$Left, [string]$Right)
    $a = $Left.TrimStart('0');  if ($a -eq '') { $a = '0' }
    $b = $Right.TrimStart('0'); if ($b -eq '') { $b = '0' }
    if ($a.Length -ne $b.Length) { return [Math]::Sign($a.Length - $b.Length) }
    return [Math]::Sign([string]::CompareOrdinal($a, $b))
}

function Test-ComparableVersion {
    param([string]$Version)
    if ([string]::IsNullOrEmpty($Version)) { return $false }
    return $Version -match '^[vV]?[0-9]+(\.[0-9]+)*([-+][0-9A-Za-z][0-9A-Za-z.-]*)?$'
}

function Test-CommitRef {
    param([string]$Version)
    if ([string]::IsNullOrEmpty($Version)) { return $false }
    if ($Version -match '^[0-9]+$') { return $false }
    return $Version -match '^[0-9a-fA-F]{7,40}$'
}

# -1 / 0 / 1; both operands must satisfy Test-ComparableVersion
function Compare-Version {
    param([string]$Left, [string]$Right)
    $l = $Left  -replace '^[vV]', ''
    $r = $Right -replace '^[vV]', ''
    $lm = [regex]::Match($l, '^([0-9]+(?:\.[0-9]+)*)(?:([-+].*))?$')
    $rm = [regex]::Match($r, '^([0-9]+(?:\.[0-9]+)*)(?:([-+].*))?$')
    $ln = $lm.Groups[1].Value.Split('.')
    $rn = $rm.Groups[1].Value.Split('.')
    $count = [Math]::Max($ln.Count, $rn.Count)
    for ($i = 0; $i -lt $count; $i++) {
        $a = '0'
        $b = '0'
        if ($i -lt $ln.Count) { $a = $ln[$i] }
        if ($i -lt $rn.Count) { $b = $rn[$i] }
        $cmp = Compare-NumericSegment $a $b
        if ($cmp -ne 0) { return $cmp }
    }
    $ls = $lm.Groups[2].Value
    $rs = $rm.Groups[2].Value
    if ($ls -eq $rs) { return 0 }
    if ($ls -eq '') { return 1 }      # 1.2.3 beats 1.2.3-rc1
    if ($rs -eq '') { return -1 }
    return [Math]::Sign([string]::CompareOrdinal($ls, $rs))
}

# an empty or unreadable version file means "unknown", never a crash
function Read-VersionFile {
    param([string]$Path)
    if ([string]::IsNullOrEmpty($Path)) { return '' }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return '' }
    $text = Get-Content -LiteralPath $Path -Raw -ErrorAction SilentlyContinue
    if ($null -eq $text) { return '' }
    return $text.Trim()
}

function Format-Version {
    param([string]$Version)
    if ([string]::IsNullOrEmpty($Version)) { return 'unknown' }
    if (Test-ComparableVersion $Version) { return $Version }
    if (Test-CommitRef $Version) { return $Version + ' (commit ref, not comparable)' }
    return $Version + ' (not comparable)'
}

# ------------------------------------------------------------ locate things

$scriptFile = $MyInvocation.MyCommand.Path
if ([string]::IsNullOrEmpty($scriptFile) -or -not (Test-Path -LiteralPath $scriptFile -PathType Leaf)) {
    Die 'cannot locate this script on disk; run it from a file (.\bootstrap-agent.ps1 install)'
}

$modRoot  = Split-Path -Parent (Get-Item -LiteralPath $scriptFile).FullName
$modsDir  = Split-Path -Parent $modRoot
$gameRoot = Split-Path -Parent $modsDir
$modId    = Split-Path -Leaf $modRoot
$coreDir  = Join-Path $gameRoot 'starsector-core'

if ((Split-Path -Leaf $modsDir) -ne 'mods') {
    Warn "this script is not inside a folder named 'mods' (found '$modsDir'); the derived game root is '$gameRoot'"
}

if ([string]::IsNullOrWhiteSpace($VmParams)) {
    $VmParams = Join-Path $gameRoot 'vmparams'
}
if (-not (Test-Path -LiteralPath $VmParams -PathType Leaf)) {
    Die "vmparams not found at '$VmParams'; it is expected next to the 'mods' folder"
}
$VmParams   = (Get-Item -LiteralPath $VmParams).FullName
$backupPath = $VmParams + '.bak'

$bootstrapDir = Join-Path $modRoot 'bootstrap'
if (-not (Test-Path -LiteralPath $bootstrapDir -PathType Container)) {
    Die "no 'bootstrap' folder next to this script (expected $bootstrapDir)"
}

# the build stages the agent under a stable name; anything else is a hand-dropped build,
# so fall back to the same globs the older layout used
$jarFile = $null
$normalizedJar = Join-Path $bootstrapDir 'LaunchRedirector.jar'
if (Test-Path -LiteralPath $normalizedJar -PathType Leaf) {
    $jarFile = Get-Item -LiteralPath $normalizedJar
} else {
    $candidates = @(Get-ChildItem -LiteralPath $bootstrapDir -File -Filter 'LaunchRedirector*.jar' |
            Sort-Object -Property LastWriteTime -Descending)
    if ($candidates.Count -eq 0) {
        Die "no LaunchRedirector*.jar inside $bootstrapDir"
    }
    foreach ($candidate in $candidates) {
        if ($candidate.Name -like '*-all.jar') { $jarFile = $candidate; break }
    }
    if ($null -eq $jarFile) { $jarFile = $candidates[0] }
    if ($candidates.Count -gt 1) {
        Warn "several LaunchRedirector jars in bootstrap; using $($jarFile.Name)"
    }
}

# the ref the build renamed away out of the file name
$bundledVersionFile = [System.IO.Path]::ChangeExtension($jarFile.FullName, '.version')
$bundledVersion     = Read-VersionFile $bundledVersionFile

# Relative to the folder the game starts its JVM in, which is also the folder the game's own
# relative -classpath entries are resolved against.
$relativeJar = '..\mods\' + $modId + '\bootstrap\' + $jarFile.Name
if (Test-Path -LiteralPath $coreDir -PathType Container) {
    Push-Location -LiteralPath $coreDir
    try {
        $fromDisk = (Resolve-Path -LiteralPath $jarFile.FullName -Relative -ErrorAction Stop).ToString()
        if (-not [string]::IsNullOrEmpty($fromDisk)) { $relativeJar = $fromDisk }
    } catch {
        # keep the constructed path
    } finally {
        Pop-Location
    }
    if (-not (Test-Path -LiteralPath (Join-Path $coreDir $relativeJar) -PathType Leaf)) {
        Warn "'$relativeJar' does not resolve to a file from '$coreDir'; the game may fail to attach the agent"
    }
} else {
    Warn "no 'starsector-core' folder in '$gameRoot'; assuming the game starts its JVM there"
}

$agentArg = '-javaagent:' + $relativeJar

# ------------------------------------------------------------------ read vmparams

$raw = [System.IO.File]::ReadAllText($VmParams)
$eol = ''
if ($raw -match '(\r?\n)$') {
    $eol  = $Matches[1]
    $body = $raw.Substring(0, $raw.Length - $eol.Length)
} else {
    $body = $raw
}

# One token, surrounded by whitespace: the pattern cannot match a longer token that merely
# contains this text.
$anyAgentPattern   = '[ \t]+-javaagent:\S*LaunchRedirector\S*\.jar'
$firstTokenPattern = '^(\S+)[ \t]+'

$existingAgents = @([regex]::Matches($body, '-javaagent:\S*LaunchRedirector\S*\.jar') |
        ForEach-Object { $_.Value })

# ------------------------------------------------- what is installed right now

$installedToken   = ''
if ($existingAgents.Count -ge 1) { $installedToken = $existingAgents[0] }

$installedPath    = ''
$installedExists  = $false
$installedVersion = ''
if (-not [string]::IsNullOrEmpty($installedToken)) {
    $rawPath = $installedToken.Substring('-javaagent:'.Length)
    try {
        if ([System.IO.Path]::IsPathRooted($rawPath)) {
            $installedPath = [System.IO.Path]::GetFullPath($rawPath)
        } else {
            $installedPath = [System.IO.Path]::GetFullPath((Join-Path $coreDir $rawPath))
        }
    } catch {
        $installedPath = $rawPath
    }
    $installedExists = Test-Path -LiteralPath $installedPath -PathType Leaf
    if ($installedExists) {
        $installedVersionFile = [System.IO.Path]::ChangeExtension($installedPath, '.version')
        $installedVersion     = Read-VersionFile $installedVersionFile
    }
}

# ---------------------------------------------------------------- the verdict

$verdict = 'install'
$reason  = ''
if ($existingAgents.Count -eq 0) {
    $reason = 'no LaunchRedirector agent in vmparams'
} elseif ($existingAgents.Count -gt 1) {
    $reason = "$($existingAgents.Count) LaunchRedirector agents present, normalizing to one"
} elseif (-not $installedExists) {
    $reason = 'the installed agent points at a file that no longer exists'
} elseif ((Test-ComparableVersion $bundledVersion) -and (Test-ComparableVersion $installedVersion)) {
    $cmp = Compare-Version $installedVersion $bundledVersion
    if ($cmp -lt 0) {
        $reason = "installed $installedVersion is older than the bundled $bundledVersion"
    } else {
        $verdict = 'skip'
        $reason  = "installed $installedVersion >= bundled $bundledVersion"
    }
} else {
    $reason = 'versions are not comparable, so the bundled agent wins'
}

function New-Backup {
    if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
        Copy-Item -LiteralPath $VmParams -Destination $backupPath
        Say "backup      : created $backupPath"
    } else {
        Say "backup      : kept existing $backupPath"
    }
}

function Save-VmParams {
    param([string]$NewBody)
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($VmParams, $NewBody + $eol, $utf8NoBom)
}

# ----------------------------------------------------------------------- report

if ($noArguments) {
    Say 'no command given - running the default "status". Use "help" for usage.'
    Say ''
}

Say "mod root    : $modRoot"
Say "game root   : $gameRoot"
Say "vmparams    : $VmParams"
if (Test-Path -LiteralPath $backupPath -PathType Leaf) {
    Say "backup      : $backupPath"
} else {
    Say 'backup      : none yet'
}
Say "bundled jar : $($jarFile.FullName)"
Say "bundled ver : $(Format-Version $bundledVersion)"
Say "agent arg   : $agentArg"
Say "agent count : $($existingAgents.Count)"
if ($existingAgents.Count -eq 0) {
    Say 'agent state : not installed'
} else {
    foreach ($agent in $existingAgents) {
        Say "agent token : $agent"
    }
    Say "agent path  : $installedPath"
    if ($installedExists) {
        Say 'agent file  : present'
    } else {
        Say 'agent file  : MISSING'
    }
    Say "agent ver   : $(Format-Version $installedVersion)"
    if (($installedToken -eq $agentArg) -and [string]::IsNullOrEmpty($installedVersion)) {
        Warn "$($bundledVersionFile) is missing; the build normally writes it next to the jar"
    }
}
Say "verdict     : $verdict"
Say "reason      : $reason"

if ($existingAgents.Count -gt 0 -and -not $installedExists) {
    Warn 'the installed agent points at a path that does not exist - the file was probably'
    Warn 'deleted without uninstalling; install repairs this, uninstall only edits vmparams'
}

if ($Command -eq 'status') { exit 0 }

Say ''

# ---------------------------------------------------------------- install / remove

switch ($Command) {
    'install' {
        if (($verdict -eq 'skip') -and (-not $Force)) {
            Say "skipped     : not downgrading, $reason"
            Say '              use -Force to install the bundled agent anyway'
            exit 0
        }
        if (($verdict -eq 'skip') -and $Force) {
            Say "forced      : $reason"
        } elseif ($Force) {
            Say "forced      : -Force given (would have installed anyway: $reason)"
        }

        $newBody = [regex]::Replace($body, $anyAgentPattern, '')
        $newBody = [regex]::Replace($newBody, $firstTokenPattern, ('$1 ' + $agentArg + ' '))

        if ($newBody -eq $body) {
            Say 'nothing to do: vmparams already carries exactly this agent'
            exit 0
        }

        New-Backup
        Save-VmParams -NewBody $newBody
        Say "installed   : added $agentArg"
        if ($existingAgents.Count -gt 0) {
            Say ('replaced    : ' + ($existingAgents -join ', '))
        }
        Say ''
        Say 'Start the game through the launcher that reads vmparams (starsector.exe on'
        Say 'Windows). starsector.bat / starsector.command hardcode their own command line'
        Say 'and never read vmparams.'
    }
    'uninstall' {
        if ($Restore) {
            if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
                Die "no backup at '$backupPath' to restore"
            }
            Copy-Item -LiteralPath $backupPath -Destination $VmParams -Force
            Say "restored    : $VmParams <- $backupPath"
            exit 0
        }

        if ($existingAgents.Count -eq 0) {
            Say 'nothing to do: vmparams carries no LaunchRedirector agent'
            exit 0
        }

        New-Backup
        Save-VmParams -NewBody ([regex]::Replace($body, $anyAgentPattern, ''))
        Say ('removed     : ' + ($existingAgents -join ', '))
        if ($existingAgents.Count -eq 1) {
            Say "              the agent file itself is left alone: $installedPath"
        }
    }
}
