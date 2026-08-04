package laggyboi.vivemonkecraft.client;

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
// NETWORKING  (Forge)
// =====================================================================
//
// The PAYLOAD CLASSES themselves are pure vanilla (CustomPacketPayload +
// StreamCodec) and are shared verbatim with the Fabric and NeoForge branches —
// only registration and send differ, which is all this class hides.
//
// Forge does NOT use NeoForge's RegisterPayloadHandlersEvent / PayloadRegistrar.
// It has its own builder: ChannelBuilder -> payloadChannel() -> play() ->
// clientbound()/serverbound() -> addMain(type, codec, handler) -> build().
// Everything goes on ONE named channel ("vivemonkecraft:main").
//
// ---------------------------------------------------------------------
// CONTRACT (identical on every loader — the mod depends on all of it):
//   * All payloads are OPTIONAL. The client MUST connect to servers that have
//     none of these channels: "no ServerConfigPayload arrived" is exactly how
//     the mod detects an un-opted-in server and stays disabled. On Forge that is
//     ChannelBuilder.optional() below — WITHOUT it the login handshake REJECTS
//     any server lacking the companion mod, which would break the whole opt-in
//     policy and make the mod unusable on vanilla servers. Do not remove it.
//   * addMain() runs handlers on the MAIN thread (as opposed to add(), the
//     network thread) — so handlers can touch game state directly.
//   * canSendToServer() returns false (never throws) when the other end has no
//     receiver. Forge negotiates per CHANNEL, so isRemotePresent answers "does
//     the other side have our channel", which is the companion-present question.
// ---------------------------------------------------------------------
//
// PER-VERSION RISK: the payload-channel API (ChannelBuilder/PayloadProtocol/
// PayloadFlow) mirrors vanilla's payload networking (Minecraft 1.20.5+, Forge
// ~50.x) and is present across Forge 54.x -> 65.x, but method signatures may have
// shifted somewhere in that range. If a build fails here, this class is the place
// to reconcile against that version's net.minecraftforge.network API.
// =====================================================================
public final class VmcNet {

    private VmcNet() {}

    private static final Identifier CHANNEL_NAME =
            Identifier.fromNamespaceAndPath("vivemonkecraft", "main");

    private static final int PROTOCOL_VERSION = 1;

    private static Channel<CustomPacketPayload> channel;

    /** Registration surface handed to {@link #register}. Both directions register
     *  the payload TYPE and its handler in one call. */
    public interface Registrar {
        <T extends CustomPacketPayload> void clientbound(
                CustomPacketPayload.Type<T> id,
                StreamCodec<? super FriendlyByteBuf, T> codec,
                Consumer<T> handler);

        <T extends CustomPacketPayload> void serverbound(
                CustomPacketPayload.Type<T> id,
                StreamCodec<? super FriendlyByteBuf, T> codec,
                BiConsumer<ServerPlayer, T> handler);

        /** C2S payload with NO handler on this side — the type still has to be
         *  registered so the codec exists for sending. Answered only by the
         *  separate dedicated-server companion. */
        <T extends CustomPacketPayload> void serverboundNoHandler(
                CustomPacketPayload.Type<T> id,
                StreamCodec<? super FriendlyByteBuf, T> codec);
    }

    /** Called once from the mod init with every payload the mod uses. */
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
            if (toServer == null) toServer = protocol.serverbound();
            toServer = toServer.addMain(id, cast(codec), (payload, context) -> { });
        }

        Channel<CustomPacketPayload> finish() {
            if (toServer != null) return toServer.build();
            if (toClient != null) return toClient.build();
            return protocol.clientbound().build();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static <T> StreamCodec<RegistryFriendlyByteBuf, T> cast(
                StreamCodec<? super FriendlyByteBuf, T> codec) {
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
