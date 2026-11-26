# Do sprawdzenia w grze: zbiorcza lista kroków FA1–FA8 i przeglądu FA9 (fauna, dźwięk, drewno, łodzie, mrowiska)

Zebrane w kroku FA9 (`docs/04-pionowy-wycinek.md` „Projekt FA9”) z list „Do sprawdzenia w grze” w „Stan po FA1” … „Stan po FA8” i z rund poprawek. Gdzie runda zmieniła punkt, jest tu wersja po rundzie. Lista trafi na stronę zamknięcia wycinka (FA10). Miejsca wskazują komendy `/polishforests find …`, `fauna`, `here` i `list`, bez stałych współrzędnych (G.37 U-9). Jeśli nie podano inaczej: nowy świat „Polska” (skala rozgrywki, ustawienia domyślne), tryb twórczy z komendami. Wygląd ocenia tylko użytkownik (G.36 U-1).

Paczka: `build/distributions/Polish-Forests-0.3.0-dev.33.mrpack` (krok FA10; `./gradlew assemble -Pmod_version=0.3.0-dev.33`, `python tools/build_mrpack.py`). Punkt 59 dopisał krok FA10 (przelot z fauną i bez); ta lista jest też na stronie zamknięcia wycinka.

## Sarna (FA1, FA2)

1. `/polishforests find biome oak_hornbeam_forest`, teleport, 2–3 minuty w lesie, potem `/polishforests fauna 64`: w pobliżu zwykle 2–5 saren w stanach żer, odpoczynek i ruch.
2. `/polishforests find biome fresh_pine_forest`: sarny stoją na bielicy pod sosnami. Żadna nie stoi na koronach, krzewach ani w wodzie; sarna zepchnięta na krzew sama z niego schodzi w ciągu chwili.
3. `/time set 0` (świt): większość saren pasie się (głowa przy ziemi) albo idzie. `/time set 6000` (południe): więcej leży, zwykle przy krzewach, odnowieniu albo młodniku; część krótko żeruje.
4. Podejście do sarny zwykłym krokiem: podnosi głowę, a z ok. 30 bloków ucieka ze szczekiem i białym, puszonym lustrem; skradając się (Shift) podejdziesz na ok. 20 bloków, biegnąc spłoszysz ją z ok. 40. Inne sarny w pobliżu czuwają albo też uciekają. Uciekająca sarna biegnie animacją biegu i nie wchodzi na liście krzewów.
5. Sarny wędrują po lesie, nie odchodzą daleko w pole; pasą się i leżą (animacje `eat` i `rest`), przy graczu podnoszą głowę (`alert`).
6. Z Serene Seasons, zima (`/season set early_winter` albo inny podsezon zimowy): sarny w grupach 2–6, futro szarobrązowe, kozły w scypule (miękkie, ciemne parostki); `late_autumn`: kozły bez parostków; `mid_autumn`: część kozłów jeszcze z wytartymi parostkami.
7. Z Serene Seasons, lato (`/season set mid_summer`): sarny pojedynczo albo po 2–3, futro rude, kozły z jasnymi wytartymi parostkami; koza z 1–2 cętkowanymi koźlętami, które idą za nią. Potem `/season set early_autumn` (albo dowolny podsezon jesieni lub zimy) i chwila (do 5 s): koźlęta dorastają do pełnej wielkości z futrem podsezonu, `/polishforests fauna 64` nie pokazuje młodych.
8. Zimą (`/season set mid_winter`) w kilku lasach i w nowo odkrytych chunkach: w grupach saren żadnego cętkowanego koźlęcia. Las odwiedzony latem, potem `/season set mid_winter`, odejście ponad 200 bloków i powrót: dawne koźlęta są dorosłe.
9. Bez Serene Seasons (kalendarz zastępczy): futro i parostki zmieniają się z dniem gry według tego samego kalendarza (`/polishforests fauna 64` pokazuje liczby; wygląd jak w punktach 6–7 dla odpowiedniej pory).
10. Odejdź ponad 200 bloków i wróć: sarny z generacji zostają, a liczba w okolicy (`/polishforests fauna 96`) jest podobna i nie rośnie; przy dalszej podróży liczba się nie kumuluje.
11. Sarna z imieniem (plakietka), na smyczy, z jaja albo z `/summon polishforests:roe_deer`: nie znika, gdy odejdziesz ponad 200 bloków (np. `/tp ~300 ~ ~`) i wrócisz.
12. Jajo przyzywające sarnę (zakładka jaj) stawia kozę albo kozła w futrze pory roku.
13. Zapis i wczytanie świata przy grupie saren z koźlętami: grupy i koźlęta przy kozach odtwarzają się po chwili.

