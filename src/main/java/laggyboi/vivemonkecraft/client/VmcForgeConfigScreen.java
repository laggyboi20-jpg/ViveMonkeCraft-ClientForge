package laggyboi.vivemonkecraft.client;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

// =====================================================================
// CONFIG SCREEN  (Forge — VANILLA widgets, no Cloth)   ***26.x variant***
// =====================================================================
//
// Cloth Config has NO Forge build for Minecraft 1.21.4+ / 26.x, so the config
// screen is built from vanilla widgets only. On the 26.x line Mojang reworked the
// GUI: `GuiGraphics` was removed (render-state extraction model) and `OptionsList`
// is now tied to `OptionsSubScreen`, so this screen extends OptionsSubScreen — the
// base owns the scrolling OptionsList, the title/background render, the "Done"
// footer button and the return-to-parent navigation, none of which we hand-roll.
//
// Every setting is a vanilla slider (doubles) or on/off toggle (booleans) with a
// tooltip, added to `this.list` in addOptions(); a preset selector sits at the top
// as a CycleButton row. Values are written straight into MovementConfig as you
// change them (via each option's ValueUpdateListener) and saved to
// config/vivemonkecraft.properties when the screen closes.
//
// NOTE: this is the 26.x-only port. The 1.21.4–1.21.11 branches keep the older
// Screen/GuiGraphics-based VmcForgeConfigScreen (their vanilla GUI API differs).
// =====================================================================
public final class VmcForgeConfigScreen extends OptionsSubScreen {

    private String pendingPreset = "— None —";

