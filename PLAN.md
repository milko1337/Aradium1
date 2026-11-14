# Plan architektury i wdrożenia Aradium Core (Ekonomia, Sklepy, Spawnery, SkyBlock Core)

> **Status dokumentu:** Fazy 1, 2, 3, 4 i 5 wdrożone (`DatabaseManager` + Ekonomia + Sklep GUI `/shop` + Zaawansowane Spawnery GUI/Silk Touch `/spawner` + SkyBlock Core `/is` z poziomami, rankingiem `/is top` i ulepszeniami `/is upgrades`).  
> **Docelowa platforma:** Paper `26.3.build.251-stable` (`api-version: '26.3'`), Java `25`, architektura modularna oparta o `AbstractModule` i `ModuleManager`.

---

## 1. Struktura pakietów projektu Core (`pl.nauka.aradium`)

```text
pl.nauka.aradium
├── AradiumPlugin.java                  # Klasa główna rejestrująca moduły w ModuleManager
├── core
│   ├── PluginModule.java               # Kontrakt cyklu życia modułu (enable/disable/reload)
│   ├── AbstractModule.java             # Bazowa klasa z automatycznym czyszczeniem listenerów
│   ├── ModuleManager.java              # Zarządzanie flagami modules.<id> w config.yml
│   ├── AradiumCommand.java             # Komenda administracyjna /aradium <reload|modules>
│   └── database
│       └── DatabaseManager.java        # Połączenie JDBC (SQLite WAL / MySQL), schemat tabel i migracje
├── utils
│   └── ChatUtils.java                  # Narzędzia Adventure Component / LegacySerializer
└── modules
    ├── economy                         # MODUŁ 1A: Autorska Ekonomia
    │   ├── EconomyModule.java          # Cykl życia modułu ekonomii i auto-zapis (write-behind)
    │   ├── EconomyManager.java         # Cache ConcurrentHashMap<UUID, Double> + SQLite/YAML
    │   ├── MoneyCommand.java           # /money [gracz]
    │   ├── PayCommand.java             # /pay <gracz> <kwota>
    │   └── EcoAdminCommand.java        # /eco <give|take|set> <gracz> <kwota>
    ├── shop                            # MODUŁ 1B: Sklepy GUI (UltimateShop Clone)
    │   ├── ShopModule.java             # Ładowanie shops/*.yml, zamykanie GUI przy disable
    │   ├── ShopManager.java            # Rejestr kategorii (main, blocks, farming, mobdrops)
    │   ├── ShopCategory.java           # Definicja kategorii GUI, formatu lore i mapy slotów
    │   ├── ShopItem.java               # Definicja pozycji (slot, materiał, buyPrice, sellPrice)
    │   ├── ShopInventoryHolder.java    # Dedykowany InventoryHolder (Anti-Dupe)
    │   ├── ShopGUI.java                # Generator GUI + automatyczne lore instrukcji kliknięć
    │   ├── ShopListener.java           # Blokada wkładania/przenoszenia itemów, LPM/Shift+LPM/PPM/ŚPM
    │   └── ShopCommand.java            # /shop [kategoria]
    ├── spawners                        # MODUŁ 2: Zaawansowane Spawnery
    │   ├── SpawnerModule.java          # Ładowanie spawners.yml i rejestracja GUI/listenerów
    │   ├── SpawnerTierDefinition.java  # Rekord tieru (opóźnienia, spawnCount, maxNearby, koszty)
    │   ├── SpawnerDataManager.java     # Operacje PersistentDataContainer (blok <-> ItemStack)
    │   ├── SpawnerUpgradeHolder.java   # InventoryHolder przypisany bezpośrednio do współrzędnych bloku
    │   ├── SpawnerGUI.java             # GUI ulepszeń (Shift + PPM na spawner)
    │   ├── SpawnerListener.java        # Obsługa Silk Touch, stawiania, kliknięcia i ochrony GUI
    │   └── SpawnerCommand.java         # /spawner give <gracz> <mob> [tier]
    ├── skyblock                        # MODUŁY 3–5: Kompletny Silnik SkyBlocka
    │   ├── SkyBlockModule.java         # Koordynator podsystemów wysp, poziomów, ulepszeń i misji
    │   ├── island
    │   │   ├── Island.java             # Model wyspy (ID, właściciel, lokalizacja, spawn, warpy, granica)
    │   │   ├── IslandManager.java      # Cache gridu wysp, przydział współrzędnych, tworzenie ze schematów
    │   │   ├── IslandRole.java         # Role: VISITOR, TRUSTED (tymczasowa do wylogowania), MEMBER, CO_OWNER, OWNER
    │   │   ├── IslandPermission.java   # Granularne flagi (BREAK, PLACE, CONTAINERS, INTERACT, INVITE, WARP)
    │   │   └── BorderColor.java        # Kolor granicy WorldBorder (BLUE, GREEN, RED)
    │   ├── level
    │   │   ├── BlockValueManager.java  # Ładowanie punktacji bloków z blocks-value.yml
    │   │   ├── IslandLevelCalculator.java # Asynchroniczne skanowanie przez ChunkSnapshot (zero lagów ticku)
    │   │   └── IslandTopCache.java     # Ranking TOP 10 na żywo dla /is top
    │   ├── upgrades
    │   │   ├── IslandUpgradeType.java  # WORLD_BORDER, SPAWNER_LIMIT, CROP_GROWTH, MEMBER_LIMIT
    │   │   └── IslandUpgradeManager.java # Koszty poziomów + modyfikatory WorldBorder i randomTick
    │   ├── quests
    │   │   ├── QuestStage.java         # Etapy 1–5 (po 7 misji w każdym)
    │   │   ├── QuestDefinition.java    # Wymagania (typ, cel, ilość, nagrody) z quests.yml
    │   │   ├── QuestProgress.java      # Współdzielony stan zadań wyspy (mapa celów + flaga ukończenia)
    │   │   └── QuestManager.java       # Ładowanie quests.yml, inkrementacja i odbiór nagród
    │   ├── gui
    │   │   ├── IslandPanelGUI.java     # Główne menu wyspy, ustawienia prywatności i koloru WorldBorder
    │   │   ├── IslandRolesGUI.java     # Menu uprawnień ról (zielona/czerwona szyba statusu)
    │   │   ├── IslandWarpsGUI.java     # Publiczne warpy graczy
    │   │   ├── IslandUpgradesGUI.java  # Ulepszenia wyspy za walutę EconomyManager
    │   │   └── QuestGUI.java           # Menu misji (zielona/żółta z progressbarem/czerwona szyba)
    │   ├── listeners
    │   │   ├── IslandProtectionListener.java # Ochrona bloków, skrzyń, granicy WorldBorder, wyczyszczenie TRUSTED przy wyjściu
    │   │   ├── SpawnerLimitListener.java     # Egzekwowanie limitu spawnerów na wyspie i prędkości wzrostu roślin
    │   │   └── QuestProgressListener.java    # Zoptymalizowane zliczanie (BlockBreak, EntityDeath, Fish, Craft, Trade, Brew)
    │   └── commands
    │       └── IslandCommand.java      # /is <create|home|sethome|warp|setwarp|invite|trust|kick|top|panel|delete|transfer>
    ├── magicitems                      # (Istniejący moduł: magiczne przedmioty + zdolność duch)
    ├── armor                           # (Istniejący moduł: zestawy Górnik/Handlarz/Wojownik + Buty lotnika)
    └── oreregions                      # (Istniejący moduł: odnawialne regiony rud)
```

