# F7: tabele A/B (PW-10, G.34)

Pomiar 2026-11-10/11 (sterownik `run_ab_f7.sh`: warunek ciszy przed każdym przebiegiem, próbnik gier co 20 s; 0 przebiegów zakłóconych przez gry, 0 nieudanych); chunki na sekundę bez jaskiń powtórzone 2026-11-11 rano (`run_ab_f7_load2.sh`, przegląd F7, runda 1; tabela niżej), bo przebieg M2 r4 pierwszego pomiaru był wolny (Uwagi do metody). Buildy: **M2** = `pl-m2-baseline` (tag `m2-baseline-r1` + uprząż V10 + `ForestPerformance` z miejscami F1–F6), **przed F1** = `c33a916` (+ ta sama uprząż), **F7** = `0e5b6b0` (kod generacji etapu 2 bez zmian). Kolejność ABBA (rundy nieparzyste: baza pierwsza). Mediany: `load` (powtórka) i `stages` z 4 przebiegów, jaskinie, przeloty i „przed F1” z 2. Iloraz = F7 / baza. Pogrubione = poza progiem. Surowe liczby: `handoff/F7/results.md`.


## Chunki na sekundę z C2ME bez jaskiń (`-Pcaves=off`, `-PloadOnly`); próg ≥ 0,7 × M2

Powtórka 2026-11-11 rano (przegląd F7, runda 1): sterownik `handoff/F7/run_ab_f7_load2.sh`, 4 rundy ABBA M2 / F7 (`4288907`, kod generacji ten sam co w `0e5b6b0`), własny katalog dzienników `handoff/F7/ab2/`, warunek ciszy: brak gry, CPU < 20% i obciążenie GPU ≤ 5% karty (wykorzystanie × zegar / zegar maks.) w 3 próbkach z rzędu, zapis obciążenia GPU co 10 s w czasie przebiegu; 0 przebiegów zakłóconych i nieudanych. Mediany 4 przebiegów; kolumna „pierwszy pomiar” to dawny iloraz z zakłóconym przebiegiem M2 r4 (Uwagi do metody). **Wynik: 15 z 16 miejsc ≥ 0,7; REAL `lowland_beech_1` 0,698 (o 0,002 poniżej progu, w granicach rozrzutu rund).**

| miejsce | M2 | F7 | iloraz | ilorazy rund (min–max) | pierwszy pomiar 2026-11-10/11 |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 148,25 | 105,85 | 0,714 | 0,698–0,733 | 0,721 |
| REAL fresh_pine_1 | 193,45 | 142,10 | 0,735 | 0,697–0,758 | 0,744 |
| REAL lowland_beech_1 | 195,05 | 136,05 | **0,698** | 0,683–0,739 | 0,725 |
| REAL alder_carr_1 | 183,90 | 138,80 | 0,755 | 0,753–0,763 | 0,728 |
| REAL upland_fir_1 | 171,25 | 127,20 | 0,743 | 0,736–0,761 | 0,783 |
| REAL bog_woodland_1 | 184,70 | 144,40 | 0,782 | 0,753–0,797 | 0,778 |
| REAL floodplain_1 | 183,65 | 137,20 | 0,747 | 0,726–0,761 | 0,744 |
| REAL montane_spruce_1 | 163,15 | 123,45 | 0,757 | 0,738–0,763 | 0,761 |
| GAMEPLAY oak_hornbeam_1 | 217,95 | 156,75 | 0,719 | 0,689–0,734 | 0,701 |
| GAMEPLAY fresh_pine_1 | 211,50 | 155,65 | 0,736 | 0,713–0,748 | 0,733 |
| GAMEPLAY lowland_beech_1 | 215,75 | 153,60 | 0,712 | 0,698–0,745 | 0,719 |
| GAMEPLAY alder_carr_1 | 244,50 | 178,15 | 0,729 | 0,665–0,754 | 0,734 |
| GAMEPLAY upland_fir_1 | 189,95 | 141,35 | 0,744 | 0,702–0,783 | 0,764 |
| GAMEPLAY bog_woodland_1 | 315,30 | 236,30 | 0,749 | 0,722–0,801 | 0,783 |
| GAMEPLAY floodplain_1 | 217,60 | 157,55 | 0,724 | 0,709–0,756 | 0,741 |
| GAMEPLAY montane_spruce_1 | 178,45 | 128,35 | 0,719 | 0,701–0,726 | 0,808 |
| **min / mediana / max** | | | 0,698 / 0,735 / 0,782 | | 0,701 / 0,743 / 0,808 |

