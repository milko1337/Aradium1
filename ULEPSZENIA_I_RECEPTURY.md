# Katalog 100 Nietypowych Ulepszeń Ekwipunku oraz System Custom Recipes (Aradium)

> Ten dokument zawiera **100 propozycji nietypowych, mechanicznych ulepszeń** (`po 10` na każdy element zbroi i narzędzi — zamiast standardowych zaklęć typu *Ostrość VI*), opis architektury **Custom Recipes** (`recipes.yml` + GUI Admina + GUI Gracza) oraz status pierwszego pakietu ulepszeń wdrożonych już w pluginie.

---

## Spis wdrożonych natychmiast mechanik (`✅ Wdrożono w pluginie`)

Poniższe **9 zwojów/run mechanicznych** zastąpiło wcześniejsze zwoje zwykłych zaklęć i jest już dostępne w `custom-items.yml`, `recipes.yml`, `/customitem` oraz `/shop` (`magic`):

1. `zwoj_kopanie_2x2` (**Kilof / Łopata**) — Kopanie obszaru `2x2` w płaszczyźnie ściany/podłoża.
2. `zwoj_kopanie_3x3` (**Kilof / Łopata**) — Kopanie obszaru `3x3` wokół wykopanego bloku.
3. `zwoj_hutnik` (**Kilof / Łopata**) — *Hutnik (Auto-Smelt)*: automatyczne przetapianie rud (`IRON_ORE`, `GOLD_ORE`, `COPPER_ORE`, `ANCIENT_DEBRIS`, `COBBLESTONE`, `SAND`).
4. `zwoj_drwal` (**Siekiera**) — *Drwal (TreeCapitator)*: ścinanie połączonego pnia drzewa do `24` bloków.
5. `zwoj_zniwiarz_3x3` (**Motyka**) — *Żniwiarz `3x3`*: masowy zbiór i automatyczne przesadzanie dojrzałych upraw w obszarze `3x3`.
6. `zwoj_wampiryzm` (**Miecz / Siekiera / Trójząb / Buzdygan**) — *Wampiryzm*: leczenie o `1/3` (`33.3%`) zadanych obrażeń.
7. `zwoj_piorun` (**Miecz / Siekiera / Trójząb / Buzdygan**) — *Uderzenie Pioruna*: `25%` szansy na piorun i `+4.0` dodatkowych obrażeń.
8. `zwoj_double_jump` (**Buty**) — *Podwójny Skok (Double Jump)*: dodatkowy podskok w powietrzu bez dotykania ziemi + ochrona przed upadkiem.
9. `runa_tieru_zbroi` (**Magiczna Zbroja**) — *Runa Awansu Zbroi*: podnosi tier części zestawu `ArmorModule` o `+1 Tier` z zachowaniem nałożonych ulepszeń.

---

## 1. Katalog 100 nietypowych ulepszeń (10 x 10 propozycji)

### 1.1. Hełm (10 propozycji)

| # | Nazwa | Mechanika i efekt w grze |
| :-: | :--- | :--- |
| 1 | **Termowizja** | Nadaje pasywne widzenie w ciemności (`NIGHT_VISION`) oraz podświetla graczy/moby w zasięgu `20` kratek. |
| 2 | **Skrzela Otchłani** | Nieskończony tlen pod wodą i brak kary szybkości kopania pod powierzchnią wody. |
| 3 | **Tarcza Umysłu** | `50%` odporności na negatywne efekty mikstur (Ślepota, Trucizna, Spowolnienie, Wither). |
| 4 | **Laserowe Spojrzenie** | `Shift + LPM` wystrzeliwuje wiązkę zadającą `4 ❤` obrażeń w linii wzroku (cooldown `15s`). |
| 5 | **Magnes Doświadczenia** | Automatycznie przyciąga kule EXP z promienia `16` kratek bezpośrednio do paska gracza. |
| 6 | **Szósty Zmysł** | Jeśli gracz ma poniżej `25%` HP, wykrywa najbliższe zagrożenie i nadaje `SPEED II` na `3s`. |
| 7 | **Fotosynteza** | W dzień pod gołym niebem powoli regeneruje głód (`1` udko co `10s`) i wytrzymałość hełmu. |
| 8 | **Korona Handlarza** | Zwiększa wypłaty ze sprzedaży itemów i zabijania mobów o `+10%`. |
| 9 | **Echo Sonaru** | Co `5s` pingiem dźwiękowo-cząsteczkowym sygnalizuje ukryte rudy diamentu/starożytnych zgliszczy w `8` kratkach. |
| 10 | **Ostatnie Tchnienie** | Raz na `10 min` ratuje przed śmiertelnym ciosem, zostawiając `2 ❤` HP i dając `RESISTANCE III` na `3s`. |

