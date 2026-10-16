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
| **B6** | Kolor liści | (a) **własne krzywe gatunków** na „surowym” kolorze listowia biomu (`RAW_FOLIAGE` w `ColorResolverRegistry`, Biome Blend zostaje), z fazą drzewa `phase` 0–3 z hasza pnia. Działa bez SS, a z SS nie sezonuje drugi raz. SS jest tylko źródłem pory roku i wyłącznikiem `changesFoliageColor`; (b) kolor biomu sezonowany przez SS × mnożnik gatunku; (c) statyczne bloki jesienne jak topola 26.3 | **(a), z (b) jako zapasem.** Tylko (a) daje różne terminy przebarwienia (brzoza i lipa od końca IX, dąb późno) i zmienia kolory także bez SS. Ryzyko: Sodium z paczki i własny `ColorResolver` (potwierdzony tylko w gałęzi deweloperskiej). Sprawdza to próba V3 przed CP-A. Jeśli nie przejdzie, wycinek bierze (b), a (a) przechodzi do M3. **Rozstrzygnięte w V3 (2026-10-16): (a).** Własny resolver działa bez Sodium i z Sodium 0.9.2 z paczką, z SS i bez: kolor ściany próbnej zgadza się z krzywą (chromatyczność ±0,005), a surowe listowie nie zmienia się z porą SS (bez podwójnego sezonowania). Ryzyko zostaje tylko w koszcie przebudowy siatek z Sodium (R9, „Stan po V3”) |

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
| **PW-8** | Jak runo zmienia porę roku | (a) **wygląd faz geofitów po stronie klienta** (owinięty model FRAPI czytający fazę roku, wspólny z zimą koron). Stan serwera tylko dla owoców borówki: losowy tick plus nadrabianie po wczytaniu chunka, odroczone do następnego ticku. Generacja stawia stan neutralny; (b) fazy jako stan bloku, losowy tick z nadrabianiem; (c) bez fenologii runa w wycinku | **(a), z (b) jako zapasem**, gdyby próba V3 (owinięcie modelu przy Sodium i MoreCulling) nie przeszła. Przy (a) wszystkie widoczne chunki są w bieżącej fazie, bez pakietów bloków. Stan zmieniany losowym tickiem ma średnio ok. 68 s opóźnienia, a poza zasięgiem symulacji zostaje stary. **Rozstrzygnięte w V3 (2026-10-16): (a).** Owinięty model (`ModelLoadingPlugin`, `emitQuads`) rysuje w fazie pustej nic, a liść w fazie bezlistnej gałązki, z Sodium, MoreCulling i w trybie Fast („Stan po V3”) |
| **PW-9** | Przycinanie koron do sąsiadów | Rozstrzyga próba V5. Warianty: (a) **terytoria koron z samego wzoru kandydatów pni, `StandMap` i hasza**, bez gatunku i kodu siedliska; (b) kody siedliska w kolumnach kandydatów obszaru 5 × 5 chunków (ok. 270 próbek modelu na chunk, koszt do zmierzenia); (c) przyjąć wyższy próg determinizmu w grądzie | **(a)**. Na etapie FEATURES etap TERRAIN, a z nim `ChunkHabitats`, mają tylko chunki w promieniu 1, a korony sąsiadów z chunków w promieniu 2 są wtedy niedostępne. Kandydaci i `StandMap` są czystą funkcją ziarna, więc terytorium każdego pnia jest znane wszędzie. Kandydat, który nie stał się drzewem, zostawia lukę w okapie. W naturze takie odstępy też są (crown shyness). **Rozstrzygnięte w V5 (2026-10-16): (a)**, z wakatami (kandydaci bez drzewa: reguła palety, teren otwarty) jako znacznikami tylko między pniami z chunków w promieniu 1 od siebie; koszt (a) 0,15–0,6 ms na chunk, (b) dodatkowo samo próbkowanie pierścienia: +1,9–2,7 ms w nowej sesji, ok. +0,3–0,8 ms z pamięcią wątku. **Po rundzie 1 przeglądu V5** kandydat, który nie stał się drzewem, nie zostawia luki: jego komórkę przejmują sąsiedzi. Bez tego zwarcie okapu miało sufit ok. p × pokrycie przy p = 1, gdzie p to udział kandydatów, które stają się drzewami (55–85% przy N1), czyli poniżej celów N4 (zob. „Stan po V5”). **Po rundzie 2:** dokładny wakat zależy od kodu ekotonu (Z10), który dla kandydata sąsiedniego chunka wymaga siedlisk z promienia 2. W grze dokładne wakaty są więc znane tylko w chunku pnia, a w promieniu 1 tylko pewne (luka, losowanie ≥ najwyższej szansy palety). Ta reguła nie daje sporów, ale zostawia pas słabszego pokrycia wzdłuż granic chunków (np. 78% wobec 100% w środku chunka przy p = 0,54). V9 usuwa go wakatami udowodnionymi z regionu albo kodami promienia 2 (V5-i, „Runda 2 poprawek”) |
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
| T-D5 (zima koron) | **W rdzeniu (V13)**: model gałązek po stronie klienta, marcescencja dębu i grabu, modele innych modów bez zmian. Zapas: opcja po CP-D, jeśli próba V3 nie przejdzie. **V3 (2026-10-16): próba przeszła, zima zostaje w rdzeniu.** Bezlistny okap od XI do IV/V to warunek wiosennego aspektu zawilca, czyli wizytówki grądu |
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
| V1b | wygładzenie tekstur po CP-A (dodany 2026-10-16) | B | 0,5–1 | niskie (estetyka) |
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
  - `block/TrunkBlock`: wariant D (`radius` 1–7, `parent` 6 kierunków, `waterlogged`), kształty z `Shapes.rotateAll` (tablica 6 × 7), modele z 7 szablonów `template_trunk_r1…r7` z wysięgiem 8 px i marginesem ε, blockstates przez `PropertyDispatch.modify(PARENT)`; ściany N, E, S i W pokazują różne wycinki tekstury kory (przegląd V1: ten sam środek powtarza motyw 4 razy wokół pnia; `docs/00` G.5 V1-j);
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
- **Wynik (odpowiedź użytkownika 2026-10-16, `docs/00` G.9):** styl tekstur przyjęty jako baza, ale „trochę za ostry”, więc wygładzenie w kroku V1b; cienki pień (wariant D, model po V2b) przyjęty; decyzje „domyślne, do potwierdzenia” z terminem CP-A przyjęte. Drobiazgi modelu pnia (pasek kolanka, dziury w narożnikach czapki) rozstrzyga V10 po pomiarze kosztu renderu (`docs/00` G.6 V2-l).

### V1b. Wygładzenie tekstur po CP-A (B, 0,5–1 dnia; dodany po CP-A)

- **Zakres:**
  - miara kontrastu lokalnego w `tools/art` (średnia i 90. percentyl różnic luminancji sąsiednich pikseli, udział ciemnych linii jednopikselowych, piksele samotne) na naszych teksturach i na korze i liściach wanilii 26.3 (kopia jara tylko poza repozytorium, do repozytorium tylko liczby);
  - przepisy łagodniejsze: kontrast lokalny w okolicy wanilii albo trochę niżej, bruzdy w tonie pośrednim zamiast twardej ciemnej linii, łagodniejsze krawędzie płyt, mniej szumu i samotnych pikseli; nadal kafelkowanie bez szwów i styl wanilii;
  - lint: nowa reguła kontrastu lokalnego z progami z pomiaru; autotest;
  - rekomendacje CP-A: mniej nasycone i mniej płaskie „lustro”, lipa odróżniona od kory zastępczej także z daleka, mniej plamiste igły, drugi wariant kory dębu i lipy, tekstura przejścia między dolną korą a „lustrem” (V1-i) z blokiem albo wartością właściwości przed CP-B (świadoma zmiana migawki rejestru);
  - tekstury (`generate.py`), arkusze w `docs/wycinek/grafika/` (przed i po: `arkusz_8x_przed.png`, `kafle_3x3_przed.png`), zrzuty tablicy próbek w `docs/wycinek/pnie/`.
- **Ukończony, gdy:** lint z regułą kontrastu i `checkArt` przechodzą, każda kora ma kontrast lokalny nie wyższy niż górny kwartyl kory wanilii (liście i rośliny nie wyższy niż ich mediana), arkusze przed i po leżą w `docs/wycinek/grafika/`, tablica próbek przechodzi z nowym blokiem w migawce rejestru, a decyzja CP-A jest zapisana w `docs/00` G i tu.
- **Ryzyko:** niskie; estetyka oceniana przy CP-B na drzewach w lesie.

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
  - wokselizacja 3D DDA z `parent` w stronę korzenia, typ bloku według promienia (`TrunkBlock` albo pełna kłoda), „lustro” sosny od ok. 0,45–0,55 wysokości (parametr rosnący lekko z wiekiem; w naturze rude, łuszczące się „lustro” okrywa mniej więcej górną połowę strzały, także poniżej korony; strefa przejściowa 1–2 bloki według V1-i), zbieżystość strzały poniżej nasady korony (przekrój gałęzi utraconych przez samooczyszczanie rośnie ku podstawie, więc półki r + 1 → r rozkładają się wzdłuż pnia; przegląd V2);
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
  - wnioski rundy 1 przeglądu V3 (`docs/00` G.8 V3-b, V3-e … V3-i): mixin na `forceOpaque` i ściany gałązek do liści w trybie Fast, cząstki według fazy, własne gałązki gatunków z wariantami, próby w trzech rendererach, fenologia według gatunku;
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
| R5 | przycinanie koron i determinizm: sąsiedzi z chunków w promieniu 2 nie mają `ChunkHabitats` na etapie FEATURES | różnice zależne od kolejności, spory koron | próba V5, PW-9 (a), test determinizmu w każdym kroku drzew (w V6 także ekoton z drzewami zastępczymi wanilii, które ignorują terytoria; `docs/00` V5-h). Wakat kandydata wolno uznać tylko wtedy, gdy na pewno nie wyrośnie na nim drzewo: dokładne wakaty tylko w chunku pnia, w promieniu 1 tylko pewne (runda 2 V5). Pas słabszego zwarcia na granicach chunków V9 usuwa według V5-i i sprawdza zrzutem okapu z góry | V5–V11 |
| R6 | okno zapisu 3 × 3 chunki | ucięte korony i długie kłody | promień korony ≤ 16, kłody do wnętrza okna | V6, V11 |
| R7 | korony nad dachami struktur | liście w wioskach | `BUILDING_GAP` z promienia korony, maska struktur, test wiosek z S8 | V9 |
| R8 | dekoratory wanilii (huby, mech) na cienkich pniach | „wiszą” obok kory | brak dekoratorów wanilii na cienkich pniach, własne huby w M4 | V6 |
| R9 | przebudowa siatek przy zmianie kroku koloru lub fazy | zacięcie klatki; `allChanged()` zrzuca cały widok na ok. 10 klatek gametestu (0,3–1,8 s w zwykłej grze) (V3) | 3 kroki na podsezon, przebudowa przez oznaczenie sekcji falą od kamery ze środkiem z początku fali (V3, runda 1: w żadnym zestawie, wanilia, Sodium, Sodium + Nvidium, nie ma przy tym zacięcia: w 34 z 36 przebudów w dwóch sesjach gametestu żadna klatka > 33 ms, raz 649 ms przy fali z Nvidium; w zwykłej grze praca klatki ≤ 30 ms; wcześniejsze „4–7 klatek po 100–570 ms z Sodium” się nie powtórzyło). Zostaje `allChanged()` samego SS przy zmianie podsezonu (1–2 klatki po 24–154 ms, widok bez siatek 0,3–1,8 s w zwykłej grze). Zapas: 1 krok na podsezon; V10 potwierdza w przelocie | V7, V10, V13 |
| R10 | własny `ColorResolver` przy Sodium z paczki | brak koloru gatunku albo błędy | próba V3 przed CP-A, zapas B6 (b). **Zamknięte w V3:** działa; runda 1: także mieszanie biomów w generatorze siatek (6 miejsc przy granicach biomów w świecie REAL: kolor surowy widziany przez generator siatek wanilii, Sodium i Sodium + Nvidium równy mieszaniu wanilii z dokładnością do 1 na kanał) | V3 |
| R11 | FRAPI na 26.3 (`ModelLoadingPlugin`, MoreCulling, Continuity, tryb Fast) | brak zimy koron i faz runa | próba V3, sygnatury spisane, zapas PW-8 (b) i zima jako opcja. **V3:** owinięcie działa z Sodium, Nvidium i MoreCulling. Tryb Fast: czysty Sodium przenosi wszystkie quady bloku `LeavesBlock` do materiału SOLID (`forceOpaque`), więc gałązki wycięte dopiero z mixinem na statycznym `ModelBlockRenderer.forceOpaque` (runda 1, sprawdzone w wanilii, Sodium i Sodium + Nvidium; V3-b) i z wewnętrznymi ścianami przez zdjęcie ściany odrzucania quadów w stronę liści; Continuity niesprawdzone (nie ma go w paczce) | V3, V13 |
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

**Testy.** `SliceSitesTest` (tabela: każde miejsce w obu trybach, komplet miejsc w każdej skali i trybie, odległości), `fastTest`, `build`, pełny zestaw `tools/dev/run-tests test` (PASS, 1233 s), `runClientGameTest -Pgametest=slice` w obu skalach i trybach (PASS; transekty powtórzone po zmianie wyszukiwania z `-Psites=transect`, PASS), `-Pgametest=stages -Psites=slice` 4 × (PASS). Złoty test bez zmian (wycinek nie zmienia terenu).

### Runda 1 poprawek po przeglądzie V0 (2026-10-15)

Wszystkie uwagi przeglądu potwierdzone (żadna nie była fałszywym alarmem); poprawione albo zapisane jako kryteria dla dalszych kroków.

**Miejsca: grąd typowy zamiast zboczowego.** Potwierdzone: w GAMEPLAY `oak_hornbeam_2` (−1509, 181) miał 100% (D: 98,9%) zespołu `SLOPE` (grąd zboczowy), `oak_hornbeam_1` (−1279, 530) 75,5%, w REAL `oak_hornbeam_1` (534, −714) 28,1%; także odcinek grądu transektu GAMEPLAY był w ponad połowie zboczowy (16% linii zboczowe wobec 12% typowych). `SliceSitesTest` wymaga teraz zespołu typowego (`Association.TYPICAL`) na ≥ 90% kwadratu w obu trybach (`MIN_TYPICAL`), a w transekcie na ≥ 90% kolumn czterech lasów (wydłużanie końców też tylko w typowym biomie). Nowe miejsca: REAL `oak_hornbeam_1` (438, −730) 98,4% typowego (D: 93,9% i 4,5% pól), GAMEPLAY `oak_hornbeam_1` (−1785, 886) 90,0% (10% zboczowego), `oak_hornbeam_2` (870, 1961) 98,0% (D: 97,7%), transekt GAMEPLAY (−1590, −65) → (−1590, 175), 241 bloków, wszystkie lasy w 100% typowe (3,3% linii to bór suchy jako inny biom). Bory świeże i transekt REAL bez zmian (zespół typowy 95,2–100%). Udziały biomów i zespołów każdego miejsca drukuje `./gradlew test --tests '*SliceSitesTest*' -PsliceReport`.

**Wyszukiwanie deterministyczne.** Linie transektu są sortowane po długości i potem po komórkach, więc równoległe zbieranie nie zmienia wyniku; dwa przebiegi `-PfindSliceSites` zapisały identyczny plik, a transekt REAL wyszedł ten sam co w pierwszym oddaniu V0. Plik ma końce linii LF na każdym systemie.

**Stałe kamery** (`src/test/resources/slice_cameras.txt`, 55 kamer, czytane przez `SliceCameras`). `./gradlew runClientGameTest -Pgametest=slice -PfindSliceCameras` wybiera kamery w pierwszym świecie każdej skali (tryb N) i zapisuje plik; każdy inny przebieg bierze je z pliku (bez pliku albo bez kamer miejsca test kończy się błędem). Jeśli zapisana kamera stoi w pniu, liściach albo bloku pełnym (nowe drzewa w V2+), przebieg loguje to i usuwa te bloki (stopy i głowa; przy widokach z góry 3 × 3 × 3 wokół głowy), a przy kamerach z wysokości oczu loguje też, czy grunt się przesunął. Kamery „z góry” liczone są od gruntu (grunt + 30 / 35 / 70), a nie od pierwszej wolnej przestrzeni nad liśćmi. `SliceSitesTest.camerasCoverEveryPlace` (w `fastTest`) pilnuje, że każde miejsce ma komplet kamer, a `eye` i `canopy` leżą ≤ 20 bloków od środka. Powtarzalność: drugi przebieg z pliku (GAMEPLAY N `oak_hornbeam_1`) dał te same kadry, różnica 0,4–2,0% pikseli (falująca trawa).
- `eye`: z kolumn ≤ 8 bloków od środka i 8 kierunków (od 30°, czyli SSW, co 45°) ta z najdłuższą wolną linią wzroku: stożek ±20° (do 3 bloków ±45°, bo pień 2 bloki obok wypełnia brzeg kadru o polu ±51°) bez pni, liści i bloków pełnych na wysokości stóp i oczu (do 4 bloków także nad głową), szukane do 16 bloków. Wyszło 9–16 bloków na miejscach i 7,5–16 na transektach (wyjątek: `edge_mixed_forest_oak_hornbeam_forest_eye` GAMEPLAY, 4 bloki, gęsty podszyt na granicy).
- `canopy`: osobna kolumna ≤ 16 bloków od środka, najbliższa, w której 5 bloków od stóp jest wolnych (najniższe liście ≥ 4 bloki nad głową), a liście są w 30 blokach nad nią i nad czterema kolumnami 2 bloki obok; pionowo w górę (−90°), yaw 90. **Poprawione w V1** (uwaga 4 przeglądu V0, niżej w „Stan po V1”): pierwotne zdanie „pora 3000 (słońce 45° nad wschodnim horyzontem, poza kadrem)” było nieprawdziwe, bo kąt słońca nie rośnie liniowo z porą dnia; o 3000 tarcza była ok. 31–35° od zenitu i widać ją było w 6 z 8 kadrów. Od V1 pora 1000 (ok. 62° od zenitu), kolumna bez kłód w promieniu 3 bloków od stóp do +8 bloków i ostrzeżenie o tarczy słońca w logu; 8 kadrów `canopy` trybu N w bazie podmienione. Od rundy 1 poprawek V1 kolumna może mieć kłody w tym walcu, jeśli pnie zajmują najwyżej 5% kadru (promienie przez kadr, `trunkCover`); bierze się najbliższą kolumnę spełniającą jeden z tych warunków, a gdy żadnej nie ma, tę z najmniejszym udziałem pni.
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

## Stan po V1 (2026-10-16)

Runda 1 poprawek (niżej, „Runda 1 poprawek po przeglądzie V1”) przerobiła przepisy kory, liście, borówkę, lint i arkusze; opis tekstur, lintu i tabela pomiarów w tej sekcji dotyczą pierwszego oddania.

**Zrobione.**
- **`tools/art/`** (Python 3.10+ z Pillow i NumPy; kod na MIT):
  - `texlib.py`: własny generator liczb losowych SplitMix64 (wynik nie zależy od wersji NumPy ani od maszyny, a `checkArt` porównuje piksele), szum wartości i fBm na torusie 16 × 16, komórki Worleya na torusie (z rozciągnięciem w pionie lub w poziomie), kwantyzacja progami kwantyli (każdy ton użyty, udziały tonów do wyboru), filtr większościowy na torusie, światło z lewej u góry przy bruzdach (`edge_light`: jaśniejsza górna i lewa krawędź płyty, cień przy dolnej i prawej), stemple (liście), warianty o wspólnych krawędziach (`edge_locked`), rampy kolorów z palety (`ramp`: kolory palety po jasności, przeskalowane do zakresu luminancji przepisu; `ramp_hue`: średni odcień palety z nasyceniem i jasnością przepisu), szarość pod tint;
  - `recipes.py`: przepisy gatunków (rodzaj, tint, paleta, ziarno, parametry, granice stylu `expect`) z opisem kory każdego gatunku (sprawdzone na zdjęciach CC0 i z domeny publicznej z Wikimedia Commons oraz w opisach gatunków w sieci i w `docs/research/06` §4.1: sosna dolna szarobrązowa w nieregularnych, podłużnych, łuskowatych płytach z ciemnymi bruzdami i rdzawym spodem po odpadłych łuskach, „lustro” cienkie, pomarańczowe, w papierowych łuskach; dąb szarobrązowy, głęboko i gęsto bruzdowany w pionie, listwy łamane w „palcowate” płytki; grab gładki, jasnopopielaty, „umięśniony”, z jaśniejszymi pionowymi smugami; lipa szarobrązowa z siecią płytkich podłużnych spękań; buk gładki, jasnosrebrnoszary, plamisty, z ciemnymi poziomymi „brwiami” i porostami);
  - `palettes.json` i `palette.py`: palety k-średnich (6 kolorów dla kory, 4 dla igieł i borówki, start z kwantyli jasności, deterministycznie) z wycinków 14 zdjęć CC0 i z domeny publicznej z Wikimedia Commons; w repozytorium są tylko kolory, udziały, wycinek (ułamki szerokości i wysokości) i odnośnik do strony zdjęcia z autorem i licencją. Zdjęcia nie trafiły do repozytorium, nie były skalowane do tekstur ani przerysowywane;
  - `lint.py` (styl), `selftest.py` (deterministyczność, kafelkowanie szumów, warianty, czy lint łapie błędy), `check.py` (`checkArt`), `generate.py` (tekstury i `CREDITS.md`), `credits.py`, `sheet.py` (arkusze), katalog `tools/art/overrides/` na ręczne poprawki użytkownika (obsługiwany w `generate.py`: plik `overrides/<nazwa>.png` zastępuje wynik przepisu i przechodzi tylko lint; w pierwszym oddaniu pusty katalog nie trafił do git, więc go nie było — od rundy 1 jest w nim `README.md`).
- **Lint stylu** (każda tekstura): 16 × 16; 4–9 kolorów; tekstury pod tint (liście, borówka, igły) w czystej szarości (R = G = B) ze średnią jasnością 0,45–0,65; kafelkowanie bez szwu (skok przez krawędź nie większy niż największy skok między sąsiednimi wierszami albo kolumnami wewnątrz, z zapasem 15% + 4); warianty łączą się z teksturą bazową w pionie w obu kolejnościach; liście 55–70% pikseli widocznych; kora w pełni nieprzezroczysta; alfa tylko 0 albo 255; piksele pojedyncze (różne od 4 sąsiadów) ≤ 10% w korze i ≤ 30% w liściach (końce igieł i światła liści są pojedyncze celowo); granice przepisu: średnia luminancja i największy udział ciemnych pikseli (luminancja < 50).
- **`checkArt`** (`./gradlew checkArt`, grupa `verification`, poza `build` i `check`): porównuje **zdekodowane piksele RGBA** i treść `.mcmeta` (JSON) plików w `src/main/resources/assets/polishforests/textures` z wynikiem generatora (albo z plikiem z `overrides/`), zgłasza każdy plik PNG lub `.mcmeta` spoza przepisów, uruchamia lint i autotest i sprawdza, czy `CREDITS.md` jest aktualny. Interpreter: `python3`, `python`, `py -3` po kolei albo `-Ppython=<ścieżka>`; bez Pythona 3.10+ z Pillow i NumPy zadanie kończy się komunikatem, co zainstalować i jak wskazać interpreter (sprawdzone z `-Ppython=/nonexistent/python`). `./gradlew generateArt` przepisuje tekstury i `CREDITS.md`. Zadanie zgodne z pamięcią podręczną konfiguracji Gradle.
- **Pierwsze 10 tekstur** (`textures/block/`): `scots_pine_bark` i `scots_pine_bark_2` (dolna kora sosny, 2 warianty o identycznym górnym i dolnym wierszu: środek każdego pola szumu jest z ziarna wariantu, a pas 3 wierszy przy krawędziach z ziarna bazy, progi tonów też z bazy; warianty układają się w pniu w dowolnej kolejności bez szwu, co sprawdza lint i autotest), `scots_pine_upper_bark` („lustro”, osobny blok `scots_pine_upper_trunk` z PW-11), `oak_bark`, `hornbeam_bark`, `linden_bark`, `beech_bark`, `scots_pine_leaves` (igły: „szczotki” pędów z parami igieł 3–4 px, w szarości pod stały kolor z V7, propozycja `#8e9963`), `broadleaf_leaves` (stemple liści 2–4 px pod tint listowia, wspólne dla gatunków liściastych do V7), `bilberry` (krzewinka do modelu krzyżowego: kanciaste, rozgałęzione pędy i drobne naprzemianległe liście, pod tint trawy; w pierwszym oddaniu 13 px wysokości, rzędy 3–15, a nie 9–11 px, jak tu stało — poprawione w rundzie 1). Kora dębu i lipy to jeden przepis (`bark_furrowed`: długie listwy między bruzdami z rozciągniętych komórek Worleya, łamane krótkimi poprzecznymi pęknięciami), z innymi parametrami: dąb ma głębokie, ciemne bruzdy i szersze listwy, lipa płytkie spękania (ton 1, tylko 5% najgłębszych w tonie 0), węższe i jaśniejsze listwy.
- **Arkusze** w `docs/wycinek/grafika/`: `arkusz_8x.png` (każda tekstura 8 × i kafel 3 × 3 w 4 ×, z tintem jak w grze: listowie i trawa grądu i boru świeżego z `src/main/generated`, igły w kolorze propozycji), `kafle_3x3.png` (kafle 3 × 3 w 8 ×), `pnie.png` (kolumny 12 bloków jak w pniu: sosna z 8 blokami dolnej kory w obu wariantach i 4 blokami „lustra”, pozostałe gatunki; obok wycinek 6 px, czyli bok cienkiego pnia).
- **Licencja zasobów (D5, D6):** `LICENSE-assets` (wstęp: zakres, uznanie autorstwa „Polish Forests by SernikNaZimno”, wanilia nieobjęta; potem pełny tekst CC BY 4.0 ze strony Creative Commons), `CREDITS.md` z generatora (licencje kodu i zasobów, tabela palet ze zdjęciami, autorami i licencjami, miejsce na dźwięki), `fabric.mod.json` z `"license": ["MIT", "CC-BY-4.0"]`, w pliku moda `LICENSE_polishforests`, `LICENSE-assets_polishforests` i `CREDITS_polishforests.md`, notka w README (rozdział „Licencja” i polecenia `tools/art`).
- **Dolna kora sosny ma czytelne płyty:** średnia luminancja 107–111 (prototyp rundy 2 z raportu: 60; `oak_log` wanilii ok. 88), piksele ciemne (luminancja < 50, bruzdy) 18–19% (prototyp: 65%), granice przepisu 95–125 i ≤ 20% pilnuje lint. Komórki płyt mają 2–6 px szerokości i 9–16 px wysokości, a tony i poprzeczne łuski dzielą je na mniejsze płytki (pień o promieniu 3 pokazuje 6 px szerokości: `pnie.png`), jasną górną i lewą krawędź, rdzawy spód po odpadłych łuskach przy części bruzd.

