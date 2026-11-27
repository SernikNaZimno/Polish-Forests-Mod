# OP1: tabele A/B (PW-10, G.34, CP-D.5)

Krok OP1 (`docs/04` „Projekt OP1”, „Stan po OP1”): pamięci generacji bez zmiany świata. Przebiegi 2026-11-26 23:38 … 2026-11-27 03:07, sterownik `handoff/OP1/run_ab_op1.sh` (wzór FA10, te same funkcje ciszy i próbnik w trakcie), ABBA (rundy nieparzyste M2 pierwszy), ta sama uprząż `ForestPerformance` w obu buildach. Budowy: M2 = `pl-m2-baseline` przy `858965f` z łatką uprzęży FA10-f, FA10 = `121dbbf` (worktree `pl-pre-op1`, tylko etapy), OP1 = `d1d6a18`. Rundy: `load` bez jaskiń 6, z jaskiniami 4, `stages` 4 (kolejność M2 → FA10 → OP1, potem odwrotnie). Warunek ciszy: brak gry użytkownika i obcego programu java, CPU < 20 %, GPU ≤ 5 % karty (3 próbki co 20 s przed przebiegiem, próbnik co 20 s w trakcie). Czekanie łącznie 918 s (na grę użytkownika 0 s); jeden przebieg (`stages_r1` buildu M2) przerwał obcy `java.exe`, poszedł do `ab/disturbed/` i został powtórzony. Żaden przebieg nie używał `-PtimingsReportOnly`.

Kolumna „FA10 (2026-11-26)” to wynik buildu FA10 z jego własnej sesji (`docs/wycinek/v17/ab_v17.md`, inne przebiegi M2); porównanie OP1 z FA10 w tej samej sesji (etapy) jest w `stages_op1_vs_fa10.md` obok. Przelotów FPS nie było: kod klienta i renderu bez zmian od bazy kroku (`git diff 121dbbf -- src/client` pusty), więc tabela FPS odpada (wiersze FPS 0).


## Chunki na sekundę z C2ME bez jaskiń (`-Pcaves=off`, `-PloadOnly`); próg ≥ 0,7 × M2

| miejsce | M2 | OP1 | iloraz | ilorazy rund (min–max) | FA10 (2026-11-26) |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 146,85 | 105,05 | 0,715 | 0,688–0,750 | 0,756 |
| REAL fresh_pine_1 | 190,90 | 136,35 | 0,714 | 0,666–0,755 | 0,736 |
| REAL lowland_beech_1 | 189,85 | 129,25 | 0,681 | 0,659–0,689 | 0,691 |
| REAL alder_carr_1 | 182,00 | 134,90 | 0,741 | 0,720–0,766 | 0,790 |
| REAL upland_fir_1 | 165,20 | 118,80 | 0,719 | 0,694–0,751 | 0,741 |
| REAL bog_woodland_1 | 183,05 | 139,60 | 0,763 | 0,727–0,789 | 0,759 |
| REAL floodplain_1 | 181,90 | 129,80 | 0,714 | 0,688–0,727 | 0,715 |
| REAL montane_spruce_1 | 162,75 | 119,70 | 0,735 | 0,728–0,751 | 0,797 |
| GAMEPLAY oak_hornbeam_1 | 218,45 | 147,85 | 0,677 | 0,652–0,706 | 0,692 |
| GAMEPLAY fresh_pine_1 | 211,70 | 151,85 | 0,717 | 0,697–0,758 | 0,702 |
| GAMEPLAY lowland_beech_1 | 209,45 | 151,15 | 0,722 | 0,703–0,727 | 0,754 |
| GAMEPLAY alder_carr_1 | 241,80 | 168,85 | 0,698 | 0,676–0,740 | 0,697 |
| GAMEPLAY upland_fir_1 | 186,80 | 139,90 | 0,749 | 0,733–0,784 | 0,754 |
| GAMEPLAY bog_woodland_1 | 310,35 | 224,90 | 0,725 | 0,702–0,768 | 0,750 |
| GAMEPLAY floodplain_1 | 212,75 | 153,05 | 0,719 | 0,683–0,755 | 0,705 |
| GAMEPLAY montane_spruce_1 | 177,30 | 124,50 | 0,702 | 0,685–0,713 | 0,729 |
| **min / mediana / max** | | | 0,677 / 0,718 / 0,763 | | |

## Strażnik z jaskiniami: chunki na sekundę z C2ME (świat z jaskiniami wobec M2 bez jaskiń); próg REAL ≥ 0,4 ×, GAMEPLAY ≥ 0,55 ×, lasy górskie REAL ≥ 0,35 ×, GAMEPLAY ≥ 0,45 ×

