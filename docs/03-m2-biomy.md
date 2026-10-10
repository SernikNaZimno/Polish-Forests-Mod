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

**Poprawka geometrii terenu (M2-8, 2026-10-03 – 2026-10-08, kroki K0–K8).** Przed fazą 2 teren dostał wielkie masywy Beskidów (decyzja M2-8: szczyty ok. 1620–1725 m w obu skalach, kosodrzewina i hala także w GAMEPLAY), ciągłe pola i gładką geometrię dolin, starorzecza jako półksiężyce, jeziora rynnowe kończące się przed doliną, niecki bez ścian, wybrzeże wydmowe z klifem na ok. 1/5 brzegu i zalew za niskim brzegiem, a także okno mieszania regionów 5 × 5 bez szwów. Złoty test ma od K7 nowy plik `golden_terrain_m2.txt`, przegenerowany w K7 i w K8z (zmiany K8: jeziora rynnowe GAMEPLAY, brzeg zalewu i delty, wydmy i plaża, starorzecza i mikrorzeźba den; „Podsumowanie K8” w `docs/m2/poprawka-geometrii.md`). Opis, odstępstwa i pomiary przed i po: `docs/m2/poprawka-geometrii.md`; elementy terenu: `docs/01-architektura.md` §15. Liczby z S0–S4 w tym planie (udziały, pasy, piętra) zmierzono na terenie sprzed poprawki; przeliczyła je poprawka klasyfikatora (etap H, 2026-10-07: „Stan po poprawce geometrii” w §3.4), a sprawdzi punkt kontrolny 2.

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
| Z10 | **Płynne przejścia** (prośba użytkownika, 2026-10-09; od S8b, §8.8): na granicach biomów i siedlisk skład i gęstość drzewostanu, podszyt, runo, gleby i bloki wierzchu oraz kolory trawy, liści i wody zmieniają się w pasie przejściowym (ekotonie), nie na linii. Szerokość pasa zależy od pary siedlisk i skaluje się przez k (Z7): szeroko między podobnymi lasami, wąsko tam, gdzie granica w naturze jest wyraźna (brzeg wody, skraj torfowiska), nigdy prosta linia ani szachownica. Skraj lasu przy łące, polu, wrzosowisku, wydmie szarej i torfowisku ma płaszcz z krzewów i okrajek z ziół. Górna granica lasu: coraz rzadsze i niższe drzewa, potem kosodrzewina. Biomy (Z9) zostają, zmienia się tylko sposób przejścia. | Las nie zmienia się „jak nożem uciął” na konturze progu klasyfikatora. |

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
- **Wybrzeże (poprawione po recenzji S4):** model daje glinę (GLACIAL_TILL) każdej kolumnie pasa nadmorskiego wyższej niż 8 m, a formę KLIF według samej wysokości, więc w S4 cały pas za plażą wychodził jako klif i las wysoczyzny (0% wydm w świecie). Brzeg wydmowy i klifowy rozróżnia teraz nowe pole `terrain.lowShore` (`low` = 1 − smoothstep(6, 20, hl) z `shapeCoast`), a granicę gołego piasku nowe pole `terrain.bareSandWidth`; teren się nie zmienia. Garb wydmy modelu (6–20 m · low) przy low < 0,5 chowa się pod terenem pasa (hl ok. 13–24 m), więc na 80 przekrojach na skalę tylko 0–1 brzeg miał low ≥ 0,5. Próg brzegu wydmowego to więc low ≥ 0,25, czyli teren przy morzu niższy niż ok. 15 m (`Calibration.LOW_SHORE`); obejmuje to wszystkie mierzeje przed zalewami tej wysokości. Na brzegu wydmowym podłoże siedliska to piasek (`HabitatClassifier.Column.substrate`: BEACH_SAND bliżej niż `bareSandWidth`, dalej SAND), więc są tam wydma biała z wydmą inicjalną, wydma szara, bór bażynowy, zaplecze zalewu i trofia B. Klif (ściana, korona, las wiatrowy) i trofia wysoczyzny są tylko na brzegu wysokim. `CoastTest`: w pasie wydm B..B+D brzegów wydmowych (36 przekrojów w REAL, 40 w GAMEPLAY) wydmy zajmują 100% kolumn lądu poza dnami (biała ok. 61%, szara ok. 38%) bez stref klifu; na brzegach wysokich klif jest w 30 z 40 (REAL) i 32 z 40 przekrojów (GAMEPLAY). Zaplecze mierzei (torfowisko niskie przy h ≤ 0,4 m, ols przy h ≤ 1 m za pasem wydm) jest tylko na brzegu wydmowym i nie sprawdza obecności zalewu ani torfu. Granica boru bażynowego 2000k drga o ±15% (szum wariantów), a pas wybrzeża kończy się za jej najdalszym położeniem (max(B + D + 2000k, 1,15 · 2000k)), więc drganie nie jest ucinane prostą linią. Do decyzji w punkcie kontrolnym 1: próg 15 m (więcej wydm albo więcej klifów). *Po poprawce geometrii (K5b, D5):* opis wyżej dotyczy modelu sprzed D5. Teraz `shapeCoast` miesza niski brzeg (plaża, wydma przednia, zaplecze) z wysokim brzegiem z klifem według udziału wysoczyzny dochodzącej do morza, a `lowShore` = 1 − ten udział klifu (`ColumnSample`), więc garb wydmy nie chowa się już pod płaskim pasem 13–24 m. Próg brzegu wydmowego to `Calibration.LOW_SHORE` = 0,5 (środek mieszania), a sprawa progu 15 m jest rozstrzygnięta decyzją D5 (klif na ok. 1/5 brzegu: 22,2% w REAL, 21,0% w GAMEPLAY) i D5a; szczegóły i pomiary w `docs/m2/poprawka-geometrii.md`, K5, „D2: zalew bez rowu i K5b (D5): wybrzeże wydmowe”. Liczby `CoastTest` wyżej są sprzed D5; po K5b (próg 0,5): REAL 40 przekrojów brzegu wydmowego (w pasie wydm 100% wydm i boru bażynowego, biała 58,6%, szara 41,4%, 0 klifów) i 40 wysokiego (33 z klifem), GAMEPLAY 40 (61,8 / 38,2%) i 40 (24 z klifem). Etap H: wydmy biegną przez dna dolin poza ujściem, brzeg wydmowy w klasyfikacji 77,8% (REAL) i 79,7% (GAMEPLAY) punktów brzegu (niżej, „Stan po poprawce geometrii”). *K8b2:* szerokość plaży B zmienia się wzdłuż brzegu niskiego (zwykle 35–85 m·k), a model podaje ją w nowym polu `terrain.beachWidth`; plaża (`Coast`), koniec pasa wydm (`duneBeltEnd`, `duneOverFloor`) i `CoastTest` liczą się od niej (bez próbki B = 60k). Brzeg wydmowy 77,8% (REAL) i 79,5% (GAMEPLAY), w pasie wydm 100% wydm i boru bażynowego (`docs/m2/poprawka-geometrii.md`, „K8b2”). *K8z (udziały po całym K8, podgląd `landscapePreview -PhabitatsOnly` przeliczony 2026-10-08, procent wszystkich kolumn REAL / GAMEPLAY, wobec etapu H):* zalew 0,045 / 0,138 → 0,034 / 0,155 (brzeg z zatokami, delty zajmują 2,8 / 6,8% zalewu, K8b1), wydma szara 0,014 / 0,516 → 0,015 / 0,564, biała 0,016 / 0,411 → 0,017 / 0,412, plaża 0,010 / 0,277 → 0,010 / 0,272, bór bażynowy 0,044 / 1,456 → 0,043 / 1,479, szuwar 0,206 / 0,209 → 0,202 / 0,176, torfowisko niskie 0,020 / 0,099 → 0,019 / 0,066; strefy: wydma inicjalna 0,002 / 0,062 → 0,002 / 0,068, linia przyboju 0,006 / 0,187 → 0,007 / 0,183. Brzeg wydmowy w `CoastTest` bez zmian wobec K8b2 (77,8 / 79,5%). Kadry i CSV: `docs/m2/podglad-siedlisk-s4.md`.
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
- **Mikrorzeźba den a Z9 (K8c, runda 1 recenzji, 2026-10-08).** K8c dodał na dnach nizinnych drugą oktawę wysokości dna nad wodą (35 m·k, waga do 0,5; `RiverNetwork.floorOffset`), a po rundzie 1 w obu skalach. Ta oktawa ma falę < 64 m, więc wchodząc w h = H − lustro − 1 zmieniała granice olsu i torfowiska z łęgiem wiązowym na każdym dnie nizinnym (recenzja: płaty 10–20 m na okresie 35 m, małe zamknięte płaty < 1024 m² +2%), wbrew Z9. Model podaje teraz jej udział w powierzchni w polu `waters.floorFine` (razy największa maska dna, najwyżej ok. ±0,35 m, 0 poza dnami nizinnymi), a klasyfikator odejmuje go od H (`HabitatClassifier.Column.H`), więc h, DGW i pasy brzegu liczą się jak przed K8c od samego szumu 90 m·k. Teren ma drugą oktawę (proste stopnie przy starorzeczach), a biomy nie. Ols GAMEPLAY w `WatersideZonesTest` 52,2% cięciw ≥ 10 bloków (jak w bazie K8c; z oktawą w h było 43,8%), REAL 74,4%; kolumny lądu o innym biomie niż w bazie K8c w kadrach 1–2 km REAL: 0,15–1,3% (HEAD K8c 0,4–1,6%; reszta to zmiany kształtu starorzeczy). Test `riparianOnlyOnFloorsSpringsAndSeeps` liczy wysokość nad wodą tak samo (bez `floorFine`).
- **Zostaje (M5 i faza 2):** płaty biomów o prostych krawędziach tam, gdzie prosty jest sam teren lub podłoże modelu: głowica doliny potoku na sandrze jako czworokąt mady (`gameplay_large_river_valley_1km`), proste zbocza dolin w Beskidach (granice pięter i ekspozycji idą po poziomicach płaskich ścian) i dna dolin na zacisku terenu brzegu: dno ścięte do 2,000 m leży mniej niż 1,15 m nad rzeką (ok. 1 m), więc dostaje siedlisko strefowe (bór wilgotny, w krainach piasków) zamiast łęgu, a granica idzie prostą tam, gdzie dno wychodzi ponad zacisk (wybrzeże REAL, np. (−172000, −221050); tak samo w bazie `49aca9c`). Wewnętrzna krawędź pasa olsu zaplecza zalewu poza dnami i granice boru bażynowego z borem mieszanym na zmianie typu próbki (piasek pasa nadmorskiego / glina wysoczyzny) są też proste i pochodzą sprzed etapu H. Na wybrzeżu GAMEPLAY przy (−3268, −5357) zostaje prostokątny płat dna łęgu wiązowego z przełączenia doliny dominującej F1 między rzekami 4,8 i 2,9 m (także w bazie). Końce wydm przy dnach dolin w samym terenie: M5.

### 3.4.1 Stan po S8 (2026-10-09): tryb „dzisiejsza Polska” i wioski w lasach

Domyślny zostaje tryb roślinności naturalnej (N, decyzja M2-B). Tryb „dzisiejsza Polska” (D) włącza przełącznik „Krajobraz” w ekranie opcji świata (`PolandPresetEditor` → `PolandWorldOptionsScreen`, od S8 z podpowiedzią dla każdej wartości); pole `agriculture` w `PolandSettings` zapisuje się zawsze, świat w wersji 2 bez pola to tryb N, a świat M1 bez `version` czyta się jak w M1 (tryb D; bez zmian od S5, `PolandSettingsTest`). Teren się nie zmienia: złoty test bez zmian i bez list.

**Maska lasu (`ForestCover`, krok 6 algorytmu).** Kolumna biomu leśnego zostaje lasem, gdy kwantyl szumu F jest mniejszy od jej P_las (P_las ≥ 1 zawsze las, 0 nigdy).
- **F:** dwie oktawy, fala 6 km·k i 2 km·k z wagą 0,5 (dłuższa to dotychczasowy szum `habitat.lesistosc`, krótsza to nowe pole z nową solą `habitat.forest_cover.fine`), i drganie krawędzi o fali 300 m·k z wagą 0,08 (Z9). Kwantyl daje własna tablica rozkładu F (512 przedziałów, liczona raz z siatki 512 × 512 punktów o stałym ziarnie; rozkład zależy tylko od stosunków fal i wag), więc F jest jednostajny i przy stałym P_las udział lasu równa się P_las. W GAMEPLAY fale 3 i 1 km.
- **P_las według siedliska** (wszystkie wartości w `Calibration`, sekcja „PRESENT_DAY mode: forest mask”): Bs 0,8; Bśw 0,76; Bw i BMw 0,7; Bb i BMb 0,75; BMśw 0,3; LMśw 0,25; LMw i LMb 0,5; Lśw 0,1; Lw 0,35; łęg wierzbowo-topolowy 0,15; łęg wiązowo-jesionowy 0,06; łęg jesionowo-olszowy 0,13; ols 0,15; bór bażynowy 0,9; świerczyna górska 0,9; buczyna karpacka 0,8 i jedlina 0,6 (w dnach dolin obie 0,15); kosodrzewina 1; pozostałe STL (wyżynne BMwyż, LMwyż i Lwyż poza jedliną, górskie, `P_OTHER`) 0,5. Olszyna górska (§4.5): pas przy potoku clamp(2W; 5k; 20k) z drganiem stref zawsze las, dalej łąka w dnach szerszych niż 80k poniżej 900 m, gdzie indziej 0,6. Od rundy 1 poprawek: ols 0,10, łęg jesionowo-olszowy 0,09, a P_las przechodzi płynnie przez progi wilgotności (niżej, „Runda 1 poprawek S8”). **Od rundy 2 poprawek** P_las lądu suchego nie zależy od biomu ani STL (ciągłe współrzędne, inne wartości; niżej, „Runda 2 poprawek S8”).
- **Kontekst:** siedliska ubogie (B, BM) poza sandrem mają P_las × 0,75 (liniowo wagą typu SANDR: wielkie kompleksy borów na sandrach, piaszczyste płaty wśród pól gdzie indziej); na pogórzu P_las najwyżej 0,15 na łagodnych stokach (wagą typu); nachylenie w blokach przesuwa P_las ku 0,8 krzywą smoothstep 8–25° (GAMEPLAY 12–35°, bo stoki w blokach są tam bardziej strome); moreny czołowe i wydmy śródlądowe co najmniej w połowie tej drogi, stoki dolin na siedliskach żyznych (LM, L) w 0,3 drogi (GAMEPLAY 0,1: gęstsza sieć dolin względem krajobrazu dawała tam 7 punktów lesistości więcej). Łęgi, olsy i olszyna nie mają kontekstu (dno jest płaskie).
- **Biomy nieleśne** bez zmian od S4 (§2.2, kolumna D; `ForestCover.nonForest`): bory suche → wrzosowisko, bory świeże → pole albo wrzosowisko, bory wilgotne → łąka wilgotna albo pole, bory bagienne → torfowisko wysokie, siedliska wilgotne liściaste, łęgi i olszyna → łąka wilgotna, łęg wiązowy → łąka wilgotna 80% / pole 20%, ols → łąka wilgotna 70% / torfowisko niskie, góry → łąka świeża (polany), reszta przy nachyleniu < 5° pole 85% / łąka świeża, wyżej łąka świeża. Wybór między dwoma biomami z szumu wariantów o fali 1,5 km·k.
- **Odstępstwa od §4.5 (wartości startowe raportu ekologii):** łęg wierzbowy 0,45 → 0,15, łęg jesionowo-olszowy 0,5 → 0,13, ols 0,35 → 0,15, reszta dna dużej rzeki 0,10 → 0,06. Przy wartościach startowych olsy i łęgi zajmowały 10–11% lasu (cel 3–6%, w Polsce 3,8%: doliny są dziś głównie łąkami). Strefy przywodne zostają, więc brzegi rzek pozostają zarośnięte (transekty niżej).
- **Strefy:** strefa, STL potencjalny i zespół nie zależą od maski. SZPALER (`TREE_ROW`, 3–10k od koryta klasy B, olsze) powstaje tylko w trybie D; od S8 źródlisko zachowuje strefę `SPRING_AREA` także w pasie szpaleru (wcześniej szpaler ją zastępował).

**Udziały (`BiomeSharesTest`, 200 tys. kolumn na skalę, 3 ziarna, oba tryby na tych samych próbkach, ok. 22 s; plik `docs/m2/m2_biome_shares_present_day.csv`).** „Bory” to biomy borowe (bór suchy, świeży, bażynowy, wilgotny, bagienny, mieszany), „typy świeże” to Bśw, BMśw i LMśw (ok. 60% lasów Polski, `docs/research/06` §1.3; Lśw, ok. 9%, liczy się z lasowymi), „olsy i łęgi” to ols, trzy łęgi i olszyna górska. Typ krajobrazu liczy się we wnętrzu (waga typu ≥ 0,9) i tylko przy co najmniej 2000 kolumnach: Beskidy (ok. 1200 kolumn w próbie) i POBRZEŻE REAL (169 kolumn) są tylko wypisywane, nie sprawdzane (opis commita `68a51fe` „cover per landscape type in range” mówi więcej, niż test sprawdza).

| Miara | Cel | REAL | GAMEPLAY |
|---|---|---|---|
| lesistość lądu | 26–34% | 32,3% | 33,1% |
| bory w lesie | 45–58% | 52,0% | 56,2% |
| typy świeże w lesie | 50–70% | 59,9% | 58,8% |
| olsy i łęgi w lesie | 3–6% | 5,0% | 5,5% |
| SANDR | 55–75% (raport ekologii) | 57,2% | 57,9% |
| WYSOCZYZNA | 15–30% (raport ekologii) | 22,5% | 20,2% |
| RÓWNINA (staroglacjalna) | 18–35% (mazowieckie, łódzkie, podlaskie 21–31%, `docs/research/05` §6) | 23,8% | 29,9% |
| POGÓRZE | 25–45% (małopolskie, podkarpackie 29–38%) | 34,0% | 28,9% |
| BESKIDY | 50–80% (tylko wypisywane) | 65,4% | 57,4% |
| POBRZEŻE | 10–40% (pas brzegu z plażą i wydmami; w trybie N tylko 40–43%) | 21,9% (169 kolumn, bez sprawdzenia) | 14,3% |

W trybie N bez zmian (lesistość 99,2% / 97,4%, bory w lesie wnętrza sandru 88,5% / 86,6%). Kadry maski: `docs/m2/m2_forest_mask_present_day_50km_biomes.png` i `_5km` (REAL, skraj sandru), `docs/m2/m2_gameplay_forest_mask_present_day_20km_biomes.png` i `_5km` (GAMEPLAY wokół (0, 0)).

**Ten sam teren i to samo siedlisko potencjalne (`PresentDayModeTest`, szybki).** W 5 obszarach na skalę (8 × 8 chunków: obszary testu gleb i rzeki transektów) plan powierzchni w obu trybach ma identyczny teren i wodę modelu, a wierzch różni się tylko w mikroreliefie §7.3 (kałuże i kępy idą za biomem; 436 i 463 kolumny z 81 920); każda kolumna ma ten sam STL, zespół i strefę (w trybie D dochodzi tylko szpaler), a kolumna leśna trybu D ma biom trybu N. Wyjątek: prześwity wrzosowiska na wydmach trybu N (w trybie D STL Bs).

**Dyski wanilii usunięte (krok 6).** `disk_sand`, `disk_clay` i `disk_gravel` (znane od S6, §7.6) zamieniały z wody trawę i ziemię brzegu na piasek, glinę i żwir do 6 bloków od wody. W trybie D wyszło to na transekcie małej rzeki REAL: bez łęgu zostały tam tylko ziołorośla przy brzegu, a 40% ich kolumn było gołych (112 kolumn piasku, 35 żwiru). Bez dysków goły grunt spadł do 12,1%. Dna mają własne łaty żwiru i gliny (`SoilBlocks.bed`).

**Pole jako ugór.** Trawa pola żółtsza (kolor 0xAFB85E zamiast 0x85BD5E) i runo z rżyska (`short_dry_grass` 24, trawa 12, mak 4, chaber 4, pokrycie 45% zamiast 30%), bo z góry pole i łąka miały ten sam zielony kolor. Uprawy przyjdą w M8.

**Wioski (decyzja M2-12, tabela §10.1).** Wioski i posterunki także w lasach, w obu trybach. Wymagania w jarze 26.3: `village_plains` i `village_taiga` to struktury jigsaw z biomem sprawdzanym w punkcie startu (`Structure.isValidBiome`), `terrain_adaptation: beard_thin`, start na `WORLD_SURFACE_WG`; zestaw `minecraft:villages` (`random_spread`, co 34 chunki, odstęp 8) próbuje w każdej komórce wszystkich pięciu typów wioski, więc typ wynika z biomu startu; `pillager_outposts` (co 32 chunki, częstość 0,2, strefa wykluczenia 10 chunków od wiosek). Generator Polski nie dodawał „brody” pod budynki (wanilijny `Beardifier` działa tylko w generatorze szumowym), więc domy stały na terenie bez wyrównania; recenzja pokazała domy wiszące do 4 bloków nad stokiem, więc brodę dodała już runda 1 poprawek (niżej).

**Test w grze `PresentDayClientGameTest` (`-Pgametest=present_day`, obie skale, ok. 6 min, maszyna spokojna).** Świat w trybie D (aktualizacja wymiarów jak przycisk „Gotowe” ekranu opcji) startuje, `validate()` przechodzi, a po zapisaniu i ponownym otwarciu świat jest dalej w trybie D (kolumna pola ma dalej biom `arable_land`). `UiAndCommandsClientGameTest` klika przełącznik i sprawdza, że generator dostaje tryb D.
- **Transekty §4.6 w trybie D** (te same miejsca co w S7): REAL duża rzeka wysokie 79,1%, trawa i małe kwiaty 0%, goły grunt 5,1%; mała rzeka (same ziołorośla, 455 kolumn) 74,3% / 0% / 12,1%; potok 80,8% / 0,5% / 6,7%; GAMEPLAY 84,0% / 0% / 6,3%, 71,8% / 2,4% / 10,5% (z łęgiem jesionowo-olszowym i szpalerem) i 87,6% / 0% / 3,6%.
- **`/locate structure #minecraft:village`** z 5 punktów ((0, 0), (±4000, ∓4000), (±8000, ±8000)), wyszukiwanie wanilii z promieniem polecenia: REAL średnio 634 bloki (97–1296), 50 ms w trybie D i 85 ms w trybie N (najdłużej 159 ms); GAMEPLAY średnio 349 bloków (64–781), 21 ms i 38 ms (najdłużej 74 ms). Siatka wiosek jest ta sama w obu trybach, zmienia się typ: np. w (304, 192) wioska tajgowa w borze mieszanym (N) i równinna na polu (D). Test wymaga wioski z każdego punktu w czasie < 5 s.
- **Zrzuty** (stan S8; w rundzie 1 poprawek przeliczone, a `s8_present_day_forest_village_*` zastąpione przez `s8_present_day_village_*`, niżej) w `docs/m2/gra/`: `s8_present_day_mosaic_realistic.png` (las mieszany, łąka wilgotna i pole po ok. 1/3), `s8_present_day_mosaic_gameplay.png` (wioska równinna na łące, pola, buczyna i jedlina), `s8_present_day_forest_village_realistic.png` (wioska równinna w lesie mieszanym), `s8_present_day_forest_village_gameplay.png` (wioska tajgowa w borze bażynowym przy polu), `s8_natural_forest_village_realistic.png` (wioska tajgowa w borze mieszanym, tryb N) i `s8_natural_forest_village_gameplay.png` (tryb N; domy prawie całkiem pod koronami).

**Co zostaje (stan S8, poprawiony w rundzie 1):** pola z uprawami, miedze i polskie wsie (M8); stopnie terenu na polach GAMEPLAY (teren, M5); budżety czasu (BIOMES, FEATURES) w trybie D w S10. Proste odcinki granic las–pole (np. pas lasu wzdłuż doliny w `s8_present_day_mosaic_realistic.png`) przypisaliśmy w S8 prostej granicy siedliska; recenzja pokazała, że przyczyną były skoki P_las na progach STL (DGW świeże/wilgotne/bagienne wzdłuż stoków dolin) i binarne flagi kontekstu, które maska zamieniała w krawędzie lasu, podczas gdy F prawie się nie zmienia na kilkuset metrach. Poprawka w rundzie 1.

#### Runda 1 poprawek S8 (2026-10-09)

Recenzja zgłosiła 4 problemy poważne i 10 drobnych; wszystkie potwierdziliśmy (bez fałszywych alarmów), poprawki niżej. Złoty test bez zmian i bez list.

**Maska bez krawędzi na granicach siedlisk (`ForestCover`).**
- **F ma trzecią oktawę „lasków”**: fala 600 m·k (GAMEPLAY 300 m), waga 0,6 (wobec 1 i 0,5 dłuższych oktaw), nowe pole z nową solą `habitat.forest_cover.woodlots` (tablica kwantyli liczy się z nowego wzoru, więc F zostaje jednostajny). Daje laski i kępy śródpolne na wysoczyźnie i tyle zmienności F na kilkuset metrach, że krawędzie idą za F, a nie za granicami STL.
- **P_las ciągłe przez progi wilgotności:** w biomie, który ma na danej żyzności typ świeży i wilgotny, P_las miesza oba liniowo w paśmie DGW 2,0 ± 0,7 m; DGW do mieszania drga ±0,4 m szumem kontekstu o fali 150 m·k (nowa sól `habitat.forest_cover.context`, warstwy przesunięciem współrzędnych). Na siedliskach żyznych (LM, L) strona wilgotna schodzi dalej do P_las olsu (DGW 1,3 → 0,3 m), a ols na glebie mineralnej liczy tę samą funkcję, więc granica ols/LMw nie jest krawędzią lasu (na torfie ols ma stałe 0,10). Potencjalny STL i klasyfikator bez zmian.
- **Stok doliny płynnie i z przerwami:** zamiast flagi `valleySlope` (wcięcie > 2 m) kontekst rośnie z wcięciem poniżej terenu sprzed dolin od 1 do 6 m (wcięcie drga ±1 m), a jego siła zmienia się wzdłuż doliny szumem kontekstu o fali 700 m·k (pełna na ok. 30% stoków, żadna na ok. 30%, pośrodku płynnie: smoothstep kwantyla 0,1–0,7), więc szerokość lasu na stoku się zmienia. Zalesione skarpy nie tworzą już ciągłych pasów stałej szerokości po obu stronach każdej doliny.
- **Biomy nieleśne:** progi szumu wariantów (1,5 km·k) drgają jak progi żyzności etapu H (±0,05, szum 150 m·k), więc ich izolinie nie są długimi łukami; wybór pole/łąka świeża na siedliskach żyznych bierze szum działek o fali 400 m·k (łąki w małych działkach zamiast owali ok. 1 km), na płaskim 94% pola, na stokach 40% (wcześniej wszystkie stoki to łąka); próg nachylenia 5° (GAMEPLAY 8° w blokach, jak kontekst nachylenia) drga ±2° szumem 200 m·k (bez prostych pasów ugoru wzdłuż prostych den dolin); siedliska wilgotne liściaste (Lw, LMw, LMb, Ol) poza lasem to w połowie łąka wilgotna, w połowie osuszone pole (`D_MOIST_MEADOW` 0,5), łęg wiązowy 50% łąki zamiast 80%. Łąki świeże i wilgotne zajmowały ok. 20% lądu REAL i 28% GAMEPLAY (w Polsce trwałe użytki zielone to ok. 10% powierzchni); teraz 13,3% i 16,0% (`BiomeSharesTest` sprawdza 8–18%).
- **Krawędzie na granicach (sonda recenzenta rozszerzona: krawędź las/teren otwarty w trybie D w odległości ≤ 2 m od granicy (biom, STL) trybu N; wiersze 1 m, okna mozaiki recenzenta i 200 losowych wierszy 4 km na skalę):**

| | REAL okna | REAL losowe | GAMEPLAY okna | GAMEPLAY losowe |
|---|---|---|---|---|
| przed (S8) | 69% | 65% | 62% | 78% |
| po rundzie 1 | 63% | 50% | 62% | 61% |
| w tym granica dna doliny lub pasa olszy przy potoku | 33% | 23% | 0% | 29% |
| przypadkowo (granica STL w 2 m od dowolnego miejsca) | 8,7% | 2,1% | 2,2% | 5,3% |

  Granice wilgotności wewnątrz biomu (LMśw/LMw/LMb z recenzji) zniknęły z listy częstych przyczyn (**korekta w rundzie 2: to było błędne**, miara z tej tabeli nie łapała krawędzi na stromej rampie P obok granicy STL; niżej, „Runda 2 poprawek S8”). Zostały granice, które są granicami form terenu i decyzji §4.5: krawędź dna doliny (łęg w dnie P 0,06–0,15, stok wyżej, to też obraz Polski: łąki w dnach, las na skarpach), pas olszy szarej przy potoku (zawsze las) i granica pięter w górach GAMEPLAY (buczyna karpacka 0,8 i jedlina 0,6 wobec grądu Lśw 0,1 na pogórzu). Kadry 3,2 km i 800 m (sonda `MaskMap`): pas lasu wzdłuż doliny w (−5120, 4096) REAL rozpadł się na płaty lasu wchodzące na wysoczyznę i laski (pas stałej szerokości został tylko przy samej krawędzi dna, ok. 350 m), równoległe pasy po obu stronach dolin w (3584, 6656) zniknęły, prosty pas ugoru w (6170, −1020) i klin lasu w (3524, 572) GAMEPLAY też; w (−5120, 4096) zostały też wąskie (3–12 m) odcinki lasu na skarpie dna dużej rzeki, tam gdzie DGW klasyfikatora skacze między sąsiednimi kolumnami (np. przy (−5140, 4096) z 1,1 do 9,3 m), czego maska nie wygładza.
- **Udziały po rundzie 1** (`BiomeSharesTest`, REAL / GAMEPLAY): lesistość 31,9 / 32,2%, bory w lesie 53,7 / 57,7%, typy świeże 61,3 / 59,5%, olsy i łęgi 4,7 / 5,8%, łąki 13,3 / 16,0% lądu; SANDR 58,2 / 57,7%, WYSOCZYZNA 21,5 / 19,1%, RÓWNINA 23,2 / 27,9%, POGÓRZE 32,9 / 26,3%, BESKIDY (tylko wypisywane) 67,6 / 53,4%, POBRZEŻE 24,3% (169 kolumn, tylko wypisywane) / 18,2%. Tryb N bez zmian. Kadry maski i CSV w `docs/m2` przeliczone.

**Grunt pod strukturami (`surface/StructureGround`, `ChunkSurface.adjustTop`).** Odpowiednik wanilijnego `Beardifier` na wierzchu planu: `fill()` zbiera elementy struktur, które odwołują się do chunka (jak `Beardifier.forStructuresInChunk`, w zasięgu 12 bloków). Pod obrysem sztywnego elementu struktury z `beard_thin` albo `beard_box` (wioski, posterunki) wierzch gruntu to `groundY − 1` (dolna warstwa szablonu stoi na gruncie, teren nad podłogą zdjęty), a wokół obrysu, do 12 bloków, grunt jest dosypany albo ścięty do najwyżej 1 bloku wysokości na blok odległości (euklidesowej od obrysu); przy sprzecznych granicach kilku elementów decyduje najbliższy. Element dalej niż 24 bloki od wierzchu kolumny się nie liczy (głębokie struktury). Dosypana kolumna ma grubszą pokrywę z tym samym profilem gleby, ścięta zachowuje grubość pokrywy. `ChunkHabitats` i heightmapy biorą wierzch po tej zmianie. Teren modelu i złoty test bez zmian (struktury nie należą do modelu). Test jednostkowy `StructureGroundTest`.

**Maska roślinności.** Ta sama klasa liczy dla każdej kolumny odległość (Chebyshev, do 15) do obrysu najbliższego budynku (sztywny element) i najbliższego elementu w ogóle (także ulicy, która idzie za terenem, więc liczy się bez sprawdzania wysokości). Maska jedzie w `ChunkHabitats` (pole `structures`, w zapisie opcjonalne; brak = brak struktur). Drzewostan sadzi pień co najmniej 6 bloków od obrysu budynku (więcej niż promień korony) i 3 od ulicy; rośliny blokowe nie stają na obrysach elementów, a rośliny z featurą (krzewy, kłody) stają co najmniej 4 bloki od elementu.

