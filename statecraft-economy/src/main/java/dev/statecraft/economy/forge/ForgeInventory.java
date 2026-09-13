package dev.statecraft.economy.forge;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.statecraft.api.UserError;
import dev.statecraft.economy.InventoryPort;
import dev.statecraft.economy.ItemLot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class ForgeInventory implements InventoryPort {
    private final ServerPlayer player;
    private final int radius;
    private EnumSet<Utility> nearby;

    public ForgeInventory(ServerPlayer player, int radius) {
        this.player = player;
        this.radius = radius;
    }

    public static boolean knownItem(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && BuiltInRegistries.ITEM.containsKey(location) && !id.equals("minecraft:air");
    }

    public static int stackSize(String id) {
        if (!knownItem(id)) return 0;
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id))).getMaxStackSize();
    }

    public static ItemLot encode(ItemStack stack) {
        if (stack.isEmpty()) return ItemLot.EMPTY;
        CompoundTag complete = stack.save(new CompoundTag());
        complete.remove("id");
        complete.remove("Count");
        if (complete.contains("ForgeCaps", Tag.TAG_COMPOUND) && complete.getCompound("ForgeCaps").isEmpty()) {
            complete.remove("ForgeCaps");
        }
        if (complete.getAllKeys().stream().anyMatch(key -> !key.equals("tag"))) {
            return new ItemLot(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), complete.toString(),
                    stack.getCount(), stack.getMaxStackSize(), true);
        }
        CompoundTag tag = stack.getTag();
        return new ItemLot(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                tag == null || tag.isEmpty() ? "" : tag.toString(), stack.getCount(), stack.getMaxStackSize());
    }

    public static ItemStack decode(ItemLot lot) {
        if (lot.empty()) return ItemStack.EMPTY;
        if (!knownItem(lot.item())) throw new UserError("An escrow item is not registered: " + lot.item() + ". Restore the providing mod before collecting it.");
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(lot.item()));
        ItemStack stack = new ItemStack(item, lot.count());
        if (!lot.snbt().isEmpty()) {
            try {
                CompoundTag data = TagParser.parseTag(lot.snbt());
                if (lot.fullStackData()) {
                    data.putString("id", lot.item());
                    data.putByte("Count", (byte) 1);
                    stack = ItemStack.of(data);
                    if (stack.isEmpty()) throw new UserError("The serialized escrow item could not be restored.");
                    stack.setCount(lot.count());
                } else stack.setTag(data);
            }
            catch (CommandSyntaxException failure) { throw new UserError("Invalid escrow item NBT. No items were delivered."); }
        }
        if (stack.getMaxStackSize() != lot.maxStackSize() || stack.getCount() > stack.getMaxStackSize()) {
            throw new UserError("The item's stack-size rules changed. An operator must restore its original item configuration.");
        }
        return stack;
    }

    @Override public List<ItemLot> snapshot() {
        return player.getInventory().items.stream().map(ForgeInventory::encode).toList();
    }

    @Override public int selectedSlot() { return player.getInventory().selected; }

    @Override public boolean near(Utility utility) {
        if (nearby == null) {
            nearby = EnumSet.noneOf(Utility.class);
            BlockPos center = player.blockPosition();
            for (BlockPos position : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                    center.offset(radius, radius, radius))) {
                if (position.distSqr(center) > (long) radius * radius || !player.serverLevel().hasChunkAt(position)) continue;
                Block block = player.serverLevel().getBlockState(position).getBlock();
                if (block == StateCraftEconomy.ATM.get()) nearby.add(Utility.ATM);
                else if (block == StateCraftEconomy.TRADING_HUB.get()) nearby.add(Utility.HUB);
                else if (block == StateCraftEconomy.COMPANY_VAULT.get()) nearby.add(Utility.VAULT);
            }
        }
        return nearby.contains(utility);
    }

    @Override public void replace(List<ItemLot> expected, List<ItemLot> replacement) {
        if (!player.serverLevel().getServer().isSameThread()) throw new IllegalStateException("Inventory transactions require the server thread.");
        if (expected.size() != replacement.size() || replacement.size() != player.getInventory().items.size()
                || !snapshot().equals(expected)) throw new UserError("Your inventory changed; retry the action.");
        List<ItemStack> decoded = new ArrayList<>(replacement.size());
        for (int slot = 0; slot < replacement.size(); slot++) {
            decoded.add(replacement.get(slot).equals(expected.get(slot))
                    ? player.getInventory().items.get(slot) : decode(replacement.get(slot)));
        }
        // No callbacks occur between comparison and replacement of the server-owned main inventory.
        for (int slot = 0; slot < decoded.size(); slot++) player.getInventory().items.set(slot, decoded.get(slot));
        player.getInventory().setChanged();
        try {
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        } catch (RuntimeException disconnected) {
            StateCraftEconomy.LOGGER.warn("Inventory committed but client synchronization failed for {}", player.getUUID(), disconnected);
        }
    }
}
