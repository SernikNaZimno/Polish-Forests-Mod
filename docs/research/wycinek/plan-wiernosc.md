# Pionowy wycinek „Grąd i bór świeży”: plan wykonawczy z perspektywy wierności przyrodniczej

Wersja: 2026-10-14. Wariant planu: **wierność przyrodnicza**. Wycinek ma być wzorcem jakości dla całej Polski, więc ten plan stawia zgodność z danymi (struktura drzewostanu, runo z fenologią, martwe drewno według norm, fauna z zachowaniami) przed czasem. Plan nie zmienia repozytorium. Po akceptacji trafi do `docs/04-wycinek.md`, a decyzje do `docs/00-decyzje-do-podjecia.md` (nowa sekcja G).

Podstawa:
- `docs/BRIEF.md`, `docs/01-architektura.md` §5, §6, §8, `docs/00-decyzje-do-podjecia.md`, `docs/03-m2-biomy.md` §1 (Z1–Z10), §8.4–8.8, §9;
- `docs/research/04`, `06` (§3.1, §3.5, §4, §6, §9), `07`, `08` (§7, §9);
- raporty wycinka w `scratchpad/slice/`: `research-ekologia.md`, `research-technika-drzew.md`, `research-fauna-grafika.md`.

Oznaczenia liczb jak w raportach: **[Ź]** ze źródła, **[L]** z `docs/research/`, **[O]** wartość orientacyjna lub przeliczenie.

---

## 0. Zasada przewodnia i co z niej wynika

**Wycinek mierzymy jak las, nie jak zrzut ekranu.** Każda warstwa ma liczbowy cel ze źródła (karta norm, §3). Ten sam test w grze, który działa jak powierzchnia próbna inwentaryzacji (WISL), mierzy go w obu skalach i obu trybach. Zrzuty i test w grze u użytkownika oceniają wygląd. Liczby oceniają wierność.

Wynikają z tego cztery reguły kolejności:
1. **Najpierw miara, potem las.** Narzędzie inwentaryzacji (V1) powstaje przed drzewami i od razu mierzy stan M2. Każdy następny krok pokazuje postęp wobec norm.
2. **Od okapu w dół.** Okap decyduje o świetle, a od światła zależy runo, podrost, luki i spawn zwierząt. Dlatego kolejność to: drzewa i drzewostan, potem gleba, runo z fenologią, martwe drewno i grzyby, na końcu fauna zależna od struktury (wiewiórka na pniach, sarna w gęstwinie).
3. **Las naturalny i gospodarczy od początku razem.** Struktura drzewostanu z jedną klasą wieku nie byłaby wzorcem dla Polski, gdzie 85% lasów to drzewostany gospodarcze w różnych klasach wieku (D9). Stąd minimalna mapa drzewostanów już w wycinku (V6).
4. **Ekoton to część wycinka.** Grąd i bór świeży stykają się w naturze przez ciąg Bśw → BMśw → LMśw → Lśw (raport ekologii §8). Wzorzec Z10 trzeba więc pokazać na prawdziwym ciągu siedlisk, a nie na sztucznej granicy dwóch lasów.

Grafika i fauna idą równoległymi torami w osobnych worktree (A5), żeby długi tor drzew nie blokował wczesnego punktu kontrolnego grafiki.

---

## 1. Zakres

### 1.1 Wchodzi

**Lasy i siedliska (rdzeń):**
- `oak_hornbeam_forest` (grąd): Lśw i Lw z podzespołami. Lw to grąd niski (stachyetosum: jesion, czyściec, kokorycz). Lśw to grąd typowy. Przy LMśw działa grąd wysoki (calamagrostietosum: trzcinnik, konwalia, sosna).
- `fresh_pine_forest` (bór świeży, Bśw).
- Warianty regionalne z pola O i P chunka, bez nowych biomów i bez zmian klasyfikatora (W2):
  - grąd: Galio sylvatici-Carpinetum na zachodzie (buk w a1, bluszcz, brak naturalnego świerka) i Tilio-Carpinetum w centrum i na wschodzie (świerk w a1 na NE, turzyca orzęsiona);
  - bór: Leucobryo-Pinetum na zachodzie i Peucedano-Pinetum na wschodzie (jałowiec, gorysz, świerk w a2).
- **Pas styku (W1):** `mixed_pine_forest` (BMśw) i `mixed_forest` (LMśw) dostają nowe drzewa i drzewostan z mapy drzewostanów. Runo dostają w wersji przejściowej z gatunkami wskaźnikowymi: w BMśw orlica, turzyca pigułkowata i dąb w podszycie, w LMśw kosmatka, konwalia i leszczyna.

**Drzewa (część M3):**
- pnie o zmiennej grubości, wariant D z raportu techniki: `TrunkBlock` z właściwościami `radius` 1–7, `parent` i `waterlogged`, plus pełna kłoda i dysk 2 × 2 dla olbrzymów;
- generator `TreeShapePlan` z archetypami i sześcioma stadiami rozwojowymi (uprawa, młodnik, drągowina, dojrzewający, dojrzały, starodrzew) według allometrii z `06 §9.2`;
- gatunki: sosna, brzoza brodawkowata, świerk, dąb szypułkowy (w borze także jako domieszka dębu bezszypułkowego), grab, lipa drobnolistna, klon zwyczajny i jesion;
- krzewy i podrost: leszczyna, jarząb, kruszyna, jałowiec, trzmielina oraz młode drzewa gatunków drzewostanu;
- liście gatunków z własnym kolorem sezonowym (B6) i zimowym stanem bezlistnym po stronie klienta, z marcescencją dębu, grabu i młodych buków (W9);
- sadzonki (`TreeGrower`) z tymi samymi kluczami `polishforests:tree/<gatunek>`.

**Drzewostan:**
- minimalna mapa drzewostanów: wydzielenia z naturalnością (suwak D9), klasą wieku, składem według ZHL, bonitacją i zwarciem (W3);
- las naturalny: wielowiekowy, wielopiętrowy (a1, a2, podrost, podszyt), z lukami 18–45 bloków i olbrzymami;
- las gospodarczy: jednowiekowy. Rzędy w uprawach i młodnikach, rozstaw po trzebieżach, zręby z biogrupami (tylko bór), gniazda rębni III (tylko grąd) i pniaki. W trybie D dochodzi pinetyzacja grądu (W5).

**Gleby (część M4, W15):** własne bloki profilu:
- bielica: poziom wymywania Ees (bielony piasek) i poziom Bfe (rdzawy);
- gleba rdzawa;
- gleba brunatna z próchnicą mull;
- ściółki: igliwie (mor) i ściółka liściasta grądu, która latem prawie znika.

Teren, wysokości i heightmapy bez zmian.

**Runo i mchy (część M4):**
- geofity grądu: zawilec gajowy, przylaszczka, ziarnopłon, kokorycz pusta, złoć, gajowiec, gwiazdnica wielkokwiatowa, marzanka, kopytnik, fiołek leśny (konwalia zostaje wanilijna);
- aspekt letni grądu;
- runo boru: borówka czarna z owocami, brusznica, wrzos, śmiałek pogięty, pszeniec, orlica (2 bloki), konwalijka;
- mchy: rokietnik, widłoząb, poduszki bielistki, płaty chrobotków w suchszych miejscach;
- gatunki luk: malina, jeżyna, wierzbówka, trzcinnik.

Pokrycie warstw liczone w skali Braun-Blanqueta i zależne od światła pod okapem.

**Fenologia:**
- aspekt wiosenny grądu od Early do Late Spring, zanik w Early Summer;
- przebarwianie i opad liści według gatunku;
- owoce borówki i brusznicy oraz wrzos w kwiecie;
- grubość ściółki zależna od sezonu;
- przesunięcie regionalne: zachód 5–10 dni wcześniej, NE 2–3 tygodnie później [L 08 §7.3], liczone z pola O i wysokości.

Wszystko działa z Serene Seasons i bez niego (B1, `FallbackCalendar`).

**Martwe drewno według norm:** kłody w 3 stadiach rozkładu, posusz, złomy, wykroty z talerzem i dołem, pniaki (tylko w lesie gospodarczym) i gałęziówka. Forma śmierci zależy od gatunku. Objętość jest liczona i kalibrowana do m³/ha wariantu (W13). Dochodzą huby dopasowane do promienia pnia, opieńka na pniakach i siewki na kłodach.

**Grzyby (W14):** 8–10 gatunków naziemnych pojawia się dynamicznie w sezonie, po deszczu, przy drzewie-partnerze, z podstawową jadalnością i trującością. Huby i nadrzewne są w martwym drewnie.

**Fauna (część M6, F4 a):**
- pełne encje (GeckoLib i SmartBrainLib 2.0.3): sarna, dzik, wiewiórka i sójka;
- żaba trawna jako `frog_variant`;
- dzięcioł duży jako dźwięk oraz kuźnie z szyszek na pniakach;
- mrowiska rudnicy;
- buchtowiska (C9);
- pejzaż dźwiękowy ptaków (W16) według biomu, podsezonu i pory dnia.

**Grafika:** proceduralny pipeline `tools/art` z lintem stylu i `checkArt` oraz `tools/fauna` (generator `.geo.json`). Razem ok. 85 tekstur, punkt kontrolny grafiki po pierwszych 8 teksturach i sarnie (F6).

