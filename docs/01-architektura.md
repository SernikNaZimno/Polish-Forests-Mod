# Architektura moda „Polish Forests” (dawniej „Przyrodniczo zgodne lasy”)

Wersja dokumentu: 2026-09-27. Podstawa: decyzje w `00-decyzje-do-podjecia.md` i raporty `research/01–08`.

## 1. Założenia wiążące

| Obszar | Ustalenie |
|---|---|
| Platforma | Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.161.0, Java 25, mapowania Mojang (kod gry nieobfuskowany) |
| Zależności wymagane | Fabric API, GeckoLib 5.5.7, SmartBrainLib 2.0.2 |
| Zależności opcjonalne | Serene Seasons 26.1.2.0.7 (+ GlitchCore); bez niego działa własny kalendarz |
| Świat | proceduralny, nieskończony, mozaika polskich typów krajobrazu z regułami geomorfologii |
| Skala | 1:1 w poziomie i pionie (1 blok = 1 m), regiony domyślnie w rzeczywistych rozmiarach z suwakiem |
| Tryby świata | „dzisiejsza Polska” (pola, łąki, stawy) albo „roślinność naturalna”; proporcja las gospodarczy/naturalny suwakiem, domyślnie 85/15 |
| Rok | 12 podsezonów po 12 dni gry (144 dni), podsezon = miesiąc od marca do lutego |

## 2. Pionowa rama świata

Zmieniona 2026-09-27 po uwagach z gry: przy poziomie morza Y −470 wanilijne struktury podziemne generowały się w powietrzu.

| Parametr | Wartość | Uzasadnienie |
|---|---|---|
| `min_y` | −64 | jak w wanilii, więc struktury podziemne, rudy i jaskinie są na zwykłych wysokościach |
| `height` | 2096 | Y od −64 do 2031, czyli maksimum silnika (współrzędna Y ma 12 bitów) |
| Poziom morza | Y 63 | jak w wanilii; 0 m n.p.m. |
| Odwzorowanie wysokości | 1:1 do ok. 880 m, płynne przejście, powyżej 1200 m ściskanie 1,76 razy | Rysy (2499 m) wypadają na Y 2000, zostaje ok. 30 bloków nad szczytem |

Przykłady: Żuławy (−2 m) to Y 61, Warszawa (≈ 100 m) to Y 163, Babia Góra (1725 m) to Y ok. 1560, Śnieżka (1603 m) to Y ok. 1490. Model krajobrazu, biomy i piętra roślinności działają zawsze w prawdziwych metrach; odwzorowanie na bloki robi klasa `PolandDimension`.

## 3. Generator terenu

### 3.1 Decyzja: własny `ChunkGenerator` zamiast density functions w JSON

Raport 02 rekomendował density functions, ale zakładał świat z danych rastrowych i niski świat. Przy świecie proceduralnym o wysokości 3056 bloków wybieramy własny generator w Javie, bo:

1. **Koszt.** Wanilijny `NoiseChunkGenerator` liczy gęstość 3D w każdej komórce całej kolumny. Przy 3056 blokach to ok. 8× więcej pracy niż w wanilii. Polski relief jest w ogromnej większości 2,5D, więc wystarczy wysokość powierzchni per kolumna i proste wypełnienie. Sekcje całkowicie pełne lub puste zapisujemy jednym stanem palety.
2. **Woda na dowolnym poziomie.** Jeziora Mazur leżą na ok. 116 m, stawy tatrzańskie na 1400–2000 m. Wanilia ma jeden `sea_level` i akwifery; własny generator wypełnia wodą do lokalnego poziomu jeziora lub rzeki.
3. **Spójność modelu.** Ten sam model krajobrazu zasila teren, biomy, glebę, wilgotność i mapę drzewostanów. Kod jest testowalny poza grą: model można próbkować w testach i renderować do PNG.
4. **Formy 3D** (ostańce jurajskie, skałki, labirynty Gór Stołowych, nawisy klifów, jaskinie w wapieniu) dodajemy jako lokalne struktury i feature'y, a nie jako globalne pole 3D.

Koszt tej decyzji: brak wanilijnych jaskiń i akwiferów „za darmo”. To zgodne z przyrodą, bo w glinie i piasku jaskiń nie ma. Jaskinie krasowe dopisujemy osobno.

### 3.2 Warstwy modelu krajobrazu

Wszystko jest deterministyczne z ziarna świata i obliczalne lokalnie dla dowolnego punktu. Nie ma globalnego rozwiązywania, więc świat jest nieskończony, a generacja wielowątkowa.

| Warstwa | Skala | Zawartość |
|---|---|---|
| L0 strefy | 300–800 km | pola ciągłe: „górotwórczość” (pasma górskie jako grzbiety szumu), „zlodowacenie” (młodo- vs staroglacjalne), „kontynentalizm” (klimat zachód–wschód), bliskość morza |
| L1 makroregiony | 20–150 km (suwak) | komórki Voronoi z rozchwianej siatki; każda dostaje typ krajobrazu z L0 i reguł sąsiedztwa sprawdzanych tylko względem sąsiadów |
| L2 formy | 20 m – 5 km | generatory form per typ: wały morenowe, rynny z jeziorami, oczka, sandry ze spadkiem 1–3‰, ozy, drumliny, wydmy paraboliczne, wąwozy lessowe, doliny z terasami, grzbiety fliszowe, granie i kotły |
| L3 hydrologia | 256 m siatka | sieć rzeczna liczona w kaflach L1 z marginesem, jeziora z lokalnym poziomem wody, torfowiska w bezodpływowych misach |
| L4 podłoże i gleba | blok | litologia (glina zwałowa, piasek, less, mada, torf, wapień, flisz, granit) i profil glebowy |
| L5 siedlisko | 4 bloki | żyzność × wilgotność × piętro → siedliskowy typ lasu → biom |

