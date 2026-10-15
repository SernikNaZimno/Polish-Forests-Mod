# Pionowy wycinek: technika drzew (pnie o zmiennej grubości, generator, liście sezonowe, zestawy drewna). Raport badawczy, 2026-10-14

Zakres: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, mapowania Mojang, Serene Seasons 26.1.2.0.7 (opcjonalnie). Raport uzupełnia `docs/research/04-flora-bloki-drzewa-technika.md`, który pisano dla 1.21.x/26.2 z nazwami Yarn i bez dostępu do kodu 26.3. Wiele jego założeń w 26.3 już nie obowiązuje (§1).

Metoda i oznaczenia:
- **[K]**: sprawdzone w kodzie 26.3, czyli w źródłach z Loom cache projektu (`.gradle/loom-cache/minecraftMaven/.../minecraft-common-…-26.3-sources.jar` i `minecraft-clientOnly-…-sources.jar`, rozpakowane do `scratchpad/mcsrc/`), w zasobach `minecraft-client.jar` albo w jarach modułów Fabric API z Gradle cache (`fabric-rendering-v1` 27.0.14, `fabric-object-builder-api-v1` 24.1.9, `fabric-item-api-v1` 14.7.0, `fabric-content-registries-v0` 15.0.4, `fabric-data-generation-api-v1` 27.2.4, `fabric-renderer-api-v1` 17.0.15, `fabric-model-loading-api-v1` 8.0.35, `fabric-transitive-access-wideners-v1` 8.1.16) i w jarze Serene Seasons (`V9PxJPuw`).
- **[M]**: zmierzone. Liczby stanów bloków dał `Bootstrap.bootStrap()` na czystym jarze `minecraft-common-deobf-26.3` (program `scratchpad/count/CountStates.java`, `CountPoplar.java`).
- **[W]**: ze źródła w sieci (URL w §11).
- **[S]**: szacunek albo propozycja, do sprawdzenia w prototypie.

## 0. Streszczenie i rekomendacja

1. **Pień o zmiennej grubości:** własny blok `TrunkBlock` na gatunek z właściwościami `radius` 1–7 (średnica 2–14 px, czyli 0,125–0,875 m), `parent` (6 kierunków ku rodzicowi, czyli ku korzeniowi) i `waterlogged`, co daje **84 stany na gatunek**. Modele to statyczne JSON z datagenu. Każdy blok rysuje ramię swojego promienia od środka do środka bloku rodzica, a połowa ramienia wychodzi poza blok (JSON pozwala na współrzędne −16…32 [K]). Dzięki temu cieńsza gałąź dochodzi do grubszego pnia bez szczeliny i bez stanów połączeń. Od średnicy ok. 0,9–1 m pień to zwykła kłoda (`RotatedPillarBlock`, 3 stany), a dla olbrzymów 2 × 2 kłody, opcjonalnie z teksturą ćwiartek słojów. Block entity, model dynamiczny (jak w Dynamic Trees) i stany połączeń „boolean × 6” odrzucam (§2.2).
2. **Generator:** czysty planer w Javie (`TreeShapePlan`, testowalny bez gry, z podglądem PNG jak `LandscapePreview`) i cienki feature. Szkielet parametryczny (pień i konary według parametrów gatunku z `06 §9.2`). Dla koron liściastych opcjonalnie lekka kolonizacja przestrzeni z siatką przestrzenną. Promienie z modelu rury (r_rodzic² = Σ r_dziecko²). Woksele są łączone przez ściany, liście w skupieniach na końcach pędów, odległości liści liczy BFS (jak `FastTreeFeature`). Korony są przycinane komórką Woronoja sąsiednich pni z `TreeStandPlan`, więc nie zachodzą na siebie, a wynik nie zależy od kolejności chunków. Szablony NBT tylko dla drzew pomnikowych i wykrotów: wanilia 26.3 ma do tego feature `minecraft:template` [K].
3. **Wydajność [S]:** stany +84…+200 na gatunek (wanilia 26.3: 1286 bloków, **35 723 stany** [M]). Liście na chunk: bór świeży ok. 500–700 (jak las wanilii), grąd ok. 1000–1500 (2–2,5 × las wanilii). Największe ryzyko to etap LIGHT i liczba wierzchołków liści. Cienkie pnie nie tłumią światła nieba (`propagatesSkylightDown` = prawda dla kształtu niepełnego [K]), więc same nie kosztują światła. Zasięg zapisu featurów to 3 × 3 chunki [K], więc promień korony ≤ 16 bloków (zawsze bezpieczny).
4. **Liście sezonowe:** własny blok liści na gatunek (`TintedParticleLeavesBlock`, więc spadające cząsteczki biorą kolor z odcienia [K]) i własne `BlockTintSource` z kolorem gatunku zależnym od fazy roku z naszego `SeasonProvider`. Bazą jest „surowy” kolor listowia biomu z własnego `ColorResolver` (rejestr Fabric `ColorResolverRegistry` [K]), bo SS podmienia wanilijny `BiomeColors.FOLIAGE_COLOR_RESOLVER` [K] i nałożyłby swój sezon drugi raz. Przebudowa siatek przy zmianie fazy działa jak w SS: `Minecraft.levelExtractor.allChanged()` [K]. Zima liściastych (gołe gałęzie) wyłącznie po stronie klienta, modelem FRAPI (`FabricBlockStateModel.emitQuads`), a nie stanem bloku: zasięg symulacji (6–8 chunków) jest mniejszy od zasięgu widzenia (10–12), więc stan zmieniany losowymi tickami zostawiałby dalekie lasy w starej porze.
5. **Zestawy drewna 26.3:** pełny zestaw to 23 bloki, 22 przedmioty i 2 typy encji (topola, 580 stanów [M]). Datagen robi z niego 23 blockstates, 22 definicje przedmiotów, 61 modeli bloków, 6 modeli przedmiotów, 21 tabel łupów, 16 receptur, 16 advancementów i wpisy w tagach [K]. Ręcznie zostaje ok. 23 PNG na gatunek. W 26.3 zmieniło się sporo: korowanie przez rejestr danych `block_transformer` i Fabric `BlockTransformerHelper.registerStripping` (bez `StrippableBlockRegistry`), paliwo i kompost jako właściwości przedmiotu, tabliczki jako modele bloków, nowy blok półki (`shelf`), łodzie z publicznym konstruktorem `Boat` (bez Terraform). Dla wycinka zalecam **żadnego pełnego zestawu albo jeden (sosna)** jako test potoku. Dąb, brzoza i świerk z wanilijnymi zestawami (D2), a własne są tylko cienkie pnie i liście.
6. **Serene Seasons:** zgodne bez kodu SS (licencja ARR): API `SeasonHelper` przez nasz most, `SeasonHelper.changesFoliageColor(level)` jako wyłącznik, nasze biomy w tagach SS (zrobione w S5), opcjonalnie sadzonki w tagach `sereneseasons:*_crops`.

Koszt wycinka (technika drzew, bez fauny i runa) [S]: ok. **20–30 dni pracy agenta** plus tekstury (§8). Największe ryzyka: LIGHT i FPS przy koronach w skali 1:1 (§4), wygląd schodków ukośnych gałęzi (§2.4), determinizm na granicach chunków (§3.4), dekoratory wanilii (huby, mech) na cienkich pniach (§3.2).

## 1. Fakty z 26.3, które zmieniają plan z raportu 04

