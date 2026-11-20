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

- **Skala projektu:** `93` głównych klas Java w `src/main/java` (`0` ostrzeżeń `javac`)
  oraz `27` plików zasobów w `src/main/resources`

- **Testy automatyczne:** `21/21` testów JUnit 5 (`org.junit.jupiter:junit-jupiter:5.11.4`)
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
  - Rejestracja komend: `/aradium <reload|modules|enable|disable|toggle>`, `/customitem`
    (`/citem`, `/specjalnyitem`), `/recipes` (`/receptury`, `/kraftingi`), `/magicitem`,
    `/magicarmor`, `/oreregion`, `/money`, `/pay`, `/eco`, `/shop`, `/sellall` (`/sprzedajwszystko`),
    `/sellgui` (`/sprzedaj`), `/spawner`, `/island` (`/is`, `/wyspa`).

- **`src/main/resources/config.yml`**
  - Flagi modułów: `magic_items`, `ore_regions`, `armor`, `economy`, `shop`, `spawners`,
    `skyblock`, `custom_items`, `moderation`, `ranks`.
  - Konfiguracja `database`, `economy`, `skyblock`, `ore_regions`, komunikaty
    `messages` oraz definicje 7 magicznych przedmiotów (`magic_items`).

- **`src/main/resources/armor.yml`**
  - Zestawy zbroi: `gornik` (T1–T5), `handlarz` (T1–T3), `wojownik` (T1–T5).
  - Specjalny element `special_armor.buty_lotnika` (niezniszczalne buty latania).

- **`src/main/resources/spawners.yml`**
  - Definicje `5` niezależnych ścieżek ulepszeń (`upgrades.delay` `1–5`, `upgrades.amount` `1–5`,
    `upgrades.range` `1–4`, `upgrades.light-immunity` `0–1`, `upgrades.ground-immunity` `0–1`),
    cennik wszystkich `85` mobów z MC (`mobs:` poza bossami `WITHER`, `ENDER_DRAGON`, `WARDEN`)
    z bazową świnią (`PIG`) oraz zachowana sekcja kompatybilności `tiers`.

- **`src/main/resources/blocks-value.yml`**
  - Punktacja bloków używana przy asynchronicznym liczeniu poziomu wyspy (`/is level`).

- **`src/main/resources/cobble-generator.yml`**
  - Tabela wag dropów (`74%->25%` bruk, `COAL_ORE`–`ANCIENT_DEBRIS`) i nazw surowców
    dla `5` poziomów podstawowego generatora lawy i wody (`COBBLE_GENERATOR_YIELD`).

- **`src/main/resources/custom-items.yml`**
  - Konfiguracja `4` stawianych generatorów (`cobble_generator`, `ore_generator_t1..t3`),
    `Pochodni Anty-Mob` (`anti_mob_torch`, promień `32` kratek) oraz `9` mechanicznych zwojów/run
    ulepszeń (`zwoj_kopanie_2x2`, `zwoj_kopanie_3x3`, `zwoj_hutnik`, `zwoj_drwal`,
    `zwoj_zniwiarz_3x3`, `zwoj_wampiryzm`, `zwoj_piorun`, `zwoj_double_jump`, `runa_tieru_zbroi`).

- **`src/main/resources/recipes.yml`**
  - Wydzielona konfiguracja `16` własnych receptur craftingu (`GENERATORS`, `SCROLLS`,
    `ARMOR`, `MAGIC`, `MISC`) z obsługą zapisu graficznego (`slots.0..8` i pełnych `ItemStack`),
    edycji i usuwania w kreatorze GUI (`/receptury admin`).

- **`src/main/resources/quests.yml`**
  - Pełne 7 etapów (`86` współdzielonych zadań wyspy zgodnie z `QUESTY_LISTA.md`),
    konfiguracja paska postępu, nagrody pieniężne/przedmiotowe oraz bonusy za etapy.

- **`src/main/resources/shops/*.yml` (`17` plików kategorii i podkategorii)**
  - Główne menu `/shop` (`main.yml` — 10 kategorii głównych), podkategorie budowania
    (`building`, `blocks`, `wood`, `natural`, `decorative_blocks`), kategorie tematyczne
    (`ores`, `farming`, `food`, `combat`, `redstone`, `magic`, `mobdrops`, `decoration`,
    `music`, `storage`, `misc`) z wielostronicowymi sekcjami `pages:`, pozycjami `custom-item:`
    w `magic.yml` i `misc.yml` oraz zbalansowanymi cenami bez arbitrażu pod SkyBlock.

