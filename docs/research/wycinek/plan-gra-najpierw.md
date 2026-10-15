# Pionowy wycinek „najpierw gra”: plan wykonawczy (2026-10-14)

Perspektywa planu: **najmniejszy zakres, po którym gracz idący przez grąd i bór świeży ma wrażenie kompletnego, żywego polskiego lasu**. Wszystko, czego gracz w ciągu godziny spaceru nie zauważy, przechodzi do M3–M7. Plan opiera się na trzech raportach z `scratchpad/slice/`: ekologia (`research-ekologia.md`), technika drzew (`research-technika-drzew.md`) oraz fauna i grafika (`research-fauna-grafika.md`), a także na `docs/BRIEF.md`, `docs/00`, `docs/01` §8 i `docs/03` §1, §8, §9. Repozytorium jest bez zmian.

Oznaczenia: **[R]** rekomendacja, **[O]** szacunek, **CP-x** punkt kontrolny użytkownika, **W-x** nowa decyzja tego planu.

---

## 0. Teza

Wrażenie „prawdziwego lasu” w grze robi pięć rzeczy, w tej kolejności:

1. **Skala i pokrój drzew:** smukłe pnie sosen 20–28 bloków z koroną w górnej ćwiartce oraz dwa piętra grądu (dęby i lipy nad grabami). Dziś są to drzewa wanilii o wysokości 5–16 bloków i pniu grubości bloku. To największa różnica.
2. **Kora i kolor liści gatunku:** po korze odróżnia się gatunki z bliska.
3. **Dno lasu:** w borze borówka, igliwie i mech, w grądzie wiosenny łan zawilca, ściółka i trochę martwego drewna.
4. **Ruch i dźwięk:** sarna uciekająca między pniami, ślady dzika, bębnienie dzięcioła i śpiew zięby.
5. **Pora roku:** grąd wiosną i latem wygląda inaczej. Daje to tanio zawilec znikający po ulistnieniu i borówka z owocami latem.

Wszystko inne (mapa drzewostanów, klasy wieku, pełne zestawy drewna, grzybobranie, mchy gatunkowe, wiewiórka, sójka jako encja, zima bezlistna, dynamika lasu) wzbogaca las, ale go nie tworzy. Odkładamy to.

## 1. Zakres

### 1.1 Co wchodzi

| Warstwa | Zakres wycinka | Uwagi |
|---|---|---|
| Biomy | `oak_hornbeam_forest` (grąd) i `fresh_pine_forest` (bór świeży), oba tryby (N i D), obie skale | Palety i normy dostrajamy tylko dla tych dwóch biomów. |
| Gatunki drzew | sosna, brzoza, świerk, dąb, lipa, grab oraz wspólny archetyp „liściaste szerokie” dla buka, klonu i jesionu (inna kora, ten sam kształt) | Klucze `polishforests:tree/<gatunek>` zostają, zmienia się tylko plik featura (§8.4 planu M2). Nowe drzewo pojawia się więc wszędzie, gdzie paleta używa gatunku (W-1). |
| Pnie | `TrunkBlock` w wariancie D (promień 1–7 px, kierunek rodzica, zalanie; 84 stany na gatunek), modele JSON z datagenu, kolizja | Bez wersji okorowanej, pni 2 × 2 i czół dla każdej średnicy. |
| Kształt drzew | czysty planer `TreeShapePlan`: parametryczny szkielet trzech archetypów (iglasty, liściasty smukły, liściasty szeroki), model rury, wokselizacja, skupienia liści z BFS, przycięcie koron do sąsiadów, promień korony ≤ 16 | Bez kolonizacji przestrzeni i szablonów NBT. Jedna klasa wieku na piętro. |
| Liście | własny blok liści każdego gatunku wycinka (`TintedParticleLeavesBlock`); kolor to odcień listowia biomu × mnożnik gatunku, więc pory roku przychodzą z SS (B6 w wersji wycinka) | Własnych krzywych jesieni i gołych gałęzi zimą nie robimy (M3). Brzoza i świerk biorą tekstury wanilii przez id. |
| Sadzonki | własna sadzonka każdego gatunku, której `TreeGrower` wskazuje ten sam klucz `tree/<gatunek>` | Tekstury brzozy i świerka z wanilii przez id (W-6). |
| Drzewostan | grąd: piętro a1 (3–5 drzew na chunk, 24–32 bloki) i a2 (5–8 grabów, 15–20 bloków), luki 18–45 bloków, podrost w lukach; bór świeży: 8–12 sosen, domieszka brzozy, rzadki podszyt, podrost sosny tylko w lukach | Jeden stan lasu, pośredni między gospodarczym a naturalnym (W-2). |
| Martwe drewno | kłody leżące, posusz i pniaki z tego samego wokselizatora (cienkie pnie); grąd ok. 0,5 obiektu na chunk, bór ok. 0,15 kłody i 1 pniak na chunk | Zamiast `deadwood/*` gatunków wycinka (W-9). |
| Runo | 4 własne bloki (wyjątek od M2-D, W-4): **borówka czarna** (stany: zielona, z owocami do zjedzenia, jesienna), **zawilec gajowy** (liście, kwiat, zanik), **igliwie** (dywan segmentowy jak `leaf_litter`), **orlica** (2 bloki, zielona i brązowa) | Stan sezonu zmienia losowy tick według `SeasonProvider`, także bez SS. Reszta runa zostaje wanilijna (mech, `leaf_litter`, `wildflowers`, konwalia, paprocie). |
| Fauna | **sarna** i **dzik** jako pełne encje (GeckoLib 5.5.7 + SBL 2.0.3), **żaba trawna** jako wariant wanilijnej żaby (`frog_variant`), wilk i królik usunięte ze spawnów lasów | Sarna: rytm dobowy, ucieczka, szczek, futro według sezonu. Dzik: nocna wataha za lochą i buchtowisko. |
| Dźwięk | `ForestAmbience`: dzięcioł (bębnienie I–V, kucie), zięba i kos za dnia, puszczyk nocą, skrzek sójki płoszący sarny (sam dźwięk) | Tylko nagrania CC0 i CC BY z `CREDITS`. |
| Grafika | proceduralna biblioteka `tools/art` z lintem stylu i `checkArt`, ok. 26–30 tekstur bloków i ok. 6 tekstur zwierząt; generator modeli `tools/fauna` | Licencja zasobów: W-D5 i F5. |
| Testy | JUnit planera i wokselizatora, podglądy PNG drzew i drzewostanu 3 × 3 chunki, gametest `slice` (stałe miejsca obu lasów, obie skale i oba tryby, zrzuty, liczniki, determinizm), `fauna`, `stages` A/B | Złoty test terenu bez zmian: wycinek nie zmienia terenu. |

### 1.2 Co nie wchodzi (i dokąd idzie)