| miejsce | M2 | OP1 | iloraz | OP1 bez jaskiń | koszt jaskiń OP1, ms na chunk | iloraz wobec M2 ze wszystkich przebiegów | FA10 |
|---|---|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 145,35 | 77,45 | 0,533 | 105,05 | 3,39 | 0,530 | 0,540 |
| REAL fresh_pine_1 | 187,20 | 80,05 | 0,428 | 136,35 | 5,16 | 0,420 | 0,427 |
| REAL lowland_beech_1 | 188,75 | 101,80 | 0,539 | 129,25 | 2,09 | 0,536 | 0,528 |
| REAL alder_carr_1 | 175,15 | 108,55 | 0,620 | 134,90 | 1,80 | 0,602 | 0,599 |
| REAL upland_fir_1 | 163,80 | 63,95 | 0,390 | 118,80 | 7,22 | 0,390 | 0,385 |
| REAL bog_woodland_1 | 181,05 | 85,35 | 0,471 | 139,60 | 4,55 | 0,466 | 0,482 |
| REAL floodplain_1 | 183,35 | 108,90 | 0,594 | 129,80 | 1,48 | 0,596 | 0,599 |
| REAL montane_spruce_1 | 160,05 | 65,10 | 0,407 | 119,70 | 7,01 | 0,404 | 0,412 |
| GAMEPLAY oak_hornbeam_1 | 219,25 | 123,70 | 0,564 | 147,85 | 1,32 | 0,566 | 0,599 |
| GAMEPLAY fresh_pine_1 | 206,90 | 129,95 | 0,628 | 151,85 | 1,11 | 0,620 | 0,590 |
| GAMEPLAY lowland_beech_1 | 205,35 | 124,50 | 0,606 | 151,15 | 1,42 | 0,596 | 0,591 |
| GAMEPLAY alder_carr_1 | 240,40 | 148,35 | 0,617 | 168,85 | 0,82 | 0,615 | 0,600 |
| GAMEPLAY upland_fir_1 | 188,90 | 113,55 | 0,601 | 139,90 | 1,66 | 0,605 | 0,575 |
| GAMEPLAY bog_woodland_1 | 307,75 | 201,90 | 0,656 | 224,90 | 0,51 | 0,655 | 0,679 |
| GAMEPLAY floodplain_1 | 209,20 | 119,05 | 0,569 | 153,05 | 1,87 | 0,563 | 0,543 |
| GAMEPLAY montane_spruce_1 | 174,75 | 78,85 | 0,451 | 124,50 | 4,65 | 0,447 | 0,461 |

## FEATURES, ms na chunk (`stages -Psites=slice -Pcaves=off`, 20 obszarów miejsc wycinka); próg ≤ 2,0 × M2 i ≤ 10 ms

| obszar | M2 | FA10 (2026-11-26) | OP1 | OP1 / M2 | OP1 / FA10 |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 4,72 | 7,88 | 8,02 | 1,700 | 1,018 |
| REAL oak_hornbeam_2 | 4,75 | 6,86 | 6,95 | 1,462 | 1,012 |
| REAL fresh_pine_1 | 5,09 | 7,52 | 7,66 | 1,503 | 1,018 |
| REAL fresh_pine_2 | 4,28 | 6,49 | 6,53 | 1,526 | 1,006 |
| REAL transect | 4,55 | 6,75 | 6,78 | 1,489 | 1,005 |
| GAMEPLAY oak_hornbeam_1 | 4,77 | 7,94 | 8,09 | 1,694 | 1,018 |
| GAMEPLAY oak_hornbeam_2 | 5,64 | 8,57 | 8,55 | 1,515 | 0,997 |
| GAMEPLAY fresh_pine_1 | 4,98 | 7,62 | 7,59 | 1,524 | 0,997 |
| GAMEPLAY fresh_pine_2 | 4,73 | 7,37 | 7,48 | 1,579 | 1,014 |
| GAMEPLAY transect | 4,80 | 7,14 | 7,30 | 1,520 | 1,023 |
| REAL D oak_hornbeam_1 | 4,81 | 7,95 | 8,12 | 1,688 | 1,022 |
| REAL D oak_hornbeam_2 | 4,78 | 6,84 | 7,07 | 1,479 | 1,034 |
| REAL D fresh_pine_1 | 5,26 | 7,54 | 7,34 | 1,397 | 0,974 |
| REAL D fresh_pine_2 | 4,20 | 6,42 | 6,55 | 1,562 | 1,021 |
| REAL D transect | 4,73 | 6,78 | 6,75 | 1,426 | 0,996 |
| GAMEPLAY D oak_hornbeam_1 | 4,73 | 7,70 | 7,76 | 1,639 | 1,008 |
| GAMEPLAY D oak_hornbeam_2 | 5,61 | 8,62 | 8,70 | 1,550 | 1,009 |
| GAMEPLAY D fresh_pine_1 | 5,02 | 7,37 | 7,25 | 1,443 | 0,984 |
| GAMEPLAY D fresh_pine_2 | 4,73 | 7,43 | 7,41 | 1,567 | 0,997 |
| GAMEPLAY D transect | 4,52 | 6,90 | 6,80 | 1,503 | 0,985 |

