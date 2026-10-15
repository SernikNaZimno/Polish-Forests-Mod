# Pionowy wycinek: plan wykonawczy (perspektywa „fundamenty techniczne”)

Data: 2026-10-14. Podstawa: `docs/BRIEF.md`, `docs/00-decyzje-do-podjecia.md`, `docs/01-architektura.md` §5, §6 i §8, `docs/03-m2-biomy.md` §1 (Z1–Z10), §3.6, §8 i §9, `docs/research/04`, `06` i `07` oraz trzy raporty wycinka w tym katalogu: `research-ekologia.md`, `research-technika-drzew.md` i `research-fauna-grafika.md`. Repozytorium jest bez zmian. Plan powinien trafić do `docs/04-pionowy-wycinek.md` dopiero w kroku V0, po akceptacji użytkownika.

Oznaczenia:
- **[Ź]**: liczba lub fakt z raportu wycinka albo z `docs/research/`;
- **[S]**: szacunek planu;
- „dni”: dni pracy agentów w takiej samej mierze jak w raportach wycinka. Dla porównania: kroki M2 podobnej wielkości (S7, S8b, S10) zajęły z rundami recenzji 1–2 dni kalendarzowe.

---

## 0. Streszczenie

Wycinek jest **budową pięciu systemów, które potem rosną tylko przez dane**:

1. bloki drzew (pień o zmiennej grubości, liście gatunku) i datagen zestawów drewna;
2. czysty generator kształtu drzewa z mapą drzewostanów;
3. potok tekstur;
4. silnik fenologii (stany sezonowe runa, kolory liści);
5. szkielet fauny.

Jeden kompletny las to tylko **dowód**, że te systemy do siebie pasują. Każdy system jest od początku ogólny (gatunek, siedlisko i archetyp to dane), ale w wycinku dostaje tylko tyle danych, ile potrzebują grąd i bór świeży.

Najważniejsze tezy planu:
- **Ryzyko na początek.** Najpierw robimy rzeczy, których potem nie da się tanio zmienić: identyfikatory i układ stanów bloków (zapisywane w światach), technikę pnia z wysięgiem modelu (Sodium, AO), koszt światła przy koronach 1:1 i styl grafiki. Treść (kolejne gatunki runa, zwierzęta) dochodzi później.
- **Pomiar kosztu (V7) zaraz po wejściu drzew 1:1, przed runem i fauną.** Drzewa 1:1 to największa zmiana kosztu od M1: 1000–1500 liści na chunk w grądzie wobec 600–800 w lesie wanilii [Ź technika §4.2].
- **Bez drugiego systemu „na chwilę”.** Nowy feature drzewa dostaje docelowy klucz `polishforests:tree/<gatunek>`, który obiecuje plan M2 §8.4. Zastępstwa wanilii przechodzą pod `tree_substitute/<gatunek>` i do końca M3 obsługują pozostałe biomy. M3 przełącza więc palety, ale nie przepisuje kodu.
- **Koszt [S]:** podstawa V0–V12 i V16 to ok. **35–54 dni pracy agentów**, a z opcjami (sójka, zima, pełny zestaw sosny) ok. **41–62 dni**. Ścieżka krytyczna (drzewa, z runem V9) to ok. 23–36 dni. Fauna, grafika i runo idą równolegle w osobnych worktree (A5).

---

## 1. Zakres

### 1.1 Co wchodzi

| Obszar | W wycinku | Skaluje się potem przez |
|---|---|---|
| Biomy | `oak_hornbeam_forest` (grąd) i `fresh_pine_forest` (bór świeży) w pełni: drzewa, martwe drewno, runo, mchy, ściółka, fauna. Według decyzji W1 także `mixed_pine_forest` i `mixed_forest`, ale tylko z nowymi drzewami (test, że system skaluje się samymi danymi) | palety JSON z datagenu |
| Skale i tryby | obie skale (`poland`, `poland_gameplay`) oraz tryby N i D w każdym gametescie wycinka. Las gospodarczy i naturalny (D9) według W3 | — |
| Gatunki drzew | sosna, dąb, grab, lipa, brzoza i świerk jako pełne drzewa proceduralne. Klon, jesion i buk dostają wspólny archetyp liściasty z teksturą zastępczą albo wypadają z palet wycinka (W1) | `SpeciesShape` w JSON, bez kodu |
| Bloki drzew | `TrunkBlock` (wariant D: `radius` 1–7, `parent`, `waterlogged`; 84 stany), `SpeciesLeavesBlock` (`TintedParticleLeavesBlock` z `phase` 0–3), pomocnik `WoodSet` z poziomami TRUNK / LITE / FULL. Zestawy według D1 i D2 | jedna linijka w `ModBlocks` na gatunek |
| Generator | `TreeShapePlan` (czysta Java): szkielet, model rury, wokselizacja DDA, korona z BFS, przycięcie Woronoja do sąsiadów, okno zapisu 3 × 3 chunki. Podgląd PNG, testy niezmienników, sadzonki | archetypy i parametry gatunku |
| Mapa drzewostanów (lite) | `StandMap` (czysta Java, nowe sole): wydzielenia z flagą gospodarczy/naturalny, klasa wieku, piętra a1, a2 i podrost, luki w lesie naturalnym. Linii oddziałowych i dróg nie ma, to M8 | pełna mapa drzewostanów w M3 |
| Martwe drewno | kłody, posusz, złomy i pniaki (te tylko w lesie gospodarczym) według norm m³/ha z raportu ekologii. Rodzina `DeadwoodBlock` (iglaste i liściaste, 3 stadia rozkładu) | normy w tabeli siedlisk |
| Runo, mchy, ściółka | rodziny bloków `SeasonalPlantBlock`, `GroundCoverBlock` i `BerryShrubBlock`. Grąd: zawilec gajowy, przylaszczka, ziarnopłon, gajowiec, kopytnik, konwalia wanilijna. Bór: borówka, brusznica, rokietnik, igliwie, orlica, wrzos | nowy gatunek runa to wpis danych i tekstura |
| Fenologia | `SeasonPhase` jako wspólna faza roku dla klienta i serwera. Kolory liści gatunku po stronie klienta. `PhenologySweep` po stronie serwera: stopniowa zmiana stanów runa i nadrabianie przy wczytaniu chunka. Działa z Serene Seasons i bez | fenologia M4 |
| Fauna | szkielet (`PolishAnimal`, `AnimalProfile`, spawn z siedliskiem, despawn, alarm leśny, podsezon). Gatunki: sarna, dzik, wiewiórka, żaba trawna (`frog_variant`) i dzięcioł (tylko dźwięk). Sójka według F4 | profile gatunków (rekord, od M6 JSON) |
| Grafika | `tools/art` (biblioteka, przepisy, palety CC0, lint stylu, zadanie `checkArt`), `tools/fauna` (generator `.geo.json` i animacji, kalibracja osi). Ok. 70 tekstur bloków i ok. 12 tekstur zwierząt | przepis i paleta na gatunek |
| Narzędzia jakości | gametest `-Pgametest=slice` (miejsca obu lasów w obu skalach i trybach, zrzuty), `-Pgametest=fauna`, „tablica próbek” bloków, A/B wobec migawki M2 w trybie `stages`, test zamrożenia rejestru bloków | każdy nowy gatunek trafia do tych samych testów |