Reguły sąsiedztwa L1, przykłady: sandr przylega do moreny czołowej od strony odpływu; pojezierze graniczy z pobrzeżem lub pradoliną; kotlina podgórska leży między wyżyną lub niziną a pogórzem; góry wysokie tylko wewnątrz pasma górskiego z pogórzem na obrzeżu; połoniny tylko w paśmie „wschodniokarpackim”.

Przejścia między makroregionami mieszamy w pasie kilku kilometrów, ważąc odległością do krawędzi komórki Voronoi.

### 3.3 Typy krajobrazu (wstępna lista, ok. 30)

Pobrzeża: plaża z wydmą białą i szarą, klif morenowy, mierzeja z zalewem, delta i depresja (Żuławy). Pojezierza: wysoczyzna morenowa falista, strefa moren czołowych, sandr, rynna z jeziorem, pole drumlinowe, pradolina. Niziny: równina staroglacjalna, wysoczyzna z ostańcami moren, pole wydm śródlądowych, dolina wielkiej rzeki, torfowisko niskie (typ Biebrza). Wyżyny: wyżyna lessowa z wąwozami, wyżyna krasowa z ostańcami (Jura), niecka gipsowa, niskie góry staroprzeglebione (Świętokrzyskie z gołoborzami), płaskowyż. Kotliny: kotlina podgórska, kotlina śródgórska. Karpaty: pogórze, Beskidy fliszowe, połoniny, Pieniny, Tatry (granity i wapienie, granie, kotły, piargi). Sudety: przedgórze, góry średnie krystaliczne (Karkonosze), góry stołowe. Szczegóły wymiarów form: raport 05.

### 3.4 Wydajność i narzędzia

- Pola L0–L3 buforowane w kaflach (LRU, bezpieczne wątkowo), próbkowane rzadko i interpolowane.
- Test wydajności przed rozbudową: liczba chunków na sekundę i KB na chunk przy wysokości 3056.
- Narzędzie deweloperskie renderujące mapę wysokości i typów krajobrazu do PNG z samego modelu, bez uruchamiania gry.
- Komenda `/polishforests here` podająca typ krajobrazu, makroregion, wysokość n.p.m., glebę i siedlisko w miejscu gracza.
- Zgodność z Distant Horizons do sprawdzenia, bo przy górach 2 km widok daleki ma duże znaczenie.

## 4. Biomy, gleby i piętra

- **Biom = grupa siedliskowa** (raport 06): ok. 16 biomów leśnych (bór suchy, bór świeży, bór wilgotny i bagienny, bór mieszany, las mieszany, grąd, buczyna niżowa, ols, łęg jesionowo-olszowy, łęg nadrzeczny, wyżynna jedlina i buczyna, buczyna górska, bór górski świerkowy, kosodrzewina, torfowisko wysokie, torfowisko niskie) oraz nieleśne (pola, łąki, murawy, wydmy, plaże, jeziora, rzeki, morze, hale, turnie, piargi).
- **Wariant = zespół roślinny i stan lasu**, wybierany regionem i szumem, nie osobnym biomem. Przykład: Leucobryo-Pinetum na zachodzie, Peucedano-Pinetum na wschodzie.
- **Własny `BiomeSource`** korzysta z modelu krajobrazu; biomy 3D dają piętra górskie według wysokości z progami per masyw (Tatry, Babia Góra, Karkonosze, Bieszczady).
  *Stan od M2 (krok S5, 2026-10-08):* biomy są kolumnowe (2,5D, decyzja M2-1): `PolandBiomeSource` bierze 36 biomów `polishforests:*` z rejestru i wybiera biom kolumny klasyfikatorem siedlisk (`worldgen/habitat/HabitatClassifier`, czysta funkcja próbki modelu i położenia); piętra górskie wynikają z wysokości gruntu w metrach. Ten sam klasyfikator liczy w `fill()` siedliska całego chunka (`ChunkHabitats`, nietrwały załącznik chunka), z których korzystają dyspozytory roślinności kroku 9 (`worldgen/feature`). Biomy, featury, tagi i lang generuje datagen z enumów pakietu `habitat` do `src/main/generated` (repozytorium; `./gradlew checkDatagen`). Szczegóły: `docs/03-m2-biomy.md` §3.5.1.
- **Granice zasięgów jako twarde reguły**: buk nie na północnym wschodzie, jodła tylko na południu, naturalny świerk tylko na północnym wschodzie i w górach, limba tylko w Tatrach.
- **Temperatura biomów 0,2–0,8**, aby Serene Seasons sezonował je poprawnie (raporty 01 i 08).
- **Podłoża**: ściółka iglasta, mieszana i bukowa, bielica, gleba rdzawa i brunatna, mada, torf niski i wysoki, less, glina zwałowa, rędzina, wapień, gips, flisz, granit.

## 5. Las

### 5.1 Mapa drzewostanów

Warstwa niezależna od biomu. Las gospodarczy dzieli się na oddziały 200–400 m z liniami oddziałowymi, a oddziały na wydzielenia 2–30 ha. Każde wydzielenie ma gatunek panujący, domieszki, wiek z rozkładu klas wieku, zwarcie i historię (zrąb, uprawa w rzędach, młodnik, drągowina, drzewostan dojrzały). Las naturalny jest wielowiekowy, z lukami, olbrzymami i dużą ilością martwego drewna.

### 5.2 Drzewa

- **Pnie o zmiennej grubości**: bloki pni i gałęzi o kilku średnicach w pikselach, pełny blok i układ 2×2 dla olbrzymów. Gałęzie łączą się z sąsiadami jak w modzie Dynamic Trees.
- **Generator proceduralny** z parametrami gatunku z raportu 06, sekcja 9: wysokość zależna od wieku i bonitacji, długość korony, pokrój, smukłość. Sosna w wieku 60 lat ma 18–26 bloków, stary buk 35–40.
- **Liście**: tekstura w skali szarości i własne kolory sezonowe per gatunek, stan bezlistny zimą, modrzew zrzuca igły.
- **Dynamika lasu**: wzrost i starzenie drzew, sukcesja na zrębach i lukach, wiatrołomy i gradacje kornika.

