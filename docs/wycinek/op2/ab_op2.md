# OP2: tabele A/B (PW-10, G.34, CP-D.5)

Krok OP2 (`docs/04` „Projekt OP2”, „Stan po OP2”): algorytmiczna optymalizacja planera drzewostanu bez zmiany świata. Bramka testu kosztu przeszła (koszt drzewostanu 0,866 × bazy, zysk 13,4%), więc pełny A/B jak w OP1. Przebiegi 2026-11-27 12:56 … 17:00, sterownik `handoff/OP2/run_ab_op2.sh` (kopia sterownika OP1, te same funkcje ciszy i próbnik w trakcie), ABBA (rundy nieparzyste M2 pierwszy), ta sama uprząż `ForestPerformance` w obu buildach. Budowy: M2 = `pl-m2-baseline` przy `858965f` z łatką uprzęży FA10-f, OP1 = `bf71f3c` (worktree `pl-pre-op2`, tylko etapy), OP2 = `e1f6db2`. Rundy: `load` bez jaskiń 6, z jaskiniami 4, `stages` 4 (kolejność M2 → OP1 → OP2, potem odwrotnie). Warunek ciszy: brak gry użytkownika i obcego programu java, CPU < 20 %, GPU ≤ 5 % karty (3 próbki co 20 s przed przebiegiem, próbnik co 20 s w trakcie). Czekanie łącznie 2582 s, w tym ok. 2160 s na grę użytkownika (przed `stages_r3` OP1); dwa przebiegi (`load_r2` OP2, `load_r4` M2) zatrzymał próbnik przy starcie przez `java.exe` własnego klienta testu (fałszywy alarm), poszły do `ab/disturbed/` i zostały powtórzone. Przez całą sesję działał w tle emulator Androida użytkownika (`qemu-system-x86_64`, ok. 0,2–0,3 rdzenia), czego warunek ciszy nie wyklucza. Żaden przebieg nie używał `-PtimingsReportOnly`.

Kolumna „OP1 (2026-11-27)” to wynik buildu OP1 z jego własnej sesji (`docs/wycinek/op1/ab_op1.md`, inne przebiegi M2); porównanie OP2 z OP1 w tej samej sesji (etapy) jest w `stages_op2_vs_op1.md` obok. Przelotów FPS nie było: kod klienta i renderu bez zmian od bazy kroku (`git diff bf71f3c -- src/client` pusty), więc tabela FPS odpada (wiersze FPS 0).


## Chunki na sekundę z C2ME bez jaskiń (`-Pcaves=off`, `-PloadOnly`); próg ≥ 0,7 × M2

| miejsce | M2 | OP2 | iloraz | ilorazy rund (min–max) | OP1 (2026-11-27) |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 134,40 | 93,80 | 0,698 | 0,662–0,752 | 0,715 |
| REAL fresh_pine_1 | 173,65 | 130,60 | 0,752 | 0,706–0,764 | 0,714 |
| REAL lowland_beech_1 | 170,95 | 123,10 | 0,720 | 0,674–0,745 | 0,681 |
| REAL alder_carr_1 | 170,25 | 122,15 | 0,717 | 0,696–0,788 | 0,741 |
| REAL upland_fir_1 | 155,45 | 111,00 | 0,714 | 0,687–0,770 | 0,719 |
| REAL bog_woodland_1 | 170,00 | 123,10 | 0,724 | 0,667–0,768 | 0,763 |
| REAL floodplain_1 | 166,05 | 121,25 | 0,730 | 0,694–0,766 | 0,714 |
| REAL montane_spruce_1 | 147,55 | 109,25 | 0,740 | 0,727–0,790 | 0,735 |
| GAMEPLAY oak_hornbeam_1 | 202,95 | 136,40 | 0,672 | 0,655–0,695 | 0,677 |
| GAMEPLAY fresh_pine_1 | 201,15 | 137,35 | 0,683 | 0,668–0,721 | 0,717 |
| GAMEPLAY lowland_beech_1 | 195,20 | 133,85 | 0,686 | 0,641–0,696 | 0,722 |
| GAMEPLAY alder_carr_1 | 226,75 | 157,80 | 0,696 | 0,660–0,713 | 0,698 |
| GAMEPLAY upland_fir_1 | 173,20 | 123,45 | 0,713 | 0,674–0,765 | 0,749 |
| GAMEPLAY bog_woodland_1 | 286,45 | 214,15 | 0,748 | 0,684–0,822 | 0,725 |
| GAMEPLAY floodplain_1 | 194,30 | 138,10 | 0,711 | 0,669–0,756 | 0,719 |
| GAMEPLAY montane_spruce_1 | 162,65 | 113,70 | 0,699 | 0,688–0,724 | 0,702 |
| **min / mediana / max** | | | 0,672 / 0,713 / 0,752 | | |