---

## 2. Schemat bazy danych (SQLite WAL / MySQL)

Wszystkie tabele używają standardowego SQL zgodnego zarówno z SQLite (z trybem `PRAGMA journal_mode = WAL` dla równoległego odczytu/zapisu bez blokowania głównego wątku), jak i MySQL (`InnoDB`, `utf8mb4`).

```sql
-- 1. Konta ekonomii graczy (Moduł 1)
CREATE TABLE IF NOT EXISTS economy_accounts (
    uuid       VARCHAR(36) PRIMARY KEY,
    balance    DOUBLE      NOT NULL DEFAULT 0.0,
    updated_at BIGINT      NOT NULL
);

-- 2. Wyspy SkyBlock (Moduły 3 i 4)
CREATE TABLE IF NOT EXISTS skyblock_islands (
    island_id      VARCHAR(36) PRIMARY KEY,
    owner_uuid     VARCHAR(36) NOT NULL,
    world_name     VARCHAR(64) NOT NULL,
    center_x       INT         NOT NULL,
    center_z       INT         NOT NULL,
    home_x         DOUBLE      NOT NULL,
    home_y         DOUBLE      NOT NULL,
    home_z         DOUBLE      NOT NULL,
    home_yaw       FLOAT       NOT NULL DEFAULT 0.0,
    home_pitch     FLOAT       NOT NULL DEFAULT 0.0,
    is_public      BOOLEAN     NOT NULL DEFAULT FALSE,
    border_color   VARCHAR(16) NOT NULL DEFAULT 'BLUE',
    island_level   DOUBLE      NOT NULL DEFAULT 0.0,
    bank_balance   DOUBLE      NOT NULL DEFAULT 0.0,
    created_at     BIGINT      NOT NULL
);

-- 3. Stałe członkostwa wysp (Member / Co-Owner; TRUSTED trzymane wyłącznie w pamięci RAM do wylogowania)
CREATE TABLE IF NOT EXISTS skyblock_members (
    island_id  VARCHAR(36) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    role       VARCHAR(16) NOT NULL,
    joined_at  BIGINT      NOT NULL,
    PRIMARY KEY (island_id, player_uuid),
    FOREIGN KEY (island_id) REFERENCES skyblock_islands(island_id) ON DELETE CASCADE
);

-- 4. Uprawnienia ról per wyspa (Moduł 3 - Role GUI)
CREATE TABLE IF NOT EXISTS skyblock_role_permissions (
    island_id   VARCHAR(36) NOT NULL,
    role        VARCHAR(16) NOT NULL,
    permission  VARCHAR(32) NOT NULL,
    allowed     BOOLEAN     NOT NULL,
    PRIMARY KEY (island_id, role, permission),
    FOREIGN KEY (island_id) REFERENCES skyblock_islands(island_id) ON DELETE CASCADE
);

-- 5. Poziomy ulepszeń wyspy (Moduł 4)
CREATE TABLE IF NOT EXISTS skyblock_upgrades (
    island_id     VARCHAR(36) NOT NULL,
    upgrade_type  VARCHAR(32) NOT NULL,
    tier          INT         NOT NULL DEFAULT 1,
    PRIMARY KEY (island_id, upgrade_type),
    FOREIGN KEY (island_id) REFERENCES skyblock_islands(island_id) ON DELETE CASCADE
);

-- 6. Publiczne warpy wysp (Moduł 3)
CREATE TABLE IF NOT EXISTS skyblock_warps (
    island_id  VARCHAR(36) NOT NULL,
    warp_name  VARCHAR(32) NOT NULL,
    world_name VARCHAR(64) NOT NULL,
    x          DOUBLE      NOT NULL,
    y          DOUBLE      NOT NULL,
    z          DOUBLE      NOT NULL,
    yaw        FLOAT       NOT NULL DEFAULT 0.0,
    pitch      FLOAT       NOT NULL DEFAULT 0.0,
    icon       VARCHAR(48) NOT NULL DEFAULT 'ENDER_PEARL',
    PRIMARY KEY (island_id, warp_name),
    FOREIGN KEY (island_id) REFERENCES skyblock_islands(island_id) ON DELETE CASCADE
);

-- 7. Postęp współdzielonych misji wyspy (Moduł 5)
CREATE TABLE IF NOT EXISTS skyblock_quests (
    island_id     VARCHAR(36) NOT NULL,
    stage         INT         NOT NULL,
    quest_id      VARCHAR(48) NOT NULL,
    progress_json TEXT        NOT NULL,
    completed     BOOLEAN     NOT NULL DEFAULT FALSE,
    completed_at  BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (island_id, quest_id),
    FOREIGN KEY (island_id) REFERENCES skyblock_islands(island_id) ON DELETE CASCADE
);
```

