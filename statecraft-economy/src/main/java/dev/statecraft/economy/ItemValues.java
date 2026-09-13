package dev.statecraft.economy;

import dev.statecraft.api.Money;
import dev.statecraft.api.UserError;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ItemValues {
    private final Map<String, Long> prices;
    private final Map<String, Long> currency;
    private final List<Map.Entry<String, Long>> denominations;

    public ItemValues(Map<String, Long> prices, Map<String, Long> currency, Predicate<String> registeredItem) {
        this.prices = checked(prices, registeredItem);
        this.currency = checked(currency, registeredItem);
        if (this.currency.isEmpty()) throw new UserError("At least one physical currency denomination is required.");
        denominations = this.currency.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey)).toList();
    }

    private static Map<String, Long> checked(Map<String, Long> source, Predicate<String> registered) {
        if (source == null || source.size() > 100_000) throw new UserError("Invalid item-value table.");
        Map<String, Long> result = new LinkedHashMap<>();
        source.forEach((id, value) -> {
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || id.equals("minecraft:air") || !registered.test(id) || value == null) {
                throw new UserError("Unknown or invalid item in item values: " + id);
            }
            Money.positive(value);
            result.put(id, value);
        });
        return Map.copyOf(result);
    }

    public Map<String, Long> prices() { return prices; }
    public Map<String, Long> currency() { return currency; }
    public List<Map.Entry<String, Long>> denominations() { return denominations; }
    public boolean isCurrency(String id) {
        return currency.containsKey(id) || id.startsWith("statecraft_economy:currency_");
    }
    public long sellPrice(String item) {
        if (isCurrency(item)) throw new UserError("Currency cannot be sold at the Trading Hub.");
        Long value = prices.get(item);
        if (value == null) throw new UserError("This item has no Trading Hub price. Use hub suggest.");
        return value;
    }

    public long removeCash(InventoryPort.Plan inventory) {
        long amount = 0;
        for (ItemLot slot : inventory.slots()) {
            Long denomination = currency.get(slot.item());
            if (!slot.empty() && denomination != null && slot.snbt().isEmpty()) {
                amount = Money.add(amount, Money.multiply(denomination, slot.count()));
                inventory.remove(slot, slot.count());
            }
        }
        return amount;
    }

    /** Greedy denomination issuance; any unrepresentable remainder stays in the ledger. */
    public long addCash(InventoryPort.Plan inventory, long requested, java.util.function.ToIntFunction<String> stackSize) {
        Money.positive(requested);
        long remaining = requested;
        for (Map.Entry<String, Long> denomination : denominations) {
            long count = remaining / denomination.getValue();
            if (count > 0) {
                if (count > Integer.MAX_VALUE) throw new UserError("That withdrawal cannot fit in an inventory.");
                inventory.add(new ItemLot(denomination.getKey(), "", (int) count, stackSize.applyAsInt(denomination.getKey())));
                remaining %= denomination.getValue();
            }
        }
        long issued = requested - remaining;
        if (issued == 0) throw new UserError("The amount is smaller than a physical currency denomination.");
        return issued;
    }
}
