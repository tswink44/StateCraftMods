package com.statecraft.economy.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A recipe guide book that shows crafting recipes for all StateCraft Economy blocks.
 * Right-click to open the recipe guide screen.
 */
public class RecipeGuideItem extends Item {

    public RecipeGuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            openGuideScreen();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    private void openGuideScreen() {
        net.minecraft.client.Minecraft.getInstance().setScreen(
            new com.statecraft.economy.client.gui.RecipeGuideScreen());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click to view StateCraft Economy recipes")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Contains crafting recipes for all blocks")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}

