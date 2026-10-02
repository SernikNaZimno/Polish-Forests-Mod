# M2: biomy, siedliska, gleby i temperatura. Plan wykonawczy

Data: 2026-10-02. Plan powstał z pięciu raportów rozpoznania (kod, wanilia 26.3, ekologia, klimat i Serene Seasons, weryfikacja w sieci), trzech niezależnych projektów i oceny dwóch sędziów.

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
1. Świat Polska używa wyłącznie biomów `polskielasy:*` (36).
2. Każda kolumna dostaje siedlisko: biom, strefę, STL, zespół, glebę, pokrycie i flagi zasięgu gatunków.
3. Brzegi rzek, jezior i starorzeczy porastają wikliny, ziołorośla, szuwar, łęgi i olsy. Krótka trawa i kwiaty zajmują tam najwyżej 10% kolumn. Tego wprost chciał użytkownik.
4. Temperatura spada z wysokością w metrach (0,55 °C/100 m). Znika letni śnieg na sandrach i w Beskidach.
5. Gleby i bloki powierzchni zależą od siedliska (bloki wanilijne, M2-D).
6. Drzewa i rośliny to zastępstwa wanilijne za warstwą `polskielasy:drzewo/*`, `polskielasy:krzew/*` i paletami JSON. M3 i M4 podmieniają pliki, a biomy zostają bez zmian.

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
| Z1 | Siedlisko liczy czysta funkcja `Klasyfikator.klasyfikuj(ColumnSample, UstawieniaSiedlisk)`. Wynik to jeden `int`. Wołają ją: źródło biomów (16 razy na chunk), `fill()` (256 razy), dyspozytory roślinności (przez mapę chunka), komendy, podgląd PNG i testy. | Biom, gleba i roślinność nie mogą się rozjechać. Całość da się testować bez gry. |
| Z2 | Biom zależy tylko od kolumny (2,5D), nie od Y kwarty. Piętra górskie wynikają z wysokości gruntu w metrach. To zmiana sekcji 4 architektury. | Piętra idą za gruntem. `spawnOriginalMobs` czyta biom na maxY. Render opadu bierze biom na Y kamery. Trawa nie zmienia koloru w powietrzu. Znika ok. 8400 wywołań `biomeFor` na chunk. |
| Z3 | Biom niesie tylko to, czego nie da się zrobić per kolumna: kolory, atmosferę, spawny wanilii, temperaturę bazową, tagi (struktury, SS) i nazwę w F3 i `/locate`. | Mało biomów, bez kopii regionalnych i piętrowych. |
| Z4 | Strefy węższe niż ok. 12 m (3 kwarty) nie są biomami, tylko polem `strefa`. Malują glebę i mikrorelief w `fill()` i sterują roślinnością w rozdzielczości bloku. | Rozmycie `BiomeManager` (±2 bloki) nie psuje pasów szuwaru i wikliny. Każdy ciek, także 1,5-metrowy, dostaje pas roślinności. |
| Z5 | Krok 9 dekoracji to ta sama krótka lista dyspozytorów w każdym biomie. Palety są w JSON. Dyspozytory mają własne ziarno (seed, chunk, warstwa). | Cykl „Feature order cycle” po naszej stronie jest niemożliwy. Cudze mody nie przesuwają naszej dekoracji. |
| Z6 | Nowe pola modelu pochodzą z obliczeń już wykonanych w `sample` i z dwóch siatek z pamięcią. W generacji nie wołamy `describe()`, `coastDistance()` ani `landElevation()`, poza budową kafli siatek. | Próbka drożeje najwyżej o kilka procent. |
| Z7 | Progi zawsze w metrach modelu. Odległości mnożymy przez `k = local` (REAL 1, GAMEPLAY 0,5) albo skalujemy szerokością koryta `W` (w GAMEPLAY już ×0,2). Minima stref podajemy w blokach. | Działa w obu skalach świata. |
| Z8 | Jedno źródło prawdy: enumy `Biom`, `Strefa`, `Stl`, `Zespol`, `Gleba`, `Gatunek`. Z nich datagen robi biomy, featury, tagi i lang, a komendy biorą z nich cele. | Brak dublowania. Testy pilnują spójności. |
| Z9 | Biom wybierają tylko wejścia o fali ≥ 64 m. Drgania granic stref o fali 30–60 m zmieniają tylko strefę, zespół i glebę. | Biomy nie tworzą jednokwartowych wysepek. |

---

## 2. Biomy (36)

Identyfikatory są po polsku w ASCII (`polskielasy:<id>`) i od M2 zamrożone, bo chunki zapisują biomy po nazwie. Kolejność enuma `Biom` jest stała, nowe biomy dopisujemy tylko na końcu. Nazwy zarezerwowane na M5 (nierejestrowane w M2): `turnie`, `piargi`, `polonina`, `murawa_kserotermiczna`, `bor_limbowy`.

**Oznaczenia:**
- `H`: wysokość gruntu w m n.p.m.; `H*`: H po korekcie ekspozycji i szumu (§5.1);
- `trofia`: B, BM, LM, L; `DGW`: głębokość wody gruntowej w m (§3.3);
- `wyp`: lokalna wypukłość w m; `nach`, `eksp`: nachylenie i ekspozycja z siatki 32 m;
- `O`, `P`: oceaniczność i podgórskość, 0–1;
- `wP`, `wB`: wagi pogórza i Beskidów;
- `d`: odległość od brzegu koryta; `W`: szerokość koryta; `u`: położenie w dnie doliny (0 przy korycie, 1 na skraju); `s`: odległość od brzegu wody stojącej; `z`: głębokość wody;
- `cD`: odległość od brzegu morza; `B` = 60·k, `D` = 220·k: plaża i pas wydm z modelu;
- klasy cieków A, B, C: §4.

### 2.1 Leśne (18)

| # | id | Nazwa | STL | Warunek wyboru |
|---|---|---|---|---|
| 1 | `bor_suchy` | Bór suchy (chrobotkowy) | Bs | trofia B, DGW > 4 i (wydma ≥ 4 m lub wyp > +2 m); H < 350 |
| 2 | `bor_swiezy` | Bór świeży | Bśw | trofia B, DGW > 2, poza pasem nadmorskim; H < 500 |
| 3 | `bor_bazynowy` | Nadmorski bór bażynowy | Bśw/Bs nadmorskie | SAND, B+D+170k ≤ cD < 2000k, H < 40, DGW > 0,5; pierwsze 250k to wariant wiatrowy |
| 4 | `bor_wilgotny` | Bór wilgotny | Bw, BMw | trofia B lub BM, 0,8 < DGW ≤ 2 |
| 5 | `bor_bagienny` | Bór bagienny i brzezina bagienna | Bb, BMb | DGW ≤ 0,5 na SAND; pierścień 20–150k wokół torfu ombro (Odstępstwo S2: `wody.s` oczka sięga tylko 45 m, więc pierścień to 20k–45 m); oczko torfowe na SANDR o promieniu < 75k; H < 400 |
| 6 | `bor_mieszany` | Bór mieszany | BMśw | trofia BM, DGW > 2, (P < 0,5 lub H < 250) |
| 7 | `las_mieszany` | Las mieszany | LMśw, LMw | trofia LM, DGW > 0,8, (P < 0,5 lub H < 250) |
| 8 | `grad` | Grąd | Lśw, Lw | trofia L, DGW > 0,8, gdy nie wchodzi buczyna ani jedlina; na POGÓRZU H < 400 |
| 9 | `buczyna_nizinna` | Buczyna niżowa (żyzna i kwaśna) | Lśw, LMśw | trofia L lub LM, DGW > 2, drenaż (wyp ≥ 0 lub nach > 3°), flaga BUK; udział rośnie z O; H < 350 |
| 10 | `ols` | Ols | Ol, LMb | DGW ≤ 0,3 przy wodzie stagnującej: zastoiska den, pierścienie jezior na glinie, mule i torfie, torf minero; H < 500 |
| 11 | `leg_jesionowo_olszowy` | Łęg jesionowo-olszowy | OlJ | dna cieków klasy B, źródliska, wysięki u podnóża zboczy, wąskie dna klasy C poniżej 700 m; H < 600 |
| 12 | `leg_wierzbowo_topolowy` | Łęg wierzbowo-topolowy | Lł miękki | dno cieku klasy A, d ≤ D_top i u < 0,35 (§4.1); H < 300 |
| 13 | `leg_wiazowo_jesionowy` | Łęg wiązowo-jesionowy | Lł twardy | dno cieku klasy A poza łęgiem miękkim i zastoiskami |
| 14 | `jedlina_wyzynna` | Wyżynna jedlina i buczyna | BMwyż, LMwyż, Lwyż | P ≥ 0,5, flaga JODŁA, trofia BM, LM lub L (L na stokach N), H 250–650, poza dnami |
| 15 | `buczyna_gorska` | Buczyna karpacka | LG, LMG | wP + wB > 0,5, regiel dolny (§5.1); na POGÓRZU stoki N powyżej 450 m |
| 16 | `swierczyna_gorska` | Świerczyna górska | BG, BWG, BMG | regiel górny do granicy lasu; w reglu dolnym wariant Abieti-Piceetum (§5.1) |
| 17 | `olszyna_gorska` | Olszyna górska | LłG, OlJG | dno cieku klasy C, d ≤ min(60k, max(10k, 3W)), H 300–1000 (stoki N do 900); młaki |
| 18 | `kosodrzewina` | Kosodrzewina | subalpejskie | granica lasu ≤ H* < próg hali, tylko duży masyw (§5.1) |

### 2.2 Nieleśne lądowe (12)

Tryb N to „roślinność naturalna”, tryb D to „dzisiejsza Polska”.

| # | id | Nazwa | Warunek wyboru |
|---|---|---|---|
| 19 | `torfowisko_wysokie` | Torfowisko wysokie | PEAT ombro: środek misy lub oczka torfowego na SANDR albo POBRZEŻU o promieniu > 75k, poza dnem doliny; w trybie D także Bb poza lasem |
| 20 | `torfowisko_niskie` | Torfowisko niskie i przejściowe | PEAT minero w zastoiskach szerokich den (półszerokość dna > 300k, u > 0,5, spadek < 0,5‰); brzeg zalewu przy h ≤ 0,4; duże oczko torfowe na glinie |
| 21 | `szuwar` | Szuwar | woda z ≤ 1,5 m w jeziorze eutroficznym, starorzeczu lub zalewie; ląd przy h ≤ 0,3. Biom tylko w pasie ≥ 12 m, węższy pas to strefa |
| 22 | `wikliny` | Wikliny nadrzeczne | strefa WIKLINA, ŁACHA lub KAMIENIEC, gdy jej pas ma ≥ 12 m |
| 23 | `wrzosowisko` | Wrzosowisko i murawa napiaskowa | D: Bs i Bśw poza lasem; N: prześwity na wydmach (≤ 5% SANDR) |
| 24 | `laka_wilgotna` | Łąka wilgotna | D: dna dolin poza pasami łęgu oraz Ol, OlJ, Lw i Bw poza lasem |
| 25 | `laka_swieza` | Łąka świeża i polana | D: część Lśw i LM poza lasem; polany reglowe poniżej 800 m |
| 26 | `pole` | Pole (w M2 jako ugór) | D: L, LM i BMśw poza lasem przy nach < 5° |
| 27 | `plaza` | Plaża | cD < B; podnóże klifu jako wariant kamienisty |
| 28 | `wydma_biala` | Wydma biała | B ≤ cD, podłoże BEACH_SAND |
| 29 | `wydma_szara` | Wydma szara | SAND, cD < B+D+170k, niski brzeg |
| 30 | `hala` | Piętro alpejskie | H* ≥ próg hali, duży masyw |

### 2.3 Wodne (6)

| # | id | Warunek wyboru |
|---|---|---|
| 31 | `morze` | WaterKind SEA, cD < 0 |
| 32 | `zalew` | SEA, cD ≥ 0, z > 1,5 m |
| 33 | `rzeka` | RIVER, klasa A lub B |
| 34 | `potok` | RIVER, klasa C |
| 35 | `jezioro` | LAKE, KETTLE, OXBOW, jezioro bezodpływowe; poza dystroficznymi; z > 1,5 m lub jezioro oligotroficzne |
| 36 | `jezioro_dystroficzne` | KETTLE lub LAKE na SANDR przy torfie ombro albo przy hashu jeziora < 0,3 |

Jeśli użytkownik wybierze wariant minimalny (pytanie o granulację), łączymy biomy tak:
- `bor_bazynowy` staje się zespołem `bor_swiezy`;
- dwa łęgi nadrzeczne łączą się w `leg_nadrzeczny`;
- `potok` łączy się z `rzeka`;
- `jezioro_dystroficzne` łączy się z `jezioro`;
- dwie łąki łączą się w `laka`;
- dwie wydmy łączą się w `wydma`;
- `wikliny` są tylko strefą.

Reguły i strefy zostają bez zmian, zmienia się tylko tabela biom ← siedlisko.

---

## 3. Model i źródło biomów

### 3.1 Nowe pola `ColumnSample`

Nowe pola trafiają do zagnieżdżonych rekordów z **samymi typami prostymi i enumami, bez tablic**. Test `sameSeedGivesSameTerrain` porównuje rekordy przez `equals`, a `equals` porównuje tablice po referencji. Nowe szumy powstają przez `root.derive("habitat.*")`, więc teren się nie zmienia.

