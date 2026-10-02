# Ekosystem Fabric i mod Serene Seasons - stan na wrzesień 2026

Data: 2026-09-21 (badanie zakończone 2026-09-22)
Status: ZAKOŃCZONE w ramach budżetu 30 wywołań WebSearch/WebFetch. Fragmenty, których nie udało się zweryfikować, są oznaczone "NIE ZBADANO" lub "NIEPOTWIERDZONE".

## Streszczenie

Najnowszą wydaną wersją Minecraft Java Edition jest 26.3 "Wilderness Bound" z 2026-09-15; od marca 2026 (26.1 "Tiny Takeover") Mojang stosuje schemat numeracji rok.drop.hotfix, wydaje grę z kodem nieobfuskowanym i wymaga Javy 25. Seria 1.21.x zakończyła się na 1.21.11 (grudzień 2025); kluczowe dla lasu bloki wanilijne pojawiły się w 1.21.4 (pale garden, pale oak), 1.21.5 (leaf litter, bush, wildflowers, dry grass, fallen trees) i 26.3 (poplar z liśćmi w 3 losowych kolorach, shelf mushrooms, red shrub, biom dappled forest). Fabric Loader 0.19.5 jest najnowszym stabilnym loaderem; Fabric API ma aktualne buildy dla 26.3 (0.161.0), 1.21.1 (0.116.17) i 1.20.1 (0.92.12) - dwie ostatnie są nadal utrzymywane jako faktyczne LTS. Fabric zdeprecjonował Yarn w 2025 r. i zaleca mapowania Mojang; od 26.1 mapowania w ogóle nie są potrzebne, a szablon fabric-example-mod nie ma już wpisu mappings i celuje w Javę 25. Serene Seasons (licencja All Rights Reserved, 7,0 mln pobrań na Modrinth) ma buildy Fabric dla 1.20.1 (9.1.0.3, 2026-06-14), 1.21.1 (10.1.0.9, 2026-09-05), 1.21.5-1.21.11 oraz 26.1.2/26.2/26.3 (26.1.2.0.7, 2026-09-21); wymaga GlitchCore i Fabric API, a na 1.20.1-1.21.11 także Cloth Config. Publiczne API to pakiet sereneseasons.api.season: SeasonHelper.getSeasonState(Level) zwraca ISeasonState (getSeason, getSubSeason, getTropicalSeason, getDay, getCycleDuration, ...) po obu stronach, a SeasonChangedEvent (GlitchCore) sygnalizuje zmianę sub-sezonu. Temperatura biomu jest korygowana w SeasonHooks o wartość z configu (domyślnie -0.8 w zimie, -0.25 wczesną wiosną i późną jesienią, przycięcie do [-0.5, 2.0]) wyłącznie dla biomów o temperaturze bazowej <= 0.8 i niewykluczonych tagami sereneseasons:blacklisted_biomes / tropical_biomes. Kolory trawy i liści są mieszane trybem overlay z nakładką per sub-sezon (np. MID_AUTUMN foliage 0xEF2121) i odbarwiane zimą (saturacja x0.45), a biomy z tagu lesser_color_change_biomes dostają tylko 25 % zmiany; brzoza ma osobną tabelę kolorów. Uprawy przypisuje się do sezonów tagami bloków/przedmiotów sereneseasons:spring_crops, summer_crops, autumn_crops, winter_crops, year_round_crops (plus greenhouse_glass i unbreakable_infertile_crops), z opcjami fertility (outOfSeasonCropBehavior 0/1/2, undergroundFertilityLevel 48). Domyślny sub-sezon trwa 8 dni (rok = 96 dni MC), startingSubSeason = 1 (wczesna wiosna), sezony działają tylko w minecraft:overworld. GeckoLib (5.5.6 dla 26.3; 4.9.3 dla 1.21.1) i TerraBlender (26.3.0.0.5 dla 26.3; 4.1.0.8 dla 1.21.1) są dostępne na Fabric dla wszystkich rozważanych wersji; TerraBlender jest standardem dodawania biomów obok wanilii, a Serene Seasons działa z nimi automatycznie przez temperaturę i tagi. Rekomendacja: budować na najnowszym dropie 26.x (obecnie 26.3), akceptując portowanie co ok. 3 miesiące, z 1.21.1 jako bezpieczną alternatywą, jeśli priorytetem jest kompatybilność z szerokim ekosystemem modów.

## 1. Minecraft Java Edition - wersje, schemat numeracji, zmiany worldgen/flora 2024-2026

Źródła: minecraft.wiki "Java Edition version history", "Java Edition 26.1", "Java Edition 26.3" (pobrane 2026-09-21).

### 1.1 Aktualna wersja i schemat numeracji

- **Najnowsza wydana wersja Minecraft Java Edition (stan 2026-09-21): 26.3 "Wilderness Bound", wydana 2026-09-15.** (minecraft.wiki/w/Java_Edition_26.3)
- Mojang porzucił schemat `1.MAJOR.MINOR`. Od marca 2026 obowiązuje schemat **`ROK.DROP.HOTFIX`** ("year.drop.hotfix"): 26.1 = pierwszy "drop" roku 2026, 26.1.1 / 26.1.2 = poprawki (hotfixy) do tego dropu, 26.2 = drugi drop itd. (minecraft.wiki/w/Java_Edition_26.1).
- Pierwszym wydaniem w nowym schemacie jest **26.1 "Tiny Takeover" (2026-03-24)**. Jednocześnie od 26.1 gra jest dystrybuowana jako **kod w pełni nieobfuskowany** (bez wariantu obfuskowanego) - to fundamentalna zmiana dla modowania (patrz 2.1).
- Wymagana Java: od 26.1 **Java SE 25 (LTS)** (wcześniej Java 21 od 1.20.5 do 1.21.11; Java 17 dla 1.20.1-1.20.4).
- Kadencja: dropy co ok. 3 miesiące (26.1 marzec, 26.2 czerwiec, 26.3 wrzesień 2026); należy spodziewać się 26.4 ok. grudnia 2026.

### 1.2 Lista wydań z ostatnich ~2 lat

Daty wydań 1.21.x poniżej pochodzą z wiedzy własnej (zgodne z minecraft.wiki wg stanu wiedzy do połowy 2026) - streszczenie strony "version history" potwierdziło tylko 1.21 (2024-06-13) oraz wszystkie daty 26.x. Oznaczone "(wiki)" = potwierdzone bezpośrednio w tym badaniu.

| Wersja | Nazwa | Data wydania | Najważniejsze zmiany worldgen / flora / fauna |
|---|---|---|---|
| 1.21 | Tricky Trials | 2024-06-13 (wiki) | Trial chambers, breeze, bogged, armadillo, wolf variants (1.20.5) - brak zmian flory |
| 1.21.1 | (hotfix) | 2024-08-08 | poprawki; **najpopularniejsza baza modpacków 1.21** |
| 1.21.2 / 1.21.3 | Bundles of Bravery | 2024-10-22 / 2024-10-23 | bundle, salmon variants, zmiany zachowania zwierząt (np. świnie/kurczaki) - drobne |
| 1.21.4 | The Garden Awakens | 2024-12-03 | **Pale Garden** (biom wariant dark forest), **pale oak** (nowe drewno), pale moss block/carpet, pale hanging moss, eyeblossom, creaking + creaking heart, resin |
| 1.21.5 | Spring to Life | 2025-03-25 | **leaf litter** (ściółka liściasta, blok "segmentowy" 1-4), **wildflowers** (kwiaty łąkowe, segmentowe), **bush** (krzak), **firefly bush**, **short dry grass / tall dry grass**, cactus flower, **fallen trees** (powalone pnie dębu, brzozy, świerka, dżungli z grzybami/pnączami), warm/cold warianty krowy, świni, kurczaka (jaja niebieskie/brązowe), dźwięki otoczenia biomów (ambient) |
| 1.21.6 | Chase the Skies | 2025-06-17 | happy ghast, dried ghast, locator bar, zmiany nieistotne dla flory |
| 1.21.7 / 1.21.8 | (hotfixy) | 2025-06-30 / 2025-07-17 | poprawki; 1.21.8 - popularna baza pod 1.21.6+ |
| 1.21.9 | The Copper Age | 2025-09-30 | copper golem, copper tools/armor, shelf (półka), copper chest, lightning rod nadaje się do przewodzenia |
| 1.21.10 | (hotfix) | 2025-10-07 | poprawki |
| 1.21.11 | Mounts of Mayhem | 2025-12-09 | nautilus (mount), zombie nautilus, camel husk, spear, zmiany wierzchowców; **ostatnia wersja serii 1.21.x** |
| 26.1 | Tiny Takeover | 2026-03-24 (wiki) | **Java 25**, kod nieobfuskowany, nowy schemat wersji; przebudowa modeli/tekstur młodych mobów (wszystkie gatunki), golden dandelion, data-driven warianty dźwięków (cat/pig/cow); nowe tagi bloków `#supports_vegetation`, `#supports_crops`, `#supports_stem_crops`, rozbicie `#dirt` na `#dirt`, `#mud`, `#moss_blocks`, `#grass_blocks` + `#substrate_overworld`; resource pack 84.0, data pack 101.1 |
| 26.1.1 / 26.1.2 | (hotfixy) | 2026-04-01 / 2026-04-09 (wiki) | poprawki; **26.1.2 to gałąź bazowa Serene Seasons 26.x** |
| 26.2 | Chaos Cubed | 2026-06-16 (wiki) | NIE ZBADANO szczegółów (brak budżetu) - wg nazwy zmiany mechaniczne; brak informacji o florze |
| 26.3 | Wilderness Bound | **2026-09-15 (wiki)** | **dappled forest** (nowy jesienny biom, generuje się w pobliżu zimnych regionów), **poplar** (topola) - nowy pełny zestaw drewna, liście w 3 kolorach (orange/red/yellow) losowanych przy generacji + sadzonki i cząsteczki spadających liści per kolor, **red shrub** (czerwony krzew), **shelf mushrooms** (huby / grzyby półkowe na pniach topoli, 2 rozmiary, bone meal), leaf litter w nowym biomie, sporadyczne świerki; abandoned camps (17 biomów); wool/concrete stairs+slabs, straw bed, cushion (encja); data pack 121.0, resource pack 97.1; nowe komponenty `minecraft:compostable`, `minecraft:cooking_fuel`, `minecraft:brewing_fuel`, `minecraft:mob_visibility`, `minecraft:villager_food`, `minecraft:block_transformer`, `minecraft:attack_animation`; komendy `/compute`, `/posteffect` |

### 1.3 Co z tego wynika dla "polskiego lasu"

