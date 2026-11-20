# Plan dalszego rozwoju Aradium (Backlog)

> **Aktualny status:** Wszystkie 6 faz **Aradium Core** (`DatabaseManager`, Ekonomia,
> Sklepy GUI, Spawnery oraz silnik SkyBlock z poziomami, ulepszeniami i 5 etapami misji)
> zostały **zakończone i wdrożone**.
>
> Dokumentacja techniczna gotowych modułów znajduje się w [`AUDIT.md`](AUDIT.md).

---

## Zadania pozostałe do wykonania

### 1. Rozbudowa kategorii Sklepu GUI (`shops/`) — ✅ UKOŃCZONO (Etap 2)

- [x] Utworzyć rozbudowaną hierarchię 17 plików `shops/*.yml` (`main`, `building`,
  `blocks`, `wood`, `natural`, `decorative_blocks`, `ores`, `farming`, `food`,
  `combat`, `redstone`, `magic`, `mobdrops`, `decoration`, `music`, `storage`, `misc`).

- [x] Podpiąć kategorie w `shops/main.yml`, dodać wielostronicowość, okna wyboru
  ilości (`-32/-16/-1/+1/+16/+32`), `/sellall`, `/sellgui` i zrównoważyć ceny
  z ekonomią SkyBlocka oraz nagrodami z misji.

---

### 2. Poprawa gui związanych z wyspą (`/is`)

- [ ] Poprawić formatowanie oraz wygląd poszczególnych gui.

---

### 3. Integracja PlaceholderAPI / Tablista i Scoreboard

- [ ] Dodać opcjonalną ekspansję `PlaceholderAPI`: `%aradium_money%`,
  `%aradium_island_level%`, `%aradium_island_rank%`, `%aradium_island_stage%`.

- [ ] Ustawić miękką zależność `softdepend: [PlaceholderAPI]` w `plugin.yml`,
  aby plugin uruchamiał się poprawnie także bez `PlaceholderAPI`.

---

### 4. Testy integracyjne Paper/Bukkit (MockBukkit)

- [ ] Dodać testy zdarzeń `InventoryClickEvent` (Anti-Dupe GUI), `BlockBreakEvent`,
  `PlayerArmorChangeEvent`, `BrewEvent` oraz `PlayerFishEvent`.

- [ ] Przetestować cykl życia `ModuleManager` (kolejność uruchamiania,
  `reload` oraz zamykanie GUI przy wyłączaniu pluginu).

---

### 5. Wersjonowanie i publikacja na GitHub

- [x] Wykonać `git commit` oraz `git push` aktualnego stanu projektu na GitHub
  po zakończeniu prac nad Fazami 1–6 Core.

- [ ] Stosować regułę z `AUDIT.md`: po każdej większej zmianie testy,
  aktualizacja dokumentacji, commit oraz push na GitHub.

---

### 6. Rozbudowa misji oraz ulepszeń wysp

- [ ] Dodać nowe misje oraz ulepszenia wysp (np. szansa na podwójny drop z roślin i inne).

- [ ] Zrównoważyć nagrody z misji oraz ulepszeń wysp z ekonomią SkyBlocka.

---

### 7. Optymalizacja pluginu

- [ ] Optymalizacja wydajności pluginu.

- [ ] Optymalizacja kodu pluginu.

- [ ] Optymalizacja zasobów pluginu.

---

### 8. Dodatkowe funkcje

- [ ] Dodać nowe itemy do pluginu. - specjalne itemy jak generatory cobla, zróżnicowane generatory cobla itp, pochodnia wyłączająca resp mobów obszarowo itp.
- [ ] Zrównoważyć funkcje z ekonomią SkyBlocka. - po rozbudowie sklepu zrównoważyć ceny itemów z misjami i ulepszeniami wysp.
- [ ] Dodać nowe mechaniki do pluginu. - dodatkowe mechaniki jak craftingi przedmiotów , specjalne ulepszenia do broni,narzędzi, zbroi
- [ ] Możliwość wyłączenia poszczególnych modułów pluginu z pliku konfigracyjnego

### 9. Moderacja czatu 

- [ ] Dodać moderację czatu do pluginu - komendy mute/ban/kick
- [ ] Dodać system rang i uprawnień do pluginu - uprawnienia dla rang, prefixy, suffixy itp.
- [ ] Dodać czyszczenie czatu

