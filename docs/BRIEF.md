# Brief projektu „Polish Forests” (dla agentów)

Stan: 2026-10-07 (po K8a). Krótki punkt wejścia: przeczytaj to zamiast całych planów i raportów, a szczegóły bierz z dokumentów z ostatniej sekcji.

## Czym jest projekt

Mod Fabric do Minecrafta 26.3 (Java 25, Loom, Gradle 9.7, JDK z `gradle.properties`): id `polishforests`, pakiet `pl.polishforests`. Tworzy proceduralny, nieskończony świat „Polska” (własny `ChunkGenerator`, model krajobrazu w czystej Javie) z polskimi typami krajobrazu, rzekami, wybrzeżem i Beskidami, a docelowo siedliska, lasy (drzewa o zmiennej grubości, runo, martwe drewno), faunę i pory roku (opcjonalnie Serene Seasons). Dwa typy świata: skala 1:1 (`poland`) i skala rozgrywki (`poland_gameplay`). Repozytorium: GitHub `SernikNaZimno/Polish-Forests-Mod`, gałąź `main`.

## Stan

- **Zrobione:** M0 (szkielet, kalendarz, most do SS), M1 (preset, generator, model krajobrazu v1, podgląd PNG, gametesty), skala rozgrywki i komendy `/polishforests`, rzeki, doliny i morze, faza 1 M2 (S0–S4: temperatura z wysokości, mixiny klimatu, pola `ColumnSample`, siatki `CoarseTerrainField`/`RegionalField`, pakiet `habitat` z klasyfikatorem, punkt kontrolny 1 PNG), zmiana nazwy na Polish Forests (M2-9), **poprawka geometrii terenu (M2-8) K0–K7**: wielkie masywy Beskidów (szczyty 1620–1725 m w obu skalach, kosodrzewina i hala), ciągłe pola i gładka geometria dolin (bez urwisk: D4, D4a, D4b), starorzecza jako półksiężyce, jeziora rynnowe przed doliną i z poziomem niezależnym od kolejności, niecki bez ścian, wybrzeże wydmowe z klifem na ok. 1/5 brzegu (D5, D5a) i zalew bez rowu (D2), okno mieszania regionów 5 × 5 bez szwów (A16). K7 zacommitowany (`49aca9c`). **Etap H (poprawki klasyfikatora siedlisk, 2026-10-07)** zacommitowany (`02cb497`): miękki poziom koryt `waters.softChannelLevel` (G3: bez wielobocznych płatów łęgów przy zbiegach), strefy cieku tylko przy prawdziwej wodzie (`BANK_H` 4 m), drganie progów żyzności i wariantu buczyny szumem 150 m·k (Z9), klasa C bez prostych ucięć, wydmy przez dna dolin w pasie wydm brzegu wydmowego (brzeg wydmowy 78–80% punktów brzegu), runda 1 recenzji (bez prostych cięć dna na wybrzeżu, drganie granicy DGW buczyny); podgląd siedlisk i CSV przeliczone (angielskie nazwy w `docs/m2`); opis w `docs/03-m2-biomy.md` §3.4 „Stan po poprawce geometrii”. Koszt `sample` po etapie H z oszczędnością w `RiverNetwork` (pamięć węzłów listy kandydatów bez pudełkowanych kluczy): GAMEPLAY cały obszar 1,18–1,19 × M1, Beskidy 1,16, REAL cały obszar 1,10–1,11, w budżecie D1 (1,20; `docs/m2/poprawka-geometrii.md`, „Etap H”). Test w grze po poprawce (gametest `views`, nowe miejsce `great_massif`, kamery brzegowe z morza): bez urwisk i ścian, zrzuty w `docs/m2/gra/`.
- **W toku: krok K8 (krótka poprawka po teście w grze, decyzja użytkownika 2026-10-07; `docs/m2/poprawka-geometrii.md`, „K8”).** **K8a zrobiony i zacommitowany:** jeziora rynnowe GAMEPLAY w wariancie d — koniec jeziora przy dolinie od gładkiej szczeliny z siatki szczelin konturu (`TunnelGaps`: obwiednie o nachyleniu 1, ograniczenie od doliny z boku i od skraju strefy, wypełnienie zamkniętych zagłębień odległości od brzegu), strażnik dna doliny (`RiverHit.floorEdgeGap`), połowa szerokości z osi jeziora, `TUNNEL_COS` 0,6. Ściany niecek: 0 (skan ±40 km, wszystkie okna `WALL_CLUSTERS`; wcześniej do 8 bloków na blok), suche zamknięte zagłębienia > 3 m przy nieckach 20 → 3 na siatce 10 m (zostają 3,3–3,7 m przy wale brzegu na zboczu doliny, limit testu 4 m), woda +30% wobec K7 (−57% wobec K4c, cel −37% nieosiągnięty), koszt D1 GAMEPLAY 1,17–1,20 × M1 (bez zapasu). Lista `src/test/golden-allow/K8a.txt` (narastająca do K8c). Dalej: K8b (wybrzeże), K8c (starorzecza), K8z (przegenerowanie pliku wzorcowego, zrzuty w grze). Do M5: R5 (ściany rzutu w REAL do 2,75 bloku na blok) i remisy D4a, wały moren W–E (D3), końce wydm i zalewów, grobla oczek, jeziora bezodpływowe u stóp wielkich masywów REAL (ściany niecek do 75 m na siatce 5 m i rzeki na wałach do 24 m nad gruntem; limity w `SurfaceContinuityTest` i `MassifSinkLakeContainmentTest`), proste załamania na kopule GAMEPLAY 1718 m i starorzecze ścięte przy dopływie. Cel `/polishforests find cliff` po D5 trafia też na wysoką wydmę niskiego brzegu (warunek `lowShore` do dodania). Plik wzorcowy przegenerujemy raz w K8z (`-PwriteGolden -PgoldenKeepCenters`, potem `diff`).
- **Potem:** faza 2 M2 (S5–S10: rejestracja 36 biomów i datagen, gleby i bloki w `fill`, dekoracja i strefy nadwodne, punkt kontrolny 2, tryb „dzisiejsza Polska”, struktury, budżety), potem **pionowy wycinek** (jeden kompletny las, np. grąd i bór świeży: drzewa, runo, martwe drewno, kilka zwierząt), dopiero potem poszerzanie na całą Polskę (M3+).