**Zestawy drewna (D1):** pełny zestaw sosny i zestawy „lite” grabu i lipy. Dąb, brzoza i świerk biorą zestawy i łup z wanilii (D2).

**Miara:**
- test inwentaryzacji: powierzchnie próbne 1 ha w obu skalach, trybach N i D, z SS i bez;
- karta norm;
- miejsca wzorcowe i transekt ekotonu w gametestach;
- pomiary budżetów.

### 1.2 Nie wchodzi (zostaje na M3–M9)

| Poza wycinkiem | Dokąd | Uwagi |
|---|---|---|
| pozostałe 32 biomy z nowymi warstwami | M3, M4 | zostają na zastępstwach M2, a ekotony wycinka biorą ich zastępstwa jako drugą stronę |
| pozostałe gatunki drzew (buk, jodła, olsze, wierzby, topole, wiąz, jawor, modrzew, cis, kosodrzewina) | M3 | wyjątek: buk jako domieszka grądu zachodniego, generowany archetypem liściastym z teksturą zastępczą. Decyzja W2 może go wyłączyć |
| dynamika lasu (D11): wzrost w czasie, sukcesja, wiatrołomy, kornik | M8 | zostają tylko sadzonki rosnące w drzewo z generatora |
| infrastruktura leśna: drogi wywozowe, słupki, ambony, ogrodzenia upraw | M8 | linie oddziałowe tylko jeśli W6 = (b) |
| jeleń, wilk, łoś, bóbr, borsuk, lis własny, ptaki jako encje poza sójką | M6, M7 | lis zostaje wanilijny |
| pełny katalog grzybów i grzybobranie z efektami (D4) | M4 | |
| torf, trzciny, pałki, mchy torfowisk | M4 | |
| gatunki obce (D10): dąb czerwony, robinia, niecierpek drobnokwiatowy | M8 | |
| typ zimy (B5), człon regionalny temperatury | M4 | |
| ziołorośla górskie w GAMEPLAY (M2-24 b) | M4 | wycinek nie dotyka gór. Proponuję zmienić „przy wycinku” na „w M4” (decyzja W18) |
| kleszcze, komary (C11), łowiectwo (C8, C12) | M6+ | |
| zgodność z Distant Horizons, Continuity | M9 | ryzyko odnotowane (R11) |

### 1.3 Zasady projektu w wycinku

- **Z1–Z9.** Nowe warstwy czytają tylko `ChunkHabitats` (kod, wierzch, woda, O, P, maska struktur) i własne szumy. Wycinek nie dodaje nowych próbek modelu (Z6), więc BIOMES i TERRAIN się nie zmieniają.
  - Każde nowe pole szumu dostaje **nową sól**, np. `feature.stand_map.cells`, `feature.stand_map.age`, `feature.tree.shape`, `feature.natural_gaps`, `feature.deadwood.objects`, `feature.ground_layer.geophytes`, `feature.fungi.flush`. Literałów istniejących soli nie ruszamy.
  - Nowe stałe enumów (`Species`, nowe enumy bloków) dopisujemy tylko na końcu. `HabitatBiome` i `Association` bez zmian (W2 b).
  - Kopia `landscape/m1/` jest nietykalna. Złoty test się nie zmienia, bo porównuje pola `ColumnSample` i wierzch, a nie materiały. V9 sprawdza to jawnie.
- **Z10 (płynne przejścia).** Wszystkie nowe palety wybierają reguły po kodzie ekotonu (`Ecotone`). Wyjątek to granice wydzieleń gospodarczych, bo ich geometria jest dziełem człowieka (W7a).
- **Obie skale (Z7).** Rozmiary związane z drzewem są 1:1 w obu skalach: wysokość, korona, rozstaw, luka, kłoda. Rozmiary związane z krajobrazem rosną z k: wydzielenia, oddziały, pasy ekotonu (W10).
- **Tryby N i D.** W trybie N las jest domyślnie w pełni naturalny, w trybie D obowiązuje domyślnie 85/15 (W4). Każdy krok testujemy w obu trybach.
- **Budżety:**
  - cały chunk ≤ 48 ms (M2-25 a) zostaje twardy w każdym obszarze, skali i trybie;
  - BIOMES i TERRAIN bez zmian;
  - FEATURES i LIGHT dostają nowe budżety w V8 (W12);
  - FPS, MSPT fauny i liczba stanów bloków mają osobne progi.
- **Praca agentów:**
  - A1–A5, A3a: commit i push po każdej średniej zmianie, WIP przy `fastTest`;
  - B1: wysokie ryzyko (mixiny, rejestracja bloków i encji, datagen biomów, plan powierzchni, generator drzew) dostaje 2 recenzentów i do 2 rund;
  - B3: problem, który przetrwał 2 rundy, idzie do użytkownika jako warianty.

---

## 2. Szacunki

**Kalibracja tempa.** W M2 kroki S5–S10 (10 kroków z rundami recenzji) zajęły ok. 7 dni kalendarzowych. Średni krok wymaga ok. 0,5–1,5 dnia pracy agentów, a krok taki jak S7 albo S8b z dwiema rundami ok. 1,5–2,5 dnia. Szacunki niżej są w dniach pracy agentów przy tym tempie, razem z recenzjami według B1 i pełnymi przebiegami testów. Bez nich liczą się czekanie na użytkownika w punktach kontrolnych i czas maszyny zajętej grą. Raporty badawcze liczyły w godzinach z innym założeniem: technika drzew 16–26 dni, fauna 7–10 dni, grafika 4–6 dni. Te liczby są tu przeliczone na tempo M2 i powiększone o kroki, których raporty nie liczyły: inwentaryzację, mapę drzewostanów, gleby, fenologię, grzyby i dźwięki.

**Razem: ok. 40–64 dni pracy agentów (V0–V20).** Trzy tory w osobnych worktree (A5) skracają kalendarz do ok. 30–46 dni.

---

## 3. Karta norm (definicja ukończenia wierności)

Test inwentaryzacji (V1) mierzy każdą pozycję na powierzchniach próbnych 1 ha. W REAL to 100 × 100 bloków. W GAMEPLAY powierzchnia ma tyle samo bloków, bo drzewa są 1:1 (W10). Pomiar idzie w 3 ziarnach, w obu skalach i trybach. Tolerancja to ±20% albo zakres podany niżej, chyba że wiersz mówi inaczej. Wartości z `[O]` po pierwszym pomiarze może skorygować użytkownik (PK-W3).

| # | Miara | Grąd naturalny | Grąd gospodarczy | Bór świeży gospodarczy | Bór naturalny | Źródło |
|---|---|---|---|---|---|---|
| N1 | Drzewa d1,3 ≥ 8 cm na ha (na chunk) | 588 ± 53 (≈ 15) | 300–600 według klasy wieku | 600–900 (50 lat), 300–400 (100 lat), czyli 8–23 na chunk | różnowiekowe kępy, 300–700 | [Ź Miścicki 2016; L 06 §6.1] |
| N2 | Piętra | a1 3–5 / chunk, 28–40 bl.; a2 6–9 / chunk, 15–22 bl. | a1 22–30 bl., a2 słabe | a1 20–28 bl.; a2 0–20% | a1 + kępy młodszych | [L 06 §3.5; Ź Janów] |
| N3 | Skład a1 (udział pni) | Db 20–50, Lp 10–40, Kl/Js 5–15, Św 0–30 (NE), Bk 0–30 (W) | ZHL Lśw: Db 50–80 + domieszki; pinetyzacja: So a1 (W5) | So 80–95, Brz 5–15, Db/Św domieszką | jak gospodarczy + Brz w lukach | [L 06 §3.5, §6.2] |
| N4 | Zwarcie koron (kolumny z liśćmi nad gruntem) | 80–95% | 75–90% | 60–80% | 50–75% | [L 06 §4.1; Ź Janów 0,6] |
| N5 | Luki | 18–45 bl. średnicy, 3–8% powierzchni [O] | gniazda 0,1–0,5 ha·k² (rębnia III) | zręby (W gospodarczym) z 1–2 biogrupami | luki z Brz i So | [Ź BPN luki; L 06 §6.1] |
| N6 | Odnowienie w lukach / pod okapem | 4 : 1 (wysokie 8 : 1); dąb ≤ 2 podrosty > 0,5 m / ha | sadzony dąb w gniazdach | sosna tylko w lukach i na zrębach | sosna w lukach na odsłoniętym piasku | [Ź BPN; Paluch] |
| N7 | Podszyt (pokrycie) | 10–40% | 10–30% | 5–30% | 10–30% | [L 06 §4.1] |
| N8 | Runo (pokrycie kolumn) | wiosną 60–100%, latem 20–60% | wiosną 50–90% | 40–80%, borówka ≥ 50% runa | jak gospodarczy | [L; Ź Popiołek] |
| N9 | Mchy | 5–20% | 5–15% | 60–95%, rokietnik dominuje | 60–90% | [L; Ź Popiołek] |
| N10 | Martwe drewno m³/ha (W13) | 100–160, ok. 1/3 stojące, ≥ 5 grubych kawałków (> 3 m, > 50 cm) / ha | 10–16 + pniaki | 4–11 + 30–80 pniaków / ha | 50–80 | [Ź GIOŚ 9170; Mróz i Pawlaczyk; Piętka] |
| N11 | Forma śmierci | Św wykroty i posusz, Db posusz i złomy konarów, Js zamierające korony, Gb i Lp rzadko | jw. | So posusz i złomy | jw. | [Ź Miścicki; L 06 §9.2] |
| N12 | Fenologia geofitów grądu | widoczne Early–Late Spring, szczyt Mid Spring, 0 od Early Summer | jw. | – | – | [Ź Rawlik 2021] |
| N13 | Okap liściasty | bezlistny od połowy XI do IV/V według gatunku; przebarwienie według `08 §9` | jw. | sosna zielona cały rok | | [L 08 §9] |
| N14 | Gleba (profil pod wierzchem) | brunatna, mull | brunatna, mull lub moder | bielica: ściółka mor, Ees, Bfe | jw. | [L 06 §4.2] |
| N15 | Ekoton Bśw → Lśw | kolejność wskaźników Bśw → BMśw → LMśw → Lśw na transekcie wzorcowym, przejście 20–100 m (REAL) przy ostrej granicy podłoża, bez linii prostej | | | | [Ź Stefańska; O] |
| N16 | Mrowiska | – | – | ok. 0,13–0,5 / ha, głównie skraje i luki | jw. | [Ź Sondej 2018] |
| N17 | Fauna | sarna i dzik spawnują się w obu lasach, w obu skalach i trybach; zachowania z kart (§V14–V17) przechodzą w gametest `fauna` | | | | [L 07; raport fauny] |