## Dzik (FA3)

14. `/polishforests find biome oak_hornbeam_forest`, `/time set night`, chwila w lesie, `/polishforests fauna 64`: dziki w pobliżu, watahy w ruchu, żerują i ryją (stan „ryje”); późną wiosną i latem warchlaki w jasne pasy idą za lochą.
15. Ten sam las, `/time set noon`: dziki leżą w barłogu, zwykle w gęstwinie; na ziemi świeże buchtowiska: płaty 2–4 bloków ziemi gruboziarnistej i ukorzenionej, bez roślin nad nimi i bez połówek wysokich roślin.
16. Wygląd: odyniec ciemny, z grzywą i szablami; locha cieplejsza, bez szabel; warchlak w pasy; animacje chodu, biegu, rycia, leżenia i czujności płynne, bez skoków modelu.
17. `/polishforests find biome alder_carr` (i `ash_alder_forest`), za dnia: dzik leży na brzegu przy wodzie i tarza się (stan „tarza się w błocie”), czasem z błotem pod sobą; nie wchodzi do wody.
18. `/gamerule polishforests:boar_rooting false`, kilka nocy: nie powstają nowe buchtowiska; to samo przy `/gamerule mob_griefing false`; po przywróceniu obu reguł płaty znów powstają.
19. Wiosną (nowy świat albo `/season set late_spring`), tryb przetrwania, podejście pieszo do lochy z warchlakami: locha nie ucieka, szarżuje z chrapnięciem, staje 2–3 bloki przed graczem i nie zadaje obrażeń; po chwili ucieka, a wataha idzie za nią gęsiego.
20. Latem (`/season set mid_summer`) albo przy odyńcu: dzik ucieka z ok. 20 bloków (przed biegnącym graczem z dalszej odległości) z chrapnięciem, sąsiednie dziki i sarny też się płoszą.
21. `/polishforests fauna 64` w kilku lasach (grąd, łęg, bór mieszany, bór świeży): zwykle 1–3 dziki wobec 2–5 saren; w borze świeżym dzików mniej albo wcale.
22. Zapis i wczytanie świata przy watasze: wataha odtwarza się po chwili i dalej idzie za lochą. Jajo przyzywające dzika daje lochę albo odyńca (ok. 30%).

## Pejzaż dźwiękowy i żaba trawna (FA4)

23. Napisy włączone (Opcje → Dostępność → Napisy). `/polishforests find biome oak_hornbeam_forest`, `/time set 23500` (świt): śpiew zięby, kosa, rudzika i bogatki z różnych drzew, napisy „Zięba śpiewa”, „Kos śpiewa” itd.; o świcie i o zmierzchu wyraźnie gęściej niż w południe (`/time set noon`).
24. `/polishforests find biome fresh_pine_forest`, `/time set midnight`: pohukiwanie puszczyka, żadnych ptaków dnia; za dnia sosnówka i mysikrólik.
25. Zima (`/season set mid_winter`): wyraźnie ciszej, tylko sikory, mysikrólik, dzięcioł, sójka i puszczyk. `/gamerule polishforests:forest_ambience false` wyłącza pejzaż (zwierzęta zostają). Głośność reguluje suwak „Otoczenie/Środowisko”.
26. Szczek sarny i chrapnięcie dzika to własne dźwięki; dzik czasem chrząka.
27. `/polishforests find biome alder_carr`, chwila przy wodzie: brązowe żaby trawne z ciemną plamą za okiem, na lądzie przy kałużach i na błocie, z rechotem; `/summon minecraft:frog` w olsie też daje żabę trawną. W grądzie przy błotnistych kałużach pojedyncze żaby; zimą (`/season set early_winter`) żab spawnera nie przybywa.
28. W olsie z żabami `/gamemode spectator` i ponad minuta w miejscu: żaby zostają; po powrocie do przetrwania nowych nie przybywa ponad zwykłą liczbę. Odlot ponad 150 bloków: żaby spawnera przy dalekiej wodzie znikają (gdy chunki zostały wczytane).
29. Dwóch graczy obok siebie (LAN): każdy słyszy pejzaż o zwykłej gęstości, bez podwojenia.

## Wiewiórka (FA5)

