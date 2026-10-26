# Changelog

Versions are published to Modrinth and GitHub Releases by the `Release` workflow. Alpha versions can change world generation: start a new world after updating.

## [Unreleased]

- **New worlds start in late spring** (May), with the forest in full leaf: the built-in calendar starts there, and the modpack sets Serene Seasons to the same start (`starting_sub_season = 3`, 12 days per sub-season). Without the modpack, set these two values in `config/sereneseasons/seasons.toml`. Worlds of earlier versions without Serene Seasons move two sub-seasons forward once.
- **Wider forest transitions.** Fresh pine, mixed pine, mixed and oak-hornbeam forests now blend their tree species, lower layers and ground flora over about 50 blocks in the 1:1 world and 25 blocks in the gameplay world, following the shape of the border.
- **Stone closer to the surface in the lowlands.** New worlds have a thin loose cover: under the soil only a few blocks of sand, clay, alluvium or mud (4–10 blocks in all, varying smoothly), then stone with its ores. River banks, dunes and cliffs never show stone in their sides; foothills and mountains are unchanged. The *Loose Cover* option on the Customize screen switches back to the realistic cover (sand and clay tens of blocks deep). Worlds created before this version keep the realistic cover, also in newly generated chunks, so there is no seam.
- **No band of vanilla stand-in trees by the new forests.** Narrow strips and patches of other lowland forests inside the oak-hornbeam, pine and mixed forests (for example the ash-alder belt of a stream) now grow the trees of the surrounding forest, and canopy stand-ins of the nine species with own models nearby grow as the mod's own trees. Larger alder carrs and floodplain forests keep their own trees beyond a mixed edge.
- **Tree roots.** Old oaks, beeches, lindens and hornbeams of the new forests spread root flares over the ground at the trunk base (fewer on maples and ashes, long flat surface roots on spruces, small ones on pines and birches); they belong to the tree. Under every tree of the new forests a root system grows in the soil, two to six blocks deep, with rooted dirt around it, which you find when digging and see in steep banks; under an open space it ends in hanging roots. The roots of windthrows stand up with their root plates. New blocks: a root flare for each of the nine species and the tree root.
- **The Customize screen fits a small window again.** The explanations below the buttons moved into tooltips, so the Scale button no longer slides off the top of the screen at the default window size.

## [0.2.0-alpha.2]

The mod's own trees grow in the oak-hornbeam and pine forests. Tree generation has changed: start a new world.

- **Nine tree species** with their own trunks, bark, leaves and saplings: Scots pine, Norway spruce, birch, pedunculate oak, hornbeam, small-leaved linden, European beech, European ash and Norway maple.
- **Trunks of varying thickness.** Trees are built from thin trunk blocks that taper from the base to the tips of the branches. Each species has its own crown shape: pines with high, open crowns and an orange upper trunk, oaks with broad forked crowns, spruces with narrow cones.
- **Real forest structure** in oak-hornbeam forests, fresh and mixed pine forests and mixed forests: a canopy, a lower tree layer, young trees and shrubs (hazel, alder buckthorn, rowan). Other forests still use vanilla stand-in trees.
- **Managed and natural forests.** Managed stands have one age class and no gaps; natural stands mix ages, with old trees and gaps in the canopy. The *Managed Forests* slider (default 85%) now works.
- **Seasonal leaves** for every species, with Serene Seasons or with the built-in calendar: spring flush, autumn colors, and in winter deciduous crowns take the gray-brown color of bare twigs. Young oaks, hornbeams and beeches keep dry brown leaves through the winter.
- **Saplings** of all nine species grow into a young tree (12–17 blocks). Scots pine wood set: log, wood, stripped log and wood, planks.
- **Smoother textures** for bark and leaves.

Known issues:
- New worlds start in early spring, so deciduous forests are leafless for the first days. The next version starts in late spring.
- The new forests are heavier to draw. With [Sodium](https://modrinth.com/mod/sodium) the frame rate stays about the same as before; without it, it drops noticeably. Chunks in the new forests load about 20% slower.

## [0.2.0-alpha.1]

First public alpha. World generation of Poland is complete; the forests still use vanilla tree models until the next milestones.

- Two world types: **Poland (1:1 Scale)** (real distances and heights) and **Poland (Gameplay Scale)** (smaller landscapes, lower mountains).
- Landscapes: outwash plains, moraine uplands with tunnel-valley lakes and kettle bogs, old glacial plains, foothills and the Beskids with rare great massifs up to about 1,725 m.
- Rivers in three orders with meanders, oxbow lakes and smooth valleys; a Baltic coast with beaches, foredunes, gray dunes, cliffs and lagoons.
- 36 habitat biomes of Polish vegetation (pine forests, oak-hornbeam forests, beech forests, floodplain forests, alder carrs, bogs, montane spruce forest, dwarf pine, alpine grassland and more), with soils, bank shelves, reedbeds and willow scrub along rivers.
- Smooth transitions between biomes: mixed tree species, forest mantles and fringes, a gradual timberline.
- Two vegetation modes: natural vegetation (default) and present-day Poland (about a third forest, meadows, fallow fields, tree rows).
- Temperature falls with altitude (0.55 °C per 100 m); works with Serene Seasons (optional).
- Commands: `/polishforests find`, `find biome`, `find zone`, `here`, `elevation`, `highest`; habitat info in F3.
