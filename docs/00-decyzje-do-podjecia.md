# Decyzje projektowe do podjęcia

Data: 2026-09-27. Źródło: raporty w `docs/research/01–08`. Status każdej decyzji: **OTWARTA** / **PODJĘTA (data, wybór)**.

## Korekty między raportami

- Minecraft 26.3 „Wilderness Bound” jest pełnym wydaniem z 2026-09-15 (minecraft.wiki, sprawdzone 2026-09-27). Raport 04 błędnie opisuje go jako snapshot. Następny drop 26.4 jest w rozwoju.
- Density functions w 26.3 różnią się od 1.21.x (usunięte m.in. `flat_cache`, `shifted_noise`, `y_clamped_gradient`; `surface_rule` → `material_rule`). Kod worldgen nie jest przenośny między tymi liniami (raport 02).

## A. Fundamenty (blokują start kodu)

| # | Decyzja | Opcje | Rekomendacja z raportów | Status |
|---|---|---|---|---|
| A1 | Wersja Minecrafta | 26.3 (Java 25, najbogatsza wanilia: leaf litter, bush, fallen trees, huby, topola; port co ~3 mies.) / 1.21.1 (LTS ekosystemu, runo trzeba pisać samemu) / 1.20.1 | 26.3 | **PODJĘTA 2026-09-27: 26.3** |
| A2 | Tryb świata | Polska z danych (NMT GUGiK + BDOT10k + BDL, ograniczona mapą) / Polska proceduralna (nieskończona, reguły sąsiedztwa) / hybryda: etap 1 raster makro, etap 2 pełne dane | hybryda | **PODJĘTA 2026-09-27: proceduralna, nieskończona** (bez danych rastrowych; realistyczne typy krajobrazu i reguły sąsiedztwa) |
| A3 | Skala pozioma | 1:1 (650×690 tys. bloków, 12–24 TB pre-gen, tylko w locie) / 1:4 / 1:10 / 1:20 | 1:10 z przewyższeniem pionowym 2–2,5× | **PODJĘTA 2026-09-27: 1:1** (1 blok = 1 m; generacja w locie, bez pre-generacji) |
| A4 | Skala pionowa i wysokość świata | 1:1 z morzem na Y −470 / morze Y 63 z ściskaniem gór / równe ściskanie | **ZMIENIONA 2026-09-27 (po uwagach z gry): poziom morza Y 63, dół świata Y −64**, bo wanilijne struktury podziemne wisiały w powietrzu. Limit silnika to Y 2031, więc wysokość jest 1:1 do ok. 900 m, a powyżej płynnie ściśnięta (ok. 1,76 razy nad 1200 m). Rysy wypadają na Y 2000. Piętra roślinności liczone są z prawdziwych metrów. |
| A5 | Krajobraz rolniczy | pola, łąki, sady, stawy, hałdy, kanały (wierność dzisiejszej Polsce) / roślinność potencjalna naturalna (Polska „bez ludzi”, las tam, gdzie by rósł) | do decyzji | **PODJĘTA 2026-09-27: przełącznik przy tworzeniu świata** (tryb „dzisiejsza Polska” z polami / „roślinność naturalna”) |
| A6 | Współistnienie z wanilią | 100% polski świat (własny world preset) / polskie biomy obok wanilijnych (TerraBlender) | własny world preset „Polska” | **PODJĘTA (wynika z A2): własny world preset „Polska”**, bez biomów wanilijnych |
| A7 | Skala drzew | 1 blok = 1 m (buk 30–40 bloków) / drzewa 1:2 | zależna od A3 | **PODJĘTA (wynika z A3): 1:1**, buk/świerk/jodła 30–40 bloków |

| A8 | Rozmiar regionów krajobrazowych | realne (20–150 km) / skompresowane | **PODJĘTA 2026-09-27: suwak w opcjach świata, domyślnie realne rozmiary**; formy terenu zawsze 1:1 |
| A9 | Układ krajobrazów | mozaika z regułami / pasy jak w Polsce / pasy + mozaika | **PODJĘTA 2026-09-27: mozaika z regułami geomorfologicznymi**, nieskończona we wszystkich kierunkach |

## B. Pory roku (Serene Seasons)

