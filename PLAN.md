# Plan dalszego rozwoju Aradium (Backlog)

> **Aktualny status:** Wszystkie 6 faz **Aradium Core** (`DatabaseManager`, Ekonomia,
> Sklepy GUI, Spawnery oraz silnik SkyBlock z poziomami, ulepszeniami i 5 etapami misji)
> zostały **zakończone i wdrożone**.
>
> Dokumentacja techniczna gotowych modułów znajduje się w [`AUDIT.md`](AUDIT.md).

---

## Zadania pozostałe do wykonania

### 1. Rozbudowa kategorii Sklepu GUI (`shops/`)

- [ ] Utworzyć dodatkowe kategorie: `shops/farming.yml`, `shops/mobdrops.yml`
  oraz `shops/ores.yml`.

- [ ] Podpiąć nowe kategorie w `shops/main.yml` i zrównoważyć ceny
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

- [ ] Wykonać `git commit` oraz `git push` aktualnego stanu projektu na GitHub
  po zakończeniu prac nad Fazami 1–6 Core.

- [ ] Stosować regułę z `AUDIT.md`: po każdej większej zmianie testy,
  aktualizacja dokumentacji, commit oraz push na GitHub.

### 6. Rozbudowa misji oraz ulepszen wysp

- [ ] Dodać nowe misje oraz ulepszenia wysp.
- [ ] Zrównoważyć nagrody z misji oraz ulepszeń wysp z ekonomią SkyBlocka.

### 7. Optymalizacja pluginu

- [ ] Optymalizacja wydajności pluginu.
- [ ] Optymalizacja kodu pluginu.
- [ ] Optymalizacja zasobów pluginu.


### 8. Dodatkowe funkcje

- [ ] Dodać nowe itemy do pluginu. - specjalne itemy jak generatory cobla, zróżnicowane generatory cobla itp, ochodnia wyłączająca resp mobów obszarowo itp.
- [ ] Zrównoważyć funkcje z ekonomią SkyBlocka. - po rozbudowie sklepu zrównoważyć ceny itemów z misjami i ulepszeniami wysp.
- [ ] Dodać nowe mechaniki do pluginu. - dodatkowe mechaniki jak craftingi przedmiotów , specjalne ulepszenia do broni,narzędzi, zbroi

### 9. Moderacja czatu 

- [ ] Dodać moderację czatu do pluginu - komendy mute/ban/kick
- [ ] Dodać system rang i uprawnień do pluginu - uprawnienia dla rang, prefixy, suffixy itp.
- [ ] Dodać czyszczenie czatu

---

## Plan wdrożenia (Etapy 1–9)

> Weryfikacja techniczna względem Paper `26.3.build.251-stable` i Javy `25`:
> wszystkie 9 planów jest spójnych z `AbstractModule` i `DatabaseManager`.
> Do testów zdarzeń (Etap 9) rekomendowane jest `mockito-junit-jupiter` zamiast
> `MockBukkit` (pełna zgodność z najnowszym API Paper `26.3` i Javą `25`).

---

### Etap 1 (Punkt 5): Punkt kontrolny Git / GitHub (`GitHub Baseline`)

- **Cel:** Zabezpieczenie ukończonych Faz 1–6 Core w repozytorium przed kolejnymi zmianami.

- **Zadania:**
  - Sprawdzić `.gitignore` (`target/`, `.idea/`, `*.iml`, `*.db`, `*.db-wal`, `*.db-shm`).
  - Wykonać commit kontrolny i `git push` obecnego stanu (`73` klasy, `15/15` testów).
  - Powtarzać commit i `git push` po zamknięciu każdego kolejnego etapu.

---

### Etap 2 (Punkt 1): Rozbudowa kategorii Sklepu GUI (`shops/`)

- **Cel:** Rozszerzenie sklepu `/shop` o pełny asortyment SkyBlocka.

- **Zadania:**
  - Utworzyć `shops/farming.yml` (nasiona, sadzonki, pszenica, ziemniaki, marchew,
    melon, dynia, trzcina, kaktus, brodawki, jajka, wieprzowina).
  - Utworzyć `shops/mobdrops.yml` (kości, nici, proch, perły Endu, płomienne różdżki,
    łzy Ghasta, pryzmaryn).
  - Utworzyć `shops/ores.yml` (węgiel, żelazo, złoto, diament, szmaragd, obsydian,
    kwarc, sztabki netheritu).
  - Podpiąć kategorie w `shops/main.yml` i aktualizować kopiowanie zasobów w `ShopModule`.

---

### Etap 3 (Punkt 7): Optymalizacja pluginu (Wydajność, Kod, Zasoby)

- **Cel:** Odchudzenie głównego wątku serwera (`TPS`) oraz kodu klas GUI przed rozbudową.

- **Zadania:**
  - **Wydajność (`O(1)` i async DB):**
    - Dodać w `QuestManager` indeks aktywnych celów (`EnumMap<QuestType, Set<String>>`),
      aby `QuestProgressListener` pomijał nieśledzone akcje bez szukania wyspy.
    - Wprowadzić asynchroniczny zapis wsadowy `dirtyIslands` (`write-behind`)
      w `IslandManager` na wzór `EconomyManager`.
  - **Optymalizacja kodu:**
    - Wydzielić wspólną klasę `utils/GuiItemBuilder.java` dla wszystkich menu GUI.
  - **Optymalizacja zasobów i SQL:**
    - Dodać indeksy SQL w `DatabaseManager` (`idx_skyblock_members_player`,
      `idx_skyblock_quests_island`) i ograniczyć powtarzalne odczyty konfiguracji.

