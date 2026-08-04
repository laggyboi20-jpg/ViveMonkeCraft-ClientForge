package laggyboi.vivemonkecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

// =====================================================================
// CAMERA STABILIZATION RENDERER  (QuestCraft / Vivecraft)
// =====================================================================
//
// Draws a black vignette border inside the VR headset display to narrow the
// perceived field-of-view while locomotion is fast, reducing motion sickness.
//
// ⚠️ STILL STUBBED (as on the Fabric 1.21.5–1.21.8 releases) ⚠️
// Minecraft 1.21.5 removed the immediate-mode render path this used
// (BufferUploader, CoreShaders, RenderSystem.setShader/enableBlend/…) in favour
// of the new RenderPipeline / GpuDevice command system, so the actual GPU draw
// (drawVignette) is a no-op. The speed/easing logic is preserved and still runs
// each frame; re-enable by implementing drawVignette() with a RenderPipeline.
//
// LOADER NOTE: the per-frame hook is NeoForge's RenderLevelStageEvent
// (AFTER_TRANSLUCENT_BLOCKS) instead of Fabric's WorldRenderEvents.AFTER_TRANSLUCENT.
// =====================================================================

public final class CameraStabilizationRenderer {

    private CameraStabilizationRenderer() {}

    // Locomotion speed (blocks/tick) at which the vignette starts appearing.
    private static final float SPEED_MIN = 0.12f;
    // Speed at which the vignette reaches full configured strength.
    private static final float SPEED_MAX = 0.55f;

    // Smoothed 0..1 factor persisted between render frames for easing.
    private static float smoothFactor = 0.0f;

    public static void register() {
        RenderLevelStageEvent.BUS.addListener(
                CameraStabilizationRenderer::onWorldRender);
    }

    private static void onWorldRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!VivemonkecraftClient.isEnabled()) return;
        if (!MovementConfig.cameraStabEnabled) return;
        if (!VivecraftBridge.isVrActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Horizontal speed drives the effect; vertical weight halved so normal falls
        // don't trigger it.
        Vec3 vel = mc.player.getDeltaMovement();
        double speed = vel.horizontalDistance() + Math.abs(vel.y) * 0.5;

        float strength  = (float) Math.max(0.0, Math.min(1.0, MovementConfig.cameraStabStrength));
        float rawFactor = (float) Math.max(0.0,
                Math.min(1.0, (speed - SPEED_MIN) / (SPEED_MAX - SPEED_MIN)));

        // Ease in fast, ease out slowly.
        if (rawFactor > smoothFactor) {
            smoothFactor += (rawFactor - smoothFactor) * 0.5f;
        } else {
            smoothFactor += (rawFactor - smoothFactor) * 0.07f;
        }
        if (smoothFactor < 0.01f) return;

        float edgeFrac = smoothFactor * strength * 0.28f;
        if (edgeFrac < 0.005f) return;

        int alpha = Math.min(230, (int) (230 * smoothFactor * strength));
        drawVignette(edgeFrac, alpha);
    }

    // TODO(1.21.5+): reimplement with the new RenderPipeline / GpuDevice API.
    // Was: identity matrices + Tesselator QUADS + BufferUploader.drawWithShader
    // with CoreShaders.POSITION_COLOR. Currently a no-op so the mod builds.
    private static void drawVignette(float edgeFrac, int alpha) {
        // intentionally empty until ported to the new render pipeline
    }
}
