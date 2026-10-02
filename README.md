# Przyrodniczo zgodne lasy

Mod do Minecrafta 26.3 (Fabric), który generuje proceduralne krajobrazy Polski w skali 1:1 z przyrodniczo wiernymi lasami, florą i fauną oraz integracją z porami roku Serene Seasons.

Stan: kamień milowy M1 (rama świata i model krajobrazu) z poprawkami po pierwszych testach w grze. Plan i decyzje są w katalogu `docs`.

## Dokumentacja

| Plik | Zawartość |
|---|---|
| `docs/00-decyzje-do-podjecia.md` | decyzje projektowe, podjęte i otwarte |
| `docs/01-architektura.md` | architektura i kamienie milowe |
| `docs/02-wydajnosc.md` | pomiary wydajności, zalecana paczka modów i ustawienia |
| `docs/research/` | raporty badawcze: geografia, lasy, fauna, klimat, technika |
| `docs/m1/` | podglądy modelu krajobrazu i zrzuty ekranu z gry po M1 |

## Wymagania

- JDK 25 lub nowszy (projekt kompiluje się z `--release 25`, np. zainstalowanym JDK 26).
- Gradle nie jest potrzebny, bo projekt zawiera wrapper.

## Polecenia

Wszystkie polecenia uruchamia się w katalogu projektu. Gradle sam używa JDK wskazanego w `gradle.properties` przez `org.gradle.java.home`, niezależnie od domyślnej Javy w systemie. Na innym komputerze trzeba tam wpisać ścieżkę do własnego JDK 25 lub nowszego.

```bash
./gradlew build
```

Kompiluje mod i uruchamia testy jednostkowe modelu krajobrazu. Gotowy plik jest w `build/libs`.

```bash
./gradlew landscapePreview
```

Renderuje model krajobrazu do `build/preview/*.png` bez uruchamiania gry i wypisuje statystyki nachyleń gór.

```bash
./gradlew runClientGameTest -Pgametest=wszystko
```

Uruchamia prawdziwego klienta i testy w grze, a zrzuty ekranu zapisuje do `build/run/clientGameTest/screenshots`. Zamiast `wszystko` można podać `ui` (ekran opcji i komendy), `wydajnosc` (porównanie z wanilią) albo `widoki` (przegląd krajobrazów). Dopisek `-Pmiejsca=plaza,klif` ogranicza widoki do wybranych miejsc (nazwy w `PolskaWorldClientGameTest`).

```bash
python tools/build_mrpack.py
```

Po `./gradlew build` buduje zalecaną paczkę modów `.mrpack` w `build/distributions`.

```bash
./gradlew runClient
```

Uruchamia grę w trybie deweloperskim. Z flagą `-Poptymalizacja` dołącza mody z zalecanej paczki.

## W grze

Świat tworzy się na zakładce „Świat”. Do wyboru są dwa typy świata:

| Typ świata | Krajobrazy | Wysokości | Dla kogo |
|---|---|---|---|
| Polska (przyrodniczo zgodne lasy) | rzeczywiste, makroregiony ok. 64 km | 1:1 do ok. 900 m, Rysy na Y 2000 | wierność i dalekie wyprawy |
| Polska (skala rozgrywki) | ok. 1,4 km, ok. 2 razy więcej niż biom wanilijny | obniżone proporcjonalnie, Rysy na Y ok. 690 | zwykła gra, słabszy komputer |

Przycisk „Dostosuj” otwiera opcje generowania: skalę, rozmiar regionów, tryb krajobrazu, udział lasów gospodarczych i gatunki obce.

| Komenda | Działanie |
|---|---|
| `/polskielasy znajdz <cel>` | najbliższy krajobraz lub forma terenu z klikalnymi współrzędnymi |
| `/polskielasy lista` | klikalna lista 31 celów w pięciu grupach |
| `/polskielasy wysokosc <od> <do>` | najbliższy teren o wysokości w przedziale, w m n.p.m. |
| `/polskielasy najwyzszy [promien_km]` | najwyższy punkt w okolicy |
| `/polskielasy tutaj` | krajobraz, wysokość, podłoże, wody i formy terenu pod graczem |

Cele:
- krajobrazy: sandr, morena, rownina, pogorze, beskidy, morze, pobrzeze;
- piętra górskie: regiel_dolny, regiel_gorny;
- wody i mokradła: rzeka, jezioro, jezioro_rynnowe, oczko, torfowisko, potok, zrodlo, starorzecze, zalew, ujscie;
- wybrzeże morskie: plaza, wydmy_nadmorskie, klif;
- formy terenu: dolina_rzeki, zbocze_doliny, rynna, wydmy, wal_morenowy, grzbiet, szczyt, przelecz, dolina_gorska.

Rzeki meandrują zależnie od spadku terenu, zostawiają starorzecza i uchodzą do morza. Wybrzeże ma plaże, klify, wydmy i zalewy.

Informacje o krajobrazie są też na ekranie F3.

## Zależności

| Mod | Wersja | Status |
|---|---|---|
| Fabric API | 0.161.0+26.3 | wymagany |
| GeckoLib | 5.5.7 | wymagany |
| SmartBrainLib | 2.0.2 | wymagany |
| Serene Seasons | 26.1.2.0.7 | opcjonalny, bez niego działa własny kalendarz |
