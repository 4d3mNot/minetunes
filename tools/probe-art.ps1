# Can the SMTC thumbnail be turned into a PNG file from PowerShell 5.1, and can a
# dominant accent colour be sampled from it? This is the gate for album art in the
# mod's HUD, so it is a probe, not a feature.
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
	Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
		$_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]

function Await-Op($op, $type) {
	$method = $asTaskGeneric.MakeGenericMethod($type)
	$task = $method.Invoke($null, @($op))
	$task.Wait(-1) | Out-Null
	return $task.Result
}

# LoadBytesAsync and friends are IAsyncOperationWithProgress, a different AsTask
# overload. Both generic arguments are uint here, so the order cannot bite.
$asTaskProgress = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
	Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
		$_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperationWithProgress`2' })[0]

function Await-OpP($op) {
	$task = $asTaskProgress.MakeGenericMethod([uint32], [uint32]).Invoke($null, @($op))
	$task.Wait(-1) | Out-Null
	return $task.Result
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage, ContentType = WindowsRuntime]

$mgr = Await-Op ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$session = $mgr.GetCurrentSession()
if ($null -eq $session) { Write-Output 'NO SESSION'; exit 0 }

$props = Await-Op ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
$thumb = $props.Thumbnail
Write-Output ('title=' + $props.Title)
Write-Output ('thumbnail null? ' + ($null -eq $thumb))
if ($null -eq $thumb) { exit 0 }
Write-Output ('thumb type=' + $thumb.GetType().FullName)

# Thumbnail is a RandomAccessStreamReference, not a bitmap: open the reference, read
# its bytes, decode with System.Drawing (which takes Spotify's JPEG fine) and
# re-encode as a fixed 64x64 PNG.
Add-Type -AssemblyName System.Drawing
$ras = Await-Op ($thumb.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
Write-Output ('stream size=' + $ras.Size)
# The .NET AsStream bridge will not bind a WinRT RCW from PowerShell, so read the
# bytes through WinRT's own DataReader instead.
$dr = New-Object Windows.Storage.Streams.DataReader($ras.GetInputStreamAt(0))
Await-OpP ($dr.LoadBytesAsync([uint32]$ras.Size)) | Out-Null
$bytes = New-Object 'byte[]' ([int]$ras.Size)
$dr.ReadBytes($bytes)
$dr.Dispose()
$ms = New-Object System.IO.MemoryStream(, $bytes)
$src = New-Object System.Drawing.Bitmap($ms)
Write-Output ('decoded=' + $src.Width + 'x' + $src.Height + ' ' + $src.RawFormat)

$size = 64
$bmp = New-Object System.Drawing.Bitmap $size, $size
$gfx = [System.Drawing.Graphics]::FromImage($bmp)
$gfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gfx.DrawImage($src, 0, 0, $size, $size)
$gfx.Dispose()
$src.Dispose()

$out = Join-Path $env:TEMP 'musicdeck\probe-art.png'
New-Item -ItemType Directory -Force -Path (Split-Path $out) | Out-Null
$bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Output ('wrote ' + $out + ' bytes=' + (Get-Item $out).Length)

# Dominant accent: average the saturated pixels so greyscale artwork does not win,
# then lift toward the brightest sample so the bar stays visible on a dark card.
$r = 0; $g = 0; $b = 0; $n = 0
for ($y = 0; $y -lt $size; $y++) {
	for ($x = 0; $x -lt $size; $x++) {
		$p = $bmp.GetPixel($x, $y)
		$max = [Math]::Max([Math]::Max($p.R, $p.G), $p.B)
		$min = [Math]::Min([Math]::Min($p.R, $p.G), $p.B)
		if (($max - $min) -gt 40 -and $max -gt 60) { $r += $p.R; $g += $p.G; $b += $p.B; $n++ }
	}
}
if ($n -eq 0) { Write-Output 'accent=none (no saturated pixels)'; } else {
	$r = [int]($r / $n); $g = [int]($g / $n); $b = [int]($b / $n)
	Write-Output ('accent=' + ('{0:x2}{1:x2}{2:x2}' -f $r, $g, $b) + ' from ' + $n + ' of ' + ($size * $size) + ' px')
}
$bmp.Dispose()
