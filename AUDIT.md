# Audyt techniczny pluginu Aradium

> **Zasada utrzymania dokumentu:** `AUDIT.md` jest głównym źródłem prawdy o stanie
> technicznym, architekturze, testach i historii zmian pluginu **Aradium**.
>
> Otwarte błędy trafiają do sekcji 2, a po naprawie i weryfikacji wpis przenoszony
> jest do sekcji 4 (**Historia zmian**). Przyszłe zadania znajdują się w [`PLAN.md`](PLAN.md).

> [!IMPORTANT]
> **Zasada wersjonowania Git / GitHub:** Po każdej większej zmianie (wdrożeniu fazy,
> nowego modułu, refaktoryzacji lub poprawce błędów) należy obowiązkowo:
>
> 1. Uruchomić pełną weryfikację:
>    `mvn clean test package "-Dmaven.compiler.showDeprecation=true"`
>    (wymagane: `0` błędów i `0` ostrzeżeń kompilacji).
>
> 2. Zaktualizować `AUDIT.md` (architektura + Changelog) i wyczyścić wykonane
>    zadania z `PLAN.md`.
>
> 3. Wykonać `git commit` z czytelnym opisem zmian oraz `git push` na GitHub.

---

## 1. Podsumowanie wykonawcze i architektura

- **Nazwa pluginu:** `Aradium` (`pl.nauka.aradium.AradiumPlugin`)

- **Wersja projektu:** `2.0.0-SNAPSHOT` (`pl.nauka:aradium`)

- **Stack technologiczny:** Maven, Java `25` (`--release 25`),
  Paper API `26.3.build.251-stable` (`api-version: '26.3'`)

- **Architektura:** Modularna (`core` + moduły w `modules/*`), sterowana przez
  `ModuleManager` oraz sekcję `modules:` w `config.yml`

- **Skala projektu:** `79` głównych klas Java w `src/main/java` (`0` ostrzeżeń `javac`)
  oraz `24` pliki zasobów w `src/main/resources`

- **Testy automatyczne:** `17/17` testów JUnit 5 (`org.junit.jupiter:junit-jupiter:5.11.4`)
  w `src/test/java/pl/nauka/aradium/AradiumAuditTest.java`

- **Artefakt wdrożeniowy:** `target/Aradium.jar` (kopiowany na serwer do
  `C:\Users\mkopy\Desktop\serv\plugins\Aradium.jar`)

---

### 1.1. Konfiguracja i zasoby YAML

- **`pom.xml`**
  - Zależności: `paper-api:26.3.build.251-stable` oraz `junit-jupiter:5.11.4`.
  - Kompilacja pod `--release 25` i stała nazwa pliku wynikowego `Aradium.jar`.

- **`src/main/resources/plugin.yml`**
  - Metadane (`api-version: '26.3'`) oraz uprawnienia `aradium.admin`, `aradium.player`.
  - Rejestracja komend: `/aradium`, `/magicitem`, `/magicarmor`, `/oreregion`,
    `/money`, `/pay`, `/eco`, `/shop`, `/sellall` (`/sprzedajwszystko`),
    `/sellgui` (`/sprzedaj`), `/spawner`, `/island` (`/is`, `/wyspa`).

- **`src/main/resources/config.yml`**
  - Flagi modułów: `economy`, `shop`, `spawners`, `skyblock`, `magic_items`,
    `ore_regions`, `armor`.
  - Konfiguracja `database`, `economy`, `skyblock`, `ore_regions`, komunikaty
    `messages` oraz definicje 7 magicznych przedmiotów (`magic_items`).

- **`src/main/resources/armor.yml`**
  - Zestawy zbroi: `gornik` (T1–T5), `handlarz` (T1–T3), `wojownik` (T1–T5).
  - Specjalny element `special_armor.buty_lotnika` (niezniszczalne buty latania).

- **`src/main/resources/spawners.yml`**
  - Definicje `4` niezależnych ścieżek ulepszeń (`upgrades.delay` `1–5`, `upgrades.amount` `1–5`,
    `upgrades.range` `1–4`, `upgrades.light-immunity` `0–1`), cennik wszystkich `85` mobów z MC
    (`mobs:` poza bossami `WITHER`, `ENDER_DRAGON`, `WARDEN`) z bazową świnią (`PIG`) oraz
    zachowana sekcja kompatybilności `tiers`.