### 5.3 Piętra

Podszyt, runo z geofitami wiosennymi, warstwa mchów, ściółka z wanilijnym `leaf_litter` jako wzorem, martwe drewno (kłody w trzech stadiach rozkładu, pniaki, wykroty, złomy, posusz) według norm m³/ha z raportu 06, mrowiska, dziuple, huby.

### 5.4 Grzybobranie i owoce

Grzyby jadalne i trujące pojawiają się zależnie od gatunku drzewa w pobliżu (mikoryza), podsezonu, niedawnego deszczu i temperatury. Mają efekty po zjedzeniu. Owoce krzewinek i krzewów są stanami bloków zależnymi od pory roku.

## 6. Fauna

- **Modele**: GeckoLib; zasoby `.geo.json` i `.animation.json` generowane skryptem z parametrycznego opisu gatunku, dopracowywane w Blockbench.
- **AI**: SmartBrainLib (Brain) z harmonogramem dobowym i sezonowym oraz klasami cech: stado, wataha, dom, nurkowanie, wspinanie, lot szybujący, hibernacja.
- **Dane gatunków w JSON**: morfologia, paleta, profil AI, zagęszczenie na km², siedliska, kalendarz. Poprawki przyrodnicze nie wymagają zmian kodu.
- **Spawn w trzech warstwach**: populacja przy generacji chunka, własne spawnery per gildia z zagęszczeniami z raportu 07, domy jako bloki z BlockEntity (gawra, nora, żeremie, gniazdo) z rejestrem w `SavedData`.
- **Kolejność gatunków**: sarna jako gatunek testowy, potem dzik, jeleń, wilk, bóbr, bocian biały; dalej reszta T1 z raportu 07.

## 7. Pory roku

Zrobione w kamieniu milowym M0: interfejs `SeasonProvider`, własny kalendarz i most do Serene Seasons ładowany tylko przy obecności moda. Kolejne kroki: nasze biomy w tagach Serene Seasons, krzewy owocowe w tagach upraw, reakcje na zmianę podsezonu (opad liści, `leaf_litter`, poroże, futro, migracje).

## 8. Plan kamieni milowych

| # | Zakres | Kryterium ukończenia |
|---|---|---|
| M0 | Szkielet projektu, kalendarz, most do Serene Seasons | **zrobione 2026-09-27**: build, testy i ładowanie gry przechodzą |
| M1 | Rama świata i model krajobrazu v1 | **zrobione 2026-09-27**: world preset „Polska” (dziś `polishforests:poland`), wymiar 3056 bloków, własny generator, 5 krajobrazów prototypowych, podgląd PNG, test w kliencie; szczegóły w sekcji 10 |
| M2 | Biomy i gleby | `BiomeSource`, 16 biomów leśnych i biomy nieleśne, bloki podłoża |
| M3 | Drzewa | bloki pni o zmiennej grubości, generator 10 głównych gatunków, liście sezonowe, mapa drzewostanów |
| M4 | Piętra lasu | podszyt, runo, mchy, ściółka, martwe drewno, grzybobranie, fenologia |
| M5 | Pełna lista krajobrazów i hydrologia | ok. 30 typów, Tatry i Sudety; **sieć rzeczna, doliny, starorzecza i wybrzeże zrobione 2026-09-30** (sekcja 13) |
| M6 | Fauna: rdzeń | format danych gatunku, generator zasobów, framework AI, spawnery, domy; 6 pierwszych gatunków |
| M7 | Fauna: rozbudowa | pozostałe gatunki T1 i T2, ptaki w trzech warstwach, dźwięki |
| M8 | Tryby świata i dynamika | tryb rolniczy, infrastruktura leśna, gatunki obce, dynamika lasu |
| M9 | Dopracowanie | wydajność, zgodność z Sodium, Lithium i Distant Horizons, balans |

**Pionowy wycinek po M2 (decyzja P2, 2026-10-05).** Po zamknięciu M2, zanim M3–M7 poszerzą zakres na całą Polskę, powstaje jeden kompletny las, np. grąd i bór świeży: drzewa, runo, martwe drewno i kilka zwierząt. Wycinek bierze po kawałku z M3, M4 i M6 i sprawdza w grze, czy warstwy do siebie pasują; pozostałe siedliska i gatunki dochodzą potem w kolejnych kamieniach.

## 9. Struktura kodu

```
pl.polishforests
├── PolishForests               punkt wejścia
├── season/                     kalendarz, most do Serene Seasons
├── worldgen/
│   ├── landscape/              model krajobrazu L0–L5 (czysta Java, bez klas gry)
│   ├── habitat/                siedliska: 36 biomów, strefy, gleby, klasyfikator (czysta Java)
│   ├── chunk/                  ChunkGenerator, BiomeSource, ChunkHabitats, rejestracja
│   ├── surface/                podłoże i gleby
│   ├── tree/                   generator drzew, mapa drzewostanów
│   └── feature/                dyspozytory kroku 9 (drzewostan, runo, martwe drewno…), plany, palety
├── block/                      pnie, liście, runo, grzyby, gleby
├── entity/                     zwierzęta, AI, spawnery, domy
└── client/                     rendery, kolory, HUD, datagen (wynik w src/main/generated)
tools/                          skrypty Python: generator modeli i tekstur, podgląd map
```

Model krajobrazu nie zależy od klas Minecrafta. Dzięki temu da się go testować jednostkowo, renderować do PNG i profilować bez uruchamiania gry.

## 10. Stan po M1 (2026-09-27)

### Co działa

