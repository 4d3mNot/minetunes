# MineTunes -> Windows SMTC helper.
#
# Runs as a long-lived child process of Minecraft and writes one JSON object per
# line to stdout. Commands arrive as files in $CommandDir, written by the game
# side and consumed here in name order.
#
# There is deliberately no stdin channel. Reading [Console]::In from a second
# thread makes powershell.exe exit with code 2 and print nothing, and reading it
# on the main thread would stall the media poll behind it.
#
# Tested against Windows PowerShell 5.1 on Windows 10. Do not run this under
# pwsh 7: System.Runtime.WindowsRuntime is a .NET Framework assembly.

param(
	[string] $CommandDir = (Join-Path $env:TEMP 'musicdeck\commands'),
	[string] $ArtPath = ''
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
	Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
		$_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]

function Await-Op($op, $type, [int] $TimeoutMs = -1) {
	$method = $asTaskGeneric.MakeGenericMethod($type)
	$task = $method.Invoke($null, @($op))
	if (-not $task.Wait($TimeoutMs)) {
		# An unbounded wait here stops the whole poll loop, and a loop that is not
		# polling is a card that froze rather than a card that is missing a picture.
		throw "the media source did not answer within $TimeoutMs ms"
	}

	return $task.Result
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Media.MediaPlaybackAutoRepeatMode, Windows.Media, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage, ContentType = WindowsRuntime]

# Album artwork, in GUI pixels, and the size the game blits 1:1. Minecraft uploads a
# DynamicTexture with a NEAREST sampler, so a larger source would be point-sampled down
# and shimmer; the resample has to happen here where a bicubic filter exists. The game
# also derives its "pixel" style by downsampling this tile itself, so a bigger source is
# what buys both looks.
$script:ArtSize = 48
$script:ArtKey = ''
$script:ArtTriedAt = [DateTime]::MinValue
$script:ArtMissing = $true
# Spotify publishes the title of a new track a moment before it publishes that track's
# thumbnail, so the first fetch of almost every new song used to fail - and the wait
# before trying again was a flat five seconds. That is the whole of the missing-cover
# complaint: the art was there all along, five seconds late. A failed fetch now comes
# back quickly and only settles into a slow poll once it is clear the source is gone.
$script:ArtRetry = 0
$script:ArtBackoff = @(0.0, 0.3, 0.6, 1.2, 2.5, 5.0)
$script:AsStreamMethod = $null
$script:DrawingReady = $false

try {
	Add-Type -AssemblyName System.Drawing
	$script:DrawingReady = $true
} catch {
	$script:ArtPath = ''
}

function Get-AsStreamMethod {
	# PowerShell's own binder refuses to pass an opaque WinRT stream RCW to a method
	# that wants IRandomAccessStream, but reflection's binder performs a real
	# QueryInterface. The helper lives on System.IO.WindowsRuntimeStreamExtensions,
	# which is not where the async extensions live, so go through the assembly.
	if ($null -eq $script:AsStreamMethod) {
		$asm = [AppDomain]::CurrentDomain.GetAssemblies() |
			Where-Object { $_.GetName().Name -eq 'System.Runtime.WindowsRuntime' } | Select-Object -First 1
		$type = $asm.GetType('System.IO.WindowsRuntimeStreamExtensions', $false)
		$script:AsStreamMethod = ($type.GetMethods() |
			Where-Object { $_.Name -eq 'AsStream' -and $_.GetParameters().Count -eq 1 -and
				$_.GetParameters()[0].ParameterType.Name -eq 'IRandomAccessStream' })[0]
	}

	return $script:AsStreamMethod
}

function Remove-Art {
	$script:ArtMissing = $true
	if ($ArtPath -eq '') {
		return
	}

	Remove-Item -LiteralPath $ArtPath -Force -ErrorAction SilentlyContinue
}