### 1.2 Czego nie ma (i gdzie trafia)

| Poza wycinkiem | Kamień |
|---|---|
| pozostałe 16 biomów leśnych z nowymi drzewami, jodła, olsze, wierzby, modrzew, cis | M3 |
| pełna mapa drzewostanów: oddziały z liniami, zręby, uprawy w rzędach, młodniki w siatkach, drogi leśne, infrastruktura | M3 (mapa), M8 (infrastruktura) |
| dynamika lasu (D11): wzrost, sukcesja, wiatrołomy, kornik | M8 |
| grzybobranie (D4), owoce poza borówką i brusznicą, własne gleby i torf, trzcina, pałka | M4 |
| pozostałe gatunki runa poza listą §1.1, pnącza (bluszcz, chmiel), porosty epifityczne, dziuple jako bloki | M4 |
| fauna poza §1.1 (jeleń, wilk, łoś, bóbr, ptaki jako encje poza sójką), domy (nory, gawry), własne spawnery gildii, populacje w `SavedData` | M6–M7 |
| łowiectwo, dropy (C8, C12), kleszcze i komary (C11) | poza zakresem lub M7 |
| zima drzew liściastych jako model gałązek (T-D5) | opcja V14 |
| pełne zestawy drewna poza sosną | M3 (D1) |
| Distant Horizons, Continuity i inne mody spoza paczki | M9 |

### 1.3 Zależności od otwartych spraw M2

- **S8c** (M2-20 d2, przyjęte domyślnie: wygładzone pole do `rise()` przed wycinkiem) zmienia tylko `habitat/ForestCover` i maskę trybu D. Pliki nie pokrywają się z wycinkiem, więc **S8c może iść równolegle w osobnym worktree** (A5). Zrzuty trybu D w wycinku powstają po S8c.
- **Punkty kontrolne M2-PK1 i M2-PK2** czekają na akceptację. Wycinek ich nie blokuje, ale zrzuty wycinka zastąpią część kadrów PK2 (grąd i bór), więc najlepiej zaakceptować je przed V6.
- Decyzje M2-24 … M2-27 nie dotyczą wycinka. Budżet całego chunka (M2-25 a) jest punktem odniesienia dla W8.

---

## 2. Zasady fundamentów (obowiązują obok Z1–Z10)

| # | Zasada | Po co |
|---|---|---|
| F-1 | **Planer czysty, feature cienki** (wzór M2: `TreeStandPlan`, `ColumnPlan`). Kształt drzewa, drzewostan, martwe drewno, runo i fenologia to czyste funkcje (ziarno, pozycja, siedlisko, parametry), testowalne bez gry i renderowalne do PNG | testy niezmienników, determinizm, podgląd bez gry |
| F-2 | **Gatunek, archetyp i siedlisko to dane.** `SpeciesShape`, `SpeciesColors`, profile runa i profile zwierząt są w rekordach z kodekiem i w JSON z datagenu. Kod zna tylko archetypy (iglasty, liściasty; czworonóg, nadrzewny, ptak) | M3, M4 i M6 dokładają gatunki bez nowych klas |
| F-3 | **Zamrożenie zapisu.** Identyfikatory bloków i przedmiotów, nazwy i zakresy właściwości (`radius` 1–7, `parent`, `phase`, `decay`) oraz kolejność stałych enumów `Species` i nowych enumów są po PK-W2 zamrożone jak `HabitatBiome`. Nowe wartości dochodzą tylko na końcu, nowe właściwości tylko z wartością domyślną zgodną ze starymi zapisami. Pilnuje tego test migawki rejestru (V1) | światy z wycinka wczytają się w M3–M7 |
| F-4 | **Docelowe klucze od razu.** Feature drzewa siedzi pod `polishforests:tree/<gatunek>`, sadzonka wskazuje ten sam klucz, a zastępstwa M2 przechodzą pod `tree_substitute/<gatunek>`. Klucze worldgen nie trafiają do zapisu chunków, więc przemianowanie jest bezpieczne | M3 zmienia dane, nie kod |
| F-5 | **Wszystko z datagenu, zasoby z generatora.** Blockstates, modele (szablony pni też, przez własny `DataProvider`), łup, tagi, receptury i lang pilnuje `checkDatagen`. Tekstury generuje `tools/art`, a `checkArt` sprawdza, że PNG zgadzają się bajt w bajt z generatorem. Ręczne poprawki leżą w osobnym katalogu `overrides/` z lintem, ale bez porównania bajtów | brak dryfu między źródłem a plikami |
| F-6 | **Ekoton dotyczy każdej warstwy.** Nowe palety drzew, martwego drewna, runa i ściółki wybierają regułę według kodu ekotonu (`Ecotone`, `SoilBlend`). Spawn zwierząt używa siedliska kolumny, a nie biomu z rozmyciem | Z10 bez wyjątków |
| F-7 | **Obie skale, oba tryby, każdy test.** Gametest wycinka zawsze sprawdza REAL i GAMEPLAY oraz N i D. Odległości w planerach ×k (Z7), ale rozmiary drzew i zwierząt są w blokach 1:1 (A7, W5) | brak „skali zapomnianej” |
| F-8 | **Pomiar przed treścią.** Każdy system, który dokłada koszt (drzewa, światło, fenologia, fauna), ma pomiar A/B wobec migawki „baza M2” z V0 w jednej sesji, zanim powstanie na nim treść | budżety nie wychodzą po fakcie |
| F-9 | **Kod tylko na licencjach zgodnych z MIT.** Dynamic Trees i Regions Unexplored (MIT) można czytać i adaptować z adnotacją. Terraform (LGPL) tylko jako zależność, a w wycinku wcale. Zasoby są wyłącznie własne, wanilia tylko przez odwołanie po id | D6, EULA |

---

## 3. Architektura systemów