- Typ świata „Polska (przyrodniczo zgodne lasy)” (od M2-9 „Poland (1:1 Scale)”, `polishforests:poland`) na liście typów świata, z wymiarem Y −1024…2031 i poziomem morza Y −470.
- Model krajobrazu w czystej Javie (`worldgen/landscape`): pasma górskie i strefa zlodowacenia (L0), makroregiony o zawirowanych granicach z regułą „Beskidy zawsze przez pogórze” (L1), rzeźba pięciu typów (L2), jeziora rynnowe, oczka i doliny wielkich rzek z własnym poziomem lustra (L3).
- Generator chunków wypełniający kolumny bez gęstości 3D; źródło biomów z pamięcią próbki na kolumnę; mixin przenoszący horyzont nieba na poziom morza.
- Testy jednostkowe: determinizm, reguła sąsiedztwa, zakres wysokości, woda zawsze otoczona brzegiem, pochodne szumu.
- Podgląd PNG (`./gradlew landscapePreview`) i test w prawdziwym kliencie (`./gradlew runClientGameTest`) ze zrzutami ekranu.

### Pomiary

| Pomiar | Wynik |
|---|---|
| Sam model krajobrazu | ok. 0,2 ms na chunk (16 384 kolumny w 11–15 ms) |
| Pełna generacja chunka z dekoracjami i oświetleniem, jeden wątek | ok. 35–46 ms |
| Beskidy, wnętrze pasma | 572–1205 m n.p.m.; stoki 5–15°: 57%, 15–30°: 25% |

### Znane ograniczenia (do kolejnych kamieni milowych)

- **Biomy zastępcze.** Świat używa biomów wanilijnych jako zastępstwa, więc drzewa są wanilijne, a zimne biomy tajgi pokrywają się śniegiem wczesną wiosną Serene Seasons. Rozwiązanie w M2 i M3.
- **Temperatura a wysokość.** Wanilia obniża temperaturę biomu od stałego Y 80, czyli w naszym świecie od ok. 550 m n.p.m., i to zbyt szybko. W M2 potrzebny jest mixin liczący spadek temperatury od poziomu morza Y −470 z gradientem ok. 0,6 °C na 100 m.
- **Rzeki.** Doliny rzek są izoliniami szumu: nie wypływają z gór, kończą się u ich podnóża, a lustro wody zmienia się skokami o 1 m. Pełna hydrologia to M5.
- **Rzeźba gór.** Beskidy mają grzbiety wzdłuż pasma, doliny poprzeczne i żleby, ale nie mają prawdziwej, rozgałęzionej sieci rzecznej. Do dopracowania w M5.
- **Tarasy.** Łagodne stoki w skali 1:1 tworzą regularne stopnie jednoblokowe. Złagodzą je roślinność, a w przyszłości ewentualnie półbloki lub warstwy.

## 11. Zmiany po uwagach z gry (2026-09-27)

- **Rama pionowa** przeniesiona na poziom morza Y 63 (sekcja 2). Mixin przesuwający horyzont nieba nie jest już potrzebny i został usunięty.
- **Ekran opcji generowania** pod przyciskiem „Dostosuj” typu świata „Polska”: rozmiar regionów (10–200%), tryb krajobrazu, udział lasów gospodarczych, gatunki obce. Działa przez mixin do `WorldCreationUiState.getPresetEditor`, bo wanilia trzyma edytory w niezmiennej mapie. Na razie tylko rozmiar regionów zmienia teren; pozostałe opcje zapisują się w świecie.
- **Komendy:** `/polishforests find <target>` szuka w tle najbliższego typu krajobrazu lub elementu terenu (outwash_plain, moraine_plateau, old_glacial_plain, foothills, beskids, upper_montane, river_valley, river, lake, peatland) i zwraca klikalne współrzędne; wymaga uprawnień jak `/locate`. `/polishforests here` opisuje teren w miejscu gracza.
- **Wydajność:** wypełnianie terenu zapisuje sekcje jednolite paletą jednowartościową, a mieszane gotową paletą czterobitową; etap terenu jest ok. 13 razy szybszy. Pomiary i zalecana paczka modów są w `02-wydajnosc.md`.
- **Odległości przy rzeczywistych rozmiarach regionów:** dla ziarna testowego najbliższe Beskidy leżą ok. 820 km od startu. Kto chce mieć góry bliżej, zmniejsza suwak rozmiaru regionów; przy 25% odległości maleją mniej więcej czterokrotnie.

## 12. Skala przyjazna rozgrywce i rozszerzone komendy (2026-09-29)

### Dwie skale świata

| Cecha | Skala rzeczywista | Skala rozgrywki |
|---|---|---|
| Typ świata w menu | Poland (1:1 Scale), po polsku „Polska (skala 1:1)” (do M2-9 „Polska (przyrodniczo zgodne lasy)”) | Poland (Gameplay Scale), po polsku „Polska (skala rozgrywki)” |
| Makroregion | ok. 64 km | ok. 1,4 km, ok. 2 razy więcej niż typowy biom wanilijny |
| Formy średnie: doliny rzek, rynny, pasma moren, pola wydm | 1:1 | ok. 7 razy mniejsze |
| Formy lokalne: pagórki, oczka, jeziora w rynnach | 1:1 | o połowę mniejsze |
| Rozstaw grzbietów górskich | 1:1 | ok. 3 razy mniejszy |
| Wysokości | 1:1 do ok. 900 m, wyżej ściśnięte; Rysy Y 2000 | bloki = 1,89 · metry^0,742; nizina Y ok. 133, Babia Góra Y ok. 540, Rysy Y ok. 690 |
| Typ wymiaru | `polishforests:poland`, Y od −64 do 2031 | `polishforests:poland_gameplay`, Y od −64 do 767 |

Skala jest polem `scale` w ustawieniach generatora (`realistic` albo `gameplay`) i przełącznikiem na ekranie opcji. Przełącznik podmienia też typ wymiaru, bo niższy świat ma ok. 2,5 raza mniej sekcji w kolumnie, co odciąża renderowanie, światło i pamięć. Model liczy zawsze w metrach, więc piętra roślinności i komendy działają tak samo w obu skalach.

