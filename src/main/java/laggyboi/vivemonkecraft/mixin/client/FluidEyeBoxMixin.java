package laggyboi.vivemonkecraft.mixin.client;

import laggyboi.vivemonkecraft.client.EmbeddedServerLogic;
import laggyboi.vivemonkecraft.client.VivemonkecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// =====================================================================
// DROWNING (mixin) — let a shrunken monke drown again
// =====================================================================
//
// PlayerHitboxMixin shrinks the COLLISION box (0.45 by default, 0.5 capped for
// Real Monke) but deliberately keeps the ORIGINAL eye height (~1.62) so the
// player model isn't dragged down. That leaves the eye point ABOVE the box —
// and vanilla only looks for fluid INSIDE the box:
//
//   EntityFluidInteraction.update() scans blocks in getFluidInteractionBox()
//   (the bounding box, deflated) and sets `eyesInside` only when the scanned
//   block also contains getEyeY(). With the eye ~1.2 blocks above the box top,
//   the eye's block is never scanned, so isEyeInFluid(WATER) is ALWAYS false.
//
// Everything gated on that flag went dead: LivingEntity.baseTick never drains
// air, never deals drown damage (you can sit on the seabed forever), and the
// underwater view/swim state never triggers.
//
// FIX: extend the fluid-interaction box UP to the eye so the eye's block is
// scanned again. getFluidInteractionBox() is used by nothing but that fluid
// scan, so collision, step assist and the grab physics are untouched — this
// only restores the water checks a full-height player already gets.
//
// Mirrors PlayerHitboxMixin's ownership gate: our own player on both logical
// sides, plus Real Monke guests on an integrated/LAN server (drowning is
// applied server-side, so the host's copy is the one that counts). On a
// dedicated server the monke-server companion carries that half.
// =====================================================================
@Mixin(Entity.class)
public class FluidEyeBoxMixin {

    // require = 0 -> if Mojang renames this, we skip the fix instead of crashing.
    @Inject(method = "getFluidInteractionBox", at = @At("RETURN"), cancellable = true, require = 0)
    private void vmc$includeEyeInFluidBox(CallbackInfoReturnable<AABB> cir) {
        AABB box = cir.getReturnValue();
        if (box == null) return; // vanilla returns null for entities with no fluid interaction

        Object self = this;

        boolean applies;
        if (self instanceof LocalPlayer) {
            applies = VivemonkecraftClient.isEnabled();
        } else if (self instanceof ServerPlayer sp) {
            LocalPlayer lp = Minecraft.getInstance().player;
            applies = lp != null && sp.getUUID().equals(lp.getUUID())
                    ? VivemonkecraftClient.isEnabled()
                    : EmbeddedServerLogic.realMonkePlayers.contains(sp.getUUID());
        } else {
            return;
        }
        if (!applies) return;

        // Only when the eye actually sits above the box — i.e. only when our shrink
        // put it there. A normal-sized player is left exactly as vanilla built it.
        double eyeY = ((Entity) self).getEyeY();
        if (eyeY <= box.maxY) return;

        cir.setReturnValue(new AABB(box.minX, box.minY, box.minZ, box.maxX, eyeY + 0.001, box.maxZ));
    }
}
