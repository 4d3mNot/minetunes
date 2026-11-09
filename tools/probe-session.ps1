# Ask the bridge once what Windows reports right now, and keep its stdout so the answer
# can be read instead of guessed at.
param([int] $Seconds = 6)

$ErrorActionPreference = 'Stop'
$root = (Get-Location).Path
$log = Join-Path $root 'build-art\bridge-probe.txt'
$art = Join-Path $root 'build-art\art.png'
$cmd = Join-Path $env:TEMP 'musicdeck\commands-probe'

if (Test-Path -LiteralPath $log) {
	Remove-Item -LiteralPath $log -Force
}

$child = Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass',
	'-File', (Join-Path $root 'src\main\resources\musicdeck\smtc-bridge.ps1'),
	'-CommandDir', $cmd, '-ArtPath', $art) -PassThru -WindowStyle Hidden `
	-RedirectStandardOutput $log

Start-Sleep -Seconds $Seconds
Stop-Process -Id $child.Id -Force -ErrorAction SilentlyContinue
Wait-Process -Id $child.Id -Timeout 5 -ErrorAction SilentlyContinue

Get-Content -LiteralPath $log -Tail 6