- Wanilia od 1.21.5 ma już gotowe bloki na **runo i ściółkę**: `leaf_litter`, `bush`, `wildflowers`, `short_dry_grass`, `tall_dry_grass`, `firefly_bush`, `fallen_*_tree` (feature'y worldgen). Od 26.3 - `shelf mushroom` (huby) i `red shrub`, oraz **wzorzec drzewa o losowym kolorze liści** (topola). Budowanie na wersji < 1.21.5 oznacza reimplementację tego wszystkiego.
- Od 26.1 tagi `#supports_vegetation`, `#supports_crops`, `#substrate_overworld` ułatwiają dodawanie własnych podłoży (np. własna "gleba leśna", "ściółka iglasta", "torf") tak, aby wanilijne rośliny na nich rosły.
- Od 26.1 zmieniono modele **wszystkich** młodych zwierząt - jeśli mod dodaje własne modele zwierząt (wymaganie użytkownika), na 26.x trzeba przygotować także wersje młodych.
- Cykl 3-miesięczny dropów oznacza konieczność portowania moda ~4 razy w roku, jeśli chcemy "być na najnowszej".

## 2. Fabric Loader, Fabric API, Loom, JDK - macierz wersji od 1.20.1

Źródła: `https://meta.fabricmc.net/v2/versions` i `/v2/versions/loader` (pobrane 2026-09-21), Modrinth API dla `fabric-api` (pobrane 2026-09-21).

- **Fabric Loader**: najnowsza stabilna **0.19.5** (jedyna oznaczona `stable: true` w meta; 0.19.0-0.19.4 i 0.18.x to wydania niestabilne/pośrednie). Loader jest niezależny od wersji MC - ta sama wersja loadera obsługuje wszystkie wersje MC (od 1.14 wzwyż).
- **Wersje gry oznaczone jako stabilne w meta Fabric (10 najnowszych)**: 26.3, 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11, 1.21.10, 1.21.9, 1.21.8, 1.21.7.

| Wersja MC | Fabric API (najnowsza) | Data publikacji Fabric API | Yarn (najnowszy build wg meta) | JDK (min.) |
|---|---|---|---|---|
| 26.3 | 0.161.0+26.3 | 2026-09-18 | (nie odczytano z meta - do sprawdzenia) | 25 (wg minecraft.wiki: "Minimum Java SE 25") |
| 26.2 | 0.161.0+26.2 | 2026-09-18 | (nie odczytano) | 25? (DO POTWIERDZENIA) |
| 26.1.2 | 0.155.3+26.1.2 | 2026-09-07 | (nie odczytano) | DO POTWIERDZENIA (21 lub 25) |
| 1.21.11 | 0.141.6+1.21.11 | 2026-07-28 | 1.21.11+build.6 | 21 |
| 1.21.10 | 0.135.0+1.21.10 | 2025-10-08 | 1.21.10+build.3 | 21 |
| 1.21.8 | 0.131.0+1.21.8 | 2025-08-03 | 1.21.8+build.1 | 21 |
| 1.21.5 | 0.128.2+1.21.5 | 2025-08-08 | 1.21.5+build.1 | 21 |
| 1.21.4 | 0.119.4+1.21.4 | 2025-08-08 | (nie odczytano) | 21 |
| 1.21.1 | 0.116.17+1.21.1 | **2026-09-01** | (nie odczytano) | 21 |
| 1.20.1 | 0.92.12+1.20.1 | **2026-09-01** | (nie odczytano) | 17 |

Uwagi:
- Fabric API dla 1.20.1 i 1.21.1 nadal dostaje aktualizacje (obie 2026-09-01) - są to faktyczne "LTS" ekosystemu Fabric.
- JDK (potwierdzone): Minecraft 1.20.1-1.20.4 wymaga **Java 17**; 1.20.5-1.21.11 wymaga **Java 21** (potwierdzone też w fabric.mod.json Serene Seasons: `"java": ">=21"`); **od 26.1 wymagana jest Java 25** (minecraft.wiki/w/Java_Edition_26.1: "first version requiring Java 25, upgraded from Java 21"). Szablon `fabric-example-mod` (gałąź master, 2026-09) ma `sourceCompatibility = JavaVersion.VERSION_25`.
- Yarn dla 26.x: w `meta.fabricmc.net/v2/versions` nie odczytano wpisów mappingów dla 26.1+ - to spójne z ogłoszeniem Fabric, że od 26.1 Yarn nie jest utrzymywany (kod gry jest nieobfuskowany, mapowania nie są potrzebne). Dla 1.21.1 i 1.20.1 Yarn istnieje (np. `1.21.1+build.3`, `1.20.1+build.10` - wartości z pamięci, NIEPOTWIERDZONE w tym badaniu).
- **Fabric Loom**: `fabric-example-mod/build.gradle` używa `id 'net.fabricmc.fabric-loom' version "${loom_version}"` - konkretna wartość jest w `gradle.properties`, którego NIE POBRANO (brak budżetu). Z wiedzy własnej: Loom 1.10.x obsługuje 1.21.4-1.21.5, Loom 1.11.x - 1.21.6-1.21.8, Loom 1.12/1.13 - 1.21.9+; dla 26.x wymagany jest nowy Loom (1.14+?) obsługujący nieobfuskowane jary. DO POTWIERDZENIA przed rozpoczęciem projektu (sprawdzić `https://github.com/FabricMC/fabric-example-mod/blob/master/gradle.properties` oraz generator na `https://fabricmc.net/develop/template/`).
- Identyfikator artefaktu Loom zmienił się z `fabric-loom` na `net.fabricmc.fabric-loom` (widoczne w aktualnym build.gradle szablonu).

### 2.1 Mapowania: Yarn vs oficjalne mapowania Mojang

Źródła: wyniki wyszukiwania (docs.fabricmc.net/develop/porting/mappings/, fabricmc.net/2026/03/14/261.html "Fabric for Minecraft 26.1", wiki.fabricmc.net/tutorial:mappings, github.com/FabricMC/yarn) - pobrane 2026-09-21.

Ustalenia:
- **Yarn został zdeprecjonowany przez Fabric w 2025 r.** Dokumentacja Fabric (docs.fabricmc.net) i implementacja referencyjna używają od tego czasu **oficjalnych mapowań Mojang ("Mojmap")** jako domyślnych. Fabric oficjalnie zaleca migrację istniejących modów z Yarn na Mojmap (strona "Migrating Mappings").
- **Od Minecraft 26.1 gra jest wydawana z nieobfuskowanym kodem**, a projekt Fabric podjął decyzję, by od tej wersji nie utrzymywać już mapowań firm trzecich (Yarn). W praktyce dla 26.x nie ma "wyboru mapowań" - nazwy klas/metod są nazwami Mojang wprost.
- Ograniczenie Mojmap (dla 1.21.x): brak nazw parametrów i Javadoc - stąd popularne nakładanie **Parchment** (parchmentmc.org) na Mojmap. Dla 26.x nazwy parametrów pochodzą wprost z nieobfuskowanego kodu (do sprawdzenia, czy Mojang publikuje też Javadoc).
- Zalecenie na 2026: **pisać nowy kod od razu w nazewnictwie Mojang** (np. `net.minecraft.world.level.Level`, `net.minecraft.world.level.biome.Biome`, `Holder<Biome>`), niezależnie od wyboru wersji MC. Serene Seasons, TerraBlender, GeckoLib i BOP (Glitchfiend/Bebop) używają Mojmap od dawna (widać to w kodzie `SeasonHelper`: `net.minecraft.world.level.Level`), więc integracja z nimi w Mojmap nie wymaga tłumaczenia nazw.
- Konsekwencja dla wyboru 1.20.1: większość poradników i modów 1.20.1 na Fabric używa Yarn; kod pisany w Mojmap na 1.20.1 jest w pełni możliwy (`loom.officialMojangMappings()`), ale przykłady z internetu trzeba "tłumaczyć".

### 2.2 Szablon projektu (template generator, example-mod)

- Generator: `https://fabricmc.net/develop/template/` (aplikacja JS - nie dało się jej sparsować przez WebFetch; generuje projekt z wybraną wersją MC, nazwą, pakietem, opcjami: Kotlin, split client/common sources, data generation, mojang mappings).
- Repozytorium referencyjne: `https://github.com/FabricMC/fabric-example-mod` (gałąź `master` = najnowsza wersja MC). Aktualny `build.gradle` (pobrany 2026-09-21):

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
    id 'maven-publish'
}
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    // Fabric API. This is technically optional, but you probably want it anyway.
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
}
sourceCompatibility = JavaVersion.VERSION_25
targetCompatibility = JavaVersion.VERSION_25
```

- Istotne: w bloku `dependencies` **nie ma już wpisu `mappings`** (ani Yarn, ani `loom.officialMojangMappings()`) - potwierdza to, że dla 26.x mapowania nie są stosowane. Dla 1.21.1 / 1.20.1 wpis `mappings loom.officialMojangMappings()` (lub `mappings "net.fabricmc:yarn:${yarn_mappings}:v2"`) jest nadal potrzebny.
- Właściwości w `gradle.properties`: `minecraft_version`, `loader_version`, `fabric_api_version` (uwaga: nowa nazwa; starsze szablony używały `fabric_version`), `loom_version`, `mod_version`, `maven_group`, `archives_base_name`.
- Struktura projektu Serene Seasons (multi-loader `common/` + `fabric/` + `neoforge/` + `forge/`) to dobry wzorzec, jeśli w przyszłości chcemy wydać też na NeoForge, ale wymaga GlitchCore lub własnej warstwy abstrakcji (Architectury). Dla projektu "tylko Fabric" wystarczy jednomodułowy szablon.

## 3. Serene Seasons - dostępne buildy Fabric, zależności, licencja

Źródło: Modrinth API `https://api.modrinth.com/v2/project/serene-seasons/version` (pobrane 2026-09-21) oraz repozytorium GitHub Glitchfiend/SereneSeasons.

| Wersja MC | Najnowszy build Fabric | Data publikacji | Zależności (Modrinth) |
|---|---|---|---|
| 26.3 | 26.1.2.0.7 | 2026-09-21 | Fabric API |
| 26.2 | 26.1.2.0.5 | 2026-08-31 | Fabric API |
| 26.1.2 | 26.1.2.0.7 | 2026-08-31 | Fabric API |
| 1.21.11 | 21.11.0.0 | 2025-12-14 | Fabric API, Cloth Config |
| 1.21.10 | 21.10.0.0 | 2025-10-19 | Fabric API, Cloth Config |
| 1.21.9 | 21.9.0.0 | 2025-10-04 | Fabric API, Cloth Config |
| 1.21.8 | 21.8.0.0 | 2025-07-26 | Fabric API, Cloth Config |
| 1.21.7 | 21.7.0.0 | 2025-07-09 | Fabric API, Cloth Config |
| 1.21.6 | 21.6.0.0 | 2025-06-22 | Fabric API, Cloth Config |
| 1.21.5 | 10.5.0.5 | 2025-07-09 | Fabric API, Cloth Config |
| 1.21.1 | 10.1.0.9 | **2026-09-05** | Fabric API, Cloth Config |
| 1.20.1 | 9.1.0.3 | **2026-06-14** | Fabric API, Cloth Config |

Uwagi:
- Schemat numeracji Serene Seasons: `MC-major.MC-minor.X.Y` (np. 21.8.0.0 dla 1.21.8), starsze: 10.x dla 1.21.x, 9.x dla 1.20.1; dla MC 26.x: `26.1.2.0.x`.
- Wersje 1.20.1 i 1.21.1 są nadal aktywnie utrzymywane (wydania w czerwcu i wrześniu 2026) - to "LTS" w praktyce.
- Repozytorium GitHub: gałąź domyślna `26.1.2`, ok. 423 commity, 294 gwiazdki, 112 forków, 85 otwartych issues. Struktura multi-loader: `common/`, `fabric/`, `forge/`, `neoforge/`.
- **Licencja: "All Rights Reserved"** (Modrinth: `LicenseRef-All-Rights-Reserved`; plik `LICENSE` w repo istnieje, treści nie pobrano). Oznacza to: **nie wolno kopiować kodu Serene Seasons do naszego moda ani go redystrybuować**; wolno natomiast używać publicznego API (`sereneseasons.api.*`) jako zależności compile-time (typowa praktyka w ekosystemie; Glitchfiend publikuje artefakty na własnym Maven: `https://maven.minecraftforge.net` / `https://maven.glitchfiend.com` - DO POTWIERDZENIA, którego repozytorium używać w 2026).
- **Zależność GlitchCore: potwierdzona** w `fabric.mod.json` gałęzi 26.1.2 (`"glitchcore": ">=${glitchcore_version}"`) oraz w opisie na Modrinth: "Versions 1.20.4+ require GlitchCore (also mandatory for 1.20.1 on mod version 9.1.0.0+)". Modrinth w metadanych wersji pokazuje tylko Fabric API i Cloth Config (dla 1.21.x), ale GlitchCore jest zależnością twardą we wszystkich wspieranych wersjach.
- Cloth Config: wymagany przez buildy 1.20.1-1.21.11 (metadane Modrinth); dla 26.x nie jest już wymieniany - prawdopodobnie config przeniesiono w całości do GlitchCore (DO POTWIERDZENIA).
- Statystyki Modrinth (2026-09-21): **7 010 370 pobrań**, 2 725 obserwujących, kategorie: Food, Game Mechanics; loadery: Fabric, Forge, NeoForge; pierwsza publikacja na Modrinth 2023-11-19; ostatnia aktualizacja 2026-09-21.
- Pełna lista wersji MC wspieranych na Modrinth (wszystkie loadery): 1.7.10, 1.12.2, 1.14.4, 1.15.1, 1.15.2, 1.16.4, 1.16.5, 1.17.1, 1.18.1, 1.18.2, 1.19, 1.19.2, 1.19.3, 1.19.4, 1.20, 1.20.1, 1.20.2, 1.20.4, 1.20.6, 1.21, 1.21.1, 1.21.3-1.21.11, 26.1.1, 26.1.2, 26.2, 26.3. Uwaga: dla 1.7.10/1.12.2 buildy Fabric nie istnieją (tylko Forge).
- Wiki projektu: `https://github.com/Glitchfiend/SereneSeasons/wiki` (nie pobrano - brak budżetu; wg wyników wyszukiwania zawiera opis configu i tagów).
- Konkurencyjny mod "Fabric Seasons" (lucaargolo) jest porzucony - issue #187 "This mod is no longer needed" wskazuje na Serene Seasons jako następcę na Fabric.