```
pl.polishforests
├── block/
│   ├── ModBlocks, ModItems                       rejestracja, kolejność stała (F-3)
│   ├── wood/WoodSet, WoodTier                    TRUNK | LITE | FULL; WoodSetRegistry (lista gatunków)
│   ├── wood/TrunkBlock, StrippedTrunk            wariant D, 84 stany
│   ├── wood/SpeciesLeavesBlock                   TintedParticleLeavesBlock + PHASE
│   ├── wood/DeadwoodBlock                        radius, parent, decay 0–2; iglaste i liściaste
│   └── plant/SeasonalPlantBlock, GroundCoverBlock, BerryShrubBlock, PlantProfile
├── tree/                                         (czysta Java)
│   ├── SpeciesShape, Archetype, Allometry        parametry z 06 §9.2, kodek
│   ├── TreeShapePlan, TreeRequest, TreeVoxels    szkielet → promienie → woksele → korona → przycięcie → BFS
│   ├── Voxelizer, CrownClipper, LeafDistance
│   └── StandMap, Stand, StandLayer               wydzielenia, wiek, gospodarczy/naturalny, luki (nowe sole feature.stand.*)
├── worldgen/feature/
│   ├── ProceduralTreeFeature                     typ polishforests:procedural_tree (konfiguracja = SpeciesShape)
│   ├── TreeStandFeature (+ plan/TreeStandPlan)   piętra, kontekst sąsiadów, wywołanie planera wprost
│   └── DeadwoodFeature / ColumnPlan               nowe wpisy palet
├── season/
│   ├── SeasonPhase                               faza roku 0..1 i krok koloru (SS lub FallbackCalendar)
│   └── PhenologySweep (+ PhenologyStamp)         serwer: stopniowe stany runa, nadrabianie przy wczytaniu
├── entity/
│   ├── ModEntities, base/PolishAnimal, base/AnimalProfile, ai/ForestAlarm, ai/HerdLeader
│   ├── spawn/PolishAnimalSpawnRules, spawn/HabitatPopulation (w spawnOriginalMobs)
│   └── roedeer/, wildboar/, squirrel/, (jay/)
└── client/
    ├── color/SpeciesFoliageTint, RawFoliageResolver, ClientSeasonRebuild
    ├── entity/PolishAnimalRenderer
    └── datagen/ModBlockModels (+ TrunkTemplateProvider), ModBlockLoot, ModRecipes, ModBlockTags, …
tools/art/      texlib.py, recipes/, palettes/ (tylko kolory i źródła), lint, sheets → src/main/resources/assets/…
tools/fauna/    szablony archetypów, UV, malowanie, chody, podgląd, walidacja
```

**Przepływ generacji drzewa:**
1. `TreeStandPlan` wybiera kandydata. Używa wzoru kandydatów pni dla całego świata (M2), reguły palety według kodu ekotonu i wydzielenia z `StandMap` (wiek, piętro, gospodarczy/naturalny).
2. Plan tworzy `TreeRequest`: gatunek, wysokość i pierśnica z allometrii, sąsiedzi z tego samego wzoru (także z sąsiednich chunków, bo wzór jest czystą funkcją), nachylenie i ziarno z nową solą `feature.tree.shape`.
3. `TreeShapePlan.plan` zwraca `TreeVoxels`.
4. `ProceduralTreeFeature` zapisuje woksele w oknie 3 × 3 chunki.

Sadzonka i `/place feature` wołają `place()` z parametrami domyślnymi (młode drzewo).

**Przepływ koloru liści (klient):**
1. `RawFoliageResolver` daje kolor listowia biomu bez nakładki SS i z mieszaniem Biome Blend (Z10).
2. `SpeciesFoliageTint` nakłada na niego krzywą gatunku z `SeasonPhase.step()` i fazą drzewa (`PHASE`, hash pnia).
3. `ClientSeasonRebuild` woła `levelExtractor.allChanged()` przy zmianie kroku koloru (2–3 kroki na podsezon).

Gdy `changesFoliageColor` jest wyłączone, liście są letnie.

**Przepływ stanów runa (serwer):** `PhenologySweep` przy zmianie kroku fazy przechodzi wczytane chunki partiami (budżet ticku) i przestawia tylko sekcje, których paleta zawiera bloki sezonowe. Przesunięcie z hasza kolumny daje stopniową wiosnę, a nie skok. Chunk wczytany później nadrabia stan przy wczytaniu według stempla z załącznika. Generacja ustawia od razu stan zgodny z bieżącą fazą.

---

## 4. Kroki

Ścieżki pracy:
- **A (drzewa):** V1 → V3 → V5a → V5b → V6 → V7 → V8;
- **B (grafika):** V2 równolegle z V1;
- **C (fauna):** V4 równolegle z V5, potem V10–V12 po V6;
- **D (runo i fenologia):** V9 po V7, równolegle z V8 i V10.

Recenzje według B1. Wysokie ryzyko (2 recenzentów, do 2 rund) mają rejestracja bloków i encji, datagen, mixiny, generator i budżety. Niskie (1 recenzent, 1 runda) mają tekstury, dane palet, lang i dokumentację. Każdy krok kończy się commitem i pushem (A3a) oraz pełnym zestawem testów przez `run-tests`.

