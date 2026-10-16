# Brief projektu „Polish Forests” (dla agentów)

Stan: 2026-10-16. Krótki punkt wejścia: przeczytaj to zamiast całych planów; szczegóły w dokumentach z ostatniej sekcji. Pełna dawna wersja briefu z opisami wszystkich kroków: `docs/historia-stanu.md`.

## Czym jest projekt

Mod Fabric do Minecrafta 26.3 (Java 25, Loom, Gradle 9.7, JDK z `gradle.properties`), id `polishforests`, pakiet `pl.polishforests`, repozytorium GitHub `SernikNaZimno/Polish-Forests-Mod`, gałąź `main`. Tworzy proceduralny, nieskończony świat „Polska” (własny `ChunkGenerator`, model krajobrazu w czystej Javie: niziny, rzeki, wybrzeże, Beskidy) z 36 biomami-siedliskami i lasami zgodnymi z przyrodą, a docelowo drzewa o zmiennej grubości, runo, martwe drewno, faunę i pory roku (opcjonalnie Serene Seasons). Dwa typy świata: skala 1:1 (`poland`) i skala rozgrywki (`poland_gameplay`); dwa tryby roślinności: naturalny (N) i „dzisiejsza Polska” (D).

## Stan

- M0, M1 i M2 zamknięte (M2: punkty kontrolne 1 i 2 przyjęte 2026-10-14; decyzje M2-13 … M2-27 domyślne, do potwierdzenia, `docs/00` E).
- Pionowy wycinek (grąd i bór świeży, plan `docs/04-pionowy-wycinek.md`), część 1 zrobiona: V0, V1, V2 (+V2b), V3, V5, CP-A (styl tekstur i cienki pień przyjęte, `docs/00` G.9).
- Część 2: **V1b** (wygładzenie tekstur) i **V4** (karta norm, pomiar bazowy M2: `docs/wycinek/karta-norm-m2.md`) zrobione. **Następne: V6** (planer drzew `TreeShapePlan`), **V7** (liście, sadzonki, LITE sosny), **V9** (`StandMap`, drzewostan wycinka; usuwa pas słabszego pokrycia koron na granicach chunków, V5-i), **V10** (pomiary i budżety; rozstrzyga pasek kolanka i dziury w narożnikach czapki pnia), potem **CP-B**.
- Tor sarny **V8** gotowy na gałęzi `slice-fauna` (model i kalibracja GeckoLib, runda 1 poprawek), **wstrzymany przez użytkownika, nie scalać**.

## Konwencje

