[CmdletBinding()]
param(
    [ValidateSet('Status', 'ListJava', 'Prepare', 'StartJfr', 'StopJfr')]
    [string]$Action = 'Status',

    [Parameter(Mandatory = $true)]
    [string]$InstancePath,

    [ValidateSet('ETF', 'ETF_C2ME', 'ETF_RTF', 'ETF_RTF_C2ME')]
    [string]$Combination = 'ETF',

    [string]$SourceJar = '',

    [int]$ProcessId = 0,

    [string]$RecordingName = 'ETF-P47',

    [ValidateRange(64, 2048)]
    [int]$MaxSizeMb = 512
)

Set-StrictMode -Version Latest

if ([string]::IsNullOrEmpty($SourceJar)) {
    $scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
    $SourceJar = Join-Path $scriptRoot '..\neoforge\build\libs\endterraforged-0.1.7.jar'
}
$ErrorActionPreference = 'Stop'

function Resolve-RequiredDirectory {
    param([string]$Path, [string]$Name)

    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        throw "$Name directory does not exist: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).ProviderPath
}

function Get-SingleToggleFile {
    param([string]$ModsPath, [string]$Pattern, [string]$Name)

    $matches = @(Get-ChildItem -LiteralPath $ModsPath -File | Where-Object {
        $_.Name -like $Pattern -and $_.Name -match '\.jar(?:\.disabled)?$'
    })
    if ($matches.Count -ne 1) {
        $found = ($matches | Select-Object -ExpandProperty Name) -join ', '
        throw "Expected exactly one $Name jar, found $($matches.Count): $found"
    }
    return $matches[0]
}

function Test-ModEnabled {
    param([System.IO.FileInfo]$File)

    return -not $File.Name.EndsWith('.disabled', [System.StringComparison]::OrdinalIgnoreCase)
}

function Set-ModEnabled {
    param([System.IO.FileInfo]$File, [bool]$Enabled)

    $currentlyEnabled = Test-ModEnabled $File
    if ($currentlyEnabled -eq $Enabled) {
        return $File
    }

    if ($Enabled) {
        $targetName = $File.Name.Substring(0, $File.Name.Length - '.disabled'.Length)
    } else {
        $targetName = $File.Name + '.disabled'
    }
    $target = Join-Path $File.DirectoryName $targetName
    if (Test-Path -LiteralPath $target) {
        throw "Cannot toggle $($File.Name); target already exists: $targetName"
    }
    Move-Item -LiteralPath $File.FullName -Destination $target
    return Get-Item -LiteralPath $target
}

function Assert-ClientStopped {
    $visibleClients = @(Get-Process -Name java, javaw -ErrorAction SilentlyContinue | Where-Object {
        $_.MainWindowTitle -match 'Minecraft|NeoForge'
    })
    if ($visibleClients.Count -gt 0) {
        $ids = ($visibleClients | Select-Object -ExpandProperty Id) -join ', '
        throw "Close the Minecraft client before changing jars or mod states. Visible Java PID(s): $ids"
    }
}

function Get-InstanceState {
    param([string]$ResolvedInstancePath)

    $modsPath = Resolve-RequiredDirectory (Join-Path $ResolvedInstancePath 'mods') 'mods'
    $etfJar = Join-Path $modsPath 'endterraforged-0.1.7.jar'
    if (-not (Test-Path -LiteralPath $etfJar -PathType Leaf)) {
        throw "ETF jar does not exist: $etfJar"
    }
    $rtf = Get-SingleToggleFile $modsPath 'reterraforged-*.jar*' 'RTF'
    $c2me = Get-SingleToggleFile $modsPath '*c2me-neoforge-mc1.21.1-*.jar*' 'C2ME'
    $rtfEnabled = Test-ModEnabled $rtf
    $c2meEnabled = Test-ModEnabled $c2me
    if ($rtfEnabled -and $c2meEnabled) {
        $actualCombination = 'ETF_RTF_C2ME'
    } elseif ($rtfEnabled) {
        $actualCombination = 'ETF_RTF'
    } elseif ($c2meEnabled) {
        $actualCombination = 'ETF_C2ME'
    } else {
        $actualCombination = 'ETF'
    }

    return [PSCustomObject]@{
        InstancePath = $ResolvedInstancePath
        ModsPath = $modsPath
        Combination = $actualCombination
        EtfJar = $etfJar
        EtfSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $etfJar).Hash
        RtfFile = $rtf.FullName
        RtfEnabled = $rtfEnabled
        C2meFile = $c2me.FullName
        C2meEnabled = $c2meEnabled
    }
}

