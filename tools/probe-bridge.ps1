# Runs the real bridge for a few seconds against whatever media session exists, and
# reports the state lines plus how the artwork tile behaved. Read-only: no command file
# is ever written, so the player is never touched.
param([int] $Seconds = 9)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
$work = Join-Path $root 'build-probe'
New-Item -ItemType Directory -Force -Path $work | Out-Null
$cmd = Join-Path $work 'commands'
$art = Join-Path $work 'art.png'
Remove-Item -LiteralPath $art -Force -ErrorAction SilentlyContinue

$script = Join-Path $root 'src\main\resources\musicdeck\smtc-bridge.ps1'
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = 'powershell.exe'
$psi.Arguments = '-NoProfile -ExecutionPolicy Bypass -File "' + $script + '" -CommandDir "' + $cmd + '" -ArtPath "' + $art + '"'
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.CreateNoWindow = $true
$p = [System.Diagnostics.Process]::Start($psi)
$deadline = (Get-Date).AddSeconds($Seconds)
while ((Get-Date) -lt $deadline) {
	$line = $p.StandardOutput.ReadLine()
	if ($null -eq $line) { Start-Sleep -Milliseconds 50; continue }
	$stamp = '{0:0.000}' -f $p.StandardOutput.BaseStream.Length
	$has = Test-Path -LiteralPath $art
	Write-Output ('{0:HH:mm:ss.fff}  art={1}  {2}' -f (Get-Date), $has, $line)
}

$p.Kill()
if (Test-Path -LiteralPath $art) {
	$tile = [System.Drawing.Bitmap]::new((Get-Item -LiteralPath $art).FullName)
	Write-Output ('tile ' + $tile.Width + 'x' + $tile.Height + '  ' + (Get-Item -LiteralPath $art).Length + ' bytes')
	$tile.Dispose()
} else {
	Write-Output 'no tile was written'
}
