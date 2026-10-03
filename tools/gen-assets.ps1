# Generates every Ancient Castles texture and JSON file: the sealed vault's textures, model, and block states; loot
# tables; the castle structure, where it spawns and how often; names; and the mod icon.
# Usage: powershell -ExecutionPolicy Bypass -File tools\gen-assets.ps1
$ErrorActionPreference = 'Stop'
trap { Write-Host "Error: $_"; exit 1 }
Add-Type -AssemblyName System.Drawing

$root = Join-Path $PSScriptRoot '..\src\main\resources'
$assets = Join-Path $root 'assets\ancientcastles'
$data = Join-Path $root 'data\ancientcastles'
$tex = Join-Path $assets 'textures'
$utf8 = New-Object System.Text.UTF8Encoding($false)

# Everything under these is generated, so start clean and leave nothing behind from older versions.
foreach ($dir in @('blockstates', 'items', 'lang', 'models', 'textures')) { Remove-Item -Recurse -Force (Join-Path $assets $dir) -ErrorAction SilentlyContinue }
Remove-Item -Recurse -Force $data -ErrorAction SilentlyContinue

function Write-Json($path, $text) {
	$dir = Split-Path $path
	if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
	[System.IO.File]::WriteAllText($path, ($text.Trim() -replace "`r`n", "`n") + "`n", $utf8)
}

function Save-Bitmap($bmp, $path) {
	$dir = Split-Path $path
	if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }
	$bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
	$bmp.Dispose()
}

# 'rrggbb' or 'aarrggbb'
function Color($hex) {
	if ($hex.Length -eq 6) { $hex = 'ff' + $hex }
	return [System.Drawing.Color]::FromArgb([Convert]::ToInt32($hex, 16))
}

function New-Bitmap($w, $h) { return New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb) }

function Fill($bmp, $x0, $y0, $x1, $y1, $hex) {
	for ($y = $y0; $y -le $y1; $y++) { for ($x = $x0; $x -le $x1; $x++) { $bmp.SetPixel($x, $y, (Color $hex)) } }
}

# Dark speckled stone with a gold frame.
function Vault-Base($bmp, $seed) {
	$rand = New-Object System.Random $seed
	for ($y = 0; $y -lt 16; $y++) {
		for ($x = 0; $x -lt 16; $x++) {
			$roll = $rand.NextDouble()
			$hex = if ($x -eq 0 -or $y -eq 0 -or $x -eq 15 -or $y -eq 15) { 'c99a2e' } elseif ($roll -lt 0.15) { '4a4650' } elseif ($roll -lt 0.3) { '2a272f' } else { '38343e' }
			$bmp.SetPixel($x, $y, (Color $hex))
		}
	}
	foreach ($p in @(@(0, 0), @(15, 0), @(0, 15), @(15, 15))) { $bmp.SetPixel($p[0], $p[1], (Color 'f2d36b')) }
}

# ---- Textures ----
# Front: a gold seal ring around a keyhole.
$bmp = New-Bitmap 16 16
Vault-Base $bmp 1
for ($y = 3; $y -le 12; $y++) { for ($x = 3; $x -le 12; $x++) {
	$d = [Math]::Sqrt([Math]::Pow($x - 7.5, 2) + [Math]::Pow($y - 7.5, 2))
	if ($d -ge 3.4 -and $d -le 4.6) { $bmp.SetPixel($x, $y, (Color $(if ($x + $y -lt 15) { 'f2d36b' } else { 'a87b1f' }))) }
} }
Fill $bmp 7 6 8 7 '0d0b10'; Fill $bmp 7 8 8 9 '0d0b10'; $bmp.SetPixel(7, 10, (Color '0d0b10')); $bmp.SetPixel(8, 10, (Color '0d0b10'))
Save-Bitmap $bmp (Join-Path $tex 'block\sealed_vault_front.png')

