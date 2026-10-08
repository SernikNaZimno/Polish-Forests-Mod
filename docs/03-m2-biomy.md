# M2: biomy, siedliska, gleby i temperatura. Plan wykonawczy

Data: 2026-10-02. Plan powstał z pięciu raportów rozpoznania (kod, wanilia 26.3, ekologia, klimat i Serene Seasons, weryfikacja w sieci), trzech niezależnych projektów i oceny dwóch sędziów.

**Nazwy w kodzie od M2-9 (2026-10-03).** Mod nazywa się „Polish Forests” (id `polishforests`, pakiet `pl.polishforests`; dawniej „Przyrodniczo zgodne lasy”, `polskielasy`). Od decyzji M2-9 identyfikatory w kodzie są angielskie, a polski zostaje jako tłumaczenie `pl_pl`. W tym planie klasy, pliki, pola rekordów, komendy, właściwości Gradle oraz id biomów, featurów, tagów i bloków mają już nowe nazwy. Dawne polskie id biomów są w §2, a dawne nazwy klas pakietu siedlisk w §11. Oznaczenia pisane wielkimi literami (typy krajobrazu, formy terenu, strefy, gleby, flagi gatunków) to notacja planu i zostają po polsku. W kodzie odpowiadają im:
- typy krajobrazu (`LandscapeType`): SANDR → `OUTWASH_PLAIN`, WYSOCZYZNA → `MORAINE_PLATEAU`, RÓWNINA → `OLD_GLACIAL_PLAIN`, POGÓRZE → `FOOTHILLS`, BESKIDY → `BESKIDS`, POBRZEŻE → `COASTLAND`, MORZE → `SEA`;
- formy terenu (`Landform`): WYDMA → `INLAND_DUNES`, WAL_MORENOWY → `END_MORAINE`, GRZBIET → `RIDGE`, SZCZYT → `SUMMIT`, DOLINA_GORSKA → `MOUNTAIN_VALLEY`, PLAZA → `BEACH`, WYDMA_NADMORSKA → `COASTAL_DUNES`, KLIF → `CLIFF`, ZRODLO → `HEADWATERS`;
- strefy (`Zone`): KORYTO → `CHANNEL`, ELODEIDY → `SUBMERGED_PLANTS`, NYMFEIDY → `FLOATING_LEAVED_PLANTS`, SZUWAR → `REEDBED`, SZUWAR_LADOWY → `SHORE_REEDBED`, ŁACHA → `POINT_BAR`, WIKLINA → `WILLOW_SCRUB`, OKRAJEK → `HERB_FRINGE`, ZIOLOROSLA → `TALL_HERBS`, WIERZBY → `RIVERSIDE_WILLOWS`, ŁOZOWISKO → `WILLOW_CARR`, KAMIENIEC → `GRAVEL_BAR`, ZIOLOROSLA_GORSKIE → `MONTANE_TALL_HERBS`, ZRODLISKO → `SPRING_AREA`, PLO → `FLOATING_MAT`, OLSZA_BRZEG → `SHORE_ALDERS`, KIDZINA → `STRANDLINE`, WYDMA_INICJALNA → `EMBRYO_DUNE`, KLIF_SCIANA → `CLIFF_FACE`, KLIF_KORONA → `CLIFF_TOP`, GRANICA_LASU → `TIMBERLINE`, SZPALER → `TREE_ROW`;
- gleby (`Soil`): tabela w §7.4 (goła glina klifu to `TILL`);
- tryb N („roślinność naturalna”) i tryb D („dzisiejsza Polska”) → `HabitatClassifier.Mode.NATURAL` i `PRESENT_DAY`; flagi BUK, JODŁA, SWIERK, GRAB → `BEECH`, `FIR`, `SPRUCE`, `HORNBEAM`;
- kody STL (Bs, Bśw, OlJ…) zostają jako `ForestSiteType.code()`, a łacińskie nazwy zespołów (`Association`) bez zmian; zespoły o polskich nazwach mają angielskie stałe, np. zastoisko → `BACKSWAMP`, jaworzyna → `SYCAMORE_RAVINE_FOREST`, młaka → `SPRING_FEN`;
- sole szumów w `derive("…")` zostają bez zmian, także polskie (`habitat.piask`, `habitat.prowincje`…), bo inna sól to inny świat.

**Poprawka geometrii terenu (M2-8, 2026-10-03 – 2026-10-06, kroki K0–K7).** Przed fazą 2 teren dostał wielkie masywy Beskidów (decyzja M2-8: szczyty ok. 1620–1725 m w obu skalach, kosodrzewina i hala także w GAMEPLAY), ciągłe pola i gładką geometrię dolin, starorzecza jako półksiężyce, jeziora rynnowe kończące się przed doliną, niecki bez ścian, wybrzeże wydmowe z klifem na ok. 1/5 brzegu i zalew za niskim brzegiem, a także okno mieszania regionów 5 × 5 bez szwów. Złoty test ma od K7 nowy plik `golden_terrain_m2.txt`. Opis, odstępstwa i pomiary przed i po: `docs/m2/poprawka-geometrii.md`; elementy terenu: `docs/01-architektura.md` §15. Liczby z S0–S4 w tym planie (udziały, pasy, piętra) zmierzono na terenie sprzed poprawki; przeliczyła je poprawka klasyfikatora (etap H, 2026-10-07: „Stan po poprawce geometrii” w §3.4), a sprawdzi punkt kontrolny 2.

**Podstawa.** Bazą jest projekt „silnik”, który dostał najwyższą średnią ocen sędziów (8,05 wobec 7,45 dla „wierności” i 7,35 dla „etapów”). Przeszczepione pomysły:
- z „wierności”: półka brzegowa, mikrorelief kęp i dolinek, bloki gleb, osobne wydmy białe i szare, testy zasięgów i pięter;
- z „etapów”: złoty test terenu, kolejność kroków, wczesny mixin temperatury, próba datagenu na jednym biomie, punkty kontrolne z użytkownikiem, rekordy z samymi typami prostymi.

Wszystkie błędy faktyczne wskazane przez sędziów sprawdziłem w jarze 26.3 i w Fabric API 0.161.0+26.3. Lista jest w sekcji 14.

**Decyzje użytkownika (2026-10-02):**
- **M2-A, granulacja:** 36 biomów jako grupy siedliskowe (§2).
- **M2-B, tryb domyślny:** przełącznik „dzisiejsza Polska” / „roślinność naturalna” działa już w M2 (krok S8). Domyślna jest roślinność naturalna, do M8.
- **M2-C, struktury:** wszystkie struktury wanilii, które da się sensownie umieścić (§10.1). Pomijamy tylko te, które wymagają warunków nieobecnych w świecie: monumenty (głęboki ocean), piramidy, świątynie dżungli, starożytne miasta (biom głębokiej ciemności pod ziemią).
- **M2-D, bloki:** bez własnych bloków do M4. Torf zastępuje `mud`, mursz `mud` z `rooted_dirt`, torfowiec `moss_block`. Przy wodzie trzcinę zastępuje `sugar_cane` na półce brzegowej, a pałkę `small_dripleaf` w wodzie 1-blokowej na dnie z błota.

---

## 0. Cel i zakres

M2 dostarcza:
1. Świat Polska używa wyłącznie biomów `polishforests:*` (36).
2. Każda kolumna dostaje siedlisko: biom, strefę, STL, zespół, glebę, pokrycie i flagi zasięgu gatunków.
3. Brzegi rzek, jezior i starorzeczy porastają wikliny, ziołorośla, szuwar, łęgi i olsy. Krótka trawa i kwiaty zajmują tam najwyżej 10% kolumn. Tego wprost chciał użytkownik.
4. Temperatura spada z wysokością w metrach (0,55 °C/100 m). Znika letni śnieg na sandrach i w Beskidach.
5. Gleby i bloki powierzchni zależą od siedliska (bloki wanilijne, M2-D).
6. Drzewa i rośliny to zastępstwa wanilijne za warstwą `polishforests:tree/*`, `polishforests:shrub/*` i paletami JSON. M3 i M4 podmieniają pliki, a biomy zostają bez zmian.

Poza M2:

| Kamień | Zakres |
|---|---|
| M3 | własne drzewa ze zmienną grubością pnia, mapa drzewostanów, las gospodarczy (D9), liście sezonowe (B6), wysokości 1:1 |
| M4 | runo i mchy jako własne bloki, ściółki, martwe drewno według norm, grzybobranie, fenologia, klimat sezonowy S2, człon regionalny temperatury, luki w lesie naturalnym (E6) |
| M5 | rzeźba dna doliny (wały, terasy, zastoiska, E4), Tatry, Sudety, Bieszczady, less, rędzina, wapień |
| M8 | pola uprawne, wsie, miedze, wały przeciwpowodziowe (E5) |

---

## 1. Zasady

| # | Zasada | Po co |
|---|---|---|
| Z1 | Siedlisko liczy czysta funkcja `HabitatClassifier.classify(ColumnSample, HabitatSettings)`. Wynik to jeden `int`. Wołają ją: źródło biomów (16 razy na chunk), `fill()` (256 razy), dyspozytory roślinności (przez mapę chunka), komendy, podgląd PNG i testy. | Biom, gleba i roślinność nie mogą się rozjechać. Całość da się testować bez gry. |
| Z2 | Biom zależy tylko od kolumny (2,5D), nie od Y kwarty. Piętra górskie wynikają z wysokości gruntu w metrach. To zmiana sekcji 4 architektury. | Piętra idą za gruntem. `spawnOriginalMobs` czyta biom na maxY. Render opadu bierze biom na Y kamery. Trawa nie zmienia koloru w powietrzu. Znika ok. 8400 wywołań `biomeFor` na chunk. |
| Z3 | Biom niesie tylko to, czego nie da się zrobić per kolumna: kolory, atmosferę, spawny wanilii, temperaturę bazową, tagi (struktury, SS) i nazwę w F3 i `/locate`. | Mało biomów, bez kopii regionalnych i piętrowych. |
| Z4 | Strefy węższe niż ok. 12 m (3 kwarty) nie są biomami, tylko polem `zone`. Malują glebę i mikrorelief w `fill()` i sterują roślinnością w rozdzielczości bloku. | Rozmycie `BiomeManager` (±2 bloki) nie psuje pasów szuwaru i wikliny. Każdy ciek, także 1,5-metrowy, dostaje pas roślinności. |
| Z5 | Krok 9 dekoracji to ta sama krótka lista dyspozytorów w każdym biomie. Palety są w JSON. Dyspozytory mają własne ziarno (seed, chunk, warstwa). | Cykl „Feature order cycle” po naszej stronie jest niemożliwy. Cudze mody nie przesuwają naszej dekoracji. |
| Z6 | Nowe pola modelu pochodzą z obliczeń już wykonanych w `sample` i z dwóch siatek z pamięcią. W generacji nie wołamy `describe()`, `coastDistance()` ani `landElevation()`, poza budową kafli siatek. | Próbka drożeje najwyżej o kilka procent. |
| Z7 | Progi zawsze w metrach modelu. Odległości mnożymy przez `k = local` (REAL 1, GAMEPLAY 0,5) albo skalujemy szerokością koryta `W` (w GAMEPLAY już ×0,2). Minima stref podajemy w blokach. | Działa w obu skalach świata. |
| Z8 | Jedno źródło prawdy: enumy `HabitatBiome`, `Zone`, `ForestSiteType`, `Association`, `Soil`, `Species`. Z nich datagen robi biomy, featury, tagi i lang, a komendy biorą z nich cele. | Brak dublowania. Testy pilnują spójności. |
| Z9 | Biom wybierają tylko wejścia o fali ≥ 64 m. Drgania granic stref o fali 30–60 m zmieniają tylko strefę, zespół i glebę. | Biomy nie tworzą jednokwartowych wysepek. |

---

## 2. Biomy (36)

Identyfikatory (`polishforests:<id>`) są od M2 zamrożone, bo chunki zapisują biomy po nazwie. **Od M2-9 identyfikatory są angielskie** (kolumna „id”). Plan pisał je po polsku w ASCII (kolumna „dawne id”, przestrzeń `polskielasy:<id>`). Polskie id nie trafiły do żadnego świata, bo biomy rejestruje dopiero krok S5. Kolejność enuma `HabitatBiome` jest stała, nowe biomy dopisujemy tylko na końcu. Nazwy zarezerwowane na M5 (nierejestrowane w M2): `crags`, `scree`, `subalpine_meadow`, `xerothermic_grassland`, `stone_pine_forest` (dawniej `turnie`, `piargi`, `polonina`, `murawa_kserotermiczna`, `bor_limbowy`).

**Oznaczenia:**
- `H`: wysokość gruntu w m n.p.m.; `H*`: H po korekcie ekspozycji i szumu (§5.1);
- `trofia`: B, BM, LM, L; `DGW`: głębokość wody gruntowej w m (§3.3);
- `wyp`: lokalna wypukłość w m; `nach`, `eksp`: nachylenie i ekspozycja z siatki 32 m;
- `O`, `P`: oceaniczność i podgórskość, 0–1;
- `wP`, `wB`: wagi pogórza i Beskidów;
- `d`: odległość od brzegu koryta; `W`: szerokość koryta; `u`: położenie w dnie doliny (0 przy korycie, 1 na skraju); `s`: odległość od brzegu wody stojącej; `z`: głębokość wody;
- `cD`: odległość od brzegu morza; `B` = 60·k, `D` = 220·k: plaża i pas wydm z modelu;
- klasy cieków A, B, C: §4;
- w kodzie: `wyp` = `terrain.convexity`, `nach` = `terrain.slope`, `eksp` = `terrain.aspect`, `O` = `region.oceanicity`, `P` = `region.mountainInfluence`, `wP` = `terrain.wFoothills`, `wB` = `terrain.wBeskids`, `d` = `waters.channelDist`, `W` = `waters.channelWidth`, `s` = `waters.s`, `cD` = `terrain.coastD`, `trofia` = `Fertility` (B, BM, LM, L = `OLIGOTROPHIC`, `OLIGO_MESOTROPHIC`, `MESOTROPHIC`, `EUTROPHIC`).

### 2.1 Leśne (18)

| # | id | dawne id | Nazwa | STL | Warunek wyboru |
|---|---|---|---|---|---|
| 1 | `dry_pine_forest` | `bor_suchy` | Bór suchy (chrobotkowy) | Bs | trofia B, DGW > 4 i (wydma ≥ 4 m lub wyp > +2 m); H < 350 |
| 2 | `fresh_pine_forest` | `bor_swiezy` | Bór świeży | Bśw | trofia B, DGW > 2, poza pasem nadmorskim; H < 500 |
| 3 | `coastal_pine_forest` | `bor_bazynowy` | Nadmorski bór bażynowy | Bśw/Bs nadmorskie | SAND, B+D+170k ≤ cD < 2000k, H < 40, DGW > 0,5; pierwsze 250k to wariant wiatrowy |
| 4 | `moist_pine_forest` | `bor_wilgotny` | Bór wilgotny | Bw, BMw | trofia B lub BM, 0,8 < DGW ≤ 2 |
| 5 | `bog_woodland` | `bor_bagienny` | Bór bagienny i brzezina bagienna | Bb, BMb | DGW ≤ 0,5 na SAND; pierścień 20–150k wokół torfu ombro (Odstępstwo S2: `waters.s` oczka sięga tylko 45 m, więc pierścień to 20k–45 m); oczko torfowe na SANDR o promieniu < 75k; H < 400 |
| 6 | `mixed_pine_forest` | `bor_mieszany` | Bór mieszany | BMśw | trofia BM, DGW > 2, (P < 0,5 lub H < 250) |
| 7 | `mixed_forest` | `las_mieszany` | Las mieszany | LMśw, LMw | trofia LM, DGW > 0,8, (P < 0,5 lub H < 250) |
| 8 | `oak_hornbeam_forest` | `grad` | Grąd | Lśw, Lw | trofia L, DGW > 0,8, gdy nie wchodzi buczyna ani jedlina; na POGÓRZU H < 400 |
| 9 | `lowland_beech_forest` | `buczyna_nizinna` | Buczyna niżowa (żyzna i kwaśna) | Lśw, LMśw | trofia L lub LM, DGW > 2, drenaż (wyp ≥ 0 lub nach > 3°), flaga BUK; udział rośnie z O; H < 350 |
| 10 | `alder_carr` | `ols` | Ols | Ol, LMb | DGW ≤ 0,3 przy wodzie stagnującej: zastoiska den, pierścienie jezior na glinie, mule i torfie, torf minero; H < 500 |
| 11 | `ash_alder_forest` | `leg_jesionowo_olszowy` | Łęg jesionowo-olszowy | OlJ | dna cieków klasy B, źródliska, wysięki u podnóża zboczy, wąskie dna klasy C poniżej 700 m; H < 600 |
| 12 | `willow_poplar_forest` | `leg_wierzbowo_topolowy` | Łęg wierzbowo-topolowy | Lł miękki | dno cieku klasy A, d ≤ D_top i u < 0,35 (§4.1); H < 300 |
| 13 | `elm_ash_forest` | `leg_wiazowo_jesionowy` | Łęg wiązowo-jesionowy | Lł twardy | dno cieku klasy A poza łęgiem miękkim i zastoiskami |
| 14 | `upland_fir_forest` | `jedlina_wyzynna` | Wyżynna jedlina i buczyna | BMwyż, LMwyż, Lwyż | P ≥ 0,5, flaga JODŁA, trofia BM, LM lub L (L na stokach N), H 250–650, poza dnami |
| 15 | `montane_beech_forest` | `buczyna_gorska` | Buczyna karpacka | LG, LMG | wP + wB > 0,5, regiel dolny (§5.1); na POGÓRZU stoki N powyżej 450 m |
| 16 | `montane_spruce_forest` | `swierczyna_gorska` | Świerczyna górska | BG, BWG, BMG | regiel górny do granicy lasu; w reglu dolnym wariant Abieti-Piceetum (§5.1) |
| 17 | `gray_alder_forest` | `olszyna_gorska` | Olszyna górska | LłG, OlJG | dno cieku klasy C, d ≤ min(60k, max(10k, 3W)), H 300–1000 (stoki N do 900); młaki |
| 18 | `dwarf_pine_scrub` | `kosodrzewina` | Kosodrzewina | subalpejskie | granica lasu ≤ H* < próg hali, tylko duży masyw (§5.1) |

### 2.2 Nieleśne lądowe (12)

Tryb N to „roślinność naturalna”, tryb D to „dzisiejsza Polska”.

| # | id | dawne id | Nazwa | Warunek wyboru |
|---|---|---|---|---|
| 19 | `raised_bog` | `torfowisko_wysokie` | Torfowisko wysokie | PEAT ombro: środek misy lub oczka torfowego na SANDR albo POBRZEŻU o promieniu > 75k, poza dnem doliny; w trybie D także Bb poza lasem |
| 20 | `fen` | `torfowisko_niskie` | Torfowisko niskie i przejściowe | PEAT minero w zastoiskach szerokich den (półszerokość dna > 300k, u > 0,5, spadek < 0,5‰); brzeg zalewu przy h ≤ 0,4; duże oczko torfowe na glinie |
| 21 | `reedbed` | `szuwar` | Szuwar | woda z ≤ 1,5 m w jeziorze eutroficznym, starorzeczu lub zalewie; ląd przy h ≤ 0,3. Biom tylko w pasie ≥ 12 m, węższy pas to strefa |
| 22 | `willow_scrub` | `wikliny` | Wikliny nadrzeczne | strefa WIKLINA, ŁACHA lub KAMIENIEC, gdy jej pas ma ≥ 12 m |
| 23 | `heath` | `wrzosowisko` | Wrzosowisko i murawa napiaskowa | D: Bs i Bśw poza lasem; N: prześwity na wydmach (≤ 5% SANDR) |
| 24 | `wet_meadow` | `laka_wilgotna` | Łąka wilgotna | D: dna dolin poza pasami łęgu oraz Ol, OlJ, Lw i Bw poza lasem |
| 25 | `hay_meadow` | `laka_swieza` | Łąka świeża i polana | D: część Lśw i LM poza lasem; polany reglowe poniżej 800 m |
| 26 | `arable_land` | `pole` | Pole (w M2 jako ugór) | D: L, LM i BMśw poza lasem przy nach < 5° |
| 27 | `beach` | `plaza` | Plaża | cD < B; podnóże klifu jako wariant kamienisty |
| 28 | `white_dune` | `wydma_biala` | Wydma biała | B ≤ cD, podłoże BEACH_SAND |
| 29 | `gray_dune` | `wydma_szara` | Wydma szara | SAND, cD < B+D+170k, niski brzeg |
| 30 | `alpine_grassland` | `hala` | Piętro alpejskie | H* ≥ próg hali, duży masyw |

### 2.3 Wodne (6)

| # | id | dawne id | Warunek wyboru |
|---|---|---|---|
| 31 | `sea` | `morze` | WaterKind SEA, cD < 0 |
| 32 | `lagoon` | `zalew` | SEA, cD ≥ 0, z > 1,5 m |
| 33 | `river` | `rzeka` | RIVER, klasa A lub B |
| 34 | `stream` | `potok` | RIVER, klasa C |
| 35 | `lake` | `jezioro` | LAKE, KETTLE, OXBOW, jezioro bezodpływowe; poza dystroficznymi; z > 1,5 m lub jezioro oligotroficzne |
| 36 | `dystrophic_lake` | `jezioro_dystroficzne` | KETTLE lub LAKE na SANDR przy torfie ombro albo przy hashu jeziora < 0,3 |

Jeśli użytkownik wybierze wariant minimalny (pytanie o granulację), łączymy biomy tak:
- `coastal_pine_forest` staje się zespołem `fresh_pine_forest`;
- dwa łęgi nadrzeczne łączą się w `floodplain_forest`;
- `stream` łączy się z `river`;
- `dystrophic_lake` łączy się z `lake`;
- dwie łąki łączą się w `meadow`;
- dwie wydmy łączą się w `dune`;
- wikliny (`willow_scrub`) są tylko strefą.

