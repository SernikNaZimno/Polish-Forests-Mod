# Pionowy wycinek: ekologia grądu i boru świeżego (raport badawczy, 2026-10-14)

Zakres: grąd (Tilio-Carpinetum, Galio sylvatici-Carpinetum; Natura 2000 9170, porównawczo 9160) i bór świeży (Leucobryo-Pinetum, wariant wschodni Peucedano-Pinetum). Ten raport uzupełnia `docs/research/06-lasy-polski-fitosocjologia-stl.md` (§3.1, §3.5, §4, §9), `07-fauna...` i `08-klimat...`. Nie powtarza ich tabel, tylko dodaje liczby sprawdzone w źródłach i przelicza je na grę. Oznaczenia: **[Ź]** liczba odczytana ze źródła (URL w §11), **[L]** liczba z lokalnych raportów `docs/research/`, **[O]** wartość orientacyjna, czyli wiedza ogólna albo przeliczenie, do weryfikacji.

Przeliczenia do gry: 1 ha = 100 × 100 bloków ≈ 39 chunków. Skala drzew 1:1 (1 blok = 1 m).

## 0. Najważniejsze liczby dla wycinka

1. **Grąd naturalny (Rezerwat Ścisły BPN, 160 powierzchni stałych):** zasobność 446 ± 38 m³/ha, 588 ± 53 drzew/ha o pierśnicy ≥ 8 cm (2015) **[Ź Miścicki 2016]**. W grze to ok. **15 pni na chunk**, licząc wszystkie piętra. Obecny `oak_hornbeam_forest` ma 9.
2. **Grąd gospodarczy, przykład z monitoringu GIOŚ (Miechowice):** warstwa A: lipa 50 %, dąb 15 %, grab 10 %, czereśnia 2 %. Przeciętna pierśnica przy II bonitacji: dąb 35–37 cm w wieku 100 lat, 42–44 cm w wieku 120, 47,5–50 cm w wieku 140; buk 28 / 33 / 40 cm **[Ź GIOŚ warsztaty]**. Wniosek: nawet stary dąb w lesie gospodarczym ma pień o grubości ok. 0,5 bloku, więc zmienna grubość pni (D8) jest konieczna.
3. **Martwe drewno:**
   - Lasy Państwowe 5,2 m³/ha, parki narodowe 35,8 m³/ha (WISL 2006–2010). Drzewa stojące 2,1 i 12,0 m³/ha, leżące 3,1 i 23,8 m³/ha **[Ź Mróz i Pawlaczyk]**.
   - Według WISL 2010–2014: grądy 13 m³/ha, bory i bory mieszane nizinne 5 m³/ha **[L]**.
   - W grądach BPN średnio ok. **130 m³/ha** **[Ź GIOŚ, konarek tajgowy]**.
   - Rezerwat sosnowy „Sosna Taborska” (261 lat) ok. 65 m³/ha, a sąsiedni drzewostan gospodarczy (136 lat) ok. 11 m³/ha **[Ź Piętka i in. 2019]**.
4. **Norma GIOŚ dla grądu:** grube martwe drewno, czyli kawałki dłuższe niż 3 m i grubsze niż 50 cm. Stan właściwy (FV) to > 5 szt./ha, niezadowalający (U1) 3–5 szt./ha, zły (U2) < 3 szt./ha. Łączne zasoby: FV > 10 % miąższości drzewostanu żywego, U1 3–10 %, U2 < 3 % (kalibracja dla kwaśnych dąbrów 9190) **[Ź Mróz i Pawlaczyk]**. W latach 2013–2014 ten wskaźnik osiągnęło tylko 18 % stanowisk grądu w regionie kontynentalnym, a 52 % miało < 3 szt./ha **[Ź GIOŚ 9170]**.
5. **Dopływ martwego drewna w lesie naturalnym:** w Rezerwacie Ścisłym BPN ubywa (zamiera) 9,13 m³/ha/rok, a przyrasta 8,96 m³/ha/rok. Najwięcej ubytków dają świerk (37 %), dąb (18 %) i jesion (17 %), lipa tylko 2,5 % **[Ź Miścicki 2016]**. To dobry parametr do dynamiki lasu (D11).
6. **Światło w grądzie:** pod pełnym okapem na dno lasu dociera < 2 % promieniowania fotosyntetycznie czynnego (PAR) **[Ź PMC 2024]**. Przed ulistnieniem dociera ok. 47 % (las dębowo-hikorowy, analogia) **[Ź ECNU]**. Geofity rosną od końca marca do końca maja i „w ciągu kilku dni” po ulistnieniu drzew znikają, a ich biomasa rozkłada się w ok. 2 miesiące **[Ź Rawlik 2021]**. Zawilec gajowy w grądzie tworzy > 1000 pędów/m² **[Ź Plant Ecology 2020]**.
7. **Bór świeży, przykłady z Janowa Lubelskiego:** zwarcie drzewostanu 0,6. Najdorodniejsze sosny mają 26 m i 35 cm albo 32 m i 45 cm pierśnicy. Podszyt ma zwarcie 0,4–0,5, a runo pokrywa 60 % i ma biomasę 1548 kg/ha, z czego borówka czarna daje 71,7 %, a borówka, wrzos i brusznica razem 96,3 %. W warstwie mchów dominuje rokietnik **[Ź Popiołek 1978]**.
8. **Odnowienie sosny:** w NE Polsce sosna w borach świeżych prawie nie odnawia się naturalnie. Ponad 95 % samosiewów rośnie w bruzdach, czyli na odsłoniętej glebie mineralnej **[Ź Sokołowski i Paluch 2004; Paluch]**. W grze sadzonki sosny w starym borze powinny być rzadkie: pojawiają się na zrębach, w lukach, na drogach i na wykrotach.
9. **Mrowiska rudnic w Puszczy Białowieskiej (1400 ha):** średnio tylko **0,13 zamieszkanego kopca na ha**, najwięcej w lasach i borach mieszanych świeżych **[Ź Sondej i in. 2018]**. Wartość 0,5–5/ha z `06 §4.5` jest za wysoka. W grze to ok. 1 kopiec na 300 chunków.
10. **Ptaki lęgowe BPN:** najmniej w starych drzewostanach iglastych (48–50 par/10 ha), najwięcej w łęgach na skraju puszczy (do 124 par/10 ha), grąd pośrodku. Grubodziób w grądzie ma 5,8 pary/10 ha **[Ź Wesołowski i in. 2002; Tomiałojć 2005]**.