- **`src/main/resources/blocks-value.yml`**
  - Punktacja bloków używana przy asynchronicznym liczeniu poziomu wyspy (`/is level`).

- **`src/main/resources/quests.yml`**
  - Pełne 5 etapów po 7 współdzielonych misji (`35` zadań łącznie), konfiguracja
    paska postępu, nagrody pieniężne/przedmiotowe oraz bonusy za etapy.

- **`src/main/resources/shops/*.yml` (`17` plików kategorii i podkategorii)**
  - Główne menu `/shop` (`main.yml` — 10 kategorii głównych), podkategorie budowania
    (`building`, `blocks`, `wood`, `natural`, `decorative_blocks`), kategorie tematyczne
    (`ores`, `farming`, `food`, `combat`, `redstone`, `magic`, `mobdrops`, `decoration`,
    `music`, `storage`, `misc`) z wielostronicowymi sekcjami `pages:` i balansem cen
    pod SkyBlock.

- **`src/main/resources/regions.yml`**
  - Domyślny szkielet `regions: {}` dla stref odnawialnych rud.

---

### 1.2. Rdzeń pluginu, baza danych i narzędzia (`core` & `utils`)

- **`AradiumPlugin.java`** (`pl.nauka.aradium`)
  - Inicjalizacja `DatabaseManager`, rejestracja 7 modułów w `ModuleManager`
    oraz obsługa `onEnable()` i bezpiecznego zamykania w `onDisable()`.

- **`core/PluginModule.java` i `core/AbstractModule.java`**
  - Kontrakt cyklu życia modułu (`getId`, `enable`, `disable`, `reload`, `isEnabled`)
    z automatycznym wyrejestrowaniem listenerów (`HandlerList.unregisterAll`) i komend.

- **`core/ModuleManager.java` i `core/AradiumCommand.java`**
  - Zarządzanie stanem `modules.<id>`, uruchamianie i wyłączanie w odwrotnej
    kolejności oraz komenda `/aradium <reload|modules>`.

- **`core/database/DatabaseManager.java`**
  - Połączenie JDBC SQLite (`WAL`) lub MySQL oraz schemat 7 tabel SQL:
    `economy_accounts`, `skyblock_islands`, `skyblock_members`,
    `skyblock_role_permissions`, `skyblock_upgrades`, `skyblock_warps`, `skyblock_quests`.

- **`utils/ChatUtils.java`**
  - Konwersja Adventure (`LegacyComponentSerializer`), `itemComponent` bez domyślnej
    kursywy dla nazw/opisów przedmiotów oraz helper `getMessage` z fallbackiem.

---

### 1.3. Moduł 1: Autorska Ekonomia i Sklepy GUI (`economy` & `shop`)

- **Ekonomia (`modules/economy/`):**
  - `EconomyManager.java`: cache `ConcurrentHashMap<UUID, Double>` ($O(1)$), metody
    `getMoney`, `hasMoney`, `addMoney`, `takeMoney`, `setMoney`, `transferMoney`
    oraz zapis SQLite/MySQL i YAML.
  - `EconomyModule.java`: cykl życia, cykliczny zrzut `write-behind` co 60s,
    tworzenie konta przy wejściu i flush przy wyłączeniu serwera.
  - Komendy z `TabCompleter`: `MoneyCommand.java` (`/money`),
    `PayCommand.java` (`/pay`), `EcoAdminCommand.java` (`/eco <give|take|set>`).

- **Sklepy GUI (`modules/shop/`):**
  - `ShopModule.java` i `ShopManager.java`: kopiowanie wszystkich 17 domyślnych plików
    `shops/*.yml`, wielostronicowe parsowanie (`pages:`, siatka `7x4` `computeAutoSlots`
    z automatycznym dzieleniem stron > 28 pozycji), indeks `bestSellByMaterial` ($O(1)$
    dla `/sellall` i `/sellgui`) oraz bezpieczne rozliczanie `SELL_GUI` i zamykanie okien
    przy `onDisable`.
  - `ShopCategory.java`, `ShopItem.java`, `ShopInventoryHolder.java`: modele kategorii
    wielostronicowych (`pageCount`, `normalizePage`, `pageItems`), pozycji sklepu oraz
    `ScreenType` (`CATEGORY`, `BUY_SCREEN`, `SELL_SCREEN`, `BUY_STACKS_SCREEN`, `SELL_GUI`)
    z ochroną `InventoryHolder` i PDC `shop_ui_button`.
  - `ShopGUI.java`, `ShopListener.java`, `ShopCommand.java`, `SellAllCommand.java`,
    `SellGuiCommand.java`: dolny pasek nawigacji (głowa gracza z saldem, przyciski stron,
    powrót/zamknięcie), 45-slotowe ekrany wyboru ilości (`-32/-16/-1/+1/+16/+32` + tryb
    kupna w stacjach i przycisk *Sprzedaj wszystko*), pełny Anti-Dupe (w tym blokada
    `COLLECT_TO_CURSOR` w `SELL_GUI`), ochrona itemów z metadanymi/PDC oraz komendy
    `/shop [kategoria]` (`/sklep`), `/sellall [inventory|hand|<materiał>]` i `/sellgui`.

