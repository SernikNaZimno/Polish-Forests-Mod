# 08. Klimat Polski, przebieg pór roku, fenologia roślin i zwierząt — integracja z Serene Seasons

Data: 2026-09-21
Autor: agent badawczy (Claude), projekt "Przyrodniczo zgodne lasy" (mod do Minecrafta, Fabric)
Status: ZAKOŃCZONO (budżet 30 wywołań WebSearch/WebFetch wyczerpany). Elementy oznaczone „NIE ZBADANO” lub „wiedza ogólna, niepotwierdzona” nie zostały zweryfikowane w źródle internetowym w tym badaniu.

Konwencja oznaczeń: **[ZW]** = zweryfikowane w źródle podanym obok; **[WO]** = wiedza ogólna / wartość z briefu zadania, NIEPOTWIERDZONA w tym badaniu (do sprawdzenia przed użyciem jako „fakt” w dokumentacji moda); **[SZAC]** = szacunek/obliczenie własne na podstawie danych [ZW].

## Streszczenie

Polska leży w klimacie umiarkowanym ciepłym przejściowym: zachód kraju jest pod silniejszym wpływem oceanicznym (łagodne zimy, Szczecin/Wrocław ok. 9,5–9,7 °C rocznie, styczeń ok. 0 °C), wschód i północny wschód — kontynentalnym (Suwałki 7,2 °C rocznie, styczeń −3,3 °C; amplituda roczna 22,5 °C vs 17,5 °C na wybrzeżu), a góry tworzą osobne piętra (Kasprowy Wierch 0,1 °C, Śnieżka 1,4 °C rocznie; gradient ok. 0,54–0,6 °C/100 m). Opady wynoszą ok. 500–600 mm na nizinach (minimum na Kujawach), 700–800 mm na wybrzeżu i pogórzach, do 1200–1700 mm w Tatrach; dni z opadem jest ok. 160 w roku, maksimum opadów przypada latem. Okres wegetacyjny (t > 5 °C) trwa od ok. 212 dni (Suwałki) do 255 dni (Szczecin, Słubice, Legnica) na nizinach i tylko 109–126 dni na szczytach (Kasprowy, Śnieżka); pokrywa śnieżna zalega od ok. 40 dni na zachodzie do ok. 100 dni na Suwalszczyźnie (101 dni) i ponad 200 dni w wysokich Tatrach. Jeziora zamarzają obecnie średnio pod koniec grudnia, lód utrzymuje się ok. 65 dni (spadek z 79 dni w latach 80.), średnia maksymalna grubość to ok. 23 cm (rekordowo 50–66 cm w 1996 r.). IMGW wyróżnia 6 termicznych pór roku (zima ≤ 0 °C, przedwiośnie 0–5, wiosna 5–15, lato ≥ 15, jesień 15–5, przedzimie 5–0 °C), a rozszerzona klasyfikacja Romera–Mareckiego — 8 pór (dodatkowo przedlecie i polecie), które dobrze odpowiadają miesiącom. Fenologia wyróżnia 8 pór (przedwiośnie z leszczyną i przebiśniegiem, pierwiośnie z mniszkiem i czeremchą, pełnia wiosny z kasztanowcem i konwalią, wczesne lato z bzem czarnym, lato z lipą, wczesna jesień z wrzosem, jesień z przebarwianiem liści, zima); między zachodem a wschodem oraz nizinami a górami różnice sięgają 2–3 tygodni. Grzyby: smardze IV–V, kurki VI–XI (szczyt VIII–X), borowiki VII–X (szczyt IX–X), rydze VIII–XI, opieńki IX–XI; wrzesień to miesiąc największej różnorodności grzybów. Zwierzęta: bociany przylatują w marcu/na początku kwietnia i odlatują średnio 27 sierpnia; rykowisko jeleni zaczyna się w drugiej połowie września i trwa ok. 4 tygodnie; bukowisko łosi przypada na wrzesień. Serene Seasons (gałąź 26.1.2, Fabric/Forge/NeoForge, licencja ARR) ma 12 sub-sezonów po 8 dni (96 dni na rok), modyfikuje temperaturę biomu per sub-sezon (zima −0,8; wczesna wiosna i późna jesień −0,25; reszta 0) tylko dla biomów o temperaturze bazowej ≤ 0,8, a śnieg/lód pojawia się poniżej vanillowego progu 0,15 i topnieje losowo z szansą 6,25–25 % zależnie od sub-sezonu (0 w zimie). Kolory trawy/liści to nakładka hex + mnożnik nasycenia per sub-sezon, z osłabieniem do 75 % dla biomów z tagiem „lesser color change”. Naturalne mapowanie: Early Spring = marzec … Late Winter = luty; regionalizację (nadmorski/zachodni/wschodni/wyżynny/górski) trzeba zrealizować przez bazową temperaturę biomów i wysokość, bo config SS jest globalny per sub-sezon. Największe luki badania: pełne tabele miesięczne IMGW (temperatura i opady dla 12 stacji), dokładne liczby dni z pokrywą śnieżną per stacja, dni z mgłą/burzą, dokładne wartości hex kolorów SS oraz kalendarz zwierząt poza jeleniem, łosiem i bocianem.

## 1. Klimat Polski — regiony, wpływy, gradienty

### 1.1. Fakty ogólne (en.wikipedia "Geography of Poland / Climate", okres 1991–2020)

- Średnia roczna temperatura: od ok. 6 °C na północnym wschodzie do ok. 10 °C na południowym zachodzie; na najwyższych szczytach poniżej 0 °C.
- Średni roczny opad dla kraju: ok. 600 mm; w górach do ok. 1300 mm/rok; miejscami (środkowa Polska, Kujawy) poniżej 500 mm.
- Zimą na nizinach ok. połowa opadu spada jako śnieg, w górach — całość.
- Okres wegetacyjny jest o ok. 40 dni dłuższy na południowym zachodzie niż na północnym wschodzie.
- Rekordy (wg en.wikipedia, NIEZWERYFIKOWANE w źródle pierwotnym): maksimum 40,5 °C (podawane jako Słubice, 28 VI 2026 — do potwierdzenia; klasyczny rekord to 40,2 °C Prószków 29 VII 1921), minimum −41,0 °C Siedlce 11 I 1940.

### 1.2. Czynniki i regiony klimatyczne (zpe.gov.pl, "Czynniki kształtujące klimat i jego cechy w Polsce")

- Klimat umiarkowany ciepły przejściowy: ścieranie się mas powietrza polarnomorskiego znad Atlantyku (wzmacnianych Prądem Północnoatlantyckim) i polarnokontynentalnego znad Azji; brak barier górskich o przebiegu południkowym umożliwia swobodną wymianę mas. Przewaga wiatrów zachodnich; duża zmienność pogody z dnia na dzień i z roku na rok.
- Masy powietrza: zimą arktyczne (N), polarnokontynentalne chłodne (E), polarnomorskie ciepłe (W), zwrotnikowe (S, rzadko); latem polarnomorskie chłodne (W), zwrotnikowe kontynentalne (S), polarnokontynentalne ciepłe (E).
- **Granica klimatu morskiego i kontynentalnego**: izoamplituda roczna 20 °C biegnąca południkowo przez środek kraju. Amplituda roczna temperatury: wybrzeże ok. 17,5 °C, północ/zachód < 18 °C, góry < 17 °C (chłodne lato), wschód ok. 22,5 °C (kontynentalizm).
- Regiony klimatyczne wg W. Okołowicza (mapa zpe.gov.pl): pomorski, mazurski, mazowiecko-podlaski, nadwiślański (strefa przejściowa), łódzki, podkarpacki, śląsko-wielkopolski, sudecki, śląsko-małopolski, karpacki, sandomierski, lubelski — każdy z gradacją wpływu oceanicznego/kontynentalnego (silny/średni/słaby). Uwaga: istnieją też inne podziały (A. Woś — 28 regionów; E. Romer — 7 typów klimatu), NIE ZBADANO ich szczegółowo.
- Zachmurzenie: średnio 6,6 (skala 0–10); maks. grudzień 7,9, min. wrzesień 5,7; NE Polska i wysokie góry ok. 7,0. Dni pochmurne ok. 155/rok (40 %; XII ok. 9, VIII ok. 7), dni pogodne ok. 45/rok (11 %; III ok. 5, XI ok. 2).
- Usłonecznienie roczne: 1400 h (3,8 h/dobę) w Sudetach i SW (Katowice < 1400 h, Śląsk 1300 h), 1600 h (4,4 h/dobę) w centrum i nad Bałtykiem (Warszawa 1600 h), do 1700 h (4,7 h/dobę) na wschodzie; NE Polska zimą tylko ok. 0,5 h/dobę; czerwiec do 8 h/dobę na Mazowszu/Podlasiu.
- Opady: średnio ok. 600 mm/rok poza górami; wahania miesięczne 32–104 mm; przewaga opadów letnich (konwekcja), maksimum lipiec (w górach czerwiec). Mapa 1991–2020: Tatry > 1200 mm (do 1700 mm na szczytach — opady orograficzne), Bielsko-Biała, Nowy Sącz, Jelenia Góra, Kłodzko 900–1000 mm, Racibórz, Katowice, Kraków, Tarnów, Krosno, Rzeszów, Resko, Kołobrzeg, Koszalin, Ustka, Łeba, Elbląg 700–800 mm, Góry Świętokrzyskie > 650 mm, reszta kraju < 600 mm; **cień opadowy pojezierzy: Kujawy i Pojezierze Pomorskie (środkowe) 500–550 mm**; Pojezierze Kaszubskie i Bytowskie oraz Wzgórza Szeskie — powyżej średniej.
- Liczba dni z opadem: ok. 160/rok (ok. 13/miesiąc), głównie opady frontalne.
- Wiatry lokalne: halny (góry), bryza (wybrzeże).

### 1.3. Temperatury regionalne (Wikipedia pl "Polska"/"Geografia Polski", normy 1991–2020)

- Średnia roczna: ok. +7 °C na Suwalszczyźnie (Suwałki 7,2 °C) do blisko +10 °C na Nizinie Śląskiej i ziemi lubuskiej (Wrocław 9,7; Legnica, Zielona Góra, Opole 9,6 °C).
- Lipiec na nizinach: od +17,9 °C na północy (Łeba) do +20,1 °C na południu (Wrocław, Tarnów); w górach Śnieżka +9,9 °C, Kasprowy Wierch +8,9 °C.

## 2. Normy klimatyczne IMGW 1991–2020 dla wybranych stacji (temperatura, opady)

