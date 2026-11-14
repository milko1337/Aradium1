# Audyt techniczny pluginu Aradium

> **Zasada utrzymania dokumentu:** Ten plik (`AUDIT.md`) stanowi główne źródło prawdy o stanie technicznym, aktywnych błędach, długu technologicznym i architekturze pluginu **Aradium**. Lista problemów w sekcji 2 zawiera wyłącznie pozycje otwarte (`[ ]`) — po naprawieniu i zweryfikowaniu błędu wpis jest usuwany z listy aktywnych problemów, a zmiana zostaje zarejestrowana w sekcji **4. Historia zmian (Changelog)**.

---

## 1. Podsumowanie wykonawcze i architektura

- **Nazwa pluginu:** Aradium (`pl.nauka.aradium.AradiumPlugin`)
- **Wersja projektu:** `2.0.0-SNAPSHOT` (`pl.nauka:aradium`)
- **Stack technologiczny:** Maven, Java `25`, Paper API `26.3.build.251-stable` (`api-version: '26.3'` w `plugin.yml`)
- **Architektura:** Modularna (`core` + niezależne moduły w `modules/*`) sterowana przez `ModuleManager` i sekcję `modules:` w `config.yml`
- **Testy automatyczne:** JUnit 5 (`org.junit.jupiter:junit-jupiter:5.11.4`) w `src/test/java/pl/nauka/aradium/AradiumAuditTest.java` (14 testów jednostkowych)
- **Docelowy plik wdrożeniowy:** `C:\Users\mkopy\Desktop\serv\plugins\Aradium.jar` (budowany jako `target/Aradium.jar` dzięki `<finalName>Aradium</finalName>` w `pom.xml`)

### Przegląd komponentów projektu