### 10. rozbudowa upgrade spawnerów — ✅ UKOŃCZONO (Etap 3)
- [x] Ulepszenie na czas respu
- [x] Ulepszenie na ilość mobów respu 
- [x] Ulepszenie na zasięg gracza w pobliżu 
- [x] Ulepszenie na nie reagownaie na światło resp mobów
- [x] Ulepszenie na ignorowanie podłoża i spawn mobów w powietrzu (`GROUND_IMMUNITY`)
- [x] Moduł do zakupu moba jaki ma się respić (bazowo zawsze świnia, wszystkie 85 mobów poza bossami: Wither, Ender Dragon, Warden)

### 11. rozbudowa upgrade wysp — ✅ UKOŃCZONO (Etap 4)
- [x] Ulepszenie na ilość itemów respu na generatorze cobla - nie jako generator specjalny z plan wcześniej lecz podstawowy generator z lavy i wody na początek rozgrywki. Gracz będzie mógł kupić ulepszenia do generatora cobla podnoszące ilość produkowanego surowca (`COBBLE_GENERATOR_YIELD`, poziomy `1x–5x`).

---

## Plan wdrożenia (Etapy 1–11)

> Weryfikacja techniczna względem Paper `26.3.build.251-stable` i Javy `25`:
> wszystkie **11 punktów (`1–11`)** z listy zadań pozostałych do wykonania jest spójnych
> z architekturą `AbstractModule` i `DatabaseManager`. Do testów zdarzeń (Etap 11)
> rekomendowane jest `mockito-junit-jupiter` zamiast `MockBukkit` (pełna zgodność
> z najnowszym API Paper `26.3` i Javą `25`).

### Mapa powiązań (Punkty `1–11` -> Etapy wdrożenia)

| Etap | Punkt w `PLAN.md` | Zakres główny |
| :--- | :--- | :--- |
| **Etap 1** | **Punkt 5** | Wersjonowanie i publikacja na GitHub (`GitHub Baseline`) |
| **Etap 2** | **Punkt 1** | Rozbudowa kategorii Sklepu GUI (`shops/`) |
| **Etap 3** | **Punkt 10** | Rozbudowa ulepszeń spawnerów (5 ścieżek + spawn w powietrzu + 85 mobów) |
| **Etap 4** | **Punkt 11** | Rozbudowa ulepszeń wysp (podstawowy generator cobla z lawy i wody) |
| **Etap 5** | **Punkt 6** | Rozbudowa misji oraz ulepszeń wysp (podwójny drop z roślin i balans) |
| **Etap 6** | **Punkt 8** | Dodatkowe funkcje (nowe itemy, mechaniki, balans sklepu, wyłączanie modułów z `config.yml`) |
| **Etap 7** | **Punkt 2** | Poprawa GUI związanych z wyspą (`/is`) |
| **Etap 8** | **Punkt 7** | Optymalizacja pluginu (wydajność, kod, zasoby) |
| **Etap 9** | **Punkt 9** | Moderacja czatu oraz System Rang i Uprawnień |
| **Etap 10** | **Punkt 3** | Integracja `PlaceholderAPI` (Tablista / Scoreboard) |
| **Etap 11** | **Punkt 4** | Testy integracyjne Paper/Bukkit (`AradiumAuditTest`) |

---

### Etap 1 (Punkt 5): Wersjonowanie i publikacja na GitHub (`GitHub Baseline`) — ✅ UKOŃCZONY

- **Cel:** Zabezpieczenie ukończonych Faz 1–6 Core w repozytorium GitHub przed kolejnymi zmianami.

- **Zadania:**
  - [x] Zweryfikować `.gitignore` (`target/`, `.idea/`, `*.iml`, `*.db`, `*.db-wal`, `*.db-shm`, `*.sqlite`).
  - [x] Utworzyć lub zweryfikować repozytorium Git i zdalny origin na GitHub.
  - [x] Wykonać bazowy commit (`Fazy 1–6 Core: 73 klasy, 15/15 testów`) oraz `git push`.
  - Powtarzać commit i `git push` po zamknięciu każdego kolejnego etapu zgodnie z `AUDIT.md`.

---

### Etap 2 (Punkt 1): Rozbudowa kategorii Sklepu GUI (`shops/`) — ✅ UKOŃCZONY

- **Cel:** Rozszerzenie sklepu `/shop` o pełny asortyment SkyBlocka wzorowany na `EconomyShopGUI`.

