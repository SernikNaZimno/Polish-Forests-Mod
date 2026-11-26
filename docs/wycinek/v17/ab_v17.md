# FA10: tabele A/B (PW-10, G.34, CP-D.5)

Daty: 2026-11-25 23:06 do 2026-11-26 08:34, runda 3 przelotów z fauną i bez 2026-11-26 14:20 do 15:06. Buildy: M2 = pl-m2-baseline przy 858965f z łatką uprzęży (migawki/m2-baseline-r1-harness.patch), FA10 = 86686ed (etap 1; runda 3 przelotów z fauną z poprawką liczenia zwierząt FA10-g, bez zmiany kodu moda).
Rundy: 6 / 4 / 4 / 2 / 3 / 2 (load, jaskinie, etapy, MSPT, przeloty z fauną, przeloty FPS; patrz runs.md). Warunek ciszy: brak gier, CPU < 20 %, GPU <= 5 %. Czas oczekiwania łącznie 438 s (417 + 21). Przebiegi zakłócone: 2 (powtórzone, w ab/disturbed). Liczby zwierząt w obszarze przelotu tylko z rundy 3 (w rundach 1 i 2 liczenie szło przed przelotami, poza widokiem obszaru, FA10-g).


## Chunki na sekundę z C2ME bez jaskiń (`-Pcaves=off`, `-PloadOnly`); próg ≥ 0,7 × M2

| miejsce | M2 | FA10 | iloraz | ilorazy rund (min–max) | F7 (2026-11-11) |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 139,00 | 105,10 | 0,756 | 0,695–0,785 | 0,714 |
| REAL fresh_pine_1 | 187,00 | 137,70 | 0,736 | 0,687–0,750 | 0,735 |
| REAL lowland_beech_1 | 189,75 | 131,15 | 0,691 | 0,667–0,704 | 0,698 |
| REAL alder_carr_1 | 174,15 | 137,55 | 0,790 | 0,721–0,814 | 0,755 |
| REAL upland_fir_1 | 164,80 | 122,10 | 0,741 | 0,701–0,777 | 0,743 |
| REAL bog_woodland_1 | 183,45 | 139,15 | 0,759 | 0,740–0,783 | 0,782 |
| REAL floodplain_1 | 183,90 | 131,45 | 0,715 | 0,681–0,748 | 0,747 |
| REAL montane_spruce_1 | 153,75 | 122,50 | 0,797 | 0,769–0,804 | 0,757 |
| GAMEPLAY oak_hornbeam_1 | 210,35 | 145,65 | 0,692 | 0,568–0,733 | 0,719 |
| GAMEPLAY fresh_pine_1 | 212,10 | 149,00 | 0,702 | 0,629–0,734 | 0,736 |
| GAMEPLAY lowland_beech_1 | 204,45 | 154,15 | 0,754 | 0,657–0,773 | 0,712 |
| GAMEPLAY alder_carr_1 | 242,80 | 169,25 | 0,697 | 0,656–0,727 | 0,729 |
| GAMEPLAY upland_fir_1 | 187,15 | 141,10 | 0,754 | 0,639–0,801 | 0,744 |
| GAMEPLAY bog_woodland_1 | 303,95 | 228,00 | 0,750 | 0,683–0,829 | 0,749 |
| GAMEPLAY floodplain_1 | 213,60 | 150,50 | 0,705 | 0,637–0,727 | 0,724 |
| GAMEPLAY montane_spruce_1 | 171,40 | 124,90 | 0,729 | 0,705–0,756 | 0,719 |
| **min / mediana / max** | | | 0,691 / 0,739 / 0,797 | | |

## Strażnik z jaskiniami: chunki na sekundę z C2ME (świat z jaskiniami wobec M2 bez jaskiń); próg REAL ≥ 0,4 ×, GAMEPLAY ≥ 0,55 ×, lasy górskie REAL ≥ 0,35 ×, GAMEPLAY ≥ 0,45 ×