**Pomiary** (lint, wartości z `python tools/art/lint.py`; luminancja Rec. 709 na 0–255, „ciemne” < 50, „pojedyncze” to piksele różne od 4 sąsiadów):

| Tekstura | Kolory | Widoczne | Luminancja | Ciemne | Pojedyncze |
|---|---|---|---|---|---|
| `scots_pine_bark` | 7 | 100% | 110,6 | 18% | 8% |
| `scots_pine_bark_2` | 7 | 100% | 106,7 | 19% | 9% |
| `scots_pine_upper_bark` | 5 | 100% | 126,7 | 0% | 8% |
| `oak_bark` | 6 | 100% | 89,4 | 23% | 1% |
| `hornbeam_bark` | 6 | 100% | 146,3 | 0% | 0% |
| `linden_bark` | 6 | 100% | 110,0 | 0% | 3% |
| `beech_bark` | 5 | 100% | 154,1 | 0% | 1% |
| `scots_pine_leaves` (szarość) | 4 | 62% | 144,9 (0,57) | 0% | 21% |
| `broadleaf_leaves` (szarość) | 5 | 63% | 127,5 (0,50) | 0% | 26% |
| `bilberry` (szarość) | 4 | 43% | 156,7 (0,61) | 0% | 29% |

Grab (146) jest wyraźnie jaśniejszy niż `pale_oak_log` wanilii (średnio ok. 80), czego wymagała uwaga przeglądu V0. Generacja 10 tekstur trwa ok. 0,4 s, `checkArt` z autotestem ok. 3 s (w tym start Gradle).

**Odstępstwa.**
- Źródła palet są w `palettes.json` (sekcja `sources`), a nie w osobnym `tools/art/sources.json` z raportu badawczego: jeden plik z kolorami i źródłami, z którego `credits.py` robi `CREDITS.md`.
- Przepisy gatunków są w jednym module `recipes.py` (funkcje i lista `TEXTURES`), a nie w katalogu `recipes/*.py` albo w JSON: przy 10 teksturach i 8 funkcjach to czytelniejsze; przy kolejnych gatunkach można je rozdzielić.
- `checkArt` sprawdza też `CREDITS.md` i autotest `tools/art`, a nie tylko piksele i `.mcmeta`.
- Lint ma dodatkowo granicę pikseli pojedynczych (z raportu: „brak pojedynczych pikseli powyżej progu”), granice przepisu (luminancja, ciemne piksele) i regułę wariantów; próg szwu porównuje skok przez krawędź z największym skokiem wewnątrz, a nie ze średnim, bo tekstury o silnym kierunku (bruzda na skrajnej kolumnie) dawały fałszywe alarmy. Ta reguła złapała prawdziwy błąd: pierwsza wersja lipy miała w wierszach 15|0 skupisko granic komórek (skok 29 wobec średnio 10).
- Kora dębu, grabu, lipy i buka ma na razie po 1 wariancie (sosna dolna 2). `pnie.png` pokazuje powtarzanie co 16 px; drugi wariant to decyzja przy CP-A (`docs/00` G.5, V1-b).
- Borówka i igły są w szarości pod tint (trawa biomu i stały kolor z V7), a nie w gotowych kolorach (`docs/00` G.5, V1-c).
- Odcień „lustra” i dolnej kory sosny bierze z palety tylko odcień (`ramp_hue`), a nasycenie i jasność ustala przepis: zdjęcia pnia w słońcu dawały wypłowiałą, różowawą szarość (paleta lustra: odcień 10–26°, nasycenie jasnych tonów 0,1–0,3), więc tekstura z samych kolorów zdjęcia wyglądała łososiowo (`docs/00` G.5, V1-e).
- Arkusze nie zawierają tekstur wanilii do porównania (pliki wanilii nie trafiają do repozytorium); porównanie w grze przy V2 (tablica próbek) i CP-A.

**Przeniesione z przeglądu V0 (kadr `canopy`, uwaga 4).** Potwierdzone i poprawione (`SliceClientGameTest`). (1) **Słońce:** `CANOPY_TIME` 3000 → 1000. Kąt słońca w grze idzie za wygładzonym kątem niebieskim (szybciej przy horyzoncie): o 3000 tarcza jest ok. 31–35° od zenitu, czyli w pionowym półpolu widzenia 35°; o 1000 ok. 62° od zenitu (28° nad wschodnim horyzontem), a światło nieba jest już pełne. `brightness()` liczy teraz piksele prawie białe (wszystkie kanały > 235) w górnych 15% kadru i przy kadrze `canopy` ostrzega powyżej 40 pikseli (log: `sun pixels`). We wszystkich 8 nowych kadrach jest 0 takich pikseli (wcześniej tarcza lub poświata w 6 z 8). (2) **Pnie przy kamerze:** `canopyCamera` wymaga, żeby w promieniu 3 bloków od kolumny (euklidesowo), od stóp do +8 bloków, nie było kłód; jeśli żadna kolumna w promieniu 16 bloków tego nie spełnia, bierze tę z najmniejszą liczbą bloków kłód i loguje to. Promień 2 z podpowiedzi przeglądu okazał się za mały: pień brzozy w `fresh_pine_1` GAMEPLAY stał na ukos (2, 1) od kolumny, czyli 2,24 bloku, i nadal zajmował ok. 20% kadru. Przy promieniu 3 w 4 miejscach nie ma kolumny bez kłód (REAL `oak_hornbeam_1` 1 blok kłody, `oak_hornbeam_2` 3, `fresh_pine_1` 6, GAMEPLAY `fresh_pine_2` 6); pnie zajmowały tam mniej kadru niż w V0, ale nie tylko brzeg: w GAMEPLAY `fresh_pine_2` pień sięgał z prawego górnego rogu ku środkowi, brzoza wypełniała lewy dolny róg, a trzeci pień prawy dolny, razem ok. 28% kadru (wcześniej ok. 39%; uwaga przeglądu V1, poprawione w rundzie 1). (3) **Baza:** `-PfindSliceCameras -Pmodes=natural -Psites=<4 miejsca>` na świecie M2 (V1 nie zmienia generacji; tag `m2-baseline-r1` ma tę samą generację): zmieniło się tylko 6 wierszy `canopy` w `slice_cameras.txt` (kamery `eye`, `30m`, `bark_*` i transektów identyczne, wybór jest deterministyczny), 8 kadrów `slice_<skala>_natural_<miejsce>_canopy.png` w `docs/wycinek/baza/` podmienione. Niebo w nowych kadrach: grądy REAL 5% i 23%, GAMEPLAY 5% i 16%; bory REAL 4% i 6%, GAMEPLAY 18% i 21% (wcześniej grądy 5–23%, bory REAL 4–5%, GAMEPLAY 24–34%: część nieba bory GAMEPLAY „traciły”, bo kolumny przy prześwitach stały przy pniach). Kadrów `canopy` trybu D w bazie nie ma (D = N wewnątrz miejsc), gametest robi je z tych samych kamer.

**Uwagi do CP-A (subiektywne, ocenia użytkownik).** Najmocniejsze: dąb (bruzdy), grab (jasny, „umięśniony”), buk, igły. Najsłabsze: dolna kora sosny przypomina z bliska „kamuflaż” z płyt nieregularnych zamiast wyraźnych łusek, lipa jest drobna i dość jednolita, „lustro” ma łuski w kształcie plam i jest dość jaskrawe, borówka to raczej masa liści niż widoczne pędy, liście liściaste są wspólne dla gatunków (różnią się dopiero kolorem gatunku w V7). Przejście dolna kora → „lustro” jest ostre (w naturze strefa przejściowa, gdzie szare łuski leżą na pomarańczowym tle); można je dodać jako trzecią teksturę kory sosny, jeśli CP-A tego zechce. Do oceny w grze (V2, tablica próbek): jak kora wygląda na cienkich pniach (bok o szerokości 2–14 px) i czy płyty sosny czytają się z odległości 5–15 bloków.

**Testy.** `python tools/art/check.py` i `./gradlew checkArt` (PASS: 10 tekstur zgodnych z generatorem, lint, `CREDITS.md`, autotest), próba z ręcznie zmienionym pikselem i obcym plikiem PNG (FAIL z nazwą pliku i liczbą różnych pikseli), `checkArt -Ppython=/nonexistent/python` (czytelny błąd), `fastTest`, `jar` (licencje w pliku), `runClientGameTest -Pgametest=slice -PfindSliceCameras -Pmodes=natural -Psites=oak_hornbeam_1,oak_hornbeam_2,fresh_pine_1,fresh_pine_2` (PASS, 2 przebiegi po 8,5 min: promień 2, potem 3), `SliceSitesTest`, pełny zestaw `tools/dev/run-tests test` (PASS, 1233 s). Złoty test bez zmian (V1 nie zmienia terenu ani `src/main/java`).

### Runda 1 poprawek po przeglądzie V1 (2026-10-16)

**Weryfikacja uwag.** Wszystkie 5 uwag poważnych i 14 drobnych przeglądu potwierdziłem pomiarem albo w plikach; fałszywych alarmów nie było. Liczby przeglądu wychodzą z dokładnością do zaokrągleń (np. odchylenie średnich wierszy dębu 14,2; ΔE grab–buk 4,9 wobec 5,0 w przeglądzie i dąb–`oak_log` 16,4 wobec 17,6, inna implementacja CIE76). Wanilijne `oak_leaves.png.mcmeta` rzeczywiście ma `"mipmap_strategy": "dark_cutout"`. Tekstury wanilii do porównań były wypakowane tylko lokalnie, poza repozytorium; w `lint.py` są wyłącznie zmierzone średnie kolory 8 tekstur wanilii (liczby).

**Kora od nowa** (`recipes.py`):
- wspólny generator płyt i listew `plated`: 3–5 falistych pionowych bruzd, każda po jednym pikselu w każdym rzędzie (każdy rząd ma tyle samo bruzd, więc nie ma ciemnych pasów), poprzeczne bruzdy w każdej szczelinie między nimi (sosna: końce płyt w poprzek płyty, czasem ze schodkiem; dąb: pęknięcia z losowej strony albo w poprzek, nie zawsze od bruzdy po lewej; lipa: ukośne spękania łączące bruzdy w sieć), płyty jako spójne obszary z własnym tonem, pionowe smugi na licach, światło z lewej (lewa krawędź płyty jasna, prawa w cieniu; sosna bez fazki na końcach płyt, bo z nią kora czytała się jak mur);
- warianty (`scots_pine_bark_2`, nowe `oak_bark_2` i `linden_bark_2`) dzielą z bazą bruzdy w pasie 3 rzędów przy krawędziach, poprzeczne bruzdy leżące w całości w tym pasie i tony płyt, które do niego sięgają; skrajne rzędy są identyczne (autotest), więc warianty układają się w dowolnej kolejności;
- **wybór przez pomiar** (`best_of`): przepis buduje teksturę dla 24–96 ziaren (`<ziarno>`, `<ziarno>#1`, …) i bierze tę z najmniejszymi pasami, która spełnia granice lintu (deterministycznie);
- **sosna dolna:** długie płyty (9–15 px wysokości, 4–5 px szerokości), ciągłe bruzdy 1 px w ciemnej czerwonobrązowej barwie (luminancja 46), prawa krawędź płyty czerwonobrązowa (bruzda czyta się na 2 px), 2 tony lic ze smugami i jasna lewa krawędź, rdza w skupiskach 2–4 px na dnie bruzd; średnia luminancja 95, 20–21% pikseli ciemnych, |dx|/|dy| 2,1–2,9 (było 1,5–1,7);
- **„lustro”:** rampa od ok. `#6e3420` do `#eca064` (odcień 21°, było 15°, mniej niebieskiego); łuski jak dachówki: pod częścią dolnej krawędzi łuski krótka ciemna szczelina 3–6 px (najwyżej 3 piksele szczelin w rzędzie), nad nią jasna zawinięta krawędź, na jaśniejszych łuskach najjaśniejszy ton (6–20 px w kilku łuskach, nie jedno „oko”); bez jasnego pasa przez całą szerokość;
- **dąb:** 4 bruzdy (luminancja 58, było 30), listwy 3 px, pęknięcia w tonie 1; ciepła szarobrązowa barwa (odcień 34°), ΔE 5,9 od wanilijnego `oak_log` (łup i pełna kłoda grubych dębów są wanilijne, D2); odchylenie luminancji 24,7 (było 40,7), pasy 3,8 (było 14,2);
- **lipa:** 5 płytkich bruzd (ton 1) z kilkoma głębokimi miejscami, ukośne spękania w sieć, węższe i jaśniejsze listwy;
- **grab:** dwie „muskuły” o nierównej szerokości (7–9,5 px, brzegi falują o ±1 px), jasne z lewej, ciemne z prawej, płytka bruzda między nimi, jaśniejsze smugi wzdłuż pnia; chłodna szarość (odcień 206°, bez liliowego), L* 49;
- **buk:** duże, słabo kontrastowe plamy ciepłej srebrnej szarości (L* 65) ze słabą pionową smugą, zielonkawy nalot, 2 nieregularne porosty (5–7 px) i jedna blizna po gałęzi („wąsy”, 10 px) z jasną wargą pod spodem; ΔE 21 od grabu, 9,7 od `smooth_stone`, 12,9 od andezytu.

**Liście, igły, borówka.** `broadleaf_leaves`: 12 stempli zamiast 6, średnia szarość 0,58 (było 0,50; wanilijne `oak_leaves` ok. 0,56). `scots_pine_leaves` i `broadleaf_leaves` mają `.mcmeta` z `"mipmap_strategy": "dark_cutout"` jak wanilia (`checkArt` porównuje treść). Igły: propozycja koloru `#7f9c86` (G.5 V1-c). Borówka od nowa: 8 pędów od podstawy, wierzchołek w rzędzie 8–9 (8 px, ok. 50 cm), liście na całej wysokości, pędy w średnim tonie, podstawa w cieniu, kolumny 1–13 (margines przy brzegu modelu krzyżowego).

**Lint** (`lint.py`, nowe reguły; opis w nagłówku pliku): pasy co blok (odchylenie średnich wierszy luminancji ≤ 5 na całej szerokości i ≤ 8 na środkowych wycinkach pni r = 2…5; wanilia 1,6–3,4 i 2,2–6,7), odchylenie luminancji kory ≤ 28 (sosna 30, głębokie bruzdy), ziarno pionowe |dx|/|dy| w przepisach kory bruzdowanej i płytowej (≥ 2; buk ≥ 1,2), „kamień” (ΔE ≥ 8 od 7 kamieni wanilii, chyba że |dx|/|dy| ≥ 2), każdy kolor na ≥ 4 pikselach (pojedyncze „oko” powtarza się na każdym bloku), odcień „lustra” 18–28°, `near` (dąb ≤ 10 od `oak_log`) i `distinct` (grab ≥ 10 od buka). **Szew:** skok przez krawędź porównany z 75. percentylem skoków wewnątrz + 1,5 odchylenia + zapas (było: z największym skokiem). Próba na 400 teksturach kafelkujących się i 400 wycinkach szumu, który się nie kafelkuje: stara reguła 0,5% fałszywych alarmów i 67% wykrytych, nowa 1,8% i 83%. W teksturach z przezroczystością skok luminancji liczony jest tylko na pikselach nieprzezroczystych po obu stronach (test 3 sigma wobec wszystkich takich skoków wewnątrz), a osobno udział pikseli o różnej alfie. Dla rzadkich liści miara jest słaba (z przesuniętych o 3–5 px dolnych połówek łapie ok. 20–30%; miary wielorzędowe i liczenie urwanych stempli nie były lepsze), więc `overrides/README.md` każe obejrzeć kafel 3 × 3 poprawionych liści. Autotest ma nowe przypadki: wycinek szumu bez kafelkowania, liście z szwem, pas jak w pierwszym „lustrze”, za duży kontrast, kolor na 2 pikselach, kora jak andezyt, kora płytowa bez ziarna.

**Inne.** `tools/art/overrides/README.md` (zasada nazw, lint, licencja); `checkArt` odrzuca plik PNG w `overrides/`, którego nazwa nie pasuje do żadnej tekstury (sprawdzone na `oak_bar.png`). `sheet.py`: `pnie.png` z wariantami w losowej kolejności, nowe `pnie_cienkie.png` (pnie r = 2, 3, 5, dwie ściany z cieniem 0,8 i 0,6) i `dystans.png` (mipmapy poziomu 2 i 3). `CREDITS.md` podaje, że paleta grabu jest tylko zdjęciem odniesienia. README i BRIEF: palety ze zdjęć „CC0 i z domeny publicznej” (**odstępstwo od planu V1**, który mówił o zdjęciach CC0: 11 zdjęć to CC0, 3 to domena publiczna — `CarpinusBetulusBark.jpg`, „Schwartau Winterlinde”, „Bark of Fagus sylvatica with white spots”; licencje sprawdził przegląd, raport badawczy i `CREDITS.md` dopuszczają PD). W `build.gradle` przywrócona spacja w komentarzu. Plan V2 (§3) dostał uwagę o różnych wycinkach kory na ścianach N, E, S, W.

**Kadr `canopy` (uwaga drobna przeglądu).** Zdanie o pniach „tylko na brzegu kadru” poprawione wyżej. `canopyCamera`: gdy kolumna ma kłody w walcu r = 3, liczy udział pni w kadrze (`trunkCover`: 24 × 14 promieni przez kadr 16:9 o polu widzenia 70°, do 32 bloków; liczy się promień, którego pierwszy niewolny blok to kłoda) i przyjmuje najbliższą kolumnę z udziałem ≤ 5%; gdy żadnej nie ma, tę z najmniejszym. Pierwsza wersja (zawsze najmniejszy udział) wybierała najgęstsze miejsca okapu, dlatego próg. `-PfindSliceCameras -Pmodes=natural -Psites=<4 miejsca>` (2 przebiegi, PASS, po 8,5 min): zmieniło się 7 wierszy `canopy` w `slice_cameras.txt` (bez zmiany tylko GAMEPLAY `oak_hornbeam_1`), 7 kadrów bazy w `docs/wycinek/baza/` podmienione. Pnie według promieni 2–6% kadru; na zrzutach widać je przy dolnym brzegu, w GAMEPLAY `fresh_pine_2` ok. 8% (było ok. 28%; promienie trochę zaniżają). Niebo: grądy REAL 21% i 0%, GAMEPLAY 5% i 22%; bory REAL 7% i 5%, GAMEPLAY 33% i 15%. REAL `oak_hornbeam_2` ma okap zwarty (kolumny bez pni w kadrze są tam pod pełnym okapem; wcześniejsze 23% nieba dawała kolumna przy pniu).

**Pomiary** (lint; luminancja 0–255, „pasy” = odchylenie średnich wierszy na całej szerokości / największe na wycinkach r = 2…5, „ziarno” = |dx|/|dy|):

| Tekstura | Kolory | Luminancja | Odchylenie | Pasy | Ziarno | Uwagi |
|---|---|---|---|---|---|---|
| `scots_pine_bark` | 6 | 95,8 | 29,1 | 4,4 / 7,6 | 2,87 | ciemne 20% (było: pasy 9,5 / 18–23, ziarno 1,7) |
| `scots_pine_bark_2` | 6 | 94,5 | 29,6 | 4,0 / 6,9 | 2,06 | ciemne 21% |
| `scots_pine_upper_bark` | 5 | 116,0 | 21,2 | 4,6 / 6,4 | 0,57 | odcień 21° (było 15°, pasy 10,9 / 19,5) |
| `oak_bark` | 6 | 92,2 | 24,7 | 3,8 / 3,8 | 2,51 | ΔE 5,9 od `oak_log` (było: odchylenie 40,7, pasy 14,2) |
| `oak_bark_2` | 6 | 91,9 | 24,7 | 4,1 / 6,0 | 2,42 | |
| `hornbeam_bark` | 5 | 117,2 | 16,4 | 1,9 / 3,6 | 4,59 | L* 49, ΔE 8,7 od andezytu, 16,7 od `smooth_stone` |
| `linden_bark` | 6 | 107,2 | 27,0 | 4,1 / 6,3 | 2,21 | |
| `linden_bark_2` | 6 | 107,3 | 26,9 | 4,0 / 7,2 | 2,23 | |
| `beech_bark` | 6 | 157,8 | 14,1 | 3,7 / 6,3 | 1,22 | L* 65, ΔE 21 od grabu (było 5,0) |
| `scots_pine_leaves` | 4 | 144,9 (0,57) | | | | 62% widocznych |
| `broadleaf_leaves` | 5 | 147,8 (0,58) | | | | 63% widocznych |
| `bilberry` | 4 | 158,4 (0,62) | | | | 30% widocznych, rzędy 8–15 |

Generacja 12 tekstur z wyborem ziaren trwa ok. 1,6 s, `python tools/art/check.py` ok. 4,3 s (spokojna maszyna, 2 przebiegi).

**Odstępstwa od podpowiedzi przeglądu.** Sosna ma odchylenie luminancji 29–30 (granica przepisu 30 zamiast 28): ciągłe bruzdy w luminancji 46 przy licach 100–130 tego wymagają. Grab ma L* 49 zamiast 50–54: przy L* 50 i zachowaniu ΔE ≥ 8 od andezytu (L* 57) kora musiałaby być wyraźnie niebieska (G.5 V1-g). Szerokość muskuł grabu 7–9,5 px (na 16 px mieszczą się dwie). Przy liściach test szwu łapie tylko grube szwy (wyżej). Reguła „kamienia” nie dotyczy kory z wyraźnym ziarnem pionowym (grab ma kolor bliski `stone`, ΔE 5,7, ale |dx|/|dy| 4,6).

**Do CP-A** (ocena użytkownika, `docs/00` G.5): styl kory na nowych arkuszach (`kafle_3x3.png`, `pnie.png`, `pnie_cienkie.png`, `dystans.png`); strefa przejściowa dolna kora → „lustro” (V1-i, decyzja przed CP-B); drugi wariant grabu i buka (V1-b); kolor igieł `#7f9c86` i tint borówki (V1-c). Liście liściaste są nadal wspólne dla 4 gatunków (różnice gatunków w V7). Tekstury jeszcze nie były w grze (V2, V3).

**Testy rundy 1.** `python tools/art/check.py` i `./gradlew checkArt` (PASS: 12 tekstur, lint, `CREDITS.md`, autotest z nowymi przypadkami), `fastTest` (PASS), `SliceSitesTest` (PASS), `runClientGameTest -Pgametest=slice -PfindSliceCameras -Pmodes=natural -Psites=oak_hornbeam_1,oak_hornbeam_2,fresh_pine_1,fresh_pine_2` 2 × (PASS), pełny zestaw `tools/dev/run-tests test` (PASS, 1200 s, na drzewie różniącym się od commitu tylko tym zdaniem). Złoty test bez zmian (runda nie zmienia terenu ani `src/main/java`).

## Stan po V2 (2026-10-16)

