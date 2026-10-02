# Technika generacji terenu w Minecraft (Fabric) na potrzeby realistycznego krajobrazu Polski

Data: 2026-09-21
Projekt: "Polish Forests", dawniej "Przyrodniczo zgodne lasy" (mod Fabric)
Status: raport ukończony w ramach budżetu 30 wywołań wyszukiwania/pobrania (2026-09-21/22). Braki są oznaczone "NIE ZBADANO", "NIE POTWIERDZONO" lub "DO POTWIERDZENIA".

## Streszczenie

Minecraft Java pozwala na świat o rozpiętości do 4064 bloków (`min_y` od -2032, szczyt budowania Y = 2031), więc pionowa skala 1:1 dla Polski (Rysy 2499 m) jest technicznie możliwa, ale wymaga obniżenia poziomu morza do ok. Y = -520 i daje ok. 167 sekcji na chunk (7x więcej niż wanilia). Od 1.18 cały kształt terenu i klimat biomów definiują density functions w JSON (`noise_settings`, `noise_router`, `surface_rules`), a biomy wybiera `multi_noise` po sześciu parametrach o dokładnie udokumentowanych progach (np. erosion 7 poziomów, continentalness od -1,2 do 1,0, PV = 1 - |3|weirdness| - 2|). Największe mody terenowe (Tectonic 16,4 mln pobrań, Terralith 23 mln, WWOO) działają wyłącznie na tych JSON-ach, a Tectonic podnosi wysokość świata do Y = 640 i ma opcję "Vertical Scale" - dowód, że przewyższenie i wysoki świat są praktykowane. Numeracja wersji zmieniła się w 2026 r.: po 1.21.x weszły 26.1 (marzec), 26.2 (czerwiec) i 26.3 "Wilderness Bound" (wg wiki 15.09.2026), która przebudowuje density functions (usunięte `cache_2d`, `flat_cache`, `shifted_noise`, `weird_scaled_sampler`, `y_clamped_gradient`; `surface_rule` -> `material_rule`) i dodaje topolę, huby, "dappled forest" i `fallen_poplar_tree`. Wanilia od 1.21.5 (25.03.2025) ma leaf litter, bush, wildflowers, firefly bush, dry grass i data-packowy typ feature `minecraft:fallen_tree` (pola `trunk_provider`, `log_length` 0-16, dekoratory) - gotowe klocki dla martwego drewna i ściółki. Nie istnieje generator z rzeczywistego DEM na Fabric 1.21+/26.x: Terra 1-to-1/Terra++ (Build the Earth) to Forge 1.12.2 z CubicChunks; na Fabric są generatory obrazkowe (Atlas, NovoAtlas - PNG 8-bit) i online-Earth (Tellus, 30 m, wymaga internetu). Dla Polski najlepsze dane to otwarty NMT GUGiK (1 m z ALS i 5 m; ASCII GRID; EPSG:2180; WCS z limitem 7 km2 na zapytanie), BDOT10k 1:10 000 (otwarta od 31.07.2020, paczka krajowa GPKG, klasy pokrycia PTLZ itd.), CORINE 2018 (MMU 25 ha - tylko do makro) oraz Bank Danych o Lasach (opis taksacyjny w TXT + wydzielenia w Shapefile przez automatyczny formularz; gatunek panujący, wiek, TSL - licencja do potwierdzenia). Układ PL-1992 jest płaski i metrowy, więc blok = piksel siatki bez projekcji Dymaxion. Skala 1:1 poziomo to 649 000 x 689 000 bloków, 1,22 mld chunków, szacunkowo 12-24 TB pre-generacji i ok. 70 dni generacji przy 200 chunkach/s - grywalne tylko w locie; warianty 1:4/1:2 (0,8-1,5 TB), 1:10/1:4 (122-244 GB, 17 h - 3 dni) i 1:20/1:8 (30-60 GB) są realistyczne, przy czym drzewa i zwierzęta nie skalują się z terenem. Rekomendowana architektura to hybryda: własny typ density function `polska:raster` czytający kaflowane rastry (LRU, mmap) i wpięty w wanilijny `NoiseChunkGenerator` przez JSON, plus własny `BiomeSource` z mapy siedlisk; etap 1 na rastrze makro (1 px = 0,5-1 km, kilka MB), etap 2 na pełnych rastrach (GB, osobne pobranie). Kluczowe decyzje do zadania użytkownikowi: wersja docelowa (1.21.x vs 26.x), skala pozioma/pionowa, czy świat ma być "Polską z danych" (ograniczoną, pre-generowaną) czy "Polską proceduralną" (nieskończoną), oraz akceptowalny rozmiar pobieranych danych.

## 0. Kontekst wersji Minecraft (stan na 2026-09-21)

- Mojang zmienił numerację wersji w 2026 r. na roczną: po linii 1.21.x weszła wersja **26.1** ("Tiny Takeover", 24.03.2026), potem **26.2** ("Chaos Cubed", 16.06.2026; m.in. eksperymentalny renderer Vulkan), a **26.3** ("Wilderness Bound") była w fazie release candidate z planowaną datą 15.09.2026 (źródła: minecraft.net "new version numbering system", minecraft.wiki "Java Edition 26.1", releasebot). Czy 26.3 faktycznie wyszło 15.09 - NIE POTWIERDZONO w tej turze (patrz "Niepewności").
- Konsekwencja dla moda: dokumentacja minecraft.wiki dla worldgenu odzwierciedla już snapshoty 26.3, w których przebudowano density functions (sekcja 2.1). Decyzja "na jaką wersję celujemy" (1.21.x vs 26.x) zmienia zestaw dostępnych typów DF i format `noise_settings`. Trzeba o to zapytać użytkownika.

## 1. Limity wysokości świata (dimension_type) i konsekwencje wydajnościowe

### 1.1. Pola dimension_type (minecraft.wiki "Dimension type", stan: 1.21.6+ i snapshoty 26.x)

| Pole | Typ | Zakres / ograniczenie | Domyślnie Overworld | Uwagi |
|---|---|---|---|---|
| `min_y` | int | od -2032 do 2031, wielokrotność 16 | -64 | dolna krawędź świata |
| `height` | int | od 16 do 4064, wielokrotność 16 | 384 | całkowita wysokość świata |
| `logical_height` | int | nie większe niż `height` | 384 | maks. wysokość, na jaką przenoszą portale/chorus |
| maks. wysokość budowania | - | `min_y + height - 1`, nie więcej niż 2031 | 319 | ograniczenie twarde |
| `coordinate_scale` | double | 0.00001 do 30000000.0 | 1.0 (Nether 8.0) | skala współrzędnych między wymiarami |
| `ambient_light` | float | 0 do 1 | 0.0 | |
| `monster_spawn_block_light_limit` | int | 0 do 15 | 0 | |
| `monster_spawn_light_level` | int / provider | 0 do 15 | 0-7 (uniform) | |
| `has_skylight` / `has_ceiling` / `has_fixed_time` | bool | - | true / false / false | |
| `infiniburn` | string | tag `#...` (od 22w06a); od 26.2 także ID i lista ID | `#minecraft:infiniburn_overworld` | |
| `skybox` | string | `none` / `overworld` / `end` | `overworld` | nowsze snapshoty |
| `cardinal_light` | string | `default` / `nether` | `default` | nowsze snapshoty |

Wnioski liczbowe:
- Maksymalna teoretyczna rozpiętość pionowa świata to 4064 bloków (np. `min_y = -2032`, `height = 4064`, szczyt na Y = 2031). Dla Polski (Rysy 2499 m n.p.m., depresja Raczki Elbląskie -1,8 m n.p.m.) skala pionowa 1:1 wymaga ok. 2500 bloków nad poziomem morza plus zapas na jaskinie/podłoże; mieści się w limicie (np. `min_y = -256`, `height = 2816` daje szczyt Y = 2559).
- Wanilia 1.18+ ma 384 bloków (-64..319), czyli 24 sekcje chunków po 16 bloków.

### 1.2. Konsekwencje wydajnościowe wysokich światów

Fakty potwierdzone (minecraft.wiki, "Chunk format"/"Dimension type" - opis ogólny):
- Chunk jest dzielony na sekcje 16x16x16; liczba sekcji = `height / 16`. Świat o wysokości 2816 ma 176 sekcji na chunk zamiast 24 (7,3x więcej). Puste sekcje (samo powietrze) są zapisywane oszczędnie (paleta 1-elementowa), więc realny koszt dysku rośnie mniej niż liniowo, ale koszt generacji noise 3D, oświetlenia (skylight propaguje przez wszystkie sekcje) i pamięci RAM rośnie w przybliżeniu proporcjonalnie do liczby niepustych sekcji.
- Generacja terenu w wanilii próbkuje density function w komórkach (`interpolated`, w 1.18-1.21 `size_horizontal`=1 -> komórka 4 bloki, `size_vertical`=2 -> komórka 8 bloków) - koszt noise rośnie liniowo z liczbą komórek w pionie.

NIE ZBADANO SZCZEGÓŁOWO: brak w tej turze twardych benchmarków "czas generacji chunku vs wysokość świata" (patrz sekcja 6 - Chunky, oraz sekcja 4.1 - Tectonic "increased height").

## 2. Density functions (JSON) i noise_settings

Od 1.18 cały kształt terenu Overworldu jest opisany danymi: `worldgen/noise_settings/overworld.json` (router szumów, reguły powierzchni, poziom morza) i `worldgen/density_function/**.json` (drzewo funkcji gęstości). Wartość `final_density` > 0 w danym bloku oznacza blok stały (`default_block`), <= 0 powietrze albo płyn (akwifery). Zmieniając te pliki (data pack / mod) uzyskuje się dowolny teren bez Javy - to ścieżka Tectonic, Terralith, WWOO (sekcja 4).

### 2.1. Typy density functions

WAŻNE ZASTRZEŻENIE WERSJI: strona minecraft.wiki "Density function" (pobrana 2026-09-21) opisuje już stan snapshotów 26.x. W snapshotcie **26.3-snap10** usunięto typy `cache_2d`, `cache_all_in_cell`, `flat_cache`, `shifted_noise`, a wcześniej usunięto `slide`, `terrain_shaper_spline`, stary `spline`, `weird_scaled_sampler`, `y_clamped_gradient`. W ich miejsce weszły `cache`, `gradient`, `lerp`, `slice`, `distance_to_point`, `find_top_surface`, `ore_vein` i pełny zestaw arytmetyki. Dla wersji 1.21.x (stabilne wydania, na które realnie celuje mod) obowiązuje "stary" zestaw wymieniony w zadaniu. Poniżej oba zestawy, z oznaczeniem.

Zestaw 1.18-1.21.x (wg zadania; semantyka z minecraft.wiki, pola potwierdzone):

| Typ | Pola | Semantyka |
|---|---|---|
| `constant` | `argument` (double) | stała wartość |
| `noise` | `noise` (id noise), `xz_scale`, `y_scale` | próbkuje szum 3D w (x*xz_scale, y*y_scale, z*xz_scale) |
| `shifted_noise` (1.21.x) | `noise`, `xz_scale`, `y_scale`, `shift_x`, `shift_y`, `shift_z` (DF) | jak noise, ale współrzędne przesunięte o wartości innych DF (używane do temperature/vegetation/continents itd.) |
| `shift`, `shift_a`, `shift_b` | `argument` (noise) | `shift`: szum w (x/4,y/4,z/4) * 4; `shift_a`: (x/4, 0, z/4) * 4; `shift_b`: (z/4, x/4, 0) * 4 - używane jako shift_x/z dla shifted_noise |
| `spline` | `spline` {`coordinate` (DF), `points` [{`location`, `value` (double lub spline), `derivative`}]} | spline kubiczny na wartości DF; rdzeń "terrain shaper" (offset/factor/jaggedness) |
| `y_clamped_gradient` (1.21.x) | `from_y`, `to_y`, `from_value`, `to_value` | liniowy gradient od Y; poza zakresem wartości brzegowe; w 26.x zastąpiony przez `gradient` |
| `cache_2d`, `flat_cache`, `cache_once`, `cache_all_in_cell` (1.21.x) | `argument` | markery cache: `flat_cache` - raz na kolumnę (na komórkę 4x4), `cache_2d` - ignoruje Y, `cache_once` - raz na pozycję, `cache_all_in_cell` - dla beardifiera; w 26.3-snap10 zastąpione jednym `cache` |
| `interpolated` | `argument` (1.21.x); w 26.x dodatkowo `cell_size_xz`, `cell_size_y` | próbkuje w narożnikach komórki i interpoluje trójliniowo - klucz do wydajności |
| `blend_alpha`, `blend_offset`, `blend_density` | `argument` (dla blend_density) | mieszanie ze starym terenem (chunki z poprzednich wersji) |
| `weird_scaled_sampler` (1.21.x) | `input` (DF), `noise`, `rarity_value_mapper` (`type_1`/`type_2`) | skalowany szum jaskiń (spaghetti/cheese) |
| `range_choice` | `input`, `min_inclusive`, `max_exclusive`, `when_in_range`, `when_out_of_range` | if-then-else po zakresie |
| `clamp` | `input`, `min`, `max` | ograniczenie |
| `abs`, `square`, `cube`, `half_negative`, `quarter_negative`, `squeeze` | `argument` | `half_negative`: `x<0 ? x/2 : x`; `quarter_negative`: `x<0 ? x/4 : x`; `squeeze`: clamp do [-1,1], potem `x/2 - x^3/24` |
| `add`, `mul`, `min`, `max` | `argument1`, `argument2` | arytmetyka dwuargumentowa |
| `old_blended_noise` | `xz_scale`, `y_scale`, `xz_factor`, `y_factor`, `smear_scale_multiplier` | szum "base_3d_noise" z ery 1.17 |
| `end_islands`, `beardifier` | - | specjalne (End, adaptacja terenu pod struktury) |

