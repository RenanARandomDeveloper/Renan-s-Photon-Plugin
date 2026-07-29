package ${package}.photon_plugin;

import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.Collection;

public final class CheckFXEntity {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CheckFXEntity() {
    }

    public static QueryBuilder check(Entity entity) {
        return new QueryBuilder(entity);
    }

    public static class QueryBuilder {
        private final Entity entity;
        private String effectName;

        protected QueryBuilder(Entity entity) {
            this.entity = entity;
        }

        public QueryBuilder name(String name) {
            this.effectName = name;
            return this;
        }

        public boolean send() {
            if (!FMLEnvironment.dist.isClient()) {
                return false;
            }

            try {
                if (this.entity == null) {
                    LOGGER.warn("[CheckFXEntity] Null entity provided for FX active check.");
                    return false;
                }

                return ClientDispatcher.isActive(this.entity, this.effectName);
            } catch (Exception e) {
                LOGGER.error("[CheckFXEntity] Failed to check active FX for effect '{}': {}", this.effectName, e.getMessage(), e);
                return false;
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientDispatcher {

        private ClientDispatcher() {
        }

        static boolean isActive(Entity entity, String effectName) {
            Collection<EntityEffectExecutor> activeEffects = EntityEffectExecutor.CACHE.get(entity);
            if (activeEffects == null || activeEffects.isEmpty()) {
                return false;
            }

            boolean nameProvided = effectName != null && !effectName.isEmpty();
            if (!nameProvided) {
                return true;
            }

            ResourceLocation targetLocation = ResourceLocation.tryParse("photon:" + effectName);
            if (targetLocation == null) {
                LOGGER.warn("[CheckFXEntity] Invalid effect name '{}' could not be parsed into a ResourceLocation.", effectName);
                return false;
            }

            for (EntityEffectExecutor executor : activeEffects) {
                if (executor == null || executor.getFx() == null) {
                    continue;
                }

                ResourceLocation currentLocation = executor.getFx().getFxLocation();
                if (targetLocation.equals(currentLocation)) {
                    return true;
                }
            }

            return false;
        }
    }
}