| Temat | Stan w 26.3 [K] | Skutek |
|---|---|---|
| Feature | `Feature` to interfejs z `place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)`, a „configured feature” to rekord typu (np. `TreeFeature`, `TemplateFeature`) w rejestrze `Registries.FEATURE`; typy są w `BuiltInRegistries.FEATURE_TYPE` | własny generator to rekord z `MapCodec`, jak `FastTreeFeature` i `TreeStandFeature` |
| Sadzonki | `TreeGrower(String, WeightedList<ResourceKey<Feature>> trees, megaTrees, …)` | sadzonka wskazuje ten sam klucz `polishforests:tree/<gatunek>`, który w M2 trzyma zastępstwo wanilii (§8.4 planu M2: „w M3 zmieniamy plik feature'a, a identyfikator zostaje”) |
| Własne placery | konstruktory `TrunkPlacerType`, `FoliagePlacerType`, `TreeDecoratorType` są „access widened by fabric-transitive-access-wideners-v1” | jeśli zostajemy przy `TreeFeature`, nie trzeba własnego access widenera ani mixinu (raport 04 §4.7 zakładał jedno z nich) |
| Szablony | jest feature `minecraft:template` (`TemplateFeature`: ważona lista szablonów, obroty, procesory) | drzewa pomnikowe i wykroty z NBT bez własnego kodu |
| Dekoratory drzew | `TreeDecorator.Context(WorldGenLevel, BiConsumer, RandomSource, Set logs, Set leaves, Set roots)` jest publiczny; są `shelf_mushroom`, `attached_to_logs`, `place_on_ground`, `pale_moss` | dekoratory wanilii działają też z własnym generatorem (ale patrz §3.2: cienkie pnie) |
| Zasięg zapisu | `ChunkPyramid`: etap FEATURES ma `blockStateWriteRadius(1)`, a `WorldGenRegion.ensureCanWrite` odrzuca zapis dalej („Detected setBlock in a far chunk”) | drzewo zapisuje tylko w 3 × 3 chunkach wokół chunka pnia: promień korony ≤ 16 bloków przy dowolnym położeniu pnia w chunku |
| Rozkład liści | `LeavesBlock.getOptionalDistanceAt`: odległość 0 dla `#minecraft:prevents_nearby_leaf_decay` (wanilia: `#logs`) | pnie i gałęzie wszystkich grubości do `#minecraft:logs` |
| Heightmapy i „blokuje ruch” | `MOTION_BLOCKING`, `OCEAN_FLOOR` i uduszenie biorą tagi `#blocks_motion_in_heightmap`, `#blocks_motion`, które obejmują `#logs` i `#leaves` | cienkie pnie w `#logs` zachowują się jak kłody (heightmapy, ruch, uduszenie) bez kodu |
| Światło | `BlockBehaviour.propagatesSkylightDown` = kształt niepełny i brak płynu; `getLightDampening` = 15 dla pełnego bloku renderu, 0 przy przepuszczaniu nieba, w pozostałych przypadkach 1 | cienki pień nie tłumi światła; zalany 1; liście (pełny kształt, `noOcclusion`) tłumią 1 |
| Warstwy renderu | `ChunkSectionLayer` SOLID / CUTOUT / TRANSLUCENT wybierana z przezroczystości tekstury (`byTransparency`, warstwa na quad) | nie ma `BlockRenderLayerMap` ani cutout_mipped; liście w trybie „Fast” są nieprzezroczyste tylko dla `instanceof LeavesBlock` (`ModelBlockRenderer.forceOpaque`) |
| Tekstury liści | wanilijne `*_leaves.png.mcmeta` mają `{"texture": {"mipmap_strategy": "dark_cutout"}}` | nasze liście dostają takie samo mcmeta (własny plik, nie kopia zasobu) |
| Kolory bloków | `BlockColors` trzyma listy `BlockTintSource` (`color(state)`, `colorInWorld(state, level, pos)`, `relevantProperties()`); Fabric: `BlockColorRegistry.register(List<BlockTintSource>, Block...)` | `ColorProviderRegistry` z raportu 04 nie istnieje; odcień zależny od stanu deklaruje właściwość w `relevantProperties` |
| Własny `ColorResolver` | `ClientLevel.getBlockTint` ma pamięci tylko dla 4 resolverów wanilii; Fabric `ColorResolverRegistry.register(ColorResolver)` (z `ClientLevelMixin`) dodaje własne; Sodium (gałąź dev) trzyma `LevelColorCache` jako mapę dowolnych resolverów [W] | własny „surowy” resolver listowia jest możliwy w wanilii, z Fabric API i z Sodium |
| Serene Seasons 26.3 | AW `mutable field BiomeColors FOLIAGE_COLOR_RESOLVER` / `GRASS_COLOR_RESOLVER`; `SeasonColorHandlers.setup()` podmienia oba; `SeasonHandlerClient` przy zmianie sub-sezonu woła `Minecraft.levelExtractor.allChanged()` (czyści też pamięci odcieni) | wszystko, co używa `BlockTintSources.foliage()/grass()`, sezonuje się samo; własne resolvery SS omija |
| Korowanie | rejestr danych `Registries.BLOCK_TRANSFORMER` (`data/minecraft/block_transformer/axe.json`, 60 KB), komponent przedmiotu `block_transformer` na siekierze; Fabric `BlockTransformerHelper.registerStripping(Block, Block)` i `BlockTransformerEvents.MODIFY` | `StrippableBlockRegistry` z raportu 04 nie istnieje; zamiana przez `CopyPropertiesProvider` zachowuje `radius`/`parent` cienkiego pnia |
| Paliwo, kompost | `Item.Properties.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS)`, `.compostable(ContextIntProviders.COMPOSTABLE_LOW)` | bez rejestrów Fabric |
| Palność | `FireBlock.setFlammable` w bootstrapie; Fabric `FlammableBlockRegistry.getDefaultInstance().add(block, 5, 5)` | jak dawniej |
| Tabliczki | modele bloków (`template_sign_rot_0…3`), tekstury `textures/block/<w>_sign.png`, `<w>_hanging_sign.png`, ikony GUI `textures/gui/signs/<w>.png`; typy `BlockEntityTypes.SIGN`, `HANGING_SIGN`; Fabric `FabricBlockEntityType.addValidBlock(Block)` | bez mixinów i bez Terraform |
| Półka | blok `<w>_shelf` (64 stany) z `BlockEntityTypes.SHELF` i recepturą ze strugniętej kłody | nowa pozycja zestawu drewna |
| Łodzie | `new Boat(EntityType, Level, Supplier<Item>)`, `new ChestBoat(…)` publiczne; `EntityType.Builder.of(…, MobCategory.MISC).noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10)`; `BoatRenderer(ctx, ModelLayerLocation)` bierze teksturę `textures/entity/<ścieżka warstwy>.png`; dozownik `DispenserBlock.registerBehavior(item, new BoatDispenseItemBehavior(type))`; Fabric `ModelLayerRegistry`, `EntityRendererRegistry` | ok. 40 linii na gatunek, Terraform Wood API niepotrzebne |
| Rejestracja bloków | `Blocks.register(ResourceKey<Block>, Function<Properties, Block>, Properties)` publiczne (ustawia `setId`); `Blocks.logProperties(…)` i `leavesProperties(…)` też publiczne; `codec()` bloków nie jest wymagany | prosty pomocnik `WoodSet` |
| Liczby [M] | 1286 bloków, 35 723 stany; zestaw topoli 23 bloki, 580 stanów (liście 28 stanów każdy, schody 80, drzwi, klapa, półka i tabliczka wisząca po 64) | §4.1 |

Precedens Mojanga: topola 26.3 nie ma zielonych liści, tylko trzy statyczne bloki `red_/orange_/yellow_poplar_leaves` z własnymi cząsteczkami, a `TreeGrower.POPLAR` losuje jedno z trzech drzew [K]. Jesienne kolory w wanilii to więc osobne bloki na drzewo, a nie zmiana w czasie.

## 2. Pnie o zmiennej grubości

### 2.1 Mody i licencje

| Mod | Technika pnia | Wersje | Licencja | Co wolno z niego wziąć |
|---|---|---|---|---|
| Dynamic Trees (DynamicTreesTeam) | `BranchBlock`/`BasicBranchBlock`: `radius` 1–8 (`MAX_RADIUS = 8`), `waterlogged`; połączenia z promieni sąsiadów w czasie renderu (`getConnectionData` czyta 6 sąsiadów, model Fabric `BasicBranchBlockBakedModel` z quadami „rękawów” i rdzeni dla każdego promienia), kształt kolizji też z sąsiadów z pamięcią `shapeCache`; grube pnie `ThickBranchBlock` do `MAX_RADIUS_THICK = 24` (3 × 3 bloki) z powłoką `TrunkShellBlock` (`ShellMuse`); wzrost drzew symulowany w czasie gry | gałąź `develop/26.1.2` z modułami `common`/`fabric`/`neoforge`; jest też `feature/26.1.2/winter_leaves` i most SS dla Fabric (`SereneSeasonsSeasonProvider`) | MIT [W] | wzorce i kod z zachowaniem informacji o licencji MIT; zasoby nie |
| Regions Unexplored | `SmallOakLogBlock`: stała grubość 8 px, `axis` + `waterlogged` (6 stanów), `propagatesSkylightDown` true, bez połączeń; `BranchBlock`: dekoracyjny poziomy patyk przy pełnej kłodzie (`HORIZONTAL_FACING`) | 1.21.1–26.1.x | MIT [W] | idee i kod (MIT) |
| Terraform Wood API (TerraformersMC) | `BareSmallLogBlock`/`SmallLogBlock`: stała grubość, `axis` + 6 boolean połączeń + `waterlogged` (+ `has_leaves`: 768 stanów), kształty z 64 masek; `QuarterLogBlock`: kłoda 2 × 2 z `bark_side` (ćwiartki słojów); łodzie i tabliczki | gałąź 26.3 | LGPL-3.0 [W] | jako zależność tak; kopiowanie kodu do moda MIT nie (fragment musiałby zostać LGPL) |
| Terrestria | używa Terraform (małe kłody, ćwiartki, korony „canopy”) | do 26.2 | LGPL-3.0 [W] | jw. |
| Ecologics | brak cieńszych pni (zwykłe zestawy drewna) | do 26.3 | kod MIT, katalogi `resources` All Rights Reserved [W] | kod tak, zasoby nie |
| Wilder Wild | wydrążone kłody, własne drzewa | do 26.3 | FrozenBlock Modding Oasis License [W] | nie kopiować |
| Nature's Spirit | własne drzewa | do 1.21.1 | licencja własna [W] | nie kopiować |

Wniosek: zmienną grubość pni w skali bloku robi tylko Dynamic Trees. Pozostałe mody mają jedną cienką grubość albo dekoracyjne patyki.

### 2.2 Warianty techniczne

| Wariant | Stany na gatunek | Render | Kształt kolizji | Kod | Ocena |
|---|---|---|---|---|---|
| A. Stała cienka kłoda (RU) | 6 | JSON | statyczny | mały | jedna grubość nie spełnia D8 (kilka średnic) |
| B. Promień × 6 boolean połączeń (Terraform z promieniem) | 7 × 64 × 2 = 896 | JSON multipart | statyczny z maski | średni | ramię grubszego bloku nie zna promienia cieńszego sąsiada, więc na złączu jest za gruby kikut albo szczelina; dużo stanów i modeli |
| C. Model dynamiczny (DT) | 7 × 2 = 14 | FRAPI `emitQuads` czyta sąsiadów | z sąsiadów, z pamięcią | duży (własny `CustomUnbakedBlockStateModel`, wypiekanie quadów, kształty) | wygląd jak D, najmniej stanów; wymaga FRAPI (Sodium ≥ 0.6 ma je wbudowane [W]), datagen nie wygeneruje modelu; kolizja liczona z sąsiadów przy każdym zapytaniu |
| **D. Promień + kierunek rodzica, statyczny JSON z wysięgiem (rekomendacja)** | 7 × 6 × 2 = **84** | JSON (dowolny renderer) | statyczny na stan | mały/średni | patrz §2.3 |
| E. Block entity z promieniem | 1 | BER albo model z danymi BE | z BE | duży | tysiące BE na chunk (pamięć, zapis NBT, ticki, render poza siatką sekcji): odrzucony |

### 2.3 Wariant D w szczegółach

Każdy blok drewna drzewa ma dokładnie jednego rodzica: sąsiada w stronę korzenia, który jest co najmniej tak gruby (model rury, §3.2). Blok rysuje jedno ramię swojego promienia od własnego środka do środka rodzica. Połowa ramienia leży we własnym bloku, a połowa wchodzi 8 px w blok rodzica. Rodzic jest grubszy, więc ta część jest schowana w jego geometrii. Widać ją tylko od powierzchni rodzica do ściany bloku, czyli dokładnie tam, gdzie bez wysięgu byłaby szczelina. Dzieci z innych stron dochodzą same, więc blok nie musi znać ani dzieci, ani ich promieni.

- **Właściwości:** `RADIUS = IntegerProperty.create("radius", 1, 7)` (promień w px, średnica 2r px = 0,125–0,875 m); `PARENT = EnumProperty.create("parent", Direction.class)`; `WATERLOGGED`. 84 stany. Blok ma `#minecraft:logs` i własny `#polishforests:<gatunek>_logs`, a także `#mineable/axe` i `#logs_that_burn` (przez `#logs`) oraz `#overworld_natural_logs`.
- **Mapowanie grubości [S]:** pierśnica d (m) → r = round(d · 8) px, ograniczone do 1–7. Od d ≈ 0,94 m pełna kłoda gatunku (`RotatedPillarBlock`), od d ≈ 1,9 m 2 × 2 kłody (opcjonalnie `QuarterLogBlock`-podobne tekstury ćwiartek słojów, pomysł z Terraform, własna implementacja). Przy typowych pierśnicach z `06 §9.2` (25–70 cm) pnie mają r 2–6, a gałęzie 1–2. To zgodne z D8.
- **Geometria modelu (szablon „ramię w górę”, potem obrót):** element 1 od `[8−r, 8, 8−r]` do `[8+r, 16, 8+r]` (własna połowa, korowina na 4 ścianach, ściana dolna korowiny jako koniec gałęzi). Element 2 od `[8−r+ε, 16, 8−r+ε]` do `[8+r−ε, 24, 8+r−ε]` (wysięg do środka rodzica, bez ściany górnej). Margines ε ≈ 0,01 px usuwa migotanie współpłaszczyznowych ścian, gdy dziecko i rodzic mają ten sam promień. 9 quadów na blok (pełna kłoda ma 6).
- **Obroty:** ten sam układ co wanilijny `BlockModelGenerators.ROTATIONS_COLUMN_WITH_FACING` dla modelu skierowanego w górę (sprawdzone na `end_rod.json` 26.3: `facing=north` → `x=90`). Mamy więc `parent=up` bez obrotu, `down` x180, `north` x90, `south` x90 y180, `west` x90 y270, `east` x90 y90. Datagen: `PropertyDispatch.modify(PARENT)` z mutatorami `X_ROT_*`/`Y_ROT_*` (widoczne w 26.3 jako „access widened by fabric-data-generation-api-v1”), 7 modeli na gatunek dziedziczących z 7 szablonów (`polishforests:block/template_trunk_r1…r7`, sloty `#bark`, `#end`). Szablony to pliki JSON pisane ręcznie, bo `ModelTemplate` nie tworzy elementów.
- **Kształt kolizji i obrysu:** suma rdzenia `Block.cube(2r)` i ramienia `Block.boxZ(2r, 0, 8)` (od ściany północnej do środka), obrócona `Shapes.rotateAll(…)` na 6 kierunków. Tablica 6 × 7 policzona raz. Wysięg poza blok jest tylko wizualny.
- **Światło i okluzja:** kształt niepełny, więc `solidRender` false, `propagatesSkylightDown` true i tłumienie 0 [K]. Pnie nie rzucają cienia (jak łańcuch czy płot). Ściany sąsiadów nie są wycinane (kształt okluzji jest mały), a `skipRendering` zostaje wanilijne.
- **Postawienie przez gracza:** rodzic = blok, w który gracz kliknął (`context.getClickedFace().getOpposite()`), promień z przedmiotu albo domyślny. Wysięg chowa się w klikniętym bloku. Po zniszczeniu rodzica widać 8-pikselowy kikut, co przyjmuję. W wycinku cienkie pnie mogą nie mieć przedmiotów (tylko generacja). Łup przez warunek `block_state_property`: r ≤ 2 daje 1–2 patyki, r ≥ 3 jedną kłodę gatunku [S].
- **Korowanie:** `BlockTransformerHelper.registerStripping(SCOTS_PINE_TRUNK, new CopyPropertiesProvider(STRIPPED_SCOTS_PINE_TRUNK))` zachowuje `radius` i `parent` (wanilia robi tak z kłodami w `BlockTransformers.axeStrippables`) [K]. Stripped wariant to kolejne 84 stany. W wycinku można go pominąć.
- **Leżące kłody (martwe drewno, wycinek):** ten sam blok i ten sam wokselizator (`parent` wzdłuż kłody), więc kłoda o pierśnicy 35 cm ma r 3 zamiast pełnego bloku. Stadia rozkładu w M4 jako osobne bloki albo tekstury.

Szkic klasy (mapowania Mojang 26.3, nazwy własne do ustalenia):

```java
public final class TrunkBlock extends Block implements SimpleWaterloggedBlock {
	public static final IntegerProperty RADIUS = IntegerProperty.create("radius", 1, 7);
	public static final EnumProperty<Direction> PARENT = EnumProperty.create("parent", Direction.class);
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	private static final VoxelShape[][] SHAPES = new VoxelShape[6][7];

	static {
		for (int r = 1; r <= 7; r++) {
			// Core and arm from the north face to the center; rotated to all six parents.
			VoxelShape north = Shapes.or(Block.cube(2 * r), Block.boxZ(2 * r, 0, 8));
			Map<Direction, VoxelShape> rotated = Shapes.rotateAll(north);
			for (Direction d : Direction.values()) {
				SHAPES[d.get3DDataValue()][r - 1] = rotated.get(d);
			}
		}
	}

	public TrunkBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(RADIUS, 4).setValue(PARENT, Direction.DOWN).setValue(WATERLOGGED, false));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(PARENT).get3DDataValue()][state.getValue(RADIUS) - 1];
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		boolean water = context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER);
		return defaultBlockState().setValue(PARENT, context.getClickedFace().getOpposite()).setValue(WATERLOGGED, water);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(RADIUS, PARENT, WATERLOGGED);
	}
}
```

Szablon modelu dla r = 3 (6 px, ramię w górę, korowina wycięta ze środka tekstury kłody):

```json
{
  "parent": "minecraft:block/block",
  "textures": { "particle": "#bark" },
  "elements": [
    { "from": [5, 8, 5], "to": [11, 16, 11],
      "faces": {
        "north": { "uv": [5, 0, 11, 8], "texture": "#bark" }, "south": { "uv": [5, 0, 11, 8], "texture": "#bark" },
        "west":  { "uv": [5, 0, 11, 8], "texture": "#bark" }, "east":  { "uv": [5, 0, 11, 8], "texture": "#bark" },
        "down":  { "uv": [5, 5, 11, 11], "texture": "#bark" } } },
    { "from": [5.01, 16, 5.01], "to": [10.99, 24, 10.99],
      "faces": {
        "north": { "uv": [5, 8, 11, 16], "texture": "#bark" }, "south": { "uv": [5, 8, 11, 16], "texture": "#bark" },
        "west":  { "uv": [5, 8, 11, 16], "texture": "#bark" }, "east":  { "uv": [5, 8, 11, 16], "texture": "#bark" } } }
  ]
}
```

### 2.4 Koszty i ryzyka wariantu D

- **Koszt [S]:** klasa bloku, 7 szablonów JSON, datagen blockstate i modeli dla gatunku, tagi i łup: 2–3 dni. Każdy następny gatunek to linijka rejestracji i 2 tekstury (korowina może być korą pełnej kłody gatunku; słoje).
- **Wygląd:** gałęzie są wyrównane do osi, więc ukośne konary to schodki z bloków połączonych ścianami (jak w Dynamic Trees). Przy r 1–2 wygląda to naturalnie, przy grubych ukośnych konarach dębu widać „schody”. Łagodzi to generator: grube konary prowadzi blisko osi, a ukośność daje dopiero gałęziom r ≤ 2.
- **Do sprawdzenia w grze:** (1) elementy poza blokiem przy Sodium z paczki (`-Poptimization`): ścięcie sekcji na granicy kadru i AO wierzchołków poza blokiem (wanilia liczy światło wierzchołka z pozycji ściany; możliwe ciemne szwy); (2) migotanie przy równych promieniach (margines ε); (3) cząsteczki niszczenia i obrys celownika; (4) Distant Horizons pokaże cienkie pnie jako pełne kolumny (LOD), co przyjmuję.
- **Wariant C jako zapas:** jeśli schodki albo wysięg wyglądają źle, model dynamiczny (C) daje ten sam układ ramion bez wysięgu, ale wymaga FRAPI i własnego modelu. Stan `radius` zostaje, a znika tylko `parent`, więc przejście jest tanie dla generatora.

## 3. Generator drzew proceduralnych

### 3.1 Porównanie metod

| Metoda | Jak działa | Plusy | Minusy | Koszt na drzewo [S] |
|---|---|---|---|---|
| L-system (Prusinkiewicz i Lindenmayer 1990) | przepisywanie ciągu symboli i interpretacja żółwia | regularne okółki iglastych, mało kodu | trudno sterować wypełnieniem korony i reakcją na sąsiadów | < 0,05 ms |
| Parametryczny rekurencyjny (Weber i Penn 1995) | poziomy rozgałęzień z parametrami: liczba, kąt, długość względna, krzywizna, zbieżność | parametry wprost z tabeli `06 §9.2` (H, Lk, Kz, smukłość); szybki i deterministyczny | korony liściaste bywają „zbyt porządne” | 0,05–0,2 ms |
| Kolonizacja przestrzeni (Runions i in. 2007) | punkty przyciągania w obwiedni korony, pędy rosną ku nim, punkty znikają w odległości zabicia | realistyczne korony liściaste; obwiednię da się przyciąć do miejsca między sąsiadami (asymetria korony, korony nie zachodzą) | więcej kodu; koszt rośnie z liczbą punktów, więc potrzebna siatka przestrzenna | 0,1–0,5 ms przy 150–300 punktach i siatce |
| Szablony NBT (`minecraft:template` w 26.3) | gotowe struktury z obrotem i procesorami | pełna kontrola artystyczna (pomniki, wykroty, „Bartek”) | powtarzalność, brak dopasowania do terenu i sąsiadów, praca ręczna | koszt wstawienia |
| Symulacja wzrostu (Dynamic Trees) | wzrost w tickach gry, sygnały przez gałęzie | drzewa rosną w grze | ciężkie w czasie gry; drzewa generacji też są symulowane; zależność od DT | duży |

### 3.2 Rekomendowana architektura (zgodna z wzorcem M2: czysty planer + cienki feature)

1. **Wejście:** gatunek (parametry z JSON przez kodek, generowane datagenem jak palety M2), klasa wieku albo wysokość, bonitacja, warstwa (a1, a2, podrost), odstęp do sąsiadów z `TreeStandPlan` (jeden wzór kandydatów świata), nachylenie terenu i ziarno z (ziarno świata, x, z pnia, nowa sól, np. `feature.tree.shape`). Nowe pole szumu dostaje nową sól, zgodnie z zasadą soli.
2. **Szkielet:** pień jako krzywa z lekkim przechyłem i błądzeniem. Archetyp „iglasty”: okółki co 1–2 bloki (3–5 gałęzi), oczyszczanie pnia poniżej nasady korony (sucha gałąź jako krótki kikut r 1). Archetyp „liściasty”: pień do nasady korony, potem rozwidlenia lub konary z parametrem dominacji wierzchołkowej, a gałęzie 2. i 3. rzędu parametrycznie albo kolonizacją przestrzeni w obwiedni korony.
3. **Promienie:** od końców pędów (r 1 px) w dół modelem rury r_rodzic^n = Σ r_dziecko^n (n ≈ 2–2,5, reguła Leonarda), skalowane tak, by pierśnica pasowała do allometrii gatunku (`06 §9.2`; przykład z raportu ekologii wycinka: dąb ok. 35–50 cm w wieku 100–140 lat). Promień nigdy nie rośnie ku końcowi pędu, więc wariant D jest spójny.
4. **Wokselizacja:** każdy odcinek idzie krokami przez ściany (3D DDA z rozbiciem kroków ukośnych na osiowe). Blok bierze największy promień spośród odcinków, które przez niego przechodzą, a `parent` to kierunek do poprzedniego bloku ku korzeniowi. r ≤ 7 daje `TrunkBlock`, r ≥ 7,5 kłodę z osią odcinka, a pień ≥ 2 bloki średnicy dysk kłód.
5. **Korona:** skupienia liści (elipsoidy z szumem i dziurami) wokół końców pędów 2. i 3. rzędu, pusty środek dużych koron (liście w zewnętrznych 2–3 blokach). Każdy liść musi być w zasięgu 6 kroków od drewna (`#logs`). Odległości liczy BFS po tablicy bajtów skrzynki drzewa (gotowe w `FastTreeFeature`), a liście z odległością 7 usuwamy przed zapisem.
6. **Przycięcie do sąsiadów:** pozycje i rozmiary sąsiednich pni w promieniu 2 R są znane z czystego `TreeStandPlan` dla 3 × 3 chunków. Woksel korony należy do drzewa, którego ważona odległość (moc komórki Woronoja ważona promieniem korony) jest najmniejsza. Korony nie zachodzą na siebie (jak „crown shyness”), a wynik zapisu nie zależy od kolejności dekoracji chunków (§3.4).
7. **Zapis:** `level.setBlock(pos, state, 2 | 16)` tylko na blokach z `#replaceable_by_trees` albo w powietrzu. Pnie nadpisują liście, liście nie nadpisują drewna. Dekoratory wanilii (`place_on_ground` ze ściółką, `attached_to_logs`) przez publiczny `TreeDecorator.Context`. **Uwaga:** huba (`shelf_mushroom`) i mech na ścianie bloku cienkiego pnia wisiałyby 8−r px od kory. Na cienkich pniach trzeba własnych hub z przesunięciem według promienia (M4) albo hub tylko na pełnych kłodach.
8. **Sadzonki:** `TreeGrower` wskazuje ten sam klucz `polishforests:tree/<gatunek>`, czyli feature z domyślnymi parametrami (młode drzewo). `TreeStandFeature` woła planer bezpośrednio z kontekstem drzewostanu (wiek, warstwa, sąsiedzi), bo `PlacedFeature.place` nie przekazuje parametrów.

Szkic interfejsu planera (czysta Java, bez klas gry):

```java
public record TreeRequest(Species species, float height, float dbh, Layer layer, long seed,
		int trunkX, int trunkY, int trunkZ, List<Neighbor> neighbors, float slopeX, float slopeZ) {}

public record TreeVoxels(long[] wood, byte[] woodRadius, byte[] woodParent, long[] leaves, byte[] leafDistance) {}

public final class TreeShapePlan {
	public static TreeVoxels plan(TreeRequest request, SpeciesShape shape) { /* skeleton -> radii -> voxels -> crown -> clip -> BFS */ }
}
```

### 3.3 Archetypy dla gatunków wycinka [S]

Wysokości i korony z `06 §9.2` dla drzewostanu dojrzałego (1 blok = 1 m, A7):

| Gatunek | Archetyp | H (bl.) | Korona: długość / średnica w zwarciu | r pnia u podstawy (px) | Uwagi |
|---|---|---|---|---|---|
| sosna (bór świeży) | iglasty, korona tylko w górnej 1/4–1/3, gałęzie parasolowate na starszych | 20–28 | 6–9 / 4–7 | 3–5 | kora górna „rudo-cynamonowa”: druga tekstura korowiny powyżej 2/3 H (dwa bloki gatunku albo `radius` < 3 → kora górna) |
| świerk (domieszka) | iglasty stożek, gałęzie do nasady 40–60% H | 25–35 | 12–18 / 4–6 | 3–5 | gałęzie zwisające: końce pędów o 1 blok niżej |
| brzoza | liściasty smukły, zwisające końce | 18–25 | 8–12 / 4–6 | 2–3 | kora biała: własna tekstura |
| dąb (grąd a1) | liściasty, grube konary, nieregularna | 25–32 | 10–14 / 8–14 | 4–7 | konary r 2–4 blisko osi (schodki) |
| lipa (a1) | liściasty, gęsta jajowata | 22–28 | 12–16 / 8–12 | 4–6 | |
| grab (a2) | liściasty, gęsta, „żłobkowany” pień | 15–20 | 9–12 / 5–8 | 2–4 | warstwa a2 pod okapem |
| klon, jesion, buk (domieszki grądu) | liściasty | 22–30 | 10–14 / 6–12 | 3–6 | wspólny archetyp, inne parametry |

### 3.4 Determinizm i granice chunków

- Kształt drzewa to czysta funkcja (ziarno świata, pozycja pnia, parametry), więc jest niezależny od chunka i wątku.
- **Zasięg zapisu:** promień korony ≤ 16 zawsze mieści się w 3 × 3 chunkach. Większy (wolnostojący dąb o koronie 20–30 m) tylko wtedy, gdy pień leży ≥ R − 16 bloków od krawędzi chunka. Planer obcina i zgłasza takie przypadki w teście.
- **Kolejność dekoracji:** dziś drzewa i rośliny na granicach chunków zależą od kolejności, jak w wanilii (`DeterminismClientGameTest`: dowolna kolejność 0,001% bez paczki, próg 0,5%, z C2ME 3,8%, próg 8%). Większe korony zwiększą tę różnicę. Przycięcie koron komórkami sąsiadów (§3.2 pkt 6) usuwa spór drzewo–drzewo. Zostaje spór drzewo–runo sąsiedniego chunka (już dziś).
- **Struktury:** `TreeStandFeature.BUILDING_GAP = 5` zakłada korony 3–4 bloków. Większe korony trzeba przyciąć maską struktur z `ChunkHabitats` (pole `structures`) albo odsunąć pnie od budynków o promień korony (wioski w lasach, M2-12).

### 3.5 Testy i podgląd (wzorzec projektu)

- JUnit: determinizm, spójność (każdy blok drewna dochodzi łańcuchem `parent` do korzenia, promień nierosnący ku końcom), liście ≤ 6 kroków od drewna, zapis w 3 × 3 chunkach, brak sporów koron między sąsiadami, liczby bloków na drzewo i czas (`@Tag("cost")`).
- Podgląd PNG bez gry: rzuty boczne i z góry drzew każdego gatunku i klasy wieku oraz fragment drzewostanu 3 × 3 chunki (jak `LandscapePreview`/`HabitatPreview`), do recenzji kształtów przed testem w grze.
- W grze: zrzuty grądu i boru świeżego z dołu i z góry (gametest jak `vegetation`), liczniki liści i drewna na chunk w `ModFeatures.STATS`.

## 4. Wydajność

### 4.1 Liczba stanów bloków

| Element | Stany na gatunek | 15 gatunków |
|---|---|---|
| Cienki pień (wariant D) | 84 (+84 ze strugniętym) | 1260–2520 |
| Pełna kłoda + drewno | 3 + 3 | 90 |
| Liście z fazą drzewa 0–3 (§5) | 28 × 4 = 112 (bez fazy 28) | 420–1680 |
| Pełny zestaw drewna (jak topola, bez 2 dodatkowych liści) | ok. 520 | do 7800 przy 15 pełnych zestawach |

Wanilia 26.3 ma 35 723 stany [M]. Wycinek (6–9 gatunków bez pełnych zestawów) dodaje ok. 1,5–2 tys. (+4–6%), a pełny katalog M3 z 10 pełnymi zestawami ok. 8–9 tys. (+25%). Paczki modów mają często ponad 100 tys. stanów; FerriteCore z paczki zmniejsza pamięć stanów. Modele: 7 modeli pnia na gatunek, a stany dzielą wypieczone modele przez obroty. Sekcja mieszanego lasu może mieć ok. 100–150 różnych stanów, czyli paletę 8-bitową zamiast 4–6-bitowej. To ok. 2 KB więcej na sekcję w pamięci i zapisie.

### 4.2 Generacja (FEATURES) [S]

- Bloki na drzewo: sosna 80-letnia ok. 25 pnia + 20–40 gałęzi + 50–150 liści; dąb lub lipa a1 ok. 30 pnia + 60–120 gałęzi + 300–400 liści. Liście to objętość korony (elipsoida albo stożek z `06 §9.2`, zewnętrzna powłoka 1,5–2,5 bloku, wypełnienie 0,5–0,65) ze skryptu `scratchpad/count/leaf_estimate.py`: sosna 46, świerk 66, brzoza 78, grab 199, dąb 290, lipa 378, buk 393, kształt dębu wanilii 36 (wanilia liczona warstwami ok. 60). Liczby orientacyjne.
- Na chunk: bór świeży (ok. 10–11 drzew) ok. 500–700 liści i 400–600 drewna; grąd (ok. 9–15 pni, raport ekologii wycinka: 588 drzew/ha ≥ 8 cm w BPN) ok. 1000–1500 liści po przycięciu koron i 600–1000 drewna. Las wanilii to ok. 600–800 liści na chunk (10 drzew po ok. 60).
- Czas: planer 0,1–0,5 ms na drzewo plus zapis ok. 0,1–0,2 µs na blok. Daje to ok. 1–4 ms na chunk wobec obecnych 0,36–0,62 ms drzewostanu (`FastTreeFeature`, S7). Budżet całego chunka ≤ 48 ms (dziś 14–22 ms) wytrzyma to z zapasem, ale budżet FEATURES „≤ 1,0 bazy leśnej” (M2-14) nie. M3 potrzebuje nowego budżetu FEATURES dla drzew 1:1 (decyzja dla użytkownika).
- Możliwa pamięć szkieletów: biblioteka szkieletów na gatunek i klasę wieku (np. 64 warianty) z obrotem i odbiciem. Koszt na drzewo spada wtedy prawie do samego zapisu, ale tracimy dopasowanie do sąsiadów. Włączać dopiero, gdy pomiar pokaże potrzebę.

### 4.3 Światło (LIGHT)

- Cienkie pnie: tłumienie 0, więc nie zmieniają propagacji światła nieba [K].
- Liście: tłumienie 1 na blok. Etap LIGHT rośnie z liczbą liści i z liczbą sekcji z geometrią: korona 20–35 m nad gruntem dokłada w wielu miejscach jedną sekcję (16 bloków) nad terenem. W M2 LIGHT wobec bazy leśnej wynosił −6…+20%, a w Beskidach REAL do 1,12. Przy 2 × więcej liści w grądzie spodziewam się LIGHT 1,2–1,6 × bazy leśnej [S]. To główna pozycja do zmierzenia w pierwszym prototypie.
- Rozgrywka: pod pełnym okapem grądu dociera < 2% PAR (raport ekologii wycinka). Gęsta korona z kilkoma warstwami liści może obniżyć światło nieba przy gruncie. Trzeba sprawdzić, czy nie powstają w dzień miejsca odradzania potworów (wanilijny test ciemności z losowym progiem). Jeśli tak, to gatunki o lekkich koronach (sosna, brzoza, modrzew) mogą mieć liście z `propagatesSkylightDown` = true (tłumienie 0), a pozostałe zostają przy 1. To też zmniejsza koszt LIGHT.

### 4.4 Render

- Liście w trybie „Fancy” (`cutoutLeaves`) nie wycinają ścian między sąsiednimi liśćmi (`LeavesBlock.skipRendering` [K]), więc 1000–1500 liści na chunk to rząd 6–9 tys. quadów samej korony na kolumnę chunków (ciemny las wanilii ma podobnie). MoreCulling z paczki ma wycinanie liści. Nasze liście muszą być `LeavesBlock`, żeby działało to i tryb „Fast”.
- Pień wariantu D ma 9 quadów (pełna kłoda 6), ale tylko ok. 25–100 bloków na drzewo, więc to drobny udział.
- Sodium ≥ 0.6 ma wbudowane FRAPI [W], a jego `LevelColorCache` obsługuje dowolny `ColorResolver` [W]. Elementy poza blokiem i AO trzeba sprawdzić w `-Poptimization`.
- Pomiar: FPS i czas klatki w stałym przelocie kamery (nowy tryb gametestu `performance`), wierzchołki z debugu Sodium.

### 4.5 Plan pomiarów pierwszego prototypu

Tryb `stages` A/B w jednej sesji (baza M2 i prototyp), na nizinie REAL i GAMEPLAY w grądzie i borze świeżym. Mierzyć: FEATURES, LIGHT, cały chunk, liście i drewno na chunk, czas planera na drzewo (`costTest`), FPS przelotu, pamięć. Progi do decyzji użytkownika przed M3: cały chunk ≤ 48 ms (D1), a dla FEATURES i LIGHT nowe budżety „drzewa 1:1”.

## 5. Liście sezonowe

### 5.1 Mechanizm w 26.3

- Blok liści gatunku: `TintedParticleLeavesBlock` (spadające cząsteczki `tinted_leaves` biorą kolor z `ClientLevel.getClientLeafTintColor`, czyli z warstwy 0 naszego `BlockTintSource` [K]), więc jesienią cząsteczki same mają kolor jesienny. Właściwość `phase` 0–3 to faza drzewa, ustawiana przez generator dla całej korony (hash pnia), żeby drzewa przebarwiały się w różnym tempie, a każde drzewo było jednolite. 112 stanów.
- Tekstura w skali szarości z mcmeta `mipmap_strategy: dark_cutout` (jak wanilia 26.3).
- Odcień: `BlockColorRegistry.register(List.of(new SpeciesFoliageTint(…)), LEAVES)`. `colorInWorld` bierze kolor bazowy biomu z **własnego** resolvera `RAW_FOLIAGE = (biome, x, z) -> biome.getFoliageColor()`, zarejestrowanego w `ColorResolverRegistry`. To zachowuje mieszanie biomów (Z10, Biome Blend 5 × 5) i omija nakładkę SS. Potem stosuje krzywą gatunku od fazy roku: wiosenne jasne listowie, lato, jesień według `08 §9` (brzoza i lipa żółte od końca IX, buk złoty → miedziany, dąb żółtobrązowy późno, grab żółty → brązowy, olsza zielona do opadu, modrzew złoty), zima. `relevantProperties()` zwraca `PHASE`, bo `ModelGroupCollector` grupuje stany według właściwości barwiących [K].
- Faza roku z naszego mostu `Seasons.provider().yearProgress(level)` (SS albo `FallbackCalendar`), liczona po stronie klienta co tick.
- Przebudowa siatek: SS przy zmianie sub-sezonu woła `Minecraft.getInstance().levelExtractor.allChanged()` [K]. My robimy to samo przy zmianie naszego kroku koloru (np. 2–3 kroki na sub-sezon 12 dni), także bez SS. Pełna przebudowa w zasięgu 12 to kilka sekund pracy w tle z widocznym „przeskokiem”. Łagodniej: przebudowa sekcji falą od kamery przez kilkadziesiąt sekund (wymaga dostępu do znakowania sekcji w `LevelExtractor`, accessor albo mixin).
- Zgodność z SS: gdy SS jest załadowany i `SeasonHelper.changesFoliageColor(level)` zwraca false, nasze liście zostają przy kolorze letnim. Iglaste mają stały odcień gatunku z lekkim zimowym przyciemnieniem (SS ich nie rusza). Trawa i rośliny z `BlockTintSources.grass()` sezonuje SS jak dotąd.

Szkic (klient):

```java
public record SpeciesFoliageTint(SpeciesColors colors) implements BlockTintSource {
	public static final ColorResolver RAW_FOLIAGE = (biome, x, z) -> biome.getFoliageColor();

	@Override
	public int color(BlockState state) {
		return colors.summer();
	}

	@Override
	public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
		int biomeBase = level.getBlockTint(pos, RAW_FOLIAGE);
		return colors.at(ClientSeasonPhase.step(), state.getValue(SpeciesLeavesBlock.PHASE), biomeBase);
	}

	@Override
	public Set<Property<?>> relevantProperties() {
		return Set.of(SpeciesLeavesBlock.PHASE);
	}
}

// PolishForestsClient.onInitializeClient():
ColorResolverRegistry.register(SpeciesFoliageTint.RAW_FOLIAGE);
BlockColorRegistry.register(List.of(new SpeciesFoliageTint(SpeciesColors.BEECH)), ModBlocks.BEECH_LEAVES);
ClientTickEvents.END_CLIENT_TICK.register(client -> {
	if (client.level != null && ClientSeasonPhase.update(client.level)) {
		client.levelExtractor.allChanged();
	}
});
```

### 5.2 Zima: gołe gałęzie i marcescencja

- **Rekomendacja:** po stronie klienta model FRAPI dla liści liściastych. `ModelLoadingPlugin` z modyfikatorem po wypieczeniu owija model liści, a `emitQuads(QuadEmitter, BlockAndTintGetter, BlockPos, BlockState, RandomSource, Predicate<Direction>)` emituje w fazie bezlistnej model „gałązek” (rzadka tekstura cutout), a w pozostałych listowie. Stan bloku, kolizja i łup się nie zmieniają, nie ma pakietów sieciowych, a dalekie lasy wyglądają tak samo jak bliskie. Buk i dąb (młode, podrost) mogą w zimie trzymać brązowe liście (marcescencja) przez sam odcień. Modrzew w zimie dostaje model gałązek.
- **Dlaczego nie stan bloku z losowymi tickami:** blok dostaje losowy tick średnio co ok. 68 s (`randomTickSpeed` 3), ale tylko w zasięgu symulacji (zalecane 6–8 chunków, `docs/02-wydajnosc.md`), a widzimy 10–12. Dalekie lasy zostawałyby więc w poprzedniej porze. Do tego każda zmiana to pakiet bloku. Stan bloku tylko dla skutków rozgrywki (owoce, nasiona) i to w M4.
- **Koszt [S]:** 2–3 dni (owijka modelu, tekstura gałązek na gatunek albo wspólna, test z Sodium). W trybie „Fast” model gałązek będzie nieprzezroczysty z ciemnym tłem jak liście wanilii.
- **Ryzyka:** MoreCulling może wycinać ściany według kształtu liścia, nie modelu gałązek (sprawdzić); FRAPI i owinięte modele innych modów (Continuity) w paczce.

## 6. Pełne zestawy drewna w 26.3

### 6.1 Co tworzy zestaw (wzór: topola 26.3 [K][M])

- **Bloki (23):** log, wood, stripped_log, stripped_wood, planks, stairs, slab, fence, fence_gate, door, trapdoor, button, pressure_plate, sign, wall_sign, hanging_sign, wall_hanging_sign, shelf, sapling, potted_sapling, liście (topola: 3 bloki).
- **Przedmioty (22):** wszystkie bloki z przedmiotem oraz `<w>_boat` i `<w>_chest_boat`.
- **Encje (2):** `<w>_boat`, `<w>_chest_boat` (`EntityType<Boat>`, `EntityType<ChestBoat>`).
- **Pliki z datagenu (z jarów 26.3):** 23 blockstates, 22 definicje przedmiotów (`items/*.json` z `tints`), 61 modeli bloków, 6 modeli przedmiotów (łodzie, drzwi, tabliczki), 21 tabel łupów, 16 receptur, 16 advancementów receptur, wpisy w ok. 25 tagach bloków, przedmiotów i encji (`#logs`, `#planks`, `#wooden_*`, `#all_signs`, `#ceiling_hanging_signs`, `#wooden_shelves`, `#saplings`, `#leaves`, `#boats`, `#chest_boats`, encje `#boat`, …), w 26.3 częściowo jako tagi blok + przedmiot (`BlockItemTags`, np. `poplar_logs`), lang (ok. 24 wpisy).
- **Tekstury ręcznie (ok. 23 PNG na gatunek):** blok: log, log_top, stripped_log, stripped_log_top, planks, door_top, door_bottom, trapdoor, sapling, sign, hanging_sign, shelf, leaves; przedmiot: door, sign, hanging_sign, boat, chest_boat; encja: boat, chest_boat; GUI: signs, hanging_signs. Do tego dla wariantu D słoje cienkiego pnia, jeśli korowina nie wystarczy.

### 6.2 Rejestracja w kodzie (26.3, Fabric 0.161.0)

| Element | API [K] |
|---|---|
| typy dźwięków i tabliczek | `BlockSetTypeBuilder.copyOf(BlockSetType.OAK).register(id)`, `WoodTypeBuilder.copyOf(WoodType.OAK).register(id, blockSetType)` |
| bloki | `Blocks.register(ResourceKey<Block>, Function<Properties, Block>, Properties)`, `Blocks.logProperties(…)`, `Blocks.leavesProperties(SoundType.GRASS)`; klasy `RotatedPillarBlock`, `StairBlock`, `SlabBlock`, `FenceBlock`, `FenceGateBlock(WoodType, …)`, `DoorBlock(BlockSetType, …)`, `TrapDoorBlock`, `ButtonBlock`, `PressurePlateBlock`, `StandingSignBlock`, `WallSignBlock`, `CeilingHangingSignBlock`, `WallHangingSignBlock`, `ShelfBlock`, `SaplingBlock(TreeGrower, …)`, `FlowerPotBlock`, `TintedParticleLeavesBlock` |
| przedmioty | `Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix().cookingFuel(…).compostable(…)))` (pomocniki `Items.registerBlock` są prywatne); `SignItem`, `HangingSignItem`, `BoatItem(EntityType, Properties)` |
| BE tabliczek i półki | `BlockEntityTypes.SIGN.addValidBlock(…)`, `HANGING_SIGN`, `SHELF` (interfejs `FabricBlockEntityType`) |
| korowanie | `BlockTransformerHelper.registerStripping(log, stripped)` (fabric-item-api-v1) |
| palność | `FlammableBlockRegistry.getDefaultInstance().add(block, 5, 5)` (kłody), `(5, 20)` (deski, schody, płoty), liście `(30, 60)` |
| łodzie | `EntityType.Builder.of((t, l) -> new Boat(t, l, () -> BOAT_ITEM), MobCategory.MISC)…build(key)`; `DispenserBlock.registerBehavior(item, new BoatDispenseItemBehavior(type))`; klient `ModelLayerRegistry.registerModelLayer(layer, BoatModel::createBoatModel)` i `BoatModel::createChestBoatModel` (dostawca `TexturedLayerDefinitionProvider.createLayerDefinition()`), `EntityRendererRegistry.register(type, ctx -> new BoatRenderer(ctx, layer))`, warstwa `boat/<gatunek>` → tekstura `polishforests:textures/entity/boat/<gatunek>.png` |
| kolory | `BlockColorRegistry.register(…)` (klient); kolor przedmiotu liści w `items/<w>_leaves.json` (`tints`) z datagenu |
| sadzonka | `new TreeGrower("scots_pine", WeightedList.of(new Weighted<>(TREE_KEY, 1)), WeightedList.of(), WeightedList.of(), …)` |
| zakładki kreatywne | `ItemGroupEvents.modifyEntriesEvent(…)` |

Datagen (Fabric 26.3): `FabricModelProvider` z `BlockModelGenerators`: `woodProvider(log).logWithHorizontal(log).wood(wood)`, `family(planks).generateFor(BlockFamily)` (rodzina jak w `BlockFamilies`, poszerzonych przez fabric-data-generation-api-v1; warianty `SIGN` i `HANGING_SIGN` rodziny robią modele tabliczek), `createShelf(shelf, stripped_log)`, `createPlantWithDefaultItem(sapling, potted, PlantType.NOT_TINTED)`, `createTintedLeaves(leaves, TexturedModel.LEAVES, kolor)`. `ItemModelGenerators.generateFlatItem` dla łodzi. Dalej `FabricBlockLootSubProvider` (`dropSelf`, `createLeavesDrops`, `createDoorTable`, `createSlabItemTable`, `createPotFlowerItemTable`), `FabricRecipeProvider` (`planksFromLogs`, `woodFromLogs`, `woodenBoat`, `chestBoat`, `shelf`, a receptury rodziny, także tabliczek wiszących, przez `generateForEnabledBlockFamilies`), `FabricTagsProvider.BlockTagsProvider` (`BlockItemTagAppender`), `ItemTagsProvider`, `EntityTypeTagsProvider` i `FabricLanguageProvider`. Wynik idzie do `src/main/generated` i pod `checkDatagen`, jak w M2.

Koszt [S]: pomocnik `WoodSet` z datagenem i pierwszy gatunek 3–4 dni, każdy następny ok. 0,5 dnia plus tekstury. Every Compat/Moonlight wykryje zestaw po parze `<w>_planks` + `<w>_log` (raport 04 §2.4), jeśli trzymamy wanilijne nazwy.

### 6.3 D1 i D2 z punktu widzenia techniki

- **D2:** dąb, brzoza, świerk (i topola dla topoli czarnej i białej) z wanilijnymi zestawami. Własne są tylko cienkie pnie (z odwołaniem do wanilijnych tekstur `minecraft:block/oak_log` w modelu, bez kopiowania plików) i liście gatunku (kolory sezonowe). Łup cienkich pni to wanilijna kłoda. Zgodność z innymi modami i zero tekstur zestawu.
- **D1:** pełne zestawy tylko dla gatunków bez odpowiednika w wanilii i ważnych dla gracza: sosna, buk, jodła, olsza, grab, lipa, jesion, klon, modrzew, wierzba (raport 04 §10.4). Reszta „lite”: cienki pień, kłoda, liście, sadzonka. Zestaw to w praktyce praca tekstur, a nie kodu.
- **Wycinek:** bez pełnych zestawów albo jeden (sosna) jako test potoku datagenu. Gatunki wycinka dostają cienkie pnie, kłody, liście i sadzonki.

## 7. Serene Seasons: zgodność

- Zależność opcjonalna (B1). Kodu SS nie kopiujemy (licencja „All Rights Reserved” w `fabric.mod.json` SS [K]). Korzystamy tylko z API (`SeasonHelper.getSeasonState(level).getSubSeason()`, `changesFoliageColor`, `usesTropicalSeasons`) przez nasz `SereneSeasonsProvider` i z tagów danych.
- SS w 26.3 kolorystycznie dotyka tylko dwóch resolverów wanilii (trawa, listowie) i przebudowuje siatki przy sub-sezonie [K]. Nasz resolver i nasze `BlockTintSource` działają obok bez konfliktu.
- Bez SS: `FallbackCalendar` (12 dni na sub-sezon, B2) daje te same fazy, a przebudowę robimy sami (§5.1).
- Tagi SS w jarze 26.3: biomy `blacklisted_biomes`, `infertile_biomes`, `lesser_color_change_biomes`, `tropical_biomes` oraz bloki i przedmioty `spring_/summer_/autumn_/winter_/year_round_crops`, `unbreakable_infertile_crops`, `greenhouse_glass` [K]. Sadzonki w `spring_crops`, `summer_crops` i `autumn_crops`, jeśli mają rosnąć tylko w sezonie wegetacyjnym (decyzja rozgrywki).
- Test: `-Pgametest=climate` z SS i `-PnoSeasons`; zrzuty liści w 4 porach z poleceniem SS ustawiającym porę (z SS) i z przestawionym czasem świata (bez SS).

## 8. Plan techniczny dla wycinka (kolejność, koszt [S])

| Krok | Zakres | Dni | Wynik do oceny |
|---|---|---|---|
| T1 | `TrunkBlock` (wariant D), 7 szablonów, datagen dla 1 gatunku, tagi, łup; świat testowy z ręcznie postawionymi gałęziami | 2–3 | zrzuty złącz, schodków i wysięgu z Sodium i bez; liczba stanów |
| T2 | liście gatunku (`TintedParticleLeavesBlock` + `phase`), `SpeciesFoliageTint`, `RAW_FOLIAGE`, krok fazy i przebudowa | 2–3 | zrzuty 4 pór z SS i bez |
| T3 | planer `TreeShapePlan` (szkielet obu archetypów, promienie, wokselizacja, korona, BFS, przycięcie do sąsiadów) z testami i podglądem PNG | 6–10 | kadry drzew każdego gatunku i klasy wieku; testy spójności i determinizmu |
| T4 | integracja z `TreeStandPlan` i paletami dla grądu i boru świeżego (gęstości według raportu ekologii, warstwy a1/a2, struktury), sadzonki | 3–5 | zrzuty lasu z dołu i z góry w obu skalach |
| T5 | martwe drewno z tego samego wokselizatora (leżące kłody o realnej grubości, pniaki) | 1–2 | zrzuty |
| T6 | pomiary (§4.5) i ewentualne cięcia (puste korony, lżejsze liście) | 2–3 | tabela budżetów do decyzji użytkownika |
| T7 (opcja) | zima liściastych: model FRAPI gałązek; marcescencja | 2–3 | zrzuty zimy |
| T8 (opcja) | jeden pełny zestaw drewna (sosna) | 3–4 | datagen, łódź, tabliczka, półka w grze |

Razem T1–T6 ok. 16–26 dni, z T7 i T8 21–33. Tekstury (D5): na gatunek wycinka 3–4 PNG (korowina, słoje, liście, sadzonka) plus gałązki zimowe. Dla 8 gatunków to ok. 30 PNG. Propozycja: skrypt Pillow w `tools/` jako baza i ręczna poprawka (raport 04 §8). Tylko własne piksele, bez kopiowania wanilii, BOP i Wilder Wild.

## 9. Ryzyka

| # | Ryzyko | Skutek | Zabezpieczenie |
|---|---|---|---|
| R1 | LIGHT i FEATURES przy koronach 1:1 (grąd 2–2,5 × liści lasu wanilii) | budżety M2 przekroczone, wolniejsze wczytywanie | pomiar w T6; puste korony; lekkie liście dla gatunków światłożądnych; nowy budżet M3 |
| R2 | FPS (quady liści w trybie Fancy) | spadek klatek w lesie | MoreCulling (liście muszą być `LeavesBlock`); kontrola gęstości koron; pomiar przelotu |
| R3 | wysięg modelu poza blok przy Sodium i AO | szwy lub znikanie fragmentów na granicy sekcji | test T1 z `-Poptimization`; zapas: wariant C (FRAPI) |
| R4 | schodki ukośnych grubych konarów | „kanciaste” dęby | konary blisko osi, ukośność tylko r ≤ 2 |
| R5 | determinizm przy większych koronach | wzrost różnic przy dowolnej kolejności | przycięcie koron komórkami sąsiadów; próg testu determinizmu bez zmian |
| R6 | zasięg zapisu 3 × 3 chunki | obcięte korony olbrzymów | R ≤ 16 albo pień blisko środka chunka; szablon dla „Bartka” |
| R7 | struktury (`BUILDING_GAP` 5) | korony nad dachami wiosek | maska struktur w przycięciu korony |
| R8 | dekoratory wanilii na cienkich pniach | huby i mech „wiszą” | własne huby z przesunięciem (M4) albo tylko na pełnych kłodach |
| R9 | przebudowa siatek przy zmianie fazy | zacięcie i przeskok koloru | 2–3 kroki na sub-sezon; przebudowa falą |
| R10 | światło pod okapem a potwory w dzień | potwory w grądzie w dzień | pomiar światła przy gruncie; tłumienie 0 dla wybranych gatunków |
| R11 | Distant Horizons i LOD | cienkie pnie jak pełne kolumny z daleka | przyjąć |
| R12 | licencje cudzych modów | spór licencyjny | kod tylko z MIT (DT, RU, kod Ecologics) z informacją o licencji; LGPL wyłącznie jako zależność; zasoby zawsze własne |

## 10. Decyzje do podjęcia (propozycje z tego badania)

| # | Decyzja | Warianty | Rekomendacja |
|---|---|---|---|
| T-D1 | Technika cienkich pni | D (promień + rodzic, JSON) / C (model dynamiczny FRAPI, jak DT) / B (stany połączeń) | D, a C jako zapas po T1 |
| T-D2 | Klasy grubości | co 2 px (r 1–7) / co 1 px (r 1–14, 168 stanów) / 4 klasy | co 2 px |
| T-D3 | Generator | parametryczny / parametryczny + kolonizacja przestrzeni dla liściastych / szablony | parametryczny + kolonizacja dla koron liściastych, szablony tylko dla pomników |
| T-D4 | Liście sezonowe (B6) | własne kolory gatunku z fazą drzewa / nakładka SS dla wszystkich / statyczne bloki jesienne jak topola 26.3 | własne kolory gatunku |
| T-D5 | Zima liściastych | model FRAPI gałązek po stronie klienta / brązowe liście przez zimę / stan bloku z losowymi tickami | FRAPI po stronie klienta (w wycinku opcjonalnie) |
| T-D6 | Pełne zestawy w wycinku (D1, D2) | brak / sosna / wszystkie gatunki wycinka | brak albo sosna; dąb, brzoza i świerk z wanilii |
| T-D7 | Budżety drzew 1:1 | nowy budżet FEATURES i LIGHT dla M3 / zostawić M2-14 | nowy budżet po pomiarze T6; cały chunk ≤ 48 ms zostaje |

## 11. Źródła

Kod i dane lokalne:
- Źródła Minecraft 26.3 (Loom cache projektu): `world/level/levelgen/feature/{Feature,TemplateFeature,TreeFeature,FallenTreeFeature,FeatureTypes}.java`, `trunkplacers/TrunkPlacerType.java`, `foliageplacers/FoliagePlacerType.java`, `treedecorators/{TreeDecorator,TreeDecoratorType}.java`, `world/level/block/{LeavesBlock,TintedParticleLeavesBlock,ChainBlock,RotatedPillarBlock,Blocks,Block}.java`, `world/level/block/state/BlockBehaviour.java`, `world/phys/shapes/Shapes.java`, `world/level/block/grower/TreeGrower.java`, `world/level/levelgen/Heightmap.java`, `world/level/chunk/status/ChunkPyramid.java`, `server/level/WorldGenRegion.java`, `world/item/component/BlockTransformers.java`, `core/component/{BlockTransformer,DataComponents}.java`, `world/item/{Items,Item,BoatItem}.java`, `world/entity/EntityTypes.java`, `world/entity/vehicle/boat/{Boat,ChestBoat,AbstractBoat}.java`, `core/dispenser/DispenseItemBehavior.java`, `world/level/block/entity/BlockEntityTypes.java`, `data/{BlockFamilies,BlockFamily}.java`, `data/tags/Vanilla*TagsProvider.java`, `data/recipes/packs/VanillaRecipeProvider.java`, `data/loot/packs/VanillaBlockLoot.java`; klient: `client/color/block/{BlockColors,BlockTintSource,BlockTintSources}.java`, `client/renderer/BiomeColors.java`, `client/multiplayer/ClientLevel.java`, `client/renderer/extract/LevelExtractor.java`, `client/renderer/chunk/{ChunkSectionLayer,SectionCompiler}.java`, `client/renderer/block/ModelBlockRenderer.java`, `client/resources/model/cuboid/CuboidModelElement.java`, `client/data/models/{BlockModelGenerators,ItemModelGenerators}.java`, `client/renderer/entity/{BoatRenderer,EntityRenderers}.java`, `client/model/geom/{ModelLayers,LayerDefinitions}.java`.
- Zasoby `minecraft-client.jar` 26.3: `blockstates/end_rod.json`, `poplar_sign.json`, `models/block/leaves.json`, `poplar_sign_rot_0.json`, `items/oak_leaves.json`, `textures/block/*_leaves.png.mcmeta`, `data/minecraft/block_transformer/axe.json`; listing plików topoli.
- Fabric API 0.161.0+26.3 (jary w Gradle cache): `BlockColorRegistry`, `BlockTintsFactory`, `ColorResolverRegistry`, `ModelLayerRegistry`, `EntityRendererRegistry` (fabric-rendering-v1), `FabricBlockStateModel` (fabric-renderer-api-v1), `ModelLoadingPlugin`, `CustomUnbakedBlockStateModel` (fabric-model-loading-api-v1), `WoodTypeBuilder`, `BlockSetTypeBuilder`, `FabricBlockEntityType` (fabric-object-builder-api-v1), `BlockTransformerHelper`, `BlockTransformerEvents` (fabric-item-api-v1), `FlammableBlockRegistry` (fabric-content-registries-v0), `FabricTagsProvider` i `FabricDynamicRegistryProvider` (fabric-data-generation-api-v1).
- Serene Seasons 26.1.2.0.7 (`maven.modrinth:serene-seasons:V9PxJPuw`): `sereneseasons.accesswidener`, `SeasonColorHandlers`, `SeasonHandlerClient`, `api/season/{SeasonHelper,ISeasonState,Season}`, `data/sereneseasons/tags/**`, `fabric.mod.json` (licencja).
- Pomiar stanów: `scratchpad/count/CountStates.java`, `CountPoplar.java` (Bootstrap na `minecraft-common-deobf-26.3.jar`).
- Projekt: `docs/BRIEF.md`, `docs/00-decyzje-do-podjecia.md`, `docs/01-architektura.md` §5, §8, `docs/02-wydajnosc.md`, `docs/03-m2-biomy.md` §8.4–§8.8, §9, `docs/research/04` i `06` §9, `08` §9, `11.4`, `src/main/java/.../feature/{FastTreeFeature,TreeStandFeature}.java`, raport ekologii wycinka `scratchpad/slice/research-ekologia.md`.

W sieci:
- Dynamic Trees: [repozytorium](https://github.com/DynamicTreesTeam/DynamicTrees) (licencja MIT według GitHub API; gałąź `develop/26.1.2`: `common/.../block/branch/{BranchBlock,BasicBranchBlock,ThickBranchBlock,TrunkShellBlock}.java`, `fabric/.../model/baked/BasicBranchBlockBakedModel.java`, `fabric/.../compat/SereneSeasonsSeasonProvider.java`), [Modrinth](https://modrinth.com/mod/dynamictrees).
- Regions Unexplored: [repozytorium](https://github.com/Apollounknowndev/RegionsUnexplored) (MIT; gałąź `21.1`: `block/type/wood/BranchBlock.java`, `world/level/block/wood/SmallOakLogBlock.java`), [Modrinth](https://modrinth.com/project/Tkikq67H).
- Terraform: [repozytorium](https://github.com/TerraformersMC/Terraform) (LGPL-3.0; gałąź 26.3: `terraform-wood-api-v1/.../wood/api/block/{SmallLogBlock,BareSmallLogBlock,QuarterLogBlock}.java`); [Terrestria](https://github.com/terraformersmc/terrestria) (LGPL-3.0).
- Ecologics: [repozytorium i LICENSE](https://github.com/samedifferent/Ecologics) (kod MIT, `resources` All Rights Reserved).
- Wilder Wild ([GitHub](https://github.com/FrozenBlock/WilderWild)) i Nature's Spirit ([GitHub](https://github.com/Team-Hibiscus/NaturesSpirit)): licencje według API Modrinth.
- Sodium: [repozytorium](https://github.com/CaffeineMC/sodium) (gałąź `dev`: `common/.../client/world/biome/LevelColorCache.java`); FRAPI wbudowane od 0.6.0 ([notka wydania 0.6.0-beta.1](https://modrinth.com/mod/sodium/version/mc1.21-0.6.0-beta.1-fabric), [Indium](https://modrinth.com/mod/Indium)).
- MoreCulling: [Modrinth](https://modrinth.com/project/RyrUIzkQ) (wycinanie liści; tryby ustawień niesprawdzone w tej wersji).
- A. Runions, B. Lane, P. Prusinkiewicz, *Modeling Trees with a Space Colonization Algorithm*, Eurographics Workshop on Natural Phenomena 2007: [algorithmicbotany.org](https://www.algorithmicbotany.org/papers/colonization.egwnp2007.html), [PDF](https://www.algorithmicbotany.org/papers/colonization.egwnp2007.pdf), [EG Digital Library](https://diglib.eg.org/handle/10.2312/NPH.NPH07.063-070).
- Z wiedzy ogólnej (bez pobrania w tej sesji): P. Prusinkiewicz, A. Lindenmayer, *The Algorithmic Beauty of Plants* (1990); J. Weber, J. Penn, *Creation and Rendering of Realistic Trees*, SIGGRAPH 1995; model rury (Shinozaki i in. 1964) i reguła Leonarda (Eloy 2011, Physical Review Letters).

Niepewności: zachowanie elementów modelu poza blokiem przy Sodium i AO (R3), koszt LIGHT i FPS (R1, R2), wycinanie liści przez MoreCulling dla modelu gałązek, dokładne nazwy metod kontekstu `ModelModifier.AfterBakeBlock` (nie sprawdzałem sygnatur) i wszystkie liczby oznaczone [S]. Raport niczego w repozytorium nie zmienia.