---

### 1.4. Moduł 2: Zaawansowane Spawnery (`spawners`)

- **`SpawnerModule.java`, `SpawnerUpgradeTrack.java`, `SpawnerTrackLevelDefinition.java`, `SpawnerMobDefinition.java`, `SpawnerState.java` i `SpawnerTierDefinition.java`**
  - Ładowanie `spawners.yml` (`5` ścieżek ulepszeń: `DELAY`, `AMOUNT`, `RANGE`, `LIGHT_IMMUNITY`,
    `GROUND_IMMUNITY`), katalog `85` mobów z wykluczeniem bossów (`FORBIDDEN_BOSS_ENTITIES`:
    `WITHER`, `ENDER_DRAGON`, `WARDEN`) oraz kompatybilność wsteczna starszych tierów `1–3`.

- **`SpawnerDataManager.java`**
  - Zapis/odczyt stanu `SpawnerState` (`spawner_entity`, `spawner_unlocked_mobs`, `spawner_delay_lvl`,
    `spawner_amount_lvl`, `spawner_range_lvl`, `spawner_light_lvl`, `spawner_ground_lvl`) w PDC bloku
    `CreatureSpawner` oraz przedmiotu (Silk Touch), domyślny mob `EntityType.PIG`, bezpieczna kolejność
    ustawiania `minSpawnDelay` / `maxSpawnDelay` oraz reguła `SpawnRule(0, 15, 0, 15)` przez `SpawnerEntry`.

- **`SpawnerAirSpawnTask.java`**
  - Task cykliczny (co `20` ticków) śledzący załadowane spawnery z wykupionym `GROUND_IMMUNITY`
    ("Spawn w powietrzu"), omijający restrykcje `SpawnPlacements` (trawa, grzybnia, woda, lawa,
    pełne podłoże) i respiący dowolnego z `85` mobów w powietrzu z poszanowaniem `requiredPlayerRange`,
    `maxNearbyEntities` i warunków światła (gdy `LIGHT_IMMUNITY` nie jest odblokowane).

- **`SpawnerUpgradeHolder.java` i `SpawnerGUI.java`**
  - Dwuekranowe GUI spawnera (`MAIN_MENU` `36` slotów z 5 ścieżkami ulepszeń oraz `MOB_SHOP`
    `54` slotów na 4 stronach do zakupu i przełączania `85` mobów) powiązane ze współrzędnymi
    konkretnego bloku (Anti-Dupe), pobierające walutę z `EconomyManager` i czyste itemy z ekwipunku.

- **`SpawnerListener.java` i `SpawnerCommand.java`**
  - Otwieranie menu przez `Shift + PPM`, wydobycie Silk Touch z zachowaniem wszystkich ulepszeń
    i odblokowanych mobów, start z bazową świnią, synchronizacja spawnerów przy `ChunkLoadEvent` /
    `ChunkUnloadEvent` / `SpawnerSpawnEvent`, ochrona przed wybuchami oraz `/spawner give`.

---

### 1.5. Moduły 3–5: Silnik SkyBlock Core (`skyblock`)

- **`SkyBlockModule.java`**
  - Koordynator menedżerów, GUI, listenerów oraz komendy `/is` (`/island`, `/wyspa`).