### Komendy

| Komenda | Działanie |
|---|---|
| `/polishforests find <target>` | 21 celów w czterech grupach: krajobrazy, piętra górskie, wody i mokradła, formy terenu |
| `/polishforests elevation <from> <to>` | najbliższy suchy teren o wysokości w podanym przedziale, w m n.p.m. |
| `/polishforests highest [radius_km]` | najwyższy punkt w okolicy, domyślnie 20 km, a w skali rozgrywki 3 km |
| `/polishforests list` | klikalna lista celów |
| `/polishforests here` | typ krajobrazu, wysokość, podłoże, wody i formy terenu pod graczem |

Formy terenu rozpoznaje metoda `LandscapeModel.describe`: wydmy, wały morenowe, rynny (także suche), jeziora rynnowe, oczka wodne i torfowe, rzeki, dna i zbocza dolin, grzbiety, szczyty, przełęcze, doliny górskie oraz regiel dolny i górny. Od M2 (krok S2) tanie formy (wydmy, wał, grzbiet, dolina górska, plaża, wydmy nadmorskie, klif, źródło) zapisuje już `sample` w polu `terrain.landformBits` próbki, razem z polami dla siedlisk (rekordy `ColumnSample.Terrain`, `Waters`, `Region`; docs/03-m2-biomy.md §3.1). `describe` liczy osobno tylko rynnę, szczyt, przełęcz i regiel.

### Poprawki modelu wykryte testami skali rozgrywki

- **Woda poza brzegiem.** Jeziora i oczka mogły wylewać się poza linię brzegu tam, gdzie teren za pierścieniem pomiaru był niższy od lustra. Woda powstaje teraz tylko wewnątrz linii brzegu, a pas 15 m za nią ma zawsze wał na poziomie lustra + 1 m.
- **Ucięte oczka.** Istnienie oczka decydowało się w każdej kolumnie osobno, więc na granicy regionów oczko mogło być ucięte. Teraz decydują warunki w jego środku (wynik jest buforowany na oczko), a strefa brzegu zawsze mieści się w komórce oczka.
- **Jeziora rynnowe przy dolinach rzek** wygasają płynnie zamiast ostrego progu.

## 13. Rzeki, doliny i morze (2026-09-30)

Etap wprowadzony po uwagach z gry: rzeki były zbyt proste, źródła urywały się klifem, a w świecie nie było morza.

Geometrię dolin, wód stojących i wybrzeża z tej sekcji przebudowała później poprawka geometrii terenu (M2-8, §15): opisy niżej to stan z 2026-09-30, a obecne elementy terenu są w §15.

### Sieć rzeczna (`RiverNetwork`)

- **Trzy rzędy cieków** na siatkach z przesunięciem węzłów: rzeki (rozstaw 20 km), rzeki średnie (5 km) i potoki (1,25 km); w skali rozgrywki odpowiednio mniej.
- **Spływ D8** do najniższego sąsiada według wygładzonej wysokości. Gdy żaden sąsiad nie jest niżej, szukany jest przełom w pierścieniach 2–4 oczek, a gdy i to zawiedzie, powstaje jezioro bezodpływowe. Dopływ, którego droga przecina koryto wyższego rzędu, uchodzi do niego (przechwycenie).
- **Poziomy wody** liczone od ujścia w górę biegu: nigdy nie rosną z biegiem rzeki, leżą poniżej najniższego gruntu na drodze cieku, a spadek jest ograniczony dla każdego rzędu. Test sprawdza brak cykli i brak wzrostu poziomu.
- **Dopływy** uchodzą w różnych miejscach wzdłuż koryta głównego, a nie w samym węźle, więc nie ma „gwiazd” zbiegających się rzek.

### Meandry i starorzecza

- **Krzywa Kinoshity** (`MeanderField`): kierunek koryta zmienia się jak θ0·sin(2πs) plus składowe skośności i spłaszczenia Parkera. Tworzy prawdziwe pętle z zaokrąglonymi, pochylonymi łukami.
- **Długość fali** ok. 11 szerokości koryta.
- **Krętość zależy od spadku przeliczonego na skalę rzeczywistą:** ok. 1,02 dla potoków górskich i do ok. 2,2 dla rzek nizinnych o spadku poniżej ok. 0,3 ‰. Amplituda i faza zmieniają się co kilka zakoli.
- **Tablica odległości.** Krzywa jest okresowa, więc odległość od koryta pochodzi z tablicy liczonej raz na 12 wartości θ0. Przy korycie jest liczona dokładnie od łamanej. Koszt to kilkadziesiąt ns.
- **Oś doliny i koryto są rozdzielone.** Dolina (dno i zbocza) biegnie od osi z łagodnymi zakolami i obejmuje cały pas meandrów. Koryto meandruje w jej dnie.
- **Rzutowanie na oś** sprawdza wszystkie lokalne minima odległości i uśrednia położenie wzdłuż cieku z wagą wyrazistości minimum. Dzięki temu poziom wody i szerokość są ciągłe także po wewnętrznej stronie łuku.
- **Starorzecza** to odcięte pętle meandrów: półksiężyce za łukiem koryta, na rzekach nizinnych o spadku do ok. 1,5 ‰. Każde ma stałe lustro, metr poniżej rzeki.

### Źródła i doliny

- **Głowica doliny.** Od źródła dno doliny wznosi się ku górze najwyżej z połową nachylenia zboczy. Dolina zamyka się więc zaokrąglonym lejem niezależnie od długości odcinka. Woda wypływa tam, gdzie dno zejdzie do lustra. Test `mountainStreamSourcesHaveNoCliffs` pilnuje, by suchy teren przy źródłach nie miał stopni powyżej 3 m na metr.
- **Zbocza dolin** mają nachylenie zależne od pasa (niziny, pogórze, góry) i szerokość zależną od głębokości wcięcia.
- **Kaskady.** Gdy ciek schodzi zboczem głębszej doliny innego cieku, jego lustro obniża się razem z terenem. Nie wisi wtedy nad dnem doliny między sztucznymi wałami.

