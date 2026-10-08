# Podgląd siedlisk M2 (punkt kontrolny 1, przeliczony w etapie H i w K8z)

Data: 2026-10-08 (przeliczony w K8z po krótkiej poprawce terenu K8; wcześniej 2026-10-07, etap H, poprawki klasyfikatora siedlisk po poprawce geometrii terenu; pierwsza wersja 2026-10-02, krok S4). Ziarno 20260927, tryb N („roślinność naturalna”), chyba że zaznaczono inaczej. Pliki powstały poleceniem `./gradlew landscapePreview -PhabitatsOnly` (bez tej flagi zadanie liczy też dawne podglądy terenu) i są skopiowane z `build/preview` do `docs/m2`. Klasy podglądu to `LandscapePreview` i `HabitatPreview` w źródłach testów, plan w `docs/03-m2-biomy.md` (§12.2; zmiany etapu H w §3.4, „Stan po poprawce geometrii”).

Każdy kadr ma 800 × 800 pikseli i legendę z prawej. W warstwie biomów legenda podaje udział każdego biomu w kadrze. Warstwa stref pokazuje strefy jaskrawymi kolorami, a kolumny bez strefy rozjaśnionym kolorem biomu. Cieniowanie rzeźby jest słabe, żeby kolory zostały czytelne.

Pliki mają nazwy `m2_<kadr>_<warstwa>.png`, warstwy `biomes`, `zones`, `dgw` i `fertility` (trofia). Pliki sprzed zmiany nazwy (M2-9, polskie nazwy `_biomy`, `_strefy`, `_trofia`) usunęliśmy w etapie H.

| Kadr (`m2_…`) | Opis | Warstwy |
|---|---|---|
| `large_river_valley_2km` | dolina rzeki rzędu 3 (W ≥ 60 m) na nizinie, REAL; starorzecza jako półksiężyce | biomy, strefy, DGW, trofia |
| `gameplay_large_river_valley_1km` | to samo w skali rozgrywki | biomy, strefy |
| `small_river_500m` | mała rzeka na wysoczyźnie (koryto 4–15 m) | biomy, strefy |
| `mountain_stream_1km` | potok w Beskidach; pasy łęgu z olszą szarą zwężają się klinem wzdłuż potoku (etap H) | biomy, strefy |
| `gameplay_beskids_confluence_300m` | zbieg potoków w Beskidach GAMEPLAY (27990, 3660), lejek ujścia G3; nowy w etapie H (dawny kadr kontrolny `Z_besk_conf_300m`) | biomy, strefy |
| `tunnel_valley_lake_1km` | brzeg jeziora rynnowego na glinie, z dala od dolin | biomy, strefy, DGW, trofia |
| `kettle_outwash_plain_500m` | oczko wytopiskowe na sandrze | biomy, strefy |
| `coast_lagoon_3km` | zalew za mierzeją, plaża, wydmy, bór bażynowy, REAL | biomy, strefy |
| `coast_40km` | przegląd wybrzeża 40 km wokół kadru zalewu, REAL; nowy w etapie H | biomy, strefy |
| `gameplay_coast_lagoon_3km`, `gameplay_coast_40km` | zalew i przegląd brzegu w skali rozgrywki (środek (605, 10983)); nowe w etapie H | biomy, strefy |
| `beskids_10km` | wnętrze Beskidów (regiel dolny) | biomy, strefy, DGW, trofia |
| `high_beskids_10km` | najwyższy teren w promieniu 1500 km (zgrubne wyszukiwanie): wielki masyw 1668 m z reglem górnym, kosodrzewiną i halą | biomy, strefy |
| `great_massif_16km`, `gameplay_great_massif_5km` | wielki masyw najbliższy (0, 0) w obu skalach (REAL 1659 m, GAMEPLAY 1637 m) | biomy, strefy |
| `highest_great_massif_16km` | najwyższy wielki masyw okna testu REAL (1723 m) | biomy, strefy |
| `gameplay_highest_great_massif_5km` | najwyższy wielki masyw GAMEPLAY (1719 m, (−217490, 246246)): kosodrzewina i hala w skali rozgrywki; nowy w etapie H | biomy, strefy |
| `outwash_plain_20km`, `moraine_plateau_20km` | wnętrza typów krajobrazu | biomy, strefy, DGW, trofia |
| `gameplay_20km` | środek świata w skali rozgrywki | biomy, strefy |
| `forest_mask_present_day_50km` | tryb D „dzisiejsza Polska” na sandrze; P_las startowe, kalibracja w S8 | biomy |
| `map_O_2000km`, `map_P_2000km`, `map_species_ranges_2000km` | pola regionalne i zasięgi buka, jodły i świerka (zachód = −X) | – |
| `valley_cross_sections` | przekroje klas A, B, C w poprzek koryta: biom (góra), strefa (dół), teren (linia) | – |

