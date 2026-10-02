# Fizycznogeograficzne typy krajobrazu Polski – przegląd na potrzeby generatora terenu w rzeczywistej skali

**Data:** 2026-09-21
**Projekt:** mod "Przyrodniczo zgodne lasy" (Minecraft, Fabric)
**Status:** raport ukończony w ramach budżetu 30 zapytań internetowych (26 WebFetch + 4 WebSearch; 2 adresy Wikipedii zwróciły 404). Fakty potwierdzone w źródle oznaczono gwiazdką (*) lub przypisem; fragmenty oparte na wiedzy ogólnej (Kondracki, podręczniki geomorfologii) są wyraźnie oznaczone "wiedza ogólna / do potwierdzenia"; braki oznaczono "NIE ZBADANO". Pełna lista niepewności – sekcja 10.

## Streszczenie

Polska jest krajem nizinnym: średnia wysokość 173 m n.p.m. (mediana 149 m), 75% powierzchni leży poniżej 200 m, tylko 5,4% w przedziale 300–500 m i ok. 3% powyżej 500 m; amplituda od -2,2 m (Marzęcino na Żuławach; tradycyjnie Raczki Elbląskie -1,8 m) do 2499 m (Rysy). Rzeźba ma wyraźny układ pasowy z południa na północ: góry (Sudety – Śnieżka 1603 m; Karpaty – Tatry 2499 m, Beskidy do 1725 m, Bieszczady 1346 m) -> kotliny podkarpackie -> wyżyny (Jura do 516 m, Świętokrzyskie 614 m, Lubelska, Roztocze) -> niziny środkowopolskie (staroglacjalne, płaskie) -> pojezierza (młodoglacjalne, pagórkowate, do 329 m – Wieżyca) -> pobrzeża (wydmy, klify do 95 m, mierzeje, delta Wisły z depresją 450 km²). Regionalizacja Solon i in. 2018 wyróżnia w Polsce 7 prowincji, 18 podprowincji, 59 makroregionów i 344 mezoregiony; raport zawiera pełną tabelę makroregionów z wysokościami, genezą, podłożem i pokrywą. Formy młodoglacjalne (moreny czołowe 20–150 m wys. wzgl., wysoczyzny faliste 5–30 m, sandry o spadku 1–3‰, rynny 0,2–2 km szer. z jeziorami do 108,5 m gł. – Hańcza, ozy, kemy, drumliny, oczka) tworzą krajobraz północy; południe nizin to płaskie równiny staroglacjalne bez jezior; wyżyny to lessy z wąwozami (gęstość >10 km/km² k. Kazimierza, less 10–30 m), kras wapienny (Jura: 1500 jaskiń, ostańce) i gipsowy (Ponidzie); góry różnią się litologią (granit, gnejs, piaskowiec ciosowy, flisz, wapień) i formami (kotły, granie, zrównania, gołoborza, połoniny, skałki). Polska ma 7081 jezior >1 ha (jeziorność 0,9%, prawie wszystkie na pojezierzach), gęstą sieć rzek (Wisła 1022 km, dorzecze 194 tys. km², 1046 m³/s), największe torfowiska Europy Środkowej (Biebrza – PN 592 km²) oraz rozległe pola wydm śródlądowych (Kampinos do 30 m, Puszcza Notecka). Lesistość wynosi 29,6% (2024), od 49,4% w lubuskim do 21,4% w łódzkim, sosna stanowi 58,5% drzewostanów, a siedliska borowe 51%; użytkowanie gruntów wg województw poza lesistością nie zostało zbadane. Piętra roślinne są parametrem masywu: górna granica lasu w Tatrach ~1500 m, na Babiej Górze ~1390 m, w Karkonoszach ~1250 m, w Bieszczadach ~1150 m (bez regla górnego). Dla generatora zdefiniowano tabelę ~33 typów krajobrazu z wymiarami form, podłożem, siedliskowym typem lasu i analogami bloków oraz 16 implikacji projektowych; kluczowe decyzje (skala pionowa/pozioma, limit wysokości świata, krajobraz rolniczy vs. roślinność potencjalna, poziom szczegółowości biomów) wymagają odpowiedzi użytkownika.

## 1. Regionalizacja fizycznogeograficzna Polski (Kondracki; Solon i in. 2018)

### 1.1. Zasady podziału i hierarchia jednostek

Regionalizacja fizycznogeograficzna Polski (Jerzy Kondracki, 1994/2002) dzieli kraj hierarchicznie na: **megaregiony -> prowincje -> podprowincje -> makroregiony -> mezoregiony**. Kody dziesiętne (np. 313.4, 512.1) są zgodne z systemem stosowanym dla całej Europy (pierwsza cyfra = megaregion/obszar, dwie pierwsze = prowincja, trzy = podprowincja, po kropce = makroregion, kolejna cyfra = mezoregion).

**Aktualizacja Solon i in. 2018** (Geographia Polonica 91(2), zespół 26 geografów pod kierunkiem Jerzego Solona, IGiPZ PAN): granice mezoregionów wytyczono precyzyjnie w skali 1:50 000 z użyciem danych LiDAR (NMT) oraz map geologicznych; liczba mezoregionów wzrosła z 316 (1994) do **344**. Struktura wg Wikipedii pl (stan 2026): **3 megaregiony, 7 prowincji, 18 podprowincji, 59 makroregionów, 344 mezoregiony**. Wektorowe granice (shapefile) są dostępne w Geoserwisie GDOŚ / IGiPZ PAN (do potwierdzenia URL – patrz sekcja 10).

Trzy megaregiony obejmujące Polskę:
1. **Pozaalpejska Europa Środkowa** (3) – Niż Środkowoeuropejski, Masyw Czeski, Wyżyny Polskie;
2. **Karpaty z okolicznymi obniżeniami** (5) – Karpaty Zachodnie i Wschodnie z Podkarpaciem;
3. **Niż Wschodnioeuropejski** (8) – Niż Wschodniobałtycko-Białoruski, Wyżyny Ukraińskie.

**Pasowość rzeźby Polski** (od południa ku północy, naprzemiennie wyższy/niższy): pas gór (najwyższy) -> pas kotlin podkarpackich (niższy) -> pas wyżyn (wyższy) -> pas nizin środkowopolskich (niższy) -> pas pojezierzy (wyższy) -> pas pobrzeży (najniższy). Źródło: Wikipedia pl "Geografia Polski". Ta pasowość jest kluczową cechą, którą generator powinien odwzorować w skali kraju (oś N–S ok. 650 km).

### 1.2. Prowincje i podprowincje

Źródło: Wikipedia pl "Regionalizacja fizycznogeograficzna Polski" (wg Solon i in. 2018).

| Kod | Prowincja | Kod | Podprowincja | Geneza / charakter |
|---|---|---|---|---|
| 31 | Niż Środkowoeuropejski | 313 | Pobrzeża Południowobałtyckie | nadmorska, młodoglacjalna, równiny, klify, mierzeje, delty |
| 31 | Niż Środkowoeuropejski | 314–316 | Pojezierza Południowobałtyckie | młodoglacjalna (zlodowacenie Wisły), pagórki morenowe, jeziora, sandry |
| 31 | Niż Środkowoeuropejski | 317 | Niziny Sasko-Łużyckie | staroglacjalna, równiny i wzniesienia, bory na piaskach |
| 31 | Niż Środkowoeuropejski | 318 | Niziny Środkowopolskie | staroglacjalna (zlodowacenia Odry/Warty), równiny denudacyjne, brak jezior |
| 33 | Masyw Czeski | 332 | Sudety z Przedgórzem Sudeckim | górska (zrębowa, stara), skały krystaliczne i osadowe |
| 34 | Wyżyny Polskie | 341 | Wyżyna Śląsko-Krakowska | wyżynna, monoklina, wapienie, kras, węgiel |
| 34 | Wyżyny Polskie | 342 | Wyżyna Małopolska | wyżynna, paleozoiczne góry (Świętokrzyskie), gipsy, lessy |
| 34 | Wyżyny Polskie | 343 | Wyżyna Lubelsko-Lwowska | wyżynna, lessy i kreda, wąwozy |
| 51 | Karpaty Zachodnie z Podkarpaciem | 512 | Podkarpacie Północne | kotliny (zapadlisko przedkarpackie), doliny, piaski |
| 51 | Karpaty Zachodnie z Podkarpaciem | 513 | Zewnętrzne Karpaty Zachodnie | górska fliszowa (Pogórza, Beskidy) |
| 51 | Karpaty Zachodnie z Podkarpaciem | 514–515 | Centralne Karpaty Zachodnie | górska alpejska (Tatry) + Podhale, Orawa |
| 52 | Karpaty Wschodnie z Podkarpaciem Wschodnim | 521 | Podkarpacie Wschodnie | płaskowyż |
| 52 | Karpaty Wschodnie z Podkarpaciem Wschodnim | 522 | Zewnętrzne Karpaty Wschodnie | górska fliszowa (Bieszczady) |
| 84 | Niż Wschodniobałtycko-Białoruski | 841 | Pobrzeża Wschodniobałtyckie | nadmorska/nizinna (Nizina Staropruska) |
| 84 | Niż Wschodniobałtycko-Białoruski | 842 | Pojezierza Wschodniobałtyckie | młodoglacjalna (Mazury, Suwalszczyzna) |
| 84 | Niż Wschodniobałtycko-Białoruski | 843 | Wysoczyzny Podlasko-Białoruskie | staroglacjalna, wysoczyzny, doliny bagienne |
| 84 | Niż Wschodniobałtycko-Białoruski | 845 | Polesie | nizinna bagienna, kras kredowy |
| 85 | Wyżyny Ukraińskie | 851 | Wyżyna Wołyńsko-Podolska | wyżynna lessowo-kredowa |

### 1.3. Makroregiony – pełny wykaz z charakterystyką

Pełna lista 59 makroregionów wg Solon i in. 2018 (kody i nazwy ze źródła: Wikipedia pl "Regionalizacja fizycznogeograficzna Polski" – zweryfikowane). Charakterystyka rzeźby, wysokości i pokrywy: syntetyczna, oparta na "Geografii regionalnej Polski" J. Kondrackiego (wiedza ogólna); wartości oznaczone gwiazdką (*) zostały potwierdzone w tym badaniu w źródle internetowym (sekcje 2–4). Pozostałe wysokości mezoregionów traktować jako orientacyjne (±10 m) i weryfikować na NMT (geoportal.gov.pl).

Skróty genezy: MG = młodoglacjalna (zlod. Wisły), SG = staroglacjalna (zlod. Odry/Warty), W = wyżynna, G = górska, D = dolinna, N = nadmorska, K = kotlinna.

