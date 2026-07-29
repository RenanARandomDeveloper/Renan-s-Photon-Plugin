package ${package}.photon_plugin;

import com.lowdragmc.photon.client.fx.BlockEffect;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

public final class RemoveFXBlockClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private RemoveFXBlockClient() {
    }

    public static RemoveBuilder destroy(double x, double y, double z) {
        return new RemoveBuilder(BlockPos.containing(x, y, z));
    }

    public static RemoveBuilder destroy(BlockPos pos) {
        return new RemoveBuilder(pos);
    }

    public static class RemoveBuilder {
        private final BlockPos pos;
        private String effectName;
        private boolean force = false;
        private int delay = 0;

        protected RemoveBuilder(BlockPos pos) {
            this.pos = pos;
        }

        public RemoveBuilder name(String name) {
            this.effectName = name;
            return this;
        }
        public RemoveBuilder force(boolean force) {
            this.force = force;
            return this;
        }

        public RemoveBuilder delay(int delayTicks) {
            this.delay = Math.max(0, delayTicks);
            return this;
        }

        public void send() {
            if (!FMLEnvironment.dist.isClient()) {
                return;
            }

            try {
                if (this.pos == null) {
                    LOGGER.warn("[RemoveFXBlockClient] Null BlockPos provided for effect removal{}.",
                            describeEffect());
                    return;
                }

                ResourceLocation resolvedLocation = null;
                if (this.effectName != null && !this.effectName.isEmpty()) {
                    try {
                        resolvedLocation = ResourceLocation.fromNamespaceAndPath("photon", this.effectName);
                    } catch (Exception e) {
                        LOGGER.error("[RemoveFXBlockClient] Invalid effect name '{}': {}",
                                this.effectName, e.getMessage(), e);
                        return;
                    }
                }

                try {
                    removeFromBlock(this.pos, resolvedLocation, this.force);
                } catch (Exception e) {
                    LOGGER.error("[RemoveFXBlockClient] Critical error while removing effect{} at position {}: {}",
                            describeEffect(), this.pos, e.getMessage(), e);
                }
            } catch (Exception e) {
                LOGGER.error("[RemoveFXBlockClient] Failed to process removal request{} at position {}. Cause: {}",
                        describeEffect(), this.pos, e.getMessage(), e);
            }
        }

        private void removeFromBlock(BlockPos pos, ResourceLocation location, boolean force) {
            ClientEffectScheduler.queueClientWork(this.delay, () -> {

                if (pos == null) return;

                var effects = BlockEffect.CACHE.get(pos);
                if (effects == null) return;

                var iterator = effects.iterator();
                while (iterator.hasNext()) {
                    var effect = iterator.next();
                    if (location == null || location.equals(effect.getFx().getFxLocation())) {
                        iterator.remove();
                        var runtime = effect.getRuntime();
                        if (runtime != null && runtime.isAlive()) {
                            runtime.destroy(force);
                        }
                    }
                }
            });
        }

        private String describeEffect() {
            return (this.effectName != null && !this.effectName.isEmpty())
                    ? " of effect '" + this.effectName + "'"
                    : " (all effects)";
        }
    }
}