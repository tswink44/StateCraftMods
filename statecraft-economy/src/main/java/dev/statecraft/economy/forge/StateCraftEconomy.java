package dev.statecraft.economy.forge;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import dev.statecraft.StateCraft;
import dev.statecraft.api.Actor;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.UserError;
import dev.statecraft.config.ForgeConfigBinding;
import dev.statecraft.economy.EconomyCommands;
import dev.statecraft.economy.EconomyConfig;
import dev.statecraft.economy.EconomyData;
import dev.statecraft.economy.EconomyEngine;
import dev.statecraft.economy.InventoryPort;
import dev.statecraft.economy.data.EconomyFiles;
import dev.statecraft.network.SuiteNetwork;
import dev.statecraft.runtime.ServerRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod(StateCraftEconomy.MOD_ID)
public final class StateCraftEconomy {
    public static final String MOD_ID = "statecraft_economy";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ForgeConfigBinding<EconomyConfig> CONFIG = new ForgeConfigBinding<>(EconomyConfig::new);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final RegistryObject<Block> ATM = block("atm", "atm");
    public static final RegistryObject<Block> TRADING_HUB = block("trading_hub", "hub");
    public static final RegistryObject<Block> MARKETPLACE = block("marketplace", "market");
    public static final RegistryObject<Block> COMPANY_VAULT = block("company_vault", "company");
    public static final RegistryObject<Block> STOCK_MARKET = block("stock_market", "stock");
    public static final Map<Long, RegistryObject<Item>> CURRENCY = currency();
    public static final RegistryObject<Item> BANK_CARD = ITEMS.register("bank_card", () -> new ScreenItem("atm", true));
    public static final RegistryObject<Item> RECIPE_GUIDE = ITEMS.register("recipe_guide", () -> new ScreenItem("guide", false));
    private static volatile Running active;
    private long ticks;

    private static final class Running {
        final ServerRuntime runtime;
        final EconomyEngine engine;
        final EconomyFiles files;
        final ForgeTrades trades;
        boolean tickFailed;

        Running(ServerRuntime runtime, EconomyEngine engine, EconomyFiles files, ForgeTrades trades) {
            this.runtime = runtime;
            this.engine = engine;
            this.files = files;
            this.trades = trades;
        }
    }