Nowe w 26.x (wg wiki): `ceil`, `floor`, `round`, `truncate`, `div`, `sub`, `pow`, `sqrt`, `log`, `negate`, `reciprocal`, `sign`, `lerp` (`first*(1-alpha)+second*alpha`), `gradient` (z trybami `clamp_to_edge`, `repeat`, `mirrored_repeat`), `interval_select`, `distance_to_point` (euclidean/euclidean_squared/manhattan/chebyshev), `find_top_surface`, `ore_vein` (raw_ore_block, filler_block), `slice`, `end_outer_islands`.

Pliki wanilii Overworld (`data/minecraft/worldgen/density_function/overworld/`): `offset`, `factor`, `jaggedness`, `depth`, `sloped_cheese`, `base_3d_noise`, `continents`, `erosion`, `ridges`, `ridges_folded`, plus katalog `overworld/caves/*` (spaghetti_2d, spaghetti_roughness_function, entrances, noodle, pillars). Kompozycja (znana z dekompilacji wanilii 1.18-1.21; do potwierdzenia na konkretnym pliku JSON):
- `depth = y_clamped_gradient(from_y=-64,to_y=320,from=1.5,to=-1.5) + offset`
- `sloped_cheese = 4 * quarter_negative(depth * factor) + jaggedness*... + base_3d_noise`
- `final_density` = min(sloped_cheese, jaskinie) z `squeeze`, `blend_density`, `interpolated`; wartość > 0 -> blok stały (default_block), <= 0 -> powietrze lub płyn wg aquifers.

### 2.2. noise_settings i noise_router

Struktura pliku `worldgen/noise_settings/overworld.json` (minecraft.wiki "Noise settings"):

| Pole | Typ | Overworld | Znaczenie |
|---|---|---|---|
| `sea_level` | int | 63 | poziom wody; UWAGA: spawn mobów używa stałej 63 niezależnie od tego pola |
| `disable_mob_generation` | bool | false | wyłącza spawn podczas generacji chunku |
| `aquifers_enabled` | bool | true | akwifery; przy false większość jaskiń pod sea_level zalana wodą |
| `ore_veins_enabled` | bool | true | żyły rud (1.18+) |
| `legacy_random_source` | bool | false | RNG sprzed 1.18 |
| `default_block` | block state | `minecraft:stone` | blok "skały" |
| `default_fluid` | block state | `minecraft:water` | płyn mórz/jezior |
| `noise.min_y` | int | -64 | od -2032 do 2031, wielokrotność 16 |
| `noise.height` | int | 384 | 0 do 4064, wielokrotność 16; `min_y + height <= 2032` |
| `noise.size_horizontal` (1.21.x) | int | 1 | komórka pozioma = 4*size (4 bloki); usunięte w 26.3-snap10 (przeniesione do `interpolated`) |
| `noise.size_vertical` (1.21.x) | int | 2 | komórka pionowa = 4*size (8 bloków); jw. |
| `noise_router` | obiekt | - | patrz niżej |
| `spawn_target` | lista | parametry klimatu | punkty, wokół których gra szuka spawnu |
| `surface_rule` (1.21.x) / `material_rule` (26.x) | reguła | - | wybór bloków powierzchni |
| `debug_functions` (26.x) | lista | - | DF wyświetlane w debug |

Pola `noise_router` (każde to density function lub odwołanie do pliku DF):

| Pole | Rola |
|---|---|
| `barrier` | bariera akwiferów (czy woda może "przeciekać" między akwiferami) |
| `fluid_level_floodedness` | stopień zalania akwiferów |
| `fluid_level_spread` | rozrzut poziomu wody akwiferów |
| `lava` | czy akwifer jest lawowy |
| `temperature`, `vegetation`, `continents`, `erosion`, `depth`, `ridges` | 6 parametrów klimatu podawanych do multi_noise biome source (vegetation = humidity, ridges = weirdness) |
| `initial_density_without_jaggedness` | gęstość wstępna używana do heightmapy wstępnej (`above_preliminary_surface`, umieszczanie struktur) |
| `final_density` | gęstość końcowa: > 0 blok stały |
| `vein_toggle`, `vein_ridged`, `vein_gap` | żyły rud (miedź/żelazo) |

Wanilia: `continents`, `erosion`, `ridges`, `temperature`, `vegetation` to `shifted_noise` z `shift_x = overworld/shift_x`... - w 26.x zamienione na `noise` + `shift`. UWAGA WERSJI: w wersji 26.3-snap10 usunięto `size_horizontal`/`size_vertical` z `noise_settings` (wielkość komórki jest teraz parametrem `interpolated`).

### 2.3. surface_rules

Typy reguł (minecraft.wiki "Surface rule"):

| Typ reguły | Pola | Semantyka |
|---|---|---|
| `block` | `result_state` {Name, Properties} | ustawia blok |
| `sequence` | `sequence` [reguły] | pierwsza pasująca reguła wygrywa |
| `condition` | `if_true` (warunek), `then_run` (reguła) | warunkowo |
| `bandlands` | - | pasy terakoty (badlands) |

Typy warunków:

| Warunek | Pola | Semantyka |
|---|---|---|
| `biome` | `biome_is` [lista id] | biom w pozycji |
| `noise_threshold` | `noise`, `min_threshold`, `max_threshold` | szum 2D kolumny w zakresie |
| `vertical_gradient` | `random_name`, `true_at_and_below` (anchor), `false_at_and_above` (anchor) | losowe przejście; prawdopodobieństwo `(false_at_and_above - Y)/(false_at_and_above - true_at_and_below)` - tak robione deepslate (0..8) i bedrock |
| `y_above` | `anchor` {absolute/above_bottom/below_top}, `surface_depth_multiplier` (-20..20), `add_stone_depth` | Y powyżej progu (+ głębokość powierzchni * mnożnik) |
| `water` | `offset`, `surface_depth_multiplier`, `add_stone_depth` | pozycja nad wodą; brak wody nad blokiem -> warunek zawsze prawdziwy |
| `temperature` | - | biom wystarczająco zimny na śnieg (temperatura biomu w danej pozycji, z uwzględnieniem wysokości) |
| `steep` | - | stromy stok od strony N lub E (użyte do kamienia na zboczach gór) |
| `hole` | - | kolumny z surface depth = 0 |
| `above_preliminary_surface` | - | nad wstępną powierzchnią (kilka bloków pod powierzchnią, bez uwzględnienia jaskiń) - zapobiega trawie w jaskiniach |
| `stone_depth` | `surface_type` (floor/ceiling), `offset`, `add_surface_depth`, `secondary_depth_range` | odległość od powierzchni (z góry lub od dołu) |
| `not` | `invert` | negacja |

Kluczowe wielkości:
- Surface depth (na kolumnę, int) = `floor(surface(X,0,Z) * 2.75 + 3.0 + positional_noise(X,0,Z) * 0.25)` z szumu `minecraft:surface` - to grubość warstwy "gleby" (typowo 3-4 bloki).
- Secondary surface depth: -1..1 z szumu `minecraft:surface_secondary`.
- Generator śledzi na blok: `stoneDepthAbove` (odległość do powierzchni nad), `stoneDepthBelow` (do pustki pod), `waterHeight`.

Schemat wanilii Overworld (uproszczony, ilustracyjny; kolejność potwierdzona semantyką, konkretne wartości do sprawdzenia w overworld.json):
1. Bedrock: `vertical_gradient` (`random_name: minecraft:bedrock_floor`, true_at_and_below `above_bottom: 0`, false_at_and_above `above_bottom: 5`).
2. Warstwa "floor" (`stone_depth floor, offset 0, add_surface_depth: true`) -> per-biom: grass_block / sand (plaże, pustynie) / gravel / snow itd., z warunkami `water`, `y_above`, `noise_threshold` (np. `minecraft:surface` dla łat gravel/coarse dirt).
3. Podpowierzchnia (`stone_depth floor, offset -1..-6, add_surface_depth`) -> dirt / sandstone / packed_ice.
4. Deepslate: `vertical_gradient` (`random_name: minecraft:deepslate`, true_at_and_below `absolute: 0`, false_at_and_above `absolute: 8`).
5. Domyślnie `default_block` (stone).

Dla moda: to właśnie surface_rules pozwalają zrobić nowe "ściółki" (np. własny blok `igliwie`, `ściółka liściasta`, `torf`, `less`, `piasek wydmowy`) zależnie od biomu, wysokości, nachylenia (`steep`), szumu i głębokości.

## 3. Multi-noise biome source i parametry biomów

Biomy Overworldu wybiera `minecraft:multi_noise` na podstawie sześciu parametrów klimatu liczonych przez density functions z `noise_router` - te same, które kształtują teren. Poniżej zakresy wanilii i możliwości dodawania własnych biomów.

### 3.1. Parametry i zakresy

Multi-noise biome source (`"type": "minecraft:multi_noise"`) wybiera biom na podstawie 6 parametrów liczonych z `noise_router`: `temperature`, `humidity` (= `vegetation`), `continentalness` (= `continents`), `erosion`, `weirdness` (= `ridges`), `depth`. Każdy wpis listy `biomes` ma `biome` (id) i `parameters` {temperature, humidity, continentalness, erosion, weirdness, depth: wartość lub [min, max] w zakresie -2..2; `offset`: 0..1}. Wybierany jest biom o najmniejszej odległości (suma kwadratów różnic parametrów + offset^2) - wg minecraft.wiki "World generation"/"Biome".

Progi wanilii Overworld (minecraft.wiki "Biome", potwierdzone w wyszukiwaniu 2026-09-21):

| Parametr | Poziomy i granice |
|---|---|
| temperature (5 poziomów) | 0: -1.0..-0.45; 1: -0.45..-0.15; 2: -0.15..0.2; 3: 0.2..0.55; 4: 0.55..1.0 |
| humidity (5 poziomów) | 0: -1.0..-0.35; 1: -0.35..-0.1; 2: -0.1..0.1; 3: 0.1..0.3; 4: 0.3..1.0 |
| erosion (7 poziomów) | 0: -1.0..-0.78; 1: -0.78..-0.375; 2: -0.375..-0.2225; 3: -0.2225..0.05; 4: 0.05..0.45; 5: 0.45..0.55; 6: 0.55..1.0 |
| continentalness | mushroom fields: -1.2..-1.05; deep ocean: -1.05..-0.455; ocean: -0.455..-0.19; coast: -0.19..-0.11; near inland: -0.11..0.03; mid inland: 0.03..0.3; far inland: 0.3..1.0 (POTWIERDZONE: minecraft.wiki "World generation") |
| weirdness -> peaks & valleys (PV) | PV ("ridges folded") = `1 - abs(3*abs(weirdness) - 2)`; valleys: -1.0..-0.85; low: -0.85..-0.2; mid: -0.2..0.2; high: 0.2..0.7; peaks: 0.7..1.0 (POTWIERDZONE: minecraft.wiki "World generation") |
| depth | ok. 0 na powierzchni, rośnie o 1/128 (0,0078125) na każdy blok w dół (POTWIERDZONE) - używane dla biomów jaskiniowych |

Reguły wyboru tabel (wiki "World generation"): biomy nie-lądowe (oceany, plaże, mushroom fields) zależą tylko od temperature i continentalness; biomy lądowe zależą od erosion i PV, a w obrębie danej "tabeli" (middle/plateau/shattered/beach/peaks) od temperature x humidity (x weirdness dla wariantów). Wiki zaznacza, że parametr temperature to nie to samo co własność `temperature` biomu, ale z grubsza sobie odpowiadają (poziom 0 = śnieg/lód). Szczegółowe tabele "middle biomes" itd. NIE PRZEPISANO (są na wiki "Biome" -> Generation; do skopiowania przy projektowaniu listy biomów).

Semantyka (wiki "World generation"): continentalness rozstrzyga ocean/plaża/ląd (wyższa = bardziej w głąb lądu); erosion: wysoka = płasko, niska = górzysto; weirdness decyduje o wariantach biomów i "shattered" terenie; temperatura wanilii zależy dodatkowo od wysokości (spadek powyżej Y ~ 80 - w kodzie: `temperature - (y - 80)/... `, dokładny wzór NIE ZBADANO).

Kluczowy wniosek: w wanilii **ta sama** 6-wymiarowa przestrzeń parametrów steruje jednocześnie kształtem terenu (przez spline `offset`/`factor`/`jaggedness` w DF) i wyborem biomu. Dlatego "biom" i "wysokość" są spójne. W generatorze opartym na heightmapie ta spójność musi być zapewniona inaczej: albo (i) obliczamy parametry klimatu z danych (wysokość, odległość od morza, pokrycie terenu) i podajemy je jako DF do multi_noise, albo (ii) piszemy własny `BiomeSource`, który czyta rastry pokrycia/siedlisk bezpośrednio.