function Update-Art($props, [string]$key) {
	if ($ArtPath -eq '' -or $key -eq '') {
		return
	}

	if ($key -ne $script:ArtKey) {
		# A different track: the previous tile's history says nothing about this one.
		$script:ArtKey = $key
		$script:ArtMissing = $true
		$script:ArtTriedAt = [DateTime]::MinValue
		$script:ArtRetry = 0
	}

	$wait = $script:ArtBackoff[[Math]::Min($script:ArtRetry, $script:ArtBackoff.Count - 1)]
	if (-not $script:ArtMissing -or ((Get-Date) - $script:ArtTriedAt).TotalSeconds -lt $wait) {
		return
	}

	$script:ArtTriedAt = Get-Date

	try {
		if ($null -eq $props.Thumbnail) {
			throw 'no-thumbnail'
		}

		$raw = Await-Op ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType]) 4000
		$box = New-Object 'object[]' 1
		$box[0] = $raw
		$stream = (Get-AsStreamMethod).Invoke($null, $box)
		$buffer = New-Object System.IO.MemoryStream
		try {
			# GDI+ wants a seekable source it can re-read, and the WinRT adapter reports
			# itself seekable but is cheaper to drain than to trick.
			$stream.CopyTo($buffer)
			$buffer.Position = 0
			$image = [System.Drawing.Image]::FromStream($buffer)
			try {
				$tile = New-Object System.Drawing.Bitmap ($script:ArtSize, $script:ArtSize)
				$gfx = [System.Drawing.Graphics]::FromImage($tile)
				$gfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
				$gfx.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
				# Spotify hands back a 300x300 PNG whose every pixel is about 78% opaque, with
				# the bottom rows faded out for its own logo. Compositing that straight onto a
				# fresh bitmap leaves holes the panel shows through, so lay the card's ink down
				# first: the tile the game reads is then fully opaque whatever the source says.
				$gfx.Clear([System.Drawing.Color]::FromArgb(255, 10, 10, 12))
				$gfx.DrawImage($image, 0, 0, $script:ArtSize, $script:ArtSize)
				$gfx.Dispose()
				$tile.Save($ArtPath + '.tmp', [System.Drawing.Imaging.ImageFormat]::Png)
				$tile.Dispose()
				# A single replace: the game side polls the file, so it must never observe
				# a half-written or momentarily absent image.
				Move-Item -LiteralPath ($ArtPath + '.tmp') -Destination $ArtPath -Force
				$script:ArtMissing = $false
			} finally {
				$image.Dispose()
			}
		} finally {
			$buffer.Dispose()
			$stream.Dispose()
		}
	} catch {
		# Podcasts, local files and ad slots genuinely have no artwork. Clearing the
		# file is what stops the previous cover from following the new track around.
		# The retry counter is what decides whether this reads as a track with no
		# picture or as a picture that is five seconds late.
		Remove-Art
		$script:ArtRetry = $script:ArtRetry + 1
	}
}


$script:Manager = $null
$script:ManagerAge = [DateTime]::MinValue

function Get-SessionManager {
	# The manager caches which session is current, so re-request it periodically
	# and whenever a poll finds nothing. Without this the bridge keeps driving a
	# dead Spotify window after the app is restarted.
	if ($null -eq $script:Manager -or ((Get-Date) - $script:ManagerAge).TotalSeconds -gt 8) {
		$script:Manager = Await-Op ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
		$script:ManagerAge = Get-Date
	}

	return $script:Manager
}

function Get-Session {
	$session = (Get-SessionManager).GetCurrentSession()
	if ($null -eq $session) {
		$script:Manager = $null
		$session = (Get-SessionManager).GetCurrentSession()
	}

	return $session
}

function Esc([string]$s) {
	if ([string]::IsNullOrEmpty($s)) {
		return ''
	}

	$sb = New-Object System.Text.StringBuilder
	foreach ($ch in $s.ToCharArray()) {
		$code = [int] $ch
		if ($ch -eq '"') {
			[void] $sb.Append('\"')
		} elseif ($ch -eq '\') {
			[void] $sb.Append('\\')
		} elseif ($code -lt 32) {
			[void] $sb.Append('\u').Append(('{0:x4}' -f $code))
		} else {
			[void] $sb.Append($ch)
		}
	}

	return $sb.ToString()
}

function Write-Line([string]$text) {
	[Console]::Out.WriteLine($text)
	[Console]::Out.Flush()
}

function Get-StateLine($session) {
	if ($null -eq $session) {
		return '{"t":"state","session":false}'
	}

	$info = $session.GetPlaybackInfo()
	$tl = $session.GetTimelineProperties()
	$status = [string] $info.PlaybackStatus
	$position = [double] $tl.Position.TotalSeconds

	# SMTC's Position is a snapshot stamped at LastUpdatedTime, not a running
	# clock. Extrapolating it is what makes the in-game progress bar move at all.
	if ($status -eq 'Playing') {
		$age = ([DateTimeOffset]::UtcNow - [DateTimeOffset] $tl.LastUpdatedTime).TotalSeconds
		if ($age -gt 0 -and $age -lt 8) {
			$position += $age
		}
	}

	$title = ''
	$artist = ''
	$album = ''
	try {
		$props = Await-Op ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]) 2000
		$title = [string] $props.Title
		$artist = [string] $props.Artist
		$album = [string] $props.AlbumTitle
		if ($title -ne '' -or $artist -ne '') {
			Update-Art $props ($title + '|' + $artist + '|' + $album)
		}
		# A poll that answers with no title at all is a stutter, not a new track, and the
		# card is hidden by the game for exactly as long as it lasts. Clearing the tile
		# here used to be what turned one bad poll into a missing cover.
	} catch {
		# A track change in flight leaves this call throwing for a moment.
	}

	$shuffle = 'null'
	if ($info.IsShuffleActive.HasValue) {
		$shuffle = if ($info.IsShuffleActive.Value) { 'true' } else { 'false' }
	}

	$repeat = 'null'
	if ($info.AutoRepeatMode.HasValue) {
		$repeat = '"' + (Esc ([string] $info.AutoRepeatMode.Value)) + '"'
	}

	return '{"t":"state","session":true' +
		',"source":"' + (Esc ([string] $session.SourceAppUserModelId)) + '"' +
		',"status":"' + (Esc $status) + '"' +
		',"title":"' + (Esc $title) + '"' +
		',"artist":"' + (Esc $artist) + '"' +
		',"album":"' + (Esc $album) + '"' +
		',"pos":' + ('{0:0.0}' -f [Math]::Max(0, $position)) +
		',"end":' + ('{0:0.0}' -f [double] $tl.EndTime.TotalSeconds) +
		',"shuffle":' + $shuffle +
		',"repeat":' + $repeat + '}'
}