| Odłożone | Dokąd | Dlaczego można odłożyć |
|---|---|---|
| mapa drzewostanów, oddziały, klasy wieku, zręby, uprawy w rzędach, suwak 85/15 (D9) | M3 i M8 | Gracz widzi jeden stary las. Różnorodność daje jedna warstwa luk i drgania wysokości. |
| kolonizacja przestrzeni, drzewa pomnikowe (NBT), pnie 2 × 2, czoła pni dla każdej średnicy, 2–3 warianty kory | M3 | Parametryczne korony wystarczą z odległości spaceru. Pomniki są rzadkie. |
| krzywe jesieni gatunków (`SpeciesFoliageTint`), gołe korony zimą (FRAPI), igły modrzewia | M3 | Jesień i zimę daje na razie SS. |
| pełne i „lite” zestawy drewna (deski, drzwi, łodzie, tabliczki, półki), korowanie | M3 (D1) | Pnie dają wanilijne kłody najbliższego wyglądu (W-D1). |
| drzewa pozostałych biomów (olsza, wierzba, jodła, topola, wiąz, jarząb) | M3 | W biomach poza wycinkiem zostają zastępstwa M2 (ryzyko R-8). |
| mchy gatunkowe, porosty, chrobotki, przylaszczka, kokorycz, brusznica, wrzos własny, huby na cienkich pniach, mrowiska | M4 | Wanilijne zastępstwa wystarczą. |
| grzybobranie (D4), fenologia pełna, torf, trzciny, pałki | M4 | Poza dwoma lasami wycinka. |
| wiewiórka, sójka jako encja, jeleń, lis własny, własny spawner, domy (nory, barłogi jako bloki), dorastanie młodych | M6–M7 | Najdroższe zwierzęta wycinka. Wiewiórka zależy od pni M3. Wycinek ma je jako opcje V-O1 i V-O2. |
| dynamika lasu (wzrost, wiatrołomy, kornik) | M8 | |
| sarna polna, fauna łąk w trybie D | M6 | |

## 2. Zasady obowiązujące w wycinku

- **Z1–Z9 bez wyjątków:** planery to czyste funkcje (ziarno, pozycja, parametry). Palety są w JSON z datagenu. Kolejności stałych enumów nie zmieniamy, nowe stałe idą na koniec. Kopia M1 jest zamrożona.
- **Sole:** stare literały `derive("…")` zostają. Nowe pola dostają nowe sole: `feature.tree.shape`, `feature.tree.crown`, `feature.deadwood.shape`, `feature.ground.phase`, `feature.tree_stand.layers`, `feature.tree_stand.canopy_gaps`. Luki M2 (`feature.tree_stand.gaps`) zostają.
- **Z10:** nowe drzewa i runo działają przez istniejące kody ekotonu (`Ecotone`, `ChunkEcotones`), bez osobnej logiki przejść. Przy zamianie gatunku globalnie (W-1) grąd → las mieszany → bór mieszany → bór świeży zmienia skład w tych samych pasach, a wysokość drzew zmienia się razem z gatunkiem. W-1 rozstrzyga, co dzieje się na styku z biomami poza wycinkiem.
- **Obie skale:** drzewa 1:1 w obu skalach (A7, W-3). Gęstości na chunk są takie same, bo skala rozgrywki zmienia krajobraz, nie las. Każdy krok ma zrzuty i testy w REAL i w GAMEPLAY.
- **Tryby N i D:** w trybie D lasy wycinka są tymi samymi biomami, a maska D się nie zmienia. Test `slice` sprawdza oba tryby.
- **Budżety:** cały chunk ≤ 48 ms (M2-25 a). FEATURES i LIGHT dostają w V6 nowe budżety M3 (W-8). BIOMES i TERRAIN się nie zmieniają, bo wycinek ich nie dotyka. Determinizm: teren i dekoracja przy jednej kolejności generacji muszą być identyczne, a w dowolnej kolejności dopuszczamy ≤ 0,5% różnicy bez paczki i ≤ 8% z C2ME.
- **Praca agentów (P1):** A1–A5 i B1–B3. Ryzyko według B1: blok pnia, generator, datagen, rejestracja encji i spawny w datagenie biomów to wysokie ryzyko (2 recenzentów, do 2 rund poprawek). Tekstury, dźwięki, lang i dokumentacja to niskie ryzyko (1 recenzent, 1 runda). Commit i push po każdej średniej zmianie (A3a). Problem blokujący po 2 rundach poprawek oznacza stop i warianty do decyzji (B3).
- **Licencje:** kod MIT. Kod Dynamic Trees (MIT) wolno brać z adnotacją, z Terraform (LGPL) nic. Zasobów Mojang nie kopiujemy, wolno się do nich odwołać przez id w modelu. Nie bierzemy zasobów Naturalist, Alex's Mobs, BOP ani Pixel Perfection. Dźwięki tylko CC0 i CC BY.

## 3. Kroki

**Tempo.** M2 zrobił kroki S5–S10 z rundami recenzji w ok. 7 dni kalendarzowych (2026-10-08 … 10-14), czyli mniej więcej jeden duży krok dziennie. Szacunki niżej to **dni pracy agentów przy tym tempie, z recenzjami**. Raporty badawcze liczyły ostrożniej, „od zera”: technika drzew 16–26 dni, fauna 7–10, grafika 4–6. Te liczby trzeba traktować jako górną granicę przy kłopotach (R-1, R-2, R-4).

Kroki V1 i V2 oraz V8 i V9 mogą iść równolegle w osobnych worktree (A5), bo zmieniają inne pliki.

| Id | Krok | Dni [O] | Ryzyko (B1) |
|---|---|---|---|
| V0 | przygotowanie: SBL 2.0.3, gametest `slice`, baza pomiarowa | 0,5 | niskie |
| V1 | `tools/art` i pierwsze tekstury → **CP-A** | 1–1,5 | niskie (estetyka: R-4) |
| V2 | `TrunkBlock` (pnie o zmiennej grubości) | 1–1,5 | wysokie |
| V3 | planer `TreeShapePlan` | 2,5–4 | wysokie |
| V4 | liście, kolor gatunku, sadzonki | 1 | średnie |
| V5 | integracja z drzewostanem obu lasów | 1,5–2 | wysokie |
| V6 | pomiary, cięcia, budżety → **CP-B** | 0,5–1 | średnie |
| V7 | martwe drewno | 1 | średnie |
| V8 | runo: 4 bloki z sezonem | 1,5–2 | średnie |
| V9 | `tools/fauna`, szkielet fauny, sarna → **CP-C** | 2–2,5 | wysokie |
| V10 | dzik | 1–1,5 | wysokie |
| V11 | tło dźwiękowe i żaba trawna | 1 | niskie |
| V12 | integracja, cztery pory roku, pełne testy, dokumentacja → **CP-D** | 1 | średnie |
| **Razem V0–V12** | | **15,5–20,5** (kalendarzowo ok. 12–16 przy równoległych worktree) | |
| V-O1 (opcja) | wiewiórka | 1,5–2 | wysokie |
| V-O2 (opcja) | sójka jako encja | 1,5–2 | wysokie |
| V-O3 (opcja) | gołe korony zimą (FRAPI) | 1–1,5 | średnie |
| V-O4 (opcja) | pełny zestaw drewna sosny | 1–1,5 | średnie |