## 4. API Serene Seasons

### 4.1 Pakiet sereneseasons.api - klasy i sygnatury

Źródło: gałąź `26.1.2` repozytorium `Glitchfiend/SereneSeasons` (drzewo pobrane przez GitHub API 2026-09-21). Pełna lista plików pakietu `common/src/main/java/sereneseasons/api/`:

- `api/SSBlockEntities.java`, `api/SSBlocks.java`, `api/SSGameRules.java`, `api/SSItems.java`
- `api/season/ISeasonColorProvider.java`
- `api/season/ISeasonState.java`
- `api/season/Season.java`
- `api/season/SeasonChangedEvent.java`
- `api/season/SeasonHelper.java`

Pozostałe kluczowe pakiety (nie-API, ale istotne dla zrozumienia mechaniki):
- `season/`: `RandomUpdateHandler`, `SeasonColorHandlers`, `SeasonHandler`, `SeasonHandlerClient`, `SeasonHooks`, `SeasonSavedData`, `SeasonTime`, `SeasonalCropGrowthHandler`
- `config/`: `FertilityConfig`, `SeasonsConfig`
- `init/`: `ModAPI`, `ModBlockEntities`, `ModBlocks`, `ModClient`, `ModConfig`, `ModCreativeTab`, `ModFertility`, `ModGameRules`, `ModItems`, `ModPackets`, `ModTags`
- `mixin/`: `MixinBiome`, `MixinBlockStateBase`, `MixinLevel`, `MixinServerLevel`; `mixin/client/`: `MixinBiomeClient`, `MixinRangeSelectItemModelProperties`, `MixinSelectItemModelProperties`, `MixinWeatherEffectRenderer`
- `util/`: `Color`, `SeasonColorUtil`
- `network/SyncSeasonCyclePacket`, `command/{CommandGetSeason, CommandSetSeason, SeasonArgument, SeasonCommands}`
- `block/SeasonSensorBlock`, `item/CalendarItem`, `item/CalendarType`
- Fabric: `fabric/src/main/java/sereneseasons/fabric/core/SereneSeasonsFabric.java` (jedyny plik specyficzny dla Fabric - reszta przez GlitchCore)
- Zasoby: `common/src/main/resources/sereneseasons.mixins.json`, `sereneseasons.accesswidener`, `fabric/src/main/resources/sereneseasons.fabric.mixins.json`

#### SeasonHelper (gałąź 26.1.2, plik `api/season/SeasonHelper.java`) - kod dosłowny

```java
package sereneseasons.api.season;

import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public class SeasonHelper
{
    public static ISeasonDataProvider dataProvider;

    /** Obtains data about the state of the season cycle in the world.
     *  This works both on the client and the server. */
    public static ISeasonState getSeasonState(Level level)
    {
        ISeasonState data;
        if (!level.isClientSide())
            data = dataProvider.getServerSeasonState(level);
        else
            data = dataProvider.getClientSeasonState(level);
        return data;
    }

    /** Check whether a biome uses tropical seasons. */
    public static boolean usesTropicalSeasons(Holder<Biome> biome)
    {
        return dataProvider.usesTropicalSeasons(biome);
    }

    public interface ISeasonDataProvider
    {
        ISeasonState getServerSeasonState(Level level);
        ISeasonState getClientSeasonState(Level level);
        boolean usesTropicalSeasons(Holder<Biome> key);
    }
}
```

Wniosek: `SeasonHelper.getSeasonState(Level)` działa po obu stronach (klient/serwer) - po stronie klienta korzysta ze stanu zsynchronizowanego pakietem `SyncSeasonCyclePacket`.

#### Season (gałąź 26.1.2, plik `api/season/Season.java`) - struktura

```java
public enum Season {
    SPRING, SUMMER, AUTUMN, WINTER;

    public enum SubSeason implements ISeasonColorProvider {
        EARLY_SPRING, MID_SPRING, LATE_SPRING,
        EARLY_SUMMER, MID_SUMMER, LATE_SUMMER,
        EARLY_AUTUMN, MID_AUTUMN, LATE_AUTUMN,
        EARLY_WINTER, MID_WINTER, LATE_WINTER;
        // pola: Season season; int grassOverlay; float grassSaturationMultiplier (domyślnie -1);
        //       int foliageOverlay; float foliageSaturationMultiplier (domyślnie -1); int birchColor
        // gettery: getSeason(), getGrassOverlay(), getGrassSaturationMultiplier(),
        //          getFoliageOverlay(), getFoliageSaturationMultiplier(), getBirchColor(), getSerializedName()
    }

    public enum TropicalSeason implements ISeasonColorProvider {
        EARLY_DRY, MID_DRY, LATE_DRY, EARLY_WET, MID_WET, LATE_WET;
        // te same pola kolorów i gettery co SubSeason (bez getSerializedName)
    }
}
```

Dokładne wartości hex nakładek kolorów per sub-sezon: patrz sekcja 4.5.

#### ISeasonState (gałąź 26.1.2, plik `api/season/ISeasonState.java`) - kod dosłowny (bez javadoc)

```java
package sereneseasons.api.season;

public interface ISeasonState
{
    int getDayDuration();          // długość dnia w tickach (normalnie 24000)
    int getSubSeasonDuration();    // długość sub-sezonu w tickach
    int getSeasonDuration();       // długość sezonu w tickach
    int getCycleDuration();        // długość całego cyklu ("roku") w tickach
    int getSeasonCycleTicks();     // ticki, które upłynęły w bieżącym cyklu
    int getDay();                  // numer dnia
    Season.SubSeason getSubSeason();
    Season getSeason();
    Season.TropicalSeason getTropicalSeason();
}
```

Przykład użycia (obie strony):

```java
ISeasonState state = SeasonHelper.getSeasonState(level);
Season season = state.getSeason();               // SPRING / SUMMER / AUTUMN / WINTER
Season.SubSeason sub = state.getSubSeason();     // np. MID_AUTUMN
float progress = (float) state.getSeasonCycleTicks() / state.getCycleDuration(); // 0..1 w "roku"
```

#### SeasonChangedEvent (gałąź 26.1.2, plik `api/season/SeasonChangedEvent.java`)

```java
public class SeasonChangedEvent<T> extends glitchcore.event.Event
{
    private final Level level;
    private final T prevSeason;
    private final T newSeason;
    // prywatny konstruktor
    public Level getLevel();
    public T getPrevSeason();
    public T getNewSeason();

    /** Fired when the current sub season changes */
    public static class Standard extends SeasonChangedEvent<Season.SubSeason> { ... }
    /** Fired when the current tropical season changes */
    public static class Tropical extends SeasonChangedEvent<Season.TropicalSeason> { ... }
}
```

Zdarzenie jest oparte o system zdarzeń **GlitchCore** (`glitchcore.event.Event`), a nie o Fabric API `Event<T>`. Subskrypcja: `glitchcore.event.EventManager.addListener(SeasonChangedEvent.Standard.class, event -> {...})` - dokładna sygnatura `EventManager` NIE ZOSTAŁA ZWERYFIKOWANA w kodzie GlitchCore (brak budżetu); nazwa klasy pochodzi ze streszczenia pliku i wiedzy o GlitchCore.

#### ISeasonColorProvider

Interfejs implementowany przez `Season.SubSeason` i `Season.TropicalSeason`; metody (wywnioskowane z getterów obu enumów, plik nie był pobrany osobno): `int getGrassOverlay()`, `float getGrassSaturationMultiplier()`, `int getFoliageOverlay()`, `float getFoliageSaturationMultiplier()`, `int getBirchColor()`.

#### Pozostałe klasy API

- `api/SSBlocks.java` - stałe bloków Serene Seasons (m.in. `SEASON_SENSOR`), `api/SSItems.java` (m.in. `CALENDAR`), `api/SSBlockEntities.java`, `api/SSGameRules.java` (reguła gry - nazwa NIEPOTWIERDZONA; w starszych wersjach `doSeasonCycle`).

### 4.2 Modyfikacja temperatury biomu (SeasonHooks, mixiny)

Źródło: `common/src/main/java/sereneseasons/season/SeasonHooks.java` (gałąź 26.1.2, pobrane 2026-09-21). Publiczne sygnatury statyczne (wywoływane z mixinów `MixinBiome`, `MixinLevel`, `MixinServerLevel`, `MixinBiomeClient`, `MixinWeatherEffectRenderer`):

```java
public static boolean shouldSnowHook(Biome biome, LevelReader levelReader, BlockPos pos, int seaLevel)
public static boolean shouldFreezeWarmEnoughToRainHook(Biome biome, BlockPos pos, int seaLevel, LevelReader levelReader)
public static boolean isRainingAtHook(Level level, BlockPos position)
public static Biome.Precipitation getPrecipitationAtTickIceAndSnowHook(LevelReader level, Biome biome, BlockPos pos, int seaLevel)
public static boolean coldEnoughToSnowSeasonal(LevelReader level, BlockPos pos, int seaLevel)
public static boolean warmEnoughToRainSeasonal(LevelReader level, BlockPos pos, int seaLevel)
public static float getBiomeTemperature(Level level, Holder<Biome> biome, BlockPos pos, int seaLevel)
public static float getBiomeTemperatureInSeason(Season.SubSeason subSeason, Holder<Biome> biome, BlockPos pos, int seaLevel)
public static Biome.Precipitation getPrecipitationAtSeasonal(Level level, Holder<Biome> biome, BlockPos pos, int seaLevel)
```

Mechanika (na podstawie kodu):
- Temperatura sezonowa = temperatura bazowa biomu (`biome.value().getBaseTemperature()`, z uwzględnieniem wysokości jak w wanilii) + `ModConfig.seasons.getSeasonProperties(subSeason).biomeTempAdjustment()`; wynik jest **przycinany do zakresu [-0.5F, 2.0F]**.
- Korekta jest stosowana **tylko dla biomów o temperaturze bazowej `<= 0.8F`** (czyli nie dla pustyń, sawann, dżungli, badlands - te mają 0.8-2.0). Biomy o temp. 0.8 (np. las, plains) są jeszcze objęte.
- Biomy w tagu `sereneseasons:tropical_biomes` nie dostają korekty temperatury; zamiast tego korzystają z cyklu `Season.TropicalSeason` (pory sucha/deszczowa); w `MID_DRY` nie ma opadów.
- Biomy w tagu `sereneseasons:blacklisted_biomes` - pełny powrót do logiki waniliowej.
- Śnieg pada, gdy `coldEnoughToSnowSeasonal(...)` (temperatura sezonowa < 0.15F - próg waniliowy); deszcz gdy `getBiomeTemperature(...) >= 0.15F`.
- Wartości `biome_temp_adjustment` per sub-sezon są w konfiguracji `SeasonsConfig.SeasonProperties` (patrz 4.6) - a więc **konfigurowalne przez użytkownika**, nie stałe w kodzie.

Konsekwencja dla moda "polskiego": wanilijne biomy o temp. 0.8 (plains, forest, flower_forest, birch_forest, dark_forest) i 0.7 (swamp), 0.25 (taiga), 0.5-0.6 (meadow, itd.) dostaną zimową korektę. Własne biomy naszego moda muszą mieć temperaturę bazową <= 0.8, by Serene Seasons je "sezonował" - warto trzymać się 0.6-0.8 dla nizin i 0.2-0.5 dla gór.

### 4.3 Uprawy i pory roku (tagi crops, fertility, config)