| # | Decyzja | Rekomendacja | Status |
|---|---|---|---|
| B1 | SS zależnością twardą czy opcjonalną | opcjonalna z własnym fallbackiem | **PODJĘTA 2026-09-27: opcjonalna** z własnym kalendarzem |
| B2 | Długość sub-sezonu | 12–15 dni | **PODJĘTA 2026-09-27: 12 dni** (rok 144 dni) jako wartość zalecana |
| B3 | Mapowanie sub-sezonów | Early Spring = marzec … Late Winter = luty | **PODJĘTA 2026-10-02: Early Spring = marzec** (już w `SubSeason.java`) |
| B4 | Regionalizacja klimatu | warianty biomów z różną temperaturą bazową / ciągła mapa kontynentalizmu | **PODJĘTA 2026-10-02: ciągła mapa przez mixin w `Biome#getHeightAdjustedTemperature`**. Mixin w Serene Seasons nie jest potrzebny, bo SS woła `Biome.getTemperature`. W M2 tylko spadek z wysokością (0,55 °C/100 m); człon regionalny i własna temperatura sezonowa w M4 (`docs/03-m2-biomy.md`, §6) |
| B5 | Zróżnicowanie zim | losowy typ zimy (łagodna/normalna/mroźna) | OTWARTA |
| B6 | Liście | własne kolory per gatunek zamiast globalnej nakładki SS | OTWARTA |

## C. Fauna

| # | Decyzja | Status |
|---|---|---|
| C1 | Zależności: GeckoLib (modele) i SmartBrainLib (AI) jako twarde | **PODJĘTA 2026-09-27: tak, obie wymagane** |
| C2 | Realistyczne hitboxy (łoś 2,1 bloku, poroże 1,6 bloku) kosztem pathfindingu | OTWARTA |
| C3 | Stan populacji: 2025 (dzik rzadki po ASF, wilk i łoś liczne) czy „klasyczny” | OTWARTA |
| C4 | Gatunki inwazyjne (jenot, norka, szop, sumik, rak pręgowaty) | **PODJĘTA 2026-09-27: tak, w opcji**, domyślnie włączone w realistycznych proporcjach |
| C5 | Liczba ptaków jako pełne encje (proponowane: 8 T1 + ~11 prostych + reszta jako dźwięk) | OTWARTA |
| C6 | Agresja wobec gracza (locha, łoś, żubr, niedźwiedzica, żmija) z obrażeniami | OTWARTA |
| C7 | Drapieżnictwo widoczne (wilk zabija sarnę), padlina | OTWARTA |
| C8 | Łowiectwo, wędkarstwo, sezony ochronne; dropy (mięso, skóry, poroże) | OTWARTA |
| C9 | Zwierzęta modyfikują teren (tamy bobrów, buchtowiska) i regułą gry | OTWARTA |
| C10 | Tempo dorastania młodych (realistyczne vs 20–30 dni gry) | OTWARTA |
| C11 | Kleszcze i komary jako efekty statusu | OTWARTA |

## D. Flora i zasoby

| # | Decyzja | Status |
|---|---|---|
| D1 | Które gatunki dostają pełny zestaw drewna (drzwi, łodzie, tabliczki), a które „lite” | OTWARTA |
| D2 | Dąb, brzoza, świerk, topola: wanilijne bloki czy własne | OTWARTA |
| D3 | Wiśnia (cherry grove) i inne niepolskie biomy/drzewa: usunąć z polskiego świata | **PODJĘTA 2026-10-02 (wynika z D7 i A6): świat Polska ma tylko biomy `polishforests:*`** |
| D4 | Grzyby: dekoracja czy system zbieractwa z jadalnością | **PODJĘTA 2026-09-27: grzybobranie** (jadalne i trujące z efektami, zależne od gatunku drzewa, pory roku i deszczu; owoce leśne jako jedzenie) |
| D5 | Tekstury i modele: kto je robi (proceduralna baza + ręczne poprawki w Blockbench?) | OTWARTA |
| D6 | Licencja moda (kod/assety) | **PODJĘTA 2026-10-02: MIT** dla kodu (plik `LICENSE`); licencja przyszłych tekstur i modeli do ustalenia przy D5 |
| D7 | Biomy odpowiadają ~30 typom krajobrazu, a mezoregiony (344) są atrybutem | **PODJĘTA 2026-10-02: biom = grupa siedliskowa, 36 biomów** (18 leśnych, 12 nieleśnych, 6 wodnych); typ krajobrazu, zespół i wariant regionalny są danymi kolumny |
| A10 | Ekran opcji generowania i komenda lokalizowania terenu | **PODJĘTA 2026-09-27 (prośba użytkownika): zrobione**, patrz `01-architektura.md`, sekcja 11 |
| A11 | Zalecana paczka modów optymalizacyjnych | **PODJĘTA 2026-09-27 (prośba użytkownika): paczka .mrpack**, patrz `02-wydajnosc.md` |
| A12 | Skala przyjazna rozgrywce | **PODJĘTA 2026-09-29: drugi typ świata**, krajobrazy ok. 1,4 km (ok. 2 razy więcej niż biom wanilijny), wysokości obniżone proporcjonalnie, niższy wymiar (Y do 767) |
| A13 | Zakres rozszerzenia komendy | **PODJĘTA 2026-09-29: wszystkie formy, które tworzy generator**, plus szukanie po wysokości; nowe typy krajobrazu w M5 |
| D8 | Pnie drzew | **PODJĘTA 2026-09-27: zmienna grubość** (bloki pni o kilku średnicach w pikselach, pełny blok, 2×2 dla olbrzymów) |
| D9 | Las gospodarczy / naturalny | **PODJĘTA 2026-09-27: suwak w opcjach świata, domyślnie 85/15**, z infrastrukturą leśną w lasach gospodarczych |
| D10 | Obce gatunki drzew i roślin (daglezja, dąb czerwony, robinia, niecierpek) | **PODJĘTA 2026-09-27: tak, w opcji**, domyślnie 1–3% drzewostanów, głównie zachód |
| D11 | Dynamika lasu | **PODJĘTA 2026-09-27: tak** (wzrost i starzenie, sukcesja, wiatrołomy, gradacje kornika) |
| C12 | Łowiectwo i wędkarstwo | **PODJĘTA 2026-09-27: nie** w pierwszym zakresie (użytkownik nie wybrał) |

