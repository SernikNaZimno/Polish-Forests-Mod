# Pionowy wycinek: minimalna fauna i grafika bez grafika (raport badawczy)

Data: 2026-10-14. Zakres: (1) 3–5 gatunków fauny dla wycinka „grąd + bór świeży” oraz technika GeckoLib 5.x i SmartBrainLib 2.x na 26.3 (wersje, modele, animacje, spawny); (2) tekstury bloków i modele zwierząt bez grafika (proceduralnie w Pythonie/PIL, generator `.geo.json`), ograniczenia jakości, licencje. Repozytorium bez zmian. Prototypy powstały tylko w scratchpadzie (§9).

Metoda: dokumenty projektu (`docs/BRIEF.md`, `docs/00`, `docs/01` §6 i §8, `docs/03-m2-biomy.md` §7.4, §8.4–8.6, §9, §10, raporty `docs/research/03`, `04`, `06`, `07`); sprawdzenie API w jarach z pamięci podręcznej Gradle (`javap`: GeckoLib 5.5.7, SmartBrainLib 2.0.2, `minecraft-common.jar` 26.3, Fabric API `object-builder` 24.1.9, `lifecycle-events` 4.1.9); API Modrinth (wersje na 2026-10-14); kilka wyszukiwań o licencjach; dwa prototypy (tekstury 16 × 16 w dwóch rundach i generator modelu sarny).

## Streszczenie

1. **Fauna wycinka (zalecenie):** cztery pełne encje: **sarna**, **dzik**, **wiewiórka** i **sójka**. Do tego **żaba trawna** jako wariant wanilijnej żaby (sam plik danych i tekstura, bez kodu) i **dzięcioł** tylko jako dźwięk z pni. **Lis** zostaje wanilijny, bo `minecraft:fox` to właśnie lis rudy. Wilk i królik powinny zniknąć ze spawnów obu lasów wycinka: wilk jest w Polsce rzadki (1 wataha na 150–300 km²), a królik nie jest gatunkiem leśnym (decyzja F1).
2. **Wersje:** GeckoLib **5.5.7** (2026-09-22, `maven.modrinth:geckolib:kSxHvs99`) jest najnowszy dla 26.3 i projekt już go ma. Dla SmartBrainLib wyszła **2.0.3** (2026-10-04, `cwerutUM`). Naprawia m.in. „scheduled activities replacing themselves every tick”, `MoveToWalkTarget`, `FollowEntity`, `BreedWithPartner`, `NearbyAdultSensor` i promień `EntityRetrievalUtil`, a z tych elementów skorzysta wycinek. **Zalecenie: przejść z 2.0.2 na 2.0.3, zanim powstanie pierwsza encja.**
3. **GeckoLib 5.5.7 zmienił pakiet na `com.geckolib`** (było `software.bernie.geckolib`). Przykłady kodu w raporcie 03 nie skompilują się bez poprawki nazw. Ścieżki: `assets/polishforests/geckolib/models/entity/…` i `geckolib/animations/entity/…` (podtyp `entity` w `DefaultedEntityGeoModel`), tekstury w `textures/entity/…`. Jar rozpoznaje wersje geometrii Bedrock od 1.12.0 do 1.21.20.
4. **SBL 2.x** sam wpina mózg przez mixiny `LivingEntityMixin` i `MobMixin`. Przykładowy `SBLSkeleton` nie nadpisuje `makeBrain` ani `customServerAiStep`. Encja implementuje tylko `SmartBrainOwner<T>`: `getSensors`, `getAlwaysRunningBehaviours`, `getIdleBehaviours`, `getFightingBehaviours`, `getAdditionalActivities` i `getSchedule()` (`SmartBrainSchedule.byDaytime()`). Biblioteka ma też nawigacje `SmoothGroundNavigation`, `SmoothWallClimberNavigation` (wiewiórka) i `SmoothFlyingPathNavigation` (sójka).
5. **Spawny w 26.3** to atrybut biomu `minecraft:gameplay/natural_mob_spawns` (`spawns_by_category`, wpisy `{type, count, weight}`, gdzie `count` to liczba albo int provider) oraz atrybut `minecraft:gameplay/creature_world_gen_spawn_probability` (float, domyślnie 0,1) dla spawnu przy generacji chunka. Nasz datagen już je zapisuje. Dwie pułapki:
   - wanilijna reguła `Animal.checkAnimalSpawnRules` wymaga `#animals_spawnable_on`, a ten tag zawiera **tylko `grass_block`**, oraz jasności surowej > 8. W borze świeżym wierzch to `podzol` (M2 §7.4), więc wanilijny predykat da tam **zero saren**. Potrzebny jest własny predykat i własny tag;
   - `MobCategory` to wciąż zamknięty enum. Limit `CREATURE` wynosi 10 (zwierzęta trwałe, cykl co 400 ticków), `AMBIENT` 15 (nietrwałe).
6. **Gęstość:** prawdziwa gęstość saren (3–8 na km²) daje 0,2–0,6 sarny na obszar spawnu gracza (17 × 17 chunków ≈ 0,074 km²), czyli w grze prawie nigdy jej nie widać. Wycinek potrzebuje mnożnika gęstości „dla gry” rzędu ×20–50 (decyzja F2).
7. **Małe zwierzęta w skali 1:1 są nieczytelne** (1 px modelu = 6,25 cm): wiewiórka ma 3,5 px tułowia, sójka 5,5 px długości, żaba 1,3 px. Zalecenie: zwierzęta < 30 cm w skali ×2 (żaba jak wanilijna), od 30 cm rzeczywiste rozmiary (decyzja F3).
8. **Tekstury bloków bez grafika są wykonalne na poziomie zbliżonym do wanilii.** W dwóch rundach prototyp osiągnął statystyki wanilii: liście 60–66% nieprzezroczystych pikseli i 5 tonów (wanilia 56–67%, 4–6), kora 6–8 kolorów. Najważniejsze wnioski z prototypu:
   - struktura tekstury musi mieć 2–4 px: niska częstotliwość daje widoczne powtórzenia przy kafelkowaniu;
   - pojedyncze piksele trzeba usuwać filtrem większościowym;
   - liście najlepiej wychodzą ze „stempli” liści 2–3 px z jasnym pikselem u góry z lewej.
9. **Gatunki drzew po samych teksturach liści 16 × 16 się nie rozróżniają.** Różnicę muszą dać kształt drzewa, kora i **mnożnik koloru gatunku na tincie biomu**. To rekomendacja do otwartej decyzji B6: zachowuje sezonowe kolory Serene Seasons i daje odróżnialne gatunki.
10. **Modele zwierząt:** generator `.geo.json`, `.animation.json` i tekstury z opisu gatunku w centymetrach działa. Prototyp sarny ma 13 kości, 16 sześcianów, UV 64 × 64 bez nakładania się i czytelną sylwetkę po 3 poprawkach. Typowe błędy generatora to znak obrotów (konwencja Bedrock/GeckoLib) i części potomne liczone w obróconym układzie rodzica. Dlatego potrzebny jest podgląd z kodu **i** zrzut w grze (gametest `fauna`). Tekstura z generatora jest płaska: oczy, nos i znaczenia trzeba dorysować kodem dla każdego gatunku. Oczekiwana jakość to poziom prostych mobów wanilii (krowa, lis), nie Naturalist.
11. **Licencje:**
    - nie kopiujemy, nie przerysowujemy i nie edytujemy plików wanilii: EULA definiuje mod jako dzieło bez „substantial part” treści Mojang. Odwołanie do tekstur wanilii przez id w modelu (np. brzoza, świerk) jest w porządku;
    - nie bierzemy zasobów Naturalist (ARR), Alex's Mobs (ARR), BOP (CC BY-NC-ND) ani Pixel Perfection (CC BY-SA 4.0, wymusiłaby share-alike);
    - zasoby CC0 (Kenney, ambientCG, ShareTextures) nadają się na **źródło palet** ze zdjęć, nie jako tekstury, bo styl i rozdzielczość nie pasują;
    - licencję naszych zasobów trzeba ustalić w D5/D6 (rekomendacja: CC BY 4.0 albo MIT jak kod, `LICENSE-assets` i `CREDITS`).
12. **Koszt (czas agentów):** fauna ok. 55–80 h (7–10 dni pracy agenta), grafika wycinka ok. 30–45 h (4–6 dni), łącznie z recenzjami według B1. Szczegóły w §5.

## 1. Stan wyjściowy w projekcie