30. `/polishforests find biome mixed_pine_forest`, `/time set 1000`: wiewiórki na pniach i w koronach, skoki z pnia na pień przez liście, żer na ziemi i na leżących kłodach z szyszką w łapach.
31. Podejście sprintem do wiewiórki na ziemi obok starej sosny z nabiegami: podskakuje na pień i w kilka sekund wspina się ≥ 4 bloki; nie wisi w powietrzu obok pnia i nie biega w kółko pod nim. Na drzewie po podejściu: wchodzi wyżej, przechodzi na drugą stronę pnia albo skacze do następnej korony.
32. Na grubej starej sośnie, dębie albo grabie (`find biome mixed_pine_forest`, `find biome oak_hornbeam_forest`) wiewiórka mija miejsca, gdzie pień cienieje, bez zatrzymania i zawracania, także głową w dół aż do ziemi; nie wchodzi łapami w szerszą część pnia pod sobą.
33. Przez kilka minut obserwacji żadna wiewiórka nie tkwi w pniu, gałęzi ani liściach, nie stoi w powietrzu i nie traci zdrowia po skoku w dół.
34. `/polishforests find biome montane_spruce_forest`: wspinaczka po świerkach, częściej ciemnobrązowe wiewiórki. W borze świeżym i grądzie wiewiórki też są (w grądzie rzadziej), większość na drzewach.
35. Zima (`/season set mid_winter`): szata z szarawym nalotem i pędzelki uszu; po 14:30 wiewiórki odpoczywają w koronach. Noc (`/time set 18000`): uczepione w koronach, bez biegania po ziemi.
36. `/polishforests fauna 32`: wiewiórki ze stanem „wspina się” / „na drzewie”. Po wyjściu ze świata i powrocie wiewiórka z korony nie zostaje w liściach.

## Sójka (FA6)

37. `/polishforests find biome oak_hornbeam_forest`, za dnia: sójki lecą pod koronami drzew okapu, między pniami i przez luki między koronami; mogą przelatywać nisko nad krzewami i młodymi drzewami, ale nie suną tuż nad wierzchem koron okapu i nie przelatują przez liście i pnie.
38. Sójki siadają na wierzchu liści niższych koron i na krawędziach koron, nie zawisają w powietrzu i nie tkwią w liściach; na ziemi skaczą i dziobią.
39. Sprint przez grąd: sójka, która widzi gracza, skrzeczy i odlatuje; sarny i dziki w pobliżu uciekają albo stają czujnie. Jeden skrzek naraz, bez podwójnego; daleko od sójek skrzek pejzażu jak w punkcie 23–25.
40. Jesień (`/season set mid_autumn`): sójki w grądzie podnoszą żołędzie (widać w dziobie), przelatują i chowają je w ziemi, bez zmiany bloków. Wieczór (`/time set 13000`): siedzą na grzędach w koronach.
41. `/polishforests fauna 32`: sójki ze stanami „leci” / „siedzi na gałęzi” / „chowa żołądź”. `find biome lowland_beech_forest` i `find biome mixed_forest` w obu skalach: sójki także tu (rzadziej w borach).
42. Animacje: bicie skrzydeł w locie, ślizg przy zniżaniu, skoki po ziemi, czubek stroszony w alarmie.

## Wyroby z drewna (FA7)

43. Zakładka „Budulec”: za furtką każdego z 15 drewien moda jego drzwi i klapa; „Bloki użytkowe”: półki, tabliczki i tabliczki wiszące każdego drewna moda. Ikony nie są szachownicą.
44. `/polishforests find biome lowland_beech_forest`, ścięcie buka: z desek drzwi (3), klapa (2), tabliczka (3); z okorowanych kłód półka, z łańcuchami tabliczka wisząca (6). Książka receptur pokazuje je w grupach z wyrobami wanilii.
45. Drzwi, klapy, tabliczki i półki kilku gatunków obok siebie: kolor desek gatunku, okucia, szybki i łańcuchy jak w wanilii; iglaste ze wzorem świerkowym, liściaste dębowym.
46. Drzwi i klapy otwierane ręką, przyciskiem i dźwignią; drzwi po zniszczeniu dają jeden przedmiot; osadnik otwiera drzwi moda jak dębowe.
47. Tabliczki stojące, ścienne i wiszące: ekran edycji z drewnem gatunku, napis widoczny, barwnik i świecący atrament działają; po wyjściu ze świata napisy zostają.
48. Półka: prawy klik wkłada i zamienia przedmioty, widać je na półce; zostają po powrocie do świata, wypadają po zniszczeniu. Ogień zapala półkę, tabliczka pali się w lawie; wszystkie wyroby są paliwem w piecu.