---

### Etap 4 (Punkt 2): Poprawa wyglądu i ergonomii GUI wyspy (`/is`)

- **Cel:** Nowoczesny, jednolity wygląd wszystkich 7 menu w `modules/skyblock/gui/`.

- **Zadania:**
  - Przeprojektować `IslandPanelGUI` (dwukolorowa ramka ze szkła, główka `PLAYER_HEAD`
    z poziomem wyspy, miejscem w `/is top`, saldem i aktywnym etapem misji).
  - Ujednolicić lore, znaczniki statusu (`✔`, `⌛`, `✖`), paski postępu i przyciski
    nawigacji we wszystkich oknach `/is` (`Schematic`, `Roles`, `Warps`, `Upgrades`,
    `Top`, `QuestGUI`).

---

### Etap 5 (Punkt 8): Dodatkowe funkcje (Generatory, Pochodnia Anty-Mob, Crafting)

- **Cel:** Nowe przedmioty techniczne, receptury i rozwój ekwipunku gracza.

- **Zadania:**
  - **Zróżnicowane generatory:** blok generatora bruku oraz generatorów rud (`T1–T3`)
    z losowaniem surowców wg wag z konfiguracji i podnoszeniem bez utraty danych PDC.
  - **Pochodnia Anty-Mob:** blok PDC blokujący naturalny spawn potworów
    (`SpawnReason.NATURAL`) w konfigurowalnym zasięgu bez blokowania spawnerów.
  - **Crafting i ulepszenia sprzętu:** receptury `ShapedRecipe` na generatory/zbroje T1
    oraz przedmioty ulepszające (podnoszenie tieru zbroi + runy broni i narzędzi).

---

### Etap 6 (Punkt 6): Rozbudowa misji, ulepszeń wysp i pełny balans ekonomii

- **Cel:** Rozszerzenie końcowej gry (`endgame`) i wyrównanie całej ekonomii SkyBlocka.

- **Zadania:**
  - Dodać nowe typy ulepszeń wyspy w `IslandUpgradeType`: `GENERATOR_TIER`
    (lepsze rudy z generatorów) oraz `MOB_DROP_BONUS` (bonus dropu/XP na wyspie).
  - Rozszerzyć `quests.yml` i `QuestGUI` o **Etap VI i VII** (wraz z przewijaniem etapów).
  - Zbalansować ceny kupna/sprzedaży w `shops/*.yml` względem nagród z etapów 1–7,
    ulepszeń wysp oraz kosztów spawnerów.

---

### Etap 7 (Punkt 9): Moderacja czatu oraz System Rang i Uprawnień

- **Cel:** Własny system kar i rang uprawnień niezależny od zewnętrznych pluginów.

- **Zadania:**
  - **Moduł Moderacji (`modules/moderation/`):**
    - Tabela `moderation_punishments` w `DatabaseManager` (bany i wyciszenia).
    - Komendy `/ban`, `/tempban`, `/unban`, `/mute`, `/tempmute`, `/unmute`, `/kick`
      oraz `/clearchat` (`/cc`).
    - Egzekwowanie blokad w `AsyncPlayerPreLoginEvent` i `AsyncChatEvent`.
  - **Moduł Rang i Uprawnień (`modules/ranks/`):**
    - Konfiguracja `ranks.yml` (`prefix`, `suffix`, kolor czatu, waga, dziedziczenie
      uprawnień), tabela `player_ranks` i `PermissionAttachment`.
    - Komenda `/rank <set|info|list|reload>` oraz formatowanie czatu.

---

### Etap 8 (Punkt 3): Integracja `PlaceholderAPI`

- **Cel:** Udostępnienie statystyk ekonomii, wyspy i rang na tabliście i scoreboardzie.

- **Zadania:**
  - Dodać zależność `me.clip:placeholderapi` (`provided`) w `pom.xml` oraz
    `softdepend: [PlaceholderAPI]` w `plugin.yml`.
  - Utworzyć `AradiumPlaceholderExpansion` z obsługą placeholderów:
    `%aradium_money%`, `%aradium_money_formatted%`, `%aradium_island_level%`,
    `%aradium_island_rank%`, `%aradium_island_stage%`, `%aradium_island_members%`,
    `%aradium_rank%`, `%aradium_prefix%`, `%aradium_suffix%`.

---

### Etap 9 (Punkt 4): Testy integracyjne zdarzeń Paper/Bukkit

- **Cel:** Pokrycie testami zdarzeń gry, zabezpieczeń Anti-Dupe i cyklu życia modułów.

- **Zadania:**
  - Dodać zależność `mockito-junit-jupiter` (`test`) w `pom.xml`.
  - Rozbudować `AradiumAuditTest.java` o testy `InventoryClickEvent` (Anti-Dupe GUI),
    `BlockBreakEvent`, `PlayerArmorChangeEvent`, `BrewEvent`, `PlayerFishEvent`,
    kar czatu oraz pełnego cyklu `ModuleManager` (`enable` -> `reload` -> `disable`).