| Element | Stan (2026-10-14) | Źródło |
|---|---|---|
| Zależności | GeckoLib 5.5.7 (`kSxHvs99`) i SBL 2.0.2 (`i5vOn6hO`) w `gradle.properties`, `implementation`, `depends` w `fabric.mod.json` (`>=5.5.7`, `>=2.0.2`) | `build.gradle`, `fabric.mod.json` |
| Kod encji | brak (pakiety `entity/`, `block/` tylko w planie `docs/01` §9) | `src/main/java` |
| Zasoby | tylko ikony moda; tekstur bloków i encji brak (M2-D: bloki własne od M4) | `src/main/resources/assets` |
| Spawny lasów (M2) | `creature`: wilk 5 (4 szt.), lis 8 (2–4), królik 4 (2–3); `ambient`: nietoperz; olsy, torfowiska, szuwar: żaba 10 | `03-m2-biomy.md` §10, `src/main/generated/.../oak_hornbeam_forest.json` |
| Wierzch gleby | grąd: `grass_block` (BROWN_SOIL); bór świeży: `podzol` (PODZOL) | §7.4 |
| Drzewostan | grąd: Db 35, Gb 30, Lp 15, Js/Kl 5, Bk 10, Św 5 (9 na chunk); bór świeży: So 85, Brz 10, Św 5 (11 na chunk) | §8.5 |
| Spawn przy generacji | `PolandChunkGenerator.spawnOriginalMobs` najpierw usuwa `ChunkHabitats`, potem woła `NaturalSpawner.spawnMobsForChunkGeneration` | kod, wiersze ok. 688–696 |

Wniosek: w `spawnOriginalMobs` siedliska kolumn są jeszcze dostępne. Wystarczy przenieść usunięcie załącznika za własny krok „populacji startowej”, a spawn przy generacji może korzystać z siedliska, strefy i zespołu kolumny (np. sarny przy skraju lasu, dziki w grądzie przy kałużach z mikroreliefu M2). Nowe próbki modelu nie są potrzebne.

## 2. Fauna wycinka

### 2.1 Wybór gatunków

| Gatunek | Realizacja | Dlaczego w wycinku | Co sprawdza w systemie |
|---|---|---|---|
| **Sarna** (*Capreolus capreolus*) | pełna encja (GeckoLib + SBL) | najliczniejsze kopytne w obu lasach; w `docs/01` §6 to gatunek testowy | profil „kopytne płochliwe”: rytm dobowy, dystans ucieczki, grupa, sezonowe futro i parostki |
| **Dzik** (*Sus scrofa*) | pełna encja | grąd (żołędzie, bukiew), bory; zachowania widoczne w terenie | profil „wataha”: lider grupy (stara locha), ucieczka gęsiego, ślady w glebie (buchtowisko, babrzysko przy kałużach M2), agresja w kontekście (C6) |
| **Wiewiórka** (*Sciurus vulgaris*) | pełna encja | oba lasy, szczególnie bór (szyszki sosny) | profil „nadrzewny”: wspinanie po pniach (także po przyszłych pniach o zmiennej grubości z M3), skoki między koronami, alarm |
| **Sójka** (*Garrulus glandarius*) | pełna encja | wszystkie lasy, zwłaszcza dębowe; „strażnik lasu” | profil „ptak mały”: lot krótki między drzewami, chodzenie po ziemi, **łańcuch alarmowy** (skrzek płoszy sarny i ostrzega inne zwierzęta); jesienią zakopywanie żołędzi, później może sadzić dęby (D11) |
| **Żaba trawna** (*Rana temporaria*) | wariant wanilijnej żaby: `polishforests:common_frog` w rejestrze danych `frog_variant` (sprawdzone: w 26.3 to rejestr danych z `asset_id` i `spawn_conditions` według biomu) | najpospolitsza żaba lasów; olsy i torfowiska już spawnują żaby | koszt bliski zera: tekstura 48 × 48 na układzie UV wanilijnej żaby, plik JSON i tag biomów. Zachowanie i rozród (skrzek, kijanki) z wanilii. Rozmiar jak w wanilii (ok. 0,5 bloku, ok. 5 × za duża, decyzja F3) |
| **Dzięcioł duży** (*Dendrocopos major*) | tylko dźwięk | bębnienie (I–V) i kucie (cały rok) mocno budują nastrój lasu | serwer wybiera losowy wysoki pień w promieniu 24–64 bloków od gracza w biomie leśnym i gra tam dźwięk. Encja (ptak przyczepiony do pnia) jest dopiero po wycinku |
| Lis rudy | wanilia `minecraft:fox` (zostaje) | wanilijny lis to *Vulpes vulpes*: nocny, poluje skokiem, zakopuje łup | nic do zrobienia; podmiana modelu w M6 |
| Wilk, królik | usunąć ze spawnów obu lasów (decyzja F1) | wilk to w Polsce rzadkie „zdarzenie regionalne” (raport 07 §2.2, §14 pkt 1); królik europejski nie jest gatunkiem lasów Polski | – |

Odrzucone na wycinek: jeleń (duży, chmara, rykowisko: lepszy jako pierwszy gatunek M6), dzięcioł czarny jako encja (lot falisty i przyczepianie do pnia to osobny system), borsuk i lis własny (nora jako `BlockEntity`, zgodnie z `docs/01` §6).

### 2.2 Karty zachowań: co w wycinku, co później

Dane z raportu 07 (§3.2, §3.4, §3.15, §5.8, §7.3, §10, §11, §13). Czas gry: tick 0 = 6:00 (wschód), 6000 = południe, 12000 = 18:00 (zachód), 18000 = północ. Kalendarz z projektu (`SeasonProvider`, 12 podsezonów po 12 dni, B2/B3).

**Sarna.** Tułów 95–140 cm, w kłębie 60–90 cm (średnio 75), 15–35 kg.
- Aktywność: szczyty rano, w południe i wieczorem, możliwe żerowanie nocne, cykle żerowania i przeżuwania co 2–3 h. Harmonogram SBL:
  - 22500–1500 żer (IDLE);
  - 1500–5000 odpoczynek w gęstwinie (REST, animacja leżenia);
  - 5000–7000 żer;
  - 7000–11500 REST;
  - 11500–14000 żer;
  - noc: REST z krótkimi wyjściami.
- Grupy: latem 1–3 (koza z koźlętami), zimą rudle 3–10 prowadzone przez kozę. W wycinku grupa spawnu 1–3 latem i 2–6 zimą według podsezonu; lider przez `FollowEntity` na najstarszą kozę w pamięci, bez stałego UUID.
- Dystans ucieczki (FID): 40–85 m w terenie otwartym, w lesie 20–40 m. Sensor graczy `NearbyPlayersSensor.setRadius(40)` co 10–20 ticków, `AvoidEntity.noCloserThan(FID).runNBlocksAway(50–100)`. Po ucieczce postój i patrzenie na gracza, alarm „bo-bo” i białe lustro (osobna tekstura).
- Pokarm: animacja `misc.eat` na trawie i krzewach, bez zmiany bloków.
- Sezon: futro letnie rude (175, 100, 55) od V do IX, zimowe szarobrązowe (125, 115, 100) od X do IV. Kozioł: parostki wytarte V–X, zrzut X–XI, scypuł XII–IV (kość `antlers` ukrywana i tekstura według `DataTicket`).
- Później (M6): koźlęta cętkowane ukryte w trawie, ruja VII–VIII, ślady (czemchanie).
- Model: kłąb 12 px, hitbox 0,7 × 1,1.

**Dzik.** Tułów 120–180 cm, w kłębie 55–110 cm (odyniec ok. 75), odyniec 80–150 kg, locha 35–140 kg.
- Aktywność: zmierzchowo-nocna (12500–23000), w dzień barłóg w gęstwinie (REST).
- Grupa: wataha 3–8 w wycinku (lochy z warchlakami i przelatkami, prowadzi stara locha); odyńce samotne poza huczką (XI–I).
- FID 20–60 m (słaby wzrok, dobry węch), nocą bliżej; chrapnięcie ostrzegawcze i ucieczka gęsiego za liderem.
- Zachowania z terenem (decyzja C9): buchtowanie (`grass_block` → `coarse_dirt`/`rooted_dirt` płatami 2–4 bloków w grądzie, z regułą gry `mobGriefing` albo własną), babrzysko przy kałużach mikroreliefu M2, czochranie o pnie (tylko animacja).
- Agresja (decyzja C6): locha z warchlakami od III do VI, gracz < 6–10 bloków, najpierw ostrzeżenie, potem szarża.
- Warchlaki pasiaste 3–4 miesiące; dojrzewanie według C10.
- Model: kłąb 12 px (odyniec), 10 px (locha), hitbox 0,9 × 0,9 (jak świnia).

