# 04. Technika dodawania flory w Fabric: zestawy drewna, drzewa, podszyt, runo, mchy, ściółka, grzyby, martwe drewno, rozmieszczanie

Data: 2026-09-21
Projekt: "Polish Forests", dawniej "Przyrodniczo zgodne lasy" (mod Fabric do Minecrafta)
Status pliku: ZAKOŃCZONY (budżet 30 wywołań WebSearch/WebFetch wykorzystany w całości; elementy niezweryfikowane w sieci są oznaczone "NIE ZWERYFIKOWANE"/"z wiedzy ogólnej"/"do potwierdzenia" i zebrane w sekcji 11).

## Streszczenie

Minecraft Java używa od 26.1 (wydanie 2026-03-24) nowego schematu wersji `rok.drop.hotfix`; we wrześniu 2026 stabilna jest 26.2, a 26.3 jest w snapshotach (do Snapshot 8) i dodaje m.in. wanilijną topolę (`poplar_trunk_placer`, `poplar_foliage_placer`), huby na kłodach (`shelf_mushroom`) i dekorator `attached_to_logs`. Serene Seasons ma wersje dla 26.2 i 26.3 (26.1.2.0.6/0.7, Fabric, opublikowane 2026-08-31 i 2026-09-21), więc integracja jest wykonalna na najnowszej wersji. Pełny zestaw drewna to 20 bloków, 19 przedmiotów i 2 typy encji łodzi na gatunek, co bez generatorów oznacza ok. 165 plików JSON i ~20 PNG na gatunek; Fabric Data Generation (`FabricModelProvider` z `BlockFamily`, `FabricBlockLootTableProvider`, `FabricTagProvider`, `FabricRecipeProvider`, `FabricLanguageProvider`) redukuje pracę ręczną do tekstur i ~10 linii rejestracji na gatunek. Typy drewna i tabliczek robi się przez `WoodTypeBuilder`/`BlockSetTypeBuilder` z Fabric API; łodzie od 1.21.2 są osobnymi `EntityType` i tu pomaga Terraform Wood API (LGPL-3.0) albo własne ~400 linii kodu. Every Compat/Moonlight wykrywają nasze drewno automatycznie po parze `<name>_planks` + `<name>_log` (lub JSON `moonlight/wood_types`), dając darmowe warianty w modach na meble. Liście: wszystkie prócz świerkowych emitują od 1.21.5 cząsteczki opadania; kolor sezonowy z Serene Seasons dostaje automatycznie każdy blok korzystający z wanilijnego resolvera koloru listowia (SS podmienia `BiomeColors.FOLIAGE_COLOR`), więc liście liściaste należy robić w skali szarości z tintem biomowym, a modrzew wymaga własnych stanów bloku (green/yellow/bare). Wanilijna ściółka `leaf_litter` (1.21.5) to blok segmentowy 1-4 z tintem `dry_foliage`, otrzymywany też z wytopu liści - gotowy wzór dla igliwia. Generacja drzew jest w pełni data-driven: 10 trunk placerów, 12 foliage placerów, 12 dekoratorów (w tym `place_on_ground` do ściółki i `shelf_mushroom` do hub), `fallen_tree` do martwego drewna; własne placery wymagają access widenera lub mixin-invokera, bo Fabric API nie ma do tego oficjalnego API. W 26.1 usunięto `random_patch`/`flower` - kępy roślin buduje się z modyfikatorów `count` + `random_offset`/`offset` (+ nowe `cuboid`, `random_chance`, `randomly_selected`), a rośliny rosną tylko na blokach z nowych tagów `#supports_vegetation`/`#substrate_overworld`, do których muszą trafić nasze gleby (torf, igliwie). Wanilijny las liściasty i tajga mają 10-11 prób drzew na chunk (ok. 300 drzew/ha przy skali 1 blok = 1 m). Proporcje gatunków w drzewostanie zadaje `random_selector` (uwaga: szanse sekwencyjne, nie wagi), a las gospodarczy w rzędach wymaga własnego `PlacementModifier`. Fenologię (kwitnienie, owoce, nagie gałęzie) najtaniej robić właściwością bloku przełączaną w random ticku wg podpory roku SS (12 podpór, 8 dni każda), kolory zaś czystym tintem. Główne otwarte decyzje: wersja docelowa (26.2 vs 26.3), skala drzew (realne 30-40 bloków?), zależność od Terraform, licencje kodu/assetów oraz lista gatunków z pełnym zestawem drewna.

## 1. Zestaw drewna ("wood set") w aktualnej wanilii i liczba plików na gatunek

Uwaga metodyczna: poniższe zestawienie wynika ze struktury assetów wanilii (znanej z 1.21.x; w 26.x struktura katalogów nie zmieniła się poza tym, co opisano), a nie z pobranego w tej sesji listingu plików - liczby są policzone ręcznie i oznaczone jako "obliczenie własne". Gatunki wanilijne z pełnym zestawem (26.3): oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, bamboo, pale_oak, **poplar (nowy w 26.3)** + crimson/warped (Nether).

### 1.1. Bloki i przedmioty jednego gatunku (wzorzec: `oak`)

| # | Blok (ID) | Klasa wanilii (Yarn 1.21.x) | Przedmiot? | Uwagi |
|---|---|---|---|---|
| 1 | `<w>_log` | `PillarBlock` | tak | oś `axis` x/y/z |
| 2 | `<w>_wood` | `PillarBlock` | tak | kora ze wszystkich stron |
| 3 | `stripped_<w>_log` | `PillarBlock` | tak | okorowanie siekierą: rejestr `StrippableBlockRegistry.register(log, stripped)` (Fabric API) |
| 4 | `stripped_<w>_wood` | `PillarBlock` | tak | |
| 5 | `<w>_planks` | `Block` | tak | |
| 6 | `<w>_stairs` | `StairsBlock` | tak | |
| 7 | `<w>_slab` | `SlabBlock` | tak | |
| 8 | `<w>_fence` | `FenceBlock` | tak | |
| 9 | `<w>_fence_gate` | `FenceGateBlock(WoodType, ...)` | tak | dźwięk z `WoodType` |
| 10 | `<w>_door` | `DoorBlock(BlockSetType, ...)` | tak | 2 połówki `half`, `hinge`, `open`, `facing`, `powered` |
| 11 | `<w>_trapdoor` | `TrapdoorBlock(BlockSetType, ...)` | tak | |
| 12 | `<w>_button` | `ButtonBlock(BlockSetType, 30, ...)` | tak | |
| 13 | `<w>_pressure_plate` | `PressurePlateBlock(BlockSetType, ...)` | tak | |
| 14 | `<w>_sign` | `SignBlock(WoodType, ...)` | tak (`SignItem` wspólny dla 14+15) | stojąca |
| 15 | `<w>_wall_sign` | `WallSignBlock(WoodType, ...)` | nie | |
| 16 | `<w>_hanging_sign` | `HangingSignBlock(WoodType, ...)` | tak (`HangingSignItem` wspólny dla 16+17) | sufitowa |
| 17 | `<w>_wall_hanging_sign` | `WallHangingSignBlock(WoodType, ...)` | nie | |
| 18 | `<w>_leaves` | `LeavesBlock` (1.21.5+: `TintedParticleLeavesBlock` / `ParticleLeavesBlock`) | tak | `distance`, `persistent`, `waterlogged` |
| 19 | `<w>_sapling` | `SaplingBlock(SaplingGenerator, ...)` | tak | `stage` 0-1 |
| 20 | `potted_<w>_sapling` | `FlowerPotBlock` | nie | rejestr doniczki: w 1.21.x przez `Blocks.FLOWER_POT` + Fabric? (wanilia: mapa w `FlowerPotBlock`; Fabric nie ma osobnego API - użyć `FlowerPotBlock` z odpowiednim contentem) |
| - | `<w>_boat` | encja `BoatEntity`; **od 1.21.2 każdy gatunek ma własny `EntityType` (`EntityType.OAK_BOAT`)** | tak (`BoatItem`) | wymaga rejestracji `EntityType` + renderera + modelu (`EntityModelLayer`) po stronie klienta |
| - | `<w>_chest_boat` | `ChestBoatEntity`, osobny `EntityType` | tak | jw. |

Razem: **20 bloków, 19 przedmiotów, 2 typy encji** na gatunek (obliczenie własne). Bloki z tabliczkami wymagają `BlockEntityType` - wanilia ma wspólne `SIGN`/`HANGING_SIGN` z listą obsługiwanych bloków; w Fabric dodaje się własne bloki do istniejącego typu przez `BlockEntityTypeBuilder`/mixin lub tworzy własne `BlockEntityType` (**to właśnie robi Terraform Wood API - `TerraformSignBlock` itd.**, patrz 2.3).

### 1.2. Pliki na gatunek (obliczenie własne wg struktury wanilii 1.21.4+/26.x)

| Rodzaj pliku | Katalog | Liczba | Uwagi |
|---|---|---|---|
| blockstates | `assets/<ns>/blockstates/*.json` | 20 | po jednym na blok |
| modele bloków | `assets/<ns>/models/block/*.json` | ~40 | log 2 (pion+poziom), stripped_log 2, wood 1, stripped_wood 1, planks 1, stairs 3, slab 2, fence 3 (post, side, inventory), fence_gate 4, door 8, trapdoor 3, button 3, pressure_plate 2, sign 1, hanging_sign 1, leaves 1, sapling 1, potted 1 |
| definicje przedmiotów (od 1.21.4) | `assets/<ns>/items/*.json` | 19 | nowy format "item model definition" (`model`, `tints`) |
| modele przedmiotów | `assets/<ns>/models/item/*.json` | ~19 | większość = parent na model bloku; `item/generated` dla door, sign, hanging_sign, boat, chest_boat, sapling |
| tekstury bloków | `assets/<ns>/textures/block/*.png` | 10 | log, log_top, stripped_log, stripped_log_top, planks, door_top, door_bottom, trapdoor, leaves, sapling |
| tekstury przedmiotów | `.../textures/item/*.png` | 5 | door, sign, hanging_sign, boat, chest_boat |
| tekstury encji/GUI | `.../textures/entity/signs/<w>.png`, `.../entity/signs/hanging/<w>.png`, `.../entity/boat/<w>.png`, `.../entity/chest_boat/<w>.png`, `.../gui/hanging_signs/<w>.png` | 5 | |
| loot tables | `data/<ns>/loot_table/blocks/*.json` | 20 | liście z tabelą fortuny (sadzonka 1/20, 1/16, 1/12, 1/10; patyki 1/50), drzwi/slab specjalne |
| receptury | `data/<ns>/recipe/*.json` | 15 | planks (z `#<ns>:<w>_logs`), wood, stripped_wood, stairs, slab, fence, fence_gate, door, trapdoor, button, pressure_plate, sign, hanging_sign, boat, chest_boat |
| advancementy receptur | `data/<ns>/advancement/recipes/**` | 15 | odblokowanie receptur (datagen robi automatycznie) |
| tagi bloków (własne pliki) | `data/<ns>/tags/block/<w>_logs.json` | 1 | + wpisy w ~20 tagach `minecraft:`/`c:` (patrz sekcja 9) |
| tagi przedmiotów | `data/<ns>/tags/item/<w>_logs.json` | 1 | + wpisy w ~15 tagach (`logs`, `planks`, `leaves`, `saplings`, `wooden_*`, `signs`, `hanging_signs`, `boats`, `chest_boats`, `c:stripped_logs`...) |
| lang | `assets/<ns>/lang/en_us.json`, `pl_pl.json` | 2 pliki, ~21 wpisów/gatunek | + nazwy encji łodzi |
| **SUMA** | | **~185 plików lub wpisów na gatunek** | z czego ręcznie (bez datagen): ~20 PNG + ~21 linijek lang + rejestracje Java |

- Bez datagen: ok. 165 plików JSON na gatunek x 15-18 gatunków polskich = 2500-3000 plików JSON. To argument decydujący za Fabric Data Generation (sekcja 2.1) - wtedy ręcznie pozostaje tylko ~20 tekstur PNG na gatunek (i te też można częściowo generować - sekcja 8).
- Rejestracja Java (szacunek): jeden `WoodSet`/`WoodFamily` helper (~150-250 linii) + po ~10 linii na gatunek. Wanilia ma podobny helper: `BlockFamilies.register(planks).button(...).fence(...).fenceGate(...).pressurePlate(...).sign(sign, wallSign).slab(...).stairs(...).door(...).trapdoor(...).group("wooden").unlockCriterionName("has_planks").build()` (`net.minecraft.data.family.BlockFamily`/`BlockFamilies`), którego używają `BlockStateModelGenerator.registerCubeAllModelTexturePool(planks).family(family)` i `RecipeProvider.generateFamily(...)`.

### 1.3. Gatunki polskie a zestawy drewna - propozycja (do decyzji użytkownika)

| Gatunek | Łac. | Wanilia ma odpowiednik? | Pełny wood set? |
|---|---|---|---|
| Sosna zwyczajna | *Pinus sylvestris* | częściowo (spruce jest "iglasty ogólny") | TAK (najważniejszy gatunek PL, ~58% powierzchni lasów - dane z innego raportu) |
| Świerk pospolity | *Picea abies* | tak = `spruce` | użyć wanilii (nowe drzewa/pokrój), ew. re-tekstura |
| Jodła pospolita | *Abies alba* | nie | TAK lub "lite" (log, planks, leaves, sapling) |
| Modrzew europejski | *Larix decidua* | nie | TAK (drewno cenione) |
| Dąb szypułkowy / bezszypułkowy | *Quercus robur* / *Q. petraea* | tak = `oak` | wanilia (2 gatunki = ten sam zestaw, inne drzewa) |
| Buk zwyczajny | *Fagus sylvatica* | nie | TAK |
| Brzoza brodawkowata / omszona | *Betula pendula* / *B. pubescens* | tak = `birch` | wanilia |
| Olsza czarna / szara | *Alnus glutinosa* / *A. incana* | nie | TAK (olsy, łęgi) |
| Grab pospolity | *Carpinus betulus* | nie | TAK lub lite |
| Jesion wyniosły | *Fraxinus excelsior* | nie | TAK |
| Klon zwyczajny / jawor | *Acer platanoides* / *A. pseudoplatanus* | nie | TAK (jeden zestaw "maple"?) |
| Lipa drobnolistna | *Tilia cordata* | nie | TAK |
| Wierzba biała / krucha / iwa | *Salix alba* / *S. fragilis* / *S. caprea* | nie | TAK (willow) |
| Osika | *Populus tremula* | częściowo: **`poplar` w 26.3** | użyć wanilijnego poplar (drewno), własny pokrój |
| Topola biała / czarna | *Populus alba* / *P. nigra* | jw. | jw. |
| Wiąz szypułkowy / polny | *Ulmus laevis* / *U. minor* | nie | lite |
| Jarząb pospolity (jarzębina) | *Sorbus aucuparia* | nie | lite (log+leaves+owoce) |
| Czeremcha, dzika czereśnia, jabłoń dzika, grusza dzika | *Prunus padus*, *P. avium*, *Malus sylvestris*, *Pyrus pyraster* | `cherry` (kwitnie różowo jak japońska - niezgodne z PL) | lite lub tylko drzewo |
| Kosodrzewina | *Pinus mugo* | nie | bez wood setu (krzew, kłoda cienka) |
| Cis pospolity | *Taxus baccata* | nie | lite (chroniony, rzadki) |

Szacunek: 9-11 pełnych zestawów + 5-7 "lite" = ok. 1600-2000 plików JSON z datagen (0 ręcznie) i 200-250 PNG ręcznie.