# Sides: two gold bands with rivets.
$bmp = New-Bitmap 16 16
Vault-Base $bmp 2
Fill $bmp 1 4 14 4 'a87b1f'; Fill $bmp 1 11 14 11 'a87b1f'
foreach ($x in @(3, 7, 11)) { $bmp.SetPixel($x, 4, (Color 'f2d36b')); $bmp.SetPixel($x + 1, 11, (Color 'f2d36b')) }
Save-Bitmap $bmp (Join-Path $tex 'block\sealed_vault_side.png')

# Top: a gold cross.
$bmp = New-Bitmap 16 16
Vault-Base $bmp 3
Fill $bmp 7 2 8 13 'a87b1f'; Fill $bmp 2 7 13 8 'a87b1f'; Fill $bmp 6 6 9 9 'f2d36b'
Save-Bitmap $bmp (Join-Path $tex 'block\sealed_vault_top.png')

# The mod icon: a castle on a green hill over a moat, against the sky.
$bmp = New-Bitmap 128 128
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear((Color '7fb6e8'))
$g.FillRectangle((New-Object System.Drawing.SolidBrush (Color '3a6fb0')), 0, 108, 128, 20)
$g.FillEllipse((New-Object System.Drawing.SolidBrush (Color '5a9e3c')), -20, 78, 168, 60)
$stone = New-Object System.Drawing.SolidBrush (Color '8a8a8a')
$dark = New-Object System.Drawing.SolidBrush (Color '5e5e5e')
$g.FillRectangle($stone, 30, 58, 68, 30)
$g.FillRectangle($dark, 20, 40, 18, 48); $g.FillRectangle($dark, 90, 40, 18, 48)
$g.FillRectangle($stone, 50, 28, 28, 40)
foreach ($x in @(20, 30, 90, 100)) { $g.FillRectangle($dark, $x, 34, 8, 6) }
foreach ($x in @(50, 60, 70)) { $g.FillRectangle($stone, $x, 22, 8, 6) }
foreach ($x in @(30, 42, 54, 66, 78, 90)) { $g.FillRectangle($stone, $x, 52, 8, 6) }
$g.FillRectangle((New-Object System.Drawing.SolidBrush (Color '2b1e14')), 58, 72, 12, 16)
$g.FillRectangle((New-Object System.Drawing.SolidBrush (Color 'c99a2e')), 61, 38, 6, 8)
$g.FillPolygon((New-Object System.Drawing.SolidBrush (Color 'b3261e')), [System.Drawing.Point[]]@((New-Object System.Drawing.Point 64, 6), (New-Object System.Drawing.Point 80, 11), (New-Object System.Drawing.Point 64, 16)))
$g.FillRectangle($dark, 63, 6, 2, 16)
$g.Dispose()
Save-Bitmap $bmp (Join-Path $assets 'icon.png')

