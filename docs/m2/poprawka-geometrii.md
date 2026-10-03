# Poprawka geometrii terenu (proste krawędzie den dolin, starorzeczy i jezior; M2-8)

Dokument wdrożenia poprawki geometrii terenu. Opisuje kroki K0–K7, narzędzia kontrolne i odstępstwa od projektu. Projekt i analizy (dna dolin, starorzecza i jeziora, szczyty, przegląd artefaktów) powstały poza repozytorium; tu zapisujemy to, co weszło do kodu, i wyniki pomiarów.

## Kroki

| Krok | Co | Teren |
|---|---|---|
| K0 | Narzędzia: dwa pliki wzorcowe złotego testu, lista dozwolonych zmian, raport zmian, zapis z zachowaniem środków łat, test ciągłości powierzchni, test lokalności, test determinizmu | bez zmian |
| K1 | Strefy dna od koryta doliny dominującej (F2) | bez zmian |
| K2 | Wielkie masywy Beskidów (M2-8), miękki pułap wierzchołka, reguły rzek przy masywach | tylko masywy |
| K3 | Ciągłe pola doliny dominującej (F1), bez uskoku przy progu nizin 0,3 (TE) | doliny |
| K4 | Geometria dolin (G1B, R1, G2–G5, A5) i optymalizacje; K4b: urwiska na stokach gór GAMEPLAY (D4) | doliny i zbocza |
| K5 | Wody stojące: starorzecza, jeziora rynnowe (z poziomem niezależnym od kolejności próbkowania), oczka, niecki, brzeg jezior bezodpływowych; K5b: wybrzeże wydmowe (D5) | wody stojące, wybrzeże |
| K6 | Okno mieszania regionów 5 × 5 (A16) | szwy regionów |
| K7 | Jedno przegenerowanie `golden_terrain_m2.txt`, dokumentacja | — |

Zalew bez rowu (D2) i wały moren W–E (D3) wchodzą razem z krokami terenu, jeśli przejdą prototyp.

## K0. Narzędzia (teren bez zmian)

Stan bazowy: commit `6046d53`. Teren po K0 jest identyczny z M1: oba testy złote przechodzą, `TerrainLocalityTest` nie znajduje żadnej zmienionej kolumny.

### Złoty test: dwa pliki wzorcowe

- `src/test/resources/golden_terrain_m1.txt` zostaje bez zmian na zawsze. Czyta go tylko `frozenM1CopyMatchesGolden`, który pilnuje, że zamrożona kopia M1 (`landscape/m1/`) daje skróty M1.
- `src/test/resources/golden_terrain_m2.txt` (nowy) czyta `terrainMatchesGolden` (dawniej `terrainMatchesM1`). Gdy pliku nie ma, test porównuje z plikiem M1.
- Plik M2 powstał w K0 zapisem z zachowaniem środków łat na niezmienionym terenie. Wiersze `patch` wszystkich łat M1 są identyczne z plikiem M1 (sprawdzone `diff`), doszły dwie łaty kontrolne w każdym zestawie. Zmieniły się tylko wiersze `field` i `total`, bo obejmują nowe łaty.
- `diagnose` porównuje kopię M1 tylko z plikiem M1 (łaty o tej samej nazwie, rozmiarze, środku i kroku). Ostrzeżenie „JVM lub procesor” pojawia się więc tylko wtedy, gdy kopia M1 nie zgadza się z plikiem M1, a nie po każdej zamierzonej zmianie terenu.
- Werdykt „tylko różnica numeryczna, zaokrąglenie skrótu” `diagnose` podaje tylko wtedy, gdy kolumny są równe kopii M1, a bieżący plik oczekuje dla tej łaty skrótu M1 (ta sama łata w pliku M1, ten sam skrót). Gdy łata wróciła do stanu M1, a plik oczekuje innego skrótu (np. po K7), pisze: „identyczna z kopią M1, ale bieżący plik oczekuje innego skrótu”.

**Łaty kontrolne** (16 × 16 co 4 m, w obu skalach):

| Zestaw | `outwash_plain_interior` | zapas | `moraine_plateau_interior` | zapas |
|---|---|---|---|---|
| REAL A | (−68929, −22477) | 3,00 / 1036 m | (−64697, 20136) | 2,00 / 638 m |
| REAL B | (−6258, −15891) | 3,00 / 1096 m | (73265, −14107) | 2,00 / 890 m |
| GAMEPLAY A | (4893, −776) | 1,25 / 288 m | (7818, −2738) | 1,00 / 198 m |
| GAMEPLAY B | (15126, 4826) | 1,00 / 242 m | (−4922, 4677) | 1,25 / 362 m |
| REAL A 0,5 | (−14970, −139862) | 2,00 / 988 m | (−19435, −60059) | 2,00 / 930 m |

Zapas: miara wyboru (niżej) oraz odległość od środka łaty do najbliższej kolumny wciętej, w dnie, z wodą, w pasie wody stojącej, bliżej koryta niż skraj dna albo w pasie wybrzeża (skan biegunowy, 720 kierunków, krok 2 m).

**Cel łaty.** Każda kolumna ma wagę typu ≥ 0,95 i jest „nietknięta”:

- bez wody i bez poziomu wody, poza pasem wody stojącej, poza dnem doliny, niewcięta (`surface == rawSurface`);
- najbliższe koryto co najmniej 300 m·k za skrajem dna: `!(channelDist − floorHalfWidth < 300·k)`; NaN i +∞ (brak cieku) przechodzą;
- co najmniej 300 m·k za pasem kształtowania wybrzeża: `!(coastD < 25 000·meso + 300·k)` (pas `shapeCoast` ma 25 km·meso, w GAMEPLAY 3750 m).

To samo (bez wagi typu) sprawdzamy w 108 punktach: 3 okręgi (1/3, 2/3 i 3/3 z 300 m·k za narożnikiem łaty) po 36 kierunków. k to skala lokalna, czyli `valleyScale` sieci rzecznej: 300 m w REAL, 150 m w GAMEPLAY. Okręgi próbkują zapas; między punktami (odstępy 30–90 m·k) wąska głowica doliny mogłaby podejść bliżej.

**Dlaczego nie `streamOrder == 0`** (projekt). `streamOrder` jest 0 tylko w dziurach między ramkami odrzucania odcinków sieci rzecznej (prostokąty o prostych krawędziach, np. dziura ok. 850 m × 6 km wokół dawnej łaty sandru REAL A). Kroki K4 celowo powiększają ramki (G1B, G4, K4.9), więc łata straciłaby cel bez zmiany terenu, a w K7 zostałaby wyszukana od nowa. Warunek na `channelDist − floorHalfWidth` nie zależy od ramek: każda ramka sięga daleko za skraj dna plus 300 m·k (zasięg ramki zawiera `maxWall` = 1200 m·k). Dzięki temu łaty REAL leżą teraz 0,5–3,5 km od punktów startowych, a nie do 130 km.

**Wybór środka.** Wyszukiwanie spiralne nie bierze pierwszego dobrego punktu (zwykle granicznego), tylko najlepszy z pierwszych 16 kandydatów spełniających cel, odległych od siebie o co najmniej 600 m·k. Miara: promień za narożnikiem łaty (w jednostkach 300 m·k, najwyżej 3), do którego wszystkie punkty okręgów co 1/4 z 300 m·k (36 kierunków) są nietknięte przy podwójnym zapasie (600 m·k od skraju dna i od pasa wybrzeża).

**Sprawdzenie na prototypie całej poprawki.** Przed zapisaniem pliku M2 wszystkie 10 łat kontrolnych policzyłem na łącznym prototypie `PW/src3` z pełnym zestawem przełączników (`-Ddno.fix=G1B,R1,G2,G3,G4,TE,F1,F2,G5,A5,A16,A3`) i porównałem z kopią M1 w tej samej JVM: 0 różnych kolumn w każdej łacie, 0 różnych punktów na okręgach, cel spełniony. Dawna łata GAMEPLAY A `outwash_plain_interior` (−132, −5985) na prototypie traciła cel (przez zmiany dolin, nie przez A16). Wśród 96 kandydatów GAMEPLAY (oba ziarna, oba typy) cel traciło na prototypie 13 z 41 kandydatów z zapasem 0, 4 z 41 z zapasem 0,25–0,75 i 2 z 14 z zapasem ≥ 1; wybrane łaty sprawdziłem pojedynczo. Prototyp nie zawiera D2, D3, D4 i D5 (masywy K2 i urwiska D4 dotyczą tylko gór); przed D2 i D5 chroni pas wybrzeża w celu łaty, a D3 może zmienić `moraine_plateau_interior` (wyjątek niżej).

Łaty kontrolne nie mogą się zmienić na żadnym kroku. Wyjątek: `moraine_plateau_interior` przy D3 (wały moren). D5 (K5b) musi zostać w pasie `shapeCoast`, inaczej zmieni łaty GAMEPLAY A i B wysoczyzny (ok. 5,3 i 7,8 km od brzegu).

### Właściwości Gradle

```
./gradlew test --tests '*GoldenTerrainTest*'                                     # porównanie z plikiem M2
./gradlew test --tests '*GoldenTerrainTest*' -PgoldenReport=build/golden_K3.txt  # raport zmian
./gradlew test --tests '*GoldenTerrainTest*' -PgoldenAllow=src/test/golden-allow/K3.txt
./gradlew test --tests '*GoldenTerrainTest*' -PwriteGolden -PgoldenKeepCenters -PgoldenFile=build/golden_terrain_m2.txt
```

- `-PwriteGolden` pisze tylko plik M2: do `-PgoldenFile=<plik>`, domyślnie `src/test/resources/golden_terrain_m2.txt`. Plik M1 nie jest już nigdy nadpisywany.
- `-PgoldenKeepCenters` (z `-PwriteGolden`): środki łat z bieżącego pliku wzorcowego. Od nowa szukane są tylko łaty, których cel w starym środku nie jest spełniony (sprawdza świeży model), i łaty nowe (miejsca bez wpisu w pliku). Kolejność łat w pliku: siatka, potem kolejność `SITES`.
- `-PgoldenAllow=<plik>`: lista dozwolonych zmian. `terrainMatchesGolden` pada tylko przy zmianie łaty spoza listy albo przy utracie celu spoza listy. Dozwolone zmiany wypisuje jako informację.
- `-PgoldenReport=<plik>`: lista zmienionych łat w składni listy dozwolonych zmian, a zmienione pola, porównanie z kopią M1 i opisy utraconych celów jako komentarze. Raport powstaje także wtedy, gdy test przechodzi (`# no changes`).

**Składnia listy dozwolonych zmian** (`#` zaczyna komentarz, więc raport może służyć za listę):

```
REAL A / grid                 # skrót łaty może się zmienić
GAMEPLAY A / oxbow_lake target  # skrót może się zmienić i łata może stracić cel
* / grid                      # we wszystkich zestawach
GAMEPLAY A / coverage         # zestaw może stracić rodzaj wody, typ lub podłoże
```

Zestawy: `REAL A`, `REAL B`, `GAMEPLAY A`, `GAMEPLAY B`, `REAL A 0.5` (A = ziarno 20260927, B = −7316550294015845337), pełny klucz z pliku (`realistic 20260927 1.0`) albo `*`. Nieznany zestaw, łata lub przyrostek kończy test błędem, żeby literówka nie dopuszczała po cichu niczego. Listy kroków leżą w `src/test/golden-allow/K3.txt` … `K6.txt` i znikają w K7.

### `SurfaceContinuityTest`: ciągłość powierzchni

