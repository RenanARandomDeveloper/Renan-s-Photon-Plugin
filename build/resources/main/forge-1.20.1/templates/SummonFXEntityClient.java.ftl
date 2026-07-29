package ${package}.photon_plugin;

import com.lowdragmc.photon.client.fx.EntityEffect;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class SummonFXEntityClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SummonFXEntityClient() {
    }

    public static EffectBuilder create(Entity entity) {
        return new EffectBuilder(entity != null ? List.of(entity) : null);
    }

    public static EffectBuilder create(List<Entity> entities) {
        return new EffectBuilder(entities);
    }

    public enum AutoRotateMode {
        NONE,
        FORWARD,
        LOOK,
        XROT
    }

    public static class EffectBuilder {
        private final List<Entity> entities;
        private String effectName;
        private Vec3 offset = Vec3.ZERO;
        private Vec3 rotation = Vec3.ZERO;
        private Vec3 scale = new Vec3(1, 1, 1);
        private int delay = 0;
        private boolean forcedDeath = false;
        private boolean allowMulti = false;
        private AutoRotateMode autoRotate = AutoRotateMode.NONE;

        protected EffectBuilder(List<Entity> entities) {
            this.entities = entities;
        }

        public EffectBuilder name(String name) {
            this.effectName = name;
            return this;
        }

        public EffectBuilder offset(double x, double y, double z) {
            this.offset = new Vec3(x, y, z);
            return this;
        }

        public EffectBuilder rotation(double x, double y, double z) {
            this.rotation = new Vec3(x, y, z);
            return this;
        }

        public EffectBuilder scale(double x, double y, double z) {
            this.scale = new Vec3(Math.max(0, x), Math.max(0, y), Math.max(0, z));
            return this;
        }

        public EffectBuilder delay(int delayTicks) {
            this.delay = Math.max(0, delayTicks);
            return this;
        }

        public EffectBuilder forcedDeath(boolean forcedDeath) {
            this.forcedDeath = forcedDeath;
            return this;
        }

        public EffectBuilder allowMulti(boolean allowMulti) {
            this.allowMulti = allowMulti;
            return this;
        }

        public EffectBuilder autoRotate(AutoRotateMode autoRotate) {
            this.autoRotate = autoRotate != null ? autoRotate : AutoRotateMode.NONE;
            return this;
        }

        public void send() {
            if (!FMLEnvironment.dist.isClient()) {
                return;
            }

            try {
                if (this.effectName == null) {
                    this.effectName = "";
                }
                if (this.effectName.isEmpty()) {
                    LOGGER.warn("[SummonFXEntityClient] Effect name not defined (use .name()). Continuing with an empty effect name.");
                }
                if (this.entities == null || this.entities.isEmpty()) {
                    LOGGER.warn("[SummonFXEntityClient] No valid entities provided for effect '{}'.", this.effectName);
                    return;
                }

                List<Entity> sanitizedEntities = new ArrayList<>();
                for (Entity entity : this.entities) {
                    if (entity != null) sanitizedEntities.add(entity);
                }

                if (sanitizedEntities.isEmpty()) {
                    LOGGER.warn("[SummonFXEntityClient] All entities were null for effect '{}'.", this.effectName);
                    return;
                }

                ClientDispatcher.dispatch(sanitizedEntities, this.effectName, this.offset, this.rotation,
                        this.scale, this.delay, this.forcedDeath, this.allowMulti, this.autoRotate);
            } catch (Exception e) {
                LOGGER.error("[SummonFXEntityClient] Failed to execute effect '{}'. Cause: {}", this.effectName, e.getMessage(), e);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientDispatcher {

        private ClientDispatcher() {
        }

        static void dispatch(List<Entity> entities, String effectName, Vec3 offset, Vec3 rotation, Vec3 scale,
                             int delay, boolean forcedDeath, boolean allowMulti, AutoRotateMode autoRotateMode) {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath("photon", effectName);

            Minecraft.getInstance().execute(() -> {
                try {
                    var level = Minecraft.getInstance().level;
                    if (level == null) {
                        LOGGER.warn("[SummonFXEntityClient] Client level unavailable for effect '{}'.", effectName);
                        return;
                    }

                    var fx = FXHelper.getFX(location);
                    if (fx == null) {
                        LOGGER.warn("[SummonFXEntityClient] FX '{}' not found.", location);
                        return;
                    }

                    EntityEffect.AutoRotate autoRotate = toPhotonAutoRotate(autoRotateMode);

                    for (Entity entity : entities) {
                        var effect = new EntityEffect(fx, level, entity, autoRotate);
                        effect.setOffset(offset.x, offset.y, offset.z);
                        effect.setRotation(rotation.x, rotation.y, rotation.z);
                        effect.setScale(scale.x, scale.y, scale.z);
                        effect.setDelay(delay);
                        effect.setForcedDeath(forcedDeath);
                        effect.setAllowMulti(allowMulti);
                        effect.start();
                    }
                } catch (Exception e) {
                    LOGGER.error("[SummonFXEntityClient] Critical error while executing effect '{}': {}", effectName, e.getMessage(), e);
                }
            });
        }
        private static EntityEffect.AutoRotate toPhotonAutoRotate(AutoRotateMode mode) {
            return switch (mode) {
                case FORWARD -> EntityEffect.AutoRotate.FORWARD;
                case LOOK -> EntityEffect.AutoRotate.LOOK;
                case XROT -> EntityEffect.AutoRotate.XROT;
                default -> EntityEffect.AutoRotate.NONE;
            };
        }
    }
}