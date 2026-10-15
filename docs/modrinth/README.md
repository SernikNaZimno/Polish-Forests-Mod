# Publikacja na Modrinth

Wersje publikuje przepływ GitHub Actions `Release` (`.github/workflows/release.yml`): buduje moda, uruchamia szybkie testy, wysyła plik `.jar` na Modrinth i tworzy wydanie na GitHubie. Lista zmian każdej wersji pochodzi z sekcji `## [wersja]` w `CHANGELOG.md`.

## Jednorazowe przygotowanie (robi właściciel konta)

1. **Projekt na Modrinth:** modrinth.com, „Create a project”, typ *Mod*, nazwa `Polish Forests`, slug `polish-forests` (wolny 2026-10-15), krótki opis z niżej.
2. **Strona projektu:**
   - opis: treść `docs/modrinth/description.md`;
   - ikona: `src/main/resources/assets/polishforests/icon.png` (512 px);
   - kategorie: *Worldgen* (główna), *Adventure*;
   - środowisko: klient *Required*, serwer *Required*;
   - licencja: *MIT* (w opisie jest zdanie o zasobach na CC BY 4.0);
   - linki: źródła i zgłoszenia błędów na GitHubie;
   - galeria: zrzuty z listy niżej.
3. **Token:** Modrinth, „Settings” → „Personal access tokens”, nowy token z uprawnieniem *Create versions* (wystarczy to jedno).
4. **GitHub:** repozytorium, „Settings” → „Secrets and variables” → „Actions”:
   - w zakładce *Secrets* nowy sekret `MODRINTH_TOKEN` z tokenem z punktu 3;
   - w zakładce *Variables* nowa zmienna `MODRINTH_PROJECT_ID` z identyfikatorem projektu (8 znaków, widoczny na stronie projektu Modrinth w menu z trzema kropkami, „Copy ID”).

Token trafia tylko do sekretów GitHuba; nie wklejaj go do czatu ani do plików repozytorium.

**Krótki opis (summary):** Procedural landscapes of Poland at 1:1 scale: moraine uplands, meandering rivers, the Baltic coast and the Beskids, with 36 phytosociologically accurate habitat biomes. Alpha.

## Wydanie wersji

1. W `CHANGELOG.md` sekcja `## [0.x.y-alpha.n]` z listą zmian po angielsku.
2. GitHub, zakładka „Actions” → „Release” → „Run workflow”: wersja (np. `0.2.0-alpha.1`) i kanał (*alpha*).
3. Pierwsza wersja nowego projektu czeka na zatwierdzenie przez moderatorów Modrinth; kolejne pojawiają się od razu.

Wydajemy tylko z commitów, które przeszły pełny zestaw testów i testy w grze, czyli przy punktach kontrolnych wycinka (CP-A … CP-D) albo po zamkniętym kroku. Commity „(WIP)” mogą mieć niedokończone bloki.

## Galeria (propozycja)

| Plik | Podpis |
|---|---|
| `docs/m2/punkt-kontrolny-2/oak_hornbeam_forest_realistic.png` | Oak-hornbeam forest (1:1 scale) |
| `docs/m2/punkt-kontrolny-2/lake_reedbed_realistic.png` | Reedbed and water lilies on a lake shore |
| `docs/m2/punkt-kontrolny-2/raised_bog_realistic.png` | Raised bog with peat moss and heather |
| `docs/m2/punkt-kontrolny-2/dwarf_pine_scrub_realistic.png` | Dwarf pine above the timberline |
| `docs/m2/gra/coastal_dunes_from_sea.png` | Baltic beach and foredune |
| `docs/m2/gra/oxbow_lake.png` | Oxbow lake in a river valley |
| `docs/m2/przejscia/gray_dune_coastal_pine_realistic_after.png` | Gray dune turning into crowberry pine forest |
| `docs/m2/przejscia/forest_field_present_day_edge_realistic_after.png` | Forest edge with shrubs and herbs (present-day mode) |
| `docs/m2/przejscia/spruce_dwarf_pine_realistic_after.png` | Gradual timberline in the Beskids |