- **`island/` (`Island`, `IslandManager`, `SkyBlockWorldGenerator`, `IslandSchematic`, `IslandRole`, `IslandPermission`, `BorderColor`, `IslandWarp`)**
  - Void world `skyblock_world`, spiralny przydział wysp, schematy (`classic`,
    `desert`, `nether`), kolory `WorldBorder`, role, sesyjny `TRUSTED` i warpy.
  - Trwały zapis wysp w SQLite/MySQL (`skyblock_*`) oraz fallback w YAML.

- **`level/` (`BlockValueManager`, `IslandLevelCalculator`, `IslandTopCache`)**
  - Asynchroniczne liczenie poziomu wyspy na bazie `ChunkSnapshot`, przyrostowe
    odświeżanie punktów przy zmianie bloków, cache rankingu `/is top` oraz
    automatyczna synchronizacja misji `ISLAND_LEVEL`.

- **`upgrades/` (`IslandUpgradeType`, `IslandUpgradeManager`)**
  - Ulepszenia wyspy za `$`: `WORLD_BORDER`, `SPAWNER_LIMIT`, `CROP_GROWTH`,
    `MEMBER_LIMIT` z zapisem w `skyblock_upgrades`.

- **`quests/` (`QuestType`, `QuestDefinition`, `QuestStage`, `QuestProgress`, `QuestManager`)**
  - Obsługa 5 etapów po 7 współdzielonych misji wyspy, serializacja JSON,
    paski postępu `buildProgressBar`, kontrola odblokowań etapów i wypłata nagród.

- **`gui/` (`SkyBlockGuiHolder`, `IslandSchematicGUI`, `IslandPanelGUI`, `IslandRolesGUI`, `IslandWarpsGUI`, `IslandUpgradesGUI`, `IslandTopGUI`, `QuestGUI`)**
  - Komplet menu wyspy (w tym `QuestGUI` z zieloną/żółtą/czerwoną szybą i paskiem
    postępu) chronionych przez `SkyBlockGuiHolder`.

- **`listeners/` (`IslandProtectionListener`, `SpawnerLimitListener`, `QuestProgressListener`)**
  - Ochrona wyspy, limit spawnerów, przyspieszony wzrost roślin (`BlockGrowEvent`),
    czyszczenie `TRUSTED` przy wyjściu i zliczanie wszystkich 11 typów celów misji.

- **`commands/IslandCommand.java`**
  - Obsługa `/is`: `create`, `home`, `sethome`, `panel`, `quests`, `level`, `top`,
    `upgrades`, `roles`, `privacy`, `border`, `warp`, `setwarp`, `delwarp`,
    `invite`, `join`, `kick`, `leave`, `trust`, `untrust`, `transfer`, `delete`.

---

### 1.6. Moduły RPG i Świata (`magicitems`, `armor`, `oreregions`)

- **`magicitems/` (`MagicItemModule`, `MagicItemDefinition`, `MagicItemListener`, `MagicItemCommand`)**
  - Magiczne przedmioty oparte o PDC i `Registry.MOB_EFFECT`, trwałe cooldowny
    w `cooldowns.yml`, zdolność `duch` (`SPECTATOR` na 2s z `flySpeed 3`, powrót
    stanu, końcowe `BLINDNESS` 3s + `SPEED` 2s) i blokada `OFF_HAND`/craft/grindstone.

- **`armor/` (`ArmorModule`, `ArmorSetDefinition`, `ArmorTierBonus`, `ArmorPieceType`, `SpecialArmorDefinition`, `ArmorListener`, `ArmorCommand`)**
  - Zestawy `gornik`, `handlarz`, `wojownik` (`CustomModelDataComponent`, dynamiczny
    licznik `(%equipped%/4)` w lore, bonusy pełnego setu przez `PotionEffect`
    i `Attribute.MAX_HEALTH`), niezniszczalne `buty_lotnika` oraz `/magicarmor`.

- **`oreregions/` (`OreRegionModule`, `CuboidRegion`, `OreRegionCommand`, `OreBreakListener`)**
  - Strefy odnawialnych rud z migracją `regions.yml`, niemutowalnym `BlockKey`,
    trwałą kolejką `pending_regens` po restarcie/crashu i komendą `/oreregion`.

---

### 1.7. Testy automatyczne (`AradiumAuditTest.java` — 17/17 zaliczonych)

1. `cuboidRegionNormalizesMinMaxAndChecksBoundaries` — granice regionów rud.

2. `cooldownCalculationAndIdNormalizationWorkCorrectly` — cooldowny i ID itemów.

