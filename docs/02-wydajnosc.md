# Wydajność i zalecana paczka modów

Stan: 2026-09-29. Pomiary z testów w grze (`./gradlew runClientGameTest -Pgametest=wydajnosc`), to samo ziarno, zasięg widzenia 12 chunków, okno 854 × 480, kamera 30 bloków nad lasem 4,2 km od startu.

## Skąd bierze się obciążenie

- **Renderowanie wysokiego świata.** Wanilijny renderer co klatkę przegląda sekcje 16 × 16 × 16 w zasięgu widzenia, także puste sekcje powietrza. Świat w skali rzeczywistej ma 131 sekcji w kolumnie, w skali rozgrywki 52, a wanilijny 24. Sodium zastępuje ten fragment renderera i usuwa większość kosztu.
- **Generacja terenu.** Nasz model krajobrazu kosztuje ok. 1,3–2,6 ms na chunk (od etapu rzek, dolin i morza; wcześniej ok. 0,5 ms), a wypełnianie bloków 1,5–3 ms. Resztę czasu zajmują wanilijne dekoracje, czyli drzewa, rudy i geody z biomów zastępczych, oraz obliczanie światła. Dekoracje znikną lub zmienią się w M2, gdy świat dostanie własne biomy.

## Pomiary

FPS zależą od obciążenia komputera w danej chwili, więc porównywać należy tylko liczby z jednego przebiegu.

| Przebieg | Świat | Wczytanie nowego terenu | FPS średnio |
|---|---|---|---|
| bez modów optymalizacyjnych | wanilia | 6,1 s | 1752 |
| bez modów optymalizacyjnych | Polska, skala rzeczywista | 15,5 s | 1708 |
| bez modów optymalizacyjnych | Polska, skala rozgrywki | 14,3 s | 1448 |
| z zalecaną paczką | wanilia | 2,0 s | 1529 |
| z zalecaną paczką | Polska, skala rzeczywista | 6,8 s | 1439 |
| z zalecaną paczką | Polska, skala rozgrywki | 4,2 s | 1261 |

Wnioski:
- **Paczka przyspiesza wczytywanie nowego terenu 2,3–3,4 raza.** Najwięcej daje C2ME, który rozkłada generację na wszystkie rdzenie.
- **Skala rozgrywki wczytuje się szybciej** niż rzeczywista, bo świat jest niższy i ma mniej sekcji do wygenerowania i oświetlenia.
- **FPS w skali rozgrywki wyszły niższe.** Kamera w tym trybie widzi więcej drzew i rzeźby, bo krajobrazy są gęściej upakowane, więc to nie jest porównanie tej samej sceny. Przy Sodium wysokość świata ma już niewielki wpływ na FPS.

### Zmiany w samym modzie

| Zmiana | Efekt |
|---|---|
| Skala rozgrywki z niższym wymiarem (Y do 767 zamiast 2031) | ok. 2,5 raza mniej sekcji w kolumnie: szybsze wczytywanie, mniej pamięci i światła |
| Sekcje jednolite zapisywane jedną wartością, mieszane pakowane od razu do palety | etap terenu ok. 13 razy szybszy niż w pierwszej wersji |
| Decyzja o istnieniu oczka liczona raz na oczko i buforowana | mniej obliczeń w strefach oczek |
| Komendy szukające terenu działają w tle | serwer nie zacina się podczas wyszukiwania |

## Zalecana paczka modów

Plik `.mrpack` buduje skrypt:

```bash
python tools/build_mrpack.py
```

Wynik trafia do `build/distributions`. Paczkę importuje się w Modrinth App, Prism Launcher lub ATLauncher. Przeszukano wszystkie 369 modów z kategorii optymalizacji w Modrinth, które mają wersję Fabric na 26.3. Wybrano te, które realnie pomagają przy tym modzie, nie dublują się i nie kolidują ze sobą.