### 1.2. Napierśnik (10 propozycji)

| # | Nazwa | Mechanika i efekt w grze |
| :-: | :--- | :--- |
| 1 | **Pancerz Cierniowy** | Odbija `25%` zadanych obrażeń wręcz z powrotem w atakującego (także bez efektu zwykłych Cierni). |
| 2 | **Serce Tytana** | Dodaje na stałe `+4 ❤` (`+8.0 HP`) maksymalnego zdrowia podczas noszenia. |
| 3 | **Płaszcz Magmy** | Zapala każdego przeciwnika atakującego wręcz na `3s` oraz nadaje odporność na lawę/ogień. |
| 4 | **Impuls Grawitacyjny** | Przy spadku poniżej `30%` HP odrzuca wszystkie potwory w promieniu `6` kratek falą uderzeniową (CD `30s`). |
| 5 | **Pochłaniacz Kinetyczny** | `15%` szansy na całkowite zablokowanie trafienia z łuku lub kuszy i zamianę na `1 ❤` tarczy absorpcji. |
| 6 | **Tarcza Próżni** | Generuje odnawialną tarczę pochłaniającą pierwszy cios co `20s`. |
| 7 | **Aura Sytości** | Zmniejsza tempo zużywania głodu o `60%` w trakcie biegu i walki. |
| 8 | **Berserker** | Im mniej HP ma właściciel (poniżej `50%`), tym silniejsze ciosy zadaje (do `+25%` obrażeń). |
| 9 | **Magnetyczny Rdzeń** | Przedmioty wypadające z zabitych mobów w promieniu `6` kratek wpadają bezpośrednio do ekwipunku. |
| 10 | **Zbroja Golema** | Daje `100%` odporności na odrzut (`KNOCKBACK_RESISTANCE`) od ciosów mobów i wybuchów. |

### 1.3. Nogawice (10 propozycji)

| # | Nazwa | Mechanika i efekt w grze |
| :-: | :--- | :--- |
| 1 | **Zryw Cienia (Dash)** | Podwójny sprint lub `Shift` w biegu wykonuje gwałtowny skok naprzód o `6` kratek (CD `4s`). |
| 2 | **Tłoki Hydrauliczne** | Pozwalają na bezskokowe wchodzenie na bloki wysokości `1` kratki (`STEP_HEIGHT +0.5`). |
| 3 | **Pancerz Szybkości** | Pasywnie zwiększa bazową prędkość poruszania o `+20%` bez efektu mikstury na ekranie. |
| 4 | **Ucieczka w Dymie** | Po otrzymaniu obrażeń poniżej `4 ❤` daje niewidzialność i chmurę dymu na `3s` (CD `45s`). |
| 5 | **Miażdżące Lądowanie** | Upadek z wysokości powyżej `5` kratek zadaje obszarowe obrażenia mobom w promieniu `4` kratek zamiast właścicielowi. |
| 6 | **Kieszenie Wymiarowe** | Automatycznie pakuje wykopany bruk/węgiel/żelazo po zebraniu pełnych `9` sztuk w bloki (Block Compressor). |
| 7 | **Kolczasta Gardziel** | Zwiększa szybkość odzyskiwania kondycji sprintu i skraca czas odnowienia zdolności innych części o `15%`. |
| 8 | **Krok po Wodzie** | Umożliwia sprint po powierzchni wody bez zapadania się (kucnięcie pozwala zanurkować). |
| 9 | **Adrenalina Łowcy** | Każde zabójstwo moba nadaje `+25%` szybkości biegu na `4s`. |
| 10 | **Wzmocnione Stawy** | Redukuje otrzymywane obrażenia od upadku i kinetyczne zderzenia Elytrą o `50%`. |