**Wiewiórka.** 20–24 cm, ogon 17–20 cm, 200–300 g.
- Aktywność: dzienna, szczyty rano i po południu (500–4000, 7000–11000); nocą w gnieździe (w wycinku encja śpi w koronie, M6 doda gniazdo jako blok). Zimą nie zasypia, tylko mniej się rusza.
- Zachowania:
  - wspinanie po pniach z tagu `polishforests:climbable_trees` (`SmoothWallClimberNavigation`, `isClimbing()` tylko przy kłodach i liściach);
  - skoki 4–5 m między koronami (`LeapAtTarget` jako ruch albo własny skok);
  - żer na szyszkach, z dekoracją „ogryzki” pod drzewem w M4;
  - jesienią zakopywanie orzechów (niewidoczne);
  - alarm „czuk-czuk” z machnięciem ogona i ucieczka w górę najbliższego pnia przy graczu ok. 8–12 bloków (własna ocena).
- Morfy: ruda (190, 90, 40) i ciemna (55, 40, 35); zimą pędzelki na uszach.

**Sójka.** 32–35 cm, rozpiętość 52–58 cm, 140–190 g.
- Aktywność: dzienna (0–12000), osiadła.
- Zachowania:
  - lot krótki między drzewami (`SmoothFlyingPathNavigation`, `SetRandomFlyTarget` z ograniczeniem wysokości), siadanie na liściach i gałęziach, skakanie po ziemi (przełączanie nawigacji jak u papugi);
  - **alarm**: skrzek „krrää”, gdy gracz jest bliżej niż 30–60 m. Pamięć `polishforests:forest_alarm` z pozycją trafia do zwierząt w promieniu ok. 32 bloków (sarny przechodzą w czujność albo ucieczkę);
  - jesienią zbieranie żołędzi (później sadzenie dębów, D11).
- Ubarwienie: tułów (190, 150, 130), lusterko na skrzydle (60, 120, 200) z czarnymi prążkami, kuper biały, czarny wąs.

**Żaba trawna.** 7–10 cm, brązowa z ciemną maską skroniową; lasy, wilgotne łąki. Gody od końca II do III w płytkich wodach, zimowanie X–III. Tekstura i reguła spawnu według biomu (lasy, olsy, łęgi, torfowiska) z priorytetem 1 (jak w wanilii) albo wyższym. Zachowanie wanilijne (język, skrzek, kijanki); sen zimowy dopiero w M6.

**Dzięcioł (dźwięk).** Bębnienie krótkie i szybkie (0,5–0,8 s) od I do V, za dnia; kucie nieregularne cały rok; okrzyk „kik”. Słychać do ok. 500 m, w grze 24–64 bloki.

### 2.3 Rozmiary w pikselach modelu i czytelność

1 px modelu = 6,25 cm (16 px = 1 blok = 1 m).

| Gatunek | Wymiar realny | Rzeczywisty rozmiar w px | Zalecenie |
|---|---|---|---|
| Sarna | kłąb 75 cm, tułów 120 cm | kłąb 12, tułów 14 + szyja i głowa | 1:1; nogi 2 × 2 px zamiast anatomicznych 1 px, bo z odległości gry są czytelniejsze |
| Dzik | kłąb 75 / 60 cm, tułów 160 cm | 12 / 10, tułów ok. 22 | 1:1 |
| Wiewiórka | tułów 22 cm, ogon 18 cm, wysokość ok. 9 cm | tułów 3,5, ogon 3, wysokość 1,5 | **×2** (tułów 7 px, ogon 6 px; ok. rozmiaru wanilijnego królika); hitbox ok. 0,4 × 0,35 |
| Sójka | 34 cm | 5,5 | 1:1 albo ×1,5 (wanilijna papuga ma podobny rozmiar) |
| Żaba trawna | 8 cm | 1,3 | rozmiar wanilijnej żaby (ok. 0,5 bloku) |
| Dzięcioł duży | 23 cm | 3,7 | (encja po wycinku) ×2 |

Wariant bez powiększania: części 0,5 px z teksturą o podwójnej gęstości (PNG 2 × większy od `texture_width`/`texture_height` w `.geo.json`). Rozjeżdża się wtedy gęstość tekseli z resztą świata i trudno to malować. Nie zalecam.

### 2.4 Gęstość i obsada

- Raport 07 §2.2: sarna 3–8 na km², dzik 0,2–1, wiewiórka 0,5–1,5 na ha w dobrych borach. Obszar spawnu gracza (17 × 17 chunków) to ok. 0,074 km², więc wychodzi 0,2–0,6 sarny i 0,01–0,07 dzika. Realna gęstość jest w grze niewidoczna.
- Wanilia ogranicza `CREATURE` do 10 na gracza (wspólny limit z lisem), `AMBIENT` do 15 (nietoperze).
- **Zalecenie dla wycinka:**
  - sarna i dzik w `CREATURE` z wagami ok. sarna 10, dzik 4, lis 4, a do tego `creature_world_gen_spawn_probability` 0,15–0,2 w lasach wycinka;
  - wiewiórka i sójka w `AMBIENT` (limit 15, cykl co tick, nietrwałe i znikające jak nietoperze; tego triku użył Eager Beavers). Nie zjadają wtedy limitu kopytnych;
  - wynik to mniej więcej mnożnik ×20–50 wobec rzeczywistości (decyzja F2).
- Później (M6): własny spawner na `ServerTickEvents.END_LEVEL_TICK` (Fabric, bez mixinu; `CustomSpawner` wanilii jest tylko w konstruktorze `ServerLevel`) z gęstością na gatunek w promieniu gracza, plus populacja startowa w `spawnOriginalMobs` z `ChunkHabitats`.
- **Despawn:** `CREATURE` jest w 26.3 trwałe, a `Animal.removeWhenFarAway` zwraca `false`, więc przy eksploracji 1:1 zwierzęta z generacji chunków gromadzą się bez końca. Zalecenie: `removeWhenFarAway = !hasCustomName() && !isLeashed() && !isPersistenceRequired()`, wanilijne odległości kategorii 128/32 bloki.

## 3. Technika: GeckoLib 5.5.7 i SmartBrainLib 2.0.x na 26.3

### 3.1 Wersje (Modrinth API, 2026-10-14, Fabric, MC 26.3)

| Biblioteka | Wersja | Id Modrinth | Data | Licencja | Uwagi |
|---|---|---|---|---|---|
| GeckoLib | 5.5.7 | `kSxHvs99` | 2026-09-22 | MIT | w projekcie; zmiany: format geometrii 1.21.20, poprawka `relative_to`, odwrócone animacje, wydajność; wymaga Fabric API ≥ 0.160.5+26.3 |
| GeckoLib | 5.5.6 | `1VWnZPbQ` | 2026-09-16 | MIT | port na 26.3 |
| SmartBrainLib | **2.0.3** | `cwerutUM` | 2026-10-04 | MPL-2.0 | **zalecana**: ok. 30 poprawek, m.in. harmonogram, `MoveToWalkTarget`, `FollowEntity`, `BreedWithPartner`, `NearbyAdultSensor` (bez wymogu `AgeableMob`), losowe przesunięcia sensorów, wątki `COMPUTED_MEMORIES` |
| SmartBrainLib | 2.0.2 | `i5vOn6hO` | 2026-09-15 | MPL-2.0 | obecnie w projekcie |

Tabela wersji z raportu 03 (GeckoLib 5.5.1 dla 26.2) jest nieaktualna. MPL-2.0 nie wymaga niczego od nas, dopóki nie zmieniamy plików SBL.

### 3.2 GeckoLib 5.5.7: co sprawdzono w jarze