### Morze i wybrzeże

- **Strefa morska** z pola fBm o fali 900 km, z łagodnymi łukami brzegu co kilkadziesiąt km i zafalowaniami co kilka km. Góry wygasają przy morzu, a pas polodowcowy się przy nim wzmacnia, jak na Pomorzu.
- **Pobrzeże:**
  - plaża ok. 60 m z gołego piasku (podłoże `BEACH_SAND`, także na białej wydmie przedniej);
  - klif tam, gdzie wysoczyzna dochodzi do morza;
  - na niskim brzegu wydma przednia;
  - miejscami zalew za mierzeją. Zalew zwęża się ku końcom i ma nieregularny brzeg od lądu.
- **Dno morza:** płycizna przybrzeżna z rewami (podwodnymi wałami), dalej szelf.
- **Ujścia.** Rzeki kończą się w morzu na poziomie 0 m.

### Komendy

`/polishforests find` ma teraz 31 celów w pięciu grupach. Nowe cele to morze, pobrzeże, potok, źródło, starorzecze, zalew, ujście, plaża, wydmy nadmorskie i klif. Obiekty wybrzeża są szukane dwuetapowo: najpierw zgrubnie po analitycznej odległości od linii brzegowej, potem gęsto przy znalezionym odcinku. Morze w skali rzeczywistej znajduje się w ułamku sekundy, choć bywa ok. 270 km od punktu startu. Test `PolishForestsCommandsTest` sprawdza, że każdy cel daje się znaleźć.

### Koszt

| Pomiar | Przed etapem | Po etapie |
|---|---|---|
| Próbkowanie kolumny | ok. 0,5–1 µs | 5–10 µs |
| Model na chunk, jeden wątek | ok. 0,2 ms | ok. 1,3–2,6 ms |
| Pełna generacja chunka z dekoracjami i oświetleniem (test w kliencie) | 35–46 ms | 44 ms |

Najdroższe są góry z gęstą siecią potoków. Pełną generację chunka nadal zdominowały wanilijne dekoracje i światło. Bufory sieci rzecznej są czyszczone po 250 tys. wpisów na mapę.

### Podglądy

Pliki w `docs/rzeki-i-morze/`:
- meandry i starorzecza wielkiej rzeki: `rzeka_nizinna_6km.png`;
- dolina rzeki na tle pogórza: `zoom_dolina_rzeki_40km.png`;
- potoki górskie ze źródłami: `potok_gorski_3km.png`;
- wybrzeże z zalewem: `zalew_20km.png`, `wybrzeze_6km.png`;
- skala rozgrywki: `rozgrywka_rzeka_2km.png`, `rozgrywka_wybrzeze_4km.png`;
- zrzuty z gry: `gra_plaza.png` (plaża i klif z morza), `gra_klif.png`.

Nazwy plików są sprzed M2-9. Nowe przebiegi `./gradlew landscapePreview` zapisują te podglądy jako `lowland_river_6km.png`, `zoom_river_valley_40km.png`, `mountain_stream_3km.png`, `lagoon_20km.png`, `coast_6km.png`, `gameplay_river_2km.png` i `gameplay_coast_4km.png`.

### Co dalej

Roślinność nadrzeczna (łęgi wierzbowo-topolowe, olsy, szuwary, ziołorośla) wymaga własnych biomów strefowanych od koryta (M2) i drzew (M3). Model podaje już do tego odległość od koryta, dno doliny i starorzecza.

## 14. Zmiana nazwy na „Polish Forests” (M2-9, 2026-10-03)

Decyzja M2-9: mod nazywa się „Polish Forests”, id `polishforests`, pakiet `pl.polishforests`. Kod i wszystko, co widzi gracz, są po angielsku, a polski zostaje jako tłumaczenie `pl_pl`. Dokumentacja zostaje po polsku. Zmieniły się identyfikatory generatora, typów wymiaru i presetów, więc światy utworzone wcześniej się nie wczytają (zgodnie z M2-7: bez migracji). Teren dla tego samego ziarna się nie zmienia (pilnuje tego złoty test).

Dawne nazwy, które mogą się pojawić w starszych notatkach, logach i w migawce `migawki/m1-z-narzedziami-S0.tar`:

