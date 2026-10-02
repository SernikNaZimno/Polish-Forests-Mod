# Pomiary bazowe M1 (M2, krok S0)

Data: 2026-10-02. Stan kodu: koniec M1, przed jakąkolwiek zmianą modelu w M2. Te liczby są punktem odniesienia dla budżetów z planu M2 (`docs/03-m2-biomy.md`, §3.6).

Kod M1 jest zachowany na dwa sposoby: zamrożona kopia modelu krajobrazu w źródłach testów (`src/test/java/pl/polishforests/worldgen/landscape/m1/`, nie zmieniać) i migawka całego kodu `migawki/m1-z-narzedziami-S0.tar` (SHA-256 i instrukcja w `migawki/README.md`).

## Złoty test terenu

`GoldenTerrainTest` liczy skróty SHA-256 pól `ColumnSample` z M1: `surface` (zaokrąglone do 1e-6 m), `waterLevel`, `waterKind`, `type`, `substrate`, `coverDepth` (1e-6 m), a także `blocks`: Y najwyższego bloku gruntu i wody w obu odwzorowaniach pionowych (`VerticalScale.REAL` i `GAMEPLAY`). Nowe pola dodane w M2 nie wchodzą do skrótu.

| Parametr | Wartość |
|---|---|
| Zestawy | 2 skale (realistyczna, rozgrywka) × 2 ziarna (20260927, −7316550294015845337) przy suwaku regionów 1,0, oraz skala realistyczna, ziarno 20260927, suwak 0,5 |
| Kolumny w zestawie | 5376: siatka 32 × 32 co 0,6 rozmiaru makroregionu (ok. 1230 km w REAL, 27 km w GAMEPLAY) i 17 łat 16 × 16 |
| Łaty | wybrzeże (morze i ląd), zalew, plaża, klif, ujście, rzeka nizinna, wielka rzeka, rzeka na Pogórzu, starorzecze, jezioro rynnowe, jezioro na sandrze, oczko, torfowisko, potok górski, wnętrze Beskidów, szczyt, wnętrze Pogórza |
| Plik wzorcowy | `src/test/resources/golden_terrain_m1.txt`: środki łat, krok, skrót każdej łaty, skrót każdego pola i całości |
| Czas testu | ok. 9 s porównanie i ok. 9 s sprawdzenie kopii M1 (próbki liczone równolegle) |

Każda łata ma cel, który sprawdzamy przy zapisie i przy każdym porównaniu. Przykłady: plaża ma co najmniej 8 kolumn `BEACH_SAND` i formę `BEACH`, wybrzeże ma po co najmniej 16 kolumn otwartego morza i suchego lądu, wielka rzeka ma formę `RIVER` (rząd ≥ 2) i zajmuje co najmniej 1/8 łaty, szczyt ma formę `SUMMIT` (w REAL powyżej 1150 m z reglem górnym, w GAMEPLAY powyżej 900 m). Cały zestaw musi też zawierać każdy rodzaj wody, każdy typ krajobrazu i każdy utwór powierzchniowy. Zapis pliku wzorcowego kończy się błędem, gdy jakiegoś miejsca nie znaleziono albo cel nie jest spełniony. Łat zastępczych nie ma.

Przy różnicy test wypisuje zmienione łaty i pola, a następnie liczy te same kolumny zamrożoną kopią M1 i dla każdej zmienionej łaty podaje max |Δsurface|, max |ΔcoverDepth| i liczbę kolumn z innym `waterLevel`, enumem lub blokiem. Gdy różnica jest mniejsza niż 1e-6 m i żaden enum ani blok się nie zmienił, dopisuje „różnica tylko numeryczna”: to znak, że zmiana kolejności działań przestawiła zaokrąglenie skrótu, a nie że zmienił się teren. Osobny test `frozenM1CopyMatchesGolden` pilnuje, że kopia M1 daje skróty z pliku. Jeśli i ona się nie zgadza, przyczyną jest JVM lub procesor, nie kod modelu.

Nowy plik wzorcowy (tylko przy zamierzonej zmianie terenu, opisanej w dokumentacji):

```
./gradlew test --tests '*GoldenTerrainTest*' -PwriteGolden
```