    public StateCraftEconomy() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, CONFIG.spec());
        bus.addListener(this::configLoading);
        bus.addListener(this::configReloading);
        bus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) { event.enqueueWork(EconomyMenus::register); }

    private static RegistryObject<Block> block(String id, String page) {
        RegistryObject<Block> block = BLOCKS.register(id, () -> new UtilityBlock(page));
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    private static Map<Long, RegistryObject<Item>> currency() {
        Map<Long, RegistryObject<Item>> values = new LinkedHashMap<>();
        for (long dollars : new long[]{1, 10, 100, 1000, 10_000, 100_000, 1_000_000}) {
            values.put(dollars, ITEMS.register("currency_" + dollars, () -> new Item(new Item.Properties())));
        }
        return Map.copyOf(values);
    }

    private static Running running() {
        Running running = active;
        if (running == null) throw new UserError("StateCraft Economy is not attached to an active server.");
        return running;
    }

    private void configLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != CONFIG.spec()) return;
        CONFIG.loaded(event.getConfig());
        CONFIG.read().validate();
    }

    private void configReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != CONFIG.spec()) return;
        CONFIG.loaded(event.getConfig());
        Running running = active;
        if (running != null) {
            running.runtime.server().execute(() -> {
                if (active == running) {
                    try { reload(running, false); }
                    catch (UserError rejected) { notifyOperators(running, rejected.getMessage()); }
                }
            });
        }
    }

    @SubscribeEvent
    public void starting(ServerStartingEvent event) {
        ServerRuntime runtime = StateCraft.runtime();
        try {
            EconomyConfig config = CONFIG.read();
            config.validate();
            EconomyFiles files = new EconomyFiles(FMLPaths.CONFIGDIR.get().resolve(MOD_ID), ForgeInventory::knownItem,
                    ForgeTrades::knownProfession, ForgeInventory::stackSize);
            files.copyMissingDefaults();
            EconomyFiles.Loaded loaded = files.load();
            ForgeTrades trades = new ForgeTrades();
            ForgeTrades.Prepared prepared = trades.prepare(loaded);
            EconomyData data = runtime.store().load("economy", EconomyData.class, EconomyData::new);
            EconomyEngine engine = new EconomyEngine(data, runtime.governance(), config, loaded.values(), Clock.systemUTC(),
                    runtime::markDirty, new ForgeValuationEnvironment(event.getServer(), runtime.governance()), ForgeInventory::stackSize);
            Running running = new Running(runtime, engine, files, trades);
            engine.setCommandHooks(new EconomyCommands.Hooks() {
                @Override public String reload() { return StateCraftEconomy.reload(running, true); }
                @Override public String setPrice(String item, Long cents) {
                    try {
                        EconomyFiles.PriceUpdate update = files.planPrice(item, cents);
                        ForgeTrades.Prepared candidate = trades.prepare(update.loaded());
                        files.writePrices(update);
                        trades.install(candidate);
                        engine.reconfigure(engine.config(), update.loaded().values());
                        return "Item prices saved atomically and reloaded. New villager offers use current trade definitions.";
                    } catch (IOException | RuntimeException invalid) {
                        LOGGER.error("Price edit rejected; existing economy tables retained.", invalid);
                        throw new UserError("Price edit rejected: " + invalid.getMessage());
                    }
                }
                @Override public String merchants(Actor actor, List<String> args) {
                    return trades.command(actor, args, runtime.server().getPlayerList().getPlayer(actor.id()));
                }
                @Override public String guide() { return recipeGuide(running); }
            });
            trades.install(prepared);
            runtime.installEconomy(engine);
            runtime.registerModule("economy", StateCraftEconomy::executePlayer);
            runtime.registerFormProvider("economy", new dev.statecraft.economy.EconomyForms(engine));
            MinecraftForge.EVENT_BUS.register(trades);
            active = running;
            ticks = 0;
            runtime.markDirty();
            LOGGER.info("StateCraft Economy attached: {} item prices, {} trade definition files, shared world snapshot.",
                    loaded.values().prices().size(), loaded.trades().size());
        } catch (IOException failure) {
            throw new UncheckedIOException("StateCraft Economy could not load configuration or world data. No economy data was reset.", failure);
        }
    }

    private static String reload(Running running, boolean disk) {
        running.runtime.requireWritable();
        try {
            EconomyConfig config = disk ? CONFIG.reload() : CONFIG.read();
            config.validate();
            EconomyFiles.Loaded loaded = running.files.load();
            ForgeTrades.Prepared prepared = running.trades.prepare(loaded);
            running.trades.install(prepared);
            running.engine.reconfigure(config, loaded.values());
            running.tickFailed = false;
            LOGGER.info("StateCraft Economy reloaded all settings, item values, and trade definitions.");
            return "Economy configuration, item values, and trades reloaded atomically. Existing villagers keep their offers; newly generated offers use the new definitions.";
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Economy reload rejected; previous live gameplay configuration retained.", failure);
            throw new UserError("Economy reload rejected: " + failure.getMessage());
        }
    }

    @SubscribeEvent
    public void stopped(ServerStoppedEvent event) {
        Running running = active;
        if (running != null) {
            MinecraftForge.EVENT_BUS.unregister(running.trades);
            running.trades.restore();
            active = null;
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        Running running = active;
        if (event.phase != TickEvent.Phase.END || running == null || ++ticks % 20 != 0
                || !running.runtime.isWritable() || running.tickFailed) return;
        try { running.engine.tick(); }
        catch (RuntimeException failure) {
            running.tickFailed = true;
            LOGGER.error("Economy scheduled processing paused after an unexpected error. Correct the problem then /sce admin reload.", failure);
            notifyOperators(running, "Economy scheduled processing paused. See the server log; reload after correcting the error.");
        }
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        Running running = active;
        if (running != null && running.runtime.isWritable() && event.getEntity() instanceof ServerPlayer player) {
            running.engine.ensurePlayer(ServerRuntime.actor(player));
        }
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(command("statecrafteconomy"));
        event.getDispatcher().register(command("sce"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String alias) {
        return Commands.literal(alias).executes(context -> {
            if (context.getSource().getEntity() instanceof ServerPlayer player) {
                SuiteNetwork.open(player, "economy:atm");
                return 1;
            }
            return executeSource(context.getSource(), "help");
        }).then(Commands.argument("action", StringArgumentType.greedyString())
                .executes(context -> executeSource(context.getSource(), StringArgumentType.getString(context, "action"))));
    }

    private static int executeSource(CommandSourceStack source, String line) {
        Running running = running();
        if (source.getEntity() instanceof ServerPlayer player) {
            ServerRuntime.Reply reply = running.runtime.invoke(player, "economy", line);
            if (reply.success()) source.sendSuccess(() -> Component.literal(reply.text()), false);
            else source.sendFailure(Component.literal(reply.text()));
            return reply.success() ? 1 : 0;
        }
        try {
            running.runtime.requireWritable();
            Actor console = new Actor(new UUID(0, 0), source.getTextName(), source.hasPermission(2),
                    source.getLevel().dimension().location().toString(),
                    ((int) Math.floor(source.getPosition().x)) >> 4, ((int) Math.floor(source.getPosition().z)) >> 4);
            String reply = running.engine.execute(console, line, InventoryPort.NONE);
            running.runtime.markDirty();
            source.sendSuccess(() -> Component.literal(reply), false);
            return 1;
        } catch (UserError error) {
            source.sendFailure(Component.literal(error.getMessage()));
            return 0;
        }
    }

    private static String executePlayer(ServerPlayer player, String line) {
        Running running = running();
        running.runtime.requireWritable();
        Actor actor = ServerRuntime.actor(player);
        List<String> args = CommandLine.split(line);
        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("gui")) {
            if (args.size() != 2 || !args.get(1).startsWith("economy:")) throw new UserError("Use gui economy:<page>.");
            MenuRegistry.get(args.get(1));
            SuiteNetwork.open(player, args.get(1));
            return "Opened " + args.get(1) + ".";
        }
        if (!args.isEmpty() && args.get(0).equalsIgnoreCase("card")) {
            return card(running, player, actor, args.subList(1, args.size()));
        }
        return running.engine.execute(actor, line, new ForgeInventory(player, running.engine.config().utilityRadius));
    }

    private static String card(Running running, ServerPlayer player, Actor actor, List<String> args) {
        if (args.isEmpty()) return "Hold a bank card: card bind <authorizedAccount> | card clear. Cards never grant permissions.";
        ItemStack card = player.getMainHandItem();
        if (!card.is(BANK_CARD.get())) throw new UserError("Hold a bank card in your main hand.");
        if (args.size() == 2 && args.get(0).equalsIgnoreCase("bind")) {
            String account = running.engine.requireAccount(actor, args.get(1));
            running.engine.selectAccount(actor, account);
            card.getOrCreateTag().putString("StateCraftAccount", account);
        } else if (args.size() == 1 && args.get(0).equalsIgnoreCase("clear")) {
            if (card.hasTag()) card.getTag().remove("StateCraftAccount");
        } else throw new UserError("Use card bind <authorizedAccount> or card clear.");
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        return "Card label updated. Account authorization is checked anew on every use and every transaction.";
    }

    private static String recipeGuide(Running running) {
        List<String> recipes = new ArrayList<>();
        running.runtime.server().getRecipeManager().getRecipes().stream()
                .filter(recipe -> recipe.getId().getNamespace().equals(MOD_ID))
                .sorted(java.util.Comparator.comparing(recipe -> recipe.getId().toString())).limit(32).forEach(recipe -> {
                    ItemStack output = recipe.getResultItem(running.runtime.server().registryAccess());
                    Map<String, Integer> input = new LinkedHashMap<>();
                    Map<String, String> symbols = new LinkedHashMap<>();
                    List<String> cells = new ArrayList<>();
                    for (Ingredient ingredient : recipe.getIngredients()) {
                        ItemStack[] options = ingredient.getItems();
                        if (options.length == 0) {
                            cells.add(".");
                            continue;
                        }
                        String description = BuiltInRegistries.ITEM.getKey(options[0].getItem()).toString()
                                + (options.length > 1 ? " (or " + (options.length - 1) + " alternatives)" : "");
                        input.merge(description, 1, Integer::sum);
                        cells.add(symbols.computeIfAbsent(description, ignored -> String.valueOf((char) ('A' + symbols.size()))));
                    }
                    String layout = recipe instanceof ShapelessRecipe ? "shapeless"
                            : String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
                    if (recipe instanceof ShapedRecipe shaped) {
                        List<String> rows = new ArrayList<>();
                        for (int row = 0; row < shaped.getHeight(); row++) {
                            rows.add(String.join(" ", cells.subList(row * shaped.getWidth(), (row + 1) * shaped.getWidth())));
                        }
                        Map<String, String> legend = new LinkedHashMap<>();
                        symbols.forEach((item, symbol) -> legend.put(symbol, item));
                        layout = shaped.getWidth() + "×" + shaped.getHeight() + ": " + String.join(" / ", rows) + "; " + legend;
                    }
                    recipes.add(recipe.getId() + " [" + layout + "]: materials " + input + " → " + output.getCount() + " × "
                            + BuiltInRegistries.ITEM.getKey(output.getItem()));
                });
        return "Live server recipes (not a hardcoded recipe list):\n" + (recipes.isEmpty() ? "(No matching recipes are currently loaded.)" : String.join("\n", recipes))
                + "\nPhysical currency is not craftable; obtain it through configured villager offers, earned Trading Hub balances, or operator minting."
                + "\n/sce cash, hub, market, property, company, bank, loan, stock, tax; /sce card bind <account>; /sce merchant list."
                + "\nATM/Company Vault proximity is required for cash, Trading Hub for item conversion, Company Vault for company payments/dividends. Electronic commands otherwise work remotely.";
    }

    private static void notifyOperators(Running running, String text) {
        for (ServerPlayer player : running.runtime.server().getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) player.sendSystemMessage(Component.literal(text));
        }
    }

    private static final class UtilityBlock extends Block {
        private final String page;
        UtilityBlock(String page) {
            super(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL).requiresCorrectToolForDrops());
            this.page = "economy:" + page;
        }
        @Override public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                               InteractionHand hand, BlockHitResult hit) {
            if (player instanceof ServerPlayer serverPlayer) SuiteNetwork.open(serverPlayer, page);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    private static final class ScreenItem extends Item {
        private final String page;
        private final boolean card;
        ScreenItem(String page, boolean card) {
            super(new Item.Properties().stacksTo(1));
            this.page = "economy:" + page;
            this.card = card;
        }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack item = player.getItemInHand(hand);
            if (player instanceof ServerPlayer serverPlayer) {
                try {
                    Running running = running();
                    if (card && item.hasTag() && item.getTag().contains("StateCraftAccount")) {
                        running.runtime.requireWritable();
                        running.engine.selectAccount(ServerRuntime.actor(serverPlayer), item.getTag().getString("StateCraftAccount"));
                    }
                    SuiteNetwork.open(serverPlayer, page);
                } catch (UserError denied) {
                    serverPlayer.sendSystemMessage(Component.literal(denied.getMessage()));
                    return InteractionResultHolder.fail(item);
                }
            }
            return InteractionResultHolder.sidedSuccess(item, level.isClientSide);
        }
    }
}