**Bez wiosek w lasach trybu D (decyzja M2-17, domyślna, do potwierdzenia).** W trybie D wioska (`#minecraft:village`) nie startuje w biomie leśnym (`polishforests:forests`): mixin `ChunkGeneratorStructureMixin` zawęża w `ChunkGenerator.tryGenerateStructure` predykat biomów startu (`PolandChunkGenerator.structureBiomes`); wanilia próbuje wtedy kolejnego typu wioski z zestawu, jak przy każdym innym złym biomie. Tagi biomów (M2-12) bez zmian, więc w trybie N wioski dalej stoją także w lasach. Skutek: w trybie D wioski tajgowe praktycznie nie powstają (ich biomy to bory, jedlina i buczyna karpacka), a wiosek jest mniej (`/locate` z 5 punktów: REAL średnio 685 bloków wobec 634 w trybie N, GAMEPLAY 579 wobec 349).

**Test w grze (`PresentDayClientGameTest`, obie skale, ok. 10 min).** Biom wioski czyta się w środku elementu startowego (tam sprawdza go wanilia), nie w rogu chunka z `/locate` (w S8 np. „wet_meadow” przy wiosce, która wystartowała gdzie indziej). W trybie D żadna z 5 wiosek na skalę nie startuje w lesie. Każda znaleziona wioska (5 na tryb i skalę, 20 wiosek) przechodzi kontrolę `villageCheck` w obrysie wioski: 0 pni na ulicy (kłoda na `dirt_path` poza elementami budynków i dekoracji wioski, które mają własne drzewa), 0 liści pod dachami budynków (poza koronami drzew dekoracji wioski, jak w wanilii: 4–16 bloków w 7 z 20 wiosek), 0 roślin na podłogach (pod dachem, na bloku innym niż gleba; klomby szablonów stoją na trawie i bielicy pod okapem) i 0 kolumn podłogi budynku z ≥ 2 blokami bez podparcia pod spodem (największa szczelina 0–1). Przed poprawką recenzent zmierzył w wioskach leśnych 13–52 pnie na ulicach, do 231 bloków liści pod dachami i 10 z 23 domów wiszących do 4 bloków. `/locate structure #minecraft:village`: REAL D średnio 685 bloków i 63 ms (wszystkie 5 na polu lub łące), REAL N 634 bloki i 78 ms; GAMEPLAY D 579 bloków i 55 ms, GAMEPLAY N 349 bloków i 43 ms (najdłużej 216 ms; maszyna spokojna).
- **Zrzuty** (`docs/m2/gra/`): `s8_present_day_village_<skala>.png` (wioska trybu D z góry; zastępuje `s8_present_day_forest_village_*`), `_street` (z ulicy na wysokości oczu w stronę środka) i `_downhill` (od strony spadku terenu, w stronę budynku, którego podłoga leży najwyżej nad gruntem 6 bloków dalej), tak samo `s8_natural_forest_village_<skala>[_street|_downhill].png` (wioska leśna trybu N, teraz z polaną wokół domów; skarpy brody na stoku GAMEPLAY widać jako tarasy), `s8_present_day_mosaic_<skala>.png` po zmianie maski (REAL: okno przeniosło się do (−5120, 6656): pole, łąka świeża i las mieszany po ok. 1/3, krawędź lasu łukowata; GAMEPLAY bez zmian miejsca).

**Dyski wanilii a tryb N (M2-16).** `HabitatsClientGameTest` nie zwalnia już piasku, gliny i żwiru przy wodzie jako „dysku wanilii” (dysków nie ma od S8), więc sprawdza gleby brzegów w pełni. Na drzewie rundy 1 (tryb N, obie skale): `-Pgametest=habitats` przechodzi (gleby w 11 miejscach: REAL 2259 z 2259 kolumn zgodnych z planem, GAMEPLAY 1685 z 1690, 5 pod głazami narzutowymi; półka, schodki i rozlewy bez zmian; BIOMES nizina 0,14 / 0,13 ms, Beskidy 0,26 / 0,25 ms, rzeka 0,17 / 0,17 ms na chunk REAL / GAMEPLAY, w budżetach M2-10), `-Pgametest=vegetation` też (transekty §4.6 REAL 77,3% / 0,4% / 6,7%, 74,7% / 2,1% / 9,4%, 74,9% / 1,2% / 8,5%, GAMEPLAY 71,3% / 2,4% / 9,7%, 71,9% / 2,4% / 10,5%, 87,1% / 0% / 3,4% (wysokie razem / trawa i małe kwiaty / goły grunt), liście do opadnięcia 0, `HABITAT_MISS` 0).

**Odstępstwa i decyzje do potwierdzenia:** M2-16 (dyski usunięte także w trybie N), M2-17 (bez wiosek w lasach trybu D), M2-18 (gęstość wiosek w trybie N), M2-19 (P_las łęgów i olsów obniżone) w `docs/00-decyzje-do-podjecia.md` E.

**Co zostaje:** schodkowe skarpy brody (1 blok na blok) przy budynkach na stromych stokach GAMEPLAY wyglądają jak tarasy (zrzuty `_downhill`), łagodniejszy spadek albo brzeg z szumem do oceny w S10; krawędzie lasu na granicy dna doliny, pasa olszy przy potoku i pięter w górach (decyzje §4.5, opis wyżej) oraz skoki DGW klasyfikatora między sąsiednimi kolumnami (wąskie paski lasu na skarpach den), których maska nie wygładza, a zmiana klasyfikatora wykracza poza tę rundę; budżety czasu w trybie D w S10.

#### Runda 2 poprawek S8 (2026-10-09)

Recenzja rundy 1 zgłosiła 2 problemy poważne i 10 drobnych. Oba poważne potwierdziliśmy: sondy recenzenta (`EdgeProbe`, `EdgeKinds`, `MoistEdges`, `RowDump`, ziarno 20260927, 40 losowych wierszy 4 km na skalę) dały na drzewie `ae999ec` dokładnie jego liczby (REAL 206 krawędzi, 42,7% na konturze F; GAMEPLAY 362, 33,7%). Wszystkie 10 drobnych to zgłoszenia z rundy 1, naprawione już w rundzie 1, co sprawdziliśmy w kodzie i w docs (fałszywe alarmy, niżej). Złoty test bez zmian i bez list; klasyfikator siedlisk, STL potencjalny, zespoły i strefy bez zmian (tryb N: 56 kadrów podglądu bajt w bajt takich samych, udziały trybu N 99,2 / 97,4% bez zmian).

**Maska: P_las lądu suchego z ciągłych współrzędnych (`ForestCover.dryLand`).** Runda 1 zrobiła P_las ciągłe w DGW, ale DGW na stoku doliny zmienia się o ok. 0,2 m na blok, więc rampa miała 5–10 m, a krawędź leżała na konturze DGW równoległym do doliny; skoki na krawędzi dna, na granicach pięter i żyzności zostały. Korekta opisu rundy 1: zdanie, że granice LMśw/LMw/LMb „zniknęły z listy częstych przyczyn”, było błędne. Miara rundy 1 (krawędź ≤ 2 m od granicy STL trybu N) nie łapała krawędzi leżących na stromej rampie P kilka metrów od granicy. Miara recenzenta (|ΔP| > 0,02 między sąsiadami 1 m) pokazała 57% (REAL) i 66% (GAMEPLAY) krawędzi na skokach P. Teraz P_las wszystkich biomów strefowych i górskich (poza łęgami w dnie, olsem na torfie, olszyną górską, borem bażynowym i kosodrzewiną) nie zależy od biomu ani STL, tylko od ciągłych współrzędnych kolumny:
- **Żyzność:** P_las typu strefowego miesza się z P_las klasy po drugiej stronie najbliższego progu bogactwa r. Próg jest ten sam, z tym samym drganiem, co w `Fertility.compute`; pasmo ma ±0,05 r, a r to kwantyl szumu 2 km·k, więc pasmo ma kilkadziesiąt metrów. Kontekst stoku doliny (tylko siedliska żyzne) i cel krawędzi dna też mieszają się przez próg.
- **Wilgotność:** pasma DGW (świeże/wilgotne 2,0 ± 0,7 m, wilgotne → ols 0,3–1,3 m) są poszerzone o wzniesienie terenu na 30 m (`P_EDGE_WIDTH_K`) i przesuwane o ±(0,4 m + wzniesienie na 40 m) szumem kontekstu 150 m·k (warstwa 1). Szerokości są w metrach (blokach) w obu skalach, bo w GAMEPLAY stoki są bardziej strome na blok. Spadek na stoku doliny szacujemy z wysokości nad krawędzią dna i odległości za krawędzią dna (siatka zgrubna terenu nie zna dolin), najwyżej 0,6. DGW do mieszania ma ciągłe progi wody zawieszonej (`Moisture.dgwForBlend`: bez progu do −1 m wklęsłości, 1,0 m przy −2 m, 0,3 m przy −3,5 m); sam klasyfikator zostaje przy progach −1,5 i −3 m.
- **Góry:** w paśmie gór (waga typów górskich 0,35–0,65) P_las to jedna funkcja wysokości: 0,23 poniżej 350 m, 0,8 od 700 m, 0,9 powyżej regla górnego (1050–1250 m); wysokość drga ±60 m szumem 150 m·k. Granice buczyny karpackiej, jedliny, świerczyny i grądu pogórza nie są więc krawędziami lasu. W dnach dolin buczyna i jedlina mają dalej 0,15.
- **Krawędź dna:** nad dnem doliny z ciekiem P_las lądu suchego rośnie od 0,1 na siedliskach żyznych (łąki i pastwiska na dolnych stokach) i 0,3 na ubogich (bory sandrów schodzą do dna) do swojej wartości na 0,5 m + wzniesienie na 20 m ponad FLOOR_H. Działa tylko w dolinie: siła rośnie z wcięciem 0,5–2,5 m, więc płaski teren nisko nad korytem i skraj zasięgu sieci rzecznej (tam rząd cieku spada do 0) jej nie mają. Początek rampy przesuwa się w górę stoku o 0 do (0,5 m + wzniesienie na 20 m) szumem (warstwa 14, tylko w górę, więc rampa zawsze zaczyna się przy dnie). Łęgi poza dnem (wysięki, źródliska, pasy cieków na stokach) i ols na glebie mineralnej biorą P_las lądu suchego, które przy niskim DGW jest bliskie olsu.
- **Wydmy śródlądowe** działają wagą wysokości wydmy (2–6 m) zamiast flagi `INLAND_DUNES` (4 m). Morena czołowa zostaje flagą, bo model nie wystawia wysokości wału.
- **Olszyna górska:** za pasem przy potoku (P_las 1, decyzja §4.5) P_las spada na 0,75 szerokości pasa do 0,09 (jak łęg jesionowo-olszowy obok). Łąki w szerokich dnach przechodzą płynnie z szerokością dna i wysokością.
- **Kalibracja** (`Calibration`, „PRESENT_DAY mode: forest mask”): Bs 0,9; Bśw 0,85; Bw i BMw 0,8 (bagienne ubogie jak wilgotne); BMśw 0,3; LMśw 0,3; LMw 0,5; Lśw 0,12; Lw 0,35; łęg wierzbowo-topolowy 0,10, wiązowo-jesionowy 0,08, jesionowo-olszowy 0,09, ols 0,10 (M2-19); pogórze 0,23. `P_OTHER`, `P_UPLAND_FIR` i `P_BOGGY_CONIFEROUS` usunięte, bo P_las nie czyta już typów wyżynnych i górskich.

| Miara (`BiomeSharesTest`) | Cel | REAL | GAMEPLAY |
|---|---|---|---|
| lesistość lądu | 26–34% | 33,2% | 32,0% |
| bory w lesie | 45–58% | 54,1% | 56,8% |
| typy świeże w lesie | 50–70% | 65,7% | 63,3% |
| olsy i łęgi w lesie | 3–6% | 3,8% | 5,3% |
| łąki (lądu) | 8–18% | 14,9% | 17,4% |
| SANDR / WYSOCZYZNA / RÓWNINA / POGÓRZE | 55–75 / 15–30 / 18–35 / 25–45% | 61,5 / 22,7 / 25,9 / 28,7% | 56,0 / 19,5 / 28,7 / 26,2% |
| BESKIDY / POBRZEŻE (tylko wypisywane) | 50–80 / 10–40% | 64,6 / 23,7% | 50,4 / 18,8% |

**Krawędzie.** Miara recenzenta: krawędź las/teren otwarty z |ΔP| > 0,02 między sąsiadami 1 m to „skok P”, reszta to kontur F; rodzaje jak w `EdgeKinds`.

| | REAL przed | REAL po | GAMEPLAY przed | GAMEPLAY po |
|---|---|---|---|---|
| skoki P, 40 wierszy recenzenta | 57,3% | 32,5% | 66,3% | 39,5% |
| skoki P, 400 wierszy | 55,6% | 30,9% | 64,4% | 39,7% |
| w tym wilgotność i ols (ten sam STL lub żyzność) | 13,6% | 8,5% | 17,7% | 13,4% |
| w tym krawędź dna | 12,9% | 5,2% | 16,5% | 8,4% |
| w tym pas olszy szarej | 13,4% | 15,3% | 13,0% | 12,8% |
| w tym piętra górskie | 6,6% | 0,2% | 2,6% | 0,0% |
| w tym żyzność | 9,1% | 1,7% | 14,2% | 4,9% |

Okna 1 km recenzenta:
- (−5120, 4096) REAL: skoki wilgotności 42 → 16%, krawędź dna 3 → 11%, kontur F 48 → 73%.
- (3584, 6656) REAL (podwójne pasy po obu stronach dolin): kontur F 51 → 84%. Pasy zniknęły, lasy to płaty F, a dna dolin to łąki.
- (46139, 49833) REAL (pogórze z potokami): kontur F 54 → 47%. Zostają pas olszy szarej (23%) i krawędź den (21%), a proste odcinki krawędzi idą za prostymi odcinkami dolin (teren, M5).
- (3072, 3072) GAMEPLAY: piętra 85 → 4%, kontur F 6 → 71%, pas olszy 21%.

W GAMEPLAY reszta skoków to głównie strome rampy: te same zmiany P przypadają tam na mniej bloków. *Korekta (S8b, po zgłoszeniu recenzji rundy 2):* strome rampy zostają także w REAL, na stokach szerokich dolin (niżej, „Co zostaje”).

**Wioski w trybie D: tani test struktur i wyszukiwanie (decyzja M2-17).** Reguła rundy 1 działała tylko w `ChunkGenerator.tryGenerateStructure`. `StructureCheck.canCreateStructure` (tani test `/locate`, map odkrywców i `getStructureGeneratingAt`) budował kontekst z samym tagiem biomów, więc każdy kandydat w lesie przechodził, a wyszukiwanie ładowało chunk do STRUCTURE_STARTS i nie znajdowało startu. Wioska tajgowa w trybie D nie może powstać wcale (wszystkie biomy `has_structure/village_taiga` to lasy), a kartograf równinny ma w 26.3 handel „mapa wioski tajgowej” z promieniem 100 (ok. 40 tys. komórek siatki). Teraz:
- mixin `StructureCheckMixin` (`@WrapOperation` na `findValidGenerationPoint` w `canCreateStructure`) daje temu testowi kontekst z predykatem `PolandChunkGenerator.structureBiomes`, tym samym co `ChunkGeneratorStructureMixin`;
- `PolandChunkGenerator.findNearestMapStructure` pomija struktury, które w tym świecie nie mogą wystartować (`canStart`: wioska, której biomy poza lasami nie należą do biomów źródła), a gdy nie zostaje żadna, od razu zwraca `null`. `/place structure` nie korzysta z tych ścieżek (jego predykat przepuszcza każdy biom), więc dalej stawia wioskę gdziekolwiek;
- `PresentDayClientGameTest.taigaVillages`: w trybie D `/locate structure minecraft:village_taiga` (promień 100) i wyszukiwanie mapy `#minecraft:on_taiga_village_maps` (promień 100, z pomijaniem znanych) nic nie znajdują, w obu skalach w 0,0 ms (limit testu 2 s). W trybie N `/locate` znajduje wioskę tajgową (REAL 796 bloków, 18 ms; GAMEPLAY 97 bloków).

**Test w grze na drzewie rundy 2 (`-Pgametest=present_day`, obie skale, 9 min 45 s, PASS, maszyna spokojna).**
- Transekty §4.6 w trybie D (wysokie razem / trawa i małe kwiaty / goły grunt): REAL 79,3 / 0 / 5,0%, 74,3 / 0 / 12,1%, 81,3 / 0,6 / 6,1%; GAMEPLAY 84,0 / 0 / 6,3%, 71,6 / 2,4 / 10,5%, 87,1 / 0 / 4,0%.
- `/locate structure #minecraft:village`: REAL D średnio 740 bloków i 52 ms (wszystkie 5 wiosek na polu lub łące), N 634 bloki i 72 ms; GAMEPLAY D 579 bloków i 54 ms, N 349 bloków i 35 ms (najdłużej 203 ms).
- Wszystkie 20 wiosek przechodzą `villageCheck` (0 pni na ulicach, 0 liści pod dachami, 0 roślin na podłogach, 0 wiszących podłóg; największa szczelina podłogi 0–1).
- Zrzuty trybu D (`s8_present_day_mosaic_*`, `s8_present_day_village_*`) przeliczone. Mozaika REAL leży teraz w (6144, 0) (grąd 32%, pole 42%, łąki 26%), GAMEPLAY w (4096, 1536). Zrzuty trybu N bez zmian, bo tryb N się nie zmienił. Kadry maski D i CSV w `docs/m2` przeliczone.

**Drobne zgłoszenia: wszystkie naprawione już w rundzie 1 (sprawdzone):**
- zwolnienie „dysków wanilii” w `HabitatsClientGameTest` usunięto w rundzie 1 (komentarz przy sprawdzaniu gleb); gametesty `habitats` i `vegetation` przeszły wtedy w trybie N, a runda 2 trybu N nie zmienia;
- biom wioski czyta się w środku elementu startowego (`startCenter`, runda 1);
- decyzje M2-16 … M2-19 są w `docs/00` E jako „domyślne, do potwierdzenia”;
- `P_OTHER` był w §3.4.1 od rundy 1, a teraz jest usunięty z kodu;
- progi wariantów i próg nachylenia z drganiem, działki łąk 400 m·k i przerwy kontekstu stoku z rundy 1 są w kodzie;
- opis Beskidów i POBRZEŻA REAL jako „tylko wypisywane” jest w §3.4.1 od rundy 1;
- zrzuty z ulicy i od strony spadku oraz asercje wiosek są w teście od rundy 1.

Uwaga „(poza dyskami wanilii)” w opisie S6 w BRIEF opisuje stan S6; ma teraz dopisek, że od S8 dysków nie ma.

**Co zostaje (do decyzji użytkownika, M2-20 w `docs/00` E; problem przetrwał 2 rundy, zasada B3).** Krawędzie lasu nadal leżą na skokach P w ok. 31% (REAL) i 40% (GAMEPLAY) przypadków (400 wierszy, miara recenzenta):
- pas olszy szarej przy potokach (P_las 1 z §4.5), 15,3% (REAL) i 12,8% (GAMEPLAY) wszystkich krawędzi;
- **strome rampy wilgotności i olsu na stokach szerokich dolin, w obu skalach** (korekta S8b; wcześniej opis wymieniał strome rampy tylko w GAMEPLAY, a skokom DGW przypisywał ok. 1% krawędzi): 8,5% krawędzi REAL i 13,4% GAMEPLAY, druga co do wielkości grupa. Przyczyna: `rise()` szacuje spadek stoku jako (H − miękki poziom koryt − FLOOR_H) / (odległość od koryta − połowa szerokości dna); na stoku szerokiej doliny odległość od koryta to ok. 986 m przy połowie dna ok. 258 m, więc szacunek wynosi ok. 0,011 (wzniesienie na 30 m = 0,34 m), a prawdziwy spadek ok. 0,22 m na blok (nachylenie z siatki zgrubnej 0,3°). Poszerzenie i przesunięcie pasm DGW prawie nie działa: DGW zmienia się o ok. 0,2 m na blok, rampa P ma ok. 10 m i leży na konturze DGW. Sonda spadków recenzenta (`GradProbe`, rev_s8r2): spośród 160 krawędzi wilgotności i olsu REAL 119 (74%) ma lokalny |dH| na blok ponad 3 razy większy od szacunku, 65 ponad 10 razy; GAMEPLAY 118 z 371 (3 razy) i 28 (10 razy). Przykład recenzenta: wiersz REAL z = 4225, x od −5092 do −5079 — przed rundą 2 P 0,26 → 0,50 → 0,10, po niej 0,31 → 0,45 → 0,12 na 13 m przy DGW 2,72 → 0,05 i F 0,244 → 0,241; krawędź przesunęła się tylko o 1 blok (z DGW 0,84/0,64 na 0,64/0,44). W oknie 288 m wokół (−5120, 4096) REAL krawędzie na skokach P spadły tylko z 53,6 do 46,4%, głównie pary biomu olsu (`ALDER_CARR`) i lasu mieszanego bagiennego (STL LMb, `BOGGY_MIXED_BROADLEAVED`) o P 0,30 i 0,23;
- **rampy przy krawędzi dna na siedliskach żyznych** (korekta S8b): krawędź dna to 5,2% krawędzi REAL i 8,3% GAMEPLAY; na siedliskach ubogich bór 0,3 nad łęgiem 0,09, a na żyznych rampa od 0,1 też bywa za stroma, np. łęg jesionowo-olszowy poza dnem przy (−5126, 3953) REAL: P 0,10–0,19 o ok. 0,03 na blok, spadek 0,11 przy szacunku 0,003 (27 z 99 krawędzi dna REAL ma spadek ponad 3 razy większy od szacunku);
- skoki DGW klasyfikatora między sąsiednimi kolumnami, proste odcięcia głowy dna doliny (dno zaczyna się skokiem przy połowie wygaszenia głowy) i proste odcinki dolin.

Ekotony S8b (zasada Z10, §8.8) nie zmieniają maski: łagodzą wygląd każdej krawędzi (drzewa mieszają się w pasie 8 m·k, płaszcz krzewów i okrajek ziół, gleby w pasie 4 m·k), ale krawędź dalej leży tam, gdzie wypada skok P.

Warianty:
- (a) przyjąć: wzór „las na stokach i sandrach, łąki w dnach, olsza wzdłuż potoków” jest zgodny z dzisiejszą Polską; na stokach szerokich dolin krawędzie zostają jednak strome i idą za konturem DGW (koszt zerowy);
- (b) usunąć zasadę „pas olszy szarej zawsze las” i dać pasowi P_las z rampą: krawędzie pasa poszłyby za F, ale potoki górskie w polach straciłyby olszyny (mała zmiana);
- (c) wygładzić skoki DGW i głowy dolin w klasyfikatorze: zmienia siedliska potencjalne i tryb N, więc do M5 razem z terenem (duży koszt);
- (d) **lokalny spadek dla `rise()`** (nowy wariant, S8b): zamiast szacunku z odległości od koryta prawdziwy spadek H. Dwie drogi: (d1) różnica centralna H z 1–2 dodatkowych próbek modelu na kolumnę lasu w trybie D — dokładna, ale próbka kosztuje 12–17 µs, więc TERRAIN i BIOMES trybu D wzrosłyby o kilkadziesiąt procent (poza budżetami M2-10/M2-11); (d2) wygładzone pole H − miękki poziom koryt na siatce zgrubnej (64 m·k lub rzadziej, z pamięcią jak `CoarseTerrainField`) i spadek z jego gradientu — ok. 0,06 próbki na chunk REAL i 0,25 w GAMEPLAY, koszt pomijalny, ale wąskie doliny GAMEPLAY dostaną za mały spadek. Zmienia tylko tryb D; potem pomiar miarą |dP| na blok i sondą `GradProbe`. Koszt pracy: średni (nowe pole z pamięcią, testy determinizmu, kalibracja udziałów trybu D od nowa).

Rekomendacja: (d2) jako osobny krok przed S10 albo (a), jeśli wygląd krawędzi po ekotonach S8b wystarczy; (c) w M5.

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

### 3.5.1 Stan po S5 (2026-10-08)

**Próba datagenu (R1).** Najpierw powstał jeden biom (`dry_pine_forest`), jeden feature (`tree/scots_pine`) i jeden placed feature w `DataGeneratorEntrypoint.buildRegistry` (`RegistrySetBuilder.add` dla `Registries.FEATURE`, `PLACED_FEATURE` i `BIOME`) z zapisem przez `FabricDynamicRegistryProvider` (fabric-data-generation-api-v1 27.2.4 w Fabric API 0.161.0+26.3). Datagen 26.3 działa, więc **plan awaryjny z §13 R1 (szablon JSON dekodowany kodekiem) nie był potrzebny**. Dwie pułapki: w bootstrapie wolno tylko odwoływać się do elementów wanilii, a nie czytać ich wartości (`Trying to access unbound value 'minecraft:pine'`), więc drzewa `tree/*` budujemy w kodzie, zamiast kopiować konfiguracje wanilii; walidacja wanilii (`VanillaRegistries.validateThatAllBiomeFeaturesHaveBiomeFilter`) wymaga `minecraft:biome` na końcu każdego placed featura z listy biomu, więc nasze placed featury najwyższego poziomu go mają (palety drzew nie, §8.1). W 26.3 featury są danymi: interfejs `Feature` z rekordami konfiguracji, typy to `MapCodec` w `BuiltInRegistries.FEATURE_TYPE`, a dawny `CONFIGURED_FEATURE` to `Registries.FEATURE`.

**Pełny datagen (Z8).** Wszystko pochodzi z enumów pakietu `habitat` i z `worldgen/feature/BiomeDecoration` (listy kroków, Z5). Wynik jest w `src/main/generated` w repozytorium (129 plików): 36 biomów, 26 featurów (20 drzew `tree/<gatunek>` i 6 dyspozytorów), 31 placed featurów (20 drzew, 6 dyspozytorów, `deep_lava_lake`, 4 nośniki mączki), 28 tagów wanilii (8 ogólnych i 20 `has_structure/*`), 5 własnych, 1 tag Serene Seasons i `lang/en_us.json`, `lang/pl_pl.json`. Ręczne pliki `lang/*.json` i `tags/worldgen/biome/polish_climate.json` z `src/main/resources` usunięte. `./gradlew checkDatagen` liczy skróty plików `src/main/generated` (bez `.cache`), uruchamia `runDatagen` i kończy się błędem, gdy któryś plik doszedł, zniknął albo się zmienił (porównanie przed i po, niezależne od gita).

- **Biomy** (`HabitatBiome`, nowe kolumny enuma dopisane bez zmiany kolejności stałych): `temperature` (T_bazowa z §6.1: 0,70; łęgi i olszyna 0,685; ols, bór bagienny, torfowisko niskie i szuwar 0,67; torfowisko wysokie 0,65; morze, zalew, plaża, wydmy i bór bażynowy 0,72), `downfall` z §6.3, jawne kolory wody (§10; biomy lądowe: kolor jeziora, mokradła i łęgi: kolor rzeki, torfowiska: kolor jeziora dystroficznego, biomy górskie: kolor potoku), trawy, liści i suchych liści (wartości startowe z colormapy wanilii w punkcie (T, downfall)), niebo z formuły wanilii dla T. Atrybuty 26.3: `audio/background_music` (lasy `music.overworld.forest`; bory, jedlina, świerczyna i kosodrzewina `old_growth_taiga`; ols, bór bagienny i torfowiska `swamp`; hala `meadow`), `gameplay/increased_fire_burnout` (ols, bór bagienny, łęgi, olszyna, torfowiska, szuwar), `visual/water_fog_color` #3B2A1A w `dystrophic_lake`, `gameplay/natural_mob_spawns` z §10: lasy wilk 5, lis 8, królik 4 bez zwierząt hodowlanych; łąki, pole i hala owca 12, świnia 10, kura 10, krowa 8; ols (las z żabą 10, bez czarownic), torfowiska i szuwar żaba 10; wrzosowisko, wikliny, plaża i wydmy królik 4; rzeka i potok łosoś 5 i topielce 30; morze dorsz 10 i topielce 30, bez kałamarnic; jeziora i zalew bez ryb; wszędzie nietoperze, kałamarnice świecące w jaskiniach i potwory jak w wanilijnym `forest`, bez `surface_slime_spawn_chance`. `carvers: []`.
- **Listy kroków (§8.1, Z5):** krok 1 `polishforests:deep_lava_lake` (`minecraft:lake_lava`, rzadkość 9, `height_range` −54..0, `environment_scan` i próg względem `OCEAN_FLOOR_WG` jak w wanilii), krok 2 `minecraft:amethyst_geode` (głazy narzutowe w S7), krok 3 lochy, krok 6 rudy i dyski w kolejności wanilii bez `underwater_magma`, krok 9 `tree_stand`, `deadwood`, `understory`, `waterside_zones`, `ground_layer`, `aquatic_plants` i na końcu jeden z `bone_meal/{forest,meadow,wetland,mountain}`, krok 10 `freeze_top_layer`. Nośniki mączki to `count 0` z kwiatami wanilii z tagu `can_spawn_from_bone_meal` (las `wildflower`, łąka `flower_meadow`, mokradło `flower_default`, góry `flower_plain`); własne kwiaty i dopisanie do tagu w S7.
- **Dyspozytory (§8.2):** sześć typów zarejestrowanych w `FEATURE_TYPE` (`worldgen/feature/ModFeatures`) z ostateczną listą kroku 9. Działa tylko `tree_stand` w wersji podstawowej: paleta JSON (`TreePalette`: reguła na biom, drzew na chunk i skład z §8.5, gatunki z flagą zasięgu tylko w kolumnach z flagą, „Bk lub Św” jako buk z gatunkiem zastępczym świerkiem), czysta funkcja `feature/plan/TreeStandPlan` (od rundy 1 recenzji: jeden wzór kandydatów dla całego świata, opis niżej), drzewa stawiane na wierzchu gruntu z `ChunkHabitats`, bez kolumn pod wodą. Bez stref, luk, rampy zasięgu i wariantów zespołów (S7). Pozostałe pięć to `PendingFeature` (nic nie robią).
- **Drzewa `tree/<gatunek>`** (20 gatunków drzewiastych z `Species`, krzewy `shrub/*` w S7): kształty wanilii z korą i liśćmi z §8.4 (sosna: `straight` 6+4, `pine_foliage`, kora i igły świerka; świerk: wanilijny; jodła: `straight` + `spruce_foliage` z korą `pale_oak`; brzoza: wanilijna; dąb, lipa, jesion, wiąz, klony: `fancy_oak`; buk: `fancy` z korą `pale_oak`; grab: `straight` 6+2 i `blob` r2–3, kora `pale_oak`; olsza czarna: `dark_oak_log` i `dark_oak_leaves`; olsza szara: `pale_oak_log`; wierzba: `swamp_oak` z pnączami; topola: kształt topoli wanilii z liśćmi dębu; jarząb: mały `blob`), placed feature z `would_survive minecraft:oak_sapling`, bez filtra biomu.
- **Tagi i struktury:** `is_overworld` (36), `stronghold_biased_to` (30 lądowych), `is_forest` (18), `is_river` (`river`, `stream`), `is_beach` (`beach`), `is_ocean` (`sea`), `is_mountain` (`montane_beech_forest`, `montane_spruce_forest`, `dwarf_pine_scrub`, `alpine_grassland`, zgodnie z §10 i decyzją M2-C), `water_on_map_outlines` (jeziora i zalew; morze i rzeki przez `#is_ocean`, `#is_river`); bez `is_hill` i `is_taiga`. Jawne `has_structure/*` w tabeli §10.1. Serene Seasons: `lesser_color_change_biomes` z §6.3. Własne: `polish_climate` (36), `forests` (18), `pine_forests` (6), `waterside` (8), `montane` (5).
- **Lang:** nazwy biomów `biome.polishforests.<id>` z `HabitatBiome` (polska i angielska nazwa), nazwy celów `find` z tabeli w `ModLanguageProvider` (datagen pada, gdy cel z `PolishForestsCommands.Target` nie ma nazwy), cele `lower_montane` i `upper_montane` z granicą `AltitudinalBelts.UPPER_MONTANE` („below/from 1150 m”, „poniżej/od 1150 m”), reszta tekstów jak przed S5; w notce ekranu opcji: tryb krajobrazu już wybiera biomy.