W każdym oknie 150 losowych transektów po 1000 m z krokiem 0,5 m (ziarno losowania 11, świat 20260927, suwak 1,0). Skok to |Δh| > 0,3 m między sąsiednimi próbkami i ponad 3 razy więcej niż różnica w obu sąsiednich krokach, przy czterech suchych próbkach. Test wypisuje każdy skok z otoczeniem: wodą stojącą, gdy którakolwiek z czterech próbek leży w jej pasie (do 160 m od brzegu; ściana niecki kończy się na skraju pasu, więc sama próbka przy skoku bywa już poza nim), wybrzeżem (do 3 km·meso od linii brzegu), doliną rzędu n (dno lub zbocze) albo typem krajobrazu.

Progi w K0 to stan zmierzony na niezmienionym terenie (2026-10-03): test przechodzi i zatrzymuje każde pogorszenie. Każdy krok zaostrza progi; cel podaje ostatnia kolumna. We wszystkich oknach obowiązuje docelowo decyzja D4 (krok K4b): żadnego skoku > 3 m, jak w `mountainStreamSourcesHaveNoCliffs`.

| Okno | Skala, środek, promień | Skoki | Największy | Przy wodzie stojącej | Cel (oprócz D4) |
|---|---|---|---|---|---|
| `gameplay_beskids` | GAMEPLAY (27609, 3254), 3 km | 43 (42 zbocza) | 36,4 m | 0 | ≤ 8, żaden przy wodzie |
| `gameplay_center` | GAMEPLAY (0, 0), 10 km | 21 | 20,3 m | 14 | ≤ 3 (≤ 1 po A3c), przy wodzie tylko (5761, −4758) |
| `gameplay_stream` | GAMEPLAY (3890, 1959), 2 km | 43 | 13,6 m | 0 | ≤ 5 |
| `gameplay_moraine` | GAMEPLAY (−1074, −2368), 3 km | 20 | 67,3 m | 16 | ≤ 1, 0 przy wodzie |
| `realistic_beskids` | REAL (154834, 1058738), 30 km | 1 | 1,06 m | 0 | ≤ 1, ≤ 1,1 m |
| `realistic_lowland` | REAL (0, 0), 100 km | 2 | 1,50 m | 2 | 0 |
| `realistic_moraine` | REAL (−66495, 21873), 20 km | 15–16 | 4,80 m | 15–16 | 0 |
| `realistic_stream` | REAL (72208, 1009510), 10 km | 0 | — | 0 | 0 |
| `gameplay_massif_spawn` | GAMEPLAY (6854, −33757), 2 km | 46 | 22,3 m | 0 | po K2 ≤ bez masywu + 2 |
| `gameplay_massif_1660` | GAMEPLAY (70230, −30250), 2 km | 54 | 68,1 m | 0 | po K2 ≤ bez masywu + 2 |
| `gameplay_massif_1718` | GAMEPLAY (−217490, 246246), 2 km | 59 | 28,5 m | 0 | po K2 ≤ bez masywu + 2 |
| `gameplay_massif_1710` | GAMEPLAY (−41536, 183081), 2 km | 51 | 39,3 m | 0 | po K2 ≤ bez masywu + 2 |
| `realistic_massif_1723` | REAL (147582, −1525292), 8 km | 0 | — | 0 | 0 |

Uwagi do okien:

- **`gameplay_moraine`.** To nie tylko torfy i oczka. Największy skok, −67,3 m w (−257,6; −3771,2), to prosto ucięty koniec jeziora rynnowego z wałem na poziomie lustra + 1 m (127 m) nad doliną wciętą do 59,7 m. Liczy się jako skok przy wodzie stojącej (sąsiednia próbka leży w pasie jeziora rynnowego). Usunie go dopiero K5.2 (koniec jeziora przy dolinie) i A3.
- **`realistic_moraine`.** Wynik zależy od przebiegu: 15 albo 16 skoków, bo poziom jeziora rynnowego w (−82818, 10254) zależy od kolejności próbkowania (niżej, „Determinizm”). Zwykle jest 15 (jeden skok −2,51 m), czasem 16 (−2,18 m i +1,82 m w tym samym miejscu). Największy skok, 4,80 m w (−82818,8; 10260,6), leży przy tym samym jeziorze. Do K5.2 próg wysokości to 6,9 m (4,80 m + największa zaobserwowana różnica poziomu 2 m), a progi liczby skoków obejmują oba warianty (16 / 16).
- Okna masywów nie mają jeszcze masywu (dochodzi w K2). Po K2 w tych oknach obowiązuje „nie więcej skoków niż bez masywu + 2”, a docelowo D4 (K4b). Pośrednie progi wariantu D4 (a) z projektu („≤ 19, max ≤ 20 m”) są nieaktualne, bo D4 wchodzi teraz.

Cały test trwa ok. 10 s.

### `TerrainLocalityTest`: lokalność zmian

Siatki 200 × 200, bieżący model porównany z kopią M1 w tej samej JVM. Kolumna, która w żadnym z modeli nie jest wcięta (powierzchnia równa `landElevation`) ani nie ma wody lub poziomu wody, musi mieć tę samą powierzchnię (do 1e-6 m). W praktyce test pilnuje więc tylko `landElevation`, czyli terenu przed dolinami i jeziorami, poza wcięciami i wodą. Nie widzi zmian w dolinach i nieckach (to cel poprawki, pilnują ich łaty złote i test ciągłości) ani nowych wcięć i nowej wody poza dolinami (takie kolumny są pomijane); test wypisuje ich udział, więc skok udziału pominiętych kolumn widać w wyniku. Porównanie poziomu wody było martwe (kolumny z wodą są pomijane), więc je usunąłem.

Wyjątki dopisują kroki, które celowo zmieniają teren poza dolinami (metoda `exempt`): zasięg wielkiego masywu (K2), szew okna 5 × 5 (K6), pas wybrzeża (D2 i D5, pas `shapeCoast`), wysoczyzny (D3). Test pada też wtedy, gdy porównano mniej niż 1000 kolumn.

| Siatka | Środek, krok | Pominięte (wcięte lub z wodą) | Porównane | Po co |
|---|---|---|---|---|
| `realistic_zero` | REAL (0, 0), 97 m | 36,1% | 25562 | ogólnie |
| `gameplay_zero` | GAMEPLAY (0, 0), 23 m | 70,0% | 11986 | ogólnie |
| `realistic_outwash_plain` | REAL (−68929, −22477), 37 m | 13,6% | 34561 | łata kontrolna |
| `realistic_moraine_plateau` | REAL (−64697, 20136), 37 m | 11,8% | 35293 | łata kontrolna |
| `gameplay_outwash_plain` | GAMEPLAY (4893, −776), 7 m | 43,4% | 22657 | łata kontrolna |
| `gameplay_moraine_plateau` | GAMEPLAY (7818, −2738), 7 m | 27,4% | 29026 | łata kontrolna |
| `gameplay_massif_spawn` | GAMEPLAY (6854, −33757), 40 m | 97,2% | 1119 | K2: masyw tylko w swoim zasięgu |
| `realistic_massif_1723` | REAL (147582, −1525292), 100 m | 37,1% | 25155 | K2 |
| `gameplay_lagoon` | GAMEPLAY (345, 10394), 15 m | 74,2% | 10337 | D2, D5 |
| `realistic_lagoon` | REAL (−223761, −151100), 60 m | 58,1% | 16745 | D2, D5 |

Wynik K0: 0 zmienionych kolumn na wszystkich siatkach. Siatki masywów i zalewów doszły już w K0 (recenzja), żeby wyjątki K2, D2 i D5 nie przechodziły trywialnie. W górach GAMEPLAY 97% kolumn leży w zboczach dolin, więc siatka masywu GAMEPLAY ma 8 km boku i porównuje ok. 1100 kolumn grzbietowych. Jeśli w K2 wyjątek masywu zbije tę liczbę poniżej 1000, trzeba powiększyć siatkę, a nie obniżać próg.

### `TerrainDeterminismTest`: determinizm

Każdy kadr liczą dwa świeże modele: wierszami równolegle od góry i jednym wątkiem od prawego dolnego rogu wstecz. Wszystkie pola M1 próbki muszą być identyczne. Kadry: jeziora rynnowe REAL (−80695, 10173) 6 km i zbliżenie (−82818, 10254) 600 m, wysoczyzna REAL (−66495, 21873) 20 km, GAMEPLAY środek 10 km, morena 3 km i Beskidy 3 km. Test trwa ok. 15 s.

**Znany błąd (z M1, kopia `landscape.m1` zachowuje się tak samo).** Poziom jeziora rynnowego jest zapamiętywany w `lakeLevels` pod kluczem `(anchor, k)`, ale liczony (`lakeLevel(key, ax, az, …)`) z punktu `(ax, az)`, w którym kończy się iteracja szukająca osi rynny. Iteracja startuje od `x` pytającej kolumny i ma 3 kroki zewnętrzne bez sprawdzania zbieżności. Tam, gdzie nie zbiega (rynny biegnące W–E, pasy N–S A1), punkt zależy od kolumny, a poziom ustala ta kolumna, która policzy jezioro pierwsza. Poziom wody, a z nim niecka, zależy więc od kolejności generowania chunków (do 2 m), a przy C2ME świat nie jest powtarzalny między sesjami.

Pomiar K0 (kolumny różne między kolejnościami): kadr pasów 6 km: 2531 ze 160000 (400 × 400 co 15 m), max 1,0 m; zbliżenie 600 m: 12143 z 90000, max 1,0 m. Pozostałe kadry: 0. Wszystkie różne kolumny leżą przy jeziorze rynnowym (`standingWaterKind == TUNNEL_VALLEY_LAKE` w którejś próbce). Liczby same się zmieniają między przebiegami (recenzent: 13172 i 108320 kolumn na kadrach 800 × 800).

Naprawa zmienia teren, więc nie mieści się w K0 (K0–K2 nie mogą zmienić terenu). Wchodzi w **K5.2**, który i tak przebudowuje `tunnelLakeAt`: poziom i środek jeziora liczone z punktu kanonicznego, zależnego tylko od klucza `(anchor, k)` (iteracja startująca od `anchor·200·local` i środka sekcji `k`), a nie od `x` kolumny. Zmiana dotknie łat z jeziorami rynnowymi, więc trzeba ją wpisać na listę dozwolonych zmian K5 (`tunnel_valley_lake` jest tam już przez A3). Do tego czasu stała `TUNNEL_LAKE_LEVEL_ORDER_DEPENDENT = true` dopuszcza różnice tylko przy jeziorach rynnowych, a każda inna różnica kończy test błędem. K5.2 ustawia ją na `false`; wtedy wymagane jest 0 różnic, a progi `realistic_moraine` w teście ciągłości trzeba ustawić na jeden stały wynik.

Złoty test liczy łaty równolegle i dotąd zawsze dawał te same skróty. Sprawdziłem to też wprost: wszystkie łaty pliku M2 (5 zestawów, po 3 powtórzenia) liczone równolegle i jednym wątkiem wstecz dają 0 różnych kolumn, więc łaty `tunnel_valley_lake` leżą przy jeziorach, przy których iteracja zbiega.

### `WatersideZonesTest`: większa próba olsu (stan bazowy)

Cięciwy olsu (bez pierścieni wód stojących) liczymy teraz na 600 przekrojach na klasę zamiast 150. Pierwsze 150 przekrojów każdej klasy to ten sam zestaw co wcześniej (ta sama kolejność według skrótu położenia), więc pozostałe sprawdzenia się nie zmieniły. Próg bez zmian: co najmniej połowa cięciw ma ≥ 10 bloków.

