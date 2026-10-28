# Make a 48px album tile through the bridge's own resize maths, from an image already on
# disk. Used when nothing with artwork is playing, so the SMTC fetch cannot be exercised:
# this proves the resize and the size the game expects, not the thumbnail download.
param(
	[Parameter(Mandatory = $true)] [string] $Source,
	[string] $Out = 'build-art\art.png',
	[int] $Size = 48
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$image = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $Source))
try {
	$side = [Math]::Min($image.Width, $image.Height)
$crop = [System.Drawing.Rectangle]::new([int] (($image.Width - $side) / 2), [int] (($image.Height - $side) / 2), $side, $side)

$tile = [System.Drawing.Bitmap]::new($Size, $Size)
$gfx = [System.Drawing.Graphics]::FromImage($tile)
try {
	$gfx.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
	$gfx.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
	$gfx.DrawImage($image, [System.Drawing.Rectangle]::new(0, 0, $Size, $Size), $crop, [System.Drawing.GraphicsUnit]::Pixel)
} finally {
	$gfx.Dispose()
}

	$target = Join-Path (Get-Location) $Out
	$tile.Save($target + '.tmp', [System.Drawing.Imaging.ImageFormat]::Png)
	$tile.Dispose()
	Move-Item -LiteralPath ($target + '.tmp') -Destination $target -Force
	Write-Output ("tile " + $Size + "x" + $Size + " from " + $Source + " (" + $image.Width + "x" + $image.Height + ")")
	$image.Dispose()
} catch {
	$image.Dispose()
	throw
}