function Write-State {
    param([object]$State)

    [PSCustomObject]@{
        Combination = $State.Combination
        EtfSha256 = $State.EtfSha256
        RtfEnabled = $State.RtfEnabled
        RtfFile = [System.IO.Path]::GetFileName($State.RtfFile)
        C2meEnabled = $State.C2meEnabled
        C2meFile = [System.IO.Path]::GetFileName($State.C2meFile)
    } | Format-List
}

function Sync-EtfJar {
    param([string]$Source, [string]$Target)

    $resolvedSource = (Resolve-Path -LiteralPath $Source).ProviderPath
    Copy-Item -LiteralPath $resolvedSource -Destination $Target -Force
    $sourceHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedSource).Hash
    $targetHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $Target).Hash
    if ($sourceHash -ne $targetHash) {
        throw "ETF jar copy verification failed: source=$sourceHash target=$targetHash"
    }
    return $targetHash
}

function Set-Combination {
    param([string]$ResolvedInstancePath, [string]$RequestedCombination)

    $state = Get-InstanceState $ResolvedInstancePath
    $rtf = Get-Item -LiteralPath $state.RtfFile
    $c2me = Get-Item -LiteralPath $state.C2meFile
    $enableRtf = $RequestedCombination -eq 'ETF_RTF' -or $RequestedCombination -eq 'ETF_RTF_C2ME'
    $enableC2me = $RequestedCombination -eq 'ETF_C2ME' -or $RequestedCombination -eq 'ETF_RTF_C2ME'
    Set-ModEnabled $rtf $enableRtf | Out-Null
    Set-ModEnabled $c2me $enableC2me | Out-Null

    $updated = Get-InstanceState $ResolvedInstancePath
    if ($updated.Combination -ne $RequestedCombination) {
        throw "Combination switch failed: requested=$RequestedCombination actual=$($updated.Combination)"
    }
    return $updated
}

function Get-JavaProcesses {
    return @(Get-Process -Name java, javaw -ErrorAction SilentlyContinue | ForEach-Object {
        [PSCustomObject]@{
            ProcessId = $_.Id
            ProcessName = $_.ProcessName
            JavaPath = $_.Path
            StartTime = $_.StartTime
            WorkingSetMb = [Math]::Round($_.WorkingSet64 / 1MB, 1)
            WindowTitle = $_.MainWindowTitle
        }
    })
}

function Resolve-Jcmd {
    param([int]$TargetProcessId)

    $process = Get-Process -Id $TargetProcessId -ErrorAction Stop
    $nextToJava = Join-Path (Split-Path -Parent $process.Path) 'jcmd.exe'
    if (Test-Path -LiteralPath $nextToJava -PathType Leaf) {
        return $nextToJava
    }
    if ($env:JAVA_HOME) {
        $fromJavaHome = Join-Path $env:JAVA_HOME 'bin\jcmd.exe'
        if (Test-Path -LiteralPath $fromJavaHome -PathType Leaf) {
            return $fromJavaHome
        }
    }
    throw "jcmd.exe was not found next to PID $TargetProcessId Java or under JAVA_HOME"
}

function Invoke-Jcmd {
    param([string]$Jcmd, [string[]]$Arguments)

    $output = & $Jcmd @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "jcmd failed with exit code $LASTEXITCODE`n$($output -join [Environment]::NewLine)"
    }
    $output | Write-Output
}

function Get-SessionRoot {
    param([string]$ResolvedInstancePath)

    $root = Join-Path $ResolvedInstancePath 'logs\p47-0b'
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    return $root
}

function Write-Manifest {
    param([string]$Path, [object]$State, [int]$TargetProcessId, [string]$JavaPath)

    $lines = @(
        "started_utc=$([DateTime]::UtcNow.ToString('o'))",
        "combination=$($State.Combination)",
        "etf_sha256=$($State.EtfSha256)",
        "rtf_enabled=$($State.RtfEnabled)",
        "rtf_file=$([System.IO.Path]::GetFileName($State.RtfFile))",
        "c2me_enabled=$($State.C2meEnabled)",
        "c2me_file=$([System.IO.Path]::GetFileName($State.C2meFile))",
        "process_id=$TargetProcessId",
        "java_path=$JavaPath"
    )
    Set-Content -LiteralPath $Path -Value $lines -Encoding utf8
}

