param([switch]$Go, [switch]$Probe)

# Restart the running Minecraft client by replaying its own launch command.
# TLauncher owns the version, the classpath and the JVM flags, so the live process is
# the only accurate copy of how the game was started. The command line may carry an
# access token, so it is held in a variable, never written to disk, and redacted in
# everything this script prints.
$ErrorActionPreference = 'Stop'
$game = Join-Path $env:APPDATA '.minecraft\versions\main'

function Redact([string]$line) {
	foreach ($secret in '--accessToken', '--xuid', '--apiKey', '--session') {
		$line = $line -replace [regex]::Escape($secret) + '[ =]+\S+', "$secret [redacted]"
	}
	return $line
}

# Which javaw belongs to us: the one launched for the main profile. Mods are not on the
# JVM classpath (Fabric loader reads the mods folder itself), so the mod name is not a
# usable marker, and 'Get-Process javaw | Select -First 1' photographs whichever game
# started first when more than one is running.
$candidates = @(Get-CimInstance Win32_Process -Filter "Name='javaw.exe'")

if ($Probe) {
	foreach ($proc in $candidates) {
		$line = $proc.CommandLine
		if ($null -eq $line) { $line = '' }
		Write-Output ('pid=' + $proc.ProcessId + ' len=' + $line.Length `
			+ ' profile=' + ($line -match 'versions.main') + ' loader=' + ($line -match 'fabricloader') `
			+ ' natives=' + ($line -match 'natives') + ' offline-token=' + ($line -match '--accessToken[ =]0(\s|$)'))
	}
	if (@($candidates).Count -eq 0) { Write-Output 'no javaw' }
	exit 0
}

$target = $null
foreach ($proc in $candidates) {
	if ($proc.CommandLine -match [regex]::Escape("$game")) { $target = $proc; break }
}

if ($null -eq $target -and @($candidates).Count -eq 1) {
	# The profile path is not always in the arguments - it can be the working directory
	# instead - so a lone client is identified by being the only one.
	$target = $candidates[0]
	Write-Output 'note: identified the client as the only running javaw'
}

if ($null -eq $target) {
	Write-Output 'NO UNAMBIGUOUS CLIENT. No javaw names the main profile and more than one is running.'
	Write-Output ("javaw processes seen: " + @($candidates).Count)
	exit 1
}

$line = $target.CommandLine
Write-Output ("pid=" + $target.ProcessId + "  args=" + ($line.Length - 1))
Write-Output ("token is the offline placeholder: " + ($line -match '--accessToken[ =]0(\s|$)'))
Write-Output ("classpath names the profile jar: " + ($line -match [regex]::Escape("$game")))

# Split off the executable the same way Windows does: a leading quoted path, else the
# first bare token up to the first space.
$exe = $null
$rest = $null
if ($line -match '^"([^"]+)"\s*(.*)$') {
	$exe = $Matches[1]
	$rest = $Matches[2]
}
elseif ($line -match '^(\S+)\s+(.*)$') {
	$exe = $Matches[1]
	$rest = $Matches[2]
}
else {
	Write-Output 'UNPARSED command line; refusing to restart.'
	exit 1
}

Write-Output ("exe exists: " + (Test-Path -LiteralPath $exe))
Write-Output ("launch tail: " + (Redact (($rest -split '\s+' | Where-Object { $_ -match '^--(username|gameDir|assetsDir|version|width|height)$' }) -join ' ')))
if ($exe -notmatch 'javaw?\.exe$') {
	Write-Output ("REFUSING: exe did not look like java - " + $exe)
	exit 1
}

if (-not $Go) {
	Write-Output 'CHECK ONLY. Re-run with -Go to quit and relaunch.'
	exit 0
}

Write-Output ('stopping pid ' + $target.ProcessId)

# Ask the window to close rather than killing the process: GLFW turns that into the
# game's own shutdown, which flushes the world. A hard Stop-Process would drop whatever
# changed since the last autosave.
$client = Get-Process -Id $target.ProcessId
$asked = $client.CloseMainWindow()
Write-Output ('close requested on the window: ' + $asked)

$deadline = (Get-Date).AddSeconds(45)
while ((Get-Process -Id $target.ProcessId -ErrorAction SilentlyContinue) -and (Get-Date) -lt $deadline) {
	Start-Sleep -Milliseconds 500
}

if (Get-Process -Id $target.ProcessId -ErrorAction SilentlyContinue) {
	Write-Output 'still alive after 45s, so stopping it outright (anything not yet autosaved is lost)'
	Stop-Process -Id $target.ProcessId -ErrorAction SilentlyContinue
	Start-Sleep -Seconds 2
}
if (Get-Process -Id $target.ProcessId -ErrorAction SilentlyContinue) {
	Write-Output 'STILL ALIVE. Refusing to start a second client.'
	exit 1
}
Write-Output 'old client exited'

Start-Process -FilePath $exe -ArgumentList $rest -WorkingDirectory $game | Out-Null
Write-Output 'relaunched'