Środki łat szuka się tylko przy zapisie (ok. 2 min). Przy porównaniu są brane z pliku, więc zmiana wyszukiwarek w innych testach nie zmienia zestawu kolumn.

## Koszt `LandscapeModel.sample` (`SampleCostTest`)

Jeden wątek, JDK 26, 12 wątków sprzętowych. Próbkowanie jak w generatorze: całe chunki 16 × 16 po kolei, 400 różnych chunków (102 400 kolumn) na pomiar. Rozgrzewka JIT na innych 400 chunkach, potem 7 przebiegów po tych samych chunkach (więcej: `-PcostRuns=15`). W każdym przebiegu obecny model i zamrożona kopia M1 są mierzone jeden po drugim, a kolejność odwraca się co przebieg. Pierwszy przebieg trafia na zimne pamięci regionalne (komórki, sieć rzeczna), więc w REAL jest kilka razy wolniejszy. Mediana i minimum opisują stan jak w grze, gdzie sąsiednie chunki mają te pamięci już wypełnione.

**Jak sprawdzać budżet „sample +≤ 5%”.** Rozstrzyga stosunek obecny/M1 z jednego uruchomienia, najlepiej liczony z minimów, a nie porównanie liczb z różnych uruchomień. Na kodzie identycznym z M1 (obecny = kopia) wyszło w jednym uruchomieniu (pełne `./gradlew test`, 7 przebiegów):

| Obszar | Obecny: mediana / minimum (µs na kolumnę) | M1: mediana / minimum | Obecny/M1: mediana stosunków / z minimów |
|---|---|---|---|
| REAL, cały obszar (±320 km, bez gór) | 4,77 / 4,26 | 4,57 / 4,43 | 1,035 / 0,962 |
| REAL, wnętrze Beskidów (±9,6 km) | 7,66 / 7,41 | 7,98 / 7,40 | 0,968 / 1,002 |
| GAMEPLAY, cały obszar (±7 km) | 6,37 / 6,22 | 6,24 / 6,20 | 1,005 / 1,003 |
| GAMEPLAY, wnętrze Beskidów (±210 m) | 11,06 / 10,99 | 12,28 / 10,99 | 0,908 / 1,000 |

Nawet w jednej JVM mediana stosunków dla tego samego kodu waha się o ±10%, a stosunek z minimów o ok. ±4%. Wniosek: wynik bliski granicy 5% trzeba powtórzyć z `-PcostRuns=15` i patrzeć na stosunek z minimów we wszystkich czterech obszarach.

Według typu w środku chunka (obecny model, to samo uruchomienie, µs na kolumnę): REAL, cały obszar: sandr 4,0, wysoczyzna morenowa 3,3, równina staroglacjalna 5,1, Pogórze 6,9, morze 0,9. W tej próbce nie ma gór. Beskidy mierzy tylko osobny przebieg we wnętrzu Beskidów: 7,1. GAMEPLAY: sandr 5,2, wysoczyzna 5,3, równina 7,0, Pogórze 10,6, morze 1,6, Beskidy 10,8 (wnętrze).

Model na chunk (256 kolumn): REAL ok. 1,1–1,2 ms na nizinach i ok. 1,9–2,0 ms w Beskidach; GAMEPLAY ok. 1,6 ms i ok. 2,8 ms.

Wcześniejsze pomiary z S0, przed dodaniem kopii M1 (osobne uruchomienia samego testu, 5 przebiegów, mediana w µs na kolumnę):

| Pomiar | Uruchomienie 1 | Uruchomienie 2 | Uruchomienie 3 | Uruchomienie 4 |
|---|---|---|---|---|
| REAL, cały obszar | 4,75 | 5,65 | 5,87 | 5,33 |
| REAL, wnętrze Beskidów | – | 8,59 | 9,25 | 8,35 |
| GAMEPLAY, cały obszar | 6,53 | 7,43 | 7,55 | 7,40 |
| GAMEPLAY, wnętrze Beskidów | – | 12,46 | 13,13 | b.d. |

Te liczby różnią się między uruchomieniami o ±10–20%, stąd pomiar na przemian z kopią M1. W tamtej wersji testu pomiar w Beskidach GAMEPLAY losował chunki z powtórzeniami (322 różne z 400). Teraz test bierze 400 różnych chunków.

