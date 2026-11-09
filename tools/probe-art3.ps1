# Last attempt at the art pipeline. PowerShell's own binder refuses to convert an
# opaque __ComObject into a WinRT interface parameter, but reflection's default binder
# performs a real QueryInterface. So: invoke CreateAsync through MethodInfo.
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

function Await-OpTyped($op, $methodReturnType) {
	$ga = $methodReturnType.GetGenericArguments()
	if ($ga.Count -eq 1) {
		# IAsyncOperation<T>, the common shape.
		return (Await-Op $op $ga[0])
	}

	$task = $op2.MakeGenericMethod($ga[0], $ga[1]).Invoke($null, @($op))
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

$mgr = Await-Op ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$props = Await-Op ($mgr.GetCurrentSession().TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
$raw = Await-Op ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])

$create = ([Windows.Graphics.Imaging.BitmapDecoder].GetMethods($flags) |
	Where-Object { $_.Name -eq 'CreateAsync' -and $_.GetParameters().Count -eq 1 -and
		$_.GetParameters()[0].ParameterType.Name -eq 'IRandomAccessStream' })[0]
Write-Output ('CreateAsync returns: ' + $create.ReturnType.FullName)

$argBox = New-Object 'object[]' 1
$argBox[0] = $raw
try {
	$asyncOp = $create.Invoke($null, $argBox)
	Write-Output ('invoked ok, op type: ' + $asyncOp.GetType().FullName)
	$decoder = Await-OpTyped $asyncOp $create.ReturnType
	Write-Output ('decoder: ' + $decoder.DecoderName + ' ' + $decoder.OrientedPixelWidth + 'x' + $decoder.OrientedPixelHeight)
	$bmp = Await-Op ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
	Write-Output ('bitmap : ' + $bmp.PixelWidth + 'x' + $bmp.PixelHeight + ' ' + $bmp.BitmapPixelFormat)
	$bytes = New-Object 'byte[]' ($bmp.PixelWidth * $bmp.PixelHeight * 4)
	# Same binder lesson as CreateAsync: hand the byte[] over through reflection.
	$copyMethod = ([Windows.Graphics.Imaging.SoftwareBitmap].GetMethods() |
		Where-Object { $_.Name -eq 'CopyToBuffer' -and $_.GetParameters().Count -eq 1 })[0]
	$copyBox = New-Object 'object[]' 1
	$copyBox[0] = $bytes
	$copyMethod.Invoke($bmp, $copyBox) | Out-Null
	Write-Output ('buffer : ' + $bytes.Length + ' first px BGRA=' + ($bytes[0..3] -join ','))

	Add-Type -AssemblyName System.Drawing
	$stride = $copy.PixelWidth * 4
	$handle = [System.Runtime.InteropServices.GCHandle]::Alloc($bytes, [System.Runtime.InteropServices.GCHandleType]::Pinned)
	try {
		$src = New-Object System.Drawing.Bitmap($copy.PixelWidth, $copy.PixelHeight, $stride, [System.Drawing.Imaging.PixelFormat]::FormatBgra32, $handle.AddrOfPinnedObject())
		$scaled = New-Object System.Drawing.Bitmap 64, 64
		$gfx = [System.Drawing.Graphics]::FromImage($scaled)
		$gfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
		$gfx.DrawImage($src, 0, 0, 64, 64)
		$gfx.Dispose()
		$out = Join-Path $env:TEMP 'musicdeck\probe-art.png'
		New-Item -ItemType Directory -Force -Path (Split-Path $out) | Out-Null
		$scaled.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
		Write-Output ('png    : ' + $out + ' ' + (Get-Item $out).Length + ' bytes')

		$r = 0; $g = 0; $b = 0; $n = 0
		for ($y = 0; $y -lt 64; $y++) {
			for ($x = 0; $x -lt 64; $x++) {
				$p = $scaled.GetPixel($x, $y)
				$max = [Math]::Max([Math]::Max($p.R, $p.G), $p.B)
				$min = [Math]::Min([Math]::Min($p.R, $p.G), $p.B)
				if (($max - $min) -gt 40 -and $max -gt 60) { $r += $p.R; $g += $p.G; $b += $p.B; $n++ }
			}
		}
		if ($n -eq 0) { Write-Output 'accent : none' } else {
			Write-Output ('accent : ' + ('{0:x2}{1:x2}{2:x2}' -f [int]($r / $n), [int]($g / $n), [int]($b / $n)) + ' from ' + $n + '/' + 4096)
		}
		$scaled.Dispose(); $src.Dispose()
	} finally {
		$handle.Free()
	}
} catch {
	Write-Output ('THREW  : ' + $_.Exception.GetType().Name + ' :: ' + $_.Exception.Message)
	if ($_.Exception.InnerException) { Write-Output ('INNER  : ' + $_.Exception.InnerException.Message) }
}