## Konwencje

- **Język:** kod, identyfikatory, komentarze, logi, komunikaty i commity po angielsku (pisownia amerykańska); to, co widzi gracz, po angielsku z tłumaczeniem `pl_pl`; dokumentacja w `docs/` i README po polsku. Dawne polskie nazwy: tabela w `docs/01-architektura.md` §14.
- **Sole szumów:** literałów w `derive("…")` (także polskich `habitat.*`) nigdy nie zmieniamy: inna sól to inny świat.
- **Enumy:** kolejność stałych jest zamrożona (np. `HabitatBiome`, id biomów zapisywane w chunkach); nowe stałe tylko na końcu.
- **Kopia M1:** `src/test/java/pl/polishforests/worldgen/landscape/m1/` to zamrożona kopia modelu M1 (złoty test, `SampleCostTest`): nie zmieniać.
- **Złoty test** (`GoldenTerrainTest`): dwa pliki wzorcowe, `golden_terrain_m1.txt` (kopia M1, nigdy nie zmieniany) i `golden_terrain_m2.txt` (obecny model, przegenerowany w K7 poprawki geometrii). Od K7 zwykły `test` porównywał bez listy dozwolonych zmian (w K8a–K8c znów domyślna lista `K8a.txt`…`K8c.txt`, do przegenerowania w K8z): każda zmiana terenu to błąd, dopóki świadomie nie przegenerujemy pliku (`-PwriteGolden -PgoldenKeepCenters -PgoldenFile=build/…`, potem `diff`: środki łat bez zmian poza łatami, które straciły cel; łaty kontrolne `*_interior` bez zmian) i nie opiszemy zmiany w docs. Dla zmiany w wielu krokach można znów użyć list `src/test/golden-allow/K*.txt` (domyślnie ostatnia według nazwy; `build.gradle` sortuje nazwy `K*.txt` leksykograficznie, więc nowa lista musi sortować się po poprzednich: `K10.txt` wypada przed `K9.txt`, dlatego numer o stałej szerokości, np. `K08.txt`, `K09.txt`, `K10.txt`; `-PgoldenStrict` porównuje bez listy); raport `-PgoldenReport` ma składnię listy.
- **Dwie skale:** `LandscapeScale.REALISTIC`/`GAMEPLAY` (poziomo: makroregion ok. 64 km / 1,4 km; mnożniki `meso`, `local` (k), `mountainSpacing`, `channel`) i `VerticalScale.REAL`/`GAMEPLAY` (metry → bloki; REAL 1:1 do ok. 900 m, potem ściśnięcie, Y −64…2031; GAMEPLAY bloki = 1,89·m^0,742, Y −64…767). Model liczy zawsze w metrach; progi w metrach modelu, odległości ×k albo szerokością koryta, minima stref w blokach. Każdą zmianę testujemy w obu skalach.
- **Determinizm i wątki:** wynik w punkcie to czysta funkcja (ziarno, współrzędne, ustawienia); generacja idzie na wielu wątkach. Pamięci podręczne (`DirectCache`, kafle siatek, `TileCache` sieci rzecznej) nie mogą zmieniać wyniku ani zależeć od kolejności zapytań (pilnują `TerrainDeterminismTest`, `habitatFieldsDoNotDependOnQueryOrder`). `String.format` z `Locale.ROOT` (polski system ma przecinki dziesiętne).
- **Testy wolne:** klasy > ok. 20 s mają `@Tag("slow")` (od K5 także `StandingWaterTest` i `StandingWaterContainmentTest`, od K7 `MassifSinkLakeContainmentTest`), pomiar kosztu `@Tag("cost")`. Nowa ciężka klasa testów dostaje tag. Gradle przydziela klasy procesom z góry, po kolei według plików klas (nie według obciążenia), więc klasę dłuższą niż ok. 2–3 min dzielimy (tak powstały `WaterContainmentTest` i `MountainStreamSourcesTest` z `RiverNetworkTest`).

