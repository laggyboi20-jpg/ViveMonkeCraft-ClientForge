package laggyboi.vivemonkecraft.client;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import laggyboi.vivemonkecraft.client.cosmetics.Cosmetic;
import laggyboi.vivemonkecraft.client.cosmetics.Cosmetics;

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
// One file for every version: the few vanilla GUI calls that changed between
// 1.21.x, 26.1 and 26.2 are //? blocks (preset button, toggle setter type).
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
        List<String> presetChoices = new java.util.ArrayList<>();
        presetChoices.add("— None —");
        presetChoices.addAll(List.of(MovementConfig.PRESET_NAMES));
        CycleButton.OnValueChange<String> onPreset = (btn, value) -> {
            pendingPreset = value;
            if (!value.equals("— None —")) {
                MovementConfig.applyPreset(value);
                MovementConfig.activePreset = value;
            }
            this.rebuildWidgets();
        };
        // 1.21.11 made the builder take the initial value (withInitialValue is gone).
        //? if >=1.21.11 {
        CycleButton<String> presetButton = CycleButton.<String>builder(Component::literal, pendingPreset)
                .withValues(presetChoices)
                .create(0, 0, 310, 20, Component.literal("Preset"), onPreset);
        //?} else {
        /*CycleButton<String> presetButton = CycleButton.<String>builder(Component::literal)
                .withValues(presetChoices)
                .withInitialValue(pendingPreset)
                .create(0, 0, 310, 20, Component.literal("Preset"), onPreset);
        *///?}
        // Only 26.2+ has addBig(AbstractWidget); before that a lone widget is a
        // small row with an empty right half.
        //? if >=26.2 {
        this.list.addBig(presetButton);
        //?} else
        /*this.list.addSmall(presetButton, null);*/

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
                v -> MovementConfig.stepTeleport = v));

        // ---- Gorilla Tag (locomotion mode 1: the Player.cs clone, GT units: 1 block = 1 m) ----
        this.list.addBig(toggle("Gorilla Tag mode", MovementConfig.locomotionMode == GorillaLocomotionHandler.MODE_GT_ANCHOR,
                "ON = a clone of Gorilla Tag's own Player.cs: hands stick where they touch, 1:1 body movement, real gravity, GT's throw. OFF = Speed Swing.",
                v -> MovementConfig.locomotionMode = v ? GorillaLocomotionHandler.MODE_GT_ANCHOR : GorillaLocomotionHandler.MODE_SPEED_SWING));
        this.list.addBig(slider("GT jump multiplier", 0.0, 10.0, MovementConfig.gtJumpMultiplier,
                "Throw = average body speed x this. Gorilla Tag: 1.1",
                v -> MovementConfig.gtJumpMultiplier = v));
        this.list.addBig(slider("GT max jump speed (m/s)", 0.0, 60.0, MovementConfig.gtMaxJumpSpeed,
                "Throw speed cap. Gorilla Tag: 6.5",
                v -> MovementConfig.gtMaxJumpSpeed = v));
        this.list.addBig(slider("GT velocity limit (m/s)", 0.0, 10.0, MovementConfig.gtVelocityLimit,
                "Moving slower than this never throws you. Gorilla Tag: 0.3",
                v -> MovementConfig.gtVelocityLimit = v));
        this.list.addBig(slider("GT velocity history (ticks)", 1.0, 20.0, MovementConfig.gtVelocityHistory,
                "Ticks averaged for the throw. 2 = Gorilla Tag's 3 frames.",
                v -> MovementConfig.gtVelocityHistory = v));
        this.list.addBig(slider("GT arm length (m)", 0.2, 5.0, MovementConfig.gtArmLength,
                "Hands are held within this distance of your head. Gorilla Tag: 1.5",
                v -> MovementConfig.gtArmLength = v));
        this.list.addBig(slider("GT hand radius (m)", 0.01, 0.5, MovementConfig.gtHandRadius,
                "Size of each hand's collision sphere. Gorilla Tag: 0.05",
                v -> MovementConfig.gtHandRadius = v));
        this.list.addBig(slider("GT gravity (m/s2)", 0.0, 40.0, MovementConfig.gtGravity,
                "Gorilla Tag uses real gravity, 9.81 (Minecraft's is ~32).",
                v -> MovementConfig.gtGravity = v));
        this.list.addBig(slider("GT ground friction", 0.0, 1.0, MovementConfig.gtGroundFriction,
                "Speed kept per tick while standing. 1 = no friction, 0 = instant stop.",
                v -> MovementConfig.gtGroundFriction = v));
        this.list.addBig(slider("GT unstick distance (m)", 0.2, 3.0, MovementConfig.gtUnstickDistance,
                "A stuck hand lets go once it is this far from where it touched. Gorilla Tag: 1.0",
                v -> MovementConfig.gtUnstickDistance = v));
        this.list.addBig(slider("GT ice slip", 0.0, 1.0, MovementConfig.gtIceSlip,
                "How much a hand slides along ice. 0 = grips like stone, 1 = frictionless.",
                v -> MovementConfig.gtIceSlip = v));

        // ---- Hoverboard ----
        this.list.addBig(toggle("Hoverboard enabled", MovementConfig.hoverboardEnabled,
                "Hold the Hoverboard item and tilt it like a joystick: 45 degrees forward = full speed. Speed Swing mode only.",
                v -> MovementConfig.hoverboardEnabled = v));
        this.list.addBig(slider("Hoverboard top speed", 0.0, 2.0, MovementConfig.hoverMaxSpeed,
                "Blocks/tick. 0.7 = 14 blocks/sec.",
                v -> MovementConfig.hoverMaxSpeed = v));
        this.list.addBig(slider("Hoverboard tilt offset (deg)", -90.0, 90.0, MovementConfig.hoverTiltOffset,
                "Shifts what counts as 'level'. Drifting forward when level? Lower this.",
                v -> MovementConfig.hoverTiltOffset = v));

        // ---- Cosmetics ----
        for (Cosmetic c : Cosmetics.all()) {
            this.list.addBig(toggle("Cosmetic: " + c.name, Cosmetics.isEquipped(c.id),
                    "Free cosmetic. Other players with ViveMonkeCraft see what you wear.",
                    v -> Cosmetics.setEquipped(c.id, v)));
        }

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
        // setter::accept fits both the 26.2+ ValueUpdateListener and the older Consumer.
        return OptionInstance.createBoolean(
                label,
                OptionInstance.cachedConstantTooltip(Component.literal(tooltip)),
                current,
                setter::accept);
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
}
