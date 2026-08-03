package laggyboi.vivemonkecraft.client.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

// =====================================================================
// LIFECYCLE EVENTS                                       [NEOFORGE BODY]
// =====================================================================
//
// Only the four hooks the mod actually needs, registered on the NeoForge GAME
// event bus (as opposed to the mod bus, which VmcBootstrap uses for payload and
// keybind registration).
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
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,
                event -> handler.accept(Minecraft.getInstance()));
    }

    /** This client finished joining a world/server. */
    public static void onClientJoin(Runnable handler) {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class,
                event -> handler.run());
    }

    /** This client left a world/server. */
    public static void onClientDisconnect(Runnable handler) {
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class,
                event -> handler.run());
    }

    /** A player joined OUR server (integrated server included). */
    public static void onServerJoin(BiConsumer<ServerPlayer, MinecraftServer> handler) {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                handler.accept(sp, sp.level().getServer());
            }
        });
    }

    /** A player left OUR server (integrated server included). */
    public static void onServerDisconnect(BiConsumer<ServerPlayer, MinecraftServer> handler) {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                handler.accept(sp, sp.level().getServer());
            }
        });
    }
}
