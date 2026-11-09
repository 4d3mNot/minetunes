# Second gate for album art: the OpenReadAsync result arrives as a __ComObject whose
# inherited IRandomAccessStream members (Size, GetInputStreamAt) PowerShell will not
# bind. Can a cast to the projected interface fix that?
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
[void][Windows.Storage.Streams.IInputStream, Windows.Storage, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]

$mgr = Await-Op ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$props = Await-Op ($mgr.GetCurrentSession().TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])

$raw = Await-Op ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
Write-Output ('raw type      : ' + $raw.GetType().FullName)
Write-Output ('raw .Size     : [' + $raw.Size + ']')
Write-Output ('raw .ContentType: [' + $raw.ContentType + ']')

# PowerShell will not cast a __ComObject to a projected interface, so QueryInterface by
# hand and ask the marshaller for a typed wrapper of the parent interface.
$unk = [System.Runtime.InteropServices.Marshal]::GetIUnknownForObject($raw)
$iid = [Windows.Storage.Streams.IRandomAccessStream].GUID
Write-Output ('iid           : ' + $iid)
$ptr = [System.Runtime.InteropServices.Marshal]::QueryInterface($raw, [ref]$iid)
$ras = [System.Runtime.InteropServices.Marshal]::GetTypedObjectForIUnknown($ptr, [Windows.Storage.Streams.IRandomAccessStream])
Write-Output ('QI type       : ' + $ras.GetType().FullName)
Write-Output ('QI .Size      : [' + $ras.Size + ']')
Write-Output ('QI .ContentTyp: [' + $ras.ContentType + ']')

$in = $ras.GetInputStreamAt(0)
Write-Output ('input stream  : ' + $in.GetType().FullName)
$dr = New-Object Windows.Storage.Streams.DataReader($in)
Write-Output ('DataReader ok : ' + ($null -ne $dr))