## 1. Nazewnictwo, zasięg i monitoring

| Jednostka | Natura 2000 | Region | Uwagi dla wycinka |
|---|---|---|---|
| Tilio-Carpinetum (grąd subkontynentalny) | 9170-2 | Polska środkowa, wschodnia i południowa | wyróżniki: turzyca orzęsiona, przytulia Schultesa, gwiazdnica wielkokwiatowa; na NE świerk w drzewostanie **[Ź GIOŚ 9170; L]** |
| Galio sylvatici-Carpinetum (grąd środkowoeuropejski) | 9170-1 | Polska zachodnia i południowo-zachodnia | wyróżniki: przytulia leśna i świerząbek; bez świerka, za to buk i bluszcz **[L]** |
| Stellario-Carpinetum (grąd subatlantycki) | 9160 | Pomorze | tylko porównawczo |
| Leucobryo-Pinetum (bór świeży suboceaniczny) | **brak** (to nie jest siedlisko Natura 2000, więc GIOŚ go nie monitoruje) | „niemal cała Polska poza NE, pobrzeżem i górami”, najczęściej w Borach Tucholskich i Stobrawskich **[Ź Wikipedia]** | Wikipedia (za Matuszkiewiczem 2007) daje zasięg szerszy niż „na zachód od Wisły” w `06 §3.1` |
| Peucedano-Pinetum | brak, poza postacią chrobotkową (91T0) | Polska wschodnia i NE | wariant regionalny boru świeżego **[L]** |

- **Udział w lasach:** siedliska Bśw zajmują 23,6 % powierzchni LP, a BMśw 24,7 % (CILP 2006) **[Ź Stefańska 2007]**. Grądy to 41,6 % potencjalnej roślinności naturalnej Polski, ale dziś kilka procent lasów **[L]**.
- **Gatunki wskaźnikowe Bśw (Bory Dolnośląskie, 464 powierzchnie):** wrzos, chrobotki (Cladonia chlorophaea, C. gracilis, C. macilenta), widłoząb (Dicranum spurium), Ptilidium ciliare. Wyróżniające: płucnica islandzka, C. arbuscula, C. rangiferina.
- **Wskaźniki BMśw**, przydatne jako granica ekotonu: turzyca pigułkowata, Brachythecium rutabulum, Lophocolea; wyróżniające: orlica, Pseudoscleropodium purum **[Ź Stefańska 2007]**.

## 2. Grąd: warstwy i liczby

| Warstwa | Wysokość | Pokrycie | Skład (udziały) | Źródło |
|---|---|---|---|---|
| a1 (górne piętro) | las gospodarczy 25–35 m; naturalny 30–42 m (rekord dębu 43,6 m, lipa > 37 m, świerk do 50 m w BPN) | razem z a2 80–95 % | dąb szypułkowy, lipa drobnolistna, klon, jesion, wiąz górski; na NE świerk. Przykład GIOŚ: Lp 50, Db 15, Gb 10, Czr 2 | [L], [Ź GIOŚ warsztaty] |
| a2 (dolne piętro) | 15–22 m | 40–90 % | grab dominuje (60–90 % pokrycia a2), lipa, klon | [L] |
| b (podszyt i podrost) | 1–7 m | 10–40 % | leszczyna, trzmielina, wiciokrzew, jarząb, podrost grabu i lipy | [Ź GIOŚ warsztaty; L] |
| c (runo) | 5–60 cm | wiosną 60–100 %, latem 20–60 % | patrz §3; w grądzie z Janowa Lubelskiego 35 gatunków i 8,76 mln osobników/ha (ok. 880/m²), ale biomasa tylko 136 kg/ha | [Ź Popiołek 1978; Sałata 1977] |
| d (mchy) | < 5 cm | 5–20 %, często prawie brak | Atrichum, Plagiomnium, Eurhynchium, Polytrichum formosum | [L; Ź Sałata 1977] |
| ściółka | — | cienka (mull), latem prawie znika | liście grabu, lipy i dębu na ciemnej glebie gruzełkowatej | [L] |