## Strażnik z jaskiniami: chunki na sekundę z C2ME (świat z jaskiniami wobec M2 bez jaskiń); próg REAL ≥ 0,4 ×, GAMEPLAY ≥ 0,55 ×

| miejsce | M2 | F7 | iloraz | F7 bez jaskiń | koszt jaskiń F7, ms na chunk |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 131,15 | 73,55 | 0,561 | 107,20 | 4,27 |
| REAL fresh_pine_1 | 185,80 | 77,80 | 0,419 | 141,65 | 5,79 |
| REAL lowland_beech_1 | 138,15 | 99,30 | 0,719 | 139,90 | 2,92 |
| REAL alder_carr_1 | 173,35 | 106,45 | 0,614 | 138,15 | 2,16 |
| REAL upland_fir_1 | 163,20 | 63,95 | **0,392** | 134,35 | 8,19 |
| REAL bog_woodland_1 | 178,30 | 86,35 | 0,484 | 143,20 | 4,60 |
| REAL floodplain_1 | 177,75 | 113,15 | 0,637 | 138,05 | 1,59 |
| REAL montane_spruce_1 | 160,05 | 73,45 | 0,459 | 124,65 | 5,59 |
| GAMEPLAY oak_hornbeam_1 | 200,95 | 127,85 | 0,636 | 155,90 | 1,41 |
| GAMEPLAY fresh_pine_1 | 210,95 | 132,20 | 0,627 | 157,50 | 1,22 |
| GAMEPLAY lowland_beech_1 | 204,75 | 122,80 | 0,600 | 155,10 | 1,70 |
| GAMEPLAY alder_carr_1 | 238,85 | 150,90 | 0,632 | 180,05 | 1,07 |
| GAMEPLAY upland_fir_1 | 180,85 | 114,50 | 0,633 | 146,90 | 1,93 |
| GAMEPLAY bog_woodland_1 | 302,50 | 207,05 | 0,684 | 235,80 | 0,59 |
| GAMEPLAY floodplain_1 | 202,70 | 122,20 | 0,603 | 162,10 | 2,01 |
| GAMEPLAY montane_spruce_1 | 172,35 | 82,45 | **0,478** | 128,45 | 4,34 |

Iloraz liczono wobec M2 z tych samych rund jaskiń (pierwszy pomiar, 2 przebiegi M2 z 23:16–23:42). Wobec mediany 4 cichych przebiegów M2 z powtórki 2026-11-11 (wyżej): REAL 0,373–0,616 (`upland_fir_1` **0,373**, `fresh_pine_1` 0,402, pozostałe ≥ 0,450), GAMEPLAY 0,462–0,657 (`montane_spruce_1` **0,462**, `floodplain_1` 0,562, `lowland_beech_1` 0,569, pozostałe ≥ 0,587). Werdykt ten sam: niespełnione w tych samych 2 miejscach, `fresh_pine_1` REAL na granicy. Przebiegów F7 z jaskiniami nie powtarzano.

## FEATURES, ms na chunk (`stages -Psites=slice -Pcaves=off`, 20 obszarów miejsc wycinka); próg ≤ 2,0 × M2 i ≤ 10 ms