**`PolandBiomeSource` (przepisany).** Kodek `RegistryOps.retrieveGetter(Registries.BIOME)`, holdery z `ModBiomeKeys` w kolejności enuma, `collectPossibleBiomes` = 36. Presety mają `"biome_source": {"type": "polishforests:poland"}`. Stan to jeden niezmienny `Binding` (ziarno, model, klasyfikator, skala pionowa) w polu `volatile`; `PolandChunkGenerator.model(seed)` tworzy model i klasyfikator (`HabitatClassifier(seed, skala, settings.mode())`) i wiąże je w `createState`; ponowne wiązanie z innymi parametrami daje ostrzeżenie w logu. `createResolver` czyta wiązanie w lambdzie i ma bufor wątku 256 wpisów mapowany po (qx, qz) i związany z wiązaniem; `createResolverForChunk` klasyfikuje 16 kolumn z góry i ignoruje qy; `findClosestBiome3d` robi jedną próbkę na kolumnę spirali wanilii (paczkami po 2048 kolumn klasyfikowanych równolegle; wygrywa pierwsze trafienie w kolejności spirali, więc wynik jest jak przy przeszukiwaniu po kolei) i zwraca Y punktu startu; `addDebugInfo` (F3, wpis „chunk_generation_stats”) pokazuje biom, STL, strefę, zespół, glebę, DGW, O, P, T i tryb. `PolandPresetEditor` tworzy nowe źródło (`PolandBiomeSource.create(lookup biomów)`) dla nowego generatora. `PolandSettings` ma pole `version` (brak = 1, presety i nowe światy 2) i `mode()`; od rundy 1 recenzji kodek zapisuje zawsze wszystkie pola i cały obiekt `settings` (opis niżej). Profile klimatu (`BiomeClimate.profile`) biorą T_bazowa z enuma: `sea` nigdy nie zamarza, `river` i `stream` jak rzeki; zastępcze biomy M1 i `BiomeClimate.PLACEHOLDERS` zniknęły.

**BIOMES.** Biom nie zależy od Y (Z2), więc `PolandChunkGenerator.createBiomes` (dla naszego źródła) zapisuje 16 kolumn w kontener biomów jednej sekcji i daje każdej sekcji kopię, zamiast ok. 8400 zapisów wanilii (131 sekcji × 64 w REAL). Pierwsza wersja z `fillBiomesFromNoise` kosztowała 0,514 ms na chunk (REAL), z czego zapisy palet ok. 0,4 ms. Liczniki `BIOME_NANOS`, `BIOME_CLASSIFY_NANOS`, `BIOME_CHUNKS`; tryb `stages` loguje też klasyfikację w `fill()` (`CLASSIFY_NANOS`), czas drzewostanu (`TreeStandFeature.NANOS`, `CHUNKS`, `TREES`) i `HABITAT_MISS`.

**`ChunkHabitats`.** Rekord (kody 256 kolumn, wierzch gruntu, lustro wody, O i P ze środka chunka) zapisywany w `fill()` jako załącznik Fabric (`ModFeatures.CHUNK_HABITATS`; od rundy 1 recenzji trwały, z kodekiem `ChunkHabitats.CODEC`), czytany przez dyspozytory przez `level.getChunk(cx, cz)`, usuwany w `spawnOriginalMobs`, więc pełne chunki zapisują się bez niego. Brak załącznika (dziś tylko proto-chunk zapisany przez wersję z nietrwałym załącznikiem) uruchamia przeliczenie z modelu i licznik `HABITAT_MISS`. Klasyfikacja w `fill()` liczy się do `SAMPLE_NANOS` (osobno `CLASSIFY_NANOS`).

**Testy.** `BiomeJsonTest` (36 plików, T ≤ 0,8 i równe enumowi, kolory, atrybuty i spawny według profili, `carvers` puste, nazwy PL i EN, tagi `is_overworld`, `has_structure/trial_chambers`, `polish_climate`, `is_forest`, `stronghold_biased_to`, `is_mountain`, kopalnie w każdym biomie), `FeatureOrderTest` (listy wszystkich biomów identyczne z `BiomeDecoration` i między sobą poza nośnikiem mączki, nośnik ostatni w kroku 9, brak usuniętych featurów i featurów inline, każde id istnieje: nasze w `src/main/generated`, wanilii w jarze gry, paleta drzew wskazuje istniejące drzewa), `TreeStandPlanTest` (determinizm; 20 tys. chunków grądu z paletą 9: 9,000 drzewa na chunk, buk 39,9% przy wadze 40%, najmniejsza odległość kandydatów 4 bloki przy c = 5; buk tylko z flagą; bez drzew pod wodą; 0,3 drzewa na chunk w torfowisku wysokim), `PolandClimateTest` (profile 36 biomów, tag = 36 biomów, presety bez listy biomów). W grze nowy `HabitatsClientGameTest` (`-Pgametest=habitats`, także w `all`), w obu skalach (ziarno 20260927). Testy w grze `ui` (ekran opcji z nowym źródłem w `PolandPresetEditor`, komendy) i `climate` przechodzą bez zmian; w `climate` z Serene Seasons zimą pada śnieg na wszystkich 2125/2304 (REAL) i 879/1142 (GAMEPLAY) sprawdzanych kolumnach sandru i wysoczyzny, bo żaden biom moda nie jest na czarnej liście SS (odstępstwo S1 o zastępczych `river`, `beach` i `ocean` zniknęło).

**Wyniki w grze (obie skale).** Świat startuje, `generator.validate()` (kolejność featurów) bez wyjątku, źródło ma 36 biomów `polishforests:*`, każdy z profilem klimatu. `level.getBiome` = klasyfikator w 8 miejscach o różnych biomach (spirala od (0, 0), środek kwarty o jednolitym otoczeniu 3 × 3, przy gruncie, 40 bloków wyżej i przy dnie świata): REAL `mixed_forest`, `ash_alder_forest`, `oak_hornbeam_forest`, `mixed_pine_forest`, `fresh_pine_forest`, `bog_woodland`, `alder_carr`, `moist_pine_forest`; GAMEPLAY `mixed_forest`, `oak_hornbeam_forest`, `ash_alder_forest`, `mixed_pine_forest`, `bog_woodland`, `fresh_pine_forest`, `lowland_beech_forest`, `upland_fir_forest`. F3 klienta pokazuje `polishforests:mixed_forest`, a wiersze generatora siedlisko (zrzuty `docs/m2/gra/s5_habitats_f3_realistic.png`, `s5_habitats_f3_gameplay.png`; widać drzewostan z palety, zrzuty zastąpione po rundzie 1 recenzji: bez alej wzdłuż granic chunków). `/locate structure` od (0, 0): twierdza 1731 m (REAL) i 1733 m (GAMEPLAY), 20 i 3 ms; kopalnia 97 m; komnaty prób 240 m (świat testu musi mieć włączone struktury, bo `TestWorldBuilder` domyślnie je wyłącza). `/locate biome polishforests:oak_hornbeam_forest`: 115 m w 3–10 ms (REAL) i 45 m w 0–5 ms (GAMEPLAY), cel < 2 s spełniony. Najgorszy przypadek, biom nieobecny w promieniu 6,4 km (`dwarf_pine_scrub`, cała spirala 401 × 401 kolumn): po kolei 2,35 s (REAL) i 5,6–5,7 s (GAMEPLAY, zimne pamięci siatek na 6,4 km), z paczkami równoległymi 0,63 s i 2,0 s. BIOMES (64 nowe chunki w nowym obszarze niziny, czas każdego wywołania na jednym wątku, cztery przebiegi): 0,123–0,150 ms na chunk (REAL), 0,142–0,155 ms (GAMEPLAY), z czego próbki i klasyfikacja 16 kolumn 0,111–0,148 ms; budżet 0,2 ms spełniony na nizinie. Gór i rzeki S5 nie mierzył; pomiar z rundy 1 recenzji (niżej) pokazał przekroczenie w Beskidach. `ChunkHabitats`: 0 braków na 100 chunków z terenem w obu skalach, żaden pełny chunk nie trzyma załącznika.

**Etapy chunka (`-Pgametest=stages`, A/B w jednej sesji: baza `2cfc53b` z biomami zastępczymi i S5; czas ściany 64 chunków na etap, ms; pierwszy obszar mierzony razem ze startem świata, ok. 1070 chunków w tle).**

| Obszar | BIOMES baza / S5 | TERRAIN baza / S5 | FEATURES baza / S5 | LIGHT baza / S5 |
|---|---|---|---|---|
| REAL nizina | 1175 / 872–882 | 1283 / 932–1243 | 247 / 2447–2485 | 306 / 952–1058 |
| REAL Beskidy | 181 / 177–188 | 337 / 350–353 | 387 / 401–405 | 482 / 526–552 |
| REAL rzeka | 138 / 133–135 | 213 / 220–225 | 282 / 349–433 | 337 / 427–500 |
| GAMEPLAY nizina | 964 / 587–814 | 1435 / 1226–1521 | 306 / 1865–2325 | 378 / 437–532 |
| GAMEPLAY Beskidy | 147 / 136–142 | 350 / 332–355 | 362 / 428–435 | 469 / 526–527 |
| GAMEPLAY rzeka | 108 / 109–119 | 231 / 215–232 | 286 / 372–383 | 330 / 441–454 |

BIOMES i TERRAIN nie wydłużyły się (klasyfikacja w `fill()` kosztuje 0,04–0,10 ms na chunk). W tym trybie, przy pełnym obciążeniu wątków generacji i z zimnymi pamięciami siatek w dalekich obszarach (BIOMES pierwszy dotyka siatek obszaru), jedno wywołanie BIOMES trwa średnio 0,20–0,61 ms (najwięcej w Beskidach REAL), a w pomiarze jednowątkowym `HabitatsClientGameTest` 0,12–0,16 ms; budżet §3.6 (jeden wątek) liczymy z tego drugiego. FEATURES rośnie tam, gdzie w bazie był biom bez drzew: nizina miała zastępcze `plains`, a w trybie roślinności naturalnej jest las (9,1 drzewa na chunk w REAL, 8,9 w GAMEPLAY). Sam drzewostan kosztuje 1,25–2,24 ms na chunk (3,3–9,1 drzewa), więc wzrost FEATURES niziny o ok. 2,2 s na 64 chunki to głównie konkurencja z chunkami startu świata, których dekoracja i światło też podrożały (potwierdzone w rundzie 1 recenzji pomiarem po starcie świata, niżej); w obszarach z drzewami już w bazie (Beskidy, rzeka) FEATURES rośnie o 4–54%, LIGHT o 10–48%. Budżet FEATURES ≤ 1,0 × M1 należy do S7 (dekoracja), a cały chunk ≤ 48 ms do S10; oba wymagają porównania z bazą na tym samym pokryciu lasem (do decyzji w S7: rzadsze lub tańsze kształty zamiast `fancy_oak`). Braków `ChunkHabitats` w pomiarze: 0.

**Odstępstwa S5:**
- **Tryb domyślny w presetach już w S5** (w planie krok S8): źródło biomów musi znać tryb, więc presety mają `"agriculture": false` (roślinność naturalna, decyzja M2-B), tak samo `PolandSettings.DEFAULT` i domyślna wartość kodeka. Tryb D działa w źródle biomów z wartościami startowymi `ForestCover` (kalibracja w S8).
- **`#is_mountain`** dostają cztery biomy górskie według §10 i decyzji M2-C, choć `m2_steps.json` (S5) pisał „bez is_mountain”; obowiązuje ten plan. `is_hill` i `is_taiga` nie są potrzebne: posterunki, portale górskie i kopalnie w górach dają `#is_mountain`, wioska tajgowa ma jawną listę.
- **Drzewa `tree/*` mają już kształty z §8.4** (w planie S7), bo podstawowy drzewostan potrzebuje drzew. Krzewy `shrub/*`, wariant niski sosny, zastępstwa `mega_spruce` i `super_birch` oraz `max_water_depth` w S7.
- **Nośniki mączki z kwiatami wanilii** (własne kwiaty w S7).
- **Test „runDatagen nie zmienia plików”** to zadanie Gradle `checkDatagen`, nie test JUnit (`runDatagen` uruchamia klienta gry); `BiomeJsonTest` i `FeatureOrderTest` czytają `src/main/generated`.
- **Polecenia `/locate` sprawdza `HabitatsClientGameTest`** (wykonanie komendy z przechwyceniem komunikatu i czasu), nie `UiAndCommandsClientGameTest`; cele `find biome` i `find zone` to S9.
- **F3:** wiersze siedliska są w wpisie F3 „chunk_generation_stats” (generator i źródło biomów, jak w wanilii), który trzeba włączyć w ustawieniach F3 (F3 + F6); sam biom `polishforests:*` jest w domyślnym wpisie „biome”.
- **Wierzch suchych kolumn według siedliska już w S5** (runda 1 recenzji; w planie S6): tymczasowa reguła `PolandChunkGenerator.surfaceBlock` zamiast pełnych gleb z §7 (opis niżej).
- **BIOMES w Beskidach ponad budżet 0,2 ms** (runda 1 recenzji): do decyzji użytkownika, warianty niżej.

**Runda 1 poprawek recenzji S5 (2026-10-08).** Wszystkie zgłoszenia sprawdzone; fałszywych alarmów nie było (po dwa zgłoszenia o siatce drzewostanu i o czasach `/locate biome` w BRIEF to te same problemy z dwóch recenzji).

- **Drzewostan bez siatki i alej.** Stary plan (całkowite oczko c = 16/⌈√n⌉, kandydat w wewnętrznych 50% oczka) dawał pnie tylko w 36 z 256 kolumn chunka (x, z ∈ {1, 2, 6, 7, 11, 12} przy c = 5) i pas 4 kolumn bez pni na każdej granicy chunka. Nowy `TreeStandPlan` ma jeden wzór kandydatów dla całego świata, niezależny od granic chunków. Siatka komórek 2 × 2 bloki wyrównana do świata ma jednego kandydata w losowej kolumnie komórki (każda kolumna jednakowo prawdopodobna) i losowy znacznik z (ziarno świata, komórka). Kandydat zostaje, jeśli żaden zostawiony kandydat o niższym znaczniku nie stoi bliżej niż 3 bloki (hamowanie sekwencyjne w kolejności znaczników, liczone lokalnie i rekurencyjnie, więc sąsiednie chunki widzą przy granicy tych samych kandydatów). Zostaje średnio 16,65 kandydata na chunk (stała `DENSITY`, zmierzona w teście na 100 tys. chunków). Kandydat staje się drzewem z szansą n_b / 16,65 według biomu swojej kolumny, więc 16,65 to górna granica palety (największa w §8.5: 12). `TreeStandPlanTest`: średnio 9,05 drzewa na chunk przy palecie 9 (buk 39,9% przy wadze 40%) i 12,08 przy palecie 12. Na obszarze 48 × 48 chunków każdy rząd lokalny x i z dostaje od −3% do +4% średniej liczby pni (wcześniej 4 rzędy z 16 były zawsze puste). Żadne dwa pnie nie stoją bliżej niż 3 bloki, także przez granicę chunka, a odległość do najbliższego pnia przy granicy chunka (3,37 bloku) jest taka sama jak w środku (3,38). Plan kosztuje kilkadziesiąt µs na chunk.
- **„Bk lub Św”** (bór mieszany 10, las mieszany 15): wpis palety ma pole `alternative`. Poza zasięgiem buku jego wagę bierze świerk, jeśli jest w zasięgu. Wcześniej waga była podzielona na 5 + 5 i 8 + 7, co na nizinach dawało ok. 5% i 8%. Test: gdy w zasięgu jest tylko świerk, dostaje on całe 40% wagi buku z palety testowej.
- **Ustawienia świata zapisują się w całości.** `optionalFieldOf(nazwa, domyślna)` pomija przy zapisie wartości równe domyślnej. Dlatego świat REAL z S5 nie miał w `world_gen_settings.dat` obiektu `settings`, a świat GAMEPLAY miał tylko `scale` i `version`. Teraz kodek `PolandSettings` zapisuje każde pole, a generator zawsze zapisuje obiekt `settings` (`PolandSettings.GENERATOR_FIELD`). Odczyt:
  - brak `settings` to świat M1 (`LEGACY_M1`: skala realistyczna, tryb „dzisiejsza Polska”, `version` 1);
  - brak `version` to 1;
  - brak `agriculture` to `true` w wersji 1 (jak w M1) i `false` od wersji 2.

  Sprawdzone w zapisanych światach gametestu (oba mają wszystkie pola) i w `PolandSettingsTest`. Światy z commitów S5 (`97d30c4`, `a9ac76a`) w skali realistycznej odczytają się teraz jako M1, czyli w trybie „dzisiejsza Polska”; to tylko światy testowe.
- **`ChunkHabitats` jako trwały załącznik.** Recenzent zmierzył 1,2–1,8% braków w sesji z powrotami (budżet < 0,5%), bo proto-chunk zapisany między TERRAIN i FEATURES tracił nietrwały załącznik. Załącznik ma teraz kodek (kody i spakowane poziomy jako tablice int, O, P). Pełne chunki nadal zapisują się bez niego, bo usuwamy go w `spawnOriginalMobs`. `HabitatsClientGameTest.revisit` sprawdza to dwa razy:
  - zapis i odczyt proto-chunka TERRAIN tak, jak robi to gra (`SerializableChunkData`), zachowuje siedliska;
  - lot do nowego obszaru, odlot o 4 km i powrót 96 bloków dalej: 0 braków na 914 chunków z terenem w obu skalach, 19–21 (REAL) i 22 (GAMEPLAY) załączniki odczytane z zapisanych proto-chunków.
- **Łęgi i olszyny na suchych dnach koryt.** Generator kładł piasek na suchych kolumnach podłoża `RIVERBED`, a drzewa mają filtr `would_survive minecraft:oak_sapling`, więc olszyna górska REAL nie miała drzew. Tymczasowa reguła `PolandChunkGenerator.surfaceBlock` (do pełnych gleb S6): suche `RIVERBED` zostaje piaskiem tylko w biomach wodnych, na plaży, na wydmie białej i w strefach łach (`POINT_BAR`, `GRAVEL_BAR`). Pod pozostałymi siedliskami dostaje trawę, a na glebach torfowych błoto. Model terenu się nie zmienia, złoty test przechodzi bez listy.
- **Plaża i wydma biała z trawą.** Ta sama reguła daje piasek na wierzchu suchych kolumn `beach` i `white_dune` także przy podłożu `SAND`; wcześniej 15–23% tych kolumn miało trawę.
- **Wierzba biała** ma `ignore_vines`. Bez tego pnącza sąsiednich wierzb blokowały pień (`TreeFeature.getMaxFreeTreeHeight`) i ok. 40% wierzb w łęgu wierzbowo-topolowym nie wyrastało.
- **Spis drzew w grze** (`HabitatsClientGameTest.census`): 5 × 5 pełnych chunków wokół miejsc z recenzji i trzech miejsc porównania biomów. Liczy pnie na wierzchu gruntu i porównuje je z paletą razy liczba suchych kolumn biomu; próg ±20% dla biomów z co najmniej 40 oczekiwanymi drzewami. Wyniki (pnie / oczekiwane):
  - REAL: olszyna górska 180 / 193,5 (wcześniej 0), łęg jesionowo-olszowy 447 / 440,6, łęg wierzbowo-topolowy 161 / 173,9 (wcześniej ok. 71% palety), las mieszany 241 / 263,4, grąd 190 / 189,2;
  - GAMEPLAY: olszyna górska 154 / 169,9, łęg wierzbowo-topolowy 166 / 161,7, łęg jesionowo-olszowy 63 / 73,3, las mieszany 429 / 447,3, grąd 160 / 144,6, ols 26 / 26,4;
  - wierzch: trawa na 0 z 6230 (REAL) i 0 z 5099 (GAMEPLAY) suchych kolumn plaży i wydmy białej; piasek na 422 z 38 018 i 171 z 31 164 suchych kolumn leśnych (łachy).
- **Kolor wody jeziora dystroficznego** (i boru bagiennego, torfowiska wysokiego) to #3A3326 zamiast #5A4A2E, bo z góry woda wyglądała jak błotnista równina. Pełna kalibracja kolorów nadal w planie (§10).
- **Obóz łąkowy** (`abandoned_camp_meadow`) także na wrzosowisku i hali. Struktury w trybie roślinności naturalnej opisuje §10.1.
- **`.cache` datagenu** nie trafia już do jara (`processResources { exclude '.cache/**' }`; sprawdzone w `build/libs`).
- **Dokumentacja:** czasy `/locate biome` w BRIEF poprawione, §14 pkt 5 oznaczony jako zastąpiony decyzją M2-C.

**BIOMES w trzech obszarach na skalę** (`HabitatsClientGameTest.biomesPerChunk`): jeden wątek, 64 nowe chunki z zimnymi pamięciami siatek obszaru, potem 64 następne na wschód; trzy przebiegi, ms na chunk, zimne / ciepłe.

| Obszar | REAL | GAMEPLAY |
|---|---|---|
| nizina (−50 000, 30 000) | 0,121–0,144 / 0,097–0,118 | 0,135–0,159 / 0,145–0,155 |
| Beskidy (obszar `beskids` trybu `stages`) | 0,238–0,287 / 0,210–0,223 | 0,244–0,259 / 0,228–0,248 |
| duża rzeka (obszar `river`) | 0,167–0,175 / 0,170–0,173 | 0,180–0,196 / 0,161–0,171 |

Budżet 0,2 ms jest dotrzymany na nizinie i przy rzece, **w Beskidach nie**: do 1,44 × budżetu na zimno i 1,05–1,24 × na ciepło. Prawie cały czas to 16 próbek modelu z klasyfikacją, 12–17 µs na kolumnę w Beskidach w grze. W `costTest` sama próbka Beskidów to 7,2 µs (REAL) i 11,1 µs (GAMEPLAY); w grze dochodzą zimne pamięci i JIT. Budżet z §3.6 zakładał próbkę 5–10 µs z M1, a przy budżecie próbki D1 (12 µs REAL, 16 µs GAMEPLAY w Beskidach) już 16 × 16 µs = 0,256 ms. Test pilnuje 0,2 ms na nizinie i 0,5 ms (ochrona przed regresją) w Beskidach i przy rzece (od S6b według decyzji M2-10: 0,2 ms na nizinie i przy rzece, 0,3 ms w Beskidach; §3.6).

**Do decyzji użytkownika:**
1. Przyjąć budżet BIOMES w górach jako 16 × budżet próbki D1 (ok. 0,3 ms), a 0,2 ms zostawić dla niziny. Koszt zerowy; cały chunk i tak mierzy S10 (≤ 48 ms, BIOMES to < 1% z tego).
2. Przyspieszyć próbkę w Beskidach (profil `sample` w górach, np. pamięć węzłów szczytów albo siatek). Teren się nie zmienia, ale wynik jest niepewny; ok. 1–2 dni.
3. Mniej próbek na chunk (np. 4 i rozszerzenie na kwarty). Wtedy biom przestaje być klasyfikatorem kolumny (Z1, Z2); odradzane.

Rekomendacja: wariant 1.

**Etapy chunka po rundzie 1.** A/B: baza `2cfc53b` z biomami zastępczymi i obecny kod. Tryb `stages` czeka teraz, aż start świata skończy generację (ok. 800–920 chunków w 9–18 s). Czas ściany 64 chunków w ms, dwa przebiegi.

| Obszar | BIOMES baza / teraz | TERRAIN baza / teraz | FEATURES baza / teraz | LIGHT baza / teraz |
|---|---|---|---|---|
| REAL nizina | 164–168 / 159–160 | 168–185 / 162–164 | 255–264 / 355–392 | 309–313 / 434–446 |
| REAL Beskidy | 192–224 / 202–236 | 339–346 / 378–393 | 392–401 / 471–480 | 529 / 569–577 |
| REAL rzeka | 126–137 / 128–130 | 206–227 / 210–230 | 280–291 / 377–408 | 346–347 / 492–495 |
| GAMEPLAY nizina | 174–178 / 164–179 | 168 / 156–163 | 315–324 / 374–375 | 367–377 / 432–449 |
| GAMEPLAY Beskidy | 145–155 / 138–142 | 335 / 341–342 | 376–378 / 423–440 | 475 / 514–516 |
| GAMEPLAY rzeka | 115–117 / 102–118 | 216–242 / 224–231 | 278–280 / 407–428 | 328–330 / 457–464 |

- Pomiar niziny z S5 (FEATURES 247 / ok. 2450 ms) zafałszowała równoległa generacja startu świata.
- Po odczekaniu FEATURES rośnie o 12–48%, czyli o ok. 1–2 ms na chunk. Tyle kosztuje drzewostan: 1,66–2,50 ms na chunk przy 4,1–9,9 drzewa, nieco ponad 1–2 ms z §8.2.
- LIGHT rośnie o 4–43% (liście drzew), najwięcej przy rzece i na nizinie REAL, gdzie baza miała mało drzew.
- TERRAIN w Beskidach REAL rośnie o 10–15%, w GAMEPLAY i na nizinie się nie zmienia. Klasyfikacja w `fill()` to tylko 0,07 ms na chunk; resztę trzeba wyjaśnić przy budżecie TERRAIN w S6/S10.
- Budżety FEATURES ≤ 1,0 × M1 i LIGHT rozstrzyga S7 na tych liczbach (rzadsze lub tańsze kształty niż `fancy_oak`).
- BIOMES w trybie `stages` (wiele wątków, obszar dotykany pierwszy raz) trwa średnio 0,26–0,85 ms na wywołanie, najdłużej w Beskidach REAL.

### 3.6 Budżet kosztu (jeden wątek)

| Pomiar | M1 | Cel M2 |
|---|---|---|
| `sample` | 5–10 µs | +≤ 5% |
| klasyfikacja | – | ≤ 0,5 µs |
| etap BIOMES | 16 próbek i ok. 8400 wywołań `biomeFor` | ≤ 0,2 ms na chunk na nizinie i przy rzekach, ≤ 0,3 ms w górach (M2-10) |
| TERRAIN | 1,3–2,6 ms model + 1,5–3 ms wypełnianie | ≤ 1,10 × M1 (z półką i mikroreliefem), średnia całego obszaru jak D1 (M2-11) |
| FEATURES | biomy zastępcze | ≤ 1,0 × M1 (cel −20%) przy tym samym pokryciu lasem (M2-14) |
| cały chunk | 44 ms | ≤ 48 ms |
| `PACK_FALLBACKS` / `HABITAT_MISS` | – | < 1% sekcji / < 0,5% chunków |

**Decyzje użytkownika o budżetach (2026-10-09, wpisane w S6b; `docs/00-decyzje-do-podjecia.md` E).**
- **M2-10, BIOMES w górach:** w Beskidach i innych obszarach górskich budżet wynosi ok. 0,3 ms na chunk (16 × budżet próbki D1: 16 próbek po 12–17 µs, S5 zmierzył 0,21–0,29 ms), na nizinie i przy rzekach zostaje 0,2 ms. `HabitatsClientGameTest` sprawdza nizinę i dużą rzekę z progiem 0,2 ms, a Beskidy z progiem 0,3 ms (wcześniej Beskidy tylko w raporcie).
- **M2-11, TERRAIN:** budżet 1,10 × M1 liczymy dla całego obszaru, jak D1 (średni koszt próbki i wypełniania na obszarze), a nie w najdroższym miejscu. Przy dużej rzece w skali REAL 1,28–1,33 × M1 (pomiar S6; w sesjach z szybszym M1 do 1,48, §7.6) jest akceptowane. Pomiar całego obszaru i całego chunka (≤ 48 ms) robi S10; TERRAIN nie ma progu w teście (pomiar `-Pgametest=stages` tylko na spokojnej maszynie, A/B w jednej sesji).
- **M2-14, FEATURES (domyślne, do potwierdzenia):** budżet 1,0 × M1 liczymy przy tym samym pokryciu lasem (las do lasu: baza `2cfc53b` z `forest` zamiast zastępczych `plains` i `meadow`), nie wobec bezleśnych biomów zastępczych sprzed M2. Pomiar S7 przy tym samym pokryciu: dekoracja 0,72–1,00, etap 0,83–1,09 (REAL Beskidy, w granicach szumu; §8.7). Próg w teście nie istnieje (pomiar `stages`).

**Po S8b (2026-10-10, ekotony Z10, §8.8):** dekoracja chunka +6–11% (0,14–0,26 ms), etap FEATURES +1–5%, BIOMES i TERRAIN bez zmian (A/B z bazą `2271c65` w jednej sesji); przy tym samym pokryciu lasem (M2-14) szacunkowo 0,78–1,10, Beskidy REAL ok. 10% ponad budżetem (rozrzut median ok. ±10%), do pomiaru całego chunka w S10.

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
- **Stan po S8:** po kalibracji łęg wierzbowy 0,15, łęg jesionowo-olszowy 0,13, reszta dna 0,06, ols 0,15 (olsy i łęgi 5,0–5,5% lasu zamiast 10–11%), pas olszy szarej clamp(2W; 5k; 20k); szczegóły i transekty §4.6 w trybie D w §3.4.1.
- **Runda 1 poprawek S8:** łęg jesionowo-olszowy 0,09, ols 0,10 (na glebie mineralnej przechodzi płynnie w P_las siedliska wilgotnego, §3.4.1), łęg wiązowy poza lasem 50% łąki wilgotnej i 50% pola (wcześniej 80/20); olsy i łęgi 4,7 / 5,8% lasu (REAL / GAMEPLAY), decyzja M2-19.
- **Runda 2 poprawek S8:** łęg wierzbowo-topolowy 0,10, wiązowo-jesionowy 0,08 (mniejsze skoki między strefami dna); łęgi poza dnem i ols na glebie mineralnej biorą P_las lądu suchego; nad dnem rampa od 0,1 (żyzne) lub 0,3 (ubogie); za pasem olszy szarej P_las spada do 0,09 na 0,75 szerokości pasa. Olsy i łęgi 3,8 / 5,3% lasu (§3.4.1, „Runda 2 poprawek S8”).

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
- W GAMEPLAY (ok. 0,4 bloku na metr) lustro + 1 m często daje ten sam blok co lustro. Wtedy półka nic nie zmienia. Test sprawdza bloki, nie metry. *Pomiar S6:* w obu skalach część brzegów i den dolin leży 2–3 bloki nad lustrem (przy dużej rzece GAMEPLAY półka S6 obniżyła 661 kolumn), więc półka działa także w GAMEPLAY.
- *Od rundy 1 recenzji S6 (§7.6):* zamiast obniżania całych stref do lustra rampa 1 bloku na kolumnę od najbliższej wody modelu, najwyżej 3 bloki od wody, bez progów na granicach stref i chunków. *Od rundy 2:* rampa nie schodzi poniżej lustra żadnej wody modelu w odległości Manhattan do 9 bloków, także za granicą chunka, więc przy schodkowej wodzie brzeg zostaje na wyższym lustrze.
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

- Kałuża powstaje tylko wtedy, gdy 4 sąsiednie kolumny w chunku mają wierzch wyżej. Na skrajnej kolumnie chunka zamiast wody jest błoto. *Od rundy 1 recenzji S6 (§7.6):* sąsiada spoza chunka generator próbkuje, więc kałuże na skraju chunka trzymają wodę tak samo często jak w środku (bez siatki chunków).
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

### 7.6 Stan po S6 (2026-10-08)

**Co zrobiono.** Nowy pakiet `worldgen/surface` (czysta Java, bez klas gry):
- `ChunkSurface` to plan powierzchni chunka. Dla 256 kolumn trzyma ostateczny wierzch gruntu, lustro wody, profil gleby (wierzch, warstwa 1 i 2 z głębokościami), początek skały i flagi (`SHELF`, `SHORE`, `PUDDLE`, `PUDDLE_MUD`, `HUMMOCK`, `SHORE_BED`).
- `SurfaceBuilder.build` liczy plan raz na chunk z próbek i kodów siedlisk, które `fill()` już ma. `ChunkSurface.material(i, y)` tylko czyta tablice, więc pętla sekcji nie próbkuje i nie liczy szumów.
- `SoilBlocks` to tabela §7.4 z zastępstwami wanilii (decyzja M2-D): torf i mursz to `mud`, torfowiec to `moss_block`. Szumy łat gleby mają nowe sole `surface.soil` i `surface.microrelief` (fala 7 i 5 bloków, kwantyle równomierne). Stary szum łat żwiru w korycie (`DETAIL`) przeniesiono bez zmian.
- `Material` to enum bloków wanilii. `chunk/MaterialStates` pobiera `BlockState` leniwie z rejestru (`Suppliers.memoize`) i rzuca wyjątek przy nieznanym id.
- Tymczasowa reguła S5 `PolandChunkGenerator.surfaceBlock` i stara metoda `strata` zniknęły.