- **Zadania:**
  - [x] Utworzyć 17 polskich plików `shops/*.yml` (`main`, `building`, `blocks`, `wood`,
    `natural`, `decorative_blocks`, `ores`, `farming`, `food`, `combat`, `redstone`,
    `magic`, `mobdrops`, `decoration`, `music`, `storage`, `misc`).
  - [x] Wdrożyć wielostronicowość kategorii (`pages:`, siatka `7x4`, automatyczny podział
    > 28 pozycji), dolny pasek nawigacji (głowa gracza z saldem, strony, powrót/zamknięcie),
    ekrany wyboru ilości (`BUY_SCREEN`, `SELL_SCREEN`, `BUY_STACKS_SCREEN`: `-32/-16/-1/+1/+16/+32`)
    oraz komendy `/sellall` i `/sellgui` (`SELL_GUI` z Anti-Dupe i bezpiecznym zwrotem).
  - [x] Podpiąć kategorie w `shops/main.yml`, zaktualizować kopiowanie zasobów w `ShopModule`
    oraz zrównoważyć ceny kupna/sprzedaży z ekonomią SkyBlocka i nagrodami z misji (`16/16` testów).

---

### Etap 3 (Punkt 10): Rozbudowa ulepszeń spawnerów (`modules/spawners/`) — ✅ UKOŃCZONY

- **Cel:** Kompleksowy system rozwoju spawnerów z bazową świnią, 5 ścieżkami ulepszeń (w tym spawnem w powietrzu) oraz wyborem moba.

- **Zadania:**
  - [x] **Domyślny mob spawnera:** każdy nowo postawiony lub zakupiony spawner startuje bazowo
    ze Świnią (`EntityType.PIG`).
  - [x] **5 niezależnych ścieżek ulepszeń** zapisywane w `PersistentDataContainer` bloku i przedmiotu
    (z pełnym przenoszeniem przy wykopaniu kilofem z `Silk Touch`):
    - **Ulepszenie na czas respu** (`DELAY`: poziomy `1–5`, `200–600t` -> `50–140t`).
    - **Ulepszenie na ilość mobów respu** (`AMOUNT`: poziomy `1–5`, `2–12` mobów / limit `6–24`).
    - **Ulepszenie na zasięg gracza w pobliżu** (`RANGE`: poziomy `1–4`, `16–48` kratek).
    - **Ulepszenie na niereagowanie na światło podczas respu mobów** (`LIGHT_IMMUNITY`: poziom `0–1`,
      `SpawnRule(0, 15, 0, 15)` w `SpawnerEntry` API Paper `26.3`).
    - **Ulepszenie na ignorowanie podłoża i spawn w powietrzu** (`GROUND_IMMUNITY`: poziom `0–1`,
      `SpawnerAirSpawnTask` omijający wymóg trawy/wody/lawy/podłoża dla wszystkich `85` mobów).
  - [x] **Moduł zakupu moba w `SpawnerGUI`:** wielostronicowe podmenu (`54` sloty, 4 strony)
    umożliwiające wykupienie i przełączanie wszystkich `85` mobów z Minecrafta (poza bossami:
    `WITHER`, `ENDER_DRAGON`, `WARDEN`) za walutę z `EconomyManager` (`17/17` testów).

---

### Etap 4 (Punkt 11): Rozbudowa ulepszeń wysp — podstawowy generator cobla z lawy i wody — ✅ UKOŃCZONY

- **Cel:** Wzmocnienie wczesnej rozgrywki SkyBlocka poprzez ulepszanie klasycznego generatora z lawy i wody (niezależnie od specjalnych bloków generatorów z Punktu 8).

- **Zadania:**
  - [x] Dodać w `IslandUpgradeType` oraz `/is upgrades` ulepszenie `COBBLE_GENERATOR_YIELD`
    dla **podstawowego generatora z lawy i wody na początek rozgrywki** (`sloty 11–15`).
  - [x] Śledzić w `BlockFormEvent` bloki bruku i kamienia powstające z kontaktu lawy i wody
    na terenie wyspy (`GeneratorBlockKey`).
  - [x] Zwiększać w `BlockBreakEvent` ilość wypadającego surowca z wykopanych bloków generatora
    zgodnie z poziomem ulepszenia wyspy (`Tier 1: 1x` -> `Tier 2: 2x` -> `Tier 3: 3x`
    -> `Tier 4: 4x` -> `Tier 5: 5x`).
  - [x] Zabezpieczyć przed duplikacją dropu przy ręcznym stawianiu bloków, przesunięciach tłokami,
    eksplozjach, odładowaniu chunków oraz wykopywaniu `Silk Touch` (`18/18` testów).

