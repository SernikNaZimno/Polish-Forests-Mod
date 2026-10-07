# Poprawka geometrii terenu (proste krawędzie den dolin, starorzeczy i jezior; M2-8)

Dokument wdrożenia poprawki geometrii terenu. Opisuje kroki K0–K7 (oraz krótką poprawkę K8), narzędzia kontrolne i odstępstwa od projektu. Projekt i analizy (dna dolin, starorzecza i jeziora, szczyty, przegląd artefaktów) powstały poza repozytorium; tu zapisujemy to, co weszło do kodu, i wyniki pomiarów.

## Kroki

| Krok | Co | Teren |
|---|---|---|
| K0 | Narzędzia: dwa pliki wzorcowe złotego testu, lista dozwolonych zmian, raport zmian, zapis z zachowaniem środków łat, test ciągłości powierzchni, test lokalności, test determinizmu | bez zmian |
| K1 | Strefy dna od koryta doliny dominującej (F2) | bez zmian |
| K2 | Wielkie masywy Beskidów (M2-8), miękki pułap wierzchołka, reguły rzek przy masywach | tylko masywy |
| K3 | Ciągłe pola doliny dominującej (F1), bez uskoku przy progu nizin 0,3 (TE) | doliny |
| K4 | Geometria dolin (G1B, R1, G2–G5, A5) i optymalizacje; K4b: urwiska na stokach gór GAMEPLAY (D4); K4c: cięcie omiatające tylko przy remisach (D4a), kryteria w blokach (D4b), łuk głowicy (A5), lejek w polach siedlisk (G3) | doliny i zbocza |
| K5 | Wody stojące: starorzecza, jeziora rynnowe (z poziomem niezależnym od kolejności próbkowania), oczka, niecki, brzeg jezior bezodpływowych; K5b: wybrzeże wydmowe (D5) | wody stojące, wybrzeże |
| K6 | Okno mieszania regionów 5 × 5 (A16); test ścian z całą niecką; D3 (wały moren W–E) odłożone do M5; `TUNNEL_COS` 0,6 sprawdzony i cofnięty (runda 1) | szwy regionów, jeziora rynnowe |
| K7 | Jedno przegenerowanie `golden_terrain_m2.txt` (protokół §4), usunięcie list dozwolonych zmian, podsumowanie poprawki i dokumentacja | — (plik wzorcowy) |
| K8 | Krótka poprawka po teście w grze (decyzja użytkownika 2026-10-07): K8a jeziora rynnowe GAMEPLAY (wariant d z K6), K8b wybrzeże, K8c starorzecza, K8z przegenerowanie pliku wzorcowego | jeziora rynnowe (K8a) |

Zalew bez rowu (D2) wszedł w K5 razem z wybrzeżem wydmowym (D5, K5b). Wały moren W–E (D3) nie przeszły prototypu w K6 (łamią testy sieci rzecznej) i są odłożone do M5.

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
- `-PgoldenAllow=<plik>`: lista dozwolonych zmian. `terrainMatchesGolden` pada tylko przy zmianie łaty spoza listy albo przy utracie celu spoza listy. Dozwolone zmiany wypisuje jako informację. Od K4c bez tej właściwości używana jest lista najnowszego kroku (ostatni według nazwy `src/test/golden-allow/K*.txt`), a `-PgoldenStrict` porównuje bez żadnej listy (K4c, „Złoty test”). K7 usunął katalog list, więc zwykły `test` porównuje teraz bez listy.
- `-PgoldenReport=<plik>`: lista zmienionych łat w składni listy dozwolonych zmian, a zmienione pola, porównanie z kopią M1 i opisy utraconych celów jako komentarze. Raport powstaje także wtedy, gdy test przechodzi (`# no changes`).

**Składnia listy dozwolonych zmian** (`#` zaczyna komentarz, więc raport może służyć za listę):

```
REAL A / grid                 # skrót łaty może się zmienić
GAMEPLAY A / oxbow_lake target  # skrót może się zmienić i łata może stracić cel
* / grid                      # we wszystkich zestawach
GAMEPLAY A / coverage         # zestaw może stracić rodzaj wody, typ lub podłoże
```

Zestawy: `REAL A`, `REAL B`, `GAMEPLAY A`, `GAMEPLAY B`, `REAL A 0.5` (A = ziarno 20260927, B = −7316550294015845337), pełny klucz z pliku (`realistic 20260927 1.0`) albo `*`. Nieznany zestaw, łata lub przyrostek kończy test błędem, żeby literówka nie dopuszczała po cichu niczego. Listy kroków leżały w `src/test/golden-allow/K3.txt` … `K6.txt`; K7 je usunął (zostają w historii git, commit `162995f`).

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

## K4c. Domknięcie K4: cięcie omiatające tylko przy remisach (D4a), kryteria D4b, łuk głowicy (A5), lejek w polach siedlisk (G3) i drobne

Stan przed krokiem: commit `006e2d5` (K3 + K4 w toku), drzewo robocze czyste. Rozstrzygnięcia: „Rozstrzygnięcia po K4” w decyzjach wdrożenia (D4a, D4b, A5, G3, drobne).

### D4a: cięcie omiatające tylko przy remisach ramion

**Problem (ponowna recenzja K4).** Cięcie omiatające z K4b mierzyło przekroje odległością euklidesową od 16-krawędziowej łamanej osi doliny. Cięciwy łamanej przechodziły do 561 m bliżej niż oś (rząd 3 REAL), a odległość euklidesowa od osi nachylonej względem krzywej jest krótsza niż odległość mierzona przez rzut wzdłuż normalnej krzywej w spodku prostopadłej. Cięcie wygrywało więc daleko od remisów. Pomiar narzędziem `SweepAB` (losowe punkty lądu: 6000 w dużym kwadracie i po 300 w każdym oknie testu ciągłości, teren rzeczny z cięciem i bez): REAL 6,8% lądu zmienione o > 1 m (do 118 m), GAMEPLAY 22,7% (do 87 m). W punktach z jednym wyraźnym ramieniem (definicja niżej) zmienione o > 0,05 m: REAL 693 z 1968, GAMEPLAY 1853 z 4058.

**Co weszło (`RiverNetwork.sweepCut`, klasa `Section`).** Przekroje liczone są w metryce samego rzutu, bez łamanej osi:

- Rodzina przekrojów: węzły krzywej t_k = k/16 i na każdej krawędzi łamanej *krzywej* punkt najbliższy kolumnie (rzut na cięciwę krawędzi, potem dokładny punkt krzywej, pochodna i prędkość z wielomianu odcinka). Każdy przekrój jest ciągłą funkcją (x, z), cięcie omiatające jest ich maksimum.
- Odległość od osi doliny: |lat − wander(t_f)| wzdłuż normalnej krzywej w t (tak jak rzut mierzy ją w spodku), z ugięciem osi `wanderAt` w oszacowaniu Newtona spodka t_f = t + along / (prędkość · g), g ≥ 0,5 (`SWEEP_MIN_DISTINCTNESS`), plus kara `SWEEP_PENALTY` · |along| (1,0), wygładzona przy 0 na 5 m·k (`SWEEP_PENALTY_SOFT`, Huber), bo z samym |along| przekrój wygrywający w remisie zostawiał wzdłuż normalnej swojego węzła doły w kształcie V do 2,7 m głębokie i 2 m szerokie (izolowane skoki w testach ciągłości).
- Dno, szerokość, margines (A5, G3) i głowica źródła brane są w górę rzeki od spodka: t + 2 · krok Newtona, gdy spodek leży w górę rzeki (`SWEEP_FLOOR_MARGIN` = 1; z marginesem 0,25 oszacowanie z węzła odległego o pół krawędzi bywało za krótkie o połowę, np. GAMEPLAY (27408,6, 2952,3): dno o 0,23 m za nisko), pas meandrów jako mniejszy z obu końców przedziału (G2 może go zwężać w dół rzeki).
- Koniec odcinka z punktem za nim: przekrój ramienia końcowego rzutu (odległość od końca osi, dno na końcu).
- ~~Pominięcie cięcia przy wyraźnym rzucie~~ (**usunięte w rundzie 1 poprawek, niżej**). Pierwsza wersja K4c w ogóle nie liczyła cięcia omiatającego tam, gdzie rzut był „wyraźny” (jedno ramię, g ≥ 0,7; `SWEEP_SKIP_DISTINCTNESS`, flaga `out[9]`). Założenie, że przy jednym wyraźnym ramieniu żaden przekrój nie tnie głębiej niż ramię, okazało się fałszywe. Na granicy pomijanego obszaru powstawały pionowe uskoki do 20 m. Test regresji porównywał teren z tą samą wersją z pominięciem, więc w pomijanym obszarze różnica była z konstrukcji zerowa: obszar ten nigdy nie był sprawdzany.

Dlaczego to (w zasadzie) nie zmienia terenu przy jednym wyraźnym ramieniu (uwaga po rundzie 1: rozumowanie nie obejmuje prawie-remisów z końcem krótkiego odcinka i bliskiego ostrza krzywej, zob. runda 1): przekrój w samym spodku daje dokładnie cięcie tego ramienia, przekroje obok niego mierzą odległość nie mniejszą (kara |along| pokrywa krzywiznę, ugięcie osi brane jest w szacowanym spodku) przy dnie nie niższym (t w górę rzeki od spodka). W remisach (punkt blisko środka krzywizny, gdzie odległość od krzywej jest prawie stała na części krzywej, czyli along ≈ 0) przekroje nie są karane i cięcie bierze najgłębszy z nich zamiast szybkiego przełączenia miękkiego t rzutu.

**Sprawdzone i odrzucone warianty** (narzędzia `SweepAB`, `Cliffs` na 10 oknach recenzji po 200 transektów, `Windows` na oknach testu ciągłości; katalog roboczy sesji `k4c`):

| Wariant | Skupiska kroku doliny > 3 m na 1 m (200 transektów) | Zmiany w punktach wyraźnych |
|---|---|---|
| bez cięcia omiatającego (tylko rzut) | 38, do 19,1 m | — |
| K4b (łamana osi, euklidesowo) | 0 | REAL 693, GAMEPLAY 1853 (> 0,05 m) |
| przekroje rzutu, kara 4 · |along|, dno ze stałym g0 = 0,25 | 404–647, do 67 m (kara 5× ostrzejsza od ściany) | 0 |
| jak wyżej z bramką według uwarunkowania ramion (κ) | 48–55, do 25 m (rampy na brzegu bramki) | 0 |
| kara 1 · |along|, g0 = 1, bez bramki | 8, do 4,2 m | REAL 5, GAMEPLAY 36 (do 7,4 m: nachylenie osi |dw/ds| do 1,2) |
| to samo z bramką według udziału ramion lub z obcięciem | 27–33, do 18–38 m | REAL 3, GAMEPLAY 9 |
| przekroje przesuwane krokami Newtona do ramion | 148, do 17,7 m | do 40 m (dalekie ramię głębsze) |
| kara (1 + |dw/ds| / g) · |along| (zachowawcza) | 99, do 9,2 m | 0 |
| maksimum po ramionach rzutu i ramionach „rodzących się” (pierwiastki f') | 29, do 19 m (cięcie ramienia przy fałdzie zmienia się z w'·dt/dX → ∞) | 0 |
| **weszło (przed rundą 1): ugięcie w szacowanym spodku, kara 1 · |along|, dno 2 kroki w górę, Huber 5 m·k, pominięcie przy wyraźnym rzucie** | okna testu (150 transektów): krok doliny ≤ 5,1 m na 1 m, ≤ 1,54 bloku na blok; na gęstej siatce uskoki pominięcia do 20 m i pola żeber (runda 1) | 0 (tautologia, runda 1) |

Bramki (cięcie włączane tylko tam, gdzie rzut jest źle uwarunkowany) zawodzą zawsze z tego samego powodu: na brzegu bramki cięcie omiatające leży o kilkadziesiąt metrów pod rzutem (np. 30 m na końcu krótkiego wygiętego odcinka 17 m za jego początkiem), więc bramka robi z tej różnicy rampę.

**Wynik przed rundą 1 (test `RiverNetworkTest.sweepCutKeepsWellConditionedTerrain`, nowy; w tej wersji tautologiczny, stan aktualny w rundzie 1).** Punkt jest *wyraźny*, gdy każdy odcinek, który tnie w nim teren, ma jedno dominujące ramię: wyrazistość g ≥ 0,5, udział w wagach średnich miękkich ≥ 0,999, koniec odcinka tylko gdy punkt jest co najmniej `END_BLEND` za nim. REAL: 7500 punktów lądu, 1935 w zasięgu dolin, 1884 wyraźne, największa zmiana w punkcie wyraźnym 0 m (limit 0,05 m), żadnej zmiany > 0,05 m w ogóle. GAMEPLAY: 6784 / 4892 / 4007, w punktach wyraźnych 0 m; zmienione o > 0,05 m 16 punktów, o > 1 m 14 (0,21% lądu, limit testu 0,5%), największa zmiana 59 m (remis dwóch ramion krótkiego odcinka rzędu 1 na masywie).

**Uzupełnienie po rundzie 2 (drobna uwaga recenzji nr 2).** Opis wyżej podawał stan sprawdzony za mocno. Obszar pomijany *nie* leżał wewnątrz sprawdzanego, a okno `gameplay_beskids` nie miało „0 skoków i kroku doliny 4,89”. Na siatce 1 m recenzja znalazła tam 3855 par 1 m z krokiem doliny > 3 m, w tym uskok pominięcia 17,7 m (bez niego największy krok 5,5 m). Brakowało też trzech rzeczy:
- szerokiego wpływu łuku A5 w GAMEPLAY: 11,7% lądu zmienione o > 1 m, wygaszone w rundzie 1 do 0,41%;
- usuwania oczek przez lejek G3: 11 w REAL i 25 w GAMEPLAY, cofnięte w rundzie 1;
- pomiaru A/B samego cięcia.

Pomiar A/B cięcia omiatającego wobec samego rzutu po rundzie 2 (narzędzie `swab`, 40 000 losowych punktów na skalę, REAL ±150 km, GAMEPLAY ±40 km, ziarno 7):
- REAL: 0,00% lądu zmienione o > 0,05 m;
- GAMEPLAY: 0,08% o > 0,05 m, 0,07% o > 1 m, 0,03% o > 5 m, największa zmiana 50,9 m (remis ramion na masywie, (9164, −33733)).

Wynik jest taki sam przed rundą 2 i po niej. Pomiar recenzji na jej 40 tys. punktów: REAL 0,00% / 0,00% (> 1 m / > 5 m), K4b 4,75% / 1,94%; GAMEPLAY 0,01% / 0,00%, najwięcej 4,35 m, K4b 16,85% / 5,62%.

**Okna testu ciągłości przed rundą 1** (150 transektów; gęsty skan rundy 1 pokazał, że limity z transektów nie ograniczały terenu; krok doliny = |Δpowierzchni| − |Δterenu przed dolinami| na 1 m, bloki = zwykły krok w blokach przez `VerticalScale`):

| Okno | skoki (liczba / największy) K4b → K4c | krok doliny K4b → K4c | bloki na blok K4c |
|---|---|---|---|
| `gameplay_beskids` | 1 / 0,68 → 0 | 2,19 → 4,89 | 1,39 |
| `gameplay_center` | 14 (przy wodzie) → 14 | 1,65 → 1,70 | 0,64 (pas brzegowy 2,45) |
| `gameplay_stream` | 6 / 2,85 → 6 / 3,02 (szew A16, K6) | 1,78 → 2,35 | 1,26 |
| `gameplay_moraine` | 14 → 13 (przy wodzie) | 0,96 → 1,08 | 0,62 (pas brzegowy 2,08) |
| `gameplay_tunnel_lake_3519` | 25 → 25 (K5.2) | 0,48 → 0,53 | 0,22 |
| `gameplay_massif_spawn` | 1 / 0,35 → 0 | 2,50 → 5,10 | 1,49 |
| `gameplay_massif_1660` | 1 / 0,33 → 3 / 0,58 | 2,17 → 4,78 | 1,54 |
| `gameplay_massif_1718` | 0 → 0 | 2,12 → 4,71 | 1,49 |
| `gameplay_massif_1710` | 1 / 0,47 → 1 / 0,47 | 2,18 → 2,52 | 1,11 |
| okna REAL | bez zmian | ≤ 1,31 | ≤ 1,34 |

Na 600 transektach (narzędzie `Windows`, ziarno 11): krok doliny do 6,8 m na 1 m (`gameplay_massif_1660`), w blokach do 1,97 bloku na blok (`gameplay_massif_spawn`), żadnego kroku > 2 bloków poza pasem brzegowym. Bez cięcia omiatającego w tych samych oknach: do 22,5 m na 1 m i do 7,5 bloku na blok (40 kroków > 2 bloków w `gameplay_massif_1660`).

**Odstępstwo (D4a, wariant zapasowy).** Obu celów naraz nie udało się spełnić (dziewięć wariantów wyżej): przy cięciu, które nie zmienia terenu w punktach wyraźnych, zostają remisy, w których cięcie wygrywa i ma zbocza ściany doliny: w czterech oknach GAMEPLAY krok doliny 4,7–5,1 m na 1 m (K4b: < 2,5 m, ale z przebudową zboczy daleko od remisów). Zgodnie z D4a wybrałem teren bez szerokich zmian. Zostające remisy to: (1) dwa dobrze uwarunkowane ramiona krótkiego odcinka rzędu 1 widziane z kilkuset metrów (udział ramion ok. 0,5–0,65, g ≈ 3), gdzie średnia miękka miesza dna różniące się o kilkaset metrów (np. dolina 690 m głęboka na masywie przy (27108, 2147): cięcie najgłębszego ramienia 210 m wobec 141 m z rzutu); (2) końce krótkich wygiętych odcinków (ramię końcowe z wagą tylko z rampy `END_BLEND` i ramię wewnętrzne przy fałdzie). Opis „najwyżej 2 bloki na blok” był nieprawdziwy: na gęstej siatce pola żeber i uskoki pominięcia miały do 6,75 bloku na blok (runda 1). Limity okien przed rundą 1: krok doliny 3 m wszędzie poza tymi czterema oknami (4,9 / 5,2 / 4,9 / 4,8 m, stan zmierzony), bloki ≤ 2 wszędzie poza pasem brzegowym.

**Miejsca na masywach i źródła:** `gameplay_blades_1707` 0 par > 25 m, największa para 13,4 m na 2,5 m (K4b 5,9 m; zbocze doliny rzędu 1, 5,4 m na 1 m, do 1,6 bloku na blok), `gameplay_fan_1673` bez zmian (10,6 m). Źródła na stokach masywów (`massifStreamSourcesHaveNoCliffs`): GAMEPLAY zwykły krok 6,01 m na 1 m (K4b 4,06), krok doliny 5,40 m (K4b 2,54) w (247697,4, −287790,7) (remis ramion krótkiego odcinka), 1,63 bloku na blok; REAL bez zmian (1,46 / 1,29 / 1,46 bloku).

### D4b: kryterium „bez urwisk” w testach

- `SurfaceContinuityTest`: w każdym oknie krok doliny ≤ 3 m na 1 m (wyjątki D4a wyżej, stan zmierzony) **oraz** zwykły krok ≤ 2 bloki na blok (`D4B_BLOCKS`, przeliczenie przez `VerticalScale` skali okna) na suchym lądzie poza wodą stojącą i poza pasem brzegowym (3 km·meso od linii brzegu). Pas brzegowy jest wypisywany osobno: ściana od strony morza płaskiego pasa nadmorskiego leży kilka metrów nad morzem, gdzie GAMEPLAY przelicza 1 m na do 1,9 bloku, więc jej 2,2 m na 1 m daje 2,45 bloku na blok (`gameplay_center`) i 2,08 (`gameplay_moraine`); to wybrzeże przebudowuje K5b (D5). Okna REAL: 1:1 do 900 m, czyli ≤ 2 m na 1 m (największy 1,34 na 150 transektach; gęsty skan rundy 2 znalazł do 2,75 bloku na blok, odstępstwo R5).
- `mountainStreamSourcesHaveNoCliffs`: obie skale (wcześniej tylko REAL), krok doliny ≤ 3 m na 1 m i ≤ 2 bloki na blok; REAL dodatkowo zwykły krok < 3 m. Teraz: REAL 1,35 / 1,30 m / 1,35 bloku, GAMEPLAY 2,24 / 1,34 m / 0,63 bloku.
- `massifStreamSourcesHaveNoCliffs`: dodane ≤ 2 bloki na blok w obu skalach; limity kroku GAMEPLAY do stanu K4c (zwykły 6,1 m, krok doliny 5,5 m, odstępstwo D4a).
- Strome kopuły masywów GAMEPLAY (do ok. 5 m na 1 m, ok. 1,1–1,5 bloku na blok) przechodzą kryterium bloków bez wyjątków.

### A5: głowica doliny łukiem

- **Weszło:** podnoszenie dna od źródła (`floor ≥ terrain − 0,5 · maxSlope · odległość od źródła`) liczone od łuku wokół głowicy, a nie od prostej w poprzek osi: odległość od źródła minus `headArc(va, r)` = r · (1 − exp(−va² / (2 r²))), gdzie va to odległość od osi doliny, a r pełna półszerokość dna (w/2 + margines). Na osi 0, przy niej ok. va² / (2r) (parabola), daleko najwyżej r, nachylenie najwyżej 0,61. Poziomice głowicy są łukami (amfiteatr), a nie prostymi w poprzek osi. Przekroje cięcia omiatającego używają tego samego łuku (ze swoją odległością d ≥ va, więc zachowawczo).
- **Kadr `R_head_-67330_21470_800m`:** w K4 głowica była klinem zwężającego się dna, a poziomice kończyły się prostymi; po K4c poziomice głowicy są współśrodkowymi łukami, bez skoków i fałd (`k4c/viz/png/R_head_-67330_21470_800m_terrain.png`, 0 skoków, 0 pikseli > 3 m na 1 m).
- **Sprawdzone i odrzucone:** (1) szerokość dna rosnąca jak √odległości (profil √x · (3 − x)/2, gładki na końcu): głowica tępa, prawie prostokątna z zaokrąglonymi narożami, a `valleyHeadsAreRounded` pada (mediana 0,47–0,48 przy limicie 0,4); (2) łuk okręgu r − √(r² − va²): pionowa styczna przy va = r robiła stopień wzdłuż boków głowicy (5–7 skoków na kadrze).
- `valleyHeadsAreRounded` bez zmian w kodzie i bez zmian wyników (szerokość dna z marginesu A5 jak w K4): mediana 0,22 (REAL, 672 głowice) i 0,21 (GAMEPLAY, 457 z 459 poniżej 0,6), 0 kolumn wciętych za głowicą.

### G3: lejek ujścia jako dno doliny odbiorczej w polach siedlisk

- **Weszło:** (waga doliny tylko do rundy 1; od rundy 1 waga doliny jest znów bez lejka, bo usuwała oczka) u i flaga dna liczą się z dna terenu z lejkiem (`terrainHalf`), a miękkie maksimum półszerokości dna (`floorHalf` w `RiverHit`) z półszerokości terenu. Klucz doliny dominującej (F1) zostaje bez lejka, więc lejek dopływu nie przejmuje dna rzeki, do której uchodzi; kolumna w lejku jest na dnie (`inFloor`), a strefy daje jej koryto doliny dominującej (`floorChannelDist`).
- **Pierwsza próba (tylko `inFloor` i u):** ols na kadrze `Z_besk_conf_300m` wzrósł z 1,57% do 1,97%. Przyczyna: półszerokość dna potoku (2 m) zostawała wąska, więc klasa C traktowała dno jak wąską dolinę V („las strefowy do brzegu”), a strefowy las na płaskim, mokrym dnie poniżej 500 m to ols (BOGGY). Z półszerokością dna z lejkiem klasa C daje łęg z olszą szarą i łęg jesionowo-olszowy.
- **Kadr `Z_besk_conf_300m` (27990, 3660):** ols 1,57% → 0,28%, łęg z olszą szarą 0,52% → 3,10%, łęg jesionowo-olszowy 0,31% → 1,53% (`k4c/viz/png/Z_besk_conf_300m_biomes_pair.png`). Wieloboczne płaty olsu przy zbiegu zniknęły; zostały pasy łęgów wzdłuż obu potoków (zarys lejka jest nadal widoczny jako granica łęgu). Kadr `G_besk_conf2_600m` bez zmian (0,54% łęgu z olszą szarą, 0 olsu).
- **F2** bez zmian: REAL strefy rzeki 100%, całe dno 99,1%; GAMEPLAY 99,0%, całe dno 93,0% (próg 92%). `noSpringsOnMassifCore`: kolumn dna lub wody na rdzeniach REAL 1604 → 1620 (lejki liczą się teraz jako dno; najgłębsza nadal przy G 0,50), limit 1620.
- Odstępstwo 3 z K4 („lejek tylko w terenie”) jest tym zamknięte dla pól siedlisk; klucz F1 nadal bez lejka (świadomie).

### Drobne z recenzji K4

- **Pole d przy dużych rzekach:** zmierzone, bez zmian w kodzie. REAL: największy gradient w pasie stref 7,58 m na 1 m, 133 kroki > 1,5 m na 1 m z 84 949 (0,16%, próg 1%); GAMEPLAY 7,08 m, 3 kroki. Kadr stref dużej rzeki `R_duza_rzeka_2km` (−2849, 8918, W ≈ 42 m): pasy łozin, ziołorośli i łach idą równo z korytem, bez prostych szwów (strome miejsca d leżą w szyjach meandrów, gdzie oba ramiona są blisko). Wariant „minimum po ramionach z narodzinami ważonymi wyrazistością” odłożony (M5, razem z rzutem na samą oś), bo na kadrach nie ma artefaktu, który by uzasadniał zmianę pola stref.
- **`noSpringsOnMassifCore`:** wcięcie obszaru szczytowego GAMEPLAY 58,0 → 51,5 m (bez szerokiego cięcia omiatającego; K2 50,6 m, pierwsza wersja K4 51,5 m), w (109332, 286015): górna krawędź ściany doliny potoku rzędu 1, 1470 m głębokiej. Próba bez nieregularnej krawędzi dna G4 (wszędzie) dała 51,9 m, więc poszerzanie krawędzi dna nie jest przyczyną; zostaje udokumentowane (odstępstwo 11 z K4, głębokość takich dolin na kopułach to sprawa K2/S3). Limit 52 m (było 59).
- **`segmentCullingIsInvisible`:** 20 000 punktów na skalę (było 10 000, projekt: 20 000).
- **Wspólny pomocnik promienia kafli:** `tileRadiusNodes` (węzły w stałej kolejności listy kafla) używany przez `candidates` i `tileRadiusSegments`.
- **Pisownia amerykańska** w kodzie: „centre” → „center”, „metre(s)” → „meter(s)”, „neighbour…” → „neighbor…”, „kilometres” → „kilometers” (komentarze w `landscape`).

### Złoty test

Lista `src/test/golden-allow/K4.txt` uzupełniona o K4c: zbiór zmienionych łat jest ten sam co w K4 z wyjątkiem dwóch łat cięcia K4b: GAMEPLAY A `kettle_pond` znów bez zmian (wypada z listy), GAMEPLAY A `summit` zmieniona (do 1,61 m, A5 i doliny K4), ale nie traci już celu (`summit` zamiast `summit target`). `terrainMatchesGolden` z `-PgoldenAllow=src/test/golden-allow/K4.txt` przechodzi; zmiany numeryczne bez zmian. Raport `-PgoldenReport=build/golden_K4c.txt` daje dokładnie ten zbiór (łaty z utraconym celem są w nim także jako zmienione, co lista K4 obejmuje wpisem `target`).

**Domyślna lista dozwolonych zmian (nowe w K4c).** Bez `-PgoldenAllow` `build.gradle` bierze teraz listę najnowszego kroku: ostatni według nazwy plik `src/test/golden-allow/K*.txt` (teraz `K4.txt`). Dzięki temu zwykłe `./gradlew test` przechodzi między krokami poprawki, a nie kończy się zamierzoną porażką `terrainMatchesGolden` jak od K3. W K7 katalog znika razem z przegenerowaniem pliku wzorcowego i wtedy domyślnie nic nie jest dozwolone. Porównanie bez żadnej listy: `-PgoldenStrict`. Uwaga: nowa lista kroku (np. `K5.txt`) musi mieć nazwę sortującą się po poprzednich; po jej dodaniu warto wymusić ponowną konfigurację (`--no-configuration-cache` przy pierwszym uruchomieniu), bo wybór pliku zapada w fazie konfiguracji.

### Koszt

Pomiar A/B `SampleCostTest` (7 rund) K4b (`006e2d5`) i K4c, osobne JVM na zmianę, 2 powtórzenia; maszyna obciążona (kopia M1 o kilka–kilkanaście procent wolniejsza niż w pomiarach K3), więc porównywać stosunki. Stosunek do kopii M1 (mediana):

| Obszar | K4b | K4c |
|---|---|---|
| REAL cały obszar | 1,076 / 1,068 | 1,083 / 1,091 |
| REAL Beskidy | 1,007 / 0,984 | 1,061 / 1,036 |
| REAL wielki masyw | 0,969 / 0,949 | 0,967 / 0,993 |
| GAMEPLAY cały obszar | 1,098 / 1,089 | 1,103 / 1,063 |
| GAMEPLAY Beskidy | 1,091 / 1,080 | 1,049 / 1,055 |
| GAMEPLAY wielki masyw | 1,050 / 1,036 | 0,974 / 0,974 |

µs na kolumnę w K4c (drugie powtórzenie): 5,14 / 9,18 / 8,63 / 6,76 / 12,11 / 13,49; budżet D1 (≤ 1,20 × M1; 6,5 / 12 / 8,5 / 16 µs) dotrzymany. Cięcie omiatające liczy się teraz tylko przy niewyraźnym rzucie: 0,21 (GAMEPLAY) i 0,12 (REAL) wejść na kolumnę, 0,75 i 0,18 wywołań `wanderAt` na kolumnę (wersja bez pominięcia: 2,9 i 1,9 wejść, 11,1 i 4,8 wywołań; GAMEPLAY cały obszar 1,21–1,23 × M1, ponad budżet). Łuk głowicy (jedno `exp` na odcinek źródłowy w zasięgu) i G3 nie zmieniają kosztu mierzalnie.

### Testy

**Nowe i zmienione:** `sweepCutKeepsWellConditionedTerrain` (nowy, wyżej); `SurfaceContinuityTest` (bloki na blok, limit kroku doliny na okno, limity skoków do stanu K4c, `gameplay_blades_1707` 13,5 m); `mountainStreamSourcesHaveNoCliffs` (obie skale, krok doliny i bloki); `massifStreamSourcesHaveNoCliffs` (bloki, limity GAMEPLAY); `noSpringsOnMassifCore` (limity 52 m i 1620 kolumn); `segmentCullingIsInvisible` (20 000 punktów).

**Pełny `./gradlew test`** (bez flag, czyli z domyślną listą `K4.txt`): BUILD SUCCESSFUL w 15 min 40 s, 132 testy, 0 porażek, 0 pominiętych. `terrainMatchesGolden` przechodzi z listą (zmiany łat jak w „Złoty test” wyżej). `sweepCutKeepsWellConditionedTerrain`: w punktach wyraźnych 0 m w obu skalach. `SampleCostTest` w pełnym przebiegu (maszyna obciążona innymi testami): 1,200 / 1,187 / 0,977 / 1,133 / 1,114 / 1,029 × M1 w kolejności tabeli kosztu, µs na kolumnę 5,81 / 9,55 / 7,49 / 13,02 (limity bezwzględne D1 6,5 / 12 / 8,5 / 16 µs dotrzymane); osobny przebieg samego `SampleCostTest` zaraz potem: 1,094 / 1,023 / 0,919 / 1,045 / 1,049 / 0,961 × M1, µs 6,23 / 9,46 / 7,19 / 13,06.

### Odstępstwa od projektu w K4c

1. **D4a, wariant zapasowy:** teren bez cięcia w punktach wyraźnych (0 m) zamiast kroku doliny ≤ 3 m wszędzie. Stan po rundzie 1: w czterech oknach GAMEPLAY remisy ramion z krokiem doliny do 4,47 m na 1 m na gęstej siatce 2 m, wszędzie ≤ 1,56 bloku na blok (odstępstwo R1 niżej).
2. ~~Pominięcie cięcia omiatającego przy wyraźnym rzucie~~: **usunięte w rundzie 1** (robiło uskoki do 20 m). Zamiast niego dowiedzione przycinanie przekrojów, test `sweepCutPruneIsExact`.
3. **Kryterium bloków poza pasem brzegowym:** ściana brzegowa pasa nadmorskiego (2,45 bloku na blok) czeka na K5b.
4. **A5 łukiem podnoszenia dna, nie szerokością dna:** szerokość dna (margines A5) zostaje jak w K4, łuk daje kształt poziomic; profil √odległości dla szerokości odrzucony (głowica tępa, test głowic pada).
5. **G3: półszerokość dna w polach z lejkiem** (poza `inFloor`/u z decyzji): bez niej ols na zbiegach rósł.
6. **Pole d bez zmian** (zmierzone, bez artefaktów na kadrze dużej rzeki).
7. **Wcięcie obszaru szczytowego GAMEPLAY 51,5 m** (> 50 m): udokumentowane, sprawa K2/S3.
8. **Domyślna lista dozwolonych zmian w `build.gradle`** (poza projektem, §4 zakładał jawne `-PgoldenAllow`): zwykłe `./gradlew test` używa listy najnowszego kroku; ścisłe porównanie przez `-PgoldenStrict`.

### Co zostaje