| Kod | Makroregion | Geneza | Wys. n.p.m. (typowe / maks.) | Deniwelacje | Litologia / podłoże | Typowa pokrywa i uwagi |
|---|---|---|---|---|---|---|
| 313.2–3 | Pobrzeże Szczecińskie | N, MG | 0–50 / 148 (Bukowiec, Wzgórza Bukowe) | 5–100 | gliny, piaski, mady, torfy | Zalew Szczeciński, jez. Dąbie (56 km²), Miedwie; Równina Goleniowska (sandr, Puszcza Goleniowska – bory); Puszcza Bukowa (buczyny); Równina Pyrzycka (żyzne czarne ziemie – pola); Wolin: klify do 95 m* |
| 313.4 | Pobrzeże Koszalińskie | N, MG | 0–60 / 115* (Rowokół) | 5–50 | piaski wydmowe, gliny, torfy | Wybrzeże Słowińskie: mierzeje, wydmy ruchome, jeziora przybrzeżne Łebsko 71 km²*, Gardno 25 km²*; równiny morenowe (Białogardzka, Słupska) – pola; bory nadmorskie |
| 313.5 | Pobrzeże Gdańskie | N, D | -2,2* – 50 / ok. 198 (Wysoczyzna Elbląska) | 0–100 | mady (Żuławy), gliny, piaski | Żuławy 1700 km²*, depresje 450 km²*; Mierzeja Helska i Wiślana (wydmy, bory); Pobrzeże Kaszubskie – kępy wysoczyznowe 50–100 m z klifami (Orłowo, Rozewie) rozcięte pradolinami (Redy-Łeby, Kaszubską); Wysoczyzna Elbląska (buczyny, głębokie doliny) |
| 314.4 | Pojezierze Zachodniopomorskie | MG | 80–150 / ok. 256 | 20–80 | glina zwałowa, piaski sandrowe | Pojezierze Drawskie (moreny fazy pomorskiej, Drawsko gł. 79,7 m), Ińskie, Myśliborskie; Równina Drawska (sandr – Puszcza Drawska); mozaika pól, lasów bukowych i jezior |
| 314.5 | Pojezierze Wschodniopomorskie | MG | 150–250 / 328,6* (Wieżyca) | 50–150 (największe na Niżu) | glina zwałowa, żwiry moren spiętrzonych | Pojezierze Kaszubskie ("Szwajcaria Kaszubska"): rynny (Raduńskie, Wdzydze gł. 68 m), strome pagóry, buczyny i pola; Pojezierze Starogardzkie – równiny morenowe |
| 314.6–7 | Pojezierze Południowopomorskie | MG (sandry) | 100–180 / ok. 220 | 10–40 | piaski i żwiry sandrowe, torfy | **Bory Tucholskie** (największy sandr – bory sosnowe, jeziora rynnowe Charzykowskie), Równina Charzykowska, Tucholska, Dolina Brdy, Gwdy; Pojezierze Krajeńskie (moreny, pola); Wysoczyzna Polanowska |
| 314.8 | Dolina Dolnej Wisły | D | 1–30 (dno) | krawędź 50–70 nad wysoczyznę | mady, piaski, torfy; zbocza: glina, iły | Dolina Kwidzyńska, Fordońska, Basen Grudziądzki: szer. 3–8 km, wcięta 50–70 m; łąki, pola na madach, wały, lasy zboczowe (grąd zboczowy z lipą) |
| 314.9 | Pojezierze Iławskie | MG | 100–150 / ok. 200 | 10–40 | glina zwałowa, piaski | Jeziorak 32 km² (najdłuższe, 27 km, rynnowe), Lasy Iławskie (bory mieszane, buczyny), pola |
| 315.1 | Pojezierze Chełmińsko-Dobrzyńskie | MG | 80–150 / 312 (Dylewska Góra, Garb Lubawski) | 10–60, Garb Lubawski do 150 | glina zwałowa | Pojezierze Chełmińskie (żyzne, pola, drumliny k. Zbójna), Brodnickie (jeziora, lasy), Dobrzyńskie; Dolina Drwęcy; Równina Urszulewska (sandr) |
| 315.3 | Pradolina Toruńsko-Eberswaldzka | D (pradolina) | 30–70 (dno) | 0–30 (wydmy), krawędź 20–50 | piaski rzeczne, torfy, wydmy | 7169 km²*; Kotlina Gorzowska z Puszczą Notecką (pola wydm parabolicznych, bory), Dolina Środkowej Noteci (torfy, łąki), Kotlina Toruńska (Puszcza Bydgoska – wydmy), Kotlina Płocka |
| 315.4 | Pojezierze Lubuskie | MG | 50–150 / 227 (Bukowiec) | 20–80 | piaski sandrowe, glina | Pojezierze Łagowskie (rynny, buczyny), Równina Torzymska (bory), Lubuski Przełom Odry (krawędź 50–80 m), Bruzda Zbąszyńska |
| 315.5 | Pojezierze Wielkopolskie | MG | 70–130 / ok. 150 | 5–30 | glina zwałowa, czarne ziemie (Kujawy) | Pojezierze Poznańskie, Gnieźnieńskie, Kujawskie; Gopło 21,5 km² (rynnowe); płaska morena denna – najbardziej rolnicze, najmniej lasów (Kujawy); moreny fazy poznańskiej |
| 315.6 | Pradolina Warciańsko-Odrzańska | D (pradolina) | 55–80 | 0–20 | torfy, piaski | Kotlina Śremska, Dolina Środkowej Obry, Kotlina Kargowska: łąki, torfowiska niskie, bory na terasach |
| 315.7 | Wzniesienia Zielonogórskie | MG (glacitektonika) | 100–150 / 221 (Wał Zielonogórski) | 50–100 | żwiry, piaski, iły spiętrzone | wał moreny spiętrzonej, lasy sosnowe i mieszane, winnice |
| 315.8 | Pojezierze Leszczyńskie | MG (faza leszczyńska) | 80–120 / 161 (Wał Żerkowski) | 10–50 | glina, piaski | Pojezierze Sławskie, Krzywińskie; jeziora Sławskie, Dominickie; pola i bory |
| 317.2 | Obniżenie Dolnołużyckie | SG | 60–100 | 5–20 | piaski | Kotlina Zasiecka, bory sosnowe, łąki nad Nysą Łużycką |
| 317.4 | Wzniesienia Łużyckie | SG (glacitektonika) | 100–180 / 227 (Wzniesienia Żarskie) | 30–100 | żwiry, iły, węgiel brunatny spiętrzony | Wał Mużakowski (morena spiętrzona, jeziorka pokopalniane), lasy |
| 317.7 | Nizina Śląsko-Łużycka | SG (sandry) | 100–200 | 5–30 | piaski, żwiry sandrowe, wydmy | **Bory Dolnośląskie** (ok. 1650 km² – największy kompleks leśny Polski, wydmy śródlądowe do 20–30 m, wrzosowiska), Równina Legnicka, Wysoczyzna Lubińska (pola) |
| 318.1–2 | Nizina Południowowielkopolska | SG | 100–200 | 5–30 | glina, piaski, równiny denudacyjne | Wysoczyzny Leszczyńska, Kaliska, Turecka, Łaska; Kotlina Szczercowska; równinna, rolnicza, kępy lasów; dolina Warty (Jeziorsko), Prosny |
| 318.3 | Obniżenie Milicko-Głogowskie | SG, D | 80–120 | 5–20 | piaski, torfy, mady | Kotlina Milicka – Stawy Milickie (ok. 77 km² stawów, największe w Europie), łąki, lasy; Pradolina Głogowska (Odra) |
| 318.4 | Wał Trzebnicki | SG (moreny spiętrzone zlod. Warty) | 150–250 / 284 (Kobyla Góra) | 100–150 | żwiry, iły spiętrzone, less | Wzgórza Dalkowskie, Trzebnickie (sady, lessy), Twardogórskie, Ostrzeszowskie; lasy bukowe i mieszane na stokach |
| 318.5 | Nizina Śląska | SG, D | 100–200 / 300 (Płaskowyż Głubczycki) | 5–30 (płaskowyż 30–60) | less, czarnoziemy, mady, piaski | najżyźniejsza nizina: Równina Wrocławska, Płaskowyż Głubczycki (less, 100% pola); Pradolina Wrocławska (Odra – łęgi, starorzecza); Bory Niemodlińskie, Stobrawskie (sandry) |
| 318.6 | Nizina Północnomazowiecka | SG | 90–150 / ok. 235 (Wzniesienia Mławskie) | 5–40 | glina, piaski | Równina Kurpiowska (sandr: Puszcza Kurpiowska – bory), Wysoczyzna Ciechanowska, Płońska (pola), Dolina Dolnej Narwi (łąki, starorzecza) |
| 318.7 | Nizina Środkowomazowiecka | SG, D | 70–130 | 2–20 (wydmy do 30*) | piaski, gliny, mady, torfy | najniższa i najbardziej płaska; Kotlina Warszawska z Puszczą Kampinoską (wydmy paraboliczne do 30 m* + bagna), Dolina Środkowej Wisły (kępy, łachy), Równina Łowicko-Błońska (pola), Równina Warszawska; sady grójeckie |
| 318.8 | Wzniesienia Południowomazowieckie | SG | 150–230 / 284 (Wzniesienia Łódzkie); hałda Góra Kamieńsk 386 (antropogeniczna) | 20–80 | glina, piaski, żwiry; Bełchatów – węgiel brunatny | Wysoczyzna Rawska, Bełchatowska (odkrywka i hałda), Równina Piotrkowska, Kozienicka (Puszcza Kozienicka), Wzgórza Opoczyńskie |
| 318.9 | Nizina Południowopodlaska | SG | 130–200 | 5–30 | glina, piaski, torfy | Wysoczyzna Siedlecka, Łukowska (Lasy Łukowskie), Podlaski Przełom Bugu (meandry, starorzecza, skarpy), mozaika drobnych pól i lasów |
| 332.1 | Przedgórze Sudeckie | W (denudacyjna) | 200–300 / 718 (Ślęża) | 20–50; Ślęża ok. 500 | granit, gabro, gnejs pod lessem | falista równina rolnicza, twardzielowe masywy (Ślęża, Wzgórza Strzelińskie 393, Strzegomskie – kamieniołomy granitu) porośnięte lasem |
| 332.2 | Pogórze Zachodniosudeckie | G (pogórze) | 250–450 / 501 (Ostrzyca) | 50–200 | bazalty, zieleńce, wapienie, less | Pogórze Izerskie, Kaczawskie: stożki bazaltowe, pola, lasy liściaste, kamieniołomy |
| 332.3 | Sudety Zachodnie | G | 400–1200 / 1603* (Śnieżka) | 500–1200 | granit, gnejs, łupki | Karkonosze (zrównania, kotły, skałki*), Góry Izerskie (1126, torfowiska), Kotlina Jeleniogórska (300–400, pola, stawy), Rudawy Janowickie (945), Góry Kaczawskie (724) |
| 332.4–5 | Sudety Środkowe | G | 400–900 / 1015 (Wielka Sowa) | 300–600 | gnejs (Sowie), piaskowiec (Stołowe 919), porfiry, melafiry (Kamienne), łupki | Góry Stołowe (płyty, labirynty skalne), Sowie, Wałbrzyskie, Kamienne, Bystrzyckie, Orlickie, Bardzkie; Kotlina Kłodzka (280–400, rolnicza) |
| 332.6 | Sudety Wschodnie | G | 500–1100 / 1425 (Śnieżnik) | 400–900 | gnejs, marmury, łupki | Masyw Śnieżnika (Jaskinia Niedźwiedzia), Góry Bialskie, Złote (989), Opawskie (889); świerczyny, buczyny |
| 341.1 | Wyżyna Śląska | W | 250–350 / 400 (Góra Św. Anny) | 30–100; hałdy 30–100 | wapienie i dolomity triasowe, karbon (węgiel), less | Garb Tarnogórski, Wyżyna Katowicka (aglomeracja, hałdy, zapadliska, zbiorniki poeksploatacyjne), Chełm (bazaltowy wulkan), Płaskowyż Rybnicki; lasy: Lasy Pszczyńskie, Lublinieckie |
| 341.2 | Wyżyna Woźnicko-Wieluńska | W | 200–300 / ok. 380 | 30–80 (kuesty) | wapienie, piaskowce, iły jurajskie | Próg Woźnicki, Herbski, Wyżyna Wieluńska (kras, Załęczański PK), Obniżenie Górnej Warty; lasy i pola |
| 341.3 | Wyżyna Krakowsko-Częstochowska | W (kras) | 300–450 / 515,6* | 50–150 (doliny do 100) | wapienie górnojurajskie*, less | ostańce, ok. 1500 jaskiń*, doliny krasowe (Prądnika), Pustynia Błędowska, ok. 20% lasów* (buczyny, bory), pola, murawy kserotermiczne |
| 342.1 | Wyżyna Przedborska | W | 200–300 / 347 (Fajna Ryba) | 30–100 | wapienie jurajskie, piaskowce kredowe, piaski | Pasmo Przedborsko-Małogoskie, Niecka Włoszczowska (piaski, lasy, stawy), Wzgórza Radomszczańskie; lasy 30–40% |
| 342.2 | Niecka Nidziańska | W | 150–300 / ok. 320 | 20–80 | gipsy (kras gipsowy), margle, less, czarnoziemy | Płaskowyż Proszowicki (lessy, wąwozy, czarnoziemy – pola), Niecka Solecka (kras gipsowy: Skorocice, jaskinie, leje), Dolina Nidy (łąki, meandry), Garb Pińczowski; najbardziej stepowa część kraju |
| 342.3 | Wyżyna Kielecka | W/G | 250–400 / 614* (Łysica) | 100–300 | kwarcyty kambryjskie*, wapienie dewońskie*, piaskowce, less (Sandomierz) | Góry Świętokrzyskie (gołoborza*, Puszcza Jodłowa*), Płaskowyż Suchedniowski (lasy), Wyżyna Sandomierska (lessy, wąwozy, sady), Przedgórze Iłżeckie |
| 343.1 | Wyżyna Lubelska | W (lessowa) | 200–300 / 311 (Działy Grabowieckie) | 30–90 (skarpa Wisły 90*) | less do 30 m* na opokach kredowych | Płaskowyż Nałęczowski (wąwozy >10 km/km²*), Padół Zamojski, Wyniosłość Giełczewska; najbardziej rolnicza wyżyna (pola, sady, chmiel); lasy <15% |
| 343.2 | Roztocze | W | 250–350 / 390 (Wielki Dział) | 50–100; krawędź nad Kotliną Sandomierską 100 | wapienie, piaski, less (Roztocze Zach.) | Roztocze Zachodnie (wąwozy lessowe), Środkowe (RPN – bory jodłowe, buczyny, piaski), Wschodnie; Puszcza Solska (sandr, bory) na przedpolu |
| 512.1 | Kotlina Ostrawska | K | 200–260 | 5–30 | żwiry, mady | mała część w Polsce (Olza), rolniczo-przemysłowa |
| 512.2 | Kotlina Oświęcimska | K | 220–300 | 10–40 | żwiry, mady, iły | Dolina Górnej Wisły – stawy rybne ("Dolina Karpia", Goczałkowice), Równina Pszczyńska (Lasy Pszczyńskie), Podgórze Wilamowickie |
| 512.3 | Brama Krakowska | K/W | 200–300 | 30–100 | wapienie jurajskie (zręby), mady | zręby wapienne (Wawel, Tyniec, Bielany), Rów Skawiński, Obniżenie Cholerzyńskie |
| 512.4–5 | Kotlina Sandomierska | K, D | 150–250 / ok. 300 (Płaskowyż Kolbuszowski) | 10–50 | piaski (Puszcza Sandomierska), mady, less (Pogórze Rzeszowskie) | szerokie doliny Wisły, Sanu, Wisłoki (łęgi, starorzecza, łąki); Równina Tarnobrzeska, Biłgorajska (Puszcza Solska), Płaskowyż Kolbuszowski – bory sosnowe na piaskach; Pogórze Rzeszowskie – pola |
| 513.3 | Pogórze Zachodniobeskidzkie | G (pogórze) | 300–450 / ok. 550 | 100–250 | flisz, less, gliny | Pogórze Śląskie, Wielickie, Wiśnickie: garby, pola pasowe, lasy grądowe, osuwiska |
| 513.4–5 | Beskidy Zachodnie | G (flisz) | 500–1200 / 1725 (Babia Góra) | 400–900 | piaskowce magurskie, godulskie, istebniańskie, łupki | Beskid Śląski (1257), Żywiecki (1725, 1557), Mały, Makowski, Wyspowy, Gorce (1310), Sądecki (1262); Kotlina Żywiecka, Sądecka; buczyna karpacka, świerczyny, polany |
| 513.6 | Pogórze Środkowobeskidzkie | G (pogórze) | 300–500 / ok. 600 | 100–250 | flisz, less | Pogórze Rożnowskie, Ciężkowickie (skałki), Strzyżowskie, Dynowskie, Przemyskie; Doły Jasielsko-Sanockie (obniżenie 250–350, rolnicze) |
| 513.7 | Beskidy Środkowe | G (flisz) | 500–850 / 997 (Lackowa) | 300–500 | flisz magurski | Beskid Niski: rusztowe, łagodne grzbiety, przełęcze (Dukielska 500), lasy bukowo-jodłowe, łąki po wsiach łemkowskich; Magurski PN |
| 514.1 | Obniżenie Orawsko-Podhalańskie | K/G | 550–1000 / 1126 (Gubałówka); Pieniny 982 | 100–500 | flisz podhalański, żwiry, torf; wapienie (Pieniny) | Kotlina Orawsko-Nowotarska (torfowiska wysokie), Pogórze Spisko-Gubałowskie (garby, pola), Rów Podtatrzański, **Pieniny** (przełom Dunajca, skałki wapienne) |
| 514.5 | Łańcuch Tatrzański | G (alpejska) | 900–2000 / 2499* (Rysy) | do 2000* | granitoidy*, gnejsy*, wapienie i dolomity* (regle) | Tatry Zachodnie (wapienne, jaskinie 857*), Wysokie (granitowe granie, kotły, ~200 stawów*); piętra roślinne* |
| 521.1 | Płaskowyż Sańsko-Dniestrzański | W (płaskowyż) | 300–400 | 50–100 | flisz, less | niewielki skrawek w Polsce (Płaskowyż Chyrowski, okolice Przemyśla) |
| 522.1 | Beskidy Lesiste | G (flisz) | 500–1100 / 1346* (Tarnica) | 500–800 | piaskowce i łupki krośnieńskie* | Bieszczady Zachodnie (połoniny >1150 m*, rusztowe grzbiety*), Góry Sanocko-Turczańskie (do ~1000); buczyna karpacka, olszyny; Jezioro Solińskie |
| 841.5 | Nizina Staropruska | MG/N | 40–120 / 216 (Wzniesienia Górowskie) | 10–60 | glina zwałowa, iły zastoiskowe | Równina Sępopolska (obniżenie, ciężkie gleby – pola), Równina Ornecka, Wzniesienia Górowskie (lasy) |
| 842.7 | Pojezierze Litewskie | MG | 150–250 / 298 (Góra Rowelska) | 50–100 (największe na pojezierzach wschodnich) | glina, żwiry, głazy, piaski sandrowe | Pojezierze Wschodniosuwalskie (Hańcza 108,5 m*, Góra Cisowa 256*, ozy, kemy, głazowiska; 60% pól*, 24% lasów*, 10% wód*), Równina Augustowska (sandr – Puszcza Augustowska, Wigry 21,7 km²), Puszcza Romincka |
| 842.8 | Pojezierze Mazurskie | MG | 100–200 / 309 (Szeska Góra) | 20–80 | glina zwałowa, piaski sandrowe, torfy | Kraina Wielkich Jezior (Śniardwy 113,8 km², Mamry 104 km², poziom ~116 m), Pojezierze Olsztyńskie, Mrągowskie, Ełckie (pagórki, jeziora, pola), Równina Mazurska (sandr – Puszcza Piska), Wzgórza Szeskie |
| 843.3 | Nizina Północnopodlaska | SG | 100–180 / 238 (Wzgórza Sokólskie) | 10–50 | glina, piaski, torfy | Kotlina Biebrzańska (bagna – BPN 592 km²*), Dolina Górnej Narwi (anastomozy), Wysoczyzna Białostocka (Puszcza Knyszyńska – bory świerkowo-sosnowe), Równina Bielska (**Puszcza Białowieska**, 130–190 m, płaska, grądy, olsy, łęgi), Wysoczyzna Kolneńska |
| 845.1 | Polesie Zachodnie | nizinna bagienna | 150–200 | 5–20 | piaski na kredzie, torfy | Pojezierze Łęczyńsko-Włodawskie (ok. 68 jezior krasowo-wytopiskowych, Poleski PN – torfowiska przejściowe i wysokie), Równina Parczewska, Kodeńska (lasy, łąki, bagna) |
| 845.3 | Polesie Wołyńskie | nizinna | 170–230 / ok. 250 | 10–60 | kreda, piaski | Pagóry Chełmskie (kredowe wzgórza, kamieniołomy), Obniżenie Dubieńskie (torfowiska węglanowe k. Chełma), Obniżenie Dorohuckie |
| 851.1 | Wyżyna Wołyńska | W (lessowa) | 200–260 | 20–50 | less, czarnoziemy, kreda | Kotlina Hrubieszowska, Grzęda Horodelska, Sokalska – najżyźniejsze gleby Polski, prawie bezleśne |
| 851.2 | Kotlina Pobuża | nizinna | 200–240 | 5–20 | piaski, torfy | Równina Bełska – łąki, bory, dolina Bugu |

## 2. Hipsometria Polski – statystyki

Źródło podstawowe: Wikipedia pl "Geografia Polski" (dane GUS/Kondracki), stan 2026-09-21.

| Parametr | Wartość | Uwagi / źródło |
|---|---|---|
| Powierzchnia całkowita | 322 575 km² | GUS |
| Obszar lądowy | 311 895 km² | GUS |
| Rozciągłość N–S / W–E | ok. 649 km / ok. 689 km | wartość ogólnie znana; do potwierdzenia dokładnie |
| Średnia wysokość | **173 m n.p.m.** | Wikipedia pl "Geografia Polski" |
| Mediana wysokości | 149 m n.p.m. | Wikipedia pl "Geografia Polski" |
| Udział obszaru poniżej 200 m n.p.m. | ok. 75% | Wikipedia pl; w innych źródłach 75,4% (niziny) |
| Udział 200–300 m | ok. 16% (obliczone jako reszta: 100 – 75 – 5,4 – 3) | wartość pochodna, do potwierdzenia |
| Udział 300–500 m | 5,4% | Wikipedia pl |
| Udział powyżej 500 m | ok. 3% | Wikipedia pl |
| Udział powyżej 1000 m | ok. 0,2% | wartość ogólnie podawana w podręcznikach; NIE POTWIERDZONO w źródle pierwotnym |
| Najwyższy punkt | Rysy, **2499 m n.p.m.** (wierzchołek NW, polski) | Tatry Wysokie |
| Najniższy punkt | **-2,2 m p.p.m., Marzęcino (Żuławy Wiślane)** wg nowszych pomiarów; wcześniej podawano Raczki Elbląskie -1,8 m | Wikipedia pl; rozbieżność – patrz sekcja 10 |
| Długość granic | 3572 km ogółem, w tym lądowe 3071 km, morska 501 km | GUS |
| Długość wybrzeża Bałtyku | 775 km (w tym Zalew Szczeciński i Wiślany; otwarte morze ok. 500 km) | GUS |
| Amplituda wysokości w Polsce | ok. 2501 m | Rysy – Marzęcino |

**Wniosek skalowy:** w rzeczywistej skali 1 blok = 1 m generator musiałby obsłużyć zakres od Y = -2 do Y = 2499 m; przy standardowym limicie świata (-64..319) potrzebne jest albo rozszerzenie limitu wysokości (data pack / mixin), albo skala pionowa ~1:8 dla gór z zachowaniem 1:1 na nizinach (patrz sekcja 9).

Udział jednostek genetycznych (Wikipedia pl, dane ogólnie znane, do potwierdzenia w Kondrackim): niziny (<300 m) ok. 91%, wyżyny (300–500 m) ok. 5–6%, góry (>500 m) ok. 3%.

**Szczegółowe piętra hipsometryczne (wartości podręcznikowe wg Kondrackiego; NIE UDAŁO SIĘ ich potwierdzić w źródle internetowym w tym badaniu – 2 wyszukiwania bez wyniku; traktować jako przybliżenie do weryfikacji w "Geografii regionalnej Polski" J. Kondrackiego):**

| Piętro | Udział powierzchni Polski | Status |
|---|---|---|
| poniżej 100 m n.p.m. | ok. 25% | niepotwierdzone (podręcznikowe) |
| 100–200 m | ok. 50% | niepotwierdzone; łącznie <200 m = 75% potwierdzone (Wikipedia pl) |
| 200–300 m | ok. 16% | pochodna |
| 300–500 m | 5,4% | potwierdzone (Wikipedia pl) |
| 500–1000 m | ok. 2,8% | pochodna (>500 m = 3% potwierdzone) |
| powyżej 1000 m | ok. 0,2% | niepotwierdzone (podręcznikowe) |

**Depresje** (Wikipedia pl "Żuławy Wiślane"): łączna powierzchnia depresji na Żuławach 450 km² (ok. 28% delty o pow. ok. 1700 km²); największy płat depresyjny wokół jeziora Druzno 181 km² (22 km × 13 km), drugi koło Nowego Dworu Gdańskiego 152 km². 47% Żuław leży w przedziale 0–5 m n.p.m., 25% powyżej 5 m. Raczki Elbląskie -1,8 m p.p.m. (dawniej najniższy punkt), obecnie Marzęcino -2,2 m p.p.m. Najwyższe punkty Żuław: 14,6 m (Grabiny-Zameczek), 11,4 m (Jegłownik); nasada delty (Mątowska Głowa) ok. 10 m n.p.m. Inne depresje w Polsce (wiedza ogólna, do potwierdzenia): okolice jeziora Dąbie, Zalewu Szczecińskiego, ujścia Redy.

## 3. Formy terenu z wymiarami

### 3.1. Formy młodoglacjalne (moreny, sandry, ozy, drumliny, kemy, rynny, wytopiska)

**Zasięg:** rzeźba młodoglacjalna (zlodowacenie Wisły / bałtyckie, maks. ok. 20–24 tys. lat temu) obejmuje pas pojezierzy i pobrzeży – w przybliżeniu na północ od linii: Zielona Góra – Leszno – Konin – Płock – Mława – Augustów (wiedza ogólna, do potwierdzenia na mapie zasięgu). Na południe od niej rzeźba staroglacjalna (zlodowacenia Odry i Warty): "praktycznie nie występują jeziora", rzeźba mało zróżnicowana, wysoczyzny zdenudowane (zpe.gov.pl "Rzeźba staro- i młodoglacjalna w Polsce").

**Wymiary form – dane potwierdzone w tym badaniu:**

| Forma | Zweryfikowane dane | Źródło |
|---|---|---|
| Kem | pagórek o płaskim wierzchołku, średnica do kilkuset metrów; wysokość od kilku do kilkudziesięciu m; piaski i żwiry | Wikipedia pl "Rzeźba młodoglacjalna"; zpe.gov.pl |
| Pradolina | szerokie doliny, dno ok. 2–25 km szerokości | Wikipedia pl "Rzeźba młodoglacjalna" |
| Wysoczyzna morenowa pagórkowata | wysokości względne 5–10 m (wyjątkowo do 20 m), zróżnicowane nachylenia stoków | wyniki wyszukiwania (definicja geomorfologiczna) |
| Najwyższy punkt strefy młodoglacjalnej | Wieżyca 328,6 m n.p.m. (Pojezierze Kaszubskie) | Wikipedia pl |
| Najniższy punkt | Żuławy, -1,8 / -2,2 m p.p.m. | Wikipedia pl |
| Głaz narzutowy Trygław (Tychowo) | obwód 44 m, wys. 3,8 m, dł. 13,7 m, szer. 9,3 m – największy w Polsce | zpe.gov.pl |
| Jezioro rynnowe Hańcza | gł. maks. **108,5 m** (najgłębsze w Polsce i na Niżu Środkowoeuropejskim), dł. 4,5 km, szer. maks. 1185 m, obj. 124,4 mln m³ | Wikipedia pl "Suwalski Park Krajobrazowy" |
| Pojezierze Suwalskie – kulminacje | Góra Cisowa 256 m n.p.m. (w parku); 24 jeziora na 63 km²; użytki rolne ok. 60%, lasy 24%, wody 10%, bagna 4% | Wikipedia pl "Suwalski Park Krajobrazowy" |

**Wymiary typowe wg podręczników geomorfologii (WARTOŚCI ORIENTACYJNE – nie potwierdzone w tym badaniu w źródle internetowym, spójne z literaturą: Klimaszewski "Geomorfologia", Migoń "Geomorfologia"; do weryfikacji):**

