package dev.statecraft.compat;

import dev.statecraft.api.TerritoryChangedEvent;
import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.Context;
import journeymap.client.api.display.DisplayType;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.event.ClientEvent;
import journeymap.client.api.model.MapPolygon;
import journeymap.client.api.model.ShapeProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;

/**
 * Loaded only by JourneyMap's client plugin discovery; common mod code must not reference this class.
 */
@ClientPlugin
public final class StateCraftJourneyMapPlugin implements IClientPlugin {
    private static final String MOD_ID = "statecraft";
    private static final Logger LOGGER = LoggerFactory.getLogger("StateCraft/JourneyMap");

    private IClientAPI api;
    private TerritoryOverlaySession session;
    private boolean disabled;

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @Override
    public void initialize(IClientAPI clientApi) {
        if (session != null) {
            return;
        }
        api = clientApi;
        session = new TerritoryOverlaySession(new JourneyMapRenderer(clientApi));
        safely(() -> {
            clientApi.subscribe(MOD_ID, EnumSet.of(ClientEvent.Type.MAPPING_STARTED,
                    ClientEvent.Type.MAPPING_STOPPED, ClientEvent.Type.DISPLAY_UPDATE));
            MinecraftForge.EVENT_BUS.register(this);
        });
    }

    @SubscribeEvent
    public void territoriesChanged(TerritoryChangedEvent event) {
        safely(() -> session.update(event.snapshot()));
    }

    @Override
    public void onEvent(ClientEvent event) {
        safely(() -> {
            String dimension = event.dimension == null ? "" : event.dimension.location().toString();
            switch (event.type) {
                case MAPPING_STARTED -> session.mappingStarted(dimension);
                case MAPPING_STOPPED -> session.mappingStopped();
                case DISPLAY_UPDATE -> session.displayUpdated(dimension);
                default -> { }
            }
        });
    }

    private void safely(ApiAction action) {
        if (disabled || session == null) {
            return;
        }
        try {
            action.run();
        } catch (Exception | LinkageError failure) {
            disabled = true;
            try {
                api.removeAll(MOD_ID, DisplayType.Polygon);
            } catch (Exception | LinkageError cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            LOGGER.warn("Optional StateCraft JourneyMap overlays disabled for this client run. "
                    + "Use JourneyMap 5.10.3 / API 1.20-1.9-SNAPSHOT on Forge 1.20.1.", failure);
        }
    }

    @FunctionalInterface
    private interface ApiAction {
        void run() throws Exception;
    }

    private record JourneyMapRenderer(IClientAPI api) implements TerritoryOverlaySession.Renderer {
        @Override
        public void clear() {
            api.removeAll(MOD_ID, DisplayType.Polygon);
        }

        @Override
        public void show(List<TerritoryMapGeometry.Region> regions) throws Exception {
            for (TerritoryMapGeometry.Region region : regions) {
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                        new ResourceLocation(region.dimension()));
                MapPolygon polygon = new MapPolygon(region.corners().stream()
                        .map(point -> new BlockPos(point.x(), 0, point.z())).toList());
                ShapeProperties style = new ShapeProperties()
                        .setStrokeColor(region.color()).setStrokeOpacity(0.9f).setStrokeWidth(1.5f)
                        .setFillColor(region.color()).setFillOpacity(0.2f);
                PolygonOverlay overlay = new PolygonOverlay(MOD_ID, region.id(), dimension, style, polygon);
                overlay.setOverlayGroupName("StateCraft territories");
                overlay.setActiveUIs(EnumSet.of(Context.UI.Fullscreen, Context.UI.Minimap));
                overlay.setLabel(region.label());
                overlay.setTitle(region.description());
                api.show(overlay);
            }
        }
    }
}