| Mod | Rola | Strona |
|---|---|---|
| Sodium 0.9.2 | szybki renderer, kluczowy przy wysokim świecie | klient |
| Sodium Extra | dodatkowe ustawienia wydajności grafiki | klient |
| Lithium | optymalizacja logiki gry i serwera | obie |
| FerriteCore | mniejsze zużycie pamięci | obie |
| ModernFix | szybsze uruchamianie i mniej pamięci | obie |
| C2ME | wielowątkowa generacja i zapis chunków | obie |
| ScalableLux | szybsze obliczanie światła | obie |
| Chunky | pregeneracja świata z wyprzedzeniem | obie |
| Ksyxis | szybsze wczytywanie świata bez stałych chunków spawnu | obie |
| Structure Layout Optimizer | szybsza generacja wiosek i innych struktur | obie |
| zFastNoise | szybsza generacja Netheru i Endu | obie |
| Packet Fixer | większe limity pakietów, potrzebne przy wysokich chunkach | obie |
| Debugify | poprawki błędów gry wpływających na wydajność | obie |
| Alternate Current | wydajniejszy redstone | obie |
| AsyncLogger | zapis logów w tle | obie |
| Clumps | łączenie kul doświadczenia | obie |
| ImmediatelyFast | szybsze rysowanie interfejsu i encji | klient |
| EntityCulling | pomija niewidoczne encje, ważne, gdy dojdą zwierzęta | klient |
| MoreCulling | pomija niewidoczne ściany bloków, np. liści | klient |
| Better Block Entities | szybsze skrzynie, tabliczki i inne bloki z encją | klient |
| AsyncParticles | cząsteczki liczone poza głównym wątkiem | klient |
| BadOptimizations | drobne optymalizacje klienta | klient |
| Dynamic FPS | mniej klatek, gdy okno jest w tle | klient |
| FastQuit | zapis świata w tle po wyjściu do menu | klient |
| RRLS | przeładowanie zasobów w tle | klient |
| Force Close Loading Screen | krótszy ekran wczytywania świata | klient |
| Nvidium | szybsze renderowanie dużego zasięgu, tylko karty NVIDIA | klient, opcjonalny |
| Distant Horizons | daleki widok gór | klient, opcjonalny |

Biblioteki: Fabric API, GeckoLib, SmartBrainLib, Serene Seasons z GlitchCore, Cloth Config, Resourceful Config, zConfig, Mod Menu z Placeholder API.

### Testy zgodności

Razem z naszym modem uruchomiono w grze wszystkie mody z tabeli oprócz Dynamic FPS, Mod Menu, Placeholder API i Distant Horizons. Test objął trzy światy i nie było awarii.

### Odrzucone

| Mod | Powód |
|---|---|
| Smooth Join | obniża zasięg widzenia do 2 chunków przy wejściu do świata i w teście nie podniósł go przez 4 minuty |
| Moonrise | niezgodny z C2ME, a C2ME daje więcej przy generacji |
| Sodium 0.9.3 (alfa) | zastąpiony stabilnym 0.9.2, którego wymaga też Nvidium |
| zFastSurface, Noisium i podobne | przyspieszają tylko wanilijny generator, którego świat Polska nie używa |
| ServerCore, VMP | przeznaczone dla serwerów z wieloma graczami |
| Cull Fewer Leaves, OptiLeaves | dublują MoreCulling |
| LazyAI i podobne | zmieniają zachowanie mobów; wrócimy do nich przy zwierzętach (M6) |

ModernFix w wersji oryginalnej i Krypton nie mają wersji na 26.3.

## Zalecane ustawienia

- Skala rozgrywki na słabszym komputerze.
- Zasięg widzenia 10–12 chunków i zasięg symulacji 6–8.
- Pregeneracja okolicy przed grą przez Chunky, np. `/chunky radius 2000` i `/chunky start`. Potem wędrówka nie obciąża procesora generacją.
- Distant Horizons zamiast dużego zasięgu widzenia, jeśli chcesz widzieć góry z daleka.

## Środowisko deweloperskie

```bash
./gradlew runClient -Poptymalizacja
```

Flaga `-Poptymalizacja` dołącza mody z paczki do gry uruchamianej z projektu. Flaga `-Pprofil` przy testach w grze nagrywa profil JFR do `build/profil.jfr`.