### 1.4. Buty (10 propozycji)

| # | Nazwa | Mechanika i efekt w grze |
| :-: | :--- | :--- |
| 1 | **Podwójny Skok (Double Jump)** ✅ | Pozwala wykonać dodatkowy wysoki podskok w powietrzu bez dotykania ziemi (z ochroną przed upadkiem). |
| 2 | **Piórkowy Upadek** | Całkowita nietykalność na obrażenia od upadku niezależnie od wysokości. |
| 3 | **Ogniste Ślady** | Tymczasowo zamienia lawę pod stopami w obsydian/magmę na `4s` i daje odporność na podpalenie od podłoża. |
| 4 | **Zielony Krok** | Przyspiesza wzrost roślin i drzew wokół spacerującego gracza (efekt delikatnej mączki kostnej). |
| 5 | **Buty Hermesa** | W trakcie sprintu generuje smugę cząsteczek i zwiększa zasięg skoku w dal o `40%`. |
| 6 | **Sprężynowe Podeszwy** | Stałe wybicie skoku o `+50%` wyżej (`JUMP_BOOST II`) z miękkim lądowaniem. |
| 7 | **Cichy Zabójca** | Tłumi dźwięki kroków i nie wyzwala czujników Sculka oraz pułapek naciskowych. |
| 8 | **Ślizg Lodowy** | Poruszanie po lodzie i zbitym lodzie daje natychmiastowe przyspieszenie z pełną kontrolą skrętu. |
| 9 | **Kotwica Próżni** | Przy spadnięciu poniżej `Y = -50` na SkyBlocku jednorazowo teleportuje gracza z powrotem na ostatni stały blok (CD `60s`). |
| 10 | **Elektrowstrząs** | Po lądowaniu ze skoku razi najbliższego potwora wyładowaniem elektrycznym. |

### 1.5. Miecz (10 propozycji)

| # | Nazwa | Mechanika i efekt w grze |
| :-: | :--- | :--- |
| 1 | **Wampiryzm** ✅ | Leczy właściciela o `1/3` (`33.3%`) zadanych obrażeń przy każdym celnym trafieniu. |
| 2 | **Uderzenie Pioruna** ✅ | `25%` szansy przy ataku na przywołanie efektu pioruna i zadanie `+4.0` dodatkowych obrażeń. |
| 3 | **Krwawienie** | Nakłada efekt krwotoku zadający `1 ❤` obrażeń co `1s` przez `4s` (z cząsteczkami czerwonego pyłu). |
| 4 | **Egzekutor** | Jeśli cel ma poniżej `25%` HP, zadaje krytyczne `+50%` obrażeń kończących walkę. |
| 5 | **Fala Tnąca (Cleave)** | Atak podstawowy rani dodatkowo wszystkie wrogie moby w promieniu `2.5` kratki przed ostrzem. |
| 6 | **Łowca Głów** | Daje `5%` szansy na upuszczenie głowy moba/gracza oraz zwiększa drop o równowartość Grabieży. |
| 7 | **Trujące Ostrze** | Zakaża trafionego wroga trucizną (`POISON II`) i spowalnia jego ruch na `3s`. |
| 8 | **Lodowe Ostrze** | Zamraża trafionego moba na `2s` (efekt śniegu sypkiego + spowolnienie III). |
| 9 | **Złodziej Dusz (Midas)** | Każde zabicie wrogiego moba nagradza dodatkowymi `$` prosto na konto ekonomii. |
| 10 | **Przebicie Pancerza** | `30%` zadawanych obrażeń ignoruje zbroję oraz tarczę przeciwnika (`Bypass Armor`). |

### 1.6. Kilof (10 propozycji)