## LIGHT, ms na chunk (`stages -Psites=slice -Pcaves=off`, 20 obszarów miejsc wycinka); próg ≤ 1,75 × M2 i ≤ 12 ms

| obszar | M2 | FA10 (2026-11-26) | OP1 | OP1 / M2 | OP1 / FA10 |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 6,73 | 9,86 | 9,72 | 1,443 | 0,986 |
| REAL oak_hornbeam_2 | 6,98 | 9,66 | 9,19 | 1,317 | 0,951 |
| REAL fresh_pine_1 | 6,75 | 8,84 | 8,74 | 1,295 | 0,989 |
| REAL fresh_pine_2 | 6,51 | 9,57 | 9,51 | 1,461 | 0,994 |
| REAL transect | 6,61 | 8,82 | 8,75 | 1,324 | 0,992 |
| GAMEPLAY oak_hornbeam_1 | 6,51 | 9,25 | 9,38 | 1,441 | 1,014 |
| GAMEPLAY oak_hornbeam_2 | 7,35 | 10,02 | 9,91 | 1,348 | 0,989 |
| GAMEPLAY fresh_pine_1 | 6,52 | 8,92 | 9,09 | 1,394 | 1,019 |
| GAMEPLAY fresh_pine_2 | 6,84 | 9,21 | 9,26 | 1,353 | 1,005 |
| GAMEPLAY transect | 6,74 | 8,67 | 8,61 | 1,277 | 0,993 |
| REAL D oak_hornbeam_1 | 6,77 | 9,77 | 9,59 | 1,418 | 0,982 |
| REAL D oak_hornbeam_2 | 6,80 | 9,52 | 9,18 | 1,351 | 0,964 |
| REAL D fresh_pine_1 | 6,31 | 8,54 | 8,54 | 1,353 | 1,000 |
| REAL D fresh_pine_2 | 6,45 | 8,51 | 8,54 | 1,325 | 1,003 |
| REAL D transect | 6,52 | 8,76 | 8,88 | 1,362 | 1,014 |
| GAMEPLAY D oak_hornbeam_1 | 6,39 | 9,16 | 9,14 | 1,430 | 0,998 |
| GAMEPLAY D oak_hornbeam_2 | 7,45 | 9,84 | 9,86 | 1,324 | 1,002 |
| GAMEPLAY D fresh_pine_1 | 6,57 | 8,89 | 8,84 | 1,346 | 0,995 |
| GAMEPLAY D fresh_pine_2 | 6,77 | 9,00 | 9,07 | 1,339 | 1,008 |
| GAMEPLAY D transect | 6,56 | 8,59 | 8,64 | 1,317 | 1,006 |

## cały chunk, ms na chunk (`stages -Psites=slice -Pcaves=off`, 20 obszarów miejsc wycinka); próg ≤ 48 ms