---

## 3. Rekomendowana kolejność wdrożenia (6 faz)

| Faza | Zakres | Uzasadnienie zależności | Status |
| :--- | :--- | :--- | :--- |
| **Faza 1: Fundament Danych i PDC** | `DatabaseManager`, `EconomyManager`, `spawners.yml`, `quests.yml`, `shops/blocks.yml`, `SpawnerDataManager`, `PLAN.md` | Wspólna warstwa persystencji i ekonomia są wymagane przez sklepy, ulepszenia spawnerów, ulepszenia wyspy i nagrody misji. | **Wdrożona [x]** |
| **Faza 2: Komendy Ekonomii i Sklep GUI** | `DatabaseManager` w `AradiumPlugin`, `EconomyModule`, `/money`, `/pay`, `/eco`, `ShopManager`, `ShopCategory`, `ShopGUI`, `ShopListener`, `/shop` | Daje graczom pełną pętlę zarabiania i wydawania waluty jeszcze przed uruchomieniem SkyBlocka. | **Wdrożona [x]** |
| **Faza 3: Ulepszane Spawnery GUI + Silk Touch** | `SpawnerModule`, `SpawnerUpgradeHolder`, `SpawnerGUI`, `SpawnerListener`, `/spawner` | Korzysta z `EconomyManager` i gotowego `SpawnerDataManager` (PDC). | **Wdrożona [x]** |
| **Faza 4: SkyBlock Core — Wyspy, Role i WorldBorder** | `SkyBlockModule`, `IslandManager`, `IslandSchematicGUI`, `IslandPanelGUI`, `IslandRolesGUI`, `IslandWarpsGUI`, `IslandProtectionListener`, `IslandCommand` (`/is`), schematy wysp, warpy, prywatność i kolory `WorldBorder`, sesyjne `TRUSTED` | Rdzeń świata wysp, na którym opierają się limity spawnerów, poziomy i misje. | **Wdrożona [x]** |
| **Faza 5: Poziomy Wyspy i Ulepszenia** | `blocks-value.yml`, `BlockValueManager`, `IslandLevelCalculator` (async `ChunkSnapshot`), `IslandTopCache`, `/is level`, `/is top` (`IslandTopGUI`), `IslandUpgradeManager`, `IslandUpgradesGUI`, `SpawnerLimitListener` (limity spawnerów i wzrost roślin) | Rozszerza już istniejący obiekt `Island` o przeliczanie wartości bloków i modyfikatory rozgrywki. | **Wdrożona [x]** |
| **Faza 6: System Misji Wysp (5 Etapów x 7 Zadań)** | Pełne `quests.yml` (Etapy 1–5), `QuestManager`, `QuestGUI` (zielona/żółta/czerwona szyba + progressbar), zoptymalizowane listenery | Integruje wszystkie wcześniejsze moduły: wykopki, zbiory, moby, ekonomię i poziom wyspy. | Następna (Oczekuje) |