| Forma | Wysokość względna | Szerokość | Długość | Nachylenie | Materiał | Przykłady w Polsce |
|---|---|---|---|---|---|---|
| Morena czołowa (wał/ciąg pagórków) | 20–100 m (maks. ok. 150–200 m: Wieżyca 329, Dylewska Góra 312, Szeska Góra 309 m n.p.m.) | pojedyncze wzgórze 0,5–2 km; ciąg 2–10 km szer. | ciągi dziesiątki–setki km (fazy: pomorska, poznańska, leszczyńska) | 5–20°, lokalnie >25° | glina zwałowa, głazy, żwiry, piaski (spiętrzone) | Wzgórza Szeskie, Dylewskie, Kaszubskie; Wał Trzebnicki (staroglac.), Wzgórza Dalkowskie |
| Morena denna falista/płaska | 2–10 m (falista), <2 m (płaska) | – | rozległe płaty dziesiątki km | 0–3° | glina zwałowa (gleby gliniaste, żyzne – pola uprawne) | wysoczyzny Pojezierza Wielkopolskiego, Chełmińskiego, Mazurskiego |
| Sandr (równina sandrowa) | równina; spadek 1–3‰ od moren czołowych na S | 5–30 km | 20–100 km | <1° | piaski i żwiry fluwioglacjalne – gleby ubogie -> bory sosnowe | Bory Tucholskie, Puszcza Piska, Augustowska, Kurpiowska (sandry), Równina Charzykowska |
| Oz | 10–40 m (rzadko 60 m) | 50–300 m (podstawa) | od kilkuset m do kilkunastu (kilkudziesięciu) km, kręty wał | zbocza 15–30° | piaski, żwiry warstwowane | oz Bukowsko-Mosiński (Wielkopolska), ozy Pojezierza Suwalskiego |
| Drumlin | 5–30 m | 100–500 m | 200–2000 m (stosunek dł./szer. 2–5), owalny "łyżkowy" | łagodne, 5–10° | glina zwałowa z jądrem piaszczystym | pola drumlinowe koło Zbójna (Pojezierze Dobrzyńskie), Gniewu, Radzynia Chełmińskiego |
| Kem | 5–40 m | 100–500 m (średnica do kilkuset m) | – | 10–20° | piaski, mułki warstwowane | Pojezierze Suwalskie, Mazurskie, Dobrzyńskie |
| Rynna subglacjalna / jezioro rynnowe | wcięcie 10–60 m (Hańcza: gł. wody 108,5 m) | 0,2–2 km | 5–40 km (wąskie, kręte, ciągi jezior) | zbocza strome 15–35° | dno nierówne (przegłębienia) | Hańcza, Jeziorak (27 km dł.), Gopło, Wigry (część), Raduńskie |
| Jezioro morenowe (zastoiskowe/wytopiskowe w obniżeniu moreny dennej) | głębokość mała 5–30 m; brzegi łagodne, linia brzegowa urozmaicona, wyspy | kilka–kilkanaście km | – | – | glina, mułki | Śniardwy (113,8 km², gł. 23,4 m – wiedza ogólna, do potw.), Mamry (104 km²), Niegocin |
| Oczko wytopiskowe | zagłębienie 2–10 m | 20–300 m średnicy | – | – | bezodpływowe, często zatorfione, olsy, szuwary | tysiące na wysoczyznach Pojezierzy i Mazur |
| Zagłębienie bezodpływowe / wytopisko duże | 5–20 m | 0,3–3 km | – | – | torfy, gytie | zatorfione misy Mazur, Krajny |

**Wniosek skalowy dla generatora:** na wysoczyźnie młodoglacjalnej dominuje "pagórkowatość" o długości fali 300–1500 m i amplitudzie 5–30 m, z rzadszymi grzbietami morenowymi (amplituda 50–150 m, długość fali 2–5 km) oraz liniowymi rynnami (szer. 0,5–2 km, głębokość 20–60 m poniżej wysoczyzny, wypełnione wodą). Sandry są prawie płaskie (spadek 1–3‰, tj. 1–3 m na km).

### 3.2. Pradoliny i doliny rzeczne (terasy, meandry, starorzecza, kępy)

**Pradoliny** (Wikipedia pl "Rzeźba młodoglacjalna", "Pradolina Toruńsko-Eberswaldzka"): szerokie, płaskodenne doliny o dnie 2–25 km szerokości, powstałe z odpływu wód roztopowych wzdłuż czoła lądolodu na zachód (do Morza Północnego). Pradolina Toruńsko-Eberswaldzka (315.3): powierzchnia w Polsce 7169 km², powstała w fazie pomorskiej; mezoregiony: Kotlina Freienwaldzka (Niemcy), Kotlina Gorzowska, Dolina Środkowej Noteci, Kotlina Toruńska, Kotlina Płocka. Wykorzystywana dziś fragmentami przez Noteć, Wartę (dolny bieg), Wisłę (Kotlina Toruńska, Płocka). Pradolina Warszawsko-Berlińska / Warciańsko-Odrzańska (315.6): Obra, Warta (odcinek koło Śremu), Ner, Bzura. Dno pradolin: piaski rzeczne i torfy; typowa pokrywa: łąki i pastwiska w części zatorfionej, bory sosnowe na terasach piaszczystych i polach wydmowych (Puszcza Notecka w Kotlinie Gorzowskiej – wydmy paraboliczne do ok. 20–30 m; wiedza ogólna, do potwierdzenia). Krawędź pradoliny względem wysoczyzny: typowo 20–50 m (wiedza ogólna; NIE potwierdzono w źródle).

**Wisła** (Wikipedia pl "Wisła"): długość 1022 km (GUS 2010; wcześniej 1047 km), dorzecze 194 424 km², średni spadek 1,04‰, źródła Czarna Wisełka 1107 m n.p.m. / Biała Wisełka 1080 m n.p.m. (Barania Góra, Beskid Śląski), średni przepływ przy ujściu 1046 m³/s, najwyższy punkt dorzecza Gerlach 2655 m. Delta: podział ramion ok. 50 km od ujścia (Biała Góra k. Sztumu): Nogat (prawe), Leniwka (lewe), Szkarpawa, Martwa Wisła, Wisła Królewiecka; Przekop Wisły (1895) – sztuczne ujście. Zbiorniki: Czerniańskie, Goczałkowickie, Włocławskie. Artykuł Wikipedii NIE podaje szerokości koryta w poszczególnych odcinkach.

**Szerokości koryt i dolin – wiedza ogólna, oznaczona jako NIEPOTWIERDZONA w tym badaniu (do weryfikacji w opracowaniach RZGW/Wód Polskich lub na ortofotomapie geoportal.gov.pl):**

| Rzeka / odcinek | Szer. koryta (m) | Szer. dna doliny / tarasu zalewowego | Spadek | Charakter |
|---|---|---|---|---|
| Wisła górna (Kraków) | 100–200 | 2–5 km | 0,3–0,5‰ | uregulowana, stopnie wodne |
| Wisła środkowa (Sandomierz–Warszawa–Płock) | 300–1000 (z łachami i kępami) | 5–15 km (Kotlina Sandomierska, Nizina Środkowomazowiecka) | 0,2–0,3‰ | roztokowo-meandrująca, "dzika": piaszczyste łachy, kępy z łęgami wierzbowo-topolowymi, starorzecza |
| Małopolski Przełom Wisły (Zawichost–Puławy) | 300–600 | 1,5–3 km wcięte 50–90 m w wyżynę lessowo-wapienną | – | przełom, krawędzie z wąwozami (Kazimierz Dolny) |
| Wisła dolna (Toruń–Tczew) | 300–500 (uregulowana ostrogami) | 3–8 km (Dolina Dolnej Wisły, wcięta 40–70 m w wysoczyzny) | 0,15–0,2‰ | jednokorytowa, obwałowana |
| Odra środkowa i dolna | 100–250 | 3–10 km, koło Szczecina dwa ramiona i jezioro Dąbie | 0,1–0,3‰ | uregulowana, polder Międzyodrze |
| Warta | 50–150 | 2–5 km (ujściowy odcinek w pradolinie do 10 km) | 0,2–0,5‰ | meandrująca, starorzecza (Ujście Warty PN) |
| Bug | 60–150 | 2–6 km | 0,15–0,3‰ | naturalna, meandrująca, liczne starorzecza, piaszczyste skarpy |
| Narew | 50–120 (odcinek Narwiański PN: wielokorytowy anastomozujący) | 2–5 km bagienne dno | 0,1–0,2‰ | "polska Amazonka" – sieć koryt w torfowisku |
| San | 50–120 | 1–4 km | 0,5–1,5‰ (górny odc. górski) | meandrujący, żwirowe łachy w górnym biegu |
| Dunajec | 40–100 | 0,3–2 km (przełom pieniński ok. 100–300 m dna) | 2–5‰ | żwirowa, roztokowa w Kotlinie Sądeckiej |

Typowe elementy dna doliny dużej rzeki nizinnej: koryto główne z piaszczystymi łachami i wyspami (kępy: dł. 200–2000 m, szer. 50–400 m), starorzecza (długość 0,3–3 km, szer. 30–150 m, głębokość 1–4 m), wały przeciwpowodziowe (wys. 3–6 m), taras zalewowy (mady, łęgi, łąki), taras nadzalewowy (piaski, wydmy śródlądowe, bory sosnowe), krawędź wysoczyzny.

### 3.3. Bagna i torfowiska

**Biebrzański Park Narodowy** (Wikipedia pl): **592,23 km² – największy PN w Polsce**, otulina 668,24 km²; ochrona ścisła 7494 ha, czynna 27 699 ha, krajobrazowa 24 030 ha; Biebrza w parku ok. 155 km biegu (od ujścia Niedźwiedzicy do ujścia do Narwi); łoś ok. 400 szt. (największa ostoja w Polsce), 271 gat. ptaków, 49 gat. ssaków; pożar 2020 r. – 5280 ha (9,5% parku), pożar 2025 – 450 ha (w tym 22 ha Czerwonego Bagna). Artykuł NIE podaje szerokości basenów ani udziału typów roślinności. **Wiedza ogólna (do potwierdzenia):** dolina Biebrzy dzieli się na Basen Północny (szer. 1–3 km), Środkowy (szer. do 10–15 km, torfowiska niskie i mechowiska, Czerwone Bagno – torfowisko wysokie z borem bagiennym) i Południowy (szer. 8–12 km, rozlewiska wiosenne trwające 2–3 mies., turzycowiska); spadek Biebrzy bardzo mały (ok. 0,1‰), silnie meandrująca, koryto 20–40 m; strefowość roślinności od koryta: szuwary i turzycowiska -> mechowiska (torfowiska niskie) -> zarośla wierzbowe/brzozowe -> olsy i brzeziny bagienne -> bory bagienne na obrzeżu; wydmy śródlądowe ("grzędy", np. Grzędy, Wilcza Góra) 5–15 m ponad torfowisko z borami sosnowymi; miąższość torfu 1–3 m (lokalnie 6 m).

**Narew (Narwiański PN, 68 km²) – wiedza ogólna:** rzeka anastomozująca – sieć 5–10 równoległych koryt (szer. 5–30 m) w torfowisku o szer. 1–3 km; szuwary trzcinowe i turzycowe.

**Polesie Lubelskie (Poleski PN 97,6 km²) – wiedza ogólna:** torfowiska niskie, przejściowe i wysokie (Durne Bagno, Bagno Bubnów – torfowiska węglanowe), płytkie jeziora krasowo-wytopiskowe (Łukie, Moszne – gł. 1–3 m, zarastające "spleją"), lasy bagienne (brzeziny, bory bagienne), teren płaski 160–180 m n.p.m., deniwelacje 2–10 m.

**Torfowiska wysokie (bałtyckie) Pomorza – wiedza ogólna:** kopułowe torfowiska wysokie typu bałtyckiego w zagłębieniach wysoczyzn i na wybrzeżu (Słowińskie Błota, Bielawa k. Karwi ~7 km², Roby, Warnie Bagno, Kurze Grzędy w Kaszubskim PK); kopuła 2–5 m ponad otoczenie, torf sfagnowy o miąższości 5–10 m; roślinność: torfowce, wrzosiec bagienny, bagno zwyczajne, bór bagienny sosnowy na obrzeżu, jeziorka dystroficzne.

**Torfowiska wysokie Orawy (Kotlina Orawsko-Nowotarska) – wiedza ogólna:** największy w Polsce kompleks torfowisk wysokich typu kontynentalnego: Puścizna Wielka (ok. 400 ha), Puścizna Rękowiańska, Bór na Czerwonem (rezerwat, ok. 115 ha), Baligówka; kopuły do 5–7 m miąższości, 600–660 m n.p.m., kosodrzewina na torfie (!), sosna, bór bagienny.

**Torfowiska górskie:** Równia pod Śnieżką (Karkonosze, subalpejskie, 20 ha*), Hala Izerska (Góry Izerskie, torfowiska wysokie na 840–880 m, mrozowisko), Wielkie Torfowisko Batorowskie (Góry Stołowe), Bieszczady – torfowiska wysokie w dolinie górnego Sanu (Tarnawa, Wołosate, Litmirz; wiedza ogólna).

**Udział mokradeł w Polsce – wiedza ogólna, do potwierdzenia (IMUZ/GDOŚ):** torfowiska ok. 4% powierzchni kraju (ok. 12–13 tys. km²), z czego >90% to torfowiska niskie (doliny rzeczne, misy pojezierne), w większości odwodnione i użytkowane jako łąki; torfowiska wysokie ok. 5% areału torfowisk (Pomorze, Orawa, góry); olsy i łęgi łącznie 3,8% powierzchni lasów (Wikipedia pl "Lasy w Polsce").

### 3.4. Wydmy śródlądowe i nadmorskie

**Kampinoski Park Narodowy** (Wikipedia pl): pow. 385,44 km² (38 544 ha), otulina 377,56 km²; ochrona ścisła 5823 ha (15%); **wydmy paraboliczne o wysokości do 30 m**, Grochalskie Piachy – największy kompleks piasków wydmowych w Polsce; wydmy porośnięte borem sosnowym; lasy 73,1% powierzchni parku, dąb ok. 10% składu; 1442 gat. roślin naczyniowych; łoś od 1951, bóbr od 1980 (7 szt.), ryś od 1992; 17 gat. nietoperzy. **Wiedza ogólna (do potwierdzenia):** Puszcza Kampinoska leży na tarasie nadzalewowym (pradolinnym) Wisły ~70–80 m n.p.m.; układ równoleżnikowy: dwa pasy wydmowe (północny i południowy) przedzielone dwoma pasami bagiennymi (olsy, łąki, turzycowiska – dawne koryta Wisły); wydmy paraboliczne otwarte ku zachodowi (wiatry W), ramiona dł. 1–3 km, wysokość 10–30 m, stok dowietrzny (zewnętrzny) 5–12°, zawietrzny (wewnętrzny) 25–33°; wydmy powstały u schyłku ostatniego glacjału (ok. 12–10 tys. lat temu).

**Wydmy śródlądowe – ogólna charakterystyka (wiedza ogólna, do potwierdzenia):** występują na piaszczystych tarasach pradolin i sandrach: Puszcza Notecka (Kotlina Gorzowska – jedno z największych pól wydmowych w Europie Środkowej, ok. 1300 km² puszczy, wydmy do 20–30 m), Puszcza Bydgoska, Bory Dolnośląskie, Puszcza Sandomierska, Puszcza Solska, Kotlina Biebrzańska (grzędy), Równina Kurpiowska, Puszcza Kozienicka, Bory Stobrawskie. Formy: paraboliczne (najczęstsze), wałowe, łukowe; wysokość typowo 5–20 m, wyjątkowo 30–40 m; utrwalone borem sosnowym świeżym/suchym (chrobotkowym); Pustynia Błędowska (Wyżyna Śląska/Jura) – ok. 32 km² odsłoniętych piasków (wtórnie, po wylesieniu w średniowieczu), wydmy 5–10 m.

**Wydmy nadmorskie:** Słowiński PN* (sekcja 3.5): ruchome wydmy Mierzei Łebskiej – Łącka Góra ok. 42 m, Czołpińska Góra ok. 56 m (wartości ogólnie znane, nie potwierdzone w artykule Wikipedii), przemieszczanie 3–10 m/rok; wydmy Mierzei Wiślanej do ok. 49,5 m (Wielbłądzi Garb); Mierzeja Helska do ok. 20 m. Profil wybrzeża wydmowego (od morza): plaża 20–80 m -> wydma przednia (biała, inicjalna; piaskownica, wydmuchrzyca) 3–10 m -> wydma szara (bażyna, kocanki, murawy) -> bór bażynowy nadmorski (sosna zwyczajna, brzoza) na wydmach brunatnych -> zagłębienia międzywydmowe z torfowiskami i jeziorami przybrzeżnymi. Materiał: piasek kwarcowy drobno- i średnioziarnisty.

### 3.5. Wybrzeże: mierzeje, klify, delty, depresje, jeziora przybrzeżne

**Długość wybrzeża:** 775 km (z zalewami), granica morska 501 km (GUS, Wikipedia pl "Geografia Polski"). Typy wybrzeża: mierzejowo-wydmowe (dominujące, ok. 70–80%), klifowe (Wolin, Trzęsacz–Rewal, Jarosławiec, Rozewie–Jastrzębia Góra, Kępa Redłowska/Orłowo, Kępa Swarzewska), deltowe (Żuławy), zalewowo-laguna (Zalew Szczeciński, Wiślany).

**Słowiński Park Narodowy** (Wikipedia pl): pow. 327,44 km² (32 744 ha po 2004), otulina 302,2 km²; jeziora przybrzeżne: **Łebsko 7,1 tys. ha** (71 km², 3. co do wielkości w Polsce), **Gardno 2,5 tys. ha**, Dołgie Wielkie 156 ha, Dołgie Małe 6,2 ha; kulminacja Rowokół 115 m n.p.m. (morena); "80% lasów stanowią bory" (bory bażynowe nadmorskie). Artykuł NIE podaje wysokości Łąckiej Góry ani prędkości wydm. **Wiedza ogólna (do potwierdzenia):** Łącka Góra ok. 42 m n.p.m. (wydma ruchoma, przesuwa się 3–10 m/rok na E), Czołpińska Góra ok. 56 m, Mierzeja Łebska dł. ok. 17 km, pole wydm ruchomych ok. 500 ha; jeziora przybrzeżne płytkie (Łebsko gł. maks. 6,3 m, średnio 1,6 m; Gardno 2,6 m), oddzielone od morza mierzeją o szer. 0,5–2 km.

**Żuławy Wiślane** (Wikipedia pl): delta ok. 1700 km², trójkąt o wysokości ok. 50 km i podstawie ok. 40 km; depresja 450 km² (28%); najniżej Marzęcino -2,2 m (dawniej Raczki Elbląskie -1,8 m); teren 0–5 m n.p.m. – 47%, >5 m – 25%; gleby: mady; sieć kanałów i wałów (Szkarpawa 27 km, Kanał Panieński 32 km); polderyzacja, prawie bezleśne (pola, łąki, wierzby głowiaste wzdłuż rowów). Poziomy wodonośne 15–30 m i 40–80 m.

**Mierzeje – wiedza ogólna, do potwierdzenia:** Mierzeja Helska dł. 34–35 km, szer. od ok. 100–200 m (nasada, Chałupy) do ok. 3 km (Hel), wydmy do ok. 20 m, bory sosnowe; Mierzeja Wiślana dł. ok. 96 km (w Polsce ok. 50 km), szer. 1–2 km, wydmy do 30 m (Krynica Morska: Wielbłądzi Garb ok. 49,5 m – najwyższa wydma stała na mierzejach), od 2022 przekop.

**Klify – wiedza ogólna, do potwierdzenia w kroku 4 (Woliński PN):** Wolin – klif Gosań ok. 93–95 m (najwyższy w Polsce), aktywny, cofa się ok. 0,5–1 m/rok; Trzęsacz (ruiny kościoła, klif ok. 15–20 m); Jastrzębia Góra/Rozewie klif ok. 30–35 m (Rozewie 33 m n.p.m. z latarnią na 83 m); Orłowo (Kępa Redłowska) ok. 40–60 m, gliny zwałowe z piaskami. Materiał klifów: glina zwałowa z głazami, piaski i mułki międzymorenowe; u podnóża plaże żwirowo-kamieniste.