| Skala | 150 przekrojów na klasę | 600 przekrojów na klasę |
|---|---|---|
| REAL | 42 cięciwy, 83,3% ≥ 10 bloków | 157 cięciw, 80,3% |
| GAMEPLAY | 50 cięciw, 62,0% | 196 cięciw, 61,2% |

Po K5 test podaje te same liczby. Jeśli liczba cięciw olsu w GAMEPLAY spadnie o ponad połowę wobec tej bazy, to jest zmiana siedlisk do opisania (mniej olsu przy rynnach i w dnach), a nie „za mała próba”.

### Odstępstwa od projektu w K0

1. **Cel łat kontrolnych.** Projekt wymagał `streamOrder == 0` i braku wody stojącej w 300 m·k. `streamOrder == 0` zależy od ramek odrzucania odcinków (K4 je powiększa), a w GAMEPLAY nie występuje w ogóle wokół wnętrz (siatka 2 × 2 km, ziarno 20260927). Zamiast tego w obu skalach: kolumna nietknięta (bez wody i pasa wody stojącej, poza dnem, niewcięta), koryto co najmniej 300 m·k za skrajem dna i co najmniej 300 m·k za pasem wybrzeża, także na 108 punktach trzech okręgów do 300 m·k wokół łaty. Warunek wybrzeża doszedł, bo dawne łaty GAMEPLAY leżały w pasie `shapeCoast` (do 158 m od plaży), który zmieni D5.
2. **„m·k” to skala lokalna** (`valleyScale` sieci rzecznej): 300 m w REAL, 150 m w GAMEPLAY.
3. **Wybór środka z największym zapasem** zamiast pierwszego punktu spirali, i sprawdzenie łat na prototypie `PW/src3` z pełnym zestawem przełączników przed zamrożeniem (opis wyżej). Pierwsza wersja K0 brała punkt graniczny; łata sandru REAL A leżała 130 km od startu w prostokątnej dziurze ramek, a łata sandru GAMEPLAY A traciła cel na prototypie.
4. **Okna testu ciągłości.** Do ośmiu okien projektu i trzech okien masywów z recenzji doszły dwa okna masywów GAMEPLAY z tabeli K4.3 projektu ((−217490, 246246) i (−41536, 183081)), bo D4 obejmuje wszystkie okna masywów. Progi K0 to zmierzony stan obecnego terenu. Liczby skoków zgadzają się z liczbami „przed” projektu tam, gdzie okna nie mają wody stojącej ani masywów (np. Beskidy GAMEPLAY 43). Liczby „przed” projektu mierzono na łącznym prototypie, w którym starorzecza i jeziora były zawsze w nowej wersji i były masywy, więc w oknach z wodą stojącą lub z masywem się różnią (np. masyw 1710 m: 51 zamiast 15). W `realistic_moraine` 15 albo 16 to skutek niedeterminizmu poziomu jezior rynnowych, a nie różnicy wobec prototypu. Wartości „do 11 m” i „do 14 m” w kolumnie przykładów projektu nie były maksimami, tylko przykładowym skokiem z narzędzia SkokiLista (np. skok 11,19 m w (29094; 809,6) jest na obecnej liście piątym największym z 43), dlatego największe skoki w tabeli są wyższe.
5. **„Przy wodzie stojącej”** liczy skok, gdy którakolwiek z czterech próbek leży w pasie wody stojącej (projekt: próbka przy skoku). Etykieta „wybrzeże” to 3 km·meso od brzegu (było 3 km bez skalowania, co w GAMEPLAY obejmowało prawie cały pas wybrzeża 3750 m). Skutek: `gameplay_moraine` 16 skoków przy wodzie zamiast 15 (doszedł skok −67,3 m przy końcu jeziora rynnowego).
6. **`TerrainLocalityTest` sprawdza już w K0** (projekt: od K3), bo na niezmienionym terenie przechodzi. Siatki wnętrz są wyśrodkowane na łatach kontrolnych, a nie na punktach startowych, bo w GAMEPLAY wokół punktu startowego sandru 99,8% kolumn było wciętych lub przy wodzie (porównanych zostawało 93). Siatki masywów i zalewów doszły już w K0.
7. **`TerrainDeterminismTest`** (nowy, nie było w projekcie) z jawnie opisanym wyjątkiem jezior rynnowych do K5.2.
8. **Lista dozwolonych zmian** ma dwie rzeczy więcej niż w projekcie: przyrostek `target` (utrata celu jest dozwolona tylko jawnie, tabela projektu rozróżnia „zmiana” i „zmiana, cel”) i wpis `coverage` (utrata rodzaju wody, typu lub podłoża w zestawie, np. brak OXBOW w GAMEPLAY A na prototypie).
9. **Kopie zapasowe.** Projekt zakładał archiwa `tar` przed każdym krokiem, bo projekt nie był repozytorium git. Teraz jest, więc stan przed krokiem to commit bazowy (K0: `6046d53`).

## K1. Strefy dna od koryta doliny dominującej (F2, teren bez zmian)

Stan przed krokiem: commit `6046d53` z niezacommitowanym K0 (migawka drzewa roboczego jako obiekt `ef175ea`, poza gałęziami).

**Problem P1.** Strefy łęgów i klasa cieku liczyły się od najbliższego koryta. Przy zbiegu dopływu z dużą rzeką granica stref była prostą dwusieczną między korytami: w komórce dopływu dno dużej rzeki dostawało strefy małej rzeki (klasa B), czyli kliny łęgu jesionowo-olszowego i olsu w pasie łęgu wierzbowo-topolowego.

**Co weszło.**

- `RiverNetwork.query` zapisuje w buforze wątku każdy odcinek, który przeszedł ramkę odrzucania, z odległością od brzegu (`pr[5] − W/2`), szerokością, lustrem i rzędem (jedna tablica `double[]`, po 4 wartości na odcinek, bez referencji do obiektów). Po pętli wybiera **koryto doliny dominującej**: najbliższe koryto spośród odcinków tego samego rzędu co ciek dominujący i o szerokości co najmniej połowy jego szerokości (ten sam ciek także przez węzeł). Remis rozstrzyga kolejność listy kafla.
- Trzy nowe pola na końcu `RiverHit` i `ColumnSample.Waters`: `floorChannelDist`, `floorChannelWidth`, `floorChannelLevel` (bez cieku: +∞, NaN, NaN). Pola M1 próbki się nie zmieniają, więc teren i skróty złotego testu też.
- `WatersideZones.stream`: gdy kolumna leży w dnie (`inFloor`), koryto doliny dominującej jest szersze niż 1,05 W najbliższego koryta, kolumna leży dalej od najbliższego koryta niż jego pas łęgu jesionowo-olszowego (jak w klasie B: `min(80k, max(15k, 4W))`, na sandrze 5k–25k, z drganiem i minimum 6 bloków), kolumna jest w dnie tego koryta (`onValleyFloor` liczone od jego lustra i odległości) i jest ono klasy A, to strefy liczy klasa A od koryta doliny dominującej. W każdym innym przypadku jak dotąd: od najbliższego koryta.
- Bez alokacji: prototyp tworzył dla takiej kolumny drugą próbkę i drugą `Column`. Tu `classA` i `restOfFloor` dostają znacznik „koryto doliny dominującej”, a `Column` ma wersje `onValleyFloor`, `uJittered`, `heightAboveChannel`, `wr` i `WatersideZones.streamClass` z podanym korytem. Brzeg wypukły dotyczy najbliższego koryta, więc dla koryta doliny dominującej jest wyłączony (jak w prototypie). Ścieżka najbliższego koryta liczy się dokładnie jak przed K1.
- Javadoc `RiverNetwork.candidates`: kolejność listy odcinków kafla (rząd 3 → 1, potem i, j) jest stała i nie wolno jej zmieniać, bo rozstrzyga remisy (dolina dominująca, koryto doliny dominującej).

**Złoty test: dowód, że teren się nie zmienił.** `./gradlew test --tests '*GoldenTerrainTest*' -PgoldenReport=build/golden_K1.txt`: 2 z 2 (`terrainMatchesGolden` z plikiem M2 i `frozenM1CopyMatchesGolden` z plikiem M1), raport: `# no changes`. `TerrainLocalityTest`: 0 zmienionych kolumn na wszystkich siatkach.

**Nowe i zmienione testy.**

- `HabitatClassifierTest.floorZonesFollowTheChannelOfTheDominantValley` (przypadki syntetyczne, 200 punktów drgania): kolumna 60 m od dopływu szerokiego na 8 m i 150 m od brzegu rzeki szerokiej na 150 m jest w łęgu wierzbowym tej rzeki; w pasie dopływu (10 m) łęg jesionowo-olszowy; poza dnem rzeki (lustro rzeki 6,7 m niżej), przy korycie nie szerszym niż 1,05 W, przy korycie klasy B i poza flagą dna wynik jest identyczny jak bez pól F2.
- `WatersideZonesTest.floorZonesFollowDominantRiver` (projekt: K1 mierzy, od K3 wymaga ≥ 95%). Okna 1,5 km·k × 1,5 km·k co 10 m·k wokół pierwszych 20 zbiegów dopływu rzędu 2 lub 1 z doliną rzędu 3 na dnie nizinnym, w obu skalach (ziarno 20260927, `RiverNetworkProbe.confluences`). Okna się nie nakładają: kolejny zbieg leży co najmniej 1,5 km·k od wcześniejszych (odległość Czebyszewa).
  - **Mianownik jest geometryczny**, niezależny od doliny dominującej modelu (`best`) i od pól F2. Rzeka to najbliższe koryto rzędu 3 liczone z jej własnych odcinków (`RiverNetworkProbe.river`, pakietowe `RiverNetwork.segmentsAt` z tymi samymi wzorami co `query`). Liczy się kolumna sucha, bez pasa wody stojącej, w dnie odcinka rzędu 3 (jak `inFloor`: odległość od osi doliny mniejsza niż połowa szerokości dna, wygaszenie źródłowe > 0,5) i najwyżej 2,3 m nad lustrem rzeki (jak `onValleyFloor`). Najbliższe koryto modelu musi być węższe od rzeki (dopływ albo inny mały ciek), a kolumna musi leżeć za jego najszerszym pasem OlJ (+20% drgania). Rzeka musi być klasy A według własnej szerokości i spadku, a kolumna w zasięgu jej łęgu topolowego (D_top −20%) i poniżej 300 m n.p.m. Takiej kolumnie należy się wiklina albo łęg wierzbowo-topolowy rzeki; wszystko inne to klin.
  - Niepowodzenia test dzieli na P2 (koryto doliny dominującej nie jest szersze od najbliższego, czyli doliną dominującą jest mniejszy ciek), „F2 wybrało inne koryto” i inne. Przy zbiegach bez policzonych kolumn podaje pierwszy filtr, który usunął wszystkie.
  - Ten sam test mierzy **całe dno** rzeki klasy A za pasem mniejszego cieku, bez ograniczenia D_top i wysokości: w ilu kolumnach F2 znalazło rzekę jako koryto doliny dominującej, a w ilu jest P2. Tu widać kliny reszty dna (łęg wiązowo-jesionowy, zastoiska), które leżą dalej od rzeki niż zasięg łęgu topolowego.
  - Pierwsza wersja testu (z raportu K1) wybierała kolumny warunkami na polach F2 (`floorChannelWidth > 1,05 W`, klasa i dno liczone od `floorChannel*`), czyli dokładnie warunkami gałęzi F2 w `WatersideZones.stream`. Wynik 100% był więc pewny z konstrukcji, a kolumny P2 wypadały z mianownika, zamiast liczyć się jako błąd. Zbiegi porównywała po równości współrzędnych, więc nakładające się okna liczyły te same kolumny dwa razy.

