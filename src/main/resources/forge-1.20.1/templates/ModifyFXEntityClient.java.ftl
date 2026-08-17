package ${package}.photon_plugin;

import com.lowdragmc.photon.client.fx.EntityEffect;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class ModifyFXEntityClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ModifyFXEntityClient() {
    }

    public static ModifyBuilder modify(Entity entity) {
        return new ModifyBuilder(entity != null ? List.of(entity) : null);
    }

    public static ModifyBuilder modify(List<Entity> entities) {
        return new ModifyBuilder(entities);
    }

    public static class ModifyBuilder {
        private final List<Entity> entities;
        private String effectName;
        private Double offsetX, offsetY, offsetZ;
        private Double rotationX, rotationY, rotationZ;
        private Double scaleX, scaleY, scaleZ;

        protected ModifyBuilder(List<Entity> entities) {
            this.entities = entities;
        }

        public ModifyBuilder name(String name) {
            this.effectName = name;
            return this;
        }

        public ModifyBuilder offset(double x, double y, double z) {
            this.offsetX = x;
            this.offsetY = y;
            this.offsetZ = z;
            return this;
        }

        public ModifyBuilder rotation(double x, double y, double z) {
            this.rotationX = x;
            this.rotationY = y;
            this.rotationZ = z;
            return this;
        }

        public ModifyBuilder scale(double x, double y, double z) {
            this.scaleX = Math.max(0, x);
            this.scaleY = Math.max(0, y);
            this.scaleZ = Math.max(0, z);
            return this;
        }

        public int send() {
            if (!FMLEnvironment.dist.isClient()) {
                return 0;
            }

            try {
                if (this.entities == null || this.entities.isEmpty()) {
                    LOGGER.warn("[ModifyFXEntityClient] No valid entities provided for effect modification{}.", describeEffect());
                    return 0;
                }

                if (this.offsetX == null && this.rotationX == null && this.scaleX == null) {
                    LOGGER.warn("[ModifyFXEntityClient] No characteristic (offset/rotation/scale) was set to modify{}.", describeEffect());
                    return 0;
                }

                List<Entity> sanitizedEntities = new ArrayList<>();
                for (Entity entity : this.entities) {
                    if (entity != null) sanitizedEntities.add(entity);
                }

                if (sanitizedEntities.isEmpty()) {
                    LOGGER.warn("[ModifyFXEntityClient] All provided entities were null for effect modification{}.", describeEffect());
                    return 0;
                }

                ResourceLocation targetLocation = null;
                if (this.effectName != null && !this.effectName.isEmpty()) {
                    targetLocation = ResourceLocation.tryParse("photon:" + this.effectName);
                    if (targetLocation == null) {
                        LOGGER.warn("[ModifyFXEntityClient] Invalid effect name '{}' could not be parsed into a ResourceLocation.", this.effectName);
                        return 0;
                    }
                }

                int modifiedCount = 0;
                for (Entity entity : sanitizedEntities) {
                    modifiedCount += ClientDispatcher.applyToEntity(entity, targetLocation,
                            this.offsetX, this.offsetY, this.offsetZ,
                            this.rotationX, this.rotationY, this.rotationZ,
                            this.scaleX, this.scaleY, this.scaleZ);
                }
                return modifiedCount;
            } catch (Exception e) {
                LOGGER.error("[ModifyFXEntityClient] Failed to process modification request{}. Cause: {}", describeEffect(), e.getMessage(), e);
                return 0;
            }
        }

        private String describeEffect() {
            return (this.effectName != null && !this.effectName.isEmpty())
                    ? " for effect '" + this.effectName + "'"
                    : " (all effects)";
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientDispatcher {

        private ClientDispatcher() {
        }

        static int applyToEntity(Entity entity, ResourceLocation targetLocation,
                                  Double offsetX, Double offsetY, Double offsetZ,
                                  Double rotationX, Double rotationY, Double rotationZ,
                                  Double scaleX, Double scaleY, Double scaleZ) {
            if (entity == null) return 0;

            var activeEffects = EntityEffect.CACHE.get(entity);
            if (activeEffects == null || activeEffects.isEmpty()) {
                return 0;
            }

            int modifiedCount = 0;
            for (var effect : new ArrayList<>(activeEffects)) {
                if (effect == null || effect.getFx() == null) {
                    continue;
                }

                if (targetLocation != null && !targetLocation.equals(effect.getFx().getFxLocation())) {
                    continue;
                }

                var runtime = effect.getRuntime();
                if (runtime == null || !runtime.isAlive()) {
                    continue;
                }

                try {
                    if (offsetX != null) {
                        effect.setOffset(offsetX, offsetY, offsetZ);
                    }

                    if (rotationX != null) {
                        effect.setRotation(rotationX, rotationY, rotationZ);
                        if (effect.autoRotate == EntityEffect.AutoRotate.NONE) {
                            Quaternionf newRotation = new Quaternionf().rotationXYZ(
                                    (float) Math.toRadians(rotationX),
                                    (float) Math.toRadians(rotationY),
                                    (float) Math.toRadians(rotationZ));
                            runtime.getRoot().updateRotation(newRotation);
                        }
                    }

                    if (scaleX != null) {
                        effect.setScale(scaleX, scaleY, scaleZ);
                        runtime.getRoot().updateScale(new Vector3f(
                                scaleX.floatValue(), scaleY.floatValue(), scaleZ.floatValue()));
                    }

                    modifiedCount++;
                } catch (Exception e) {
                    LOGGER.error("[ModifyFXEntityClient] Failed to modify active FX on entity {}: {}", entity, e.getMessage(), e);
                }
            }

            return modifiedCount;
        }
    }
}
