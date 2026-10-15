# Pionowy wycinek „Grąd i bór świeży”. Plan wykonawczy

Data: 2026-10-14. Status: **zaakceptowany 2026-10-15** (punkt kontrolny CP-0: D1 (b), D2 (b), D5 (a) z zasobami na CC BY 4.0, PW-1 (a), PW-2 (a); pozostałe decyzje §0 przyjęte domyślnie według rekomendacji, „domyślne, do potwierdzenia”). Decyzje z §0 są w `docs/00-decyzje-do-podjecia.md`, sekcja G „Pionowy wycinek”. Stan kroków: na końcu dokumentu („Stan po V0” i kolejne).

**Podstawa.** Plan łączy trzy projekty wycinka: „najpierw gra”, „wierność przyrodnicza” i „fundamenty techniczne”, według ocen dwóch sędziów. Pierwszy sędzia oceniał wartość dla gracza i realizm, drugi wykonalność techniczną i ryzyko. Wyniki: „najpierw gra” 7 i 7, „fundamenty” 6,5 i 7, „wierność” 6 i 3. Bazą jest „najpierw gra”: kolejność kroków, wczesne punkty kontrolne, dźwięk i fauna w wersji minimum. Przeszczepione elementy:
- z „fundamentów”: docelowe klucze `tree/<gatunek>` z zastępstwami M2 pod `tree_substitute/`, nowe drzewa w ciągu Bśw → BMśw → LMśw → grąd, test migawki rejestru i zamrożenie identyfikatorów, krzywe koloru gatunków na „surowym” kolorze listowia, uproszczona mapa drzewostanów, pomiar kosztu przed runem i fauną, model sarny do samego renderu przed AI;
- z „wierności”: karta norm mierzona w grze z pomiarem bazowym M2, zima koron po stronie klienta (z marcescencją dębu i grabu), wygląd faz runa po stronie klienta, forma śmierci według gatunku, pniaki tylko w lesie gospodarczym, odnowienie sosny tylko w lukach, przylaszczka, pejzaż dźwiękowy ptaków;
- poprawki sędziów: przycinanie koron sprawdzone próbą przed planerem, stan generacji bez fazy sezonu, `checkArt` na pikselach, despawn i dwa źródła spawnu kalibrowane osobno, mszysty wierzch pod krzewinkami, orlica w BMśw zamiast w borze świeżym, realne szacunki.

Źródła: `docs/BRIEF.md`, `docs/00`, `docs/01` §5, §6, §8, `docs/03-m2-biomy.md` §1, §8.4–8.8, §9, raporty wycinka (`research-ekologia.md`, `research-technika-drzew.md`, `research-fauna-grafika.md`; od V0 w `docs/research/wycinek/`). Oznaczenia: **[Ź]** liczba z raportu lub źródła, **[S]** szacunek planu, **CP-x** punkt kontrolny użytkownika, **PW-x** nowa decyzja wycinka.

---

## 0. Decyzje dla użytkownika

Najważniejsze są na początku. Decyzje oznaczone „do CP-0” trzeba rozstrzygnąć przed startem. Pozostałe przechodzą domyślnie z rekomendacją i dopiskiem „domyślne, do potwierdzenia” (zasada z 2026-10-09).

### 0.1 Otwarte decyzje projektu (D1, D2, D5, B6)

| # | Pytanie | Warianty | Rekomendacja |
|---|---|---|---|
| **D1** (do CP-0) | Pełne zestawy drewna | (a) w wycinku żadnych: pnie dają najbliższą wanilijną kłodę; (b) **zestaw „lite” sosny**: kłoda, drewno, okorowane, deski, liście, sadzonka; +1 dzień, ok. 5 tekstur; (c) pełny zestaw sosny (drzwi, tabliczki, półka, łodzie): +2–3 dni, ok. 20 tekstur | **(b)**. Sosna to ok. 58% lasów, więc ścięta sosna nie powinna dawać desek świerkowych. Przejście z LITE na FULL tylko dopisuje bloki i nie psuje zapisów. Pełny zestaw sosny to opcja O3. Listę docelową (FULL: sosna, buk, jodła, olsza, grab, lipa, jesion, klon, modrzew, wierzba; reszta LITE) potwierdzamy w M3 |
| **D2** (do CP-0) | Dąb, brzoza, świerk, topola: bloki wanilii czy własne | (a) wszystko wanilijne (przy cienkich pniach niemożliwe); (b) **własny cienki pień, blok liści i sadzonka dla każdego gatunku wycinka**. Brzoza i świerk biorą teksturę wanilii przez id modelu. Dąb dostaje własną korę (ciemną, bruzdowaną). Łup, kłody i deski dębu, brzozy i świerka są wanilijne (`minecraft:oak_log` w `#oak_logs`). Topola nie wchodzi do wycinka; (c) wszystko własne | **(b)**. Blok liści musi być własny (kolor gatunku, faza drzewa, zima), a cienki pień wynika z D8. Kora wanilii pasuje do brzozy i świerka, ale nie do dębu szypułkowego. Plików wanilii nie kopiujemy, tylko odwołujemy się do nich po id (EULA) |
| **D5** (do CP-0) | Tekstury i modele: kto i jak, licencja | (a) **agenci proceduralnie** (`tools/art`, `tools/fauna`) z lintem stylu, `checkArt` i punktami kontrolnymi; ręczne poprawki użytkownika (Aseprite, Blockbench) w katalogu `overrides/`, który przechodzi lint bez porównania z generatorem; (b) grafik zewnętrzny poprawia bazę agentów; (c) gotowe paczki (prawie nic nie pasuje: BY-SA, NC i ARR wykluczone). Licencja zasobów: CC BY 4.0, MIT albo CC0 | **(a), zasoby na CC BY 4.0** (`LICENSE-assets`, `CREDITS.md` z generatora), kod nadal MIT. Dźwięki tylko CC0 i CC BY. Po CP-A, jeśli styl nie wystarczy, otwiera się (b). Zakaz kopiowania lub przerysowywania plików wanilii, Naturalist, Alex's Mobs, BOP i Pixel Perfection |
| **B6** | Kolor liści | (a) **własne krzywe gatunków** na „surowym” kolorze listowia biomu (`RAW_FOLIAGE` w `ColorResolverRegistry`, Biome Blend zostaje), z fazą drzewa `phase` 0–3 z hasza pnia. Działa bez SS, a z SS nie sezonuje drugi raz. SS jest tylko źródłem pory roku i wyłącznikiem `changesFoliageColor`; (b) kolor biomu sezonowany przez SS × mnożnik gatunku; (c) statyczne bloki jesienne jak topola 26.3 | **(a), z (b) jako zapasem.** Tylko (a) daje różne terminy przebarwienia (brzoza i lipa od końca IX, dąb późno) i zmienia kolory także bez SS. Ryzyko: Sodium z paczki i własny `ColorResolver` (potwierdzony tylko w gałęzi deweloperskiej). Sprawdza to próba V3 przed CP-A. Jeśli nie przejdzie, wycinek bierze (b), a (a) przechodzi do M3 |

### 0.2 Nowe decyzje wycinka

| # | Pytanie | Warianty | Rekomendacja |
|---|---|---|---|
| **PW-1** (do CP-0) | Zakres i czas | (a) **ten plan**: ok. 31–42 dni pracy agentów, kalendarzowo ok. 22–28 dni, pierwszy spacer (CP-B) ok. 13.–17. dnia; (b) „najpierw gra” bez dodatków: jeden stan lasu, mnożnik koloru, bez zimy koron, bez karty norm; ok. 24–30 dni, CP-B ok. 10.–12. dnia; (c) jak (a), ale zima koron i krzywe gatunków jako opcje po CP-D: ok. 27–36 dni | **(a)**. Dodatki względem (b) to ok. 7–10 dni: zima i krzywe działające bez SS, dwa stany lasu (D9), klucze docelowe, karta norm, fenologia runa, pejzaż ptaków. Dają realizm, który wycinek ma sprawdzić. Plan skrócenia w §7 |
| **PW-2** (do CP-0) | Las gospodarczy w trybie N (D9) | (a) **suwak D9 w obu trybach, domyślnie 85/15**, jak w decyzji D9; (b) tryb N to 100% lasu naturalnego, suwak tylko w D; (c) suwak w obu trybach, domyślnie 0/100 w N i 85/15 w D | **(a)**. Tryb N jest domyślny do M8, więc przy (b) lub (c) domyślny świat nie pokazałby typowego polskiego lasu. Plany „fundamenty” i „wierność” proponowały (b) i (c). Ich argument („Polska bez ludzi”) jest spójny, ale zmienia decyzję użytkownika |
| **PW-3** | Gdzie rosną nowe drzewa | (a) globalna podmiana `tree/<gatunek>` we wszystkich biomach; (b) **feature proceduralny pod docelowym kluczem `tree/<gatunek>`, a zastępstwa M2 pod `tree_substitute/<gatunek>`**. Palety grądu, Bśw, BMśw i LMśw wskazują `tree/`, pozostałe biomy `tree_substitute/`; (c) tylko dwa biomy wycinka | **(b)**. Ciąg Bśw → BMśw → LMśw → grąd [Ź ekologia §8] dostaje drzewa w jednej skali (Z10). Przy (a) jesion, dąb i sosna o wysokości 25–30 bloków stanęłyby w łęgach, olsach i jedlinach obok olszy, wiązu i jodły wanilii o wysokości 8–18 bloków, w obrębie jednego biomu (`tree/birch` występuje w paletach 13 razy). Klucze worldgen nie trafiają do zapisu chunków, więc przemianowanie jest bezpieczne. M3 zmienia już tylko palety |
| **PW-4** | Struktura drzewostanu | (a) jeden stan pośredni (starodrzew 100–140 lat z lukami); (b) **mapa drzewostanów „lite” (`StandMap`)**: wydzielenia z flagą gospodarczy/naturalny według D9, 3 klasy wieku (drągowina, drzewostan dojrzały, starodrzew), luki tylko w naturalnym, pniaki tylko w gospodarczym, granice nieregularne (Z10), bez linii oddziałowych; +2–3 dni; (c) pełna mapa z oddziałami, zrębami i uprawami w rzędach (M3, linie w M8) | **(b)**. 85% polskich lasów to drzewostany gospodarcze. Bez dwóch stanów wycinek nie sprawdzi pniaków, klas wieku ani różnicy w ilości martwego drewna. `StandMap` jest czystą funkcją ziarna i pozycji (bez kodu siedliska), więc służy też przycinaniu koron (PW-9) |
| **PW-5** | Martwe drewno w lesie naturalnym | Las gospodarczy zawsze według średnich WISL: bór ok. 0,1 kłody i 1–2 pniaki na chunk, grąd ok. 0,25 obiektu na chunk [Ź]. Las naturalny: (a) „stan właściwy” GIOŚ, czyli grąd ok. 0,5 obiektu na chunk (≥ 5 grubych kawałków na ha), bór ok. 0,4; (b) **poziom rezerwatów**: grąd jak BPN, ok. 2 obiekty na chunk (ok. 130 m³/ha), bór jak Taborz, ok. 1 (ok. 65 m³/ha) | **(b)**, oceniane przy CP-C, z (a) jako zapasem, gdyby las wyglądał na wiatrołom. Las naturalny to tylko 15% wydzieleń, a ilość martwego drewna najmocniej odróżnia go od gospodarczego |
| **PW-6** | Własne bloki runa przed M4 (wyjątek od M2-D) | (a) **5 bloków**: borówka czarna (kwiat, owoce do zebrania, jesień), zawilec gajowy, przylaszczka, igliwie (dywan segmentowy), orlica (2 bloki); (b) 4, bez przylaszczki; (c) ok. 25 gatunków (plan „wierność”) | **(a)**. To najbardziej rozpoznawalne elementy dna obu lasów i ich pory roku. Przylaszczka daje dwufazowy aspekt (najpierw fioletowy, potem biały łan zawilca) za koszt jednego wpisu w rodzinie bloków. Reszta zostaje na zastępstwach wanilii do M4 |
| **PW-7** | Mech pod krzewinkami | Jedna kolumna mieści jeden blok runa, a w borze świeżym mchy pokrywają 60–95%, borówka 40–70% [Ź]. Warianty: (a) wierzch `moss_block` płatami (zmiana wierzchu w dekoracji, jak `SoilBlend`); (b) **właściwość `mossy` w blokach runa boru**: model dwuwarstwowy z mchem pod krzewinką albo igliwiem, a na kolumnach bez krzewinek wanilijny `moss_carpet`; (c) mech i borówka dzielą kolumny (razem ≤ 100%) | **(b)**. Nie zmienia wierzchu ani heightmap, a pokrycia z raportu ekologii stają się możliwe. Koszt: 2 stany na blok i ok. 0 dni |
| **PW-8** | Jak runo zmienia porę roku | (a) **wygląd faz geofitów po stronie klienta** (owinięty model FRAPI czytający fazę roku, wspólny z zimą koron). Stan serwera tylko dla owoców borówki: losowy tick plus nadrabianie po wczytaniu chunka, odroczone do następnego ticku. Generacja stawia stan neutralny; (b) fazy jako stan bloku, losowy tick z nadrabianiem; (c) bez fenologii runa w wycinku | **(a), z (b) jako zapasem**, gdyby próba V3 (owinięcie modelu przy Sodium i MoreCulling) nie przeszła. Przy (a) wszystkie widoczne chunki są w bieżącej fazie, bez pakietów bloków. Stan zmieniany losowym tickiem ma średnio ok. 68 s opóźnienia, a poza zasięgiem symulacji zostaje stary |
| **PW-9** | Przycinanie koron do sąsiadów | Rozstrzyga próba V5. Warianty: (a) **terytoria koron z samego wzoru kandydatów pni, `StandMap` i hasza**, bez gatunku i kodu siedliska; (b) kody siedliska w kolumnach kandydatów obszaru 5 × 5 chunków (ok. 270 próbek modelu na chunk, koszt do zmierzenia); (c) przyjąć wyższy próg determinizmu w grądzie | **(a)**. Na etapie FEATURES etap TERRAIN, a z nim `ChunkHabitats`, mają tylko chunki w promieniu 1, a korony sąsiadów z chunków w promieniu 2 są wtedy niedostępne. Kandydaci i `StandMap` są czystą funkcją ziarna, więc terytorium każdego pnia jest znane wszędzie. Kandydat, który nie stał się drzewem, zostawia lukę w okapie. W naturze takie odstępy też są (crown shyness) |
| **PW-10** | Budżety po drzewach 1:1 | (a) **cały chunk ≤ 48 ms twardo** w każdym obszarze, skali i trybie; FEATURES i LIGHT dostają nowe budżety z pomiaru V10 (propozycja [S]: FEATURES ≤ 2,5 × baza M2, LIGHT ≤ 1,6 ×); FPS w stałym przelocie z paczką ≥ 0,8 × M2; chunki na sekundę z C2ME ≥ 0,8 × M2; (b) stare budżety M2-14 (niewykonalne przy 2 × więcej liści w grądzie); (c) tylko cały chunk | **(a)**. Liczby do korekty po V10, decyzja przy CP-B |
| **PW-11** | Zamrożenie zapisu | (a) **po CP-B**: identyfikatory bloków i przedmiotów, nazwy i zakresy właściwości (`radius` 1–7, `parent`, `phase`, `mossy`, `berries`) oraz kolejność nowych enumów, pilnowane testem migawki rejestru; (b) dopiero przy wydaniu publicznym | **(a)**. Użytkownik gra w zapisanych światach. Kora sosny: **osobny blok `scots_pine_upper_trunk`** („lustro” w górnej części), a nie reguła z promienia, ustalone przed zamrożeniem |
| **PW-12** | Rozmiar drzew w skali rozgrywki | (a) **1:1 jak w REAL** (A7), te same gęstości na chunk; odległości planerów (luki, wydzielenia, ekotony) ×k (Z7); (b) wysokości ×0,75 w GAMEPLAY | **(a)**. Drzewo odnosi się do wzrostu gracza, a nie do krajobrazu. Ocena przy CP-B |
| **PW-13** | Potwory pod okapem w dzień | (a) **gdy pomiar V10 pokaże problem**, liście gatunków światłożądnych (sosna, brzoza) przepuszczają światło nieba, a grąd zostaje ciemny jak ciemny las wanilii; (b) przepuszczają liście wszystkich gatunków (tańsze LIGHT, słabszy cień); (c) przyjąć potwory | **(a)** |
| **PW-14** | Fauna wycinka (dawne F4) | (a) sarna, dzik, wiewiórka, sójka jako encje, żaba, dzięcioł; (b) **minimum**: sarna i dzik jako encje, żaba trawna jako `frog_variant`, dzięcioł i tło ptaków jako dźwięk, skrzek sójki płoszący sarny jako sam dźwięk. Wiewiórka i sójka jako opcje O1 i O2 | **(b)**. Wspinanie po cienkich pniach i lot w gęstych koronach to osobne ryzyka. Wiewiórka i sójka wchodzą po CP-D, gdy styl sarny zostanie przyjęty |
| **PW-15** | Wanilijne zwierzęta i gęstość (dawne F1, F2) | Wanilia: (a) zostawić; (b) **usunąć wilka i królika ze wszystkich lasów** (wspólny profil `spawns()`), lis zostaje, bo `minecraft:fox` to lis rudy; (c) usunąć wszystkie. Gęstość: (a) realna (niewidoczna); (b) **ok. ×20–50**; spawn przy generacji (`creature_world_gen_spawn_probability`) i spawn naturalny (wagi w limicie `CREATURE` 10) kalibrowane osobno; (c) własny spawner (M6) | **(b) i (b)**. Prawdopodobieństwo 0,15 przy grupach 1–3 dałoby ok. 0,2 sarny na chunk, czyli 100–250 razy więcej niż w naturze, a limit 10 zwierząt tego nie ogranicza. Startowo 0,02–0,04, kalibracja przy CP-D |
| **PW-16** | Pejzaż dźwiękowy | (a) brak; (b) **według biomu, podsezonu i pory dnia**: zięba, kos, rudzik i śpiewak w grądzie, sosnówka, czubatka i mysikrólik w borze, chór o świcie IV–VI, cisza ptaków śpiewających zimą poza sikorami, dzięcioł bębniący I–V, puszczyk nocą; (c) ptaki jako encje (M7) | **(b)**. Duże zanurzenie w lesie za ok. dzień pracy |
| **PW-17** | Kolejność względem M2 | (a) czekać na S8c; (b) **wycinek startuje od razu, S8c (w toku, `08ee079`) kończy się w osobnym worktree przed CP-B**; (c) S8c po wycinku | **(b)**. S8c zmienia `habitat/ForestCover` i maskę trybu D, a nie pliki wycinka. Zrzuty trybu D przy CP-B powstają już po S8c |

