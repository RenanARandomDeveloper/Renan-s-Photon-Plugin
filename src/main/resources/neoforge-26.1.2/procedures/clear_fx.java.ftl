if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
var mc = net.minecraft.client.Minecraft.getInstance();
if (mc.player != null && mc.isSameThread()) {

if (mc.particleEngine instanceof com.lowdragmc.photon.core.mixins.accessor.ParticleEngineAccessor accessor) {
accessor.getParticles().entrySet().removeIf(entry ->
entry.getKey() instanceof com.lowdragmc.photon.client.gameobject.emitter.renderpipeline.ParticleQueueRenderType ||
entry.getKey() == com.lowdragmc.photon.client.gameobject.FXObject.NO_RENDER_RENDER_TYPE);
}
com.lowdragmc.photon.client.fx.EntityEffectExecutor.CACHE.clear();
com.lowdragmc.photon.client.fx.BlockEffectExecutor.CACHE.clear();
}
}