| Dawniej | Od M2-9 |
|---|---|
| nazwa „Przyrodniczo zgodne lasy”, id `polskielasy`, pakiet `pl.polskielasy`, klasa `PolskieLasy` | „Polish Forests”, `polishforests`, `pl.polishforests`, `PolishForests` |
| `/polskielasy znajdz <cel>`, `lista`, `tutaj`, `wysokosc <od> <do>`, `najwyzszy [promien_km]` | `/polishforests find <target>`, `list`, `here`, `elevation <from> <to>`, `highest [radius_km]` |
| cele `sandr`, `morena`, `rownina`, `pogorze`, `beskidy`, `pobrzeze`, `regiel_dolny`, `regiel_gorny`, `jezioro_rynnowe`, `oczko`, `torfowisko`, `zrodlo`, `wydmy`, `wal_morenowy`… | `outwash_plain`, `moraine_plateau`, `old_glacial_plain`, `foothills`, `beskids`, `coastland`, `lower_montane`, `upper_montane`, `tunnel_valley_lake`, `kettle_pond`, `peatland`, `headwaters`, `inland_dunes`, `end_moraine`… (pełna lista w `README.md`) |
| typy świata `polskielasy:polska` („Polska (przyrodniczo zgodne lasy)”) i `polskielasy:polska_rozgrywka` | `polishforests:poland` („Poland (1:1 Scale)”) i `polishforests:poland_gameplay` („Poland (Gameplay Scale)”) |
| skala `realistyczna` / `rozgrywka` w presecie i `level.dat` | `realistic` / `gameplay` |
| klasy `PolskaChunkGenerator`, `PolskaBiomeSource`, `PolskaDimension`, `PolskaCommands` … | `PolandChunkGenerator`, `PolandBiomeSource`, `PolandDimension`, `PolishForestsCommands` … (klasy siedlisk: `docs/03-m2-biomy.md`, §11) |
| `-Pgametest=wszystko\|ui\|wydajnosc\|widoki\|etapy\|klimat` | `-Pgametest=all\|ui\|performance\|views\|stages\|climate` |
| `-Pmiejsca=plaza,klif` | `-Psites=beach,cliff` |
| `-PzlotyZapisz`, `-PkosztPrzebiegi`, `-Poptymalizacja`, `-Pprofil`, `-PtylkoSiedliska` | `-PwriteGolden`, `-PcostRuns`, `-Poptimization`, `-Pprofile`, `-PhabitatsOnly` |
| `-Dpolskielasy.gametest`, `.miejsca`, `.zloty.zapisz`, `.zloty.plik`, `.koszt.przebiegi` | `-Dpolishforests.gametest`, `.sites`, `.golden.write`, `.golden.file`, `.cost.runs` |
| `src/test/resources/zloty_teren_m1.txt`, `SampleKosztTest`, `PolskaCommandsTest` | `src/test/resources/golden_terrain_m1.txt`, `SampleCostTest`, `PolishForestsCommandsTest` |
| podglądy `przeglad_typy_400km.png`, `zoom_sandr_20km.png`, `rozgrywka_*`, `m2_<kadr>_biomy.png`, `_strefy.png`, `_trofia.png` | `overview_types_400km.png`, `zoom_outwash_plain_20km.png`, `gameplay_*`, `m2_<frame>_biomes.png`, `_zones.png`, `_fertility.png` |
| id biomów po polsku (np. `grad`, `ols`, `kosodrzewina`) | `oak_hornbeam_forest`, `alder_carr`, `dwarf_pine_scrub` (tabela w `docs/03-m2-biomy.md`, §2) |

Obrazy w `docs/m1`, `docs/m2`, `docs/rzeki-i-morze` i `docs/skala-rozgrywki` mają nazwy plików sprzed M2-9. Sole szumów w `derive("…")`, także polskie (`habitat.*`), zostały bez zmian, bo inna sól to inny świat.

## 15. Poprawka geometrii terenu (M2-8, 2026-10-03 – 2026-10-08)

Etap wprowadzony po uwagach z gry: proste krawędzie den dolin, starorzeczy i jezior, urwiska na stokach gór, szwy między makroregionami, wybrzeże bez wydm i za niskie Beskidy. Kroki K0–K7, decyzje, odstępstwa i pomiary przed i po są w `docs/m2/poprawka-geometrii.md` (podsumowanie po K7); krótka poprawka K8 po teście w grze (jeziora rynnowe GAMEPLAY, brzeg zalewu z deltami, zmienna wydma przednia i plaża, starorzecza ze szpicem przy obcym korycie, druga oktawa den nizinnych, przegenerowanie pliku wzorcowego w K8z) ma tam osobne podsumowanie na końcu. Model nadal liczy w metrach i jest czystą funkcją (ziarno, współrzędne, ustawienia).

**Mieszanie makroregionów (`LandscapeModel.blend`, A16).** Okno komórek ma 5 × 5 zamiast 3 × 3. Drugi pierścień jest czytany tylko tam, gdzie jakaś jego komórka może dostać wagę (dokładne dolne ograniczenie odległości), więc wynik jest dokładnie taki jak dla pełnego okna, a szwy `landElevation` na granicy okna znikają. Pamięć komórek to `DirectCache`.

**Wielkie masywy Beskidów (M2-8, K2).** Rzadkie masywy typu Babiej Góry: siatka kandydatów 60 km (REAL) i 4 km (GAMEPLAY), sól `mountain.great`, przerzedzenie (rozłączne zasięgi), eliptyczna kopuła wzdłuż pasma z wypełnianiem dolin fliszu i miękkim pułapem. Szczyty mają ok. 1620–1725 m w obu skalach (REAL: 9 masywów w oknie 1000 km, GAMEPLAY: 25 w oknie 600 km). Pole `Terrain.massif` jest maksimum z polem masywu (G). API: `greatMassifs(box)`, `nearestGreatMassif(x, z)`. Sieć rzeczna nie ma źródeł na wierzchowinie (G > 0,3) i omija ją przy wyborze odpływu. Kosodrzewina i hala (E12) są w obu skalach.

**Sieć rzeczna i doliny (`RiverNetwork`, K1, K3, K4).**
- **Dolina dominująca** (F1) to największy klucz „metry w głąb dna”. `floorHalfWidth` i `channelGradient` to miękkie maksimum po dolinach, `valleyWeight` maksimum po odcinkach, a `u` minimum po dnach zawierających kolumnę, więc nie skaczą na prostych przełączenia doliny. Koryto doliny dominującej (F2, pola `floorChannel*` w `ColumnSample.Waters`) służy strefom dna.
- **Pas meandrów** rośnie płynnie z udziałem nizin (TE).
- **Oś doliny** jest gładka w węzłach (G1B). Amplituda przechodzi przez węzeł płynnie (G2). Ujścia dopływów mają lejek (G3, w terenie i w polach siedlisk), skraj dna jest nieregularny (G4), a zbocza dwóch dolin łączy gładkie minimum (G5). Głowica doliny kończy się łukiem (A5).
- **Rzut na odcinek** liczy dokładne minima odległości od krzywej Hermite'a (pierwiastki wielomianu stopnia 5, bez próbkowania) i uśrednia ramiona z wagami wyrazistości. **Cięcie omiatające** (`sweepCut`, maksimum przekrojów doliny wzdłuż łamanej osi) wygrywa tylko przy remisach ramion (D4a), więc na stokach nie ma urwisk.
- **`RiverHit.floorGap` i `lakeGap`** to odległości od skraju doliny i od jeziora bezodpływowego, z których korzystają jeziora rynnowe.