### 0.3 Pozostałe otwarte decyzje, których dotyka wycinek

| # | Rekomendacja dla wycinka |
|---|---|
| T-D5 (zima koron) | **W rdzeniu (V13)**: model gałązek po stronie klienta, marcescencja dębu i grabu, modele innych modów bez zmian. Zapas: opcja po CP-D, jeśli próba V3 nie przejdzie. Bezlistny okap od XI do IV/V to warunek wiosennego aspektu zawilca, czyli wizytówki grądu |
| T-D1, T-D2 (pień) | wariant D (`radius` 1–7 co 2 px, `parent`, `waterlogged`; 84 stany); wariant C (model FRAPI) jako zapas |
| T-D3 (generator) | parametryczny, z lekką kolonizacją przestrzeni w koronach liściastych tylko wtedy, gdy parametry nie wystarczą; szablony NBT po wycinku |
| B5 (zimy) | wycinek nie dotyka; „zima normalna” |
| C2 (hitboxy) | sarna 0,7 × 1,1, dzik 0,9 × 0,9: bez problemu; decyzja zostaje przy łosiu i jeleniu (M6) |
| C3 (populacja) | stan 2025: dzik rzadszy niż sarna (wagi 10 : 4) |
| C5 (ptaki) | w wycinku tylko dźwięk (PW-16); decyzja zostaje do M7 |
| C6 (agresja) | locha z warchlakami (III–VI) tylko ostrzega: chrapnięcie i pozorowana szarża bez obrażeń; decyzja z obrażeniami w M6 |
| C8 (dropy) | brak w wycinku (zgodnie z C12) |
| C9 (zmiana terenu) | buchtowanie dzika z regułą gry `polishforests:boarRooting`, domyślnie włączone, wyłączane też przez `mobGriefing`; działa po generacji, więc nie zmienia determinizmu |
| C10 (dorastanie) | młode tylko w grupach spawnu, dorastanie wanilijne do M6 |
| C11 (kleszcze, komary) | poza wycinkiem |
| F3 (małe zwierzęta ×2) | dotyczy tylko opcji O1 i O2; rekomendacja: poniżej 30 cm ×2, żaba jak w wanilii |
| M2-24 (ziołorośla górskie w GAMEPLAY) | wycinek nie dotyka gór: wariant (b) przenieść do M4 |

### 0.4 Stan M2 do zapisania w V0

- Punkt kontrolny 2 M2 (zrzuty roślinności) użytkownik przyjął 2026-10-14 (notatki projektu), a `docs/00` i BRIEF nadal podają go jako czekający. Zapisać jako PODJĘTY.
- Akceptacji punktu kontrolnego 1 (PNG siedlisk) nikt nie zapisał. Zapytać przy CP-0 albo zapisać jako przyjęty razem z CP-0.
- M2-20 (d2, krok S8c) jest w toku. Domyślne decyzje M2-13 … M2-27 bez zmian.
- **Zapisane w V0 (2026-10-15):** punkt kontrolny 2 jako zaakceptowany 2026-10-14 (wprost), punkt kontrolny 1 jako przyjęty razem z nim (`docs/00` E, „Stan M2”); S8c i jego runda 1 poprawek są zakończone (`9f627cd`), więc M2 jest zamknięty.

---

## 1. Cel i zakres

**Cel.** Gracz idzie przez grąd i bór świeży w obu skalach i obu trybach i ma wrażenie kompletnego, żywego polskiego lasu. Widzi pokrój i korę gatunków, dwa piętra grądu, las gospodarczy i naturalny, martwe drewno, runo z porami roku, sarny i dziki, słyszy ptaki. Wycinek sprawdza w grze, czy warstwy M3, M4 i M6 do siebie pasują. Systemy są od początku ogólne (gatunek, siedlisko i archetyp to dane), ale dostają tylko dane dwóch lasów i pasa styku.

### 1.1 Co wchodzi

| Warstwa | Zakres |
|---|---|
| Biomy | `oak_hornbeam_forest` i `fresh_pine_forest` w pełni (drzewa, martwe drewno, runo, mchy, fauna). `mixed_pine_forest` (BMśw) i `mixed_forest` (LMśw) dostają nowe drzewa i `StandMap`, a runo tylko przez wskaźniki pasa styku: orlica i dąb w podszycie w BMśw, konwalia i leszczyna w LMśw (PW-3). Obie skale, oba tryby |
| Gatunki drzew | sosna (pień dolny i górny „lustro”), świerk, brzoza, dąb, lipa, grab oraz wspólny archetyp „liściasty szeroki” dla buka, klonu i jesionu (własna kora buka z prototypu, klon i jesion ze wspólną korą zastępczą) |
| Bloki drzew | `TrunkBlock` (wariant D, 84 stany na gatunek), blok liści gatunku (`TintedParticleLeavesBlock`, `phase` 0–3), sadzonka i doniczka, zestaw LITE sosny (D1 b). Łup: r ≤ 2 daje patyki, r ≥ 3 kłodę gatunku albo najbliższą kłodę wanilii |
| Generator | `TreeShapePlan` (czysta Java): szkielet trzech archetypów (iglasty, liściasty smukły, liściasty szeroki), model rury, wokselizacja DDA, skupienia liści z pustym środkiem, BFS odległości ≤ 6, przycięcie koron według PW-9, promień korony ≤ 16. Podgląd PNG, sadzonki rosnące w drzewo z planera |
| Drzewostan | `StandMap` lite (PW-4). Grąd naturalny: a1 3–5 na chunk (28–40 bloków), a2 z grabu 6–9 na chunk (15–22 bloków), luki 18–45 bloków. Grąd gospodarczy: jednowiekowy, a1 22–30 bloków, Db 50–80% z domieszkami. Bór: So 80–95%, Brz 5–15%, Św w a2 na NE (rampa O z M2 §9), sosna odnawia się tylko w lukach, dąb w lesie naturalnym bez podrostu. Korony na skraju lasu asymetryczne w stronę otwartą |
| Martwe drewno | kłoda leżąca, posusz, złom i pniak z tego samego wokselizatora i bloków pnia. Forma śmierci według gatunku (świerk: wykrot i posusz, dąb: posusz i złomy konarów, sosna: posusz i złomy, grab i lipa rzadko). Pniaki tylko w lesie gospodarczym. Normy według PW-5. Wanilijne `fallen_*` usunięte z palet wycinka |
| Runo i mchy | 5 bloków z PW-6, mszysty spód według PW-7, reszta runa wanilijna (`leaf_litter`, konwalia, `fern`, `moss_carpet`). Pokrycia z §6 (karta norm) |
| Pory roku | krzywe kolorów gatunków (B6 a), zima koron z marcescencją (T-D5), fazy runa po stronie klienta, owoce borówki jako stan serwera (PW-8). Wszystko działa z SS i bez niego (`FallbackCalendar`) |
| Fauna | sarna i dzik (GeckoLib 5.5.7 w pakiecie `com.geckolib`, SmartBrainLib 2.0.3), żaba trawna jako `frog_variant`, bez wilka i królika w lasach (PW-14, PW-15) |
| Dźwięk | `ForestAmbience` z PW-16 i skrzek sójki jako źródło `ForestAlarm` |
| Grafika | `tools/art` (biblioteka, przepisy, palety k-średnich ze zdjęć CC0, lint stylu, `checkArt`) i `tools/fauna` (generator `.geo.json` i animacji, kalibracja osi). Ok. 45–55 tekstur bloków i ok. 6 tekstur zwierząt |
| Miara | karta norm (§6) mierzona w gametest `slice` z pomiarem bazowym M2, pomiar A/B `stages`, FPS w przelocie |

### 1.2 Co nie wchodzi

