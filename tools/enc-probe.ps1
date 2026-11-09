# Capture the bridge's stdout as raw bytes, the way MediaBridge reads it, so a mangled
# name can be told apart from a mangled terminal. Prints nothing itself: read the file.
param(
	[string] $Bridge = 'src\main\resources\musicdeck\smtc-bridge.ps1',
	[string] $Out = 'build-art\stdout.bin',
	[int] $Seconds = 7
)

$ErrorActionPreference = 'Stop'
$root = (Get-Location).Path
$target = Join-Path $root $Out
$dir = Split-Path -Parent $target
if (-not (Test-Path $dir)) {
	New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

if (Test-Path -LiteralPath $target) {
	Remove-Item -LiteralPath $target -Force
}

$cmd = Join-Path $env:TEMP 'musicdeck\commands-enc'
Get-ChildItem -LiteralPath $cmd -File -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue

$child = Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass',
	'-File', (Join-Path $root $Bridge), '-CommandDir', $cmd, '-ArtPath', (Join-Path $root 'build-art\enc-art.png')) `
	-RedirectStandardOutput $target -PassThru -WindowStyle Hidden

Start-Sleep -Seconds $Seconds
Stop-Process -Id $child.Id -Force -ErrorAction SilentlyContinue
Wait-Process -Id $child.Id -Timeout 5 -ErrorAction SilentlyContinue
Write-Output ("wrote " + (Get-Item -LiteralPath $target).Length + " bytes to " + $Out)