| Id | Krok | Zakres | Ukończony, gdy | Ryzyko | Dni |
|---|---|---|---|---|---|
| **V0** | Przygotowanie | Plan do `docs/04-pionowy-wycinek.md`, decyzje W/F do `docs/00`. SBL 2.0.2 → 2.0.3. Migawka „baza M2” (tag i kopia jak migawka M1) do pomiarów A/B. Stałe miejsca testowe obu lasów (i BMśw/LMśw przy W1 b) w obu skalach i trybach, znalezione `find biome` na ziarnie testowym. Szkielet gametestu `-Pgametest=slice` (zrzuty miejsc, spis bloków) i pakiety `block/`, `tree/`, `entity/` | build, `fastTest` i `slice` w obu skalach przechodzą na niezmienionym świecie. Zrzuty bazowe miejsc w katalogu wycinka. SBL 2.0.3 ładuje się w grze | niskie | 1 |
| **V1** | Bloki drzew i potok datagenu | `ModBlocks` i `WoodSet` z poziomami (TRUNK: pień cienki i okorowany, kłoda; LITE: + drewno, okorowane, liście, sadzonka, doniczka; FULL: + deski, schody, płyta, płot, furtka, drzwi, klapa, przycisk, płytka, tabliczki, półka, łodzie). `TrunkBlock` (wariant D) z kształtami, wodą i korowaniem (`BlockTransformerHelper`, `CopyPropertiesProvider`). Własny `DataProvider` szablonów `template_trunk_r1…r7`. Datagen blockstates (`PropertyDispatch.modify(PARENT)`), łupu (r ≤ 2 patyki, r ≥ 3 kłoda), tagów (`#logs`, `#overworld_natural_logs`, `#<gatunek>_logs`) i lang. Palność, paliwo i kompost jako właściwości przedmiotu. Pierwszy gatunek: sosna z teksturą zastępczą. **Test migawki rejestru** (lista bloków, właściwości, wartości; świadoma regeneracja jak złoty test). „Tablica próbek”: 7 promieni × 6 rodziców, z wodą, na granicy sekcji | zrzuty tablicy bez szczelin i migotania, bez Sodium i z nim (`-Poptimization`), z AO. Kolizja i obrys celownika poprawne. `checkDatagen` czysty. Liczba stanów zgodna z planem (84 na pień). Testy JUnit kształtów i migawki rejestru przechodzą | **wysokie**: wysięg modelu poza blok przy Sodium i AO (R3). Zapas: wariant C (FRAPI), `radius` zostaje | 2–3 |
| **V2** | Potok tekstur `tools/art` (równolegle z V1) | `texlib.py` (szumy na torusie, Worley, rampy, kwantyzacja, stemple, warianty), przepisy kory, liści, desek i czół, palety k-średnich ze zdjęć CC0 (w repozytorium tylko kolory i odnośnik). Lint stylu (4–9 kolorów, liście 55–70% kryjące, tekstury z tintem w szarości, kafelkowanie bez szwów). Zadanie Gradle `checkArt` (porównanie bajt w bajt, katalog `overrides/` tylko lint). Arkusze podglądu. `LICENSE-assets` i generator `CREDITS.md`. Pierwsze ok. 8 tekstur: sosna (kora dolna ×2, „lustro”, czoło, igły), grab (kora, liście), dąb (kora) | `checkArt` i lint przechodzą. Arkusz 8 × i kafel 3 × 3 w katalogu wycinka. Tekstury podpięte do bloków V1 w tablicy próbek | średnie: estetyka agenta (R4), dlatego PK-W1. `checkArt` wymaga Pythona (opisane w BRIEF; zadanie zgłasza czytelny błąd) | 2–3 |
| **V3** | Liście gatunku i kolor sezonowy | `SpeciesLeavesBlock` (`TintedParticleLeavesBlock`, `PHASE` 0–3, 112 stanów), tagi `#leaves`, mcmeta `dark_cutout`. `SeasonPhase` (jedna faza roku dla klienta i serwera, z SS albo `FallbackCalendar`). `RawFoliageResolver` (`ColorResolverRegistry`), `SpeciesFoliageTint` z krzywymi gatunków z `08 §9` (B6), przebudowa siatek, wyłącznik `changesFoliageColor`. Kolor przedmiotu liści (`tints` w `items/*.json`). Liście dla wszystkich 6 gatunków wycinka, dla dębu, brzozy i świerka z teksturą wanilii przez id (D2) | zrzuty 4 pór roku z SS i bez (`-PnoSeasons`) dla 6 gatunków na tablicy. Brak podwójnego sezonowania z SS. Tryb Fast działa (`instanceof LeavesBlock`). Brak zacięcia przy przebudowie (czas klatki w logu) | średnie: zacięcie przy przebudowie (R9); Sodium i własny resolver | 2–3 |
| **V4** | `tools/fauna` i model sarny (równolegle z V5) | Generator `.geo.json` i `.animation.json` z opisu archetypu (czworonóg, mały nadrzewny, ptak), UV bez nakładania, malowanie tekstur, podgląd z kodu, walidacja. **Kalibracja osi i obrotów w grze** (model testowy z kośćmi w znanych kątach, zrzut z gametestu, porównanie z `BakedGeoModel`). Sarna jako encja tylko do renderu (jajo spawnu, bez AI): model, 4 tekstury (koza i kozioł, lato i zima), animacje idle, walk, run, eat i alert. Pakiet `com.geckolib` (GeckoLib 5) | zrzut sarny z 4 stron w grze zgodny z podglądem. Test kalibracji osi przechodzi. Walidator nie zgłasza błędów UV | średnie: znak obrotów w GeckoLib 5 (R-F3) | 1,5–2 |
| **PK-W1** | **Punkt kontrolny 1: wygląd** | Strona ze zrzutami: tablica pni i liści (z Sodium i bez), tekstury V2, liście w 4 porach roku, sarna. Decyzje do potwierdzenia: D5 i licencja (F5), B6, T-D1 i T-D2 (technika i klasy grubości pni) | użytkownik akceptuje styl albo podaje poprawki, zanim powstanie ok. 70 tekstur | — | czekanie |
| **V5a** | Generator: rdzeń i iglaste | `tree/` (czysta Java): `SpeciesShape` z kodekiem (JSON z datagenu), allometria (wysokość i pierśnica według wieku i bonitacji z `06 §9.2`), szkielet iglasty (okółki, kikuty pod nasadą korony), model rury (n = 2–2,5), wokselizacja 3D DDA (największy promień, `parent` w stronę korzenia, typ bloku według promienia: `TrunkBlock` / kłoda z osią / dysk 2 × 2), korona i BFS odległości (≤ 6), przycięcie Woronoja do sąsiadów, okno zapisu, druga kora sosny („lustro”) według wysokości. Zadanie `treePreview` (PNG z boku i z góry: gatunek × klasa wieku, drzewostan 3 × 3 chunki). Testy: determinizm, łańcuch `parent` do korzenia, promień nie rośnie ku końcom, liście ≤ 6 od drewna, zapis tylko w 3 × 3, brak sporów koron, koszt `@Tag("cost")`. Sosna i świerk | testy przechodzą. PNG sosny i świerka w 3 klasach wieku w katalogu wycinka. Koszt planera ≤ 0,2 ms na drzewo iglaste (jeden wątek) [S] | **wysokie**: zakres pracy, schodki ukośnych gałęzi (R4) | 3–5 |
| **V5b** | Generator: liściaste | Archetyp liściasty: pień do nasady korony, konary z dominacją wierzchołkową, gałęzie 2. i 3. rzędu parametrycznie, w dużych koronach opcjonalnie lekka kolonizacja przestrzeni (150–300 punktów), skupienia liści z pustym środkiem. Dąb, lipa, grab i brzoza oraz wspólny archetyp klon/jesion/buk (W1). Tekstury kory, czół i liści grabu, lipy i dębu (ok. 12, V2) | PNG 4 gatunków w 3 klasach wieku. Koszt ≤ 0,5 ms na drzewo liściaste [S]. Testy V5a dla wszystkich gatunków | wysokie: koszt kolonizacji, wygląd koron | 3–5 |
| **V6** | Mapa drzewostanów (lite) i drzewostan wycinka | `StandMap` (wydzielenia 2–30 ha·k² z pola Woronoja z meandrami według W4, nowe sole `feature.stand.*`): gospodarczy/naturalny (W3, D9), klasa wieku (drągowina, dojrzały, starodrzew), luki 18–45 bloków w lesie naturalnym co 1–2 ha [Ź]. `TreeStandPlan`: piętra a1, a2 i podrost. Gęstości: grąd naturalny ok. 15 pni na chunk ze wszystkimi piętrami (dąb, lipa, klon/jesion w a1, grab w a2), gospodarczy jednowiekowy, bór 8–15 sosen na chunk [Ź]. Sosna odnawia się tylko w lukach. Korony na skraju lasu asymetryczne w stronę otwartą (przycięcie z „pustym” sąsiadem). `ProceduralTreeFeature` pod kluczem `tree/<gatunek>`, zastępstwa pod `tree_substitute/` dla pozostałych biomów (F-4). `BUILDING_GAP` z promienia korony i maska struktur. Sadzonki LITE (`TreeGrower` na ten sam klucz). Ekotony: nowe drzewa przez kod ekotonu | gametest `slice` w obu skalach i trybach: spis drzew w normie palety ±20%, liście do opadnięcia 0, `HABITAT_MISS` 0, brak koron w budynkach wiosek (test z S8), zrzuty obu lasów (gospodarczy i naturalny) oraz przejść do sąsiadów. `DeterminismClientGameTest` w progach (0,5% bez paczki, 8% z C2ME). Złoty test bez zmian | wysokie: determinizm na granicach chunków (R5), struktury (R7) | 3–5 |
| **PK-W2** | **Punkt kontrolny 2: drzewa w grze** | Zrzuty i krótki test w grze użytkownika (`runClient -Poptimization`, komendy `find biome`): pojedyncze drzewa, grąd i bór, las gospodarczy i naturalny, obie skale, tryb D. Po akceptacji **zamrożenie identyfikatorów i układu stanów** (F-3). Decyzje W1–W5, D1 i D2 potwierdzone | akceptacja albo lista poprawek (do 2 rund, potem B3) | — | czekanie |
| **V7** | Pomiary i budżety drzew 1:1 | A/B w jednej sesji z bazą M2 (tryb `stages`, 4 przebiegi, obie skale, oba tryby, obszary wycinka i „area”): FEATURES, LIGHT, cały chunk, liście i drewno na chunk, czas planera, chunki na sekundę z C2ME, FPS w stałym przelocie kamery z Sodium, pamięć i liczba stanów. Pomiar światła pod okapem (potwory w dzień, próg spawnu zwierząt). Cięcia według potrzeby: puste środki koron, `propagatesSkylightDown` dla gatunków światłożądnych, rzadsze skupienia liści. Propozycja budżetów M3 (W8) | raport z liczbami w `docs/04`. Cały chunk ≤ 48 ms w każdym obszarze. FPS i chunki na sekundę w granicach W8. Brak potworów w dzień pod okapem (albo decyzja). Budżety wpisane do `docs/03` §3.6 lub `docs/04` | **wysokie**: LIGHT i FPS (R1, R2) | 2–3 |
| **V8** | Martwe drewno | `DeadwoodBlock` (iglaste i liściaste × `decay` 0–2, wokselizator V5), obiekty: kłoda (leżąca, `parent` poziomy), posusz (pień bez liści, kora odpada), złom (pień urwany, korona leżąca obok), pniak (tylko w lesie gospodarczym, wysokość 0,3–0,6), wykrot jako opcja. Normy na chunk z tabeli ekologii [Ź]: bór gospodarczy ok. 0,1 kłody i 1–2 pniaki, grąd gospodarczy ok. 0,25 obiektu, grąd naturalny ok. 2, bór naturalny ok. 1. Forma śmierci według gatunku. Kierunek kłody tak, by mieściła się w oknie zapisu. Huby wanilii tylko na pełnych kłodach (R8). Tekstury ok. 12 | gametest: liczba obiektów na chunk w normie ±30% w obu lasach i trybach, zrzuty, test okna zapisu. FEATURES po V8 ≤ budżet W8 | średnie | 2–3 |
| **V9** | Runo, mchy, ściółka i fenologia | Rodziny bloków: `SeasonalPlantBlock` (`phase`: spoczynek / liście / kwitnienie / zanik, opcjonalnie owoce), `GroundCoverBlock` (segmenty jak `leaf_litter`: igliwie, rokietnik), `BerryShrubBlock` (borówka i brusznica bez kolców, stany owoców, zbieranie), orlica dwublokowa (zielona i brązowa). `PlantProfile` w danych (podsezony faz z ekologii §3 i §9 [Ź]). `PhenologySweep` z budżetem ticku, przesunięciem z hasza kolumny i nadrabianiem przy wczytaniu. Generacja ustawia od razu bieżącą fazę. Palety `ground_layer` i `understory` dla obu lasów z nowymi blokami (pokrycia z ekologii: borówka 40–70% kolumn boru, mchy 60–90%, aspekt wiosenny grądu 60–100%). Tagi SS (`sereneseasons:*_crops` dla borówki). Mrowisko jako prosty blok lub struktura, ok. 1 na 300 chunków (W10). Tekstury ok. 25 | zrzuty grądu i boru w 4 podsezonach (III, IV, VI, X), z SS i bez. Transekty runa w normie. Koszt sweepu ≤ 0,5 ms na tick serwera przy zmianie fazy, bez skoku MSPT > 5 ms. Liczba pakietów bloków w logu w granicach W9. `GroundLayerPlanTest` rozszerzony | wysokie: seria pakietów sieciowych i zgodność stanu w świecie z fazą (R10) | 5–8 |
| **PK-W3** | **Punkt kontrolny 3: pory roku** | Strona ze zrzutami lasu wiosną (aspekt zawilca), latem, jesienią i zimą, liście i runo, z SS i bez | akceptacja albo lista poprawek | — | czekanie |
| **V10** | Szkielet fauny | `PolishAnimal` (Animal + GeoEntity + SmartBrainOwner: despawn > 128 bloków bez imienia i smyczy, FID, alarm, podsezon, wariant przez `DataTicket`), `AnimalProfile` (rekord: wymiary, FID, grupy, harmonogram, gęstość, sezon futra), `ModEntities`. Predykat spawnu (tag `polishforests:forest_animals_spawnable_on`, próg światła według V7, `MOTION_BLOCKING_NO_LEAVES`). `HabitatPopulation` w `spawnOriginalMobs` przed usunięciem `ChunkHabitats`. Spawny w datagenie biomów (F1: bez wilka i królika w lasach wycinka, F2: mnożnik). `ForestAlarm`. Lang EN i PL. Gametest `fauna` | gametest `fauna` w obu skalach i trybach: spawn w obu lasach, 0 zwierząt na koronach, despawn, MSPT < 2 ms przy 40 zwierzętach. `DeterminismClientGameTest` bez zmian (haszuje tylko bloki) | wysokie: rejestracja encji i spawny (B1), R-F1, R-F2 | 3–4 |
| **V11** | Sarna i dzik | Sarna: harmonogram (rano, południe, zmierzch), ucieczka z 20–40 m, szczek ostrzegawczy, grupy, futro i parostki według podsezonu. Dzik: nocny, wataha za lochą (`HerdLeader`), ucieczka gęsiego, barłóg. Buchtowanie według C9, agresja lochy według C6. Model i tekstury dzika z `tools/fauna`. Dźwięki CC0 lub CC BY, a do tego czasu dźwięki zastępcze wanilii | gametest: ucieczka w promieniu FID, lider prowadzi, alarm płoszy, zrzuty dzień i noc, lato i zima. JUnit harmonogramów i wyboru lidera | średnie | 2–3 |
| **V12** | Wiewiórka, żaba, dzięcioł | Wiewiórka: wspinanie po pniach V1 wszystkich promieni (własny ruch „przy pniu”, gdy `WallClimberNavigation` nie trzyma się cienkiej kolizji), skoki między koronami, ucieczka na drzewo, 2 odmiany barwne, zima. Skala ×2 według F3. Żaba trawna jako `frog_variant` (dane i tekstura). Dzięcioł jako dźwięk z wysokich pni w sezonie bębnienia | gametest: wspinanie na 3 grubościach pnia, wiewiórka nie utyka w liściach, wariant żaby w obu lasach i przy wodzie | średnie: wspinanie po cienkich pniach (R-F5) | 2–3 |
| V13 | (opcja, F4) Sójka | Lot w wolnej przestrzeni pod koronami, siadanie, skrzek jako źródło `ForestAlarm` | gametest: sójka nie lata nad koronami, alarm płoszy sarnę | średnie (R-F6) | 1,5–2 |
| V14 | (opcja, T-D5) Zima: gałązki | `ModelLoadingPlugin` owija model liści i w fazie bezlistnej emituje gałązki (FRAPI). Marcescencja dębu i buku jako brązowe liście | zrzuty zimy z Sodium i bez, MoreCulling bez artefaktów | średnie | 2–3 |
| V15 | (opcja, D1) Pełny zestaw sosny | Poziom FULL w `WoodSet` dla sosny: deski, schody, płyta, płot, furtka, drzwi, klapa, przycisk, płytka, tabliczki, półka, łodzie (encje, renderer, dozownik), receptury i advancementy. Tekstury ok. 10 | tabliczka, półka i łódź działają w grze. `checkDatagen` i `checkArt` czyste. Every Compat rozpoznaje parę `scots_pine_planks` + `scots_pine_log` | niskie (sprawdzony wzór wanilii) | 2–3 |
| **V16** | Integracja i domknięcie | Pełny A/B całego wycinka wobec bazy M2 (wszystkie obszary, obie skale, oba tryby, z C2ME i bez). Determinizm. Pełny zestaw testów, `checkDatagen`, `checkArt`, `runClientGameTest` (`slice`, `fauna`, `all` w częściach). Dokumentacja: `docs/04`, BRIEF, `docs/01` §5 §6 §8, `docs/00`, README. Lista „co zostaje do M3/M4/M6” | wszystkie testy PASS. Budżety W8 spełnione albo zgłoszone (B3). Strona końcowa ze zrzutami i liczbami | średnie | 2–3 |
| **PK-W4** | **Punkt kontrolny 4: wycinek** | Test w grze użytkownika: spacer przez grąd i bór w obu skalach, zwierzęta, pory roku, FPS z paczką | akceptacja wycinka, decyzja o M3 (kolejność gatunków, pełne zestawy D1) | — | czekanie |

