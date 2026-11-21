---
name: aradium-workflow
description: >-
  Runbook for developing, building, testing, deploying, and maintaining the Aradium
  Minecraft Paper 26.3 plugin across machines. Use when starting a new session on this
  project, implementing the next stage from PLAN.md, adding a new module, running Maven
  verification, or releasing changes to GitHub.
---

# Aradium Plugin — Procedura Pracy i Wdrożeń

Ten skill opisuje powtarzalną procedurę pracy nad pluginem **Aradium** (`pl.nauka.aradium`), dzięki której agent na dowolnym komputerze odtworzy pełny kontekst i zachowa spójny standard projektu.

## 1. Start nowej sesji / po przeniesieniu na inny PC

1. Przeczytaj pliki źródłowe kontekstu:
   - `PLAN.md` — sprawdź, który etap (`1–11`) jest oznaczony jako ukończony, a który jest następny w kolejce.
   - `AUDIT.md` — sprawdź aktualną architekturę modułów, liczbę klas, zasobów i testów.
   - Jeśli zadanie dotyczy misji lub nietypowych ulepszeń/receptur, przeczytaj odpowiednio `QUESTY_LISTA.md` lub `ULEPSZENIA_I_RECEPTURY.md`.
2. Sprawdź dostępność Mavena i JDK 25:
   - Najpierw sprawdź `mvn -version` w `PATH`.
   - Jeśli `mvn` nie ma w `PATH` na Windows, sprawdź lokalizację tymczasową:
     `& "$env:LOCALAPPDATA\Temp\apache-maven-3.9.9\bin\mvn.cmd" -version`.

## 2. Dodawanie nowego modułu lub etapu

1. Utwórz dedykowany pakiet w `src/main/java/pl/nauka/aradium/modules/<modul>/`.
2. Utwórz klasę modułu dziedziczącą po `AbstractModule` z unikalnym identyfikatorem `id`:
   - W `onEnable()` załaduj konfigurację YAML, zarejestruj komendy (`registerCommand`) i listenery (`registerListener`).
   - W `onDisable()` zamknij otwarte okna GUI tego modułu, zatrzymaj taski/scheduler i zapisz dane na dysk/SQL.
   - Jeśli moduł ma logikę testowaną w `AradiumAuditTest` bez instancji serwera, udostępnij lekki konstruktor testowy (`super("<id>")` + `Logger`).
3. Zarejestruj moduł w `AradiumPlugin.java`, dodaj flagę `modules.<id>: true` w `src/main/resources/config.yml` oraz nowe komendy w `src/main/resources/plugin.yml`.
4. Dopisz testy jednostkowe w `src/test/java/pl/nauka/aradium/AradiumAuditTest.java` walidujące nowe pliki YAML (z przechwytywaniem logów `WARNING+`) oraz logikę domenową.

## 3. Weryfikacja, wdrożenie i GitHub

1. Uruchom pełny build i testy z wykrywaniem przestarzałych wywołań API:
   ```powershell
   mvn clean test package "-Dmaven.compiler.showDeprecation=true"
   ```
   Wymagany wynik: `BUILD SUCCESS`, `0` błędów, `0` ostrzeżeń `javac` oraz `100%` zaliczonych testów.
2. Zaktualizuj `AUDIT.md` (liczba plików Java w `src/main/java`, zasobów w `src/main/resources`, lista testów, sekcja architektury oraz wpis w `## 4. Historia zmian (Changelog)`) i oznacz ukończone zadania w `PLAN.md`.
3. Opcjonalne wdrożenie lokalne (jeśli istnieje lokalny serwer testowy, np. `C:\Users\<user>\Desktop\serv\plugins\`):
   - Skopiuj `target/Aradium.jar` do `plugins/Aradium.jar`.
   - Skopiuj zmienione pliki YAML z `src/main/resources/` do `plugins/Aradium/` (zachowując podkatalog `shops/`).
4. Zapisz i wypchnij zmiany do repozytorium:
   ```powershell
   git status -s
   git add -A
   git commit -m "<typ>(<zakres>): <krótki opis zmian>"
   git push origin master
   ```