## Struktura pakietów i kluczowe klasy (`src/main/java/pl/polishforests/`)

- `PolishForests` – punkt wejścia, kolejność rejestracji.
- `season/` – `SeasonProvider`, `FallbackCalendar` (własny kalendarz), `SubSeason`, `compat/SereneSeasonsProvider` (most do SS).
- `climate/` – `PolandClimate` (temperatura z wysokości), `BiomeClimate`, `ClimateBinding`; `mixin/` – `BiomeTemperatureMixin`, `BiomeFreezeMixin`, `BiomeClimateMixin`.
- `command/PolishForestsCommands` – `/polishforests find|elevation|highest|list|here`.
- `worldgen/chunk/` – `PolandChunkGenerator` (wypełnianie chunków), `PolandBiomeSource`, `PolandSettings` (opcje świata), `PolandScale` (skala + typ wymiaru), `VerticalScale` (metry → bloki), `PolandDimension` (rama pionowa).
- `worldgen/landscape/` (czysta Java, bez klas gry):
  - `LandscapeModel` – model krajobrazu L0–L3, `sample` (→ `ColumnSample`), `describe`, `landElevation`;
  - `ColumnSample` – wynik próbki: teren, wody, region (rekordy `Terrain`, `Waters`, `Region`); w `Waters` pola koryta najbliższego (`channel*`), koryta doliny dominującej (`floorChannel*`, F2) i miękki poziom koryt (`softChannelLevel`, etap H: od niego klasyfikator liczy wysokość nad wodą);
  - `RiverNetwork` – sieć rzeczna (3 rzędy cieków, doliny, cięcie omiatające); `MeanderField` – meandry Kinoshity;
  - `CoarseTerrainField` (sBar, nachylenie, ekspozycja), `RegionalField` (oceaniczność O, podgórskość P), `PeakField` (siatka szczytów, wielkie masywy), `DirectCache` (bezblokadowa pamięć kafli);
  - `LandscapeScale`, `LandscapeType`, `Landform`, `Substrate`, `WaterKind`, `Noise` (szumy z solami).