**Plaże:** szerokość typowo 20–80 m, piaski drobno- i średnioziarniste, za plażą wydma przednia (biała) 3–10 m, wydma szara z bażyną i kocankami, dalej bór sosnowy nadmorski (wiedza ogólna).

### 3.6. Wyżyny lessowe i wąwozy

**Kazimierski Park Krajobrazowy** (Wikipedia pl): pow. 149,74 km², otulina 246,44 km²; **gęstość wąwozów lessowych koło Bochotnicy przekracza 10 km/km² – największa w Europie**; miąższość lessu od kilkunastu do 30 m; skarpa doliny Wisły przy ujściu Chodelki do 90 m wysokości. Artykuł nie podaje szerokości Małopolskiego Przełomu Wisły ani udziału lasów. **Wiedza ogólna (do potwierdzenia):** wąwozy lessowe mają strome (60–90°), pionowe ściany, głębokość 5–30 m, szerokość dna 1–10 m, długość 0,2–2 km, rozgałęzione dendrytycznie; dna wąwozów – grądy z lipą, klonem, grabem, leszczyną; wierzchowiny – pola i sady; Korzeniowy Dół (Kazimierz) – dł. ok. 400 m, głębokość do 10 m, z odsłoniętymi korzeniami; Małopolski Przełom Wisły (Zawichost–Puławy) dł. ok. 80 km, dolina szer. 1,5–3 km, wcięta 50–90 m; opoki kredowe (Kazimierz, Janowiec) tworzą skałki i kamieniołomy.

**Pozostałe obszary lessowe Polski (wiedza ogólna):**

| Obszar | Wys. n.p.m. | Miąższość lessu | Gęstość wąwozów | Charakter |
|---|---|---|---|---|
| Płaskowyż Nałęczowski (Wyżyna Lubelska) | 180–230 m | 10–30 m* | >10 km/km²* lokalnie; typowo 2–5 km/km² | najsilniej rozczłonkowany; pola, sady; lasy w wąwozach |
| Wyżyna Sandomierska / Wzgórza Opatowskie | 200–300 m | 10–20 m | 3–8 km/km² (Sandomierz: Wąwóz Królowej Jadwigi – dł. 500 m, gł. 10 m) | pola, sady, winnice |
| Roztocze Zachodnie (Gorajskie) | 280–350 m | 10–20 m | 3–6 km/km² (Szczebrzeszyn – "Piekiełko") | pola, wąwozy zalesione |
| Płaskowyż Proszowicki (Niecka Nidziańska) | 200–300 m | 5–15 m | 1–3 km/km² | czarnoziemy, prawie bezleśny (lasy <5%), doliny Szreniawy, Nidzicy – szerokie, płaskie |
| Wyżyna Miechowska | 250–400 m | 5–15 m | 2–4 km/km² | pola, murawy kserotermiczne na zboczach, kreda pod lessem |
| Płaskowyż Głubczycki (Nizina Śląska) | 230–300 m | 5–15 m | <1 km/km² | najbardziej rolniczy (lasy ok. 5%), łagodnie falisty |
| Pogórze Rzeszowskie, Dynowskie (Karpaty) | 250–400 m | 5–15 m | 1–3 km/km² | pola pasowe, lasy grądowe |
| Wzgórza Trzebnickie | 150–250 m | 5–10 m | 1–2 km/km² | sady, pola |
| Wyżyna Wołyńska (Grzęda Horodelska) | 200–260 m | 5–15 m | 1–2 km/km² | czarnoziemy, bezleśna |

Gleby lessowe: czarnoziemy (Proszowice, Hrubieszów, Sandomierz), gleby płowe i brunatne – najżyźniejsze w Polsce, dlatego krajobraz lessowy jest prawie bezleśny (lesistość 5–15%), z lasami tylko w wąwozach i na stromych zboczach (grądy, buczyny na Roztoczu).

### 3.7. Kras wapienny (Jura) i gipsowy (Niecka Nidziańska)

**Wyżyna Krakowsko-Częstochowska** (Wikipedia pl): długość ok. 80 km (Kraków–Częstochowa), pow. ok. 2615 km², wysokości od ok. 300 m (na E od Częstochowy) do ok. 500 m (część południowa); najwyżej: **Góra Zamkowa (Janowskiego) 515,6 m** w Podzamczu k. Ogrodzieńca, Grodzisko 512,8 m i Wielka Skała 512,8 m (Jerzmanowice), Kapucyn 502 m. Dominują wapienie górnojurajskie (skaliste, płytowe) tworzące płytę, także dolomity i margle; **ok. 1500 jaskiń, z tego ok. 150 dłuższych niż 40 m**: Jaskinia Wierna 1027 m dł. (30 m gł.), Wierzchowska Górna 975 m, Studnisko 337 m dł. i 77,5 m gł. (najgłębsza). Doliny krasowe: Prądnika, Dłubni (liczne źródła i potoki). Pustynia Błędowska – piaski z wydmami. Lasy ok. 20% powierzchni; Ojcowski PN. **Wiedza ogólna (do potwierdzenia):** ostańce (mogoty) wapienne – skałki 10–30 m wysokości (Okiennik Wielki, Maczuga Herkulesa ok. 25 m, Brama Krakowska, Skały Kroczyckie, Podlesickie, Rzędkowickie), tworzą "Szlak Orlich Gniazd"; Dolina Prądnika – dł. ok. 15 km w OPN, wcięta 60–120 m w wierzchowinę, dno 100–300 m szer., ściany skalne do 80 m; Ojcowski PN 21,46 km² (najmniejszy); wierzchowina falista 400–450 m z lessem i pola; zbocza – buczyny, grądy, jaworzyny; murawy kserotermiczne na skałkach; wywierzyska (źródła krasowe), leje, kuesta jurajska od zachodu (próg 50–100 m nad Obniżeniem Górnej Warty); północna część (Wyżyna Częstochowska) bardziej płaska, z lasami (ok. 30–40%) i polami na rędzinach.

**Kras gipsowy Niecki Nidziańskiej (Ponidzie) – wiedza ogólna, do potwierdzenia:** gipsy mioceńskie (kryształy "jaskółcze ogony" do 3 m!) na obszarze Niecki Soleckiej i Garbu Pińczowskiego; formy: leje krasowe (średnica 5–50 m, gł. 2–10 m), doliny krasowe (Skorocice – rezerwat, jaskinia Skorocicka 352 m), wywierzyska, zapadliska, "ślepe doliny", jeziorka krasowe; wysokości 200–300 m, deniwelacje 20–60 m; roślinność stepowa (murawy kserotermiczne, ostnice) na rędzinach gipsowych, solniska (Busko, Solec – wody siarczkowe); Nadnidziański PK; lasy <15%.

**Inne obszary krasowe (wiedza ogólna):** Tatry Zachodnie (kras wysokogórski, 857 jaskiń*), Sudety (Jaskinia Niedźwiedzia w marmurach, Góry Kaczawskie), Wyżyna Wieluńska (jaskinie Szachownica), kras kredowy Polesia i Pagórów Chełmskich (jeziora krasowe), kras Gór Świętokrzyskich (Jaskinia Raj – dewońskie wapienie, dł. 240 m; Chęciny).

### 3.8. Góry Świętokrzyskie

Źródło: Wikipedia pl "Góry Świętokrzyskie". Ciąg pasm długości ok. 70 km od okolic Dobrzeszowa (W) po okolice Opatowa (E), rozciągłość W–E. **Łysica 614 m n.p.m.** (Agata – najwyższy szczyt), Łysa Góra (Święty Krzyż, ok. 595 m – wiedza ogólna) w Paśmie Łysogórskim; Góra Słowiec 433 m (Pasmo Orłowińskie). Pasma: Łysogórskie, Jeleniowskie, Masłowskie, Klonowskie, Bielińskie, Orłowińskie, Zgórskie, Posłowickie, Dymińskie, Chęcińskie, Oblęgorskie. Geologia: trzon z kwarcytowych piaskowców kambryjskich ("najtwardsze i najbardziej zwięzłe skały"), wapienie dewońskie z fauną; budowa fałdowa, orogeneza hercyńska (dolny karbon) – najstarsze góry w Polsce (fałdowanie kaledońskie i hercyńskie). **Gołoborza**: rumowiska skalne z bloków kwarcytów kambryjskich na stokach Łysogór, geneza peryglacjalna (plejstocen, wietrzenie mrozowe w czasie zlodowaceń południowopolskich); Gołoborze im. R. Kobendzy. Puszcza Jodłowa – lasy jodłowe i bukowe, modrzew polski na Chełmowej Górze. **Świętokrzyski PN: 7626 ha + 20 786 ha otuliny** (utworzony 1950, trzeci w Polsce). Klimat: średnia roczna 6–7 °C (o 1–2 °C niższa niż w Warszawie), inwersje – stoki cieplejsze o 5 °C od dolin. Lessy w okolicach Sandomierza (Wąwóz Królowej Jadwigi). **Wiedza ogólna (do potwierdzenia):** pasma mają szer. 1–3 km, grzbiety wąskie, symetryczne, stoki 10–25°; deniwelacje względem otaczających dolin i Kielc (260 m) 200–350 m; gołoborza zajmują pasy o szer. 50–200 m i dł. do 1 km na stokach 15–25°, bloki 0,3–2 m; Pasmo Jeleniowskie (Szczytniak 554 m), Masłowskie (Klonówka 473 m), Chęcińskie (wapienne, kras, kamieniołomy, Jaskinia Raj); Pasmo Klonowskie – piaskowce dewońskie; doliny między pasmami wypełnione łupkami (miękkimi) – rolnicze, 250–300 m n.p.m.

### 3.9. Sudety i Przedgórze Sudeckie

**Charakter ogólny:** góry zrębowe (stare, hercyńskie, odmłodzone tektonicznie w trzeciorzędzie), oddzielone od Przedgórza Sudeckiego wyraźnym **sudeckim uskokiem brzeżnym** (skok 200–400 m; wiedza ogólna). Zróżnicowana litologia: granity (Karkonosze, Strzegom, Strzelin), gnejsy (Góry Sowie, Śnieżnik), piaskowce kredowe (Góry Stołowe), bazalty (Pogórze Kaczawskie), wapienie (Kaczawskie, Krowiarki), zieleńce, łupki. Rzeźba: rozległe płaskie wierzchowiny (powierzchnie zrównania), strome stoki, głębokie doliny, kotliny śródgórskie (Jeleniogórska ok. 300–400 m n.p.m., Kłodzka 280–400 m).

**Karkonosze** (Wikipedia pl "Karkonosze"): dł. ok. 40 km (Przełęcz Szklarska – Przełęcz Lubawska), szer. 8–20 km, pow. ok. 650 km² (Polska 185 km²); **Śnieżka 1603 m** (najwyższa w Sudetach i Czechach), Luční hora 1555, Studniční hora 1554, Wielki Szyszak 1509, Smogornia 1490, Łabski Szczyt 1472, Mały Szyszak 1440; blisko 100 szczytów >1000 m. Granit karkonoski w części zachodniej głównego grzbietu i na Pogórzu Karkonoskim; wschodnia osłona metamorficzna (gnejsy, łupki łyszczykowe, amfibolity) buduje Czarny Grzbiet ze Śnieżką, Kowarski i Lasocki Grzbiet. **Formy:** rozległe powierzchnie zrównania na grzbiecie (Równia pod Śnieżką ~1400 m – torfowisko wysokie subalpejskie 20 ha; torfowiska łącznie 85 ha po stronie polskiej); **6 kotłów polodowcowych po stronie polskiej (północnej)**: Śnieżne Kotły (Mały i Wielki), Czarny Kocioł Jagniątkowski, Kocioł Wielkiego Stawu, Kocioł Małego Stawu, Kocioł Łomniczki; stawy: Wielki Staw, Mały Staw, Śnieżne Stawki; **co najmniej 150 grup skałek granitowych do 25 m wysokości** (Pielgrzymy, Słonecznik); gołoborza, nisze niwalne, wieńce gruzowe (peryglacjał). Asymetria: stok polski (N) stromy, krótki, wysoki; czeski (S) łagodny. **Piętra** (obniżone o kilkaset m względem Tatr): podgórskie do ~500 m, regiel dolny do ~1000 m, regiel górny do ~1250 m (górna granica lasu), kosodrzewina ~1250–1450 m, piętro alpejskie powyżej. Wysokość względna Śnieżki nad Kotliną Jeleniogórską ok. 1200 m (obliczone: 1603 – ~400; wiedza ogólna). Ściany kotłów polodowcowych: Śnieżne Kotły ok. 200–300 m (wiedza ogólna, do potwierdzenia).

**Góry Stołowe – wiedza ogólna, do potwierdzenia:** jedyne w Polsce góry płytowe; poziome ławice piaskowca ciosowego (górna kreda) tworzą trzy poziomy stoliw: ok. 500, 700–800 i 850–919 m; Szczeliniec Wielki 919 m (ściany skalne 10–50 m, labirynt skalny, szczeliny do 100 m dł.), Skalniak 915 m, Błędne Skały (labirynt 850 m n.p.m., korytarze szer. 0,3–1 m); Narożnik, Radkowskie Ściany; na wierzchowinach torfowiska (Wielkie Torfowisko Batorowskie); PN 63 km².

**Góry Izerskie – wiedza ogólna:** granit i gnejs, rozległe zrównania na 1000–1100 m (Hala Izerska 840–880 m z torfowiskami wysokimi, mrozowisko), Wysoka Kopa 1126 m; łagodne, zalesione świerkiem (klęska ekologiczna lat 80.).

**Masyw Śnieżnika – wiedza ogólna:** Śnieżnik 1425 m (gnejsy, łupki), kopulasty, rozłogi; Jaskinia Niedźwiedzia (marmury); Kotlina Kłodzka – 280–400 m n.p.m., rolnicza, otoczona pasmami 700–1000 m (Bystrzyckie, Orlickie, Bardzkie, Złote).

**Przedgórze Sudeckie – wiedza ogólna:** falista równina denudacyjna 200–300 m n.p.m. na skałach krystalicznych przykrytych lessem/gliną, z izolowanymi masywami twardzielowymi: **Ślęża 718 m** (gabro, granit; wys. względna ok. 500 m nad Równiną Wrocławską), Wzgórza Strzegomskie (granit, kamieniołomy), Strzelińskie (Gromnik 393 m), Masyw Ślęży; intensywnie rolnicze (lessy, czarnoziemy okolic Wrocławia), lasy tylko na masywach.

**Pogórze Zachodniosudeckie (Izerskie, Kaczawskie) – wiedza ogólna:** 300–500 m, wzgórza bazaltowe (Ostrzyca 501 m – stożek wulkaniczny), wapienie, zieleńce; pola i lasy liściaste.

**Sudety Wschodnie (Góry Opawskie) – wiedza ogólna:** Biskupia Kopa 889 m; niskie, zalesione.

### 3.10. Karpaty: Pogórze, Beskidy, Bieszczady, Pieniny, Tatry, Podhale

**Tatry** (Wikipedia pl "Tatry"): pow. 785 km² (Polska ok. 175 km² = 22,3%), dł. 57 km w linii prostej (80 km wzdłuż grani), szer. maks. 18,5 km, średnia 15 km. Szczyty: **Gerlach 2655 m** (najwyższy w Tatrach), **Rysy 2499 m** (wierzchołek NW, najwyższy w Polsce), Bystra 2248 m (Tatry Zachodnie), Hawrań 2152 m (Bielskie), Giewont 1895 m. Geologia: trzon krystaliczny – granitoidy (granodioryty, tonality, 290±15 mln lat) w Tatrach Wysokich; gnejsy i migmatyty w Zachodnich; serie osadowe wierchowa i reglowa (wapienie, dolomity, piaskowce, mułowce triasu–kredy) – **w polskiej części skały osadowe zajmują większą powierzchnię niż krystaliczne** (Tatry Zachodnie, regle). **Deniwelacje: do 2 km** (kotliny u podnóża 500–700 m n.p.m. vs. szczyty 2000–2655 m). Formy: doliny U-kształtne (Białej Wody, Roztoki, Rybiego Potoku, Pięciu Stawów Polskich), doliny zawieszone (Dolinka Buczynowa), cyrki lodowcowe, misy glacjalne (Morskie Oko), moreny czołowe/boczne/denne/ablacyjne, lodowce gruzowe, żleby, stożki piargowe (holocen). **Stawy: blisko 200**, Morskie Oko 34,93 ha (największe), Wielki Staw Polski (gł. 79,3 m – wiedza ogólna, najgłębszy w Polsce po Hańczy); Czarny Staw pod Rysami gł. 76,4 m (wiedza ogólna). **Jaskinie: 857 w polskich Tatrach, łącznie >133 km**; Cień Księżyca 30,5 km (najdłuższa), Wielka Śnieżna 824 m deniwelacji (najgłębsza w Polsce), Śnieżna Studnia 763 m. **Piętra roślinne:** pogórze do ~650 m; regiel dolny do 1200–1250 m (pierwotnie buk, jodła); regiel górny do 1500 m (świerk; **górna granica lasu ~1500 m**); kosodrzewina do 1800 m; halne do 2300 m; turniowe >2300 m. Klimat: śnieg do 355 cm (Kasprowy Wierch 1996), opad dobowy do 300 mm, halny do 288 km/h (1968, 500 ha wiatrołomów). Ściany: Kazalnica Mięguszowiecka ~500 m (wiedza ogólna); nachylenia stoków w piętrze turniowym 35–60°, żleby 30–45°, stożki piargowe 30–35° (wartości ogólne geomorfologii wysokogórskiej).

**Podhale i Kotlina Orawsko-Nowotarska – wiedza ogólna, do potwierdzenia:** Obniżenie Orawsko-Podhalańskie (514.1): Kotlina Orawsko-Nowotarska 580–700 m n.p.m., płaska, żwirowe stożki napływowe Czarnego i Białego Dunajca, **torfowiska wysokie (Bór na Czerwonem ok. 50 ha, Puścizna Wielka, Puścizna Rękowiańska – kopuły torfu do 5–7 m miąższości)**; Pogórze Spisko-Gurałtowskie i Pogórze Gubałowskie 800–1100 m (Gubałówka 1126 m) – flisz podhalański, łagodne garby, pola i łąki na wierzchowinach, lasy na stokach; Rów Podtatrzański 800–1000 m.

**Pieniny – wiedza ogólna, do potwierdzenia:** wapienie jurajsko-kredowe (pieniński pas skałkowy), Trzy Korony 982 m, Sokolica 747 m; **przełom Dunajca** dł. ok. 8–9 km (w linii prostej 2,5 km), ściany skalne 300–500 m nad rzeką (Sokolica: 300 m), koryto 40–100 m; ostre grzbiety, białe skałki, murawy naskalne, lasy bukowo-jodłowe, jaworzyny; PN 23,5 km².

**Beskidy Zachodnie (513.4–5) – wiedza ogólna:** góry fliszowe (piaskowce i łupki magurskie, godulskie, istebniańskie), kopulaste, łagodne grzbiety, szerokie doliny; Beskid Śląski (Skrzyczne 1257 m, Barania Góra 1220 m), Beskid Żywiecki (**Babia Góra 1725 m** – najwyższa poza Tatrami, stok N stromy z żlebami i gołoborzami; Pilsko 1557 m), Beskid Mały, Makowski, Wyspowy (izolowane kopuły 800–1100 m nad kotlinami: Mogielica 1170 m), Gorce (Turbacz 1310 m, polany), Beskid Sądecki (Radziejowa 1262 m), Kotlina Żywiecka, Sądecka. Deniwelacje 400–900 m; nachylenia stoków 15–30°; górna granica lasu na Babiej Górze ok. 1360–1400 m (regiel górny świerkowy, kosodrzewina, piętro alpejskie). Regiel dolny: buczyna karpacka (buk, jodła, jawor) – dominujący typ lasu Beskidów.

**Beskidy Środkowe / Beskid Niski (513.7) – wiedza ogólna:** najniższa część Karpat, 500–1000 m (Lackowa 997 m), rusztowe grzbiety, łagodne, przełęcze (Dukielska 500 m), lasy bukowo-jodłowe, opuszczone wsie łemkowskie (łąki, sady). Magurski PN 194 km².

