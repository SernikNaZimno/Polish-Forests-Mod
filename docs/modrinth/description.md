![Polish Forests](https://raw.githubusercontent.com/SernikNaZimno/Polish-Forests-Mod/main/docs/logo/logo_horizontal.png)

**Polish Forests** generates an endless, procedural Poland: outwash plains and moraine uplands, meandering rivers with oxbow lakes, the Baltic coast with dunes and lagoons, and the Beskids with dwarf pine above the timberline. The vegetation follows Polish phytosociology: every column of the world gets a habitat, and the habitat decides the biome, the soil and the plants.

> **Alpha.** World generation can change between versions. Start a new world after updating. The oak-hornbeam and pine forests already have the mod's own trees; the other forests still use vanilla stand-ins. Ground flora, deadwood and animals come next.

## Features

- **Two world types.** *Poland (1:1 Scale)* with real distances and heights (sea level at Y 63, the Beskids above 1,500 m), and *Poland (Gameplay Scale)* with smaller landscapes and lower mountains for normal play.
- **Landscapes of the Polish lowlands and mountains.** Outwash plains with pine forests, moraine uplands with tunnel-valley lakes and kettle bogs, river valleys with smooth slopes, foothills and the Beskids with rare great massifs.
- **Rivers and the coast.** Three orders of rivers with meanders and oxbow lakes, valley floors with willow scrub, reedbeds and floodplain forests; beaches, foredunes, gray dunes with crowberry pine forest, cliffs and lagoons with deltas.
- **36 habitat biomes.** Fresh and dry pine forests, oak-hornbeam forests, lowland and Carpathian beech forests, fir forests, ash-alder and willow-poplar floodplain forests, alder carrs, raised bogs and fens, montane spruce forest, dwarf pine scrub and alpine grassland, and more.
- **Own trees.** Nine Polish tree species with trunks of varying thickness, their own bark, leaves and saplings, and leaves that change with the seasons. Oak-hornbeam and pine forests have a canopy, a lower tree layer, young trees and shrubs, as managed or natural stands.
- **Smooth transitions.** Neighbouring forests mix their species over tens of blocks, forest edges get a shrub mantle and a herb fringe, and the timberline thins out gradually.
- **Two vegetation modes.** *Natural vegetation* (default) or *present-day Poland* with about a third of the land forested, meadows, fallow fields and tree rows along streams.
- **Climate.** Temperature falls with altitude, so snow lies on the mountains and not on the lowlands. Seasons with [Serene Seasons](https://modrinth.com/mod/serene-seasons) (optional).
- **Commands.** `/polishforests find <target>`, `find biome <id>`, `find zone <id>` and `here` show where things are and what habitat you stand in. F3 shows the habitat too.

## Requirements

- Minecraft 26.3, Fabric Loader 0.19.5 or newer, Java 25
- [Fabric API](https://modrinth.com/mod/fabric-api), [GeckoLib](https://modrinth.com/mod/geckolib), [SmartBrainLib](https://modrinth.com/mod/smartbrainlib)
- Optional: [Serene Seasons](https://modrinth.com/mod/serene-seasons). A new world starts in late spring; to match it with Serene Seasons, set `starting_sub_season = 3` and `sub_season_duration = 12` under `[time_settings]` in `config/sereneseasons/seasons.toml` (the modpack does this for you).
- Recommended for the tall 1:1 world: [Sodium](https://modrinth.com/mod/sodium), [Lithium](https://modrinth.com/mod/lithium), [C2ME](https://modrinth.com/mod/c2me-fabric)

Choose the *Poland (1:1 Scale)* or *Poland (Gameplay Scale)* world type when you create a world. The mod is needed on both the server and the client.

## Compatibility

- **Tree felling:** [FallingTree](https://modrinth.com/mod/fallingtree) and [Tree Harvester](https://modrinth.com/mod/tree-harvester) (optional) fell the mod's trees whole with one cut: trunk, branches and root flares, never a neighboring tree, a shrub or the roots in the ground. Dead wood (lying logs, windthrows) is cut block by block. Cut-off leaves of the mod's trees fall within one to three seconds, while leaves a neighboring crown still holds stay. Thick wood gives logs, thin branches sticks, and young pole-stage trees only sticks. The mod's old trees have up to about 360 wood blocks, while FallingTree by default fells at most 100: raise `maxSize` and `maxScanSize` under `trees` in `config/fallingtree.json` (the modpack sets 2048, drops the loot at the cut and lowers the axe wear to 0.2 per block). Tree Harvester has no size limit; it wears the axe by 1 per wood block (`loseDurabilityModifier` in its config) and replants a sapling at the base.

## Roadmap

Next is the rest of the oak-hornbeam and fresh pine forest: deadwood, roots, ground flora and mosses with seasons, roe deer, wild boar and birdsong. After that the same for all of Poland: more tree species, managed and natural forests, the Tatras and the Sudetes, and Polish fauna.

## License

Code: MIT. Textures, models and other assets: CC BY 4.0. Source and issue tracker on [GitHub](https://github.com/SernikNaZimno/Polish-Forests-Mod).
