package ${package}.photon_plugin;

import com.lowdragmc.photon.client.fx.EntityEffect;
import com.lowdragmc.photon.command.EntityEffectCommand;
import com.mojang.logging.LogUtils;
import ${package}.${JavaModName}Mod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import com.lowdragmc.photon.PhotonNetworking;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class SummonFXEntityServer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SummonFXEntityServer() {
    }

    public static EffectBuilder create(Entity entity) {
        return new EffectBuilder(entity != null ? List.of(entity) : null);
    }

    public static EffectBuilder create(List<Entity> entities) {
        return new EffectBuilder(entities);
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
        private EntityEffect.AutoRotate autoRotate = EntityEffect.AutoRotate.NONE;

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

        public EffectBuilder autoRotate(EntityEffect.AutoRotate autoRotate) {
            this.autoRotate = autoRotate != null ? autoRotate : EntityEffect.AutoRotate.NONE;
            return this;
        }

        public void send() {
            try {
                if (this.effectName == null) {
                    this.effectName = "";
                }
                if (this.effectName.isEmpty()) {
                    LOGGER.warn("[SummonFXEntityServer] Effect name not defined (use .name()). Continuing with an empty effect name.");
                }
                if (this.entities == null || this.entities.isEmpty()) {
                    LOGGER.warn("[SummonFXEntityServer] No valid entities provided for effect '{}'.", this.effectName);
                    return;
                }

                List<Entity> sanitizedEntities = new ArrayList<>();
                for (Entity entity : this.entities) {
                    if (entity != null) sanitizedEntities.add(entity);
                }

                if (sanitizedEntities.isEmpty()) {
                    LOGGER.warn("[SummonFXEntityServer] All entities were null for effect '{}'.", this.effectName);
                    return;
                }
                ${JavaModName}Mod.queueServerWork(1, () -> {
                    try {
                        var effectPacket = new EntityEffectCommand();
                        effectPacket.setLocation(ResourceLocation.fromNamespaceAndPath("photon", this.effectName));
                        effectPacket.setEntities(sanitizedEntities);
                        effectPacket.setOffset(this.offset);
                        effectPacket.setRotation(this.rotation);
                        effectPacket.setScale(this.scale);
                        effectPacket.setDelay(this.delay);
                        effectPacket.setForcedDeath(this.forcedDeath);
                        effectPacket.setAllowMulti(this.allowMulti);
                        effectPacket.setAutoRotate(this.autoRotate);
                        PhotonNetworking.NETWORK.sendToAll(effectPacket);
                    } catch (Exception e) {
                        LOGGER.error("[SummonFXEntityServer] Critical error while sending packet for effect '{}': {}", this.effectName, e.getMessage(), e);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("[SummonFXEntityServer] Failed to send packet for effect '{}'. Cause: {}", this.effectName, e.getMessage(), e);
            }
        }
    }
}