## Łodzie i mrowiska (FA8)

49. `/polishforests find biome lake`: łódź z desek bukowych albo limbowych (5 desek w kształcie litery U) pływa, wsiadasz prawym klikiem, płynie i skręca jak łódź wanilii; drewno w kolorze desek gatunku. Łódź ze skrzynią: przedmioty zostają po wyjściu ze świata; rozbita łódź daje swój przedmiot.
50. `/polishforests find anthill`, teleport na podane współrzędne (klik): kopiec z igliwia (1–2 bloki wysokości, 2–3 szerokości) na skraju boru, w luce albo w widnym starym borze, nie przy pniu. Jeśli komenda podała pozycję z planu (komunikat, że chunk nie był wczytany), po teleporcie drugie `find anthill` wskazuje kopiec, który stoi (w tym chunku albo następny najbliższy). Za dnia, bez deszczu, od kwietnia do października po kopcu chodzą mrówki; nocą i w deszczu nie.
51. Kilka razy `find anthill` z różnych miejsc boru (`find biome fresh_pine_forest`): kopce w borach mniej więcej co sto metrów (ok. 0,65 kopca na hektar boru), w lesie mieszanym rzadko, w grądzie i buczynie wcale.
52. Łopata zbiera mrowisko warstwami; stawiane warstwy rosną jak śnieg; mrowisko się pali.
53. F3 + T (przeładowanie zasobów): tekstury desek, drzwi, klap, tabliczek z ekranami edycji, półek, łodzi i ich przedmiotów zostają (nie zmieniają się w szachownicę).

## Świat „Default + Polish Forests” i reguły gry

54. Świat „Default + Polish Forests”: `find biome oak_hornbeam_forest` i `find biome fresh_pine_forest`, kilka minut w lesie (`/polishforests fauna 96`): sarny, dziki, wiewiórki i sójki w lasach moda (w małych płatach pojawiają się po kilku minutach), wiewiórki wspinają się i sójki siadają też na drzewach wanilii; o świcie ten sam śpiew; `find biome alder_carr` (w tym świecie bagno) — żaby trawne; `find anthill` znajduje kopiec w borze.
55. W obu typach świata wilki, lisy i króliki nadal są w lasach (`/polishforests fauna 96`, linie wanilii).
56. `/gamerule polishforests:forest_fauna false`, potem nowe chunki: zwierzęta moda się nie pojawiają, istniejące zostają, `/polishforests fauna` pisze, że reguła jest wyłączona.

57. (Przegląd FA9, runda 1, DP-178.) Dwa nowe światy „Polska” (GAMEPLAY) z tym samym ziarnem: w każdym od razu `/polishforests find biome oak_hornbeam_forest`, teleport i `/polishforests fauna 64` przed upływem minuty. Liczba saren, dzików, wiewiórek i sójek z generacji jest w obu światach taka sama (różnić mogą się tylko zwierzęta spawnera, które dochodzą później).

## Paczka

58. Paczka `Polish-Forests-0.3.0-dev.33.mrpack` importuje się w Modrinth App albo Prism Launcher (Minecraft 26.3, Fabric) i startuje bez błędów, z Serene Seasons i FallingTree (opcjonalny) z konfiguracją z paczki.

## Płynność z fauną (FA10)

59. Przelot przez las z fauną i bez (płynność). Nowy świat „Polska” (skala rozgrywki) z paczki `0.3.0-dev.33` (Sodium, C2ME), tryb twórczy, F3 pokazuje FPS. `/polishforests find biome oak_hornbeam_forest`, teleport, `/polishforests fauna 64` (sarny, dziki, wiewiórki, sójki w pobliżu), potem kilka minut lotu: nisko między pniami i nad okapem, także w `find biome fresh_pine_forest` i `find biome lowland_beech_forest`. Następnie `/gamerule polishforests:forest_fauna false` i ten sam lot w nowe chunki (np. 500 bloków dalej): płynność i FPS bez wyraźnej różnicy (pomiar FA10: z fauną 0,92–1,00 × FPS bez fauny), bez przycięć przy wczytywaniu chunków ze zwierzętami, zwierzęta nie przeskakują i nie drgają przy granicy chunków. Na koniec `/gamerule polishforests:forest_fauna true`.