**Bieszczady (Beskidy Lesiste 522.1) – do weryfikacji w kolejnym kroku:** flisz (piaskowce krośnieńskie), rusztowy układ grzbietów NW–SE, **połoniny** (łąki subalpejskie powyżej naturalnie obniżonej górnej granicy lasu ok. 1100–1200 m – buk karłowaty, brak regla górnego świerkowego), Tarnica 1346 m, Halicz 1333, Połonina Wetlińska 1255, Caryńska 1297; głębokie doliny z żwirowymi korytami (San, Solinka, Wetlina); Bieszczadzki PN 292 km²; lasy: buczyna karpacka (buk 60%+), olszyna karpacka w dolinach, "krainy dolin" – po wysiedleniach wtórne łąki i zarośla olszy szarej.

**Pogórza (513.3, 513.6) – wiedza ogólna:** Pogórze Śląskie, Wielickie, Wiśnickie, Rożnowskie, Ciężkowickie, Strzyżowskie, Dynowskie, Przemyskie, Bukowskie: **300–500 m n.p.m. (kulminacje do 550–600 m)**, szerokie, spłaszczone garby o deniwelacjach 100–250 m, nachylenia 5–15°, doliny szer. 0,5–2 km; flisz przykryty lessem i glinami; mozaika pól (wąskie pasowe pola na stokach), sadów, lasów grądowych i bukowych na grzbietach; osuwiska; skałki piaskowcowe (Prządki, Skamieniałe Miasto w Ciężkowicach – ostańce 10–20 m).

### 3.11. Kotliny podkarpackie, Wyżyna Śląska, niziny środkowopolskie, pojezierza, pobrzeża

Uzupełnienie tabeli makroregionów (sekcja 1.3) o rysy krajobrazowe istotne dla generatora. O ile nie zaznaczono inaczej – wiedza ogólna (Kondracki), do potwierdzenia.

**Kotlina Sandomierska (512.4–5):** zapadlisko przedkarpackie wypełnione iłami mioceńskimi, przykryte piaskami czwartorzędowymi; równiny 150–250 m n.p.m., bardzo szerokie doliny Wisły (5–15 km) i Sanu (3–8 km) z madami, łęgami i starorzeczami; Puszcza Sandomierska i Puszcza Solska – bory sosnowe na piaskach i wydmach; Płaskowyż Kolbuszowski – pagórki do 300 m z lasami mieszanymi; Pogórze Rzeszowskie – lessowe, rolnicze. Deniwelacje 10–50 m.

**Kotlina Oświęcimska (512.2):** 220–300 m n.p.m., dolina górnej Wisły ze **stawami rybnymi** (kompleksy stawów z XIII–XVI w. – Dolina Karpia; Zbiornik Goczałkowicki 32 km²), łąki, olsy, Lasy Pszczyńskie; teren płaski z pagórkami żwirowymi.

**Wyżyna Śląska (341.1):** 250–350 m n.p.m., Garb Tarnogórski (wapienie i dolomity triasowe, kras, kopalnie rud), Wyżyna Katowicka – krajobraz antropogeniczny: hałdy górnicze 30–100 m wysokości (stożkowe i stołowe, np. Hałda Szarlota w Rydułtowach ok. 134 m wys. względnej – wiedza ogólna), zapadliska i niecki osiadań wypełnione wodą (zbiorniki antropogeniczne), zwałowiska, piaskownie (Pustynia Błędowska, zbiornik Pogoria, Dzierżno), lasy sosnowe zdegradowane; Góra Św. Anny 400 m – nek bazaltowy. Lesistość woj. śląskiego 32,2%* mimo uprzemysłowienia (Lasy Lublinieckie, Pszczyńskie, Rudzkie).

**Nizina Śląska (318.5):** 100–200 m, Równina Wrocławska i Płaskowyż Głubczycki – czarnoziemy i gleby płowe na lessie, prawie bezleśne, wielkoobszarowe pola; Pradolina Wrocławska – Odra z łęgami (największe kompleksy łęgów w Polsce – Łęgi Odrzańskie), starorzecza; Bory Stobrawskie, Niemodlińskie na piaskach sandrowych; Wał Trzebnicki (do 284 m) jako wyraźna krawędź od północy.

**Wielkopolska (315.5, 315.8, 318.1–2):** wysoczyzny morenowe płaskie i faliste 80–150 m, deniwelacje 5–30 m, mozaika wielkich pól (największe gospodarstwa), pasy moren czołowych (fazy leszczyńska, poznańska, pomorska – kolejne łuki co 50–100 km), rynny (Gopło, Powidzkie), pradoliny (Warszawsko-Berlińska, Toruńsko-Eberswaldzka) z łąkami; Puszcza Notecka (sandr+wydmy), Puszcza Zielonka; Wielkopolski PN (75,8 km²: rynny, ozy, moreny czołowe fazy poznańskiej – "muzeum form polodowcowych"); najniższa lesistość: kujawsko-pomorskie 23,5%*, wielkopolskie 25,8%*.

**Mazowsze (318.6–8):** staroglacjalne, płaskie równiny 80–150 m; dolina Wisły szer. 5–15 km (Dolina Środkowej Wisły – ostatnia "dzika" duża rzeka Europy: roztoki, łachy, kępy z łęgami wierzbowo-topolowymi; Natura 2000); Puszcza Kampinoska (wydmy 30 m* + bagna); Wzniesienia Łódzkie 284 m; Puszcza Kozienicka, Biała, Bolimowska (bory); sady grójeckie; lesistość mazowieckie 23,4%*.

**Podlasie (843.3, 318.9):** staroglacjalne wysoczyzny 100–200 m, ale z bagiennymi dolinami (Biebrza*, Narew) i wielkimi puszczami: **Białowieska** (polska część ok. 620 km², BPN 105 km²; płaska morena denna 135–190 m, grądy 50%, olsy, łęgi, bory, drzewa do 50 m wys.), **Knyszyńska** (ok. 1050 km², moreny kemowe i wzgórza do 200 m, bory świerkowo-sosnowe "borealne", źródliska), Augustowska (1140 km² – sandr, bory, Wigry, Kanał Augustowski); rozdrobnione pola, tradycyjny krajobraz rolniczy; lesistość podlaskie 31,2%*.

**Pojezierze Mazurskie (842.8):** wysoczyzny morenowe 120–200 m, Wzgórza Szeskie 309 m, Kraina Wielkich Jezior Mazurskich – Śniardwy (113,8 km²), Mamry (104 km²), kanały łączące jeziora, poziom wody ok. 116 m n.p.m., jeziora płytkie (Śniardwy 23 m) i rynnowe głębokie (Mamry 44 m, Ełckie 56 m); Puszcza Piska (sandr Równiny Mazurskiej, bory sosnowe), Puszcza Borecka (grądy, świerk), Mazurski PK; lesistość warmińsko-mazurskie 31,8%*, wody ok. 6% (najwięcej w kraju).

**Pojezierze Suwalskie (842.7):** najzimniejszy region ("polski biegun zimna"), największe deniwelacje na Niżu (do 100 m na 1 km: Zagłębienie Szeszupy 200 m gł. względem otaczających wzgórz – wiedza ogólna), Hańcza 108,5 m*, Góra Cisowa 256*, Góra Rowelska 298 m, ozy, kemy, głazowiska (rezerwat Bachanowo, Głazowisko Łopuchowskie), 60% pól*, 24% lasów*, 10% wód*; Puszcza Romincka (świerkowa).

**Pojezierze Pomorskie (314.x):** najsilniej urzeźbione – Pojezierze Kaszubskie (Wieżyca 328,6 m*, deniwelacje 100–150 m, rynny Jezior Raduńskich, buczyny), Pojezierze Drawskie (Drawsko 79,7 m gł.), Bytowskie; wielkie sandry z borami (Bory Tucholskie – ok. 3000 km² lasów, Puszcza Drawska, Goleniowska, Wkrzańska); lesistość pomorskie 36,5%*, zachodniopomorskie 35,8%*.

**Pojezierze Wielkopolskie i Lubuskie (315.4–5):** łagodniejsze, 70–150 m (Lubuskie do 227), Ziemia Lubuska – najbardziej lesiste województwo (**lubuskie 49,4%***: Puszcza Rzepińska, Notecka, Bory Zielonogórskie, Lubuskie – sosna na piaskach), rynny (Łagów), Lubuski Przełom Odry, Park Narodowy Ujście Warty (80,7 km² – rozlewiska, łąki zalewowe, ptaki).

**Pobrzeża (313.x):** wąski pas 10–40 km: wybrzeże wydmowo-mierzejowe i klifowe, równiny morenowe 0–60 m, jeziora przybrzeżne, Żuławy*; Zalew Szczeciński (ok. 900 km² z częścią niemiecką, gł. 4–8 m) i Wiślany (ok. 840 km², w Polsce 328 km², gł. 2–5 m); Woliński PN* (klify 95 m, moreny 75% pow. lądowej, Grzywacz 115,9 m, sosna 68%, buk 23%, dąb 7%, delta wsteczna Świny – 44 wyspy bagienne w Zalewie).

## 4. Hydrografia

### 4.1. Sieć rzeczna i główne rzeki

**Zlewiska** (Wikipedia pl "Geografia Polski"): 99,7% powierzchni Polski należy do zlewiska Bałtyku (dorzecze Wisły ok. 54% kraju, Odry ok. 34%, rzeki Przymorza ok. 10%), reszta – Morze Czarne (Orawa, Strwiąż – 0,2%) i Północne (Izera, Orlica – 0,1%).

**Główne rzeki** (długości: Wikipedia pl "Geografia Polski", "Wisła"; szerokości i spadki: patrz sekcja 3.2 – niepotwierdzone):

| Rzeka | Długość (km) | Dorzecze (km²) | Źródło (m n.p.m.) | Ujście | Średni przepływ (m³/s) | Uwagi |
|---|---|---|---|---|---|---|
| Wisła | 1022* (GUS 2010; d. 1047) | 194 424* | 1107* (Czarna Wisełka) | Zatoka Gdańska (Przekop) | 1046* (ujście) | spadek średni 1,04‰*; przełomy: Małopolski, Fordoński |
| Odra | 854 (w Polsce 741,9*) | ok. 119 000 (wiedza ogólna) | 634 (Czechy, Góry Odrzańskie) | Zalew Szczeciński | ok. 570 (wiedza ogólna) | uregulowana, skanalizowana w górnym biegu |
| Warta | 808* | ok. 54 500 | 380 (Kromołów, Jura) | Odra (Kostrzyn) | ok. 215 | największy dopływ Odry; pradoliny |
| Bug | 772 (w Polsce 587*) | ok. 39 400 (w Polsce) | 310 (Ukraina) | Narew/Zalew Zegrzyński | ok. 160 | graniczny, naturalny, meandry |
| Narew | 484 | ok. 75 000 (z Bugiem) | 159 (Białoruś) | Wisła (Nowy Dwór Maz.) | ok. 330 (z Bugiem) | anastomozy, bagna |
| San | 443 | ok. 16 900 | 925 (Bieszczady) | Wisła | ok. 130 | żwirowy w górnym biegu; Solina |
| Noteć | 388 | ok. 17 300 | 100 (Kujawy) | Warta | ok. 75 | pradolinna, skanalizowana |
| Pilica | 319 | ok. 9300 | 350 (Jura) | Wisła | ok. 45 | Sulejów |
| Wieprz | 303 | ok. 10 400 | 260 (Roztocze) | Wisła (Dęblin) | ok. 35 | meandry, łąki |
| Dunajec | 247 | ok. 6800 | 1500 (Tatry, jako Biały Dunajec) | Wisła | ok. 85 | przełom Pieniński, Czorsztyn |
| Nysa Łużycka | 252 | ok. 4300 | 750 (Góry Izerskie, Czechy) | Odra | ok. 30 | graniczna |
| Bóbr | 272 | ok. 5900 | 780 (Karkonosze/Rudawy) | Odra | ok. 40 | zapory |
| Drwęca | 207 | ok. 5300 | 190 (Garb Lubawski) | Wisła | ok. 30 | rezerwat ichtiologiczny |
| Łeba, Słupia, Rega, Parsęta (rzeki Przymorza) | 100–170 | 1000–3200 | 100–250 | Bałtyk / jeziora przybrzeżne | 10–30 | rzeki "pstrągowe", szybkie, żwirowe, doliny wcięte w moreny |

Wartości bez gwiazdki: wiedza ogólna (Wikipedia/IMGW), zaokrąglone – do potwierdzenia.

**Gęstość sieci rzecznej – wiedza ogólna, NIE POTWIERDZONO w tym badaniu:** średnio dla Polski ok. 0,5–1,0 km/km² (dla cieków stałych; z rowami melioracyjnymi 1,5–2 km/km²); największa w Karpatach i Sudetach (1,5–2,5 km/km² – gęste sieci potoków o spadkach 20–100‰), na pojezierzach mniejsza (0,3–0,6 km/km² – odpływ przez jeziora, zlewnie bezodpływowe), na wyżynach lessowych i wapiennych mała (0,2–0,5 km/km² – kras, infiltracja; suche doliny). Typowy potok górski: szer. 2–10 m, spadek 20–100‰ (Tatry >100‰), kamieniste koryto z progami; potok wyżynny/nizinny: szer. 1–5 m, spadek 1–5‰; rzeka średnia (Drwęca, Wieprz, Pilica): szer. 20–60 m, spadek 0,3–1‰, meandry o promieniu 50–200 m; wielka rzeka nizinna: patrz sekcja 3.2.

**Reżim:** rzeki nizinne – wezbrania roztopowe (III–IV), niżówki letnie; górskie – wezbrania letnie (VI–VII, deszcze nawalne) i roztopowe; powodzie: 1997 (Odra), 2010 (Wisła), 2024 (Nysa Kłodzka, Sudety – wiedza ogólna).

### 4.2. Jeziora

Źródło: Wikipedia pl "Jeziora w Polsce" (katalog A. Choińskiego), "Suwalski Park Krajobrazowy", "Słowiński Park Narodowy", "Tatry".

| Parametr | Wartość |
|---|---|
| Liczba jezior >1 ha | **7081** (Choiński); w 1954 r. 9296 (ubyło 2215 najmniejszych – zanik, zarastanie, melioracje) |
| Łączna powierzchnia jezior >1 ha | 2813,77 km² |
| Jeziorność Polski | 0,9% (Szwecja 8,5%, Kanada 7,6%) |
| Jeziora >50 ha | ponad 1000 |
| Rozmieszczenie | zdecydowana większość w strefie młodoglacjalnej: Pojezierze Pomorskie, Mazurskie, Wielkopolskie, Suwalskie; jeziorność lokalnie 5–15% (Kraina Wielkich Jezior Mazurskich, Pojezierze Kaszubskie – wiedza ogólna) |
| Typy | polodowcowe (rynnowe, morenowe, wytopiskowe, oczka) – ogromna większość, wiek 11–12 tys. lat; przybrzeżne (Łebsko 71 km², Gardno 25 km², Sarbsko, Wicko, Jamno, Bukowo); krasowe (Polesie Zachodnie – Pojezierze Łęczyńsko-Włodawskie); cyrkowe/górskie (Tatry: ~200 stawów, Morskie Oko 34,93 ha; Karkonosze: Wielki i Mały Staw); starorzecza w dolinach; deltowe (Druzno, Dąbie) |
| Największe (wiedza ogólna, do potwierdzenia) | Śniardwy 113,8 km² (gł. 23,4 m, morenowe), Mamry 104,4 km² (gł. 43,8 m), Łebsko 71,4 km², Dąbie 56 km², Miedwie 35 km² (gł. 43,8 m), Jeziorak 32 km² (rynnowe, dł. 27 km – najdłuższe), Niegocin 26 km², Gardno 24,7 km², Jamno 22,4 km², Wigry 21,7 km² (gł. 73 m), Gopło 21,5 km² (rynnowe), Drawsko 18 km² (gł. 79,7 m) |
| Najgłębsze | **Hańcza 108,5 m** (rynnowe, Pojezierze Wschodniosuwalskie; dł. 4,5 km, szer. 1185 m) – potwierdzone; Drawsko 79,7 m, Wielki Staw (Tatry) 79,3 m, Wigry 73 m, Czarny Staw pod Rysami 76,4 m, Wdzydze 68 m, Morskie Oko 50,8 m – wiedza ogólna |
| Typowe rozmiary jezior pojeziernych | rynnowe: dł. 2–10 km, szer. 0,2–1 km, gł. 20–60 m; morenowe: 1–10 km średnicy, gł. 5–30 m; oczka: 0,01–1 ha, gł. 1–5 m (wartości ogólne, do potwierdzenia) |

### 4.3. Zbiorniki zaporowe i źródła

**Zbiorniki zaporowe – wiedza ogólna (Wikipedia pl "Sztuczne zbiorniki wodne w Polsce"; NIE POTWIERDZONO w tym badaniu – budżet wyczerpany):**

| Zbiornik | Rzeka | Pow. (km²) | Pojemność (mln m³) | Gł. maks. (m) | Typ krajobrazu |
|---|---|---|---|---|---|
| Solina | San | ok. 22 | ok. 472 (największy pojemnościowo) | ok. 60 | górski, fiordowy, zalesione stoki Bieszczadów |
| Włocławek | Wisła | ok. 70 (największy powierzchniowo) | ok. 370 | ok. 15 | nizinny, dolina Wisły |
| Jeziorsko | Warta | ok. 42 | ok. 200 | ok. 10 | nizinny, ptasi (rozlewiska) |
| Goczałkowice | Wisła | ok. 32 | ok. 165 | ok. 13 | Kotlina Oświęcimska |
| Czorsztyn–Niedzica | Dunajec | ok. 12 | ok. 230 | ok. 50 | Pieniny/Podhale |
| Siemianówka | Narew | ok. 32 | ok. 80 | ok. 7 | Podlasie, płytki |
| Sulejów | Pilica | ok. 22 | ok. 75 | ok. 11 | nizinny |
| Otmuchów, Nysa | Nysa Kłodzka | 20, 20 | 130, 125 | – | Przedgórze Sudeckie |
| Dobczyce | Raba | ok. 10 | ok. 125 | – | Pogórze |
| Rożnów, Czchów | Dunajec | 16, 3 | 160, 12 | – | Pogórze Rożnowskie |
| Turawa | Mała Panew | ok. 20 | ok. 100 | – | Nizina Śląska |
| Zalew Zegrzyński | Narew/Bug | ok. 30 | ok. 90 | – | Mazowsze |
| Mietków, Słup, Pilchowice | Bystrzyca, Nysa Szalona, Bóbr | 9, 5, 2 | 70, 38, 50 | – | Sudety/Przedgórze (Pilchowice – zapora kamienna 1912, kanion) |

Łącznie w Polsce ok. 100 większych zbiorników zaporowych; pojemność całkowita ok. 4 mld m³ (ok. 6% odpływu rocznego – niska retencja). Do tego tysiące stawów rybnych (Milicz ok. 77 km², Dolina Karpia, Stawy Przemkowskie) i zbiorników poeksploatacyjnych (Pogoria, Dzierżno, jeziora pokopalniane w Łuku Mużakowa i koło Konina).

**Źródła – wiedza ogólna:** największe wydajności – źródła krasowe (wywierzyska) Tatr (Lodowe Źródło ok. 1000 l/s, Olczyskie) i Jury (Zygmunta i Elżbiety w Złotym Potoku), źródła szczelinowe Roztocza i Wyżyny Lubelskiej (Zwierzyniec, Wąwolnica), źródliska Puszczy Knyszyńskiej i Kaszub (nisze źródliskowe w zboczach dolin morenowych – łęgi źródliskowe), wody mineralne Karpat (Krynica, Szczawnica, Iwonicz) i Sudetów (Kudowa, Lądek, Cieplice – termalne 87 °C w Cieplicach na głębokości), solanki (Ciechocinek, Kołobrzeg), siarczkowe (Busko, Solec).