Pliki CSV:
- `m2_biome_shares.csv`: udział każdego biomu we wszystkich kolumnach (z wodą) i na sandrze, REAL i GAMEPLAY, 200 tys. kolumn na skalę z 3 ziaren (jak `BiomeSharesTest`). Na końcu lesistość lądu i udział borów w lesie wnętrza sandru (waga typu ≥ 0,9) i całego typu `OUTWASH_PLAIN` (z pasami mieszania; w GAMEPLAY poniżej 85%, do decyzji).
- `m2_zone_shares.csv`: udział każdej strefy.
- Zmiany udziałów w etapie H (po rundzie 1 recenzji; REAL / GAMEPLAY, procent wszystkich kolumn): buczyna niżowa 3,71 / 4,93 → 4,17 / 3,88; las mieszany 11,67 / 10,75 → 11,48 / 11,12; grąd 23,91 / 22,50 → 23,62 / 23,07; biała wydma 0,016 / 0,326 → 0,016 / 0,411, szara 0,014 / 0,352 → 0,014 / 0,516; ols w GAMEPLAY 1,91 → 1,94; strefy GAMEPLAY: wiklina 0,663 → 0,471, źródliska 0,360 → 0,218 (bez stref wzdłuż suchych odcinków koryt). Bory we wnętrzu sandru 88,5% (REAL) i 86,6% (GAMEPLAY).
- Zmiany udziałów w K8z (teren K8: jeziora rynnowe GAMEPLAY, brzeg zalewu z deltami, zmienna wydma przednia i plaża, starorzecza ze szpicem, druga oktawa den; REAL / GAMEPLAY): zalew 0,045 / 0,138 → 0,034 / 0,155; szara wydma 0,014 / 0,516 → 0,015 / 0,564; biała 0,016 / 0,411 → 0,017 / 0,412; plaża 0,010 / 0,277 → 0,010 / 0,272; bór bażynowy 0,044 / 1,456 → 0,043 / 1,479; szuwar 0,206 / 0,209 → 0,202 / 0,176; torfowisko niskie 0,020 / 0,099 → 0,019 / 0,066; jezioro 0,636 / 0,328 → 0,669 / 0,382, dystroficzne GAMEPLAY 0,028 → 0,045; lasy prawie bez zmian (bory we wnętrzu sandru 88,5 / 86,6%). Szczegóły: `docs/m2/poprawka-geometrii.md`, „K8z”.

Co widać po etapie H (i po K8z):
- **Zbiegi potoków** (`gameplay_beskids_confluence_300m`, `mountain_stream_1km`): dno lejka ujścia ma zaokrąglone płaty łęgu z olszą szarą zamiast wieloboków o prostych krawędziach i ostrych narożnikach (wysokość nad wodą od miękkiego poziomu koryt); olsu prawie nie ma (0,10% kadru zbiegu).
- **Granice biomów strefowych** (`moraine_plateau_20km`, `tunnel_valley_lake_1km`): progi żyzności i wariantu buczyny drgają szumem o fali 150 m·k, więc granice nie są równoległymi izoliniami ani łukami kół; płaty buczyny niżowej są zwarte (bez koronkowej tekstury siatki terenu i bez wysepek przy granicy zasięgu buka), a na płaskim zapleczu wybrzeża krawędź buczyny nie jest już prostą równoległą do brzegu (drganie granicy DGW 2 m).
- **Suche odcinki koryt** (Beskidy, kadry masywów): bez pasów wikliny, ziołorośli i łęgów wzdłuż koryt, których dolina nie wcina.
- **Wybrzeże** (`coast_lagoon_3km`, `coast_40km`, `gameplay_coast_*`): brzeg niski z plażą, białą i szarą wydmą na ok. 4/5 długości, także w poprzek den dolin poza samym ujściem, do falistego końca pasa wydm (za nim dno ma strefy cieku); klif tylko na brzegu wysokim; za mierzeją zalew, pas trzciny, torfowisko niskie i ols zaplecza (poza dnami dolin).
- **Po K8z** (`coast_lagoon_3km`, `gameplay_coast_lagoon_3km`, `large_river_valley_2km`): wewnętrzny brzeg zalewu z zatoką, cyplem i wysepką, płat delty przy ujściu z siedliskami lądowymi i pasem szuwaru; plaża zmiennej szerokości i pagórki wydm szarych w GAMEPLAY; starorzecza kończą się szpicem, także przy dopływie.
- **Masywy**: świerczyna → strefa granicy lasu → kosodrzewina → hala w obu skalach (w kadrze `gameplay_highest_great_massif_5km` kosodrzewina 3,1%, hala 0,6%).

Znane ograniczenia widoczne na obrazach:
- Proste krawędzie płatów tam, gdzie prosty jest sam teren lub podłoże modelu: głowica doliny potoku na sandrze jako czworokąt mady (`gameplay_large_river_valley_1km`, płat grądu przy źródle), granice pięter i ekspozycji na prostych zboczach dolin Beskidów (`beskids_10km`), dna dolin na zacisku terenu brzegu 2,000 m (bór wilgotny zamiast łęgu do linii, gdzie dno wychodzi ponad zacisk; `coast_lagoon_3km`), wewnętrzna krawędź pasa olsu zaplecza zalewu i granice boru bażynowego na zmianie typu próbki (piasek / glina). Wszystkie były już w bazie przed etapem H (`docs/03-m2-biomy.md` §3.4, „Zostaje”); końce wydm przy dnach dolin w samym terenie to M5 (`docs/m2/poprawka-geometrii.md`, „Co zostaje”).
- W kadrach 20 i 40 km „morze” GAMEPLAY to zamknięte baseny (makroregion 1,4 km), a pas brzegu (plaża, wydmy) ma szerokość kilku pikseli.
- Strefy węższe niż piksel kadru (np. szuwar lądowy 2–10 m na kadrze 20 km) widać tylko w kadrach 300 m–2 km.