| miejsce | M2 | FA10 | iloraz | FA10 bez jaskiń | koszt jaskiń FA10, ms na chunk | iloraz wobec M2 ze wszystkich przebiegów | F7 |
|---|---|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 141,45 | 76,45 | 0,540 | 105,10 | 3,57 | 0,545 | 0,561 |
| REAL fresh_pine_1 | 190,95 | 81,45 | 0,427 | 137,70 | 5,02 | 0,436 | 0,419 |
| REAL lowland_beech_1 | 192,40 | 101,55 | 0,528 | 131,15 | 2,22 | 0,533 | 0,719 |
| REAL alder_carr_1 | 179,50 | 107,50 | 0,599 | 137,55 | 2,03 | 0,616 | 0,614 |
| REAL upland_fir_1 | 171,65 | 66,05 | 0,385 | 122,10 | 6,95 | 0,396 | 0,392 |
| REAL bog_woodland_1 | 180,85 | 87,10 | 0,482 | 139,15 | 4,29 | 0,478 | 0,484 |
| REAL floodplain_1 | 185,25 | 111,00 | 0,599 | 131,45 | 1,40 | 0,599 | 0,637 |
| REAL montane_spruce_1 | 160,00 | 65,85 | 0,412 | 122,50 | 7,02 | 0,423 | 0,459 |
| GAMEPLAY oak_hornbeam_1 | 210,35 | 126,00 | 0,599 | 145,65 | 1,07 | 0,599 | 0,636 |
| GAMEPLAY fresh_pine_1 | 218,15 | 128,80 | 0,590 | 149,00 | 1,05 | 0,597 | 0,627 |
| GAMEPLAY lowland_beech_1 | 211,30 | 124,95 | 0,591 | 154,15 | 1,52 | 0,608 | 0,600 |
| GAMEPLAY alder_carr_1 | 246,95 | 148,05 | 0,600 | 169,25 | 0,85 | 0,606 | 0,632 |
| GAMEPLAY upland_fir_1 | 190,95 | 109,85 | 0,575 | 141,10 | 2,02 | 0,583 | 0,633 |
| GAMEPLAY bog_woodland_1 | 301,70 | 204,95 | 0,679 | 228,00 | 0,49 | 0,674 | 0,684 |
| GAMEPLAY floodplain_1 | 218,10 | 118,50 | 0,543 | 150,50 | 1,79 | 0,548 | 0,603 |
| GAMEPLAY montane_spruce_1 | 169,25 | 78,10 | 0,461 | 124,90 | 4,80 | 0,458 | 0,478 |

## FEATURES, ms na chunk (`stages -Psites=slice -Pcaves=off`, 20 obszarów miejsc wycinka); próg ≤ 2,0 × M2 i ≤ 10 ms

| obszar | M2 | F7 (2026-11-11) | FA10 | FA10 / M2 | FA10 / F7 |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 4,94 | 7,79 | 7,88 | 1,595 | 1,011 |
| REAL oak_hornbeam_2 | 4,91 | 7,36 | 6,86 | 1,398 | 0,932 |
| REAL fresh_pine_1 | 5,33 | 7,19 | 7,52 | 1,411 | 1,045 |
| REAL fresh_pine_2 | 4,46 | 6,23 | 6,49 | 1,455 | 1,042 |
| REAL transect | 4,70 | 6,53 | 6,75 | 1,435 | 1,034 |
| GAMEPLAY oak_hornbeam_1 | 4,64 | 7,79 | 7,94 | 1,710 | 1,019 |
| GAMEPLAY oak_hornbeam_2 | 5,74 | 8,55 | 8,57 | 1,493 | 1,002 |
| GAMEPLAY fresh_pine_1 | 5,01 | 7,23 | 7,62 | 1,523 | 1,055 |
| GAMEPLAY fresh_pine_2 | 4,75 | 7,24 | 7,37 | 1,551 | 1,018 |
| GAMEPLAY transect | 4,84 | 6,93 | 7,14 | 1,474 | 1,030 |
| REAL D oak_hornbeam_1 | 4,98 | 7,74 | 7,95 | 1,594 | 1,027 |
| REAL D oak_hornbeam_2 | 4,87 | 6,81 | 6,84 | 1,406 | 1,005 |
| REAL D fresh_pine_1 | 5,27 | 7,34 | 7,54 | 1,432 | 1,027 |
| REAL D fresh_pine_2 | 4,34 | 6,14 | 6,42 | 1,481 | 1,046 |
| REAL D transect | 4,69 | 6,51 | 6,78 | 1,447 | 1,042 |
| GAMEPLAY D oak_hornbeam_1 | 4,59 | 7,50 | 7,70 | 1,678 | 1,026 |
| GAMEPLAY D oak_hornbeam_2 | 5,70 | 8,42 | 8,62 | 1,512 | 1,024 |
| GAMEPLAY D fresh_pine_1 | 5,02 | 7,10 | 7,37 | 1,469 | 1,038 |
| GAMEPLAY D fresh_pine_2 | 4,70 | 7,21 | 7,43 | 1,580 | 1,030 |
| GAMEPLAY D transect | 4,66 | 6,64 | 6,90 | 1,482 | 1,039 |

## LIGHT, ms na chunk (`stages -Psites=slice -Pcaves=off`, 20 obszarów miejsc wycinka); próg ≤ 1,75 × M2 i ≤ 12 ms

