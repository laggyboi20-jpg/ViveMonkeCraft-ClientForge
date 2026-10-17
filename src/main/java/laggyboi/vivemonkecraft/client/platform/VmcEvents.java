package laggyboi.vivemonkecraft.client.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
//? if <1.21.6 {
/*import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
*///?}

import java.util.function.BiConsumer;
import java.util.function.Consumer;

// =====================================================================
// LIFECYCLE EVENTS                                           [FORGE BODY]
// =====================================================================
//
// Only the four hooks the mod actually needs.
//
// Forge 1.21.6+ runs EventBus 7: every event class carries its own static BUS
// and you attach with BUS.addListener(...). Before that (1.21.4-1.21.5) game
// events go through MinecraftForge.EVENT_BUS — see listen() below.
//
// Server-side hooks fire for the INTEGRATED server too (singleplayer /
// Open-to-LAN), which is what makes EmbeddedServerLogic work without a separate
// server jar — that must stay true on every loader.
//
// NOTE: client commands are deliberately NOT here. The /vmc tree is built
// generically in VmcCommands and registered by each loader's bootstrap, because
// the brigadier source type differs per loader (FabricClientCommandSource vs
// CommandSourceStack) while the tree itself is source-agnostic.
// =====================================================================
public final class VmcEvents {

    private VmcEvents() {}

    /** End of every client tick — drives the whole mod. */
    public static void onClientTick(Consumer<Minecraft> handler) {
        //? if >=1.21.6 {
        TickEvent.ClientTickEvent.Post.BUS.addListener(
                event -> handler.accept(Minecraft.getInstance()));
        //?} else
        /*listen(TickEvent.ClientTickEvent.Post.class, event -> handler.accept(Minecraft.getInstance()));*/
    }

    /** This client finished joining a world/server. */
    public static void onClientJoin(Runnable handler) {
        //? if >=1.21.6 {
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> handler.run());
        //?} else
        /*listen(ClientPlayerNetworkEvent.LoggingIn.class, event -> handler.run());*/
    }

    /** This client left a world/server. */
    public static void onClientDisconnect(Runnable handler) {
        //? if >=1.21.6 {
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> handler.run());
        //?} else
        /*listen(ClientPlayerNetworkEvent.LoggingOut.class, event -> handler.run());*/
    }

    /** A player joined OUR server (integrated server included). */
    public static void onServerJoin(BiConsumer<ServerPlayer, MinecraftServer> handler) {
        Consumer<PlayerEvent.PlayerLoggedInEvent> listener = event -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                handler.accept(sp, sp.level().getServer());
            }
        };
        //? if >=1.21.6 {
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(listener);
        //?} else
        /*listen(PlayerEvent.PlayerLoggedInEvent.class, listener);*/
    }

    /** A player left OUR server (integrated server included). */
    public static void onServerDisconnect(BiConsumer<ServerPlayer, MinecraftServer> handler) {
        Consumer<PlayerEvent.PlayerLoggedOutEvent> listener = event -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                handler.accept(sp, sp.level().getServer());
            }
        };
        //? if >=1.21.6 {
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(listener);
        //?} else
        /*listen(PlayerEvent.PlayerLoggedOutEvent.class, listener);*/
    }

    //? if <1.21.6 {
    /*// EventBus 6: one game bus; the explicit class avoids lambda type guessing.
    private static <T extends Event> void listen(Class<T> type, Consumer<T> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, listener);
    }
    *///?}
}