| Komponent | Plik | Rola |
| :--- | :--- | :--- |
| Plan wdrożenia Core | `PLAN.md` | Architektura docelowa pakietów, schemat bazy danych SQLite/MySQL oraz 6-etapowy plan wdrożenia (Ekonomia, Sklepy, Spawnery, SkyBlock Core) |
| Konfiguracja Maven | `pom.xml` | Zależność `paper-api:26.3.build.251-stable`, `junit-jupiter:5.11.4` (`test`), Java 25 (`release: 25`) |
| Deskryptor pluginu | `src/main/resources/plugin.yml` | Metadane (`api-version: '26.3'`), komendy `/aradium`, `/magicitem`, `/magicarmor`, `/oreregion`, `/money`, `/pay`, `/eco`, `/shop`, `/spawner`, `/island` (`/is`, `/wyspa`), uprawnienia `aradium.admin`, `aradium.player` |
| Konfiguracja główna | `src/main/resources/config.yml` | Flagi modułów (`economy`, `shop`, `spawners`, `skyblock`, `magic_items`, `ore_regions`, `armor`), konfiguracja `database`, `economy`, `skyblock`, `ore_regions`, komunikaty (`messages`), 7 magicznych przedmiotów (`magic_items`) |
| Konfiguracja zbroi | `src/main/resources/armor.yml` | Definicje zestawów zbroi (`gornik` T1–T5, `handlarz` T1–T3, `wojownik` T1–T5) oraz specjalnych elementów (`special_armor.buty_lotnika` — niezniszczalne buty latania) |
| Konfiguracja spawnerów | `src/main/resources/spawners.yml` | Definicja 3 tierów spawnerów (koszty ulepszeń, opóźnienia spawnu, ilość mobów, limity i zasięg gracza) |
| Punktacja bloków wyspy | `src/main/resources/blocks-value.yml` | Punktacja materiałów budowlanych, surowców i bloków specjalnych do obliczania poziomu wyspy (`/is level`) |
| Konfiguracja misji | `src/main/resources/quests.yml` | Konfiguracja systemu misji SkyBlock (pełny Etap 1 z 7 misjami + przykład z Etapu 5) |
| Menu główne sklepu | `src/main/resources/shops/main.yml` | Główne menu `/shop` kierujące do kategorii sklepu (m.in. `blocks`) |
| Konfiguracja sklepu | `src/main/resources/shops/blocks.yml` | Kategoria `blocks` sklepu GUI (ceny `buy-price`/`sell-price`, wyłączanie przez `-1`, przycisk powrotu, format lore) |
| Konfiguracja regionów | `src/main/resources/regions.yml` | Domyślny szkielet `regions: {}` kopiowany przez `saveResource` |
| Menadżer bazy danych | `src/main/java/pl/nauka/aradium/core/database/DatabaseManager.java` | Centralne połączenie JDBC SQLite (tryb `WAL`) / MySQL i schemat tabel dla Ekonomii i SkyBlock Core |
| Moduł ekonomii | `src/main/java/pl/nauka/aradium/modules/economy/EconomyModule.java` | Cykl życia `EconomyManager`, okresowy zapis wsadowy (write-behind) co 60s, inicjalizacja przy wejściu i flush przy wyłączeniu |
| Menadżer ekonomii | `src/main/java/pl/nauka/aradium/modules/economy/EconomyManager.java` | Cache `ConcurrentHashMap<UUID, Double>` ($O(1)$), `getMoney`/`hasMoney`/`addMoney`/`takeMoney`/`setMoney`/`transferMoney` + zapis SQLite/YAML |
| Komenda `/money` | `src/main/java/pl/nauka/aradium/modules/economy/MoneyCommand.java` | Sprawdzanie stanu konta własnego lub wskazanego gracza z `TabCompleter` |
| Komenda `/pay` | `src/main/java/pl/nauka/aradium/modules/economy/PayCommand.java` | Przelewy waluty między graczami z walidacją kwot i salda |
| Komenda `/eco` | `src/main/java/pl/nauka/aradium/modules/economy/EcoAdminCommand.java` | Administracyjne zarządzanie kontami (`/eco <give|take|set> <gracz> <kwota>`) |
| Moduł sklepu GUI | `src/main/java/pl/nauka/aradium/modules/shop/ShopModule.java` | Kopiowanie `shops/*.yml`, inicjalizacja `ShopManager`/`ShopGUI` i zamykanie otwartych GUI przy `disable` |
| Menadżer sklepu | `src/main/java/pl/nauka/aradium/modules/shop/ShopManager.java` | Parsowanie i rejestr kategorii z `shops/*.yml` |
| Rekord pozycji sklepu | `src/main/java/pl/nauka/aradium/modules/shop/ShopItem.java` | Definicja przedmiotu sklepu (slot, materiał, `buyPrice`, `sellPrice`, `targetShop`) |
| Rekord kategorii sklepu | `src/main/java/pl/nauka/aradium/modules/shop/ShopCategory.java` | Konfiguracja kategorii GUI (`size`, `backButtonSlot`, `parentShopId`, `actionLoreFormat`) |
| Holder GUI sklepu | `src/main/java/pl/nauka/aradium/modules/shop/ShopInventoryHolder.java` | Dedykowany `InventoryHolder` do jednoznacznej identyfikacji GUI sklepu (Anti-Dupe) |
| Generator GUI sklepu | `src/main/java/pl/nauka/aradium/modules/shop/ShopGUI.java` | Budowa GUI, automatyczne lore instrukcji kliknięć (`LPM`, `Shift+LPM`, `PPM`, `ŚPM`), walidacja miejsca/salda i ochrona przed sprzedażą itemów z metadanymi/PDC |
| Listener sklepu GUI | `src/main/java/pl/nauka/aradium/modules/shop/ShopListener.java` | Pełne anulowanie `InventoryClickEvent` i `InventoryDragEvent` (Anti-Dupe) i routing kliknięć |
| Komenda `/shop` | `src/main/java/pl/nauka/aradium/modules/shop/ShopCommand.java` | Otwieranie sklepu `/shop [kategoria]` (alias `/sklep`) z podpowiedziami kategorii |
| Definicja tieru spawnera | `src/main/java/pl/nauka/aradium/modules/spawners/SpawnerTierDefinition.java` | Rekord statystyk i kosztów ulepszeń pojedynczego tieru spawnera |
| Menadżer PDC spawnerów | `src/main/java/pl/nauka/aradium/modules/spawners/SpawnerDataManager.java` | Zapis/odczyt tieru i `EntityType` w PDC bloku `CreatureSpawner` i przedmiotu (Silk Touch + stawianie) oraz aktualizacja statystyk |
| Moduł spawnerów | `src/main/java/pl/nauka/aradium/modules/spawners/SpawnerModule.java` | Parsowanie `spawners.yml`, inicjalizacja `SpawnerDataManager`/`SpawnerGUI` oraz zamykanie GUI przy `disable` |
| Holder GUI spawnera | `src/main/java/pl/nauka/aradium/modules/spawners/SpawnerUpgradeHolder.java` | Dedykowany `InventoryHolder` powiązany ze współrzędnymi konkretnego bloku spawnera (Anti-Dupe) |
| Generator GUI spawnera | `src/main/java/pl/nauka/aradium/modules/spawners/SpawnerGUI.java` | Menu ulepszeń (statystyki + podgląd kolejnego tieru), pobór waluty z `EconomyManager` i czystych itemów z ekwipunku oraz odświeżanie widzów |
| Listener spawnerów | `src/main/java/pl/nauka/aradium/modules/spawners/SpawnerListener.java` | Otwieranie GUI (`Shift + PPM`), Silk Touch bez farmy XP, stawianie z PDC, ochrona przed wybuchami oraz Anti-Dupe GUI |
| Komenda `/spawner` | `src/main/java/pl/nauka/aradium/modules/spawners/SpawnerCommand.java` | Administracyjne nadawanie spawnerów `/spawner give <gracz> <mob> [tier]` wraz z `TabCompleter` |
| Moduł SkyBlock | `src/main/java/pl/nauka/aradium/modules/skyblock/SkyBlockModule.java` | Cykl życia modułu SkyBlock, rejestracja komendy `/is`, listenerów (`IslandProtectionListener`, `SpawnerLimitListener`), poziomów, rankingu TOP i ulepszeń |
| Rdzeń wysp i generator | `src/main/java/pl/nauka/aradium/modules/skyblock/island/` | `Island`, `IslandManager`, `SkyBlockWorldGenerator`, `IslandSchematic`, `IslandRole`, `IslandPermission`, `BorderColor`, `IslandWarp` (siatka spiralna, schematy, WorldBorder, zapis SQLite/MySQL) |
| Poziomy i ranking wysp | `src/main/java/pl/nauka/aradium/modules/skyblock/level/` | `BlockValueManager` (`blocks-value.yml`), `IslandLevelCalculator` (async `ChunkSnapshot` + przyrostowa aktualizacja), `IslandTopCache` (ranking na żywo `/is top`) |
| Ulepszenia wyspy | `src/main/java/pl/nauka/aradium/modules/skyblock/upgrades/` | `IslandUpgradeType` (`WORLD_BORDER`, `SPAWNER_LIMIT`, `CROP_GROWTH`, `MEMBER_LIMIT`), `IslandUpgradeManager` (zakup za `$`, zapis `skyblock_upgrades`, aplikowanie statystyk) |
| Menu GUI SkyBlock | `src/main/java/pl/nauka/aradium/modules/skyblock/gui/` | `SkyBlockGuiHolder`, `IslandSchematicGUI`, `IslandPanelGUI`, `IslandRolesGUI`, `IslandWarpsGUI`, `IslandUpgradesGUI`, `IslandTopGUI` (schematy, prywatność, kolory granicy, role, warpy, ulepszenia, TOP 10) |
| Ochrona wysp | `src/main/java/pl/nauka/aradium/modules/skyblock/listeners/IslandProtectionListener.java` | Blokada ingerencji bez uprawnień wyspy, ochrona przed wybuchami/pojemnikami oraz czyszczenie sesyjnego `TRUSTED` przy wylogowaniu |
| Limity spawnerów i wzrost | `src/main/java/pl/nauka/aradium/modules/skyblock/listeners/SpawnerLimitListener.java` | Egzekwowanie limitu spawnerów na wyspie (`SPAWNER_LIMIT`), przyspieszanie wzrostu roślin (`CROP_GROWTH` w `BlockGrowEvent`) oraz aktualizacja poziomu przy zmianie bloków |
| Komenda `/island` (`/is`) | `src/main/java/pl/nauka/aradium/modules/skyblock/commands/IslandCommand.java` | Obsługa wysp (`/is`, `create`, `home`, `sethome`, `level`, `top`, `upgrades`, `warp`, `setwarp`, `delwarp`, `roles`, `trust`, `untrust`, `invite`, `accept`, `kick`, `leave`, `transfer`, `delete`, `privacy`, `border`) |
| Klasa główna | `src/main/java/pl/nauka/aradium/AradiumPlugin.java` | Inicjalizacja `DatabaseManager`, rejestracja modułów (`EconomyModule`, `ShopModule`, `SpawnerModule`, `SkyBlockModule`, `MagicItemModule`, `OreRegionModule`, `ArmorModule`), obsługa `onEnable()` / `onDisable()`, `/aradium` |
| Kontrakt modułu | `src/main/java/pl/nauka/aradium/core/PluginModule.java` | Interfejs cyklu życia modułu (`getId`, `enable`, `disable`, `reload`, `isEnabled`) |
| Klasa bazowa modułu | `src/main/java/pl/nauka/aradium/core/AbstractModule.java` | Automatyczne rejestrowanie i wyrejestrowywanie listenerów (`HandlerList.unregisterAll`) oraz komend (`TabExecutor`) |
| Menedżer modułów | `src/main/java/pl/nauka/aradium/core/ModuleManager.java` | Łączenie modułów z konfiguracją `modules.<id>`, bezpieczne włączanie, wyłączanie w odwrotnej kolejności i reload |
| Komenda główna | `src/main/java/pl/nauka/aradium/core/AradiumCommand.java` | Obsługa `/aradium <reload|modules>` wraz z `TabCompleter` |
| Definicja przedmiotu | `src/main/java/pl/nauka/aradium/modules/magicitems/MagicItemDefinition.java` | Rekord danych konfiguracyjnych magicznego przedmiotu i flaga umiejętności specjalnych (`isGhostAbility`) |
| Moduł magicznych itemów | `src/main/java/pl/nauka/aradium/modules/magicitems/MagicItemModule.java` | Klucze PDC, `isMagicItem`, `Registry.MOB_EFFECT`, sesje trybu `duch` (`SPECTATOR` + `flySpeed` + efekty końcowe), trwałość cooldownów w `cooldowns.yml` |
| Komenda magicznych itemów | `src/main/java/pl/nauka/aradium/modules/magicitems/MagicItemCommand.java` | `/magicitem` z walidacją poziomu `1-255`, obsługą zestawów zbroi, pełnego ekwipunku oraz `TabCompleter` |
| Listener magicznych itemów | `src/main/java/pl/nauka/aradium/modules/magicitems/MagicItemListener.java` | Ochrona `Interact`/`BlockPlace`/`Consume`/craftingu/grindstone, obsługa umiejętności `GHOST` oraz blokada `TeleportCause.SPECTATE` |
| Typ części zbroi | `src/main/java/pl/nauka/aradium/modules/armor/ArmorPieceType.java` | Enum 4 części zbroi (`HELMET`, `CHESTPLATE`, `LEGGINGS`, `BOOTS`) oraz mapowanie materiałów |
| Bonus tieru zbroi | `src/main/java/pl/nauka/aradium/modules/armor/ArmorTierBonus.java` | Rekord efektów mikstur, `maxHealthLevels` (`+4.0 HP` / poziom), lore i `CustomModelData` dla danego tieru |
| Definicja zestawu zbroi | `src/main/java/pl/nauka/aradium/modules/armor/ArmorSetDefinition.java` | Rekord zestawu zbroi (`id`, `displayName`, `materialPrefix`, `nbtTag`, nazwy elementów i mapa tierów) |
| Specjalny element zbroi | `src/main/java/pl/nauka/aradium/modules/armor/SpecialArmorDefinition.java` | Rekord specjalnego przedmiotu pancerza (`buty_lotnika` — materiał, slot, `unbreakable`, umiejętność `FLIGHT`) |
| Moduł magicznych zbroi | `src/main/java/pl/nauka/aradium/modules/armor/ArmorModule.java` | Ładowanie `armor.yml`, klucze PDC (`armor_set`, `armor_tier`, `armor_tag`, `armor_equipped_count`, `special_armor`), `CustomModelDataComponent`, weryfikacja Full Set (4/4), aktualizacja licznika `(%equipped%/4)`, efekty/`Attribute.MAX_HEALTH` oraz logika latania i ochrony przed 1. upadkiem dla "Butów lotnika" |
| Listener magicznych zbroi | `src/main/java/pl/nauka/aradium/modules/armor/ArmorListener.java` | Event-driven sprawdzanie zestawu i "Butów lotnika" przez `PlayerArmorChangeEvent`, synchronizacja lore przy kliknięciach ekwipunku/wyrzucaniu, anulowanie pierwszego upadku w `EntityDamageEvent`, `PlayerJoinEvent`, `PlayerChangedWorldEvent`, `PlayerGameModeChangeEvent`, `PlayerRespawnEvent`, `PlayerQuitEvent` i `PrepareGrindstoneEvent` |
| Komenda magicznych zbroi | `src/main/java/pl/nauka/aradium/modules/armor/ArmorCommand.java` | `/magicarmor <gracz> <zestaw\|przedmiot> [tier] [all\|helmet\|chestplate\|leggings\|boots]` z `TabCompleter` i nadawaniem specjalnych zbroi |
| Definicja regionu | `src/main/java/pl/nauka/aradium/modules/oreregions/CuboidRegion.java` | Rekord prostopadłościanu (`ofCoordinates`, bezalokacyjne `contains`) niezależny od kolejności ładowania światów |
| Moduł stref rud | `src/main/java/pl/nauka/aradium/modules/oreregions/OreRegionModule.java` | Cache regionów, automatyczna migracja `regions.yml`, niemutowalny `BlockKey`, trwała kolejka `pending_regens`, odtwarzanie w `onEnable()`/`onDisable()`, `getZoneNames` |
| Komenda stref rud | `src/main/java/pl/nauka/aradium/modules/oreregions/OreRegionCommand.java` | `/oreregion <ruda> <min|max> [strefa]` z dynamicznym `TabCompleter` dla rud i istniejących stref |
| Listener kopania rud | `src/main/java/pl/nauka/aradium/modules/oreregions/OreBreakListener.java` | Delegowanie `BlockBreakEvent` do `OreRegionModule#handleBlockBreak` |
| Narzędzia czatu | `src/main/java/pl/nauka/aradium/utils/ChatUtils.java` | Adventure `LegacyComponentSerializer`, komponenty bez domyślnej kursywy dla itemów, `getMessage` z fallbackiem |
| Testy jednostkowe | `src/test/java/pl/nauka/aradium/AradiumAuditTest.java` | Testy `CuboidRegion`, `MagicItemModule`, `OreRegionModule`, `ArmorModule` i walidacja `armor.yml` |