| obszar | M2 | FA10 (2026-11-26) | OP1 | OP1 / M2 | OP1 / FA10 |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 16,66 | 23,27 | 22,98 | 1,379 | 0,987 |
| REAL oak_hornbeam_2 | 17,24 | 22,20 | 22,63 | 1,313 | 1,020 |
| REAL fresh_pine_1 | 16,93 | 21,49 | 21,69 | 1,281 | 1,009 |
| REAL fresh_pine_2 | 16,45 | 20,73 | 21,20 | 1,289 | 1,023 |
| REAL transect | 16,34 | 21,16 | 20,98 | 1,284 | 0,991 |
| GAMEPLAY oak_hornbeam_1 | 16,50 | 22,01 | 22,20 | 1,345 | 1,009 |
| GAMEPLAY oak_hornbeam_2 | 20,17 | 26,15 | 25,52 | 1,265 | 0,976 |
| GAMEPLAY fresh_pine_1 | 17,52 | 22,49 | 22,45 | 1,281 | 0,998 |
| GAMEPLAY fresh_pine_2 | 17,96 | 22,88 | 23,05 | 1,283 | 1,007 |
| GAMEPLAY transect | 17,18 | 21,67 | 21,64 | 1,260 | 0,999 |
| REAL D oak_hornbeam_1 | 17,06 | 22,80 | 22,85 | 1,339 | 1,002 |
| REAL D oak_hornbeam_2 | 17,39 | 22,02 | 21,86 | 1,256 | 0,993 |
| REAL D fresh_pine_1 | 17,16 | 21,29 | 21,27 | 1,239 | 0,999 |
| REAL D fresh_pine_2 | 15,82 | 19,80 | 19,99 | 1,264 | 1,010 |
| REAL D transect | 16,73 | 20,84 | 21,81 | 1,303 | 1,047 |
| GAMEPLAY D oak_hornbeam_1 | 16,03 | 21,71 | 21,78 | 1,359 | 1,003 |
| GAMEPLAY D oak_hornbeam_2 | 20,22 | 25,69 | 25,73 | 1,273 | 1,002 |
| GAMEPLAY D fresh_pine_1 | 17,66 | 22,09 | 22,16 | 1,254 | 1,003 |
| GAMEPLAY D fresh_pine_2 | 17,79 | 22,66 | 23,10 | 1,299 | 1,019 |
| GAMEPLAY D transect | 16,73 | 21,12 | 21,21 | 1,268 | 1,004 |

## Miejsca Tatr (KT-6, opis bez progu; M2 nie ma Tatr)

| miejsce | chunki/s bez jaskiń | chunki/s z jaskiniami | koszt jaskiń, ms na chunk |
|---|---|---|---|
| GAMEPLAY tatra_high_1 | 186,60 | 80,75 | 7,02 |
| GAMEPLAY tatra_regle_1 | 168,45 | 75,65 | 7,28 |
| GAMEPLAY tatra_stone_pine_1 | 171,50 | 76,70 | 7,21 |
| REAL tatra_high_1 | 145,80 | 84,75 | 4,94 |
| REAL tatra_regle_1 | 120,30 | 64,75 | 7,13 |
| REAL tatra_stone_pine_1 | 125,80 | 56,00 | 9,91 |

## Werdykty progów (PerformanceThresholds, CP-D.5)

| metryka | PASS | FAIL | ACCEPTED | REPORT | DESCRIBE |
|---|---|---|---|---|---|
| CHUNKS_NO_CAVES | 13 | 3 | 0 | 0 | 0 |
| CHUNKS_WITH_CAVES | 16 | 0 | 0 | 0 | 0 |
| FEATURES | 20 | 0 | 0 | 0 | 0 |
| LIGHT | 20 | 0 | 0 | 0 | 0 |
| WHOLE_CHUNK | 20 | 0 | 0 | 0 | 0 |