| obszar | M2 | F7 (2026-11-11) | FA10 | FA10 / M2 | FA10 / F7 |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 6,71 | 9,56 | 9,86 | 1,469 | 1,031 |
| REAL oak_hornbeam_2 | 7,00 | 8,99 | 9,66 | 1,379 | 1,074 |
| REAL fresh_pine_1 | 7,38 | 8,59 | 8,84 | 1,198 | 1,030 |
| REAL fresh_pine_2 | 6,55 | 9,40 | 9,57 | 1,460 | 1,018 |
| REAL transect | 6,62 | 8,69 | 8,82 | 1,333 | 1,015 |
| GAMEPLAY oak_hornbeam_1 | 6,46 | 9,01 | 9,25 | 1,432 | 1,027 |
| GAMEPLAY oak_hornbeam_2 | 7,42 | 9,73 | 10,02 | 1,349 | 1,029 |
| GAMEPLAY fresh_pine_1 | 6,58 | 8,66 | 8,92 | 1,356 | 1,030 |
| GAMEPLAY fresh_pine_2 | 6,74 | 9,05 | 9,21 | 1,366 | 1,018 |
| GAMEPLAY transect | 6,72 | 8,53 | 8,67 | 1,291 | 1,017 |
| REAL D oak_hornbeam_1 | 6,67 | 9,20 | 9,77 | 1,464 | 1,061 |
| REAL D oak_hornbeam_2 | 7,05 | 9,46 | 9,52 | 1,350 | 1,006 |
| REAL D fresh_pine_1 | 6,40 | 8,41 | 8,54 | 1,335 | 1,015 |
| REAL D fresh_pine_2 | 6,45 | 8,24 | 8,51 | 1,318 | 1,033 |
| REAL D transect | 6,74 | 8,56 | 8,76 | 1,299 | 1,023 |
| GAMEPLAY D oak_hornbeam_1 | 6,34 | 8,91 | 9,16 | 1,443 | 1,028 |
| GAMEPLAY D oak_hornbeam_2 | 7,48 | 9,46 | 9,84 | 1,314 | 1,040 |
| GAMEPLAY D fresh_pine_1 | 6,50 | 8,68 | 8,89 | 1,368 | 1,024 |
| GAMEPLAY D fresh_pine_2 | 6,67 | 8,92 | 9,00 | 1,349 | 1,009 |
| GAMEPLAY D transect | 6,68 | 8,19 | 8,59 | 1,285 | 1,048 |

## cały chunk, ms na chunk (`stages -Psites=slice -Pcaves=off`, 20 obszarów miejsc wycinka); próg ≤ 48 ms

| obszar | M2 | F7 (2026-11-11) | FA10 | FA10 / M2 | FA10 / F7 |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 16,78 | 22,58 | 23,27 | 1,386 | 1,030 |
| REAL oak_hornbeam_2 | 17,48 | 21,82 | 22,20 | 1,270 | 1,017 |
| REAL fresh_pine_1 | 17,98 | 21,18 | 21,49 | 1,196 | 1,015 |
| REAL fresh_pine_2 | 16,00 | 20,41 | 20,73 | 1,296 | 1,016 |
| REAL transect | 16,59 | 20,25 | 21,16 | 1,275 | 1,045 |
| GAMEPLAY oak_hornbeam_1 | 15,90 | 21,55 | 22,01 | 1,384 | 1,021 |
| GAMEPLAY oak_hornbeam_2 | 20,36 | 25,21 | 26,15 | 1,284 | 1,037 |
| GAMEPLAY fresh_pine_1 | 17,65 | 21,58 | 22,49 | 1,275 | 1,042 |
| GAMEPLAY fresh_pine_2 | 17,88 | 22,57 | 22,88 | 1,279 | 1,014 |
| GAMEPLAY transect | 17,14 | 20,93 | 21,67 | 1,265 | 1,036 |
| REAL D oak_hornbeam_1 | 17,32 | 22,19 | 22,80 | 1,316 | 1,027 |
| REAL D oak_hornbeam_2 | 17,55 | 21,99 | 22,02 | 1,255 | 1,001 |
| REAL D fresh_pine_1 | 16,93 | 20,78 | 21,29 | 1,258 | 1,025 |
| REAL D fresh_pine_2 | 16,07 | 19,18 | 19,80 | 1,232 | 1,032 |
| REAL D transect | 16,90 | 20,18 | 20,84 | 1,233 | 1,032 |
| GAMEPLAY D oak_hornbeam_1 | 16,14 | 21,28 | 21,71 | 1,345 | 1,020 |
| GAMEPLAY D oak_hornbeam_2 | 20,52 | 24,98 | 25,69 | 1,252 | 1,028 |
| GAMEPLAY D fresh_pine_1 | 17,33 | 21,47 | 22,09 | 1,275 | 1,029 |
| GAMEPLAY D fresh_pine_2 | 17,73 | 22,50 | 22,66 | 1,279 | 1,007 |
| GAMEPLAY D transect | 17,05 | 20,39 | 21,12 | 1,238 | 1,036 |