| obszar | M2 | przed F1 | F7 | F7 / M2 | F7 / przed F1 |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 4,71 | 7,72 | 7,79 | 1,653 | 1,009 |
| REAL oak_hornbeam_2 | 4,80 | 6,65 | 7,36 | 1,534 | 1,107 |
| REAL fresh_pine_1 | 5,16 | 7,03 | 7,19 | 1,394 | 1,022 |
| REAL fresh_pine_2 | 4,45 | 5,95 | 6,23 | 1,398 | 1,047 |
| REAL transect | 4,60 | 6,84 | 6,53 | 1,419 | 0,955 |
| GAMEPLAY oak_hornbeam_1 | 4,58 | 7,54 | 7,79 | 1,701 | 1,033 |
| GAMEPLAY oak_hornbeam_2 | 5,58 | 8,24 | 8,55 | 1,534 | 1,038 |
| GAMEPLAY fresh_pine_1 | 4,94 | 7,09 | 7,23 | 1,464 | 1,020 |
| GAMEPLAY fresh_pine_2 | 4,59 | 7,11 | 7,24 | 1,579 | 1,019 |
| GAMEPLAY transect | 4,71 | 6,78 | 6,93 | 1,471 | 1,022 |
| REAL D oak_hornbeam_1 | 4,80 | 7,59 | 7,74 | 1,614 | 1,021 |
| REAL D oak_hornbeam_2 | 4,55 | 6,70 | 6,81 | 1,496 | 1,016 |
| REAL D fresh_pine_1 | 5,05 | 7,12 | 7,34 | 1,454 | 1,030 |
| REAL D fresh_pine_2 | 4,25 | 6,08 | 6,14 | 1,445 | 1,010 |
| REAL D transect | 4,48 | 6,40 | 6,51 | 1,451 | 1,017 |
| GAMEPLAY D oak_hornbeam_1 | 4,43 | 7,38 | 7,50 | 1,693 | 1,017 |
| GAMEPLAY D oak_hornbeam_2 | 5,66 | 8,20 | 8,42 | 1,489 | 1,027 |
| GAMEPLAY D fresh_pine_1 | 4,87 | 6,82 | 7,10 | 1,459 | 1,041 |
| GAMEPLAY D fresh_pine_2 | 4,59 | 6,94 | 7,21 | 1,572 | 1,039 |
| GAMEPLAY D transect | 4,62 | 6,47 | 6,64 | 1,438 | 1,027 |

## LIGHT, ms na chunk (`stages -Psites=slice -Pcaves=off`, 20 obszarów miejsc wycinka); próg ≤ 1,75 × M2 i ≤ 12 ms

| obszar | M2 | przed F1 | F7 | F7 / M2 | F7 / przed F1 |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 6,60 | 9,46 | 9,56 | 1,449 | 1,011 |
| REAL oak_hornbeam_2 | 7,04 | 9,44 | 8,99 | 1,277 | 0,953 |
| REAL fresh_pine_1 | 6,76 | 8,60 | 8,59 | 1,271 | 0,998 |
| REAL fresh_pine_2 | 6,38 | 8,73 | 9,40 | 1,474 | 1,077 |
| REAL transect | 6,59 | 8,53 | 8,69 | 1,319 | 1,018 |
| GAMEPLAY oak_hornbeam_1 | 6,32 | 9,71 | 9,01 | 1,425 | 0,928 |
| GAMEPLAY oak_hornbeam_2 | 7,20 | 9,59 | 9,73 | 1,350 | 1,014 |
| GAMEPLAY fresh_pine_1 | 6,45 | 8,52 | 8,66 | 1,343 | 1,016 |
| GAMEPLAY fresh_pine_2 | 6,70 | 9,05 | 9,05 | 1,351 | 1,001 |
| GAMEPLAY transect | 6,61 | 8,74 | 8,53 | 1,291 | 0,976 |
| REAL D oak_hornbeam_1 | 6,55 | 9,16 | 9,20 | 1,406 | 1,005 |
| REAL D oak_hornbeam_2 | 6,82 | 8,87 | 9,46 | 1,387 | 1,067 |
| REAL D fresh_pine_1 | 6,20 | 8,30 | 8,41 | 1,355 | 1,012 |
| REAL D fresh_pine_2 | 6,37 | 8,05 | 8,24 | 1,294 | 1,024 |
| REAL D transect | 6,50 | 9,45 | 8,56 | 1,317 | 0,907 |
| GAMEPLAY D oak_hornbeam_1 | 6,18 | 8,68 | 8,91 | 1,442 | 1,027 |
| GAMEPLAY D oak_hornbeam_2 | 7,27 | 10,45 | 9,46 | 1,301 | 0,905 |
| GAMEPLAY D fresh_pine_1 | 6,45 | 8,59 | 8,68 | 1,345 | 1,011 |
| GAMEPLAY D fresh_pine_2 | 6,59 | 8,57 | 8,92 | 1,353 | 1,041 |
| GAMEPLAY D transect | 6,50 | 8,18 | 8,19 | 1,260 | 1,001 |

## cały chunk, ms na chunk (`stages -Psites=slice -Pcaves=off`, 20 obszarów miejsc wycinka); próg ≤ 48 ms