**Suma [S]:**

| Zakres | Dni |
|---|---|
| podstawa V0–V12 i V16 | 35–54 |
| opcje V13–V15 | 5,5–8 |
| ścieżka krytyczna V0 → V1 → V3 → V5a → V5b → V6 → V7 → V8 → V16 | ok. 20–31 (bez czekania na punkty kontrolne) |
| z V9 zamiast V8 (najdłuższy krok poza drzewami) | ok. 23–36 |

Przy dwóch–trzech równoległych worktree (A5: grafika, fauna, runo) kalendarz skraca się do mniej więcej ścieżki krytycznej.

**Szacunek tekstur:** ok. 70 bloków i ok. 12 zwierząt. Rozkład: V2 8, V5b ok. 12, V8 ok. 12, V9 ok. 25, V11 i V12 ok. 8 (zwierzęta), V15 ok. 10.

---

## 5. Punkty kontrolne dla użytkownika

| PK | Kiedy | Co użytkownik ogląda | Co rozstrzyga |
|---|---|---|---|
| PK-W0 | przed V0 | ten plan | decyzje W1–W13, D1, D2, D5, F1–F6 (lista §6) |
| PK-W1 | po V1–V4 | tablica pni i liści w grze (z Sodium i bez), arkusz ok. 8 tekstur, liście w 4 porach roku, sarna z 4 stron | styl grafiki (D5, F6), licencja zasobów (F5), B6, technika pnia (T-D1, T-D2) |
| PK-W2 | po V6 | zrzuty pojedynczych drzew i obu lasów (gospodarczy i naturalny, obie skale, tryb D) oraz własny test w grze (`runClient -Poptimization`, `find biome`) | wygląd drzew i drzewostanu. Po akceptacji zamrażamy identyfikatory i układ stanów (F-3) |
| PK-W3 | po V9 | las w 4 podsezonach z SS i bez | fenologia, kolory gatunków, gęstość runa |
| PK-W4 | po V16 | test w grze: grąd, bór, zwierzęta, pory roku, FPS | akceptacja wycinka i start M3 |