## Strażnik z jaskiniami: chunki na sekundę z C2ME (świat z jaskiniami wobec M2 bez jaskiń); próg REAL ≥ 0,4 ×, GAMEPLAY ≥ 0,55 ×, lasy górskie REAL ≥ 0,35 ×, GAMEPLAY ≥ 0,45 ×

| miejsce | M2 | OP2 | iloraz | OP2 bez jaskiń | koszt jaskiń OP2, ms na chunk | iloraz wobec M2 ze wszystkich przebiegów | OP1 |
|---|---|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 130,95 | 68,80 | 0,525 | 93,80 | 3,87 | 0,524 | 0,533 |
| REAL fresh_pine_1 | 174,80 | 72,65 | 0,416 | 130,60 | 6,11 | 0,416 | 0,428 |
| REAL lowland_beech_1 | 175,60 | 90,60 | 0,516 | 123,10 | 2,91 | 0,528 | 0,539 |
| REAL alder_carr_1 | 165,55 | 96,25 | 0,581 | 122,15 | 2,20 | 0,574 | 0,620 |
| REAL upland_fir_1 | 155,20 | 60,65 | 0,391 | 111,00 | 7,48 | 0,391 | 0,390 |
| REAL bog_woodland_1 | 168,50 | 79,90 | 0,474 | 123,10 | 4,39 | 0,473 | 0,471 |
| REAL floodplain_1 | 163,75 | 101,80 | 0,622 | 121,25 | 1,58 | 0,613 | 0,594 |
| REAL montane_spruce_1 | 149,20 | 59,95 | 0,402 | 109,25 | 7,53 | 0,404 | 0,407 |
| GAMEPLAY oak_hornbeam_1 | 200,95 | 115,30 | 0,574 | 136,40 | 1,34 | 0,568 | 0,564 |
| GAMEPLAY fresh_pine_1 | 196,20 | 112,75 | 0,575 | 137,35 | 1,59 | 0,568 | 0,628 |
| GAMEPLAY lowland_beech_1 | 193,25 | 109,70 | 0,568 | 133,85 | 1,64 | 0,566 | 0,606 |
| GAMEPLAY alder_carr_1 | 230,80 | 133,45 | 0,578 | 157,80 | 1,16 | 0,581 | 0,617 |
| GAMEPLAY upland_fir_1 | 175,40 | 102,60 | 0,585 | 123,45 | 1,65 | 0,589 | 0,601 |
| GAMEPLAY bog_woodland_1 | 289,35 | 180,80 | 0,625 | 214,15 | 0,86 | 0,625 | 0,656 |
| GAMEPLAY floodplain_1 | 197,00 | 112,40 | 0,571 | 138,10 | 1,66 | 0,576 | 0,569 |
| GAMEPLAY montane_spruce_1 | 157,85 | 73,90 | 0,468 | 113,70 | 4,74 | 0,461 | 0,451 |

## FEATURES, ms na chunk (`stages -Psites=slice -Pcaves=off`, 20 obszarów miejsc wycinka); próg ≤ 2,0 × M2 i ≤ 10 ms