### V0. Przygotowanie (0,5 dnia)

- **Zakres:**
  - podniesienie SmartBrainLib 2.0.2 → 2.0.3 (`gradle.properties`, `fabric.mod.json`), kompilacja i uruchomienie gry;
  - nowy gametest `-Pgametest=slice` ze stałymi miejscami: 2 grądy, 2 bory świeże, 1 granica grąd → las mieszany → bór, w REAL i GAMEPLAY, w trybach N i D. Miejsca wyszukuje `HabitatTargets`, a potem zapisujemy je na stałe jak `GAMEPLAY_SITES`;
  - kamery: z ziemi na wysokości oczu, z 30 m i spod okapu;
  - liczniki z `ModFeatures.STATS`: liście, drewno, martwe drewno, runo na chunk;
  - baza: zrzuty i `stages` obecnego M2 w tych miejscach.
- **Ukończone, gdy:** `slice` przechodzi na obecnym `main` w obu skalach i trybach, zrzuty bazowe leżą w `docs/wycinek/baza/`, a SBL 2.0.3 nie psuje `build` ani `fastTest`.
- **Ryzyko:** niskie. SBL 2.0.3 może zmienić sygnatury, ale kodu encji jeszcze nie ma.

### V1. Biblioteka tekstur i pierwsza paczka (1–1,5 dnia, równolegle z V2)

- **Zakres:**
  - `tools/art/`: `texlib.py` (szumy na torusie, Worley, rampy, kwantyzacja, filtr większościowy, stemple, światło), przepisy gatunków, palety k-średnich ze zdjęć CC0 (do repozytorium idą tylko kolory i odnośniki), lint stylu (4–9 kolorów, liście 55–70% pikseli, tint w szarości o średniej jasności 0,45–0,65, kafelkowanie bez szwów, 16 × 16) i zadanie `checkArt` (PNG zgodne bajt w bajt z generatorem);
  - **pierwsza paczka, 10 tekstur:** kora sosny dolna i górne „lustro”, dąb, grab, lipa, buk, igły sosny (kępka), borówka, zawilec, igliwie;
  - arkusz 8× i kafel 3 × 3; gametest „tablica próbek” (ściana bloków i kolumny pni) trafia do V2.
- **Ukończone, gdy:** lint i `checkArt` przechodzą, arkusze leżą w `docs/wycinek/grafika/`, a dolna kora sosny ma czytelne płyty (problem z prototypu rundy 2 jest naprawiony).
- **Ryzyko:** subiektywna estetyka (R-4). Dlatego zaraz potem jest **CP-A**.

### V2. Pnie o zmiennej grubości (1–1,5 dnia)

- **Zakres:**
  - `block/TrunkBlock` w wariancie D (§2.3 raportu techniki): `radius` 1–7, `parent` (6 kierunków), `waterlogged`;
  - modele: 7 szablonów `template_trunk_r1…r7` z wysięgiem 8 px i marginesem ε, z obrotami `parent` w datagenie;
  - kolizja: tablica 6 × 7 kształtów;
  - bloki pni dla 9 gatunków wycinka (sosna dolna i górna jako właściwość albo dwa bloki: do wyboru w kroku), brzoza i świerk z teksturami wanilii przez id;
  - tagi: `#logs`, `#overworld_natural_logs`, gatunkowe;
  - łup: r ≤ 2 daje patyki, r ≥ 3 wanilijną kłodę najbliższego wyglądu (W-D1);
  - palność 5/5;
  - świat testowy i zrzuty kolumn r1–r7 z wysięgiem w każdym z 6 kierunków, z Sodium (`-Poptimization`) i bez, na granicy sekcji, z AO, z obrysem celownika i cząsteczkami niszczenia.
- **Ukończone, gdy:**
  - `checkDatagen` jest czysty;
  - zrzuty nie pokazują szczelin ani migotania na złączach r → r i r → r+1;
  - wysięg rysuje się poprawnie z Sodium;
  - JUnit kształtów (rozmiar i obroty) przechodzi;
  - liczba nowych stanów jest zmierzona (oczekiwane ok. 9 × 84 = 756 przy jednym bloku na gatunek).
- **Ryzyko:** wysokie (R-3, wysięg przy Sodium). Zapasem jest wariant C (model FRAPI czytający sąsiadów). Przejście na niego kosztuje ok. 1–1,5 dnia, a `radius` zostaje.

### Punkt kontrolny CP-A: styl grafiki i pnia (po V1 i V2, ok. 2. dzień)

- **Co dostaje użytkownik:** stronę z arkuszem 10 tekstur, kaflami 3 × 3 i zrzutem z gry: ściana próbek, kolumny pni r1–r7 i jedno ręcznie złożone drzewo testowe (sosna z prostej rury, jeszcze bez planera).
- **Pytanie:** czy styl tekstur i wygląd cienkiego pnia są do przyjęcia. Odpowiedź „nie” zatrzymuje dalsze tekstury: najwyżej 1–2 rundy, potem warianty (B3).
- **Decyzje przy CP-A:** W-D5 i F5 (licencja zasobów) oraz F6.

### V3. Planer drzew `TreeShapePlan` (2,5–4 dni)

- **Zakres:**
  - **Czysta Java** w `worldgen/tree/`:
    - `TreeRequest` → `TreeVoxels`;
    - archetypy z `06 §9.2` i §3.3 raportu techniki: iglasty (sosna z koroną w górnych 25–35% i suchymi kikutami niżej, świerk ze stożkiem), liściasty smukły (brzoza), liściasty szeroki (dąb z grubymi konarami blisko osi, lipa jajowata, grab pod okapem, wspólny dla buka, klonu i jesionu);
    - promienie z modelu rury (n = 2–2,5);
    - wokselizacja 3D DDA z `parent` w stronę korzenia;
    - skupienia liści na końcach pędów z pustym środkiem dużych koron;
    - BFS odległości ≤ 6;
    - przycięcie koron komórką Woronoja ważoną promieniem korony sąsiadów z `TreeStandPlan` (sąsiedzi z jednego wzoru kandydatów świata);
    - promień korony ≤ 16, więc zapis mieści się w 3 × 3 chunkach.
  - **Feature** `polishforests:procedural_tree` (rekord z `MapCodec`): gatunek, zakres wysokości, warstwa. Zapis tylko na `#replaceable_by_trees` albo w powietrzu, drewno nadpisuje liście. Dekoratory wanilii nie działają na cienkich pniach (R-9).
  - **Testy:**
    - JUnit: determinizm, łańcuch `parent` do korzenia, promień nie rośnie ku końcom, liście ≤ 6 kroków od drewna, zapis w 3 × 3 chunkach, brak sporów między koronami;
    - koszt w `@Tag("cost")`;
    - podgląd PNG: bok i góra każdego gatunku (3 losowe drzewa) oraz drzewostan 3 × 3 chunki.