- Remisy ramion z D4a (po rundzie 1: krok doliny do 4,47 m na 1 m w czterech oknach GAMEPLAY na gęstej siatce, ≤ 1,56 bloku na blok): M5, razem z rzutem na samą oś doliny (wtedy średnia miękka nie będzie mieszać den odległych ramion).
- Izolowane skoki na zboczach dolin rzędu 1 w `gameplay_massif_1660` (po rundzie 1: 2 skoki, 0,33 i 1,14 m, zagięcia między dwoma przekrojami bez uskoku) i `gameplay_massif_1710` (1, 0,47 m): wąskie pasy, gdzie stromy jest sam rzut.
- Szew A16 w `gameplay_stream` (skoki do 3,02 m): K6.
- Ściana brzegowa pasa nadmorskiego (2,45 bloku na blok w pasie brzegowym): K5b.
- Zarys lejka G3 widoczny jako granica pasa łęgów na zbiegach potoków (bez olsu): etap poprawek klasyfikatora siedlisk (commit 3; runda 2).

### Runda 1 poprawek po recenzji K4c

Stan przed rundą: K4c w drzewie roboczym na `006e2d5`, niezacommitowany.

Recenzja K4c znalazła pionowe uskoki na granicy pominięcia cięcia omiatającego, tautologiczny test regresji, pola żeber (tarkę) przy remisach, szerokie podniesienie zboczy przez łuk głowicy A5 w GAMEPLAY i usuwanie oczek przez lejek G3. Poniżej co z każdą uwagą zrobiłem. Opisy wyżej w rozdziale K4c (punkt o pominięciu przy wyraźnym rzucie, akapit „Dlaczego to nie zmienia terenu…”, wynik testu regresji i tabela okien) opisują stan przed tą rundą; tam, gdzie były nieprawdziwe, są poprawione niżej.

**Pominięcie przy „wyraźnym” rzucie było błędne i zostało usunięte.** Teza „przy jednym wyraźnym ramieniu żaden przekrój nie przekroczy ramienia” jest fałszywa. Sprawdziłem trzy punkty z recenzji narzędziem `Probe`/`Scan` (przekroje S(t) na 64–128 punktach):

