param([string] $Jar = (Join-Path $PSScriptRoot '..\build\libs\minetunes-1.0.0.jar'))
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($Jar)
$names = $zip.Entries | ForEach-Object { $_.FullName }
"jar      : $Jar"
"classes  : " + (($names | Where-Object { $_ -like '*musicdeck/client/*.class' } | Measure-Object).Count)
$names | Where-Object { $_ -like '*musicdeck/client/*.class' } | ForEach-Object { "  " + (Split-Path $_ -Leaf) }

# Mod Menu is a compile-only dependency: its classes must be referenced, never carried.
$borrowed = $names | Where-Object { $_ -like '*terraformersmc*' }
"modmenu  : " + ($(if ($borrowed) { "BUNDLED " + ($borrowed -join ', ') } else { "not bundled, only compiled against" }))
$modmenuEntry = $names | Where-Object { $_ -like '*MusicDeckModMenu.class' }
"entry    : " + ($(if ($modmenuEntry) { "MusicDeckModMenu.class present" } else { "MISSING - Mod Menu has no door" }))

$ps = $zip.Entries | Where-Object { $_.FullName -like '*smtc-bridge.ps1' }
if ($null -eq $ps) { "ps1      : MISSING" } else {
	$reader = New-Object System.IO.StreamReader ($ps.Open())
	$text = $reader.ReadToEnd(); $reader.Close()
	"ps1      : " + $ps.FullName + " " + $text.Length + " chars"
	"art      : Update-Art=" + ($text -match 'function Update-Art') + " AsStream=" + ($text -match 'AsStream') + " ArtPath=" + ($text -match '\$ArtPath')
}

function Read-Entry($entry) {
	$reader = New-Object System.IO.StreamReader ($entry.Open())
	$text = $reader.ReadToEnd(); $reader.Close()
	return $text
}

$manifest = $zip.Entries | Where-Object { $_.FullName -eq 'fabric.mod.json' }
$mod = Read-Entry $manifest
"manifest : name=" + ($mod -match '"name":\s*"MineTunes"') + " id=" + ($mod -match '"id":\s*"musicdeck"') + " modmenu-entrypoint=" + ($mod -match '"modmenu":\s*\[') `
	+ " hard-depends-on-modmenu=" + ($mod -match '"depends":\s*\{[^}]*modmenu')
$stale = $names | Where-Object { $_ -like '*spotify*' }
"oldname  : " + ($(if ($stale) { "STILL PRESENT " + ($stale -join ', ') } else { "nothing left under the old name" }))

# The icon only counts if it is inside the jar at the exact path the manifest names: a
# loader that cannot find it falls back to the placeholder block without saying so.
$iconMatch = [regex]::Match($mod, '"icon":\s*"([^"]+)"')
if (-not $iconMatch.Success) {
	"icon     : manifest declares none"
} else {
	$iconPath = $iconMatch.Groups[1].Value
	$entry = $zip.Entries | Where-Object { $_.FullName -eq $iconPath }
	if ($null -eq $entry) {
		"icon     : MISSING from the jar at $iconPath"
	} else {
		$stream = $entry.Open()
		$buffer = New-Object System.IO.MemoryStream
		$stream.CopyTo($buffer)
		$stream.Close()
		$bytes = $buffer.ToArray()
		$wide = [int] $bytes[16] * 16777216 + [int] $bytes[17] * 65536 + [int] $bytes[18] * 256 + [int] $bytes[19]
		$tall = [int] $bytes[20] * 16777216 + [int] $bytes[21] * 65536 + [int] $bytes[22] * 256 + [int] $bytes[23]
		$onDisk = Join-Path (Split-Path -Parent $PSScriptRoot) ("src\main\resources\" + ($iconPath -replace '/', '\'))
		$same = (Test-Path $onDisk) -and ((Get-Item $onDisk).Length -eq $bytes.Length)
		"icon     : $iconPath " + $wide + "x" + $tall + " " + $bytes.Length + " bytes, matches src=" + $same
	}
}

$lang = $zip.Entries | Where-Object { $_.FullName -like '*assets/musicdeck/lang/en_us.json' }
if ($null -eq $lang) { "lang     : MISSING" } else {
	$text = Read-Entry $lang
	$keys = 0
	foreach ($key in @('key.category.musicdeck.main', 'musicdeck.keys.title', 'musicdeck.keys.press',
		'musicdeck.keys.back', 'musicdeck.keys.all', 'musicdeck.keys.hint', 'musicdeck.settings.keys',
		'musicdeck.setting.deck')) {
		if ($text -notmatch ('"' + $key + '"')) { "lang     : MISSING " + $key } else { $keys++ }
	}

	"lang     : " + $keys + " of 8 checked keys, " + $text.Length + " chars"
}
$art = $names | Where-Object { $_ -like '*textures/gui*' }
"art-entry: " + ($(if ($art) { $art -join ', ' } else { "(none, generated at runtime)" }))
$zip.Dispose()
