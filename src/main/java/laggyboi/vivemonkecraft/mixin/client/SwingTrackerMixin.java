package laggyboi.vivemonkecraft.mixin.client;

import laggyboi.vivemonkecraft.client.VrViewDrop;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// =====================================================================
// BLOCK-BREAK ALIGNMENT (mixin) — mine the block you LOOK at, not the one above
// =====================================================================
//
// This is Vivecraft's OWN block breaking: SwingTracker.activeProcess builds the
// mining points from vrdata_world_pre.getController(hand).getPosition() and
// raycasts them into the world. We lower the VIEW (vrdata_world_render) but not
// that gameplay snapshot, so the swing lands ~1 block above what you see.
//
// Same scoped origin drop as InteractTrackerMixin — see VrViewDrop.
//
// This half of the fix was MISSING: the old mixin only covered InteractTracker
// (and with a stale target at that), so mining kept breaking the block above
// even where interaction was aligned.
// =====================================================================
@Mixin(targets = "org.vivecraft.client_vr.gameplay.trackers.SwingTracker", remap = false)
public class SwingTrackerMixin {

    @Inject(method = "activeProcess", at = @At("HEAD"), require = 0, remap = false)
    private void vmcSt$lowerForSwing(LocalPlayer player, CallbackInfo ci) {
        VrViewDrop.push();
    }

    @Inject(method = "activeProcess", at = @At("RETURN"), require = 0, remap = false)
    private void vmcSt$restoreAfterSwing(LocalPlayer player, CallbackInfo ci) {
        VrViewDrop.pop();
    }
}
