$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
  Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
                 $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, $type) { $m = $asTaskGeneric.MakeGenericMethod($type); $t = $m.Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackInfo,Windows.Media.Control,ContentType=WindowsRuntime]
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime]
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$s = $mgr.GetCurrentSession()
Write-Output "=== PLAYBACKINFO ==="; $s.GetPlaybackInfo() | Get-Member -MemberType Property | ForEach-Object { $_.Name } | Sort-Object | Out-String -Width 200
Write-Output "=== MEDIA PROPS (async) ==="
$p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
$p | Get-Member -MemberType Property | ForEach-Object { $_.Name } | Sort-Object | Out-String -Width 200
Write-Output "=== VALUES ==="
"Track=[$($p.Track)]  Artist=[$($p.Artist)]  AlbumTitle=[$($p.AlbumTitle)]"
"PlaybackType=$($p.PlaybackType)  Thumbnail=$($null -ne $p.Thumbnail)"
$pi = $s.GetPlaybackInfo()
"Status=$($pi.PlaybackStatus)  AutoRepeat=$($pi.AutoRepeatMode)  Shuffle=$($pi.ShuffleActive)"
"Rate=$($pi.PlaybackRate)  Position=$([int]$s.GetTimelineProperties().Position.TotalSeconds)/$([int]$s.GetTimelineProperties().EndTime.TotalSeconds)"