## FPS z paczką (`-Poptimization`: Sodium, Nvidium, C2ME; świat z jaskiniami; okap = 42 bloki nad gruntem, oko = wysokość oczu); próg ≥ 0,8 × M2, przeloty nad okapem lasów F1–F6 tylko w raporcie

| przelot | M2 FPS | FA10 FPS | iloraz | M2 GPU ms/klatkę | FA10 GPU ms/klatkę | FA10 czas klatki − M2, ms | F7 |
|---|---|---|---|---|---|---|---|
| REAL oak_hornbeam_1 okap | 1546 | 1343 | 0,869 | 0,47 | 0,58 | 0,098 | 0,866 |
| REAL oak_hornbeam_1 oko | 1497 | 1559 | 1,041 | 0,23 | 0,33 | -0,027 | 1,043 |
| REAL fresh_pine_1 okap | 1577 | 1284 | 0,815 | 0,35 | 0,63 | 0,144 | 0,924 |
| REAL fresh_pine_1 oko | 1505 | 1433 | 0,952 | 0,22 | 0,50 | 0,033 | 1,096 |
| REAL lowland_beech_1 okap | 1587 | 1199 | 0,755 | 0,49 | 0,67 | 0,204 | 0,756 |
| REAL lowland_beech_1 oko | 1507 | 1391 | 0,923 | 0,24 | 0,53 | 0,055 | 0,922 |
| REAL alder_carr_1 okap | 1583 | 1235 | 0,780 | 0,47 | 0,67 | 0,178 | 0,767 |
| REAL alder_carr_1 oko | 1467 | 1509 | 1,028 | 0,24 | 0,38 | -0,019 | 1,046 |
| REAL upland_fir_1 okap | 1585 | 1203 | 0,759 | 0,47 | 0,66 | 0,201 | 0,781 |
| REAL upland_fir_1 oko | 1511 | 1437 | 0,951 | 0,23 | 0,39 | 0,034 | 0,989 |
| REAL bog_woodland_1 okap | 1579 | 1455 | 0,922 | 0,35 | 0,52 | 0,054 | 0,924 |
| REAL bog_woodland_1 oko | 1477 | 1477 | 1,000 | 0,22 | 0,36 | -0,000 | 1,013 |
| REAL floodplain_1 okap | 1555 | 1331 | 0,856 | 0,49 | 0,61 | 0,108 | 0,853 |
| REAL floodplain_1 oko | 1484 | 1512 | 1,018 | 0,25 | 0,28 | -0,012 | 1,010 |
| REAL montane_spruce_1 okap | 1577 | 1258 | 0,798 | 0,45 | 0,65 | 0,161 | 0,840 |
| REAL montane_spruce_1 oko | 1512 | 1260 | 0,833 | 0,22 | 0,61 | 0,133 | 0,852 |
| GAMEPLAY oak_hornbeam_1 okap | 1609 | 1309 | 0,814 | 0,45 | 0,63 | 0,142 | 0,804 |
| GAMEPLAY oak_hornbeam_1 oko | 1502 | 1543 | 1,027 | 0,23 | 0,34 | -0,018 | 1,030 |
| GAMEPLAY fresh_pine_1 okap | 1590 | 1336 | 0,840 | 0,38 | 0,59 | 0,120 | 0,815 |
| GAMEPLAY fresh_pine_1 oko | 1514 | 1446 | 0,955 | 0,22 | 0,48 | 0,031 | 0,953 |
| GAMEPLAY lowland_beech_1 okap | 1616 | 1163 | 0,720 | 0,45 | 0,73 | 0,241 | 0,707 |
| GAMEPLAY lowland_beech_1 oko | 1503 | 1497 | 0,996 | 0,23 | 0,42 | 0,003 | 0,989 |
| GAMEPLAY alder_carr_1 okap | 1607 | 1465 | 0,911 | 0,33 | 0,53 | 0,061 | 0,897 |
| GAMEPLAY alder_carr_1 oko | 1496 | 1562 | 1,044 | 0,24 | 0,41 | -0,028 | 1,038 |
| GAMEPLAY upland_fir_1 okap | 1589 | 1311 | 0,825 | 0,45 | 0,59 | 0,134 | 0,807 |
| GAMEPLAY upland_fir_1 oko | 1503 | 1490 | 0,991 | 0,22 | 0,39 | 0,006 | 0,981 |
| GAMEPLAY bog_woodland_1 okap | 1626 | 1367 | 0,841 | 0,41 | 0,59 | 0,117 | 0,831 |
| GAMEPLAY bog_woodland_1 oko | 1528 | 1494 | 0,977 | 0,22 | 0,43 | 0,015 | 0,986 |
| GAMEPLAY floodplain_1 okap | 1522 | 1275 | 0,838 | 0,51 | 0,65 | 0,127 | 0,840 |
| GAMEPLAY floodplain_1 oko | 1481 | 1455 | 0,982 | 0,24 | 0,34 | 0,012 | 0,984 |
| GAMEPLAY montane_spruce_1 okap | 1614 | 1275 | 0,790 | 0,42 | 0,60 | 0,164 | 0,777 |
| GAMEPLAY montane_spruce_1 oko | 1534 | 1492 | 0,972 | 0,22 | 0,43 | 0,019 | 0,982 |