### 3.2. Dodawanie własnych biomów (data pack vs TerraBlender vs Fabric API)

Cztery drogi (źródła: minecraft.wiki "Custom"/"Dimension definition", github.com/TerraformersMC/Biolith, modrinth.com/mod/biolith, github.com/Apollounknowndev/lithostitched):

| Droga | Jak działa | Zalety | Wady |
|---|---|---|---|
| **A. Własny data pack z własną listą `multi_noise`** (nadpisanie `data/minecraft/dimension/overworld.json` lub własny `world_preset`) | mod dostarcza kompletną listę `biomes` z parametrami; może też podmienić `noise_settings` | pełna kontrola; tak robią Terralith, Tectonic, WWOO | konflikt z każdym innym modem, który nadpisuje tę samą listę; nie da się "dodać" biomu, tylko podmienić całość |
| **B. Własny `world_preset`** (`data/<modid>/worldgen/world_preset/polska.json`, tag `#minecraft:normal` by pojawił się w menu) | świat tworzony z wybranym presetem, wanilia nietknięta | zgodność z innymi modami (ich biomy trafiają do zwykłego Overworldu, a nasz preset ma własny) | gracz musi wybrać preset; mody biomowe nie "wstrzykną" biomów do naszego presetu |
| **C. TerraBlender** (Glitchfiend; Fabric/Forge/NeoForge) | API "regions": każdy mod rejestruje region z wagą i własną listą parametrów; TerraBlender przełącza regiony szumem | standard dla BOP/Regions Unexplored; Tectonic z nim współpracuje | dodatkowa zależność; wersje na 26.x NIE SPRAWDZONO |
| **D. Biolith** (TerraformersMC; Fabric) | "biome placement mod focusing on configurability and consistent distribution of modded biomes"; od 2.0.0-alpha.1 rozmieszczanie biomów i surface rules przez data pack (umieszczenie biomu w punkcie szumu); sub-biomy; zgodny z TerraBlender i Fabric Biome API; od 3.0.3 działa z Moderner Beta (1.21+) | lekki, data-packowy, Fabric-first | mniej rozpowszechniony niż TerraBlender |
| **E. Lithostitched** (Apollounknowndev - autor Tectonic) | biblioteka worldgen z opcjonalnym systemem regionów jako rejestr data packu z wagami | używana przez Tectonic; możliwa w przyszłości implementacja API TerraBlender na Lithostitched | zależność |

