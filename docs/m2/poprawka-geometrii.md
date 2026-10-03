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

## K3. Ciągłe pola doliny dominującej (F1), pas meandrów bez progu nizin (TE)

Stan przed krokiem: commit `2546ec3` (K0–K2), czyste drzewo robocze, pusta lista `git stash`.

**Problemy.**

- **P2–P4.** Dolinę dominującą w kolumnie wybierał najmniejszy iloraz `floorDist / floorHalf`. W pasie meandrów iloraz jest 0 dla każdej doliny, więc remis rozstrzygała kolejność listy odcinków (dopływ wygrywał w pasie w poprzek dna dużej rzeki), a poza pasem granica przełączenia była parą prostych przez punkt przecięcia brzegów den. Na tych prostych skakały `streamOrder`, `channelGradient`, `floorHalfWidth`, `u` i `valleyWeight`, a z `valleyWeight` także wygaszanie rynien i istnienie oczek.
- **P7.** Szerokość pasa meandrów w dnie włączała się skokiem przy udziale nizin 0,3 (`lowland > 0.3 ? 1.4·lowland : 0`). Izolinia udziału nizin biegnie równolegle do prostej granicy komórek, więc w dolinach powstawały proste uskoki terenu.

### Co weszło

Logika z prototypu `PW/src3` (`-Ddno.fix=F1,TE`), bez przełączników, w `RiverNetwork.query`:

- **TE:** `meanderBeltFactor(lowland) = 1 + 1,4·lowland·smoothstep(0,2; 0,4; lowland)`, liczone raz na kolumnę. Ten sam wzór ma `segmentsAt` (pomiar F2 w testach).
- **F1:** klucz `floorHalf − floorDist` (metry w głąb dna). Suchy koniec źródłowy (wygaszenie ≤ 0,5) ma klucz obcięty poniżej zera (`RiverNetwork.f1Key`, po recenzji K3; pierwsza wersja odejmowała 1e5 m jak projekt), więc nigdy nie wygrywa z dnem. Dolina dominująca to największy klucz; remis rozstrzyga szersze dno, potem kolejność listy kafla.
- `valleyWeight` to maksimum po odcinkach, `u` minimum po dnach zawierających kolumnę, a `floorHalfWidth` i `channelGradient` to miękkie maksimum z wagami exp((klucz − maksimum)/τ), τ = 15 m·k. Sumy obejmują tylko odcinki w pasie 8τ od najgłębszej doliny; przy nowym maksimum sumy są przeskalowane albo (gdy nowe maksimum jest wyżej o więcej niż 8τ) wyzerowane, jak w prototypie. `streamOrder` zostaje dyskretny (z doliny dominującej).
- Starorzecza, pole `source` i F2 (koryto doliny dominującej) liczą się od nowej doliny dominującej. Warunek spadku starorzeczy bierze, jak w prototypie, spadek odcinka doliny dominującej, a nie miękkie maksimum.
- Optymalizacja z K4.11, bez zmiany wyniku: maksimum `valleyWeight` i minimum `u` tylko przy `floorDist < floorHalf + 200 m·k` (dalej waga odcinka jest 0), spadek odcinka tylko w pasie 8τ.
- Stałe `F1_TAU`, `F1_SOFT_BAND`, `F1_HEAD_GAP` (po recenzji; wcześniej `F1_HEAD_PENALTY`). Javadoc `RiverHit`, `ColumnSample.Waters` i `candidates`: kolejność listy kafla rozstrzyga też obcięcie miękkiego maksimum, więc nie wolno jej zmieniać.

### Złoty test

`./gradlew test --tests '*GoldenTerrainTest*' -PgoldenReport=build/golden_K3.txt`: zmieniła się tylko łata `grid` w zestawach REAL B, GAMEPLAY A, GAMEPLAY B i REAL A 0,5 (REAL A bez zmian), żadna łata nie straciła celu. Wobec kopii M1: max|Δsurface| 0,0031 / 0,045 / 0,695 / 0,0005 m, różne kolumny: podłoże 1 / 1 / 0 / 0, bloki 0 / 1 / 1 / 0, poziom i rodzaj wody 0. Zgadza się to z pomiarem prototypu (`dna-dolin` §6.2: F1 zmienia 2 łaty, TE 4, wszystkie `grid`). Łaty kontrolne, `great_massif` i wszystkie łaty „=” bez zmian.

Lista `src/test/golden-allow/K3.txt`: `* / grid`. Z nią `terrainMatchesGolden` przechodzi (2 z 2).

### Testy

**Nowy `RiverNetworkTest.noStepAtLowlandThreshold`** (projekt §3.2). REAL ±60 km co 150 m wzdłuż x, a od recenzji K3 także GAMEPLAY ±10 km co 25 m: każde przejście udziału nizin (z pobrzeżem, jak w `query`) przez 0,3 jest bisekcją zawężone do pary kolumn kilka µm od siebie; para sucha nie może różnić się o więcej niż 0,5 m. Przejścia, w których skacze sam udział nizin (o więcej niż 10⁻⁴ między kolumnami pary: szew okna 3 × 3, A16, K6), nie są przejściami progu i są tylko liczone. Ta sama siatka na zamrożonej kopii M1 pokazuje, że test widzi stare uskoki.

| | Przejścia (suche) | Uskoki > 0,5 m | Największy | Szwy mieszania |
|---|---|---|---|---|
| REAL, kopia M1 | 988 (986) | 158 | 56,8 m w (686,5; 29400) | 0 |
| REAL, K3 | 988 (986) | **0** | — | 0 |
| GAMEPLAY, kopia M1 | 848 (843) | 200 | 7,85 m w (4990,4; −375) | 1 |
| GAMEPLAY, K3 | 848 (843) | **0** | — | 1 |

Szew GAMEPLAY w (5727,8; 2725): udział nizin skacze tam z 0,2904 na 0,3000, co daje 0,56 m różnicy powierzchni. Pierwszy próg szwu (0,01) go nie łapał, więc test zgłaszał go jako uskok TE; próg 10⁻⁴ (przy odstępie µm ciągły udział zmienia się o ok. 10⁻⁹) liczy go jako szew.

Analiza den dolin podawała „15 uskoków (5–20 m)”, bo narzędzie `DnoProg` zatrzymywało się po 15 znaleziskach.

**`TerrainLocalityTest`:** 0 zmienionych kolumn poza dolinami, wodą i zasięgiem masywów na wszystkich siatkach. Siatka `gameplay_outwash_plain` ma teraz 2333 kolumny (5,8%) różne od kopii M1, wszystkie wcięte albo z wodą (doliny obok łaty kontrolnej, poza nią).

**`TerrainDeterminismTest`:** 0 różnic w polach F2 i poza jeziorami rynnowymi; znany wyjątek jezior rynnowych bez zmian (2531 i 12143 kolumn, do K5.2).

**`SurfaceContinuityTest`** (progi zaostrzone do stanu K3):

| Okno | K2 | K3 |
|---|---|---|
| `gameplay_beskids` | 43, 36,4 m | 41, 36,4 m |
| `gameplay_center` | 21 (14 przy wodzie) | 19 (14) |
| `gameplay_stream` | 43, 13,6 m | 37, 13,6 m |
| `gameplay_moraine` | 20 (16) | 19 (16) |
| `gameplay_massif_1660` | 93, 69,5 m | 87, 69,5 m |
| pozostałe okna | | bez zmian |

Największe skoki się nie zmieniły: to skoki rzutu krótkich odcinków rzędu 1 (A2, K4b) i wody stojące (K5).

**Ciągłość pól doliny** (narzędzie `FieldSeams` w katalogu roboczym sesji, `k3ab`): siatka 800 × 800 na sześciu kadrach, pary sąsiednich suchych kolumn w dolinie. Para z różnicą > 1% (`floorHalfWidth`, `channelGradient`) albo > 0,02 (`u`) jest zawężana bisekcją 24 razy; nieciągłość to różnica, która zostaje przy odstępie 0,1–0,2 µm.

| Kadr | K2: `floorHalf` / spadek / `u` / rząd | K3 (kara 1e5) | K3 po recenzji (`f1Key`) |
|---|---|---|---|
| REAL zbieg (−18844, 11206), 1,5 km | 593 / 593 / 0 / 593 | 0 / 0 / 0 / 294 | 0 / 0 / 0 / 294 |
| GAMEPLAY dolina dużej rzeki (−10323, −33338), 1 km | 4991 / 5018 / 0 / 2060 | 282 / 703 / 0 / 1603 | 18 / 279 / 0 / 1470 |
| REAL dolina dużej rzeki (−5203, 93856), 2 km | 5 / 0 / 0 / 5 | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 |
| REAL zbieg (−23749, −21820), 1,5 km | 1202 / 1202 / 0 / 447 | 0 / 243 / 0 / 138 | 0 / 243 / 0 / 138 |
| GAMEPLAY zbieg (−3268, −5357), 800 m | 4151 / 4153 / 0 / 2143 | 432 / 3093 / 0 / 2246 | 2 / 3388 / 0 / 2091 |
| REAL próg nizin (0, 29400), 3 km | 3473 / 3473 / 120 / 2851 | 815 / 2147 / 0 / 2097 | 31 / 1603 / 0 / 2259 |

