$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
  Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
                 $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, $type) { $m = $asTaskGeneric.MakeGenericMethod($type); $t = $m.Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties,Windows.Media.Control,ContentType=WindowsRuntime]
[void][Windows.Foundation.IAsyncAction,Windows.Foundation,ContentType=WindowsRuntime]
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$s = $mgr.GetCurrentSession()
$p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
Write-Output "TITLE=[$($p.Title)]"
Write-Output "BEFORE=$($s.GetPlaybackInfo().PlaybackStatus)"
$r = Await ($s.TryPauseAsync()) ([bool])
Start-Sleep -Milliseconds 400
Write-Output "PAUSE_RESULT=$r AFTER=$($s.GetPlaybackInfo().PlaybackStatus)"
$r2 = Await ($s.TryPlayAsync()) ([bool])
Start-Sleep -Milliseconds 400
Write-Output "PLAY_RESULT=$r2 RESTORED=$($s.GetPlaybackInfo().PlaybackStatus)"
$tl = $s.GetTimelineProperties()
Write-Output "POS=$([int]$tl.Position.TotalSeconds)"