Fabric API (`fabric-biome-api-v1`): od 1.18 pozwala dodawać biomy do Netheru i Endu oraz modyfikować istniejące biomy (`BiomeModifications`: spawny, feature'y), ale **nie ma API do dodawania biomów do Overworldu multi_noise** - stąd TerraBlender/Biolith (stan wg wiedzy autora raportu; wiki.fabricmc.net nie zostało w tej turze odczytane - DO POTWIERDZENIA).

Rekomendacja wstępna: dla świata "Polska z rzeczywistych danych" (sekcja 7) najsensowniejszy jest **własny `world_preset` + własny ChunkGenerator/BiomeSource** (droga B), bo biomy mają wynikać z map, nie z szumu. Dla wariantu czysto proceduralnego - droga A lub D.

## 4. Jak realistyczny teren robią mody

Przegląd pięciu rodzin: (1) data packi DF (Tectonic), (2) data packi biomowe (Terralith, WWOO), (3) platformy generacji (Terra), (4) generatory z danych Ziemi (Terra 1-to-1/Terra++/BTE, Tellus), (5) generatory obrazkowe (Atlas/NovoAtlas).

### 4.1. Tectonic / Terratonic

Źródło: modrinth.com/mod/tectonic (pobrane 2026-09-21), github.com/Apollounknowndev/tectonic.

- Charakter: data pack + mod (Fabric, Forge, NeoForge, Quilt, czysty data pack). Licencja MIT. 16,4 mln pobrań, 7,3 tys. obserwujących, ostatnia aktualizacja "20 godzin temu" (wrzesień 2026), opublikowany 4 lata temu. GitHub: 156 gwiazdek, 25 forków, 111 otwartych issues.
- Wspierane wersje wg Modrinth: **1.21.1 i 26.1** (inne wersje nie dostają poprawek). Czyli Tectonic już przeszedł na numerację 26.x.
- Sposób działania: wyłącznie **density functions w JSON** (nadpisuje `minecraft:overworld` noise_settings i pliki DF) - brak własnego generatora w Javie; wersja "mod" dodaje tylko ekran konfiguracji. To dowód, że sam JSON wystarcza na duże, złożone rzeźby.
- Co generuje: kontynenty "tysiące do dziesiątek tysięcy bloków" szerokości; pasma górskie ciągnące się dziesiątki tysięcy bloków, "zbliżające się do limitu budowania"; głębsze oceany (do warstwy deepslate) z nawisami i dolinami; rzeki podziemne łączące się z rzekami powierzchniowymi; tunele lawowe; "jungle pillars" >100 bloków; kaniony (badlands); wydmy; wielopoziomowe płaskowyże z rampami; doliny otoczone płaskowyżami; mokradła (las/dżungla/tajga przy poziomie morza z licznymi zbiornikami).
- Kompatybilność: mody biomowe przez TerraBlender lub **Biolith** (Biomes O' Plenty, Regions Unexplored, Nature's Spirit). Osobne wersje dla Terralith ("Terratonic" - samodzielny data pack zapewniający zgodność z Terralith; w wersji mod wbudowany) i CliffTree.
- Konfiguracja (github.com/Apollounknowndev/tectonic/wiki/Config, pobrane 2026-09-21):
  - **Increased Height**: "Increases the max build and generation height to y640" (czyli `height` podniesione tak, by szczyt był na Y=640; przy `min_y=-64` daje to `height=704` = 44 sekcje; wiki nie podaje min_y, wymogu nowego świata ani ostrzeżeń wydajnościowych - to jedyny udokumentowany parametr).
  - **Vertical Scale**: rozciąga teren nad poziomem morza (2x = dwukrotnie wyższe góry) - dowód, że skalowanie pionowe da się zrobić prostym mnożnikiem w DF.
  - **Ultrasmooth**: silne wygładzenie ("jak Tectonic v2"), problemy w głębokich oceanach i biomach windswept.
  - **Ocean Offset** (wartości > -0.2 eliminują oceany), **Underground Rivers**, **Flat Terrain Skew** (próg płaskowyż/równina).
  - **Ocean Depth / Deep Ocean Depth**: 0 = poziom morza, -0.5 = 64 bloki poniżej.
  - Jaskinie: cheese/noodle/spaghetti z gęstościami; **Lava Tunnels**; **Snow Start Offset** (poziom Y śniegu w zimnych biomach).
- Wniosek dla moda: Tectonic pokazuje, że "increased height" do Y=640 jest praktykowane masowo (16 mln pobrań) bez dramatu wydajnościowego; przejście na 2500+ bloków to jednak inna liga (ok. 4x więcej sekcji niż Tectonic) i wymaga własnych testów.

### 4.2. Terralith (Stardust Labs), Nullscape

Źródło: modrinth.com/datapack/terralith (pobrane 2026-09-21), github.com/Stardust-Labs-MC/Terralith, stardustlabs.miraheze.org/wiki/Terralith.

- Terralith: data pack (i wersja mod: Fabric, Forge, NeoForge, Quilt) dodający **ponad 95 nowych biomów** wyłącznie z bloków wanilii (bez nowych bloków - ważna różnica względem naszego moda). Wersje MC: **1.18.2 - 1.21.x**; 23 mln pobrań, 8,6 tys. obserwujących; ostatnia aktualizacja "2 miesiące temu" (lipiec 2026) - status wsparcia 26.x NIE POTWIERDZONO. Licencja: "Stardust Labs License" (własna; nie jest to licencja otwarta - trzeba sprawdzić przed kopiowaniem czegokolwiek).
- Podejście: własny `noise_settings` + własna lista biomów w `multi_noise` (nadpisanie `dimension/overworld.json`), nowe biomy zajmują "nisze" w przestrzeni parametrów (weirdness/erosion/continentalness) obok biomów wanilii; teren: kaniony, shattered biomes, wyspy latające, głębokie rowy oceaniczne, biomy jaskiniowe, Skylands. Przykładowe biomy: Yellowstone, Yosemite Cliffs, Volcanic Peaks, Moonlight Grove.
- Kompatybilność: z Tectonic tylko w wersji mod (data pack wymaga Terratonic); z modami biomowymi na TerraBlender; z data packami biomowymi - zwykle nie (bo obie strony nadpisują tę samą listę multi_noise). Nie da się usunąć ze świata po utworzeniu.
- Nullscape: projekt Stardust Labs dla wymiaru End (nie dotyczy Overworldu) - NIE BADANO szczegółowo.
- Dokładne użycie weirdness/erosion przez Terralith (które zakresy parametrów zajmuje) NIE ZBADANO - wymaga przejrzenia `data/minecraft/dimension/overworld.json` w repozytorium.

Wniosek: konflikt "kto nadpisuje listę biomów multi_noise" to zasadniczy problem współistnienia modów worldgen na Fabric; dlatego powstały TerraBlender i Biolith (sekcja 3.2).

### 4.3. William Wythers' Overhauled Overworld

Źródła: modrinth.com/mod/wwoo (wersje 2.0.1, 2.6.1), curseforge, modrinth.com/datapack/william-wythers-overhauled-overworld-(datapack).

- Podejście: przebudowa **wszystkich** biomów wanilii na "rodziny" wariantów regionalnych (od tajg borealnych po atole i lasy deszczowe) plus rzadkie strefy przejściowe tam, gdzie biomy się stykają - cel: bardziej realistyczny, spójny, "atmosferyczny" świat bez ostrych granic biomów. Drzewa różnych kształtów i rozmiarów; w wyżynach częste klify i piargi ("scree escarpments"); skały wanilii przypisane do biomów (dioryt we flower forest, granit w sunflower plains/birch forest, andezyt w meadow/cherry grove).
- Wyłącznie bloki wanilii (serwer z modem, klienci vanilla). Wersja: WWOO 2.6.1; wynik wyszukiwania podaje wsparcie "1.21.11 Fabric" (to sugeruje, że linia 1.21.x doszła do 1.21.11 przed zmianą numeracji - NIE ZWERYFIKOWANO).
- Znaczenie dla nas: WWOO to najlepszy wzorzec "sub-biomów i przejść" - dokładnie to, czego potrzebuje polski las (np. bór sosnowy świeży -> bór mieszany -> grąd -> łęg wzdłuż gradientu wilgotności), ale robi to szumem, nie danymi.

### 4.4. Terra (PolyhedralDev)

Źródło: github.com/PolyhedralDev/Terra (pobrane 2026-09-21).

- "Modern world generation modding platform, primarily for Minecraft": API generacji wokselowej z naciskiem na konfigurację i rozszerzalność, loader addonów niezależny od platformy, "core addons" z domyślnymi konfiguracjami. Konfiguracja w plikach YAML ("config packs").
- Platformy oficjalne wg README: **Fabric** oraz **Bukkit/Paper**; README zachęca do PR-ów z innymi platformami (Forge/NeoForge nie wymienione jako wspierane w README). Wersje MC i data ostatniego wydania NIE ODCZYTANE z README (do sprawdzenia na Modrinth: modrinth.com/mod/terra).
- Licencje: API i core addons - MIT; implementacje platform - GPLv3 (ważne: mod używający Terra jako biblioteki na Fabric łączy się z kodem GPLv3).
- Generacja z obrazu/heightmapy: README nie wspomina. Z wiedzy autora raportu Terra ma addon `config-noise-function` z samplerem typu `IMAGE` (próbkowanie kanału z pliku PNG) oraz addon `biome-provider-image` (biomy z obrazu) - **NIE POTWIERDZONO w tej turze**; trzeba sprawdzić w dokumentacji terra.polydev.org. Nawet jeśli istnieje, to sampler PNG 8-bitowy (0-255) nie odda 2500 m zakresu z rozdzielczością 1 m - potrzebne byłyby 16-bitowe rastry lub własny addon.
- Wniosek: Terra to alternatywa "generator w Javie z konfiguracją YAML" - dojrzała, ale to ciężka zależność (GPL na platformie, własny ekosystem addonów, własny system biomów niezależny od multi_noise wanilii). Dla moda skupionego na lasach i faunie zależność od Terra oznaczałaby, że biomy/feature'y trzeba definiować w konwencji Terra zamiast wanilii.

### 4.5. Terra 1-to-1 / Terra++ / Build the Earth i inne mody "Earth"

Źródła: modrinth.com/mod/terraplusplus, curseforge "terra-1-to-1-minecraft-world-project", github.com/orangeadam3/terra121, github.com/BuildTheEarth/terraplusplus, en.wikipedia.org/wiki/Build_the_Earth.

- **Terra 1-to-1 (terra121)**: mod Forge dodający typ świata generujący Ziemię w skali 1:1 z publicznych zbiorów danych online (teren, biomy, drzewa, drogi). Nie jest formalnie częścią Build the Earth, ale jest używany w ich modpacku, a autorzy współpracują z zespołem BTE.
- **Terra++ (TerraPlusPlus)**: fork Terra 1-to-1 nastawiony na wydajność i poprawki. Modrinth: **tylko Minecraft 1.12.2, tylko Forge**, ostatnia aktualizacja "5 lat temu" na Modrinth (aktywny rozwój na GitHubie BuildTheEarth/terraplusplus). Wymaga: Forge, **CubicChunks, CubicWorldGen** (dlatego może mieć wysokość terenu 1:1 - CubicChunks znosi limit wysokości). Dane: wysokości i biomy z publicznych zbiorów (Terra++ używa kafli wysokościowych AWS Terrain Tiles / Mapzen "terrarium" - wg wiedzy autora raportu, NIE POTWIERDZONO w tej turze na stronie źródłowej), OpenStreetMap (drogi, budynki, wody), Treecover2000 v1.7 (Hansen Global Forest Change) do rozmieszczania drzew. Licencja MIT.
- Projekcja: BTE używa zmodyfikowanej projekcji Dymaxion ("Airocean"/"BTE modified") - do potwierdzenia w kodzie `projection/` w repozytorium (NIE POTWIERDZONO w tej turze).
- **Brak wersji Fabric i brak portu na 1.21+** wg Modrinth (stan 2026-09-21). Wyszukiwanie "Terra 1-to-1 Fabric 1.21" nie zwróciło żadnego portu Fabric.
- Wniosek: nie ma gotowego generatora "z DEM" na Fabric 1.21+/26.x; podejście z rzeczywistym NMT trzeba zaimplementować samodzielnie (sekcja 7), ale architektura Terra++ (kafle pobierane online, cache, CubicChunks do wysokości) jest wzorcem.

**Inne mody "Earth"/obrazkowe na Fabric (wyszukiwanie Modrinth 2026-09-21; loadery i wersje do potwierdzenia na stronach projektów):**

| Mod | Co robi | Dane / format | Uwagi |
|---|---|---|---|
| **Tellus** (modrinth.com/mod/tellus) | odtwarza rzeczywisty teren, budynki i drogi; "Earth-scale landscapes" z danych geograficznych i OSM; wysokości 30 m globalnie, gęściej tam, gdzie dostępne; biomy, miasta, klimatyczna pogoda/czas | pobiera online, **wymaga aktywnego połączenia z internetem** (nie działa offline) | najbliższy współczesny odpowiednik Terra++; loader/wersja MC NIE ODCZYTANE (do sprawdzenia) |
| **NovoAtlas** (modrinth.com/mod/novoatlas) | generacja z **heightmapy PNG w skali szarości**; zalecany WorldPainter do tworzenia map; 1.21+, także NeoForge; lepsze łączenie struktur z terenem, jaskinie podwodne, mapy biomów jaskiniowych | PNG 8-bit (256 poziomów) | ograniczenie 8-bit: 2500 m / 256 = ~10 m na poziom szarości - za mało dla nizin |
| **Atlas** (modrinth.com/mod/atlas) | "data-driven image-based world generator": heightmapa + mapa biomów jako obrazy wpięte w wymiar data packu | PNG | pierwowzór NovoAtlas |
| **McOSM** (modrinth.com/mod/mcosm) | import danych OpenStreetMap do Minecrafta, zgodny z Build The Earth | OSM | drogi/budynki, nie teren |
| **realterrain** (github.com/bobombolo/realterrain) | generator z rzeczywistych danych terenu | NIE ZBADANO | do sprawdzenia |
| Kolekcja "Fabric Terrain Gen" (modrinth.com/collection/PyAEVyNN) | lista modów terenowych na Fabric | - | punkt startowy do przeglądu |
| Narzędzie zewnętrzne "Minecraft Earth Map Generator" (minecraftmaps.com/tools/earth-map-generator) | teren z satelitarnych danych wysokościowych, pokrycie (las/piasek/woda) z **ESA WorldCover**, budynki/drogi/wody z OSM; generuje gotową mapę | eksport świata | dowód, że para "DEM + WorldCover + OSM" jest standardowym przepisem |

Wniosek: istnieją na Fabric 1.21+ generatory obrazkowe (NovoAtlas/Atlas) i "online-Earth" (Tellus), ale żaden nie łączy: (1) NMT 1-5 m o pełnej dynamice 16-bit, (2) map siedlisk leśnych, (3) własnych biomów/bloków. Kod NovoAtlas/Atlas warto przejrzeć jako referencję wczytywania rastrów do ChunkGeneratora.

## 5. Źródła danych geoprzestrzennych dla Polski

Zestawienie zbiorcze (szczegóły w podsekcjach; "otwarte" = bezpłatne do dowolnego użytku wg źródła; pozycje oznaczone "?" wymagają potwierdzenia):

| Zbiór | Dostawca | Rozdzielczość / skala | Format | Licencja | Rozmiar (szac.) |
|---|---|---|---|---|---|
| NMT 1 m | GUGiK (ALS/ISOK) | 1 m | ASCII GRID (.asc), GeoTIFF przez WCS | otwarte (PGiK, od 31.07.2020) | ~1,25 TB float32 dla kraju (szac.) |
| NMT 5 m | GUGiK | 5 m | ASCII GRID | otwarte | ~50 GB float32 (szac.) |
| Copernicus DEM GLO-30 | ESA/Copernicus | 30 m (DSM) | GeoTIFF (COG) | otwarte | Polska: ~1,4 GB (szac.) |
| FABDEM | Univ. of Bristol | 30 m (DTM) | GeoTIFF | CC BY-NC-SA 4.0 | jw. |
| CORINE CLC2018 | GIOŚ / Copernicus Land | 1:100 000, MMU 25 ha | .gdb (GIOŚ); SHP/GPKG/GeoTIFF 100 m (Copernicus) | otwarte (Copernicus); GIOŚ ? | setki MB |
| BDOT10k | GUGiK | 1:10 000 | GPKG (paczka krajowa), GeoParquet, SHP/GML (WODGiK) | otwarte (art. 40a PGiK, od 31.07.2020) | kilka-kilkanaście GB (szac.) |
| BDL (opis taksacyjny + wydzielenia) | Lasy Państwowe / BULiGL | wydzielenie (ha) | TXT + ESRI Shapefile; WMS | ? (formularz automatyczny) | GB (szac.) |
| ESA WorldCover 2021 | ESA | 10 m | GeoTIFF | CC BY 4.0 (?) | Polska ~1 GB (szac.) |
| Copernicus HRL Forest 2018 | Copernicus Land | 10 m | GeoTIFF | otwarte (?) | jw. |
| MPHP10 | Wody Polskie / IMGW | 1:10 000 | SHP/GPKG (?) | ? | ? |
| SMGP | PIG-PIB | 1:50 000 | WMS; arkusze (?) | ? | ? |
| Mapa glebowo-rolnicza | IUNG / WODGiK | 1:25 000 | SHP (?) | zróżnicowana (?) | ? |

### 5.1. Modele wysokościowe (NMT GUGiK, Copernicus DEM, SRTM, EU-DEM, NASADEM, FABDEM)

**NMT GUGiK** (geoportal.gov.pl/pl/dane/numeryczny-model-terenu-nmt/, pobrane 2026-09-21):
- Rozdzielczości: **1 m x 1 m** (model podstawowy z lotniczego skaningu laserowego ALS, w tym ISOK; aktualizowany systematycznie) oraz **5 m x 5 m** (z pomiarów stereoskopowych przy produkcji ortofotomapy o pikselu <= 10 cm).
- Formaty: Arc/Info ASCII GRID (`.asc`) - podstawowy; strona wspomina eksport GeoTIFF (przez WCS). Układ płaski: PL-1992 (**EPSG:2180**); układy wysokościowe: PL-KRON86-NH (dane 2000-2019) i PL-EVRF2007-NH (dane od 2018).
- Pobieranie: (1) moduł "Pobierz dane" na geoportalu (arkusze w podziale sekcyjnym 1:5000 / 1:10 000), (2) usługi **WMS/WCS** (WCS z limitem **7 km2 na zapytanie**), (3) WFS ze skorowidzami arkuszy (do skryptowego pobierania), (4) "Geoportal API" - NIE ZBADANO szczegółów.
- Licencja: "bezpłatnie i możliwe do dowolnego wykorzystania" - dane NMT są otwarte na mocy nowelizacji Prawa geodezyjnego i kartograficznego (od 31.07.2020; w zadaniu podano "od 2021" - faktyczna data wejścia w życie otwarcia NMT/ortofoto to **31 lipca 2020**; DO POTWIERDZENIA dokładna data).
- Dokładność: dla NMT 1 m z ALS błąd średni wysokości typowo <= 0,15-0,20 m (standard ISOK; NIE POTWIERDZONO na stronie w tej turze).
- Rozmiar danych: strona nie podaje. Szacunek własny: Polska 312 700 km2 = 3,127e11 komórek 1 m; w float32 to ok. **1,25 TB** surowych danych (bez kompresji); w 5 m: 1,25e10 komórek = ok. **50 GB** float32; w siatce 30 m (skala 1:1 poziomo dla świata MC i tak jest za dokładna): 3,5e8 komórek = 1,4 GB. Pliki ASCII GRID są ok. 2-3x większe niż binarne. Dla moda wystarczy NMT 5 m (a realnie przeskalowany do rozmiaru bloku) - patrz sekcja 6.

**Globalne/europejskie DEM** (zestawienie z wiedzy autora raportu; potwierdzenia w poszukiwaniu poniżej):

| Model | Rozdzielczość | Zakres | Licencja | Uwagi |
|---|---|---|---|---|
| Copernicus DEM GLO-30 | 30 m (1 arcsec) | globalny | otwarta (Copernicus, bezpłatna) | DSM (powierzchnia, nie teren), TanDEM-X 2011-2015; pobieranie z dataspace.copernicus.eu / AWS Open Data |
| Copernicus DEM GLO-90 / EEA-10 | 90 m / 10 m (tylko Europa, EEA-10 z ograniczeniami) | | | EEA-10 nie w pełni otwarty |
| SRTM 1 arcsec (SRTMGL1) | 30 m | 60N-56S | domena publiczna (NASA/USGS) | 2000 r., DSM radarowy, luki w górach |
| NASADEM | 30 m | jw. | domena publiczna | reprocessing SRTM (2020), lepsze wypełnienie luk |
| EU-DEM v1.1 | 25 m | Europa | otwarta (Copernicus) | hybryda SRTM+ASTER, 2011/2016; stary |
| FABDEM | 30 m | globalny | **CC BY-NC-SA 4.0** (niekomercyjna!) | Copernicus DEM z usuniętymi budynkami i lasami (DTM) |

Wniosek: dla Polski NMT GUGiK (1 m/5 m, DTM, otwarty, EPSG:2180) bije wszystko na głowę; globalne DEM mają sens tylko jako fallback lub do prototypu (GLO-30 jest DSM - w lasach zawyża teren o wysokość koron, 20-30 m!, co dla moda leśnego jest istotne).

### 5.2. Pokrycie terenu (CORINE, BDOT10k, Copernicus HRL, ESA WorldCover)

**CORINE Land Cover 2018 (Polska)** (clc.gios.gov.pl, pobrane 2026-09-21):
- Skala 1:100 000; **MMU 25 ha** dla poligonów, **100 m** szerokości dla obiektów liniowych; nomenklatura hierarchiczna 3-poziomowa (44 klasy na poziomie 3 w Europie; w Polsce występuje ich 31 - liczba z wiedzy autora, NIE POTWIERDZONO na stronie).
- Źródła: zobrazowania RapidEye i IRS-P6 (2011-2012, dla CLC2012) oraz Sentinel-2 i Landsat-8 (2017, dla CLC2018).
- Format: geobaza CLC2018 (`.gdb`, ESRI) z GIOŚ; w Copernicus Land Monitoring Service również shapefile/GeoPackage i raster GeoTIFF 100 m dla całej Europy.
- Licencja: bezpłatny dostęp (Copernicus Land: pełna otwarta licencja; GIOŚ: strona nie precyzuje - DO POTWIERDZENIA przy pobieraniu z clc.gios.gov.pl).
- Klasy leśne: 311 lasy liściaste, 312 lasy iglaste, 313 lasy mieszane, 324 lasy w stanie zmian (zręby, uprawy), 321/322/333 murawy/wrzosowiska/tereny słabo pokryte roślinnością; 411 bagna śródlądowe, 412 torfowiska; 231 łąki; 211 grunty orne; 141/142 zieleń miejska. CLC 2024 / CLC+ Backbone (10 m, 11 klas) nie wspomniane na stronie GIOŚ - NIE ZBADANO.
- Wniosek: CLC ma za grubą MMU (25 ha = np. kwadrat 500 x 500 m = 500 x 500 bloków w skali 1:1) do odwzorowania drobnej mozaiki lasów śródpolnych i mniejszych zadrzewień; nadaje się do makro-rozkładu (iglasty/liściasty/mieszany, mokradła, łąki). Do detali potrzebne BDOT10k / BDL / HRL 10 m.

**BDOT10k** (geoportal.gov.pl "Topographic Objects Database", gov.pl/web/gugik, dane.gov.pl - wyszukiwanie 2026-09-21):
- Baza Danych Obiektów Topograficznych o szczegółowości mapy topograficznej **1:10 000**; zawiera: budynki/budowle, sieć komunikacyjną, sieć wodną, tereny chronione, **kompleksy pokrycia terenu i użytkowania**, jednostki podziału terytorialnego, obiekty orientacyjne.
- Otwarta: od **31.07.2020** bez opłat (art. 40a Prawa geodezyjnego i kartograficznego); udostępniana także z wojewódzkich zasobów (WODGiK).
- Pobieranie: (1) geoportal.gov.pl -> "Dane do pobrania" -> Topografia -> BDOT10k -> "paczka krajowa": **połączone klasy obiektów dla całego kraju w GeoPackage (.gpkg)**; (2) usługa pobierania per klasa obiektów w formatach **GPKG i GeoParquet**; (3) portale wojewódzkie (np. mapy.lodzkie.pl, geoportal.podlaskie.eu, wgik.dolnyslask.pl) - często per powiat/gmina w SHP/GML.
- Klasy pokrycia terenu (kategoria PT, z wiedzy autora raportu, nazwy do potwierdzenia w specyfikacji BDOT10k 2021): PTLZ (lasy i zadrzewienia: atrybuty kategoria: las/zagajnik/zadrzewienie; rodzaj: liściasty/iglasty/mieszany), PTRK (roślinność krzewiasta), PTUT (uprawy trwałe: sady, plantacje), PTTR (roślinność trawiasta i uprawy rolne), PTWP (wody powierzchniowe), PTZB (zabudowa), PTKM (tereny pod drogami/kolejami), PTGN (grunt nieużytkowany - piaski, wydmy), PTWZ (wyrobiska), PTSO (składowiska), PTPL (place), PTNZ (pozostałe). Do tego SW (sieć wodna: SWRS rzeki/strumienie, SWKN kanały, SWRM rowy), OI (obiekty inne, np. OIPR - obiekty przyrodnicze: pomniki, głazy), TC (tereny chronione: parki narodowe, rezerwaty).
- Znaczenie: BDOT10k daje **rodzaj lasu (iglasty/liściasty/mieszany) i granice zadrzewień w skali 1:10 000** dla całego kraju - to mapa "gdzie jest las" o rząd wielkości dokładniejsza od CORINE, ale bez gatunku panującego i wieku (to ma BDL).

**Copernicus HRL Forest** i **ESA WorldCover** - NIE ZBADANO w tej turze (budżet). Z wiedzy autora raportu (do potwierdzenia): HRL Forest 2018: Tree Cover Density (0-100 %), Dominant Leaf Type (iglaste/liściaste), Forest Type - rastry **10 m**, otwarta licencja Copernicus; ESA WorldCover 2021: **10 m**, 11 klas (m.in. tree cover, shrubland, grassland, cropland, built-up, bare, water, herbaceous wetland), licencja **CC BY 4.0**, kafle 3x3 stopnia GeoTIFF. Dla Polski oba są mniej wartościowe niż BDOT10k+BDL, ale są proste do wczytania (raster) i mają jednolity format - dobre do prototypu.

### 5.3. Dane leśne (Bank Danych o Lasach)

Źródła: bdl.lasy.gov.pl/portal/ (pobrane), bdl.lasy.gov.pl/portal/wniosek, bdl.lasy.gov.pl/portal/udostepnianie, www9.bdl.lasy.gov.pl/portal/o-udostepnianiu, buligl.pl/en/w/projekt-bdl, lasy.gov.pl/pl/nasze-lasy/bank-danych-o-lasach.

- BDL (prowadzony przez BULiGL na zlecenie Lasów Państwowych) gromadzi: **opisy taksacyjne z planów urządzenia lasu dla lasów wszystkich form własności**, dane siedliskowe i fitosocjologiczne, Wielkoobszarową Inwentaryzację Stanu Lasu (WISL), gospodarkę łowiecką, pożary lasów, "lasy poza ewidencją". Portal: mapa interaktywna, generator raportów, aplikacja mobilna mBDL, mapa turystyczna.
- **Dane do pobrania**: dane źródłowe o lasach w zarządzie PGL Lasy Państwowe przez **zautomatyzowany system udostępniania** (formularz: bdl.lasy.gov.pl/portal/wniosek). Zestaw zawiera **opis taksacyjny w plikach tekstowych** oraz **geometrię oddziałów i wydzieleń w ESRI Shapefile**. Format GeoPackage - nie potwierdzony. Lasy prywatne: osobne aplikacje dla powiatów/wykonawców; ich dane w pobieraniu NIE POTWIERDZONE (prawdopodobnie tylko WMS/mapa).
- Usługi OGC: portal wymienia "Usługi OGC" (WMS; WFS - NIE POTWIERDZONO), konkretne URL-e nie zostały odczytane (spodziewane: mapserver.bdl.lasy.gov.pl - DO SPRAWDZENIA).
- Atrybuty opisu taksacyjnego (z wiedzy o strukturze SILP/BDL, nazwy pól DO POTWIERDZENIA w pobranym zestawie): gatunek panujący (np. SO, SW, BK, DB, BRZ, OL, JD, MD, GB, JW, LP, OS, TP, WZ, JS), udział, wiek, bonitacja, zadrzewienie, zwarcie, **typ siedliskowy lasu (TSL: Bs, Bśw, Bw, Bb, BMśw, BMw, BMb, LMśw, LMw, LMb, Lśw, Lw, Ol, OlJ, Lł, oraz górskie BG, BMG, LMG, LG, BWG, LłG i wyżynne)**, warstwy (drzewostan, podrost, podszyt), rodzaj powierzchni.
- Licencja/warunki: strona "O udostępnianiu" istnieje, ale treści warunków NIE ODCZYTANO; kontakt: bdl@bdl.lasy.gov.pl. Trzeba sprawdzić, czy dane są otwarte (dane publiczne LP) i czy dopuszczają redystrybucję w modzie (prawdopodobnie tak dla użytku niekomercyjnego - DO POTWIERDZENIA).
- Znaczenie: BDL to **jedyne źródło gatunku panującego, wieku drzewostanu i TSL** dla ~7,4 mln ha lasów LP (ok. 77 % lasów Polski). Wydzielenie (średnio kilka ha) to naturalna jednostka "mikrobiomu" leśnego w modzie: TSL -> zestaw runa/podszytu, gatunek panujący + wiek -> wysokość i gęstość drzew, martwe drewno.

### 5.4. Gleby, hydrografia, geologia

NIE ZBADANO w tej turze (wyczerpany budżet; wyszukiwanie łączone zwróciło wyłącznie wyniki o BDOT10k). Poniżej wiedza autora raportu do potwierdzenia:
- **Hydrografia**: MPHP10 (Mapa Podziału Hydrograficznego Polski 1:10 000; cieki, zbiorniki, zlewnie; PGW Wody Polskie/IMGW; dostępna na dane.gov.pl / hydroportal); dla moda praktyczniej użyć klas SW i PTWP z BDOT10k (te same rzeki, jednolity format) - rzeki i jeziora jako wektory, a poziom wody z NMT.
- **Gleby**: mapa glebowo-rolnicza 1:25 000 (IUNG-PIB; udostępniana przez wojewódzkie ODGiK, licencje zróżnicowane; kompleksy przydatności rolniczej i typy gleb) - dotyczy głównie gruntów rolnych, w lasach brak; dla lasów typ siedliskowy (BDL) zastępuje mapę glebową. Europejska SGDB (ESDB, 1:1 000 000) - za gruba.
- **Geologia**: Szczegółowa Mapa Geologiczna Polski **1:50 000** (SMGP, PIG-PIB) - arkusze w geologia.pgi.gov.pl, WMS "Geologia powierzchniowa"; Mapa Geologiczna Polski 1:500 000; dla moda przydatna do wyboru "skały macierzystej" pod glebą (piaski wydmowe, gliny zwałowe, lessy, wapienie Jury, granity Karkonoszy, piaskowce fliszowe Karpat) - czyli do surface_rules (sekcja 2.3) i do rozmieszczenia własnych bloków skalnych.
- Wszystkie trzy zbiory wymagają osobnej weryfikacji licencji i formatów.

## 6. Skala: rozmiar świata, chunki, dysk, czas generacji

Dane wejściowe: rozciągłość Polski ok. **649 km E-W x 689 km N-S**, powierzchnia **312 700 km2** (GUS; wartości z treści zadania, zgodne z Wikipedią). Skrajne wysokości: Rysy **2499 m n.p.m.**, depresja Raczki Elbląskie **-1,8 m** (wiedza ogólna, do potwierdzenia w raporcie geograficznym). Chunk = 16 x 16 bloków; plik regionu (`r.X.Z.mca`) = 32 x 32 chunki = 1024 chunki (minecraft.wiki "Region file format" - wiedza ogólna).

### 6.0. Rozmiar świata w blokach i chunkach (poziomo)

| Skala pozioma (1 blok = ? m) | Wymiary świata [bloki] | Chunki (po powierzchni 312 700 km2) | Chunki (prostokąt obejmujący) | Pliki regionów (po powierzchni) |
|---|---|---|---|---|
| 1:1 (1 m) | 649 000 x 689 000 | 1,22 x 10^9 | 1,75 x 10^9 | 1,19 x 10^6 |
| 1:2 (2 m) | 324 500 x 344 500 | 3,05 x 10^8 | 4,37 x 10^8 | 2,98 x 10^5 |
| 1:4 (4 m) | 162 250 x 172 250 | 7,63 x 10^7 | 1,09 x 10^8 | 7,46 x 10^4 |
| 1:10 (10 m) | 64 900 x 68 900 | 1,22 x 10^7 | 1,75 x 10^7 | 1,19 x 10^4 |
| 1:20 (20 m) | 32 450 x 34 450 | 3,05 x 10^6 | 4,37 x 10^6 | 2 983 |
| 1:50 (50 m) | 12 980 x 13 780 | 4,89 x 10^5 | 6,99 x 10^5 | 477 |

Wzory: chunki = powierzchnia [m2] / (256 * s^2), gdzie s = metry na blok; regiony = chunki / 1024.

World border: domyślna granica świata to +/- 29 999 984 bloków (ok. 60 mln bloków szerokości) - Polska w skali 1:1 (649 tys. bloków) zajmuje ok. 1 % tej rozpiętości; **world border nie jest ograniczeniem** w żadnym wariancie. Ograniczeniem są dysk, czas generacji i czas przemierzania (pieszo 4,3 bloku/s: 649 km = ok. 42 h marszu; elytrą ok. 30 bloków/s: ok. 6 h).

### 6.0.1. Dysk (pre-generacja)

Założenie (do zweryfikowania własnym testem): w pełni wygenerowany chunk wanilii 1.18+ (384 bloków wysokości, kompresja zlib w plikach .mca) zajmuje na dysku rzędu **10 KB** (obserwowane wartości 5-15 KB/chunk; źródło: doświadczenie społeczności, brak jednego autorytatywnego pomiaru - NIE POTWIERDZONO liczbą z dokumentacji). Wyższy świat zwiększa rozmiar tylko o sekcje niepuste (sekcje samego powietrza są zapisywane paletą 1-elementową, koszt ~0), więc przyjmuję 10 KB (dolna) i 20 KB (górna, dla wysokich światów z jaskiniami).

| Skala pozioma | Chunki | Dysk @10 KB | Dysk @20 KB |
|---|---|---|---|
| 1:1 | 1,22 x 10^9 | 12,2 TB | 24,4 TB |
| 1:2 | 3,05 x 10^8 | 3,1 TB | 6,1 TB |
| 1:4 | 7,63 x 10^7 | 763 GB | 1,5 TB |
| 1:10 | 1,22 x 10^7 | 122 GB | 244 GB |
| 1:20 | 3,05 x 10^6 | 31 GB | 61 GB |
| 1:50 | 4,89 x 10^5 | 4,9 GB | 9,8 GB |

Bez pre-generacji dysk rośnie tylko tam, gdzie gracz był - świat 1:1 jest więc "technicznie możliwy" do grania lokalnie (generacja w locie), ale nie do pre-generacji ani dystrybucji jako gotowy zapis.

### 6.0.2. Czas generacji

Benchmarki: Chunky (pop4959; Fabric/Paper; modrinth.com/mod/chunky-pregenerator; FAQ na github.com/pop4959/Chunky/wiki/FAQ) raportuje "chunks per second"; Nemez (nemez.net, "Minecraft CPU Benchmarks: Chunk Generation Speed", 2024) mierzył przepustowość Chunky na PaperMC w promieniu 500 bloków dla różnych CPU. **Konkretne liczby z tych stron nie zostały odczytane w tej turze** (snippet wyszukiwania ich nie zawierał). Z doświadczenia społeczności wanilia 1.18+ na współczesnym 8-16-rdzeniowym CPU generuje rzędu **100-400 chunków/s** (Chunky domyślnie używa wielu wątków). Przyjmuję 200 chunków/s jako założenie robocze; generator z heightmapy może być szybszy (brak kosztownego szumu 3D na całą kolumnę), a świat 7x wyższy - wolniejszy.

| Skala | Chunki | Czas @200 c/s | Czas @50 c/s (wolny generator/wysoki świat) |
|---|---|---|---|
| 1:1 | 1,22 x 10^9 | 71 dni | 283 dni |
| 1:2 | 3,05 x 10^8 | 17,7 dnia | 71 dni |
| 1:4 | 7,63 x 10^7 | 4,4 dnia | 17,7 dnia |
| 1:10 | 1,22 x 10^7 | 17 h | 2,8 dnia |
| 1:20 | 3,05 x 10^6 | 4,2 h | 17 h |
| 1:50 | 4,89 x 10^5 | 41 min | 2,7 h |

### 6.1. Warianty skali poziomej i pionowej

Twarde ograniczenie pionowe: maks. Y budowania = 2031, min. Y = -2032, rozpiętość <= 4064 (sekcja 1). **Skala pionowa 1:1 (Rysy = 2499 bloków nad morzem) NIE mieści się z poziomem morza na Y=63** (2499 + 63 = 2562 > 2031) - trzeba obniżyć poziom morza do ok. Y = -520...-600. Współczynnik przewyższenia = skala pozioma / skala pionowa (np. 1:4 poziomo i 1:2 pionowo = 2x; w kartografii modele reliefu stosują 2-5x, bo w skali 1:1 niziny są wizualnie płaskie: 100 m różnicy na 10 km to 1 blok schodka na 100 bloków).

| Wariant (poziomo / pionowo) | Przewyższenie | Świat [bloki] | `min_y` / `height` (propozycja) | Poziom morza Y | Rysy [Y] | Sekcje/chunk | Ocena |
|---|---|---|---|---|---|---|---|
| 1:1 / 1:1 | 1x | 649k x 689k | -640 / 2672 (max Y 2031) | -520 | 1979 | 167 | "Prawdziwa Polska"; 12+ TB pre-gen; niziny płaskie jak stół; przemierzanie nierealne; góry imponujące; tylko generacja w locie |
| 1:2 / 1:1 | 2x | 324k x 344k | -640 / 2672 | -520 | 1979 | 167 | nadal 3 TB; góry 2x strome (Tatry: średnie stoki 30 st. -> ok. 49 st., wciąż "chodzalne" ze schodkami) |
| 1:4 / 1:2 | 2x | 162k x 172k | -64 / 1408 (max Y 1343) | 63 | 1313 | 88 | rozsądny kompromis "duży świat": 0,8-1,5 TB pre-gen, 4-18 dni; wydzielenie 5 ha = 56 x 56 bloków; Tatry 1250 bloków wysokości |
| 1:4 / 1:1 | 4x | 162k x 172k | -640 / 2672 | -520 | 1979 | 167 | góry karykaturalnie strome (30 st. -> 67 st.), klify wszędzie; NIE polecane |
| 1:10 / 1:4 | 2,5x | 65k x 69k | -64 / 768 (max Y 703) | 63 | 688 | 48 | **wariant bazowy do rozważenia**: 122-244 GB, 17 h-3 dni pre-gen; wysokość jak Tectonic "increased height" (Y 640); wydzielenie 5 ha = 22 x 22 bloki (nadal czytelne); rzeka 20 m = 2 bloki (za wąska - wymaga minimalnej szerokości); chunk = 160 m |
| 1:20 / 1:8 | 2,5x | 32k x 34k | -64 / 448 (max Y 383) | 63 | 375 | 28 | "Polska na weekend": 30-60 GB, 4-17 h; prawie wanilijna wysokość; drobne lasy śródpolne (<1 ha) znikają; rzeki tylko duże |
| 1:50 / 1:20 | 2,5x | 13k x 14k | -64 / 384 (wanilia) | 63 | 188 | 24 | mapa poglądowa; lasy jako plamy; nie oddaje "wszystkich krajobrazów" |

Uwagi projektowe do skali:
- **Drzewa i zwierzęta nie skalują się** wraz z terenem (świerk 40 m to zawsze ok. 40 bloków wysokości, a nie 4). Przy 1:10 poziomo drzewa są względem terenu 10x "za duże", więc las 100 x 100 m (10 x 10 bloków) pomieści 2-3 drzewa zamiast kilkudziesięciu. Efekt: gęstość drzewostanu trzeba dobierać "na oko" tak, by las wyglądał jak las, a nie odtwarzać liczby drzew na hektar. Piętra lasu (podszyt, runo) i tak są w skali bloków.
- Szerokości rzek/dróg/wydzieleń wymagają minimalnej szerokości w blokach (np. rzeka >= 3 bloki, żeby była rozpoznawalna), niezależnie od skali - typowa praktyka generatorów obrazkowych (WorldPainter).
- Skala nieliniowa (np. 1:4 w Karpatach/Sudetach, 1:10 na nizinach) jest możliwa technicznie (reprojekcja siatki), ale zniekształca geografię i utrudnia użycie danych wektorowych - odradzane; lepiej jedna skala pozioma + jedno przewyższenie.
- Ochrona przed "schodkowaniem" nizin: przy skali pionowej 1:4 różnica 4 m = 1 blok; na Żuławach (0-10 m n.p.m.) cały krajobraz to 2-3 poziomy bloków. Rozwiązanie: dodatek proceduralnego mikroreliefu (+/- 1-2 bloki szumem o małej amplitudzie, gaszony na wodach i bagnach) - dokładnie to, co robi wanilia w `base_3d_noise`.
- Wszystkie liczby dyskowe i czasowe są szacunkami z jawnymi założeniami; przed decyzją trzeba wykonać test: wygenerować np. 10 000 chunków prototypowym generatorem przy wysokości 768 i 1408 i zmierzyć KB/chunk oraz chunki/s.

## 7. Architektura: własny ChunkGenerator vs density functions vs hybryda

### 7.1. Własny ChunkGenerator w Javie (Fabric)

Źródła: wiki.fabricmc.net/tutorial:chunkgenerator ("Custom Chunk Generators"), javadoc yarn (maven.fabricmc.net/docs/yarn-1.21.x, klasy `ChunkGenerator`, `NoiseChunkGenerator`, `DebugChunkGenerator`, `FlatChunkGenerator`).

- Fabric Wiki: ChunkGeneratory "odpowiadają za kształtowanie, dodawanie bloków powierzchniowych właściwych biomom i rzeźbienie chunków oraz za zapełnianie ich feature'ami i encjami"; własna klasa dziedziczy po `ChunkGenerator`, trzeba nadpisać metody (wiele można skopiować z `NoiseChunkGenerator`) i `getCodec()` zwracające własny `CODEC`.
- Kluczowe metody (nazwy yarn 1.21.x; dokładne sygnatury do sprawdzenia w javadoc): `populateNoise(...)` - "generuje bazowy kształt chunku z podstawowych stanów bloków" (zwraca `CompletableFuture<Chunk>`); `buildSurface(...)` - bloki powierzchni po szumie; `carve(...)` - jaskinie; `populateEntities(...)`; `getHeight(x, z, Heightmap.Type, HeightLimitView, NoiseConfig)` i `getColumnSample(...)` - używane przez struktury i spawn; `getWorldHeight()`, `getMinimumY()`, `getSeaLevel()`; `appendDebugHudText(...)`.
- Rejestracja: `Registry.register(Registries.CHUNK_GENERATOR, Identifier.of("modid", "polska"), PolskaChunkGenerator.CODEC)` w inicjalizatorze moda (wzorzec z Fabric Wiki; w 1.19.3+ rejestr nazywa się `Registries.CHUNK_GENERATOR`). Codec (`RecordCodecBuilder`) zwykle koduje `BiomeSource` (`BiomeSource.CODEC.fieldOf("biome_source")`) i własne ustawienia (np. `RegistryEntry<ChunkGeneratorSettings>` lub własny rekord: ścieżka danych, skala, przewyższenie, poziom morza).
- Użycie: plik `data/<modid>/dimension/...` lub `worldgen/world_preset/polska.json` z `"generator": {"type": "modid:polska", "biome_source": {...}, "settings": {...}}` oraz `dimension_type` z własnym `min_y`/`height` (sekcja 1). Tag `#minecraft:normal` dla `world_preset` dodaje preset do menu tworzenia świata.
- Własny `BiomeSource`: rejestr `Registries.BIOME_SOURCE` + codec; metoda `getBiome(biomeX, biomeY, biomeZ, MultiNoiseSampler)` działa w siatce **4 x 4 x 4 bloków** (biomy są od 1.18 zapisywane per 4x4x4 w sekcji chunku), więc mapa biomów ma naturalną rozdzielczość 4 bloków (przy 1:10 = 40 m).
- Wątki: serwer generuje chunki równolegle na puli wątków roboczych ("Worker-Main-N"); generator i cache rastrów muszą być thread-safe (niezmienne kafle + `ConcurrentHashMap`/Caffeine LRU; wczytywanie kafla synchronizowane per klucz, by nie wczytywać dwa razy).

### 7.2. Czysto proceduralny świat "w stylu Polski" (density functions w JSON)

- Wszystko w data packu: własne `noise_settings/polska.json` (noise_router z własnymi spline'ami `offset`/`factor`/`jaggedness`), własne `density_function/*.json`, własne `surface_rule`, własna lista `multi_noise`. Zero Javy dla terenu (Tectonic, Terralith i WWOO tak działają). Świat nieskończony, kompatybilny z innymi modami przez Biolith/TerraBlender.
- Da się odwzorować **typy** krajobrazów Polski (pas pobrzeży z mierzejami i wydmami, pojezierza z rynnami i morenami, niziny z pradolinami, wyżyny, kotliny podkarpackie, Karpaty fliszowe, Tatry, Sudety) jako nisze w przestrzeni (continentalness, erosion, PV, temperature, humidity), ale **nie ich układ geograficzny** (Bałtyk na północy, góry na południu) - szum nie wie, gdzie jest północ. Zadanie użytkownika ("wszystkie 16 województw, rzeczywiste wymiary") tego nie spełnia.
- Zaleta: brak wielogigabajtowych danych; wada: "Polska-podobna", nie Polska.

### 7.3. Hybryda A: makroregiony z mapy + detal proceduralny (rekomendowana jako minimum)

- Niewielki raster "makro" (np. 1 piksel = 1 km lub 500 m; Polska = 649 x 689 px lub 1298 x 1378 px, kilka MB) z warstwami: wysokość uśredniona (z NMT 5 m lub Copernicus GLO-30 zagregowanego), region fizycznogeograficzny (Kondracki/Solon 2018 - do sprawdzenia dostępności w wektorach), udział lasu i typ lasu (CORINE/BDOT10k), odległość od morza, sieć głównych rzek i jezior (BDOT10k SW/PTWP zrasteryzowane).
- Własny typ density function `polska:raster` zarejestrowany w `Registries.DENSITY_FUNCTION_TYPE` (kodek z polem `layer`, `scale`, interpolacja bikubiczna) - dzięki temu wszystko dalej jest **zwykłym JSON-em wanilii**: `offset` = `polska:raster(height)`, `continents` = `polska:raster(dist_sea)`, `erosion` = funkcja nachylenia z rastra, `ridges` = wanilijny szum (dla drobnej rzeźby), `temperature`/`vegetation` = raster klimatu + szum. `final_density` = wanilijne `sloped_cheese` z naszym `offset` + jaskinie wanilii. Multi_noise wybiera biomy z naszej listy po tych parametrach.
- Zalety: świat ma prawdziwy układ (Bałtyk, pojezierza, góry na południu), rozmiar dowolnej skali, dane malutkie, cała maszyneria wanilii (akwifery, żyły rud, carvery, struktury, `surface_rules`, `placed_feature`) działa bez zmian; można używać narzędzi/edytorów DF; kompatybilność z Tectonic-owymi technikami.
- Wady: detale (konkretne wzgórze, konkretny las) są proceduralne; to "Polska z lotu satelity", nie z lotu drona.
- Ryzyko: rejestr `DENSITY_FUNCTION_TYPE` jest w wanilii rejestrem wbudowanym; rejestracja własnego typu przed wczytaniem data packów to powszechna praktyka modów worldgen (np. Lithostitched) - DO POTWIERDZENIA na kodzie źródłowym takiego moda.

### 7.4. Hybryda B: pełne rastry (NMT + pokrycie + BDL) + detal proceduralny (docelowa)

- Jak 7.3, ale rastry w rozdzielczości bloku (dla 1:10 poziomo: siatka 10 m -> 6,5k x 6,9k... uwaga: to 65 000 x 69 000 próbek = 4,5 x 10^9 wartości; int16 = **9 GB** bez kompresji; dla 1:4 - 56 GB; dla 1:1 - 625 GB). Wniosek: rastry pełnej rozdzielczości muszą być **kaflowane, kompresowane (deflate/LERC/PNG16) i ładowane leniwie z LRU**, a dla skal <= 1:4 - dostarczane jako osobne pobranie lub strumieniowane online (jak Tellus/Terra++), bo nie zmieszczą się w JAR-ze na Modrinth. Przy 1:10 wystarczy siatka źródłowa 10 m (a nawet 20-30 m + interpolacja bikubiczna) - ok. 2-4 GB po kompresji; przy 1:20 - siatka 20-30 m: <1 GB.
- Warstwy: (1) wysokość (int16, m lub dm), (2) klasa pokrycia (uint8: BDOT10k PT + CORINE fallback), (3) typ lasu/TSL/gatunek panujący/wiek (uint8 x 3 z BDL - tylko LP; poza LP: rodzaj lasu z BDOT10k), (4) woda: rzeki/jeziora/bagna (uint8 maska + szerokość), (5) opcjonalnie geologia/gleby (uint8).
- Kafle: np. 512 x 512 próbek x 5 warstw ~ 1,5 MB nieskompresowane; LRU na 64-256 kafli (100-400 MB RAM); wczytywanie przez `FileChannel.map` (mmap) lub z JAR-a zasobów; format: własny prosty (nagłówek + deflate) albo GeoTIFF przez bibliotekę (imageio-ext/ GeoTools są ciężkie - lepiej własna konwersja offline do prostego formatu).
- Pipeline offline (poza modem): GDAL (`gdalwarp` do EPSG:2180 i siatki docelowej, `gdal_translate` do kafli), rasteryzacja BDOT10k/BDL (`gdal_rasterize`), obliczenie pól pomocniczych (odległość od morza, nachylenie, wilgotność topograficzna TWI). Mod tylko czyta gotowe kafle.
- Odwzorowanie: PL-1992 (EPSG:2180) jest już płaskim układem metrowym dla całej Polski (zniekształcenia < 0,1 %), więc **blok = piksel siatki EPSG:2180** bez żadnej projekcji Dymaxion; oś X bloków = wschód, oś Z = -północ (Minecraft Z rośnie na południe). To ogromna przewaga nad Terra++ (który musi rzutować kulę).
- Detal proceduralny: mikrorelief (+/- 1-2 bloki), rozmieszczenie drzew/podszytu/runa wg BDL (gatunek, wiek -> wysokość i zwarcie), martwe drewno, kamienie, jaskinie z wanilii tylko w skałach krasowych/skalnych regionach (maska z geologii).

### 7.5. Porównanie

| Kryterium | 7.1 własny generator "od zera" | 7.2 JSON proceduralny | 7.3 hybryda makro | 7.4 hybryda pełna |
|---|---|---|---|---|
| Wierność "16 województw, rzeczywiste wymiary" | pełna | brak układu geograficznego | układ + typy krajobrazów | pełna (ograniczona skalą) |
| Dane do dystrybucji | GB | 0 | MB | GB (osobne pobranie) |
| Kompatybilność z modami worldgen | niska (własny generator) | wysoka | wysoka (wanilijny NoiseChunkGenerator) | średnia |
| Nakład Javy | duży | zero | mały (1 typ DF + ew. BiomeSource) | średni (DF + BiomeSource + kafle + pipeline) |
| Ryzyko wydajności | zależy od implementacji | jak wanilia | jak wanilia | cache kafli; generacja szybka |
| Rekomendacja | nie (zbyt dużo do napisania bez korzyści) | tylko jako fallback | **etap 1** | **etap 2** |

## 8. Nowe elementy wanilii istotne dla lasu (1.21.x i nowsze)

### 8.1. Java Edition 1.21.5 "Spring to Life" (wydana 25.03.2025; minecraft.wiki "Java Edition 1.21.5")

| Element | Gdzie generuje się w wanilii | Uwagi |
|---|---|---|
| Leaf Litter (ściółka liściowa) | forest, dark forest, wooded badlands | działa jak pink petals (1-4 "płatki" na bloku, kierunkowe); brązowy kolor - idealny wzorzec dla naszej ściółki bukowej/dębowej |
| Wildflowers (kwiaty polne) | birch forest, old growth birch forest, meadow | wielosztukowy blok jak petals |
| Bush (krzak) | plains, windswept hills/gravelly hills/forest, river, forest, birch forest | zielony krzak - wzorzec dla podszytu |
| Firefly Bush (krzak świetlików) | swamp, nad rzekami | cząsteczki świetlików przy świetle <= 13 |
| Short Dry Grass, Tall Dry Grass | desert, badlands | sucha trawa - wzorzec dla muraw kserotermicznych/wydm |
| Cactus Flower | desert, badlands | - |
| Fallen trees (powalone drzewa) | tam, gdzie rosną stojące odpowiedniki: oak, spruce, birch, jungle | czasem z grzybami lub pnączami; wyłączone w meadow, bamboo jungle, river, grove i większości flower forest (poza wariantami brzozowymi) |
| Warianty zwierząt gospodarskich | świnie, krowy, kury: cold/warm/temperate wg biomu spawnu | potomstwo dziedziczy losowo; kury "cold" znoszą niebieskie, "warm" brązowe jaja; owce - rozkład kolorów wg temperatury biomu |
| Pale Garden | zajmuje więcej miejsca kosztem dark forest | - |
| Wilki | 6 wariantów dźwięków | - |

Wcześniej: **1.21.2/1.21.3 "The Garden Awakens"** (grudzień 2024) dodało Pale Garden (pale oak, pale moss, pale hanging moss, creaking) - NIE ZWERYFIKOWANO w tej turze daty; **1.20 "Trails & Tales"** (czerwiec 2023) dodało cherry grove i drewno wiśniowe.

### 8.2. Fallen trees - implementacja (feature JSON)

Źródło: minecraft.wiki "Fallen tree" (pobrane 2026-09-21). Dodane w snapshotcie **25w09a** (1.21.5).

- Typ configured feature: **`minecraft:fallen_tree`** z polami: `trunk_provider` (block state provider kłody), `log_length` (int provider, **0-16** włącznie), `log_decorators` (lista dekoratorów kłody leżącej), `stump_decorators` (lista dekoratorów pniaka). Dekoratory to te same `tree_decorator` co dla drzew (np. `minecraft:trunk_vine`, `minecraft:attached_to_logs` z grzybami) - konkretne typy dekoratorów użyte przez wanilię NIE ODCZYTANE.
- Budowa: "jeden pionowy pień (pniak) i kłoda z rzędu bloków kłody leżąca na boku".
- Instancje configured feature: `fallen_oak_tree` (kłoda 4-7), `fallen_birch_tree` (5-8), `fallen_super_birch_tree` (5-15), `fallen_spruce_tree` (6-10), `fallen_jungle_tree` (4-11), `fallen_poplar_tree` (4-7; 26.3).
- Pniak ma 75 % szans na pnącza na bokach (poza świerkiem i brzozą); na niektórych wariantach grzyby.
- Placed features (rarity/count per biom) - wiki nie podaje; DO SPRAWDZENIA w `data/minecraft/worldgen/placed_feature/fallen_*_tree.json` w JAR-ze gry.
- Znaczenie: mamy gotowy, data-packowy typ feature dla **martwego drewna leżącego** - wystarczy skonfigurować własne `trunk_provider` (np. kłoda buka/sosny/dębu naszego moda, w tym warianty "spróchniałe") i dekoratory (huby = `shelf mushroom` z 26.3, mchy). Do uzupełnienia własnym feature'em: wykroty (korzenie), złomy (ułamane pnie stojące), stosy gałęzi.

### 8.3. Java Edition 26.3 "Wilderness Bound" (data wydania wg wiki: 15.09.2026)

Źródło: minecraft.wiki "Java Edition 26.3" (pobrane 2026-09-21). Zawartość istotna dla lasu:
- Nowy biom **Dappled Forest** ("jesienny las" przy zimnych biomach): **topole (poplar)** w wariantach liści czerwonych/pomarańczowych/żółtych, czerwone krzewy (red shrub, "roślina krzakowata w małych łatach"), ściółka (leaf litter), pieczarki brązowe, **huby (shelf mushrooms, małe i duże; "sprężyste" przy upadku)**, rzadko świerki; powalone topole (`fallen_poplar_tree`); sadzonki topoli.
- Pełny zestaw drewna topolowego (kłody, okorowane, deski, schody, płyty, tabliczki, drzwi, płoty, furtki, klapy, półki).
- Struktury: opuszczone obozowiska (w 16 biomach); słomiane łóżka (sen bez zmiany spawnu); schody/płyty z wełny i betonu.
- Moby w dappled forest: owce, kury, krowy, świnie, króliki, lisy.
- Wniosek: wanilia sama wprowadza elementy polskiego lasu (topola, huby na kłodach, jesienne barwy, ściółka). Mod powinien **budować na tych blokach** (huby, leaf litter, bush, fallen_tree, red shrub jako wzorzec dla np. trzmieliny/kaliny), a nie duplikować ich.
- UWAGA WERSJI: 26.3 przebudowuje density functions (sekcja 2.1: usunięte `cache_2d`, `flat_cache`, `shifted_noise`, `weird_scaled_sampler`, `y_clamped_gradient`; `size_horizontal`/`size_vertical` przeniesione do `interpolated`; `surface_rule` -> `material_rule`). Data packi worldgen z 1.21.x wymagają migracji na 26.3.

## Implikacje projektowe dla moda

1. **Zdecydować wersję docelową przed napisaniem pierwszego JSON-a worldgen.** 1.21.x (stary zestaw DF: `shifted_noise`, `y_clamped_gradient`, `flat_cache`, `size_horizontal/vertical` w noise_settings) i 26.3+ (nowy zestaw: `cache`, `gradient`, `lerp`, `interpolated` z `cell_size_*`, `material_rule`) nie są zgodne. Tectonic wspiera 1.21.1 i 26.1; Terralith 1.18.2-1.21.x. Rekomendacja: celować w **26.3+** (nowe bloki leśne: topola, huby, dappled forest; nowa arytmetyka DF ułatwia własne funkcje), utrzymując abstrakcję w Javie tam, gdzie JSON się różni.
2. **Wysokość świata**: przyjąć `dimension_type` własnego presetu z `min_y`/`height` wg wariantu skali; dla wariantu bazowego 1:10/1:4 - `min_y = -64`, `height = 768` (max Y 703; jak Tectonic "increased height" Y 640). Wariant 1:4/1:2 - `height = 1408`. Pionowe 1:1 tylko z poziomem morza ok. Y = -520 (`min_y = -640`, `height = 2672`).
3. **Jedna skala pozioma + jedno przewyższenie 2-2,5x** (np. 1:10 poziomo, 1:4 pionowo). Bez przewyższenia niziny (75 % Polski poniżej 200 m n.p.m.) są płaskie jak stół; z przewyższeniem > 3x góry stają się klifami nie do przejścia.
4. **Minimalne szerokości obiektów w blokach** niezależnie od skali: rzeka >= 3 bloki, droga leśna >= 2, wydzielenie leśne >= 8 x 8 bloków; drobniejsze obiekty z danych scalać lub pomijać (jak MMU w kartografii).
5. **Hybryda "raster -> density function"** zamiast własnego ChunkGeneratora od zera: zarejestrować typ DF `polska:raster` (`Registries.DENSITY_FUNCTION_TYPE`) i wpiąć go w `noise_router` (offset/continents/erosion/temperature/vegetation) własnego `noise_settings/polska.json`; resztę (jaskinie, akwifery, żyły rud, `surface_rules`, feature'y) zostawić wanilii. Zysk: zgodność z narzędziami i modami, mniej Javy.
6. **Własny `BiomeSource`** (`Registries.BIOME_SOURCE`) czytający mapę siedlisk (BDL TSL + BDOT10k rodzaj lasu + CORINE fallback) zamiast multi_noise tam, gdzie mamy dane; multi_noise z parametrami z rastra tylko dla wariantu proceduralnego/makro. Biomy są w siatce 4 x 4 x 4 bloki - mapa biomów w rozdzielczości 4 bloków wystarczy.
7. **Własny `world_preset`** (tag `#minecraft:normal`) zamiast nadpisywania `minecraft:overworld` - unika konfliktów z Terralith/Tectonic/BOP i pozwala graczowi wybrać "Polska" w menu; kompatybilność z modami biomowymi przez Biolith (Fabric-first, data-packowe umieszczanie biomów) jako opcja dla świata proceduralnego.
8. **Dane w kaflach z LRU**: rastry (wysokość int16, pokrycie/siedlisko/gatunek/wiek/woda uint8) w kaflach 512 x 512, deflate, wczytywane leniwie (mmap lub strumień), cache 64-256 kafli; generacja jest wielowątkowa - struktury niezmienne + `ConcurrentHashMap`/Caffeine. Rastry pełnej rozdzielczości dla skal <= 1:4 nie zmieszczą się w JAR-ze - osobne pobranie (Tellus/Terra++ strumieniują online).
9. **Pipeline offline w GDAL** (poza modem): reprojekcja do EPSG:2180, resampling do siatki docelowej (10 m dla 1:10), rasteryzacja BDOT10k PT/SW i wydzieleń BDL, pola pochodne (nachylenie, ekspozycja, TWI, odległość od morza), pakowanie w kafle. Mod nigdy nie czyta GeoTIFF/SHP bezpośrednio (unika ciężkich bibliotek GIS w Javie).
10. **NMT GUGiK 5 m jako źródło wysokości** (1 m to 1,25 TB i za dużo detalu); pobieranie skryptem przez WFS skorowidzy + WCS (limit 7 km2/zapytanie -> ok. 45 000 zapytań dla kraju; lepiej: arkusze .asc z modułu "Pobierz dane" lub porozumienie o pobraniu masowym). Globalne DEM (GLO-30 = DSM zawyżony o korony drzew) tylko do prototypu; FABDEM ma licencję NC.
11. **BDL jako serce "przyrodniczej zgodności"**: wydzielenie (gatunek panujący, wiek, TSL, zwarcie) -> zestaw drzew (gatunek, wysokość z wieku i bonitacji), podszyt, runo, mchy, martwe drewno. Poza LP (23 % lasów) - fallback z BDOT10k (liściasty/iglasty/mieszany) i reguł siedliskowych z NMT (wilgotność topograficzna, ekspozycja). Zweryfikować licencję BDL przed redystrybucją.
12. **surface_rules jako narzędzie do ściółek i gleb**: własne bloki (igliwie, ściółka liściasta, torf, próchnica, piasek wydmowy, less, glina, rędzina) wybierane warunkami `biome`, `noise_threshold`, `steep`, `stone_depth`, `y_above`, `water` + własny warunek z rastra (własny typ `SurfaceRules.ConditionSource` przez rejestr `MATERIAL_CONDITION` - DO POTWIERDZENIA możliwości).
13. **Mikrorelief proceduralny** (+/- 1-2 bloki, `base_3d_noise`-like) nakładany na raster, wygaszany na wodach, bagnach i łąkach kośnych; w lasach dodatkowo wykroty/dołki jako feature.
14. **Wykorzystać wanilijne klocki 1.21.5/26.3**: `minecraft:fallen_tree` (własne `trunk_provider`, dekoratory z hubami), `leaf_litter`, `bush`, `wildflowers`, `firefly_bush` (torfowiska, łęgi), `short/tall_dry_grass` (murawy napiaskowe, wydmy), `shelf mushroom`, `red shrub` jako wzorce; własne bloki dodawać tam, gdzie wanilia nie ma odpowiednika (borówka, paprocie, mchy torfowce, chrust).
15. **Woda**: rzeki i jeziora z BDOT10k (SW/PTWP) jako maska + poziom lustra z NMT; `sea_level` globalny tylko dla Bałtyku; jeziora powyżej poziomu morza wymagają lokalnego poziomu wody (własny DF/`fluid_level_floodedness`/`fluid_level_spread` z rastra) - to punkt, w którym wanilijne akwifery trzeba przejąć.
16. **Struktury i spawn**: `getHeight`/`getColumnSample` generatora muszą zwracać prawdziwą heightmapę z rastra, żeby wioski/moby/spawny zwierząt stały na ziemi; `spawn_target` presetu ustawić w rozsądnym miejscu (np. Puszcza Białowieska lub centrum).
17. **Testy wydajności przed decyzją o skali**: wygenerować 10 000 chunków przy `height` 768 i 1408, zmierzyć KB/chunk i chunki/s (Chunky na Fabric), a następnie przeliczyć tabele z sekcji 6 na twarde liczby.
18. **Dystrybucja**: świat 1:10 to 122-244 GB po pre-generacji - nie dystrybuować zapisów; dystrybuować mod + pakiet rastrów (2-4 GB) i generować w locie; pre-generację zostawić serwerom.
19. **Etapowanie**: etap 1 - hybryda makro (raster 1 km, kilka MB, świat "Polska z satelity") z pełnym systemem biomów/lasów; etap 2 - pełne rastry NMT 5 m + BDOT10k + BDL; etap 3 - dane hydrograficzne/geologiczne (MPHP, SMGP) dla wód i skał.
20. **Pytać użytkownika** o: wersję MC, skalę, akceptowalny rozmiar pobrania, czy świat ma być ograniczony do Polski (co za granicą: proceduralne "Europa-podobne" tło czy ocean/bariera?).

## Niepewności / do potwierdzenia

1. Czy Java Edition 26.3 faktycznie wyszło 15.09.2026 (wiki podaje tę datę; wcześniejsze źródła mówiły o "release candidate") - sprawdzić na minecraft.net/minecraft.wiki.
2. Dokładne kompozycje wanilijnych plików `overworld/sloped_cheese`, `depth`, `final_density` - zapisane z pamięci (dekompilacja 1.18-1.21), nie z pobranego JSON-a; do potwierdzenia w JAR-ze wersji docelowej.
3. Wzór spadku temperatury z wysokością w wanilii - NIE ZBADANO.
4. Tabele "middle/plateau/shattered/beach biomes" (temperature x humidity) - nie przepisane; są na minecraft.wiki "Biome" -> Generation.
5. Czy Fabric API nadal nie ma API do dodawania biomów Overworldu (stan wg wiedzy autora) oraz status wsparcia TerraBlender/Biolith/Lithostitched dla 26.x.
6. Tectonic "increased height": tylko "do y640" potwierdzone; `min_y`, wymóg nowego świata, koszt wydajności - nieudokumentowane na wiki moda.
7. Terralith: które zakresy parametrów zajmuje i czy wspiera 26.x (ostatnia aktualizacja lipiec 2026); licencja "Stardust Labs License" - treść nieprzeczytana.
8. Terra (PolyhedralDev): czy istnieje sampler `IMAGE`/addon obrazkowy i jakie wersje MC są wspierane (README nie mówi; sprawdzić terra.polydev.org i Modrinth).
9. Terra++: konkretne źródło kafli wysokościowych (AWS Terrain Tiles?) i projekcja (zmodyfikowany Dymaxion) - z wiedzy, nie z pobranej strony.
10. Tellus, NovoAtlas, Atlas, McOSM, realterrain: loadery (Fabric/NeoForge), wersje MC, licencje, formaty (16-bit PNG?) - z wyników wyszukiwania, nie ze stron projektów.
11. NMT GUGiK: dokładna data otwarcia danych (31.07.2020 wg nowelizacji PGiK; w zadaniu "od 2021"), dokładności (błąd średni), rozmiar danych, możliwość pobrania masowego (czy istnieje API/paczki wojewódzkie), czy WCS zwraca GeoTIFF.
12. CORINE: liczba klas w Polsce (31?), licencja GIOŚ, dostępność CLC 2024 / CLC+ Backbone 10 m.
13. BDOT10k: dokładne nazwy klas PT (PTLZ, PTRK, ...) i atrybutów (rodzaj lasu) wg specyfikacji 2021; rozmiar paczki krajowej GPKG.
14. BDL: licencja/warunki redystrybucji, dokładne nazwy pól opisu taksacyjnego, URL WMS/WFS, dostępność lasów prywatnych w pobieraniu, format GeoPackage.
15. Copernicus HRL Forest, ESA WorldCover, MPHP10, SMGP 1:50 000, mapa glebowo-rolnicza, ESDB - NIE ZBADANE w tej turze (tylko wiedza ogólna).
16. Liczby dysku (10-20 KB/chunk) i czasu generacji (100-400 chunków/s) - założenia ze społeczności, bez odczytanego benchmarku (Nemez 2024, Chunky FAQ - do przeczytania).
17. Skrajne wysokości Polski (Rysy 2499 m, Raczki Elbląskie -1,8 m) i udział nizin - wiedza ogólna, do potwierdzenia w raporcie geograficznym projektu.
18. Możliwość rejestracji własnych typów `DensityFunction` i `SurfaceRules.ConditionSource` w rejestrach wbudowanych na Fabric bez mixinów - praktyka modów (Lithostitched) do potwierdzenia na kodzie.
19. Dokładne sygnatury metod `ChunkGenerator` w yarn dla wersji docelowej (populateNoise/buildSurface/carve/getHeight) - z javadoc yarn 1.21.x wg wyników wyszukiwania; sprawdzić dla 26.x.
20. Limit rozmiaru pliku na Modrinth/CurseForge (dla pakietu rastrów) - NIE ZBADANO.

## Źródła

Minecraft / technika:
- https://minecraft.wiki/w/Dimension_type
- https://minecraft.wiki/w/Density_function
- https://minecraft.wiki/w/Noise_settings
- https://minecraft.wiki/w/Surface_rule
- https://minecraft.wiki/w/World_generation
- https://minecraft.wiki/w/Biome
- https://minecraft.wiki/w/Custom
- https://minecraft.wiki/w/Dimension_definition/parameter_point
- https://minecraft.wiki/w/Tutorial:Custom_world_generation
- https://minecraft.wiki/w/Java_Edition_1.21.5
- https://minecraft.wiki/w/Java_Edition_26.1
- https://minecraft.wiki/w/Java_Edition_26.1.1
- https://minecraft.wiki/w/Java_Edition_26.3
- https://minecraft.wiki/w/Fallen_tree
- https://www.minecraft.net/en-us/article/minecraft-new-version-numbering-system
- https://releasebot.io/updates/minecraft
- https://www.comicshoplocator.com/games/minecraft/
- https://wiki.fabricmc.net/tutorial:chunkgenerator
- https://maven.fabricmc.net/docs/yarn-1.21.5+build.1/net/minecraft/world/gen/chunk/ChunkGenerator.html
- https://maven.fabricmc.net/docs/yarn-1.21.4+build.4/net/minecraft/world/gen/chunk/NoiseChunkGenerator.html
- https://maven.fabricmc.net/docs/yarn-1.21+build.9/net/minecraft/world/gen/chunk/DebugChunkGenerator.html

Mody:
- https://modrinth.com/mod/tectonic
- https://github.com/Apollounknowndev/tectonic
- https://github.com/Apollounknowndev/tectonic/wiki/Config
- https://github.com/Apollounknowndev/lithostitched/releases
- https://modrinth.com/datapack/terralith
- https://github.com/Stardust-Labs-MC/Terralith
- https://stardustlabs.miraheze.org/wiki/Terralith
- https://modrinth.com/mod/wwoo
- https://modrinth.com/mod/wwoo/version/8RVcoS4A
- https://modrinth.com/datapack/william-wythers-overhauled-overworld-(datapack)
- https://www.curseforge.com/minecraft/mc-mods/william-wythers-overhauled-overworld
- https://github.com/PolyhedralDev/Terra
- https://modrinth.com/mod/terraplusplus
- https://github.com/BuildTheEarth/terraplusplus
- https://github.com/bitbyte2015/terraplusplus
- https://github.com/orangeadam3/terra121
- https://www.curseforge.com/minecraft/mc-mods/terra-1-to-1-minecraft-world-project
- https://en.wikipedia.org/wiki/Build_the_Earth
- https://modrinth.com/mod/tellus
- https://modrinth.com/mod/novoatlas
- https://modrinth.com/mod/atlas
- https://modrinth.com/mod/mcosm
- https://github.com/bobombolo/realterrain/
- https://modrinth.com/collection/PyAEVyNN
- https://www.minecraftmaps.com/tools/earth-map-generator
- https://github.com/TerraformersMC/Biolith
- https://github.com/TerraformersMC/Biolith/blob/main/README.md
- https://modrinth.com/mod/biolith
- https://modrinth.com/mod/chunky-pregenerator
- https://github.com/pop4959/Chunky/wiki/FAQ
- https://nemez.net/posts/20240414-minecraft-cpu-performance-testing/page-8/

Dane geoprzestrzenne:
- https://www.geoportal.gov.pl/pl/dane/numeryczny-model-terenu-nmt/
- https://www.geoportal.gov.pl/en/data/topographic-objects-database-bdot10k/
- https://www.geoportal.gov.pl/pl/aplikacje/portal-bdot10k/
- https://www.geoportal.gov.pl/aktualnosci/polaczone-klasy-obiektow-bdot10k-w-formacie-gpkg-dla-obszaru-kraju-dostepne-w-serwisie-www-geoportal-gov-pl/
- https://www.gov.pl/web/gugik/nowy-sposob-udostepniania-danych-bdot10k-w-geoportalu
- https://dane.gov.pl/pl/dataset/2030/resource/27186,bdot10k-usuga-pobierania-w-geoportalgovpl
- http://www.gugik.gov.pl/__data/assets/pdf_file/0013/23611/Zeszyt-cwiczen-dla-uzytkownikow-Bazy-Danych-Obiektow-Topograficznych-BDOT10k.pdf
- https://mapy.lodzkie.pl/mapa/do-pobrania-bdot10k/
- https://geoportal.podlaskie.eu/pobierz/dane-bdot10k-do-pobrania.html
- http://wgik.dolnyslask.pl/bdot10k
- https://clc.gios.gov.pl/index.php/clc-2018/o-projekcie
- https://www.bdl.lasy.gov.pl/portal/
- https://www.bdl.lasy.gov.pl/portal/wniosek
- https://www.bdl.lasy.gov.pl/portal/udostepnianie
- https://www9.bdl.lasy.gov.pl/portal/o-udostepnianiu?v=1
- https://www.bdl.lasy.gov.pl/portal/mapy
- https://buligl.pl/en/w/projekt-bdl
- https://www.lasy.gov.pl/pl/nasze-lasy/bank-danych-o-lasach
- https://dataspace.copernicus.eu/explore-data/data-collections/copernicus-contributing-missions/collections-description/COP-DEM
- https://documentation.dataspace.copernicus.eu/APIs/SentinelHub/Data/DEM.html
- https://docs.sentinel-hub.com/api/latest/static/files/data/dem/resources/license/License-COPDEM-30.pdf
- https://registry.opendata.aws/copernicus-dem/
- https://gee-community-catalog.org/projects/glo30/
- https://gee-community-catalog.org/projects/fabdem/
- https://un-spider.org/links-and-resources/data-sources/copernicus-dem-glo-30-glo-90
- https://www.tandfonline.com/doi/full/10.1080/17538947.2024.2308734
