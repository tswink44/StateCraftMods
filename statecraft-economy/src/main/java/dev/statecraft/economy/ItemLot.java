package dev.statecraft.economy;

import dev.statecraft.api.UserError;

import java.util.Objects;

/** SNBT is a vanilla tag, or explicitly marked full stack metadata including serialized capabilities. */
public record ItemLot(String item, String snbt, int count, int maxStackSize, boolean fullStackData) {
    public static final ItemLot EMPTY = new ItemLot("minecraft:air", "", 0, 64);

    public ItemLot(String item, String snbt, int count, int maxStackSize) {
        this(item, snbt, count, maxStackSize, false);
    }

    public ItemLot {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(snbt, "snbt");
        if (!item.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || count < 0
                || maxStackSize < 1 || maxStackSize > 1024 || snbt.length() > 65_536
                || (fullStackData && snbt.isEmpty())
                || (count > 0 && item.equals("minecraft:air"))) {
            throw new UserError("Invalid or excessively large item stack.");
        }
    }

    public boolean empty() { return count == 0; }
    public boolean sameItem(ItemLot other) {
        return item.equals(other.item) && snbt.equals(other.snbt) && maxStackSize == other.maxStackSize
                && fullStackData == other.fullStackData;
    }
    public ItemLot withCount(int quantity) { return quantity == 0 ? EMPTY : new ItemLot(item, snbt, quantity, maxStackSize, fullStackData); }
}
