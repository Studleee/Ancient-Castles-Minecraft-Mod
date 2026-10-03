# Ancient Castles

A Fabric mod for Minecraft 26.3. Rare, weathered castles stand on hills ringed by water moats, built to match the
biome they're in. Walk into the throne room to wake the castle's lord; defeat it to unseal the treasure vault behind
the throne.

## Castles

Each castle is a raised hill with a moat around its foot and a bridge over it. A path climbs to the gatehouse. On top:

- A square curtain wall with buttresses, an overhanging parapet, and a wall walk, and a round tower with arrow slits
  at each corner.
- A gatehouse: two towers flanking an arched gate with a portcullis, and banners.
- The keep, two stories tall:
  - The throne hall, with pillars, chandeliers, stained glass, banners, a gold-trimmed dais with carpeted steps, the
    throne, and the **Sealed Vault** set behind it.
  - Upstairs, a library with an enchanting table, the lord's bed, and a dining table.
  - A lookout tower rising from the roof, flying the castle's flag.
  - Underneath, a dungeon with prison cells, a mob spawner, and loot.
- The courtyard grows with the castle's size: flower beds, a fountain, barracks, a storehouse, a blacksmith, market
  stalls, tree gardens, and (in large castles) stables with horses and a wheat plot.
- Torches and lanterns along the walls, towers, and path, and guards on the walls and at the gate.

Sizes vary: small (about 21 blocks across the walls), medium (31), and large (43). The walls are weathered with mossy
and cracked blocks, plus vines in leafy biomes.

## Themes and lords

| Biome | Built from | Lord |
| --- | --- | --- |
| Plains, forests, meadows | Stone bricks, oak | The Warlord (vindicator) |
| Taiga | Cobblestone, spruce | Pillager Captain |
| Snowy | Stone bricks, packed ice, spruce | The Frost Lord (stray) |
| Desert | Sandstone, birch | The Husk King |
| Badlands | Red sandstone, dark oak | The Bone Baron (skeleton) |
| Jungle | Mossy stone bricks, jungle wood | The Jungle Tyrant (zombie) |
| Swamp | Mud bricks, mangrove | The Bog King (bogged) |
| Dark forest, pale garden | Deepslate bricks, dark oak | The Dark Sorcerer (evoker) |
| Savanna | Bricks, acacia | The Ashen Knight (wither skeleton) |
| Cherry grove | Polished diorite, calcite, cherry | The Blossom Witch |

Guards and the dungeon spawner match the theme too: vindicators and pillagers, strays in the snow, husks in the
desert, bogged in the swamp, wither skeletons on the savanna, and so on.

Lords have extra health, armor, and damage, and they show a boss health bar. When one dies, its vault turns into a
treasure chest with diamonds, gold, emeralds, enchanted gear, golden apples, and more.

## Finding one

Castles appear in about one of every 40x40-chunk region, where the ground is flat and dry enough.

- `/locate structure ancientcastles:castle` finds the nearest one.
- `/place structure ancientcastles:castle` builds one where you stand, if the ground there is flat enough.

## Building

Needs JDK 25. Run `build.bat` (or `.\gradlew.bat build`); the jar is in `build\libs`. `run.bat` starts a dev client.
`tools\gen-assets.ps1` regenerates the textures, models, loot tables, and worldgen JSON.