**Ukończenie wycinka:**
- wszystkie N1–N17 są w tolerancji (albo z odstępstwem zaakceptowanym przez użytkownika);
- budżety są spełnione;
- `build`, `test`, `checkDatagen`, `checkArt` i `runClientGameTest` (wszystkie tryby, w tym nowe `inventory`, `trees`, `seasons`, `fauna`, `slice`) przechodzą w obu skalach;
- użytkownik zaakceptował PK-W1…PK-W7.

---

## 4. Kroki

Tory, każdy w osobnym worktree:
- **A, flora:** V0, V1, V3–V13, V19;
- **B, grafika:** V2, potem tekstury do kroków toru A;
- **C, fauna:** V14–V18, V20 (zestawy drewna) po V7.

Pliki datagenu (`ModLanguageProvider`, tagi) są wspólne, więc tory scalamy w ustalonej kolejności i po każdym scaleniu uruchamiamy `checkDatagen`.

### V0. Brama wejścia: zamknięcie M2 i przygotowanie wycinka (tor A)
- **Zakres:**
  - krok S8c (M2-20 d2: wygładzone pole H − miękki poziom koryt do `rise()`), bo bez niego Z10 w trybie D nie jest spełnione;
  - zebranie od użytkownika akceptacji PK1 i PK2 M2 oraz pakietu decyzji (§6);
  - SmartBrainLib 2.0.2 → 2.0.3;
  - miejsca wzorcowe wycinka w obu skalach i trybach, znalezione `find biome` i sondą klasyfikatora i zapisane w gametestach. Pięć miejsc: grąd typowy, grąd niski, grąd wysoki, bór świeży na zachodzie i na wschodzie, oraz transekt Bśw → BMśw → LMśw → Lśw;
  - szkic `docs/04-wycinek.md`.
- **Gotowe, gdy:**
  - S8c przeszedł recenzję B1, złoty test i klasyfikator trybu N są bez zmian;
  - decyzje zapisane w `docs/00` G;
  - SBL 2.0.3 kompiluje się, a `fastTest` i `-Pgametest=ui` przechodzą;
  - lista miejsc ma współrzędne dla obu skal, a sonda potwierdza ≥ 35% koła biomu i pełny ciąg STL na transekcie.
- **Ryzyko:** S8c zmienia maskę trybu D (`ForestCover`), czyli wysokie ryzyko B1. Transekt z pełnym ciągiem STL może nie istnieć blisko startu w GAMEPLAY, bo pasy są tam wąskie.
- **Szacunek:** 2–3 dni (S8c 1,5–2).

### V1. Karta norm i inwentaryzacja lasu (tor A)
- **Zakres:**
  - klasa `ForestNorms` (czysta Java, liczby i źródła z §3, jak `Calibration`);
  - inwentaryzacja na poziomie planerów: JUnit na wyniku `TreeStandPlan`/`ColumnPlan` mierzy drzewa na ha według warstw, skład, rozstaw i pokrycia;
  - gametest `-Pgametest=inventory`: powierzchnie próbne 1 ha w miejscach wzorcowych mierzą N1–N10 i N14–N16 z bloków świata. Pierśnicę liczy z promienia pnia, zwarcie z kolumn z liśćmi powyżej 2 bloków, m³/ha martwego drewna z wokseli kłód;
  - raport CSV i tabela wobec norm.
- **Pomiar bazowy na M2:** stan obecny (9 drzew wanilii na chunk, kłody `fallen_*`), żeby każdy kolejny krok pokazywał postęp.
- **Gotowe, gdy:** raport bazowy M2 jest w `docs/wycinek/inventory-m2.csv`, test liczy wszystkie miary w obu skalach i trybach, a miary sprawdzone na ręcznie zbudowanej powierzchni testowej (znana liczba pni i kłód) zgadzają się co do sztuki.
- **Ryzyko:** pierśnica i m³/ha z wokseli to przybliżenie, więc zaokrąglenia promienia (co 2 px) dają błąd ±15% dla cienkich drzew. Dokumentujemy to jako niepewność pomiaru.
- **Szacunek:** 1–2 dni.

### V2. Pipeline grafiki i pierwsze tekstury (tor B)
- **Zakres:**
  - `tools/art`: `texlib`, receptury kory według opisu z `06 §4.1`, palety k-średnich ze zdjęć CC0 (do repozytorium idą tylko kolory i odnośnik);
  - lint stylu: 4–9 kolorów, liście z 55–70% pikseli, tekstury pod tint w szarości, kafelkowanie bez szwów;
  - zadanie `checkArt`: PNG zgodne bajt w bajt z generatorem;
  - pierwsze 8 tekstur: kora dolna sosny (2 warianty), „lustro” sosny, czoło sosny, kora dębu, kora grabu, kora lipy, igły sosny, liście liściaste pod tint;
  - gametest „tablica próbek”;
  - `LICENSE-assets` i `CREDITS` (F5).
- **Gotowe, gdy:** lint i `checkArt` przechodzą, tablica próbek jest w `docs/wycinek/art/`, a dolna kora sosny przeszła 3. rundę (raport: za ciemna w rundzie 2).
- **Ryzyko:** subiektywna estetyka. Zabezpieczeniem jest PK-W1.
- **Szacunek:** 1,5–2,5 dnia.

### V3. Pnie o zmiennej grubości (tor A)
- **Zakres:**
  - `TrunkBlock` (wariant D: `radius` 1–7, `parent`, `waterlogged`; 84 stany na gatunek);
  - ręcznie pisane szablony `template_trunk_r1…r7` z wysięgiem 8 px;
  - datagen blockstates z obrotami jak `end_rod`;
  - kształty z `Shapes.rotateAll`;
  - tagi `#logs`, `#overworld_natural_logs` i własne `#<gatunek>_logs`;
  - łup: patyki dla r ≤ 2, kłoda gatunku dla r ≥ 3 (D2: dąb, brzoza i świerk wanilijne);
  - bloki dla 8 gatunków drzew i 3 krzewów (leszczyna, jarząb, kruszyna). Sosna ma osobny blok górnego „lustra”. Brzoza i świerk odwołują się do tekstur wanilii przez id;
  - świat testowy z ręcznymi pniami wszystkich promieni i połączeń.
- **Gotowe, gdy:**
  - zrzuty z Sodium (`-Poptimization`) i bez niego nie pokazują szczelin, migotania ani błędów AO na granicy sekcji;
  - obrys i cząsteczki są poprawne;
  - korowanie i paliwo działają tylko tam, gdzie zestaw to przewiduje;
  - przyrost stanów jest zapisany (ok. 900–1000).
- **Ryzyko (R3):** wysięg modelu poza blok przy Sodium i AO. Zapasem jest wariant C (FRAPI). B1 wysokie (rejestracja bloków, datagen).
- **Szacunek:** 1,5–2,5 dnia.

### V4. Liście gatunków, kolor sezonowy i zima (tor A)
- **Zakres:**
  - `SpeciesLeavesBlock` (`TintedParticleLeavesBlock` z właściwością `phase` 0–3, ustawianą raz na drzewo z hasza pnia, czyli zmienność osobnicza);
  - `SpeciesFoliageTint` z `RAW_FOLIAGE` w `ColorResolverRegistry`. Zachowuje mieszanie kolorów biomów (Z10) i omija nakładkę SS;
  - krzywe gatunków z `08 §9`: brzoza i lipa żółte od końca IX, klon żółty do pomarańczowego, grab żółty → brązowy, dąb późno żółtobrązowy, jesion opada zielono-żółty;
  - przesunięcie regionalne z O i wysokości;
  - `ClientSeasonPhase` z przebudową siatek w 2–3 krokach na podsezon. Wyłącznikiem jest `changesFoliageColor`;
  - zima (W9): model FRAPI gałązek w fazie bezlistnej, marcescencja dębu, grabu i młodych drzew w podroście, igły sosny ciemniejsze zimą;
  - mcmeta `dark_cutout`, kolor cząsteczek opadających liści z odcienia.
