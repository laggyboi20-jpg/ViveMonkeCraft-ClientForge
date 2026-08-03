package laggyboi.vivemonkecraft.client.platform;

import laggyboi.vivemonkecraft.client.VivemonkecraftClient;
import laggyboi.vivemonkecraft.client.VmcCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.fml.common.Mod;

// =====================================================================
// LOADER ENTRY POINT                                         [FORGE BODY]
// =====================================================================
//
// The ONLY class that knows what a mod entry point looks like on this loader.
// Everything it does is: register the keybind, register the /vmc command with
// this loader's command event, and hand off to the shared VivemonkecraftClient.
//
// The Fabric branch replaces this file with a ClientModInitializer and the
// NeoForge branch with a @Mod(dist = CLIENT) constructor. Nothing else changes
// between branches.
//
// NO CONFIG SCREEN HERE: Cloth Config has no Forge build for Minecraft 26.x, so
// VmcConfigScreen is excluded from compilation on this branch (see build.gradle).
// Forge users configure via config/vivemonkecraft.properties, "/vmc reload" and
// "/vmc set <setting> <value>" (which tab-completes every setting name).
//
// Client-only: unlike NeoForge there is no dist attribute on Forge's @Mod, so
// this mod is kept off servers by mods.toml (displayTest + CLIENT-side deps) and
// by simply never touching server-only code paths. It must still be able to
// CONNECT to servers that don't have it — see the optional-channel contract in
// VmcNet.
// =====================================================================
@Mod("vivemonkecraft")
public final class VmcBootstrap {

    public VmcBootstrap() {

        VmcKeybinds.init();

        // MUST be RegisterClientCommandsEvent, not RegisterCommandsEvent — this is
        // a client-only mod, so a server-side registration would leave /vmc working
        // in singleplayer but dead on dedicated servers. Forge dispatches client
        // commands with CommandSourceStack; the shared tree is generic over the
        // source type, so it slots straight in.
        RegisterClientCommandsEvent.BUS.addListener(event ->
                event.getDispatcher().register(VmcCommands.<CommandSourceStack>build()));

        new VivemonkecraftClient().init();
    }
}