- **`src/main/resources/regions.yml`**
  - Domyślny szkielet `regions: {}` dla stref odnawialnych rud.

---

### 1.2. Rdzeń pluginu, baza danych i narzędzia (`core` & `utils`)

- **`AradiumPlugin.java`** (`pl.nauka.aradium`)
  - Inicjalizacja `DatabaseManager`, rejestracja `8` modułów w `ModuleManager`
    (w tym `CustomItemModule`) oraz obsługa `onEnable()` i bezpiecznego zamykania w `onDisable()`.

- **`core/PluginModule.java` i `core/AbstractModule.java`**
  - Kontrakt cyklu życia modułu (`getId`, `enable`, `disable`, `reload`, `isEnabled`)
    z automatycznym wyrejestrowaniem listenerów (`HandlerList.unregisterAll`) i komend.

- **`core/ModuleManager.java` i `core/AradiumCommand.java`**
  - Zarządzanie stanem `modules.<id>` (w tym dynamiczne `setModuleEnabled`),
    uruchamianie i wyłączanie w odwrotnej kolejności oraz komenda
    `/aradium <reload|modules|enable|disable|toggle> [moduł]`.

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

- **`upgrades/` (`IslandUpgradeType`, `IslandUpgradeManager`, `CobbleGeneratorManager`)**
  - Ulepszenia wyspy za `$`: `WORLD_BORDER`, `SPAWNER_LIMIT`, `CROP_GROWTH`,
    `DOUBLE_CROP_DROP`, `MOB_DROP_BONUS`, `COBBLE_GENERATOR_YIELD` (mnożnik `1x–5x`
    i losowanie rud z `cobble-generator.yml`) oraz `MEMBER_LIMIT` z zapisem w `skyblock_upgrades`.

- **`quests/` (`QuestType`, `QuestDefinition`, `QuestStage`, `QuestProgress`, `QuestManager`)**
  - Obsługa 7 etapów misji wyspy (`86` zadań według rozpiski w `QUESTY_LISTA.md`,
    w tym `SPAWNER_MOB_UNLOCK`, `ISLAND_UPGRADE`, `EQUIP_ARMOR`, `SPAWNER_UPGRADE` i `BREED`),
    serializacja JSON, paski postępu `buildProgressBar`, kontrola odblokowań etapów i wypłata nagród.

- **`gui/` (`SkyBlockGuiHolder`, `IslandSchematicGUI`, `IslandPanelGUI`, `IslandRolesGUI`, `IslandWarpsGUI`, `IslandUpgradesGUI`, `IslandTopGUI`, `QuestGUI`)**
  - Komplet menu wyspy (w tym `IslandUpgradesGUI` z 7 symetrycznymi ikonami ulepszeń
    oraz `QuestGUI` o rozmiarze `45` slotów z zakładkami etapów `1–7` w formie surowców na samej górze
    `1–7`, przerwą z szarego szkła `9–17`, dwoma rzędami tematycznych misji `19–25` i `28–34`,
    powrotem na slocie `40` i paskiem postępu) chronionych przez `SkyBlockGuiHolder`.

- **`listeners/` (`IslandProtectionListener`, `SpawnerLimitListener`, `QuestProgressListener`)**
  - Ochrona wyspy, limit spawnerów, przyspieszony wzrost roślin (`BlockGrowEvent`),
    szansa na podwójne plony z dojrzałych upraw (`BlockDropItemEvent`), bonus XP/łupu z mobów
    (`EntityDeathEvent` z filtrem Anti-Dupe), generator surowców i mnożnik wydajności (`BlockFormEvent`
    + `BlockBreakEvent`), czyszczenie `TRUSTED` przy wyjściu oraz zliczanie wszystkich `16` typów celów misji
    (w tym `EntityBreedEvent`, `SpawnerGUI`, `IslandUpgradeManager` oraz `ArmorModule`).

- **`commands/IslandCommand.java`**
  - Obsługa `/is`: `create`, `home`, `sethome`, `panel`, `quests`, `level`, `top`,
    `upgrades`, `roles`, `privacy`, `border`, `warp`, `setwarp`, `delwarp`,
    `invite`, `join`, `kick`, `leave`, `trust`, `untrust`, `transfer`, `delete`.