Rząd zostaje dyskretny z założenia. W pierwszej wersji K3 (kara 1e5 m jak w projekcie) wszystkie pozostałe nieciągłości `floorHalfWidth` i część nieciągłości spadku leżały na linii wygaszenia źródłowego 0,5. **To nie było „jak w M1”**: w M1 skakały tam tylko `inFloor` i `u`, a kara oddawała całą suchą głowicę (i obszar za źródłem) dowolnej niesuchej dolinie w zasięgu, nawet odległej, więc rząd, `headwaters`, szerokość dna i spadek skakały na prostej linii wygaszenia w poprzek każdej głowicy. Wykryła to recenzja K3; poprawka jest niżej („Poprawki po recenzji K3”). Po niej na linii wygaszenia zostaje kilkadziesiąt nieciągłości tam, gdzie głowica nachodzi na dno innej doliny (start dna, A5 w K4). Reszta nieciągłości spadku („inne” w `FieldSeams`) to obcięcie miękkiego maksimum na skraju pasa 8τ: odcinek o spadku kilkadziesiąt razy większym wchodzi z wagą e^−8 ≈ 3·10^−4, co daje skok o kilka procent względnie, ale rzędu 0,01‰ bezwzględnie, czyli bez znaczenia dla progów klas cieków. Po poprawce takich miejsc jest w części kadrów więcej (suche głowice wchodzą teraz do miękkiego maksimum z kluczem tuż poniżej zera), ale to te same znikome skoki.

**F2 (`WatersideZonesTest.floorZonesFollowDominantRiver`), progi od K3** (`F2_SHARE_ENFORCED = true`):

| | REAL K1 | REAL K3 | GAMEPLAY K1 | GAMEPLAY K3 |
|---|---|---|---|---|
| Kolumny w zasięgu łęgu topolowego | 736 | 736 | 288 | 285 |
| W nich strefy rzeki (próg 95%) | 100,0% | 100,0% | 99,7% | 95,8% → **99,6%** po recenzji |
| Całe dno: rzeka znaleziona | 98,0% | **99,1%** | 88,6% | **92,9%** |
| Całe dno: P2 | 764 (2,0%) | 330 (0,9%) | 533 (11,4%) | 337 (7,1%) |

W pierwszej wersji K3 12 kolumn GAMEPLAY w zasięgu łęgu topolowego było „innych” (koryto doliny dominującej to rzeka, ale strefa inna niż wiklina i łęg wierzbowo-topolowy), a nie P2. Diagnoza po recenzji (klasa `F2Other` w katalogu roboczym sesji, `k3rev/fix`): 11 z nich to kolumny u ujścia dopływu, w których miękkie maksimum `channelGradient` miesza stromy dopływ (3,07–5,04‰ przy spadku rzeki 0,6‰), więc reguła F2 `streamClass(…) == A` dawała rzece klasę C i kolumna wracała do stref najbliższego koryta. Poprawka: F2 klasyfikuje koryto doliny dominującej jego własnym spadkiem (`floorChannelGradient`, niżej). Została 1 kolumna: plaża przy ujściu do morza (−5161,6; −7402,4), słusznie. Pozostałe wiersze tabeli po recenzji bez zmian. Bufor kandydatów F2: najwyżej 25 / 28, 0 przepełnień.

**Dlaczego całe dno GAMEPLAY nie osiąga 95%** (warunek z K1: „opisać przyczynę, a nie tylko obniżyć próg”). Diagnostyka (tymczasowa klasa testowa `K3F2Diag`, usunięta po pomiarze; kadry kolumn całego dna w katalogu roboczym sesji, `k3diag`): pozostałe kolumny P2 to trójkąty u ujść dopływów, które F1 zostawia z założenia (projekt: „przełączenie zostaje tylko w małym trójkącie u ujścia dopływu, o rozmiarze około półszerokości jego dna”). Na skraju dna rzeki kolumna leży głębiej w dnie dopływu niż w dnie rzeki: klucz dopływu `floorHalf_d − floorDist_d` (w jego pasie meandrów po prostu `floorHalf_d`) przewyższa klucz rzeki `floorHalf_r − floorDist_r` w pasie o szerokości ok. `floorHalf_d` przy skraju dna rzeki. Rozkład względnej głębokości w dnie rzeki `(floorHalf_r − floorDist_r)/floorHalf_r`:

| Głębokość w dnie rzeki | 0–0,1 | 0,1–0,2 | 0,2–0,3 | 0,3–0,4 | 0,4–0,5 | 0,5–0,6 | 0,6–0,7 | 0,7–0,8 | ≥ 0,8 |
|---|---|---|---|---|---|---|---|---|---|
| REAL: P2 / rzeka | 125 / 1100 | 85 / 1175 | 45 / 1264 | 20 / 1303 | 18 / 1352 | 16 / 1378 | 13 / 1350 | 8 / 1397 | 0 / 27 285 |
| GAMEPLAY: P2 / rzeka | 96 / 324 | 77 / 334 | 61 / 320 | 46 / 330 | 26 / 327 | 17 / 318 | 10 / 318 | 4 / 298 | 0 / 1823 |

W GAMEPLAY dno dopływu rzędu 1–2 jest szerokie względem dna rzeki (przy wąskich korytach przeważa podstawa `fpBase`), więc trójkąty sięgają głębiej, a mierzony pas (kolumny bliżej dopływu niż rzeki, za pasem OlJ dopływu) jest wąski. Dominującą doliną kolumn P2 jest dopływ rzędu 2 (REAL 330 z 330, GAMEPLAY 303 z 337) albo 1 (GAMEPLAY 34). Próg całego dna: REAL 95% (bez zmian), GAMEPLAY 92% (zmierzone 92,9%). Usunięcie trójkątów wymagałoby zmiany F2 (koryto największej doliny, w której dnie leży kolumna, zamiast rzędu doliny dominującej), czyli nowej kalibracji stref; to M5 razem z N4.

Trójkąty ujść nie są jedynym źródłem klinów w dnie GAMEPLAY (recenzja K3). W zbiegu (−3268, −5357) w dnie rzeki klasy A leżą kliny boru wilgotnego o prostych krawędziach, ok. 100–150 m, niezmienione od K2: F2 wybiera tam jako koryto doliny dominującej wąskie równoległe koryto rzędu 3 (W 3,0 m), np. (−3317,4; −5446,7): rząd 3, `inFloor`, u 0,71, koryto F2 W 3,0 m w odległości 63 m, więc obowiązują strefy małego cieku daleko od kolumny. Równoległe koryta rzędu 3 w jednym dnie (A9/N3) to drugie źródło prostych klinów, do M5.

Próg 92% ma margines 0,9 p.p. **K4 musi ten udział zmierzyć ponownie** (G3 poszerza ujścia dopływów, G4 zmienia skraj dna) i wyjaśnić każdą zmianę, a nie obniżać progu dalej.

**Kadry recenzji K1** (`viz_K1`: te same 14 kadrów, `render.sh`, mapy F2 `VizF2 fields`, skrypt `newedges_k3.py`; nowe granice biomów wobec stanu sprzed K1 na granicy gałęzi F2):

- REAL `R_conf_18844_11206_1500m`: klin olsu wzdłuż dopływu z dwiema prostymi krawędziami 431 m i 304 m zniknął. Zostały krótkie odcinki 189 m i 77 m przy wejściu doliny dopływu w dno rzeki (trójkąt ujścia i prosty skraj dna rzeki, P9). Razem 750 → 281 m.
- GAMEPLAY `gameplay_large_river_valley_1km`: pas boru wilgotnego i boru bagiennego o prostych, równoległych krawędziach wzdłuż wąskiego koryta rzędu 3 (W 2,4 m) zniknął; F2 bierze teraz koryto rzeki po obu stronach wąskiego koryta. Krótki klin przy dopływie SE też zniknął. Nowe granice na granicy F2: 2468 → 1572 m; reszta leży na skraju dna rzeki (strefy rzeki kończą się na skraju dna, który jest prosty do G4 w K4).
- Pozostałe kadry: nowe granice na granicy F2 tam, gdzie F1 przeniosło dno pod rzekę główną. Najdłuższe (`R_conf_23749_21820_1500m`, 2089 i 1740 m) to falisty skraj pasa meandrów równoległego cieku w dnie rzeki; nie są proste (biegną wzdłuż pasa), ale wydłużają sumę (`R_overview_12km`: 2220 → 3735 m). Kryterium K1 „długość nowych granic na granicy F2 prawie zero” jest więc spełnione tylko w dwóch wskazanych miejscach; reszta to skraje den (P9, K4) i trójkąty ujść (wyżej).

**`WatersideZonesTest`, próba olsu:** REAL 155 cięciw, 80,0% ≥ 10 bloków (K1: 157, 80,3%); GAMEPLAY 179 cięciw, 59,2% (K1: 189, 58,7%). Liczba cięciw GAMEPLAY spadła o 9% wobec K0 (196), daleko od progu „o połowę”.

### Koszt

Pomiar A/B jak w K1 i K2: `SampleCostTest` z drzewa K2 (`HEAD` = `2546ec3`) i K3, osobne JVM na zmianę, 3 powtórzenia po 7 rund (katalog roboczy sesji, `k3ab/ab_run1.log`). Mediany z trzech median, µs na kolumnę (stosunek do kopii M1 w tej samej JVM):

| Obszar | K2 | K3 |
|---|---|---|
| REAL cały obszar | 4,03 (1,040) | 4,05 (1,035) |
| REAL Beskidy | 6,53 (1,008) | 6,61 (1,017) |
| REAL wielki masyw | 6,07 (0,902) | 6,11 (0,916) |
| GAMEPLAY cały obszar | 5,63 (1,031) | 5,64 (1,040) |
| GAMEPLAY Beskidy | 9,57 (0,983) | 9,65 (0,998) |
| GAMEPLAY wielki masyw | 9,99 (0,923) | 10,00 (0,922) |