## 2. Fabric Data Generation i biblioteki pomocnicze (Terraform Wood API, BlockSetType/WoodType, Every Compat)

### 2.1. Fabric Data Generation (docs.fabricmc.net/develop/data-generation/*)

Strony dokumentacji (wersje 26.2 / 26.1.2 / 1.21.11 / 1.21.1 / 1.20.4): Setup, Block Models, Item Models, Loot Tables, Tags, Recipes, Translations, Advancements, Enchantments, **Feature Generation (worldgen)**.
- Modele: klasa `FabricModelProvider` z metodami `generateBlockStateModels(BlockStateModelGenerator)` i `generateItemModels(ItemModelGenerator)`; rejestracja w `DataGeneratorEntrypoint.onInitializeDataGenerator(FabricDataGenerator)` przez `pack.addProvider(...)`.
- `BlockStateModelGenerator`: `registerSimpleCubeAll`/`createTrivialCube`, `createTrivialBlock(block, TexturedModel.COLUMN_ALT)` (kolumny), `registerLog(log).log(log).wood(wood)` (kłoda + drewno; w 26.x nazwy Yarn mogą się różnić: `createLogWithHorizontal`/`registerLog`), `registerDoor`/`createDoor`, `registerTrapdoor`, `registerCubeColumn`, **`family(BlockFamily)`** generuje naraz modele: stairs, slab, fence, fence_gate, button, pressure_plate, sign+wall_sign, door, trapdoor, z tekstury bloku bazowego (planks).
- Modele niestandardowe: własny `Model`/`ModelTemplate` wskazujący na parent JSON w `assets/modid/models/block/`, `TextureMap`/`TextureMapping` mapujące sloty, `BlockStateSupplier`/`BlockModelDefinitionGenerator` (warianty, obroty, uvlock).
- Pozostałe providery (znane z API Fabric; szczegóły wersji do potwierdzenia): `FabricBlockLootTableProvider` (`addDrop`, `drops`, `leavesDrops(leaves, sapling, 0.05f, 0.0625f, 0.083333336f, 0.1f)` - wanilijne szanse sadzonki wg fortuny, `doorDrops`, `slabDrops`), `FabricTagProvider.BlockTagProvider`/`ItemTagProvider` (`getOrCreateTagBuilder` -> w nowszych `valueLookupBuilder`), `FabricRecipeProvider` (`generateFamily(BlockFamily)`, `offerPlanksRecipe`, `offerBoatRecipe`, `offerChestBoatRecipe`, `offerHangingSignRecipe`, `offerBarkBlockRecipe`), `FabricLanguageProvider` (`translationBuilder.add(block, "Nazwa")`).
- Wniosek: przy poprawnie zdefiniowanym `BlockFamily` + `registerLog` + kilku wywołaniach datagen pełen zestaw drewna generuje ~60-70 plików JSON na gatunek automatycznie; ręcznie zostają tylko tekstury PNG i ewentualne modele nietypowe (liście z tintem, sadzonka cross).

### 2.2. BlockSetType / WoodType w Fabric

- Fabric API (moduł `fabric-object-builder-api-v1`) udostępnia `BlockSetTypeBuilder` i `WoodTypeBuilder` (zastąpiły dawne `BlockSetTypeRegistry`/`WoodTypeRegistry`, zmiana od 1.20.2). Użycie: `BlockSetTypeBuilder.copyOf(BlockSetType.OAK).build(ID)` oraz `WoodTypeBuilder.copyOf(WoodType.OAK).build(ID, blockSetType)` lub `buildAndRegister(...)`. Źródło: https://maven.fabricmc.net/docs/fabric-api-0.92.0+1.20.5/net/fabricmc/fabric/api/object/builder/v1/block/type/WoodTypeBuilder.html, https://fabricmc.net/2023/09/12/1202.html
- `WoodType` decyduje o teksturach tabliczek: zwykłe `assets/<ns>/textures/entity/signs/<path>.png`, wiszące `assets/<ns>/textures/entity/signs/hanging/<path>.png`, oraz o dźwiękach tabliczek i furtek. `BlockSetType` decyduje o dźwiękach drzwi/klap/przycisków/płyt i o tym, czy drzwi otwiera się ręką.
- Aktualność w 26.x: klasy nadal istnieją w Fabric API dla 1.21.x; obecność w 26.2 NIE ZWERYFIKOWANA bezpośrednio (do sprawdzenia w javadoc najnowszego fabric-api).

### 2.3. Terraform (TerraformersMC)

Źródło: https://github.com/TerraformersMC/Terraform
- Biblioteka modułowa, licencja **LGPL-3.0**, Maven: `maven { url = 'https://maven.terraformersmc.com/' }`, np. `implementation "com.terraformersmc.terraform-api:terraform-wood-api-v1:<wersja>"`. Wersje główne odpowiadają wersjom MC (semver).
- Moduły: `terraform-wood-api-v1` (własne typy drewna, łodzie, tabliczki), `terraform-tree-api-v1` (generacja drzew: dodatkowe trunk/foliage placers), `terraform-biome-remapper-api-v1`, `terraform-config-api-v1`, `terraform-shapes-api-v1`, `terraform-surfaces-api-v1`, `terraform-dirt-api-v1` (własne gleby - **ciekawe dla torfu/igliwia**).
- README modułów wood/tree nie udało się pobrać (404 pod `blob/HEAD/...`). Z wiedzy ogólnej (NIE ZWERYFIKOWANE w tej sesji): wood API zawiera `TerraformBoatType` + `TerraformBoatTypeRegistry` (rejestr typów łodzi z przedmiotem, deskami i - od 1.21.2 - własnym `EntityType`), `TerraformBoatItemHelper.registerBoatItem(...)`, `TerraformSignBlock`/`TerraformWallSignBlock`/`TerraformHangingSignBlock`/`TerraformWallHangingSignBlock` (tabliczki z własną teksturą `Identifier` zamiast `WoodType`, rejestrowane w wanilijnych `BlockEntityType.SIGN`/`HANGING_SIGN` przez mixin) oraz klient: `TerraformBoatClientHelper.registerModelLayers(...)`. Tree API: dodatkowe placery/dekoratory używane przez Terrestria (np. `FallenTrunkPlacer`? - nazwa niepewna). **Do zweryfikowania na https://github.com/TerraformersMC/Terraform (gałąź dla 26.x) i w Terrestria/Traverse.**
- Aktualność dla 26.x: Terraformers (Terrestria, Traverse, Cinderscapes) zwykle aktualizują szybko; wersja Terraform dla 26.2/26.3 - do sprawdzenia na maven.terraformersmc.com / Modrinth.
- Alternatywa bez zależności: własne klasy tabliczek jak w Terraform (2 mixiny do `BlockEntityType`) i własne `EntityType` łodzi wzorowane na `BoatEntity` (od 1.21.2 `EntityType.Builder.create(BoatEntity::new /* z supplier item */, SpawnGroup.MISC)`). Koszt: ~300-500 linii jednorazowo. Decyzja: zależność (mniej kodu, LGPL) vs własne (brak zależności).

### 2.4. Every Compat / Moonlight