| obszar | M2 | OP1 (2026-11-27) | OP2 | OP2 / M2 | OP2 / OP1 |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 5,09 | 8,02 | 8,24 | 1,618 | 1,028 |
| REAL oak_hornbeam_2 | 5,12 | 6,95 | 7,06 | 1,380 | 1,016 |
| REAL fresh_pine_1 | 5,34 | 7,66 | 7,64 | 1,430 | 0,997 |
| REAL fresh_pine_2 | 4,62 | 6,53 | 6,56 | 1,419 | 1,005 |
| REAL transect | 4,80 | 6,78 | 6,71 | 1,397 | 0,990 |
| GAMEPLAY oak_hornbeam_1 | 4,81 | 8,09 | 8,11 | 1,685 | 1,002 |
| GAMEPLAY oak_hornbeam_2 | 5,79 | 8,55 | 8,63 | 1,491 | 1,010 |
| GAMEPLAY fresh_pine_1 | 5,17 | 7,59 | 7,45 | 1,441 | 0,982 |
| GAMEPLAY fresh_pine_2 | 4,81 | 7,48 | 7,49 | 1,557 | 1,002 |
| GAMEPLAY transect | 4,99 | 7,30 | 7,20 | 1,441 | 0,986 |
| REAL D oak_hornbeam_1 | 5,06 | 8,12 | 7,98 | 1,576 | 0,982 |
| REAL D oak_hornbeam_2 | 5,02 | 7,07 | 7,01 | 1,395 | 0,991 |
| REAL D fresh_pine_1 | 5,42 | 7,34 | 7,48 | 1,380 | 1,020 |
| REAL D fresh_pine_2 | 4,51 | 6,55 | 6,60 | 1,464 | 1,008 |
| REAL D transect | 4,90 | 6,75 | 6,81 | 1,391 | 1,009 |
| GAMEPLAY D oak_hornbeam_1 | 4,80 | 7,76 | 7,93 | 1,650 | 1,022 |
| GAMEPLAY D oak_hornbeam_2 | 6,08 | 8,70 | 8,70 | 1,432 | 1,000 |
| GAMEPLAY D fresh_pine_1 | 5,55 | 7,25 | 7,32 | 1,320 | 1,010 |
| GAMEPLAY D fresh_pine_2 | 4,92 | 7,41 | 7,45 | 1,513 | 1,005 |
| GAMEPLAY D transect | 5,21 | 6,80 | 6,84 | 1,313 | 1,006 |

## LIGHT, ms na chunk (`stages -Psites=slice -Pcaves=off`, 20 obszarów miejsc wycinka); próg ≤ 1,75 × M2 i ≤ 12 ms

| obszar | M2 | OP1 (2026-11-27) | OP2 | OP2 / M2 | OP2 / OP1 |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 7,00 | 9,72 | 9,78 | 1,397 | 1,006 |
| REAL oak_hornbeam_2 | 7,50 | 9,19 | 9,18 | 1,224 | 0,999 |
| REAL fresh_pine_1 | 6,51 | 8,74 | 9,10 | 1,399 | 1,041 |
| REAL fresh_pine_2 | 6,65 | 9,51 | 9,03 | 1,358 | 0,950 |
| REAL transect | 7,88 | 8,75 | 8,81 | 1,119 | 1,007 |
| GAMEPLAY oak_hornbeam_1 | 6,33 | 9,38 | 9,48 | 1,498 | 1,010 |
| GAMEPLAY oak_hornbeam_2 | 7,63 | 9,91 | 9,97 | 1,306 | 1,006 |
| GAMEPLAY fresh_pine_1 | 6,60 | 9,09 | 9,02 | 1,367 | 0,993 |
| GAMEPLAY fresh_pine_2 | 6,81 | 9,26 | 9,12 | 1,338 | 0,985 |
| GAMEPLAY transect | 6,83 | 8,61 | 8,98 | 1,316 | 1,043 |
| REAL D oak_hornbeam_1 | 6,75 | 9,59 | 9,54 | 1,413 | 0,995 |
| REAL D oak_hornbeam_2 | 6,89 | 9,18 | 9,63 | 1,398 | 1,049 |
| REAL D fresh_pine_1 | 7,02 | 8,54 | 8,49 | 1,209 | 0,994 |
| REAL D fresh_pine_2 | 6,41 | 8,54 | 8,66 | 1,351 | 1,015 |
| REAL D transect | 6,73 | 8,88 | 9,22 | 1,369 | 1,038 |
| GAMEPLAY D oak_hornbeam_1 | 6,27 | 9,14 | 9,63 | 1,537 | 1,054 |
| GAMEPLAY D oak_hornbeam_2 | 7,51 | 9,86 | 10,01 | 1,333 | 1,015 |
| GAMEPLAY D fresh_pine_1 | 6,68 | 8,84 | 9,00 | 1,347 | 1,018 |
| GAMEPLAY D fresh_pine_2 | 6,77 | 9,07 | 9,08 | 1,342 | 1,001 |
| GAMEPLAY D transect | 6,94 | 8,64 | 8,56 | 1,234 | 0,991 |

