package dev.statecraft.economy.forge;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.statecraft.api.Actor;
import dev.statecraft.api.UserError;
import dev.statecraft.economy.EconomyEngine;
import dev.statecraft.economy.ItemValues;
import dev.statecraft.economy.data.EconomyFiles;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ForgeTrades {
    private static final String MERCHANT = "StateCraftMerchant";
    private static final String REVISION = "StateCraftTradeRevision";
    private static final String RESTOCK = "StateCraftNextRestock";
    public record Prepared(Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> vanilla,
                           Map<String, List<VillagerTrades.ItemListing>> merchants, String revision) {}

    private final Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> baseline = new LinkedHashMap<>();
    private Prepared active;

    public ForgeTrades() {
        VillagerTrades.TRADES.forEach((profession, levels) -> baseline.put(profession, copy(levels)));
    }

    public static boolean knownProfession(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && BuiltInRegistries.VILLAGER_PROFESSION.containsKey(location)
                && !id.equals("minecraft:none") && !id.equals("minecraft:nitwit");
    }

    private static Int2ObjectMap<VillagerTrades.ItemListing[]> copy(Int2ObjectMap<VillagerTrades.ItemListing[]> source) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> result = new Int2ObjectOpenHashMap<>();
        source.forEach((level, offers) -> result.put(level.intValue(), offers.clone()));
        return result;
    }

    public Prepared prepare(EconomyFiles.Loaded loaded) {
        for (String currency : loaded.values().currency().keySet()) {
            ItemStack prototype = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(currency)));
            if (!ForgeInventory.encode(prototype).snbt().isEmpty()) {
                throw new UserError("Currency items must have plain, untagged defaults without persistent capabilities: " + currency);
            }
        }
        Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> vanilla = new LinkedHashMap<>();
        baseline.forEach((profession, levels) -> vanilla.put(profession, copy(levels)));
        Map<String, List<VillagerTrades.ItemListing>> merchants = new LinkedHashMap<>();
        for (EconomyFiles.Trades file : loaded.trades()) {
            try {
                List<VillagerTrades.ItemListing> custom = new ArrayList<>();
                for (EconomyFiles.Offer offer : file.offers()) {
                    currencyExchange(offer, loaded.values());
                    VillagerTrades.ItemListing listing = compile(offer, loaded.values());
                    if (file.profession() != null) {
                        VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(new ResourceLocation(file.profession()));
                        Int2ObjectMap<VillagerTrades.ItemListing[]> levels = vanilla.computeIfAbsent(profession, ignored -> new Int2ObjectOpenHashMap<>());
                        List<VillagerTrades.ItemListing> level = new ArrayList<>(Arrays.asList(levels.getOrDefault(offer.level(), new VillagerTrades.ItemListing[0])));
                        level.add(listing);
                        levels.put(offer.level(), level.toArray(VillagerTrades.ItemListing[]::new));
                    } else custom.add(listing);
                }
                if (file.merchant() != null) merchants.put(file.merchant(), List.copyOf(custom));
            } catch (RuntimeException invalid) {
                throw new UserError("Rejected trade file " + file.source() + ": " + invalid.getMessage());
            }
        }
        return new Prepared(Map.copyOf(vanilla), Map.copyOf(merchants), loaded.revision());
    }

    public void install(Prepared prepared) {
        prepared.vanilla().forEach(VillagerTrades.TRADES::put);
        active = prepared;
    }

    public void restore() {
        baseline.forEach((profession, levels) -> VillagerTrades.TRADES.put(profession, copy(levels)));
        active = null;
    }

    private static VillagerTrades.ItemListing compile(EconomyFiles.Offer offer, ItemValues values) {
        ItemStack buy = stack(offer.buy(), values), second = offer.secondBuy() == null ? ItemStack.EMPTY : stack(offer.secondBuy(), values);
        ItemStack sell = stack(offer.sell(), values);
        return (merchant, random) -> new MerchantOffer(buy.copy(), second.copy(), sell.copy(), offer.maxUses(), offer.xp(), offer.priceMultiplier());
    }

    private static ItemStack stack(EconomyFiles.Stack definition, ItemValues values) {
        ItemStack result = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(definition.item())), definition.count());
        if (!definition.nbt().isEmpty()) {
            if (values.isCurrency(definition.item())) throw new UserError("Physical currency offers cannot have NBT.");
            try { result.setTag(TagParser.parseTag(definition.nbt())); }
            catch (CommandSyntaxException invalid) { throw new UserError("Invalid item SNBT: " + invalid.getMessage()); }
        }
        if (result.isEmpty() || result.getCount() > result.getMaxStackSize()) throw new UserError("Invalid trade stack count.");
        return result;
    }

    private static void currencyExchange(EconomyFiles.Offer offer, ItemValues values) {
        Long output = values.currency().get(offer.sell().item());
        Long first = values.currency().get(offer.buy().item());
        if (output == null || first == null) return;
        BigInteger input = BigInteger.valueOf(first).multiply(BigInteger.valueOf(offer.buy().count()));
        if (offer.secondBuy() != null) {
            Long second = values.currency().get(offer.secondBuy().item());
            if (second == null) return;
            input = input.add(BigInteger.valueOf(second).multiply(BigInteger.valueOf(offer.secondBuy().count())));
        }
        BigInteger paid = BigInteger.valueOf(output).multiply(BigInteger.valueOf(offer.sell().count()));
        if (paid.compareTo(input) > 0) throw new UserError("A cash-only exchange would create currency at the configured denominations.");
    }

    public String command(Actor actor, List<String> args, ServerPlayer player) {
        if (active == null) throw new UserError("Trade definitions are not loaded.");
        if (args.isEmpty() || (args.size() == 1 && args.get(0).equalsIgnoreCase("list"))) {
            return "Available merchants: " + active.merchants().keySet().stream().sorted()
                    .map(dev.statecraft.api.ui.DisplayText::words).collect(java.util.stream.Collectors.joining(", "))
                    + "\nMerchants restock once per Minecraft day."
                    + (actor.admin() ? "\nOperator command: /sce merchant spawn <id>." : "");
        }
        EconomyEngine.requireAdmin(actor);
        if (args.size() != 2 || !args.get(0).equalsIgnoreCase("spawn")) throw new UserError("Use merchant list or merchant spawn <id>.");
        if (player == null) throw new UserError("A player position is required to spawn a merchant.");
        String id = args.get(1);
        if (!active.merchants().containsKey(id) || active.merchants().get(id).isEmpty()) throw new UserError("Unknown or empty custom merchant definition.");
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(player.serverLevel());
        if (trader == null) throw new UserError("Minecraft could not create the merchant.");
        trader.setPos(player.getX() + 1.5, player.getY(), player.getZ());
        if (!player.serverLevel().noCollision(trader)) throw new UserError("Make room beside you before spawning the merchant.");
        trader.setCustomName(Component.literal("StateCraft " + id.replace('_', ' ')));
        trader.setCustomNameVisible(true);
        trader.setPersistenceRequired();
        trader.setNoAi(true);
        trader.setDespawnDelay(Integer.MAX_VALUE);
        trader.getPersistentData().putString(MERCHANT, id);
        refresh(trader, true);
        if (!player.serverLevel().addFreshEntity(trader)) throw new UserError("The merchant could not be added to this level.");
        return "Spawned " + id + " with " + trader.getOffers().size() + " configured offers.";
    }

    @SubscribeEvent
    public void interact(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide && event.getTarget() instanceof WanderingTrader trader
                && trader.getPersistentData().contains(MERCHANT)) refresh(trader, false);
    }

    private void refresh(WanderingTrader trader, boolean force) {
        if (active == null) return;
        CompoundTag data = trader.getPersistentData();
        long now = trader.level().getGameTime();
        if (!force && active.revision().equals(data.getString(REVISION)) && now < data.getLong(RESTOCK)) return;
        String id = data.getString(MERCHANT);
        MerchantOffers offers = new MerchantOffers();
        for (VillagerTrades.ItemListing listing : active.merchants().getOrDefault(id, List.of())) {
            MerchantOffer offer = listing.getOffer(trader, trader.getRandom());
            if (offer != null) offers.add(offer);
        }
        // Use the server's persistence API; client merchant-screen setters are not a server integration point.
        CompoundTag saved = new CompoundTag();
        trader.addAdditionalSaveData(saved);
        saved.put("Offers", offers.createTag());
        trader.readAdditionalSaveData(saved);
        data.putString(REVISION, active.revision());
        data.putLong(RESTOCK, now + 24_000);
    }
}