---

### 1.6. Moduły RPG, Przedmiotów Specjalnych i Świata (`magicitems`, `armor`, `oreregions`, `customitems`)

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

- **`customitems/` (`CustomItemModule`, `CustomItemListener`, `CustomItemCommand`, `GeneratorDefinition`, `AntiMobTorchDefinition`, `UpgradeScrollDefinition`, `CustomRecipeManager`, `CustomRecipeDefinition`, `CustomRecipeGUI`, `CustomRecipeGuiHolder`, `CustomRecipeCommand`, `RecipeDefinition`)**
  - Stawiane generatory bloków (`cobble_generator`, `ore_generator_t1..t3`) generujące
    surowce `1` kratkę wyżej z trwałym zapisem w `custom_blocks_data.yml` i ochroną
    przed tłokami/wybuchami, `Pochodnia Anty-Mob` (`anti_mob_torch`, promień `32` kratek
    blokujący naturalny spawn potworów bez blokowania spawnerów), system własnych receptur
    w `recipes.yml` z kreatorem/edytorem GUI Admina (`/receptury admin`) i przeglądarką
    kategorii dla graczy (`/receptury`), oraz `9` nietypowych ulepszeń mechanicznych
    (`Kopanie 2x2`, `Kopanie 3x3`, `Hutnik Auto-Smelt`, `Drwal`, `Żniwiarz 3x3`, `Wampiryzm 1/3 HP`,
    `Uderzenie Pioruna`, `Podwójny Skok Double Jump`, `Awans Tieru Zbroi`) zapisywanych w PDC
    i nakładanych przez Drag & Drop bądź `Off-Hand + Shift + PPM`.

---

### 1.7. Testy automatyczne (`AradiumAuditTest.java` — 21/21 zaliczonych)

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

14. `skyblockLevelsTopAndUpgradesWorkCorrectly` — `blocks-value.yml`, `/is top` oraz wszystkie
    `7` ulepszeń wyspy (`WORLD_BORDER`, `SPAWNER_LIMIT`, `CROP_GROWTH`, `MEMBER_LIMIT`,
    `COBBLE_GENERATOR_YIELD`, `DOUBLE_CROP_DROP`, `MOB_DROP_BONUS`).

15. `skyblockQuestsAllFiveStagesProgressAndGuiWorkCorrectly` — 7 etapów misji (`86` zadań
    z `QUESTY_LISTA.md`, w tym `SPAWNER_MOB_UNLOCK`, `ISLAND_UPGRADE`, `EQUIP_ARMOR`,
    `SPAWNER_UPGRADE` i `BREED`), JSON, paski postępu, układ `QuestGUI` (etapy `1–7`,
    przerwa `9–17`, misje `19–25`/`28–34`) i cele `BLOCK_AND_CRAFT`.

16. `allShopCategoriesLoadCleanlyWithValidMaterialsAndPagination` — walidacja wszystkich 17 plików
    `shops/*.yml`, materiałów Paper `26.3`, powiązań `parent-shop`/`target-shop`, indeksowania
    najlepszych cen sprzedaży oraz automatycznego stronicowania.

17. `spawnerFourUpgradeTracksAndAllNonBossMobsWorkCorrectly` — walidacja `5` ścieżek ulepszeń
    (`DELAY`, `AMOUNT`, `RANGE`, `LIGHT_IMMUNITY`, `GROUND_IMMUNITY`) w `spawners.yml`,
    kompletu `85` mobów z wykluczeniem bossów (`WITHER`, `ENDER_DRAGON`, `WARDEN`),
    domyślnego stanu `PIG`, serializacji PDC, reguły światła `SpawnRule(0, 15, 0, 15)` i lore GUI.

18. `cobbleGeneratorUpgradeAndAntiDupeRulesWorkCorrectly` — walidacja `cobble-generator.yml`
    (`5` tierów, wagi sumujące się do `100%`, losowanie rud `COAL_ORE`–`ANCIENT_DEBRIS`,
    synchronizacja z `IslandUpgradeManager`), obliczeń mnożnika dropu `1x–5x`
    oraz blokady duplikacji dla ręcznie postawionych bloków i kilofów z `Silk Touch`.

