package laggyboi.vivemonkecraft.mixin.client;

import laggyboi.vivemonkecraft.client.VrViewDrop;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// =====================================================================
// BLOCK-USE ALIGNMENT (mixin) — touch the block you LOOK at, not the one above
// =====================================================================
//
// InteractTracker decides which block/entity your real hand is touching, from
// vrdata_world_pre. We lower the VIEW but not that snapshot, so the touch point
// sits ~1 block too high. Scope the same drop around the tracker call — see
// VrViewDrop for the full explanation.
//
// TARGET: activeProcess(LocalPlayer). Vivecraft 1.3.x replaced the old Tracker
// API method `doProcess` with activeProcess/inactiveProcess, and 26.x Minecraft
// ships DEOBFUSCATED — so the old `doProcess(Lnet/minecraft/class_746;)V`
// descriptor matched nothing here and (require = 0) silently no-opped, which is
// exactly why breaking-the-block-above came back. Matching by NAME only keeps
// this from rotting again on the next parameter change.
//
// Reflection-only (no Vivecraft compile dependency); require = 0 means it just
// no-ops if the internals differ on some Vivecraft version.
// =====================================================================
@Mixin(targets = "org.vivecraft.client_vr.gameplay.trackers.InteractTracker", remap = false)
public class InteractTrackerMixin {

    @Inject(method = "activeProcess", at = @At("HEAD"), require = 0, remap = false)
    private void vmcIt$lowerForInteract(LocalPlayer player, CallbackInfo ci) {
        VrViewDrop.push();
    }

    @Inject(method = "activeProcess", at = @At("RETURN"), require = 0, remap = false)
    private void vmcIt$restoreAfterInteract(LocalPlayer player, CallbackInfo ci) {
        VrViewDrop.pop();
    }
}