| # | Nazwa | Mechanika i efekt w grze |
| :-: | :--- | :--- |
| 1 | **Kopanie 2x2** ✅ | Wycina obszar `2x2` bloki zgodnie z płaszczyzną ściany lub podłogi (z poszanowaniem wyspy). |
| 2 | **Kopanie 3x3** ✅ | Wycina pełne okno `3x3` (`9` bloków naraz) na podstawie kierunku patrzenia. |
| 3 | **Hutnik (Auto-Smelt)** ✅ | Automatycznie przetapia rudy żelaza, złota, miedzi, netherytu, bruku i piasku na gotowe sztabki/szkło. |
| 4 | **Żyłowanie (VeinMiner)** | Wykopuje całą połączoną żyłę tej samej rudy (do `16` bloków) jednym ruchem kilofa. |
| 5 | **Telekineza** | Wszystkie wykopane bloki i rudy trafiają od razu do ekwipunku zamiast wypadać na ziemię. |
| 6 | **Geolog** | `10%` szansy przy kopaniu kamienia lub bruku na wykopanie bonusowej bryłki węgla/miedzi/żelaza lub `$`. |
| 7 | **Precyzyjny Odwiert** | Pozwala bezpiecznie wydobywać spawnery i szkło bez niszczenia ich zwykłym dropem (jak *Silk Touch*). |
| 8 | **Wiertło Prędkości** | Po wykopaniu `3` bloków z rzędu nadaje `HASTE II` na `4s` bez zużywania mikstur. |
| 9 | **Przekuwacz** | Gdy kilof ma poniżej `10%` wytrzymałości, pobiera XP gracza, aby naprawić po `10` pkt za `1` orb. |
| 10 | **Ekstraktor EXP** | Zwiększa o `100%` doświadczenie wypadające z rud oraz bloków węgla/kwarcu. |

### 1.7. Siekiera (10 propozycji)

| # | Nazwa | Mechanika i efekt w grze |
| :-: | :--- | :--- |
| 1 | **Drwal (TreeCapitator)** ✅ | Ścina od razu cały pień drzewa połączony pionowo/ukośnie (do `24` bloków). |
| 2 | **Korowacz Midasowy** | Korowanie pnia PPM daje szansę na drop złotej bryłki lub żywicy (`GOLD_NUGGET`). |
| 3 | **Rozłupywacz Tarcz** | Trafienie gracza z tarczą całkowicie rozbraja jej blokadę na `8s` zamiast standardowych `5s`. |
| 4 | **Siekiera Berserkera** | Zadaje `+40%` obrażeń w walce wręcz mobom o wysokim zdrowiu (`Iron Golem`, `Ravager`). |
| 5 | **Tartak** | Zamienia wykopane pnie drewna od razu w `6` desek zamiast standardowych `4`. |
| 6 | **Rzut Toporem** | `PPM` w powietrzu miota obracającą się siekierą, która zadaje obrażenia i wraca jak trójząb (CD `6s`). |
| 7 | **Sadownik** | Ścięcie drzewa automatycznie sadzi z powrotem odpowiednią sadzonkę w miejscu pnia. |
| 8 | **Opad Liści** | Przyspiesza zrywanie liści z potrójną szansą na jabłka, patyki i sadzonki. |
| 9 | **Wstrząs Ziemi** | Uderzenie krytyczne ze skoku ogłusza moby w promieniu `3` kratek na `1.5s`. |
| 10 | **Wampirzy Topór** | Kradnie `20%` pancerza lub regeneruje `2 ❤` HP po powaleniu przeciwnika. |

### 1.8. Łopata (10 propozycji)

