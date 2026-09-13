package dev.statecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.statecraft.StateCraft;
import dev.statecraft.api.BoundaryEdges;
import dev.statecraft.api.MenuRegistry;
import dev.statecraft.api.TerritoryChangedEvent;
import dev.statecraft.api.TerritorySnapshot;
import dev.statecraft.network.SuiteNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = StateCraft.MOD_ID, value = Dist.CLIENT)
public final class ClientHooks {
    public static final KeyMapping MENU = new KeyMapping("key.statecraft.menu", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.statecraft");
    public static final KeyMapping BORDERS = new KeyMapping("key.statecraft.borders", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.statecraft");
    private static TerritorySnapshot territory = TerritorySnapshot.EMPTY;
    private static BoundaryEdges.Mode borderMode = BoundaryEdges.Mode.OFF;
    private static int nextRequest;
    private static final Map<Integer, Consumer<SuiteNetwork.ActionResponse>> RESPONSES = new HashMap<>();
    private static final Map<Integer, Consumer<SuiteNetwork.FormResponse>> FORMS = new HashMap<>();

    private ClientHooks() {}

    public static TerritorySnapshot territory() { return territory; }
    public static BoundaryEdges.Mode borderMode() { return borderMode; }
    public static int nextRequest() { return nextRequest = nextRequest == Integer.MAX_VALUE ? 1 : nextRequest + 1; }
    public static void watch(int id, ManagementScreen screen) { RESPONSES.put(id, screen::reply); }
    static void watchAction(int id, Consumer<SuiteNetwork.ActionResponse> receiver) { RESPONSES.put(id, receiver); }
    public static void forget(int id) { RESPONSES.remove(id); }
    static void watchForm(int id, Consumer<SuiteNetwork.FormResponse> receiver) { FORMS.put(id, receiver); }
    static void forgetForm(int id) { FORMS.remove(id); }

    public static void formReply(SuiteNetwork.FormResponse response) {
        Consumer<SuiteNetwork.FormResponse> receiver = FORMS.remove(response.id());
        if (receiver != null) {
            receiver.accept(response);
        }
    }

    public static void open(String page) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(page.equals("statecraft:main") ? new NavigationScreen()
                    : new ManagementScreen(MenuRegistry.get(page)));
        }
    }

    public static void reply(SuiteNetwork.ActionResponse response) {
        Consumer<SuiteNetwork.ActionResponse> receiver = RESPONSES.remove(response.id());
        if (receiver != null) {
            receiver.accept(response);
        }
    }

    public static void territory(TerritorySnapshot snapshot) {
        territory = snapshot;
        MinecraftForge.EVENT_BUS.post(new TerritoryChangedEvent(snapshot));
    }

    public static void cycleBorders() {
        borderMode = borderMode.next();
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.literal("StateCraft borders: " + borderMode.label()), true);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) {
            return;
        }
        while (MENU.consumeClick()) {
            MenuRegistry.pages().stream().filter(page -> page.id().startsWith("statecraft:"))
                    .findFirst().ifPresent(page -> open(page.id()));
        }
        while (BORDERS.consumeClick()) {
            cycleBorders();
        }
    }

    @SubscribeEvent
    public static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        RESPONSES.clear();
        FORMS.clear();
        territory(TerritorySnapshot.EMPTY);
    }

    @Mod.EventBusSubscriber(modid = StateCraft.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {}
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(MENU);
            event.register(BORDERS);
        }
    }
}
