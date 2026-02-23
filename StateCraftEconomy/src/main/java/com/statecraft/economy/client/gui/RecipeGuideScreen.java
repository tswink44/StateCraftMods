package com.statecraft.economy.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * A GUI screen that displays crafting recipes for all StateCraft Economy blocks.
 * Shows a visual crafting grid with ingredients for each block.
 */
public class RecipeGuideScreen extends Screen {

    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 200;
    private int guiLeft, guiTop;

    private int currentPage = 0;
    private final List<RecipeEntry> recipes = new ArrayList<>();

    public RecipeGuideScreen() {
        super(Component.literal("StateCraft Economy Recipe Guide"));
        buildRecipes();
    }

    private void buildRecipes() {
        // ATM: Iron surrounding Glass Pane center, Redstone bottom-center
        recipes.add(new RecipeEntry("ATM",
            "Banking terminal for deposits, withdrawals, and transfers",
            new String[]{"III", "IGI", "IRI"},
            new IngredientInfo[]{
                new IngredientInfo('I', "Iron Ingot", new ItemStack(Items.IRON_INGOT)),
                new IngredientInfo('G', "Glass Pane", new ItemStack(Items.GLASS_PANE)),
                new IngredientInfo('R', "Redstone", new ItemStack(Items.REDSTONE))
            },
            getModItemStack("atm")));

        // Trading Hub: Planks surrounding Emerald top-center, Chest center
        recipes.add(new RecipeEntry("Trading Hub",
            "Player-to-player item trading post",
            new String[]{"PEP", "PCP", "PPP"},
            new IngredientInfo[]{
                new IngredientInfo('P', "Planks", new ItemStack(Items.OAK_PLANKS)),
                new IngredientInfo('E', "Emerald", new ItemStack(Items.EMERALD)),
                new IngredientInfo('C', "Chest", new ItemStack(Items.CHEST))
            },
            getModItemStack("trading_hub")));

        // Company Vault: Iron surrounding Diamond center, Gold bottom-center
        recipes.add(new RecipeEntry("Company Vault",
            "Shared company storage and treasury access",
            new String[]{"III", "IDI", "IGI"},
            new IngredientInfo[]{
                new IngredientInfo('I', "Iron Ingot", new ItemStack(Items.IRON_INGOT)),
                new IngredientInfo('D', "Diamond", new ItemStack(Items.DIAMOND)),
                new IngredientInfo('G', "Gold Ingot", new ItemStack(Items.GOLD_INGOT))
            },
            getModItemStack("company_vault")));

        // Marketplace: Signs top/mid sides, Emerald top-center, Chest center, Iron bottom
        recipes.add(new RecipeEntry("Marketplace",
            "Global marketplace for buying and selling items",
            new String[]{"SES", "SCS", "III"},
            new IngredientInfo[]{
                new IngredientInfo('S', "Sign", new ItemStack(Items.OAK_SIGN)),
                new IngredientInfo('E', "Emerald", new ItemStack(Items.EMERALD)),
                new IngredientInfo('C', "Chest", new ItemStack(Items.CHEST)),
                new IngredientInfo('I', "Iron Ingot", new ItemStack(Items.IRON_INGOT))
            },
            getModItemStack("marketplace")));

        // Stock Market: Gold sides, Emerald Block top-center, Diamond center, Iron Block bottom
        recipes.add(new RecipeEntry("Stock Market",
            "Trade company stocks and view market trends",
            new String[]{"GEG", "GDG", "III"},
            new IngredientInfo[]{
                new IngredientInfo('G', "Gold Ingot", new ItemStack(Items.GOLD_INGOT)),
                new IngredientInfo('E', "Emerald Block", new ItemStack(Items.EMERALD_BLOCK)),
                new IngredientInfo('D', "Diamond", new ItemStack(Items.DIAMOND)),
                new IngredientInfo('I', "Iron Block", new ItemStack(Items.IRON_BLOCK))
            },
            getModItemStack("stock_market")));
    }

