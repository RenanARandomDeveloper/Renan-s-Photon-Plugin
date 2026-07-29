package ${package}.photon_plugin;

import com.lowdragmc.photon.client.fx.EntityEffect;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class RemoveFXEntityClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private RemoveFXEntityClient() {
    }

    public static RemoveBuilder destroy(Entity entity) {
        return new RemoveBuilder(entity != null ? List.of(entity) : null);
    }

    public static RemoveBuilder destroy(List<Entity> entities) {
        return new RemoveBuilder(entities);
    }

    public static class RemoveBuilder {
        private final List<Entity> entities;
        private String effectName;
        private boolean force = false;
        private int delay = 0;
        protected RemoveBuilder(List<Entity> entities) {
            this.entities = entities;
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
                if (this.entities == null || this.entities.isEmpty()) {
                    LOGGER.warn("[RemoveFXEntityClient] No valid entities provided for effect removal{}.",
                            describeEffect());
                    return;
                }

                List<Entity> sanitizedEntities = new ArrayList<>();
                for (Entity entity : this.entities) {
                    if (entity != null) {
                        sanitizedEntities.add(entity);
                    }
                }

                if (sanitizedEntities.isEmpty()) {
                    LOGGER.warn("[RemoveFXEntityClient] All provided entities were null for effect removal{}.",
                            describeEffect());
                    return;
                }

                ResourceLocation resolvedLocation = null;
                if (this.effectName != null && !this.effectName.isEmpty()) {
                    try {
                        resolvedLocation = ResourceLocation.fromNamespaceAndPath("photon", this.effectName);
                    } catch (Exception e) {
                        LOGGER.error("[RemoveFXEntityClient] Invalid effect name '{}': {}",
                                this.effectName, e.getMessage(), e);
                        return;
                    }
                }

                for (Entity entity : sanitizedEntities) {
                    try {
                        removeFromEntity(entity, resolvedLocation, this.force);
                    } catch (Exception e) {
                        LOGGER.error("[RemoveFXEntityClient] Critical error while removing effect{} from entity {}: {}",
                                describeEffect(), entity, e.getMessage(), e);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[RemoveFXEntityClient] Failed to process removal request{}. Cause: {}",
                        describeEffect(), e.getMessage(), e);
            }
        }

        private void removeFromEntity(Entity entity, ResourceLocation location, boolean force) {
            ClientEffectScheduler.queueClientWork(this.delay, () -> {

                if (entity == null) return;

                var effects = EntityEffect.CACHE.get(entity);
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

                if (effects.isEmpty()) {
                    EntityEffect.CACHE.remove(entity);
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