## Miejsca Tatr (KT-6, opis bez progu; M2 nie ma Tatr)

| miejsce | chunki/s bez jaskiń | chunki/s z jaskiniami | koszt jaskiń, ms na chunk |
|---|---|---|---|
| GAMEPLAY tatra_high_1 | 189,45 | 81,50 | 6,99 |
| GAMEPLAY tatra_regle_1 | 166,75 | 77,05 | 6,98 |
| GAMEPLAY tatra_stone_pine_1 | 173,15 | 78,10 | 7,03 |
| REAL tatra_high_1 | 144,90 | 82,80 | 5,18 |
| REAL tatra_regle_1 | 117,60 | 64,55 | 6,99 |
| REAL tatra_stone_pine_1 | 124,75 | 57,10 | 9,50 |

| przelot nad Tatrami | FPS z paczką |
|---|---|
| GAMEPLAY tatra_high_1 okap | 1586 |
| GAMEPLAY tatra_high_1 oko | 1598 |
| GAMEPLAY tatra_regle_1 okap | 1349 |
| GAMEPLAY tatra_regle_1 oko | 1457 |
| GAMEPLAY tatra_stone_pine_1 okap | 1501 |
| GAMEPLAY tatra_stone_pine_1 oko | 1468 |
| REAL tatra_high_1 okap | 1559 |
| REAL tatra_high_1 oko | 1578 |
| REAL tatra_regle_1 okap | 1317 |
| REAL tatra_regle_1 oko | 1463 |
| REAL tatra_stone_pine_1 okap | 1455 |
| REAL tatra_stone_pine_1 oko | 1479 |

## MSPT fauny (KFA-6, ZW-8; `-Pgametest=fauna -Psites=mspt`, GAMEPLAY N, budżet ≤ 0,05 ms ticku serwera na zwierzę)

| scena | ms na zwierzę i tick (przebiegi) | mediana | budżet | werdykt | średni tick serwera przed → po, ms |
|---|---|---|---|---|---|
| roe deer | 0,0144 / 0,0145 | 0,0145 | 0,05 | PASS | 5,99 → 5,41, 6,19 → 5,32 |
| wild boar | 0,0161 / 0,0168 | 0,0164 | 0,05 | PASS | — |
| red squirrel | 0,0079 / 0,0081 | 0,0080 | 0,05 | PASS | — |
| Eurasian jay | 0,0127 / 0,0125 | 0,0126 | 0,05 | PASS | — |
| common frog | 0,0414 / 0,0450 | 0,0432 | 0,05 | PASS | 5,58 → 6,65, 5,81 → 6,80 |
| mixed scene | 0,0182 / 0,0188 | 0,0185 | 0,05 | PASS | 4,48 → 5,45, 4,94 → 5,62 |

## Przeloty z fauną i bez (reguła `polishforests:forest_fauna`, `-PperfFauna=on|off`, z paczką, bez progu)

