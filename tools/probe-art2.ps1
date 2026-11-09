# Fourth gate, and the decisive one: pass the opaque stream RCW to a WinRT *static*
# method (BitmapDecoder.CreateAsync), which binds through WinRT metadata rather than
# .NET reflection, then get a SoftwareBitmap back — a runtime class, which PowerShell
# can actually see.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime

$flags = [System.Reflection.BindingFlags]'Public,Static'
$ops = [System.WindowsRuntimeSystemExtensions].GetMethods($flags) | Where-Object { $_.Name -eq 'AsTask' }
$op1 = ($ops | Where-Object { $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
$op2 = ($ops | Where-Object { $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperationWithProgress`2' })[0]

function Await-Op($op, $type) {
	$task = $op1.MakeGenericMethod($type).Invoke($null, @($op))
	$task.Wait(-1) | Out-Null
	return $task.Result
}

function Await-Op2($op, $result, $progress) {
	$task = $op2.MakeGenericMethod($result, $progress).Invoke($null, @($op))
	$task.Wait(-1) | Out-Null
	return $task.Result
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage, ContentType = WindowsRuntime]
[void][Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType = WindowsRuntime]
[void][Windows.Graphics.Imaging.SoftwareBitmap, Windows.Graphics.Imaging, ContentType = WindowsRuntime]
[void][Windows.Graphics.Imaging.BitmapPixelFormat, Windows.Graphics.Imaging, ContentType = WindowsRuntime]
[void][Windows.Graphics.Imaging.BitmapAlphaMode, Windows.Graphics.Imaging, ContentType = WindowsRuntime]

# BitmapProgressCallback is a delegate, so PowerShell cannot name it. Read the generic
# arguments off the projection's own return type instead of guessing them.
$create = ([Windows.Graphics.Imaging.BitmapDecoder].GetMethods() |
	Where-Object { $_.Name -eq 'CreateAsync' -and $_.GetParameters().Count -eq 1 })[0]
$createArgs = $create.ReturnType.GetGenericArguments()
Write-Output ('CreateAsync generic args: ' + (($createArgs | ForEach-Object { $_.Name }) -join ', '))

$mgr = Await-Op ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$props = Await-Op ($mgr.GetCurrentSession().TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
$raw = Await-Op ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])

$decoder = Await-Op2 ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($raw)) $createArgs[0] $createArgs[1]
Write-Output ('decoder  : ' + $decoder.DecoderName + ' ' + $decoder.OrientedPixelWidth + 'x' + $decoder.OrientedPixelHeight + ' dpi=' + $decoder.DpiX)

$bmp = Await-Op ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
Write-Output ('bitmap   : ' + $bmp.PixelWidth + 'x' + $bmp.PixelHeight + ' ' + $bmp.BitmapPixelFormat + ' ' + $bmp.BitmapAlphaMode)

$copy = Await-Op ($bmp.Clone([Windows.Graphics.Imaging.BitmapPixelFormat]::Bgra8, [Windows.Graphics.Imaging.BitmapAlphaMode]::Premultiplied)) ([Windows.Graphics.Imaging.SoftwareBitmap])
$bytes = New-Object 'byte[]' ($copy.PixelWidth * $copy.PixelHeight * 4)
$copy.CopyToBuffer($bytes)
Write-Output ('buffer   : ' + $bytes.Length + ' bytes, first px BGRA=' + $bytes[0] + ',' + $bytes[1] + ',' + $bytes[2] + ',' + $bytes[3])

# Straight to a System.Drawing bitmap from the BGRA buffer, then PNG + accent.
Add-Type -AssemblyName System.Drawing
$stride = $copy.PixelWidth * 4
$handle = [System.Runtime.InteropServices.GCHandle]::Alloc($bytes, [System.Runtime.InteropServices.GCHandleType]::Pinned)

$out = Join-Path $env:TEMP 'musicdeck\probe-art.png'
New-Item -ItemType Directory -Force -Path (Split-Path $out) | Out-Null
$src = [System.Drawing.Bitmap]::new($copy.PixelWidth, $copy.PixelHeight, $stride, [System.Drawing.Imaging.PixelFormat]::FormatBgra32, $handle.AddrOfPinnedObject())
$scaled = New-Object System.Drawing.Bitmap 64, 64
$gfx = [System.Drawing.Graphics]::FromImage($scaled)
$gfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gfx.DrawImage($src, 0, 0, 64, 64)
$gfx.Dispose()
$scaled.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$handle.Free()
Write-Output ('png      : ' + $out + ' ' + (Get-Item $out).Length + ' bytes')

$r = 0; $g = 0; $b = 0; $n = 0
for ($y = 0; $y -lt 64; $y++) {
	for ($x = 0; $x -lt 64; $x++) {
		$p = $scaled.GetPixel($x, $y)
		$max = [Math]::Max([Math]::Max($p.R, $p.G), $p.B)
		$min = [Math]::Min([Math]::Min($p.R, $p.G), $p.B)
		if (($max - $min) -gt 40 -and $max -gt 60) { $r += $p.R; $g += $p.G; $b += $p.B; $n++ }
	}
}
if ($n -eq 0) { Write-Output 'accent   : none' } else {
	$r = [int]($r / $n); $g = [int]($g / $n); $b = [int]($b / $n)
	Write-Output ('accent   : ' + ('{0:x2}{1:x2}{2:x2}' -f $r, $g, $b) + ' from ' + $n + '/' + (64 * 64))
}
$scaled.Dispose()