- **Gotowe, gdy:**
  - zrzuty tych samych drzew w 12 podsezonach z SS i bez (`-PnoSeasons`) zgadzają się z N13;
  - zmiana podsezonu nie zacina klienta ponad 1 klatkę przy 12 chunkach (pomiar);
  - MoreCulling z paczki działa (liście to `LeavesBlock`).
- **Ryzyko:** zacięcia przy przebudowie siatek (R9), zgodność modelu gałązek z MoreCulling i Continuity, sygnatury `ModelLoadingPlugin` niesprawdzone na 26.3. B1 wysokie (render i FRAPI).
- **Szacunek:** 2–3 dni.

### V5. Generator kształtu drzew `TreeShapePlan` (tor A)
- **Zakres:** czysty planer (bez klas gry) z podglądem PNG.
  - **Archetypy z `06 §9.2`:** sosna (korona w górnej 1/4–1/3, suche kikuty poniżej nasady korony, czyli naturalne oczyszczanie), świerk (stożek, opadające końce), brzoza (smukła, zwisające gałązki), dąb (grube kręte konary, a1), lipa (gęsta jajowata), grab (pień „muskularny” przez lekkie odchylenia osi, często wielopniowy), klon i jesion (wspólny liściasty z parametrami), krzewy (wielopniowa leszczyna, jarząb, kruszyna, jałowiec kolumnowy).
  - **Wejście:** gatunek, stadium, wiek, bonitacja, warstwa (a1, a2, podrost, podszyt), drzewo wolnostojące albo w zwarciu (Kw i Kz, długość korony Lk), sąsiedzi do przycięcia koron, nachylenie terenu i ziarno z nową solą `feature.tree.shape`.
  - **Wysokość i pierśnica z allometrii:** wysokość z wieku i bonitacji, pierśnica przez smukłość h/d = 60–100 w zwarciu i 30–50 dla drzew wolnostojących.
  - **Geometria:** model rury (n = 2–2,5), wokselizacja DDA, liście w skupieniach z pustym środkiem w dużych koronach, BFS odległości liści ≤ 6, przycięcie komórkami Woronoja ważonymi promieniem korony, promień korony ≤ 16 bloków.
  - **Drzewa pomnikowe:** szablon `minecraft:template` dla 1–2 olbrzymów (dąb 1,5–2 m pierśnicy, pień 2 × 2) jako rzadkość w lesie naturalnym.
- **Gotowe, gdy:**
  - JUnit przechodzi: determinizm, każdy blok dochodzi łańcuchem `parent` do korzenia, promień nie rośnie ku końcom pędów, liście ≤ 6 od drewna, zapis tylko w 3 × 3 chunkach, brak sporów między koronami;
  - wymiary H, Kz i Lk każdego gatunku w każdym stadium mieszczą się w zakresach `06 §9.2` (test parametryczny);
  - koszt w `costTest` ≤ 0,5 ms na drzewo a1;
  - arkusze PNG (bok i góra, 6 stadiów × 9 gatunków) przeszły recenzję;
  - **PK-W2** zaakceptowany.
- **Ryzyko:** schodki na ukośnych konarach (R4), czas planera, „rodzinne podobieństwo” koron. B1 wysokie (2 recenzentów, przegląd pełnych arkuszy).
- **Szacunek:** 4–6 dni.

### V6. Mapa drzewostanów (minimalna) (tor A)
- **Zakres:** czysta klasa `StandMap` (bez nowych próbek, tylko `ChunkHabitats` i nowe sole).
  - **Wydzielenia:** komórki 2–30 ha·k² (sól `feature.stand_map.cells`). Każde ma:
    - naturalność według suwaka D9 i W4;
    - klasę wieku z rozkładu LP: I 8–9%, II ok. 15%, III 23–25%, IV 19–20%, V ok. 15%, > 100 lat ok. 14% [L 06 §1.4];
    - skład według ZHL dla STL i wariantu regionalnego;
    - bonitację z żyzności i DGW;
    - zwarcie.
  - **Las gospodarczy:** jednowiekowy.
    - Rzędy co 2 bloki w uprawach i młodnikach (bruzdy jako płaty odsłoniętej gleby). Rozstaw 2–3 bloki w drągowinie, 3–4 w wieku 50 lat i ok. 5 w wieku 100 lat.
    - Zręby ze stadiów I (bór, z 1–2 biogrupami ok. 25 bloków). Gniazda rębni III z sadzonym dębem (grąd).
    - Pniaki po trzebieżach.
    - W trybie D pinetyzacja części wydzieleń grądowych (W5): sosna w a1, grądowy podrost (leszczyna, grab, lipa) i zubożałe runo.
  - **Las naturalny:** wielowiekowy.
    - Pole luk (sól `feature.natural_gaps`): luki 18–45 bloków w różnych stadiach (świeża, z maliną, z odnowieniem grabu i lipy).
    - Skupiskowe rozmieszczenie, olbrzymy, a2 z grabu.
  - **Granice wydzieleń (W7a):** proste tylko na liniach oddziałowych, wewnątrz oddziału nieregularne. Linie oddziałowe zależą od W6.
  - **Ekotony:** `Ecotone` miesza mapę drzewostanów przez granice siedlisk tak jak palety. Wydzielenie przechodzi przez granicę siedliska, ale skład zmienia się w pasie Z10.
- **Gotowe, gdy:**
  - JUnit mierzy udziały klas wieku i naturalności w 10⁴ wydzieleń (±3 p.p.), rozkład wielkości wydzieleń, udział luk (N5) i brak zależności od kolejności zapytań;
  - PNG mapy drzewostanów dla kadrów punktu kontrolnego 1 są w obu skalach i trybach;
  - obie skale dają wydzielenia 2–30 ha·k².
- **Ryzyko:** konflikt reguły Z10 z prostymi liniami LP (W7a), skala wydzieleń w GAMEPLAY (W10), w trybie N bez lasu gospodarczego uprawy i zręby zobaczy tylko tryb D (W4).
- **Szacunek:** 2,5–4 dni.

### V7. Integracja drzewostanu w świecie (tor A)
- **Zakres:**
  - `TreeStandFeature` dla biomów wycinka i pasa styku (W1). Kandydaci pni z mapy drzewostanów (gęstość według stadium zamiast stałej z §8.5), warstwy a1, a2, podrost i podszyt, wywołanie `TreeShapePlan` z sąsiadami;
  - maska struktur dla większych koron (`BUILDING_GAP`, R7);
  - drzewa w płytkiej wodzie bez zmian;
  - sadzonki przez `TreeGrower` z tymi samymi kluczami (sadzonka z gracza rośnie jako drzewo wolnostojące stadium „młodnik”);
  - ekotony z biomami spoza wycinka biorą po drugiej stronie zastępstwa M2;
  - `ModFeatures.STATS` liczy liście i drewno.
- **Gotowe, gdy:**
  - inwentaryzacja spełnia N1–N7 we wszystkich miejscach wzorcowych, obu skalach i trybach;
  - `DeterminismClientGameTest` przechodzi (teren i dekoracja w jednej kolejności identyczne, dowolna kolejność w progach 0,5% / 8% albo nowy próg uzasadniony pomiarem);
  - `VegetationClientGameTest` i `PresentDayClientGameTest` przechodzą;
  - liście do opadnięcia < 0,1%;
  - zrzuty miejsc wzorcowych są gotowe;
  - **PK-W3** (zrzuty i krótki test w grze u użytkownika) zaakceptowany.
- **Ryzyko:** determinizm przy koronach do 16 bloków (R5), struktury i wioski w grądzie (M2-12), światło pod okapem (R10). B1 wysokie.
- **Szacunek:** 2–3 dni.

### V8. Pomiary drzewostanu i budżety (tor A, brama)
- **Zakres:**
  - tryb `stages` A/B (M2 wobec wycinka) w miejscach wzorcowych i obszarze „area”, obie skale, oba tryby, z paczką i bez: FEATURES, LIGHT, cały chunk, liście i drewno na chunk, czas planera;
  - FPS w stałym przelocie, pamięć, liczba stanów;
  - jasność pod okapem: odsetek kolumn z jasnością ≤ 7 w dzień, czyli potwory w dzień;
  - cięcia według kolejności z W11, gdy budżet nie wychodzi.
- **Gotowe, gdy:**
  - cały chunk ≤ 48 ms wszędzie;
  - użytkownik zatwierdził budżety FEATURES i LIGHT (W12);
  - FPS z paczką ≥ 0,8 × M2 w tym samym przelocie (cel do potwierdzenia w W12);
  - potwory w dzień pod okapem nie pojawiają się częściej niż w wanilijnym ciemnym lesie.
- **Ryzyko:** grąd naturalny w pełnej gęstości (1000–1500 liści na chunk) przekracza LIGHT albo FPS (R1, R2). Cięcia mogą obniżyć wierność N1 i N4, a wtedy decyduje użytkownik (B3).
- **Szacunek:** 1–2 dni.

