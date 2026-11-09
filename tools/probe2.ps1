$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() |
  Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
                 $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await($op, $type) { $m = $asTaskGeneric.MakeGenericMethod($type); $t = $m.Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$s = $mgr.GetCurrentSession()
Write-Output "=== SESSION ==="; $s | Get-Member -MemberType Property,Method | ForEach-Object { $_.Name } | Sort-Object | Out-String -Width 200
Write-Output "=== PROPERTIES ==="; $s.Properties | Get-Member -MemberType Property | ForEach-Object { $_.Name } | Sort-Object | Out-String -Width 200
Write-Output "=== CONTROLS ==="; $s.GetPlaybackInfo().Controls | Get-Member -MemberType Property | ForEach-Object { $_.Name } | Sort-Object | Out-String -Width 200
Write-Output "=== VALUES ==="
"Play=$($s.GetPlaybackInfo().Controls.Play) Pause=$($s.GetPlaybackInfo().Controls.Pause) Next=$($s.GetPlaybackInfo().Controls.Next) Prev=$($s.GetPlaybackInfo().Controls.Previous)"
"AlbumTitle=[$($s.Properties.AlbumTitle)] Track=[$($s.Properties.Track)] Artist=[$($s.Properties.Artist)]"
"Genre=[$($s.Properties.Genre)] Subtitle=[$($s.Properties.Subtitle)] ContentDesc=[$($s.Properties.ContentDescription)]"