| obszar | M2 | przed F1 | F7 | F7 / M2 | F7 / przed F1 |
|---|---|---|---|---|---|
| REAL oak_hornbeam_1 | 17,07 | 22,48 | 22,58 | 1,322 | 1,005 |
| REAL oak_hornbeam_2 | 17,45 | 22,13 | 21,82 | 1,250 | 0,986 |
| REAL fresh_pine_1 | 16,91 | 20,58 | 21,18 | 1,253 | 1,029 |
| REAL fresh_pine_2 | 15,69 | 19,35 | 20,41 | 1,301 | 1,055 |
| REAL transect | 16,98 | 20,69 | 20,25 | 1,193 | 0,979 |
| GAMEPLAY oak_hornbeam_1 | 15,52 | 21,90 | 21,55 | 1,388 | 0,984 |
| GAMEPLAY oak_hornbeam_2 | 20,29 | 24,83 | 25,21 | 1,243 | 1,015 |
| GAMEPLAY fresh_pine_1 | 17,31 | 21,27 | 21,58 | 1,246 | 1,015 |
| GAMEPLAY fresh_pine_2 | 17,55 | 22,18 | 22,57 | 1,285 | 1,017 |
| GAMEPLAY transect | 16,85 | 20,86 | 20,93 | 1,242 | 1,003 |
| REAL D oak_hornbeam_1 | 16,74 | 22,20 | 22,19 | 1,325 | 1,000 |
| REAL D oak_hornbeam_2 | 17,25 | 20,95 | 21,99 | 1,275 | 1,050 |
| REAL D fresh_pine_1 | 16,32 | 20,50 | 20,78 | 1,273 | 1,014 |
| REAL D fresh_pine_2 | 15,50 | 18,76 | 19,18 | 1,237 | 1,022 |
| REAL D transect | 16,17 | 20,98 | 20,18 | 1,248 | 0,962 |
| GAMEPLAY D oak_hornbeam_1 | 15,37 | 20,77 | 21,28 | 1,385 | 1,025 |
| GAMEPLAY D oak_hornbeam_2 | 20,06 | 25,82 | 24,98 | 1,245 | 0,968 |
| GAMEPLAY D fresh_pine_1 | 17,08 | 21,12 | 21,47 | 1,257 | 1,017 |
| GAMEPLAY D fresh_pine_2 | 17,52 | 21,62 | 22,50 | 1,284 | 1,041 |
| GAMEPLAY D transect | 16,62 | 20,16 | 20,39 | 1,227 | 1,012 |

## FPS z paczką (`-Poptimization`: Sodium, Nvidium, C2ME; świat z jaskiniami; 8 punktów na okręgu 40 bloków, okap = 42 bloki nad gruntem, oko = wysokość oczu); próg ≥ 0,8 × M2