K3 kosztuje 0–1,5%, mniej niż „sam F1” w analizie den dolin (do +3,5 p.p.), bo `exp` liczy się tylko w pasie 8τ, a maksimum i minimum pomijają odcinki daleko od dna. Wszystko w budżecie D1 (+20%; 6,5 / 12 / 8,5 / 16 µs). W pełnym `./gradlew test` (jedna JVM po innych klasach, rozrzut kilku punktów, K1) `SampleCostTest` dał 1,090 / 1,005 / 0,910 / 1,058 / 1,032 / 0,911 (4,41 / 6,46 / 6,16 / 5,94 / 10,06 / 10,17 µs) w kolejności tabeli.

**Pełny `./gradlew test`:** 127 testów, 1 porażka zamierzona: `terrainMatchesGolden` bez `-PgoldenAllow` (cztery łaty `grid`, jak wyżej). Z `-PgoldenAllow=src/test/golden-allow/K3.txt` złoty test przechodzi 2 z 2.

### Poprawki po recenzji K3

**Sucha głowica doliny (F1).** Kara 1e5 m z projektu sprawiała, że sucha głowica (pierwsze 200 m·k od źródła i obszar za t = 0) przegrywała z każdą niesuchą doliną w ramce, nie tylko z dnem, choćby ta dolina była daleko. Rząd, `headwaters`, szerokość dna i spadek brały się wtedy z obcej doliny i wracały skokiem na prostej linii wygaszenia 0,5 w poprzek każdej głowicy. Przykład GAMEPLAY (−3237,4; −5647,0), 0,6 m od koryta dopływu: K2 rząd 2, dno 28,3 m, spadek 6,0‰; pierwsza wersja K3 rząd 3, 47,2 m, 0,016‰ z rzeki 265 m dalej, strefa wiklina → ziołorośla na pasie ok. 100 m. Teraz klucz suchej głowicy jest obcięty poniżej zera (`RiverNetwork.f1Key`): `kh − δ` poza jej dnem i `−δ²/(δ + kh)` w nim, δ = `F1_HEAD_GAP` = 1 m·k, `kh = floorHalf − floorDist`. Jest ciągły i rosnący w `kh`, zawsze ujemny, więc głowica nadal nigdy nie wygrywa z dnem (klucz dna > 0, zostaje równoważność `inFloor` ⇔ `uMin`), ale zachowuje swoją głowicę i obszar za źródłem wobec dolin, których dno jest dalej, jak w M1.

- Teren bez zmian: `best` wpływa na teren tylko przez starorzecza, które wymagają `inFloor`, a w dnie dolina dominująca jest ta sama co przy karze 1e5 (suche głowice mają klucz < 0 < klucz dna). Złoty test: te same cztery skróty łat `grid` co przed recenzją.
- GAMEPLAY (956, 725) ±1 km co 4 m (sonda `HeadProbe`, `k3rev/fix`): rząd różny od K2 w 41 534 kolumnach w pierwszej wersji K3, teraz w 2297 (zmiany F1 przy dnach); klasa spadku (próg 3‰) różna od K2 w 17 068 → 4051 kolumnach; kolumny `headwaters` K2 163 636, K3 122 165, teraz 163 000; `inFloor` identyczne z pierwszą wersją K3.
- Przykład z recenzji (420..456; 1293) GAMEPLAY: znowu rząd 1 i 45‰ (pierwsza wersja K3: rząd 3, 0,6‰), jak w K2.
- `FieldSeams`: nieciągłości na linii wygaszenia `floorHalf` 282 → 18, 432 → 2, 815 → 31 (tabela wyżej).

**Klasa koryta F2 jego własnym spadkiem.** Nowe pole `floorChannelGradient` (`RiverHit`, `ColumnSample.Waters`; bufor kandydatów F2 ma teraz 5 wartości na odcinek, `FLOOR_STRIDE`): spadek odcinka koryta doliny dominującej, a nie miękkie maksimum. Reguła F2 w `WatersideZones.stream` klasyfikuje nim koryto (`streamClass(c, wr, gradient)`). Kolumny „inne” F2 w GAMEPLAY: 12 → 1 (plaża), strefy rzeki w zasięgu łęgu topolowego 95,8% → 99,6%. `HabitatClassifierTest.floorZonesFollowTheChannelOfTheDominantValley` sprawdza oba kierunki (miękkie maksimum 5‰ przy rzece 0,3‰ daje strefy rzeki; strome koryto doliny dominującej oddaje strefy najbliższemu korytu).

**`noStepAtLowlandThreshold` w obu skalach** (tabela wyżej) i próg szwu mieszania 10⁻⁴.

**Okno `gameplay_tunnel_lake_3519`** w `SurfaceContinuityTest` (GAMEPLAY (3519, −4356), r 300 m): wiszące jezioro rynnowe. Lustro 121 m trzyma wał szerokości 1–2 px nad dnem doliny rzeki na 32–37 m, suchy uskok ok. 86 m. Wada z M1 (K2: 89 m), którą K3 przesunął: `valleyWeight` = maksimum obniża tam `tunnelPresence`, jezioro traci 8132 px wody, a nowy brzeg SW to prosta ściana ok. 170 m. Pomiar: 23 skoki, wszystkie przy wodzie stojącej, największy 85,83 m; progi 25 / 88 m / 25 z zapasem na zależny od kolejności poziom jezior rynnowych. K5.2 (`floorGap`, jezioro kończy się przed doliną) ma je usunąć; cel okna: 0.

**Koszt po recenzji.** A/B jak wyżej (`k3ab/ab2.py`, `ab2_run.log`): pierwsza wersja K3 i K3 po recenzji, 3 powtórzenia po 7 rund, mediany z trzech median w µs na kolumnę (stosunek do kopii M1). REAL cały obszar 4,09 (1,038) → 4,07 (1,028), Beskidy 6,57 (1,018) → 6,61 (1,026), wielki masyw 6,08 (0,909) → 6,11 (0,913); GAMEPLAY cały obszar 5,61 (1,028) → 5,66 (1,034), Beskidy 9,61 (0,993) → 9,70 (1,003), wielki masyw 9,96 (0,926) → 10,12 (0,932). Różnice 0–1,6%, w granicach rozrzutu, w budżecie D1. Pełne `./gradlew test` po recenzji: `SampleCostTest` 1,022 / 0,936 / 0,848 / 0,987 / 0,949 / 0,879 i 1,045 / 0,936 / 0,845 / 1,002 / 0,940 / 0,872.

**Pełny `./gradlew test` po recenzji:** 128 testów (nowe okno ciągłości), 1 porażka zamierzona: `terrainMatchesGolden` bez `-PgoldenAllow`, te same cztery łaty `grid` z tymi samymi skrótami co przed recenzją. Z `-PgoldenAllow=src/test/golden-allow/K3.txt` złoty test przechodzi.


### Odstępstwa od projektu w K3

1. **Próg F2 całego dna w GAMEPLAY 92% zamiast 95%.** Próg 95% całego dna nie pochodzi z projektu, tylko z K1 (odstępstwo 4 K1). Przyczyna i rozkład wyżej: trójkąty ujść, które F1 zostawia z założenia. Próg z projektu (strefy rzeki w zasięgu łęgu topolowego ≥ 95%) obowiązuje w obu skalach i jest spełniony.
2. **Test `noStepAtLowlandThreshold` skanuje wszystkie przejścia progu** (`DnoProg` zatrzymywał się po 15), liczy osobno szwy mieszania regionów i sprawdza na kopii M1, że widzi stare uskoki (158). Projekt liczył uskoki tylko w dolinie (`streamOrder > 0`); test liczy każdą suchą parę, bo przy odstępie 5 µm każdy taki uskok jest nieciągłością.
3. **Optymalizacje F1 z K4.11 weszły już w K3** (maksimum i minimum tylko blisko dna, spadek tylko w pasie 8τ), bo nie zmieniają wyniku. Po recenzji spadek odcinka liczy się dla każdego odcinka w ramce, bo potrzebuje go bufor F2.
4. **Klucz suchej głowicy obcięty poniżej zera zamiast kary 1e5 m** (projekt i prototyp: `key − 1e5`). Kara oddawała całą suchą głowicę obcej dolinie i dawała nowe proste nieciągłości na linii wygaszenia (wyżej). Obcięcie zachowuje cel projektu (głowica nigdy nie wygrywa z dnem) i nie zmienia terenu.
5. **Nowe pole `floorChannelGradient`** (poza projektem): F2 klasyfikuje koryto doliny dominującej jego własnym spadkiem, bo miękkie maksimum spadku miesza u ujść strome dopływy.

### Co zostaje

