package laggyboi.vivemonkecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.lwjgl.glfw.GLFW;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

// =====================================================================
// CLIENT ENTRY POINT  —  NeoForge port of the 1.21.4 Fabric client
// =====================================================================
//
// This is the NEOFORGE flavour of the exact same client the Fabric 1.21.4 release
// ships: same keybind, same /vmc command, same auto-start / VR watcher tick, same
// networking. Only the LOADER glue differs — Fabric's ClientModInitializer +
// ClientPlayNetworking + PayloadTypeRegistry + ClientTickEvents are replaced by
// NeoForge's @Mod constructor + PayloadRegistrar + game/mod event buses. The mod
// LOGIC below (fields, onEndTick, applyEnabled, the command tree, presets) is the
// Fabric behaviour verbatim.
//
// dist = Dist.CLIENT: client-only mod (Fabric said "environment": "client"). It
// must still be able to CONNECT to servers that don't have it — every payload is
// registered optional() below so the handshake never rejects a vanilla server.
// =====================================================================
@Mod("vivemonkecraft")
public class VivemonkecraftClient {

    // Is gorilla locomotion currently on? Static so the mixins can read it.
    // Starts OFF — auto-enabled once when the player joins a world (see onEndTick).
    private static boolean enabled = false;

    // Whether we have already auto-enabled this world session.
    // Reset to false on disconnect so we auto-start again next time a world loads.
    private boolean autoStarted = false;

    // VR-active state last tick — used to edge-trigger auto enable/disable so the mod
    // turns on when a headset appears and off when it goes away.
    private boolean vrWasActive = false;

    // Ticks to wait after joining before auto-enabling the mod.
    // This gives the server's config packet time to arrive so we know whether the
    // server runs the companion mod (monke-server) BEFORE we decide.
    // 10 ticks = 500 ms — plenty of time even over a WAN connection.
    private static final int PACKET_WAIT_TICKS = 10;

    // Counts ticks since the last JOIN event.  Incremented each tick until
    // autoStarted becomes true, then ignored.  Reset to 0 on JOIN and DISCONNECT.
    private int ticksSinceJoin = 0;

    // Whether the "this server doesn't run the ViveMonke server mod" notice has
    // already been shown this connection (shown at most once per join).
    private boolean warnedNoServerMod = false;

    // Last applied "Real Monke" state — re-applies the collision box / server
    // shrink when the setting (or the mod's enabled state) flips mid-game.
    private boolean lastRealMonke = false;

    // Last announced "monke model" (legless look) state.
    private boolean lastMonkeModel = false;

    // Whether we last told a DEDICATED server we're gripping (for the no-fall-damage
    // slide). Tracks state so we send one "released" packet when gripping ends rather
    // than spamming it. Singleplayer/LAN host doesn't use this (handled in the handler).
    private boolean wallSlideSent = false;

    // Whether a GUI screen was open last tick — used to drop grips exactly once
    // when a screen opens (inventory/chat/settings must not move the player).
    private boolean wasInGui = false;

    // The dimension the player was in last tick. Changing dimension (e.g. to the
    // Nether) rebuilds the player entity, which recomputes its collision box ONCE
    // and then caches it — so the Real Monke shrink silently reverts to full size
    // until something forces a refresh (the in-game "toggle off+on" fix). We watch
    // for the change and re-apply automatically.
    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> lastDimension = null;

    // The player ENTITY instance last tick. Respawn (and dimension change) replace it
    // with a new LocalPlayer; when that happens we re-apply Real Monke / monke model,
    // otherwise the rebuilt entity reverts to full 2-block height after dying.
    private net.minecraft.client.player.LocalPlayer lastPlayerRef = null;

    // The physics handler.
    private GorillaLocomotionHandler handler;