### V9. Gleby wycinka (tor A)
- **Zakres (W15):**
  - własne bloki profilu: `bleached_sand` (Ees), `rusty_sand` (Bfe), `brown_forest_soil` (mull), `needle_litter` (mor, wierzch boru zamiast samego `podzol`) i `broadleaf_litter` (grąd, z sezonową grubością od V11);
  - profile w `SoilBlocks` dla `PODZOL`, `RUSTY_SOIL` i `BROWN_SOIL` w biomach wycinka i pasie styku; `SoilBlend` miesza wierzch w ekotonie;
  - tagi `#dirt`, `#supports_vegetation`, `#animals_spawnable_on` i własny `forest_animals_spawnable_on`;
  - tekstury z toru B.
- **Gotowe, gdy:**
  - złoty test i heightmapy są bez zmian (sprawdzone jawnie);
  - test gleb w grze (11 miejsc i miejsca wzorcowe) zgadza się z planem;
  - przekrój wykopu pokazuje profil N14;
  - `PACK_FALLBACKS` = 0 (paleta sekcji nadal 4–8 bitów);
  - TERRAIN mieści się w M2-26 b.
- **Ryzyko:** plan powierzchni to wysokie ryzyko B1. Nowe bloki wierzchu mogą zmienić zachowanie roślin wanilii (`canSurvive`) i spawn.
- **Szacunek:** 1,5–2,5 dnia.

### V10. Runo, mchy i ściółka (tor A)
- **Zakres:**
  - **Geofity grądu:** bloki dwuwarstwowe jak `wildflowers` (łodyga pod tint trawy, kwiat bez tintu) z fazami liść, kwiat i spoczynek.
  - **Pozostałe rośliny:** aspekt letni grądu, runo boru (borówka i brusznica ze stanem owoców, wrzos, śmiałek, pszeniec, orlica 2-blokowa, konwalijka), mchy (rokietnik jako dywan, widłoząb, poduszka bielistki, płaty chrobotka) i gatunki luk.
  - **Palety ze skalą Braun-Blanqueta:**
    - zawilec w łanach dziesiątek bloków (> 1000 pędów/m², więc łan = pełne pokrycie płatu);
    - przylaszczka w małych kępach;
    - borówka dominuje płatami;
    - rokietnik ciągły z przerwami.
  - **Zależność od światła:** pokrycie zależy od zwarcia nad kolumną z mapy koron V7. W lukach jest malina, jeżyna, wierzbówka i trzcinnik, pod pełnym okapem grądu runo letnie rzadsze.
  - **Wskaźniki pasa styku:** w BMśw i LMśw (N15).
  - **Warianty regionalne (W2):** bluszcz płożący i pszeniec gajowy na zachodzie, turzyca orzęsiona i kokorycz na wschodzie, gorysz i jałowiec w Peucedano-Pinetum.
  - **Datagen:** bloki, modele, tagi, lang, mączka kostna (`can_spawn_from_bone_meal`).
- **Gotowe, gdy:**
  - inwentaryzacja spełnia N8 i N9 (latem i wiosną) oraz N15 na transekcie;
  - transekty §4.6 i ekotony Z10 są bez regresji (`EcotoneSharpnessTest`);
  - zrzuty z bliska i z wysokości gracza są gotowe;
  - tekstury przeszły lint;
  - FEATURES jest w budżecie V8.
- **Ryzyko:** „kropki” jaskrawych kwiatów i zbyt jednorodne łany, rośliny bez tekstur dobrej jakości (trawy i paprocie w masie), liczba bloków. B1: datagen wysoki.
- **Szacunek:** 3–5 dni.

### V11. Fenologia runa, owoców i ściółki (tor A)
- **Zakres (W7):**
  - wygląd faz runa po stronie klienta (model FRAPI zależny od `ClientSeasonPhase`, ta sama infrastruktura co V4). Wszystkie chunki w zasięgu widzenia są więc zawsze w bieżącej fazie;
  - stan serwerowy (random tick z nadrabianiem po wczytaniu chunka: znacznik podsezonu w załączniku chunka) tylko tam, gdzie gra tego wymaga: owoce borówki, brusznicy i maliny do zebrania, grzyby (V13);
  - ściółka liściasta grądu gruba od Late Autumn, cienka latem;
  - przesunięcie regionalne z O i wysokości;
  - mapowanie faz na podsezony według raportu ekologii §3 i §9, kalendarz kwitnienia z `08 §7.3`;
  - z SS i bez.
- **Gotowe, gdy:**
  - gametest `seasons` robi zrzuty miejsc wzorcowych w 6 podsezonach (Early Spring, Mid Spring, Late Spring, Mid Summer, Mid Autumn, Mid Winter) z SS i bez;
  - spełnione są N12 i N13, a owoce są zbieralne tylko w sezonie;
  - nadrabianie po 2 podsezonach nieobecności daje poprawny stan w ≤ 1 s gry po wczytaniu;
  - **PK-W4** zaakceptowany.
- **Ryzyko:** obrys celownika „zniknętych” geofitów (stan bez zmian), przebudowy siatek, koszt nadrabiania.
- **Szacunek:** 2–3 dni.

### V12. Martwe drewno według norm (tor A)
- **Zakres:** `DeadwoodPlan` (czysty) z obiektami:
  - kłoda: stadia świeża z korą, z mchem i hubą, zmurszała;
  - posusz stojący, w tym „rura” brzozy;
  - złom: pień złamany na 2–8 blokach;
  - wykrot: talerz korzeniowy 2–5 bloków i dół 1 blok, z korektą wierzchu w dekoracji bez zmiany heightmap modelu;
  - pniak, tylko w lesie gospodarczym, zgodny z rozstawem po trzebieży;
  - gałęziówka.

  Dalsze reguły:
  - forma śmierci według gatunku (N11);
  - objętość V = 0,785 · d² · L, kalibracja liczby obiektów do m³/ha wariantu z W13, struktura wymiarowa z WISL (75% posuszu < 25 cm, 80% leżaniny to fragmenty);
  - kłody z tego samego wokselizatora i `TrunkBlock`, więc kłoda o średnicy 35 cm ma r = 3;
  - siewki jarzębu i świerka na zmurszałych kłodach;
  - huby dopasowane do promienia pnia: hubiak na brzozie, pniarek na sośnie i świerku, żółciak na dębie, opieńka kępami na pniakach;
  - wanilijne `fallen_*` usunięte z palet wycinka.
- **Gotowe, gdy:**
  - inwentaryzacja spełnia N10 w każdym wariancie, z liczbą grubych kawałków grądu naturalnego ≥ 5 na ha;
  - nic nie wisi w powietrzu (test podparcia) i nic nie stoi na ulicach struktur;
  - koszt jest w budżecie V8;
  - **PK-W5** (razem z V13) zaakceptowany.
- **Ryzyko:** wykroty zmieniają wierzch (płaty gleby, kałuże M2, ekoton gleb). Duża ilość drewna w trybie N może wyglądać na bałagan, a to decyzja W13.
- **Szacunek:** 2,5–4 dni.

### V13. Grzyby (tor A)
- **Zakres (W14):**
  - 8–10 gatunków: borowik szlachetny, podgrzybek brunatny, kurka, maślak sitarz (młodniki), muchomor czerwony (pod brzozą), gąska zielonka (bór, IX–XI), piestrzenica (maj, bór), muchomor sromotnikowy i sromotnik (grąd), smardz (pod jesionem, IV–V);
  - owocniki pojawiają się random tickiem w oknie sezonu, w ciągu kilku dni po deszczu, w promieniu kilku bloków od drzewa-partnera z tagu gatunku, i znikają po kilku dniach;
  - podstawowe jedzenie i efekty trujące (komponenty przedmiotu);
  - nadrzewne z V12.
- **Gotowe, gdy:**
  - test w grze przyspiesza czas i widzi owocniki tylko w sezonie, tylko po deszczu i przy partnerze;
  - JUnit sprawdza reguły;
  - koszt random ticka < 0,05 ms ticku serwera przy 12 chunkach symulacji.
- **Ryzyko:** gatunki trujące a gracz (opis w przedmiocie), deszcz bez SS (pogoda wanilii).
- **Szacunek:** 1,5–2,5 dnia.

### V14. Szkielet fauny i narzędzia modeli (tor C, start po V2)
- **Zakres:**
  - `tools/fauna` (szablony czworonoga, małego zwierzęcia nadrzewnego i ptaka; pakowanie UV; malowanie z detalami oczu i maski; chody; podgląd; walidacja);
  - model kalibracyjny osi w grze z testem regresji (R3);
  - `PolishAnimal`: despawn ponad 128 blokami bez imienia i smyczy, dystans ucieczki (FID), `ForestAlarm`, podsezon przez `DataTicket` (futro, parostki);
  - predykat spawnu z tagiem `forest_animals_spawnable_on` (podzol, igliwie i gleby z V9), progiem światła ≥ 5 i `MOTION_BLOCKING_NO_LEAVES`;
  - spawny w datagenie dla biomów wycinka, bez wilka i królika (F1);
  - gametest `fauna`.
- **Gotowe, gdy:**
  - zwierzę testowe spawnuje się w obu lasach, w obu skalach i trybach, nie na koronach, i znika za 128 blokami;
  - model kalibracyjny ma poprawne osie;
  - MSPT przy 40 zwierzętach < 2 ms.