## cały chunk, ms na chunk (`stages -Psites=slice -Pcaves=off`, 20 obszarów miejsc wycinka); próg ≤ 48 ms

| obszar | M2 | OP1 (2026-11-27) | OP2 | OP2 / M2 | OP2 / OP1 |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 17,48 | 22,98 | 23,40 | 1,339 | 1,018 |
| REAL oak_hornbeam_2 | 18,45 | 22,63 | 22,65 | 1,227 | 1,001 |
| REAL fresh_pine_1 | 17,32 | 21,69 | 21,90 | 1,264 | 1,010 |
| REAL fresh_pine_2 | 16,33 | 21,20 | 20,82 | 1,275 | 0,982 |
| REAL transect | 17,87 | 20,98 | 20,77 | 1,162 | 0,990 |
| GAMEPLAY oak_hornbeam_1 | 16,43 | 22,20 | 22,49 | 1,369 | 1,013 |
| GAMEPLAY oak_hornbeam_2 | 20,82 | 25,52 | 26,16 | 1,256 | 1,025 |
| GAMEPLAY fresh_pine_1 | 17,93 | 22,45 | 22,46 | 1,253 | 1,000 |
| GAMEPLAY fresh_pine_2 | 18,18 | 23,05 | 23,09 | 1,270 | 1,002 |
| GAMEPLAY transect | 17,42 | 21,64 | 22,59 | 1,297 | 1,044 |
| REAL D oak_hornbeam_1 | 17,32 | 22,85 | 23,05 | 1,330 | 1,009 |
| REAL D oak_hornbeam_2 | 17,84 | 21,86 | 22,44 | 1,258 | 1,026 |
| REAL D fresh_pine_1 | 18,47 | 21,27 | 21,48 | 1,163 | 1,010 |
| REAL D fresh_pine_2 | 16,06 | 19,99 | 20,20 | 1,257 | 1,010 |
| REAL D transect | 17,16 | 21,81 | 21,62 | 1,260 | 0,991 |
| GAMEPLAY D oak_hornbeam_1 | 16,35 | 21,78 | 22,55 | 1,379 | 1,035 |
| GAMEPLAY D oak_hornbeam_2 | 21,36 | 25,73 | 26,55 | 1,243 | 1,032 |
| GAMEPLAY D fresh_pine_1 | 18,31 | 22,16 | 22,20 | 1,212 | 1,002 |
| GAMEPLAY D fresh_pine_2 | 18,29 | 23,10 | 22,91 | 1,252 | 0,992 |
| GAMEPLAY D transect | 18,45 | 21,21 | 21,27 | 1,153 | 1,003 |

## Miejsca Tatr (KT-6, opis bez progu; M2 nie ma Tatr)

