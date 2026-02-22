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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

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
            // Use DistExecutor with a supplier that returns a SafeRunnable
            // The class reference inside the lambda won't be loaded until the lambda is executed
            DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientProxy::openGuideScreen);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    /**
     * Client proxy class - referenced by name only, won't cause class loading on server
     */
    public static class ClientProxy {
        public static DistExecutor.SafeRunnable openGuideScreen() {
            return () -> com.statecraft.economy.client.ClientGuideHelper.openGuideScreen();
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click to view StateCraft Economy recipes")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Contains crafting recipes for all blocks")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}