Źródła: `init/ModTags.java`, `config/FertilityConfig.java`, `season/SeasonalCropGrowthHandler.java`, `init/ModFertility.java`, pliki tagów w `common/src/main/resources/data/sereneseasons/tags/` (gałąź 26.1.2).

Tagi bloków (rejestr `minecraft:block`) i przedmiotów (`minecraft:item`) o tych samych nazwach:

| Tag | Znaczenie |
|---|---|
| `sereneseasons:spring_crops` | uprawa płodna wiosną |
| `sereneseasons:summer_crops` | uprawa płodna latem |
| `sereneseasons:autumn_crops` | uprawa płodna jesienią |
| `sereneseasons:winter_crops` | uprawa płodna zimą |
| `sereneseasons:year_round_crops` | uprawa płodna cały rok |
| `sereneseasons:greenhouse_glass` (tylko block) | blok "szkła szklarniowego" - pod nim uprawy rosną niezależnie od sezonu |
| `sereneseasons:unbreakable_infertile_crops` (tylko block) | uprawy, których nie niszczy tryb `outOfSeasonCropBehavior = 2` |

Pliki JSON tagów istnieją w repozytorium: `data/sereneseasons/tags/block/{autumn,spring,summer,winter,year_round}_crops.json`, `greenhouse_glass.json`, `unbreakable_infertile_crops.json` oraz `tags/item/{...}_crops.json`. Oznacza to, że **nasz mod może dodać własne krzewy/uprawy do tych tagów zwykłym plikiem data packu** (`data/sereneseasons/tags/block/summer_crops.json` z `"replace": false`).

`FertilityConfig` (plik `fertility.toml` / JSON w folderze config) - opcje:

| Opcja | Domyślnie | Opis (z kodu) |
|---|---|---|
| `seasonalCrops` | `true` | Czy uprawy są w ogóle zależne od pór roku |
| `cropTooltips` | `true` | Tooltipy na przedmiotach z listą płodnych sezonów (tylko dla otagowanych) |
| `outOfSeasonCropBehavior` | `0` | 0 = rosną wolno, 1 = nie rosną, 2 = niszczą się przy próbie wzrostu |
| `undergroundFertilityLevel` | `48` | Maksymalna wysokość Y, do której uprawy poza sezonem są płodne "pod ziemią" (zakres `DimensionType.MIN_Y`..MAX) |

Tag biomów `sereneseasons:infertile_biomes` - biomy, w których uprawy nigdy nie są płodne (np. domyślnie wanilijne biomy bardzo zimne - do potwierdzenia zawartości JSON).

NIE ZBADANO (brak budżetu): dokładny algorytm `SeasonalCropGrowthHandler` (jak "rosną wolno" jest realizowane - prawdopodobnie anulowanie części random ticków) oraz domyślna zawartość plików JSON tagów crops (które wanilijne uprawy są w którym sezonie).

### 4.4 Tagi biomów

Źródło: `init/ModTags.java` (gałąź 26.1.2) - rejestr `minecraft:worldgen/biome`, pliki `common/src/main/resources/data/sereneseasons/tags/worldgen/biome/*.json`:

| Stała w ModTags | Tag | Efekt |
|---|---|---|
| `BLACKLISTED_BIOMES` | `sereneseasons:blacklisted_biomes` | Biom całkowicie pomijany: brak korekty temperatury, brak zmian kolorów, wanilijna pogoda |
| `INFERTILE_BIOMES` | `sereneseasons:infertile_biomes` | Uprawy nigdy nie są płodne |
| `LESSER_COLOR_CHANGE_BIOMES` | `sereneseasons:lesser_color_change_biomes` | Kolor sezonowy mieszany z oryginalnym w proporcji 25 % sezonowy / 75 % oryginalny (`mixColours(newColour, originalColour, 0.75F)`) |
| `TROPICAL_BIOMES` | `sereneseasons:tropical_biomes` | Zamiast 4 pór roku: cykl DRY/WET (`Season.TropicalSeason`), brak korekty temperatury |

UWAGA: w treści zadania tag nazwano `less_color_change_biomes`; w aktualnym kodzie nazywa się **`lesser_color_change_biomes`**.

Dla polskich biomów naszego moda: żadnego z nich nie tagujemy jako tropical; ewentualnie biomy iglaste (bory sosnowe/świerkowe) można dodać do `lesser_color_change_biomes`, bo Serene Seasons zmienia kolor liści (foliage color) także dla iglaków, co dla sosny/świerka jest nierealistyczne - lepszym rozwiązaniem jest jednak własny color provider dla naszych liści iglastych (patrz 4.5).

### 4.5 Kolory liści i trawy

Źródła: `util/SeasonColorUtil.java`, `season/SeasonColorHandlers.java`, `api/season/Season.java`, `api/season/ISeasonColorProvider.java` (gałąź 26.1.2).

Algorytm `SeasonColorUtil.applySeasonalGrassColouring(...)` / `applySeasonalFoliageColouring(...)` (identyczna logika):
1. Jeśli biom jest w `blacklisted_biomes` lub wymiar nie jest na whiteliście -> zwróć oryginalny kolor.
2. Pobierz `overlay` (int RGB) i `saturationMultiplier` (float) z `ISeasonColorProvider` (czyli z bieżącego `SubSeason` lub `TropicalSeason`).
3. Jeśli w configu wyłączono `changeGrassColor`/`changeFoliageColor` -> użyj wartości `MID_SUMMER`.
4. Jeśli overlay != `0xFFFFFF` -> `overlayBlend(originalColour, overlay)` (tryb mieszania "overlay" per kanał RGB: dla kanału podkładu < 128 mnożenie, inaczej "screen" - klasyczny overlay z Photoshopa).
5. Jeśli biom jest w `lesser_color_change_biomes` -> `mixColours(newColour, originalColour, 0.75F)` (75 % oryginału).
6. Jeśli `saturationMultiplier != -1` -> `saturateColour(colour, multiplier)` (konwersja do HSV, mnożenie S, powrót do RGB).

Metody pomocnicze w `SeasonColorUtil`: `multiplyColours`, `overlayBlend`, `overlayBlendChannel`, `mixColours(a, b, ratio)`, `saturateColour(colour, multiplier)`.

Kolor brzozy (`getBirchColor()` w `SubSeason`) jest obsługiwany osobno (opcja `changeBirchColor`) - w wanilii liście brzozy mają stały kolor bez tintu biomu, więc Serene Seasons nadpisuje go własną wartością per sub-sezon. **To istotne dla naszego moda**: liście nowych gatunków (dąb, buk, grab, lipa, klon, jesion, olcha, topola, wierzba, sosna, świerk, jodła, modrzew) będą "sezonowane" tylko, jeśli używają wanilijnego `FoliageColor`/`GrassColor` (tint biomu) lub jeśli sami zaimplementujemy własne providery czytające `SeasonHelper.getSeasonState(level).getSubSeason()` i jego `getFoliageOverlay()`.

#### Dokładne wartości nakładek kolorów (gałąź 26.1.2, `Season.java`, cytat z kodu)

Format konstruktora: `SUB(Season, grassOverlay[, grassSat], foliageOverlay[, foliageSat], birchColor)`; brak parametru saturacji = `-1` (bez zmiany nasycenia). `0xFFFFFF` = brak nakładki (kolor wanilijny).

| SubSeason | grassOverlay | grassSat | foliageOverlay | foliageSat | birchColor |
|---|---|---|---|---|---|
| EARLY_SPRING | 0x778087 | 0.85 | 0x6F818F | 0.85 | 0x869A68 |
| MID_SPRING | 0x678297 | -1 | 0x4F86AF | -1 | 0x6EB283 |
| LATE_SPRING | 0x6F818F | -1 | 0x5F849F | -1 | 0x74AE73 |
| EARLY_SUMMER | 0x778087 | -1 | 0x6F818F | -1 | 0x7AAA64 |
| MID_SUMMER | 0xFFFFFF | -1 | 0xFFFFFF | -1 | 0x80A755 |
| LATE_SUMMER | 0x877777 | -1 | 0x9F5F5F | -1 | 0x98A54B |
| EARLY_AUTUMN | 0x8F6F6F | -1 | 0xC44040 | -1 | 0xB1A442 |
| MID_AUTUMN | 0x9F5F5F | -1 | 0xEF2121 | -1 | 0xE2A231 |
| LATE_AUTUMN | 0xAF4F4F | 0.85 | 0xDB3030 | 0.85 | 0xC98A35 |
| EARLY_WINTER | 0xAF4F4F | 0.60 | 0xDB3030 | 0.60 | 0xB1723B |
| MID_WINTER | 0xAF4F4F | 0.45 | 0xDB3030 | 0.45 | 0xA0824D |
| LATE_WINTER | 0x8E8181 | 0.60 | 0xA57070 | 0.60 | 0x8F925F |

| TropicalSeason | grassOverlay | grassSat | foliageOverlay | foliageSat | birchColor |
|---|---|---|---|---|---|
| EARLY_DRY | 0xFFFFFF | -1 | 0xFFFFFF | -1 | 0x80A755 |
| MID_DRY | 0xA58668 | 0.8 | 0xB7867C | 0.95 | 0x98A54B |
| LATE_DRY | 0x8E7B6D | 0.9 | 0xA08B86 | 0.975 | 0x80A755 |
| EARLY_WET | 0x758C8A | -1 | 0x728C91 | -1 | 0x80A755 |
| MID_WET | 0x548384 | -1 | 0x2498AE | -1 | 0x76AC6C |
| LATE_WET | 0x658989 | -1 | 0x4E8893 | -1 | 0x80A755 |

Interpretacja: jesień to nakładka czerwona (`0xC44040` -> `0xEF2121` -> `0xDB3030`) w trybie overlay na wanilijny kolor liści biomu; zima to ta sama czerwonawa nakładka, ale z silnym odbarwieniem (saturacja x0.45-0.60), co daje brązowo-szare liście; wiosna to nakładka niebieskawa (`0x4F86AF`) dająca chłodniejszą, jaśniejszą zieleń. Wanilijny kolor liści brzozy `0x80A755` = wartość MID_SUMMER.

Ważne dla naszego moda: Serene Seasons **nie rozróżnia gatunków** - wszystkie liście korzystające z tintu biomu (dąb, jungle, akacja, dark oak, mangrowe + liście modów) dostają tę samą nakładkę; świerk (0x619961) i brzoza mają stałe kolory (brzoza obsłużona osobno, świerk w ogóle nie zmienia koloru). Realistyczne polskie przebarwienie (buk - miedziany, dąb - brązowy późno, klon - czerwony/pomarańczowy, lipa/brzoza - żółty, modrzew - złoty i zrzucanie igieł, sosna/świerk/jodła - bez zmian) wymaga **własnych block color providerów** w naszym modzie, które czytają `SeasonHelper.getSeasonState(level).getSubSeason()` i stosują własne tabele kolorów per gatunek (a wanilijny mechanizm SS można dla naszych liści pominąć, nie rejestrując ich z tintem `FoliageColor`).

### 4.6 Konfiguracja (config)

Źródło: `config/SeasonsConfig.java` (gałąź 26.1.2). Konfiguracja jest ładowana przez GlitchCore (`init/ModConfig.java`); pliki w `config/sereneseasons/` (format i nazwy plików - do potwierdzenia).

| Kategoria | Opcja | Domyślnie | Zakres / opis |
|---|---|---|---|
| Weather | `generateSnowAndIce` | `true` | "Generate snow and ice during the Winter season" |
| Weather | `changeWeatherFrequency` | `true` | "Change the frequency of rain/snow/storms based on the season" |
| Time | `dayDuration` | `24000` | 20..MAX_INT; wewnętrzna długość dnia w tickach (dla modów zmieniających długość dnia) |
| Time | `subSeasonDuration` | **`8`** | 1..MAX_INT; "The duration of a sub season in days" -> pełny rok = 12 x 8 = **96 dni MC = 32 h realnego czasu** |
| Time | `startingSubSeason` | `1` | 0 = losowy, 1-3 = wiosna (Early/Mid/Late), 4-6 lato, 7-9 jesień, 10-12 zima |
| Time | `progressSeasonWhileOffline` | `true` | czy sezon płynie na pustym serwerze |
| Aesthetic | `changeGrassColor` | `true` | |
| Aesthetic | `changeFoliageColor` | `true` | |
| Aesthetic | `changeBirchColor` | `true` | |
| Dimension | `whitelistedDimensions` | `["minecraft:overworld"]` | "Seasons will only apply to dimensions listed here" |
| Season properties | per sub-sezon: `melt_percent`, `melt_rolls`, `biome_temp_adjustment`, `min_rain_time`, `max_rain_time`, `min_thunder_time`, `max_thunder_time` | tabela poniżej | topnienie śniegu/lodu, korekta temperatury, częstotliwość pogody |

