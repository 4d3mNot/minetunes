# Regenerate build-art/art.png from whatever Windows is playing right now, using the
# real bridge script rather than a copy of its maths. Starts the helper, waits for one
# artwork write, and stops only the process it started.
param(
	[string] $Bridge = 'src\main\resources\musicdeck\smtc-bridge.ps1',
	[string] $Out = 'build-art\art.png',
	[int] $Seconds = 12
)

$ErrorActionPreference = 'Stop'
$root = (Get-Location).Path
$target = Join-Path $root $Out
$dir = Split-Path -Parent $target
if (-not (Test-Path $dir)) {
	New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

if (Test-Path $target) {
	Remove-Item -LiteralPath $target -Force
}

$cmd = Join-Path $env:TEMP 'musicdeck\commands-gen'
Get-ChildItem -LiteralPath $cmd -File -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue

$child = Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass',
	'-File', (Join-Path $root $Bridge), '-CommandDir', $cmd, '-ArtPath', $target) -PassThru -WindowStyle Hidden

$deadline = (Get-Date).AddSeconds($Seconds)
while ((Get-Date) -lt $deadline) {
	if (Test-Path -LiteralPath $target) {
		Start-Sleep -Milliseconds 200
		break
	}

	if ($child.HasExited) {
		break
	}

	Start-Sleep -Milliseconds 250
}

Stop-Process -Id $child.Id -Force -ErrorAction SilentlyContinue
Wait-Process -Id $child.Id -Timeout 5 -ErrorAction SilentlyContinue

if (-not (Test-Path -LiteralPath $target)) {
	Write-Output 'NO-ARTWORK: nothing wrote a tile (is any media session playing?)'
	exit 1
}

Add-Type -AssemblyName System.Drawing
$image = [System.Drawing.Image]::FromFile($target)
Write-Output ("art.png " + $image.Width + "x" + $image.Height + "  " + (Get-Item -LiteralPath $target).LastWriteTime)
$image.Dispose()