# ---- Sealed vault: one model, turned to face the throne room ----
Write-Json (Join-Path $assets 'models\block\sealed_vault.json') @'
{
	"parent": "minecraft:block/orientable",
	"textures": {
		"front": "ancientcastles:block/sealed_vault_front",
		"side": "ancientcastles:block/sealed_vault_side",
		"top": "ancientcastles:block/sealed_vault_top"
	}
}
'@
$themes = @('plains', 'taiga', 'snowy', 'desert', 'badlands', 'jungle', 'swamp', 'dark_forest', 'savanna', 'cherry')
$turns = @{ 'north' = 0; 'east' = 90; 'south' = 180; 'west' = 270 }
$variants = @()
foreach ($facing in @('north', 'east', 'south', 'west')) {
	foreach ($theme in $themes) {
		$y = $turns[$facing]
		$rotation = if ($y -eq 0) { '' } else { ", `"y`": $y" }
		$variants += "`t`t`"facing=$facing,theme=$theme`": { `"model`": `"ancientcastles:block/sealed_vault`"$rotation }"
	}
}
Write-Json (Join-Path $assets 'blockstates\sealed_vault.json') ("{`n`t`"variants`": {`n" + ($variants -join ",`n") + "`n`t}`n}")
Write-Json (Join-Path $assets 'items\sealed_vault.json') '{ "model": { "type": "minecraft:model", "model": "ancientcastles:block/sealed_vault" } }'

# ---- Loot ----
# One entry: an item, with an optional count range and optional extra modifier (raw JSON).
function Entry($item, $weight, $min = 0, $max = 0, $extra = '') {
	$mods = @()
	if ($max -gt 0) { $mods += "{ `"type`": `"minecraft:set_count`", `"count`": { `"type`": `"minecraft:uniform`", `"min`": $min, `"max`": $max } }" }
	if ($extra) { $mods += $extra }
	$modifier = if ($mods.Count -eq 0) { '' } elseif ($mods.Count -eq 1) { ", `"modifier`": $($mods[0])" } else { ", `"modifier`": [ $($mods -join ', ') ]" }
	return "{ `"type`": `"minecraft:item`", `"name`": `"minecraft:$item`", `"weight`": $weight$modifier }"
}
function Pool($minRolls, $maxRolls, $entries) {
	return "{ `"rolls`": { `"type`": `"minecraft:uniform`", `"min`": $minRolls, `"max`": $maxRolls }, `"entries`": [`n`t`t`t`t" + ($entries -join ",`n`t`t`t`t") + "`n`t`t`t] }"
}
function Write-Loot($name, $pools) {
	Write-Json (Join-Path $data "loot_table\chests\$name.json") @"
{
	"type": "minecraft:chest",
	"pools": [
		$($pools -join ",`n`t`t")
	],
	"random_sequence": "ancientcastles:chests/$name"
}
"@
}
$randomEnchant = '{ "type": "minecraft:enchant_randomly", "options": "#minecraft:on_random_loot" }'
function Levels($min, $max) { return "{ `"type`": `"minecraft:enchant_with_levels`", `"levels`": { `"type`": `"minecraft:uniform`", `"min`": $min, `"max`": $max }, `"options`": `"#minecraft:on_random_loot`" }" }

Write-Loot 'castle_storage' @(
	(Pool 3 6 @(
		(Entry 'bread' 15 1 4), (Entry 'cooked_beef' 10 1 3), (Entry 'apple' 10 1 3), (Entry 'iron_ingot' 12 1 5),
		(Entry 'gold_ingot' 6 1 3), (Entry 'coal' 10 2 8), (Entry 'arrow' 8 4 12), (Entry 'leather' 6 1 4),
		(Entry 'torch' 6 4 12), (Entry 'emerald' 4 1 3), (Entry 'saddle' 2), (Entry 'name_tag' 2), (Entry 'golden_apple' 2))),
	(Pool 0 1 @((Entry 'book' 5 0 0 $randomEnchant), (Entry 'iron_pickaxe' 4), (Entry 'lead' 3), (Entry 'bucket' 3)))
)
Write-Loot 'castle_armory' @(
	(Pool 2 4 @(
		(Entry 'iron_sword' 10), (Entry 'iron_helmet' 8), (Entry 'iron_chestplate' 6), (Entry 'iron_leggings' 6),
		(Entry 'iron_boots' 8), (Entry 'shield' 8), (Entry 'bow' 8), (Entry 'crossbow' 6), (Entry 'arrow' 12 8 24),
		(Entry 'chainmail_chestplate' 4), (Entry 'diamond_sword' 2 0 0 (Levels 5 15)))),
	(Pool 1 1 @((Entry 'iron_ingot' 1 2 6)))
)
Write-Loot 'castle_dungeon' @(
	(Pool 3 6 @(
		(Entry 'bone' 15 2 8), (Entry 'rotten_flesh' 12 2 8), (Entry 'string' 10 2 6), (Entry 'gunpowder' 8 1 4),
		(Entry 'iron_ingot' 10 1 4), (Entry 'gold_ingot' 8 1 4), (Entry 'redstone' 6 2 8), (Entry 'lapis_lazuli' 6 2 8),
		(Entry 'diamond' 3 1 2), (Entry 'emerald' 4 1 3), (Entry 'iron_bars' 4 2 6))),
	(Pool 1 2 @(
		(Entry 'book' 6 0 0 $randomEnchant), (Entry 'golden_apple' 4), (Entry 'saddle' 4), (Entry 'iron_horse_armor' 4),
		(Entry 'golden_horse_armor' 3), (Entry 'name_tag' 3), (Entry 'music_disc_13' 2), (Entry 'music_disc_cat' 2),
		(Entry 'experience_bottle' 4 2 6)))
)
Write-Loot 'castle_treasure' @(
	(Pool 4 7 @(
		(Entry 'diamond' 10 2 6), (Entry 'gold_ingot' 12 4 12), (Entry 'emerald' 10 3 10), (Entry 'golden_apple' 10 1 3),
		(Entry 'enchanted_golden_apple' 2), (Entry 'book' 8 0 0 (Levels 20 39)), (Entry 'diamond_sword' 4 0 0 (Levels 20 39)),
		(Entry 'diamond_chestplate' 4 0 0 (Levels 20 39)), (Entry 'netherite_scrap' 3 1 2), (Entry 'experience_bottle' 8 4 12),
		(Entry 'totem_of_undying' 2), (Entry 'name_tag' 4), (Entry 'diamond_horse_armor' 3))),
	(Pool 1 1 @((Entry 'gold_block' 1)))
)

