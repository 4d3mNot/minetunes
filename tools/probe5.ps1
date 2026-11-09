$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
function Await-Op($op, $type) { $m = $asTaskGeneric.MakeGenericMethod($type); $t = $m.Invoke($null, @($op)); $t.Wait(-1) | Out-Null; $t.Result }
[void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]
$mgr = Await-Op ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$tl = $mgr.GetCurrentSession().GetTimelineProperties()
$tl | Get-Member -MemberType Property | ForEach-Object { "{0} :: {1}" -f $_.Name, $_.Definition }
Write-Output "--- values ---"
Write-Output ("Start=" + $tl.StartTime.TotalSeconds + " Pos=" + $tl.Position.TotalSeconds + " End=" + $tl.EndTime.TotalSeconds)
Write-Output ("LastUpdated raw = [" + $tl.LastUpdatedTimestamp + "]")