| Poza wycinkiem | Dokąd |
|---|---|
| drzewa pozostałych biomów (olsza, wierzba, jodła, topola, wiąz, jarząb, modrzew, cis); te biomy zostają na `tree_substitute/` | M3 |
| pełna mapa drzewostanów: oddziały, zręby, uprawy w rzędach, drągowiny gęstsze niż 16 pni na chunk (wymagają drugiego wzoru kandydatów z nową solą); linie oddziałowe i drogi | M3, M8 |
| pełne zestawy drewna, korowanie cienkich pni, pnie 2 × 2 i drzewa pomnikowe z NBT, czoła pni dla każdej średnicy | M3 (O3 dla sosny) |
| pozostałe geofity i runo, mchy gatunkowe, porosty, własne gleby, profile bielicy, torf, trzciny, pałki, huby na cienkich pniach, stadia rozkładu kłód jako bloki | M4 |
| grzybobranie (D4), mrowiska | M4 (mrowisko jako opcja O4) |
| przesunięcie fenologii z pola O i wysokości (klient serwera dedykowanego nie zna modelu krajobrazu) | M4 |
| wiewiórka, sójka jako encja, jeleń, wilk, własny spawner, domy, dorastanie | M6–M7 (O1, O2) |
| dynamika lasu, gatunki obce, sarna polna w trybie D | M8, M6 |

### 1.3 Opcje (każda osobno, po CP-D, odcinane jako pierwsze)

| Id | Opcja | Dni [S] |
|---|---|---|
| O1 | wiewiórka (×2 według F3, wspinanie po pniach r 1–7 i kłodach, skoki między koronami, `AMBIENT`) | 2–3 |
| O2 | sójka jako encja (lot pod koronami, alarm przechodzi z dźwięku na encję, `AMBIENT`) | 1,5–2 |
| O3 | pełny zestaw drewna sosny (`WoodSet` FULL: drzwi, tabliczki, półka, łodzie) | 2–3 |
| O4 | mrowiska rudnicy (ok. 0,13 na ha, skraje i luki borów) [Ź] | 0,5 |

---

## 2. Zasady

Obowiązują Z1–Z10 z `docs/03-m2-biomy.md` §1, zasady pracy A1–A5 i B1–B3 oraz poniższe.

| # | Zasada | Po co |
|---|---|---|
| ZW-1 | **Planer czysty, feature cienki.** Kształt drzewa, `StandMap`, martwe drewno, runo i terytoria koron to czyste funkcje (ziarno, pozycja, parametry), testowalne bez gry i renderowalne do PNG. `TreeStandFeature` woła planer wprost, bo `PlacedFeature.place` nie przekazuje kontekstu (warstwa, wiek, sąsiedzi) | testy niezmienników, determinizm, podgląd bez gry |
| ZW-2 | **Sole i wzór kandydatów.** Literały `derive("…")` zostają. Nowe pola dostają nowe sole: `feature.tree.shape`, `feature.tree.crown`, `feature.stand.cells`, `feature.stand.age`, `feature.stand.natural`, `feature.stand.canopy_gaps`, `feature.deadwood.objects`, `feature.ground.patches`. Wzór kandydatów pni z M2 (`MIN_SPACING` 3, `DENSITY` 16,65) się nie zmienia, bo zmieniłby wszystkie biomy. Piętra i klasy wieku wybierają spośród tych samych kandydatów | inna sól to inny świat; biomy spoza wycinka zostają identyczne |
| ZW-3 | **Docelowe klucze od razu** (PW-3): `tree/<gatunek>` to drzewo proceduralne, a sadzonka wskazuje ten sam klucz. `tree_substitute/<gatunek>` przejmuje zastępstwa M2 | M3 zmienia dane, nie kod |
| ZW-4 | **Zamrożenie zapisu po CP-B** (PW-11). Test migawki rejestru (bloki, właściwości, wartości, przedmioty) z świadomą regeneracją jak złoty test. Nowe wartości tylko na końcu, nowe właściwości z wartością domyślną zgodną ze starymi zapisami | światy z wycinka wczytają się w M3–M7 |
| ZW-5 | **Generacja bez pory roku.** Stan bloków po generacji jest czystą funkcją ziarna, współrzędnych i ustawień. Owoce borówki rodzą się w stanie neutralnym i dostają sezon dopiero w ticku świata. Hasz determinizmu pomija właściwości sezonowe (lista w jednym miejscu, `SeasonalProperties`). Faza drzewa `phase` pochodzi z hasza pnia, więc jest deterministyczna | porównanie z plikiem wzorcowym z innej sesji i innej pory roku |
| ZW-6 | **Ekoton dotyczy każdej warstwy** (Z10). Nowe reguły drzew, martwego drewna, runa i spawnu wybierają się według kodu ekotonu (`Ecotone`, `ChunkEcotones`). Granice wydzieleń `StandMap` są nieregularne (meandry, rampa wieku 3–6 bloków·k) | brak prostych linii |
| ZW-7 | **Obie skale, oba tryby, z SS i bez**, w każdym gametest wycinka. Drzewa i zwierzęta 1:1, odległości planerów ×k | brak „skali zapomnianej” |
| ZW-8 | **Pomiar przed treścią.** Pomiar A/B wobec bazy M2 z V0 w jednej sesji po drzewach (V10), zanim powstanie runo i fauna. Fauna ma własny pomiar MSPT | budżety nie wychodzą po fakcie |
| ZW-9 | **Zasoby z generatora.** Tekstury generuje `tools/art`, a `checkArt` porównuje **zdekodowane piksele RGBA i plik mcmeta**, nie bajty PNG (kompresja zależy od wersji Pillow i zlib). `overrides/` przechodzi tylko lint. Modele bloków, blockstates, łup, tagi, receptury i lang pochodzą z datagenu (`checkDatagen`). Szablony pni `template_trunk_r1…r7` daje własny `DataProvider` | brak dryfu między źródłem a plikami |
| ZW-10 | **Licencje.** Kod tylko na licencjach zgodnych z MIT (Dynamic Trees, Regions Unexplored z adnotacją). Terraform (LGPL) nie wchodzi. Zasoby są własne, wanilia tylko przez id. Dźwięki CC0 i CC BY z manifestem i `CREDITS.md` | D6, EULA |
| ZW-11 | **Praca równoległa.** Dwa tory w osobnych worktree (A5): A „świat” i B „grafika, klient, fauna”. Wspólne pliki: `ModLanguageProvider`, dostawcy tagów, `ModBlocks`, `ModItems`, datagen biomów i `src/main/generated`. Scalamy je w ustalonej kolejności (A przed B), po każdym scaleniu `runDatagen` i `checkDatagen`, a wygenerowanych plików nie scalamy ręcznie. Gametesty w kliencie i pomiary idą po kolei, bo jest jedna maszyna z oknem gry | konflikty scalania, fałszywe pomiary |

---

## 3. Kroki

Ryzyko według B1. Wysokie (2 recenzentów, do 2 rund) mają: rejestracja bloków i encji, datagen, generator i jego integracja, mixiny, render po stronie klienta, budżety. Niskie (1 recenzent, 1 runda) mają: tekstury, dźwięki, dane palet, lang i dokumentacja. Każdy krok kończy się commitem i pushem (A3a), a pełny zestaw idzie przez `tools/dev/run-tests` raz na krok. Dni to dni pracy agentów z recenzjami: dolna granica przy tempie M2, górna według raportów badawczych.

| Id | Krok | Tor | Dni [S] | Ryzyko |
|---|---|---|---|---|
| V0 | przygotowanie | A | 0,5–1 | niskie |
| V1 | `tools/art` i pierwsze tekstury | B | 1,5–2 | niskie (estetyka) |
| V2 | `TrunkBlock`, tablica próbek, migawka rejestru | A | 2–2,5 | **wysokie** |
| V3 | próby renderu klienta | B | 1 | **wysokie** |
| **CP-A** | styl grafiki i pnia (ok. 4. dzień) | | | |
| V4 | karta norm i pomiar bazowy M2 | B | 1–1,5 | średnie |
| V5 | próba przycinania koron | A | 0,5–1 | **wysokie** |
| V6 | planer drzew `TreeShapePlan` | A | 5–7 | **wysokie** |
| V7 | liście gatunku, krzywe kolorów, sadzonki, LITE sosny | B | 1,5–2 | **wysokie** |
| V8 | `tools/fauna` i model sarny (sam render) | B | 2–2,5 | średnie |
| V9 | `StandMap` i drzewostan wycinka | A | 3–4 | **wysokie** |
| V10 | pomiary i budżety | A | 1–1,5 | **wysokie** |
| **CP-B** | las drzew w grze, zamrożenie (ok. 13.–17. dzień) | | | |
| V11 | martwe drewno | A | 1,5–2 | średnie |
| V12 | runo, mchy, ściółka | A | 2–2,5 | **wysokie** (datagen) |
| V13 | wygląd sezonowy klienta: zima koron, fazy runa, owoce | B | 2–2,5 | **wysokie** |
| **CP-C** | pory roku i dno lasu (ok. 19.–23. dzień) | | | |
| V14 | szkielet fauny i sarna | B | 3–3,5 | **wysokie** |
| V15 | dzik | B | 1,5–2 | średnie |
| V16 | pejzaż dźwiękowy i żaba trawna | A | 1–1,5 | niskie |
| V17 | integracja, pełne testy, dokumentacja | A | 1,5–2 | średnie |
| **CP-D** | spacer (ok. 22.–28. dzień) | | | |
| **Razem** | | | **31–42** (kalendarzowo ok. 22–28) | |

### V0. Przygotowanie (A, 0,5–1 dnia)

- **Zakres:**
  - plan do `docs/04-pionowy-wycinek.md`, raporty wycinka do `docs/research/wycinek/`, decyzje z §0 do `docs/00` G, stan M2 według §0.4 (BRIEF, `docs/00` E);
  - SmartBrainLib 2.0.2 → 2.0.3 (`gradle.properties`, `fabric.mod.json`), kompilacja i start gry;
  - gametest `-Pgametest=slice`, na razie tylko szkielet: stałe miejsca (2 grądy, 2 bory świeże, transekt Bśw → BMśw → LMśw → grąd) w REAL i GAMEPLAY, w trybach N i D, znalezione przez `HabitatTargets` i zapisane na stałe jak `GAMEPLAY_SITES`; kamery z wysokości oczu, z 30 m i spod okapu; liczniki z `ModFeatures.STATS`;
  - baza pomiarowa: tag migawki „baza M2” (`main` po S8c albo przed nim, opisane), zrzuty i `stages` w miejscach wycinka;
  - pakiety `block/`, `tree/`, `entity/`.
- **Ukończony, gdy:** `slice` przechodzi na niezmienionym świecie w obu skalach i trybach, zrzuty bazowe leżą w `docs/wycinek/baza/`, SBL 2.0.3 nie psuje `build` ani `fastTest`, a decyzje są zapisane.
- **Ryzyko:** niskie. Transekt z pełnym ciągiem siedlisk może być w GAMEPLAY daleko od startu. Wtedy dwa krótsze transekty (Bśw → BMśw i LMśw → grąd).

### V1. `tools/art` i pierwsze tekstury (B, 1,5–2 dni)

- **Zakres:**
  - `tools/art/`: `texlib.py` (szumy na torusie, Worley, rampy, kwantyzacja, filtr większościowy, stemple, światło, warianty), przepisy gatunków, palety k-średnich ze zdjęć CC0 (do repozytorium idą tylko kolory i odnośnik);
  - lint stylu: 4–9 kolorów, liście 55–70% pikseli, tekstury pod tint w szarości ze średnią jasnością 0,45–0,65, kafelkowanie bez szwów, 16 × 16;
  - zadanie `checkArt` według ZW-9, poza `build`, z czytelnym błędem bez Pythona;
  - generator `CREDITS.md`, plik `LICENSE-assets`;
  - **pierwsze 10 tekstur:** kora sosny dolna (2 warianty) i „lustro”, kora dębu, grabu, lipy i buka, igły sosny, liście liściaste pod tint, borówka;
  - arkusz 8 × i kafel 3 × 3.
- **Ukończony, gdy:** lint i `checkArt` przechodzą, arkusze leżą w `docs/wycinek/grafika/`, a dolna kora sosny ma czytelne płyty (w prototypie rundy 2 była za ciemna).
- **Ryzyko:** subiektywna estetyka, dlatego zaraz potem jest CP-A.

### V2. `TrunkBlock`, tablica próbek, migawka rejestru (A, 2–2,5 dnia)

- **Zakres:**
  - `block/TrunkBlock`: wariant D (`radius` 1–7, `parent` 6 kierunków, `waterlogged`), kształty z `Shapes.rotateAll` (tablica 6 × 7), modele z 7 szablonów `template_trunk_r1…r7` z wysięgiem 8 px i marginesem ε, blockstates przez `PropertyDispatch.modify(PARENT)`;
  - bloki pni dla wszystkich gatunków wycinka, w tym `scots_pine_upper_trunk` (PW-11); kora brzozy i świerka z wanilii przez id;
  - tagi: `#logs`, `#overworld_natural_logs`, `#<gatunek>_logs`, `#mineable/axe`; łup według §1.1; palność 5/5;
  - **test migawki rejestru** (`RegistrySnapshotTest`: bloki, właściwości, wartości, przedmioty; plik wzorcowy z regeneracją `-PwriteRegistrySnapshot`);
  - gametest „tablica próbek” (`-Pgametest=slice -Psites=samples`): kolumny r1–r7 z wysięgiem w 6 kierunkach, złącza r → r i r → r+1, z wodą, na granicy sekcji; z Sodium (`-Poptimization`) i bez, z AO, z obrysem celownika i cząsteczkami niszczenia; ręcznie złożone drzewo testowe (sosna z prostej rury).