| przelot | M2 FPS | F7 FPS | iloraz | M2 GPU ms/klatkę | F7 GPU ms/klatkę | F7 czas klatki − M2, ms |
|---|---|---|---|---|---|---|
| REAL oak_hornbeam_1 okap | 1578 | 1367 | 0,866 | 0,48 | 0,58 | 0,098 |
| REAL oak_hornbeam_1 oko | 1514 | 1579 | 1,043 | 0,23 | 0,33 | -0,027 |
| REAL fresh_pine_1 okap | 1410 | 1303 | 0,924 | 0,35 | 0,63 | 0,059 |
| REAL fresh_pine_1 oko | 1335 | 1464 | 1,096 | 0,21 | 0,50 | -0,066 |
| REAL lowland_beech_1 okap | 1594 | 1206 | **0,756** | 0,49 | 0,67 | 0,202 |
| REAL lowland_beech_1 oko | 1535 | 1415 | 0,922 | 0,24 | 0,53 | 0,055 |
| REAL alder_carr_1 okap | 1622 | 1245 | **0,767** | 0,47 | 0,67 | 0,187 |
| REAL alder_carr_1 oko | 1512 | 1582 | 1,046 | 0,23 | 0,38 | -0,029 |
| REAL upland_fir_1 okap | 1619 | 1265 | **0,781** | 0,47 | 0,66 | 0,173 |
| REAL upland_fir_1 oko | 1529 | 1513 | 0,989 | 0,23 | 0,40 | 0,007 |
| REAL bog_woodland_1 okap | 1615 | 1492 | 0,924 | 0,35 | 0,53 | 0,051 |
| REAL bog_woodland_1 oko | 1529 | 1549 | 1,013 | 0,21 | 0,38 | -0,008 |
| REAL floodplain_1 okap | 1582 | 1349 | 0,853 | 0,50 | 0,61 | 0,109 |
| REAL floodplain_1 oko | 1528 | 1543 | 1,010 | 0,25 | 0,28 | -0,006 |
| REAL montane_spruce_1 okap | 1629 | 1369 | 0,840 | 0,45 | 0,47 | 0,117 |
| REAL montane_spruce_1 oko | 1562 | 1331 | 0,852 | 0,21 | 0,47 | 0,111 |
| GAMEPLAY oak_hornbeam_1 okap | 1644 | 1322 | 0,804 | 0,45 | 0,63 | 0,148 |
| GAMEPLAY oak_hornbeam_1 oko | 1541 | 1588 | 1,030 | 0,22 | 0,33 | -0,019 |
| GAMEPLAY fresh_pine_1 okap | 1647 | 1343 | 0,815 | 0,38 | 0,60 | 0,137 |
| GAMEPLAY fresh_pine_1 oko | 1551 | 1478 | 0,953 | 0,21 | 0,48 | 0,032 |
| GAMEPLAY lowland_beech_1 okap | 1663 | 1175 | **0,707** | 0,45 | 0,73 | 0,250 |
| GAMEPLAY lowland_beech_1 oko | 1553 | 1536 | 0,989 | 0,23 | 0,42 | 0,007 |
| GAMEPLAY alder_carr_1 okap | 1660 | 1488 | 0,897 | 0,33 | 0,53 | 0,069 |
| GAMEPLAY alder_carr_1 oko | 1532 | 1589 | 1,038 | 0,24 | 0,41 | -0,024 |
| GAMEPLAY upland_fir_1 okap | 1643 | 1326 | 0,807 | 0,46 | 0,59 | 0,145 |
| GAMEPLAY upland_fir_1 oko | 1547 | 1518 | 0,981 | 0,22 | 0,39 | 0,012 |
| GAMEPLAY bog_woodland_1 okap | 1678 | 1395 | 0,831 | 0,41 | 0,59 | 0,121 |
| GAMEPLAY bog_woodland_1 oko | 1573 | 1551 | 0,986 | 0,22 | 0,43 | 0,009 |
| GAMEPLAY floodplain_1 okap | 1546 | 1299 | 0,840 | 0,51 | 0,65 | 0,123 |
| GAMEPLAY floodplain_1 oko | 1537 | 1512 | 0,984 | 0,23 | 0,34 | 0,011 |
| GAMEPLAY montane_spruce_1 okap | 1662 | 1292 | **0,777** | 0,42 | 0,60 | 0,172 |
| GAMEPLAY montane_spruce_1 oko | 1545 | 1517 | 0,982 | 0,21 | 0,43 | 0,012 |

## Koszt kroków F: chunki na sekundę z C2ME bez jaskiń, F7 wobec buildu przed F1 (`c33a916`), bez progu

| miejsce | przed F1 | F7 | iloraz |
|---|---|---|---|
| REAL oak_hornbeam_1 | 111,50 | 107,20 | 0,961 |
| REAL fresh_pine_1 | 143,75 | 141,65 | 0,985 |
| REAL lowland_beech_1 | 147,75 | 139,90 | 0,947 |
| REAL alder_carr_1 | 163,45 | 138,15 | 0,845 |
| REAL upland_fir_1 | 167,75 | 134,35 | 0,801 |
| REAL bog_woodland_1 | 167,60 | 143,20 | 0,854 |
| REAL floodplain_1 | 185,30 | 138,05 | 0,745 |
| REAL montane_spruce_1 | 165,25 | 124,65 | 0,754 |
| GAMEPLAY oak_hornbeam_1 | 166,30 | 155,90 | 0,937 |
| GAMEPLAY fresh_pine_1 | 160,25 | 157,50 | 0,983 |
| GAMEPLAY lowland_beech_1 | 176,95 | 155,10 | 0,877 |
| GAMEPLAY alder_carr_1 | 220,30 | 180,05 | 0,817 |
| GAMEPLAY upland_fir_1 | 161,25 | 146,90 | 0,911 |
| GAMEPLAY bog_woodland_1 | 235,70 | 235,80 | 1,000 |
| GAMEPLAY floodplain_1 | 179,75 | 162,10 | 0,902 |
| GAMEPLAY montane_spruce_1 | 162,20 | 128,45 | 0,792 |
| **min / mediana / max** | | | 0,745 / 0,889 / 1,000 |