Reguły i strefy zostają bez zmian, zmienia się tylko tabela biom ← siedlisko.

---

## 3. Model i źródło biomów

### 3.1 Nowe pola `ColumnSample`

Nowe pola trafiają do zagnieżdżonych rekordów z **samymi typami prostymi i enumami, bez tablic**. Test `sameSeedGivesSameTerrain` porównuje rekordy przez `equals`, a `equals` porównuje tablice po referencji. Nowe szumy powstają przez `root.derive("habitat.*")`, więc teren się nie zmienia.

| Rekord.pole | Znaczenie | Źródło | Koszt |
|---|---|---|---|
| `terrain.rawSurface` | teren przed wcięciem dolin i jezior (m) | `LM:541`, wartość przed `rivers.query` | 0 |
| `terrain.coastD` | cD (m) | `LM:540` | 0 |
| `terrain.wOutwashPlain … wCoastland` | 6 wag typów: SANDR, WYSOCZYZNA, RÓWNINA, POGÓRZE, BESKIDY, POBRZEŻE | `Blend` (`LM:543-547`) | 0 |
| `terrain.landformBits` (short, bity) | WYDMA, WAL_MORENOWY, GRZBIET, DOLINA_GORSKA, PLAZA, WYDMA_NADMORSKA, KLIF, ZRODLO | warunki z `describe` przeniesione do `sample` (z `raw`, `coastD`, `surface`) | ~0 |
| `terrain.convexity` | lokalna wypukłość (m): składowe krótkofalowe (ripple i wydma, pagórki i wał, flisz) ważone wagami `blend` | kontekst wyjściowy w `cellElevation` | ~0 |
| `terrain.duneHeight`, `terrain.ridgeProfile`, `terrain.massif`, `terrain.cliffHeight` | wysokość wydmy (m), profil fliszu p[0] (0 dolina, 1 grzbiet), siła masywu 0–1, wysokość krawędzi przy brzegu | `duneHeight`, `flyschParts`, `mountainElevation` (`LM:506`), `raw` | 0 |
| `terrain.sandiness` | piaszczystość utworu 0–1 | 1 szum λ 2 km·k, przez tablicę kwantyli | ~40 ns |
| `terrain.sBar`, `terrain.slope`, `terrain.aspect` | wygładzony teren, nachylenie (°), ekspozycja (°) | `CoarseTerrainField` (§3.2) | ~80 ns |
| `waters.streamOrder`, `waters.headwaters` | rząd cieku, strefa źródłowa | `RiverHit` | 0 |
| `waters.channelDist` | d: m od brzegu najbliższego koryta, ≤ 0 w korycie, +∞ poza zasięgiem | minimum `pr[1] − w/2` po odcinkach z filtrem `reach` (`RN:976-1011`; dziś zapis tylko przy d < połowa+12); po S2 ciągła odmiana `pr[5]` (Odstępstwo S2) | kilka porównań na odcinek |
| `waters.channelWidth`, `waters.channelLevel` | W i lustro tego koryta | `Segment.widthAt` / `levelAt` | 0 |
| `waters.inFloor`, `waters.u`, `waters.floorHalfWidth` | dno doliny, u = `bestFloorDist / bestFloorHalf` (NaN poza dnem), półszerokość dna | `RN:1003-1004` | 0 |
| `waters.channelGradient` | spadek w ‰ w skali 1:1 | `bestSlope` (`RN:1073`) wyjęty przed warunek starorzecza | 1 dzielenie |
| `waters.convexBank` | brzeg wypukły zakola | znak krzywizny `MeanderField` × strona w `projectChannel` | ~10 ns |
| `waters.s`, `waters.shoreLevel`, `waters.standingWaterKind`, `waters.ombrotrophicPeat`, `waters.lakeId` | brzeg jeziora, oczka, starorzecza lub jeziora bezodpływowego (ze znakiem: dodatnie na lądzie), lustro, rodzaj, torf ombro, hash do troficzności | `LakeHit` (pas 45–140 m), `kettleAt`, `tunnelLakeAt`; `oxbow()` zwraca też odległość na zewnątrz, do 40·k; po poprawkach S2 jeziora do 150k (Odstępstwo S2) | ~0 |
| `region.oceanicity`, `region.mountainInfluence` | oceaniczność, podgórskość | `RegionalField` (§3.2) | ~60 ns |

Rekord urośnie z ok. 48 do ok. 150 B, czyli ok. 40 KB śmieci na chunk. Jeśli profil pokaże presję GC, dodajemy `sampleInto(ColumnBatch, i, x, z)` z tablicami prymitywów na wątek. Rekord zostaje wtedy dla komend i testów.

**Stan po S2.** `ColumnSample` ma rekordy `Terrain`, `Waters` i `Region` (opis pól w javadocu `ColumnSample`). Wartość NaN znaczy „nie dotyczy” (np. `u` poza dnem, `ridgeProfile` bez komórki fliszu, `sandiness` w morzu), a +∞ w odległościach „poza zasięgiem”. `sBar`, `slope`, `aspect` i pola `Region` doszły w S3 (stan po S3 w §3.2). `describe()` bierze formy, rząd cieku, dno doliny i teren przed wcięciem z próbki, a sam liczy już tylko rynnę, przełęcz, szczyt i regiel. Złoty test przechodzi bez zmian. Koszt `sample` wobec kopii M1 (`SampleCostTest`, 15 przebiegów, trzy uruchomienia): mediana stosunków 1,02–1,05, stosunek z minimów 0,97–1,08 przy szumie ok. ±4–6% na identycznym kodzie. W pełnym `./gradlew test` (7 przebiegów) mediany wyszły 0,96–1,03. Razem z pomiarami recenzji: REAL, cały obszar (nizina) ok. +5% (mediana median ok. 1,048, czyli granica budżetu S2), pozostałe obszary ok. +1–3%. Po poprawkach recenzji (ciągłe d, pierścienie jezior, brzeg wypukły szerokich koryt) mediany wynoszą 1,039, 1,031, 1,037 i 1,004 (REAL cały obszar, REAL Beskidy, GAMEPLAY cały obszar, GAMEPLAY Beskidy). Przy siatkach S3 (budżet fazy 1: +5–10%) trzeba szukać oszczędności: zmienne lokalne zamiast obiektów `Stojaca` i `ReliefParts` albo `sampleInto`. Część oszczędności zrobiliśmy w poprawkach S3 (stan po S3 w §3.2). Liczby są w `docs/m2/pomiary-bazowe-m1.md`.