Zrzuty idą jak w M2: strona-artefakt i katalog `docs/slice/` w repozytorium. Każdy zrzut ma współrzędne kamery, żeby użytkownik mógł stanąć w tym samym miejscu.

---

## 6. Decyzje dla użytkownika

### 6.1 Otwarte decyzje z `docs/00`, których dotyka wycinek

| # | Pytanie | Warianty | Rekomendacja |
|---|---|---|---|
| **D1** | Pełne zestawy drewna | (a) w wycinku żadnego; (b) FULL dla sosny, LITE dla grabu i lipy; (c) FULL dla wszystkich własnych gatunków wycinka. Docelowo (M3): FULL dla sosny, buka, jodły, olszy, grabu, lipy, jesionu, klonu, modrzewia i wierzby; LITE dla wiązu, jarzębu, cisu, czeremchy i dzikich drzew owocowych | **(b) w wycinku**: raz sprawdzony potok FULL (tabliczki, półka, łodzie) zdejmuje ryzyko z ok. 10 zestawów M3. Przejście z LITE na FULL tylko dodaje bloki, nie zmienia istniejących, więc nie psuje zapisów. Listę docelową potwierdzić przy M3 |
| **D2** | Dąb, brzoza, świerk, topola: bloki wanilii czy własne | (a) wszystkie z wanilii (własne są tylko cienki pień i blok liści z teksturą wanilii przez id); (b) jak (a), ale dąb dostaje własną korę (ciemna, bruzdowana) dla pnia, kłody i drewna, a deski, drzwi itd. zostają z wanilii; (c) wszystkie własne | **(b)**: kora wanilii jest dla dębu szypułkowego za jasna i za gładka [Ź fauna-grafika §4.4]. Brzoza, świerk i topola z wanilii pasują. Blok liści jest **zawsze własny** (faza i kolor gatunku), z teksturą wanilii przez id (EULA dopuszcza). Kłoda `polishforests:oak_log` daje w recepturze `minecraft:oak_planks` i należy do `#oak_logs` |
| **D5** | Tekstury i modele: kto, jak, licencja | (a) generator proceduralny `tools/art` i `tools/fauna` z iteracjami agenta, lintem i punktami kontrolnymi; (b) grafik lub zlecenie; (c) jak (a) plus ręczne poprawki użytkownika (Aseprite, Blockbench) w `overrides/`. Licencja zasobów: CC BY 4.0 / MIT / CC0 | **(a) z furtką (c)**. Katalog `overrides/` przechodzi lint, ale bez porównania bajtów. **Licencja CC BY 4.0** dla zasobów (plik `LICENSE-assets`, `CREDITS.md` z generatora), MIT dla kodu bez zmian. Dźwięki tylko na CC0, CC BY lub CC BY-SA |
| **B6** | Kolory liści | (a) własne krzywe gatunku na „surowym” kolorze listowia biomu (własny resolver, SS tylko jako wyłącznik i źródło fazy); (b) kolor biomu sezonowany przez SS × stały mnożnik gatunku; (c) statyczne bloki jesienne jak topola 26.3 | **(a)**. Raporty wycinka różnią się tu zdaniem: technika drzew zaleca (a), fauna-grafika (b). (a) daje różny termin przebarwienia gatunków (brzoza i lipa od końca IX, dąb późno, olsza zielona do opadu) [Ź 08 §9], działa bez SS i nie sezonuje drugi raz. Koszt jak (b) plus krzywe w danych |
| B5 | Typy zim | — | wycinek nie dotyka. W wycinku „zima normalna” |
| C2 | Hitboxy | — | gatunki wycinka ≤ 1,1 bloku, więc problemu nie ma. Decyzja zostaje przy łosiu i jeleniu (M6) |
| C3 | Stan populacji | (a) 2025: dzik rzadszy po ASF; (b) klasyczny | **(a)**: dzik rzadszy niż sarna (waga w `AnimalProfile`) |
| C5 | Ptaki jako encje | — | w wycinku tylko sójka (F4) i dzięcioł jako dźwięk. Decyzja zostaje do M7 |
| C6 | Agresja | (a) brak; (b) locha z warchlakami (III–VI): ostrzeżenie, potem szarża z małymi obrażeniami; (c) pełna | **(b)**, z regułą gry do wyłączenia |
| C8 | Dropy | (a) brak w wycinku; (b) wanilijne mięso i skóra | **(a)**, zgodnie z C12 |
| C9 | Zmiana terenu przez zwierzęta | (a) buchtowanie dzika z regułą gry, domyślnie włączone; (b) bez | **(a)**: tanie (+2 h) i rozpoznawalne. Nie zmienia determinizmu generacji, bo działa po generacji |
| C10 | Dorastanie | (a) młode tylko w grupach spawnu, dorastanie jak w wanilii do M6; (b) realistyczne | **(a)** |
| C11 | Kleszcze i komary | — | poza wycinkiem |