**Zrobione.**
- **`block/TrunkBlock`** (wariant D, T-D1/T-D2): `radius` 1–7 (średnica 2–14 px), `parent` (6 kierunków, sąsiad w stronę korzenia), `waterlogged`; 84 stany na blok. Kształt kolizji i obrysu: rdzeń `Block.cube(2r)` plus ramię `Block.boxZ(2r, 0, 8)` do ściany rodzica, obrócone `Shapes.rotateAll` (tablica 6 × 7 liczona raz); wysięg modelu poza blok jest tylko wizualny. Blok nie jest pełny: przepuszcza światło nieba (tłumienie 0, zalany 1), nie zasłania ścian sąsiadów, nie dusi. Postawiony przez gracza bierze za rodzica kliknięty blok, a promień z klikniętego pnia (pień „idzie dalej”) albo 4. Obrót i odbicie struktur obracają `parent`. Twardość, dźwięk, instrument i palność (5/5, `FlammableBlockRegistry`) jak kłoda wanilii; przedmioty pni są paliwem jak kłoda.
- **10 bloków gatunków wycinka** (`block/TreeTrunk`, rejestracja `block/ModBlocks`, przedmioty w zakładce „Bloki naturalne” za kłodą bladego dębu): `scots_pine_trunk`, `scots_pine_upper_trunk` (PW-11), `spruce_trunk`, `birch_trunk`, `oak_trunk`, `hornbeam_trunk`, `linden_trunk`, `beech_trunk`, `ash_trunk`, `norway_maple_trunk` (sosna, świerk, brzoza, dąb, grab, lipa i buk z §1.1 oraz klon zwyczajny i jesion, jedyne gatunki archetypu „liściasty szeroki” w paletach grądu, BMśw i LMśw; jawor rośnie tylko poza pasem W1b).
- **Kora:** sosna dolna, dąb i lipa w 2 wariantach (losowo na blok jak warianty wanilii; wspólne skrajne rzędy z V1, więc bez szwu), „lustro”, grab i buk z tekstur V1; brzoza i świerk z wanilii przez id (`minecraft:block/birch_log`, `spruce_log`, D2); klon i jesion ze wspólną korą zastępczą `broadleaf_bark` (nowa tekstura `tools/art`: przepis `bark_furrowed` z barwą zdjęcia lipy, jaśniejsza i szarsza, siatka wąskich listew; lint przechodzi, ΔE ≥ 8 od lipy). Słoje (`#end`: tylko koniec wysięgu, czyli kikut po zniszczeniu rodzica, i przedmiot) to wierzch kłody, którą pień daje jako łup (wanilia przez id).
- **Modele** (datagen, ZW-9): własny `DataProvider` `TrunkTemplates` pisze 7 szablonów `template_trunk_r1…r7` i szablon przedmiotu; `ModBlockModels` (Fabric) robi po 7 modeli na blok i wariant kory i blockstates przez `PropertyDispatch.initial(RADIUS)` i `PropertyDispatch.modify(PARENT)`. Szablon to ramię skierowane **w dół** (`parent=down` bez obrotu: stojący pień, przypadek najczęstszy, ma korę we właściwej orientacji); obroty: `up` x180, `north` x270, `south` x270 y180, `west` x270 y270, `east` x270 y90. Elementy (a = 8 − r, b = 8 + r): ramię [a, 0, a]–[b, 8, b] (wiersze tekstury 0–8), „czapka” rdzenia [a + 2ε, 8 − δ, a + 2ε]–[b − 2ε, 8 + r, b − 2ε] (wiersze 16 − r…16, na górze kora: żywy wierzchołek pędu ma korę, nie słoje), wysięg [a + ε, −8, a + ε]–[b − ε, δ, b − ε] (wiersze 8 − δ…16, na dole słoje); ε = 0,01 px, δ = 0,25 px; 14 quadów na blok (pełna kłoda 6). Każdy blok rysuje więc jeden cały kafel tekstury na swojej dolnej połowie i górnej połowie rodzica: stojący pień pokazuje korę kafel po kaflu jak kłoda wanilii. Ściany N, W, S, E pokazują różne wycinki kory (V1-j; tabela przesunięć w `TrunkTemplates`: przy r ≤ 2 kora owija pień w sposób ciągły, przy r = 2 cały kafel 16 px, przy r ≥ 3 przesunięcia rozłożone w 0…16 − 2r z ciągłością na części narożników).
- **Łup** (`ModBlockLoot`): z jedwabnym dotykiem sam pień; r 1–2: 1–2 patyki; r 3–7: kłoda gatunku (`TreeTrunk.log()`: wanilijne `oak_log`, `birch_log`, `spruce_log` według D2; do czasu własnego drewna sosna `spruce_log` (V7 zmieni na kłodę LITE), grab i buk `pale_oak_log`, lipa, jesion i klon `oak_log`).
- **Tagi** (`ModBlockTagProvider`): `#polishforests:<gatunek>_logs` (9 tagów, bloki i przedmioty; sosna z obu bloków); tagi bloków dębu, brzozy i świerka w wanilijnych `#oak_logs`, `#birch_logs`, `#spruce_logs` (D2: drewno tych gatunków jest wanilijne; do rundy 1 poprawek także tagi przedmiotów, więc z przedmiotu pnia były deski wanilii, zob. niżej), pozostałe w `#logs_that_burn`, a przez nie w `#logs` (liście nie opadają przy pniu, siekiera, heightmapy i ruch jak przy kłodzie); każdy pień także w `#overworld_natural_logs`, `#mineable/axe` i `#polishforests:trunks` (tagi bloków; tag przedmiotów `trunks` doszedł w rundzie 1 poprawek). Lang EN i PL (nazwy w `TreeTrunk`).
- **Migawka rejestru** (ZW-4, PW-11): `block/RegistrySnapshot` wypisuje bloki moda z właściwościami, wartościami w kolejności i stanem domyślnym oraz przedmioty; `RegistrySnapshotTest` (w `fastTest`) porównuje to z `src/test/resources/registry_snapshot.txt`, a `-PwriteRegistrySnapshot` zapisuje plik od nowa (świadomie, jak złoty test). Testy jednostkowe nie mają ładowarki Fabric, więc pomocnik testów `GameRegistries` robi `Bootstrap.bootStrap()`, otwiera rejestry bloków i przedmiotów przez refleksję (jak mixiny Fabric), rejestruje zawartość moda tą samą metodą co gra (`ModBlocks.registerContent()`), inicjuje pamięć stanów i zamyka rejestry. Ten sam plik sprawdza w grze tablica próbek (rejestr działającej gry). `TrunkShapeTest`: dla każdego `parent` i promienia granice, objętość i kierunek ramienia kształtu, woda, obrót, przepuszczanie światła.
- **Tablica próbek** (`-Pgametest=slice -Psites=samples`, klasa `TrunkSamples`, wywoływana z `SliceClientGameTest`): świat płaski, kamienna platforma, a pierwsze dwie warstwy próbek leżą po obu stronach granicy sekcji (Y −49 | −48): kolumny r1–r7; dla każdego z 6 kierunków `parent` rząd r1–r7 (gospodarz r7 i dwa bloki dziecka promienia r: wysięg poza blok, złącze r → r, wierzchołek); złącza r + 1 → r i kolanka (pień skręca w bok tym samym i cieńszym promieniem); zalane kolumny i gałąź w basenie; gałąź r4 przez granicę chunka x 15 | 16; ręcznie złożona sosna z modelu rury (r² pnia = 1 + Σ r² gałęzi powyżej; dolne 2/3 kora dolna, górna 1/3 „lustro”; gałęzie r2 → r1 w górnej 1/3 ze schodkiem po skosie, suche kikuty niżej, liście świerka wanilii jako zastępstwo do V7; w tej wersji 2 z 7 gałęzi po schodku miały błędnego rodzica i wisiały oderwane, poprawione w rundzie 1 razem z „lustrem” od połowy wysokości i zbieżystością). Zrzuty w południe z gładkim oświetleniem (AO), w nocy przy latarniach, z góry i z dołu pod ostrym kątem (szwy), pod wodą; gracz w trybie przetrwania rozbija ręką pień brzozy r4 (obrys celownika, pęknięcia i cząsteczki uderzeń, potem cząsteczki zniszczenia; łup sprawdzany: `birch_log`). Test sprawdza też, że klient ma model i cząsteczkę kory dla wszystkich 840 stanów i wszystkie bloki tablicy (niżej). Zrzuty: `docs/wycinek/pnie/` (27 bez Sodium, 9 z Sodium).

**Wybór wariantu (R3).** Wariant D zostaje, zapasowy wariant C (model FRAPI czytający sąsiadów) nie jest potrzebny. Z Sodium 0.9.2 (`-Poptimization`, cała paczka; runda 1 V3: z włączonym Nvidium, czysty Sodium w „Poprawka V2b przed CP-A”) zrzuty różnią się od zrzutów bez Sodium na 0,0–0,2% pikseli (próg 40 jednostek jasności; do 0,6% w kadrach z cząsteczkami i ręką): wysięg poza blok, AO, granica sekcji i chunka, woda, obrys i cząsteczki rysują się tak samo.

**Poprawione w trakcie (przed commitem).** (1) Szablon z raportu technicznego (ramię w górę, wysięg wcięty o ε, rdzeń tylko do środka) dawał jasne włoskowate linie: w szczelinie szerokości ε, gdzie wcięta ściana zaczyna się dokładnie tam, gdzie kończy się zewnętrzna, część wierszy pikseli patrzyła przez puste wnętrze pnia na tło. Teraz wewnętrzna ściana zachodzi δ = 0,25 px za każdy taki szew (wysięg w głąb własnego bloku, czapka w dół do ramienia) i linii nie ma (kadry `seams_above`, `seams_below`, `columns`). (Stan commitu c8c089d: runda 1 poprawek zaczęła czapkę w środku bloku i dała ramieniu wierzch, co przywróciło linie na szwie w środku bloku; runda 2 zastąpiła zakładkę czapki ścianami wewnętrznymi, zob. niżej.) (2) Wierzch czapki ze słojami dawał przy złączu r + 1 → r jasny pierścień: teraz kora. (3) W pierwszym przebiegu z Sodium (z C2ME) klient nie dostał części aktualizacji sekcji (kamienna platforma znikła w całych chunkach, pnie nad nią były): tablica porównuje teraz każdy postawiony blok klienta ze stanem serwera i wysyła różnice ponownie; w kolejnych przebiegach klient miał od razu wszystkie 4450 bloków. To wyścig paczki przy blokach stawianych tuż po starcie świata, nie sprawa pni.

**Pomiary.**
- **Stany bloków:** moda 840 (10 bloków × 84), gry z modem 36 627, czyli +2,3% wobec 35 787 bez pni (wanilia 35 723 i inne mody środowiska deweloperskiego). Plan zakładał ok. 10 × 84.
- **Pliki datagenu:** 10 blockstates, 109 modeli (8 szablonów, 91 modeli pni, 10 przedmiotów), 10 tabel łupu, 10 tagów moda i 9 tagów wanilii (bloki i przedmioty); `checkDatagen` czysty (354 pliki).
- **Model:** 14 quadów na blok pnia (pełna kłoda 6); koszt renderu i FPS mierzy V10.

**Odstępstwa.**
- Szablon jest ramieniem w dół, a nie w górę jak w raporcie technicznym (§2.3), ma trzy elementy (ramię, czapka rdzenia, wysięg) i zakładki δ zamiast dwóch elementów; uzasadnienie wyżej. Wierzch czapki ma korę, a słoje tylko koniec wysięgu i przedmiot.
- Bloki pni mają przedmioty (raport dopuszczał pnie bez przedmiotów): gracz może je stawiać w trybie kreatywnym i zbierać jedwabnym dotykiem; identyfikatory przedmiotów są w migawce.
- Klon i jesion dostały nową wspólną teksturę `broadleaf_bark` (w V1 jej nie było); `checkArt` obejmuje teraz 13 tekstur, arkusze `docs/wycinek/grafika/arkusz_8x.png` i `kafle_3x3.png` przeliczone.
- Bez okorowanego pnia (raport: w wycinku opcjonalny) i bez trzeciej tekstury przejściowej kory sosny (V1-i, domyślnie bez strefy; blok można jeszcze dodać przed CP-B, bo migawka nie jest zamrożona).
- Zrzuty tablicy w trybie przetrwania (`breaking`, `destroyed`) mają rozmiar okna (854 × 480), bo przy innym rozmiarze HUD układa się dla okna; pozostałe 1280 × 720.

**Uwagi do CP-A.** Na cienkich gałęziach r1–r2 poprzeczne spękania kory sosny (dolnej i „lustra”) powtarzają się co blok jako ciemne pierścienie („bambus”, kadr `section_boundary`); przy r ≥ 3 tego nie widać. Przy złączu r + 1 → r widać półkę 1 px (planer V6 z modelem rury ma dawać łagodny spadek promienia). Gruby gospodarz r7 z cienkim dzieckiem ma płaski wierzch z korą (w drzewach z planera takie złącza nie występują).

**Przeniesione z rundy 1 poprawek V1.** Przywrócone 4 przypadki autotestu `tools/art/selftest.py` usunięte bez opisu w rundzie 1 (tekstura pod tint nie w szarości, liście poniżej 55% pikseli, piksel półprzezroczysty, za ciemna tekstura pod tint); przechodzą bez zmian w lincie. Opis autotestu w „Stan po V1” (deterministyczność, kafelkowanie, warianty, czy lint łapie błędy) obejmuje je znów w całości.

**Testy.** `RegistrySnapshotTest`, `TrunkShapeTest`, `fastTest`, `checkDatagen` (bez zmian po drugim przebiegu), `python tools/art/check.py` (13 tekstur, lint, `CREDITS.md`, autotest z przywróconymi przypadkami), `runClientGameTest -Pgametest=slice -Psites=samples` bez Sodium (2 przebiegi: przed poprawką szwów i po niej) i z `-Poptimization` (3 przebiegi: z wyścigiem, z błędnym porównaniem liści w nowym sprawdzeniu klienta, PASS), pełny zestaw `tools/dev/run-tests test` (PASS). Złoty test bez zmian (V2 nie zmienia generacji świata).

### Runda 1 poprawek po przeglądzie V2 (2026-10-16)

**Weryfikacja uwag.** Wszystkie 4 uwagi poważne i 11 drobnych potwierdzone (w kodzie, pomiarem albo na zrzutach); fałszywych alarmów nie było. Dwie uwagi o migotaniu z daleka (drobna o ε w `TrunkTemplates` i drobna bez pliku) to ta sama sprawa i mają jedną poprawkę. Próg AO wynika z tego, że ramię jest oświetlane w kontekście swojego bloku (ziemia pod nim zaciemnia), a wysięg w kontekście dziecka; w trybie płaskim (bez AO) znika. „Bambus”: miara pierścieni na dokładnie tych wycinkach, które pokazuje gra (niżej), dawała przed poprawką dla dolnej kory sosny 79 przy r1 i 49–54 przy r2, dla „lustra” 55 i 41 (wanilia bez brzozy: r1 15–35, r2 8–26).

**Model i blok.**
- **Bez AO** (`"ambientocclusion": false` w szablonach `template_trunk_r1…r7`, `docs/00` G.6 V2-j): pień stojący na ziemi nie ma już progu jasności pół bloku nad ziemią (zrzuty `ground`, `columns`); w liściach i przy stopniu terenu też nie (`ground`, `ground_leaves`). Cień styku z ziemią znika razem z progiem. **Decyzja przy CP-A:** zostaje płaskie światło albo wariant C (model FRAPI z bocznymi ścianami całego bloku w jednym kontekście AO, +1–1,5 dnia).
- **Czapka:** wcięta o c = 0,1 px (boki i wierzch, było 2ε = 0,02 px) i zaczyna się w środku bloku; ramię dostało korę na wierzchu (zamyka półkę 0,1 px wokół czapki, inaczej przez nią byłoby widać puste wnętrze); wiersze tekstury biegną bez przerwy (wiersz = 8 − y mod 16 na każdej ścianie, czapka nie jest już ściśnięta). Ściany czapki i wysięgu dziecka są 0,09 px od siebie zamiast 0,01 px: według przeglądu (bufor głębi 24 bity, bliska płaszczyzna 0,05) walczą dopiero od ok. 70 bloków, gdzie tekstura jest już zmipmapowana. 15 quadów na blok (było 14). **Regres** (przegląd rundy 2): wierzch ramienia, oświetlony jak ściana górna, prześwitywał przez szczelinę ε pod wysięgiem dziecka i leżał na półce 0,1 px pod czapką wierzchołka, czyli jasna linia na szwie w środku bloku; poprawione w rundzie 2.
- **Kształt** (`TrunkBlock.shape`, V2-i): graniastosłup promienia przez cały blok wzdłuż osi rodzica (był rdzeń i własna połowa ramienia), dla kolizji, obrysu i promieni. Celownik trafia w każdą widoczną część prostego pnia; wierzchołek i kolanko są o 8 − r px dłuższe niż rysunek. Kształt zależny od sąsiadów odrzucony: kolizja bez właściwości stanu zależałaby od sąsiadów i omijała pamięć kształtów stanu. `TrunkShapeTest` sprawdza nowe granice, objętość (2r)² × 16 px³ i punkty przy obu ścianach osi.
- **Tagi** (V2-d): przedmioty wszystkich gatunków tylko w `#logs_that_burn` (paliwo, węgiel drzewny) i w nowym tagu przedmiotów `#polishforests:trunks`; z wanilijnych tagów przedmiotów `#oak_logs`, `#birch_logs`, `#spruce_logs` wyszły (gałązka r1 nie daje już 4 desek). Tagi bloków bez zmian. Nazwy tagów przedmiotów w lang EN i PL (`tag.item.polishforests.<gatunek>_logs` jak nazwa pnia w liczbie mnogiej, `trunks`: „Thin Trunks” / „Cienkie pnie”).

**Kora** (`tools/art`, V2-k):
- **Lint pierścieni** (`texlib.ring_drop`, `texlib.rings`, reguła w `lint.py`): na każdym z 4 wycinków każdego promienia 1–7 (`texlib.TRUNK_OFFSETS`, kopia `TrunkTemplates.OFFSETS`; `check.py` sprawdza, że są równe) największy skok jednego wiersza (ciemniejszego albo jaśniejszego) względem mediany 3 wierszy powyżej i 3 poniżej; progi `RING_CAPS`: 32 przy r1, 26 wyżej. Miara pasów na wycinkach pni (`texlib.banding`) też liczy się teraz na wycinkach z gry (było: środkowe wycinki r = 2…5); próg 9 (wanilia na wycinkach z gry 3,4–11,5: `oak_log` 7,4, `pale_oak_log` i `cherry_log` 8,4, `jungle_log` 11,5; był 8 na wycinkach środkowych). Wybór ziarna (`bark_score`) bierze pierścienie i każdą inną regułę lintu tekstury. Autotest: nowy przypadek ciemnej poprzecznej bruzdy na ścianie pnia r1. Arkusz `pnie_cienkie.png` pokazuje r = 1, 2, 4 z 4 ścianami w wycinkach z gry.
- **„Lustro”** od nowa (`upper_cells`, `paint_upper`, `upper_colors`): 22 łuski Worleya w 3 tonach o zbliżonej jasności (luminancja 112, 117, 122), różnych w odcieniu (rudoczerwony −5°, pomarańczowy, ochra +8°), rozdawanych losowo w proporcjach 0,3 / 0,4 / 0,3; pod częścią dolnej krawędzi łuski krótka pozioma szczelina 2–3 px (najwyżej 2 piksele w rzędzie, 12 w całej teksturze, nigdy jedna nad drugą), jasna zawinięta krawędź nad 60% z nich, 5 bladych szaropomarańczowych łusek 2–3 px. Najczęstszy kolor ma 34% pikseli (było 80%), 6 kolorów, odcień 22°, odchylenie luminancji 10 (wanilijne kłody 9–21). Drugi wariant `scots_pine_upper_bark_2` (łuski bazy w 3 skrajnych rzędach, rzędy skrajne identyczne); `scots_pine_upper_trunk` losuje oba.
- **Dolna kora sosny:** końce płyt ukośne (`cross='diag'`) i w tonie ściany bruzdy (płytsze niż długie bruzdy, `cross_tone`); pierścienie r1 / r2: 23 / 23 i 29 / 20 (było 79 / 49–54).
- **Buk** chłodniejszy i równiejszy: nasycenie 0,11 (było 0,13), węższa rampa jasności 136–194, glony na 6% zamiast 12% pikseli i słabsze, mocniejsza pionowa smuga (`streak` 0,5); L* 68, ΔE 8,2 od gładkiego kamienia (reguła „kamienia” ≥ 8 nie pozwala przy tej jasności zejść niżej z nasyceniem). **Grab** oliwkowoszary (odcień 80°, nasycenie 0,07; był niebieskoszary 206°), smugi bez zmian. **Lipa** szarsza (nasycenie 0,2, było 0,3; ΔE 11,1 od dębu, 9,3 od `broadleaf_bark`) i trzeci wariant `linden_bark_3`.
- Wybór przez pomiar z nowymi miarami zmienił też wybrane ziarna dębu, `broadleaf_bark` i wariantów (przepisy bez zmian). Drugi wariant dębu dostał 200 prób (reszta 48–96). Generacja 15 tekstur trwa ok. 24 s (było ok. 1,6 s), `python tools/art/check.py` ok. 70 s.

**Tablica próbek** (`TrunkSamples`):
- **sosna:** usunięty błąd rodzica po wspięciu gałęzi (2 z 7 gałęzi wisiały oderwane); „lustro” od połowy wysokości (`MIRROR_START` 0,5, rude od 12. z 22 bloków), zbieżystość poniżej nasady korony (`TAPER`: +1 px² przekroju na blok poniżej pierwszej gałęzi; promień 6 na dole, 5 od 8. bloku, 4 od 15.). Plan V6 dostał oba parametry (lustro od 0,45–0,55 wysokości ze strefą przejściową, zbieżystość); sprzeczność z kryterium CP-A („rude lustro w górnej części pnia”) zniknęła;
- **nowe miejsca i kadry:** pnie r3–r6 brzozy, grabu, buka i sosny na trawie, dąb przy stopniu terenu i lipa na stopniu, grab z gałęzią w liściach (`ground`, `ground_leaves`); przedmioty (pasek 9 pni, dąb w ręce, 3 na ziemi; `items`); kikut pnia nad rozbitym blokiem z dołu (`stub`); widoki z 48 i 96 bloków przez pole widzenia 30° na kolanka i na zmianę kory sosny (`far_joints_*`, `far_pine_*`), każdy drugi raz z kamerą przesuniętą o 0,1 bloku (te drugie klatki zostają w katalogu przebiegu);
- **test celownika:** gracz w trybie przetrwania celuje w wysokości 2/16, 12/16 i 15/16 drugiego bloku kolumn r1 i r4; `hitResult` musi być tym blokiem (przy starym kształcie 12/16 i 15/16 leżały poza kształtem);
- kamera `chunk_boundary` z południa (stała w rzędzie złączy), `gamerule send_command_feedback false` (bez napisu o trybie gry na zrzutach przetrwania), toasty (nowe przepisy na węgiel drzewny) czyszczone przed zrzutem przedmiotów;
- **światło i siatki klienta:** w jednym przebiegu bez Sodium klient miał w sekcji nad granicą światło bloków 0 (pnie nocą ciemne, podłoga przy latarniach jasna), a w jednym przebiegu z Sodium nie było siatki platformy w całych chunkach, choć klient miał bloki. Tablica porównuje teraz także światło bloków i nieba każdej pozycji z serwerem i wysyła światło różniących się chunków ponownie (w ostatnich przebiegach po 1 razie: 158–473 pozycje w 3–4 chunkach, potem zgodne), a potem przebudowuje wszystkie siatki (`levelExtractor.allChanged()`). To wyścig przy blokach stawianych tuż po starcie świata, nie sprawa pni.

**Pomiary.**
- Zrzuty Sodium wobec wanilii (ostatnia para przebiegów, próg 40): 0,01–0,14% pikseli w kadrach statycznych, 0,17–0,25% w widokach z 96 bloków, 0,44–0,78% w kadrach z cząsteczkami, ręką i animowaną wodą.
- Widoki z daleka: dwie klatki z kamerą przesuniętą o 0,1 bloku różnią się na 0,45–1,6% pikseli, ale to paralaksa krawędzi (cała scena się przesuwa); na 2-krotnych powiększeniach kolanek i złączy z 48 bloków nie widać migotania ani prążków. Testu z ruchem kamery (wideo) nie ma.
- Stany bloków bez zmian (840 moda, 36 627 gry), migawka rejestru bez zmian. 15 quadów na blok pnia.
- Tablica: 4733 bloki (było 4450), przebieg ok. 4 min.

**Odstępstwa.**
- Próg AO usunięty wyłączeniem AO (podpowiedź (a) przeglądu), nie wariantem C; decyzja przy CP-A.
- Kolizja też jest graniastosłupem przez cały blok (przegląd dopuszczał zostawienie starej kolizji): to, co obrysowane, jest twarde, a strzały nie przelatują przez szczeliny między segmentami.
- Czapka zamiast podpowiedzianego „obniżenia o 0,05–0,1 px” jest wcięta o 0,1 px z boków i od góry, ma wiersze tekstury bez ściśnięcia, a ramię dostało wierzch.
- Widoki z daleka bez ruchu kamery: dwie klatki z przesunięciem i ocena powiększeń.
- Na zrzucie `items` widać obrys bloku podłogi, w który patrzy gracz (zwykły HUD).

**Do CP-A** (obok „Uwag do CP-A” wyżej, które przy „bambusie” są już nieaktualne): V2-j (płaskie światło pni albo wariant C), wygląd nowego „lustra” i kory po zmianach (V2-k; `pnie_cienkie.png`, zrzuty `section_boundary`, `pine`, `columns`, `ground`), strefa przejściowa sosny (V1-i, teraz przy „lustrze” od połowy wysokości). Półka 1 px przy złączu r + 1 → r zostaje do planera V6 (zbieżystość rozkłada takie półki wzdłuż pnia).