- **Język:** kod, identyfikatory, komentarze, logi, komunikaty, commity po angielsku (pisownia amerykańska); to, co widzi gracz, po angielsku + `pl_pl`; `docs/` i README po polsku.
- **Sole szumów:** literałów w `derive("…")` nigdy nie zmieniamy (inna sól = inny świat); nowe pola szumu dostają nowe sole.
- **Enumy:** kolejność stałych zamrożona (np. `HabitatBiome`, id zapisywane w chunkach); nowe stałe tylko na końcu.
- **Kopia M1:** `src/test/java/pl/polishforests/worldgen/landscape/m1/` zamrożona, nie zmieniać.
- **Złoty test** (`GoldenTerrainTest`): `golden_terrain_m1.txt` nigdy nie zmieniany, `golden_terrain_m2.txt` = obecny teren. Każda zmiana terenu to błąd, dopóki świadomie nie przegenerujesz (`-PwriteGolden -PgoldenKeepCenters -PgoldenFile=build/…`, `diff`, opis w docs). Zmiana w wielu krokach: listy `src/test/golden-allow/K*.txt` sortowane leksykograficznie (numeruj `K08`, `K09`, `K10`), `-PgoldenStrict` bez listy.
- **Migawka rejestru** (`RegistrySnapshotTest`, `src/test/resources/registry_snapshot.txt`): bloki, właściwości, przedmioty moda; przegenerowanie (`-PwriteRegistrySnapshot`) tylko świadomie i z opisem, zamrożona po CP-B.
- **Z10 płynne przejścia:** na granicach biomów i siedlisk skład, gęstość, podszyt, runo, gleby i kolory zmieniają się w pasie przejściowym (nigdy prosta linia ani szachownica); skraj lasu z płaszczem i okrajkiem. Nowe warstwy i reguły palet korzystają z kodów `Ecotone` / `SoilBlend` (`docs/03` §8.8). Ostre tylko brzeg wody, plaża, klif.
- **Zasoby graficzne:** tekstury tylko z `tools/art` (przepis + paleta + lint) przez `generate.py`; ręcznych zmian PNG nie ma (`checkArt` porównuje piksele), poprawki użytkownika w `tools/art/overrides/`. Nie kopiujemy ani nie przerysowujemy zasobów wanilii i innych modów (odwołanie po id wolno). Zdjęcia referencyjne tylko CC0 / domena publiczna, w repo tylko kolory. Zasoby CC BY 4.0 (`LICENSE-assets`, `CREDITS.md`), kod MIT, dźwięki CC0 / CC BY. **Po CP-A tekstury gładkie:** lint pilnuje kontrastu lokalnego w progach z wanilii 26.3 (`tools/art/measure.py`).
- **Dwie skale:** `LandscapeScale` / `VerticalScale` REALISTIC i GAMEPLAY; model liczy w metrach; każdą zmianę testuj w obu skalach (i w obu trybach N/D, gdy dotyczy roślinności).
- **Determinizm:** wynik w punkcie to czysta funkcja (ziarno, współrzędne, ustawienia), generacja wielowątkowa; pamięci podręczne nie mogą zależeć od kolejności zapytań. `String.format` z `Locale.ROOT`.
- **Testy wolne:** klasy > ok. 20 s mają `@Tag("slow")`, pomiary kosztu `@Tag("cost")`; klasę > 2–3 min dziel (Gradle przydziela klasy procesom z góry).

## Struktura pakietów (`src/main/java/pl/polishforests/`, najważniejsze)

- `PolishForests` – punkt wejścia; `season/` – kalendarz, most do SS; `climate/` + `mixin/` – temperatura z wysokości.
- `command/PolishForestsCommands` – `/polishforests find|here|…`; `HabitatTargets` – plany szukania biomów i stref.
- `worldgen/chunk/PolandChunkGenerator` – wypełnianie chunków; `PolandBiomeSource` (36 biomów), `ChunkHabitats`, `PolandSettings`, `VerticalScale`.
- `worldgen/landscape/LandscapeModel` – model krajobrazu (czysta Java), `sample` → `ColumnSample`; `RiverNetwork`, `PeakField`, `Noise`.
- `worldgen/habitat/HabitatClassifier` – siedlisko kolumny; `HabitatBiome`, `Species`, `ForestCover` (maska trybu D), `Calibration` (progi).
- `worldgen/surface/SurfaceBuilder` – plan powierzchni chunka; `SoilBlocks`, `BankShelf`, `SoilBlend`.
- `worldgen/feature/` – `ModFeatures`, `TreeStandFeature` + `plan/TreeStandPlan` (drzewostan), `PlantLayerFeature` (warstwy runa), `plan/Ecotone`.
- `worldgen/tree/CrownTerritories` – terytoria koron (V5); `StandMap` (w V9 pełna), `Vacancies`, `AgeClass`, `CrownLayer`.
- `worldgen/norms/` – `ForestNorms` (karta norm §6), `ForestCensus`, `CensusGrid`, `CensusTestPlot` (V4).
- `block/TrunkBlock` – cienki pień (`radius` 1–7); `TreeTrunk` (12 bloków pni), `ModBlocks`, `RegistrySnapshot`.
- `src/client/.../datagen/` – datagen (biomy, drzewa, modele pni `TrunkTemplates`); wynik w `src/main/generated` (w repo, nie edytować ręcznie).
- `src/gametest/` – testy w kliencie: m.in. `SliceClientGameTest` (wycinek, karta norm, tablica próbek pni), `RenderProbesClientGameTest`, `PerformanceClientGameTest`.
- `src/test/` – JUnit, złoty test, podglądy PNG, `SliceSitesTest`, `TrunkModelRayTest`, `CrownTerritoryTest`.