Uruchomienie: `./gradlew test --tests '*SampleCostTest*'`. Wynik jest na standardowym wyjściu, w wierszach `[sample cost]`. Przy powtórzeniu bez zmian w kodzie Gradle pomija test jako aktualny, więc trzeba dodać `--rerun`.

### Po S2 (eksport pól do `ColumnSample`)

Przed zmianami, na kodzie równym M1 (7 przebiegów), obecny/M1, mediana stosunków / z minimów: REAL cały obszar 0,995 / 1,059; REAL Beskidy 0,997 / 1,002; GAMEPLAY cały obszar 0,994 / 1,029; GAMEPLAY Beskidy 0,976 / 0,983.

Po S2 (15 przebiegów, `-PcostRuns=15`), obecny/M1, mediana stosunków / z minimów:

| Obszar | Uruchomienie 1 | Uruchomienie 2 | Uruchomienie 3 (wersja końcowa) |
|---|---|---|---|
| REAL, cały obszar | 1,059 / 1,043 | 1,026 / 1,026 | 1,048 / 1,055 |
| REAL, wnętrze Beskidów | 1,066 / 1,086 | 1,029 / 1,053 | 1,023 / 0,969 |
| GAMEPLAY, cały obszar | 1,026 / 1,071 | 1,048 / 1,074 | 1,016 / 1,083 |
| GAMEPLAY, wnętrze Beskidów | 1,018 / 1,013 | 1,037 / 1,047 | 1,021 / 1,048 |

W pełnym `./gradlew test --rerun` po S2 (7 przebiegów): 1,025 / 1,047; 0,963 / 0,972; 0,975 / 1,000; 0,985 / 1,006 (kolejność obszarów jak w tabeli).

Uruchomienie 1 było przed dwiema oszczędnościami: piaszczystości nie liczymy w morzu, a brzeg wypukły tylko przy wyraźnych meandrach i bliżej koryta. Dodatkowy koszt to jeden szum piaszczystości, trzy małe obiekty na próbkę (rekordy `Terrain`, `Waters` i kontekst rzeźby) oraz brzeg wypukły przy korytach (kilka szumów).

Szum na identycznym kodzie to ok. ±4–6%, więc pojedyncze uruchomienia nie rozstrzygają. Recenzja S2 zmierzyła REAL, cały obszar jeszcze trzy razy: mediany 1,053 (pełny test), 1,052 (samodzielnie, 15 przebiegów) i 1,017 (drugi pełny test). Mediana wszystkich median REAL, cały obszar wynosi ok. 1,048, czyli **ok. +5%**, na granicy budżetu S2 (+≤ 5%), a nie ok. +3%. Pozostałe obszary mają ok. +1–3%. Stosunki z minimów w pełnym teście dochodziły do 1,095 i 1,156 (REAL Beskidy, szum).

Po poprawkach recenzji S2 (ciągłe d z `MeanderField.distances`, pierścień 150 m·k jezior bezodpływowych i rynnowych, brzeg wypukły szerokich koryt), 15 przebiegów, `--rerun`, obecny/M1, mediana stosunków / z minimów: REAL cały obszar 1,039 / 1,046; REAL Beskidy 1,031 / 1,039; GAMEPLAY cały obszar 1,037 / 1,042; GAMEPLAY Beskidy 1,004 / 1,012. W pełnym `./gradlew test --rerun` po poprawkach (7 przebiegów): 1,034 / 1,002; 0,999 / 0,964; 1,005 / 1,005; 0,988 / 0,987. Bezwzględnie (ten pomiar, maszyna szybsza niż w poprzednich): 4,10, 6,68, 5,77 i 9,85 µs/kolumnę wobec 3,93, 6,46, 5,59 i 9,79 µs w M1. Przy siatkach S3 (budżet fazy 1: +5–10%) trzeba szukać oszczędności: zmienne lokalne zamiast obiektów `Stojaca` i `ReliefParts` albo `sampleInto` z tablicami.

### Po S3 (siatki `CoarseTerrainField` i `RegionalField`)

15 przebiegów, `--rerun`, obecny/M1, mediana stosunków / z minimów:

| Obszar | Przed S3 (kod S2, maszyna wolna) | S3, uruchomienie 1 | S3, uruchomienie 2 | S3 z wyłączonymi siatkami |
|---|---|---|---|---|
| REAL, cały obszar | 1,067 / 1,071 | 1,071 / 1,081 | 1,074 / 1,062 | 1,050 / 1,057 |
| REAL, wnętrze Beskidów | 1,049 / 1,084 | 1,039 / 1,041 | 1,049 / 1,040 | 1,042 / 1,042 |
| GAMEPLAY, cały obszar | 1,028 / 1,012 | 1,048 / 1,054 | 1,050 / 1,046 | 1,054 / 1,049 |
| GAMEPLAY, wnętrze Beskidów | 1,028 / 1,021 | 1,024 / 1,018 | 1,022 / 1,019 | 1,019 / 1,018 |

Pomiar „przed S3” trafił na wolniejszy stan maszyny (REAL, cały obszar: 6,56 µs/kolumnę wobec 4,36 µs w uruchomieniu 1 po S3). Ostatnia kolumna to kod S3, w którym `terrain()` i `sample` wstawiały stałe zamiast odczytu siatek. To ten sam przebieg pomiaru w tej samej sesji, więc rozstrzyga przyrost S3: ok. +2% w REAL, cały obszar i 0–1% w pozostałych obszarach. Bezwzględnie po S3 (uruchomienie 1): 4,36, 7,01, 5,97 i 10,36 µs/kolumnę wobec 4,11, 6,70, 5,72 i 10,12 µs w M1. Same odczyty obu siatek po rozgrzaniu kafli (400 chunków z tego testu, 11 przebiegów, mediana) kosztują 45 ns na kolumnę, czyli 1,1% `sample` w REAL i 0,8% w GAMEPLAY. Budowa kafla `CoarseTerrainField` to 121 wywołań `landElevation`. W REAL kafel obejmuje 256 chunków, w GAMEPLAY 64. W `SampleCostTest` kafle są zimne tylko w pierwszym przebiegu, bo chunki są rozrzucone.

### Po poprawkach S3 (oszczędności bez zmiany wyniku)

Tablice robocze `projectChannel` i `query` z bufora wątku, zmienne lokalne zamiast `Stojaca`, mniej tablic w `blend`, pominięcie szumu brzegu dalekich jezior z samego pierścienia siedlisk (opis w docs/03-m2-biomy.md §3.2). Złoty test bez zmian. 15 przebiegów, `--rerun`, obecny/M1, mediana stosunków / z minimów:

| Obszar | Uruchomienie 1 | Uruchomienie 2 | Uruchomienie 3 |
|---|---|---|---|
| REAL, cały obszar | 1,012 / 1,008 | 1,020 / 1,046 | 1,019 / 1,039 |
| REAL, wnętrze Beskidów | 0,984 / 0,973 | 1,001 / 1,002 | 0,998 / 1,005 |
| GAMEPLAY, cały obszar | 1,003 / 1,003 | 1,023 / 1,014 | 1,029 / 1,021 |
| GAMEPLAY, wnętrze Beskidów | 0,967 / 0,967 | 0,977 / 0,977 | 0,981 / 0,982 |

W pełnym `./gradlew test --rerun` (7 przebiegów): 1,018 / 1,018; 0,992 / 0,995; 1,002 / 1,005; 0,979 / 0,979. Bezwzględnie (uruchomienie 2): 4,10, 6,53, 5,60 i 9,60 µs/kolumnę wobec 4,01, 6,52, 5,52 i 9,87 µs w M1. Profil JFR przed poprawkami (REAL, cały obszar): 59% alokowanych bajtów to tablice z `projectChannel`, 9% tablice z `query`, 7% i 3% tablice z `blend`, `ReliefParts` 2%, `Stojaca` 1%. Czas: `RiverNetwork.query` ok. 63% `sample`, w tym `projectChannel` 41% i `StrictMath.hypot` ok. 19% (tak samo w M1; zamiana `hypot` mogłaby zmienić wynik o ulp, więc jej nie robimy).

Pojedyncze zapytania siatki terenu (świeża siatka, punkty w osobnych kaflach, 2000 punktów, 3 powtórzenia): pierwsze zapytanie, które liczy samo oczko z 16 węzłów, 14–26 µs; drugie, które buduje kafel, 107–186 µs; kolejne, z pamięci, 0,15–0,4 µs.

