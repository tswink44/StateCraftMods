package com.statecraft.economy.item;

import com.statecraft.economy.config.EconomyConfig;
import com.statecraft.economy.core.EconomyManager;
import com.statecraft.economy.network.NetworkHandler;
import com.statecraft.economy.network.packets.OpenATMScreenPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A bank card that provides remote ATM access or balance checking.
 * Behavior is controlled by the 'bankCardMode' config:
 * - "full": Opens the full ATM interface (deposit, withdraw, transfer, etc.)
 * - "balance_only": Shows balance in chat only
 */
public class BankCardItem extends Item {

    public BankCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            String mode = EconomyConfig.BANK_CARD_MODE.get();

            if ("full".equalsIgnoreCase(mode)) {
                // Full ATM access — open the SimpleATMScreen remotely
                UUID defaultBankId = EconomyManager.getInstance().getBankRegistry().getDefaultBank().getId();
                NetworkHandler.sendToPlayer(new OpenATMScreenPacket(defaultBankId), serverPlayer);
            } else {
                // Balance-only mode — show balance in chat
                double balance = EconomyManager.getInstance().getBalance(player.getUUID());
                String formatted = EconomyManager.getInstance().formatCurrency(balance);
                player.sendSystemMessage(Component.literal("§6Bank Balance: §f" + formatted));
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        String mode;
        try {
            mode = EconomyConfig.BANK_CARD_MODE.get();
        } catch (Exception e) {
            // Config may not be loaded yet during early tooltip rendering
            mode = "full";
        }

        if ("full".equalsIgnoreCase(mode)) {
            tooltip.add(Component.literal("Right-click to open ATM")
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Full banking access anywhere")
                .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.literal("Right-click to check balance")
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("Use at an ATM for transactions")
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}