**Półka brzegowa (`BankShelf`).** *Ten opis dotyczy wersji z kroku S6. W rundzie 1 recenzji półkę zastąpiła rampa od najbliższej wody (opis w „Runda 1 poprawek” niżej).* Półkę dostają:
- strefy `POINT_BAR`, `TALL_HERBS` i `SHORE_REEDBED` w całości;
- strefa `WILLOW_SCRUB` w pierwszych 2 blokach od koryta;
- pierwsze 2 bloki brzegu jezior, oczek i starorzeczy.

Kolumna półki przy wodzie (rząd 1, flaga `SHORE`) schodzi dokładnie do najwyższego lustra wśród 4 sąsiadów. Sąsiadów spoza chunka generator próbkuje na żądanie: średnio 0,0–0,1 próbki na chunk na nizinie i w górach, 2,3 (GAMEPLAY) i 8,3 (REAL) przy dużej rzece, 1,8–2,3 w całych sesjach testu. Dalsze kolumny półki schodzą do zaokrąglonego poziomu swojego koryta lub jeziora z modelu i nie mają wody obok. Kolumna obniża się najwyżej o `MAX_DROP` = 3 bloki. Przy 2 blokach 17% kolumn brzegowych w REAL i 23% w GAMEPLAY zostawało bez wody obok, bo w obu skalach część brzegów leży 3 bloki nad lustrem.

**Dno przybrzeżne:** woda w odległości do 2 bloków (Chebyshev) od suchej kolumny chunka ma dno z `mud`, w potokach z `gravel`. Przy krawędzi chunka decyduje odległość od brzegu z modelu (−d, −s). Pozostałe dna: rzeka `sand`/`gravel`, potok `gravel`/`cobblestone`, jezioro `mud`/`clay`, jezioro dystroficzne, zalew i kałuże `mud`, morze `sand`, a przy głębokości > 20 m `mud`.

**Mikrorelief (`Microrelief`):** udziały z §7.3, szum o fali 4,5 bloku w obu skalach. Niskie kwantyle dają kałuże, wysokie kępy (`rooted_dirt` w olsie, `moss_block` w torfowisku wysokim i borze bagiennym). Kałuża trzyma wodę tylko wtedy, gdy 4 sąsiedzi leżą w chunku, są suche i mają wierzch co najmniej na poziomie jej wody. W przeciwnym razie, także na krawędzi chunka, miejsce dostaje błoto. Dwie sąsiednie kałuże mają więc wspólny poziom albo wyższa nie powstaje. Półka i kolumny brzegowe nie dostają mikroreliefu. *Od rundy 1 recenzji kałuża na krawędzi chunka sprawdza sąsiada spoza chunka (opis niżej).*

**Reszta `fill()`:**
- `ChunkHabitats` i heightmapy `OCEAN_FLOOR_WG` i `WORLD_SURFACE_WG` biorą wierzch i wodę z planu, czyli po półce, kępach i kałużach. Drzewa stoją na kępach, a w kałużach nie rosną.
- `computeHabitats` (brak załącznika) liczy ten sam plan. Nowa metoda `PolandChunkGenerator.surface(...)` zwraca plan dla testów.
- `pack()` buduje paletę liniową 4 bity dla ≤ 16 stanów i paletę mieszającą 5–8 bitów dla 17–256 stanów. Liczniki: `PACKED_SECTIONS`, `PACK_FALLBACKS` i `SURFACE_NANOS` (część `FILL_NANOS`).
- Tryb diagnostyczny: `-Dpolishforests.debug.habitats=true`, w gametestach `-PdebugHabitats`. Strefy dostają beton w kolorze (porządek strefy − 1) mod 16, a kolumny bez strefy terakotę w kolorze (porządek biomu) mod 16. *Od rundy 1 recenzji:* każda strefa i każdy biom ma własny blok (opis w „Runda 1 poprawek”). Zrzuty `docs/m2/gra/s6_debug_river_realistic.png` (łacha w kolorze limonki na poziomie wody, wiklina różowa) i `s6_debug_lake_reedbed_realistic.png` (szuwar lądowy żółty, łozowisko fioletowe z kałużami).

**Pokrywa w blokach.** Nowe pole ustawień świata `cover_in_blocks`. Presety i `PolandSettings.DEFAULT` mają `true`. Brak pola w zapisie (światy sprzed S6, także M1) to `false`, czyli stara reguła: pokrywa ⌈`coverDepth`⌉ bloków, więc nowe chunki starego świata zachowują dawną grubość pokrywy. Pozostałe zmiany powierzchni z S6 (półka, gleby, mikrorelief, wierzch z gleby lub dna na każdej kolumnie) dotyczą też nowych chunków starych światów, więc te nie pasują do chunków wygenerowanych wcześniej (bez migracji, decyzja M2-7; sprostowanie z rundy 1 recenzji). Z przełącznikiem skała zaczyna się na `topBlockY(surface − coverDepth)`. Na nizinie REAL daje to 1,00 bloku na metr, a w GAMEPLAY 0,72, bo ok. 60 m pokrywy sięga poniżej poziomu morza, gdzie odwzorowanie jest bardziej strome. Wcześniej w obu skalach był 1 blok na metr. `PolandSettingsTest` sprawdza zapis i odczyt.

**Odstępstwa:**
- **Wierzch suchej kolumny to zawsze gleba (co najmniej 1 blok).** Dotyczy też kolumn bez pokrywy w modelu. Wcześniej 7% suchych kolumn leśnych w obszarach `SoilTest` (cienka zwietrzelina grzbietów Beskidów) miało goły kamień, na którym drzewa nie rosną. Teraz w planie 0 z 49 651. Gołoborza i wychodnie skał przyjdą z dekoracją (S7, M5).
- Bez `mossy_cobblestone` (5%) na bielicy górskiej, bo nie należy do `#supports_vegetation` (zasada sadzonek). Kamienie przyjdą z dekoracją.
- Wyjątki zasady sadzonek w `SoilTest`: łacha, kamieniec oraz strefy plaży i wydmy (`STRANDLINE`, `EMBRYO_DUNE`). Klasyfikator nie daje ich w biomach leśnych, ale test sprawdza wszystkie pary biom × strefa.
- `MAX_DROP` = 3 bloki zamiast „1–2 bloków” półki (pomiar wyżej). Szerokość półki w wiklinie i przy jeziorach to 2 bloki.
- Własnych bloków, tekstur, lootu i ich rejestracji nie ma (decyzja M2-D, M4). Pliki `block/ModBlocks`, `tools/textures/soils.py` i datagen modeli zostają na M4.
- `getBaseHeight` i `getBaseColumn` dalej liczą wierzch z modelu, bez półki i kęp (±1–3 bloki). Używają ich struktury i `/locate`. Heightmapy chunków są dokładne.
- Do testu w grze trafiły zmiany: miejsca gleb szuka się w planie, a nie w `PolandWorldClientGameTest`; test rozlewów planuje tick wody przy każdym lustrze planu; dyski wanilii (`disk_sand`, `disk_clay`, `disk_gravel` z kroku 6) zamieniają pojedyncze bloki brzegu na piasek, glinę lub żwir, więc test liczy je osobno (opis niżej).

**Testy JUnit:**
- `SoilTest`:
  - wierzch gleb leśnych w `#supports_vegetation` (tag rozwiązany z jaru gry; 18 biomów × strefy × siatka kwantyli);
  - piasek na plaży i wydmie białej, wydma szara w `#supports_dry_vegetation`;
  - `coverBlocks` w obu skalach i stara reguła bez przełącznika;
  - w 587 sekcjach przy powierzchni (nizina, Beskidy, duża rzeka, wybrzeże, w GAMEPLAY także jezioro rynnowe) najwyżej 9 stanów, limit 32;
  - na suchych kolumnach leśnych 0 kamiennych wierzchów.
- `BankShelfTest`: siatki 6 × 6 chunków w 8 obszarach na skalę (duża rzeka, ziołorośla, łacha, wiklina, jezioro, szuwar lądowy, ols, bór bagienny). Wyniki:
  - plan nie ma żadnej otwartej krawędzi wody (woda z powietrzem obok na tym samym Y), której nie miałby model, także przez granice chunków. Model ma ich 46 (REAL) i 4 (GAMEPLAY): to stopnie poziomu rzeki;
  - wodę obok wierzchu ma 100% kolumn brzegowych: 1046 w REAL i 1368 w GAMEPLAY;
  - obniżonych kolumn półki jest 9528 w REAL i 4462 w GAMEPLAY;
  - dno przybrzeżne ma 2607 i 2305 kolumn, wszystkie z błota lub żwiru;
  - udziały mikroreliefu (wynik / plan, w %):

    | Obszar | Kałuże REAL | Kępy REAL | Kałuże GAMEPLAY | Kępy GAMEPLAY |
    |---|---|---|---|---|
    | ols | 19,5 / 20 | 23,2 / 25 | 18,9 / 20 | 25,1 / 25 |
    | bór bagienny | 7,0 / 8 | 20,3 / 20 | 8,2 / 8 | 20,4 / 20 |

    Wodę trzyma 74–75% miejsc kałuż w olsie i 32–68% w borze bagiennym (na zboczach misy więcej błota);
  - plan jest deterministyczny.

**Test w grze** (`HabitatsClientGameTest`, `-Pgametest=habitats`, obie skale, ziarno 20260927):
- **Blok gruntu = `SoilBlocks` w 11 miejscach §12.3:** łęg A, łęg B, ols, szuwar jeziorny, wiklina, bór suchy, buczyna, regiel górny, kosodrzewina, plaża z wydmami, torfowisko wysokie. Kosodrzewina i świerczyna leżą przy wielkim masywie najbliższym (0, 0). Sprawdzana jest każda sucha kolumna biomu miejsca w jego chunku bez pnia na sobie. REAL: 2349 z 2350 kolumn zgodnych, 1 pod dyskiem piasku wanilii. GAMEPLAY: 1694 z 1736, 42 pod dyskami. Miejsce „wiklina” w GAMEPLAY to strefa wikliny w grądzie przy masywie, a „szuwar jeziorny” to strefa szuwaru w olsie.
- **Półka:** woda obok wierzchu przy 100% z 261 (REAL) i 590 (GAMEPLAY) kolumn brzegowych w czterech obszarach (duża rzeka, szuwar jeziorny, ols, łęg A). Kryterium wynosi ≥ 90%.
- **Rozlewy:** test planuje tick wody na każdym lustrze planu: 6023 bloki w REAL i 2071 w GAMEPLAY, w tym kałuże. Po 200 tickach nie zostaje żaden zaplanowany tick i nie ma wody poza planem dalej niż 8 bloków od otwartej krawędzi modelu. Plan nie ma też nowych krawędzi. Woda poza planem przy stopniach poziomu rzeki w modelu: 56 bloków (REAL, duża rzeka) i 4 (REAL, ols) — to stan modelu sprzed S6.
- `PACK_FALLBACKS`: 0 z 24 642 (REAL) i 0 z 41 307 (GAMEPLAY) spakowanych sekcji.
- Spis drzew i `revisit` bez zmian wobec S5. Spis liczy teraz pnie na wierzchu planu.
- Zrzuty: `docs/m2/gra/s6_shelf_river_realistic.png` (łacha na poziomie wody) i `s6_shelf_alder_carr_realistic.png` (ols z kępami i kałużami).

**Budżet TERRAIN (`-Pgametest=stages`, A/B w jednej sesji, spokojna maszyna).** Porównanie trzech wersji: kod S6, baza S5 (`35f2aee`) i M1 z migawki `migawki/m1-z-narzedziami-S0.tar` (SHA-256 zgodny, tryb `etapy`). Dwa przebiegi na wersję. Wartości to czas ściany 64 chunków (ms), a w nawiasie próbkowanie + wypełnianie w ms na chunk.

| Obszar | M1 (S0) | S5 | S6 | S6 / M1 |
|---|---|---|---|---|
| REAL nizina | – (pomiar M1 ze startem świata, 836–978) | 166–170 (1,40–1,46) | 162–166 (1,31–1,40) | – |
| REAL Beskidy | 330–335 (3,93–3,95) | 358–430 (4,10–4,83) | 354–383 (4,05–4,52) | 1,06–1,16 (CPU 1,03–1,15) |
| REAL duża rzeka | 182–187 (1,55–1,57) | 243–245 (2,43–2,47) | 239–242 (2,35–2,50) | 1,28–1,33 (CPU 1,50–1,60) |
| GAMEPLAY nizina | – (ze startem świata, 1188–1352) | 163–170 (1,35–1,38) | 160 (1,38–1,43) | – |
| GAMEPLAY Beskidy | 333–334 (3,62–3,74) | 332 (3,83) | 332 (3,72–3,79) | 1,00 (CPU 1,01–1,05) |
| GAMEPLAY duża rzeka | 216–232 (2,16–2,20) | 226–242 (2,32–2,43) | 225–233 (2,32–2,39) | 0,97–1,08 (CPU 1,05–1,10) |

- **S6 nie wydłuża TERRAIN wobec S5** w żadnym obszarze. Plan powierzchni kosztuje 0,02–0,05 ms na chunk, przy dużej rzece REAL 0,14 ms (z 8,3 próbkami sąsiadów).
- **Budżet ≤ 1,10 × M1** jest dotrzymany w GAMEPLAY i na granicy w Beskidach REAL (1,06–1,16 ściany, 1,03–1,15 CPU). **Przy dużej rzece REAL nie jest dotrzymany** (1,28–1,33). Przyczyną jest koszt próbki modelu z poprawki geometrii (1,73–1,93 wobec 1,11–1,12 ms na chunk; ten sam w S5), a nie S6. Budżet D1 (`sample` do 1,20 × M1) liczono na całym obszarze, a ten obszar to sama dolina dużej rzeki.
- **Nizina:** migawka M1 nie czeka na koniec startu świata, więc jej pomiar niziny jest nieważny. Tu porównanie dotyczy tylko S5 i S6 (bez zmian).
- **Do decyzji użytkownika:**
  1. przyjąć budżet TERRAIN wobec M1 jak D1, czyli liczony z kosztu próbki na całym obszarze, a dla doliny dużej rzeki REAL zapisać odstępstwo;
  2. przyspieszyć `RiverNetwork` w dolinach REAL (profil `sample` przy rzece, wynik niepewny, 1–2 dni).

  Rekomendacja: wariant 1. Cały chunk (≤ 48 ms) rozstrzyga S10.

**Co zostaje:** rośliny półki (trzcina, świetliki, pałka) i dyski wanilii na glebach moda przychodzą w S7, a własne bloki torfu, murszu i torfowca w M4. Do oceny w grze zostają proste, schodkowe krawędzie łachy na poziomie wody (granica strefy z drganiem ±20%) i widoczne łaty `coarse_dirt`, `mud` i `podzol` przed pokryciem runem.

#### Runda 1 poprawek S6 (2026-10-09)

**Weryfikacja zgłoszeń.** Wszystkie zgłoszenia recenzji okazały się prawdziwe. Sprawdziłem je nowymi wskaźnikami `BankShelfTest`, uruchomionymi także na kodzie sprzed rundy (`1b87dfa`, obszary testu w obu skalach), a półkę i rozlewy dodatkowo w grze:

| Zgłoszenie | Przed rundą (REAL / GAMEPLAY) | Po rundzie |
|---|---|---|
| gołe dno potoków GAMEPLAY | dno potoku w Beskidach GAMEPLAY: 4 z 43 kolumn ze żwirem lub otoczakami, reszta `stone`; dno przybrzeżne 3322 z 3361 | 43 z 43; dno przybrzeżne 3361 z 3361 (REAL 335 z 335 i 4092 z 4092) |
| ściany na wewnętrznej krawędzi półki | 1798 / 1666 par suchych sąsiadów z różnicą ≥ 2 bloków przy płaskim modelu | 0 / 0 |
| rozlewy przy otwartych krawędziach modelu | woda z otwartych krawędzi modelu (przepływ do 7 bloków) dochodzi do 212 / 260 kolumn gruntu usuniętego przez półkę | 0 / 0 |
| suche dno półki poniżej lustra | 20 / 70 suchych obniżonych kolumn poniżej lustra wody w promieniu 2 bloków | 0 / 0 |
| siatka chunków w mikroreliefie | kałuże z wodą na skraju chunka: 0% (wewnątrz 44–97%) | 38–96% na skraju, 44–97% wewnątrz |
| dno przybrzeżne na morzu | flaga `SHORE_BED` także na wodzie morskiej przy plaży | tylko rzeki i jeziora |
| buczyna: `dirt` zarasta trawą | w 26.3 `SpreadingSnowyBlock.randomTick` zamienia oświetlony `dirt` obok trawy w trawę | `podzol` 45%, `coarse_dirt` 35%, `rooted_dirt` 20% (nie zarastają) |
| profil torfowiska | pod 2–5 blokami torfu glina z reguły osadu; w GAMEPLAY w olsie pod torfem od razu kamień | torfowisko wysokie: torf 3–6 bloków, pod nim piasek 2; niskie: mursz, torf 2, błoto 1; cały profil torfu leży na pokrywie |
| kolory trybu diagnostycznego | 22 strefy i 36 biomów na 16 kolorach (powtórzenia) | każda strefa i każdy biom ma własny blok |
| sformułowania w docs i commicie | „nowe chunki starego świata pasują do starych”, „no water leaves the plan” | sprostowane niżej |

**Półka jako rampa (`BankShelf`, `ColumnCache`).**
- Sucha kolumna w promieniu 3 bloków (Chebyshev) od wody modelu (rzeka, jezioro, oczko, starorzecze; bez morza) schodzi rampą 1 bloku na kolumnę. Wierzch wynosi najwyżej `W + k − 1` dla każdej wody w zasięgu (lustro `W`, odległość `k`). Kolumna obniża się najwyżej o `MAX_DROP + 1 − c` bloków, gdzie `c` to odległość od najbliższej wody, więc rampa wraca do wierzchu modelu w 4 kolumnach. Brzegi wyższe niż 3 bloki nad wodą obniża mniej (`FADE` = 6: od 6 bloków bez zmian).
- Rampa nie zależy od strefy siedliska ani od pól odległości modelu, tylko od prawdziwej wody. Dlatego każdy chunk liczy ją tak samo, nie ma progów na granicach stref i chunków, a odcinek koryta bez wody nie dostaje rowu. Przy płaskim modelu sąsiednie kolumny różnią się najwyżej o 1 blok, a stopień modelu `k` rośnie najwyżej do `k + 1`.
- Strażnicy przed rozlewem. Kolumna nigdy nie schodzi poniżej:
  - lustra wody obok niej i w promieniu 2 bloków (także za granicą chunka);
  - wody otwartej krawędzi modelu w promieniu 8 bloków w chunku;
  - poziomu własnego koryta 8 bloków w górę biegu. Spadek liczę z różnicy poziomu koryta do sąsiadów, więc w promieniu 8 bloków poniżej stopnia poziomu rzeki kolumna zostaje na lustrze górnego odcinka. Bez tego woda z górnego lustra płynęła po dolnym na obniżony brzeg, także z sąsiedniego chunka.
- Przy wodzie morskiej kolumny się nie obniżają (reguły plaży).
- Flaga `SHORE` zostaje: suche kolumny przy wodzie w strefach łachy, ziołorośli, szuwaru lądowego i wikliny oraz w pierwszych 2 blokach brzegu jezior. Flaga `SHELF` oznacza teraz każdą obniżoną kolumnę.
- Plan czyta kolumny do 3 bloków za granicą chunka. `ColumnCache` to dzielona, bezblokadowa pamięć skrótów kolumn (wierzch i lustro modelu, rodzaj wody, czy woda jest blisko według pól modelu) dla ziarna świata. Każdy plan publikuje swoje kolumny przygraniczne. Kolumnę, którą plan musiał pobrać z modelu poza swoim chunkiem, pamięć trzyma z pełną próbką. `fill()` bierze ją przy wypełnianiu chunka tej kolumny (`SurfaceBuilder.reuse`), więc każda kolumna jest próbkowana mniej więcej raz. Wartości są czystą funkcją kolumny. `BankShelfTest.planIsDeterministic` porównuje plany zbudowane w innej kolejności, czyli z pamięcią i bez niej.

**Inne poprawki.**
- Każda kolumna, także mokra, ma co najmniej 1 blok gleby lub dna nad skałą (`MIN_SOIL`).
- Kałuża na skraju chunka sprawdza sąsiada spoza chunka. Sąsiad musi być suchy w modelu, mieć wierzch co najmniej na poziomie wody kałuży i na pewno nie zostać obniżony przez półkę (bez wody w zasięgu rampy, także według jego pól modelu).
- `SHORE_BED` tylko na wodzie rzek i jezior.
- Profil gleby pakowany na 7 bitach materiału (do 128 materiałów).
- Kolory diagnostyczne. Strefy: beton dla pierwszych 16, wełna w kolorach 0–5 dla stref 17–22. Biomy: terakota dla pierwszych 16, cement dla 17–32, wełna w kolorach 15–12 dla biomów 33–36.
- `HabitatsClientGameTest` czeka przed pomiarem BIOMES, aż skończy się generacja w tle po teleportach. W tej rundzie dwa pierwsze przebiegi dały na nizinie REAL 0,29 / 0,21 i 0,21 / 0,14 ms przy wciąż trwającej generacji miejsca F3. Budżet §3.6 dotyczy jednego wątku.

**Testy JUnit (`BankShelfTest`, teraz `@Tag("slow")`, ok. 25 s).** Obszary: duża rzeka, ziołorośla, łacha, wiklina, jezioro, szuwar lądowy, ols, bór bagienny i potok górski w Beskidach w obu skalach. Do tego miejsca z recenzji: w REAL starorzecze (−4389, 2169), szuwar jeziorny (−4160, 4096) i jezioro rynnowe (4301, −23367), w GAMEPLAY starorzecze (1643, −452), jezioro rynnowe (−20280, 10620), łęg (−640, 3200), potok (−134, −52) i bór bagienny (210, 54). Kryteria i wyniki (REAL / GAMEPLAY):
- woda obok wierzchu kolumn brzegowych: 97,0% z 1412 i 98,0% z 2148 (próg 90%);
- schodki ≥ 2 przy płaskim modelu: 0 / 0; stopnie modelu 1 → 2: 0 / 0; większe stopnie modelu urosłe o więcej niż 1: 0 / 0;
- nowe otwarte krawędzie wody: 0 / 0; woda z otwartych krawędzi modelu na usuniętym gruncie: 0 / 0;
- obniżone kolumny bez wody modelu w promieniu 4 bloków (rowy): 0 / 0; suche kolumny poniżej lustra w promieniu 2: 0 / 0;
- dno przybrzeżne z błota (w potokach ze żwiru) i dno potoku górskiego ze żwiru lub otoczaków: 100%;
- kałuże z wodą na skraju chunka wobec środka: ols 95 / 97% (REAL) i 96 / 96% (GAMEPLAY), bór bagienny 83 / 83% i 38 / 44%.
Próbki sąsiadów spoza chunka w tych obszarach (same okolice wody): 30 (REAL) i 39 (GAMEPLAY) na chunk. Przed `ColumnCache` było to 73–90.

**Test w grze** (`-Pgametest=habitats`, obie skale; dla półki 5 obszarów REAL i 6 GAMEPLAY, w tym nowe: starorzecze REAL, potok i bór bagienny przy potoku GAMEPLAY):
- gleby: 2268 z 2269 (REAL, 1 pod dyskiem wanilii) i 1680 z 1722 (GAMEPLAY, 42 pod dyskami);
- kolumny brzegowe z wodą obok wierzchu: 96,2% z 423 (REAL) i 99,3% z 714 (GAMEPLAY);
- schodki ≥ 2 przy płaskim modelu: 0 we wszystkich obszarach;
- po 200 tickach woda poza planem w usuniętym gruncie lub z dala od otwartych krawędzi modelu: 0 we wszystkich obszarach. Woda nad gruntem modelu przy jego otwartych krawędziach (stan modelu sprzed S6, tylko raport): 56 (duża rzeka REAL), 4 (ols REAL), 23 (potok GAMEPLAY) i 25 (bór bagienny GAMEPLAY). W potoku i borze bagiennym recenzent zmierzył przed rundą 81 i 75 bloków, z czego 58 i 50 w usuniętym gruncie. Teraz zostaje dokładnie reszta: 23 i 25;
- `PACK_FALLBACKS` 0 z 26 202 i 0 z 45 132; brak `ChunkHabitats` 0; `revisit` bez braków;
- próbki sąsiadów spoza chunka w całej sesji: 12,5 (REAL) i 12,8 (GAMEPLAY) na chunk, z czego 44% i 41% generator użył potem ponownie (S6 przed rundą: 1,8–2,3 na chunk).
- Zrzuty: `docs/m2/gra/s6_shelf_river_realistic.png` (łacha schodzi do wody rampą), `s6_shelf_lake_reedbed_realistic.png` (szuwar jeziorny bez ściany), `s6_shelf_river_gameplay.png`, `s6_shelf_bog_woodland_stream_gameplay.png` (kępy torfowca, potok z rampą). Tryb diagnostyczny (`-PdebugHabitats`, zrzuty zastąpione): `s6_debug_river_realistic.png` (łacha w kolorze limonki schodzi do wody tarasami po 1 bloku, bez ściany na granicy z wikliną) i `s6_debug_lake_reedbed_realistic.png` (szuwar lądowy żółty, dalej od wody z kałużami biomu szuwaru, łozowisko fioletowe, przy jeziorze tarasy rampy).

**Odstępstwa od §7.2 po rundzie.** Strefy łachy, ziołorośli i szuwaru lądowego nie leżą już w całości na poziomie wody. Na lustrze leży pierwszy rząd przy wodzie, a dalej teren rośnie o 1 blok na kolumnę do wierzchu modelu. W REAL, gdzie brzeg leży 1 blok nad lustrem, obniża się więc tylko pierwszy rząd. Obniżenie dotyczy wszystkich brzegów rzek i jezior, nie tylko stref §7.2.

**Budżet TERRAIN po rundzie 1 (`-Pgametest=stages`, przebiegi na przemian w jednej sesji, spokojna maszyna).** Porównanie trzech wersji:
- M1: migawka `migawki/m1-z-narzedziami-S0.tar` w kopii roboczej poza repozytorium, z dopisanym do harnessu czekaniem na koniec startu świata (jak w trybie `stages` od S5), więc nizina M1 jest wreszcie zmierzona. `src/main` migawki jest bez zmian;
- S6 sprzed rundy: `1b87dfa`;
- kod po rundzie.

Każda wersja przeszła 3–4 przebiegi. Drugi przebieg kodu po rundzie mógł być zakłócony, bo w tle działała moja pętla oczekiwania na jednym rdzeniu. Dlatego dodałem czwarty przebieg. Czasy ściany drugiego przebiegu mieszczą się w zakresie pozostałych, więc mediany od niego nie zależą. Wartości to czas ściany 64 chunków (ms, mediana i zakres), a w nawiasie mediana próbkowania modelu bez klasyfikacji i wypełniania w ms na chunk (`SAMPLE − CLASSIFY`, `FILL`). Maszyna była tej nocy wolniejsza i bardziej zmienna niż przy pomiarze S6: M1 przy dużej rzece REAL 210–273 ms wobec 182–187 wczoraj, a jeden przebieg M1 w Beskidach REAL dał 655 ms. Liczą się więc stosunki z tej samej sesji.

| Obszar | M1 | S6 sprzed rundy | po rundzie | po rundzie / M1 | sprzed rundy / M1 |
|---|---|---|---|---|---|
| REAL nizina | 177 (156–188; 0,97 + 0,55) | 193 (162–194; 1,06 + 0,55) | 197 (189–204; 1,19 + 0,61) | 1,12 | 1,09 |
| REAL Beskidy | 390 (324–655; 2,63 + 2,32) | 414 (364–456; 2,85 + 2,45) | 441 (428–466; 2,67 + 2,97) | 1,13 | 1,06 |
| REAL duża rzeka | 248 (210–273; 1,58 + 0,58) | 292 (279–305; 2,16 + 0,87) | 311 (307–366; 2,10 + 1,12) | 1,25 | 1,18 |
| GAMEPLAY nizina | 166 (162–242; 0,99 + 0,42) | 183 (176–195; 1,22 + 0,48) | 191 (185–210; 1,29 + 0,52) | 1,15 | 1,11 |
| GAMEPLAY Beskidy | 381 (368–430; 3,05 + 1,33) | 415 (397–426; 3,36 + 1,25) | 406 (395–440; 3,30 + 1,47) | 1,07 | 1,09 |
| GAMEPLAY duża rzeka | 244 (214–247; 2,08 + 0,40) | 281 (255–288; 2,42 + 0,47) | 278 (270–286; 2,17 + 0,77) | 1,14 | 1,15 |

Wnioski:
- **Budżet TERRAIN ≤ 1,10 × M1 nie jest dotrzymany w 5 z 6 obszarów.** Spełniają go tylko Beskidy GAMEPLAY (1,07). Na nizinie, zmierzonej teraz po raz pierwszy, wynik to 1,12 (REAL) i 1,15 (GAMEPLAY). Krok S6 jest więc **ukończony pod warunkiem decyzji użytkownika w sprawie TERRAIN**.
- **Udział rundy 1:** po rundzie / sprzed rundy wynosi 0,98–1,07. Najwięcej, +7%, przy dużej rzece REAL i w Beskidach REAL. Plan powierzchni kosztuje tam 0,44–0,50 ms na chunk (sprzed rundy 0,04–0,22). To głównie próbki kolumn spoza chunka: 22–27 na chunk, z czego `fill()` używa potem ponownie 18–21. Na nizinie kosztuje 0,09–0,15 ms.
- **Udział modelu (`SAMPLE − CLASSIFY`, klasyfikacja to 0,04–0,11 ms na chunk):** model kosztuje 1,23 × M1 na nizinie REAL, 1,30 × na nizinie GAMEPLAY, 1,33 × przy dużej rzece REAL, 1,02–1,08 × w Beskidach i 1,04 × przy rzece GAMEPLAY. Na nizinach i przy rzece REAL przekroczenie pochodzi więc głównie z kosztu próbki modelu (pola S2–S3, poprawka geometrii; był już w S5 i przed rundą). D1 (`sample` do 1,20 × M1 na całym obszarze, `SampleCostTest`) pozostaje dotrzymany (K8z: 1,09–1,18).
- **Beskidy REAL (pytanie z rundy 1 recenzji S5):** model kosztuje tam tyle co w M1 (2,67 wobec 2,63 ms na chunk). Wzrost pochodzi z wypełniania: 2,97 wobec 2,32 ms. Na to składa się plan powierzchni 0,44 ms (półka przy gęstej sieci potoków), a reszta (+0,2 ms, +9%) to `ChunkHabitats`, profil gleby z planu i palety. Sprzed rundy było to 1,06 × M1, a wcześniejsze 1,06–1,16 z dwóch przebiegów to w dużej mierze rozrzut pomiaru (M1 sam waha się tu o 20%).
- **Do decyzji użytkownika** (dotyczy obu obszarów REAL ponad budżetem, czyli doliny dużej rzeki i Beskidów, oraz nizin w obu skalach):
  1. przyjąć budżet TERRAIN wobec M1 jak D1, czyli z kosztu próbki modelu na całym obszarze, i zapisać odstępstwo: dolina dużej rzeki REAL 1,25, niziny 1,12–1,15, Beskidy REAL 1,13. O całym chunku (≤ 48 ms) rozstrzyga S10;
  2. przyspieszyć model (`RiverNetwork` w dolinach, pola S2–S3 na nizinie; profil `sample`, wynik niepewny, 1–3 dni) i plan powierzchni (mniej próbek spoza chunka, np. przez wcześniejsze odcięcie kolumn bez wody w zasięgu, ok. 0,5 dnia; zysk do 5–7% przy wodzie).

  Rekomendacja: wariant 1, a optymalizację planu powierzchni zrobić przy S10 razem z pomiarem całego chunka.

**Pełny zestaw testów:** `tools/dev/run-tests test` PASS (drzewo `e2b735fbaea3`, 1143 s), złoty test bez zmian i bez listy dozwolonych zmian.