Plik konfiguracyjny: `config/sereneseasons/seasons.toml` (z kodu: `SereneSeasons.MOD_ID + "/seasons.toml"`); analogicznie fertility - prawdopodobnie `fertility.toml` (NIEPOTWIERDZONE).

Metody: `boolean isDimensionWhitelisted(ResourceKey<Level> dimension)`, `SeasonProperties getSeasonProperties(Season.SubSeason season)`.

Rekord właściwości sezonu (cytat): `public record SeasonProperties(Season.SubSeason subSeason, float meltChance, int meltRolls, float biomeTempAdjustment, int minRainTime, int maxRainTime, int minThunderTime, int maxThunderTime)`

**Domyślne wartości per sub-sezon (gałąź 26.1.2, `SeasonsConfig.java`):**

| SubSeason | meltChance (%) | meltRolls | biomeTempAdjustment | minRainTime | maxRainTime | minThunder | maxThunder |
|---|---|---|---|---|---|---|---|
| EARLY_WINTER | 0.0 | 0 | **-0.8** | 12000 | 36000 | -1 | -1 |
| MID_WINTER | 0.0 | 0 | **-0.8** | 12000 | 36000 | -1 | -1 |
| LATE_WINTER | 0.0 | 0 | **-0.8** | 12000 | 36000 | -1 | -1 |
| EARLY_SPRING | 6.25 | 1 | **-0.25** | 12000 | 96000 | vanilla | vanilla |
| MID_SPRING | 8.33 | 1 | 0.0 | 12000 | 96000 | vanilla | vanilla |
| LATE_SPRING | 12.5 | 1 | 0.0 | 12000 | 96000 | vanilla | vanilla |
| EARLY_SUMMER | 25.0 | 1 | 0.0 | 12000 | 96000 | vanilla | vanilla |
| MID_SUMMER | 25.0 | 1 | 0.0 | 12000 | 96000 | vanilla | vanilla |
| LATE_SUMMER | 25.0 | 1 | 0.0 | 12000 | 96000 | vanilla | vanilla |
| EARLY_AUTUMN | 12.5 | 1 | 0.0 | vanilla | vanilla | vanilla | vanilla |
| MID_AUTUMN | 8.33 | 1 | 0.0 | vanilla | vanilla | vanilla | vanilla |
| LATE_AUTUMN | 6.25 | 1 | **-0.25** | vanilla | vanilla | vanilla | vanilla |

("vanilla" = `RAIN_DELAY.minInclusive()/maxInclusive()` i `THUNDER_DELAY...` - wanilijne zakresy ServerLevel: deszcz 12000-180000 ticków przerwy, burza 12000-180000; `-1` dla burz zimą = brak burz.)

Interpretacja liczbowa:
- Zima obniża temperaturę biomu o **0.8** - biom o temp. 0.8 (las, plains) spada do 0.0 (< 0.15 = śnieg i lód), biom o 0.7 (bagno) do -0.1, tajga 0.25 -> -0.55 -> przycięte do -0.5. Biomy 0.8 < T <= 2.0 (sawanna 1.2, pustynia 2.0, dżungla 0.95) nie są dotykane.
- Wczesna wiosna i późna jesień: -0.25 - las (0.8) spada do 0.55 (nadal deszcz, ale np. góry z 0.3 -> 0.05 dostają śnieg) - to daje "przymrozki" w górach i na wyżynach.
- Topnienie: wiosną 6.25 % -> 8.33 % -> 12.5 % szansy na random tick (1 rzut), latem 25 %; zimą 0 (śnieg się nie topi).
- Deszcz zimą częściej (przerwa 12000-36000 ticków = 0.5-1.5 dnia) - w zimnych biomach będzie to śnieg; wiosną/latem przerwa 12000-96000.

Reguła gry (`api/SSGameRules.java`): istnieje gamerule Serene Seasons (nazwa NIEPOTWIERDZONA; historycznie `doSeasonCycle`).

### 4.7 Dostęp do pory roku po stronie klienta i serwera, zdarzenia/hooki Fabric

Architektura (na podstawie listy klas i kodu `SeasonHelper`):
- **Serwer**: stan sezonu jest przechowywany w `SeasonSavedData` (SavedData per świat, plik w `data/` świata) i aktualizowany co tick w `SeasonHandler` (implementuje `SeasonHelper.ISeasonDataProvider`). Zmiana sub-sezonu wyzwala `SeasonChangedEvent.Standard`; zmiana sezonu tropikalnego - `SeasonChangedEvent.Tropical`.
- **Synchronizacja**: `network/SyncSeasonCyclePacket` (rejestrowany w `init/ModPackets`) wysyła ticki cyklu do klientów przy dołączaniu i cyklicznie; po stronie klienta `SeasonHandlerClient` trzyma mapę wymiar -> stan i odpowiada na `getClientSeasonState(level)`.
- **Wszystko przez `SeasonHelper.getSeasonState(Level)`** - jedna metoda działa po obu stronach (patrz 4.1). Na kliencie jest to stan zsynchronizowany, więc może być opóźniony o kilka ticków względem serwera; do logiki rozgrywki (spawn, wzrost, zachowania zwierząt) używać po stronie serwera.
- **Obliczanie sezonu z ticków**: `SeasonTime` (klasa narzędziowa) - dzieli `seasonCycleTicks` przez `subSeasonDuration * dayDuration`; kolejność sub-sezonów zaczyna się od EARLY_WINTER? NIEPOTWIERDZONE - w tabeli config sub-sezony wymienione są od EARLY_WINTER, a `startingSubSeason=1` = Early Spring wg komentarza configu; kolejność enumu `SubSeason` zaczyna się od EARLY_SPRING (ordinal 0).
- **Hooki Fabric**: Serene Seasons na Fabric NIE rejestruje własnych zdarzeń Fabric API (`net.fabricmc.fabric.api.event.Event`). Jedyna klasa specyficzna dla Fabric to `fabric/core/SereneSeasonsFabric.java` (entrypoint `glitchcore`), a cała logika zdarzeń jest w GlitchCore (`glitchcore.event.*`). Aby reagować na zmianę sezonu, nasz mod musi:
  1. dodać GlitchCore jako zależność compile-time (jest i tak zależnością twardą SS), i użyć `EventManager.addListener(SeasonChangedEvent.Standard.class, ...)` (sygnatura NIEZWERYFIKOWANA); albo
  2. nie subskrybować zdarzeń, tylko odpytywać `SeasonHelper.getSeasonState(level)` w tickach serwera (`ServerTickEvents.END_WORLD_TICK` z Fabric API) i samemu wykrywać zmianę sub-sezonu - **rozwiązanie bezpieczniejsze** (brak zależności od API GlitchCore, działa też przy braku Serene Seasons - wtedy używamy własnego "fallbacku" np. stałego lata).
- **Mixiny SS w wanilii** (co jest podmieniane; ważne, by nasz mod nie kolidował):
  - `MixinBiome` (serwer/wspólne): `shouldSnow`, `shouldFreeze`/`warmEnoughToRain`, `coldEnoughToSnow`, `getPrecipitationAt` -> `SeasonHooks.*`;
  - `MixinBiomeClient`: kolory trawy/liści (`getGrassColor`, `getFoliageColor`) -> `SeasonColorHandlers`/`SeasonColorUtil`;
  - `MixinLevel`: `isRainingAt` -> `isRainingAtHook`;
  - `MixinServerLevel`: `tickPrecipitation`/`tickIceAndSnow` (generowanie śniegu/lodu zimą wg `generateSnowAndIce`) i częstotliwość pogody (`changeWeatherFrequency`);
  - `MixinBlockStateBase`: random tick upraw (`SeasonalCropGrowthHandler`) - blokada/spowolnienie wzrostu poza sezonem;
  - `MixinWeatherEffectRenderer` (klient): renderowanie śniegu zamiast deszczu w zimnych sezonowo miejscach;
  - `MixinRangeSelectItemModelProperties`, `MixinSelectItemModelProperties` (klient): właściwości modeli przedmiotów (kalendarz).
  Dokładne cele (`@Inject`/`@Redirect`) NIE ZOSTAŁY ODCZYTANE (brak budżetu) - nazwy metod powyżej wynikają z nazw hooków w `SeasonHooks`.
- Praktyczna konsekwencja: **wanilijne API `Biome.coldEnoughToSnow(pos)`, `Biome.warmEnoughToRain(pos)`, `Biome.getPrecipitationAt(pos)` zwracają już wartości sezonowe**, gdy SS jest zainstalowany. Nasz kod spawnu/zachowań może więc korzystać z tych wanilijnych metod i automatycznie "widzieć" zimę - ale tylko dla biomów o temp. bazowej <= 0.8 i niewykluczonych tagiem.

### 4.8 Integracje w innych modach (BOP, Tectonic, Terralith, Farmer's Delight)

Wynik wyszukiwania (2026-09-21) - kodu integracji w repozytoriach BOP/Tectonic/Terralith/Farmer's Delight NIE PRZEJRZANO bezpośrednio (brak budżetu). Ustalenia pośrednie:

- **Seasonal-Integration** (`https://github.com/The-Bernician-Lamb/Seasonal-Integration`) - datapack/mod, który integruje inne mody z Serene Seasons wyłącznie przez **tagi**: dodaje biomy innych modów do `sereneseasons:tropical_biomes` i `sereneseasons:blacklisted_biomes`, a ich uprawy/nasiona do tagów `*_crops`. Wymaga NeoForge lub Fabric i MC >= 1.21.1. Pokazuje to standardowy wzorzec integracji: **żadnego kodu Java - tylko pliki JSON tagów w przestrzeni `sereneseasons`**.
- **Biomes O' Plenty** (Glitchfiend, ten sam zespół co Serene Seasons): integracja polega na tym, że BOP dostarcza w swoich zasobach pliki tagów `data/sereneseasons/tags/worldgen/biome/*.json` (tropical/blacklisted) i tagi upraw dla własnych roślin - wzorzec ten samej firmy (NIEPOTWIERDZONE przez odczyt repo Bebop w tym badaniu).
- **Terralith / Tectonic** (Stardust Labs): to datapacki/mody worldgen; Terralith ma znane problemy z SS przy biomach o niestandardowej temperaturze - społeczność stosuje datapacki tagów (jak Seasonal-Integration). NIEPOTWIERDZONE w kodzie.
- **Farmer's Delight (Refabricated)**: uprawy FD (cebula, kapusta, pomidor, ryż) są przypisywane do sezonów przez tagi `sereneseasons:*_crops` - albo w samym FD, albo w Seasonal-Integration. NIEPOTWIERDZONE w kodzie.
- MCreator: forum potwierdza, że sprawdzanie obecności SS odbywa się przez id `sereneseasons` ("is the mod with id sereneseasons loaded").

Wniosek: dominującym, sprawdzonym wzorcem integracji w ekosystemie jest **integracja danymi (tagi)**, a integracja kodem (odczyt `SeasonHelper`) jest stosowana przez mody, które chcą reagować na sezon w logice (np. mody z hibernacją zwierząt, migracją ptaków). Nasz mod potrzebuje obu.

### 4.9 Zależność w fabric.mod.json i kod warunkowy

Plik `fabric/src/main/resources/fabric.mod.json` Serene Seasons (gałąź 26.1.2) - kluczowe pola:

```json
{
  "id": "${mod_id}",            // = "sereneseasons"
  "version": "${mod_version}",
  "license": "${mod_license}",
  "entrypoints": { "glitchcore": ["sereneseasons.fabric.core.SereneSeasonsFabric"] },
  "mixins": ["${mod_id}.mixins.json", "${mod_id}.fabric.mixins.json"],
  "depends": {
    "fabricloader": "*",
    "fabric-api": "*",
    "minecraft": "${minecraft_version}",
    "java": ">=21",
    "glitchcore": ">=${glitchcore_version}"
  },
  "custom": { "modmenu": { "links": { "modmenu.discord": "${mod_discord_url}" } } }
}
```

