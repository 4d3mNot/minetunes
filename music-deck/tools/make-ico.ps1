param(
	[Parameter(Mandatory = $true)][string] $Source,
	[Parameter(Mandatory = $true)][string] $Out
)
# Neither shortcut works here. A PNG-compressed icon record is small, but GDI+'s managed
# Icon reader walks off the end of it and throws, and Bitmap.GetHicon() hands back a
# handle whose DIB decodes with the colours shredded. So the record is written by hand:
# a 32-bit BGRA bottom-up DIB plus its one-bit AND mask, which every reader accepts.
Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap $Source
if ($bmp.PixelFormat -ne [System.Drawing.Imaging.PixelFormat]::Format32bppArgb) {
	$argb = New-Object System.Drawing.Bitmap ($bmp.Width, $bmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
	$gfx = [System.Drawing.Graphics]::FromImage($argb)
	$gfx.DrawImage($bmp, 0, 0, $bmp.Width, $bmp.Height)
	$gfx.Dispose()
	$bmp.Dispose()
	$bmp = $argb
}

$wide = $bmp.Width
$tall = $bmp.Height
$rect = New-Object System.Drawing.Rectangle (0, 0, $wide, $tall)
$lock = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
	[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$rows = New-Object byte[] ($lock.Stride * $tall)
[System.Runtime.InteropServices.Marshal]::Copy($lock.Scan0, $rows, 0, $rows.Length)
$bmp.UnlockBits($lock)

$maskStride = [Math]::Ceiling($wide / 32) * 4
$pixelBytes = New-Object byte[] ($wide * $tall * 4)
$maskBytes = New-Object byte[] ($maskStride * $tall)
for ($y = 0; $y -lt $tall; $y++) {
	$dst = ($tall - 1 - $y) * $wide * 4          # a DIB is stored bottom-up
	$src = $y * $lock.Stride
	[Array]::Copy($rows, $src, $pixelBytes, $dst, $wide * 4)
	$maskRow = ($tall - 1 - $y) * $maskStride
	for ($x = 0; $x -lt $wide; $x++) {
		if ($rows[$src + $x * 4 + 3] -lt 128) {
			$maskBytes[$maskRow + [Math]::Floor($x / 8)] =
				$maskBytes[$maskRow + [Math]::Floor($x / 8)] -bor (128 -shr ($x % 8))
		}
	}
}
$bmp.Dispose()

$imageSize = 40 + $pixelBytes.Length + $maskBytes.Length
$memory = New-Object System.IO.MemoryStream
$bw = New-Object System.IO.BinaryWriter $memory
$bw.Write([uint16] 0); $bw.Write([uint16] 1); $bw.Write([uint16] 1)
$bw.Write([byte] $(if ($wide -ge 256) { 0 } else { $wide }))
$bw.Write([byte] $(if ($tall -ge 256) { 0 } else { $tall }))
$bw.Write([byte] 0); $bw.Write([byte] 0)
$bw.Write([uint16] 1); $bw.Write([uint16] 32)
$bw.Write([uint32] $imageSize); $bw.Write([uint32] 22)
$bw.Write([int32] 40); $bw.Write([int32] $wide); $bw.Write([int32] ($tall * 2))
$bw.Write([uint16] 1); $bw.Write([uint16] 32); $bw.Write([uint32] 0)
$bw.Write([uint32] $imageSize); $bw.Write([int32] 0); $bw.Write([int32] 0)
$bw.Write([int32] 0); $bw.Write([int32] 0)
$bw.Write($pixelBytes); $bw.Write($maskBytes)
$bw.Flush()
[System.IO.File]::WriteAllBytes($Out, $memory.ToArray())
$bw.Close()
"$Out : $wide x $tall, $((Get-Item $Out).Length) bytes"