- Repozytorium Every Compat to https://github.com/MehVahdJukaar/WoodGood (dawna nazwa "Wood Good"); zależy od Moonlight Lib (https://github.com/MehVahdJukaar/Moonlight), która ma `WoodType`/`LeavesType` (własny rejestr dynamiczny) i generuje assety w locie.
- Wykrywanie modowego drewna w Moonlight: klasa `WoodType` z `Finder`, który sprawdza, czy dostawcy `planks` i `log` zwracają istniejące bloki (nie `air`/null) - czyli **wykrywanie po parze bloków deski + kłoda**.
- README (https://github.com/MehVahdJukaar/WoodGood): Every Compat "dodaje wszystkie bloki, które dodają inne mody, we wszystkich zainstalowanych typach drewna". Wykrywanie typów drewna: (1) konwencja nazw `<name>_planks` + `<name>_log` w tej samej przestrzeni nazw, (2) rejestr `WoodType` Moonlight, (3) tagi. Autor moda może wymusić/poprawić wykrycie: **datapack JSON w `data/<ns>/moonlight/wood_types/`** (definiuje planks/log i opcjonalnie stripped_log, wood, stripped_wood, leaves, sapling itd.) lub kodem (`EveryCompatAPI.registerModule(...)` - to dla modów dodających *typy bloków*, nie drewno). Loadery: Fabric i NeoForge (gałąź 1.21). Wersje dla 26.x - NIE ZWERYFIKOWANO (sprawdzić Modrinth).
- Wniosek: jeśli nasze bloki będą nazwane wg konwencji (`buk_planks`? - lepiej angielskie ID: `beech_planks`, `beech_log`, `beech_leaves`, `beech_sapling`), Every Compat/Moonlight wykryją drewno automatycznie i wygenerują warianty (skrzynie, półki, meble innych modów) bez naszej pracy. ID bloków powinny być angielskie i zgodne z wanilią; polskie nazwy tylko w plikach lang `pl_pl.json`.

## 3. Liście: kolorowanie, liście sezonowe (Serene Seasons), cząsteczki opadających liści, leaf litter, iglaste vs liściaste, modrzew, fancy/fast

### 3.1. Wanilijne liście (https://minecraft.wiki/w/Leaves, stan 26.3)

- Tint biomowy (foliage colormap `foliage.png`): dąb, dżungla, akacja, dark oak, mangrowiec. Stałe kolory (tint stały w kodzie): świerk `#619961`, brzoza `#80a755`. **Bez tintu (tekstura wstępnie pokolorowana):** wiśnia, azalia, azalia kwitnąca, pale oak, **topola (26.3)**.
- Renderowanie: w trybie "Fast" (lub wyłączone fancy leaves) liście są nieprzezroczyste - przezroczyste piksele rysowane jako ciemna zieleń; w "Fancy" tekstura z kanałem alfa i widocznymi wewnętrznymi ścianami (render layer cutout_mipped). Własne liście: rejestrować w `BlockRenderLayerMap` jako cutout_mipped (1.21.x) - w 26.x API render layers Fabric zmieniło się (do sprawdzenia).
- **Cząsteczki opadających liści: od 1.21.5 WSZYSTKIE liście oprócz świerkowych emitują cząsteczki spadających liści** (wiśnia - płatki `cherry_leaves`, pale oak - `pale_oak_leaves`, pozostałe - `tinted_leaves` z kolorem liści). Wiśnia/pale oak/topola mają cząsteczki z wstępnie pokolorowanej tekstury, azalia kolor zakodowany na sztywno. Mechanizm: `randomDisplayTick` po stronie klienta, cząsteczka spawnowana pod liściem z małą szansą, jeśli blok pod spodem jest powietrzem (klasa Yarn 1.21.5: `LeavesBlock` z polem `leafParticleChance`, podklasy `TintedParticleLeavesBlock` i `ParticleLeavesBlock` - nazwy do potwierdzenia).
- Blockstate: `distance` 1-7, `persistent`, `waterlogged`. Rozkład (decay), gdy odległość taksówkowa od bloku z tagu `#minecraft:logs` > 6 (Java).
- Dropy: sadzonka 5% (fortuna I 6.25%, II 8.33%, III 10%), patyki 2%, jabłko 0.5% (dąb, dark oak), dżungla sadzonka 2.5%.
- Wytop liści -> `leaf_litter` (0.1 XP). Palność (flammability/encouragement): 30/60 jak wanilijne liście (tag `#minecraft:leaves` -> `FlammableBlockRegistry` w Fabric).

### 3.2. Serene Seasons - aktualność i sposób kolorowania

- Najnowsze wersje (Modrinth API, https://api.modrinth.com/v2/project/serene-seasons/version, stan 2026-09-21): **26.1.2.0.7 dla MC 26.3** (Fabric/NeoForge/Forge, opublikowana 2026-09-21), **26.1.2.0.6 dla MC 26.2**, 26.1.2.0.7 dla 26.1.2, 10.1.0.9 dla 1.21.1 (2026-09-05). Zależność: projekt `P7dR8mSH` = Fabric API. Repo: https://github.com/Glitchfiend/SereneSeasons (Fabric/Forge/NeoForge w jednym repo, "All rights reserved" w stopce - **licencja do sprawdzenia przed jakąkolwiek integracją kodową; bezpieczniej używać tylko API i tagów**).
- Zachowanie (opisy CurseForge/Modrinth): trawa i liście zmieniają kolor przez 12 podetap (3 na porę roku): wiosna - niebieskawa zieleń, lato - żółtawa zieleń, jesień - trawa żółknie, liście pomarańczowe, zima - brąz/szarość. **Drzewa iglaste (świerk/jodła) nie są objęte zmianą koloru**; liście nie znikają zimą.
- Mechanizm kodu: plik AT/AW w repozytorium SS (`sereneseasons_at.cfg`, https://github.com/Glitchfiend/SereneSeasons/blob/master/src/main/resources/META-INF/sereneseasons_at.cfg) otwiera pola `BiomeColors.GRASS_COLOR` i `FOLIAGE_COLOR` (typ `ColorResolver`) - SS **podmienia wanilijne resolvery koloru trawy i listowia** na własne, mieszające kolor z sezonowymi mapami kolorów. Wniosek: **każdy blok, którego `BlockColorProvider` woła `BiomeColors.getFoliageColor(world, pos)` / `getGrassColor(...)`, dostaje sezonowe kolory automatycznie, bez żadnej integracji**. Bloki ze stałym kolorem (brzoza `#80a755`, świerk `#619961`) SS traktuje osobno (w kodzie SS istnieją osobne ścieżki dla brzozy; świerk/iglaste są wykluczone) - nazwy opcji konfiguracyjnych (np. `changeBirchColor`) NIE POTWIERDZONE.
- Cykl: 4 pory x 3 podpory = 12 faz; domyślnie **96 dni MC/rok, 24 dni/pora, 8 dni/podpora** (wiki SS: https://github.com/Glitchfiend/SereneSeasons/wiki/Seasons), konfigurowalne. Biomy tropikalne (pustynie, dżungle, sawanny) mają tylko Wet/Dry. Znany błąd (#314): cykl kolorów listowia nie skaluje się przy zmianie długości podpory (8 -> 12 dni) - sprawdzić, czy naprawiony.
- Tagi biomów SS (z wiedzy ogólnej o wersjach 1.19-1.21; NIE ZWERYFIKOWANE dla 26.x): `sereneseasons:blacklisted_biomes` (brak pór roku), `sereneseasons:tropical_biomes`, `sereneseasons:less_color_change_biomes`, `sereneseasons:infertile_biomes`, oraz tagi płodności roślin (item/block): `sereneseasons:spring_crops`, `summer_crops`, `autumn_crops`, `winter_crops`, `unbreakable_fertile_crops`. **Nasze biomy trzeba dodać do właściwych tagów SS (temperate = domyślnie), a nasze uprawy/krzewy do tagów płodności.**
- API (z wiedzy ogólnej, do potwierdzenia w pakiecie `sereneseasons.api.season`): `SeasonHelper.getSeasonState(Level/World)` -> `ISeasonState` z `getSeason()`, `getSubSeason()` (`Season.SubSeason.EARLY_SPRING ... LATE_WINTER`), `getTropicalSeason()`, `getDay()`, `getSeasonCycleTicks()`; `SeasonHelper.usesTropicalSeasons(biome)`; `ModConfig`/`ServerConfig`. Alternatywa bez twardej zależności: sprawdzać `FabricLoader.getInstance().isModLoaded("sereneseasons")` i mieć własny "fallback" pór roku (np. z czasu świata) - wtedy fenologia działa też bez SS.

### 3.3. Kolorowanie własnych liści (ColorProviderRegistry, tintindex)

- Model liści: parent `minecraft:block/leaves` (cube_all z `"tintindex": 0` na wszystkich ścianach). Tekstura w skali szarości/lekko zielona, kolor nakładany przez tint.
- Rejestracja koloru bloku (Fabric API `fabric-rendering-v1`): `ColorProviderRegistry.BLOCK.register((state, world, pos, tintIndex) -> world != null && pos != null ? BiomeColors.getFoliageColor(world, pos) : FoliageColors.getDefaultColor(), BEECH_LEAVES, ...)`. Dla igliwia/ściółki: `BiomeColors` w 1.21.5+ ma także kolor "dry foliage" (`dry_foliage.png`) - nazwa metody do potwierdzenia.
- Kolor przedmiotu: do 1.21.3 `ColorProviderRegistry.ITEM.register(...)`; **od 1.21.4 kolor przedmiotu definiuje się w `assets/<ns>/items/<w>_leaves.json` przez pole `tints` (np. `{"type":"minecraft:constant","value":-12012264}` lub `minecraft:grass` z temperature/downfall)** - `ColorProviderRegistry.ITEM` prawdopodobnie usunięte/zdeprecjonowane (do potwierdzenia w Fabric API dla 26.x).
- Liście niebiomowe (stały kolor jak brzoza): provider zwraca stałą; wtedy SS ich nie przebarwia - dla polskiej brzozy (żółte jesienią!) lepiej użyć tintu biomowego (foliage) i tekstury w skali szarości, aby SS działało, LUB własnego providera z własną logiką sezonową (sekcja 7).
- Render layer: liście muszą być rysowane w warstwie cutout_mipped (1.21.x: `BlockRenderLayerMap.INSTANCE.putBlock(block, RenderLayer.getCutoutMipped())`; w 26.x Fabric zmieniło API na `BlockRenderLayerMap.putBlock(block, BlockRenderLayer.CUTOUT_MIPPED)` - wersja do potwierdzenia). Sadzonki/runo: cutout.

### 3.4. Własne cząsteczki opadających liści/igieł/płatków

- Wanilia (1.21.5+): klasy liści `TintedParticleLeavesBlock` (cząsteczka `minecraft:tinted_leaves` w kolorze bloku) i `ParticleLeavesBlock` (cząsteczka podana w konstruktorze, np. `cherry_leaves`, `pale_oak_leaves`), pole `leafParticleChance` (wanilia: dąb ok. 0.01, wiśnia 0.1? - wartości do potwierdzenia). Mechanizm: `randomDisplayTick` (klient, ~1000 razy/tick w promieniu 32 bloków), jeśli `random.nextFloat() < chance` i blok pod spodem jest powietrzem -> `ParticleUtil.spawnParticle(...)` pod liściem.
- Własna cząsteczka (Fabric): `SimpleParticleType NEEDLE = Registry.register(Registries.PARTICLE_TYPE, id, FabricParticleTypes.simple())`; plik `assets/<ns>/particles/needle.json` {"textures":["<ns>:needle"]}; tekstura `assets/<ns>/textures/particle/needle.png` (8x8 lub 16x16); klient: `ParticleFactoryRegistry.getInstance().register(NEEDLE, NeedleParticle.Factory::new)` z klasą dziedziczącą po wanilijnej cząsteczce liścia (Yarn 1.21.5: `LeavesParticle` z fabrykami parametryzowanymi grawitacją/obrotem - dawniej `CherryLeavesParticle`). Koszt: ~60 linii na typ cząsteczki.
- Zastosowania: igły modrzewia (żółte, jesień), płatki kwiatów (czeremcha, jabłoń dzika - białe), nasiona brzozy/lipy, "puch" topoli/wierzby (maj-czerwiec), liście jesienne w kolorze gatunku. Jesienią zwiększyć `leafParticleChance` przez stan bloku (sekcja 7).

### 3.5. Iglaste vs liściaste, modrzew, "fancy/fast"

- Iglaste zimozielone (sosna, świerk, jodła, cis, kosodrzewina): stały kolor (bez tintu biomowego lub stały tint), brak cząsteczek opadania lub bardzo rzadkie; **SS ich nie przebarwia** (zgodne z rzeczywistością). Świerk wanilijny spełnia to domyślnie.
- Modrzew (*Larix decidua*): jedyny polski iglasty zrzucający igły. Implementacja: blok liści z właściwością `phase` {green, yellow, bare} (patrz sekcja 7) - `bare` = model tylko z gałązkami (cutout, tekstura z dużą przezroczystością, wciąż w tagu `#leaves`, by rozkład i heightmapy działały), `yellow` = tekstura żółta + cząsteczki igieł ze zwiększoną szansą. Wiosną `bare -> green` (jasnozielone).
- Fancy/fast: wanilia nie wymaga osobnej tekstury "opaque" - w trybie Fast renderer rysuje liście jako nieprzezroczyste (przezroczyste piksele ciemnozielone). Własne liście dziedziczą to zachowanie automatycznie, jeśli są `LeavesBlock` i w warstwie cutout_mipped (Fabric/Sodium obsługują). Warto sprawdzić wygląd tekstur w Fast (mało pikseli alfa = ciemne plamy).
- Przezroczystość i wydajność: gęste korony (buk, grab) z liśćmi cutout są droższe w renderowaniu niż wanilijne; pokroje z pełnymi kulami liści rzędu 5-7 bloków promienia mogą obniżyć FPS - planować LOD-y (Distant Horizons) i testy wydajności.

## 4. Generacja drzew: TreeFeatureConfig, trunk/foliage/root placers, decorators, własne placery w Javie, szablony NBT vs procedura, sadzonki, pnie 2x2

### 4.0. Kontekst wersji (WAŻNE)

- Minecraft Java zmienił schemat numeracji: od **26.1 ("Tiny Takeover", wydane 2026-03-24)** obowiązuje format `rok.drop.hotfix` zamiast `1.x` (źródło: minecraft.wiki/w/Java_Edition_26.1). Dokumentacja Fabric (docs.fabricmc.net) w wrześniu 2026 oferuje wersje: **26.2, 26.1.2, 1.21.11, 1.21.1, 1.20.4**. Strony minecraft.wiki opisują już "Java Edition 26.3 Snapshot 1-8" (snapshoty kolejnego dropu).
- Wniosek: mod celujący w "aktualną" wersję to 26.2 (stabilna) lub 26.3 (snapshoty). Wiele poradników i modów w sieci dotyczy 1.20.x/1.21.x; nazwy klas (Yarn) i format JSON zmieniały się między 1.21.x a 26.x. Trzeba zdecydować z użytkownikiem o wersji docelowej.

### 4.1. Konfiguracja feature `minecraft:tree` (TreeFeatureConfig) - pola

Źródło: https://minecraft.wiki/w/Configured_feature i https://minecraft.wiki/w/Tree_definition (stan: Java Edition 26.3 Snapshot 1).

| Pole | Znaczenie |
|---|---|
| `trunk_provider` | block state provider pnia (np. `simple_state_provider` z `..._log`) |
| `foliage_provider` | block state provider liści |
| `below_trunk_provider` (opcjonalne) | blok podkładany pod pień (zastąpił dawne `dirt_provider`/`force_dirt`?) - NIE POTWIERDZONO nazwy dawnego pola w 26.x |
| `trunk_placer` | sposób generowania pnia (patrz tabela) |
| `foliage_placer` | sposób generowania korony |
| `root_placer` (opcjonalne) | korzenie (tylko `mangrove_root_placer`) |
| `minimum_size` | `two_layers_feature_size` / `three_layers_feature_size` - minimalne wolne miejsce na koronę zależnie od wysokości |
| `decorators` | lista dekoratorów (może być pusta) |
| `ignore_vines` | domyślnie false |

### 4.2. Trunk placers (wanilia 26.3)

| Typ | Pola | Zachowanie (wg wiki) |
|---|---|---|
| `straight_trunk_placer` | `base_height` 0-32, `height_rand_a` 0-24, `height_rand_b` 0-24 | prosty pień; wysokość = base + rand(a) + rand(b); korona doczepiana nad najwyższą kłodą |
| `forking_trunk_placer` | jw. | pień z 1-2 gałęziami, nawis 1-3 bloków (wanilijna akacja) |
| `giant_trunk_placer` | jw. | pień 2x2 (topmost tylko 1 blok) - mega świerk/sosna |
| `mega_jungle_trunk_placer` | jw. | jak giant + gałęzie długości 5 w losowych kierunkach w górnej połowie |
| `dark_oak_trunk_placer` | jw. | pień 2x2 z kolumnami gałęzi w obszarze 4x4 w 5 najwyższych blokach |
| `fancy_trunk_placer` | jw. | pień wysokości (height+2)*0.618 z gałęziami w losowych kierunkach (duży dąb "fancy_oak") |
| `bending_trunk_placer` | jw. + `bend_length` 1-64, `min_height_for_leaves` | najwyższe 2 bloki mają szansę przesunięcia (wygięcie); pień mangrowca |
| `upwards_branching_trunk_placer` | `extra_branch_steps`, `extra_branch_length`, `place_branch_per_log_probability` 0-1, `can_grow_through` (lista bloków) | gałęzie rosnące w górę (azalia/mangrowiec) |
| `cherry_trunk_placer` | `branch_count` 1-3, `branch_horizontal_length` 2-16, `branch_start_offset_from_top` -16..0, `branch_end_offset_from_top` -16..16 | kilka gałęzi poziomo-skośnych na zadanych wysokościach (wiśnia) |
| `poplar_trunk_placer` (NOWY, 26.3) | `branch_amount` 1-4, `trunk_height_above_branches` 0-8 | gałęzie + przedłużenie pnia nad nimi (topola) |

### 4.3. Foliage placers (wanilia 26.3)

| Typ | Pola | Uwagi |
|---|---|---|
| `blob_foliage_placer` | `radius` 0-16, `offset` 0-16, `height` 0-16 | kula/blob (dąb, brzoza) |
| `spruce_foliage_placer` | `radius`, `offset`, `trunk_height` 0-24 | stożki od góry pnia, promień rośnie do maksimum (świerk) |
| `pine_foliage_placer` | `radius`, `offset`, `height` 0-24 | korona tylko na szczycie (sosna wanilijna) |
| `mega_pine_foliage_placer` | `radius`, `offset`, `crown_height` 0-24 | mega sosna |
| `acacia_foliage_placer` | `radius`, `offset` | płaska korona |
| `bush_foliage_placer` | `radius`, `offset`, `height` 0-16 | krzew (dąb-krzew w dżungli) |
| `fancy_foliage_placer` | `radius`, `offset`, `height` 0-16 | kule na końcach gałęzi (fancy oak) |
| `jungle_foliage_placer` | `radius`, `offset`, `height` 0-16 | |
| `dark_oak_foliage_placer` | `radius`, `offset` | |
| `random_spread_foliage_placer` | `radius`, `offset`, `foliage_height` 1-512, `leaf_placement_attempts` 0-256 | losowo rozrzucone liście (azalia, mangrowiec) |
| `cherry_foliage_placer` | `radius`, `offset`, `height` 4-16, `wide_bottom_layer_hole_chance`, `corner_hole_chance`, `hanging_leaves_chance`, `hanging_leaves_extension_chance` (wszystkie 0-1) | korona wiśni z "zwisami" |
| `poplar_foliage_placer` (NOWY, 26.3) | `radius`, `offset`, `height` 5-16, `side_hole_chance` 0-1 | wysoka, wąska korona topoli |

### 4.4. Dekoratory drzew (wanilia 26.3)

| Typ | Pola | Działanie |
|---|---|---|
| `trunk_vine` | - | pnącza na każdej ścianie każdej kłody pnia z p=75% |
| `leave_vine` | `probability` | pnącza na liściach, przedłużane 4 bloki w dół |
| `cocoa` | `probability` | kakao na 3 dolnych blokach pnia, 25%/ściana |
| `beehive` | `probability` | jeden ul 1 blok pod najniższymi liśćmi |
| `alter_ground` | `provider` | zamienia bloki z tagu `#minecraft:dirt` wokół drzewa (wanilia: podzol pod świerkami) |
| `attached_to_leaves` | `probability`, `exclusion_radius_xz` 0-16, `exclusion_radius_y` 0-16, `required_empty_blocks` 0-16, `block_provider`, `directions` | doczepia bloki do odsłoniętych ścian liści (mangrowe propagule, zwisające liście) |
| `attached_to_logs` (nowy) | `probability`, `block_provider`, `directions` | doczepia bloki do odsłoniętych ścian pnia - **idealne dla hub (grzybów nadrzewnych) i mchów na pniach** |
| `place_on_ground` | `tries` (128), `radius` (2), `height` (1), `block_state_provider` | stawia bloki na ziemi wokół drzewa wg heightmapy MOTION_BLOCKING_NO_LEAVES - **wanilia używa tego do leaf litter pod drzewami** |
| `creaking_heart` | `probability` | serce creakinga w pniu (pale oak) |
| `pale_moss` | `leaves_probability`, `trunk_probability`, `ground_probability` | mech blady na liściach/pniu/ziemi + wiszący mech |
| `shelf_mushroom` (nowy, 26.3) | `probability` | **huby ("shelf mushrooms") na każdej kłodzie pnia z zadanym prawdopodobieństwem** - wanilia ma już blok huby |

### 4.5. Root placer i minimum_size

- `mangrove_root_placer`: `root_provider`, `trunk_offset_y`, `above_root_placement` {`above_root_provider`, `above_root_placement_chance`}, `mangrove_root_placement` {`max_root_width` 1-12, `max_root_length` 1-64, `random_skew_chance`, `can_grow_through`, `muddy_roots_in`, `muddy_roots_provider`}. To jedyny typ korzeni w wanilii - dla wykrotów/korzeni szkarpowych trzeba własny `RootPlacerType`.
- `two_layers_feature_size`: `limit` 0-81 (dom. 1), `lower_size` 0-16 (dom. 0), `upper_size` 0-16 (dom. 1). `three_layers_feature_size`: `limit`, `upper_limit` 0-80, `lower_size`, `middle_size`, `upper_size`. Oba: opcjonalne `min_clipped_height` 0-80 (pozwala "przyciąć" drzewo przy braku miejsca zamiast anulować).

### 4.6. Wanilijne powalone drzewa (feature `fallen_tree`)

Źródło: https://minecraft.wiki/w/Fallen_tree
- Dodane w Java 1.21.5 (snapshot 25w09a). Warianty: dąb, świerk, brzoza, "super birch", dżunglowe, **topola (26.3)**.
- Struktura: 1 pionowa kłoda (pniak) + w odległości 1-2 bloków pozioma kłoda z rzędu bloków (nie zawsze).
- Długości kłody: dąb 4-7, brzoza 5-8, super birch 5-15, świerk 6-10, dżungla 4-11, topola 4-7.
- Dekoracje: pniak z pnączami 75% (nie u świerka/brzozy); świerk/brzoza 1-2 muchomory czerwone (red_mushroom); brzoza/topola grzyby brązowe; topola dodatkowo **huby (shelf mushrooms) na kłodzie**.
- Konfiguracja: typ `fallen_tree`, pola `trunk_provider`, `log_length` (0-16), `log_decorators`, `stump_decorators`. Osobne configured features: `fallen_oak_tree`, `fallen_birch_tree` itd.
- Nie generują się w Meadow, Grove, Bamboo Jungle; w Flower Forest tylko brzozy.
- Wniosek: martwe drewno leżące można robić czystymi datapackami (JSON) bez Javy, z dekoratorami grzybów/mchu.

### 4.7. Własne TrunkPlacer / FoliagePlacer / TreeDecorator w Javie (Fabric)

Źródła: https://fabricmc.net/wiki/tutorial:trees (tutorial 1.19.2, nadal poglądowo aktualny), https://github.com/FabricMC/fabric-api/issues/1504 i PR https://github.com/FabricMC/fabric/pull/1507 (Fabric API **nie ma** oficjalnego API do typów placerów; PR porzucony, autor stworzył bibliotekę "Arctree"), https://github.com/MinecraftForge/MinecraftForge/issues/8789 (ten sam problem na Forge).

- Problem: konstruktory `TrunkPlacerType`, `FoliagePlacerType`, `TreeDecoratorType`, `RootPlacerType` oraz statyczna metoda `register(String, MapCodec)` są prywatne. Rozwiązania: (a) **access widener** w `src/main/resources/<modid>.accesswidener`: `accessible method net/minecraft/world/gen/trunk/TrunkPlacerType <init> (Lcom/mojang/serialization/MapCodec;)V` (analogicznie `foliage/FoliagePlacerType`, `treedecorator/TreeDecoratorType`, `root/RootPlacerType`), potem `Registry.register(Registries.TRUNK_PLACER_TYPE, Identifier.of(MODID, "pine_trunk"), new TrunkPlacerType<>(PineTrunkPlacer.CODEC))`; (b) mixin `@Invoker("register")` na `TrunkPlacerType` (`TrunkPlacerTypeInvoker.invokeRegister("pine_trunk", CODEC)` - przykład z tutoriala Fabric). Rejestrować w `onInitialize` **przed** wczytaniem datapacków (zwykłe statyczne inicjalizacje wystarczą).
- Szkielet własnego trunk placera (Yarn 1.21.x; nazwy pakietów w 26.x do potwierdzenia):
```java
public class PineTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<PineTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i ->
        fillTrunkPlacerFields(i).and(Codec.intRange(0, 16).fieldOf("crown_branches").forGetter(p -> p.crownBranches))
        .apply(i, PineTrunkPlacer::new));
    @Override protected TrunkPlacerType<?> getType() { return ModTrunkPlacerTypes.PINE; }
    @Override public List<FoliagePlacer.TreeNode> generate(TestableWorld world, BiConsumer<BlockPos, BlockState> replacer,
            Random random, int height, BlockPos start, TreeFeatureConfig config) {
        setToDirt(world, replacer, random, start.down(), config);          // podkład (below_trunk_provider)
        for (int y = 0; y < height; y++) getAndSetState(world, replacer, random, start.up(y), config); // kłody
        // gałęzie: getAndSetState(..., state -> state.with(PillarBlock.AXIS, Direction.Axis.X)) itd.
        return List.of(new FoliagePlacer.TreeNode(start.up(height), 0, false)); // węzły korony (radiusOffset, giantTrunk)
    }
}
```
- Foliage placer: dziedziczyć po `FoliagePlacer`, zaimplementować `generate(TestableWorld, BlockPlacer, Random, TreeFeatureConfig, int trunkHeight, TreeNode node, int foliageHeight, int radius, int offset)`, `getRandomHeight(Random, int trunkHeight, TreeFeatureConfig)` i `isInvalidForLeaves(Random, dx, y, dz, radius, giantTrunk)` (decyduje o "wycinaniu" rogów). Pomocnik `generateSquare(...)` stawia warstwę liści o zadanym promieniu. Każdy `TreeNode` zwrócony z trunk placera dostaje własną koronę - **wielokrotne węzły = wiele "kul" liści na końcach gałęzi** (tak działa fancy oak i wiśnia).
- Dekorator: dziedziczyć po `TreeDecorator`, `generate(TreeDecorator.Generator g)` z dostępem do `g.getLogPositions()`, `g.getLeavesPositions()`, `g.getRootPositions()`, `g.getWorld()`, `g.replace(pos, state)`, `g.isAir(pos)` - to najprostsza droga do: hub na pniach, mchu u podstawy, mrowisk, igliwia i owoców na gałęziach. W 26.3 wiele z tego robią już wanilijne `attached_to_logs`, `place_on_ground`, `shelf_mushroom`, `pale_moss`.
- Rozmiar drzewa a `minimum_size` i `replaceable_by_trees`: drzewo jest anulowane, gdy w zasięgu korony są bloki niezastępowalne; drzewa 30-40 m wysokości i 10+ m korony (buk, dąb) będą często kolidować - używać `min_clipped_height`, luźniejszego `minimum_size` i własnego runa w `#replaceable_by_trees`.
- Proceduralnie vs szablony NBT: wanilia nie ma typu feature "template"; można napisać własne `Feature<C>` (konstruktor `Feature` jest publiczny, rejestr `Registries.FEATURE`), które w `generate(FeatureContext)` pobiera `context.getWorld().toServerWorld().getStructureTemplateManager().getTemplateOrBlank(id)` i stawia `StructureTemplate` z `StructurePlacementData` (losowy obrót/odbicie, `BlockRotStructureProcessor` do "zniszczeń", `BlockIgnoreStructureProcessor`). Plusy: pełna kontrola artystyczna (np. wykroty, pomnikowe dęby, olsza na szczudłach), minusy: powtarzalność (potrzeba 5-10 wariantów/gatunek), pliki NBT (kilkadziesiąt KB każdy), brak dopasowania do terenu, liście muszą mieć `persistent=false` i sensowne `distance` (albo rozłożą się/nie rozłożą). **Rekomendacja: procedura dla 95% drzew; szablony tylko dla drzew pomnikowych/wykrotów** (tak robi np. Wilder Wild dla niektórych struktur - NIE ZWERYFIKOWANE w tej sesji).
- Pnie 2x2 ("grube"): wanilia = `giant_trunk_placer`/`dark_oak_trunk_placer`/`mega_jungle` (pień 2x2, `TreeNode.giantTrunk=true` przesuwa koronę). Dla dębów pomnikowych (obwód >4 m -> średnica >1.3 m -> realistycznie 1-2 bloki) i buków 2x2 wystarczy; pnie 3x3 wymagają własnego placera. Sadzonki 2x2: `SaplingGenerator` (1.20.3+ klasa konkretna; konstruktor `SaplingGenerator(String id, Optional<RegistryKey<ConfiguredFeature<?,?>>> megaVariant, Optional<...> regularVariant, Optional<...> beesVariant)` lub wariant z `secondaryChance` i wariantami "secondary") - jeśli `megaVariant` obecny, 4 sadzonki w kwadracie wyrastają w drzewo 2x2.

### 4.8. Sadzonki i wzrost

- `SaplingBlock`: właściwość `stage` 0-1; random tick: jeśli światło >= 9 i `random.nextInt(7) == 0`, to `stage 0 -> 1`, a przy `stage 1` generuje drzewo (`SaplingGenerator.generate(...)`). Mączka kostna: 45% szansy (`random.nextFloat() < 0.45`) na postęp. Wartości z wiedzy ogólnej o 1.20-1.21 (do potwierdzenia).
- Realistyczny wzrost (siewka -> młodnik -> drzewo) można zrobić jako 2-3 stadia bloku sadzonki (`age`) z coraz większym modelem, a "las w wieku X" tylko przez generację (różne configured features: młodnik = gęste, niskie drzewa bez korony bocznej; drągowina; drzewostan dojrzały) - SS może wpływać na wzrost (tag `sereneseasons:*_crops` blokuje wzrost poza sezonem - sadzonki nie rosną zimą).

### 4.9. Mapowanie pokrojów polskich gatunków na placery (propozycja)

| Gatunek | Wysokość realna (m) / w blokach | Trunk placer | Foliage placer | Uwagi |
|---|---|---|---|---|
| Sosna zwyczajna *Pinus sylvestris* | 20-35 / 18-30 | `straight` (base 14, rand 6) + gałęzie tylko w górnej 1/3: **własny** lub `cherry_trunk_placer` (branch_start_offset_from_top -6, 2-3 gałęzie) | `pine`/`random_spread` na węzłach gałęzi (parasolowata, nieregularna) | goły pień, korona "parasol"; w młodniku stożek |
| Świerk pospolity *Picea abies* | 30-50 / 25-40 | `straight` | `spruce` (radius 2-3, trunk_height 1-2 -> gałęzie do ziemi) | wanilijny; stary = `giant` + `mega_pine` |
| Jodła pospolita *Abies alba* | 30-50 / 25-40 | `straight` | `spruce` z regularnymi okółkami (własny "layered cone", płaski wierzchołek u starych) | |
| Modrzew europejski *Larix decidua* | 25-40 / 20-35 | `straight` | `spruce` rzadszy (własny z `leaf_placement_attempts`) | liście sezonowe (3.5) |
| Buk zwyczajny *Fagus sylvatica* | 30-40 / 25-35 | `fancy` (wysoki pień, gałęzie w górnej połowie) | `fancy` na wielu węzłach, radius 3-4 | kopulasta, szeroka korona |
| Dąb szypułkowy *Quercus robur* | 25-35 / 20-30 | **własny "giant fancy"**: pień 2x2 u starych + grube (2-szer.) konary | `blob`/`fancy` radius 4-5 | najgrubsze pnie; wanilijny fancy_oak jako baza |
| Brzoza brodawkowata *Betula pendula* | 20-30 / 15-25 | `straight` smukły | `cherry_foliage_placer` (hanging_leaves_chance 0.5+) daje **zwisające gałązki** | |
| Olsza czarna *Alnus glutinosa* | 20-30 / 15-25 | `straight`/`forking` | `blob` wąski, stożkowaty u młodych | **`mangrove_root_placer` = "olsza na szczudłach" w olsie**, `surface_water_depth_filter` 1-2 |
| Grab pospolity *Carpinus betulus* | 15-25 / 12-20 | `forking` | `blob` radius 3, gęsty | II piętro w grądzie |
| Jesion wyniosły *Fraxinus excelsior* | 30-40 / 25-35 | `forking`/własny (kilka grubych, wznoszących się konarów) | `random_spread` (ażurowa korona) | |
| Klon zwyczajny *Acer platanoides* / jawor *A. pseudoplatanus* | 20-30 / 18-25 | `fancy` | `blob` radius 4 | |
| Lipa drobnolistna *Tilia cordata* | 20-30 / 18-25 | `fancy` | `blob` radius 4, gęsty | |
| Wierzba biała *Salix alba* / płacząca | 15-25 / 12-20 | `forking` krótki, gruby | `cherry` z hanging_leaves_extension_chance wysokim + **własny blok "witki" rosnący w dół** (jak weeping_vines) | nad wodą |
| Osika *Populus tremula* | 20-30 / 18-25 | `straight` wysoki | `blob` radius 2-3 tylko na szczycie | drżące liście = cząsteczki? |
| Topola biała/czarna *Populus alba/nigra* | 25-35 / 20-30 | `poplar_trunk_placer` (26.3) | `poplar_foliage_placer` | wanilijna topola 26.3 |
| Kosodrzewina *Pinus mugo* | 1-3 / 1-3 | `straight` base 1 (kłoda cienka lub bez kłody) | `bush` radius 2 | wielopniowa: kilka feature obok siebie / `upwards_branching` |
| Jarząb *Sorbus aucuparia* | 5-15 / 5-12 | `straight`/`forking` | `blob` radius 2 | owoce: `attached_to_leaves` z blokiem owoców |

Uwaga do skali: przy 1 blok = 1 m realne wysokości (buk 35 m) dają drzewa 3-5x wyższe od wanilijnych (dąb 4-7 bloków). To ma konsekwencje dla wydajności, `minimum_size` i wysokości świata; użytkownik chce "rzeczywistych wymiarów" - **do potwierdzenia, czy 1 blok = 1 m obowiązuje także dla drzew** (patrz sekcja 11).

## 5. Piętra lasu jako bloki: podszyt, jagody, runo, mchy, grzyby, martwe drewno, mrowiska, ściółka, gleby

### 5.1. Ściółka liściowa - wanilijny `leaf_litter` (wzór dla igliwia)

Źródło: https://minecraft.wiki/w/Leaf_Litter
- Dodana w Java 1.21.5 (snapshot 25w02a, "Spring to Life"). Razem z nią: `bush`, `wildflowers`, `firefly_bush`, `cactus_flower`, powalone drzewa.
- Blockstate: `segment_amount` 1-4 (do 4 kawałków w jednym bloku, dokładanie kolejnych kliknięciem) + `facing` (N/S/E/W, ustawiany przeciwnie do gracza). Ten sam model "segmentowy" mają `wildflowers` i `pink_petals` (klasa `FlowerbedBlock`/`SegmentedBlock` - nazwa klasy do potwierdzenia w Yarn 26.x).
- Wymaga pełnej, solidnej górnej ściany bloku pod spodem. Twardość 0, dowolne narzędzie. Palna (spala się jako paliwo 5 s = pół przedmiotu). Przezroczysta. Wypłukiwana przez wodę/lawę. Kompost 30%.
- Otrzymywanie: łamanie generowanej; **wytop dowolnych liści w piecu daje ściółkę**.
- Kolor: tint z mapy `dry_foliage.png` (nowa colormap obok `foliage.png`/`grass.png`): brązy w większości biomów, szary w pale garden. Dla własnego bloku igliwia można użyć własnego ColorProvider albo stałej tekstury bez tintu.
- Generuje się naturalnie w: forest, dark forest, dappled forest (nowy biom 26.x), wooded badlands, abandoned camps. Wanilijnie stawiana m.in. dekoratorem drzewa `place_on_ground`.

### 5.2. Martwe drewno leżące - patrz 4.6 (`fallen_tree`).

### 5.3. Jagody - wzór `sweet_berry_bush` (https://minecraft.wiki/w/Sweet_Berry_Bush)

- Klasa wanilijna: `SweetBerryBushBlock` (Yarn), blockstate `age` 0-3: 0 młoda roślina, 1 bez owoców, 2 "trochę owoców" (zbiór 1-2, reset do age 1), 3 pełne (zbiór 2-3, reset do 1).
- Wzrost: random tick, **20% szansy na tick jeśli nie dojrzały**, wymagane światło >= 10 nad krzewem, blok nad nim nie może być pełny nieprzezroczysty. Mączka kostna +1 stadium bez względu na światło; przy age 3 wyrzuca owoc.
- Zbiór prawym klikiem (bez narzędzia), łamanie: age 3 -> 2-3 owoce, age 2 -> 1-2, fortuna +1/poziom. Kompost 30%.
- Podłoża: grass block, dirt, coarse dirt, rooted dirt, farmland, podzol, mycelium, moss block, mud, muddy mangrove roots (w 26.1 zapewne tag `#supports_vegetation` - do potwierdzenia).
- Spowalnia byty do ~34% prędkości i zadaje 1 HP/tick przy ruchu poziomym; lisy odporne; niweluje obrażenia od upadku.
- Generacja: tajga i śnieżna tajga - szansa 1/12 na chunk (rarity_filter 12), także old growth pine/spruce taiga i "abandoned camps".
- Wniosek dla naszych jagód: borówka czarna (*Vaccinium myrtillus*), brusznica (*V. vitis-idaea*), żurawina (*Oxycoccus palustris*), malina (*Rubus idaeus*), jeżyna (*Rubus fruticosus agg.*), poziomka (*Fragaria vesca*), bagno zwyczajne (*Rhododendron tomentosum* - trujące, bez owoców jadalnych) -> jedna wspólna klasa `BerryBushBlock` parametryzowana: `maxAge`, item owocu, ilości, czy kłuje (malina/jeżyna: tak, jak sweet berry; borówka: nie), wysokość hitboxu (borówka ~0.3 m -> niski model; malina/jeżyna 1-2 bloki), sezon owocowania z SS (sekcja 7).

### 5.4. Podłoża i tagi dla roślin - patrz 9.1 (`#supports_vegetation`, `#dirt`, `#substrate_overworld`).

### 5.5. Podszyt - krzewy (wanilijny `bush` jako wzór, https://minecraft.wiki/w/Bush)

- Wanilijny `bush` (1.21.5): roślina 1-blokowa, model krzyżowy (cross), bez kolizji, **tint koloru trawy** (biome grass color, np. #64C73F sparse jungle, #90814D badlands), sadzona na blokach ziemnych (grass, dirt, coarse, rooted, podzol, mycelium, farmland, mud, muddy mangrove roots, moss), nie do doniczki; drop tylko nożycami/silk touch; twardość 0; palny; kompost 30%. Generuje się w forest, birch forest, old growth birch forest, plains, windswept hills/gravelly/forest, brzegi rzek, abandoned camps. Klasa: prawdopodobnie `ShortPlantBlock`/`BushBlock` (do potwierdzenia).
- Krzewy polskie i propozycja bloków:

| Krzew | Łac. | Wysokość realna | Propozycja bloku |
|---|---|---|---|
| Leszczyna pospolita | *Corylus avellana* | 2-5 m | 2-3-blokowy wielopędowy: dolny blok "łodygi" (cross, wiele pędów) + górne "liście krzewu" (LeavesBlock bez rozkładu); owoce (orzechy) jako stan `fruit` we wrześniu |
| Kruszyna pospolita | *Frangula alnus* | 1-3 m | 2-blokowy (`TallPlantBlock` z `half`) lub mini-drzewo `bush` foliage |
| Jałowiec pospolity | *Juniperus communis* | 1-3 m (do 5) | 1-2-blokowy zimozielony, kolumnowy; model 3D (nie cross) |
| Bez czarny | *Sambucus nigra* | 3-6 m | mini-drzewo (feature `tree`, `straight` 2 + `bush` foliage), kwiaty V-VI (białe baldachy jako stan), owoce IX |
| Kalina koralowa | *Viburnum opulus* | 2-4 m | 2-blokowy z czerwonymi owocami jesienią/zimą |
| Trzmielina pospolita | *Euonymus europaeus* | 2-5 m | 2-blokowy, różowe owoce X |
| Jarzębina | *Sorbus aucuparia* | patrz 4.9 | małe drzewo |
| Głóg jednoszyjkowy | *Crataegus monogyna* | 2-6 m | mini-drzewo cierniste (obrażenia jak sweet_berry_bush), owoce X |
| Tarnina | *Prunus spinosa* | 1-3 m | 2-blokowy cierniste, kwitnie IV przed liśćmi, owoce X |
| Malina, jeżyna | *Rubus idaeus*, *R. fruticosus* agg. | 1-2 m | jak sweet_berry_bush (spowalnia, kłuje), 4 stadia |
| Borówka czarna | *Vaccinium myrtillus* | 0.2-0.5 m | niski blok (hitbox 6/16), 4 stadia, bez kolizji, dominuje w borach |
| Wrzos | *Calluna vulgaris* | 0.2-0.5 m | 1-blokowy, kwitnie VIII-IX (stan `flowering`) |
| Bagno zwyczajne | *Rhododendron tomentosum* | 0.5-1.5 m | torfowiska/bory bagienne; trujące (bez zbioru) |
| Kosodrzewina | *Pinus mugo* | 1-3 m | patrz 4.9 |

- Technika bloków 2-wysokich: `TallPlantBlock` (właściwość `half` = upper/lower, stawianie sprawdza wolne miejsce nad, łamanie jednej połowy niszczy drugą). Bloki wieloblokowe nieregularne (krzew 2x2x2): albo jako mini-feature `tree` z `bush_foliage_placer` (kłoda + liście, jak wanilijny "dżunglowy krzak"), albo własny blok z kilkoma częściami (jak łóżko/drzwi) - większy koszt. **Rekomendacja: krzewy > 2 m jako feature `tree` (trunk 1-2 + bush foliage z osobnym blokiem liści krzewu), krzewy <= 2 m jako `TallPlantBlock`/1-blokowe.**

### 5.6. Runo - rośliny zielne

- Wanilia (26.x): 1-blokowe `FlowerBlock` (z efektem podejrzanego gulaszu), `ShortPlantBlock` (short_grass, fern; `replaceable`), 2-blokowe `TallPlantBlock`/`TallFlowerBlock` (tall_grass, large_fern, rose_bush, peony, lilac, sunflower), segmentowe `FlowerbedBlock` (`pink_petals`, `wildflowers`, 1-4 segmenty + facing - **ten sam model co leaf litter**), `bush`, `firefly_bush` (świeci, cząsteczki świetlików nocą - wzór dla "łąki nocą"), `dead_bush`, mchy. Wzrost trawy z mączki kostnej: `Fertilizable`.
- Proponowane gatunki (nazwy PL/łac., typ bloku, siedlisko, sezon):

| Roślina | Łac. | Blok | Siedlisko | Kwitnienie |
|---|---|---|---|---|
| Zawilec gajowy | *Anemone nemorosa* | segmentowy (1-4) biały | grąd, buczyna | III-V (geofit wiosenny: potem znika!) |
| Przylaszczka pospolita | *Hepatica nobilis* | 1-blokowy niebieski | grąd | III-IV |
| Konwalia majowa | *Convallaria majalis* | 1-blokowy | bory mieszane, grądy | V |
| Szczawik zajęczy | *Oxalis acetosella* | segmentowy niski | bory, buczyny (cień) | IV-V |
| Czosnek niedźwiedzi | *Allium ursinum* | 1-blokowy, łanowy (noise_based_count) | łęgi, buczyny żyzne | IV-V, chroniony częściowo |
| Śnieżyczka przebiśnieg | *Galanthus nivalis* | 1-blokowy | łęgi, grądy | II-III (przez śnieg!) |
| Nerecznica samcza | *Dryopteris filix-mas* | 1-blokowy (fern) | cieniste lasy | - |
| Orlica pospolita | *Pteridium aquilinum* | 2-blokowy (large_fern) | bory, zręby | jesienią brązowieje |
| Wrzos | *Calluna vulgaris* | 1-blokowy | bory suche, wrzosowiska | VIII-IX |
| Borówka czarna, brusznica | *Vaccinium myrtillus*, *V. vitis-idaea* | niski krzew (5.3) | bory | V; owoce VII-VIII / VIII-IX |
| Turzyce | *Carex* spp. | 1-blokowy trawiasty (tint trawy) | olsy, torfowiska | - |
| Wełnianka pochwowata | *Eriophorum vaginatum* | 1-blokowy, białe "puchy" V-VI | torfowiska wysokie | V-VI |
| Śmiałek pogięty, trzcinnik | *Deschampsia flexuosa*, *Calamagrostis* | trawy (short/tall_grass retekstura) | bory / zręby | - |
| Trzcina | *Phragmites australis* | 2-3-blokowy (jak sugar_cane / `block_column`) | brzegi, olsy | - |
| Kopytnik, marzanka wonna, gajowiec | *Asarum europaeum*, *Galium odoratum*, *Lamium galeobdolon* | 1-blokowe runo grądowe | grądy, buczyny | |

- Tagi: wszystkie rośliny runa -> `#minecraft:replaceable_by_trees` (i `replaceable` w ustawieniach bloku dla traw), kwiaty -> `#minecraft:flowers`/`small_flowers`/`bee_attractive`, `c:flowers`, kompost. Podłoże: `#supports_vegetation` (26.1).
- Sezonowość runa: geofity wiosenne (zawilec, przylaszczka, czosnek) w rzeczywistości znikają latem - technicznie: stan `dormant` (model = nic/"ściółka") lub usunięcie bloku i ponowna generacja jest niemożliwa (generacja jednorazowa) -> zostawić blok z modelem "liście bez kwiatów" (patrz sekcja 7).

### 5.7. Warstwa mszysta

- Wanilia: `moss_block` (pełny), `moss_carpet` (1/16 wysokości), `pale_moss_block`, **`pale_moss_carpet` (ma właściwości `bottom` + `north/east/south/west` = none/low/tall - "wspina się" po ścianach bloków sąsiednich)**, `pale_hanging_moss` (zwisający). Feature `pale_moss_patch` i `vegetation_patch` (kolumny mchu z `vegetation_chance` na runo) oraz `multiface_growth` (`MultifaceGrowthBlock`, jak glow lichen: bloki wielościenne rosnące na dowolnych ścianach - **epifity na pniach**).
- Polskie mchy do odwzorowania jako dywany (moss_carpet z różnymi teksturami): rokietnik pospolity (*Pleurozium schreberi*) - bory sosnowe; gajnik lśniący (*Hylocomium splendens*) - bory świerkowe; płonnik pospolity (*Polytrichum commune*) - wilgotne bory, wyższy (blok 4/16); widłoząb miotlasty (*Dicranum scoparium*); torfowce (*Sphagnum* spp.) - torfowiska, jako pełny blok "torfowiec" (odpowiednik moss_block, na torfie) + dywan; mech u podstawy pni: model `pale_moss_carpet` z bokami "tall".
- Rozmieszczenie: `vegetation_patch` (surface: floor, `xz_radius` 4-8, `vegetation_chance` 0.3) na `#moss_replaceable` w cienistych borach; `noise_threshold_count` dla "łanów"; dekorator drzewa `pale_moss`-podobny własny (mech na dolnej części pnia od N - realistycznie od strony wilgotnej/północnej).

### 5.8. Grzyby

- Wanilia: `red_mushroom`, `brown_mushroom` (1-blokowe, światło < 13 do postawienia, rozrastają się random tickiem, mączka = huge mushroom), `huge_*_mushroom`, Nether fungi, **od 26.3 "shelf mushrooms" (huby) na kłodach** - wanilijny blok naścienny stawiany dekoratorem `shelf_mushroom` i na powalonych topolach (dokładne ID bloku, warianty i model - NIE ZWERYFIKOWANE; sprawdzić w changelogu 26.3 Snapshot 1-8).
- Polskie grzyby (jadalne/trujące) jako 1-blokowe cross/3D: borowik szlachetny (*Boletus edulis*), podgrzybek brunatny (*Imleria badia*), kurka (*Cantharellus cibarius*), maślak zwyczajny (*Suillus luteus*, pod sosną), koźlarz babka (*Leccinum scabrum*, pod brzozą), opieńka miodowa (*Armillaria mellea*, kępy na pniakach), muchomor czerwony (*Amanita muscaria*, pod brzozą/świerkiem), muchomor sromotnikowy (*A. phalloides*, pod dębem/bukiem), purchawka (*Lycoperdon perlatum*); huby na pniach: hubiak pospolity (*Fomes fomentarius*, buk/brzoza), pniarek obrzeżony (*Fomitopsis pinicola*, iglaste), żółciak siarkowy (*Laetiporus sulphureus*, dąb), czyreń (*Phellinus*). Mikoryza = generować dany grzyb tylko przy określonym gatunku drzewa: dekorator drzewa `place_on_ground` z odpowiednim providerem lub własny dekorator "mycorrhiza".
- Blok naścienny na pniu: `HorizontalFacingBlock` z modelem "półki" + warunek `canPlaceAt` = blok za nim w `#logs`; stawianie przez `attached_to_logs` (26.3) lub własny dekorator; na martwym drewnie przez `log_decorators` w `fallen_tree`.
- Sezonowość: grzyby kapeluszowe pojawiają się VIII-XI (stan `visible`/losowe "wyrastanie" z grzybni?) - najprościej: blok grzybni niewidoczny? Zbyt kosztowne; alternatywa: grzyby generowane na stałe, ale dropią owocniki tylko w sezonie (7).

### 5.9. Mrowiska, pniaki, wykroty, martwe drewno stojące

- Mrowisko (*Formica rufa*, kopce do 1-2 m wys., śr. do 2-3 m): blok 1x1 (model kopca 16x10x16, tekstura igliwia) lub 2x2x1 (4 bloki `part`) - rekomendacja: 1 blok "duży kopiec" + wariant "mały" (2 stany `size`); cząsteczki mrówek; generacja `simple_block` + `rarity_filter` 3-6 w borach sosnowych/świerkowych na `#dirt`.
- Pniak: wanilia = pionowa kłoda w `fallen_tree` (stump); własny blok "pniak" (model 12x8x12 z korzeniami, `facing`) daje lepszy wygląd; ścinanie drzewa przez gracza nie zostawia pniaka (wanilia) - opcjonalnie: łamanie najniższej kłody zostawia pniak (event `PlayerBlockBreakEvents.AFTER`).
- Wykrot (drzewo wywrócone z bryłą korzeniową): szablon NBT (4.7) lub własny feature: pionowa "tarcza" 3x3 z `rooted_dirt`/kłód + leżący pień + dół po korzeniach.
- Martwe drzewo stojące (posusz, "świerk kornikowy"): feature `tree` z providerem pnia = `stripped_*_log`/własny blok "martwe drewno" + foliage placer z `foliage_provider` = powietrze? (nie działa - foliage wymaga bloku) -> własny foliage placer "bez liści" lub `random_spread` z bardzo małym `leaf_placement_attempts` i blokiem "suche gałęzie"; huby z `shelf_mushroom`.
- Stadia rozkładu kłód: bloki `dead_log` (kora), `decaying_log` (z mchem, tekstura z mchem), `rotten_log` (miękkie, `moss_block`-podobne) - 3 bloki x liściaste/iglaste, bez pełnych zestawów drewna.

### 5.10. Gleby i ściółka jako bloki

- Wanilia: `dirt`, `coarse_dirt`, `rooted_dirt`, `podzol` (bory iglaste - realnie bielice!), `mud`, `clay`, `gravel`, `sand`, `mycelium`, `moss_block`, `mud` (bagna). 26.1 podzieliło tagi: `#dirt`, `#mud`, `#moss_blocks`, `#grass_blocks`, zbiorczo `#substrate_overworld`.
- Nowe bloki proponowane: **torf** (*peat*; torfowiska wysokie/niskie, bory bagienne; paliwo - torf jak węgiel o niższej wartości; pod nim `mud`/woda), **igliwie** (`needle_litter`, segmentowy jak `leaf_litter` 1-4, tekstura rdzawa bez tintu, pod sosnami/świerkami, drop przy wytopie liści iglastych), **ściółka liściowa** = wanilijny `leaf_litter` (tint dry_foliage) - ewentualnie warianty "bukowa" (miedziana, długo zalegająca), **próchnica/mor** (ciemna warstwa pod ściółką - opcjonalnie jako `coarse_dirt` retekstura per biom przez surface rules), **bielica** = `podzol` (nazwa PL w lang), **gleba brunatna** = `dirt`, **mada** (łęgi) = `mud`/`clay`, **rędzina** (Jura) = `dirt` na wapieniu, **czarnoziem** = ciemny wariant `dirt`, **piasek wydmowy** = `sand`.
- Każdy nowy blok gleby: tagi `#dirt` (lub `#mud`), `#substrate_overworld`, `#supports_vegetation`, `#supports_crops`? (torf - nie), `#mineable/shovel`, `#animals_spawnable_on`, `#wolves_spawnable_on`, `#foxes_spawnable_on`, `#azalea_grows_on`, `#moss_replaceable`, `#sniffer_diggable_block`, `#enderman_holdable`, `#bamboo_plantable_on`? (nie), `#dead_bush_may_place_on`, `#supports_mushroom`? (26.1 - nazwa do potwierdzenia); ścieżki mobów (`PathNodeType`) standardowe.
- Rozmieszczenie gleb należy do surface rules (raport o generacji terenu), tu tylko: `disk` feature dla plam torfu/gliny, `alter_ground` dekorator (podzol pod świerkami/sosnami, igliwie), `place_on_ground` (ściółka).

## 6. Rozmieszczanie: placed features, modifiers, vegetation patches, random_selector, gęstość drzew per biom, GenerationStep, las gospodarczy vs naturalny

### 6.1. Placement modifiers (placed feature) - stan 26.3

Źródło: https://minecraft.wiki/w/Placed_feature. Modyfikatory stosowane są **po kolei**; każdy przyjmuje listę pozycji i zwraca listę pozycji (0..n). Bez modyfikatorów feature stawiany jest raz w NW rogu chunku na dnie świata.

| Modyfikator | Pola | Semantyka |
|---|---|---|
| `biome` | - | zostawia pozycję tylko, jeśli biom w tym miejscu ma ten placed feature w swojej liście; nie działa w feature'ach zagnieżdżonych (random_selector) |
| `block_predicate_filter` | `predicate` | filtr predykatem blokowym (`would_survive`, `matching_blocks`, `all_of`, `any_of`, `not`, `replaceable`, `solid`, `has_sturdy_face`, `inside_world_bounds`, `matching_fluids`, `matching_block_tag`, `unobstructed`, `true`) |
| `count` | `count` (int provider 0-4096) | mnoży pozycje; kilka `count` mnoży się |
| `count_on_every_layer` | `count` | na każdej "warstwie" oddzielonej powietrzem/wodą/lawą losuje N pozycji (jaskinie/nawisy) |
| `cuboid` (nowy) | `xz_size` 1-16, `y_size` 1-16, `include_interior`, `include_edges` | powiela pozycję w prostopadłościan - **zastępuje dawny `random_patch` (patrz 6.2)** |
| `environment_scan` | `direction_of_search` up/down, `max_steps` 1-32, `target_condition`, opcjonalnie `allowed_search_condition` | skanuje w pionie aż do spełnienia warunku |
| `fixed_placement` (od 1.21-pre2) | `positions` | stałe współrzędne |
| `height_range` | `height` (height provider: `uniform`, `biased_to_bottom`, `very_biased_to_bottom`, `trapezoid`, `constant`) | ustawia Y |
| `heightmap` | `heightmap` = MOTION_BLOCKING / MOTION_BLOCKING_NO_LEAVES / OCEAN_FLOOR / OCEAN_FLOOR_WG / WORLD_SURFACE / WORLD_SURFACE_WG | Y = 1 nad heightmapą |
| `in_square` | - | +rand(0..15) do X i Z (rozrzut w chunku) |
| `noise_based_count` | `noise_to_count_ratio`, `noise_factor`, `noise_offset` | liczba kopii = ceil((noise(x/f, z/f)+offset)*ratio); noise<=0 -> 0 (wanilia: trawa, paprocie) |
| `noise_threshold_count` | `noise_level`, `below_noise`, `above_noise` | liczba zależna od progu szumu (wanilia: gęstość kwiatów) |
| `offset` (nowy) | `x`,`y`,`z` int providers -16..16 | przesunięcie |
| `random_chance` (nowy) | `chance` 0-1 | bramka losowa |
| `randomly_selected` (nowy) | lista modyfikatorów | losuje jeden modyfikator z listy |
| `rarity_filter` | `chance` | pozycja przechodzi z p = 1/chance |
| `surface_relative_threshold_filter` | `heightmap`, `min_inclusive`, `max_inclusive` | odległość od powierzchni w zakresie |
| `surface_water_depth_filter` | `max_water_depth` | odrzuca, jeśli woda nad dnem głębsza niż N (drzewa: 0) |
| `carving_mask` | USUNIĘTY w 1.21.2 (24w33a) | - |

Typowy łańcuch dla drzew w wanilii: `count` -> `in_square` -> `surface_water_depth_filter(0)` -> `heightmap(OCEAN_FLOOR)` -> `block_predicate_filter(would_survive: sapling)` -> `biome`.

### 6.2. Zmiany w 26.1 dotyczące roślinności (wg minecraft.wiki)

- POTWIERDZONE (Minecraft 26.1 Pre-Release 1, minecraft.net): "The flower, flower_no_bonemeal, and random_patch feature types have been removed. Instead, patches can be expressed as a sequence of count and random_offset placement modifiers." Czyli kępa roślin = placed feature: `count(N)` -> `random_offset`/`offset`(xz_spread, y_spread) -> `heightmap`/`environment_scan` -> `block_predicate_filter` -> configured feature `simple_block`. Na wiki modyfikator figuruje jako `offset`; w notce patchowej jako `random_offset` - nazwę w JSON zweryfikować w konkretnej wersji. Dodatkowo nowe: `cuboid`, `random_chance`, `randomly_selected`.
- Praktyczny skutek: **tutoriale sprzed 26.1 (kwiaty/trawy przez `random_patch`) są nieaktualne dla 26.x**; przy 1.21.x nadal działa `random_patch` {`tries` (dom. 128), `xz_spread` (7), `y_spread` (3), `feature`}.
- 26.1 przebudowało tagi podłoża: `#dirt` rozbito na `#dirt`, `#mud`, `#moss_blocks`, `#grass_blocks`, zbiorczo `#substrate_overworld`; dodano tagi "wsparcia roślin": `#supports_vegetation` (krzewy, trawy, kwiaty), `#supports_crops`, `#supports_sugar_cane`, `#supports_cactus`, `#supports_lily_pad` i inne. **Nowe bloki gleby (torf, igliwie) muszą trafić do tych tagów, inaczej wanilijne rośliny na nich nie przetrwają.**
- Pozostałe feature'y roślinne (26.3): `vegetation_patch` (kolumny na podłodze/suficie, `depth`, `xz_radius`, `vegetation_chance`, `extra_bottom_block_chance`, `surface`, `ground`, `replaceable`, `vegetation_feature`), `waterlogged_vegetation_patch`, `block_column` (kolumny warstw, kierunek), `simple_block` (jeden blok, opcjonalny schedule_tick), `root_system`, `fallen_tree`, `random_selector` (lista {feature, chance} + default), `random_boolean_selector`, `simple_random_selector`, `tree`, `huge_*_mushroom`, `bamboo`, `kelp`, `seagrass`, `twisting_vines`, `weeping_vines`, `multiface_growth` (glow lichen/mech ścienny - **wzór dla mchów naściennych na pniach**), `sculk_patch`, `spring_feature`, `disk` (plamy piasku/gliny/żwiru), `ore`.

### 6.3. Gęstość drzew w wanilii (ile drzew/chunk)

Źródło: `data/minecraft/worldgen/placed_feature/trees_taiga.json` z mcmeta 1.21.5 (https://raw.githubusercontent.com/misode/mcmeta/1.21.5-data/data/minecraft/worldgen/placed_feature/trees_taiga.json):
```json
{"feature":"minecraft:trees_taiga","placement":[
 {"type":"minecraft:count","count":{"type":"minecraft:weighted_list","distribution":[{"data":10,"weight":9},{"data":11,"weight":1}]}},
 {"type":"minecraft:in_square"},
 {"type":"minecraft:surface_water_depth_filter","max_water_depth":0},
 {"type":"minecraft:heightmap","heightmap":"OCEAN_FLOOR"},
 {"type":"minecraft:biome"}]}
```
- Tajga: **10 prób drzewa na chunk (90%) lub 11 (10%)** = w kodzie `PlacedFeatures.createCountExtraModifier(10, 0.1f, 1)`. To próby - część nie wychodzi (brak miejsca, `minimum_size`), realnie ok. 7-9 drzew/chunk (16x16 m = 256 m^2), czyli ~300 drzew/ha. Realny las: sosnowy bór dojrzały ~400-700 drzew/ha, młodnik kilka tysięcy/ha (wartości z sekcji leśnych raportów - tu nie badane).
- Forest: POTWIERDZONE - `trees_birch_and_oak_leaf_litter.json` (1.21.5, https://raw.githubusercontent.com/misode/mcmeta/1.21.5-data/data/minecraft/worldgen/placed_feature/trees_birch_and_oak_leaf_litter.json) ma identyczny łańcuch: `count` weighted_list {10: w9, 11: w1} -> `in_square` -> `surface_water_depth_filter 0` -> `heightmap OCEAN_FLOOR` -> `biome`; configured feature `trees_birch_and_oak_leaf_litter` (random_selector: brzoza z leaf litter, fancy oak, dąb z leaf litter - dekorator `place_on_ground`). **Wanilijny las liściasty i tajga = 10-11 prób drzew/chunk.** Pozostałe (z pamięci klasy `VegetationPlacements`, do weryfikacji w mcmeta): dark forest `count(16)` + `dark_forest_vegetation` (random_selector: huge mushrooms, dark oak, birch, oak), flower forest 6, birch forest 10, old growth taiga 10, windswept forest 3, sparse jungle 2, savanna 1, plains rarity 20 (1 drzewo na 20 chunków), meadow rarity 100.
- Heightmap `OCEAN_FLOOR` + `surface_water_depth_filter 0` -> drzewa tylko na suchym gruncie; dla olsów/łęgów (olsza na podmokłym) można dopuścić `max_water_depth` 1-2 jak mangrowce (wanilia mangrove: 5?) - do sprawdzenia.
- Mieszanie gatunków: configured feature typu `random_selector` {`features`: [{`feature`: placed_feature, `chance`: 0.0-1.0}...], `default`: placed_feature}. Wanilia forest: brzoza 20%, fancy oak 10%, reszta dąb. Uwaga: **chance są sprawdzane po kolei (pierwszy trafiony wygrywa), nie jako wagi**: udział i-tego = chance_i * prod(1-chance_j dla j<i). Do zadanych proporcji (np. bór sosnowy 80% sosna, 15% brzoza, 5% dąb) trzeba przeliczyć: brzoza 0.15/(1-0.05)... lub użyć `simple_random_selector` (równe wagi) / kilku wpisów tego samego gatunku.
- Kolejność generacji (`GenerationStep.Feature` / `GenerationStep.Decoration`): RAW_GENERATION, LAKES, LOCAL_MODIFICATIONS, UNDERGROUND_STRUCTURES, SURFACE_STRUCTURES, STRONGHOLDS, UNDERGROUND_ORES, UNDERGROUND_DECORATION, FLUID_SPRINGS, **VEGETAL_DECORATION** (drzewa, trawa, kwiaty, grzyby - kolejność wewnątrz kroku = kolejność w liście biomu `features[9]`; **wszystkie biomy muszą mieć feature'y tego kroku w tej samej kolejności względnej, inaczej błąd "Feature order cycle"**), TOP_LAYER_MODIFICATION (śnieg/lód). W Fabric: `BiomeModifications.addFeature(BiomeSelectors.includeByKey(...), GenerationStep.Feature.VEGETAL_DECORATION, placedFeatureKey)` lub bezpośrednio w JSON biomu (własne biomy).
- Las gospodarczy w rzędach vs naturalny: wanilia nie ma "siatki" - `in_square` daje rozrzut losowy. Rzędy: własny `PlacementModifier` (Java, `PlacementModifierType` rejestrowany w `Registries.PLACEMENT_MODIFIER_TYPE`) zwracający pozycje na siatce (np. co 2 bloki w rzędzie, rzędy co 3, z niewielkim jitterem i orientacją zależną od szumu/chunku) + `count` = 1 + `biome`; alternatywnie `fixed_placement` w JSON (stałe pozycje w chunku, np. siatka 3x2) - ale nie da "linii" ciągłych między chunkami bez własnego kodu. Naturalny: `count` + `in_square` + `noise_based_count` (kępowość) + `random_selector` proporcje.

## 7. Fenologia: kwitnienie/owocowanie/zmiana kolorów wg pory roku Serene Seasons - przełączanie stanów bloków i koszt

### 7.1. Trzy mechanizmy i ich koszt

| Mechanizm | Jak | Co daje | Koszt | Kiedy użyć |
|---|---|---|---|---|
| A. Tint (kolor) | `BlockColorProvider` -> `BiomeColors.getFoliageColor` (SS podmienia resolver) lub własny provider czytający `SeasonHelper.getSeasonState(world)` | zieleń -> żółć/pomarańcz/brąz bez zmiany tekstury; zero zmian stanu | zerowy po stronie serwera; klient liczy kolor per blok przy rebuildzie chunku (cache w `ColorResolver` z blendingiem biomów) | wszystkie liście liściaste, trawy, runo; podstawowa jesień |
| B. Właściwość bloku + random tick | `EnumProperty<Phase> PHASE` (np. `green, flowering, fruiting, autumn, bare`), w `randomTick` odczyt podpory roku SS i ewentualna zmiana stanu (`world.setBlockState(pos, state.with(PHASE, ...), Block.NOTIFY_LISTENERS)`) | inna tekstura/model per faza (kwiaty, owoce, nagie gałęzie, igły), inne dropy/interakcje, inne cząsteczki | serwer: liście już mają random tick (rozkład) - narzut 1 odczyt sezonu (cache statyczny per tick) + rzadkie `setBlockState`; sieć: każda zmiana stanu = pakiet bloku (przy 10^5 liści w widoku -> rozłożone na minuty: przy `randomTickSpeed` 3 blok dostaje tick średnio co ~1365 ticków ≈ 68 s; pełne przebarwienie lasu w 2-4 min - **efekt naturalnie stopniowy**); więcej stanów bloku = więcej wpisów palety chunków (nieznaczny) | modrzew (bare), owoce (jarzębina, leszczyna, jagody), kwitnienie (czeremcha, lipa, wrzos), geofity, "puch" |
| C. Tylko render (bez stanu) | model wybierany wg pory: niemożliwe w wanilijnym pipeline bez stanu; możliwe przez Fabric Model Loading API (`ModelLoadingPlugin` + własny `BakedModel` wybierający quady wg globalnej fazy) - wymaga przebudowy chunków (klient) przy zmianie fazy | zmiana wyglądu bez pakietów sieciowych | klient: rebuild wszystkich chunków przy zmianie fazy (co 8 dni - akceptowalne); brak informacji o fazie na serwerze (owoce/dropy nie mogą od niej zależeć) | czysto wizualne (kwiaty lipy, kolory), gdy liczba stanów by eksplodowała |

- Rekomendacja: **A dla koloru** (darmowe z SS), **B dla wszystkiego, co ma skutek rozgrywkowy** (owoce, nagie gałęzie, zbiory), C tylko wyjątkowo. Uwaga na `persistent` liście postawione przez gracza - też powinny podlegać fazom (random tick działa niezależnie od `persistent`).
- Jednorazowy koszt B: liczba wariantów blockstate = liczba faz x 7 (`distance`) x 2 (`persistent`) x 2 (`waterlogged`) - dla 5 faz = 140 stanów na blok liści (wanilia 28); z 20 gatunkami = 2800 stanów - bez znaczenia dla palety globalnej (miliony), ale każdy stan potrzebuje wpisu w blockstate JSON (datagen: `BlockStateVariantMap.create(PHASE)` + `coordinate`/`multipart`).
- Kalendarz fenologiczny (przykład, podpory SS przy 8 dniach): EARLY_SPRING (III): przebiśnieg, leszczyna (kotki), nagie liściaste; MID_SPRING (IV): zawilce, przylaszczki, brzoza/modrzew pączki; LATE_SPRING (V): pełne liście, konwalia, czeremcha, kwitnienie jabłoni/głogu; EARLY_SUMMER (VI): lipa kwitnie, bez czarny, wełnianka; MID_SUMMER (VII): borówka owocuje, malina; LATE_SUMMER (VIII): brusznica, jeżyna, wrzos kwitnie, początek grzybów; EARLY_AUTUMN (IX): jarzębina, leszczyna orzechy, grzyby szczyt, brzoza żółknie; MID_AUTUMN (X): buk/dąb brązowe, klon czerwony, modrzew żółty, opad liści (leaf litter przyrasta?); LATE_AUTUMN (XI): nagie, modrzew bare; zima: nagie, śnieg; brusznica/kalina/głóg owoce zimą (pokarm ptaków).
- Ściółka rosnąca jesienią: opcjonalnie `randomTick` liści w fazie `autumn` stawia/zwiększa `leaf_litter` `segment_amount` pod drzewem (wymaga sprawdzenia bloku poniżej; koszt mały, efekt bardzo realistyczny; wiosną ściółka nie znika - rozkład: random tick 1/N na zmniejszenie segmentu).
- Bez SS: fallback "pory z czasu świata" (`world.getTimeOfDay()/24000 % 96`) w tej samej abstrakcji `SeasonProvider` - projekt nie powinien twardo zależeć od SS (licencja "All rights reserved"; użycie API przez `isModLoaded` jest bezpieczne).

## 8. Tekstury bloków 16x16: strategie, warianty, palety, licencje

(Sekcja oparta na wiedzy ogólnej o pipeline'ie assetów Minecrafta; w tej sesji nie pobrano stron o licencjach - patrz sekcja 11.)

### 8.1. Strategie tworzenia

| Strategia | Narzędzia | Plusy | Minusy | Zastosowanie |
|---|---|---|---|---|
| Ręcznie, piksel po pikselu | Aseprite, Paint.NET, GIMP, Blockbench (modele + tekstury) | najwyższa jakość, spójność z wanilią | 20 PNG x 15 gatunków = 300 tekstur; ~20-40 min/tekstura | kłody, deski, liście, owoce |
| Proceduralnie (skrypt) | Python + Pillow/NumPy: szum Perlina/Worleya -> płytki kory, plamki liści, wariacje odcienia; parametry per gatunek (sosna: pomarańczowe płaty; brzoza: białe z czarnymi "brwiami"; buk: gładka szara) | setki wariantów za darmo, spójna paleta, łatwe poprawki | wygląd "syntetyczny" bez ręcznej korekty; trzeba i tak dopracować | warianty kory, gleb, ściółki, mchów, kamieni; bazy pod ręczne poprawki |
| Retekstura wanilii "w stylu" | własna paleta zbliżona do wanilii (np. Faithful jako inspiracja stylu) | spójność | **nie kopiować pikseli wanilii** (patrz 8.3) | |
| Warianty tekstur (`blockstates` z wagami) | `"variants": {"": [{"model":"a","weight":3},{"model":"b"},{"model":"a","y":90},...]}` | losowość bez kodu; obroty `y: 0/90/180/270` (i `x`) | wiele modeli/tekstur | gleby, ściółka, mchy, kłody (2-3 warianty kory), liście (2 warianty) |
| Fabric Model Loading / "connected textures" | CTM (Continuity mod dla Fabric) - opcjonalna zależność | duże płaty kory bez powtórzeń | zależność kliencka | opcjonalnie |

- Rozdzielczość: trzymać 16x16 (spójność z wanilią; użytkownik nie prosił o HD). Liście: tekstura z ~30-45% pikseli przezroczystych, w skali szarości (tint), test w trybie Fast. Kłody: `log` (bok, powtarzalny pionowo) + `log_top` (słoje). Ściółka/igliwie: 4 modele segmentów jak wanilia (kopiować geometrię modelu, nie tekstury).
- Palety: wanilia używa ograniczonych palet (kilka odcieni na teksturę, "dithering" minimalny). Tekstury liści wanilii są w skali szarości (tint); trawa/liście brzozy/świerku mają "zapieczony" kolor. Do sezonowego tintu **wszystkie nasze liście w skali szarości**.
- Kolor tintu sezonowego: tekstura szara x kolor: jesienny pomarańcz `#D9821E`-podobny da dobre efekty tylko przy jasnej teksturze bazowej - projektować tekstury pod mnożenie (średnia jasność ~0.7).

### 8.2. Warianty i losowe obroty w JSON

- Bloki "ziemne" (gleba, ściółka, mchy, igliwie): wanilijne `grass_block`/`stone` używają 4 obrotów `y` (i `x` dla pełnych bloków) - w datagen `BlockStateModelGenerator.registerRandomHorizontalRotations`/`createBlockStateWithRandomHorizontalRotations` (nazwa Yarn do potwierdzenia).
- Kłody z 2-3 wariantami kory: warianty na `axis=y` i `axis=x/z` (poziome) osobno.
- Nowy format modeli przedmiotów (1.21.4+): `assets/<ns>/items/*.json` z `model.type` = `minecraft:model`, `minecraft:select`, `minecraft:range_dispatch`, `minecraft:condition`, `minecraft:composite` + `tints` - pozwala np. na ikonę owocu zależną od sezonu (`minecraft:select` po `custom_model_data` lub własny `ItemModel` property) bez kodu (własne właściwości wymagają kodu).

### 8.3. Licencje i prawa

- Assety Mojang (tekstury, modele, dźwięki) są własnością Mojang/Microsoft; Wytyczne użytkowania Minecrafta (Minecraft Usage Guidelines / EULA) zabraniają redystrybucji istotnych części gry, w tym assetów, jako własnych; mody mogą **odwoływać się** do tekstur wanilii w czasie działania (np. `minecraft:block/oak_log` w modelu, tint wanilijny), ale **nie powinny kopiować/modyfikować i dystrybuować plików PNG wanilii** w jarze moda (praktyka społeczności; dokładne brzmienie wytycznych NIE ZWERYFIKOWANE w tej sesji). Bezpieczna droga: własne tekstury od zera, ewentualnie wzorowane stylistycznie.
- Paczki tekstur/mody na CC: Faithful (32x) - licencja własna, wymaga uznania; wiele modów drzew (Terrestria - LGPL-3.0, Biomes O' Plenty - CC BY-NC-ND 4.0 (kod i assety **bez** zezwolenia na pochodne!), Regions Unexplored - LGPL? (do sprawdzenia), Wilder Wild - "All rights reserved"/własna (do sprawdzenia)). **Nie kopiować tekstur z BOP ani Wilder Wild.** Kod na LGPL można studiować i linkować, ale kopiowanie wymaga zachowania licencji.
- Dla naszego moda: wybrać licencję (np. MIT/LGPL dla kodu, CC BY 4.0 dla assetów) i zapisać w README - decyzja użytkownika.
- Fotografie referencyjne kory/liści: własne lub CC0 (Wikimedia Commons z podaną licencją) - tekstury pikselowe "z fotografii" (downscale) wyglądają źle w 16x16 i mogą naruszać licencję zdjęcia; lepiej rysować.

## 9. Tagi ważne dla zgodności i integracja z innymi modami

### 9.1. Tagi wanilijne (https://minecraft.wiki/w/Block_tag_(Java_Edition), stan 26.3)

| Tag | Skutek dodania własnego bloku |
|---|---|
| `minecraft:logs` | rozpoznawany jako kłoda: liście nie gniją w zasięgu 6, receptury desek/ognisk/łodzi używające `#logs`, `creaking_heart` |
| `minecraft:logs_that_burn` | ogień rozprzestrzenia się (wanilia: wszystkie prócz nether); paliwo |
| `minecraft:<gatunek>_logs` (np. `oak_logs`; w 26.3 także `pale_oak_logs`, `poplar_logs`) | tag per gatunek: receptura desek "z jednego gatunku" - dla naszych gatunków tworzymy własne `modid:beech_logs` i dodajemy je do `minecraft:logs` |
| `minecraft:overworld_natural_logs` | naturalne kłody Overworldu (m.in. sprawdzanie przez funkcje świata) |
| `minecraft:planks` | receptury (stół, skrzynia, łóżko, tabliczka, patyki, łódź...) |
| `minecraft:leaves` | liście: rozkład, przezroczystość dla heightmap MOTION_BLOCKING_NO_LEAVES, wytop na leaf litter, ścieżki mobów |
| `minecraft:saplings` | sadzonki (kompost, receptury, drop tabliczek?) |
| `minecraft:flowers`, `minecraft:small_flowers`, `minecraft:bee_attractive` (29 bloków), `minecraft:bee_growables` | pszczoły zapylają, wzrost po zapyleniu (borówki!) |
| `minecraft:wooden_doors/_stairs/_slabs/_fences/_buttons/_pressure_plates/_trapdoors` oraz zbiorcze `doors/stairs/slabs/fences/buttons/pressure_plates/trapdoors`, `fence_gates` | zachowanie (łączenie płotów, drzwi otwierane ręką, receptury) |
| `minecraft:signs`, `standing_signs`, `wall_signs`, `all_signs`, `ceiling_hanging_signs`, `wall_hanging_signs`, `all_hanging_signs` | tabliczki |
| `minecraft:mineable/axe` (kłody, deski, tabliczki), `mineable/hoe` (liście, mchy, gąbka), `mineable/shovel` (gleby) | szybkość kopania i drop |
| `minecraft:replaceable_by_trees` | bloki, które drzewo może nadpisać przy generacji (trawa, kwiaty, liście innego drzewa); **własne runo musi tu być, inaczej blokuje drzewa** |
| `minecraft:supports_vegetation` (26.1), `supports_crops`, `supports_sugar_cane`, `supports_cactus`, `supports_lily_pad`, `azalea_grows_on`, `azalea_root_replaceable`, `moss_replaceable`, `lush_ground_replaceable`, `substrate_overworld`, `dirt`, `mud`, `moss_blocks`, `grass_blocks` (26.1) | **własne gleby (torf, igliwie, ściółka) muszą trafić do `#dirt`/`#supports_vegetation`/`#substrate_overworld`**, inaczej rośliny i drzewa się na nich nie utrzymają/nie wygenerują |
| `minecraft:animals_spawnable_on`, `foxes_spawnable_on`, `rabbits_spawnable_on`, `frogs_spawnable_on`, `parrots_spawnable_on`, `wolves_spawnable_on`, `camels_spawnable_on` | spawn zwierząt na własnych glebach (bardzo istotne dla części "fauna") |
| `minecraft:edible_for_sheep`, `snaps_goat_horn`, `sniffer_diggable_block`, `enchantment_power_provider`, `completes_find_tree_tutorial` | drobne mechaniki |

### 9.2. Tagi konwencjonalne Fabric (`c:`), źródło: `ConventionalBlockTags.java` gałąź 1.21.5 (https://github.com/FabricMC/fabric/blob/1.21.5/fabric-convention-tags-v2/src/main/java/net/fabricmc/fabric/api/tag/convention/v2/ConventionalBlockTags.java)

- Drewno: `c:stripped_logs`, `c:stripped_woods`, `c:fences/wooden` (alias `minecraft:wooden_fences`), `c:fence_gates/wooden`, `c:chests/wooden`, `c:barrels/wooden`, `c:bookshelves`. **Nie ma `c:logs` ani `c:planks` - używa się `minecraft:logs` / `minecraft:planks`.**
- Rośliny: `c:flowers`, `c:flowers/small`, `c:flowers/tall`.
- Podłoża: `c:stones`, `c:cobblestones`, `c:sands`, `c:gravels`, `c:ores`, `c:storage_blocks`, `c:budding_blocks`.
- Pomocnicze: `c:hidden_from_recipe_viewers`, `c:relocation_not_supported`, `c:player_workstations/crafting_tables`, `c:villager_job_sites`.
- Tagi `c:` są wspólne z NeoForge (ujednolicone w 1.21) - dają zgodność z recepturami innych modów (np. mody na meble używające `#c:stripped_logs`).

### 9.3. Integracja z innymi modami - wnioski

- Every Compat/Moonlight: konwencja nazw `<name>_planks` + `<name>_log` + opcjonalnie `data/<ns>/moonlight/wood_types/<name>.json` -> automatyczne warianty w modach na meble/skrzynie itd.
- Serene Seasons: nasze liście z providerem koloru listowia (foliage) dostają sezonowe kolory automatycznie (do potwierdzenia w 3.2); dla owoców/kwitnienia trzeba użyć API SS (`SeasonHelper.getSeasonState(world)` - klasa do potwierdzenia).
- Tagi biomów `#minecraft:is_forest`, `#minecraft:is_taiga` itd. oraz `c:is_...` decydują, czy inne mody (np. mody z mobami) będą spawnować w naszych biomach - dodać nasze biomy do odpowiednich tagów.

## 10. Implikacje projektowe dla moda

1. **Wersja docelowa**: celować w Minecraft 26.2 (stabilna) z gałęzią pod 26.3, bo tylko tam są wanilijne: topola, huby na kłodach (`shelf_mushroom`), `attached_to_logs`, powalone drzewa, `leaf_litter`, `bush`, cząsteczki liści i tagi `#supports_vegetation`. Serene Seasons 26.1.2.0.6/0.7 obsługuje 26.2/26.3. Unikać 1.21.1 mimo dużej bazy modów - brakuje w nim połowy potrzebnych mechanik.
2. **Architektura zestawów drewna**: jedna klasa `WoodSet`/`TreeSpecies` (rekord: id, `BlockSetType`, `WoodType`, kolor liści, typ liści - biomowy/stały/sezonowy, `SaplingGenerator`, czy pełny zestaw) rejestrująca 20 bloków + 19 przedmiotów + 2 encje, oraz jeden `BlockFamily` per gatunek dla datagen. Wszystkie ID angielskie (`beech_log`), polskie nazwy tylko w `pl_pl.json`; to warunek zgodności z Every Compat/Moonlight i innymi modami.
3. **Data Generation obowiązkowo** od pierwszego commita (`fabric-datagen` source set): modele/blockstates (`family()`, `registerLog`), loot (`leavesDrops` z sadzonką), tagi (własne `<w>_logs` + wpisy do ~35 tagów `minecraft:`/`c:`), receptury (`generateFamily`, łodzie, tabliczki), lang. Ręcznie tylko PNG i modele nietypowe (krzewy 3D, huby, mrowisko).
4. **Zestawy "pełne" vs "lite"**: pełne dla ~10 gatunków gospodarczo ważnych (sosna, jodła, modrzew, buk, olsza, grab, jesion, klon, lipa, wierzba), "lite" (log, wood, planks?, leaves, sapling) dla rzadkich (wiąz, jarząb, cis, czeremcha, dzika jabłoń/grusza); świerk/dąb/brzoza/topola z wanilii z własnymi pokrojami. Decyzja użytkownika (sekcja 11).
5. **Liście liściaste: tekstura w skali szarości + tint biomowy** (`BiomeColors.getFoliageColor`), by Serene Seasons przebarwiało je bez integracji; iglaste zimozielone ze stałym kolorem (SS ich nie rusza - zgodne z naturą); modrzew i gatunki z owocami/kwiatami jako `LeavesBlock` z właściwością `phase` zmienianą w random ticku wg `SeasonHelper.getSeasonState(world).getSubSeason()` (opakować w `SeasonProvider` z fallbackiem bez SS).
6. **Cząsteczki**: użyć wanilijnych `tinted_leaves` (klasa `TintedParticleLeavesBlock`) dla liściastych, własne `SimpleParticleType` dla igieł modrzewia, płatków (czeremcha, jabłoń, głóg) i puchu topoli; szansa zależna od fazy (jesień x10).
7. **Drzewa proceduralne, nie NBT**: 10 wanilijnych trunk placerów + 12 foliage placerów pokrywa ~60% pokrojów; napisać 4-6 własnych (sosna "parasol", dąb "giant fancy" 2x2 z grubymi konarami, jodła/świerk "okółkowy", wierzba płacząca z witkami, kosodrzewina wielopędowa, martwe drzewo bez liści) przez access widener na `TrunkPlacerType`/`FoliagePlacerType`/`TreeDecoratorType`. Szablony NBT tylko dla drzew pomnikowych i wykrotów (5-10 wariantów).
8. **Skala drzew**: jeśli 1 blok = 1 m, buk/świerk/jodła 30-40 bloków - potrzebne: `min_clipped_height`, luźne `minimum_size`, runo w `#replaceable_by_trees`, testy wydajności renderu (korony 7-9 bloków promienia) i wysokość świata (raport terenu). Jeśli to zbyt drogie - skala drzew 1:2 (buk 18-20) przy skali terenu 1:1 - **zapytać użytkownika**.
9. **Piętra lasu jako zestaw klas bloków**: `BerryBushBlock` (parametryzowany: stadia, owoc, kłucie, wysokość), `TallShrubBlock` (2 bloki), krzewy >2 m jako feature `tree` z `bush_foliage_placer` i własnym blokiem liści krzewu, `SegmentedPlantBlock` (jak `leaf_litter`/`wildflowers`: zawilce, szczawik, igliwie), `MossCarpetBlock` z bokami jak `pale_moss_carpet`, `ShelfMushroomBlock` (huby, `facing`, wymaga `#logs` za sobą), `AnthillBlock`, `DeadLogBlock` (3 stadia rozkładu, bez zestawu drewna), `PeatBlock`.
10. **Gleby i tagi**: każdy nowy blok gleby (torf, igliwie, ściółka bukowa) do `#dirt`/`#mud`, `#substrate_overworld`, `#supports_vegetation`, `#mineable/shovel`, `#animals_spawnable_on`, `#wolves_spawnable_on`, `#foxes_spawnable_on`, `#moss_replaceable`; każdy blok runa do `#replaceable_by_trees`; kwiaty do `#flowers`/`#bee_attractive`; jagody do `#bee_growables` i tagów płodności SS.
11. **Rozmieszczanie w 26.x**: kępy runa = placed feature `count` -> `random_offset`/`offset` -> `heightmap` -> `block_predicate_filter` -> `simple_block` (nie `random_patch`); łany (czosnek, zawilce) przez `noise_threshold_count`/`noise_based_count`; drzewa: `count` 10-11 + `in_square` + `surface_water_depth_filter` + `heightmap OCEAN_FLOOR` + `biome`; olsy z `max_water_depth` 1-2 i `mangrove_root_placer`.
12. **Proporcje gatunków**: jedna funkcja pomocnicza przeliczająca zadane udziały (np. bór świeży: sosna 85%, brzoza 10%, dąb 5%) na sekwencyjne `chance` w `random_selector`, generowana datagenem (`FabricDynamicRegistryProvider` dla configured/placed features - **do sprawdzenia nazwa w 26.x**).
13. **Struktura wiekowa i las gospodarczy**: osobne configured features dla młodnika (gęsto, niskie), drągowiny i drzewostanu dojrzałego; las gospodarczy w rzędach przez własny `PlacementModifier` "siatka z jitterem" (rejestr `Registries.PLACEMENT_MODIFIER_TYPE`), rozdzielony na "kwatery" szumem (raport o krajobrazie decyduje o udziale lasów gospodarczych ~80% w PL).
14. **Kolejność feature'ów**: jeden centralny rejestr kolejności VEGETAL_DECORATION wspólny dla wszystkich biomów (uniknąć "feature order cycle"); ściółka/igliwie po drzewach (dekorator `place_on_ground`), grzyby i mrowiska po ściółce, mchy po wszystkim.
15. **Martwe drewno**: wanilijne `fallen_tree` (JSON) dla każdego gatunku z `log_decorators` (huby, mchy, opieńki) i `stump_decorators`; własny feature dla wykrotów; posusz stojący jako `tree` z blokiem martwego drewna i własnym "pustym" foliage placerem.
16. **Fenologia i koszt**: kolor = tint (0 kosztu), stany = random tick (już płacony przez liście), unikać schedule ticków masowych; zapisywać fazę tylko dla bloków, gdzie ma skutek rozgrywkowy. Jesienny przyrost `leaf_litter` pod drzewami w random ticku liści.
17. **Serene Seasons jako zależność opcjonalna** (licencja SS "All rights reserved": nie kopiować kodu, tylko API + tagi biomów/upraw); nasze biomy dodać do tagów SS (temperate domyślnie), jagody/uprawy do `sereneseasons:*_crops`.
18. **Tekstury**: 16x16, własne (nie kopiować wanilii/BOP/Wilder Wild), liście szare pod tint, kora 2-3 warianty w blockstate z obrotami, skrypt proceduralny (Pillow) do gleb/ściółki/mchów jako baza pod ręczne poprawki; wybrać licencję assetów (np. CC BY 4.0).
19. **Zależności**: Fabric API (wymagane), Terraform Wood API (LGPL, opcjonalnie dla łodzi/tabliczek), Moonlight nie wymagane (Every Compat wykrywa nas samo), Serene Seasons opcjonalne, Continuity/CTM opcjonalne klienckie.
20. **Kolejność prac**: (1) rdzeń `WoodSet` + datagen na 1 gatunku (buk), (2) liście sezonowe + SS, (3) 3-4 własne placery, (4) piętra lasu (jagody, runo, mchy, huby, ściółka), (5) placed features i proporcje per biom, (6) martwe drewno i mrowiska, (7) skalowanie na wszystkie gatunki.

## 11. Niepewności / do potwierdzenia

### 11.1. Pytania do użytkownika (decyzje)

1. Wersja docelowa: 26.2 (stabilna, SS 26.1.2.0.6) czy 26.3 (snapshoty, topola i huby w wanilii, SS 26.1.2.0.7)? Czy utrzymywać też 1.21.1 (najwięcej modów)?
2. Skala drzew: czy "rzeczywiste wymiary" obejmują wysokość drzew (buk 35 bloków, korona 10-12 bloków), czy tylko teren? Ma to duży wpływ na wydajność i wygląd.
3. Które gatunki mają dostać pełny zestaw drewna (drzwi, łodzie, tabliczki...), a które tylko kłodę/liście/sadzonkę? Czy świerk, dąb, brzoza, topola mają używać wanilijnych bloków (kompatybilność) czy własnych (spójność stylu)?
4. Zależność od Terraform Wood API (LGPL) vs własna implementacja łodzi/tabliczek?
5. Licencja moda (kod/assety) i czy tekstury mają być rysowane ręcznie, generowane, czy zlecane?
6. Czy Serene Seasons ma być wymagane, czy opcjonalne (z własnym fallbackiem pór roku)?
7. Czy gracze mają móc sadzić krzewy/runo/mchy (przedmioty, nożyce) - wpływa na liczbę przedmiotów i loot tables.
8. Czy wanilijna wiśnia (różowe kwitnienie) ma zostać w świecie (niezgodna z florą PL), czy być wyłączona/zastąpiona czereśnią ptasią?
9. Poziom szczegółowości grzybów: dekoracja (kilka bloków) czy system zbieractwa z jadalnością/trującymi i sezonem?

### 11.2. Fakty do zweryfikowania technicznie (nie potwierdzone w tej sesji)

- Nazwa modyfikatora kępy w 26.x: `random_offset` (notka 26.1 Pre-Release 1) vs `offset` (wiki) - sprawdzić w `mcmeta` dla 26.2.
- Czy `below_trunk_provider` zastąpiło `dirt_provider`/`force_dirt` w konfiguracji `tree` (wiki 26.3 wymienia tylko `below_trunk_provider`).
- Dokładne ID i model wanilijnych "shelf mushrooms" (huby) w 26.3 oraz czy są tylko dla topoli.
- Nazwy klas Yarn w 26.x dla: `TintedParticleLeavesBlock`/`ParticleLeavesBlock`, `FlowerbedBlock`/segmentowego bloku, `SaplingGenerator` (konstruktory), `BlockRenderLayerMap`, `BlockStateModelGenerator.registerLog`, `FabricTagProvider.valueLookupBuilder`, `FabricDynamicRegistryProvider`.
- Czy `ColorProviderRegistry.ITEM` istnieje jeszcze w Fabric API dla 26.x (od 1.21.4 tinty przedmiotów są w `items/*.json`).
- Czy `WoodTypeBuilder`/`BlockSetTypeBuilder` istnieją w Fabric API dla 26.2 (javadoc znaleziony tylko dla 1.20.5).
- Terraform: lista klas wood/tree API i wersja dla 26.x (README modułów zwróciło 404).
- Every Compat/Moonlight: wersje dla 26.x i dokładny schemat `moonlight/wood_types/*.json`.
- Serene Seasons: dokładne nazwy tagów biomów i upraw (`sereneseasons:*`), API (`SeasonHelper`, `ISeasonState`), sposób obsługi brzozy/świerka i licencja pliku LICENSE (stopka "All rights reserved").
- Wartości `leafParticleChance` w wanilii i `SaplingBlock` (1/7, światło 9, mączka 45%).
- Gęstości drzew dla biomów innych niż forest/taiga (dark forest 16, flower forest 6 itd.) - pobrać z mcmeta.
- Wanilijne `mangrove` `surface_water_depth_filter` (5?) jako wzór dla olsów.
- Dokładne brzmienie Minecraft Usage Guidelines co do modyfikowanych assetów w modach; licencje Regions Unexplored, Wilder Wild.
- Przykłady kodu własnych placerów z konkretnych modów (Wilder Wild, Regions Unexplored, Terrestria) - nie pobrano (budżet); linki do repozytoriów w sekcji 12 do samodzielnego przejrzenia.

### 11.3. NIE ZBADANO (poza budżetem)

- Szczegóły `vegetation_patch`/`multiface_growth` (pola) w 26.x - opis z pamięci 1.19-1.21.
- Fabric Docs "Feature Generation" (datagen worldgen) - strona istnieje wg sidebaru, nie została odczytana.
- Biblioteka "Arctree" (następca porzuconego PR do Fabric API o typach placerów) - aktualność nieznana.
- Wydajność renderu bardzo dużych koron (pomiar) i wpływ Sodium/Distant Horizons.

## 12. Źródła

Odczytane w tej sesji (WebFetch/WebSearch, 2026-09-21/22):

1. https://minecraft.wiki/w/Configured_feature - pola feature `tree`, lista feature'ów roślinnych, usunięcie `random_patch` (26.1)
2. https://minecraft.wiki/w/Tree_definition - trunk/foliage placers, dekoratory, root placer, minimum_size (26.3)
3. https://minecraft.wiki/w/Placed_feature - wszystkie placement modifiers (26.3)
4. https://minecraft.wiki/w/Fallen_tree - powalone drzewa, konfiguracja `fallen_tree`
5. https://minecraft.wiki/w/Leaf_Litter - ściółka liściowa (1.21.5)
6. https://minecraft.wiki/w/Leaves - tinty, fancy/fast, cząsteczki, dropy
7. https://minecraft.wiki/w/Bush - blok krzewu (1.21.5)
8. https://minecraft.wiki/w/Sweet_Berry_Bush - mechanika krzewu jagodowego
9. https://minecraft.wiki/w/Block_tag_(Java_Edition) - tagi bloków
10. https://minecraft.wiki/w/Java_Edition_26.1 - data wydania, schemat wersji, tagi podłoża
11. https://www.minecraft.net/en-us/article/minecraft-26-1-pre-release-1 - usunięcie `random_patch`/`flower` (przez WebSearch)
12. https://docs.fabricmc.net/develop/data-generation/block-models - Fabric datagen modeli, lista stron datagen, wersje docs
13. https://maven.fabricmc.net/docs/fabric-api-0.92.0+1.20.5/net/fabricmc/fabric/api/object/builder/v1/block/type/WoodTypeBuilder.html - WoodTypeBuilder
14. https://fabricmc.net/2023/09/12/1202.html - BlockSetTypeBuilder/WoodTypeBuilder (zmiana 1.20.2)
15. https://github.com/FabricMC/fabric/blob/1.21.5/fabric-convention-tags-v2/src/main/java/net/fabricmc/fabric/api/tag/convention/v2/ConventionalBlockTags.java - tagi `c:`
16. https://fabricmc.net/wiki/tutorial:trees - tutorial własnych drzew/placerów (1.19.2)
17. https://github.com/FabricMC/fabric-api/issues/1504 i https://github.com/FabricMC/fabric/pull/1507 - brak API dla typów placerów
18. https://github.com/MinecraftForge/MinecraftForge/issues/8789 - TrunkPlacerType prywatny
19. https://github.com/TerraformersMC/Terraform - moduły, Maven, licencja LGPL-3.0
20. https://github.com/MehVahdJukaar/WoodGood - Every Compat README
21. https://github.com/MehVahdJukaar/Moonlight - Moonlight Lib (WoodType finder)
22. https://github.com/Glitchfiend/SereneSeasons - repo SS (26.1.2, loadery)
23. https://github.com/Glitchfiend/SereneSeasons/wiki/Seasons - cykl 96 dni, podpory
24. https://github.com/Glitchfiend/SereneSeasons/blob/master/src/main/resources/META-INF/sereneseasons_at.cfg - AT na BiomeColors (mechanizm kolorów)
25. https://github.com/Glitchfiend/SereneSeasons/issues/314 - błąd cyklu kolorów przy zmianie długości podpory
26. https://api.modrinth.com/v2/project/serene-seasons/version - wersje SS (26.3/26.2/1.21.1)
27. https://www.curseforge.com/minecraft/mc-mods/serene-seasons - opis zachowania kolorów
28. https://raw.githubusercontent.com/misode/mcmeta/1.21.5-data/data/minecraft/worldgen/placed_feature/trees_taiga.json - gęstość tajgi
29. https://raw.githubusercontent.com/misode/mcmeta/1.21.5-data/data/minecraft/worldgen/placed_feature/trees_birch_and_oak_leaf_litter.json - gęstość lasu

Do samodzielnego przejrzenia (nie odczytane w tej sesji):

30. https://github.com/FrozenBlock/WilderWild - własne placery/feature'y (licencja do sprawdzenia)
31. https://github.com/TerraformersMC/Terrestria - użycie Terraform tree/wood API (LGPL-3.0)
32. https://github.com/TerraformersMC/Traverse
33. https://github.com/Glitchfiend/BiomesOPlenty - pokroje drzew (CC BY-NC-ND - tylko do nauki)
34. https://github.com/misode/mcmeta (gałąź/tag `26.2-data`) - wszystkie wanilijne configured/placed features 26.x
35. https://docs.fabricmc.net/develop/ (26.2) - Feature Generation, Tags, Loot Tables, Recipes, Translations
36. https://maven.terraformersmc.com/ - wersje Terraform dla 26.x
37. https://modrinth.com/mod/every-compat, https://modrinth.com/mod/moonlight - wersje dla 26.x
38. https://www.minecraft.net/en-us/usage-guidelines - wytyczne użytkowania assetów