- **Ryzyko:** rejestracja encji i spawny w datagenie biomów to B1 wysokie. Wspólny limit `CREATURE`.
- **Szacunek:** 2,5–3,5 dnia.

### V15. Sarna (tor C)
- **Zakres:**
  - model (13 kości), tekstury: koza i kozioł, lato i zima, lustro w alarmie;
  - animacje: idle, walk, run, eat, alert, rest;
  - harmonogram dobowy SBL z raportu fauny;
  - grupy: latem 1–3, zimą rudle 2–6 z liderką;
  - FID 20–40 m w lesie, szczek „bo-bo”;
  - futro i parostki według podsezonu: parostki wytarte V–X, zrzut X–XI, scypuł XII–IV.
- **Gotowe, gdy:**
  - gametest pokazuje ucieczkę z FID, grupę i harmonogram (odpoczynek w gęstwinie w południe);
  - zrzuty są w 4 podsezonach;
  - **PK-W1** (8 tekstur i sarna) zaakceptowany.
- **Ryzyko:** estetyka modelu i tekstury zwierzęcia („plastik”).
- **Szacunek:** 1–1,5 dnia.

### V16. Dzik (tor C)
- **Zakres:**
  - wataha 3–8 za starą lochą, ucieczka gęsiego, chrapnięcie ostrzegawcze;
  - aktywność zmierzchowo-nocna, barłóg w gęstwinie w dzień;
  - warchlaki pasiaste przez 3–4 podsezony (C10);
  - buchtowanie płatami 2–4 bloków w grądzie z regułą gry (C9) i babrzysko przy kałużach M2;
  - agresja lochy z warchlakami III–VI po ostrzeżeniu (C6);
  - stan populacji 2025: dzik rzadszy od sarny (C3).
- **Gotowe, gdy:** gametest sprawdza ucieczkę gęsiego, buchtowanie (zmiana bloków tylko po generacji, determinizm bez zmian) i szarżę z ostrzeżeniem, a zrzuty dnia i nocy są gotowe.
- **Ryzyko:** buchtowanie a gleby V9 i runo (co dzik niszczy, co odrasta).
- **Szacunek:** 1–2 dni.

### V17. Wiewiórka i sójka (tor C, po V7)
- **Zakres:**
  - **wiewiórka:** skala ×2 (F3), wspinanie po pniach wszystkich promieni (`SmoothWallClimberNavigation`, test 3 grubości), skoki 4–5 bloków między koronami, ucieczka na drzewo, morfy ruda i ciemna, pędzelki zimą, `AMBIENT`;
  - **sójka:** krótkie loty pod koronami, siadanie na gałęziach, chodzenie po ziemi, skrzek przy graczu w 30–60 m wysyłający `forest_alarm` (sarny w 32 blokach czujne albo uciekają), jesienią zbieranie żołędzi (bez sadzenia, D11), `AMBIENT`.
- **Gotowe, gdy:** gametest: wiewiórka wchodzi na pień r = 2, 4 i 7 oraz na pełną kłodę, alarm sójki płoszy sarnę, a sójka nie utyka w liściach dłużej niż 5 s w 95% lotów.
- **Ryzyko (R5, R6):** cienkie pnie bez kolizji poziomej, nawigacja lotu w gęstych koronach.
- **Szacunek:** 2,5–4 dni.

### V18. Żaba trawna, dzięcioł, mrowiska i pejzaż dźwiękowy (tor C)
- **Zakres:**
  - **żaba trawna:** `frog_variant` z teksturą na UV wanilijnej żaby (bez kopiowania PNG wanilii), warunek po tagach lasów i stref wilgotnych (R12);
  - **dzięcioł duży:** bębnienie I–V i kucie cały rok z losowych wysokich pni w 24–64 blokach; kuźnie (szyszki w pniakach i złomach sosny) jako dekoracja V12;
  - **mrowisko rudnicy:** struktura 1–2 × 2–3 bloki z igliwia, ok. 0,13–0,5 na ha w borach, na skrajach i w lukach; cząsteczki mrówek III–X;
  - **pejzaż dźwiękowy (W16):** zięba, rudzik, kos i śpiewak w grądzie, sosnówka, czubatka i mysikrólik w borze, grubodziób; chór o świcie IV–VI, cisza ptaków śpiewających zimą poza sikorami;
  - licencje dźwięków tylko CC0 i CC BY z `CREDITS` (R8).
- **Gotowe, gdy:** inwentaryzacja spełnia N16, gametest odtwarza dźwięki według pory dnia i podsezonu, a manifest licencji jest kompletny.
- **Ryzyko:** dostępność nagrań polskich gatunków na wolnych licencjach (xeno-canto często NC).
- **Szacunek:** 1,5–2,5 dnia.

### V19. Integracja, transekt wzorcowy i odbiór (tor A, na końcu)
- **Zakres:**
  - scalenie torów i pełny zestaw testów (`build`, `test`, `checkDatagen`, `checkArt`, `runClientGameTest` we wszystkich trybach i obu skalach, z SS i bez, `-Poptimization`, determinizm z C2ME i bez);
  - inwentaryzacja końcowa z §3;
  - pomiary budżetów;
  - świat demonstracyjny z listą miejsc i komendą `find` dla miejsc wzorcowych;
  - dokumentacja: `docs/04-wycinek.md`, BRIEF, `docs/00` G, `docs/01` §5, §8;
  - **PK-W7**: test w grze przez użytkownika z listą kontrolną (§5).
- **Gotowe, gdy:** wszystkie testy PASS, karta norm w tolerancji albo z zaakceptowanymi odstępstwami, budżety spełnione, PK-W7 zaakceptowany, a lista „co uogólnić na M3–M7” zapisana.
- **Ryzyko:** konflikty scalania datagenu i lang, czas pełnego `all` (> 7000 s, dzielony jak w S10).
- **Szacunek:** 2–3 dni.

### V20. Zestawy drewna (tor C albo A, po V7; według D1)
- **Zakres:**
  - pomocnik `WoodSet` w API 26.3 (`BlockTransformerHelper.registerStripping`, `cookingFuel`, tabliczki jako modele bloków, półka, publiczny `Boat`);
  - pełny zestaw sosny (23 bloki, 22 przedmioty, 2 typy encji, ok. 23 PNG);
  - zestawy „lite” grabu i lipy: kłoda, drewno, okorowane, deski, liście, sadzonka;
  - pnie V3 dają łup z zestawu.
- **Gotowe, gdy:** datagen tworzy wszystkie pliki, receptury i korowanie działają w grze, łódź pływa, a Every Compat wykrywa zestaw.
- **Ryzyko:** liczba stanów (zestaw ok. 520) i praca nad teksturami.
- **Szacunek:** 2–3 dni (0 dni przy D1 = żaden).

**Kolejność i zależności (skrót):**
- tor A: V0 → V1 → V3 → V4 → V5 → V6 → V7 → V8 → V9 → V10 → V11 → V12 → V13 → V19;
- tor B: V2 równolegle z V1 i V3, potem tekstury dla V3, V4, V9, V10, V12;
- tor C: V14 po V2, potem V15 (PK-W1), V16, V17 po V7, V18 po V12, V20 po V7.

---

## 5. Punkty kontrolne dla użytkownika

| PK | Po kroku | Co dostaje użytkownik | Pytanie |
|---|---|---|---|
| PK-W0 | V0 | lista decyzji (§6) z rekomendacjami; potwierdzenie PK1 i PK2 M2 | akceptacja zakresu i decyzji |
| PK-W1 | V2 + V15 | tablica próbek (8 tekstur, kafel 3 × 3, arkusz 8×), sarna z 4 stron w lecie i zimie, krótkie nagranie lub sekwencja zrzutów chodu | „czy ten styl może być wzorcem 85 tekstur?” (F6) |
| PK-W2 | V5 | arkusze PNG drzew (9 gatunków × 6 stadiów, bok i góra) i zrzuty z gry pojedynczych drzew z bliska (pnie o zmiennej grubości, z Sodium i bez) | pokrój i proporcje gatunków |
| PK-W3 | V7/V8 | zrzuty miejsc wzorcowych z góry i z wysokości gracza (grąd naturalny, grąd gospodarczy, pinetyzacja w D, bór w 3 klasach wieku, zręby, luki, transekt), tabela inwentaryzacji wobec norm, pomiary budżetów; **test w grze**: świat z komendami do miejsc wzorcowych | struktura drzewostanu, gęstość, budżety W11/W12 |
| PK-W4 | V11 | te same kadry w 6 podsezonach z SS i bez (strona z suwakiem podsezonu) | aspekt wiosenny, przebarwianie, zima |
| PK-W5 | V12/V13 | kadry martwego drewna wariantów, tabela m³/ha, zrzuty grzybów w sezonie | ilość drewna (W13), wygląd stadiów rozkładu |
| PK-W6 | V17/V18 | zrzuty i krótkie nagrania zachowań (ucieczka sarny, wataha nocą, wiewiórka na pniu, alarm sójki); **test w grze** z listą zachowań do sprawdzenia | gęstość fauny (F2), skala małych zwierząt (F3) |
| PK-W7 | V19 | świat demonstracyjny, lista kontrolna (każda warstwa, oba tryby, obie skale, 4 pory roku), raport końcowy; **test w grze** | odbiór wycinka, zgoda na rozszerzenie na Polskę (M3+) |