- **Dynamika lasu naturalnego (BPN):** przyrost 8,96 m³/ha/rok. Udział gatunków w przyroście: świerk 29 %, lipa 16 %, grab 15 %, olsza 13 %, dąb 13 % (cały rezerwat, z olsami). Zasobność lipy, grabu i olszy rośnie, a jesionu, świerka i osiki maleje. W latach 2000–2015 z odnowienia do drzewostanu przechodziło tylko 6 gatunków, z czego grab dawał 46 % miąższości dorostu **[Ź Miścicki 2016]**.
- **Luki:** naturalne luki w grądach BPN mają 250–1610 m², czyli średnicę ok. 18–45 bloków, i powstają głównie po zamarciu grup świerków. Odnowień jest w nich 4 razy więcej niż pod okapem, a wysokich (> 1,3 m) 8 razy więcej. Lukę wypełniają grab i lipa niezależnie od jej wielkości. Brzoza, osika, iwa, jarząb i olsza odnawiają się prawie wyłącznie w lukach **[Ź baztech BGPK-1801-6813]**.
- **Dąb:** w grądzie lipowo-dębowo-grabowym BPN rosną tylko 2 podrosty dębu wyższe niż 0,5 m na ha **[Ź Bobiec i in. 2011]**. W lesie naturalnym dąb występuje jako stare drzewa bez następców, a w gospodarczym jako sadzony młodnik w gniazdach.
- **Wskaźniki GIOŚ 9170** (2013–2014, region kontynentalny, 174 stanowiska) **[Ź GIOŚ 9170]**:
  - naturalne odnowienie jest właściwe na 68 % stanowisk, „obfite, głównie w przewietleniach i lukach”;
  - zróżnicowana struktura pionowa na 60 %;
  - drzewa > 100 lat stanowią > 10 % miąższości na 52 % stanowisk;
  - gatunki obce: w runie najczęściej niecierpek drobnokwiatowy, w drzewostanie dąb czerwony, robinia i czeremcha amerykańska. To pasuje do opcji D10.
- **Gatunki regionalnie charakterystyczne dla grądu subkontynentalnego:** trzmielina, przytulia Schultesa, jaskier kaszubski **[Ź Woziwoda]**.

## 3. Grąd: fenologia runa i mapowanie na sub-sezony SS

W projekcie Early Spring odpowiada marcowi, a sub-sezon trwa 12 dni (B2, B3). Terminy dla Polski środkowej. Na zachodzie fazy przypadają o 5–10 dni wcześniej, na NE o 2–3 tygodnie później **[L 08 §7.3]**.

| Sub-sezon SS (miesiąc) | Okap | Runo grądu (kwitnie lub wygląda) | Źródło |
|---|---|---|---|
| Late Winter (II) | bez liści | leszczyna pyli; śnieżyczka (Galanthus) lokalnie | [L] |
| Early Spring (III) | bez liści, PAR na dnie ok. 50 % | przylaszczka (fioletowa) jako pierwsza, potem złoć żółta, wawrzynek; liście ziarnopłonu i kokoryczy | [Ź Rawlik 2021; L] |
| Mid Spring (IV) | grab i czeremcha zaczynają się listnić (koniec IV) | **szczyt aspektu:** biały łan zawilca gajowego (> 1000 pędów/m²), zawilec żółty, kokorycz pusta (fioletowa i biała), ziarnopłon (żółty), fiołek leśny, szczyr, kopytnik; czosnek niedźwiedzi (liście) w grądzie niskim | [Ź Rawlik; RDOŚ; Plant Ecology 2020] |
| Late Spring (V) | dąb i lipa się listnią (ok. 5 V), okap zamyka się | geofity żółkną i „w ciągu kilku dni” znikają; kwitną gajowiec, marzanka, gwiazdnica wielkokwiatowa, konwalia, czworolist, czosnek niedźwiedzi | [Ź Rawlik; L] |
| Early Summer (VI) | pełny okap, < 2 % PAR | aspekt letni: niecierpek pospolity, czyściec leśny, prosownica, perłówka, turzyca orzęsiona; nad ziemią nie ma już geofitów | [Ź PMC 2024; L] |
| Mid i Late Summer (VII–VIII) | pełny | runo ubogie (20–50 %), paprocie, kopytnik; kwitnie lilia złotogłów (rzadka) | [L] |
| Early i Mid Autumn (IX–X) | przebarwienie: grab i lipa żółte, dąb brązowy (późno) | grzyby (szczyt IX–X), opad liści | [L] |
| Late Autumn i zima (XI–I) | bez liści | brązowa ściółka; zielone zostają kopytnik, bluszcz (zachód) i mchy | [L] |