**Testy rundy 1.** `fastTest` (z `TrunkShapeTest` i `RegistrySnapshotTest`), `checkDatagen`, `checkArt` i `python tools/art/check.py` (15 tekstur, lint z pierścieniami, `CREDITS.md`, autotest z nowym przypadkiem, zgodność `TRUNK_OFFSETS`), `runClientGameTest -Pgametest=slice -Psites=samples`: bez Sodium 4 przebiegi (PASS; drugi z ciemnymi pniami nocą przed synchronizacją światła), z `-Poptimization` 3 przebiegi (PASS; drugi bez siatki platformy przed przebudową siatek); w każdym: rejestr równy migawce, modele i cząsteczki 840 stanów, test celownika, łup `birch_log`; pełny zestaw `tools/dev/run-tests test` (PASS, 1205 s, na drzewie commitu 6664b32, różniącym się od końcowego tylko tym zdaniem). Złoty test terenu bez zmian (runda nie zmienia generacji świata).

### Runda 2 poprawek po przeglądzie V2 (2026-10-16)

**Weryfikacja uwag.**
- **Poważna (linie na szwie w środku bloku): potwierdzona.** Na zrzutach rundy 1 jasna linia 1 px na y = 8 bloku (lipa r7 i grab r6 w `seams_above`, lipa r7 w `columns`, `reach_down_thick`), z Sodium i bez; w c8c089d jej nie było. Przyczyna jak w uwadze: od rundy 1 czapka zaczynała się w y = 8, a ramię miało wierzch oświetlony jak ściana górna (1,0 wobec 0,8 i 0,6 ścian bocznych); przez szczelinę ε między ramieniem a wysięgiem dziecka i na półce 0,1 px pod czapką wierzchołka widać było ten wierzch. Nowy test promieni (niżej) pokazuje to samo na modelach rundy 1: wierzch ramienia na idealnej ścianie bocznej w 0,10–0,18% promieni kolumn i 0,18–0,35% promieni złączy r + 1 → r.
- **Drobne (10): nieaktualne, wszystkie poprawione już w rundzie 1** (opisują stan c8c089d i jego numery wierszy): błąd rodzica sosny po wspięciu (tego wiersza już nie ma, rodzic gałęzi zostaje `br.dir().getOpposite()`), migotanie z daleka (czapka wcięta o 0,1 px, widoki `far_*` z 48 i 96 bloków z klatką przesuniętą), V2-d (przedmioty pni tylko w `#logs_that_burn` i `#polishforests:trunks`), tag przedmiotów `trunks` (jest: `copy(TRUNKS, item(TRUNKS))`), „lustro” od połowy wysokości (`MIRROR_START` 0,5, plan V6), zbieżystość (`TAPER`), drugie migotanie bez pliku (jak wyżej), czytelność kory buka, grabu i lipy (V2-k, `linden_bark_3`), nazwy tagów przedmiotów w lang (`tag.item.polishforests.*`, EN i PL), kadry tablicy (kamera `chunk_boundary` z południa, bez napisu czatu, nowe kadry `items`, `stub`, `far_*`, `ground*`). Sprawdzone w kodzie tej rundy; nic nie trzeba było zmieniać.

**Model** (`TrunkTemplates`, szablony `template_trunk_r1…r7`, V2-e):
- **ramię** bez wierzchu (4 ściany, wiersze 0–8);
- **ściany wewnętrzne:** 4 płaskie ściany c = 0,1 px wewnątrz ramienia, szerokie jak wysięg (od a + ε do b − ε), od 0 do 8, z kolumnami i wierszami tekstury jak na ramieniu. Przez szczelinę ε pod wysięgiem dziecka i przez półkę pod czapką widać teraz korę oświetloną jak ściana wokół. Przegląd podpowiadał „fartuch” 0,25 px pod czapką; ściany na całą wysokość ramienia łapią promienie do ok. 89° z góry, a fartuch tylko do ok. 68°;
- **czapka** z płaskich ścian: 4 boki c wewnątrz promienia, szerokie jak wysięg, od 8 do wierzchu wysięgu dziecka z boku (8 + r − ε, wiersze 16 − r + ε…16), i wierzch z kory c poniżej promienia (8 + r − c), też szeroki jak wysięg. Boki sięgające do marginesu ε zasłaniają koniec wysięgu dziecka z boku (słoje), który przy kolanku wystawał 0,09 px za czapkę: w rundzie 1 widać go było na wprost jako pasek słojów na tylnej stronie kolanka;
- wysięg bez zmian (tylko kolumny tekstury wyrównane do ramienia, było lekkie ściśnięcie o 2ε);
- **18 quadów na blok** (było 15); stany, blockstates i migawka rejestru bez zmian.

**Test promieni** (`block/TrunkModelRayTest`, w `fastTest`, ok. 9–12 s). Czyta wygenerowane szablony i składa sceny z dwóch bloków dla wszystkich promieni: kolumna r → r, złącze r + 1 → r, kolanko r → r i r → r − 1. Strzela promieniami w punkty powierzchni idealnego pnia (graniastosłupa promienia), gęsto przy krawędziach ścian modelu, z 64 kierunków (0,5–75° od normalnej), i porównuje pierwszą przednią ścianę modelu z wejściem w idealny pień:
- na płaskiej części idealnej powierzchni (dalej niż 0,4 px od jej krawędzi) promień musi trafić w model (bez prześwitu), najwyżej 1,5 px od wejścia, w ścianę tego samego kierunku, czyli z tym samym cieniowaniem kierunkowym (jasna linia z przeglądu to ściana górna w ścianie bocznej);
- słoje mogą się pokazać tylko na odsłoniętym końcu wysięgu (kikucie);
- promienie, które nie wchodzą głębiej niż 0,25 px albo wchodzą pod kątem ponad 80° do ściany, tylko muskają sylwetkę i nie są sprawdzane.

Kolumny i złącza muszą być bez błędów; kolanko tych samych promieni ma dwie znane wady (niżej), każdą z limitem 0,2% promieni.

| Model | kolumny | złącza r + 1 → r | kolanka r → r − 1 | kolanka r → r |
|---|---|---|---|---|
| c8c089d (czapka 2ε od 8 − δ) | 0 | 0 | 0 | 0,04–0,05% |
| runda 1 (0efdf26) | 0,10–0,18% | 0,18–0,35% | 0,15–0,18% | 0,37–0,48% |
| runda 2 | 0 | 0 | 0 | 0,16–0,20% |

**Znane wady kolanka tych samych promieni** (V2b: słoje zastąpione paskiem kory, czapka jako prostopadłościan; runda 1 poprawek V2b: kora 0,1 px za słojami; niżej) (pień skręca w bok bez zmiany promienia: wspięcie gałęzi, skręt przewodnika). Czapka leży 0,1 px pod bokami wysięgu dziecka, żeby nie walczyć z nimi w buforze głębi. Na stronie odwróconej od dziecka zostaje więc stopień 0,09 px, przez który pod skosem widać pasek końca wysięgu (słoje): na zrzutach `joints_back` i `joints` to pojedyncze brązowe piksele na krawędzi wierzchu kolanka. Do tego dochodzi szczelina ε wzdłuż złącza gałęzi z ramieniem (widać przez nią ścianę ramienia w poprzek gałęzi). W c8c089d (czapka 0,02 px) było tego 4 razy mniej, ale tamte ściany, 0,01 px od siebie, walczyłyby w buforze głębi od ok. 25 bloków. Możliwości do CP-A (`docs/00` G.6 V2-l):
- zostawić tak (domyślnie);
- obramować słoje paskiem kory w tej samej płaszczyźnie: słoje wcięte o c, 4 paski kory w pierścieniu; +4 quady (22 na blok), kikut dostaje korowy brzeg;
- mniejsze wcięcie c, np. 0,05 px: o połowę mniej tych pikseli, ale walka w buforze głębi od ok. 46 bloków.

**Tablica próbek:** nowy kadr `joints_back` (kolanko r4 z bliska, od strony odwróconej od gałęzi). Automatycznego sprawdzenia linii na zrzutach nie ma: tę rolę pełni test promieni (deterministyczny, bez gry). Prosty detektor jasnych poziomych odcinków (co najmniej 5 pikseli jaśniejszych o ponad 20 od pikseli nad i pod) łapie na zrzutach z gry także fakturę kory, więc nie nadaje się na próg testu; niżej służy tylko jako pomiar.

**Pomiary.**
- **Zrzuty:** linie z przeglądu na szwie w środku bloku znikły w `seams_above`, `columns` i `reach_down_thick`, z Sodium i bez (powiększenia porównane z rundą 1). Nie znikła kropkowana linia na wypukłych krawędziach czapki (wierzchołek, półka r + 1 → r, kolanko), którą dawały płetwy nowej czapki z płaskich ścian; test promieni jej nie widział (pomija punkty do 0,4 px od krawędzi). Usunięta w V2b (niżej).
- **Detektor jasnych odcinków** (piksele w odcinkach, bez Sodium; c8c089d / runda 1 / runda 2): `seams_above` 32 / 132 / 15, `columns` 53 / 69 / 37, `reach_down_thick` 63 / 161 / 66, `reach_north_thick` 36 / 58 / 17, `section_boundary` 64 / 114 / 69. Z Sodium (runda 1 → runda 2): `seams_above` 173 → 69, `columns` 136 → 111. Resztę daje faktura kory; kora zmieniła się w rundzie 1, więc z c8c089d porównanie jest tylko przybliżone.
- **Sodium wobec wanilii** (próg 40): 0,01–0,13% pikseli w kadrach statycznych, 0,17–0,25% w widokach z 96 bloków, 0,35–0,57% w kadrach z cząsteczkami, ręką i animowaną wodą.
- **Widoki z daleka** (klatka przesunięta o 0,1 bloku): różnica 0,45–1,62% pikseli bez Sodium i 0,50–2,01% z Sodium, jak w rundzie 1 (paralaksa).
- **Model:** 18 quadów na blok pnia (pełna kłoda 6); koszt mierzy V10.

**Odstępstwa.**
- Zamiast fartucha 0,25 px pod czapką są ściany wewnętrzne na całą wysokość ramienia.
- Czapka i ściany wewnętrzne są z płaskich elementów szerokich jak wysięg (to zasłania pasek słojów kolanka oglądany na wprost), a wierzch czapki to osobny płaski element.
- Kolanko tych samych promieni ma resztę wady (wyżej); test ją mierzy i ogranicza, a nie zeruje.

**Testy rundy 2.**
- `TrunkModelRayTest`: PASS. Na modelach rundy 1 FAIL; na modelach c8c089d błędy tylko w kolankach.
- `fastTest`, `checkDatagen`.
- `runClientGameTest -Pgametest=slice -Psites=samples` bez Sodium i z `-Poptimization`, po 1 przebiegu, PASS: rejestr równy migawce, modele i cząsteczki 840 stanów, wszystkie 4733 bloki u klienta, światło zgodne po 1 ponownym wysłaniu, test celownika, łup `birch_log`.
- Pełny zestaw `tools/dev/run-tests test`: PASS (1205 s, na drzewie commitu 7241637, różniącym się od końcowego tylko tym zdaniem).
- Złoty test terenu bez zmian (runda nie zmienia generacji świata).

### Poprawka V2b przed CP-A (2026-10-16)

**Wada (otwarta z przeglądu rundy 2 V2, opis w „Stan po V5”).** Czapka rundy 2 była z płaskich ścian sięgających marginesu ε: boki leżały c = 0,1 px wewnątrz promienia, ale były szerokie jak wysięg (od a + ε do b − ε) i sięgały do 8 + r − ε, a wierzch leżał na 8 + r − c i też był szeroki jak wysięg. Każda ściana wystawała więc ok. 0,09 px poza ścianę prostopadłą („płetwy”), a na każdej wypukłej krawędzi czapki (koniec pędu, półka r + 1 → r, zewnętrzna strona kolanka) promienie widziały kolejno bok, wierzch, bok, wierzch. Na zrzutach to kropkowana jasna linia 1 px wzdłuż krawędzi, w wanilii, w czystym Sodium i z Nvidium (kadry `v2b_*.png`, lewa kolumna). Zdania rundy 2 o „końcu jasnej linii” dotyczyły tylko linii na szwie w środku bloku (ta rzeczywiście znikła); linia na krawędziach wypukłych została aż do V2b.

**Model** (`TrunkTemplates`, szablony `template_trunk_r1…r7`):
- **czapka** jako jeden prostopadłościan [a + c, 8, a + c]–[b − c, 8 + r − c, b − c]: 4 boki (wiersze 16 − r + c…16, kolumny jak na ramieniu) i wierzch z kory; krawędzie wypukłe są czyste (wskazówka przeglądu);
- **koniec wysięgu** (V2-l (b)): słoje tylko na [a + c, b − c]², wokół nich 4 paski kory w tej samej płaszczyźnie (od marginesu ε do c). Przy kolanku tych samych promieni koniec wysięgu dziecka wystaje 0,09 px poza czapkę z trzech stron (wierzch i oba boki), tak samo jak w rundzie 2: boki czapki rundy 2 też leżały na a + c, a pasek słojów widać było także na bokach (te same liczby promieni na każdej stronie, sprawdzone w przeglądzie V2b; słaba jasna pionowa linia w lewej kolumnie `v2b_kolanko.png`). V2b zmienia tylko teksturę paska na korę, nie zmniejsza go; słojów tam już nie widać. (Pierwsza wersja tego akapitu twierdziła błędnie, że wcześniej pasek wystawał tylko od góry.) Kikut (pień nad rozbitym blokiem) ma słoje z brzegiem kory 0,09 px;
- ramię, ściany wewnętrzne i boki wysięgu bez zmian;
- **22 quady na blok** (po rundzie 1 poprawek V2b 27, niżej; było 18: czapka 5 jak wcześniej, koniec wysięgu 5 zamiast 1); pełna kłoda 6. Stany, blockstates, modele bloków i migawka rejestru bez zmian (`RegistrySnapshotTest`, tablica w grze: „registry equals the snapshot”, 840 stanów moda, 36 627 gry); datagen zmienia tylko 7 szablonów.

**Test promieni** (`TrunkModelRayTest`):
- nowa scena **samotnego końca pędu** (`tip r1…r7`: jeden blok, wierzchołek i kikut wysięgu) obok kolumn, złączy r + 1 → r (półka) i kolanek;
- **skan krawędzi wypukłych**: wzdłuż każdej krawędzi idealnego graniastosłupa, która jest wypukła i czysta (obie ściany są powierzchnią idealną do 0,65 px od krawędzi i 0,4 px wzdłuż niej, z dala od nieciągłości modelu), równoległe promienie co 0,002 px w poprzek krawędzi (0,6 px na każdej ścianie), z 5 kierunków między normalnymi (10–80°) i 3 pochyleń wzdłuż krawędzi. Trafiane ściany muszą mieć jeden z dwóch kierunków krawędzi i zmienić się raz. Promienie zasłonięte inną częścią idealnego pnia i trafiające model dalej niż 0,7 px od krawędzi (przez wcięcie c pod skosem widać np. dziecko wyrastające z półki 1 px dalej) są pomijane;
- kolanko tych samych promieni nie może już pokazywać słojów; każda z dwóch znanych wad (pasek końca wysięgu w stopniu, szczelina ε wzdłuż złącza) najwyżej 0,1% promieni (było 0,2%); w V2b próg dotyczył osobno każdej ściany i kierunku, a filtr przepuszczał też słoje na płaskiej ścianie (poprawione w rundzie 1 poprawek V2b, niżej).

| Model | skany krawędzi z błędem | kolanka r → r (promienie) |
|---|---|---|
| runda 2 V2 | 300–1020 na scenę we wszystkich 33 scenach (np. `tip r1`: „up, north, up, north” w 75 z 1380 skanów) | 0,16–0,20% (pasek słojów i szczelina ε) |
| V2b | 0 | 0,05–0,10% (pasek kory w 3 stopniach 0,01–0,04% każdy, szczelina ε 0,003%); spadek wobec rundy 2 tylko dlatego, że pasek kory nie jest `#end`, więc promienie w strefach przy krawędziach (dawniej „słoje poza kikutem”, ok. 4100 na scenę) przestały się liczyć. Promieni trafiających w pasek jest dokładnie tyle samo co w rundzie 2 (przegląd V2b) |

Test pada więc na starych szablonach i przechodzi na nowych (ok. 15 s, w `fastTest`).

**Tablica próbek.** Nowe zbliżenia z ok. 1 bloku: `cap_edge` (wierzchołek kolumny r6 z północnego zachodu), `ledge_edge` (półka r5 → r4 z południowego zachodu), `elbow_edge` (kolanko r4 z gałęzią na wschód, od południowego zachodu). Tablica rozpoznaje renderer jak próby renderu V3: zrzuty `samples_<vanilla|sodium|sodium_nvidium>_*` (dawniej `samples_sodium_*` także dla Nvidium). Przebiegi przed poprawką (z nowymi kadrami) i po niej w trzech rendererach (wanilia, `-Poptimization -PnoNvidium`, `-Poptimization` na RTX 4060), wszystkie 6 PASS: rejestr równy migawce, modele i cząsteczki 840 stanów, wszystkie 4733 bloki u klienta, test celownika, łup `birch_log`.

**Zrzuty** (`docs/wycinek/pnie/`):
- przed i po, powiększenia 3×, wiersze: wanilia, czysty Sodium, Sodium + Nvidium: `v2b_krawedz_czapki.png`, `v2b_polka.png`, `v2b_kolanko.png`. Kropkowana linia na krawędziach wierzchołka i półki znikła we wszystkich trzech rendererach. Na kolanku znikła linia wzdłuż krawędzi czapki; w stopniu 0,09 px przy końcu wysięgu gałęzi zostaje z bliska cienka linia: przed poprawką jasna (słoje i płetwa), teraz kora końca wysięgu. Na wierzchu linia jest ciemniejsza (pasek 0,6 albo 0,8 w wierzchu 1,0), na bokach zależnie od osi gałęzi: przy gałęzi wschód–zachód (kadr `elbow_edge`) ciemniejsza (pasek cieniowany jak ściana wschód–zachód, 0,6 w ścianie południowej 0,8), przy gałęzi północ–południe jaśniejsza (0,8 w ścianie 0,6). To ta sama wada kolanka tych samych promieni (V2-l) o tej samej wielkości co w rundzie 2, tylko z inną teksturą; na zrzutach `joints_back` i `joints` z 2–3 bloków prawie jej nie widać;
- po poprawce: `samples_<vanilla|sodium|sodium_nvidium>_{cap_edge,ledge_edge,elbow_edge}.png`, a `samples_vanilla_{seams_above,joints,joints_back,stub}.png` przeliczone (stare pliki `samples_sodium_*.png` w tym katalogu są z Sodium + Nvidium, z rundy 2 V2).

**Pomiary.**
- Zrzuty po poprawce wobec wanilii (próg 40 jednostek jasności): czysty Sodium 0,00–0,15% pikseli w kadrach statycznych i z daleka, 0,15–0,63% w kadrach z cząsteczkami, ręką i przedmiotami; Sodium + Nvidium 0,00–0,15% i 0,15–0,65%.
- Wanilia po poprawce wobec przed: 0,00–0,14% pikseli w kadrach pni (krawędzie czapek), do 0,66% w kadrach z cząsteczkami.
- Widoki z 48 i 96 bloków bez zmian (0,00–0,01% wobec przebiegu przed poprawką): czapka leży jak dotąd 0,1 px pod ścianami wysięgu dziecka, więc ryzyko walki w buforze głębi się nie zmienia.

**Czysty Sodium w tablicy V2 (punkt kontrolny CP-A).** Przebiegi „z Sodium” w V2 i obu rundach poprawek (`-Poptimization`) były w rzeczywistości Sodium + Nvidium (runda 1 V3). Przy przygotowaniu CP-A tablica przeszła też w czystym Sodium 0.9.2 bez Nvidium (`-Poptimization -PnoNvidium`, model rundy 2): **PASS** (rejestr, modele i cząsteczki 840 stanów, bloki, światło, celownik, łup), zrzuty różnią się od wanilii na 0,01–0,66% pikseli (próg 40; najwięcej w kadrach z cząsteczkami zniszczenia i ręką). Wysięg poza blok, granica sekcji i chunka, woda, obrys i cząsteczki rysują się jak w wanilii. Wnioski „z Sodium” z V2 (R3: wariant D zostaje) dotyczą więc obu rendererów; w V2b trzy renderery dały ten sam obraz (wyżej).

**Odstępstwa.**
- V2-l (b) przyjęta bez osobnej decyzji (wskazówka przeglądu); przy CP-A można wrócić do (a) albo (c). (a) to dziś słoje na całym końcu wysięgu przy czapce jako prostopadłościanie (płetw nie przywraca, w stopniu znów widać słoje); pierwotne zdanie „(a) przywraca płetwy” dotyczyło czapki szerokiej jak wysięg.
- Ściany wewnętrzne zostają płaskie i szerokie jak wysięg (ich krawędzie leżą wewnątrz ramienia i widać je tylko przez półkę 0,1 px i szczelinę ε).
- Zrzuty przed poprawką zrobione nową wersją tablicy (z kadrami zbliżeń) na szablonach rundy 2, nie wzięte z przeglądu.

**Testy V2b.** `TrunkModelRayTest` (FAIL na szablonach rundy 2, PASS na nowych), `RegistrySnapshotTest`, `TrunkShapeTest`, `fastTest`, `checkDatagen`, tablica próbek w 3 rendererach przed i po (6 × PASS), arkusze `python tools/art/sheet.py` (niżej), pełny zestaw `tools/dev/run-tests test` (PASS, 1520 s, na drzewie commitu 6ee6e7c, różniącym się od końcowego tylko tym zdaniem). Złoty test terenu bez zmian (V2b nie zmienia generacji świata).

**Arkusze tekstur** (`docs/wycinek/grafika/`) przeliczone z bieżących przepisów (`python tools/art/sheet.py`): `arkusz_8x.png` i `kafle_3x3.png` miały jeszcze kafel `broadleaf_leaves` sprzed poprawki V3-g (z pionową szczeliną); pozostałe arkusze bez zmian w pikselach.

### Runda 1 poprawek po przeglądzie V2b (2026-10-16)

**Prześwity w ścianach pni (T-złącza, regres V2b): naprawione.** Przegląd znalazł w tablicy próbek z dodatkowymi zbliżeniami pojedyncze piksele nieba albo podłoża wewnątrz pni, na liniach stopnia 0,1 px między ramieniem a czapką. Potwierdzone: czapka-prostopadłościan ma dolną krawędź boków od a + c do b − c, a górna krawędź ściany wewnętrznej w tej samej płaszczyźnie biegła od a + ε do b − ε. Narożnik jednego quada leżał więc we wnętrzu krawędzi drugiego (T-złącze) i rasteryzacja zostawiała na szwie pojedyncze niepokryte piksele. To samo działo się na końcu wysięgu: słoje [a + c, b − c]² stykały się z paskami ramki biegnącymi od a + ε do b − ε. Nowa kontrola `TrunkModelRayTest.coplanarFacesMeetAtCorners` (bez promieni) liczy na szablonach V2b 16 T-złączy na blok (944 w 33 scenach), na szablonach rundy 2 0, a po poprawce 0. Test promieni ich nie widział, bo liczy przecięcia analitycznie.

**Model** (`TrunkTemplates`, szablony `template_trunk_r1…r7`):
- każda ściana wewnętrzna to 3 quady, podzielone na a + c i b − c (wskazówka przeglądu), więc dolne narożniki boków czapki są wierzchołkami ściany pod nimi;
- koniec wysięgu to 2 quady zamiast 5: słoje [a + c, b − c]² na końcu i kora szeroka jak wysięg 0,1 px za nimi (`END_BARK_DEPTH`). Nie leżą w jednej płaszczyźnie, więc nie ma T-złączy. 9 pól bez T-złączy (wskazówka przeglądu) kosztowałoby 7 quadów więcej. Sprawdzony był też wariant odwrotny, słoje 0,1 px przed korą: słoje wystawały wtedy poza kikut (w teście promieni „deep hit” na kolanku r7), więc odpadł. Kora i słoje leżą 0,1 px od siebie, tak jak czapka od wysięgu dziecka (bez walki w buforze głębi do ok. 70 bloków);
- **27 quadów na blok** (runda 2: 18, V2b: 22): ramię 4, ściany wewnętrzne 12, czapka 5, wysięg 4, koniec 2. Stany, blockstates i migawka rejestru bez zmian; datagen zmienia tylko 7 szablonów.

**Kolanko tych samych promieni (V2-l).** Kora końca leży teraz 0,1 px za otworem stopnia, więc strome promienie widzą przez otwór wierzch czapki, a nie korę końca. W teście promieni wada paska spada do 0,005–0,018% promieni (suma wierzchu i obu boków). Ten sam test na szablonach V2b daje 0,04–0,10%, a na szablonach rundy 2 0,15–0,19% (słoje). Na zbliżeniu `elbow_edge` linia w stopniu jest przerywana zamiast ciągłej (`v2b_kolanko.png`, trzecia kolumna). Sam stopień 0,09 px i jego odsłonięcie z trzech stron się nie zmieniły.

**Test** (`TrunkModelRayTest`):
- nowy test `coplanarFacesMeetAtCorners`: ściany modelu w jednej płaszczyźnie i o tym samym kierunku (w bloku i między blokami każdej sceny) stykają się tylko narożnikami i nie nakładają się;
- słoje są sprawdzane przed kierunkiem ściany. Wcześniej słoje na płaskiej ścianie innego kierunku dawały komunikat „face …” i przechodziły przez filtr znanej wady kolanka; filtr wymaga teraz kory końca (`down #bark`), a etykiety quadów podają teksturę;
- znane wady kolanka są liczone jako suma po ścianach: pasek końca wysięgu (wierzch i oba boki) najwyżej 0,03%, szczelina ε (oba boki) najwyżej 0,01%. Wcześniej próg 0,1% obowiązywał osobno dla każdego komunikatu, więc pasek mógł mieć łącznie do 0,3%;
- wiersz sceny wypisuje udział każdego rodzaju błędu;
- bez zmian: skan krawędzi wypukłych pomija promienie, które nie trafiają modelu, a główna kontrola pomija strefy 0,4 px przy krawędziach, więc prześwitu tuż przy krawędzi wypukłej nie wykrywa żadna z nich. To sprawdza tablica w grze (niżej).

