package laggyboi.vivemonkecraft.client.platform;

import laggyboi.vivemonkecraft.client.VivemonkecraftClient;
import laggyboi.vivemonkecraft.client.VmcCommands;
import laggyboi.vivemonkecraft.client.VmcForgeConfigScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
//? if <1.21.6 {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
*///?}

// =====================================================================
// LOADER ENTRY POINT                                         [FORGE BODY]
// =====================================================================
//
// The ONLY class that knows what a mod entry point looks like on this loader.
// Everything it does is: register the keybinds and items, register the /vmc
// command with this loader's command event, wire the config screen, and hand
// off to the shared VivemonkecraftClient.
//
// Config screen: Cloth Config has no Forge build for these versions, so the
// Forge branch ships its own vanilla-widget screen (VmcForgeConfigScreen) and
// offers it through Forge's "Config" button on the Mods list.
//
// The mod is client-only (mods.toml clientSideOnly=true). It must still be able
// to CONNECT to servers that don't have it — see the optional channel in VmcNet.
// =====================================================================
@Mod("vivemonkecraft")
public final class VmcBootstrap {

    public VmcBootstrap() {

        VmcKeybinds.init();
        VmcItems.init();

        // The /vmc command. RegisterClientCommandsEvent (not RegisterCommandsEvent):
        // this is a CLIENT-ONLY mod, so a server-side registration would only exist
        // on the integrated server and /vmc would vanish on dedicated servers.
        //? if >=1.21.6 {
        RegisterClientCommandsEvent.BUS.addListener(event ->
                event.getDispatcher().register(VmcCommands.<CommandSourceStack>build()));
        //?} else {
        /*MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RegisterClientCommandsEvent.class,
                event -> event.getDispatcher().register(VmcCommands.<CommandSourceStack>build()));
        *///?}

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> VmcForgeConfigScreen.create(parent)));

        new VivemonkecraftClient().init();
    }
}