    public VmcForgeConfigScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options,
                Component.literal("ViveMonkeCraft — Gorilla Locomotion"));
    }

    /** Registered as the Forge config-screen factory target. */
    public static Screen create(Screen parent) {
        MovementConfig.load();
        return new VmcForgeConfigScreen(parent);
    }

    @Override
    protected void addOptions() {
        // Preset selector as the first row. Choosing a value applies that preset to
        // the MovementConfig fields immediately and rebuilds the screen so every
        // slider re-reads its new value. "— None —" leaves everything untouched.
        CycleButton<String> presetButton = CycleButton.<String>builder(Component::literal, pendingPreset)
                .withValues(List.of("— None —", "tutorial", "Default", "Long Arms", "Zero Gravity", "Speed Run"))
                .create(0, 0, 310, 20, Component.literal("Preset"),
                        (btn, value) -> {
                            pendingPreset = value;
                            if (!value.equals("— None —")) {
                                applyPreset(value);
                                MovementConfig.activePreset = value;
                            }
                            this.rebuildWidgets();
                        });
        // 26.1.x OptionsList has no addBig(AbstractWidget) overload (26.2 added it),
        // so the preset goes in as a "small" (half-width) row instead.
        this.list.addSmall(presetButton, (AbstractWidget) null);

        // ---- Movement ----
        this.list.addBig(toggle("Step assist teleports", MovementConfig.stepTeleport,
                "ON = step assist places you directly on top of the ledge (instant). OFF = an upward velocity boost arcs you over it.",
                v -> MovementConfig.stepTeleport = v));
        this.list.addBig(toggle("Step assist", MovementConfig.stepAssist,
                "Raise step height so legs stop snagging on block edges.",
                v -> MovementConfig.stepAssist = v));
        this.list.addBig(slider("Push speed", 0.0, 8.0, MovementConfig.pullStrength,
                "How strongly your swing moves you. 1.0 = 1:1, higher = faster. (Ignored in anchor mode.)",
                v -> MovementConfig.pullStrength = v));
        this.list.addBig(slider("Ground friction (anti-slide)", 0.0, 1.0, MovementConfig.groundFriction,
                "1.0 = slide forever, 0.5 = stops fast, 0.2 = near-instant.",
                v -> MovementConfig.groundFriction = v));
        this.list.addBig(slider("Air friction", 0.0, 1.0, MovementConfig.airFriction,
                "Horizontal speed bleed while airborne and not gripping. 1.0 = normal drag, 0.0 = throws carry forever.",
                v -> MovementConfig.airFriction = v));
        this.list.addBig(slider("Wall stickiness", 0.0, 1.0, MovementConfig.wallStickiness,
                "How hard hands cling to walls while gripping. 1.0 = hang forever, 0.0 = no clinging.",
                v -> MovementConfig.wallStickiness = v));
        this.list.addBig(slider("Floor stickiness", 0.0, 1.0, MovementConfig.floorStickiness,
                "How much you slide across the floor while a hand is planted. 1.0 = stop dead, 0.0 = ice-like.",
                v -> MovementConfig.floorStickiness = v));
        this.list.addBig(slider("Jitter filter (minImpulse)", 0.0, 0.1, MovementConfig.minImpulse,
                "Movements smaller than this are ignored so a resting hand stays still.",
                v -> MovementConfig.minImpulse = v));

        // ---- Reach & Body ----
        this.list.addBig(slider("Gorilla arm length", 1.0, 6.0, MovementConfig.handReachMultiplier,
                "Longer arms = reach the ground with less real-arm reach. 2.5 default.",
                v -> MovementConfig.handReachMultiplier = v));
        this.list.addBig(slider("Max arm length (blocks)", 1.5, 8.0, MovementConfig.maxArmLength,
                "Hard limit on how far a hand can be from your head.",
                v -> MovementConfig.maxArmLength = v));
        this.list.addBig(slider("Hand grab size", 0.02, 0.5, MovementConfig.handRadius,
                "Radius of the hand touch sphere. Bigger = easier to grab.",
                v -> MovementConfig.handRadius = v));
        this.list.addBig(slider("Hitbox height (legs)", 0.2, 1.0, MovementConfig.hitboxHeightScale,
                "Shrinks your collision box height to climb onto blocks. 1.0 = normal, 0.25 = compact.",
                v -> MovementConfig.hitboxHeightScale = v));
        this.list.addBig(slider("Step height", 0.5, 2.0, MovementConfig.stepHeight,
                "Ledge height you can step over. 0.6 = vanilla, 1.5 = default.",
                v -> MovementConfig.stepHeight = v));

        // ---- Jump & Gravity ----
        this.list.addBig(slider("Jump threshold", 0.0, 1.0, MovementConfig.velocityLimit,
                "You only launch on release if you were moving faster than this.",
                v -> MovementConfig.velocityLimit = v));
        this.list.addBig(slider("Jump power", 0.0, 5.0, MovementConfig.jumpMultiplier,
                "How much your speed is multiplied into the launch. 1.0 = release speed, 1.4 = boost.",
                v -> MovementConfig.jumpMultiplier = v));
        this.list.addBig(slider("Max launch speed", 0.1, 5.0, MovementConfig.maxJumpSpeed,
                "Hard cap on launch speed (blocks/tick). 1.0 = 20 blocks/sec.",
                v -> MovementConfig.maxJumpSpeed = v));
        this.list.addBig(slider("Jump smoothing (ticks)", 1.0, 20.0, MovementConfig.velocityHistorySize,
                "How many ticks of movement are averaged for the launch.",
                v -> MovementConfig.velocityHistorySize = v));
        this.list.addBig(slider("Gravity", 0.0, 1.0, MovementConfig.gravityMultiplier,
                "Scales gravity while airborne and not gripping. 1.0 = normal, 0.0 = float. Also /vmc gravity <0-1>.",
                v -> MovementConfig.gravityMultiplier = v));

        // ---- Visual ----
        this.list.addBig(toggle("Clamp hand models to surfaces", MovementConfig.clampHandModels,
                "While gripping, draw the Vivecraft hand model ON the block face instead of inside it. Purely visual.",
                v -> MovementConfig.clampHandModels = v));
        this.list.addBig(toggle("Real Monke (gorilla size)", MovementConfig.realMonke,
                "Caps collision box HEIGHT at 0.5 blocks on client and server so you fit through 1-block tunnels.",
                v -> MovementConfig.realMonke = v));
        this.list.addBig(slider("Camera height offset", 0.0, 1.5, MovementConfig.cameraHeightOffset,
                "Blocks to LOWER the VR view + hands. 0 = off. Makes your own body model look squashed.",
                v -> MovementConfig.cameraHeightOffset = v));
        this.list.addBig(toggle("Monke model (no legs)", MovementConfig.monkeModel,
                "The Gorilla Tag body: legs removed, torso shortened. Synced via monke-server.",
                v -> MovementConfig.monkeModel = v));
        this.list.addBig(slider("Monke torso offset Y", -12.0, 12.0, MovementConfig.modelTorsoOffsetY,
                "Torso up/down in model pixels (1 px = 1/16 block, +down).",
                v -> MovementConfig.modelTorsoOffsetY = v));
        this.list.addBig(slider("Monke torso pitch (deg)", -360.0, 360.0, MovementConfig.modelTorsoPitch,
                "Torso rotation in degrees. -120 = GT lean (default).",
                v -> MovementConfig.modelTorsoPitch = v));
        this.list.addBig(slider("Monke torso height scale", 0.1, 2.0, MovementConfig.modelTorsoScaleY,
                "Torso height multiplier. 0.75 = a bit shorter than the arms.",
                v -> MovementConfig.modelTorsoScaleY = v));
        this.list.addBig(slider("Monke arms offset Y", -12.0, 12.0, MovementConfig.modelArmsOffsetY,
                "Arms up/down in model pixels (+down).",
                v -> MovementConfig.modelArmsOffsetY = v));
        this.list.addBig(slider("Monke arms pitch (deg)", -360.0, 360.0, MovementConfig.modelArmsPitch,
                "Extra arm rotation in degrees (added on top of the swing animation).",
                v -> MovementConfig.modelArmsPitch = v));
        this.list.addBig(slider("Monke head offset Y", -12.0, 12.0, MovementConfig.modelHeadOffsetY,
                "Head up/down in model pixels (+down).",
                v -> MovementConfig.modelHeadOffsetY = v));
        this.list.addBig(slider("Monke head pitch (deg)", -360.0, 360.0, MovementConfig.modelHeadPitch,
                "Extra head rotation in degrees (added on top of look direction).",
                v -> MovementConfig.modelHeadPitch = v));

        // ---- Block interactions ----
        this.list.addBig(toggle("Punch mining (experimental)", MovementConfig.punchMining,
                "Break the block your hand touches, only with the right tool and only when you PUNCH it. Off by default.",
                v -> MovementConfig.punchMining = v));
        this.list.addBig(slider("Block-breaking threshold", 0.0, 1.0, MovementConfig.punchMiningThreshold,
                "How easily you break blocks. 0 = damage on touch.",
                v -> MovementConfig.punchMiningThreshold = v));
        this.list.addBig(toggle("Punch mining: no tool needed", MovementConfig.punchMiningNoTool,
                "Let punch mining break blocks with ANY item (even bare hands) — hand speed alone decides.",
                v -> MovementConfig.punchMiningNoTool = v));
        this.list.addBig(toggle("Magma block sides hurt", MovementConfig.magmaTouchDamage,
                "Grabbing a magma block on any face deals the hot-floor damage. Fire resistance negates it.",
                v -> MovementConfig.magmaTouchDamage = v));

        // ---- GT physics ----
        this.list.addBig(toggle("GT physics beta (anchor mode)", MovementConfig.gtPhysics,
                "ON = official GorillaLocomotion algorithm: hands anchor and drag your body 1:1. OFF = older speed-based model.",
                v -> MovementConfig.gtPhysics = v));
        this.list.addBig(slider("Push strength", 0.1, 100.0, MovementConfig.gtPushStrength,
                "Body movement = hand movement x this. 1.0 = authentic Gorilla Tag 1:1.",
                v -> MovementConfig.gtPushStrength = v));
        this.list.addBig(slider("Drag gain", 0.05, 100.0, MovementConfig.gtDragGain,
                "Fraction of the distance to the anchor corrected each tick. 0.30 = smooth. Keep below 1.0.",
                v -> MovementConfig.gtDragGain = v));
        this.list.addBig(slider("Unstick distance (blocks)", 0.2, 3.0, MovementConfig.gtUnstickDistance,
                "How far a hand may stray from its anchor before the grip releases. GT uses 1.0.",
                v -> MovementConfig.gtUnstickDistance = v));
        this.list.addBig(slider("Ice slip", 0.0, 1.0, MovementConfig.gtIceSlip,
                "How fast an anchor drifts toward the hand on ice. 0 = grips like stone, 0.95 = push off only.",
                v -> MovementConfig.gtIceSlip = v));

        // ---- Experimental ----
        this.list.addBig(toggle("Allow Vivecraft teleport", MovementConfig.allowTeleport,
                "OFF (default): teleport disabled while gorilla locomotion is on (it desyncs hand physics).",
                v -> MovementConfig.allowTeleport = v));
        this.list.addBig(toggle("Ice floor = ice wall (experimental)", MovementConfig.iceFloorWallLogic,
                "Treat grabbing an ice FLOOR exactly like an ice WALL: gravity on, pure push-off. Legacy physics only.",
                v -> MovementConfig.iceFloorWallLogic = v));
        this.list.addBig(toggle("Vanilla ice friction", MovementConfig.vanillaIceFriction,
                "Ice acts on hands/feet like vanilla legs: you SKATE with real friction instead of the mod's capped ice.",
                v -> MovementConfig.vanillaIceFriction = v));

        // ---- Debug ----
        this.list.addBig(toggle("Debug logging", MovementConfig.debugLogging,
                "Write a trace to logs/vivemonkecraft-debug.log. Performance heavy.",
                v -> MovementConfig.debugLogging = v));
        this.list.addBig(toggle("Show hand markers", MovementConfig.showHandMarkers,
                "Render split arm lines at your hands. Green = touching a block, red = not.",
                v -> MovementConfig.showHandMarkers = v));
    }

    @Override
    public void onClose() {
        // Persist everything the sliders/toggles wrote into the static fields, then
        // let the base class apply unsaved changes and return to the parent screen.
        MovementConfig.save();
        super.onClose();
    }

    // -----------------------------------------------------------------------
    // Vanilla-widget helpers — the only two places that touch OptionInstance.
    // -----------------------------------------------------------------------

    /** An on/off toggle bound to a boolean field. */
    private static OptionInstance<Boolean> toggle(String label, boolean current, String tooltip,
                                                  Consumer<Boolean> setter) {
        return OptionInstance.createBoolean(
                label,
                OptionInstance.cachedConstantTooltip(Component.literal(tooltip)),
                current,
                setter);
    }

    /**
     * A slider bound to a double field over [min, max]. Vanilla's UnitDouble slider
     * runs 0..1; we map that onto the real range and show the real value in the label.
     */
    private static OptionInstance<Double> slider(String label, double min, double max, double current,
                                                 String tooltip, DoubleConsumer setter) {
        double t0 = Mth.clamp((current - min) / (max - min), 0.0, 1.0);
        return new OptionInstance<>(
                label,
                OptionInstance.cachedConstantTooltip(Component.literal(tooltip)),
                (caption, value) -> Component.literal(
                        label + ": " + String.format(Locale.ROOT, "%.2f", min + value * (max - min))),
                OptionInstance.UnitDouble.INSTANCE,
                t0,
                value -> setter.accept(min + value * (max - min)));
    }

    // =========================================================================
    // PRESETS  (copied verbatim from the Cloth VmcConfigScreen so behaviour matches)
    // =========================================================================
    private static void applyPreset(String preset) {
        MovementConfig.applyDefaultPreset();
        switch (preset) {
            case "Default":
                break;
            case "tutorial":
                MovementConfig.pullStrength        = 2.5;
                MovementConfig.groundFriction      = 1.0;
                MovementConfig.airFriction         = 0.1;
                MovementConfig.wallStickiness      = 1.0;
                MovementConfig.floorStickiness     = 1.0;
                MovementConfig.minImpulse          = 0.002;
                MovementConfig.handReachMultiplier = 1.0;
                MovementConfig.velocityLimit       = 0.05;
                MovementConfig.maxArmLength        = 3.0;
                MovementConfig.handRadius          = 0.12;
                MovementConfig.hitboxHeightScale   = 0.25;
                MovementConfig.stepAssist          = true;
                MovementConfig.stepTeleport        = true;
                MovementConfig.stepHeight          = 1.5;
                MovementConfig.jumpMultiplier      = 1.5;
                MovementConfig.maxJumpSpeed        = 1.0;
                MovementConfig.velocityHistorySize = 6;
                MovementConfig.gravityMultiplier   = 1.0;
                MovementConfig.showHandMarkers     = true;
                MovementConfig.monkeModel          = true;
                MovementConfig.realMonke           = true;
                MovementConfig.modelTorsoPitch     = -120.0;
                MovementConfig.gtPhysics           = false;
                break;
            case "Long Arms":
                MovementConfig.pullStrength        = 2.5;
                MovementConfig.groundFriction      = 0.4;
                MovementConfig.airFriction         = 0.1;
                MovementConfig.wallStickiness      = 0.9;
                MovementConfig.floorStickiness     = 1.0;
                MovementConfig.minImpulse          = 0.002;
                MovementConfig.handReachMultiplier = 3.5;
                MovementConfig.velocityLimit       = 0.05;
                MovementConfig.jumpMultiplier      = 1.8;
                MovementConfig.maxJumpSpeed        = 1.5;
                MovementConfig.velocityHistorySize = 5;
                MovementConfig.gravityMultiplier   = 1.0;
                MovementConfig.showHandMarkers     = true;
                MovementConfig.monkeModel          = true;
                MovementConfig.realMonke           = true;
                MovementConfig.modelTorsoPitch     = -120.0;
                break;
            case "Zero Gravity":
                MovementConfig.gravityMultiplier   = 0.1;
                MovementConfig.airFriction         = 0.05;
                MovementConfig.wallStickiness      = 1.0;
                MovementConfig.floorStickiness     = 0.8;
                MovementConfig.pullStrength        = 2.0;
                MovementConfig.jumpMultiplier      = 2.0;
                MovementConfig.maxJumpSpeed        = 2.5;
                MovementConfig.groundFriction      = 0.9;
                MovementConfig.monkeModel          = true;
                MovementConfig.realMonke           = true;
                MovementConfig.modelTorsoPitch     = -120.0;
                break;
            case "Speed Run":
                MovementConfig.pullStrength        = 5.0;
                MovementConfig.handReachMultiplier = 1.0;
                MovementConfig.maxArmLength        = 1.5;
                MovementConfig.airFriction         = 0.1;
                MovementConfig.jumpMultiplier      = 3.5;
                MovementConfig.maxJumpSpeed        = 3.0;
                MovementConfig.velocityHistorySize = 4;
                MovementConfig.groundFriction      = 0.7;
                MovementConfig.gravityMultiplier   = 0.9;
                MovementConfig.monkeModel          = true;
                MovementConfig.realMonke           = true;
                MovementConfig.modelTorsoPitch     = -120.0;
                break;
            default:
                break;
        }
    }
}
