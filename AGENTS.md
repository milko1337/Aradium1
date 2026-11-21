# Zasady Pracy Agenta — Projekt Aradium (Paper 26.3 / Java 25)

## Kontekst i kontynuacja pracy na dowolnym komputerze

1. **Źródło prawdy projektu:** przed rozpoczęciem pracy w nowym czacie sprawdź aktualny stan w:
   - `AUDIT.md` — aktualna architektura, liczba klas/testów oraz Changelog,
   - `PLAN.md` — harmonogram etapów `1–11` i zadania pozostałe do wdrożenia,
   - `QUESTY_LISTA.md` i `ULEPSZENIA_I_RECEPTURY.md` — specyfikacje misji oraz katalog propozycji ulepszeń.
2. **Język komunikacji:** odpowiadaj użytkownikowi po polsku, zwięźle i technicznie. Wszystkie nazwy przedmiotów, komend, komunikatów i opisów GUI w pluginie pisz po polsku.
3. **Linki do plików:** przy odwołaniach do plików w odpowiedzi używaj klikalnych linków Markdown z `file:///` i ukośnikami `/`.

## Stack techniczny i reguły weryfikacji

- **Środowisko:** Java `25` (`--release 25`), Maven, Paper API `26.3.build.251-stable` (`api-version: '26.3'`), JUnit 5 (`AradiumAuditTest.java`).
- **Wymóg kompilacji bez ostrzeżeń:** po zmianach w kodzie zawsze uruchamiaj kompilację z flagą `-Dmaven.compiler.showDeprecation=true` i utrzymuj `0` błędów oraz `0` ostrzeżeń `javac`.
- **Pułapki API Paper `26.3` w testach jednostkowych (`Bukkit.getServer() == null`):**
  - Nie wywołuj `Material#isAir()`, `Material#isBlock()` ani `Material#isItem()` bez sprawdzenia `Bukkit.getServer() != null` (uruchamiają inicjalizację `RegistryAccess`). Do sprawdzania powietrza w logice/testach używaj porównań `mat == null || mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR` (np. `RecipeDefinition.isAirMaterial`).
  - Nie odwołuj się do `Enchantment.*`, `PotionEffectType.*` ani `Registry.ENCHANTMENT` podczas inicjalizacji klas używanych w czystych testach jednostkowych bez uruchomionego serwera.
  - Zamiast przestarzałego `InventoryClickEvent#setCursor(...)` używaj `event.getView().setCursor(...)` oraz `event.getView().getCursor()`.
  - W logice `MobSpawner` najpierw ustawiaj właściwy limit opóźnień (`minSpawnDelay` / `maxSpawnDelay`), aby uniknąć `IllegalArgumentException`.

## Standard zamykania etapu / większych zmian

1. Pełny build i testy JUnit 5 zakończone `BUILD SUCCESS` oraz `0` ostrzeżeń deprecacji.
2. Aktualizacja `AUDIT.md` (architektura, testy, Changelog) oraz odhaczenie zrealizowanych punktów w `PLAN.md`.
3. Wdrożenie zbudowanego `target/Aradium.jar` i zmienionych konfiguracji YAML na lokalny serwer testowy (jeśli katalog serwera istnieje na danym komputerze).
4. `git add -A`, czytelny `git commit` oraz `git push origin master`.
