package laggyboi.vivemonkecraft.client.platform;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
//? if <1.21.6
/*import net.minecraftforge.eventbus.api.EventPriority;*/

// =====================================================================
// MOD ITEMS                                                 [FORGE BODY]
// =====================================================================
//
// HOVERBOARD — hold it in either hand (hotbar or off-hand slot) and it works
// like a joystick: tilt the board and it pushes you in the direction its
// underside thrusts. All the riding logic lives in HoverboardHandler; the item
// itself is a plain, unstackable marker item.
//
// Registered on the client, which runs in the same JVM as the integrated
// server (singleplayer / LAN host), so that server knows it too. Dedicated
// servers need the companion server mod to register the same id.
//
// The Fabric branch registers it directly; shared code only calls hoverboard().
// =====================================================================
public final class VmcItems {

    private static final ResourceKey<Item> HOVERBOARD_KEY = ResourceKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath("vivemonkecraft", "hoverboard"));

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, "vivemonkecraft");

    private static final RegistryObject<Item> HOVERBOARD = ITEMS.register("hoverboard",
            () -> new Item(new Item.Properties().setId(HOVERBOARD_KEY).stacksTo(1)));

    /** Called once from the bootstrap. */
    public static void init() {
        ITEMS.register(ForgeBus.mod());

        //? if >=1.21.9 {
        BuildCreativeModeTabContentsEvent.BUS.addListener(VmcItems::addToTab);
        //?} else if >=1.21.6 {
        /*BuildCreativeModeTabContentsEvent.getBus(ForgeBus.mod()).addListener(VmcItems::addToTab);
        *///?} else {
        /*ForgeBus.mod().addListener(EventPriority.NORMAL, false, BuildCreativeModeTabContentsEvent.class,
                VmcItems::addToTab);
        *///?}
    }

    private static void addToTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(HOVERBOARD);
        }
    }

    public static Item hoverboard() {
        return HOVERBOARD.get();
    }

    private VmcItems() {}
}
