package laggyboi.vivemonkecraft.client.platform;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
//? if >=1.21.6 {
import net.minecraftforge.eventbus.api.bus.BusGroup;
//?} else
/*import net.minecraftforge.eventbus.api.IEventBus;*/

// =====================================================================
// FORGE EVENT BUSES BY ERA                                  [FORGE ONLY]
// =====================================================================
//
// Forge changed how listeners attach twice in the versions this branch builds:
//   1.21.4-1.21.5  EventBus 6: MinecraftForge.EVENT_BUS (game) + the mod's
//                  IEventBus (mod events), addListener(priority, ..., Class, Consumer).
//   1.21.6-1.21.8  EventBus 7: game events carry a static BUS; mod events are
//                  reached with Event.getBus(modBusGroup).
//   1.21.9+        mod events carry a static BUS too.
// The platform classes branch on these; this holds the mod bus they share.
// =====================================================================
final class ForgeBus {

    private ForgeBus() {}

    //? if >=1.21.6 {
    /** The mod's bus group (mod-lifecycle events, DeferredRegisters). */
    static BusGroup mod() {
        return FMLJavaModLoadingContext.get().getModBusGroup();
    }
    //?} else {
    /*/^* The mod's event bus (mod-lifecycle events, DeferredRegisters). ^/
    static IEventBus mod() {
        return FMLJavaModLoadingContext.get().getModEventBus();
    }
    *///?}
}