Wyniki K1:

| | REAL | GAMEPLAY |
|---|---|---|
| Zbiegi z policzonymi kolumnami | 11 z 20 | 8 z 20 |
| Kolumny w zasięgu łęgu topolowego | 736 | 288 |
| W nich strefy rzeki (wiklina, łęg wierzbowo-topolowy) | **100,0%** | **99,7%** (1 kolumna „inne”) |
| To samo bez pól F2 (strefy najbliższego koryta, jak przed K1) | 0,0% | 0,0% |
| W nich niepowodzenia P2 | 0 | 0 |
| Całe dno: kolumny | 37 936 | 4693 |
| Całe dno: F2 znalazło rzekę | 98,0% | 88,6% |
| Całe dno: P2 (strefy mniejszego cieku, kliny) | 764 (2,0%) | 533 (11,4%) |

Zbiegi bez kolumn: REAL 7 × rzeka rzędu 3 nie jest klasy A (za wąska), 2 × wszystkie kolumny poza D_top; GAMEPLAY 8 × rzeka nie klasy A, 2 × brak suchego dna rzeki w oknie, 2 × żadna kolumna nie leży za pasem mniejszego cieku. Najwięcej P2 na całym dnie w REAL: zbieg (−18844; 11206), 188 kolumn (5,3% dna), czyli klin olsu, który recenzent znalazł na kadrze tego zbiegu (niżej, „Co zostaje”).

Wniosek: w zasięgu łęgu topolowego kliny zniknęły już w K1 (w tych oknach nie ma tam ani jednej kolumny P2). Zostały kliny reszty dna, dalej od rzeki: 2,0% dna w REAL i 11,4% w GAMEPLAY. Usuwa je F1 w K3.

Wymagania testu. Teraz: strefy rzeki w ≥ 95% policzonych kolumn, w których model znalazł rzekę jako koryto doliny dominującej (sprawdza strefy F2, nie wybór doliny; teraz 736 z 736 i 287 z 288), oraz dość kolumn (REAL ≥ 300 przy ≥ 5 zbiegach, GAMEPLAY ≥ 150 przy ≥ 4). Od K3 (stała `F2_SHARE_ENFORCED = true`): strefy rzeki w ≥ 95% wszystkich policzonych kolumn i rzeka znaleziona w ≥ 95% całego dna.
- Ten sam test liczy odcinki w ramce odrzucania na siatce przekrojów `WatersideZonesTest` (240 × 240 punktów, obie skale): najwyżej 25 (REAL) i 28 (GAMEPLAY), 0 kolumn ponad pojemność bufora 64.
- `TerrainDeterminismTest`: pola koryta doliny dominującej muszą być identyczne w obu kolejnościach próbkowania we wszystkich kolumnach (także przy jeziorach rynnowych). Wynik: 0 różnic we wszystkich 6 kadrach.

**Wpływ na siedliska.** `WatersideZonesTest` (próba K0): REAL bez zmian (157 cięciw olsu, 80,3% ≥ 10 bloków); GAMEPLAY 189 cięciw zamiast 196, 58,7% zamiast 61,2% (próg 50%). Ols w dnach dużych rzek przy dopływach to teraz zastoiska klasy A albo łęg dużej rzeki zamiast olsu klasy B dopływu. Przekroje uporządkowane: REAL A 144/150, B 149/150, C 149/150; GAMEPLAY 150/150 w każdej klasie.

**Kadry `HabitatPreview` przed i po K1.** Recenzja K1 wyrenderowała kadry `large_river_valley_2km` (REAL, środek (−5203; 93856), 2 km) i `gameplay_large_river_valley_1km` (GAMEPLAY, (−10323; −33338), 1 km) oraz 12 kadrów zbiegów, raz na migawce sprzed K1 (`ef175ea`) i raz po K1. Środki to te z `HabitatPreview.findClassSite`, a mapy rysują `HabitatPreview.samples` i `save`, czyli tak samo jak `./gradlew landscapePreview -PhabitatsOnly`. Teren jest identyczny we wszystkich 14 kadrach (0 różnych kolumn powierzchni, lustra i rodzaju wody). Bufor kandydatów z poprawek recenzji (tablica liczb zamiast referencji) nie zmienia wyniku, bo wybór porównuje te same liczby w tej samej kolejności. Sprawdziłem to: dwa kadry `HabitatPreview` wyrenderowane po poprawkach są identyczne z kadrami „po K1”.

- `large_river_valley_2km`: zniknęły dwa kliny łęgu wiązowo-jesionowego z prostymi krawędziami (dwusieczne od ujść dopływów) w pasie łęgu wierzbowo-topolowego rzeki. 19 108 kolumn (2,99% kadru) zmienia się z łęgu wiązowo-jesionowego na łęg wierzbowo-topolowy, a w 10 204 kolumnach dochodzi strefa ziołorośli. Pas łęgu topolowego biegnie teraz wzdłuż rzeki przez ujścia.
- `gameplay_large_river_valley_1km`: zmienia się 20,6% kadru. Bór bagienny → łęg wiązowo-jesionowy 62 tys. kolumn, bór wilgotny → łęg wiązowo-jesionowy 39 tys., ols → łęg wiązowo-jesionowy 26 tys. Kliny stref dopływów (klasa B) w dnie rzeki, ograniczone prostymi dwusiecznymi, zniknęły; dno rzeki ma teraz łęg wiązowo-jesionowy z pasem łęgu wierzbowo-topolowego. Zostaje pas boru wilgotnego i boru bagiennego wzdłuż wąskiego koryta rzędu 3 (W 2,4 m), które przecina dno, i krótki klin przy dopływie SE (P2, „Co zostaje”).
- Kadry zbiegów REAL: zmienia się 12–15% kadru, głównie ols → łęg wiązowo-jesionowy.
- Długość granic biomów maleje w każdym zmienionym kadrze, np. przegląd REAL 12 km: −22,8 km i +10,2 km granic, `gameplay_large_river_valley_1km`: −7,4 km i +3,7 km.

Pliki: `viz_K1` w katalogu roboczym sesji (`png/before`, `png/after`, `png/diff`, `diff_report.txt`, `edges_report.txt`, `newedges_report.txt`; kadry w `frames.txt`, renderowanie `render.sh`).

**Udziały biomów.** F2 zmienia duże części den dużych rzek, zwłaszcza w GAMEPLAY: mniej boru bagiennego, boru wilgotnego i olsu, więcej łęgu wiązowo-jesionowego. `BiomeSharesTest` przechodzi, ale pliki udziałów (`m2_biome_shares.csv`, `m2_zone_shares.csv`) przesuną się w tę stronę już od K1. Przegenerowuje je K7; tej zmiany nie należy przypisywać krokom K3–K6.

**Koszt.** Pierwszy pomiar K1 (`SampleCostTest`, 7 przebiegów w pełnym `./gradlew test`) dał stosunki do kopii M1 1,007–1,079 (mediany). Raport K1 przypisał je rozrzutowi i podał zły zakres K0 („0,98–1,03”). Pięć wcześniejszych przebiegów (zmiana nazw, K0 i jego poprawki) dało mediany 0,90–1,03 i 0,93–0,99 z minimów; bez przebiegu sprzed K0 mediany 0,90–0,99. Przebieg K1 był powyżej każdego z nich, częściowo dlatego, że kopia M1 liczyła w nim szybciej niż zwykle; drugi pełny przebieg (recenzja K1) dał 0,953–0,996.

Dlatego pomiar A/B: te same klasy `SampleCostTest` skompilowane z trzech drzew (K0 = migawka `ef175ea`, K1 jak w raporcie, K1 po poprawkach recenzji), każde w osobnej JVM, na zmianę, 3 powtórzenia po 7 rund. Mediany z trzech powtórzeń (µs na kolumnę, w nawiasie stosunek do M1):

| Obszar | K0 | K1 (bufor `Segment[]`) | K1 po poprawkach (`double[]`) |
|---|---|---|---|
| REAL cały obszar | 3,97 (1,015) | 4,02 (1,027) | 4,01 (1,019) |
| REAL Beskidy | 6,39 (0,990) | 6,51 (1,000) | 6,46 (0,993) |
| GAMEPLAY cały obszar | 5,50 (1,014) | 5,58 (1,022) | 5,60 (1,031) |
| GAMEPLAY Beskidy | 9,47 (0,981) | 9,61 (0,988) | 9,53 (0,985) |

K1 kosztuje więc ok. 1–2% (stosunek do M1 +0,3–1,7 p.p. wobec K0), a nie 5–10 p.p. z pierwszego przebiegu. Bufor z referencjami do odcinków (`Segment[]`, bariera zapisu GC przy każdym odcinku) zastąpiła jedna tablica `double[]` (odległość, szerokość, lustro, rząd), a rzadkie powiększanie bufora jest w osobnej metodzie. Zysk z tej zmiany mieści się w rozrzucie (do 1% w obu kadrach Beskidów, w całych obszarach bez wyraźnej różnicy), ale bufor wątku nie trzyma już nieaktualnych odcinków. Wszystko mieści się w budżecie D1 (+20%, bezwzględne limity 6,5/12/8,5/16 µs). Pliki: `K1fix/ab` w katalogu roboczym sesji (`ab.py`, `ab_run1.log`). W pełnym `./gradlew test` po poprawkach `SampleCostTest` dał 1,023–1,065 (mediany; 4,40 / 7,19 / 6,00 / 10,16 µs). Ten pomiar biegnie w jednej JVM po kilkunastu innych klasach testów, więc jego stosunek do M1 skacze między przebiegami o kilka punktów (K1: 1,007–1,079, recenzja K1: 0,953–0,996, teraz 1,023–1,065); różnice kroków trzeba porównywać pomiarem A/B jak wyżej. Koszt klasyfikacji (`classificationTakesAtMostHalfAMicrosecond`) przechodzi.

**Co zostaje (zgodnie z projektem).**

- Do K3 doliną dominującą bywa dopływ, gdy jego pas meandrów przecina dno dużej rzeki (P2). F2 bierze wtedy koryto dopływu i zostają jego strefy. Test liczy takie kolumny jako P2 (całe dno: 2,0% w REAL, 11,4% w GAMEPLAY). Kliny znikną w pełni dopiero z F1 (K3), i dopiero wtedy obowiązują progi 95%.
- Kliny pośrednie znalezione w recenzji K1 (nowe krótkie proste granice tam, gdzie dolina dominująca przechodzi z rzeki na dopływ; po jednej stronie F2 daje strefy rzeki, po drugiej dominuje dopływ). Są dużo krótsze niż usunięte kliny. Według klucza F1 (`floorHalf − floorDist`) w obu sondach wygrywa rzeka, ale K3 musi to sprawdzić:
  - REAL, kadr `R_conf_18844_11206_1500m`: klin olsu wzdłuż dopływu z dwiema prostymi krawędziami, 431 m (x −19594…−19307, z 10677…10824) i 304 m (x −19360…−19245, z 10630…10812). Sonda (−19424; 10757) → (−19422; 10766): `best` przeskakuje z rzędu 2 (u = 0,37, połowa dna 62 m) na rząd 3 (u = 0,39, 202 m), koryto doliny dominującej z W 4 na W 29,5 m, ols → łęg wiązowo-jesionowy.
  - GAMEPLAY, kadr `gameplay_large_river_valley_1km`: pas boru wilgotnego i boru bagiennego o prostych, równoległych krawędziach wzdłuż wąskiego koryta rzędu 3 (W 2,4 m, przypadek N3) w dnie rzeki. Odcinki 169–184 m, np. x −10583…−10410, z −33402…−33257 oraz x −10048…−9884, z −33675…−33577; razem 2,47 km nowych granic na granicy decyzji K1. Sonda (−10460; −33358) → (−10455; −33351): połowa dna 86 → 33 m, W koryta doliny dominującej 12 → 2,4 m, łęg wiązowo-jesionowy → bór wilgotny. Do tego krótki klin przy dopływie SE, (−10104; −33307) → (−10004; −33269).
  - K3: wyrenderować te same kadry (`viz_K1/frames.txt`, `render.sh`), uruchomić `newedges.py` i wymagać, żeby pas i kliny w tych miejscach zniknęły, a długość nowych granic na granicy F2 spadła prawie do zera.
