package com.statecraft.economy.item;

import com.statecraft.economy.core.EconomyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A coin item that can be used as currency
 */
public class CoinItem extends Item {
    private final int value;
    private final String displayName;
    private final int color;

    public CoinItem(Properties properties, int value, String displayName, int color) {
        super(properties);
        this.value = value;
        this.displayName = displayName;
        this.color = color;
    }

    public int getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColor() {
        return color;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        double totalValue = value * stack.getCount();
        String formattedValue = EconomyManager.getInstance().formatCurrency(totalValue);

        tooltip.add(Component.literal("Value: " + formattedValue)
            .withStyle(ChatFormatting.GOLD));

        if (stack.getCount() > 1) {
            tooltip.add(Component.literal("(Each: " + EconomyManager.getInstance().formatCurrency(value) + ")")
                .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return value >= 1000; // Platinum coins have enchant glint
    }
}