Przy każdym punkcie zrzuty trafiają do `docs/wycinek/pk-wN/` i na stronę z porównaniem. Recenzenci oglądają pełne zestawy (B4 odrzucona).

---

## 6. Decyzje dla użytkownika

### 6.1 Otwarte decyzje projektu, których dotyka wycinek

**D1. Pełne zestawy drewna.**
- (a) żaden w wycinku;
- (b) pełny zestaw sosny;
- (c) pełny zestaw sosny oraz „lite” grabu i lipy;
- (d) pełne zestawy wszystkich gatunków wycinka.

**Rekomendacja: (c).** Sosna to 58% lasów i dobry test potoku zestawu. Grab i lipa przy ścince dają własne drewno, bez dziesiątek bloków drzwi i łodzi. Docelowo w M3 pełne zestawy dostają sosna, buk, jodła, olsza, grab, lipa, jesion, klon, modrzew i wierzba, a pozostałe gatunki wersję „lite” (raport techniki §6.3).

**D2. Dąb, brzoza, świerk, topola: wanilijne bloki czy własne.**
- (a) wszystko wanilijne, także wygląd pnia;
- (b) własne cienkie pnie i liście (konieczne dla D8), ale tekstury i łup z wanilii;
- (c) jak (b), ale dąb z własną teksturą kory;
- (d) pełne własne zestawy.

**Rekomendacja: (c).**
- Brzoza i świerk wanilijne pasują do polskich gatunków, więc model odwołuje się do tekstury wanilii przez id, bez kopiowania.
- Wanilijna jasna kora dębu nie przypomina ciemnej, bruzdowanej kory dębu szypułkowego, a dąb to drzewo a1 grądu. Dlatego własna kora dębu, ale łup i deski wanilijne (`oak_log`).
- Topola (poza wycinkiem) w M3 według wariantu (b): zestaw wanilijnej topoli 26.3 dla topoli czarnej i białej.

**D5. Tekstury i modele: kto i jak, licencja.**
- (a) agenci proceduralnie (`tools/art`, `tools/fauna`) z punktem kontrolnym grafiki;
- (b) zlecony grafik (koszt, czas, spójność);
- (c) agenci robią bazę, a grafik albo użytkownik poprawia kluczowe zasoby w Blockbench (UV i nazwy kości są stabilne).

Licencja zasobów: CC BY 4.0, MIT albo CC0.

**Rekomendacja: (a) teraz, z możliwością (c) po PK-W1.** Zasoby na CC BY 4.0 z `LICENSE-assets` i `CREDITS` (F5). Dźwięki tylko na CC0, CC BY i CC BY-SA. Zakazy: nie kopiujemy ani nie przerysowujemy plików wanilii, Naturalist, Alex's Mobs, BOP i Pixel Perfection.

**B6. Liście.**
- (a) nakładka SS na kolor biomu;
- (b) własna krzywa koloru gatunku (`RAW_FOLIAGE` + faza roku), z wyłącznikiem `changesFoliageColor`;
- (c) mnożnik gatunku × kolor SS.

**Rekomendacja: (b).** Tylko krzywa gatunku daje różne terminy przebarwiania (brzoza od końca IX, dąb od końca X, jesion opada zielony) i marcescencję. Wariant (c) przebarwia wszystko naraz.

**C2. Realistyczne hitboxy.** Rekomendacja: tak. W wycinku sarna 0,7 × 1,1, dzik 0,9 × 0,9, czyli bez problemu dla pathfindingu. Właściwa decyzja zapadnie przy łosiu i jeleniu.

**C3. Stan populacji.** Rekomendacja: stan 2025 (dzik rzadszy po ASF), wagi spawnu sarna 10, dzik 4.

**C6. Agresja.** Rekomendacja: locha z warchlakami III–VI, najpierw ostrzeżenie, potem szarża z małym obrażeniem.

**C8. Dropy.** Rekomendacja: brak w wycinku.

**C9. Buchtowanie.** Rekomendacja: tak, z regułą gry (domyślnie włączone).

**C10. Dorastanie.**
- (a) wanilijne;
- (b) realistyczne (rok);
- (c) według kalendarza moda (podsezony).

**Rekomendacja: (c).** Warchlak jest pasiasty przez 3–4 podsezony. Rok gry ma 144 dni, więc czas jest czytelny, a zgodny z cyklem.

**C5. Ptaki.** Rekomendacja: w wycinku jako encja tylko sójka, reszta jako pejzaż dźwiękowy (W16).

**M2-24. Ziołorośla górskie w GAMEPLAY.** Domyślnie przyjęto (a) do wycinka, potem (b). Rekomendacja: przenieść (b) do M4 (W18), bo wycinek nie dotyka gór.

### 6.2 Nowe decyzje wycinka

