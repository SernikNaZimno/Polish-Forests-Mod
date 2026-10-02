# Technika tworzenia zwierząt w Fabric: modele, tekstury, animacje, AI, spawn (50-80 gatunków polskiej fauny)

Data: 2026-09-21
Projekt: "Polish Forests", dawniej "Przyrodniczo zgodne lasy" (mod Fabric)
Status: raport zakończony w ramach budżetu 30 zapytań WebSearch/WebFetch (wykorzystano 29) + odczyty kodu z GitHub (API/raw). Braki oznaczono "NIE ZBADANO" lub "do potwierdzenia".

## Streszczenie

Fabric od Minecrafta 26.1 (14 marca 2026) używa wyłącznie mapowań Mojang (gra nie jest już obfuskowana, Yarn nie jest wspierany po 1.21.11), wymaga Javy 25 i nowej wtyczki Loom; oficjalna dokumentacja Fabric dokumentuje wersje 26.2, 26.1.2, 1.21.11, 1.21.1 i 1.20.4. Rejestracja encji to `EntityType.Builder` + `Registry.register(BuiltInRegistries.ENTITY_TYPE, ...)`, atrybuty przez `FabricDefaultAttributeRegistry`, renderer przez `EntityRenderers.register`, a od MC 1.21.2 modele pracują na `EntityRenderState` (kod modeli z 1.21.1 nie kompiluje się w nowszych wersjach). Dla 50-80 gatunków rekomendowany jest GeckoLib (5.5.1 dla 26.2, 4.x dla 1.20.1/1.21.1): modele i animacje to pliki JSON w formacie Bedrock (`.geo.json` format_version 1.12.0, `.animation.json` 1.8.0), które da się generować programowo i dopracowywać w Blockbench; natywny `.bbmodel` nie ma specyfikacji i nie powinien być celem generatora. Tekstury w modach zwierzęcych mają 128x128 (duże ssaki), 64x64/64x32 (średnie), 32x32 (ptaki) w gęstości wanilii; box UV zajmuje 2(sx+sz) × (sy+sz) px na sześcian, per-face UV pozwala pomijać ściany. Zasoby Naturalist są All Rights Reserved (kod MIT), Untamed Wilds jest GPL-3.0, Eager Beavers CC0 - tekstury i modele muszą być własne. Wanilijny system Goal jest używany przez wszystkie przeanalizowane mody zwierzęce; system Brain (pamięć, sensory co 20 ticków, aktywności, `Schedule` dobowy) lepiej pasuje do rytmu dobowego/sezonowego, stad i stanów, a biblioteka SmartBrainLib (MPL-2.0, aktywna, autor GeckoLib) usuwa jego największe wady. Limit spawnu CREATURE to tylko 10 zwierząt na obszar 17x17 chunków (cykl co 400 ticków), więc bogatą faunę trzeba zapewnić własnym spawnerem tickowanym (wzorzec `CatSpawner`/`PhantomSpawner`), własnymi grupami spawnu (mixin w `SpawnGroup`) i spawnem przy generacji chunków, a jednocześnie własne zwierzęta powinny despawnować poza zasięgiem. Żaden zbadany mod nie ma sezonowej hibernacji ani migracji; Naturalist ma tylko sen nocny niedźwiedzia (`canSleep` wg `getDayTime`), Eager Beavers ma bobry realnie noszące kłody do wody (bez hydrologii), Untamed Wilds ma nory i gniazda jako bloki z BlockEntity oraz stada/watahy. Najlepszym wzorcem "symulacji poza zasięgiem" jest ul pszczeli: dom (gawra, nora, żeremie, gniazdo) jako BlockEntity przechowujący NBT mieszkańców plus rejestr domów w `SavedData` z dogenerowaniem populacji przy ładowaniu chunka. Serene Seasons udostępnia `SeasonHelper.getSeasonState(level).getSubSeason()` (12 podsezonów) i istnieje na Fabric także dla 26.1.2. Dźwięki: mono OGG Vorbis, `sounds.json` z wariantami; nagrania z xeno-canto (CC, prawa u nagrywających; do moda tylko CC BY/BY-SA/CC0) i Freesound (CC0/CC BY/CC BY-NC) z obowiązkową atrybucją. Wanilia nie ma "entity activation range" (Paper: animals 32 bl., tracking 96 bl.), więc budżet AI dla dalekich encji trzeba zaimplementować samemu. Raport zawiera przykładową strukturę kodu bobra (Brain/SmartBrainLib, AmphibiousPathNavigation, żeremie jako BlockEntity, własny spawner kolonii).

## 1. Rejestracja encji i renderer/model (API Fabric, mapowania Yarn/Mojang, 1.20.1 / 1.21.1 / najnowsza)

### 1.1. Kod referencyjny z oficjalnej Fabric Wiki (tutorial:entity) - UWAGA: strona używa już mapowań Mojang

Źródło: https://wiki.fabricmc.net/tutorial:entity (pobrane 2026-09-21). Strona sama zaznacza, że jest przestarzała i odsyła do https://docs.fabricmc.net/develop/entities/first-entity. Kod na wiki jest w **mapowaniach Mojang** (`BuiltInRegistries`, `PathfinderMob`, `MobCategory`, `LayerDefinition`, `MeshDefinition`, `CubeListBuilder`, `PartPose`, `MobRenderer`, `EntityRendererProvider.Context`, `ModelLayerLocation`, `EntityRenderers`) i już w nowym modelu renderowania z `EntityRenderState` (wprowadzonym w MC 1.21.2/1.21.3):

```java
// Rejestracja typu encji (Mojang mappings, MC >= 1.21.2 - Identifier.fromNamespaceAndPath)
public static final EntityType<CubeEntity> CUBE = Registry.register(
    BuiltInRegistries.ENTITY_TYPE,
    Identifier.fromNamespaceAndPath("entitytesting", "cube"),
    EntityType.Builder.create(CubeEntity::new, MobCategory.CREATURE)
        .dimensions(0.75f, 0.75f)
        .build("cube"));   // w 1.21.2+ build() przyjmuje ResourceKey<EntityType<?>>, nie String - patrz 1.2

public class CubeEntity extends PathfinderMob {
    public CubeEntity(EntityType<? extends PathfinderMob> entityType, Level level) { super(entityType, level); }
}

// Atrybuty (wspólne dla wszystkich wersji, Fabric API):
FabricDefaultAttributeRegistry.register(CUBE, CubeEntity.createMobAttributes());

// Model (Mojang: EntityModel<RenderState>, LayerDefinition, MeshDefinition, PartDefinition, CubeListBuilder, PartPose)
public class CubeEntityModel extends EntityModel<CubeEntityRenderState> {
    private final ModelPart base;
    public CubeEntityModel(ModelPart modelPart) { this.base = modelPart.getChild(PartNames.CUBE); }
    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();
        root.addOrReplaceChild(PartNames.CUBE,
            CubeListBuilder.create().texOffs(0, 0).addBox(-6F, 12F, -6F, 12F, 12F, 12F),
            PartPose.rotation(0F, 0F, 0F));
        return LayerDefinition.create(modelData, 64, 64);   // rozmiar tekstury 64x64
    }
    @Override public void setupAnim(CubeEntityRenderState state) {}
}

// Renderer
public class CubeEntityRenderer extends MobRenderer<CubeEntity, CubeEntityRenderState, CubeEntityModel> {
    public CubeEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new CubeEntityModel(context.bakeLayer(EntityTestingClient.MODEL_CUBE_LAYER)), 0.5f); // 0.5f = promień cienia
    }
    @Override public CubeEntityRenderState createRenderState() { return new CubeEntityRenderState(); }
    @Override public Identifier getTextureLocation(CubeEntityRenderState state) {
        return Identifier.fromNamespaceAndPath("entitytesting", "textures/entity/cube/cube.png");
    }
}

// Rejestracja po stronie klienta
public class EntityTestingClient implements ClientModInitializer {
    public static final ModelLayerLocation MODEL_CUBE_LAYER =
        new ModelLayerLocation(Identifier.fromNamespaceAndPath("entitytesting", "cube"), "main");
    @Override public void onInitializeClient() {
        EntityRenderers.register(EntityTesting.CUBE, CubeEntityRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(MODEL_CUBE_LAYER, CubeEntityModel::getTexturedModelData);
    }
}
```

### 1.2. Tabela odpowiedników nazw Yarn <-> Mojang (istotnych dla encji)