**Sprostowania.**
- Commit `1b87dfa` i podsumowanie kroku S6 podawały, że „żadna woda nie opuszcza planu w 200 tickach”. W rzeczywistości 56 bloków (duża rzeka REAL) i 4 (ols REAL) wypłynęło poza plan. Test pomijał je, bo leżały do 8 bloków od otwartych krawędzi modelu (stopnie poziomu rzeki). Ta sama maska ukrywała rozlewy S6 przy takich krawędziach. Od tej rundy woda w gruncie, który model miał (y ≤ wierzch modelu), liczy się zawsze jako rozlew S6.
- Commit `1b87dfa` podawał też, że TERRAIN przekracza 1,10 × M1 „tylko przy dużej rzece”. Jeden z dwóch pomiarów Beskidów REAL też go przekraczał (1,16).
- Nowe chunki starego świata nie pasują do starych (wyżej, „Pokrywa w blokach”).

**Co zostaje po rundzie 1:**
- S7: rośliny półki; dyski wanilii (`disk_sand`, `disk_clay`, `disk_gravel`) przy kałużach i na brzegach (w GAMEPLAY w łęgu 17 ze 185 kolumn pod dyskiem) — ograniczyć je do koryt i jezior albo zastąpić własnymi;
- do oceny w grze: schodkowe tarasy rampy (1 blok na kolumnę, linie równoległe do wody) i drobne łaty „moro” na madzie lekkiej i łasze (`coarse_dirt` ok. 25–30%, `mud` 10–25%, fala 5–7 bloków; recenzja proponuje mniej `coarse_dirt`, dłuższą falę albo błoto zamiast `coarse_dirt`; do decyzji użytkownika lub oceny po S7);
- M4: własne bloki torfu, murszu i torfowca.

#### Runda 2 poprawek S6 (2026-10-09)

**Weryfikacja zgłoszeń.** Oba zgłoszenia główne są prawdziwe. Recenzent przeszukał 120 losowych obszarów rzek i jezior na skalę (6 × 6 chunków) i znalazł przypadki, których nie było w obszarach testu: zbiegi, koryta roztokowe, szwy między dwoma ciekami, brzegi starorzeczy blisko koryta i schodkowe potoki górskie. Drobne zgłoszenia sprawdziłem na kodzie po rundzie 1 (`7d0a091`):

| Zgłoszenie | Stan na `7d0a091` | Po rundzie 2 |
|---|---|---|
| ściany i słupy od strażnika `levelStep` (spadek liczony z różnicy `channelLevel` do sąsiadów skacze przy zmianie najbliższego koryta; strażnik działał też przy starorzeczach i jeziorach 17–20 bloków od koryta) | przegląd recenzenta: 36 (REAL) i 46 (GAMEPLAY) schodków ≥ 2 przy płaskim modelu w 5 z 240 obszarów; w grze słupy do 3 bloków | 0 / 0 w 240 obszarach losowych i we wszystkich obszarach testu; w grze 0 |
| rozlewy z otwartych krawędzi w sąsiednim chunku (`openEdgesWithin` widział tylko własny chunk) | przegląd: 471 kolumn usuniętego gruntu w zasięgu wody z otwartych krawędzi (REAL, schodkowy potok przy (59458, 136152)); w grze 32 bloki wody w usuniętym gruncie | 0 / 0 w przeglądzie; w grze 0 we wszystkich 15 obszarach |
| kałuże na skraju chunka zawsze błotem | ols i bór bagienny naprawione w rundzie 1, ale **torfowisko wysokie nadal miało 0% kałuż z wodą na skraju** (wewnątrz 100%): pole `s` torfowiska w niecce bez wody dawało „woda blisko” | 100% na skraju i wewnątrz (REAL i GAMEPLAY) |
| buczyna z `dirt`, `SHORE_BED` na morzu, javadoc `PolandSettings` i §7.6 o starych światach, kolory diagnostyczne, profil torfowiska, sucha półka poniżej lustra, javadoc `BankShelf` o GAMEPLAY | naprawione w rundzie 1 (tabela wyżej) | bez zmian (zgłoszenia nieaktualne) |
| sformułowanie commita `1b87dfa` o rozlewach | sprostowane w rundzie 1 („Sprostowania”); opublikowanych commitów nie zmieniamy | bez zmian |
| dyski wanilii przy kałużach | wpisane do S7 w rundzie 1 | bez zmian |
| wzór „moro” na madzie lekkiej i łasze | do decyzji użytkownika lub oceny po S7 | bez zmian |

**Strażnik ciągły (`BankShelf`, `SurfaceBuilder.Work.guardField`).** Trzech strażników rundy 1 (woda obok i w promieniu 2, otwarte krawędzie w promieniu 8 w chunku, poziom koryta 8 bloków w górę biegu) zastąpił jeden:
- Kolumna nigdy nie schodzi poniżej lustra żadnej wody modelu (bez morza) w odległości Manhattan do 9 bloków, także za granicą chunka. 7 bloków to zasięg wody płynącej ze źródła. 2 bloki to zapas na wyższą wodę, która na ukośnym stopniu lustra zamienia się w źródła nad niższą wodą (`FlowingFluid.getNewLiquid`: dwa źródła obok i źródło pod spodem) i rozlewa się po niej. Przy promieniu 7 w grze zostało 6 bloków takiego rozlewu przy szwie koryt REAL (87470, −42069).
- Dalej strażnik słabnie o 1 blok na blok, a każdy składnik jest ograniczony z góry wierzchem modelu kolumny: `min(W, a) − max(0, m − 9)`. Każdy składnik zmienia się więc między sąsiadami o najwyżej 1 blok. Składniki odcięte za zasięgiem (11 bloków) są ≤ `a − 3`, więc nigdy nie działają. Strażnik nie robi schodków także tam, gdzie zmienia się najbliższe koryto.
- Wygaszanie wysokich brzegów (`FADE`) liczę od każdej wody w zasięgu rampy (najmniejsze `k − W`), a nie od `r − c + 1`. Przy schodkowej wodzie tamto dawało schodki 2 bloków na wysokich brzegach potoku (46 par w obszarze schodkowego potoku REAL).

Skutek: przy schodkowej wodzie (potoki górskie, zbiegi, szwy) brzeg zostaje na poziomie wyższego lustra, więc część kolumn brzegowych nie ma wody obok wierzchu (schodkowy potok REAL w grze: 48 ze 176). Na zwykłych rzekach i jeziorach nic się nie zmienia.

**Próbki spoza chunka.** Strażnik czyta wodę do 11 bloków za granicą chunka. Kolumnę spoza chunka próbkuję tylko w zasięgu kandydata do obniżenia i tylko wtedy, gdy najbliższa kolumna chunka dopuszcza tam wodę według swoich pól (`waterMayBeWithin`: odległość od koryta albo od jeziora z wodą ≤ d + 2). Plany publikują w `ColumnCache` także kolumny w pobliżu wody, nie tylko przygraniczne. Pamięć ma teraz 2¹⁸ miejsc.

**Testy JUnit (`BankShelfTest`).**
- Nowy test `shelfOverRandomWaterAreas`: 120 losowych obszarów rzek i jezior na skalę, wybranych jak w przeglądzie recenzenta. REAL / GAMEPLAY: schodki ≥ 2 przy płaskim modelu 0 / 0, nowe otwarte krawędzie 0 / 0, woda z otwartych krawędzi na usuniętym gruncie 0 / 0, rowy 0 / 0, sucha kolumna poniżej lustra 0 / 0, stopnie modelu 1 → 2: 141 z 27 119 i 27 z 20 613 obniżonych kolumn (próg 1%), kolumny brzegowe z wodą obok wierzchu 98,3% i 98,1%.
- Nowe obszary nazwane: REAL schodkowy potok (59458, 136152) i szew koryt (87432, −42036), GAMEPLAY zbieg (17085, 16600), starorzecze przy korycie (−14271, 22788) i schodkowy potok (20028, −8612), w obu skalach torfowisko wysokie (mikrorelief).
- Udział kolumn brzegowych z wodą obok wierzchu i próg stopni modelu 1 → 2 liczę bez obszarów rundy 2: 97,2% z 1412 (REAL) i 99,3% z 2148 (GAMEPLAY). Obszary rundy 2 sprawdzają schodki i rozlewy, a przy schodkowej wodzie brzeg zostaje wyżej celowo. Razem z nimi udział w REAL wynosi 86,5%, a w schodkowym potoku REAL 135 stopni modelu 1 rośnie do 2 (rampa schodzi po stromym brzegu).
- Kałuże na skraju chunka wobec środka: torfowisko wysokie 100 / 100% w obu skalach (przed rundą 0 / 100%).
- Próbki spoza chunka na chunk w przeglądzie: 41,8 (REAL) i 43,5 (GAMEPLAY), na kodzie rundy 1 17,9 i 30,4. Siatki 6 × 6 chunków mają dużo kolumn za brzegiem siatki, których nikt potem nie wypełnia, więc liczby są wyższe niż w grze.

**Test w grze** (`-Pgametest=habitats`, obie skale, BUILD SUCCESSFUL; nowe obszary półki: REAL schodkowy potok i szew koryt, GAMEPLAY zbieg i starorzecze przy korycie). Nowa opcja `-Pscales=realistic|gameplay` uruchamia tylko jedną skalę, a komunikat błędu podaje pierwsze rozlewy S6.
- Schodki ≥ 2 przy płaskim modelu: 0 we wszystkich 15 obszarach.
- Po 200 tickach woda poza planem w usuniętym gruncie lub z dala od otwartych krawędzi modelu: 0 we wszystkich obszarach. Na kodzie rundy 1 recenzent zmierzył 32 bloki w schodkowym potoku REAL.
- Woda nad gruntem modelu przy jego otwartych krawędziach (stan modelu, tylko raport): duża rzeka REAL 56, ols REAL 4, schodkowy potok REAL 487, szew koryt REAL 239, potok GAMEPLAY 23, bór bagienny 25, zbieg 6, starorzecze przy korycie 64. To głównie wyższa woda rozlana po niższej na ukośnych stopniach lustra modelu. Woda zachowuje się tak także w modelu bez S6, a brzegi modelu ją zatrzymują, więc nie jest to rozlew S6.
- Kolumny brzegowe z wodą obok wierzchu (bez obszarów rundy 2): 96,9% z 423 (REAL) i 99,7% z 714 (GAMEPLAY). Obszary rundy 2: schodkowy potok REAL 48 ze 176, szew koryt 275 z 342, zbieg 379 z 418, starorzecze przy korycie 211 z 211.
- Gleby: 2261 z 2262 (REAL, 1 pod dyskiem wanilii) i 1680 z 1722 (GAMEPLAY, 42 pod dyskami). `PACK_FALLBACKS` 0 z 35 473 i 0 z 57 839 sekcji. Brak `ChunkHabitats` 0. BIOMES na nizinie 0,142 / 0,096 ms (REAL) i 0,139 / 0,141 ms (GAMEPLAY).
- Próbki spoza chunka w całej sesji: 28,1 (REAL) i 25,9 (GAMEPLAY) na chunk, z czego 47% i 45% generator użył potem ponownie (runda 1: 12,5 i 12,8).

**Budżet TERRAIN po rundzie 2 (`-Pgametest=stages`, po 3 przebiegi na przemian w jednej sesji, spokojna maszyna).** Porównanie trzech wersji: M1 (migawka z dopisanym na czas pomiaru czekaniem na koniec startu świata, jak w rundzie 1; potem przywrócona), kod rundy 1 (`7d0a091`, osobny worktree poza repozytorium, usunięty po pomiarze) i kod po rundzie 2. Wartości to czas ściany 64 chunków (ms, mediana i zakres), w nawiasie plan powierzchni (ms na chunk) i próbki spoza chunka na chunk.

| Obszar | M1 | runda 1 | runda 2 | runda 2 / M1 | runda 1 / M1 | runda 2 / runda 1 |
|---|---|---|---|---|---|---|
| REAL nizina | 166 (157–172) | 173 (172–180; 0,06; 0) | 174 (170–184; 0,07; 0) | 1,05 | 1,04 | 1,01 |
| REAL Beskidy | 329 (327–349) | 377 (371–379; 0,34; 22) | 395 (376–396; 0,64; 45) | 1,20 | 1,15 | 1,05 |
| REAL duża rzeka | 178 (172–180) | 263 (252–349; 0,39; 27) | 263 (258–276; 0,64; 59) | 1,48 | 1,48 | 1,00 |
| GAMEPLAY nizina | 154 (149–166) | 179 (173–180; 0,11; 2) | 168 (167–168; 0,11; 2) | 1,09 | 1,16 | 0,94 |
| GAMEPLAY Beskidy | 334 (330–351) | 364 (363–369; 0,23; 12) | 362 (347–365; 0,23; 12) | 1,08 | 1,09 | 0,99 |
| GAMEPLAY duża rzeka | 215 (209–224) | 244 (244–247; 0,34; 25) | 253 (247–255; 0,60; 49) | 1,18 | 1,13 | 1,04 |

Wnioski:
- **Udział rundy 2:** runda 2 / runda 1 wynosi 0,94–1,05. Plan powierzchni przy wodzie kosztuje teraz 0,60–0,64 ms na chunk (runda 1: 0,34–0,39), bo strażnik czyta 2 razy więcej kolumn spoza chunka (45–59 na chunk w tych obszarach). Na nizinie i w Beskidach GAMEPLAY plan się nie zmienił.
- **Wobec M1 budżet TERRAIN ≤ 1,10 nadal nie jest dotrzymany** w Beskidach REAL (1,20), przy dużej rzece REAL (1,48) i przy rzece GAMEPLAY (1,18). Spełniają go niziny (1,05 i 1,09) i Beskidy GAMEPLAY (1,08). M1 był w tej sesji szybszy niż w rundzie 1 (duża rzeka REAL 178 wobec 248 ms), a kod rundy 1 nie, więc stosunki do M1 różnią się od rundy 1 o kilkanaście punktów. To rozrzut sesji, nie zmiana kodu (runda 1 / M1 przy dużej rzece REAL: 1,18 wtedy, 1,48 teraz).
- Decyzja o budżecie TERRAIN zostaje u użytkownika z wariantami z rundy 1. Rekomendacja bez zmian: liczyć budżet jak D1 i optymalizować plan powierzchni przy S10 (np. kolumny spoza chunka tylko dla strażnika tam, gdzie lustra w zasięgu się różnią). **Rozstrzygnięte decyzją M2-11 (2026-10-09): budżet TERRAIN liczony dla całego obszaru jak D1, dolina dużej rzeki REAL akceptowana (§3.6).**

**Odstępstwa po rundzie 2.**
- Przy schodkowej wodzie (stopnie lustra w promieniu 9 bloków) brzeg nie schodzi do niższego lustra, więc kolumny brzegowe nie mają tam wody obok wierzchu. Kryterium 90% liczę bez obszarów rundy 2.
- Zapas 2 bloków na rozlew wyższej wody po niższej to heurystyka. Na długim odcinku poniżej ukośnego stopnia wyższa woda może się rozlać dalej niż 9 bloków i dojść do obniżonego brzegu (pierwszy rząd przy niższej wodzie). W obszarach testów i w przeglądzie takiego przypadku już nie ma. Pewne rozwiązanie wymaga zmiany samych stopni lustra w modelu albo w planie (warianty niżej).

**Do decyzji użytkownika** (B3: zgłoszenie rozlewów przetrwało 2 rundy, zostaje tylko rozlew przy ukośnych stopniach lustra):
1. przyjąć obecny stan: strażnik do 9 bloków, a rozlew wyższej wody po niższej przy ukośnych stopniach traktować jako zachowanie modelu. Bez kosztu; rekomendacja. **Przyjęty (decyzja M2-15, 2026-10-09, domyślna, do potwierdzenia; „Stan po S6b”).**
2. Strażnik według połączonej wody: kolumna nie schodzi poniżej najwyższego lustra wody połączonej z najbliższą wodą w promieniu ok. 16 bloków. Ok. 0,5–1 dnia, ok. 2 razy więcej próbek spoza chunka przy wodzie, mniej kolumn brzegowych z wodą obok wierzchu przy stopniach, nadal bez pewności dla bardzo długich odcinków.
3. Usunąć przyczynę: stabilne stopnie lustra, np. próg z kamieni lub żwiru na wysokości wyższego lustra w poprzek koryta przy każdym stopniu (w planie powierzchni), albo stopnie tylko na prostych odcinkach (w modelu). Ok. 1–2 dni. Zmienia wygląd koryt (progi widoczne w potokach, rzadkie na nizinie), a wariant w modelu wymaga przegenerowania złotego pliku.

**Pełny zestaw testów:** `tools/dev/run-tests test` PASS (drzewo `e6f459303398` przed wpisaniem tej linii, 990 s), złoty test bez zmian i bez listy dozwolonych zmian; `BankShelfTest` trwa teraz ok. 1 min (przegląd 240 obszarów).

**Co zostaje po rundzie 2:** jak po rundzie 1 (S7: rośliny półki i dyski wanilii; ocena w grze tarasów rampy i wzoru „moro”; M4: własne bloki torfu, murszu i torfowca), a do tego decyzje o budżecie TERRAIN i o rozlewie przy ukośnych stopniach lustra.

#### Stan po S6b (2026-10-09)

Krok S6b domyka S6 i S7: naprawia ściany 2 bloków przeniesione z rundy 2 recenzji S6 (§8.7, „Przeniesione z S6”), wdraża borówkę jako `bush` (M2-13, §8.7) i zapisuje decyzje M2-10 … M2-15 (`docs/00-decyzje-do-podjecia.md` E, §3.6, §10.1, §12.4). Teren modelu się nie zmienia: złoty test (`golden_terrain_m1.txt` i `golden_terrain_m2.txt`) przechodzi bez zmian i bez listy dozwolonych zmian; klasyfikator siedlisk też bez zmian.

**Przyczyny ścian (sprawdzone na miejscach recenzenta).** Przegląd recenzenta rundy 2 (losowe obszary rzek i jezior oraz obszary przy schodkowej wodzie: lustro innej wody 6–14 bloków dalej) dawał na `623ab45` schodki ≥ 2 bloków przy płaskim modelu: w trybie schodkowym (ziarno przeglądu 4242, 150 obszarów na skalę) 7 w REAL i 46 w GAMEPLAY, w trybie losowym (7001, 200 obszarów) 6 i 1. Dwie przyczyny:
1. **Wygaszanie wysokich brzegów.** Spadek kolumny był minimum trzech ograniczeń: rampy od najniższej wody (`W + k − 1`), `MAX_DROP + 1 − c` od najbliższej wody i wygaszania liczonego od wody najkorzystniejszej (największe `FADE + 1 − h − k`). Rampa i wygaszanie pochodziły więc od różnych wód. Woda wyższego lustra wchodząca w okno rampy (Czebyszew 3) podnosiła ograniczenie wygaszania sąsiada o 3 bloki naraz. Przykład GAMEPLAY (7349, −19466): niższy potok (lustro 63) 2 bloki dalej i wyższy (65) w odległości 3 dawały spadek 2, a kolumna obok, dla której wyższy potok leżał w odległości 4, spadek 0.
2. **Próbkowanie spoza chunka.** Kolumna przy granicy chunka próbkowała kolumny sąsiedniego chunka tylko wtedy, gdy jej własne pola (`channelDist`, `s`) dopuszczały wodę w promieniu 10 bloków, i tylko gdy w jej chunku nie było wody bliżej niż granica. Pola starorzecza kończą się 2–5 bloków od jego brzegu (`s` = ∞ dalej; w GAMEPLAY już 2–3 bloki od brzegu), więc z dwóch sąsiadów w tym samym chunku jeden widział starorzecze za granicą i schodził o 2 bloki, a drugi nie. Przykład REAL (−27777 / −27778, −138754): starorzecze 2 i 3 bloki dalej, w sąsiednim chunku.

**Poprawka (`BankShelf.waterDrop`, `SurfaceBuilder.Work.rampDrop`, `rampBand`).**
- Każda woda w zasięgu rampy (Czebyszew ≤ 3; lustro `W`, odległość `k`, kolumna `h = a − W` bloków nad nim) dopuszcza spadek `min(h, MAX_DROP, FADE − h) + 1 − k`, czyli rampę i wygaszanie liczone od tej samej wody. Kolumna bierze największy spadek po wodach w zasięgu (co najmniej 0). Przy jednym lustrze to dokładnie spadek rund 1 i 2; przy wodzie schodkowej spadek jest równy albo mniejszy niż dawniej (maksimum minimów zamiast minimum maksimów), więc nie ma nowych obniżeń. Każdy składnik zmienia się między sąsiadami o najwyżej 1 blok i na brzegu okna wynosi ≤ 0, więc wejście wody w okno nie robi stopnia.
- Kolumny spoza chunka dla rampy wybiera jedna decyzja na kolumnę spoza chunka, wspólna dla całego planu: kolumnę z pasa 3 bloków za granicą próbkujemy (albo bierzemy z `ColumnCache`), gdy pola którejś kolumny chunka w odległości do 3 bloków od niej dopuszczają wodę w tej odległości (`BankShelf.waterReach`, zapas 2 bloków jak dotąd). Wszystkie kolumny chunka widzą więc tę samą wodę za granicą. Zniknęło też odcięcie „woda w chunku bliżej niż granica”, które zmieniało wynik zależnie od chunka.
- Strażnik rundy 2 (`guardField`) i reszta planu bez zmian.

**Wyniki przeglądu (te same obszary i ziarna co recenzent; schodki ≥ 2 przy płaskim modelu, REAL / GAMEPLAY, przed → po).**

| Przegląd | Schodki ≥ 2 | Kolumny brzegowe z wodą obok wierzchu | Próbki spoza chunka na chunk |
|---|---|---|---|
| schodkowy, ziarno 4242, 150 obszarów | 7 → 0 / 46 → 0 | 86,4% / 85,8% (bez zmian) | 96,2 → 90,4 / 86,5 → 76,2 |
| losowy, ziarno 7001, 200 obszarów | 6 → 0 / 1 → 0 | 99,2% / 98,7% (bez zmian) | 45,0 → 43,2 / 50,0 → 47,4 |
| schodkowy, nowe ziarno 777, 150 obszarów | 3 → 0 (REAL) / 0 | 82,4% / 87,6% | 97,3 → 91,1 (REAL) / 82,3 |
| losowy, nowe ziarno 9001, 300 obszarów | 0 / 0 (przed: 0 / 0) | 98,2% / 98,7% | 43,1 / 44,8 |

We wszystkich przeglądach po poprawce: 0 nowych otwartych krawędzi wody, 0 przepływów z otwartych krawędzi modelu na usunięty grunt, 0 rowów, 0 suchych kolumn poniżej pobliskiego lustra, 0 stopni modelu urosłych o więcej niż 1. Stopnie modelu 1 → 2 (rampa na stromym brzegu) bez istotnej zmiany: schodkowy 4242 566 → 574 (REAL) i 313 → 308 (GAMEPLAY), czyli 0,4–0,6% obniżonych kolumn (próg testów 1%). „Kolumny brzegowe z wodą obok wierzchu” w przeglądzie schodkowym są niższe celowo: przy schodkowej wodzie strażnik zostawia brzeg na wyższym lustrze (runda 2).

**Testy JUnit.**
- `BankShelfTest`: nowe obszary nazwane REAL `fade_step` (74 601, −33 251) i `oxbow_border` (−27 778, −138 754), GAMEPLAY `fade_step` (7350, −19 466), `fade_step_b` (13 574, −5053) i `oxbow_border` (−16 353, −19 489). Na `623ab45` `shelfHoldsTheWater` nie przechodzi (schodki 2 w `fade_step` i `oxbow_border`), po poprawce: schodki 0 / 0, kolumny brzegowe z wodą obok wierzchu bez obszarów schodkowych (rund 2 i S6b) 97,7% z 1896 (REAL) i 99,4% z 2466 (GAMEPLAY) (runda 2: 97,2% z 1412 i 99,3% z 2148; teraz z obszarem `oxbow_border`), `shelfOverRandomWaterAreas` bez zmian (98,3% / 98,1%, 0 schodków).
- Nowa klasa `SteppedWaterShelfTest` (`@Tag("slow")`, 102 s w pełnym zestawie): 40 losowych obszarów przy schodkowej wodzie na skalę, wybranych jak w przeglądzie recenzenta (ziarno 4242), z kryteriami `shelfOverRandomWaterAreas` bez progu udziału kolumn brzegowych (tylko raport). Na `623ab45` nie przechodzi (REAL: ściany w obszarach a29 i a30, GAMEPLAY w a1, a6, a23 i a25), po poprawce 0 schodków, 0 rozlewów i rowów; stopnie modelu 1 → 2: 259 z 31 580 (0,8%) i 161 z 17 331 (0,93%) obniżonych kolumn, blisko progu 1% (na `623ab45` podobnie: 0,8% w REAL przy 60 obszarach).
- `GroundLayerPlanTest.noSweetBerryBushes` (M2-13, §8.7).

**Test w grze** (`-Pgametest=habitats`, obie skale, nowe obszary półki `fade_step` i `oxbow_border` w obu skalach, BUILD SUCCESSFUL z `-PtimingsReportOnly`, niżej).
- Schodki ≥ 2 przy płaskim modelu: 0 we wszystkich 9 (REAL) i 10 (GAMEPLAY) obszarach. Po 200 tickach woda poza planem w usuniętym gruncie lub z dala od otwartych krawędzi modelu: 0 wszędzie, nowe otwarte krawędzie 0. Woda nad gruntem modelu przy jego otwartych krawędziach (stan modelu, tylko raport): jak w rundzie 2 (duża rzeka REAL 56, schodkowy potok REAL 486, szew koryt 239, …) i nowe `fade_step` 30 (REAL) i 24 (GAMEPLAY).
- Kolumny brzegowe z wodą obok wierzchu (bez obszarów schodkowych): 97,8% z 769 (REAL) i 99,3% z 973 (GAMEPLAY) (runda 2: 96,9% z 423 i 99,7% z 714); `oxbow_border` 342 z 346 i 254 z 259, `fade_step` GAMEPLAY 50 ze 152 (brzeg na wyższym lustrze).
- Gleby 2252 z 2253 (REAL, 1 pod dyskiem wanilii) i 1645 z 1689 (GAMEPLAY, 39 pod dyskami, 5 pod głazami narzutowymi), `PACK_FALLBACKS` 0 z 44 587 i 0 z 69 859 sekcji, brak `ChunkHabitats` 0, `revisit` bez braków.
- Próbki spoza chunka w całej sesji: 28,6 (REAL) i 25,1 (GAMEPLAY) na chunk, z czego 48% i 46% generator użył potem ponownie (runda 2: 28,1 i 25,9; 47% i 45%).
- `VegetationClientGameTest` (`-Pgametest=vegetation -Psites=none`): transekty §4.6 bez zmian (wysokie 74,7–76,7% REAL, 69,7–87,1% GAMEPLAY, trawa i małe kwiaty 0–2,4%, goły grunt 5,3–12,2%), pokrycie koronami wiklin 81,2–85,4% / 72,1%, kosodrzewiny 69,9% / 70,5%, liście do opadnięcia 0, `HABITAT_MISS` 0.

**Pomiary czasu: niezrobione (maszyna nie była spokojna).** W czasie kroku na komputerze działała inna ciężka aplikacja (gra zajmująca stale ok. 2,7 z 12 wątków), więc według zasady pomiarów (spokojna maszyna, A/B w jednej sesji) nie mierzyłem TERRAIN ani `costTest`. W tych warunkach BIOMES w `HabitatsClientGameTest` wyszło ponad progi: nizina 0,16–0,26 ms, Beskidy 0,27–0,46 ms, rzeka 0,22–0,26 ms na chunk (na spokojnej maszynie w S5: 0,10–0,16, 0,21–0,29 i 0,16–0,20 ms); BIOMES nie zależy od planu powierzchni, więc to obciążenie maszyny, nie S6b. Dlatego test ma nowy przełącznik `-PtimingsReportOnly` (progi czasu BIOMES i `/locate biome` tylko w raporcie; domyślnie progi działają) i z nim przeszedł. Koszt planu powierzchni: pętla rampy przegląda tyle samo kolumn co dotąd (okno 7 × 7), decyzja o pasie za granicą to ok. 5 tys. tanich porównań na chunk, a próbek spoza chunka jest w przeglądach o 4–12% mniej (w grze 28,6 / 25,1 na chunk wobec 28,1 / 25,9 w rundzie 2), więc TERRAIN nie powinien wzrosnąć; pomiar `-Pgametest=stages` i BIOMES z progami M2-10 na spokojnej maszynie zostaje do S10 (albo do najbliższego kroku na spokojnej maszynie).

**Decyzje użytkownika wpisane w S6b.**
- **M2-11 (TERRAIN):** budżet 1,10 × M1 liczymy dla całego obszaru jak D1; dolina dużej rzeki REAL (1,28–1,48 × M1 w pomiarach S6) jest akceptowana (§3.6). Warianty z rund 1 i 2 nie są już potrzebne; optymalizacja planu powierzchni zostaje przy S10.
- **M2-15 (rozlew przy ukośnych stopniach lustra, domyślne, do potwierdzenia):** przyjęty wariant 1 z rundy 2: strażnik do 9 bloków, a rozlew wyższej wody po niższej przy ukośnych stopniach lustra modelu to zachowanie modelu (rzadkie rozlewisko), bez strażnika 16 bloków i bez progów w korytach.
- **M2-10 (BIOMES w górach):** próg testu w Beskidach 0,3 ms (wcześniej 0,5 ms ochrony przed regresją), nizina i rzeka 0,2 ms (rzeka wcześniej 0,5 ms) (§3.6).

**Pełny zestaw testów:** `tools/dev/run-tests test` PASS (drzewo `858efb0df5f3` przed wpisaniem tej linii, 1144 s), złoty test bez zmian i bez listy dozwolonych zmian.

**Zostaje (nie zmieniane w S6b).**
- Strażnik rundy 2 nadal decyduje o próbkowaniu spoza chunka z pól najbliższej kolumny chunka. W przeglądzie z nowym ziarnem (777, REAL) recenzencki sprawdzian strażnika znalazł 3 kolumny przy szwie dwóch potoków (88 404–88 405, 61 657–61 658) 1 blok poniżej lustra wody (434) w odległości Manhattan 9 za granicą chunka: `channelDist` kolumny chunka mierzy tam odległość do innego koryta i myli się o 2,04 bloku (zapas 2). Tak samo na `623ab45` (te same 3 kolumny), więc to stan sprzed S6b. Rozlewu nie ma: grunt między wodami leży na 435, ponad oboma lustrami (0 przepływów z otwartych krawędzi i 0 rozlewów w grze). Do S10: zapas 3 bloków albo decyzja strażnika jak w `rampBand` (z kilku kolumn chunka), z pomiarem kosztu.
- Bez zmian z rund 1–2: tarasy rampy i wzór „moro” do oceny w grze, własne bloki torfu, murszu i torfowca w M4, drobne płaty dysków wanilii przy kałużach.

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
| `tree_stand` | Jeden wzór kandydatów dla całego świata (od rundy 1 recenzji S5, §3.5.1; wcześniej losowanie warstwowe z całkowitym oczkiem c = 16/⌈√n⌉, które dawało aleje wzdłuż granic chunków): komórki 2 × 2 wyrównane do świata, kandydat w losowej kolumnie komórki, hamowanie sekwencyjne w kolejności losowych znaczników na 3 bloki także przez granice chunków (średnio 16,65 kandydata na chunk), szansa n_b/16,65. Kandydat: kod kolumny → pierwsza pasująca reguła palety {biomy, strefy, stl, zespoły} → gatunek z wag × rampa zasięgu (§9) → `PlacedFeature.place` na `OCEAN_FLOOR`. Luki: szum o fali 40–80 m usuwa 5–10%. `max_water_depth` 0–2 dla wierzby i olszy. | 1–2 ms |
| `deadwood` | `fallen_{oak,birch,spruce,poplar}_tree` według palety, 0,15–0,5 na chunk | < 0,1 ms |
| `understory` | krzewy z palety na wolnych kolumnach | < 0,2 ms |
| `waterside_zones` | Pętla po 256 kolumnach według strefy: trzcina (`sugar_cane` 2–4 albo własna trzcina) na półce; pałka (`small_dripleaf` albo własna) w wodzie 1-blokowej na dnie z błota; wiklina; okrajek `tall_grass`/`large_fern`/`bush`; lepiężnik (`big_dripleaf`) na `coarse_dirt`/`rooted_dirt`/torfie; kidzina; łacha. `setBlock(…, 2)` z `canSurvive`. Y bierzemy z `ChunkHabitats`. | ~0,1 ms |
| `ground_layer` | Pętla po 256 kolumnach: ważony stan bloku według (STL, zespół) z pokryciem w %; rośliny podwójne przez `DoublePlantBlock.placeAt`; sprawdzenie powietrza i `canSurvive` | 0,1–0,2 ms |
| `aquatic_plants` | `lily_pad` (NYMFEIDY), `seagrass`/`tall_seagrass` (ELODEIDY, rzeki) według strefy i głębokości | < 0,1 ms |