Wniosek dla bloków: geofity grądu najlepiej zrobić jako rośliny ze stanem sezonu: liść, kwiat, zanik do pustej ściółki od Early Summer. Inaczej aspekt wiosenny nie zadziała, bo w Minecrafcie runo nie znika samo.

## 4. Bór świeży (Leucobryo-Pinetum): warstwy i liczby

| Warstwa | Wysokość | Pokrycie | Skład | Źródło |
|---|---|---|---|---|
| a1 | 20–28 m (dorodne sosny 26–32 m); pierśnica dojrzałych 25–45 cm | zwarcie 0,6 (60–80 %) | sosna 80–95 %, brzoza brodawkowata 5–15 %, domieszka dębu bezszypułkowego, na W buka, na NE świerka | [Ź Popiołek; Wikipedia; L] |
| a2 | 8–15 m | 0–20 % | świerk (do 15 m i 15 cm), dąb, brzoza | [Ź Popiołek] |
| b | 0,5–5 m | 5–30 % (zwarcie 0,4–0,5) | jałowiec, kruszyna, jarząb; podrost dębu, sosny (tylko w lukach) i jodły na S | [Ź Wikipedia; Popiołek] |
| c | 10–40 cm (orlica do 1,5 m) | 40–80 % (przykład: 60 %) | borówka czarna dominuje (ok. 70 % biomasy runa), brusznica, wrzos, śmiałek pogięty, pszeniec zwyczajny, konwalijka, siódmaczek, gruszyczki | [Ź Popiołek; L] |
| d | 3–10 cm, kępy i darnie | 60–95 % | rokietnik (Pleurozium) do 83 % biomasy mchów, gajnik, widłoząb, poduchy bielistki, płonnik; chrobotki w suchszych płatach | [Ź Popiołek; Wikipedia] |
| ściółka | — | gruba warstwa butwiny (mor, 5–15 cm) | igliwie i szyszki na bielicy (biały poziom Ees) | [L 06 §4.2] |

- **Las gospodarczy** to las jednowiekowy, a rozstaw drzew zależy od wieku **[L 06 §6.1, 6.4]**:
  - 50 lat: 600–900 drzew/ha (15–23 na chunk, odstęp 3–4 bloki);
  - 100 lat: 300–400 drzew/ha (8–10 na chunk);
  - młodniki mają rzędy co ok. 1,5 m, a starsze drzewostany pniaki po trzebieżach.

  Obecny `fresh_pine_forest` ma 11 drzew na chunk, co odpowiada drzewostanowi ok. 80–90-letniemu.
- **Las „naturalny”:** to raczej ciąg dynamiczny niż jeden stan. Pożary i wiatrołomy dają różnowiekowe kępy, a sosna odnawia się w lukach na odsłoniętym piasku. Pod okapem wchodzą świerk na NE, dąb i grab (sukcesja). Martwego drewna jest ok. 6 razy więcej niż w gospodarczym (65 i 11 m³/ha, Taborz) **[Ź Piętka i in.; Sokołowski i Paluch]**.
- **Sosna w borze nie odnawia się w cieniu** **[Ź Paluch]**. W grze w starym borze nie powinno być gęstego podrostu sosny: dąb i jarząb tak, sosna tylko w lukach.

## 5. Martwe drewno: normy i przeliczenie na bloki

| Stan | m³/ha | Stojące : leżące | Źródło |
|---|---|---|---|
| Polska, wszystkie lasy (WISL 2010–2014) | 9,6 (leżanina 4,2, posusz 2,8, pniaki 2,6) | ok. 40 : 60 | [L] |
| LP / parki narodowe (WISL 2006–2010) | 5,2 / 35,8 | 2,1 : 3,1 / 12,0 : 23,8 | [Ź Mróz i Pawlaczyk] |
| Bory i bory mieszane nizinne | 5 | — | [L] |
| Grądy (gospodarcze, średnio) | 13 | — | [L] |
| Bór sosnowy gospodarczy / rezerwat (Taborz) | 11 / 65 | — | [Ź Piętka i in.] |
| Grądy BPN (naturalne) | ok. 130 | ok. 1/3 stojące [O] | [Ź GIOŚ konarek] |
| Puszcza Białowieska, wysoka naturalność | 120 leżącego, razem 130–140 | — | [Ź Wikipedia: Martwe drewno w lesie] |
| Progi europejskie | bory 20–30, lasy liściaste niżowe 30–50 | — | [Ź Müller i Bütler 2010 za Mróz i Pawlaczyk] |
| Proponowany próg GIOŚ (dyskusja) | FV 40, U1 20 | — | [Ź Mróz i Pawlaczyk] |

