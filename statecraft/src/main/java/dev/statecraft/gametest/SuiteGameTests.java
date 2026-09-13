package dev.statecraft.gametest;

import com.mojang.authlib.GameProfile;
import dev.statecraft.StateCraft;
import dev.statecraft.api.CommandLine;
import dev.statecraft.api.CommandTemplate;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.form.FormField;
import dev.statecraft.api.form.FormQuery;
import dev.statecraft.runtime.ServerRuntime;
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
            governance.execute(actor, "chunk claim " + city);

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
}