- Pakiet `com.geckolib`. Encja: `GeoEntity` (`registerControllers(AnimatableManager.ControllerRegistrar)`, `getAnimatableInstanceCache()` z `GeckoLibUtil.createInstanceCache(this)`). Renderer: `new GeoEntityRenderer<>(context, ENTITY_TYPE)`, który znajduje model, animacje i teksturę po id typu.
- Ścieżki: `assets/<mod>/geckolib/models/entity/<id>.geo.json`, `assets/<mod>/geckolib/animations/entity/<id>.animation.json`, `assets/<mod>/textures/entity/<id>.png`. Podfoldery są dozwolone, człony `.geo`/`.animation` opcjonalne (wiki GeckoLib 5).
- Rozpoznawane wersje geometrii: 1.12.0, 1.14.0, 1.16.0, 1.19.30, 1.21.0, 1.21.20 (`ModelFormatVersion`), są też klasy siatek wielokątów (`GeometryPolyMesh`). Generator może pisać prosty format 1.12.0 z box UV.
- Nazwy animacji w `DefaultAnimations`: `misc.idle`, `move.walk`, `move.run`, `move.swim`, `move.fly`, `misc.rest`, `misc.sit`, `attack.bite` itd.
- Gotowe kontrolery: `genericWalkIdleController`, `genericWalkRunIdleController`, `genericFlyIdleController`, `genericWalkFlyIdleController(Mob)`, `triggerOnlyController` (animacje wyzwalane z serwera, np. alarm czy kwik).
- Dane do renderu: renderer nie widzi encji (`EntityRenderState`). Warianty (futro, morfa, parostki, lustro) przechodzą przez `DataTicket` (`addGeckolibData` na `GeoRenderState`), tekstura z `DefaultedGeoModel.getTextureResource(GeoRenderState)` albo `withAltTexture`.
- Warstwy: `AutoGlowingGeoLayer` (oczy nocą, niepotrzebne), `TextureLayerGeoLayer`, `CustomBoneTextureGeoLayer`.
- Blockbench: nie znalazłem dokumentacji eksportu wtyczki „GeckoLib Animation Utils” dla GeckoLib 5. Generator pisze czysty format Bedrock (1.12.0 i 1.8.0), który GeckoLib parsuje, więc Blockbench jest **opcjonalny** (podgląd i ręczna edycja przez użytkownika). Po stronie agentów wystarczy podgląd z kodu i zrzut w grze.

### 3.3 SmartBrainLib 2.x: co sprawdzono w jarze

- `SmartBrainOwner<T> extends SmartBrainBuilder<T>`:
  - `getSensors(T)` (wymagana);
  - `getAlwaysRunningBehaviours`, `getIdleBehaviours`, `getFightingBehaviours`, `getAdditionalActivities(T, Activity[])`, `getDefaultActivity`, `getActivityActivationPriority`, `getScheduleIgnoringActivities`;
  - `getSchedule()` → `SmartBrainSchedule.byDaytime().activityAt(tick, Activity)`; są też `byEntityAge`, `byHealthThreshold` i `createCustom(Type)`, przez który harmonogram może zależeć od podsezonu (sen zimowy, gody).
- Mózg i jego tick obsługują mixiny SBL (`common.LivingEntityMixin`, `common.MobMixin`); przykład `SBLSkeleton` nadpisuje tylko metody listy zachowań. 26.3 ma `LivingEntity.makeBrain(Brain.Packed)`, a SBL 2.0.3 przekazuje `packedBrain` (z changelogu).
- Zachowania przydatne w wycinku:
  - ruch: `AvoidEntity` (`noCloserThan`, `runNBlocksAway`, `speedModifier`), `FleeTarget`, `Panic`, `FollowEntity` (lider grupy, `teleportAfterDist` wyłączyć), `FollowParent`, `SetRandomWalkTarget`, `SetRandomFlyTarget`, `SetWalkTargetToBlock` (barłóg, kałuża);
  - złożenia: `Idle`, `LookAtTarget`, `SetPlayerLookTarget`, `FirstApplicableBehaviour`, `OneRandomBehaviour`, `SequentialBehaviour`;
  - walka i rozród: `BreedWithPartner`, `AnimatableMeleeAttack` (szarża lochy), `CustomBehaviour`.
- Sensory: `NearbyPlayersSensor.setRadius(r).scanRate(n)`, `NearbyLivingEntitySensor`, `HurtBySensor`, `NearbyAdultSensor`, `NearbyBabySensor`, `NearbyBlocksSensor` (kałuże, pnie), `InWaterSensor`.
- Nawigacje: `SmoothGroundNavigation`, `SmoothWallClimberNavigation`, `SmoothFlyingPathNavigation`, `SmoothAmphibiousPathNavigation`.

### 3.4 Rejestracja encji w 26.3 (Fabric API)

- `FabricEntityType.Builder.createMob(Factory, MobCategory, b -> b.spawnPlacement(SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, predicate).defaultAttributes(Sarna::createAttributes))`, potem `.sized(w, h)` i `build(ResourceKey)`.
- `MOTION_BLOCKING_NO_LEAVES` jest konieczne, inaczej zwierzę spawnuje się na koronach drzew.
- `SpawnPlacementTypes`: `ON_GROUND`, `IN_WATER`, `NO_RESTRICTIONS`, `IN_LAVA`.
- `MobCategory` to zamknięty enum: rozszerzanie przez mixin jest kruche, więc w wycinku używamy wyłącznie `CREATURE` i `AMBIENT`.
- Pętla własnego spawnera: `ServerTickEvents.END_LEVEL_TICK`; ładowanie chunków: `ServerChunkEvents.CHUNK_LOAD`/`CHUNK_GENERATE`.

### 3.5 Spawny w 26.3

- Biom (`natural_mob_spawns`, `"modifier": "overlay"`):

```json
"minecraft:gameplay/natural_mob_spawns": {"argument": {"spawn_costs": {}, "spawns_by_category": {
  "creature": [{"type": "polishforests:roe_deer", "count": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 3}, "weight": 10}],
  "ambient":  [{"type": "polishforests:red_squirrel", "count": 1, "weight": 6}]}}, "modifier": "overlay"},
"minecraft:gameplay/creature_world_gen_spawn_probability": 0.15
```

  Datagen (`HabitatBiome.spawns()`, `ModWorldgen`) zapisuje już atrybut spawnów. Wycinek dopisuje gatunki do profili dwóch biomów (albo do całej grupy borów i lasów liściastych, zależnie od zakresu wycinka).
- Predykat: własny `PolishAnimalSpawnRules.check(...)`:
  - blok pod spodem w tagu `polishforests:forest_animals_spawnable_on`: `grass_block`, `podzol`, `coarse_dirt`, `rooted_dirt`, `dirt`, `moss_block` i przyszłe gleby z M4. Wanilijny `#animals_spawnable_on` to tylko `grass_block`, a `foxes_spawnable_on` i `wolves_spawnable_on` mają też `podzol` i `coarse_dirt`;
  - światło: zamiast `getRawBrightness > 8` np. ≥ 5. Wanilijny próg 8 pod gęstymi koronami M3 (drzewa 20–40 bloków, korony 8–15 bloków grubości) może wyłączyć spawn w środku lasu. Do zmierzenia w wycinku.
- Spawn przy generacji: `spawnOriginalMobs` → `NaturalSpawner.spawnMobsForChunkGeneration` (wanilia, z prawdopodobieństwem z atrybutu). Z siedliskami: `ChunkHabitats` usuwać dopiero po własnym kroku populacji (§1).
- Tryb D („dzisiejsza Polska”) i łąki: sarna polna (raport 07) to temat po wycinku.

### 3.6 Proponowany układ kodu (do planu kroku, nie do wykonania teraz)

```
pl.polishforests.entity
  ModEntities            rejestracja typów, atrybutów, spawnPlacement, tagów
  base/PolishAnimal      Animal + GeoEntity + SmartBrainOwner: despawn, FID, alarm, podsezon, DataTicket wariantu
  base/AnimalProfile     dane gatunku: wymiary, FID, grupy, harmonogram, sezon futra/poroża (rekord Java; JSON po wycinku)
  ai/ForestAlarm         pamięć alarmu, rozgłaszanie (sójka, szczek sarny, chrapnięcie dzika)
  ai/HerdLeader          wybór lidera z pamięci NEARBY_ADULTS, ucieczka gęsiego
  roedeer/, wildboar/, squirrel/, jay/
  spawn/PolishAnimalSpawnRules, spawn/ForestAmbience (dźwięk dzięcioła)
client/entity/PolishAnimalRenderer (GeoEntityRenderer + DataTicket), ModEntityRenderers
tools/fauna/  generator .geo.json/.animation.json/tekstur (§4.5)
```

Testy:
- JUnit: predykat spawnu, wybór lidera, harmonogramy (czysta Java);
- gametest `-Pgametest=fauna`:
  - spawn w obu biomach w obu skalach i trybach N/D;
  - tablica zrzutów każdego zwierzęcia z 4 stron, dzień i noc, futro letnie i zimowe;
  - ucieczka przed graczem w promieniu FID, alarm sójki płoszy sarnę;
  - despawn za 128 blokami;
  - MSPT z 40 zwierzętami.

