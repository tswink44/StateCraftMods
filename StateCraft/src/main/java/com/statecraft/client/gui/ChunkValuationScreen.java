package com.statecraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Screen showing detailed breakdown of chunk valuation factors
 * Uses reflection to access StateCraftEconomy classes
 */
public class ChunkValuationScreen extends StateCraftScreen {

    private final int chunkX;
    private final int chunkZ;
    private boolean dataLoaded = false;
    private boolean dataRequested = false;
    private int ticksWaiting = 0;

    // Cached valuation data
    private double baseValue = 100.0;
    private double locationMultiplier = 1.0;
    private double distanceFromSpawn = 0;
    private double biomeMultiplier = 1.0;
    private String biomeName = "Unknown";
    private double demandMultiplier = 1.0;
    private int nearbyClaims = 0;
    private double governmentMultiplier = 1.0;
    private double improvementMultiplier = 1.0;
    private int improvementScore = 0;
    private double totalValue = 100.0;
    private double cityTaxRate = 0.0; // Actual city tax rate from server

    public ChunkValuationScreen(int chunkX, int chunkZ) {
        super(Component.literal("Chunk Valuation"));
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.guiWidth = 280;
        this.guiHeight = 220;
    }

    @Override
    protected void init() {
        super.init();

        // Request valuation data from server via reflection
        requestValuationData();

        int buttonY = guiTop + guiHeight - 28;

        // Back button
        this.addRenderableWidget(createButton(
            guiLeft + guiWidth / 2 - 40, buttonY, 80, 20,
            Component.literal("Back"),
            btn -> goBack()
        ));
    }

    @Override
    protected boolean shouldRenderTitle() {
        // We render our own custom orange title in renderContent
        return false;
    }