Tryb świata dyspozytor czyta z `((PolandChunkGenerator) generator).settings()`.

### 8.3 `ChunkHabitats`

- Zawartość: `int[256]` kodów, `short[256]` wierzchu gruntu, `short[256]` lustra wody, O i P chunka.
- Zapis w `fill()` jako załącznik Fabric (`AttachmentRegistry.create`; API 2.2.30 ma mixiny dla `ChunkAccess` i `ImposterProtoChunk`). Od rundy 1 recenzji S5 trwały (`persistent(ChunkHabitats.CODEC)`), bo zapisany proto-chunk tracił nietrwały załącznik (1,2–1,8% braków w sesji z powrotami).
- Odczyt w dyspozytorach przez `level.getChunk(cx, cz)`.
- Brak załącznika (dawniej proto-chunk zapisany między TERRAIN a FEATURES, np. przy C2ME, zamknięciu serwera albo powrocie gracza; od rundy 1 tylko proto-chunk zapisany przez starszą wersję) uruchamia przeliczenie z modelu (ok. 2–3 ms) i zwiększa licznik `HABITAT_MISS`.
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
| bory | `moss_carpet` 30–70% (świeży), `pale_moss_carpet` 50–80% jako chrobotki (suchy), `bush` jako krzewinki 20–40% (od S6b także borówka, decyzja M2-13: bez `sweet_berry_bush`, własna borówka w M4), `fern`/`large_fern` jako orlica 5–15% (BM) |
| grąd | `leaf_litter` 30–60%, `wildflowers` jako geofity 10–30%, `lily_of_the_valley` 3%, `fern` 5%, trawa ≤ 10% |
| buczyna | `leaf_litter` 70–90%, poza tym prawie nic |
| ols, łęgi | `tall_grass` i `large_fern` 60–90%, `bush`, `firefly_bush` przy wodzie, lepiężnik w OlJ |
| wrzosowisko | `pink_petals` jako wrzos, `pale_moss_carpet`, trawy |
| łąka | `short_grass` i kwiaty |
| hala | `short_grass`, `bush` |

Grzyby wanilijne mają ≤ 1/256, bo grzybobranie to M4. Własne kwiaty biomów dopisujemy do tagu featurów `minecraft:can_spawn_from_bone_meal`.

### 8.7 Stan po S7 (2026-10-09)

**Co zrobiono.** Wszystkie sześć dyspozytorów kroku 9 działa (Z5 bez zmian: te same listy w każdym biomie, palety w JSON z datagenu, każda warstwa z własnym ziarnem z (ziarno świata, chunk, sól warstwy)).

- **Drzewostan** (`TreeStandFeature` + czysty `plan/TreeStandPlan`). Wzór kandydatów z rundy 1 recenzji S5 zostaje (komórki 2 × 2, hamowanie na 3 bloki, 16,65 kandydata na chunk). Nowe:
  - **reguły palety** z warunkiem siedliskowym (`plan/HabitatMatch`: biomy, strefy, STL, zespoły, gleby; pusta lista = dowolne); pierwsza pasująca reguła decyduje, więc reguły stref i zespołów stoją przed regułą biomu. Strefy bez drzew: koryto, elodeidy, nymfeidy, szuwary, łacha, wiklina, okrajek, ziołorośla (też górskie), łozowisko, kamieniec, pło, kidzina, wydma inicjalna, ściana i korona klifu. Strefy z własnym drzewostanem: WIERZBY (6, wierzba 80, olsza 20), OLSZA_BRZEG (8, olsza 70, brzoza 30), SZPALER (8, olsza), GRANICA_LASU (4, świerk karłowy 90, jarząb 10). Zespoły i STL: Populetum albae (topola 60, wierzba 40), Abieti-Piceetum (Św 60, w tym 1,8% świerka olbrzymiego, Jd 30, Jrz 10), ziołorośla żlebowe (3), pas wiatrowy boru bażynowego (5, sosna niska), brzezina bagienna (BMb: Brz 80, So niska 20), jaworzyna (Jw 50, Bk 30, Js 10, Jd 10); dalej jedna reguła na biom ze składem §8.5 (wagi w promilach; bór suchy: So 66,5, So niska 28,5, Brz 5; świerczyna: 3% świerków to świerk olbrzymi); kosodrzewina: 1 karłowy świerk na chunk; torfowisko wysokie 0,3 sosny niskiej;
  - **rampa zasięgu** (`plan/SpeciesRamp`, §9): waga gatunku z flagą rośnie od 0 na progu do pełnej 0,10 dalej (Bk: max(O − 0,40; P − 0,40), Jd: P − 0,50, Św: max(0,30 − O; P − 0,50)), przesunięta o ±0,05 szumem o fali 2 km·k (nowa sól `feature.tree_stand.range_ramp`), raz na chunk z O i P z `ChunkHabitats`; reszta wagi przechodzi na gatunek zastępczy („Bk lub Św”); grab ma tylko regułę wysokości (flaga twarda);
  - **luki** (§8.2): szum o fali 60 m·k (nowa sól `feature.tree_stand.gaps`) powyżej progu 0,455 usuwa drzewa z 7,5% powierzchni (zmierzone 7,4%); szansa pozostałych kandydatów rośnie o 1/(1 − 0,075), więc średnia na chunk zostaje paletowa;
  - **drzewa w płytkiej wodzie**: wpis palety ma `max_water_depth` (olsza 1, wierzba 2 w łęgu wierzbowym, 1 w olszynie i przy rzekach B); kolumna pod wodą bierze tylko takie wpisy (pień na dnie z błota);
  - **rozstaw ≥ c/2** (§12.1, c = 16/⌈√n⌉): przy n ≤ 4 (c/2 > 3) kandydat ustępuje, gdy kandydat o niższym znaczniku bliżej niż c/2 też zostałby drzewem przy tej samej szansie (przerzedzenie bez rekurencji, liczone z samego wzoru kandydatów, więc także przez granice chunków); szansa jest podniesiona tak, by zostało n (wzór (1 − e^(−x a))/a = n dla pola wykluczenia a = π(d² − 0,7·3²)/256, stała 0,7 skalibrowana w teście: średnia w 2% od n dla n 0,125–4).
- **Warstwy kolumnowe** (`PlantLayerFeature` z jednym czystym planerem `plan/ColumnPlan` dla `deadwood`, `understory`, `waterside_zones`, `ground_layer`, `aquatic_plants`): pętla po 256 kolumnach, pierwsza reguła, która pasuje do kodu siedliska, środowiska (ląd, ląd z wodą obok wierzchu = półka, dno lub lustro wody o głębokości z zakresu) i bloku wierzchu (trawa, ziemia/coarse/rooted, podzol, błoto, mech, piasek, żwir, glina), pokrywa `coverage` kolumn roślinami z wag. Obie wartości losowe kolumny zależą tylko od współrzędnych świata (bez szwów na granicach chunków); reguła z `patch` > 1 bierze każdą z nich w 60% z płatu (komórka o boku `patch`, granica przesunięta o ±1 blok na kolumnę), a mieszanina dwóch rozkładów jednostajnych jest jednostajna, więc pokrycie i udziały zostają paletowe, a rośliny rosną kępami (zgodność sąsiadów 0,40 wobec 0,27 bez płatów). Dane kolumn (`VegetationColumns`): poziomy z `ChunkHabitats`, blok wierzchu ze świata (pod pniem jest już ziemia), woda obok wierzchu (za granicą chunka blok sąsiada). Roślina blokowa stawiana z flagą 2 tylko w wolnym miejscu (powietrze na lądzie i nad wodą, źródło wody na dnie) i gdy `canSurvive`: rośliny podwójne przez `DoublePlantBlock.placeAt` (w wodzie z `waterlogged`; od rundy 1 obie połówki poza wodą zaznaczone do post-processingu chunka), lepiężnik (`big_dripleaf`) jako liść na łodydze o wysokości z palety (od rundy 1: 1–2 bloki zamiast wanilijnych losowych 2–5), trzcina jako kolumna 2–4; roślina-feature (krzewy, kłody) to placed feature z własnym filtrem. Losowy kierunek dla bloków z `facing`.
- **Palety** (`client/datagen/ModVegetation`, tylko bloki wanilii, decyzja M2-D):
  - martwe drewno: kłody `polishforests:deadwood/<gatunek>` (kora jak w §8.4, od rundy 1 także `deadwood/gray_alder` z korą `pale_oak` w olszynie górskiej; rzadkie grzyby 5%), 0,25–0,5 na chunk według biomu;
  - podszyt: kosodrzewina `shrub/dwarf_mountain_pine` na 5% kolumn (korony 21–37 kolumn; zmierzone pokrycie koronami w rundzie 1: 69,7% REAL, 70,8% GAMEPLAY), jałowiec na wrzosowisku i w borach, leszczyna w grądzie, łęgach jesionowo-olszowym i wiązowym, lasach mieszanych i jedlinie (1–3 na chunk; od rundy 1 nie w olsie, gdzie wiklina jako wierzba szara 1,5 na chunk), wierzba piaskowa jako wiklina na wydmie szarej; nic w strefach bez drzew;
  - strefy nadwodne: pałka (`small_dripleaf`) w wodzie 1-blokowej na błocie (szuwar 60%, wody i lasy nizinne 30%), trzcina (`sugar_cane` 2–4) na półce przy wodzie modelu (biom szuwaru 75%, strefy nadbrzeżne w biomach nizinnych 70%, brzegi wód eutroficznych 50%; od rundy 1 nie przy potokach górskich ani w borach, buczynie i na wrzosowisku poza płatami szuwaru brzegowego, a przy kałużach mikroreliefu tylko 40% w szuwarze, 15% na łące wilgotnej i torfowisku niskim, 10% w lasach i na łąkach), wiklina (`shrub/osier`) w strefie i biomie wiklin na 16% kolumn (od rundy 1, w S7 11%: korony pokrywały wtedy 62–78% zamiast 80–100% z §8.5; teraz 81–85% w REAL i 72% w węższych wiklinach GAMEPLAY), w łozowisku 8%, siewki na łasze 2% i na kamieńcu 5% (wiklina rośnie też na piasku i żwirze), `firefly_bush` w wiklinach, lepiężnik (1–2 bloki) na `coarse_dirt`/`rooted_dirt` kamieńca (50%) i na `coarse_dirt`/`rooted_dirt`/błocie ziołorośli górskich (30%), olszyny (12%) i łęgu jesionowo-olszowego (4%), kidzina jako `leaf_litter` i `short_dry_grass` w pasie 2 bloków od strony mokrej plaży (50%; od rundy 1, wcześniej 30% całej strefy), dalej na suchej plaży suche trawy (8%), wydma inicjalna z suchymi trawami (15%), pło z `moss_carpet`, korona klifu z krzewinkami;
  - runo: tabela §8.6 jako reguły (pokrycie × udział): bór świeży mech 50%, krzewinki (`bush`) 25%, borówka (`sweet_berry_bush`) 8%; bór suchy chrobotki (`pale_moss_carpet`) 52%; grąd `leaf_litter` 45%, geofity (`wildflowers`) 20%, konwalia 4%, paprocie 6%, trawa 6%; buczyna `leaf_litter` 83%; ols i łęgi `tall_grass` i `large_fern` 62–68% (z krzewinkami 73–78%); strefy okrajka, ziołorośli i łozowiska 85%, z czego wysokie 77%; wrzosowisko `pink_petals` jako wrzos; łąki trawa i kwiaty; hala trawa i krzewinki; wydmy suche trawy i chrobotki;
  - rośliny wodne: `lily_pad` na nymfeidach (45%), `seagrass`/`tall_seagrass` w elodeidach (35%), w rzekach (12%), w zalewie (15%) i rzadko w morzu (4%).
- **Drzewa i krzewy** (`client/datagen/ModTrees`): krzewy `shrub/dwarf_mountain_pine`, `shrub/osier`, `shrub/hazel`, `shrub/juniper` (1–2 kłody i korona `bush_foliage`); warianty `tree/scots_pine_low` (3+2), `tree/spruce_stunted`, `tree/spruce_mega` (olbrzymi świerk wanilii); dąb i brzoza mieszają dwa kształty we własnym featurze (`weighted_random_selector`: `tree/oak_fancy` 60%, `tree/oak_small` 40%; `tree/birch_small` 80%, `tree/birch_tall` 20% z rzadkimi ulami), więc paleta nadal wskazuje jeden identyfikator gatunku.
- **Szybsze drzewa.** Wszystkie konfiguracje drzew i krzewów mają typ `polishforests:tree` (`FastTreeFeature`): ta sama konfiguracja i ten sam kształt co `minecraft:tree` przy tym samym źródle losowym, ale odległości liści liczy przeszukiwanie wszerz po tablicy bajtów skrzynki drzewa (tylko własne liście, zmieniane są tylko liście z nową odległością), bez aktualizacji kształtów bloków na powierzchni skrzynki. Od rundy 1: kłody stawiane przez rozmieszczacz korony (topola) są źródłami, liście nieosiągnięte w 6 krokach biorą odległość od sąsiadów w świecie (do 6 przebiegów), a liście wciąż z odległością 7 są usuwane, zanim zadziałają dekoratory (opis w „Runda 1 poprawek S7”). W profilu JFR wanilijna aktualizacja liści i kształtów zajmowała ok. 30% całej dekoracji; drzewostan kosztuje teraz 0,37–0,74 ms na chunk zamiast 1,0–2,6 ms.
- **Dekoracja czyta biomy jednej sekcji** (mixin `ChunkGeneratorDecorationMixin`, ryzyko R11 przeniesione z S10): `applyBiomeDecoration` zbierał biomy wszystkich sekcji 3 × 3 chunków (ok. 5% dekoracji w REAL), a nasze sekcje mają kopię tych samych biomów kolumn; pętla bierze tylko pierwszą sekcję, gdy dekoruje generator „Polska” z własnym źródłem biomów (od rundy 1 flaga wątku ustawiana w `PolandChunkGenerator.applyBiomeDecoration`, gdy źródłem jest `PolandBiomeSource`; wcześniej warunkiem był załącznik `ChunkHabitats`, który `fill()` zapisuje przy każdym źródle biomów, więc przy źródle z datapacka z biomami zależnymi od Y pętla pominęłaby wyższe sekcje).
- **Głazy narzutowe** (krok 2, §8.1): `polishforests:glacial_erratics` (`weighted_random_selector` z trzema `block_blob`: granit 5, dioryt 2, `mossy_cobblestone` 3), rzadkość 1/6, wysokość `OCEAN_FLOOR_WG`, filtr `polishforests:habitat` (`HabitatFilter`, nowy typ modyfikatora rozmieszczenia: biomy, strefy, STL, zespoły, gleby, od rundy 1 także `max_mountain_influence`; czyta `ChunkHabitats` chunka punktu) z glebami moren i piasków młodoglacjalnych (brunatna, kwaśna, rdzawa, bielicowa, inicjalna, buczynowa) i od rundy 1 z P chunka ≤ 0,3 (lądolód nie sięgał Beskidów, a ich gleby brunatne pasują do listy gleb), potem filtr biomu.
- **Nośniki mączki kostnej**: własne featury kwiatów `polishforests:flowers/{forest,meadow,wetland,mountain}` (las: geofity i konwalia; łąka: mniszek, mak, jastrun, chaber, houstonia; mokradło: mniszek jako knieć, czosnek; góry: houstonia, czosnek jako krokus, mniszek) w tagu `minecraft:can_spawn_from_bone_meal` (nowy `ModFeatureTagProvider`); nośniki biomów wskazują je zamiast kwiatów wanilii.
- **Pomiar warstw**: `ModFeatures.STATS` (czas, chunki i postawione rośliny każdej warstwy), `PolandChunkGenerator.DECORATION_NANOS`/`DECORATION_CHUNKS` (czas `applyBiomeDecoration` jednego chunka bez generacji sąsiadów); tryb `stages` loguje oba.

**Testy.**
- `TreeStandPlanTest`: determinizm; gęstość kandydatów 16,654; 48 × 48 chunków świerczyny: 12,04 drzewa na chunk, rzędy lokalne ±12%, najmniejszy rozstaw 3 bloki także przez granice, odległość do najbliższego pnia przy granicy 3,37 wobec 3,38 w środku; rozstaw ≥ c/2 i liczba drzew ±10% dla n = 12 / 9 / 7 / 4 / 1 / 0,5 / 0,3 (zmierzone 12,04 / 9,02 / 7,03 / 3,96 / 0,99 / 0,49 / 0,30; najmniejszy rozstaw 3, 3, 3, 4, 8, 8, 8 przy c/2 = 2, 2,67, 2,67, 4, 8, 8, 8); grąd 9,02 drzewa, buk 39,9% przy wadze 40%; luki 7,4% powierzchni, z lukami 9,00 drzewa i buk 39,9%, żadnego drzewa w luce; reguły stref i zespołów (wiklina bez drzew, Populetum 60% topoli, świerki karłowe na granicy lasu); rampa (połowa wagi buku, reszta dla świerka; bez świerka buk 25%); flagi zasięgu, gatunek zastępczy, drzewa w wodzie (tylko wierzby do 2 bloków, bez drzew w wodzie 3-blokowej).
- `GroundLayerPlanTest`: determinizm; od rundy 1 środowiska `PUDDLE_SHORE` i `SEAWARD_EDGE` (pierwsza reguła i pakowanie środowiska w umieszczeniu); pokrycie ±5% (0,853 / 0,802 / 0,402 wobec 0,85 / 0,80 / 0,40) i udziały ±5 punktów, z płatami i bez; pierwsza reguła według środowiska, głębokości, gruntu i siedliska; płaty skupiają rośliny bez szwów (pokrycie przy granicy chunka 0,848, w środku 0,852); wygenerowana paleta runa spełnia §4.6 w każdym gęstym biomie i strefie już bez drzew, trzciny i wiklin (trawa i małe kwiaty 0–6%, wysokie 66–81%, goły grunt 12–20%; od rundy 1 krzewinki `bush`, `firefly_bush` i `sweet_berry_bush` nie liczą się do wysokich: wysokie 64–81%).
- `FeatureOrderTest`: krok 2 z głazami, warstwy wskazują istniejące featury (krzewy, kłody), nośniki mączki w tagu `can_spawn_from_bone_meal`, reguły drzewostanu bez drzew dla stref.
- W grze nowy `VegetationClientGameTest` (`-Pgametest=vegetation`, także w `all`; `-Pscales`, `-Psites`): `generator.validate()` bez wyjątku w obu skalach, tryb roślinności naturalnej; transekty 3 rzek × 2 skale (obszar 5 × 5 pełnych chunków przy przecięciu rzeki: duża rzeka z pomiaru etapów, mała rzeka klasy B (od rundy 1 najbliższy odcinek ze strefą ziołorośli lub wierzb nadrzecznych, wcześniej miejsce łęgu B testu gleb, które nie leży przy rzece klasy B), potok górski z olszyną znaleziony przy Beskidach; od rundy 1 każdy transekt musi mieć ≥ 40 kolumn lądowych stref nadwodnych i ≥ 20 kolumn wody rzeki lub potoku); `HABITAT_MISS`; od rundy 1 pokrycie koronami wiklin i kosodrzewiny (raport) i liście do opadnięcia (< 0,1%); zrzuty punktu kontrolnego 2. W `HabitatsClientGameTest` kolumny pod głazem nie liczą się do porównania gleby, a spis drzew liczy pień jako pionowe kłody na trzech blokach nad gruntem (krzewy i leżące kłody to od S7 też kłody na wierzchu; od rundy 1 jałowiec ma 1–2 kłody, więc też się nie liczy); próg testu to ±20% palety stref, a zmierzone odchylenia w S7 mieściły się w ±10% (np. REAL łęg jesionowo-olszowy 441 / 440,6, las mieszany 250 / 263,4; GAMEPLAY las mieszany 431 / 447,3), gleby w 11 miejscach zgodne z planem, półka 96,9% i 99,7% kolumn brzegowych z wodą obok, 0 rozlewów i schodków, `PACK_FALLBACKS` 0. Ten test znalazł rozlew z wysokiej trawy morskiej stawianej przy lustrze (górna połowa `tall_seagrass` w powietrzu to nowe źródło wody nad lustrem): `tall_seagrass` stoi teraz tylko tam, gdzie obie połowy są w wodzie.

Pełny zestaw `tools/dev/run-tests test`: PASS (16 min), `checkDatagen` bez zmian, testy w grze `vegetation` i `habitats` przechodzą w obu skalach. Po rundzie 1 poprawek: PASS (18 min), `checkDatagen` bez zmian, `vegetation` i `habitats` przechodzą w obu skalach (spis drzew w biomach z ≥ 40 oczekiwanymi drzewami w ±10% palety, gleby w 11 miejscach zgodne z planem).

**Transekty §4.6 po rundzie 1 poprawek** (tryb N, kolumny lądowe stref nadwodnych, łęgów i olsów; drzewa + krzewy + rośliny wysokie / krzewinki (`bush`, `firefly_bush`, `sweet_berry_bush`, od rundy 1 osobno, nie jako wysokie) / trawa i małe kwiaty / goły grunt bez łach i kamieńców; „woda rzeki” to kolumny z wodą i biomem rzeki lub potoku):

| Skala | Rzeka | Kolumn (stref nadwodnych) | Woda rzeki | Wysokie razem (drzewa / krzewy / rośliny) | Krzewinki | Trawa i małe kwiaty | Goły grunt | Pałka w wodzie 1-blokowej |
|---|---|---|---|---|---|---|---|---|
| REAL | duża (−19 484, 11 253) | 3486 (2308) | 2719 | 76,7% (4,3 / 42,4 / 30,1) | 3,9% | 0,4% | 7,5% | 65 z 195 |
| REAL | mała (800, −4304) | 3166 (455) | 438 | 74,7% (4,1 / 9,3 / 61,3) | 5,7% | 2,1% | 9,4% | – |
| REAL | potok (155 490, 1 059 162) | 2526 (507) | 424 | 74,7% (4,4 / 14,4 / 56,0) | 6,9% | 1,2% | 8,7% | – |
| GAMEPLAY | duża (−1851, 6022) | 5606 (1962) | 606 | 69,7% (4,0 / 17,1 / 48,6) | 7,3% | 2,3% | 12,2% | 59 z 188 |
| GAMEPLAY | mała (−1400, −296) | 1571 (438) | 40 | 71,9% (3,1 / 5,2 / 63,7) | 6,1% | 2,4% | 10,5% | – |
| GAMEPLAY | potok (27 825, 3374) | 263 (249) | 15 | 87,1% (7,6 / 66,2 / 13,3) | 2,7% | 0,0% | 5,3% | – |

Kryteria (≤ 10%, ≥ 60%, ≤ 25%) spełnione we wszystkich sześciu transektach, także po przeniesieniu krzewinek z „wysokich” do osobnej grupy. Reszta kolumn to mech, paprocie, ściółka i suche trawy („inne”, 2–9%). W S7 przed rundą 1 transekt „małej rzeki” leżał w miejscu łęgu B testu gleb, które nie jest przy rzece klasy B (REAL (−16, 16): tylko potok, GAMEPLAY (112, 64): bez wody; zgłoszenie blokujące rundy 1), więc jego wiersze w pierwotnej tabeli S7 (80,1% i 76,6%) nie pokazywały małej rzeki.

**Pokrycie koronami krzewów** (od rundy 1, raport `crown cover`: kolumny lądowe z liśćmi lub kłodą 1–4 bloki nad gruntem, obszar 5 × 5 chunków): wikliny REAL 81,2% przy dużej rzece i 85,4% w miejscu wiklin testu gleb, GAMEPLAY 72,1% przy dużej rzece (przy 11% kolumn z wikliną przed rundą 1: 71,1%, 77,7% i 61,8%; §8.5: 80–100%); kosodrzewina REAL 69,7%, GAMEPLAY 70,8% (§8.5: ok. 70%).

**Budżety (§3.6).** Pomiar z rundy 1 poprawek (zastępuje pomiar S7 z dwóch przebiegów i jedną metryką): tryb `stages`, na przemian trzy konfiguracje w jednej sesji na spokojnej maszynie, po 3 przebiegi, mediana. Konfiguracje: **baza** = `2cfc53b` z biomami zastępczymi (worktree pomiarowy z tym samym czekaniem na koniec startu świata i tym samym licznikiem dekoracji), **baza leśna** = ta sama baza z `minecraft:forest` zamiast zastępczych `plains` (nizina) i `meadow` (doliny rzek) w obu presetach, czyli to samo pokrycie lasem co tryb naturalny (Beskidy, nizina GAMEPLAY i bory sandrów mają w bazie już las, więc tam baza leśna = baza), **S7** = obecny kod. Dwie metryki: czas `applyBiomeDecoration` na chunk (ms, 100 chunków, bez generacji sąsiadów) i czas etapu FEATURES w trybie `stages` (ms na 64 chunki; obejmuje też TERRAIN pierścienia sąsiadów, więc przy rzekach powtarza koszt TERRAIN z S6).

| Obszar | Dekoracja baza / baza leśna / S7 (ms na chunk) | S7 / baza | S7 / baza leśna | Etap FEATURES baza / baza leśna / S7 (ms) | S7 / baza | S7 / baza leśna |
|---|---|---|---|---|---|---|
| REAL nizina | 1,99 / 3,26 / 2,35 | 1,18 | 0,72 | 254 / 336 / 278 | 1,09 | 0,83 |
| REAL Beskidy | 3,08 / 2,59 / 2,59 | 0,84 | 1,00 | 385 / 352 / 384 | 1,00 | 1,09 |
| REAL rzeka | 2,17 / 2,89 / 2,47 | 1,14 | 0,85 | 283 / 322 / 320 | 1,13 | 0,99 |
| GAMEPLAY nizina | 2,76 / 2,87 / 2,35 | 0,85 | 0,82 | 299 / 302 / 274 | 0,92 | 0,91 |
| GAMEPLAY Beskidy | 2,77 / 2,69 / 2,47 | 0,89 | 0,92 | 381 / 365 / 360 | 0,94 | 0,99 |
| GAMEPLAY rzeka | 1,72 / 2,76 / 2,26 | 1,31 | 0,82 | 273 / 342 / 325 | 1,19 | 0,95 |

Pojedyncze przebiegi różnią się do ok. 25% (np. dekoracja REAL Beskidy: baza 3,08 / 2,76 / 3,61, S7 2,66 / 2,59 / 2,55). Rozrzut median: w Beskidach baza i baza leśna mają identyczny kod i preset, a ich mediany różnią się o 16% (dekoracja REAL), 9% (etap REAL) i 3–4% (GAMEPLAY), więc różnice ok. ±10% są w granicach szumu.

- **Przy tym samym pokryciu** (S7 / baza leśna) dekoracja mieści się w budżecie ≤ 1,0 wszędzie (0,72–1,00), a cel −20% osiąga na nizinie REAL (0,72), przy rzekach (0,82–0,85) i na nizinie GAMEPLAY (0,82); w Beskidach 0,92–1,00. Etap FEATURES: 0,83–0,99, poza REAL Beskidy 1,09 (w granicach szumu, por. wyżej).
- **Wobec bazy z biomami zastępczymi** (litera §3.6) budżet nie jest dotrzymany tam, gdzie baza nie miała drzew: nizina REAL 1,18 (etap 1,09), rzeka REAL 1,14 (1,13), rzeka GAMEPLAY 1,31 (1,19); gdzie baza miała las: 0,84–0,89 (etap 0,92–1,00).
- Warstwy S7 (ms na chunk, mediany z trybu `stages`): drzewostan 0,36–0,62 (2–9,9 drzewa), runo 0,08–0,15 (95–208 roślin), strefy nadwodne 0,03–0,19 (przy dużej rzece REAL 25 roślin i krzewów), podszyt 0,01–0,05, martwe drewno 0,02–0,03, rośliny wodne 0,01–0,02. Naprawa odległości liści i niższa korona buku nie podniosły kosztu drzewostanu (w S7 0,37–0,74).
- **LIGHT** (budżet przeniesiony z S5, §3.5.1; etap LIGHT w `stages`, ms na 64 chunki, baza / baza leśna / S7): REAL nizina 313 / 408 / 393 (1,26 / 0,96), REAL Beskidy 483 / 465 / 556 (1,15 / 1,20), REAL rzeka 354 / 415 / 390 (1,10 / 0,94), GAMEPLAY nizina 368 / 377 / 394 (1,07 / 1,05), GAMEPLAY Beskidy 476 / 456 / 482 (1,01 / 1,06), GAMEPLAY rzeka 321 / 416 / 402 (1,25 / 0,97). Światło rośnie z liczbą liści: wobec bazy z zastępczymi biomami o 1–26%, wobec bazy leśnej o −6…+6%, poza Beskidami REAL (+20%: buczyny i jedliny S7 mają więcej liści niż tajga wanilii w bazie; tam baza i baza leśna to ten sam kod, a ich mediany różnią się o 4%). Kształty koron z S7 (prosty pień z koroną `blob` zamiast `fancy_oak`) zostały; w rundzie 1 korona buku straciła jedną warstwę. §3.6 nie ma osobnego budżetu LIGHT; liczy się on do „cały chunk ≤ 48 ms” w S10.
- **`HABITAT_MISS`**: 0 na 6307 (REAL) i 6374 (GAMEPLAY) chunków z terenem w teście roślinności S7, po rundzie 1 0 na 8494 i 7851, 0 w trybie `stages`.
- **`generator.validate()`** bez wyjątku w obu skalach.
- TERRAIN i złoty test: model terenu i plan powierzchni bez zmian, `GoldenTerrainTest` przechodzi bez list.

**Odstępstwa:**
- **Kształty drzew:** buk to prosty pień 9+4 z koroną `blob` r3 o 4 warstwach (§8.4: `fancy` z korą `pale_oak`), lipa, jesion, wiąz, klony i dąb bezszypułkowy prosty pień 6+2 z koroną r3 (§8.4: `fancy_oak`), `fancy` dębu ma wysokość 4–12 zamiast 3–14 (§8.4: 5–14); typ featurów drzew to `polishforests:tree` zamiast `minecraft:tree` (te same pola JSON). Powód: koszt FEATURES; kształty własne przychodzą w M3.
- **Mixin R11** (w planie: pomiar w S10, „ewentualne nadpisanie”) wszedł już w S7 z powodu budżetu FEATURES.
- **Głazy** filtrują po glebach kodu siedliska, nie po podłożu (`GLACIAL_TILL`, piaski młodoglacjalne), bo kod siedliska nie zawiera podłoża.
- **Zastępstwa bloków spoza §8.6**: wierzba piaskowa wydmy szarej to `shrub/osier`, kidzina to `leaf_litter` z `short_dry_grass`, wydmy mają suche trawy wanilii jako piaskownicę, pło `moss_carpet`. Gęstości podszytu, martwego drewna, roślin wodnych i stref nadwodnych (poza wikliną i kosodrzewiną z §8.5) nie są podane w planie; przyjęte wartości są w paletach (`ModVegetation`) i wyżej.
- **Grzyby** tylko na kłodach (5% bloków kłody), nie w runie (§8.6: ≤ 1/256).
- **Test w grze**: transekty i zrzuty są w nowym `VegetationClientGameTest`, nie w `HabitatsClientGameTest` (krok w `m2_steps.json`: `SiedliskaClientGameTest`); transekty w trybie N, tryb D w S8. Transekt to obszar 5 × 5 chunków wokół przecięcia rzeki, nie linia.
- **Od rundy 1:** świerczyna ma 12 drzew na chunk aż do strefy granicy lasu (`TIMBERLINE`), a w niej 4 (skok na granicy strefy zamiast spadku „12 → 4” z §8.5; kod siedliska nie niesie odległości od granicy lasu, rampa może przyjść z M3/M4); wikliny GAMEPLAY pokrywają koronami 72% strefy (§8.5: 80–100%; wąska strefa, korony nad wodą i na łachach), REAL 81–85%; liście, które nie mają kłody w 6 krokach (opadłyby w grze), `FastTreeFeature` usuwa od razu (wanilia zostawia je do opadnięcia); jałowiec ma 1–2 kłody (wysokość z koroną nadal 2–3 bloki, §8.4); rośliny podwójne i pnącza zaznaczone do post-processingu kosztują przy przejściu chunka w stan pełny po jednym sprawdzeniu kształtu na zaznaczoną pozycję (2 na roślinę podwójną, w łęgach do ok. 250 na chunk) na wątku serwera; tego kosztu nie mierzyliśmy osobno.