## Komendy

```bash
./gradlew build                     # kompilacja + test
./gradlew fastTest                  # bez 'slow' i 'cost', 3 procesy, ok. 1 min
./gradlew test                      # pełny zestaw (bez 'cost'); -PtestForks=<n> (każdy proces 3 GB sterty)
tools/dev/run-tests [--reuse] test  # pełny zestaw ze skrótem drzewa; log w build/test-logs/
./gradlew costTest [-PcostRuns=15]  # pomiary czasu (SampleCostTest, CrownTerritoryCostTest), tylko na spokojnej maszynie
./gradlew test --tests '*GoldenTerrainTest*' [-PgoldenReport=build/g.txt] [-PwriteGolden -PgoldenKeepCenters -PgoldenFile=…]
./gradlew test --tests '*RegistrySnapshotTest*' [-PwriteRegistrySnapshot]
./gradlew test --tests '*SliceSitesTest*' [-PfindSliceSites] [-PsliceReport]
./gradlew runDatagen | checkDatagen # datagen do src/main/generated / błąd, gdy się zmienia
./gradlew checkArt | generateArt    # tekstury = wynik tools/art (Python 3.10+, Pillow, NumPy; -Ppython=<ścieżka>)
python tools/art/sheet.py | lint.py | measure.py [png|katalog]   # arkusze docs/wycinek/grafika/, lint, kontrast i ΔE
./gradlew landscapePreview [-PhabitatsOnly]   # PNG do build/preview
./gradlew crownPreview                        # terytoria koron do docs/wycinek/korony/
./gradlew runClient [-Poptimization [-PnoNvidium]]
python tools/norms/table.py [build/norms/karta-norm.csv] [--out plik.md]   # tabela karty norm
python tools/dev/contact_sheet.py <katalog|glob> [...] --out arkusz.png [--cols 3] [--thumb 480] [--crop x0,y0,x1,y1] [--title …]
```

**Gametesty** (otwierają okno gry): `./gradlew runClientGameTest -Pgametest=<tryb>`, tryby: `all|ui|performance|views|stages|climate|habitats|vegetation|present_day|determinism|ecotones|slice|probes`.
- Flagi: `-Psites=<lista>|none` (dla `slice`: `samples` = tablica próbek pni, ok. 3,5 min; `none` = sama karta norm, ok. 2 min → `build/norms/karta-norm.csv`; pełny `slice` ok. 67 min; dla `stages`: `slice`), `-Pscales=realistic|gameplay`, `-Pmodes=natural|present_day`, `-PnoSeasons` (bez SS i GlitchCore), `-Poptimization` (paczka: Sodium, C2ME…; na NVIDIA z shaderami siatek sam włącza Nvidium), `-PnoNvidium` (czysty Sodium), `-PtimingsReportOnly` (progi czasu tylko w raporcie, gdy maszyna zajęta), `-Pprofile`, `-PdebugHabitats`, `-PfindSliceCameras` (tylko świat M2).
- `probes`: 6 zestawów (bez flag, `-Poptimization -PnoNvidium`, `-Poptimization`, każdy też z `-PnoSeasons`), wynik `build/probes/<renderer>_<ss|noss>.txt`.
- Determinizm z paczką: `-Pgametest=determinism`, skopiuj `build/determinism/plain.txt` poza `build/determinism`, potem `-Poptimization -PdeterminismReference=<kopia>`.