- **Ukończone, gdy:**
  - wszystkie testy przechodzą;
  - planer kosztuje ≤ 0,3 ms na drzewo liściaste szerokie (jeden wątek, mediana);
  - recenzenci obejrzeli pełne podglądy (B4 odrzucona);
  - sylwetki gatunków dają się rozróżnić z boku: sosna z koroną parasolowatą, świerk ze stożkiem, dąb z szerokimi konarami, grab gęsty i niższy.
- **Ryzyko:** wysokie. Możliwe kłopoty: schodki na ukośnych konarach (R-5), za wolny planer (zapas: biblioteka gotowych szkieletów na gatunek, losowo obracanych i skalowanych) oraz determinizm przy dużych koronach (R-6).

### V4. Liście, kolor gatunku, sadzonki (1 dzień)

- **Zakres:**
  - **Liście:** własny blok liści każdego gatunku wycinka (`TintedParticleLeavesBlock`, `mcmeta dark_cutout`).
    - Tekstury: igły sosny własne. Dąb, grab, lipa i archetyp szeroki dzielą jedną teksturę liści liściastych: własną z V1 albo z wanilii przez id. Brzoza i świerk biorą tekstury wanilii przez id.
    - Kolor: `BlockTintSources.foliage()` × mnożnik gatunku (grab jaśniejszy i żółtszy, dąb ciemniejszy, lipa pośrednia). Serene Seasons sezonuje go sam. Sosna i świerk mają stały kolor, brzoza kolor brzozy wanilii × mnożnik.
    - Liście trafiają do `#minecraft:leaves`.
  - **Łup liści:** patyki i sadzonka gatunku.
  - **Sadzonki:** własna sadzonka każdego gatunku, `TreeGrower` na klucz `tree/<gatunek>` (ten sam feature co w generacji, z domyślną warstwą), doniczka, kompost i mączka kostna.
  - **Zrzuty:** liście w 4 porach roku z SS i bez (`-PnoSeasons`).
- **Ukończone, gdy:**
  - `checkDatagen` jest czysty;
  - zrzuty pokazują rozróżnialne odcienie gatunków w lecie i sezonowe kolory SS;
  - sadzonka w świecie testowym rośnie w drzewo z planera;
  - w trybie Fast liście renderują się jako nieprzezroczyste (`instanceof LeavesBlock`).
- **Ryzyko:** średnie. Mnożnik może się źle zachowywać z sezonową nakładką SS, np. zielonkawa jesień. Wtedy zostaje wcześniejsze przejście na `SpeciesFoliageTint` z M3 (+1 dzień).

### V5. Integracja z drzewostanem (1,5–2 dni)

- **Zakres:**
  - **Zamiana featurów:** pliki `tree/<gatunek>` dla 9 gatunków wycinka przechodzą na `procedural_tree` (W-1). Pozostałe gatunki zostają przy zastępstwach M2.
  - **`TreeStandPlan` rozpoznaje warstwy** (nowe pole palety, nowa sól):
    - grąd: piętro a1 to Db, Lp, Kl/Js, Bk, Św (3–5 na chunk, 24–32 bloki); a2 to Gb z domieszką Lp (5–8 na chunk, 15–20 bloków); podrost grabu i lipy oraz leszczyna gęściej w lukach;
    - luki okapu 18–45 bloków z szumu (nowa sól), średnio jedna na 1–2 ha;
    - bór świeży: So 8–12 na chunk (20–28 bloków), Brz 5–15%, Św w a2 na NE (rampa O), podszyt z jałowca i jarzębu 5–20%, podrost sosny tylko w lukach.
  - **Rozstaw:** rozstaw c/2 z M2 liczony według promienia korony warstwy.
  - **Struktury:** `BUILDING_GAP` zależny od promienia korony (korona nie wchodzi nad dach).
  - **Liczniki** liści i drewna na chunk w `STATS`.
  - **Gametest `slice`:** zrzuty, determinizm (`determinism` z C2ME i bez), 0 liści do opadnięcia (próg 0,1%), 0 drzew na ulicach wiosek, korony nie wchodzą pod dachy.
- **Ukończone, gdy:**
  - gęstości w gametest mieszczą się w ±20% palety;
  - test determinizmu przechodzi z obecnymi progami;
  - `VegetationClientGameTest` (transekty §4.6) i `ecotones` przechodzą;
  - zrzuty obu lasów są w obu skalach i trybach;
  - złoty test jest bez zmian.
- **Ryzyko:** wysokie. Duże korony mogą rosnąć nad dachami struktur, a na granicach biomów poza wycinkiem wysokość drzew skacze (R-7, R-8).

### V6. Pomiary, cięcia, budżety (0,5–1 dnia)

- **Zakres:**
  - **Pomiar `stages` A/B** z bazą M2 z V0 (4 przebiegi, mediany) w obu lasach, obu skalach i obu trybach, plus obszar „area”. Mierzymy FEATURES, LIGHT, cały chunk oraz liście i drewno na chunk.
  - **FPS** w stałym przelocie kamery, z paczką optymalizacyjną i bez.
  - **Potwory w dzień** pod okapem grądu: liczba bloków ze światłem nieba < 8 na wysokości gruntu w południe.
  - **Cięcia, jeśli trzeba:** puste środki koron, mniejsze skupiska liści, `propagatesSkylightDown` dla liści sosny i brzozy (gatunki światłożądne) albo mniej drzew a2.
- **Ukończone, gdy:**
  - cały chunk ≤ 48 ms we wszystkich obszarach;
  - nowe budżety FEATURES i LIGHT są zapisane w `docs/` (W-8);
  - FPS ≥ 0,8 × bazy M2 w tym samym przelocie, z paczką optymalizacyjną;
  - pod okapem nie pojawiają się potwory w dzień albo decyzja W-10 jest podjęta.
- **Ryzyko:** średnie. Głównie LIGHT w grądzie (R-1) i FPS przy liściach w trybie Fancy (R-2).

### Punkt kontrolny CP-B: „las drzew” (po V6, ok. 8–10. dzień)

To pierwszy moment, w którym użytkownik **gra**.

- **Co dostaje użytkownik:**
  - stronę ze zrzutami obu lasów (z ziemi, z 30 m i spod okapu, REAL i GAMEPLAY, N i D, lato i jesień) i z granicą grąd → bór;
  - ziarno i współrzędne miejsc (`/polishforests find biome oak_hornbeam_forest`);
  - liczby wydajności;
  - krótką listę rzeczy do sprawdzenia w grze: spacer 10 minut, ścięcie drzewa, posadzenie sadzonki, przelot elytrą nad lasem.
- **Pytania:**
  - czy pokrój i gęstość są dobre;
  - czy skok wysokości na styku z biomami poza wycinkiem (W-1) jest do przyjęcia;
  - czy liczby wydajności są do przyjęcia.
- Przy CP-B można zamknąć też **punkt kontrolny 2 M2** (roślinność), bo zrzuty pokazują ten sam las z nowymi drzewami (W-0).

### V7. Martwe drewno (1 dzień)