    private ItemStack getModItemStack(String name) {
        try {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation("statecraft_economy", name));
            if (item != null) {
                return new ItemStack(item);
            }
        } catch (Exception ignored) {}
        return new ItemStack(Items.BARRIER);
    }

    @Override
    protected void init() {
        super.init();
        guiLeft = (this.width - GUI_WIDTH) / 2;
        guiTop = (this.height - GUI_HEIGHT) / 2;

        // Previous page
        this.addRenderableWidget(Button.builder(Component.literal("◀ Prev"), btn -> {
            if (currentPage > 0) currentPage--;
        }).bounds(guiLeft + 10, guiTop + GUI_HEIGHT - 28, 60, 20).build());

        // Next page
        this.addRenderableWidget(Button.builder(Component.literal("Next ▶"), btn -> {
            if (currentPage < recipes.size() - 1) currentPage++;
        }).bounds(guiLeft + GUI_WIDTH - 70, guiTop + GUI_HEIGHT - 28, 60, 20).build());

        // Close
        this.addRenderableWidget(Button.builder(Component.literal("Close"), btn -> {
            this.onClose();
        }).bounds(guiLeft + GUI_WIDTH / 2 - 25, guiTop + GUI_HEIGHT - 28, 50, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // Background panel
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xCC222244);
        // Border
        graphics.fill(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + 1, 0xFF555588);
        graphics.fill(guiLeft, guiTop + GUI_HEIGHT - 1, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF555588);
        graphics.fill(guiLeft, guiTop, guiLeft + 1, guiTop + GUI_HEIGHT, 0xFF555588);
        graphics.fill(guiLeft + GUI_WIDTH - 1, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF555588);

        // Title bar
        graphics.fill(guiLeft + 1, guiTop + 1, guiLeft + GUI_WIDTH - 1, guiTop + 16, 0xAA333366);
        graphics.drawCenteredString(this.font, "§6§lStateCraft Economy Recipe Guide",
            guiLeft + GUI_WIDTH / 2, guiTop + 4, 0xFFFFFFFF);

        // Page indicator
        String pageStr = (currentPage + 1) + "/" + recipes.size();
        graphics.drawCenteredString(this.font, "§7" + pageStr,
            guiLeft + GUI_WIDTH / 2, guiTop + GUI_HEIGHT - 38, 0xFFAAAAAA);

        // Render current recipe
        if (currentPage >= 0 && currentPage < recipes.size()) {
            renderRecipe(graphics, recipes.get(currentPage), mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRecipe(GuiGraphics graphics, RecipeEntry recipe, int mouseX, int mouseY) {
        int centerX = guiLeft + GUI_WIDTH / 2;

        // Block name
        graphics.drawCenteredString(this.font, "§e§l" + recipe.name,
            centerX, guiTop + 22, 0xFFFFFF00);

        // Description
        graphics.drawCenteredString(this.font, "§7" + recipe.description,
            centerX, guiTop + 34, 0xFFAAAAAA);

        // Crafting grid
        int gridLeft = centerX - 27; // 3 slots * 18px each = 54, centered
        int gridTop = guiTop + 50;
        int slotSize = 18;

        // Grid background
        graphics.fill(gridLeft - 2, gridTop - 2,
            gridLeft + slotSize * 3 + 2, gridTop + slotSize * 3 + 2, 0xFF444444);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotX = gridLeft + col * slotSize;
                int slotY = gridTop + row * slotSize;

                // Slot background
                graphics.fill(slotX, slotY, slotX + slotSize - 1, slotY + slotSize - 1, 0xFF8B8B8B);
                graphics.fill(slotX + 1, slotY + 1, slotX + slotSize - 1, slotY + slotSize - 1, 0xFF373737);
                graphics.fill(slotX + 1, slotY + 1, slotX + slotSize - 2, slotY + slotSize - 2, 0xFF555555);

                // Get the character for this grid position
                char c = recipe.pattern[row].charAt(col);
                if (c != ' ') {
                    // Find the ingredient
                    for (IngredientInfo info : recipe.ingredients) {
                        if (info.key == c) {
                            graphics.renderItem(info.displayStack, slotX + 1, slotY + 1);

                            // Tooltip on hover
                            if (mouseX >= slotX && mouseX < slotX + slotSize &&
                                mouseY >= slotY && mouseY < slotY + slotSize) {
                                graphics.renderTooltip(this.font, info.displayStack, mouseX, mouseY);
                            }
                            break;
                        }
                    }
                }
            }
        }

        // Arrow
        int arrowX = gridLeft + slotSize * 3 + 10;
        int arrowY = gridTop + slotSize + 2;
        graphics.drawString(this.font, "§f→", arrowX, arrowY, 0xFFFFFFFF);

        // Result
        int resultX = arrowX + 16;
        int resultY = gridTop + slotSize;
        graphics.fill(resultX - 1, resultY - 1, resultX + slotSize, resultY + slotSize, 0xFF444444);
        graphics.fill(resultX, resultY, resultX + slotSize - 1, resultY + slotSize - 1, 0xFF555555);
        graphics.renderItem(recipe.result, resultX + 1, resultY + 1);

        // Result tooltip on hover
        if (mouseX >= resultX && mouseX < resultX + slotSize &&
            mouseY >= resultY && mouseY < resultY + slotSize) {
            graphics.renderTooltip(this.font, recipe.result, mouseX, mouseY);
        }

        // Ingredient legend
        int legendY = gridTop + slotSize * 3 + 10;
        graphics.drawString(this.font, "§6Ingredients:", guiLeft + 20, legendY, 0xFFFFFF00);
        legendY += 12;

        for (IngredientInfo info : recipe.ingredients) {
            graphics.renderItem(info.displayStack, guiLeft + 20, legendY - 2);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(this.font, "§f" + info.key + " §7= " + info.name,
                guiLeft + 40, legendY, 0xFFCCCCCC);
            graphics.pose().popPose();
            legendY += 14;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== Data Classes ====================

    private static class RecipeEntry {
        final String name;
        final String description;
        final String[] pattern;
        final IngredientInfo[] ingredients;
        final ItemStack result;

        RecipeEntry(String name, String description, String[] pattern,
                    IngredientInfo[] ingredients, ItemStack result) {
            this.name = name;
            this.description = description;
            this.pattern = pattern;
            this.ingredients = ingredients;
            this.result = result;
        }
    }

    private static class IngredientInfo {
        final char key;
        final String name;
        final ItemStack displayStack;

        IngredientInfo(char key, String name, ItemStack displayStack) {
            this.key = key;
            this.name = name;
            this.displayStack = displayStack;
        }
    }
}

