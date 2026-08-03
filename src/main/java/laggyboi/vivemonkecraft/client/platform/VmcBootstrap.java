package laggyboi.vivemonkecraft.client.platform;

import laggyboi.vivemonkecraft.client.VivemonkecraftClient;
import laggyboi.vivemonkecraft.client.VmcCommands;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

// =====================================================================
// LOADER ENTRY POINT                                       [FABRIC BODY]
// =====================================================================
//
// The ONLY class that knows what a mod entry point looks like on this loader.
// Everything it does is: register the keybind, register the /vmc command with
// this loader's command event, and hand off to the shared VivemonkecraftClient.
//
// The NeoForge and Forge branches replace this file with an @Mod class that does
// the same three things through their own events. Nothing else changes.
// =====================================================================
public final class VmcBootstrap implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        VmcKeybinds.init();

        // Fabric dispatches client commands with FabricClientCommandSource; the
        // shared tree is generic over the source type, so it slots straight in.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(VmcCommands.<FabricClientCommandSource>build()));

        new VivemonkecraftClient().init();
    }
}