| Szablony | T-złącza (33 sceny) | skany krawędzi z błędem | pasek końca wysięgu na kolanku r → r | szczelina ε |
|---|---|---|---|---|
| runda 2 V2 (668ec6f) | 0 | 300–1020 na scenę | 0,15–0,19% (słoje) | 0,005% |
| V2b | 944 | 0 | 0,04–0,10% (kora) | 0,005% |
| runda 1 poprawek V2b | 0 | 0 | 0,005–0,018% | 0,005% |

**Tablica w grze.** Tablica próbek w trzech rendererach (wanilia, `-Poptimization -PnoNvidium`, `-Poptimization` na RTX 4060) z 112 dodatkowymi zbliżeniami przeglądu (dodanymi tylko na czas przebiegu, poza repozytorium): 3 × PASS. Izolowane piksele nieba (żaden z 8 sąsiadów nie jest niebem) w 155 kadrach wanilii: runda 2 4330, V2b 4348, teraz 4330 (to głównie liście i woda). Wszystkie dziury T-złączy (`chunk_boundary`, `rv_ledge_r2_se/sw`, `rv_etip_r4_*`, `rv_stub_*`, `rv_tip_r7_ne`, `seams_below`) zniknęły w wanilii i w czystym Sodium. Wobec rundy 2 zniknęło 15 izolowanych pikseli (w tym 8 na pniach) i doszło 15; z nowych 6 leży na pniach, wszystkie w miejscach, które pokazywało już V2b (`elbow_edge` 2, `rv_elbow_r6_s` 2, `rv_tip_r3_nw`, `rv_tipclose_r4_nw`), a reszta to kadry z ręką i cząsteczkami, dalekie kadry i woda (te same różnice daje porównanie V2b z rundą 2). Zrzuty `docs/wycinek/pnie/v2b_r1_dziury.png` (runda 2, V2b, teraz; ostatni wiersz to dziura, która zostaje). Zrzuty po poprawce wobec wanilii: czysty Sodium 0,00–0,54% pikseli, Sodium + Nvidium 0,00–0,58% (próg 40); wanilia wobec V2b: do 0,02% w kadrach pni (`elbow_edge`), widoki z 48 i 96 bloków bez zmian. Zrzuty `samples_<vanilla|sodium|sodium_nvidium>_{cap_edge,ledge_edge,elbow_edge}.png` i `samples_vanilla_{seams_above,seams_below,joints,joints_back,stub,chunk_boundary}.png` przeliczone, arkusze `v2b_*.png` mają trzecią kolumnę.

**Zostaje: dziury w narożnikach czapki.** 6 pikseli w 155 kadrach, w każdym rendererze w tych samych miejscach (z Nvidium 4), na końcach linii stopnia, w narożnikach czapki. Czapka-prostopadłościan zostawia nad środkiem bloku w każdym narożniku pionową szczelinę 0,1 × 0,1 px między ścianami ramienia, której w rundzie 2 nie było (zasłaniały ją płetwy boków czapki). Promień pod skosem może przez nią przejść ze stopnia jednej strony do stopnia drugiej, zejść pod środek bloku i wyjść przez tylną (odrzucaną) stronę ściany ramienia. Usunąć ją mogą 2 skrzydełka kory w każdym narożniku: przedłużenia boków czapki do marginesu ε, od środka bloku do wierzchu czapki, z wierzchołkami wspólnymi z czapką i ścianą wewnętrzną. Kosztuje to 8 quadów (35 na blok); krawędzie wypukłe zostają czyste, bo skrzydełka nie wystają ponad wierzch. Nie zrobione bez decyzji o koszcie (CP-A, `docs/00` G.6 V2-l). Z dalszych niż ok. 1 blok odległości tych pojedynczych pikseli praktycznie nie widać.

**Poprawione opisy V2b** (wyżej w „Poprawka V2b przed CP-A”, w `docs/00` G.6 V2-l i w BRIEF):
- pasek końca wysięgu na kolanku był odsłonięty z trzech stron już w rundzie 2, wtedy jako słoje także na bokach (przegląd: te same liczby promieni na każdej stronie). V2b zmieniło tylko teksturę, a spadek w teście (0,16–0,20% → 0,05–0,10%) wynikał tylko z tego, że kora przestała się liczyć jako „słoje poza kikutem” w strefach przy krawędziach;
- linia w stopniu nie jest po prostu „ciemniejsza”: na wierzchu tak, na bokach przy gałęzi wschód–zachód ciemniejsza (0,6 w ścianie 0,8), przy gałęzi północ–południe jaśniejsza (0,8 w ścianie 0,6);
- runda 2 miała 300–1020 skanów krawędzi z błędem na scenę, nie 300–1200;
- „(a) przywraca płetwy” dotyczyło dawnej czapki; dziś (a) to słoje na całym końcu wysięgu.

**Nowa znana drobna wada (od rundy 1 V2, nie regres).** Na styku dziecka z półką r + 1 → r wierzch czapki rodzica leży na 8 + r + 1 − c, czyli 0,1 px pod granicą tekseli. Nad półką widać więc pasek 0,1 px następnego wiersza tekstury ściany wysięgu dziecka. Przykład: grab r2, kolumna 8, wiersz 13 ma jasność 80, a wiersz 12 117, co w kadrach `rv_ledge_r2_se/sw` daje z ok. 1 bloku ciemną nitkę 1–2 px; z 3 bloków jej nie widać. Kadr sprzed V2b ma ją identyczną. Usunąć ją może tylko inne przesunięcie czapki, bo wcięcie wierzchu jest potrzebne przy kolankach tych samych promieni. Dopisane do V2-e, do oceny przy CP-A.

**Koszt.** 27 quadów na blok to 1,5 × rundy 2 (18); wanilijna kłoda ma 6, a po zasłonięciu przez sąsiadów zwykle mniej. 14 z nich (ściany wewnętrzne i koniec wysięgu) leży wewnątrz bloku, więc ani wanilia, ani Sodium nie odrzucą ich przez zasłonięcie. Pomiaru renderu gęstego lasu nie było (pnie wycinka trafią do świata w V6). Warianty przy CP-A (V2-l): (a) słoje na całym końcu wysięgu, 26 quadów; (b) obecne, 27; (c) c = 0,05 px: pasek o połowę węższy, walka ścian w buforze głębi od ok. 46 bloków zamiast ok. 70, 27 quadów; do tego ewentualnie skrzydełka w narożnikach, +8.

**Testy rundy.** `TrunkModelRayTest` (oba testy PASS; `coplanarFacesMeetAtCorners` pada na szablonach V2b, `seamsLookLikeTheIdealTrunk` na szablonach rundy 2), `RegistrySnapshotTest`, `TrunkShapeTest`, `fastTest`, `checkDatagen`, tablica próbek w 3 rendererach (3 × PASS: rejestr równy migawce, 840 stanów moda, 36 627 gry, wszystkie 4733 bloki u klienta, modele i cząsteczki, światło, łup `birch_log`), pełny zestaw `tools/dev/run-tests test` (PASS, 1237 s, na drzewie commitu 92078c3, różniącym się od końcowego tylko tym zdaniem). Złoty test terenu bez zmian.

## Stan po V5 (2026-10-16)

**Zrobione.**
- **`worldgen/tree/CrownTerritories`** (czysta Java, ZW-1): terytorium korony każdego zachowanego kandydata pni M2 to komórka diagramu mocy (Woronoj ważony kwadratem promienia korony), przycięta do koła zasięgu pnia. Wejście: wzór kandydatów przez nowy widok tylko do odczytu `TreeStandPlan.Candidates` (sam wzór, `MIN_SPACING` i `DENSITY` bez zmian), `StandMap` i hasz kolumny kandydata z nową solą `feature.tree.crown` (ZW-2). Bez gatunku i bez kodu siedliska. Wynik: `CrownTerritory` (wiersze bitowe ±16 bloków wokół pnia, warstwa, promień, zasięg, flaga skraju). Sesja (`Session`) trzyma pamięć wzoru i kandydatów chunków (kafle 16 × 16); wszystko w pamięci to czyste funkcje, więc sesja nie zmienia wyniku.
- **`StandMap`** jako interfejs zgodny z planem V9 (klasa wieku `AgeClass`, flaga naturalny, luki tylko w lesie naturalnym) z mapą jednorodną `StandMap.uniform`; atrapa z plamami klas wieku, lasu naturalnego i luk (`PatchyStandMap`, sole testowe `test.patchy_stand_map.*`) jest tylko w testach. Prawdziwą mapę z solami `feature.stand.*` robi V9.
- **Warstwa i promień.** Warstwa kandydata (`CrownLayer`: okap a1, piętro a2, odnowienie) z hasza według udziałów drzewostanu [S]: gospodarczy jednopiętrowy (a1 95 / 85 / 80% w drągowinie, dojrzałym i starodrzewie), naturalny dwupiętrowy (a1 70 / 35 / 30%, a2 25 / 50 / 50%); kandydat w luce lasu naturalnego to odnowienie. Promień korony z warstwy i klasy wieku [S]: a1 3,5 / 5,5 / 7, a2 2,5 / 3,5 / 4,5, odnowienie 1 / 1,5 / 2 bloki, razy 0,85–1,2 z hasza. To górne granice rozpiętości koron liściastych (dąb, lipa), bo gatunek jest nieznany; węższe korony (sosna, brzoza) nie wypełnią terytorium. Każda warstwa ma osobny diagram: piętra leżą na różnych wysokościach, a V6 ma trzymać korony a2 i odnowienia pod nasadą korony okapu liczoną z klasy wieku (bez sąsiadów, więc też czysto).
- **Skraj lasu (asymetria, §1.1).** Kandydat w luce (dla okapu i a2) i kandydat na terenie otwartym (`OpenGround`: biom kolumny nie jest leśny; od rundy 1 poprawek `Vacancies`: każdy kandydat bez drzewa, także w lesie, zob. niżej) to znaczniki. W parze znacznik–drzewo znacznik waży 0, a drzewo (1,5 R)², a drzewo ze znacznikiem w odległości ≤ 1,5 R może sięgać na 1,5 R: korona wychyla się nad łąkę albo lukę, od strony lasu ogranicza ją sąsiad. Teren otwarty wymaga kodów siedliska, więc wchodzi do pary tylko wtedy, gdy chunki obu pni leżą w promieniu 1 od siebie. Ta relacja jest symetryczna, więc oba pnie pary liczą te same wagi i ich terytoria się nie nakładają. Terytorium pyta wtedy tylko o kolumny chunka pnia i 8 sąsiadów, czyli o `ChunkHabitats` dostępne przy dekoracji tego chunka (FEATURES). Luki `StandMap` są czyste, więc działają w każdej parze. **Poprawione w rundzie 2:** wakat kandydata zależy od kodu ekotonu, który dla sąsiedniego chunka wymaga promienia 2. Dokładne wakaty są więc tylko w chunku pnia, a w promieniu 1 tylko pewne („Runda 2 poprawek”).
- **Dokładność.** Pozycje to kolumny, wagi to liczby całkowite w 1/16 bloku², więc przynależność kolumny to porównanie liczb całkowitych; remis wygrywa pień o mniejszym (x, z). W parze dwóch pni tej samej warstwy wagi są przycięte tak, że każdy pień ma w terytorium kolumnę swojego pnia (mały pień obok dużego też ma koronę nad sobą). Zasięg ≤ 16 bloków (okno zapisu 3 × 3 chunki), partnerzy do 32 bloków (pnie dalej niż 32 bloki nie mają wspólnej kolumny). Terytorium jest wypukłe.
- **Testy i podgląd:** `CrownTerritoryTest` (w `fastTest`), `CrownTerritoryCostTest` (`@Tag("cost")`), nowe zadanie `./gradlew crownPreview` do `docs/wycinek/korony/terytoria_canopy.png`, `terytoria_subcanopy.png` i `terytoria_regeneration.png`. Podgląd pokazuje 8 × 8 chunków atrapy z połową lasu naturalnego i łąką z szumu: każde terytorium w swoim kolorze, pnie czarne, pnie skraju czerwone, luki w kratkę, las gospodarczy z niebieskim odcieniem; kandydaci na łące są pominięci.

**Wybór wariantu PW-9: (a)**, zapisany w §0.2 i w `docs/00` G.2. Oba warianty są deterministyczne (czyste funkcje ziarna i pozycji; w (a) dodatkowo `ChunkHabitats` promienia 1 chunka pnia), więc wariant (c) i decyzja użytkownika (B3) nie są potrzebne. O wyborze decyduje koszt: (b) wymaga ok. 270 próbek modelu na chunk w pierścieniu chunków o promieniu 2, czyli ok. 1,3 × rdzenia TERRAIN (+1,9–2,6 ms na chunk w nowej sesji, przy FEATURES bazy M2 4,3–5,8 ms), a terytoria i tak trzeba potem policzyć. (a) kosztuje 0,15–0,17 ms na chunk w sesji wątku (jak `ChunkEcotones`) i ok. 0,6 ms w nowej sesji. Uwaga z rundy 1 poprawek: te liczby porównywały (b) bez pamięci z (a) w sesji wątku; (b) z pamięcią kodów pierścienia w wątku kosztuje dodatkowo ok. 0,3–0,8 ms na chunk (tabela w „Runda 1 poprawek”), więc przewaga (a) jest mniejsza, ale zostaje. Ograniczenie (a): terytorium z terenem otwartym da się policzyć tylko z chunka pnia, bo z innego chunka brakuje siedlisk części promienia 1 pnia (test sprawdza, że zapytanie wtedy kończy się błędem zamiast innym wynikiem); bez terenu otwartego z każdego chunka. V6 pisze koronę pnia tylko przy dekoracji chunka pnia, więc to wystarcza. Kandydat na terenie otwartym (od rundy 1: każdy wakat) w chunku dalej niż w promieniu 1 liczy się jak drzewo. Pary w odległości d ≤ 16 bloków zawsze leżą w promieniu 1, a dalszy wakat tnie terytorium pnia T tylko przy d < R_T + R_U (pień bez znacznika w zasięgu) albo d < (3 R_T + √(5 R_T² + 4 R_U²)) / 2, ok. 3 R przy równych promieniach (pień skraju z zasięgiem 1,5 R; to obejmuje okap dojrzały i starodrzew z R > ok. 5,7, d = 17–25). Pomiar (`reportRadiusOneVacancyRule`, 16 × 16 chunków, reguła promienia 1 wobec promienia 2): w lesie gospodarczym dojrzałym różni się 1–2 z 1943–3261 terytoriów (najwyżej o 4 kolumny), w atrapie z plamami 11–12, w naturalnym starodrzewie 16–25 (do 1,3%, najwyżej o 37 kolumn). Pierwotne zdanie („tylko w parach z R_T + R_U > 16”) dotyczyło tylko pni bez znacznika. Po rundzie 2 ten akapit opisuje regułę rundy 1 (dokładne wakaty w promieniu 1); regułę gry opisuje „Runda 2 poprawek”.

**Pomiary.**
- Koszt (`CrownTerritoryCostTest`, jeden wątek, spokojna maszyna, jedna sesja, mediana z 7 przebiegów; obszary 8 × 8 chunków wokół `oak_hornbeam_1` i `fresh_pine_1` w obu skalach; atrapa `StandMap` z 15% lasu naturalnego; teren otwarty z kodów siedliska promienia 1 policzonych przed pomiarem; µs na chunk; ok. 16,8 pnia na chunk):

| Obszar, tryb | (a) nowa sesja | (a) sesja wątku | (a) bez terenu otwartego | (b) próbki pierścienia 2 | rdzeń TERRAIN (256 próbek i klasyfikacja) |
|---|---|---|---|---|---|
| REAL grąd, N | 612 | 154 | 606 | 2327 (271 próbek, 8,6 µs) | 1776 |
| REAL grąd, D | 608 | 153 | 593 | 2379 | 1805 |
| REAL bór, N | 621 | 150 | 606 | 2158 (269, 8,0 µs) | 1595 |
| REAL bór, D | 632 | 160 | 616 | 2233 | 1673 |
| GAMEPLAY grąd, N | 625 | 169 | 617 | 1860 (265, 7,0 µs) | 1347 |
| GAMEPLAY grąd, D | 634 | 172 | 617 | 1947 | 1429 |
| GAMEPLAY bór, N | 614 | 160 | 585 | 2558 (267, 9,6 µs) | 1983 |
| GAMEPLAY bór, D | 608 | 159 | 594 | 2562 | 1997 |

  (b)/(a nowa sesja) 3,0–4,2; (b)/rdzeń TERRAIN 1,28–1,38. Nowa sesja liczy od nowa wzór i miejsca kandydatów 5 × 5 chunków (ok. 420), sesja wątku w kolejności rastrowej tylko 5 nowych kafli. Koszt prawdziwej `StandMap` z V9 doliczy się do każdego kandydata. Pierwszy pomiar, przed przejściem na kafle: sesja wątku 305–341 µs.
- Pokrycie i liczby pni (`CrownTerritoryTest.reportCoverByStand`, jednorodna `StandMap`, wszyscy kandydaci jako drzewa; do kalibracji N1, N2 i N4 w V9): gospodarczy drągowina / dojrzały / starodrzew: a1 15,8 / 13,9 / 13,0 pnia na chunk, a2 0,8 / 1,9 / 1,8; średnie terytorium a1 16 / 18 / 19 kolumn; pokrycie terytoriami okapu 100 / 99 / 97%. Naturalny: a1 11,4 / 5,7 / 4,8, a2 4,3 / 8,2 / 8,2, odnowienie 0,8 / 2,7 / 3,6; terytorium a1 21 / 42 / 51 kolumn; pokrycie okapu 92 / 93 / 98%. To pokrycie terytoriami, nie zwarcie koron: kandydaci, którzy nie staną się drzewami, i węższe korony gatunków je obniżą (pomiar sufitu zwarcia i poprawka: „Runda 1 poprawek”).
- Skraj (test `edgeCrownsLeanOverOpenGround`, prosta granica łąki, dojrzały las gospodarczy): pnie okapu do 4 bloków od skraju mają środek terytorium przesunięty średnio o +1,27 bloku w stronę łąki, a 319 kolumn ich terytoriów leży nad łąką; pnie w głębi lasu +0,05 (po rundzie 1: +2,86 bloku i 845 kolumn, bo kolumna kandydata na łące już nie zatrzymuje korony).

**Odstępstwa i uwagi dla V6 i V9.**
- `StandMap` jest w V5 interfejsem z mapą jednorodną, a atrapa z plamami jest tylko w testach (plan dopuszczał atrapę zgodną z interfejsem). Udziały warstw i promienie to szacunki [S] do kalibracji w V9 (N1, N2, N4).
- Teren otwarty to „biom kolumny spoza grupy leśnej” z surowego kodu `ChunkHabitats`, a nie kod ekotonu. Drzewo, które przez ekoton płaszcza stanie na kolumnie otwartej, ma przy sąsiedzie z lasu małą komórkę (w parze waży 0). Wagi pary są i tak spójne, więc determinizm zostaje.
- Osobne diagramy warstw: korona okapu może przykryć pień a2 albo odnowienia. V6 musi trzymać korony niższych pięter pod nasadą korony okapu z klasy wieku i pisać drewno przed liśćmi. Korony okapu i a2 nie rywalizują o kolumny.
- Terytorium mają wszyscy zachowani kandydaci; o tym, którzy staną się drzewami (paleta, ekoton, struktury, woda), decyduje V9. Kandydat, który nie stał się drzewem, zostawia lukę w okapie (PW-9). **Nieaktualne po rundzie 1 poprawek:** wakaty to znaczniki, ich komórki przejmują sąsiedzi, a same wakaty nie mają terytorium.
- Sesja ma służyć jednemu wątkowi (w grze `ThreadLocal`, jak `ChunkEcotones`). Pamięć wzoru kandydatów w `TreeStandPlan.Candidates` zaczyna od nowa po 65 536 komórkach, a kafle sesji po 256 chunkach.
- Nowe zadanie Gradle `crownPreview` (podgląd PNG, ZW-1); `treePreview` z V6 może je wchłonąć.

**Przeniesione z przeglądu rundy 2 V2 (nienaprawione, poza zakresem V5; naprawione w „Poprawka V2b przed CP-A” w „Stan po V2”).** Czapka pnia z płaskich ścian sięgających marginesu ε (`TrunkTemplates`, ok. wiersza 139) ma płetwy ok. 0,09 px: ściany czapki wystają poza prostopadłe ściany w narożnikach, a wierzch wystaje poza ściany. Z bliska na każdym końcu pędu, półce r + 1 → r i zewnętrznej stronie kolanka widać kropkowaną linię 1 px w wanilii i w Sodium (zrzuty `seams_above`, `stub`, `joints` przeglądu). `TrunkModelRayTest` tego nie łapie, bo pomija punkty do 0,4 px od krawędzi. Zdania „koniec jasnej linii” w „Runda 2 poprawek po przeglądzie V2” i w BRIEF nie uwzględniają tej wady. Wskazówka recenzenta: czapka jako jeden prostopadłościan [a+c, 8, a+c]–[b−c, 8+r−c, b−c] (4 boki i wierzch, czyste krawędzie wypukłe) albo ściany kończące się na płaszczyznach ścian prostopadłych; słoje kolanka zasłonić przez V2-l (b) (słoje tylko na a+c..b−c, 4 paski kory w tej samej płaszczyźnie, 22 quady na blok); `TrunkModelRayTest` rozszerzyć o krawędzie wypukłe (trafiona ściana to jedna z idealnych ścian krawędzi, najwyżej jedna zmiana ściany w gęstym skanie w poprzek krawędzi), scenę samotnego końca pędu i półkę r + 1 → r. Do zrobienia w poprawkach V2 przed CP-A.

**Testy.** `CrownTerritoryTest` (6 testów: niezależność od pytającego chunka i kolejności dla 2 ziaren, 64 chunków pytających i ok. 34 tys. porównań; teren otwarty tylko z promienia 1 i ta sama odpowiedź w losowej kolejności; brak sporów i pień w swoim terytorium dla 3 map drzewostanu, z terenem otwartym i bez; wychylenie na skraju; tylko kandydaci; raport pokrycia), `TreeStandPlanTest` bez zmian, `fastTest`, `costTest --tests '*CrownTerritoryCostTest*'`, pełny zestaw `tools/dev/run-tests test`. Złoty test terenu bez zmian (V5 nie zmienia generacji świata).

### Runda 1 poprawek po przeglądzie V5 (2026-10-16)

Przegląd zgłosił 1 uwagę poważną i 3 drobne. Wszystkie potwierdzone i poprawione.

**Poważna: sufit zwarcia okapu (N4) w wariancie (a).** Potwierdzone pomiarem (`CrownTerritoryTest.vacantCandidatesLeaveNoHoles`). Terytoria liczono ze wszystkich zachowanych kandydatów (16,65 na chunk), a kandydat, który nie stał się drzewem, zostawiał pustą komórkę. Pokrycie terytoriami drzew przy udziale p kandydatów, którzy stają się drzewami (hasz, jednorodna `StandMap`, korony wypełniające terytorium, czyli górna granica zwarcia):

| Drzewostan | p = 0,9 (14,9 drzew na chunk) | p = 0,7 (11,7) | p = 0,55 (9,2) |
|---|---|---|---|
| gospodarczy drągowina / dojrzały / starodrzew, okap | 90 / 90 / 88% → 100 / 99 / 97% | 72 / 72 / 73% → 99 / 100 / 98% | 58 / 57 / 56% → 98 / 100 / 99% |
| naturalny drągowina / dojrzały / starodrzew, okap | 83 / 82 / 87% → 94 / 97 / 100% | 67 / 65 / 68% → 95 / 98 / 100% | 53 / 52 / 54% → 91 / 95 / 98% |
| wszystkie warstwy, zakres sześciu drzewostanów | 91–98% → 98–100% | 74–92% → 98–100% | 59–82% → 96–100% |

(strzałka: wakaty liczone jak drzewa → wakaty jako znaczniki). N1 wymaga 9–14 pni na chunk w grądzie gospodarczym i 8–12 w borze świeżym, czyli p ≈ 0,5–0,85, więc przed poprawką zwarcie miało sufit ok. 55–85% wobec celów N4 75–90% (grąd gospodarczy) i 60–80% (bór świeży gospodarczy), i to jeszcze przed węższymi koronami sosny i brzozy.