### 3.7 Wydajność

- SBL: sensory co 10–40 ticków (`scanRate`), sensor graczy o promieniu FID (40) tylko u sarny.
- GeckoLib: 10–20 kości na zwierzę (prototyp sarny: 13); wydajność renderu poprawiona w 5.5.7.
- Budżet do zmierzenia w gametest: < 0,05 ms ticku serwera na zwierzę średnio (40 zwierząt < 2 ms), bez wzrostu kosztu generacji chunka poza `spawnOriginalMobs`. Determinizm terenu i dekoracji się nie zmienia: spawn zwierząt jest losowy jak w wanilii, a `DeterminismClientGameTest` haszuje tylko stany bloków.

## 4. Grafika bez grafika

### 4.1 Wanilia 26.3 jako miara stylu (pomiar lokalny, pliki nie trafiają do repozytorium)

| Tekstura | Nieprzezroczystość | Kolorów | Jasność min/średnia/maks (0–255) | Nasycenie |
|---|---|---|---|---|
| oak_leaves / spruce_leaves / birch_leaves | 67% / 62% / 56% | 4 / 6 / 4 | 101/144/187, 66/126/154, 110/130/184 | 0 (szarość pod tint) |
| short_grass / fern / bush | 55% / 35% / 50% | 6 / 6 / 6 | ok. 90–180 | 0 |
| oak_log / spruce_log / pale_oak_log / poplar_log | 100% | 6 / 6 / 6 / 8 | 44/88/123, 26/41/62, 52/80/116, 46/63/83 | 0,15–0,72 |
| oak_planks / spruce_planks | 100% | 7 / 7 | 82/134/161, 62/89/105 | 0,52–0,58 |
| leaf_litter | 46% | 13 | 125/171/234 | 0 (tint `dry_foliage`) |
| lily_of_the_valley | 20% | 7 | 104/158/255 | 0,53 |

Reguły stylu z pomiaru:
- 4–9 kolorów na teksturę;
- tekstury barwione tintem są w skali szarości ze średnią jasnością ok. 0,5–0,6;
- liście mają 55–67% pikseli;
- kora ma wyraźny kierunek pionowy;
- skupiska pikseli mają 2–4 px, pojedynczego szumu prawie nie ma.

### 4.2 Prototyp tekstur (Python 3.14, Pillow 12.2, NumPy 2.4)

Technika:
- szum wartości i fBm oraz komórki Worleya na torusie 16 × 16 (kafelkowanie bez szwów);
- rampa koloru gatunku z 5–7 tonów;
- kwantyzacja progami histogramu (każdy ton użyty);
- filtr większościowy usuwający pojedyncze piksele;
- światło z lewej u góry;
- dla liści i roślin „stemple” małych kształtów zamiast szumu;
- ziarno stałe, wynik deterministyczny.

Runda 1 (15 tekstur: kora sosny dolna i górna, buk, dąb, grab, czoła kłód, deski, liście dębu, buka i grabu, zawilec, borówka, igliwie):
- liście za rzadkie (34–55%) i z dużymi plamami: przy kafelkowaniu widać okres 16 px;
- buk plamisty bez kierunku;
- dolna kora sosny o niskim kontraście (4 kolory), wygląda jak ziemia;
- deski wyglądają jak cegły;
- dobrze wyszły kora dębu, „lustro” sosny (górny pień) i zawilec.

Runda 2 (8 tekstur po poprawkach):
- liście ze stempli 2–3 px: 60–66% pikseli, 5 tonów, struktura jak w wanilii;
- buk gładki szary z ciemnymi „brwiami” i porostami, czytelny;
- deski z jasną spoiną i rzadkimi końcami desek;
- borówka gęstsza;
- dolna kora sosny zbyt ciemna (szczeliny dominują): kolejna runda potrzebna.

Wnioski:
1. Kafelkowane tekstury bloków (kora, deski, czoła, liście, gleby, ściółka, mchy) dochodzą do poziomu „wanilia-podobnego” w 2–3 rundach na rodzaj tekstury. Najwięcej pracy wymaga jednorazowe zbudowanie biblioteki technik, potem jeden gatunek to zmiana parametrów i palety.
2. Gatunki liści różnią się na 16 × 16 bardzo słabo (dąb, buk i grab w rundzie 2 są do siebie podobne, jak w wanilii). Gatunek musi rozpoznawać się po pokroju drzewa i korze oraz po mnożniku koloru (§4.4).
3. Rośliny runa (model krzyżowy) najlepiej wychodzą jako małe „programy rysujące” dla gatunku (łodyga, liście, kwiat), czyli pixel art zapisany kodem. Zawilec był czytelny od razu.

### 4.3 Zalecany pipeline tekstur (`tools/art/`)

- `texlib.py`: szumy na torusie, Worley, rampy, kwantyzacja, filtr większościowy, stemple, światło, warianty (2–3 na korę i glebę).
- `recipes/*.py` albo JSON: typ struktury kory (płyty, bruzdy, gładka, smugi, łuski) i parametry według opisu kory w raporcie 06 §4.1, paleta 5–7 tonów.
- **Palety ze zdjęć CC0:** k-średnie (6 kolorów) ze zdjęcia kory danego gatunku z ambientCG, ShareTextures albo zdjęć PD/CC0 z Wikimedia Commons. Same zdjęcia nie trafiają do moda i nie są skalowane do 16 px (wyglądałoby źle). Do repozytorium idzie tylko lista kolorów i odnośnik do źródła.
- **Lint stylu** (test w Pythonie, uruchamiany jak `checkDatagen`):
  - liczba kolorów 4–9;
  - nieprzezroczystość liści 55–70%;
  - tekstury z tintem mają nasycenie ≈ 0 i średnią jasność 0,45–0,65;
  - brak pojedynczych pikseli powyżej progu;
  - kafelkowanie bez szwów;
  - rozmiar 16 × 16;
  - PNG zgodne bajt w bajt z generatorem (zadanie `checkArt`, żeby nikt nie edytował plików ręcznie).
- **Kontrola wizualna:**
  - arkusz 8 × i kafel 3 × 3 (jak `scripts/preview.py` w prototypie);
  - gametest „tablica próbek”: ściana z każdego bloku, mały las z każdego gatunku i zrzuty lasów wycinka;
  - recenzenci oglądają pełne zestawy (B4 odrzucona).
- **Punkt kontrolny grafiki dla użytkownika** po pierwszych ok. 8 teksturach i pierwszym zwierzęciu, przed resztą, z prawem do „nie podoba mi się” zanim powstanie 60 plików.

### 4.4 Technika bloków ważna dla wycinka

- **Liście:** szarość i tint biomu `BiomeColors.getAverageFoliageColor`. Serene Seasons podmienia resolver koloru listowia, więc kolory sezonowe przychodzą bez integracji (raport 04 §3.2). **Rekomendacja do B6:** `ColorProvider` = tint biomu × mnożnik gatunku (np. grab jaśniejszy i żółtszy, dąb ciemniejszy, buk z połyskiem). Gatunki stają się odróżnialne bez wychodzenia z sezonowej palety SS. Iglaste (sosna, świerk) bez tintu sezonowego, ze stałym kolorem jak wanilijny świerk.
- **Kwiaty runa:** dwie warstwy jak w wanilijnym `wildflowers`/`pink_petals`: łodyga i liście w szarości pod tint trawy, kwiat bez tintu. Geofity mają fazy (kwitnienie, same liście, spoczynek) jako właściwość bloku, przełączaną w random ticku według podsezonu. To 2–3 tekstury na gatunek.
- **Pnie 1:1 (do M3):**
  - pień ma 20–40 bloków wysokości, więc powtórzenie co 16 px rzuca się w oczy. Potrzebne 2–3 warianty kory z wagami w blockstate (wariant z hasza pozycji jest deterministyczny);
  - sosna: dwa rodzaje kory (dolna spękana, górne „lustro”) jako właściwość bloku ustawiana przez generator drzewa według wysokości;
  - pnie o zmiennej grubości (D8): bok to wycinek UV tej samej kory, więc wzór musi być czytelny na 4 px szerokości (struktury ≤ 4 px), a czoła potrzebują osobnej tekstury na każdą średnicę (słoje i pierścień kory w skali), np. 3–4 średnice × gatunek.
