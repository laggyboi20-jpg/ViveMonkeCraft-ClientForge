package laggyboi.vivemonkecraft.client.platform;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

// =====================================================================
// NETWORKING                                               [FABRIC BODY]
// =====================================================================
//
// The PAYLOAD CLASSES themselves are pure vanilla (CustomPacketPayload +
// StreamCodec) and are shared verbatim across all three loader branches — only
// registration and send differ, which is all this class hides.
//
// ---------------------------------------------------------------------
// CONTRACT (identical on every loader — the port depends on all of it):
//
//   * All payloads are OPTIONAL. The client MUST be able to connect to a server
//     that has none of these channels: "no ServerConfigPayload arrived" is
//     exactly how the mod detects an un-opted-in server and stays disabled.
//     On NeoForge/Forge this means PayloadRegistrar#optional() — without it the
//     loader treats channels as required and the handshake rejects the
//     connection, silently destroying the server opt-in policy.
//   * Handlers run on the MAIN thread (client thread / server thread), so
//     handler bodies may touch game state directly.
//   * canSendToServer() must return false — not throw — when no receiver exists
//     on the other end.
// ---------------------------------------------------------------------
// =====================================================================
public final class VmcNet {

    private VmcNet() {}

    /**
     * Registration surface handed to {@link #register}. Implemented per loader.
     * Both directions register the payload TYPE and its handler in one call so no
     * branch can register a codec and forget the handler.
     */
    public interface Registrar {

        /** Server -> client payload plus the client-side handler. */
        <T extends CustomPacketPayload> void clientbound(
                CustomPacketPayload.Type<T> id,
                StreamCodec<? super FriendlyByteBuf, T> codec,
                Consumer<T> handler);

        /** Client -> server payload plus the server-side handler (sender, payload). */
        <T extends CustomPacketPayload> void serverbound(
                CustomPacketPayload.Type<T> id,
                StreamCodec<? super FriendlyByteBuf, T> codec,
                BiConsumer<ServerPlayer, T> handler);

        /**
         * Client -> server payload with NO handler on this side. Used by the client
         * mod for packets only the separate server companion answers; the type still
         * has to be registered so the codec exists for sending.
         */
        <T extends CustomPacketPayload> void serverboundNoHandler(
                CustomPacketPayload.Type<T> id,
                StreamCodec<? super FriendlyByteBuf, T> codec);
    }

    /** Called once from the bootstrap with every payload the mod uses. */
    public static void register(Consumer<Registrar> block) {
        block.accept(new FabricRegistrar());
    }

    private static final class FabricRegistrar implements Registrar {

        @Override
        public <T extends CustomPacketPayload> void clientbound(
                CustomPacketPayload.Type<T> id,
                StreamCodec<? super FriendlyByteBuf, T> codec,
                Consumer<T> handler) {
            PayloadTypeRegistry.clientboundPlay().register(id, codec);
            // Fabric runs play-phase receivers on the client thread already.
            ClientPlayNetworking.registerGlobalReceiver(id,
                    (payload, context) -> handler.accept(payload));
        }

        @Override
        public <T extends CustomPacketPayload> void serverbound(
                CustomPacketPayload.Type<T> id,
                StreamCodec<? super FriendlyByteBuf, T> codec,
                BiConsumer<ServerPlayer, T> handler) {
            PayloadTypeRegistry.serverboundPlay().register(id, codec);
            ServerPlayNetworking.registerGlobalReceiver(id,
                    (payload, context) -> handler.accept(context.player(), payload));
        }

        @Override
        public <T extends CustomPacketPayload> void serverboundNoHandler(
                CustomPacketPayload.Type<T> id,
                StreamCodec<? super FriendlyByteBuf, T> codec) {
            PayloadTypeRegistry.serverboundPlay().register(id, codec);
        }
    }

    // -----------------------------------------------------------------------
    // Sending
    // -----------------------------------------------------------------------

    /** Client -> server. Caller should gate on {@link #canSendToServer}. */
    public static void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    /** False when the other end has no receiver (e.g. a server without the companion). */
    public static boolean canSendToServer(CustomPacketPayload.Type<?> id) {
        return ClientPlayNetworking.canSend(id);
    }

    /** Server -> one client. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    /** Server -> every connected client. */
    public static void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }
}