**Poprawka (wariant 2 z przeglądu, wdrożony w V5): wakaty jako znaczniki.** `OpenGround` zastąpił interfejs `Vacancies`: wakat to zachowany kandydat bez żadnego drzewa (brak reguły palety albo reguła nie daje drzewa: szansa, przerzedzenie rzadkich drzewostanów, szum luk, głębokość wody), w lesie i na terenie otwartym. Wakat jest znacznikiem jak dotąd teren otwarty: w parze waży 0, drzewo (1,5 R)², relacja tylko między pniami z chunków w promieniu 1 od siebie (symetryczna, więc dalej bez sporów i niezależnie od kolejności). Reguła palety, szansa, przerzedzenie i szum luk to czyste funkcje kodu kolumny z `ChunkHabitats` promienia 1 i ziarna, więc V9 policzy wakaty z danych, które dekoracja chunka ma. **Błędne, poprawione w rundzie 2:** reguła palety idzie z kodu ekotonu (Z10), który dla kandydata sąsiedniego chunka wymaga siedlisk z promienia 2. Wakat nie ma terytorium (`chunk` go pomija, `territory` zgłasza błąd). Przy okazji: kolumna znacznika nie jest już zarezerwowana (zastrzeżenie „pień ma swoją kolumnę” działa tylko w parze dwóch drzew), bo wcześniej kandydat na łące ucinał koronę skraju półpłaszczyzną przez swoją kolumnę; wychylenie koron skraju wzrosło z +1,27 do +2,86 bloku (845 zamiast 319 kolumn nad łąką). Wariant 1 z przeglądu (każdy kandydat okapu staje się drzewem, gęstość tylko z udziałów warstw) odrzucony: udziały nie zależą od siedliska, więc nie da się nimi trzymać N1 osobno dla grądu i boru, a reszta kandydatów i tak musiałaby być drzewami a2 albo odnowienia.

**Uwaga dla V9: wielkość korony w drzewostanie zwartym wyznacza gęstość, nie tabela `RADIUS`.** W lesie gospodarczym średnie terytorium okapu to 16 / 18 / 19 kolumn przy p = 1 i 23–27 przy p = 0,7 (promień równoważny ok. 2,7–2,9 bloku), więc promienie [S] 5,5–7 dojrzałego i starodrzewu prawie nic nie zmieniają; starsze drzewa gospodarcze dostaną większe korony tylko przez mniejszą gęstość (N1, udziały warstw). To zgodne z naturą (390 drzew/ha daje korony o promieniu ok. 2,9 m), ale V9 kalibruje wielkość koron w lesie gospodarczym liczbą drzew, a `RADIUS` i `EDGE_REACH` działają głównie w lesie naturalnym, przy lukach i na skraju.

**Drobna 1: koszt (b) mierzony w innych warunkach niż (a).** Potwierdzone. `CrownTerritoryCostTest` mierzy teraz (b) także z pamięcią kodów pierścienia w wątku (ta sama kolejność rastrowa i ten sam obszar 8 × 8 chunków co (a) w sesji wątku), a (a) z wakatami (teren otwarty z kodów i atrapa palety: 65% kandydatów w lesie to drzewa, ok. 11 drzew na chunk). Jeden wątek, spokojna maszyna, mediana z 7, µs na chunk:

| Obszar, tryb | (a) nowa sesja | (a) sesja wątku | (a) bez wakatów | (b) nowa sesja | (b) pamięć wątku (próbek na chunk) | rdzeń TERRAIN |
|---|---|---|---|---|---|---|
| REAL grąd, N | 601 | 163 | 595 | 2359 | 362 (38) | 1890 |
| REAL grąd, D | 601 | 163 | 581 | 2433 | 356 (38) | 1879 |
| REAL bór, N | 604 | 159 | 582 | 2179 | 315 (38) | 1664 |
| REAL bór, D | 596 | 150 | 594 | 2265 | 331 (38) | 1720 |
| GAMEPLAY grąd, N | 633 | 192 | 598 | 1861 | 275 (37) | 1382 |
| GAMEPLAY grąd, D | 630 | 188 | 602 | 1973 | 293 (37) | 1465 |
| GAMEPLAY bór, N | 608 | 173 | 583 | 2638 | 376 (37) | 2072 |
| GAMEPLAY bór, D | 604 | 172 | 580 | 2664 | 384 (37) | 2079 |

  Kolumny (b) to tylko próbkowanie kodów; terytoria (b) kosztowałyby jeszcze tyle co (a). Na obszarze 8 × 8 chunków pamięć wątku próbkuje średnio 37–38 kandydatów na chunk; w długim przebiegu rastrowym byłoby to ok. 5 nowych chunków pierścienia, czyli ok. 83 próbki na chunk, co przy 7,0–10,0 µs na próbkę daje ok. 0,6–0,8 ms (szacunek recenzenta). Prawdziwa różnica (b) − (a) w grze wynosi więc ok. +0,3–0,8 ms na chunk, nie +1,9–2,6 ms. (a) zostaje: jest tańsza, a pomiar `reportRadiusOneVacancyRule` pokazuje, że wiedza o wakatach w promieniu 2 zmienia najwyżej 1,3% terytoriów. (b) dałaby dokładny skraj i wakaty w promieniu 2 oraz możliwość promienia korony z gatunku (gatunek z reguły palety i rampy zasięgu), za cenę pamięci kodów pierścienia w każdym wątku i kolejności generacji, która w grze nie jest rastrowa.

**Drobna 2: drzewa zastępcze ignorują terytoria.** Potwierdzone. Gwarancja „korony nie dzielą kolumny i wynik nie zależy od kolejności” obejmuje tylko drzewa proceduralne; zawężona w javadoc `CrownTerritories`. Reguła dla sąsiedztw mieszanych: kandydat z drzewem zastępczym (`tree_substitute/`, PW-3: biomy spoza wycinka i kolumny ekotonu) nie jest wakatem, więc zachowuje swoją komórkę, a sąsiednie korony proceduralne nie dostają w jego stronę zasięgu skraju (1,5 R). Korona wanilii może jednak wyjść poza komórkę, a liście obu drzew są `#replaceable_by_trees`, więc w części wspólnej wygrywa drzewo pisane później (R5). V6 ma test determinizmu na ekotonie z drzewami zastępczymi (zmierzyć liczbę kolumn liści zależnych od kolejności); jeśli będzie widoczna, V9 wybierze: drzewa zastępcze tylko poza zasięgiem `MAX_REACH` od terytoriów proceduralnych albo liście proceduralne niezastępujące liści innych drzew.

**Drobna 3: zdanie o terenie otwartym w promieniu 2.** Potwierdzone (wzór recenzenta sprawdzony). Zdanie w „Wybór wariantu PW-9” poprawione, z pomiarem `reportRadiusOneVacancyRule`.

**Zmiany.** `worldgen/tree/Vacancies` (zamiast `OpenGround`), `CrownTerritories` (wakaty jako znaczniki, kolumna znacznika bez rezerwacji, wakaty bez terytorium, javadoc gwarancji, pakietowa sesja z promieniem wakatów do testu), `CrownTerritory.edge` (znacznik w zasięgu: wakat albo luka; teraz ma go większość pni lasu przy p < 1, a zasięg 1,5 R ograniczają sąsiednie drzewa), `CrownTerritoryTest` (wakaty w testach determinizmu i sporów, `vacantCandidatesHaveNoTerritory`, `vacantCandidatesLeaveNoHoles`, `reportRadiusOneVacancyRule`), `CrownTerritoryCostTest` ((b) z pamięcią wątku, (a) z wakatami), podgląd `crownPreview` z atrapą palety (65% kandydatów w lesie to drzewa; wakaty w lesie jako szare kółka; czerwone kropki to pnie ze znacznikiem w zasięgu skraju).

**Testy.** `CrownTerritoryTest` (9 testów), `fastTest`, `costTest --tests '*CrownTerritoryCostTest*'`, pełny zestaw `tools/dev/run-tests test`. Złoty test terenu bez zmian (runda nie zmienia generacji świata).

### Runda 2 poprawek po przeglądzie V5 (2026-10-16)

Przegląd zgłosił 1 uwagę poważną i powtórzył 3 drobne z rundy 1. Poważna potwierdzona i poprawiona w prototypie; drobne były poprawione już w rundzie 1.

**Poważna: wakatów kandydatów z sąsiednich chunków nie da się poznać przy dekoracji.** Potwierdzone w kodzie. `TreeStandFeature.plant` wybiera regułę palety z kodu ekotonu (`ChunkEcotones.of(...).trees()`, Z10), a nie z surowego kodu `ChunkHabitats`. Kolumna bierze kod kolumny odległej o najwyżej `Ecotone.REACH` = 16 bloków (z meandrem), więc kod ekotonu kandydata u z chunka C' (sąsiada C) zależy od siedlisk sąsiadów C', czyli od chunków w promieniu 2 od C. Przy FEATURES chunka C tych siedlisk nie ma. To samo dotyczy V5-h (czy u dostaje drzewo zastępcze wanilii). Zdanie z rundy 1, że reguła palety, szansa, przerzedzenie i szum luk są czystymi funkcjami kodu kolumny z `ChunkHabitats` promienia 1, było więc błędne.

Gdyby V9 przybliżył wakat sąsiada (surowym kodem albo regionem z dziurami), C i C' mogłyby się nie zgodzić. C uznałby u za wakat i dał swojemu pniowi t wagi znacznika i zasięg 1,5 R w stronę u. C' posadziłby na u drzewo proceduralne z wagami drzewo–drzewo. Terytoria t i u by się nałożyły, czyli powstałby spór koron zależny od kolejności dekoracji, który PW-9 (a) miał wykluczyć. Do tego spór pojawiałby się sporadycznie, zależnie od tego, które chunki akurat są wczytane. Pokazuje to nowy test `guessedNeighborVacanciesCauseNoDisputes`: dekoracja zgaduje wakaty kandydatów sąsiadów z 10% błędów, w każdym chunku inaczej. Przy regule rundy 1 (dokładne wakaty w promieniu 1) daje to 273–281 kolumn zajętych dwa razy (6 × 6 chunków, 341 pni), przy nowej regule 0.

**Niezmiennik** (javadoc `Vacancies` i `CrownTerritories`). Znacznik nie ma terytorium, więc rozłączność terytoriów dwóch drzew wymaga tylko, żeby żaden kandydat nazwany wakatem nie wyrósł w drzewo. Fałszywe „wakat” daje spór koron. Fałszywe „nie wakat” zostawia tylko lukę, a to jest bezpieczne. Potrzebna jest więc poprawność odpowiedzi „wakat”, a nie symetria relacji z rundy 1.

**Poprawka: reguła (i) z przeglądu, wdrożona w prototypie.** `Vacancies` zadaje teraz dwa pytania:
- `vacant(x, z)` to wakat dokładny. Pyta się o niego tylko kandydatów chunka pnia, bo dekoracja tego chunka ma kody ekotonu;
- `surelyVacant(x, z)` to wakat pewny, bez kodu siedliska. Pyta się o niego kandydatów chunków w promieniu 1. Kandydat jest pewnym wakatem, gdy stoi w luce szumu drzewostanu albo jego losowanie jest ≥ najwyższej szansy reguł palety. Opcjonalnie V9 doliczy wodę głębszą, niż pozwala jakikolwiek wpis palety (surowa głębokość wody chunka kandydata). Wakat pewny musi pociągać wakat dokładny.

Kandydat dalej niż w promieniu 1, i taki, którego wakat nie jest pewny, liczy się jak drzewo. Sesja gry to `session()`: wakaty dokładne w promieniu 0 i pewne w promieniu 1; `session(int, int)` służy tylko testom. `TreeStandPlan` dostał nowe metody tylko do odczytu; generacja się nie zmienia, złoty test też nie:
- `chance(n, gaps)`, ta sama szansa co w `of`, który teraz z niej korzysta;
- `Palette.highestChance`;
- `Candidates.draw` i `Candidates.treeless`.

Nowy test `TreeStandPlanTest.treelessCandidatesGetNoTree` sprawdza dwie rzeczy. W chunku jednej reguły kandydat jest drzewem dokładnie wtedy, gdy nie jest `treeless` przy szansie reguły. W chunkach z kodami mieszanymi kolumna po kolumnie (jak w ekotonie) żadne z 20 797 drzew nie stoi na kandydacie `treeless` przy najwyższej szansie. Przy palecie M2 (najgęstsza reguła ma 12 drzew na chunk, szansa 0,779) pewnymi wakatami jest 26,9% zachowanych kandydatów: ok. 22% z losowania i 7,5% z luk.

**Zwarcie przy tej regule** (`vacantCandidatesLeaveNoHoles`, jednorodna `StandMap`). Atrapa `PaletteVacancies` korzysta z prawdziwych losowań i testu luk planu; p to udział kandydatów lasu, które stają się drzewami. Pokrycie okapu terytoriami, w %, w czterech wariantach (górna granica zwarcia N4):
1. wakaty liczone jak drzewa;
2. znaczniki tylko w chunku pnia, wariant (iii);
3. reguła gry, wariant (i);
4. wakaty dokładne w promieniu 1, czyli reguła rundy 1 (niedostępna w grze).

| Drzewostan | p = 0,72 (12 drzew na chunk) | p = 0,55 (9,2) | p = 0,42 (7,0) |
|---|---|---|---|
| gospodarczy drągowina | 76 / 88 / 97 / 97 | 55 / 79 / 87 / 95 | 41 / 70 / 78 / 91 |
| gospodarczy dojrzały | 75 / 88 / 99 / 99 | 54 / 79 / 89 / 99 | 40 / 70 / 79 / 99 |
| gospodarczy starodrzew | 72 / 86 / 98 / 98 | 52 / 76 / 87 / 98 | 39 / 68 / 77 / 99 |
| naturalny drągowina | 71 / 85 / 93 / 93 | 52 / 76 / 83 / 90 | 38 / 64 / 70 / 83 |
| naturalny dojrzały | 73 / 87 / 97 / 97 | 53 / 77 / 86 / 95 | 37 / 61 / 69 / 89 |
| naturalny starodrzew | 78 / 88 / 99 / 99 | 56 / 76 / 85 / 98 | 40 / 62 / 71 / 97 |

Pokrycie wszystkich warstw w regule gry wynosi 88–97% przy p = 0,55 i 78–92% przy p = 0,42. Przy p ≥ 0,72 reguła gry jest równa dokładnej, bo każdy wakat w lesie jest wtedy pewny. Palety wycinka w M2: grąd 9 drzew na chunk (p ≈ 0,54), Bśw 11 (0,66), BMśw 10 (0,60), LMśw 9 (0,54).

**Gdzie ginie pokrycie: pas wzdłuż granic chunków.** Pień t przy granicy chunka liczy kandydata u zza granicy jak drzewo, jeśli wakat u nie jest pewny. Część komórki pustego u po stronie t zostaje więc bez korony. Drzewa chunka u znają wakat dokładnie, ale ogranicza je dwusieczna z t. Pomiar `reportCoverNearChunkBorders` (gospodarczy dojrzały) daje pokrycie okapu w kolumnach w odległości 0…7 od najbliższej granicy chunka, w %, w zapisie reguła gry / dokładne w promieniu 1:

| p | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|---|---|---|
| 0,66 | 90/98 | 93/99 | 95/98 | 97/99 | 99/99 | 99/99 | 99/99 | 100/100 |
| 0,54 | 78/98 | 83/99 | 89/98 | 94/99 | 97/99 | 98/99 | 99/99 | 100/100 |
| 0,42 | 67/98 | 73/99 | 78/98 | 84/99 | 91/99 | 95/99 | 97/99 | 98/99 |

Luki gromadzą się w pasie szerokim na ok. 4 bloki przy granicach chunków i tworzą siatkę co 16 bloków. Podgląd `terytoria_canopy.png` ją pokazuje. Siatka to prosta linia, której zabrania Z10. Pomiar dotyczy terytoriów, nie koron: korony węższe niż terytorium częściowo zamaskują pas, ale V9 nie może na tym polegać. Na granicy chunka słabiej wychyla się też skraj lasu. Test `edgeCrownsLeanOverOpenGround` (p = 0,65) daje dla skraju w środku chunka +1,29 bloku i 17,1 kolumny nad łąką na pień, a dla skraju na granicy chunka +0,38 bloku i 4,7 kolumny. Liczby z rundy 1 (+2,86 bloku) dotyczyły lasu bez wakatów i dokładnej wiedzy o łące, więc nie są porównywalne.

**Wybór dla V9 (V5-i, domyślny, do potwierdzenia przy CP-B).** Prototyp i `session()` zostają przy regule (i). Tylko ją V9 może wdrożyć od razu bez sporów. V9 musi jednak usunąć pas. Kolejność prób:
1. **(i+) wakaty udowodnione z danych dekoracji.** Kod ekotonu kandydata sąsiada liczy się z regionu chunka pnia (kody promienia 1, dalej `Ecotone.UNKNOWN`). Wynik się uznaje tylko wtedy, gdy obliczenie nie odczytało żadnej nieznanej komórki; wtedy jest dokładny. Kontrakt `surelyVacant` na to pozwala: odpowiedź może zależeć od pytającego chunka, byle nigdy nie była błędna. Ile pasa to odzyska, trzeba zmierzyć. Kandydat tuż za granicą ma większość odczytów po stronie C, ale szybka ścieżka `Region.uniform` sprawdza kwadrat 16 bloków, który sięga promienia 2. Potrzebna jest więc wersja `Ecotone.effective`, która zgłasza odczyt `UNKNOWN` i czyta tylko punkty na promieniu wektora mieszania.
2. **(ii) kody ekotonu kandydatów pierścienia promienia 1 z kodów chunków promienia 2 próbkowanych z modelu krajobrazu.** Bez współdzielenia to koszt co najmniej wariantu (b), bo ekoton czyta kilka punktów wokół każdego kandydata. Kody są jednak czystą funkcją kolumny, a TERRAIN tych chunków i tak je policzy. Wspólna pamięć kodów chunków (do sprawdzenia: wątki, pamięć, zgodność z `ChunkHabitats`) może więc ten koszt prawie znieść. Wtedy porównanie (a) z (b) z rundy 1 trzeba powtórzyć, bo (b) z tą samą pamięcią dałby też gatunek i promień korony z reguły palety.
3. **(iii) przyjąć liczby promienia 0** (druga liczba w tabeli wyżej). Odrzucone: pas jest jeszcze szerszy.

Testy dla V6 i V9:
- test na prawdziwych kodach ekotonu: kandydat w pasie ekotonu przy granicy chunków, oba chunki dekorowane w obu kolejnościach, 0 kolumn spornych (prototyp ma już `guessedNeighborVacanciesCauseNoDisputes` na atrapie);
- zrzut okapu z góry w miejscach wycinka: siatka granic chunków niewidoczna;
- `reportCoverNearChunkBorders` na prawdziwej palecie.

**PW-9 = (a) zostaje.** Geometria terytoriów (wzór kandydatów, `StandMap`, hasz) nie potrzebuje siedlisk. Zmienia się źródło wakatów, a koszt (ii) trzeba doliczyć przy porównaniu z (b).

**Drobne 1–3: powtórzone z rundy 1, poprawione w niej (5b5ed65).** Sprawdzone ponownie w kodzie i w dokumentach:
1. `CrownTerritoryCostTest.cachedRingB` mierzy (b) z pamięcią wątku w tej samej sesji rastrowej co (a). Liczby są w tabeli „Runda 1 poprawek” i w §0.2.
2. Gwarancja jest zawężona do drzew proceduralnych (javadoc `CrownTerritories`, V5-h, test V6 na ekotonie z drzewami zastępczymi).
3. Zdanie o promieniu 2 jest przeredagowane wzorem recenzenta i poparte pomiarem. Dotyczy jednak reguły rundy 1 (wakaty dokładne w promieniu 1). Dla reguły gry odpowiednikiem jest `reportSureVacancyRadius`: wakaty pewne znane w promieniu 2 zamiast 1 zmieniają 0–2 z 1792–2011 terytoriów, najwyżej o 29 kolumn.

**Koszt nie zmierzony ponownie.** Maszyna nie była spokojna: inny proces zajmował ok. 2 rdzeni. Tabela kosztu z rundy 1 zostaje. Reguła gry pyta o wakat pewny kandydata sąsiada jednym haszem i jedną próbką szumu luk, zamiast hasza atrapy rundy 1, więc zmiana kosztu (a) powinna być mała; potwierdzi to pomiar V9.

**Zmiany.**
- `worldgen/tree/Vacancies`: niezmiennik, `vacant` dokładny i `surelyVacant` pewny.
- `CrownTerritories`: sesja z promieniami wakatów dokładnych (0) i pewnych (1), javadoc.
- `TreeStandPlan`: `chance`, `Palette.highestChance`, `Candidates.draw` i `Candidates.treeless`; sól losowania jako stała `DRAW_SALT` o tej samej wartości.
- Testy:
  - nowa atrapa `PaletteVacancies`;
  - `CrownTerritoryTest`: widok dekoracji `decorated`, który zgłasza błąd przy pytaniu o wakat dokładny spoza chunka pnia albo pewny spoza promienia 1 i sprawdza, że wakat pewny pociąga dokładny; `guessedNeighborVacanciesCauseNoDisputes`; zwarcie w 4 regułach; `reportCoverNearChunkBorders`; `reportSureVacancyRadius` zamiast `reportRadiusOneVacancyRule`; skraj w środku chunka i na granicy;
  - `TreeStandPlanTest.treelessCandidatesGetNoTree`;
  - `CrownTerritoryCostTest` i `CrownTerritoryPreview` na nowej atrapie.
- Podglądy `crownPreview` przeliczone.

**Testy.** Przeszły:
- `CrownTerritoryTest` (11 testów) i `TreeStandPlanTest`;
- `fastTest`;
- pełny zestaw `tools/dev/run-tests test`: PASS (1479 s, na drzewie commitu 1723686, różniącym się od końcowego tylko tym zdaniem i zawinięciem jednego wiersza javadoc `CrownTerritories`).

Złoty test terenu się nie zmienia, bo runda nie zmienia generacji świata.

## Stan po V3 (2026-10-16)

> **Poprawki rundy 1 przeglądu (niżej, „Runda 1 poprawek po przeglądzie V3”).** Zestawy opisane tu jako „Sodium i paczka” to w rzeczywistości **Sodium + Nvidium**: na RTX 4060 Nvidium z paczki włącza się sam i rysuje teren zamiast Sodium. Czysty Sodium (karty AMD i Intel albo Nvidium wyłączone) rysował gałązki w trybie Fast jako **nieprzezroczyste kostki**, więc zdania o gałązkach „wyciętych w trybie Fast” niżej były prawdziwe tylko dla wanilii i Nvidium; runda 1 naprawiła to mixinem. Wiersze Sodium tabeli przebudowy (4–7 klatek po 100–570 ms) **się nie powtórzyły** i zostały zastąpione pomiarami rundy 1; V3-d nie wymaga decyzji. Zdania błędne są oznaczone „(błędne, runda 1)”.

**Wynik.** Obie próby **działają** we wszystkich czterech zestawach: bez Sodium i z Sodium 0.9.2 z całą paczką (`-Poptimization`: MoreCulling 1.9.0-beta.1, C2ME, ImmediatelyFast, Nvidium 0.4.4 i reszta; runda 1: z Nvidium włączonym), z Serene Seasons i bez (`-PnoSeasons`). **B6 = (a)** (własne krzywe gatunków na surowym kolorze listowia) i **PW-8 = (a)** (wygląd faz po stronie klienta przez owinięty model), a zima koron (T-D5) zostaje w rdzeniu (V13). Zapasy B6 (b) i PW-8 (b) nie są potrzebne. Jedyne ryzyko, które próba potwierdziła, to koszt przebudowy siatek przy Sodium (R9, niżej) (błędne, runda 1: zacięcia się nie powtórzyły; zostaje tylko `allChanged()` SS przy zmianie podsezonu).