- **Ukończony, gdy:** `checkDatagen` jest czysty, zrzuty nie pokazują szczelin ani migotania, wysięg rysuje się poprawnie z Sodium, JUnit kształtów i migawki przechodzi, a liczba nowych stanów jest zmierzona (ok. 10 × 84).
- **Ryzyko:** wysokie (R3: wysięg przy Sodium i AO). Zapas: wariant C, czyli model FRAPI czytający sąsiadów (+1–1,5 dnia, `radius` zostaje).

### V3. Próby renderu klienta (B, 1 dzień)

- **Zakres:** dwie krótkie próby, które rozstrzygają B6 i PW-8 przed CP-A:
  - własny `ColorResolver` `RAW_FOLIAGE` w `ColorResolverRegistry` z `BlockTintSource` gatunku (jedna krzywa próbna), przebudowa siatek `levelExtractor.allChanged()` przy zmianie kroku koloru, z Sodium z paczki i bez, z SS i bez (`-PnoSeasons`); czas klatki przy przebudowie;
  - owinięcie modelu bloku przez `ModelLoadingPlugin` (FRAPI, `emitQuads`): liść próbny z modelem gałązek w fazie bezlistnej i roślina próbna z fazą „pusta”; z Sodium, MoreCulling i trybem Fast.
- **Ukończony, gdy:** obie próby mają zrzuty i wynik „działa / nie działa” z przyczyną, sygnatury FRAPI 26.3 są spisane w `docs/04`, a B6 i PW-8 mają wybrany wariant.
- **Ryzyko:** wysokie, ale to jest właśnie cel kroku. Przy porażce: B6 (b) i PW-8 (b), a zima koron przechodzi do opcji.

### Punkt kontrolny CP-A: styl grafiki i pnia (ok. 4. dzień)

- **Użytkownik dostaje:** stronę z arkuszem 10 tekstur i kaflami 3 × 3, zrzuty z gry (tablica próbek z Sodium i bez, drzewo testowe), wynik prób V3 (liść w 4 porach z SS i bez, gałązki zimowe próbne).
- **Pytanie:** czy styl tekstur i wygląd cienkiego pnia są do przyjęcia. „Nie” zatrzymuje dalsze tekstury: najwyżej 2 rundy, potem warianty (B3), w tym D5 (b).
- **Rozstrzyga:** D5 i licencję zasobów, B6, T-D1, T-D2.

### V4. Karta norm i pomiar bazowy M2 (B, 1–1,5 dnia)

- **Zakres:**
  - `ForestNorms` (czysta Java, liczby i źródła z §6, jak `Calibration`);
  - liczniki w `slice`: powierzchnie próbne 1 ha (100 × 100 bloków) w miejscach wycinka; drzewa według piętra z wysokości, skład, zwarcie (kolumny z liśćmi powyżej 2 bloków), luki, podszyt, pokrycie runa i mchów, martwe drewno w obiektach i m³/ha z wokseli, kolejność wskaźników na transekcie;
  - raport CSV i tabela wobec norm; **pomiar bazowy na M2** (`docs/wycinek/karta-norm-m2.csv`).
- **Ukończony, gdy:** liczniki zgadzają się co do sztuki na ręcznie zbudowanej powierzchni testowej, raport bazowy M2 jest w repozytorium, a test liczy wszystko w obu skalach i trybach.
- **Ryzyko:** pierśnica z promienia co 2 px ma błąd ok. ±15% dla cienkich drzew. Dlatego karta mierzy liczbę pni i objętość z tolerancją ±30%, a nie pierśnicę.

### V5. Próba przycinania koron (A, 0,5–1 dnia, równolegle z V2)

- **Zakres:** prototyp w czystej Javie terytoriów koron według PW-9 (a): komórka mocy (Woronoj ważony) z kandydatów wzoru M2 w promieniu 32 bloków, promień korony z warstwy i klasy wieku z `StandMap` (bez gatunku), asymetria na skraju lasu z flagi „poza lasem” wyliczonej bez `ChunkHabitats` albo tylko dla chunków w promieniu 1. Pomiar kosztu wariantu (b) na próbce 5 × 5 chunków.
- **Ukończony, gdy:** JUnit pokazuje, że terytorium pnia nie zależy od chunka, z którego pytamy, ani od kolejności zapytań; koszt (a) i (b) jest zmierzony; PW-9 ma wariant zapisany w `docs/04`.
- **Ryzyko:** wysokie. Jeśli żaden wariant nie da determinizmu w progach, decyzja (c) idzie do użytkownika przed V6 (B3).

### V6. Planer drzew `TreeShapePlan` (A, 5–7 dni)

- **Zakres:**
  - czysta Java w `tree/`: `TreeRequest` → `TreeVoxels`; parametry gatunku (`SpeciesShape`) na razie jako rekord Java, JSON z kodekiem w M3, gdy parametry się ustalą;
  - archetypy z `06 §9.2` i raportu techniki §3.3: iglasty (sosna z koroną w górnych 25–35% i suchymi kikutami niżej, świerk ze stożkiem i opadającymi końcami), liściasty smukły (brzoza), liściasty szeroki (dąb z grubymi konarami blisko osi, lipa jajowata, grab gęsty i niższy, wspólny dla buka, klonu i jesionu);
  - allometria: wysokość i pierśnica z klasy wieku i warstwy; promienie z modelu rury (n = 2–2,5), promień nierosnący ku końcom;
  - wokselizacja 3D DDA z `parent` w stronę korzenia, typ bloku według promienia (`TrunkBlock` albo pełna kłoda), „lustro” sosny powyżej ok. 2/3 wysokości;
  - korona: skupienia liści na końcach pędów, pusty środek dużych koron, BFS odległości ≤ 6, przycięcie do terytorium z V5, promień ≤ 16;
  - feature `polishforests:procedural_tree` (rekord z `MapCodec`: gatunek, warstwa, klasa wieku): zapis tylko na `#replaceable_by_trees` albo w powietrzu, drewno nadpisuje liście, bez dekoratorów wanilii na cienkich pniach (R8);
  - zadanie `treePreview` (PNG z boku i z góry: gatunek × klasa wieku, drzewostan 3 × 3 chunki).
  - **Kolejność:** najpierw iglaste. Ich PNG idą na stronę „do wglądu” (bez czekania na odpowiedź), dopiero potem liściaste.
- **Ukończony, gdy:**
  - JUnit przechodzi: determinizm, łańcuch `parent` do korzenia, promień nierosnący, liście ≤ 6 od drewna, zapis tylko w 3 × 3 chunkach, brak sporów koron;
  - koszt w `@Tag("cost")`: ≤ 0,2 ms na drzewo iglaste i ≤ 0,5 ms na liściaste szerokie (mediana, jeden wątek);
  - sylwetki gatunków z boku dają się rozróżnić, a recenzenci obejrzeli pełne arkusze (B4 odrzucona).
- **Ryzyko:** wysokie. Schodki na ukośnych konarach (R4); za wolny planer (zapas: biblioteka gotowych szkieletów na gatunek i klasę wieku, obracanych i skalowanych); korony liściaste „zbyt porządne” (zapas: lekka kolonizacja przestrzeni, 150–300 punktów, T-D3).

### V7. Liście gatunku, krzywe kolorów, sadzonki, LITE sosny (B, 1,5–2 dnia)

- **Zakres:**
  - blok liści każdego gatunku (`TintedParticleLeavesBlock`, `phase` 0–3, mcmeta `dark_cutout`, `#minecraft:leaves`); tekstury: igły sosny i liście liściaste własne, brzoza i świerk z wanilii przez id;
  - `SeasonPhase` (jedna faza roku dla klienta i serwera, z SS albo z `FallbackCalendar`), krzywe gatunków z `08 §9` (wiosna, lato, jesień, zima; brzoza i lipa żółte od końca IX, grab żółty → brązowy, dąb późno żółtobrązowy, jesion opada zielono-żółty), 2–3 kroki koloru na podsezon, wyłącznik `changesFoliageColor`; iglaste ze stałym odcieniem i lekkim zimowym przyciemnieniem; kolor przedmiotu liści (`tints`);
  - wariant zapasowy, gdy V3 nie przeszło: odcień biomu × mnożnik gatunku (B6 b);
  - sadzonka gatunku (`TreeGrower` na `tree/<gatunek>` z domyślną młodą klasą wieku), doniczka, kompost, mączka kostna; `canGrow` sprawdza wolne miejsce;
  - zestaw LITE sosny (D1 b): kłoda, drewno, okorowane, deski, receptury, tagi (`#planks`, `#logs_that_burn`), korowanie pełnej kłody (`BlockTransformerHelper.registerStripping`).
- **Ukończony, gdy:** `checkDatagen` jest czysty; zrzuty tablicy w 4 porach roku z SS i bez pokazują rozróżnialne odcienie gatunków i różne terminy przebarwienia; brak podwójnego sezonowania z SS; tryb Fast działa (`instanceof LeavesBlock`); sadzonka w świecie testowym rośnie w drzewo z planera; czas klatki przy przebudowie jest w logu.
- **Ryzyko:** wysokie (render, rejestracja). Zacięcia przy przebudowie siatek (R9).

### V8. `tools/fauna` i model sarny, sam render (B, 2–2,5 dnia)

- **Zakres:**
  - `tools/fauna/`: generator `.geo.json` (format 1.12.0) i `.animation.json` z opisu archetypu w centymetrach (czworonóg), pakowanie UV bez nakładania, malowanie z detalami gatunku (oczy, nos, lustro), chody, podgląd z kodu, walidacja;
  - **kalibracja osi i obrotów w grze:** model testowy z kośćmi w znanych kątach, zrzut z gametestu, test regresji położenia kości;
  - sarna jako encja tylko do renderu (jajo spawnu, bez AI): model (ok. 13 kości), 4 tekstury (koza i kozioł, lato i zima), animacje idle, walk, run, eat, alert, rest.
- **Ukończony, gdy:** zrzut sarny z 4 stron w grze zgadza się z podglądem, test osi przechodzi, a walidator nie zgłasza błędów UV.
- **Ryzyko:** średnie (R17: znak obrotów w GeckoLib 5).

### V9. `StandMap` i drzewostan wycinka (A, 3–4 dni)

- **Zakres:**
  - `tree/StandMap` (czysta Java, tylko ziarno i pozycja, nowe sole z ZW-2): wydzielenia 2–30 ha·k² (Woronoj z meandrami), flaga gospodarczy/naturalny według suwaka D9 (PW-2), klasa wieku (drągowina, drzewostan dojrzały, starodrzew; udziały według rozkładu LP), luki 18–45 bloków co ok. 1–2 ha tylko w lesie naturalnym;
  - `TreeStandPlan` rozpoznaje piętra a1, a2 i podrost (nowe pole palety) spośród kandydatów wzoru M2 (ZW-2); gęstości z §6 (N1), z górną granicą wzoru ok. 16 pni na chunk;
  - grąd: a1 Db, Lp, Kl/Js, Bk, Św na NE; a2 Gb z domieszką Lp; podrost grabu i lipy oraz leszczyna gęściej w lukach; dąb w lesie naturalnym bez podrostu;
  - bór: So, Brz, Św w a2 na NE; podszyt z jałowca, kruszyny i jarzębu 5–30%; sosna odnawia się tylko w lukach;
  - pas styku: BMśw i LMśw z nowymi drzewami (PW-3); ekotony przez kody ekotonu (ZW-6);
  - `tree/` i `tree_substitute/` według PW-3, `TreeStandFeature` woła planer wprost (ZW-1);
  - `BUILDING_GAP` z promienia korony i maska struktur z `ChunkHabitats` (korona nie wchodzi nad dach ani na ulicę);
  - liczniki liści i drewna na chunk w `STATS`; zrzuty `slice`.
- **Ukończony, gdy:**
  - karta norm N1–N7 mieści się w tolerancji w miejscach wycinka (§6), w obu skalach i trybach, dla lasu gospodarczego i naturalnego;
  - `DeterminismClientGameTest` przechodzi z obecnymi progami (0,5% bez paczki, 8% z C2ME) i z haszem według ZW-5;
  - liście do opadnięcia ≤ 0,1%, `HABITAT_MISS` 0, 0 drzew na ulicach i 0 liści pod dachami wiosek (test z S8);
  - `VegetationClientGameTest`, `EcotoneClientGameTest` i `PresentDayClientGameTest` przechodzą, a złoty test jest bez zmian;
  - biomy spoza W1b mają dekorację identyczną z bazą (te same zastępstwa pod nowymi kluczami).
- **Ryzyko:** wysokie (R5, R7, R26: skok skali na styku z biomami spoza pasa).

### V10. Pomiary i budżety (A, 1–1,5 dnia)

