package laggyboi.vivemonkecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// =====================================================================
// VR VIEW DROP — one source of truth for "how far is the view lowered",
// plus a scoped drop of Vivecraft's GAMEPLAY snapshot origin.
// =====================================================================
//
// VrCameraHeightMixin lowers vrdata_world_RENDER so the VIEW (and the hand
// MODELS) sit lower. Roomscale interaction — punching a block to mine it
// (SwingTracker) and touching a block to use it (InteractTracker) — is
// computed from vrdata_world_PRE, which is NOT lowered, so those rays fire
// from your true (higher) hand position and you hit the block ~1 above the
// one you see.
//
// The fix is to lower vrdata_world_pre.origin by the SAME amount, but ONLY
// for the duration of the tracker call: every position the tracker reads
// (controllers, eye) derives from that origin, so the whole interaction drops
// to match the view — while movement, which reads vrdata_world_pre OUTSIDE
// those calls, still sees the unmodified origin. (Lowering it globally shoves
// the player ~0.9 into the floor; Vivecraft repositions the body to the
// gameplay snapshots.)
//
// Reflection-only: the mod has no Vivecraft compile dependency, it talks to
// Vivecraft/QuestCraft by name. A `broken` flag means an unknown Vivecraft
// layout silently no-ops instead of crashing or spamming.
// =====================================================================
public final class VrViewDrop {

    private VrViewDrop() {}

    private static Method vmc$vrPlayerGet;   // VRPlayer.get()
    private static Field  vmc$preField;      // VRPlayer.vrdata_world_pre
    private static Field  vmc$originField;   // VRData.origin
    private static boolean vmc$broken = false;

    // State held between push() and pop() so we restore the exact value.
    private static Object vmc$preData   = null;
    private static Vec3   vmc$savedOrig = null;
    private static int    vmc$depth     = 0;

    /**
     * How far the VR view is currently lowered, in blocks. 0 = no drop.
     * Read by the render mixin AND by the interaction scopes — they MUST agree,
     * or what you break stops matching what you see.
     */
    public static double currentDrop() {
        if (!VivemonkecraftClient.isEnabled()) return 0.0;
        // No drop while riding (boat/minecart/horse) or elytra-flying: the seat/flight
        // already sets your eye height, so dropping it 0.9 would sink the camera down
        // INTO the vehicle. Locomotion is suspended in those states anyway.
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && (p.isPassenger() || p.isFallFlying())) return 0.0;
        return MovementConfig.realMonke ? 0.9 : MovementConfig.cameraHeightOffset;
    }

    /** Lower vrdata_world_pre.origin by the current drop. Always pair with {@link #pop()}. */
    public static void push() {
        if (vmc$broken) return;
        if (vmc$depth++ > 0) return; // an outer scope already dropped it

        vmc$preData   = null;
        vmc$savedOrig = null;

        double drop = currentDrop();
        if (drop <= 0.0) return;

        try {
            if (vmc$vrPlayerGet == null) {
                Class<?> vrPlayer = Class.forName("org.vivecraft.client_vr.gameplay.VRPlayer");
                vmc$vrPlayerGet = vrPlayer.getMethod("get");
                vmc$preField    = vrPlayer.getField("vrdata_world_pre");
            }
            Object vrPlayer = vmc$vrPlayerGet.invoke(null);
            if (vrPlayer == null) return;
            Object pre = vmc$preField.get(vrPlayer);
            if (pre == null) return;
            if (vmc$originField == null) {
                vmc$originField = pre.getClass().getField("origin");
            }
            Object originObj = vmc$originField.get(pre);
            if (originObj instanceof Vec3 origin) {
                // Stash BEFORE mutating so pop() always restores, even if we throw next.
                vmc$preData   = pre;
                vmc$savedOrig = origin;
                vmc$originField.set(pre, origin.add(0.0, -drop, 0.0));
            }
        } catch (Throwable t) {
            vmc$broken = true;
        }
    }

    /** Restore the origin saved by {@link #push()}. Safe to call unpaired. */
    public static void pop() {
        if (vmc$depth == 0) return;
        if (--vmc$depth > 0) return; // inner scope; the outer one restores
        if (vmc$preData == null || vmc$savedOrig == null) return;
        try {
            vmc$originField.set(vmc$preData, vmc$savedOrig);
        } catch (Throwable t) {
            vmc$broken = true;
        } finally {
            vmc$preData   = null;
            vmc$savedOrig = null;
        }
    }
}
