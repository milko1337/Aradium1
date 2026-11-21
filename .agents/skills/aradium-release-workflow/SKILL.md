---
name: aradium-release-workflow
description: >-
  Runs full Maven verification, updates AUDIT.md/PLAN.md, deploys Aradium.jar and modified YAML files to the test server if present, and commits/pushes changes to GitHub.
  Use when finishing a development stage, feature, or bugfix in the Aradium project.
---

# Procedura Weryfikacji, Wdrożenia i Publikacji (Aradium)

Wykonuj poniższe kroki po każdej ukończonej fazie lub większej zmianie w pluginie:

## 1. Wykrycie Mavena i kompilacja z testami

Sprawdź najpierw `mvn` w `PATH`, a jeśli brak — użyj lokalnej paczki Maven w `%LOCALAPPDATA%\Temp`:

```powershell
$mvn = (Get-Command mvn -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)
if (-not $mvn) {
    $mvn = "$env:LOCALAPPDATA\Temp\apache-maven-3.9.9\bin\mvn.cmd"
}
& $mvn clean test package "-Dmaven.compiler.showDeprecation=true"
```

Wymagania:
- `BUILD SUCCESS`
- `0` błędów oraz `0` ostrzeżeń deprecacji `javac`
- wszystkie testy w `AradiumAuditTest` zaliczone bez pominiętych asercji.

## 2. Aktualizacja dokumentacji

- Zaktualizuj `AUDIT.md`: liczba głównych klas Java w `src/main/java`, liczba plików zasobów w `src/main/resources`, liczba zaliczonych testów, opis nowych modułów/klas oraz nowy wpis u góry sekcji **4. Historia zmian (Changelog)**.
- Odhacz ukończone punkty w `PLAN.md`.

## 3. Wdrożenie na lokalny serwer testowy (opcjonalne per PC)

Jeśli na bieżącym komputerze istnieje folder serwera testowego (np. `C:\Users\$env:USERNAME\Desktop\serv\plugins` lub wskazany przez użytkownika), skopiuj zbudowany `target\Aradium.jar` oraz zmodyfikowane zasoby YAML z `src\main\resources\` do `...\plugins\Aradium\`.

## 4. Zapis i publikacja w Git

```powershell
git status -s
git add -A
if ($LASTEXITCODE -eq 0) { git commit -m "<typ>(<zakres>): <krótki opis>" }
if ($LASTEXITCODE -eq 0) { git push origin master }
```