3. `blockKeyAndPendingMaterialResolutionAreSafe` — `BlockKey` i `pending_regens`.

4. `ghostFlySpeedConversionAndDefinitionFlagWorkCorrectly` — zdolność `duch`.

5. `armorFullSetMatchingRequiresFourMatchingPiecesAndTiers` — matching 4/4 zbroi.

6. `armorMaxHealthBonusAndYamlStructureAreValid` — `armor.yml` i `MAX_HEALTH`.

7. `armorLoreFormattingUpdatesEquippedCountAndClampsBounds` — licznik `(%equipped%/4)`.

8. `aviatorBootsGameModeAndSingleFallProtectionWorkCorrectly` — "Buty lotnika".

9. `economyManagerOperationsAndYamlPersistenceWorkCorrectly` — salda i zapis YAML.

10. `spawnerQuestsAndShopYamlConfigsAreValid` — walidacja `spawners.yml`, `quests.yml`, `shops/blocks.yml` i `shops/natural.yml`.

11. `shopManagerAndAutoLoreGenerationWorkCorrectly` — formatowanie kwot, podkategorie i lore sklepu.

12. `spawnerModuleParsingAndGuiLoreWorkCorrectly` — parsowanie tierów spawnerów.

13. `skyblockIslandsRolesPermissionsAndGridWorkCorrectly` — siatka wysp, role i uprawnienia.

14. `skyblockLevelsTopAndUpgradesWorkCorrectly` — `blocks-value.yml`, `/is top`, ulepszenia.

15. `skyblockQuestsAllFiveStagesProgressAndGuiWorkCorrectly` — 5 etapów misji, JSON,
    paski postępu i cele `BLOCK_AND_CRAFT`.

16. `allShopCategoriesLoadCleanlyWithValidMaterialsAndPagination` — walidacja wszystkich 17 plików
    `shops/*.yml`, materiałów Paper `26.3`, powiązań `parent-shop`/`target-shop`, indeksowania
    najlepszych cen sprzedaży oraz automatycznego stronicowania.

17. `spawnerFourUpgradeTracksAndAllNonBossMobsWorkCorrectly` — walidacja `5` ścieżek ulepszeń
    (`DELAY`, `AMOUNT`, `RANGE`, `LIGHT_IMMUNITY`, `GROUND_IMMUNITY`) w `spawners.yml`,
    kompletu `85` mobów z wykluczeniem bossów (`WITHER`, `ENDER_DRAGON`, `WARDEN`),
    domyślnego stanu `PIG`, serializacji PDC, reguły światła `SpawnRule(0, 15, 0, 15)` i lore GUI.

---

### 1.8. Jak dodać nowy moduł

1. Utworzyć nowy pakiet w `pl.nauka.aradium.modules.<nazwa_modułu>`.

2. Utworzyć klasę dziedziczącą po `AbstractModule` (unikalne `id`, np. `"custom_drops"`).

3. W `onEnable()` zarejestrować listenery (`registerListener(...)`) i komendy
   (`registerCommand(...)`), a w `onDisable()` posprzątać zasoby.

4. Dodać `<id>: true` w `modules:` w `config.yml` i zarejestrować moduł przez
   `moduleManager.registerModule(...)` w `AradiumPlugin`.

---

### 1.9. Standard pracy i publikacji zmian (GitHub Workflow)

1. Zweryfikuj build i testy pod JDK 25:
   ```powershell
   mvn clean test package "-Dmaven.compiler.showDeprecation=true"
   ```