**Do decyzji użytkownika (B3; od S6b rozstrzygnięte domyślnie decyzją M2-14, wariant 1, do potwierdzenia):** budżet FEATURES ≤ 1,0 × S0 (§3.6: baza „biomy zastępcze”). Przy tym samym pokryciu lasem (baza leśna, pomiar z rundy 1) dekoracja ma 0,72–1,00, a etap FEATURES 0,83–1,09 (REAL Beskidy w granicach szumu); wobec bazy z zastępczymi biomami bez drzew budżet jest przekroczony na nizinie REAL (1,18 / etap 1,09) i przy rzekach (REAL 1,14 / 1,13, GAMEPLAY 1,31 / 1,19). Warianty:
1. liczyć budżet wobec bazy przy tym samym pokryciu (baza leśna; koszt zerowy; rekomendacja: tryb naturalny, decyzja M2-B, z definicji ma więcej drzew niż zastępcze `plains` i `meadow`, a przy tym samym pokryciu S7 jest tańszy od wanilii);
2. rzadsze krzewy i drzewa przy rzekach i na nizinie (np. wikliny 16% → 9%, drzewostan −20%), żeby zmieścić się wobec bazy zastępczej; poniżej palet §8.5 i pokrycia wiklin z §8.5, kosztem §4.6 przy rzekach;
3. dalsza optymalizacja (własne kłody i krzewy bez `TreeFeature`, tańsze rudy w wysokim świecie REAL) przy S10.

**Przeniesione z S6 (nienaprawione, poza zakresem S7; od S6b: (1) naprawione, §7.6 „Stan po S6b”, (2) rozstrzygnięte decyzją M2-11):** dwa zgłoszenia rundy 2 recenzji S6 dotyczą planu powierzchni, nie roślinności, więc S7 ich nie zmienia: (1) ściany 2 bloków przy schodkowej wodzie i przy starorzeczach (nieciągły człon wygaszania w `nearestWater` dla wody w pierścieniu Czebyszewa 4 i decyzja o próbkowaniu spoza chunka z pól kolumny; recenzent: REAL 7 schodków w 4 obszarach, GAMEPLAY 46 w 13 obszarach w próbie przy zmianie lustra; podpowiedź naprawy w zgłoszeniu: okno wygaszania do Czebyszewa 7 z pola strażnika i decyzja o próbkowaniu na pas brzegowy chunka); (2) TERRAIN ≤ 1,10 × S0 niedotrzymany (REAL Beskidy 1,20, duża rzeka 1,48, GAMEPLAY rzeka 1,18), czeka na decyzję użytkownika z S6.

**Punkt kontrolny 2** (`docs/m2/punkt-kontrolny-2/`, `-Pgametest=vegetation`, ziarno 20260927, południe, bez HUD; zrzuty z rundy 1 poprawek: kamera w jednym z 16 kierunków 8–16 bloków od środka miejsca z najczystszym widokiem (linia do środka i do brzegów kadru), w lasach 2–4 bloki nad gruntem, w miejscach otwartych 10, wikliny 16; miejsca jak w teście gleb, §12.3, poza wiklinami GAMEPLAY przy dużej rzece; do tego 7 częstych lasów bez miejsca gleb). Do akceptacji użytkownika:

| Miejsce (§12.3) | REAL | GAMEPLAY |
|---|---|---|
| łęg A (`willow_poplar_forest`) | `willow_poplar_forest_realistic.png`: łęg wierzbowy ze zwartym runem z wysokich traw, kępa trzciny przy kałuży z piaskiem, korony wierzb po bokach. | `willow_poplar_forest_gameplay.png`: gęsty łęg wierzbowy z koronami do ziemi, wysokie trawy i kałuże. |
| łęg B (`ash_alder_forest`) | `ash_alder_forest_realistic.png`: olsze i jesiony nad wysokimi trawami, w środku płat lepiężnika z liśćmi przy ziemi (1–2 bloki). | `ash_alder_forest_gameplay.png`: olsze i jesiony, wysokie trawy i lepiężnik na błocie. |
| ols (`alder_carr`) | `alder_carr_realistic.png`: ciemne pnie olszy, runo z traw i paproci, pojedyncza trzcina przy kałuży, krzew wierzby szarej na pierwszym planie. | `alder_carr_gameplay.png`: ols z ciemnymi pniami, kępa trzciny przy kałuży i wysokie trawy. |
| szuwar jeziorny (`lake_reedbed`) | `lake_reedbed_realistic.png`: z góry pas trzciny na półce, za nim wysokie trawy, w wodzie grzybienie i pałka. | `lake_reedbed_gameplay.png`: z góry trzcina i wysokie trawy wokół małego jeziora, w wodzie pałka, za nim las. |
| wikliny (`willow_scrub`) | `willow_scrub_realistic.png`: z 16 bloków zwarte krzewy wikliny przy dużej rzece z łachą. | `willow_scrub_gameplay.png`: z 16 bloków wiklina wzdłuż dużej rzeki z trzciną i łachą, za nią łęg wierzbowo-topolowy. |
| bór suchy na wydmie (`dry_pine_forest`) | `dry_pine_forest_realistic.png`: sosny (także niskie) na podzolu i coarse dirt, chrobotki, suche trawy i krzewinki. | `dry_pine_forest_gameplay.png`: rzadki bór sosnowy z krzewinkami, chrobotkami i głazem narzutowym. |
| buczyna (`lowland_beech_forest`) | `beech_forest_realistic.png`: szare pnie buków nad ściółką (`leaf_litter`) na stoku, z boku polana z kwiatami. | `beech_forest_gameplay.png`: buczyna z szarymi pniami i ściółką, na skraju polana z geofitami. |
| regiel górny (`montane_spruce_forest`) | `montane_spruce_forest_realistic.png`: od środka (miejsce leży w strefie granicy lasu): karłowe świerki na stoku z trawą, w tle zwarta świerczyna. | `montane_spruce_forest_gameplay.png`: od środka: pnie świerków i jodeł, leżąca kłoda, ściółka i mech. |
| kosodrzewina (`dwarf_pine_scrub`) | `dwarf_pine_scrub_realistic.png`: niskie zwarte kępy kosodrzewiny na stoku z trawą i glebą. | `dwarf_pine_scrub_gameplay.png`: z góry kępy kosodrzewiny na hali z trawą. |
| plaża z wydmami (`beach`) | `beach_realistic.png`: sucha plaża z rzadkimi suchymi trawami, wyżej wydma biała (pasa kidziny przy mokrej plaży w kadrze nie widać). | `beach_gameplay.png`: wydma biała z suchymi trawami (miejsce gleb GAMEPLAY leży na wydmie), w tle wydma szara. |
| torfowisko wysokie (`raised_bog`) | `raised_bog_realistic.png`: kępy torfowca z krzewinkami i wrzosem, oczka z błotem, pojedyncze niskie sosny. | `raised_bog_gameplay.png`: torfowisko z wrzosem i krzewinkami, oczka, za nim bór bagienny z brzozami. |
| grąd (`oak_hornbeam_forest`, od rundy 1) | `oak_hornbeam_forest_realistic.png`: pnie dębu i grabu, runo z traw, geofitów i kwiatów. | `oak_hornbeam_forest_gameplay.png`: grąd z kwiatami i trawą w runie, pnie grabu. |
| bór świeży (`fresh_pine_forest`) | `fresh_pine_forest_realistic.png`: mech i krzewinki między sosnami; pień sosny i brzoza blisko kamery po bokach kadru. | `fresh_pine_forest_gameplay.png`: sosny i brzozy nad mchem i krzewinkami. |
| las mieszany (`mixed_forest`) | `mixed_forest_realistic.png`: sosny i dęby, ściółka, mech, trawy i borówka. | `mixed_forest_gameplay.png`: pnie sosny i dębu, ściółka i trawy. |
| buczyna karpacka (`montane_beech_forest`) | `montane_beech_forest_realistic.png`: szare pnie buków na stoku, ściółka i paprocie. | `montane_beech_forest_gameplay.png`: buczyna na stoku, ściółka i trawy. |
| jedlina (`upland_fir_forest`) | – (brak w pobliżu obszarów testu REAL) | `upland_fir_forest_gameplay.png`: ciemna jedlina, szare pnie jodeł po bokach, gęsty podszyt. |
| łęg wiązowo-jesionowy (`elm_ash_forest`) | `elm_ash_forest_realistic.png`: gęsty łęg; leszczyna i korony zasłaniają część kadru, runo z wysokich traw. | `elm_ash_forest_gameplay.png`: skraj łęgu z wysoką trawą na polanie. |
| olszyna górska (`gray_alder_forest`) | `gray_alder_forest_realistic.png`: szare pnie olszy szarej, runo z traw i ściółki. | `gray_alder_forest_gameplay.png` (od rundy 2, miejsce (28 072, 3880)): z góry na stoku smukłe ciemne pnie olszy, gęste runo z wysokiej trawy i paproci, liście lepiężnika i płaty ściółki. |

Uwagi do oceny: drzewa są zastępstwami wanilii (§8.4, kształty własne w M3), a trawa wysoka, trzcina i lepiężnik to bloki wanilii (M2-D). Trzcina wanilii nie rośnie w wodzie, więc pas szuwaru w wodzie jeziora to woda z pałką (`small_dripleaf`) i grzybieniami (ograniczenie M2-D). W gęstych lasach (bór świeży REAL, jedlina GAMEPLAY, łęg wiązowo-jesionowy REAL) pnie lub podszyt nadal zajmują brzeg kadru.

### Runda 1 poprawek S7 (2026-10-09)

Recenzja: 1 zgłoszenie blokujące, 7 poważnych (dwa o liściach do opadnięcia i dwa o zrzutach się pokrywają), 14 drobnych. Wszystkie sprawdzone; żadne nie okazało się fałszywym alarmem. Dwa drobne to decyzje użytkownika (borówka jako `sweet_berry_bush`, determinizm w S10), jedno opisuje zachowanie wanilii (leżące kłody nad dołkiem).

**Blokujące: transekt małej rzeki bez rzeki.** Potwierdzone sondą klasyfikatora (ziarno testu, model z `build/classes`): w promieniu 3 km od miejsca łęgu B testu gleb REAL ma tylko potok (`stream`, klasa C) i wikliny, a GAMEPLAY wokół (112, 64) nie ma wody. Najbliższe odcinki klasy B (strefy `TALL_HERBS` i `RIVERSIDE_WILLOWS`): REAL (800, −4304), 4,5 km od miejsca gleb, GAMEPLAY (−1400, −296), 1,5 km. Transekt małej rzeki zaczyna od nich wyszukiwanie spiralą klasyfikatora (do kolumny jednej z tych stref), a każdy transekt musi mieć ≥ 40 kolumn lądowych stref nadwodnych i ≥ 10 kolumn wody rzeki lub potoku (potok GAMEPLAY ma 1–2 bloki szerokości: 15 kolumn w obszarze; inaczej test pada z „not a river bank”). Wyniki w tabeli transektów wyżej.

**Liście do opadnięcia (dwa zgłoszenia poważne).** Potwierdzone; przyczyny trzy:
1. korona buku: `blob` r3 z wysokością 4 to 5 warstw (promienie 2, 2, 3, 3, 4), więc narożniki dolnej warstwy leżą 7–8 kroków od pnia; teraz wysokość 3, czyli 4 warstwy (2, 2, 3, 3), najdalszy liść 6 kroków od pnia (opis „4 warstwy” w odstępstwach był więc dotąd nieprawdziwy);
2. `FastTreeFeature` szukał odległości tylko po własnych liściach, a liście nadpisane przez koronę późniejszego drzewa dostawały odległość 7;
3. (znalezione przy naprawie) kłody, które rozmieszczacz korony topoli stawia w koronie (`replaceLeavesWithLog`), przechodzą przez `FoliageSetter`, więc liczyły się jako liście, a nie jako źródła odległości.

Naprawa w `FastTreeFeature`: kłody z rozmieszczacza korony są źródłami; liść, którego przeszukiwanie nie osiągnęło w 6 krokach, bierze odległość od sąsiadów w świecie jak `LeavesBlock.updateDistance` (kłoda 0, liść jego odległość, plus jeden; do 6 przebiegów, więc łańcuch przez obce liście i obce pnie też się liczy); liść, który nadal ma 7, zostałby usunięty przez pierwsze losowe ticki, więc jest usuwany od razu (woda zostaje, gdzie liść był zalany), zanim zadziałają dekoratory. Pnącza dekoratorów są zaznaczone do post-processingu chunka. Nowa asercja w `VegetationClientGameTest`: liście z odległością 7 bez `persistent` w transektach i we wszystkich miejscach zrzutów (5 × 5 pełnych chunków, do 48 bloków nad gruntem) < 0,1%. Wynik po rundzie 1: REAL 0 z 223 221 liści w 19 obszarach, GAMEPLAY 0 z 272 346 w 20 obszarach (recenzent przed poprawką: 7041 z ok. 697 000, 1,0%, w buczynach 2–2,9%). Koszt: drzewostan w teście roślinności 0,57 ms (REAL) i 0,63 ms (GAMEPLAY) na chunk.

**Lepiężnik.** Potwierdzone (`placeWithRandomHeight` daje 2–5 bloków). Teraz liść na łodydze o wysokości z palety (pola `min_height`/`max_height` rośliny, jak dla trzciny): 1–2 bloki, bez losowania wanilii.

**Trzcina przy potokach górskich.** Potwierdzone. Reguła trzciny w strefach nadbrzeżnych (szuwar brzegowy, wikliny, okrajek, ziołorośla, łozowisko) obejmuje tylko biomy nizinne: bez buczyny karpackiej, świerczyny, kosodrzewiny, hali, olszyny górskiej i jedliny (`HabitatCondition.withBiomes`). Przy potokach rosną tam wiklina, ziołorośla (runo) i lepiężnik.

**Trzcina przy kałużach (drobne) i nad wodami borów (drobne).** Potwierdzone. Środowisko `SHORE` oznacza od tej rundy tylko wodę modelu obok wierzchu (kolumna wody z biomem wodnym albo strefą szuwaru `REEDBED` w kodzie siedliska; przez granicę chunka kod sąsiada z jego `ChunkHabitats`), a nowe `PUDDLE_SHORE` kolumnę, obok której są tylko kałuże mikroreliefu. Trzcina przy kałużach: 40% w biomie szuwaru, 15% na łące wilgotnej i torfowisku niskim, 10% w lasach i na łąkach nad wodami eutroficznymi; resztę tych kolumn obsadza runo (wysoka trawa i paproć wielka jako turzyce). Bory, buczyna i wrzosowisko wypadły z reguły brzegów (trzcina tylko w płatach szuwaru brzegowego, które wyznacza klasyfikator, §4.4). Ograniczenie M2-D bez zmian: trzcina wanilii nie rośnie w wodzie, więc pas szuwaru w wodzie to woda z pałką (do akceptacji użytkownika razem z punktem kontrolnym).

**Zrzuty (dwa zgłoszenia poważne).** Potwierdzone. Kamera: 16 kierunków, odległość 8–16 bloków, w lasach 2–4 bloki nad gruntem, wolne 3 bloki (stopy, głowa i blok nad głową), wynik widoku z linii do środka (30 bloków, waga 3) i trzech linii do brzegów kadru (12 bloków), więc pień ani korona nie zasłania boku kadru. Wikliny z 16 bloków nad gruntem (ponad koronami); regiel górny od środka jak inne lasy (z góry korony wypełniały kadr). Wikliny GAMEPLAY: miejsce przy dużej rzece transektu (najbliższa kolumna strefy wiklin do (−1851, 6022)), bo miejsce gleb (7702, −34 380) to grąd z wąską strefą wiklin. Nowe kadry siedmiu częstych lasów bez miejsca gleb (`EXTRA_NAMES`): grąd, bór świeży, las mieszany, buczyna karpacka, jedlina (tylko GAMEPLAY: w REAL nie ma jej w pobliżu obszarów testu), łęg wiązowo-jesionowy, olszyna górska; środki sprawdzone klasyfikatorem (biom w logu).

**Budżet FEATURES (poważne).** Potwierdzone: dotąd 2 przebiegi, inna metryka niż czas etapu i porównanie nie przy tym samym pokryciu. Nowy pomiar (3 przebiegi, obie metryki, baza leśna) wyżej, w „Budżety (§3.6)”; brief mówi „bez budżetu FEATURES”, a commit rundy 1 podaje budżet jako otwarty; tytułu opublikowanego commita `b85020e` („step S7 complete”) nie zmieniamy.

**Drobne:**
- **LIGHT** (z S5): pomiar wyżej, w „Budżety (§3.6)”.
- **Mixin R11**: potwierdzone; warunek z flagi wątku ustawianej przy `PolandBiomeSource` (§8.7, punkt o mixinie).
- **Klasyfikacja transektu**: `bush`, `firefly_bush` i `sweet_berry_bush` liczą się od tej rundy jako krzewinki (osobna kolumna raportu, nie „wysokie”), także w `GroundLayerPlanTest`; pokrycie koronami wiklin i kosodrzewiny mierzone w grze (raport `crown cover`, wyniki pod tabelą transektów); wiklina pokrywała przy 11% kolumn tylko 62–78% strefy, więc od rundy 1 stoi na 16% kolumn (81–85% REAL, 72% GAMEPLAY).
- **Leszczyna w olsie**: usunięta; w olsie wiklina (`shrub/osier`) jako wierzba szara, 1,5 na chunk. **Kłody w olszynie górskiej**: nowa `deadwood/gray_alder` z korą `pale_oak` (8 z 10 kłód, reszta świerk).
- **Świerczyna przy granicy lasu**: skok 12 → 4 drzew na chunk na granicy strefy `TIMBERLINE` zostaje (kod siedliska nie niesie odległości od granicy lasu); dopisane do odstępstw.
- **Spis drzew**: jałowiec stoi na 1–2 kłodach (`StraightTrunkPlacer(1, 1, 0)`, z koroną nadal 2–3 bloki), więc spis, który liczy trzy pionowe kłody, go nie liczy; opis progu (±20%) i zmierzonych odchyleń (do 10%) poprawiony; zdanie o lepiężniku poprawione (kamieniec: tylko `coarse_dirt`/`rooted_dirt`).
- **Kidzina**: linia w pasie 2 bloków od mokrej plaży lub morza (nowe środowisko `SEAWARD_EDGE`, 50%), reszta suchej plaży z suchymi trawami (8%).
- **Głazy w Karpatach**: filtr `polishforests:habitat` ma pole `max_mountain_influence`; głazy tylko przy P chunka ≤ 0,3. Głaz nad powietrzem na stoku to zachowanie `block_blob` wanilii (kamienie leśne tajgi też tak stoją), bez zmian.
- **Rośliny bez podparcia**: obie połówki roślin podwójnych poza wodą (od dołu) i pnącza wierzb są zaznaczane do post-processingu chunka (`markPosForPostProcessing`); przy przejściu chunka w stan pełny `updateShape` usuwa połówkę, której drugą połówkę albo grunt zastąpił późniejszy feature sąsiada (pień, korona, kłoda, dysk piasku), i pnącze bez podparcia. Bez zmian: trzcina i lepiężnik na gruncie zmienionym później przez dysk sąsiada (ich `updateShape` planuje tick, który zrzuca przedmiot; przypadki rzadkie) i leżące kłody nad dołkiem 1 bloku (wanilijny `fallen_tree` dopuszcza do 2 bloków przerwy).
- **Buczyna na skraju zasięgu**: w regułach buczyny niżowej i karpackiej buk ma pełną wagę bez rampy zasięgu (nowe pole wpisu palety `range_ramp`, domyślnie `true`), bo biom leży w zasięgu buku z definicji.
- **Determinizm**: potwierdzone (featury przez granice chunków zależą od kolejności dekoracji chunka i sąsiadów, jak w wanilii; dotyczy to też chunków wnętrza obszaru). Do S10: test determinizmu §12.3 porównuje dokładnie teren przed FEATURES, a bloki po dekoracji z tolerancją przy granicach albo przy jednej kolejności generacji; dopisane w §12.3.
- **Borówka jako `sweet_berry_bush`** (7–10% kolumn borów i boru bagiennego spowalnia gracza i po wzroście rani): do decyzji użytkownika (warianty: mniejszy udział, np. 2–3%, albo `bush` zamiast borówki do M4).

**Sprawdzenie po poprawkach sondą recenzenta** (te same miejsca i liczenie co w recenzji, obszary 3 × 3 i 5 × 5 chunków): liście do opadnięcia 0 w 10 obszarach (przed poprawką 0,4–4,0% liści tych obszarów), po 300 tickach z `random_tick_speed` 1000 w buczynach REAL i GAMEPLAY 0 opadłych liści i 0 przedmiotów (było 37 i 87 liści); lepiężnik ma 1 lub 2 bloki (było 2–5, 75% ≥ 3); podstawy trzciny przy kałużach: łęg wierzbowy REAL (−3904, 3904) 54 (było 259), miejsce szuwaru REAL (−4160, 4096) w olsie 49 (było 330) i w biomie szuwaru 144 (było 278), przy wodzie jeziora 65 (bez zmian), łęg wiązowy REAL (−19 612, 11 205) 25 (było 185); przy potoku górskim REAL (155 490, 1 059 162) 0 (było 70), GAMEPLAY (27 825, 3374) 6 przy wodzie potoku w kolumnach grądu (było 7; grąd jest biomem nizinnym, więc reguła brzegów wód eutroficznych go obejmuje); głazy w Beskidach GAMEPLAY 0 (było 21 i 17 kolumn), na nizinie bez zmian.


### Runda 2 poprawek S7 (2026-10-09)

**Kadr olszyny górskiej GAMEPLAY (poważne).** Potwierdzone sondą klasyfikatora (ziarno 20260927): w kole o promieniu 16 bloków wokół dawnego środka (27 825, 3374) olszyna górska zajmuje 9% kolumn (w 5 × 5 chunkach recenzent: 66 z 6400), reszta to grąd, więc kadr pokazywał grąd. Nowy środek (28 072, 3880) leży w największym płacie olszyny w pobliżu wskazanym przez recenzenta (sonda w siatce 8 bloków wokół (28 120, 3880)): olszyna górska ma 100% kolumn w promieniu 16 bloków i 24 bloków, 73% w promieniu 40. Nowy kadr `gray_alder_forest_gameplay.png` (opis w tabeli wyżej). Żeby błąd się nie powtórzył, `VegetationClientGameTest` liczy dla każdego miejsca, którego nazwa jest identyfikatorem biomu, udział tego biomu w kole 16 bloków (co 2 bloki, klasyfikator), wypisuje go w logu i przerywa test, gdy dodatkowe miejsce (`EXTRA_NAMES`) ma mniej niż 30%. Udziały pozostałych dodatkowych miejsc (sonda): REAL 100% (grąd, bór świeży, las mieszany, buczyna karpacka), 100% (łęg wiązowo-jesionowy, 99% w promieniu 24), olszyna górska 39% (wąski pas przy potoku; recenzent: 1546 z 6400 kolumn w 5 × 5 chunkach); GAMEPLAY 100% we wszystkich sześciu pozostałych. Test `-Pgametest=vegetation -Pscales=gameplay -Psites=gray_alder_forest` przechodzi (transekty, pokrycie koronami, liście do opadnięcia 0 z 67 529, `HABITAT_MISS` 0).

**Drobne zgłoszenia rundy 2:** wszystkie poza zrzutem olszyny powtarzają zgłoszenia rundy 1, naprawione już w commitach rundy 1 (`1874af7`, `e7b28b3`, `90006f5`); sprawdzone ponownie w kodzie:
- LIGHT: wiersze baza / baza leśna / S7 są w „Budżety (§3.6)” (od rundy 1).
- Mixin R11: warunek to flaga wątku `PolandChunkGenerator.decoratingColumnBiomes()`, ustawiana tylko przy `PolandBiomeSource`, a nie załącznik `ChunkHabitats`.
- Klasyfikacja transektu: `bush`, `firefly_bush` i `sweet_berry_bush` to krzewinki (osobna kolumna), pokrycie koronami wiklin i kosodrzewiny jest mierzone w grze (`crown cover`: GAMEPLAY wiklina 72,1%, kosodrzewina 71,0%). Kolumna z powietrzem i liśćmi lub kłodą 2 bloki nad gruntem zostaje liczona jako krzew: to rzut korony krzewu (wiklina, leszczyna, kosodrzewina) na grunt, jak w mierze pokrycia koronami.
- Trzcina w borach, buczynie i na wrzosowisku: `reedBanks` ich nie zawiera (od rundy 1), trzcina przy jeziorach lobeliowych tylko w płatach szuwaru brzegowego.
- Leszczyna w olsie: usunięta (w olsie wiklina jako wierzba szara); kłody olszyny górskiej: `deadwood/gray_alder` z korą `pale_oak`.
- Świerczyna przy granicy lasu: skok 12 → 4 opisany w odstępstwach (kod siedliska nie niesie odległości od granicy lasu).
- Spis drzew: jałowiec ma 1–2 kłody, więc nie spełnia warunku trzech kłód; próg testu ±20%, zmierzone odchylenia do ±10% (opis poprawiony w rundzie 1); zdanie o lepiężniku na kamieńcu mówi tylko o `coarse_dirt`/`rooted_dirt`. Tytułu opublikowanego commita `b85020e` nie zmieniamy (bez zmiany opublikowanych commitów).
- Trzcina przy kałużach: środowisko `PUDDLE_SHORE`, w lasach 10% (było 50%); pas szuwaru w wodzie bez trzciny to ograniczenie M2-D do akceptacji.
- Kidzina: tylko pas 2 bloków od strony mokrej plaży (`SEAWARD_EDGE`).
- Głazy w Karpatach: filtr z `max_mountain_influence` 0,3.
- Rośliny bez podparcia: rośliny podwójne i pnącza zaznaczone do post-processingu chunka (`PlantLayerFeature`, `FastTreeFeature`).
- Buk na skraju zasięgu: w buczynach buk bez rampy (`range_ramp`).
- Determinizm: zapis w §12.3 (do S10).
- Borówka jako `sweet_berry_bush`: nadal do decyzji użytkownika.

**Po S6b (2026-10-09).** Borówka to wanilijny `bush` (decyzja M2-13, domyślna, do potwierdzenia): waga `sweet_berry_bush` przeszła na `bush` w regułach runa borów (suchy, świeży, nadmorski, wilgotny, mieszany), boru bagiennego, torfowiska wysokiego, lasu mieszanego i świerczyny oraz korony klifu, więc pokrycie i udział krzewinek się nie zmieniły (np. bór świeży: krzewinki 31,9% kolumn, wcześniej 24,1% `bush` i 7,7% `sweet_berry_bush`). Żadna paleta z datagenu nie ma już `sweet_berry_bush` (`GroundLayerPlanTest.noSweetBerryBushes`; krzewinki borów świeżego, wilgotnego i bagiennego 25–35%, w zakresie §8.6). Budżet FEATURES liczymy przy tym samym pokryciu lasem (decyzja M2-14, domyślna, do potwierdzenia; §3.6), więc wariant 1 z listy wyżej. Zgłoszenia z rundy 2 S6 przeniesione do S7 (ściany 2 bloków przy schodkowej wodzie i starorzeczach) naprawił S6b (§7.6, „Stan po S6b”); budżet TERRAIN rozstrzyga decyzja M2-11 (§3.6).

### 8.8 Stan po S8b (2026-10-10): płynne przejścia między biomami (zasada Z10)

Prośba użytkownika z 2026-10-09 (zasada Z10, §1): na granicach biomów i siedlisk skład i gęstość drzewostanu, podszyt, runo, gleby i kolory mają się zmieniać w pasie przejściowym, a nie na linii. Do S8b dekoracja przełączała palety dokładnie tam, gdzie zmieniał się kod siedliska kolumny: w 70 losowych obszarach z granicą biomów reguła drzew, reguła runa i gleba zmieniały się naraz na 92% (REAL N), 81% (REAL D), 95% (GAMEPLAY N) i 75% (GAMEPLAY D) par sąsiednich kolumn na granicy, a profil w poprzek granicy był skokiem 0 → 1. Biomy (Z9), model terenu i plan powierzchni w `fill()` się nie zmieniają (klasyfikator tylko przy granicy lasu w górach, niżej): złoty test przechodzi bez zmian i bez list.

**Ekotony (`feature/plan/Ecotone`, czysta Java).** Każda kolumna (w runie: płat ok. 3 bloków o postrzępionej granicy) losuje wektor u o rozkładzie izotropowym, którego rzut na każdy kierunek jest jednostajny w [−1, 1] (poziome składowe punktu jednostajnego na sferze; własne ziarno warstwy). Kolumna bierze kod kolumny w punkcie u·w dla największej klasy szerokości w (16, 14, 12, … 1 blok), przy której kod się różni, a półszerokość pary siedlisk H jest ≥ w. Przy prostej granicy udział kodu drugiej strony w odległości d wynosi więc (1 − d/H)/2: rampa liniowa od 50% na granicy do 0 w odległości H po obu stronach, pas ma 2H. Kody pochodzą z `ChunkHabitats` chunka i ośmiu sąsiadów (przy dekoracji wszystkie są po `fill()`), bez nowych próbek modelu; kolumna nieznanego sąsiada zostaje przy swoim kodzie.

| Para | H w m·k: drzewa / podszyt, runo, martwe drewno / gleba |
|---|---|
| podobne lasy (np. grąd i bór świeży, buczyna i jedlina, regiel dolny i górny, świerczyna i jej granica lasu) | 24 / 24 / 8 |
| las suchy i wilgotny (bór i ols, grąd i łęg) | 12 / 12 / 6 |
| las i zarośla (świerczyna i kosodrzewina, łęg i wikliny) | 12 / 8 / 4 |
| las i teren otwarty suchy (łąka, pole, wrzosowisko, wydma szara, hala) | 8 / 0 (płaszcz i okrajek) / 4 |
| las i teren otwarty wilgotny (łąka wilgotna, torfowiska, szuwar) | 6 / 0 (płaszcz i okrajek) / 3 |
| teren otwarty tego samego rodzaju (łąka i pole) | 12 / 12 / 6 |
| teren otwarty suchy i wilgotny, zarośla i teren otwarty | 8 / 8 / 4 |
| wydma biała i szara | 4 / 4 / 2 |
| woda, plaża, wąskie strefy (Z4: strefy nadwodne, kidzina, klif, szpaler) i ich sąsiedzi | 0 |

- Odległości mnożymy przez k (Z7) i ograniczamy do jednego chunka (16 bloków), bo dekoracja chunka widzi tylko kody 3 × 3 chunków. **Odstępstwo:** najszerszy pas w REAL ma 32 m zamiast 48 m (GAMEPLAY: 24 bloki, czyli 48 m·k).
- Biomy reguły gęstości §4.6 (łęgi, ols, olszyna górska, wikliny, szuwar) nie biorą w podszycie i runie kodu innego biomu: ich wysokie zioła zostają, a pas przejściowy leży po drugiej stronie (grąd przy łęgu dostaje ziołorośla łęgu). Drzewa mieszają się w obie strony.
- Drzewostan, martwe drewno, podszyt i runo wybierają reguły palety według kodu ekotonu (`TreeStandFeature`, `PlantLayerFeature`); strefy nadwodne i rośliny wodne zostają przy kodzie kolumny (granica wody jest ostra). Ten sam kandydat pnia ma więc gatunek i gęstość reguły drugiej strony z prawdopodobieństwem rampy: skład i gęstość zmieniają się w pasie.