- **Zakres:**
  - **Kształty:** kłoda leżąca (cienki pień w poziomie, z kikutem korzeni lub bez), posusz (stojący pień bez liści, ze złamanym wierzchołkiem), pniak (1–2 bloki, r 3–6) i złom. Wszystkie powstają w tym samym wokselizatorze (`DeadwoodShapePlan`, nowa sól). Przy kłodach `leaf_litter` i `moss_carpet`.
  - **Gatunki i normy** (W-9):
    - grąd: ok. 0,5 obiektu na chunk (dąb jako posusz, grab i lipa rzadko, świerk jako wykrot z odsłoniętymi korzeniami, gdy jest w a1);
    - bór: 0,15 kłody i ok. 1 pniak na chunk.
  - Klucze `deadwood/<gatunek>` zostają, zmienia się tylko plik featura.
  - Huby wanilii nie trafiają na cienkie pnie (R-9).
- **Ukończone, gdy:**
  - liczby w gametest mieszczą się w ±30% normy;
  - kłody leżą na gruncie bez wiszenia (≤ 1 blok luki na ≥ 80% długości);
  - nie ma kłód w wodzie ani na ulicach;
  - determinizm jest bez zmian.
- **Ryzyko:** średnie. Kłody na stokach mogą wisieć albo wbijać się w teren. Rozwiązanie: kierunek wzdłuż poziomicy i skrócenie kłody.

### V8. Runo: 4 bloki z sezonem (1,5–2 dni, równolegle z V9)

- **Zakres:**
  - **Borówka czarna:** krzewinka; stany `phase` (zielona, kwitnąca, z owocami, jesienna czerwona); owoce do zebrania i zjedzenia (przedmiot „jagody”); bez kolców i spowolnienia, czyli zastępuje `bush` z M2-13.
  - **Zawilec gajowy:** dwie warstwy jak `wildflowers` (łodyga pod kolor trawy, kwiat bez tintu); fazy: liście (Early Spring), kwiat (Mid Spring), żółknięcie (Late Spring), zanik do pustego bloku-zastępcy z ściółką (Early Summer – Late Winter).
  - **Igliwie:** dywan segmentowy 1–4 jak `leaf_litter`, bez tintu, rdzawy.
  - **Orlica:** 2 bloki, zielona latem i brązowa od Mid Autumn.
  - **Mechanizm sezonu:** losowy tick czyta podsezon z `SeasonProvider` (także `FallbackCalendar` bez SS). Stan generacji ustawia dekoracja według pory w chwili generacji, więc nowy chunk od razu ma właściwą fazę.
  - **Palety:** grąd dostaje zawilec 15–35% w płatach i `leaf_litter`, bór świeży borówkę 40–60%, igliwie 20–40% i mech 30–60%, bór mieszany orlicę. Ekotony runa bez zmian w kodzie.
  - **Lang** EN i PL, tagi (mączka kostna, kompost), 8–9 tekstur z V1 i dodatkowe.
- **Ukończone, gdy:**
  - zrzuty grądu w Early Spring, Mid Spring, Late Spring i Early Summer pokazują pojawianie się i znikanie aspektu wiosennego;
  - borówka owocuje w Mid i Late Summer;
  - transekty §4.6 przechodzą;
  - `GroundLayerPlanTest` jest zaktualizowany;
  - `-PnoSeasons` przechodzi.
- **Ryzyko:** średnie.
  - Zmiana fazy losowym tickiem trwa kilka minut po zmianie podsezonu, a poza zasięgiem symulacji stan zostaje stary (R-11). Zanik stopniowy jest przyrodniczo poprawny. Daleka faza poprawia się przy wejściu gracza, bo blok sprawdza fazę przy pierwszym ticku.
  - Zawilec to ok. 1000 bloków na chunk z losowym tickiem. Koszt ticku trzeba zmierzyć, a gdyby był za duży: faza po stronie klienta przez model, jak zima w M3.

### V9. Szkielet fauny i sarna (2–2,5 dnia)

- **Zakres:**
  - **`tools/fauna`:** generator `.geo.json` i `.animation.json` (szablon czworonoga), pakowanie UV, malowanie z detalami gatunku (oczy, nos, lustro), chody, podgląd z kodu, walidacja; jednorazowa kalibracja osi modelem testowym w gametest i test regresji położenia kości.
  - **Kod:**
    - `entity/base/PolishAnimal`: Animal + GeoEntity + SmartBrainOwner, despawn za 128 blokami (bez imienia i smyczy), ucieczka, `ForestAlarm`, wariant przez `DataTicket`;
    - `AnimalProfile` jako rekord Java;
    - `ModEntities`;
    - predykat spawnu z tagiem `forest_animals_spawnable_on` (`grass_block`, `podzol`, `coarse_dirt`, `rooted_dirt`, `moss_block`) i progiem światła ≥ 5;
    - spawny w datagenie dla lasów wycinka;
    - usunięcie wilka i królika (F1);
    - `creature_world_gen_spawn_probability` 0,15 (F2).
  - **Sarna:** harmonogram dobowy SBL, ucieczka z 20–40 bloków w lesie, szczek „bo-bo” i białe lustro, grupy 1–3 latem i 2–6 zimą, futro letnie i zimowe według podsezonu, parostki kozła (wytarte i scypuł), animacje idle, walk, run, eat, alert i rest.
  - **Gametest `fauna`:** spawn w obu lasach, obu skalach i trybach, tablica zrzutów z 4 stron (dzień, noc, lato, zima), ucieczka przed graczem, despawn, MSPT z 40 sarnami.
- **Ukończone, gdy:**
  - sarny pojawiają się w borze na `podzol` (R-12);
  - średnio ≥ 1 sarna w zasięgu 64 bloków od gracza po 5 minutach w lesie wycinka, a nie więcej niż limit wanilii;
  - MSPT ≤ 0,05 ms na zwierzę;
  - determinizm jest bez zmian;
  - zrzuty przeszły recenzję.
- **Ryzyko:** wysokie. Konwencja osi w generatorze (R-13), rejestracja encji i spawny w datagenie (B1: 2 recenzentów).

### Punkt kontrolny CP-C: pierwsze zwierzę (po V9, ok. 11–12. dzień)

- **Co dostaje użytkownik:** zrzuty sarny (tablica, w lesie, w ucieczce, latem i zimą), krótkie nagranie klatek (GIF z serii zrzutów) i świat do sprawdzenia.
- **Pytania:**
  - czy styl modelu jest do przyjęcia;
  - czy gęstość jest dobra (F2: „zoo” albo pustka);
  - czy dalej robimy dzika w tym samym stylu.
- **Decyzje przy CP-C:** F3 i F4 (czy V-O1 i V-O2 wchodzą).

### V10. Dzik (1–1,5 dnia)

- **Zakres:**
  - model z tego samego szablonu (odyniec, locha, warchlak pasiasty);
  - nocny harmonogram, w dzień barłóg w gęstwinie;
  - wataha 3–8 za najstarszą lochą (`FollowEntity` z pamięci), ucieczka gęsiego, chrapnięcie;
  - **buchtowisko** (C9): `grass_block` → `coarse_dirt` i `rooted_dirt` płatami 2–4 bloków w grądzie, z regułą gry `polishforests:boarRooting` (domyślnie włączona, wyłączana razem z `mobGriefing`);
  - babrzysko przy kałużach mikroreliefu;
  - **agresja** (C6): w wycinku locha z warchlakami (III–VI) tylko ostrzega (chrapnięcie, pozorowana szarża). Atak z obrażeniami odkładamy do decyzji C6.