**Pułapki środowiska:**
- Windows + Git Bash; skrypty Pythona z `PYTHONIOENCODING=utf-8`; ścieżka repo ma spacje (cudzysłów); długie komendy w tle.
- Zrzuty gametestów w `build/run/clientGameTest/screenshots` – katalog czyszczony przy każdym starcie, kopiuj od razu.
- Każda zmiana drzewa (także `docs/`) zmienia skrót `run-tests`; `test --tests …` w międzyczasie unieważnia UP-TO-DATE i pełny zestaw idzie od nowa.
- `costTest` i pomiary czasu w grze tylko bez innej pracy w tle (drugi klient, testy); inaczej `-PtimingsReportOnly`.
- Pomiar wobec M1: migawka w osobnym katalogu (`migawki/README.md`).

## Zasady pracy agentów (`docs/00` F)

- **A1/A2:** w trakcie kroku `fastTest` i wybrane klasy; pełny `test` raz na koniec przez `run-tests`; recenzent bierze wynik z `--reuse`.
- **A3a (2026-10-07):** commit i push (`origin main`) po każdej średniej i znaczącej zmianie. Pośredni: kompilacja + `fastTest` + klasy dotyczące zmiany, tytuł z „(WIP)”. Ostatni commit kroku po `tools/dev/run-tests test` (PASS). Wiadomość po angielsku: tytuł + kilka punktów + `Co-Authored-By: …`. Bez plików tymczasowych, drzewo czyste i wypchnięte; nigdy force-push ani amend opublikowanych commitów.
- **A4/A5:** ten brief jako wejście; agenci równolegli tylko w osobnych worktree bez wspólnych plików.
- **B1:** recenzja według ryzyka (teren, mixiny, biomy, datagen: 2 recenzentów, do 2 rund; dokumentacja, lang, tabele: 1 recenzent, 1 runda). **B2:** tańszy model do prac mechanicznych. **B3:** problem po 2 rundach poprawek → stop i 2–3 warianty z kosztami dla użytkownika. Recenzenci oglądają pełne zestawy kadrów (B4 odrzucona).
- **T1–T5 (2026-10-16):** T1 krótki brief (ten plik; historia w `historia-stanu.md`). T2 mniej tur: zbieraj odczyty i komendy w paczki. T3 zrzuty oglądaj na arkuszach miniatur (`tools/dev/contact_sheet.py`), pełne kadry tylko do zbliżeń. T4 wykonawca przed recenzją sam sprawdza swoją pracę (testy, kadry, opis zgodny z faktami). T5 ponowna recenzja i zbieranie materiałów na tańszym modelu.
- Drobne wybory domyślnie, z zapisem w `docs/00` G i raporcie; pytaj tylko o duże, trudne do odwrócenia sprawy.

## Gdzie szukać szczegółów

- `docs/04-pionowy-wycinek.md` – plan wycinka (§3 kroki, §6 karta norm) i sekcje „Stan po V<n>” / „Runda … poprawek” z pomiarami i uwagami dla kolejnych kroków.
- `docs/00-decyzje-do-podjecia.md` – wszystkie decyzje; G = wycinek (G.5–G.11 drobne wybory kroków), F = sposób pracy.
- `docs/03-m2-biomy.md` – M2: biomy, siedliska, gleby, dekoracja, Z10 (§8.8), testy (§12).
- `docs/01-architektura.md` – architektura, kamienie milowe (§8), skale (§12), rzeki i morze (§13); `docs/02-wydajnosc.md` – wydajność w grze.
- `docs/m2/poprawka-geometrii.md` – K0–K8 i „Co zostaje do M5”.
- `docs/historia-stanu.md` – pełna dawna treść briefu (opisy kroków M2 i wycinka do V4).
- Zrzuty: `docs/wycinek/` (baza M2, grafika, pnie, korony, v3), `docs/m2/`.
