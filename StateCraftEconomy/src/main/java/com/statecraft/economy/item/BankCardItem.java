package com.statecraft.economy.item;

import com.statecraft.economy.core.EconomyManager;
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
 * A bank card that shows account balance when used
 */
public class BankCardItem extends Item {

    public BankCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            double balance = EconomyManager.getInstance().getBalance(player.getUUID());
            String formatted = EconomyManager.getInstance().formatCurrency(balance);
            player.sendSystemMessage(Component.literal("§6Bank Balance: §f" + formatted));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.literal("Right-click to check balance")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Use at an ATM for transactions")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}

