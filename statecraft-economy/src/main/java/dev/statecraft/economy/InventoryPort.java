package dev.statecraft.economy;

import dev.statecraft.api.UserError;

import java.util.ArrayList;
import java.util.List;

public interface InventoryPort {
    enum Utility { ATM, HUB, VAULT }

    List<ItemLot> snapshot();
    int selectedSlot();
    boolean near(Utility utility);

    /** Must either replace every slot, or throw without changing any slot. Runs on the server thread. */
    void replace(List<ItemLot> expected, List<ItemLot> replacement);

    default Plan plan() { return new Plan(this); }

    default ItemLot held() {
        List<ItemLot> slots = snapshot();
        int selected = selectedSlot();
        if (selected < 0 || selected >= slots.size() || slots.get(selected).empty()) {
            throw new UserError("Hold the item in your main hand.");
        }
        return slots.get(selected);
    }

    default void require(Utility... utilities) {
        for (Utility utility : utilities) {
            if (near(utility)) return;
        }
        throw new UserError("Stand near the required utility block: " + List.of(utilities) + ".");
    }

    InventoryPort NONE = new InventoryPort() {
        @Override public List<ItemLot> snapshot() { return List.of(); }
        @Override public int selectedSlot() { return -1; }
        @Override public boolean near(Utility utility) { return false; }
        @Override public void replace(List<ItemLot> expected, List<ItemLot> replacement) {
            if (!expected.isEmpty() || !replacement.isEmpty()) throw new UserError("A player inventory is required.");
        }
    };

    final class Plan {
        private final InventoryPort inventory;
        private final List<ItemLot> expected;
        private final List<ItemLot> slots;
        private List<ItemLot> committedSlots;
        private boolean committed;
        private boolean closed;

        public Plan(InventoryPort inventory) {
            this.inventory = inventory;
            expected = List.copyOf(inventory.snapshot());
            slots = new ArrayList<>(expected);
            for (ItemLot slot : slots) {
                if (slot.count() > slot.maxStackSize()) throw new UserError("Invalid inventory stack size.");
            }
        }

        public List<ItemLot> slots() { return List.copyOf(slots); }

        public int count(ItemLot kind) {
            long result = 0;
            for (ItemLot slot : slots) if (slot.sameItem(kind)) result += slot.count();
            return (int) Math.min(result, Integer.MAX_VALUE);
        }

        public void remove(ItemLot kind, int quantity) {
            if (quantity <= 0 || count(kind) < quantity) throw new UserError("Not enough matching items.");
            int remaining = quantity;
            for (int i = 0; i < slots.size() && remaining > 0; i++) {
                ItemLot slot = slots.get(i);
                if (slot.sameItem(kind)) {
                    int taken = Math.min(slot.count(), remaining);
                    slots.set(i, slot.withCount(slot.count() - taken));
                    remaining -= taken;
                }
            }
        }

        public int capacity(ItemLot kind) {
            long capacity = 0;
            for (ItemLot slot : slots) {
                if (slot.empty()) capacity += kind.maxStackSize();
                else if (slot.sameItem(kind)) capacity += slot.maxStackSize() - slot.count();
            }
            return (int) Math.min(capacity, Integer.MAX_VALUE);
        }

        public void add(ItemLot lot) {
            if (lot.empty()) return;
            if (capacity(lot) < lot.count()) throw new UserError("Not enough inventory space. No items or money were moved.");
            int remaining = lot.count();
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < slots.size() && remaining > 0; i++) {
                    ItemLot slot = slots.get(i);
                    if ((pass == 0 && !slot.empty() && slot.sameItem(lot)) || (pass == 1 && slot.empty())) {
                        int existing = slot.empty() ? 0 : slot.count();
                        int added = Math.min(lot.maxStackSize() - existing, remaining);
                        slots.set(i, lot.withCount(existing + added));
                        remaining -= added;
                    }
                }
            }
        }

        public void commit() {
            if (closed) throw new IllegalStateException("Inventory plan was already completed.");
            List<ItemLot> replacement = List.copyOf(slots);
            inventory.replace(expected, replacement);
            committedSlots = replacement;
            committed = true;
            closed = true;
        }

        public void rollback() {
            if (!committed) throw new IllegalStateException("Only a committed inventory plan can be rolled back.");
            inventory.replace(committedSlots, expected);
            committed = false;
        }

        public void checkUnchanged() {
            if (closed || !expected.equals(inventory.snapshot())) {
                throw new UserError("Your inventory changed; retry the action.");
            }
        }
    }
}