---

## 4. Propozycje rozwiązań architektonicznych i optymalizacyjnych (dla setek graczy)

1. **Ekonomia typu Write-Behind (zero zapytań SQL na głównym wątku):**
   - Wszystkie operacje `getMoney`, `hasMoney`, `addMoney`, `takeMoney` wykonują się na `ConcurrentHashMap<UUID, Double>` w czasie $O(1)$.
   - Zmodyfikowane UUID trafiają do zbioru `dirtyAccounts`, który okresowy task asynchroniczny zrzuca wsadowo (`PreparedStatement#executeBatch`) do SQLite WAL (lub opcjonalnego YAML). Przy zamykaniu serwera (`onDisable`) następuje synchroniczne `flushDirtyAccounts()` gwarantujące brak utraty sald.
2. **Bezpieczne GUI bez duplikowania przedmiotów (Anti-Dupe):**
   - Każde GUI posiada własną klasę implementującą `InventoryHolder` (`ShopInventoryHolder`, `SpawnerUpgradeHolder`, `QuestInventoryHolder`, `IslandPanelHolder`).
   - W `InventoryClickEvent` oraz `InventoryDragEvent`: jeśli `event.getView().getTopInventory().getHolder()` jest jedną z naszych klas, anulujemy zdarzenie natychmiastowo na samym początku (`event.setCancelled(true)`), blokując m.in. Shift-Click z dolnego ekwipunku, klawisze `1–9`, podwójne kliknięcie (`COLLECT_TO_CURSOR`) i przeciąganie.
   - `SpawnerUpgradeHolder` przechowuje referencję do współrzędnych `(world, x, y, z)` i zamyka GUI, jeśli blok spawnera zostanie w międzyczasie zniszczony (ochrona przed wyścigiem dwóch graczy lub zniszczeniem spawnera z otwartym menu).