---

### Etap 5 (Punkt 6): Rozbudowa misji oraz ulepszeń wysp + balans z ekonomią

- **Cel:** Rozwój rolnictwa, walk z mobami i dalszych etapów postępu wyspy w równowadze z ekonomią SkyBlocka.

- **Zadania:**
  - **Nowe ulepszenia wysp (`IslandUpgradeType` i `/is upgrades`):**
    - **Szansa na podwójny drop z roślin (`DOUBLE_CROP_DROP`):** procentowa szansa
      na podwojenie plonów przy zbiorze w pełni dojrzałych upraw (`Ageable` z maksymalnym `age`).
    - **Dodatkowe ulepszenia wyspy:** m.in. bonus dropu/XP z mobów na wyspie (`MOB_DROP_BONUS`)
      oraz ulepszenia wspierające rozwój wyspy.
  - **Nowe misje wysp (`quests.yml` i `QuestGUI`):** dodanie nowych zadań i kolejnych
    etapów (**Etap VI i VII**) uwzględniających nowe mechaniki i ulepszenia.
  - **Balans ekonomii:** zrównoważenie nagród pieniężnych i przedmiotowych z misji
    wobec kosztów wszystkich ulepszeń wysp w `/is upgrades`.

---

### Etap 6 (Punkt 8): Dodatkowe funkcje (Nowe itemy, Mechaniki, Balans sklepu, Moduły z `config.yml`)

- **Cel:** Rozbudowa gry o zaawansowane przedmioty techniczne, własny crafting, rozwój ekwipunku oraz konfigurację aktywnych modułów.

- **Zadania:**
  - **Nowe specjalne itemy:**
    - Stawiane **specjalne generatory cobla** oraz **zróżnicowane generatory cobla/rud (`T1–T3`)**
      losujące surowce wg wag z konfiguracji (z zachowaniem danych PDC po wykopaniu).
    - **Pochodnia Anty-Mob:** blok z PDC wyłączający naturalny spawn potworów
      (`SpawnReason.NATURAL`) obszarowo w zadanym promieniu na wyspie (bez blokowania spawnerów).
  - **Nowe mechaniki:**
    - Własne craftingi przedmiotów (`ShapedRecipe`) dla generatorów, pochodni anty-mob i ulepszaczy.
    - Specjalne ulepszenia (runy/zwoje) do **broni, narzędzi i zbroi** rozwijające ich statystyki lub tier.
  - **Wyłączanie poszczególnych modułów z `config.yml`:**
    - Sekcja `modules:` (`economy`, `skyblock`, `shop`, `spawners`, `armor`, `moderation`, `ranks`)
      w `config.yml` obsługiwana przez `ModuleManager` przy starcie oraz `/aradium reload`.
  - **Zrównoważenie funkcji z ekonomią SkyBlocka:**
    - Pełne dostrojenie cen wszystkich itemów w rozbudowanym sklepie `/shop` (Etap 2)
      względem nagród z misji, ulepszeń wysp, ulepszeń spawnerów i nowych przedmiotów.

---

### Etap 7 (Punkt 2): Poprawa GUI związanych z wyspą (`/is`)

- **Cel:** Nowoczesny, spójny i czytelny interfejs wszystkich 7 okien GUI wyspy (z uwzględnieniem nowych ulepszeń i misji z Etapów 4–5).

- **Zadania:**
  - Przeprojektować `IslandPanelGUI` (dwukolorowa ramka ze szkła, główka `PLAYER_HEAD`
    z poziomem wyspy, miejscem w `/is top`, saldem i aktywnym etapem misji).
  - Ujednolicić lore, znaczniki statusu (`✔`, `⌛`, `✖`), paski postępu i przyciski
    nawigacji we wszystkich menu `/is` (`IslandSchematicGUI`, `IslandRolesGUI`,
    `IslandWarpsGUI`, `IslandUpgradesGUI`, `IslandTopGUI`, `QuestGUI`).

---

### Etap 8 (Punkt 7): Optymalizacja pluginu (Wydajność, Kod, Zasoby)

- **Cel:** Odciążenie głównego wątku serwera (`TPS`), odchudzenie kodu i przyspieszenie zapytań do bazy danych po wdrożeniu wszystkich nowych funkcji.