- **Ściółka i mchy:** segmentowe jak `leaf_litter` (geometria modelu z wanilii odtworzona, tekstury własne); igliwie bez tintu (rdzawe), grądowa ściółka liściasta może zostać wanilijna (`leaf_litter`).
- **D2 dla wycinka:** brzoza i świerk wanilijne (pasują). Dąb: wanilijna jasnobrązowa kora nie przypomina ciemnej, bruzdowanej kory dębu szypułkowego (prototyp rundy 1 wyszedł dobrze), więc własny pień wart jest kosztu. Liście i deski dębu mogą zostać wanilijne do decyzji D1/D2.

### 4.5 Modele zwierząt: generator `.geo.json`

Prototyp `scripts/geogen.py` (sarna, kozioł):
- wejście: wymiary w cm z raportu 07 §13 (kłąb, nogi 55% wysokości, tułów, szerokość klatki), zaokrąglenie do całych px (wyjątki: ogon 0,5 px);
- kości: `root`, `body`, `neck`, `head`, `ear_left/right`, `antler_left/right`, 4 nogi, `tail`; 16 sześcianów;
- UV: pakowanie półkami w 64 px szerokości, test: każda ściana w teksturze, 0 nakładań (wynik: 64 × 64, wykorzystane 33 wiersze);
- tekstura: wypełnienie ścian według semantyki (grzbiet ciemny, brzuch jasny, gradient boków, lustro na tylnej ścianie, nos, kopyta), szum futra ±10;
- animacje: `misc.idle` (ruch ucha, głowy), `move.walk` i `move.run` (sinusoida, pary nóg po przekątnej, podskok tułowia), `misc.eat`, `misc.alert` (głowa w górę, uszy);
- podgląd: rzut prostokątny 3/4 z siatką 1 bloku i tyczką 1 m.

Co pokazał prototyp:
- po 3 poprawkach sylwetka jest rozpoznawalna (mały kozioł z parostkami, głowa ok. 1,1 m);
- **znak obrotów** i **części potomne** to główne źródła błędów. Szyja szła w dół, a głowa wisiała w powietrzu, bo była umieszczona w nieobróconym układzie szyi. Konwencję obrotów (Bedrock: przód modelu to −Z, X lustrzane względem Javy; jak GeckoLib odwraca osie X/Y przy wczytywaniu) **trzeba raz skalibrować w grze** zrzutem gametestu na modelu testowym z oznaczonymi osiami, potem zablokować testem;
- tekstura z generatora jest płaska i „martwa” bez oczu. Oczy (2 px ciemne z jasnym pikselem), maska, nos, obrzeża uszu i smugi sierści trzeba dopisać kodem dla gatunku.

Szablony szkieletów do wycinka:
- czworonóg (sarna, dzik);
- mały nadrzewny (wiewiórka: tułów, głowa, uszy z pędzelkami, ogon z 3 segmentami do animacji);
- ptak (sójka: tułów, głowa, skrzydła z 2 segmentami, ogon, nogi);
- żaba bez szablonu (model wanilii).

### 4.6 Animacje

- Generowane parametrycznie: chód, kłus i galop czworonogów, oddech, ruchy uszu i ogona, jedzenie, czujność, leżenie (`misc.rest`), sen. Długość pętli i amplitudy z gatunku.
- Ręcznie, keyframe'ami w kodzie generatora: wspinanie wiewiórki (głową w dół przy schodzeniu), skok między koronami, lot sójki (mach i szybowanie), lądowanie na gałęzi, skrzek, buchtowanie dzika. To 2–4 h na trudną animację z oglądaniem zrzutów i klatek.
- Wyzwalanie: `triggerOnlyController` i `triggerAnim(controller, name)` z serwera (alarm, kwik, skrzek).

### 4.7 Ograniczenia jakości (uczciwie)

| Klasa zasobu | Oczekiwana jakość | Główne ryzyko |
|---|---|---|
| Kora, deski, czoła, gleby, ściółka | blisko wanilii po 2–3 rundach | „proceduralna jednakowość”: wszystkie kory z tej samej biblioteki wyglądają jak rodzina; powtórzenia na 30-metrowych pniach |
| Liście | jak wanilia, ale gatunki nierozróżnialne | rozwiązanie przez mnożnik tintu i pokrój (§4.4) |
| Rośliny runa | dobre dla prostych roślin (kwiaty, krzewinki), słabsze dla traw i paproci w masie | bez kontroli w grze łatwo o zbyt jaskrawe kwiaty i „kropki” |
| Modele zwierząt | proste, czytelne sylwetki na poziomie mobów wanilii | małe zwierzęta (§2.3), anatomia upraszczana do sześcianów |
| Tekstury zwierząt | płaskie po generatorze, akceptowalne po ręcznie zakodowanych detalach | wygląd „plastikowy”, brak cieniowania krawędzi; oczy decydują o „życiu” |
| Animacje | chód i bieg dobre, złożone ruchy przeciętne | sztywność; brak przejść między stanami bez pracy nad kontrolerami |

Ocena estetyki należy do agenta, który ją tworzy, więc jest subiektywna. Dlatego potrzebny jest punkt kontrolny użytkownika (§4.3) i recenzja ze zrzutami. Bez grafika wycinek będzie spójny i czytelny, ale nie „ładniejszy od wanilii”. Gdyby użytkownik chciał wyższej jakości, pipeline daje bazę, którą grafik poprawi w Blockbench (UV i nazwy kości są stabilne).

### 4.8 Licencje i źródła zasobów

| Źródło | Licencja | Użycie w projekcie |
|---|---|---|
| Tekstury, modele i dźwięki Mojang | EULA: mod nie może zawierać „substantial part” treści Mojang | **nie** kopiować, nie przerysowywać, nie edytować PNG wanilii. **Wolno** odwołać się do tekstury wanilii przez id w modelu (brzoza, świerk, liście dębu) i lokalnie mierzyć statystyki stylu (jak w §4.1, bez zapisu w repozytorium) |
| Naturalist | kod MIT, **zasoby ARR** | tylko inspiracja (raport 03) |
| Alex's Mobs | ARR (brak licencji w repozytorium) | nie |
| Biomes O' Plenty | CC BY-NC-ND 4.0 | nie |
| Untamed Wilds | GPL-3.0 (kod) | tylko czytanie wzorców, bez kopiowania |
| Eager Beavers | CC0 | wolno, ale bóbr nie jest w wycinku, a model jest w GeckoLib 4 |
| Pixel Perfection (XSSheep) | CC BY-SA 4.0 | nie: share-alike objęłoby nasze zasoby; styl i tak inny |
| Kenney (Voxel Pack) | CC0 | wolno, ale styl kreskówkowy nie pasuje do 16 × 16 wanilii |
| ambientCG, ShareTextures, Poly Haven | CC0 (zdjęcia i PBR) | **źródło palet** (k-średnie z kory, mchów, gleb); nie jako tekstury |
| Wikimedia Commons | różne, tylko PD/CC0 | palety sierści i upierzenia (obok RGB z raportu 07 §13) |
| xeno-canto | prawa przy nagrywających, często CC BY-NC-SA | tylko nagrania CC BY / CC BY-SA / CC0; atrybucja w `CREDITS`; ssaków mało |
| Freesound | CC0, CC BY, CC BY-NC | tylko CC0 i CC BY, atrybucja |

- Nasze zasoby (decyzja D5, uzupełnienie D6): zalecam **CC BY 4.0** (albo MIT jak kod) z plikiem `LICENSE-assets` i manifestem źródeł palet i dźwięków (`tools/art/sources.json` → `CREDITS.md` z generatora).
- Status praw autorskich treści tworzonych z udziałem AI jest w części jurysdykcji niejasny. Przy licencji zezwalającej nie blokuje to dystrybucji (ryzyko niskie).

### 4.9 Spis tekstur wycinka (szacunek)

