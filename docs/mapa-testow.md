# Mapa testów

Decyzja użytkownika z 2026-11-04 (docs/00 G.37): testy dobieramy według tego, czego zmiana dotyka, a nie wszystkie po każdej rundzie. Mapa (ścieżka → obszary → komendy) jest w jednym miejscu, w `tools/dev/test_plan.py`.

```bash
python tools/dev/test_plan.py <commit przed krokiem>          # testy w trakcie kroku
python tools/dev/test_plan.py <commit przed krokiem> --end    # testy na koniec kroku
python tools/dev/test_plan.py --list                          # sama mapa
```

## Zasady

1. **W trakcie kroku:** tylko to, co wypisze `test_plan.py`: `fastTest` (`run-tests --reuse`), wybrane klasy JUnit i gametesty obszarów, których dotyczy zmiana. Gametest powtarzamy tylko wtedy, gdy od jego ostatniego PASS zmienił się kod jego obszaru.
2. **Na koniec kroku:** jeden pełny przebieg `test_plan.py … --end`, z jednym pełnym `test` przez `run-tests`. Recenzent i agent kontrolny biorą wynik z `--reuse` i logów, nie uruchamiają gier ponownie.
3. **Powtarzalność świata sprawdzamy na blokach, nie na zrzutach:** służy do tego gametest `determinism` (bloki okna dekoracji porównane blok po bloku, dowolna kolejność chunków, z C2ME raport z tolerancją J1-q). Zrzutów ekranu nie używamy do sprawdzania, czy „ten sam seed daje ten sam las”.
4. **Zrzuty** robimy tylko wtedy, gdy zmiana dotyczy renderu (modele, tekstury, klient), i tylko dla tablicy, której dotyczy zmiana. Zrzutów nie ocenia agent ani pod kątem wyglądu (G.36 U-1), ani do porównań pikseli między przebiegami. Wygląd sprawdza użytkownik według listy „Do sprawdzenia w grze”.
5. **Złoty test terenu** (`GoldenTerrainTest`, JUnit, bez okna gry) uruchamiamy, gdy zmiana dotyka rzeźby: `worldgen/landscape`, `chunk`, `surface`, `cave`, `climate` i danych `worldgen`. Pilnuje on zgodności ze starymi zapisami, żeby na granicy starych i nowych chunków nie powstawały uskoki. Celowe zmiany terenu (np. Tatry) robimy jako nowe obszary i nowe sole, z listą dozwolonych zmian `src/test/golden-allow/`.
6. **Wpływy pośrednie** (wspólna sól lub generator liczb losowych, kolejność rejestrów, mixin w generacji) mapa wyłapuje tylko zgrubnie. Od nich jest pełny przebieg na koniec kroku. Nieznany plik w `src/` wybiera wszystkie obszary.
7. **Pomiary wydajności** robimy tylko wtedy, gdy krok tego wymaga: jeden przebieg kontrolny, gdy zmiana może zmienić koszt generacji, a pełne A/B tylko w krokach, które je przewidują.
8. **Listy do sprawdzenia** dla użytkownika wskazują miejsca komendą `/polishforests find <cel|biome …|zone …>` (z `/polishforests list` i `here`), a nie stałymi współrzędnymi. Współrzędne podajemy co najwyżej jako podpowiedź dla podanego ziarna.
