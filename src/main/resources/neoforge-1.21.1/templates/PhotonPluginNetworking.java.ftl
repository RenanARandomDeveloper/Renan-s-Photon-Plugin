package ${package}.photon_plugin;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = "${modid}", bus = EventBusSubscriber.Bus.MOD)
public final class PhotonPluginNetworking {

    private PhotonPluginNetworking() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("${modid}");

        registrar.playToClient(ModifyEntityEffectPacket.TYPE, ModifyEntityEffectPacket.CODEC, ModifyEntityEffectPacket::execute);
        registrar.playToClient(ModifyBlockEffectPacket.TYPE, ModifyBlockEffectPacket.CODEC, ModifyBlockEffectPacket::execute);
    }
}