| Rekord.pole | Znaczenie | Źródło | Koszt |
|---|---|---|---|
| `teren.rawSurface` | teren przed wcięciem dolin i jezior (m) | `LM:541`, wartość przed `rivers.query` | 0 |
| `teren.coastD` | cD (m) | `LM:540` | 0 |
| `teren.wSandr … wPobrzeze` | 6 wag typów: SANDR, WYSOCZYZNA, RÓWNINA, POGÓRZE, BESKIDY, POBRZEŻE | `Blend` (`LM:543-547`) | 0 |
| `teren.formy` (short, bity) | WYDMA, WAL_MORENOWY, GRZBIET, DOLINA_GORSKA, PLAZA, WYDMA_NADMORSKA, KLIF, ZRODLO | warunki z `describe` przeniesione do `sample` (z `raw`, `coastD`, `surface`) | ~0 |
| `teren.wyp` | lokalna wypukłość (m): składowe krótkofalowe (ripple i wydma, pagórki i wał, flisz) ważone wagami `blend` | kontekst wyjściowy w `cellElevation` | ~0 |
| `teren.wydma`, `teren.grzbiet`, `teren.masyw`, `teren.klif` | wysokość wydmy (m), profil fliszu p[0] (0 dolina, 1 grzbiet), siła masywu 0–1, wysokość krawędzi przy brzegu | `duneHeight`, `flyschParts`, `mountainElevation` (`LM:506`), `raw` | 0 |
| `teren.piask` | piaszczystość utworu 0–1 | 1 szum λ 2 km·k, przez tablicę kwantyli | ~40 ns |
| `teren.sBar`, `teren.nach`, `teren.eksp` | wygładzony teren, nachylenie (°), ekspozycja (°) | `CoarseTerrainField` (§3.2) | ~80 ns |
| `wody.rzad`, `wody.zrodlo` | rząd cieku, strefa źródłowa | `RiverHit` | 0 |
| `wody.odlKoryta` | d: m od brzegu najbliższego koryta, ≤ 0 w korycie, +∞ poza zasięgiem | minimum `pr[1] − w/2` po odcinkach z filtrem `reach` (`RN:976-1011`; dziś zapis tylko przy d < połowa+12); po S2 ciągła odmiana `pr[5]` (Odstępstwo S2) | kilka porównań na odcinek |
| `wody.szerKoryta`, `wody.poziomKoryta` | W i lustro tego koryta | `Segment.widthAt` / `levelAt` | 0 |
| `wody.wDnie`, `wody.u`, `wody.polSzerDna` | dno doliny, u = `bestFloorDist / bestFloorHalf` (NaN poza dnem), półszerokość dna | `RN:1003-1004` | 0 |
| `wody.spadek` | spadek w ‰ w skali 1:1 | `bestSlope` (`RN:1073`) wyjęty przed warunek starorzecza | 1 dzielenie |
| `wody.brzegWypukly` | brzeg wypukły zakola | znak krzywizny `MeanderField` × strona w `projectChannel` | ~10 ns |
| `wody.s`, `wody.poziomBrzegu`, `wody.rodzajStojacej`, `wody.torfOmbro`, `wody.idJeziora` | brzeg jeziora, oczka, starorzecza lub jeziora bezodpływowego (ze znakiem: dodatnie na lądzie), lustro, rodzaj, torf ombro, hash do troficzności | `LakeHit` (pas 45–140 m), `kettleAt`, `tunnelLakeAt`; `oxbow()` zwraca też odległość na zewnątrz, do 40·k; po poprawkach S2 jeziora do 150k (Odstępstwo S2) | ~0 |
| `region.O`, `region.P` | oceaniczność, podgórskość | `RegionalField` (§3.2) | ~60 ns |

Rekord urośnie z ok. 48 do ok. 150 B, czyli ok. 40 KB śmieci na chunk. Jeśli profil pokaże presję GC, dodajemy `sampleInto(ColumnBatch, i, x, z)` z tablicami prymitywów na wątek. Rekord zostaje wtedy dla komend i testów.

**Stan po S2.** `ColumnSample` ma rekordy `Teren`, `Wody` i `Region` (opis pól w javadocu `ColumnSample`). Wartość NaN znaczy „nie dotyczy” (np. `u` poza dnem, `grzbiet` bez komórki fliszu, `piask` w morzu), a +∞ w odległościach „poza zasięgiem”. `sBar`, `nach`, `eksp` i pola `Region` doszły w S3 (stan po S3 w §3.2). `describe()` bierze formy, rząd cieku, dno doliny i teren przed wcięciem z próbki, a sam liczy już tylko rynnę, przełęcz, szczyt i regiel. Złoty test przechodzi bez zmian. Koszt `sample` wobec kopii M1 (`SampleKosztTest`, 15 przebiegów, trzy uruchomienia): mediana stosunków 1,02–1,05, stosunek z minimów 0,97–1,08 przy szumie ok. ±4–6% na identycznym kodzie. W pełnym `./gradlew test` (7 przebiegów) mediany wyszły 0,96–1,03. Razem z pomiarami recenzji: REAL, cały obszar (nizina) ok. +5% (mediana median ok. 1,048, czyli granica budżetu S2), pozostałe obszary ok. +1–3%. Po poprawkach recenzji (ciągłe d, pierścienie jezior, brzeg wypukły szerokich koryt) mediany wynoszą 1,039, 1,031, 1,037 i 1,004 (REAL cały obszar, REAL Beskidy, GAMEPLAY cały obszar, GAMEPLAY Beskidy). Przy siatkach S3 (budżet fazy 1: +5–10%) trzeba szukać oszczędności: zmienne lokalne zamiast obiektów `Stojaca` i `Rzezba` albo `sampleInto`. Część oszczędności zrobiliśmy w poprawkach S3 (stan po S3 w §3.2). Liczby są w `docs/m2/pomiary-bazowe-m1.md`.