**Odstępstwo S2:**
- `terrain.wCoastland` nie pochodzi z `Blend`, bo makroregiony nie mają typu POBRZEZE i waga byłaby zawsze 0. To udział pasa wybrzeża: 1 tam, gdzie próbka dostaje typ POBRZEZE (cD < B + 400k), i spada do 0 przy cD = B + D + 2000k. Pięć wag typów regionów to surowe wagi `Blend` (suma 1). Klasyfikator miesza je z wagą 1 − wPobrzeze.
- `terrain.landformBits` to `int` z bitami `Landform.bit()`, a nie `short`. Nazwy form są z enuma `Landform` (`INLAND_DUNES`, `COASTAL_DUNES`), lista jest w `Landform.FROM_SAMPLE`.
- `terrain.convexity` składa się z falowania i wydm sandru, pagórków i wałów wysoczyzny, składowej 500 m·k równiny oraz, we fliszu, wkładu żlebów względem ich średniej (0,52) i szorstkości. Grzbiety i doliny podłużne fliszu opisuje `terrain.ridgeProfile`.
- `terrain.sandiness` to kwantyl szumu `habitat.piask` (λ = 2 km·k) z tablicy dystrybuanty liczonej raz ze stałego ziarna, więc rozkład jest jednostajny na [0, 1].
- `waters.channelDist` = min(pr[5] − W/2) po wszystkich odcinkach w zasięgu, bez wygaszania szerokości przy źródle. W i lustro (`levelAt`, bez zaokrąglenia) są z odcinka, który daje to minimum. `u`, półszerokość dna, rząd i spadek (w ‰) są z cieku o dominującej dolinie. pr[5] to ciągła odmiana odległości od koryta pr[1], którą rzeźbi teren. `MeanderField.distance` (M1) ma dwa skoki. Dla koryta i brzegu nie mają one znaczenia, ale w d są widoczne. Funkcja przełącza się z tablicy na dokładną odległość od łamanej przy 0,15 λ (skok do ok. 6 m przy dużej rzece, λ = 11W), a dokładne szukanie pomija łamaną za granicą okresu u (skoki do ok. 0,8 m). Pole d używa `MeanderField.distances`: płynnego przejścia tablica ↔ dokładna odległość w przedziale 0,12–0,20 λ i szukania z kubełkami modulo okres. Teren liczy dalej starą wartość (złoty test bez zmian).
- **d nie jest odległością euklidesową.** d liczymy w układzie doliny: u wzdłuż osi odcinka (krzywej Hermite'a), v w poprzek, z zakolami doliny. W tym samym układzie rysowane jest koryto, więc strefy z d układają się wzdłuż koryta widocznego w świecie. Przy dużej rzece, której dolina leży kilka kilometrów od osi odcinka (np. lat ≈ −7,3 km na odcinku rzędu 3 dla ziarna testowego), układ jest ściśnięty po wewnętrznej stronie łuku osi. Tam d odbiega od odległości do najbliższej kolumny rzeki o −21% do +27% (pomiar recenzji S2), a jego spadek dochodzi lokalnie do ok. 7,7 m na 1 m. Na transektach 24 × 2 km wokół tej rzeki (REAL) w pasie d ≤ 200 m·k spadek powyżej 1,5 m na 1 m miało 0,58% kroków, powyżej 3 m na 1 m 0,24%, a powyżej 5 m na 1 m 0,06%. Przy rzece nizinnej i rzece rzędu 2 było to 0–0,02% kroków (do 3 m na 1 m), w GAMEPLAY 0–0,06% (do 6,5 m na 1 m). Poza pasem spadek dochodzi do ok. 3 m na 1 m (wewnętrzna strona łuku doliny). Poprawka metryczna (minimum po krzywej w metryce z jakobianu układu) byłaby nieciągła przy zmianie ramienia krzywej, więc zostawiamy d w układzie doliny. Test `channelDistanceIsContinuousAndNonPositiveInChannel` sprawdza cztery miejsca na skalę: rzekę nizinną, rzekę rzędu 3, rzekę rzędu 2 i potok w Beskidach. Wymaga braku skoków (kroki > 1,5 m zagęszcza do 1/256 i 1/65536 m). W pasie spadek musi być ≤ 10 m na 1 m, a powyżej 1,5 m na 1 m najwyżej w 1% kroków. Poza pasem spadek musi być ≤ 4 m na 1 m. Klasyfikator S4 dzieli d progami, więc w tych miejscach pasy stref są lokalnie węższe lub szersze, o tyle samo co koryto. Szum granic stref (±20% szerokości) w dużej części to maskuje. DGW używa d dalej od koryta tylko w członie i·r, gdzie taki błąd nie ma znaczenia.
- **d ≤ 0 nie oznacza wody.** d liczymy z pełnym W, a koryto przy źródle jest węższe (headFade). W suchej głowicy doliny (dno wyżej niż 3 m nad lustrem) koryta nie ma wcale. W obu miejscach d ≤ 0 na pasie szerokości ok. W bez wody. Strefę KORYTO i biom `river` (§4.1, wiersz 0) wybieramy z `waterKind == RIVER`, a nie z d ≤ 0.
- `waters.convexBank` liczymy przy d ≤ max(W; 15 m·k), jak zasięg łach i wikliny w raporcie ekologii. Liczymy go na odcinkach o θ0 ≥ 0,35 (krętość od ok. 1,03) albo o szerokim korycie (Wr = W / chan ≥ 6 m). Odcinki o spadku powyżej ok. 2,8‰ mają krętość 1,02 (θ0 ≈ 0,28). Bez wyjątku dla szerokich koryt KAMIENIEC (§4.3: brzeg wypukły, Wr ≥ 6) byłby osiągalny tylko na rzekach górskich o spadku do ok. 2,8‰. Wąskie potoki (Wr < 6) mają zawsze false. Test liczy przekroje koryta, nie powierzchnię, na pięciu odcinkach na skalę, z oknem na korycie (nie na osi odcinka). W 90–93% przekrojów dokładnie jeden brzeg jest wypukły, a wypukłych brzegów jest 45–47%. Reszta to głównie przekroje przy punktach przegięcia krzywej. Udział powierzchni pasa brzegu wynosi ok. 40%, bo w ciasnym zakolu brzeg wewnętrzny jest krótszy od zewnętrznego. Test sprawdza też stronę w świecie. W kole o promieniu 1,5W + d wokół brzegu wypukłego jest więcej wody niż wokół wklęsłego w 90% rozstrzygniętych przekrojów w REAL (666) i w 94% w GAMEPLAY (880).
- Dodatkowe pole `waters.standingWaterRadius` (promień oczka lub jeziora bezodpływowego, półszerokość jeziora rynnowego lub starorzecza w danym miejscu). Potrzebują go warunki „oczko torfowe o promieniu < 75k / > 75k” z §2. `waters.standingWaterKind` to nowy enum `ColumnSample.StandingWaterKind`: odróżnia oczko torfowe i jezioro bezodpływowe, czego `WaterKind` nie robi. `waters.ombrotrophicPeat` jest prawdą dla oczka torfowego, którego brzeg leży dalej niż 300 m·k od koryta. Liczy się to raz na oczko z zapytania w jego środku, które M1 już robiło.
- Pas `waters.s`: jeziora bezodpływowe do 150 m·k, jeziora rynnowe do max(150 m·k; 70 m), czyli pas olsu z §4.4. Teren zmieniają nadal tylko do 45 m i do zasięgu niecki (`tunnelBank`, 70–140 m). Jeziora bezodpływowe z szerszego filtra kandydatów (`RiverNetwork.LAKE_RING`) trafiają tylko do pierścienia (`RiverHit.ring*`), a nie do terenu. Oczka mają pas do 45 m (`KETTLE_BANK`), a starorzecza do 40 m·k. Pas oczka wynika z komórki 700 m·k, w której mieści się całe oczko ze strefą brzegu. Pas 150 m·k wchodziłby do sąsiednich komórek. Ols przy oczkach (≤ 40k, §4.4) się mieści. Pierścień 20–150k boru bagiennego wokół torfu ombro (§2, nr 5) jest ucięty do 45 m (w GAMEPLAY 45 m zamiast 75 m), bo torf ombro to w modelu tylko oczka torfowe. Pierścień starorzecza jest znany tylko tam, gdzie model sprawdza starorzecze, czyli w dnie, z dala od koryt i na płaskich nizinach.
- `terrain.convexity` i `terrain.duneHeight` w pasie 25 km·meso od morza mnożymy przez ten sam czynnik co rzeźbę w `shapeCoast`: 0,12 + 0,88 smoothstep(0; 25 km·meso; cD). Bez tego wydma 4 m przy cD = 5 km (REAL) miałaby ok. 19 m. Bit `INLAND_DUNES` w `terrain.landformBits` liczymy, jak w M1, z wartości bez czynnika (zgodność z `describe` i złotym testem).
- W morzu i zalewie `sample` nie pyta sieci rzecznej, więc `describe()` nie zgłasza już formy ZRODLO w kolumnach z wodą morską. Wcześniej mógł, bo pytał sieć osobno.

### 3.2 Siatki z pamięcią

**`CoarseTerrainField`** (wygładzony teren, nachylenie, ekspozycja):
- węzły co 32 m (REAL) albo 16 m (GAMEPLAY), wartość w węźle to `landElevation` (teren bez rzek, ok. 3–6 µs);
- nachylenie jest w układzie modelu danej skali (po S3: patrz niżej, uwaga dla S4);
- kafel ma 8×8 węzłów z marginesem 1, a `sBar` to średnia 3×3 węzłów (okno ok. 96 m w REAL, 48 m w GAMEPLAY);
- nachylenie i ekspozycja pochodzą z różnic węzłów, odczyt jest dwuliniowy;
- wychodzi 0,25 węzła na chunk w REAL i 1 w GAMEPLAY, czyli kilka µs na chunk;
- pamięć: `DirectCache` (`AtomicReferenceArray`, 4096 niezmiennych kafli, ok. 2–3 MB), bez blokad i bez czyszczenia całości.

Zastępuje to siatkę z 8 pełnymi `sample()` na węzeł z projektu „wierność”. Tamta w GAMEPLAY kosztowałaby +9–17% modelu i liczyłaby `sBar` z wcięciami dolin, wbrew definicji z raportu ekologii.

**`RegionalField`** (O, P; wariant V3 z raportu ekologii):
- węzły co 16 km·zs (w GAMEPLAY ok. 350 m), interpolacja dwuliniowa, niezmienne kafle;
- O = clamp(0,15 + 0,45·N + 0,25·Wz + 0,15·S; 0; 1), gdzie:
  - N to szum o fali 900 km·zs;
  - Wz to udział morza w 8 punktach na −X co 60 km·zs, z wagami 1/k (zachód to strona zachodu słońca);
  - S = 1 − smoothstep(0; 150 km·zs; coastDistance);
- P = smoothstep(0,35; 0,80; liniowe pole pasm z `mountainField`), kalibrowane tak, by P ≥ 0,5 sięgało ok. 150–250 km·zs od osi pasma;
- koszt węzła ok. 10 µs, raz na wiele tysięcy chunków.

**Stan po S3.** `terrain.sBar`, `terrain.slope` i `terrain.aspect` pochodzą z `CoarseTerrainField`, a `region.oceanicity` i `region.mountainInfluence` z `RegionalField`. Obie siatki trzymają niezmienne kafle w `DirectCache` i działają w każdej kolumnie, także w morzu i zalewie. Siatka terenu to jedyne wywołanie `landElevation` dodane w M2 do ścieżki `sample` (Z6). Kod M1 woła je dalej w oczkach, poziomie jezior i węzłach sieci rzecznej (z własną pamięcią). Złoty test przechodzi bez zmian.
- **Nachylenie w GAMEPLAY (uwaga dla S4).** `slope` to nachylenie w układzie modelu danej skali. W GAMEPLAY odległości poziome są ściśnięte (k = 0,5, pasma 0,3) przy podobnych wysokościach, więc stoki są tam znacznie bardziej strome niż w REAL. Przykład: na sandrze w oknie 4 km stoków ≥ 1° jest w GAMEPLAY 3544 na 4000 punktów, a w REAL 0. W świecie gry wysokości ściska odwzorowanie pionowe (`VerticalScale.GAMEPLAY`: bloki = 1,89 · m^0,742), więc stoki w blokach są podobne do REAL. Nachylenie w blokach to tan(nach) · d(bloki)/d(metry) przy wysokości `sBar`, czyli ok. 0,40 · tan(nach) przy 130 m i ok. 0,24 · tan(nach) przy 1000 m (w REAL czynnik 1 do ok. 900 m). Progi w stopniach z §2, §4 i §5.1 (buczyna > 3°, pole < 5°, ols < 3°, ekspozycja od 5°, jaworzyna > 30°) klasyfikator S4 musi więc porównywać z nachyleniem przeliczonym tym czynnikiem, a nie z `slope` wprost. Ekspozycja nie zależy od skali.
- **Pojedyncze zapytania.** Kafel `CoarseTerrainField` to 121 wywołań `landElevation` (ok. 0,1–0,2 ms). Pierwsze zapytanie w kaflu spoza pamięci liczy więc tylko swoje oczko z 4 × 4 węzłów surowych (16 wywołań) i zaznacza kafel, a dopiero drugie liczy i zapamiętuje cały kafel. Węzeł liczy ta sama funkcja, więc wynik jest identyczny co do bitu (`sparseCellMatchesTile`). Zmierzone: pierwsze zapytanie 14–26 µs, budowa kafla 107–186 µs, odczyt z pamięci 0,15–0,4 µs. Rozrzucone zapytania (komendy, `BiomeSharesTest`, `findClosestBiome3d` z dużym krokiem, §3.5) kosztują więc ok. 16 `landElevation` na punkt zamiast 121. Przy gęstym próbkowaniu (np. krok 32 m) kafle i tak się zapełniają: ok. 2 wywołania na próbkę w REAL i ok. 7,5 w GAMEPLAY przy kroku 32 m, co S5 musi wliczyć w koszt szukania biomu. Kafle `RegionalField` (ok. 0,3 ms) obejmują 128 km·zs, więc w REAL rozrzucone zapytania prawie zawsze trafiają w pamięć.
- `CoarseTerrainFieldTest`: w węźle `sBar` to średnia 3 × 3 `landElevation`, a gradient to różnica centralna. Między węzłami nachylenie zgadza się z różnicami centralnymi `landElevation` (krok = odstęp węzłów) w ±10% w 91–98% punktów o nachyleniu ≥ 1°, w obu skalach i pięciu typach krajobrazu. Mediana stosunku wynosi 0,986–0,998, średnia 0,985–0,993. W REAL sandr i wysoczyzna mają za mało stoków ≥ 1° w oknie 64 m, więc test je pomija. Błąd ekspozycji w 90% punktów wynosi ≤ 4°. Krok w stronę ekspozycji schodzi w dół w ≥ 98% punktów o nachyleniu ≥ 5°. Wynik nie zależy od stanu pamięci ani od wątków (siatka z 4 miejscami, zapytania równoległe).
- `RegionalFieldTest`: O i P leżą w [0, 1], O od 0,15 do 1. Największa zmiana na 1 km·zs (linie wzdłuż X, Z i po przekątnej, 3 ziarna, obie skale) wynosi dla O 0,0095–0,0115, a dla P 0,0143–0,0154. Pas bez buka i świerka zajmuje 13,8%, 14,0% i 14,1% lądu (REAL, 3 ziarna) oraz 13,8% w GAMEPLAY. Udziały lądu: buk 57–61%, naturalny świerk 38–41%, P ≥ 0,5 10–13%, O ≥ 0,75 2–3%. Granica P = 0,5 leży w medianie 220 km od osi pasma (linia zerowa `mountainRaw`; 10 ziaren razem, 4382 punkty, kwartyle 178 i 272 km). Mediany pojedynczych ziaren rozchodzą się od 179 do 279 km (ziarna 20260927, 1 i 2: 210, 201 i 245 km), a ziarna z małymi pasmami mają tylko 150–230 punktów granicy. Dlatego test liczy medianę z 10 ziaren razem. Wcześniejsza liczba 161–200 km dotyczyła odległości od rdzenia pasma (mountainField ≥ 0,8), a nie od osi, i tylko trzech ziaren. Średnie O w pasie 50 km od brzegu wynosi 0,61, a ponad 300 km od niego 0,35. W pasie 50–150 km od brzegu O jest przy morzu na zachodzie średnio o 0,06 wyższe niż przy morzu na wschodzie (0,489 wobec 0,425; test kierunku członu Wz). Wynik `RegionalField` nie zależy od stanu pamięci ani od wątków (pola z 4 miejscami w pamięci, zapytania równoległe).
- Koszt (`SampleCostTest`, 15 przebiegów, obecny/M1, mediana stosunków): po S3 1,071 i 1,074 (REAL, cały obszar), 1,039 i 1,049 (REAL, Beskidy), 1,048 i 1,050 (GAMEPLAY, cały obszar), 1,024 i 1,022 (GAMEPLAY, Beskidy). W tej samej sesji ten sam kod z wyłączonymi siatkami (stałe zamiast odczytu) dał 1,050, 1,042, 1,054 i 1,019. Siatki dokładają więc ok. 0–2% (budżet S3: +5%), a same odczyty obu siatek po rozgrzaniu kafli kosztują ok. 45 ns na kolumnę (ok. 1% `sample`). Szczegóły są w `docs/m2/pomiary-bazowe-m1.md`.
- **Oszczędności (poprawki S3).** Wynik się nie zmienia (złoty test bez zmian). Profil JFR pokazał, że 59% alokowanych bajtów to tablice z `RiverNetwork.projectChannel`. (1) `projectChannel` i `query` biorą tablice robocze z bufora wątku (`RiverNetwork.Scratch` w `TileCache`) zamiast tworzyć 6 tablic na odcinek i 5 na zapytanie. (2) `Stojaca` zastąpiły zmienne lokalne w `sample`. (3) `blend` liczy odległości w tablicach wyniku zamiast w dwóch osobnych, a `LandscapeType.values()` jest wołane raz. (4) Jeziora z samego pierścienia siedlisk dalsze niż pierścień + 1,3 R nie liczą szumu brzegu. Brzeg leży najdalej 1,2 R od środka, więc taki brzeg i tak nie trafiłby do wyniku. `ReliefParts` zostaje obiektem: to 2% alokacji, a zastąpienie wymagałoby przekazywania kilku wyników przez wszystkie funkcje rzeźby. Po poprawkach (trzy uruchomienia, 15 przebiegów, obecny/M1, mediana stosunków): REAL cały obszar 1,012, 1,020 i 1,019; REAL Beskidy 0,984, 1,001 i 0,998; GAMEPLAY cały obszar 1,003, 1,023 i 1,029; GAMEPLAY Beskidy 0,967, 0,977 i 0,981. Stosunek z minimów to najwyżej 1,046. Kryterium kroku (≤ +5% wobec M1) jest więc spełnione z zapasem ok. 2–5% na dalsze kroki.

**Odstępstwo S3:**
- **P: progi i uśrednianie.** P to średnia z okna 5 × 5 węzłów (ok. 80 km·zs) wartości smoothstep(0,465; 0,915; liniowe pole pasm). Liniowe pole to `mountainLinear`: (1 − |mountainRaw|) · maska pasma · odsunięcie od morza, jak w `mountainField`, ale bez sześcianu. Przy progach z planu (0,35; 0,80) granica P = 0,5 leżała w medianie 245–310 km od rdzenia pasma, a nie 150–250 km. Bez uśredniania P zmieniało się do 0,049 na km·zs (5 ziaren). Strome zbocza P dają maski pasma i odsunięcia od morza z `mountainField`, a nie sam grzbiet. Uśrednianie ogranicza zmianę do 1/80 na km·zs wzdłuż osi siatki (po przekątnej zmierzono do 0,0154).
- **N: smoothstep kwantyla.** N = smoothstep(0; 1; kwantyl szumu o fali 900 km·zs). Kwantyl ma tę samą tablicę dystrybuanty co `terrain.sandiness`. Wzór O z planu zostaje bez zmian. Przy samym kwantylu (rozkład jednostajny) pas bez buka i świerka zajmował 17,5–20,5% lądu na 5 ziarnach, przy surowym szumie (0,5 + 0,5 · szum) 30–35%. Smoothstep zagęszcza prowincje przy biegunach oceanicznym i kontynentalnym i daje 13,5–15%.
- **Wz z miękką granicą morza:** punkt liczy się jako morze z wagą 1 − smoothstep(−0,02; 0,02; seaField), żeby O nie skakało, gdy punkt zachodni przechodzi przez linię brzegu.
- **DirectCache:** kafel ma dwa możliwe miejsca, wyznaczone dwiema połówkami skrótu. Nowy kafel trafia w wolne z nich, a gdy oba są zajęte, w to, które wskazuje bit skrótu. Przy jednym miejscu na kafel ok. 9% z 400 rozrzuconych kafli (tak próbkuje `SampleCostTest`) przepychałoby się w każdym przebiegu. Kafle trzymają liczby `float`. 4096 kafli `CoarseTerrainField` to ok. 4 MB, a nie 2–3 MB, bo kafel ma 9 × 9 węzłów z trzema wielkościami. Kafle liczone są z 11 × 11 węzłów surowych (margines 1). `RegionalField` ma 1024 miejsca.
- **Ekspozycja:** azymut kierunku spadku (−gradient) od północy (−Z) zgodnie z ruchem wskazówek zegara: 90° to wschód (+X), 180° południe (+Z), 270° zachód (−X). Na terenie płaskim (gradient 0, np. dno głębokiego morza) wynosi NaN. Atan liczymy wzorem Abramowitza i Stegun 4.4.49 (błąd ≤ 10⁻⁵°, deterministycznie).
- **Pola w morzu:** siatka terenu i pola regionalne są liczone także w kolumnach morza i zalewu. Stała `ColumnSample.Region.BRAK` zniknęła.
- **Budżet kosztu:** przed poprawkami recenzji kryterium „sample z siatkami ≤ +5% względem S0” liczone łącznie wobec M1 nie było spełnione dla REAL, cały obszar (1,07): S2 zużyło ok. +4–5%, S3 ok. 0–2%. Po oszczędnościach bez zmiany wyniku (powyżej) mediany wynoszą 0,97–1,03, więc kryterium jest spełnione. `sampleInto` i zastąpienie `ReliefParts` zostają w zapasie na S10.
- **Pojedyncze zapytania siatki terenu** liczą samo oczko (16 wywołań `landElevation`), a cały kafel dopiero przy drugim zapytaniu (powyżej). Plan zakładał tylko gęstą generację.
- **Zasięg P mierzony od osi pasma** (linia zerowa `mountainRaw`, która jest teraz widoczna w pakiecie dla testów) na 10 ziarnach razem. Progi P (0,465 i 0,915) bez zmian.

### 3.3 Pochodne w klasyfikatorze (bez nowych próbek)

**h** = H − lustro najbliższej wody w zasięgu − 1 m. Korekta wynika z kwantyzacji brzegu: bank w modelu leży na lustrze + 1 m (`RN:1044`).

**DGW** (wzór z raportu ekologii; poprawia odwróconą formułę z projektu „silnik”):

GW = min(sBar − g_typ ; L_w + i·r), DGW = clamp(H − GW; 0; 12).

- `g_typ` mieszane wagami typów: SANDR 2,5; POBRZEŻE 1,5; RÓWNINA 3; WYSOCZYZNA 4; POGÓRZE 5; BESKIDY 8.
- `L_w` i `r`: lustro (+0,3 m) i odległość najbliższej wody w zasięgu 2 km·k: koryto (poziomKoryta, d), woda stojąca (poziomBrzegu, s), morze (0, cD). Bez wody w zasięgu GW = sBar − g_typ.
- `i`: piasek 0,003; glina 0,01; flisz 0,04 (z `sandiness` i podłoża).
- PEAT daje 0. Brzeg na LAKE_MUD daje ≤ 0,2.
- Woda zawieszona na glinie: H − sBar ≤ −1,5 m daje DGW ≤ 1,0; ≤ −3 m daje ≤ 0,3.

**Wilgotność:**

| Klasa | Warunek |
|---|---|
| sucha | SAND, DGW > 4, wydma lub wyp > +2 |
| świeża | DGW > 2 |
| wilgotna | 0,8 < DGW ≤ 2 |
| bagienna | DGW ≤ 0,5 (pas 0,5–0,8 rozstrzyga szum) |

**Trofia.** Liczymy bogactwo r = 1 − kwantyl(piask). Dla każdego typu znamy udziały (B, BM, LM, L). Udziały mieszamy wagami typów, a potem tniemy r progami skumulowanymi. Semantyka jest ta sama we wszystkich typach, co poprawia odwrócone kwantyle z „etapów”.

| Typ / podłoże | B | BM | LM | L |
|---|---|---|---|---|
| SANDR | 60 | 30 | 10 | 0 |
| WYSOCZYZNA (TILL) | 0 | 10 | 30 | 60 |
| RÓWNINA (TILL) | 15 | 25 | 30 | 30 |
| POBRZEŻE (SAND) | 70 | 20 | 10 | 0 |
| POGÓRZE (FLYSCH) | 0 | 10 | 30 | 60 |
| BESKIDY (FLYSCH) | 0 | 15 | 30 | 55 |

- Forma WYDMA i pas nadmorski dają B. ALLUVIUM daje L.
- Przy trofii B lub BM na GLACIAL_TILL glebę liczymy jak na piasku.

### 3.4 Algorytm `HabitatClassifier.classify`

```
1. woda                       -> OpenWater: sea, lagoon, river/stream, lake/dystrophic_lake; z ≤ 1,5 i woda eutroficzna -> reedbed;
                                 strefy KORYTO, ELODEIDY, NYMFEIDY
2. cD < B + D + 2000k         -> Coast (§5.2); null = dalej
3. (rzad > 0 i (wDnie lub d < zasięg)) lub s ≤ pierścień(rodzaj) lub ZRODLO
                              -> WatersideZones (§4); null = dalej
4. wP + wB > 0,5              -> AltitudinalBelts (§5.1); null = strefowe
5. strefowe: stl = ForestSiteType.of(trofia, wilgotność, formy) -> biom (§2) z regułami zasięgu (§9)
6. tryb D: jeśli !ForestCover.isForested(stl, nach, F(x,z)) -> biom nieleśny (strefy przywodne zostają)
7. pack: biom 6b | strefa 5b | stl 5b | zespol 5b | pokrycie 2b | flagi BUK/JODLA/SWIERK/GRAB 4b | gleba 5b  (32 bity)
```

Kolejność oznacza priorytet. Strefa jest ustawiana niezależnie od biomu, np. OKRAJEK wewnątrz `willow_poplar_forest`. Wszystkie progi trzymamy w `habitat/Calibration.java`.

**Stan po S4.** Pakiet `worldgen/habitat` (czysta Java) ma enumy `HabitatBiome` (36, kolejność i identyfikatory z §2, test `biomeIdsAreFrozen`), `Zone` (23), `ForestSiteType`, `Association`, `LandCover`, `Soil`, `Species`, kod `Habitat` (32 bity jak w kroku 7) oraz klasy `HabitatClassifier`, `OpenWater`, `Coast`, `WatersideZones`, `AltitudinalBelts`, `Moisture`, `Fertility`, `SpeciesRanges`, `ForestCover` i `Calibration`. Klasyfikacja trwa 0,14–0,20 µs na kolumnę (`HabitatClassifierTest`, 100 tys. kolumn z pięciu obszarów, najlepszy z 8 przebiegów). Próg 1150 m jest tylko w `AltitudinalBelts` (`UPPER_MONTANE`). Opis terenu (`describe`), źródło biomów M1 i komendy `lower_montane`/`upper_montane` biorą z niego granicę nominalną (`AltitudinalBelts.isUpperMontane`, bez ekspozycji i szumu), a biomy granicę z korektą. Tryb D (maska `ForestCover`) działa, ale P_las to wartości startowe z raportu ekologii; kalibracja i test udziałów trybu D należą do S8. Domyślny jest tryb N.

Udziały w trybie N (`BiomeSharesTest`, 200 tys. kolumn na skalę, 3 ziarna, wtedy plik `docs/m2/m2_udzialy_biomow.csv`; od etapu H `docs/m2/m2_biome_shares.csv` z liczbami po poprawce geometrii): lesistość lądu 99,4% (REAL) i 98,6% (GAMEPLAY). Bory zajmują 88,7% i 88,2% lasu we wnętrzu sandru (waga typu ≥ 0,9), a w całym typie SANDR, razem z pasami mieszania z wysoczyzną, 86,1% i 84,1%. Dosłowne kryterium „na SANDR bory ≥ 85% lasu” jest więc spełnione w GAMEPLAY tylko we wnętrzu typu (do decyzji w punkcie kontrolnym 1; obie liczby są w CSV). W 64 skupiskach po 125 × 125 kolumn (2·10⁶ próbek w obu skalach) każdy łęg spełnia kryteria geometryczne liczone wprost z próbki (`riparianOnlyOnFloorsSpringsAndSeeps`, bez predykatów klasyfikatora): dno modelu albo najwyżej 5 m nad lustrem cieku w dolinie, albo źródlisko. Poza flagą dna modelu leży 19,7% kolumn łęgów (dno według terenu, wysięki i pas 6 bloków przy brzegu), najwyżej 5,0 m nad lustrem. W skupiskach są 20 biomy w REAL i 24 w GAMEPLAY (27 razem). Pozostałe dziewięć jest osiągalnych w próbkach syntetycznych: piętra wysokie, tryb D i biomy wąskich pasów (zalew, wydma szara, torfowisko niskie w zastoiskach wielkich den). Przekroje rzek (`WatersideZonesTest`, po 150 przekrojów na klasę i skalę wybranych równomiernie z siatki) mają kolejność stref z §12.1 w 144–150 ze 150 przekrojów. Kolumny z cofnięciem rangi o najwyżej 1 m (migotanie progu na drobnej rzeźbie skraju dna) nie liczą się jako błąd. Minima E11 (pasy w przekrojach): wiklina ≥ 3 bloki w 100% pasów (REAL) i 98,3% (GAMEPLAY), OlJ ≥ 6 bloków w 98,7% i 96,8%, ols ≥ 10 bloków w 83,3% i 62,0% cięciw; najkrótsze pasy: wiklina 3,0 i 2,0 m, OlJ 4,5 i 1,0 m, ols 2,5 i 0,5 m (tolerancje testu w Odstępstwie S4). Piętra (`AltitudinalBeltsTest`): w reglu dolnym (600–1100 m, poza dnami) buczyna, jedlina i olszyna zajmują 96,7% (REAL) i 96,2% (GAMEPLAY), w 1200–1350 m świerczyna 99,6%. Przejście buczyny w świerczynę typową w wyniku klasyfikacji (połowa kolumn regla, stoki ≥ 5°, poza dnami) leży na stokach S na 1195 m, a na N na 1106 m (różnica 90 m). Na stokach N buczynę zastępuje od 900 m Abieti-Piceetum, więc granica buczyny i jakiejkolwiek świerczyny różni się między S i N o ok. 300 m. Kosodrzewina i hala (E12) wymagają szczytu ponad 1470 m w promieniu 3 km·mspace. W REAL ma go ziarno 4 (masyw 1645 m, ok. 7200 kolumn kosodrzewiny i hali; każda sprawdzona kolumna ma taki szczyt według `landElevation`), a ziarna 20260927 (1443 m) i 1 (1461 m) nie mają. W GAMEPLAY najwyższe szczyty w kwadracie 70 km to 1131 m (20260927) i 1247 m (ziarno 4), więc nie ma tam nawet regla górnego. Kosodrzewinę i halę w obu skalach dała poprawka geometrii terenu (M2-8, krok K2, 2026-10-03): wielkie masywy mają szczyty 1632–1723 m w REAL i 1618–1719 m w GAMEPLAY, a ziarno 20260927 ma kosodrzewinę w obu skalach (masyw najbliższy (0, 0) w REAL: 8,86 km² kosodrzewiny i 0,51 km² hali; masyw najbliższy spawnu GAMEPLAY: 0,75 km² i 0,010 km², `AltitudinalBeltsTest.greatMassifHasDwarfPineAndAlpine`). Miejsca „kosodrzewina” w gametestach (§12.3) i znajdowanie tych biomów komendą (§12.4 p. 5) są więc wykonalne w obu skalach.

Podgląd (`./gradlew landscapePreview`, z `-PhabitatsOnly` sam podgląd siedlisk, ok. 4 min) zapisuje kadry z §12.2 w warstwach biomów (z legendą i udziałami), stref, DGW i trofii, mapy O, P i zasięgów 2000 km, maskę lasu trybu D (50 km), przekroje dolin A, B, C oraz pliki CSV udziałów biomów i stref. Wynik jest w `docs/m2/`, opis kadrów w `docs/m2/podglad-siedlisk-s4.md`. Pliki w `docs/m2/` mają nazwy sprzed M2-9; nowe nazwy podaje ten sam opis.

**Odstępstwo S4:**
- **Wywołanie:** `new HabitatClassifier(seed, scale, mode).classify(sample, x, z)` zamiast `classify(ColumnSample, HabitatSettings)`. Położenie (x, z) służy tylko szumom `habitat.*`: drgania granic stref, płaty, warianty i przejścia pięter. Wynik zależy więc wyłącznie od ziarna, skali, trybu, próbki i położenia. Klasyfikator jest niezmienny, a pochodne kolumny (`HabitatClassifier.Column`) liczy leniwie w obiekcie lokalnym jednego wywołania.
- **Duży masyw (poprawione po recenzji S4):** pole `terrain.summit` to rzeczywisty najwyższy teren bez dolin (`landElevation`) w promieniu 3 km·mspace (900 m w GAMEPLAY), a próg E12 to wprost 1470 m (`AltitudinalBelts.LARGE_MASSIF`). Zamiast progu `massif ≥ m0`, bo sam `massif` nie wystarcza (przy osi silnego pasma szczyty przekraczają 1470 m bez masywu). Pierwsza wersja z S4 (dno pasma + rzeźba · kopuły) zawyżała szczyty o 22–245 m i dawała kosodrzewinę bez dużego masywu. Pole liczy siatka z pamięcią `PeakField`: `landElevation` co 62,5 m·mspace w kaflach 32 × 32 i maksimum w kole wokół węzłów co 250 m·mspace (kafle 8 × 8), interpolowane dwuliniowo. Koło w węźle ma promień 3 km·mspace pomniejszony o przekątną oczka, więc wartość ponad progiem gwarantuje szczyt ponad progiem w promieniu 3 km·mspace od kolumny. Model liczy pole tylko w Beskidach od `AltitudinalBelts.SUMMIT_FROM` (1180 m, najniżej położony pas granicy lasu), gdzie indziej daje 0, więc koszt `sample` poza najwyższymi grzbietami się nie zmienia.
- **Dno doliny z terenu:** flaga `inFloor` pochodzi z doliny dominującej. Jest ucięta prostą linią przy zmianie doliny i obejmuje też grunt wysoko nad bliższym korytem. Dlatego dno to grunt najwyżej 2,3 m nad lustrem najbliższego koryta (dno modelu leży 1,2–2,2 m nad lustrem), do tego `inFloor` albo odległość w półszerokości dna (co najmniej 80k). Gdy grunt leży mniej niż 1,2 m nad tym lustrem (dopływ schodzący bystrzem), rozstrzyga sama flaga. Poza flagą `u` = d / półszerokość dna. *Po poprawce geometrii:* dolinę dominującą wybiera klucz F1 („metry w głąb dna”), `u` to minimum po dnach zawierających kolumnę, `valleyWeight` maksimum po odcinkach, a `floorHalfWidth` i `channelGradient` miękkie maksimum (K3), więc przyczyna prostych ucięć `inFloor` w dużej części znikła. *Etap H:* wysokość nad wodą liczy się od miękkiego poziomu koryt (`softChannelLevel`), a na fladze dna do 4 m (`BANK_H`); szczegóły w „Stan po poprawce geometrii” niżej.
- **Klasa A:** rząd pochodzi z doliny dominującej, a W z najbliższego koryta. Mały dopływ w dnie dużej doliny ma więc rząd 3, dlatego rząd 3 daje klasę A dopiero przy Wr ≥ 15 m (Wr ≥ 30 zawsze). Mały ciek w szerokim dnie (półszerokość > 250k) za swoim OlJ ma resztę dna dużej doliny (łęg wiązowy, zastoiska).
- **Łęg topolowy** według samego d ≤ D_top, bez warunku u < 0,35 i bez preferencji garbów. u skakało prostą linią przy zmianie doliny, a preferencja przeplatała łęg topolowy z wiązowym w pasie D_top–1,15 D_top. Wały brzegowe przyjdą z rzeźbą dna w M5. *Po poprawce geometrii:* `u` jest ciągłe (K3, F1); w etapie H warunku u < 0,35 nie przywróciliśmy (niżej, „Stan po poprawce geometrii”).
- **Zastoiska i ols w dnach małych rzek** według h = H − lustro − 1 (h < 0,6 w zastoiskach, < 0,45 w olsie), a nie według `convexity`. `convexity` ma składowe krótkofalowe (falowanie sandru) i dawało w dnie paski co kilka metrów, wbrew Z9. Płaty mają szum o fali 120 m bez mnożnika k (zastoiska 50% warunku, ols 45%). Ols i zastoiska powstają tylko w dnie z miejscem na pas 2 × 10 bloków (E11).
- **DGW:** człon cieku i wody stojącej działa w dnie zawsze, a poza nim z wcięciem terenu w dolinę lub nieckę (rawSurface − H od 0,5 do 3 m; poza tym kara 50 m). Bez tego DGW skakało prostą linią na granicy zasięgu zapytania sieci rzecznej (d = +∞ dalej). Lustro koryta to min(lustro, H − 1,2), bo najbliższe koryto bywa wyżej niż dno. Człon morza nie ma ucięcia zasięgu 2 km. Starorzecza nie wchodzą do DGW, bo ich pierścienie dawały prostokątne plamy. Pas DGW 0,5–0,8 rozstrzyga szum o fali 80 m bez k.
- **Wysięk (poprawione po recenzji S4)** (łęg jesionowo-olszowy poza dnem): siedlisko bagienne na LM lub L poniżej 600 m, w dolinie (teren wcięty co najmniej 0,5 m poniżej terenu przed doliną, jak `INCISION_FROM` w DGW) i najwyżej 5 m nad lustrem najbliższego koryta (`SEEP_HL`). W S4 nie było warunku bliskości i 12–15% łęgów leżało poza dnem, do 47 m nad ciekiem na płaskiej wysoczyźnie. Nie ma warunku zbocza (H < rawSurface − 2), bo wtedy skraj dna dostawał wąski pas olsu strefowego, ani warunku odległości, bo granica w stałej odległości od koryta zostawiała strzępy łęgu węższe niż 6 bloków. Klasa B dostaje też łęg w pasie 6 bloków od brzegu poza dnem (wąskie dna, nisko nad ciekiem), żeby pas OlJ miał minimum E11.
- **Źródliska:** forma ZRODLO obejmuje całe dno odcinka źródłowego, a promienia 10–40k nie da się wyznaczyć z próbki. Źródlisko to więc pas od koryta o szerokości 0–40k zmiennej z szumem o fali 120 m bez k (średnio 30% pełnego pasa). Pas zaczyna się przy korycie, więc łączy się z łęgiem dna. W S4 były to płaty o fali 35 m·k, czyli wysepki biomu wbrew Z9 i strzępy OlJ węższe niż 6 bloków. Wybór torfowiska niskiego albo olsu w zastoiskach ma ten sam szum o fali 120 m bez k (wcześniej 35 m·k).
- **Trofia:** SANDR 65/31/4/0 zamiast 60/30/10/0. Przy 60/30/10 bory zajmowały 81% lasu sandru (las mieszany z LM 7%), a bliżej moreny udziały BM i LM i tak rosną przez mieszanie typów. Pas nadmorski daje B tylko przez udziały POBRZEŻA ważone `wCoastland` (bez progu wPobrzeze > 0,5, który rysował linię wzdłuż brzegu). Wydmy nadmorskie dają B tylko na piasku (na brzegu wydmowym także na glinie modelu, zob. Wybrzeże). Pas nadmorski na glinie wysokiego brzegu ma udziały wysoczyzny. Mady (ALLUVIUM) dają L tylko poza krainami piasków (waga SANDR + pas nadmorski < 0,5); w krainach piasków dno ma trofię regionu.
- **Wybrzeże (poprawione po recenzji S4):** model daje glinę (GLACIAL_TILL) każdej kolumnie pasa nadmorskiego wyższej niż 8 m, a formę KLIF według samej wysokości, więc w S4 cały pas za plażą wychodził jako klif i las wysoczyzny (0% wydm w świecie). Brzeg wydmowy i klifowy rozróżnia teraz nowe pole `terrain.lowShore` (`low` = 1 − smoothstep(6, 20, hl) z `shapeCoast`), a granicę gołego piasku nowe pole `terrain.bareSandWidth`; teren się nie zmienia. Garb wydmy modelu (6–20 m · low) przy low < 0,5 chowa się pod terenem pasa (hl ok. 13–24 m), więc na 80 przekrojach na skalę tylko 0–1 brzeg miał low ≥ 0,5. Próg brzegu wydmowego to więc low ≥ 0,25, czyli teren przy morzu niższy niż ok. 15 m (`Calibration.LOW_SHORE`); obejmuje to wszystkie mierzeje przed zalewami tej wysokości. Na brzegu wydmowym podłoże siedliska to piasek (`HabitatClassifier.Column.substrate`: BEACH_SAND bliżej niż `bareSandWidth`, dalej SAND), więc są tam wydma biała z wydmą inicjalną, wydma szara, bór bażynowy, zaplecze zalewu i trofia B. Klif (ściana, korona, las wiatrowy) i trofia wysoczyzny są tylko na brzegu wysokim. `CoastTest`: w pasie wydm B..B+D brzegów wydmowych (36 przekrojów w REAL, 40 w GAMEPLAY) wydmy zajmują 100% kolumn lądu poza dnami (biała ok. 61%, szara ok. 38%) bez stref klifu; na brzegach wysokich klif jest w 30 z 40 (REAL) i 32 z 40 przekrojów (GAMEPLAY). Zaplecze mierzei (torfowisko niskie przy h ≤ 0,4 m, ols przy h ≤ 1 m za pasem wydm) jest tylko na brzegu wydmowym i nie sprawdza obecności zalewu ani torfu. Granica boru bażynowego 2000k drga o ±15% (szum wariantów), a pas wybrzeża kończy się za jej najdalszym położeniem (max(B + D + 2000k, 1,15 · 2000k)), więc drganie nie jest ucinane prostą linią. Do decyzji w punkcie kontrolnym 1: próg 15 m (więcej wydm albo więcej klifów). *Po poprawce geometrii (K5b, D5):* opis wyżej dotyczy modelu sprzed D5. Teraz `shapeCoast` miesza niski brzeg (plaża, wydma przednia, zaplecze) z wysokim brzegiem z klifem według udziału wysoczyzny dochodzącej do morza, a `lowShore` = 1 − ten udział klifu (`ColumnSample`), więc garb wydmy nie chowa się już pod płaskim pasem 13–24 m. Próg brzegu wydmowego to `Calibration.LOW_SHORE` = 0,5 (środek mieszania), a sprawa progu 15 m jest rozstrzygnięta decyzją D5 (klif na ok. 1/5 brzegu: 22,2% w REAL, 21,0% w GAMEPLAY) i D5a; szczegóły i pomiary w `docs/m2/poprawka-geometrii.md`, K5, „D2: zalew bez rowu i K5b (D5): wybrzeże wydmowe”. Liczby `CoastTest` wyżej są sprzed D5; po K5b (próg 0,5): REAL 40 przekrojów brzegu wydmowego (w pasie wydm 100% wydm i boru bażynowego, biała 58,6%, szara 41,4%, 0 klifów) i 40 wysokiego (33 z klifem), GAMEPLAY 40 (61,8 / 38,2%) i 40 (24 z klifem). Etap H: wydmy biegną przez dna dolin poza ujściem, brzeg wydmowy w klasyfikacji 77,8% (REAL) i 79,7% (GAMEPLAY) punktów brzegu (niżej, „Stan po poprawce geometrii”). *K8b2:* szerokość plaży B zmienia się wzdłuż brzegu niskiego (zwykle 35–85 m·k), a model podaje ją w nowym polu `terrain.beachWidth`; plaża (`Coast`), koniec pasa wydm (`duneBeltEnd`, `duneOverFloor`) i `CoastTest` liczą się od niej (bez próbki B = 60k). Brzeg wydmowy 77,8% (REAL) i 79,5% (GAMEPLAY), w pasie wydm 100% wydm i boru bażynowego (`docs/m2/poprawka-geometrii.md`, „K8b2”).
- **Szuwar jako biom w wodzie:** w zbiornikach o promieniu ≥ 50k, w starorzeczach pas 15k przy brzegu (dalej nymfeidy). Szacowanie szerokości płycizny z głębokości dawało odwrócone pierścienie (strefa przy brzegu, biom dalej).
- **Pierścienie starorzeczy** tylko przy starorzeczach o półszerokości ≥ 8 m i dalej od koryta niż ta półszerokość. Model podaje pierścień także przy wąskich ciekach, gdzie wody starorzecza nie ma, co dawało rząd jednakowych kresek łozowiska wzdłuż potoku.
- **Minima E11** stosujemy po drganiu szerokości (max(minimum, szerokość · (1 ± 0,2))). Na przekrojach nie wszystkie pasy je spełniają: przekrój tnie płaty i brzegi wysięków pod różnymi kątami, a wysięk może kończyć się tuż za dnem razem z siedliskiem bagiennym. Test wymaga więc minimum w ≥ 95% pasów wikliny i OlJ oraz w ≥ 50% cięciw olsu (zmierzone udziały w Stanie po S4).
- **Testy:** `AltitudinalBeltsTest` liczy regiel dolny w 600–1100 m poza dnami, a nie „poniżej 1100 m”: niżej są dna i stoki jedliny wyżynnej. Granicę regla S–N mierzy w wyniku klasyfikacji (przejście buczyny w świerczynę typową), a nie we wzorze progu. Kosodrzewinę sprawdza wokół wszystkich szczytów ponad 1400 m (siatka 2 km w kwadracie 3000 km w REAL, 60 m w kwadracie 70 km w GAMEPLAY; ziarna 20260927, 4 i 1): co czwarta kolumna kosodrzewiny lub hali musi mieć w promieniu 3 km·mspace teren ponad 1470 m z siatki `landElevation` co 25 m·mspace. `WatersideZonesTest` liczy przekroje wzdłuż normalnej do koryta (po gradiencie d przekrój zakosami wracał przez te same pasy), bierze do 150 przekrojów na klasę w kolejności skrótu punktu startowego (w S4: 35 pierwszych według x, czyli z jednego pasa świata) i wymaga uporządkowania w ≥ 90% przekrojów. Dla olsu wymaga ≥ 10 bloków w co najmniej połowie cięciw, bo przekrój tnie płaty pod różnymi kątami. Pierścieni wód stojących tu nie mierzy. `BiomeSharesTest` sprawdza bory sandru we wnętrzu typu (waga ≥ 0,9), bo w GAMEPLAY pasy mieszania z wysoczyzną zajmują dużą część sandru; udział w całym typie tylko wypisuje. Test osiągalności wypisuje biomy i strefy ze świata osobno dla każdej skali.
- **Lesistość w trybie N** wynosi 98,6–99,4%, a nie 90–95% z raportu ekologii. Otwarte są tylko wody, torfowiska, szuwary, wikliny, plaże, wydmy i prześwity wrzosowisk. Luki w lesie naturalnym (E6) to M4.
- **DGW w skali rozgrywki:** człon i·r liczy odległość od koryta, wody stojącej i morza w skali 1:1 (r / k, Z7), bo w GAMEPLAY odległości są ściśnięte przy podobnych wysokościach i zwierciadło rosło o połowę wolniej niż w REAL (tak jak nachylenie, §3.2).
- **Jedlina wyżynna** pod reglem dolnym ma te same warunki co strefowa (§2.1 nr 14: H 250–650 m, trofia bez B, L tylko na stokach N) i nie wchodzi w dna dolin. Buczyna i świerczyna górska nie sprawdzają zasięgów buka i świerka: w pasie górskim P jest bliskie 1, więc leżą w zasięgu z geografii pól regionalnych.
- **Łozowisko przy jeziorach** powstaje na torfie, mule i także na glinie (plan §4.4: torf lub muł), bo brzegi jezior rynnowych na wysoczyźnie leżą zwykle na glinie, a model nie ma osobnego podłoża namułów brzegowych. To samo podłoże dopuszcza ols przy jeziorach.
- **Siedlisko suche i drenaż buczyny niżowej** według wklęsłości `rawSurface − sBar` (`Column.concavity`), a nie `convexity`, z tego samego powodu co zastoiska (składowe krótkofalowe `convexity`, Z9).
- **Bór bagienny wokół torfowiska ombrotroficznego** w pasie 0–45 m od brzegu misy (`KETTLE_RING`), a nie 20k–45 m: torf w próbce kończy się na brzegu misy, więc pas 0–20k nie ma osobnego siedliska i bór bagienny zaczyna się od brzegu (w misie torfowisko wysokie w dużych oczkach, bór bagienny w małych).
- **Progi:** wszystkie progi klasyfikacji są w `Calibration` (po recenzji S4 przeniesione także: dno przy innym korycie 1,15 m, lustro dna 1,2 m, łacha, rząd szerokiego dna, drganie boru bażynowego, warianty trybu D), a wysokości i nachylenia pięter w `AltitudinalBelts` (ekspozycja od 5°, grzbiet wiatrowy poniżej 15°, jaworzyna). Napisy celów komend `upper_montane` i `lower_montane` w `lang` mają jeszcze liczbę 1150 wpisaną na stałe; w S5 `ModLanguageProvider` ma je generować z `AltitudinalBelts.UPPER_MONTANE`.

**Stan po poprawce geometrii (etap H, 2026-10-07).** Poprawki klasyfikatora po nowej geometrii terenu (K0–K7). Teren się nie zmienia (złoty test bez zmian); kadry i udziały przeliczone (`docs/m2/podglad-siedlisk-s4.md`, `docs/m2/m2_biome_shares.csv`, `m2_zone_shares.csv`).

- **Miękki poziom koryta (G3, rozstrzygnięcie po K4c).** Nowe pole `waters.softChannelLevel` (`RiverNetwork`, z bufora kandydatów F2, bez nowych próbek): poziomy luster koryt w zasięgu ważone (1 − δ/r)², gdzie δ to nadwyżka odległości nad najbliższym korytem, a r = d_min + 5 m·k (najwyżej 80 m·k). Przy jednym korycie pole jest równe `channelLevel`, a na dwusiecznej między korytami jest ciągłe; `channelLevel` przeskakuje tam o różnicę luster (w kadrze zbiegu GAMEPLAY do 32 m na 1 m). Klasyfikator mierzy od niego wysokość nad wodą: dno doliny (`onValleyFloor`), h stref (zastoiska, ols), wysięki, pas OlJ klasy B i DGW. Na fladze dna modelu (z lejkiem G3) dno sięga do `BANK_H` = 4 m nad wodą (było `FLOOR_H` = 2,3 m, które cięło lejek ząbkami); poza flagą skraj dna zostaje do 2,3 m. Kadr `gameplay_beskids_confluence_300m` (dawny `Z_besk_conf_300m`): wieloboczne płaty łęgów zniknęły, zostały zaokrąglone płaty dna lejka; ols 0,28% → 0,10%, łęg z olszą szarą 3,10% → 4,06% kadru. Test `WatersideZonesTest.softChannelLevelIsContinuousAtConfluences`: każdy krok pola > 5 cm zagęszczony do 1 mm maleje razem z krokiem (brak skoków), a najbliższe koryto ma w tych kadrach 123 (GAMEPLAY) i 47 (REAL) skoków > 0,5 m. Runda 1 recenzji: kandydat, którego lustro leży ponad 4 m pod gruntem kolumny, traci wagę (smoothstep 4–12 m, do 1% wagi; `RiverNetwork.SOFT_LEVEL_DRY_*`). Suchy, niewycięty odcinek koryta głęboko pod gruntem tuż obok prawdziwego koryta ściągał miękki poziom brzegu kilkadziesiąt metrów pod wodę (do ok. 40 m na 1 m na stokach Beskidów GAMEPLAY), a brzeg tracił strefy (1 na 4531 kolumn brzegu); czynnik jest ciągły w terenie i poziomach, a przy jednym korycie się skraca. Teraz pole ma w kadrach testu najwyżej 3,5 m na 1 m (GAMEPLAY) i 8,3 m na 1 m (REAL), a nowy test `realChannelBanksStayByWater` (brzegi prawdziwych koryt: d ≤ 3 m·k, najwyżej 2,5 m nad wodą) nie znajduje żadnej kolumny brzegu bez wody (0 z 7530, 4902, 8237 i 5852 w czterech obszarach). Pole jest ciągłe w pobliżu koryt; daleko od nich (d_min ok. 700 m·k i więcej) zbiór kandydatów zmienia się na prostych liniach ramek odcinków i pole skacze tam razem z `channelLevel`, bez widocznego skutku (DGW jest tam ograniczone do 12 m).
- **Strefy tylko przy prawdziwej wodzie (problem 1 fazy 1).** Odcinek koryta, którego dolina nie wcina (krótkie odcinki źródłowe na stokach i kopułach), ma lustro 10 m i więcej pod gruntem, a dostawał pasy wikliny, ziołorośli, łęgu i źródliska wzdłuż linii bez wody. Teraz strefy cieku i łęgi są tylko na gruncie najwyżej `BANK_H` = 4 m nad miękkim poziomem koryt (`Column.byWater`; brzegi prawdziwych koryt leżą 1,2–2,3 m nad wodą, a kolumny w obrysie suchych odcinków w 55–91% ponad 10 m). Dno dużej rzeki (F2) liczy się od jej koryta, więc suchy dopływ na jej dnie go nie zabiera. Test `noWatercourseZonesAlongDryChannels`: w Beskidach GAMEPLAY 6912 kolumn brzegu suchych odcinków na 256 tys. próbek, żadna ze strefą cieku ani łęgiem. Udziały stref GAMEPLAY: wiklina 0,663% → 0,471%, źródliska 0,360% → 0,218%. Wysięki i pas OlJ klasy B przy wąskim dnie mają ten sam limit wysokości (`SEEP_HL` = `BANK_H` = 4 m, w S4 5 m): pas 4–5 m po wprowadzeniu `byWater` był martwy.
- **Drganie progów (Z9, problem 2).** Granice biomów strefowych były izoliniami szumów o fali 2 km·k (żyzność) i 1,5 km·k (warianty buczyny), czyli w kadrach kilkuset metrów długimi prostymi i łukami kół (`tunnel_valley_lake_1km`). Każdy skumulowany próg żyzności drga własnym szumem o fali 150 m·k (75 m w GAMEPLAY) z amplitudą `FERTILITY_JITTER` = 0,05 kwantyla, najwyżej połowa udziałów po obu stronach progu (udział 0 zostaje 0, progi zostają uporządkowane, udziały średnio bez zmian); próg wariantu buczyny drga tak samo, z tym samym ograniczeniem do połowy udziału (runda 1 recenzji: ze stałą amplitudą przy granicy zasięgu buka, gdzie udział spada do 0, powstawały wysepki buczyny 10–30 m). Szum liczy się tylko przy progu. Granica świeże/wilgotne buczyny (DGW = 2 m) drga o ±0,3 m DGW (`BEECH_DGW_JITTER`, szum 150 m·k): na płaskim zapleczu wybrzeża DGW zmienia się powoli z wagami typów, więc krawędź buczyny była prostą równoległą do brzegu (ok. 1,4 km w kadrze recenzji przy (−171669, −220818)). Próg mady w krainach piasków (`ALLUVIUM_ON_SAND` 0,5 wagi sandru i pasa nadmorskiego) drga o ±0,15 (`ALLUVIUM_ON_SAND_JITTER`), bo dno każdej doliny przecinającej izolinię 0,5 zmieniało łęg w bór prostą linią w poprzek dna. Buczyna niżowa: warunek drenażu `rawSurface − sBar ≥ −BEECH_CONCAVITY` (0,75 m) zamiast ≥ 0, bo na płaskim terenie to pole ma kilka decymetrów tekstury interpolacji siatki `CoarseTerrainField` (koronkowe płaty buczyny); żeby udział buczyny został jak w S4, `BEECH_MAX` 0,85 → 0,5 (REAL 3,7% → 4,2%, GAMEPLAY 4,9% → 3,9% wszystkich kolumn).
- **Klasa C bez prostych ucięć.** Półszerokość dna potoku zmienia się wolno wzdłuż cieku, więc jej progi (wąska dolina V 10 m·k, całe dno do 30 m·k) przecinały dno prostą linią (`mountain_stream_1km`: płaty olszy szarej ucięte prostymi). Półszerokość drga ±20% (szum 150 m·k), pas łęgu rośnie od 0 między 0,75 a 1 progu wąskiej doliny, a „całe dno” wygasza się między 30 a 37,5 m·k, więc łęg zwęża się i kończy klinem wzdłuż potoku. „Całe dno” sięga do max(80 m·k, 2 × półszerokość), żeby objąć lejek ujścia (G3).
- **Wybrzeże (D5, problem 3).** Próg `LOW_SHORE` 0,5 zostaje (brzeg wydmowy według pola 79,2% REAL, 80,0% GAMEPLAY). Niski brzeg D5 leży na poziomie den dolin, więc na ok. 15% brzegów wydmowych dno doliny rzeki (do 800 m szerokie) zabierało pas wydm i łęg sięgał plaży. Teraz w pasie wydm brzegu wydmowego (B + D + 170 m·k; koniec pasa szarej wydmy drga o ±0,6 · 170 m·k szumem o fali 300 m·k, `GRAY_DUNE_JITTER`, tak samo na dnie i poza nim) wydmy biegną przez dno doliny; strefy rzeki zostają tylko w ujściu, do max(30 m·k, 1,5 W) od koryta (±20%; `Column.duneOverFloor`, mady → piasek). Za pasem wydm dno zostaje strefom cieku, a progi wysokości zaplecza zalewu (torfowisko h ≤ 0,4 m, ols mierzei h ≤ 1 m) nie działają na dnach dolin. Runda 1 recenzji usunęła rozszerzenie na zaplecze do granicy boru bażynowego przy ciekach nie klasy A: jego bramka `wCoastland` > 0,5 cięła dna prostą linią równoległą do brzegu (cD ok. 1370 m w REAL, 690 m w GAMEPLAY), a progi zaplecza leżały dokładnie na zacisku terenu brzegu (dno 2,000 m, h = 1,0 m), co dawało proste równoległoboki boru bażynowego na dnach, w GAMEPLAY plamy boru bażynowego < 64 m na dnach olsu i cienkie linie torfowiska; bez drgania kończył się też prostą linią łęg na dnie przy cD = 450 m. `CoastTest.duneShoreShareAtBothScales` (wszystkie punkty brzegu siatki testu): brzeg wydmowy (biała wydma, bez klifu) REAL 64,5% → 77,8%, GAMEPLAY 63,4% → 79,7%; brzeg wysoki z klifem 17,9% i 14,5%; ani to, ani to (ujścia) 4,3% i 5,8%; wymóg 75–85%.
- **Masywy i piętra (§5 projektu poprawki).** Progi pięter bez zmian. Kosodrzewina i hala na prawdziwym terenie w obu skalach (`greatMassifHasDwarfPineAndAlpine`): REAL masyw najbliższy (0, 0) 8,86 km² kosodrzewiny i 0,51 km² hali, najwyższy (1723 m) 7,64 i 2,13 km²; GAMEPLAY najbliższy 0,75 i 0,010 km², najwyższy (1719 m) 0,77 i 0,153 km². Regiel górny 1200–1350 m: świerczyna 94,6%; regiel dolny 600–1100 m: buczyna, jedlina i olszyna 96,7% (REAL) i 96,3% (GAMEPLAY); granica świerczyny S 1191 m, N 1105 m. Nowy kadr `gameplay_highest_great_massif_5km` (hala 0,6% kadru), a `high_beskids_10km` trafia teraz na masyw 1668 m z kosodrzewiną i halą.
- **F1/F2 a testy.** `WatersideZonesTest` (końcowy przebieg po rundzie 1 recenzji): przekroje uporządkowane REAL A 145, B 148, C 149 ze 150, GAMEPLAY 150, 150, 150; OlJ ≥ 6 bloków w 97,6% pasów (REAL, 164) i 98,7% (GAMEPLAY, 157); ols ≥ 10 bloków w 74,2% cięciw (REAL, 163 cięciwy) i 54,3% (GAMEPLAY, 210; wymóg ≥ 50%, w pierwszych 150 przekrojach 62,8%); F2 REAL 100% (całe dno 99,1%), GAMEPLAY 100% (305 kolumn), całe dno 93,1%, z równiną przybrzeżną 90,6%. `riparianOnlyOnFloorsSpringsAndSeeps` liczy wysokość nad wodą od niższego z poziomów (najbliższe koryto, miękki poziom), bo od miękkiego poziomu mierzy klasyfikator; łęgi poza flagą dna leżą najwyżej 4,0 m nad wodą (S4: 5,0 m). Warunek u < 0,35 dla łęgu topolowego (Odstępstwo S4) sprawdzony i nieprzywrócony: u jest teraz ciągłe, ale łęg topolowy wyznacza już d ≤ D_top, a drugi próg dałby drugą granicę równoległą do brzegu dna.
- **Koszt.** Klasyfikacja 0,11–0,15 µs na kolumnę (sam test; w pełnym zestawie obok innych procesów 0,2–0,3 µs; test czasu powtarza rundy, aż jedna zmieści się w limicie, najwyżej 60). `sample`: miękki poziom koryta kosztował ok. 1% w GAMEPLAY (cały obszar 1,20–1,22 × M1, budżet D1 1,20), więc zgodnie z D1 dołożyliśmy oszczędność w `RiverNetwork`: segment wylotowy i jezioro bezodpływowe węzła siatki w jednej bezblokadowej pamięci `DirectCache` (2^18 miejsc, klucz bez pudełkowania) zamiast 3–4 odczytów map z kluczami `Long` na każdy z ok. 500–675 węzłów listy kandydatów kafla. `costTest -PcostRuns=15` (A/B w jednej sesji): bez oszczędności GAMEPLAY cały obszar 1,198 i 1,214, Beskidy 1,171 i 1,180, REAL cały obszar 1,172 i 1,197, Beskidy 1,131 i 1,143; z nią GAMEPLAY cały obszar 1,179 i 1,186, Beskidy 1,161 i 1,162, wielki masyw 1,13, REAL cały obszar 1,107 i 1,096, Beskidy 1,10–1,11, wielki masyw 0,98–0,99. Budżet D1 jest spełniony we wszystkich obszarach. Spadek w REAL jest częściowo cechą pomiaru (400 rozrzuconych chunków mieści się teraz w pamięci; przy 16 384 miejscach REAL był o ok. 1% wolniejszy), w grze sąsiednie chunki i tak dzielą węzły.
- **Zostaje (M5 i faza 2):** płaty biomów o prostych krawędziach tam, gdzie prosty jest sam teren lub podłoże modelu: głowica doliny potoku na sandrze jako czworokąt mady (`gameplay_large_river_valley_1km`), proste zbocza dolin w Beskidach (granice pięter i ekspozycji idą po poziomicach płaskich ścian) i dna dolin na zacisku terenu brzegu: dno ścięte do 2,000 m leży mniej niż 1,15 m nad rzeką (ok. 1 m), więc dostaje siedlisko strefowe (bór wilgotny, w krainach piasków) zamiast łęgu, a granica idzie prostą tam, gdzie dno wychodzi ponad zacisk (wybrzeże REAL, np. (−172000, −221050); tak samo w bazie `49aca9c`). Wewnętrzna krawędź pasa olsu zaplecza zalewu poza dnami i granice boru bażynowego z borem mieszanym na zmianie typu próbki (piasek pasa nadmorskiego / glina wysoczyzny) są też proste i pochodzą sprzed etapu H. Na wybrzeżu GAMEPLAY przy (−3268, −5357) zostaje prostokątny płat dna łęgu wiązowego z przełączenia doliny dominującej F1 między rzekami 4,8 i 2,9 m (także w bazie). Końce wydm przy dnach dolin w samym terenie: M5.

### 3.5 `PolandBiomeSource` (przepisany)

- **Kodek:** `RecordCodecBuilder.mapCodec(i -> i.group(RegistryOps.retrieveGetter(Registries.BIOME)).apply(...))`. Holdery bierzemy z `getter.getOrThrow(HabitatBiome.key())` w kolejności enuma (wzór: `TheEndBiomeSource`).
  - Presety skracają się do `"biome_source": {"type": "polishforests:poland"}`.
  - Stare pola M1 w `level.dat` są ignorowane, bo `MapCodec` pomija nieznane klucze.
- **`collectPossibleBiomes()`:** wszystkie 36, stałe od dekodowania, niezależne od trybu i ziarna.
- **Stan:** `volatile Binding` (model, klasyfikator z `PolandSettings`, `VerticalScale`). `bind` dostaje teraz ustawienia. Wiązanie zostaje w `PCG.createState`, bo to jest przed `ServerLevel.uncachedBiomeResolver`.
- **`createResolverForChunk`:** 16 holderów z góry (16 × próbka + klasyfikacja). Resolver zwraca `cols[ix*4+iz]` i ignoruje qy.
- **`createResolver`:** czyta `binding` wewnątrz lambdy, co usuwa błąd „plains na zawsze”. Do tego bufor wątku: 256 wpisów mapowanych bezpośrednio po (qx, qz), bo struktury pytają tę samą kolumnę na kilku Y.
- **Nadpisany `findClosestBiome3d`:** jedna próbka na kolumnę zamiast ok. 33 poziomów Y. Dla promienia 6,4 km to ok. 1 s zamiast ok. 30 s zawieszenia serwera.
- **`addDebugInfo` i F3:** biom, STL, strefa, zespół, gleba, DGW, O, P, T(pos).
- **`PolandPresetEditor`:** tworzy nową instancję źródła dla nowego generatora. `bind` loguje ostrzeżenie przy ponownym wiązaniu z innymi parametrami.

### 3.6 Budżet kosztu (jeden wątek)

| Pomiar | M1 | Cel M2 |
|---|---|---|
| `sample` | 5–10 µs | +≤ 5% |
| klasyfikacja | – | ≤ 0,5 µs |
| etap BIOMES | 16 próbek i ok. 8400 wywołań `biomeFor` | ≤ 0,2 ms na chunk |
| TERRAIN | 1,3–2,6 ms model + 1,5–3 ms wypełnianie | ≤ 1,10 × M1 (z półką i mikroreliefem) |
| FEATURES | biomy zastępcze | ≤ 1,0 × M1 (cel −20%) |
| cały chunk | 44 ms | ≤ 48 ms |
| `PACK_FALLBACKS` / `HABITAT_MISS` | – | < 1% sekcji / < 0,5% chunków |

**Wynik budżetu `sample` po poprawce geometrii terenu (decyzja D1 z wdrożenia poprawki, 2026-10-03).** Cel +≤ 5% zastąpiła decyzja D1: do 1,20 × kopia M1 (bezwzględnie REAL cały obszar ≤ 6,5 µs, REAL Beskidy ≤ 12 µs, GAMEPLAY cały obszar ≤ 8,5 µs, GAMEPLAY Beskidy ≤ 16 µs). Stan po K6 (`costTest -PcostRuns=15`, spokojna maszyna, mediana): REAL cały obszar 1,16 (4,5 µs), REAL Beskidy 1,13–1,14 (7,2 µs), GAMEPLAY cały obszar 1,19 (6,4 µs), GAMEPLAY Beskidy 1,16–1,17 (11,1 µs); budżet dotrzymany bez zapasu (`docs/m2/poprawka-geometrii.md`, K6 i podsumowanie).

**Odstępstwo S0:** czasów etapów chunka (`profileStages`) w S0 nie zmierzyliśmy, bo w krokach nie uruchamiamy gry. Baza musi pochodzić z kodu modelu M1, więc nie zastąpi jej test w grze na końcu fazy 1 (wtedy model zawiera już S2 i S3). Trzeba uruchomić `./gradlew runClientGameTest -Pgametest=stages` przed S2, dopóki `src/main` jest równe M1. Po S2 można to zrobić z migawki `migawki/m1-z-narzedziami-S0.tar` (SHA-256 i instrukcja w `migawki/README.md`; kod migawki ma dawne nazwy, więc tryb nazywa się tam `etapy`), w tej samej sesji co pomiar bieżącego kodu. Tak samo trzeba zmierzyć bazę chunków na sekundę z C2ME dla S10, której nie zmierzono wcale. Tryb `stages` loguje przyrost liczników modelu i wypełniania w czasie pomiaru. Wcześniej logował sumy narastające z obu światów, co było błędem. Mierzy trzy obszary na skalę: nizinę z M1, wnętrze Beskidów i dużą rzekę. Budżet `sample` rozstrzyga `SampleCostTest`: mierzy na przemian obecny model i zamrożoną kopię M1 (`src/test/.../landscape/m1/`, nie zmieniać) w jednej JVM i podaje stosunek obecny/M1. Na identycznym kodzie stosunek z minimów mieści się w ok. ±4%, a mediana stosunków w ok. ±10%. Złoty test obejmuje też zestaw z suwakiem regionów 0,5, bloki obu odwzorowań pionowych i cele łat (plaża z `BEACH_SAND`, klif, ujście, torf, szczyt). Przy różnicy porównuje wartości z kopią M1. W skali rozgrywki Beskidy prawie nie przekraczają 1150 m (ważne dla `AltitudinalBelts`, S4). Szczegóły: `docs/m2/pomiary-bazowe-m1.md`.

---

## 4. Strefy nadwodne

**Klasy cieków.** `Wr = W / chan` to szerokość w skali 1:1.

| Klasa | Warunek |
|---|---|
| A, duża rzeka nizinna | wN ≥ 0,5 i (rząd 3 lub Wr ≥ 30), spadek ≤ 3‰ |
| B, mała rzeka nizinna | wN ≥ 0,5, Wr < 30, spadek ≤ 3‰ |
| C, potok górski | wP + wB > 0,5 lub spadek > 3‰ |

**Wspólne zasady:**
- Minima w blokach (E11): szuwar 2, wiklina 3, OlJ 6, ols 10.
- Granice drgają: ±20% szerokości, ±0,05 u, szum o fali 30–60·k m.
- Priorytet: woda → łacha i kamieniec → wiklina → okrajek → łęgi → zastoiska → strefowe.
- **η nie używamy.** Dno doliny jest dziś płaskie (lustro + 1,2–2,2 m), więc strefy wyznaczamy z d, u, W, rzędu i spadku. Gdy M5 doda wały i terasy, dojdzie warunek η bez zmiany struktury reguł.

### 4.1 Klasa A (przykład W = 150 m)

Oznaczenia: D_wb = clamp(1,7W; 30k; 300k), D_top = clamp(3,3W; 80k; 500k).

| # | Strefa / biom | Warunek | Gleba | Roślinność (M2) |
|---|---|---|---|---|
| 0 | KORYTO / `river` | `waterKind == RIVER` (Odstępstwo S2: d ≤ 0 bywa też bez wody, przy źródle i w suchej głowicy doliny) | piasek; pas 1–2 bloków przy brzegu: `mud` | rdestnice (`seagrass`), pałka w wodzie 1-blokowej |
| 1 | ŁACHA / `willow_scrub` | brzeg wypukły, d < 0,5W, szum | piasek z łatami błota, półka | siewki wierzb, rzadko |
| 2 | WIKLINA / `willow_scrub` (biom, gdy pas ≥ 12 m) | d ≤ max(3 bl; 8k; 0,3W), na brzegu wypukłym do max(15k; 1,0W) | MADA_LEKKA, półka 1–2 bloki | `shrub/osier` 80–100%, trzcina na półce, `firefly_bush` |
| 3 | OKRAJEK (ziołorośla) | pas max(2 bl; 4k; 0,05W) **za wikliną** oraz luki w łęgu (szum < −0,5) | `rooted_dirt`, błoto | `tall_grass`, `large_fern`, `bush` 80% |
| 4 | `willow_poplar_forest`, zespół Salicetum albae | d ≤ D_wb | MADA_LEKKA | wierzba 70%, topola 30% |
| 5 | `willow_poplar_forest`, zespół Populetum albae | D_wb < d ≤ D_top i u < 0,35; preferencja wyp > 0 | MADA_LEKKA | topola 60%, wierzba 40% |
| 6 | `elm_ash_forest` | reszta dna | MADA_CIEZKA | dąb, jesion, wiąz, geofity |
| 7 | zastoiska: `alder_carr` 60% / `fen` 40% (gdy półszerokość dna > 300k i spadek < 0,5‰) | u > 0,6 i wyp < −0,3 (20–30% powierzchni) | TORF_NISKI, mikrorelief | olsza na kępach |
| 8 | pierścień starorzecza | s ≤ max(2 bl; 15k): SZUWAR_LADOWY; ≤ 35k: ŁOZOWISKO; dalej `alder_carr` przy u > 0,5, inaczej łęg według d | jak strefy | trzcina, wiklina, olsza |
| 9 | zbocze doliny (poza dnem, valleyWeight > 0) | wysięki u podnóża (DGW ≤ 0,5): `ash_alder_forest`; dalej strefowe według eksp i O (grąd zboczowy, świetlista dąbrowa na S, buczyna przy O ≥ 0,6, bory na piasku) | strefowa | strefowa |

### 4.2 Klasa B (przykład W = 6 m)

| # | Strefa / biom | Warunek |
|---|---|---|
| 1 | ZIOLOROSLA (brzeg) | 0 < d ≤ max(2 bl; 1,5k; 0,5W); półka |
| 2 | WIERZBY | Wr ≥ 15, podłoże piaszczyste, d ≤ 20k |
| 3 | `ash_alder_forest` | wDnie, d ≤ min(80k; max(15k; 4W)); na SANDR 5–25k |
| 4 | `alder_carr` (+ ŁOZOWISKO 5–30k na skraju) | półszerokość dna > 60k, d > max(20k; 4W), (wyp < −0,5 lub PEAT lub u > 0,5 przy szumie) |
| 5 | `ash_alder_forest`, zespół źródliskowy | ZRODLO, promień 10–40k |
| 6 | skraj dna i zbocze | glina: grąd niski, LMw; piasek: `moist_pine_forest` 20–100k, potem `fresh_pine_forest` |

### 4.3 Klasa C

| # | Strefa / biom | Warunek | Uwagi |
|---|---|---|---|
| 1 | KAMIENIEC / `willow_scrub` | brzeg wypukły, Wr ≥ 6, d ≤ W, H 300–1000 (Odstępstwo S2: `convexBank` liczony też na słabo krętych odcinkach, gdy Wr ≥ 6) | żwir. Tag `supports_big_dripleaf` nie obejmuje żwiru, więc lepiężnik rośnie na łatach `coarse_dirt`/`rooted_dirt` na skraju kamieńca |
| 2 | WIKLINA (górska) | Wr ≥ 4, d ≤ max(3 bl; W), H ≤ 900 | wiklina |
| 3 | `gray_alder_forest` | wDnie, d ≤ min(60k; max(10k; 3W)), H ≤ 1000 (stoki N 900); przy półszerokości dna < 30k całe dno | żwir, `coarse_dirt`, ziołorośla, lepiężnik |
| 4 | ZIOLOROSLA_GORSKIE | H > 1000 lub półszerokość dna < 10k; d ≤ max(1 bl; 0,5W) | las strefowy do brzegu |
| 5 | `ash_alder_forest`, zespół Carici remotae-Fraxinetum | rząd 1, H < 700, P ≥ 0,4, wąskie dno z wysiękiem | |
| 6 | `gray_alder_forest`, zespół młaka | ZRODLO lub stok z DGW ≤ 0,3, H 400–1100, promień 10–50k | błoto, mursz |
| 7 | `montane_beech_forest`, zespół jaworzyna | dolne 20–80k zbocza, nach > 30°, eksp N–E | rumosz |

### 4.4 Jeziora, oczka, starorzecza, zalew

| Pas | Warunek | Biom / strefa |
|---|---|---|
| głębia | z > 5 (w starorzeczu > 3) | `lake` |
| elodeidy | z 1,5–5 | `lake`, strefa ELODEIDY (`seagrass`) |
| nymfeidy | z 0,8–3; w starorzeczach 60–90% lustra, w dużych jeziorach zatoki (szum 30%) | strefa NYMFEIDY (`lily_pad`) |
| szuwar wodny | z ≤ 1,5, woda eutroficzna | `reedbed` (pas ≥ 12 m) lub strefa SZUWAR |
| szuwar lądowy | s ≤ max(2 bl; 10k), h ≤ 0,3 | strefa SZUWAR_LADOWY, półka |
| łozowisko | s ≤ 30k, h ≤ 0,8, torf lub muł | strefa ŁOZOWISKO |
| ols | s ≤ 150k (oczka ≤ 40k), h ≤ 1,0, nach < 3°, podłoże TILL, PEAT lub LAKE_MUD (Odstępstwo S2: `waters.s` jezior sięga 150k, oczek 45 m, starorzeczy 40k; dalej od starorzecza ols wyznaczają d i u, §4.1 nr 8) | `alder_carr` |
| zaplecze | wysięki u zbocza rynny: OlJ; dalej strefowe | – |

Warianty szczególne:
- **Jezioro lobeliowe** (SANDR, bez torfu): szuwar w płatach 2–5k (20% brzegu), strefa OLSZA_BRZEG 1–5k, dalej bory według DGW.
- **Jezioro dystroficzne:** strefa PLO 1–20k (torfowiec na poziomie lustra nad wodą), potem `raised_bog`, `bog_woodland` i `moist_pine_forest`.
- **Zalew:**

| Warunek | Biom / strefa |
|---|---|
| z ≤ 1,5 | `reedbed` |
| brzeg od lądu, h ≤ 0,4 | `fen` (D: `wet_meadow`) |
| h 0,2–1 na torfie | `alder_carr` |
| mierzeja | `alder_carr` w obniżeniach, potem `coastal_pine_forest` |

### 4.5 Tryb D nad wodą

- Strefy WIKLINA, OKRAJEK, ZIOLOROSLA, KAMIENIEC, SZUWAR i ŁOZOWISKO zostają zawsze.
- Dochodzi strefa SZPALER: 1–3 olsze, 3–10k przy każdym cieku klasy B.
- Duża rzeka:
  - łęg wierzbowy ma P_las 0,45, a poza lasem jest `wet_meadow`;
  - reszta dna ma P_las 0,10 (łąka wilgotna 80%, pole 20%).
- Ols w zastoiskach ma P_las 0,35.
- Potok: w dolinach szerszych niż 80k poniżej 900 m są łąki, a pas olszy szarej 5–20k zostaje.

### 4.6 Gęstość (twarda reguła prośby użytkownika)

W strefach WIKLINA, OKRAJEK, ZIOLOROSLA, SZUWAR_*, ŁOZOWISKO i KAMIENIEC oraz w łęgach i olsach:
- krótka trawa i małe kwiaty zajmują ≤ 10% kolumn lądowych;
- rośliny wysokie, krzewy, trzcina, pałka i drzewa zajmują ≥ 60% kolumn;
- goły grunt zajmuje ≤ 25% (poza łachą i kamieńcem);
- runo łęgów i olsów pokrywa 80–100%.

Sprawdza to gametest (§12.3).

---

## 5. Piętra górskie i wybrzeże

### 5.1 Beskidy i Pogórze (klasa `habitat/AltitudinalBelts.java`)

`AltitudinalBelts` zastępuje trzy kopie progu 1150 m: `PolandBiomeSource:26`, `LandscapeModel:750`, `PolishForestsCommands:70-72`.

```
dT   = 40·szum(λ 150k)                                   // m
eks  = nach ≥ 5° ? 50·cos(eksp − 180°) : 0               // stok S +50 m, stok N −50 m
regielDolny = 550  + eks + dT
regielGorny = 1150 + eks + dT
granicaLasu = 1390 + eks − 60·[GRZBIET i nach < 15°] + dT
progHali    = 1650 + eks + dT
duzyMasyw   = szczyt ≥ 1470   // najwyższy teren w promieniu 3 km·mspace (pole terrain.summit, PeakField; Odstępstwo S4)

H ≥ progHali    i duzyMasyw  -> alpine_grassland
H ≥ granicaLasu i duzyMasyw  -> dwarf_pine_scrub (ostatnie 60 m pod granicą: strefa GRANICA_LASU, karłowe świerki)
H ≥ regielGorny              -> montane_spruce_forest (bez dużego masywu las sięga do wierzchołka)
H ≥ regielDolny              -> montane_beech_forest, chyba że Abieti-Piceetum -> montane_spruce_forest:
                                (GRZBIET i trofia ≤ BM) lub (eks < 0 i H > 900) lub (wDnie i H > 700 i polSzerDna > 30k)
POGÓRZE, stok N, H > 450     -> montane_beech_forest
niżej                        -> trofia L i H < 400 -> oak_hornbeam_forest; P ≥ 0,5 -> upland_fir_forest; inaczej strefowe
```

- Zespół jaworzyna (nach > 30°, N–E) i ziołorośla w żlebach (grzbiet < 0,15 w reglu górnym) to zespoły w kodzie kolumny, nie biomy.
- Model Beskidów sięga ok. 1725 m, więc kosodrzewina i hala pojawią się tylko na najwyższych masywach (typ Babiej Góry i Pilska).
  - **Odstępstwo S4 (zamknięte w K2 poprawki geometrii, 2026-10-03):** teren M1 miał szczyty niższe: w REAL 1443 m (ziarno 20260927) do 1645 m (ziarno 4), w GAMEPLAY do ok. 1250 m, więc kosodrzewina i hala były tylko w REAL dla części ziaren. Wielkie masywy (M2-8) mają szczyty 1632–1723 m (REAL) i 1618–1719 m (GAMEPLAY), a test `dwarfPineOnlyOnLargeMassif` pilnuje, że kosodrzewina i hala są tylko przy szczycie ponad 1470 m (`docs/m2/poprawka-geometrii.md`, K2).
- Profile Tatr, Karkonoszy i Bieszczad dochodzą w M5 jako kolejne `BeltProfile`.

### 5.2 Wybrzeże (długości × k)

| Pas | Warunek | Biom · strefa/zespół |
|---|---|---|
| plaża mokra i sucha | cD < 0,35B / cD < B | `beach` · KIDZINA na 0,35B–B |
| podnóże klifu | cD < B, KLIF w sąsiedztwie | `beach`, zespół kamienisty (żwir) |
| wydma inicjalna i biała | B ≤ cD, BEACH_SAND | `white_dune` · WYDMA_INICJALNA do B+20k |
| wydma szara | SAND, cD < B+D+170k | `gray_dune` |
| bór bażynowy wiatrowy | cD < B+D+420k | `coastal_pine_forest`, zespół karłowy; mnożnik wysokości drzew dla M3: 0,4 + 0,6·smoothstep(0; 1500k; cD) |
| bór bażynowy typowy | cD < 2000k, SAND, H < 40; obniżenia przy DGW ≤ 0,5 | `coastal_pine_forest` / `bog_woodland` / `alder_carr` |
| ściana klifu | KLIF (surface > 8, raw > 8, cD < B + raw/2,5 + 20k), tylko brzeg wysoki (`lowShore` < `LOW_SHORE` = 0,5; `lowShore` = 1 − udział klifu z `shapeCoast`, D5 w K5b; Odstępstwo S4) | biom wysoczyzny · KLIF_SCIANA (goła glina) |
| korona klifu | do 20k za krawędzią | jw. · KLIF_KORONA (zarośla, bez drzew) |
| las wiatrowy | 20–150k za krawędzią | buczyna (O ≥ 0,6), grąd lub las mieszany, zespół wiatrowy |

---

## 6. Temperatura, śnieg i Serene Seasons

### 6.1 Model (S0)

Przeliczenie z raportu klimatu: **T = 0,20 + 0,05·t[°C]**, czyli próg śniegu 0,15 odpowiada −1 °C średniej rocznej. Gradient wynosi 0,55 °C/100 m, czyli **Γ = 0,000275 na metr**.

```
T(pos) = T_bazowa(biom) − Γ·max(0, m(pos)) + 0,011·n(x, z)    // m = vertical.metersAboveSea(y), n ∈ [−1, 1], λ 150 m (ok. ±40 m)
```

`T_bazowa` to wartość z JSON biomu, sprowadzona do poziomu morza. Wszystkie wartości są ≤ 0,8, żeby działała bramka SS.

| Grupa | T_bazowa |
|---|---|
| niziny, góry, wody śródlądowe | 0,70 |
| łęgi | 0,685 |
| `alder_carr`, `bog_woodland`, `fen`, `reedbed` | 0,67 |
| `raised_bog` | 0,65 |
| `sea`, `lagoon`, `beach`, wydmy, `coastal_pine_forest` | 0,72 |

Skutek przy T_bazowa = 0,70 jest taki sam w obu skalach, bo liczymy w metrach:

| Okres | Śnieg od |
|---|---|
| bez SS oraz SS IV–X | ok. 2000 m (Babia Góra bez śniegu latem) |
| SS III i XI (−0,25) | ok. 1090 m |
| SS XII–II (−0,8) | wszędzie |

Kontrola: szczyt Babiej Góry (1725 m) ma T ≈ 0,226, czyli ok. 0,5 °C. Wartość 0,27 z raportu klimatu dotyczy Śnieżki z inną temperaturą bazową, więc do testów jej nie bierzemy.

**Kontynentalizm.** Pole O nie wchodzi do temperatury w M2. Mixin ma miejsce na człon regionalny (domyślnie 0). W M4 człon wejdzie razem z S2. Klientowi wyślemy wtedy siatkę t0, a nie ziarno, bo `Noise.derive` jest odwracalne i zdradziłoby ziarno świata.

### 6.2 Mixiny (wspólne dla obu stron)

Nowy plik `src/main/resources/polishforests.mixins.json`, wpis w `fabric.mod.json` bez ograniczenia do klienta. Klient też liczy opad.

- **`BiomeClimateMixin`:** interfejs duck `BiomeClimateAccess` z polem `volatile BiomeClimate polishforests$climate` (VerticalScale, T_bazowa, tryb zamarzania).
- **`BiomeTemperatureMixin`:** `@Inject(method = "getHeightAdjustedTemperature(Lnet/minecraft/core/BlockPos;I)F", at = @At("HEAD"), cancellable = true, require = 1)`.
  - Przy `climate != null` zwraca `PolandClimate.temperature(...)`. Bez profilu działa wanilia, czyli w Netherze, Endzie i światach wanilijnych.
  - Bufor `getTemperature` zostaje. SS woła `getTemperature`, więc przejmuje wynik bez mixinu w SS.
  - Nie ruszamy `warmEnoughToRain`, `coldEnoughToSnow` ani `getBaseTemperature` (bramka SS).
- **`BiomeFreezeMixin`:** HEAD w `shouldFreeze(LevelReader, BlockPos, boolean)`, tylko dla biomów z profilem:
  - `sea` nigdy nie zamarza;
  - `river` i `stream` zamarzają dopiero przy T < 0,05;
  - reszta jak w wanilii.
  
  To jest zgodne z `@Redirect` SS wewnątrz metody, bo przerywamy tylko w naszych przypadkach.
- **Wiązanie profilu:**
  - `CommonLifecycleEvents.TAGS_LOADED` (obie strony): profil dla biomów z tagu `#polishforests:polish_climate`. W kroku S1 tag obejmuje 12 biomów zastępczych z T_bazowa = 0,70 (ocean 0,72, nigdy nie zamarza). Od S5 obejmuje 36 biomów moda.
  - Serwer: skala z `PCG.createState` (przed generacją) oraz `ServerLevelEvents.LOAD` dla overworldu z `PolandChunkGenerator`.
  - Klient (tylko serwer zdalny): `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE`. Typ wymiaru `polishforests:poland` daje REAL, `poland_gameplay` daje GAMEPLAY, awaryjnie wysokość 2096 lub 832. Do tego poprawiona `PolandDimension.isPoland`.
  - Obiekty `Biome` powstają od nowa przy każdym świecie, więc stan nie przecieka.
- **Koszt:** przy chybieniu bufora odczyt pola, `metersAboveSea` i 1 szum, czyli < 0,1 µs.

**Stan S1 (2026-10-02):** mixiny, `PolandClimate`, `BiomeClimate`, `BiomeClimateAccess`, `ClimateBinding` i `ClientClimate` są gotowe. `PolandClimateTest` daje latem śnieg od 1964–2036 m (REAL) i 1965–2036 m (GAMEPLAY), przy −0,25 próg 1055–1129 m, a koszt `PolandClimate.temperature` to ok. 18 ns (REAL) i 36 ns (GAMEPLAY). Weryfikacja w grze czeka na test na końcu fazy. Tryb `-Pgametest=climate` robi ją w obu skalach (dwa światy, REAL i `poland_gameplay`):
  - latem brak śniegu na sandrze i w Beskidach poniżej 1900 m, wokół najwyższego miejsca w promieniu ok. 60 km od pierwszych Beskidów na spirali. Dla ziarna testu to ok. 1311 m (REAL) i 1125 m (GAMEPLAY), a test wymaga co najmniej 1200 i 1000 m. Wyżej sprawdza punkty w powietrzu na 1500 i 1850 m;
  - z SS (`localRuntime`) latem `season set mid_summer`: temperatura z porą roku przez hak SS, a na blokach `Biome.shouldSnow` (hak SS na początku metody) i brak śniegu z generacji;
  - z SS zimą `season set mid_winter`: śnieg na sandrze i wysoczyźnie morenowej (co najmniej 100 kolumn spoza czarnej listy SS) i `shouldSnow` na blokach;
  - biomy Netheru i Endu bez profili;
  - opad klienta zgodny z serwerem w 20 punktach i skala klienta równa skali serwera;
  - tryby zamarzania na wodzie morza z chwilowo podmienionym profilem: przy T ok. 0,10 zamarza tylko `VANILLA`, przy T ok. −1 `RIVER` i `VANILLA`, nigdy `NEVER`.

`MixinTargetsTest` sprawdza w testach jednostkowych, że cele `@Inject` obu mixinów istnieją w `Biome`. Unit testy działają bez mixinów, więc samo działanie mixinów sprawdza dopiero test w grze.

**Odstępstwa S1:**
- Tag `#polishforests:polish_climate` ma 10 biomów, nie 12. Preset ma 12 pól, ale `forest` i `river` występują w nim dwa razy. `PolandClimateTest` pilnuje, żeby tag i oba presety miały ten sam zbiór.
- `TAGS_LOADED` nie może przypiąć profilu przy pierwszym wczytaniu, bo skala świata nie jest jeszcze znana (tagi wczytują się przed poziomami). Pierwsze przypięcie robią `PCG.createState` (biomy źródła z tagu), `ServerLevelEvents.LOAD` i u klienta `AFTER_CLIENT_LEVEL_CHANGE`. `TAGS_LOADED` (także `/reload`) wylicza profile od nowa ze skalą wziętą z już przypiętych profili. Logika jest w nowej klasie `climate/ClimateBinding` i nie ma stanu statycznego. W grze jednoosobowej klient dzieli obiekty `Biome` z serwerem zintegrowanym (`ClientConfigurationPacketListenerImpl.handleConfigurationFinished`), więc nic nie przypina. Inaczej rozpoznanie skali po wysokości mogłoby zmienić temperatury serwera w świecie bez generatora „Polska”. Przy serwerze zdalnym klient przy zmianie wymiaru tylko przypina profile (`attachWithoutDetaching`). Profile zdejmuje wyłącznie przeliczenie po `TAGS_LOADED`, według zawartości tagu.
- Rzeka zamarza przy T < 0,05 liczonym **z korektą pory roku**. Bez niej rzeki nie zamarzałyby zimą na nizinach. Korektę daje nowa metoda `SeasonProvider.temperatureInSeason`. Z SS woła hak `SeasonHooks.getBiomeTemperature`, który nie należy do API SS, więc przy błędzie łączenia wraca do temperatury bez pory roku. Bez SS korekty nie ma (do S2 w M4).
- Zastępczy `minecraft:river` jest na czarnej liście SS i obsługuje też jeziora. Do S5 rzeki i jeziora zimą nie zamarzają (próg 0,05 bez korekty to ok. 2360 m), a nad zastępczymi `river`, `beach` i `ocean` zimą pada deszcz. Zniknie to razem z biomami moda.
- `PolandDimension.isPoland` rozpoznaje obie ramy. Nowe są `PolandDimension.scaleOf` (rozpoznanie awaryjne po wysokości, tylko overworld, z ostrzeżeniem w logu) i `PolandScale.byDimensionType`.
- Test w grze dostał tryb `climate` (także w `all`, po pomiarze generacji i zrzutach, żeby teleport do Beskidów nie zawyżał ms/chunk). W grze jednoosobowej klient dzieli obiekty biomów z serwerem, więc porównanie opadu w 20 punktach sprawdza głównie rozpoznanie skali u klienta. Pełna kontrola klienta wymaga serwera zdalnego.
- Kryterium „latem brak śniegu na 1500 m” na prawdziwym gruncie jest niesprawdzalne dla ziarna testu: Beskidy w promieniu ok. 60 km od pierwszego miejsca na spirali sięgają ok. 1311 m (REAL) i 1125 m (GAMEPLAY). Spirala z warunkiem wysokości jest za droga (w REAL pierwsze miejsce powyżej 1150 m leży ok. 1000 km od środka, a już sama spirala do pierwszych Beskidów trwa ok. 3 min). Test sprawdza więc grunt do najwyższego znalezionego miejsca (co najmniej 1200 i 1000 m), a 1500 i 1850 m w punktach w powietrzu nad Beskidami.

### 6.3 Serene Seasons

- Tagi z datagenu w `data/sereneseasons/tags/worldgen/biome/`. Bez SS są nieszkodliwe.
  - `lesser_color_change_biomes` (25% zmiany): `dry_pine_forest`, `fresh_pine_forest`, `coastal_pine_forest`, `moist_pine_forest`, `bog_woodland`, `montane_spruce_forest`, `dwarf_pine_scrub`, `raised_bog`, `gray_dune`.
  - `blacklisted_biomes`, `tropical_biomes`, `infertile_biomes`: nic nie dodajemy. Wody nie trafiają na czarną listę, bo zimą padałby nad nimi deszcz.
- Preset używa klucza `minecraft:overworld`, który jest na białej liście SS.
- Przy generacji (`WorldGenRegion`) SS nie zna pory roku. Chunk wygenerowany zimą jest goły do pierwszego opadu. Akceptujemy to do S2 (M4).
- Kolory trawy, liści, suchych liści i wody są jawne w każdym biomie (enum `HabitatBiome`), więc nie zależą od T_bazowa. `downfall` wpływa tylko na kolory: bory suche 0,4; świeże 0,55; mieszane 0,7; grądy i buczyny 0,8; łęgi, olsy i torfowiska 0,85–0,9; plaże i wydmy 0,4.

---

## 7. Gleby, półka brzegowa, mikrorelief, bloki

### 7.1 Mechanizm w `fill()`

- W pierwszej pętli: `code = classifier(sample)`, potem `Soil`, potem tablice kolumny `topState[]`, `l1State[]`, `l1Depth[]`, `l2State[]`, `coverBlocks[]`, `dy[]` (mikrorelief) i `water[]`. `strata()` tylko porównuje z tablicami.
- **Jednostki:** `coverBlocks = top − vertical.topBlockY(surface − coverDepth)`. Dziś metry porównujemy z blokami, przez co pokrywa w GAMEPLAY jest 2,5–4 razy za gruba. Zmiana ma osobny przełącznik i osobny test, poza złotym testem.
- **`pack()`** obsługuje 5–8 bitów (`PalettedContainer` przyjmuje bity 5–8). Licznik `PACK_FALLBACKS`.
- **`BlockState` pobieramy leniwie** (`SoilBlocks`, `Suppliers.memoize`), bo bloki moda muszą być zarejestrowane przed użyciem. `ModBlocks.register()` jest wołane przed `PolishForestsWorldgen.register()`.
- **Heightmapy:** `fill` ustawia `OCEAN_FLOOR_WG` i `WORLD_SURFACE_WG` po uwzględnieniu półki, kęp i kałuż.
- **Mapa chunka:** `fill` zapisuje `ChunkHabitats` (§8.3).

### 7.2 Półka brzegowa i dno przybrzeżne

To jest warunek konieczny, żeby przy wodzie w ogóle wyrosła trzcina i świetliki:
- `RiverNetwork` ustawia brzeg na lustrze + 1 m (`RN:1044`), a dno koryta na ≤ lustro − 0,3 m.
- W REAL suchy grunt leży więc co najmniej blok nad wodą.
- `SugarCaneBlock.canSurvive` i predykat `patch_firefly_bush_near_water` wymagają wody obok bloku, na którym roślina stoi.

Reguły:
- W strefach ŁACHA, WIKLINA (pierwsze 1–2 bloki od wody), ZIOLOROSLA, SZUWAR_LADOWY i przy brzegach jezior `fill()` obniża wierzch gruntu tak, by górny blok gruntu leżał na tym samym Y co górny blok wody.
- Woda się nie rozleje: stały blok na tym samym Y jest dla niej ścianą, a nad półką jest powietrze.
- W GAMEPLAY (ok. 0,4 bloku na metr) lustro + 1 m często daje ten sam blok co lustro. Wtedy półka nic nie zmienia. Test sprawdza bloki, nie metry.
- **Dno przybrzeżne:** pas 1–2 bloków wody przy brzegu koryt i jezior ma dno z `mud` (w potokach z `gravel`). `small_dripleaf` i własna pałka wymagają dna z `#supports_vegetation` albo z gliny lub mchu. Piasek i żwir tego warunku nie spełniają.

### 7.3 Mikrorelief (szum o fali 3–6 m, deterministyczny)

| Biom / strefa | Kałuże (−1 blok, woda źródłowa) | Kępy (+1 blok) |
|---|---|---|
| `alder_carr` | 20% | 25% (`rooted_dirt`, korzenie) |
| `raised_bog` | 15% | 30% (torfowiec) |
| `fen` | 25% | – |
| `bog_woodland` | 8% | 20% |
| ląd `reedbed`, ŁOZOWISKO | 35% | – |
| `willow_poplar_forest` (namuły) | 5% | – |

- Kałuża powstaje tylko wtedy, gdy 4 sąsiednie kolumny w chunku mają wierzch wyżej. Na skrajnej kolumnie chunka zamiast wody jest błoto.
- Kałuże poszerzają pas, w którym może rosnąć trzcina.
- `hasWater` modelu się nie zmienia.

### 7.4 Gleby

Bloki własne oznaczone *; w nawiasie zastępstwo, gdyby użytkownik ich nie chciał.

| `Soil` (nazwa z planu) | Wierzch | Niżej (bloki) | Gdzie |
|---|---|---|---|
| `INITIAL_PODZOL` (BIELICA_INICJALNA) | `coarse_dirt` 60% / `podzol` 40% | piasek | `dry_pine_forest`, `heath` |
| `PODZOL` (BIELICA) | `podzol` | `coarse_dirt` 1, piasek | `fresh_pine_forest`, `coastal_pine_forest` |
| `RUSTY_SOIL` (RDZAWA) | `podzol` / `grass_block` 50/50 | `dirt` 1, piasek lub glina | `mixed_pine_forest` |
| `SANDY_GLEYSOL` (GLEJOWA_PIASZCZYSTA) | `podzol` + 15% `mud` | piasek, `clay` od 2 | `moist_pine_forest` |
| `BOG_PEAT` (TORF_WYSOKI) | `sphagnum_moss`* (`moss_block`) | `peat`* 2–5 (`mud`), piasek | `raised_bog`, `bog_woodland`, PLO |
| `FEN_PEAT` (TORF_NISKI) | `muck`* (`mud`) + kępy `rooted_dirt` | `peat`* 2 (`mud`), `mud` | `alder_carr`, `fen`, ŁOZOWISKO |
| `ACID_BROWN_SOIL` (BRUNATNA_KWASNA) | `grass_block` / `podzol` | `dirt` 2, glina | `mixed_forest`, `upland_fir_forest` |
| `BROWN_SOIL` (BRUNATNA) | `grass_block` | `rooted_dirt` 1, `dirt` 2 | `oak_hornbeam_forest`, łąki, `arable_land` (ugór) |
| `BEECH_BROWN_SOIL` (BRUNATNA_BUKOWA) | `dirt` / `coarse_dirt` (trawa się nie rozrasta) | `dirt` 2 | buczyny |
| `MUCK` (MURSZ) | `muck`* 40% / `rooted_dirt` + 20% `mud` | `dirt` z `mud` | `ash_alder_forest`, ZRODLISKO |
| `LIGHT_ALLUVIAL_SOIL` (MADA_LEKKA) | `grass_block` / `coarse_dirt` / `sand` + łaty `mud` | piasek i ziemia warstwami | `willow_poplar_forest`, WIKLINA, ŁACHA (piasek z błotem) |
| `HEAVY_ALLUVIAL_SOIL` (MADA_CIEZKA) | `grass_block` | `dirt` 3, `clay` | `elm_ash_forest`, `wet_meadow` |
| `GRAVELLY_ALLUVIAL_SOIL` (MADA_ZWIROWA) | `coarse_dirt` / `grass_block`; KAMIENIEC: `gravel` z łatami `coarse_dirt` | żwir | `gray_alder_forest` |
| `MOUNTAIN_BROWN_SOIL` (GORSKA_BRUNATNA) | `grass_block` / `coarse_dirt` | `coarse_dirt`, flisz | `montane_beech_forest` |
| `MOUNTAIN_PODZOL` (BIELICA_GORSKA) | `podzol` + 20% `moss_block` + 5% `mossy_cobblestone` | `coarse_dirt`, flisz | `montane_spruce_forest` |
| `RANKER` | `coarse_dirt` / `grass_block`; gołoborza `cobblestone`/`andesite` | rumosz | `dwarf_pine_scrub`, `alpine_grassland` |
| `DUNE_SAND` (PIASEK_WYDMY) | `sand` (biała); `sand` 60% / `coarse_dirt` 40% (szara) | piasek | wydmy |
| `BEACH_SAND` (PLAZA) | `sand`; kamienista `gravel` | piasek | `beach` |
| `CHANNEL_BED`, `STREAM_BED`, `LAKE_BED`, `DYSTROPHIC_LAKE_BED`, `SEA_BED`, `LAGOON_BED` (DNO_*) | rzeka: `sand`/`gravel` (pas przybrzeżny `mud`); potok: `gravel`/`cobblestone`; jezioro: `mud`/`clay`; dystroficzne: `peat`*; morze: `sand`, dalej `mud`; zalew: `mud` | – | wody |

**Zasada sadzonek:** wierzch każdej gleby leśnej musi należeć do `#supports_vegetation`, bo drzewa i runo sprawdzają `would_survive <sadzonka>`. Piasek może leżeć dopiero niżej. Wyjątki: kamieniec, łacha, plaża, wydma biała, dna. Na wydmie białej rosną `short_dry_grass` i `tall_dry_grass` (`#supports_dry_vegetation` obejmuje piasek). Pilnuje tego `SoilTest`.

### 7.5 Bloki własne (przesunięte do M4, decyzja M2-D)

W M2 nie rejestrujemy bloków. Tabela poniżej opisuje plan na M4; w M2 w tabeli gleb obowiązują zastępstwa z nawiasów.

| Blok | Tagi |
|---|---|
| `polishforests:peat` (torf wysoki i przejściowy, jasnobrązowy, włóknisty) | `#minecraft:dirt` (daje `substrate_overworld`, `supports_vegetation` i `cannot_replace_below_tree_trunk`), `#minecraft:supports_big_dripleaf`, `#minecraft:mineable/shovel`, `#minecraft:frogs_spawnable_on` |
| `polishforests:muck` (czarny, zmurszały torf niski) | jak `peat` |
| `polishforests:sphagnum_moss` (dywan torfowców jako grunt, czerwono-zielony, bez odcienia) | `#minecraft:moss_blocks`, `#minecraft:supports_big_dripleaf`, `#minecraft:mineable/hoe` |
| opcjonalnie `polishforests:common_reed`, `polishforests:cattail` (rośliny wynurzone, 2 bloki, dolny może być zalany) | `#minecraft:replaceable_by_trees`, `#minecraft:mineable/hoe`; stoją na `#supports_vegetation` lub w wodzie do 1 bloku |

Każdy blok dostaje blockstate, model, teksturę 16×16, loot i lang PL/EN. Tekstury robi proceduralnie skrypt `tools/textures/soils.py`. Ręczne poprawki w Blockbench mogą przyjść później (D5).

**Tryb diagnostyczny:** flaga `-Dpolishforests.debug.habitats=true` maluje wierzch betonem w kolorze strefy lub biomu. Wąskie pasy da się wtedy sprawdzić w grze jednym spojrzeniem.

---

## 8. Dekoracja

### 8.1 Listy kroków (identyczne w każdym biomie, generowane z jednego enuma)

| Krok | Zawartość | Uwagi |
|---|---|---|
| 0 | – | |
| 1 | `polishforests:deep_lava_lake` (`lake_lava`, rzadkość 9, `height_range` −54..0, `environment_scan`) | bez `lake_lava_surface` |
| 2 | `minecraft:amethyst_geode`, `polishforests:glacial_erratics` | głazy: `block_blob` z `granite`/`diorite`/`mossy_cobblestone`, rzadkość 1/6, filtr `polishforests:habitat` (GLACIAL_TILL i piaski młodoglacjalne) |
| 3 | `minecraft:monster_room`, `minecraft:monster_room_deep` | |
| 4–5 | – | struktury wanilii trafiają tu same |
| 6 | rudy w kolejności wanilii (`ore_dirt` … `ore_copper`), potem `disk_sand`, `disk_clay`, `disk_gravel` | bez `underwater_magma` |
| 7 | – | |
| 8 | – | bez `spring_lava` (sięga do `below_top 8`, w REAL do Y 2023, `valid_blocks` obejmują `dirt`) i bez `spring_water` (do Y 192; wodospady na stokach sprzeczne z hydrologią) |
| 9 | `polishforests:tree_stand`, `polishforests:deadwood`, `polishforests:understory`, `polishforests:waterside_zones`, `polishforests:ground_layer`, `polishforests:aquatic_plants`, na końcu jeden z `polishforests:bone_meal/{forest,meadow,wetland,mountain}` | bez `glow_lichen` (104–157 prób na chunk w świecie bez jaskiń) |
| 10 | `minecraft:freeze_top_layer` | |

- `carvers: []`, bo nasz generator ich nie uruchamia.
- **Bezpieczeństwo względem cyklu:**
  - każdy biom ma te same węzły w tej samej kolejności;
  - jedyny element różny, nośnik mączki kostnej (`count 0`, feature kwiatowy z tagu `can_spawn_from_bone_meal`), stoi zawsze na końcu;
  - nigdy nie wpisujemy featurów inline;
  - placed features w paletach nie mają `minecraft:biome` (dla pod-featura `BiomeFilter` rzuca wyjątek).
- **Rezerwy pod M4** nie są potrzebne, bo nowe warstwy to nowe wpisy palet w istniejących dyspozytorach. W 26.3 nie ma zresztą placed featura `minecraft:no_op` (jest tylko typ).

### 8.2 Dyspozytory

Typy rejestrujemy w `BuiltInRegistries.FEATURE_TYPE`. Konfiguracja (palety) jest w JSON z datagenu. Każdy dyspozytor tworzy własne `WorldgenRandom(Xoroshiro)` z (seed świata, chunk, sól warstwy). Logika to czysta funkcja `Plan.of(ChunkHabitats, seed, chunkPos) → List<Placement>`, testowalna bez MC. Feature tylko ją wykonuje. `getSubFeatures()` zwraca featury z palet.

| Feature | Działanie | Koszt na chunk |
|---|---|---|
| `tree_stand` | Losowanie warstwowe: oczko c = 16/⌈√n⌉, jeden kandydat na oczko z przesunięciem w wewnętrznych 50% (rozstaw ≥ c/2), szansa n/(16/c)². Kandydat: kod kolumny → pierwsza pasująca reguła palety {biomy, strefy, stl, zespoły} → gatunek z wag × rampa zasięgu (§9) → `PlacedFeature.place` na `OCEAN_FLOOR`. Luki: szum o fali 40–80 m usuwa 5–10%. `max_water_depth` 0–2 dla wierzby i olszy. | 1–2 ms |
| `deadwood` | `fallen_{oak,birch,spruce,poplar}_tree` według palety, 0,15–0,5 na chunk | < 0,1 ms |
| `understory` | krzewy z palety na wolnych kolumnach | < 0,2 ms |
| `waterside_zones` | Pętla po 256 kolumnach według strefy: trzcina (`sugar_cane` 2–4 albo własna trzcina) na półce; pałka (`small_dripleaf` albo własna) w wodzie 1-blokowej na dnie z błota; wiklina; okrajek `tall_grass`/`large_fern`/`bush`; lepiężnik (`big_dripleaf`) na `coarse_dirt`/`rooted_dirt`/torfie; kidzina; łacha. `setBlock(…, 2)` z `canSurvive`. Y bierzemy z `ChunkHabitats`. | ~0,1 ms |
| `ground_layer` | Pętla po 256 kolumnach: ważony stan bloku według (STL, zespół) z pokryciem w %; rośliny podwójne przez `DoublePlantBlock.placeAt`; sprawdzenie powietrza i `canSurvive` | 0,1–0,2 ms |
| `aquatic_plants` | `lily_pad` (NYMFEIDY), `seagrass`/`tall_seagrass` (ELODEIDY, rzeki) według strefy i głębokości | < 0,1 ms |

Tryb świata dyspozytor czyta z `((PolandChunkGenerator) generator).settings()`.

### 8.3 `ChunkHabitats`

- Zawartość: `int[256]` kodów, `short[256]` wierzchu gruntu, `short[256]` lustra wody, O i P chunka.
- Zapis w `fill()` jako nietrwały załącznik Fabric (`AttachmentRegistry.create`; API 2.2.30 ma mixiny dla `ChunkAccess` i `ImposterProtoChunk`).
- Odczyt w dyspozytorach przez `level.getChunk(cx, cz)`.
- Brak załącznika (proto-chunk zapisany między TERRAIN a FEATURES, np. przy C2ME lub zamknięciu serwera) uruchamia przeliczenie z modelu (ok. 2–3 ms) i zwiększa licznik `HABITAT_MISS`.
- Usunięcie w `spawnOriginalMobs`, ostatnim etapie generacji.
- Plan awaryjny: pamięć mapowana bezpośrednio na 16 384 wpisy (ok. 25 MB).

**Filtr `polishforests:habitat`** (`PlacementFilter`: biomy, strefy, STL, gleby) czyta `ChunkHabitats`, a dla punktu w sąsiednim chunku jego załącznik. Służy głazom, datapackom i przyszłym featurom w stylu wanilii.

### 8.4 Zastępstwa drzew (`polishforests:tree/<species>`, `polishforests:shrub/<species>`)

Każdy gatunek to placed feature z `would_survive minecraft:oak_sapling` i feature JSON typu `minecraft:tree`. W M3 zmieniamy plik feature'a, a identyfikator zostaje. Kora jest najbardziej rozpoznawalną cechą gatunku, więc dobieramy ją pierwszą.

| Gatunek | id | Zastępstwo M2 | Wysokość |
|---|---|---|---|
| sosna | `tree/scots_pine` | `straight` 6+4, `pine_foliage`, `spruce_log`, `spruce_leaves`; wariant niski 3+2 (bór bagienny, pas wiatrowy) | 4–16 |
| świerk | `tree/spruce` | `minecraft:spruce_checked`; w górach 3% `mega_spruce_checked` | 8–18 |
| jodła | `tree/fir` | `straight` + `spruce_foliage`, `pale_oak_log` (szara kora), `spruce_leaves` | 12–18 |
| brzoza | `tree/birch` | `birch_checked`, 20% `super_birch_bees_0002` | 6–12 |
| dąb | `tree/oak` | `fancy_oak_checked` 60%, `oak_checked` 40% | 5–14 |
| buk | `tree/beech` | `fancy_trunk` + `fancy_foliage`, `pale_oak_log`, `oak_leaves` (nie `dark_oak`, bo pień 2×2 jest za gruby) | 10–20 |
| grab | `tree/hornbeam` | `straight` 6+2, `blob` r2–3, `pale_oak_log`, `oak_leaves` | 7–10 |
| lipa, jesion, wiąz, klon, jawor | `tree/linden` itd. | osobne identyfikatory, w M2 kształt `fancy_oak` | 6–14 |
| olsza czarna | `tree/black_alder` | `straight` 8+3, `blob` r2, `dark_oak_log`, `dark_oak_leaves` | 8–12 |
| olsza szara | `tree/gray_alder` | `straight` 6+3, wąski `blob` r2, `pale_oak_log`, `oak_leaves` | 7–10 |
| wierzba biała | `tree/white_willow` | `minecraft:swamp_oak` (pnącza jako chmiel) | 5–9 |
| topola | `tree/poplar` | `poplar_trunk_placer` + `poplar_foliage_placer`, `poplar_log`, `oak_leaves` | 11–16 |
| jarząb | `tree/rowan` | mały `oak` + `blob` r2 | 4–7 |
| kosodrzewina | `shrub/dwarf_mountain_pine` | 1 kłoda + `bush_foliage` r2–3, `spruce_log`, `spruce_leaves` | 1–3 |
| wiklina | `shrub/osier` | `straight` 1–2 + `bush_foliage` r2, `oak_log`, `oak_leaves` | 2–3 |
| leszczyna | `shrub/hazel` | `bush_foliage` r2, `oak_leaves` | 2–3 |
| jałowiec | `shrub/juniper` | `spruce_foliage` r1, h 2–3 | 2–3 |

Drzewa są celowo umiarkowane, bo koszt światła rośnie z liczbą liści. Drzewa 30–40 m przychodzą w M3.

### 8.5 Gęstość i skład

Liczby to drzewa na chunk. Gatunki objęte regułami zasięgu (§9) mają wagę mnożoną przez rampę.

| Biom | Drzew/chunk | Skład, % |
|---|---|---|
| `dry_pine_forest` | 7 | So 95 (30% niska), Brz 5 |
| `fresh_pine_forest` | 11 | So 85, Brz 10, Św 5 |
| `coastal_pine_forest` | 9 (pas wiatrowy 5, sosna niska) | So 90, Brz 10 |
| `moist_pine_forest` | 10 | So 65, Brz 25, Św 10 |
| `bog_woodland` | 6 | So niska 70, Brz 30 (brzezina: Brz 80) |
| `mixed_pine_forest` | 10 | So 55, Db 25, Brz 10, Bk lub Św 10 |
| `mixed_forest` | 9 | Db 45, So 30, Bk lub Św 15, Gb 10 |
| `oak_hornbeam_forest` | 9 | Db 35, Gb 30, Lp 15, Js/Kl 5, Bk 10, Św 5 |
| `lowland_beech_forest` | 7 | Bk 85, Db 10, Jw 5 |
| `alder_carr` | 9 | Ol 85, Brz 10, Js 5 |
| `ash_alder_forest` | 9 | Ol 55, Js 30, Wz 10, Brz 5 |
| `willow_poplar_forest` | 7 | Wb 70 / Tp 30; zespół topolowy 40/60 |
| `elm_ash_forest` | 8 | Db 35, Js 30, Wz 25, Kl/Lp 10 |
| `upland_fir_forest` | 10 | Jd 50, Bk 25, Db 15, So 10 |
| `montane_beech_forest` | 8 | Bk 70, Jd 20, Św 5, Jw 5 |
| `montane_spruce_forest` | 12, spada do 4 przy granicy lasu | Św 90, Jrz 10 (Abieti-Piceetum: Jd 30) |
| `gray_alder_forest` | 8 | Olsz 70, Js 15, Św 10, Wb 5 |
| `dwarf_pine_scrub` | 0 drzew | kosodrzewina pokrywa 70%, 1 karłowy Św |
| `raised_bog` | 0,3 | So niska |
| `heath` | 0,5 | So, Brz, jałowiec |
| łąki | 0,1 | pojedyncze drzewa i szpalery |
| `willow_scrub` | 0 | wiklina pokrywa 80–100% |
| `arable_land`, `alpine_grassland`, `beach`, wydmy | 0 | wydma szara: wierzba piaskowa jako krzew, So 1/8 |

### 8.6 Runo (pokrycie kolumn)

| Grupa | Skład |
|---|---|
| bory | `moss_carpet` 30–70% (świeży), `pale_moss_carpet` 50–80% jako chrobotki (suchy), `bush` jako krzewinki 20–40%, `sweet_berry_bush` jako borówka na podzolu i mchu, `fern`/`large_fern` jako orlica 5–15% (BM) |
| grąd | `leaf_litter` 30–60%, `wildflowers` jako geofity 10–30%, `lily_of_the_valley` 3%, `fern` 5%, trawa ≤ 10% |
| buczyna | `leaf_litter` 70–90%, poza tym prawie nic |
| ols, łęgi | `tall_grass` i `large_fern` 60–90%, `bush`, `firefly_bush` przy wodzie, lepiężnik w OlJ |
| wrzosowisko | `pink_petals` jako wrzos, `pale_moss_carpet`, trawy |
| łąka | `short_grass` i kwiaty |
| hala | `short_grass`, `bush` |

Grzyby wanilijne mają ≤ 1/256, bo grzybobranie to M4. Własne kwiaty biomów dopisujemy do tagu featurów `minecraft:can_spawn_from_bone_meal`.

---

## 9. Zasięgi gatunków w świecie proceduralnym

O i P pochodzą z `RegionalField` (V3). Na chunk są praktycznie stałe, więc zapisujemy je w `ChunkHabitats`. Rampa ekotonu ma szerokość ±0,05 i jest mnożona przez szum o fali 2 km·k, żeby granica nie była linią.

| Gatunek | Reguła | Użycie w M2 |
|---|---|---|
| Buk | O ≥ 0,40 lub P ≥ 0,40; H ≤ górna granica regla dolnego; nie na Bs, Bb, Ol ani Lł | flaga BUK, biom `lowland_beech_forest`, wagi w paletach |
| Jodła | P ≥ 0,50, H ≤ 1250; nie na Bs, Bb, Ol | flaga JODŁA, `upland_fir_forest`, wariant w `montane_beech_forest` |
| Świerk naturalny | O < 0,30 lub P ≥ 0,50 | flaga SWIERK, domieszki, zespoły NE |
| Grab | H ≤ 600 (stoki S ≤ 700); tylko L i LM | flaga GRAB, palety |
| Olsza szara | P ≥ 0,50 lub O < 0,30 | `gray_alder_forest`, palety |
| Dąb bezszypułkowy, modrzew, cis, bluszcz | reguły z raportu ekologii (§5.1) | tylko `SpeciesRanges.java` i pole zespołu; użycie od M3/M4 |
| Limba | tylko typ „Tatry” | M5 |

Na poziomie biomu progi są twarde. Na poziomie drzewostanu waga gatunku jest mnożona przez rampę. Między O ≥ 0,4 a O < 0,3 przy P < 0,4 powstaje pas bez buka i świerka, jak na Mazowszu. Test pilnuje, by zajmował 10–20% lądu.

---

## 10. Tagi, struktury, spawny, atrybuty

Fakty sprawdzone w jarze 26.3:
- `has_structure/stronghold` = `#minecraft:is_overworld`;
- `has_structure/trial_chambers` to **jawna lista biomów wanilii**, bez tagów `is_*`;
- `has_structure/mineshaft` używa `#is_ocean`, `#is_river`, `#is_beach`, `#is_mountain`, `#is_hill`, `#is_taiga`, `#is_forest` oraz nazw biomów;
- `has_structure/pillager_outpost` i `ruined_portal_mountain` zawierają `#minecraft:is_mountain`.

Tagi biomów (datagen):

| Tag | Biomy | Skutek |
|---|---|---|
| `minecraft:is_overworld` | wszystkie 36 | twierdze, czyli dostęp do Endu |
| `minecraft:stronghold_biased_to` | lądowe | rozmieszczenie twierdz |
| `minecraft:is_forest` | 18 leśnych | kopalnie, `ruined_portal_standard` |
| `minecraft:is_river` | `river`, `stream` | kopalnie, portale, topielce, kontury map |
| `minecraft:is_beach` | `beach` | wraki na plaży, skarby, kopalnie |
| `minecraft:is_ocean` | `sea` | wraki, `ruined_portal_ocean`, kopalnie |
| `has_structure/mineshaft` | nieleśne lądowe, `lagoon`, wody stojące (jawnie) | kopalnie wszędzie |
| `has_structure/trial_chambers` | wszystkie 36 (jawnie) | komnaty prób |
| `has_structure/ruined_portal_standard` | nieleśne lądowe (jawnie) | portale |
| `has_structure/ocean_ruin_cold` | `sea` | ruiny na dnie Bałtyku |
| `minecraft:water_on_map_outlines` | wody | mapy |
| `sereneseasons:*` | §6.3 | |
| własne: `polishforests:forests`, `pine_forests`, `waterside`, `montane`, `polish_climate` | – | filtry i mixin |

- **`#is_mountain`** dostają `montane_beech_forest`, `montane_spruce_forest`, `dwarf_pine_scrub` i `alpine_grassland` (decyzja M2-C: posterunki, portale górskie i kopalnie w górach są pożądane). `#is_hill` i `#is_taiga` nie są używane, chyba że krok S5 wykaże, że jakaś struktura wymaga ich do sensownego rozmieszczenia.
- **Wioski, posterunki, obozy, igloo, chaty i rezydencje:** decyzja M2-C, mapowanie w §10.1.
- **Spawny** (atrybut `gameplay/natural_mob_spawns`):
  - lasy: wilk 5, lis 8, królik 4, bez zwierząt hodowlanych;
  - łąki, pola i hala: owca 12, świnia 10, kura 10, krowa 8;
  - olsy, torfowiska i szuwar: żaba 10;
  - rzeka: łosoś, topielce z wagą 30;
  - morze: dorsz, bez kałamarnic;
  - potwory jak w wanilijnym `forest`, bez czarownic w olsach i szlamów na powierzchni.
  
  Polska fauna przychodzi w M6.
- **Atrybuty:**
  - muzyka: lasy `music.overworld.forest`; bory i świerczyny `old_growth_taiga`; olsy i torfowiska `swamp`; hala `meadow`;
  - `increased_fire_burnout`: olsy, łęgi, torfowiska, szuwar;
  - `dystrophic_lake`: `water_fog_color` #3B2A1A.
- **Kolory wody:** rzeka #4A6E5E, potok #4F8FB8, jezioro #3D6E70, dystroficzne #5A4A2E, morze #3A6A7A, zalew #5B7A5A. Kolory trawy i liści są wartościami startowymi z colormapy, kalibrowanymi na zrzutach w czterech porach roku z SS.
- **Chmury:** `visual/cloud_height` w obu typach wymiaru wynosi dziś 192,33. W REAL to poziom nizin. Podnosimy do REAL Y ≈ 1060 (ok. 1000 m n.p.m.) i GAMEPLAY Y ≈ 380.

### 10.1 Struktury (decyzja M2-C: wszystkie, które da się sensownie umieścić)

Krok S5 wypisuje wszystkie tagi `has_structure/*` z jara 26.3 i dla każdej struktury zapisuje wybór w tabeli w `docs/03-m2-biomy.md`. Wartości startowe:

| Struktura | Biomy | Uwagi |
|---|---|---|
| twierdza | wszystkie lądowe (`is_overworld`, `stronghold_biased_to`) | dostęp do Endu |
| kopalnia | wszystkie lądowe i wody śródlądowe | |
| komnaty prób | wszystkie 36 | |
| wioska równinna | `hay_meadow`, `arable_land`, `heath` | wioski wanilijne do M8, potem polskie wsie |
| wioska tajgowa | `fresh_pine_forest`, `mixed_pine_forest` | drewniane chaty pasują do borów |
| posterunek | `hay_meadow`, `arable_land`, `heath`, biomy `#is_mountain` | |
| chata czarownicy | `alder_carr`, `fen` | |
| igloo | `alpine_grassland` | |
| rezydencja leśna | `oak_hornbeam_forest`, `lowland_beech_forest`, `montane_beech_forest` | odpowiednik ciemnego lasu |
| ruiny szlaku (trail ruins) | bory, `montane_spruce_forest`, `oak_hornbeam_forest` | |
| zrujnowany portal | standardowy: lądowe; bagienny: `alder_carr`, torfowiska; górski: `#is_mountain`; oceaniczny: `sea` | |
| wrak | `sea`; wrak na plaży: `beach` | |
| ruiny oceaniczne (zimne) | `sea` | |
| zakopany skarb | `beach` | |
| monument, piramida, świątynia dżungli, starożytne miasto, wioski pustynne, sawannowe i śnieżne | brak | wymagają warunków nieobecnych w świecie |

Każdą strukturę sprawdza gametest (`/locate structure`), a jej położenie na terenie 1:1 ocenia zrzut ekranu.

---

## 11. Pliki i klasy

`JAVA` = `src/main/java/pl/polishforests/`, `CLIENT` = `src/client/java/pl/polishforests/client/`.

**Model (czysta Java):**

| Plik | Zmiana |
|---|---|
| `JAVA/worldgen/landscape/ColumnSample.java` | rekordy `Terrain`, `Waters`, `Region` (typy proste) |
| `JAVA/worldgen/landscape/LandscapeModel.java` | kontekst wyjściowy w `cellElevation`, flagi form w `sample`, wpięcie siatek, `describe()` na nowych polach |
| `JAVA/worldgen/landscape/RiverNetwork.java` | `RiverHit` z polami d, W, lustro, u, półszerokość dna, spadek, brzeg wypukły; pierścień starorzecza |
| `JAVA/worldgen/landscape/Landform.java` | progi z `AltitudinalBelts` |
| `JAVA/worldgen/landscape/CoarseTerrainField.java`, `RegionalField.java`, `DirectCache.java` | nowe |

**Siedliska** (`JAVA/worldgen/habitat/`, czysta Java; w nawiasach nazwy sprzed M2-9):
- `HabitatBiome` (`Biom`; enum: id, grupa, T, downfall, kolory, atmosfera, spawny, tagi, zamarzanie, nośnik mączki);
- `Zone` (`Strefa`), `ForestSiteType` (`Stl`), `Association` (`Zespol`), `LandCover` (`Pokrycie`), `Soil` (`Gleba`), `Species` (`Gatunek`), `Habitat` (`Siedlisko`, pakowanie do `int`);
- `HabitatClassifier` (`Klasyfikator`), `Moisture` (`Wilgotnosc`), `Fertility` (`Trofia`), `WatersideZones` (`StrefyNadwodne`), `AltitudinalBelts` (`Pietra`), `Coast` (`Wybrzeze`), `OpenWater` (`Woda`), `SpeciesRanges` (`Zasiegi`), `ForestCover` (`Lesistosc`), `Calibration` (`Kalibracja`).

**Generator** (`JAVA/worldgen/chunk/`):
- `PolandBiomeSource` (przepisany);
- `PolandChunkGenerator` (klasyfikacja i gleba w `fill`, półka, mikrorelief, `pack` 5–8 bitów, `coverBlocks`, `ChunkHabitats`, F3, wiązanie klimatu);
- `ModBiomeKeys` (`ResourceKey`), `ChunkHabitats` (nowy);
- `PolandSettings` (pole `version`: `optionalFieldOf`, brak = 1, presety M2 = 2);
- `PolandDimension` (`isPoland` dla obu skal).

**Powierzchnia:** `JAVA/worldgen/surface/SoilBlocks.java`, `BankShelf.java`, `Microrelief.java`.

**Featury** (`JAVA/worldgen/feature/`):
- `ModFeatures` (rejestracja typów, filtra i załącznika);
- `TreeStandFeature`, `DeadwoodFeature`, `UnderstoryFeature`, `WatersideZonesFeature`, `GroundLayerFeature`, `AquaticPlantsFeature`, `HabitatFilter`;
- `plan/` (czyste planery), `config/` (rekordy palet z kodekami).

**Bloki:** brak w M2 (decyzja M2-D); `JAVA/block/ModBlocks.java` w M4.

**Klimat:**
- `JAVA/climate/`: `PolandClimate`, `BiomeClimate`, `BiomeClimateAccess`, `ClimateBinding` (od S1);
- `JAVA/mixin/`: `BiomeClimateMixin`, `BiomeTemperatureMixin`, `BiomeFreezeMixin`;
- `CLIENT/ClientClimate.java`.

**Pozostałe:**
- `JAVA/command/PolishForestsCommands.java` (cele `biome`/`zone`, `here` z drugą linią);
- `JAVA/PolishForests.java` (kolejność rejestracji, zdarzenia), `JAVA/worldgen/PolishForestsWorldgen.java`;
- `CLIENT/screen/PolandPresetEditor.java`.

**Datagen** (`CLIENT/datagen/`): `PolishForestsDataGenerator`, `ModBiomeProvider` (`Registries.BIOME`, **`Registries.FEATURE`**, `Registries.PLACED_FEATURE`), `ModBiomeTagProvider`, `ModBlockTagProvider`, `ModFeatureTagProvider`, `ModLanguageProvider`, `ModModelProvider`, `ModLootProvider`. Wynik trafia do `src/main/generated` w repozytorium. Test uruchamia `runDatagen` i sprawdza `git diff --exit-code`.

**Zasoby:**
- `src/main/resources/polishforests.mixins.json` (nowy), `fabric.mod.json`;
- `data/polishforests/worldgen/world_preset/poland.json`, `poland_gameplay.json`;
- `data/polishforests/dimension_type/poland.json`, `poland_gameplay.json` (chmury);
- `tools/textures/soils.py`.

**Testy:** `src/test/java/pl/polishforests/...` (§12). **Dokumentacja:** `docs/01-architektura.md`, `docs/00-decyzje-do-podjecia.md`, `docs/m2/`.

---

## 12. Testy i definicja ukończenia

### 12.1 Jednostkowe (JUnit, bez gry)

| Test | Co sprawdza |
|---|---|
| `GoldenTerrainTest` | hash (surface, waterLevel, waterKind, type, substrate, coverDepth) siatki 32 × 32 i łat 16 × 16 w trudnych miejscach i wnętrzach kontrolnych dla 5 zestawów (2 skale × 2 ziarna + suwak regionów 0,5) jest identyczny z plikiem wzorcowym: `terrainMatchesGolden` porównuje obecny model z `golden_terrain_m2.txt` (przegenerowany raz w K7 poprawki geometrii terenu, `docs/m2/poprawka-geometrii.md`; bez list dozwolonych zmian), a `frozenM1CopyMatchesGolden` zamrożoną kopię M1 z `golden_terrain_m1.txt` |
| `SurfaceContinuityTest`, `TerrainLocalityTest`, `TerrainDeterminismTest` | poprawka geometrii terenu: izolowane skoki powierzchni na transektach (progi „nie gorzej” i cele kroków); teren poza dolinami i wodami identyczny z kopią M1; kolumna nie zależy od kolejności próbkowania (ściśle od K5.2: poziom jezior rynnowych z punktu kanonicznego) |
| `LandscapeModelTest`, `RiverNetworkTest` (rozszerzenia) | nowe pola są deterministyczne i skończone; d jest ciągłe (\|Δ\| ≤ 1,5 m na 1 m poza przełączeniem koryta) i ≤ 0 w korycie; u ∈ [0, 1] w dnie; ok. 50% brzegów w zakolach jest wypukłych |
| `RegionalFieldTest` | zakres [0, 1]; gładkość; pas bez buka i świerka zajmuje 10–20% lądu na 3 ziarnach; zasięg P ≥ 0,5 od osi pasma (10 ziaren razem); Wz patrzy na zachód; niezależność od pamięci i wątków |
| `HabitatClassifierTest` | determinizm; tabelaryczne przypadki syntetyczne; każdy z 36 biomów i każda strefa osiągalne; brak łęgu poza dnem i źródliskami na 10⁶ próbek |
| `WatersideZonesTest` | przekroje 200 rzek klas A, B, C × 2 skale. Kolejność: koryto → wiklina → okrajek → łęg wierzbowy → topolowy → wiązowy → zbocze (A), koryto → ziołorośla → OlJ → strefowe (B), koryto → kamieniec/wiklina → olszyna → strefowe (C). Minima w blokach (E11) spełnione |
| `AltitudinalBeltsTest` | Beskidy: poniżej 1100 m buczyna, jedlina i olszyna ≥ 70%; 1200–1350 m świerczyna ≥ 80%; kosodrzewina tylko przy dużym masywie; granica regla na stokach S wyżej niż na N o 80–120 m |
| `SpeciesRangesTest` | zero buka przy O < 0,35 i P < 0,35; zero jodły przy P < 0,45; zero naturalnego świerka przy O > 0,35 i P < 0,45 |
| `BiomeSharesTest` | 200 tys. punktów, 1000×1000 km REAL i 30×30 km GAMEPLAY, 3 ziarna (rozrzucone punkty: ok. 16 `landElevation` na punkt w siatce terenu, §3.2). Tryb N: lesistość ≥ 88%, SANDR bory ≥ 85% lasu. Tryb D: lesistość 26–34%, bory 45–58% lasu, typy świeże 50–70%, olsy i łęgi 3–6% |
| `PolandClimateTest` | obie skale: T(0 m) = T_bazowa; latem śnieg ≥ 1950 m; przy −0,25 próg 1050–1150 m; wszystkie T_bazowa ≤ 0,8 |
| `BiomeJsonTest` | 36 plików; T ≤ 0,8; klucze lang PL i EN; obecność w `is_overworld`, `trial_chambers` i `polish_climate` |
| `FeatureOrderTest` | listy wszystkich biomów są identyczne poza nośnikiem mączki; nośnik jest ostatni; każde id istnieje |
| `TreeStandPlanTest`, `GroundLayerPlanTest` | determinizm; średnia liczba drzew ±10% od palety; rozstaw ≥ c/2; udziały gatunków ±5%; pokrycie runa ±5% |
| `SoilTest` | `coverBlocks` w obu skalach; wierzch gleb leśnych należy do `#supports_vegetation`; liczba stanów w sekcji przy powierzchni ≤ 32 |
| `PolishForestsCommandsTest` | nowe cele `biome` i `zone` dają się znaleźć |
| `SampleCostTest` | raport (bez asercji): µs na `sample` i `classify` dla 100 tys. kolumn wobec M1 |

### 12.2 Podgląd PNG (`./gradlew landscapePreview`)

- Nowe tryby: biomy z legendą, strefy, DGW, O i P, trofia.
- Kadry:
  - dolina dużej rzeki: 2 km REAL, 1 km GAMEPLAY;
  - mała rzeka: 500 m;
  - potok górski: 1 km;
  - jezioro rynnowe z pierścieniami: 1 km;
  - oczko na sandrze: 500 m;
  - wybrzeże z zalewem: 3 km;
  - Beskidy: 10 km;
  - mapy O, P i zasięgów: 2000 km;
  - maska lasu w trybie D: 50 km.
- Przekrój doliny (strefy w funkcji d) dla klas A, B, C.
- Plik CSV z udziałami. Wyniki trafiają do `docs/m2/`.

### 12.3 W grze (client gametest)

| Test | Co sprawdza |
|---|---|
| `PolandWorldClientGameTest` | 11 miejsc w obu skalach (łęg A, łęg B, ols, szuwar jeziorny, wikliny, bór suchy na wydmie, buczyna, regiel górny, kosodrzewina, plaża z wydmami, torfowisko wysokie). Po stronie serwera `getBiome` = klasyfikator, a blok gruntu = `SoilBlocks`; `generator.validate()` bez wyjątku; zrzuty ekranu |
| `HabitatsClientGameTest` | Transekty: 3 rzeki × 2 skale × 2 tryby, kryteria z §4.6. Półka: ≥ 90% kolumn strefy brzegowej ma wodę obok bloku wierzchu. Zamknięcie wody w blokach (brak rozlewów po 200 tickach). `/locate structure` znajduje stronghold, mineshaft i trial_chambers. `/locate biome polishforests:oak_hornbeam_forest` trwa < 2 s |
| temperatura | bez SS brak śniegu poniżej 1900 m latem; z SS (`localRuntime`) zimą śnieg na nizinie, latem brak na 1500 m; opad klienta (`ClientLevel.getPrecipitationAt`) zgodny z serwerem w 20 punktach |
| `PerformanceClientGameTest.profileStages` | BIOMES, TERRAIN, FEATURES i LIGHT oraz liczniki `CLASSIFY_NANOS`, `HABITAT_MISS`, `PACK_FALLBACKS` i czas każdej warstwy dyspozytorów, przed M2 i po każdym kroku |
| determinizm | dwa światy z tym samym ziarnem, z paczką optymalizacyjną (C2ME) i bez; skrót bloków 64 chunków identyczny |
| `UiAndCommandsClientGameTest` | wszystkie cele; `here` zawiera biom, siedlisko, strefę, glebę i T |

### 12.4 Definicja ukończenia M2

1. `./gradlew build test runClientGameTest` przechodzi w obu skalach i obu trybach. Datagen jest aktualny. W logach nie ma „Feature order cycle”, „Unknown registry key” ani „Tried to biome check”.
2. Świat Polska nie ma biomów spoza `polishforests`. F3 pokazuje biom, STL i strefę.
3. Kryteria pasa nadrzecznego z §4.6 są spełnione w obu skalach i trybach. To jest prośba użytkownika.
4. Śnieg i pory roku jak w §6.1, z SS i bez.
5. Działają twierdze, kopalnie i komnaty prób. Każdy biom i każda strefa dają się znaleźć komendą.
6. Budżety z §3.6 są dotrzymane.
7. Użytkownik zaakceptował PNG siedlisk (punkt kontrolny 1) i zrzuty roślinności (punkt kontrolny 2).
8. Dokumentacja i decyzje są zaktualizowane (§15).

---

## 13. Ryzyka

| # | Ryzyko | Ograniczenie |
|---|---|---|
| R1 | Datagen 26.3: dokumentacja Fabric kończy się na 26.2, `CONFIGURED_FEATURE` zastąpiło `FEATURE` | próba na 1 biomie i 1 featurze na początku S5; plan awaryjny: szablon JSON dekodowany kodekiem w bootstrapie |
| R2 | Identyfikatory biomów są nieodwracalne | decyzja o granulacji przed S5; enum tylko dopisywany |
| R3 | Cudze `BiomeModifications` dopisują featury do naszych biomów (cykl, przesunięcie indeksów) | nasze listy są identyczne; dyspozytory mają własne ziarno; test z popularnymi modami roślin przed wydaniem |
| R4 | Utrata `ChunkHabitats` | przeliczenie z modelu i licznik; plan awaryjny z pamięcią mapowaną |
| R5 | Mixin na prywatnej metodzie lub zmiana bramki SS ≤ 0,8 w kolejnym dropie | `require = 1`, `PolandClimateTest`, gametest z SS |
| R6 | Rozjazd klimatu klienta i serwera (inny typ wymiaru, datapack) | tylko wizualny; typ wymiaru z identyfikatora, w razie braku wanilia; ostrzeżenie w logu |
| R7 | Półka lub kałuże wylewają wodę | półka na tym samym Y co woda; kałuże tylko przy 4 sąsiadach wyżej; test zamknięcia wody w blokach |
| R8 | Zmiana `ColumnSample` przypadkiem zmienia teren | złoty test (S0) przed jakąkolwiek zmianą |
| R9 | Narastający koszt modelu | budżety, `SampleCostTest`, zakaz `describe`/`landElevation`/`coastDistance` w generacji |
| R10 | Heurystyki DGW, trofii, P_las, O i P dają złe udziały | wszystkie progi w `Calibration`; `BiomeSharesTest` i PNG; punkt kontrolny 1 |
| R11 | `applyBiomeDecoration` zbiera biomy ze wszystkich sekcji 9 chunków (1179 sekcji w REAL) | pomiar w S10; ewentualne nadpisanie z jedną sekcją na chunk, kosztem utrzymania |
| R12 | Zastępcze drzewa za małe dla skali 1:1, a `pale_oak_log` u buka, grabu, jodły i olszy szarej myli graczy | umiarkowane gęstości; opis „zastępstwo do M3” w `here`; M3 |
| R13 | Światy z M1 dostaną szwy na styku starych i nowych chunków | brak migracji; pole `version` na przyszłość |
| R14 | Rzadkie biomy daleko w REAL (kosodrzewina, hala) | cele komendy `find` z dużym promieniem; w testach syntetyczne próbki |
| R15 | Opad rysowany na wysokości kamery: przelot nad niziną może pokazać śnieg | zachowanie wanilii, zostawiamy |
| R16 | Bez lawy na powierzchni trudniej o obsydian | lawa poniżej Y 0 i zrujnowane portale |

---

## 14. Poprawki faktów (sprawdzone 2026-10-02)

1. Zdarzenie serwera to **`ServerLevelEvents.LOAD`/`UNLOAD`** (fabric-lifecycle-events-v1 4.1.9), nie `ServerWorldEvents`. Klient: `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE`. Tagi: `CommonLifecycleEvents.TAGS_LOADED`.
2. Tag to **`minecraft:supports_big_dripleaf`** (nie `big_dripleaf_placeable`). Nie zawiera żwiru ani piasku, więc lepiężnik na kamieńcu stawiamy na łatach `coarse_dirt`/`rooted_dirt`.
3. `small_dripleaf` wymaga `#supports_small_dripleaf` (glina, `moss_block`) albo wody źródłowej nad blokiem z `#supports_vegetation`. Dno z piasku lub żwiru nie działa, stąd pas `mud` przy brzegu.
4. `has_structure/trial_chambers` to jawna lista biomów wanilii. Bez dopisania naszych biomów komnaty prób znikną.
5. `#minecraft:is_mountain` włącza `pillager_outpost`, `ruined_portal_mountain` i kopalnie. Nie dodajemy do niego naszych biomów.
6. W 26.3 nie ma placed featura `minecraft:no_op`, jest tylko typ.
7. `spring_lava` ma `valid_blocks` z `minecraft:dirt` i zakres do `below_top 8` (w REAL Y 2023). Lawa może wypływać na stokach, więc usuwamy.
8. Brzeg w modelu leży na lustrze + 1 m (`RN:1044`), więc bez półki trzcina i świetliki przy rzekach w REAL się nie postawią. W GAMEPLAY zaokrąglenie często daje ten sam blok co lustro.
9. Rekord z polem tablicowym psuje `equals` w `sameSeedGivesSameTerrain`. Nowe pola mają tylko typy proste.
10. Siatka z 8 pełnymi `sample()` na węzeł co 8 m w GAMEPLAY to +9–17% modelu, a nie +2–4%. Zastępuje ją `CoarseTerrainField` na `landElevation`.
11. DGW: GW = min(sBar − g; L_w + i·r), DGW = H − GW. Formuła z projektu „silnik” była odwrócona.
12. OKRAJEK leży **za** wikliną i w lukach łęgu, nie przy korycie.
13. Kosodrzewina wymaga dużego masywu (E12, raport 06 §5.5). Sam próg wysokości to błąd.
14. Jedlina wyżynna rośnie na 250–650 m, nie 200–550.
15. Buk to nie `dark_oak` (pień 2×2, ciemna kora), tylko `pale_oak_log` z `oak_leaves`.
16. Kalibracja: 0,27 z raportu klimatu dotyczy Śnieżki. Babia Góra przy Γ 0,55 °C/100 m wychodzi ok. 0,226.
17. Kwantyle trofii muszą mieć tę samą semantykę we wszystkich typach (§3.3).
18. `docs/01-architektura.md`:
    - sekcja 10 podaje „Y 80, ok. 550 m” i „morze Y −470”, a po A4 wanilia liczy spadek od Y 80, czyli 17 m n.p.m.;
    - sekcja 13 twierdzi, że model podaje odległość od koryta, a do M2 nie podaje.
19. Decyzja B4 („ciągła mapa wymaga mixinu w SS”) jest nieaktualna. SS woła `Biome.getTemperature`, więc wystarczy mixin w `Biome`.
20. Raport 08 §11.4: `lesser_color_change_biomes` daje 25% zmiany i 75% oryginału.
21. Numeracja nowych decyzji M2 dostaje prefiks `M2-`, żeby nie kolidować z D1–D11 w `docs/00`.

---

## 15. Decyzje

### 15.1 Do zamknięcia w `docs/00-decyzje-do-podjecia.md`

- **B3:** zamknięta. Marzec = EARLY_SPRING jest już w `SubSeason.java`.
- **B4:** zamknięta. Ciągła mapa przez mixin w `Biome`, bez mixinu w SS. W M2 tylko wysokość (S0). Człon regionalny (siatka t0, bez ziarna) i S2 w M4.
- **D7:** biom = grupa siedliskowa, typ krajobrazu to atrybut. Liczbę biomów ustala odpowiedź użytkownika.
- **Nowe:**
  - M2-1: biomy kolumnowe 2,5D;
  - M2-2: gradient 0,55 °C/100 m;
  - M2-3: zasięgi V3, zachód = −X;
  - M2-4: lawa tylko poniżej Y 0, bez źródeł na powierzchni;
  - M2-5: chmury podniesione;
  - M2-6: Bałtyk nie zamarza;
  - M2-7: brak migracji światów M1.

### 15.2 Odpowiedzi użytkownika (2026-10-02)

1. Tryb: przełącznik w M2, domyślnie roślinność naturalna (M2-B).
2. Struktury: wszystkie, które da się sensownie umieścić (M2-C, §10.1).
3. Granulacja: 36 biomów (M2-A, D7 zamknięta).
4. Bloki własne: dopiero w M4 (M2-D).

### 15.3 Kolejność i punkty kontrolne

S0 → S1 (równolegle z S2) → S2 → S3 → S4 (**punkt kontrolny 1: PNG**) → S5 → S6 → S7 (**punkt kontrolny 2: zrzuty roślinności**) → S8 → S9 → S10.

Każdy krok zostawia działający świat i zielone testy. Jeśli zabraknie czasu, S8 przechodzi do M8.