    // Toggle keybind — UNBOUND by default (no key assigned out of the box).
    // To toggle via Vivecraft radial menu: go to VR Settings -> Radial Menu and assign
    // the "ViveMonkeCraft: Toggle" keybind to a radial slot.
    // To toggle via keyboard: rebind in Options -> Controls -> Miscellaneous.
    // Created eagerly so the tick code can reference it; REGISTERED in
    // RegisterKeyMappingsEvent (NeoForge's equivalent of Fabric's KeyBindingHelper).
    private final KeyMapping toggleKey = new KeyMapping(
            "key.vivemonkecraft.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            KeyMapping.Category.MISC
    );

    // Forge instantiates this once via a no-arg @Mod constructor.
public VivemonkecraftClient() {

        handler = new GorillaLocomotionHandler();

        // Register the per-frame hand marker renderer (replaces old particles).
        HandMarkerRenderer.register();

        // Register the camera stabilization vignette (motion-sickness reduction).
        CameraStabilizationRenderer.register();

        // Load the editable config at startup (creates it the first time).
        MovementConfig.load();

        // Networking: register every payload + handler on the Forge channel (VmcNet).
        registerPayloads();

        // Keybind registration is a mod-bus event (Forge EventBus 7 static BUS).
        RegisterKeyMappingsEvent.BUS.addListener(this::registerKeyMappings);

        // Config screen: Forge's OWN "Config" button in the Mods list. Built from pure
        // VANILLA widgets (VmcForgeConfigScreen), so unlike the old Cloth screen it is
        // ALWAYS available -- no optional dependency, no presence check.
        // NOTE (per-version): ConfigScreenHandler.ConfigScreenFactory is the Forge
        // 1.19-1.21 config-button API; if 26.x renamed it, this is the only call to fix.
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> VmcForgeConfigScreen.create(parent)));

        // Game-bus events: tick, join, disconnect, and the /vmc client command.
        TickEvent.ClientTickEvent.Post.BUS.addListener(
                e -> onEndTick(Minecraft.getInstance()));
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(e -> onClientJoin());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> onClientDisconnect());
        RegisterClientCommandsEvent.BUS.addListener(this::registerCommands);

        // Embedded server logic (integrated / LAN host plays the monke-server role).
        EmbeddedServerLogic.register();
    }

    // -----------------------------------------------------------------------
    // Networking registration (Forge channel via VmcNet)
    // -----------------------------------------------------------------------

    // All payloads are OPTIONAL (VmcNet builds the channel with .optional()), which is
    // what lets the client still connect to servers that have NONE of these channels --
    // "no ServerConfigPayload arrived" is exactly how the mod detects an un-opted-in
    // server and stays disabled. Handlers run on the MAIN thread (VmcNet uses addMain).
    private void registerPayloads() {
        VmcNet.register(reg -> {
            // S2C -- the server companion (or our integrated server) sends these.
            reg.clientbound(ServerConfigPayload.ID, ServerConfigPayload.STREAM_CODEC,
                    this::onServerConfig);
            reg.clientbound(MonkeModelS2CPayload.ID, MonkeModelS2CPayload.STREAM_CODEC,
                    payload -> MonkeModelClientSet.set(payload.player(), payload.enabled()));

            // C2S answered by the integrated server (EmbeddedServerLogic) when hosting.
            reg.serverbound(RealMonkeC2SPayload.ID, RealMonkeC2SPayload.STREAM_CODEC,
                    EmbeddedServerLogic::onRealMonke);
            reg.serverbound(MonkeModelC2SPayload.ID, MonkeModelC2SPayload.STREAM_CODEC,
                    EmbeddedServerLogic::onMonkeModel);

            // C2S answered ONLY by the separate dedicated-server companion. The TYPE must
            // still be registered so the client can send it (and canSend sees a channel);
            // the integrated server needs no handler.
            reg.serverboundNoHandler(WallSlideC2SPayload.ID, WallSlideC2SPayload.STREAM_CODEC);
            reg.serverboundNoHandler(MagmaTouchC2SPayload.ID, MagmaTouchC2SPayload.STREAM_CODEC);
        });
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(toggleKey);
    }

    // Client handler for the server companion's config packet. Receiving this packet
    // IS the multiplayer authorization: without it the mod refuses to enable on a
    // dedicated server (see serverAuthorized). Runs on the main thread (enqueueWork).
    private void onServerConfig(ServerConfigPayload payload) {
        ServerLimits.apply(payload);
        VmcDebugLog.event("NET", "← ServerConfig modEnabled=" + payload.modEnabled()
                + " (server companion present → authorized)");

        Minecraft client = Minecraft.getInstance();
        if (!payload.modEnabled()) {
            // Server banned the mod — force-disable immediately, even if auto-start
            // already fired before the packet arrived.
            if (enabled) {
                enabled = false;
                handler.onDisable(client);
            }
            if (client.player != null) {
                client.gui.setOverlayMessage(
                        Component.literal("§e[ViveMonkeCraft] §cDisabled by server"),
                        false);
            }
        } else if (autoStarted && !enabled) {
            // Authorization arrived AFTER the auto-start window closed (slow
            // connection) — turn the mod on now.
            applyEnabled(true);
        }
    }

    // -----------------------------------------------------------------------
    // Connection lifecycle (game bus)
    // -----------------------------------------------------------------------

    // Reset state when the player joins a world so the tick handler will fire
    // applyEnabled(true) once the player entity is ready (after PACKET_WAIT_TICKS).
    private void onClientJoin() {
        autoStarted       = false;
        ticksSinceJoin    = 0;
        warnedNoServerMod = false;
        lastRealMonke     = false;   // re-apply the shrink after the join settles
        lastMonkeModel    = false;   // re-announce the legless look too
    }

    // On disconnect: turn off gorilla locomotion, clear server limits, and reset
    // counters so the next world join auto-starts again after the grace window.
    private void onClientDisconnect() {
        Minecraft client = Minecraft.getInstance();
        autoStarted       = false;
        ticksSinceJoin    = 0;
        warnedNoServerMod = false;
        lastRealMonke     = false;
        lastMonkeModel    = false;
        wasInGui          = false;
        lastDimension     = null;
        wallSlideSent     = false;   // next world starts with no pending grip state
        vrWasActive       = false;   // re-evaluate VR presence fresh next world
        MonkeModelClientSet.clear(); // stale legless flags don't carry over
        ServerLimits.reset();        // clear caps + authorization for the next world
        if (enabled) {
            enabled = false;
            handler.onDisable(client);
        }
    }

    // -----------------------------------------------------------------------
    // /vmc client command
    // -----------------------------------------------------------------------
    //   /vmc               -> toggle on/off
    //   /vmc on|off        -> set explicitly
    //   /vmc reload        -> re-read the config file
    //   /vmc set <k> <v>   -> set a config field by name
    //   /vmc gravity <0-1> -> set the gravity multiplier (operator only)
    private void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(literal("vmc")
            .executes(ctx -> { toggle(); return 1; })
            .then(literal("on").executes(ctx -> { applyEnabled(true); return 1; }))
            .then(literal("off").executes(ctx -> { applyEnabled(false); return 1; }))
            .then(literal("reload").executes(ctx -> { reloadConfig(); return 1; }))
            .then(literal("set")
                .then(argument("setting", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        String typed = builder.getRemainingLowerCase();
                        for (String n : MovementConfig.settingNames()) {
                            if (n.toLowerCase().startsWith(typed)) builder.suggest(n);
                        }
                        return builder.buildFuture();
                    })
                    .then(argument("value", StringArgumentType.word())
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            String name  = StringArgumentType.getString(ctx, "setting");
                            String value = StringArgumentType.getString(ctx, "value");
                            String result = MovementConfig.setByName(name, value);
                            if (mc.player != null) {
                                mc.gui.setOverlayMessage(Component.literal(
                                    result != null
                                        ? "§e[ViveMonkeCraft] §f" + result
                                        : "§c[ViveMonkeCraft] §fUnknown setting or bad value: "
                                            + name + " " + value),
                                    false);
                            }
                            return result != null ? 1 : 0;
                        })
                    )
                )
            )
            .then(literal("gravity")
                .then(argument("level", DoubleArgumentType.doubleArg(0.0, 1.0))
                    .executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        // Op-lock: requires permission level 2 (operator).
                        // In singleplayer the host is always level 4, so this always works.
                        // On a server it reflects what the server reported to the client.
                        if (mc.player == null || !mc.player.permissions().hasPermission(
                                    net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
                            if (mc.player != null) {
                                mc.gui.setOverlayMessage(
                                    Component.literal("§c[ViveMonkeCraft] §fNeed operator access to change gravity"),
                                    false
                                );
                            }
                            return 0;
                        }
                        double level = DoubleArgumentType.getDouble(ctx, "level");
                        MovementConfig.gravityMultiplier = level;
                        MovementConfig.save();
                        mc.gui.setOverlayMessage(
                            Component.literal("§e[ViveMonkeCraft] §fGravity: §b" + level
                                + (level == 0.0 ? " §7(zero-G)" : level == 1.0 ? " §7(normal)" : "")),
                            false
                        );
                        return 1;
                    })
                )
            )
        );
    }

    public static boolean isEnabled() {
        return enabled;
    }

    // Client -> server helpers (NeoForge). canSend mirrors Fabric's
    // ClientPlayNetworking.canSend: false when the other end has no receiver.
    private static boolean canSend(CustomPacketPayload.Type<?> id) {
        return VmcNet.canSendToServer(id);
    }

    // -----------------------------------------------------------------------
    // Per-tick: keybind handling + physics
    // -----------------------------------------------------------------------

    private void onEndTick(Minecraft client) {
        // Auto-start: wait PACKET_WAIT_TICKS ticks after joining so the server's
        // config packet has time to arrive before we decide to enable.
        // Singleplayer enables unconditionally. On a dedicated server, no packet =
        // no companion mod = the mod STAYS OFF (server-side opt-in, Modrinth policy);
        // the player gets a one-time chat notice explaining why.
        // Done here rather than in JOIN because on QuestCraft the player entity
        // may not be fully constructed yet at the moment JOIN fires.
        if (!autoStarted && client.player != null) {
            ticksSinceJoin++;
            if (ticksSinceJoin >= PACKET_WAIT_TICKS) {
                autoStarted = true;
                // Actually enabling is handled by the VR watcher below (it requires a
                // headset). Here we only warn once if the server hasn't opted in.
                if (!serverAuthorized(client) && !warnedNoServerMod) {
                    warnedNoServerMod = true;
                    client.player.displayClientMessage(Component.literal(
                        "§e[ViveMonkeCraft] §cThis server doesn't run the monke-server "
                        + "companion mod, so gorilla locomotion is disabled here. "
                        + "§7(Server admins: install the ViveMonke server mod to allow it.)"),
                        false);
                }
            }
        }

        // VR PRESENCE DRIVES THE MOD: auto-ON when a headset becomes active (and the
        // server allows it), auto-OFF when VR goes away — so it's inert in plain desktop
        // Minecraft and springs to life in VR. Edge-triggered (acts only on the VR
        // on/off transition), so you can still manually toggle it off within a VR
        // session without it snapping back on every tick.
        boolean vrActive = VivecraftBridge.isVrActive();
        if (autoStarted && client.player != null) {
            if (vrActive && !vrWasActive) {
                VmcDebugLog.event("VR", "headset ACTIVE");
                if (!enabled && serverAuthorized(client) && ServerLimits.modEnabled) applyEnabled(true);
            } else if (!vrActive && vrWasActive) {
                VmcDebugLog.event("VR", "headset INACTIVE");
                if (enabled) applyEnabled(false);
            }
            vrWasActive = vrActive;
        }

        // (Teleport is blocked directly in TeleportTrackerMixin by cancelling its
        // doProcess while the mod is on and allowTeleport is off — the setTeleportOverride
        // route proved unreliable, isTeleportEnabled ignored it on QuestCraft.)

        // Toggle on each press of the keybind (keyboard or Vivecraft radial menu).
        while (toggleKey.consumeClick()) {
            toggle();
        }

        // Dimension change (Nether/End portal, /execute in, etc.) AND respawn after
        // death both REPLACE the player entity, which recomputes its collision box
        // once and caches it — so our Real Monke shrink silently reverts (the model
        // springs back to full 2-block height). Force the watchers below to re-apply
        // by clearing their "last" state — same effect as toggling the option off+on.
        // A dimension change is caught by the dimension key; a same-dimension respawn
        // is caught by the player INSTANCE changing (a brand-new LocalPlayer object).
        if (client.player != null) {
            var dim = client.player.level().dimension();
            if ((lastDimension != null && !lastDimension.equals(dim))
                    || client.player != lastPlayerRef) {
                lastRealMonke  = false;
                lastMonkeModel = false;
            }
            lastDimension = dim;
            lastPlayerRef = client.player;
        }

        // Apply / remove Real Monke when the setting or the mod's enabled state
        // flips mid-game (config screen save, /vmc set, preset, toggle).
        boolean monke = enabled && MovementConfig.realMonke;
        if (monke != lastRealMonke && client.player != null) {
            lastRealMonke = monke;
            client.player.refreshDimensions();
            applyRealMonkeScale(client, monke);
        }

        // Announce / retract the monke model (legless look): render it locally for
        // ourselves immediately, and tell the server (dedicated monke-server OR our
        // own integrated/LAN server via the embedded logic) so it broadcasts to all
        // other mod users. canSend() is true whenever a receiver exists on the other
        // end — including our own integrated server when hosting LAN.
        boolean model = enabled && MovementConfig.monkeModel;
        if (model != lastMonkeModel && client.player != null) {
            lastMonkeModel = model;
            MonkeModelClientSet.set(client.player.getUUID(), model);
            if (canSend(MonkeModelC2SPayload.ID)) {
                VmcNet.sendToServer(new MonkeModelC2SPayload(model));
            }
        }

        boolean grippingNow = false;
        boolean magmaNow    = false;
        if (enabled && client.player != null && !client.isPaused()
                && ServerLimits.modEnabled && serverAuthorized(client)) {
            // GUI GUARD: while ANY screen is open (inventory, chat, settings, ...)
            // gorilla locomotion must not move the player. Drop all grips the
            // moment a screen opens, and stay fully inert until it closes — the
            // next tick after closing resumes normally.
            if (client.screen != null) {
                if (!wasInGui) {
                    wasInGui = true;
                    handler.onGuiPause(client);
                }
            } else {
                wasInGui = false;
                handler.tick(client);
                grippingNow = handler.isGripping();
                magmaNow    = handler.isTouchingMagma();
            }
        }

        // No-fall-damage slide, dedicated-server half: keep the companion mod's fall
        // suppression alive while we grip. (Singleplayer/LAN host resets the integrated
        // server player inside the handler, so this only fires on a remote server.)
        syncWallSlide(client, grippingNow);

        // Magma touch, dedicated-server half: ask the companion mod to hurt us while a
        // hand grips a magma block. (Singleplayer/LAN hurts the integrated player in the
        // handler.) Sent each touching tick; hurt invulnerability frames throttle it.
        if (magmaNow && !client.hasSingleplayerServer()
                && canSend(MagmaTouchC2SPayload.ID)) {
            VmcNet.sendToServer(MagmaTouchC2SPayload.INSTANCE);
        }
    }

    // Sends one keepalive per gripping tick and a single "released" packet when
    // gripping ends. No-op in singleplayer/LAN host (handled in the handler) and when
    // no receiver exists on the other end (server without the companion mod).
    private void syncWallSlide(Minecraft client, boolean gripping) {
        if (client.hasSingleplayerServer()) return;
        if (!canSend(WallSlideC2SPayload.ID)) return;
        if (gripping) {
            if (!wallSlideSent) VmcDebugLog.event("NET", "→ WallSlide(true) [no-fall-damage]");
            VmcNet.sendToServer(new WallSlideC2SPayload(true));
            wallSlideSent = true;
        } else if (wallSlideSent) {
            VmcDebugLog.event("NET", "→ WallSlide(false)");
            VmcNet.sendToServer(new WallSlideC2SPayload(false));
            wallSlideSent = false;
        }
    }

    // -----------------------------------------------------------------------
    // Real Monke — server-synced HEIGHT-ONLY hitbox shrink
    // -----------------------------------------------------------------------

    // The SERVER validates movement with its own copy of the player's box, so the
    // shrink must land on both logical sides or tunnels get rubber-banded:
    //   singleplayer → refresh the integrated server's ServerPlayer; the shared
    //                  PlayerHitboxMixin applies the same height cap to it
    //   dedicated    → ask the monke-server companion via RealMonkeC2SPayload
    private static void applyRealMonkeScale(Minecraft client, boolean on) {
        if (client.hasSingleplayerServer()) {
            var server = client.getSingleplayerServer();
            if (server == null || client.player == null) return;
            java.util.UUID id = client.player.getUUID();
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayer(id);
                if (sp != null) sp.refreshDimensions();   // PlayerHitboxMixin caps the height
            });
        } else if (ServerLimits.packetReceived
                && canSend(RealMonkeC2SPayload.ID)) {
            VmcNet.sendToServer(new RealMonkeC2SPayload(on));
        }
    }

    // -----------------------------------------------------------------------
    // Multiplayer authorization (server-side opt-in)
    // -----------------------------------------------------------------------

    // Singleplayer / hosting a LAN world: always allowed — there is no one to gain
    // an unfair advantage over, and the integrated server can't run server mods.
    // Dedicated server: allowed ONLY after the monke-server companion mod has sent
    // its config packet this connection. No packet = the server didn't opt in =
    // the mod refuses to run (required by Modrinth's movement-mod policy, and it
    // shuts out bad actors who would use this on unsuspecting vanilla servers).
    private static boolean serverAuthorized(Minecraft client) {
        return client.hasSingleplayerServer() || ServerLimits.packetReceived;
    }

    // -----------------------------------------------------------------------
    // Shared toggle logic (used by BOTH the keybind and the /vmc command)
    // -----------------------------------------------------------------------

    private void toggle() {
        applyEnabled(!enabled);
    }

    private void applyEnabled(boolean on) {
        Minecraft client = Minecraft.getInstance();

        // Multiplayer opt-in gate: on a dedicated server without the monke-server
        // companion mod, refuse every enable attempt — keybind, /vmc on, auto-start.
        if (on && !serverAuthorized(client)) {
            if (client.player != null) {
                client.gui.setOverlayMessage(
                    Component.literal("§e[ViveMonkeCraft] §cThis server doesn't run the monke-server mod"),
                    false
                );
            }
            return;
        }

        // Server config always wins: if the server has banned the mod, refuse any
        // attempt to enable it — keybind, /vmc on, or auto-start all end up here.
        if (on && !ServerLimits.modEnabled) {
            if (client.player != null) {
                client.gui.setOverlayMessage(
                    Component.literal("§e[ViveMonkeCraft] §cDisabled by server"),
                    false
                );
            }
            return;
        }

        enabled = on;
        VmcDebugLog.event("STATE", "gorilla locomotion " + (on ? "ENABLED" : "DISABLED"));

        if (on) {
            // Turning ON: re-read the config so edits apply, no restart needed.
            MovementConfig.load();
        } else {
            // Turning OFF: drop grips and restore step height / scale / gravity.
            handler.onDisable(client);
        }

        if (client.player != null) {
            // Refresh the collision box so the shorter "gorilla" hitbox applies/reverts now.
            client.player.refreshDimensions();

            String state = on ? "§aON" : "§cOFF";
            // Show in the action bar (overlay message).
            client.gui.setOverlayMessage(
                Component.literal("§e[ViveMonkeCraft] §fGorilla Locomotion: " + state),
                false
            );
        }
    }

    private void reloadConfig() {
        MovementConfig.load();
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.gui.setOverlayMessage(
                Component.literal("§e[ViveMonkeCraft] §fConfig reloaded"),
                false
            );
        }
    }
}