| przelot | bez fauny | z fauną | iloraz | czas klatki z fauną − bez, ms | zwierzęta moda w obszarze przelotu |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 okap | 1332 | 1297 | 0,974 | 0,020 | 44 {eurasian_jay=20, red_squirrel=18, roe_deer=6}, żaby 0 |
| REAL oak_hornbeam_1 oko | 1512 | 1430 | 0,946 | 0,038 | 44 {eurasian_jay=20, red_squirrel=18, roe_deer=6}, żaby 0 |
| REAL fresh_pine_1 okap | 1252 | 1248 | 0,997 | 0,003 | 27 {eurasian_jay=10, red_squirrel=14, roe_deer=3}, żaby 0 |
| REAL fresh_pine_1 oko | 1333 | 1277 | 0,958 | 0,033 | 27 {eurasian_jay=10, red_squirrel=14, roe_deer=3}, żaby 0 |
| REAL lowland_beech_1 okap | 1186 | 1176 | 0,992 | 0,007 | 35 {eurasian_jay=16, red_squirrel=11, roe_deer=8}, żaby 0 |
| REAL lowland_beech_1 oko | 1367 | 1312 | 0,960 | 0,031 | 35 {eurasian_jay=16, red_squirrel=11, roe_deer=8}, żaby 0 |
| GAMEPLAY oak_hornbeam_1 okap | 1301 | 1290 | 0,991 | 0,007 | 39 {eurasian_jay=18, red_squirrel=14, roe_deer=4, wild_boar=3}, żaby 0 |
| GAMEPLAY oak_hornbeam_1 oko | 1533 | 1406 | 0,917 | 0,059 | 39 {eurasian_jay=18, red_squirrel=14, roe_deer=4, wild_boar=3}, żaby 0 |
| GAMEPLAY fresh_pine_1 okap | 1332 | 1288 | 0,967 | 0,025 | 35 {eurasian_jay=10, red_squirrel=13, roe_deer=12}, żaby 0 |
| GAMEPLAY fresh_pine_1 oko | 1393 | 1307 | 0,939 | 0,047 | 35 {eurasian_jay=10, red_squirrel=13, roe_deer=12}, żaby 0 |
| GAMEPLAY lowland_beech_1 okap | 1162 | 1138 | 0,980 | 0,018 | 42 {eurasian_jay=9, red_squirrel=16, roe_deer=17}, żaby 0 |
| GAMEPLAY lowland_beech_1 oko | 1417 | 1305 | 0,921 | 0,061 | 42 {eurasian_jay=9, red_squirrel=16, roe_deer=17}, żaby 0 |

## Werdykty progów (PerformanceThresholds, CP-D.5)

| metryka | PASS | FAIL | ACCEPTED | REPORT | DESCRIBE |
|---|---|---|---|---|---|
| CHUNKS_NO_CAVES | 13 | 3 | 0 | 0 | 0 |
| CHUNKS_WITH_CAVES | 15 | 1 | 0 | 0 | 0 |
| FPS | 20 | 0 | 0 | 12 | 0 |
| FEATURES | 20 | 0 | 0 | 0 | 0 |
| LIGHT | 20 | 0 | 0 | 0 | 0 |
| WHOLE_CHUNK | 20 | 0 | 0 | 0 | 0 |