- Przy zbiegu dwóch odcinków tego samego rzędu i podobnej szerokości oba spełniają warunek, więc między ich korytami zostaje dwusieczna. Zostają też dwusieczne poza dnami klasy A (N4). To M5.

### Odstępstwa od projektu w K1

1. **Bufor kandydatów rośnie zamiast limitu 64.** Projekt (recenzja) przewidywał stały bufor 64 miejsc i zapis tylko tych odcinków, które mogą wygrać (odległość poniżej zasięgu stref, rząd ≥ 2 albo W ≥ 15 m·k), z testem przepełnień. Filtr zmieniałby wynik: odcinek pominięty przez filtr nie może zablokować wyboru dalszego, szerszego odcinka, więc pola różniłyby się od prototypu, a przy przepełnieniu dalsze odcinki znikałyby w kolejności listy i pola stref mogłyby skakać. Bufor zaczyna od 64 miejsc i w razie potrzeby podwaja się (raz na wątek i model, bo każda `RiverNetwork` ma własne bufory wątków), więc żaden kandydat nie ginie, a wynik jest dokładnie taki jak w prototypie bez limitu. Test przepełnień zostaje jako kontrola pojemności początkowej (0 przepełnień, najwyżej 28 kandydatów).
2. **Zbiegi do pomiaru** wyszukuje test (`RiverNetworkProbe.confluences`: końce odcinków rzędu 2, potem 1, w dnie nizinnym doliny rzędu 3, co najmniej 1,5 km·k od siebie), w obu skalach, a nie lista `dna-dolin/out/zbiegi_real.txt` (tylko REAL). Pierwsze 20 wierszy tej listy to końce odcinków rzędu 3 w dolinach rzędu 3, czyli zbiegi dwóch rzek rzędu 3 o podobnej szerokości, przy których F2 z założenia nie działa (projekt, „Co zostaje”).
3. **Pomiar liczy także wiklinę** (`WILLOW_SCRUB`) jako strefę rzeki, bo przy korycie rzeki klasa A daje pas wikliny przed łęgiem.
4. **Pomiar całego dna i ostrzejszy próg od K3.** Projekt mierzy tylko kolumny łęgu topolowego. Test mierzy też całe dno rzeki klasy A za pasem mniejszego cieku, bo pozostałe kliny (P2) leżą dalej niż zasięg łęgu topolowego i pierwszy pomiar ich nie widzi. Od K3 test wymaga, żeby F2 znajdowało rzekę w ≥ 95% całego dna. Jeśli F1 tego nie osiągnie, K3 musi opisać przyczynę, a nie tylko obniżyć próg.
5. **Pomocnik testowy w kodzie produkcyjnym.** `RiverNetwork.segmentsAt` (pakietowy, alokuje) liczy geometrię odcinków danego rzędu tymi samymi wzorami co `query`, żeby test mógł wyznaczyć rzekę bez `best`. Gra go nie wywołuje (jak `frameCandidates`).

## K2. Wielkie masywy Beskidów (M2-8)

Stan przed krokiem: commit `6046d53` z niezacommitowanymi K0 i K1 (różnica wobec `HEAD` zapisana przed krokiem w katalogu roboczym sesji, `k2base/pre_k2.diff` i `pre_k2_untracked.tar`). Po recenzji K2 część opisu poniżej się zmieniła; zmiany z recenzji są w podsekcji „Poprawki po recenzji K2”.

**Zasięg zmian.** `landElevation` zmienia się tylko w zasięgu masywów (G > 0). Poza nim jest bit w bit taki jak w M1 (`greatMassifsReachTarget`: 10 000 punktów Beskidów w każdym oknie; `TerrainLocalityTest`). Zmienia się jednak także sieć rzeczna: reguły rzek przy masywach i nowy teren w zasięgu przekładają spływ, a to zmienia doliny daleko od masywu. W REAL wokół masywu 1723 m (kadr 60 km) zmienione doliny sięgają ok. 30 km na NE i E (Δh od −326 do +1173 m, jedna duża dolina przez masyw znika, inna się pojawia). Udowodniona jest więc lokalność terenu poza dolinami, a nie lokalność dolin. Łaty złotego testu poza `great_massif` się nie zmieniły.

### Co weszło

**Pole masywów** (`LandscapeModel`, przy polu `mountain`):

- Nowa sól `"mountain.great"` (nigdy nie zmieniać; liczbowe sole `unit(i, j, 1..4)` też należą do świata). Siatka kandydatów o boku S = 60 km (REAL) i 4 km (GAMEPLAY). Kandydat leży w `(i + 0,2 + 0,6·u2, j + 0,2 + 0,6·u3)·S` i jest dopuszczalny przy `u1 < 0,45`, `mountainField ≥ 0,90` i wadze BESKIDS ≥ 0,98 w środku.
- **Przerzedzenie** w `ceil(2,5·Ra/S)` pierścieniach oczek (REAL 1, GAMEPLAY 2), jak chciała recenzja projektu. Odpada kandydat, który ma bliżej niż 2,5 Ra dopuszczalnego kandydata o mniejszym `u1`. W oknie GAMEPLAY 600 km jest przez to 25 masywów zamiast 26 z prototypu (prototyp sprawdzał tylko 8 sąsiednich oczek).
- Elipsa wzdłuż pasma: Ra = 8 km·mspace, Rc = 4 km·mspace, kierunek z gradientu `mountainRaw` (jak w `cell`), obrys `d² · (1 + 0,3·fbm(0,6 Ra))`, siła G = (1 − d²)³.
- **Zasięgi masywów są rozłączne.** G > 0 wymaga d² < 1/0,7 przed szumem obrysu, czyli odległości mniejszej niż 1,2 Ra od środka, a przyjęte środki leżą co najmniej 2,5 Ra od siebie (pełne przerzedzenie: z dwóch dopuszczalnych kandydatów bliżej niż 2,5 Ra ten z większym `u1` zawsze odpada). W każdym punkcie liczy się więc co najwyżej jeden masyw, a podniesienie jest ciągłe z konstrukcji. Recenzja projektu chciała maksimum po masywach, bo przy niepełnym przerzedzeniu prototypu zasięgi mogły się stykać. Kod i tak bierze masyw najsilniejszy, a test `greatMassifsReachTarget` sprawdza odstęp środków.
- **Kopuła** (`greatMassifLift`, `greatMassifFill`, `greatMassifCeiling`) w komórkach BESKIDS **i FOOTHILLS** (po recenzji K2): obwiednia grzbietów `floor + relief` dąży do `T = cel + 35 m` z wagą G, z tego 35% do `floor`. Gdzie obwiednia jest już wyżej niż T, obniża się tylko w miarę wypełnienia dolin: `lift = G·l0` dla `l0 = T − obwiednia ≥ 0`, a dla `l0 < 0` `lift = G·l0·smoothstep(0,3; 0,9; G)`. Doliny fliszu są wypełnione przy środku (`fill = smoothstep(0,3; 0,9; G)` dla p0 i p1), kopuły `p3 += G·(1 − p3)`.
- **Miękki pułap** (recenzja projektu, p. 2): tam, gdzie wynik przekracza 1500 m, nasycenie `1500 + 250·tanh` łączy się z pułapem `knee + 35·tanh((h − knee)/35)`, `knee = cel − 35 m`, z wagą `smoothstep(0; 0,3; G)`. Poza zasięgiem masywów wzór jest bit w bit stary (w komórkach FOOTHILLS poza zasięgiem nasycenia nie ma, jak w M1).
- `Terrain.massif = max(massif, G)` (z komórki BESKIDS, Javadoc `ColumnSample.Terrain`). Pole `summit` liczy `PeakField` z `landElevation`, więc wstawka z prototypu, która podbijała `szczyt`, odpada.
- `describe` liczy SUMMIT i MOUNTAIN_PASS na masywie z wypełnionymi dolinami, tak jak teren (w obu typach fliszu). Bez tego wierzchołek kopuły nie był SUMMIT (p0 < 0,75 przed wypełnieniem) i łata `great_massif` nie znajdowała celu.
- API: rekord `LandscapeModel.GreatMassif(x, z, targetSummit)`, publiczne `greatMassifs(box)` i `nearestGreatMassif(x, z)` (testy, kadry, łata złota, później komenda `find`), pakietowe `greatMassifAt`, `greatMassifStrength`, `greatMassifCore`, `greatMassifNear`, `greatMassifCell`, `greatMassifRa`, `greatMassifRc`. Pamięci podręczne `greatMassifCells` i `greatMassifEligibleCache` (klucze `Noise.key(i, j, 7/8)`) są czystą funkcją oczka, obsługiwane przez `get`/`put` (obliczenie czyta sąsiednie oczka, więc nie `computeIfAbsent`), czyszczone przy 100 tys. wpisów bez wpływu na wynik.

**Reguły rzek** (`RiverNetwork`):

- (a) `isSpring` zwraca false na wierzchowinie (G > 0,3), dla wszystkich rzędów.
- (b) `link`: węzeł spoza wierzchowiny wybiera najniższego niższego sąsiada w pierścieniach 1–4, do którego prosta droga nie przecina wierzchowiny. Punkty drogi leżą co najwyżej 0,25 Rc od siebie, najmniej 7 punktów wewnętrznych (po recenzji K2; wcześniej zawsze 7, czyli przy drogach rzędu 3 w REAL co 2,5–10 km, więcej niż szerokość wierzchowiny w poprzek pasma). Szybki filtr `greatMassifNear` omija liczenie G poza okolicą masywów.
- Gdy żadnego takiego sąsiada nie ma: sąsiad niższy w pierścieniach 1–4 o **najmniejszym maksimum G na drodze** (odstępstwo, niżej). Nowych jezior bezodpływowych przez to nie przybywa.
- Dwie poprawki geometrii odcinków przy masywach z recenzji K2 (styczna na końcu odcinka wpadającego do jeziora bezodpływowego i rzut na odcinek): podsekcja „Poprawki po recenzji K2”.

### Strojenie S3: płaskowyż masywu na wysokiej obwiedni

Pomiar `landElevation` co 100 m (REAL) i 20 m (GAMEPLAY) w kwadracie 2,4 Ra, tylko kolumny bliższe temu masywowi niż innym; okna z projektu (REAL 1000 km wokół (400; −1250) km, GAMEPLAY 600 km wokół (0, 0)). Kryterium `greatMassifSummitIsNotPlateau`: pole ≥ 1650 m ≤ 0,35 pola 1390–1650 m i ≤ 2,5 km² (REAL) / 0,25 km² (GAMEPLAY).