Struktura kawałków **[L, WISL]**: 75 % martwych drzew stojących ma pierśnicę < 25 cm, 55 % leżaniny ma średnicę < 25 cm, a ok. 80 % leżących to fragmenty, nie całe drzewa. Grube kawałki pochodzą głównie z wykrotów i złomów.

**Przeliczenie na obiekty w grze [O]:** objętość kawałka V ≈ 0,785 · d² · L.

| Obiekt w grze | Wymiar | Objętość |
|---|---|---|
| gałęziówka | d 0,15 m × 4 m | ≈ 0,07 m³ |
| kłoda średnia | d 0,35 m × 10 m | ≈ 1 m³ |
| kłoda gruba (norma GIOŚ) | d 0,55 m × 15 m | ≈ 3,5 m³ |
| posusz | d 0,35 m × 15 m, ze zbieżystością | ≈ 1 m³ |

| Wariant | Na ha (100 × 100 bl) | Na chunk |
|---|---|---|
| Bór gospodarczy (5 m³/ha) | 2 kłody średnie, 1 posusz, ok. 10 gałęziówek, 30–80 pniaków [O] | ok. 0,1 kłody, 1–2 pniaki |
| Grąd gospodarczy (13 m³/ha) | 1 gruba, 5 średnich, 2–3 posusze, ok. 15 gałęziówek | ok. 0,25 obiektu |
| Grąd stan właściwy GIOŚ (≥ 5 grubych/ha, ok. 30–40 m³/ha) | 5 grubych, 10 średnich, 5 posuszy | ok. 0,5 |
| Rezerwat sosnowy (65 m³/ha) | 8 grubych, 25 średnich, 8 posuszy, wykroty | ok. 1 |
| Grąd naturalny BPN (130 m³/ha) | 20 grubych, 45 średnich, 15 posuszy, 3–5 wykrotów; złomy dębów, „rury” brzozy | ok. 2 |

- **Forma śmierci według gatunku** **[L 06 §9.2; Ź Miścicki]**:
  - świerk: wykroty i posusz po korniku (największy udział w ubytkach);
  - dąb: stoi długo jako posusz, gubi konary, ma dziuple;
  - jesion: zamierające korony (zamieranie jesionu);
  - grab i lipa: rzadko zamierają (4 % i 2,5 % ubytków);
  - sosna: posusz i złomy, wykroty na płytkich glebach.
- **Pniaki** dotyczą tylko lasu gospodarczego (do 80 % martwego drewna na zrębach **[L]**). Las naturalny pniaków po ścince nie ma.

## 6. Grzyby typowe

**Grąd** (Puszcza Kozienicka, 1971–1972) **[Ź Sałata 1977]**:
- W grądzie z jodłą stwierdzono 89 gatunków, w grądzie niskim 35. Dominują saprotrofy ściółki: Gymnopus (Collybia) peronatus, Clitocybe gibba i C. odora, gołąbek (Lepista nuda), purchawka chropowata, pierścieniak grynszpanowy, sromotnik bezwstydny.
- Na drewnie liściastym: Bjerkandera adusta, skórnik szorstki (Stereum hirsutum), próchnilce (Xylaria hypoxylon, X. polymorpha), grzybówki, opieńka.
- Mikoryzowe, według wiedzy mykologicznej **[O, L 06 §4.4]**: muchomor sromotnikowy (dąb, grab, VII–X), koźlarz grabowy (pod grabem), borowik usiatkowany (dąb, VI–IX), gołąbki, lejkowiec dęty (buk i grab). Wiosną smardze pod jesionem (IV–V).
- Nadrzewne, całoroczne: żagiew łuskowata (V–VII), żółciak siarkowy (dąb), ozorek dębowy, wachlarzowiec.

**Bór świeży** **[Ź Sałata 1977; L 06 §4.4]**:
- W Leucobryo-Pinetum (z jodłą) stwierdzono 63 gatunki, w tym piestrzenicę kasztanowatą „gromadnie na piaszczystej glebie” w maju, kolczakowate i gołąbki.
- Gatunki zbierane: borowik szlachetny, podgrzybek brunatny (najpospolitszy), kurka (VI–X), maślak sitarz (VII–XI), gąska zielonka (IX–XI), siedzuń sosnowy u nasady sosen (VIII–X), muchomor czerwony (pod brzozą), lisówka pomarańczowa. Na pniakach sosny łuszczaki i opieńka.
- Szczyt owocnikowania przypada na VIII–X, po deszczach (D4: grzybobranie).

## 7. Fauna (wybór do wycinka)

