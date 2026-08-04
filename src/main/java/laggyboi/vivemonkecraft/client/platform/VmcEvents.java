package laggyboi.vivemonkecraft.client.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

// =====================================================================
// LIFECYCLE EVENTS                                           [FORGE BODY]
// =====================================================================
//
// Only the four hooks the mod actually needs.
//
// Forge 26.x runs EventBus 7: every event class carries its own static BUS and
// you attach with BUS.addListener(...) — there is no MinecraftForge.EVENT_BUS
// .register(this) / @SubscribeEvent scanning needed for these.
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
        TickEvent.ClientTickEvent.Post.BUS.addListener(
                event -> handler.accept(Minecraft.getInstance()));
    }

    /** This client finished joining a world/server. */
    public static void onClientJoin(Runnable handler) {
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> handler.run());
    }

    /** This client left a world/server. */
    public static void onClientDisconnect(Runnable handler) {
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> handler.run());
    }

    /** A player joined OUR server (integrated server included). */
    public static void onServerJoin(BiConsumer<ServerPlayer, MinecraftServer> handler) {
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                handler.accept(sp, sp.level().getServer());
            }
        });
    }

    /** A player left OUR server (integrated server included). */
    public static void onServerDisconnect(BiConsumer<ServerPlayer, MinecraftServer> handler) {
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer sp) {
                handler.accept(sp, sp.level().getServer());
            }
        });
    }
}