- **Ukończone, gdy:**
  - `fauna` obejmuje dzika;
  - zrzuty pokazują watahę nocą i buchtowisko za dnia;
  - buchtowanie nie zmienia bloków w generacji (determinizm bez zmian);
  - reguła gry działa.
- **Ryzyko:** wysokie. Ścieżki watahy w gęstym podszycie i buchtowanie na glebach z `SoilBlend`.

### V11. Tło dźwiękowe i żaba trawna (1 dzień)

- **Zakres:**
  - **`ForestAmbience`:** serwerowe zdarzenie co 10–40 s, losowy wysoki pień w promieniu 24–64 bloków w biomie leśnym. Dźwięki:
    - dzięcioł: bębnienie I–V za dnia, kucie cały rok;
    - zięba (IV–VII za dnia) i kos o zmierzchu;
    - puszczyk nocą;
    - skrzek sójki, gdy gracz biegnie przez las, co płoszy sarny w promieniu 32 bloków (`ForestAlarm`).
  - **Dźwięki:** wyszukanie CC0 i CC BY (Freesound, xeno-canto tylko z licencją bez NC), konwersja do mono OGG, `sounds.json`, napisy (lang), `CREDITS.md`.
  - **Żaba trawna:** `polishforests:common_frog` w `frog_variant` (tekstura 48 × 48 na UV wanilii, warunek po tagach `forests` i `waterside`) oraz spawn żab w grądzie przy kałużach.
- **Ukończone, gdy:**
  - dźwięki grają w obu lasach według pory dnia i podsezonu (gametest liczy zdarzenia);
  - każdy plik ma licencję i źródło w `CREDITS`;
  - żaba trawna pojawia się w olsach i grądach, a wanilijne odmiany żab nie pojawiają się w tych biomach.
- **Ryzyko:** niskie. Głównym problemem jest dostępność nagrań bez NC (R-14). Zapasem są dźwięki wanilii.

### V12. Integracja, cztery pory roku, testy, dokumentacja (1 dzień)

- **Zakres:**
  - pełny `tools/dev/run-tests test`, `checkDatagen` i `checkArt`;
  - `runClientGameTest` (`all` w częściach, `slice`, `fauna`, `determinism` z C2ME i bez);
  - zrzuty obu lasów w 8 podsezonach z SS i w 2 bez SS;
  - końcowy `stages`;
  - dokumentacja: `docs/04-wycinek.md` (plan i stan), BRIEF, `docs/00` (decyzje), `docs/01` §8, README (nowe bloki i zwierzęta), `LICENSE-assets`.
- **Ukończone, gdy:** wszystko przechodzi, drzewo robocze jest czyste i wypchnięte, a strona CP-D jest gotowa.
- **Ryzyko:** średnie. `all` przekracza 7000 s, więc biegnie w częściach (jak w S10).

### Punkt kontrolny CP-D: spacer (koniec wycinka)

- **Co dostaje użytkownik:** stronę z galerią pór roku, krótką listą „co sprawdzić w grze” i ziarnem z miejscami.
- **Lista:**
  - spacer o świcie w grądzie (sarny, zięba);
  - noc w borze (dzik, puszczyk);
  - wiosenny łan zawilca;
  - borówki latem;
  - martwe drewno;
  - granica grąd → bór;
  - ścięcie i posadzenie drzewa;
  - obie skale i tryb D.
- **Pytanie:** czy las „żyje” i co jest pierwsze do poprawy przed M3. Po akceptacji wycinek jest zamknięty, a plan M3 bierze z niego mechanizmy: pnie, planer, liście, sadzonki, martwe drewno, `tools/art`, `tools/fauna`, `PolishAnimal`.

### Opcje (po CP-C albo po CP-D, każda osobno)

- **V-O1 Wiewiórka (1,5–2 dnia):**
  - szablon „mały nadrzewny” w skali ×2 (F3);
  - wspinanie po `#polishforests:climbable_trees` (test na 3 grubościach pnia, R-15);
  - skoki między koronami, ucieczka na drzewo;
  - dwie morfy i pędzelki zimą;
  - `AMBIENT`.
- **V-O2 Sójka jako encja (1,5–2 dnia):**
  - szablon „ptak”;
  - krótkie loty pod koronami, chodzenie po ziemi;
  - alarm z V11 przechodzi z dźwięku na encję;
  - `AMBIENT`.
- **V-O3 Gołe korony zimą (1–1,5 dnia):**
  - `ModelLoadingPlugin`: w fazie bezlistnej model liści zastępują gałązki, tylko po stronie klienta;
  - dąb i buk z liśćmi zasychającymi na gałęziach (marcescencja);
  - test z MoreCulling.
- **V-O4 Pełny zestaw drewna sosny (1–1,5 dnia):** pomocnik `WoodSet` i pierwszy gatunek, jako test potoku dla M3.

## 4. Harmonogram i punkty kontrolne

| Dzień [O] | Ścieżka A (świat) | Ścieżka B (grafika i fauna) | Punkt kontrolny |
|---|---|---|---|
| 1 | V0, V2 | V1 | |
| 2 | V2 (zrzuty) | V1 (poprawki) | **CP-A**: styl tekstur i pnia |
| 3–6 | V3 | tekstury dla V4, V7, V8 | |
| 6–7 | V4, V5 | | |
| 8–9 | V5, V6 | V9 (start: `tools/fauna`) | **CP-B**: las drzew, gra |
| 9–10 | V7 | V9 | |
| 10–12 | V8 | V9 (sarna) | **CP-C**: sarna |
| 12–14 | | V10, V11 | |
| 14–16 | V12 | | **CP-D**: spacer |

Bez równoległych worktree kroki zajmą ok. 15,5–20,5 dnia. Przy kłopotach z R-1 … R-5 górną granicą są szacunki raportów, ok. 30 dni.

Równolegle, w osobnym worktree, idzie **krok S8c** (M2-20 d2, krawędzie lasu trybu D). Zmienia `habitat/ForestCover`, a nie pliki wycinka. Zalecam skończyć go przed CP-B (W-7).

## 5. Decyzje dla użytkownika

### 5.1 Otwarte decyzje z `docs/00`