| Wariant | REAL masyw (258824, −1539366), cel 1700 m | GAMEPLAY masyw (−177226, 30624), cel 1698 m | Najniższy szczyt − cel |
|---|---|---|---|
| projekt: podniesienie tylko w górę, pułap 40 m | ≥ 1650 m: 5,47 km², stosunek 0,53 | 0,225 km², 0,37 | REAL −29 m |
| pułap zależny od G (`knee − D·(1 − ∛G)^p`), D 350–600 m, p 0,5–0,7 | 0,8–2,4 km² | nie mierzone | REAL −34 do −42 m (szczyty poza środkiem kopuły) |
| podniesienie ze znakiem w całym zasięgu, pułap 40 m | 2,19 km², 0,18 | 0,225 km², 0,37 | REAL −29 m |
| podniesienie ze znakiem w całym zasięgu, pułap 25 m | 1,77 km², 0,15 | 0,185 km², 0,29 | REAL −42 m (poza kryterium −40) |
| podniesienie ze znakiem w całym zasięgu, pułap 35 m (pierwsza wersja K2) | 2,08 km², 0,18 | 0,209 km², 0,34 | REAL −32 m |
| **obniżanie tylko w miarę wypełnienia dolin, pułap 35 m (po recenzji)** | **2,10 km², 0,16 (≥ 1390 m: 14,9 km²)** | **0,209 km², 0,34** | **REAL −26 m** |

- Przyczyna płaskowyżu: obwiednia pasma w tym miejscu (floor + relief) ma ok. 1856 m, więcej niż obwiednia docelowa T = 1735 m. Podniesienie `max(0, …)` jest wtedy zerowe, a wypełnione doliny dają teren ok. 1800 m na kilku km², który pułap ścina do 1700 m.
- Pierwsza wersja K2 obniżała taką obwiednię w całym zasięgu masywu. To przełożyło spływ wokół masywu: powstało jezioro bezodpływowe w (260287, −1536765), a odcinek rzędu 1 wpadający do niego wyciął w kopule szczelinę głęboką na 1 km (niżej). Teraz obwiednia obniża się tylko tam, gdzie doliny są wypełniane (`smoothstep(0,3; 0,9; G)`), czyli tam, gdzie powstawał płaskowyż. Jeziora i szczeliny nie ma, a płaskowyżu też nie.
- W kolumnach z obwiednią < T wynik jest taki sam jak z podniesieniem tylko w górę („bit w bit jak w projekcie” dotyczy tylko ich). Pomiar okien zmienił się w REAL tylko na masywie (258824, −1539366), a w GAMEPLAY w żadnym masywie.
- Pułap 35 m zamiast 40 m: z 40 m masyw GAMEPLAY (−177226, 30624) miał stosunek 0,37 (tak samo w pomiarze recenzji projektu: 0,22 km² przy 0,8 km² ≥ 1390 m, choć recenzja pisała, że wszystkie masywy GAMEPLAY spełniają kryterium). 25 m daje za niskie szczyty.

**Wynik (REAL, okno 1000 km, 9 masywów, po recenzji K2):**

| Masyw | Cel | Szczyt | ≥ 1390 m | ≥ 1650 m | Stosunek |
|---|---|---|---|---|---|
| (23486, −1238305) | 1681 | 1668 | 9,10 km² | 0,21 km² | 0,02 |
| (147582, −1525292) | 1731 | 1723 | 9,75 | 1,98 | 0,26 |
| (143663, −1476297) | 1708 | 1702 | 9,99 | 1,98 | 0,25 |
| (219194, −1516439) | 1634 | 1632 | 10,21 | 0 | 0 |
| (258824, −1539366) | 1700 | 1697 | 14,91 | 2,10 | 0,16 |
| (433485, −1485508) | 1701 | 1698 | 10,08 | 2,14 | 0,27 |
| (515882, −1401302) | 1677 | 1659 | 9,28 | 0,10 | 0,01 |
| (694514, −1238173) | 1678 | 1652 | 13,43 | 0,01 | 0,00 |
| (826315, −1103167) | 1682 | 1669 | 9,90 | 0,35 | 0,04 |

GAMEPLAY (okno 600 km): 25 masywów, szczyty 1618–1719 m (szczyt − cel od −2 do −23 m), ≥ 1650 m najwyżej 0,209 km², stosunek najwyżej 0,34 (masyw (−177226, 30624); K6 zmienia `landElevation` na szwach regionów, więc musi ten test uruchomić). Masyw najbliższy spawnu GAMEPLAY: (6854, −33757), cel 1637 m. Masyw najbliższy (0, 0) w REAL: (98290, 1033389), cel 1659 m, ok. 1038 km od środka. Na szczytach `sample` = `landElevation` (bez wód).

**Rozkład nachyleń** (recenzja K2; `landElevation`, różnice centralne, siatka 25 m w kwadracie ±4,8 km w REAL i 10 m w kwadracie ±1,44 km w GAMEPLAY; „w blokach” z odwzorowaniem pionowym skali: REAL 1:1 do ok. 900 m, GAMEPLAY 1,89·m^0,742):

| Masyw | Stan | m: < 15° | 15–30° | 30–45° | 45–60° | ≥ 60° | bloki: 30–45° | ≥ 45° |
|---|---|---|---|---|---|---|---|---|
| REAL (258824, −1539366) | K0 | 49,2% | 45,1% | 5,5% | 0,2% | 0 | 3,2% | 0 |
| | K2 po recenzji | 41,2% | 40,6% | 15,3% | 2,9% | 0 | 9,5% | 0,9% |
| REAL (147582, −1525292) | K0 | 92,0% | 7,9% | 0,1% | 0 | 0 | 0,1% | 0 |
| | K2 po recenzji | 68,9% | 20,6% | 9,3% | 1,1% | 0 | 5,0% | 0,1% |
| GAMEPLAY (6854, −33757) | K0 | 20,4% | 33,3% | 30,6% | 14,3% | 1,4% | 0,1% | 0 |
| | K2 po recenzji | 16,0% | 24,7% | 21,9% | 22,6% | 14,9% | 4,5% | 0 |
| GAMEPLAY (−217490, 246246) | K0 | 16,3% | 36,3% | 33,5% | 11,8% | 2,1% | 0,6% | 0 |
| | K2 po recenzji | 10,7% | 26,7% | 27,9% | 19,2% | 15,4% | 5,4% | 0,1% |

Pierwsza wersja K2 dawała na masywie (258824, −1539366) 14,9% stoków 30–45° w metrach i 9,4% w blokach, czyli tyle, co projekt (14% i 8%). Zmiana obniżania nie zmieniła rozkładu. W GAMEPLAY kopuła jest stroma w metrach modelu (≥ 45°: 37% wobec 16% bez masywu), ale w blokach stoki 30–45° to tylko 4,5–5,4%.

**Piętra na prawdziwym terenie** (`AltitudinalBeltsTest.greatMassifHasDwarfPineAndAlpine`, klasyfikacja co 40 m / 10 m, po recenzji K2):

| | Masyw | Kosodrzewina | Hala | Świerczyna |
|---|---|---|---|---|
| REAL, najbliższy (0, 0), kwadrat 16 km | (98290, 1033389), cel 1659 m | 8,86 km² | 0,51 km² | 24,5 km² |
| REAL, najwyższy w oknie | (147582, −1525292), 1723 m | 7,64 km² | 2,13 km² | 14,9 km² |
| GAMEPLAY, najbliższy spawnu, kwadrat 5 km | (6854, −33757), cel 1637 m | 0,75 km² | 0,010 km² | 1,26 km² |
| GAMEPLAY, najwyższy w oknie | (−217490, 246246), 1719 m | 0,77 km² | 0,153 km² | 1,22 km² |

Hala na najwyższym masywie REAL to ok. 1/4 kosodrzewiny (projekt z pułapem: 2,0 i 7,8 km²). `dwarfPineOnlyOnLargeMassif` (E12 liczone wprost z `landElevation`): 0 kolumn kosodrzewiny lub hali bez szczytu > 1470 m w promieniu 3 km·mspace dla ziaren 20260927, 4 i 1 (REAL) oraz 20260927 (GAMEPLAY). Ziarno 20260927 ma kosodrzewinę w obu skalach (test tego wymaga), kwadrat GAMEPLAY poszerzony z 70 do 100 km. To zamyka odstępstwo S4 z §5.1 planu (opis w `docs/03-m2-biomy.md` zmienia K7).

### Poprawki po recenzji K2

Recenzja K2 znalazła na stokach masywów urwiska i kaniony, których pierwsza wersja nie pokazywała w testach. Pomiar siatkowy (`CliffScan`, katalog roboczy sesji `k2fix`): pary sąsiednich suchych kolumn z |Δh| > 25 m na 5 m (REAL) lub na 2,5 m (GAMEPLAY), w kwadracie ±1,2 Ra wokół każdego masywu okna.

| Stan | REAL, 9 masywów | GAMEPLAY, 25 masywów |
|---|---|---|
| K0 (bez masywów) | 3560 | 9884 |
| pierwsza wersja K2 | 7326 | 8853 |
| obniżanie tylko przy wypełnieniu | 5568 | — |
| + styczna końca odcinka przy jeziorze bezodpływowym | 3693 | — |
| + kopuła także w komórkach FOOTHILLS, gęstsze punkty drogi | 3689 | 9132 |
| **+ rzut z pełną odległością przy masywach (po recenzji)** | **3652** | **7991** |

**1. Szczelina 1 km w kopule masywu (258824, −1539366) i „pudła” za źródłami.** Recenzja miała rację co do źródła szczeliny: to nie A2 z M1, tylko skutek obniżania obwiedni w całym zasięgu (wyżej). Mechanizm bezpośredni ustaliłem sondą `QProbe`/`FoldProbe`: odcinek rzędu 1 (263163, −1536910) → (260287, −1536765) kończy się w nowym jeziorze bezodpływowym. Styczna węzła z jeziorem bezodpływowym (`tangent`, brak odpływu) to domyślne {1, 0}, czyli na wschód, a odcinek płynie na zachód. Krzywa Hermite'a zawraca więc tuż przed końcem (pętla przy t ≈ 0,9). Dla punktu w kopule 2 km od osi lokalne minimum odległości z próbek t = q/16 trafia na zawrót, krok Gaussa-Newtona w `refine` przeskakuje i zatrzymuje się na brzegu przedziału (t = 0,886, odległość wzdłuż 2012 m, w poprzek 0,1 m). `projectChannel` brał tylko składową poprzeczną, więc kolumna dostawała odległość od koryta kilku metrów, dno doliny i wcięcie 1019 m. Tak samo powstały „pudła” z recenzji:

| Miejsce | Odcinek | t, odległość wzdłuż / w poprzek | Pierwsza wersja K2 |
|---|---|---|---|
| (144478, −1474422), masyw (143663, −1476297) | (150892, −1476504) → (146745, −1473298), koniec w jeziorze bezodpływowym | 0,9375; −2356 / −103 m | ściana 378 m |
| (150167, −1525912), masyw 1723 m | (152986, −1525865) → (151640, −1525604), nowe jezioro | 0,9375; −1452 / −18 m | 151 m |
| (151166,6; −1524180,1), okno `realistic_massif_1723` | dopływy tego samego jeziora | — | skok 68,5 m |

To nie jest mechanizm z hipotezy recenzji (poziom źródła ograniczony spadkiem w dół przy podniesionym stoku): wcina odcinek poniżej źródła albo odcinek kończący się w jeziorze, a ściana stoi tam, gdzie minimum rzutu znika. Poprawki:

