# Third gate: enumerate what the marshaller actually offers, then try the short route
# of asking for a typed wrapper of the parent interface straight off the IUnknown.
$ErrorActionPreference = 'Stop'
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
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.IRandomAccessStream, Windows.Storage, ContentType = WindowsRuntime]

$mgr = Await-Op ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$props = Await-Op ($mgr.GetCurrentSession().TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
$raw = Await-Op ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])

'--- Marshal.QueryInterface overloads ---'
([System.Runtime.InteropServices.Marshal].GetMethods() | Where-Object { $_.Name -eq 'QueryInterface' -or $_.Name -eq 'GetTypedObjectForIUnknown' }) |
	ForEach-Object { $_.Name + '(' + (($_.GetParameters() | ForEach-Object { $_.ParameterType.Name + ' ' + $_.Name }) -join ', ') + ')' }

'--- direct typed wrapper off the IUnknown ---'
$unk = [System.Runtime.InteropServices.Marshal]::GetIUnknownForObject($raw)
try {
	$ras = [System.Runtime.InteropServices.Marshal]::GetTypedObjectForIUnknown($unk, [Windows.Storage.Streams.IRandomAccessStream])
	'typed   : ' + $ras.GetType().FullName
	'Size    : [' + $ras.Size + ']'
	'Content : [' + $ras.ContentType + ']'
	$in = $ras.GetInputStreamAt(0)
	'inType  : ' + $in.GetType().FullName
} catch {
	'THREW   : ' + $_.Exception.Message
}