## E. Kamień milowy M2 (biomy, siedliska, gleby, temperatura)

Plan: `docs/03-m2-biomy.md`.

| # | Decyzja | Status |
|---|---|---|
| M2-A | Granulacja biomów | **PODJĘTA 2026-10-02: 36 grup siedliskowych** (identyfikatory zamrożone od M2) |
| M2-B | Tryb domyślny przed M8 | **PODJĘTA 2026-10-02: przełącznik działa w M2, domyślnie roślinność naturalna**; „dzisiejsza Polska” daje ok. 30% lasów i łąki z ugorem, a po M8 stanie się domyślna |
| M2-C | Struktury wanilii | **PODJĘTA 2026-10-02: wszystkie, które da się sensownie umieścić** (wioski na łąkach i w borach, chaty czarownic w olsach, posterunki na łąkach i halach, rezydencje w grądach i buczynach itd.; mapowanie w `docs/03-m2-biomy.md`, §10.1) |
| M2-D | Własne bloki w M2 | **PODJĘTA 2026-10-02: nie, dopiero w M4**; torf → błoto, torfowiec → blok mchu, trzcina → trzcina cukrowa, pałka → small_dripleaf |
| M2-1 | Biomy kolumnowe 2,5D zamiast 3D | **PODJĘTA 2026-10-02 (projekt)**: piętra z wysokości gruntu w metrach, temperatura ciągła z mixinu |
| M2-2 | Gradient temperatury | **PODJĘTA 2026-10-02 (projekt)**: 0,55 °C/100 m, T = 0,20 + 0,05·t |
| M2-3 | Zasięgi gatunków w świecie proceduralnym | **PODJĘTA 2026-10-02 (projekt)**: pola oceaniczności O i podgórskości P, zachód = −X. Od S3 liczy je `RegionalField` (wariant V3, kalibracja w docs/03-m2-biomy.md, Odstępstwo S3; granica P = 0,5 w medianie ok. 220 km od osi pasma, rozrzut ziaren 180–280 km) |
| M2-4 | Lawa | **PODJĘTA 2026-10-02 (projekt)**: tylko poniżej Y 0, bez źródeł lawy i wody na stokach |
| M2-5 | Chmury | **PODJĘTA 2026-10-02 (projekt)**: podniesione nad niziny (REAL ok. Y 1060, rozgrywka ok. Y 380) |
| M2-6 | Bałtyk | **PODJĘTA 2026-10-02 (projekt)**: nie zamarza; rzeki zamarzają tylko w mrozy |
| M2-7 | Światy z M1 | **PODJĘTA 2026-10-02 (projekt)**: bez migracji (szwy na styku starych i nowych chunków) |
| M2-8 | Wysokość najwyższych masywów Beskidów | **PODJĘTA 2026-10-02: rzadkie masywy do ok. 1725 m (Babia Góra, Pilsko) w obu skalach**; razem z poprawką geometrii terenu, przed fazą 2 M2. Daje kosodrzewinę i halę, a w skali rozgrywki także regiel górny |
| M2-9 | Nazwa i język moda | **PODJĘTA 2026-10-02: „Polish Forests”**, id `polishforests`; wszystko, co widzi gracz, i kod po angielsku, polski jako tłumaczenie; dokumentacja po polsku |