# ---- Worldgen: castles in about 1 of every 40x40-chunk region, where the ground is flat enough ----
Write-Json (Join-Path $data 'worldgen\structure\castle.json') @'
{
	"type": "ancientcastles:castle",
	"biomes": "#ancientcastles:has_structure/castle",
	"step": "surface_structures",
	"spawn_overrides": {},
	"terrain_adaptation": "none"
}
'@
Write-Json (Join-Path $data 'worldgen\structure_set\castles.json') @'
{
	"placement": {
		"type": "minecraft:random_spread",
		"salt": 20261003,
		"spacing": 40,
		"separation": 16
	},
	"structures": [
		{ "structure": "ancientcastles:castle", "weight": 1 }
	]
}
'@
$biomes = @('plains', 'sunflower_plains', 'meadow', 'forest', 'flower_forest', 'birch_forest', 'old_growth_birch_forest',
	'taiga', 'old_growth_pine_taiga', 'old_growth_spruce_taiga', 'snowy_taiga', 'snowy_plains', 'ice_spikes',
	'desert', 'badlands', 'wooded_badlands', 'eroded_badlands', 'jungle', 'sparse_jungle', 'bamboo_jungle',
	'swamp', 'mangrove_swamp', 'dark_forest', 'pale_garden', 'savanna', 'savanna_plateau', 'cherry_grove')
Write-Json (Join-Path $data 'tags\worldgen\biome\has_structure\castle.json') ("{ `"values`": [ " + (($biomes | ForEach-Object { "`"minecraft:$_`"" }) -join ', ') + " ] }")

# ---- Names ----
Write-Json (Join-Path $assets 'lang\en_us.json') @'
{
	"creativeTab.ancientcastles": "Ancient Castles",
	"block.ancientcastles.sealed_vault": "Sealed Vault",
	"boss.ancientcastles.warlord": "The Warlord",
	"boss.ancientcastles.pillager_captain": "Pillager Captain",
	"boss.ancientcastles.frost_lord": "The Frost Lord",
	"boss.ancientcastles.husk_king": "The Husk King",
	"boss.ancientcastles.bone_baron": "The Bone Baron",
	"boss.ancientcastles.jungle_tyrant": "The Jungle Tyrant",
	"boss.ancientcastles.bog_king": "The Bog King",
	"boss.ancientcastles.dark_sorcerer": "The Dark Sorcerer",
	"boss.ancientcastles.ashen_knight": "The Ashen Knight",
	"boss.ancientcastles.blossom_witch": "The Blossom Witch",
	"message.ancientcastles.sealed": "Sealed. Defeat this castle's lord to open it.",
	"message.ancientcastles.awakens": "%s awakens!",
	"message.ancientcastles.unsealed": "The vault is unsealed!"
}
'@

Write-Host "Generated the sealed vault, loot tables, castle worldgen, names, and icon."