19. `doubleCropAndMobDropBonusUpgradesWorkCorrectly` — weryfikacja szans procentowych,
    przeliczania bonusu XP z mobów oraz filtra Anti-Dupe wykluczającego ekwipunek,
    totemy i niestandardowe przedmioty.

20. `customItemsRecipesAntiMobTorchScrollsModuleTogglesAndShopBalanceWorkCorrectly` — walidacja
    `custom-items.yml` (`4` generatory, `Pochodnia Anty-Mob` `32` kratki, `9` mechanicznych zwojów/run),
    ochrony przed spawnowaniem bez blokowania spawnerów, reguł ulepszeń mechanicznych i wykluczeń
    (`2x2` vs `3x3`), przełączania modułów w `ModuleManager` oraz braku arbitrażu cenowego we wszystkich `17` kategoriach `/shop`.

21. `recipesYamlGuiEditorMatrixTrimmingAndMechanicalUpgradesWorkCorrectly` — walidacja wydzielonego
    `recipes.yml` (`16` receptur w `5` kategoriach: `GENERATORS`, `SCROLLS`, `ARMOR`, `MAGIC`, `MISC`),
    przycinania kształtów `2x2`/`3x3`, slotów kreatora Admin GUI (`/receptury admin`), przełączania
    i usuwania receptur oraz obliczeń mechanik (`2x2`/`3x3`, `Hutnik Auto-Smelt`, `Wampiryzm`,
    `Żniwiarz 3x3`, `Double Jump` oraz serializacji PDC zdolności).

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

### 2026-11-21 — Nietypowe ulepszenia mechaniczne (`ULEPSZENIA_I_RECEPTURY.md`) oraz system `recipes.yml` + GUI Admina i Gracza (`2.0.0-SNAPSHOT`)

- Utworzono dokument [`ULEPSZENIA_I_RECEPTURY.md`](ULEPSZENIA_I_RECEPTURY.md) z katalogiem
  `100` nietypowych ulepszeń (`po 10` na każdy z `10` elementów ekwipunku) i opisem systemu receptur.

- Zastąpiono zwoje zwykłych zaklęć `9` mechanicznymi zwojami/runami zapisywanymi w PDC:
  `Kopanie 2x2`, `Kopanie 3x3`, `Hutnik (Auto-Smelt)`, `Drwal (TreeCapitator)`,
  `Żniwiarz 3x3`, `Wampiryzm (1/3 HP)`, `Uderzenie Pioruna`, `Podwójny Skok (Double Jump)`
  oraz `Awans Tieru Zbroi` (zaktualizowano `shops/magic.yml`).

- Wydzielono wszystkie własne receptury do `recipes.yml` (`CustomRecipeManager`, `CustomRecipeDefinition`)
  oraz dodano interaktywny kreator/edytor GUI dla administratorów (`/receptury admin`, bezpieczne
  przechowywanie wirtualnych szablonów bez ryzyka duplikacji) i przeglądarkę kategorii dla graczy
  (`/receptury`, `CustomRecipeGUI`, `CustomRecipeCommand`, `21/21` testów w `AradiumAuditTest`).

### 2026-11-21 — Etap 6: Przedmioty specjalne, receptury, zwoje/runy, przełączanie modułów i balans sklepu (`2.0.0-SNAPSHOT`)

- Dodano moduł `custom_items` (`pl.nauka.aradium.modules.customitems`) oraz konfigurację
  `custom-items.yml`: stawiane generatory (`cobble_generator`, `ore_generator_t1..t3`)
  z trwałym zapisem w `custom_blocks_data.yml` i ochroną przed tłokami/wybuchami oraz
  `Pochodnię Anty-Mob` (`anti_mob_torch`, promień `32` kratek blokujący naturalny spawn
  wrogich mobów bez blokowania spawnerów).

- Wdrożono własne receptury `ShapedRecipe` dla wszystkich `11` nowych przedmiotów,
  komendę `/customitem give <gracz> <id> [ilość]` oraz `6` zwojów/run ulepszeń broni,
  narzędzi i zbroi (`zwoj_ostrosci`, `zwoj_wydajnosci`, `zwoj_fortuny`, `zwoj_ochrony`,
  `runa_niezniszczalnosci`, `runa_tieru_zbroi`) obsługiwanych przez Drag & Drop w
  ekwipunku i `Off-Hand + Shift + PPM`.