- **Zakres:**
  - A/B `stages` z bazą M2 z V0 w jednej sesji (4 przebiegi, mediany), w obu lasach, skalach i trybach oraz w obszarze „area”: FEATURES, LIGHT, cały chunk, liście i drewno na chunk, czas planera; chunki na sekundę z C2ME; pamięć i liczba stanów;
  - FPS w stałym przelocie kamery z paczką i bez (nowy przelot w `performance`);
  - jasność przy gruncie pod okapem grądu w południe i próby spawnu potworów w dzień (PW-13);
  - cięcia według potrzeby, w tej kolejności: puste środki koron, mniejsze skupiska liści, liście sosny i brzozy przepuszczające niebo, rzadsze a2.
- **Ukończony, gdy:** cały chunk ≤ 48 ms wszędzie; FPS i chunki na sekundę w progach PW-10; nowe budżety FEATURES i LIGHT zapisane w `docs/04` jako propozycja do CP-B; potwory w dzień nie częściej niż w ciemnym lesie wanilii albo PW-13 wykonane.
- **Ryzyko:** wysokie (R1, R2). Cięcia nie mogą zejść poniżej N1 i N4 bez decyzji użytkownika (B3).

### Punkt kontrolny CP-B: las drzew w grze (ok. 13.–17. dzień)

To pierwszy moment, w którym użytkownik **gra**.

- **Użytkownik dostaje:**
  - stronę ze zrzutami obu lasów (gospodarczy i naturalny, 3 klasy wieku, z ziemi, z 30 m i spod okapu, REAL i GAMEPLAY, N i D, lato i jesień), z transektem Bśw → grąd i ze stykiem z biomami spoza pasa;
  - arkusze PNG drzew, zrzut sarny z 4 stron (sam model), tabelę karty norm wobec M2, liczby wydajności;
  - ziarno i współrzędne (`/polishforests find biome oak_hornbeam_forest`), listę do sprawdzenia: spacer 10 minut, ścięcie drzewa, posadzenie sadzonki, przelot elytrą nad lasem, `runClient -Poptimization`.
- **Pytania:** pokrój i gęstość; budżety (PW-10); skala w GAMEPLAY (PW-12); czy styl sarny pozwala robić AI.
- **Po akceptacji:** zamrożenie zapisu (PW-11, ZW-4).

### V11. Martwe drewno (A, 1,5–2 dnia)

- **Zakres:**
  - `tree/DeadwoodPlan` (czysty, nowa sól): kłoda leżąca (z kikutem korzeni lub bez), posusz (pień bez liści, złamany wierzchołek), złom (pień urwany na 2–8 blokach, korona leżąca obok), pniak (0,3–0,6 m, tylko las gospodarczy), wszystkie z wokselizatora V6 i bloków pnia;
  - forma śmierci według gatunku; normy według PW-5 i §6 (N10); kierunek kłody wzdłuż poziomicy i do wnętrza okna zapisu; `leaf_litter` i `moss_carpet` przy kłodach;
  - klucze `deadwood/<gatunek>` zostają, a zmienia się plik featura.
- **Ukończony, gdy:** liczba obiektów na chunk w normie ±30% w obu lasach, stanach i trybach; kłody leżą na gruncie (≤ 1 blok luki na ≥ 80% długości); nie ma kłód w wodzie ani na ulicach; determinizm bez zmian; FEATURES w budżecie PW-10.
- **Ryzyko:** średnie. Kłody na stokach mogą wisieć albo wbijać się w teren (zapas: krótsza kłoda).

### V12. Runo, mchy, ściółka (A, 2–2,5 dnia)

- **Zakres:**
  - rodzina bloków runa (`SeasonalPlantBlock`, `GroundCoverBlock`, `BerryShrubBlock`), w której nowy gatunek to wpis danych i tekstura:
    - borówka czarna: krzewinka bez kolców i spowolnienia, zastępuje `bush` z M2-13, owoce do zebrania i zjedzenia (przedmiot „jagody”), `berries` jako stan serwera (PW-8), `mossy`;
    - zawilec gajowy i przylaszczka: dwie warstwy jak `wildflowers` (łodyga pod tint trawy, kwiat bez tintu);
    - igliwie: dywan segmentowy 1–4 jak `leaf_litter`, bez tintu, rdzawy, `mossy`;
    - orlica: 2 bloki (zielona, brązowa od Mid Autumn);
  - palety `ground_layer` i `understory` obu lasów z pokryciami z §6 (zawilec w łanach dziesiątek bloków, przylaszczka w kępach, borówka płatami, mszysty spód według PW-7); orlica w BMśw (wskaźnik) i rzadko w Bśw (≤ 2%); w lukach `StandMap` gęstszy podrost; runo luk (malina, jeżyna, wierzbówka) i zależność runa od światła pod koroną kolumny to M4;
  - lang EN i PL, tagi (mączka kostna `can_spawn_from_bone_meal`, kompost, `sereneseasons:summer_crops` dla borówki), tekstury z toru B.
- **Ukończony, gdy:** karta norm N8 i N9 w tolerancji (latem i wiosną); transekty §4.6 i `EcotoneSharpnessTest` bez regresji; `GroundLayerPlanTest` rozszerzony; FEATURES w budżecie; zrzuty z bliska i z wysokości gracza.
- **Ryzyko:** wysokie (datagen). „Kropki” jaskrawych kwiatów i zbyt jednorodne łany (kontrola na zrzutach).

### V13. Wygląd sezonowy klienta: zima koron, fazy runa, owoce (B, 2–2,5 dnia)

- **Zakres:**
  - wspólna infrastruktura z V3: `ModelLoadingPlugin` owija model liści liściastych i bloków runa, a `emitQuads` wybiera wariant z fazy roku klienta (`ClientSeasonPhase`);
  - zima koron: model gałązek w fazie bezlistnej (od połowy XI do IV/V według gatunku), marcescencja dębu, grabu i młodych drzew podrostu (brązowe liście przez zimę), igły sosny ciemniejsze zimą;
  - fazy runa (podsezony według raportu ekologii §3 i §9): przylaszczka Early Spring, zawilec Early–Late Spring ze szczytem w Mid Spring i zanikiem od Early Summer, borówka kwitnie w Late Spring i przebarwia się jesienią; obrys celownika i kształt „zniknętej” rośliny według fazy (R12);
  - owoce borówki: losowy tick w Mid i Late Summer plus nadrabianie po wczytaniu chunka, odroczone do następnego ticku (nie w `CHUNK_LOAD`), ze stemplem podsezonu w załączniku chunka;
  - zrzuty w 8 podsezonach z SS i w 2 bez SS.
- **Ukończony, gdy:** zrzuty grądu w Early, Mid i Late Spring oraz Early Summer pokazują pojawianie się i zanik aspektu wiosennego pod bezlistnym okapem; borówka owocuje tylko w sezonie; nadrabianie po 2 podsezonach nieobecności daje poprawny stan w ≤ 1 s gry; tryb Fast, MoreCulling i Sodium bez artefaktów; czas klatki przy zmianie kroku w logu; `-PnoSeasons` przechodzi.
- **Ryzyko:** wysokie (R11: FRAPI na 26.3, modele innych modów, np. Continuity). Zapas: PW-8 (b) i zima jako opcja.

### Punkt kontrolny CP-C: pory roku i dno lasu (ok. 19.–23. dzień)

- **Użytkownik dostaje:** stronę z tymi samymi kadrami w podsezonach (suwak), z SS i bez; zrzuty martwego drewna w lesie gospodarczym i naturalnym; tabelę karty norm (N1–N10, N12, N15) wobec M2 i wobec celów.
- **Pytania:** ilość martwego drewna (PW-5), gęstość runa i mchów, aspekt wiosenny, przebarwienie i zima.

### V14. Szkielet fauny i sarna (B, 3–3,5 dnia)

- **Zakres:**
  - `entity/base/PolishAnimal` (Animal + GeoEntity + SmartBrainOwner), `AnimalProfile` (rekord: wymiary, FID, grupy, harmonogram, sezon futra, wagi), `ModEntities`, `ForestAlarm`, wariant przez `DataTicket`;
  - despawn: `removeWhenFarAway(distSqr)` zwraca prawdę tylko dla odległości > 128 bloków (bez imienia, smyczy i `persistenceRequired`), żeby losowy despawn wanilii powyżej 32 bloków nie przerzedzał saren;
  - predykat spawnu: tag `polishforests:forest_animals_spawnable_on` (`grass_block`, `podzol`, `coarse_dirt`, `rooted_dirt`, `moss_block`), próg światła z pomiaru V10 (startowo ≥ 5), `MOTION_BLOCKING_NO_LEAVES`;
  - spawn przy generacji w `spawnOriginalMobs` przed usunięciem `ChunkHabitats` (z siedliska kolumny, nie z biomu); spawny naturalne w datagenie biomów; PW-15 (bez wilka i królika, dwie kalibracje gęstości);
  - sarna: harmonogram dobowy SBL (żer rano, w południe i o zmierzchu, odpoczynek w gęstwinie), ucieczka z 20–40 bloków, szczek „bo-bo” i białe lustro, grupy 1–3 latem i 2–6 zimą, futro i parostki według podsezonu (wytarte V–X, zrzut X–XI, scypuł XII–IV);
  - gametest `-Pgametest=fauna`: spawn w obu lasach, skalach i trybach, 0 zwierząt na koronach, ucieczka, despawn, MSPT przy 40 zwierzętach, tablica zrzutów (dzień, noc, lato, zima).
- **Ukończony, gdy:** sarny pojawiają się także w borze na `podzol` i pod okapem; po 5 minutach w lesie wycinka średnio ≥ 1 sarna w promieniu 64 bloków, a liczba nie rośnie bez końca przy eksploracji; MSPT ≤ 0,05 ms na zwierzę; determinizm bez zmian (haszuje tylko bloki).
- **Ryzyko:** wysokie (R14, R15, R16; rejestracja encji i spawny według B1).

### V15. Dzik (B, 1,5–2 dnia)

- **Zakres:** model z szablonu czworonoga (odyniec, locha, warchlak pasiasty); aktywność zmierzchowo-nocna, w dzień barłóg w gęstwinie; wataha 3–8 za najstarszą lochą (`FollowEntity` z pamięci, `HerdLeader`), ucieczka gęsiego, chrapnięcie; buchtowisko (C9: `grass_block` → `coarse_dirt` i `rooted_dirt` płatami 2–4 bloków w grądzie, reguła gry), babrzysko przy kałużach mikroreliefu; agresja według C6 (tylko ostrzeżenie).
- **Ukończony, gdy:** `fauna` obejmuje dzika; zrzuty pokazują watahę nocą i buchtowisko za dnia; buchtowanie nie działa w generacji; reguła gry działa.
- **Ryzyko:** średnie. Ścieżki watahy w gęstym podszycie.

### V16. Pejzaż dźwiękowy i żaba trawna (A, 1–1,5 dnia)

- **Zakres:**
  - `ForestAmbience`: serwerowe zdarzenie co 10–40 s z losowego wysokiego pnia w promieniu 24–64 bloków, według biomu, podsezonu i pory dnia (PW-16); skrzek sójki, gdy gracz biegnie przez las, wysyła `ForestAlarm` do saren w promieniu 32 bloków;
  - dźwięki CC0 i CC BY (Freesound, xeno-canto tylko bez NC), mono OGG, `sounds.json`, napisy w lang, `CREDITS.md`; do czasu znalezienia nagrań dźwięki zastępcze wanilii;
  - żaba trawna `polishforests:common_frog` w `frog_variant` (tekstura 48 × 48 na układzie UV wanilijnej żaby, warunek po tagach lasów i stref wilgotnych), spawn żab w grądzie przy kałużach.
- **Ukończony, gdy:** gametest liczy zdarzenia dźwięku według pory dnia i podsezonu; każdy plik ma licencję i źródło; żaba trawna pojawia się w grądach i olsach, a wanilijne odmiany nie.
- **Ryzyko:** niskie (R18: nagrania bez NC).

### V17. Integracja, pełne testy, dokumentacja (A, 1,5–2 dnia)

- **Zakres:** pełny `tools/dev/run-tests test`, `checkDatagen`, `checkArt`, `runClientGameTest` (`slice`, `fauna`, `determinism` z C2ME i bez, `all` w częściach jak w S10); końcowy A/B `stages`; karta norm końcowa; zrzuty w 8 podsezonach; dokumentacja: `docs/04` (stan), BRIEF, `docs/00`, `docs/01` §5, §6, §8, README (nowe bloki i zwierzęta), `LICENSE-assets`; lista „co uogólnić w M3, M4 i M6”.
- **Ukończony, gdy:** wszystko przechodzi, budżety spełnione albo zgłoszone (B3), drzewo robocze czyste i wypchnięte, strona CP-D gotowa.
- **Ryzyko:** średnie. Konflikty scalania datagenu (R20), czas `all` > 7000 s.

### Punkt kontrolny CP-D: spacer (ok. 22.–28. dzień)

- **Użytkownik dostaje:** stronę z galerią pór roku, kartą norm, liczbami wydajności, ziarnem i miejscami oraz listą do sprawdzenia w grze: świt w grądzie (sarny, zięba), noc w borze (dzik, puszczyk), wiosenny łan zawilca, zima pod bezlistnym okapem, borówki latem, martwe drewno w lesie naturalnym, granica grąd → bór, ścięcie i posadzenie drzewa, obie skale i tryb D.
- **Pytania:** czy las „żyje”; co poprawić najpierw przed M3; gęstość fauny (PW-15); które opcje O1–O4 wchodzą.
- **Po akceptacji:** wycinek jest zamknięty. Plan M3 przejmuje mechanizmy: pnie, planer, `StandMap`, liście i krzywe, sadzonki, martwe drewno, rodzinę bloków runa, `tools/art`, `tools/fauna`, `PolishAnimal`.