    private void requestValuationData() {
        try {
            // Use reflection to send packet via Economy mod's NetworkHandler
            Class<?> networkHandlerClass = Class.forName("com.statecraft.economy.network.NetworkHandler");
            Class<?> packetClass = Class.forName("com.statecraft.economy.network.packets.RequestChunkValuationPacket");

            var packetConstructor = packetClass.getConstructor(int.class, int.class);
            Object packet = packetConstructor.newInstance(chunkX, chunkZ);

            var sendMethod = networkHandlerClass.getMethod("sendToServer", Object.class);
            sendMethod.invoke(null, packet);

            dataRequested = true;
        } catch (Exception e) {
            // Economy mod not loaded or error
            dataLoaded = true; // Show default values
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!dataLoaded && dataRequested) {
            ticksWaiting++;

            // Try to get cached valuation data via reflection
            try {
                Class<?> handlerClass = Class.forName("com.statecraft.economy.network.ClientPacketHandler");
                var getCachedMethod = handlerClass.getMethod("getCachedValuation");
                Object data = getCachedMethod.invoke(null);

                if (data != null) {
                    // Get fields from the data object
                    Class<?> dataClass = data.getClass();

                    int dataChunkX = dataClass.getField("chunkX").getInt(data);
                    int dataChunkZ = dataClass.getField("chunkZ").getInt(data);

                    if (dataChunkX == chunkX && dataChunkZ == chunkZ) {
                        this.baseValue = dataClass.getField("baseValue").getDouble(data);
                        this.locationMultiplier = dataClass.getField("locationMultiplier").getDouble(data);
                        this.distanceFromSpawn = dataClass.getField("distanceFromSpawn").getDouble(data);
                        this.biomeMultiplier = dataClass.getField("biomeMultiplier").getDouble(data);
                        this.biomeName = (String) dataClass.getField("biomeName").get(data);
                        this.demandMultiplier = dataClass.getField("demandMultiplier").getDouble(data);
                        this.nearbyClaims = dataClass.getField("nearbyClaims").getInt(data);
                        this.governmentMultiplier = dataClass.getField("governmentMultiplier").getDouble(data);
                        this.improvementMultiplier = dataClass.getField("improvementMultiplier").getDouble(data);
                        this.improvementScore = dataClass.getField("improvementScore").getInt(data);
                        this.totalValue = dataClass.getField("totalValue").getDouble(data);
                        this.cityTaxRate = dataClass.getField("cityTaxRate").getDouble(data);
                        this.dataLoaded = true;
                    }
                }
            } catch (Exception e) {
                // Ignore reflection errors
            }

            // Timeout after 100 ticks (5 seconds)
            if (ticksWaiting > 100) {
                dataLoaded = true;
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + 15;
        int y = guiTop + 10;
        int rightX = guiLeft + guiWidth - 15;

        // Title
        graphics.drawCenteredString(this.font, "§6Chunk Valuation Breakdown", this.width / 2, y, COLOR_PRIMARY);
        y += 14;
        graphics.drawCenteredString(this.font, "§7Chunk (" + chunkX + ", " + chunkZ + ")", this.width / 2, y, COLOR_SECONDARY);
        y += 16;

        renderDivider(graphics, guiLeft + 10, y, guiWidth - 20);
        y += 8;

        if (!dataLoaded) {
            graphics.drawCenteredString(this.font, "§7Loading...", this.width / 2, y + 40, COLOR_TEXT);
            return;
        }

        // Base Value
        drawValueRow(graphics, x, y, "Base Value:", String.format("$%.2f", baseValue), "§f");
        y += 12;

        // Location Multiplier
        String locInfo = String.format("%.2fx §8(%.0f blocks from spawn)", locationMultiplier, distanceFromSpawn);
        drawValueRow(graphics, x, y, "Location:", locInfo, getMultiplierColor(locationMultiplier));
        y += 12;

        // Biome Multiplier
        String biomeDisplay = formatBiomeName(biomeName);
        String biomeInfo = String.format("%.2fx §8(%s)", biomeMultiplier, biomeDisplay);
        drawValueRow(graphics, x, y, "Biome:", biomeInfo, getMultiplierColor(biomeMultiplier));
        y += 12;

        // Demand Multiplier
        String demandInfo = String.format("%.2fx §8(%d nearby claims)", demandMultiplier, nearbyClaims);
        drawValueRow(graphics, x, y, "Demand:", demandInfo, getMultiplierColor(demandMultiplier));
        y += 12;

        // Improvements (multiplicative)
        String impInfo = String.format("%.2fx §8(%d improvement score)", improvementMultiplier, improvementScore);
        drawValueRow(graphics, x, y, "Improvements:", impInfo, getMultiplierColor(improvementMultiplier));
        y += 14;

        renderDivider(graphics, guiLeft + 10, y, guiWidth - 20);
        y += 8;

        // Subtotal (base * all multipliers)
        double subtotal = baseValue * locationMultiplier * biomeMultiplier * demandMultiplier * improvementMultiplier;
        drawValueRow(graphics, x, y, "Subtotal:", String.format("$%.2f", subtotal), "§e");
        y += 14;

        renderDivider(graphics, guiLeft + 10, y, guiWidth - 20);
        y += 8;

        // Total Value
        graphics.drawString(this.font, "§fTotal Value:", x, y, COLOR_TEXT);
        String totalStr = String.format("§a§l$%.2f", totalValue);
        int totalWidth = this.font.width(totalStr.replaceAll("§.", ""));
        graphics.drawString(this.font, totalStr, rightX - totalWidth, y, COLOR_TEXT);
        y += 14;

        // Tax info - use actual city tax rate
        double estimatedTax = totalValue * cityTaxRate;
        double taxPercent = cityTaxRate * 100;
        if (cityTaxRate > 0) {
            graphics.drawCenteredString(this.font,
                String.format("§7Estimated Tax (%.1f%%): §c$%.2f §7per period", taxPercent, estimatedTax),
                this.width / 2, y, COLOR_SECONDARY);
        } else {
            graphics.drawCenteredString(this.font,
                "§7No property tax in this area",
                this.width / 2, y, COLOR_SECONDARY);
        }
    }

    private void drawValueRow(GuiGraphics graphics, int x, int y, String label, String value, String valueColor) {
        graphics.drawString(this.font, "§7" + label, x, y, COLOR_TEXT);
        String cleanValue = value.replaceAll("§.", "");
        int valueWidth = this.font.width(cleanValue);
        graphics.drawString(this.font, valueColor + value, guiLeft + guiWidth - 15 - valueWidth, y, COLOR_TEXT);
    }

    private String getMultiplierColor(double multiplier) {
        if (multiplier >= 1.2) return "§a"; // Green for high
        if (multiplier >= 0.9) return "§e"; // Yellow for normal
        return "§c"; // Red for low
    }

    /**
     * Format biome name for display (removes minecraft: prefix and formats)
     */
    private String formatBiomeName(String biomeName) {
        if (biomeName == null) return "Unknown";
        String name = biomeName.replace("minecraft:", "");
        StringBuilder result = new StringBuilder();
        for (String word : name.split("_")) {
            if (result.length() > 0) result.append(" ");
            if (!word.isEmpty()) {
                result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
            }
        }
        return result.toString();
    }

    private void goBack() {
        clearCachedValuation();
        this.minecraft.setScreen(new ChunkInfoScreen(chunkX, chunkZ));
    }

    private void clearCachedValuation() {
        try {
            Class<?> handlerClass = Class.forName("com.statecraft.economy.network.ClientPacketHandler");
            var clearMethod = handlerClass.getMethod("clearCachedValuation");
            clearMethod.invoke(null);
        } catch (Exception e) {
            // Ignore
        }
    }

    @Override
    public void onClose() {
        clearCachedValuation();
        super.onClose();
    }
}

