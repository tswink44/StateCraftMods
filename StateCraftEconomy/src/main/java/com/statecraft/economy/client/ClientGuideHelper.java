package com.statecraft.economy.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-only helper class for opening the recipe guide screen.
 * This class is in the client package and will only be loaded on the client side.
 */
@OnlyIn(Dist.CLIENT)
public class ClientGuideHelper {
    public static void openGuideScreen() {
        net.minecraft.client.Minecraft.getInstance().setScreen(
            new com.statecraft.economy.client.gui.RecipeGuideScreen());
    }
}