Zestawienie na podstawie znajomości obu mapowań; pozycje oznaczone (*) zweryfikowane w źródłach pobranych w tym raporcie, pozostałe - z pamięci, do potwierdzenia w Linkie (https://linkie.shedaniel.me/) przed użyciem.

| Yarn (1.20.1 / 1.21.1) | Mojang (official) | Uwagi |
|---|---|---|
| `Registries.ENTITY_TYPE` | `BuiltInRegistries.ENTITY_TYPE` (*) | |
| `Identifier.of(ns, path)` (1.21+) / `new Identifier(ns, path)` (1.20.1) | `ResourceLocation.fromNamespaceAndPath` / (od 26.x) `Identifier.fromNamespaceAndPath` (*) | w 1.21 konstruktor `Identifier` stał się prywatny -> `Identifier.of` |
| `SpawnGroup.CREATURE` | `MobCategory.CREATURE` (*) | |
| `EntityType.Builder.create(factory, group)` | `EntityType.Builder.of(factory, category)`; na wiki `create` (*) | `.dimensions(w, h)`; w 1.21.2+ `.build(RegistryKey<EntityType<?>>)` |
| `FabricEntityTypeBuilder` (Fabric API) | j.w. | **przestarzały** od Fabric API dla 1.20.5+; Fabric udostępnia zamiast tego rozszerzenia `EntityType.Builder` (np. interfejs `FabricEntityTypeBuilder`-injected `alwaysUpdateVelocity`) - do potwierdzenia w docs |
| `PathAwareEntity` | `PathfinderMob` (*) | bazowa dla zwierząt: `AnimalEntity` / `Animal` |
| `MobEntity` | `Mob` | |
| `AnimalEntity` | `Animal` | |
| `DefaultAttributeContainer.Builder`, `MobEntity.createMobAttributes()` | `AttributeSupplier.Builder`, `Mob.createMobAttributes()` (*) | |
| `EntityAttributes.GENERIC_MAX_HEALTH` (1.20.1/1.21.1) / `EntityAttributes.MAX_HEALTH` (1.21.2+) | `Attributes.MAX_HEALTH` | w 1.21.2 usunięto prefiks GENERIC_ |
| `EntityModel<T>` , `SinglePartEntityModel` | `EntityModel<S extends EntityRenderState>` (*), `HierarchicalModel` (do 1.21.1) | |
| `TexturedModelData.of(ModelData, w, h)` | `LayerDefinition.create(MeshDefinition, w, h)` (*) | |
| `ModelData`, `ModelPartData`, `ModelPartBuilder.create().uv(u,v).cuboid(...)`, `ModelTransform.pivot/of` | `MeshDefinition`, `PartDefinition`, `CubeListBuilder.create().texOffs(u,v).addBox(...)`, `PartPose.offset/rotation` (*) | |
| `EntityModelLayer` | `ModelLayerLocation` (*) | |
| `EntityModelLayerRegistry.registerModelLayer` (Fabric API) | to samo (*) | |
| `EntityRendererRegistry.register` (Fabric API) | vanilla `EntityRenderers.register` (*) | |
| `MobEntityRenderer<T, M>` (1.20.1/1.21.1) / `MobEntityRenderer<T, S, M>` (1.21.2+) | `MobRenderer` (*) | 3 parametry generyczne od 1.21.2 |
| `EntityRendererFactory.Context` | `EntityRendererProvider.Context` (*) | |
| `getTexture(T entity)` / od 1.21.2 `getTexture(S state)` | `getTextureLocation(S state)` (*) | |
| `setAngles(...)` | `setupAnim(...)` (*) | od 1.21.2 przyjmuje RenderState, nie encję |
| `EntityRenderState` (1.21.2+) | `EntityRenderState` | model nie ma dostępu do encji na kliencie - dane trzeba kopiować w `updateRenderState` |

**Kluczowy fakt dla planowania wersji:** w MC 1.21.2 (listopad 2024) Mojang przebudował renderowanie encji na `EntityRenderState` - kod modeli/rendererów z 1.21.1 nie kompiluje się w 1.21.2+. GeckoLib ukrywa tę różnicę w dużej mierze (własny `GeoRenderer`), co jest argumentem za nim przy 50+ gatunkach utrzymywanych między wersjami.

### 1.3. Oficjalna dokumentacja Fabric "Creating your first entity" (docs.fabricmc.net, wersja 26.2)

Źródło: https://docs.fabricmc.net/develop/entities/first-entity (pobrane 2026-09-21). Selektor wersji na stronie oferuje: **26.2, 26.1.2, 1.21.11, 1.21.1, 1.20.4** - to lista wersji MC aktualnie dokumentowanych przez Fabric. Dokumentacja używa **mapowań Mojang** i ostrzega: "Blockbench supports multiple mappings (such as Mojang Mappings, Yarn, and others). Ensure you select the correct mapping that matches your development environment - this tutorial uses Mojang Mappings."

```java
// Rejestracja (26.2): ResourceKey<EntityType<?>> jako klucz, EntityType.Builder.of(...).sized(w, h)
public static final EntityType<MiniGolemEntity> MINI_GOLEM = register(
    ModEntityTypeIds.MINI_GOLEM,
    EntityType.Builder.<MiniGolemEntity>of(MiniGolemEntity::new, MobCategory.MISC).sized(0.75f, 1.75f));

// Atrybuty
public static AttributeSupplier.Builder createMiniGolemAttributes() {
    return PathfinderMob.createMobAttributes()
        .add(Attributes.MAX_HEALTH, 5)
        .add(Attributes.TEMPT_RANGE, 10)      // nowy atrybut (1.21.2+): zasięg TemptGoal
        .add(Attributes.MOVEMENT_SPEED, 0.3);
}
// FabricDefaultAttributeRegistry.register(MINI_GOLEM, createMiniGolemAttributes());

// Warstwa modelu i renderer
public static final ModelLayerLocation MINI_GOLEM = new ModelLayerLocation(ExampleMod.id("mini_golem"), "main");
ModelLayerRegistry.registerModelLayer(ModEntityModelLayers.MINI_GOLEM, MiniGolemEntityModel::getTexturedModelData); // uwaga: w 26.x nazwa Fabric API to ModelLayerRegistry (na wiki 1.21: EntityModelLayerRegistry)
public class MiniGolemEntityRenderer extends MobRenderer<MiniGolemEntity, MiniGolemEntityRenderState, MiniGolemEntityModel> {
    public MiniGolemEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new MiniGolemEntityModel(context.bakeLayer(ModEntityModelLayers.MINI_GOLEM)), 0.375f);
    }
    public Identifier getTextureLocation(MiniGolemEntityRenderState state) { return TEXTURE; }
}
EntityRenderers.register(ModEntityTypes.MINI_GOLEM, MiniGolemEntityRenderer::new);
```

Model w tutorialu używa `LayerDefinition.create(modelData, 64, 32)` (tekstura 64x32). Animacje wanilii: `AnimationState` (na encji), `AnimationDefinition.Builder` + `AnimationChannel` (definicja keyframe'ów w kodzie Java, eksportowana z Blockbench przez "Export -> Java Animation"), zastosowanie `KeyframeAnimation` w modelu. Dokumentacja odsyła do osobnego artykułu "Creating a Spawn Egg" (nie pobrano - budżet).

### 1.4. Wersje Minecrafta i mapowania - stan na 2026-09-21

Źródła: blog Fabric "Fabric for Minecraft 26.1" (https://fabricmc.net/2026/03/14/261.html), "Fabric for Minecraft 1.21.11" (https://fabricmc.net/2025/12/05/12111.html), docs "Migrating Mappings" (https://docs.fabricmc.net/develop/porting/mappings/).

| Fakt | Wartość | Źródło |
|---|---|---|
| Nowe nazewnictwo wersji MC | po 1.21.11 (grudzień 2025) Mojang przeszedł na numerację rok.wydanie: **26.1** (14 marca 2026), **26.2** (aktualna w docs Fabric, 2026-09) | blog Fabric 26.1, docs.fabricmc.net |
| Obfuskacja | "26.1 is the first version of Minecraft to not be obfuscated. Because of that, no mods from 1.21.11 or before will work without, at minimum, recompilation." | blog Fabric 26.1 |
| Yarn | "Yarn will not be available for versions of Minecraft after 1.21.11" - Yarn nieoficjalnie wspierany od 26.1; Fabric API przemianowało API na nazwy Mojang (np. `ItemGroupEvents` -> `CreativeModeTabEvents`) | blog 1.21.11, 26.1 |
| Loom | nowa wtyczka `net.fabricmc.fabric-loom` (bez remapowania) zamiast `net.fabricmc.fabric-loom-remap`; Loom 1.15, Gradle 9.4.0 | blog 26.1 |
| Java | **Java 25** minimum dla Gradle JVM (26.1); IntelliJ IDEA 2025.3+ (mixiny) | blog 26.1 |
| Fabric Loader | 0.18.4 (marzec 2026) | blog 26.1 |
| Usunięte moduły Fabric API w 26.1 | `fabric-convention-tags-v1`, `fabric-loot-api-v2`, `HudRenderCallback` (-> `HudElementRegistry`) | blog 26.1 |
| Zmiany MC 26.1 istotne dla encji | `ItemStackTemplate` zamiast `ItemStack` przed załadowaniem świata; `ChunkSectionLayer`; handel wieśniaków sterowany datapackiem; `FluidModel` | blog 26.1 |
| Renderer | 26.2 snapshoty: wybór backendu OpenGL/Vulkan, OpenGL planowany do usunięcia po stabilizacji Vulkana | blog 26.1 |
| Narzędzia migracji Yarn -> Mojang | Loom Gradle Plugin (`migrateMappings`) lub wtyczka IntelliJ "Ravel" | docs Migrating Mappings |

**Wniosek dla wyboru wersji docelowej:** 
- 1.20.1: najbardziej "zamrożona" wersja modpacków, Yarn, GeckoLib 4.x, stary renderer (model ma dostęp do encji) - ale Fabric docs już jej nie dokumentuje (najstarsza w selektorze: 1.20.4).
- 1.21.1: nadal szeroko wspierana (Naturalist ma gałąź 1.21.1-NeoForge), Yarn, GeckoLib 4.x, renderer przed EntityRenderState.
- 26.1/26.2 ("najnowsza"): mapowania Mojang obowiązkowe, Java 25, EntityRenderState, GeckoLib 5; kod encji z 1.21.1 wymaga przepisania modeli/rendererów. Serene Seasons - dostępność na 26.x do potwierdzenia (raport 04/05?).
- Jeśli mod ma powstać "dogłębnie" i długo żyć, warto pisać od razu w mapowaniach Mojang (nawet na 1.21.1 - Loom pozwala użyć `officialMojangMappings()`), żeby migracja do 26.x była mniejsza.

## 2. GeckoLib 4/5, AzureLib, Blockbench (.geo.json, .animation.json, .bbmodel) - wybór dla 50+ gatunków

### 2.1. Wersje GeckoLib (oficjalna wiki GeckoLib 5, źródło: https://github.com/Tslat/Geckolib-Wiki/blob/main/docs/index.mdx, opublikowana na https://wiki.geckolib.com)

| Wersja MC | Wersja GeckoLib | Wsparcie (wg wiki) |
|---|---|---|
| 26.2 | 5.5.1 | aktywny rozwój, poprawki, wsparcie społeczności, dokumentacja |
| 26.1.2 | 5.5.1 | j.w. |
| 26.1 | 5.5 | wsparcie społeczności + dokumentacja |
| 1.21.11 | 5.4.3 | tylko dokumentacja |
| 1.21.10 | 5.3-Alpha-3 | brak |
| 1.21.9 | - | brak wersji |
| 1.21.8 / 1.21.7 / 1.21.6 | 5.2.2 / 5.2.1 / 5.2.0 | stara wiki |
| 1.21.5 | 5.1.0 | stara wiki |
| 1.21.1 i 1.20.1 | GeckoLib **4.x** (stara wiki https://github.com/bernie-g/geckolib/wiki, sekcja "Geckolib 4") | nie ma w tabeli GeckoLib 5; wersje 4.x nadal na Modrinth/CurseForge (nie zweryfikowano numerów - do potwierdzenia) |

Stara wiki (bernie-g/geckolib/wiki) ma rozdziały: Installation, Getting Started, Geo Models, Animation Controller (GL4), Emissive Textures, Render Layers, Keyframe Events, Examples. Nowa wiki (GeckoLib 5) ma rozdziały: entities (the-entity-class, the-entity-renderer, animating-the-entity, common-issues, copy-paste-templates, replaced-entities), blocks, armor, concepts (animation: animationpoint, animationtimeline, controller/{overview, statehandler, defaultanimations, ordering}, molang; geobones: bone-position-listeners, bone-snapshots, bone-updaters; geomodels: overview, basic, defaulted, automatic; rendering: renderstates, renderpassinfo, datatickets), examples (entities, items, blocks, armor), making-models/placing-the-files.

### 2.2. Minimalna encja GeckoLib 5 (kod z wiki, mapowania Mojang)

```java
public class ExampleEntity extends PathfinderMob implements GeoEntity {
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    public ExampleEntity(EntityType<? extends PathfinderMob> entityType, Level level) { super(entityType, level); }
    @Override public void registerControllers(final AnimatableManager.ControllerRegistrar controllers) { /* kontrolery animacji */ }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return this.geoCache; }
}
// Renderer "Simple" - bez własnej klasy (zalecane przez wiki, gdy nie trzeba nic nadpisywać):
//   EntityRenderers.register(EntityRegistry.EXAMPLE_ENTITY, context -> new GeoEntityRenderer<>(context, EntityRegistry.EXAMPLE_ENTITY));
// Renderer "Advanced":
public class ExampleEntityRenderer<R extends EntityRenderState & GeoRenderState> extends GeoEntityRenderer<ExampleEntity, R> {
    public ExampleEntityRenderer(EntityRendererProvider.Context context, EntityType<ExampleEntity> entityType) { super(context, entityType); }
}
```

Ścieżki zasobów przy automatycznym GeoModelu (renderer z `EntityType`) - encja `example_entity`:
- Model: `resources/assets/<mod_id>/geckolib/models/entity/example_entity.geo.json`
- Animacje: `resources/assets/<mod_id>/geckolib/animations/entity/example_entity.animation.json`
- Tekstura: `resources/assets/<mod_id>/textures/entity/example_entity.png`

`DefaultedEntityGeoModel(Identifier.fromNamespaceAndPath(MOD_ID, "animal/example_animal"))` -> `geckolib/models/entity/animal/example_animal.geo.json`, `geckolib/animations/entity/animal/example_animal.animation.json`, `textures/entity/animal/example_animal.png`. (W GeckoLib 4 ścieżki to `geo/`, `animations/`, `textures/` - jak w Eager Beavers: `assets/beavermod/geo/beaver.geo.json`.) Własne podfoldery: klasa `extends DefaultedGeoModel` z nadpisanym `subtype()`.

RenderStates w GeckoLib 5: "RenderStates are a concept Mojang introduced to Minecraft in the 1.21.x versions" - renderer nie ma dostępu do encji; dane potrzebne przy renderowaniu (np. czy oswojony, wariant futra, sezon) trzeba zapisać przez `DataTicket`: w `GeoEntityRenderer#addRenderData(entity, relatedObject, renderState, partialTick)` -> `renderState.addGeckoLibData(ModDataTickets.IS_TAMED, animatable.hasOwner())`, odczyt `renderPassInfo.getGeckolibData(...)`. Dla naszego moda: warianty tekstur (futro zimowe/letnie, poroże) i skala młodych muszą przejść przez DataTickets (GeckoLib 5) lub `EntityRenderState` (wanilia 1.21.2+).

Kontrolery animacji (na przykładzie Eager Beavers, GeckoLib 4 - API w 5 podobne): `new AnimationController<>(this, "Walk/Idle", transitionTicks, state -> state.setAndContinue(ANIM))`, `RawAnimation.begin().thenLoop("animation.beaver.walk")`, `PlayState.CONTINUE/STOP`; wiele kontrolerów równolegle (ruch, głowa, jedzenie) - każdy steruje innymi kośćmi.

### 2.3. Format .bbmodel (natywny Blockbench) - czy generować programowo?

Źródła: Blockbench Wiki "The .bbmodel format" (https://blockbench.net/wiki/docs/bbmodel/), gist mgerhardy "Blockbench model bbmodel format spec" (https://gist.github.com/mgerhardy/48adb8a9ccaa2079779ac38a4324fa8e), DeepWiki JannisX11/blockbench "BBModel Format".
- .bbmodel to JSON (opcjonalnie skompresowany LZUTF8 z prefiksem `<lz>`), format_version 5.0 (aktualnie); pola: `meta {format_version, model_format ("bedrock" / "geckolib"? / "modded_entity" / "java_block" / "free"), box_uv}`, `name`, `resolution {width, height}`, `elements[]` (cube: `type, uuid, name, from, to, origin, rotation, uv_offset, faces{north..down: {uv, texture}}`), `outliner[]` (grupy = kości: `name, uuid, origin, rotation, isOpen, children[uuid|group]`), `textures[]` (z base64 `source`), `animations[]` (keyframes per bone, `animators`).
- Oficjalne stanowisko Blockbench: "There is no complete specification of the JSON format at this point in time. If you are looking to create a model loader or similar system that works with Blockbench, it is generally recommended to create a separate custom format via a Blockbench plugin, instead of relying on bbmodel files."
- **Wniosek:** generować programowo należy **`.geo.json` + `.animation.json`** (formaty Bedrock są udokumentowane przez Microsoft i stabilne, Blockbench je importuje "File -> Import -> Bedrock/GeckoLib model" i pozwala dalej ręcznie edytować); .bbmodel traktować jako plik roboczy artysty, generowanie go jest możliwe (JSON), ale bez gwarancji stabilności.

### 2.4. AzureLib

Wynik WebSearch (2026-09-21; https://modrinth.com/mod/azurelib, https://github.com/AzureDoom/AzureLib): AzureLib to **fork GeckoLib 4.x** ("a branch derived from Geckolib 4.x... more focus on compatibility"); ten sam format plików (.geo.json/.animation.json), zbliżone API (klasy `GeoEntity` -> `GeoEntity` w pakiecie `mod.azure.azurelib`), 30+ funkcji easing, równoległe animacje, keyframe'y dźwięków/cząsteczek. Wersje: Fabric 1.16.5-1.20.1+, NeoForge 1.21.1+; wsparcie dla 26.x - nie potwierdzono (9minecraft wspomina "26.3", niezweryfikowane). Relacje między projektami są napięte ("no help will be given to Geckolib"). Wniosek: dla nowego moda na 26.x GeckoLib 5 jest bezpieczniejszy (aktywny rozwój, tabela wsparcia, ten sam autor co SmartBrainLib); AzureLib ma sens tylko, gdy celujemy w 1.20.1 i chcemy uniknąć znanych konfliktów GeckoLib 4 z innymi modami.

### 2.5. Eksport z Blockbench: "Modded Entity (Java)" vs "GeckoLib Animated Model"

- Blockbench ma wbudowane formaty: **Java Block/Item**, **Modded Entity** (eksport do klasy Java `ModelPart`/`CubeListBuilder` z wyborem mapowań Mojang/Yarn/MCP - to model "ręczny" w kodzie; animacje eksportuje osobno jako `AnimationDefinition` Java - tylko dla Mojang mappings w 1.19.3+), **Bedrock Entity**, a przez wtyczkę **GeckoLib Animated Model** (wtyczka GeckoLib w Blockbench: `File -> New Project -> GeckoLib Animated Model`, właściwości: Model Type, Object ID, Mod ID, Default UV Mode, Default Texture Size; eksport: `File -> Export -> Export GeckoLib Model / Export GeckoLib Animations / Export GeckoLib Display Settings`; konwersja istniejącego modelu: `File -> Convert Project -> GeckoLib Animated Model`). Źródło: wiki GeckoLib `making-models/blockbench-plugin-usage.mdx`, `exporting-the-files.mdx`.
- Ostrzeżenie Fabric docs (1.3): przy eksporcie "Modded Entity" trzeba wybrać mapowania zgodne ze środowiskiem (Mojang vs Yarn) - niezgodność = błędy kompilacji.

### 2.6. Porównanie: model ręczny w kodzie vs GeckoLib - decyzja dla 50-80 gatunków

| Kryterium | Ręczny (`EntityModel` + `setupAnim` w Javie, ew. `AnimationDefinition`) | GeckoLib 5 (JSON + kontrolery) |
|---|---|---|
| Nakład na gatunek | klasa modelu 150-400 linii + animacje w kodzie (sin/cos lub keyframe'y Java z Blockbench); każda zmiana = rekompilacja | 3 pliki JSON generowane/eksportowane; animacje edytowalne w Blockbench bez kodu; kontrolery 10-30 linii |
| Zależności | brak (0 dodatkowych modów) | GeckoLib (bardzo powszechny: Naturalist, Eager Beavers, setki modów; użytkownicy zwykle już go mają) |
| Migracja wersji (1.21.1 -> 1.21.2+ -> 26.x) | kosztowna: przepisanie na `EntityRenderState` dla każdego z 50+ modeli | GeckoLib ukrywa większość (własne `GeoRenderState`/DataTickets); zmiany w kilku klasach bazowych |
| Wydajność renderowania | najwyższa (bezpośrednie `ModelPart.render`) | narzut interpolacji keyframe'ów i Molang; przy 100+ mobach w kadrze zauważalny, mitygowalny LOD |
| Generowanie programowe | możliwe (generator kodu Java), ale trudniejsze do iteracji | **naturalne**: geo.json/animation.json to dane |
| Efekty | warstwy renderu ręcznie (`FeatureRenderer`) | `GeoRenderLayer` (glowmask/emisja, nakładki: poroże, futro zimowe jako osobne warstwy/tekstury), keyframe'y dźwięku (`setSoundKeyframeHandler`) i cząsteczek, `DefaultAnimations` (gotowe kontrolery: `genericWalkIdleController`, nazwy `move.walk`, `misc.idle`, `attack.bite`...) |
| Przykłady w modach zwierzęcych | Alex's Mobs, Better Animals Plus (Forge, własne modele + Citadel) | Naturalist, Eager Beavers, większość nowych modów Fabric |

**Rekomendacja:** GeckoLib 5 (dla 26.x) / GeckoLib 4 (dla 1.20.1/1.21.1) + pipeline generujący `.geo.json`/`.animation.json` z parametrycznych opisów gatunków (Python) i doszlifowanie w Blockbench. Nazewnictwo animacji wg konwencji `DefaultAnimations` (`move.walk`, `move.run`, `move.swim`, `move.fly`, `misc.idle`, `misc.sleep`, `misc.eat`, `attack.bite`), żeby jeden zestaw kontrolerów bazowych obsłużył wszystkie gatunki.

## 3. Tekstury: rozmiary, UV (box vs per-face), generowanie programowe (PIL), styl w modach (Naturalist, Alex's Mobs), licencje

### 3.1. Rozmiary i konwencje

| Rozmiar | Typowe użycie (wanilia / mody) | Źródło |
|---|---|---|
| 64x32 | krowa, świnia, owca, kurczak, kaczka (Naturalist duck), tutorial Fabric (`LayerDefinition.create(..., 64, 32)`) | docs.fabricmc.net, pomiar PNG |
| 64x64 | gracz, wilk (1.20.5+), koza, lis, wiele średnich mobów; bóbr Eager Beavers (`texture_width: 64, texture_height: 64`); roadrunner Alex's Mobs | geo.json bobra, pomiar PNG |
| 128x128 | duże ssaki w modach: niedźwiedź i jeleń Naturalist, grizzly Alex's Mobs; wanilia: sniffer 192x192?, koń 64x64, wielbłąd 128x128 (z pamięci) | pomiar PNG |
| 32x32 | małe ptaki (bluejay Naturalist, crow Alex's Mobs) | pomiar PNG |
| 16x16 / 32x16 | owady, ryby drobne (motyl Naturalist? - nie mierzono) | - |

Gęstość: 1 texel = 1/16 bloku (1 px modelu) - trzymanie tej gęstości daje spójność z wanilią; mody realistyczne nie stosują 2x-4x tekstur dla zwierząt (wyjątkowo HD dla bardzo dużych, np. 256x256 słoń w Alex's Mobs - nie zweryfikowano). Rozmiar tekstury deklarowany w `description.texture_width/height` (geo.json) lub `LayerDefinition.create(mesh, w, h)`; UV w px tekstury; tekstura PNG może być większa niż zadeklarowana (skalowanie), ale zwykle równa.

### 3.2. Box UV vs per-face UV (schemat Bedrock geometry 1.12.0, Microsoft Learn, https://learn.microsoft.com/en-us/minecraft/creator/reference/content/schemasreference/schemas/minecraftschema_geometry_1.12.0)

- Box UV: `"uv": [u, v]` - "Specifies the upper-left corner on the texture for the start of the texture mapping for this box." Rozwinięcie sześcianu o rozmiarze (sx, sy, sz) (standard Minecraft `ModelPart.Cuboid`, ten sam w Javie i Bedrock; weryfikacja na skórce gracza: głowa 8x8x8 przy uv (0,0) ma "górę" w (8,0)-(16,8) i "spód" w (16,0)-(24,8)):
  - wiersz górny (y od v do v+sz): `up` (góra) na (u+sz, v), rozmiar sx×sz; `down` (spód) na (u+sz+sx, v), rozmiar sx×sz;
  - wiersz dolny (y od v+sz do v+sz+sy): bok (u, v+sz) sz×sy, `north` (przód) (u+sz, v+sz) sx×sy, drugi bok (u+sz+sx, v+sz) sz×sy, `south` (tył) (u+2sz+sx, v+sz) sx×sy; który bok jest `east`, a który `west`, zależy od `mirror` (mirror zamienia je miejscami).
  - Całkowity obszar: szerokość 2·(sx+sz), wysokość sy+sz. Np. korpus bobra 12x14x9 -> 42x23 px. `mirror: true` "Mirrors the UV's of the unrotated cubes along the x axis, also causes the east/west faces to get flipped" (symetryczne kończyny z jednej tekstury). `inflate` - "Grow this box by this additive amount in all directions" (futro/sierść jako drugi, "napompowany" box z półprzezroczystą teksturą - jak wełna owcy, "jacket" gracza).
- Per-face UV: `"uv": {"north": {"uv": [u,v], "uv_size": [w,h]}, "south": ..., "east": ..., "west": ..., "up": ..., "down": ...}` - "Omitting a face will cause that face to not get drawn" (pominięcie ścian wewnętrznych = mniej overdraw; płaskie elementy 0-grubości jak uszy/ogon bobra `size: [2, 1, 0]`); `uv_size` może być ujemne (odbicie). Blockbench: "Default UV Mode" w ustawieniach projektu GeckoLib. Java `CubeListBuilder` obsługuje tylko box UV (per-face wymaga własnego `ModelPart`/GeckoLib), co jest kolejnym argumentem za GeckoLib przy per-face.
- Rozmiary niecałkowite i `inflate` są dozwolone (bóbr: `origin: [-6, -0.12176, -4.01547]`), ale generator powinien zaokrąglać rozmiary sześcianów do całych px, aby UV nie "pływało".

### 3.3. Generowanie tekstur programowo (Python/PIL) - możliwości i ograniczenia

Możliwe (z doświadczenia i z definicji formatu; brak zewnętrznego źródła - do zweryfikowania prototypem):
1. Generator z opisu gatunku: lista kości/sześcianów (jak w geo.json) -> automatyczne **rozmieszczenie UV** (pakowanie prostokątów 2(sx+sz) × (sy+sz) na siatce 64/128 px, algorytm "shelf"/"skyline" z mirror dla parzystych kończyn) -> zapis geo.json z `uv` i tekstury PNG (RGBA) z wypełnieniem każdej ściany.
2. Wypełnienie ścian: kolor bazowy gatunku per część ciała (grzbiet ciemniejszy, brzuch jaśniejszy - "countershading"), szum futra (losowe piksele ±10-15% jasności, ziarno 1-2 px), wzory parametryczne: cętki (jeleń młode, ryś - elipsy w losowym rozkładzie Poissona), pręgi (dzik warchlak - podłużne paski wzdłuż osi Z ciała, borsuk - czarno-biała maska głowy: pasy wzdłuż X), "lustro" jelenia (jasna plama na zadzie), maska szopa, biały ogon/kuper sarny, plamy krowy... 
3. Warianty sezonowe: ten sam generator z paletą "zima" (jeleń: szaro-brunatny, lato: rudy; lis: gęstsze/jaśniejsze futro; zając bielak nie występuje w PL - zając szarak bez zmian; gronostaj: biały zimą - `Mustela erminea` faktycznie występuje w PL i bieleje) -> osobne pliki `<gatunek>_winter.png`, wybór w rendererze przez DataTicket (GeckoLib 5) wg `SubSeason`.
4. Młode: osobna tekstura (jasniejsza, cętki u jelenia/dzika) na tym samym lub uproszczonym modelu; skalowanie `isBaby()` -> `getScale()`/`ScalingRenderState` (wanilia od 1.20.5 ma atrybut `GENERIC_SCALE`/`Attributes.SCALE` - realny sposób na wielkość młodych i dymorfizm).
Ograniczenia: (a) automatyczne tekstury wyglądają "płasko"/syntetycznie - brak ręcznego cieniowania krawędzi, oczu, nozdrzy; rozwiązanie: generator produkuje bazę (kolory, wzory, UV), a artysta dorysowuje detale w Blockbench/Aseprite; (b) box UV marnuje miejsce (ściany `up/down` długich kończyn) - 128x128 dla dużych ssaków wystarcza (bóbr z 10 kośćmi mieści się w 64x64); (c) PIL nie wie nic o 3D - "grzbiet" trzeba mapować na ścianę `up` korpusu, "brzuch" na `down`, boki na `east/west` - generator musi znać semantykę kości (nazewnictwo: `body`, `head`, `leg_fl`, `leg_fr`, `leg_bl`, `leg_br`, `tail`, `ear_l`, `ear_r`, `antler_l/r`, `snout`); (d) licencje: tekstury generowane własnym kodem są w 100% nasze - **nie wolno** próbkować z Naturalist (ARR) ani Alex's Mobs (brak licencji = ARR); wanilijne tekstury Mojanga też nie mogą być redystrybuowane w modzie (EULA).

### 3.4. Styl w modach (obserwacje z pomiarów i kodu)

- Naturalist: 128x128 dla dużych ssaków, 64x32 kaczka, 32x32 ptaki; osobny model+tekstura dla młodych (`*_baby.geo.json`); glowmask (`*_glowmask.png`) dla oczu w nocy (`GeoRenderLayer` emisyjna); warianty jako osobne PNG w podfolderze gatunku (`deer/white_deer.png`, `snake/{cave,coral,green,rattle}_snake.png`).
- Alex's Mobs: płaskie pliki `textures/entity/<mob>[_variant].png` (np. `anaconda_yellow_shedding.png` - kombinacja wariantu i stanu); 128x128 grizzly, 64x64 roadrunner, 32x32 crow.
- Wniosek: dla 50-80 gatunków przyjąć **3 klasy rozmiaru**: 128x128 (łoś, jeleń, niedźwiedź, żubr, dzik, wilk, ryś, tur? nie), 64x64 (sarna, lis, borsuk, bóbr, wydra, zając, kuna, bocian, żuraw, orzeł), 32x32 (ptaki śpiewające, wiewiórka, jeż, płazy, ryby drobne, owady 16x16).

## 4. AI: Goal/GoalSelector vs Brain/Behavior; nawigacja (ląd, woda, powietrze, wspinanie, nurkowanie)

### 4.1. System celów (Goal / GoalSelector) - fakty z minecraft.wiki "Mob AI" (https://minecraft.wiki/w/Mob_AI, strona oznaczona "work in progress")

- "Mobs attempt to perform the lowest priority goal they can, and may switch goals if there is an opportunity to pursue a lower priority goal." (niższa liczba = wyższy priorytet). GoalSelector sprawdza `canStart()` co **2 ticki** (domyślnie; `Goal.shouldRunEveryTick()` pozwala tickować co tick).
- Flagi kontroli (`Goal.Control` Yarn / `Goal.Flag` Mojang): `MOVE`, `LOOK`, `JUMP`, `TARGET` - dwa goale o wspólnej fladze nie działają jednocześnie; wyższy priorytet wywłaszcza niższy.
- `goalSelector` (zachowania) vs `targetSelector` (wybór celu ataku: `RevengeGoal`/`HurtByTargetGoal`, `ActiveTargetGoal`/`NearestAttackableTargetGoal`).
- Przykład zombie: 1 Revenge; 2 ActiveTarget(Player), ZombieAttack; 3 ActiveTarget(IronGolem/Villager); 7 WanderAroundFar; 8 LookAround.
- Kary ścieżkowe (path node penalties): -1 = blokada (lawa, zamknięte drzwi, płot), 0 = normalne (powietrze, otwarte drzwi), 8 = unikać (woda, miód), 16 = stronić (ogień, kontakt z lawą). Zmienia się je per-mob przez `setPathfindingPenalty(PathNodeType.WATER, 0)` (Yarn) / `setPathfindingMalus(PathType.WATER, 0)` (Mojang) - kluczowe dla bobra/wydry/łosia (woda nie może być "unikana").
- Opisy: Wander - losowy cel co tick (preferuje bloki z większą liczbą bloków pod spodem); Panic/Flee - po obrażeniach, mob szuka `hurt_by_entity` i ucieka; Tempt - "Searches for the player that is not on Spectator mode, is in range [tempt_range], holding a tempting item" (w 1.21.2+ zasięg to atrybut `TEMPT_RANGE`); Breed - wymaga drugiego rodzica (`breed_target` w brain); Follow Parent - młode śledzi dorosłego tego samego typu (`nearest_visible_adult`).

### 4.2. System mózgu (Brain / Behavior) - fakty z minecraft.wiki "Mob AI"

- Brain przechowuje: pamięć (`MemoryModuleType` -> slot z wartością i opcjonalnym czasem wygaśnięcia), listę zadań (Behaviors/Tasks) per aktywność, listę sensorów. Sensory działają domyślnie co **20 ticków** (15+ typów: PlayerSensor, NearestLivingEntitySensor, HurtBySensor, ...).
- Typy pamięci (wybór): `attack_target`, `walk_target`, `look_target`, `nearest_players`, `hurt_by_entity`, `mobs`, `visible_mobs`, `breed_target`, `nearest_visible_adult`, `home`, `job_site`, `meeting_point`, `last_slept`, `last_woken`, `is_tempted`, `temptation_cooldown_ticks`, `is_in_water`, `is_pregnant`, `ram_cooldown_ticks`, `long_jump_cooldown`, `sniffer_*`, `gaze_cooldown_ticks` (pełna lista: minecraft.wiki / klasa `MemoryModuleType`).
- Aktywności (24 zdefiniowane): `core`, `idle`, `work`, `play`, `rest`, `meet`, `panic`, `raid`, `pre_raid`, `hide`, `fight`, `celebrate`, `admire_item`, `avoid`, `ride`, `play_dead`, `long_jump`, `ram`, `tongue`, `swim`, `lay_spawn_eggs`, `sniff`, `investigate`, `roar`, `emerge`, `dig` (lista z pamięci klasy `Activity`; wiki podaje liczbę 24). Aktywność ma warunki wejścia jako lista (MemoryModuleType, MemoryModuleState REGISTERED/VALUE_PRESENT/VALUE_ABSENT).
- **Harmonogram (`Schedule`)**: wieśniak - `work` (dzień), `home`/`rest` (noc), `meet` (południe), `idle`; harmonogram to mapa tick-dnia -> aktywność (`ScheduleBuilder.withActivity(tickOfDay, activity)`), wbudowane: `EMPTY`, `SIMPLE`, `VILLAGER_BABY`, `VILLAGER_DEFAULT`. **Nadaje się wprost do dobowego rytmu zwierząt (aktywność o świcie/zmierzchu - jeleń, dzik; nocna - borsuk, kuna, sowy).**
- Moby używające Brain (wg wiki): wieśniak, piglin, piglin brute, hoglin, zoglin?, aksolotl, żaba, kijanka, koza, allay, wielbłąd, sniffer, warden, lis? (wiki wymienia lisa - do potwierdzenia: lis w Javie używa Goal + memory dla `EatGrassGoal`? nie - lis ma własne goale; wiki może odnosić się do Bedrock), lama?, breeze, creaking, pancernik (armadillo), nautilus (nowy mob 26.x? - do potwierdzenia) "plus ~30 innych". Wilk i lis w Java Edition używają Goal (klasy `WolfEntity`, `FoxEntity` z `initGoals`) - z pamięci, do potwierdzenia.

### 4.3. Nawigacja i kontrolery ruchu (nazwy klas z pamięci, Yarn 1.21.1 -> Mojang; weryfikacja w Linkie - patrz 4.4)

| Zastosowanie | Yarn | Mojang | Uwagi |
|---|---|---|---|
| ląd (domyślne) | `MobNavigation` | `GroundPathNavigation` | `setCanSwim(true)` = pływanie po powierzchni; `setCanOpenDoors`, `setCanEnterOpenDoors` |
| woda (ryby, kałamarnice) | `SwimNavigation` + `AquaticMoveControl` | `WaterBoundPathNavigation` + `SmoothSwimmingMoveControl` | ryby: `FishEntity`/`AbstractFish`, `SwimAroundGoal`/`RandomSwimmingGoal` |
| ziemno-wodne (aksolotl, żaba, żółw) | `AmphibiousSwimNavigation` | `AmphibiousPathNavigation` | aksolotl: `AxolotlEntity` z Brain, `AxolotlMoveControl`; żółw ma `TurtleSwimNavigation`; **bóbr/wydra: to jest właściwa baza** + `canBreatheInWater`/własny licznik powietrza dla nurkowania |
| lot (papuga, pszczoła, allay, phantom) | `BirdNavigation` + `FlightMoveControl` | `FlyingPathNavigation` + `FlyingMoveControl` | `FlightMoveControl(mob, maxPitchChange, noGravity)`; bocian/żuraw/ptaki drapieżne: `BirdNavigation`, `setCanPathThroughDoors`, `setCanEnterOpenDoors`; lądowanie: przełączanie między `MobNavigation` a `BirdNavigation` (jak pszczoła/papuga); duże ptaki wymagają własnego `MoveControl` (szybowanie) |
| wspinanie (pająk) | `SpiderNavigation` | `WallClimberNavigation` | `isClimbing()` = kolizja z blokiem poziomo (`horizontalCollision`) - działa na dowolnej ścianie; dla kuny/wiewiórki/rysia: ograniczyć do kłód/liści (tag) w nadpisanym `isClimbing()` |
| nurkowanie (bóbr, wydra, kormoran) | brak gotowego; własny `MoveControl` z celem 3D w wodzie + `AmphibiousSwimNavigation` | j.w. | wanilia: delfin (`DolphinEntity`, `SwimNavigation`, `canBreatheInWater=false` z licznikiem `getMoistness`) to najbliższy wzorzec "ssaka oddychającego powietrzem pod wodą" |

### 4.4. Który system dla złożonych zachowań?

Wnioski (uzasadnienie w sekcjach 4.1-4.2, 6, 8):
- Goal: prosty, tani (co 2 ticki), ogromna liczba gotowych goali w wanilii (`WanderAroundFarGoal`, `LookAtEntityGoal`, `MeleeAttackGoal`, `TemptGoal`, `FollowParentGoal`, `AnimalMateGoal`, `EscapeDangerGoal`, `FleeEntityGoal`, `ActiveTargetGoal`, `EatGrassGoal`, `SwimGoal`, `MoveToTargetPosGoal`, `AvoidSunlightGoal`, `SleepGoal` (u lisa jako wewnętrzna klasa), `FlyGoal`, `FollowMobGoal`), łatwe dziedziczenie; wszystkie przeanalizowane mody zwierzęce (Eager Beavers, Naturalist?, Better Animals Plus) używają Goal. Wady: brak wbudowanej pamięci/sensorów/harmonogramu - stan trzeba trzymać ręcznie w polach encji (NBT), stada = ręczne listy (patrz 6).
- Brain: wbudowane `Schedule` (dobowy), pamięć z wygasaniem, sensory, aktywności z warunkami, `PrioritizedBehavior`; społeczność uważa implementację Mojanga za "cryptic... overly complex" (opis SmartBrainLib), ale biblioteka **SmartBrainLib** (Tslat, ten sam autor co GeckoLib; MPL-2.0; ostatni push 2026-09-20 - aktywna) upraszcza ją (automatyczne MemoryModuleType, `SmartBrainOwner`, `ExtendedSensor`, `ExtendedBehaviour`, `FirstApplicableBehaviour`, `OneRandomBehaviour`, `AllApplicableBehaviours`, `SetRandomWalkTarget`, `LookAtTarget`, `Idle`, `FollowParent`, `BreedWithPartner`, `AvoidEntity`, `Panic`, `FleeTarget`, `AnimalMate`...). Źródła: https://github.com/Tslat/SmartBrainLib , https://modrinth.com/mod/smartbrainlib .
- Rekomendacja: dla 50-80 gatunków z rytmem dobowym/sezonowym, hierarchią stad i "stanami" (hibernacja, gody, migracja) **Brain przez SmartBrainLib** jako rdzeń + dziedziczone klasy bazowe (np. `PolishAnimal` -> `HerdAnimal`, `BurrowingAnimal`, `AmphibiousMammal`, `Bird`), a Goal tylko tam, gdzie wystarczy prosty mob (ryby, płazy drobne). Alternatywa niskiego ryzyka: Goal + własna mała "maszyna stanów" (enum `DailyPhase`, `Season`), jak w większości modów - mniej elegancka, ale lepiej przetestowana. Szczegóły kosztów w sekcji 8.

### 4.5. Weryfikacja nazw Yarn <-> Mojang

Próba automatycznej weryfikacji przez API Linkie (`https://linkieapi.shedaniel.me/api/search?namespace=yarn&query=...&version=1.21.1&translate=mojang`) **nie powiodła się** (puste odpowiedzi - endpoint lub parametry inne niż zakładano). Nazwy Mojang potwierdzone w kodzie źródłowym Naturalist 1.21.1 (mapowania Mojang): `FlyingMoveControl(this, 10, false)`, `FlyingPathNavigation`, `PathNavigation`, `WaterAvoidingRandomFlyingGoal`, `FollowAdultGoal`?? (to klasa Naturalist), `FloatGoal`, `TemptGoal`, `SitWhenOrderedToGoal`, `FollowOwnerGoal`, `LookAtPlayerGoal`, `AvoidEntityGoal`, `BreedGoal`, `MeleeAttackGoal`, `RandomStrollGoal`, `RandomLookAroundGoal`, `NearestAttackableTargetGoal`, `ResetUniversalAngerTargetGoal`, `MoveToBlockGoal`, `PathfinderMob`, `Animal`, `NeutralMob`, `ShoulderRidingEntity`, `FlyingAnimal`, `EntityDataAccessor`/`SynchedEntityData`/`EntityDataSerializers`, `Goal.Flag`. Nazwy Yarn potwierdzone w Eager Beavers 1.20.2: `TameableEntity`, `AnimalEntity`, `Goal.Control`, `EscapeDangerGoal`, `AnimalMateGoal`, `TemptGoal`, `FollowParentGoal`, `WanderAroundGoal`, `LookAtEntityGoal`, `LookAroundGoal`, `FollowOwnerGoal`, `MoveToTargetPosGoal`, `SpawnRestriction`, `SpawnGroup`, `Heightmap.Type`, `BiomeSelectors`, `BiomeKeys`. Pary Yarn->Mojang dla goali (z pamięci, spójne z powyższym): `EscapeDangerGoal`->`PanicGoal`, `AnimalMateGoal`->`BreedGoal`, `WanderAroundGoal`->`RandomStrollGoal`, `WanderAroundFarGoal`->`WaterAvoidingRandomStrollGoal`, `LookAtEntityGoal`->`LookAtPlayerGoal`, `LookAroundGoal`->`RandomLookAroundGoal`, `MoveToTargetPosGoal`->`MoveToBlockGoal`, `SwimGoal`->`FloatGoal`, `FleeEntityGoal`->`AvoidEntityGoal`, `ActiveTargetGoal`->`NearestAttackableTargetGoal`, `RevengeGoal`->`HurtByTargetGoal`, `EatGrassGoal`->`EatBlockGoal`, `FlyGoal`->`WaterAvoidingRandomFlyingGoal`, `MobNavigation`->`GroundPathNavigation`, `BirdNavigation`->`FlyingPathNavigation`, `FlightMoveControl`->`FlyingMoveControl`, `SpiderNavigation`->`WallClimberNavigation`, `AmphibiousSwimNavigation`->`AmphibiousPathNavigation`, `SwimNavigation`->`WaterBoundPathNavigation`, `AquaticMoveControl`->`SmoothSwimmingMoveControl`, `PathAwareEntity`->`PathfinderMob`, `MobEntity`->`Mob`, `AnimalEntity`->`Animal`, `DataTracker`->`SynchedEntityData`, `TrackedData`->`EntityDataAccessor`. Przed implementacją potwierdzić w IDE (Loom `genSources`).

## 5. Spawn: BiomeModifications, SpawnGroup, SpawnRestriction/SpawnPlacements, limity, despawn/persistence, spawn przy generacji chunka, ryby

### 5.1. Kategorie spawnu (SpawnGroup / MobCategory) i limity (mob cap) - Java Edition

Źródło: minecraft.wiki, "Mob spawning" (https://minecraft.wiki/w/Mob_spawning), pobrane 2026-09-21.

| Kategoria (Yarn `SpawnGroup` / Mojang `MobCategory`) | Cap (na gracza, przy pełnym obszarze 17x17 chunków) | Charakter | Uwagi |
|---|---|---|---|
| `MONSTER` | 70 | wrogie | cykl co 1 tick |
| `CREATURE` | 10 | pasywne/neutralne (zwierzęta lądowe) | cykl spawnu co 400 ticków (20 s); mobom z tej grupy wanilia nadaje "friendly" i nie despawnują naturalnie (krowy, świnie itd. są persistent w praktyce - patrz 5.4) |
| `AMBIENT` | 15 | nietoperze | |
| `AXOLOTLS` | 5 | aksolotle | |
| `UNDERGROUND_WATER_CREATURE` | 5 | glow squid | |
| `WATER_CREATURE` | 5 | kałamarnice, delfiny | |
| `WATER_AMBIENT` | 20 | ryby (dorsz, łosoś, rozdymka, tropikalne) | ryby despawnują |
| `MISC` | -1 (brak limitu) | nie spawnuje się naturalnie | do encji specjalnych (pojazdy, pociski) |

Wzór na globalny limit: `globalCap = mobCap × chunks ÷ 289`, gdzie `chunks` = liczba chunków objętych obszarem 17×17 wokół dowolnego gracza (289 = 17×17). Przy jednym graczu i pełnym obszarze wynosi to dokładnie mobCap. **Wniosek: wanilia pozwala na zaledwie ~10 zwierząt lądowych CREATURE w promieniu ~8 chunków od gracza** - to główny problem dla moda z 50-80 gatunkami (rozwiązania w sekcji 5.6).

### 5.2. Spawn w stadach (pack spawning)

- Rozmiar paczki (pack size): 8 dla wilków, dorszy i ryb tropikalnych; 6 dla koni i osłów; 4 dla pozostałych mobów (domyślne `minGroupSize`/`maxGroupSize` w `SpawnEntry`).
- Rozkład: ~85% spawnów w promieniu 5 bloków od środka paczki, ~99% w promieniu 10 bloków.
- Warunki dla zwierząt pasywnych: poziom światła na bloku spawnu >= 9; blok pod spodem musi być "powiązany z mobem" (dla większości zwierząt `grass_block`; w kodzie tag `minecraft:animals_spawnable_on`).

### 5.3. Spawn przy generacji chunka

Z minecraft.wiki: przy generowaniu chunka wybierany jest wpis z ważonej listy `creature` spawnów biomu; na jego podstawie losowany jest rozmiar paczki między min a max. Prawdopodobieństwo spawnu na chunk (`creature_spawn_probability` w JSON biomu, Yarn `SpawnSettings.getCreatureSpawnProbability()`) wynosi 0.1 dla większości biomów (0.0 dla oceanów/pustyń itp. - do potwierdzenia dla konkretnych biomów). **Ten mechanizm ignoruje mob cap** - zwierzęta wygenerowane wraz z chunkiem nie liczą się przy generacji do limitu i (jako CREATURE) nie despawnują, dlatego w świecie wanilii jest ich więcej niż 10 na gracza.

### 5.4. Despawn i persistence (z minecraft.wiki)

- Wrogie moby: natychmiastowy despawn dalej niż 128 bloków od najbliższego gracza; losowy despawn (1/800 na tick) w odległości > 32 bloków po 30 s bez gracza w zasięgu.
- Persistent (nie despawnują): nazwane (name tag), oswojone, przewożące przedmiot (podniesiony przedmiot), w łódce/na smyczy, po interakcji z graczem (np. nakarmione - w kodzie `setPersistent()` / `PersistentProjectileEntity` niezwiązane).
- Zwierzęta z grupy CREATURE w Javie w praktyce **nie despawnują** (klasa `AnimalEntity` nadpisuje `canImmediatelyDespawn` na false w większości przypadków; ryby, kałamarnice, nietoperze despawnują). Konsekwencja: zwierzęta z generacji chunków kumulują się (tzw. "pierwotna populacja") i wypełniają cap 10, co blokuje naturalne dospawnowanie nowych CREATURE po pobliskich wybiciu - wanilia "leczy" to tylko rozmnażaniem.

### 5.5. Spawn cost (soul sand valley / warped forest)

Wzór: mob może się pojawić, jeśli `sum(charge istniejącego moba ÷ odległość) × charge nowego moba < energy_budget nowego moba`. Konfigurowane w JSON biomu: `spawn_costs: { "minecraft:skeleton": { "energy_budget": 0.7, "charge": 0.15 } }`. Umożliwia to "rozrzedzenie" gatunku bez obniżania capu - użyteczne dla rzadkich drapieżników (ryś, wilk, niedźwiedź) w naszym modzie, ale działa tylko dla spawnu naturalnego (nie przy generacji chunka - do potwierdzenia).

### 5.6. API Fabric: BiomeModifications.addSpawn i SpawnRestriction

Sygnatura (javadoc Fabric API 0.110.0+1.21.1, https://maven.fabricmc.net/docs/fabric-api-0.110.0+1.21.1/net/fabricmc/fabric/api/biome/v1/BiomeModifications.html):

```java
static void addSpawn(Predicate<BiomeSelectionContext> biomeSelector, SpawnGroup spawnGroup, EntityType<?> entityType, int weight, int minGroupSize, int maxGroupSize)
```
Selektory: `BiomeSelectors.includeByKey(BiomeKeys.RIVER)`, `BiomeSelectors.tag(TagKey)`, `BiomeSelectors.foundInOverworld()`, `BiomeSelectors.categories(...)` (stare). Ponieważ nasz mod generuje **własne biomy** (raport 01/02), spawn można też zapisać bezpośrednio w JSON biomu (`data/<modid>/worldgen/biome/*.json` -> `"spawners": {"creature": [{"type": "modid:jelen", "weight": 12, "minCount": 2, "maxCount": 6}]}` oraz `"spawn_costs"`), co jest czytelniejsze i data-driven; `BiomeModifications` zostaje do wstrzykiwania w biomy wanilii/innych modów.

Restrykcje spawnu (Yarn 1.20.1/1.21.1): `SpawnRestriction.register(type, SpawnRestriction.Location.ON_GROUND | IN_WATER | IN_LAVA | NO_RESTRICTIONS, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AnimalEntity::canMobSpawn)` -> w 1.21.2+ `SpawnLocationTypes.ON_GROUND` (Mojang: `SpawnPlacements.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules)`; Fabric 1.21.2+ udostępnia też `EntityType.Builder#spawnRestriction(...)` przez `FabricEntityTypeBuilder`/injected interface - do potwierdzenia w docs). Własny predykat może sprawdzać: blok pod spodem (tag `modid:jelen_spawnable_on`: trawa, ściółka leśna, mech), odległość od wody (bóbr, wydra, bocian - jak `isNearWater` w Eager Beavers), sezon (Serene Seasons `SeasonHelper.getSeasonState(level).getSeason()`), porę dnia, wysokość Y (kozica, świstak - tylko > 1000 m n.p.m. w skali świata), czy chunk jest "leśny" (gęstość liści w promieniu).

### 5.7. Jak zwiększyć liczbę zwierząt bez przeciążenia

Wyniki WebSearch (2026-09-21): istnieją mody zmieniające capy przez modyfikację enumu `SpawnGroup`: **Proper Mobcap Modifier** ("just modifies SpawnGroup enum getters to get the values from config"; https://modrinth.com/mod/proper-mobcap-modifier), **Custom Spawns [Fabric]** (capy, persistence, częstotliwość spawnu pasywnych; https://www.curseforge.com/minecraft/mc-mods/custom-spawns), **Spawncap Control Utility**, **Fabric per player spawns** (Paper-like per-player mobcap). Konstruktor enumu (javadoc Yarn): `SpawnGroup(String name, int spawnCap, boolean peaceful, boolean rare, int immediateDespawnRange)` - `rare` = cykl co 400 ticków (CREATURE), `immediateDespawnRange` = 128 (32 dla AXOLOTLS/WATER_AMBIENT/UNDERGROUND_WATER_CREATURE).

Opcje dla naszego moda (od najmniej do najbardziej inwazyjnej):
1. **Spawn przy generacji chunka** (`creature` w JSON biomu, `creature_spawn_probability` podniesione do np. 0.2-0.4 w biomach leśnych) - populacja "startowa" nie podlega capowi; wada: brak uzupełniania po wybiciu.
2. **Własny `SpawnGroup`** przez mixin rozszerzający enum (Fabric: `@Mixin(SpawnGroup.class)` + `@Invoker("<init>")`/`ArrayList` na `$VALUES`; wzorzec z modów Proper Mobcap Modifier) - np. `PZL_LARGE_MAMMAL` (cap 8), `PZL_SMALL_MAMMAL` (cap 20), `PZL_BIRD` (cap 24), `PZL_AMPHIBIAN` (cap 12), `PZL_FISH` (cap 20) - każda grupa ma niezależny limit, a wanilijne CREATURE zostaje bez zmian (kompatybilność z innymi modami). Ryzyko: kruche przy zmianie wersji; alternatywa - `SpawnGroup` w 26.x może już nie być enumem (do potwierdzenia).
3. **Własny spawner tickowany** (jak `PhantomSpawner`, `PatrolSpawner`, `CatSpawner`, `WanderingTraderManager` - interfejs `SpecialSpawner` Yarn / `CustomSpawner` Mojang, rejestrowany w `ServerWorld` przez mixin lub Fabric `ServerTickEvents`): co N ticków, dla losowego gracza wybiera pozycję w promieniu 24-128 bloków, sprawdza własny limit gatunku w promieniu (np. `world.getEntitiesByType(type, box, pred).size() < maxPerArea`) i warunki (biom, sezon, pora dnia, odległość od wody) - **pełna kontrola**, nie dotyka mobcapu wanilii. To rozwiązanie użyto m.in. w wanilii dla kotów w wioskach i fantomów; polecane jako główne dla rzadkich gatunków (wilk, ryś, niedźwiedź, łoś, bocian).
4. Podniesienie `creature` cap konfigiem (jak Proper Mobcap Modifier) - najprostsze, ale ogólnoświatowe i kolizyjne z innymi modami.

Despawn: własne zwierzęta powinny **despawnować** poza zasięgiem (jak ryby/nietoperze), inaczej populacja rośnie bez końca przy eksploracji (chunki z generacji + nowe spawny). Wzorzec: nadpisać `canImmediatelyDespawn(double distanceSquared)` -> `!hasCustomName() && !isPersistent()` oraz `isDisallowedInPeaceful` domyślnie; wyjątki: oswojone, nazwane, z "domem" (żeremie/nora/gniazdo w pamięci) w załadowanym chunku. Zapisywanie "domów" w danych chunka/świata (`PersistentState`) pozwala odtworzyć populację (re-spawn przy domu), zamiast utrzymywać encje wiecznie - to standardowy sposób "udawania symulacji" (patrz sekcja 8).

### 5.8. Ryby i spawn w wodzie

Wanilia: `WATER_AMBIENT` (cap 20, despawn 1/800 tick powyżej 32 bloków? - u ryb `canImmediatelyDespawn` = true poza 64 bl.; do potwierdzenia), `SpawnRestriction.Location.IN_WATER`, predykat `FishEntity::canSpawn` (Yarn `canSpawn(EntityType, WorldAccess, SpawnReason, BlockPos, Random)`: blok i blok nad nim = woda, dla łososia/dorsza w oceanie dodatkowo `pos.getY() < seaLevel - 13`? nie - to dla tropikalnych; z pamięci, do potwierdzenia). Rzeki w Polsce (raport 01) powinny dostać wpisy `water_ambient` w JSON biomu rzeki: płoć, okoń, szczupak, lin, kleń; strumienie górskie: pstrąg potokowy, lipień. Uwaga: rzeki wanilii są płytkie (1-3 bl.) - większe ryby (sum, szczupak) wymagają głębszych koryt z własnej generacji.

## 6. Zachowania zaawansowane w istniejących modach (Naturalist, Alex's Mobs, Untamed Wilds, Better Animals Plus, Exotic Birds i in.): hibernacja, stada, gniazda, nory, migracje, poroże, futro zimowe, młode, bobry

### 6.1. Mody z bobrami budującymi tamy/żeremia (wynik WebSearch 2026-09-21)

| Mod | Loader | Co robi | Źródło |
|---|---|---|---|
| **Eager Beavers** (bodhiahn) | Fabric | Bobry ścinają drzewa, podnoszą kłody i niosą je do wody, aby budować tamę; struktura "Beaver Lodge" (żeremie) generowana w świecie jako miejsce, gdzie bobry mieszkają. ~5,7 tys. pobrań na CurseForge. Kod otwarty na GitHub. | https://github.com/bodhiahn/eager-beavers , https://modrinth.com/project/QNh0xawR |
| Beaver Mod (SuperRedingBros) | (Forge, do potwierdzenia) | bóbr + struktura tamy + itemy; ~2,1 tys. pobrań | https://www.curseforge.com/minecraft/mc-mods/beaver-mod |
| Beaver Dam 1.18.2 | ? | mod z PlanetMinecraft | https://www.planetminecraft.com/mod/beaver-dam-5164722/ |

Wniosek wstępny: istnieje przynajmniej jeden otwarty mod Fabric (Eager Beavers) z **aktywnym** budowaniem tamy przez encję (transport kłód do wody), a nie tylko statyczną strukturą. Analiza kodu - patrz 6.2.

### 6.2. Analiza kodu Eager Beavers (bodhiahn/eager-beavers, gałąź master, odczyt raw 2026-09-21)

Fakty z `gradle.properties`: `minecraft_version=1.20.2`, `yarn_mappings=1.20.2+build.4`, `loader_version=0.14.23`, `fabric_version=0.90.4+1.20.2`; używa GeckoLib 4 (`software.bernie.geckolib.animatable.GeoEntity`, `software.bernie.geckolib.core.animation.AnimationController`). Licencja CC0-1.0 (kod i - wg README - całość repozytorium; zasoby geo/animacje można więc legalnie wykorzystać jako punkt wyjścia, choć jakość modelu należy ocenić samodzielnie).

Struktura (13 klas Java): `BeaverMod`, `BeaverModClient`, `entity/Beaver.java` (949 linii - cała logika), `entity/client/BeaverModel.java`, `BeaverRenderer.java`, `ModEntities.java`, `world/gen/BeaverSpawn.java`, `BeaverGen.java`, itemy (`BeaverPelt`, `BeaverArmorMaterial`), `data/ModModelProvider`. Zasoby: `assets/beavermod/geo/beaver.geo.json`, `assets/beavermod/animations/animation.beaver.{idle,walk,swim,holdidle,holdwalk}.json` + zbiorczy `beaver.animation.json`.

Klasa `Beaver extends TameableEntity implements GeoEntity` (Yarn 1.20.2). Lista goali z `initGoals()` (priorytet, klasa):

```java
protected void initGoals() {
    this.goalSelector.add(0, new BeaverSwimGoal());                 // własny SwimGoal: setControls(JUMP, MOVE), navigation.setCanSwim(true)
    this.goalSelector.add(1, new EscapeDangerGoal(this, 1.0f));
    this.goalSelector.add(2, new DamGoal(0.6f, 30, 1));             // extends MoveToTargetPosGoal: szuka bloku wody z max. liczbą sąsiadów-wody + stały blok obok, tam stawia niesioną kłodę
    this.goalSelector.add(3, new MateGoal(1.0));                    // extends AnimalMateGoal
    this.goalSelector.add(3, new PickupSidewaysLogGoal(.6f));       // MoveToTargetPosGoal: podnosi leżące (poziome) kłody nie przy wodzie -> equipStack(MAINHAND, ItemStack(log))
    this.goalSelector.add(4, new BeavGoal(.6f, 16, 3));             // MoveToTargetPosGoal: "ścinanie" - celuje w 2. kłodę pnia od dołu (log poniżej, brak logu powyżej), wymaga mobGriefing
    this.goalSelector.add(1, new FollowOwnerGoal(this, 1.0, 10.0f, 2.0f, false));
    this.goalSelector.add(7, new TemptGoal(this, .5f, BREEDING_INGREDIENT, false));
    this.goalSelector.add(8, new FollowParentGoal(this, .7f));
    this.goalSelector.add(8, new PickupItemGoal());                 // skanuje ItemEntity w boxie 8x8x8 co ~10 ticków
    this.goalSelector.add(9, new WanderAroundGoal(this, .5f));
    this.goalSelector.add(10, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f));
    this.goalSelector.add(11, new LookAroundGoal(this));
    this.goalSelector.add(15, new WaterWanderGoal(1.0));           // losowy cel w wodzie, gdy navigation.isIdle()
}
```

Inne rozwiązania: `canBreatheInWater()` = true; `isBreedingItem` = tag `ItemTags.LOGS`; niesiona kłoda to `ItemStack` w `EquipmentSlot.MAINHAND` (+ `setCarriedBlock(BlockState)` do renderowania); "tama" = po prostu stawianie kłód (`getSidewaysLogState`) w blokach wody obok stałego bloku - **brak** modelu hydrologicznego (brak podnoszenia poziomu wody, brak żeremia jako budowli encji; "Beaver Lodge" to zwykła struktura z generatora). Nawigacja: standardowa `MobNavigation` z `setCanSwim(true)` - bóbr nie nurkuje, pływa po powierzchni. Animacje: 4 kontrolery GeckoLib (`Walk/Idle`, `Hat`, `Swim`, `Eat`) w jednym `AnimationController` każdy; przełączanie `state.setAndContinue(isHoldingItem ? HOLD_WALK_ANIM : WALK_ANIM)`.

Spawn (`BeaverSpawn.java`, w całości):

```java
BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.RIVER), SpawnGroup.AMBIENT, ModEntities.BEAVER, 100, 1, 5);
SpawnRestriction.register(ModEntities.BEAVER, SpawnRestriction.Location.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AnimalEntity::canMobSpawn);
```

Uwaga: autor użył `SpawnGroup.AMBIENT` (cap 15, tick co 1) zamiast `CREATURE` (cap 10, co 400 ticków) - to popularna "sztuczka" na częstsze spawnowanie, ale wtedy moby despawnują jak nietoperze, jeśli klasa nie nadpisze `canImmediatelyDespawn`/`isDisallowedInPeaceful` (Beaver dziedziczy po `TameableEntity`->`AnimalEntity`, więc nie despawnuje). Waga 100 przy min 1 - max 5 w rzece.

### 6.3. Format plików GeckoLib/Blockbench (na przykładzie beaver.geo.json i animation.beaver.walk.json)

`beaver.geo.json` (Bedrock geometry, eksport Blockbench "Bedrock Entity"/"GeckoLib Animated Model"):

```json
{ "format_version": "1.12.0",
  "minecraft:geometry": [ {
    "description": { "identifier": "geometry.beaver", "texture_width": 64, "texture_height": 64,
                     "visible_bounds_width": 4, "visible_bounds_height": 2.5, "visible_bounds_offset": [0, 0.75, 0] },
    "bones": [
      { "name": "body", "pivot": [0, 6.44686, -1.56861], "rotation": [90, 0, 0],
        "cubes": [ {"origin": [-6, -0.12176, -4.01547], "size": [12, 14, 9], "pivot": [0, -10.12176, 2.98453], "rotation": [7, 0, 0], "uv": [0, 0]} ] },
      { "name": "head", "pivot": [0, 3.52762, -6.87575],
        "cubes": [ {"origin": [-4, 1, -14], "size": [8, 6, 7], "pivot": [0,0,0], "rotation": [-2.5, 0, 0], "uv": [33, 29]}, ... ] },
      { "name": "leg1", "pivot": [-4.5, 5, 2.5], "cubes": [ {"origin": [-7, 0, 0], "size": [5, 6, 5], "uv": [0, 36]}, ... ] } ] } ] }
```

Cechy: jednostki = piksele modelu (16 px = 1 blok); `origin` = narożnik min (x,y,z), `size` w px; `uv: [u, v]` = box UV (lewy górny róg rozwinięcia sześcianu); `bones` mogą mieć `parent`; obroty w stopniach; tekstura 64x64 dla bobra (~12x14x9 px korpus). Format jest prostym JSON-em - **da się generować programowo** (Python) ze zdefiniowanych list kości/sześcianów.

`animation.beaver.walk.json` (Bedrock animation):

```json
{ "format_version": "1.8.0",
  "animations": { "animation.beaver.walk": { "loop": true, "animation_length": 1.04167,
    "bones": {
      "body": { "relative_to": {"rotation": "entity"},
                "rotation": {"0.0": {"vector": [0, 0, -1.7]}, "0.5": {"vector": [0, 0, 1]}, "1.0417": {"vector": [0, 0, -1.8]}},
                "position": {"0.0": {"vector": [0, -0.2, 0]}, "0.4583": {"vector": [0, 0.2, 0]}, "1.0417": {"vector": [0, -0.2, 0]}} },
      "leg1": { "rotation": {"0.0": {"vector": [-10, 0, 0]}, "0.5": {"vector": [35, 0, 0]}, "1.0417": {"vector": [-9.5, 0, 0]}} } } } } }
```

Cechy: klucze czasowe w sekundach jako stringi, `vector` [x,y,z] w stopniach (rotation) lub px (position) lub mnożnikach (scale); możliwe `"lerp_mode": "catmullrom"`, wyrażenia Molang w stringach (np. `"math.sin(query.anim_time*360)*10"`), `loop: true | false | "hold_on_last_frame"`. Także generowalne programowo (np. sinusoidalne chodzenie czworonogów z parametrów: amplituda, częstotliwość, faza per noga).

### 6.4. Naturalist (Starfish Studios) - struktura kodu (repozytorium przeniesione: https://github.com/crispytwig/Naturalist; odczyt API GitHub 2026-09-21)

- Gałęzie: `1.19.2`, `1.20.1-Arch` (Architectury: Fabric+Forge), `1.21.1-MultiLoader` (domyślna: `1.21.1-NeoForge`, `minecraft_version=1.21.1`, `neo_version=21.1.226`, Parchment), **`26.2-MultiLoader`, `26.3-MultiLoader`** (ostatni push 2026-09-20 - mod jest aktywnie portowany na najnowsze wersje; czy gałęzie MultiLoader zawierają Fabric - do potwierdzenia). Licencja: kod MIT, **zasoby (modele, tekstury, animacje) All Rights Reserved** - nie wolno kopiować assetów Naturalist.
- Pakiet `com.starfish_studios.naturalist.server.entity`:
  - `base/`: `NaturalistAnimal`, `NaturalistGeoEntity`, `SleepingAnimal`, `ClimbingAnimal`, `HidingAnimal`, `EggLayingAnimal`, `Catchable` - **interfejsy-cechy** (mixin-style), które klasa zwierzęcia implementuje; to wzorzec do naśladowania dla 50+ gatunków (kompozycja cech zamiast głębokiej hierarchii).
  - `ai/goal/`: `SleepGoal` (sen wg pory dnia/warunków - używany m.in. przez niedźwiedzia i lisa/węża), `AlertOthersPanicGoal` (panika przenoszona na stado - jelenie), `BabyPanicGoal`, `BigPanicGoal`, `BabyHurtByTargetGoal`, `AttackPlayerNearBabiesGoal` (niedźwiedzica broni młodych), `DistancedFollowParentGoal`, `FollowAdultGoal` (młode idzie za dowolnym dorosłym - stado), `FlyingWanderGoal` (ptaki), `HideGoal` (chowanie się - żółw/ślimak), `LayEggGoal`, `EggLayingBreedGoal`, `SearchForItemsGoal`, `CloseMeleeAttackGoal`, `SmoothFloatGoal` (pływanie po powierzchni).
  - `ai/navigation/`: `BetterGroundPathNavigation`, `BetterWallClimberNavigation` (wspinanie - wąż/jaszczurka po ścianach).
  - `mob/`: Alligator, Bass, **Bear, Bird, Boar, Butterfly, Caterpillar, Catfish, Deer, Dragonfly, Duck**, Elephant, Firefly, Giraffe, Hippo, Lion, Lizard, LizardTail, Rhino, Snail, **Snake**, Tortoise, Vulture, Zebra (26 klas; w 1.21.1 brak lisa/wiewiórki?).
- Zasoby: `assets/naturalist/geo/entity/<mob>.geo.json` + osobne `<mob>_baby.geo.json` (**młode = osobny model**, nie skalowanie), `textures/entity/<mob>/<mob>.png`, `<mob>_baby.png`, `<mob>_glowmask.png` (emisja oczu), jaja w stadiach `alligator_egg/stage_0..2.png`. Naturalist używa GeckoLib (interfejs `NaturalistGeoEntity`).

#### 6.4.1. Analiza kodu Naturalist 1.21.1 (mapowania Mojang/Parchment; odczyt raw 2026-09-21)

Modrinth (https://modrinth.com/mod/naturalist): 11,1 mln pobrań, loadery Fabric/Forge/NeoForge, wersje MC 1.18.2, 1.19-1.19.2, 1.20.1, 1.21.1, **26.2 i 26.3**; gałąź `26.2-MultiLoader` ma `fabric_loader_version=0.19.5`, `fabric_api_version=0.160.0+26.2` - **Fabric na 26.2 potwierdzony**. Opis: "47 zwierząt z 66 wariantami" (wg strony Modrinth).

`SleepingAnimal` (interfejs): `boolean canSleep(); void setSleeping(boolean)`. `SleepGoal<E extends PathfinderMob & SleepingAnimal>`:

```java
public SleepGoal(E mob) { this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); this.mob = mob; }
@Override public boolean canUse() {   // tylko gdy mob nie jest sterowany (xxa/yya/zza == 0)
    if (mob.xxa == 0.0F && mob.yya == 0.0F && mob.zza == 0.0F) return mob.canSleep() || mob.isSleeping(); else return false; }
@Override public boolean canContinueToUse() { return mob.canSleep(); }
@Override public void start() { mob.setJumping(false); mob.setSleeping(true); mob.getNavigation().stop();
    mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0D); }
@Override public void stop() { mob.setSleeping(false); }
```

`Bear extends NaturalistAnimal implements NeutralMob, NaturalistGeoEntity, SleepingAnimal, IShearable` (853 linii). Stan snu = `EntityDataAccessor<Boolean> SLEEPING` (synchronizowany do klienta -> animacja `animation.sf_nba.bear.sleep`; dźwięk `BEAR_SLEEP`, pitch x0.3). Goale:

```java
goalSelector: 0 BearFloatGoal(FloatGoal) | 1 BreedGoal | 2 BearMeleeAttackGoal | 3 BearSleepGoal(SleepGoal) | 4 BearTemptGoal, BabyPanicGoal
  | 5 DistancedFollowParentGoal(1.25, 48.0, 8.0, 12.0), SearchForItemsGoal(1.2F, FOOD_ITEMS, 8, 2) | 6 BearHarvestFoodGoal(MoveToBlockGoal: miód z ula - BeehiveBlock.dropHoneycomb, jagody)
  | 7 BearPickupFoodAndSitGoal (siada w wodzie i "łowi") | 8 RandomStrollGoal | 9 LookAtPlayerGoal | 10 RandomLookAroundGoal
targetSelector: 1 BabyHurtByTargetGoal | 2 BearAttackPlayerNearBabiesGoal(NearestAttackableTargetGoal<Player>, 20) | 3 NearestAttackableTargetGoal<Player>(isAngryAt)
  | 4 NearestAttackableTargetGoal<PathfinderMob>(tag naturalist:bear_hostiles && !isSleeping && !isBaby) | 5 ResetUniversalAngerTargetGoal
```
**Naturalist nie ma hibernacji sezonowej** (brak odwołań do sezonu/zimy w `Bear.java`; `canSleep()` zależy od pory dnia/warunków - do sprawdzenia, ale nie od Serene Seasons). "Hibernacja" w opisach społeczności to sen nocny. Wniosek: integracja hibernacji z porami roku będzie autorską funkcją naszego moda.

`Bird extends ShoulderRidingEntity implements FlyingAnimal, NaturalistGeoEntity`: `this.moveControl = new FlyingMoveControl(this, 10, false)`; `createNavigation` -> `new FlyingPathNavigation(this, level)`; goale: `FloatGoal`, `BirdTemptGoal`, `SitWhenOrderedToGoal`, `FollowOwnerGoal`, `BirdWanderGoal extends WaterAvoidingRandomFlyingGoal`, **`BirdFlockGoal extends FollowAdultGoal`** (stado = każdy ptak podąża za dorosłym w promieniu 6-12 bl. - prosty "boids"), `BirdAvoidEntityGoal`. Warianty (bluejay, canary, cardinal, finch, robin, sparrow) = jedna klasa + osobne tekstury 32x32 + `bird_baby_*.png`.

`ClimbingAnimal extends NaturalistAnimal`: `EntityDataAccessor<Byte> CLIMB_FLAG`, `createNavigation` -> `BetterWallClimberNavigation`, w `tick()`: `setNaturalistClimbing(this.horizontalCollision)` - dokładnie wzorzec pająka.

Rozmiary tekstur (odczyt nagłówków PNG, 2026-09-21):

| Mod | Zwierzę | Rozmiar PNG | Uwagi |
|---|---|---|---|
| Naturalist | bear/bear.png | **128x128** | duże ssaki |
| Naturalist | deer/deer.png | **128x128** | + `deer_baby.png`, `reindeer.png`, `white_deer.png` (warianty) |
| Naturalist | duck/duck.png | 64x32 | |
| Naturalist | bird/bluejay.png | 32x32 | ptaki śpiewające; `bluejay-sheet.png` (arkusz?) |
| Alex's Mobs | grizzly_bear.png | 128x128 | |
| Alex's Mobs | roadrunner.png | 64x64 | |
| Alex's Mobs | crow.png | 32x32 | |

Styl obu modów: pixel-art w "gęstości" wanilii (1 texel = 1/16 bloku), płaskie kolory z 2-4 odcieniami cieniowania, brak gradientów; futro rysowane szumem punktowym. Licencje: Naturalist - zasoby All Rights Reserved (kod MIT); Alex's Mobs - plik LICENSE nie znaleziony w repo 1.20 (do potwierdzenia; CurseForge podaje "All Rights Reserved" - z pamięci).

### 6.5. Alex's Mobs (AlexModGuy/AlexsMobs, gałąź domyślna `1.20`, ostatni push 2026-02-04)

- Skala: 4872 plików w drzewie, **120 klas encji** (`entity/Entity*.java`), **111 klas AI** (`entity/ai/`). Forge/NeoForge (nie Fabric; port Fabric = "Alex's Mobs Fabric" nieoficjalny - do potwierdzenia). Licencja - do potwierdzenia (API GitHub: brak SPDX).
- Wzorce AI widoczne w nazwach klas: `AnimalAIHerdPanic` (panika stadna), `AnimalAIFollowParentRanged`, `CosmicCodAIFollowLeader`, `FroststalkerAIFollowLeader` (stado leader/follower), `ElephantAIFollowCaravan`, `GorillaAIFollowCaravan`, `LeafcutterAntAIFollowCaravan` (karawana = łańcuch follow), `AnteaterAIRaidNest` (atak na gniazdo), `SealAIDiveForItems` (nurkowanie), `AnimalAISwimBottom`, `AnimalAIWadeSwimming`, `SemiAquaticAIRandomSwimming` (ssaki ziemno-wodne), `AnimalSwimMoveControllerSink`, `SmartClimbPathNavigator` (wspinanie), `SwimmerJumpPathNavigator`, `FlyingAITempt`, `FlyingAITargetDroppedItems`. Wszystko na systemie **Goal** (klasy `*AI*` = Goal), bez Brain.
- Tekstury: płaskie pliki `textures/entity/<mob>.png` z wariantami `<mob>_yellow.png`, `<mob>_shedding.png`, `<mob>_moss.png` - warianty jako osobne pliki, wybierane w rendererze.

### 6.6. Better Animals Plus (itsmeow/betteranimalsplus, gałąź `1.19`, ostatni push 2023-07 - nieaktywny)

Klasy w `common/entity/`: EntityDeer, EntityMoose (łoś), EntityBear/EntityBearNeutral, EntityBoar, EntityBadger (borsuk), EntityFeralWolf, EntitySquirrel, EntityGoose, EntityPheasant (bażant), EntitySongbird, EntityButterfly, EntityDragonfly, EntityReindeer, EntityLammergeier, EntityFreshwaterEel, EntityLamprey, EntityCrayfish... AI: `HybridPathNavigator` + `HybridMoveController` (ląd+woda), `WaterfowlNavigator` + `WaterMoveHelper` (gęś/kaczka), `LammerMoveHelper` (szybowanie sępa), `EntityAIEatBerries`, `EntityAIEatGrassCustom`, `FollowParentGoalButNotStupid`, `HungerNearestAttackableTargetGoal` + interfejs `IHaveHunger` (drapieżnik atakuje tylko głodny), `MoveIntoBlockGoal`, `EntityAITemptAnyNav`. Klasy bazowe wariantów: `EntityAnimalWithTypes`, `EntityAnimalWithTypesAndSize`, `EntityAnimalEatsGrassWithTypes` (warianty tekstur = "typy" w `DataTracker`, rozmiar per wariant). Stado jelenia: brak dedykowanej klasy "herd" w nazwach - stado wynika ze spawnu paczkowego + FollowParent. Model: ręczny (Java, `client/model/`), nie GeckoLib.

### 6.7. Integracja z Serene Seasons - API (repozytorium Glitchfiend/SereneSeasons, gałąź `26.1.2`, push 2026-09-21; katalogi `common`, `fabric`, `forge`, `neoforge` -> Fabric wspierany także na 26.1.2)

```java
// sereneseasons.api.season.SeasonHelper
public static ISeasonState getSeasonState(Level level);           // serwer lub klient
public static boolean usesTropicalSeasons(Holder<Biome> biome);
// ISeasonState
int getDayDuration(); int getSubSeasonDuration(); int getSeasonDuration(); int getCycleDuration();
int getSeasonCycleTicks(); int getDay(); Season.SubSeason getSubSeason(); Season getSeason(); Season.TropicalSeason getTropicalSeason();
// Season: SPRING, SUMMER, AUTUMN, WINTER; SubSeason: EARLY_/MID_/LATE_ x 4 = 12 podsezonów (każdy z kolorem trawy/liści/brzozy)
// SeasonChangedEvent - zdarzenie zmiany sezonu (klasa w api/season/)
```
Wniosek: zachowania sezonowe (hibernacja: `LATE_AUTUMN..EARLY_SPRING`; gody jelenia: `EARLY/MID_AUTUMN`; zrzucanie poroża: `LATE_WINTER/EARLY_SPRING`; przylot bociana: `EARLY_SPRING`, odlot: `LATE_SUMMER`; futro zimowe: `LATE_AUTUMN..LATE_WINTER`) można oprzeć na `getSubSeason()`; zależność opcjonalna przez `FabricLoader.getInstance().isModLoaded("sereneseasons")` + fallback na własny prosty kalendarz (`getDay() % 360`). Wersja SS na 1.20.1/1.21.1 dla Fabric istnieje (raport o porach roku - do potwierdzenia w innym raporcie).

### 6.8. Untamed Wilds (RayTrace082/untamedwilds, gałąź `1.18.2`, GPL-3.0, Forge + Citadel, ostatni push 2024-05; forki: ACowAdonis/untamed-wilds-au-naturel 1.20.1, Raguto/UntamedWilds-1.21.1)

Najbogatszy zbiór wzorców "ekologicznych" w otwartym kodzie (1902 plików w drzewie). **Uwaga licencyjna: GPL-3.0 to copyleft - kod można czytać i uczyć się z niego, ale skopiowanie fragmentów zmusiłoby nasz mod do licencji GPL-3.0.** Klasy warte przestudiowania (pakiet `untamedwilds.entity`):
- Hierarchia: `ComplexMob` -> `ComplexMobTerrestrial` / `ComplexMobAmphibious` / `ComplexMobAquatic`; interfejsy `ISpecies` (gatunek/wariant sterowany danymi: `SpeciesDataHolder`, `SpawnDataHolder`, `SpawnDataListenerEvent` - **gatunki i ich spawn ładowane z JSON**, "Species are dynamically assigned to each Biome depending on its features from a list of weighted entries"), `IPackEntity` (wataha), `HerdEntity` (stado), `INestingMob` (gniazdujące), `INeedsPostUpdate`.
- Bloki: `CritterBurrowBlock` + `CritterBurrowBlockEntity` (**nora** jako blok z BlockEntity przechowującym drobne zwierzęta), `NestReptileBlock` + `ReptileNestBlockEntity` (gniazdo z jajami) - dokładnie wzorzec "dom = BlockEntity" z sekcji 8.4.
- AI: `GotoSleepGoal` (sen), `GrazeGoal` (wypas), `FishWanderAsSchoolGoal` + `FishReturnToSchoolGoal` (ławice), `FollowParentGoal`, `SmartMateGoal`, `SmartAvoidGoal`, `AmphibiousRandomSwimGoal` + `AmphibiousTransition` + `SmartAmphibiousMoveControl`/`SmartSwimmingMoveControl` (ląd<->woda), `LayEggsOnNestGoal`, `RaidCropsGoal` (dzik na polu), `MeleeAttackCircleHerd` (stado otacza), `MeleeAttackCharger` (szarża), targety: `HuntMobTarget`, `HuntPackMobTarget` (polowanie watahą), `HuntWeakerTarget`, `HurtPackByTargetGoal` (wataha broni członka), `ProtectChildrenTarget`, `AngrySleeperTarget` (obudzony atakuje), `GuardPositionTarget`, `BisonTerritorialityFight` (walki terytorialne samców - wzór dla rykowiska jeleni), `BearRaidChestsGoal`.
- Spawn: `FaunaSpawn` - **wyłącznie przy generacji świata** ("All mobs/features are created as part of Worldgen. They also will not respawn (outside of breeding/growing)") - to świadoma decyzja autora: populacja rośnie tylko przez rozmnażanie; README przyznaje brak RetroGen. Dla naszego moda to ostrzeżenie: sam spawn worldgen bez uzupełniania prowadzi do wymierania fauny w eksplorowanych obszarach.

### 6.9. Exotic Birds i inne (nie analizowano kodu)

- **Exotic Birds** (CurseForge, 15,4 mln pobrań; Forge): "more than 30 new types of birds", dzięcioły stukające w drzewa, łabędzie na rzekach, lirogony naśladujące dźwięki; ma bloki gniazd z jajami (z pamięci - do potwierdzenia); kod - nie sprawdzono, czy otwarty. https://www.curseforge.com/minecraft/mc-mods/exotic-birds
- Wildlife, Realistic Bees, Bird Nests, Fauna, Creatures and Beasts, Animalistic - NIE ZBADANO (budżet zapytań). 

### 6.10. Synteza wzorców implementacyjnych dla zachowań wymaganych przez użytkownika

| Zachowanie | Wzorzec z istniejącego kodu | Propozycja dla moda |
|---|---|---|
| Stado (jeleń, sarna, dzik, żubr) | Naturalist `FollowAdultGoal`/`BirdFlockGoal` (każdy idzie za dorosłym), `AlertOthersPanicGoal`; Untamed Wilds `HerdEntity`, `MeleeAttackCircleHerd`; Alex's Mobs `AnimalAIHerdPanic`, `*FollowLeader`; spawn paczkowy (4-8) | `HerdMember` z `UUID herdLeader` w NBT (lider = najstarsza samica/łania - jak w naturze: chmara prowadzona przez licówkę); follower: `FollowEntity(leader)` gdy > 8 bl.; lider: `SetRandomWalkTarget`; panika rozgłaszana przez memory `HERD_ALARM` (sensor skanujący stado co 20 ticków); samce poza rykowiskiem osobno (chmary byków) |
| Wataha (wilk) | Untamed Wilds `IPackEntity`, `HuntPackMobTarget`, `HurtPackByTargetGoal`; wanilia: wilk oswojony `WolfEntity` (drużyna gracza) | `PackMember` z parą alfa (para rodzicielska, 4-8 os.), polowanie kooperacyjne = wspólny `ATTACK_TARGET` z wyboru alfy (`SetAdditionalAttackTargets` SBL), terytorium (`GuardPositionTarget` -> `GlobalPos` "rendez-vous"), wycie o zmierzchu (schedule) |
| Gniazdo (bocian, ptaki śpiewające, żuraw) | Untamed Wilds `NestReptileBlock`+BlockEntity, `LayEggsOnNestGoal`; Naturalist `LayEggGoal`, jaja `stage_0..2.png`; wanilia: jaja żółwia (`TurtleEggBlock` z `HATCH` 0-2), sniffer egg; pszczoła/ul (BlockEntity trzyma encje) | `NestBlock` (bocian: na kominie/słupie/drzewie - struktura z raportu 01; drobne ptaki: w liściach - niewidoczne "gniazdo" jako BlockEntity w bloku liści lub własny blok `pzl:bird_nest` w koronie), stany: pusty/jaja/pisklęta (BlockState property), sezonowo: `EARLY_SPRING` zajęcie, `MID_SPRING` jaja, `EARLY_SUMMER` pisklęta, `LATE_SUMMER` wylot; ptak z gniazdem = `HOME` memory; despawn gdy daleko, respawn przy gnieździe |
| Nora (borsuk, lis, królik), gawra (niedźwiedź), żeremie (bóbr) | Untamed Wilds `CritterBurrowBlock`+BlockEntity; wanilia `BeehiveBlockEntity` (NBT encji, wypuszczanie po warunkach) | blok wejścia + BlockEntity z listą NBT mieszkańców; wejście = usunięcie encji (zero kosztu ticku), wyjście = deserializacja; hibernacja niedźwiedzia = gawra z warunkiem `SubSeason in [LATE_AUTUMN..EARLY_SPRING]` + brak wyjścia; borsuk: sen zimowy przerywany (wychodzi w odwilż = jeśli temperatura biomu/sezonu > 0 - Serene Seasons ma `SeasonHelper`... temperatura sezonowa przez `ISeasonState`? nie ma bezpośrednio - obliczać z SubSeason) |
| Migracja (bocian, żuraw, gęsi, jaskółki) | brak gotowego w modach; sezonowy despawn/spawn (8.4) | `SeasonalPresence` w danych gatunku: `present: [EARLY_SPRING..LATE_SUMMER]`; poza sezonem: encje "odlatują" (lecą w górę na S, despawn), gniazda pamiętają; klucz żurawi jako efekt wizualny (encja "stado w locie" bez kolizji, bardzo tania) |
| Okres godowy (rykowisko jeleni, gody łosi, bekowisko saren, toki cietrzewi/głuszców) | Untamed Wilds `BisonTerritorialityFight`; wanilia: koza `RamTarget`, walki (`Attributes.ATTACK_DAMAGE` między samcami) | aktywność `RUT` w Schedule tylko w `EARLY/MID_AUTUMN` (jeleń), `LATE_SUMMER/EARLY_AUTUMN` (łoś), `MID_SUMMER` (sarna - bekowisko lipiec/sierpień), `EARLY_SPRING` (toki); ryk = `getAmbientSound` z sezonem, samce zbierają chmary, `AnimatableMeleeAttack` między bykami; `BreedWithPartner` tylko w sezonie -> młode rodzą się w `LATE_SPRING` (ciąża jako `IS_PREGNANT` memory + licznik dni w NBT) |
| Poroże (jeleń, sarna, łoś, daniel) | wanilia: brak; mody - warianty tekstur/modeli (Naturalist `reindeer.png`) | kość `antlers` w geo.json widoczna/ukryta przez `GeoBone.setHidden` na podstawie DataTicket `ANTLER_STAGE` (0 = zrzucone `LATE_WINTER..EARLY_SPRING`, 1 = scypuł/wzrost `MID_SPRING..MID_SUMMER` tekstura aksamitna, 2 = pełne `LATE_SUMMER..MID_WINTER`); zrzucone poroże = item dropowany w miejscu (zbieractwo!) |
| Futro zimowe (jeleń, lis, sarna, gronostaj, zając) | Alex's Mobs warianty `_variant.png` wybierane w rendererze; Naturalist `white_deer.png` | `getTextureResource` wg DataTicket `SEASON_COAT` (zima/lato) z płynnym "przejściem" = losowe opóźnienie per osobnik (±10 dni) żeby stado nie zmieniało się naraz; gronostaj (*Mustela erminea*): biały zimą |
| Młode (skala, model, tekstura, zachowanie) | wanilia: `isBaby()` + `Attributes.SCALE` (1.20.5+), `AgeableMob.getAgeScale`; Naturalist osobny `*_baby.geo.json` (inne proporcje: duża głowa); `FollowParentGoal`/`DistancedFollowParentGoal(48, 8, 12)`; `BabyPanicGoal`, `AttackPlayerNearBabiesGoal` (obrona) | skala 0.5-0.6 + osobna tekstura (cętki jelonka, pręgi warchlaka) w tym samym geo (uproszczenie) lub osobny geo dla gatunków o innych proporcjach; dorastanie -24000 ticków wanilii -> realistycznie kilka sezonów (`age` liczony dniami: jelonek dorosły po ~1 roku gry = 360 dni? za długo dla gracza - kompromis 20-30 dni), matka broni (`ProtectChildrenTarget`) |
| Dźwięki | Naturalist: `BEAR_SLEEP` z pitch ×0.3 przy śnie, ambient zależny od stanu; GeckoLib `setSoundKeyframeHandler` (dźwięk w klatce animacji: klekot bociana, uderzenie ogona) | 3-5 wariantów per głos (`sounds.json` losowanie ważone), sezonowe głosy (ryk tylko w rui), `getAmbientSoundInterval` dłuższy dla ssaków (400-800 ticków), krótszy dla ptaków śpiewających (100-200) o świcie (chór poranny jako Schedule) |
| Wspinanie (kuna, wiewiórka, ryś, żbik) | Naturalist `ClimbingAnimal` + `BetterWallClimberNavigation` (horizontalCollision); Alex's Mobs `SmartClimbPathNavigator` | `WallClimberNavigation` + `isClimbing()` ograniczone do bloków w tagu `pzl:climbable_trees` (kłody, liście); wiewiórka: skoki między koronami (`LeapAtTarget`/`JumpGoal`) |
| Nurkowanie (bóbr, wydra, kormoran, perkoz) | Alex's Mobs `SealAIDiveForItems`, `AnimalAISwimBottom`, `AnimalSwimMoveControllerSink`; Untamed Wilds `AmphibiousTransition`; wanilia delfin (`getMoistness`), aksolotl (`AmphibiousPathNavigation`) | `DivingAnimal`: cel 3D pod wodą (`SetRandomSwimTarget` SBL), własny MoveControl z pitch, licznik zapasu powietrza, powrót na powierzchnię; kormoran: lot -> lądowanie na wodzie -> nurkowanie -> suszenie skrzydeł (animacja) |
| Lot (bocian, żuraw, myszołów, bielik, kruk) | Naturalist `Bird` (`FlyingMoveControl(this, 10, false)`, `FlyingPathNavigation`, `WaterAvoidingRandomFlyingGoal`); Better Animals Plus `LammerMoveHelper` (szybowanie sępa) | duże ptaki: własny `SoaringMoveControl` (krążenie w termice: cel na okręgu o promieniu 16-32 bl. na wys. 20-40 bl. nad terenem, powolne obroty), lądowanie na "grzędach" (tag bloków: szczyt drzewa, słup, komin, gniazdo), żerowanie na ziemi (`GroundPathNavigation` po wylądowaniu - przełączanie nawigacji jak pszczoła) |

## 7. Dźwięki: SoundEvent, sounds.json, xeno-canto, freesound, licencje

### 7.1. Rejestracja dźwięków (docs.fabricmc.net "Creating Custom Sounds", https://docs.fabricmc.net/develop/sounds/custom, wersja 26.2, mapowania Mojang)

- Pliki: `resources/assets/<modid>/sounds/*.ogg`; format: "OGG Vorbis is an open container format" oraz "your audio needs to have only a single channel (Mono)" - dźwięki stereo nie są pozycjonowane w 3D (grają "w głowie" gracza).
- `sounds.json` w `assets/<modid>/sounds.json`:

```json
{ "metal_whistle": { "subtitle": "sound.example-mod.metal_whistle", "sounds": ["example-mod:metal_whistle"] } }
```

Pozostałe pola `sounds.json` (z minecraft.wiki, z pamięci - do potwierdzenia): `category` (master, music, record, weather, block, hostile, neutral, player, ambient, voice), `sounds[]` jako string lub obiekt `{ "name", "volume", "pitch", "weight", "stream" (true dla długich >~10 s), "attenuation_distance" (domyślnie 16), "preload", "type": "sound"|"event" }`; wiele wpisów w `sounds` = losowy wybór ważony (idealne dla 3-5 wariantów głosu jelenia).

```java
public class CustomSounds {
  public static final SoundEvent ITEM_METAL_WHISTLE = registerSound("metal_whistle");
  private static SoundEvent registerSound(String id) {
    Identifier identifier = ExampleMod.id(id);
    return Registry.register(BuiltInRegistries.SOUND_EVENT, identifier, SoundEvent.createVariableRangeEvent(identifier));
  }
  public static void initialize() {}
}
```
(Yarn 1.20.1/1.21.1: `Registries.SOUND_EVENT`, `SoundEvent.of(Identifier)`.) W encji: nadpisać `getAmbientSound()`, `getHurtSound(DamageSource)`, `getDeathSound()`, `getEatSound(ItemStack)`, `playStepSound(...)`, `getSoundVolume()`; częstotliwość dźwięku otoczenia: `getMinAmbientSoundDelay()` (Yarn) domyślnie 80 ticków + losowość (`ambientSoundChance`). Napisy: klucz `subtitle` w `lang/pl_pl.json`.

### 7.2. Źródła nagrań i licencje

**xeno-canto** (https://xeno-canto.org; strona `about/terms` zwraca "Access Denied" dla botów - dane z https://en.wikipedia.org/wiki/Xeno-canto, 2026-09-21): projekt citizen science, >1 mln nagrań, >12 900 gatunków (ptaki, od 2022 także płazy, nietoperze, koniki polne); prawa autorskie **pozostają przy nagrywających**; wszystkie nagrania na licencjach Creative Commons wybranych przez autora (na stronie dostępne: CC BY, CC BY-SA, CC BY-NC, CC BY-NC-SA, CC BY-NC-ND - z pamięci; domyślna przy uploadzie to **CC BY-NC-SA 4.0** - do potwierdzenia). Ma API (`https://xeno-canto.org/api/2/recordings?query=...`, w 2025 wymagane klucz API po rejestracji - do potwierdzenia) z polami m.in. `id`, `gen`, `sp`, `en`, `rec` (nagrywający), `cnt` (kraj), `type` (song/call), `lic` (URL licencji), `q` (jakość A-E). Dla moda (dystrybucja darmowa, ale z opcją np. Patreon) bezpieczne są tylko nagrania **CC BY / CC BY-SA / CC0** - filtr `lic` w zapytaniu; licencje NC wykluczają jakiekolwiek zarabianie, ND wyklucza przycinanie/miksowanie. Atrybucja: "XC123456, nagrywający Jan Kowalski, xeno-canto.org, CC BY 4.0" w pliku CREDITS moda.

**Freesound** (https://freesound.org/help/faq/, 2026-09-21): trzy licencje - **CC0** ("You can do practically anything with the sound. You can even sell it"), **CC BY 4.0** (obowiązek atrybucji), **CC BY-NC 4.0** (bez zarabiania) + wycofana Sampling+ 1.0 (stare pliki). Format atrybucji zalecany przez Freesound: `"[nazwa dźwięku]" by [użytkownik] (freesound.org/s/[ID]) licensed under [licencja]`. Pliki: oryginały (WAV/AIFF/FLAC/MP3/OGG) + podglądy; API v2 (klucz po rejestracji). Konwersja do mono OGG Vorbis (ffmpeg: `ffmpeg -i in.wav -ac 1 -ar 44100 -c:a libvorbis -q:a 4 out.ogg`).

Inne (nie badano w tym raporcie, do rozważenia): Macaulay Library (Cornell) - licencje restrykcyjne (na ogół nie do redystrybucji); Wikimedia Commons (kategoria "Bird sounds", licencje CC); BBC Sound Effects (RemArc - tylko niekomercyjne); nagrania własne (dozwolone, najbezpieczniejsze). Wielu polskich gatunków (np. bocian biały - klekot, żuraw, łoś na rykowisku - rzadkie, jeleń - rykowisko, wilk - wycie, bóbr - uderzenie ogonem) trzeba szukać w xeno-canto po nazwie łacińskiej (`Ciconia ciconia`, `Grus grus`, `Alces alces`, `Cervus elaphus`, `Canis lupus`, `Castor fiber`) - dostępność dla ssaków jest znacznie mniejsza niż dla ptaków.

## 8. Wydajność: koszt tickowania, Brain vs Goal, limity encji, activation range, symulacja poza załadowanymi chunkami

### 8.1. Entity activation range (Spigot/Paper) - wzorzec do odtworzenia w Fabric

Źródło: https://docs.papermc.io/paper/reference/spigot-configuration (sekcja `entity-activation-range`, pobrane 2026-09-21). "The distance in blocks from a player at which entities tick normally. Outside this range, entities tick less frequently." Domyślne: animals **32**, monsters 32, raiders 64, misc 16, water 16, villagers 32, flying-monsters 32. `wake-up-inactive`: animals-max-per-tick 4, animals-every 1200 ticków ("How often an inactive animal outside of range will be woken up"), animals-for 100 ticków ("How long to wake an inactive animal up for"). `entity-tracking-range` (wysyłanie do klienta): players 128, animals **96**, monsters 96, misc 96, other 64. `mob-spawn-range`: 8 chunków. Wanilia (i Fabric) **nie ma** activation range - wszystkie encje w załadowanych, tickujących chunkach (simulation distance, domyślnie 10 chunków na SP? w 1.18+ `simulation-distance` domyślnie 10) tickują co tick z pełnym AI. Wniosek: w naszym modzie dodać własny "budżet AI": jeśli `distanceToNearestPlayerSq > 32^2`, tickować Brain/goale co 10-20 ticków (nadpisać `tickMovement`/`mobTick` (Yarn) = `customServerAiStep` (Mojang)), a animacje/look control pomijać.

### 8.2. Lithium (CaffeineMC/lithium, LGPL-3.0, gałąź develop, push 2026-09-16)

Lithium (Fabric) optymalizuje m.in. AI (goal selector, sensory, pathfinding), kolizje encji i tickowanie nieaktywnych encji; nie wprowadza activation range (celowo zachowuje zachowanie wanilii). NIE ZBADANO: lista opcji `mixin.ai.*`/`mixin.entity.*` - plik `lithium-mixin-config.md` nie istnieje już w gałęzi `develop` (drzewo zawiera tylko `CHANGELOG.md`, `CONTRIBUTING.md`, `LICENSE.md`); opcje są teraz opisane prawdopodobnie w wiki/`lithium.properties`. Mod powinien być kompatybilny z Lithium (unikać mixinów w te same metody `GoalSelector.tick`, `Brain.tick`, `ServerEntityManager`).

### 8.3. Koszty tickowania - wiedza ogólna (bez pojedynczego źródła; do potwierdzenia profilerem)

- GoalSelector: co 2 ticki iteracja po N goalach (`canStart`) - koszt rośnie liniowo z liczbą goali; drogie są goale skanujące encje w promieniu (`getEntitiesByClass` w boxie 8-16 bl. co tick - jak `PickupItemGoal` Eager Beavers co 10 ticków) i pathfinding (`findPathTo` - A* na siatce, limit `followRange` 16-32 bl.; koszt ~O(węzłów), największy pojedynczy koszt AI).
- Brain: sensory co 20 ticków (tańsze skanowanie), ale `Brain.tick` iteruje po wszystkich zachowaniach aktywnych aktywności co tick; wieśniacy są znani jako najdroższe moby wanilii (głównie przez pathfinding do POI i `NearestLivingEntitySensor`). SmartBrainLib deklaruje wydajniejszą implementację.
- Render: GeckoLib renderuje każdą kość osobno z interpolacją keyframe'ów co klatkę - przy 100+ encjach w kadrze koszt CPU po stronie klienta jest zauważalny; mitygacja: LOD (uproszczony model/animacja > 32 bl.), `shouldRender` z mniejszym zasięgiem dla małych zwierząt (ptaki śpiewające, płazy: 48 bl. zamiast 96).
- Limity encji: brak twardego limitu w wanilii; praktycznie ~300-500 aktywnych mobów z AI na serwerze to próg spadku TPS (zależny od CPU) - do zmierzenia.

### 8.4. "Symulacja" poza załadowanymi chunkami

Niemożliwa wprost (encje w niezaładowanych chunkach są zserializowane w NBT i nie tickują). Sposoby udawania:
1. **Stan świata w `PersistentState`/`SavedData`** (per wymiar): rejestr "domów" (żeremia, nory, gniazda, legowiska) z pozycją, gatunkiem, liczebnością, czasem ostatniej symulacji; przy załadowaniu chunka (`ServerChunkEvents.CHUNK_LOAD`, Fabric API) liczy się `deltaDays = (now - lastSimulated) / 24000` i aktualizuje populację modelem (narodziny w sezonie, śmiertelność) oraz spawnuje encje przy domu ("catch-up").
2. **Sezonowe znikanie/pojawianie się** (migracje ptaków): zamiast przemieszczać encje - despawn poza sezonem (bocian: `LATE_SUMMER` odlatuje = encja odlatuje w górę i jest usuwana, gniazdo zapamiętuje "zajęte"); powrót `EARLY_SPRING` = spawn przy gnieździe.
3. **Hibernacja** = encja w bloku/strukturze (gawra jako blok z BlockEntity przechowującym NBT niedźwiedzia, jak `Bee` w ulu: `BeehiveBlockEntity` trzyma `NbtCompound` pszczół i wypuszcza je po warunkach) - **zero kosztu ticku** przez całą zimę i odporność na despawn. To wzorzec wanilii, który idealnie pasuje do gawry, nory borsuka, żeremia, gniazda.
4. Blok gniazda/nory tickujący zamiast encji (`BlockEntity` z `tick` co 100-200 ticków, sprawdzający sezon i wypuszczający/wchłaniający mieszkańców).

## 9. Przykładowa struktura kodu jednej złożonej encji (bóbr europejski, *Castor fiber*)

Propozycja syntetyczna oparta na wzorcach z Eager Beavers (6.2), Naturalist (6.4.1), pszczoły/ula wanilii (8.4) i API z sekcji 1-2, 5. Mapowania Mojang (26.x); w nawiasach Yarn dla 1.21.1. Ścieżki względem `src/main/`.

```
java/pl/przyrodniczelasy/
  entity/
    PzlEntityTypes.java              // rejestracja: EntityType.Builder.of(BeaverEntity::new, PzlSpawnGroups.SEMI_AQUATIC_MAMMAL).sized(0.8f, 0.5f).build(key)
    PzlEntityAttributes.java         // FabricDefaultAttributeRegistry.register(BEAVER, BeaverEntity.createAttributes())
    PzlSpawnGroups.java              // (opcjonalnie) mixin rozszerzający MobCategory/SpawnGroup lub własny spawner (5.7)
    base/
      PzlAnimal.java                 // extends Animal implements GeoEntity: cache GeckoLib, wspólne DataTickets (season, isBaby, variant), AI budget (8.1)
      SeasonalBehaviour.java         // interfejs: onSubSeasonChanged(SubSeason), isActiveInSeason(SubSeason)
      HomeOwner.java                 // interfejs: Optional<GlobalPos> getHome(); setHome(...); homeType()
      DivingAnimal.java              // interfejs: maxDiveTicks(), isDiving(), air budget (jak Dolphin.getMoistness)
    mammal/beaver/
      BeaverEntity.java              // extends PzlAnimal implements SmartBrainOwner<BeaverEntity>, HomeOwner, DivingAnimal, SeasonalBehaviour
      BeaverAi.java                  // statyczne: sensory, aktywności (CORE, IDLE, WORK=budowa, REST=w żeremiu), Schedule (nocny/zmierzchowy)
      behaviour/
        FindDamSite.java             // ExtendedBehaviour: skan cieku (bloki wody z prądem, wąski przekrój, głębokość <= 2) -> MemoryModuleType DAM_SITE
        HarvestTree.java             // ścinanie drzew miękkich (tag pzl:beaver_food_trees: wierzba, topola, osika, brzoza) w promieniu 16 bl. od wody; wymaga mobGriefing; etapy: ogryzanie (blok "nadgryziony pień" z BlockState property), upadek
        CarryLogToDam.java           // niesienie (ItemStack w MAINHAND / własny DataTracker) -> stawianie bloku pzl:beaver_dam (waterlogged, z poziomem "wypełnienia") 
        RaiseWaterLevel.java         // po zamknięciu przekroju: zamiana wody za tamą na "głęboką" (własny fluid/waterlogged block) w promieniu N - uproszczony model hydrologiczny (patrz Niepewności)
        BuildLodge.java              // gdy staw ma >= 2 bl. głębokości: stawianie struktury żeremia (BlockEntity LodgeBlockEntity) z komorą powietrzną
        EnterLodge.java / ExitLodge  // wejście = serializacja encji do LodgeBlockEntity (jak BeehiveBlockEntity), wyjście po warunkach (noc, sezon, głód)
        DiveForFood.java             // nurkowanie do kłączy grążeli/pałki (nowe rośliny wodne z raportu 02) - cel 3D w wodzie, własny MoveControl
        FellTreeAlert.java           // uderzenie ogonem (dźwięk pzl:beaver_tail_slap) przy zagrożeniu - Panic rozgłaszany do rodziny (memory NEARBY_FAMILY)
      BeaverMoveControl.java         // hybryda: ląd (GroundPathNavigation) / woda (AmphibiousPathNavigation + cel 3D); przełączanie jak Axolotl/Frog
    ...
  block/
    BeaverDamBlock.java, BeaverLodgeBlock.java, LodgeBlockEntity.java (NBT list encji, tick co 200: sezon, wypuszczanie), GnawedLogBlock.java
  world/
    PzlHomesState.java               // SavedData (PersistentState): rejestr żeremi/nor/gniazd + lastSimulatedTime; catch-up przy CHUNK_LOAD (8.4)
    spawner/BeaverColonySpawner.java // CustomSpawner: przy generacji/ładowaniu rzeki bez kolonii w promieniu 256 bl. -> rodzina 2-5 os. + żeremie startowe (struktura)
  client/
    PzlClient.java                   // EntityRenderers.register(BEAVER, ctx -> new PzlAnimalRenderer<>(ctx, BEAVER)); rejestracja DataTickets
    render/PzlAnimalRenderer.java    // extends GeoEntityRenderer: addRenderData(season, isBaby, carriedLog); warstwy: GeoRenderLayer dla niesionej kłody, futro zimowe (tekstura _winter)
    model/BeaverGeoModel.java        // DefaultedEntityGeoModel(id("mammal/beaver")) + nadpisanie getTextureResource wg DataTicket sezonu
  sound/PzlSounds.java               // SoundEvent: beaver_ambient, beaver_hurt, beaver_death, beaver_tail_slap, beaver_gnaw
resources/
  assets/pzl/geckolib/models/entity/mammal/beaver.geo.json       (+ beaver_baby.geo.json lub skala)
  assets/pzl/geckolib/animations/entity/mammal/beaver.animation.json   (move.walk, move.swim, move.dive, misc.idle, misc.gnaw, misc.carry, misc.sleep, misc.tail_slap)
  assets/pzl/textures/entity/mammal/beaver.png, beaver_winter.png, beaver_baby.png (64x64)
  assets/pzl/sounds.json, sounds/beaver_*.ogg (mono OGG), lang/pl_pl.json (subtitles, nazwa "Bóbr europejski")
  data/pzl/worldgen/biome/*.json (spawners: brak dla bobra - spawn przez BeaverColonySpawner), tags/block/beaver_food_trees.json, tags/entity_type/beaver_predators.json (wilk, ryś, lis dla młodych)
  data/pzl/worldgen/structure/beaver_lodge.json (+ template .nbt) - startowe żeremie; loot_table/entities/beaver.json (brak dropu skóry? decyzja etyczna użytkownika)
```

Kluczowe metody `BeaverEntity` (Mojang 26.x):
- `createAttributes()`: `Animal.createMobAttributes().add(Attributes.MAX_HEALTH, 12).add(Attributes.MOVEMENT_SPEED, 0.22).add(Attributes.SCALE, 1.0)` (młode: `getAgeScale()`/`Attributes.SCALE` 0.5).
- `createNavigation(Level)`: `AmphibiousPathNavigation` z `setCanFloat(true)`; `setPathfindingMalus(PathType.WATER, 0)`; `canBreatheUnderwater()` = false + własny `diveTicks` (bóbr wytrzymuje do ~15 min; w grze 600-1200 ticków), `getMaxAirSupply()` = 1200.
- `getBrain()/brainProvider()` przez SmartBrainLib: `SmartBrainOwner`: `getSensors()` (NearbyPlayers, NearbyLivingEntity z filtrem drapieżników, HurtBy, własny `NearbyWaterSensor`, `SeasonSensor`), `getCoreTasks()` (`FloatToSurfaceOfFluid`, `LookAtTarget`, `MoveToWalkTarget`, `Panic`/`FleeTarget` od `beaver_predators`), `getIdleTasks()` (`FirstApplicableBehaviour(BreedWithPartner, FollowParent, DiveForFood, SetRandomSwimTarget/SetRandomWalkTarget, Idle)`), aktywność `WORK` (`FindDamSite -> HarvestTree -> CarryLogToDam -> RaiseWaterLevel -> BuildLodge`), `REST` (`EnterLodge`), `getSchedule()`: `SmartBrainSchedule` - 0-1000 (świt) WORK; 1000-12000 (dzień) REST (w żeremiu); 12000-13000 IDLE; 13000-23000 (noc) WORK/IDLE (bóbr jest zmierzchowo-nocny); zimą (SubSeason MID_WINTER..LATE_WINTER): REST z krótkimi wyjściami pod lodem (uproszczenie).
- `registerControllers`: `DefaultAnimations.genericWalkIdleController()` + `genericSwimController` + kontroler "work" (gnaw/carry/tail_slap triggerable: `triggerableAnim("tail_slap", ...)` wyzwalany z serwera `triggerAnim("work", "tail_slap")`).
- `getAmbientSound/getHurtSound/getDeathSound` -> `PzlSounds`; `getMinAmbientSoundDelay`? (Mojang: `getAmbientSoundInterval()`) 200.
- Persistencja: `removeWhenFarAway(double)` -> `false` gdy ma dom w `PzlHomesState`, inaczej `true` (patrz 5.7); NBT: `Home`, `DiveTicks`, `CarriedLog`, `FamilyId` (UUID rodziny - bobry żyją w rodzinach 2-8 os., młode zostają 2 lata).

Szacunek nakładu (na podstawie 949 linii Eager Beavers z prostszą logiką): bóbr z tamą/żeremiem/hydrologią ~2500-4000 linii Javy + 3 bloki + 1 struktura + SavedData; to najbardziej złożona encja moda - reszta gatunków reużywa klas bazowych (stado, nora, gniazdo, lot).

## Implikacje projektowe dla moda

1. **Wersja docelowa i mapowania:** pisać od początku w mapowaniach Mojang (nawet jeśli celem będzie 1.21.1 - Loom `officialMojangMappings()`), bo 26.x nie ma Yarn; struktura multi-version jak Naturalist (`26.2-MultiLoader`). Decyzja o wersji (1.20.1 vs 1.21.1 vs 26.2) musi uwzględniać dostępność Serene Seasons i GeckoLib: SS ma gałąź 26.1.2 z Fabric, GeckoLib 5.5.1 na 26.2, Naturalist już na 26.3 - **26.x jest realne**.
2. **Silnik modeli: GeckoLib** (5 dla 26.x / 4 dla 1.2x) + konwencja nazw animacji `DefaultAnimations` (`move.walk`, `misc.idle`...) i `DefaultedEntityGeoModel` z podfolderami `mammal/`, `bird/`, `fish/`, `amphibian/`, `insect/`. Jedna klasa renderera dla wszystkich gatunków, dane sezonowe/wariantowe przez `DataTicket`.
3. **Pipeline zasobów:** generator (Python) z parametrycznego opisu gatunku (wymiary w cm -> px: 1 px = 6,25 cm; łoś 2,1 m w kłębie = 34 px = 2,1 bloku!) produkuje `.geo.json` (kości wg standardowego szkieletu czworonoga/ptaka/ryby), `.animation.json` (chód/kłus/galop sinusoidalny, pływanie, lot) i bazę tekstury (paleta, countershading, wzory, UV); artysta dopracowuje w Blockbench. Rozmiary tekstur: 128 (duże), 64 (średnie), 32 (małe), 16 (owady).
4. **Skala realistyczna:** wanilijna krowa ma 1,4 bl. wysokości - realne wymiary (łoś 2,1 m, jeleń 1,5 m, żubr 1,9 m) będą większe niż krowa; hitboxy (`sized`) muszą mieścić się w korytarzach lasu (podszyt z raportu 02) - inaczej pathfinding zawiedzie; `Attributes.SCALE` do dymorfizmu i młodych.
5. **AI: Brain przez SmartBrainLib** jako rdzeń (harmonogram dobowy `Schedule`, pamięć z wygasaniem, sensory co 20 ticków), z klasami bazowymi cech (`SleepingAnimal`, `ClimbingAnimal`, `HomeOwner`, `HerdMember`, `PackMember`, `DivingAnimal`, `SeasonalBehaviour`) w stylu Naturalist; Goal tylko dla najprostszych mobów (ryby, owady). Wszystkie "stany biologiczne" (sen, hibernacja, ruja, ciąża, migracja, linienie) jako `MemoryModuleType` + NBT.
6. **Sezony:** miękka zależność od Serene Seasons (`isModLoaded`), własny `SeasonProvider` z fallbackiem; tabela gatunek -> podsezon dla: hibernacji, godów, narodzin, migracji, poroża, futra. Nasłuch `SeasonChangedEvent`.
7. **Spawn - trzy warstwy:** (a) worldgen: `creature`/`water_ambient` w JSON własnych biomów z podniesionym `creature_spawn_probability`; (b) własny `CustomSpawner` per "gildia" (duże ssaki, drapieżniki, ptaki, ziemno-wodne) z limitami na obszar i warunkami (biom, sezon, pora dnia, woda, wysokość); (c) własne `SpawnGroup` przez mixin tylko jeśli (b) okaże się niewystarczające. Wszystkie własne zwierzęta despawnują poza 128 bl., chyba że mają dom lub są nazwane/oswojone.
8. **Domy jako bloki z BlockEntity** (gawra, nora, żeremie, gniazdo, ul dzikich pszczół, mrowisko) + `SavedData` rejestr domów z "catch-up" populacji przy ładowaniu chunka - jedyny wykonalny sposób "symulacji" poza zasięgiem i zerowy koszt ticku hibernujących/śpiących.
9. **Bóbr:** klasa referencyjna złożoności (sekcja 9): tama = własny blok waterlogged, żeremie = struktura + BlockEntity, hydrologia uproszczona (podniesienie poziomu wody za tamą do 2-3 bl. w promieniu N); wymaga `mobGriefing`-podobnej reguły gry `pzl:animalsModifyTerrain`.
10. **Ptaki:** dwa profile - małe (Naturalist `Bird`: `FlyingMoveControl(10,false)`, `FlyingPathNavigation`, stado przez `FollowAdult`) i duże szybujące (własny `SoaringMoveControl`, grzędy, gniazda sezonowe, migracja przez despawn/respawn). Chór poranny jako aktywność w Schedule.
11. **Wydajność:** własny budżet AI (Brain co 10-20 ticków dla encji > 32 bl. od gracza), `shouldRender` skrócony dla małych zwierząt, LOD animacji, limity per spawner; test z 300+ encjami w profilerze przed rozszerzaniem listy gatunków; kompatybilność z Lithium.
12. **Dźwięki:** katalog `sounds.json` generowany skryptem z manifestu (gatunek -> pliki XC/Freesound, licencja, autor) + automatyczny `CREDITS.md`; tylko CC0/CC BY/CC BY-SA; konwersja do mono OGG; keyframe'y dźwięku w animacjach (klekot, uderzenie ogona, ryk).
13. **Licencje assetów:** nie wolno używać modeli/tekstur Naturalist (ARR), Alex's Mobs (brak licencji) ani kodu Untamed Wilds (GPL) - tylko jako inspiracja; Eager Beavers (CC0) można wykorzystać, ale jakość modelu do oceny.
14. **Kolejność wdrożenia:** 1) szkielet: `PzlAnimal` + renderer + generator geo/anim/tekstur + 1 gatunek testowy (sarna); 2) system domów + spawner + sezony; 3) profile: stado (jeleń), wataha (wilk), nora (borsuk/lis), ptak mały (zięba), ptak duży (bocian), ziemno-wodny (bóbr), ryba (płoć), płaz (żaba trawna), owad (motyl); 4) skalowanie do 50-80 gatunków przez dane (JSON gatunku: wymiary, paleta, profil AI, spawn, sezony).
15. **Dane gatunków w JSON** (jak `SpeciesDataHolder` w Untamed Wilds): jeden plik na gatunek z parametrami morfologii, ekologii i spawnu - ułatwia dodawanie gatunków bez kodu i konsultowanie z użytkownikiem (nieścisłości przyrodnicze można poprawiać w danych).

## Niepewności / do potwierdzenia

1. Czy `SpawnGroup`/`MobCategory` w MC 26.x jest nadal enumem (możliwość rozszerzenia mixinem) - nie sprawdzono.
2. Dokładne sygnatury 26.2: `EntityType.Builder.build(ResourceKey)`, `SpawnPlacements.register` vs `EntityType.Builder#spawnRestriction` (Fabric), nazwa `ModelLayerRegistry` vs `EntityModelLayerRegistry` - docs pokazują `ModelLayerRegistry`, wiki `EntityModelLayerRegistry`.
3. Tabela Yarn<->Mojang w 1.2 i 4.5 częściowo z pamięci (Linkie API nie odpowiedziało) - zweryfikować w IDE.
4. Numery wersji GeckoLib 4.x dla 1.20.1 i 1.21.1 (Modrinth) - nie pobrano.
5. Czy gałęzie `26.2-MultiLoader`/`26.3-MultiLoader` Naturalist budują wariant Fabric (gradle.properties ma `fabric_api_version`, Modrinth wymienia Fabric - prawdopodobnie tak).
6. Licencja Alex's Mobs (brak pliku LICENSE w repo `1.20`); licencja Exotic Birds i czy kod jest otwarty.
7. Domyślna licencja xeno-canto (CC BY-NC-SA 4.0?) i aktualne zasady API (klucz?) - strona `about/terms` niedostępna dla narzędzia.
8. Szczegóły despawnu ryb (`WATER_AMBIENT`: 64 bl.?) i predykatu `FishEntity::canSpawn`; spawn cost przy generacji chunka.
9. Wartość `creature_spawn_probability` dla konkretnych biomów wanilii (0.1 dla większości - wg wiki).
10. Rzeczywisty koszt CPU Brain vs Goal i GeckoLib przy 300+ encjach - wymaga pomiaru (brak źródła liczbowego).
11. Lithium: aktualna lista opcji `mixin.ai.*` (plik konfiguracyjny przeniesiony).
12. SmartBrainLib: wspierane wersje (README pusty; wiki wymaga osobnych podstron) - sprawdzić Modrinth/CurseForge przed decyzją; alternatywa: własna cienka warstwa nad wanilijnym `Brain`.
13. Czy Serene Seasons udostępnia temperaturę sezonową biomu w API (dla odwilży/zamarzania) - w `api/season` widać tylko `ISeasonState`/`SeasonHelper`; sprawdzić `SeasonHooks`/`sereneseasons.season.SeasonHooks.getBiomeTemperature` (poza pakietem api).
14. Pytania do użytkownika (decyzje): (a) wersja MC (1.20.1 = największa baza modpacków i SS, ale bez wsparcia docs; 26.2 = przyszłościowa, Java 25); (b) czy zwierzęta mają dropować mięso/skóry/poroże (etyka vs gameplay); (c) czy hitboxy mają być w pełni realistyczne (łoś 2,1 bl. wysokości, poroże 1,6 bl. szerokości) kosztem pathfindingu w gęstym lesie; (d) czy dopuszczalna jest twarda zależność od GeckoLib i SmartBrainLib (dwa dodatkowe mody) - alternatywa to znacznie większy nakład; (e) czy zwierzęta mają modyfikować teren (bobry, dziki buchtujące, borsuki kopiące) i jak to ma respektować `mobGriefing`; (f) tempo dorastania młodych i cyklu życia (realistyczne = miesiące gry vs kompromis 20-30 dni); (g) czy polowanie drapieżników ma być widoczne (wilk zabija sarnę) - wpływ na populacje i wrażliwość gracza.

## Źródła

Dokumentacja Fabric / Minecraft:
- https://docs.fabricmc.net/develop/entities/first-entity (Creating your first entity, wersja 26.2)
- https://wiki.fabricmc.net/tutorial:entity (stary tutorial encji, kod Mojang mappings)
- https://fabricmc.net/2026/03/14/261.html (Fabric for Minecraft 26.1)
- https://fabricmc.net/2025/12/05/12111.html (Fabric for Minecraft 1.21.11 - koniec Yarn)
- https://docs.fabricmc.net/develop/porting/mappings/ (Migrating Mappings)
- https://docs.fabricmc.net/develop/porting/fabric-api (Porting to Fabric API 26.1)
- https://docs.fabricmc.net/develop/sounds/custom (Creating Custom Sounds)
- https://maven.fabricmc.net/docs/fabric-api-0.110.0+1.21.1/net/fabricmc/fabric/api/biome/v1/BiomeModifications.html
- https://maven.fabricmc.net/docs/yarn-1.21.4+build.1/net/minecraft/entity/ai/brain/Brain.html
- https://minecraft.wiki/w/Mob_spawning
- https://minecraft.wiki/w/Mob_AI
- https://learn.microsoft.com/en-us/minecraft/creator/reference/content/schemasreference/schemas/minecraftschema_geometry_1.12.0 (schemat geo.json)
- https://docs.papermc.io/paper/reference/spigot-configuration (entity-activation-range, tracking range)

GeckoLib / AzureLib / Blockbench / SmartBrainLib:
- https://wiki.geckolib.com/ oraz źródła wiki: https://github.com/Tslat/Geckolib-Wiki (docs/index.mdx, docs/entities/the-entity-class.mdx, the-entity-renderer.mdx, docs/concepts/geomodels/defaulted-geomodel.mdx, docs/concepts/rendering/renderstates.mdx, docs/concepts/animation/controller/overview.mdx, defaultanimations.mdx, docs/making-models/placing-the-files.mdx, blockbench-plugin-usage.mdx, exporting-the-files.mdx)
- https://github.com/bernie-g/geckolib/wiki (stara wiki GeckoLib 3/4)
- https://modrinth.com/mod/azurelib , https://github.com/AzureDoom/AzureLib
- https://blockbench.net/wiki/docs/bbmodel/ (The .bbmodel format)
- https://gist.github.com/mgerhardy/48adb8a9ccaa2079779ac38a4324fa8e (bbmodel format spec)
- https://github.com/Tslat/SmartBrainLib , https://github.com/Tslat/SmartBrainLib/wiki , https://modrinth.com/mod/smartbrainlib

Kod modów (GitHub, odczyt raw/API 2026-09-21):
- https://github.com/bodhiahn/eager-beavers (Beaver.java, BeaverSpawn.java, geo/animations, gradle.properties; CC0-1.0), https://modrinth.com/project/QNh0xawR
- https://github.com/crispytwig/Naturalist (dawniej starfish-studios/Naturalist; gałęzie 1.21.1-NeoForge, 26.2-MultiLoader; Bear.java, Bird.java, SleepGoal.java, SleepingAnimal.java, ClimbingAnimal.java, LICENSE), https://modrinth.com/mod/naturalist
- https://github.com/AlexModGuy/AlexsMobs (gałąź 1.20; drzewo klas AI, tekstury)
- https://github.com/itsmeow/betteranimalsplus (gałąź 1.19; drzewo klas)
- https://github.com/RayTrace082/untamedwilds (gałąź 1.18.2, GPL-3.0; drzewo klas, README), forki: https://github.com/ACowAdonis/untamed-wilds-au-naturel , https://github.com/Raguto/UntamedWilds-1.21.1
- https://github.com/Glitchfiend/SereneSeasons (gałąź 26.1.2; common/src/main/java/sereneseasons/api/season/SeasonHelper.java, ISeasonState.java, Season.java)
- https://github.com/CaffeineMC/lithium (LGPL-3.0)
- https://www.curseforge.com/minecraft/mc-mods/beaver-mod , https://www.curseforge.com/minecraft/mc-mods/exotic-birds

Spawn / capy:
- https://modrinth.com/mod/proper-mobcap-modifier , https://www.curseforge.com/minecraft/mc-mods/custom-spawns , https://www.curseforge.com/minecraft/mc-mods/spawncapcontrolutility , https://modrinth.com/mod/fabric-per-player-spawns
- https://maven.fabricmc.net/docs/yarn-21w15a+build.2/net/minecraft/entity/SpawnGroup.html

Dźwięki:
- https://en.wikipedia.org/wiki/Xeno-canto (strona https://xeno-canto.org/about/terms niedostępna dla narzędzia)
- https://freesound.org/help/faq/