### Jak dodać nowy moduł
1. Utworzyć nowy pakiet w `pl.nauka.aradium.modules.<nazwa_modułu>`.
2. Utworzyć klasę modułu dziedziczącą po `AbstractModule` (podać unikalne `id`, np. `"custom_drops"`).
3. W `onEnable()` zarejestrować listenery (`registerListener(...)`) i komendy (`registerCommand(...)`), a w `onDisable()` zwolnić zadania/zasoby.
4. Dodać flagę `<id>: true` w `modules:` w `config.yml` i zarejestrować instancję modułu przez `moduleManager.registerModule(...)` w `AradiumPlugin`.

---

## 2. Aktywne problemy i błędy

_Brak otwartych błędów — wszystkie dotychczas zidentyfikowane pozycje (`AUD-01` – `AUD-17`) zostały naprawione, zweryfikowane i usunięte z listy._

---

## 3. Dalsze kierunki rozwoju (Opcjonalne / Backlog)

1. **Testy integracyjne z MockBukkit / serwerem Paper:**
   - Rozbudowa zestawu testów `src/test` o symulację zdarzeń Bukkita (`PlayerInteractEvent`, `PlayerArmorChangeEvent`, `BlockBreakEvent`, `BlockPlaceEvent`) oraz cyklu życia `ModuleManager`.