---

## 4. Punkty kontrolne (podsumowanie)

| CP | Kiedy [S] | Co użytkownik ogląda | Co rozstrzyga |
|---|---|---|---|
| CP-0 | przed V0 | ten plan | §0 (do CP-0: D1, D2, D5, PW-1, PW-2), stan M2 (§0.4) |
| CP-A | ok. 4. dzień | 10 tekstur, tablica pni z Sodium i bez, próby kolorów i zimy | styl (D5), licencja, B6, technika pnia |
| (podgląd) | ok. 9. dzień | PNG drzew iglastych, bez czekania na odpowiedź | uwagi do pokroju przed liściastymi |
| CP-B | ok. 13.–17. dzień | las drzew w grze, karta norm, wydajność, model sarny | pokrój i gęstość, PW-10, PW-12; zamrożenie zapisu |
| CP-C | ok. 19.–23. dzień | pory roku, runo, martwe drewno, karta norm | PW-5, gęstość runa i mchów, fenologia |
| CP-D | ok. 22.–28. dzień | spacer z fauną i dźwiękiem | odbiór wycinka, PW-15, opcje O1–O4, start M3 |

Zrzuty trafiają na stronę Artifact (jak punkt kontrolny 2 M2) i do `docs/wycinek/cp-x/`. Każdy zrzut ma współrzędne kamery. Recenzenci oglądają pełne zestawy (B4 odrzucona). Poprawki po punkcie: najwyżej 2 rundy, potem warianty (B3).

---

## 5. Testy

| Rodzaj | Testy |
|---|---|
| JUnit (czysta Java) | `TreeShapePlanTest` (determinizm, łańcuch `parent`, promień nierosnący, liście ≤ 6, okno 3 × 3, brak sporów koron); `CrownTerritoryTest` (niezależność od chunka i kolejności zapytań); `StandMapTest` (udziały klas wieku i naturalności w 10⁴ wydzieleń ±3 p.p., luki, wielkość wydzieleń w obu skalach); `DeadwoodPlanTest`; `GroundLayerPlanTest` rozszerzony; `ForestNormsTest`; `RegistrySnapshotTest`; kształty `TrunkBlock`; predykat spawnu, harmonogramy, wybór lidera; koszt planera w `@Tag("cost")` |
| Podgląd PNG | `treePreview`: gatunek × klasa wieku z boku i z góry, drzewostan 3 × 3 chunki, mapa `StandMap` dla kadrów |
| W grze | `slice` (miejsca wycinka, karta norm, zrzuty ze stałych kamer `slice_cameras.txt`, liczniki, tablica próbek przez `-Psites=samples`, sezony przez `-PnoSeasons` i ustawianie pory); `fauna`; `determinism` (hasz według ZW-5, z C2ME i bez); `stages` A/B i przelot FPS w `performance`; regresje `vegetation`, `ecotones`, `present_day`, `habitats` |
| Zasoby i dane | `checkDatagen`, `checkArt` (piksele RGBA i mcmeta), lint stylu |
| Bez zmian | złoty test terenu (wycinek nie zmienia terenu ani `ColumnSample`), klasyfikator, kopia M1 |

---

## 6. Karta norm (miara realizmu)

Mierzona w `slice` (V4) na powierzchniach 1 ha (100 × 100 bloków ≈ 39 chunków) w obu skalach i trybach. Pierwsza kolumna to pomiar bazowy M2. Tolerancja ±20%, a dla martwego drewna i luk ±30%, chyba że wiersz mówi inaczej. Odstępstwa akceptuje użytkownik przy CP-B lub CP-C.

| # | Miara | Grąd naturalny | Grąd gospodarczy | Bór świeży gospodarczy | Bór naturalny | Źródło |
|---|---|---|---|---|---|---|
| N1 | pnie na chunk (wszystkie piętra) | ok. 15 (588 ± 53 drzew/ha ≥ 8 cm) | 9–14 według klasy wieku | drągowina ok. 15–16 (górna granica wzoru), dojrzały 10–12, starodrzew 8–10 | kępy różnowiekowe, 8–14 | [Ź Miścicki 2016; L 06 §6] |
| N2 | piętra | a1 3–5 / chunk, 28–40 bl.; a2 6–9 / chunk, 15–22 bl. | a1 22–30 bl., a2 słabe | a1 20–28 bl.; a2 0–20% | a1 i kępy młodsze | [L 06 §3.5; Ź] |
| N3 | skład a1 (udział pni) | Db 20–50, Lp 10–40, Kl/Js 5–15, Św 0–30 (NE), Bk 0–30 (W) | Db 50–80 i domieszki | So 80–95, Brz 5–15 | jak gospodarczy, Brz w lukach | [L 06 §3.5, §6.2] |
| N4 | zwarcie (kolumny z liśćmi nad gruntem) | 80–95% | 75–90% | 60–80% | 50–75% | [L 06 §4.1; Ź Janów] |
| N5 | luki | 18–45 bl., 3–8% powierzchni [S] | brak | brak | luki z Brz i So | [Ź BPN] |
| N6 | odnowienie | w lukach 4 × gęstsze niż pod okapem; dąb bez podrostu | — | sosna tylko w lukach | sosna w lukach | [Ź BPN; Paluch] |
| N7 | podszyt (pokrycie) | 10–40% | 10–30% | 5–30% | 10–30% | [L 06 §4.1] |
| N8 | runo (pokrycie kolumn) | wiosną 60–100%, latem 20–60% | wiosną 50–90% | 40–80%, borówka 40–70% kolumn | jak gospodarczy | [Ź Popiołek; L] |
| N9 | mchy (z mszystym spodem, PW-7) | 5–20% | 5–15% | 60–95% | 60–90% | [Ź Popiołek] |
| N10 | martwe drewno (obiekty na chunk) | ok. 2 (PW-5 b) albo 0,5 (a) | ok. 0,25 | ok. 0,1 kłody i 1–2 pniaki | ok. 1 (b) albo 0,4 (a) | [Ź GIOŚ; Mróz i Pawlaczyk; Piętka] |
| N12 | geofity grądu | widoczne Early–Late Spring, szczyt Mid Spring, 0 od Early Summer | jw. | — | — | [Ź Rawlik 2021] |
| N15 | transekt Bśw → BMśw → LMśw → grąd | kolejność wskaźników zachowana, przejście 20–100 m (REAL) przy ostrej granicy podłoża, bez prostej linii | | | | [Ź Stefańska; S] |

Odstępstwa celowe, jawnie w karcie: gęstość fauny ×20–50 (PW-15), żaba w rozmiarze wanilii, brak pierśnicy jako miary (dokładność ±15%), N11, N13, N14, N16 i N17 z planu „wierność” poza kartą (forma śmierci i okap sprawdzane na zrzutach, gleby i mrowiska poza wycinkiem, fauna w gametest `fauna`).

---

## 7. Harmonogram, równoległość i plan skrócenia

| Dzień [S] | Tor A (świat) | Tor B (grafika, klient, fauna) | Punkt |
|---|---|---|---|
| 1–3 | V0, V2, V5 | V1, V3 | |
| 4 | V2 (zrzuty) | V1 (poprawki) | **CP-A** |
| 4–11 | V6 | V4, V7, V8, tekstury kory, liści i runa | podgląd PNG ok. 9. dnia |
| 11–15 | V9 | V8, tekstury martwego drewna | |
| 13–17 | V10 | | **CP-B** |
| 15–21 | V11, V12 | V13, start V14 | **CP-C** |
| 19–26 | V16, V17 | V14, V15 | |
| 22–28 | | | **CP-D** |

S8c (M2-20 d2) kończy się w trzecim worktree przed CP-B (PW-17). Kalendarz jest dłuższy od samej ścieżki krytycznej, bo gametesty w kliencie i pomiary zajmują jedną maszynę (ZW-11).

**Plan skrócenia** (gdy kroki przekraczają górne szacunki o ponad 30% albo blokada przetrwa 2 rundy), w tej kolejności:
1. opcje O1–O4 nie wchodzą;
2. biblioteka gotowych szkieletów zamiast planera na każde drzewo (V6);
3. zima koron i fazy runa po stronie klienta jako opcja; runo przez losowy tick z nadrabianiem (PW-8 b);
4. krzywe gatunków zastąpione mnożnikiem (B6 b);
5. `StandMap` z jednym stanem pośrednim (PW-4 a).

---

## 8. Ryzyka

| # | Ryzyko | Skutek | Zabezpieczenie | Krok |
|---|---|---|---|---|
| R1 | LIGHT i FEATURES przy koronach 1:1 (grąd 1000–1500 liści na chunk wobec 600–800 w lesie wanilii) | budżety przekroczone, wolne wczytywanie | pomiar V10 przed runem i fauną, puste środki koron, PW-13, rzadsze a2, nowe budżety (PW-10) | V10 |
| R2 | FPS przez quady liści w trybie Fancy (6–9 tys. na kolumnę chunka) | spadek klatek w grądzie | liście jako `LeavesBlock` (MoreCulling, tryb Fast), przelot FPS, gęstość skupień | V10 |
| R3 | wysięg modelu pnia poza blok przy Sodium i AO | szczeliny, migotanie | tablica próbek w V2 jako pierwszy test, zapas: wariant C | V2 |
| R4 | schodki ukośnych grubych konarów | „kanciaste” dęby | konary blisko osi, ukośność tylko dla r ≤ 2, ocena na PNG | V6 |
| R5 | przycinanie koron i determinizm: sąsiedzi z chunków w promieniu 2 nie mają `ChunkHabitats` na etapie FEATURES | różnice zależne od kolejności, spory koron | próba V5, PW-9 (a), test determinizmu w każdym kroku drzew | V5–V11 |
| R6 | okno zapisu 3 × 3 chunki | ucięte korony i długie kłody | promień korony ≤ 16, kłody do wnętrza okna | V6, V11 |
| R7 | korony nad dachami struktur | liście w wioskach | `BUILDING_GAP` z promienia korony, maska struktur, test wiosek z S8 | V9 |
| R8 | dekoratory wanilii (huby, mech) na cienkich pniach | „wiszą” obok kory | brak dekoratorów wanilii na cienkich pniach, własne huby w M4 | V6 |
| R9 | przebudowa siatek przy zmianie kroku koloru lub fazy | zacięcie klatki | 2–3 kroki na podsezon, pomiar czasu klatki, zapas: przebudowa falą od kamery | V7, V13 |
| R10 | własny `ColorResolver` przy Sodium z paczki | brak koloru gatunku albo błędy | próba V3 przed CP-A, zapas B6 (b) | V3 |
| R11 | FRAPI na 26.3 (`ModelLoadingPlugin`, MoreCulling, Continuity, tryb Fast) | brak zimy koron i faz runa | próba V3, sygnatury spisane, zapas PW-8 (b) i zima jako opcja | V3, V13 |
| R12 | obrys celownika i łup „niewidocznej” rośliny w fazie zaniku | gracz celuje w pustkę | kształt według fazy roku (oba końce znają porę), brak kolizji roślin | V13 |
| R13 | nadrabianie owoców przy wczytaniu chunka | zaległe stany albo zmiany w `CHUNK_LOAD` | kolejka na następny tick, stempel podsezonu, test po 2 podsezonach | V13 |
| R14 | wanilijny predykat spawnu (`grass_block`, światło > 8) | 0 saren w borze na `podzol`, mało pod okapem | własny tag i predykat, próg z pomiaru V10 | V14 |
| R15 | despawn wanilii (losowy powyżej 32 bloków, spawn `CREATURE` co 400 ticków) | rzadka, „migająca” populacja | `removeWhenFarAway` tylko powyżej 128 bloków, kalibracja na zrzutach | V14 |
| R16 | spawn przy generacji poza limitem `CREATURE` | „zoo” przy eksploracji 1:1 | dwie osobne kalibracje (PW-15), startowo 0,02–0,04 | V14 |
| R17 | konwencja osi i obrotów w GeckoLib 5 | „połamane” modele | kalibracja osi w grze i test regresji w V8 | V8 |
| R18 | nagrania polskich ptaków tylko z licencją NC | brak tła dźwiękowego | manifest licencji, dźwięki zastępcze wanilii, mniej gatunków | V16 |
| R19 | subiektywna estetyka agentów | odrzucenie stylu po pracy | CP-A po 10 teksturach, model sarny przy CP-B przed AI, `overrides/`, D5 (b) | V1, V8 |
| R20 | konflikty scalania torów (lang, tagi, `ModBlocks`, datagen biomów, `src/main/generated`) | `checkDatagen` FAIL, utracone zmiany | ZW-11: kolejność scalania, `runDatagen` po scaleniu, bez ręcznego scalania wygenerowanych plików | wszystkie |
| R21 | jedna maszyna dla gametestów w kliencie i pomiarów | kalendarz dłuższy, fałszywe pomiary | kolejka gametestów, pomiary A/B w jednej sesji na spokojnej maszynie, `-PtimingsReportOnly` | V10, V17 |
| R22 | szacunki dla nowych dziedzin (bloki, render, encje, grafika) | wycinek dłuższy | punkty kontrolne co 4–6 dni, plan skrócenia (§7), B3 po 2 rundach | wszystkie |
| R23 | rozrost zakresu (M3 i M4 „przy okazji”) | wycinek się nie kończy | §1.2 jawnie, opcje tylko po CP-D | wszystkie |
| R24 | `checkArt` zależny od Pythona | build nie działa na innej maszynie | zadanie poza `build`, czytelny błąd, opis w BRIEF | V1 |
| R25 | skok skali na styku pasa W1b z biomami spoza niego (olsy, łęgi, jedliny na zastępstwach 5–18 bloków) | widoczna „ściana” wysokich drzew | ekoton miesza oba drzewostany w pasie Z10, opis w dokumentacji, ocena przy CP-B, M3 usuwa | V9 |
| R26 | zapisane światy po zmianie bloków | utrata bloków albo złe stany | zamrożenie po CP-B, test migawki rejestru | V2, CP-B |
| R27 | gęstsze drzewostany niż wzór kandydatów (ok. 16,65 na chunk) | drągowiny rzadsze niż w naturze | drągowiny przycięte do ok. 16 na chunk, drugi wzór z nową solą w M3 | V9 |
| R28 | zmiany API GeckoLib, SBL, SS i Fabric API w łatkach 26.3 i 26.4 | rekompilacja | wersje przypięte, aktualizacja świadoma | wszystkie |