Wnioski:
- Identyfikator moda do użycia w `depends`/`suggests`: **`sereneseasons`**.
- Serene Seasons na Fabric **wymaga GlitchCore** (`glitchcore`), wchodzi przez entrypoint `glitchcore` (nie `main`). Modrinth w metadanych wersji nie zawsze to pokazuje - w praktyce GlitchCore jest zależnością twardą.
- Serene Seasons deklaruje `fabric-api: *` (dowolna wersja).

Przykład deklaracji w naszym `fabric.mod.json` (składnia Fabric Loader; wersje przykładowe):

```json
{
  "depends": {
    "fabricloader": ">=0.19.5",
    "minecraft": "~26.3",
    "java": ">=25",
    "fabric-api": "*"
  },
  "suggests": {
    "sereneseasons": "*",
    "terrablender": "*"
  },
  "recommends": {
    "geckolib": "*"
  }
}
```

- `depends` = zależność twarda (gra nie wystartuje bez niej); `recommends` = zalecana (Loader tylko ostrzega/wyświetla w logu); `suggests` = sugerowana, wyłącznie informacyjna; `breaks` = niekompatybilne; `conflicts` = ostrzeżenie o konflikcie. Dla integracji opcjonalnej z Serene Seasons właściwe jest `suggests` (lub `recommends`), a kod integracyjny musi być ładowany warunkowo.
- **Kod warunkowy** (Fabric Loader API):

```java
import net.fabricmc.loader.api.FabricLoader;

public final class SeasonsCompat {
    public static final boolean SERENE_SEASONS_LOADED =
        FabricLoader.getInstance().isModLoaded("sereneseasons");

    /** Zwraca "meta-sezon" naszego moda; bez SS - stałe lato lub własny prosty kalendarz. */
    public static PolishSeason getSeason(Level level) {
        if (SERENE_SEASONS_LOADED) {
            return SereneSeasonsBridge.getSeason(level); // klasa odwołująca się do sereneseasons.api.*
        }
        return PolishSeason.SUMMER;
    }
}
```

Zasada: klasa `SereneSeasonsBridge` (jedyna importująca `sereneseasons.api.season.*`) może być ładowana przez JVM dopiero, gdy `SERENE_SEASONS_LOADED == true` - inaczej `NoClassDefFoundError`. Kompilacja wymaga artefaktu SS jako `modCompileOnly` (Loom): `modCompileOnly "com.github.glitchfiend:SereneSeasons-fabric:<mc>-<ver>"` (dokładne koordynaty Maven Glitchfiend NIEPOTWIERDZONE - do sprawdzenia w README/wiki SS; alternatywa: `modCompileOnly "maven.modrinth:serene-seasons:<version-id>"` przez repozytorium `https://api.modrinth.com/maven`, które publikuje wszystkie wersje z Modrinth).
- Mixiny warunkowe: jeśli nasz mod musi mixinować coś zależnie od obecności SS, użyć `IMixinConfigPlugin.shouldApplyMixin` lub osobnego pliku mixinów ładowanego przez `FabricLoader.isModLoaded`.
- Alternatywnie Fabric Loader obsługuje wpis `"entrypoints"` z warunkiem? NIE - Fabric Loader nie ma warunkowych entrypointów; warunek trzeba zrealizować w kodzie.

## 5. Dostępność bibliotek: GeckoLib, AzureLib, TerraBlender, Fabric Biome API, Cloth Config/ModMenu, Terraform Wood API

### 5.1 GeckoLib (animowane modele encji/bloków/przedmiotów; format Blockbench + Molang)

Źródło: Modrinth API `project/geckolib/version?loaders=["fabric"]` (pobrane 2026-09-21).

| Wersja MC | Najnowszy GeckoLib (Fabric) | Data |
|---|---|---|
| 26.3 | 5.5.6 | 2026-09-16 |
| 26.2 | 5.5.5 | 2026-09-06 |
| 26.1.2 | 5.5.2 | 2026-06-27 |
| 26.1 | 5.5 | 2026-03-24 |
| 1.21.11 | 5.4.5 | 2026-03-03 |
| 1.21.10 | 5.3-alpha-3 | 2025-10-29 (tylko alpha!) |
| 1.21.8 | 5.2.2 | 2025-08-01 |
| 1.21.5 | 5.1.0 | 2025-06-16 |
| 1.21.4 | 4.8.5 | 2025-06-06 |
| 1.21.1 | **4.9.3** | **2026-09-16** |
| 1.20.1 | 4.8.4 | 2026-06-20 |

Wspierane wersje gry (Fabric): 26.3, 26.2, 26.1.2, 26.1, 1.21.11, 1.21.10, 1.21.8, 1.21.7, 1.21.6, 1.21.5, 1.21.4, 1.21.1, 1.20.1. Najnowsze wydanie w ogóle: 4.9.3 dla 1.21.1 (2026-09-16; poprawki Molang i easing catmull-rom) - dowód, że **1.21.1 jest aktywnie utrzymywaną gałęzią "LTS" GeckoLib**. GeckoLib 5.x (1.21.5+) ma inne API niż 4.x (1.20.1/1.21.1/1.21.4) - kod animacji nie przenosi się 1:1 między 1.21.1 a 26.x.

### 5.2 AzureLib

NIE ZBADANO (brak budżetu). Z wiedzy własnej: AzureLib to fork GeckoLib 4 (AzureDoom), dostępny na Fabric/NeoForge dla 1.20.1-1.21.x, z API zbliżonym do GeckoLib 4; wybór GeckoLib jest bezpieczniejszy z uwagi na potwierdzone wsparcie 26.x (patrz wyżej) - dla AzureLib wsparcie 26.x NIEPOTWIERDZONE.

### 5.3 TerraBlender (dodawanie biomów do overworldu obok wanilii)

Źródło: Modrinth API `project/terrablender/version?loaders=["fabric"]` (pobrane 2026-09-21).

| Wersja MC | Najnowszy TerraBlender (Fabric) | Data |
|---|---|---|
| 26.3 | 26.3.0.0.5 | 2026-09-21 |
| 26.2 | 26.2.0.0.2 | 2026-07-07 |
| 26.1.2 | 26.1.2.0.3 | 2026-07-07 |
| 26.1 | 26.1.0.2 | 2026-03-29 |
| 1.21.11 | 21.11.0.0 | 2025-12-13 |
| 1.21.10 | 21.10.0.0 | 2025-10-12 |
| 1.21.8 | 6.0.0.3 | 2025-07-19 |
| 1.21.5 | 5.0.0.1 | 2025-04-26 |
| 1.21.4 | 4.3.0.2 | 2025-01-05 |
| 1.21.1 | 4.1.0.8 | 2025-01-05 |
| 1.20.1 | 3.0.1.10 | 2025-03-10 |

Wspierane wersje (Fabric): 1.19.4 - 26.3 (pełna lista: 26.3, 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11, 1.21.10, 1.21.9, 1.21.8, 1.21.7, 1.21.6, 1.21.5, 1.21.4, 1.21.3, 1.21.1, 1.21, 1.20.6, 1.20.5, 1.20.4, 1.20.3, 1.20.2, 1.20.1, 1.19.4).