**Zrobione.**
- **Bloki próbne** `polishforests_probe:probe_leaves` (`TintedParticleLeavesBlock`, właściwości wanilijnych liści) i `polishforests_probe:probe_plant` (`BushBlock`, model `tinted_cross` z teksturą `polishforests:block/bilberry` pod tintem trawy). Kod prób jest w modzie gametestów (`src/gametest/java/pl/polishforests/gametest/probe/`), a zasoby w `src/gametest/resources/assets/polishforests_probe/`: blockstates, model liści (rodzic `minecraft:block/leaves`, tekstura `polishforests:block/broadleaf_leaves`), model gałązek (`minecraft:block/cube_all` z teksturą `minecraft:block/dead_bush` przez id, tylko na próbę) i model rośliny. Bloki rejestruje się tylko w przebiegu `-Pgametest=probes` (`RenderProbes.enabled()`), więc nie ma ich w grze, w pliku moda, w innych gametestach ani w migawce rejestru (osobna przestrzeń nazw, a `RegistrySnapshot` czyta tylko `polishforests`). Bloki nie mają przedmiotów. Sprawdzone: `-Pgametest=slice -Psites=none` przechodzi bez bloków próbnych.
- **Kolor (B6).** `ProbeSeason.RAW_FOLIAGE = (biome, x, z) -> biome.getFoliageColor()` w `ColorResolverRegistry`; `BlockTintSource` liścia próbnego bierze `level.getBlockTint(pos, RAW_FOLIAGE)` (mieszanie biomów zostaje) i nakłada **jedną krzywą próbną**: 3 kroki koloru na podsezon (36 na rok), w każdym podsezonie cel i jego waga względem koloru biomu (wiosna jasnozielona, lato kolor biomu, jesień żółta, pomarańczowa i brązowa), między podsezonami przejście w trzech krokach. Krok liczy się z `Seasons.provider().yearProgress(level)` po stronie klienta co tick (SS albo `FallbackCalendar`). Zmiana kroku przebudowuje siatki, domyślnie przez oznaczenie wszystkich sekcji w zasięgu jako brudnych (`setSectionRangeDirty`); `ProbeSeason.fullRebuild` przełącza na `levelExtractor.allChanged()` (pomiary niżej).
- **Model (PW-8, T-D5).** `ModelLoadingPlugin` z `modifyBlockModelOnLoad` owija model stanu bloku (`SeasonalModel.Root extends WrapperUnbakedRootBlockStateModel`): model zastępczy jest zależnością, więc wczytuje się i wypieka razem z modelem bloku. `SeasonalModel extends WrapperBlockStateModel` w `emitQuads` rysuje model bloku albo model zastępczy, albo nic. Liść próbny jest bezlistny od EARLY_WINTER do EARLY_SPRING (gałązki), a roślina próbna jest „pusta” poza EARLY–LATE_SPRING (geofit wiosenny). `collectParts` (ścieżki bez FRAPI: bloki przesuwane, nakładka niszczenia) daje to samo, a `createGeometryKey` zwraca `null`, bo geometria zależy od pory roku. Gałązki są emitowane część po części, bez transformacji, którą domyślne `emitQuads` dokłada liściom w trybie Fast (`ModelBlockRenderer.forceOpaque`: wszystko do warstwy SOLID). Dzięki temu gołe korony zostają w trybie Fast wycięte, a nie stają się czarnymi kostkami (błędne, runda 1: tylko bez Sodium i z Nvidium; czysty Sodium sam przenosi wszystkie quady bloku liści do materiału SOLID, więc runda 1 dodała mixin na `ModelBlockRenderer.forceOpaque`).
- **Gametest** `RenderProbesClientGameTest` (`-Pgametest=probes`, tylko z nazwy, nie w `all`; `-Psites=board|rebuild` wybiera część; wyniki w `build/probes/<renderer>_<ss|noss>.txt`).
  - Część `board`: świat płaski, kamienna podłoga; głęboka ściana liści próbnych (7 × 6 × 4) i obok taka sama ściana `oak_leaves` wanilii (SS barwi ją sam); ekrany jednego bloku grubości z obu rodzajów liści na tle nieba; płat rośliny próbnej na gruboziarnistej ziemi; drzewko z pniem `oak_trunk` r3 i kulistą koroną z liści próbnych. Pory: EARLY_SPRING, MID_SPRING, MID_SUMMER, MID_AUTUMN, LATE_AUTUMN, MID_WINTER (z SS `season set`, bez SS `time set`, bez SS także 4. i 8. dzień MID_AUTUMN, czyli kroki koloru wewnątrz podsezonu), potem tryb Fast w MID_SUMMER i MID_WINTER. Test czeka na krok koloru i przebudowane siatki, robi zrzuty i sprawdza je:
    - w fazie z liśćmi chromatyczność mediany środka ściany próbnej (każdy kanał przez sumę) różni się od tintu krzywej o ≤ 0,03;
    - tint z `BlockColors` równa się krzywej;
    - surowy kolor listowia jest ten sam w każdej porze, czyli SS nie sezonuje go drugi raz;
    - udział nieba w środku ekranu wynosi ≤ 25% z liśćmi i ≥ 35% w fazie gałązek;
    - udział pikseli rośliny wynosi ≥ 8% w fazie widocznej i ≤ 0,5% w fazie pustej;
    - w trybie Fast liście próbne są tak samo pełne albo wycięte jak liście dębu wanilii, a gałązki są wycięte (≥ 35% nieba); runda 1: także tyle samo nieba co w Fancy (±5 punktów, wewnętrzne ściany korony zostają).
  - Część `rebuild`: świat „Polska” REAL (N), miejsce `oak_hornbeam_1`, kamera 30 bloków nad gruntem, zasięg widzenia 12, przebudowa wszystkich siatek trzema sposobami po 3 razy: `allChanged()`; oznaczenie wszystkich sekcji jako brudnych naraz; fala pierścieni kolumn od kamery, jeden pierścień co 2 ticki. Do tego jedna zmiana kroku koloru przez obsługę zdarzenia (z SS: `season set late_summer`, więc SS też woła `allChanged()`). Czas renderu klatki (`Minecraft.getFrameTimeNs()`) i liczba sekcji z siatką w widoku (`countRenderedSections()`) są zapisywane w każdym ticku.

**Pomiary (2026-10-16, spokojna maszyna, cztery przebiegi w jednej sesji).** Kolor, gałązki i roślina:

| Zestaw | odległość chromatyczności ściany próbnej (fazy z liśćmi) | surowe listowie | listowie wanilii (SS) w X | niebo przez ekran: liście / gałązki | Fast: liście próbne / dąb / gałązki | roślina: wiosna / reszta roku |
|---|---|---|---|---|---|---|
| bez Sodium, z SS | 0,000–0,005 | `#77ab2f` w każdej porze | `#df6d0c` | 18,9% / 53,1% | 0,0% / 0,0% / 70,7% | 12,6% / 0,0% |
| bez Sodium, bez SS | 0,000–0,005 (także kroki 22 i 23 w MID_AUTUMN) | `#77ab2f` | `#77ab2f` | 18,9% / 53,1% | 0,0% / 0,0% / 70,7% | 12,6% / 0,0% |
| Sodium + Nvidium (paczka), z SS | 0,001–0,005 | `#77ab2f` | `#df6d0c` | 18,9% / 52,9% | 35,2% / 33,0% / 70,7% | 12,4% / 0,0% |
| Sodium + Nvidium (paczka), bez SS | 0,001–0,005 (kroki 22 i 23 też) | `#77ab2f` | `#77ab2f` | 18,9% / 52,9% | 35,2% / 33,0% / 70,7% | 12,4% / 0,0% |

Krzywa próbna daje w MID_SPRING `#9cc652`, w MID_SUMMER `#77ab2f`, w MID_AUTUMN `#cba230` (kroki 22 i 23: `#b99331`, `#a78333`), w LATE_AUTUMN `#957434`. Mediany ściany z Sodium są o ok. 5% ciemniejsze niż bez niego (inne cieniowanie), chromatyczność jest ta sama. Drugi przebieg części `board` w trzech zestawach dał identyczne liczby. Z paczką tryb Fast nie robi z liści wanilii pełnych kostek: liście zostają wycięte, a tylne ściany są pominięte (33% nieba przez ekran dębu wobec 0% bez Sodium). Liść próbny zachowuje się wtedy jak liść wanilii. (Runda 1: to zasługa Nvidium, które rysuje cały nieprzezroczysty teren z odcięciem alfa 0,5, a nie Sodium ani MoreCulling; czysty Sodium z MoreCulling daje w trybie Fast pełne liście, 0% nieba.) Krzywa nie zależy od SS: z SS i bez kolory ściany próbnej są te same, a liście dębu wanilii z SS mają w X `#df6d0c` i w I `#cca577`.

Przebudowa siatek (zasięg 12, ok. 354–407 sekcji z siatką w widoku; mediany z 3 przebiegów; „klatki > 50 ms” liczone od wywołania do 40 ticków po końcu). **Runda 1:** wiersze „Sodium i paczka” (w rzeczywistości Sodium + Nvidium) się nie powtórzyły (najpewniej obciążona maszyna; gametest dodatkowo zawyża budżet Sodium), „czas do końca” to czas zegara gametestu, a przy Sodium detektor końca odpalał za wcześnie; kolumna „najmniejszy udział” liczy klatki < 95% widoku, nie klatki z 0%. Aktualne liczby: tabela rundy 1.

| Zestaw | sposób | czas do końca | p95 klatki | najgorsza klatka | klatki > 50 ms | najmniejszy udział widoku z siatką |
|---|---|---|---|---|---|---|
| bez Sodium, z SS | `allChanged()` | 952 ms | 4,6 ms | 23,7 ms | 0 | 0% (8–10 klatek bez siatek) |
| | sekcje brudne naraz | 607 ms | 3,9 ms | 15,2 ms | 0 | 100% |
| | fala pierścieni | 2185 ms | 2,5 ms | 3,0 ms | 0 | 100% |
| bez Sodium, bez SS | `allChanged()` | 719 ms | 10,4 ms | 26,4 ms | 0 | 0% |
| | sekcje brudne naraz | 1028 ms | 5,2 ms | 25,5 ms | 0 | 100% |
| | fala pierścieni | 2248 ms | 3,3 ms | 5,2 ms | 0 | 100% |
| Sodium i paczka, z SS | `allChanged()` | 2343 ms | 323,5 ms | 391,2 ms | 5 | 0% (8–9 klatek) |
| | sekcje brudne naraz | 326 ms | 299,8 ms | 415,6 ms | 7 | 100% |
| | fala pierścieni | 2978 ms | 159,0 ms | 309,0 ms | 4 | 100% |
| Sodium i paczka, bez SS | `allChanged()` | 1952 ms | 150,4 ms | 508,4 ms | 4 | 0% |
| | sekcje brudne naraz | 362 ms | 277,9 ms | 566,8 ms | 7 | 100% |
| | fala pierścieni | 3654 ms | 108,9 ms | 342,5 ms | 5 | 100% |

Bez przebudowy klatka ma medianę 0,8–1,1 ms i maksimum 1,6–2,2 ms. Zmiana kroku przez obsługę zdarzenia daje to samo co odpowiadający jej sposób. Bez SS sekcje oznacza się jako brudne: bez Sodium 739 ms, najgorsza klatka 24,6 ms; z Sodium 365 ms i 7 klatek > 50 ms. Z SS (`season set`) SS i tak woła `allChanged()`: bez Sodium 1278 ms i 9 klatek bez siatek, z Sodium 2494 ms i 6 klatek > 50 ms. To jedna przebudowa, a nie dwie po sobie.

Wnioski:
- `allChanged()` zrzuca wszystkie siatki: przez 0,5–1 s (bez Sodium) albo ok. 2 s (z Sodium) świat w zasięgu widzenia znika i wraca. SS robi to przy każdej zmianie podsezonu.
- Oznaczenie sekcji jako brudnych zostawia stare siatki, aż przyjdą nowe. Bez Sodium nie ma żadnej klatki > 26 ms, a fala pierścieni wygładza wszystko do ≤ 5 ms.
- Z Sodium przy każdym sposobie zostaje 4–7 klatek po 100–570 ms (zacięcie ok. 1 s). Fala zmniejsza p95 o połowę, ale nie usuwa najgorszych klatek. (Błędne, runda 1: nie powtórzyło się; przy oznaczaniu sekcji żaden zestaw nie ma klatki > 9 ms.)
- Czasy klatek pochodzą z gametestu: gra renderuje tam jedną klatkę na tick, sterowaną przez wątek testu, więc to miara pracy wątku renderu, a nie FPS. Prawdziwe FPS w przelocie mierzy V10.

**Sygnatury FRAPI i API kolorów w 26.3** (Fabric API 0.161.0+26.3: `fabric-model-loading-api-v1` 8.0.35, `fabric-renderer-api-v1` 17.0.15, `fabric-rendering-v1` 27.0.14; odczytane z klas jarów i sprawdzone kompilacją i działaniem):
- `ModelLoadingPlugin.register(ModelLoadingPlugin)`, `void initialize(ModelLoadingPlugin.Context)`;
- `Context`: `modifyBlockModelOnLoad()` → `Event<ModelModifier.OnLoadBlock>`, `modifyBlockModelBeforeBake()`, `modifyBlockModelAfterBake()`, `registerBlockStateResolver(Block, BlockStateResolver)`, `addModel(ExtraModelKey<T>, UnbakedExtraModel<T>)`;
- `ModelModifier.OnLoadBlock.modifyModelOnLoad(BlockStateModel.UnbakedRoot, OnLoadBlock.Context)` (`Context.state()`); `BeforeBakeBlock.modifyModelBeforeBake(UnbakedRoot, Context)` (`state()`, `baker()`); `AfterBakeBlock.modifyModelAfterBake(BlockStateModel, Context)` (`state()`, `sourceModel()`, `baker()`). Dodatkowy model jako zależność trzeba dodać już przy wczytaniu (OnLoad), bo przed wypiekaniem zależności są już rozwiązane;
- `WrapperUnbakedRootBlockStateModel` (pole `wrapped`; `bake(BlockState, ModelBaker)`, `resolveDependencies(ResolvableModel.Resolver)`, `visualEqualityGroup(BlockState)`), `WrapperBlockStateModel` (pole `wrapped`; `collectParts(RandomSource, List<BlockStateModelPart>)`, `particleMaterial()`, `materialFlags()`);
- `FabricBlockStateModel` (wstrzyknięte w `BlockStateModel`): `emitQuads(QuadEmitter, BlockAndTintGetter, BlockPos, BlockState, RandomSource, Predicate<Direction> cullTest)`, `createGeometryKey(BlockAndTintGetter, BlockPos, BlockState, RandomSource)` (domyślnie `null`, w `WrapperBlockStateModel` z modelu owiniętego), `particleMaterial(BlockAndTintGetter, BlockPos, BlockState)`, `materialFlags(…, RandomSource)`. Domyślne `emitQuads` pcha w trybie Fast dla `LeavesBlock` transformację do warstwy SOLID (`ModelBlockRenderer.forceOpaque(cutoutLeaves, state)`);
- `BlockStateModelPart.emitQuads(QuadEmitter, Predicate<Direction>)`; `SingleVariant.Unbaked(Variant)`, `new Variant(Identifier)`, `BlockStateModel.Unbaked.bake(ModelBaker)`;
- `QuadEmitter` m.in. `chunkLayer(ChunkSectionLayer)`, `tintIndex(int)`, `pushTransform(QuadTransform)` / `popTransform()`, `emit()`;
- kolory: `ColorResolverRegistry.register(ColorResolver)` (`ColorResolver.getColor(Biome, double, double)`), `BlockColorRegistry.register(List<BlockTintSource>, Block...)`; `BlockTintSource`: `color(BlockState)`, `colorInWorld(BlockState, BlockAndTintGetter, BlockPos)`, `colorAsTerrainParticle(…)`, `relevantProperties()`; `BlockAndTintGetter.getBlockTint(BlockPos, ColorResolver)`. Sodium 0.9.2 trzyma w `LevelColorCache` mapę dowolnych resolverów (`Reference2ReferenceOpenHashMap`) i wypełnia ją przy pierwszym użyciu;
- tryb Fast (runda 1): statyczne `ModelBlockRenderer.forceOpaque(boolean cutoutLeaves, BlockState)` (`!cutoutLeaves && block instanceof LeavesBlock`), pytane przez `SectionCompiler`, `MovingBlockFeatureRenderer`, domyślne `emitQuads` FRAPI i `BlockRenderer` Sodium; `LeavesBlock.skipRendering` (w trybie Fast ściany do innych liści); `QuadView.cullFace()` / `cullFace(Direction)` (renderery odrzucają quad po tej ścianie także po teście `cullTest`); `FallingParticlesLeavesBlock.spawnFallingLeavesParticle(Level, BlockPos, RandomSource)`;
- przebudowa: `Minecraft.levelExtractor.allChanged()` (czyści też pamięci odcieni `ClientLevel`), `levelExtractor.setSectionRangeDirty(minX, minY, minZ, maxX, maxY, maxZ)`, `levelRenderer.hasRenderedAllSections()`, `levelExtractor.countRenderedSections()` (Sodium nadpisuje oba).

**Zrzuty i arkusze** (`docs/wycinek/v3/`, 61 plików, ok. 22 MB; runda 1: zastąpione zrzutami rundy 1, opis niżej):
- `pory_roku.png`: arkusz, w którym wiersze to cztery zestawy, a kolumny sześć podsezonów; widok ogólny tablicy z drzewkiem, ścianą dębu wanilii, ścianą próbną z ekranem i płatem rośliny;
- `probes_<zestaw>_overview_<podsezon>.png` (także `fast_mid_summer` i `fast_mid_winter`) dla czterech zestawów;
- dla `vanilla_ss` i `sodium_ss` dodatkowo: drzewko (`tree_mid_spring`, `tree_mid_autumn`, `tree_mid_winter`, `tree_fast_mid_winter`), ekrany (`screen_mid_summer`, `screen_mid_winter`, `screen_fast_*`, `oak_screen_fast_mid_summer`), roślina z góry (`plants_mid_spring`, `plants_mid_summer`) i ściana próbna jesienią (`wall_mid_autumn`);
- liczby każdego przebiegu: `wyniki_<zestaw>.txt`.

W EARLY_SPRING widać na zrzutach to, czego potrzebuje grąd: bezlistne korony nad zielonym płatem geofitu. (Runda 1: granice faz próby nie są docelową fenologią. Liść próbny ma pełną zieloną koronę już od MID_SPRING (IV), a cel planu to okap bezlistny do IV/V ze szczytem zawilca w MID_SPRING pod nim; kluczowy miesiąc aspektu grądu to MID_SPRING, nie EARLY_SPRING. V7 i V13 ustawiają rozwój liści według gatunku.)

**Odstępstwa.**
- Kod prób jest w modzie gametestów, a nie w `src/client` za flagą. Nie trafia do pliku moda, a V7 i V13 przeniosą do `src/client` tylko to, co potrzebne (`SpeciesFoliageTint`, `ClientSeasonPhase`, owinięcie modelu).
- Model gałązek używa tekstury wanilii `dead_bush` przez id (próba). Własna tekstura gałązek z `tools/art` powstanie w V13.
- Oprócz `allChanged()` mierzone są też dwa łagodniejsze sposoby przebudowy (sekcje brudne, fala), bo `allChanged()` zrzuca widok. Plan przewidywał falę jako zapas R9.
- Tryb Fast z paczką Sodium: zamiast „liście pełne” sprawdzane jest „liść próbny jak liść wanilii”. W paczce liście wanilii też są wtedy wycięte (runda 1: tylko z Nvidium).
- Czasy klatek pochodzą z gametestu (jedna klatka na tick), a nie z normalnej gry.

**Wnioski dla V7 i V13 (domyślne, do potwierdzenia przy CP-A, `docs/00` G.8):**
- V3-a: krok koloru przebudowuje siatki przez oznaczenie sekcji jako brudnych falą od kamery, a nie przez `allChanged()`; 3 kroki na podsezon, z czego 1 pokrywa się ze zmianą podsezonu (z SS ta przebudowa jest już przebudową SS).
- V3-b: gałązki w trybie Fast są wycięte (runda 1: przez mixin na `forceOpaque`, z wewnętrznymi ścianami; `docs/00` G.8).
- V3-c: `createGeometryKey` modelu sezonowego zwraca `null`.
- V3-d: jeśli V10 potwierdzi w zwykłej grze z Sodium zacięcia ok. 1 s przy każdym kroku, kroki wewnątrz podsezonu znikają (1 krok na podsezon, razem z przebudową SS). (Runda 1: zacięć nie ma, zostają 3 kroki; bez pytania do użytkownika.)

**Testy.** `runClientGameTest -Pgametest=probes` w czterech zestawach (bez Sodium z SS i bez, z `-Poptimization` z SS i bez; PASS; część `board` powtórzona w trzech zestawach dla zrzutów, wyniki identyczne), `-Pgametest=slice -Psites=none -PnoSeasons -Pscales=gameplay -Pmodes=natural` bez bloków próbnych (PASS), `fastTest`, pełny zestaw `tools/dev/run-tests test`. Złoty test terenu bez zmian (V3 nie zmienia `src/main` ani generacji świata).

### Runda 1 poprawek po przeglądzie V3 (2026-10-16)

**Weryfikacja uwag.** Wszystkie 5 uwag blokujących i poważnych oraz 10 drobnych potwierdzone; fałszywych alarmów nie było. Trzy uwagi (blokująca o gałązkach w trybie Fast i dwie poważne o Nvidium) to ta sama sprawa i mają jedną poprawkę.
- **Nvidium (blokująca i poważna):** potwierdzone w logu (`renderer sodium_nvidium … Nvidium loaded true, enabled true`, GPU NVIDIA GeForce RTX 4060) i odczytem `Nvidium.IS_ENABLED`. Wszystkie zestawy „Sodium” z V3 były Sodium + Nvidium.
- **Gałązki w trybie Fast przy czystym Sodium:** potwierdzone przebiegiem z `-PnoNvidium` na kodzie V3: `FAIL sodium_ss fast MID_WINTER: screen sky 0.0%`. Gołe korony były pełnymi brązowymi kostkami. Przyczyna jak w uwadze: wanilijne `SectionCompiler`, domyślne `emitQuads` FRAPI i `BlockRenderer` Sodium pytają o statyczne `ModelBlockRenderer.forceOpaque(cutoutLeaves, state)` (`!cutoutLeaves && block instanceof LeavesBlock`). Sodium przenosi wtedy każdy quad bloku do materiału SOLID bez odcięcia alfa. Nvidium rysuje nieprzezroczysty teren z odcięciem 0,5, więc to on, a nie Sodium ani MoreCulling, zostawiał liście wycięte w trybie Fast (33% nieba).
- **Zacięcia R9 z Sodium (poważna):** nie powtórzyły się w dwóch sesjach rundy 1 (tabela niżej) ani w zwykłej grze. Sodium wylicza budżet budowy i wysyłania siatek na klatkę ze średniej odstępu między klatkami (`RenderSectionManager`: średnia krocząca z wagą 0,05, obcięta do 1–100 ms; `LimitedResourceBudget` z 0,3 tej średniej; sprawdzone w kodzie bajtowym Sodium 0.9.2). W gametest jedna klatka przypada na tick, który trwa 50–250 ms czasu zegara, więc budżet rośnie i jedna klatka może wziąć dużo pracy. Liczby V3 pochodziły najpewniej z obciążonej maszyny.
- **Tekstura liści (poważna):** potwierdzona. Kolumna 5 miała 2/16 pikseli (4 i 6: 8 i 4). Wanilia: dąb ma minimum 9/16, brzoza 5, akacja 4.
- **Drobne:** cienka skorupa korony w trybie Fast (70,7% nieba wobec 53% w Fancy), detektor końca przebudowy z Sodium (odpalał po 3 tickach, przed zacięciami), tolerancja koloru większa niż odstęp kroków (0,012–0,018), mieszanie biomów nieprzetestowane, środek fali liczony od nowa w każdym pierścieniu, tekstura i model gałązek, cząstki opadających liści zimą, fenologia faz próbnych i brak legendy na arkuszu. Wszystkie potwierdzone (w kodzie albo na zrzutach).

**Zmiany (tylko mod gametestów, `build.gradle`, `tools/art` i tekstura; kod `src/main` i `src/client` bez zmian).**
- **Gałązki wycięte w trybie Fast w każdym rendererze:** mixin moda gametestów `ModelBlockRendererMixin` na statycznym `ModelBlockRenderer.forceOpaque` zwraca `false` dla bloku, którego model sezonowy pokazuje wyciętą alternatywę (`SeasonalModel.keepCutOutInFastMode`, tylko liść próbny w fazie bezlistnej). Wszystkie trzy renderery (wanilia z Indigo, Sodium, Nvidium) pytają tę metodę, więc wystarcza jedno miejsce.
- **Wewnętrzne ściany gołej korony w trybie Fast:** w trybie Fast liście pomijają ściany do innych liści (`LeavesBlock.skipRendering`), a renderery odrzucają quad po jego ścianie odrzucania (`cullFace`) niezależnie od testu przekazanego do `emitQuads`. Dlatego w fazie gałązek model przepuszcza te ściany testem i zdejmuje im `cullFace` przekształceniem quada (`QuadEmitter.pushTransform`). Ekran gałązek ma w trybie Fast tyle samo nieba co w Fancy (52,9–53,1%, było 70,7%).
- **Cząstki:** liść próbny (`RenderProbes.ProbeLeavesBlock`) nie zrzuca opadających liści w fazie bezlistnej; `particleMaterial` modelu sezonowego bierze materiał pokazywanego modelu (gałązek).
- **Zestawy i znacznik renderera:** `-PnoNvidium` usuwa Nvidium z `-Poptimization`. Przebieg sam ustala renderer: `vanilla`, `sodium` albo `sodium_nvidium` (z `Nvidium.IS_ENABLED` przez refleksję, sprawdzone też w świecie), a w wierszu `run` zapisuje GPU. Pliki wyników i zrzutów mają ten znacznik. Zestawów jest teraz 6.
- **Kontrola koloru:** w krokach z liśćmi ściana próbna musi być bliżej bieżącego odcienia niż odcienia poprzedniego sprawdzanego kroku, gdy te dwa różnią się o ≥ 0,006 chromatyczności. Siatka nieprzebudowana nie przejdzie więc nawet przy krokach wewnątrz podsezonu.
- **Koniec przebudowy:** przebudowa trwa do ostatniej „wolnej” klatki (ponad 2 × najdłuższa klatka bez przebudowy, co najmniej 4 ms) albo ostatniej klatki z < 95% widoku z siatką, co najmniej do wydania wywołania (fala: 28 klatek). Test czeka na 20 kolejnych klatek bez wolnej. Czas podawany jest w klatkach, a nie w ms zegara gametestu. Fala bierze środek z początku fali (`ProbeSeason.cameraSection`, `markRingDirty(mc, center, ring)`).
- **Mieszanie biomów w generatorze siatek** (`biomeBlend`, część `rebuild`): 6 miejsc w świecie REAL przy granicach biomów, gdzie mieszanie (promień 2) różni się od koloru biomu środka o 3–4 na kanał (`#59ae30` obok `#55af31` i `#60ae33`). Stawia się tam liście próbne, a kamera obraca się na każde (wanilia kompiluje tylko rysowane sekcje). Źródło odcienia zapisuje kolor surowy widziany przez generator siatek (`RenderSectionRegion` wanilii albo wycinek świata Sodium z jego `LevelColorCache` i rozmyciem pudełkowym). Ten kolor musi się równać mieszaniu wanilii (`ClientLevel.calculateBlockTint`) z dokładnością do 2 na kanał i różnić się od koloru niezmieszanego. Wynik: różnica 0 bez Sodium, 1 z Sodium i z Nvidium.
- **Widok spod korony** (`tree_up_*`) w każdym podsezonie.
- **Tekstura `broadleaf_leaves`** (`tools/art`): kępki stawiane w najlepszym z 6 losowych miejsc, tam gdzie wiersze i kolumny są najpuściej (`texlib.stamp(candidates=6)`; przy `candidates = 1` losowanie jak dotąd, więc inne tekstury bez zmian). Ziarno wybrane z 32 według rozrzutu liczby pikseli w kolumnach i wierszach (`leaves_score`). Nowa reguła lintu `expect['min_line']` (na razie tylko liście liściaste): każda kolumna i wiersz ≥ 7/16. Wynik: kolumny i wiersze 8–14 pikseli, odchylenie 1,4 / 1,5 (było 2–14 i 3,4 / 1,9; dąb wanilii 1,0 / 1,4), 65% pikseli. Niebo przez ekran liści w lecie 18,0–18,1% (było 18,9%). Kępki przechodzą przez krawędzie kafla (zawijanie), a dwa tony bazowe idą z gładkiego szumu. Igły sosny mają kolumnę z 4/16 i nie zostały zmienione (brzoza i akacja wanilii też mają 4–5).