- Trójkąty ujść (P2 przy skraju dna rzeki, wyżej), równoległe koryta rzędu 3 w jednym dnie (A9/N3, kliny boru wilgotnego w zbiegu (−3268, −5357)) i dwusieczne poza dnami klasy A (N4): M5.
- Poza dnami F1 zamienia zakrzywione (i nieciągłe) granice dominacji z K2 na proste: klucz `floorHalf − floorDist` jest liniowy w odległości, więc rząd i spadek poza dnem zmieniają się na długich prostych (np. GAMEPLAY Beskidy (27609, 3254), REAL pogórze (2331, 904)). Pikseli nieciągłości jest mniej (np. 5250 → 2909, 11162 → 4118), a kody siedlisk zmieniły się w 0,00–0,10% kolumn tych kadrów. Zdanie projektu „pola zmieniają się tam, gdzie zmienia się dominacja, nie na dwusiecznej” jest więc prawdziwe tylko formalnie. Uproszczenie klasyfikatora w M5 (N4) nie może używać rzędu i spadku poza dnem bez wygładzenia (Javadoc `ColumnSample.Waters`).
- Nieciągłości pól na linii wygaszenia źródłowego 0,5 tam, gdzie głowica nachodzi na dno innej doliny (po recenzji kilkadziesiąt na kadr; `inFloor` i `u` jak w M1): A5 w K4 zmienia głowice i razem z nimi rozstrzyga, czy start dna ma być ciągły.
- F2 GAMEPLAY całe dno 92,9% przy progu 92%: K4 mierzy ponownie i wyjaśnia każdą zmianę.
- Starorzecza idą za nowym `best`: część zmalała albo ma proste cięcia, np. REAL (−13447, −22163): podkowa staje się sierpem ok. 40 × 10 m z prostą krawędzią, bo w obu pasach meandrów klucz = `floorHalf` i wygrywa szersze dno sąsiedniej doliny rzędu 2 (koryto W 8,9 m, 198 m dalej); GAMEPLAY (−3200, −5360): +1395 / −412 px wody. Zgodne z projektem, złote łaty `oxbow_lake` tego nie łapią. K5.1 przebudowuje starorzecza (nadal `oxbow(best, …)`) i musi sprawdzić, że sierpy nie są cięte linią dominacji F1 między nachodzącymi pasami meandrów.
- Wiszące jezioro rynnowe GAMEPLAY (3519, −4356): okno `gameplay_tunnel_lake_3519`, K5.2.
- TE wydłuża istniejące proste grzbiety między równoległymi dolinami (`TE_C_2km`, `TE_A_2km`): P9/G5, K4.
- Skoki A2 w oknach GAMEPLAY i masywów: K4b.

## K4. Geometria dolin (G1B, G2–G5, A5, K4.9) i K4b: dokładne minima odległości i cięcie przeciągnięte (D4)

Stan przed krokiem: commit `2546ec3` (K0–K2) z niezacommitowanym K3 w drzewie roboczym (migawka różnic w katalogu roboczym sesji, `k4base/pre_k4_worktree.diff`), pusta lista `git stash`.

**Problemy.** P5: oś doliny łamała się w węzłach o 15–72°. P6: w węźle skakała szerokość pasa meandrów w dnie. P8: ujście dopływu wcinało się w zbocze większej doliny ostrą, prostą ostrogą. P9: brzegi den biegły równolegle na kilometrach. A5: głowice dolin rzędu 2–3 były prostokątami pełnej szerokości dna, uciętymi prostą przy źródle. A2 (D4): urwiska na stokach gór GAMEPLAY, do kilkudziesięciu metrów na masywach.

### Co weszło

Logika z prototypu `PW/src3` (`-Ddno.fix=G1B,G2,G3,G4,G5,A5`), bez przełączników, w `RiverNetwork`:

- **G1B:** `wanderAt` odejmuje nachylenie łuków w końcowych ćwiartkach odcinka (`G1B_TAU` = 0,25), więc oś doliny jest gładka (C1) w węzłach, a środek odcinka zostaje na miejscu. Stałe nachylenia (`wanderSlope0`, `wanderSlope1`) liczy konstruktor `Segment` (K4.11). `maxLateral` uwzględnia poprawkę (1,12 |w1| + 1,24 |w2|) i `max(amp, ampEnd)`.
- **G2:** odcinek, który przechodzi w węźle w ten sam ciek (główny dopływ następnego węzła), dostaje `ampEnd` = `amp` odcinka w dół rzeki i przejście `ampAt(t)` na ostatnich `ampBlend` długości (kilka długości fali i trzykrotna różnica amplitud, najwyżej pół odcinka). Pas meandrów w `floorDist` liczy się z `ampAt(t)`.
- **G3:** ujście dopływu bocznego i przechwycenia (`mouth`: nie główny dopływ następnego węzła i nie do morza) poszerza dno lejkiem: + (0,6 · margines + 30 m·k) na ostatnich 4 (margines + 40 m·k) odcinka. Lejek mają też boczne dopływy węzła, który spływa do jeziora bezodpływowego (kończą się w tym węźle), a główny dopływ nie, jak przy każdym węźle (pierwsza wersja opisu mówiła „bez jezior bezodpływowych”, a warunek `SINK` w kodzie był martwy; recenzja). **Tylko w terenie** (odstępstwo 3).
- **G4:** nieregularny skraj dna: dwie fale szumu (500 i 1700 m·k, wagi 0,65 i 0,35) liczone raz na kolumnę, przy pierwszym odcinku w ramce; margines dna i pas meandrów razy 1 + 0,3 · szum. Szum z tego samego `Noise` sieci rzecznej z przesunięciami współrzędnych jak w prototypie (bez nowej soli).
- **G5:** gładkie minimum zboczy dwóch dolin (`smoothMin`, promień 4 m), 0 w dnach obu dolin i poza nimi, nie niżej niż lustro + 0,3 m i nie poniżej 0 m.
- **A5:** dno odcinka źródłowego narasta od 0,15 marginesu przy źródle do pełnego na 3 (margines + 40 m·k) (`floorMargin`, `headFactor`); po recenzji także pas meandrów dna (`floorBelt`), a meandry przy źródle narastają na długości wzrostu koryta (`envelopeStart`), opis niżej („A5 po recenzji”).
- **K4.9:** `reach` odcinka + 2,2 · max(amp, ampEnd), `floorHalfMax` z zapasem na G4 (× 1,36) i lejek G3.
- **K4b (D4):** rzut na odcinek (`projectChannel`) od nowa na dokładnych minimach odległości, a po recenzji cięcie przeciągnięte (`sweepCut`), opis niżej. Zastępuje R1 (dokręcanie rzutu pełnym Newtonem): dokładne minima nie potrzebują dokręcania, więc metod `refine` i `converged` nie ma.
- **Wyciek pamięci w testach:** bufor kafla (`TileCache`, wartość `ThreadLocal` sieci) trzymał referencję do swojej sieci (`owner`), więc wartość utrzymywała przy życiu własny `ThreadLocal`, a każda sieć użyta na wątkach puli zostawała w pamięci z pamięciami podręcznymi. W grze sieci są dwie–trzy, ale JVM testów (3 GB) przekroczyła limit po nowych testach K4. Pole `owner` jest zbędne (`ThreadLocal` należy do jednej sieci), więc usunięte; kopia M1 ma je nadal (zamrożona).
- **K4.11 (koszt):** `Math.sqrt` zamiast `Math.hypot` w ramce (`chordDistance`, liczona dla każdego odcinka z listy kafla w każdej kolumnie), w rzucie i w odległości od jezior; `StrictMath.hypot` był w profilu JFR największą pojedynczą pozycją `sample` (17% próbek razem z kopią M1). Spadek odcinka (`gradient`) liczony raz przy budowie odcinka; `wanderAt` raz na ramię rzutu (`meanderDistances` z podanym łukiem).

### K4b: rzut ciągły z konstrukcji i cięcie przeciągnięte (D4)

**Przyczyna urwisk (pomiar, `K4Probe` w katalogu roboczym sesji).** Rzut M1 szukał minimów odległości od krzywej odcinka przez porównanie 17 próbek i dokręcał każde 5 krokami Gaussa-Newtona w przedziale ±1/16. Trzy mechanizmy dawały skoki:

1. słabe minimum (para minimum–maksimum między dwiema próbkami) pojawiało się i znikało skokowo z wagą daleką od zera;
2. minimum na końcu odcinka włączało się porównaniem dwóch próbek;
3. dokręcanie zatrzymywało się na brzegu przedziału daleko od spodka prostopadłej. Przykład GAMEPLAY (68585, −31807): t 0,036 → 0,127 i odległość od osi doliny 240 → 274 m na 0,25 m, urwisko 63 m.

Po wejściu G1B…A5 (bez K4b) skoków > 3 m na suchym lądzie poza wodami stojącymi było w oknach testu ciągłości 70 (K3: 157), w tym 27 w oknie masywu 1660 m do 62 m: zgodnie z pomiarem recenzji projektu.

**Część 1: dokładne minima odległości (`projectChannel`, `distanceMinima`).**