| Grupa | Grąd | Bór świeży | Źródło |
|---|---|---|---|
| Ssaki | sarna, jeleń, dzik, lis, borsuk (nory w zboczach), kuna leśna, wiewiórka, orzesznica (leszczyna), mysz leśna, nornica ruda, borowiec wielki (dziuple) | sarna, jeleń, dzik, lis, wiewiórka (szyszki), kuna; zając na skrajach | [L 07 §2–4] |
| Ptaki | zięba (najliczniejsza), rudzik, kos, śpiewak, bogatka, modraszka, kowalik, pełzacz leśny, świstunka, muchołówka białoszyja (E), grubodziób (5,8 pary/10 ha w BPN), dzięcioł duży i średni (stare dęby), puszczyk, sójka, siniak | zięba, sosnówka, czubatka, mysikrólik, świergotek drzewny, lerka i lelek (luki i zręby), krzyżodziób, dzięcioł duży („kuźnie” z szyszek), dzięcioł czarny, kruk; głuszec tylko w ostojach | [Ź Tomiałojć 2005; Wesołowski 2002; L 07] |
| Płazy i gady | żaba trawna, ropucha szara, traszka zwyczajna, padalec (salamandra tylko w górach) | jaszczurka zwinka i żyworodna, żmija zygzakowata, padalec; ropucha w czasie wędrówek | [L 07 §7] |
| Owady i inne | chrabąszcz majowy (rójka V), jelonek rogacz (stare dęby, W i S), szerszeń (dziuple), biegacze, winniczek, kleszcz | mrówka rudnica (kopce na skrajach i w lukach, ok. 0,13 zamieszkanego kopca/ha), żuk leśny, barczatka sosnówka, strzygonia choinówka i brudnica mniszka (gradacje), kleszcz | [Ź Sondej i in. 2018; L 07 §9] |

Zagęszczenie kopytnych do spawnu **[L 07 §2.2]**: sarna 3–8 os./km², jeleń 0,7–6, dzik 0,2–1 (po ASF). Na 1 km² (ok. 3900 chunków) to kilka osobników, czyli rzadkie spotkania. Propozycja minimum wycinka (M6/M7 po kawałku): sarna i dzik w obu lasach, wiewiórka, dzięcioł duży, żaba trawna i ropucha (wiosenna wędrówka w grądzie). Mrowisko i ślady (buchtowiska, kuźnie) jako bloki lub struktury, bez encji.

## 8. Ekoton grąd – bór świeży

- **Ciąg siedlisk:** grąd i bór świeży leżą 2–3 stopnie trofii od siebie (L i B), więc w naturze rzadko stykają się bezpośrednio. Typowy ciąg to Bśw → BMśw (Querco roboris-Pinetum, bór mieszany) → LMśw (las mieszany, kwaśna dąbrowa) → Lśw (grąd wysoki, T-C calamagrostietosum) → grąd typowy **[L 06 §2–3]**.
- **Ciąg w glebie:** bielicowa lub rdzawa na piasku → rdzawa brunatna → brunatna kwaśna lub płowa na glinie; próchnica mor → moder → mull **[L 06 §4.2]**.
- **Wskaźniki kolejnych stopni** **[Ź Stefańska; L]**:
  - Bśw: chrobotki, wrzos, rokietnik, bielistka;
  - BMśw: orlica, turzyca pigułkowata, trzcinnik leśny, konwalijka, dąb w podszycie;
  - LMśw: kosmatka owłosiona, konwalia, przytulia, leszczyna;
  - grąd: zawilec, gwiazdnica, przylaszczka, gajowiec, grab w a2.
- **Szerokość [O]:** na granicy sandru i moreny (ostra zmiana podłoża) przejście ma 20–100 m. Na stopniowym gradiencie (piasek gliniasty) do kilkuset metrów. Zasadzie Z10 odpowiada mozaika: w pasie przejścia rosną najpierw dęby i leszczyna pod sosnami, potem sosny pojedynczo w grądzie wysokim.
- **Las gospodarczy:** w Polsce bardzo częsta jest sośnina na siedlisku grądowym (pinetyzacja). Pod sosną 60–80-letnią rosną wtedy leszczyna, grab i lipa w podroście, a runo jest grądowe, ale zubożałe. To naturalny „most” wizualny między dwoma lasami wycinka. GIOŚ ocenia go jako zniekształcenie **[Ź GIOŚ warsztaty]**.

## 9. Liczby do wycinka (propozycje, [O] na podstawie §0–8)

1. **Grąd naturalny:**
   - a1 to 3–5 drzew na chunk (dąb, lipa, klon/jesion, świerk na NE), 28–40 bloków wysokości, pnie 0,5–1,5 bloku (D8);
   - a2 to 6–9 grabów na chunk, 15–22 bloków;
   - podrost i podszyt w kępach, gęstszy w lukach (luka 18–45 bloków średnicy co ok. 1–2 ha);
   - martwe drewno ok. 2 obiekty na chunk.