- `RiverNetwork.build`: odcinek, który kończy się w jeziorze bezodpływowym przy masywie (prostokąt odcinka ± oczko siatki rzędu dotyka zasięgu masywu, `greatMassifNear`), dochodzi do jeziora wzdłuż cięciwy zamiast stycznej {1, 0}.
- `RiverNetwork.projectChannel`: dla odcinków, których prostokąt wpływu dotyka zasięgu masywu (`Segment.nearMassif`), minimum, którego `refine` nie doprowadził do spodka prostopadłej (składowa wzdłuż ≠ 0), dostaje pełną odległość `hypot(w poprzek, wzdłuż)`. Przy zbieżnym minimum składowa wzdłuż jest 0 albo pomijalna.
- Obie poprawki działają tylko przy masywach, żeby teren M1 poza nimi został bit w bit (złoty test do K7). Ten sam błąd jest w M1: styczna {1, 0} ma każde jezioro bezodpływowe, więc zawraca każdy odcinek, który dopływa do niego od wschodu, a nieodnalezione minima zdarzają się na każdym krótkim, mocno wygiętym odcinku. Globalny eksperyment z pełną odległością (wszystkie odcinki, bez poprawki stycznej) dał w REAL 3687 par, prawie tyle co K0, i zmienił też okna poza masywami. **K4** ma przenieść styczną cięciwy na wszystkie jeziora bezodpływowe, a **K4b** pełną odległość (i bezpieczny krok `refine`) na wszystkie odcinki; obie zmiany zmieniają łaty i wymagają listy dozwolonych zmian.

**2. Kopuła ucięta granicą komórki regionu (GAMEPLAY).** Recenzja (drobna uwaga) podejrzewała, że zasięg kopuły wchodzi w komórki FOOTHILLS. Pomiar (`MassifWeights`): w GAMEPLAY 24 z 25 masywów ma w wierzchowinie (G > 0,3) wagę BESKIDS < 0,98, a 17 z 25 < 0,5; w REAL 3 z 9 < 0,98, żaden < 0,5. Pas BESKIDS w GAMEPLAY ma w poprzek 1–2 komórki po 1,4 km, a kopuła ok. 2,9 km. Pierwsza wersja podnosiła tylko komórki BESKIDS, więc kopuła kończyła się na granicy komórki ścianą: przekrój przez masyw (−249923, −109703) spadał z 1608 m (G 0,86) do 494 m (G 0,53) na 470 m. To „stół” A6, którego projekt na masywach nie przewidywał. Teraz kopuła działa także w komórkach FOOTHILLS (ta sama obwiednia docelowa, wypełnienie i pułap; poza zasięgiem nic się nie zmienia). Ten sam przekrój: 1641 → 853 m.

| Różnica wysokości komórek BESKIDS i FOOTHILLS w kolumnach mieszanych (obie wagi ≥ 0,05) | G 0,3–0,6 | G > 0,6 |
|---|---|---|
| GAMEPLAY, pierwsza wersja K2 | 1115 m | 1353 m |
| GAMEPLAY, po recenzji | 759 m | 594 m |
| REAL, pierwsza wersja K2 | 867 m | 1072 m |
| REAL, po recenzji | 502 m | 359 m |

Reszta to inna rzeźba fliszu obu typów (inny odstęp dolin i inna orientacja komórek), czyli A6 z M1 (M5). Wariant, w którym komórka FOOTHILLS w wierzchowinie przechodzi w rzeźbę BESKIDS, dawał 562/316 m, ale liczył w tych kolumnach obie rzeźby i dał więcej skoków w oknie `gameplay_massif_1660` (99 wobec 93), więc nie wszedł. Pary > 30 m na 10 m w `landElevation` na 25 masywach GAMEPLAY: K0 1728, pierwsza wersja K2 43 240, po recenzji 28 895. Nowy test: `LandscapeModelTest.greatMassifDomeSpansRegionTypes`.

Koszt tej poprawki: w komórkach FOOTHILLS w zasięgu doliny są głębsze, więc w dwóch oknach GAMEPLAY przybyło skoków A2 (niżej, „Testy lokalności i ciągłości”).

**3. Gęstsze punkty drogi w regule (b)** (recenzja, drobna uwaga): odstęp ≤ 0,25 Rc zamiast 7 punktów. W oknach testów nie zmienił żadnego wyniku pomiaru (`CliffScan` GAMEPLAY 9132 przed i po), ale zamyka przypadek długich dróg rzędu 3.

### Rzeki przy masywach

`RiverNetworkTest.noSpringsOnMassifCore`, wszystkie masywy z okien testu, siatka co 25 m (REAL) i 10 m (GAMEPLAY) na wierzchowinach:

| | REAL | GAMEPLAY |
|---|---|---|
| węzły rzędów 1–3 na wierzchowinie (żaden nie jest źródłem, żaden odcinek nie zaczyna się tam źródłem) | 201 | 618 |
| obszar szczytowy (G > 0,9): wcięcie najwyżej (próg) | 19,6 m (20) | 50,6 m (51) |
| szczelina (260024, −1538766): wcięcie (próg 50 m) | 0 (było 1022 m) | — |
| kolumny dna doliny lub wody na wierzchowinie (próg), najgłębsza | 1598 (1598), G 0,50 | 326 (326), G 0,36 |
| największe wcięcie wierzchowiny (próg) | 741 m przy G 0,41 (742) | 856 m przy G 0,30 (856) |

- **Kryterium projektu „wcięcie wierzchowiny < 50 m” jest źle postawione** (recenzja, drobna uwaga 1). Wcięcie (`landElevation` − powierzchnia) przy G 0,3–0,5 to stok kopuły nad doliną rzeki, która omija masyw: np. 741 m w (142763, −1473397) to ściana doliny rzeki rzędu 3, 60 m od skraju jej dna. Babia Góra też wznosi się ok. 1000 m nad dolinami u podnóża. Kanionem w kopule byłoby wcięcie obszaru szczytowego, a to jest teraz najwyżej 19,6 m (REAL) i 50,6 m (GAMEPLAY, ściana doliny potoku obok). Test pilnuje obu wartości na stanie zmierzonym.
- **Dna dolin na skraju wierzchowiny** biorą się z krzywych odcinków: reguła (b) sprawdza prostą drogę między węzłami, a krzywa (styczne, wędrowanie osi) długiego odcinka wybrzusza się do G 0,50 (odcinek źródłowy rzędu 2 w (142063, −1477272) na masywie (143663, −1476297)). K4 zmienia geometrię odcinków (G1B, R1), więc musi te liczby zmierzyć ponownie; jeśli wzrosną, trzeba sprawdzać wierzchowinę na krzywej przy budowie odcinka.
- Powrót do sąsiada o najmniejszym G na drodze zadziałał w oknach testu w jednym węźle: rząd 1, (255555, −1537853) przy masywie (258824, −1539366). Stara reguła prowadziła go przez szczyt (G na drodze 0,79), teraz idzie skrajem wierzchowiny (0,37).
- `RiverNetworkTest.massifStreamSourcesHaveNoCliffs` (nowy): źródła rzędu 1 na stokach masywów (G > 0 w źródle, do 12 na masyw), przekroje od t = −0,5 (za źródłem, na przedłużeniu osi) do 0,5, ±60 m·k. REAL: 108 źródeł, największy skok 1,46 m na 1 m (cel D4: < 3 m, spełniony). GAMEPLAY: 300 źródeł, 25,87 m na 1 m w (4989,7; −33301,9) przy masywie spawnu, skok `tSoft` krótkiego odcinka rzędu 1 (A2, K4b).

Jeziora bezodpływowe u podnóża (S5): zostają. Ich brzegi to ściana `applyLake` (`5000·smoothstep` w pasie 0,7–1,0 banku), którą usuwa K5.4 (A3, niecki bez ściany); patrz `massifSpotsHaveNoCliffs`.

### Złoty test

- Pierwsza wersja K2: `-PgoldenReport` przed dopisaniem łaty: 2 z 2, raport `# no changes`. Zapis z `-PwriteGolden -PgoldenKeepCenters`: tylko nowe wiersze `patch great_massif` i zmienione `field`/`total`.
- Po recenzji K2: raport pokazał zmianę tylko łat `great_massif` (wszystkie zestawy poza REAL A 0,5) i utratę celu w REAL B (szczyt przesunął się poza łatę przez kopułę w komórkach FOOTHILLS). Zapis z `-PgoldenKeepCenters`: wszystkie inne łaty zachowały środki i skróty; REAL B `great_massif` wyszukana na nowo, środek (−194683, 494076) zamiast (−194869, 494003). Potem 2 z 2 i `# no changes`. W nagłówku pliku wpis historii K2 uzupełniony.
- Łata `great_massif` (start `Start.GREAT_MASSIF` = `nearestGreatMassif(0, 0)`, krok 40 m REAL / 12 m GAMEPLAY, wyszukiwanie co 200 m / 20 m). Cel: kolumna BESKIDS ponad `AltitudinalBelts.LARGE_MASSIF` (1470 m) z formą SUMMIT. Środki: REAL A (98290, 1033389), REAL B (−194683, 494076), GAMEPLAY A (6854, −33757), GAMEPLAY B (−2271, −49969), REAL A 0,5 (96472, −734138).

### Testy lokalności i ciągłości

**`TerrainLocalityTest`:** wyjątek `exempt` = zasięg wielkiego masywu (`greatMassifStrength > 0`). Siatka `gameplay_massif_spawn` ma krok 50 m (bok 10 km) zamiast 40 m, bo z wyjątkiem porównywała 988 kolumn: teraz 2987 porównanych, 71 wyjętych. `realistic_massif_1723`: 18139 porównanych, 5981 wyjętych. Wszędzie 0 zmienionych kolumn poza dolinami, wodą i zasięgiem masywów.

**`TerrainDeterminismTest`:** dwa kadry na masywach (`gameplay_great_massif` (6854, −33757), 300 × 300 co 17 m, i `realistic_great_massif` (258824, −1539366) co 55 m): 0 różnych kolumn. Nowe pole `Segment.nearMassif` jest czystą funkcją odcinka i jest ustawiane przed publikacją odcinka w mapie (jak `minX` i inne pola ramki).

**`SurfaceContinuityTest`, okna masywów:**

| Okno | K0: skoki, największy | pierwsza wersja K2 | po recenzji K2 |
|---|---|---|---|
| `gameplay_massif_spawn` | 46, 22,3 m | 46, 22,0 m | 42, 22,0 m |
| `gameplay_massif_1660` | 54, 68,1 m | **79**, 69,5 m | **93**, 69,5 m |
| `gameplay_massif_1718` | 59, 28,5 m | 44, 19,5 m | 41, **58,0 m** |
| `gameplay_massif_1710` | 51, 39,3 m | 15, 15,7 m | 16, 15,7 m |
| `realistic_massif_1723` | 0 | **3, 68,5 m** | 0 |

- Okno REAL wróciło do stanu K0: skok 68,5 m był zawrotem odcinka przy nowym jeziorze bezodpływowym (wyżej), nie skokiem `tSoft`.
- Kryterium K2 („nie więcej skoków niż przed K2”) nie jest spełnione w dwóch oknach GAMEPLAY i po recenzji jest tam gorzej niż w pierwszej wersji. Kopuła w komórkach FOOTHILLS (poprawka 2) daje w nich głębsze doliny, a ten sam skok rzutu krótkiego odcinka rzędu 1 daje wtedy wyższe urwisko (np. 58 m w (−216918, 247864) przy G 0,07, ściana doliny odcinka rzędu 1 wcięta o 240 m). Bez tej poprawki okna wyglądałyby jak w pierwszej wersji (79 i 19,5 m). Uznałem to za mniejsze zło niż ściany do 1100 m na granicach komórek w 17 z 25 kopuł. Na wszystkich 25 masywach GAMEPLAY urwisk jest po recenzji mniej niż w K0 (7991 wobec 9884 par).
- Progi okien to stan po recenzji; cele K4b w opisach okien: 1660 ≤ 54, 1718 największy ≤ 28,48 m, D4: żaden skok > 3 m.