---

## 9. Pochodzenie elementów planu

| Element | Skąd |
|---|---|
| kolejność, CP-A i CP-B wcześnie, listy „co sprawdzić w grze”, 4 bloki runa, `ForestAmbience`, żaba `frog_variant`, sadzonki z planera, pomiar potworów pod okapem, próg FPS 0,8 × M2, jawna lista „poza wycinkiem” i opcji | „najpierw gra” |
| klucze `tree/` i `tree_substitute/`, pas W1b, migawka rejestru i zamrożenie, krzywe gatunków na `RAW_FOLIAGE`, faza drzewa `phase`, `StandMap` lite, pomiar przed treścią, rodziny bloków runa, LITE sosny jako potok, asymetryczne korony skraju, spawn z siedliska kolumny, model sarny przed AI | „fundamenty techniczne” |
| karta norm i pomiar bazowy, zima koron z marcescencją w rdzeniu, fazy runa po stronie klienta ze stanem serwera tylko dla owoców, forma śmierci według gatunku, pniaki tylko w gospodarczym, sosna tylko w lukach, przylaszczka, pejzaż ptaków, jawne odstępstwa w karcie | „wierność przyrodnicza” |
| próba przycinania koron przed planerem, generacja bez pory roku, `checkArt` na pikselach, despawn > 128 bloków, osobna kalibracja spawnu przy generacji, mszysty spód pod krzewinkami, orlica w BMśw, ograniczona równoległość, realne szacunki, D9 85/15 w obu trybach | oceny sędziów |
| odrzucone: globalna podmiana `tree/`, jeden stan lasu, mnożnik SS jako rozwiązanie główne, `PhenologySweep`, pełny zestaw sosny i wiewiórka w rdzeniu, linie oddziałowe, profile gleb, grzyby dynamiczne, 6 stadiów × 9 gatunków, 13 geofitów, przesunięcie fenologii z pola O po stronie klienta, 8 punktów kontrolnych | oceny sędziów |

---

## Stan po V0 (2026-10-15)

**Zrobione.**
- **Decyzje:** plan zaakceptowany przy CP-0 (status na górze dokumentu). D1 (b), D2 (b), D5 (a) z zasobami na CC BY 4.0, PW-1 (a) i PW-2 (a) zapisane w `docs/00` G.1 jako podjęte przez użytkownika 2026-10-15; B6, PW-3 … PW-17 (G.2) i decyzje z §0.3 (G.3) jako „domyślne, do potwierdzenia”, z punktem kontrolnym, przy którym najpóźniej trzeba je potwierdzić. Wpisy B6, D1, D2, D5, D6 (licencja zasobów), M2-24, B5 i C2 … C11 w `docs/00` wskazują na sekcję G.
- **Stan M2 (§0.4):** M2 zamknięty: punkt kontrolny 2 zaakceptowany 2026-10-14 wprost, punkt kontrolny 1 razem z nim (`docs/00` E „Stan M2”, BRIEF, `docs/01` §8). S8c z rundą 1 poprawek był już zakończony (`9f627cd`), więc PW-17 nie wymaga osobnego worktree.
- **SmartBrainLib 2.0.3:** wersja dla Fabric 26.3 istnieje w repozytorium Maven Modrinth (id wersji `cwerutUM`, opublikowana 2026-10-04, zależność wymagana: GeckoLib). Zmienione `gradle.properties`, `fabric.mod.json` (`>=2.0.3`), `tools/build_mrpack.py`, README i `docs/01` (tabela zależności). `build` i `fastTest` przechodzą, gra startuje z SBL 2.0.3 (lista modów w logu gametestu `slice`). SBL nie jest jeszcze używany w kodzie (fauna od V14).
- **Stałe miejsca wycinka** (`src/test/resources/slice_sites.txt`, ziarno 20260927, wspólne dla trybów N i D):

  | Skala | Miejsce | Biom | Środek albo początek → koniec |
  |---|---|---|---|
  | REAL | `oak_hornbeam_1` | grąd | (438, −730) |
  | REAL | `oak_hornbeam_2` | grąd | (−1596, −571) |
  | REAL | `fresh_pine_1` | bór świeży | (−699, 1924) |
  | REAL | `fresh_pine_2` | bór świeży | (−2837, −1948) |
  | REAL | `transect` | Bśw → BMśw → LMśw → grąd | (−2628, −1565) → (−2504, −625), 950 bloków: 93 / 330 / 366 / 161 bloków |
  | GAMEPLAY | `oak_hornbeam_1` | grąd | (−1785, 886) |
  | GAMEPLAY | `oak_hornbeam_2` | grąd | (870, 1961) |
  | GAMEPLAY | `fresh_pine_1` | bór świeży | (−1195, −583) |
  | GAMEPLAY | `fresh_pine_2` | bór świeży | (−398, 999) |
  | GAMEPLAY | `transect` | Bśw → BMśw → LMśw → grąd | (−1590, −65) → (−1590, 175), 241 bloków: 57 / 57 / 63 / 56 bloków |

  Miejsca znalazł `SliceSitesTest.findSites` (`./gradlew test --tests '*SliceSitesTest*' -PfindSliceSites`): `HabitatTargets.locate` szuka najbliższego miejsca biomu w trybie D z pierścienia punktów startowych, a wokół trafienia szukany jest najbliższy środek kwadratu 100 × 100 bloków (powierzchnia 1 ha karty norm, V4), w którym biom ma ≥ 95% punktów siatki co 5 bloków w obu trybach (w trybie D kwadrat jest więc lasem), a jego zespół typowy (`Association.TYPICAL`) ≥ 90% (od rundy 1 poprawek, niżej). Miejsca leżą ≥ 640 bloków od (0, 0) (poza chunkami startu świata, więc obszary pomiaru etapów są nowym terenem), ≥ 320 bloków od siebie, a dwa miejsca jednego biomu ≥ 1500 bloków (REAL) i 400 (GAMEPLAY) od siebie (dwa różne drzewostany). Transekt: mapa biomów w oknie wokół trafień `HabitatTargets.locate` dla LMśw, linie z komórek Bśw w 48 kierunkach, sprawdzenie blok po bloku w obu trybach (kolejność Bśw → BMśw → LMśw → grąd bez powrotów, każdy las ≥ 40 bloków w REAL i ≥ 16 w GAMEPLAY, inne biomy ≤ 15% i najwyżej 24 / 12 bloków naraz, lasy w zespole typowym na ≥ 90% swoich kolumn), wydłużenie końców w głąb Bśw i grądu (do 120 / 40 bloków), wybór transektu o najdłuższym najkrótszym lesie. Zwykły przebieg `SliceSitesTest` (w `fastTest`, kilka sekund) tylko sprawdza tabelę. Pełny transekt mieści się blisko startu w obu skalach, więc zapas z §3 V0 (dwa krótsze transekty) nie był potrzebny.
- **Gametest `slice`** (`SliceClientGameTest`, `-Pgametest=slice`, tylko z nazwy, nie w `all`; `-Pscales`, `-Pmodes=natural|present_day`, `-Psites=<miejsca>|none`): cztery światy (REAL i GAMEPLAY, N i D), w każdym dla każdego miejsca sprawdzenie klasyfikatora świata (kwadrat 100 × 100, transekt blok po bloku według tych samych reguł) i `getBiome` w środku wygenerowanego chunka, potem zrzuty ze **stałych kamer** z `src/test/resources/slice_cameras.txt` (od rundy 1 poprawek; opis kamer niżej). Na świat log `[slice]` z porą roku, licznikami warstw (`ModFeatures.STATS`: chunki, ms i obiekty na chunk) i `HABITAT_MISS` (musi być 0). Wspólny odczyt tabeli: `SliceSites` (ścieżkę podaje build jako `-Dpolishforests.slice.sites`).
- **Etapy w miejscach wycinka:** `-Pgametest=stages -Psites=slice` mierzy w `PerformanceClientGameTest` obszary 8 × 8 chunków o środku w każdym miejscu (środek transektu) w czterech światach zamiast obszarów M2 i „area”.
- **Baza pomiarowa M2:** tag git `m2-baseline-r1` na końcowym commicie rundy 1 poprawek V0 (zastępuje `m2-baseline` z pierwszego oddania V0, który zostaje w repozytorium, ale ma stare miejsca grądu i transektu GAMEPLAY i ruchome kamery; przesunięcie opublikowanego tagu wymagałoby force-push). Generacja świata jest w obu tagach taka jak na `main` po S8c i rundzie 1 jego poprawek (`9f627cd`; V0 nie zmienia `src/main` poza wersją SBL i pustymi pakietami), a harness (gametest `slice` ze stałymi kamerami, `stages -Psites=slice`, tabele miejsc i kamer) jest już w `m2-baseline-r1`, więc V10 może zbudować bazę z tagu w osobnym worktree i mierzyć A/B w jednej sesji. Zrzuty bazowe: `docs/wycinek/baza/` (72 pliki `slice_<skala>_<tryb>_<miejsce>_<kamera>.png`, zapisane bez kanału alfa z optymalizacją PNG, ok. 42 MB; zawartość niżej). Pomiar etapów: `docs/wycinek/baza/stages_m2.csv`.
- **Pakiety:** `pl.polishforests.block`, `pl.polishforests.worldgen.tree`, `pl.polishforests.entity` (same `package-info` z opisem przyszłej zawartości).

**Odstępstwa.**
- Pakiet drzew to `worldgen/tree/`, nie `tree/` na najwyższym poziomie: tak przewiduje `docs/01-architektura.md` §9 (model i generacja świata w `worldgen/`), a plan pisze ścieżki skrótowo (np. `habitat/ForestCover` to też `worldgen/habitat/`). Dotyczy to tylko pakietu Java: `tree/` z §3 (np. `tree/TreeShapePlan`, `tree/DeadwoodPlan`) to `pl.polishforests.worldgen.tree`. Klucze cech świata `tree/<gatunek>` i `tree_substitute/<gatunek>` (PW-3, ZW-3, §1.1) to identyfikatory rejestru, nie ścieżki pakietu, i zostają takie jak w PW-3 (poprawka po przeglądzie V0).
- Miejsca są wspólne dla trybów N i D (te same współrzędne, w trybie D też las), a nie osobne dla każdego trybu: zrzuty obu trybów pokazują to samo miejsce i da się je porównać. Udział biomu w kwadracie w trybie D wynosi 95,2–100% (REAL) i 97,1–100% (GAMEPLAY).
- Tablica próbek pni (`-Psites=samples`) to część V2, nie V0.
- `slice` nie wchodzi do `all` (jak `ecotones`), bo `all` już przekracza 7000 s; pełny przebieg `slice` trwa ok. 54 min (ok. 40 s oczekiwania na wczytanie przed pierwszym zrzutem w każdym nowym miejscu).
- Transekt GAMEPLAY ma 241 blok, a odcinki lasów 56–63 bloki. Szerokości przejść (N15) zmierzy karta norm (V4).

**Pomiary (baza M2, 2026-10-15, spokojna maszyna: obciążenie CPU ok. 2% przed pomiarem, poza grą nic ciężkiego).**
- `stages -Psites=slice` (powtórzone w rundzie 1 poprawek dla nowych miejsc), 4 przebiegi w jednej sesji, mediany na obszar 8 × 8 chunków (pełne liczby z zakresami w `stages_m2.csv`): cały chunk 15,5–20,5 ms (budżet ≤ 48 ms; 20,3–20,5 ms tylko w nowym `oak_hornbeam_2` GAMEPLAY przy pogórzu, gdzie TERRAIN ma 5,0–5,1 ms), TERRAIN 2,8–5,1 ms, FEATURES 4,3–5,8 ms, LIGHT 6,3–7,4 ms na chunk z 64; dekoracja 2,3–2,8 ms na dekorowany chunk, w tym drzewostan 0,33–0,60 ms; drzewa 7,9–11,5 na chunk (grąd 7,9–9,2, bór świeży 9,3–11,5); `HABITAT_MISS` 0, żaden obszar nie był wygenerowany wcześniej. Przebiegów było 5; drugi pominięty, bo wszystkie jego obszary były o ok. 3 ms/chunk wolniejsze (zakłócony przebieg). Rozrzut przebiegów (min–max) całego chunka: do ok. 2 ms (najwięcej 2,8 ms w `fresh_pine_1` REAL D), w 8 z 20 obszarów ≤ 1 ms.
- Liczniki warstw w gametest `slice` (teren wygenerowany podczas odwiedzin miejsc, bez startu świata; obiekty na chunk): drzewostan REAL N 9,7, REAL D 8,2, GAMEPLAY N 9,2, GAMEPLAY D 6,3; runo 170–185; podszyt 1,2–1,6; martwe drewno 0,2–0,3; `HABITAT_MISS` 0 we wszystkich czterech światach.