| # | Nazwa | Mechanika i efekt w grze |
| :-: | :--- | :--- |
| 1 | **Koparka 3x3** ✅ | Działa z tym samym zwojem `2x2` / `3x3`, błyskawicznie usuwając ziemię, piasek i żwir na obszarze. |
| 2 | **Przesiewacz Złota** | Kopanie żwiru i błota daje `15%` szansy na samorodki złota lub krzemień zamiast zwykłego żwiru. |
| 3 | **Szklarz** ✅ | Z ulepszeniem *Hutnik* automatycznie wypala wykopany piasek na szkło i glinę w cegły. |
| 4 | **Archeolog** | `5%` szansy podczas kopania piasku dusz lub ziemi na znalezisko monet `$` / kości / odłamka ametystu. |
| 5 | **Niwelator Terenu** | Ignoruje grawitację spadającego piasku/żwiru nad wykopanym blokiem, zbierając cały słup w dół. |
| 6 | **Krzemieniarz** | Z żwiru zawsze wypada `100%` krzemienia (albo podwójny żwir w zależności od trybu). |
| 7 | **Błotny Pocisk** | `PPM` rzuca kulę błota oślepiającą i spowalniającą potwory (CD `8s`). |
| 8 | **Darniowiec** | Wykopanie bloku trawy z tym ulepszeniem upuszcza gotowy `GRASS_BLOCK` zamiast ziemi. |
| 9 | **Telekineza Ziemna** | Zbiera sypkie bloki bezpośrednio do ekwipunku, nie pozwalając im wpaść do lawy lub próżni wyspy. |
| 10 | **Okop** | Szybkie kopanie śniegu, gliny i piasku regeneruje wytrzymałość łopaty kosztem `1` głodu. |

### 1.9. Motyka (10 propozycji)

| # | Nazwa | Mechanika i efekt w grze |
| :-: | :--- | :--- |
| 1 | **Żniwiarz 3x3** ✅ | Zbiera dojrzałe uprawy w promieniu `3x3` i natychmiast przesadza je na nowo (`Ageable`). |
| 2 | **Automatyczny Siew** | Kliknięcie PPM na spulchnioną ziemię obsiewa nasionami z ekwipunku cały pas `3x3`. |
| 3 | **Błogosławieństwo Demeter** | +`35%` szansy na dodatkowy plon ponad standardowe ulepszenie wyspy `DOUBLE_CROP_DROP`. |
| 4 | **Pług 3x3** | `PPM` spulchnia od razu `9` bloków ziemi pod uprawy jednocześnie. |
| 5 | **Deszcz Mączki Kostnej** | `Shift + PPM` zużywa `1` mączkę kostną, nawożąc naraz obszar `5x5` upraw (CD `5s`). |
| 6 | **Złote Żniwa** | `1%` szansy przy zbiorze marchewek/jabłek/pszenicy na drop złotej marchewki lub chleba. |
| 7 | **Kosa Śmierci** | Działa jako broń zadająca podwójne obrażenia szkieletom i zombie na farmie. |
| 8 | **Kolekcjoner Nasion** | Zbiera liście, trzcinę i bambus bez uszkadzania samego korzenia rośliny (`y = 0`). |
| 9 | **Magnes Plonów** | Przenosi zebrane plony i nasiona natychmiast do pierwszego wolnego slotu gracza. |
| 10 | **Rozdrabniacz** | Nadmiarowe nasiona zebrane z farmy mogą zostać zamienione bezpośrednio w mączkę kostną. |

### 1.10. Łuk / Kusza i Wędka (10 propozycji)

| # | Nazwa | Mechanika i efekt w grze |
| :-: | :--- | :--- |
| 1 | **Wybuchowa Strzała** | Strzała eksploduje przy uderzeniu zadając obrażenia obszarowe bez niszczenia bloków wyspy. |
| 2 | **Trójszał (Salwa)** | Wystrzeliwuje `3` strzały wachlarzowo kosztem tylko `1` strzały z kołczanu. |
| 3 | **Piorunujący Grot** | Trafienie w pełni naciągniętym łukiem przywołuje piorun w miejscu lądowania celu. |
| 4 | **Strzała Wampira** | Dystansowy drain życia leczący strzelca o `25%` zadanych obrażeń strzałą. |
| 5 | **Kotwiczka (Grappling)** | Rzut wędką i przyciągnięcie katapultuje gracza w stronę wbicia haczyka (CD `6s`). |
| 6 | **Sieć Rybacka** | Każde celne zarzucenie wędki ma szansę wyłowić `2` przedmioty jednocześnie. |
| 7 | **Łowca Skarbów** | Zwiększa o `2x` szansę na skrzynki skarbów, siodła, znaczniki i zaklęte książki przy łowieniu. |
| 8 | **Gorący Haczyk** | Wyłowione ryby wpadają do ekwipunku od razu upieczone (`COOKED_COD`, `COOKED_SALMON`). |
| 9 | **Lodowa Strzała** | Zatrzymuje trafionego moba w lodowej klatce spowolnienia na `2.5s`. |
| 10 | **Snajper** | Zadaje do `+60%` dodatkowych obrażeń proporcjonalnie do dystansu lotu strzały. |

