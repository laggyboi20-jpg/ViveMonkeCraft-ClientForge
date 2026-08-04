package laggyboi.vivemonkecraft.client;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// =====================================================================
// EMBEDDED SERVER LOGIC — the monke-server companion, baked into the client
// =====================================================================
//
// A LAN host ("Open to LAN" / Essential invite) runs an INTEGRATED server in
// the same JVM as the client. NeoForge's server-side player events + payload
// handlers work there, so registering them from the client mod gives a LAN host
// all the server-side behaviour WITHOUT anyone installing the separate ServerBuild
// jar:
//
//   * sends ServerConfigPayload on join → guests get AUTHORIZED (the packet is
//     the multiplayer opt-in; without it a guest's mod stays off). Defaults to
//     modEnabled=true, no caps/allowances — a friends LAN game is unrestricted.
//   * receives Real Monke requests → tracks who's shrunk so PlayerHitboxMixin
//     can shrink guests' collision boxes on the host (so tunnels validate).
//   * receives Monke Model toggles → broadcasts them to everyone, and replays
//     the current set to joiners, so all mod users see each other legless.
//
// IMPORTANT: this only runs where an integrated server exists (singleplayer /
// LAN host). On a DEDICATED server the client mod isn't loaded at all, so these
// events never fire there — dedicated servers still need the ServerBuild, which
// preserves the server-side opt-in (Modrinth policy).
//
// LOADER NOTE: on Fabric the C2S RECEIVERS were registered here with
// ServerPlayNetworking.registerGlobalReceiver. NeoForge attaches a payload's
// handler at REGISTRATION time (registrar.playToServer), so those two receivers
// now live in VivemonkecraftClient.registerPayloads() and simply call the
// onRealMonke / onMonkeModel methods below. The join/disconnect wiring stays here.
// =====================================================================
public final class EmbeddedServerLogic {

    /** Guests (and host) who have Real Monke on — read by PlayerHitboxMixin. */
    public static final Set<UUID> realMonkePlayers = ConcurrentHashMap.newKeySet();

    /** Players who have the legless Monke Model on — for join replay. */
    private static final Set<UUID> monkeModelPlayers = ConcurrentHashMap.newKeySet();

    private EmbeddedServerLogic() {}

    public static void register() {
        // Authorize every joiner + replay the current monke-model set to them.
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            // Unrestricted config — a LAN game among friends needs no caps. (Wire
            // format must match ServerConfigPayload: modEnabled, then 10 doubles.)
            VmcNet.sendToPlayer(player, new ServerConfigPayload(
                    true,        // modEnabled
                    0.0,         // maxJumpSpeed (no hard cap)
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,   // allowances unset
                    -1.0, -1.0)); // gravity / air-friction minimums unset

            for (UUID u : monkeModelPlayers) {
                VmcNet.sendToPlayer(player, new MonkeModelS2CPayload(u, true));
            }
        });

        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            UUID id = player.getUUID();
            realMonkePlayers.remove(id);
            if (monkeModelPlayers.remove(id)) {
                MonkeModelS2CPayload off = new MonkeModelS2CPayload(id, false);
                MinecraftServer server = player.getServer();
                if (server != null) {
                    for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                        VmcNet.sendToPlayer(p, off);
                    }
                }
            }
        });
    }

    // Real Monke: track the requester so the hitbox mixin shrinks their box, then
    // refresh dimensions so it applies immediately. Called from the C2S handler
    // registered in VivemonkecraftClient.registerPayloads (integrated server only).
    public static void onRealMonke(Player p, RealMonkeC2SPayload payload) {
        if (!(p instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        if (payload.enabled()) realMonkePlayers.add(id);
        else                   realMonkePlayers.remove(id);
        player.refreshDimensions();
    }

    // Monke Model: track + broadcast to everyone so all mod users see it. Called
    // from the C2S handler registered in VivemonkecraftClient.registerPayloads.
    public static void onMonkeModel(Player p, MonkeModelC2SPayload payload) {
        if (!(p instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        if (payload.enabled()) monkeModelPlayers.add(id);
        else                   monkeModelPlayers.remove(id);
        MonkeModelS2CPayload sync = new MonkeModelS2CPayload(id, payload.enabled());
        MinecraftServer server = player.getServer();
        if (server != null) {
            for (ServerPlayer pp : server.getPlayerList().getPlayers()) {
                VmcNet.sendToPlayer(pp, sync);
            }
        }
    }
}
