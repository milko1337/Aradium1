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

- **Skala projektu:** `73` główne klasy Java w `src/main/java` (`0` ostrzeżeń `javac`)
  oraz `9` plików zasobów w `src/main/resources`

- **Testy automatyczne:** `15/15` testów JUnit 5 (`org.junit.jupiter:junit-jupiter:5.11.4`)
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
    `/money`, `/pay`, `/eco`, `/shop`, `/spawner`, `/island` (`/is`, `/wyspa`).

- **`src/main/resources/config.yml`**
  - Flagi modułów: `economy`, `shop`, `spawners`, `skyblock`, `magic_items`,
    `ore_regions`, `armor`.
  - Konfiguracja `database`, `economy`, `skyblock`, `ore_regions`, komunikaty
    `messages` oraz definicje 7 magicznych przedmiotów (`magic_items`).

- **`src/main/resources/armor.yml`**
  - Zestawy zbroi: `gornik` (T1–T5), `handlarz` (T1–T3), `wojownik` (T1–T5).
  - Specjalny element `special_armor.buty_lotnika` (niezniszczalne buty latania).

- **`src/main/resources/spawners.yml`**
  - Definicje 3 tierów ulepszeń spawnerów: koszty w `$` i przedmiotach,
    `min/max-spawn-delay`, `spawn-count`, `max-nearby-entities`, `required-player-range`.

- **`src/main/resources/blocks-value.yml`**
  - Punktacja bloków używana przy asynchronicznym liczeniu poziomu wyspy (`/is level`).

- **`src/main/resources/quests.yml`**
  - Pełne 5 etapów po 7 współdzielonych misji (`35` zadań łącznie), konfiguracja
    paska postępu, nagrody pieniężne/przedmiotowe oraz bonusy za etapy.

- **`src/main/resources/shops/main.yml` i `shops/blocks.yml`**
  - Główne menu `/shop` kierujące do kategorii oraz definicje pozycji kategorii
    `blocks` (`buy-price`, `sell-price`, blokada przez `-1`, przycisk powrotu).

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
  - `ShopModule.java` i `ShopManager.java`: kopiowanie `shops/*.yml`, parsowanie
    kategorii i zamykanie otwartych GUI przy `disable`.
  - `ShopCategory.java`, `ShopItem.java`, `ShopInventoryHolder.java`: modele kategorii
    i pozycji sklepu oraz dedykowany `InventoryHolder` (Anti-Dupe).
  - `ShopGUI.java`, `ShopListener.java`, `ShopCommand.java`: automatyczne lore
    (`LPM`, `Shift+LPM`, `PPM`, `ŚPM`), pełny Anti-Dupe, ochrona przed sprzedażą
    itemów z metadanymi/PDC oraz komenda `/shop [kategoria]` (`/sklep`).

---

### 1.4. Moduł 2: Zaawansowane Spawnery (`spawners`)

- **`SpawnerModule.java` i `SpawnerTierDefinition.java`**
  - Ładowanie `spawners.yml`, inicjalizacja GUI i rekordy parametrów tierów.

- **`SpawnerDataManager.java`**
  - Zapis/odczyt tieru i `EntityType` w PDC bloku `CreatureSpawner` i przedmiotu,
    bezpieczna kolejność ustawiania `minSpawnDelay` / `maxSpawnDelay`.

- **`SpawnerUpgradeHolder.java` i `SpawnerGUI.java`**
  - Menu ulepszeń powiązane ze współrzędnymi konkretnego spawnera (Anti-Dupe),
    pobierające walutę z `EconomyManager` oraz czyste itemy z ekwipunku.

- **`SpawnerListener.java` i `SpawnerCommand.java`**
  - Otwieranie menu przez `Shift + PPM`, wydobycie Silk Touch bez farmy XP,
    zachowanie tieru po postawieniu, ochrona przed wybuchami oraz `/spawner give`.

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

### 1.7. Testy automatyczne (`AradiumAuditTest.java` — 15/15 zaliczonych)

1. `cuboidRegionNormalizesMinMaxAndChecksBoundaries` — granice regionów rud.

2. `cooldownCalculationAndIdNormalizationWorkCorrectly` — cooldowny i ID itemów.

3. `blockKeyAndPendingMaterialResolutionAreSafe` — `BlockKey` i `pending_regens`.

4. `ghostFlySpeedConversionAndDefinitionFlagWorkCorrectly` — zdolność `duch`.

5. `armorFullSetMatchingRequiresFourMatchingPiecesAndTiers` — matching 4/4 zbroi.

6. `armorMaxHealthBonusAndYamlStructureAreValid` — `armor.yml` i `MAX_HEALTH`.

7. `armorLoreFormattingUpdatesEquippedCountAndClampsBounds` — licznik `(%equipped%/4)`.

8. `aviatorBootsGameModeAndSingleFallProtectionWorkCorrectly` — "Buty lotnika".

9. `economyManagerOperationsAndYamlPersistenceWorkCorrectly` — salda i zapis YAML.

10. `spawnerQuestsAndShopYamlConfigsAreValid` — walidacja `spawners.yml`, `quests.yml`, `shops/blocks.yml`.

11. `shopManagerAndAutoLoreGenerationWorkCorrectly` — formatowanie kwot i lore sklepu.

12. `spawnerModuleParsingAndGuiLoreWorkCorrectly` — parsowanie tierów spawnerów.

13. `skyblockIslandsRolesPermissionsAndGridWorkCorrectly` — siatka wysp, role i uprawnienia.

14. `skyblockLevelsTopAndUpgradesWorkCorrectly` — `blocks-value.yml`, `/is top`, ulepszenia.

15. `skyblockQuestsAllFiveStagesProgressAndGuiWorkCorrectly` — 5 etapów misji, JSON,
    paski postępu i cele `BLOCK_AND_CRAFT`.

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