**Wody stojące (K5).**
- Starorzecza to półksiężyce na łuku dawnej pętli Kinoshity, z zatkanymi końcami i jednym lustrem.
- Jeziora rynnowe kończą się przed doliną i przed jeziorem bezodpływowym, a poziom lustra liczy się z punktu kanonicznego. Teren nie zależy więc od kolejności generowania chunków (`TerrainDeterminismTest` jest ścisły).
- Oczka i jeziora bezodpływowe mają płatowe brzegi. Niecki przechodzą w teren bez ściany (A3), poza jeziorami bezodpływowymi u stóp wielkich masywów REAL (niżej, „Odłożone do M5”). Lustro oczka pochodzi z terenu po dolinach (A3c), a torf nigdy nie leży wyżej niż grunt wokół.

**Wybrzeże (K5b, D2, D5; `LandscapeModel.shapeCoast`).**
- Ok. 4/5 brzegu jest niskie: plaża zwykle 35–85 m·k (pole `Terrain.beachWidth`, K8b2), wydma przednia 6–15 m o grzbiecie zmiennym wzdłuż brzegu z siodłami, wydmy szare jako pagórki, zaplecze 1,5 m nad morzem do 6 km·meso od brzegu.
- Klif (ok. 1/5 brzegu) jest tylko tam, gdzie wysoczyzna morenowa dochodzi do morza.
- Zalew leży tylko za niskim brzegiem i nie ma rowu; jego wewnętrzny brzeg ma zatoki i cyple (szum `coast.lagoon.shore`), końce są zaokrąglone, a przy ujściach rzek leżą delty (`RiverNetwork.lagoonMouth`, `lagoonDelta`, tylko w `sample`; K8b1).
- Pole `Terrain.lowShore` to udział brzegu niskiego (1 − udział klifu).
- Niski brzeg jest w `landElevation`, więc obniża poziomy rzek spływających do morza (decyzja D5a; w GAMEPLAY zmienia doliny na ok. 20% lądu).

**Testy terenu.**
- `GoldenTerrainTest`: plik `golden_terrain_m1.txt` pilnuje zamrożonej kopii M1, a `golden_terrain_m2.txt` obecnego modelu. Plik M2 przegenerowano w K7 i w K8z. Zmiana terenu wymaga przegenerowania z `-PgoldenKeepCenters` i sprawdzenia `diff`; łaty kontrolne wnętrz nie mogą się zmienić.
- `SurfaceContinuityTest`: skoki, krok doliny ≤ 3 m na 1 m (D4) i ≤ 2 bloki na blok (D4b) w oknach obu skal, z gęstą siatką gór GAMEPLAY, poza wyjątkami z limitami stanu zmierzonego (remisy D4a w GAMEPLAY: krok doliny do 5,8 m na 1 m w gęstej siatce i do 3,84 m na transektach `gameplay_massif_spawn`; R5 w REAL do 2,75 bloku na blok); pas brzegowy 3 km·meso test tylko wypisuje (ściana klifu do ok. 2,4 bloku na blok).
- `TerrainLocalityTest`: teren poza dolinami i wodami jest równy kopii M1, poza zasięgiem masywów, drugim pierścieniem okna i pasem niskiego brzegu.
- `TerrainDeterminismTest`: wynik nie zależy od kolejności próbkowania.
- Testy wód stojących: `StandingWaterTest`, `StandingWaterContainmentTest`, `MassifSinkLakeContainmentTest` (rzeki przy jeziorach bezodpływowych masywów REAL, limity stanu zmierzonego).

**Koszt.** `sample` kosztuje 1,09–1,18 × kopia M1 po K8 (budżet D1: 1,20; REAL ok. 4,2 µs, GAMEPLAY ok. 6,1–6,3 µs na kolumnę w całym obszarze).

**Odłożone do M5.**
- Ściany rzutu w REAL do 2,75 bloku na blok w kilku miejscach (R5) i remisy D4a.
- Wały moren W–E (D3).
- Jeziora rynnowe GAMEPLAY: ściany niecek usunięte w K8a (wariant d), zostaje dalsze odzyskanie wody (−57% wobec K4c), suche rowy wzdłuż bocznych dolin i dwa płytkie zagłębienia 3,3–3,7 m.
- Wybrzeże po K8b: odnogi delt, wydmy paraboliczne, osobliwości `coastDistance` przy siodłach pola morza, stopnie plaży równoległe do brzegu na krótkich odcinkach.
- Końce wydm przy dnach dolin i końce zalewów.
- Jeziora bezodpływowe u stóp wielkich masywów REAL (recenzja K7): ściany niecek (siatka 5 m: masyw 1719 m 598 par > 25 m, do 38,2 m; 1698 m 548 par, do 75,6 m; 1659 m 62 pary, do 31,7 m) i rzeki na wałach nad suchym gruntem, które spadają do jeziora (do 24 m nad gruntem i 25 m spadku); limity stanu zmierzonego w `SurfaceContinuityTest` i `MassifSinkLakeContainmentTest`, szczegóły w `docs/m2/poprawka-geometrii.md`, „Co zostaje”.
- Proste załamania stoku na kopule masywu GAMEPLAY 1718 m (twarde maksimum kopuły i pola bazowego) i proste załamania w kadrach `R_potok_3km` i `G_rzeka_1km` (A8/G5, S8); żadne nie jest urwiskiem.
- Proste stopnie zboczy prostych odcinków dolin przy starorzeczach (obie skale), prosta krawędź W–E u ujścia doliny przy (1643, −452) GAMEPLAY i wąskie starorzecza wzdłuż meandrującego dopływu (K8c i runda 1 jego recenzji, `docs/m2/poprawka-geometrii.md`).
