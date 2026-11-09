# When does the helper retry a thumbnail it could not get?
#
# The missing-cover complaint is a timing question, so this measures the timing. It loads
# the real Update-Art out of the shipped script - by slicing the file, not by copying the
# logic, so a change to the helper shows up here instead of being tested against a stale
# duplicate - then hands it a source that has a title and no picture, and prints every
# attempt.
#
# Pass -Flat5 to re-run the same measurement with the old single five-second wait, which
# is the control that says this probe is watching the thing it claims to watch.
param([switch] $Flat5)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$lines = Get-Content -LiteralPath (Join-Path $root 'src\main\resources\musicdeck\smtc-bridge.ps1')
$from = -1
$decl = -1
$to = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
	if ($lines[$i].StartsWith('$ErrorActionPreference')) { $from = $i }
	if ($lines[$i].StartsWith('function Update-Art')) { $decl = $i }
	if ($decl -ge 0 -and $to -lt 0 -and $i -gt $decl -and $lines[$i] -eq '}') { $to = $i }
}

if ($from -lt 0 -or $decl -lt 0 -or $to -lt 0) {
	throw 'could not find the artwork half of the helper; the file has been restructured'
}

$slice = ($lines[($from)..($to)] -join "`n")
Write-Output ("loaded " + $slice.Length + " characters of the real helper, ending with: " +
	($lines[($to - 2)..$to] -join ' / ').Trim())
Invoke-Expression $slice
if ($Flat5) {
	$script:ArtBackoff = @(5.0)
	Write-Output 'CONTROL: the wait table forced back to a flat five seconds'
}

$scratch = Join-Path $env:TEMP 'musicdeck\probe-art.png'
$ArtPath = $scratch
Remove-Item -LiteralPath $scratch -Force -ErrorAction SilentlyContinue

# A track whose title has arrived and whose thumbnail has not: the state nearly every new
# song passes through, and the reason the card used to sit blank.
$source = [pscustomobject]@{ Title = 'Probe'; Artist = ''; AlbumTitle = ''; Thumbnail = $null }
Write-Output ('retry table: ' + ($script:ArtBackoff -join ', '))

$started = Get-Date
$last = $script:ArtTriedAt
for ($pass = 0; $pass -lt 90; $pass++) {
	Update-Art $source 'Probe||'
	if ($script:ArtTriedAt -ne $last) {
		Write-Output ('attempt ' + ($pass) + ' at +' +
			[Math]::Round((Get-Date).Subtract($started).TotalMilliseconds) + 'ms')
		$last = $script:ArtTriedAt
	}

	Start-Sleep -Milliseconds 100
}

Write-Output ('tile on disk after the run: ' + (Test-Path -LiteralPath $scratch))