| # | Pytanie | Warianty | Rekomendacja |
|---|---|---|---|
| **D1** | Pełne zestawy drewna | (a) w wycinku żadnych: pnie dają wanilijne kłody najbliższego wyglądu (sosna i świerk świerkowe, dąb, lipa, grab, buk, klon i jesion dębowe, brzoza brzozowa), decyzja o pełnych i „lite” w M3; (b) „lite” zestaw sosny (kłoda, drewno, deski, liście, sadzonka): +1 dzień i 4 tekstury; (c) pełny zestaw sosny (V-O4): +1–1,5 dnia i ok. 20 tekstur | **(a)**. Gracz zauważy deski świerkowe z sosny, ale to nie psuje wrażenia lasu. Listę z raportu techniki (sosna, buk, jodła, olsza, grab, lipa, jesion, klon, modrzew, wierzba) zatwierdzić w M3. |
| **D2** | Dąb, brzoza, świerk, topola: wanilia czy własne bloki | (a) wszystko wanilijne (niemożliwe przy cienkich pniach); (b) własne bloki pnia, liści i sadzonki dla wszystkich gatunków wycinka, tekstury brzozy i świerka z wanilii przez id, własna kora dębu, deski i kłody pełne wanilijne, topola poza wycinkiem (zastępstwo M2); (c) wszystko własne z własnymi teksturami | **(b)**. Cienkie pnie i własne liście (mnożnik gatunku, sadzonka) są potrzebne każdemu gatunkowi, a kora brzozy i świerka wanilii pasuje. Wanilijny dąb nie przypomina dębu szypułkowego, a prototyp własnej kory wyszedł dobrze. |
| **D5** | Tekstury i modele: kto i jak; licencja | (a) proceduralnie przez agentów (`tools/art`, `tools/fauna`) z lintem, `checkArt` i punktami kontrolnymi, z Blockbench jako opcjonalnym narzędziem użytkownika; (b) agenci robią bazę, grafik (zewnętrzny) poprawia; (c) gotowe paczki na wolnych licencjach (prawie nic nie pasuje: CC BY-SA i NC wykluczone). Licencja: CC BY 4.0, MIT albo CC0 | **(a)**, z licencją **CC BY 4.0** (`LICENSE-assets`, `CREDITS` z generatora). Jeśli po CP-A styl nie wystarczy, otwiera się (b). |
| **B6** | Liście | (a) w wycinku: tint biomu z SS × mnożnik gatunku, a własne krzywe jesieni (`SpeciesFoliageTint`) w M3; (b) od razu własne krzywe gatunków (+1 dzień, przebudowa siatek po zmianie kroku koloru); (c) statyczne bloki kolorów jak topola 26.3 | **(a)** w wycinku, (b) w M3. |
| **C6** | Agresja wobec gracza | (a) w wycinku tylko ostrzeżenie i pozorowana szarża lochy; (b) szarża z małym obrażeniem III–VI; (c) brak | **(a)**, a decyzja C6 w M6. |
| **C9** | Zwierzęta zmieniają teren | (a) buchtowanie dzika z regułą gry, domyślnie włączone; (b) wyłączone; (c) tylko kosmetyczne cząsteczki | **(a)**. Ślady dzika są tanim i dobrze widocznym znakiem życia. |
| **C3** | Stan populacji | (a) 2025: dzik rzadszy niż sarna (wagi sarna 10, dzik 4); (b) „klasyczny” | **(a)**. |
| **C10**, **C8**, **C2** | Dorastanie, dropy, hitboxy | w wycinku: młode tylko w grupach spawnu z dorastaniem wanilii, bez dropów, hitboxy ≤ 1,1 bloku | odłożyć do M6, w wycinku jak obok. |

### 5.2 Decyzje z raportu fauny i grafiki

| # | Pytanie | Warianty | Rekomendacja |
|---|---|---|---|
| **F1** | Wanilijne zwierzęta w lasach | (a) zostawić; (b) usunąć wilka i królika, lis zostaje; (c) usunąć wszystkie | **(b)**, w całej grupie lasów (wspólny profil `spawns()`), nie tylko w dwóch biomach. |
| **F2** | Gęstość fauny | (a) prawdziwa (niewidoczna); (b) ×20–50 przez wagi i limity wanilii; (c) własny spawner | **(b)**, kalibrowane przy CP-C; (c) w M6. |
| **F3** | Skala małych zwierząt | (a) 1:1; (b) poniżej 30 cm ×2; (c) tekstura podwójnej gęstości | **(b)**. W wycinku dotyczy tylko opcji V-O1 i V-O2. |
| **F4** | Zestaw gatunków | (a) sarna, dzik, wiewiórka, sójka, żaba, dzięcioł (dźwięk); (b) minimum: sarna, dzik, żaba, dzięcioł (dźwięk) i tło ptaków | **(b)** w planie głównym. Wiewiórka i sójka jako opcje po CP-C, gdy styl sarny zostanie przyjęty. |
| **F6** | Kiedy punkt kontrolny grafiki | (a) wcześnie (CP-A po 10 teksturach i pniu, CP-C po sarnie); (b) na końcu | **(a)**. |

### 5.3 Nowe decyzje tego planu