Czy TerraBlender jest potrzebny? Wanilijny `MultiNoiseBiomeSource` overworldu ma listę parametrów biomów "zaszytą" w `OverworldBiomeBuilder` (kod Java, nie datapack; w datapacku można nadpisać cały `minecraft:overworld` preset, ale to łamie kompatybilność z innymi modami worldgen). Fabric API nie udostępnia oficjalnego API do dodawania biomów do overworldu (dawne `OverworldBiomes` z Fabric Biome API zostało usunięte w erze 1.18/1.19; pozostało `BiomeModifications` do modyfikacji istniejących biomów: dodawanie feature'ów, spawnów, zmiana efektów). **TerraBlender jest de facto standardem** (używają go BOP, Terralith-kompatybilne mody, Regions Unexplored, Wilder Wild częściowo): mod rejestruje `Region` z własną mapą parametrów klimatu (`ParameterUtils`, `VanillaParameterOverlayBuilder`, `addBiome(...)`) i wagą (`weight`) względem regionu wanilijnego; TerraBlender przełącza regiony w przestrzeni szumem "region". To pozwala, by nasze polskie biomy współistniały z wanilią lub - przy wysokiej wadze - dominowały (a nawet wyparły wanilię, jeśli waga wanilii = 0 w konfigu TB).
Współpraca SS z TerraBlender: Serene Seasons nie zależy od TB i działa na poziomie `Biome` (temperatura, tagi), więc **biomy dodane przez TB są sezonowane automatycznie**, jeśli mają `temperature <= 0.8` i nie są w `blacklisted_biomes`/`tropical_biomes`. Oba mody są od Glitchfiend i są testowane razem (BOP + SS). NIEPOTWIERDZONE w kodzie, wynika z architektury opisanej w 4.2.
Alternatywa bez TB dla "całkowicie polskiego" świata: własny `BiomeSource` (typ `codec` zarejestrowany w `Registries.BIOME_SOURCE`) i własny preset świata (`worldgen/world_preset`) - wtedy nie potrzebujemy TB, ale tracimy kompatybilność z innymi modami dodającymi biomy. Decyzja do podjęcia z użytkownikiem (patrz "Niepewności").

### 5.4 Fabric Biome API (`net.fabricmc.fabric.api.biome.v1`)

Z wiedzy własnej (dokumentacja docs.fabricmc.net NIE POBRANA w tym badaniu): `BiomeModifications.addFeature(BiomeSelectors.tag(...), GenerationStep.Decoration.VEGETAL_DECORATION, placedFeatureKey)`, `BiomeModifications.addSpawn(selector, MobCategory.CREATURE, entityType, weight, min, max)`, `BiomeModifications.create(id).add(ModificationPhase.ADDITIONS, selector, (ctx) -> ctx.getEffects()...)`; selektory `BiomeSelectors.foundInOverworld()`, `.tag(TagKey)`, `.includeByKey(...)`. Służy do wstrzykiwania naszych feature'ów runa/podszytu i spawnów zwierząt do wanilijnych biomów (np. jeśli chcemy "spolszczyć" wanilijny `forest`), bez TB. Nie dodaje nowych biomów do overworldu.

### 5.5 Cloth Config / ModMenu

NIE ZBADANO bezpośrednio wersji. Ustalone pośrednio: Cloth Config jest zależnością Serene Seasons dla 1.20.1-1.21.11 (metadane Modrinth), więc na tych wersjach jest dostępny i stabilny; ModMenu - SS deklaruje pole `custom.modmenu.links` w fabric.mod.json, czyli ModMenu jest w powszechnym użyciu (wersje dla 26.x DO POTWIERDZENIA; z wiedzy: ModMenu wydaje builds w ciągu dni od każdej wersji MC). Dla naszego moda: config przez Fabric-owy własny JSON (GSON) lub Cloth Config + ModMenu (ekran ustawień w grze).

### 5.6 Terraform Wood API (TerraformersMC)

NIE ZBADANO (brak budżetu). Z wiedzy własnej: `terraform-wood-api-fabric` (TerraformersMC) dostarcza klasy `TerraformBoatItem`, `TerraformBoatType`, `TerraformSignBlock`, `TerraformHangingSignBlock` oraz rejestrację łodzi/tabliczek dla nowych rodzajów drewna; wspierał 1.20.1 i 1.21.x; wsparcie dla 26.x NIEPOTWIERDZONE. Od 1.21.2+ wanilia uczyniła łodzie osobnymi typami encji (`EntityType` per wariant), a w 1.21.x drewno w dużej mierze przeszło na `BlockSetType`/`WoodType` + data-driven modele, więc potrzeba tej biblioteki zmalała; na 26.x prawdopodobnie da się zarejestrować pełny zestaw drewna (log, wood, planks, stairs, slab, fence, gate, door, trapdoor, button, pressure plate, sign, hanging sign, boat, chest boat, shelf) bez zewnętrznej biblioteki, kopiując wzorzec wanilijnej topoli (26.3) - do weryfikacji w kodzie 26.3 (`net.minecraft.world.level.block.Blocks` - wpisy `POPLAR_*`).

## 6. Rekomendacja wersji MC dla projektu w 2026

### 6.1 Macierz dostępności (stan 2026-09-21, wszystkie daty = ostatnie wydanie Fabric)

| Kryterium | 1.20.1 | 1.21.1 | 1.21.11 | 26.3 (najnowsza) |
|---|---|---|---|---|
| Java | 17 | 21 | 21 | 25 |
| Mapowania | Yarn (dominujące) / Mojmap | Mojmap zalecane, Yarn dostępny | Mojmap, Yarn dostępny | brak (kod nieobfuskowany) |
| Fabric API | 0.92.12 (2026-09-01) | 0.116.17 (2026-09-01) | 0.141.6 (2026-07-28) | 0.161.0 (2026-09-18) |
| Serene Seasons | 9.1.0.3 (2026-06-14) | 10.1.0.9 (2026-09-05) | 21.11.0.0 (2025-12-14) | 26.1.2.0.7 (2026-09-21) |
| GeckoLib | 4.8.4 (2026-06-20) | 4.9.3 (2026-09-16) | 5.4.5 (2026-03-03) | 5.5.6 (2026-09-16) |
| TerraBlender | 3.0.1.10 (2025-03-10) | 4.1.0.8 (2025-01-05) | 21.11.0.0 (2025-12-13) | 26.3.0.0.5 (2026-09-21) |
| Wanilijne runo (leaf litter, bush, wildflowers, dry grass, fallen trees) | brak | brak | jest (od 1.21.5) | jest |
| Pale garden / pale oak | brak | brak | jest | jest |
| Poplar (drzewo z losowym kolorem liści), shelf mushrooms, red shrub, dappled forest | brak | brak | brak | jest |
| Tagi `#supports_vegetation`, `#substrate_overworld` | brak | brak | brak | jest (od 26.1) |
| Nowe modele młodych mobów (wzorzec dla własnych zwierząt) | brak | brak | brak | jest (od 26.1) |
| Stabilność targetu | bardzo wysoka (3 lata) | bardzo wysoka (2 lata) | średnia (koniec serii) | niska: nowy drop co ~3 mies. |
| Liczba innych modów (kompatybilność) | największa (Forge+Fabric) | bardzo duża (NeoForge+Fabric) | umiarkowana | rośnie, ale najmniejsza |
| Ryzyko martwych bibliotek | niskie | niskie | średnie (GeckoLib 1.21.10 tylko alpha) | średnie (opóźnienia portów po każdym dropie) |

### 6.2 Statystyki popularności wersji

Wyszukiwanie "Modrinth statistics most popular Minecraft version 2026" NIE dało twardych liczb (Modrinth nie publikuje publicznie rozkładu pobrań per wersja gry). Dostępne przesłanki jakościowe: 1.20.1 pozostaje główną bazą modpacków Forge; 1.21.1 jest bazą dużych modpacków NeoForge/Fabric (np. All the Mods 11); listy "top mods" w 2026 nadal dzielą się na "1.20.1 / 1.21+". Pośredni, twardy dowód: biblioteki (Fabric API, GeckoLib, Serene Seasons) publikują nowe buildy dla 1.20.1 i 1.21.1 jeszcze we wrześniu 2026, mimo że wersje mają 2-3 lata - żadna inna wersja pośrednia (1.21.4, 1.21.5, 1.21.8) nie jest tak utrzymywana. **NIE ZBADANO: dokładnych udziałów procentowych.**

### 6.3 Rekomendacja

Rekomendacja główna: **budować na najnowszym dropie 26.x (obecnie 26.3), z równoległym śledzeniem kolejnych dropów** - pod warunkiem akceptacji przez użytkownika kosztów portowania.

Uzasadnienie (specyficzne dla tego projektu):
1. Wymaganie "wszystkie piętra lasu jak w polskim lesie" jest w 26.3 w dużej mierze wspierane przez wanilię: leaf litter, bush, wildflowers, dry grass, fallen trees (1.21.5), shelf mushrooms (huby), red shrub, poplar z losowym kolorem liści (26.3). Na 1.20.1/1.21.1 wszystko to trzeba napisać od zera, co znacząco zwiększa zakres projektu i pogarsza kompatybilność z zasobami (tekstury, modele) społeczności.
2. Tagi `#supports_vegetation`/`#substrate_overworld` (26.1) pozwalają dodać własne podłoża leśne (ściółka iglasta, gleba brunatna, torf, mursz) bez mixinów.
3. Wszystkie kluczowe biblioteki mają aktualne buildy na 26.3 w ciągu tygodnia od wydania (SS 2026-09-21, TB 2026-09-21, GeckoLib 2026-09-16, Fabric API 2026-09-18).
4. Kod nieobfuskowany + brak mapowań upraszcza naukę i debugowanie (stack trace = prawdziwe nazwy).
5. Realistyczne modele zwierząt: 26.1 przemodelowało wszystkie młode - projekt i tak musi robić własne modele (GeckoLib 5), więc lepiej zacząć na API, które nie będzie porzucone (GeckoLib 5.x rozwija się dla 26.x; 4.x jest w trybie utrzymania).

Wady 26.x, które użytkownik musi zaakceptować:
- Port do 26.4 (grudzień 2026), 27.1 (marzec 2027) itd. - co 3 miesiące zmiany API; opóźnienie bibliotek 1-3 tygodnie po każdym dropie.
- Mniej modów dodatkowych i mniej poradników; dokumentacja Fabric dla 26.x jest nowsza i mniej "wygooglowalna".
- Java 25 na komputerze użytkownika (launcher Mojang instaluje JRE automatycznie; własne launchery mogą wymagać ręcznej instalacji).

Alternatywa "bezpieczna": **1.21.1** - jeśli priorytetem jest współpraca z dużym ekosystemem (Terralith, Farmer's Delight, Create) i brak portowania przez lata; wtedy runo/ściółkę/krzaki implementujemy sami (wzorując się na kodzie 1.21.5, który jest publicznie widoczny w Mojmap), a Serene Seasons 10.1.0.9 / GeckoLib 4.9.3 / TerraBlender 4.1.0.8 są stabilne.

Odradzane: 1.20.1 (Java 17, Yarn, brak 1.21-owych zmian w komponentach przedmiotów, TerraBlender bez aktualizacji od marca 2025; nowy projekt w 2026 nie powinien zaczynać na kodzie sprzed 3 lat), oraz wersje pośrednie 1.21.4-1.21.11 (bez statusu LTS, GeckoLib 1.21.10 tylko alpha, SS 1.21.11 bez aktualizacji od grudnia 2025).

## Implikacje projektowe dla moda

### Wersja i toolchain
1. **Docelowa wersja: 26.3 (najnowszy drop), Java 25, Fabric Loader >= 0.19.5, Fabric API 0.161.0+26.3, Loom w wersji z aktualnego `fabric-example-mod`** - do zatwierdzenia przez użytkownika (alternatywa 1.21.1). Projekt trzymać w gałęzi per drop (`26.3`, później `26.4`), jak robi Glitchfiend.
2. Pisać kod w nazewnictwie Mojang (`net.minecraft.world.level.*`), bez Yarn - niezależnie od wersji. Dzięki temu integracja z SS/TB/GeckoLib (wszystkie w Mojmap) nie wymaga tłumaczenia nazw.
3. Zależności w `fabric.mod.json`: `depends`: fabricloader, fabric-api, minecraft, java; `suggests`/`recommends`: `sereneseasons`, `terrablender` (jeśli TB jest opcjonalne), `geckolib` jako `depends` (modele zwierząt są rdzeniem moda). Ładowanie kodu integracyjnego przez `FabricLoader.getInstance().isModLoaded("sereneseasons")` i osobną klasę mostu.
4. Repozytoria Maven do `build.gradle`: `https://api.modrinth.com/maven` (artefakty `maven.modrinth:serene-seasons`, `maven.modrinth:terrablender`, `maven.modrinth:geckolib`, `maven.modrinth:glitchcore`) jako `modCompileOnly` (SS) / `modImplementation` (TB, GeckoLib). Dokładne koordynaty zweryfikować w README każdej biblioteki.

### Integracja z Serene Seasons
5. Jedno źródło prawdy o sezonie w naszym modzie: interfejs `PolishSeasonProvider` z implementacją SS (`SeasonHelper.getSeasonState(level)`) i fallbackiem (własny prosty kalendarz lub stałe lato), tak aby mod działał także bez SS.
6. Fenologię (kwitnienie, owocowanie, opad liści, ruja jeleni we wrześniu/październiku, gody łosi, sen zimowy niedźwiedzi, migracja bocianów - przylot koniec marca, odlot koniec sierpnia) mapować na 12 sub-sezonów SS (każdy = 8 dni MC domyślnie). Zaproponować użytkownikowi mapowanie: EARLY_SPRING = marzec, MID_SPRING = kwiecień, LATE_SPRING = maj, EARLY_SUMMER = czerwiec, MID_SUMMER = lipiec, LATE_SUMMER = sierpień, EARLY_AUTUMN = wrzesień, MID_AUTUMN = październik, LATE_AUTUMN = listopad, EARLY_WINTER = grudzień, MID_WINTER = styczeń, LATE_WINTER = luty.
7. Temperatury bazowe naszych biomów ustawiać w zakresie **0.2-0.8** (niziny 0.7-0.8, pojezierza 0.6-0.7, wyżyny 0.5-0.6, pogórze 0.4-0.5, regiel dolny 0.3, regiel górny/kosodrzewina 0.2, hale/turnie 0.0-0.1), aby SS je sezonowało i aby zima (-0.8) dała śnieg na nizinach (0.8 -> 0.0 < 0.15), a przedwiośnie/późna jesień (-0.25) dały śnieg tylko w górach (<= 0.4 -> < 0.15). Żadnego biomu nie oznaczać jako tropical; rozważyć `lesser_color_change_biomes` dla borów iglastych tylko, jeśli nie zrobimy własnych providerów kolorów.
8. Kolory liści per gatunek: nie polegać na globalnej nakładce SS. Dla każdego gatunku (buk *Fagus sylvatica*, dąb szypułkowy *Quercus robur*, grab *Carpinus betulus*, brzoza *Betula pendula*, klon *Acer platanoides*, lipa *Tilia cordata*, jesion *Fraxinus excelsior*, olsza *Alnus glutinosa*, topola osika *Populus tremula*, wierzba *Salix alba*, modrzew *Larix decidua*, sosna *Pinus sylvestris*, świerk *Picea abies*, jodła *Abies alba*) zdefiniować własny `BlockColor` provider z tabelą 12 kolorów (sub-sezon -> RGB) odczytywaną z `SubSeason` SS; iglaste zimozielone bez zmian, modrzew z tabelą żółknięcia i (opcjonalnie) stan "bez igieł" zimą jako osobny blockstate.
9. Opad liści: reagować na `SeasonChangedEvent.Standard` (lub własne wykrywanie zmiany sub-sezonu w `ServerTickEvents.END_WORLD_TICK`): w LATE_AUTUMN/EARLY_WINTER zamieniać część bloków liści liściastych na wariant "nagi" i generować `leaf_litter` (wanilijny blok 1.21.5+) pod koronami; wiosną (MID_SPRING) odwracać. Wzorzec cząsteczek spadających liści: wanilijna topola 26.3 (per kolor).
10. Krzewy owocowe i runo (borówka czarna *Vaccinium myrtillus*, malina *Rubus idaeus*, jeżyna *Rubus fruticosus agg.*, leszczyna *Corylus avellana*, dzika róża *Rosa canina*, bez czarny *Sambucus nigra*, jarząb *Sorbus aucuparia*, poziomka *Fragaria vesca*) rejestrować jako bloki wzrastające w random ticku i **tagować do `sereneseasons:summer_crops` / `autumn_crops`** (pliki `data/sereneseasons/tags/block/*.json` w naszym jarze) - wtedy SS sam blokuje owocowanie poza sezonem; dodatkowo nasze bloki mogą czytać sub-sezon do własnych faz (kwitnienie/owoc/zrzut).
11. Grzyby (borowik *Boletus edulis*, podgrzybek *Imleria badia*, kurka *Cantharellus cibarius*, maślak *Suillus luteus*, muchomor *Amanita muscaria*, huba *Fomes fomentarius*): generowane jako feature'y tylko w EARLY_AUTUMN-MID_AUTUMN (i po deszczu - `Level.isRainingAt` jest hookowane przez SS, więc "po deszczu" widzi sezon); huby na pniach - wzorzec wanilijnych shelf mushrooms 26.3.
12. Zwierzęta: hibernacja niedźwiedzia brunatnego (*Ursus arctos*) EARLY_WINTER-LATE_WINTER, ruja jelenia (*Cervus elaphus*) EARLY_AUTUMN-MID_AUTUMN, bociany (*Ciconia ciconia*) obecne od EARLY_SPRING (przylot: koniec marca) do LATE_SUMMER (odlot: koniec sierpnia), poza tym okresem brak spawnu i despawn, bobry (*Castor fiber*) aktywne cały rok, ale zimą pod lodem/w żeremiu; wilk (*Canis lupus*) - watahy, gody LATE_WINTER; łoś (*Alces alces*) - bukowisko EARLY_AUTUMN; kuna (*Martes martes*) - nocna cały rok. Zaimplementować przez sprawdzenie `getSubSeason()` w celach AI (Brain/Goal) i w warunkach spawnu (`SpawnPlacements` + własny predykat), nie przez zmianę wag spawnów w biomach.
13. Śnieg i lód: SS generuje je samo (`generateSnowAndIce = true`); nasz mod nie powinien dublować tej logiki, ale może dodać własne bloki (śnieg na gałęziach, szadź) reagujące na `Biome.coldEnoughToSnow(pos)` (hookowane).
14. Uprawy polskie (żyto, pszenica, ziemniak, burak, rzepak, kapusta) - tagi SS: żyto ozime i pszenica ozima do `autumn_crops` + `spring_crops`? (SS traktuje tag jako "sezon płodności", nie "sezon siewu") - do decyzji projektowej; ziemniak/burak `summer_crops`, rzepak ozimy `autumn_crops`+`spring_crops`.
15. Testy integracyjne: uruchamiać środowisko dev z SS + GlitchCore + TB + GeckoLib (`modRuntimeOnly` z Modrinth Maven) oraz bez SS, aby wykryć `NoClassDefFoundError`.

### Worldgen
16. Używać TerraBlender `Region` z bardzo wysoką wagą (lub konfiguracją wyłączającą region wanilijny), aby świat był "polski" w całości, ale pozostał kompatybilny z innymi modami. Alternatywę (własny `BiomeSource` + world preset "Polska") przedstawić użytkownikowi jako opcję "czysty tryb".
17. Runo/podszyt/ściółka/martwe drewno: budować z wanilijnych bloków 1.21.5+/26.3 tam, gdzie istnieją (`leaf_litter`, `bush`, `wildflowers`, `short_dry_grass`, `fallen_*_tree`, shelf mushrooms, red shrub) i dodawać własne tylko dla brakujących elementów (mchy leśne inne niż wanilijny `moss_block`, ściółka iglasta, mursz, powalone pnie nowych gatunków, wykroty).
18. Własne podłoża (ściółka iglasta, gleba brunatna, torf) dodać do tagów 26.1 `#minecraft:supports_vegetation`, `#minecraft:dirt`/`#substrate_overworld`, aby wanilijne i modowe rośliny na nich rosły i aby feature'y wanilijne je akceptowały.
19. Modele zwierząt: GeckoLib 5.x (26.x) - modele dorosłe i młode (26.1 wymusiło osobne modele młodych w wanilii; wzorować się na tym).

### Proces
20. Po każdym nowym dropie (co ~3 miesiące) sprawdzać: Fabric API, SS, TB, GeckoLib (opóźnienie 1-3 tygodnie), zanim mod zostanie zaktualizowany; utrzymywać tabelę wersji jak w sekcji 6.1.

## Niepewności / do potwierdzenia

Pytania do użytkownika (decyzje projektowe):
1. **Wybór wersji MC**: 26.3 (najnowszy drop; najbogatsza wanilia, portowanie co 3 miesiące, Java 25) czy 1.21.1 (LTS ekosystemu, wszystko na 2+ lata, ale runo/ściółkę/krzaki trzeba napisać samemu)? Czy użytkownik akceptuje, że na 26.x część modów towarzyszących (Terralith, Farmer's Delight) może nie być dostępna od razu?
2. **Serene Seasons jako zależność twarda czy opcjonalna?** Twarda upraszcza kod (brak fallbacku), ale wiąże z licencją All Rights Reserved i GlitchCore. Rekomendacja: opcjonalna (`suggests`) z fallbackiem.
3. **Czy świat ma być w 100 % polski** (własny world preset/biome source, brak wanilijnych biomów) czy polskie biomy mają współistnieć z wanilijnymi przez TerraBlender (z możliwością konfiguracji wagi)?
4. **Długość roku**: domyślne 96 dni MC (8 dni na sub-sezon) czy dłuższy (np. 12-15 dni na sub-sezon), aby fenologia zwierząt/roślin miała czas się rozegrać? Zmiana jest po stronie configu SS (użytkownik), ale mod może sugerować wartość.
5. **Mapowanie sub-sezonów na miesiące** (propozycja w Implikacjach, pkt 6) - zatwierdzić.
6. **Zakres sezonowania liści**: czy mod ma wyłączyć globalną nakładkę SS dla własnych liści (własne kolory per gatunek), a wanilijne liście zostawić SS?
7. **Uprawy ozime** - jak mapować "płodność" SS na cykl siew jesień/zbiór lato?

Fakty NIEPOTWIERDZONE lub NIE ZBADANE w tym badaniu (warto dopytać/doczytać):
- Dokładna wersja Fabric Loom dla 26.3 oraz wartości `gradle.properties` w fabric-example-mod (nie pobrano pliku).
- Wersje Yarn dla 1.20.1/1.21.1 (z pamięci: `1.20.1+build.10`, `1.21.1+build.3`).
- Szczegóły 26.2 "Chaos Cubed" (czy zawiera zmiany worldgen/flory) - nie pobrano strony.
- Daty wydań 1.21.1-1.21.11 pochodzą z wiedzy własnej, nie z pobranej strony (strona "version history" potwierdziła tylko 1.21 i 26.x).
- Koordynaty Maven Serene Seasons/GlitchCore (Glitchfiend Maven vs Modrinth Maven).
- Sygnatura subskrypcji zdarzeń GlitchCore (`EventManager.addListener`) oraz treść `SereneSeasonsFabric.java`.
- Dokładne cele mixinów SS (`@Inject`/`@Redirect`), algorytm spowolnienia wzrostu w `SeasonalCropGrowthHandler`, domyślna zawartość plików JSON tagów (które wanilijne uprawy/biomy są w których tagach).
- Nazwa gameruli w `SSGameRules` (historycznie `doSeasonCycle`).
- Czy Cloth Config jest nadal wymagany dla SS 26.x (metadane Modrinth go nie wymieniają).
- Treść pliku LICENSE w repo (Modrinth: All Rights Reserved).
- AzureLib, Terraform Wood API, Cloth Config, ModMenu - wersje dla 26.x nie zostały sprawdzone.
- Zachowanie SS wobec biomów TerraBlender potwierdzone tylko architektonicznie (nie testem).
- Udziały procentowe wersji MC wśród graczy modów - brak publicznych danych.
- Czy Mojang od 26.1 publikuje Javadoc/nazwy parametrów w nieobfuskowanym kodzie (dla 1.21.x potrzebny był Parchment).
- Kolejność sub-sezonów w `SeasonTime` (który sub-sezon ma ordinal 0 w cyklu) i w jakim sub-sezonie zaczyna się nowy świat przy `startingSubSeason = 1` (komentarz configu: 1 = Early Spring).

## Źródła

Pobrane bezpośrednio (WebFetch, 2026-09-21):
1. https://minecraft.wiki/w/Java_Edition_version_history - historia wersji, schemat numeracji, daty 26.x
2. https://minecraft.wiki/w/Java_Edition_26.3 - "Wilderness Bound", 2026-09-15, dappled forest, poplar, shelf mushrooms, formaty packów
3. https://minecraft.wiki/w/Java_Edition_26.1 - "Tiny Takeover", 2026-03-24, Java 25, nowy schemat wersji, tagi wegetacji, modele młodych mobów
4. https://meta.fabricmc.net/v2/versions - wersje gry i mappingów Yarn
5. https://meta.fabricmc.net/v2/versions/loader - Fabric Loader 0.19.5 stable
6. https://api.modrinth.com/v2/project/fabric-api/version (filtr fabric + wersje gry) - wersje Fabric API
7. https://api.modrinth.com/v2/project/serene-seasons/version - buildy Serene Seasons per wersja MC
8. https://api.modrinth.com/v2/project/serene-seasons - licencja, pobrania, wspierane wersje
9. https://api.modrinth.com/v2/project/geckolib/version?loaders=["fabric"] - wersje GeckoLib
10. https://api.modrinth.com/v2/project/terrablender/version?loaders=["fabric"] - wersje TerraBlender
11. https://github.com/Glitchfiend/SereneSeasons - repozytorium, gałąź domyślna 26.1.2
12. https://api.github.com/repos/Glitchfiend/SereneSeasons/git/trees/26.1.2?recursive=1 - pełne drzewo plików
13. https://github.com/Glitchfiend/SereneSeasons/tree/26.1.2/common/src/main/java/sereneseasons/api - katalog API
14. https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/fabric/src/main/resources/fabric.mod.json
15. https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/api/season/SeasonHelper.java
16. https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/api/season/Season.java
17. https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/api/season/ISeasonState.java
18. https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/api/season/SeasonChangedEvent.java
19. https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/season/SeasonHooks.java
20. https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/config/SeasonsConfig.java
21. https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/config/FertilityConfig.java
22. https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/init/ModTags.java
23. https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/util/SeasonColorUtil.java
24. https://raw.githubusercontent.com/FabricMC/fabric-example-mod/master/build.gradle - szablon (Java 25, brak mappings)
25. https://fabricmc.net/develop/ - strona wejściowa (bez macierzy wersji; odsyła do docs.fabricmc.net)

Z wyników wyszukiwania (WebSearch, 2026-09-21; treści stron nie pobrano w całości):
26. https://docs.fabricmc.net/develop/porting/mappings/ - "Migrating Mappings" (Yarn -> Mojang)
27. https://fabricmc.net/2026/03/14/261.html - "Fabric for Minecraft 26.1" (koniec utrzymywania Yarn od 26.1)
28. https://wiki.fabricmc.net/tutorial:mappings - opis Yarn vs Mojmap vs Parchment
29. https://github.com/FabricMC/yarn - repozytorium Yarn
30. https://github.com/The-Bernician-Lamb/Seasonal-Integration - datapack integracji przez tagi (Fabric/NeoForge, MC >= 1.21.1)
31. https://github.com/Glitchfiend/SereneSeasons/wiki - wiki (nie pobrano)
32. https://github.com/lucaargolo/fabric-seasons/issues/187 - "This mod is no longer needed" (Fabric Seasons porzucony na rzecz SS)
33. https://mcreator.net/forum/110707/serene-seasons-compatibility-solution - sprawdzanie `isModLoaded("sereneseasons")`
34. https://www.youtube.com/watch?v=Lu--qsO3Yxg - "TOP 20 Minecraft Mods OF The Month July 2026 (1.20.1 / 1.21+)" (przesłanka jakościowa o podziale wersji)

Nie pobrane, ale zalecane do dalszej weryfikacji:
- https://fabricmc.net/develop/template/ (generator szablonu, JS)
- https://github.com/FabricMC/fabric-example-mod/blob/master/gradle.properties (Loom, loader, API)
- https://docs.fabricmc.net/develop/getting-started/setting-up-a-development-environment
- https://github.com/Glitchfiend/GlitchCore (EventManager, config)
- https://github.com/Glitchfiend/TerraBlender (Region API)
- https://github.com/bernie-g/geckolib (GeckoLib 5 API)
- https://github.com/TerraformersMC/terraform-wood-api-fabric
- https://modrinth.com/mod/serene-seasons , https://www.curseforge.com/minecraft/mc-mods/serene-seasons
