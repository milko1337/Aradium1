---
trigger: always_on
---

# Zasady pracy nad projektem Aradium (Paper 26.3 / Java 25)

## 1. Komunikacja i dokumentacja źródłowa
- Komunikuj się z użytkownikiem oraz twórz nazwy/opisy GUI, wiadomości czatu i konfiguracje YAML po **polsku**.
- Głównymi źródłami prawdy o projekcie są pliki w katalogu głównym repozytorium:
  - `AUDIT.md` — aktualna architektura, lista klas, zasobów, testów i pełna historia zmian (Changelog).
  - `PLAN.md` — harmonogram etapów (`1–11`) oraz zadania pozostałe do wykonania.
  - `QUESTY_LISTA.md` — rozpisane `86` misji SkyBlock (Etapy I–VII).
  - `ULEPSZENIA_I_RECEPTURY.md` — katalog `100` nietypowych ulepszeń i architektura `recipes.yml`.
- Zaczynając pracę w nowej konwersacji (np. po przeniesieniu na inny komputer), najpierw zapoznaj się z `AUDIT.md` i `PLAN.md`.

## 2. Architektura i konwencje techniczne
- **Stack:** Maven, Java `25` (`--release 25`), Paper API `26.3.build.251-stable` (`api-version: '26.3'`), JUnit 5 (`AradiumAuditTest.java`).
- **Modularność:** każda nowa funkcja działa jako moduł dziedziczący po `AbstractModule` (`pl.nauka.aradium.core`), sterowany flagą `modules.<id>` w `config.yml`, rejestrowany w `AradiumPlugin` i zarządzany przez `ModuleManager`.
- **Formatowanie tekstu w grze:** używaj `ChatUtils.color(...)`, `ChatUtils.component(...)` oraz `ChatUtils.itemComponent(...)` (usuwa domyślną kursywę z nazw/lore przedmiotów).
- **Bezpieczeństwo GUI (Anti-Dupe):** każde okno GUI posiada własny `InventoryHolder` z bezwarunkowym anulowaniem kliknięć i przeciągań (`InventoryClickEvent`, `InventoryDragEvent`) w polach UI, weryfikacją `InventoryAction.COLLECT_TO_CURSOR` i bezpiecznym zwrotem przedmiotów gracza przy zamknięciu lub wyłączeniu pluginu.
- **Konfiguracja YAML:** zachowuj kodowanie UTF-8 bez BOM; wszystkie kluczowe listy dropów, cen, misji, receptur i ulepszeń trzymaj w edytowalnych plikach `.yml` w `src/main/resources/`.

## 3. Krytyczne pułapki Paper 26.3 i testów jednostkowych
- **Zero ostrzeżeń kompilacji:** kod musi przechodzić `mvn clean test package "-Dmaven.compiler.showDeprecation=true"` z wynikiem `0` błędów i `0` ostrzeżeń `javac`.
- **Testy bez uruchomionego serwera (`Bukkit.getServer() == null`):**
  - **NIE wywołuj** `Material#isAir()`, `Material#isBlock()`, `Material#isItem()` ani statycznych pól `Enchantment.*` / `Registry.*` w logice parsowania lub metodach testowanych jednostkowo bez strażnika `Bukkit.getServer() != null` — w Paper `26.3` wywołują one `RegistryAccess.registryAccess()` i rzucają `ExceptionInInitializerError`.
  - Zamiast `mat.isAir()` używaj porównań `mat == null || mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR` (np. `RecipeDefinition.isAirMaterial(mat)`).
- **Unikaj przestarzałych metod API Paper 26.3:**
  - Używaj `event.getView().setCursor(...)` / `event.getView().getCursor()` zamiast `InventoryClickEvent#setCursor(...)`.
  - Do rejestrów używaj `RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)` zamiast przestarzałego `Registry.ENCHANTMENT`.

## 4. Standard po każdej większej zmianie
1. Uruchom pełną weryfikację: `mvn clean test package "-Dmaven.compiler.showDeprecation=true"` (wszystkie testy w `AradiumAuditTest` muszą przejść).
2. Zaktualizuj `AUDIT.md` (architektura, liczby klas/zasobów/testów, Changelog) oraz odznacz wykonane punkty w `PLAN.md`.
3. Jeśli na danym komputerze istnieje lokalny serwer testowy, wdróż `target/Aradium.jar` oraz zmienione pliki `.yml`.
4. Wykonaj `git add -A`, czytelny `git commit` oraz `git push origin master`.