2. **Grąd gospodarczy (85 %):** jednowiekowy drzewostan dębowo-grabowo-lipowy (zgodnie z ZHL Lśw: Db 50–80 + domieszki), a1 22–30 bloków, ok. 0,25 obiektu martwego drewna na chunk, pniaki.
3. **Bór świeży gospodarczy:** sosna w wieku 60–100 lat, 8–15 drzew na chunk w luźnym rozkładzie, korony tylko w górnych 25–40 % wysokości (`06 §9.2`). Podszyt z jałowca, kruszyny i jarzębu na 5–30 % powierzchni, borówka na 40–70 % kolumn, mchy na 60–90 %. Martwe drewno ok. 0,1 kłody na chunk i 1–2 pniaki.
4. **Bór „naturalny” (15 %):** kępy różnowiekowe, luki z brzozą i młodą sosną, dąb, świerk lub jodła w podroście, ok. 1 obiekt martwego drewna na chunk.
5. **Sezony:** aspekt wiosenny grądu od Early Spring do Late Spring, zanik w Early Summer. Borówka kwitnie w Late Spring, owocuje w Mid i Late Summer, wrzos kwitnie od Late Summer do Early Autumn, grzyby rosną od Late Summer do Mid Autumn.

## 10. Niepewności i korekty względem `docs/research/`

- **Mrowiska:** 0,13 zamieszkanego kopca/ha (Białowieża, 1400 ha) zamiast 0,5–5/ha w `06 §4.5` i `07 §9.1`. Lokalnie (skraje borów) zagęszczenie może być wyższe, ale średnia jest niska.
- **Zasięg Leucobryo-Pinetum:** Wikipedia (Matuszkiewicz 2007) podaje „niemal cała Polska poza NE, pobrzeżem i górami”, a `06 §3.1` „na zachód od Wisły”. Do ujednolicenia mapą zasięgu w M3 (§9 planu M2).
- **Brak liczb:**
  - pełnych tabel BPN dla samego Tilio-Carpinetum (liczba drzew, pole przekroju i zasobność per gatunek; prace Brzezieckiego i Palucha są za paywallem lub zablokowane);
  - gęstości ptaków w grądzie (abstrakt podaje tylko zakres 48–124 pary/10 ha);
  - progu łącznej objętości martwego drewna GIOŚ w m³/ha dla 9170 (jest tylko kalibracja procentowa dla 9190);
  - wysokości sosny z tablic zasobności według bonitacji (tablice Szymkiewicza niedostępne online), dlatego podano tylko przykłady zmierzone.
- **Zaktualizowane dane WISL:** wyniki 2017–2021 to 9,8 m³/ha **(źródło wtórne)**. Pliki BDL WISL 2019–2023 i 2020–2024 zwracały błąd 404. Wynik 23 m³/ha w Drawieńskim PN wobec 4,22 m³/ha w lasach gospodarczych pochodzi tylko ze streszczenia wyszukiwarki, z niepewnym źródłem pierwotnym, i nie wszedł do tabel.
- **Wartości z analogii:** wartość 47 % PAR przed ulistnieniem pochodzi z lasu dębowo-hikorowego (USA) i jest analogią. Udział drewna stojącego w lesie naturalnym (ok. 1/3) wynika z danych dla parków narodowych (12,0 z 35,8 m³/ha).

## 11. Źródła