- Rozszerzono `ModuleManager`, `AradiumCommand` i `config.yml` o pełne wyłączanie i
  przełączanie modułów z konfiguracji oraz komend `/aradium <enable|disable|toggle> <moduł>`.

- Wyrównano ceny duplikujących się pozycji pomiędzy `shops/blocks.yml` i `shops/natural.yml`
  (eliminacja arbitrażu cenowego), dodano obsługę `custom-item:` w `ShopItem`/`ShopManager`/`ShopGUI`
  oraz podpięto nowe przedmioty do `shops/magic.yml` i `shops/misc.yml` (`20/20` testów w `AradiumAuditTest`).

### 2026-11-20 — Obniżenie wierszy misji w `QuestGUI` o jedną linię (`2.0.0-SNAPSHOT`)

- Przesunięto oba wiersze misji w `QuestGUI` oraz wszystkich `86` wpisów `slot:` w `quests.yml`
  o `+9` slotów (z `10–16`/`19–25` na `19–25`/`28–34`), pozostawiając przerwę z szarego szkła
  w rzędzie `2` (`9–17`) pomiędzy zakładkami etapów (`1–7`) a pierwszą linią misji, wraz z
  aktualizacją `QuestGUI#resolveQuestSlot` (`10–16 -> +9`) i testu #15 w `AradiumAuditTest`.

### 2026-11-20 — Pełna rozpiska 86 misji z `QUESTY_LISTA.md` (Etapy I–VII) (`2.0.0-SNAPSHOT`)

- Dodano `3` nowe typy celów w `QuestType`: `SPAWNER_MOB_UNLOCK` (`SpawnerGUI`), `ISLAND_UPGRADE`
  (`IslandUpgradeManager` / `Island#getTotalPurchasedUpgradeLevels()`) oraz `EQUIP_ARMOR`
  (`ArmorModule`: pełne zestawy `GORNIK_TIER_1`, `HANDLARZ_TIER_2` i `BUTY_LOTNIKA`)
  wraz ze zintegrowaną synchronizacją w `QuestManager#syncDynamicQuests`.

- Zaktualizowano `quests.yml` pod dokładny plan użytkownika z `QUESTY_LISTA.md` (`9 + 11 + 13 + 14 + 13 + 13 + 13 = 86`
  misji w 7 etapach) oraz zaktualizowano testy #10 i #15 w `AradiumAuditTest`.

### 2026-11-20 — Nowy układ `QuestGUI` i nowe misje (`Mistrz Hodowli`, `Budowniczy`, `Mistrz Ekonomii`, `Kolekcjoner Spawnerów`) (`2.0.0-SNAPSHOT`)

- Przesunięto zakładki Etapów I–VII na samą górę `QuestGUI` (`sloty 1–7`), dwa rzędy misji
  etapu na `sloty 10–16` oraz `19–25`, przerwę z szarego szkła na `sloty 27–35` i dolną linię
  z przyciskiem powrotu na slocie `40`.

- Dodano nowe typy celów `QuestType.BREED` (`EntityBreedEvent` w `QuestProgressListener`)
  oraz `QuestType.SPAWNER_UPGRADE` (wykrywane przy ulepszeniu w `SpawnerGUI`), rozszerzając
  `quests.yml` do `56` misji w `7` etapach: `Mistrz Hodowli` na każdym etapie (z różnymi mobami),
  `Budowniczy` (punkty wyspy) na każdym etapie, `Mistrz Ekonomii` (posiadana gotówka)
  od Etapu III oraz `Kolekcjoner Spawnerów` na Etapach VI (`1` ulepszenie) i VII (`4` ulepszenia).

### 2026-11-20 — Odświeżenie `QuestGUI` (45 slotów, ikony surowców etapów i przedmiotów misji) (`2.0.0-SNAPSHOT`)

- Przebudowano `QuestGUI` na `45` slotów z pełną obramówką z szarego szkła
  (`GRAY_STAINED_GLASS_PANE`), przenosząc zakładki Etapów I–VII na 2. linię (`sloty 10–16`),
  przycisk powrotu na slot `40`, a wszystkie `49` misji na 4. linię (`sloty 28–34`).

