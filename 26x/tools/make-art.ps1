param(
	[Parameter(Mandatory = $true)][string] $Source,
	[Parameter(Mandatory = $true)][string] $Out,
	[int] $Size = 0
)
Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Image]::FromFile($Source)
$dir = Split-Path -Parent $Out
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
if ($Size -le 0) {
	$src.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
} else {
	$bmp = New-Object System.Drawing.Bitmap ($Size, $Size)
	$gfx = [System.Drawing.Graphics]::FromImage($bmp)
	$gfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
	$gfx.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
	$gfx.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
	$gfx.DrawImage($src, 0, 0, $Size, $Size)
	$gfx.Dispose()
	$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
	$bmp.Dispose()
}
$src.Dispose()
"$([System.IO.Path]::GetFileName($Out)) : $((Get-Item $Out).Length) bytes"