function Handle-Command([string]$line) {
	if ($line -eq 'quit') {
		return $false
	}

	$verb = $line
	$arg = ''
	$split = $line.IndexOf(':')
	if ($split -gt 0) {
		$verb = $line.Substring(0, $split)
		$arg = $line.Substring($split + 1)
	}

	$escaped = Esc $line
	$session = Get-Session

	if ($null -eq $session) {
		Write-Line ('{"t":"ack","cmd":"' + $escaped + '","ok":false,"error":"no-media-session"}')
		return $true
	}

	try {
		# Every Try* method on the session is IAsyncOperation<bool>, not
		# IAsyncAction: the bool is "the player accepted the request".
		switch ($verb) {
			'toggle' { $ok = Await-Op ($session.TryTogglePlayPauseAsync()) ([bool]) }
			'play' { $ok = Await-Op ($session.TryPlayAsync()) ([bool]) }
			'pause' { $ok = Await-Op ($session.TryPauseAsync()) ([bool]) }
			'next' { $ok = Await-Op ($session.TrySkipNextAsync()) ([bool]) }
			'prev' { $ok = Await-Op ($session.TrySkipPreviousAsync()) ([bool]) }
			'shuffle' { $ok = Await-Op ($session.TryChangeShuffleActiveAsync($arg -eq 'on')) ([bool]) }
			'repeat' {
				$mode = [Windows.Media.MediaPlaybackAutoRepeatMode]::$arg
				$ok = Await-Op ($session.TryChangeAutoRepeatModeAsync($mode)) ([bool])
			}
			'seek' {
				# TryChangePlaybackPositionAsync wants the position in 100ns ticks.
				$ticks = [long] (([double] $arg) * 10000000.0)
				$ok = Await-Op ($session.TryChangePlaybackPositionAsync($ticks)) ([bool])
			}
			'state' { $ok = $true }
			default {
				Write-Line ('{"t":"ack","cmd":"' + $escaped + '","ok":false,"error":"unknown-command"}')
				return $true
			}
		}

		Write-Line ('{"t":"ack","cmd":"' + $escaped + '","ok":' + $(if ($ok) { 'true' } else { 'false' }) + '}')
	} catch {
		Write-Line ('{"t":"ack","cmd":"' + $escaped + '","ok":false,"error":"' + (Esc $_.Exception.Message) + '"}')
	}

	Write-Line (Get-StateLine $session)
	return $true
}

function Clear-CommandDir {
	Get-ChildItem -LiteralPath $CommandDir -File -ErrorAction SilentlyContinue |
		Remove-Item -Force -ErrorAction SilentlyContinue
}

function Drain-Commands {
	foreach ($file in (Get-ChildItem -LiteralPath $CommandDir -Filter '*.cmd' -File -ErrorAction SilentlyContinue | Sort-Object Name)) {
		$line = $null
		try {
			$line = (Get-Content -LiteralPath $file.FullName -Raw -ErrorAction Stop).Trim()
		} catch {
			continue
		}

		Remove-Item -LiteralPath $file.FullName -Force -ErrorAction SilentlyContinue
		if ($line.Length -eq 0) {
			continue
		}

		if (-not (Handle-Command $line)) {
			return $false
		}
	}

	return $true
}

if (-not (Test-Path -LiteralPath $CommandDir)) {
	New-Item -ItemType Directory -Force -Path $CommandDir | Out-Null
}

Clear-CommandDir
Write-Line '{"t":"hello","bridge":3}'

# Commands are checked every 100ms so a keypress feels instant. The media session
# is polled every third pass, because each poll pays for two WinRT round trips.
$pass = 0
while ($true) {
	if (-not (Drain-Commands)) {
		exit 0
	}

	if (($pass % 3) -eq 0) {
		try {
			Write-Line (Get-StateLine (Get-Session))
		} catch {
			# A transient WinRT failure must not look like a dead bridge to the game.
			Write-Line ('{"t":"error","msg":"' + (Esc $_.Exception.Message) + '"}')
		}
	}

	$pass = ($pass + 1) % 3000
	Start-Sleep -Milliseconds 100
}