- Dodano obsługę ikon etapów w `QuestStage` / `QuestManager` (`COAL`, `COPPER_INGOT`,
  `IRON_INGOT`, `GOLD_INGOT`, `DIAMOND`, `EMERALD`, `NETHERITE_INGOT`) oraz zamieniono
  kolorowe szyby misji na tematyczne przedmioty z `quests.yml` (np. `COD` dla łowienia ryb)
  wraz z efektem połysku dla wybranego etapu i ukończonych zadań.

### 2026-11-20 — Etap 5 (Punkt 6): Rozbudowa misji (`Etapy VI–VII`) oraz ulepszeń wysp (`DOUBLE_CROP_DROP`, `MOB_DROP_BONUS`) (`2.0.0-SNAPSHOT`)

- Dodano dwa nowe ulepszenia wysp w `IslandUpgradeType`, `Island`, `IslandUpgradeManager`
  i `config.yml`: `DOUBLE_CROP_DROP` (`0%–100%` szansy na 2x plon za `0$–55 000$`)
  oraz `MOB_DROP_BONUS` (`0%–100%` bonusu dropu i XP za `0$–75 000$`).

- Przebudowano `IslandUpgradesGUI` pod symetryczny układ `7` ulepszeń (`sloty 10–16`)
  oraz zaimplementowano w `SpawnerLimitListener` bezpieczne podwajanie plonów dojrzałych
  upraw (`BlockDropItemEvent`, `Ageable` MAX) i premię XP/łupów z mobów (`EntityDeathEvent`)
  z ochroną przed duplikacją ekwipunku, totemów i przedmiotów z metadanymi.

- Rozbudowano `quests.yml`, `QuestManager` i `QuestGUI` o **Etap VI (Potęga Przemysłu)**
  i **Etap VII (Imperium Aradium)** — łącznie `7` etapów po `7` zadań (`49` misji),
  zakładki na slotach `1–7` oraz zbalansowane nagrody pod najwyższe ulepszenia wyspy;
  dodano normalizację rud deep-slate miedzi/lazurytu/redstone i wsparcie buraków w `QuestProgressListener`.

- Zaktualizowano testy #14 i #15 oraz dodano test #19 (`doubleCropAndMobDropBonusUpgradesWorkCorrectly`),
  uzyskując `81` klas głównych bez ostrzeżeń kompilacji, `19/19` zaliczonych testów
  i wdrożono zmiany na serwer testowy.

### 2026-11-20 — Etap 4 (Punkt 11): Ulepszenie wyspy — generator surowców z lawy i wody (`cobble-generator.yml`) (`2.0.0-SNAPSHOT`)

- Wydzielono plik konfiguracyjny `cobble-generator.yml` (kopiowany automatycznie do folderu pluginu)
  z polskimi nazwami materiałów oraz wagami szans dla `5` poziomów generatora (`COBBLESTONE`,
  `COAL_ORE`, `COPPER_ORE`, `IRON_ORE`, `LAPIS_ORE`, `REDSTONE_ORE`, `GOLD_ORE`,
  `DIAMOND_ORE`, `EMERALD_ORE`, `ANCIENT_DEBRIS`).

- Utworzono `CobbleGeneratorManager`, dodano `IslandUpgradeType.COBBLE_GENERATOR_YIELD`,
  pole `cobbleGeneratorYield` w `Island` oraz synchronizację `syncCobbleGeneratorTiers`
  w `IslandUpgradeManager` i `SkyBlockModule`.

- Przebudowano `IslandUpgradesGUI` (`5` symetrycznych ikon na slotach `11–15` + rozpiska procentowa
  surowców dla aktualnego poziomu) oraz wdrożono w `SpawnerLimitListener` podmienianie bloku
  w `BlockFormEvent` na wylosowaną rudę i mnożenie naturalnych dropów (`block.getDrops`)
  z pełnym Anti-Dupe (czyszczenie przy `BlockPlaceEvent`, tłokach, eksplozjach,
  odładowaniu chunku i wyłączeniu bonusu dla `Silk Touch` / `CREATIVE`).

- Zaktualizowano test #14 i test JUnit 5 #18 (`cobbleGeneratorUpgradeAndAntiDupeRulesWorkCorrectly`),
  uzyskując `81` klas głównych bez ostrzeżeń kompilacji oraz `18/18` zaliczonych testów
  i wdrożono zmiany na serwer testowy.

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