- GIOŚ, wyniki monitoringu 9170, 2013–2014 (Mróz, Mikita): https://siedliska.gios.gov.pl/images/pliki_pdf/wyniki/2013-2014/dla_siedlisk/Grd-rodkowoeuropejski-i-subkontynentalny-Galio-Carpinet.pdf
- GIOŚ, „Monitoring siedlisk przyrodniczych w Polsce”, sesja terenowa (9170 Miechowice, pierśnice dębu i buka): https://siedliska.gios.gov.pl/images/pliki_pdf/aktualnosci/Warsztaty%20terenowe%20z%20zakresu%20monitoringu%20przyrodniczego.pdf
- GIOŚ, monitoring konarka tajgowego (martwe drewno w grądach BPN ok. 130 m³/ha): https://siedliska.gios.gov.pl/images/pliki_pdf/wyniki/2006-2008/dla_gat_zwierzat/Konarek-tajgowy-Phryganophilus-ruficollis.pdf
- W. Mróz, P. Pawlaczyk, „Martwe drewno w lesie jako element monitoringu…” (WISL 2006–2010, progi FV/U1/U2, Müller i Bütler 2010): https://www.kp.org.pl/pdf/krakow3/mroz_pawlaczyk_martwe_drewno.pdf
- S. Miścicki 2016, „Zmiany drzewostanów BPN w okresie 2000–2015”, Leśne Prace Badawcze 77(4), DOI 10.1515/frp-2016-0038: https://lpb-ibl.sggw.edu.pl/article/view/11667
- Piętka S. i in. 2019, „Coarse woody debris… reserve stand and managed forest in the Taborz region”, J. For. Res. 30: https://journal.hep.com.cn/jfr/EN/10.1007/s11676-018-0612-y
- Wikipedia, „Martwe drewno w lesie”: https://pl.wikipedia.org/wiki/Martwe_drewno_w_lesie
- Wikipedia, „Leucobryo-Pinetum”: https://pl.wikipedia.org/wiki/Leucobryo-Pinetum
- K. Rawlik 2021, „Wiosenna tama – geofity w lasach”, Kórniczanin (ID PAN): https://www.idpan.poznan.pl/images/Rawlik%20K%20Kórniczanin%202021.pdf
- RDOŚ Warszawa, „Wiosenne obserwacje”: https://www.gov.pl/web/rdos-warszawa/wiosenne-obserwacje
- Światło pod okapem (< 2 % PAR): https://www.ncbi.nlm.nih.gov/pmc/articles/PMC11207122/ ; przepuszczalność przed ulistnieniem 47 % (las dębowo-hikorowy): https://pure.ecnu.edu.cn/en/publications/biologically-effective-uv-b-exposures-of-an-oak-hickory-forest-un/
- Runo grądu, zawilec > 1000 pędów/m² (Plant Ecology 2020): https://link.springer.com/article/10.1007/s11258-020-01067-4
- E. Stefańska 2007, „Wskaźniki siedlisk boru świeżego i mieszanego świeżego…”, SiM CEPL 16: https://cepl.sggw.edu.pl/wp-content/uploads/sites/75/2021/08/SIM16A_15_Stefanska_WSKAZNIKI_SIEDLISK_BORU_SWIEZEGO_I_MIESZANEGO_SWIEZEGO.pdf
- Z. Popiołek 1978, „Ocena stanu biomasy runa i mchów… nadl. Janów Lubelski”, Ann. UMCS C 33: https://www.dlibra.umcs.lublin.pl/Content/29795/download
- B. Sałata 1977, „Grzyby wyższe rezerwatu leśnego Zagożdżon w Puszczy Kozienickiej”, Ann. UMCS C 32: https://www.dlibra.umcs.lublin.pl/Content/29760/download
- Sokołowski, Paluch 2004 (Sylwan), odnowienie sosny w NE Polsce: https://agro.icm.edu.pl/agro/element/bwmeta1.element.agro-article-ac754917-ef01-4807-abac-d6690804f172/c/48.pdf ; Paluch (samosiewy sosny w bruzdach): https://agro.icm.edu.pl/agro/element/bwmeta1.element.agro-article-5097fcfc-fc4f-4968-a3db-00dc097083ac/c/9.pdf
- Luki w grądach BPN (250–1610 m², grab i lipa jako wypełniacze luk): https://yadda.icm.edu.pl/baztech/element/bwmeta1.element.baztech-article-BGPK-1801-6813
- Bobiec i in. 2011, odnowienie dębu w BPN (EJFR): https://link.springer.com/article/10.1007/s10342-010-0471-3
- Wesołowski i in. 2002, „The breeding bird community of a primaeval temperate forest (Białowieża)”, Acta Ornithol. 37: https://bioone.org/journals/acta-ornithologica/volume-37/issue-1/068.037.0105/The-Breeding-Bird-Community-of-a-Primaeval-Temperate-Forest-Bia%c5%82owie%c5%bca/10.3161/068.037.0105.full
- Tomiałojć 2005, grubodziób w BPN, Acta Ornithol. 40: https://complete.bioone.org/journals/acta-ornithologica/volume-40/issue-2/068.040.0208/Distribution-Breeding-Density-and-Nest-Sites-of-Hawfinches-Coccothraustes-coccothraustes/10.3161/068.040.0208.full
- Sondej i in. 2018, „Wood ants in the Białowieża Forest…”, Ann. Zool. Fennici 55: https://complete.bioone.org/journals/annales-zoologici-fennici/volume-55/issue-1%e2%80%933/086.055.0110/Wood-Ants-in-the-Bia%c5%82owie%c5%bca-Forest-and-Factors-Affecting-their/10.5735/086.055.0110.full
- Woziwoda, „Identyfikacja i ocena stanu zachowania siedlisk grądowych”, SiM CEPL: https://cepl.sggw.edu.pl/wp-content/uploads/sites/75/2021/08/SIM16A_06_Woziwoda_IDENTYFIKACJA_I_OCENA_STANU_ZACHOWANIA_SIEDLISK_GRADOWYCH.pdf
- Lokalne raporty: `docs/research/06-lasy-polski-fitosocjologia-stl.md` (§3.1, §3.5, §4.1–4.5, §6, §9), `07-fauna-polski-siedliska-zachowania.md` (§2, §7, §9), `08-klimat-pory-roku-fenologia.md` (§7.3); obecne składy biomów: `docs/03-m2-biomy.md` §8.5–8.6.