function Write-LogSummary {
    param([string]$LatestLog, [string]$SummaryPath)

    $content = Get-Content -LiteralPath $LatestLog -Raw -Encoding utf8
    $markers = @(
        'topology=OUTER_CONTINENTS',
        'seaMode=WITH_FLOOR',
        'terrainLayout=REGION_PLANNED',
        'archipelago=true'
    )
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("latest_log=$LatestLog")
    foreach ($marker in $markers) {
        $lines.Add("marker[$marker]=$($content.Contains($marker))")
    }
    $checks = [ordered]@{
        'injection_error' = 'InjectionError'
        'redirect_conflict' = 'Redirect conflict'
        'out_of_memory' = 'OutOfMemoryError'
        'cant_keep_up' = "Can't keep up!"
        'c2me_delegate' = 'DelegateNode'
    }
    foreach ($entry in $checks.GetEnumerator()) {
        $count = [regex]::Matches($content, [regex]::Escape($entry.Value)).Count
        $lines.Add("count[$($entry.Key)]=$count")
    }
    Set-Content -LiteralPath $SummaryPath -Value $lines -Encoding utf8
}

$resolvedInstance = Resolve-RequiredDirectory $InstancePath 'instance'

switch ($Action) {
    'Status' {
        Write-State (Get-InstanceState $resolvedInstance)
    }
    'ListJava' {
        Get-JavaProcesses | Sort-Object StartTime | Format-Table -AutoSize
    }
    'Prepare' {
        Assert-ClientStopped
        $state = Get-InstanceState $resolvedInstance
        $hash = Sync-EtfJar $SourceJar $state.EtfJar
        $updated = Set-Combination $resolvedInstance $Combination
        if ($updated.EtfSha256 -ne $hash) {
            throw "Prepared instance hash changed unexpectedly"
        }
        Write-State $updated
    }
    'StartJfr' {
        if ($ProcessId -le 0) {
            throw 'StartJfr requires -ProcessId from Task Manager or -Action ListJava'
        }
        $state = Get-InstanceState $resolvedInstance
        $process = Get-Process -Id $ProcessId -ErrorAction Stop
        $jcmd = Resolve-Jcmd $ProcessId
        $sessionRoot = Get-SessionRoot $resolvedInstance
        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        $baseName = "$stamp-$($state.Combination.ToLowerInvariant())"
        $recordingPath = Join-Path $sessionRoot "$baseName.jfr"
        $manifestPath = Join-Path $sessionRoot "$baseName.manifest.txt"
        $statePath = Join-Path $sessionRoot 'active-recording.json'
        Write-Manifest $manifestPath $state $ProcessId $process.Path
        Invoke-Jcmd $jcmd @(
            "$ProcessId", 'JFR.start', "name=$RecordingName", 'settings=profile',
            "filename=$recordingPath", 'dumponexit=true', "maxsize=${MaxSizeMb}m"
        )
        [PSCustomObject]@{
            ProcessId = $ProcessId
            RecordingName = $RecordingName
            RecordingPath = $recordingPath
            ManifestPath = $manifestPath
            Combination = $state.Combination
        } | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding utf8
        Write-Output "JFR recording: $recordingPath"
    }
    'StopJfr' {
        $sessionRoot = Get-SessionRoot $resolvedInstance
        $statePath = Join-Path $sessionRoot 'active-recording.json'
        if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) {
            throw "Active recording state does not exist: $statePath"
        }
        $recording = Get-Content -LiteralPath $statePath -Raw -Encoding utf8 | ConvertFrom-Json
        $targetProcessId = [int]$recording.ProcessId
        if ($ProcessId -gt 0 -and $ProcessId -ne $targetProcessId) {
            throw "ProcessId does not match active recording: requested=$ProcessId active=$targetProcessId"
        }
        $jcmd = Resolve-Jcmd $targetProcessId
        Invoke-Jcmd $jcmd @(
            "$targetProcessId", 'JFR.dump', "name=$($recording.RecordingName)",
            "filename=$($recording.RecordingPath)"
        )
        Invoke-Jcmd $jcmd @("$targetProcessId", 'JFR.stop', "name=$($recording.RecordingName)")

        $basePath = [System.IO.Path]::ChangeExtension([string]$recording.RecordingPath, $null)
        $latestLog = Join-Path $resolvedInstance 'logs\latest.log'
        if (Test-Path -LiteralPath $latestLog -PathType Leaf) {
            $copiedLog = "$basePath.latest.log"
            Copy-Item -LiteralPath $latestLog -Destination $copiedLog -Force
            Write-LogSummary $copiedLog "$basePath.summary.txt"
        }
        Remove-Item -LiteralPath $statePath
        Write-Output "JFR saved: $($recording.RecordingPath)"
        Write-Output "Manifest: $($recording.ManifestPath)"
    }
}
