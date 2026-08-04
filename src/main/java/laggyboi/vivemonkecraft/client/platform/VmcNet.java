package laggyboi.vivemonkecraft.client.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;
import net.minecraftforge.network.payload.PayloadProtocol;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

// =====================================================================
// NETWORKING                                                [FORGE BODY]
// =====================================================================
//
// The PAYLOAD CLASSES themselves are pure vanilla (CustomPacketPayload +
// StreamCodec) and are shared verbatim across all three loader branches — only
// registration and send differ, which is all this class hides.
//
// Forge 26.x does NOT use NeoForge's RegisterPayloadHandlersEvent/PayloadRegistrar.
// It has its own builder: ChannelBuilder -> payloadChannel() -> play() ->
// clientbound()/serverbound() -> addMain(type, codec, handler) -> build().
// Everything goes on ONE named channel ("vivemonkecraft:main").
//
// ---------------------------------------------------------------------
// CONTRACT (identical on every loader — the port depends on all of it):
//
//   * All payloads are OPTIONAL. The client MUST be able to connect to a server
//     that has none of these channels: "no ServerConfigPayload arrived" is
//     exactly how the mod detects an un-opted-in server and stays disabled.
//     >>> On Forge this is ChannelBuilder.optional() below. WITHOUT IT Forge
//     >>> treats the channel as required and the login handshake REJECTS any
//     >>> server that doesn't have the companion mod — which would silently
//     >>> destroy the server opt-in policy and make the mod unusable on vanilla
//     >>> servers. Do not remove it.
//   * Handlers run on the MAIN thread — that is exactly what addMain() means
//     (as opposed to add(), which runs on the network thread).
//   * canSendToServer() must return false — not throw — when no receiver exists
//     on the other end. Forge negotiates per CHANNEL rather than per payload, so
//     isRemotePresent answers "does the other side have our channel at all",
//     which is precisely the companion-present question the mod asks.
// ---------------------------------------------------------------------
// =====================================================================
public final class VmcNet {

    private VmcNet() {}

    private static final Identifier CHANNEL_NAME =
            Identifier.fromNamespaceAndPath("vivemonkecraft", "main");

    // Bumped only if the wire format of a payload changes.
    private static final int PROTOCOL_VERSION = 1;

    private static Channel<CustomPacketPayload> channel;

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

    /** Called once from the shared client init with every payload the mod uses. */
    public static void register(Consumer<Registrar> block) {
        PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> protocol =
                ChannelBuilder.named(CHANNEL_NAME)
                        .networkProtocolVersion(PROTOCOL_VERSION)
                        .optional()          // see the contract note above
                        .payloadChannel()
                        .play();

        ForgeRegistrar reg = new ForgeRegistrar(protocol);
        block.accept(reg);
        channel = reg.finish();
    }

    private static final class ForgeRegistrar implements Registrar {

        private final PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> protocol;
        private PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> toClient;
        private PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> toServer;

        ForgeRegistrar(PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> protocol) {
            this.protocol = protocol;
        }

        @Override
        public <T extends CustomPacketPayload> void clientbound(
                CustomPacketPayload.Type<T> id,
                StreamCodec<? super FriendlyByteBuf, T> codec,
                Consumer<T> handler) {
            if (toClient == null) toClient = protocol.clientbound();
            toClient = toClient.addMain(id, cast(codec),
                    (payload, context) -> handler.accept(payload));
        }

        @Override
        public <T extends CustomPacketPayload> void serverbound(
                CustomPacketPayload.Type<T> id,
                StreamCodec<? super FriendlyByteBuf, T> codec,
                BiConsumer<ServerPlayer, T> handler) {
            if (toServer == null) toServer = protocol.serverbound();
            toServer = toServer.addMain(id, cast(codec), (payload, context) -> {
                ServerPlayer sender = context.getSender();
                if (sender != null) handler.accept(sender, payload);
            });
        }

        @Override
        public <T extends CustomPacketPayload> void serverboundNoHandler(
                CustomPacketPayload.Type<T> id,
                StreamCodec<? super FriendlyByteBuf, T> codec) {
            // Forge has no "type only" registration — register a no-op handler.
            // These payloads are answered only by the separate server companion mod,
            // so nothing on this side should ever receive them anyway.
            if (toServer == null) toServer = protocol.serverbound();
            toServer = toServer.addMain(id, cast(codec), (payload, context) -> { });
        }

        Channel<CustomPacketPayload> finish() {
            if (toServer != null) return toServer.build();
            if (toClient != null) return toClient.build();
            return protocol.clientbound().build();
        }

        // The shared API takes StreamCodec<? super FriendlyByteBuf, T> (what the
        // payload classes declare); the play protocol wants StreamCodec<RegistryFriendlyByteBuf, T>.
        // FriendlyByteBuf is a supertype of RegistryFriendlyByteBuf, so any codec
        // accepting the former also accepts the latter — the cast is sound.
        @SuppressWarnings({"unchecked", "rawtypes"})
        private static <T> StreamCodec<RegistryFriendlyByteBuf, T> cast(
                StreamCodec<? super FriendlyByteBuf, T> codec) {
            // Via raw: the wildcard capture can't be converted directly.
            return (StreamCodec<RegistryFriendlyByteBuf, T>) (StreamCodec) codec;
        }
    }

    // -----------------------------------------------------------------------
    // Sending
    // -----------------------------------------------------------------------

    /** Client -> server. Caller should gate on {@link #canSendToServer}. */
    public static void sendToServer(CustomPacketPayload payload) {
        if (channel == null) return;
        channel.send(payload, PacketDistributor.SERVER.noArg());
    }

    /** False when the other end has no receiver (e.g. a server without the companion). */
    public static boolean canSendToServer(CustomPacketPayload.Type<?> id) {
        if (channel == null) return false;
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null) return false;
        return channel.isRemotePresent(listener.getConnection());
    }

    /** Server -> one client. */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (channel == null) return;
        channel.send(payload, PacketDistributor.PLAYER.with(player));
    }

    /** Server -> every connected client. */
    public static void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
        if (channel == null || server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            channel.send(payload, PacketDistributor.PLAYER.with(p));
        }
    }
}
