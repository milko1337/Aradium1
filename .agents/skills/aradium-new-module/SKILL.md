---
name: aradium-new-module
description: >-
  Guides the creation and registration of a new modular feature package in Aradium following AbstractModule, config toggle, Paper 26.3 safety rules, and AradiumAuditTest coverage.
  Use when adding a new plugin module such as moderation, ranks, or PlaceholderAPI integration.
---

# Dodawanie Nowego Modułu w Aradium

Aby nowy moduł był w pełni kompatybilny z `ModuleManager`, `/aradium` oraz testami JUnit 5:

1. **Pakiet i klasa modułu (`src/main/java/pl/nauka/aradium/modules/<nazwa>/`):**
   - Utwórz klasę `<Nazwa>Module extends AbstractModule`.
   - Konstruktor produkcyjny przekazuje `super(plugin, "<id>")`.
   - Jeśli logika/parsowanie ma być testowane w JUnit bez uruchomionego serwera, wydziel lekkie metody statyczne lub konstruktor testowy oparty o `super("<id>")` i `Logger`.
   - W `onEnable()` załaduj domyślny plik YAML (jeśli istnieje), zarejestruj listenery (`registerListener(...)`) i komendy (`registerCommand(...)`).
   - W `onDisable()` zamknij otwarte okna GUI tego modułu u graczy online, anuluj własne taski i wykonaj zrzut danych do DB/YAML.

2. **Rejestracja i konfiguracja:**
   - Zarejestruj moduł w `AradiumPlugin#onEnable()` przez `this.moduleManager.registerModule(new <Nazwa>Module(this));` oraz dodaj `Optional<<Nazwa>Module> get<Nazwa>Module()`.
   - Upewnij się, że flaga `<id>: true` znajduje się w `modules:` w `src/main/resources/config.yml`.
   - Dopisz nowe komendy i uprawnienia w `src/main/resources/plugin.yml`.

3. **Komunikacja między modułami i testy:**
   - Zależności między modułami rozwiązuj przez `plugin.get...Module().filter(PluginModule::isEnabled)`.
   - Unikaj wywołań wymagających uruchomionego `Server`/`RegistryAccess` (np. `Material#isAir()`, `Enchantment.*`, `Bukkit.getServer()`) na ścieżce jednostkowych testów ładowania YAML i obliczeń domenowych.
   - Dodaj przypadek testowy w `src/test/java/pl/nauka/aradium/AradiumAuditTest.java` i zweryfikuj cały zestaw przez skill `aradium-release-workflow`.