| # | Pytanie | Warianty | Rekomendacja |
|---|---|---|---|
| **W-0** | Start wycinka a otwarte punkty kontrolne 1 i 2 M2 oraz domyślne decyzje M2-13 … M2-27 | (a) zaakceptować teraz i startować; (b) startować od razu, punkt kontrolny 2 zamknąć przy CP-B (nowe zrzuty pokazują ten sam las z nowymi drzewami), punkt kontrolny 1 (PNG siedlisk) zaakceptować teraz; (c) czekać | **(b)**. |
| **W-1** | Gdzie pojawiają się nowe drzewa | (a) gatunki wycinka wszędzie, gdzie występują w paletach (np. sosna we wszystkich borach, dąb w łęgach i lasach mieszanych), a gęstości i piętra dostrojone tylko w dwóch biomach; (b) tylko w grądzie, borze świeżym i biomach sąsiednich w ciągu Bśw → BMśw → LMśw → grąd; (c) tylko w dwóch biomach (osobne klucze `tree/<gatunek>_slice`) | **(a)**. Ta sama sosna w każdym borze nie ma skoku wysokości na ekotonach Z10 i nie wymaga zmian w palecie. Skok zostaje tylko przy gatunkach poza wycinkiem (olsza, wierzba, jodła) aż do M3. Koszt: wydajność trzeba zmierzyć w całym obszarze, nie tylko w lasach wycinka. (c) łamie Z10 na każdej granicy dwóch lasów wycinka. |
| **W-2** | Charakter lasu w wycinku | (a) jeden stan pośredni: starodrzew 100–140 lat z lukami, martwe drewno między gospodarczym a „stanem właściwym” GIOŚ; (b) dwa stany z szumu (85% gospodarczy jednowiekowy z pniakami, 15% naturalny wielowiekowy), bez mapy drzewostanów (+1–1,5 dnia); (c) pełna mapa drzewostanów (M3) | **(a)**. Daje najwięcej „lasu” na dzień pracy. Suwak D9 i mapa drzewostanów w M3. |
| **W-3** | Drzewa w skali rozgrywki | (a) 1:1 jak w REAL (A7), te same gęstości; (b) niższe o ok. 25% w GAMEPLAY | **(a)**. Drzewo odnosi się do wzrostu gracza, a nie do krajobrazu. W GAMEPLAY wzgórza są niższe, więc las będzie wyglądał na wyższy: ocena przy CP-B. |
| **W-4** | Własne bloki runa przed M4 (wyjątek od M2-D) | (a) 4 bloki: borówka, zawilec, igliwie, orlica; (b) żadnych, zastępstwa wanilii do M4; (c) 8 bloków (także brusznica, przylaszczka, kokorycz, rokietnik) | **(a)**. To cztery najbardziej rozpoznawalne elementy dna obu lasów, a zawilec niesie też porę roku. |
| **W-5** | Jak runo zmienia porę roku | (a) losowy tick z `SeasonProvider` (stan bloku, stopniowo, tylko w zasięgu symulacji); (b) tylko po stronie klienta, przez model (natychmiast, wszędzie, bez zmiany stanu; +1 dzień, FRAPI) | **(a)** w wycinku. (b) w M3 razem z zimą drzew, jeśli dalekie płaty z inną fazą będą raziły. |
| **W-6** | Sadzonki | (a) własne sadzonki gatunków wycinka rosnące w drzewo z planera; (b) liście bez sadzonek do M3; (c) wanilijne sadzonki | **(a)**. Inaczej las nie jest odnawialny dla gracza, a wanilijna sadzonka dałaby małe drzewo wanilii. |
| **W-7** | Krok S8c (M2-20 d2) | (a) przed wycinkiem; (b) równolegle w osobnym worktree, przed CP-B; (c) po wycinku | **(b)**. Pliki się nie pokrywają, a Z10 w trybie D jest spełniona przed pierwszym testem w grze. |
| **W-8** | Budżety wydajności drzew 1:1 | (a) cały chunk ≤ 48 ms twardo, nowe budżety FEATURES i LIGHT ustalone z pomiaru V6, FPS ≥ 0,8 × bazy M2 z paczką optymalizacyjną; (b) dotychczasowe FEATURES ≤ 1,0 bazy leśnej (niemożliwe przy koronach 1:1); (c) tylko cały chunk | **(a)**. |
| **W-9** | Ilość martwego drewna | (a) grąd ok. 0,5 obiektu na chunk (ok. 30–40 m³/ha, „stan właściwy” GIOŚ), bór 0,15 kłody i 1 pniak na chunk; (b) średnia lasów gospodarczych (grąd 0,25, bór 0,1); (c) puszcza (grąd 2, bór 1) | **(a)**. Widać martwe drewno, a las nie wygląda na wiatrołom. W M3 zależnie od naturalności wydzielenia. |
| **W-10** | Potwory pod okapem w dzień | (a) gdy pomiar V6 pokaże problem, liście sosny i brzozy przepuszczają światło nieba, a grąd zostaje ciemny jak ciemny las wanilii; (b) liście wszystkich gatunków przepuszczają niebo (mniej kosztu LIGHT, słabszy cień); (c) przyjąć potwory w ciemnym grądzie | **(a)**. |
| **W-11** | Pokazywanie punktów kontrolnych | (a) strona Artifact ze zrzutami i listą do sprawdzenia, plus pliki w `docs/wycinek/`; (b) same pliki w repozytorium | **(a)**, tak jak punkt kontrolny 2 M2. |

## 6. Ryzyka

| # | Ryzyko | Skutek | Zabezpieczenie |
|---|---|---|---|
| R-1 | LIGHT i FEATURES przy koronach 1:1 (grąd 1000–1500 liści na chunk wobec 600–800 w lesie wanilii) | cały chunk ponad 48 ms, wolne wczytywanie | pomiar w V6, puste środki koron, liście przepuszczające niebo (W-10), mniej drzew a2, biblioteka szkieletów |
| R-2 | FPS przez quady liści w trybie Fancy | spadek FPS w grądzie | MoreCulling (liście jako `LeavesBlock`), gęstość koron, pomiar przelotu, próg W-8 |
| R-3 | wysięg modelu pnia poza blok przy Sodium i AO | szczeliny, migotanie | test w V2 przed planerem; zapas: wariant C (FRAPI, +1–1,5 dnia) |
| R-4 | subiektywna estetyka tekstur i modeli agentów | odrzucenie stylu po wykonaniu wszystkiego | CP-A po 10 teksturach, CP-C po sarnie, lint stylu; wariant D5 (b) |
| R-5 | schodki na ukośnych konarach | dąb wygląda „pikselowo” | konary blisko osi, krótkie odcinki ukośne |
| R-6 | determinizm przy dużych koronach i różnej kolejności chunków | test determinizmu nie przechodzi | przycięcie Woronoja z jednego wzoru kandydatów, promień ≤ 16, JUnit sporów |
| R-7 | korony nad dachami struktur, drzewa na ulicach | błędy jak przed S8 | `BUILDING_GAP` zależny od promienia korony, maska struktur, testy wiosek z `present_day` |
| R-8 | skok wysokości na styku z biomami poza wycinkiem (olsy, łęgi, jedliny: zastępstwa M2 o wysokości 5–14 bloków) | widoczna „ściana” wysokich drzew | W-1 (a) zmniejsza problem, reszta do M3; ocena przy CP-B |
| R-9 | huby, mech i pnącza z dekoratorów wanilii na cienkich pniach | wiszą obok kory | brak dekoratorów wanilii na cienkich pniach do M4 |
| R-10 | sadzonka wyrośnięta przez gracza: zapis poza 3 × 3 chunkami albo w zabudowie | ucięte korony | ten sam planer z limitem promienia, `canGrow` sprawdza wolne miejsce |
| R-11 | faza runa poza zasięgiem symulacji zostaje stara | dalekie płaty zawilca latem | sprawdzenie fazy przy pierwszym ticku po wejściu gracza; W-5 (b) w M3 |
| R-12 | wanilijna reguła spawnu (`grass_block`, światło > 8) | 0 saren w borze na `podzol`, mało pod okapem | własny predykat i tag, próg ≥ 5, test w `fauna` |
| R-13 | konwencja osi w generatorze `.geo.json` | „połamane” modele | model kalibracyjny i test regresji w V9 |
| R-14 | brak nagrań CC0 i CC BY polskich ptaków | brak tła dźwiękowego | manifest licencji, zastępcze dźwięki wanilii, mniej gatunków |
| R-15 | wspinanie wiewiórki po cienkich pniach (opcja V-O1) | wiewiórka nie wchodzi na drzewo | test na 3 grubościach, własny ruch „przy pniu” |
| R-16 | limit `CREATURE` 10 i kumulacja przy eksploracji 1:1 | pusto albo „zoo” | despawn za 128 blokami, wagi, kalibracja przy CP-C |
| R-17 | koszt losowego ticku runa (ok. 1000 zawilców na chunk) | MSPT | pomiar w V8; tick tylko przy zmianie podsezonu albo faza po stronie klienta |
| R-18 | zmiany API GeckoLib i SBL w łatkach 26.3 i 26.4 | rekompilacja | wersje przypięte, aktualizacja świadoma |
| R-19 | szacunki „tempa M2” zbyt optymistyczne dla nowej techniki (bloki, render, encje) | wycinek trwa 25–30 dni zamiast 16–20 | punkty kontrolne co ok. 3–4 dni, B3 po 2 rundach, opcje V-O odcinane jako pierwsze |