## 5. Piętra klimatyczno-roślinne w górach (w kontekście rzeźby)

Granice pięter są zależne od masywności i klimatu gór – w Sudetach leżą "nawet o kilkaset metrów niżej niż w innych górach wysokich" (Wikipedia pl "Karkonosze"). W Bieszczadach brak regla górnego – nad buczyną od razu połoniny (wiedza ogólna, do potwierdzenia).

| Piętro | Tatry (Wikipedia pl) | Karkonosze (Wikipedia pl) | Babia Góra (wiedza ogólna) | Bieszczady (wiedza ogólna) | Rzeźba / podłoże typowe | Roślinność |
|---|---|---|---|---|---|---|
| Pogórze / podgórskie | do ~650 m | do ~500 m | do ~700 m | do ~600 m | garby, doliny, stożki, mady/gliny | grąd, łęg, buczyna z dębem; pola, łąki |
| Regiel dolny | 650–1200/1250 m | 500–1000 m | 700–1150 m | 600–1150 m | strome stoki, gleby brunatne na fliszu/granicie | buczyna karpacka/sudecka (buk, jodła, jawor, świerk) |
| Regiel górny | 1250–1500 m | 1000–1250 m | 1150–1390 m | brak (w Bieszczadach) | stoki, żleby, gołoborza | bór świerkowy górnoreglowy |
| Kosodrzewina (subalpejskie) | 1500–1800 m | 1250–1450 m | 1390–1650 m | połoniny 1150–1346 m (łąki, borówczyska, buk karłowaty) | zrównania, kotły, piargi, torfowiska (Równia pod Śnieżką) | kosodrzewina, jarzębina, ziołorośla |
| Halne (alpejskie) | 1800–2300 m | >1450 m (tylko szczyty) | 1650–1725 m | – | granie, stoki gruzowe, wieńce gruzowe | murawy halne, sit skucina |
| Turniowe (subniwalne) | >2300 m | – | – | – | skalne turnie, żleby, piargi | porosty, mchy, ~120 gat. roślin naczyniowych |

**Implikacja:** w generatorze piętra należy wyznaczać z wysokości bezwzględnej I typu masywu (parametr "obniżenia granic": Tatry 0 m, Babia Góra ok. -100 m, Karkonosze ok. -250 m, Bieszczady: regiel górny wyłączony, połoniny od ~1150 m).

## 6. Użytkowanie ziemi według województw (GUS)