**`SurfaceContinuityTest.massifSpotsHaveNoCliffs`** (nowy): miejsca urwisk z recenzji, siatka (REAL co 5 m, bok 2–2,2 km; GAMEPLAY co 2,5 m, bok 1 km), pary suchych sąsiadów z |Δh| > 25 m:

| Miejsce | K0 | pierwsza wersja K2 | po recenzji (próg) | Właściciel |
|---|---|---|---|---|
| `realistic_box_canyon_1698` (144478, −1474422) | 0, 6,9 m | 334, 377,9 m | 0, 5,6 m | — |
| `realistic_slot_1700` (260024, −1538100) | 0, 6,2 m | 1915, 1021,6 m | 0, 10,2 m | — |
| `realistic_sink_lake_1723` (150900, −1525100) | 0, 7,8 m | 313, 151,1 m | 231, 45,9 m | K5.4 (brzeg jeziora) |
| `realistic_sink_lake_1659` (518000, −1404342) | 0, 6,3 m | 161, 100,6 m | 148, 97,3 m | K5.4 (brzeg jeziora) |
| `gameplay_blades_1707` (91727, 169377) | 0, 12,9 m | 321, 83,1 m | 320, 83,1 m | K4b (A2) |
| `gameplay_fan_1673` (128900, 76850) | 4, 27,8 m | 861, 89,8 m | 535, 83,1 m | K4b (A2) |

W miejscach jezior zostały ściany brzegów nowych jezior bezodpływowych (`applyLake`, sonda bez wcięcia żadnego odcinka), a w GAMEPLAY skoki `tSoft` (np. (129149, 76657): t 0,125 → 0,029 na 2,5 m, odległość od osi 287 → 327 m przy ścianie doliny 550 m).

### Koszt

`SampleCostTest` ma trzeci obszar na skalę: 400 chunków na wielkim masywie (REAL (147582, −1525292), GAMEPLAY (6854, −33757), połowa boku Ra). Kopia M1 nie ma tam masywu, więc stosunek to koszt masywu razem z `PeakField`.

Pomiar A/B (`SampleCostTest`, 7 rund; drzewo pierwszej wersji K2 i drzewo po recenzji skompilowane osobno, na zmianę, po 3 uruchomienia; mediany z trzech median, µs na kolumnę, w nawiasie stosunek do kopii M1 w tej samej JVM). Przed K2 (K0 + K1, pomiar A/B pierwszej wersji K2): REAL cały obszar 3,94 µs (1,018), Beskidy 6,41 µs (1,001), GAMEPLAY 5,51 µs (1,022) i 9,50 µs (0,989).

| Obszar | K2, pierwsza wersja | K2 po recenzji |
|---|---|---|
| REAL cały obszar | 4,04 (1,023) | 4,00 (1,021) |
| REAL Beskidy | 6,47 (1,001) | 6,53 (1,009) |
| REAL wielki masyw | 6,08 (0,904) | 6,00 (0,898) |
| GAMEPLAY cały obszar | 5,53 (1,014) | 5,49 (1,017) |
| GAMEPLAY Beskidy | 9,61 (0,991) | 9,50 (0,985) |
| GAMEPLAY wielki masyw | 10,05 (0,930) | 9,81 (0,910) |

- Poprawki recenzji nie zmieniły kosztu (różnice w granicach rozrzutu). Pierwsza wersja poprawek kosztowała w GAMEPLAY +5–7 p.p. (cały obszar 1,063, Beskidy 1,057): pełna odległość liczona przez `Math.hypot` przy każdym minimum odcinka przy masywie (cztery wywołania; składowa wzdłuż prawie nigdy nie jest dokładnie 0) i `greatMassifAt` w każdej komórce fliszu osobno. Teraz pełna odległość tylko przy składowej wzdłuż > 1 mm, przez `Math.sqrt`, a `greatMassifAt` raz na kolumnę (`greatMassifFor`). Złoty test po tej zmianie: `# no changes`.

- Pierwsza wersja (3 × 3 oczka w każdym `greatMassifAt`, mapa także dla oczek bez kandydata) kosztowała w Beskidach ok. +2 p.p. Teraz `greatMassifAt` pyta tylko oczka, których kandydaci mogą sięgnąć punktu (w REAL zasięg 9,6 km jest krótszy niż 0,2 oczka, więc tylko własne oczko), a `greatMassifCell` odrzuca oczko po losie `u1` bez pamięci podręcznej.
- Na masywie `sample` jest tańsze niż w M1 (0,90–0,94) mimo `PeakField` ponad 1180 m: na wierzchowinie nie ma źródeł, więc odcinków rzek jest mniej (raport szczytów: −31%).

### Odstępstwa od projektu w K2

1. **Obniżanie obwiedni tylko w miarę wypełnienia dolin** (strojenie S3). Projekt: `lift = G·max(0, T − obwiednia)`. Pierwsza wersja K2: podniesienie ze znakiem w całym zasięgu (szczelina 1 km, wyżej). Po recenzji: obniżanie z wagą `smoothstep(0,3; 0,9; G)`. „Bit w bit jak w projekcie” dotyczy tylko kolumn z obwiednią < T. `GM_FILL0/1` zabierały według recenzji projektu połowę kosodrzewiny innym masywom; pułap zależny od G obniżał szczyty poza środkiem kopuły.
2. **Pułap 35 m zamiast 40 m** (`GM_CAP`), strojenie S3: inaczej jeden masyw GAMEPLAY ma stosunek 0,37 > 0,35.
3. **Powrót reguły (b)**: sąsiad o najmniejszym G na drodze zamiast starej reguły. Stara reguła prowadziła jedyny taki węzeł w oknach testu przez szczyt.
4. **`describe` z wypełnieniem dolin na masywie** (nie było w projekcie), potrzebne dla celu łaty `great_massif` (SUMMIT) i zgodne z terenem.
5. **Kryteria wierzchowiny w `noSpringsOnMassifCore`**: brak źródeł, obszar szczytowy (G > 0,9) wcięty najwyżej o 20 / 51 m, szczelina (260024, −1538766) ≤ 50 m, a wcięcie i dna dolin na całej wierzchowinie na stanie zmierzonym, zamiast „wcięcie wierzchowiny < 50 m” (wyżej: to stok kopuły nad doliną, nie kanion).
6. **`greatMassifsReachTarget`**: „szczyty poza masywami bez zmian (mediana 946 ± 5 m)” sprawdza bezpośrednio: `landElevation` równe kopii M1 w 10 000 losowych punktach Beskidów poza zasięgiem masywów w każdym oknie (mocniejsze niż mediana). Liczy też odstęp środków masywów (rozłączność zasięgów) i zgodność `nearestGreatMassif` z oknem.
7. **Okna masywów w `SurfaceContinuityTest`**: progi to stan zmierzony po recenzji, nie „bez masywu + 2”. Dwa okna GAMEPLAY są gorsze niż w K0 (wyżej).
8. **Siatka masywu GAMEPLAY w `TerrainLocalityTest`** ma krok 50 m (bok 10 km), bo z wyjątkiem masywu porównywała 988 kolumn.
9. **25 masywów GAMEPLAY zamiast 26**: skutek pełnego przerzedzenia (recenzja projektu), nie strojenia.
10. **S2 (masywy typu Pilska, D6)** odłożone. Rozkład szczytów jest nadal dwumodalny (brak szczytów 1452–1618 m). Dodanie rzadszej klasy masywów 1550–1600 m zmieni łatę `great_massif` tylko wtedy, gdy masyw najbliższy (0, 0) się zmieni; trzeba to zrobić z pomiarem pięter, najlepiej po K7 (procedura S2/S3 z §4 projektu).
11. **Kadry**: `HabitatPreview` ma kadry `great_massif_16km`, `gameplay_great_massif_5km` (§5.3 projektu) i po recenzji `highest_great_massif_16km` (najwyższy masyw okna, ziarno 20260927). Kadr `high_beskids_10km` zostaje z dawnym wyszukiwaniem (siatka 5 km trafia na masyw 1637 m, a nie na najwyższy 1723 m), żeby dało się porównywać kadry przed i po.
12. **Kopuła także w komórkach FOOTHILLS** (po recenzji). Projekt zakładał, że A6 nie dotyczy masywów, bo waga BESKIDS w środku ≥ 0,98. W GAMEPLAY to nie wystarcza (wyżej).
13. **Styczna cięciwy na końcu odcinka w jeziorze bezodpływowym i pełna odległość nieodnalezionego minimum rzutu — tylko przy masywach** (po recenzji). To poprawki błędów M1, ale K2 nie może zmienić terenu M1 poza masywami, więc działają tylko tam, gdzie K2 i tak zmienia teren. K4 i K4b muszą je uogólnić (wyżej).
14. **Gęstsze punkty drogi w regule (b)** (po recenzji): co najwyżej 0,25 Rc zamiast 7 punktów.

### Co zostaje

- **K4b (D4), z konkretnymi kryteriami:** okna `gameplay_massif_1660` ≤ 54 skoki i `gameplay_massif_1718` największy skok ≤ 28,48 m (stan K0), cel D4 żaden skok > 3 m we wszystkich oknach masywów; `massifSpotsHaveNoCliffs` `gameplay_blades_1707` i `gameplay_fan_1673` bez par > 25 m; `massifStreamSourcesHaveNoCliffs` w GAMEPLAY < 3 m na 1 m. Mechanizm: skok `tSoft` między gałęziami rzutu krótkich odcinków rzędu 1 i minima, których `refine` nie doprowadza do spodka prostopadłej (t zostaje na brzegu przedziału, np. 2/16 i 15/16). K4b ma też przejąć dla wszystkich odcinków pełną odległość nieodnalezionego minimum (dziś tylko przy masywach).
- **K4:** styczna cięciwy na końcu odcinka w każdym jeziorze bezodpływowym (dziś tylko przy masywach; w M1 ten zawrót tworzy klinowe doliny przy jeziorach); ponowny pomiar den dolin na wierzchowinach (`noSpringsOnMassifCore`).
- **K5.4 (A3):** ściany brzegów jezior bezodpływowych u podnóża masywów (`realistic_sink_lake_1723`, `realistic_sink_lake_1659`: cel 0 par > 25 m).
- Jeziora bezodpływowe u podnóża masywów (S5): w 9 kwadratach masywów REAL woda jezior wzrosła z 5,91 do 6,86 km² (pomiar recenzji K2, pierwsza wersja).
- Reszta A6 na masywach GAMEPLAY (różnica rzeźby fliszu obu typów, do 594 m przy G > 0,6): M5.
- Komenda `find` dla kosodrzewiny i hali w GAMEPLAY (§5.3 projektu) i dokumentacja w `docs/01-architektura.md` §13 oraz `docs/03-m2-biomy.md` (odstępstwo S4, §5.1): K7.
- Pole ≥ 1650 m masywu GAMEPLAY (−177226, 30624) ma stosunek 0,34 przy progu 0,35. `landElevation` zmieni jeszcze tylko K6 (A16, szwy regionów), więc K6 musi ten test uruchomić.