**Testy.** `SliceSitesTest` (tabela: każde miejsce w obu trybach, komplet miejsc w każdej skali i trybie, odległości), `fastTest`, `build`, pełny zestaw `tools/dev/run-tests test` (PASS), `runClientGameTest -Pgametest=slice` w obu skalach i trybach (PASS; transekty powtórzone po zmianie wyszukiwania z `-Psites=transect`, PASS), `-Pgametest=stages -Psites=slice` 4 × (PASS). Złoty test bez zmian (wycinek nie zmienia terenu).

### Runda 1 poprawek po przeglądzie V0 (2026-10-15)

Wszystkie uwagi przeglądu potwierdzone (żadna nie była fałszywym alarmem); poprawione albo zapisane jako kryteria dla dalszych kroków.

**Miejsca: grąd typowy zamiast zboczowego.** Potwierdzone: w GAMEPLAY `oak_hornbeam_2` (−1509, 181) miał 100% (D: 98,9%) zespołu `SLOPE` (grąd zboczowy), `oak_hornbeam_1` (−1279, 530) 75,5%, w REAL `oak_hornbeam_1` (534, −714) 28,1%; także odcinek grądu transektu GAMEPLAY był w ponad połowie zboczowy (16% linii zboczowe wobec 12% typowych). `SliceSitesTest` wymaga teraz zespołu typowego (`Association.TYPICAL`) na ≥ 90% kwadratu w obu trybach (`MIN_TYPICAL`), a w transekcie na ≥ 90% kolumn czterech lasów (wydłużanie końców też tylko w typowym biomie). Nowe miejsca: REAL `oak_hornbeam_1` (438, −730) 98,4% typowego (D: 93,9% i 4,5% pól), GAMEPLAY `oak_hornbeam_1` (−1785, 886) 90,0% (10% zboczowego), `oak_hornbeam_2` (870, 1961) 98,0% (D: 97,7%), transekt GAMEPLAY (−1590, −65) → (−1590, 175), 241 bloków, wszystkie lasy w 100% typowe (3,3% linii to bór suchy jako inny biom). Bory świeże i transekt REAL bez zmian (zespół typowy 95,2–100%). Udziały biomów i zespołów każdego miejsca drukuje `./gradlew test --tests '*SliceSitesTest*' -PsliceReport`.

**Wyszukiwanie deterministyczne.** Linie transektu są sortowane po długości i potem po komórkach, więc równoległe zbieranie nie zmienia wyniku; dwa przebiegi `-PfindSliceSites` zapisały identyczny plik, a transekt REAL wyszedł ten sam co w pierwszym oddaniu V0. Plik ma końce linii LF na każdym systemie.

**Stałe kamery** (`src/test/resources/slice_cameras.txt`, 55 kamer, czytane przez `SliceCameras`). `./gradlew runClientGameTest -Pgametest=slice -PfindSliceCameras` wybiera kamery w pierwszym świecie każdej skali (tryb N) i zapisuje plik; każdy inny przebieg bierze je z pliku (bez pliku albo bez kamer miejsca test kończy się błędem). Jeśli zapisana kamera stoi w pniu, liściach albo bloku pełnym (nowe drzewa w V2+), przebieg loguje to i usuwa te bloki (stopy i głowa; przy widokach z góry 3 × 3 × 3 wokół głowy), a przy kamerach z wysokości oczu loguje też, czy grunt się przesunął. Kamery „z góry” liczone są od gruntu (grunt + 30 / 35 / 70), a nie od pierwszej wolnej przestrzeni nad liśćmi. `SliceSitesTest.camerasCoverEveryPlace` (w `fastTest`) pilnuje, że każde miejsce ma komplet kamer, a `eye` i `canopy` leżą ≤ 20 bloków od środka. Powtarzalność: drugi przebieg z pliku (GAMEPLAY N `oak_hornbeam_1`) dał te same kadry, różnica 0,4–2,0% pikseli (falująca trawa).
- `eye`: z kolumn ≤ 8 bloków od środka i 8 kierunków (od 30°, czyli SSW, co 45°) ta z najdłuższą wolną linią wzroku: stożek ±20° (do 3 bloków ±45°, bo pień 2 bloki obok wypełnia brzeg kadru o polu ±51°) bez pni, liści i bloków pełnych na wysokości stóp i oczu (do 4 bloków także nad głową), szukane do 16 bloków. Wyszło 9–16 bloków na miejscach i 7,5–16 na transektach (wyjątek: `edge_mixed_forest_oak_hornbeam_forest_eye` GAMEPLAY, 4 bloki, gęsty podszyt na granicy).
- `canopy`: osobna kolumna ≤ 16 bloków od środka, najbliższa, w której 5 bloków od stóp jest wolnych (najniższe liście ≥ 4 bloki nad głową), a liście są w 30 blokach nad nią i nad czterema kolumnami 2 bloki obok; pionowo w górę (−90°), yaw 90 i pora 3000 (słońce 45° nad wschodnim horyzontem, poza kadrem). Niebo (piksele niebieskie): grądy 5–23%, bory REAL 4–5% (gęste, ciemne piętra `spruce_leaves`), bory GAMEPLAY 24–34%. W `fresh_pine_1` GAMEPLAY w kadrze jest pień brzozy tuż obok kolumny.
- `30m`: 30 bloków za środkiem (przeciwnie do kierunku 30°), grunt + 30, patrzy na środek; kolumna do 3 bloków obok, gdy głowa byłaby w liściach.
- `bark_<kłoda>`: zbliżenia pnia z ok. 3 bloków na wysokości oczu, każda kłoda raz na skalę (najbliższy stojący pień ≥ 3 bloki kłody osi Y, ≤ 24 bloki od środka miejsca), tylko w trybie N (kora nie zależy od trybu): `oak_log` i `pale_oak_log` (grab) w `oak_hornbeam_1`, `birch_log` i `spruce_log` (sosna i świerk M2) w `fresh_pine_1`.
- Transekt: `eye_<biom>` w środku każdego lasu, patrząc wzdłuż ku grądowi; na każdej z trzech granic `edge_<biom>_<biom>_eye` (12 bloków przed granicą w REAL, 10 w GAMEPLAY, wzdłuż linii) i `edge_<biom>_<biom>_side` (45 bloków w bok, grunt + 35, patrzy na granicę w poprzek linii); `30m` nad początkiem; w GAMEPLAY `overview` (80 bloków w bok od środka, grunt + 70, zasięg widzenia na ten kadr 12 chunków, więc oba końce są przed mgłą). Granice Bśw|BMśw, BMśw|LMśw i LMśw|grąd mają więc bazę „przed” dla Z10 i N15.
- Rozmiar: z wysokości oczu i zbliżenia kory 1280 × 720 (kora czytelna przy CP-A i V1), pozostałe 854 × 480. Kadry „po” muszą mieć te same rozmiary (stałe w `SliceClientGameTest`).
- Jasność każdego zrzutu (średnia luma, udział pikseli ciemnych < 40, udział nieba) idzie do logu, a przy kadrze z wysokości oczu z > 60% ciemnych pikseli pada ostrzeżenie. W bazie ostrzeżenie zostaje przy 4 kadrach borów (REAL `fresh_pine_2_eye` 69%, `transect_eye_fresh_pine_forest` 61% REAL i 63% GAMEPLAY, `edge_mixed_forest_oak_hornbeam_forest_eye` REAL 61%): linia wzroku jest tam wolna (7,5–15 bloków), ciemny jest niski okap `spruce_leaves` (uwaga przeglądu o wyglądzie boru, niżej). Po LITE sosny te kadry powinny się rozjaśnić.

**Pora roku (ZW-7).** Z Serene Seasons przebieg ustawia `season set mid_summer` (jak `PolandWorldClientGameTest`), a log `[slice]` każdego świata podaje podporę roku; baza jest więc z pełni lata (SS `MID_SUMMER`; pierwsze oddanie V0 miało niezapisaną wczesną wiosnę SS). `-Pgametest=slice -Psites=none -PnoSeasons` przechodzi w obu skalach i trybach (wbudowany kalendarz, `EARLY_SPRING`, 57 s).

**Zawartość bazy** (`docs/wycinek/baza/`, 72 pliki): tryb N w całości (55 kadrów: na miejsce `eye`, `canopy`, `30m`; 4 zbliżenia kory na skalę; na transekt 4 × `eye_<biom>`, 3 × 2 kadry granic, `30m`, w GAMEPLAY `overview`) i z trybu D tylko widoki z góry (`30m`, `_side`, `overview`, 17 kadrów). Wewnątrz miejsc baza D jest równa bazie N: miejsca mają ≥ 95% lasu także w D, a tryb D w M2 zmienia tylko maskę lasu (w pierwszym oddaniu wszystkie 34 pary N/D różniły się ≤ 10% pikseli, 3 były identyczne bajt w bajt). Skraj lasu i pola trybu D widać tylko w rogach widoków z góry. Gametest robi wszystkie kadry D (w `build/run/clientGameTest/screenshots`), więc do porównań „po” są dostępne. Do V9: po `StandMap` zapisać, które miejsca D wylosowały las gospodarczy, a które naturalny (D9 85/15), i rozważyć miejsce tylko dla trybu D na skraju las–pole jako bazę Z10 w trybie D; jego kadry „przed” da się zrobić z tagu `m2-baseline-r1` (generacja świata M2).

**Poprawki opisu.** Kierunek 30° to południowy-południowy zachód (SSW), nie południowy zachód. Rozrzut przebiegów i zakres liczników warstw poprawione wyżej (liczniki obejmują teren wygenerowany podczas odwiedzin miejsc, bez startu świata, bo stan początkowy jest brany po utworzeniu świata). Zdanie o `tree/` dotyczy tylko pakietu Java; klucze cech `tree/<gatunek>` i `tree_substitute/<gatunek>` zostają jak w PW-3.

**Uwagi dla kolejnych kroków (z przeglądu zrzutów bazy; nie są błędami V0, to kryteria akceptacji).**
- Bór świeży (LITE sosny, V7, CP-A): nasada korony w REAL ≥ 10–12 bloków, igły jaśniejsze i bardziej szare niż `spruce_leaves`, korona prześwitująca (niebo widoczne w kadrze `canopy`), w kadrze `eye` widać rude lustro w górnej części pnia; dziś niskie, ciemne, niebieskozielone piętra czytają się jak tajga.
- Grab (V1, D2): kora jasnopopielata i gładka, z jaśniejszymi pionowymi smugami, wyraźnie jaśniejsza niż `pale_oak_log` (dziś wygląda jak drzewo martwe albo osmalone). Liście każdego gatunku różne tonem i jasnością, nie tylko kształtem (dąb jaśniejszy i luźniejszy, grab ciemniejszy i gęsty, lipa sinawa od spodu). Okap grądu w REAL na 8–15 m, dwa piętra (dąb wyżej, grab i lipa niżej), a nie 3–5 bloków nad gruntem.
- Martwe drewno (V11, `DeadwoodPlan`): wanilijny `fallen_tree` (np. `deadwood/hornbeam`) stawia zawsze jednoblokowy pniak ze słojami, więc w trybie N widać ślady wycinki. W trybie N kłoda z kikutem korzeni albo złom; na miejscach `slice` w trybie N bez pniaków (pniaki tylko w lesie gospodarczym, PW-4).
- Runo (V12, V13): geofity wiosenne i konwalia kwitną naraz pod pełnym okapem letnim, a dno grądu to trawnik `grass_block` z plamami ściółki (park). Fazy runa po stronie klienta mają rozdzielić aspekt wiosenny od letniego okapu. W borach `moss_carpet` jest zbyt nasycony i żółtozielony, w twardych kwadratach na pomarańczowej ziemi (szachownica): przygasić mech, ściółka igliwia rdzawobrązowa, nie pomarańczowa, borówka żywiej zielona niż mech (dziś wanilijny `bush` jest szarobladozielony).

**Testy rundy 1.** `-PfindSliceSites` 2 × (ten sam plik), `SliceSitesTest` (5 testów, w tym nowy `camerasCoverEveryPlace`), `build`, `fastTest`, pełny zestaw `tools/dev/run-tests test` (PASS, 1208 s); `runClientGameTest -Pgametest=slice -PfindSliceCameras` w obu skalach i trybach (PASS, 67 min, `HABITAT_MISS` 0), powtórka z pliku kamer (GAMEPLAY N `oak_hornbeam_1`, PASS), `-Pgametest=slice -Psites=none -PnoSeasons` (PASS), `-Pgametest=stages -Psites=slice` 5 × w jednej sesji (PASS). Złoty test bez zmian.