| metryka | skala | miejsce | wartość | próg | werdykt | reguła |
|---|---|---|---|---|---|---|
| CHUNKS_NO_CAVES | REALISTIC | lowland_beech_1 | 0,681 × | 0,700 | **FAIL** | forest norm >= 0.70 |
| CHUNKS_NO_CAVES | GAMEPLAY | oak_hornbeam_1 | 0,677 × | 0,700 | **FAIL** | forest norm >= 0.70 |
| CHUNKS_NO_CAVES | GAMEPLAY | alder_carr_1 | 0,698 × | 0,700 | **FAIL** | forest norm >= 0.70 |
| CHUNKS_NO_CAVES | REALISTIC | oak_hornbeam_1 | 0,715 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | REALISTIC | fresh_pine_1 | 0,714 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | REALISTIC | alder_carr_1 | 0,741 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | REALISTIC | upland_fir_1 | 0,719 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | REALISTIC | bog_woodland_1 | 0,763 × | 0,700 | PASS | forest norm >= 0.70, target 0.75 met |
| CHUNKS_NO_CAVES | REALISTIC | floodplain_1 | 0,714 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | REALISTIC | montane_spruce_1 | 0,735 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | GAMEPLAY | fresh_pine_1 | 0,717 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | GAMEPLAY | lowland_beech_1 | 0,722 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | GAMEPLAY | upland_fir_1 | 0,749 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | GAMEPLAY | bog_woodland_1 | 0,725 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | GAMEPLAY | floodplain_1 | 0,719 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | GAMEPLAY | montane_spruce_1 | 0,702 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_WITH_CAVES | REALISTIC | oak_hornbeam_1 | 0,533 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | fresh_pine_1 | 0,428 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | lowland_beech_1 | 0,539 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | alder_carr_1 | 0,620 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | upland_fir_1 | 0,390 × | 0,350 | PASS | mountain guard with caves >= 0.35 |
| CHUNKS_WITH_CAVES | REALISTIC | bog_woodland_1 | 0,471 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | floodplain_1 | 0,594 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | montane_spruce_1 | 0,407 × | 0,350 | PASS | mountain guard with caves >= 0.35 |
| CHUNKS_WITH_CAVES | GAMEPLAY | oak_hornbeam_1 | 0,564 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | fresh_pine_1 | 0,628 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | lowland_beech_1 | 0,606 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | alder_carr_1 | 0,617 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | upland_fir_1 | 0,601 × | 0,450 | PASS | mountain guard with caves >= 0.45 |
| CHUNKS_WITH_CAVES | GAMEPLAY | bog_woodland_1 | 0,656 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | floodplain_1 | 0,569 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | montane_spruce_1 | 0,451 × | 0,450 | PASS | mountain guard with caves >= 0.45 |
| FEATURES | REALISTIC | oak_hornbeam_1 | 8,02 ms (1,700 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | oak_hornbeam_2 | 6,95 ms (1,462 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | fresh_pine_1 | 7,66 ms (1,503 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | fresh_pine_2 | 6,53 ms (1,526 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | transect | 6,78 ms (1,489 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | oak_hornbeam_1 | 8,09 ms (1,694 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | oak_hornbeam_2 | 8,55 ms (1,515 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | fresh_pine_1 | 7,59 ms (1,524 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | fresh_pine_2 | 7,48 ms (1,579 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | transect | 7,30 ms (1,520 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | oak_hornbeam_1_d | 8,12 ms (1,688 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | oak_hornbeam_2_d | 7,07 ms (1,479 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | fresh_pine_1_d | 7,34 ms (1,397 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | fresh_pine_2_d | 6,55 ms (1,562 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | transect_d | 6,75 ms (1,426 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | oak_hornbeam_1_d | 7,76 ms (1,639 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | oak_hornbeam_2_d | 8,70 ms (1,550 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | fresh_pine_1_d | 7,25 ms (1,443 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | fresh_pine_2_d | 7,41 ms (1,567 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | transect_d | 6,80 ms (1,503 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| LIGHT | REALISTIC | oak_hornbeam_1 | 9,72 ms (1,443 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | oak_hornbeam_2 | 9,19 ms (1,317 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | fresh_pine_1 | 8,74 ms (1,295 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | fresh_pine_2 | 9,51 ms (1,461 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | transect | 8,75 ms (1,324 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | oak_hornbeam_1 | 9,38 ms (1,441 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | oak_hornbeam_2 | 9,91 ms (1,348 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | fresh_pine_1 | 9,09 ms (1,394 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | fresh_pine_2 | 9,26 ms (1,353 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | transect | 8,61 ms (1,277 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | oak_hornbeam_1_d | 9,59 ms (1,418 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | oak_hornbeam_2_d | 9,18 ms (1,351 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | fresh_pine_1_d | 8,54 ms (1,353 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | fresh_pine_2_d | 8,54 ms (1,325 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | transect_d | 8,88 ms (1,362 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | oak_hornbeam_1_d | 9,14 ms (1,430 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | oak_hornbeam_2_d | 9,86 ms (1,324 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | fresh_pine_1_d | 8,84 ms (1,346 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | fresh_pine_2_d | 9,07 ms (1,339 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | transect_d | 8,64 ms (1,317 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| WHOLE_CHUNK | REALISTIC | oak_hornbeam_1 | 22,98 ms (1,379 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | oak_hornbeam_2 | 22,63 ms (1,313 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | fresh_pine_1 | 21,69 ms (1,281 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | fresh_pine_2 | 21,20 ms (1,289 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | transect | 20,98 ms (1,284 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | oak_hornbeam_1 | 22,20 ms (1,345 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | oak_hornbeam_2 | 25,52 ms (1,265 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | fresh_pine_1 | 22,45 ms (1,281 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | fresh_pine_2 | 23,05 ms (1,283 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | transect | 21,64 ms (1,260 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | oak_hornbeam_1_d | 22,85 ms (1,339 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | oak_hornbeam_2_d | 21,86 ms (1,256 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | fresh_pine_1_d | 21,27 ms (1,239 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | fresh_pine_2_d | 19,99 ms (1,264 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | transect_d | 21,81 ms (1,303 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | oak_hornbeam_1_d | 21,78 ms (1,359 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | oak_hornbeam_2_d | 25,73 ms (1,273 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | fresh_pine_1_d | 22,16 ms (1,254 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | fresh_pine_2_d | 23,10 ms (1,299 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | transect_d | 21,21 ms (1,268 ×) | 48,000 | PASS | whole chunk <= 48 ms |