| miejsce | chunki/s bez jaskiń | chunki/s z jaskiniami | koszt jaskiń, ms na chunk |
|---|---|---|---|
| GAMEPLAY tatra_high_1 | 177,65 | 76,60 | 7,43 |
| GAMEPLAY tatra_regle_1 | 152,65 | 70,85 | 7,56 |
| GAMEPLAY tatra_stone_pine_1 | 162,90 | 70,95 | 7,96 |
| REAL tatra_high_1 | 140,90 | 77,45 | 5,81 |
| REAL tatra_regle_1 | 106,90 | 59,55 | 7,44 |
| REAL tatra_stone_pine_1 | 115,85 | 53,00 | 10,24 |

## Werdykty progów (PerformanceThresholds, CP-D.5)

| metryka | PASS | FAIL | ACCEPTED | REPORT | DESCRIBE |
|---|---|---|---|---|---|
| CHUNKS_NO_CAVES | 10 | 6 | 0 | 0 | 0 |
| CHUNKS_WITH_CAVES | 16 | 0 | 0 | 0 | 0 |
| FEATURES | 20 | 0 | 0 | 0 | 0 |
| LIGHT | 20 | 0 | 0 | 0 | 0 |
| WHOLE_CHUNK | 20 | 0 | 0 | 0 | 0 |