## Uwagi do metody

- **Pierwszy pomiar `load` (2026-11-10, zastąpiony powtórką).** Przebieg M2 r4 (23:10) był w każdym z 16 miejsc o 10–33% wolniejszy niż M2 r1–r3 (np. GAMEPLAY `oak_hornbeam_1` 182,8 wobec 220,9 / 224,1 / 227,7), a do tego dzienniki F7 rund 1–2 pochodziły z pary z buildem przed F1 (sterownik zapisywał `<część>_r<n>_head.log` w obu parach), więc para ABBA nie była zachowana: mediana M2 z 3 cichych i 1 wolnego przebiegu zaniżała M2 i zawyżała ilorazy (np. GAMEPLAY `montane_spruce_1` 0,808 → w powtórce 0,719, REAL `lowland_beech_1` 0,725 → 0,698). Dawny opis „iloraz ±0,01” był błędny. Przebiegi F7 obu nocy i powtórki są zgodne (np. REAL `oak_hornbeam_1` 108,6 / 100,5 / 105,8 / 108,6 w pierwszym pomiarze, 104,7–108,0 w powtórce), więc zakłócony był przebieg M2 r4, nie przebiegi F7. Przyczyny spowolnienia M2 r4 nie ustalono (gry nie było, CPU 5%).
- **Obciążenie GPU przed przebiegiem.** Dawny sterownik logował surowe wykorzystanie GPU z `nvidia-smi` (próg < 50%); od 22:58 było to 18–23%. W powtórce zapisano też zegar i stan P: w spoczynku karta stoi w P8 przy 210 MHz (maks. 3105 MHz) i pokazuje 12–29% wykorzystania od samej aplikacji pulpitu, czyli 1–2% karty. Takie same 18–23% w pierwszym pomiarze to najpewniej ten sam spoczynek, nie stały konsument GPU (zegara wtedy nie logowano). Powtórka używa progu obciążenia ≤ 5% karty (wykorzystanie × zegar / zegar maks.) i zapisuje obciążenie w czasie przebiegu (średnio 7–12% karty, razem z klientem testu; M2 i F7 podobnie).
- **Przebiegi F7 rund 1–2 `stages`** pochodzą z pary z buildem przed F1 (04:25–04:32, ta sama usterka nazw); etapy obu buildów szły w tym samym spoczynku GPU, a werdykt FEATURES / LIGHT ma duży zapas, więc ich nie powtarzano.
- **Etapy** (`stages -Psites=slice`) profilują 20 obszarów miejsc wycinka (`profileSlice`; 5 miejsc × 2 skale × 2 tryby), nie miejsca lasów F1–F6; koszt lasów F1–F6 w generacji pokazują chunki na sekundę (`load`).
- **Strażnik z jaskiniami**: M2 nie czyta `-Pcaves`, więc iloraz to F7 z jaskiniami wobec M2 w tych samych rundach (2 przebiegi). Mediana M2 z 6 przebiegów (`load` + `caves`) daje dla `upland_fir_1` REAL 0,373, dla `montane_spruce_1` GAMEPLAY 0,48: wynik nie zależy od wyboru mediany bazy.
- **Szacunek dla buildu przed F1 z jaskiniami** (rachunek, nie pomiar: koszt jaskiń F7 w ms na chunk dodany do czasu buildu przed F1 bez jaskiń): `upland_fir_1` REAL ok. 0,43 × M2, `montane_spruce_1` GAMEPLAY ok. 0,55 × M2. Strażnik w górach był więc spełniony bez zapasu jeszcze przed krokami F; las planera kroków F3 i F6 (ok. +1,5 ms na chunk) przesuwa oba miejsca pod próg.
- **Przeloty**: pora roku i grafika domyślne uprzęży (bez `-PperfSeason`), zasięg 12 chunków, bez limitu klatek; 2 przebiegi na build. Przelotów buildu przed F1 nie mierzono (polecenie 8 tego nie przewidywało).