| Grupa | Tekstury | Liczba |
|---|---|---|
| Sosna | kora dolna (2 warianty), „lustro” (2), czoło, deski, igły (kępka liści), czoła średnic M3 (3) | ok. 10 |
| Dąb (pień) | kora (2), czoło, czoła średnic (3) | ok. 6 |
| Grab, lipa | kora, czoło, deski, liście każdy; czoła średnic (3 + 3) | ok. 14 |
| Runo grądu | zawilec, przylaszczka, gajowiec, gwiazdnica, marzanka, kopytnik, fiołek; fazy 2 na gatunek (konwalia wanilijna) | ok. 14 |
| Runo boru | borówka (3 stany: bez owoców, z owocami, jesień), brusznica (2), śmiałek, pszeniec, orlica 2-blokowa (zielona i brązowa) | ok. 10 |
| Mchy i porosty | rokietnik (dywan), widłoząb, poduszka bielistki, chrobotek | 4 |
| Ściółka | igliwie (2 warianty) | 2 |
| Martwe drewno | kłody w 3 stadiach × iglaste i liściaste (bok i czoło) | ok. 12 |
| Mrowisko | 2 | 2 |
| Zwierzęta | sarna (koza lato, koza zima, kozioł lato, kozioł zima, lustro w alarmie jako wariant), dzik (dorosły, warchlak), wiewiórka (2 morfy, zima), sójka, żaba | ok. 12 |
| **Razem** | | **ok. 85** (bez kłód: ok. 73) |

Bloki (rejestracja, modele JSON z datagenu, tagi) to koszt M3 i M4, nie tego raportu. Tu liczona jest tylko grafika.

## 5. Koszty (czas agentów)

Założenia:
- sesja agenta; przebiegi testów według A1/A2;
- recenzje według B1: rejestracja encji i spawny w datagenie biomów to wysokie ryzyko (2 recenzentów, do 2 rund), tekstury i dane to 1 recenzent i 1 runda;
- „h” to czas pracy agenta z iteracjami i oglądaniem zrzutów, bez czekania na użytkownika.

| Pozycja | Szacunek |
|---|---|
| SBL 2.0.2 → 2.0.3 (`gradle.properties`, `depends`), kompilacja, `fastTest`, uruchomienie gry | 0,5–1 h |
| `tools/fauna/`: szablony (czworonóg, mały nadrzewny, ptak), pakowanie UV, malowanie, chody, podgląd, walidacja (JSON, UV, nakładania), test kalibracji osi w grze | 8–12 h |
| Szkielet fauny: `PolishAnimal`, `ModEntities`, renderer z `DataTicket`, predykat i tag spawnu, despawn, sensor FID, `ForestAlarm`, podsezon (futro, parostki), spawny w datagenie (2 biomy), lang EN/PL | 10–14 h |
| Gametest `fauna` (spawn w obu biomach, skalach i trybach, tablica zrzutów, ucieczka, alarm, despawn, MSPT) | 5–8 h |
| Sarna (model, tekstury 4–5, animacje 6, harmonogram, grupa, FID) | 5–7 h |
| Dzik (wataha z liderem, ucieczka gęsiego, barłóg, babrzysko; buchtowanie, jeśli C9 = tak, +2 h; agresja lochy, jeśli C6 = tak, +2 h) | 7–11 h |
| Wiewiórka (wspinanie, skoki, ucieczka na drzewo, 2 morfy, animacje wspinania) | 9–13 h |
| Sójka (lot, siadanie, przełączanie nawigacji, alarm płoszący, animacje lotu) | 9–13 h |
| Żaba trawna (`frog_variant`, tekstura na UV wanilijnej żaby z kodu `FrogModel`, tag) | 1–2 h |
| Dzięcioł (dźwięk z pni, sezon bębnienia) | 2–3 h |
| Dźwięki (wyszukanie CC0 i CC BY, konwersja do mono OGG, `sounds.json`, `CREDITS`) | 3–5 h |
| **Fauna razem** (z rundami recenzji ok. +30%) | **ok. 55–80 h (7–10 dni pracy agenta)** |
| `tools/art/`: biblioteka, lint stylu, arkusze, `checkArt`, palety CC0 | 5–8 h |
| ok. 70 tekstur bloków (średnio 15–25 min z iteracją) | 18–30 h |
| Gametest „tablica próbek” i 1–2 rundy po zrzutach | 4–7 h |
| **Grafika razem** | **ok. 30–45 h (4–6 dni)** |

Najtańszy sensowny wariant „minimum” (gdyby czas był ważniejszy): sarna i dzik jako encje, żaba jako wariant, dzięcioł jako dźwięk, a wiewiórka i sójka dopiero w M6. Fauna ok. 30–40 h.

## 6. Ryzyka

| # | Ryzyko | Skutek | Ograniczenie |
|---|---|---|---|
| R1 | Wanilijny predykat spawnu (`grass_block`, światło > 8) | 0 zwierząt w borze świeżym (`podzol`), mało w gęstym lesie M3 | własny predykat i tag (§3.5), pomiar światła pod koronami w gametest |
| R2 | Wspólny limit `CREATURE` 10 i trwałość `CREATURE` | mało zwierząt albo kumulacja bez końca przy eksploracji 1:1 | małe zwierzęta w `AMBIENT`, despawn za 128 blokami, później własny spawner |
| R3 | Konwencja osi i obrotów w generatorze `.geo.json` | modele „połamane” w grze, choć w podglądzie z kodu wyglądają dobrze | jeden model kalibracyjny w gametest i test regresji (zrzut lub porównanie położenia kości z `BakedGeoModel`) |
| R4 | Subiektywna estetyka agenta | użytkownik odrzuca styl po wykonaniu wszystkich tekstur | punkt kontrolny grafiki po ok. 8 teksturach i pierwszym zwierzęciu |
| R5 | Wspinanie na pniach M3 o zmiennej grubości | `WallClimberNavigation` działa na kolizji poziomej; cienki pień (4 px) może nie dawać kolizji w ścieżce | w wycinku tag pni (wanilia i M3), test wspinania na 3 grubościach; ewentualnie własny ruch „przyczepiony do pnia” |
| R6 | Lot sójki w gęstym lesie | `FlyingPathNavigation` blokuje się w liściach, ptak lata nad koronami | cele lotu tylko w wolnej przestrzeni pod koronami, krótkie odcinki, lądowanie na wolnych liściach |
| R7 | Małe zwierzęta nieczytelne w skali 1:1 | wiewiórki „znikają” | skala ×2 (decyzja F3) |
| R8 | Dźwięki z licencją NC | nie wolno ich dołączyć | filtr licencji w manifeście; do tego czasu dźwięki zastępcze wanilii (np. lis, królik) |
| R9 | Zmiany API GeckoLib i SBL w łatkach 26.3 i 26.4 | rekompilacja i drobne poprawki | wersje przypięte w `gradle.properties`, aktualizacja świadomie |
| R10 | Gęstość ×20–50 | „zoo” w lesie albo pustka | parametr gęstości w jednym miejscu (`AnimalProfile`), kalibracja na zrzutach i w grze z użytkownikiem |
| R11 | Spawn przy generacji (`spawnOriginalMobs`) a test determinizmu | encje są losowe (jak w wanilii) | sprawdzone: `DeterminismClientGameTest` haszuje tylko stany bloków (teren przed FEATURES i dekorację), więc encje go nie zmieniają. Buchtowanie dzika (C9) zmienia bloki dopiero po generacji, więc też nie wpływa na test |
| R12 | Wariant `frog_variant` z priorytetem | wanilijne żaby w biomach spoza tagu albo nasza żaba także w obcych biomach | warunek po tagu `polishforests:forests` i `waterside`, test w gametest |

## 7. Decyzje do podjęcia przez użytkownika

Nowe (propozycja numeracji w części C lub F):

| # | Decyzja | Warianty | Rekomendacja |
|---|---|---|---|
| F1 | Wanilijne zwierzęta w lasach wycinka | (a) zostawić wilka, lisa i królika; (b) usunąć wilka i królika, lis zostaje; (c) usunąć wszystkie wanilijne | (b) |
| F2 | Gęstość fauny w grze | (a) realna (praktycznie niewidoczna); (b) ×20–50 przez wagi i limity wanilii; (c) własny spawner z gęstością na gatunek (M6) | (b) w wycinku, (c) w M6 |
| F3 | Skala małych zwierząt | (a) 1:1 wszystkie; (b) < 30 cm ×2, żaba jak wanilia; (c) 1:1 z teksturą podwójnej gęstości | (b) |
| F4 | Zestaw gatunków wycinka | (a) sarna, dzik, wiewiórka, sójka, żaba (wariant), dzięcioł (dźwięk); (b) minimum: sarna, dzik, żaba, dzięcioł | (a), jeśli 7–10 dni pracy agenta na faunę jest do przyjęcia |
| F5 | Licencja zasobów (D5/D6) | CC BY 4.0 / MIT / CC0 | CC BY 4.0 (albo MIT) z `LICENSE-assets` i `CREDITS` |
| F6 | Punkt kontrolny grafiki | (a) po ok. 8 teksturach i sarnie; (b) na końcu wycinka | (a) |