**Pomiary rundy 1** (2026-10-16; sesja 1: wszystkie części w 6 zestawach, sesja 2: część `rebuild` w 6 zestawach; maszyna bez innych zadań projektu, ale z przeglądarką i komunikatorem w tle). Kolor i gałązki (sesja 1):

| Zestaw | chromatyczność ściany (fazy z liśćmi) | do poprzedniego kroku | niebo: liście / gałązki | Fast: liście próbne / dąb / gałązki | roślina: wiosna / reszta | mieszanie biomów (największa różnica) |
|---|---|---|---|---|---|---|
| wanilia, z SS | 0,000–0,005 | 0,050–0,137 | 18,0% / 53,1% | 0,0% / 0,0% / 53,1% | 12,6% / 0,0% | 0 |
| wanilia, bez SS | 0,000–0,005 (kroki 22 i 23: 0,004 i 0,002) | 0,016–0,137 | 18,0% / 53,1% | 0,0% / 0,0% / 53,1% | 12,6% / 0,0% | 0 |
| Sodium, z SS | 0,003–0,008 | 0,050–0,134 | 18,1% / 52,9% | 0,0% / 0,0% / 52,9% | 12,4% / 0,0% | 1 |
| Sodium, bez SS | 0,003–0,008 (0,003 i 0,005) | 0,015–0,134 | 18,1% / 52,9% | 0,0% / 0,0% / 52,9% | 12,4% / 0,0% | 1 |
| Sodium + Nvidium, z SS | 0,001–0,005 | 0,049–0,137 | 18,0% / 52,9% | 34,6% / 33,0% / 52,9% | 12,4% / 0,0% | 1 |
| Sodium + Nvidium, bez SS | 0,001–0,005 (0,002 i 0,005) | 0,014–0,137 | 18,0% / 52,9% | 34,6% / 33,0% / 52,9% | 12,4% / 0,0% | 1 |

Surowe listowie `#77ab2f` w każdej porze i w każdym zestawie (bez podwójnego sezonowania).

Przebudowa siatek (REAL, `oak_hornbeam_1`, zasięg 12, 354 sekcje z siatką w widoku z Sodium i 407 bez; każdy sposób 3 razy; wartości: sesja 1 / sesja 2; „klatki” to klatki gametestu, jedna na tick; mediana klatki bez przebudowy 0,8–1,0 / 0,9–1,6 ms):

| Zestaw | `allChanged()`: najgorsza klatka; klatki > 50 ms (mediana); klatki < 95% widoku | sekcje brudne naraz: najgorsza klatka; klatki do końca | fala pierścieni: najgorsza klatka |
|---|---|---|---|
| wanilia, z SS | 25,6 / 35,3 ms; 0 / 0; 10 / 19 | 5,5 / 13,3 ms; 5 / 3 | 3,0 / 4,7 ms |
| wanilia, bez SS | 19,6 / 23,5 ms; 0 / 0; 7 / 9 | 5,1 / 6,6 ms; 5 / 6 | 2,8 / 4,4 ms |
| Sodium, z SS | 39,4 / 53,4 ms; 0 / 0; 10 / 10 | 6,5 / 30,4 ms; 15 / 17 | 4,4 / 32,8 ms |
| Sodium, bez SS | 47,8 / 74,2 ms; 0 / 1; 10 / 11 | 7,1 / 6,3 ms; 13 / 16 | 5,4 / 8,1 ms |
| Sodium + Nvidium, z SS | 77,1 / 149,6 ms; 1 / 1; 10 / 15 | 6,6 / 9,2 ms; 16 / 27 | 5,5 / 17,7 ms |
| Sodium + Nvidium, bez SS | 83,9 / 188,2 ms; 1 / 2; 10 / 12 | 8,5 / 7,3 ms; 0* / 22 | 6,2 / 648,9 ms** |

\* W sesji 1 trafiła się bez przebudowy klatka 54,9 ms, więc próg „wolnej” klatki wyniósł 110 ms, a koniec przebudowy wyszedł 0. \*\* Jedna z trzech fal w sesji 2 miała 3 klatki > 50 ms (najdłuższa 649 ms), dwie pozostałe najwyżej 7,5 ms. W 34 z 36 przebudów oznaczeniem sekcji (dwie sesje, 6 zestawów, naraz i falą) żadna klatka nie przekroczyła 33 ms. Przebudowa przez zmianę kroku koloru daje to samo co odpowiadający jej sposób (bez SS sekcje brudne, z SS `allChanged()` SS). Fala trwa 28 klatek (14 pierścieni co 2 ticki); sekcje brudne naraz kończą się po 3–6 klatkach bez Sodium i 13–27 z Sodium.

Zwykła gra (pomocnik przeglądu `V3Review` w kopii repozytorium w katalogu tymczasowym, poza projektem; świat REAL, to samo miejsce i kamera, limit 120 FPS, bez V-Sync, 10 s nagrania na przebudowę, każdy sposób 3 razy; pliki `zwykla_gra_*.txt`): czas pracy klatki (od jej początku do wyświetlenia) przy oznaczeniu sekcji najwyżej 6,3–16,3 ms bez Sodium, 4,6–22,1 ms z Sodium i 5,3–29,6 ms z Nvidium; przy `allChanged()` 1–2 klatki po 24–154 ms i 31–219 klatek (ok. 0,3–1,8 s) bez pełnego widoku. Odstępy między klatkami dochodziły w tej sesji do 40–80 ms także bez przebudowy (nagranie bazowe: najdłuższy odstęp 38–53 ms w 3 z 4 przebiegów), więc maszyna nie była w pełni spokojna. W spokojniejszej sesji przeglądu oznaczenie sekcji nie dało odstępu > 33 ms w żadnym rendererze.

Wnioski:
- Oznaczenie sekcji jako brudnych (naraz albo falą) nie daje zacięcia w żadnym rendererze. Pojedyncze wolne klatki (30–33 ms, raz 649 ms przy Nvidium) zdarzają się rzadko, a podobne odstępy są także bez przebudowy. Fala jest najgładsza bez Sodium (≤ 5 ms), z Sodium porównywalna z sekcjami naraz.
- `allChanged()` zostaje jedynym realnym kosztem: widok bez siatek przez ok. 10 klatek gametestu (0,3–1,8 s zwykłej gry) i 1–2 klatki do 50–190 ms z Sodium i Nvidium. Robi to SS przy każdej zmianie podsezonu, niezależnie od moda.
- V3-d nie wymaga decyzji: zostają 3 kroki koloru na podsezon z przebudową przez oznaczenie sekcji (falą). V10 potwierdza to w przelocie i sprawdza, czy wolna klatka przy fali z Nvidium się powtarza.
- Tryb Fast: liście próbne zachowują się jak liście wanilii w tym samym rendererze (pełne w wanilii i w czystym Sodium, wycięte z Nvidium); gałązki są wycięte i tak gęste jak w Fancy we wszystkich trzech.

**Zrzuty i arkusze** (`docs/wycinek/v3/`, zastępują zrzuty V3; 61 plików, ok. 17 MB):
- `pory_roku.png`: legenda (A drzewko próbne, B ściana dębu wanilii barwiona przez SS, C ściana liści próbnych z ekranem za nią, D płat rośliny próbnej) i 6 zestawów × 6 podsezonów + tryb Fast w I;
- `galazki_zima.png`: gałązki zimowe próbne w trzech rendererach z SS (korona w Fancy i Fast, widok spod korony zimą i latem, ekran gałązek w trybie Fast); tekstura zastępcza `dead_bush`;
- `liscie.png`: ekrany liści próbnych i dębu wanilii w lecie, ściana próbna w X w trzech rendererach, kafle 3 × 3 tekstury przed i po poprawce;
- `probes_<zestaw>_{overview,tree,screen}_fast_mid_winter.png` dla 6 zestawów; dla `vanilla_ss`, `sodium_ss` i `sodium_nvidium_ss` także `tree_mid_winter`, `tree_up_mid_winter`, `tree_up_mid_spring`, `screen_mid_summer`, `oak_screen_mid_summer`, `wall_mid_autumn`, `plants_mid_spring`, `overview_mid_autumn`;
- `wyniki_<zestaw>_sesja1.txt`, `wyniki_<zestaw>_sesja2.txt`, `zwykla_gra_*.txt`.

**Wnioski dla V7 i V13 (domyślne, `docs/00` G.8):**
- V3-b (zmienione): gałązki wycięte w trybie Fast przez mixin na `ModelBlockRenderer.forceOpaque` (dla bloków liści gatunku w fazie bezlistnej) i z wewnętrznymi ścianami (zdjęty `cullFace` ścian do liści). Samo pominięcie transformacji FRAPI nie działa w Sodium.
- V3-e: model sezonowy bierze materiał cząstek z pokazywanego modelu; w fazie bezlistnej brak opadających liści, a dąb i grab (marcescencja) zrzucają tylko rzadkie suche brązowe liście (V13).
- V3-f: tekstura gałązek każdego gatunku bez kierunku „w górę”, z gałązkami przechodzącymi przez krawędzie bloku, szarobrązowa według gatunku (zimowe pędy dębu, grabu i brzozy są szarobrązowe do ciemnoczerwonobrązowych) i mniej nasycona od kory; model z kilkoma obróconymi wariantami zamiast jednego. Obecne gałązki (`dead_bush`, `cube_all`) to tylko zastępstwo: na górnych i dolnych ścianach dają poziome smugi, a całość czyta się jak suchy krzak. Widok spod korony jest w gametest.
- V3-g: tekstura liści liściastych z kępkami rozłożonymi po wierszach i kolumnach (lint `min_line`).
- V3-h: każda próba renderu (V7, V10, V13) w trzech rendererach: wanilia, czysty Sodium (`-PnoNvidium`), Sodium + Nvidium.
- V3-i: granice faz próbnych nie są fenologią docelową; V7 i V13 ustawiają rozwój liści według gatunku (brzoza i grab koniec MID_SPRING, dąb LATE_SPRING) i jaśniejszy, bardziej nasycony cel wiosenny (próbne `#9cc652` wygląda oliwkowo).
- V3-a (uzupełnione): fala bierze środek z początku fali.

**Testy rundy 1.** `runClientGameTest -Pgametest=probes` w 6 zestawach (wanilia, `-Poptimization -PnoNvidium`, `-Poptimization`, każdy z SS i z `-PnoSeasons`; PASS w sesji 1), część `rebuild` jeszcze raz w 6 zestawach (sesja 2, PASS); zwykła gra: 4 przebiegi pomocnika przeglądu; `compileGametestJava`, `fastTest`, `python tools/art/check.py` (15 tekstur, lint z `min_line`, autotest), pełny zestaw `tools/dev/run-tests test`. Złoty test terenu bez zmian (runda nie zmienia generacji świata).

## Stan po V1b (2026-10-16)

**Punkt kontrolny CP-A** (odpowiedź użytkownika 2026-10-16, `docs/00` G.9): styl tekstur przyjęty jako baza, ale „trochę za ostry”; cienki pień (model po V2b, V2-l (b)) przyjęty; decyzje „domyślne, do potwierdzenia” z terminem CP-A przyjęte. Rekomendacje strony CP-A przyjęte domyślnie i zrobione w tym kroku. Pasek kolanka i dziury w narożnikach czapki (skrzydełka +8 quadów) rozstrzyga V10 po pomiarze kosztu renderu.

**Zrobione.**
- **Miara „ostrości”** (`texlib.local_contrast`, `tools/art/measure.py`): średnia i 90. percentyl różnic luminancji (Rec. 709, 0–255) wszystkich par sąsiednich pikseli w 4-sąsiedztwie na torusie (oba widoczne), udział pikseli w ciemnych liniach jednopikselowych (piksel o ≥ 24 ciemniejszy od obu sąsiadów z lewej i prawej albo z góry i z dołu) i udział pikseli samotnych (luminancja inna niż u wszystkich 4 sąsiadów). `measure.py` mierzy nasze tekstury albo dowolne pliki PNG; wanilię 26.3 zmierzyłem na plikach wypakowanych z `minecraft-client.jar` (pamięć Loom) do scratchpada, poza repozytorium (do repozytorium trafiły tylko liczby).
- **Lint** (`lint.py`, `CONTRAST`): kora: średnia ≤ 13, p90 ≤ 33, linie ≤ 4% (ok. górny kwartyl 9 naturalnych kłód wanilii); liście: ≤ 29, ≤ 61, ≤ 16% (mediana 7 szarych liści wanilii przed tintem); rośliny: ≤ 23, ≤ 50, ≤ 7% (mediana krzaka, paproci, trawy i krzewu jagód, dla p90 trzeci kwartyl). Przepis może podać własne progi (`expect['contrast']`). Reguła wchodzi też do kary wyboru ziarna (`best_of`), więc wybierane są gładsze ziarna. Tabela lintu pokazuje kontrast (`steps <średnia>/<p90>  lines`).
- **Przepisy** (`recipes.py`; każdy ton ma własną luminancję, `tones_at` i parametr `lums`):
  - **bruzdy jako miękki rowek** (dolna kora sosny, dąb, lipa, kora zastępcza): rdzeń w tonie średnio ciemnym, ściana z lewej w tonie pośrednim, z prawej ciemniejsze z dwóch lic (`groove_sides`), bez jasnej krawędzi obok najciemniejszego tonu; jasny ton tylko pod poprzecznym pęknięciem; tony użyte przez 1–3 piksele łączą się z sąsiednim (`fold_rare`). Sosna: rdzeń czerwonobrązowy 78 (było 46), ściana 88, lica 102 / 113 (było 103–132 z jasną krawędzią); bruzda czyta się barwą i 3 px przejścia, a nie skokiem 60–70 luminancji. Dąb: bruzda 66 (było 58), lica 92–108 (było 85–126);
  - **„lustro”**: nasycenie 0,58 (było 0,7), łuski 104 / 116 / 128 (było 112 / 117 / 122: mniej płaskie), szczelina 90 (było 80), blade łuski szarsze (0,3), średni odcień 23,5°;
  - **lipa** ciemniejsza i brązowa (średnia luminancja 96, nasycenie 0,26), **kora zastępcza** jesionu i klonu jaśniejsza i szara (129, nasycenie 0,07): ΔE 15,1 (było ok. 9; lint `distinct` ≥ 15, było ≥ 8), z daleka (`dystans.png`) ciemny brązowoszary pień wobec bladoszarego;
  - **igły**: każdy pęd w najlepszym z 6 losowych miejsc (najpustsze wiersze i kolumny, jak stemple liści), rampa 104–184 (było 64–210), wybór z 32 ziaren według rozrzutu, każdy wiersz i kolumna ≥ 6 z 16 pikseli; **liście liściaste** rampa 100–204 (było 88–214); **borówka** rampa 92–180 (było 52–194), cień liścia w tonie pędu;
  - **grab i buk** w tonach o mniejszych krokach (krótkie spękania grabu 98 zamiast 80, „wąsy” buka 146 zamiast 136) i **drugie warianty** `hornbeam_bark_2`, `beech_bark_2` (wspólne wiersze krawędzi z bazą: grab dzieli granice „muskułów” w pasie 3 wierszy, buk pola plam i glonów przez `edge_locked`; porosty, blizny, smugi i spękania tylko w środkowych wierszach);
  - **kora przejściowa sosny** `scots_pine_transition_bark` i `_2` (V1-i): szarobrązowe płyty dolnej kory na rudej korze „lustra”, rude pasy w bruzdach, ok. 1/4 płyt złuszczona, kilka bladych łusek; zbudowana na strukturze dolnej kory (`params['structure_of']`, `build_plated`), więc bruzdy przechodzą przez granicę bloku z dolną korą w obu kolejnościach (autotest); z „lustrem” granica zostaje (inna struktura);
  - budowa podstaw wariantów jest zapamiętana (`recipes.once`, klucz: nazwa i obiekt palet): 19 tekstur ok. 23 s (przed V1b 15 tekstur ok. 25 s).
- **Blok `scots_pine_transition_trunk`** (`TreeTrunk.SCOTS_PINE_TRANSITION`, na końcu enuma; „Scots Pine Transition Trunk” / „Przejściowy pień sosny zwyczajnej”, łup jak sosna: `spruce_log`, słoje `spruce_log_top`, tagi `scots_pine_logs`, `trunks`): zarezerwowany przed CP-B, migawka rejestru przegenerowana świadomie (`-PwriteRegistrySnapshot`: +1 blok z 84 stanami, +1 przedmiot; nic innego się nie zmieniło). Strefę przejścia ustawi planer drzew (V6); sosna tablicy próbek ma 2 bloki kory przejściowej pod „lustrem” (`TrunkSamples.TRANSITION_BLOCKS`). Modele grabu i buka losują teraz 2 warianty kory, pnia przejściowego też 2.
- **Autotest** (`selftest.py`): miara na teksturze płaskiej i szachownicy, lint łapie ciemne bruzdy jednopikselowe w stylu V1 i za ostrą korę i liście, bruzdy kory przejściowej w skrajnych wierszach leżą tam, gdzie bruzdy dolnej kory.
- **Arkusze** `docs/wycinek/grafika/`: `arkusz_8x.png`, `kafle_3x3.png`, `pnie.png` (sosna: 6 bloków dolnej kory, 2 przejściowej, 4 „lustra”), `pnie_cienkie.png`, `dystans.png` po V1b; przed V1b: `arkusz_8x_przed.png`, `kafle_3x3_przed.png` (stan z `f5d4170`, piksel w piksel jak arkusze w repozytorium przed V1b).

**Pomiary** (`python tools/art/measure.py`; średnia / p90 różnic luminancji sąsiednich pikseli / udział pikseli w ciemnych liniach jednopikselowych):

| Tekstura | Przed V1b | Po V1b |
|---|---|---|
| `scots_pine_bark`; `_2` | 17,9 / 57,0 / 19,1%; 20,2 / 61,9 / 21,1% | 7,2 / 25,2 / 1,6%; 7,8 / 25,2 / 2,0% |
| `scots_pine_upper_bark`; `_2` | 6,2 / 23,5 / 4,7%; 5,5 / 19,5 / 3,5% | 6,0 / 22,2 / 3,1%; 6,9 / 24,6 / 3,9% |
| `scots_pine_transition_bark`; `_2` | — | 4,6 / 14,2 / 0%; 5,2 / 14,2 / 0% |
| `oak_bark`; `_2` | 22,0 / 54,5 / 25,8%; 21,9 / 54,5 / 25,8% | 9,2 / 26,5 / 0%; 9,0 / 26,5 / 0% |
| `hornbeam_bark`; `_2` | 7,0 / 19,6 / 0,8%; — | 4,5 / 18,9 / 0%; 4,4 / 18,9 / 0% |
| `linden_bark`, `_2`, `_3` | 23,6–24,4 / 67,3 / 19,5–21,5% | 9,9–10,3 / 22,1 / 1,2–1,6% |
| `broadleaf_bark` | 20,3 / 60,6 / 17,2% | 6,6 / 15,4 / 0,4% |
| `beech_bark`; `_2` | 7,4 / 14,9 / 3,1%; — | 5,0 / 11,9 / 1,6%; 4,6 / 8,9 / 2,3% |
| `scots_pine_leaves` | 29,1 / 110,0 / 12,0% | 17,7 / 60,0 / 6,2% |
| `broadleaf_leaves` | 27,4 / 62,0 / 11,4% | 22,8 / 52,0 / 11,4% |
| `bilberry` | 31,4 / 70,9 / 15,8% | 18,8 / 44,0 / 2,6% |

Wanilia 26.3 (te same miary): kora 9 naturalnych kłód (akacja, wiśnia, ciemny dąb, dżungla, namorzyn, dąb, blady dąb, topola, świerk) średnia 7,2–19,8 (kwartyle 8,1 / 9,1 / 13,1), p90 15,7–48,5 (19,6 / 21,8 / 32,6), linie 0–21,1% (0,4 / 1,2 / 3,1%); `oak_log` 19,8 / 48,5 / 21,1%, `spruce_log` 9,1 / 21,8 / 0,4%, `jungle_log` 12,3 / 26,7 / 3,1%, `birch_log` 29,4 / 95,1 / 14,5% (czarne przetchlinki, poza progami). Liście szare (dąb, brzoza, świerk, dżungla, akacja, ciemny dąb, namorzyn): średnia 19,9–43,1 (mediana 28,3), p90 42,9–74,4 (60,9), linie 1,4–25,7% (16,2%); `spruce_leaves` 43,1 / 72,0 / 16,2%. Rośliny (krzak, paproć, trawa, krzew jagód 1–2): 18,0–27,2 (22,3), 41,0–53,7 (47,5, q3 49,9), 3,6–13,1% (6,7%). Nasza kora po V1b: średnia 4,4–10,3 (mediana 7,2), p90 8,9–26,5 (22,1), linie 0–3,9%: w okolicy mediany wanilii; dąb i sosna jak `jungle_log` i `spruce_log`, wyraźnie łagodniej niż `oak_log`.

Inne miary lintu po V1b: sosna dolna luminancja 97,7, odchylenie 12,2–12,5 (było 28–29), ziarno 2,6–3,8; dąb 83,7–84,0, ΔE 7,4 od `oak_log` (lint ≤ 10); lipa 96,2–96,7; kora zastępcza 128,6; grab 116,4–116,5; buk 164,7–164,8; kora przejściowa 106,8–107,2, odcień 22,5°; „lustro” 115,8–118,6, odcień 23,5°; pierścienie na cienkich pniach najwyżej 25,7 (r1, „lustro”; próg 32), poza „lustrem” ≤ 16.

**Odstępstwa i uwagi.**
- „Drugi wariant kory dębu i lipy” z rekomendacji CP-A już był (`oak_bark_2` i `linden_bark_2` od rundy 1 V1, `linden_bark_3` od rundy 1 V2); zostały wygładzone. Dodałem za to drugie warianty grabu i buka (otwarte pytanie V1-b), żeby każdy własny pień miał co najmniej 2 warianty (`docs/00` G.10 V1b-f).
- Kora przejściowa to osobny blok, nie wartość właściwości (G.10 V1b-g), spójnie z PW-11.
- Kontrast tekstur pod tintem liczę na szarości (przed tintem), jak wanilię w pliku; tint zmniejsza różnice luminancji proporcjonalnie do jasności koloru.
- Rysunek pojedynczych pędów igieł jest po wygładzeniu słabszy (równiejsza „mozaika” igieł zamiast ciemnych gałązek z jasnymi kępami); do oceny przy CP-B na drzewach.
- Grab, buk i „lustro” już przed V1b miały kontrast w okolicy wanilii; zmiany tam są małe (tony spękań i blizn, nasycenie i rozrzut jasności „lustra”).
- `check.py` (`checkArt`) trwa teraz ok. 1–2 min (3 pełne budowy: wynik, lint i autotest z dwiema budowami); przed V1b podobnie (ok. 25 s na budowę).

**Zrzuty z gry** (`docs/wycinek/pnie/`): tablica próbek po V1b w trzech rendererach (`runClientGameTest -Pgametest=slice -Psites=samples`, bez flag, `-Poptimization -PnoNvidium`, `-Poptimization`; PASS w każdym: migawka rejestru w grze 55 wierszy, 924 stany pni = 11 bloków × 84, modele i cząsteczki wszystkich stanów), pliki `samples_<renderer>_*.png` podmienione (39 wanilii, 14 czystego Sodium, 3 Sodium + Nvidium); dla porównania stan przed V1b: `v1b_pine_przed.png`, `v1b_columns_przed.png`, `v1b_overview_przed.png`, `v1b_far_pine_48_przed.png` (te same kadry co `samples_vanilla_pine`, `_columns`, `_overview`, `_far_pine_48`). Sosna tablicy ma teraz 2 bloki kory przejściowej pod „lustrem”.

**Testy.** `python tools/art/check.py` (PASS: 19 tekstur zgodnych z generatorem, lint z regułą kontrastu, `CREDITS.md`, autotest z nowymi przypadkami), `./gradlew runDatagen` i `checkDatagen` (398 plików bez zmian po drugim przebiegu), `RegistrySnapshotTest` z `-PwriteRegistrySnapshot` (świadomie: +`scots_pine_transition_trunk`), `fastTest` (PASS), tablica próbek w trzech rendererach (PASS), pełny zestaw `tools/dev/run-tests test` (PASS, 1665 s). Złoty test bez zmian (V1b nie zmienia terenu).