**Skraj lasu: płaszcz i okrajek (`Ecotone.edges`, nowe pole palet `edges`).** Gdzie las (suchy lub wilgotny) styka się z terenem otwartym, kolumny lasu do 4 m·k od terenu otwartego i kolumny terenu otwartego do 1,5 m·k od lasu to płaszcz, a kolumny terenu otwartego do 6 m·k od lasu to okrajek (odległość fazowana 3-4 w obszarze 3 × 3 chunków; szerokości zmienia szum o fali 16 m·k, nowa sól `feature.ecotone.edges`, w granicach 0,5–1,5 wartości, więc pasy idą za skrajem bez prostych linii; najmniej 1 blok płaszcza i 2 bloki okrajka). Kolumna terenu otwartego w płaszczu bierze kod najbliższej kolumny lasu (krzewy lasu wychodzą metr–dwa na łąkę). Palety (datagen `ModVegetation`):
- podszyt, płaszcz: leszczyna na siedliskach żyznych (grąd, łęg wiązowo-jesionowy, las mieszany, buczyny, jedlina), jałowiec z domieszką leszczyny przy borach i świerczynie, „wiklina” (wierzba szara i uszata) z leszczyną przy olsach, łęgach, borze bagiennym i olszynie górskiej; 5–6% kolumn pasa. Tarnina, głóg i róża jako krzewy nie mają zastępników wanilii (M4);
- runo, okrajek: na łąkach, polach i hali wysoka trawa, paprocie, krzewinki, złocień i `rose_bush` jako róża dzika (85%); na wrzosowisku i wydmie szarej krzewinki i suche trawy (75%); na łąkach wilgotnych i torfowiskach turzyce i paprocie jako wysoka trawa i duża paproć (85%);
- drzewa przy skraju lasu mieszają się w pasie 8 m·k (6 m·k przy torfowiskach): gęstość spada od lasu do zera na łące rampą, a pojedyncze drzewa stoją na łące i w polu do 8 m·k od skraju. Działa w obu trybach (N: wrzosowiska, torfowiska, wydmy, łąki wilgotne, hala; D: maska las/łąka/pole).

**Gleby (`surface/SoilBlend`).** Przed dekoracją chunka (`PolandChunkGenerator.applyBiomeDecoration`) wierzch każdej suchej kolumny poza elementami struktur bierze glebę kodu ekotonu warstwy gleb (półszerokości jak w tabeli, płaty 3 bloków o postrzępionych granicach, własna sól), ale tylko gdy w świecie leży jeszcze wierzch własnej gleby kolumny (nie kępa, nie dno półki, nie grunt struktury). Wierzch liczy ta sama funkcja co plan (`SurfaceBuilder.soilTop`: `SoilBlocks.dry` z tymi samymi szumami płatów), więc mieszanki podzolu, trawy, ziemi gruboziarnistej, błota i mchu przechodzą w siebie płatami w pasie 4–16 bloków (REAL) i 2–8 (GAMEPLAY). Plan powierzchni, `ChunkHabitats` i heightmapy się nie zmieniają (zmienia się blok na tym samym poziomie). Tryb diagnostyczny nie miesza. `HabitatsClientGameTest` (gleby 11 miejsc) uznaje blok gleby kolumny po drugiej stronie granicy (`PolandChunkGenerator.soilBlend`).

**Granica lasu w górach (`AltitudinalBelts`, zmiana klasyfikatora).** Do S8b strefa `TIMBERLINE` (karłowate świerki, 4 drzewa na chunk) zaczynała się 60 m pod granicą lasu ostrą poziomicą, a drzewostan spadał tam z 12 do 4 drzew na chunk; nad granicą kosodrzewina z 1 świerkiem na chunk. Teraz strefa leży w płatach (szum o fali 12 m·k, nowa sól `habitat.timberline.patches`), których udział rośnie liniowo od 0 na 120 m pod granicą lasu do 1 na granicy (ta sama średnia powierzchnia strefy co dawny pas 60 m), a w najniższych 40 m kosodrzewiny płaty karłowatych świerków zajmują od połowy kolumn przy granicy do zera (strefa `TIMBERLINE` w biomie kosodrzewiny: świerki i kosówka razem). Próbkowanie pola szczytów zaczyna się 60 m niżej (`SUMMIT_FROM` 1180 → 1120 m), żeby rampa nie urwała się przy najniżej obniżonej granicy; pole szczytów służy tylko klasyfikatorowi, teren się nie zmienia. Zmieniają się strefy (nie biomy) w górach obu trybów. `TimberlineRampTest` (najwyższy masyw obu skal, średnia liczba drzew na chunk w pasach 10 m względem granicy lasu, z ekotonami drzew): REAL 12,0 na −130 m → 8,8 (−100) → 7,8 (−50) → 4,5 (−10) → 2,4 (0) → 1,6 (+30) → 1,1 (+50), GAMEPLAY 12,0 (−160) → 8,5 (−100) → 7,2 (−40) → 4,9 (−10) → 2,8 (0) → 1,1 (+40); największy krok między pasami 2,1 drzewa na chunk (przed S8b 8 na −60 m i 3 na granicy). Karłowate świerki są niższe od świerków regla, więc drzewostan jest ku górze coraz rzadszy i niższy, potem przechodzi w kosodrzewinę.

**Kolory (Biome Blend klienta).** Wanilia 26.3 (`ClientLevel.calculateBlockTint`) uśrednia kolor trawy, liści i wody w kwadracie (2r + 1)² bloków, domyślnie r = 2, czyli 5 × 5: kolor zmienia się na ok. 5 blokach (plus rozmycie biomu przez `BiomeManager`). Zmierzyliśmy różnice kolorów (ΔE CIELAB) par biomów sąsiadujących na granicach (sonda na 300 oknach 128 × 128 bloków na skalę i tryb):
- trawa i liście w trybie N: średnio ΔE 1,4–1,9 (trawa) i 1,8–2,4 (liście), najwyżej ok. 10 przy rzadkich parach (bór suchy i mieszany 9,8 / 11,5): przejście 5 bloków jest miękkie, bez zmian;
- trawa w trybie D: pole (ugór S8, `0xAFB85E`) różniło się od łąk o ΔE 22–26, a granice pola z łąkami i lasem to 40–50% wszystkich granic trybu D (średnio ΔE 11–14). Pole ma teraz `0x97BC5D` (ΔE 10 do łąki świeżej, 15 do łąki wilgotnej): mozaika dalej czytelna, przejście miękkie;
- woda: woda gór (`0x4F8FB8`) różniła się od wody nizinnej o ΔE 26 i od wody łęgów i rzek o 36 (np. olszyna górska i łęg, potok i rzeka: wyraźny pas koloru wzdłuż cieku). Teraz woda gór ma `0x4D8090`, a potok i olszyna górska pośrodku `0x4C7879` (sąsiedzi ΔE 4,5–11); woda boru bagiennego i torfowiska wysokiego `0x3C5652` zamiast `0x3A3326` (ΔE 12 do sąsiadów zamiast 28–33); jezioro dystroficzne zostaje ciemnobrązowe (granica jeziora jest ostra).

Płynny kolor z ciągłych pól odrzuciliśmy: klient nie zna ziarna świata (w trybie wieloosobowym), więc pola modelu musiałby wysyłać serwer, a większy promień mieszania w mixinie podniósłby koszt każdego przeliczenia odcieni ((2r + 1)² odczytów biomu na blok). Wybrany wariant nic nie kosztuje po stronie klienta (tylko stałe w JSON biomów).

**Pomiar ostrości (`EcotoneSharpnessTest`).** 70 losowych obszarów 5 × 5 chunków z granicą dwóch biomów lądowych na skalę i tryb, reguły palet z wygenerowanego JSON i typ gleby, z solami i płatami gry:

| | REAL N | REAL D | GAMEPLAY N | GAMEPLAY D |
|---|---|---|---|---|
| pary na granicy, gdzie drzewa, runo i gleba zmieniają się naraz: przed / po | 92,2% / 8,6% | 80,7% / 6,1% | 95,0% / 7,5% | 74,9% / 4,4% |
| szerokość przejścia (udział drugiej strony 10–90%), drzewa / runo / gleby; przed S8b 0 | 28 / 28 / 17 bloków | 25 / 25 / 13 | 24 / 25 / 8 | 15 / 14 / 6 |

Profil drzew w poprzek granicy po S8b (REAL N, udział strony B): 0,15 na −14 → 0,30 (−8) → 0,48 (−2) → 0,52 (+1) → 0,70 (+7) → 0,84 (+13); przed S8b 0 dla d < 0 i 1 dla d ≥ 0. Test pilnuje, że współbieżna zmiana spada co najmniej o połowę i poniżej 40%, a przejście drzew i gleb ma ≥ 4 bloki (REAL) i ≥ 2 (GAMEPLAY). `EcotoneTest` sprawdza rampy na prostych granicach, pary bez mieszania, regułę §4.6, nieznanych sąsiadów i pasy płaszcza i okrajka.

**Zrzuty A/B w grze (`EcotoneClientGameTest`, `-Pgametest=ecotones`; `docs/m2/przejscia/<granica>_<skala>_before.png` i `_after.png`).** Siedem granic w obu skalach, każda z góry od strony pierwszego siedliska (`<granica>_<skala>`), a cztery skraje lasu także nisko od strony terenu otwartego (`<granica>_edge_<skala>`): grąd i bór świeży (`oak_hornbeam_fresh_pine`), bór i torfowisko wysokie (`pine_raised_bog`), regiel górny z granicą lasu i kosodrzewina (`spruce_dwarf_pine`), wydma szara i nadmorski bór bażynowy (`gray_dune_coastal_pine`), buczyna karpacka i jedlina (`beech_fir`), w trybie D łęg i łąka (`floodplain_meadow_present_day`) oraz las i pole (`forest_field_present_day`). Miejsca i kierunek od pierwszego siedliska do drugiego wyszukała sonda klasyfikatora (oba siedliska ≥ 35% koła 24 bloków REAL / 16 GAMEPLAY). „Before” to ta sama klasa testu na drzewie `2271c65` (worktree), „after” to obecny kod, domyślny Biome Blend klienta. Ocena:
- **skraje lasu:** przed S8b las kończył się na linii (pole, łąka, wydma i torfowisko do samych pni); po S8b przed skrajem stoi płaszcz (leszczyna przy grądzie i łęgu, jałowce przy borze bażynowym na wydmie, wiklina przy borze bagiennym) i pas wysokich ziół z różą i złocieniem na polu i łące (`forest_field_present_day_edge_*`, `floodplain_meadow_present_day_edge_*`), a przy torfowisku pas turzyc; pojedyncze drzewa wychodzą na teren otwarty;
- **lasy podobne:** sosny i drzewa liściaste grądu przemieszane w pasie kilkunastu bloków (`oak_hornbeam_fresh_pine_*`), a buczyna i jedlina przechodzą w siebie bez linii (z góry różnica jest mała, bo oba lasy mają podobne korony);
- **granica lasu:** zwarta świerczyna przechodzi w coraz rzadsze świerki z karłowatymi w płatach, a dalej w kosodrzewinę z pojedynczymi świerkami (`spruce_dwarf_pine_*`);
- **kolory:** pole jest mniej żółte i przejście trawy pole–łąka jest miękkie na 5 blokach; woda w kałużach boru bagiennego i torfowiska jest ciemnozielonoszara zamiast brązowej, bez skoku przy sąsiednich biomach. Ostre przejścia koloru zostają tylko przy jeziorze dystroficznym i w rzadkich parach (wydma szara i łąka wilgotna, ΔE 14).

**Koszt (budżety §3.6).** Tryb `stages`, na przemian baza `2271c65` (worktree) i obecny kod, po 3 przebiegi w jednej sesji na spokojnej maszynie, mediany (ms na chunk; etap FEATURES w ms na 64 chunki):

| Obszar | Dekoracja baza / S8b | S8b / baza | Etap FEATURES baza / S8b | S8b / baza | BIOMES S8b / baza | próbkowanie i wypełnianie S8b / baza |
|---|---|---|---|---|---|---|
| REAL nizina | 2,42 / 2,62 | 1,08 | 276 / 288 | 1,04 | 0,96 | 0,97 / 0,94 |
| REAL Beskidy | 2,53 / 2,79 | 1,10 | 382 / 385 | 1,01 | 0,99 | 1,01 / 0,98 |
| REAL rzeka | 2,42 / 2,60 | 1,08 | 310 / 327 | 1,05 | 0,93 | 0,94 / 0,96 |
| GAMEPLAY nizina | 2,38 / 2,58 | 1,08 | 283 / 295 | 1,04 | 0,99 | 1,01 / 1,00 |
| GAMEPLAY Beskidy | 2,58 / 2,72 | 1,06 | 359 / 376 | 1,05 | 0,99 | 0,98 / 0,97 |
| GAMEPLAY rzeka | 2,25 / 2,50 | 1,11 | 325 / 334 | 1,03 | 0,99 | 1,01 / 1,01 |

- Ekotony kosztują 0,14–0,26 ms na chunk dekoracji (6–11%): obszar kodów 3 × 3 chunków, kody ekotonu drzew, podszytu i runa (ok. 27 µs każdy), skraj lasu (transformata odległości tylko tam, gdzie las styka się z terenem otwartym) i wierzch gleb (ok. 32 µs) liczone raz na chunk (`feature/ChunkEcotones`, pamięć ostatniego chunka wątku). Pierwsza wersja, z obszarem i kodami liczonymi osobno w każdej warstwie, kosztowała 10–28%.
- BIOMES i TERRAIN bez zmian w granicach szumu (0,93–1,01): klasyfikator dostał tylko szum płatów granicy lasu (liczony w pasie 120 m pod granicą), a niższe `SUMMIT_FROM` dotyczy ok. 60 m stoków wielkich masywów.
- LIGHT 1,01–1,04 (krzewy płaszcza).
- Wobec budżetu FEATURES ≤ 1,0 przy tym samym pokryciu lasem (M2-14; pomiar S7 dekoracja 0,72–1,00 wobec bazy leśnej, inna sesja): dekoracja po S8b wynosi szacunkowo 0,78–1,10; najdroższe Beskidy REAL (S7 1,00) przekraczają budżet o ok. 10%, czyli o wielkość rozrzutu median (§8.7). Pomiar całego chunka (≤ 48 ms) i ewentualna optymalizacja (np. jeden wektor ekotonu dla wszystkich warstw roślin) w S10.

**Testy w grze po S8b (obecny kod, obie skale, PASS).** `-Pgametest=habitats`: BIOMES REAL 0,10–0,26 ms, GAMEPLAY 0,14–0,24 ms na chunk (w budżetach M2-10), gleby 11 miejsc zgodne z planem, w tym 3–21 kolumn na miejsce z glebą siedliska po drugiej stronie granicy (np. ols GAMEPLAY 21 z 84); `-Pgametest=vegetation`: transekty §4.6 w trybie N — wysokie razem 74,1–77,3% (REAL) i 71,6–87,1% (GAMEPLAY), trawa i małe kwiaty 0,0–2,4%, goły grunt 3,4–10,5% — pokrycie koronami wiklin 81,2 / 85,4% (REAL) i 70,2% (GAMEPLAY), kosodrzewiny 69,7% i 70,2%, bez liści do opadnięcia ponad próg i bez `HABITAT_MISS`; `-Pgametest=present_day`: transekty §4.6 w trybie D 74,3–85,4% (REAL) i 71,9–87,1% (GAMEPLAY), wioski bez pni na ulicach i liści pod dachami. Zrzuty punktu kontrolnego 2 (`docs/m2/punkt-kontrolny-2/`) i trybu D (`docs/m2/gra/s8_*.png`) pokazują stan sprzed S8b (nie przeliczone).

**Odstępstwa i co zostaje.**
- Najszerszy pas w REAL 32 m zamiast 48 m (zasięg dekoracji to sąsiednie chunki); szerszy wymagałby kodów siedlisk spoza pierścienia sąsiadów, których dekoracja nie ma.
- Podszyt i runo łęgów, olsów, wiklin i szuwarów nie mieszają się z innymi biomami (reguła §4.6); pas przejściowy runa leży po stronie drugiego siedliska.
- Płaszcz z leszczyny, jałowca i wikliny: tarnina, głóg, róża (jako krzew), kruszyna i trzmielina w M4 (własne bloki i krzewy).
- Granice kolorów są 5-blokowe (Biome Blend klienta); kolory par o ΔE do ok. 15 zostają.
- Granice lasu i łąki w trybie D nadal leżą tam, gdzie wypada skok P maski (decyzja M2-20, §3.4.1): ekotony łagodzą ich wygląd, ale ich nie przesuwają.

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
  - stan po S5: atrybuty i spawny są w datagenie (`ModWorldgen`, profile `HabitatBiome.music()`, `spawns()`, `increasedFireBurnout()`), §3.5.1.
- **Kolory wody:** rzeka #4A6E5E, potok #4F8FB8, jezioro #3D6E70, dystroficzne #3A3326 (po rundzie 1 recenzji S5; było #5A4A2E, z góry wyglądało jak błotnista równina), morze #3A6A7A, zalew #5B7A5A. Kolory trawy i liści są wartościami startowymi z colormapy, kalibrowanymi na zrzutach w czterech porach roku z SS.
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

**Wybór po S5 (2026-10-08).** Wszystkie 52 tagi `has_structure/*` z jara 26.3. „Przez tag” znaczy, że lista wanilii zawiera tag, do którego należą nasze biomy (`#is_overworld`, `#is_forest`, `#is_river`, `#is_beach`, `#is_ocean`, `#is_mountain`); resztę datagen dopisuje jawnie (`ModBiomeTagProvider.STRUCTURES`). Nowe w 26.x obozowiska (`abandoned_camp_*`, namiot z drzewami danego biomu) przypisaliśmy według drzew obozu.

| Tag `has_structure/…` | Nasze biomy | Jak |
|---|---|---|
| `stronghold` | wszystkie 36; rozmieszczenie pierścieni: 30 lądowych (`stronghold_biased_to`) | przez tag `#is_overworld` |
| `mineshaft` | wszystkie 36 | lasy, góry, rzeki, plaża i morze przez tagi; jawnie 12 nieleśnych lądowych, `lagoon`, `lake`, `dystrophic_lake` |
| `trial_chambers` | wszystkie 36 | jawnie |
| `village_plains` | od S8 (M2-12): `hay_meadow`, `arable_land`, `heath`, `mixed_forest`, `oak_hornbeam_forest`, `lowland_beech_forest`; od rundy 1 poprawek S8 w trybie D bez biomów leśnych (M2-17) | jawnie |
| `village_taiga` | od S8 (M2-12): `dry_pine_forest`, `fresh_pine_forest`, `coastal_pine_forest`, `moist_pine_forest`, `mixed_pine_forest`, `upland_fir_forest`, `montane_beech_forest`; w trybie D żadne (M2-17) | jawnie |
| `pillager_outpost` | od S8: biomy obu typów wiosek (bez buczyny karpackiej, która jest w `#is_mountain`) i 4 biomy `#is_mountain` | jawnie i przez tag |
| `swamp_hut` | `alder_carr`, `fen` | jawnie |
| `igloo` | `alpine_grassland` | jawnie |
| `woodland_mansion` | `oak_hornbeam_forest`, `lowland_beech_forest`, `montane_beech_forest` | jawnie |
| `trail_ruins` | 6 borów (`dry_pine_forest`, `fresh_pine_forest`, `coastal_pine_forest`, `moist_pine_forest`, `bog_woodland`, `mixed_pine_forest`), `montane_spruce_forest`, `oak_hornbeam_forest` | jawnie |
| `ruined_portal_standard` | 18 leśnych, `river`, `stream`, 12 nieleśnych lądowych | lasy, rzeki i plaża przez tagi, nieleśne jawnie |
| `ruined_portal_swamp` | `alder_carr`, `raised_bog`, `fen` | jawnie |
| `ruined_portal_mountain` | `montane_beech_forest`, `montane_spruce_forest`, `dwarf_pine_scrub`, `alpine_grassland` | przez tag `#is_mountain` |
| `ruined_portal_ocean`, `shipwreck` | `sea` | przez tag `#is_ocean` |
| `shipwreck_beached`, `buried_treasure` | `beach` | przez tag `#is_beach` |
| `ocean_ruin_cold` | `sea` | jawnie |
| `abandoned_camp_forest` | `mixed_forest`, `oak_hornbeam_forest`, `lowland_beech_forest`, `elm_ash_forest`, `montane_beech_forest` | jawnie |
| `abandoned_camp_old_growth_pine_taiga` | `dry_pine_forest`, `fresh_pine_forest`, `coastal_pine_forest`, `moist_pine_forest`, `mixed_pine_forest` | jawnie |
| `abandoned_camp_birch_forest` | `bog_woodland` (brzezina bagienna) | jawnie |
| `abandoned_camp_old_growth_spruce_taiga` | `montane_spruce_forest` | jawnie |
| `abandoned_camp_taiga` | `upland_fir_forest` | jawnie |
| `abandoned_camp_dappled_forest` (topole) | `willow_poplar_forest` | jawnie |
| `abandoned_camp_meadow` | `hay_meadow` | jawnie |
| `abandoned_camp_swamp` | `alder_carr` | jawnie |
| `abandoned_camp_bamboo_jungle`, `_cherry_grove`, `_flower_forest`, `_old_growth_birch_forest`, `_pale_garden`, `_savanna`, `_snowy_taiga`, `_sparse_jungle`, `_windswept_forest`, `_wooded_badlands` | brak | drzewa i warunki obce Polsce |
| `ocean_monument`, `ocean_ruin_warm`, `desert_pyramid`, `jungle_temple`, `ancient_city`, `village_desert`, `village_savanna`, `village_snowy`, `mineshaft_mesa`, `ruined_portal_desert`, `ruined_portal_jungle` | brak | wymagają warunków nieobecnych w świecie |
| `bastion_remnant`, `nether_fortress`, `nether_fossil`, `ruined_portal_nether`, `end_city` | nie dotyczy | Nether i End |

`/locate structure` w `HabitatsClientGameTest` znajduje twierdzę, kopalnię i komnaty prób w obu skalach (§3.5.1); położenie pozostałych struktur na terenie 1:1 ocenią zrzuty w S10.

**Struktury w trybie roślinności naturalnej (domyślnym, decyzja M2-B; runda 1 recenzji S5).** W tym trybie nie ma `hay_meadow`, `arable_land` ani `wet_meadow` (recenzent nie znalazł ich w promieniu 60 km w żadnej skali). Skutki:
- `abandoned_camp_meadow` stoi teraz także na `heath` i `alpine_grassland` (hala jak wanilijna łąka górska), wcześniej nie mogło powstać wcale;
- `village_plains` i posterunki nizinne zostają tylko na wrzosowisku (≤ 5% sandru): w REAL recenzent nie znalazł wioski równinnej w promieniu ok. 54 km (wyszukiwanie 5,4 s), w GAMEPLAY najbliższa była 5,4 km od środka. Wioski wanilijne to rozwiązanie do M8 (potem polskie wsie, które w trybie „dzisiejsza Polska” mają łąki i pola). **Decyzja M2-12 (2026-10-09):** wioski i posterunki rozbójników także w lasach, jak w wanilii (`has_structure/village_*` i `pillager_outpost` w odpowiednich biomach leśnych i nieleśnych, typ wioski dobrany do biomu, np. `village_plains` na łąkach, wrzosowisku i w lasach liściastych, `village_taiga` w borach i świerczynach; dokładny przydział w S8), nie tylko na wrzosowisku. **Wdrożone w S8** (tabela wyżej, §3.4.1): wioski równinne na otwartym lądzie i w lasach liściastych i mieszanych, tajgowe w borach, jedlinie i buczynie karpackiej (drewniane wsie Beskidów), w obu trybach. Bez wiosek: mokradła, łęgi, olsy, łąki wilgotne (dna dolin), wydmy, plaża, regiel górny, kosodrzewina, hala (powyżej najwyżej położonych wsi Polski) i wody. `/locate structure #minecraft:village`: REAL średnio 634 bloki i 50–85 ms, GAMEPLAY 349 bloków i 21–38 ms. **Runda 1 poprawek S8:** w trybie D wioski tylko w biomach nieleśnych (M2-17, `ChunkGeneratorStructureMixin`); generator wyrównuje grunt pod sztywnymi elementami struktur z `beard_thin`/`beard_box` (`surface/StructureGround`, odpowiednik `Beardifier`), a drzewostan i runo omijają obrysy, ulice i zasięg koron (§3.4.1); **runda 2:** tę samą regułę M2-17 widzi tani test struktur `/locate` i map odkrywców (`StructureCheckMixin`), a wyszukiwanie pomija typy wiosek, które nie mogą wystartować (w trybie D tajgowe), więc mapa wioski tajgowej i `/locate structure minecraft:village_taiga` od razu nic nie znajdują;
- igloo tylko na hali w Beskidach.

`/locate structure` dla struktury nieobecnej w pobliżu blokuje wątek serwera na długo (zmierzone przez recenzenta: igloo 30,2 s GAMEPLAY bez wyniku i 5,4 s REAL, `abandoned_camp_meadow` 19,4 s GAMEPLAY przed zmianą tagu, `abandoned_camp_old_growth_spruce_taiga` 12,3 s GAMEPLAY, wynik 27,9 km, `ruined_portal_ocean` 12,5 s REAL). W GAMEPLAY to głównie budowanie zimnych kafli siatek w promieniu ok. 51 km. Wanilia wypisuje „0 bloków” dla wyników dalszych niż ok. 46 km (przepełnienie int w `dist2d`), np. `pillager_outpost` w (40512, 32304) REAL. Opis też w `docs/02-wydajnosc.md` (znane problemy).

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
| `BiomeSharesTest` | 200 tys. punktów, 1000×1000 km REAL i 30×30 km GAMEPLAY, 3 ziarna (rozrzucone punkty: ok. 16 `landElevation` na punkt w siatce terenu, §3.2). Tryb N: lesistość ≥ 88%, SANDR bory ≥ 85% lasu. Tryb D: lesistość 26–34%, bory 45–58% lasu, typy świeże 50–70%, olsy i łęgi 3–6%; od S8 także lesistość wnętrz typów krajobrazu z co najmniej 2000 kolumnami (tabela w §3.4.1; Beskidy i POBRZEŻE REAL tylko wypisywane), od rundy 1 poprawek S8 łąki 8–18% lądu. Oba tryby na tych samych próbkach |
| `StructureGroundTest` | (runda 1 poprawek S8) broda pod obrysem i skarpy 1 blok na blok, ulice i głębokie elementy bez zmiany gruntu, sprzeczne elementy, maska odległości |
| `PresentDayModeTest` | (S8) ten sam teren i woda modelu, STL, zespół i strefa w obu trybach (szpaler tylko w D), wierzch różny tylko w mikroreliefie |
| `PolandClimateTest` | obie skale: T(0 m) = T_bazowa; latem śnieg ≥ 1950 m; przy −0,25 próg 1050–1150 m; wszystkie T_bazowa ≤ 0,8 |
| `BiomeJsonTest` | 36 plików; T ≤ 0,8; klucze lang PL i EN; obecność w `is_overworld`, `trial_chambers` i `polish_climate` |
| `FeatureOrderTest` | listy wszystkich biomów są identyczne poza nośnikiem mączki; nośnik jest ostatni; każde id istnieje |
| `TreeStandPlanTest`, `GroundLayerPlanTest` | determinizm; średnia liczba drzew ±10% od palety; rozstaw ≥ c/2; udziały gatunków ±5%; pokrycie runa ±5% |
| `SoilTest` | `coverBlocks` w obu skalach; wierzch gleb leśnych należy do `#supports_vegetation`; liczba stanów w sekcji przy powierzchni ≤ 32 |
| `PolishForestsCommandsTest` | nowe cele `biome` i `zone` dają się znaleźć |
| `SampleCostTest` | raport (bez asercji): µs na `sample` i `classify` dla 100 tys. kolumn wobec M1 |
| `EcotoneTest` | (S8b, Z10) przy prostej granicy udział kodu drugiej strony to rampa liniowa (1 − d/H)/2 dla par podobnych lasów, lasów suchych i wilgotnych, lasu i łąki (drzewa i gleby, obie skale); woda, wąskie strefy i plaże bez mieszania; łęgi i olsy zachowują swoje rośliny; nieznani sąsiedzi; płaszcz i okrajek o nominalnych szerokościach i nieprostej granicy |
| `EcotoneSharpnessTest` | (S8b) w 70 losowych obszarach 5 × 5 chunków z granicą biomów na skalę i tryb: udział par sąsiednich kolumn na granicy, gdzie reguła drzew, reguła runa i gleba zmieniają się naraz, spada co najmniej o połowę i poniżej 40%; przejście drzew i gleb w poprzek granicy ma ≥ 4 bloki (REAL) i ≥ 2 (GAMEPLAY) |
| `TimberlineRampTest` | (S8b) na najwyższym masywie obu skal średnia liczba drzew na chunk w pasach 10 m wysokości względem granicy lasu zmienia się między sąsiednimi pasami o ≤ 2,5 |

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
| determinizm | dwa światy z tym samym ziarnem, z paczką optymalizacyjną (C2ME) i bez; skrót bloków 64 chunków identyczny. Od S7 (runda 1 recenzji): featury przez granice chunków (korony, kłody, rośliny przy granicy) zależą od kolejności, w jakiej chunk i jego sąsiedzi przechodzą dekorację, jak w wanilii (w recenzji S7 dwa przebiegi z tym samym ziarnem różniły się o 0,1–1,9% liści w obszarach 5 × 5 chunków), więc test w S10 porównuje skrót terenu (stan przed FEATURES) dokładnie, a bloki po dekoracji z tolerancją różnic przy granicach chunków albo przy wymuszonej jednej kolejności generacji |
| `UiAndCommandsClientGameTest` | wszystkie cele; `here` zawiera biom, siedlisko, strefę, glebę i T; od S8 przełącznik „Krajobraz” daje tryb D |
| `EcotoneClientGameTest` | (S8b, `-Pgametest=ecotones`, tylko z nazwy) zrzuty 7 granic biomów w obu skalach (5 w trybie N, 2 w trybie D) do `docs/m2/przejscia/`; ta sama klasa robi zrzuty „przed” na drzewie bez ekotonów |
| `PresentDayClientGameTest` | (S8, `-Pgametest=present_day`) świat w trybie D w obu skalach: `validate()`, transekty §4.6, `/locate structure #minecraft:village` z 5 punktów w obu trybach (od rundy 1 poprawek biom w środku elementu startowego, w trybie D żadna wioska w lesie, każda wioska bez pni na ulicach, liści pod dachami, roślin na podłogach i wiszących podłóg; od rundy 2 w trybie D `/locate structure minecraft:village_taiga` i mapa wioski tajgowej nic nie znajdują w < 2 s, w trybie N `/locate` znajduje wioskę tajgową), zrzuty mozaiki i wioski (z góry, z ulicy, od strony spadku), tryb po ponownym otwarciu zapisu |

### 12.4 Definicja ukończenia M2

1. `./gradlew build test runClientGameTest` przechodzi w obu skalach i obu trybach. Datagen jest aktualny. W logach nie ma „Feature order cycle”, „Unknown registry key” ani „Tried to biome check”.
2. Świat Polska nie ma biomów spoza `polishforests`. F3 pokazuje biom, STL i strefę.
3. Kryteria pasa nadrzecznego z §4.6 są spełnione w obu skalach i trybach. To jest prośba użytkownika.
4. Śnieg i pory roku jak w §6.1, z SS i bez.
5. Działają twierdze, kopalnie i komnaty prób. Każdy biom i każda strefa dają się znaleźć komendą.
6. Budżety z §3.6 są dotrzymane.
7. Użytkownik zaakceptował PNG siedlisk (punkt kontrolny 1) i zrzuty roślinności (punkt kontrolny 2). *Stan 2026-10-09:* zrzuty punktu kontrolnego 2 (`docs/m2/punkt-kontrolny-2/`, strona https://claude.ai/artifact/FKKFrZZMn2FZmmevt9nAVz) **czekają na akceptację użytkownika**; punkt 7 jest otwarty, dopóki ich nie zaakceptuje.
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
5. `#minecraft:is_mountain` włącza `pillager_outpost`, `ruined_portal_mountain` i kopalnie. ~~Nie dodajemy do niego naszych biomów.~~ Zastąpione decyzją M2-C (§10): tag dostają cztery biomy górskie (`montane_beech_forest`, `montane_spruce_forest`, `dwarf_pine_scrub`, `alpine_grassland`), bo posterunki, portale górskie i kopalnie w górach są pożądane.
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