## Etapy chunka w grze (`PerformanceClientGameTest`, tryb `stages`)

**Jeszcze nie zmierzone.** W krokach planu nie uruchamiamy gry. Pomiar bazowy musi być zrobiony na kodzie modelu z M1, więc nie zastąpi go test w grze na końcu fazy 1, bo wtedy model zawiera już S2 i S3. Dwie drogi:

1. Najlepiej przed S2, dopóki `src/main` jest identyczne z M1 (S1 dodaje tylko mixin temperatury biomu). Wystarczy uruchomić w projekcie:

   ```
   ./gradlew runClientGameTest -Pgametest=stages
   ```

2. Po S2: odtworzyć kod M1 z `migawki/m1-z-narzedziami-S0.tar` w osobnym katalogu i uruchomić tam ten sam tryb (w kodzie migawki ma dawną nazwę `etapy`, a wiersze logu `[wydajnosc]`), w tej samej sesji co pomiar bieżącego kodu (instrukcja w `migawki/README.md`).

Tryb `stages` tworzy światy „Polska” w obu skalach, bez świata wanilijnego i bez pomiaru FPS. W każdej skali mierzy trzy obszary 8 × 8 chunków (ziarno 20260927):

| Obszar | REAL (róg NW w blokach) | GAMEPLAY (róg NW w blokach) |
|---|---|---|
| nizina (`lowland`; ten sam punkt co w M1, płaska nizina bez rzek) | −40000, 25000 | −40000, 25000 |
| beskidy (`beskids`; łata „beskids” ze złotego testu) | 154770, 1058674 | 27545, 3190 |
| rzeka (`river`; łata „large_river”) | −19548, 11189 | −1915, 5958 |

Dla każdego obszaru loguje czasy etapów STRUCTURE_STARTS, BIOMES, TERRAIN, FEATURES, LIGHT i FULL oraz koszt modelu (`SAMPLE_NANOS`) i wypełniania (`FILL_NANOS`) na chunk. Liczniki generatora są statyczne i rosną przez cały proces. Wcześniej test logował ich sumy narastające, więc wiersz drugiego świata uśredniał chunki obu skal i chunki ze startu świata. Teraz loguje tylko przyrost w czasie pomiaru danego obszaru. Przyrost obejmuje sąsiednie chunki potrzebne do dekoracji i światła (zwykle ok. 100 chunków na obszar) oraz chunki wczytywane w tym czasie w tle wokół gracza. W logu szukaj wierszy `[performance]`.

Ostatnie znane wyniki z M1 (`docs/01-architektura.md`, „Koszt”; `docs/02-wydajnosc.md`) są tylko orientacyjne. Nie rozróżniają skal, a liczby modelu i wypełniania mogły zawierać ten sam błąd liczników:

| Pomiar | M1 (orientacyjnie) | Cel M2 (§3.6) |
|---|---|---|
| Pełna generacja chunka z dekoracjami i światłem | ok. 44 ms (zakres 35–46 ms) | ≤ 48 ms |
| Model krajobrazu na chunk (`SAMPLE_NANOS`) | 1,3–2,6 ms | +≤ 5% |
| Wypełnianie bloków (`FILL_NANOS`) | 1,5–3 ms | TERRAIN ≤ 1,10 × M1 |
| BIOMES | 16 próbek i ok. 8400 wywołań `biomeFor` na chunk | ≤ 0,2 ms na chunk |

**Brakuje też bazy dla S10:** liczby chunków na sekundę z paczką C2ME na kodzie M1 (kryterium „z C2ME ≥ 0,9 × chunków na sekundę z M1”). Trzeba ją zmierzyć tą samą drogą (przed S2 albo z migawki).

## Uwaga o skali rozgrywki: regiel górny

W GAMEPLAY Beskidy rzadko przekraczają 1150 m n.p.m. Dla ziarna 20260927 najwyższy punkt w promieniu 8 km od wnętrza Beskidów ma ok. 1015 m, a dla −7316550294015845337 w tym samym promieniu tylko 6 punktów (na siatce co 20 m) przekracza 1150 m. Próg 1150 m (regiel górny, `AltitudinalBelts` w S4) prawie nie wystąpi więc w skali rozgrywki. Łata „summit” w złotym teście ma tam próg 900 m.
