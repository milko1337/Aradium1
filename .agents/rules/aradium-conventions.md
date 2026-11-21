# Konwencje kodu i bezpieczeństwa — Aradium

## Architektura modułowa (`core/`)

- Każdy moduł dziedziczy po `AbstractModule`, posiada unikalne `id` w formacie `snake_case` i jest rejestrowany w `AradiumPlugin`.
- Każdy moduł musi mieć przełącznik `modules.<id>: true/false` w `src/main/resources/config.yml` i obsługiwać czyste włączanie, wyłączanie (`HandlerList.unregisterAll` i zamykanie otwartych GUI) oraz `/aradium <reload|enable|disable|toggle>`.
- Zależności pomiędzy modułami rozwiązuj dynamicznie przez `plugin.get...Module().filter(PluginModule::isEnabled)`, aby wyłączenie jednego modułu nie wywoływało błędów w innych.

## Ochrona przed duplikacją (Anti-Dupe) i integracja wysp SkyBlock

- **GUI (`InventoryHolder`):** każde okno GUI posiada własny `InventoryHolder` (`SkyBlockGuiHolder`, `ShopInventoryHolder`, `SpawnerUpgradeHolder`, `CustomRecipeGuiHolder`). Wszelkie kliknięcia i `InventoryDragEvent` w slotach interfejsu muszą być anulowane, a odblokowane sloty (jak `SELL_GUI` czy `ADMIN_EDITOR` receptur) muszą blokować `COLLECT_TO_CURSOR`, oddzielać wirtualne podglądy PDC od prawdziwych przedmiotów gracza i zwracać przedmioty przy zamknięciu (`InventoryCloseEvent`) z zabezpieczeniem przed podwójnym zwrotem.
- **Generator z lawy i wody / generatory bloków:** unikaj farmienia przez ręczne stawianie bloków, tłoki (`BlockPistonExtendEvent`/`Retract`), eksplozje i `Silk Touch`.
- **Uprawnienia wysp:** umiejętności obszarowe narzędzi (`2x2`, `3x3`, `Drwal`, `Żniwiarz 3x3`) muszą respektować granice i uprawnienia wyspy (`IslandPermission.BREAK` / `PLACE`) oraz używać strażnika rekurencji.
- **Ekonomia i `/shop`:** dla każdego materiału w `shops/*.yml` utrzymuj `maxSellPrice < minBuyPrice` bez arbitrażu między kategoriami; przedmioty z PDC/custom-item nigdy nie mogą być skupowane przez `/sellall` ani `/sellgui`.
