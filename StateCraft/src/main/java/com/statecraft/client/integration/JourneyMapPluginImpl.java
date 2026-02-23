package com.statecraft.client.integration;

import com.statecraft.StateCraft;
import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.event.ClientEvent;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * JourneyMap plugin implementation for StateCraft.
 * This class is detected by JourneyMap at runtime via the @ClientPlugin annotation.
 *
 * IMPORTANT: This class is ONLY loaded when JourneyMap is present.
 * Do not reference this class directly from non-JourneyMap code.
 */
@ClientPlugin
@ParametersAreNonnullByDefault
public class JourneyMapPluginImpl implements IClientPlugin {

    private IClientAPI api;

    @Override
    public void initialize(IClientAPI jmClientApi) {
        this.api = jmClientApi;
        // Delegate to the safe helper class
        JourneyMapPlugin.onApiReady(api);
    }

    @Override
    public String getModId() {
        return StateCraft.MOD_ID;
    }

    @Override
    public void onEvent(ClientEvent event) {
        // Handle JourneyMap events
        if (event.type == ClientEvent.Type.MAPPING_STOPPED) {
            JourneyMapPlugin.onMappingStopped();
        }
    }
}

