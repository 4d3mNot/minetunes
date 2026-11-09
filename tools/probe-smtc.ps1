$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
  Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
                 $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, $type) {
  $m = $asTaskGeneric.MakeGenericMethod($type)
  $t = $m.Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result
}
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$s = $mgr.GetCurrentSession()
if ($null -eq $s) { Write-Output 'NO_SESSION'; exit 0 }
Write-Output ("SOURCE=" + $s.SourceAppUserModelId)
Write-Output ("PLAYBACK=" + $s.GetPlaybackInfo().PlaybackStatus)
$p = $s.Properties
Write-Output ("TITLE=" + $p.Title)
Write-Output ("ARTIST=" + $p.Artist)
Write-Output ("ALBUM=" + $p.Album)
$tl = $s.GetTimelineProperties()
Write-Output ("POS=" + [int]$tl.Position.TotalSeconds + " END=" + [int]$tl.EndTime.TotalSeconds)
$c = $s.GetPlaybackInfo().Controls
Write-Output ("CTRL PlayPause=" + $c.PlayPause + " Next=" + $c.Next + " Prev=" + $c.Previous + " Shuffle=" + $c.Shuffle + " Repeat=" + $c.RepeatMode)