2. **Nowe moduły rozgrywki:**
   - Wykorzystanie architektury `AbstractModule` do wdrożenia np. niestandardowych dropów z rud lub systemów bossów/aren.

---

## 4. Historia zmian (Changelog)

| Data | Wersja | Opis zmian | Powiązane punkty audytu |
| :--- | :--- | :--- | :--- |
| 2026-11-14 | `1.0-SNAPSHOT` | Przeprowadzenie pełnego audytu bazowego i utworzenie dokumentu `AUDIT.md`. | `AUD-01` – `AUD-13` (zidentyfikowane) |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Pełna refaktoryzacja do architektury modularnej (`core` + `modules`), usunięcie zbędnych elementów, migracja na Paper `26.3.build.251-stable` i Javę 25 (zero ostrzeżeń kompilacji) oraz wdrożenie poprawek bazowych. | `AUD-01` – `AUD-13` (rozwiązane) |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Druga fala poprawek audytowych: ochrona `OFF_HAND`/`BlockPlace`/`Consume`, zapis aktywnych cooldownów w `cooldowns.yml`, niemutowalny `BlockKey`, ładowanie `CuboidRegion` niezależne od kolejności światów, migracja starego formatu `regions.yml`, podpowiedzi stref oraz dodanie testów JUnit 5 (`AradiumAuditTest`, 3/3 zaliczone). | `AUD-14` – `AUD-17` (rozwiązane) |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Wzmocnienie krytycznych i wysokich poprawek audytowych: trwała kolejka `pending_regens` w `regions.yml` (odporność na crash serwera), zapis aktywnych cooldownów przy wyjściu gracza i normalizacja `typeId`, pełne blokowanie `OFF_HAND` przy użyciu przedmiotu w `HAND` oraz blokada craftingu (`PrepareItemCraftEvent`) i grindstone (`PrepareGrindstoneEvent`). | `AUD-01` – `AUD-04`, `AUD-14` (wzmocnione) |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Oczyszczenie listy aktywnych błędów w `AUDIT.md` po weryfikacji wszystkich naprawionych pozycji (`AUD-01` – `AUD-17`). | `AUD-01` – `AUD-17` (usunięte z listy otwartych) |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Ustawiono stałą nazwę artefaktu `Aradium.jar` (`<finalName>Aradium</finalName>` w `pom.xml`), zbudowano plugin oraz wdrożono go do `C:\Users\mkopy\Desktop\serv\plugins\Aradium.jar`. | Wdrożenie / konfiguracja buildu |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Przebudowano działanie przedmiotu `duch` (`ability: "GHOST"`): tryb `SPECTATOR` na 2s z `flySpeed 3` (`0.3f`), bezpieczne przywracanie trybu gry i `flySpeed` (także przy wyjściu i `onDisable`), blokada teleportacji spektatora oraz końcowe efekty `BLINDNESS` (3s) i `SPEED` (2s). Dodano testy w `AradiumAuditTest` (4/4 zaliczone) i wdrożono `Aradium.jar` na serwer. | Nowa funkcja (`magicitems`) |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Wdrożono moduł magicznych zestawów zbroi (`armor`): dodano `armor.yml` (zestawy `gornik` T1–T5, `handlarz` T1–T3, `wojownik` T1–T5), tagowanie PDC + `CustomModelDataComponent`, event-driven weryfikację pełnego zestawu (4/4) przez `PlayerArmorChangeEvent`, nakładanie/zdejmowanie `PotionEffect.INFINITE_DURATION` i modyfikatora `Attribute.MAX_HEALTH` (`addTransientModifier`), komendę `/magicarmor` i obsługę zestawów w `/magicitem`. Dodano testy w `AradiumAuditTest` (6/6 zaliczone) i wdrożono pliki na serwer. | Nowa funkcja (`armor`) |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Dodano dynamiczną aktualizację licznika założonych części zestawu `(%equipped%/4)` w opisie (`lore`) magicznych zbroi przy zakładaniu, zdejmowaniu, klikaniu w ekwipunku, odkładaniu do skrzyni i wyrzucaniu (`armor_equipped_count` w PDC, `ArmorModule#syncPlayerArmorLore`, `ArmorListener`). Dodano test w `AradiumAuditTest` (7/7 zaliczone) i zaktualizowano `Aradium.jar` oraz `armor.yml` na serwerze. | Poprawka UX (`armor`) |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Dodano unikalny element zbroi "Buty lotnika" (`special_armor.buty_lotnika` w `armor.yml`, `LEATHER_BOOTS`, `Unbreakable = true`): włączanie/wyłączanie latania na podstawie slota butów, ochrona trybów `CREATIVE`/`SPECTATOR`, jednorazowa ochrona przed pierwszym upadkiem (`DamageCause.FALL`) po zdjęciu w powietrzu oraz odświeżanie w `PlayerJoinEvent`, `PlayerChangedWorldEvent` i `PlayerGameModeChangeEvent`. Dodano test w `AradiumAuditTest` (8/8 zaliczone) i wdrożono pliki na serwer. | Nowa funkcja (`armor`) |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Przygotowano Etap 1 architektury Aradium Core (Ekonomia, Sklepy, Spawnery, SkyBlock): utworzono plan wdrożenia `PLAN.md`, centralny `DatabaseManager` (SQLite WAL / MySQL), kompletny `EconomyManager`, pliki konfiguracyjne `spawners.yml`, `quests.yml` i `shops/blocks.yml` oraz obsługę PDC spawnerów (`SpawnerTierDefinition`, `SpawnerDataManager`). Dodano testy jednostkowe w `AradiumAuditTest` (10/10 zaliczone) i zsynchronizowano pliki z serwerem. | Architektura Core / Faza 1 |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Wdrożono Fazę 1 i 2 Core w grze: zintegrowano `DatabaseManager` w `AradiumPlugin`, uruchomiono `EconomyModule` (cykliczny zapis write-behind + `/money`, `/pay`, `/eco`) oraz kompletny moduł Sklepu GUI `ShopModule` (`/shop`, `shops/main.yml`, `shops/blocks.yml`, automatyczne lore kliknięć LPM/Shift+LPM/PPM/ŚPM, Anti-Dupe w `ShopListener`, ochrona przed sprzedażą itemów z metadanymi/PDC). Zaktualizowano `AradiumAuditTest` (11/11 zaliczone) i wdrożono `Aradium.jar` oraz konfiguracje na serwer. | Core Faza 1–2 (`economy`, `shop`) |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Wdrożono Fazę 3 Core (Moduł 2: Zaawansowane Spawnery): dodano `SpawnerModule`, `SpawnerUpgradeHolder`, `SpawnerGUI` (ulepszanie przez Shift + PPM za walutę i przedmioty), `SpawnerListener` (Silk Touch bez duplikacji XP, transfer PDC przy stawianiu/wydobyciu, ochrona przed wybuchami i Anti-Dupe) oraz komendę `/spawner give <gracz> <mob> [tier]`. Zaktualizowano `AradiumAuditTest` (12/12 zaliczone) i wdrożono pliki na serwer. | Core Faza 3 (`spawners`) |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Wdrożono Fazę 4 Core (Moduł 3: SkyBlock Core — Zarządzanie Wyspą): dodano `SkyBlockModule`, `SkyBlockWorldGenerator` (void world `skyblock_world`), `IslandManager`, schematy wysp (`classic`, `desert`, `nether` w `IslandSchematicGUI`), menu GUI (`IslandPanelGUI`, `IslandRolesGUI`, `IslandWarpsGUI`), listener ochrony i sesyjnych `TRUSTED` (`IslandProtectionListener`) oraz komendę `/island` (`/is`, `/wyspa`). Zaktualizowano `AradiumAuditTest` (13/13 zaliczone), kompilacja Java 25 bez ostrzeżeń i wdrożono pliki na serwer. | Core Faza 4 (`skyblock`) |
| 2026-11-14 | `2.0.0-SNAPSHOT` | Wdrożono Fazę 5 Core (Moduł 4: Poziomy Wyspy i Ulepszenia): dodano `blocks-value.yml`, `BlockValueManager`, asynchroniczny `IslandLevelCalculator` oparty o `ChunkSnapshot` + przyrostowe odświeżanie bloków, `IslandTopCache`, `IslandTopGUI` (`/is top`), `IslandUpgradeType`, `IslandUpgradeManager` (zapis w `skyblock_upgrades` i YAML, integracja z `EconomyManager`), `IslandUpgradesGUI` (`/is upgrades`) oraz `SpawnerLimitListener` (limit spawnerów i przyspieszony wzrost roślin w `BlockGrowEvent`). Zaktualizowano `AradiumAuditTest` (14/14 zaliczone), kompilacja Java 25 (66 plików bez ostrzeżeń) i wdrożono na serwer. | Core Faza 5 (`skyblock` level & upgrades) |