- `worldgen/habitat/` (czysta Java): `HabitatClassifier` (siedlisko kolumny jako `int`), `HabitatBiome` (36 biomów), `Zone`, `ForestSiteType`, `Association`, `Soil`, `Species`, `Habitat` (pakowanie), `WatersideZones`, `AltitudinalBelts` (piętra), `Coast`, `OpenWater`, `Moisture`, `Fertility`, `SpeciesRanges`, `ForestCover` (tryb D), `Calibration` (wszystkie progi).
- `src/client/java/.../client/` – ekran opcji świata (`screen/`), `ClientClimate`, datagen (`datagen/PolishForestsDataGenerator`).
- `src/gametest/` – testy w kliencie: `PolandWorldClientGameTest`, `PerformanceClientGameTest`, `UiAndCommandsClientGameTest`.
- `src/test/` – JUnit (model, siedliska, złoty test), podglądy PNG (`LandscapePreview`, `HabitatPreview`), kopia M1.

## Komendy

```bash
./gradlew build                      # kompilacja + 'test' (wszystko bez 'cost')
./gradlew fastTest                   # szybkie testy (bez 'slow' i 'cost'), 3 procesy, ok. 1 min
./gradlew test                       # pełny zestaw (bez 'cost'), 3 procesy równolegle
./gradlew costTest                   # SampleCostTest sam, w jednej JVM, zawsze od nowa (-PcostRuns=15)
./gradlew test --tests '*GoldenTerrainTest*' [-PgoldenReport=build/g.txt] [-PgoldenAllow=...] [-PgoldenStrict]
./gradlew test --tests '*GoldenTerrainTest*' -PwriteGolden [-PgoldenKeepCenters] [-PgoldenFile=...]
tools/dev/run-tests [--reuse] <zadanie> [argumenty]   # np. tools/dev/run-tests --reuse test
./gradlew landscapePreview [-PhabitatsOnly]           # PNG do build/preview
./gradlew runClientGameTest -Pgametest=all|ui|performance|views|stages|climate [-Psites=beach,cliff] [-Pprofile]
./gradlew runDatagen                 # datagen (od S5 wynik w src/main/generated, test: git diff --exit-code)
./gradlew runClient [-Poptimization] # gra deweloperska (z paczką optymalizacyjną)
```

- `-PtestForks=<n>` zmienia liczbę procesów `test`/`fastTest` (1 = szeregowo). Każdy proces ma 3 GB sterty. Testy używają równoległych strumieni, więc procesy dzielą rdzenie.
- `tools/dev/run-tests` liczy skrót całego drzewa roboczego (pliki śledzone i nieśledzone poza `.gitignore`, przez tymczasowy indeks), uruchamia zadanie Gradle i zapisuje log w `build/test-logs/<drzewo>-<zadanie>-<argumenty>.log` oraz wiersz w `build/test-logs/index` (czas, drzewo, zadanie, skrót argumentów, PASS/FAIL, sekundy, log, argumenty). Z `--reuse`, gdy ostatni wpis dla tego samego drzewa, zadania i argumentów to PASS, tylko wypisuje ścieżkę logu. Pełny zestaw uruchamiaj przez nie raz na krok, a recenzent z `--reuse`. Każda zmiana w drzewie, także tylko w `docs/`, daje nowy skrót. Przy zmianie samej dokumentacji Gradle zgłosi `test` jako UP-TO-DATE w kilka sekund, chyba że w międzyczasie biegło `test --tests …`: inny filtr unieważnia aktualność i pełny zestaw idzie od nowa.
- `costTest` mierzy czas zegarowy: uruchamiaj go, gdy nie działa nic ciężkiego (inne testy, gra, agent w innym worktree); rozstrzyga stosunek obecny/M1 z jednego uruchomienia (`docs/m2/pomiary-bazowe-m1.md`).
- Gametesty zapisują zrzuty w `build/run/clientGameTest/screenshots`.