Otwarte decyzje, których wycinek dotyka (z domyślną propozycją dla wycinka):
- **B6** (liście): tint biomu × mnożnik gatunku (§4.4);
- **C2** (hitboxy): dla gatunków wycinka bez problemu (≤ 1,1 bloku), właściwa decyzja przy łosiu i jeleniu;
- **C3** (populacje): dzik rzadszy niż sarna (stan 2025);
- **C6** (agresja): locha z warchlakami od III do VI, ostrzeżenie, potem szarża z małym obrażeniem;
- **C8** (dropy): w wycinku brak;
- **C9** (zmiana terenu): buchtowanie z regułą gry, domyślnie włączone;
- **C10** (dorastanie): warchlaki i koźlęta tylko w grupach spawnu, dorastanie wanilijne do M6;
- **D1/D2** (zestawy drewna): w wycinku zestawy „lite” (pień, drewno, okorowany, deski, liście, sadzonka) dla sosny, grabu i lipy, pień dębu własny, brzoza i świerk wanilijne;
- **D5** (kto robi tekstury): proceduralnie + punkt kontrolny (ten raport).

## 8. Proponowana kolejność (część fauny i grafiki wycinka)

1. SBL 2.0.3; biblioteka `tools/art` i lint stylu; 6–8 tekstur sosny i grabu oraz gametest tablicy próbek.
2. `tools/fauna` i model kalibracyjny osi w grze; sarna (pierwsze zwierzę).
3. **Punkt kontrolny grafiki** (tekstury i sarna, zrzuty; decyzje F3 i F5).
4. Szkielet fauny pełny (spawny w datagenie, predykat, despawn, alarm), gametest `fauna`, dzik.
5. Pozostałe tekstury wycinka (runo, mchy, martwe drewno) razem z blokami M3/M4 wycinka.
6. Wiewiórka (po pniach M3), sójka, żaba, dzięcioł; dźwięki; pomiary MSPT; zrzuty końcowe.

## 9. Pliki prototypów (scratchpad, poza repozytorium)

Katalog: `C:/Users/henke/AppData/Local/Temp/claude/C--Users-henke-Desktop-code-Mic-player/39088e90-5fa7-42c1-91b8-99cc31d862ee/scratchpad/slice/`

- `scripts/texgen.py` (runda 1), `scripts/texgen2.py` (runda 2), `scripts/preview.py` (arkusz 8 × i kafel 3 × 3 z tintem biomu grądu), `scripts/stats.py` (statystyki stylu), `scripts/geogen.py` (generator sarny: `.geo.json`, `.animation.json`, tekstura, podgląd; zmienna `YAW` ustawia kąt podglądu).
- `proto/` i `proto_sheet.png` (runda 1, 15 tekstur), `proto2/` i `proto2_sheet.png` (runda 2, 8 tekstur).
- `geo/roe_deer.geo.json`, `geo/roe_deer.animation.json`, `geo/roe_deer.png` (64 × 64), `geo/roe_deer_uv_x8.png`, `geo/roe_deer_preview.png` (od tyłu), `geo_front/roe_deer_preview.png` (od przodu).
- `vanilla_sheet.png`, `vanilla_cmp/`, `mc/`: pliki wanilii wyjęte lokalnie z jaru 26.3 tylko do porównania stylu; **nie kopiować do repozytorium ani nie publikować**.
- `jars/`, `mr_geckolib.json`, `mr_smartbrainlib.json`: metadane bibliotek i odpowiedzi API Modrinth.

Uruchamianie (Git Bash): `python -P scripts/texgen2.py <out>`, `python -P scripts/preview.py <dir> <png> <tytuł>`, `YAW=-145 python -P scripts/geogen.py <out>`.

## 10. Źródła

Projekt:
- `docs/BRIEF.md`;
- `docs/00-decyzje-do-podjecia.md` (B6, C2–C11, D1, D2, D5, D6, P2);
- `docs/01-architektura.md` §6, §8, §9;
- `docs/03-m2-biomy.md` §7.4, §8.4–8.6, §9, §10;
- `docs/research/03-zwierzeta-technika-modele-ai.md`, `04-flora-bloki-drzewa-technika.md`, `06-lasy-polski-fitosocjologia-stl.md`, `07-fauna-polski-siedliska-zachowania.md`.

Sprawdzone lokalnie (`javap`, odczyt plików z jarów w `~/.gradle/caches`):
- `geckolib-kSxHvs99.jar` (5.5.7): `fabric.mod.json`, `DefaultAnimations`, `DefaultedGeoModel`/`DefaultedEntityGeoModel` (stałe `geckolib/models`, `geckolib/animations`, `entity`), `GeoEntityRenderer`, `GeoRenderState`, `DataTickets`, `ModelFormatVersion`;
- `smartbrainlib-i5vOn6hO.jar` (2.0.2): `SmartBrainOwner`, `SmartBrainBuilder`, `SmartBrainSchedule`, `SmartBrain`, `SBLSkeleton`, `smartbrainlib.mixins.json`, `NearbyPlayersSensor`, `AvoidEntity`, `FollowEntity`, nawigacje;
- `minecraft-common.jar` 26.3: `MobCategory` (limity i flagi), `EnvironmentAttributes` (`gameplay/natural_mob_spawns`, `gameplay/creature_world_gen_spawn_probability` = 0,1), `Animal.checkAnimalSpawnRules`, tagi `animals_spawnable_on`, `foxes_spawnable_on`, `wolves_spawnable_on`, `frogs_spawnable_on`, `frog_variant/*.json`, `wolf_variant/woods.json`, biom `forest.json`, `CustomSpawner`, `SpawnPlacements`, `SpawnPlacementTypes`, `LivingEntity.makeBrain(Brain.Packed)`;
- `minecraft-client.jar` 26.3: tekstury wanilii do statystyk stylu (tylko lokalnie);
- Fabric API: `fabric-object-builder-api-v1` 24.1.9 (`FabricEntityType.Builder.Mob.spawnPlacement`, `defaultAttributes`), `fabric-lifecycle-events-v1` 4.1.9 (`ServerTickEvents.END_LEVEL_TICK`, `ServerChunkEvents`).

Sieć (2026-10-14):
- Modrinth API, wersje i changelogi: [GeckoLib](https://api.modrinth.com/v2/project/geckolib/version), [SmartBrainLib](https://api.modrinth.com/v2/project/smartbrainlib/version); strony [modrinth.com/mod/geckolib](https://modrinth.com/mod/geckolib), [modrinth.com/mod/smartbrainlib](https://modrinth.com/mod/smartbrainlib);
- [GeckoLib 5 wiki: Placing the files](https://wiki.geckolib.com/docs/geckolib5/making-models/placing-the-files); [GeckoLib wiki (stara): Making Your Models (Blockbench)](https://github.com/bernie-g/geckolib/wiki/Making-Your-Models-(Blockbench));
- [Minecraft EULA](https://www.minecraft.net/en-us/eula) (definicja „Mods”: bez istotnej części treści Mojang); strona Usage Guidelines nie odpowiedziała w czasie;
- licencja Pixel Perfection CC BY-SA 4.0: [Planet Minecraft](https://www.planetminecraft.com/texture-pack/131pixel-perfection/), [Pixel XTended Perfection](https://content.luanti.org/packages/Geraos16/pixel_xt_perfection/);
- Kenney Voxel Pack CC0: [OpenGameArt](https://opengameart.org/content/voxel-pack);
- ambientCG CC0: [Patreon ambientCG](https://www.patreon.com/ambientcg), [AlternativeTo](https://alternativeto.net/software/ambientcg/about); ShareTextures CC0 (kora): [bark-5](https://www.sharetextures.com/textures/wood/bark-5);
- dźwięki: [Freesound FAQ](https://freesound.org/help/faq/), [xeno-canto (Wikipedia)](https://en.wikipedia.org/wiki/Xeno-canto) (za raportem 03);
- schemat geometrii Bedrock 1.12.0: [Microsoft Learn](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/schemasreference/schemas/minecraftschema_geometry_1.12.0) (za raportem 03).

Niezweryfikowane (do sprawdzenia przy kroku):
- dokładny znak obrotów X/Y przy wczytywaniu geometrii w GeckoLib 5;
- czy wtyczka Blockbench dla GeckoLib 5 wymaga własnego typu projektu dla encji;
- jasność pod koronami M3 a próg spawnu;
- dystans ucieczki wiewiórki (własna ocena 8–12 m).