| metryka | skala | miejsce | wartość | próg | werdykt | reguła |
|---|---|---|---|---|---|---|
| CHUNKS_NO_CAVES | REALISTIC | oak_hornbeam_1 | 0,698 × | 0,700 | **FAIL** | forest norm >= 0.70 |
| CHUNKS_NO_CAVES | GAMEPLAY | oak_hornbeam_1 | 0,672 × | 0,700 | **FAIL** | forest norm >= 0.70 |
| CHUNKS_NO_CAVES | GAMEPLAY | fresh_pine_1 | 0,683 × | 0,700 | **FAIL** | forest norm >= 0.70 |
| CHUNKS_NO_CAVES | GAMEPLAY | lowland_beech_1 | 0,686 × | 0,700 | **FAIL** | forest norm >= 0.70 |
| CHUNKS_NO_CAVES | GAMEPLAY | alder_carr_1 | 0,696 × | 0,700 | **FAIL** | forest norm >= 0.70 |
| CHUNKS_NO_CAVES | GAMEPLAY | montane_spruce_1 | 0,699 × | 0,700 | **FAIL** | forest norm >= 0.70 |
| CHUNKS_NO_CAVES | REALISTIC | fresh_pine_1 | 0,752 × | 0,700 | PASS | forest norm >= 0.70, target 0.75 met |
| CHUNKS_NO_CAVES | REALISTIC | lowland_beech_1 | 0,720 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | REALISTIC | alder_carr_1 | 0,717 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | REALISTIC | upland_fir_1 | 0,714 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | REALISTIC | bog_woodland_1 | 0,724 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | REALISTIC | floodplain_1 | 0,730 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | REALISTIC | montane_spruce_1 | 0,740 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | GAMEPLAY | upland_fir_1 | 0,713 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | GAMEPLAY | bog_woodland_1 | 0,748 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | GAMEPLAY | floodplain_1 | 0,711 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_WITH_CAVES | REALISTIC | oak_hornbeam_1 | 0,525 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | fresh_pine_1 | 0,416 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | lowland_beech_1 | 0,516 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | alder_carr_1 | 0,581 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | upland_fir_1 | 0,391 × | 0,350 | PASS | mountain guard with caves >= 0.35 |
| CHUNKS_WITH_CAVES | REALISTIC | bog_woodland_1 | 0,474 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | floodplain_1 | 0,622 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | montane_spruce_1 | 0,402 × | 0,350 | PASS | mountain guard with caves >= 0.35 |
| CHUNKS_WITH_CAVES | GAMEPLAY | oak_hornbeam_1 | 0,574 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | fresh_pine_1 | 0,575 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | lowland_beech_1 | 0,568 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | alder_carr_1 | 0,578 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | upland_fir_1 | 0,585 × | 0,450 | PASS | mountain guard with caves >= 0.45 |
| CHUNKS_WITH_CAVES | GAMEPLAY | bog_woodland_1 | 0,625 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | floodplain_1 | 0,571 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | montane_spruce_1 | 0,468 × | 0,450 | PASS | mountain guard with caves >= 0.45 |
| FEATURES | REALISTIC | oak_hornbeam_1 | 8,24 ms (1,618 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | oak_hornbeam_2 | 7,06 ms (1,380 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | fresh_pine_1 | 7,64 ms (1,430 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | fresh_pine_2 | 6,56 ms (1,419 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | transect | 6,71 ms (1,397 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | oak_hornbeam_1 | 8,11 ms (1,685 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | oak_hornbeam_2 | 8,63 ms (1,491 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | fresh_pine_1 | 7,45 ms (1,441 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | fresh_pine_2 | 7,49 ms (1,557 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | transect | 7,20 ms (1,441 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | oak_hornbeam_1_d | 7,98 ms (1,576 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | oak_hornbeam_2_d | 7,01 ms (1,395 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | fresh_pine_1_d | 7,48 ms (1,380 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | fresh_pine_2_d | 6,60 ms (1,464 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | transect_d | 6,81 ms (1,391 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | oak_hornbeam_1_d | 7,93 ms (1,650 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | oak_hornbeam_2_d | 8,70 ms (1,432 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | fresh_pine_1_d | 7,32 ms (1,320 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | fresh_pine_2_d | 7,45 ms (1,513 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | transect_d | 6,84 ms (1,313 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| LIGHT | REALISTIC | oak_hornbeam_1 | 9,78 ms (1,397 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | oak_hornbeam_2 | 9,18 ms (1,224 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | fresh_pine_1 | 9,10 ms (1,399 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | fresh_pine_2 | 9,03 ms (1,358 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | transect | 8,81 ms (1,119 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | oak_hornbeam_1 | 9,48 ms (1,498 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | oak_hornbeam_2 | 9,97 ms (1,306 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | fresh_pine_1 | 9,02 ms (1,367 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | fresh_pine_2 | 9,12 ms (1,338 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | transect | 8,98 ms (1,316 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | oak_hornbeam_1_d | 9,54 ms (1,413 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | oak_hornbeam_2_d | 9,63 ms (1,398 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | fresh_pine_1_d | 8,49 ms (1,209 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | fresh_pine_2_d | 8,66 ms (1,351 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | transect_d | 9,22 ms (1,369 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | oak_hornbeam_1_d | 9,63 ms (1,537 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | oak_hornbeam_2_d | 10,01 ms (1,333 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | fresh_pine_1_d | 9,00 ms (1,347 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | fresh_pine_2_d | 9,08 ms (1,342 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | transect_d | 8,56 ms (1,234 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| WHOLE_CHUNK | REALISTIC | oak_hornbeam_1 | 23,40 ms (1,339 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | oak_hornbeam_2 | 22,65 ms (1,227 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | fresh_pine_1 | 21,90 ms (1,264 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | fresh_pine_2 | 20,82 ms (1,275 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | transect | 20,77 ms (1,162 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | oak_hornbeam_1 | 22,49 ms (1,369 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | oak_hornbeam_2 | 26,16 ms (1,256 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | fresh_pine_1 | 22,46 ms (1,253 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | fresh_pine_2 | 23,09 ms (1,270 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | transect | 22,59 ms (1,297 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | oak_hornbeam_1_d | 23,05 ms (1,330 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | oak_hornbeam_2_d | 22,44 ms (1,258 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | fresh_pine_1_d | 21,48 ms (1,163 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | fresh_pine_2_d | 20,20 ms (1,257 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | transect_d | 21,62 ms (1,260 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | oak_hornbeam_1_d | 22,55 ms (1,379 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | oak_hornbeam_2_d | 26,55 ms (1,243 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | fresh_pine_1_d | 22,20 ms (1,212 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | fresh_pine_2_d | 22,91 ms (1,252 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | transect_d | 21,27 ms (1,153 ×) | 48,000 | PASS | whole chunk <= 48 ms |