### 6.2 Nowe decyzje wycinka

| # | Pytanie | Warianty | Rekomendacja |
|---|---|---|---|
| **W1** | Zasięg wycinka w biomach | (a) tylko grąd i bór świeży; (b) jak (a) plus `mixed_pine_forest` i `mixed_forest` z nowymi drzewami (palety, bez nowego runa i fauny); (c) wszystkie biomy z gatunkami wycinka | **(b)**: naturalny ciąg Bśw → BMśw → LMśw → grąd [Ź ekologia §8]. To też test, że system skaluje się samymi danymi (F-2), przy koszcie ok. 0,5–1 dnia w V6. W pozostałych ekotonach zostają zastępstwa wanilii, co jest znane i opisane. Buk, klon i jesion dostają w palecie wycinka wspólny archetyp liściasty z teksturą zastępczą |
| **W2** | Jak włączyć nowe drzewa | (a) podmienić `tree/<gatunek>` globalnie we wszystkich biomach; (b) feature proceduralny pod docelowym kluczem `tree/<gatunek>`, zastępstwa przeniesione pod `tree_substitute/<gatunek>` dla biomów spoza wycinka; (c) osobne klucze `tree_m3/…`, a w M3 przemianowanie | **(b)**: reszta świata zostaje stabilna jako tło porównań i pomiarów, a M3 zmienia tylko palety (F-4). Klucze worldgen nie trafiają do zapisu chunków |
| **W3** | Tryb N a las gospodarczy (D9) | (a) tryb N = 100% las naturalny, tryb D = suwak D9 (domyślnie 85/15); (b) suwak w obu trybach; (c) jak (a), ale w trybie N dodatkowo pewien udział starodrzewu | **(a)**: tryb N to roślinność potencjalna („Polska bez ludzi”, A5), więc las gospodarczy nie ma w nim sensu. Wymaga potwierdzenia, bo suwak D9 jest dziś w ustawieniach niezależnie od trybu |
| **W4** | Granice wydzieleń w lesie gospodarczym a Z10 | (a) granice nieregularne (Woronoj z meandrami) i rampa wieku 3–6 bloków·k; (b) granice proste jak w prawdziwym lesie (wyjątek od Z10); (c) proste tylko wzdłuż linii oddziałowych w M8, poza nimi jak (a) | **(a) teraz, (c) w M8**. Z10 zakazuje prostych linii, a jedyne prawdziwie proste granice w polskim lesie to linie oddziałowe i drogi, które przyjdą z infrastrukturą |
| **W5** | Rozmiar drzew w skali rozgrywki | (a) 1:1 jak w REAL (A7), gęstość na blok taka sama; (b) wysokości ×0,75 | **(a)**. W GAMEPLAY świat sięga Y 767, a teren jest niższy, więc 30–40 bloków jest w porządku. Odległości planerów (luki, wydzielenia, ekotony) skalujemy ×k (Z7) |
| **W6** (T-D1, T-D2) | Technika i klasy grubości pni | technika: D (promień + rodzic, JSON z wysięgiem) / C (model dynamiczny FRAPI) / B (6 boolean); klasy: co 2 px (`radius` 1–7, 84 stany) / co 1 px (168 stanów) / 4 klasy | **D, co 2 px**, C jako zapas. Układ zamrażamy po PK-W2 (F-3), bo późniejsza zmiana zakresu `radius` zmieniłaby znaczenie zapisanych stanów |
| **W7** (T-D3) | Generator | parametryczny / L-system / kolonizacja przestrzeni / szablony NBT | **parametryczny**, w koronach liściastych opcjonalnie kolonizacja przestrzeni, szablony NBT (`minecraft:template`) tylko dla drzew pomnikowych i wykrotów (po wycinku) |
| **W8** (T-D7) | Budżety po drzewach 1:1 | (a) cały chunk ≤ 48 ms zostaje twardy, a FEATURES i LIGHT dostają nowe budżety z pomiaru V7 (wobec bazy M2 przy tym samym pokryciu lasem); (b) stare budżety M2-14 bez zmian (niewykonalne przy 2× liści); (c) tylko cały chunk | **(a)**. Propozycja [S]: FEATURES ≤ 2,5 × baza M2, LIGHT ≤ 1,6 ×, cały chunk ≤ 48 ms, chunki na sekundę z C2ME ≥ 0,8 × baza M2, FPS w przelocie z Sodium ≥ 0,8 × baza, fauna < 2 ms na tick przy 40 zwierzętach. Liczby do korekty po V7 |
| **W9** | Fenologia runa | (a) stany bloków i `PhenologySweep` serwera ze stopniowym przejściem i nadrabianiem; (b) tylko losowe ticki (zasięg symulacji 6–8 chunków, dalej stany nieaktualne); (c) w wycinku tylko aspekt letni | **(a)**. Aspekt wiosenny to wizytówka grądu, a silnik i tak jest potrzebny w M4. Cena: seria pakietów bloków przy zmianie fazy, rozłożona w czasie przesunięciem z hasza kolumny |
| **W10** | Mrowiska i grzyby w wycinku | (a) mrowisko tak (ok. 1 na 300 chunków [Ź]), grzyby nie; (b) oba; (c) żadne | **(a)**. Grzybobranie (D4) to osobny system M4 |
| **W11** | Zamrożenie identyfikatorów | (a) po PK-W2, z testem migawki rejestru; (b) dopiero po wydaniu publicznym | **(a)**. Użytkownik testuje w zapisanych światach |
| **W12** | Kolejność względem M2 | (a) wycinek po S8c i akceptacji M2-PK1 i M2-PK2; (b) V0–V5 równolegle z S8c (inne pliki), PK M2 przed V6 | **(b)** |
| **W13** | Gęstość grądu naturalnego | (a) liczby z BPN: ok. 15 pni na chunk ze wszystkimi piętrami [Ź]; (b) obecne 9 na chunk | **(a)**, z przycięciem koron. Koszt sprawdza V7; zapas to rzadsze a2 |
| F1 | Wanilijne zwierzęta w lasach wycinka | (a) zostają; (b) bez wilka i królika, lis zostaje; (c) bez żadnych | **(b)** |
| F2 | Gęstość fauny | (a) realna (prawie niewidoczna); (b) ×20–50 przez wagi i limity wanilii; (c) własny spawner (M6) | **(b)** w wycinku, (c) w M6 |
| F3 | Skala małych zwierząt | (a) 1:1; (b) poniżej 30 cm ×2, żaba jak w wanilii | **(b)** |
| F4 | Zestaw gatunków | (a) sarna, dzik, wiewiórka, sójka, żaba, dzięcioł; (b) sarna, dzik, wiewiórka, żaba, dzięcioł (sójka jako opcja V13); (c) minimum: sarna, dzik, żaba, dzięcioł | **(b)**. Wiewiórka sprawdza styk fauny z nowymi pniami (wspinanie), czyli ryzyko między warstwami. Lot ptaków to osobny archetyp M7 |
| F5 | Licencja zasobów | jak D5 | CC BY 4.0 |
| F6 | Punkt kontrolny grafiki | (a) wcześnie (PK-W1); (b) na końcu | **(a)** |

