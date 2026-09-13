package dev.statecraft.gametest;

import com.mojang.authlib.GameProfile;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.statecraft.StateCraft;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.CommandTemplate;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.api.ui.ActionIntent;
import dev.statecraft.api.ui.ActionOutcome;
import dev.statecraft.api.ui.ActionSelection;
import dev.statecraft.api.ui.OperationRef;
import dev.statecraft.api.ui.UiQuery;
import dev.statecraft.api.ui.EntityRef;
import dev.statecraft.api.ui.PersonalDashboard;
import dev.statecraft.api.ui.UiView;
import dev.statecraft.domain.GovernanceData;
import dev.statecraft.persistence.WorldStore;
import dev.statecraft.runtime.ServerRuntime;
import dev.statecraft.runtime.UiOperations;
import dev.statecraft.runtime.UiRuntime;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(StateCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SuiteGameTests {
    private SuiteGameTests() {}

    @GameTest(template = "test_empty", timeoutTicks = 40)
    public static void dedicatedServerLoadsTheSuite(GameTestHelper helper) {
        var runtime = StateCraft.runtime();
        helper.assertTrue(runtime.governance() != null, "Governance service is installed");
        boolean economyLoaded = ModList.get().isLoaded("statecraft_economy");
        helper.assertTrue(runtime.economy().available() == economyLoaded, "Economy bridge matches the installed mods");
        if (economyLoaded) {
            for (String id : new String[]{"atm", "trading_hub", "marketplace", "company_vault", "stock_market"}) {
                helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(new ResourceLocation("statecraft_economy", id)),
                        "Registered economy block: " + id);
                helper.assertTrue(runtime.server().getRecipeManager().byKey(new ResourceLocation("statecraft_economy", id)).isPresent(),
                        "Usable crafting recipe: " + id);
            }
            for (String id : new String[]{"currency_1", "currency_10", "currency_100", "currency_1000",
                    "currency_10000", "currency_100000", "currency_1000000", "bank_card", "recipe_guide"}) {
                helper.assertTrue(ForgeRegistries.ITEMS.containsKey(new ResourceLocation("statecraft_economy", id)),
                        "Registered economy item: " + id);
            }
            for (String id : new String[]{"bank_card", "recipe_guide"}) {
                helper.assertTrue(runtime.server().getRecipeManager().byKey(new ResourceLocation("statecraft_economy", id)).isPresent(),
                        "Usable crafting recipe: " + id);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 40)
    public static void managementMenusAreRegistered(GameTestHelper helper) {
        helper.assertTrue(!MenuRegistry.pages().isEmpty(), "At least one governance menu is registered");
        var overview = dev.statecraft.api.MenuCategory.OVERVIEW.pages(MenuRegistry.pages()).stream()
                .map(dev.statecraft.api.MenuPage::id).toList();
        helper.assertTrue(overview.equals(java.util.List.of("statecraft:dashboard", "statecraft:help")),
                "Overview and Help has only the personal dashboard and combined Help entry");
        helper.assertTrue(dev.statecraft.api.HelpNavigation.available(MenuRegistry.pages(), dev.statecraft.api.HelpNavigation.COMMANDS),
                "Command help stays reachable through Help");
        helper.assertTrue(dev.statecraft.api.HelpNavigation.available(MenuRegistry.pages(), dev.statecraft.api.HelpNavigation.RECIPES)
                        == ModList.get().isLoaded("statecraft_economy"),
                "Recipes are an internal Help destination only when Economy is installed");
        for (var page : MenuRegistry.pages()) {
            helper.assertTrue(!CommandLine.split(page.query()).isEmpty(), "Menu has a valid query: " + page.id());
            helper.assertTrue(!page.actions().isEmpty(), "Menu has usable actions: " + page.id());
            for (var action : page.actions()) {
                new CommandTemplate(action.command());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void menuQueriesUseTheLiveServerGateway(GameTestHelper helper) {
        UUID playerId = UUID.randomUUID();
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(playerId, "Menu" + playerId.toString().substring(0, 8)));
        BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        var runtime = StateCraft.runtime();
        for (var page : MenuRegistry.pages()) {
            String namespace = page.id().substring(0, page.id().indexOf(':'));
            var reply = runtime.invoke(player, namespace, page.query());
            helper.assertTrue(!reply.text().startsWith("StateCraft encountered an internal error"),
                    "Menu query must not crash: " + page.id() + " -> " + reply.text());
            if (!reply.success()) {
                String message = reply.text().toLowerCase(java.util.Locale.ROOT);
                helper.assertTrue(!message.matches("(?s).*(unknown|unsupported|unrecognized)(?: [a-z_]+)? (command|action).*")
                                && !message.contains("not implemented") && !message.contains("module is not installed"),
                        "Menu query must reach a supported command: " + page.id() + " -> " + reply.text());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void claimedLandRejectsUnauthorizedForgeEvents(GameTestHelper helper) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String nation = "Nation" + suffix;
        String state = "State" + suffix;
        String city = "City" + suffix;
        var level = helper.getLevel();
        BlockPos local = new BlockPos(1, 1, 1);
        BlockPos position = helper.absolutePos(local);
        helper.setBlock(local, Blocks.CHEST);
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Owner" + suffix));
        var guest = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Guest" + suffix));
        owner.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        guest.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        var governance = StateCraft.runtime().engine();
        var actor = ServerRuntime.actor(owner);
        governance.login(ServerRuntime.actor(guest));
        try {
            governance.execute(actor, "nation create " + nation);
            governance.execute(actor, "state create " + nation + " " + state);
            governance.execute(actor, "city create " + state + " " + city);
            governance.execute(actor, "chunk claim " + nation + " here");
            governance.execute(actor, "chunk assignstate " + nation + " here " + state);
            governance.execute(actor, "chunk assigncity " + state + " here " + city);

            var deniedBreak = new BlockEvent.BreakEvent(level, position, level.getBlockState(position), guest);
            MinecraftForge.EVENT_BUS.post(deniedBreak);
            helper.assertTrue(deniedBreak.isCanceled(), "A visitor cannot break claimed blocks");
            var allowedBreak = new BlockEvent.BreakEvent(level, position, level.getBlockState(position), owner);
            MinecraftForge.EVENT_BUS.post(allowedBreak);
            helper.assertTrue(!allowedBreak.isCanceled(), "A legitimate mayor can build without operator bypass");
            var interaction = new PlayerInteractEvent.RightClickBlock(guest, InteractionHand.MAIN_HAND, position,
                    new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
            MinecraftForge.EVENT_BUS.post(interaction);
            helper.assertTrue(interaction.isCanceled(), "A visitor cannot open a claimed container");

            var placement = new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, position.above()),
                    level.getBlockState(position), guest);
            MinecraftForge.EVENT_BUS.post(placement);
            helper.assertTrue(placement.isCanceled(), "A visitor cannot place blocks without a permit");
            governance.execute(actor, "chunk permit here " + guest.getGameProfile().getName() + " PLACE");
            guest.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Blocks.STONE));
            var builderInteraction = new PlayerInteractEvent.RightClickBlock(guest, InteractionHand.MAIN_HAND, position,
                    new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
            MinecraftForge.EVENT_BUS.post(builderInteraction);
            helper.assertTrue(!builderInteraction.isCanceled(), "A placement permit must allow building");
            helper.assertTrue(builderInteraction.getUseBlock() == net.minecraftforge.eventbus.api.Event.Result.DENY,
                    "A placement permit must not grant container access");
            var permittedPlacement = new BlockEvent.EntityPlaceEvent(
                    BlockSnapshot.create(level.dimension(), level, position.above()), level.getBlockState(position), guest);
            MinecraftForge.EVENT_BUS.post(permittedPlacement);
            helper.assertTrue(!permittedPlacement.isCanceled(), "The placement event must honor the same permit");

            var stand = new ArmorStand(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
            var attack = new AttackEntityEvent(guest, stand);
            MinecraftForge.EVENT_BUS.post(attack);
            helper.assertTrue(attack.isCanceled(), "A visitor cannot attack claimed entities");
            var explosion = new Explosion(level, null, position.getX(), position.getY(), position.getZ(),
                    3.0F, false, Explosion.BlockInteraction.DESTROY);
            explosion.getToBlow().add(position);
            var affected = new ArrayList<net.minecraft.world.entity.Entity>();
            affected.add(stand);
            var detonation = new ExplosionEvent.Detonate(level, explosion, affected);
            MinecraftForge.EVENT_BUS.post(detonation);
            helper.assertTrue(detonation.getAffectedBlocks().isEmpty(), "Explosions cannot destroy protected blocks");
            helper.assertTrue(detonation.getAffectedEntities().isEmpty(), "Explosions cannot damage protected entities");
        } finally {
            if (governance.government(nation).isPresent()) {
                governance.execute(actor, "nation disband " + nation + " cascade");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void stateCreationDropdownsUseLivePermissionsAndCitizenship(GameTestHelper helper) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String nation = "FormsNation" + suffix;
        String foreignNation = "OtherNation" + suffix;
        String existingState = "Existing" + suffix;
        String newState = "Selected" + suffix;
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Owner" + suffix));
        var citizen = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Citizen" + suffix));
        var foreign = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "Foreign" + suffix));
        var runtime = StateCraft.runtime();
        var governance = runtime.engine();
        var ownerActor = ServerRuntime.actor(owner);
        var citizenActor = ServerRuntime.actor(citizen);
        var foreignActor = ServerRuntime.actor(foreign);
        governance.login(citizenActor);
        governance.login(foreignActor);
        try {
            governance.execute(ownerActor, "nation create " + nation);
            governance.execute(ownerActor, "state create " + nation + " " + existingState);
            governance.execute(ownerActor, "nation invite " + nation + " " + citizenActor.name());
            governance.execute(citizenActor, "nation accept " + nation);
            governance.execute(foreignActor, "nation create " + foreignNation);
            String nationId = governance.government(nation).orElseThrow().id();
            String otherId = governance.government(foreignNation).orElseThrow().id();
            String template = "state create <nation> <name> <governor>";
            var schema = runtime.describeForm(owner, "statecraft:states", template, Map.of(), FormQuery.INITIAL);
            FormField parent = schema.field("nation").orElseThrow();
            FormField governor = schema.field("governor").orElseThrow();
            helper.assertTrue(parent.kind() == FormField.Kind.CHOICE, "Nation is a dropdown, not a raw ID box");
            helper.assertTrue(parent.choices().stream().anyMatch(choice -> choice.value().equals(nationId)),
                    "An eligible managed nation is offered");
            helper.assertTrue(parent.choices().stream().noneMatch(choice -> choice.value().equals(otherId)),
                    "An unmanaged foreign nation is not offered");
            helper.assertTrue(parent.value().equals(nationId), "The sole eligible nation is preselected");
            helper.assertTrue(governor.kind() == FormField.Kind.CHOICE && governor.dependencies().contains("nation"),
                    "Governor selection depends on nation");
            helper.assertTrue(governor.choices().stream().anyMatch(choice -> choice.value().equals(citizen.getUUID().toString())),
                    "An unassigned citizen of this nation can become governor");
            helper.assertTrue(governor.choices().stream().noneMatch(choice -> choice.value().equals(owner.getUUID().toString())
                            || choice.value().equals(foreign.getUUID().toString())),
                    "Existing state membership and foreign citizenship are excluded");

            var unauthorized = runtime.describeForm(foreign, "statecraft:states", template,
                    Map.of("nation", nationId, "governor", citizen.getUUID().toString()), FormQuery.INITIAL);
            helper.assertTrue(unauthorized.field("nation").orElseThrow().value().isEmpty(),
                    "Forged parent selections are cleared");
            helper.assertTrue(unauthorized.field("governor").orElseThrow().value().isEmpty(),
                    "A forged parent cannot retain a dependent governor");
            governance.execute(ownerActor, new CommandTemplate(template).render(Map.of(
                    "nation", parent.value(), "name", newState, "governor", citizen.getUUID().toString())));
            helper.assertTrue(governance.government(newState).orElseThrow().leader().equals(citizen.getUUID()),
                    "The offered selection is accepted by the actual create-state command");
        } finally {
            if (governance.government(nation).isPresent()) {
                governance.execute(ownerActor, "nation disband " + nation + " cascade");
            }
            if (governance.government(foreignNation).isPresent()) {
                governance.execute(foreignActor, "nation disband " + foreignNation + " cascade");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void readOnlyGatewayDefersActivityWithoutSerializingTheWorld(GameTestHelper helper) throws IOException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "Reader" + suffix));
        var runtime = StateCraft.runtime();
        runtime.engine().login(ServerRuntime.actor(player));
        var data = runtime.store().load("governance", GovernanceData.class, GovernanceData::new);
        data.players.get(player.getUUID().toString()).lastSeen = 1;
        runtime.saveNow();
        long revision = runtime.store().revision();

        var reply = runtime.invoke(player, "statecraft", "help");

        helper.assertTrue(reply.success(), "The read-only request succeeds: " + reply.text());
        helper.assertTrue(data.players.get(player.getUUID().toString()).lastSeen > 1,
                "Activity is still updated in memory");
        helper.assertTrue(runtime.store().revision() == revision,
                "A read-only command must not serialize changed activity timestamps");
        runtime.saveNow();
        var saved = new WorldStore(runtime.worldDirectory()).load("governance", GovernanceData.class, GovernanceData::new);
        helper.assertTrue(saved.players.get(player.getUUID().toString()).lastSeen > 1,
                "Forced saves still retain deferred activity");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void rejectedActionsPersistTheirActualProfileSideEffects(GameTestHelper helper) throws IOException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "Error" + suffix));
        var runtime = StateCraft.runtime();

        var reply = runtime.invoke(player, "statecraft", "not_a_command");

        helper.assertTrue(!reply.success(), "The unsupported action must be rejected");
        var saved = new WorldStore(runtime.worldDirectory()).load("governance", GovernanceData.class, GovernanceData::new);
        helper.assertTrue(saved.players.containsKey(player.getUUID().toString()),
                "Profile creation before a rejected action is not silently left unpersisted");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void economyConsolePersistsBeforeReturning(GameTestHelper helper) throws IOException {
        var runtime = StateCraft.runtime();
        if (runtime.economy().available()) {
            String recipient = "player:" + UUID.randomUUID();
            var server = runtime.server();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "sce admin mint " + recipient + " 10.00");
            helper.assertTrue(runtime.economy().balance(recipient) == 1000, "The actual console command credited its recipient");
            var snapshot = JsonParser.parseString(Files.readString(runtime.store().directory().resolve("world.json")))
                    .getAsJsonObject().getAsJsonObject("sections");
            Object live = runtime.store().load("economy", Object.class, Object::new);
            helper.assertTrue(snapshot.get("economy").equals(new Gson().toJsonTree(live)),
                    "The complete economy section is persisted before the console command returns");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "sce admin set " + recipient + " 0");
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void reviewedActionsReplayReceiptsNotEffects(GameTestHelper helper) throws IOException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "UiA" + suffix));
        var recipient = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "UiB" + suffix));
        var runtime = StateCraft.runtime();
        runtime.engine().login(ServerRuntime.actor(owner));
        runtime.engine().login(ServerRuntime.actor(recipient));
        var data = runtime.store().load("governance", GovernanceData.class, GovernanceData::new);
        int before = data.players.get(recipient.getUUID().toString()).inbox.size();
        ActionSelection selection = ActionSelection.form("statecraft:mail", "mail send <recipient> <subject> <body>",
                Map.of("recipient", recipient.getUUID().toString(), "subject", "Reviewed mail", "body", "One delivery only."));
        var quote = runtime.ui().preview(owner, selection);
        String reviewText = quote.preview().title().fallback() + quote.preview().warning().fallback()
                + quote.preview().lines().stream().map(line -> line.label().fallback() + line.value().fallback())
                    .collect(java.util.stream.Collectors.joining("\n"));
        helper.assertTrue(!reviewText.contains(owner.getUUID().toString()) && !reviewText.contains(recipient.getUUID().toString()),
                "Reviewed mail shows player names rather than internal identities");
        helper.assertTrue(runtime.ui().status(owner, quote.operation()).outcome() == ActionOutcome.READY,
                "An unsubmitted review can be retried only with its own ID");
        var first = runtime.ui().execute(owner, selection, quote.operation());
        helper.assertTrue(first.success(), "The reviewed action succeeds: " + first.text());
        helper.assertTrue(!first.text().contains(owner.getUUID().toString()) && !first.text().contains(recipient.getUUID().toString()),
                "Guided completion does not echo account identifiers");
        var again = runtime.ui().execute(owner, selection, quote.operation());
        helper.assertTrue(again.success(), "A repeated ID returns its stored completion");
        helper.assertTrue(data.players.get(recipient.getUUID().toString()).inbox.size() == before + 1,
                "The repeated request did not deliver another message");
        var message = data.players.get(recipient.getUUID().toString()).inbox.get(
                data.players.get(recipient.getUUID().toString()).inbox.size() - 1);
        var read = runtime.ui().execute(recipient,
                ActionSelection.form("statecraft:mail", "mail read <message>", Map.of("message", message.id)), OperationRef.NONE);
        helper.assertTrue(read.success() && read.text().contains("One delivery only."),
                "Guided mail reading preserves the full message");
        helper.assertTrue(!read.text().contains(owner.getUUID().toString()) && !read.text().contains(recipient.getUUID().toString())
                        && !read.text().contains(message.id),
                "Guided query results hide system-generated mail and account identifiers");
        UiView receiptView = runtime.ui().view(owner, UiQuery.detail(
                new EntityRef("statecraft", EntityRef.Kind.OPERATION, quote.operation().id().toString())));
        helper.assertTrue(!receiptView.body().fallback().contains(quote.operation().id().toString()),
                "Normal receipt details keep recovery identifiers hidden");
        helper.assertTrue(runtime.ui().status(recipient, quote.operation()).outcome() == ActionOutcome.UNKNOWN,
                "Another player cannot inspect the owner's receipt");
        var altered = ActionSelection.form(selection.page(), selection.template(),
                Map.of("recipient", recipient.getUUID().toString(), "subject", "Changed request", "body", "Different body."));
        helper.assertTrue(runtime.ui().execute(owner, altered, quote.operation()).outcome().uncertain(),
                "An ID cannot authorize changed inputs");
        helper.assertTrue(runtime.ui().execute(owner, selection,
                new OperationRef(UUID.randomUUID(), quote.operation().id())).outcome() == ActionOutcome.UNKNOWN,
                "A different world's ID cannot execute locally");

        var operations = runtime.store().load("ui_operations", UiOperations.Data.class, UiOperations.Data::new);
        operations.receipts.remove(quote.operation().id().toString());
        runtime.saveNow();
        helper.assertTrue(runtime.ui().execute(owner, selection, quote.operation()).outcome() == ActionOutcome.UNKNOWN,
                "Evicting a completed receipt does not revive its consumed review");
        helper.assertTrue(data.players.get(recipient.getUUID().toString()).inbox.size() == before + 1,
                "Expired history IDs never repeat effects");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void preparedOperationsRequireReconciliationAfterInterruption(GameTestHelper helper) throws IOException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "UiC" + suffix));
        var recipient = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "UiD" + suffix));
        var runtime = StateCraft.runtime();
        var actor = ServerRuntime.actor(owner);
        runtime.engine().login(actor);
        runtime.engine().login(ServerRuntime.actor(recipient));
        var data = runtime.store().load("governance", GovernanceData.class, GovernanceData::new);
        int before = data.players.get(recipient.getUUID().toString()).inbox.size();
        var selection = ActionSelection.form("statecraft:mail", "mail send <recipient> <subject> <body>",
                Map.of("recipient", recipient.getUUID().toString(), "subject", "Interrupted mail", "body", "Not sent."));
        var quote = runtime.ui().preview(owner, selection);
        String reviewText = quote.preview().title().fallback() + quote.preview().warning().fallback()
                + quote.preview().lines().stream().map(line -> line.label().fallback() + line.value().fallback())
                    .collect(java.util.stream.Collectors.joining("\n"));
        helper.assertTrue(!reviewText.contains(owner.getUUID().toString()) && !reviewText.contains(recipient.getUUID().toString()),
                "Payment reviews identify accounts by friendly names");
        runtime.ui().operations().begin(actor, quote.operation(), UiRuntime.requestHash(selection),
                selection.page(), "Interrupted mail", ActionIntent.MUTATION);
        runtime.saveNow();
        runtime.ui().forget(owner.getUUID());

        helper.assertTrue(runtime.ui().execute(owner, selection, quote.operation()).outcome() == ActionOutcome.UNCERTAIN,
                "A prepared receipt is never interpreted as permission to retry");
        helper.assertTrue(data.players.get(recipient.getUUID().toString()).inbox.size() == before,
                "The interrupted operation was not executed");
        var administrator = new dev.statecraft.api.Actor(UUID.randomUUID(), "UiOperator", true, actor.dimension(), actor.chunkX(), actor.chunkZ());
        runtime.executeCore(administrator, "admin operation resolve " + quote.operation().id() + " not_executed \"Verified no message or account effect\"");
        runtime.saveNow();
        helper.assertTrue(runtime.ui().status(owner, quote.operation()).outcome() == ActionOutcome.REJECTED,
                "Explicit operator reconciliation becomes visible after it is persisted");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void reviewedElectronicPaymentsCannotBeRepeated(GameTestHelper helper) {
        var runtime = StateCraft.runtime();
        if (runtime.economy().available()) {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            var owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "UiE" + suffix));
            var recipient = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "UiF" + suffix));
            runtime.engine().login(ServerRuntime.actor(owner));
            runtime.engine().login(ServerRuntime.actor(recipient));
            runtime.invoke(owner, "economy", "balance");
            runtime.invoke(recipient, "economy", "balance");
            String from = "player:" + owner.getUUID(), to = "player:" + recipient.getUUID();
            var server = runtime.server();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "sce admin set " + from + " 10.00");
            var selection = ActionSelection.form("economy:atm", "transfer <fromAccount> <toAccount> <amount>",
                    Map.of("fromAccount", from, "toAccount", to, "amount", "1.00"));
            var quote = runtime.ui().preview(owner, selection);
            var reply = runtime.ui().execute(owner, selection, quote.operation());
            helper.assertTrue(reply.success(), "The reviewed payment succeeds: " + reply.text());
            helper.assertTrue(runtime.ui().execute(owner, selection, quote.operation()).success(), "The receipt can be replayed");
            helper.assertTrue(runtime.economy().balance(from) == 900 && runtime.economy().balance(to) == 100,
                    "Only one payment was made");
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void contextualSectionsUseTheLivePresentationGateway(GameTestHelper helper) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "UiView" + suffix));
        var runtime = StateCraft.runtime();
        runtime.engine().login(ServerRuntime.actor(player));
        for (var page : MenuRegistry.pages()) {
            try {
                UiView view = runtime.ui().view(player, UiQuery.page(page.id()));
                helper.assertTrue(view.rows().size() <= UiView.MAX_ROWS, "View rows are bounded: " + page.id());
                helper.assertTrue(view.actions().size() <= UiView.MAX_ACTIONS, "View actions are bounded: " + page.id());
                for (var action : view.actions()) action.selection().registeredAction();
                if (page.id().equals("economy:guide")) {
                    helper.assertTrue(!view.body().fallback().contains("minecraft:") && !view.body().fallback().contains("statecraft_economy:"),
                            "The recipe guide uses item names rather than registry keys");
                    helper.assertTrue(view.body().fallback().contains("Iron Ingot"),
                            "The recipe guide still shows readable material requirements");
                }
            } catch (dev.statecraft.api.UserError denied) {
                String message = denied.getMessage().toLowerCase(java.util.Locale.ROOT);
                helper.assertTrue(!message.contains("has not provided") && !message.contains("not implemented")
                                && !message.contains("unknown command") && !message.contains("unsupported command"),
                        "Every section must reach a presentation provider: " + page.id() + " -> " + denied.getMessage());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void personalDashboardShowsCitizenshipAndExactAccountDestinations(GameTestHelper helper) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DashA" + suffix));
        var other = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DashB" + suffix));
        var runtime = StateCraft.runtime();
        var actor = ServerRuntime.actor(owner);
        var engine = runtime.engine();
        engine.login(actor);
        engine.login(ServerRuntime.actor(other));
        String nation = "Nation" + suffix, state = "State" + suffix, city = "City" + suffix, company = "Company" + suffix;
        try {
            engine.execute(actor, "nation create " + nation);
            engine.execute(actor, "state create " + nation + " " + state);
            engine.execute(actor, "city create " + state + " " + city);
            engine.execute(actor, "company create " + company);
            PersonalDashboard data = runtime.ui().personalDashboard(owner, PersonalDashboard.Request.FIRST);
            helper.assertTrue(data.name().equals(actor.name()), "Name comes from the connected player");
            helper.assertTrue(data.nation().name().fallback().equals(nation), "The actual nation citizenship is shown");
            helper.assertTrue(data.city().name().fallback().equals("[" + city + "] (" + state + "/" + nation + ")"),
                    "City formatting includes its state and nation");
            helper.assertTrue(data.city().target().entity().id().equals(engine.government(city).orElseThrow().id()),
                    "Clicking citizenship targets the exact government overview");
            helper.assertTrue(data.companies().entries().stream().anyMatch(entry -> entry.name().fallback().equals(company)
                            && entry.target().entity().kind() == EntityRef.Kind.COMPANY),
                    "Shareholdings link to the company overview");
            var overviewRequest = new dev.statecraft.api.ui.GovernmentOverview.Request(data.nation().target().entity().id(), 0, 0);
            var overview = runtime.ui().governmentOverview(owner, overviewRequest);
            helper.assertTrue(overview.officialInbox() != null && overview.groups().stream().anyMatch(group -> group.id().equals("settings")),
                    "Government managers receive Settings and the scoped official inbox");
            runtime.ui().view(owner, overview.officialInbox());
            var publicOverview = runtime.ui().governmentOverview(other, overviewRequest);
            helper.assertTrue(publicOverview.officialInbox() == null
                            && publicOverview.groups().stream().noneMatch(group -> group.id().equals("settings")),
                    "Other players cannot see government invitation settings or official mail");
            boolean privateMailbox = false;
            try { runtime.ui().view(other, overview.officialInbox()); }
            catch (dev.statecraft.api.UserError denied) { privateMailbox = true; }
            helper.assertTrue(privateMailbox, "Opening a copied official-mail target rechecks the sender's authority");
            PersonalDashboard stranger = runtime.ui().personalDashboard(other, PersonalDashboard.Request.FIRST);
            helper.assertTrue(stranger.nation() == null && stranger.companies().entries().isEmpty(),
                    "Dashboard data is scoped to the actual sender");
            if (runtime.economy().available()) {
                var account = data.accounts().entries().get(0);
                helper.assertTrue(account.target().page().equals("economy:atm")
                                && account.target().entity().id().equals(actor.account()),
                        "The personal balance opens that account's ATM");
                runtime.ui().view(owner, account.target());
                boolean rejected = false;
                try { runtime.ui().view(other, account.target()); }
                catch (dev.statecraft.api.UserError denied) { rejected = true; }
                helper.assertTrue(rejected, "A foreign player cannot reuse the personal account target");
            }
        } finally {
            if (engine.company(company).isPresent()) engine.execute(actor, "company disband " + company);
            if (engine.government(nation).isPresent()) engine.execute(actor, "nation disband " + nation + " cascade");
        }
        helper.succeed();
    }
}