- **Zadania:**
  - **Optymalizacja wydajności (`O(1)` i async DB):**
    - Dodać w `QuestManager` indeks aktywnych celów (`EnumMap<QuestType, Set<String>>`),
      aby `QuestProgressListener` pomijał nieśledzone akcje bez wyszukiwania wyspy.
    - Wprowadzić asynchroniczny zapis wsadowy `dirtyIslands` (`write-behind`)
      w `IslandManager` na wzór `EconomyManager`.
    - Zoptymalizować sprawdzanie pochodni anty-mob oraz generatorów poprzez cache chunkowy.
  - **Optymalizacja kodu:**
    - Wydzielić wspólną klasę `utils/GuiItemBuilder.java` dla wszystkich menu GUI
      i usunąć powtarzalny kod pomocniczy w listenerach.
  - **Optymalizacja zasobów i SQL:**
    - Dodać indeksy SQL w `DatabaseManager` (`idx_skyblock_members_player`,
      `idx_skyblock_quests_island`) i wyeliminować wielokrotne odczyty plików YAML w pętli gry.

---

### Etap 9 (Punkt 9): Moderacja czatu oraz System Rang i Uprawnień

- **Cel:** Własny system kar moderacyjnych, czyszczenia czatu oraz rang uprawnień niezależny od zewnętrznych pluginów.

- **Zadania:**
  - **Moderacja czatu (`modules/moderation/`):**
    - Tabela `moderation_punishments` w `DatabaseManager` (bany i wyciszenia z czasem wygaśnięcia i powodem).
    - Komendy `/mute`, `/tempmute`, `/unmute`, `/ban`, `/tempban`, `/unban`, `/kick`.
    - Egzekwowanie kar w `AsyncPlayerPreLoginEvent` oraz `AsyncChatEvent`.
  - **Czyszczenie czatu:**
    - Komenda `/clearchat` (`/cc`) z uprawnieniem bypass dla administracji i powiadomieniem.
  - **System rang i uprawnień (`modules/ranks/`):**
    - Konfiguracja `ranks.yml` (`prefix`, `suffix`, kolor czatu, waga rangi, lista uprawnień i dziedziczenie).
    - Tabela `player_ranks` w `DatabaseManager` oraz nadawanie uprawnień graczom przez `PermissionAttachment`.
    - Komenda `/rank <set|info|list|reload>` oraz formatowanie czatu zgodnie z aktywną rangą.

---

### Etap 10 (Punkt 3): Integracja `PlaceholderAPI` (Tablista / Scoreboard)

- **Cel:** Udostępnienie statystyk ekonomii, wyspy i rang dla pluginów tablisty oraz scoreboardu.

- **Zadania:**
  - Dodać zależność `me.clip:placeholderapi` (`provided`) w `pom.xml` oraz
    `softdepend: [PlaceholderAPI]` w `plugin.yml`.
  - Utworzyć `AradiumPlaceholderExpansion` z obsługą placeholderów:
    `%aradium_money%`, `%aradium_money_formatted%`, `%aradium_island_level%`,
    `%aradium_island_rank%`, `%aradium_island_stage%`, `%aradium_island_members%`,
    `%aradium_rank%`, `%aradium_prefix%`, `%aradium_suffix%`.

---

### Etap 11 (Punkt 4): Testy integracyjne Paper/Bukkit (`AradiumAuditTest`)

- **Cel:** Pełne pokrycie testami zdarzeń gry, zabezpieczeń Anti-Dupe, mechanik SkyBlocka oraz cyklu życia modułów.

- **Zadania:**
  - Dodać zależność `mockito-junit-jupiter` (`test`) w `pom.xml`.
  - Rozbudować `AradiumAuditTest.java` o testy:
    - `InventoryClickEvent` (ochrona Anti-Dupe we wszystkich GUI),
    - `BlockFormEvent` i `BlockBreakEvent` (podstawowy generator cobla z lawy i wody,
      podwójny drop z roślin, specjalne generatory, Silk Touch spawnerów, progres misji),
    - `PlayerArmorChangeEvent`, `BrewEvent`, `PlayerFishEvent`,
    - kar moderacji (`AsyncChatEvent`, `AsyncPlayerPreLoginEvent`) i systemu rang,
    - pełnego cyklu `ModuleManager` (`enable` -> `reload` -> `disable`, w tym wyłączanie modułów z `config.yml`).