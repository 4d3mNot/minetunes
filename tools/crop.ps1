# Crop and enlarge a strip of the preview so a glyph can actually be read.
param([string]$In='build-art\hud-preview.png',[string]$Out='build-art\crop.png',
      [int]$X=0,[int]$Y=0,[int]$W=600,[int]$H=60,[int]$Zoom=2)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$src=[System.Drawing.Image]::FromFile((Join-Path (Get-Location) $In))
$part=New-Object System.Drawing.Bitmap $W,$H
$pg=[System.Drawing.Graphics]::FromImage($part);$pg.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor;$pg.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half;$pg.DrawImage($src,[System.Drawing.Rectangle]::new(0,0,$W,$H),[System.Drawing.Rectangle]::new($X,$Y,$W,$H),[System.Drawing.GraphicsUnit]::Pixel);$pg.Dispose()
$big=New-Object System.Drawing.Bitmap ($W*$Zoom),($H*$Zoom)
$g=[System.Drawing.Graphics]::FromImage($big)
$g.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
$g.DrawImage($part,0,0,$W*$Zoom,$H*$Zoom)
$g.Dispose();$part.Dispose();$src.Dispose()
$big.Save((Join-Path (Get-Location) $Out),[System.Drawing.Imaging.ImageFormat]::Png)
$big.Dispose()
Write-Output ("crop " + $Out + " " + $W + "x" + $H + " from " + $X + "," + $Y)
