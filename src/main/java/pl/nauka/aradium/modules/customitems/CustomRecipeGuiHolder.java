package pl.nauka.aradium.modules.customitems;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Identyfikator okien GUI systemu Custom Recipes (przeglądarka gracza oraz kreator administratora).
 */
public class CustomRecipeGuiHolder implements InventoryHolder {

    public enum ScreenType {
        PLAYER_CATEGORIES,
        PLAYER_CATEGORY_LIST,
        PLAYER_RECIPE_VIEW,
        ADMIN_LIST,
        ADMIN_EDITOR
    }

    private final ScreenType screenType;
    private final CustomRecipeDefinition.RecipeCategory category;
    private final int page;
    private final String recipeId;

    private CustomRecipeDefinition.RecipeCategory editorCategory;
    private boolean editorEnabled;
    private boolean creatingNew;
    private boolean itemsReturned;
    private Inventory inventory;

    public CustomRecipeGuiHolder(
            ScreenType screenType,
            CustomRecipeDefinition.RecipeCategory category,
            int page,
            String recipeId
    ) {
        this.screenType = screenType;
        this.category = category;
        this.page = Math.max(0, page);
        this.recipeId = recipeId;
        this.editorCategory = category != null ? category : CustomRecipeDefinition.RecipeCategory.MISC;
        this.editorEnabled = true;
    }

    public ScreenType getScreenType() {
        return screenType;
    }

    public CustomRecipeDefinition.RecipeCategory getCategory() {
        return category;
    }

    public int getPage() {
        return page;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public CustomRecipeDefinition.RecipeCategory getEditorCategory() {
        return editorCategory;
    }

    public void setEditorCategory(CustomRecipeDefinition.RecipeCategory editorCategory) {
        this.editorCategory = editorCategory;
    }

    public boolean isEditorEnabled() {
        return editorEnabled;
    }

    public void setEditorEnabled(boolean editorEnabled) {
        this.editorEnabled = editorEnabled;
    }

    public boolean isCreatingNew() {
        return creatingNew;
    }

    public void setCreatingNew(boolean creatingNew) {
        this.creatingNew = creatingNew;
    }

    public boolean isItemsReturned() {
        return itemsReturned;
    }

    public void markItemsReturned() {
        this.itemsReturned = true;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