- GAMEPLAY (27656,4; 2595,9): ramię odcinka rzędu 1 (194 m) w t = 0,67 ma g = 1,08, ale koniec t = 1 leży tylko 3 m dalej (376,9 wobec 373,9 m) i jest prawie stacjonarny (along = −0,01 m). Dno na końcu jest o 9,6 m niżej, więc przekrój końca tnie 16,6 m głębiej. To prawie-remis z nienarodzonym jeszcze ramieniem końcowym; kryterium ramion go nie widzi.
- GAMEPLAY (26646,5; 1308,4): dwa ramiona, g = 0,706 tuż przy progu 0,7 — szew progu.
- REAL (152760,95; 1054313): punkt leży prawie na krzywej przy jej bliskim ostrzu (prędkość parametryzacji |P'| spada do ok. 60 przy długości odcinka 1265 m), a ugięcie osi zmienia się tam o 45 m na 3 m krzywej. Rzut mierzy od osi 70 m, przekroje obok ostrza 28–35 m.

Wszystkie trzy to prawie-remisy, w których cięcie omiatające ma działać; pominięcie zamieniało je w uskok na granicy obszaru pomijanego. Teraz `query` liczy cięcie omiatające zawsze (bez flagi `out[9]` i bez okna wokół ramienia; okno w jednej z prób też dawało różnice do 37 m przy ostrzu, więc je odrzuciłem).

**Ciągły wybór przekroju zamiast stałej rodziny węzłów (żebra).** `Scan` pokazał, że przy remisach S(t) ma wąskie maksima między węzłami (np. 313 m przy t ≈ 0,98 wobec 298 m w najbliższym węźle), więc maksimum po 16 węzłach przeskakiwało z węzła na węzeł i każdy węzeł dawał płaską fasetę (kara |along| w metryce L1). Maksima S(t) leżą w spodkach ramion (załamanie dna tLo) i w prawie stacjonarnych punktach odległości. Cięcie bierze teraz maksimum po węzłach, po **dokładnych ramionach rzutu** (pierwiastki f) i po **ekstremach f** (pierwiastki f', ramiona „w zarodku”). Ramię rodzi się tam, gdzie ekstremum f dochodzi do 0, więc wchodzi do maksimum z wartością, którą ekstremum już miało; zbiór próbek zmienia się bez skoku maksimum. (Runda 2: to zdanie było prawdziwe tylko dla ramion. Same ekstrema f rodzą się parami tam, gdzie f'' = 0, i wychodzą przez końce odcinka, a w obu miejscach maksimum skakało; poprawka niżej.) Pojedynczego ramienia nie liczę (to sam rzut, nie głębszy niż limit). Sprawdzone i odrzucone: złoty podział między węzłami (skoki do 27 m, gdy kandydat do doprecyzowania zmieniał się z kolumną), 8 i 4 węzły (żebra wracają: do 16,8 m na 1 m), iterowany Newton (3 kroki uciekają od punktu prawie stacjonarnego: 17 m na 0,4 m).

**Strome przekroje: g ≥ 1 i dolna granica prędkości.** Oszacowanie spodka t + along/(|P'|·g) przesuwa się z punktem o 1/(|P'|·g) na metr, a z nim ugięcie osi i dno brane w tym miejscu. Przy g do 0,5 przekroje dalekie od spodka opadały do 7 m na 1 m (2,4 bloku na blok na masywach), więc `SWEEP_MIN_DISTINCTNESS` = 1 (dla 0,5 ≤ g < 1 krok jest za krótki najwyżej o połowę, co pokrywa `SWEEP_FLOOR_MARGIN`). Przy ostrzu krzywej prędkość spada do 1/20 długości i przekrój opadał o 27 m na 0,5 m (REAL (152745, 1054317)), więc w kroku Newtona prędkość jest co najmniej 0,4 długości odcinka (`SWEEP_MIN_SPEED`).

**Koszt bez pominięcia.** Bez pominięcia cięcie kosztowało w GAMEPLAY 0,2–0,3 × M1. Zamiast niezabezpieczonego pominięcia jest dowiedzione przycinanie przekrojów (`Section.notDeeperThanArm`): przy jednym ramieniu, na odcinku o dnie opadającym i korycie rozszerzającym się w dół, przekrój, którego przedział [tLo, tHi] zawiera ramię t*, którego dolne ograniczenie odległości dna (zakres ugięcia osi w przedziale t, 128 przedziałów na odcinek, największy pas meandrów odcinka) nie jest mniejsze niż odległość dna ramienia, i którego tLo · len nie przekracza podniesienia głowicy w ramieniu, nie tnie głębiej niż ramię (cięcie maleje z odległością dna, rośnie z głębokością dna, półszerokością i ścianą; tLo ≤ t* daje dno nie niższe i półszerokość nie większą). Pozostałe przekroje mają tańsze ograniczenia przed szumem ugięcia: głębokość z dna w t i w tLo, ugięcie z zakresu przedziału, półszerokość bez smoothstepów; wartości ugięcia do granic liczone raz przy budowie odcinka. Wywołań `wanderAt` w przekrojach: masyw GAMEPLAY 12,6 → 0,39 na kolumnę, cały obszar GAMEPLAY 0,30, REAL 0,02.

**Test regresji (D4a) nie jest już tautologią.** `sweepCutKeepsWellConditionedTerrain` porównuje `query` (pełne cięcie, bez pominięcia) z `queryWithoutSweep`. Punkt wyraźny: każdy tnący odcinek ma jedno dominujące ramię (g ≥ 0,5, udział ≥ 0,999, koniec tylko `END_BLEND` za punktem) **i** żadna inna część krzywej nie jest prawie równie blisko (próbki t = j/256 poza otoczeniem ramienia: |along| ≥ 0,2 · |X − P(t)|). Wynik: REAL 7500 punktów lądu, 1249 wyraźnych, zmiana w wyraźnych 0 m, żadnej zmiany > 0,05 m; GAMEPLAY 6784 / 1343 wyraźnych, w wyraźnych 0 m, > 1 m 16 punktów (0,24% lądu, limit 0,5%), największa zmiana 59 m (remis na masywie). Punkty recenzji (27656, 2596) GAMEPLAY i (152761, 1054313) REAL nowa definicja zalicza do prawie-remisów (koniec odcinka 3 m dalej niż ramię, bliskie ostrze krzywej); ich ciągłość sprawdza test niżej.

**Nowy test `sweepCutIsContinuousAtTheReviewedScarps`.** Siatka 0,5 m na kwadratach 80 m wokół 13 miejsc z recenzji (siedem uskoków pominięcia, żebra T_tie, F_tie, C_besk i trzy miejsca na masywach). Kryteria: krok między sąsiadami ≤ 3 m na 0,5 m (uskoki pominięcia miały 3–20 m na 0,25 m) i ≤ 2 bloki na blok na 1 m. Stan: największy krok 2,35 m na 0,5 m (zbocze doliny w (70381,5; −32437)), najwięcej 1,80 bloku na blok (REAL przy ostrzu), w GAMEPLAY do 1,36 bloku na blok.

**Gęsty skan okien górskich (`SurfaceContinuityTest.denseGridHasNoCliffs`, nowy).** Siatka 2 m w oknach `gameplay_beskids` (6 km) i czterech oknach masywów (4 km), łącznie ok. 25 mln kolumn, ok. 70 s: w tych oknach GAMEPLAY wszędzie ≤ 1,56 bloku na blok, żadnej pary > 2 bloków (okien REAL ten test nie obejmował; runda 2: odstępstwo R5); krok doliny do 4,26 / 4,47 / 4,09 / 3,50 / 2,97 m na 1 m (limity okien: stan zmierzony, `DENSE_VALLEY`). Limity transektów ustawione na stan zmierzony: krok doliny `gameplay_beskids` 4,89 → 2,40 m (z powrotem limit 3 m), `gameplay_massif_spawn` 5,10 → 3,84, `gameplay_massif_1660` 4,78 → 3,14, `gameplay_massif_1718` 4,71 → 2,96 (limit 3 m). W `gameplay_massif_1660` dwa skoki transektów (0,33 i 1,14 m) to zagięcia, gdzie remisują dwa przekroje (spadek 1,5 → 3,7 m na 1 m bez uskoku, profil co 0,25 m: (69755, −29217)); limit 1,15 m. Źródła na masywach GAMEPLAY: zwykły krok 6,01 → 4,74 m na 1 m, krok doliny 5,40 → 3,27 m (limity 4,8 i 3,3).

**Przycinanie jest dokładne (nowy test `RiverNetworkTest.sweepCutPruneIsExact`).** Recenzja żądała gęstego porównania wariantu produkcyjnego z pełnym cięciem, które nie przechodzi przy żadnej różnicy. Pominięcia już nie ma, a jego następca, `notDeeperThanArm`, odrzuca tylko przekroje nie głębsze od ramienia. Ramię nie przekracza progu `limit`, od którego cięcie omiatające w ogóle działa, więc teren z przycinaniem i bez niego musi być identyczny co do bitu. Pakietowy wariant `queryWithoutPrune` (tylko dla testów, parametr `prune` w wewnętrznym `query`) liczy cięcie bez przycinania. Test porównuje obie wersje bez żadnej tolerancji:
- siatki 401 × 401 we wszystkich oknach górskich (`gameplay_beskids`, cztery okna masywów GAMEPLAY, `realistic_beskids`, `realistic_massif_1723`);
- siatki 0,5 m na kwadratach 80 m w miejscach uskoków i żeber z recenzji.

Wynik: GAMEPLAY 1 011 373 kolumny lądu, w tym 944 418 w dolinach; REAL 373 444 i 148 696; różnic 0.

Na kopii z przełącznikiem (narzędzie `PruneAB`) porównałem całą powierzchnię `sample` na gęstszych siatkach: Beskidy GAMEPLAY co 3 m, masywy co 2 m, centrum co 10 m, Beskidy REAL co 30 m, masyw REAL co 8 m, miejsca z recenzji co 0,25–0,5 m. Różnica wszędzie 0 m.

Przycinanie naprawdę działa (narzędzie `PruneCount`):

| Obszar | Przekroje odrzucone przed szumem ugięcia (na kolumnę) | Dokładne przekroje (na kolumnę) |
|---|---|---|
| masyw 1660, GAMEPLAY | 17,8 | 0,41 |
| Beskidy, GAMEPLAY | 17,7 | 0,36 |
| Beskidy, REAL | 3,0 | 0,05 |

**Ząbki w oknie `gameplay_massif_1660` (uwaga główna nr 3).** Sprawdziłem siatkę 1 m na dwóch prostokątach z recenzji i siatkę 2 m na całym oknie (narzędzie `Teeth`), porównując teren rzeczny z cięciem i sam rzut.

Wiersz z = −30236 ma teraz gładkie nachylenia: 5,2 → 7,8 → 7,0 m na 10 m, a potem 15,7 m, tak jak w samym rzucie, który daje tam 12,0–15,6. Przed rundą były to ząbki 8,5 / 9,2 / 6,1 / 9,8 / 8,6 / 5,7 / … m.

Piksele z załamaniem > 0,4 m/m na piksel, wśród pikseli zmienionych przez cięcie omiatające:

| Obszar | Z cięciem omiatającym | Sam rzut |
|---|---|---|
| prostokąt (69230–69500, −30270…−30230) | 46 | 60 |
| prostokąt (69630–69790, −29470…−29320) | 0 | 25 |
| całe okno | 4889 | 5852 |

Cięcie omiatające nie dokłada już faset, tylko je wygładza. Największe nachylenie w oknie spada z 17,06 m na 1 m w samym rzucie do 4,85.

**Kadry (narzędzie `VizK4c` recenzenta, 0,25–0,5 m/px, `jumpstat.py`):**

| Kadr | K4b | K4c przed rundą | teraz |
|---|---|---|---|
| `F_seam_27652_2592` | 2,61 m / 0,72 bl. | uskok 17,95 m na 1 m, 4,72 bl. | 3,20 m / 0,90 bl., bez skoków |
| `F_seam_70803_-31977` | 2,28 / 0,74 | 21,58 m, 6,75 bl. | 3,83 / 1,19, bez skoków |
| `Cspawn_400m` | — | 20,07 m, 5,51 bl. | 2,82 / 0,87 (13 px skoku jak w K4b) |
| `R_seam_126919_1032083` | 0,55 m | 3,04 m, 1026 px skoku | 1,10 m, bez skoków |
| `S_skip_-1415_2545` | 0,43 / 0,19 | 3,63 / 1,49 | 0,87 / 0,40 |
| `T_tie_27108_2147` | 2,44 / 0,75 | 5,78 / 1,59, 71 px skoku | 2,47 / 0,75, bez żeber (jedno zagięcie między dolinami) |
| `F_tie_70420_-31550` | 3,37 / 0,92 | 9,56 / 2,53, pole żeber | 4,85 / 1,33, bez żeber (zostaje stromy pas rzutu) |
| `C_besk_28492_872` | 1,66 / 0,53 | 5,07 / 1,59 | 2,97 / 0,95 |
| `C_m1660_71512_-31618` | 2,91 / 0,93 | 5,72 / 1,79 | 4,60 / 1,33 |
| `C_m1718_-216918_247939` | 2,57 / 0,82 | 6,44 / 2,11 | 4,33 / 1,54 |
| `C_m1718_-217767_244581` | 2,28 / 0,69 | 6,86 / 2,12 | 4,61 / 1,45 |
| `C_spawn_5404_-33220` | 2,79 / 0,82 | 20,64 / 5,66 | 3,17 / 0,97 |

(największy zwykły krok na 1 m / bloki na blok; „px skoku” to piksele detektora skoków).

**A5: łuk głowicy wygaszony od źródła.** `headRise` = odległość od źródła − `headArc(va, r)` · (1 − smoothstep(r, 3r, odległość od źródła)); nadal niemalejące w t, więc dno dalej rośnie monotonnie w górę. A/B łuk / bez łuku na 40 tys. punktów recenzji: GAMEPLAY zmienione o > 1 m 11,66% → 0,41% lądu (do +3,2 m), REAL 0,60% → 0,02% (do +2,5 m); rynna wzdłuż suchej głowicy znika razem z szerokim podniesieniem. `valleyHeadsAreRounded` bez zmian (mediana 0,22 / 0,21). Uwaga drobna recenzji (najbliższa dnu poziomica głowicy nizinnej prosta na ok. 70 m przy dużym r) zostaje: mniejsze r przy wygaszeniu ograniczyłoby łuk do kilkudziesięciu metrów od źródła; do oceny na kadrach K7.

**G3: waga doliny bez lejka.** `valleyWeight` decyduje tylko o terenie (istnienie oczek i obecność rynien jeziornych w `LandscapeModel`), nie o siedliskach, więc liczy się znowu z `floorHalf` bez lejka; lejek zostaje w `inFloor`, u i półszerokości dna pól siedlisk. Wyliczenie wszystkich komórek oczek (narzędzie `kettles` recenzji): REAL ±150 km 18 710 (jak K4b; K4c przed rundą 18 699), GAMEPLAY ±40 km 6227 (jak K4b; było 6202). Wieloboczne płaty łęgów przy zbiegach (uwaga główna nr 6) **nie są poprawione**: proste krawędzie biorą się z prostych potoków i sumy den (dno potoku ∪ lejek ∪ dno doliny odbiorczej), a ostre narożniki z przecięć tych zarysów; usunięcie wymaga zmiany klasyfikatora siedlisk — odkładam do etapu poprawek klasyfikatora siedlisk (commit 3 w decyzjach wdrożenia), a nie do K7 (poprawione w rundzie 2; tam też sprawdzenie, że pola dna z `RiverNetwork` są gładkie).

**Drobne.** Ograniczenia całego odcinka i bloku odejmują teraz c/2 kary Hubera (było niezachowawcze o 2,5 m·k). Nagłówek `K4.txt` wskazuje raport `build/golden_K4c.txt`.

**Pomiary A/B całości wobec K4c sprzed rundy (40 tys. punktów recenzji):** GAMEPLAY zmienione o > 1 m 11,2% lądu (prawie całe z wygaszenia łuku A5, najwyżej −9,4 m), REAL 0,58% (do −6,3 m); wobec wariantu recenzji bez łuku: GAMEPLAY 0,42%, REAL 0,02%. Cięcie omiatające wobec samego rzutu (punkty testu regresji): REAL 0,00% lądu > 1 m, GAMEPLAY 0,24%.

**Koszt po rundzie 1** (`SampleCostTest`, stosunek do kopii M1, mediana):

| Przebieg | REAL cały | REAL Beskidy | REAL masyw | GAMEPLAY cały | GAMEPLAY Beskidy | GAMEPLAY masyw |
|---|---|---|---|---|---|---|
| sam test, przebieg 1 | 1,175 | 1,094 | 0,997 | 1,154 | 1,144 | 1,115 |
| sam test, przebieg 2 | 1,167 | 1,106 | 0,985 | 1,165 | 1,145 | 1,101 |
| pełny przebieg obciążony innymi testami | 1,197 | 1,211 | 1,007 | 1,179 | 1,188 | 1,135 |

W przebiegach osobnych µs na kolumnę wynoszą 4,60–4,76 (REAL cały), 7,14–7,20 (REAL Beskidy), 6,25–6,30 (GAMEPLAY cały) i 11,04–11,24 (GAMEPLAY Beskidy).

Budżet D1 (≤ 1,20 × M1) jest dotrzymany w przebiegach osobnych, a limity bezwzględne (6,5 / 12 / 8,5 / 16 µs) we wszystkich. W pełnym przebiegu REAL Beskidy wyszły 1,211, ale tylko przez szum maszyny: ze średnich minimów rund wychodzi 1,125. Wzrost wobec K4c sprzed rundy (osobno 1,09 / 1,02 / 0,92 / 1,05 / 1,05 / 0,96) to cena liczenia cięcia omiatającego wszędzie.

**Złoty test.** Raport `-PgoldenReport` po rundzie (`k4c1/rep_new.txt`) zawiera ten sam zbiór zmienionych łat co przed rundą, więc treść listy `K4.txt` się nie zmieniła; zmienił się tylko nagłówek (nazwa raportu). Domyślne `./gradlew test` (z listą `K4.txt`) przechodzi.

**Testy.** Nowe testy:
- `RiverNetworkTest.sweepCutIsContinuousAtTheReviewedScarps`;
- `RiverNetworkTest.sweepCutPruneIsExact`;
- `SurfaceContinuityTest.denseGridHasNoCliffs` (5 okien).

Zmienione:
- `sweepCutKeepsWellConditionedTerrain`: bez tautologii, ostrzejsza definicja punktu wyraźnego;
- limity okien `SurfaceContinuityTest` ustawione na stan zmierzony;
- `massifStreamSourcesHaveNoCliffs`: limity GAMEPLAY 4,8 i 3,3 m.

Pełny `./gradlew test` (bez flag, czyli z domyślną listą `K4.txt`, plus `-PgoldenReport=build/golden_K4c_r1.txt`): BUILD SUCCESSFUL w 12 min 46 s, 139 testów, 0 porażek, 0 błędów, 0 pominiętych. Szczegóły:
- `sweepCutPruneIsExact`: 0 różnic;
- `denseGridHasNoCliffs`: 1,38 / 1,40 / 1,25 / 1,56 / 1,28 bloku na blok, krok doliny 4,26 / 4,47 / 4,09 / 3,50 / 2,97 m na 1 m;
- raport złotego testu: ten sam zbiór łat co przed rundą;
- `SampleCostTest` w tym przebiegu: 1,186 / 1,115 / 1,015 / 1,149 / 1,134 / 1,081 × M1, µs 4,58 / 7,21 / 6,35 / 11,02.

**Odstępstwa rundy 1:**

1. **R1, D4a nadal w wariancie zapasowym.** Krok doliny ≤ 3 m na 1 m nie jest spełniony wszędzie. Na gęstej siatce 2 m:

   | Okno | Krok doliny (m na 1 m) |
   |---|---|
   | `gameplay_beskids` | 4,26 |
   | `gameplay_massif_spawn` | 4,47 |
   | `gameplay_massif_1660` | 4,09 |
   | `gameplay_massif_1718` | 3,50 |

   Limity `DENSE_VALLEY` są ustawione na stan zmierzony. Kryterium D4b (≤ 2 bloki na blok) jest spełnione w oknach GAMEPLAY gęstej siatki: najwięcej 1,56. To zbocza stromych przekrojów przy remisach ramion, bez uskoków i bez żeber. (Runda 2: w REAL D4b nie jest spełnione, do 2,75 bloku na blok; odstępstwo R5.)
2. **R2, G3: wieloboczne płaty łęgów przy zbiegach zostają** (uwaga główna nr 6 i drobna nr 4). Proste krawędzie i narożniki płatów to zarys sumy den (dno potoku ∪ lejek ∪ dno doliny odbiorczej). Klasyfikator siedlisk bierze go z twardej flagi `inFloor` (`HabitatClassifier.onValleyFloor`, `u()`). Usunięcie wymaga zmiany klasyfikatora i oceny na kadrach biomów, więc odkładam to do etapu poprawek klasyfikatora siedlisk (commit 3 w decyzjach wdrożenia; w rundzie 2 poprawione z „K7”, bo K7 tylko przegenerowuje plik wzorcowy). Ilościowo cel G3 jest spełniony: olsu na kadrze `Z_besk_conf_300m` jest 0,28%, a było 1,57%.
3. **R3, A5 na nizinach.** Najbliższa dnu poziomica głowicy nizinnej biegnie prosto na ok. 70 m (drobna nr 3, duże r = w/2 + margines). Zmniejszenie r skróciłoby łuk do kilkudziesięciu metrów od źródła. Do strojenia (§6 projektu) po obejrzeniu świata (w rundzie 2 poprawione z „K7”: K7 nie zmienia terenu).
4. **R4, nowe wejście testowe `queryWithoutPrune`** (parametr `prune` w wewnętrznym `query`). Kod produkcyjny zawsze przycina; wariant bez przycinania jest tylko dla testów.

**Co zostaje po rundzie 1:**
- remisy ramion D4a (krok doliny do 4,47 m na 1 m): M5, razem z rzutem na samą oś;
- wieloboczne płaty łęgów G3: etap poprawek klasyfikatora siedlisk (commit 3); prosta poziomica nizinnej głowicy A5: strojenie po obejrzeniu świata (runda 2);
- koszt REAL w obciążonym pełnym przebiegu blisko granicy budżetu: pilnować w K5–K6;
- szew A16: K6;
- ściana brzegowa: K5b.

### Runda 2 poprawek po recenzji K4c

Stan przed rundą: K4c i runda 1 w drzewie roboczym na `006e2d5`, niezacommitowane. Narzędzia rundy (katalog roboczy sesji `k4c3`):
- `K3D jumps`: skaner recenzji. Bierze kafle 32 m z aktywnym cięciem omiatającym, wykrytym na siatce 2 m (GAMEPLAY) lub 16 m (REAL). Te kafle skanuje siatką 0,5 m (GAMEPLAY) albo 1 m (REAL). Pary sąsiadów, których punkt środkowy nie dzieli różnicy, bada bisekcją w 34 krokach.
- `K3D swab`: A/B cięcia na losowych punktach.
- `K3Dbg`: wypis członków rodziny przekrojów i pól odcinka w punkcie.
- `VizK4`: kadry biomów.

**1. Nieciągłości maksimum cięcia omiatającego (uwaga blokująca): prawdziwe, naprawione.**

Odtworzyłem wszystkie cztery miejsca z recenzji (`K3D jumps`, bisekcja):

| Miejsce | Skok | Mechanizm |
|---|---|---|
| REAL (132704; 1042536,5) | 1,97 m | (b) |
| REAL (131841,4; 1086475) | 0,49 m | (b) |
| GAMEPLAY (−216251; 246904,75) | 0,58 m | (a) |
| REAL (156980,5; 1059412) | 12,8 m terenu rzecznego, pod jeziorem | (a) |

Wypis członków rodziny potwierdza przyczynę. W (156978,5; 1059408,85) para ekstremów f w t = 0,8940 i 0,8952 daje cięcie 10,4 i 11,5 m. 2,5 cm dalej tej pary już nie ma, a węzły dają najwyżej 0,6 m.

Zdanie z rundy 1 „zbiór próbek zmienia się bez skoku maksimum” było prawdziwe tylko dla ramion. Maksimum rodziny jest ciągłe wtedy, gdy każdy człon jest ciągłą funkcją (x, z), dopóki istnieje, i wchodzi do rodziny albo z niej wychodzi z wartością nie większą niż maksimum pozostałych. Ekstrema f łamały ten warunek na dwa sposoby:
- (a) para pierwiastków f' rodzi się tam, gdzie f'' = 0, i od razu wchodziła z pełną wartością;
- (b) pierwiastek f' wychodzi przez koniec odcinka. Przy samym końcu przekrój liczył się wzorem wnętrza (|lat − w| + kara Hubera), a węzeł końcowy wzorem ramienia końcowego (√(lw² + along²)). Wzór wnętrza może być mniejszy o najwyżej c/2 = 2,5 m·k odległości.

Co weszło (`RiverNetwork.sweepCut`, `Section.fromFoot`):
- **(a) Waga ekstremum f.** Waga to smoothstep(0, `SWEEP_TWIN` · k, ξ), gdzie ξ = f''² / (2 |f'''| |P''|), a `SWEEP_TWIN` = 10 m·k. Przy narodzinach pary f' ≈ a + b (t − t0)², więc a = −f''² / (2 f''') w każdym z pierwiastków, a kolumna zmienia a o |P''| na metr. ξ szacuje więc odległość kolumny (w metrach) od miejsca narodzin pary: jest 0 przy narodzinach i rośnie liniowo z odległością. Nowa para wchodzi z wagą 0.
- **(a) Waga ramienia.** Ramię ma wagę 1 − (1 − w)(1 − smoothstep(0, `SWEEP_ARM_BIRTH`, g)), gdzie w to waga z ξ w jego t, a `SWEEP_ARM_BIRTH` = 0,05. Ramię rodzi się z ekstremum f przy g = 0, ma wtedy jego wagę i wchodzi z wartością, którą ekstremum już miało. Od g = 0,05 ma pełną wagę.
- **(b) Koniec odcinka.** W ostatniej i w pierwszej 1/16 odcinka, gdy kolumna leży za normalną po stronie końca, odległość przekroju przechodzi w odległość ramienia końcowego √(lw² + along²), jeśli ta jest większa. W samym końcu ekstremum ma więc najwyżej wartość węzła końcowego. ~~Wyjątek: daleki koniec odcinka źródłowego krótszego niż 3 r łuku głowicy (A5)~~ (K5: wyjątku nie ma; łuk głowicy `headArc` rośnie z odległością od osi, więc większa odległość podnosi dno łuku, a cięcie jest nierosnące w odległości bez wyjątku). Węzły wewnętrzne się nie zmieniają (mieszanie jest 0 w t = k/16), ramiona też nie (along = 0). Odległość tylko rośnie, więc wszystkie ograniczenia i przycinanie zostają ważne.
- Javadoc `sweepCut` i `fromFoot` mówi teraz ten warunek wprost i opisuje cztery przypadki zmian rodziny.

Sprawdzone i odrzucone:
- **waga z |f''| / |P'|² (czyli |g'|):** przy ostrzu krzywej prędkość |P'| jest mała. Para w t ≈ 0,894, odległa o 0,0012 w t, miała wagę 1 i skok 10,9 m zostawał;
- **waga z |f''| / len²:** ta sama para dostawała 0,19, czyli zostawał skok ok. 1,6 m. Miara ξ w metrach daje tej parze 0,0000;
- **trwałość (persistence) ekstremów albo „bariera” do sąsiednich ekstremów lub końców:** gdy obok rodzi się inna para, zmienia się sąsiad, więc waga skacze. Odrzucone bez wdrażania;
- **max(wzór końca, wzór wnętrza) w węźle końcowym:** pogłębiałoby teren za końcem każdego odcinka o do 2,5 m·k odległości, także przy dobrze uwarunkowanym rzucie (np. przy głowicach źródeł). To byłoby wbrew D4a.

Wyniki:
- **Nowy test `RiverNetworkTest.sweepCutIsContinuousWhereItsFamilyChanges`** obejmuje cztery miejsca z recenzji: siatka 0,5 m na kwadratach 24 m, teren rzeczny `query`. Każdą parę sąsiadów, która różni się o > 0,02 m i której punkt środkowy nie dzieli różnicy, test bada bisekcją w 36 krokach. Limit to 0,01 m. Przed poprawką test pada (0,576 m w GAMEPLAY), teraz największy krok po bisekcji wynosi 0,000000 m we wszystkich czterech miejscach.
- **Skan okien `K3D jumps`** (powierzchnia na suchym lądzie). Nieciągłości zrobione przez cięcie, czyli skok z cięciem większy od skoku bez cięcia o > 0,02 m:
  - `realistic_beskids` (60 km): 15 według recenzji (6 przy moim wykrywaniu co 16 m) → 0;
  - `gameplay_massif_1718`: 3 → 0;
  - `gameplay_massif_1660`: 1 → 1. Ten jeden to 0,04 m przy (70414,75; −31444,5): sam rzut skacze tam o 0,039 m (profil co 0,1 mm), więc to skok rzutu. Skaner liczy go jako zrobiony przez cięcie, bo bisekcja bez cięcia zbiega gdzie indziej;
  - pozostałe okna GAMEPLAY i REAL: 0.

  Największe kroki w blokach się nie zmieniły: GAMEPLAY 1,54 / 1,37 / 1,73 / 1,81 / 0,93 / 0,97 / 1,08; REAL 2,75 / 0,85 / 2,17 (odstępstwo R5 niżej).
- **Czwarte miejsce (pod jeziorem)** nie ma już skoku. Zostaje jednak bardzo strome zbocze samego rzutu przy ostrzu krzywej, do 14,9 m na 1 m terenu rzecznego: zakrywała je para ekstremów, która rodzi się tuż obok i ma teraz wagę bliską 0. Leży pod lustrem jeziora (powierzchnia 491,88 m), więc jest niewidoczne. Na suchym lądzie skan okien nie znalazł takiego miejsca: największe kroki w blokach są bez zmian.
- **Pozostałe testy cięcia** dają to samo co po rundzie 1:
  - `sweepCutKeepsWellConditionedTerrain`: REAL 0 zmian; GAMEPLAY 16 punktów > 1 m (0,24%), najwięcej 59 m;
  - `sweepCutIsContinuousAtTheReviewedScarps`: 2,35 m na 0,5 m, 1,80 bloku na blok;
  - `denseGridHasNoCliffs`: 1,38 / 1,40 / 1,25 / 1,56 / 1,28 bloku na blok, krok doliny 4,26 / 4,47 / 4,09 / 3,50 / 2,97 m na 1 m.
- **A/B cięcia wobec samego rzutu** (`swab`, wynik w rozdziale K4c wyżej) jest taki sam przed rundą 2 i po niej.
- **Złoty test:** raport `-PgoldenReport` jest identyczny jak po rundzie 1, więc lista `K4.txt` się nie zmienia.
- **Koszt** (`SampleCostTest`, stosunek do M1, mediana; na maszynie działał równolegle skan okien, więc liczby są zaszumione): po rundzie 2 dwa przebiegi dały REAL cały 1,148 / 1,176, REAL Beskidy 1,119 / 1,112, REAL masyw 1,009 / 0,997, GAMEPLAY cały 1,161 / 1,173, GAMEPLAY Beskidy 1,148 / 1,138, GAMEPLAY masyw 1,108 / 1,092. Stan sprzed rundy w tym samym czasie: 1,150 / 1,118 / 1,001 / 1,147 / 1,143 / 1,097. Wagi to kilka mnożeń na ramię lub ekstremum, które przeszły odrzucanie blokami, więc różnica mieści się w szumie. Budżet D1 jest dotrzymany.

**2. D4b w REAL (uwaga główna): prawdziwe; odstępstwo R5 i nowy gęsty test.**

Sprawdziłem wszystkie miejsca z recenzji oraz jedno nowe w `realistic_massif_1723`, które znalazł mój skan okna 16 km (2,17 bloku na blok, 940 par > 2). Wypis pól odcinka (`K3Dbg`) daje dwie przyczyny:

- **Ściany samego rzutu, których cięcie omiatające nie dotyka (D4a).** Miejsca: (125919; 1050642), (174776; 1050993), (150330; −1530776), a także (151575; 1071336) i pozostałe miejsca recenzji. Rzut ma tam jedno ramię z wyrazistością g = 0,62 / 0,51 / 0,62, a oś doliny jest mocno nachylona względem krzywej odcinka: dw/ds = 1,23 / −0,98 / 1,00. Odległość od osi |lat − wander(t)| rośnie wtedy o √(1 + (dw/ds / g)²) = 2,2 / 2,2 / 1,9 m na 1 m. Ściana zaprojektowana na 1,5 · maxSlope ≈ 1,05 m na 1 m ma więc 2,3–2,6 m na 1 m przy niemal płaskim terenie surowym (0,1–0,16 m na 1 m). W K4b miejsca te leżały na dnie, bo euklidesowe cięcie K4b poszerzało tam dno. Właśnie tę szeroką zmianę usunęła decyzja D4a.
- **Remis ramion przy (142364; 1040652)** (g = 0,37): sam rzut ma tam do 16,8 m na 1 m, a cięcie omiatające łagodzi go do 2,73.

Dlaczego nie poprawiam tego w tej rundzie:
- Poprawka musiałaby zmienić ścianę rzutu (szerokość ściany razy |∇d|, z pochodną ugięcia osi), czyli policzyć dodatkowy szum na kolumnę w ścianie. Zmieniłaby teren wszystkich dolin REAL z osią nachyloną względem krzywej, a koszt REAL i tak jest blisko budżetu D1.
- Przekroje cięcia omiatającego potrzebowałyby tej samej poprawki, bo inaczej remisy zostaną strome.
- Tę samą przyczynę (rzut mierzy odległość wzdłuż normalnej krzywej zamiast od samej osi doliny) mają remisy D4a odłożone do M5 („rzut na samą oś doliny”). Tam zniknie też |∇d| > 1.

Recenzja dopuszczała opisane odstępstwo z liczbami. Wybrałem je.

Nowy test `SurfaceContinuityTest.realisticPatchesHaveNoCliffs`:
- obejmuje okna `realistic_beskids` i `realistic_massif_1723`, za duże na pełną siatkę;
- w każdym oknie bierze 120 losowych łat 200 m (ziarno 23) oraz miejsca z recenzji i to nowe (`REAL_SPOTS`), wszystko co 1 m;
- liczy suchy ląd poza wodą stojącą i pasem brzegowym;
- trwa ok. 20 s na okno.

Stan:
- `realistic_beskids`: 2,75 bloku na blok, 5910 par > 2 w 7 z 128 łat;
- `realistic_massif_1723`: 2,17 bloku na blok, 915 par w 1 z 121 łat.

Wszystkie pary > 2 leżą w miejscach recenzji, w losowych łatach nie ma żadnej. Limity to stan zmierzony: 2,8 / 6000 i 2,2 / 950. Skan okna `realistic_beskids` po kaflach z aktywnym cięciem pokazuje te same miejsca: 27 kafli, 5757 par, najwięcej 2,75. Poprawiłem zdania „D4b spełnione wszędzie” w rozdziale rundy 1 i w opisie D4b.

**3. G3: wieloboczne płaty łęgów (uwaga główna): prawdziwe, ale ich źródło nie leży w `RiverNetwork`; odłożone do etapu poprawek klasyfikatora siedlisk (commit 3).**

Wyrenderowałem kadr `Z_besk_conf_300m` (`VizK4`, 800 px) razem z polami, które `RiverNetwork` daje siedliskom (`k4c3/viz/base/pair.png`, `pair2.png`). Pola `inFloor`, u, waga doliny i `floorChannelDist` są gładkie: zarys dna z lejkiem to łuki, bez prostych krawędzi i narożników. Proste krawędzie płatów i prostokątna dziura grądu pojawiają się dopiero w biomach. Powstają więc w klasyfikatorze (progi stref nadwodnych, wysokość nad korytem kanału doliny dominującej itd.), a nie w zarysie dna. Wygładzanie `terrainHalf` albo u w lejku, które proponowała recenzja, nie zmieni tych krawędzi.

Według decyzji wdrożenia poprawki klasyfikatora to osobny commit 3, a K7 tylko przegenerowuje plik wzorcowy. Przeniosłem więc odstępstwo R2 z „K7” do tego etapu (poprawione w R2, w „Co zostaje” i w opisie G3). Ilościowo cel G3 jest spełniony: ols 1,57% → 0,28%.

**Drobne uwagi:**
1. **Ograniczenia całego odcinka i bloku bez c/2 kary Hubera:** fałszywy alarm wobec obecnego kodu. Poprawiłem to w rundzie 1: `slack` w `sweepCut` zawiera `SWEEP_PENALTY_SOFT · k / 2` i służy obu ograniczeniom. Numery linii w recenzji wskazują wersję sprzed rundy 1.
2. **Dokumentacja K4c podawała stan za mocno:** poprawione. Dopisałem uzupełnienie w rozdziale K4c: liczby recenzji, A/B cięcia `swab`, wpływ łuku A5 w GAMEPLAY i usuwanie oczek przez G3. Tabele i wyniki sprzed rundy 1 były już tak oznaczone.
3. **A5 przy głowicy nizinnej** (prosta poziomica na ok. 70 m): zostaje odstępstwo R3. Przeniosłem je z „K7” do strojenia (§6) po obejrzeniu świata, bo K7 nie zmienia terenu. Mniejsze r albo zwężanie dna przy źródle zmienia wszystkie głowice, więc wymaga oceny na kadrach `R_head` i `R_morena_3km` oraz testu `valleyHeadsAreRounded`. Tego nie robię w rundzie poprawek.
4. **Wieloboczne płaty G3:** jak uwaga 3.
5. **Nagłówek `K4.txt`:** fałszywy alarm wobec obecnego pliku. Od rundy 1 wskazuje `build/golden_K4c.txt`.

**Pełny `./gradlew test`** (bez flag, czyli z domyślną listą `K4.txt`, plus `-PgoldenReport=build/golden_K4c_r2.txt`): BUILD SUCCESSFUL w 13 min 31 s, 142 testy, 0 porażek, 0 błędów, 0 pominiętych. Szczegóły:
- `sweepCutIsContinuousWhereItsFamilyChanges`: 0,000000 m w czterech miejscach;
- `sweepCutPruneIsExact`: 0 różnic (GAMEPLAY 1 011 373 kolumny lądu, REAL 373 444);
- `realisticPatchesHaveNoCliffs`: 2,75 / 5910 i 2,17 / 915, jak wyżej;
- raport złotego testu identyczny z raportem rundy 1;
- `SampleCostTest` w pełnym przebiegu (obciążonym innymi testami): 1,230 / 1,204 / 1,058 / 1,198 / 1,241 / 1,182 × M1, µs 6,07 / 10,79 / 8,21 / 14,43 (limity bezwzględne 6,5 / 12 / 8,5 / 16 µs dotrzymane). Zaraz potem osobny przebieg samego testu: 1,194 / 1,113 / 0,991 / 1,183 / 1,146 / 1,096 × M1, µs 4,79 / 7,25 / 6,24 / 10,87, czyli budżet D1 dotrzymany. REAL cały obszar jest nadal blisko granicy (przed rundą 1,17–1,19).

**Odstępstwa rundy 2:**

5. **R5: D4b nie jest spełnione w REAL.** Ściany samego rzutu przy osi doliny nachylonej względem krzywej mają do 2,62 bloku na blok, a remis przy (142364; 1040652) 2,75. Liczby: 5910 + 915 par > 2 bloków w 8 miejscach, w losowych łatach żadnej. Poprawka w M5 razem z rzutem na samą oś doliny. Do tego czasu limity `realisticPatchesHaveNoCliffs` są ustawione na stan zmierzony.
6. **R6: wagi członków rodziny.** Ekstrema f blisko narodzin pary (do ok. 10 m·k od miejsca narodzin) i ramiona tuż po narodzinach (g < 0,05) tną słabiej niż przed rundą. Tam, gdzie para ekstremów zakrywała strome zbocze samego rzutu przy ostrzu krzywej, to zbocze wraca bez skoku (pod jeziorem w (156980; 1059412), do 14,9 m na 1 m terenu rzecznego). Skan okien nie znalazł takiego miejsca na suchym lądzie.

**Co zostaje po rundzie 2:**
- remisy ramion D4a (krok doliny do 4,47 m na 1 m) i R5 (ściany rzutu w REAL do 2,75 bloku na blok): M5, razem z rzutem na samą oś doliny;
- wieloboczne płaty łęgów G3: etap poprawek klasyfikatora siedlisk (commit 3);
- prosta poziomica nizinnej głowicy A5: strojenie po obejrzeniu świata;
- skok samego rzutu 0,04 m w `gameplay_massif_1660` (70414,75; −31444,5) oraz izolowane skoki rzutu w tym oknie i w `gameplay_massif_1710`: M5;
- koszt REAL blisko granicy budżetu: pilnować w K5–K6;
- szew A16: K6;
- ściana brzegowa: K5b.

## K5. Wody stojące (K5.1–K5.5, A3, A3c), zalew (D2) i wybrzeże wydmowe (K5b, D5); domknięcie K4c i próba R5

Stan przed krokiem: commit `08cea01` (K4c po dwóch rundach poprawek i narzędzia pracy), drzewo robocze czyste. Rozstrzygnięcia: „Rozstrzygnięcia po K4c” w decyzjach wdrożenia (drobne poprawki, R5), K5 z projektu, D2 i D5. Narzędzia kroku (katalog roboczy sesji `k5`): `Census` (liczba kolumn każdego rodzaju wody na siatce), `CoastSurvey` (przekroje brzegu), `R5Scan` (skan okna co 4 m z doczytaniem 1 m), `RiverDump` (teren rzeczny w losowych punktach, A/B), `Png` (kadry cieniowane z wodami), `Bench` (koszt `sample` na fragmentach testu kosztu, A/B w osobnych JVM).

### Drobne z ponownej recenzji K4c

- **Fałszywy „wyjątek” dalekiego końca krótkiego odcinka źródłowego** usunięty z javadocu `sweepCut`, z komentarza `Section.fromFoot` i z opisu rundy 2 (wyżej): łuk głowicy `headArc` rośnie z odległością od osi, więc większa odległość podnosi dno łuku, a cięcie jest nierosnące w odległości bez wyjątku.
- **Javadoc `SWEEP_TWIN`:** ciągłość nie obejmuje izolowanych punktów ostrza fałdy (f''' = 0), które leżą poza zasięgiem dolin.
- **`denseGridHasNoCliffs` mierzy kroki na 1 m.** Para sąsiadów siatki 2 m, której średni krok przekracza 0,4 limitu (bloków albo kroku doliny), dostaje próbkę w środku i liczy się jako dwa kroki po 1 m (krok 1 m powyżej limitu przy średniej poniżej 0,4 limitu wymagałby grzbietu albo wcięcia węższego niż 2 m). Teren bez zmian, liczby rosną: krok doliny `gameplay_beskids` 4,26 → 5,78 m na 1 m, `gameplay_massif_spawn` 4,47 → 5,51, `gameplay_massif_1660` 4,09 → 4,57, `gameplay_massif_1718` 3,50 → 3,53, `gameplay_massif_1710` 2,97 → 2,99 (limity `DENSE_VALLEY` 5,8 / 5,55 / 4,6 / 3,55); najwięcej 1,71 bloku na blok (`gameplay_massif_spawn`), żadnego kroku > 2. (Recenzja podawała do 1,81 bloku na blok z siatki 0,5 m kafli z aktywnym cięciem.) Czas testu bez zmian (ok. 100 s na pięć okien, doczytanych 0,1–0,4 mln par na okno).
- **`sweepCutIsContinuousAtTheReviewedScarps` z bisekcją** zamiast progu 3 m na 0,5 m: każda para suchych sąsiadów różniąca się o > 0,02 m, której punkt środkowy nie dzieli różnicy, idzie do bisekcji (wspólny pomocnik `bisectedStep` z `sweepCutIsContinuousWhereItsFamilyChanges`), limit 0,01 m. Bisekcja znalazła skok 0,015 m terenu rzecznego w (28464; 887,52) GAMEPLAY: to szew samego `landElevation` (0,022 m; komórka [19, 1] BESKIDS z wagą 1,2·10⁻⁴ wchodzi do okna 3 × 3, A16, K6). Pomocnik odejmuje więc krok `landElevation` na końcowym przedziale. Wynik: 0,000000 m we wszystkich 13 miejscach.
- **Do „Co zostaje” (M5):** szczelina samego rzutu ok. 40 m × 0,1 m, do 2,5 m głębokości w (74489,5; 89655,02) REAL (prawie-ostrze odcinka rzędu 1). Nie dopisałem jej do próbnika regresji.

### R5: szersza ściana rzutu w REAL — prototyp nie przeszedł, R5 zostaje wyjątkiem do M5

Prototyp na kopii (`k5/r5`, łatka `r5_patch.py`): w `projectChannel` każde ramię dostaje czynnik ściany k = √(1 + (w'/(|P'| · g))²), gdzie w' = d wander/dt z tabeli liczonej raz na odcinek (różnice centralne 129 próbek gęstych ugięcia, interpolacja liniowa), g to wyrazistość ramienia. Czynnik jest średnią miękką po ramionach z ich wagami rzutu, na końcu odcinka 1, wygaszony dla g od 0,7 do 1 (`1 − smoothstep`), najwyżej 3. W `query` ściana `wall = min(maxWall, wall · k)`, więc ramka odrzucania odcinków się nie zmienia.

Kryteria decyzji i wynik (`R5Scan`: siatka 4 m, pary ze średnim krokiem > 0,5 bloku na blok doczytane co 1 m):

| Wariant | `realistic_massif_1723` (16 km) | Kroki > 2 bl./bl. |
|---|---|---|
| stan K4c | 2,17 bloku na blok w (150330,5; −1530776) | 231 |
| R5: czynnik dokładny, bramka g 0,7–1, najwyżej 3 | **101,8** w (144008,5; −1521924) | 2893 |
| R5: bramka rosnąca g 0,3–0,45 i malejąca 0,75–1, najwyżej 2,5 | **13,1** w (144002,5; −1521944), miejsce wyjściowe 4,2 | 1783 |

Przyczyna (wypis ramion, `R5Dbg`): czynnik zmienia się z t ramienia, a t przesuwa się o 1/(|P'| · g) na metr. Przy małym g jedyne ramię odcinka (144045; −1522099) → (143441; −1521856) leży w t = 0,037 (g ≈ 0,02, k = 3), a 0,5 m dalej już na końcu odcinka (k = 1). Ściana zmienia szerokość o setki metrów na metrze, a cięcie o 100 m. Każda wersja, która poszerza ścianę tam, gdzie g jest małe, ma ten sam problem: szerokość ściany musiałaby zmieniać się wolniej niż ok. 0,5 m na metr, czyli na dystansie rzędu kilometra, a wtedy przestaje być lokalna. Kryteria (1) i (4) padają, więc (2) koszt i (3) złoty test nie były mierzone.

**Decyzja (zgodnie z rozstrzygnięciem): R5 zostaje świadomym wyjątkiem od D4a/D4b do M5** (rzut na samą oś doliny). Pełny skan okna `realistic_beskids` (60 km, co 4 m z doczytaniem 1 m, 775 s) potwierdza obraz testu: 2,67 bloku na blok, 1508 kroków 1 m > 2 w sześciu miejscach, wszystkie z `REAL_SPOTS`; żadnego innego. GAMEPLAY spełnia D4b. Limity `realisticPatchesHaveNoCliffs` (2,8 / 6000 i 2,2 / 950) bez zmian.

### K5.1: starorzecza jako półksiężyce

- **Weszło (`RiverNetwork.oxbow`, `MeanderField.arcDistance`, `arcBounds`):** starorzecze leży na łuku dawnej, bardziej rozwiniętej pętli Kinoshity (θ0 + 0,5…1,0, od przegięcia do przegięcia, przycięty do części 2/3–całość łuku, niesymetrycznie), po zewnętrznej stronie łuku obecnego koryta, odsunięte o pas brzegu 12 m i półszerokość. Półszerokość rośnie od 0 na rogach (√sin(π a)) do 0,5 W + 3 m·k w środku. Szerokość maleje płynnie do zera przy korycie (zatkane końce), przy skraju dna, przy granicy nizin i na niskim terenie przy wybrzeżu. Wszystkie parametry zależą tylko od numeru łuku (lustro, kształt, id); sprawdzane są trzy łuki. Szansa łuku 0,22, brak starorzecza przy lustrze < 1 m.
- **Poza projektem (po pomiarze testem nowym):** dwa twarde progi zostawiały proste ucięcia. (1) Starorzecze należy do meandrów doliny dominującej (F1), więc tam, gdzie dno przejmuje inna dolina, znikało wzdłuż linii przełączenia. Teraz szerokość wygasza się z marginesem klucza F1 doliny dominującej nad najbliższym rywalem, który nie jest jej przedłużeniem przez węzeł (`continues`); w (7100, −5600) GAMEPLAY woda kończyła się 2 m w głębi brzegu. (2) Próg spadku `best.gradient < 1,5 ‰` skakał na węzłach; teraz liczy się spadek miękkiego maksimum (ciągły przez węzły, F1) z wygaszeniem od 1,2 do 1,5 ‰ (`OXBOW_MAX_SLOPE`).
- **Bezpieczeństwo zapytań:** `oxbow` liczy się po pętli odcinków `query` (bez zagnieżdżonego zapytania); granice łuku `ArcBounds` są rekordem (pola final), więc publikacja między wątkami jest bezpieczna.
- **Liczby:** starorzecza w teście (siatka REAL ±60 km co 60 m, GAMEPLAY ±8 km co 10 m): 2017 → 2589 i 518 → 644. Woda OXBOW (`Census`): GAMEPLAY ±8 km 648 → 1028 kolumn, REAL wysoczyzna ±40 km 2992 → 4444 (całe pętle mają większą powierzchnię niż dawne wycinki pierścienia).
- **Nowy test `StandingWaterTest.oxbowLakesAreCrescents`** (osobna klasa, żeby nie wydłużać `RiverNetworkTest`): 25 pierwszych starorzeczy (według id) na skalę, każde wypełnione na siatce 1/5 półszerokości; jedno lustro; woda kończy się tylko na linii brzegu (każda komórka wody przy komórce bez tej wody najwyżej 2 komórki w głąb brzegu); mediana udziału szerokości w rogach (10% długości geodezyjnej od każdego końca) ≤ 0,6 i ≤ 0,7 w ≥ 75% starorzeczy; mediana długości ≥ 6 półszerokości. Wynik: REAL mediana 0,53, 22 z 25 ≤ 0,7; GAMEPLAY 0,46, 20 z 25; ucięć 0. Na stanie K4c (starorzecze M1) test pada: wszystkie 25 starorzeczy REAL kończą się 4,4–5,0 komórki w głębi brzegu (proste cięcia), choć udziały rogów same ich nie odróżniają (mediana 0,60).

### K5.2: jeziora rynnowe kończą się przed doliną, poziom niezależny od kolejności

- **`RiverHit.floorGap`** = min po odcinkach (floorDist − terrainHalf − 0,5 · wall), liczone z półszerokością dna terenu z lejkiem G3 (rozstrzygnięcie po K4) i nieregularnym skrajem G4. **`RiverHit.lakeGap`**: odległość od brzegu najbliższego jeziora bezodpływowego, obcięta do 150 m·k (`LAKE_GAP_MAX`). Do tej odległości jest dokładna, bo lista kafla zawiera każde jezioro w pierścieniu 150 m·k. Projekt brał `lakeShore`, który przeskakuje na granicach kafli (filtr M1 `nearTile`).
- **`tunnelLakeAt`:** obecność rynny nie zależy od wagi doliny; soczewka = `ellipticEnd` przy granicy jezior × `ellipticEnd` przy dolinie (30 m·k, długość 1,5 półszerokości, 150…570 m·k, limit K4.9) × `ellipticEnd` przy jeziorze bezodpływowym (długość najwyżej 120 m·k) × bramka orientacji N–S × `smoothstep(25k, 60k, half)`; niecka gaśnie z jeziorem. Wczesny powrót, gdy któraś odległość < 30 m·k.
- **Determinizm:** poziom lustra liczony z punktu kanonicznego, czyli z iteracji osi rynny startującej od kotwicy jeziora (`tunnelAxis(anchor · 200 m·k, k)`), więc zależy tylko od klucza. `TerrainDeterminismTest` jest teraz ścisły (stała `TUNNEL_LAKE_LEVEL_ORDER_DEPENDENT` usunięta): 0 różnych kolumn we wszystkich 8 kadrach, także w kadrach pasów A1 (w K0: 2531 i 12143 kolumny).
- **Liczby:** woda jezior rynnowych GAMEPLAY ±8 km 15799 → 10629 kolumn (−33%; projekt: −37%), REAL wysoczyzna ±40 km 15946 → 12005 (−25%).
- **Nowy test `LandscapeModelTest.tunnelLakesEndBeforeValleys`:** kolumny wody jezior rynnowych (REAL ±40 km co 40 m, GAMEPLAY ±8 km co 10 m) mają `floorGap` i `lakeGap` ≥ 30 m·k, jedno id = jedno lustro. Wynik: REAL 15 jezior, najmniejszy odstęp od doliny 36,4 m; GAMEPLAY 8 jezior, 17,4 m (limit 15 m).

### K5.3 i K5.5: brzegi oczek i jezior bezodpływowych bez prostych odcinków

- **Oczka (`kettleAt`):** brzeg r · (1 + 0,25 · płaty), płaty z szumu próbkowanego po okręgu w przestrzeni szumu (1,1 i 2,3, `clamp` do [−1, 1]), amplituda 0 przy środku; szybkie odrzucenie, gdy d − 1,25 r > `KETTLE_BANK`.
- **Jeziora bezodpływowe (`RiverNetwork.sinkLakeShore`):** ten sam wzór, amplituda ±0,2 R, jedna funkcja dla terenu i pierścienia siedlisk; brzeg mieści się w 0,8–1,2 R, więc filtr kafla dalej obowiązuje.
- **Złoty test:** K5.5 nie zmienił żadnej łaty „=” (lista K5 niżej). `noSpringsOnMassifCore`: kolumn dna lub wody na rdzeniach masywów REAL 1620 → 1621 (jedyna woda rdzeni to jeziora bezodpływowe), limit 1625.

### K5.4: niecki bez ściany (A3) i poziom oczek z terenu po dolinach (A3c)

- **`applyLake` (A3):** stok niecki przechodzi w teren w paśmie 0,4–1,0 zasięgu niecki (`lerp`), bez ściany 5000 m · smoothstep; torfowisko bez wału i bez stopnia 1,5 m (stok zaczyna się na poziomie torfu, lustro − 0,5 m). Wał lustro + 1 m w pasie 15–40 m przy wodzie zostaje (szczelność).
- **A3c (`kettleLevel`, `surfaceBeforeKettle`):** lustro oczka z najniższego punktu powierzchni sprzed oczek (teren po dolinach sieci rzecznej, nieckach jezior bezodpływowych i rynnowych) na okręgu r · 1,3 + 25 (12 punktów) i w środku, liczone raz na oczko (`get`/`put`). Zagnieżdżone zapytania sieci rzecznej działają w `sample` dopiero po jego własnym zapytaniu (wynik jest rekordem), tak jak w `kettleState`. `surfaceBeforeKettle` pomija koryta, wały i starorzecza den dolin: oczko istnieje tylko z dala od dolin (waga doliny w środku < 0,3), więc jego okrąg sięga najwyżej zboczy.
- **Poza projektem:** torf leży na poziomie lustro − 0,5 m, ale nigdy wyżej niż grunt sprzed niecki (`min`). Bez tego oczko, którego brzeg przecinał zagłębienie pominięte przez 12 punktów poziomu, leżało 1,07 m nad nim ((−2941, −938,5) GAMEPLAY): mały stopień na brzegu.
- **Liczby:** woda oczek GAMEPLAY ±8 km co 10 m 6202 → 6064 (−2,2%), torf 3896 → 3840 (−1,4%); REAL wysoczyzna ±40 km: 16398 → 16478, torf 11019 → 11012. Miejsca ścian jezior bezodpływowych przy masywach: `realistic_sink_lake_1723` 230 par > 25 m / 46,0 m → 0 / 13,3 m; `realistic_sink_lake_1659` 148 / 97,4 m → 50 / 30,2 m. Lustro tego jeziora leży ok. 100 m pod stromym zboczem masywu, a stok niecki (27 m) wspina się na nie z nachyleniem do 6 m na 1 m; zasięg niecki jeziora bezodpływowego jest związany z filtrem kafla sieci rzecznej (M5).
- **Nowy test `LandscapeModelTest.kettleBogsNeverAboveSurroundings`:** 72 promienie od każdego z 104 oczek torfowych GAMEPLAY ±8 km. Stopień od ostatniej kolumny torfu do pierwszej za brzegiem: 0,008 m (limit 0,3). Spadek do gruntu 2–2,5 m za brzegiem: 0,85 m (limit 1,1; torf może leżeć w zagłębieniu stoku). Liczba kolumn oczek ±10% wobec stanu sprzed K5.

### D2: zalew bez rowu i K5b (D5): wybrzeże wydmowe

**Prototyp na kopii (`k5/d5`, łatka `d5_patch.py` z parametrami jako właściwościami JVM), potem projekt bez zmian parametrów.**

**Co weszło (`LandscapeModel.shapeCoast`, `cliffShore`, stałe `COAST_*`):**

- **Brzeg wysoki (klif)** tylko na wysoczyźnie morenowej (waga 0,3–0,7) i tylko tam, gdzie szum wybrzeża o fali 30 km·meso leży w górnych kwantylach (0,62–0,78, `noiseQuantile`). Teren to dotychczasowy ściśnięty relief ze ścianą 2,5 : 1 od strony morza. Młodoglacjalna wysoczyzna dochodzi do morza na ok. 3/4 brzegu (73% punktów brzegu ma wagę moreny > 0,3), więc klif wychodzi na ok. 1/5 brzegu.
- **Brzeg niski** (reszta, płynne przejście z tym samym udziałem):
  - plaża 60 m·k;
  - wydma przednia (biała), garb sin² na 130 m·k, wysokość 6–15 m wzdłuż brzegu;
  - wydmy szare: pagórki 2–8 m na zapleczu do 420 m·k za plażą;
  - zaplecze 1,5 m nad morzem, które dochodzi do ściśniętego reliefu dopiero 6 km·meso od brzegu (`COAST_LOW_END`; GAMEPLAY 900 m).
- **Zalew (D2)** tylko za brzegiem niskim. Głębokość gaśnie, gdy zaplecze rośnie od 3 do 6 m. Szerokość nie zależy od typu brzegu. Ku końcom zalewu wzdłuż brzegu głębokość spada do zera, a misa zachowuje połowę szerokości. Profil misy sin(π v). Początek 300 m·k za plażą, czyli mierzeja ma plażę, wydmę przednią i część wydm szarych. (Recenzja K5: woda nie kończyła się „zaokrąglonym czubkiem”, tylko igłą lub klinem, a sama misa nie gasła z głębokością i zostawiała uskoki do 6 m; poprawione w rundzie 1, niżej.)
- **Pole `lowShore`** = 1 − udział klifu (wcześniej 1 − smoothstep(6, 20, hl)); `Calibration.LOW_SHORE` 0,25 → 0,5. Podłoże: glina (till) tylko na brzegu wysokim; wydmy brzegu niskiego to piasek także powyżej 8 m.
- Sam kształt brzegu zmienia się tylko w pasie 25 km·meso, a teren poza dolinami tylko do 6 km·meso od brzegu (`TerrainLocalityTest` dostał wyjątek pasa `COAST_LOW_END`; siatki zalewów nadal porównują 2920 i 2453 kolumn, 0 zmian poza dolinami i wodą). Łaty kontrolne wnętrz bez zmian. **Ale** kształt brzegu jest częścią `landElevation`, z którego sieć rzeczna liczy trasy i poziomy węzłów, więc doliny zmieniają się daleko od brzegu (niżej, „Wpływ na rzeki”).

**Pomiar `CoastSurvey`** (przekroje wzdłuż normalnej brzegu, siatka jak w `CoastTest`, `landElevation`):

| | REAL przed | REAL po | GAMEPLAY przed | GAMEPLAY po |
|---|---|---|---|---|
| przekroje | 279 | 279 | 2658 | 2658 |
| klif (ściana > 1 m na 1 m i > 8 m) | 100% | 22,2% | 99,7% | 21,0% |
| brzeg niski | 0% | 77,8% | 0,3% | 79,0% |
| `lowShore` ≥ 0,25 (dawny próg wydm) | 12,9% | 81,7% | 16,5% | 82,0% |
| wydma przednia (p10 / p50 / p90) | — | 10,7 / 12,5 / 13,9 m | — | 10,6 / 12,5 / 14,4 m |
| zaplecze B + 600…1000 m·k (p50) | — (pas 15–22 m) | 2,0 m | — | 5,8 m |

`CoastTest` (próg `LOW_SHORE` 0,5): REAL 40 przekrojów brzegu wydmowego i 40 wysokiego (33 z klifem), w pasie wydm 100% wydm i boru bażynowego (biała 58,6%, szara 41,4%), 0 klifów; GAMEPLAY 40 i 40 (24 z klifem), 100% (61,8 / 38,2%). Kadry kontrolne (`Png`): zalew REAL (−223761, −151100) 12 km: przed rów szerokości ok. 130 m wzdłuż brzegu, po zalew 1,5–2 km za mierzeją z wydmami, koniec południowy zwęża się na ok. 3 km; zalew GAMEPLAY (345, 10394) 3 km: jezioro przybrzeżne ok. 300 m szerokie z zaokrąglonym końcem; przegląd brzegu 40 km w obu skalach. Pas brzegowy testu ciągłości: `gameplay_moraine` 2,08 → 0,51 bloku na blok (brzeg niski), `gameplay_center` 2,40 (ściana klifu).

**Udziały wybrzeża w podglądzie siedlisk** (`./gradlew landscapePreview -PhabitatsOnly`, `m2_biome_shares.csv` i `m2_zone_shares.csv`, procent wszystkich kolumn kadrów REAL / GAMEPLAY; przed: plik z 2026-10-03, stan K4): biała wydma 0,000 / 0,087 → 0,016 / 0,328, szara wydma 0,000 / 0,072 → 0,014 / 0,348, zalew 0,004 / 0,023 → 0,047 / 0,187, bór bażynowy 0,054 / 1,725 → 0,043 / 1,432, plaża bez zmian (0,010 / 0,277); strefy: ściana klifu 0,003 / 0,071 → 0,000 / 0,024, wierzchowina klifu 0,003 / 0,053 → 0,000 / 0,020, wydma inicjalna 0,000 / 0,024 → 0,002 / 0,049. Kadr `coast_lagoon_3km`: zalew za mierzeją z plażą, wydmami białymi i szarymi, pasy szuwaru i torfowiska niskiego na brzegu zalewu.

**Wpływ na rzeki (D2: zalew i brzeg są w `landElevation`).** Sieć rzeczna liczy z `landElevation` trasę węzłów i poziom (poziom węzła ograniczony najniższym terenem na drodze do celu). Niższy brzeg obniża poziomy rzek przy ujściach. Obniżenie idzie w górę rzeki wszędzie tam, gdzie poziom ogranicza spadek, a nie teren, czyli na nizinach. Pomiar A/B terenu rzecznego (`RiverDump`, 20 000 losowych punktów):

| Obszar | punkty lądu dalej niż 6 km·meso od brzegu | zmienione o > 0,05 m | o > 1 m | najwięcej |
|---|---|---|---|---|
| GAMEPLAY ±40 km | 15938 | 3252 (20%) | 2525 (16%) | 174,7 m |
| REAL ±150 km wokół zalewu (−223761, −151100) | 9447 | 186 (2,0%) | 128 | 36,1 m |
| REAL ±150 km wokół (0, 0) | 20000 | 0 | 0 | — |

W GAMEPLAY morza są małe i rozsiane po świecie, a rzeki nizinne mają poziom ograniczony spadkiem, więc zmiana sięga dużej części dolin. Wariant z trasą węzłów liczoną z dawnego kształtu brzegu (poziom nadal z nowego terenu) zmienia nadal 2833 punkty (2210 o > 1 m, najwięcej 34,9 m), więc trasa nie jest główną przyczyną. Poziom z dawnego terenu postawiłby rzeki nad nowym, niskim zapleczem (koryta na wałach). Zostawiam to tak; to świadoma zmiana do oceny w recenzji (otwarte, niżej). Testy rzek i szczelności wody przechodzą (`waterIsContained*`, `WaterContainmentTest`, `networkIsAcyclicAndLevelsNeverRiseDownstream`).

**`WatersideZonesTest.floorZonesFollowDominantRiver`.** D5 przesunął dwa zbiegi GAMEPLAY na niski brzeg (20 i 237 m od morza). Ich płaskie zaplecze leży na poziomie den, więc dna rzeki i dopływów zlewają się w jedną równinę przybrzeżną, a całe dno z rzeką spadło z 93,0% (K4c) do 90,6% (próg 92%). Pomiar pomija teraz równinę przybrzeżną (`COAST_FLAT` = 1,5 km·meso od brzegu): REAL 99,1%, GAMEPLAY 93,1% (4146 kolumn, 7 zbiegów z kolumnami liczonymi). Cięciwy olsu (test bazowy K0): REAL 160 z 600 przekrojów na klasę (78,1% ≥ 10 bloków; K0: 157, 80,3%), GAMEPLAY 204 (57,8%; K0: 196, 61,2%), czyli bez spadku o połowę.

### Złoty test: lista `K5.txt`

`src/test/golden-allow/K5.txt` = lista K4 + łaty zmierzone po K5 (`-PgoldenReport=build/golden_K5.txt`). Nowe w K5:

- wody stojące:
  - `tunnel_valley_lake`, `outwash_plain_lake`, `kettle_pond`, `peatland` w REAL A;
  - `oxbow_lake`, `kettle_pond` w REAL B;
  - `oxbow_lake target`, `outwash_plain_lake target`, `kettle_pond` w GAMEPLAY A;
  - `oxbow_lake`, `tunnel_valley_lake`, `outwash_plain_lake`, `peatland` w GAMEPLAY B;
  - `outwash_plain_lake`, `kettle_pond`, `peatland` w REAL A 0,5;
- `river_mouth` w REAL A, GAMEPLAY B i REAL A 0,5 (kolumny starorzeczy);
- `coverage` w GAMEPLAY A (brak wody OXBOW, jak w projekcie) i w REAL A 0,5 (jedyne starorzecze zestawu było w łacie `oxbow_lake`, która traci cel; obie łaty są szukane od nowa w K7);
- wybrzeże (D2, D5):
  - `lagoon` we wszystkich zestawach (z utratą celu poza REAL A);
  - `cliff` we wszystkich zestawach (z utratą celu poza REAL B: dawny klif jest teraz brzegiem niskim);
  - `coast` w GAMEPLAY A (w REAL B i GAMEPLAY B `coast` i `beach` były już dozwolone; tam zmienia się tylko podłoże wydm, piasek zamiast gliny);
- `GAMEPLAY A / summit target`: D5 obniża poziomy rzek zlewni niskiego brzegu, dolina obok jedynej kolumny SUMMIT łaty jest 8 cm głębsza, więc kolumna nie jest już wyższa od swojego okręgu.

Wszystkie łaty z tabeli końcowej §4 oznaczone „=” (poza wybrzeżem, którego tabela nie obejmowała, bo D2 i D5 nie miały prototypu) pozostają bez zmian. Trzy łaty „numeric” z K4 pozostają tylko numeryczne. Łaty kontrolne wnętrz bez zmian. `terrainMatchesGolden` z domyślną listą (`K5.txt`) przechodzi.

### Koszt

`SampleCostTest` (osobno, `costTest`, dwa uruchomienia), stosunek do kopii M1 (mediana):

| Obszar | po rundzie 2 K4c (osobno) | K5 przebieg 1 | K5 przebieg 2 |
|---|---|---|---|
| REAL cały obszar | 1,194 | 1,215 | 1,217 |
| REAL Beskidy | 1,113 | 1,108 | 1,133 |
| REAL wielki masyw | 0,991 | 1,014 | 0,948 |
| GAMEPLAY cały obszar | 1,183 | 1,169 | 1,284 (z minimów 1,185) |
| GAMEPLAY Beskidy | 1,146 | 1,125 | 1,183 |
| GAMEPLAY wielki masyw | 1,096 | 1,108 | 1,172 |

µs na kolumnę (przebieg 1): 5,07 / 7,40 / 6,46 / 11,22 (limity bezwzględne D1 6,5 / 12 / 8,5 / 16 µs dotrzymane). Pomiar A/B tego samego zestawu fragmentów w osobnych JVM na zmianę (`Bench`, minima 9 rund, trzy pary): REAL cały obszar 4,40–4,51 µs przed K5 i 4,47–4,52 po; GAMEPLAY 6,22–6,33 i 6,29–6,49. K5 dokłada więc ok. 1% w REAL i 1–2% w GAMEPLAY (profil JFR: `tunnelLakeAt` 0,5% próbek, starorzecza poniżej progu wypisu). **REAL cały obszar jest teraz o 1–2% ponad budżetem D1 (1,20).** Nie znalazłem w K5 oszczędności tej wielkości (koszt to głównie budowa sieci rzecznej przy pierwszym przejściu i rzut). K6 (A16) doda koszt `blend`, więc szukanie oszczędności zostaje na K6 (otwarte).

### Testy

**Nowe:**
- `StandingWaterTest.oxbowLakesAreCrescents`;
- `LandscapeModelTest.tunnelLakesEndBeforeValleys`;
- `LandscapeModelTest.kettleBogsNeverAboveSurroundings`.

**Zmienione:**
- `TerrainDeterminismTest`: ścisły, bez wyjątku jezior rynnowych;
- `SurfaceContinuityTest`:
  - limity okien z wodą stojącą 0 (cele §3.3 spełnione: `gameplay_center` 14 → 0, także bez oczka (5761, −4758); `gameplay_moraine` 13 → 0; `realistic_lowland` 2 → 0; `realistic_moraine` 15–16 → 0; `gameplay_tunnel_lake_3519` 25 → 0);
  - gęsta siatka na 1 m;
  - limity ścian jezior bezodpływowych przy masywach (0 / 13,4 m i 50 / 30,3 m) i ostrzy (12,0 m);
- `RiverNetworkTest`: bisekcja w `sweepCutIsContinuousAtTheReviewedScarps` i `noSpringsOnMassifCore` (1625);
- `TerrainLocalityTest`: wyjątek pasa niskiego brzegu;
- `WatersideZonesTest`: pomiar F2 bez równiny przybrzeżnej.

**Pełny `test`** (`tools/dev/run-tests test`, bez flag, czyli z domyślną listą `K5.txt`): BUILD SUCCESSFUL w 8 min 47 s, 144 testy, 0 porażek, 0 błędów, 0 pominiętych (wpis PASS w `build/test-logs/index` dla drzewa z tą dokumentacją). Szczegóły: gęsta siatka 1,68 / 1,71 / 1,48 / 1,57 / 1,28 bloku na blok i krok doliny 5,78 / 5,50 / 4,57 / 3,53 / 2,99 m na 1 m; starorzecza jak wyżej; jeziora rynnowe REAL 15 jezior (36,4 m), GAMEPLAY 8 (17,4 m); oczka torfowe 0,008 / 0,846 m; F2 99,1% / 93,1%; determinizm 0 różnic.

### Odstępstwa od projektu w K5

1. **R5 nie wszedł** (prototyp zrobił skoki do 101,8 bloku na blok); wyjątek do M5, limity bez zmian.
2. **Starorzecza:** dodatkowe wygaszanie przy zmianie doliny dominującej (margines klucza F1) i spadek miękkiego maksimum zamiast spadku odcinka. Bez nich zostawały proste ucięcia, które znalazł nowy test.
3. **`lakeGap` zamiast `lakeShore`** jako odległość od jeziora bezodpływowego dla końca jeziora rynnowego (ciągła na granicach kafli), z końcem najwyżej 120 m·k.
4. **Torf nigdy wyżej niż grunt sprzed niecki** (poza A3/A3c).
5. **Test starorzeczy** z kryteriami rozkładu (mediana i udział) oraz kryterium „woda kończy się na brzegu”, bo próg projektu (0,3 szerokości w 10% łuku dla każdego starorzecza) jest sprzeczny z profilem √sin(π a) samego K5.1 (0,56). Test jest w osobnej klasie.
6. **Test oczek torfowych:** kryterium stopnia na brzegu (0,3 m) i spadku na 2 m (1,1 m) zamiast „nigdy wyżej niż teren 2 m za brzegiem”, bo zbocze zagłębienia samo opada o 0,85 m na 2 m.
7. **D5 (nie było w projekcie):** wybór klifu z wysoczyzny morenowej i kwantyli szumu, zaplecze 1,5 m dochodzące do reliefu 6 km·meso od brzegu, `LOW_SHORE` 0,5, piasek na wydmach powyżej 8 m. **D2:** profil misy sin(π v), połowa szerokości na końcach zalewu, początek 300 m·k za plażą.
8. **`WatersideZonesTest`** pomija równinę przybrzeżną 1,5 km·meso (wyżej).
9. **Wpływ D5 na rzeki** (20% punktów lądu GAMEPLAY, 2% przy brzegu REAL) większy niż zakładała recenzja projektu dla D2 („łaty spoza pasa wybrzeża zmienić się nie mogą”). Łaty spoza pasa wybrzeża, które i tak nie są na liście, się nie zmieniły. Zmieniła się `GAMEPLAY A / summit`, która już była na liście K4, i straciła cel.

### Runda 1 poprawek po recenzji K5

Recenzja znalazła 4 problemy blokujące i 4 poważne. Zanim cokolwiek zmieniłem, sprawdziłem wszystkie na stanie K5 narzędziami recenzentów: `K5RevProbe frame` i `VizK5Scan` (skan 1 m: przecieki, szwy lustra, pary suchych sąsiadów > 2 bloków na blok). Wszystkie się potwierdziły.

**1. Oczka przecinały wały jezior rynnowych i zalewu (A3c, blokujące, dwa zgłoszenia).** Okrąg poziomu oczka trafiał w dno niecki sąsiedniego jeziora rynnowego albo w dno zalewu. Lustro oczka spadało wtedy do 57 m pod lustro jeziora, a stok oczka ścinał wał jeziora. Stan K5 (skan 1 m, pary przecieku):
- GAMEPLAY: (5761, −4758) 213, (−1274, −6223) 291, (6130, −6600) 423, (3032, −7216) 147, (10730, 12420) 285, (17600, −1290) 260;
- REAL: (−77955, 8162) 690;
- torf poniżej morza przy zalewie: REAL (−221658, −151118) 186, GAMEPLAY (−14940, −1537) 64.

Poprawka (wariant 1 recenzji, `kettleState`). Oczko nie istnieje, gdy:
- jego zasięg (brzeg + `KETTLE_BANK`) spotyka wał jeziora rynnowego, czyli pas do 40 m od brzegu, w którym `applyLake` podnosi grunt do lustra + 1 m. Sprawdza to `kettleTouchesTunnelLake`: siatka co 10 m·k na tarczy oczka, dolne ograniczenie odległości od brzegu (obecność 1, bez dolin i jezior bezodpływowych), z szybkim odrzuceniem w środku. Liczone raz na oczko, więc oczko jest całe albo go nie ma;
- jego zasięg spotyka zasięg jeziora bezodpływowego (`RiverNetwork.sinkLakeClearance`: |p − środek| − 1,2 R po węzłach kafla);
- jego lustro jest niższe niż 1 m (niskie zaplecze D5, brzeg zalewu).

`surfaceBeforeKettle` liczy teren po dolinach i po niecce jeziora rynnowego, bez jezior bezodpływowych: oczko może leżeć na zewnętrznym stoku niecki, ale nie na wale.

Oczek ubywa:
- GAMEPLAY ±8 km: 17 z 223 (13 przy jeziorach rynnowych, 4 na poziomie morza). Woda oczek 6064 → 5607 kolumn, torf 3840 → 3490. Granica testu `kettleBogsNeverAboveSurroundings` zmieniona z ±10% na ±15% wobec stanu sprzed K5;
- REAL wysoczyzna ±40 km: −0,5%.

Wynik: wszystkie powyższe kadry mają 0 przecieków (`StandingWaterContainmentTest`).

**2. Zalew (D2, blokujące).** Przy głębokości → 0 `lerp(bowl, result, −depth)` dawało (1 − bowl) · teren, a bramki `depth > 0` i `lagoon > 0` wracały skokiem do terenu. Zostawały uskoki do 6 m na 1 m, a na końcach zalewu proste uskoki długości ok. 1,3 km. Teraz cała misa gaśnie z siłą zalewu f = lagoon · low · (1 − smoothstep(3, 6, base)): teren = min(t, t − bowl · f · (t + 6)), bez twardych bramek. Przy f = 1 wynik jest ten sam co w K5, a przy f → 0 teren wraca płynnie.

Skan 1 m, pary > 2 bloki na blok (K5 → runda 1):
- G_zalew_3km: 671 → 0;
- R_zalew_3km: 1929 → 0;
- R_zalew_6km: 6589 → 0 (do tego 186 przecieków → 0);
- G (620, 11600): 531 → 0;
- R (−218700, −156510): 715 → 0 (także 2 pary „koryta na wale” → 0).

Wody zalewu ubyło (GAMEPLAY ±20 km co 25 m: 10146 → 7348 kolumn), bo przy f < 1 misa jest płytsza na całej szerokości. Koniec wody zwęża się jak √(odległość wzdłuż brzegu). Szum zalewu ma falę 60 km·meso i wygasza zalew na długim odcinku, więc koniec nadal jest długim, wąskim klinem.

Nowe okna `SurfaceContinuityTest`: `realistic_lagoon` i `gameplay_lagoon`. Przy samym zalewie nie ma skoków. Zostają 2 i 7 skoków do 0,99 m: wały ujść rzek podniesione do 1 m obok plaży na poziomie morza (M1).

**3. Pasy jezior rynnowych w GAMEPLAY (A1, blokujące).** Kotwica jeziora zależała od punktu startu iteracji Newtona, czyli od x kolumny. W GAMEPLAY sekcja jest długa względem skali pola (2,25 km wobec 9 km; w REAL 4,5 km wobec 60 km), więc kolumny jednego jeziora zbiegały do różnych pierwiastków. Powstawały pasy N–S z lustrami różnymi do 68 m. Skan 1 m w K5:
- G (−16500, −8820) 1 km: 14649 par przecieku i 5726 par szwów (do 44 m);
- G (2180, 17650) 1 km: 54509 par szwów (do 68 m).

Poprawka (`tunnelShape`, `buildTunnelBlock`, `TunnelContour`, bloki w `DirectCache`):
- **Śledzenie konturów.** Kontury pola rynny są śledzone raz na sekcję i blok x (blok = 2 sekcje). Pierwiastki na linii środka sekcji pochodzą ze stałej globalnej siatki x (bisekcja). Od nich idą kroki Newtona w x co 20 m·k w z, do obu końców sekcji. Ślad kończy się, gdy kontur skręca bardziej niż dx/dz = 2,5, gdy Newton nie zbiega albo skacze, albo gdy kontur odpływa o więcej niż długość sekcji. Blok jest czystą funkcją (k, bx) z marginesem na najdalszy istotny kontur i największy dryf, więc sąsiednie bloki się zgadzają.
- **Wybór konturu.** Kolumna bierze najbliższy (wzdłuż x) śledzony kontur. Ślad poza swoim końcem liczy się jak punkt końca, więc wybór zmienia się w sposób ciągły. Jezioro gaśnie, gdy drugi kontur jest prawie tak samo blisko (`TUNNEL_SPLIT` 40 m + 3 · zasięg niecki), i przy końcu śladu. Dzięki temu wody o różnych kluczach zawsze dzieli ląd.
- **Kotwica i lustro jak w K5.** `tunnelAxis` liczy kotwicę od pierwiastka i punkt kanoniczny lustra od kotwicy. Jeziora REAL, które K5 liczył spójnie, mają ten sam klucz i lustro.
- **Eliptyczna odległość od brzegu.** Woda jest tam, gdzie dist < h · lensEnd, czyli koniec jest tak samo zaokrąglony. Poza wodą odległość rośnie z nachyleniem ok. 1, także wzdłuż osi. Za końcem soczewki (granica sekcji, dolina, jezioro bezodpływowe) rośnie dalej z odległością od końca. Pozostałe wygaszania (ukośna rynna, za wąskie jezioro, drugi kontur, koniec śladu) dodają do odległości `tunnelBank` · (1 − wygaszenie), rozłożone na co najmniej 1,5 zasięgu niecki. K5 dodawał cały `tunnelBank` na kilku metrach (`1 − smoothstep(0; 0,2; lens)`), stąd ściany 36 m na 4 m na końcach jezior (problem poważny 6).

Wynik:
- oba kadry pasów: 0 przecieków i 0 szwów (`StandingWaterContainmentTest`);
- nowy test `StandingWaterTest.tunnelLakesHaveOneLevel`: jedno id = jedno lustro i żaden sąsiad 1 m w innym jeziorze rynnowym z innym lustrem. GAMEPLAY ±20 km co 25 m: 9241 kolumn, 45 jezior; REAL ±60 km wokół okna moreny co 75 m: 5682 kolumny, 25 jezior; 0 błędów;
- `tunnelLakesEndBeforeValleys`: REAL 14 jezior (najmniejszy odstęp od doliny 30,2 m), GAMEPLAY 9 (15,3 m);
- woda jezior rynnowych: GAMEPLAY ±8 km 10511 → 9979 kolumn, ±20 km 10060 → 8953 (−11%: znikły pasy i jeziora na konturach, które nie przecinają środka sekcji); REAL wysoczyzna ±40 km ok. −5%.

Jezioro przy (17600, −1290) GAMEPLAY było artefaktem kotwicy z iteracji od kolumny. Kontur, który teraz przez nie przechodzi, ma inne losowanie i nie ma jeziora. Koszt: kolumna przy rynnie nie liczy już iteracji Newtona, tylko czyta blok, więc `tunnelLakeAt` jest tańszy.

**4. Starorzecza nad dnem innej doliny i w „studniach” (poważne, dwa zgłoszenia).** W K5 lustro starorzecza (z doliny dominującej F1) leżało czasem nad niższym dnem innej doliny albo w rowie na stoku, wysoko nad dnem. Pomiar w K5 (teren po dolinach minus lustro, w kolumnach wody starorzeczy):
- REAL: 2,5–4,1 m we wszystkich;
- GAMEPLAY: p10–p90 2,7–4,1 m, ale 5% kolumn ma > 40 m (do 117 m), a 18 starorzeczy ma minimum < 1 m.

Poprawka (`oxbow`, nowy parametr `floor` = teren po wszystkich dolinach w kolumnie): szerokość mnożona przez smoothstep(lustro + 1,5; lustro + 2,5; floor) · (1 − smoothstep(lustro + 4,2; lustro + 5,5; floor)) (`OXBOW_FLOOR_MIN`, `OXBOW_FLOOR_MAX`), czyli w sposób ciągły.

Wynik na siatce testu starorzeczy. Dla każdej kolumny wody: 8 sąsiadów 1 m nie może mieć suchego gruntu poniżej lustra, a 8 punktów 3 m nie może mieć suchego gruntu > 10 m nad lustrem.
- GAMEPLAY: K5 62 z 644 starorzeczy z błędem (11 przecieków, 309 „studni”) → 0 z 564;
- REAL: 0 → 0 (2589 → 2583 starorzeczy);
- kadry: (−642, 2156) 156 par przecieku → 0, (7040, −5640) 74 → 0, (−4519, 2968) 372 → 0; półksiężyce na stoku przy (1516, −4408) zniknęły;
- woda OXBOW GAMEPLAY ±8 km: 940 → 828 kolumn (−12%).

To kryterium weszło do `StandingWaterTest.oxbowLakesAreCrescents`.

**5. Wpływ D5 na rzeki (poważne): decyzja użytkownika, teren bez zmian.** Javadoc `shapeCoast` twierdził, że teren poza pasem 25 km·meso się nie zmienia. Teraz opisuje wpływ na sieć rzeczną. Opis D5 w tym dokumencie (wyżej) też poprawiłem. Sprawa jest w „Co zostaje” jako decyzja do podjęcia przed commitem K5.

**6. Ściany niecek jezior rynnowych (poważne).** Poprawka jest w punkcie 3 (eliptyczna odległość od brzegu i wygaszanie bez skoku `tunnelBank`). Skan 1 m, pary suchych sąsiadów > 2 bloki na blok przy wodzie stojącej (K4c → K5 → runda 1):
- R_pasy 6 km: 30562 → 3029 → 0;
- R_jrA 6 km: 5024 → 1257 → 0;
- G_morena 3 km: 6521 → 416 → 0;
- G_jr6326 2 km: 7617 → 1186 → 0;
- G_jr6134 2 km: 436 → 46 → 0;
- G (−16500, −8820) 1 km: 9900 (K5) → 0;
- G (2180, 17650) 1 km: 15447 (K5) → 6 (3 bloki, 4,6 m, w pierścieniu siedliskowym oczka, na zboczu, nie przy wodzie).

**Drobne:**
- **Udział klifu w oknach 40 km REAL** (pomiar recenzji): średnio 18,3%, ale na okno 0–58%, mediana 12%. 11 z 30 okien nie ma klifu, 7 ma > 35% (fala szumu klifu 30 km·meso). W GAMEPLAY 12 okien: 13,6–24,7%, mediana 19,7%. Klify w skupiskach są wiarygodne dla polskiego wybrzeża. Jeśli D5 ma znaczyć ok. 20% na każde 40 km, trzeba skrócić falę szumu (do decyzji, „Co zostaje”).
- **`WatersideZonesTest.floorZonesFollowDominantRiver`:** równina przybrzeżna jest liczona osobno i wypisywana. GAMEPLAY: 683 z 4834 kolumn dna (14,1%), F2 znajduje tam rzekę w 75,7%, całe dno z równiną 90,6%. Nowe asercje (obie skale): udział równiny ≤ 16% i całe dno z równiną ≥ 90%. Spadek należy do decyzji o D5.
- **`segmentCullingIsInvisible`** porównuje też `floorGap` (do końca soczewki jeziora rynnowego, (30 + 570) m·k), `lakeGap` i pola starorzecza (lustro, głębokość, lustro pierścienia, odległość od brzegu).
- **Dokumentacja:** klasa `Coast` (glina tylko przy udziale klifu ≥ 0,5) i javadoc testu `kettleBogsNeverAboveSurroundings` (przeniesiony nad metodę).
- **Przejście klif → brzeg niski:** wydma przednia rośnie z obniżonej wierzchowiny klifu przez gładkie maksimum (zaokrąglenie do 1 m · garb) zamiast `max()`. Proste cięcie końców wydm przez dna dolin (`min` z doliną) zostaje, bo dolina wcina się w teren po kształcie brzegu (M5, „Co zostaje”).
- **Brzeg jezior bezodpływowych (K5.5):** trzecia harmoniczna szumu po okręgu (4,6, amplituda 0,2), żeby boki między płatami nie były proste. Miejsce `realistic_sink_lake_1659` (ta sama ściana niecki pod zboczem masywu, M5) się przesunęło: 50 / 30,2 m → 62 / 31,7 m. Limit testu zaktualizowany.
- **Kadr „starorzecza B”** REAL (19500, 70400) nie ma już starorzecza: w K7 trzeba wybrać nowy kadr kontrolny.

**Złoty test.** Lista `K5.txt` dostała `REAL B / tunnel_valley_lake` i `REAL A 0.5 / tunnel_valley_lake` (niecki jezior rynnowych bez ścian). Wszystkie inne zmiany rundy są w łatach, które już są na liście (wybrzeże, wody stojące). `terrainMatchesGolden` przechodzi.

**Koszt** (`costTest`, osobno, dwa uruchomienia, stosunek do kopii M1, mediana / z minimów): REAL cały obszar 1,131 / 1,131 i 1,164 / 1,152 (K5: 1,215–1,217, czyli teraz w budżecie D1 1,20: kolumna przy jeziorze rynnowym nie liczy już iteracji Newtona); REAL Beskidy 1,223 / 1,115 i 1,097 / 1,113; REAL wielki masyw 1,059 i 1,000; GAMEPLAY cały obszar 1,100 i 1,155; GAMEPLAY Beskidy 1,223 / 1,183 i 1,205 / 1,139; GAMEPLAY wielki masyw 1,184 / 1,118 i 1,084 / 1,096. Runda nie zmienia nic w górach, więc rozrzut median Beskidów (1,10–1,22 między uruchomieniami) to szum pomiaru; z minimów oba uruchomienia ≤ 1,18. µs na kolumnę (mediana): 4,71–4,78 / 7,14–8,38 / 6,65–7,31 / 6,22–6,58 / 11,58–12,56 / 11,80–12,94, w limitach bezwzględnych D1.

**Pełny `test`** (`tools/dev/run-tests test`): BUILD SUCCESSFUL w 8 min 53 s, 149 testów, 0 porażek, 0 błędów, 0 pominiętych (drzewo `5d45cddb7352`, ostatni kod rundy; po zmianie samej dokumentacji ponowny przebieg `run-tests test` jest UP-TO-DATE).

**Nowe testy:**
- `StandingWaterContainmentTest`: szczelność na siatce 1 m w 12 kadrach GAMEPLAY i 2 REAL z recenzji oraz przy pierwszym znalezionym oczku i jeziorze rynnowym w każdej skali, z regułą jak w `assertSitesContained`;
- `StandingWaterTest.tunnelLakesHaveOneLevel`;
- szczelność i „studnie” w `oxbowLakesAreCrescents`;
- okna zalewów w `SurfaceContinuityTest`.

### Runda 2 poprawek po recenzji K5

Recenzja rundy 1 zgłosiła 2 problemy blokujące, 3 poważne i 8 drobnych. Wszystkie blokujące i poważne odtworzyłem na stanie rundy 1 narzędziami recenzenta (`RevR1 win/pairs/tw/kr`, `RevDam`, `RevKT`, `RevLow`; skan 1 m: przecieki, szwy lustra, pary suchych sąsiadów > 2 bloków na blok). Wszystkie się potwierdziły. Drobne 1–5 i 7 były już zrobione w rundzie 1 (to lista z recenzji K5), 6 i 8 zostają w „Co zostaje”.

**1. Oczka przy korytach (A3c, blokujące).** Oczko stosuje się tylko w kolumnach bez wody, a jego lustro pochodzi z terenu po dolinach. Gdy potok płynął wyżej niż lustro oczka, koryto zostawało na swojej wysokości, a niecka obniżała grunt obok: potok na grobli nad torfowiskiem. Kadry GAMEPLAY (skan 1 m): (3755, 11000) 202 pary przecieku do 29,5 m, (430, −10310) 275 par do 19,3 m.

Poprawka (`kettleState`). Gdy zasięg oczka (1,25 r + `KETTLE_BANK`) spotyka koryto (`channelDist` w środku ≤ zasięg + 2 m), oczko istnieje tylko wtedy, gdy:
- brzeg koryta leży poza wodą oczka (co najmniej 5 m za jego brzegiem),
- stok niecki nad korytem jest wyżej niż woda koryta: ⌊poziom koryta⌋ + 2 ≤ lustro + 0,25 · (odległość brzegu koryta od brzegu oczka − 5).

Stok niecki jest wtedy wyżej niż woda koryta, więc niecka nie obniża gruntu przy wodzie poniżej jej poziomu. Lustro oczka liczy się tu tylko dla oczek przy korycie (raz na oczko). Pierwsza wersja (oczko nie istnieje przy każdym korycie w zasięgu) usuwała 71 z 1671 oczek w GAMEPLAY ±20 km i 11% wody oczek ±8 km. Teraz:
- kadry: 0 przecieków;
- skan „oczko przy korycie” (okna 80 m przy każdym oczku, którego niecka sięga koryta): GAMEPLAY ±20 km 40 oczek, REAL wysoczyzna ±40 km 4 oczka, 0 przecieków;
- przegląd oczek przy innej wodzie (`RevKT`, okna 1 m przy oczkach do 150 m od innej wody): GAMEPLAY ±20 km 1579 oczek, 907 przy wodzie, 0 przecieków i szwów;
- woda oczek GAMEPLAY ±8 km: 5607 → 5376 kolumn, torf 3490 → 3442 (wobec stanu sprzed K5 −13% i −12%, granica testu ±15% bez zmian).

**2. Jeziora rynnowe przy zalewie (blokujące).** Lustro jeziora rynnowego leżało poniżej morza, gdy jego okrąg trafiał w zalew albo w niskie zaplecze D5. Skutek: szwy lustra między zalewem (0 m) a jeziorem i woda zalewu obok suchego gruntu poniżej morza. Kadry GAMEPLAY (skan 1 m): (−32039, −24520) 167 szwów do 18 m i 298 przecieków do 17 m, (−6400, 32900) 290 i 288, (−30100, 10080) 256 i 55.

Poprawka: jezioro rynnowe z lustrem < 1 m nie istnieje. Lustro jest jedno na jezioro, więc jezioro jest całe albo go nie ma. Lustro liczy się teraz z gruntu wzdłuż samego jeziora (punkt 4), więc jezioro, którego pas brzegu sięga zalewu, ma lustro ≤ 0. Wynik:
- trzy kadry: 0 szwów i 0 przecieków;
- GAMEPLAY ±40 km co 20 m: przecieki zalewu 4 → 0 i szwy zalew–jezioro 3 → 0;
- `RevLow`: jezior rynnowych z lustrem < 1 m nie ma w GAMEPLAY ±40 km ani w REAL (w rundzie 1 w REAL ±250 km były 2).

**3. Ściany niecek jezior rynnowych (poważne).** Zgłoszenie się potwierdziło: odległość od brzegu zmieniała się o 3–8 m na metr, a rampa niecki (0,4–1,0 zasięgu) mieściła się w kilku metrach. Znalazłem cztery przyczyny:
- **Drugi kontur.** Wygaszenie przy drugim konturze liczyło odstęp e2 − e1 wzdłuż x. Wzdłuż z zmienia się on do 2 · 2,5 m na metr (nachylenia konturów). Teraz odstęp liczy się w poprzek konturów: e · cos własnego konturu, z cos przyciętym na końcach śladu. Zmienia się o najwyżej ok. 2 m na metr.
- **Skalowanie połowy szerokości.** Połowa szerokości h była mnożona przez wygaszenia: przy drugim konturze na 3 · `tunnelBank`, przy końcu śladu na 1,5 · `tunnelBank`, przy ukośnym konturze przez smoothstep(0,75; 0,92; cos). Przy h do 350 m dawało to 2–8 m na metr. Teraz h jest ograniczona samą odległością: h ≤ (odstęp − `TUNNEL_SPLIT`)/2 i h ≤ `runs`. `runs` to odległość wzdłuż z od końca śladu albo od węzła, w którym kontur odchodzi od N–S o więcej niż ok. 39° (`TUNNEL_COS` 0,78). Liczy się ją raz na ślad i interpoluje, więc zmienia się o najwyżej 1 m na metr. Wygaszenie zasięgu niecki ((1 − f) · zasięg) zostaje, rozłożone na 3 i 1,5 zasięgu.
- **Głębokie wcięcie przy stałym zasięgu.** Na wysoczyźnie GAMEPLAY niecka wcina się na 50–180 m. Przy zasięgu 70 m rampa ma wtedy nachylenie 2–4 m na metr nawet przy odległości zmieniającej się o 1 m na metr. Teraz zasięg niecki jest osobny dla każdego jeziora: 1,2 × największa wysokość gruntu nad lustrem + 1 m (próbki śladu przy połowie szerokości + 1,5 `tunnelBank`), od `tunnelBank` do min(3 `tunnelBank`; 0,9 `tunnelSill`) (`tunnelLake`, `tunnelBankMax`). Druga granica wynika z progu między jeziorami łańcucha: tam odległość od brzegu wynosi co najmniej `tunnelSill`. Gdyby zasięg ją przekraczał, niecki dwóch jezior z różnymi lustrami spotykałyby się na granicy sekcji (pierwsza wersja: skok 12 m).
- **Doliny.** Wcięcie sięga też niżej (punkt 4), więc niecka przy końcu jeziora obniżała dno sąsiedniej doliny poniżej wody rzeki. Najpierw ograniczałem zasięg przy dolinie, ale to ściskało rampę: ścian było 100. Teraz jezioro kończy się dalej od doliny: `TUNNEL_END_GAP` + zasięg − 0,5 `tunnelBank`. Niecka sięga w wycięcie doliny najwyżej na 0,5 `tunnelBank` − `TUNNEL_END_GAP` (K5: `tunnelBank` − `TUNNEL_END_GAP`). Przy jeziorze bezodpływowym zasięg wraca do `tunnelBank` o 1 m na metr luki. `tunnelLakesEndBeforeValleys` ma teraz granicę `TUNNEL_END_GAP` + 0,5 `tunnelBank`; zmierzone: REAL 103 m, GAMEPLAY 50,9 m.

Pierścień siedliskowy (s skończone) zostaje bez zmian: max(`tunnelBank`, 150 m·k), także gdy niecka sięga dalej. Ślady i kontury są te same co w rundzie 1, tylko margines bloku jest szerszy (`tunnelRelevant` × √(1 + 2,5²) i większy zasięg).

Wynik, pary suchych sąsiadów > 2 bloki na blok przy jeziorze rynnowym (runda 1 → runda 2):
- GAMEPLAY ±20 km co 10 m: 81 (do 4 bloków) → 16 (do 5);
- GAMEPLAY ±40 km co 20 m: 59 (do 17) → 14 (do 8);
- REAL wybrzeże ±25 km co 15 m: 28 (do 5) → 13 (do 3);
- REAL wysoczyzna ±20 km co 10 m: 0 → 0;
- kadry recenzji: (17600, −1290) 186 → 0, (10730, 12420) 73 → 59.

Zostają dwa rodzaje miejsc. Pierwsze to krawędź obszaru młodoglacjalnego w GAMEPLAY, np. (10350, 11920) i (10730, 12420): połowa szerokości idzie za wagą typu (obecność, gate), a ta w GAMEPLAY zmienia się z 0 do 1 na ok. 300 m. Drugie to strome zbocza samego terenu (krok surowego terenu 1,4–3 m na metr) przy głębokim wcięciu. Do „Co zostaje”.

**4. Jeziora na groblach (poważne).** Lustro pochodziło z okręgu 450 m·k wokół jednego punktu kanonicznego. Gdy jezioro biegło wzdłuż zbocza, jego wał (lustro + 1 m przy s < 15–40 m) stawał się groblą. Teraz lustro to najniższy grunt przed dolinami (`landElevation`) wzdłuż samego jeziora minus 1 m (`tunnelLake`). Próbki leżą w każdym węźle śladu w obrębie soczewki sekcji (i do 40 m za jej końcami): na osi i w poprzek na 15 m, 35 m, ¼, ½, ¾ i całej połowie szerokości oraz 15 i 35 m za nią. Połowa szerokości to ta z obecności i gate w węźle, bez pozostałych wygaszeń, czyli górne ograniczenie. Brzeg zaokrąglonego końca leży w pasie wału prawie na całej szerokości jeziora, choć woda zwęża się do czubka, dlatego próbki w poprzek nie zwężają się na końcach. Klucz pamięci podręcznej to pierwiastek śladu (ten sam w każdym bloku). Id jeziora się nie zmienia. Koszt to ok. 6 tys. wywołań `landElevation` raz na jezioro.

Wynik, suche kolumny ponad terenem surowym przy jeziorze rynnowym:
- > 8 m: GAMEPLAY ±20 km co 10 m 2287 → 0, ±40 km 7334 → 0;
- > 3 m: GAMEPLAY ±20 km 0 (nowy test).

Dla porównania: oczka mają do 11 m (13 kolumn, stan K5.4, nie zmieniałem).

Ceną jest głębsze wcięcie. Wcięcie przy brzegu GAMEPLAY ±20 km: mediana 51 → 69 m, najwięcej 126 → 181 m; jezior z groblą > 3 m 23 → 0. REAL: mediana 8 m, najwięcej 36 m.

**5. D5 a rzeki (poważne): decyzja użytkownika.** Bez zmian w terenie. BRIEF przenosi K5 do „W trakcie” do czasu decyzji (warianty a/b/c w „Co zostaje”).

**Wody jezior rynnowych** (stan rundy 1 → runda 2):
- GAMEPLAY ±20 km co 20 m: 14495 → 9703 kolumn (−33%). Przyczyny: koniec dalej od dolin (największa część), próg ukośnego konturu, brak jezior przy zalewie;
- REAL ±60 km wokół wysoczyzny co 75 m: 5682 → 5852 (+3%);
- `tunnelLakesHaveOneLevel`: GAMEPLAY 45 → 35 jezior, REAL 25 → 26, 0 błędów.

**Złoty test.** Te same łaty co w rundzie 1, wszystkie na liście `K5.txt` (REAL B `tunnel_valley_lake` z lustrem o 1 m niżej, łaty `grid`, `kettle_pond`, `outwash_plain_lake`). `terrainMatchesGolden` przechodzi bez zmian listy.

**Nowe testy:**
- `StandingWaterContainmentTest`: kadry rundy 2 (oczka przy korytach, zalewy, niecka przy dolinie (17620, −1819), (24520, 16080));
- `StandingWaterContainmentTest.tunnelValleyBasinsHaveNoDamsOrWalls`: GAMEPLAY ±20 km co 20 m i REAL ±20 km co 40 m. Brak suchych kolumn > 3 m ponad terenem surowym; ścian > 2 bloków najwyżej 6 w GAMEPLAY (zmierzone 3) i 0 w REAL.

**Koszt** (`costTest`, osobno, dwa uruchomienia, stosunek do kopii M1, mediana): REAL cały obszar 1,164 i 1,168 (z minimów 1,181), czyli w budżecie D1 1,20; REAL Beskidy 1,104 i 1,122; REAL wielki masyw 1,009 i 1,001; GAMEPLAY cały obszar 1,166 i 1,165; GAMEPLAY Beskidy 1,164 i 1,150; GAMEPLAY wielki masyw 1,121 i 1,114. µs na kolumnę (mediana): 4,47–4,69 / 7,14–7,15 / 6,53–6,55 / 6,16–6,18 / 10,86–10,90 / 11,80–11,87. Lustro i zasięg liczą się raz na jezioro, a szersze bloki konturów raz na blok, więc koszt kolumny się nie zmienia (w granicach rozrzutu rundy 1).

**Pełny `test`** (`tools/dev/run-tests test`): BUILD SUCCESSFUL w 8 min 52 s, 150 testów, 0 porażek, 0 błędów, 0 pominiętych (drzewo `ff0d3101701e`).

### Co zostaje

- **Decyzja użytkownika przed commitem K5: wpływ D5 na poziomy rzek GAMEPLAY** (20% lądu dalej niż 6 km·meso od brzegu zmienione o > 5 cm, 16% o > 1 m, do 175 m, przełożone ujścia; REAL ok. 2% przy zalewach; `GAMEPLAY A / summit` traci cel; F2 całego dna GAMEPLAY z równiną przybrzeżną 90,6%). Możliwości: (a) zostawić (przebieg wygląda wiarygodnie, recenzja nie widzi artefaktów); (b) rozdzielić poziomy sieci przy brzegu od terenu D5 (np. dolny limit poziomu z dawnego kształtu tylko w pasie niskiego brzegu; ryzyko koryt na wałach); (c) obniżać zaplecze mniej albo tylko tam, gdzie szum jest wysoki. Każda z nich wymaga nowego pomiaru łat.
- **Do decyzji (drobne): rozrzut klifu na 40 km** REAL 0–58% na okno (mediana 12%) przy średniej 18–22%; krótsza fala szumu klifu dałaby ok. 20% na każde 40 km.
- **Koszt:** po rundzie 2 REAL cały obszar 1,16–1,17 × M1 (z minimów 1,18; budżet D1 1,20), GAMEPLAY 1,11–1,17. K6 (A16) doda koszt `blend`, więc pomiar trzeba powtórzyć po K6; zapas do budżetu jest mały.
- **Jeziora rynnowe GAMEPLAY (runda 2):** wody ubyło o ok. 1/3 (±20 km), bo jezioro kończy się dalej od dolin, a ukośne odcinki konturu (> ok. 39° od N–S) nie mają jeziora. Wcięcie niecek jest głębsze (mediana 69 m, do 181 m na wysoczyźnie GAMEPLAY), bo lustro to najniższy grunt wzdłuż jeziora. Zostały ściany > 2 bloków w dwóch rodzajach miejsc: na krawędzi obszaru młodoglacjalnego, gdzie połowa szerokości idzie za wagą typu (GAMEPLAY (10350, 11920), (10730, 12420): do 5 bloków), i na stromym terenie surowym przy głębokim wcięciu (np. (−20500, 10940): 8 bloków, teren surowy 2,9 m na 1 m). Pierwsze usunęłaby obecność liczona na osi jeziora (funkcja z), ale wtedy wczesne odrzucenie kolumn spoza strefy młodoglacjalnej nie byłoby pewne i trzeba by budować bloki konturów wszędzie (koszt).
- **Oczka:** grobla do 11 m ponad terenem surowym przy 3 oczkach GAMEPLAY ±20 km (13 kolumn co 10 m; lustro z okręgu po dolinach, K5.4). Recenzja jej nie zgłaszała; do M5 albo K7.
- **Test końca jeziora rynnowego przy jeziorze bezodpływowym:** w obszarach `tunnelLakesEndBeforeValleys` żadne jezioro rynnowe nie leży przy jeziorze bezodpływowym (luka zostaje na limicie), więc ta droga nie ma pokrycia testem (drobne z recenzji K5).
- **Końce wydm przy dnach dolin** (proste cięcie `min` z doliną, REAL (−220637, −157487), GAMEPLAY (−699, 8025)) i obwiednia wydm szarych równoległa do brzegu: M5.
- **Koniec zalewu wzdłuż brzegu** zwęża się jak √(odległość), ale przy długim wygaszaniu szumu zalewu (fala 60 km·meso) to wąski klin długości ok. 1–2 km; zaokrąglony koniec wymagałby wygaszania wzdłuż brzegu na długości porównywalnej z szerokością zalewu (M5 albo strojenie).
- **R5** (ściany rzutu w REAL do 2,75 bloku na blok) i remisy D4a: M5.
- **Szczelina rzutu** 40 m × 0,1 m w (74489,5; 89655,02) REAL: M5.
- **Ściana niecki jeziora bezodpływowego** pod stromym zboczem masywu (`realistic_sink_lake_1659`: 62 pary > 25 m na 5 m, do 31,7 m): M5, razem z zasięgiem niecki niezależnym od filtra kafla.
- **Ściana klifu** (2,5 m na 1 m) w pasie brzegowym GAMEPLAY do 2,40 bloku na blok: to klif, poza kryterium D4b.
- **K7:** łaty `lagoon` i `cliff` (utrata celu), `oxbow_lake` w GAMEPLAY A i REAL A 0,5 (pokrycie OXBOW) i `summit` w GAMEPLAY A są szukane od nowa; trzeba sprawdzić, że pokrycie OXBOW wraca.
- **Klasyfikator siedlisk (etap H):** ocena udziałów biomów wybrzeża (CSV podglądu, wyżej przy D5) i kadru `coast_lagoon_3km` po nowej geometrii.

### Decyzje po K5

- **D5a (użytkownik, 2026-10-06): zostawić wpływ niskiego brzegu D5 na poziomy rzek** (wariant a). Liczby z „Co zostaje”: GAMEPLAY ok. 20% lądu dalej niż 6 km·meso od brzegu zmienione o > 5 cm, 16% o > 1 m, lokalnie do 175 m przez zmianę biegu; REAL ok. 2%. Uzasadnienie: realistyczne (polskie rzeki dochodzą do morza prawie na poziomie zera), świat nie jest wydany. Wymóg dla recenzji: sprawdzić na kilku kadrach miejsca o największej zmianie, że nie powstały artefakty (kaniony na nizinie, wiszące doliny, koryta na wałach, rozlana woda); Javadoc `shapeCoast` i docs opisują ten wpływ zgodnie z prawdą.
- **Rozrzut klifu w oknach 40 km: zaakceptowany.** Globalnie klif 21–22% brzegu, w oknach 40 km REAL 0–58% (mediana 12%). Skupiska zgodne z polskim wybrzeżem (Wolin, Trzęsacz, Rozewie, Orłowo; długie odcinki bez klifu na mierzejach). Bez zmian w kodzie.
- **R5:** wynik opisany wyżej („R5: szersza ściana rzutu w REAL”): wyjątek od D4a/D4b do M5.
- **Znane ograniczenia K5 przenoszone do K6:**
  - Problem 3 (ściany niecek jezior rynnowych) nadal nie jest naprawiony. Kryterium D4b (≤ 2 bl/bl) jest złamane w kilku skupiskach GAMEPLAY i problem przetrwał 2 rundy. Przyczyna: lustro to najniższy grunt wzdłuż całego jeziora, więc jezioro przecina wzgórza moreny na 100–185 m; zasięg niecki jest ograniczony (szczegóły i miejsca w „Co zostaje”, punkt o jeziorach rynnowych GAMEPLAY).
  - Nowy test `tunnelValleyBasinsHaveNoDamsOrWalls` i metryka „tw” z raportu widzą tylko pierścień siedliskowy, a nie całą nieckę. Oba liczą pary, w których jedna kolumna ma `standingWaterKind == TUNNEL_VALLEY_LAKE`; pierścień ma max(`tunnelBank`; 150 m·k): 75 m GAMEPLAY, 150 m REAL. Ściany głębiej w niecce nie są objęte kryterium.
  - Woda jezior rynnowych w GAMEPLAY spadła wobec K4c dwa razy bardziej, niż zakładał projekt: projekt (PROJEKT_POPRAWKI, K5.2) przewidywał −37%, a jest −67% (wobec rundy 1 raport i docs podają tylko −33%, bez odniesienia do K4c i projektu). Pomiar `RevLow` na kolumnach jezior rynnowych (GAMEPLAY ±20 km co 20 m) do powtórzenia w K6 z odniesieniem do K4c.

## K6. Okno mieszania regionów 5 × 5 (A16), D3 (prototyp, odłożone) i domknięcie znanych ograniczeń K5

Baza: `fb60fdd` (K5 po rundzie 2, drzewo czyste). Narzędzia pomiarowe (poza repozytorium): skan szwów `landElevation` (nadwyżka kroku 1 m nad średnią sąsiednich kroków > 0,5 m), porównanie `blend` z pełnym oknem 7 × 7 liczonym siłą, skan ścian przy jeziorach rynnowych z całą niecką (`tunnelBasinMargin`), liczniki wody, zrzuty `sample` baza/teraz, bench A/B w osobnych JVM.

### A16: okno 5 × 5 bez nowych szwów

`LandscapeModel.blend` czyta drugi pierścień okna (16 komórek) tylko tam, gdzie któraś jego komórka może dostać wagę. Środek komórki leży w 0,15–0,85 komórki wzdłuż każdej osi, więc dla każdej komórki jest dolne ograniczenie odległości (`ringBound`, osobno w x i z). Komórka, której ograniczenie przekracza min + 9τ (próg wag `BLEND_CUTOFF`, waga e⁻⁹), i tak nie dostałaby wagi, więc nie jest czytana. Wynik jest zawsze dokładnie taki jak dla pełnego okna 5 × 5, a przełączanie „czytam / nie czytam” nie robi szwu (pominięta komórka i tak ma wagę 0). To wariant z recenzji projektu („per punkt jako dokładne ograniczenie”). Trzeci pierścień nigdy się nie liczy: jego ograniczenie to co najmniej 2,15 komórki, najbliższy środek jest najdalej 0,85·√2 ≈ 1,2 komórki, a 9τ to najwyżej 0,82 komórki (τ ≤ 0,2 komórki / 2,2), także przy suwaku regionów.

Sprawdzenie dokładności: wagi typów `blend` wobec okna 7 × 7 liczonego siłą: GAMEPLAY 1,0 i 0,3 (±20 km co 37 m, 1,17 mln punktów), REAL 0,5, 1,0 i 4,0 (0,36 mln punktów każdy): 0 różnic > 10⁻¹², największa 6·10⁻¹⁵.

Ile punktów czyta drugi pierścień (co najmniej jedną komórkę) i ile ma w nim wagę > 0:
- GAMEPLAY: 37,9% / 8,9% (największa waga drugiego pierścienia 0,024);
- REAL 1,0: 0,12% / 0,008% (najwięcej 0,0001), więc REAL z domyślnym suwakiem praktycznie nie płaci za A16;
- REAL 0,5: 13,1% / 1,7%.

Szwy:
- miejsce z projektu (5540, 2725) GAMEPLAY, okno 600 m co 1 m: 1096 punktów ze szwem `landElevation` (do 4,95 m) → 1 (0,50 m, (5666, 2730), na granicy progu skanu);
- GAMEPLAY ±20 km co 13,7 m: największy szew 4,83 m (5537, 2852) znika; zostaje 384 punktów nadwyżki > 0,5 m (406 w K5) do 2,32 m, z innych przyczyn (te same co w K5);
- REAL 0,5 ±300 km co 211 m: 11 → 11 (do 1,19 m, bez zmian);
- `SurfaceContinuityTest`, okno `gameplay_stream`: 6 skoków / 3,03 m → 0 (cel projektu ≤ 5); limit okna 0;
- `RiverNetworkTest.noStepAtLowlandThreshold`: 0 szwów mieszania przy progu nizin 0,3 w obu skalach (kopia M1: 1 w GAMEPLAY); nowa asercja: 0.

`greatMassifSummitIsNotPlateau` (ponownie): wyniki takie same jak w K5 z dokładnością wypisu (największy stosunek pasa ≥ 1650 m do pasa kosodrzewiny 0,34, największy obszar ≥ 1650 m 2,14 km² w REAL), test przechodzi. `greatMassifDomeSpansRegionTypes` też.

Testy porównujące teren z kopią M1 poza dolinami i wodą pomijają teraz kolumny z wagą w drugim pierścieniu (`LandscapeModel.secondRingWeight`, tylko dla testów): `TerrainLocalityTest.exempt` (np. `gameplay_zero`: 806 kolumn pominiętych, 0 zmienionych poza dolinami i wodą) i `LandscapeModelTest.greatMassifsReachTarget` (GAMEPLAY: 943 z ok. 11,5 tys. punktów Beskidów, REAL: 0).

**Złoty test.** Raport (`-PgoldenReport=build/golden_K6b.txt`) wobec stanu K5 (`build/golden_K5r2.txt`) ma jedną nową łatę: `GAMEPLAY A / great_massif` (dopisana w K2, po prototypie A16, więc projekt jej nie mierzył): 1 kolumna z 256, 2,4 cm, cel zachowany (na kwadracie 4 km wokół 9,4% kolumn, do 0,71 m). Z łat projektu dla A16 (`grid` w GAMEPLAY A, B i REAL A 0,5, `foothills_river` i `beskids` w GAMEPLAY B, wszystkie już na liście) notatki raportu wobec M1 pokazują zmianę `GAMEPLAY B / beskids` (max|Δsurface| 16,9 → 16,7 m); pozostałe różnią się poniżej dokładności wypisu. Lista `src/test/golden-allow/K6.txt` = lista K5 + `GAMEPLAY A / great_massif`.

### D3: wały moren czołowych W–E — prototyp nie przeszedł, odłożone do M5

Prototyp na kopii: `moraineRidge` z `ridged(x / 2,5, wz)` i zniekształceniem wzdłuż z (zamiast `ridged(wx, z / 2,5)`), sole bez zmian. Na kadrach (REAL 30 km wokół (−128000, −400000), GAMEPLAY 3 km wokół (−24400, −29500)) wały biegną W–E, a doliny N–S przecinają je przełomami. Ale:
- **testy sieci rzecznej padają** daleko od wysoczyzn, bo wały są w `landElevation`, z którego sieć liczy spływ: `RiverNetworkTest.noSpringsOnMassifCore` (GAMEPLAY: wierzchowina masywu (109062, 285995) wcięta na 54,6 m przy (109332, 286015), limit 21 m) i `SurfaceContinuityTest.denseGridHasNoCliffs` `gameplay_beskids` (krok doliny 5,91 m na 1 m przy (29947, 3165,5), limit 5,80). Z samym A16 oba przechodzą;
- złoty test: nie dochodzą nowe nazwy łat (wszystkie zmienione są już na liście), ale zmieniają się łaty rzek i gór: `GAMEPLAY A / lowland_river` (kolumny z innymi blokami niż M1: 17 → 256), `GAMEPLAY A / foothills` (max|Δ| wobec M1 6,0 → 10,0 m), `GAMEPLAY B / lagoon` (21 → 256), poza tym `grid` we wszystkich zestawach, `kettle_pond` i `peatland` w GAMEPLAY A;
- `TerrainLocalityTest` potrzebowałby wyjątku dla wysoczyzn (`gameplay_zero`: 2355 kolumn zmienionych poza dolinami i wodą, do 25 m; `gameplay_lagoon`: 1820).

Zgodnie z D3 („jeśli łamie testy sieci rzecznej… cofnąć”) cofnięte; teren bez zmian, opis w „Co zostaje” (M5: wały W–E razem z przeglądem sieci rzecznej i przełomów).

### Znane ograniczenia K5 (problemy poważne recenzji K5, nienaprawione po 2 rundach)

**Test widział tylko pierścień siedliskowy — naprawione.** Nowa metoda testowa `LandscapeModel.tunnelBasinMargin(x, z)` = odległość od brzegu jeziora rynnowego − zasięg jego niecki (≤ 0 tam, gdzie `sample` rzeźbi nieckę). `StandingWaterContainmentTest.tunnelValleyBasinsHaveNoDamsOrWalls` liczy parę, gdy któraś kolumna leży w pierścieniu albo w niecce, i wypisuje oba rodzaje osobno:
- GAMEPLAY ±20 km co 20 m: 5 par (3 w pierścieniu, 2 w zewnętrznej niecce; wcześniej test widział 3), do 3 bloków na blok; `WALLS_MAX` = 5;
- REAL ±20 km co 40 m wokół wysoczyzny: 0;
- gęste siatki 1 m w znanych skupiskach (`WALL_CLUSTERS`, z limitami równymi zmierzonym, czyli strażnik regresji wyjątku): (−20500, 10900) 500 m: 3397 par (1944 + 1453), do 8 bloków na blok; (28660, 32560) 300 m: 1541 (952 + 589), do 6; (−19990, 14380) 300 m: 1250 (731 + 519), do 4; (10350, 11915) 300 m: 386 (386 + 0), do 5; (−16170, 7800) 300 m: 232 (141 + 91), do 3.

Liczby z raportu K5 („81 → 16” przy ±20 km, „59 → 14” przy ±40 km) liczyły tylko pierścień. Z całą niecką (skan ścian zrobionych przez model, czyli przy parze terenu surowego ≤ 2 bloki): GAMEPLAY ±20 km co 10 m: 25 par (16 + 9), 7 skupisk, do 5 bloków; ±40 km co 20 m: 32 pary (15 + 17), 16 skupisk, do 8 bloków (15,5 m na 1 m przy (−20500, 10940)); przy `TUNNEL_COS` 0,6 (przed rundą 1) 37 par w 17 skupiskach. Runda 1 dodała okna 1 m skupisk spoza ±20 km do `WALL_CLUSTERS` (niżej, „Runda 1 poprawek po recenzji K6”).

**Ściany niecek jezior rynnowych (w „Decyzje po K5” problem 3; kryterium D4b ≤ 2 bloki na blok) — wyjątek do M5, do decyzji użytkownika (B3).** Rozłożyłem przyczyny w pięciu skupiskach (profil odległości od brzegu i jej składników):
- **Koniec jeziora przy dolinie na stromym gruncie** (4 z 5 skupisk: (−20500, 10938), (28653, 32547), (−19981, 14479), (−16160, 7820)): `floorGap` (szczelina za wycięciem doliny) zmienia się o 4–6 m na metr, bo zawiera połowę ściany doliny, a ściana rośnie z wysokością terenu nad dnem (`wall` = (teren − dno)/`maxSlope`, na nizinie `maxSlope` = 0,12·skala). Odległość od brzegu za końcem soczewki rośnie więc o 4–6 m na metr i rampa niecki (zasięg do 112,5 m) mieści się w 20–30 m. To nie jest skutek ograniczenia zasięgu (0,9 `tunnelSill`) ani profilu rampy: prototyp rampy na całym zasięgu (0–1 zamiast 0,4–1) zmniejszył najgorsze miejsca o 1–2 bloki (8 → 6, 6 → 5, 4 → 3), ale przesunął ściany do pierścienia (3397 → 3632, 1541 → 1705) — nie wszedł.
- **Skraj obszaru młodoglacjalnego** ((10351, 11900); w (−16160, 7820) obie przyczyny): połowa szerokości idzie za wagą typu (w GAMEPLAY 0 → 1 na ok. 300 m), zmienia się o ok. 1,7 m na metr, a wygaszanie wąskiego jeziora (`shapeFade`, 25–60 m·k połowy szerokości) dodaje do odległości od brzegu cały zasięg niecki na kilkunastu metrach (do 14 m odległości na 1 m).

Warianty (do wyboru przez użytkownika):
- (a) jezioro dzielone albo kończone progiem tam, gdzie grunt wzdłuż konturu wznosi się ponad lustro o więcej niż 1,5–2 zasięgi niecki (wariant recenzji): krótsze, płytsze jeziora, mniej wody (sprzeczne z odzyskaniem wody jezior rynnowych), lustro i zasięg dalej raz na jezioro (koszt kolumny bez zmian), ryzyko: progi między częściami muszą mieć ląd jak `TUNNEL_SPLIT`; nie usuwa głównej przyczyny (szczelina przy dolinie);
- (b) zasięg niecki ponad 0,9 `tunnelSill` z szerszymi progami łańcucha: mniej wody, nie usuwa głównej przyczyny;
- (c) **obecny stan**: wyjątek do M5 z limitem w teście liczonym z zewnętrzną niecką (wyżej);
- (d) (nowy, z rozbioru przyczyn) koniec jeziora przy dolinie od szczeliny o nachyleniu ≤ 1 (np. połowa ściany z wysokości gładkiego pola `CoarseTerrainField` zamiast terenu w kolumnie): usuwa główną przyczynę w 4 z 5 skupisk; koszt: zmiana `RiverNetwork` (łaty `tunnel_valley_lake`, `grid`), wpływ na wodę nieznany; ryzyko z rundy 2 K5: niecka nie może sięgać w wycięcie doliny poniżej wody rzeki — wymaga prototypu i pomiaru;
- dla skraju obszaru: obecność liczona na osi jeziora (funkcja z), koszt: bloki konturów budowane także poza strefą młodoglacjalną.

**Woda jezior rynnowych GAMEPLAY — bez zmian wobec K5 (próba odzyskania cofnięta w rundzie 1).** Pierwsza wersja K6 zmieniła próg ukośnego konturu `TUNNEL_COS` 0,78 (39° od N–S) na 0,6 (ok. 53°): +13% wody w GAMEPLAY (±20 km co 20 m: 9703 → 10972 kolumn), +8% w REAL, bez nowych ścian. Recenzja K6 pokazała jednak (A/B siatek co 10 m, kadry, przekroje), że jeziora odcinków ukośnych kończą się zwykle przy dolinie rzecznej, a koniec soczewki idzie za szczeliną za wycięciem doliny liczoną w samej kolumnie (`RiverHit.floorGap`). Gdzie ta szczelina trzyma się blisko końca soczewki na całym odcinku (dolina równoległa do jeziora albo wijąca się obok), odległość od brzegu zostaje mała bez wody, a niecka (do lustra + 1 m + 0,35 · odległość) wycina suche, zamknięte zagłębienia o prostych krawędziach. Próg 0,6 usuwał 2 takie miejsca z K5 ((16937, −737), (−503, −4162)), a dodawał albo powiększał co najmniej 7: GAMEPLAY (−16680, 10117) 7,7 m głębokości, (18775, −7850) do 37 m poniżej K5, (19706, −8290) 12 m, (−19175, −5641) rów 700 × 100 m, (18694, 600), (14392, −14284); REAL (−60008, 49998) 7,2 → 15,8 m. Do tego odzyskana woda to głównie jedno jezioro (967 z 1269 nowych kolumn) z lustrem 74 m w terenie 155–196 m (wcięcie 80–122 m), oczek ubyło o 0,9% (`kettleTouchesTunnelLake` widziało dłuższe kształty), a koniec nowego jeziora REAL (−59520, 2486) był klinem, nie zaokrągleniem. Przyczyna jest ta sama co głównej przyczyny ścian (lokalna `floorGap`), więc naprawa należy do wariantu d (koniec przy dolinie od gładkiej szczeliny albo od szczeliny na osi jeziora) i decyzji użytkownika; w rundzie 1 próg wrócił do 0,78. Kolumny wody jezior rynnowych (K6 po rundzie 1 = K5 runda 2, zmierzone ponownie):

| Obszar | K4c | K5 runda 1 | K5 runda 2 | K6 (0,6, cofnięte) | K6 po rundzie 1 | K6 wobec K4c |
|---|---|---|---|---|---|---|
| GAMEPLAY ±20 km co 20 m | 29338 | 14495 | 9703 | 10972 | 9703 | −67% |
| GAMEPLAY ±40 km co 25 m | 58890 | 28248 | 19825 | 22702 | 19825 | −66% |
| REAL ±60 km wokół wysoczyzny (−66495, 21873) co 75 m | 7550 | 5682 | 5852 | 6298 | 5852 | −22,5% |

Projekt (K5.2) zakładał −37%. Ubytek wobec K4c pochodzi głównie z K5.2 i rundy 1 (koniec przed doliną, kontury śledzone przez środek sekcji; K4c → runda 1: −51%), a w rundzie 2 z końca jeziora dalej od doliny (`valleyEnd` z całym zasięgiem niecki). Powrót `valleyEnd` do K5 (`TUNNEL_END_GAP` + 0,5 `tunnelBank`) dałby GAMEPLAY ±20 km 11934 (+23% wobec rundy 2), ale przywraca obniżanie dna doliny poniżej wody rzeki (runda 2 K5), więc nie wszedł. Dalsze odzyskanie wody wiąże się z decyzją o ścianach: wariant d usuwa przyczynę suchych zagłębień, więc po nim można wrócić do `TUNNEL_COS` 0,6.

### Koszt

`costTest` (trzy uruchomienia, stosunek do kopii M1, mediana / z minimów):

| Obszar | K5 runda 2 (dwa przebiegi, mediana) | K6 przebieg 1 | K6 przebieg 2 | K6 przebieg 3 |
|---|---|---|---|---|
| REAL cały obszar | 1,164 i 1,168 | 1,219 / 1,219 | 1,251 / 1,251 | 1,145 / 1,269 |
| REAL Beskidy | 1,104 i 1,122 | 1,097 / 1,157 | 1,168 / 1,162 | 1,249 / 1,208 |
| REAL wielki masyw | 1,009 i 1,001 | 1,011 / 1,016 | 1,035 / 1,023 | 0,995 / 1,026 |
| GAMEPLAY cały obszar | 1,166 i 1,165 | 1,220 / 1,202 | 1,201 / 1,201 | 1,284 / 1,197 |
| GAMEPLAY Beskidy | 1,164 i 1,150 | 1,183 / 1,179 | 1,187 / 1,187 | 1,224 / 1,226 |
| GAMEPLAY wielki masyw | 1,121 i 1,114 | 1,151 / 1,144 | 1,159 / 1,149 | 1,183 / 1,183 |

µs na kolumnę (mediana, przebiegi 1–2): REAL 5,04–5,29 / 7,60–7,87 / 6,93–7,06; GAMEPLAY 6,67–6,74 / 11,61–12,09 / 12,45–13,79 (limity bezwzględne D1: REAL cały obszar 6,5, REAL Beskidy 12, GAMEPLAY cały obszar 8,5, GAMEPLAY Beskidy 16 µs — dotrzymane). Maszyna nie była spokojna (inne procesy; przebieg 3 z czasami bezwzględnymi o ok. 50% wyższymi), więc stosunki skaczą o ±0,05 między przebiegami. Pomiar A/B w osobnych JVM na zmianę (ten sam zestaw 400 fragmentów, minima z 9 rund, najlepsze z 3–5 par): REAL cały obszar K5 4,45 µs, sam A16 4,48 (+0,7%), K6 4,46 (+0,2%); GAMEPLAY cały obszar 6,88 / 6,83 / 7,04 (+2,4%, w granicach rozrzutu); GAMEPLAY Beskidy K5 12,46, K6 12,49. Kod K6 nie dokłada więc mierzalnego kosztu w REAL (drugi pierścień czytany w 0,12% punktów), w GAMEPLAY najwyżej ok. 2%. **Rozstrzygający pomiar `costTest` wobec budżetu D1 (1,20) trzeba było powtórzyć na spokojnej maszynie** (zrobione w rundzie 1, niżej): przebiegi 1–2 dają REAL cały obszar 1,22–1,25, czyli ponad budżet, ale A/B nie pokazuje wzrostu wobec K5 (1,16–1,17).

**Runda 1 (spokojna maszyna).** Recenzja zwróciła uwagę, że w przebiegach 1–2 obecny model zwolnił bardziej niż kopia M1. Pomiar A/B w osobnych JVM tym samym `SampleCostTest` (15 rund, na zmianę K5 i K6, dwa razy każdy) pokazał, że sam K5 leży na granicy: REAL cały obszar 1,171–1,212, GAMEPLAY cały 1,174–1,187; K6 sprzed rundy: REAL 1,170–1,222, GAMEPLAY 1,202–1,245 (ok. +2–3% w GAMEPLAY). Profil (JFR, GAMEPLAY): `blend` to ok. 5% czasu `sample` (raz na kolumnę przy ciepłych kaflach, ok. 290 ns zamiast 235 ns w K5), a wyszukiwanie komórki w `ConcurrentHashMap<Long, Cell>` (z `Long.equals`) ok. 2,4%. Oszczędności rundy 1 (wynik identyczny co do bitu, złoty test bez zmian): pamięć komórek jako `DirectCache` (bez pudełkowania kluczy) i w `blend` odległość z samego środka komórki (`regionCenterX/Z`, skrót współrzędnych), a komórka z pamięci tylko wtedy, gdy dostaje wagę. `blend` sam: REAL 210 → 193 ns, GAMEPLAY 289 → 277 ns. A/B po rundzie: REAL cały 1,179–1,191, GAMEPLAY cały 1,187–1,199 (K5 w tych samych seriach 1,196–1,212 i 1,181–1,187). `./gradlew costTest -PcostRuns=15` na końcowym drzewie, dwa przebiegi, mediana / z minimów:

| Obszar | Przebieg 1 | Przebieg 2 |
|---|---|---|
| REAL cały obszar | 1,163 / 1,190 | 1,164 / 1,194 |
| REAL Beskidy | 1,128 / 1,133 | 1,140 / 1,140 |
| REAL wielki masyw | 1,012 / 1,016 | 1,012 / 1,009 |
| GAMEPLAY cały obszar | 1,189 / 1,180 | 1,193 / 1,198 |
| GAMEPLAY Beskidy | 1,159 / 1,162 | 1,173 / 1,173 |
| GAMEPLAY wielki masyw | 1,123 / 1,123 | 1,141 / 1,136 |

µs na kolumnę (mediana): REAL 4,53–4,58 / 7,16–7,23 / 6,65–6,67; GAMEPLAY 6,39–6,41 / 11,07–11,18 / 12,04–12,17. Budżet D1 (≤ 1,20 × M1; 6,5 / 12 / 8,5 / 16 µs) dotrzymany, ale zapas jest mały (GAMEPLAY cały obszar 1,19–1,20, REAL z minimów 1,19): następne kroki, które dokładają koszt `sample`, muszą najpierw szukać oszczędności (najwięcej kosztuje `RiverNetwork.query`, ok. 63% czasu; `RiverNetwork.link` też szuka w `ConcurrentHashMap<Long, …>`, ok. 2,4%).

### Testy

**Nowe i zmienione:**
- `RiverNetworkTest.noStepAtLowlandThreshold`: asercja 0 szwów mieszania;
- `SurfaceContinuityTest`: okno `gameplay_stream` 0 skoków;
- `TerrainLocalityTest.exempt` i `LandscapeModelTest.greatMassifsReachTarget`: pomijają kolumny z wagą w drugim pierścieniu (A16);
- `StandingWaterContainmentTest.tunnelValleyBasinsHaveNoDamsOrWalls`: cała niecka, `WALLS_MAX` 5, okna skupisk 1 m (`WALL_CLUSTERS`);
- metody testowe `LandscapeModel.secondRingWeight` i `tunnelBasinMargin`;
- runda 1: `WALL_CLUSTERS` z 12 oknami 1 m (5 w ±20 km i 7 skupisk ze skanu ±40 km co 20 m spoza ±20 km).

**Pełny `test`** (`tools/dev/run-tests test`, z domyślną listą `K6.txt`): BUILD SUCCESSFUL w 10 min 40 s, 150 testów, 0 porażek, 0 błędów, 0 pominiętych (drzewo `6d11811262fe`; po wpisaniu tego zdania ponowny `run-tests test` jest UP-TO-DATE). Po rundzie 1: BUILD SUCCESSFUL w 9 min 13 s, 150 testów, 0 porażek, 0 błędów, 0 pominiętych (drzewo `7c572bb08603`; po wpisaniu tego zdania ponowny `run-tests test` jest UP-TO-DATE).

### Odstępstwa od projektu w K6

1. A16 z dokładnym ograniczeniem odległości osobno dla każdej komórki drugiego pierścienia (projekt: warunek dla całego pierścienia albo stała na model); wynik ten sam co pełne 5 × 5.
2. Złoty test: dodatkowa łata `GAMEPLAY A / great_massif` (nie istniała przy pomiarze projektu).
3. `TUNNEL_COS` 0,6 (nie było w projekcie) cofnięty w rundzie 1 do 0,78: suche zagłębienia przy końcach jezior przy dolinach (opis wyżej).
4. D3 nie wszedł (prototyp łamie testy sieci rzecznej).

### Runda 1 poprawek po recenzji K6

Poważne:
1. **Koszt D1 niesprawdzony** — sprawdzony na spokojnej maszynie, z oszczędnościami w `blend` i pamięci komórek; budżet dotrzymany (szczegóły w „Koszt”, „Runda 1”).
2. **Ściany niecek łamią D4b** — prawda, ale to wyjątek do decyzji użytkownika (B3), nie wada kodu K6; K6 nie zamyka D4b. Zgodnie ze wskazówką recenzji okna 1 m dostały skupiska spoza ±20 km: skan ±40 km co 20 m (końcowy kod) daje 32 pary w 16 skupiskach, z czego 7 okien dopisanych do `WALL_CLUSTERS` z limitami ze zmierzonych wartości: (−20420, 10500) 400 m: 3183 par (1964 + 1219), do 6 bloków na blok; (−20100, 12200): 961 (293 + 668), do 4; (−20700, 11200): 930 (340 + 590), do 4; (28680, 33540): 597 (306 + 291), do 4; (−26460, 9800): 201 (120 + 81), do 3; (−23080, 39240): 247 (247 + 0), do 5; (36400, −19940): 267 (267 + 0), do 4 (okna 300 m, gdzie nie podano inaczej). Pięć dawnych okien bez zmian (3397 / 1541 / 1250 / 386 / 232).
3. **`TUNNEL_COS` 0,6 robi suche zagłębienia** — potwierdzone przekrojami ze składnikami odległości od brzegu we wszystkich 7 miejscach: koniec soczewki przy dolinie (`valleyGap` blisko `valleyEnd` na długim odcinku, `lensEnd` 0–0,5) daje odległość od brzegu 4–40 m bez wody. Lokalnie nie da się tego odróżnić od prawdziwego końca jeziora (potrzebna byłaby szczelina doliny na osi jeziora, czyli wariant d), więc próg wrócił do 0,78. Siatka GAMEPLAY ±20 km co 10 m: wobec K5 0 miejsc suchego gruntu zmienionego o > 3 m; wobec K6 sprzed rundy wszystkie zgłoszone zagłębienia znikają ((18694, 626) 18,8 m zamkniętej głębokości, (−19175, −5641) 14,6 m, (18771, −7843) 14,2 m, (19706, −8290) 11,5 m, (−16680, 10117) 7,4 m, (3498, 18093) 7,3 m, (14392, −14284) 5,4 m), a wracają suche zagłębienia K5 (największe (17359, −808) 12,4 m; razem 6 > 3 m). REAL (−60008, 49998): profil jak w K5. Woda: jak w K5 runda 2 (tabela wyżej).

Drobne:
- głębokość wcięcia odzyskanego jeziora i −0,9% oczek przy 0,6: opisane wyżej; po cofnięciu progu nie dotyczą;
- numeracja problemów recenzji K5: w K6 problemy nazwane zamiast numerów („Decyzje po K5” zostaje przy „problem 3” dla ścian), tak samo Javadoc `WALL_CLUSTERS`;
- A16: lokalna `cells` w `blend` przemianowana na `read`; zniekształcenie siatki regionów w metodach `warpX`/`warpZ` wspólnych dla `blend` i `secondRingWeight`;
- oczka znikające przy dłuższych kształtach jezior: skutek 0,6, po cofnięciu nie dotyczy;
- Javadoc `TUNNEL_COS` i akapit o wodzie przepisane z liczbami A/B; klinowe końce przy odcinkach ograniczonych biegiem konturu zostają (jak w K5);
- **fałszywy alarm:** zwolnienie `secondRingWeight(x, z) > 0` w `TerrainLocalityTest` i `greatMassifsReachTarget` nie pomija kolumn z wagą rzędu 10⁻¹². Komórka dostaje wagę tylko przy d < 9τ, czyli exp(−d) > e⁻⁹ ≈ 1,2·10⁻⁴ wobec najbliższej komórki; po normalizacji (najwyżej 25 komórek) waga drugiego pierścienia, jeśli jest, wynosi co najmniej ok. 5·10⁻⁶. Przy różnicach wysokości między typami regionów rzędu setek metrów to zmiana `landElevation` o 10⁻³ m i więcej, ponad tolerancję testów (10⁻⁶ m), więc próg > 0 jest dokładnie warunkiem „M1 może się różnić”. Liczba 8,7% kolumn GAMEPLAY to kolumny z prawdziwą wagą drugiego pierścienia (skan siłą: 26585 z 300 tys.).

Sprawdzenie dokładności po zmianach `blend`: okno 7 × 7 liczone siłą wobec `blend` w GAMEPLAY 1,0 i 0,3, REAL 1,0, 0,5 (po 300 tys. punktów) i 4,0 (100 tys.): największa różnica wag typów 6·10⁻¹⁵. Złoty test: raport identyczny z K6 sprzed rundy (jedyna nowa łata `GAMEPLAY A / great_massif`).

### Co zostaje

- **Decyzja użytkownika (B3): ściany niecek jezior rynnowych GAMEPLAY** (ściany niecek, w „Decyzje po K5” problem 3; warianty a–d wyżej). Teraz wyjątek do M5 z limitami w teście: ±20 km co 20 m 5 par, 12 okien 1 m skupisk (do 3397 par i 8 bloków na blok w (−20500, 10900)); ±40 km co 20 m 32 pary w 16 skupiskach. REAL: 0. Wariant d usunąłby też przyczynę suchych zagłębień przy końcach jezior (runda 1) i pozwoliłby wrócić do `TUNNEL_COS` 0,6.
- **Woda jezior rynnowych GAMEPLAY** −67% wobec K4c (projekt −37%; jak w K5 runda 2, bo `TUNNEL_COS` 0,6 cofnięty), do decyzji razem ze ścianami. Suche zagłębienia K5 przy końcach jezior przy dolinach (np. GAMEPLAY (17359, −808), 12,4 m) zostają do tej samej decyzji.
- **Koszt:** budżet D1 dotrzymany po rundzie 1 (REAL cały 1,16 / z minimów 1,19, GAMEPLAY cały 1,19–1,20), ale bez zapasu: kolejne kroki zwiększające koszt `sample` muszą szukać oszczędności (np. `RiverNetwork.link` bez pudełkowanych kluczy).
- **D3 (wały moren W–E):** M5, razem z przeglądem sieci rzecznej (przełomy przez wały, reguły źródeł na masywach).
- Pozostałe pozycje „Co zostaje” K5 bez zmian (R5, szczelina rzutu, ściana niecki jeziora bezodpływowego pod masywem, końce wydm, koniec zalewu, oczka z groblą do 11 m, K7: łaty do wyszukania od nowa).

## K7. Przegenerowanie `golden_terrain_m2.txt` (protokół §4) i dokumentacja

Baza: `162995f` (K6 po rundzie 1), drzewo czyste (drzewo `2e6f83ecbd2d`, pełny `test` dla niego: PASS w `build/test-logs/index`). Kod modelu (`src/main`) w K7 się nie zmienia. Zmienione: `src/test/resources/golden_terrain_m2.txt`, historia pliku w `GoldenTerrainTest.HISTORY` i Javadoc klasy, komentarze właściwości złotego testu w `build.gradle`. Katalog `src/test/golden-allow/` (listy K3–K6) jest usunięty. `golden_terrain_m1.txt` bez zmian. Protokół: §4 projektu z poprawką warunku 1 z K4 (łata „=” tylko jako zmiana numeryczna, przyrostek `numeric`) i z rozstrzygnięciami D2, D3 i D5 (wybrzeże).

### Raport przed zapisem: zmienione łaty wobec sumy list K3–K6

`./gradlew test --tests '*GoldenTerrainTest*' -PgoldenReport=build/golden_K7pre.txt` (domyślna lista `K6.txt`): test przechodzi.

- Zmienione są 81 ze 105 łat pięciu zestawów. 17 łat traci cel, a w dwóch zestawach brakuje wody OXBOW (`coverage`).
- **Raport = suma list K3–K6.** Listy są narastające (5 / 49 / 82 / 83 wpisy po rozwinięciu `*`), więc suma to lista K6. Zbiór zmienionych łat jest jej równy (81 łat i 2 wpisy `coverage`), tak samo zbiór utraconych celów (17 + 2). Nie ma zmiany spoza list ani wpisu listy bez zmiany.
- **Bez zmian zostaje 24 łaty:** wszystkie 10 łat kontrolnych (`outwash_plain_interior`, `moraine_plateau_interior`), `great_massif` w REAL A, REAL B, GAMEPLAY B i REAL A 0,5, `coast` w REAL A i REAL A 0,5, `beach` w REAL A, REAL B, GAMEPLAY A i REAL A 0,5, `summit` w REAL A, `lowland_river` i `peatland` w REAL B, `large_river` w GAMEPLAY A.
- **Każda zmiana ma przyczynę opisaną w kroku**, który dopisał ją do listy:
  - `grid` (K3, F1 i TE, potem każdy krok);
  - łaty rzek, gór, Pogórza i potoków (K4: G1B…G5, A5, K4b/K4c);
  - wody stojące (K5.1–K5.5, A3, A3c);
  - wybrzeże (K5b: D2 i D5);
  - `GAMEPLAY A / summit` (wpływ D5 na poziomy rzek, utrata celu);
  - `GAMEPLAY A / great_massif` (A16, K6).

### Sprawdzenie przed zapisem (warunki 1–6 §4)

1. **Tabela łat.** Każda łata oznaczona w tabeli „zmiana” zmieniła się, a każda „zmiana, cel” straciła cel (8 łat, jak na łącznym prototypie). Zmieniły się też łaty „=”:
   - **numerycznie** (przyrostek `numeric`, sprawdza go test, a werdykt podaje raport): REAL A `lowland_river` 0,07 µm („numerical difference only”), REAL A 0,5 `large_river` 0,12 mm i `foothills_river` 0,03 mm („numerical change of the M1 state only”);
   - **wybrzeże D2 i D5**, których tabela nie obejmowała, bo nie miały prototypu: `lagoon` i `cliff` we wszystkich zestawach oraz `coast` w GAMEPLAY A (K5, „Złoty test: lista `K5.txt`”);
   - **odstępstwo 1:** `tunnel_valley_lake` w REAL B i w REAL A 0,5, ponad próg numeryczny. W REAL B lustro jest o 1 m niżej w całej łacie (poziom kanoniczny K5.2 i koniec jeziora z rundy 2 K5), a w REAL A 0,5 powierzchnia zmienia się o 2,75 mm, bez innych bloków (niecka wygaszana gładką odległością od brzegu, runda 1 K5). Obie łaty dopisała z przyczyną runda 1 K5, ale bez zaznaczenia, że w tabeli są „=”.

   Cel traci więcej łat, niż przewiduje tabela „zmiana, cel”: `lagoon` i `cliff` (D2, D5: dawny zalew albo klif leży teraz na niskim brzegu), `GAMEPLAY A / summit` (D5) oraz pokrycie OXBOW w GAMEPLAY A i REAL A 0,5 (zgodnie z projektem i K5).
2. **Wybrzeże kolumna po kolumnie.** 0 zmienionych kolumn wobec kopii M1 jest w `coast` w REAL A i REAL A 0,5 oraz w `beach` w REAL A, REAL B, GAMEPLAY A i REAL A 0,5. Warunek nie zachodzi dla `lagoon` i `cliff` we wszystkich zestawach i dla `coast` w GAMEPLAY A: to D2 i D5, decyzje podjęte po projekcie (odstępstwo 2).
3. **Kolumna po kolumnie wobec kopii M1** (narzędzie tymczasowe w pakiecie testów, usunięte po kroku). Zmieniona kolumna to kolumna z inną powierzchnią albo grubością pokrywy (> 1 µm), poziomem lub rodzajem wody, typem, podłożem albo blokami.
   - REAL A: `river_mouth` ma 141 zmienionych kolumn, `large_river` 38, `lowland_river` 0. Wszystkie leżą w dolinie albo przy wodzie (`streamOrder > 0`, dno, pas wody stojącej, wcięcie albo woda w którymkolwiek modelu).
   - We wszystkich 105 łatach jest tak samo. Wyjątek to `great_massif` w REAL B (256 kolumn) i GAMEPLAY B (23), czyli kopuła masywu z K2, której M1 nie miał. Skróty tych łat w K7 się nie zmieniły.
   - `waterKind` zmienia się między NONE, RIVER, OXBOW, LAKE i KETTLE tylko w łatach z ciekami albo wodami stojącymi. Wliczają się tu też `grid`, `beskids`, `foothills`, `mountain_stream` i `great_massif`, bo koryta się przesunęły.
   - SEA pojawia się albo znika tylko w `lagoon` (D2) i w `grid`: od 1 do 6 kolumn na zestaw, wszystkie najwyżej 9,5% szerokości pasa 25 km·meso od brzegu, czyli w niskim brzegu D5 i przy zalewie D2. W pozostałych łatach SEA się nie zmienia (część odstępstwa 2).
4. **Lokalność.** `TerrainLocalityTest` w pełnym zestawie przechodzi, z wyjątkami: wielki masyw (K2), drugi pierścień okna 5 × 5 (K6) i pas niskiego brzegu (D5).
5. **Łaty kontrolne.** Wszystkie 10 mają w nowym pliku ten sam wiersz (środek i skrót) co przed K7.
6. **Pokrycie.** Zapis sprawdza cele i pokrycie świeżym modelem (`checkTargets`) i przechodzi, więc każdy rodzaj wody, typ i podłoże są w każdym zestawie. OXBOW wraca w GAMEPLAY A i REAL A 0,5 przez nowe łaty `oxbow_lake` (niżej).

### Zapis

```
./gradlew test --tests '*GoldenTerrainTest*' -PwriteGolden -PgoldenKeepCenters -PgoldenFile=build/golden_terrain_m2.txt
```

Od nowa są szukane tylko łaty, które w starym środku nie spełniają celu (sprawdza to świeży model). Jest ich 17, dokładnie te z raportu:

| Zestaw | Łata | Stary środek | Nowy środek |
|---|---|---|---|
| REAL A | `cliff` | (−218893, −160429) | (−181586, −207822) |
| REAL B | `lagoon` | (−12116, −87667) | (5863, −73767) |
| REAL B | `river_mouth` | (12056, −70882) | (1909, −77279) |
| REAL B | `large_river` | (−38259, −7565) | (15721, 45353) |
| REAL B | `mountain_stream` | (−61950, 293413) | (−61947, 293537) |
| GAMEPLAY A | `lagoon` | (345, 10394) | (585, 10609) |
| GAMEPLAY A | `cliff` | (−5222, −2913) | (−3216, 6103) |
| GAMEPLAY A | `oxbow_lake` | (574, −2766) | (1683, −563) |
| GAMEPLAY A | `outwash_plain_lake` | (3526, −4479) | (5401, −1821) |
| GAMEPLAY A | `summit` | (26359, 2208) | (26379, 2184) |
| GAMEPLAY B | `lagoon` | (−6119, −6903) | (−6870, −6954) |
| GAMEPLAY B | `cliff` | (−136, −1826) | (1114, −1458) |
| GAMEPLAY B | `large_river` | (2203, −1095) | (−5134, −2741) |
| GAMEPLAY B | `mountain_stream` | (−23361, 9439) | (−24278, 8072) |
| REAL A 0,5 | `lagoon` | (−113839, −72565) | (−97363, −91338) |
| REAL A 0,5 | `cliff` | (−101869, −89579) | (−86401, −110485) |
| REAL A 0,5 | `oxbow_lake` | (−19227, −13512) | (−8366, 1505) |

Osiem łat projektu („zmiana, cel”) jest wśród nich, a resztę dołożyło wybrzeże (D2, D5) i `GAMEPLAY A / summit`. Nowe łaty `oxbow_lake` mają wodę OXBOW, więc pokrycie wraca.

**Diff** (`diff` plików bez komentarzy i środków łat, jak w §4):
- różni się 81 wierszy `patch`: zbiór jest równy zbiorowi zmienionych łat z raportu; 17 z nich ma nowy środek, 64 tylko nowy skrót;
- 24 wiersze `patch` są identyczne, w tym 10 łat kontrolnych i `great_massif` w czterech zestawach;
- **odstępstwo 3:** zmienił się wiersz `GAMEPLAY A / great_massif` (A16 w K6: jedna kolumna o 2,4 cm, cel zachowany), choć §4 zakłada, że wiersze `great_massif` mają zostać identyczne;
- wiersze `field` i `total` zmieniły się we wszystkich zestawach;
- środki łat wobec pliku M1 różnią się tylko w 17 łatach z tabeli wyżej i w łatach dopisanych w K0 i K2.

**Instalacja.** Plik skopiowany do `src/test/resources/`, katalog `src/test/golden-allow/` usunięty. `./gradlew test --tests '*GoldenTerrainTest*' -PgoldenReport=build/golden_K7post.txt` bez listy daje 2 z 2 (`terrainMatchesGolden` z nowym plikiem M2, `frozenM1CopyMatchesGolden` z plikiem M1), raport: `# no changes`. W nagłówku pliku M2 jest historia K0–K7. Mechanizm list w `build.gradle` zostaje na przyszłe wieloetapowe zmiany terenu (np. M5): bez katalogu `src/test/golden-allow/` nic nie jest dozwolone.

**Dalsze zmiany terenu po K7** zmieniają już skróty nowego pliku: każdą trzeba przegenerować z `-PgoldenKeepCenters` i sprawdzić `diff`, tak jak zmianę S2 albo S3 w §4, punkt 5.

### Testy

**Pełny `test`** (`tools/dev/run-tests test`, bez flag i bez listy): BUILD SUCCESSFUL w 8 min 59 s, 150 testów, 0 porażek, 0 błędów, 0 pominiętych (drzewo `6c921c271325`, z kodem i plikiem wzorcowym K7; dokumentację uzupełniano w trakcie przebiegu, a ponowny `run-tests test` na końcowym drzewie jest UP-TO-DATE, bo dokumentacja nie jest wejściem testów).

**Nie uruchomione ponownie** (kod modelu bez zmian od `162995f`; odstępstwo 4): `costTest` (obowiązuje pomiar K6 po rundzie 1). Kadry `landscapePreview -PhabitatsOnly` i gametest `views` w pierwszej wersji K7 też pominięto, choć nie biegły w K6 (ostatnie kadry z K5 przed A16, ostatni gametest 2026-10-03, przed K0–K2); uruchomione w rundzie 1 recenzji K7 (niżej).

**Pomiar przed/po do podsumowania** (narzędzie tymczasowe, usunięte): te same 150 transektów `SurfaceContinuityTest` w każdym oknie, policzone kopią M1 i obecnym modelem (tabela w podsumowaniu niżej).

### Odstępstwa od protokołu w K7

1. Dwie łaty „=” (`tunnel_valley_lake` w REAL B i REAL A 0,5) zmieniły się ponad próg numeryczny; przyczyny z rundy 1 K5 opisane wyżej.
2. Wybrzeże D2 i D5 łamie warunek 2 (`lagoon`, `cliff`, `coast` w GAMEPLAY A) i część warunku 3 (SEA zmienia się w `lagoon` i w `grid` przy brzegu). Tabela §4 nie obejmowała D2 i D5, bo nie miały prototypu.
3. Wiersz `GAMEPLAY A / great_massif` się zmienił (A16 w K6).
4. Koszt nie jest powtórzony, bo kod modelu się nie zmienił; obowiązuje pomiar K6 po rundzie 1. (Pierwsza wersja K7 pominęła też kadry PNG i gametest `views`, opisując je jako „pomiary K6”; w K6 nie biegły. Uruchomione w rundzie 1 recenzji K7.)

### Runda 1 poprawek po recenzji K7

**Kadry i gametest na końcowym drzewie** (kod modelu = `162995f`; po K7 zmienił się tylko komentarz `shapeCoast`).
- `./gradlew landscapePreview -PhabitatsOnly` (4 min 23 s): kadry §3.4 i §5.3 w `build/preview`. Masywy mają kosodrzewinę i halę w obu skalach (`great_massif_16km` REAL: kosodrzewina 3,5%, hala 0,2%; `gameplay_great_massif_5km`: 3,0% i 0,0%). Starorzecza to półksiężyce (`large_river_valley_2km`, `gameplay_large_river_valley_1km`), zalew leży za mierzeją z plażą i wydmami (`coast_lagoon_3km`). Udziały (`m2_biome_shares.csv`, REAL / GAMEPLAY): biała wydma 0,016 / 0,326, szara 0,014 / 0,352, zalew 0,045 / 0,138, bór bażynowy 0,043 / 1,451, plaża 0,010 / 0,277, kosodrzewina i hala 0,000 (próbka losowa jak w `BiomeSharesTest`); strefy: ściana klifu 0,000 / 0,024, wydma inicjalna 0,002 / 0,049. W GAMEPLAY zalewu jest mniej niż w pomiarze K5b (0,187), zgodnie z ubytkiem wody zalewu w rundzie 1 K5 (płytsza misa przy f < 1, wyżej); wydm tyle samo. Widoczne dalej (etap H, klasyfikator): prostymi odcinkami ucięte płaty olszy szarej i łęgu w dnie potoku (`mountain_stream_1km`, odstępstwo S4 „Dno doliny z terenu”), łuki kół granic płatów biomów (`tunnel_valley_lake_1km`, `gameplay_great_massif_5km`), a jeziora bezodpływowe pod masywem REAL są prawie okrągłe.
- `./gradlew runClientGameTest -Pgametest=views` (15 min 37 s, BUILD SUCCESSFUL): model 168 ms na 64 chunki, generacja 43 ms na chunk; 13 zrzutów w `build/run/clientGameTest/screenshots`. Beskidy: grunt 1650 m (wielki masyw), świerczyna. Na zrzutach nie ma urwisk; doliny mają zwykłe tarasy po bloku. Uwagi:
  - **`cliff` nie pokazuje klifu:** cel `/polishforests find cliff` (`PolishForestsCommands.Target.CLIFF`) to dowolna kolumna z formą CLIFF, a po D5 formę CLIFF ma też wysoka wydma przednia na niskim brzegu. Kamera stoi nad plażą przy niskim brzegu. Poprawka (poza K7, zmienia komendę i gametest): warunek `lowShore < Calibration.LOW_SHORE` w celu CLIFF; do decyzji, otwarte.
  - `oxbow_lake` (−4407, 2171) REAL: woda z prostym prawym brzegiem i prosty stopień terenu wzdłuż niej; prawdopodobnie ta sama klasa co starorzecze ścięte przy dopływie (K5.1, „Co zostaje” w podsumowaniu), niesprawdzone modelem.
  - `coastal_dunes`: wydma to łagodny garb z owalnymi tarasami bloków, bez piasku na powierzchni (bloki siedlisk dopiero w S6).

**Dokumentacja.**
- `docs/03-m2-biomy.md`: odstępstwo S4 „Wybrzeże” dostało notę o D5 (`lowShore` = 1 − udział klifu, `LOW_SHORE` 0,5, próg 15 m rozstrzygnięty, liczby `CoastTest` po K5b), wiersz „ściana klifu” w tabeli §5.2 i wiersz `GoldenTerrainTest` w §12.1 (5 zestawów, dwa pliki wzorcowe) poprawione.
- `docs/m2/podglad-siedlisk-s4.md`: ograniczenia „proste krawędzie den” i „wydmy” oznaczone jako opis kadrów sprzed poprawki.
- `docs/01-architektura.md` §15: wyjątki `SurfaceContinuityTest` (D4a, R5, pas brzegowy tylko wypisywany), niecki z wyjątkiem jezior pod masywami, nowe pozycje „Odłożone do M5”.
- `docs/BRIEF.md`: zastrzeżenie o sortowaniu list `K*.txt` (`K10.txt` przed `K9.txt`) wróciło; nowe pozycje M5.
- Podsumowanie niżej: komórki „po” `gameplay_moraine` i `realistic_moraine` z definicji testu (pierwsza wersja liczyła suchy ląd w pasach wód stojących), uwagi o skokach przy zalewach i w `gameplay_massif_1660`, zdanie o `landElevation` poza masywami (A16 i D5 też je zmieniają), ściany jeziora 1659 m 148 → 62 (nie 50), wyjątki i „Co zostaje” uzupełnione.
- Javadoc `LandscapeModel.shapeCoast`: wpływ niskiego brzegu na rzeki zostaje decyzją D5a (sam komentarz).

**Jeziora bezodpływowe u stóp masywów REAL** (recenzja K7, blokujące jako brak w dokumentacji i testach; kod modelu bez zmian). Pomiar recenzji powtórzony co do liczby (siatka 5 m `spotScan` i siatki 1 m): opis i liczby w podsumowaniu, „Co zostaje”. Nowe straże regresji na stanie zmierzonym:
- `SurfaceContinuityTest.massifSpotsHaveNoCliffs`: miejsca `realistic_sink_lake_1719` (263300, −1536950, pół boku 1000 m; 598 par > 25 m, do 38,2 m), `realistic_sink_lake_1698` (146743, −1473317, 800 m; 548, 75,6 m), `realistic_sink_lake_213514` (213514, −1519439, 800 m; 20, 27,9 m);
- nowa klasa `MassifSinkLakeContainmentTest` (`slow`): na siatkach 1 m wokół jezior pod masywami 1719, 1698, 1723 i 1659 m, jeziora (213514, −1519439) i zwykłego jeziora w Beskidach (157239, 1059020) liczy pary woda–suchy grunt niżej niż lustro i stopnie rzeka–woda stojąca > 1 m; limity to stan po K7 (np. 1719 m: 289 par do 24 m, 8 stopni do 25 m), cel: zero.

**Pełny `test` po rundzie 1** (`tools/dev/run-tests test`, bez flag): BUILD SUCCESSFUL w 10 min 19 s, 154 testy (150 + 3 nowe miejsca + nowa klasa), 0 porażek, 0 błędów, 0 pominiętych (drzewo `ed67ae31a8e9`; dalej zmieniała się tylko dokumentacja).

**Wiadomość commita K6 (`162995f`) jest nieścisła:** `TUNNEL_COS` wrócił w rundzie 1 K6 do 0,78 (`LandscapeModel.TUNNEL_COS`), a wały moren W–E (D3) odłożono do M5; tytuł i punkt „TUNNEL_COS 0.78 -> 0.6” opisują stan sprzed rundy 1. Historii nie przepisujemy (bez force-push), sprostowanie idzie do wiadomości commita K7.
5. Kontrola kolumna po kolumnie i pomiar skoków M1 → K7 zrobione narzędziami tymczasowymi w pakiecie testów (usunięte), a nie `LataDiag` z katalogu projektu.

## Podsumowanie poprawki geometrii terenu (K0–K7, M2-8)

Okres: 2026-10-03 – 2026-10-06. Commity: `2546ec3` (K0–K2), `006e2d5` i `1449fcb` (K3, K4, K4b, K4c), `fb60fdd` (K5), `162995f` (K6), K7 (ten krok). Projekt i analizy powstały poza repozytorium (`PROJEKT_POPRAWKI.md`, `DECYZJE_WDROZENIA.md`, prototyp `PW/src3`).

### Problem

Użytkownik zgłosił proste krawędzie i urwiska terenu: dna dolin i ich brzegi cięte prostymi, osie dolin łamane w węzłach, ostre ujścia, prostokątne głowice dolin, źródła rzek urywające się klifem, starorzecza jako wycinki pierścieni z prostymi końcami, jeziora rynnowe ucięte prosto przy dolinach i z wałem nad doliną, niecki ze ścianą, szwy między makroregionami, wybrzeże jako płaski pas 13–24 m nad morzem ze ścianą od strony morza (prawie bez wydm) oraz za niskie Beskidy (bez kosodrzewiny i hali, decyzja M2-8).

### Decyzje

- **D1:** budżet kosztu `sample` do 1,20 × kopia M1 (bezwzględnie 6,5 / 12 / 8,5 / 16 µs: REAL cały obszar, REAL Beskidy, GAMEPLAY cały obszar, GAMEPLAY Beskidy); optymalizacje obowiązkowe.
- **D2:** zalew bez rowu, tylko za niskim brzegiem (K5b).
- **D3:** wały moren W–E z prototypem; prototyp złamał testy sieci rzecznej, więc odłożone do M5 (K6).
- **D4:** żadnych urwisk na stokach gór GAMEPLAY (K4b). Doprecyzowane przez **D4a** (cięcie omiatające tylko przy remisach ramion rzutu, K4c) i **D4b** (dolina dokłada najwyżej 3 m na 1 m, a zwykły krok to najwyżej 2 bloki na blok w obu skalach).
- **D5:** wybrzeże wydmowe, ok. 4/5 brzegu niskie z plażą i wydmami, klif tylko na wysoczyźnie (K5b). **D5a** (użytkownik, 2026-10-06): wpływ niskiego brzegu na poziomy rzek zostaje.
- **D6** (masywy typu Pilska, S2): nie wdrożone (opcjonalne).
- **A5:** głowica doliny łukiem. **G3:** lejek ujścia jako dno doliny odbiorczej w polach siedlisk; płaty łęgów przy zbiegach przechodzą do etapu H (poprawki klasyfikatora).
- **R5:** ściany rzutu w REAL ponad 2 bloki na blok to wyjątek do M5, po nieudanym prototypie w K5.
- Rozrzut udziału klifu w oknach 40 km zaakceptowany.
- **Otwarte (B3, do decyzji użytkownika):** ściany niecek jezior rynnowych GAMEPLAY (warianty a–d w K6) i ubytek wody tych jezior.

### Co zrobiono

| Krok | Zakres | Teren |
|---|---|---|
| K0 | Narzędzia: dwa pliki złotego testu, łaty kontrolne wnętrz, listy dozwolonych zmian i raport, `SurfaceContinuityTest`, `TerrainLocalityTest`, `TerrainDeterminismTest` | bez zmian |
| K1 | F2: strefy dna od koryta doliny dominującej (pola `floorChannel*`) | bez zmian |
| K2 | M2-8: wielkie masywy Beskidów (sól `mountain.great`, kopuła, miękki pułap), reguły rzek przy masywach | masywy |
| K3 | F1: ciągłe pola doliny dominującej (miękkie maksimum); TE: pas meandrów bez progu nizin | doliny |
| K4 | G1B (gładka oś w węzłach), G2 (amplituda przez węzły), G3 (lejki ujść), G4 (nieregularny skraj dna), G5 (gładkie minimum zboczy), A5 (głowice łukiem), K4.9, K4.11 (koszt); K4b: rzut na dokładnych minimach odległości i cięcie omiatające (D4); K4c: cięcie tylko przy remisach (D4a), kryteria w blokach (D4b) | doliny i zbocza |
| K5 | Starorzecza jako półksiężyce; jeziora rynnowe kończą się przed doliną, z poziomem niezależnym od kolejności; płatowe brzegi oczek i jezior bezodpływowych; niecki bez ściany (A3); poziom oczek z terenu po dolinach (A3c); K5b: wybrzeże wydmowe (D5) i zalew za niskim brzegiem (D2) | wody stojące, wybrzeże |
| K6 | A16: okno mieszania regionów 5 × 5, dokładne i bez szwów; test ścian niecek z całą niecką | szwy regionów |
| K7 | Jedno przegenerowanie `golden_terrain_m2.txt`, usunięcie list, dokumentacja | plik wzorcowy |

### Główne odstępstwa od projektu

- **Cel łat kontrolnych** liczony z odległości od skraju dna i od pasa wybrzeża, a nie przez `streamOrder == 0` (K0).
- **Rzut doliny** na dokładnych minimach odległości i z cięciem omiatającym tylko przy remisach (K4b, K4c), zamiast dokręcania Newtonem (R1). Remisy D4a w czterech oknach GAMEPLAY dają krok doliny do 5,8 m na 1 m, ale najwyżej 1,71 bloku na blok.
- **R5 nie wszedł:** ściany rzutu w REAL do 2,75 bloku na blok w kilku miejscach, wyjątek do M5.
- **D3 nie wszedł** (M5).
- **D5 i D2 wyszły poza tabelę złotego testu**, a niski brzeg zmienia poziomy rzek GAMEPLAY na ok. 20% lądu (D5a).
- **Jeziora rynnowe GAMEPLAY** mają o 67% mniej wody niż w K4c (projekt: −37%). Ściany ich niecek łamią D4b w kilkunastu skupiskach (wyjątek z limitami w teście, decyzja B3).
- **Łaty „=” zmienione ponad próg numeryczny:** `tunnel_valley_lake` w REAL B i REAL A 0,5 (K7, odstępstwo 1).

### Pomiary przed i po

**Skoki powierzchni.** Te same 150 transektów po 1000 m co 0,5 m w każdym oknie testu ciągłości (ziarno 11, świat 20260927), policzone w jednej JVM kopią M1 („przed”) i modelem po K7 („po”), tymi samymi definicjami co `SurfaceContinuityTest`:
- skok: izolowany |Δh| > 0,3 m między sąsiednimi próbkami, przy czterech suchych próbkach;
- krok 1 m: największe |Δh| na 1 m na suchym lądzie poza pasem brzegowym 3 km·meso;
- krok w blokach: to samo przeliczone przez `VerticalScale` okna.

Okna masywów w M1 nie mają masywu.

| Okno | Skoki przed → po | Największy skok (m) | Największy krok 1 m (m) | Największy krok (bloki na blok) | Kroki > 2 bloków na blok |
|---|---|---|---|---|---|
| `gameplay_beskids` | 43 → 0 | 36,38 → — | 36,94 → 2,89 | 10,63 → 0,88 | 22 → 0 |
| `gameplay_center` | 21 → 0 | 20,30 → — | 44,75 → 2,03 | 18,15 → 0,64 | 35 → 0 |
| `gameplay_stream` | 43 → 0 | 13,63 → — | 14,54 → 2,24 | 4,60 → 0,66 | 30 → 0 |
| `gameplay_moraine` | 20 → 0 | 67,30 → — | 67,32 → 1,44 | 29,51 → 0,61 | 154 → 0 |
| `gameplay_tunnel_lake_3519` | 22 → 0 | 85,83 → — | 85,91 → 0,56 | 39,72 → 0,23 | 653 → 0 |
| `gameplay_massif_spawn` | 46 → 0 | 22,31 → — | 23,11 → 5,28 | 7,54 → 1,56 | 44 → 0 |
| `gameplay_massif_1660` | 54 → 1 | 68,12 → 1,14 | 71,23 → 4,58 | 19,22 → 1,12 | 51 → 0 |
| `gameplay_massif_1718` | 59 → 0 | 28,47 → — | 32,79 → 4,84 | 8,87 → 1,13 | 39 → 0 |
| `gameplay_massif_1710` | 51 → 0 | 39,27 → — | 39,36 → 5,02 | 11,10 → 1,11 | 55 → 0 |
| `gameplay_lagoon` | 3 → 7 | 0,98 → 0,93 | 10,41 → 0,71 | 4,35 → 0,53 | 2 → 0 |
| `realistic_beskids` | 1 → 0 | 1,06 → — | 1,33 → 1,34 | 1,33 → 1,34 | 0 → 0 |
| `realistic_lowland` | 2 → 0 | 1,50 → — | 1,50 → 0,62 | 1,50 → 0,62 | 0 → 0 |
| `realistic_moraine` | 16 → 0 | 4,80 → — | 5,38 → 0,27 | 5,38 → 0,27 | 16 → 0 |
| `realistic_stream` | 0 → 0 | — | 1,30 → 1,31 | 1,30 → 1,31 | 0 → 0 |
| `realistic_massif_1723` | 0 → 0 | — | 1,26 → 1,30 | 1,26 → 1,30 | 0 → 0 |
| `realistic_lagoon` | 3 → 2 | 1,50 → 0,99 | 0,22 → 0,22 | 0,22 → 0,22 | 0 → 0 |

Uwagi:
- Przy samych zalewach nie ma skoków (K5). Skoki w oknach zalewów (2 w REAL i 7 w GAMEPLAY, do 0,99 m) to wały ujść rzek podniesione do 1 m obok plaży na poziomie morza, mechanizm M1 (K5, „Nowe okna `SurfaceContinuityTest`”). W `gameplay_lagoon` jest ich więcej niż w M1 (3 → 7), ale to ten sam mechanizm (pomiar K5) i żaden nie przekracza 1 m.
- Jedyny skok w `gameplay_massif_1660` (1,14 m) to zagięcie, gdzie remisują dwa przekroje doliny (spadek 1,5 → 3,7 m na 1 m bez uskoku, (69755, −29217)), a nie stopień; limit testu 1,15 m (K4c, „Gęsty skan okien górskich”).
- Wiersze `gameplay_moraine` i `realistic_moraine` „po” poprawione w rundzie 1 recenzji K7 na wartości z definicji `SurfaceContinuityTest` (pierwsza wersja tabeli liczyła tam także suchy ląd w pasach wód stojących: 3,14 m i 1,34 bloku na blok oraz 0,68).
- W pasie brzegowym ściana klifu ma do 2,40 bloku na blok w `gameplay_center` (M1: 2,45 w płaskim pasie wzdłuż całego brzegu; teraz tylko klif, ok. 1/5 brzegu).
- W oknach masywów GAMEPLAY największy krok to 4,6–5,3 m na 1 m (strome kopuły i zbocza dolin), ale tylko 1,1–1,6 bloku na blok.
- Gęsta siatka 2 m z doczytaniem 1 m (`denseGridHasNoCliffs`, okna górskie GAMEPLAY, pomiar K5) daje najwyżej 1,71 bloku na blok, a krok doliny do 5,78 m na 1 m (remisy D4a). Bez cięcia omiatającego (pomiar K4c na 600 transektach) było tam do 22,5 m na 1 m i do 7,5 bloku na blok.
- Wyjątki do M5: R5 (REAL, do 2,75 bloku na blok w sześciu miejscach `realistic_beskids` i jednym `realistic_massif_1723`), ściany niecek jezior rynnowych GAMEPLAY (±20 km co 20 m: 5 par, do 3 bloków na blok; w oknach 1 m skupisk do 8 bloków na blok) i jeziora bezodpływowe u stóp wielkich masywów REAL: ściany niecek i rzeki na wałach nad nimi (niżej, „Co zostaje”; okna transektów ich nie obejmują).

**Szwy regionów (A16).** W miejscu z projektu (5540, 2725) GAMEPLAY było 1096 punktów ze szwem `landElevation` do 4,95 m, a zostaje 1 punkt (0,50 m). W GAMEPLAY ±20 km znika największy szew (4,83 m).

**Determinizm.** Kolumny różne między kolejnościami próbkowania przy jeziorach rynnowych: 2531 i 12143 (K0, do 1 m) → 0 (K5.2). `TerrainDeterminismTest` jest ścisły.

**Koszt `sample`** (stosunek do kopii M1 w jednej JVM, `costTest -PcostRuns=15`, spokojna maszyna, K6 po rundzie 1; kod modelu w K7 bez zmian). Przed poprawką stosunek wynosił ok. 1,0 (projekt, seria „dziś”: 1,03 / 0,99 / 1,01 / 0,97).

| Obszar | Mediana | Z minimów | µs na kolumnę | Limit D1 |
|---|---|---|---|---|
| REAL cały obszar | 1,163–1,164 | 1,190–1,194 | 4,53–4,58 | 6,5 µs |
| REAL Beskidy | 1,128–1,140 | 1,133–1,140 | 7,16–7,23 | 12 µs |
| REAL wielki masyw | 1,012 | 1,009–1,016 | 6,65–6,67 | — |
| GAMEPLAY cały obszar | 1,189–1,193 | 1,180–1,198 | 6,39–6,41 | 8,5 µs |
| GAMEPLAY Beskidy | 1,159–1,173 | 1,162–1,173 | 11,07–11,18 | 16 µs |
| GAMEPLAY wielki masyw | 1,123–1,141 | 1,123–1,136 | 12,04–12,17 | — |

Budżet D1 (1,20) jest dotrzymany, ale bez zapasu. Najwięcej kosztuje `RiverNetwork.query` (ok. 63% czasu).

**Masywy (M2-8, K2).**
- **Przed:** najwyższe szczyty REAL to 1443 m (ziarno 20260927), 1461 m (ziarno 1) i 1645 m (ziarno 4). W GAMEPLAY w kwadracie 70 km: 1131 m (20260927) i 1247 m (ziarno 4). Kosodrzewina i hala były tylko w REAL dla części ziaren.
- **Po:** REAL ma w oknie 1000 km 9 wielkich masywów o szczytach 1632–1723 m (szczyt − cel od −26 do −2 m), a pole ≥ 1650 m wynosi najwyżej 2,14 km² (stosunek do pasa 1390–1650 m najwyżej 0,27). GAMEPLAY ma w oknie 600 km 25 masywów o szczytach 1618–1719 m (≥ 1650 m najwyżej 0,209 km², stosunek najwyżej 0,34).
- **Piętra** na masywie najbliższym (0, 0) w REAL: kosodrzewina 8,86 km², hala 0,51 km². Na masywie najbliższym spawnu GAMEPLAY: 0,75 km² i 0,010 km². Ziarno 20260927 ma kosodrzewinę w obu skalach.
- Masywy (K2) zmieniają `landElevation` tylko w swoim zasięgu, ale sieć rzeczna przy masywach się przekłada. Poza zasięgiem masywów `landElevation` różni się od M1 jeszcze przez okno mieszania regionów 5 × 5 (A16, K6: w GAMEPLAY do 4,56 m, w kadrze (5540, 2725) zmienia się 96% pikseli, mediana 0,47 m) i przez pas niskiego brzegu (D5, D2: do 25–32 m w pasie brzegowym w obu skalach, np. zalew REAL 3 km 24,9 m, brzeg GAMEPLAY 40 km 31,9 m; pomiar kadrów recenzji K7).

**Wybrzeże (D5, K5b; `CoastSurvey`, przekroje wzdłuż normalnej brzegu):**

| | REAL przed | REAL po | GAMEPLAY przed | GAMEPLAY po |
|---|---|---|---|---|
| klif (ściana > 1 m na 1 m i > 8 m) | 100% | 22,2% | 99,7% | 21,0% |
| brzeg niski | 0% | 77,8% | 0,3% | 79,0% |
| `lowShore` ≥ 0,25 | 12,9% | 81,7% | 16,5% | 82,0% |
| wydma przednia (p50) | — | 12,5 m | — | 12,5 m |

Udziały w podglądzie siedlisk (procent kolumn kadrów REAL / GAMEPLAY, przed = stan K4, po = kadry K7 z rundy 1 recenzji):
- biała wydma 0,000 / 0,087 → 0,016 / 0,326;
- szara wydma 0,000 / 0,072 → 0,014 / 0,352;
- zalew 0,004 / 0,023 → 0,045 / 0,138;
- ściana klifu 0,003 / 0,071 → 0,000 / 0,024.

Klif zajmuje 21–22% brzegu (w oknach 40 km REAL od 0 do 58%, mediana 12%).

**Wody stojące.**
- Starorzecza (siatki testu): REAL 2017 → 2589, GAMEPLAY 518 → 644. Woda OXBOW GAMEPLAY ±8 km: 648 → 1028 kolumn.
- Jeziora rynnowe wobec K4c: GAMEPLAY ±20 km 29338 → 9703 kolumn (−67%), REAL ±60 km 7550 → 5852 (−22,5%).
- Oczka GAMEPLAY ±8 km: −2,2%, torf: −1,4%.
- Ściany jezior bezodpływowych przy masywach REAL: 230 par > 25 m → 0 (1723 m) i 148 → 62 (1659 m; 50 po K5, 62 po rundzie 1 K5, bo trzecia harmoniczna brzegu przesunęła brzeg wzdłuż stromego zbocza). Recenzja K7 znalazła trzy kolejne takie jeziora (niżej, „Co zostaje”).

### Co zostaje (poza poprawką)

- **Decyzja użytkownika B3:** ściany niecek i woda jezior rynnowych GAMEPLAY (warianty a–d w K6). Wariant d usunąłby też suche zagłębienia przy końcach jezior i pozwolił wrócić do `TUNNEL_COS` 0,6. Każdy wariant, który zmienia teren, wymaga przegenerowania pliku wzorcowego z `-PgoldenKeepCenters` i sprawdzenia `diff`.
- **M5:**
  - R5 i remisy D4a (rzut na samą oś doliny);
  - D3 (wały moren W–E z przełomami);
  - szczelina rzutu w (74489,5; 89655,02) REAL;
  - ściana niecki jeziora bezodpływowego pod masywem 1659 m;
  - **jeziora bezodpływowe u stóp wielkich masywów REAL** (recenzja K7, stan po K7; mechanizm M1, który głębokie niecki masywów K2 wzmacniają): rzeka, która przecina pas brzegowy jeziora, zostaje na poziomie swojego węzła, a niecka obniża grunt po obu stronach koryta, więc rzeka biegnie po wale szerokości ok. 3 m i spada prosto do jeziora. Masyw 1719 m (jezioro na poziomie 663 m, (263300, −1536950)): siatka 5 m 598 par > 25 m, najwięcej 38,2 m; rzeka na 687–688 m obok suchego gruntu 664 m po obu stronach i 25 m spadku do jeziora. Masyw 1698 m (jezioro 642 m): brzeg na 643 m ok. 30 m od dna doliny rzędu 3 na 387–390 m (profil x = 146728: 640,7 m przy z = −1472860, 405,6 m przy z = −1472840, do 15,3 m na 1 m), siatka 5 m 548 par, najwięcej 75,6 m; woda 228 m głęboka trzymana przez obrzeże szerokości ok. 8 m (ścianę jeziora miał tu już M1, 9,3 m na 1 m, ale płatowy brzeg K5.5 przesunął jezioro dalej w zbocze doliny). Jezioro (213514, −1519439): 20 par, najwięcej 27,9 m. Rzeki nad gruntem i spadki rzeka–jezioro (siatki 1 m): 1719 m 24 / 25 m, 1698 m 15 / 16 m, (213514, −1519439) 16 / 17 m, 1723 m 10 / 11 m, 1659 m 18 / 19 m; zwykłe jezioro bezodpływowe w Beskidach (157239, 1059020) 4 / 5 m (w M1 tam 4–7 m). Limity stanu zmierzonego pilnują `SurfaceContinuityTest.massifSpotsHaveNoCliffs` (trzy nowe miejsca) i `MassifSinkLakeContainmentTest`. Naprawa w modelu: obniżyć koryto razem z niecką w jej zasięgu albo kończyć rzekę na obrzeżu niecki stopniowaną kaskadą; brzeg jeziora trzymać z dala od niższego dna sąsiedniej doliny;
  - **starorzecze przy dopływie** (K5.1, wygaszanie szerokości F1 przy dolinie dopływu): w kadrze §3.4 `large_river_valley_2km` (REAL) przy (−5440, 93770) lewe starorzecze ma prawie prosty brzeg ok. 220 m równoległy do dopływu, a prawe górne kończy się ostrym wklęsłym wcięciem na tej samej linii; `StandingWaterTest.oxbowLakesAreCrescents` bierze tylko 25 pierwszych starorzeczy i tego nie łapie. Możliwa poprawka: dłuższy margines wygaszania albo skracanie łuku zamiast zwężania; do próby dołożyć starorzecza przy zbiegach;
  - **załamania stoku na masywie GAMEPLAY 1718 m** (recenzja K7): w samym `landElevation` proste załamanie N–S przy x ≈ −217939 (z 246280–246500, nachylenie skacze z 1,7 do 3,1 m na 1 m) i prawie proste W–E przy z ≈ 246252–246266 (x −218000…−217700) tworzą prostokątny narożnik widoczny w cieniowaniu; drugie proste załamanie W–E ponad 600 m przy z ≈ 247198–247206 pochodzi częściowo z rzeźby Beskidów M1, ale kopuła je wydłuża. To nie urwiska (najwyżej ok. 0,73 bloku na blok); prawdopodobnie twarde maksimum kopuły i pola bazowego, poprawka: gładkie maksimum (jak F1 dla pól dolin), M5 albo strojenie S2/S3;
  - kontrole kadrów §3.4 niespełnione od K4 (Do A8/G5 i S8): `R_potok_3km`, wachlarz przy (570, 100) px stał się prostymi załamaniami ok. 400 m (przybliżenie 600 m przy (72845, 1008385): proste załamanie W–E i załamania N–S, na których łamią się poziomice); `G_rzeka_1km`, „równoległobok” przy (445–610, 125–350) px dalej z prostymi jasnymi załamaniami między ścianami. Żadne nie jest urwiskiem (0 par > 2 bloków na blok), ale to ta sama klasa „prostych krawędzi”, którą zgłosił użytkownik;
  - końce wydm przy dnach dolin;
  - koniec zalewu wzdłuż brzegu;
  - grobla oczek do 11 m.
- **Cel `find cliff` po D5** (gametest `views`, runda 1 recenzji K7): `PolishForestsCommands.Target.CLIFF` szuka formy CLIFF, którą ma też wysoka wydma przednia na niskim brzegu, więc komenda i zrzut `cliff` trafiają na niski brzeg. Poprawka: warunek `lowShore < Calibration.LOW_SHORE` w celu (zmienia komendę, do zrobienia z najbliższą zmianą komend lub gametestów).
- **Etap H (poprawki klasyfikatora siedlisk):** zrobiony 2026-10-07 (niżej, „Etap H”).
- **Koszt:** kolejne kroki, które dokładają koszt `sample`, muszą najpierw szukać oszczędności (etap H dołożył pamięć węzłów listy kandydatów kafla bez pudełkowanych kluczy; dalsze możliwości: pozostałe mapy `ConcurrentHashMap<Long, …>` w `RiverNetwork`, np. `link` i `area` przy budowie odcinków).

## Etap H: poprawki klasyfikatora siedlisk po poprawce geometrii (2026-10-07)

Baza: commit `49aca9c` (drzewo czyste). Teren bez zmian: złoty test (`golden_terrain_m2.txt`) przechodzi bez listy dozwolonych zmian. Opis zmian, pomiary i to, co zostaje: `docs/03-m2-biomy.md` §3.4, „Stan po poprawce geometrii”; kadry: `docs/m2/podglad-siedlisk-s4.md`.

- **G3 (płaty łęgów przy zbiegach):** nowe pole `ColumnSample.Waters.softChannelLevel` (miękki poziom koryt z bufora kandydatów F2 w `RiverNetwork.query`, stałe `SOFT_LEVEL_*`); klasyfikator mierzy od niego wysokość nad wodą, a na fladze dna (z lejkiem) dno sięga do 4 m nad wodą (`Calibration.BANK_H`). Kadr `Z_besk_conf_300m` (w podglądzie `gameplay_beskids_confluence_300m`): bez wieloboków, ols 0,28% → 0,10%. `channelLevel` zostaje polem najbliższego koryta (teren i oczka go używają).
- **Strefy tylko przy prawdziwej wodzie, drganie progów żyzności (Z9), klasa C bez prostych ucięć, wydmy przez dna dolin na brzegu wydmowym (D5):** `docs/03-m2-biomy.md`. Brzeg wydmowy w klasyfikacji: REAL 77,8%, GAMEPLAY 79,7% punktów brzegu (przed: 64,5% i 63,4%).
- **Nowe testy:** `WatersideZonesTest.noWatercourseZonesAlongDryChannels`, `WatersideZonesTest.softChannelLevelIsContinuousAtConfluences`, `CoastTest.duneShoreShareAtBothScales`; `HabitatClassifierTest.riparianOnlyOnFloorsSpringsAndSeeps` mierzy wysokość nad wodą od niższego z poziomów (najbliższe koryto, miękki poziom).
- **Koszt `sample`:** miękki poziom koryta kosztował ok. 1% w GAMEPLAY. `costTest -PcostRuns=15`, po dwa pomiary A/B w jednej sesji (baza przez `git stash`): GAMEPLAY cały obszar 1,197 i 1,193 (baza) wobec 1,203 i 1,216 (etap H; z minimów 1,195/1,191 wobec 1,200/1,202), REAL cały obszar 1,188/1,163 wobec 1,190/1,170, Beskidy i masywy bez zmian (1,13–1,17 i 1,01–1,14), czyli budżet D1 (1,20) w GAMEPLAY przekroczony o ok. 1–2%. Runda 1 recenzji, zgodnie z D1 (najpierw oszczędności): segment wylotowy i jezioro bezodpływowe węzła siatki w jednej bezblokadowej pamięci `DirectCache` w `RiverNetwork.candidates` (2^18 miejsc, klucz (i, 4·j + rząd) bez pudełkowania) zamiast 3–4 odczytów map z kluczami `Long` na każdy z ok. 500–675 węzłów listy kandydatów kafla; wynik się nie zmienia (złoty test bez zmian). A/B w jednej sesji: bez oszczędności GAMEPLAY cały obszar 1,198 i 1,214, REAL cały obszar 1,172 i 1,197; z nią GAMEPLAY 1,179 i 1,186 (Beskidy 1,16, wielki masyw 1,13), REAL 1,107 i 1,096 (Beskidy 1,10–1,11, wielki masyw 0,98). Budżet D1 jest spełniony. Przy 16 384 miejscach 400 rozrzuconych chunków testu REAL nie mieściło się w pamięci i REAL był o ok. 1% wolniejszy, więc spadek REAL jest częściowo cechą pomiaru.
- **Runda 1 recenzji (2026-10-07):** wydmy przez dna tylko w pasie wydm z drgającym końcem (`GRAY_DUNE_JITTER`), bez rozszerzenia na zaplecze i bez progów zaplecza zalewu na dnach (proste cięcia na bramce `wCoastland` 0,5 i na zacisku brzegu 2,000 m); drganie granicy DGW buczyny i progu mady w krainach piasków; próg wariantu buczyny drga najwyżej o połowę udziału; miękki poziom koryt pomija suche kandydaty ponad 4 m pod gruntem (`SOFT_LEVEL_DRY_*`, test `realChannelBanksStayByWater`); `SEEP_HL` = `BANK_H`; odporny test czasu klasyfikacji. Szczegóły w `docs/03-m2-biomy.md` §3.4.

## Test w grze po poprawce geometrii (2026-10-07)

Baza: `02cb497` (etap H); teren bez zmian, zmienia się tylko `PolandWorldClientGameTest` (zrzuty `views`).

**Polecenie:** `./gradlew runClientGameTest -Pgametest=views -Psites=great_massif,meanders,oxbow_lake,beach,coastal_dunes,cliff,lagoon,headwaters` (świat REAL, ziarno 20260927, zasięg widzenia 10 chunków, większy tylko tam, gdzie miejsce o to prosi). Wynik: BUILD SUCCESSFUL w 10 min 22 s, 8 zrzutów, bez wyjątków z gry i modu (w logu tylko komunikaty środowiska: OSHI nie czyta liczników wydajności polskiego Windowsa przy `CrashReport.preload`, Realms bez autoryzacji w środowisku deweloperskim). Model 154 ms na 64 chunki, generacja 48 ms na chunk. Trzy wcześniejsze przebiegi tej samej komendy (BUILD SUCCESSFUL, 9 min 18 s – 9 min 44 s) posłużyły do ustawienia kamer (niżej).

**Zmiany w teście:**
- nowe miejsce `great_massif`: szczyt masywu najbliższego (0, 0) (`LandscapeModel.nearestGreatMassif`; środek (98290, 1033389), cel 1659 m, szczyt szukany siatkami 100 / 20 / 4 m: (98062, 1033937), 1654 m). Kamera stoi 360 m od szczytu na najbardziej stromym z 16 stoków (tu zachodnim, spadek 122 m między 180 a 360 m), 12 bloków nad poziomem szczytu i patrzy 6° poniżej niego, przy zasięgu widzenia 28 chunków. Z niższej kamery (pierwsze dwa przebiegi: 20 i 40 bloków nad gruntem 400 m od szczytu) wypukłe ramię kopuły i korony świerków zasłaniały szczyt;
- zmiana zasięgu widzenia w trakcie gry wymaga `Options.broadcastOptions()`: serwer ogranicza widok gracza do zasięgu z informacji klienta, więc bez tego klient dostał 473 chunki zamiast ok. 2000;
- miejsca brzegowe (`beach`, `coastal_dunes`, `cliff`): kamera stoi 40 m za linią brzegu (`coastDistance` celu + 40 m w stronę morza), a zasięg widzenia rośnie tak, żeby cel był 80 bloków przed mgłą (13, 23 i 13 chunków). Dotąd kamera stała 80 m od celu, a cel `coastal_dunes` leży ok. 235 m od brzegu, więc kamera wisiała nad tylnym stokiem wydmy przedniej i patrzyła w głąb lądu; „łagodny garb z owalnymi tarasami” z rundy 1 recenzji K7 to garb na zapleczu, a nie wydma przednia.

**Zrzuty** (`docs/m2/gra/`):
- `great_massif_summit_dome.png`: zachodni stok masywu wznosi się tarasami po bloku do płaskiej kopuły szczytowej, cały porośnięty świerkami, bez urwisk; kosodrzewiny i hali w grze jeszcze nie ma (powyżej 1150 m stoi zastępczy biom świerczyny, biomy siedlisk dopiero w fazie 2 M2).
- `oxbow_lake.png`: wschodnie ramię starorzecza (kamera (−4407, 2171)) ma prawie prosty zewnętrzny brzeg ok. 90 m z wałem 1 m wzdłuż niego (w modelu łagodnie zakrzywiony koniec półksiężyca, który urywa się ok. 50 m przed korytem małego cieku), a dno doliny schodzi prostymi stopniami po bloku.
- `headwaters.png`: dolinka źródłowa przy (−67, −1) z potokiem na płaskim dnie i stokami w tarasach; dno opada stopniami po bloku, których krawędzie biegną prostymi odcinkami (poziomice prawie płaskiego dna), bez ścian.
- `coastal_dunes_from_sea.png`: widok z morza na brzeg wydmowy: szeroka plaża wznosi się tarasami po bloku do grzbietu wydmy przedniej (ok. 12 m), za nim łąki zaplecza.
- `cliff_target_low_shore.png`: cel `cliff` nadal trafia na niski brzeg: plaża, gęste stopnie piasku na czole wydmy przedniej (ok. 8 m) i łąka za nią, klifu na zrzucie nie ma.
- `lagoon.png`: kamera stoi nad wewnętrznym brzegiem zalewu (w modelu zalew ma tu ok. 1,3 km szerokości i do 3,9 m głębokości, mierzeja ok. 450 m szerokości leży poza kadrem), który biegnie prostą linią aż do mgły, a po lewej wpada do niego meandrująca rzeka z płatami piasku na brzegach.

Zrzutów `meanders` (sam brzeg szerokiej rzeki i łąka z małym owalnym zagłębieniem 1 m, bez zakola w kadrze) i `beach` (piaszczysta plaża tego samego brzegu wydmowego 320 m od `coastal_dunes`) nie kopiowano.

**Błędy geometrii:** poważnych nie ma: w 8 kadrach żadnych urwisk, ścian ani rowów; stoki, dna i brzegi to tarasy po bloku. Otwarte drobne (bez naprawy w tym kroku):
- cel `find cliff` (`PolishForestsCommands.Target.CLIFF`) trafia na wysoką wydmę przednią niskiego brzegu; poprawka bez zmian (warunek `lowShore < Calibration.LOW_SHORE`), zmienia komendę;
- kopuła wielkiego masywu jest bardzo płaska (w promieniu 180 m od szczytu tylko 5–7 m, czyli ok. 4 bloki niżej), więc z boku wygląda jak płaskowyż; to skutek miękkiego sufitu K2 (`greatMassifCeiling`: wszystko powyżej celu − `GM_CAP`, tu 1624 m, ściśnięte w ostatnie 35 m), do oceny razem z biomami piętra halnego;
- wewnętrzny brzeg zalewu (ok. 1,76 km od analitycznej linii brzegu) jest prawie prosty: w modelu na 1,2 km odchyla się od prostej o ok. 5 m; do oceny razem ze znanymi „końcami zalewów” (M5);
- prawie prosty odcinek brzegu starorzecza z wałem 1 m; czy to wygaszanie szerokości F1 przy dolinie dopływu (K5.1, „starorzecze przy dopływie”, M5), niesprawdzone.


## K8. Krótka poprawka po teście w grze (2026-10-07)

Decyzja użytkownika z 2026-10-07 („krótka poprawka teraz”, przed fazą 2 M2): K8a — jeziora rynnowe GAMEPLAY (wariant d z K6), K8b — wybrzeże (wewnętrzny brzeg zalewu, wydma przednia, `find cliff`), K8c — starorzecza (prosty brzeg z wałem przy końcu półksiężyca), K8z — jedno przegenerowanie `golden_terrain_m2.txt`. Zmiany terenu w K8a–K8c idą przez narastające listy `src/test/golden-allow/K8a.txt`, `K8b.txt`, `K8c.txt` (domyślnie ostatnia według nazwy, `build.gradle`).

### K8a. Jeziora rynnowe GAMEPLAY: wariant d (koniec przy dolinie od gładkiej szczeliny)

Baza: `0da46a3` (drzewo czyste). Prototyp na kopii modelu w katalogu roboczym poza repozytorium (narzędzia: skan ścian zrobionych przez model, okna 1 m `WALL_CLUSTERS`, skan suchych zamkniętych zagłębień, liczniki wody i oczek), potem kod w projekcie.

**Przyczyny (potwierdzone na prototypie).**
- **Koniec jeziora przy dolinie na stromym gruncie** (4 z 5 skupisk ścian K6): szczelina za wycięciem doliny w samej kolumnie (`RiverHit.floorGap` = floorDist − terrainHalf − 0,5 · ściana, ściana = (teren − dno)/`maxSlope`) zmienia się o 4–6 m na metr, bo ściana rośnie z wysokością terenu nad dnem. Przykład (−20545, 10800…11060): teren surowy 80 → 155 m na 40 m, `floorGap` 162 → −10 m na 40 m. Gładkie pole `CoarseTerrainField` (`sBar`, okno 3 × 3 węzły po 16 m w GAMEPLAY) nie pomaga: `sBar` idzie tu za terenem surowym (11 m na 10 m wobec 15 m na 10 m), więc połowa ściany „z wysokości gładkiego pola” zmienia się prawie tak samo.
- **Soczewka liczona w kolumnie przy dolinie z boku:** koniec soczewki (`ellipticEnd`) szedł za szczeliną w kolumnie także w poprzek jeziora. Przy dolinie biegnącej wzdłuż jeziora woda kurczyła się od strony doliny, a po drugiej stronie odległość od brzegu miała minimum > 0 z dala od osi: niecka bez wody, czyli suche zamknięte zagłębienie (np. (16960, −690): 60 m głębokości, (−16240, 6890): 65 m). Tak samo przy nakładaniu się końca śladu i końca przy dolinie.
- **Skraj obszaru młodoglacjalnego** ((10351, 11900)): połowa szerokości z obecności w kolumnie (waga typu, w GAMEPLAY 0 → 1 na ok. 300 m), a wygaszenie wąskiego jeziora (`shapeFade`, 25–60 m·k) dodawało cały zasięg niecki na kilkunastu metrach.

**Zmiana (`LandscapeModel.tunnelShape`, `tunnelGaps`, `tunnelLake`; `RiverNetwork`).**
- **Siatka szczelin konturu (`TunnelGaps`, raz na kontur, w pamięci podręcznej):** w każdym węźle śladu (co 20 m·k wzdłuż z) i co 20 m·k w poprzek konturu (do 1,2 połowy szerokości jeziora w węźle + zasięg niecki) zapytanie sieci rzecznej na gruncie sprzed dolin, jak w `sample`: `floorGap`, nowe pole `RiverHit.floorEdgeGap` (szczelina za brzegiem dna, bez ściany: zmienia się o ok. 1 m na metr) i to, czy próbka leży w strefie młodoglacjalnej w szerokim sensie (waga typów młodoglacjalnych > 0, szum rynien > 0, ląd). Z tego:
  - **gładka szczelina:** połowa drogi między dolną a górną obwiednią o nachyleniu 1 (metryka L1: z wzdłuż śladu + odległość w poprzek) szczeliny przyciętej do [koniec przy dolinie − zasięg − 10 m·k; koniec przy dolinie + `TUNNEL_END_MAX`]; tam, gdzie szczelina zmienia się najwyżej o 1 m na metr, równa samej szczelinie (`TUNNEL_GAP_LOW`, `TUNNEL_GAP_MIX` 0,5);
  - **koniec soczewki przy dolinie** z największej gładkiej szczeliny w przekroju jeziora (1,2 połowy szerokości węzła), więc jezioro przy dolinie z boku zachowuje wodę po drugiej stronie, a koniec na osi pozostaje zaokrąglony (`ellipticEnd`);
  - **ograniczenie od doliny z boku i od skraju strefy:** odległość od brzegu ≥ max(koniec przy dolinie − gładka szczelina; zasięg − odległość od próbek spoza strefy), oba człony zmieniają się najwyżej o ok. 1 m na metr;
  - **wypełnienie zagłębień:** odległość od brzegu w każdej próbce (kształt z `tunnelShape` w węźle z powyższymi ograniczeniami) wypełniona zalewaniem z priorytetem od próbek z wodą; zamknięte zagłębienie odległości od brzegu (sucha niecka między doliną a zboczem, za końcem wody, między jeziorami) podnosi się do swojego progu, a jezioro bez wody nie ma niecki. Kolumna dostaje to podniesienie (interpolacja dwuliniowa). Pierwsza wersja wypełniała tylko wzdłuż osi (najmniejsza odległość przekroju) i zostawiała zagłębienie po drugiej stronie doliny biegnącej osią jeziora ((7240, 11460), 14,5 m).
- **Strażnik dna doliny** (kolumna): odległość od brzegu ≥ zasięg niecki − `floorEdgeGap`, więc niecka nigdy nie sięga dna doliny (ograniczenie z rundy 2 K5: niecka nie obniża dna poniżej wody rzeki). Zastępuje dawny warunek „koniec jeziora pół ściany za wycięciem”, który w kolumnie był stromy.
- **Połowa szerokości z osi jeziora** (`TunnelLake`: połowa szerokości, bramka i odległość wzdłuż osi do najbliższego wąskiego węzła, interpolowane między węzłami): obecność liczona na osi, nie w kolumnie. Wąskie końce (połowa szerokości < `TUNNEL_NARROW` 40 m·k) działają jak koniec śladu: połowa szerokości ograniczona odległością od wąskiego węzła, wygaszenie na 1,5 zasięgu niecki (zamiast `shapeFade` w kolumnie). `sample` liczy jezioro w kolumnach z wagą typów młodoglacjalnych > 0 (dawniej obecność > 0,05); próbki spoza strefy w siatce dają odległość od brzegu ≥ zasięg + 20 m·k, więc odcięcie kolumn spoza strefy nie robi skoku (w GAMEPLAY waga typu spada z 0,2 do 0 na co najmniej ok. 470 m, w REAL na ok. 20 km).
- **`TUNNEL_COS` 0,78 → 0,6** (ok. 53° od N–S), jak w pierwszej wersji K6: suche zagłębienia, przez które K6 cofnął próg, nie wracają (pomiary niżej).
- Dno jeziora (głębokość) idzie za bramką osi, nie kolumny. `RiverHit.floorEdgeGap`: nowe pole, liczone w tej samej pętli co `floorGap` (bez kosztu).

**Pomiary przed (baza `0da46a3`) i po (K8a).** Narzędzia jak w K6 (pary suchych sąsiadów 1 m > 2 bloków na blok zrobione przez model, czyli przy parze terenu surowego ≤ 2 bloki, w pierścieniu albo w niecce).

| Pomiar | Przed | Po |
|---|---|---|
| Ściany niecek, skan GAMEPLAY ±40 km co 20 m | 32 pary w 16 skupiskach, do 8 bloków na blok | **0** |
| Ściany, test GAMEPLAY ±20 km co 20 m (`WALLS_MAX`) | 5 par, do 3 bloków | **0** (limit 0) |
| Ściany, 12 okien 1 m `WALL_CLUSTERS` | 3397 / 1541 / 1250 / 386 / 232 / 3183 / 961 / 930 / 597 / 201 / 247 / 267 par, do 8 bloków | **0 we wszystkich** (limity 0) |
| Ściany REAL: skan ±40 km co 20 m wokół wysoczyzny, test ±20 km co 40 m | 0 | 0 |
| Suche zamknięte zagłębienia przy nieckach > 3 m ponad zagłębienie terenu surowego, GAMEPLAY ±20 km co 10 m | 20, do 60 m (największe: (16960, −690) 60 m, (−16240, 6890) 50 m, (−16210, 6560) 38 m, (18440, 1990) 34 m) | 3 (patrz niżej) |
| To samo, REAL ±30 km co 20 m wokół wysoczyzny | 1 ((−60055, 50053), 5,2 m) | 0 |

Trzy miejsca na siatce 10 m po K8a: (−18450, 14540), 12 m — zagłębienie terenu po dolinach (dno bocznej doliny), niecka go nie obniża (powierzchnia ta sama co przed K8a; przed K8a niecka innego jeziora otwierała mu odpływ); (18020, −5560), 6 m — złudzenie siatki 10 m (bruzda w załamaniu ograniczenia od doliny, na siatce 2 m odpływa); (−16300, −9520), 3,3 m — przy brzegu jeziora wał (lustro + 1 m) zamyka zagłębienie zbocza doliny. Test `StandingWaterContainmentTest.tunnelValleyBasinsHaveNoClosedPits` (okna 500 m co 2,5 m wokół wszystkich 28 miejsc sprzed K8a i prototypów, zagłębienie liczone tylko tam, gdzie niecka obniża grunt o > 0,5 m): jedno zagłębienie 3,7 m w (−15998, 5693) (ten sam mechanizm co (−16300, −9520)); limit testu `PIT_MAX` 4 m. Cel „0 zagłębień > 3 m” nie jest więc spełniony w pełni (odstępstwo 4).

Woda jezior rynnowych (kolumny wody; K4c i K6 z tabeli K6, K7 = K6):

| Obszar | K4c | K6/K7 | K8a z `TUNNEL_COS` 0,78 | K8a (0,6) | K8a wobec K7 | K8a wobec K4c |
|---|---|---|---|---|---|---|
| GAMEPLAY ±20 km co 20 m | 29338 | 9703 | 11240 | 12600 | +30% | −57% |
| GAMEPLAY ±40 km co 25 m | 58890 | 19825 | 23662 | 26943 | +36% | −54% |
| REAL ±60 km wokół wysoczyzny co 75 m | 7550 | 5852 | 6349 | 6817 | +16% | −10% |

Ubytek wobec K4c zmalał z −67% do −57% (GAMEPLAY), ale celu „w stronę −37%” nie osiągnął: największa część ubytku pochodzi z K5.2 i rundy 1 K5 (koniec przed doliną, kontury śledzone przez środek sekcji, próg ukośnego konturu), których K8a nie zmienia. Z wariantem 0,78 wody było mniej, a ścian i zagłębień tyle samo (0 ścian, 6 zagłębień > 3 m na siatce 10 m wobec 3 z 0,6), więc próg wrócił do 0,6.

Oczka (`kettleTouchesTunnelLake` idzie teraz za połową szerokości z osi jeziora): GAMEPLAY ±8 km co 10 m woda 5376 → 5376 kolumn, torf 3442 → 3376, 203 → 202 oczka; ±20 km co 10 m woda 39069 → 38728 (−0,9%), torf 26621 → 26244, 1567 → 1558 oczek; REAL ±40 km co 40 m woda 16256 → 16214, 2349 → 2346 oczek. Szczelność: `StandingWaterContainmentTest` (wszystkie okna GAMEPLAY i REAL, w tym oczka przy jeziorach rynnowych i niecka przy dolinie (17620, −1819)): 0 przecieków; `tunnelLakesHaveOneLevel`: 0 błędów (GAMEPLAY 33 jeziora, REAL 26).

`LandscapeModelTest.tunnelLakesEndBeforeValleys`: najmniejsza szczelina za brzegiem dna przy wodzie REAL 140,0 m (limit `tunnelBank` 140 m), GAMEPLAY 70,1 m (limit 70 m); najmniejsza szczelina za wycięciem (`floorGap`) REAL 27,4 m, GAMEPLAY 27,1 m (w K5 103 i 50,9 m przy limicie TUNNEL_END_GAP + 0,5 `tunnelBank`, teraz limit 0: woda za środkiem ściany, odstępstwo 2).

**Koszt.** `costTest -PcostRuns=15`, A/B w jednej sesji (baza przez `git stash` kodu i testów), stosunek do kopii M1, mediana / z minimów:

| Obszar | Baza | K8a przebieg 1 | K8a przebieg 2 |
|---|---|---|---|
| REAL cały obszar | 1,061 / 1,061 | 1,087 / 1,091 | 1,074 / 1,102 |
| REAL Beskidy | 1,080 / 1,080 | 1,105 / 1,094 | 1,060 / 1,071 |
| REAL wielki masyw | 0,974 / 0,980 | 0,974 / 0,977 | 0,966 / 0,961 |
| GAMEPLAY cały obszar | 1,165 / 1,172 | 1,195 / 1,178 | 1,174 / 1,199 |
| GAMEPLAY Beskidy | 1,136 / 1,167 | 1,157 / 1,162 | 1,164 / 1,157 |
| GAMEPLAY wielki masyw | 1,124 / 1,123 | 1,130 / 1,136 | 1,122 / 1,122 |

µs na kolumnę (mediana): REAL 4,41–4,42 / 7,17–7,39 / 6,65–6,66, GAMEPLAY 6,54–6,69 / 11,36–11,38 / 12,73–12,82 (limity bezwzględne D1 dotrzymane). Budżet D1 (1,20) jest dotrzymany, ale bez zapasu w GAMEPLAY (ok. +1–2% wobec bazy: kolumny z wagą typów młodoglacjalnych > 0 sprawdzają kontur, kolumny jezior czytają siatkę). Ciepły koszt kolumny w pomiarze własnym (jeden wątek, okno 4 km z jeziorami) bez zmian w granicach rozrzutu (6,29–6,38 wobec 6,27–6,36 µs). Jednorazowo na kontur: siatka szczelin ok. 0,26 s na jednym wątku w GAMEPLAY (ok. 36 tys. zapytań; 6 konturów w oknie 4 × 4 km: 1,56 s), w REAL podobnie; pierwsza runda `costTest` REAL ma 72,9 µs na kolumnę (M1: 28,2), dalsze rundy jak wyżej. W grze to jednorazowy koszt przy pierwszym chunku przy jeziorze (siatki w pamięci podręcznej, 256 konturów, ok. 0,3 MB każda). Kolumna, której odległość od brzegu bez członów siatki (one ją tylko podnoszą) już przekracza zasięg pierścienia, nie buduje siatki (`tunnelShape`): rozproszone zapytania, np. wyszukiwanie komend, nie liczą siatki dla każdego mijanego jeziora; wynik bez zmian.

**Złoty test.** Raport `-PgoldenReport=build/golden_K8a.txt`: zmienione łaty REAL B `grid`, `tunnel_valley_lake` (dno jeziora: głębokość idzie za bramką osi; poziom liczony jak dotąd), `outwash_plain_lake` (sięga tam niecka jeziora rynnowego), GAMEPLAY A `grid`, GAMEPLAY B `grid`; żadna łata kontrolna `*_interior` ani cel się nie zmieniają. Lista `src/test/golden-allow/K8a.txt` (domyślna, więc zwykły `test` przechodzi; `build.gradle` opisuje listy K8a–K8c), narastająca do K8c, przegenerowanie w K8z.

**Testy (nowe i zmienione):**
- `StandingWaterContainmentTest`: `WALLS_MAX` 5 → 0, `WALL_CLUSTERS` z limitami 0 (strażnik powrotu ścian); nowy `tunnelValleyBasinsHaveNoClosedPits` (`PIT_SITES`, `PIT_MAX` 4 m, `PIT_CUT` 0,5 m);
- `LandscapeModelTest.tunnelLakesEndBeforeValleys`: woda za środkiem ściany (`floorGap` ≥ 0) i co najmniej `tunnelBank` za brzegiem dna (`floorEdgeGap`) zamiast `floorGap` ≥ TUNNEL_END_GAP + 0,5 `tunnelBank`.

**Pełny `test`** (`tools/dev/run-tests test`): PASS (860 s; drzewo `4c0920730c0e`, potem tylko ten wpis w dokumentacji).

**Odstępstwa od zadania K8a:**
1. Gładka szczelina nie z `CoarseTerrainField`, tylko z siatki szczelin liczonej na konturze (obwiednie o nachyleniu 1): `sBar` idzie za stromym terenem i nie daje nachylenia ≤ 1 (pomiar wyżej). Siatka łączy oba warianty z zadania („połowa ściany z gładkiego pola” i „szczelina na osi jeziora”).
2. Warunek z rundy 2 K5 („woda co najmniej TUNNEL_END_GAP + 0,5 `tunnelBank` za środkiem ściany w kolumnie”) zastąpiony strażnikiem dna: niecka nie sięga dna doliny (`floorEdgeGap` ≥ zasięg niecki), woda za środkiem ściany. Na stromym gruncie woda podchodzi bliżej doliny niż w K5–K7 (najmniej 27 m za środkiem ściany wobec 51–103 m); szczelność bez zmian (0 przecieków).
3. Obecność na osi jeziora weszła (koszt mieści się w D1, ale bez zapasu w GAMEPLAY); wczesne odrzucenie kolumn w `sample` jest szersze (waga typów młodoglacjalnych > 0 zamiast obecności > 0,05), a skraj strefy ogranicza jezioro członem odległości od próbek spoza strefy.
4. Suche zagłębienia: zostało jedno 3,7 m (okno 2,5 m) i jedno 3,3 m (siatka 2 m) przy wale brzegu przy zboczu doliny; limit testu 4 m zamiast 3 m.
5. Woda: −57% (GAMEPLAY ±20 km) wobec K4c zamiast „w stronę −37%” (+30% wobec K7).
6. Pamięć jezior rynnowych `tunnelLakes` czyszczona przy 4096 wpisach (dawniej 100 000), bo wpis ma teraz tablice węzłów.

**Co zostaje:**
- Ubytek wody jezior rynnowych wobec K4c (−54…−57% w GAMEPLAY) pochodzi z K5.2 i rundy 1 K5 (koniec przed doliną, kontury przez środek sekcji); dalsze odzyskanie wymagałoby np. przesunięcia `valleyEnd` bliżej doliny (dziś zasięg niecki − 0,5 `tunnelBank` + TUNNEL_END_GAP) przy strażniku dna — do oceny razem z kadrami w K8z albo M5.
- Dwa płytkie zagłębienia (3,3–3,7 m) przy wale brzegu na zboczu doliny; zagłębienie (−18450, 14540) 12 m w dnie bocznej doliny (teren po dolinach, nie niecka) — do przeglądu sieci rzecznej w M5.
- Koszt: GAMEPLAY cały obszar 1,17–1,20 × M1 — kolejne kroki zwiększające koszt `sample` muszą najpierw szukać oszczędności (np. `RiverNetwork.link` bez pudełkowanych kluczy, ok. 2,4%). Jednorazowy koszt siatki szczelin (ok. 0,26 s na kontur) można zmniejszyć rzadszymi próbkami w REAL.

### Runda 1 poprawek po recenzji K8a

Recenzja K8a (2026-10-07): jeden problem blokujący, dwa poważne i dziewięć drobnych. Narzędzia jak w K8a (skany ścian i zagłębień, liczniki wody i oczek) oraz kadry recenzenta 1 m (załamania według fazy z mod 10, porównanie z `af65015`), uruchamiane na kopii drzewa roboczego poza repozytorium. Liczby „po” dotyczą kodu z repozytorium, nie prototypu.

**Problem blokujący: proste krawędzie wzdłuż wierszy siatki szczelin (potwierdzony).** Interpolacja dwuliniowa siatki (`bound`, wypełnienie zagłębień, `end`) i tablic węzłów jeziora (połowa szerokości, bramka) miała załamanie w każdym wierszu węzłów (co 20 m·k, w GAMEPLAY przy z ≡ 5 mod 10) i w każdej kolumnie siatki. Obwiednie w metryce L1 dawały na przecięciach prostokątne „pudełka”. Zmiany w `LandscapeModel`:
- siatkę i tablice węzłów czyta kwadratowy B-splajn (`spline`, `TunnelGaps.grid`). Nachylenie jest ciągłe i nie większe niż największa różnica sąsiednich węzłów, więc warunek „najwyżej 1 m na metr” zostaje. Wartość mieści się w zakresie trzech najbliższych węzłów;
- obwiednie o nachyleniu 1 liczy transformata fazowa (chamfer) z maską 5 × 5 (16 kierunków) w poziomej odległości między próbkami (x każdej próbki, wiersze co dz). Zastępuje rozdzielną metrykę L1 siatki. Poziomice są prawie okrągłe (błąd ok. 3%) i także przy ukośnym konturze idą za rzeczywistą odległością;
- człony odległości od brzegu (soczewka, ograniczenie od doliny i od skraju strefy, strażnik dna) łączy gładkie maksimum `smoothMax` o promieniu `TUNNEL_ROUND` 30 m·k. Narożniki są zaokrąglone; tam, gdzie człony są równe, odległość rośnie najwyżej o 7,5 m·k;
- wypełnienie zagłębień: kolumna bierze max(własna odległość od brzegu, wypełniona odległość próbek przez B-splajn − `TUNNEL_FILL_SLACK` 0,5 m). K8a dodawał do odległości kolumny podniesienie próbek, co zostawiało zamknięte zagłębienia między próbkami. Próbki siatki liczą teraz odległość od brzegu tak jak kolumna: tablice węzłów i `bound` przez B-splajn, granice sekcji w x próbki, własna szczelina jeziora bezodpływowego próbki. Dzięki temu wypełnienie nie zmienia kolumn poza zagłębieniami. Pierwsza próba z wartościami z osi zabierała do 25% wody w końcach jezior przy granicy sekcji.

Pomiar narzędziem recenzenta `phase.py`: załamania > 0,2 m na metr w suchej niecce według fazy z mod 10 (próbki w środkach pikseli, więc wiersze węzłów wypadają w fazach 4 i 5):

| Kadr 1 m | `af65015` fazy 4 / 5 | `af65015` pozostałe | runda 1 fazy 4 / 5 | runda 1 pozostałe |
|---|---|---|---|---|
| M_w1S (−20350, 10900) | 881 / 916 | 62–127 | 51 / 47 | 43–64 |
| M_p4 (−15998, 5693) | 620 / 656 | 26–66 | 12 / 9 | 6–14 |
| M_w5 (−16170, 7800) | 160 / 87 (faza 3: 90) | 11–20 | 2 / 3 | 0–3 |
| M_p7 (7290, 11300) | 100 / 103 | 1–44 | 0 / 0 | 0 |
| L03 (6388, −8598) | 203 / 220 | 10–81 | 5 / 6 | 3–7 |
| L02 (3088, −5798) | 70 / 64 | 17–30 | 13 / 14 | 8–16 |

Kadry Z_w1_S, Z_w1_NE, ZG_w2_N, G_w10, G_p8, ZG_p1_SW, G_p4 i L03 nie mają już prostych linii W–E, prostokątnych pudełek ani płaskich ścian. W ZG_p1_SW zostaje słaby ślad załamania przy końcu suchej niecki, poniżej progu 0,2 m na metr. Nowy test `StandingWaterContainmentTest.tunnelValleyBasinsHaveNoGridCreases` sprawdza okna 1 m `CREASE_WINDOWS` (w1 południe, p4, w5, p7, L03, L02): załamań w wierszach węzłów (z ≡ 5 mod 10, próbki w całych metrach) może być najwyżej 1,5 × średnia faz z dala od wierszy + 10.

**Problem poważny: wieloboczne kontury jezior (potwierdzony).** Przyczyny:
1. obwiednie L1 dawały proste brzegi i narożniki wzdłuż przekątnych siatki;
2. przy wąskim węźle, węźle ukośnego odcinka i końcu śladu woda kończyła się klinem o prostych bokach (połowę szerokości ograniczała sama odległość od takiego węzła, nachylenie 1). Dwa kliny krótkiego jeziora tworzyły „latawiec” (w4);
3. twardy max członów robił narożnik tam, gdzie brzeg soczewki spotyka prostą granicę od doliny;
4. największa szczelina przekroju skakała, gdy przekrój się poszerzał.

Zmiany: obwiednie euklidesowe i gładkie maksimum (wyżej) oraz koniec wody jako soczewka. Przy końcu połowa szerokości spada do `TUNNEL_TIP` 60 m·k (gładkie minimum z połową szerokości, `tunnelTipHalf`), a wodę zamyka ćwiartka elipsy na 1,5 połowy szerokości (`tunnelTipLens`, jak koniec przy dolinie). Odległość do końca wody to `TunnelLake.tip`: oba kierunki łączy gładkie minimum o promieniu 4 `TUNNEL_TIP`, więc krótkie jezioro ma zaokrąglony środek zamiast szczytu. Wartości `end` (największa szczelina przekroju) i połowa szerokości z osi zmieniają się najwyżej o 1 m na metr wzdłuż śladu (dolne obwiednie). Obie tylko skracają lub zwężają jezioro; połowa szerokości mieści się w paśmie, w którym liczono poziom wody.

Na kadrach:
- w4 (G_w4, M_w4): zaokrąglone jajo zamiast latawca. Zostaje łagodny zachodni narożnik na styku granicy od doliny z bokiem jeziora;
- w5: bez ostrego końca i wcięcia V;
- REAL B (ZR_B_tip): koniec przy dolinie to proste odcięcie wzdłuż doliny z zaokrąglonymi narożnikami (promień ok. 30 m) zamiast dwóch ostrych narożników;
- REAL A (ZR_C): bez bruzdy wzdłuż osi za ogonem jeziora, ogon jest krótszy;
- jezioro w klinie REAL (−60055, 50053) (ZR_pit) znikło razem z niecką, bo po wygładzeniu nie zostaje w nim woda. Zagłębienie, które K8a tam usunął, nie wróciło (skan REAL niżej).

Zostaje prosty brzeg N–S przy x ≈ −57493 w REAL A. To ukośnie ścięty koniec soczewki przy granicy sekcji: soczewka jest elipsą we współrzędnych (odległość w poprzek, z), więc przy ukośnej osi jeden bok końca jest prawie prosty. Kształt jest sprzed K8a i widać go dopiero przy szerszym jeziorze z osi. Poprawka wymaga liczenia końca w punkcie osi najbliższym kolumnie i zmienia wszystkie jeziora ukośne, więc przechodzi do K8z albo M5.

**Problem poważny: nowe suche zagłębienie poza `PIT_SITES` (potwierdzony).** W (−26381, 10061) GAMEPLAY zagłębienie było 4,9 m głębsze od zagłębienia terenu surowego w oknie testu (siatka 2,5 m) na `af65015`; po rundzie 1 jest to 0,5 m. Skan GAMEPLAY ±40 km co 10 m (kryterium recenzji: margines ≤ 0 albo pierścień, obniżenie > 0,5 m, nadwyżka > 3 m) znalazł na `af65015` 11 takich zagłębień, do 8,8 m w (16080, 34390). Po rundzie 1 zostało jedno: (−15980, 5660), 3,7 m, które już było w `PIT_SITES`. Test `tunnelValleyBasinsHaveNoClosedPits` sprawdza teraz także 12 okien `WALL_CLUSTERS`, to miejsce i cztery najgłębsze miejsca ze skanu ±40 km na `af65015`, razem 45 okien.

| Zagłębienia w nieckach (nadwyżka ponad zagłębienie terenu surowego) | `af65015` | runda 1 |
|---|---|---|
| Okna testu (2,5 m): największa | 6,0 m (16085, 34325) | 3,45 m (17990, −5670) |
| (−15980, 5660) / (17990, −5670) / (−26310, 9570) | 3,7 / 1,7 / 3,1 m | 3,0 / 3,45 / 3,0 m |
| Skan GAMEPLAY ±20 km co 10 m, > 3 m | 2 | 1 ((−15980, 5660), 3,7 m) |
| Skan GAMEPLAY ±40 km co 10 m, > 3 m | 11 (do 8,8 m) | 1 ((−15980, 5660), 3,7 m) |
| Skan REAL ±30 km co 20 m, > 3 m | 0 | 0 |

(17990, −5670) to sucha odnoga niecki wzdłuż bocznej doliny. Odległość od brzegu nie ma tam zamkniętego zagłębienia, ale ma je mieszanie zbocza niecki z terenem po dolinach (`applyLake`). Poprawka wymagałaby wypełniania zagłębień w terenie, a nie w odległości od brzegu, więc limit `PIT_MAX` 4 m zostaje. Na kadrach 1 m G_w10 zagłębienie w rowie (−26310, 9572) ma 7 m, ale jego najgłębsza komórka nie jest obniżona przez nieckę (to teren po dolinach), więc test go nie liczy.

**Drobne:**
1. *Pomiary K8a z prototypu n7 (potwierdzone).* Liczby wody i oczek w rozdziale K8a pochodzą z prototypu bez ograniczenia `span`. Różnica jest rzędu 0,02%: woda GAMEPLAY ±20 km to 12600 w n7 i 12598 na `af65015`. Tabela niżej podaje pomiary kodu z repozytorium.
2. *Nachylenie członów w świecie (potwierdzone).* Obwiednie liczą teraz poziomą odległość próbek (wyżej), a połowa szerokości z osi ma dolną obwiednię o nachyleniu 1 wzdłuż śladu (wcześniej do 2,3 m na metr w (35819, −2805)). Javadoc `TunnelGaps.bound` poprawiony.
3. *Limit pamięci siatek (potwierdzone).* Siatka siedziała też w polu każdego obiektu konturu w 256 blokach `DirectCache`, więc limit mapy nie ograniczał pamięci, a wątki liczyły tę samą siatkę równolegle. Teraz siatki są tylko w mapie `tunnelGapGrids`: najwyżej `TUNNEL_GAP_CACHE` (256) siatek po ok. 0,3 MB, czyli ok. 77 MB. Każdą liczy raz `computeIfAbsent`; inne wątki czekają na tę samą siatkę, a budowa nie dotyka mapy. Opis pamięci w akapicie „Koszt” K8a („256 konturów”) był więc nieścisły.
4. *Dwa okna `WALL_CLUSTERS` bez niecki (potwierdzone).* (−23080, 39240) ma 0 kolumn, (−20700, 11200) ma 57; opis jest w javadoc. Nowych okien przy końcach jezior blisko dolin nie dodałem, bo najmniejsza szczelina za wycięciem doliny (punkt 6) leży w głębokiej niecce daleko od dna.
5. *Trzy zagłębienia > 3 m po K8a są nowe (potwierdzone).* (−18450, 14540), (18020, −5560) i (−16300, −9520) nie należały do 20 sprzed K8a. Po rundzie 1 żadne z nich nie przekracza 3 m w skanie ±20 km. (−18450, 14540) (12 m w terenie po dolinach; strażnik dna nie pozwala niecce otworzyć mu odpływu) trzeba obejrzeć na kadrach w K8z; to punkt do M5.
6. *Limit szczeliny za wycięciem doliny (potwierdzone).* Opis commita `b8ee80c` pominął poluzowanie limitu do 0 (wyjaśnione w odstępstwie 2 K8a i w javadoc testu). Pola rekordu w `LandscapeModelTest.tunnelLakesEndBeforeValleys` nazywają się teraz `cutGap` i `floorEdgeGap`. Po rundzie 1 najmniejsza szczelina za wycięciem to 56,6 m w REAL i 4,9 m w GAMEPLAY w (3290, −5690). Tam woda leży w głębokiej niecce jeziora L02, 205 m za brzegiem dna, a teren po dolinach jest 61 m nad lustrem: to szeroka ściana pod wysokim terenem, nie jezioro na zboczu doliny. Za brzegiem dna: REAL 140,4 m, GAMEPLAY 70,1 m (limity 140 i 70).
7. *Grunt podniesiony brzegiem ponad teren po dolinach (potwierdzone, kod bez zmian).* Sonda (GAMEPLAY ±20 km co 20 m, suche kolumny niecki poza dnem): `af65015` 299 kolumn > 3 m, 169 > 10 m, maks. 31,7 m w (9820, −17360); po rundzie 1 285, 172, maks. 32,6 m w (3400, −4700). Do obejrzenia na kadrach w K8z, np. (9820, −17360), (−1400, −6240), (3420, −4700).
8. *Głębokie suche rowy wzdłuż bocznych dolin z końcami V (potwierdzone, częściowo poprawione).* Zygzak profilu dna rowu na liniach siatki znikł (B-splajn), a końce V są zaokrąglone. Same rowy (sucha część niecki wzdłuż doliny, w G_w1 do ok. 140 m obniżenia) zostają do oceny wyglądu w K8z.
9. *Piła ±0,3–0,5 m co ok. 6 m na ścianie rowu (ZG_p5_SW).* Po rundzie 1 kadr ZG_p5_SW nie ma załamań > 0,2 m na metr. Okres 6 m nie pasuje do siatki (10 m), więc przyczyna była inna (prawdopodobnie przecięcie członów pod kątem); znikła razem z wygładzeniem.

**Pomiary po rundzie 1 (kod z repozytorium).**

| Pomiar | `af65015` | runda 1 |
|---|---|---|
| Ściany niecek GAMEPLAY ±40 km co 20 m / REAL ±40 km co 20 m | 0 / 0 | 0 / 0 |
| Ściany, test ±20 km (`WALLS_MAX`) i 12 okien 1 m | 0 | 0 |
| Groble (`DAM_MAX`) GAMEPLAY ±20 km / REAL | 0 / 0 | 0 / 0 |
| Woda jezior rynnowych GAMEPLAY ±20 km co 20 m | 12598 | 12515 (−0,7%) |
| Woda GAMEPLAY ±40 km co 25 m | — (n7: 26943) | 26721 |
| Woda REAL ±60 km co 75 m | — (n7: 6817) | 6810 |
| Jeziora z wodą GAMEPLAY ±20 km co 10 m | 34 | 32 |
| Oczka GAMEPLAY ±8 / ±20 km, REAL ±40 km (woda, torf, liczba) | 5376, 3376, 202 / 38728, 26244, 1558 / 16214, 10956, 2346 | bez zmian |
| `tunnelLakesEndBeforeValleys`: za wycięciem REAL / GAMEPLAY | 27,4 / 27,1 m | 56,6 / 4,9 m |

Woda wobec K4c w GAMEPLAY ±20 km: −57%, bez zmian; cel „w stronę −37%” nadal nie jest osiągnięty. Dwa jeziora mniej w ±20 km to małe jeziora, w których po wygładzeniu nie zostaje woda (jak w ZR_pit).

**Koszt.** `costTest -PcostRuns=15`, A/B w jednej sesji (baza `af65015`: sam `LandscapeModel.java` z commita K8a), stosunek do kopii M1, mediana / z minimów, µs na kolumnę w nawiasie:

| Obszar | Baza, przebieg 1 | Baza, przebieg 2 | Runda 1, przebieg 1 | Runda 1, przebieg 2 | Runda 1, przebieg 3 |
|---|---|---|---|---|---|
| REAL cały obszar | 1,085 / 1,112 (4,23) | 1,085 / 1,096 (4,23) | 1,101 / 1,108 (4,23) | 1,089 / 1,093 (4,23) | 1,105 / 1,120 (4,22) |
| REAL Beskidy | 1,109 / 1,118 (6,94) | 1,089 / 1,105 (6,99) | 1,119 / 1,116 (6,99) | 1,098 / 1,101 (6,96) | 1,126 / 1,122 (6,99) |
| REAL wielki masyw | 0,983 / 0,985 (6,45) | 0,982 / 0,984 (6,47) | 0,987 / 0,988 (6,49) | 0,980 / 0,982 (6,48) | 1,001 / 0,984 (6,53) |
| GAMEPLAY cały obszar | 1,186 / 1,192 (6,37) | 1,183 / 1,181 (6,33) | **1,218 / 1,219** (6,44) | 1,178 / 1,184 (6,37) | **1,212 / 1,205** (6,39) |
| GAMEPLAY Beskidy | 1,170 / 1,166 (11,04) | 1,156 / 1,156 (11,03) | 1,193 / 1,186 (11,24) | 1,157 / 1,149 (11,14) | 1,183 / 1,187 (11,11) |
| GAMEPLAY wielki masyw | 1,134 / 1,134 (12,05) | 1,124 / 1,119 (11,93) | 1,150 / 1,146 (12,15) | 1,123 / 1,126 (12,09) | 1,145 / 1,145 (12,04) |

Czas kolumny rundy 1 jest w granicach rozrzutu taki sam jak bazy (GAMEPLAY cały obszar 6,37–6,44 wobec 6,33–6,37 µs, do +1%; limity bezwzględne D1 dotrzymane). Stosunek skacze razem z pomiarem kopii M1 (5,27–5,39 µs w tych samych przebiegach): w dwóch z trzech przebiegów rundy 1 GAMEPLAY cały obszar przekracza 1,20 (1,21–1,22), w jednym nie (1,18); baza w tej sesji 1,18–1,19. Pomiar jednowątkowy tym samym zestawem chunków (narzędzie `K8Cost`, 15 rund, na przemian): `af65015` 6,520–6,527 µs, runda 1 6,502–6,515 µs, czyli bez różnicy. Profil (JFR): `tunnelShape` bez budowy siatki to ok. 1,2% czasu `sample`, budowa siatek (jednorazowa) 4,5% całego przebiegu z rozgrzewką. Budżet D1 w GAMEPLAY jest więc na granicy jak po K8a; kolejne kroki muszą najpierw znaleźć oszczędność (najwięcej kosztuje `RiverNetwork.query`, ok. 71% czasu).

**Złoty test.** Raport `-PgoldenReport`: zmienione te same łaty co w K8a (REAL B `grid`, `tunnel_valley_lake`, `outwash_plain_lake`, GAMEPLAY A `grid`, GAMEPLAY B `grid`), więc lista `src/test/golden-allow/K8a.txt` bez zmian; łaty kontrolne `*_interior` i cele bez zmian.

**Testy.** Nowe i zmienione:
- `StandingWaterContainmentTest.tunnelValleyBasinsHaveNoGridCreases` (nowy, okna `CREASE_WINDOWS`; wynik: w wierszach węzłów 52 / 11 / 2 / 0 / 4 / 12 załamań wobec średnio 56,3 / 12,3 / 0,7 / 0 / 5,7 / 12,0 w fazach z dala od wierszy);
- `tunnelValleyBasinsHaveNoClosedPits`: 45 okien (`PIT_SITES` z (−26381, 10061) i czterema miejscami skanu ±40 km oraz `WALL_CLUSTERS`), największa nadwyżka 3,45 m (limit 4 m); javadoc `WALL_CLUSTERS` i `PIT_MAX` uzupełnione;
- `LandscapeModelTest.tunnelLakesEndBeforeValleys`: pola `cutGap`, `floorEdgeGap`, javadoc z wynikami rundy 1.

Commity pośrednie: `184c48f` (B-splajn, obwiednie euklidesowe, gładkie maksimum), `cd91218` (zaokrąglone końce wody, wypełniona odległość od brzegu, siatki tylko w mapie, testy); przed każdym kompilacja, `fastTest`, `LandscapeModelTest`, `StandingWaterContainmentTest`, `GoldenTerrainTest` (lista K8a), przed drugim także `TerrainDeterminismTest`. Pełny `test` (`tools/dev/run-tests test`): PASS w 694 s (drzewo `af37def1e1a9`; potem zmieniał się tylko ten wpis w dokumentacji).

**Odstępstwa w rundzie 1:**
1. Ukośnie ścięty koniec soczewki przy ukośnej osi (REAL A, ZR_C) i proste odcięcie wzdłuż doliny (z zaokrąglonymi narożnikami) zostają; zob. wyżej.
2. Zagłębienia: limit `PIT_MAX` 4 m bez zmian (największe 3,45 m). Zagłębienie w suchej odnodze niecki pochodzi z mieszania zbocza niecki z terenem, a nie z odległości od brzegu.
3. Promień gładkiego maksimum to 30 m·k. Próba z 80 m·k dawała okrąglejsze kształty, ale zabierała do 25% wody w kadrach REAL i pogłębiała zagłębienie (−15980, 5660) do 9,7 m.

**Co zostaje** (poza listą K8a): ukośny koniec soczewki (wyżej), suche rowy wzdłuż bocznych dolin i grunt podniesiony brzegiem na zboczu doliny; wszystko do oceny na kadrach w K8z.