---

## 7. Ryzyka

| # | Ryzyko | Skutek | Zabezpieczenie | Krok |
|---|---|---|---|---|
| R1 | LIGHT i FEATURES przy koronach 1:1 | budżety przekroczone | pomiar V7 przed treścią, puste środki koron, `propagatesSkylightDown` dla gatunków światłożądnych, nowe budżety (W8) | V7 |
| R2 | FPS przez quady liści (Fancy: 6–9 tys. na kolumnę chunka) | gorsza płynność | MoreCulling (liście jako `LeavesBlock`), gęstość skupień, pomiar przelotu | V7 |
| R3 | Wysięg modelu pnia poza blok przy Sodium, AO i na granicach sekcji | szczeliny, migotanie | tablica próbek w V1 jako pierwszy test, zapas: wariant C (FRAPI) | V1 |
| R4 | Schodki ukośnych konarów | brzydkie grube gałęzie | konary blisko osi, ocena na PNG i w PK-W2 | V5 |
| R5 | Determinizm na granicach chunków (korony, kłody, runo pod koronami) | różnice zależne od kolejności | przycięcie Woronoja z czystego wzoru kandydatów, zapis tylko w 3 × 3, test determinizmu w każdym kroku drzew | V5–V8 |
| R6 | Okno zapisu 3 × 3 chunki | ucięte korony i długie kłody | promień korony ≤ 16, kierunek kłody w stronę wnętrza okna, szablony dla olbrzymów | V5, V8 |
| R7 | Korony nad dachami struktur | liście w wioskach | `BUILDING_GAP` z promienia korony, maska struktur z `ChunkHabitats`, test wiosek z S8 | V6 |
| R8 | Huby i mech wanilii obok cienkich pni | „wiszące” dekoracje | dekoratory tylko na pełnych kłodach, własne huby w M4 | V8 |
| R9 | Zacięcie przy przebudowie siatek (zmiana koloru) | skok czasu klatki | 2–3 kroki na podsezon, przebudowa falą od kamery jako zapas | V3 |
| R10 | Seria pakietów i zaległe stany przy fenologii | skok MSPT, stany niezgodne z porą roku | budżet ticku, przesunięcie z hasza, stempel chunka, test MSPT | V9 |
| R11 | Zapisane światy po zmianie bloków | utrata bloków albo złe stany | zamrożenie po PK-W2 (F-3, W11), test migawki rejestru | V1, PK-W2 |
| R12 | Mieszanie nowych drzew z zastępstwami wanilii w ekotonach do biomów spoza wycinka | kontrast skali przy granicy | W1 b (ciąg Bśw–BMśw–LMśw–grąd w całości), opis w dokumentacji; M3 to usuwa | V6 |
| R13 | Estetyka grafiki agenta | odrzucenie stylu po pracy | PK-W1 po 8 teksturach, `overrides/` dla ręcznych poprawek | V2 |
| R14 | Konwencja osi i obrotów GeckoLib 5 | „połamane” modele | kalibracja osi w grze w V4 | V4 |
| R15 | Wanilijny predykat spawnu (`grass_block`, światło > 8) | 0 saren w borze na `podzol` i pod okapem | własny tag i predykat, próg światła z pomiaru V7 | V10 |
| R16 | Limit `CREATURE` (10) i trwałość | pustka albo kumulacja przy eksploracji 1:1 | małe zwierzęta w `AMBIENT`, despawn > 128 bloków, mnożnik gęstości w jednym miejscu | V10 |
| R17 | Wspinanie po cienkich pniach | wiewiórka spada lub utyka | test na 3 grubościach, własny ruch przy pniu | V12 |
| R18 | Rozrost zakresu (M3 i M4 „przy okazji”) | wycinek się nie kończy | lista §1.2, punkty kontrolne z jawnym „poza zakresem”, B3 po 2 rundach | wszystkie |
| R19 | Czas gametestów (`all` > 7000 s) | długie przebiegi | osobne grupy `slice` i `fauna`, `all` w częściach jak w S10 | V0 |
| R20 | Pomiary na zajętej maszynie | fałszywe stosunki | A/B w jednej sesji, `-PtimingsReportOnly`, powtórzenie na spokojnej maszynie | V7, V16 |
| R21 | Zależność `checkArt` od Pythona | build nie działa na innej maszynie | `checkArt` poza `build`, czytelny komunikat, opis w BRIEF | V2 |
| R22 | Aktualizacje GeckoLib, SBL, SS, Fabric API w łatkach 26.3 i 26.4 | rekompilacja | wersje przypięte w `gradle.properties`, aktualizacja świadoma | wszystkie |
| R23 | Licencje dźwięków (NC na xeno-canto) | nie wolno dołączyć | filtr licencji w manifeście, do tego czasu dźwięki zastępcze wanilii | V11–V12 |
| R24 | Niepewne szacunki [S] (koszt planera, liczba liści, dni pracy) | przesunięcie terminów | pomiary w V5 i V7 przed treścią, plan skrócenia: biblioteka gotowych szkieletów na gatunek i klasę wieku | V5, V7 |

---

## 8. Kolejność i równoległość (podsumowanie)

```
PK-W0 → V0 ─┬─ V1 ── V3 ─┬─ PK-W1 ── V5a ── V5b ── V6 ── PK-W2 ── V7 ─┬─ V8 ────────────┬─ V16 ── PK-W4
            ├─ V2 ───────┤                                             ├─ V9 ── PK-W3 ───┤
            └────────────┴─ V4 (od V1)                                 └─ V10 ── V11 ── V12 ┘
                                         (S8c równolegle w osobnym worktree)
opcje: V13 po V12, V14 po V3/PK-W3, V15 po V1 (w dowolnym oknie ścieżki B)
```

Pierwszy tydzień daje odpowiedzi na trzy pytania o największym ryzyku:
- czy wariant D pnia wygląda dobrze z Sodium (V1);
- czy styl tekstur przechodzi (V2, PK-W1);
- czy kolor sezonowy działa z SS i bez niego (V3).

Dopiero potem zaczyna się najdroższa praca, czyli generator.
