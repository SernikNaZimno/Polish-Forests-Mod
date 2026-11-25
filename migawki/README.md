# Migawki kodu

Migawka powstała, zanim projekt stał się repozytorium git (2026-10-02). Stan kodu potrzebny do pomiarów porównawczych jest też w historii gita (commit `ec2c309` zawiera już zmiany S1–S4, więc do pomiaru M1 nadal służy ta migawka). Plików nie zmieniać i nie usuwać do końca M2.

## `m1-z-narzedziami-S0.tar`

- SHA-256: `6c5a2ed48ea79ae8f6201aac48b9ed1b9c7d8daa24eccfe95860cb8f5ddf6fa5`
- Data: 2026-10-02, po poprawkach kroku S0 planu M2, przed S1.
- Zawartość: `src/` (wszystkie zbiory źródeł), `build.gradle`, `gradle.properties`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/`, `.gitattributes`, `.gitignore`. Bez `docs/`, `build/`, `.gradle/` i `run/`.
- Nazwy: to kod sprzed zmiany nazwy moda (M2-9), więc w środku są dawne nazwy: pakiet `pl.polskielasy`, `SampleKosztTest`, tryb `etapy`, właściwości `-PzlotyZapisz` i `-PkosztPrzebiegi`, plik `zloty_teren_m1.txt`, wiersze logu `[wydajnosc]`. W projekcie odpowiadają im `pl.polishforests`, `SampleCostTest`, `stages`, `-PwriteGolden`, `-PcostRuns`, `golden_terrain_m1.txt` i `[performance]`.
- `src/main` i `src/client` są identyczne z kodem końca M1 (sprawdzone `diff -rq` z migawką sprzed S0). `src/test` i `src/gametest` zawierają narzędzia pomiarowe S0: `GoldenTerrainTest`, `SampleKosztTest`, zamrożoną kopię modelu `landscape.m1` i `PerformanceClientGameTest` z trybem `etapy` (liczniki jako przyrosty, trzy obszary na skalę).

### Pomiar bazowy etapów chunka na kodzie M1

Jeśli `src/main` w projekcie już się zmienił (od S2 model jest inny), pomiar bazowy robi się z migawki, w tej samej sesji co pomiar bieżącego kodu (kod migawki ma dawne nazwy, stąd tryb `etapy`):

```
mkdir "../pl-m1-pomiar"
tar xf migawki/m1-z-narzedziami-S0.tar -C "../pl-m1-pomiar" --force-local
cd "../pl-m1-pomiar"
./gradlew runClientGameTest -Pgametest=etapy
```

W nowym katalogu Loom przygotuje Minecrafta od nowa (pierwsze uruchomienie trwa dłużej). Wyniki są w logu, w wierszach `[wydajnosc]`. Potem ten sam pomiar w projekcie, na bieżącym kodzie: `./gradlew runClientGameTest -Pgametest=stages` (wiersze `[performance]`). Dopóki `src/main` było równe M1 (przed S2), wystarczał tryb `etapy` w samym projekcie.

## `m2-baseline-r1-harness.patch` (krok FA10)

- SHA-256: `9357fe5fa1efcda63cdee2c9ecf6213a714b24226167316357623f08207bb6c6`
- Data: 2026-11-25, przed pomiarem zbiorczym FA10.
- Zawartość: uprząż pomiarowa bazy M2, która dotąd była tylko niezapisanymi zmianami w worktree `pl-m2-baseline` (tag `m2-baseline-r1` + uprząż V10, `-PloadOnly` z O1, `-PnoNvidium`, `ForestPerformance` z miejscami F1–F6, bez Tatr): `build.gradle`, `PerformanceClientGameTest.java` i nowy plik `ForestPerformance.java`. Sprawdzone: `git archive m2-baseline-r1` + `git apply` daje pliki identyczne z worktree (poza końcami linii).
- Po co: worktree `pl-m2-baseline` ma zniknąć po FA10 (CP-D.12, G.62-i), a tag sam nie ma uprzęży. Z tagu i łatki bazę M2 da się odbudować:

```
git worktree add --detach "../pl-m2-baseline" m2-baseline-r1
cd "../pl-m2-baseline"
git apply "../Przyrodniczo zgodne lasy minecraft/migawki/m2-baseline-r1-harness.patch"
./gradlew runClientGameTest -Pgametest=performance -Psites=slice -Poptimization -PloadOnly -Pcaves=off
```

Loom przygotuje Minecrafta od nowa (pierwsze uruchomienie trwa dłużej). Baza nie czyta `-Pcaves`, `-PperfSites` ani `-PperfFauna` (świat M2 nie ma jaskiń moda ani fauny).
