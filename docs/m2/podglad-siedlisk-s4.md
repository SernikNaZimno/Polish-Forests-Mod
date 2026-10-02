# Podgląd siedlisk M2 (krok S4, punkt kontrolny 1)

Data: 2026-10-02. Ziarno 20260927, tryb N („roślinność naturalna”), chyba że zaznaczono inaczej. Pliki powstały poleceniem `./gradlew landscapePreview -PtylkoSiedliska` (bez tej flagi zadanie liczy też dawne podglądy terenu). Klasy podglądu to `LandscapePreview` i `PodgladSiedlisk` w źródłach testów, plan w `docs/03-m2-biomy.md` (§12.2, stan po S4).

Każdy kadr ma 800 × 800 pikseli i legendę z prawej. W warstwie biomów legenda podaje udział każdego biomu w kadrze. Warstwa stref pokazuje strefy jaskrawymi kolorami, a kolumny bez strefy rozjaśnionym kolorem biomu. Cieniowanie rzeźby jest słabe, żeby kolory zostały czytelne.

| Plik `m2_…` | Kadr | Warstwy |
|---|---|---|
| `dolina_duzej_rzeki_2km` | dolina rzeki rzędu 3 (W ≥ 60 m) na nizinie, REAL | biomy, strefy, DGW, trofia |
| `rozgrywka_dolina_duzej_rzeki_1km` | to samo w skali rozgrywki | biomy, strefy |
| `mala_rzeka_500m` | mała rzeka na wysoczyźnie (koryto 4–15 m) | biomy, strefy |
| `potok_gorski_1km` | potok w Beskidach | biomy, strefy |
| `jezioro_rynnowe_1km` | brzeg jeziora rynnowego na glinie, z dala od dolin | biomy, strefy, DGW, trofia |
| `oczko_sandr_500m` | oczko wytopiskowe na sandrze | biomy, strefy |
| `wybrzeze_zalew_3km` | zalew za mierzeją, plaża, wydmy, bór bażynowy | biomy, strefy |
| `beskidy_10km` | wnętrze Beskidów (regiel dolny) | biomy, strefy, DGW, trofia |
| `beskidy_wysokie_10km` | najwyższy masyw w promieniu 1500 km (1445 m): regiel górny | biomy, strefy |
| `sandr_20km`, `wysoczyzna_20km` | wnętrza typów krajobrazu | biomy, strefy, DGW, trofia |
| `rozgrywka_20km` | środek świata w skali rozgrywki | biomy, strefy |
| `maska_lasu_D_50km_biomy` | tryb D „dzisiejsza Polska” na sandrze; P_las startowe, kalibracja w S8 | biomy |
| `mapa_O_2000km`, `mapa_P_2000km`, `mapa_zasiegow_2000km` | pola regionalne i zasięgi buka, jodły i świerka (zachód = −X) | – |
| `przekroje_dolin` | przekroje klas A, B, C w poprzek koryta: biom (góra), strefa (dół), teren (linia) | – |

Pliki CSV:
- `m2_udzialy_biomow.csv`: udział każdego biomu we wszystkich kolumnach (z wodą) i na sandrze, REAL i GAMEPLAY, 200 tys. kolumn na skalę z 3 ziaren (jak `BiomeSharesTest`). Na końcu lesistość lądu i udział borów w lesie wnętrza sandru.
- `m2_udzialy_stref.csv`: udział każdej strefy.

Znane ograniczenia widoczne na obrazach:
- Proste krawędzie den dolin, wycinki pierścieni starorzeczy i proste odcinki brzegów niektórych jezior pochodzą z geometrii modelu krajobrazu M1/S2 (np. dno doliny dominującej, starorzecze jako wycinek pierścienia). Klasyfikator ich nie tworzy, ale ich nie ukryje. Teren musi zostać bez zmian (złoty test).
- Kosodrzewiny i hali nie ma w żadnym kadrze: najwyższy szczyt w pobliżu środka świata ma 1445 m, a duży masyw wymaga szczytu ponad 1470 m (E12).
- Strefy węższe niż piksel kadru (np. szuwar lądowy 2–10 m na kadrze 20 km) widać tylko w kadrach 500 m–2 km.
