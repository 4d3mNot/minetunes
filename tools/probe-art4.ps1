# Two ways out of the opaque stream: (a) find the CopyToBuffer overload that really
# takes a byte[], (b) hand the WinRT stream to .NET's AsStream() and let System.Drawing
# decode Spotify's own JPEG/PNG bytes. (b) is the one the mod should ship if it works,
# because it skips the SoftwareBitmap round-trip entirely.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
Add-Type -AssemblyName System.Drawing

$flags = [System.Reflection.BindingFlags]'Public,Static'
$instFlags = [System.Reflection.BindingFlags]'Public,Instance'
$ops = [System.WindowsRuntimeSystemExtensions].GetMethods($flags) | Where-Object { $_.Name -eq 'AsTask' }
$op1 = ($ops | Where-Object { $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]

function Await-Op($op, $type) {
	$task = $op1.MakeGenericMethod($type).Invoke($null, @($op))
	$task.Wait(-1) | Out-Null
	return $task.Result
}

function Invoke-One($target, $method, $arg) {
	$box = New-Object 'object[]' 1
	$box[0] = $arg
	return $method.Invoke($target, $box)
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage, ContentType = WindowsRuntime]
[void][Windows.Graphics.Imaging.SoftwareBitmap, Windows.Graphics.Imaging, ContentType = WindowsRuntime]

$mgr = Await-Op ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$props = Await-Op ($mgr.GetCurrentSession().TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
$raw = Await-Op ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
Write-Output '--- CopyToBuffer overloads ---'
foreach ($m in [Windows.Graphics.Imaging.SoftwareBitmap].GetMethods($instFlags)) {
	if ($m.Name -eq 'CopyToBuffer') {
		Write-Output ('  ' + (($m.GetParameters() | ForEach-Object { $_.ParameterType.Name }) -join ', '))
	}
}

Write-Output '--- AsStream candidates ---'
# [Type]::GetType needs the fusion-correct assembly string, which this facade does not
# expose, so look inside the assembly PowerShell already loaded instead.
$asm = [AppDomain]::CurrentDomain.GetAssemblies() |
	Where-Object { $_.GetName().Name -eq 'System.Runtime.WindowsRuntime' } | Select-Object -First 1
Write-Output ('  assembly: ' + $asm.GetName().Name + ' / ' + $asm.Location)
$streamExt = $asm.GetType('System.IO.WindowsRuntimeStreamExtensions', $false)
if ($null -eq $streamExt) {
	Write-Output '  types in assembly:'
	$asm.GetTypes() | ForEach-Object { Write-Output ('    ' + $_.FullName) }
	exit 0
}
foreach ($m in $streamExt.GetMethods($flags)) {
	if ($m.Name -eq 'AsStream') {
		Write-Output ('  AsStream(' + (($m.GetParameters() | ForEach-Object { $_.ParameterType.Name }) -join ', ') + ') -> ' + $m.ReturnType.Name)
	}
}

# Try (b): AsStream(IRandomAccessStream) -> System.IO.Stream -> Image.FromStream.
$asStream = ($streamExt.GetMethods($flags) |
	Where-Object { $_.Name -eq 'AsStream' -and $_.GetParameters().Count -eq 1 -and
		$_.GetParameters()[0].ParameterType.Name -eq 'IRandomAccessStream' })[0]
Write-Output ('AsStream returns: ' + $asStream.ReturnType.FullName)
$out_dir = Join-Path $env:TEMP 'musicdeck'
New-Item -ItemType Directory -Force -Path $out_dir | Out-Null

try {
	$netStream = Invoke-One $null $asStream $raw
	Write-Output ('netStream: ' + $netStream.GetType().FullName + ' canRead=' + $netStream.CanRead + ' canSeek=' + $netStream.CanSeek)
	# GDI+ wants a seekable source it can re-read, so buffer the whole thumbnail first.
	$mem = New-Object System.IO.MemoryStream
	$netStream.CopyTo($mem)
	$mem.Position = 0
	Write-Output ('buffered : ' + $mem.Length + ' bytes')
	$img = [System.Drawing.Image]::FromStream($mem)
	Write-Output ('decoded  : ' + $img.GetType().Name + ' ' + $img.Width + 'x' + $img.Height + ' fmt=' + $img.RawFormat)

	$scaled = New-Object System.Drawing.Bitmap 64, 64
	$gfx = [System.Drawing.Graphics]::FromImage($scaled)
	$gfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
	$gfx.DrawImage($img, 0, 0, 64, 64)
	$gfx.Dispose()
	$png = Join-Path $out_dir 'probe-art.png'
	$scaled.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)
	Write-Output ('png      : ' + $png + ' ' + (Get-Item $png).Length + ' bytes')

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
		Write-Output ('accent   : ' + ('{0:x2}{1:x2}{2:x2}' -f [int]($r / $n), [int]($g / $n), [int]($b / $n)) + ' from ' + $n + '/4096')
	}
	$scaled.Dispose(); $img.Dispose(); $netStream.Dispose()
} catch {
	Write-Output ('THREW    : ' + $_.Exception.GetType().Name + ' :: ' + $_.Exception.Message)
	if ($_.Exception.InnerException) { Write-Output ('INNER    : ' + $_.Exception.InnerException.GetType().Name + ' :: ' + $_.Exception.InnerException.Message) }
}
