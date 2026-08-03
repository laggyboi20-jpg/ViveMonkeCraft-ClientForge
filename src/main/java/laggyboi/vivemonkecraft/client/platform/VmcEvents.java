package laggyboi.vivemonkecraft.client.platform;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

// =====================================================================
// LIFECYCLE EVENTS                                         [FABRIC BODY]
// =====================================================================
//
// Only the four hooks the mod actually needs. Server-side hooks fire for the
// INTEGRATED server too (singleplayer / Open-to-LAN), which is what makes
// EmbeddedServerLogic work without a separate server jar — that must stay true
// on every loader.
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
        ClientTickEvents.END_CLIENT_TICK.register(handler::accept);
    }

    /** This client finished joining a world/server. */
    public static void onClientJoin(Runnable handler) {
        ClientPlayConnectionEvents.JOIN.register((h, sender, client) -> handler.run());
    }

    /** This client left a world/server. */
    public static void onClientDisconnect(Runnable handler) {
        ClientPlayConnectionEvents.DISCONNECT.register((h, client) -> handler.run());
    }

    /** A player joined OUR server (integrated server included). */
    public static void onServerJoin(BiConsumer<ServerPlayer, MinecraftServer> handler) {
        ServerPlayConnectionEvents.JOIN.register(
                (h, sender, server) -> handler.accept(h.player, server));
    }

    /** A player left OUR server (integrated server included). */
    public static void onServerDisconnect(BiConsumer<ServerPlayer, MinecraftServer> handler) {
        ServerPlayConnectionEvents.DISCONNECT.register(
                (h, server) -> handler.accept(h.player, server));
    }
}