Źródło: portal IMGW-PiB "Normy klimatyczne 1991-2020" (https://klimat.imgw.pl/pl/climate-normals/). Portal udostępnia interaktywną tabelę dla stacji synoptycznych z kilkudziesięcioma wskaźnikami: średnia/min/max temperatura dobowa, miesięczne sumy opadów, liczba dni z opadem (progi 0,1 / 1 / 5 / 10 / 50 / 100 / 150 mm), pokrywa śnieżna, ciśnienie, usłonecznienie, prężność pary wodnej.

### 2.1. Średnia temperatura powietrza (norma 1991–2020, IMGW) — wartości odczytane z portalu

| Stacja | Rok [°C] | Styczeń [°C] | Grudzień [°C] |
|---|---|---|---|
| Szczecin | 9,5 | 0,6 | 1,9 |
| Gdańsk | brak danych w tabeli portalu ("NA") — patrz inne źródła niżej | – | – |
| Suwałki | 7,2 | −3,3 | −1,6 |
| Poznań | 9,4 | −0,4 | 0,9 |
| Warszawa | 9,0 | −1,5 | −0,1 |
| Białystok | 7,7 | −2,8 | −1,2 |
| Wrocław | 9,7 | 0,0 | 1,1 |
| Kraków | 8,9 | −1,6 | −0,5 |
| Lublin | 8,2 | −2,5 | −1,0 |
| Zakopane | 6,2 | −3,3 | −2,3 |
| Kasprowy Wierch (1991 m n.p.m.) | 0,1 | −7,4 | −6,1 |
| Śnieżka (1603 m n.p.m.) | 1,4 | −5,9 | −4,7 |

Wnioski z samej tabeli: gradient zachód→wschód na nizinach wynosi ok. 2–2,5 °C rocznie (Szczecin 9,5 / Wrocław 9,7 vs Suwałki 7,2 / Białystok 7,7), a w styczniu ok. 3,5–4 °C (Szczecin +0,6 vs Suwałki −3,3). Gradient wysokościowy: Zakopane (ok. 855 m) 6,2 °C vs Kasprowy Wierch (1991 m) 0,1 °C → ok. 6,1 °C na 1136 m ≈ 0,54 °C/100 m.

### 2.2. Pełne przebiegi miesięczne — STAN BADANIA

NIE ZBADANO w pełni: portal IMGW (tabela interaktywna) nie oddał w pobranej treści kolumn miesięcznych ani opadów, a Wikipedia en ("Geography of Poland") zawiera pełne tabele 1991–2020 tylko dla Warszawy, Wrocławia i Szczecina (nie zostały wyekstrahowane). Poniższe wartości orientacyjne pochodzą z pamięci modelu i są zgodne z potwierdzonymi wartościami rocznymi/stycznia/grudnia (sekcja 2.1), ale **wymagają weryfikacji na https://klimat.imgw.pl/pl/climate-normals/ przed użyciem w dokumentacji moda** [WO].

| Stacja | I | II | III | IV | V | VI | VII | VIII | IX | X | XI | XII | Rok [ZW] | Opad roczny [WO] |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Szczecin | 0,6 | 1,4 | 4,2 | 9,5 | 13,9 | 17,3 | 19,3 | 19,0 | 14,5 | 9,5 | 4,9 | 1,9 | 9,5 | ~540 mm |
| Gdańsk | ~−0,3 | ~0,3 | ~2,8 | ~7,7 | ~12,4 | ~16,0 | ~18,4 | ~18,3 | ~14,0 | ~9,2 | ~4,5 | ~1,1 | ~8,7 [WO] | ~550 mm |
| Suwałki | −3,3 | −2,6 | 0,9 | 7,3 | 12,9 | 16,4 | 18,4 | 17,7 | 12,8 | 7,3 | 2,4 | −1,6 | 7,2 | ~600 mm |
| Poznań | −0,4 | 0,6 | 3,8 | 9,7 | 14,4 | 17,8 | 19,7 | 19,2 | 14,3 | 9,2 | 4,4 | 0,9 | 9,4 | ~530 mm |
| Warszawa | −1,5 | −0,4 | 3,0 | 9,3 | 14,4 | 17,6 | 19,6 | 18,9 | 14,0 | 8,6 | 3,7 | −0,1 | 9,0 | ~550 mm |
| Białystok | −2,8 | −2,0 | 1,5 | 8,0 | 13,5 | 16,8 | 18,8 | 17,9 | 13,0 | 7,6 | 2,7 | −1,2 | 7,7 | ~590 mm |
| Wrocław | 0,0 | 1,0 | 4,3 | 10,0 | 14,6 | 18,0 | 20,1 | 19,5 | 14,7 | 9,6 | 4,7 | 1,1 | 9,7 | ~560 mm |
| Kraków | −1,6 | −0,3 | 3,3 | 9,3 | 14,2 | 17,7 | 19,5 | 19,0 | 14,1 | 9,0 | 4,0 | −0,5 | 8,9 | ~680 mm |
| Lublin | −2,5 | −1,4 | 2,3 | 8,5 | 13,9 | 17,3 | 19,2 | 18,5 | 13,4 | 8,1 | 3,1 | −1,0 | 8,2 | ~570 mm |
| Zakopane (ok. 855 m) | −3,3 | −2,4 | 1,0 | 6,0 | 10,8 | 14,2 | 16,0 | 15,6 | 11,3 | 6,8 | 2,0 | −2,3 | 6,2 | ~1100 mm |
| Kasprowy Wierch (1991 m) | −7,4 | −7,5 | −5,0 | −1,4 | 3,4 | 6,9 | 8,9 [ZW] | 8,7 | 5,2 | 1,7 | −3,0 | −6,1 | 0,1 | ~1800 mm |
| Śnieżka (1603 m) | −5,9 | −5,7 | −3,4 | 0,4 | 5,3 | 8,5 | 9,9 [ZW] | 9,8 | 6,4 | 2,7 | −1,8 | −4,7 | 1,4 | ~1200 mm |

Uwagi: (1) Kolumny I, XII i Rok dla wszystkich stacji poza Gdańskiem oraz VII dla Kasprowego i Śnieżki są potwierdzone [ZW]; pozostałe wartości są orientacyjne [WO]. (2) Klasy opadów z mapy 1991–2020 (zpe.gov.pl) [ZW]: Kraków 700–800 mm, Kołobrzeg/Koszalin/Łeba/Elbląg 700–800 mm, Tatry > 1200 mm (szczyty do 1700 mm), reszta nizin < 600 mm — spójne z powyższymi szacunkami. (3) Dla moda ważniejsze od dokładnych dziesiątych stopnia są **relacje**: NE zimniejsze o ok. 2–2,5 °C rocznie i 3,5–4 °C w styczniu od W; lipiec w górach (8,9–9,9 °C) chłodniejszy o 10 °C od nizin (18–20 °C); zima w Zakopanem jak w Suwałkach (−3,3 °C), ale lato chłodniejsze (16 vs 18,4 °C).

## 3. Pokrywa śnieżna, przymrozki, okres wegetacyjny, lód na jeziorach i rzekach

### 3.1. Okres wegetacyjny (dni ze średnią dobową > 5 °C)

Dwa zestawy liczb z różnych źródeł (różne metody liczenia — mapowe klasy vs. średnie ze stacji):

| Obszar | zpe.gov.pl (mapa, klasy) | Wikipedia pl (stacje, 1991–2020) |
|---|---|---|
| Nizina Śląska, Kotlina Sandomierska, ziemia lubuska, Szczecin | > 220–225 dni (Kraków, Opole, Wrocław, Zielona Góra, Szczecin > 220) | Legnica, Słubice, Szczecin: 255 dni |
| Gorzów, Kołobrzeg, Bydgoszcz, Toruń, Łódź, Katowice, Rzeszów | 210–220 dni | – |
| Jelenia Góra, Kielce, Lublin, Warszawa, Białystok, Gdańsk | 200–210 dni | – |
| Sudety, Karpaty (pogórza), Olsztyn, Suwałki | 190–200 dni | Suwałki: 212 dni |
| Tatry, wysokie góry | < 180 dni; partie szczytowe ok. 100 dni (VI–IX) | Kasprowy Wierch 109 dni, Śnieżka 126 dni |

Różnica zachód–wschód na nizinach: ok. 40 dni (en.wikipedia) — spójne z 255 vs 212.

### 3.2. Pokrywa śnieżna — liczba dni (wyniki cząstkowe)

- Suwałki: średnio 101 dni z pokrywą śnieżną w roku (Wikipedia pl "Równina Augustowska"/"Polska").
- Harta (Pogórze Dynowskie, woj. podkarpackie, ok. 300–400 m n.p.m.): średnio ok. 70 dni, ale ogromna zmienność: ok. 20 dni w sezonie 2019/2020 do > 130 dni w 1995/1996 (Wikipedia pl "Harta").
- Wartości z briefu zadania [WO]: zachód (Szczecin, Wrocław, ziemia lubuska) ok. 30–45 dni; centrum (Warszawa, Poznań) ok. 50–60 dni; wschód (Białystok, Lublin) ok. 70–90 dni; Suwalszczyzna 90–100 dni (Suwałki 101 dni [ZW]); Zakopane ok. 120–140 dni; Śnieżka ok. 180–200 dni; Kasprowy Wierch > 200 dni (szacunkowo 210–230 dni). Dokładne wartości per stacja NIE ZBADANO w źródle pierwotnym (portal IMGW ma wskaźniki pokrywy śnieżnej, ale nie zostały odczytane).
- Zmienność międzyroczna jest ogromna (przykład Harty: 20 vs 130 dni) — dla moda oznacza to, że sensowna jest losowa zmienność liczby "śnieżnych" dni między latami gry (np. ±30 %).
- Daty pierwszego/ostatniego śniegu [WO]: pierwszy opad śniegu — niziny zachodnie druga połowa XI / początek XII, wschód i NE koniec X / początek XI, góry (Tatry) wrzesień–październik; ostatni śnieg — zachód koniec III, NE połowa IV, Tatry (hale) V–VI. Trwała pokrywa: NE od połowy XII do połowy III; zachód epizodyczna (kilka epizodów po 5–15 dni). NIE ZBADANO w źródle pierwotnym.
- Średnia grubość pokrywy [WO]: niziny 5–15 cm (maksima 30–50 cm w mroźne zimy), Suwalszczyzna średnio 15–25 cm, Zakopane średnio 30–50 cm (maks. > 1 m), Kasprowy Wierch średnio 100–200 cm, maksima > 3 m (rekord ok. 355 cm, marzec 1995 — do potwierdzenia). NIE ZBADANO w źródle pierwotnym.

### 3.3. Przymrozki [WO — NIE ZBADANO w źródle pierwotnym]

- Ostatnie przymrozki wiosenne: zachód i wybrzeże — koniec kwietnia; centrum — pierwsza dekada maja ("zimni ogrodnicy" 12–14 V i "zimna Zośka" 15 V to tradycyjne daty ostatnich groźnych przymrozków); NE i kotliny górskie (Kotlina Orawsko-Nowotarska, Zakopane) — do końca maja, sporadycznie w czerwcu.
- Pierwsze przymrozki jesienne: NE i kotliny górskie — koniec września / początek października; centrum — połowa października; zachód i wybrzeże — koniec października / listopad.
- Okres bezprzymrozkowy: ok. 170–190 dni na zachodzie i wybrzeżu, ok. 140–150 dni na Suwalszczyźnie, < 120 dni w kotlinach górskich; w Tatrach powyżej 1500 m przymrozki możliwe w każdym miesiącu.
- Zjawisko zastoisk mrozowych (kotliny, doliny) — lokalnie przymrozki znacznie częstsze niż na zboczach (inwersja temperatury). Istotne dla moda: doliny górskie powinny mieć niższą temperaturę nocną niż stoki.

### 3.4. Zlodzenie jezior i rzek [ZW: naukaoklimacie.pl, dane IMGW-PIB 1981–2020]

| Wskaźnik | Wartość |
|---|---|
| Najdłuższa seria obserwacji zlodzenia jeziora w Polsce | Jezioro Charzykowskie, od 1955 r. |
| Najwcześniejsze pojawienie się lodu (1981–2000) | 3 XI; pełna pokrywa lodowa średnio do 15 XII |
| Najwcześniejsze pojawienie się lodu (po 2000) | 10 XI; pełna pokrywa średnio 28 XII |
| Średni czas trwania pokrywy lodowej, dekada 1981–1990 | 79 dni |
| Średni czas trwania pokrywy lodowej, dekada 2011–2020 | 65 dni (spadek o 14 dni; blisko 30 % jezior straciło > 20 dni) |
| Trend | początek zlodzenia +5 dni/dekadę (łącznie +20 dni później), koniec −2 dni/dekadę (łącznie 8 dni wcześniej) |
| Średnia maksymalna grubość lodu | 23 cm; spadek ok. 2 cm/dekadę (łącznie −8 cm w 1981–2020) |
| Rekordowy rok 1996 | średnia maks. grubość 50 cm; maksimum 66 cm na jeziorze Litygajno (Mazury) |
| Rok 2020 | pierwszy rok, w którym ok. 75 % badanych jezior pozostało bez pokrywy lodowej |
| Pokrywa lodowa | ok. 80 % wszystkich rejestrowanych zjawisk lodowych (reszta: śryż, lód brzegowy) |
| Tempo zamarzania | małe, płytkie jeziora przy silnym mrozie — 1 dzień; duże akweny — kilkanaście dni |
| Mazury, sezon 2025/2026 (raporty lokalne, luty 2026) | 15–50 cm grubości lodu |

- Rzeki: NIE ZBADANO. [WO]: duże rzeki nizinne (Wisła, Odra) zamarzają obecnie rzadko i krótko (śryż, lód brzegowy, zatory w mroźne zimy — Wisła w rejonie Płocka/Włocławka); małe rzeki i strumienie zamarzają przy kilkudniowych mrozach < −10 °C; potoki górskie zwykle nie zamarzają całkowicie (szybki nurt), lód brzegowy i "sople" na progach.
- Implikacja: w modzie jeziora powinny zamarzać z opóźnieniem względem pojawienia się śniegu (śnieg w grudniu, lód pełny pod koniec grudnia) i topnieć ok. 8–10 tygodni później; rzeki (bloki wody płynącej) nie powinny zamarzać, chyba że w "mroźnym" wariancie zimy.

## 4. Mgły, wiatry (halny), burze, dni z opadem

- Dni z opadem [ZW, zpe.gov.pl]: ok. 160 dni w roku (ok. 13 w miesiącu), głównie opady frontalne; w górach więcej (Tatry: NIE ZBADANO liczby, [WO] ok. 200–220 dni).
- Burze [ZW częściowo]: IMGW udostępnia mapę "Średnia liczba dni z burzą w roku" (https://imgw.isok.gov.pl/mapy-klimatologiczne/burze-z-gradem/srednia-burza.html) — mapa jest obrazem, wartości nie zostały odczytane. [WO]: ok. 15–20 dni z burzą na wybrzeżu i NW, 20–25 dni w centrum, 25–35 dni na SE i w Karpatach (najwięcej: Pogórze Karpackie, Tatry); sezon burzowy V–VIII, maksimum VI–VII; burze zimowe bardzo rzadkie (spójne z SS: brak burz zimą).
- Mgły [ZW częściowo]: IMGW udostępnia mapę "Średnia liczba dni z mgłą w roku" (https://imgw.isok.gov.pl/mapy-klimatologiczne/mgla/srednia-mgla.html) — wartości nie zostały odczytane. [WO]: ok. 40–60 dni na nizinach, 60–80 dni w kotlinach i dolinach (Kotlina Kłodzka, Sądecka, dolina Wisły), na szczytach górskich (Śnieżka, Kasprowy) 250–300 dni "we mgle" (chmury); maksimum mgieł radiacyjnych IX–XI, nad ranem; mgły adwekcyjne nad morzem wiosną.
- Halny [ZW, en.wikipedia "Halny"]: fenowy wiatr wiejący w Tatrach (Polska i Słowacja) z południa; większość halnych występuje w październiku i listopadzie, czasem w lutym i marcu, rzadko w pozostałych miesiącach. [WO]: porywy > 100 km/h (rekordy > 200 km/h), ciepły i suchy — powoduje gwałtowne topnienie śniegu i wiatrołomy w reglach (np. 2004 r. w TPN). Cechy do odwzorowania: ciepły, suchy wiatr, czyste niebo nad Tatrami z "wałem" chmur nad grzbietem, łamanie drzew (świerki).
- Bryza [ZW, zpe.gov.pl]: wiatr lokalny nad morzem (dzienna z morza, nocna z lądu).
- Wiatry ogólnie [ZW]: przewaga zachodnich; [WO] najsilniejsze wiatry na wybrzeżu i szczytach (Śnieżka średnio ok. 12 m/s, Kasprowy ok. 6–7 m/s), najsłabsze w kotlinach.
- Zachmurzenie i usłonecznienie — patrz sekcja 1.2 (grudzień najbardziej pochmurny, wrzesień najpogodniejszy).

## 5. Termiczne pory roku (6 pór) — progi i daty w regionach

### 5.1. Definicje (IMGW; klasyfikacja wywodząca się od E. Romera)

W Polsce (klimat umiarkowany przejściowy) IMGW wyróżnia 6 termicznych pór roku na podstawie średniej dobowej temperatury powietrza t:

| Pora termiczna | Kryterium (t = średnia dobowa) | Typowy okres (niziny centralne) |
|---|---|---|
| Zima | t ≤ 0,0 °C | XII–II |
| Przedwiośnie | 0,0 < t ≤ 5,0 °C | III |
| Wiosna | 5,0 < t ≤ 15,0 °C | IV–V (do połowy VI) |
| Lato | t ≥ 15,0 °C | VI–VIII |
| Jesień | 5,0 < t ≤ 15,0 °C (po lecie) | IX–X (XI) |
| Przedzimie | 0,0 < t ≤ 5,0 °C (po jesieni) | XI–XII |

Źródło progów: Wikipedia pl "Kryterium termiczne" i wyniki wyszukiwania (IMGW). Uwaga: rozszerzona klasyfikacja Romera–Mareckiego (Wikipedia pl "Pory roku") dzieli rok na 8 pór: zima ≤0 °C, przedwiośnie 0–5, wiosna 5–10, przedlecie 10–15, lato ≥15, polecie 10–15, jesień 5–10, przedzimie 0–5 °C. Dla moda przydatniejsza jest 8-porowa wersja, bo mapuje się lepiej na 12 sub-sezonów SS (patrz sekcja 12).

- Granice pór wyznacza się z przebiegu średniej dobowej temperatury (wygładzonego), więc nie ma stałych dat — przesuwają się regionalnie (wybrzeże / centrum / góry) i z roku na rok.
- Poznań (repozytorium AMU, "Zmienność termicznych pór roku w Poznaniu"): termiczna zima trwa średnio ok. 70 dni; polecie jest porą o najmniejszej zmienności długości (odchylenie standardowe 8 dni).

### 5.2. Regionalny przebieg (zpe.gov.pl)

- Wiosna termiczna (t > 5 °C) zaczyna się na zachodzie na początku kwietnia; nad Bałtykiem i na wschodzie później (ok. 2–3 tyg.).
- Lato termiczne (t > 15 °C) zaczyna się dość równomiernie na przełomie maja i czerwca; nad Bałtykiem kilka dni później (chłodzące morze); w górach powyżej ok. 1000–1200 m lato termiczne nie występuje wcale (Kasprowy Wierch: średnia lipca 8,9 °C).
- Jesień termiczna (t < 15 °C) zaczyna się na wschodzie już pod koniec sierpnia; na zachodzie i wybrzeżu we wrześniu.
- Zima termiczna (t < 0 °C) zaczyna się na NE kraju pod koniec listopada; na zachodzie w połowie/końcu grudnia (a w łagodne zimy w ogóle nie występuje na wybrzeżu i w dolinie Odry).
- Długość okresu wegetacyjnego (t > 5 °C) — patrz sekcja 3.

(dokładne daty stacji — NIE ZBADANO w źródle pierwotnym; wartości powyżej są jakościowe)

## 6. Piętra klimatyczne w górach

### 6.1. Gradient [ZW + SZAC]

- Ze stacji IMGW (norma 1991–2020): Zakopane (ok. 855 m n.p.m.) 6,2 °C vs Kasprowy Wierch (1991 m) 0,1 °C → 6,1 °C / 1136 m ≈ **0,54 °C/100 m** [SZAC]; Kraków (ok. 237 m) 8,9 °C vs Kasprowy 0,1 °C → 8,8 °C / 1754 m ≈ 0,50 °C/100 m. Typowo przyjmuje się 0,5–0,6 °C/100 m; gradient jest większy latem (ok. 0,6–0,7) niż zimą (ok. 0,3–0,4, inwersje w kotlinach) [WO].
- Śnieżka (1603 m) 1,4 °C vs Jelenia Góra (ok. 342 m, [WO] ok. 8,3 °C) → ok. 0,55 °C/100 m [SZAC].

### 6.2. Piętra klimatyczne Tatr wg M. Hessa (1965) [WO — klasyfikacja podręcznikowa, NIE ZWERYFIKOWANA w tym badaniu]

| Piętro klimatyczne | Średnia roczna temperatura | Zakres wysokości (Tatry, stoki N) | Odpowiednik roślinny |
|---|---|---|---|
| umiarkowanie ciepłe | 6–8 °C | do ok. 650–700 m | pogórze, dolne części regla dolnego |
| umiarkowanie chłodne | 4–6 °C | ok. 700–1100 m | regiel dolny (buk, jodła, świerk) |
| chłodne | 2–4 °C | ok. 1100–1550 m | regiel górny (świerk); górna granica lasu ok. 1550 m |
| bardzo chłodne | 0–2 °C | ok. 1550–1850 m | kosodrzewina (*Pinus mugo*) |
| umiarkowanie zimne | −2–0 °C | ok. 1850–2200 m | hale (murawy alpejskie) |
| zimne | < −2 °C | > 2200 m | turnie (piętro subniwalne) |

- Kasprowy Wierch (1991 m, 0,1 °C) leży na granicy piętra bardzo chłodnego i umiarkowanie zimnego; Śnieżka (1603 m, 1,4 °C) — w piętrze bardzo chłodnym (Karkonosze: górna granica lasu niżej, ok. 1250 m, kosodrzewina 1250–1450 m, piętro alpejskie powyżej) [WO].
- Lato termiczne (t ≥ 15 °C) nie występuje powyżej ok. 1000–1200 m (Kasprowy: lipiec 8,9 °C [ZW]); okres wegetacyjny na Kasprowym 109 dni, na Śnieżce 126 dni [ZW]; pokrywa śnieżna > 200 dni [WO].
- Opady rosną z wysokością do ok. 1700 mm na szczytach Tatr [ZW]; zimą w górach cały opad jako śnieg [ZW, en.wikipedia].
- Inwersje: zimą i jesienią w kotlinach (Nowy Targ, Zakopane) zalega chłodne powietrze — mrozy poniżej −30 °C w Kotlinie Orawsko-Nowotarskiej (Jabłonka), podczas gdy stoki są cieplejsze [WO].

## 7. Fenologia roślin — kalendarz i różnice regionalne

### 7.1. Fenologiczne pory roku (Wikipedia pl "Pory roku")

| Pora fenologiczna | Wskaźnik (roślina / faza) |
|---|---|
| Przedwiośnie | topnienie śniegu; kwitnienie (pylenie) leszczyny pospolitej (*Corylus avellana*) |
| Pierwiośnie | kwitnienie mniszka lekarskiego (*Taraxacum officinale*), czeremchy zwyczajnej (*Padus avium* / *Prunus padus*) |
| Wiosna (pełnia wiosny) | zazielenienie lasów liściastych; kwitnienie kasztanowca białego (*Aesculus hippocastanum*) |
| Wczesne lato | kwitnienie bzu czarnego (*Sambucus nigra*) |
| Lato | kwitnienie lipy (*Tilia* spp.); dojrzewanie owoców |
| Wczesna jesień | kwitnienie wrzosu zwyczajnego (*Calluna vulgaris*) |
| Jesień | przebarwianie i opadanie liści |
| Zima | spoczynek wegetacyjny |

### 7.2. Osiem pór fenologicznych wg Ogrodu Botanicznego UAM (Poznań) [ZW: https://ogrod.amu.edu.pl/fenologiczne-pory-roku/]

| Pora | Charakterystyka | Rośliny wskaźnikowe (UAM) |
|---|---|---|
| Przedwiośnie (zaranie wiosny) | kwitnienie roślin wiatropylnych przed ulistnieniem | śnieżyczka przebiśnieg (*Galanthus nivalis*), leszczyna pospolita (*Corylus avellana*), olsza czarna (*Alnus glutinosa*) |
| Pierwiośnie (wczesna wiosna) | okres przejściowy, nieustabilizowana pogoda | pierwiosnek lekarski (*Primula veris*), mniszek lekarski (*Taraxacum officinale*) |
| Pełnia wiosny (późna wiosna) | wzrost nasłonecznienia i temperatury | jarząb pospolity (*Sorbus aucuparia*), żarnowiec miotlasty (*Cytisus scoparius*) |
| Wczesne lato | dzień się wydłuża, rośnie temperatura | bez czarny (*Sambucus nigra*), dereń świdwa (*Cornus sanguinea*) |
| Lato (późne lato) | najcieplejszy okres, stabilna pogoda | lipa drobnolistna (*Tilia cordata*), wrotycz pospolity (*Tanacetum vulgare*) |
| Wczesna jesień | dzień się skraca, dojrzewanie owoców | kwitnienie bluszczu pospolitego (*Hedera helix*) |
| Jesień (pełna jesień) | przebarwianie liści, przygotowanie do spoczynku | przebarwianie liści klonów (*Acer*), lip (*Tilia*), dębów (*Quercus*), buków (*Fagus sylvatica*), grabów (*Carpinus betulus*) |
| Zima | spoczynek; definicja UAM: „trzy kolejno następujące po sobie dni z maksymalną temperaturą poniżej 0 °C” | – |

- Daty dla Poznania (UAM, seria 2019–2026, przykłady): przedwiośnie 2024 rozpoczęło się 16 II, pierwiośnie 2024 — 30 III [ZW]. Wyniki wyszukiwania (tygodnik-rolniczy, salamandra.org.pl) [ZW]: przedwiośnie może trwać ponad 2,5 miesiąca; zaczyna się czasem już w ostatnich dniach stycznia, a czasem dopiero na początku kwietnia; typowo od początku lutego do początku marca (zachód Polski), pierwiośnie — marzec (wg tygodnika; w praktyce w centrum kraju koniec III–IV), pełnia lata — od połowy czerwca do połowy lipca (kwitnienie lipy, dojrzewanie porzeczek).
- Dodatkowe gatunki przedwiośnia [ZW, tygodnik-rolniczy/salamandra]: krokus spiski (*Crocus scepusiensis*), zawilec gajowy (*Anemone nemorosa*), przylaszczka pospolita (*Hepatica nobilis*), wawrzynek wilczełyko (*Daphne mezereum*), podbiał pospolity (*Tussilago farfara*), knieć błotna/kaczeniec (*Caltha palustris*). Pierwiośnie: mniszek, czeremcha, pierwiosnek, pierwsze liście brzozy. Pełnia wiosny: konwalia majowa (*Convallaria majalis*), kasztanowiec (*Aesculus hippocastanum*), lilak (*Syringa vulgaris*), klon, czereśnia.

### 7.3. Kalendarz fenologiczny roślin dla Polski centralnej (niziny, np. Warszawa/Łódź) — synteza

Daty średnie; [ZW] = potwierdzone wyżej, pozostałe [WO] (brief zadania + wiedza ogólna, NIE ZWERYFIKOWANE w źródle pierwotnym — IMGW prowadzi obserwacje fenologiczne, ale ich tabele nie zostały pozyskane).

| Faza | Gatunek (pl / łac.) | Termin — Polska centralna | Zachód / wybrzeże | NE (Suwalszczyzna) | Góry (regiel dolny, ~900 m) |
|---|---|---|---|---|---|
| kwitnienie | śnieżyczka przebiśnieg *Galanthus nivalis* | II/III (2–3 dekada II) [ZW: przedwiośnie] | koniec I–II | III | III/IV |
| pylenie | leszczyna pospolita *Corylus avellana* | II/III [ZW: przedwiośnie; Poznań 16 II 2024] | II | III (1–2 dek.) | III/IV |
| pylenie | olsza czarna *Alnus glutinosa* | koniec II–III [ZW: przedwiośnie] | II | III | IV |
| kwitnienie | wawrzynek wilczełyko *Daphne mezereum* | III [ZW: przedwiośnie] | koniec II | III/IV | IV |
| kwitnienie | podbiał *Tussilago farfara*, przylaszczka *Hepatica nobilis* | III [ZW: przedwiośnie] | III | koniec III/IV | IV |
| kwitnienie | zawilec gajowy *Anemone nemorosa* | koniec III–IV [ZW: przedwiośnie/pierwiośnie] | III/IV | IV (2–3 dek.) | IV/V |
| kwitnienie | pierwiosnek lekarski *Primula veris*, mniszek *Taraxacum officinale* | IV (2–3 dek.) [ZW: pierwiośnie; Poznań 30 III 2024] | IV (1–2 dek.) | koniec IV/V | V |
| listnienie | brzoza brodawkowata *Betula pendula* | ok. 20 IV [WO] | 10–15 IV | koniec IV/początek V | V (1–2 dek.) |
| listnienie | modrzew europejski *Larix decidua* | ok. 15–20 IV [WO] | IV (1–2 dek.) | koniec IV | V |
| kwitnienie | czeremcha zwyczajna *Prunus padus* | koniec IV / początek V [ZW: pierwiośnie] | IV (3 dek.) | V (1–2 dek.) | V (2–3 dek.) |
| kwitnienie | czereśnia ptasia *Prunus avium* | koniec IV / początek V [ZW: pełnia wiosny] | IV (3 dek.) | V (1 dek.) | V (2 dek.) |
| listnienie | dąb szypułkowy *Quercus robur* | ok. 5 V [WO] | koniec IV | V (2 dek.) | – (poza zasięgiem w wyższych położeniach) |
| listnienie | buk zwyczajny *Fagus sylvatica* | koniec IV / początek V [WO] | koniec IV | (poza naturalnym zasięgiem NE) | V (2 dek.) |
| listnienie | lipa drobnolistna *Tilia cordata* | początek V [WO] | koniec IV | V (2 dek.) | V (3 dek.) |
| kwitnienie | jabłoń *Malus domestica* | V (1–2 dek.) [WO] | koniec IV/V | V (2–3 dek.) | V (3 dek.)/VI |
| kwitnienie | kasztanowiec *Aesculus hippocastanum* | V (1–2 dek.) [ZW: pełnia wiosny] | V (1 dek.) | V (3 dek.) | VI |
| kwitnienie | konwalia majowa *Convallaria majalis* | V (2–3 dek.) [ZW: pełnia wiosny] | V (2 dek.) | koniec V/VI | VI |
| kwitnienie | jarząb pospolity *Sorbus aucuparia* | V (2–3 dek.) [ZW: pełnia wiosny] | V (2 dek.) | koniec V/VI | VI |
| kwitnienie | robinia akacjowa *Robinia pseudoacacia* | koniec V / początek VI [WO] | koniec V | VI (1–2 dek.) | (rzadka) |
| kwitnienie | bez czarny *Sambucus nigra* | koniec V / VI (1 dek.) [ZW: wczesne lato] | koniec V | VI (2 dek.) | VI (3 dek.) |
| kwitnienie | lipa drobnolistna *Tilia cordata* | koniec VI / VII (1 dek.) [ZW: lato; „połowa VI–połowa VII”] | koniec VI | VII (2 dek.) | VII (3 dek.) |
| kwitnienie | wrzos zwyczajny *Calluna vulgaris* | VIII (2–3 dek.) – IX [ZW: wczesna jesień] | VIII | koniec VIII/IX | IX |
| kwitnienie | bluszcz pospolity *Hedera helix* | IX–X [ZW: wczesna jesień, UAM] | IX | (rzadki) | (rzadki) |

Reguła regionalna [ZW jakościowo / WO liczbowo]: fazy wiosenne na zachodzie wyprzedzają centrum o ok. 5–10 dni, a NE i Suwalszczyznę o ok. 2–3 tygodnie; w górach opóźnienie ok. 3–4 dni na każde 100 m wysokości (ok. 1 tydzień na 200 m). Fazy jesienne przebiegają w odwrotnej kolejności (NE i góry wcześniej o 1–2 tygodnie). Wybrzeże: wiosna opóźniona (chłodne morze), jesień wydłużona.

## 8. Owoce, nasiona, grzyby — dojrzewanie i sezony zbioru

### 8.1. Grzyby [ZW: wyniki wyszukiwania — sezonnagrzyby.pl, krainagrzybow.pl, beszamel.pl, powiat.kielce.pl; brak potwierdzenia ze strony Lasów Państwowych]

| Gatunek (pl / łac.) | Sezon występowania | Szczyt owocnikowania |
|---|---|---|
| smardz jadalny *Morchella esculenta* | IV – koniec V | IV / początek V (do końca V zwykle zanikają) |
| pieprznik jadalny (kurka) *Cantharellus cibarius* | VI – XI | VIII, IX, X |
| borowik szlachetny *Boletus edulis* | VII – X | IX, X |
| mleczaj rydz *Lactarius deliciosus* | VIII – XI | X, XI |
| opieńka miodowa *Armillaria mellea* | IX – XI | X |
| podgrzybek brunatny *Imleria badia*, maślaki *Suillus*, koźlarze *Leccinum*, gąski *Tricholoma* | VIII – XI [ZW: „wrzesień — większość gatunków”] | IX–X |

- Wrzesień jest miesiącem największej różnorodności i liczebności grzybów w Polsce [ZW]; najlepszy okres to połowa lata i jesień, pod warunkiem ciepłych deszczy latem [ZW] — czyli grzyby powinny w modzie zależeć od opadu w poprzednich dniach i temperatury (nie tylko od sub-sezonu).
- Pierwsze przymrozki (X) kończą sezon borowików; opieńki i rydze znoszą lekkie przymrozki (X–XI) [WO].
- [WO] Inne: czubajka kania *Macrolepiota procera* VII–X; koźlarz babka *Leccinum scabrum* pod brzozami VII–X; grzyby nadrzewne (huba, żółciak siarkowy *Laetiporus sulphureus* V–IX; boczniak ostrygowaty *Pleurotus ostreatus* X–XII, także zimą po odwilży); muchomor czerwony *Amanita muscaria* VIII–XI.

### 8.2. Owoce i nasiona [WO — brief zadania + wiedza ogólna; NIE ZWERYFIKOWANE w tym badaniu, poza fenologią pór roku UAM („wczesna jesień = dojrzewanie owoców”, „lato = dojrzewanie porzeczek”)]

| Gatunek (pl / łac.) | Dojrzewanie (Polska centralna) | Uwagi |
|---|---|---|
| poziomka pospolita *Fragaria vesca* | VI (2–3 dek.) – VII | zręby, skraje lasu, świetliste bory |
| porzeczka (dzika) *Ribes* spp. | VII [ZW: „pełnia lata — dojrzewanie porzeczek”] | olsy, łęgi |
| borówka czarna *Vaccinium myrtillus* | VII (1 dek.) – VIII | bory sosnowe, regiel górny; w górach VII/VIII |
| borówka brusznica *Vaccinium vitis-idaea* | VIII – IX | bory suche; owoce zostają zimą pod śniegiem |
| malina właściwa *Rubus idaeus* | VII – VIII | zręby, wiatrołomy, polany |
| jeżyna *Rubus fruticosus* agg. | VIII – IX | skraje lasów, zarośla |
| czeremcha *Prunus padus* | VII – VIII | pokarm ptaków |
| bez czarny *Sambucus nigra* | VIII – IX | ptaki |
| jarząb pospolity (jarzębina) *Sorbus aucuparia* | IX (pełne dojrzewanie), owoce trwają do zimy | kluczowy pokarm zimowy jemiołuszek, gili, drozdów |
| leszczyna (orzech laskowy) *Corylus avellana* | koniec VIII – IX | wiewiórki, sójki, orzesznice |
| dąb (żołędzie) *Quercus robur / Q. petraea* | koniec IX – X (opad) | lata nasienne co 3–7 lat; dzik, sójka, jeleń |
| buk (buczyna, bukiew) *Fagus sylvatica* | X | lata nasienne co 5–8 lat; dzik, orzesznica, zięba |
| grab, klon, lipa, jesion (skrzydlaki, orzeszki) | IX – X, opad do zimy | – |
| róża dzika *Rosa canina* | IX – X, owoce trwają na krzewie zimą | witamina C dla ptaków i zwierząt |
| tarnina *Prunus spinosa* | X, jadalne po pierwszych przymrozkach (X/XI) | gęste zarośla — kryjówki |
| głóg *Crataegus* spp. | IX – X, trwają zimą | – |
| kalina koralowa *Viburnum opulus* | IX – X, trwają zimą | – |
| szyszki sosny (nasiona) *Pinus sylvestris* | dojrzewają X–XII, otwierają się i wysiewają III–V | krzyżodziób, wiewiórka, dzięcioły („kuźnie”) |
| szyszki świerka *Picea abies* | X – XI, wysiew II–IV | wiewiórka, krzyżodziób |
| jemioła *Viscum album* | XI – III (owoce zimą) | jemiołuszki, paszkoty |
| bukiew/żołędzie, orzechy laskowe — zapasy | IX – X (gromadzenie), II–IV (wykopywanie) | sójka, wiewiórka, orzesznica, myszy |

## 9. Przebarwianie i opad liści, stan bezlistny

- [ZW, UAM] Fenologiczna jesień = przebarwianie liści klonów (*Acer platanoides*, *A. pseudoplatanus*), lip (*Tilia*), dębów (*Quercus*), buków (*Fagus*), grabów (*Carpinus*). Wczesna jesień zaczyna się kwitnieniem bluszczu i wrzosu (VIII/IX).
- [WO — brief zadania + wiedza ogólna; NIE ZWERYFIKOWANE liczbowo] Kalendarz dla Polski centralnej:

| Gatunek | Początek przebarwiania | Pełne przebarwienie | Opad liści (koniec) | Kolor jesienny |
|---|---|---|---|---|
| brzoza brodawkowata *Betula pendula* | koniec IX | 1–2 dek. X | koniec X / początek XI | żółty |
| lipa drobnolistna *Tilia cordata* | koniec IX | 1–2 dek. X | 3 dek. X | żółty |
| klon zwyczajny *Acer platanoides* | koniec IX / 1 dek. X | 2–3 dek. X | koniec X / początek XI | żółty do pomarańczowego |
| jesion wyniosły *Fraxinus excelsior* | 1 dek. X (zrzuca zielone/żółtawe) | – | 2–3 dek. X (jeden z pierwszych) | żółtozielony |
| kasztanowiec *Aesculus hippocastanum* | IX (często wcześniej przez szrotówka) | 1 dek. X | 2–3 dek. X | brązowożółty |
| osika *Populus tremula* | koniec IX | X | koniec X | żółty/pomarańczowy |
| grab pospolity *Carpinus betulus* | 1–2 dek. X | 3 dek. X | XI (część liści zostaje na zimę, jak u buka młodego) | żółty → brązowy |
| buk zwyczajny *Fagus sylvatica* | 1 dek. X | 2–3 dek. X | koniec X – XI (młode buki i podrost trzymają suche liście do wiosny — marcescencja) | złoty → miedziany → brązowy |
| dąb szypułkowy / bezszypułkowy *Quercus robur / petraea* | 2–3 dek. X | koniec X / 1 dek. XI | XI (do początku XII; część liści zimą) | żółtobrązowy, brązowy |
| olsza czarna *Alnus glutinosa* | X (opada zielona, bez przebarwienia) | – | koniec X / XI | zielony/brązowy |
| modrzew europejski *Larix decidua* | 2–3 dek. X | koniec X / 1 dek. XI | XI (2–3 dek.) | złotożółty |
| czeremcha, bez czarny, leszczyna | koniec IX / X | X | koniec X | żółty |
| jarzębina *Sorbus aucuparia* | koniec IX | X | X/XI | pomarańczowy/czerwony |

- Stan bezlistny lasów liściastych: od ok. połowy XI do połowy IV (brzoza ok. 20 IV, dąb ok. 5 V zamyka fazę listnienia) — ok. 5 miesięcy; w NE i górach dłużej (do końca IV/V), na zachodzie krócej (od końca XI do 10–15 IV) [WO].
- Regionalnie: przebarwianie zaczyna się wcześniej w NE i w górach (o ok. 1–2 tygodnie), później na zachodzie i wybrzeżu; pierwsze przymrozki i wiatry przyspieszają opad; ciepła, sucha jesień wydłuża fazę barw [WO].
- W górach: modrzew i buk w reglu dolnym przebarwiają się już od końca IX; kosodrzewina i świerk pozostają zielone [WO].

## 10. Fenologia zwierząt — tabela miesiąc → wydarzenia

### 10.1. Fakty potwierdzone [ZW]

- Rykowisko jelenia szlachetnego (*Cervus elaphus*) rozpoczyna się w drugiej połowie września i trwa około 4 tygodni (przełom IX/X) — Nadleśnictwo Trzebielino (Lasy Państwowe), polskieradio.pl.
- Bukowisko łosia (*Alces alces*) odbywa się we wrześniu — polskieradio.pl / apoczywaj.pl.
- Nazewnictwo (PZŁ): rykowisko = gody jelenia; bekowisko = gody daniela (*Dama dama*) i (w potocznym użyciu także) sarny; bukowisko = gody łosia; huczka = gody dzika (*Sus scrofa*); scypuł = owłosiona skóra na rosnącym porożu jeleni, łosi, saren; gawra = zimowe legowisko niedźwiedzia.
- Bocian biały (*Ciconia ciconia*): w Polsce od marca do września (pora lęgowa); przylot w marcu / na początku kwietnia; średni termin odlotu 27 sierpnia (część ptaków przed 15 VIII, pierwsze pod koniec VII); główna fala odlotu w ostatniej dekadzie sierpnia; młode opuszczają lęgowiska na przełomie VIII/IX — GDOŚ (gov.pl), ekologia.pl, interia.pl.
- Kalendarz łowiecki (poradniklowiecki.pl): sezonowość polowań ma chronić okresy wrażliwe — rykowisko, bukowisko, wykoty (wycielenia) i prowadzenie młodych.

### 10.2. Kalendarz roczny — synteza (miesiąc → wydarzenia). Legenda: [ZW] potwierdzone wyżej; pozostałe [WO] = brief zadania + wiedza ogólna, NIE ZWERYFIKOWANE w tym badaniu

| Miesiąc | Ssaki | Ptaki | Płazy, gady, owady, ryby |
|---|---|---|---|
| I | dziki — huczka trwa (XI–I); wilki (*Canis lupus*) — cieczka (I–II, szczyt luty); niedźwiedź (*Ursus arctos*) — w gawrze, narodziny młodych (I/II, 1–3 młode); jeleń — chmary zimowe (łanie z cielętami oddzielnie od byków), żerowanie na pędach i korze; łoś — zrzuca poroże (XI–I); sarna (*Capreolus capreolus*) — rudle zimowe, kozły w scypule (nowe poroże rośnie X/XI–IV); żubr (*Bison bonasus*) — stada zimowe przy paśnikach (Puszcza Białowieska: dokarmianie sianem XII–III); bóbr (*Castor fiber*) — aktywny pod lodem, żeruje z magazynu gałęzi przy żeremiu, gody I–II; ryś (*Lynx lynx*) — ruja I–III; jeż (*Erinaceus*) — hibernacja (X–IV); nietoperze — hibernacja w jaskiniach/piwnicach (X/XI–III/IV); borsuk (*Meles meles*) — sen zimowy (przerywany w odwilże, XI/XII–II/III); gronostaj (*Mustela erminea*) i łasica (*M. nivalis*, tylko część populacji) — białe futro zimowe; zając (*Lepus europaeus*) — początek „marcowania” (gody I–III, szczyt III); wiewiórka (*Sciurus vulgaris*) — korzysta z zapasów, gody I–II | zimujące: gil (*Pyrrhula pyrrhula*), jemiołuszka (*Bombycilla garrulus*, nalot z północy X–III, żeruje na jarzębinie i jemiole), czeczotka, jer, myszołów włochaty; krzyżodziób świerkowy (*Loxia curvirostra*) — lęgi zimowe (I–III) przy urodzaju szyszek; kruk — początek toków; puchacz — huczy (I–II); sójka, sikory, dzięcioły, kowalik — stałe; kaczki i łabędzie na niezamarzniętych wodach | płazy — w hibernacji (w mule, pod ściółką, żaba trawna często na dnie zbiorników); ryby — pod lodem, mało aktywne (miętus — tarło pod lodem XII–II) |
| II | wilki — szczyt cieczki; jeleń — najstarsze byki zaczynają zrzucać poroże (koniec II–IV); lis (*Vulpes vulpes*) — cieczka (I–II), futro zimowe do III; sarna — kozły w scypule; dzik — koniec huczki; niedźwiedź — w gawrze z młodymi; bóbr — gody pod lodem | pierwsze skowronki (*Alauda arvensis*) i szpaki (*Sturnus vulgaris*) w łagodne zimy (koniec II); gęsi zbożowe/białoczelne — początek przelotu (II/III); żuraw (*Grus grus*) — pierwsze powroty przy odwilży (koniec II); puszczyk — toki, lęgi (II–III); bielik — składa jaja (II/III) | – |
| III | zając — szczyt marcowania; jeleń — zrzucanie poroża (III–IV, młode byki najpóźniej); łoś — początek wzrostu nowego poroża; niedźwiedź — opuszcza gawrę (III/IV, samice z młodymi później, IV/V); borsuk, jeż — budzą się (koniec III/IV); nietoperze — wylot z zimowisk (III/IV); wiewiórka — pierwsze mioty (III–IV); ryś — ruja; lis — szczenięta rodzą się w norze (III/IV) | przylot: skowronek, szpak, czajka (*Vanellus vanellus*), żuraw (III, szczyt 2–3 dek.), gęsi, bocian biały (koniec III / początek IV [ZW]), pliszka siwa, zięba; odlot ptaków zimujących (jemiołuszki, gile północne, III/IV); głuszec (*Tetrao urogallus*) — początek toków (III/IV); cietrzew (*Lyrurus tetrix*) — toki od III | żaba trawna (*Rana temporaria*), żaba moczarowa (*R. arvalis*; samce niebieskie), ropucha szara (*Bufo bufo*) — wędrówki godowe do wód i gody (koniec III–IV, przy > 5 °C w nocy i deszczu); traszki — wędrówki do wody; pierwsze motyle zimujące (rusałka pawik, cytrynek) w ciepłe dni; szczupak — tarło (III/IV); trzmiele — królowe |
| IV | wilki — narodziny szczeniąt (koniec IV / V, 4–7 w miocie); sarna — kozły wycierają scypuł (III/IV), kozy w ciąży (embrionalna diapauza!); jeleń — nowe poroże rośnie w scypule; dzik — prosięta (III–V, szczyt IV); niedźwiedź — samice z młodymi opuszczają gawrę; borsuk — młode (II–III w norze, wychodzą IV/V); bóbr — młode rodzą się (V–VI), roślinność zielona; jeż — koniec hibernacji (IV) | głuszec — szczyt tokowiska (IV, o świcie); cietrzew — szczyt toków (IV, tokowiska na torfowiskach i polanach); jaskółka dymówka (*Hirundo rustica*) — przylot (IV, 1–2 dek.), oknówka (koniec IV); kukułka (koniec IV); bocian — lęgi (składanie jaj IV, wysiadywanie ok. 33 dni); słonka — ciągi (III–V); większość ptaków — budowa gniazd, lęgi (IV–VI) | gody ropuch i żab trwają, skrzek; kumaki (*Bombina*), rzekotka (*Hyla arborea*) — gody IV–V (chóry w maju); zaskroniec, jaszczurki — po hibernacji (IV); mrówki rudnice — kopce aktywne; chrabąszcz majowy — rójka (koniec IV / V) |
| V | sarna — koźlęta (V–VI, zwykle 2); łoś — łoszaki (V–VI); żubr — cielęta (V–VII); jeleń — cielęta (koniec V–VI); wilki — szczenięta w norze; kuny (*Martes*) — młode (III–IV); nietoperze — kolonie rozrodcze samic (V–VIII); lis — futro letnie (rzadkie) | jerzyk (*Apus apus*) — przylot (1 dek. V), wilga (*Oriolus oriolus*, V), derkacz; szczyt lęgów; bocian — pisklęta (koniec V / VI); żuraw — pisklęta (V); słowik — śpiew (V–VI) | rzekotki — chóry; żaby zielone (*Pelophylax*) — gody (V–VI, „rechot”); majówki, chrabąszcze; komary, kleszcze — szczyt aktywności (V–VI, ponownie IX) |
| VI | jeleń — poroże w scypule dorasta; sarna — koźlęta ukryte w trawie/zbożu (kosiarki!); dzik — lochy z prosiętami; niedźwiedź — gody (V–VII); wiewiórka — drugi miot (VI–VII); jeż — młode (VI–VIII); bóbr — młode wychodzą z żeremia | pisklęta, podloty; bocian — młode w gnieździe (loty od VII); kukułka milknie (koniec VI); zimorodek — lęgi | żaby zielone; świetliki (VI/VII, noce świętojańskie); jelonek rogacz — rójka (VI); rusałki; kleszcze |
| VII | sarna — bekowisko/ruja (2 poł. VII – VIII, „gonienie” kóz przez kozły); jeleń — poroże wykształcone, wycieranie scypułu (koniec VII – VIII); łoś — poroże w scypule; daniel — scypuł czyszczony VIII/IX; wilki — szczenięta wychodzą, „rendez-vous”; niedźwiedź — żeruje na borówkach, malinach; nietoperze — młode latają (VII) | bocian — młode wylatują (koniec VII); pierwsze przeloty siewkowców na S (VII/VIII); wiele ptaków milknie, pierzenie (VII–VIII); jerzyki — odlot (koniec VII / początek VIII) | żaby — młode (żabki) wychodzą z wody (VII); motyle (paź, mieniaki); szerszenie; kleszcze — mniej |
| VIII | sarna — koniec rui (VIII); jeleń — wycieranie scypułu, byki odbudowują tłuszcz; łoś — wycieranie scypułu (koniec VIII); dzik — żeruje w zbożach/kukurydzy; niedźwiedź — hiperfagia (VIII–X); wiewiórka, sójka — początek gromadzenia zapasów (VIII/IX); borsuk — tuczenie | bocian — odlot: pierwsze przed 15 VIII, średnio 27 VIII, główna fala 3 dek. VIII [ZW]; jaskółki, wilgi — zbierają się (koniec VIII); bocian czarny — odlot VIII/IX; dudek, kukułka — odlot VIII | żmija (*Vipera berus*) — rodzi młode (VIII/IX); jaszczurki — młode; rójki mrówek; pająki krzyżaki; przełom VIII/IX — „babie lato” |
| IX | łoś — bukowisko (IX [ZW]); jeleń — początek rykowiska (2 poł. IX [ZW], trwa ok. 4 tyg.); niedźwiedź — intensywne żerowanie (bukiew, żołędzie, owoce); dzik — żołędzie i bukiew, tuczenie; borsuk, jeż — tuczenie; wiewiórka — magazynuje orzechy, żołędzie, szyszki; nietoperze — gody i „rojenie” przy jaskiniach (VIII–IX), migracje; lis — zaczyna futro zimowe (IX–XI) | żurawie — zloty na noclegowiskach (IX–X); jaskółki — odlot (IX, 1–2 dek.); gęsi — przelot (IX/X); sójki — gromadzą żołędzie (IX–X); pierwsze jemiołuszki bardzo rzadko (X) | żaby — koniec aktywności, wędrówki do zimowisk (IX–X); ropuchy — do ściółki; jeże (ssaki) — zapasy tłuszczu |
| X | jeleń — końcówka rykowiska (do 1–2 dek. X); daniel (*Dama dama*) — bekowisko (X, szczyt połowa X); sarna — kozły zrzucają poroże (X–XII, najstarsze najwcześniej), nowe rośnie w scypule; łoś — poroże wykształcone, zrzuca XI–I; muflon — ruja (X–XII); dzik — początek huczki (XI–I; w ciepłych latach X); niedźwiedź — szuka gawry (koniec X–XII); borsuk — ostatnie żerowania; jeż — hibernacja od X (przy < 10 °C); nietoperze — do zimowisk (X/XI); gronostaj — linienie na białe (X–XI); wiewiórka — zapasy | żurawie — odlot (X / początek XI); gęsi — masowe przeloty; drozdy (kwiczoły) z północy; gil — nalot z N (X/XI; część populacji lęgowa krajowa); jemiołuszki — pierwsze (X/XI); bielik, kruk — stałe; puchacz, puszczyk — jesienne pohukiwania | żaby, ropuchy, traszki — hibernacja (od X); jaszczurki, węże — hibernacja; komary/kleszcze — koniec (po przymrozkach); pstrąg potokowy — tarło (X–XII) |
| XI | łoś — zrzuca poroże (XI–I); dzik — huczka (XI–I, szczyt XII); niedźwiedź — zalega w gawrze (XI/XII, zależnie od pogody i tłuszczu; w Bieszczadach coraz częściej „niedźwiedzie niezasypiające”); borsuk — sen zimowy (XI/XII–II/III); jeleń — chmary zimowe, przejście na pokarm zimowy (pędy, kora — spałowanie); sarna — rudle zimowe; wilki — wataha razem, polowania na jelenie; lis — futro zimowe pełne; gronostaj — biały; bóbr — kończy magazyn gałęzi pod wodą, wzmacnia tamy; wiewiórka — mniej aktywna, gniazdo | jemiołuszki, gile, czeczotki — nalot; kaczki — na niezamarzniętych wodach; sikory, dzięcioły — przy karmnikach (dokarmianie od XI/XII); łabędzie krzykliwe — zimowanie | ryby — zimowiska w głębinach; sieja, sielawa — tarło (XI–XII); traszki — czasem w piwnicach |
| XII | dziki — szczyt huczki; wilki — wataha, początek cieczki (koniec XII); niedźwiedź — gawra; żubr — dokarmianie (XII–III), stada zimowe do 100 osobn. w Białowieży; jeleń, daniel — paśniki, lizawki; sarna — rudle; bóbr — pod lodem; łoś — zrzuca poroże; nietoperze, jeż, borsuk — sen; zając — futro nie bieleje (w Polsce nie ma zająca bielaka poza NE historycznie) | zimowe stada ptaków: gile, jemiołuszki, czeczotki, jery; mieszane stada sikor, mysikrólików, kowalików, dzięciołków; kruki, bieliki przy padlinie; krzyżodzioby — budowa gniazd (XII–I) | miętus — tarło pod lodem (XII–II); wszystko inne — spoczynek |

### 10.3. Poroże, linienie, sen zimowy — zestawienie [WO — brief + wiedza ogólna, NIE ZWERYFIKOWANE poza pozycjami [ZW]]

| Gatunek | Zrzucanie poroża | Nowe poroże (scypuł) | Wycieranie scypułu | Gody | Młode |
|---|---|---|---|---|---|
| jeleń szlachetny *Cervus elaphus* | II–IV (stare byki najpierw) | III–VII | VII/VIII | rykowisko: 2 poł. IX – X, ok. 4 tyg. [ZW] | V–VI (1 cielę) |
| łoś *Alces alces* | XI–I | III–VIII | VIII/IX | bukowisko: IX [ZW] (do X) | V–VI (1–2 łoszaki) |
| sarna *Capreolus capreolus* | X–XII | XI–III | III/IV | ruja („bekowisko” potoczne): 2 poł. VII – VIII | V–VI (1–3 koźlęta; diapauza embrionalna do XII/I) |
| daniel *Dama dama* | IV–V | V–VIII | VIII/IX | bekowisko: X (szczyt połowa X) | VI–VII |
| dzik *Sus scrofa* | – | – | – | huczka: XI–I (szczyt XII) | III–V (4–8 prosiąt), czasem drugi miot |
| żubr *Bison bonasus* | rogi stałe | – | – | VIII–IX | V–VII |
| niedźwiedź brunatny *Ursus arctos* | – | – | – | V–VII (opóźniona implantacja) | I/II w gawrze (1–3) |
| wilk *Canis lupus* | – | – | – | cieczka I–II (szczyt II) | koniec IV – V (4–7) |

| Gatunek | Zimowy stan | Okres |
|---|---|---|
| niedźwiedź brunatny | sen zimowy w gawrze (temperatura ciała obniżona nieznacznie; łatwo się budzi) | XI/XII – III/IV (samice z młodymi do IV/V) |
| borsuk *Meles meles* | sen zimowy (przerywany) | XI/XII – II/III |
| jeż *Erinaceus roumanicus / europaeus* | hibernacja właściwa | X – IV |
| nietoperze (*Myotis*, *Plecotus*, *Rhinolophus* …) | hibernacja w jaskiniach, fortach, piwnicach | X/XI – III/IV |
| popielica, orzesznica, koszatka (*Glis*, *Muscardinus*, *Dryomys*) | hibernacja właściwa (7 mies.) | X – IV/V |
| chomik europejski, suseł | hibernacja | X – III/IV |
| bóbr | aktywny pod lodem; magazyn gałęzi pod wodą, otwory w lodzie | XII – III |
| wiewiórka | aktywna; zapasy (orzechy, żołędzie, szyszki, grzyby suszone) | cały rok; w mrozy dni w gnieździe |
| gronostaj *Mustela erminea* | futro białe (czarny koniec ogona) | XI – III (linienie X–XI i III–IV) |
| łasica *Mustela nivalis* | w Polsce zwykle NIE bieleje (tylko część populacji na NE) | – |
| zając szarak | nie bieleje; „marcowanie” | gody I–III (szczyt III), mioty III–IX |
| lis | futro zimowe gęste od X/XI, wyleniałe do V | – |
| jeleń, sarna, dzik | stada zimowe (chmary, rudle, watahy), paśniki, lizawki; spałowanie kory | XI – III |
| żubr | stada zimowe (do ok. 100 osobn.), dokarmianie sianem, lizawki | XII – III |

### 10.4. Ptaki — przyloty, odloty, zimowanie, lęgi [WO poza bocianem [ZW]]

| Gatunek | Przylot | Lęgi | Odlot | Uwagi |
|---|---|---|---|---|
| bocian biały *Ciconia ciconia* | III / początek IV [ZW] (tradycyjnie ok. 25 III [WO]) | IV–VII (ok. 33 dni wysiadywania, młode wylatują VII) | średnio 27 VIII [ZW], pierwsze pod koniec VII, młode VIII/IX | gniazda na słupach, dachach; żeruje na łąkach, w Polsce ok. 20 % populacji światowej [WO] |
| bocian czarny *Ciconia nigra* | koniec III / IV | IV–VII (lasy, stare drzewa) | VIII / IX | leśny, płochliwy |
| żuraw *Grus grus* | koniec II – III (szczyt 2–3 dek. III) | III–VI (bagna, olsy) | X – początek XI (zloty IX–X) | klangor, tańce godowe III–IV |
| skowronek *Alauda arvensis* | koniec II – III | IV–VII | X | pierwszy zwiastun wiosny |
| szpak *Sturnus vulgaris* | II / III | IV–VI | X / XI (część zimuje) | – |
| czajka *Vanellus vanellus* | III | IV–VI | X | – |
| jaskółka dymówka *Hirundo rustica* | IV (1–2 dek.) | V–VIII (2 lęgi) | IX (1–2 dek.) | – |
| oknówka *Delichon urbicum* | koniec IV | V–VIII | IX | – |
| jerzyk *Apus apus* | 1 dek. V | V–VII | koniec VII / początek VIII | – |
| kukułka *Cuculus canorus* | koniec IV | V–VI (pasożyt lęgowy) | VIII | milknie pod koniec VI |
| wilga *Oriolus oriolus* | V | V–VI | VIII | – |
| słowik szary *Luscinia luscinia* | koniec IV / V | V–VI | VIII / IX | śpiew V–VI |
| gęsi (zbożowa, białoczelna) | przelot II/III i IX–XI; część zimuje na W | – | – | – |
| gil *Pyrrhula pyrrhula* | lęgowy w górach i N (nieliczny); nalot z N X/XI–III | V–VII | – | zimą przy jarzębinie, w miastach |
| jemiołuszka *Bombycilla garrulus* | nalot z N: X/XI – III/IV (inwazyjnie, co kilka lat masowo) | (nie lęgnie się w Polsce) | III/IV | jarzębina, jemioła, głóg |
| czeczotka, jer, myszołów włochaty, srokosz (część), łabędź krzykliwy | zimujące z N: X/XI – III | – | – | – |
| głuszec *Tetrao urogallus* | osiadły | toki III/IV–V (szczyt IV, o świcie), lęgi V–VI | – | Puszcza Augustowska, Bory Dolnośląskie, Karpaty; zimą igliwie sosny |
| cietrzew *Lyrurus tetrix* | osiadły | toki III–V (szczyt IV, o świcie, tokowiska otwarte), lęgi V–VI | – | torfowiska, Podlasie, Karpaty |
| jarząbek *Tetrastes bonasia* | osiadły | toki IV, lęgi V | – | lasy górskie i NE |
| puchacz, puszczyk, sóweczka, włochatka | osiadłe | puchacz I–III (huczy I–II), puszczyk II–IV | – | – |
| bielik *Haliaeetus albicilla* | osiadły | jaja II/III, młode V–VII | – | – |
| kruk *Corvus corax* | osiadły | toki I–II, jaja II–III | – | – |
| krzyżodziób świerkowy *Loxia curvirostra* | koczujący | lęgi I–IV (zimowe, przy urodzaju szyszek) | – | – |
| dzięcioły, sikory, kowalik, pełzacz, sójka, mysikrólik | osiadłe | IV–VI | – | zimą stada mieszane, karmniki |

Okres lęgowy większości ptaków w Polsce: od ok. 1 III do 15 X (definicja z rozporządzenia o ochronie gatunkowej — okres ochronny), a biologicznie szczyt IV–VI [WO].

## 11. Serene Seasons — mechanika moda (długość roku, sub-sezony, śnieg/lód, kolory, uprawy, config)

### 11.1. Podstawy (GitHub wiki: Home, Seasons, Winter)

- Repozytorium: https://github.com/Glitchfiend/SereneSeasons — domyślna gałąź (stan 2026-09-21): `26.1.2` (numeracja wersji Minecrafta rocznikowa: 26.1.x). Struktura multi-loader: katalogi `common/`, `fabric/`, `forge/`, `neoforge/` → wersja Fabric jest oficjalnie wspierana.
- Biomy umiarkowane: 4 pory (Spring, Summer, Autumn, Winter), każda z 3 sub-sezonami (Early/Mid/Late) → 12 sub-sezonów.
- Domyślna długość: 8 dni gry na sub-sezon → 24 dni na porę → 96 dni gry na rok (konfigurowalne).
- Biomy tropikalne (pustynie, dżungle, sawanny) zamiast tego mają Wet Season / Dry Season; w nich "in season" są tylko uprawy letnie.
- Efekty: zmiana temperatury biomu, kolorów trawy i liści, częstości opadów i burz, płodności upraw (Crop Fertility); przedmioty: Calendar (kalendarz), Season Sensor (blok czujnika pory roku).
- Modrinth (https://modrinth.com/mod/serene-seasons, stan 2026-09-21): ok. 7 mln pobrań, licencja ARR (All Rights Reserved — UWAGA: nie wolno kopiować kodu do własnego moda, integracja tylko przez API/zależność), loadery Fabric / Forge / NeoForge, środowisko klient+serwer, wersje MC od 1.7.10 do 1.21.11 (+ gałąź 26.1.x na GitHubie), ostatnia aktualizacja "12 godzin temu" — mod jest aktywnie rozwijany.
- Zima (wiki "Winter"): kolory trawy/liści blakną, temperatura biomów spada, co pozwala na opady śniegu w biomach umiarkowanych; opady częstsze, ale burze nie występują.

### 11.2. Mechanika temperatury i śniegu/lodu — z kodu (gałąź 26.1.2, `common/src/main/java/sereneseasons/season/SeasonHooks.java`)

- Metoda `getBiomeTemperatureInSeason(Season.SubSeason subSeason, Holder<Biome> biome, BlockPos pos, int seaLevel)`.
- Temperatura jest modyfikowana **tylko** dla biomów: nie-tropikalnych (tag `ModTags.Biomes.TROPICAL_BIOMES`), nie na czarnej liście (`ModTags.Biomes.BLACKLISTED_BIOMES`), o bazowej temperaturze biomu ≤ 0,8 (w skali Minecrafta), tylko w wymiarach z whitelisty (`ModConfig.seasons.isDimensionWhitelisted(level.dimension())`).
- Wzór: `biomeTemp = Mth.clamp(biomeTemp + ModConfig.seasons.getSeasonProperties(subSeason).biomeTempAdjustment(), -0.5F, 2.0F)` — czyli **korekta temperatury per sub-sezon jest wartością konfigurowalną** (`biomeTempAdjustment`), a nie stałą zaszytą w kodzie jak w starszych wersjach.
- Progi: hooki `coldEnoughToSnowSeasonal(...)` / `warmEnoughToRainSeasonal(...)` używają vanillowego progu **0,15** — jeśli zmodyfikowana temperatura < 0,15 → śnieg zamiast deszczu, woda zamarza; ≥ 0,15 → deszcz. Minecraft dodatkowo obniża temperaturę z wysokością (parametr `seaLevel` w hookach — patrz vanilla `Biome.getTemperature(pos, seaLevel)`).
- Biomy tropikalne: sezon z `SeasonHelper.getSeasonState(level).getTropicalSeason()` (EARLY/MID/LATE_DRY, EARLY/MID/LATE_WET).

### 11.3. Topnienie śniegu i lodu (`season/RandomUpdateHandler.java`)

- Działa tylko gdy `ModConfig.seasons.generateSnowAndIce` = true i wymiar na whiteliście; wykonywane w fazie END ticku serwera, dla chunków tickujących w pobliżu graczy (`forEachBlockTickingChunk`).
- Parametry per sub-sezon z configu: `meltChance()` (procent → `meltRand = meltChance/100`), `meltRolls()` (liczba losowań na chunk na tick).
- Warunek topnienia: `SeasonHooks.getBiomeTemperature(world, biome, pos, seaLevel) >= 0.15F` (sprawdzane dla pozycji nad gruntem i na gruncie) oraz biom nie na czarnej liście. Efekt: `Blocks.SNOW` (warstwa śniegu) → powietrze; `Blocks.ICE` → `IceBlock.melt(...)` (woda).
- Wniosek: śnieg zalega tak długo, jak korekta sub-sezonu trzyma temperaturę biomu poniżej 0,15; po przekroczeniu progu topnieje stopniowo (losowo), a nie natychmiast — tempo zależy od `meltChance`/`meltRolls`.

### 11.4. Kolory trawy i liści (`api/season/Season.java`, `util/SeasonColorUtil.java`)

- `Season` = SPRING, SUMMER, AUTUMN, WINTER. `SubSeason` (12 wpisów, implementuje `ISeasonColorProvider`, `StringRepresentable`): EARLY/MID/LATE_SPRING, EARLY/MID/LATE_SUMMER, EARLY/MID/LATE_AUTUMN, EARLY/MID/LATE_WINTER. Każdy wpis ma: kolor nakładki trawy (grass overlay, hex np. `0x778087`), mnożnik nasycenia trawy, kolor nakładki liści (foliage overlay), mnożnik nasycenia liści, kolor brzozy (birch color) — wartości nasycenia w zakresie 0,45–0,85. `TropicalSeason`: EARLY/MID/LATE_DRY, EARLY/MID/LATE_WET z analogicznymi polami.
- `SeasonColorUtil.mixColours(a, b, ratio)` — liniowa interpolacja kanałów ARGB; `saturateColour(colour, mult)` — RGB→HSV, S·mult, →RGB.
- `applySeasonalGrassColouring` / `applySeasonalFoliageColouring`: (1) biom na czarnej liście lub wymiar poza whitelistą → kolor oryginalny; (2) pobierz overlay i mnożnik nasycenia z providera (sub-sezonu); (3) jeśli zmiany sezonowe wyłączone → wartości MID_SUMMER; (4) overlay ≠ 0xFFFFFF → zmieszaj z kolorem biomu; (5) biom z tagiem `LESSER_COLOR_CHANGE_BIOMES` → wynik zmieszany w 75 % z oryginałem; (6) mnożnik nasycenia ≠ −1 → zastosuj nasycenie.
- Dokładne wartości hex per sub-sezon — NIE UDAŁO SIĘ odczytać w całości (streszczenie źródła podało tylko przykład `0x778087`).

### 11.5. Konfiguracja (`common/src/main/java/sereneseasons/config/SeasonsConfig.java`, gałąź 26.1.2) [ZW]

| Pole | Klucz w pliku config | Domyślnie | Opis (z komentarza w kodzie) |
|---|---|---|---|
| `subSeasonDuration` | `time_settings.sub_season_duration` | `8` | długość sub-sezonu w dniach (gry) |
| `startingSubSeason` | `time_settings.starting_sub_season` | `1` | początkowy sub-sezon nowego świata: 0 = losowy, 1–3 = Early/Mid/Late Spring itd. (do 12) |
| `progressSeasonWhileOffline` | `time_settings.progress_season_while_offline` | `true` | czy pory roku biegną na serwerze bez graczy |
| `generateSnowAndIce` | `weather_settings.generate_snow_ice` | `true` | generuj śnieg i lód w zimie |
| `changeWeatherFrequency` | `weather_settings.change_weather_frequency` | `true` | zmieniaj częstość deszczu/śniegu/burz zależnie od pory |
| `whitelistedDimensions` | `dimension_settings.whitelisted_dimensions` | `["minecraft:overworld"]` | wymiary, w których działają pory roku (`isDimensionWhitelisted`) |

Właściwości per sub-sezon (`getSeasonProperties(subSeason)`), format: (meltChance [%], meltRolls, biomeTempAdjustment, minRainTime, maxRainTime, minThunderTime, maxThunderTime) — czasy w tickach (1 dzień = 24 000 ticków):

| Sub-sezon | meltChance | meltRolls | biomeTempAdjustment | minRainTime | maxRainTime | minThunderTime | maxThunderTime |
|---|---|---|---|---|---|---|---|
| Early Winter | 0,0 | 0 | **−0,8** | 12 000 | 36 000 | −1 | −1 (brak burz) |
| Mid Winter | 0,0 | 0 | **−0,8** | 12 000 | 36 000 | −1 | −1 |
| Late Winter | 0,0 | 0 | **−0,8** | 12 000 | 36 000 | −1 | −1 |
| Early Spring | 6,25 | 1 | **−0,25** | 12 000 | 96 000 | 12 000 | 180 000 |
| Mid Spring | 8,33 | 1 | 0,0 | 12 000 | 96 000 | 12 000 | 180 000 |
| Late Spring | 12,5 | 1 | 0,0 | 12 000 | 96 000 | 12 000 | 180 000 |
| Early Summer | 25,0 | 1 | 0,0 | 12 000 | 96 000 | 12 000 | 180 000 |
| Mid Summer | 25,0 | 1 | 0,0 | 12 000 | 96 000 | 12 000 | 180 000 |
| Late Summer | 25,0 | 1 | 0,0 | 12 000 | 96 000 | 12 000 | 180 000 |
| Early Autumn | 12,5 | 1 | 0,0 | 12 000 | 192 000 | 12 000 | 180 000 |
| Mid Autumn | 8,33 | 1 | 0,0 | 12 000 | 192 000 | 12 000 | 180 000 |
| Late Autumn | 6,25 | 1 | **−0,25** | 12 000 | 192 000 | 12 000 | 180 000 |

Interpretacja [SZAC]:
- Vanilla: przerwa między opadami 12 000–180 000 ticków (0,5–7,5 dnia). SS: zima 0,5–1,5 dnia (opady bardzo częste, wyłącznie śnieg w biomach ≤ 0,8), wiosna/lato 0,5–4 dni (częściej niż vanilla), jesień 0,5–8 dni (jak vanilla lub rzadziej). Burze: nigdy zimą; poza zimą jak vanilla.
- Śnieg: biom o temperaturze bazowej T dostaje śnieg, gdy T + adj < 0,15. Zimą (adj −0,8): każdy biom z T ≤ 0,8 (wszystkie lasy umiarkowane: forest 0,7, birch 0,6, dark forest 0,7, plains 0,8, taiga 0,25, swamp 0,8) → śnieg i lód. Early Spring / Late Autumn (adj −0,25): śnieg tylko gdy T < 0,40 (tajga 0,25, old growth taiga 0,3, windswept 0,2, grove −0,2, snowy slopes −0,3). Pozostałe 6 sub-sezonów: vanilla (śnieg tylko w biomach T < 0,15).
- Topnienie: w zimie meltRolls = 0 → śnieg NIE topnieje w ogóle (nawet w ciepłych biomach). Od Early Spring 1 losowanie na chunk na tick z szansą 6,25 % → 8,33 % → 12,5 % → 25 % (lato) → 12,5 % → 8,33 % → 6,25 % (Late Autumn). Przy 20 ticków/s jeden chunk dostaje ok. 1,25 prób topnienia/s wczesną wiosną i 5 prób/s latem — czyli śnieg z Late Winter znika w Early Spring w ciągu kilku dni gry (stopniowo, „plamami”).
- Kolejność sub-sezonów w configu (1–12): Early/Mid/Late Spring, Early/Mid/Late Summer, Early/Mid/Late Autumn, Early/Mid/Late Winter.
- Crop Fertility (wiki „Crop-Fertility”): uprawy przypisane do sezonów (spring/summer/autumn/winter crops) przez tagi; poza sezonem rosną wolniej lub nie rosną (zależnie od configu); w biomach tropikalnych tylko uprawy letnie; pod dachem szklanym/pod ziemią ograniczenia nie działają. Szczegóły (`FertilityConfig`, wartości) — NIE ZBADANO (strona wiki nie została pobrana z braku budżetu).
- API: pakiet `sereneseasons.api.season` (`SeasonHelper.getSeasonState(level)` → `getSeason()`, `getSubSeason()`, `getTropicalSeason()`, `getDay()`, `getSeasonCycleTicks()`), `ISeasonState`, `ISeasonColorProvider`; tagi biomów w `ModTags.Biomes`: `BLACKLISTED_BIOMES`, `TROPICAL_BIOMES`, `LESSER_COLOR_CHANGE_BIOMES` (nazwy w datapacku: `sereneseasons:blacklisted_biomes`, `sereneseasons:tropical_biomes`, `sereneseasons:lesser_color_change_biomes` — nazwy plików do potwierdzenia). Klasa `SeasonHooks` w `sereneseasons.season` (nie w API) — hookowanie przez mixin jest możliwe, ale kruche.
- Zależność: SS (wersje 1.20+) wymaga biblioteki GlitchCore (ten sam autor) — [WO], do potwierdzenia na Modrinth.

## 12. Propozycja mapowania: polski kalendarz klimatyczny → 12 sub-sezonów SS

### 12.1. Zasada

12 sub-sezonów = 12 miesięcy; naturalne i zgodne z termicznymi porami roku Romera–Mareckiego jest przypisanie **Early Spring = marzec** (przedwiośnie), tak by Mid Winter = styczeń (najzimniejszy) i Mid Summer = lipiec (najcieplejszy). Przy domyślnych 8 dniach na sub-sezon 1 dzień gry ≈ 3,8 doby rzeczywistej; jeśli mod ma odwzorować zdarzenia trwające 1–2 tygodnie (rykowisko 4 tyg. ≈ 7–8 dni gry; kwitnienie czeremchy ok. 10 dni ≈ 2–3 dni gry), rozważyć **subSeasonDuration = 10–15** (rok 120–180 dni) — do decyzji użytkownika.

### 12.2. Tabela mapowania (Polska centralna, niziny; różnice regionalne w sekcji 13)

| Sub-sezon SS | Miesiąc | Pora termiczna (Romer–Marecki) | Pora fenologiczna | Klimat (Warszawa: T śr., śnieg) | Las — rośliny | Zwierzęta |
|---|---|---|---|---|---|---|
| Early Spring | III | przedwiośnie (0–5 °C) | przedwiośnie → pierwiośnie | ok. 3 °C; śnieg topnieje (plamy), lód na jeziorach do ok. 10–20 III, roztopy, wysokie stany rzek | pyli leszczyna i olsza, przebiśnieg, wawrzynek, podbiał, przylaszczka; zawilce pod koniec; drzewa bezlistne; pierwsze pąki | przylot skowronków, szpaków, żurawi, czajek, bocianów (koniec); gody żab i ropuch (koniec III); marcowanie zajęcy; jeleń zrzuca poroże; niedźwiedź opuszcza gawrę; toki głuszca/cietrzewia zaczynają się; nietoperze i jeże się budzą |
| Mid Spring | IV | wiosna (5–10 °C) | pierwiośnie → pełnia wiosny | ok. 9 °C; przymrozki nocne częste; ostatni śnieg możliwy | zawilce (dywany), pierwiosnek, mniszek; listnienie brzozy (ok. 20 IV), modrzewia, czeremchy; kwitnienie czeremchy (koniec IV); trawa zielona; smardze | szczyt toków głuszca i cietrzewia; jaskółki, kukułka; lęgi; gody ropuch, kumaków; prosięta; narodziny wilków (koniec); sarny wycierają scypuł; chrabąszcze |
| Late Spring | V | wiosna/przedlecie (10–15 °C) | pełnia wiosny | ok. 14 °C; „zimni ogrodnicy” 12–15 V (ostatnie przymrozki) | listnienie dębu (ok. 5 V), buka, lipy; kwitnienie kasztanowca, jabłoni, konwalii, jarzębiny, bzu (koniec V), robinii; pełne ulistnienie ok. 20 V | koźlęta, cielęta jelenia, łoszaki; jerzyki, wilgi; szczyt lęgów; rzekotki, żaby zielone; kleszcze i komary |
| Early Summer | VI | przedlecie/lato (15+ °C) | wczesne lato | ok. 18 °C; burze; najdłuższy dzień | kwitnienie bzu czarnego, lipy (koniec VI); poziomki dojrzewają; runo pełne; trawy kwitną (łąki) | podloty ptaków; świetliki; jelonek; sarny ukrywają koźlęta; bobry — młode; niedźwiedź — gody |
| Mid Summer | VII | lato (≥ 15 °C) | lato | ok. 20 °C; maksimum opadów (burze), upały > 30 °C | lipa kwitnie (do połowy VII); borówka czarna, maliny, czeremcha, porzeczki dojrzewają; pierwsze kurki, borowiki (po deszczach) | bekowisko saren (od 2 poł. VII); jeleń wyciera scypuł (koniec VII); młode żaby opuszczają wodę; bociany — młode wylatują; jerzyki odlatują (koniec) |
| Late Summer | VIII | lato | lato → wczesna jesień | ok. 19 °C; burze; „babie lato” pod koniec | maliny, jeżyny (koniec), brusznica; wrzos zaczyna kwitnąć (2–3 dek.); orzechy laskowe (koniec); szczyt kurek; pierwsze żółte liście lipy/brzozy w suszę | odlot bocianów (śr. 27 VIII); łosie wycierają scypuł; niedźwiedź — hiperfagia; nietoperze — rojenie; żmije rodzą młode; zapasy wiewiórek i sójek |
| Early Autumn | IX | polecie (10–15 °C) | wczesna jesień | ok. 14 °C; pierwsze przymrozki w NE; mgły poranne | wrzos kwitnie; jarzębina, dzika róża, głóg, jeżyny, orzechy laskowe dojrzewają; żołędzie zaczynają spadać (koniec); szczyt grzybów (borowiki, podgrzybki, rydze, maślaki); brzoza zaczyna żółknąć (koniec) | bukowisko łosi; rykowisko jeleni (od 2 poł.); odlot jaskółek, zloty żurawi; gęsi; niedźwiedź żeruje na bukwi; dziki na żołędziach; żaby wędrują do zimowisk |
| Mid Autumn | X | jesień (5–10 °C) | jesień (pełna) | ok. 9 °C; przymrozki; mgły; halny w Tatrach (X–XI) | pełnia barw: brzoza, klon, lipa (1–2 dek.), buk (2–3 dek.), dąb (koniec); opad liści od 2 dek.; modrzew żółknie (koniec); żołędzie i bukiew; opieńki, rydze, ostatnie borowiki; tarnina po przymrozku | koniec rykowiska; bekowisko danieli; sarny zrzucają poroże; odlot żurawi (koniec X); nalot gili, jemiołuszek, kwiczołów; jeż, nietoperze, płazy — hibernacja; gronostaj bieleje; niedźwiedź szuka gawry |
| Late Autumn | XI | przedzimie (0–5 °C) | późna jesień → zima | ok. 4 °C; pierwszy śnieg (przelotny), pierwsze zamarzanie małych stawów; wiatry; grudzień pochmurny | opad liści kończy się (dąb, modrzew do 2–3 dek.); stan bezlistny; ostatnie opieńki; owoce na krzewach (róża, głóg, kalina, jarzębina); sosna/świerk — dojrzałe szyszki | huczka dzików (od XI); łoś zrzuca poroże; niedźwiedź i borsuk — do gawry/nory; chmary/rudle zimowe; wilki polują watahą; ptaki przy karmnikach; jemiołuszki na jarzębinie; sieja/sielawa — tarło |
| Early Winter | XII | zima (≤ 0 °C) | zima | ok. 0 °C (Warszawa −0,1; NE −1,6; W +1,9); pokrywa śnieżna ustala się w NE (poł. XII), na W epizodyczna; jeziora zamarzają (śr. 28 XII) | bezlistne; śnieg na gałęziach; igły; zimowe pąki; jemioła owocuje | szczyt huczki; żubry przy paśnikach; jelenie spałują korę; bobry pod lodem; krzyżodzioby budują gniazda; kruki, bieliki przy padlinie; miętus — tarło |
| Mid Winter | I | zima | zima | ok. −1,5 °C (Suwałki −3,3; Szczecin +0,6); maks. pokrywy śnieżnej i lodu (śr. maks. 23 cm, do 50–66 cm) | spoczynek; śnieg; okiść na świerkach; szyszki sosny zamknięte | cieczka wilków (I–II); młode niedźwiedzie w gawrze (I/II); gody bobrów pod lodem; puchacz huczy; zimowe stada; tropy na śniegu; gronostaj biały; dokarmianie (paśniki, lizawki) |
| Late Winter | II | zima → przedwiośnie | zima → przedwiośnie (na W: przedwiośnie od połowy II) | ok. −0,4 °C; dzień wyraźnie dłuższy; odwilże na zachodzie; śnieg trwa w NE i górach | na W: leszczyna zaczyna pylić (Poznań 16 II 2024), przebiśniegi (koniec II); pąki nabrzmiewają; szyszki sosny zaczynają się otwierać w słońcu | szczyt cieczki wilków; jeleń — stare byki zrzucają poroże (koniec II); lisy — szczyt cieczki; pierwsze skowronki i gęsi (koniec II, na W); bóbr — gody; kruk — toki; puszczyk — lęgi |

### 12.3. Ustawienia SS zalecane dla Polski centralnej (baseline) [SZAC]

- `sub_season_duration`: 8 (domyślnie) lub 12 (rok 144 dni) dla bardziej „czytelnego” przebiegu zdarzeń przyrodniczych.
- `starting_sub_season`: 1 (Early Spring = marzec) — start świata na przedwiośniu; alternatywnie 4 (Early Summer) dla przyjaznego startu.
- `generate_snow_ice`: true; `change_weather_frequency`: true.
- `biomeTempAdjustment`: pozostawić domyślne −0,8 (XII–II) i −0,25 (III, XI). Dla „ciepłej” (zachodniej) Polski rozważyć Early Winter −0,6 i Late Winter −0,6 (śnieg tylko w biomach T ≤ 0,75), ale config jest globalny — patrz sekcja 13.
- `meltChance` w Early Spring: podnieść do ok. 10–12 %, jeśli sub-sezon jest dłuższy niż 8 dni (żeby roztopy nie trwały cały marzec).

## 13. Różnice regionalne (nadmorski / nizinny zachodni / nizinny wschodni / wyżynny / górski) — implementacja

### 13.1. Profil regionów (synteza sekcji 1–6) — wartości [ZW] tam, gdzie podano stację; reszta [WO]

| Region (stacja) | T rok | T I | T VII | Opad | Dni z pokrywą śnieżną | Okres wegetacyjny | Zima termiczna | Fenologia wiosny vs centrum |
|---|---|---|---|---|---|---|---|---|
| Nadmorski (Gdańsk, Łeba, Kołobrzeg) | ok. 8,5–9 | ok. −0,5…+0,5 | 17,9 (Łeba) | 550–800 mm | 40–60 | 200–210 (Gdańsk) | od ok. 20 XII, przerywana | wiosna −5…−10 dni (chłodne morze), jesień +1–2 tyg. dłuższa, lato późniejsze o kilka dni |
| Nizinny zachodni (Szczecin, Poznań, Wrocław, Zielona Góra) | 9,4–9,7 | −0,4…+0,6 | 19,3–20,1 | 500–560 mm | 30–45 | 220–255 | od poł. XII, często brak | wiosna +5…+10 dni wcześniej |
| Nizinny centralny (Warszawa, Łódź) | 9,0 | −1,5 | 19,6 | 520–560 mm | 50–60 | 200–215 | od ok. 10 XII | baza (0) |
| Nizinny wschodni / NE (Białystok, Suwałki, Lublin) | 7,2–8,2 | −3,3…−2,5 | 18,4–19,2 | 570–620 mm | 70–101 (Suwałki 101 [ZW]) | 195–212 | od końca XI do połowy III | wiosna −2…−3 tyg., jesień wcześniej o 1–2 tyg. |
| Wyżynny (Kielce, Kraków-Pogórze, Roztocze, 250–400 m) | 7,5–8,9 | −2…−1,6 | 18–19,5 | 600–800 mm | 60–90 (Harta ~70 [ZW]) | 200–215 | od początku XII | −1 tydz. |
| Górski — kotliny/regiel dolny (Zakopane 855 m, Jelenia Góra) | 6,2 (Zakopane) | −3,3 | 16,0 | 900–1200 mm | 120–140 | 180–200 | od ok. 20 XI do końca III | −3…−4 tyg. |
| Górski — regiel górny/kosodrzewina (1250–1800 m) | 1,4–3 | −5,9 (Śnieżka) | 9,9 (Śnieżka) | 1200–1500 mm | 180–200 | 126 (Śnieżka) | XI–IV | −6…−8 tyg.; brak lata termicznego |
| Górski — hale/turnie (Kasprowy 1991 m) | 0,1 | −7,4 | 8,9 | 1500–1800 mm | > 200 (do 230) | 109 | X–V | śnieg możliwy w każdym miesiącu; brak drzew |

### 13.2. Jak to zaimplementować w Minecrafcie z Serene Seasons [SZAC — propozycje]

1. **Config SS jest globalny per sub-sezon** (jeden `biomeTempAdjustment` dla całego świata) — regionalizację można uzyskać wyłącznie przez **bazową temperaturę biomu** (`temperature` w JSON biomu) oraz **wysokość Y**. SS modyfikuje tylko biomy z T ≤ 0,8, a śnieg pojawia się przy T + adj < 0,15.
2. Propozycja bazowych temperatur biomów (warianty regionalne tego samego typu lasu, np. `las_mieszany_zachodni`, `las_mieszany_wschodni`):

| Wariant regionalny biomu | T bazowa | Śnieg w sub-sezonach (adj: XII–II −0,8; III, XI −0,25) | Odpowiada |
|---|---|---|---|
| nadmorski / zachodni | 0,75–0,80 | tylko Early–Late Winter (XII–II) → 3 sub-sezony | ok. 40–60 dni śniegu realnie (w grze: 24 dni z 96, tj. 25 % roku — więcej niż realnie; kompromis) |
| centralny | 0,65–0,70 | XII–II | jw. |
| wschodni / NE | 0,35–0,40 | XI–III (5 sub-sezonów, bo 0,40 − 0,25 = 0,15) | ok. 100 dni śniegu; NE dostaje śnieg wcześniej i traci później — zgodnie z rzeczywistością |
| wyżynny | 0,50–0,60 | XII–II (+ epizodycznie) | – |
| górski — regiel dolny | 0,30–0,40 | XI–III | Zakopane 120–140 dni |
| górski — regiel górny | 0,15–0,25 | XI–III (+ deszcz/śnieg na granicy) | Śnieżka ok. 190 dni |
| górski — kosodrzewina / hale | 0,0–0,10 | cały rok śnieg wg SS (T < 0,15 zawsze) — ZA DUŻO; realnie lato bez śniegu | wymaga własnej korekty: latem podnieść do 0,2 (własny hook) lub akceptować „wieczny śnieg” tylko powyżej turni |
| turnie > 2200 m | −0,3…−0,5 | cały rok | Tatry Wysokie — realnie płaty śniegu do VII–VIII, ale nie ciągła pokrywa |

   Uwaga: klamra w SS to [−0,5; 2,0], a próg śniegu 0,15 — pomiędzy 0,15 a 0,40 bazowej temperatury leży „strefa NE i regla dolnego”, a poniżej 0,15 — „strefa wiecznego śniegu” w vanilla; ponieważ SS nie podnosi temperatury latem (adj = 0), nie da się przez sam config uzyskać biomu, który ma śnieg XI–IV, a lato bez śniegu, inaczej niż przez T = 0,15–0,40. To wystarcza dla regla górnego (śnieg XI–III), ale nie dla hal (śnieg X–V). Rozwiązanie: własny mixin/hook w naszym modzie dodający **dodatni** offset latem dla biomów z tagiem `lasy_pl:alpejskie` (np. +0,2 w V–IX) albo — prościej — własna warstwa „śniegu wysokogórskiego” niezależna od SS.
3. **Wysokość**: vanilla obniża temperaturę z wysokością (od Y ≈ 80 wzwyż, ok. 0,00125/blok w 1.18–1.21 [WO — do sprawdzenia w kodzie `Biome.getHeightAdjustedTemperature`]). Przy skali 1 blok = 1 m i Tatrach o realnej wysokości (2500 m) świat wymaga rozszerzenia wysokości (vanilla 384 bloków: Y −64…320). Dwie opcje: (a) skala pionowa 1:4–1:6 (Rysy ≈ Y 320) z biomami pięter zdefiniowanymi po Y; (b) modyfikacja `dimension_type.height` do 2048+ (kosztowna wydajnościowo) — decyzja użytkownika (pkt 1 wymagań mówi o „rzeczywistych wymiarach”). Gradient w grze warto ustawić tak, by 0,54 °C/100 m realnych = spadek T biomu o ok. 0,04/100 m realnych (skala: 1 °C ≈ 0,07 jednostki T — [SZAC] przy założeniu T = 0,8 ↔ 9 °C, T = 0,15 ↔ 0 °C).
4. **Kolory liści/trawy per region**: SS nakłada globalny overlay per sub-sezon; różnice regionalne (NE żółknie 1–2 tyg. wcześniej) można uzyskać tagiem `sereneseasons:lesser_color_change_biomes` dla biomów zachodnich/nadmorskich (75 % efektu) — grubo, ale bez pisania kodu. Precyzyjnie: własny `ISeasonColorProvider` per biom (SS API) z przesunięciem faz o ±1 sub-sezon.
5. **Fenologia zdarzeń (kwitnienie, owoce, gody, przyloty)**: nasz mod powinien liczyć własny „dzień fenologiczny” = dzień roku SS (`getDay()` w cyklu 0…12·subSeasonDuration) + offset regionalny biomu (np. nadmorski −2 dni, zachodni −2, centralny 0, wschodni +5, wyżynny +2, regiel dolny +7, regiel górny +14 dni gry przy 8-dniowych sub-sezonach; proporcjonalnie przy dłuższych) — i na tej podstawie przełączać stany bloków (kwiat/liść/owoc) oraz zachowania mobów. Dzięki temu jeden config SS obsługuje wszystkie regiony.
6. **Lód na jeziorach**: SS zamraża wodę tam, gdzie T + adj < 0,15 (w zimie wszędzie ≤ 0,8) — czyli równocześnie ze śniegiem. Realnie lód pełny pojawia się ok. 2–4 tygodnie po pierwszym śniegu; rozważyć własną regułę: lód dopiero od Mid Winter (styczeń) na zachodzie, od Early Winter na NE; rzeki (woda płynąca) — nie zamarzają.
7. **Sezonowość pogody**: burze V–VIII (SS: brak zimą — OK), mgły IX–XI (własny efekt: gęsta mgła o poranku w Mid/Late Autumn, w dolinach), halny w Tatrach X–XI i II–III (własny event: silny ciepły wiatr + topnienie + wiatrołomy).

## 14. Implikacje projektowe dla moda

1. **Mapowanie czasu**: Early Spring = marzec … Late Winter = luty. Zapisać w kodzie jedną tablicę `SubSeason → miesiąc` i wszystkie zdarzenia fenologiczne wyrażać w „dniach roku SS”, a nie w sub-sezonach, by działały przy dowolnym `sub_season_duration`.
2. **Długość roku**: domyślne 96 dni gry jest za krótkie na wierne odwzorowanie zdarzeń trwających 1–4 tygodnie; zaproponować użytkownikowi 12–15 dni/sub-sezon (rok 144–180 dni) jako ustawienie zalecane, z zachowaniem kompatybilności z 8.
3. **Śnieg i lód**: polegać na mechanice SS (próg 0,15, adj −0,8 zimą, −0,25 III/XI, meltChance 6–25 %), a regionalizację uzyskać przez bazową temperaturę wariantów biomów: 0,75–0,8 (W/nadmorski), 0,65–0,7 (centrum), 0,35–0,4 (NE, regiel dolny), 0,15–0,25 (regiel górny), < 0,15 (hale/turnie — wymaga własnej korekty letniej).
4. **Nie kopiować kodu SS** (licencja ARR) — integrować przez `sereneseasons.api` (SeasonHelper, ISeasonState, ISeasonColorProvider, tagi biomów) i zależność `serene-seasons` + `glitchcore` w `fabric.mod.json` (wersje do potwierdzenia); przewidzieć tryb bez SS (fallback: własny prosty licznik pór roku lub stałe „lato”).
5. **Kolory**: użyć SS do trawy/liści (overlay + saturacja), ale dla naszych nowych gatunków drzew zaimplementować własne `BlockColor` providery z krzywymi barw per gatunek (brzoza/lipa żółte 1–2 dek. X; klon żółto-pomarańczowy 2–3 dek. X; buk złoto-miedziany 2–3 dek. X; dąb brązowy koniec X–XI; modrzew złoty koniec X–XI; olsza opada zielona; świerk/sosna/jodła stałe) oraz stan bezlistny (osobny model/tekstura liści „nagie gałęzie”) od ok. połowy XI do połowy IV.
6. **Bloki fenologiczne**: liście z właściwością `phase` (bud/leaf/flower/fruit/autumn/bare); kwitnienie czeremchy (koniec IV), jarzębiny (V), lipy (koniec VI/VII), wrzosu (VIII/IX); owoce: poziomka VI–VII, borówka VII–VIII, malina VII–VIII, jeżyna VIII–IX, jarzębina IX–zima, orzech laskowy IX, żołędzie/bukiew X, róża/głóg/tarnina X–zima (tarnina „smaczna” po przymrozku).
7. **Grzyby**: spawn zależny od (a) dnia roku SS (smardze IV–V; kurki VI–XI; borowiki VII–X; rydze VIII–XI; opieńki IX–XI), (b) wilgotności — liczby dni deszczowych w ostatnich N dniach gry, (c) temperatury (brak po przymrozku < 0 °C, z wyjątkiem opieniek/rydzów), (d) typu lasu (borowik — bory sosnowe/świerkowe, rydz — młodniki sosnowe, kurki — bory, opieńki — pniaki/martwe drewno, smardze — łęgi/olsy). Wrzesień = maksimum różnorodności.
8. **Runo sezonowe**: geofity wiosenne (przebiśnieg II/III, zawilec IV, przylaszczka III/IV, konwalia V) pojawiają się i znikają (VI–VII), trawa i paprocie rosną V–IX, jesienią brązowieją; zimą pod śniegiem.
9. **Zwierzęta — cykl roczny sterowany dniem roku SS + offset regionalny**: rykowisko jeleni (2 poł. IX – 2 dek. X: ryk, walki byków, harem), bukowisko łosi (IX), bekowisko danieli (X), ruja saren (2 poł. VII–VIII), huczka dzików (XI–I), cieczka wilków (I–II) i szczenięta (koniec IV–V), narodziny cieląt/koźląt (V–VI), prosięta (III–V), niedźwiedź w gawrze (XI/XII–III/IV; młode I/II), borsuk (XI/XII–II/III), jeż (X–IV), nietoperze (X/XI–III/IV), bóbr pod lodem (XII–III, magazyn gałęzi), zapasy wiewiórki/sójki (IX–X) i wykopywanie (II–IV), marcowanie zajęcy (III).
10. **Poroże jako stan modelu**: jeleń — brak II/III–IV, scypuł IV–VII, pełne VIII–II; łoś — brak XI/I–III, scypuł IV–VIII, pełne IX–XII; sarna — brak X/XII, scypuł XI–III, pełne IV–X; daniel — brak IV–V, scypuł V–VIII, pełne IX–IV. Wymaga 3 wariantów modelu/tekstury per gatunek.
11. **Sezonowe futro**: gronostaj biały XI–III; lis gęste futro X/XI–IV; jeleń/sarna szata zimowa szarobrązowa (X–IV) vs letnia rudobrązowa (V–IX) — 2 tekstury.
12. **Stada zimowe**: jelenie w chmary (XI–III), sarny w rudle, dziki w watahy (cały rok, ale zimą większe), żubry do 100 osobników przy paśnikach (XII–III). Paśniki i lizawki jako bloki przyciągające (XI–III), z dokarmianiem przez gracza (siano, buraki, kasztany).
13. **Ptaki**: rozdzielić gatunki na (a) osiadłe (sikory, dzięcioły, sójki, kruk, bielik, puchacz, głuszec, cietrzew), (b) letnie migranty (bocian III/IV–koniec VIII, żuraw III–X/XI, jaskółki IV–IX, jerzyk V–VII, kukułka IV–VIII, skowronek II/III–X), (c) zimowe goście (jemiołuszka, gil północny, czeczotka, jer X/XI–III/IV). Spawn/despawn sterowany dniem roku SS; toki głuszca/cietrzewia (III/IV–V, o świcie) jako event dźwiękowo-animacyjny.
14. **Płazy**: migracje żab i ropuch do wód (koniec III–IV, nocą, w deszcz, > 5 °C), skrzek, kijanki (IV–VI), młode (VII); hibernacja X–III (w mule/ściółce) — moby znikają lub śpią w bloku.
15. **Lód**: jeziora zamarzają ok. 2–4 tyg. po pierwszym śniegu (średnia 28 XII), utrzymują lód ok. 65 dni (do połowy/końca III), grubość śr. maks. 23 cm — lód „cienki” (bez bezpiecznego chodzenia) w XII i III, „gruby” I–II; rzeki nie zamarzają (poza wariantem mroźnej zimy). Zmienność międzyroczna: losować „typ zimy” (łagodna/normalna/mroźna) na sezon.
16. **Pogoda**: burze tylko V–IX (SS blokuje zimą; my dodatkowo obniżyć w III–IV i X); mgły poranne IX–XI w dolinach i nad wodami; halny w Tatrach X–XI, II–III (ciepły wiatr, topnienie, wiatrołomy świerków); bryza nad morzem; wiatr zachodni jako domyślny kierunek (ważne dla nachylenia drzew/wydm — opcjonalnie).
17. **Piętra górskie**: regiel dolny (do ~1250 m: buk, jodła, świerk), regiel górny (1250–1550 m: świerk), kosodrzewina (1550–1800 m), hale (1800–2300 m), turnie (> 2300 m) — w Karkonoszach granice ok. 300 m niżej (górna granica lasu ok. 1250 m). Fenologia opóźniona ok. 3–4 dni/100 m; brak lata termicznego > 1000–1200 m.
18. **Zmienność klimatyczna**: dodać globalny losowy offset roku (±0,05 T, tj. ok. ±0,7 °C) i zmienność długości pokrywy śnieżnej ±30 %, by zimy różniły się między sobą jak w rzeczywistości (Harta: 20 vs 130 dni).
19. **Zdarzenia rzadkie**: lata nasienne dębu (co 3–7 lat) i buka (co 5–8 lat) — masowy opad żołędzi/bukwi → więcej dzików i myszy w następnym roku; inwazje jemiołuszek co kilka lat; gradacje kornika po suchych latach (świerk) — jako opcjonalne „wydarzenia roku”.
20. **Weryfikacja danych**: przed finalizacją dokumentacji potwierdzić na portalu IMGW pełne normy miesięczne i wskaźniki śniegowe dla 12 stacji (sekcja 2.2 zawiera wartości orientacyjne), oraz sprawdzić w kodzie SS aktualne wartości kolorów (`Season.java`) i `FertilityConfig`.

## 15. Niepewności / do potwierdzenia

Luki badawcze (budżet 30 wywołań wyczerpany):
1. NIE ZBADANO: pełne tabele miesięczne norm IMGW 1991–2020 (temperatura i opad) dla 12 stacji — potwierdzone tylko wartości roczne, styczniowe i grudniowe (oraz lipiec dla Kasprowego i Śnieżki). Tabela w sekcji 2.2 jest orientacyjna [WO]. Portal IMGW: https://klimat.imgw.pl/pl/climate-normals/ (tabela interaktywna, wymaga ręcznego odczytu lub pobrania CSV).
2. NIE ZBADANO: liczba dni z pokrywą śnieżną, średnia/maksymalna grubość, daty pierwszego i ostatniego śniegu per stacja (potwierdzone tylko Suwałki 101 dni i Harta ok. 70 dni). Mapy IMGW: https://imgw.isok.gov.pl/mapy-klimatologiczne/ (obrazy).
3. NIE ZBADANO liczbowo: dni z mgłą, dni z burzą (mapy IMGW są obrazami), prędkości halnego, okres bezprzymrozkowy per stacja.
4. NIE ZBADANO: zamarzanie rzek (Wisła, Odra, potoki górskie).
5. NIE ZBADANO: dokładne średnie daty początku termicznych pór roku per stacja (tylko opis jakościowy z zpe.gov.pl; prace: Szyga-Pluta, repozytorium AMU — „Zmienność termicznych pór roku w Poznaniu”; Tylkowski, UJK).
6. NIE ZBADANO w źródle pierwotnym: kalendarz fenologiczny roślin z datami per gatunek (IMGW prowadzi obserwacje fenologiczne; atlas-roslin.pl ma stronę „fenologia”; UAM podaje 8 pór i wskaźniki, ale bez pełnej tabeli dat). Daty w sekcji 7.3 i 9 są [WO].
7. NIE ZBADANO w źródle pierwotnym: większość kalendarza zwierząt (potwierdzone: rykowisko 2 poł. IX ~4 tyg., bukowisko IX, bocian przylot III/IV, odlot śr. 27 VIII, nazewnictwo PZŁ). Reszta sekcji 10 to [WO].
8. NIE ZBADANO: `FertilityConfig` SS (mechanika płodności upraw), dokładne wartości hex kolorów per sub-sezon w `Season.java` (tylko przykład 0x778087 i zakres nasycenia 0,45–0,85), nazwy plików tagów biomów w datapacku, zależność od GlitchCore i minimalne wersje dla MC 26.1.x, mechanika `changeWeatherFrequency` w kodzie (poza wartościami configu).
9. Rekord temperatury 40,5 °C (Słubice, 28 VI 2026) — podany przez Wikipedię pl/en, niezweryfikowany w IMGW (klasyczny rekord: 40,2 °C Prószków 1921).
10. Wartości vanilla dot. spadku temperatury z wysokością i parametru `seaLevel` w hookach SS 26.1 — do sprawdzenia w kodzie Minecrafta/SS.

Decyzje, o które trzeba zapytać użytkownika:
- Długość sub-sezonu SS: domyślne 8 dni (rok 96 dni) czy 12–15 dni (rok 144–180 dni) — kompromis między tempem gry a wiernością zdarzeń trwających tygodnie.
- Skala pionowa świata: realne wysokości (Rysy 2499 m → wymaga wysokości świata > 2500 bloków) czy skala 1:4–1:6 (Rysy ≈ Y 320) — pkt 1 wymagań mówi o „rzeczywistych wymiarach”, ale to skrajnie kosztowne.
- Czy regionalizacja ma być przez warianty biomów (np. „las mieszany suwalski” vs „lubuski”) czy przez ciągłą mapę „kontynentalizmu” (własny noise) z jednym typem biomu — druga opcja jest wierniejsza, ale wymaga własnego hooka temperatury poza SS.
- Czy dopuszczamy własny mixin do `SeasonHooks` (kruchy przy aktualizacjach SS) czy trzymamy się wyłącznie API i tagów.
- Ile z „ciepłej Polski zachodniej” (40 dni śniegu) odwzorowujemy: przy domyślnym SS każdy biom umiarkowany ma śnieg przez 3 sub-sezony (25 % roku) — akceptować czy budować własną korektę (np. Late Winter bez śniegu na zachodzie)?
- Czy zimy mają być losowo zróżnicowane (łagodna/normalna/mroźna) — a jeśli tak, czy globalnie czy per region.
- Czy modelujemy zmiany klimatu (trend ocieplenia: lód na jeziorach −14 dni/40 lat) — raczej nie, ale użytkownik chce „jak w rzeczywistości”.
- Poziom szczegółowości ptaków (kilkadziesiąt gatunków z migracjami) — które gatunki są priorytetem.

## 16. Źródła

Klimat:
- IMGW-PiB, Normy klimatyczne 1991–2020 (portal): https://klimat.imgw.pl/pl/climate-normals/
- zpe.gov.pl, „Czynniki kształtujące klimat i jego cechy w Polsce”: https://zpe.gov.pl/a/przeczytaj/DSCauhSet
- Wikipedia en, „Geography of Poland” (sekcja Climate; tabele 1991–2020 dla Warszawy, Wrocławia, Szczecina): https://en.wikipedia.org/wiki/Climate_of_Poland (przekierowanie) / https://en.wikipedia.org/wiki/Geography_of_Poland
- Wikipedia pl, „Geografia Polski”: https://pl.wikipedia.org/wiki/Geografia_Polski
- Wikipedia pl, „Polska” (klimat: okres wegetacyjny 212–255 dni, lipiec Łeba/Wrocław, Śnieżka, Kasprowy): https://pl.wikipedia.org/wiki/Polska
- Wikipedia pl, „Okres wegetacyjny”: https://pl.wikipedia.org/wiki/Okres_wegetacyjny
- Wikipedia pl, „Równina Augustowska” (Suwałki 101 dni śniegu): https://pl.wikipedia.org/wiki/R%C3%B3wnina_Augustowska
- Wikipedia pl, „Harta (Polska)” (pokrywa śnieżna 20–130 dni): https://pl.wikipedia.org/wiki/Harta_(Polska)
- Wikipedia pl, „Rekordy klimatyczne w Polsce”: https://pl.wikipedia.org/wiki/Rekordy_klimatyczne_w_Polsce
- Wikipedia pl, „Klimat we Wrocławiu”: https://pl.wikipedia.org/wiki/Klimat_we_Wroc%C5%82awiu
- Wikipedia pl, „Kryterium termiczne” (progi 6 pór termicznych IMGW): https://pl.wikipedia.org/wiki/Kryterium_termiczne
- Wikipedia pl, „Pory roku” (termiczne 8 pór Romera–Mareckiego, fenologiczne, astronomiczne): https://pl.wikipedia.org/wiki/Pory_roku
- Repozytorium AMU, „Zmienność termicznych pór roku w Poznaniu”: https://repozytorium.amu.edu.pl/bitstreams/0b7c47f9-6fe0-40c5-99b2-590c2e1700a0/download
- Szyga-Pluta, Badania Fizjograficzne (termiczne pory roku): https://repozytorium.amu.edu.pl/bitstream/10593/9800/1/13_szyga-pluta.pdf
- Tylkowski, UJK, „Charakterystyka rocznej temperatury powietrza…”: https://ios.ujk.edu.pl/wydawnictwa/z14/Tylkowski.pdf
- IMGW CMM, „Pory roku okiem astronoma, meteorologa i agrometeorologa”: https://cmm.imgw.pl/?page_id=40550
- konferencja-przyrodnicza.pl, „Termiczne pory roku w Polsce” (HTTP 403 — nie pobrano): https://konferencja-przyrodnicza.pl/termiczne-pory-roku-w-polsce-dlaczego-wyrozniamy-ich-az-szesc
- atlas-roslin.pl, „termiczne pory roku”: https://www.atlas-roslin.pl/termiczne-pory-roku.htm
- naukaoklimacie.pl, „Zlodzenie jezior (pokrywa lodowa jezior) w Polsce a zmiana klimatu” (dane IMGW 1981–2020): https://naukaoklimacie.pl/aktualnosci/zmiana-klimatu-a-zlodzenie-jezior-w-polsce
- WM112.pl / Mazury112.pl, raporty lodowe Mazury I–II 2026: https://wm112.pl/gizycko/raport-lodowy-4-6-lutego-2026-grubosc-lodu-na-mazurach-15-50-cm/ ; https://mazury112.pl/raport-lodowy-z-mazur-aktualne-pomiary-grubosci-lodu-22-23-stycznia-2026/
- IMGW ISOK, mapy klimatologiczne — dni z burzą: https://imgw.isok.gov.pl/mapy-klimatologiczne/burze-z-gradem/srednia-burza.html ; dni z mgłą: https://imgw.isok.gov.pl/mapy-klimatologiczne/mgla/srednia-mgla.html
- Wikipedia en, „Halny”: https://en.wikipedia.org/wiki/Halny
- IMGW, serwis górski: https://gory.imgw.pl/
- tatry-przewodnik.com.pl, „Klimat Tatr”: https://tatry-przewodnik.com.pl/blog/?klimat-tatr=

Fenologia roślin i grzybów:
- Ogród Botaniczny UAM, „Fenologiczne pory roku”: https://ogrod.amu.edu.pl/fenologiczne-pory-roku/
- WLIN, „Fenologiczne pory roku”: https://www.wlin.pl/natura/fenologia/fenologiczne-pory-roku/
- Magazyn Salamandra, „Fenologiczne pory roku”: https://magazyn.salamandra.org.pl/m06a07.html
- Tygodnik Rolniczy, „Jak działa kalendarz fenologiczny?”: https://www.tygodnik-rolniczy.pl/wies-i-rodzina/ogrod/jak-dziala-kalendarz-fenologiczny-2434186
- Wikibooks, „Ekoogrodnictwo/Fenologiczne pory roku”: https://pl.wikibooks.org/wiki/Ekoogrodnictwo/Fenologiczne_pory_roku
- atlas-roslin.pl, „fenologia”: https://www.atlas-roslin.pl/fenologia.htm
- sezonnagrzyby.pl, „Kalendarz grzybiarza”: https://sezonnagrzyby.pl/kalendarz/
- krainagrzybow.pl, „Kiedy zaczyna się sezon grzybowy? Kalendarz 130+ gatunków”: https://krainagrzybow.pl/blog/kiedy-zaczyna-sezon-grzybowy.html
- Starostwo Powiatowe w Kielcach, „Kalendarz grzybiarza”: https://www.powiat.kielce.pl/starostwo/aktualnosci/Kalendarz-grzybiarza/idn:7968
- beszamel.se.pl, „Kalendarz grzybiarza”: https://beszamel.se.pl/porady/jak-zrobic/kalendarz-grzybiarza-kiedy-szukac-grzybow-aa-A9ro-d2Wn-PHAE.html

Fenologia zwierząt:
- Nadleśnictwo Trzebielino (Lasy Państwowe), „Rykowisko – czyli okres godowy jeleni”: https://trzebielino.szczecinek.lasy.gov.pl/aktualnosci/-/asset_publisher/sE8O/content/rykowisko-czyli-okres-godowy-jeleni/maximized
- Polski Związek Łowiecki, „Zwierzęta w terminologii łowieckiej”: https://www.pzlow.pl/edukacja/zwierzeta-w-terminologii-lowieckiej/
- Polskie Radio Jedynka, „Rykowisko, bukowisko, bekowisko – gody jeleniowatych”: https://jedynka.polskieradio.pl/artykul/3429136,Rykowisko-bukowisko-bekowisko---gody-jeleniowatych
- apoczywaj.pl, „Rykowisko, bukowisko i bekowisko”: https://apoczywaj.pl/rzecz-o-zwierzetach/rykowisko-bukowisko-i-bekowisko-czyli-gody-jeleniowatych
- poradniklowiecki.pl, „Kalendarz łowiecki – sezonowość polowań w praktyce”: https://poradniklowiecki.pl/kalendarz-lowiecki-sezonowosc-w-praktyce/
- RDLP Lublin, „Leśny słownik pojęć”: https://www.lublin.lasy.gov.pl/aktualnosci/-/asset_publisher/H9HmG48Tuos2/content/lesny-slownik-pojec
- GDOŚ (gov.pl), „W tym roku bociany białe już opuszczają Polskę”: https://www.gov.pl/web/gdos/w-tym-roku-bociany-biale-juz-opuszczaja-polske---jak-pomagac-bocianom-ktore-pozostana-z-nami
- ekologia.pl, „Kiedy i gdzie odlatują bociany?”: https://www.ekologia.pl/srodowisko/kiedy-i-gdzie-odlatuja-bociany/
- National Geographic Polska, „Kto pierwszy przylatuje do Polski? Kalendarz wiosennych przylotów”: https://www.national-geographic.pl/przyroda/kto-pierwszy-przylatuje-do-polski-kalendarz-wiosennych-przylotow/
- Interia Zielona, „Bociany w Polsce już zbierają się do odlotu”: https://zielona.interia.pl/wiadomosci/polska/news-bociany-w-polsce-juz-zbieraja-sie-do-odlotu-ekspert-nie-zost,nId,7750925

Serene Seasons:
- GitHub, Glitchfiend/SereneSeasons (gałąź 26.1.2): https://github.com/Glitchfiend/SereneSeasons
- GitHub wiki: Home https://github.com/Glitchfiend/SereneSeasons/wiki ; Seasons https://github.com/Glitchfiend/SereneSeasons/wiki/Seasons ; Winter https://github.com/Glitchfiend/SereneSeasons/wiki/Winter ; Crop Fertility https://github.com/Glitchfiend/SereneSeasons/wiki/Crop-Fertility ; Calendar https://github.com/Glitchfiend/SereneSeasons/wiki/Calendar ; Season Sensor https://github.com/Glitchfiend/SereneSeasons/wiki/Season-Sensor ; Commands https://github.com/Glitchfiend/SereneSeasons/wiki/Commands
- Kod (raw, gałąź 26.1.2): SeasonHooks https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/season/SeasonHooks.java ; SeasonsConfig https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/config/SeasonsConfig.java ; Season.java https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/api/season/Season.java ; RandomUpdateHandler https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/season/RandomUpdateHandler.java ; SeasonColorUtil https://raw.githubusercontent.com/Glitchfiend/SereneSeasons/26.1.2/common/src/main/java/sereneseasons/util/SeasonColorUtil.java
- Modrinth, Serene Seasons: https://modrinth.com/mod/serene-seasons ; wersje: https://modrinth.com/mod/serene-seasons/versions
- CurseForge, Serene Seasons: https://www.curseforge.com/minecraft/mc-mods/serene-seasons