## Zasady pracy agentów (decyzje użytkownika z 2026-10-05, `docs/00-decyzje-do-podjecia.md` F)

- **A1:** w trakcie kroku iteruj na `fastTest` i na wybranych klasach (`--tests`); pełny `test` raz na koniec kroku.
- **A2:** wynik pełnego zestawu dla niezmienionego drzewa bierz z `run-tests --reuse`, nie uruchamiaj ponownie.
- **A3:** krótkie przebiegi pracy; commit i push (`origin main`) po każdym zamkniętym kroku lub rundzie poprawek. Commit po angielsku, ostatnia linia `Co-Authored-By: …`. Nigdy force-push.
- **A3a (decyzja użytkownika z 2026-10-07): commit i push po każdej średniej i znaczącej zmianie lub etapie**, nie dopiero na końcu kroku: np. gdy prototyp trafił do projektu, po każdym zakończonym podzadaniu, po każdej rundzie poprawek, przed dłuższym eksperymentem. Commit pośredni: kompilacja, `fastTest` i klasy testów dotyczące zmiany (w tym `GoldenTerrainTest` z bieżącą listą dozwolonych zmian) przechodzą, tytuł z dopiskiem „(WIP)”. Ostatni commit kroku po pełnym zestawie (`tools/dev/run-tests test`, PASS), bez „(WIP)”. Bez plików tymczasowych; na końcu drzewo czyste i wypchnięte.
- **A4:** ten brief jako punkt wejścia; pełne plany czytaj tylko w potrzebnych sekcjach.
- **A5:** agenci równolegli w osobnych git worktree, tylko gdy nie zmieniają tych samych plików (każdy worktree ma własne `build/`).
- **B1:** recenzja według ryzyka: geometria terenu, mixiny, rejestracja biomów i datagen → 2 recenzentów i do 2 rund poprawek; dokumentacja, komendy, lang, tabele danych → 1 recenzent, 1 runda.
- **B2:** tańszy model do prac mechanicznych (commity, aktualizacja dokumentacji, inwentaryzacje, tłumaczenia, zwykłe uruchomienia testów).
- **B3:** problem blokujący, który przetrwał 2 rundy poprawek → stop i 2–3 warianty z kosztami do decyzji użytkownika.
- Recenzenci nadal oglądają pełne zestawy kadrów podglądu (B4 odrzucona).

## Gdzie szukać szczegółów

- `docs/00-decyzje-do-podjecia.md` – wszystkie decyzje (A–F) ze statusem.
- `docs/01-architektura.md` – architektura, rama pionowa, plan kamieni milowych (§8), dwie skale (§12), rzeki i morze (§13), zmiana nazwy (§14), elementy terenu po poprawce geometrii (§15).
- `docs/02-wydajnosc.md` – pomiary wydajności w grze, paczka `.mrpack`.
- `docs/03-m2-biomy.md` – plan M2: biomy (§2), pola i siatki (§3), strefy nadwodne (§4), piętra i wybrzeże (§5), klimat (§6), gleby (§7), dekoracja (§8), pliki (§11), testy i definicja ukończenia (§12), kolejność kroków (§15.3).
- `docs/m2/poprawka-geometrii.md` – kroki K0–K7, narzędzia kontrolne, wyniki i „Co zostaje” po każdym kroku; na końcu podsumowanie całej poprawki (decyzje, odstępstwa, pomiary przed i po).
- `docs/m2/pomiary-bazowe-m1.md` – koszt `sample` wobec M1, jak mierzyć budżet.
- `docs/m2/gra/` – wybrane zrzuty z gry (`runClientGameTest -Pgametest=views`), opis w `docs/m2/poprawka-geometrii.md`, „Test w grze po poprawce geometrii”.
- `docs/m2/podglad-siedlisk-s4.md` – podgląd siedlisk (punkt kontrolny 1, przeliczony w etapie H; kadry `docs/m2/m2_<kadr>_<warstwa>.png`, udziały `m2_biome_shares.csv`, `m2_zone_shares.csv`).
