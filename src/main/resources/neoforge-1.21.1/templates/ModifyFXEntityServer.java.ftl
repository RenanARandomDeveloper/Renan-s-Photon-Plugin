package ${package}.photon_plugin;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class ModifyFXEntityServer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ModifyFXEntityServer() {
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

        public void send() {
            try {
                if (this.entities == null || this.entities.isEmpty()) {
                    LOGGER.warn("[ModifyFXEntityServer] No valid entities provided for effect modification{}.", describeEffect());
                    return;
                }

                if (this.offsetX == null && this.rotationX == null && this.scaleX == null) {
                    LOGGER.warn("[ModifyFXEntityServer] No characteristic (offset/rotation/scale) was set to modify{}.", describeEffect());
                    return;
                }

                List<Entity> sanitizedEntities = new ArrayList<>();
                for (Entity entity : this.entities) {
                    if (entity != null) sanitizedEntities.add(entity);
                }

                if (sanitizedEntities.isEmpty()) {
                    LOGGER.warn("[ModifyFXEntityServer] All provided entities were null for effect modification{}.", describeEffect());
                    return;
                }

                ServerEffectScheduler.queueServerWork(1, () -> {
                    try {
                        var packet = new ModifyEntityEffectPacket();

                        List<Integer> ids = new ArrayList<>();
                        for (Entity entity : sanitizedEntities) {
                            ids.add(entity.getId());
                        }
                        packet.setEntityIds(ids);

                        if (this.effectName != null && !this.effectName.isEmpty()) {
                            packet.setName(this.effectName);
                        }
                        if (this.offsetX != null) {
                            packet.setOffset(this.offsetX, this.offsetY, this.offsetZ);
                        }
                        if (this.rotationX != null) {
                            packet.setRotation(this.rotationX, this.rotationY, this.rotationZ);
                        }
                        if (this.scaleX != null) {
                            packet.setScale(this.scaleX, this.scaleY, this.scaleZ);
                        }

                        PacketDistributor.sendToAllPlayers(packet);
                    } catch (Exception e) {
                        LOGGER.error("[ModifyFXEntityServer] Critical error while sending modify packet{}: {}", describeEffect(), e.getMessage(), e);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("[ModifyFXEntityServer] Failed to send modify packet{}. Cause: {}", describeEffect(), e.getMessage(), e);
            }
        }

        private String describeEffect() {
            return (this.effectName != null && !this.effectName.isEmpty())
                    ? " for effect '" + this.effectName + "'"
                    : " (all effects)";
        }
    }
}