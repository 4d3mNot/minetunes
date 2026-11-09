# What does the SMTC thumbnail look like before the bridge touches it?
#
# Prints the decoded size, pixel format and an alpha profile of the first and last rows,
# so a translucent or letterboxed tile can be blamed on the source rather than on the
# resize. Same await and stream-adapter dance the bridge uses, because PowerShell's own
# binder cannot pass the WinRT stream RCW.
param(
	[int] $Rows = 6
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Runtime.WindowsRuntime

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
	Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
		$_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]

function Await-Op($op, $type) {
	$task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
	$task.Wait(-1) | Out-Null
	return $task.Result
}

[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage, ContentType = WindowsRuntime]

$asm = [AppDomain]::CurrentDomain.GetAssemblies() |
	Where-Object { $_.GetName().Name -eq 'System.Runtime.WindowsRuntime' } | Select-Object -First 1
$streamType = $asm.GetType('System.IO.WindowsRuntimeStreamExtensions', $false)
$asStream = ($streamType.GetMethods() |
	Where-Object { $_.Name -eq 'AsStream' -and $_.GetParameters().Count -eq 1 -and
		$_.GetParameters()[0].ParameterType.Name -eq 'IRandomAccessStream' })[0]

$manager = Await-Op ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) `
	([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$session = $manager.GetCurrentSession()
if ($null -eq $session) {
	Write-Output 'NO-SESSION'
	exit 1
}

$props = Await-Op $session.TryGetMediaPropertiesAsync() `
	([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
Write-Output ("session=" + $session.SourceApp + "  " + $props.Title + " - " + $props.Artist)
if ($null -eq $props.Thumbnail) {
	Write-Output 'NO-THUMBNAIL'
	exit 1
}

$raw = Await-Op ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
$box = New-Object 'object[]' 1
$box[0] = $raw
$net = $asStream.Invoke($null, $box)
$buffer = New-Object System.IO.MemoryStream
$net.CopyTo($buffer)
Write-Output ("encoded bytes=" + $buffer.Length)
$buffer.Position = 0
$image = [System.Drawing.Image]::FromStream($buffer)
Write-Output ("decoded " + $image.Width + "x" + $image.Height + "  pixelFormat=" + $image.RawFormat.Guid +
	"  frameFormat=" + $image.PixelFormat)

$bitmap = New-Object System.Drawing.Bitmap $image
$top = [Math]::Min($Rows, $bitmap.Height)
foreach ($y in @(0 .. ($top - 1)) + @([Math]::Max($top, $bitmap.Height - $Rows) .. ($bitmap.Height - 1))) {
	$alpha = 0
	for ($x = 0; $x -lt $bitmap.Width; $x++) {
		$alpha += $bitmap.GetPixel($x, $y).A
	}

	Write-Output ("row " + $y + "  meanAlpha=" + [int] ($alpha / $bitmap.Width))
}

$bitmap.Dispose()
$image.Dispose()
$buffer.Dispose()
$net.Dispose()