2. Wdróż zaktualizowany `target/Aradium.jar` (oraz zmienione konfiguracje YAML)
   do katalogu `C:\Users\mkopy\Desktop\serv\plugins\`.

3. Zaktualizuj `AUDIT.md` oraz wyczyść zrealizowane punkty w `PLAN.md`.

4. Wykonaj commit i wyślij zmiany na GitHub po każdej większej zmianie:
   ```powershell
   git status
   git add -A
   git commit -m "<krótki opis większej zmiany>"
   git push
   ```

---

## 2. Aktywne problemy i błędy

_Brak otwartych błędów — wszystkie dotychczas zidentyfikowane pozycje (`AUD-01` – `AUD-17`)
zostały naprawione, zweryfikowane i przeniesione do historii zmian._

---

## 3. Dalsze kierunki rozwoju

Aktualna lista zadań pozostałych do realizacji znajduje się w pliku [`PLAN.md`](PLAN.md).

---

## 4. Historia zmian (Changelog)

### 2026-11-15 — Etap 3 (Punkt 10): Zaawansowane ulepszenia spawnerów (`5` ścieżek + spawn w powietrzu + 85 mobów) (`2.0.0-SNAPSHOT`)

- Dodano klasy modelu i stanu dla modułu `spawners`: `SpawnerUpgradeTrack`,
  `SpawnerTrackLevelDefinition`, `SpawnerMobDefinition`, `SpawnerState` (z domyślną świnią
  `EntityType.PIG` i twardą blokadą bossów `FORBIDDEN_BOSS_ENTITIES`: `WITHER`, `ENDER_DRAGON`,
  `WARDEN`) oraz `SpawnerAirSpawnTask` do spawnowania w powietrzu.

- Przebudowano `spawners.yml`, `SpawnerModule` oraz `SpawnerDataManager`: wprowadzono
  `5` niezależnych ścieżek ulepszeń (`DELAY` `1–5`, `AMOUNT` `1–5`, `RANGE` `1–4`,
  `LIGHT_IMMUNITY` `0–1` ustawiające `SpawnRule(0, 15, 0, 15)` przez `SpawnerEntry`
  oraz `GROUND_IMMUNITY` `0–1` omijające wymagania podłoża `SpawnPlacements`),
  pełny katalog `85` mobów z Minecrafta oraz pełne przenoszenie stanu PDC przy `Silk Touch`.

- Wdrożono dwuekranowe GUI spawnera w `SpawnerGUI`, `SpawnerUpgradeHolder` i `SpawnerListener`
  (`36`-slotowy panel główny z 5 ścieżkami + `54`-slotowy wielostronicowy sklep mobów na 4 stronach
  z Anti-Dupe, synchronizacja chunków/vanilla spawnu), zaktualizowano `/spawner give` i `config.yml`.

- Zaktualizowano test JUnit 5 #17 (`spawnerFourUpgradeTracksAndAllNonBossMobsWorkCorrectly`),
  uzyskując `80` klas głównych bez ostrzeżeń kompilacji oraz `17/17` zaliczonych testów
  i wdrożono zmiany na serwer testowy.

### 2026-11-15 — Etap 2 (Punkt 1): Rozbudowa Sklepu GUI wzorowana na EconomyShopGUI (`2.0.0-SNAPSHOT`)

- Przebudowano model danych i logikę `modules/shop/` (`ShopItem`, `ShopCategory`,
  `ShopInventoryHolder`, `ShopManager`, `ShopModule`) pod wielostronicowość (`pages:`,
  siatka `7x4` z automatycznym podziałem > 28 pozycji), podkategorie oraz indeks
  najwyższej ceny sprzedaży $O(1)$ (`bestSellByMaterial`).

- Wdrożono w `ShopGUI` i `ShopListener` dolny pasek nawigacji (głowa gracza z saldem,
  nawigacja stron, powrót/zamknięcie), 45-slotowe ekrany wyboru ilości (`BUY_SCREEN`,
  `SELL_SCREEN`, `BUY_STACKS_SCREEN`: `-32/-16/-1/+1/+16/+32` + tryb staków i przycisk
  *Sprzedaj wszystko*) oraz pełny Anti-Dupe z blokadą `COLLECT_TO_CURSOR` w `SELL_GUI`.

- Dodano komendy `SellAllCommand` (`/sellall`) oraz `SellGuiCommand` (`/sellgui`)
  z bezpiecznym zwrotem przedmiotów niesprzedawalnych/specjalnych przy zamknięciu lub `onDisable`.

- Utworzono komplet 17 polskich plików `shops/*.yml` (`main`, `building`, `blocks`, `wood`,
  `natural`, `decorative_blocks`, `ores`, `farming`, `food`, `combat`, `redstone`, `magic`,
  `mobdrops`, `decoration`, `music`, `storage`, `misc`) zbalansowanych pod SkyBlocka
  i przetestowanych w JUnit 5 (`75` klas bez ostrzeżeń, `16/16` testów, wdrożono na serwer).

### 2026-11-14 — Dokumentacja i proces GitHub (`2.0.0-SNAPSHOT`)

- Uporządkowano czytelność `AUDIT.md` (podział na podsekcje `1.1`–`1.9`,
  wyraźne odstępy i brak rozjeżdżających się opisów).

- Wprowadzono zasadę wykonywania commita i `git push` na GitHub po większych zmianach.

- Wyczyszczono `PLAN.md` z wykonanych prac, zostawiając wyłącznie przyszłe zadania.

### 2026-11-14 — Core Faza 6: System Misji Wysp (`2.0.0-SNAPSHOT`)

- Rozbudowano `quests.yml` o pełne 5 etapów po 7 współdzielonych zadań (`35` misji).

- Dodano pakiet `skyblock/quests` (`QuestType`, `QuestDefinition`, `QuestStage`,
  `QuestProgress`, `QuestManager`) oraz persystencję `skyblock_quests` w SQL/YAML.

- Dodano `QuestGUI`, `QuestProgressListener`, `/is quests` oraz test #15
  (`73` klasy bez ostrzeżeń, `15/15` testów, wdrożono na serwer).

### 2026-11-14 — Core Faza 5: Poziomy Wyspy i Ulepszenia (`2.0.0-SNAPSHOT`)

- Dodano `blocks-value.yml`, `BlockValueManager`, asynchroniczny `IslandLevelCalculator`
  (`ChunkSnapshot`), `IslandTopCache` i `IslandTopGUI` (`/is top`).

- Dodano `IslandUpgradeType`, `IslandUpgradeManager`, `IslandUpgradesGUI` (`/is upgrades`)
  oraz `SpawnerLimitListener` (`14/14` testów, `66` klas bez ostrzeżeń).

### 2026-11-14 — Core Faza 4: SkyBlock Core — Zarządzanie Wyspą (`2.0.0-SNAPSHOT`)

- Wdrożono `SkyBlockModule`, `SkyBlockWorldGenerator`, `IslandManager`, schematy wysp
  (`IslandSchematicGUI`), menu GUI (`IslandPanelGUI`, `IslandRolesGUI`, `IslandWarpsGUI`),
  `IslandProtectionListener` i `/island` (`13/13` testów).

### 2026-11-14 — Core Faza 3: Zaawansowane Spawnery (`2.0.0-SNAPSHOT`)

- Wdrożono `SpawnerModule`, `SpawnerUpgradeHolder`, `SpawnerGUI` (`Shift + PPM`),
  `SpawnerListener` (Silk Touch, PDC, wybuchy, Anti-Dupe) i `/spawner give` (`12/12` testów).

### 2026-11-14 — Core Fazy 1–2: Fundament Danych, Ekonomia i Sklep GUI (`2.0.0-SNAPSHOT`)

- Przygotowano `PLAN.md`, centralny `DatabaseManager`, `EconomyManager` i `EconomyModule`
  (`/money`, `/pay`, `/eco`) oraz `ShopModule` (`/shop`, `shops/main.yml`,
  `shops/blocks.yml`, Anti-Dupe w `ShopListener`, `10/10` -> `11/11` testów).

### 2026-11-14 — Moduły Armor i MagicItems (`2.0.0-SNAPSHOT`)

- Dodano element `special_armor.buty_lotnika` (niezniszczalne `LEATHER_BOOTS`,
  latanie + ochrona trybów `CREATIVE`/`SPECTATOR` + ochrona przed 1. upadkiem, `8/8` testów).

- Dodano dynamiczny licznik `(%equipped%/4)` w lore magicznych zbroi (`7/7` testów).

- Wdrożono moduł `armor` (`gornik`, `handlarz`, `wojownik`, PDC + `CustomModelData`, `6/6` testów).

- Przebudowano zdolność `duch` (`SPECTATOR` 2s z `flySpeed 3`, powrót, `BLINDNESS` + `SPEED`, `4/4` testy).

### 2026-11-14 — Refaktoryzacja modularna i audyt bazowy (`1.0` -> `2.0.0-SNAPSHOT`)

- Przeprowadzono audyt bazowy (`AUD-01` – `AUD-13`), refaktoryzację do architektury
  modularnej pod Paper `26.3.build.251-stable` i Javę 25 (zero ostrzeżeń).

- Wdrożono poprawki `AUD-14` – `AUD-17` (trwałe `cooldowns.yml`, `pending_regens`,
  ochrona `OFF_HAND`/craftingu/grindstone), ustawiono stały build `Aradium.jar`
  i wyczyszczono listę aktywnych błędów.