---

## 2. Architektura systemu Custom Recipes (`recipes.yml` + GUI Admina i Gracza)

System własnych receptur został wydzielony z `custom-items.yml` do dedykowanego pliku `recipes.yml` oraz modułu GUI:

1. **Plik `src/main/resources/recipes.yml` (`CustomRecipeManager` + `CustomRecipeDefinition`):**
   - Przechowuje kategorie: `GENERATORS`, `SCROLLS`, `ARMOR`, `MAGIC`, `MISC`.
   - Obsługuje zarówno format skrócony (`shape` + `ingredients` + `custom-item` / `special-armor` / `magic-item` / `material`), jak i format graficzny zapisywany prosto z GUI Administratora (`slots.0..8` i pełny `ItemStack` ze wszystkimi tagami PDC, nazwą i lore!).
   - W `PrepareItemCraftEvent` dodatkowo waliduje, czy przedmioty w stole rzemieślniczym pasują dokładnie do niestandardowych składników zapisanych w GUI (np. z PDC/CustomModelData).
2. **GUI Administratora (`/receptury admin` — uprawnienie `aradium.admin` / OP):**
   - **Lista receptur (`ADMIN_LIST`, `54` sloty):** przeglądanie wszystkich receptur ze stanem (`✔ Aktywna` / `✖ Wyłączona`), tworzenie nowej receptury (`+ Nowa receptura`), szybkie włączanie/wyłączanie (PPM), usuwanie (`Shift+PPM`) oraz przeładowanie (`recipes.yml`).
   - **Kreator / Edytor (`ADMIN_EDITOR`, `54` sloty):** odblokowane 9 slotów siatki craftingu `3x3` (`10–12`, `19–21`, `28–30`) + odblokowany slot wyniku (`25`). Admin wkłada dowolne przedmioty (waniliowe, zwoje, generatory, magiczne zbroje) prosto ze swojego ekwipunku, wybiera kategorię, klika **Zapisz**, a plugin zapisuje recepturę do `recipes.yml` i od razu rejestruje ją w serwerze!
3. **GUI Gracza (`/receptury` / `/recipes` / `/kraftingi` — uprawnienie `aradium.player`):**
   - **Ekran 1 (`PLAYER_CATEGORIES`, `45` slotów):** wybór jednej z 5 kategorii (`Generatory`, `Zwoje i Runy`, `Specjalne Zbroje`, `Przedmioty Magiczne`, `Różności`).
   - **Ekran 2 (`PLAYER_CATEGORY_LIST`, `54` sloty):** wielostronicowa lista przedmiotów dostępnych w wybranej kategorii.
   - **Ekran 3 (`PLAYER_RECIPE_VIEW`, `45` slotów):** podgląd ułożenia składników w siatce `3x3` oraz gotowego przedmiotu (w 100% zabezpieczony Anti-Dupe).

---

## 3. Sekcja wyboru użytkownika (do zaznaczenia przed kolejną turą ulepszeń)

Wpisz poniżej ID wybranych kolejnych ulepszeń z sekcji 1 (`np. Kilof #4 Żyłowanie, Buty #9 Kotwica Próżni`), które chcesz dodać w kolejnym pakiecie:

- **Wybrane ulepszenia do dodania w następnej kolejności:** _(do uzupełnienia przez Ciebie)_