- **Dokładne minima.** Kwadrat odległości od krzywej Hermite'a to wielomian stopnia 6, a jego pochodna to wielomian stopnia 5: f(t) = (P(t) − X) · P'(t). Współczynniki f niezależne od punktu liczy konstruktor odcinka. Pierwiastki izolujemy bez próbkowania: pierwiastki f''' (równanie kwadratowe), potem f'', f' i f, każde między pierwiastkami pochodnej, gdzie wielomian jest monotoniczny, metodą Newtona z przedziałem. Minima to pierwiastki, gdzie f zmienia znak z − na +, oraz końce, gdy punkt leży za nimi. Ramię rodzi się lub ginie tylko w fałdzie (razem z maksimum), a minimum na końcu przechodzi w ciągłe minimum wewnętrzne.
- **Wagi ramion.** Wyrazistość g = ½ D''(t) / |P'|² (1 dla prostej, 0 w fałdzie), waga smoothstep(0, `ARM_FOLD` = 1, g); koniec odcinka dostaje dodatkowo pełną wagę `END_BLEND` = 40 m za końcem. Waga nowo narodzonego ramienia jest więc 0.
- **Uśrednianie także odległości.** t, odległość od osi doliny, odległość od koryta i ciągła odległość od koryta są średnimi po ramionach z wagami exp(−(odległość − najmniejsza) / σ) · waga ramienia, każda ze swoją odległością. M1 brał minimum odległości po ramionach: przy narodzinach ramienia o innym łuku niż sąsiednie (np. koniec odcinka, gdzie łuk = 0, obok ramienia z łukiem 43 m) minimum skakało o kilkadziesiąt metrów, choć t było już ciągłe.
- **σ** = 10 + 0,3 · max(0, odległość od krzywej − `maxBend`), czyli od dolnego ograniczenia odległości od osi doliny, a nie od krzywej jak w M1. Oś długiego odcinka bywa kilometry od krzywej (łuki do 0,24 długości): punkt przy korycie 7,6 km od krzywej mieszał ramię oddalone o 2 km (odległość od koryta 864 m zamiast −14 m).

Same dokładne minima zostawiały resztę (pierwsza wersja K4, odstępstwo 1 przed recenzją): t i odległość są ciągłe, ale tam, gdzie odległość od krzywej jest prawie stała na części krzywej (punkt blisko środka krzywizny końca krótkiego, wygiętego odcinka, 100–380 m od koryta), oba ramiona mają małe wagi, które zmieniają się na centymetrach, a ramiona różnią się poziomem dna o kilkanaście metrów (0,1 długości stromego odcinka). **Recenzja K4** policzyła to właściwą miarą (zmiana wysokości na 1 m, a nie izolowane skoki): w oknach masywów zostawały ściany 10–50 m o nachyleniu 14–24 m na 1 m, często wzdłuż prostych, a w Beskidach GAMEPLAY pasy 3,6–4,3 m na 1 m długie na kilkaset metrów. Mój pomiar (`Cliffs`, 600 transektów po 1 km w oknie, krok 0,5 m, krok doliny = |Δpowierzchni| − |Δterenu przed dolinami| na 1 m): 99 skupisk powyżej 3 m na 1 m, najwięcej 22,1 m na 1 m (masyw 1718 m), 18,0 (Beskidy), 20,3 (masyw 1660 m), 19,0 (masyw przy spawnie), 13,8 (potok). Wszystkie zbadane przypadki (`K4Probe`, 10 miejsc) miały ten sam układ: ramię na końcu odcinka (g < 0, waga tylko z rampy `END_BLEND`, ok. 0,01–0,09) i ramię wewnętrzne przy t 0,06–0,1 tuż przed fałdem.

**Część 2 (po recenzji): cięcie przeciągnięte (`sweepCut`).** Wagi ramion nie dają się uwarunkować tanio (sprawdzone i odrzucone warianty niżej), więc teren nie zależy już od nich tam, gdzie są źle uwarunkowane. Cięcie doliny każdego odcinka jest co najmniej jego cięciem przeciągniętym minus `SWEEP_TOLERANCE` = 0,05 m:

- **Przekroje.** Oś doliny A(t) = P(t) + łuk(t) · normalna jako łamana przez 17 węzłów t_k = k/16 (`SWEEP_NODES` = 16), zapisana w odcinku przy budowie (`sweepNodes`: punkt krzywej, styczna krzywej, punkt osi; 6 liczb float na węzeł). Przekrój doliny (dno, ściana, A5, G3, G4, głowica źródła) liczony jest w każdym węźle od punktu osi oraz na każdej krawędzi łamanej od najbliższego punktu krawędzi. t przekroju to t węzła przesunięte wzdłuż stycznej krzywej o along / prędkość (największa prędkość krzywej w dół i najmniejsza w górę, więc dno nie wychodzi niżej, niż daje spodek prostopadłej w pierwszym przybliżeniu); na krawędzi t obu węzłów mieszane położeniem na krawędzi.
- **Ciągłość z konstrukcji.** Każdy przekrój jest ciągłą funkcją (x, z) o ograniczonym gradiencie (stromość ściany, spadek potoku, nachylenie terenu), a cięcie przeciągnięte to ich maksimum. Jest ciągłe bez względu na to, co robią ramiona rzutu, i zmienia się najwyżej ze stromością ściany. Gdzie ramiona są dobrze uwarunkowane, cięcie przeciągnięte jest równe cięciu rzutu z dokładnością do milimetrów albo mniejsze, więc teren zostaje jak po części 1 (`SWEEP_TOLERANCE` chroni łaty nizinne przed zmianami poniżej milimetra).
- **Gdzie cięcie przeciągnięte wygrywa.** (a) W prawie-remisach ramion: bierze najgłębszy z równoodległych przekrojów, więc zamiast przełączenia ramion na centymetrach jest gładkie zbocze. (b) Tam, gdzie łuki osi zmieniają się szybko: rzut mierzy odległość od osi w spodku prostopadłej do krzywej, a oś przechodzi bliżej (do kilkudziesięciu metrów w GAMEPLAY), więc ściana jest głębsza. To poprawka geometrii (prawdziwa odległość od osi), ale zmienia ściany stromych dolin GAMEPLAY o kilka–kilkadziesiąt metrów (kadry niżej).
- **Woda.** Dno nie schodzi niżej niż dno rzutu (t ograniczone od dołu, tolerancja), a nasyp (dno ponad terenem, ujemne cięcie) obniża się tylko o nadwyżkę cięcia przeciągniętego nad zerem, więc kaskady, brzegi koryt i wały zostają jak w części 1 (testy szczelności przechodzą).
- **Koszt.** Pętla po węzłach tylko wtedy, gdy granice z całego odcinka (najniższe dno, najszersze dno i ściana, odległość od krzywej minus największy łuk i strzałka krawędzi) dopuszczają cięcie większe od cięcia rzutu; dalej przekrój po przekroju z tym samym ograniczeniem. Tablica węzłów: 102 liczby float na odcinek.

Sprawdzone i odrzucone warianty (pomiary w katalogu roboczym sesji, `k4` i `k4fix`):

- **Wagi z bariery** (różnicy odległości ramienia i sąsiedniego maksimum): Lipschitz z konstrukcji, ale przy narodzinach ramienia bariera sąsiada spada skokowo z nieskończoności, a w płaskim dołku obie wagi są bliskie zera i ich iloraz jest źle uwarunkowany (4 skoki > 3 m, 31 par w `blades`).
- **Inne `ARM_FOLD` i `END_BLEND`** (0,5–2 i 5–100 m): krótsze `END_BLEND` usuwa jeden skok, ale wydłuża i zaostrza narodziny końca; dłuższe pogarsza okna.
- **Średnia Gibbsa po krzywej wszędzie**: 7–10 skoków do 53 m w REAL Beskidach (punkt przy korycie odcinka o dużych łukach dostawał średnią łuku z szerokiego okna).
- **Średnia Gibbsa tylko tam, gdzie ramiona są źle uwarunkowane** (wskaźnik z wag ramion, płynne przejście): na granicy przejścia średnia różni się od ramion o 0,2 t i 20 m odległości, więc przejście samo tworzy urwiska (90–220 skupisk zamiast 99). Tak samo cięcie przeciągnięte włączane tym wskaźnikiem (62–92 skupiska).
- **Cięcie przeciągnięte z przekrojami tylko w węzłach** (pierwsza wersja poprawki): 0 skupisk, wszystkie testy, ale każdy węzeł działa jak stożek, więc tam, gdzie wygrywa poprawka (b), ściany stromych dolin GAMEPLAY miały załamania co 1/16 odcinka („grzebień”, `k4fix/viz/png_k4`). Gęstsze węzły (32) osłabiały je, ale kosztowały +6–12 p.p.
- **Same krawędzie łamanej** (bez węzłów): czystsze, ale zostawały pojedyncze skoki 0,3–0,7 m tam, gdzie mieszanina ramion jest głębsza od każdego przekroju; węzły z przesunięciem t wzdłuż stycznej je pokrywają.
- **Bez przesunięcia t** (t węzła albo t z położenia na łamanej): „reguła V” obniżała dna stromych potoków poniżej poziomu wody, a wały brzegów podnosiły je z powrotem (skoki po 1,0 m); z ekstrapolacją w jedną stronę z ograniczeniem do pół oczka (wariant B) to samo przy ujściach.

**Wynik w oknach `SurfaceContinuityTest`** (150 transektów, skoki > 0,3 m na 0,5 m; liczba / największy; „krok doliny” = największa |Δpowierzchni| − |Δterenu przed dolinami| na 1 m na suchym lądzie poza wodą stojącą, kryterium D4 ≤ 3 m):

| Okno | K3 | K4 dokładne minima | K4 po recenzji | krok doliny po recenzji |
|---|---|---|---|---|
| `gameplay_beskids` | 41 / 36,4 m | 1 / 3,18 m | 1 / 0,68 m | 2,19 m |
| `gameplay_center` | 19 / 20,3 m | 16 (14 przy wodzie) | 14 (wszystkie przy wodzie) | 1,65 m |
| `gameplay_stream` | 37 / 13,6 m | 6 / 3,02 m | 6 / 2,85 m (szew A16, K6) | 1,78 m |
| `gameplay_moraine` | 19 / 67,3 m | 13 (przy wodzie) | 14 (13 przy wodzie, 0,37 m na zboczu) | 0,96 m |
| `realistic_beskids` | 1 / 1,06 m | 0 | 0 | 1,26 m |
| `realistic_lowland` | 2 / 1,50 m | 2 / 1,50 m | 2 / 1,50 m (torf oczka, K5.4) | 0,60 m |
| `realistic_moraine` | 15–16 / 4,80 m | 14–15 | 14 (przy wodzie) | 0,21 m |
| `realistic_stream` | 0 | 0 | 0 | 1,13 m |
| `gameplay_tunnel_lake_3519` | 23 / 85,8 m | 25 | 25 / 87,1 m (K5.2) | 0,48 m |
| `gameplay_massif_spawn` | 42 / 22,0 m | 2 / 4,29 m | 1 / 0,35 m | 2,50 m |
| `gameplay_massif_1660` | 87 / 69,5 m | 1 / 0,33 m | 1 / 0,33 m | 2,17 m |
| `gameplay_massif_1718` | 41 / 58,0 m | 0 | 0 | 2,12 m |
| `gameplay_massif_1710` | 15 / 15,7 m | 1 / 0,47 m | 1 / 0,47 m | 2,18 m |
| `realistic_massif_1723` | 0 | 0 | 0 | 1,16 m |

Skoki, które zostały na zboczach (0,33–0,68 m), to pasy ok. 0,4 m szerokie o nachyleniu 1,5–2 m na 1 m (profil co 5 cm, `JumpProf`): tam mieszanina ramion jest odrobinę głębsza od cięcia przeciągniętego, poniżej bloku. W liczbach z pierwszej wersji K4 były dwa błędy (recenzja): `realistic_lowland` miał 2 skoki do 1,50 m, a nie 3 do 10,4 m (ściany jeziora w (96844, 14706) w tym oknie nie ma), a `gameplay_center` 16 (14 przy wodzie), a nie 18 (16).

**Szerszy pomiar kroku doliny** (`Cliffs`, 600 transektów w każdym oknie z recenzji; `CliffsBroad`, losowe transekty po 1 km w losowych miejscach typu, ziarno świata 20260927; suchy ląd poza wodami stojącymi):

| Pomiar | K4 dokładne minima | K4 po recenzji |
|---|---|---|
| 10 okien recenzji (600 transektów): skupiska kroku doliny > 3 m na 1 m | 99, najwięcej 22,1 m | 0 |
| Beskidy GAMEPLAY, 3000 transektów: skupiska > 3 m / izolowane skoki (0,3–1 / 1–3 / 3–10 / > 10 m) | 86 (do 19,9 m) / 18 / 9 / 3 / 0 | 0 / 16 / 5 / 0 / 0 |
| Pogórze GAMEPLAY, 3000 transektów | 33 (do 18,2 m) / 14 / 7 / 2 / 0 | 0 / 12 / 1 / 0 / 0 |
| Wysoczyzna morenowa GAMEPLAY, 2000 transektów | 1 (7,3 m; w (−271577, −223878) 4,2 m na 0,25 m) / 10 / 0 / 1 / 0 | 0 / 11 / 0 / 0 / 0 |
| Beskidy i pogórze REAL, po 1500 transektów | 0 / 0 | 0 / 0 |

Zwykła zmiana wysokości na 1 m (bez odjęcia terenu przed dolinami) w oknach masywów GAMEPLAY dalej przekracza 3 m: 4,6–5,0 m na 1 m (`largest 1 m step` w teście), w 4000–8000 miejscach na okno, tak samo przed K4 i po nim. To kopuły masywów z K2, nie doliny: np. x = 6956, z od ok. −34400 do −34230, 684 → 1516 m na ok. 170 m przy wadze doliny 0 i terenie równym terenowi przed dolinami (recenzja). Kryterium D4 „żaden krok > 3 m na 1 m w oknach masywów” jest więc nieosiągalne bez zmiany kopuł; test mierzy krok doliny (≤ 3 m wymuszone we wszystkich oknach), a zwykły krok wypisuje. Stromość kopuł to sprawa K2/S3 (zgłoszone głównemu agentowi).

**Miejsca urwisk na masywach (`massifSpotsHaveNoCliffs`, pary > 25 m):** `gameplay_blades_1707` 320 / 83,1 m (K2) → 10 / 31,4 m (dokładne minima) → 0 / 5,9 m, `gameplay_fan_1673` 535 / 83,1 m → 0 / 10,6 m; miejsca REAL bez zmian (ściany jezior: K5.4). **Źródła na stokach masywów (`massifStreamSourcesHaveNoCliffs`):** GAMEPLAY zwykły krok 25,87 (K2) → 6,13 → 4,06 m na 1 m, w tym krok doliny 2,54 m (D4 ≤ 3 m wymuszone): w (−186812,8; −274959,9) sam teren przed dolinami spada 2,7 m na 1 m; REAL 1,46 → 1,31 m (krok doliny 1,18 m). `mountainStreamSourcesHaveNoCliffs` (REAL Beskidy) < 3 m jak dotąd.

**Kadry** (`k4fix/viz`, narzędzie `VizK4` recenzji; „przed” = pierwsza wersja K4, „po” = po recenzji): ściany z recenzji zniknęły bez śladu (`C_besk_28492_872_300m`, `C_m1660_…`, `C_m1718_…`, `C_stream_4113_3644_300m`, `Z_besk_wall_500m`, `Z_spawn_wedge_400m`: 900–25 000 par > 3 m na 1 m → 0). Ściany stromych dolin GAMEPLAY są tam, gdzie wygrywa poprawka (b), głębsze o kilka–kilkadziesiąt metrów (`G_beskidy_3km`: 316 tys. z 640 tys. pikseli zmienionych, największa różnica −82 m w miejscu dawnej ściany; `Z_rays_new_400m`: do −24 m, zbocza gładkie, bez nowych załamań).

### A5 po recenzji: głowice nizinne

Recenzja: na niżu głowica została prostokątem (`R_morena_3km`, głowica (−67330, 21470): dno ok. 230 m szerokie przy źródle, prosta ściana w poprzek osi), bo A5 zwężało tylko margines dna, a na niżu dno to głównie pas meandrów odejmowany w odległości od dna (ok. 2,4 amplitudy). Test głowic nie mógł tego wykryć: `inFloor` wymaga wygaszenia > 0,5, czyli nie działa w pierwszych 200 m·k od źródła.

- **Pas meandrów zwęża się razem z marginesem** (`floorBelt`): na odcinku źródłowym pas = amplituda · beltK · G4 · max(`headFactor`, amplituda meandrów w tym miejscu / amplituda), więc nie węższy niż kanał rzeczywiście meandruje i koryto zostaje w dnie.
- **Meandry przy źródle narastają dłużej** (`envelopeStart`): na odcinku źródłowym na długości co najmniej `headFade` (400 m·k, na której rośnie samo koryto), a nie 1,5 długości fali. Bez tego meandry miały pełną amplitudę po 130 m od źródła i pas meandrów trzymał pełną szerokość dna; teraz dno głowicy zwęża się w klin o długości ok. 400 m·k (`k4fix/viz/png/R_head_-67330_21470_800m_terrain.png`).
- **Nowy test** `valleyHeadsAreRounded` mierzy szerokość dna terenu (kolumny, gdzie odległość od dna ≤ półszerokość dna terenu, `floorGeometry`; dla samego odcinka źródłowego) na przekroju 0,25 F od źródła wobec 4 F dalej. Wymaga mediany ≤ 0,4 i co najmniej 80% głowic poniżej 0,6. Teraz: mediana 0,22 (REAL, 672 głowice, wszystkie < 0,6) i 0,21 (GAMEPLAY, 457 z 459). Bez A5 (stan K3) mediana 1,00, z samym marginesem (pierwsza wersja K4) 0,61, głowice nizinne 0,58: test pada w obu (sprawdzone na kopii z przełącznikiem trybu A5). Sprawdzenie „za głowicą teren nie jest wcięty” zostaje (0 kolumn).

### Złoty test

`-PgoldenReport=build/golden_K4.txt` (przebieg w katalogu roboczym sesji, ten sam kod): zmienione łaty w zestawach (łaty z „cel” tracą cel):

| Zestaw | Zmienione łaty |
|---|---|
| REAL A | grid, foothills_river, mountain_stream, beskids, foothills; numerycznie: lowland_river (=), large_river, oxbow_lake |
| REAL B | grid, coast, river_mouth (cel), large_river (cel), foothills_river, outwash_plain_lake, mountain_stream (cel), beskids, summit, foothills |
| GAMEPLAY A | grid, river_mouth, lowland_river, foothills_river, tunnel_valley_lake, outwash_plain_lake, kettle_pond, peatland, mountain_stream, beskids, summit (cel), foothills |
| GAMEPLAY B | grid, coast, beach, large_river (cel), foothills_river, kettle_pond, mountain_stream (cel), beskids, summit, foothills; numerycznie: lowland_river |
| REAL A 0,5 | grid, lowland_river, oxbow_lake (cel), mountain_stream, beskids, summit, foothills; numerycznie: large_river (=), foothills_river (=) |

- Zbiór łat zmienionych i łat, które tracą cel, jest zgodny z tabelą `dna-dolin/RAPORT.md` §6.2 dla zestawu G1B…G5 (z A5 i K4b w łatach gór), poza łatami „numerycznie” i dwiema łatami GAMEPLAY A z cięcia przeciągniętego (niżej).
- **Cięcie przeciągnięte (odstępstwo 10):** GAMEPLAY A `kettle_pond` (zbocze niecki do 1,1 m niżej, 10 kolumn z innym blokiem, bez zmian wody i podłoża) i GAMEPLAY A `summit` traci cel: ściana doliny źródłowej, której oś leży 406 m od szczytu (rzut mierzył 444 m), obniża wierzchołek o do 2,3 m (88 z 256 kolumn z innym blokiem), więc forma SUMMIT nie jest już lokalnym maksimum powyżej 900 m w łacie. Obie łaty są w tabeli końcowej §4 „zmiana”; `summit` w GAMEPLAY A zostanie w K7 wyszukana od nowa (nie było jej na liście „zmiana, cel”).
- **Numerycznie** to różnice max |Δsurface| od 0,07 µm do 0,12 mm, bez żadnej kolumny z innym blokiem, wodą, typem czy podłożem: t dokładnego minimum zamiast 5 kroków Gaussa-Newtona (M1 kończył ok. 10⁻⁶ t od minimum) i `Math.sqrt` zamiast `Math.hypot`. To prawdziwe zmiany powierzchni poniżej milimetra; tylko w REAL A `lowland_river` (0,07 µm) zmiana jest mniejsza od 1 µm, czyli zaokrąglenie skrótu trafiło na granicę. **Trzy z nich są w tabeli końcowej §4 „=”**: REAL A `lowland_river` (0,07 µm), REAL A 0,5 `large_river` (0,12 mm) i REAL A 0,5 `foothills_river` (0,03 mm; w pierwszej wersji opisu pominięte, recenzja). Lista dozwolonych zmian oznacza je przyrostkiem `numeric`: `GoldenTerrainTest` dopuszcza wtedy tylko zmianę stanu M1 poniżej 1 mm wobec zamrożonej kopii M1, bez żadnej kolumny z innym blokiem, poziomem i rodzajem wody, typem czy podłożem (`NUMERIC_SURFACE`; sprawdza to sam test, a nie opis). **Poprawka protokołu K7 (warunek 1 sprawdzenia przed zapisem):** łata „=” może być na liście zmian tylko jako zmiana numeryczna w tym sensie, potwierdzona raportem (`-> numerical change of the M1 state only` albo `-> numerical difference only`); każda inna zmiana łaty „=” zatrzymuje zapis.
- `lagoon`, `cliff`, łaty kontrolne `outwash_plain_interior`, `moraine_plateau_interior` i `great_massif` bez zmian.
- Lista `src/test/golden-allow/K4.txt` (lista K3 + powyższe, z komentarzami). Z nią `terrainMatchesGolden` przechodzi (2 z 2).

### Testy

**Nowe i zmienione testy (`RiverNetworkTest`, `SurfaceContinuityTest`):**

- `valleyAxisIsSmoothAtNodes`: kąt osi doliny w węzłach, gdzie ciek przechodzi w następny odcinek (różnica skończona 10⁻⁴ odcinka). REAL 1634 węzły: mediana 0,021°, p90 0,052°, maksimum 0,098°; GAMEPLAY 862 węzły: 0,023°, 0,053°, 0,107° (progi 1° i 3°; M1 15–72°).
- `valleyHeadsAreRounded`: od nowa po recenzji (wyżej, „A5 po recenzji”).
- `segmentCullingIsInvisible`: `query` z ramką i `querySegments` po wszystkich odcinkach promienia kafla (`tileRadiusSegments`, promień ze wspólnej metody `tileRadius`, tej samej co `candidates`) na 10 000 punktów na skalę (projekt: 20 000 w obu; po usunięciu wycieku pamięci zestaw jest pełny), w tym po 400 w każdym oknie testu ciągłości: 0 różnic (REAL 10 000 punktów, GAMEPLAY 8004 na lądzie).
- `SurfaceContinuityTest`: krok doliny (|Δpowierzchni| − |Δterenu przed dolinami| na 1 m) ≤ 3 m wymuszony we wszystkich oknach (D4); zwykły krok na 1 m wypisywany. Limity skoków do stanu po recenzji (tabela wyżej); `realistic_lowland` 2 / 1,51 m, `gameplay_center` 14 / 14 przy wodzie.
- `massifStreamSourcesHaveNoCliffs`: zwykły krok GAMEPLAY ≤ 4,1 m na 1 m (stan, teren przed dolinami do 2,7 m na 1 m), krok doliny ≤ 3 m (D4) w obu skalach.
- `massifSpotsHaveNoCliffs`: `blades` 0 / 6,0 m, `fan` 0 / 10,7 m.
- `noSpringsOnMassifCore`: obszar szczytowy wcięty najwyżej 20,5 m (REAL) i 58,0 m (GAMEPLAY), dno lub woda na wierzchowinie 1604 i 151 kolumn, najgłębiej G 0,50 i 0,34 (odstępstwo 11).

**Pozostałe wyniki** (przebieg klas `landscape` i `habitat`, 109 z 109):

- `TerrainLocalityTest`: 0 zmienionych kolumn poza dolinami, wodą i zasięgiem masywów na wszystkich siatkach.
- `TerrainDeterminismTest`: 0 różnic poza znanym wyjątkiem jezior rynnowych (2542 i 12143 kolumn, do K5.2).
- `WatersideZonesTest.floorZonesFollowDominantRiver`: REAL strefy rzeki 100%, całe dno 99,1%; GAMEPLAY 99,0% i całe dno 93,0% (próg 92%). Próba olsu: REAL 168 cięciw, 79,8% ≥ 10 bloków; GAMEPLAY 201 cięciw, 57,7%.
- `channelDistanceIsContinuousAndNonPositiveInChannel`: 0 skoków pola d. W REAL największy gradient w pasie stref 7,58 m na 1 m (K3: 4,33), 133 kroki > 1,5 m na 1 m z 84 949 (0,16%, próg 1%): pole d przy dużej rzece (W 41,6 m) liczone jako średnia ramion (drobna uwaga recenzji, „Co zostaje”).
- `noStepAtLowlandThreshold`: 0 uskoków w obu skalach.

**Pełny `./gradlew test`:** 131 testów, 1 porażka zamierzona: `terrainMatchesGolden` bez `-PgoldenAllow` (57 zmian łat i celów z listy wyżej). `./gradlew test --tests '*GoldenTerrainTest*' -PgoldenAllow=src/test/golden-allow/K4.txt -PgoldenReport=build/golden_K4.txt`: BUILD SUCCESSFUL, ta sama lista co przebieg w katalogu roboczym. `SampleCostTest` w pełnym przebiegu (maszyna obciążona innymi testami): 1,123 / 1,068 / 0,947 / 1,176 / 1,079 / 1,049 w kolejności tabeli kosztu.

### Koszt

Pomiar A/B: `SampleCostTest` (7 rund) z pierwszej wersji K4 i po recenzji, osobne JVM na zmianę, 2 powtórzenia. Średnie z median, µs na kolumnę (stosunek do kopii M1 w tej samej JVM):

| Obszar | K4, pierwsza wersja | K4 po recenzji |
|---|---|---|
| REAL cały obszar | 4,19 (1,024) | 4,45 (1,090) |
| REAL Beskidy | 6,56 (0,975) | 7,26 (1,035) |
| REAL wielki masyw | 6,21 (0,878) | 6,64 (0,936) |
| GAMEPLAY cały obszar | 5,57 (0,977) | 6,31 (1,107) |
| GAMEPLAY Beskidy | 9,63 (0,958) | 10,84 (1,069) |
| GAMEPLAY wielki masyw | 9,85 (0,873) | 11,62 (1,021) |

Pomiar z 2026-10-03 wieczorem, 2 powtórzenia (maszyna obciążona: kopia M1 o 5–15% wolniejsza niż w pomiarze pierwszej wersji, więc µs nie są porównywalne z tabelą K3; stosunki są).

- Cięcie przeciągnięte kosztuje 6–15 p.p. (najwięcej GAMEPLAY wielki masyw i cały obszar); budżet D1 (+20% wobec M1; 6,5 / 12 / 8,5 / 16 µs) jest dotrzymany z zapasem na K5 i K6 (A16: +2–6%).
- Optymalizacje cięcia przeciągniętego (bez zmiany wyniku, sprawdzone tym samym raportem złotego testu i liczbą kroków w `Cliffs`): granice z całego odcinka (najniższe dno, najszersze dno i ściana, prostokąt węzłów osi), bloki po 4 krawędzie odrzucane razem (strzałka bloku), granica przekroju bez t przed granicą z t, a przede wszystkim pominięcie cięcia, które nie zejdzie poniżej wyniku z poprzednich odcinków + promień G5 (G5 bierze wtedy zwykłe minimum, a wynik dalszymi odcinkami tylko maleje). Bez tego ostatniego GAMEPLAY cały obszar kosztował 1,16–1,20. Wersja z 32 węzłami kosztowała GAMEPLAY 1,16–1,17, więc zostało 16.
- Izolacja pierwiastków (`solve`, `rootsBetween`, `distanceMinima`) to ok. 13% próbek profilu JFR przed optymalizacjami (profil obejmuje też kopię M1).

### Odstępstwa od projektu w K4

1. **D4 (K4b) inaczej niż w projekcie, kryterium zmierzone krokiem doliny.** Projekt przewidywał „rzut ciągły z konstrukcji” (np. pole odległości od łamanej osi). Weszły dokładne minima odległości i cięcie przeciągnięte (maksimum przekrojów doliny wzdłuż łamanej osi), opis wyżej. Kryterium „żaden skok > 3 m na 1 m” mierzę jako krok doliny (zmiana wysokości na 1 m ponad zmianę terenu przed dolinami): po recenzji ≤ 2,5 m na 1 m we wszystkich oknach testu, 0 skupisk > 3 m w 600 transektach okien recenzji i w szerokich pomiarach. Zwykła zmiana na 1 m w oknach masywów GAMEPLAY przekracza 3 m na samych kopułach K2 (do 5 m na 1 m, bez dolin), czego D4 bez zmiany kopuł nie spełni (zgłoszone jako sprawa K2/S3). Zostają izolowane skoki 0,33–0,68 m na zboczach (pasy ok. 0,4 m szerokie, poniżej bloku).
2. **R1 zastąpione dokładnymi minimami (K4b).** Izolacja pierwiastków wielomianu daje dokładne minima wszędzie, więc R1 (pełny Newton tam, gdzie Gauss-Newton nie zbiegł) nie jest potrzebne.
3. **Lejek G3 tylko w terenie.** Pola doliny (klucz F1, `inFloor`, u, półszerokość dna, waga doliny) liczą się z dna bez lejka. Z lejkiem w polach dopływ dominował w trójkątach dna rzeki przy zbiegu i dostawał tam swoje strefy (F2 GAMEPLAY w zasięgu łęgu topolowego 72,7% przy progu 95%, całe dno 89,4% przy progu 92%). **Skutek w siedliskach (poprawione po recenzji; wcześniej opisane jako „pas zbocza”):** w lejku teren jest płaskim dnem kilka metrów nad potokiem, ale pola mówią „poza dnem” (`inFloor` = 0, u = NaN), więc klasyfikator maluje tam łaty o prostych krawędziach: przy zbiegu GAMEPLAY ok. (27990, 3660) lejek obniża teren do 33 m, a ols rośnie z 0,1% do 1,6% kadru, łęg z olszą szarą z 0 do 0,5%, łęg jesionowo-olszowy z 0,1 do 0,3% (łaty ok. x 27998–28019, z 3611–3671 i x 27975–27998, z 3694–3729; `viz_K4/png/Z_besk_conf_300m_*`). Ols nie pasuje do beskidzkiego potoku na 480 m. Do K7 (niżej, „Co zostaje”).
4. **Styczna cięciwy na końcu odcinka w jeziorze bezodpływowym zostaje tylko przy masywach** (zapowiedź z K2: „K4 rozciąga ją na każde jezioro”). Dla wszystkich jezior przesuwała rzeki daleko od jezior: ujście dopływu na odcinku kończącym się w jeziorze przesunęło się o 3 km, a z nim odcinek rzędu 3 długości 85 km o ok. 1 km. Dokładny rzut K4b usuwa zaś mechanizm urwisk przy masywach, dla którego K2 wprowadził tę regułę: bez reguły (próba) miejsca `realistic_box_canyon_1698` i `realistic_slot_1700` oraz okno `realistic_massif_1723` dalej mają 0 urwisk. Reguła K2 zostaje, żeby nie zmieniać terenu masywów; uogólnienie do M5.
5. **Pełna odległość nieodnalezionego minimum** (K2, tylko przy masywach) jest zbędna: dokładne minima mają składową wzdłuż równą 0. Pole `Segment.nearMassif` usunięte.
6. **`valleyHeadsAreRounded`** mierzy dolinę samego odcinka źródłowego (`querySegments`, `floorGeometry`), bo za źródłem często leży inna dolina, i porównuje szerokość dna terenu przy źródle z szerokością dalej (projekt: „0,5 · półszerokości dna za źródłem teren nie jest wcięty”; to sprawdzenie też zostaje).
7. **σ wag ramion od dolnego ograniczenia odległości od osi doliny** (`maxBend`) zamiast od odległości od krzywej (M1), i średnie także odległości zamiast minimum (K4b, wyżej).
8. **K4.11 częściowo inaczej niż w projekcie:** sprawdzanie zbieżności R1 odpada razem z R1; zapis kandydatów F2 tylko w zasięgu stref nie wszedł (K1 i K3: bufor bez limitu, test przepełnień), a zamiast tego doszło usunięcie `Math.hypot` (największy zysk).
9. **Wody stojące: limity bez poprawy wobec celów §3.3.** `realistic_lowland` 2 skoki do 1,50 m (torf oczka w (−92914, 80060), K5.4; cel 0), `gameplay_center` 14 skoków, wszystkie przy wodzie (oczka, torf, rynna; cel ≤ 3 po A3), `gameplay_moraine` 13 przy wodzie, `realistic_moraine` 14 przy wodzie, `gameplay_tunnel_lake_3519` 25 (wiszące jezioro rynnowe, K5.2). K5.4 (A3) i K5.2 mają sprowadzić te okna do celów §3.3 i zaostrzyć limity. (W pierwszej wersji tego opisu `realistic_lowland` miał „3 skoki do 10,4 m” przy ścianie jeziora bezodpływowego w (96844, 14706): ten skok nie występuje, recenzja.)
10. **Cięcie przeciągnięte zmienia teren poza prawie-remisami ramion** (poprawka (b): odległość od osi samej, a nie od spodka prostopadłej do krzywej): ściany stromych dolin GAMEPLAY głębsze o kilka–kilkadziesiąt metrów, łaty GAMEPLAY A `kettle_pond` i `summit` (cel) spoza listy pierwszej wersji K4, obie „zmiana” w tabeli końcowej.
11. **Wcięcie obszaru szczytowego masywu w GAMEPLAY 58,0 m** przy kryterium projektu < 50 m (`noSpringsOnMassifCore`; po K2 50,6 m, w pierwszej wersji K4 51,5 m): w (109362, 285955) to górna krawędź ściany doliny potoku rzędu 1 na stoku, 1470 m głębokiej, 531 m od jej osi (rzut mierzył 548 m); ściana w GAMEPLAY ma do 600 m szerokości i jest tu stromsza niż 3 m na 1 m. To nie jest kanion w kopule (brak dna i wody na obszarach szczytowych). Do K2/S3 razem ze stromością kopuł.
12. **Pole `TileCache.owner` usunięte** (poza projektem): utrzymywało przy życiu `ThreadLocal` każdej sieci, przez co JVM testów (3 GB) kończyła się brakiem pamięci.

### Poprawki po recenzji K4

- **Blokujące (D4):** cięcie przeciągnięte (wyżej). Kryterium jest spełnione dla kroków dolin we wszystkich oknach i w szerokich pomiarach; nie jest spełnione dla kopuł K2 (osobna sprawa) i dla izolowanych skoków poniżej 0,7 m.
- **Łaty „=” zmienione numerycznie:** wszystkie trzy nazwane, przyczyna opisana jako prawdziwe zmiany poniżej milimetra (poza 0,07 µm w REAL A `lowland_river`), przyrostek `numeric` sprawdzany przez test i poprawka warunku K7 (wyżej, „Złoty test”).
- **Ściany niewidoczne dla testu ciągłości:** test mierzy krok doliny na 1 m (≤ 3 m wymuszone) i wypisuje zwykły krok; liczby z pierwszej wersji („0 / 0,33 m” w oknach masywów) poprawione w tabeli.
- **A5 na niżu:** pas meandrów i meandry przy źródle (wyżej); test od nowa.
- **Drobne:** `mouth` bez martwego warunku `SINK` i z opisem zgodnym z działaniem (lejek mają też boczne dopływy węzła spływającego do jeziora bezodpływowego, główny dopływ nie; teren bez zmian); G3 w siedliskach opisany (odstępstwo 3) i zostawiony do K7; limity okien z wodą stojącą zaostrzone i przypisane K5.4/K5.2 (odstępstwo 9); `SUMMIT_CUT` opisane jako odstępstwo (11); `segmentCullingIsInvisible` 10 000 punktów na skalę i wspólny promień kafla; „centre” → „center”; liczby `realistic_lowland` i `gameplay_center` poprawione; brak kontroli kadrów R_potok_3km i G_rzeka_1km dopisany do „Co zostaje”; pole d (drobna uwaga) bez zmian, do pomiaru w K7.

### Co zostaje

- Izolowane skoki 0,33–0,68 m na zboczach (pasy ok. 0,4 m, gdzie mieszanina ramion jest głębsza od cięcia przeciągniętego): M5 razem z rzutem na samą oś doliny.
- Stromość kopuł masywów GAMEPLAY (do 5 m na 1 m bez dolin) i ściany dolin głębszych niż `maxWall` · stromość (np. 1470 m przy obszarze szczytowym, odstępstwo 11): K2/S3.
- Szew mieszania regionów w `gameplay_stream` (5540, 2725), skoki 2,7–2,9 m: K6 (A16).
- Ściany jezior bezodpływowych (miejsca `realistic_sink_lake_*`), torf oczek, wiszące jezioro rynnowe (3519, −4356): K5.4 i K5.2. W K5.2 `floorGap` liczyć z półszerokości dna terenu (z lejkiem G3), żeby końce jezior rynnowych nie sięgały w lejek.
- Lejek G3 w siedliskach (odstępstwo 3): przed K7 sprawdzić kilka kadrów zbiegów GAMEPLAY i zdecydować, czy lejek ma dostać strefę dna doliny przyjmującej, czy klasyfikator ma go traktować jak stok (np. `heightAboveChannel` wobec doliny dominującej).
- Pole d (strefy nadwodne) przy dużych rzekach: gradient do 7,58 m na 1 m (0,16% kroków > 1,5): przed K7 zmierzyć pasy stref na kadrach `HabitatPreview` przy dużych rzekach.
- Kontrole kadrów §3.4, które nie są spełnione: `R_potok_3km`: wachlarz przy (570, 100) px nie znika, głowica kończy się ostrym klinem w kształcie V (w pierwszej wersji K4 z izolowanym skokiem przy czubku ok. (72788, 1008372)); `G_rzeka_1km`: „równoległobok” przy (445–610, 125–350) px dalej jest, z prostymi załamaniami między ścianami (G5 o promieniu 4 m ma na ścianach 1,4 m na 1 m ok. 3 m szerokości, w GAMEPLAY prawie niewidoczny). Do A8/G5 (promień) i S8 (strojenie).
- Uogólnienie stycznej cięciwy w jeziorach bezodpływowych (odstępstwo 4): M5.
- F2 GAMEPLAY całe dno 93,0% przy progu 92%: trójkąty ujść (K3) bez zmian.
- Nieciągłości pól na linii wygaszenia źródłowego (K3, „Co zostaje”) nie mierzyłem ponownie narzędziem `FieldSeams`; A5 zmienia tam szerokość dna.