**Odstępstwo S2:**
- `teren.wPobrzeze` nie pochodzi z `Blend`, bo makroregiony nie mają typu POBRZEZE i waga byłaby zawsze 0. To udział pasa wybrzeża: 1 tam, gdzie próbka dostaje typ POBRZEZE (cD < B + 400k), i spada do 0 przy cD = B + D + 2000k. Pięć wag typów regionów to surowe wagi `Blend` (suma 1). Klasyfikator miesza je z wagą 1 − wPobrzeze.
- `teren.formy` to `int` z bitami `Landform.bit()`, a nie `short`. Nazwy form są z enuma `Landform` (`WYDMY`, `WYDMY_NADMORSKIE`), lista jest w `Landform.Z_PROBKI`.
- `teren.wyp` składa się z falowania i wydm sandru, pagórków i wałów wysoczyzny, składowej 500 m·k równiny oraz, we fliszu, wkładu żlebów względem ich średniej (0,52) i szorstkości. Grzbiety i doliny podłużne fliszu opisuje `teren.grzbiet`.
- `teren.piask` to kwantyl szumu `habitat.piask` (λ = 2 km·k) z tablicy dystrybuanty liczonej raz ze stałego ziarna, więc rozkład jest jednostajny na [0, 1].
- `wody.odlKoryta` = min(pr[5] − W/2) po wszystkich odcinkach w zasięgu, bez wygaszania szerokości przy źródle. W i lustro (`levelAt`, bez zaokrąglenia) są z odcinka, który daje to minimum. `u`, półszerokość dna, rząd i spadek (w ‰) są z cieku o dominującej dolinie. pr[5] to ciągła odmiana odległości od koryta pr[1], którą rzeźbi teren. `MeanderField.distance` (M1) ma dwa skoki. Dla koryta i brzegu nie mają one znaczenia, ale w d są widoczne. Funkcja przełącza się z tablicy na dokładną odległość od łamanej przy 0,15 λ (skok do ok. 6 m przy dużej rzece, λ = 11W), a dokładne szukanie pomija łamaną za granicą okresu u (skoki do ok. 0,8 m). Pole d używa `MeanderField.distances`: płynnego przejścia tablica ↔ dokładna odległość w przedziale 0,12–0,20 λ i szukania z kubełkami modulo okres. Teren liczy dalej starą wartość (złoty test bez zmian).
- **d nie jest odległością euklidesową.** d liczymy w układzie doliny: u wzdłuż osi odcinka (krzywej Hermite'a), v w poprzek, z zakolami doliny. W tym samym układzie rysowane jest koryto, więc strefy z d układają się wzdłuż koryta widocznego w świecie. Przy dużej rzece, której dolina leży kilka kilometrów od osi odcinka (np. lat ≈ −7,3 km na odcinku rzędu 3 dla ziarna testowego), układ jest ściśnięty po wewnętrznej stronie łuku osi. Tam d odbiega od odległości do najbliższej kolumny rzeki o −21% do +27% (pomiar recenzji S2), a jego spadek dochodzi lokalnie do ok. 7,7 m na 1 m. Na transektach 24 × 2 km wokół tej rzeki (REAL) w pasie d ≤ 200 m·k spadek powyżej 1,5 m na 1 m miało 0,58% kroków, powyżej 3 m na 1 m 0,24%, a powyżej 5 m na 1 m 0,06%. Przy rzece nizinnej i rzece rzędu 2 było to 0–0,02% kroków (do 3 m na 1 m), w GAMEPLAY 0–0,06% (do 6,5 m na 1 m). Poza pasem spadek dochodzi do ok. 3 m na 1 m (wewnętrzna strona łuku doliny). Poprawka metryczna (minimum po krzywej w metryce z jakobianu układu) byłaby nieciągła przy zmianie ramienia krzywej, więc zostawiamy d w układzie doliny. Test `channelDistanceIsContinuousAndNonPositiveInChannel` sprawdza cztery miejsca na skalę: rzekę nizinną, rzekę rzędu 3, rzekę rzędu 2 i potok w Beskidach. Wymaga braku skoków (kroki > 1,5 m zagęszcza do 1/256 i 1/65536 m). W pasie spadek musi być ≤ 10 m na 1 m, a powyżej 1,5 m na 1 m najwyżej w 1% kroków. Poza pasem spadek musi być ≤ 4 m na 1 m. Klasyfikator S4 dzieli d progami, więc w tych miejscach pasy stref są lokalnie węższe lub szersze, o tyle samo co koryto. Szum granic stref (±20% szerokości) w dużej części to maskuje. DGW używa d dalej od koryta tylko w członie i·r, gdzie taki błąd nie ma znaczenia.
- **d ≤ 0 nie oznacza wody.** d liczymy z pełnym W, a koryto przy źródle jest węższe (headFade). W suchej głowicy doliny (dno wyżej niż 3 m nad lustrem) koryta nie ma wcale. W obu miejscach d ≤ 0 na pasie szerokości ok. W bez wody. Strefę KORYTO i biom `rzeka` (§4.1, wiersz 0) wybieramy z `waterKind == RIVER`, a nie z d ≤ 0.
- `wody.brzegWypukly` liczymy przy d ≤ max(W; 15 m·k), jak zasięg łach i wikliny w raporcie ekologii. Liczymy go na odcinkach o θ0 ≥ 0,35 (krętość od ok. 1,03) albo o szerokim korycie (Wr = W / chan ≥ 6 m). Odcinki o spadku powyżej ok. 2,8‰ mają krętość 1,02 (θ0 ≈ 0,28). Bez wyjątku dla szerokich koryt KAMIENIEC (§4.3: brzeg wypukły, Wr ≥ 6) byłby osiągalny tylko na rzekach górskich o spadku do ok. 2,8‰. Wąskie potoki (Wr < 6) mają zawsze false. Test liczy przekroje koryta, nie powierzchnię, na pięciu odcinkach na skalę, z oknem na korycie (nie na osi odcinka). W 90–93% przekrojów dokładnie jeden brzeg jest wypukły, a wypukłych brzegów jest 45–47%. Reszta to głównie przekroje przy punktach przegięcia krzywej. Udział powierzchni pasa brzegu wynosi ok. 40%, bo w ciasnym zakolu brzeg wewnętrzny jest krótszy od zewnętrznego. Test sprawdza też stronę w świecie. W kole o promieniu 1,5W + d wokół brzegu wypukłego jest więcej wody niż wokół wklęsłego w 90% rozstrzygniętych przekrojów w REAL (666) i w 94% w GAMEPLAY (880).
- Dodatkowe pole `wody.promienStojacej` (promień oczka lub jeziora bezodpływowego, półszerokość jeziora rynnowego lub starorzecza w danym miejscu). Potrzebują go warunki „oczko torfowe o promieniu < 75k / > 75k” z §2. `wody.rodzajStojacej` to nowy enum `ColumnSample.RodzajStojacej`: odróżnia oczko torfowe i jezioro bezodpływowe, czego `WaterKind` nie robi. `wody.torfOmbro` jest prawdą dla oczka torfowego, którego brzeg leży dalej niż 300 m·k od koryta. Liczy się to raz na oczko z zapytania w jego środku, które M1 już robiło.
- Pas `wody.s`: jeziora bezodpływowe do 150 m·k, jeziora rynnowe do max(150 m·k; 70 m), czyli pas olsu z §4.4. Teren zmieniają nadal tylko do 45 m i do zasięgu niecki (`tunnelBank`, 70–140 m). Jeziora bezodpływowe z szerszego filtra kandydatów (`RiverNetwork.LAKE_RING`) trafiają tylko do pierścienia (`RiverHit.ring*`), a nie do terenu. Oczka mają pas do 45 m (`KETTLE_BANK`), a starorzecza do 40 m·k. Pas oczka wynika z komórki 700 m·k, w której mieści się całe oczko ze strefą brzegu. Pas 150 m·k wchodziłby do sąsiednich komórek. Ols przy oczkach (≤ 40k, §4.4) się mieści. Pierścień 20–150k boru bagiennego wokół torfu ombro (§2, nr 5) jest ucięty do 45 m (w GAMEPLAY 45 m zamiast 75 m), bo torf ombro to w modelu tylko oczka torfowe. Pierścień starorzecza jest znany tylko tam, gdzie model sprawdza starorzecze, czyli w dnie, z dala od koryt i na płaskich nizinach.
- `teren.wyp` i `teren.wydma` w pasie 25 km·meso od morza mnożymy przez ten sam czynnik co rzeźbę w `shapeCoast`: 0,12 + 0,88 smoothstep(0; 25 km·meso; cD). Bez tego wydma 4 m przy cD = 5 km (REAL) miałaby ok. 19 m. Bit `WYDMY` w `teren.formy` liczymy, jak w M1, z wartości bez czynnika (zgodność z `describe` i złotym testem).
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

**Stan po S3.** `teren.sBar`, `teren.nach` i `teren.eksp` pochodzą z `CoarseTerrainField`, a `region.O` i `region.P` z `RegionalField`. Obie siatki trzymają niezmienne kafle w `DirectCache` i działają w każdej kolumnie, także w morzu i zalewie. Siatka terenu to jedyne wywołanie `landElevation` dodane w M2 do ścieżki `sample` (Z6). Kod M1 woła je dalej w oczkach, poziomie jezior i węzłach sieci rzecznej (z własną pamięcią). Złoty test przechodzi bez zmian.
- **Nachylenie w GAMEPLAY (uwaga dla S4).** `nach` to nachylenie w układzie modelu danej skali. W GAMEPLAY odległości poziome są ściśnięte (k = 0,5, pasma 0,3) przy podobnych wysokościach, więc stoki są tam znacznie bardziej strome niż w REAL. Przykład: na sandrze w oknie 4 km stoków ≥ 1° jest w GAMEPLAY 3544 na 4000 punktów, a w REAL 0. W świecie gry wysokości ściska odwzorowanie pionowe (`VerticalScale.GAMEPLAY`: bloki = 1,89 · m^0,742), więc stoki w blokach są podobne do REAL. Nachylenie w blokach to tan(nach) · d(bloki)/d(metry) przy wysokości `sBar`, czyli ok. 0,40 · tan(nach) przy 130 m i ok. 0,24 · tan(nach) przy 1000 m (w REAL czynnik 1 do ok. 900 m). Progi w stopniach z §2, §4 i §5.1 (buczyna > 3°, pole < 5°, ols < 3°, ekspozycja od 5°, jaworzyna > 30°) klasyfikator S4 musi więc porównywać z nachyleniem przeliczonym tym czynnikiem, a nie z `nach` wprost. Ekspozycja nie zależy od skali.
- **Pojedyncze zapytania.** Kafel `CoarseTerrainField` to 121 wywołań `landElevation` (ok. 0,1–0,2 ms). Pierwsze zapytanie w kaflu spoza pamięci liczy więc tylko swoje oczko z 4 × 4 węzłów surowych (16 wywołań) i zaznacza kafel, a dopiero drugie liczy i zapamiętuje cały kafel. Węzeł liczy ta sama funkcja, więc wynik jest identyczny co do bitu (`sparseCellMatchesTile`). Zmierzone: pierwsze zapytanie 14–26 µs, budowa kafla 107–186 µs, odczyt z pamięci 0,15–0,4 µs. Rozrzucone zapytania (komendy, `BiomeSharesTest`, `findClosestBiome3d` z dużym krokiem, §3.5) kosztują więc ok. 16 `landElevation` na punkt zamiast 121. Przy gęstym próbkowaniu (np. krok 32 m) kafle i tak się zapełniają: ok. 2 wywołania na próbkę w REAL i ok. 7,5 w GAMEPLAY przy kroku 32 m, co S5 musi wliczyć w koszt szukania biomu. Kafle `RegionalField` (ok. 0,3 ms) obejmują 128 km·zs, więc w REAL rozrzucone zapytania prawie zawsze trafiają w pamięć.
- `CoarseTerrainFieldTest`: w węźle `sBar` to średnia 3 × 3 `landElevation`, a gradient to różnica centralna. Między węzłami nachylenie zgadza się z różnicami centralnymi `landElevation` (krok = odstęp węzłów) w ±10% w 91–98% punktów o nachyleniu ≥ 1°, w obu skalach i pięciu typach krajobrazu. Mediana stosunku wynosi 0,986–0,998, średnia 0,985–0,993. W REAL sandr i wysoczyzna mają za mało stoków ≥ 1° w oknie 64 m, więc test je pomija. Błąd ekspozycji w 90% punktów wynosi ≤ 4°. Krok w stronę ekspozycji schodzi w dół w ≥ 98% punktów o nachyleniu ≥ 5°. Wynik nie zależy od stanu pamięci ani od wątków (siatka z 4 miejscami, zapytania równoległe).
- `RegionalFieldTest`: O i P leżą w [0, 1], O od 0,15 do 1. Największa zmiana na 1 km·zs (linie wzdłuż X, Z i po przekątnej, 3 ziarna, obie skale) wynosi dla O 0,0095–0,0115, a dla P 0,0143–0,0154. Pas bez buka i świerka zajmuje 13,8%, 14,0% i 14,1% lądu (REAL, 3 ziarna) oraz 13,8% w GAMEPLAY. Udziały lądu: buk 57–61%, naturalny świerk 38–41%, P ≥ 0,5 10–13%, O ≥ 0,75 2–3%. Granica P = 0,5 leży w medianie 220 km od osi pasma (linia zerowa `mountainRaw`; 10 ziaren razem, 4382 punkty, kwartyle 178 i 272 km). Mediany pojedynczych ziaren rozchodzą się od 179 do 279 km (ziarna 20260927, 1 i 2: 210, 201 i 245 km), a ziarna z małymi pasmami mają tylko 150–230 punktów granicy. Dlatego test liczy medianę z 10 ziaren razem. Wcześniejsza liczba 161–200 km dotyczyła odległości od rdzenia pasma (mountainField ≥ 0,8), a nie od osi, i tylko trzech ziaren. Średnie O w pasie 50 km od brzegu wynosi 0,61, a ponad 300 km od niego 0,35. W pasie 50–150 km od brzegu O jest przy morzu na zachodzie średnio o 0,06 wyższe niż przy morzu na wschodzie (0,489 wobec 0,425; test kierunku członu Wz). Wynik `RegionalField` nie zależy od stanu pamięci ani od wątków (pola z 4 miejscami w pamięci, zapytania równoległe).
- Koszt (`SampleKosztTest`, 15 przebiegów, obecny/M1, mediana stosunków): po S3 1,071 i 1,074 (REAL, cały obszar), 1,039 i 1,049 (REAL, Beskidy), 1,048 i 1,050 (GAMEPLAY, cały obszar), 1,024 i 1,022 (GAMEPLAY, Beskidy). W tej samej sesji ten sam kod z wyłączonymi siatkami (stałe zamiast odczytu) dał 1,050, 1,042, 1,054 i 1,019. Siatki dokładają więc ok. 0–2% (budżet S3: +5%), a same odczyty obu siatek po rozgrzaniu kafli kosztują ok. 45 ns na kolumnę (ok. 1% `sample`). Szczegóły są w `docs/m2/pomiary-bazowe-m1.md`.
- **Oszczędności (poprawki S3).** Wynik się nie zmienia (złoty test bez zmian). Profil JFR pokazał, że 59% alokowanych bajtów to tablice z `RiverNetwork.projectChannel`. (1) `projectChannel` i `query` biorą tablice robocze z bufora wątku (`RiverNetwork.Scratch` w `TileCache`) zamiast tworzyć 6 tablic na odcinek i 5 na zapytanie. (2) `Stojaca` zastąpiły zmienne lokalne w `sample`. (3) `blend` liczy odległości w tablicach wyniku zamiast w dwóch osobnych, a `LandscapeType.values()` jest wołane raz. (4) Jeziora z samego pierścienia siedlisk dalsze niż pierścień + 1,3 R nie liczą szumu brzegu. Brzeg leży najdalej 1,2 R od środka, więc taki brzeg i tak nie trafiłby do wyniku. `Rzezba` zostaje obiektem: to 2% alokacji, a zastąpienie wymagałoby przekazywania kilku wyników przez wszystkie funkcje rzeźby. Po poprawkach (trzy uruchomienia, 15 przebiegów, obecny/M1, mediana stosunków): REAL cały obszar 1,012, 1,020 i 1,019; REAL Beskidy 0,984, 1,001 i 0,998; GAMEPLAY cały obszar 1,003, 1,023 i 1,029; GAMEPLAY Beskidy 0,967, 0,977 i 0,981. Stosunek z minimów to najwyżej 1,046. Kryterium kroku (≤ +5% wobec M1) jest więc spełnione z zapasem ok. 2–5% na dalsze kroki.

**Odstępstwo S3:**
- **P: progi i uśrednianie.** P to średnia z okna 5 × 5 węzłów (ok. 80 km·zs) wartości smoothstep(0,465; 0,915; liniowe pole pasm). Liniowe pole to `mountainLinear`: (1 − |mountainRaw|) · maska pasma · odsunięcie od morza, jak w `mountainField`, ale bez sześcianu. Przy progach z planu (0,35; 0,80) granica P = 0,5 leżała w medianie 245–310 km od rdzenia pasma, a nie 150–250 km. Bez uśredniania P zmieniało się do 0,049 na km·zs (5 ziaren). Strome zbocza P dają maski pasma i odsunięcia od morza z `mountainField`, a nie sam grzbiet. Uśrednianie ogranicza zmianę do 1/80 na km·zs wzdłuż osi siatki (po przekątnej zmierzono do 0,0154).
- **N: smoothstep kwantyla.** N = smoothstep(0; 1; kwantyl szumu o fali 900 km·zs). Kwantyl ma tę samą tablicę dystrybuanty co `teren.piask`. Wzór O z planu zostaje bez zmian. Przy samym kwantylu (rozkład jednostajny) pas bez buka i świerka zajmował 17,5–20,5% lądu na 5 ziarnach, przy surowym szumie (0,5 + 0,5 · szum) 30–35%. Smoothstep zagęszcza prowincje przy biegunach oceanicznym i kontynentalnym i daje 13,5–15%.
- **Wz z miękką granicą morza:** punkt liczy się jako morze z wagą 1 − smoothstep(−0,02; 0,02; seaField), żeby O nie skakało, gdy punkt zachodni przechodzi przez linię brzegu.
- **DirectCache:** kafel ma dwa możliwe miejsca, wyznaczone dwiema połówkami skrótu. Nowy kafel trafia w wolne z nich, a gdy oba są zajęte, w to, które wskazuje bit skrótu. Przy jednym miejscu na kafel ok. 9% z 400 rozrzuconych kafli (tak próbkuje `SampleKosztTest`) przepychałoby się w każdym przebiegu. Kafle trzymają liczby `float`. 4096 kafli `CoarseTerrainField` to ok. 4 MB, a nie 2–3 MB, bo kafel ma 9 × 9 węzłów z trzema wielkościami. Kafle liczone są z 11 × 11 węzłów surowych (margines 1). `RegionalField` ma 1024 miejsca.
- **Ekspozycja:** azymut kierunku spadku (−gradient) od północy (−Z) zgodnie z ruchem wskazówek zegara: 90° to wschód (+X), 180° południe (+Z), 270° zachód (−X). Na terenie płaskim (gradient 0, np. dno głębokiego morza) wynosi NaN. Atan liczymy wzorem Abramowitza i Stegun 4.4.49 (błąd ≤ 10⁻⁵°, deterministycznie).
- **Pola w morzu:** siatka terenu i pola regionalne są liczone także w kolumnach morza i zalewu. Stała `ColumnSample.Region.BRAK` zniknęła.
- **Budżet kosztu:** przed poprawkami recenzji kryterium „sample z siatkami ≤ +5% względem S0” liczone łącznie wobec M1 nie było spełnione dla REAL, cały obszar (1,07): S2 zużyło ok. +4–5%, S3 ok. 0–2%. Po oszczędnościach bez zmiany wyniku (powyżej) mediany wynoszą 0,97–1,03, więc kryterium jest spełnione. `sampleInto` i zastąpienie `Rzezba` zostają w zapasie na S10.
- **Pojedyncze zapytania siatki terenu** liczą samo oczko (16 wywołań `landElevation`), a cały kafel dopiero przy drugim zapytaniu (powyżej). Plan zakładał tylko gęstą generację.
- **Zasięg P mierzony od osi pasma** (linia zerowa `mountainRaw`, która jest teraz widoczna w pakiecie dla testów) na 10 ziarnach razem. Progi P (0,465 i 0,915) bez zmian.

### 3.3 Pochodne w klasyfikatorze (bez nowych próbek)

**h** = H − lustro najbliższej wody w zasięgu − 1 m. Korekta wynika z kwantyzacji brzegu: bank w modelu leży na lustrze + 1 m (`RN:1044`).

**DGW** (wzór z raportu ekologii; poprawia odwróconą formułę z projektu „silnik”):

GW = min(sBar − g_typ ; L_w + i·r), DGW = clamp(H − GW; 0; 12).

- `g_typ` mieszane wagami typów: SANDR 2,5; POBRZEŻE 1,5; RÓWNINA 3; WYSOCZYZNA 4; POGÓRZE 5; BESKIDY 8.
- `L_w` i `r`: lustro (+0,3 m) i odległość najbliższej wody w zasięgu 2 km·k: koryto (poziomKoryta, d), woda stojąca (poziomBrzegu, s), morze (0, cD). Bez wody w zasięgu GW = sBar − g_typ.
- `i`: piasek 0,003; glina 0,01; flisz 0,04 (z `piask` i podłoża).
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

### 3.4 Algorytm `Klasyfikator.klasyfikuj`

```
1. woda                       -> Woda: morze, zalew, rzeka/potok, jezioro/dystroficzne; z ≤ 1,5 i woda eutroficzna -> szuwar;
                                 strefy KORYTO, ELODEIDY, NYMFEIDY
2. cD < B + D + 2000k         -> Wybrzeze (§5.2); null = dalej
3. (rzad > 0 i (wDnie lub d < zasięg)) lub s ≤ pierścień(rodzaj) lub ZRODLO
                              -> StrefyNadwodne (§4); null = dalej
4. wP + wB > 0,5              -> Pietra (§5.1); null = strefowe
5. strefowe: stl = Stl.of(trofia, wilgotność, formy) -> biom (§2) z regułami zasięgu (§9)
6. tryb D: jeśli !Lesistosc.las(stl, nach, F(x,z)) -> biom nieleśny (strefy przywodne zostają)
7. pack: biom 6b | strefa 5b | stl 5b | zespol 5b | pokrycie 2b | flagi BUK/JODLA/SWIERK/GRAB 4b | gleba 5b  (32 bity)
```

Kolejność oznacza priorytet. Strefa jest ustawiana niezależnie od biomu, np. OKRAJEK wewnątrz `leg_wierzbowo_topolowy`. Wszystkie progi trzymamy w `habitat/Kalibracja.java`.

**Stan po S4.** Pakiet `worldgen/habitat` (czysta Java) ma enumy `Biom` (36, kolejność i identyfikatory z §2, test `identyfikatoryBiomowSaZamrozone`), `Strefa` (23), `Stl`, `Zespol`, `Pokrycie`, `Gleba`, `Gatunek`, kod `Siedlisko` (32 bity jak w kroku 7) oraz klasy `Klasyfikator`, `Woda`, `Wybrzeze`, `StrefyNadwodne`, `Pietra`, `Wilgotnosc`, `Trofia`, `Zasiegi`, `Lesistosc` i `Kalibracja`. Klasyfikacja trwa 0,11–0,19 µs na kolumnę (`KlasyfikatorTest`, 100 tys. kolumn z pięciu obszarów, najlepszy z 8 przebiegów). Próg 1150 m jest tylko w `Pietra` (`REGIEL_GORNY`). Opis terenu (`describe`), źródło biomów M1 i komendy `regiel_dolny`/`regiel_gorny` biorą z niego granicę nominalną (`Pietra.reglGorny`, bez ekspozycji i szumu), a biomy granicę z korektą. Tryb D (maska `Lesistosc`) działa, ale P_las to wartości startowe z raportu ekologii; kalibracja i test udziałów trybu D należą do S8. Domyślny jest tryb N.

Udziały w trybie N (`BiomeSharesTest`, 200 tys. kolumn na skalę, 3 ziarna, plik `docs/m2/m2_udzialy_biomow.csv`): lesistość lądu 99,4% (REAL) i 98,6% (GAMEPLAY), bory w lesie wnętrza sandru 88,6% i 87,9%. W 64 skupiskach po 125 × 125 kolumn (2·10⁶ próbek w obu skalach) łęgi leżą tylko w dnie, źródliskach i wysiękach. W świecie występuje 27 biomów. Pozostałe dziewięć jest osiągalnych w próbkach syntetycznych: piętra wysokie, tryb D i biomy wąskich pasów (zalew, wydma szara, torfowisko niskie w zastoiskach wielkich den). Przekroje rzek (`StrefyNadwodneTest`, po 35 przekrojów na klasę i skalę) mają kolejność stref z §12.1 w 33–35 z 35 przekrojów. Kolumny z cofnięciem rangi o najwyżej 1 m (migotanie progu na drobnej rzeźbie skraju dna) nie liczą się jako błąd. Pasy wikliny i OlJ mają minima E11. Piętra (`PietraTest`): w reglu dolnym (600–1100 m, poza dnami) buczyna, jedlina i olszyna zajmują 97% (REAL) i 96% (GAMEPLAY), w 1200–1350 m świerczyna 99,6%, a granica regla górnego leży na stokach S średnio 91 m wyżej niż na N. Najwyższy szczyt w kwadracie 3000 × 3000 km wokół środka (ziarno 20260927) ma 1445 m, więc kosodrzewina i hala pojawiają się tylko w próbkach syntetycznych (R14).

Podgląd (`./gradlew landscapePreview`, z `-PtylkoSiedliska` sam podgląd siedlisk, ok. 4 min) zapisuje kadry z §12.2 w warstwach biomów (z legendą i udziałami), stref, DGW i trofii, mapy O, P i zasięgów 2000 km, maskę lasu trybu D (50 km), przekroje dolin A, B, C oraz pliki CSV udziałów biomów i stref. Wynik jest w `docs/m2/`, opis kadrów w `docs/m2/podglad-siedlisk-s4.md`.

**Odstępstwo S4:**
- **Wywołanie:** `new Klasyfikator(ziarno, skala, tryb).klasyfikuj(próbka, x, z)` zamiast `klasyfikuj(ColumnSample, UstawieniaSiedlisk)`. Położenie (x, z) służy tylko szumom `habitat.*`: drgania granic stref, płaty, warianty i przejścia pięter. Wynik zależy więc wyłącznie od ziarna, skali, trybu, próbki i położenia. Klasyfikator jest niezmienny, a pochodne kolumny (`Klasyfikator.Kolumna`) liczy leniwie w obiekcie lokalnym jednego wywołania.
- **Duży masyw:** nowe pole `teren.szczyt` (wysokość grzbietów okolicy: dno pasma + rzeźba · kopuły, z nasyceniem jak teren; koszt pomijalny, teren bez zmian) zamiast progu `masyw ≥ m0`. Sam `masyw` nie wystarcza, bo przy osi silnego pasma szczyty przekraczają 1470 m bez masywu. Pole zawyża rzeczywisty szczyt w promieniu 3 km o 36–175 m (67 kolumn powyżej 1200 m), więc próg na nim wynosi 1470 + 180 m (`Pietra.PROG_SZCZYTU`).
- **Dno doliny z terenu:** flaga `wDnie` pochodzi z doliny dominującej. Jest ucięta prostą linią przy zmianie doliny i obejmuje też grunt wysoko nad bliższym korytem. Dlatego dno to grunt najwyżej 2,3 m nad lustrem najbliższego koryta (dno modelu leży 1,2–2,2 m nad lustrem), do tego `wDnie` albo odległość w półszerokości dna (co najmniej 80k). Gdy grunt leży mniej niż 1,2 m nad tym lustrem (dopływ schodzący bystrzem), rozstrzyga sama flaga. Poza flagą `u` = d / półszerokość dna.
- **Klasa A:** rząd pochodzi z doliny dominującej, a W z najbliższego koryta. Mały dopływ w dnie dużej doliny ma więc rząd 3, dlatego rząd 3 daje klasę A dopiero przy Wr ≥ 15 m (Wr ≥ 30 zawsze). Mały ciek w szerokim dnie (półszerokość > 250k) za swoim OlJ ma resztę dna dużej doliny (łęg wiązowy, zastoiska).
- **Łęg topolowy** według samego d ≤ D_top, bez warunku u < 0,35 i bez preferencji garbów. u skakało prostą linią przy zmianie doliny, a preferencja przeplatała łęg topolowy z wiązowym w pasie D_top–1,15 D_top. Wały brzegowe przyjdą z rzeźbą dna w M5.
- **Zastoiska i ols w dnach małych rzek** według h = H − lustro − 1 (h < 0,6 w zastoiskach, < 0,45 w olsie), a nie według `wyp`. `wyp` ma składowe krótkofalowe (falowanie sandru) i dawało w dnie paski co kilka metrów, wbrew Z9. Płaty mają szum o fali 120 m bez mnożnika k (zastoiska 50% warunku, ols 45%). Ols i zastoiska powstają tylko w dnie z miejscem na pas 2 × 10 bloków (E11).
- **DGW:** człon cieku i wody stojącej działa w dnie zawsze, a poza nim z wcięciem terenu w dolinę lub nieckę (rawSurface − H od 0,5 do 3 m; poza tym kara 50 m). Bez tego DGW skakało prostą linią na granicy zasięgu zapytania sieci rzecznej (d = +∞ dalej). Lustro koryta to min(lustro, H − 1,2), bo najbliższe koryto bywa wyżej niż dno. Człon morza nie ma ucięcia zasięgu 2 km. Starorzecza nie wchodzą do DGW, bo ich pierścienie dawały prostokątne plamy. Pas DGW 0,5–0,8 rozstrzyga szum o fali 80 m bez k.
- **Wysięk** (łęg jesionowo-olszowy poza dnem): siedlisko bagienne na LM lub L w zasięgu cieku poniżej 600 m, bez warunku zbocza (H < rawSurface − 2). Z tym warunkiem skraj dna dostawał wąski pas olsu strefowego.
- **Źródliska:** forma ZRODLO obejmuje całe dno odcinka źródłowego, a promienia 10–40k nie da się wyznaczyć z próbki. Źródlisko to więc płaty (30%) do 40k od koryta.
- **Trofia:** SANDR 65/31/4/0 zamiast 60/30/10/0. Przy 60/30/10 bory zajmowały 81% lasu sandru (las mieszany z LM 7%), a bliżej moreny udziały BM i LM i tak rosną przez mieszanie typów. Pas nadmorski daje B tylko przez udziały POBRZEŻA ważone `wPobrzeze` (bez progu wPobrzeze > 0,5, który rysował linię wzdłuż brzegu). Wydmy nadmorskie dają B tylko na piasku. Pas nadmorski na glinie (wysoki brzeg) ma udziały wysoczyzny.
- **Wybrzeże:** zaplecze klifu (korona, las wiatrowy) tylko na glinie. Wysokie wydmy i sandry przy morzu idą do boru bażynowego. Granica boru bażynowego 2000k drga o ±15% (szum wariantów), żeby nie była linią równoległą do brzegu.
- **Szuwar jako biom w wodzie:** w zbiornikach o promieniu ≥ 50k, w starorzeczach pas 15k przy brzegu (dalej nymfeidy). Szacowanie szerokości płycizny z głębokości dawało odwrócone pierścienie (strefa przy brzegu, biom dalej).
- **Pierścienie starorzeczy** tylko przy starorzeczach o półszerokości ≥ 8 m i dalej od koryta niż ta półszerokość. Model podaje pierścień także przy wąskich ciekach, gdzie wody starorzecza nie ma, co dawało rząd jednakowych kresek łozowiska wzdłuż potoku.
- **Minima E11** stosujemy po drganiu szerokości (max(minimum, szerokość · (1 ± 0,2))).
- **Testy:** `PietraTest` liczy regiel dolny w 600–1100 m poza dnami, a nie „poniżej 1100 m”: niżej są dna i stoki jedliny wyżynnej. `StrefyNadwodneTest` liczy przekroje wzdłuż normalnej do koryta (po gradiencie d przekrój zakosami wracał przez te same pasy) i wymaga uporządkowania w ≥ 90% przekrojów. Dla olsu wymaga ≥ 10 bloków w co najmniej połowie cięciw, bo przekrój tnie płaty pod różnymi kątami. Pierścieni wód stojących tu nie mierzy. `BiomeSharesTest` liczy bory sandru we wnętrzu typu (waga ≥ 0,9), bo w GAMEPLAY pasy mieszania z wysoczyzną zajmują dużą część sandru.
- **Lesistość w trybie N** wynosi 98,6–99,4%, a nie 90–95% z raportu ekologii. Otwarte są tylko wody, torfowiska, szuwary, wikliny, plaże, wydmy i prześwity wrzosowisk. Luki w lesie naturalnym (E6) to M4.

### 3.5 `PolskaBiomeSource` (przepisany)

- **Kodek:** `RecordCodecBuilder.mapCodec(i -> i.group(RegistryOps.retrieveGetter(Registries.BIOME)).apply(...))`. Holdery bierzemy z `getter.getOrThrow(Biom.key())` w kolejności enuma (wzór: `TheEndBiomeSource`).
  - Presety skracają się do `"biome_source": {"type": "polskielasy:polska"}`.
  - Stare pola M1 w `level.dat` są ignorowane, bo `MapCodec` pomija nieznane klucze.
- **`collectPossibleBiomes()`:** wszystkie 36, stałe od dekodowania, niezależne od trybu i ziarna.
- **Stan:** `volatile Binding` (model, klasyfikator z `PolskaSettings`, `VerticalScale`). `bind` dostaje teraz ustawienia. Wiązanie zostaje w `PCG.createState`, bo to jest przed `ServerLevel.uncachedBiomeResolver`.
- **`createResolverForChunk`:** 16 holderów z góry (16 × próbka + klasyfikacja). Resolver zwraca `cols[ix*4+iz]` i ignoruje qy.
- **`createResolver`:** czyta `binding` wewnątrz lambdy, co usuwa błąd „plains na zawsze”. Do tego bufor wątku: 256 wpisów mapowanych bezpośrednio po (qx, qz), bo struktury pytają tę samą kolumnę na kilku Y.
- **Nadpisany `findClosestBiome3d`:** jedna próbka na kolumnę zamiast ok. 33 poziomów Y. Dla promienia 6,4 km to ok. 1 s zamiast ok. 30 s zawieszenia serwera.
- **`addDebugInfo` i F3:** biom, STL, strefa, zespół, gleba, DGW, O, P, T(pos).
- **`PolskaPresetEditor`:** tworzy nową instancję źródła dla nowego generatora. `bind` loguje ostrzeżenie przy ponownym wiązaniu z innymi parametrami.

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

**Odstępstwo S0:** czasów etapów chunka (`profileStages`) w S0 nie zmierzyliśmy, bo w krokach nie uruchamiamy gry. Baza musi pochodzić z kodu modelu M1, więc nie zastąpi jej test w grze na końcu fazy 1 (wtedy model zawiera już S2 i S3). Trzeba uruchomić `./gradlew runClientGameTest -Pgametest=etapy` przed S2, dopóki `src/main` jest równe M1. Po S2 można to zrobić z migawki `migawki/m1-z-narzedziami-S0.tar` (SHA-256 i instrukcja w `migawki/README.md`), w tej samej sesji co pomiar bieżącego kodu. Tak samo trzeba zmierzyć bazę chunków na sekundę z C2ME dla S10, której nie zmierzono wcale. Tryb `etapy` loguje przyrost liczników modelu i wypełniania w czasie pomiaru. Wcześniej logował sumy narastające z obu światów, co było błędem. Mierzy trzy obszary na skalę: nizinę z M1, wnętrze Beskidów i dużą rzekę. Budżet `sample` rozstrzyga `SampleKosztTest`: mierzy na przemian obecny model i zamrożoną kopię M1 (`src/test/.../landscape/m1/`, nie zmieniać) w jednej JVM i podaje stosunek obecny/M1. Na identycznym kodzie stosunek z minimów mieści się w ok. ±4%, a mediana stosunków w ok. ±10%. Złoty test obejmuje też zestaw z suwakiem regionów 0,5, bloki obu odwzorowań pionowych i cele łat (plaża z `BEACH_SAND`, klif, ujście, torf, szczyt). Przy różnicy porównuje wartości z kopią M1. W skali rozgrywki Beskidy prawie nie przekraczają 1150 m (ważne dla `Pietra`, S4). Szczegóły: `docs/m2/pomiary-bazowe-m1.md`.

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
| 0 | KORYTO / `rzeka` | `waterKind == RIVER` (Odstępstwo S2: d ≤ 0 bywa też bez wody, przy źródle i w suchej głowicy doliny) | piasek; pas 1–2 bloków przy brzegu: `mud` | rdestnice (`seagrass`), pałka w wodzie 1-blokowej |
| 1 | ŁACHA / `wikliny` | brzeg wypukły, d < 0,5W, szum | piasek z łatami błota, półka | siewki wierzb, rzadko |
| 2 | WIKLINA / `wikliny` (biom, gdy pas ≥ 12 m) | d ≤ max(3 bl; 8k; 0,3W), na brzegu wypukłym do max(15k; 1,0W) | MADA_LEKKA, półka 1–2 bloki | `krzew/wiklina` 80–100%, trzcina na półce, `firefly_bush` |
| 3 | OKRAJEK (ziołorośla) | pas max(2 bl; 4k; 0,05W) **za wikliną** oraz luki w łęgu (szum < −0,5) | `rooted_dirt`, błoto | `tall_grass`, `large_fern`, `bush` 80% |
| 4 | `leg_wierzbowo_topolowy`, zespół Salicetum albae | d ≤ D_wb | MADA_LEKKA | wierzba 70%, topola 30% |
| 5 | `leg_wierzbowo_topolowy`, zespół Populetum albae | D_wb < d ≤ D_top i u < 0,35; preferencja wyp > 0 | MADA_LEKKA | topola 60%, wierzba 40% |
| 6 | `leg_wiazowo_jesionowy` | reszta dna | MADA_CIEZKA | dąb, jesion, wiąz, geofity |
| 7 | zastoiska: `ols` 60% / `torfowisko_niskie` 40% (gdy półszerokość dna > 300k i spadek < 0,5‰) | u > 0,6 i wyp < −0,3 (20–30% powierzchni) | TORF_NISKI, mikrorelief | olsza na kępach |
| 8 | pierścień starorzecza | s ≤ max(2 bl; 15k): SZUWAR_LADOWY; ≤ 35k: ŁOZOWISKO; dalej `ols` przy u > 0,5, inaczej łęg według d | jak strefy | trzcina, wiklina, olsza |
| 9 | zbocze doliny (poza dnem, valleyWeight > 0) | wysięki u podnóża (DGW ≤ 0,5): `leg_jesionowo_olszowy`; dalej strefowe według eksp i O (grąd zboczowy, świetlista dąbrowa na S, buczyna przy O ≥ 0,6, bory na piasku) | strefowa | strefowa |

### 4.2 Klasa B (przykład W = 6 m)

| # | Strefa / biom | Warunek |
|---|---|---|
| 1 | ZIOLOROSLA (brzeg) | 0 < d ≤ max(2 bl; 1,5k; 0,5W); półka |
| 2 | WIERZBY | Wr ≥ 15, podłoże piaszczyste, d ≤ 20k |
| 3 | `leg_jesionowo_olszowy` | wDnie, d ≤ min(80k; max(15k; 4W)); na SANDR 5–25k |
| 4 | `ols` (+ ŁOZOWISKO 5–30k na skraju) | półszerokość dna > 60k, d > max(20k; 4W), (wyp < −0,5 lub PEAT lub u > 0,5 przy szumie) |
| 5 | `leg_jesionowo_olszowy`, zespół źródliskowy | ZRODLO, promień 10–40k |
| 6 | skraj dna i zbocze | glina: grąd niski, LMw; piasek: `bor_wilgotny` 20–100k, potem `bor_swiezy` |

### 4.3 Klasa C

| # | Strefa / biom | Warunek | Uwagi |
|---|---|---|---|
| 1 | KAMIENIEC / `wikliny` | brzeg wypukły, Wr ≥ 6, d ≤ W, H 300–1000 (Odstępstwo S2: `brzegWypukly` liczony też na słabo krętych odcinkach, gdy Wr ≥ 6) | żwir. Tag `supports_big_dripleaf` nie obejmuje żwiru, więc lepiężnik rośnie na łatach `coarse_dirt`/`rooted_dirt` na skraju kamieńca |
| 2 | WIKLINA (górska) | Wr ≥ 4, d ≤ max(3 bl; W), H ≤ 900 | wiklina |
| 3 | `olszyna_gorska` | wDnie, d ≤ min(60k; max(10k; 3W)), H ≤ 1000 (stoki N 900); przy półszerokości dna < 30k całe dno | żwir, `coarse_dirt`, ziołorośla, lepiężnik |
| 4 | ZIOLOROSLA_GORSKIE | H > 1000 lub półszerokość dna < 10k; d ≤ max(1 bl; 0,5W) | las strefowy do brzegu |
| 5 | `leg_jesionowo_olszowy`, zespół Carici remotae-Fraxinetum | rząd 1, H < 700, P ≥ 0,4, wąskie dno z wysiękiem | |
| 6 | `olszyna_gorska`, zespół młaka | ZRODLO lub stok z DGW ≤ 0,3, H 400–1100, promień 10–50k | błoto, mursz |
| 7 | `buczyna_gorska`, zespół jaworzyna | dolne 20–80k zbocza, nach > 30°, eksp N–E | rumosz |

### 4.4 Jeziora, oczka, starorzecza, zalew

| Pas | Warunek | Biom / strefa |
|---|---|---|
| głębia | z > 5 (w starorzeczu > 3) | `jezioro` |
| elodeidy | z 1,5–5 | `jezioro`, strefa ELODEIDY (`seagrass`) |
| nymfeidy | z 0,8–3; w starorzeczach 60–90% lustra, w dużych jeziorach zatoki (szum 30%) | strefa NYMFEIDY (`lily_pad`) |
| szuwar wodny | z ≤ 1,5, woda eutroficzna | `szuwar` (pas ≥ 12 m) lub strefa SZUWAR |
| szuwar lądowy | s ≤ max(2 bl; 10k), h ≤ 0,3 | strefa SZUWAR_LADOWY, półka |
| łozowisko | s ≤ 30k, h ≤ 0,8, torf lub muł | strefa ŁOZOWISKO |
| ols | s ≤ 150k (oczka ≤ 40k), h ≤ 1,0, nach < 3°, podłoże TILL, PEAT lub LAKE_MUD (Odstępstwo S2: `wody.s` jezior sięga 150k, oczek 45 m, starorzeczy 40k; dalej od starorzecza ols wyznaczają d i u, §4.1 nr 8) | `ols` |
| zaplecze | wysięki u zbocza rynny: OlJ; dalej strefowe | – |

Warianty szczególne:
- **Jezioro lobeliowe** (SANDR, bez torfu): szuwar w płatach 2–5k (20% brzegu), strefa OLSZA_BRZEG 1–5k, dalej bory według DGW.
- **Jezioro dystroficzne:** strefa PLO 1–20k (torfowiec na poziomie lustra nad wodą), potem `torfowisko_wysokie`, `bor_bagienny` i `bor_wilgotny`.
- **Zalew:**

| Warunek | Biom / strefa |
|---|---|
| z ≤ 1,5 | `szuwar` |
| brzeg od lądu, h ≤ 0,4 | `torfowisko_niskie` (D: `laka_wilgotna`) |
| h 0,2–1 na torfie | `ols` |
| mierzeja | `ols` w obniżeniach, potem `bor_bazynowy` |

### 4.5 Tryb D nad wodą

- Strefy WIKLINA, OKRAJEK, ZIOLOROSLA, KAMIENIEC, SZUWAR i ŁOZOWISKO zostają zawsze.
- Dochodzi strefa SZPALER: 1–3 olsze, 3–10k przy każdym cieku klasy B.
- Duża rzeka:
  - łęg wierzbowy ma P_las 0,45, a poza lasem jest `laka_wilgotna`;
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

### 5.1 Beskidy i Pogórze (klasa `habitat/Pietra.java`)

`Pietra` zastępuje trzy kopie progu 1150 m: `PolskaBiomeSource:26`, `LandscapeModel:750`, `PolskaCommands:70-72`.

```
dT   = 40·szum(λ 150k)                                   // m
eks  = nach ≥ 5° ? 50·cos(eksp − 180°) : 0               // stok S +50 m, stok N −50 m
regielDolny = 550  + eks + dT
regielGorny = 1150 + eks + dT
granicaLasu = 1390 + eks − 60·[GRZBIET i nach < 15°] + dT
progHali    = 1650 + eks + dT
duzyMasyw   = masyw ≥ m0      // m0 kalibrujemy tak, by kosodrzewina była tylko tam, gdzie szczyt w promieniu 3 km > 1470 m (E12)

H ≥ progHali    i duzyMasyw  -> hala
H ≥ granicaLasu i duzyMasyw  -> kosodrzewina (ostatnie 60 m pod granicą: strefa GRANICA_LASU, karłowe świerki)
H ≥ regielGorny              -> swierczyna_gorska (bez dużego masywu las sięga do wierzchołka)
H ≥ regielDolny              -> buczyna_gorska, chyba że Abieti-Piceetum -> swierczyna_gorska:
                                (GRZBIET i trofia ≤ BM) lub (eks < 0 i H > 900) lub (wDnie i H > 700 i polSzerDna > 30k)
POGÓRZE, stok N, H > 450     -> buczyna_gorska
niżej                        -> trofia L i H < 400 -> grad; P ≥ 0,5 -> jedlina_wyzynna; inaczej strefowe
```

- Zespół jaworzyna (nach > 30°, N–E) i ziołorośla w żlebach (grzbiet < 0,15 w reglu górnym) to zespoły w kodzie kolumny, nie biomy.
- Model Beskidów sięga ok. 1725 m, więc kosodrzewina i hala pojawią się tylko na najwyższych masywach (typ Babiej Góry i Pilska).
- Profile Tatr, Karkonoszy i Bieszczad dochodzą w M5 jako kolejne `BeltProfile`.

### 5.2 Wybrzeże (długości × k)

| Pas | Warunek | Biom · strefa/zespół |
|---|---|---|
| plaża mokra i sucha | cD < 0,35B / cD < B | `plaza` · KIDZINA na 0,35B–B |
| podnóże klifu | cD < B, KLIF w sąsiedztwie | `plaza`, zespół kamienisty (żwir) |
| wydma inicjalna i biała | B ≤ cD, BEACH_SAND | `wydma_biala` · WYDMA_INICJALNA do B+20k |
| wydma szara | SAND, cD < B+D+170k | `wydma_szara` |
| bór bażynowy wiatrowy | cD < B+D+420k | `bor_bazynowy`, zespół karłowy; mnożnik wysokości drzew dla M3: 0,4 + 0,6·smoothstep(0; 1500k; cD) |
| bór bażynowy typowy | cD < 2000k, SAND, H < 40; obniżenia przy DGW ≤ 0,5 | `bor_bazynowy` / `bor_bagienny` / `ols` |
| ściana klifu | KLIF (surface > 8, raw > 8, cD < B + raw/2,5 + 20k) | biom wysoczyzny · KLIF_SCIANA (goła glina) |
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
| `ols`, `bor_bagienny`, `torfowisko_niskie`, `szuwar` | 0,67 |
| `torfowisko_wysokie` | 0,65 |
| `morze`, `zalew`, `plaza`, wydmy, `bor_bazynowy` | 0,72 |

Skutek przy T_bazowa = 0,70 jest taki sam w obu skalach, bo liczymy w metrach:

| Okres | Śnieg od |
|---|---|
| bez SS oraz SS IV–X | ok. 2000 m (Babia Góra bez śniegu latem) |
| SS III i XI (−0,25) | ok. 1090 m |
| SS XII–II (−0,8) | wszędzie |

Kontrola: szczyt Babiej Góry (1725 m) ma T ≈ 0,226, czyli ok. 0,5 °C. Wartość 0,27 z raportu klimatu dotyczy Śnieżki z inną temperaturą bazową, więc do testów jej nie bierzemy.

**Kontynentalizm.** Pole O nie wchodzi do temperatury w M2. Mixin ma miejsce na człon regionalny (domyślnie 0). W M4 człon wejdzie razem z S2. Klientowi wyślemy wtedy siatkę t0, a nie ziarno, bo `Noise.derive` jest odwracalne i zdradziłoby ziarno świata.

### 6.2 Mixiny (wspólne dla obu stron)

Nowy plik `src/main/resources/polskielasy.mixins.json`, wpis w `fabric.mod.json` bez ograniczenia do klienta. Klient też liczy opad.

- **`BiomeKlimatMixin`:** interfejs duck `BiomeKlimat` z polem `volatile KlimatBiomu polskielasy$klimat` (VerticalScale, T_bazowa, tryb zamarzania).
- **`BiomeTemperatureMixin`:** `@Inject(method = "getHeightAdjustedTemperature(Lnet/minecraft/core/BlockPos;I)F", at = @At("HEAD"), cancellable = true, require = 1)`.
  - Przy `klimat != null` zwraca `PolskaKlimat.temperatura(...)`. Bez profilu działa wanilia, czyli w Netherze, Endzie i światach wanilijnych.
  - Bufor `getTemperature` zostaje. SS woła `getTemperature`, więc przejmuje wynik bez mixinu w SS.
  - Nie ruszamy `warmEnoughToRain`, `coldEnoughToSnow` ani `getBaseTemperature` (bramka SS).
- **`BiomeFreezeMixin`:** HEAD w `shouldFreeze(LevelReader, BlockPos, boolean)`, tylko dla biomów z profilem:
  - `morze` nigdy nie zamarza;
  - `rzeka` i `potok` zamarzają dopiero przy T < 0,05;
  - reszta jak w wanilii.
  
  To jest zgodne z `@Redirect` SS wewnątrz metody, bo przerywamy tylko w naszych przypadkach.
- **Wiązanie profilu:**
  - `CommonLifecycleEvents.TAGS_LOADED` (obie strony): profil dla biomów z tagu `#polskielasy:klimat_polski`. W kroku S1 tag obejmuje 12 biomów zastępczych z T_bazowa = 0,70 (ocean 0,72, nigdy nie zamarza). Od S5 obejmuje 36 biomów moda.
  - Serwer: skala z `PCG.createState` (przed generacją) oraz `ServerLevelEvents.LOAD` dla overworldu z `PolskaChunkGenerator`.
  - Klient (tylko serwer zdalny): `ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE`. Typ wymiaru `polskielasy:polska` daje REAL, `polska_rozgrywka` daje GAMEPLAY, awaryjnie wysokość 2096 lub 832. Do tego poprawiona `PolskaDimension.isPolska`.
  - Obiekty `Biome` powstają od nowa przy każdym świecie, więc stan nie przecieka.
- **Koszt:** przy chybieniu bufora odczyt pola, `metersAboveSea` i 1 szum, czyli < 0,1 µs.

**Stan S1 (2026-10-02):** mixiny, `PolskaKlimat`, `KlimatBiomu`, `BiomeKlimat`, `WiazanieKlimatu` i `KlimatKlienta` są gotowe. `PolskaKlimatTest` daje latem śnieg od 1964–2036 m (REAL) i 1965–2036 m (GAMEPLAY), przy −0,25 próg 1055–1129 m, a koszt `PolskaKlimat.temperatura` to ok. 18 ns (REAL) i 36 ns (GAMEPLAY). Weryfikacja w grze czeka na test na końcu fazy. Tryb `-Pgametest=klimat` robi ją w obu skalach (dwa światy, REAL i `polska_rozgrywka`):
  - latem brak śniegu na sandrze i w Beskidach poniżej 1900 m, wokół najwyższego miejsca w promieniu ok. 60 km od pierwszych Beskidów na spirali. Dla ziarna testu to ok. 1311 m (REAL) i 1125 m (GAMEPLAY), a test wymaga co najmniej 1200 i 1000 m. Wyżej sprawdza punkty w powietrzu na 1500 i 1850 m;
  - z SS (`localRuntime`) latem `season set mid_summer`: temperatura z porą roku przez hak SS, a na blokach `Biome.shouldSnow` (hak SS na początku metody) i brak śniegu z generacji;
  - z SS zimą `season set mid_winter`: śnieg na sandrze i wysoczyźnie morenowej (co najmniej 100 kolumn spoza czarnej listy SS) i `shouldSnow` na blokach;
  - biomy Netheru i Endu bez profili;
  - opad klienta zgodny z serwerem w 20 punktach i skala klienta równa skali serwera;
  - tryby zamarzania na wodzie morza z chwilowo podmienionym profilem: przy T ok. 0,10 zamarza tylko `WANILIA`, przy T ok. −1 `RZEKA` i `WANILIA`, nigdy `NIGDY`.

`MixinCeleTest` sprawdza w testach jednostkowych, że cele `@Inject` obu mixinów istnieją w `Biome`. Unit testy działają bez mixinów, więc samo działanie mixinów sprawdza dopiero test w grze.

**Odstępstwa S1:**
- Tag `#polskielasy:klimat_polski` ma 10 biomów, nie 12. Preset ma 12 pól, ale `forest` i `river` występują w nim dwa razy. `PolskaKlimatTest` pilnuje, żeby tag i oba presety miały ten sam zbiór.
- `TAGS_LOADED` nie może przypiąć profilu przy pierwszym wczytaniu, bo skala świata nie jest jeszcze znana (tagi wczytują się przed poziomami). Pierwsze przypięcie robią `PCG.createState` (biomy źródła z tagu), `ServerLevelEvents.LOAD` i u klienta `AFTER_CLIENT_LEVEL_CHANGE`. `TAGS_LOADED` (także `/reload`) wylicza profile od nowa ze skalą wziętą z już przypiętych profili. Logika jest w nowej klasie `climate/WiazanieKlimatu` i nie ma stanu statycznego. W grze jednoosobowej klient dzieli obiekty `Biome` z serwerem zintegrowanym (`ClientConfigurationPacketListenerImpl.handleConfigurationFinished`), więc nic nie przypina. Inaczej rozpoznanie skali po wysokości mogłoby zmienić temperatury serwera w świecie bez generatora „Polska”. Przy serwerze zdalnym klient przy zmianie wymiaru tylko przypina profile (`przypnijBezZdejmowania`). Profile zdejmuje wyłącznie przeliczenie po `TAGS_LOADED`, według zawartości tagu.
- Rzeka zamarza przy T < 0,05 liczonym **z korektą pory roku**. Bez niej rzeki nie zamarzałyby zimą na nizinach. Korektę daje nowa metoda `SeasonProvider.temperatureInSeason`. Z SS woła hak `SeasonHooks.getBiomeTemperature`, który nie należy do API SS, więc przy błędzie łączenia wraca do temperatury bez pory roku. Bez SS korekty nie ma (do S2 w M4).
- Zastępczy `minecraft:river` jest na czarnej liście SS i obsługuje też jeziora. Do S5 rzeki i jeziora zimą nie zamarzają (próg 0,05 bez korekty to ok. 2360 m), a nad zastępczymi `river`, `beach` i `ocean` zimą pada deszcz. Zniknie to razem z biomami moda.
- `PolskaDimension.isPolska` rozpoznaje obie ramy. Nowe są `PolskaDimension.scaleOf` (rozpoznanie awaryjne po wysokości, tylko overworld, z ostrzeżeniem w logu) i `PolskaScale.byDimensionType`.
- Test w grze dostał tryb `klimat` (także w `wszystko`, po pomiarze generacji i zrzutach, żeby teleport do Beskidów nie zawyżał ms/chunk). W grze jednoosobowej klient dzieli obiekty biomów z serwerem, więc porównanie opadu w 20 punktach sprawdza głównie rozpoznanie skali u klienta. Pełna kontrola klienta wymaga serwera zdalnego.
- Kryterium „latem brak śniegu na 1500 m” na prawdziwym gruncie jest niesprawdzalne dla ziarna testu: Beskidy w promieniu ok. 60 km od pierwszego miejsca na spirali sięgają ok. 1311 m (REAL) i 1125 m (GAMEPLAY). Spirala z warunkiem wysokości jest za droga (w REAL pierwsze miejsce powyżej 1150 m leży ok. 1000 km od środka, a już sama spirala do pierwszych Beskidów trwa ok. 3 min). Test sprawdza więc grunt do najwyższego znalezionego miejsca (co najmniej 1200 i 1000 m), a 1500 i 1850 m w punktach w powietrzu nad Beskidami.

### 6.3 Serene Seasons

- Tagi z datagenu w `data/sereneseasons/tags/worldgen/biome/`. Bez SS są nieszkodliwe.
  - `lesser_color_change_biomes` (25% zmiany): `bor_suchy`, `bor_swiezy`, `bor_bazynowy`, `bor_wilgotny`, `bor_bagienny`, `swierczyna_gorska`, `kosodrzewina`, `torfowisko_wysokie`, `wydma_szara`.
  - `blacklisted_biomes`, `tropical_biomes`, `infertile_biomes`: nic nie dodajemy. Wody nie trafiają na czarną listę, bo zimą padałby nad nimi deszcz.
- Preset używa klucza `minecraft:overworld`, który jest na białej liście SS.
- Przy generacji (`WorldGenRegion`) SS nie zna pory roku. Chunk wygenerowany zimą jest goły do pierwszego opadu. Akceptujemy to do S2 (M4).
- Kolory trawy, liści, suchych liści i wody są jawne w każdym biomie (enum `Biom`), więc nie zależą od T_bazowa. `downfall` wpływa tylko na kolory: bory suche 0,4; świeże 0,55; mieszane 0,7; grądy i buczyny 0,8; łęgi, olsy i torfowiska 0,85–0,9; plaże i wydmy 0,4.

---

## 7. Gleby, półka brzegowa, mikrorelief, bloki

### 7.1 Mechanizm w `fill()`

- W pierwszej pętli: `kod = klasyfikator(próbka)`, potem `Gleba`, potem tablice kolumny `topState[]`, `l1State[]`, `l1Depth[]`, `l2State[]`, `coverBlocks[]`, `dy[]` (mikrorelief) i `woda[]`. `strata()` tylko porównuje z tablicami.
- **Jednostki:** `coverBlocks = top − vertical.topBlockY(surface − coverDepth)`. Dziś metry porównujemy z blokami, przez co pokrywa w GAMEPLAY jest 2,5–4 razy za gruba. Zmiana ma osobny przełącznik i osobny test, poza złotym testem.
- **`pack()`** obsługuje 5–8 bitów (`PalettedContainer` przyjmuje bity 5–8). Licznik `PACK_FALLBACKS`.
- **`BlockState` pobieramy leniwie** (`GlebaBloki`, `Suppliers.memoize`), bo bloki moda muszą być zarejestrowane przed użyciem. `PolskieBloki.register()` jest wołane przed `PolskaWorldgen.register()`.
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
| `ols` | 20% | 25% (`rooted_dirt`, korzenie) |
| `torfowisko_wysokie` | 15% | 30% (torfowiec) |
| `torfowisko_niskie` | 25% | – |
| `bor_bagienny` | 8% | 20% |
| ląd `szuwar`, ŁOZOWISKO | 35% | – |
| `leg_wierzbowo_topolowy` (namuły) | 5% | – |

- Kałuża powstaje tylko wtedy, gdy 4 sąsiednie kolumny w chunku mają wierzch wyżej. Na skrajnej kolumnie chunka zamiast wody jest błoto.
- Kałuże poszerzają pas, w którym może rosnąć trzcina.
- `hasWater` modelu się nie zmienia.

### 7.4 Gleby

Bloki własne oznaczone *; w nawiasie zastępstwo, gdyby użytkownik ich nie chciał.

| `Gleba` | Wierzch | Niżej (bloki) | Gdzie |
|---|---|---|---|
| BIELICA_INICJALNA | `coarse_dirt` 60% / `podzol` 40% | piasek | `bor_suchy`, `wrzosowisko` |
| BIELICA | `podzol` | `coarse_dirt` 1, piasek | `bor_swiezy`, `bor_bazynowy` |
| RDZAWA | `podzol` / `grass_block` 50/50 | `dirt` 1, piasek lub glina | `bor_mieszany` |
| GLEJOWA_PIASZCZYSTA | `podzol` + 15% `mud` | piasek, `clay` od 2 | `bor_wilgotny` |
| TORF_WYSOKI | `torfowiec`* (`moss_block`) | `torf`* 2–5 (`mud`), piasek | `torfowisko_wysokie`, `bor_bagienny`, PLO |
| TORF_NISKI | `mursz`* (`mud`) + kępy `rooted_dirt` | `torf`* 2 (`mud`), `mud` | `ols`, `torfowisko_niskie`, ŁOZOWISKO |
| BRUNATNA_KWASNA | `grass_block` / `podzol` | `dirt` 2, glina | `las_mieszany`, `jedlina_wyzynna` |
| BRUNATNA | `grass_block` | `rooted_dirt` 1, `dirt` 2 | `grad`, łąki, `pole` (ugór) |
| BRUNATNA_BUKOWA | `dirt` / `coarse_dirt` (trawa się nie rozrasta) | `dirt` 2 | buczyny |
| MURSZ | `mursz`* 40% / `rooted_dirt` + 20% `mud` | `dirt` z `mud` | `leg_jesionowo_olszowy`, ZRODLISKO |
| MADA_LEKKA | `grass_block` / `coarse_dirt` / `sand` + łaty `mud` | piasek i ziemia warstwami | `leg_wierzbowo_topolowy`, WIKLINA, ŁACHA (piasek z błotem) |
| MADA_CIEZKA | `grass_block` | `dirt` 3, `clay` | `leg_wiazowo_jesionowy`, `laka_wilgotna` |
| MADA_ZWIROWA | `coarse_dirt` / `grass_block`; KAMIENIEC: `gravel` z łatami `coarse_dirt` | żwir | `olszyna_gorska` |
| GORSKA_BRUNATNA | `grass_block` / `coarse_dirt` | `coarse_dirt`, flisz | `buczyna_gorska` |
| BIELICA_GORSKA | `podzol` + 20% `moss_block` + 5% `mossy_cobblestone` | `coarse_dirt`, flisz | `swierczyna_gorska` |
| RANKER | `coarse_dirt` / `grass_block`; gołoborza `cobblestone`/`andesite` | rumosz | `kosodrzewina`, `hala` |
| PIASEK_WYDMY | `sand` (biała); `sand` 60% / `coarse_dirt` 40% (szara) | piasek | wydmy |
| PLAZA | `sand`; kamienista `gravel` | piasek | `plaza` |
| DNO_* | rzeka: `sand`/`gravel` (pas przybrzeżny `mud`); potok: `gravel`/`cobblestone`; jezioro: `mud`/`clay`; dystroficzne: `torf`*; morze: `sand`, dalej `mud`; zalew: `mud` | – | wody |

**Zasada sadzonek:** wierzch każdej gleby leśnej musi należeć do `#supports_vegetation`, bo drzewa i runo sprawdzają `would_survive <sadzonka>`. Piasek może leżeć dopiero niżej. Wyjątki: kamieniec, łacha, plaża, wydma biała, dna. Na wydmie białej rosną `short_dry_grass` i `tall_dry_grass` (`#supports_dry_vegetation` obejmuje piasek). Pilnuje tego `GlebaTest`.

### 7.5 Bloki własne (przesunięte do M4, decyzja M2-D)

W M2 nie rejestrujemy bloków. Tabela poniżej opisuje plan na M4; w M2 w tabeli gleb obowiązują zastępstwa z nawiasów.

| Blok | Tagi |
|---|---|
| `polskielasy:torf` (torf wysoki i przejściowy, jasnobrązowy, włóknisty) | `#minecraft:dirt` (daje `substrate_overworld`, `supports_vegetation` i `cannot_replace_below_tree_trunk`), `#minecraft:supports_big_dripleaf`, `#minecraft:mineable/shovel`, `#minecraft:frogs_spawnable_on` |
| `polskielasy:mursz` (czarny, zmurszały torf niski) | jak `torf` |
| `polskielasy:torfowiec` (dywan torfowców jako grunt, czerwono-zielony, bez odcienia) | `#minecraft:moss_blocks`, `#minecraft:supports_big_dripleaf`, `#minecraft:mineable/hoe` |
| opcjonalnie `polskielasy:trzcina`, `polskielasy:palka` (rośliny wynurzone, 2 bloki, dolny może być zalany) | `#minecraft:replaceable_by_trees`, `#minecraft:mineable/hoe`; stoją na `#supports_vegetation` lub w wodzie do 1 bloku |

Każdy blok dostaje blockstate, model, teksturę 16×16, loot i lang PL/EN. Tekstury robi proceduralnie skrypt `tools/tekstury/gleby.py`. Ręczne poprawki w Blockbench mogą przyjść później (D5).

**Tryb diagnostyczny:** flaga `-Dpolskielasy.debug.siedliska=true` maluje wierzch betonem w kolorze strefy lub biomu. Wąskie pasy da się wtedy sprawdzić w grze jednym spojrzeniem.

---

## 8. Dekoracja

### 8.1 Listy kroków (identyczne w każdym biomie, generowane z jednego enuma)

| Krok | Zawartość | Uwagi |
|---|---|---|
| 0 | – | |
| 1 | `polskielasy:lawa_gleboka` (`lake_lava`, rzadkość 9, `height_range` −54..0, `environment_scan`) | bez `lake_lava_surface` |
| 2 | `minecraft:amethyst_geode`, `polskielasy:glazy_narzutowe` | głazy: `block_blob` z `granite`/`diorite`/`mossy_cobblestone`, rzadkość 1/6, filtr `polskielasy:siedlisko` (GLACIAL_TILL i piaski młodoglacjalne) |
| 3 | `minecraft:monster_room`, `minecraft:monster_room_deep` | |
| 4–5 | – | struktury wanilii trafiają tu same |
| 6 | rudy w kolejności wanilii (`ore_dirt` … `ore_copper`), potem `disk_sand`, `disk_clay`, `disk_gravel` | bez `underwater_magma` |
| 7 | – | |
| 8 | – | bez `spring_lava` (sięga do `below_top 8`, w REAL do Y 2023, `valid_blocks` obejmują `dirt`) i bez `spring_water` (do Y 192; wodospady na stokach sprzeczne z hydrologią) |
| 9 | `polskielasy:drzewostan`, `polskielasy:martwe_drewno`, `polskielasy:podszyt`, `polskielasy:strefy_nadwodne`, `polskielasy:runo`, `polskielasy:rosliny_wodne`, na końcu jeden z `polskielasy:nawoz/{lesne,laki,wilgotne,gorskie}` | bez `glow_lichen` (104–157 prób na chunk w świecie bez jaskiń) |
| 10 | `minecraft:freeze_top_layer` | |

- `carvers: []`, bo nasz generator ich nie uruchamia.
- **Bezpieczeństwo względem cyklu:**
  - każdy biom ma te same węzły w tej samej kolejności;
  - jedyny element różny, nośnik mączki kostnej (`count 0`, feature kwiatowy z tagu `can_spawn_from_bone_meal`), stoi zawsze na końcu;
  - nigdy nie wpisujemy featurów inline;
  - placed features w paletach nie mają `minecraft:biome` (dla pod-featura `BiomeFilter` rzuca wyjątek).
- **Rezerwy pod M4** nie są potrzebne, bo nowe warstwy to nowe wpisy palet w istniejących dyspozytorach. W 26.3 nie ma zresztą placed featura `minecraft:no_op` (jest tylko typ).

### 8.2 Dyspozytory

Typy rejestrujemy w `BuiltInRegistries.FEATURE_TYPE`. Konfiguracja (palety) jest w JSON z datagenu. Każdy dyspozytor tworzy własne `WorldgenRandom(Xoroshiro)` z (seed świata, chunk, sól warstwy). Logika to czysta funkcja `Plan.of(ChunkHabitats, seed, chunkPos) → List<Umieszczenie>`, testowalna bez MC. Feature tylko ją wykonuje. `getSubFeatures()` zwraca featury z palet.

| Feature | Działanie | Koszt na chunk |
|---|---|---|
| `drzewostan` | Losowanie warstwowe: oczko c = 16/⌈√n⌉, jeden kandydat na oczko z przesunięciem w wewnętrznych 50% (rozstaw ≥ c/2), szansa n/(16/c)². Kandydat: kod kolumny → pierwsza pasująca reguła palety {biomy, strefy, stl, zespoły} → gatunek z wag × rampa zasięgu (§9) → `PlacedFeature.place` na `OCEAN_FLOOR`. Luki: szum o fali 40–80 m usuwa 5–10%. `max_glebokosc_wody` 0–2 dla wierzby i olszy. | 1–2 ms |
| `martwe_drewno` | `fallen_{oak,birch,spruce,poplar}_tree` według palety, 0,15–0,5 na chunk | < 0,1 ms |
| `podszyt` | krzewy z palety na wolnych kolumnach | < 0,2 ms |
| `strefy_nadwodne` | Pętla po 256 kolumnach według strefy: trzcina (`sugar_cane` 2–4 albo własna trzcina) na półce; pałka (`small_dripleaf` albo własna) w wodzie 1-blokowej na dnie z błota; wiklina; okrajek `tall_grass`/`large_fern`/`bush`; lepiężnik (`big_dripleaf`) na `coarse_dirt`/`rooted_dirt`/torfie; kidzina; łacha. `setBlock(…, 2)` z `canSurvive`. Y bierzemy z `ChunkHabitats`. | ~0,1 ms |
| `runo` | Pętla po 256 kolumnach: ważony stan bloku według (STL, zespół) z pokryciem w %; rośliny podwójne przez `DoublePlantBlock.placeAt`; sprawdzenie powietrza i `canSurvive` | 0,1–0,2 ms |
| `rosliny_wodne` | `lily_pad` (NYMFEIDY), `seagrass`/`tall_seagrass` (ELODEIDY, rzeki) według strefy i głębokości | < 0,1 ms |

Tryb świata dyspozytor czyta z `((PolskaChunkGenerator) generator).settings()`.

### 8.3 `ChunkHabitats`

- Zawartość: `int[256]` kodów, `short[256]` wierzchu gruntu, `short[256]` lustra wody, O i P chunka.
- Zapis w `fill()` jako nietrwały załącznik Fabric (`AttachmentRegistry.create`; API 2.2.30 ma mixiny dla `ChunkAccess` i `ImposterProtoChunk`).
- Odczyt w dyspozytorach przez `level.getChunk(cx, cz)`.
- Brak załącznika (proto-chunk zapisany między TERRAIN a FEATURES, np. przy C2ME lub zamknięciu serwera) uruchamia przeliczenie z modelu (ok. 2–3 ms) i zwiększa licznik `HABITAT_MISS`.
- Usunięcie w `spawnOriginalMobs`, ostatnim etapie generacji.
- Plan awaryjny: pamięć mapowana bezpośrednio na 16 384 wpisy (ok. 25 MB).

**Filtr `polskielasy:siedlisko`** (`PlacementFilter`: biomy, strefy, STL, gleby) czyta `ChunkHabitats`, a dla punktu w sąsiednim chunku jego załącznik. Służy głazom, datapackom i przyszłym featurom w stylu wanilii.

### 8.4 Zastępstwa drzew (`polskielasy:drzewo/<gatunek>`, `polskielasy:krzew/<gatunek>`)

Każdy gatunek to placed feature z `would_survive minecraft:oak_sapling` i feature JSON typu `minecraft:tree`. W M3 zmieniamy plik feature'a, a identyfikator zostaje. Kora jest najbardziej rozpoznawalną cechą gatunku, więc dobieramy ją pierwszą.

| Gatunek | id | Zastępstwo M2 | Wysokość |
|---|---|---|---|
| sosna | `drzewo/sosna` | `straight` 6+4, `pine_foliage`, `spruce_log`, `spruce_leaves`; wariant niski 3+2 (bór bagienny, pas wiatrowy) | 4–16 |
| świerk | `drzewo/swierk` | `minecraft:spruce_checked`; w górach 3% `mega_spruce_checked` | 8–18 |
| jodła | `drzewo/jodla` | `straight` + `spruce_foliage`, `pale_oak_log` (szara kora), `spruce_leaves` | 12–18 |
| brzoza | `drzewo/brzoza` | `birch_checked`, 20% `super_birch_bees_0002` | 6–12 |
| dąb | `drzewo/dab` | `fancy_oak_checked` 60%, `oak_checked` 40% | 5–14 |
| buk | `drzewo/buk` | `fancy_trunk` + `fancy_foliage`, `pale_oak_log`, `oak_leaves` (nie `dark_oak`, bo pień 2×2 jest za gruby) | 10–20 |
| grab | `drzewo/grab` | `straight` 6+2, `blob` r2–3, `pale_oak_log`, `oak_leaves` | 7–10 |
| lipa, jesion, wiąz, klon, jawor | `drzewo/lipa` itd. | osobne identyfikatory, w M2 kształt `fancy_oak` | 6–14 |
| olsza czarna | `drzewo/olsza` | `straight` 8+3, `blob` r2, `dark_oak_log`, `dark_oak_leaves` | 8–12 |
| olsza szara | `drzewo/olsza_szara` | `straight` 6+3, wąski `blob` r2, `pale_oak_log`, `oak_leaves` | 7–10 |
| wierzba biała | `drzewo/wierzba` | `minecraft:swamp_oak` (pnącza jako chmiel) | 5–9 |
| topola | `drzewo/topola` | `poplar_trunk_placer` + `poplar_foliage_placer`, `poplar_log`, `oak_leaves` | 11–16 |
| jarząb | `drzewo/jarzab` | mały `oak` + `blob` r2 | 4–7 |
| kosodrzewina | `krzew/kosodrzewina` | 1 kłoda + `bush_foliage` r2–3, `spruce_log`, `spruce_leaves` | 1–3 |
| wiklina | `krzew/wiklina` | `straight` 1–2 + `bush_foliage` r2, `oak_log`, `oak_leaves` | 2–3 |
| leszczyna | `krzew/leszczyna` | `bush_foliage` r2, `oak_leaves` | 2–3 |
| jałowiec | `krzew/jalowiec` | `spruce_foliage` r1, h 2–3 | 2–3 |

Drzewa są celowo umiarkowane, bo koszt światła rośnie z liczbą liści. Drzewa 30–40 m przychodzą w M3.

### 8.5 Gęstość i skład

Liczby to drzewa na chunk. Gatunki objęte regułami zasięgu (§9) mają wagę mnożoną przez rampę.

| Biom | Drzew/chunk | Skład, % |
|---|---|---|
| `bor_suchy` | 7 | So 95 (30% niska), Brz 5 |
| `bor_swiezy` | 11 | So 85, Brz 10, Św 5 |
| `bor_bazynowy` | 9 (pas wiatrowy 5, sosna niska) | So 90, Brz 10 |
| `bor_wilgotny` | 10 | So 65, Brz 25, Św 10 |
| `bor_bagienny` | 6 | So niska 70, Brz 30 (brzezina: Brz 80) |
| `bor_mieszany` | 10 | So 55, Db 25, Brz 10, Bk lub Św 10 |
| `las_mieszany` | 9 | Db 45, So 30, Bk lub Św 15, Gb 10 |
| `grad` | 9 | Db 35, Gb 30, Lp 15, Js/Kl 5, Bk 10, Św 5 |
| `buczyna_nizinna` | 7 | Bk 85, Db 10, Jw 5 |
| `ols` | 9 | Ol 85, Brz 10, Js 5 |
| `leg_jesionowo_olszowy` | 9 | Ol 55, Js 30, Wz 10, Brz 5 |
| `leg_wierzbowo_topolowy` | 7 | Wb 70 / Tp 30; zespół topolowy 40/60 |
| `leg_wiazowo_jesionowy` | 8 | Db 35, Js 30, Wz 25, Kl/Lp 10 |
| `jedlina_wyzynna` | 10 | Jd 50, Bk 25, Db 15, So 10 |
| `buczyna_gorska` | 8 | Bk 70, Jd 20, Św 5, Jw 5 |
| `swierczyna_gorska` | 12, spada do 4 przy granicy lasu | Św 90, Jrz 10 (Abieti-Piceetum: Jd 30) |
| `olszyna_gorska` | 8 | Olsz 70, Js 15, Św 10, Wb 5 |
| `kosodrzewina` | 0 drzew | kosodrzewina pokrywa 70%, 1 karłowy Św |
| `torfowisko_wysokie` | 0,3 | So niska |
| `wrzosowisko` | 0,5 | So, Brz, jałowiec |
| łąki | 0,1 | pojedyncze drzewa i szpalery |
| `wikliny` | 0 | wiklina pokrywa 80–100% |
| `pole`, `hala`, `plaza`, wydmy | 0 | wydma szara: wierzba piaskowa jako krzew, So 1/8 |

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
| Buk | O ≥ 0,40 lub P ≥ 0,40; H ≤ górna granica regla dolnego; nie na Bs, Bb, Ol ani Lł | flaga BUK, biom `buczyna_nizinna`, wagi w paletach |
| Jodła | P ≥ 0,50, H ≤ 1250; nie na Bs, Bb, Ol | flaga JODŁA, `jedlina_wyzynna`, wariant w `buczyna_gorska` |
| Świerk naturalny | O < 0,30 lub P ≥ 0,50 | flaga SWIERK, domieszki, zespoły NE |
| Grab | H ≤ 600 (stoki S ≤ 700); tylko L i LM | flaga GRAB, palety |
| Olsza szara | P ≥ 0,50 lub O < 0,30 | `olszyna_gorska`, palety |
| Dąb bezszypułkowy, modrzew, cis, bluszcz | reguły z raportu ekologii (§5.1) | tylko `Zasiegi.java` i pole zespołu; użycie od M3/M4 |
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
| `minecraft:is_river` | `rzeka`, `potok` | kopalnie, portale, topielce, kontury map |
| `minecraft:is_beach` | `plaza` | wraki na plaży, skarby, kopalnie |
| `minecraft:is_ocean` | `morze` | wraki, `ruined_portal_ocean`, kopalnie |
| `has_structure/mineshaft` | nieleśne lądowe, `zalew`, wody stojące (jawnie) | kopalnie wszędzie |
| `has_structure/trial_chambers` | wszystkie 36 (jawnie) | komnaty prób |
| `has_structure/ruined_portal_standard` | nieleśne lądowe (jawnie) | portale |
| `has_structure/ocean_ruin_cold` | `morze` | ruiny na dnie Bałtyku |
| `minecraft:water_on_map_outlines` | wody | mapy |
| `sereneseasons:*` | §6.3 | |
| własne: `polskielasy:lesne`, `bory`, `nadwodne`, `gorskie`, `klimat_polski` | – | filtry i mixin |

- **`#is_mountain`** dostają `buczyna_gorska`, `swierczyna_gorska`, `kosodrzewina` i `hala` (decyzja M2-C: posterunki, portale górskie i kopalnie w górach są pożądane). `#is_hill` i `#is_taiga` nie są używane, chyba że krok S5 wykaże, że jakaś struktura wymaga ich do sensownego rozmieszczenia.
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
  - `jezioro_dystroficzne`: `water_fog_color` #3B2A1A.
- **Kolory wody:** rzeka #4A6E5E, potok #4F8FB8, jezioro #3D6E70, dystroficzne #5A4A2E, morze #3A6A7A, zalew #5B7A5A. Kolory trawy i liści są wartościami startowymi z colormapy, kalibrowanymi na zrzutach w czterech porach roku z SS.
- **Chmury:** `visual/cloud_height` w obu typach wymiaru wynosi dziś 192,33. W REAL to poziom nizin. Podnosimy do REAL Y ≈ 1060 (ok. 1000 m n.p.m.) i GAMEPLAY Y ≈ 380.

### 10.1 Struktury (decyzja M2-C: wszystkie, które da się sensownie umieścić)

Krok S5 wypisuje wszystkie tagi `has_structure/*` z jara 26.3 i dla każdej struktury zapisuje wybór w tabeli w `docs/03-m2-biomy.md`. Wartości startowe:

| Struktura | Biomy | Uwagi |
|---|---|---|
| twierdza | wszystkie lądowe (`is_overworld`, `stronghold_biased_to`) | dostęp do Endu |
| kopalnia | wszystkie lądowe i wody śródlądowe | |
| komnaty prób | wszystkie 36 | |
| wioska równinna | `laka_swieza`, `pole`, `wrzosowisko` | wioski wanilijne do M8, potem polskie wsie |
| wioska tajgowa | `bor_swiezy`, `bor_mieszany` | drewniane chaty pasują do borów |
| posterunek | `laka_swieza`, `pole`, `wrzosowisko`, biomy `#is_mountain` | |
| chata czarownicy | `ols`, `torfowisko_niskie` | |
| igloo | `hala` | |
| rezydencja leśna | `grad`, `buczyna_nizinna`, `buczyna_gorska` | odpowiednik ciemnego lasu |
| ruiny szlaku (trail ruins) | bory, `swierczyna_gorska`, `grad` | |
| zrujnowany portal | standardowy: lądowe; bagienny: `ols`, torfowiska; górski: `#is_mountain`; oceaniczny: `morze` | |
| wrak | `morze`; wrak na plaży: `plaza` | |
| ruiny oceaniczne (zimne) | `morze` | |
| zakopany skarb | `plaza` | |
| monument, piramida, świątynia dżungli, starożytne miasto, wioski pustynne, sawannowe i śnieżne | brak | wymagają warunków nieobecnych w świecie |

Każdą strukturę sprawdza gametest (`/locate structure`), a jej położenie na terenie 1:1 ocenia zrzut ekranu.

---

## 11. Pliki i klasy

`JAVA` = `src/main/java/pl/polskielasy/`, `CLIENT` = `src/client/java/pl/polskielasy/client/`.

**Model (czysta Java):**

| Plik | Zmiana |
|---|---|
| `JAVA/worldgen/landscape/ColumnSample.java` | rekordy `Teren`, `Wody`, `Region` (typy proste) |
| `JAVA/worldgen/landscape/LandscapeModel.java` | kontekst wyjściowy w `cellElevation`, flagi form w `sample`, wpięcie siatek, `describe()` na nowych polach |
| `JAVA/worldgen/landscape/RiverNetwork.java` | `RiverHit` z polami d, W, lustro, u, półszerokość dna, spadek, brzeg wypukły; pierścień starorzecza |
| `JAVA/worldgen/landscape/Landform.java` | progi z `Pietra` |
| `JAVA/worldgen/landscape/CoarseTerrainField.java`, `RegionalField.java`, `DirectCache.java` | nowe |

**Siedliska** (`JAVA/worldgen/habitat/`, czysta Java):
- `Biom` (enum: id, grupa, T, downfall, kolory, atmosfera, spawny, tagi, zamarzanie, nośnik mączki);
- `Strefa`, `Stl`, `Zespol`, `Pokrycie`, `Gleba`, `Gatunek`, `Siedlisko` (pakowanie do `int`);
- `Klasyfikator`, `Wilgotnosc`, `Trofia`, `StrefyNadwodne`, `Pietra`, `Wybrzeze`, `Woda`, `Zasiegi`, `Lesistosc`, `Kalibracja`.

**Generator** (`JAVA/worldgen/chunk/`):
- `PolskaBiomeSource` (przepisany);
- `PolskaChunkGenerator` (klasyfikacja i gleba w `fill`, półka, mikrorelief, `pack` 5–8 bitów, `coverBlocks`, `ChunkHabitats`, F3, wiązanie klimatu);
- `PolskaBiomy` (`ResourceKey`), `ChunkHabitats` (nowy);
- `PolskaSettings` (pole `wersja`: `optionalFieldOf`, brak = 1, presety M2 = 2);
- `PolskaDimension` (`isPolska` dla obu skal).

**Powierzchnia:** `JAVA/worldgen/surface/GlebaBloki.java`, `PolkaBrzegowa.java`, `Mikrorelief.java`.

**Featury** (`JAVA/worldgen/feature/`):
- `PolskaFeatures` (rejestracja typów, filtra i załącznika);
- `DrzewostanFeature`, `MartweDrewnoFeature`, `PodszytFeature`, `StrefyNadwodneFeature`, `RunoFeature`, `RoslinyWodneFeature`, `SiedliskoFilter`;
- `plan/` (czyste planery), `config/` (rekordy palet z kodekami).

**Bloki:** brak w M2 (decyzja M2-D); `JAVA/block/PolskieBloki.java` w M4.

**Klimat:**
- `JAVA/climate/`: `PolskaKlimat`, `KlimatBiomu`, `BiomeKlimat`, `WiazanieKlimatu` (od S1);
- `JAVA/mixin/`: `BiomeKlimatMixin`, `BiomeTemperatureMixin`, `BiomeFreezeMixin`;
- `CLIENT/KlimatKlienta.java`.

**Pozostałe:**
- `JAVA/command/PolskaCommands.java` (cele `biom`/`strefa`, `tutaj` z drugą linią);
- `JAVA/PolskieLasy.java` (kolejność rejestracji, zdarzenia), `JAVA/worldgen/PolskaWorldgen.java`;
- `CLIENT/screen/PolskaPresetEditor.java`.

**Datagen** (`CLIENT/datagen/`): `PolskieLasyDataGenerator`, `BiomyProvider` (`Registries.BIOME`, **`Registries.FEATURE`**, `Registries.PLACED_FEATURE`), `TagiBiomowProvider`, `TagiBlokowProvider`, `TagiFeaturProvider`, `JezykProvider`, `ModeleProvider`, `LootProvider`. Wynik trafia do `src/main/generated` w repozytorium. Test uruchamia `runDatagen` i sprawdza `git diff --exit-code`.

**Zasoby:**
- `src/main/resources/polskielasy.mixins.json` (nowy), `fabric.mod.json`;
- `data/polskielasy/worldgen/world_preset/polska.json`, `polska_rozgrywka.json`;
- `data/polskielasy/dimension_type/polska.json`, `polska_rozgrywka.json` (chmury);
- `tools/tekstury/gleby.py`.

**Testy:** `src/test/java/pl/polskielasy/...` (§12). **Dokumentacja:** `docs/01-architektura.md`, `docs/00-decyzje-do-podjecia.md`, `docs/m2/`.

---

## 12. Testy i definicja ukończenia

### 12.1 Jednostkowe (JUnit, bez gry)

| Test | Co sprawdza |
|---|---|
| `GoldenTerrainTest` | hash (surface, waterLevel, waterKind, type, substrate, coverDepth) dla 4096 punktów × 2 skale × 2 ziarna jest identyczny z M1 |
| `LandscapeModelTest`, `RiverNetworkTest` (rozszerzenia) | nowe pola są deterministyczne i skończone; d jest ciągłe (\|Δ\| ≤ 1,5 m na 1 m poza przełączeniem koryta) i ≤ 0 w korycie; u ∈ [0, 1] w dnie; ok. 50% brzegów w zakolach jest wypukłych |
| `RegionalFieldTest` | zakres [0, 1]; gładkość; pas bez buka i świerka zajmuje 10–20% lądu na 3 ziarnach; zasięg P ≥ 0,5 od osi pasma (10 ziaren razem); Wz patrzy na zachód; niezależność od pamięci i wątków |
| `KlasyfikatorTest` | determinizm; tabelaryczne przypadki syntetyczne; każdy z 36 biomów i każda strefa osiągalne; brak łęgu poza dnem i źródliskami na 10⁶ próbek |
| `StrefyNadwodneTest` | przekroje 200 rzek klas A, B, C × 2 skale. Kolejność: koryto → wiklina → okrajek → łęg wierzbowy → topolowy → wiązowy → zbocze (A), koryto → ziołorośla → OlJ → strefowe (B), koryto → kamieniec/wiklina → olszyna → strefowe (C). Minima w blokach (E11) spełnione |
| `PietraTest` | Beskidy: poniżej 1100 m buczyna, jedlina i olszyna ≥ 70%; 1200–1350 m świerczyna ≥ 80%; kosodrzewina tylko przy dużym masywie; granica regla na stokach S wyżej niż na N o 80–120 m |
| `ZasiegiTest` | zero buka przy O < 0,35 i P < 0,35; zero jodły przy P < 0,45; zero naturalnego świerka przy O > 0,35 i P < 0,45 |
| `BiomeSharesTest` | 200 tys. punktów, 1000×1000 km REAL i 30×30 km GAMEPLAY, 3 ziarna (rozrzucone punkty: ok. 16 `landElevation` na punkt w siatce terenu, §3.2). Tryb N: lesistość ≥ 88%, SANDR bory ≥ 85% lasu. Tryb D: lesistość 26–34%, bory 45–58% lasu, typy świeże 50–70%, olsy i łęgi 3–6% |
| `PolskaKlimatTest` | obie skale: T(0 m) = T_bazowa; latem śnieg ≥ 1950 m; przy −0,25 próg 1050–1150 m; wszystkie T_bazowa ≤ 0,8 |
| `BiomeJsonTest` | 36 plików; T ≤ 0,8; klucze lang PL i EN; obecność w `is_overworld`, `trial_chambers` i `klimat_polski` |
| `KolejnoscFeaturesTest` | listy wszystkich biomów są identyczne poza nośnikiem mączki; nośnik jest ostatni; każde id istnieje |
| `PlanDrzewostanuTest`, `PlanRunaTest` | determinizm; średnia liczba drzew ±10% od palety; rozstaw ≥ c/2; udziały gatunków ±5%; pokrycie runa ±5% |
| `GlebaTest` | `coverBlocks` w obu skalach; wierzch gleb leśnych należy do `#supports_vegetation`; liczba stanów w sekcji przy powierzchni ≤ 32 |
| `PolskaCommandsTest` | nowe cele `biom` i `strefa` dają się znaleźć |
| `SampleKosztTest` | raport (bez asercji): µs na `sample` i `klasyfikuj` dla 100 tys. kolumn wobec M1 |

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
| `PolskaWorldClientGameTest` | 11 miejsc w obu skalach (łęg A, łęg B, ols, szuwar jeziorny, wikliny, bór suchy na wydmie, buczyna, regiel górny, kosodrzewina, plaża z wydmami, torfowisko wysokie). Po stronie serwera `getBiome` = klasyfikator, a blok gruntu = `GlebaBloki`; `generator.validate()` bez wyjątku; zrzuty ekranu |
| `SiedliskaClientGameTest` | Transekty: 3 rzeki × 2 skale × 2 tryby, kryteria z §4.6. Półka: ≥ 90% kolumn strefy brzegowej ma wodę obok bloku wierzchu. Zamknięcie wody w blokach (brak rozlewów po 200 tickach). `/locate structure` znajduje stronghold, mineshaft i trial_chambers. `/locate biome polskielasy:grad` trwa < 2 s |
| temperatura | bez SS brak śniegu poniżej 1900 m latem; z SS (`localRuntime`) zimą śnieg na nizinie, latem brak na 1500 m; opad klienta (`ClientLevel.getPrecipitationAt`) zgodny z serwerem w 20 punktach |
| `PerformanceClientGameTest.profileStages` | BIOMES, TERRAIN, FEATURES i LIGHT oraz liczniki `CLASSIFY_NANOS`, `HABITAT_MISS`, `PACK_FALLBACKS` i czas każdej warstwy dyspozytorów, przed M2 i po każdym kroku |
| determinizm | dwa światy z tym samym ziarnem, z paczką optymalizacyjną (C2ME) i bez; skrót bloków 64 chunków identyczny |
| `UiAndCommandsClientGameTest` | wszystkie cele; `tutaj` zawiera biom, siedlisko, strefę, glebę i T |

### 12.4 Definicja ukończenia M2

1. `./gradlew build test runClientGameTest` przechodzi w obu skalach i obu trybach. Datagen jest aktualny. W logach nie ma „Feature order cycle”, „Unknown registry key” ani „Tried to biome check”.
2. Świat Polska nie ma biomów spoza `polskielasy`. F3 pokazuje biom, STL i strefę.
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
| R5 | Mixin na prywatnej metodzie lub zmiana bramki SS ≤ 0,8 w kolejnym dropie | `require = 1`, `PolskaKlimatTest`, gametest z SS |
| R6 | Rozjazd klimatu klienta i serwera (inny typ wymiaru, datapack) | tylko wizualny; typ wymiaru z identyfikatora, w razie braku wanilia; ostrzeżenie w logu |
| R7 | Półka lub kałuże wylewają wodę | półka na tym samym Y co woda; kałuże tylko przy 4 sąsiadach wyżej; test zamknięcia wody w blokach |
| R8 | Zmiana `ColumnSample` przypadkiem zmienia teren | złoty test (S0) przed jakąkolwiek zmianą |
| R9 | Narastający koszt modelu | budżety, `SampleKosztTest`, zakaz `describe`/`landElevation`/`coastDistance` w generacji |
| R10 | Heurystyki DGW, trofii, P_las, O i P dają złe udziały | wszystkie progi w `Kalibracja`; `BiomeSharesTest` i PNG; punkt kontrolny 1 |
| R11 | `applyBiomeDecoration` zbiera biomy ze wszystkich sekcji 9 chunków (1179 sekcji w REAL) | pomiar w S10; ewentualne nadpisanie z jedną sekcją na chunk, kosztem utrzymania |
| R12 | Zastępcze drzewa za małe dla skali 1:1, a `pale_oak_log` u buka, grabu, jodły i olszy szarej myli graczy | umiarkowane gęstości; opis „zastępstwo do M3” w `tutaj`; M3 |
| R13 | Światy z M1 dostaną szwy na styku starych i nowych chunków | brak migracji; pole `wersja` na przyszłość |
| R14 | Rzadkie biomy daleko w REAL (kosodrzewina, hala) | cele komendy `znajdz` z dużym promieniem; w testach syntetyczne próbki |
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