| # | Pytanie | Warianty | Rekomendacja |
|---|---|---|---|
| W1 | Gdzie działają nowe warstwy | (a) tylko grąd i bór świeży; (b) + pas styku BMśw i LMśw (nowe drzewa, mapa drzewostanów, runo przejściowe); (c) wszystkie biomy z gatunkami wycinka (podmiana kluczy `tree/*` w całym świecie) | **(b)**: naturalny ekoton grąd–bór przechodzi przez BMśw i LMśw. Bez nich Z10 pokazywałoby skok 30-metrowych sosen do 16-metrowych zastępstw. (c) to już M3 |
| W2 | Warianty regionalne zespołów | (a) jeden zespół na las (Tilio-Carpinetum, Leucobryo-Pinetum); (b) warianty z O i P w paletach, bez zmian klasyfikatora (Galio-Carpinetum W i Tilio-Carpinetum E; Leucobryo-Pinetum W i Peucedano-Pinetum E); (c) nowe stałe `Association` (zmiana klasyfikatora, przeliczenie kadrów) | **(b)**: wierność regionalna przy zerowym ryzyku dla siedlisk. Rampa O z szumem jak w §9 planu M2 |
| W3 | Struktura drzewostanu | (a) jeden drzewostan na biom (stały wiek); (b) minimalna mapa drzewostanów (wydzielenia, klasa wieku, naturalność, skład ZHL); (c) pełna mapa M3 z oddziałami, liniami, historią | **(b)**: bez klas wieku i naturalności wycinek nie jest wzorcem dla lasów Polski (85% gospodarczych, średni wiek 59 lat). (c) w M3 |
| W4 | Las gospodarczy w trybie N | (a) w N tylko naturalny, suwak D9 tylko w D; (b) suwak w obu trybach, domyślnie 85/15; (c) suwak w obu, domyślnie 0/100 w N i 85/15 w D | **(c)**: tryb N to „Polska bez ludzi”, więc gospodarka leśna jest tam sprzeczna z definicją. Gracz może ją włączyć. Wymaga potwierdzenia, bo zmienia domyślną wartość D9 w trybie N |
| W5 | Pinetyzacja (sośnina na siedlisku grądowym) w lasach gospodarczych trybu D | (a) brak; (b) ok. 20% wydzieleń gospodarczych grądu [O] z sosną w a1, grądowym podrostem i zubożałym runem; (c) inna proporcja | **(b)**: częsty polski obraz i naturalny „most” wizualny między lasami wycinka. Udział do weryfikacji danymi BDL |
| W6 | Linie oddziałowe | (a) w M8; (b) w wycinku w lasach gospodarczych: pas 3–6 bloków co 200–400 m·k, nieutwardzony, z trawą i śmiałkiem, bez słupków; (c) z słupkami i drogami wywozowymi | **(b)**: siatka oddziałów to najbardziej rozpoznawalna cecha polskiego lasu gospodarczego, a bez słupków koszt jest mały (część V6). (c) w M8 |
| W7a | Granice wydzieleń a Z10 | (a) proste jak w LP; (b) nieregularne z meandrami (Z10 dosłownie); (c) proste tylko wzdłuż linii oddziałowych, wewnątrz oddziału nieregularne | **(c)**: granica wydzielenia na linii oddziałowej jest w Polsce prosta, i to jest prawda o lesie gospodarczym. Z10 dotyczy granic siedlisk. Wymaga zgody, bo literalnie łamie „nigdy prosta linia” |
| W7 | Mechanizm fenologii runa | (a) stan bloku i random tick z nadrabianiem po wczytaniu chunka; (b) wygląd po stronie klienta (model FRAPI), stan bez zmian; (c) hybryda: wygląd klienta, a stan serwera tylko dla owoców i grzybów | **(c)**: wszystkie widoczne chunki w bieżącej fazie bez pakietów sieciowych (jak liście), a mechanika zbioru tylko tam, gdzie gracz jej używa |
| W8 | Kolor jesienny w obrębie gatunku | (a) jeden kolor gatunku; (b) faza 0–3 na drzewo (zmienność osobnicza, 112 stanów na gatunek); (c) zmienność z hasza pozycji bez stanu | **(b)**: w naturze sąsiednie drzewa przebarwiają się w różnym czasie. Koszt to 84 stany więcej na gatunek |
| W9 | Zima drzew liściastych | (a) liście cały rok; (b) model kliencki gałązek z marcescencją dębu, grabu i podrostu; (c) zmiana stanu bloku | **(b)**, obowiązkowo w wycinku (w raporcie techniki była opcją). Bezlistny okap przez 5 miesięcy to podstawa aspektu wiosennego i wierności zimy |
| W10 | Skala elementów w GAMEPLAY | (a) wszystko 1:1; (b) drzewa, korony, rozstaw, luki i kłody 1:1, a wydzielenia, oddziały i pasy ekotonu × k; (c) drzewa też × k | **(b)**: gracz i zwierzęta się nie skalują, a krajobraz tak (Z7) |
| W11 | Gęstość grądu naturalnego, gdy budżet nie wychodzi | (a) pełna gęstość BPN (ok. 15 pni na chunk), cięcia tylko technicznie (liście z pustym środkiem, tańsze liście, `propagatesSkylightDown` dla gatunków światłożądnych); (b) gęstość obniżona o ok. 30%; (c) mniejsze korony a2 | **(a)**, a (c) jako druga linia. Gęstość to wierność, a cięcia mają iść w technikę. Decyzja zapada po pomiarze w V8 |
| W12 | Budżety wycinka | cały chunk ≤ 48 ms (twardy); FEATURES i LIGHT: (a) nowe budżety z pomiaru V8 przy tym samym pokryciu; (b) stare budżety M2-14 (≤ 1,0 bazy leśnej); FPS z paczką ≥ 0,8 × M2; MSPT 40 zwierząt < 2 ms; stany +≤ 10% | **(a)**: drzewa 1:1 nie zmieszczą się w budżecie zastępstw M2 (FEATURES 1–4 ms na chunk wobec 0,4–0,6). Rozstrzyga cały chunk i FPS |
| W13 | Poziom martwego drewna | gospodarczy według średnich WISL (bór 5–11, grąd 13 m³/ha + pniaki) we wszystkich wariantach; naturalny: (a) grąd jak BPN ok. 130, bór jak Taborz ok. 65; (b) poziom „stanu właściwego” GIOŚ, ok. 30–40 (grąd) i 20–30 (bór); (c) średnie parków narodowych ok. 36 | **(a)**: las naturalny ma być jak puszcza. Ocena wizualna w PK-W5, a (b) jako wariant zapasowy |
| W14 | Grzyby w wycinku | (a) brak (M4); (b) owocniki sezonowe jako stała dekoracja; (c) dynamiczne (random tick, deszcz, drzewo-partner) z podstawową jadalnością, 8–10 gatunków | **(c)**: mikoryza i rytm owocnikowania to wierność przyrodnicza. Pełny katalog i efekty w M4 |
| W15 | Własne gleby w wycinku | (a) bloki wanilii (M2-D); (b) 3–5 własnych bloków profilu (bielica Ees i Bfe, rdzawa, brunatna, igliwie, ściółka liściasta); (c) M4 | **(b)**: bielica z białym poziomem pod igliwiem to najbardziej charakterystyczny przekrój boru. Złoty test nie porównuje materiałów |
| W16 | Ptaki śpiewające | (a) brak; (b) pejzaż dźwiękowy według biomu, podsezonu i pory dnia (licencje CC0 i CC BY); (c) encje | **(b)**: duża wierność za mały koszt. Encje w M7 |
| W17 | Gęstość rzadkich elementów (mrowiska, olbrzymy, wykroty) | (a) gęstość z danych (mrowiska 0,13–0,5 / ha w skupieniach przy skrajach i lukach); (b) mnożnik widoczności ×2–5 w `Calibration` | **(a)**: w przeciwieństwie do fauny obiekty stoją w miejscu i da się je znaleźć. Mnożnik jako parametr dla użytkownika |
| W18 | Ziołorośla górskie w GAMEPLAY (M2-24 b) | (a) przy wycinku; (b) w M4 z roślinami ziołorośli | **(b)**: wycinek nie dotyka gór |
| F1 | Wanilijne zwierzęta w lasach wycinka | (a) zostawić wilka, lisa i królika; (b) usunąć wilka i królika; (c) usunąć wszystkie | **(b)**: lis wanilijny to lis rudy, a wilk w Polsce to zdarzenie regionalne (M6) |
| F2 | Gęstość fauny | (a) realna (praktycznie niewidoczna); (b) ×20–50 przez wagi i limity wanilii; (c) własny spawner (M6) | **(b)** w wycinku, z jawnym parametrem i opisem odstępstwa od wierności; (c) w M6 |
| F3 | Skala małych zwierząt | (a) 1:1; (b) poniżej 30 cm ×2, żaba jak w wanilii; (c) 1:1 z teksturą podwójnej gęstości | **(b)**: odstępstwo od wierności dla czytelności, opisane w karcie norm |
| F4 | Zestaw gatunków | (a) sarna, dzik, wiewiórka, sójka, żaba, dzięcioł; (b) minimum: sarna, dzik, żaba, dzięcioł | **(a)**: wiewiórka sprawdza pnie M3, sójka łańcuch alarmowy |
| F5 | Licencja zasobów | CC BY 4.0 / MIT / CC0 | CC BY 4.0 (część D5) |
| F6 | Punkt kontrolny grafiki | (a) po ok. 8 teksturach i sarnie; (b) na końcu | **(a)** = PK-W1 |

---

## 7. Ryzyka

| # | Ryzyko | Skutek | Zabezpieczenie |
|---|---|---|---|
| R1 | LIGHT i FEATURES przy koronach 1:1 w grądzie naturalnym | przekroczenie budżetu, cięcia wbrew wierności | pomiar V8 przed runem; liście z pustym środkiem; W11 (cięcia techniczne przed gęstością); decyzja użytkownika |
| R2 | FPS przez quady liści (6–9 tys. na kolumnę chunka) | gorsza grywalność | MoreCulling (liście jako `LeavesBlock`), pomiar przelotu, gęstość koron |
| R3 | Wysięg modelu pnia poza blok przy Sodium i AO | szczeliny, migotanie | test w V3 przed resztą; zapas: wariant C (FRAPI) bez zmiany `radius` |
| R4 | Schodki grubych konarów | nienaturalne korony dębu | konary blisko osi, PK-W2 |
| R5 | Determinizm przy koronach do 16 bloków przez granice chunków | wzrost różnic przy dowolnej kolejności | przycięcie koron Woronojem, kolejność zapisów (pień nadpisuje runo), test determinizmu w V7 |
| R6 | Zasięg zapisu 3 × 3 chunki | ucięte korony olbrzymów | promień ≤ 16 bloków, olbrzymy blisko środka chunka albo szablon |
| R7 | Korony nad dachami struktur i wiosek (M2-12) | liście w budynkach | maska struktur z większym marginesem |
| R8 | Huby i mech wanilii na cienkich pniach | grzyby wiszące w powietrzu | własne huby przesunięte według promienia (V12) |
| R9 | Zacięcia przy przebudowie siatek (liście i runo) | przycięcie klatki raz na krok sezonu | 2–3 kroki na podsezon, pomiar; przebudowa falą od kamery jako zapas |
| R10 | Ciemno pod okapem: potwory w dzień i brak spawnu zwierząt (próg 8) | wierny okap psuje grę | pomiar światła w V8; próg spawnu zwierząt ≥ 5; gatunki światłożądne z `propagatesSkylightDown` |
| R11 | Distant Horizons i Continuity | cienkie pnie z daleka jako kolumny | przyjęte do M9 |
| R12 | Licencje (kod LGPL, zasoby ARR, dźwięki NC) | zasoby do usunięcia | kod tylko MIT, LGPL tylko jako zależność, zasoby własne, manifest licencji dźwięków |
| R13 | Normy z luk w danych (brak tabel BPN dla Tilio-Carpinetum, brak progu GIOŚ w m³/ha, brak tablic Szymkiewicza) | cele z `[O]` mogą być nietrafione | karta norm z kolumną źródła, korekta w PK-W3 i PK-W5 |
| R14 | Konflikt wierności z Z10 (proste granice LP) i z grywalnością (gęstość fauny, skala zwierząt) | spór o kierunek | jawne decyzje W7a, F2, F3 z opisem odstępstwa |
| R15 | Wielkość zakresu (40–64 dni) i zmęczenie zakresem | wycinek ciągnie się jak cały M3+M4 | trzy tory równoległe, punkty kontrolne co 1–2 tygodnie, B3 przy blokadach; W1, W14–W16 można zawęzić bez ruszania rdzenia |
| R16 | Pomiar inwentaryzacji z wokseli (pierśnica co 2 px, objętość kłód) | błąd ±15% dla cienkich drzew | tolerancje w karcie norm, kalibracja na ręcznej powierzchni (V1) |
| R17 | Scalanie trzech torów (datagen, lang, tagi) | konflikty, `checkDatagen` FAIL | ustalona kolejność scalania, `checkDatagen` po każdym scaleniu |
| R18 | Estetyka zasobów agentów | odrzucenie stylu po wielu plikach | PK-W1 wcześnie, wariant (c) z D5 |
| R19 | Nawigacja wiewiórki po cienkich pniach i sójki w liściach | utykanie, brak wspinaczki | testy na 3 grubościach i w gęstych koronach (V17), własny ruch „przyczepiony do pnia” jako zapas |
| R20 | Plan powierzchni (gleby V9) i wykroty (V12) zmieniają wierzch | regresje półki, kałuż, ekotonów gleb | złoty test i testy półki bez zmian, B1 z 2 recenzentami |