**Polska ogółem** (Wikipedia pl "Geografia Polski", dane GUS/EGiB): grunty orne 59,3% (w ujęciu użytków rolnych ogółem), grunty leśne 30,4%, zabudowane i zurbanizowane 6,0%, wody 2,5%, nieużytki 1,5%. Wg innego zestawienia GUS 2020 (wynik wyszukiwania): grunty orne 40,8%, łąki i pastwiska 11,4%, lasy 30,2%, sady 7,8% (wartość podejrzana – prawdopodobnie błąd źródła; sady w Polsce to ok. 1%), zabudowa 6%, wody 2,1%, nieużytki i inne 1,7%. **Lasy 31.12.2024 (GUS "Leśnictwo"): grunty leśne 9488,8 tys. ha, lasy 9289,0 tys. ha, lesistość 29,6%** (wynik wyszukiwania; do potwierdzenia w Roczniku Statystycznym Leśnictwa 2024: https://stat.gov.pl/download/gfx/portalinformacyjny/pl/defaultaktualnosci/5515/13/7/1/rocznik_statystyczny_lesnictwa_2024_0612.pdf). Wikipedia pl "Lasy w Polsce": lesistość 29,5% (2016, 9,23 mln ha), 29,6% (2021, 9,265 mln ha); 1946 – 20,8%; cel 33% w 2050; siedliska borowe 51%, lasowe 49% (w tym olsy i łęgi 3,8%); skład gatunkowy (2014): sosna 58,5%, brzoza 7,5%, dąb 7,5%, świerk 6,4%, buk 5,8%, olsza 5,4%, pozostałe liściaste 4,7%, pozostałe iglaste 4,2%; własność: publiczne 82% (LP 78%, PN 2%), prywatne 18% (najwięcej prywatnych: małopolskie 43,3%, mazowieckie 42,9%, lubelskie 39,6%; najmniej: lubuskie 1,2%, zachodniopomorskie 1,6%, dolnośląskie 2,6%).

**Lesistość według województw** (dane Lasy Państwowe/GUS za 2024, cytowane w zestawieniu z 2025 r. – źródło wtórne; kolejność i wartości zgodne z Rocznikiem Statystycznym Leśnictwa; do ostatecznego potwierdzenia w PDF GUS):

| Lp. | Województwo | Lesistość 2024 (%) | Grunty orne (% pow. woj.) | Łąki i pastwiska (%) | Wody (%) | Zabudowa (%) |
|---|---|---|---|---|---|---|
| 1 | lubuskie | **49,4** | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 2 | podkarpackie | 38,3 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 3 | pomorskie | 36,5 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 4 | zachodniopomorskie | 35,8 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 5 | śląskie | 32,2 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 6 | warmińsko-mazurskie | 31,8 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 7 | podlaskie | 31,2 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 8 | dolnośląskie | 29,8 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 9 | małopolskie | 28,6 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 10 | świętokrzyskie | 28,3 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 11 | opolskie | 26,7 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 12 | wielkopolskie | 25,8 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 13 | kujawsko-pomorskie | 23,5 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 14 | lubelskie | 23,5 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 15 | mazowieckie | 23,4 | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| 16 | łódzkie | **21,4** | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO | NIE ZBADANO |
| – | **Polska** | **29,6** | ok. 36–40 | ok. 10–11 | 2,1–2,5 | 6,0 |

Kolumny gruntów ornych, łąk, wód i zabudowy wg województw wymagają pobrania z BDL GUS (kategoria "Powierzchnia geodezyjna kraju według kierunków wykorzystania", https://bdl.stat.gov.pl/bdl/metadane/cechy/1874) – nie udało się w budżecie tego badania. Orientacyjnie (wiedza ogólna): najwyższy udział gruntów ornych – kujawsko-pomorskie, wielkopolskie, opolskie, lubelskie, łódzkie, mazowieckie (50–60%); najwyższy udział łąk i pastwisk – podlaskie, warmińsko-mazurskie, małopolskie, podkarpackie (15–25%); najwyższy udział wód – warmińsko-mazurskie (ok. 6%), pomorskie, zachodniopomorskie (ok. 4–5%); zabudowa – śląskie (ok. 13%), małopolskie.

## 7. Charakterystyka 16 województw – dominujące krajobrazy, miejsca, parki

Lesistość (*) – z tabeli w sekcji 6 (LP/GUS 2024). Parki narodowe (23 w Polsce) i wybrane parki krajobrazowe (PK; łącznie w Polsce ok. 125) – wiedza ogólna, stabilna; powierzchnie PN oznaczone * pochodzą z artykułów Wikipedii zweryfikowanych w tym badaniu.

| Województwo | Lesistość* | Dominujące typy krajobrazu (kody makroregionów) | Charakterystyczne miejsca | Parki narodowe | Wybrane parki krajobrazowe |
|---|---|---|---|---|---|
| dolnośląskie | 29,8% | Sudety (332.3–6), Przedgórze (332.1), Nizina Śląska (318.5), Wał Trzebnicki, Bory Dolnośląskie (317.7), Obniżenie Milickie | Śnieżka 1603*, Szczeliniec 919, Ślęża 718, Kotlina Kłodzka, Stawy Milickie, Łęgi Odrzańskie, Bory Dolnośląskie (wydmy, wrzosowiska) | Karkonoski (59,5 km²), Gór Stołowych (63 km²) | Ślężański, Dolina Baryczy (największy PK w Polsce, 870 km²), Śnieżnicki, Książański, Rudawski, Chełmy, Dolina Bobru, Przemkowski |
| kujawsko-pomorskie | 23,5% | Pojezierze Chełmińsko-Dobrzyńskie (315.1), Wielkopolskie/Kujawy (315.5), Dolina Dolnej Wisły (314.8), Pradolina Toruńska (315.3), Bory Tucholskie (314.7) | Dolina Dolnej Wisły (skarpy 50–70 m), Gopło, Jeziora Brodnickie, drumliny Zbójna, Puszcza Bydgoska, czarne ziemie Kujaw | – | Brodnicki, Gostynińsko-Włocławski, Tucholski, Wdecki, Chełmiński i Nadwiślański, Górznieńsko-Lidzbarski, Krajeński, Nadgoplański |
| lubelskie | 23,5% | Wyżyna Lubelska (343.1), Roztocze (343.2), Polesie (845.1, 845.3), Wyżyna Wołyńska (851), Nizina Południowopodlaska, Kotlina Sandomierska (Równina Biłgorajska) | wąwozy lessowe Kazimierza (>10 km/km²*), Małopolski Przełom Wisły, Roztocze, Puszcza Solska, jeziora Polesia, Pojezierze Łęczyńsko-Włodawskie, Bug | Roztoczański (84,8 km²), Poleski (97,6 km²) | Kazimierski (149,7 km²*), Krasnobrodzki, Szczebrzeszyński, Nadwieprzański, Sobiborski, Chełmski, Strzelecki, Wrzelowiecki, Kozłowiecki |
| lubuskie | **49,4%** (najwyższa) | Pojezierze Lubuskie (315.4), Pradolina Toruńsko-Eberswaldzka (Kotlina Gorzowska), Wzniesienia Zielonogórskie, Obniżenie Dolnołużyckie, Wzniesienia Łużyckie | Puszcza Notecka (wydmy), Puszcza Rzepińska, Łuk Mużakowa (geopark), Lubuski Przełom Odry, Ujście Warty, Bory Zielonogórskie, Pojezierze Łagowskie | Ujście Warty (80,7 km²) | Łagowsko-Sulęciński, Gryżyński, Pszczewski, Barlinecko-Gorzowski, Krzesiński, Przemęcki, Łuk Mużakowa |
| łódzkie | **21,4%** (najniższa) | Wzniesienia Południowomazowieckie (318.8: Wzniesienia Łódzkie 284 m, Wysoczyzna Bełchatowska), Nizina Południowowielkopolska (318.1–2), Wyżyna Przedborska (342.1) | odkrywka i hałda Bełchatów (Góra Kamieńsk 386 m), Jeziorsko, Sulejów, Pilica, Warta (Załęczański Łuk – kras), Puszcza Bolimowska, Lasy Spalskie | – | Załęczański, Bolimowski, Przedborski, Sulejowski, Spalski, Wzniesień Łódzkich, Międzyrzecza Warty i Widawki |
| małopolskie | 28,6% | Tatry (514.5), Podhale/Orawa/Pieniny (514.1), Beskidy Zachodnie (513.4–5), Pogórza (513.3, 513.6), Jura (341.3), Niecka Nidziańska (342.2 – Płaskowyż Proszowicki), Brama Krakowska, Kotlina Oświęcimska, Kotlina Sandomierska | Rysy 2499*, Morskie Oko*, Babia Góra 1725, Gorce, Pieniny/przełom Dunajca, Ojców/Dolina Prądnika, torfowiska Orawy, Kotlina Sądecka, Pustynia Błędowska (część) | Tatrzański (211,6 km²), Babiogórski (33,9 km²), Gorczański (70,3 km²), Pieniński (23,5 km²), Ojcowski (21,5 km²), Magurski (część) | Popradzki, Ciężkowicko-Rożnowski, Wiśnicko-Lipnicki, Dłubniański, Orlich Gniazd (część), Tenczyński, Bielańsko-Tyniecki, Rudniański, Dolinki Krakowskie, Pasma Brzanki |
| mazowieckie | 23,4% | Nizina Środkowomazowiecka (318.7), Północnomazowiecka (318.6), Wzniesienia Południowomazowieckie (318.8), Nizina Południowopodlaska (318.9), Kotlina Płocka | Puszcza Kampinoska (wydmy 30 m*), Dolina Środkowej Wisły (kępy, łachy), Puszcza Kurpiowska, Kozienicka, Biała, Bolimowska, Podlaski Przełom Bugu, Zalew Zegrzyński, sady grójeckie | Kampinoski (385,4 km²*) | Mazowiecki, Chojnowski, Kozienicki, Nadbużański, Brudzeński, Gostynińsko-Włocławski (część), Górznieńsko-Lidzbarski (część), Bolimowski (część), Podlaski Przełom Bugu |
| opolskie | 26,7% | Nizina Śląska (318.5: Równina Opolska, Niemodlińska, Płaskowyż Głubczycki), Wyżyna Śląska (341.1: Chełm – Góra Św. Anny), Sudety Wschodnie (Góry Opawskie 889), Przedgórze Sudeckie (Przedgórze Paczkowskie) | Góra Św. Anny (nek bazaltowy 400 m), Bory Stobrawskie i Niemodlińskie, Płaskowyż Głubczycki (lessy, bezleśny), Turawa, Otmuchów/Nysa, Odra | – | Góra Św. Anny, Stobrawski, Gór Opawskich |
| podkarpackie | 38,3% | Bieszczady (522.1), Beskid Niski (513.7), Pogórza (513.6: Dynowskie, Przemyskie, Strzyżowskie), Kotlina Sandomierska (512.4–5), Płaskowyż Sańsko-Dniestrzański (521.1) | Tarnica 1346*, połoniny*, Solina, Puszcza Sandomierska, dolina Sanu, Doły Jasielsko-Sanockie, Pogórze Przemyskie ("Turnicki" – projekt PN), Wisłok, Prządki | Bieszczadzki (292 km²), Magurski (194 km²) | Ciśniańsko-Wetliński, Doliny Sanu, Gór Słonnych, Pogórza Przemyskiego, Jaśliski, Czarnorzecko-Strzyżowski, Pasma Brzanki (część), Puszczy Solskiej (część), Lasy Janowskie (część) |
| podlaskie | 31,2% | Nizina Północnopodlaska (843.3), Pojezierze Litewskie/Suwalskie (842.7), Pojezierze Mazurskie (Ełckie – część), Nizina Południowopodlaska (część) | Puszcza Białowieska, Knyszyńska, Augustowska, Biebrza (592 km² PN*), Narew (anastomozy), Hańcza 108,5 m*, Wigry, Wzgórza Sokólskie, Kanał Augustowski, Bug | Białowieski (105 km²), Biebrzański (592,2 km²*), Narwiański (68 km²), Wigierski (150 km²) | Suwalski (63,4 km²*), Puszczy Knyszyńskiej, Łomżyński PK Doliny Narwi |
| pomorskie | 36,5% | Pobrzeże Gdańskie (313.5: Żuławy, Mierzeje, Kępy), Pobrzeże Koszalińskie (313.4: Słowińskie), Pojezierze Wschodniopomorskie (314.5: Kaszubskie), Południowopomorskie (314.6–7: Bory Tucholskie), Dolina Dolnej Wisły | Wieżyca 328,6*, Żuławy (-2,2 m*), Hel, Mierzeja Wiślana, klify Orłowa/Rozewia, wydmy ruchome Łeby*, Łebsko*, Bory Tucholskie, Wdzydze, Jeziora Raduńskie | Słowiński (327,4 km²*), Bory Tucholskie (46,1 km²) | Kaszubski, Trójmiejski, Nadmorski, Wdzydzki, Zaborski, Tucholski, Mierzei Wiślanej, Doliny Słupi, Wdecki (część) |
| śląskie | 32,2% | Wyżyna Śląska (341.1), Wyżyna Woźnicko-Wieluńska (341.2), Jura (341.3 – Częstochowska), Beskid Śląski, Żywiecki, Mały (513.4–5), Pogórze Śląskie, Kotlina Oświęcimska (512.2), Kotlina Ostrawska | aglomeracja GOP z hałdami, Pustynia Błędowska, Góra Zamkowa 515,6* (Ogrodzieniec), Skrzyczne 1257, Pilsko 1557, Barania Góra (źródła Wisły 1107*), Goczałkowice, Dolina Karpia, Lasy Lublinieckie | – | Orlich Gniazd (część), Beskidu Śląskiego, Żywiecki, Beskidu Małego, Cysterskie Kompozycje Krajobrazowe Rud Wielkich, Załęczański (część), Stawki, Lasy nad Górną Liswartą |
| świętokrzyskie | 28,3% | Wyżyna Kielecka (342.3: Góry Świętokrzyskie 614*), Niecka Nidziańska (342.2: Ponidzie – kras gipsowy), Wyżyna Przedborska (342.1), Wyżyna Sandomierska (lessy) | Łysica*, gołoborza*, Puszcza Jodłowa*, Ponidzie (gipsy, stepy), Sandomierz (wąwozy lessowe), Chęciny (kras, Jaskinia Raj), Krzemionki (neolityczne kopalnie krzemienia) | Świętokrzyski (76,3 km²*) | Nadnidziański, Szaniecki, Kozubowski, Chęcińsko-Kielecki, Suchedniowsko-Oblęgorski, Sieradowicki, Cisowsko-Orłowiński, Jeleniowski, Przedborski (część) |
| warmińsko-mazurskie | 31,8% | Pojezierze Mazurskie (842.8), Pojezierze Iławskie (314.9), Nizina Staropruska (841.5), Pobrzeże Gdańskie (Wysoczyzna Elbląska, Żuławy – część), Pojezierze Chełmińsko-Dobrzyńskie (Garb Lubawski 312) | Śniardwy, Mamry, Kraina Wielkich Jezior, Jeziorak, Puszcza Piska, Borecka, Napiwodzko-Ramucka, Wzgórza Szeskie 309, Wysoczyzna Elbląska (klify nad Zalewem), Kanał Elbląski, Zalew Wiślany | – (najwięcej wód: ok. 6% pow.) | Mazurski (536 km²), Puszczy Rominckiej, Wysoczyzny Elbląskiej, Pojezierza Iławskiego, Welski, Brodnicki (część), Górznieńsko-Lidzbarski (część) |
| wielkopolskie | 25,8% | Pojezierze Wielkopolskie (315.5), Leszczyńskie (315.8), Nizina Południowowielkopolska (318.1–2), Pradoliny (315.3, 315.6), Pojezierze Południowopomorskie (Krajeńskie – część) | Puszcza Notecka, Zielonka, Wielkopolski PN (formy polodowcowe), Gopło, Powidzkie, Jeziorsko, Pradolina Warszawsko-Berlińska, Dolina Baryczy (część), rynna Obry, odkrywki Konin | Wielkopolski (75,8 km²) | Sierakowski, Puszcza Zielonka, Rogaliński (dęby rogalińskie), Promno, Lednicki, Nadwarciański, Żerkowsko-Czeszewski, Powidzki, Przemęcki (część), Dolina Baryczy (część), Nadgoplański |
| zachodniopomorskie | 35,8% | Pobrzeże Szczecińskie (313.2–3), Koszalińskie (313.4), Pojezierze Zachodniopomorskie (314.4), Południowopomorskie (314.6–7), Pojezierze Myśliborskie | klify Wolina 95 m*, Zalew Szczeciński, jez. Dąbie, Miedwie, Puszcza Bukowa, Goleniowska, Wkrzańska, Drawska, Pojezierze Drawskie (Drawsko 79,7 m), Ińskie, wydmy i mierzeje Kołobrzeg–Darłowo, Jamno, Bukowo | Woliński (109,4 km²*), Drawieński (114,4 km²) | Szczeciński PK "Puszcza Bukowa", Iński, Drawski, Cedyński, Barlinecko-Gorzowski (część), Dolina Dolnej Odry, Ujście Warty (część) |

## 8. Tabela syntetyczna: typ krajobrazu -> parametry -> podłoże -> roślinność -> analog bloków Minecraft

Parametry liczbowe: syntetyczne (zweryfikowane wartości oznaczone *; pozostałe – wiedza ogólna/podręcznikowa, do weryfikacji na NMT). Siedliskowe typy lasu (STL) wg klasyfikacji Lasów Państwowych: Bs – bór suchy, Bśw – bór świeży, Bw – bór wilgotny, Bb – bór bagienny, BMśw – bór mieszany świeży, LMśw – las mieszany świeży, Lśw – las świeży, Lw – las wilgotny, Ol – ols, OlJ – ols jesionowy, Lł – las łęgowy, LMwyż/Lwyż – wyżynne, BG/BMG/LMG/LG – górskie, BWG – bór wysokogórski. "Analog bloków" – propozycja mapowania na bloki vanilla + bloki moda (nowe bloki oznaczone [mod]).

| Typ krajobrazu | Wys. bezwzgl. (m) | Deniwelacje (m) | Nachylenia | Typowe formy i wymiary | Podłoże | Szata roślinna / STL | Analog bloków Minecraft |
|---|---|---|---|---|---|---|---|
| **Wybrzeże wydmowo-mierzejowe** | 0–20 (wydmy do 42–56) | 5–40 | 5–33° (wydmy) | plaża 20–80 m; wydma przednia 3–10 m; wydmy ruchome do 42 m*, przesuw 3–10 m/rok; mierzeja szer. 0,1–3 km | piasek kwarcowy | psammofity, bażyna, bór bażynowy (Bs/Bśw nadmorski), sosna | sand, suspicious_sand, coarse_dirt (wydma szara), [mod] piasek wydmowy, sosna nadmorska (krzywa) |
| **Wybrzeże klifowe** | 0–95* | 20–95 | 45–90° | klif czynny wys. 20–95 m*, cofanie 0,8 m/rok*; u podnóża plaża żwirowa; na krawędzi buczyna | glina zwałowa, piaski, głazy | buczyna pomorska (Lśw), bór na wydmach | clay/packed_mud (glina zwałowa) [mod] "glina zwałowa z głazami", gravel, stone (głazy: cobblestone/mossy), buk [mod] |
| **Jezioro przybrzeżne** | 0–1 | 0–2 | 0° | Łebsko 71 km²*, gł. 1–6 m; szuwary szer. 50–500 m; mierzeja od morza | muł, torf, piasek | trzcinowiska, olsy, bory bagienne | water, mud, [mod] szuwar trzcinowy, ols (olsza czarna) |
| **Delta / Żuławy (depresja)** | -2,2*–15* | 0–5 | 0° | polder; kanały co 200–500 m; wały 3–6 m; depresja 450 km²* | mady, torfy | pola, łąki, wierzby głowiaste; naturalnie: łęgi wierzbowo-topolowe (Lł), olsy | farmland, grass_block, [mod] mada (ciemna żyzna ziemia), wierzba [mod], rowy z water |
| **Wysoczyzna morenowa falista (młodoglacjalna)** | 60–150 (Pomorze 150–250) | 5–30 | 0–5° | fale o dł. 300–1500 m; oczka 20–300 m co 0,5–1 km; głazy narzutowe | glina zwałowa, piaski gliniaste | pola (dominują), grąd (Lśw/LMśw), buczyna (Pomorze), olsy w oczkach | grass_block/farmland, [mod] glina zwałowa (block pod glebą), coarse_dirt, cobblestone (eratyki), dąb/grab/buk [mod] |
| **Moreny czołowe (wzgórza)** | 150–329* | 50–150 | 10–25° | wzgórze 0,5–2 km szer.; ciągi 2–10 km szer., 100 km dł.; Wieżyca 328,6* | żwiry, głazy, glina spiętrzona | buczyna pomorska, grąd, bór mieszany (LMśw/Lśw) | gravel, [mod] glina zwałowa, stone/cobblestone, buk [mod], dąb, grab |
| **Sandr** | 80–180 | 0–10 (wydmy do 30*) | <1°; spadek 1–3‰ | równina 20–100 km; wydmy paraboliczne 5–30 m; jeziora rynnowe wcięte 20–60 m | piaski i żwiry sandrowe | **bór sosnowy** (Bśw, Bs, BMśw), bór chrobotkowy, wrzosowiska | sand pod cienką warstwą podzol/coarse_dirt, [mod] "bielica" (gleba), sosna [mod], chrobotek/wrzos [mod] |
| **Rynna polodowcowa z jeziorem** | dno jeziora do -50 (Hańcza 108,5*) | 20–100 | zbocza 15–35° | szer. 0,2–2 km; dł. 5–40 km; kręta; wyspy | glina, piaski; dno: gytia | zbocza: grąd zboczowy, buczyna; brzegi: szuwary, olsy | water (gł. do 100 bloków!), [mod] gytia/muł jeziorny, gravel, grąd |
| **Jezioro morenowe płytkie** | 100–120 | 0–25 | 0–5° | Śniardwy 113,8 km², gł. 23 m; urozmaicona linia brzegowa, wyspy | glina, muł | szuwary, olsy, łąki | water, mud, [mod] szuwar |
| **Oz / kem / drumlin** | +10–40 nad otoczenie | 10–40 | oz 15–30°, drumlin 5–10° | oz: 50–300 m szer., 0,5–15 km dł.; drumlin 100–500 × 200–2000 m; kem średnica do kilkuset m* | piaski, żwiry (oz, kem); glina (drumlin) | bór mieszany, grąd; drumliny – pola | gravel, sand, [mod] glina zwałowa; sosna, dąb |
| **Pradolina (dno)** | 30–80 | 0–5 (wydmy do 30) | 0–1° | dno 2–25 km szer.*; krawędź 20–50 m; terasy | torfy, piaski rzeczne, mady | łąki na torfach, olsy, łęgi; na terasach bory sosnowe i wydmy | [mod] torf (block), grass (łąka), water (rowy), sand, sosna |
| **Wysoczyzna staroglacjalna (Mazowsze, Wielkopolska S, Podlasie)** | 80–200 | 2–20 | 0–3° | równiny denudacyjne; brak jezior; doliny denudacyjne; kemy zdegradowane | glina zwałowa zwietrzała, piaski, lokalnie żwiry | pola (dominują), grąd (Lśw), bory mieszane na piaskach, dąbrowy | farmland/grass_block, coarse_dirt, [mod] glina zwałowa; sosna, dąb, grab |
| **Dolina wielkiej rzeki nizinnej (Wisła, Bug, Narew, Odra, Warta)** | 0–200 | 0–5 (koryto 2–8 m gł.) | 0° | koryto 100–1000 m; łachy, kępy 200–2000 m; starorzecza 0,3–3 km; taras zalewowy 2–15 km; wał 3–6 m | mady, piaski rzeczne, muł | łęg wierzbowo-topolowy (Lł), łęg jesionowo-wiązowy, olsy, łąki zalewowe, szuwary | water (płynące), sand (łachy), [mod] mada, mud, wierzba/topola biała [mod], wiąz/jesion [mod] |
| **Bagno / torfowisko niskie (Biebrza, Narew, Polesie)** | 100–120 | 0–3 (wydmy grzędy 5–15) | 0° | doliny 3–15 km szer.; rozlewiska 2–3 mies.; anastomozy; miąższość torfu 1–3 m | torf niski, muł | turzycowiska, mechowiska, szuwary, olsy (Ol), brzeziny bagienne, bory bagienne na obrzeżu | [mod] torf, mud, water (płytka), [mod] turzyca/mech torfowiec, brzoza omszona, olsza, sosna (Bb) |
| **Torfowisko wysokie (Pomorze, Orawa, góry)** | 20–120 (Orawa 600–660; Karkonosze 1400*) | kopuła 2–7 | 0–2° | 10–400 ha; jeziorka dystroficzne; bór bagienny na obrzeżu | torf sfagnowy 5–10 m | torfowce, wrzosiec, bagno, żurawina, kosodrzewina (Orawa), bór bagienny (Bb) | [mod] torf wysoki (jaśniejszy), [mod] torfowiec (blok/roślina), water (ciemna), sosna karłowata [mod] |
| **Wydmy śródlądowe (Kampinos, Puszcza Notecka, Bory Dolnośląskie)** | 70–150 | 5–30* | 5–12° dowietrzne / 25–33° zawietrzne | paraboliczne, ramiona 1–3 km, otwarte na W; pola wydmowe 100–1300 km² | piasek eoliczny | bór suchy/świeży (Bs/Bśw), chrobotki, wrzos; obniżenia: olsy, bagna | sand + podzol, [mod] bielica, sosna, [mod] chrobotek |
| **Płaskowyż lessowy z wąwozami (Lubelszczyzna, Sandomierz, Proszowice)** | 180–300 | 20–90* (skarpa Wisły 90*) | 0–5° wierzchowina; 60–90° ściany wąwozów | wąwozy gł. 5–30 m, dno 1–10 m, dł. 0,2–2 km, gęstość 2–10+ km/km²* | less (10–30 m*) na kredzie/opoce | pola, sady; wąwozy: grąd lipowo-grabowy (Lwyż), zarośla | [mod] less (blok jasnobeżowy, pionowe ściany), farmland, [mod] czarnoziem, lipa/grab/klon [mod], leszczyna [mod] |
| **Wyżyna wapienna krasowa (Jura)** | 300–516* | 50–150 | 0–5° wierzchowina; 90° skałki | ostańce 10–30 m; doliny wcięte 60–120 m; 1500 jaskiń*; wywierzyska | wapień jurajski, less, rędziny | buczyny (Lwyż), grąd, bory (20% lasów*), murawy kserotermiczne, pola | [mod] wapień (jasny, calcite-like), stone jako podłoże, [mod] rędzina, buk, jaskinie generowane w wapieniu |
| **Kras gipsowy (Ponidzie)** | 200–300 | 20–60 | 0–10° | leje 5–50 m; doliny ślepe; kryształy gipsu | gips, margle, less | murawy kserotermiczne (stepy), pola, lasy <15% | [mod] gips (jasnoszary, półprzezroczysty kryształ), [mod] rędzina, trawy stepowe [mod] |
| **Góry Świętokrzyskie (niskie, stare)** | 300–614* | 200–350 | 10–25° | pasma 1–3 km szer., 70 km dł.*; gołoborza 50–200 m szer.* | kwarcyt*, wapień dewoński*, łupki | Puszcza Jodłowa: jodła, buk, modrzew polski* (LMwyż/Lwyż), gołoborza bez roślin (porosty) | [mod] kwarcyt (bloki gołoborzy: luźne, mchem porośnięte), stone, jodła [mod], buk [mod], modrzew [mod] |
| **Pogórze karpackie** | 300–600 | 100–250 | 5–15° | garby dł. 2–10 km; doliny 0,5–2 km; osuwiska; skałki piaskowcowe 10–20 m | flisz (piaskowiec + łupek) pod lessem/gliną | pola pasowe, sady, grąd, buczyna na grzbietach (Lwyż, LMwyż) | [mod] piaskowiec fliszowy (szarobrązowy, warstwowany), [mod] łupek ilasty, farmland pasowe, grab, dąb, buk |
| **Beskidy (średnie góry fliszowe)** | 500–1725 | 400–900 | 15–30° | kopulaste, grzbiety zaokrąglone, polany, doliny V-kształtne, potoki żwirowe 2–10 m szer. | piaskowce magurskie/godulskie, łupki | regiel dolny: buczyna karpacka (LG), jodła; regiel górny: świerk (BWG); polany (hale) | [mod] piaskowiec fliszowy, stone, gravel (potoki), buk, jodła, świerk [mod], jawor [mod] |
| **Bieszczady (połoniny)** | 500–1346* | 500–800 | 15–30° | rusztowe grzbiety NW–SE*; połoniny >1150 m*; brak regla górnego* | piaskowce krośnieńskie* | buczyna karpacka (500–1150 m*), olszyna karpacka, połoniny (borówczyska, trawy), buk karłowaty | jak Beskidy + [mod] borówka, trawa połoninowa; brak świerków |
| **Pieniny (wapienne skałki)** | 400–982 | 300–500 | 30–90° | przełom Dunajca dł. 8–9 km, ściany 300 m, koryto 40–100 m | wapienie pienińskie | buczyna, jodła, jaworzyna, murawy naskalne, reliktowe sosny na skałkach | [mod] wapień (jasny), stone, buk, jodła, sosna reliktowa |
| **Podhale / Orawa (kotlina śródgórska)** | 550–1126 | 100–500 | 3–15° | garby fliszowe, stożki żwirowe, torfowiska wysokie 600–660 m | flisz podhalański, żwiry, torf | pola, łąki, bory świerkowe, torfowiska z kosodrzewiną | [mod] flisz, gravel, [mod] torf wysoki, świerk, kosodrzewina [mod] |
| **Tatry (alpejskie)** | 900–2499* | do 2000* | 35–60° (turnie), 30–45° (żleby), 30–35° (piargi) | granie; kotły; doliny U; ~200 stawów*; Morskie Oko 35 ha*; 857 jaskiń*; ściany do 500 m | granit* (Wysokie), wapienie/dolomity* (Zachodnie, regle), gnejsy* | regiel dolny do 1250*, górny do 1500*, kosodrzewina do 1800*, hale do 2300*, turnie* | granite/stone (turnie), gravel + [mod] piarg (luźne bloki), calcite/[mod] wapień, water (stawy), snow, świerk, limba [mod], kosodrzewina [mod] |
| **Karkonosze / Sudety Zachodnie** | 400–1603* | 500–1200 | 20–35° (stok N), zrównania 0–5° | zrównania szczytowe, 6 kotłów*, skałki granitowe do 25 m*, torfowiska subalpejskie*, gołoborza | granit karkonoski*, gnejsy* | piętra obniżone*: regiel dolny do 1000, górny do 1250 (świerk), kosodrzewina, murawy; torfowiska wysokie | granite, [mod] skałka granitowa (formy "tors"), gravel, [mod] torf wysoki, świerk, kosodrzewina |
| **Góry Stołowe (płytowe)** | 500–919 | 100–300 | 0° stoliwa, 90° ściany | trzy poziomy stoliw; ściany 10–50 m; labirynty skalne, szczeliny 0,3–1 m szer. | piaskowiec ciosowy kredowy, margle | bory świerkowe, torfowiska na stoliwach, buczyny na stokach | [mod] piaskowiec ciosowy (jasny, prostopadłościenne bloki), sandstone jako fallback, świerk |
| **Kotlina śródgórska sudecka (Jeleniogórska, Kłodzka)** | 280–450 | 20–100 | 0–5° | równina falista, stawy, wzgórza wyspowe (granitowe skałki) | granit zwietrzały, gliny, mady | pola, łąki, grąd, olsy, stawy | grass, farmland, granite (wzgórza wyspowe), water |
| **Przedgórze Sudeckie** | 200–718 | 20–500 (Ślęża) | 0–5° / masywy 15–30° | równina denudacyjna; twardzielce 300–700 m | granit, gabro, gnejs pod lessem | pola (czarnoziemy), na masywach: buczyny, dąbrowy, gołoborza (Ślęża) | farmland, [mod] less/czarnoziem, granite, diorite (gabro), buk, dąb |
| **Wyżyna Śląska (antropogeniczna)** | 250–400 | 30–130 (hałdy) | 25–35° (hałdy) | hałdy stożkowe/stołowe 30–130 m; zapadliska z wodą; piaskownie; kopalnie | wapień/dolomit triasowy, karbon (węgiel), odpady | lasy sosnowe zdegradowane, brzoza na hałdach, murawy galmanowe | [mod] hałda (czarna skała płonna), coal_ore, [mod] dolomit, brzoza, sosna |
| **Nizina Śląska / lessowa nizinna** | 100–300 | 5–30 | 0–3° | wielkie pola; dolina Odry z łęgami | less, czarnoziemy, mady, piaski | pola (dominują), łęgi (Lł), grąd, bory na piaskach | farmland, [mod] czarnoziem, dąb, wiąz, jesion, topola |
| **Stawy rybne (Milicz, Oświęcim)** | 100–250 | 0–3 | 0° | stawy 10–300 ha, groble 1–2 m, szuwary | glina, muł | szuwary, olsy, łęgi | water (płytka 1–2 bloki), mud, [mod] grobla, olsza, trzcina |

**Skale referencyjne dla generatora (podsumowanie):** wzgórze morenowe: szer. 500–2000 m, wys. wzgl. 20–100 m (do 150 m na Kaszubach i Suwalszczyźnie); oczko: 20–300 m; rynna: 200–2000 m szer., 5–40 km dł., 20–100 m gł.; wydma paraboliczna: 1–3 km rozpiętości, 5–30 m wys.; wąwóz lessowy: 5–30 m gł., 1–10 m szer. dna, 0,2–2 km dł.; ostaniec wapienny: 10–30 m; dolina karpacka V: 0,3–2 km szer., 300–800 m gł.; kocioł tatrzański: 0,5–2 km średnicy, ściany 200–500 m; grzbiet beskidzki: 5–20 km dł., kopuła o promieniu 1–3 km; koryto wielkiej rzeki: 100–1000 m; dno doliny wielkiej rzeki: 2–15 km; klif: 20–95 m; plaża: 20–80 m.

## 9. Implikacje projektowe dla moda

1. **Skala pionowa vs. limit świata.** Rzeczywisty zakres wysokości Polski to -2,2…2499 m (amplituda ~2500 m). Vanilla (1.18+) ma zakres Y = -64…319 (384 bloków). Opcje: (a) rozszerzyć limit wysokości przez data pack `dimension_type` (`min_y`/`height` – max 4064 bloków łącznie; wymaga własnego wymiaru lub nadpisania overworld – rejestracja dimension type + noise settings, wykonalne na Fabric bez mixinów; koszt: wydajność chunków, oświetlenie, mapy, kompatybilność z modami); (b) skala pionowa 1:1 dla nizin i pojezierzy (0–330 m mieści się w 384 blokach z zapasem na jeziora do -100 m: Hańcza!), a góry przez nieliniową kompresję (np. 1:1 do 300 m, potem 1:2–1:4 – Tatry ~900 bloków przy 1:2,5…). Rekomendacja: min_y = -64, height = 1024 (Y do 960) i kompresja 1:2,6 powyżej 300 m -> Rysy ≈ 300 + 2199/2,6 ≈ 1146 → NIE mieści się; przy height 2048 (Y do 1984) i kompresji 1:1,3 mieści się z Tatrami na ~1990. **Decyzja do uzgodnienia z użytkownikiem** (pytanie w sekcji 10). Dla lasów kluczowe jest, by regiel dolny/górny/kosodrzewina miały po min. 50–100 bloków wysokości – przy kompresji >1:3 piętra spłaszczą się do nieczytelnych pasków.
2. **Skala pozioma.** Polska to ~650 × 690 km; przy 1 blok = 1 m to 650 000 bloków – technicznie mieści się w limicie świata (30 mln), ale gracz przemierza 1 km w ~4 min. Pasowość rzeźby (pobrzeża -> pojezierza -> niziny -> wyżyny -> kotliny -> góry) ma sens tylko, gdy generator odwzorowuje ją w osi N–S; rozważyć skalę poziomą 1:10 (65 km na mapę kraju) z zachowaniem skali pionowej 1:1 dla form małych (wąwóz, wydma, oczko) i "regionalnych" biomów. Alternatywnie generator proceduralny "typów krajobrazu" bez mapy Polski, ale z regułami sąsiedztwa (sandr przylega do moreny czołowej od S; pradolina między pasami moren; klif tylko przy morzu; połoniny tylko w biomie bieszczadzkim).
3. **Dane wejściowe realnej skali.** Do wiernej reprodukcji użyć NMT GUGiK (geoportal.gov.pl, siatka 1 m lub 5 m, darmowy, licencja otwarta), granic mezoregionów Solon 2018 (shapefile, IGiPZ PAN/GDOŚ), BDL Lasów Państwowych (siedliskowe typy lasu i drzewostany – bdl.lasy.gov.pl, usługi WMS/WFS), Corine Land Cover / BDOT10k (pokrycie terenu), MPHP (sieć rzeczna), SMGP (geologia 1:50 000, PGI). Heightmap-based generation w Fabric: własny `ChunkGenerator` czytający kafelki wysokości + maska "typ krajobrazu" (indeks mezoregionu) -> reguły biomów i podłoża.
4. **Dwa poziomy generatora.** Warstwa 1 (regionalna, 1–10 km): pasy/makroregiony wyznaczają wysokość bazową, amplitudę i "styl" rzeźby (młodoglacjalna: pagórki 5–30 m + rynny; staroglacjalna: płaska 2–20 m; lessowa: wierzchowina + wąwozy; górska fliszowa: kopuły; alpejska: granie i kotły). Warstwa 2 (lokalna, 20–500 m): formy (oczko, oz, kem, wydma paraboliczna, wąwóz, ostaniec, starorzecze, kępa, gołoborze, skałka) generowane jako "feature" z wymiarami z sekcji 8.
5. **Podłoże warstwowe zamiast jednolitego kamienia.** Wprowadzić bloki: glina zwałowa (z eratykami), piasek sandrowy, less (ściany pionowe – wysoka "spójność"), mada, torf niski, torf wysoki (sfagnowy), gytia/muł jeziorny, wapień jurajski, gips, kwarcyt, piaskowiec fliszowy + łupek (warstwowanie 1:1, sprzyja osuwiskom), piaskowiec ciosowy, granit karkonoski/tatrzański, gnejs, bazalt (neki), rędzina, czarnoziem, bielica, gleba brunatna. Warstwa gleby (1–3 bloki) determinuje STL i przez to zestaw drzew/runa: bielica na piasku -> Bs/Bśw (sosna, chrobotki, wrzos, borówka); gleba brunatna na glinie -> Lśw/grąd (dąb, grab, lipa); mada -> Lł (wierzba, topola, wiąz, jesion); torf niski -> Ol (olsza czarna, turzyce); torf wysoki -> Bb (sosna karłowata, torfowce, bagno, żurawina); rędzina -> buczyna wyżynna / murawy; less -> pola/grąd lipowy.
6. **Woda.** Jeziora rynnowe muszą mieć realną głębokość (Hańcza 108 m; typowo 20–60 m) – generator jezior powinien rzeźbić misę poniżej poziomu wysoczyzny, nie tylko "nalewać" na poziomie morza. Jeziora Mazur leżą na ~116 m n.p.m., Tatr na 1400–2000 m – poziom wody per jezioro, nie globalny sea level. Rzeki: gradient i szerokość rosną w dół biegu (potok 2–10 m -> 20–60 m -> 100–1000 m); wielkie rzeki nizinne z łachami, kępami i starorzeczami wymagają osobnego "river carver" (pas meandrowy 2–15 km z tarasami).
7. **Mokradła jako pełnoprawny typ terenu** (nie tylko vanilla swamp): torfowisko niskie (Biebrza: 3–15 km szer., turzycowiska, olsy, sezonowe rozlewiska – integracja z Serene Seasons: wiosną poziom wody +1 blok), torfowisko wysokie (kopuła, jeziorka, kosodrzewina na Orawie), anastomozująca Narew, stawy rybne z groblami, starorzecza.
8. **Piętra górskie parametryzowane per masyw** (sekcja 5): próg regla dolnego/górnego/kosodrzewiny/hal zależny od "masywu" (Tatry 1250/1500/1800; Babia Góra 1150/1390/1650; Karkonosze 1000/1250/1450; Bieszczady 1150 → połoniny, brak regla górnego). Gołoborza (Świętokrzyskie, Babia Góra, Karkonosze, Ślęża) jako feature bloków luźnego kamienia bez gleby.
9. **Antropogeniczne elementy krajobrazu** są nieodłączne od "wszystkich typów krajobrazu Polski": pola (grunty orne ~37–40% kraju; mozaika wąskich pasów na Podlasiu/Małopolsce vs. wielkie łany w Wielkopolsce/na Śląsku), łąki (ok. 11%), sady (Grójec), stawy (Milicz), hałdy (GOP), wały, kanały (Żuławy), polder, drogi z alejami. Bez nich Wielkopolska, Kujawy, Lubelszczyzna, Nizina Śląska byłyby całkowicie nierozpoznawalne (lasy 5–25%). Zdecydować, czy generator tworzy pola (np. jako "biom rolniczy" z farmland/trawą, miedzami i zadrzewieniami śródpolnymi) – patrz pytanie w sekcji 10.
10. **Lesistość jako parametr regionalny**: lubuskie 49% vs. łódzkie 21%; sandry ~80–100% lasu, lessy 5–15%, moreny denne 10–25%. Generator powinien losować "płaty leśne" z prawdopodobieństwem zależnym od podłoża (piasek -> las prawie pewny; less/czarnoziem -> pole prawie pewne; glina -> mieszane), a nie stałym per biom.
11. **Skład gatunkowy startowy** (Wikipedia pl "Lasy w Polsce"): sosna 58,5%, brzoza 7,5%, dąb 7,5%, świerk 6,4%, buk 5,8%, olsza 5,4% – oznacza, że domyślnym lasem nizinnym Polski jest bór sosnowy, a nie las dębowy jak w vanilla. Siedliska borowe 51% / lasowe 49%; olsy i łęgi 3,8%.
12. **Formy skalne jako "struktury"**: ostańce jurajskie (10–30 m, wapień), skałki granitowe Karkonoszy (do 25 m, 150+ grup), labirynty Gór Stołowych (ściany 10–50 m), skałki fliszowe Pogórza (Prządki), gołoborza, klify (z cofaniem – niezmienne w grze), głazy narzutowe (Trygław 3,8 m wys.) rozsiane na wysoczyznach młodoglacjalnych (1 duży głaz na 1–10 km², mniejsze częściej).
13. **Jaskinie** tylko w wapieniach (Jura ~1500, Tatry 857, Świętokrzyskie, Sudety – marmury) i w gipsach; brak jaskiń w glinie/piasku/flisz (poza szczelinami). Vanilla cave carver należy ograniczyć do bloków wapienia/gipsu/granitu (Tatry).
14. **Wybrzeże**: profil plaża -> wydma biała -> szara -> bór bażynowy; klify tylko na wysoczyznach morenowych dochodzących do morza; jeziora przybrzeżne za mierzeją; Żuławy jako depresja z polderami (wymaga "poziomu morza" powyżej terenu – specjalna obsługa, by nie zalać).
15. **Najpierw prototyp 3–4 kontrastowych krajobrazów** o największej wartości dla lasów: (1) sandr z borem i rynną (Bory Tucholskie), (2) wysoczyzna morenowa z grądem i oczkami + Puszcza Białowieska jako "bogaty grąd", (3) dolina wielkiej rzeki z łęgami i starorzeczami (Wisła), (4) Beskidy z buczyną karpacką i piętrami; potem Tatry, wyżyna lessowa, torfowisko, wybrzeże.
16. **Nazewnictwo i rejestr biomów** oprzeć na mezoregionach Solon 2018 (344 jednostek – za dużo na biomy; użyć ~25–35 "typów krajobrazu" z sekcji 8 jako biomów, a mezoregion jako tag/atrybut dla spawnu fauny i nazw na mapie).

## 10. Niepewności / do potwierdzenia

**Nie udało się potwierdzić w źródle internetowym (budżet 30 zapytań wyczerpany; próby nieudane odnotowano):**
1. Dokładne udziały pięter hipsometrycznych (<100, 100–200, 200–300, 500–1000, >1000 m) – potwierdzone tylko: <200 m = 75%, 300–500 m = 5,4%, >500 m = 3% (Wikipedia pl). Wartości 25%/50%/16%/2,8%/0,2% są podręcznikowe (Kondracki) i wymagają weryfikacji (2 wyszukiwania bezowocne; strona "Ukształtowanie powierzchni Polski" na Wikipedii nie istnieje – 404).
2. Najniższy punkt: Wikipedia pl podaje **Marzęcino -2,2 m** (nowsze pomiary) zamiast tradycyjnego Raczki Elbląskie -1,8 m. Który przyjąć w modzie? (obie wartości poniżej -1 m; różnica bez znaczenia dla generatora, ale istotna dla nazw/opisów).
3. Szerokości koryt i dolin rzek w poszczególnych odcinkach (Wisła, Odra, Warta, Bug, Narew, San) – artykuł "Wisła" ich nie zawiera; wartości w sekcji 3.2 to szacunki z wiedzy ogólnej. Do sprawdzenia na ortofotomapie geoportal.gov.pl lub w MPHP.
4. Gęstość sieci rzecznej Polski (km/km²) – nie znaleziono liczby ze źródła pierwotnego.
5. Wymiary typowe ozów, drumlinów, kemów, moren czołowych, rynien (sekcja 3.1, tabela druga) – strony zpe.gov.pl, Wikipedia pl "Rzeźba młodoglacjalna" i geografia24.pl nie podają liczb; wartości z podręczników geomorfologii (Klimaszewski, Migoń) – zalecana weryfikacja w "Geomorfologii" P. Migonia (PWN 2006) lub w artykułach naukowych.
6. Łącka Góra (42 m) i prędkość wydm ruchomych (3–10 m/rok), Czołpińska Góra (56 m) – artykuł "Słowiński PN" tych liczb nie zawiera; sprawdzić na slowinskipn.pl.
7. Wysokości klifów: Wolin 95 m potwierdzone (Woliński PN), Jastrzębia Góra, Orłowo, Trzęsacz – niepotwierdzone.
8. Śniardwy (113,8 km², 23,4 m), Mamry (104 km²), lista największych/najgłębszych jezior – strony "Lista jezior w Polsce" (pl) i "List of lakes of Poland" (en) nie zawierały tabel z danymi; potwierdzone tylko Hańcza 108,5 m, Łebsko 71 km², Gardno 25 km², Morskie Oko 34,93 ha.
9. Użytkowanie gruntów według województw (grunty orne, łąki, wody, zabudowa) – NIE ZBADANO; dostępne tylko lesistość (16 województw, LP/GUS 2024 – ze źródła wtórnego, wartości do potwierdzenia w Roczniku Statystycznym Leśnictwa 2024, PDF GUS). Pozostałe kolumny: BDL GUS, kategoria K7/P1874.
10. Powierzchnie i wysokości w tabeli makroregionów (sekcja 1.3) poza gwiazdkowanymi – wiedza ogólna (Kondracki 2002); wysokości mezoregionów mogą różnić się o ±5–10 m od aktualnych pomiarów LiDAR.
11. Zbiorniki zaporowe (sekcja 4.3) – niezweryfikowane w tym badaniu.
12. Karkonosze – artykuł nie podaje wymiarów kotłów polodowcowych ani nachyleń stoków; Śnieżne Kotły "ściany 200–300 m" – wiedza ogólna.
13. Bieszczady – Wikipedia pl podaje piętra: pogórze do 500 m, regiel dolny 500–1150 m, połoniny >1150 m; w innych źródłach (BdPN) górna granica lasu 1150–1250 m. Jezioro Solińskie, powierzchnia BdPN (292 km²) – niepotwierdzone.
14. Rozbieżność: obwód głazu Trygław 44 m (zpe.gov.pl) vs. 50 m (geografia24.pl).
15. Rozbieżność: długość Wisły 1022 km (GUS 2010) vs. 1047 km (starsze źródła, nadal w "Geografii Polski" na Wikipedii) vs. 1023,5 km (pomiar 2005).
16. Udział sadów 7,8% w jednym z zestawień GUS 2020 (wynik wyszukiwania) jest niewiarygodny (sady w Polsce ok. 1% powierzchni) – prawdopodobnie błąd cytującej strony.
17. Data w polu "aktualizacja Solon i in. 2018": liczba mezoregionów 344 (potwierdzone), 59 makroregionów (wg listy Wikipedii – policzone: 59 pozycji; w publikacji Solon i in. mogą być liczone inaczej, np. 60 z Kotliną Freienwaldzką poza Polską).

**Pytania do użytkownika (decyzje projektowe):**
- A. Skala: czy świat ma być mapą Polski (heightmap z NMT, skala pozioma 1:1 czy 1:10?) czy proceduralnym generatorem "krajobrazów polskich" z regułami sąsiedztwa? "Rzeczywiste wymiary" sugerują 1:1, ale 650 km × 690 km to ~450 000 km² do wygenerowania.
- B. Wysokość świata: rozszerzyć limit (data pack `height` do 2048+; ryzyko wydajności i kompatybilności z innymi modami) czy kompresować góry powyżej ~300 m? Jak bardzo "wierne" mają być Tatry (2499 m = 2499 bloków)?
- C. Czy generator ma tworzyć krajobraz rolniczy (pola, łąki, sady, stawy, hałdy, wały, kanały Żuław)? Bez tego 60% powierzchni Polski (niziny lessowe, Wielkopolska, Kujawy) będzie pokryte lasem, którego tam nie ma – sprzeczność z "wiernością", ale "mod o lasach" może celowo pokazywać Polskę potencjalnej roślinności naturalnej (mapa Matuszkiewicza). Które podejście?
- D. Punkt odniesienia dla depresji: Raczki Elbląskie (-1,8) czy Marzęcino (-2,2)?
- E. Czy biomy mają odpowiadać makroregionom (59), typom krajobrazu (~30, sekcja 8), czy mezoregionom (344)?
- F. Czy jaskinie vanilla mają być wyłączone poza obszarami wapiennymi/gipsowymi?
- G. Zbiorniki zaporowe i jeziora antropogeniczne – uwzględniać (Solina, Włocławek) czy tylko naturalne?

## 11. Źródła

**Odczytane w pełni (WebFetch) – zweryfikowane:**
1. Regionalizacja fizycznogeograficzna Polski (Kondracki / Solon i in. 2018) – https://pl.wikipedia.org/wiki/Regionalizacja_fizycznogeograficzna_Polski
2. Geografia Polski – https://pl.wikipedia.org/wiki/Geografia_Polski
3. Jeziora w Polsce – https://pl.wikipedia.org/wiki/Jeziora_w_Polsce
4. Lasy w Polsce – https://pl.wikipedia.org/wiki/Lasy_w_Polsce
5. Rzeźba młodoglacjalna – https://pl.wikipedia.org/wiki/Rze%C5%BAba_m%C5%82odoglacjalna
6. Pradolina Toruńsko-Eberswaldzka – https://pl.wikipedia.org/wiki/Pradolina_Toru%C5%84sko-Eberswaldzka
7. Rzeźba staro- i młodoglacjalna w Polsce (ZPE, MEN) – https://zpe.gov.pl/a/przeczytaj/DuFP4hBWb
8. Suwalski Park Krajobrazowy – https://pl.wikipedia.org/wiki/Suwalski_Park_Krajobrazowy
9. Wisła – https://pl.wikipedia.org/wiki/Wis%C5%82a
10. Słowiński Park Narodowy – https://pl.wikipedia.org/wiki/S%C5%82owi%C5%84ski_Park_Narodowy
11. Żuławy Wiślane – https://pl.wikipedia.org/wiki/%C5%BBu%C5%82awy_Wi%C5%9Blane
12. Tatry – https://pl.wikipedia.org/wiki/Tatry
13. Karkonosze – https://pl.wikipedia.org/wiki/Karkonosze
14. Wyżyna Krakowsko-Częstochowska – https://pl.wikipedia.org/wiki/Wy%C5%BCyna_Krakowsko-Cz%C4%99stochowska
15. Kazimierski Park Krajobrazowy – https://pl.wikipedia.org/wiki/Kazimierski_Park_Krajobrazowy
16. Góry Świętokrzyskie – https://pl.wikipedia.org/wiki/G%C3%B3ry_%C5%9Awi%C4%99tokrzyskie
17. Bieszczady Zachodnie – https://pl.wikipedia.org/wiki/Bieszczady_Zachodnie
18. Biebrzański Park Narodowy – https://pl.wikipedia.org/wiki/Biebrza%C5%84ski_Park_Narodowy
19. Woliński Park Narodowy – https://pl.wikipedia.org/wiki/Woli%C5%84ski_Park_Narodowy
20. Kampinoski Park Narodowy – https://pl.wikipedia.org/wiki/Kampinoski_Park_Narodowy
21. Rzeźbotwórcza działalność lądolodów (geografia24.pl) – https://geografia24.pl/rzezbotworcza-dzialalnosc-ladolodow/
22. Rolnictwo w Polsce – https://pl.wikipedia.org/wiki/Rolnictwo_w_Polsce (brak tabeli wojewódzkiej)
23. Hipsometria Polski (scienne.pl) – https://scienne.pl/hipsometria-polski/ (bez danych liczbowych)

**Wyniki wyszukiwania (WebSearch) – źródła wtórne, wskazane do weryfikacji:**
24. Lesistość województw 2024/2025 (zestawienie wg Lasów Państwowych, post w serwisie Threads @toprzelom) – https://www.threads.com/@toprzelom/post/DTtK8PZjVGc/
25. Rocznik Statystyczny Leśnictwa 2024 (GUS, PDF – źródło pierwotne dla lesistości; nieodczytany) – https://stat.gov.pl/download/gfx/portalinformacyjny/pl/defaultaktualnosci/5515/13/7/1/rocznik_statystyczny_lesnictwa_2024_0612.pdf oraz kopia na BDL LP: https://www.bdl.lasy.gov.pl/portal/Media/Default/Publikacje/GUS_lesnictwo_2024.pdf
26. Bank Danych Lokalnych GUS – Powierzchnia i użytkowanie gruntów (metadane) – https://bdl.stat.gov.pl/bdl/metadane/cechy/1874
27. Obszary wiejskie w Polsce w 2024 r. (GUS, 31.03.2026) – https://stat.gov.pl/obszary-tematyczne/rolnictwo-lesnictwo/rolnictwo/obszary-wiejskie-w-polsce-w-2024-r-,2,7.html
28. Zasoby lasów w Polsce (LP, PDF) – https://walily.bialystok.lasy.gov.pl/documents/62696/22965593/Zasoby+le%C5%9Bne+w+Polsce.pdf
29. Grunty leśne podliczone (portalsamorzadowy.pl) – https://www.portalsamorzadowy.pl/ochrona-srodowiska/grunty-lesne-podliczone-w-ktorym-regionie-jest-ich-najwiecej,235112.html
30. Mapy hipsometryczne Polski (Dokumentacja Geograficzna 1969, RCIN) – https://rcin.org.pl/Content/28662/PDF/WA51_40004_r1969-z1_Dokumentacja-Geogr.pdf
31. Use of SRTM-3 data in the analysis of Poland's surface relief (CORE) – https://core.ac.uk/works/130153721

**Nieudane (404 – tytuł nie istnieje):** https://pl.wikipedia.org/wiki/Ukształtowanie_powierzchni_Polski ; https://pl.wikipedia.org/wiki/Lista_jezior_w_Polsce ; en.wikipedia "List of lakes of Poland" – istnieje, ale bez tabel z danymi.

**Zalecane źródła pierwotne do dalszej weryfikacji (nieodczytane w tym badaniu):**
- Solon J. i in. 2018, "Physico-geographical mesoregions of Poland: verification and adjustment of boundaries on the basis of contemporary spatial data", Geographia Polonica 91(2): 143–170 – https://doi.org/10.7163/GPol.0115 (granice w formacie SHP: https://www.gdos.gov.pl/dane-i-metadane – Geoserwis GDOŚ)
- Kondracki J., "Geografia regionalna Polski", PWN (2002/2011) – kanoniczne wysokości i opisy mezoregionów.
- Migoń P., "Geomorfologia", PWN 2006 – wymiary form.
- GUGiK NMT (geoportal.gov.pl, usługa "Numeryczny Model Terenu", siatka 1 m) – https://www.geoportal.gov.pl/
- Bank Danych o Lasach – https://www.bdl.lasy.gov.pl/portal/ (siedliskowe typy lasu, mapy WMS)
- Mapa potencjalnej roślinności naturalnej Polski (Matuszkiewicz, IGiPZ PAN) – https://www.igipz.pan.pl/Roslinnosc-potencjalna-zgik.html
- Choiński A., "Katalog jezior Polski", UAM 2006.
- Państwowy Instytut Geologiczny – Szczegółowa Mapa Geologiczna Polski 1:50 000 – https://geolog.pgi.gov.pl/
- Słowiński PN (wydmy): https://slowinskipn.pl/ ; Biebrzański PN: https://www.biebrza.org.pl/ ; TPN: https://tpn.pl/ ; KPN: https://kpnmab.pl/