| metryka | skala | miejsce | wartość | próg | werdykt | reguła |
|---|---|---|---|---|---|---|
| CHUNKS_NO_CAVES | REALISTIC | lowland_beech_1 | 0,691 × | 0,700 | **FAIL** | forest norm >= 0.70 |
| CHUNKS_NO_CAVES | GAMEPLAY | oak_hornbeam_1 | 0,692 × | 0,700 | **FAIL** | forest norm >= 0.70 |
| CHUNKS_NO_CAVES | GAMEPLAY | alder_carr_1 | 0,697 × | 0,700 | **FAIL** | forest norm >= 0.70 |
| CHUNKS_WITH_CAVES | GAMEPLAY | floodplain_1 | 0,543 × | 0,550 | **FAIL** | regression guard with caves >= 0.55 |
| CHUNKS_NO_CAVES | REALISTIC | oak_hornbeam_1 | 0,756 × | 0,700 | PASS | forest norm >= 0.70, target 0.75 met |
| CHUNKS_NO_CAVES | REALISTIC | fresh_pine_1 | 0,736 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | REALISTIC | alder_carr_1 | 0,790 × | 0,700 | PASS | forest norm >= 0.70, target 0.75 met |
| CHUNKS_NO_CAVES | REALISTIC | upland_fir_1 | 0,741 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | REALISTIC | bog_woodland_1 | 0,759 × | 0,700 | PASS | forest norm >= 0.70, target 0.75 met |
| CHUNKS_NO_CAVES | REALISTIC | floodplain_1 | 0,715 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | REALISTIC | montane_spruce_1 | 0,797 × | 0,700 | PASS | forest norm >= 0.70, target 0.75 met |
| CHUNKS_NO_CAVES | GAMEPLAY | fresh_pine_1 | 0,702 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | GAMEPLAY | lowland_beech_1 | 0,754 × | 0,700 | PASS | forest norm >= 0.70, target 0.75 met |
| CHUNKS_NO_CAVES | GAMEPLAY | upland_fir_1 | 0,754 × | 0,700 | PASS | forest norm >= 0.70, target 0.75 met |
| CHUNKS_NO_CAVES | GAMEPLAY | bog_woodland_1 | 0,750 × | 0,700 | PASS | forest norm >= 0.70, target 0.75 met |
| CHUNKS_NO_CAVES | GAMEPLAY | floodplain_1 | 0,705 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_NO_CAVES | GAMEPLAY | montane_spruce_1 | 0,729 × | 0,700 | PASS | forest norm >= 0.70 (target 0.75 not met) |
| CHUNKS_WITH_CAVES | REALISTIC | oak_hornbeam_1 | 0,540 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | fresh_pine_1 | 0,427 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | lowland_beech_1 | 0,528 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | alder_carr_1 | 0,599 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | upland_fir_1 | 0,385 × | 0,350 | PASS | mountain guard with caves >= 0.35 |
| CHUNKS_WITH_CAVES | REALISTIC | bog_woodland_1 | 0,482 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | floodplain_1 | 0,599 × | 0,400 | PASS | regression guard with caves >= 0.40 |
| CHUNKS_WITH_CAVES | REALISTIC | montane_spruce_1 | 0,412 × | 0,350 | PASS | mountain guard with caves >= 0.35 |
| CHUNKS_WITH_CAVES | GAMEPLAY | oak_hornbeam_1 | 0,599 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | fresh_pine_1 | 0,590 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | lowland_beech_1 | 0,591 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | alder_carr_1 | 0,600 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | upland_fir_1 | 0,575 × | 0,450 | PASS | mountain guard with caves >= 0.45 |
| CHUNKS_WITH_CAVES | GAMEPLAY | bog_woodland_1 | 0,679 × | 0,550 | PASS | regression guard with caves >= 0.55 |
| CHUNKS_WITH_CAVES | GAMEPLAY | montane_spruce_1 | 0,461 × | 0,450 | PASS | mountain guard with caves >= 0.45 |
| FPS | REALISTIC | oak_hornbeam_1 canopy | 0,869 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | REALISTIC | oak_hornbeam_1 eye | 1,041 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | REALISTIC | fresh_pine_1 canopy | 0,815 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | REALISTIC | fresh_pine_1 eye | 0,952 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | REALISTIC | lowland_beech_1 canopy | 0,755 × | — | REPORT | canopy flight over a forest of F1-F6: report only |
| FPS | REALISTIC | lowland_beech_1 eye | 0,923 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | REALISTIC | alder_carr_1 canopy | 0,780 × | — | REPORT | canopy flight over a forest of F1-F6: report only |
| FPS | REALISTIC | alder_carr_1 eye | 1,028 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | REALISTIC | upland_fir_1 canopy | 0,759 × | — | REPORT | canopy flight over a forest of F1-F6: report only |
| FPS | REALISTIC | upland_fir_1 eye | 0,951 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | REALISTIC | bog_woodland_1 canopy | 0,922 × | — | REPORT | canopy flight over a forest of F1-F6: report only |
| FPS | REALISTIC | bog_woodland_1 eye | 1,000 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | REALISTIC | floodplain_1 canopy | 0,856 × | — | REPORT | canopy flight over a forest of F1-F6: report only |
| FPS | REALISTIC | floodplain_1 eye | 1,018 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | REALISTIC | montane_spruce_1 canopy | 0,798 × | — | REPORT | canopy flight over a forest of F1-F6: report only |
| FPS | REALISTIC | montane_spruce_1 eye | 0,833 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | GAMEPLAY | oak_hornbeam_1 canopy | 0,814 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | GAMEPLAY | oak_hornbeam_1 eye | 1,027 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | GAMEPLAY | fresh_pine_1 canopy | 0,840 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | GAMEPLAY | fresh_pine_1 eye | 0,955 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | GAMEPLAY | lowland_beech_1 canopy | 0,720 × | — | REPORT | canopy flight over a forest of F1-F6: report only |
| FPS | GAMEPLAY | lowland_beech_1 eye | 0,996 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | GAMEPLAY | alder_carr_1 canopy | 0,911 × | — | REPORT | canopy flight over a forest of F1-F6: report only |
| FPS | GAMEPLAY | alder_carr_1 eye | 1,044 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | GAMEPLAY | upland_fir_1 canopy | 0,825 × | — | REPORT | canopy flight over a forest of F1-F6: report only |
| FPS | GAMEPLAY | upland_fir_1 eye | 0,991 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | GAMEPLAY | bog_woodland_1 canopy | 0,841 × | — | REPORT | canopy flight over a forest of F1-F6: report only |
| FPS | GAMEPLAY | bog_woodland_1 eye | 0,977 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | GAMEPLAY | floodplain_1 canopy | 0,838 × | — | REPORT | canopy flight over a forest of F1-F6: report only |
| FPS | GAMEPLAY | floodplain_1 eye | 0,982 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FPS | GAMEPLAY | montane_spruce_1 canopy | 0,790 × | — | REPORT | canopy flight over a forest of F1-F6: report only |
| FPS | GAMEPLAY | montane_spruce_1 eye | 0,972 × | 0,800 | PASS | FPS with the pack >= 0.8 |
| FEATURES | REALISTIC | oak_hornbeam_1 | 7,88 ms (1,595 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | oak_hornbeam_2 | 6,86 ms (1,398 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | fresh_pine_1 | 7,52 ms (1,411 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | fresh_pine_2 | 6,49 ms (1,455 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | transect | 6,75 ms (1,435 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | oak_hornbeam_1 | 7,94 ms (1,710 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | oak_hornbeam_2 | 8,57 ms (1,493 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | fresh_pine_1 | 7,62 ms (1,523 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | fresh_pine_2 | 7,37 ms (1,551 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | transect | 7,14 ms (1,474 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | oak_hornbeam_1_d | 7,95 ms (1,594 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | oak_hornbeam_2_d | 6,84 ms (1,406 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | fresh_pine_1_d | 7,54 ms (1,432 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | fresh_pine_2_d | 6,42 ms (1,481 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | REALISTIC | transect_d | 6,78 ms (1,447 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | oak_hornbeam_1_d | 7,70 ms (1,678 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | oak_hornbeam_2_d | 8,62 ms (1,512 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | fresh_pine_1_d | 7,37 ms (1,469 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | fresh_pine_2_d | 7,43 ms (1,580 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| FEATURES | GAMEPLAY | transect_d | 6,90 ms (1,482 ×) | 2,000 | PASS | FEATURES <= 2.0 x and <= 10 ms |
| LIGHT | REALISTIC | oak_hornbeam_1 | 9,86 ms (1,469 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | oak_hornbeam_2 | 9,66 ms (1,379 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | fresh_pine_1 | 8,84 ms (1,198 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | fresh_pine_2 | 9,57 ms (1,460 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | transect | 8,82 ms (1,333 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | oak_hornbeam_1 | 9,25 ms (1,432 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | oak_hornbeam_2 | 10,02 ms (1,349 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | fresh_pine_1 | 8,92 ms (1,356 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | fresh_pine_2 | 9,21 ms (1,366 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | transect | 8,67 ms (1,291 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | oak_hornbeam_1_d | 9,77 ms (1,464 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | oak_hornbeam_2_d | 9,52 ms (1,350 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | fresh_pine_1_d | 8,54 ms (1,335 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | fresh_pine_2_d | 8,51 ms (1,318 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | REALISTIC | transect_d | 8,76 ms (1,299 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | oak_hornbeam_1_d | 9,16 ms (1,443 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | oak_hornbeam_2_d | 9,84 ms (1,314 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | fresh_pine_1_d | 8,89 ms (1,368 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | fresh_pine_2_d | 9,00 ms (1,349 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| LIGHT | GAMEPLAY | transect_d | 8,59 ms (1,285 ×) | 1,750 | PASS | LIGHT <= 1.75 x and <= 12 ms |
| WHOLE_CHUNK | REALISTIC | oak_hornbeam_1 | 23,27 ms (1,386 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | oak_hornbeam_2 | 22,20 ms (1,270 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | fresh_pine_1 | 21,49 ms (1,196 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | fresh_pine_2 | 20,73 ms (1,296 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | transect | 21,16 ms (1,275 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | oak_hornbeam_1 | 22,01 ms (1,384 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | oak_hornbeam_2 | 26,15 ms (1,284 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | fresh_pine_1 | 22,49 ms (1,275 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | fresh_pine_2 | 22,88 ms (1,279 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | transect | 21,67 ms (1,265 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | oak_hornbeam_1_d | 22,80 ms (1,316 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | oak_hornbeam_2_d | 22,02 ms (1,255 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | fresh_pine_1_d | 21,29 ms (1,258 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | fresh_pine_2_d | 19,80 ms (1,232 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | REALISTIC | transect_d | 20,84 ms (1,233 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | oak_hornbeam_1_d | 21,71 ms (1,345 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | oak_hornbeam_2_d | 25,69 ms (1,252 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | fresh_pine_1_d | 22,09 ms (1,275 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | fresh_pine_2_d | 22,66 ms (1,279 ×) | 48,000 | PASS | whole chunk <= 48 ms |
| WHOLE_CHUNK | GAMEPLAY | transect_d | 21,12 ms (1,238 ×) | 48,000 | PASS | whole chunk <= 48 ms |
