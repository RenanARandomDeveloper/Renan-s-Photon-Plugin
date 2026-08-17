package ${package}.photon_plugin;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod.EventBusSubscriber(modid = "${modid}", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PhotonPluginNetworking {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("${modid}", "photon_plugin_networking"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private PhotonPluginNetworking() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            int id = 0;
            CHANNEL.registerMessage(id++, ModifyEntityEffectPacket.class,
                    ModifyEntityEffectPacket::encode, ModifyEntityEffectPacket::decode, ModifyEntityEffectPacket::handle);
            CHANNEL.registerMessage(id++, ModifyBlockEffectPacket.class,
                    ModifyBlockEffectPacket::encode, ModifyBlockEffectPacket::decode, ModifyBlockEffectPacket::handle);
        });
    }
}