3. **Bezpieczna aktualizacja `CreatureSpawner` (kolejność `min/max delay`):**
   - Paper/Spigot wyrzuca `IllegalArgumentException`, jeśli ustawisz `minSpawnDelay` większy od aktualnego `maxSpawnDelay` lub `maxSpawnDelay` mniejszy od aktualnego `minSpawnDelay`.
   - Rozwiązanie w `SpawnerDataManager`: jeśli nowy `minSpawnDelay > obecny maxSpawnDelay`, najpierw podnosimy `maxSpawnDelay`, a potem ustawiamy `minSpawnDelay`; w przeciwnym razie najpierw ustawiamy `minSpawnDelay`, a potem `maxSpawnDelay`.
4. **Asynchroniczne liczenie poziomu wyspy (`ChunkSnapshot`):**
   - Na głównym wątku pobieramy wyłącznie lekkie migawki chunków (`chunk.getChunkSnapshot(false, false, false)`) leżących w obrębie aktualnego `WorldBorder` wyspy.
   - Całe iterowanie po współrzędnych `(x, y, z)` i sumowanie wartości z `blocks-value.yml` wykonuje się w puli wątków roboczych (`CompletableFuture.supplyAsync(...)`). Wynik wraca na główny wątek i odświeża cache `/is top`.
5. **Optymalizacja spawnerów i wzrostu roślin (Moduł 4):**
   - Zamiast ciężkich skanów wyspy co tick, wzrost roślin przyspieszamy zdarzeniowo w `BlockGrowEvent` (losowa szansa dodatkowego postępu `Ageable#setAge` proporcjonalna do poziomu ulepszenia wyspy) oraz opcjonalnie `StructureGrowEvent`.
   - Limit spawnerów weryfikujemy w czasie $O(1)$ w `BlockPlaceEvent` / `BlockBreakEvent` (przechowując licznik postawionych spawnerów w metadanych obiektu `Island`, korygowany przy przeliczeniu `IslandLevel`).
6. **Współdzielone misje (`QuestProgress`) z filtrowaniem zdarzeń:**
   - W `QuestProgressListener` pierwszym warunkiem przed jakimkolwiek dostępem do bazy czy wyspy jest szybkie sprawdzenie typu zdarzenia/materiału przeciwko aktywnemu zbiorowi celów bieżącego etapu wyspy (early return przy `BlockBreakEvent` bloków niebędących celem żadnego zadania).
   - Tymczasowe nadania roli `TRUSTED` trzymamy wyłącznie w pamięci (`Map<UUID, Set<UUID>>`), czyszczonej automatycznie przy `PlayerQuitEvent`.
