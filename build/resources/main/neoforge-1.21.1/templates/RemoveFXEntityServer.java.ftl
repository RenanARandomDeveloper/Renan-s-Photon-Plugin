package ${package}.photon_plugin;

import com.lowdragmc.photon.command.RemoveEntityEffectCommand;
import com.mojang.logging.LogUtils;
import ${package}.${JavaModName}Mod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class RemoveFXEntityServer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private RemoveFXEntityServer() {
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
            try {
                if (this.entities == null || this.entities.isEmpty()) {
                    LOGGER.warn("[RemoveFXEntityServer] No valid entities provided for effect removal{}.",
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
                    LOGGER.warn("[RemoveFXEntityServer] All provided entities were null for effect removal{}.",
                            describeEffect());
                    return;
                }

                ResourceLocation resolvedLocation = null;
                if (this.effectName != null && !this.effectName.isEmpty()) {
                    try {
                        resolvedLocation = ResourceLocation.fromNamespaceAndPath("photon", this.effectName);
                    } catch (Exception e) {
                        LOGGER.error("[RemoveFXEntityServer] Invalid effect name '{}': {}",
                                this.effectName, e.getMessage(), e);
                        return;
                    }
                }

                final ResourceLocation finalLocation = resolvedLocation;
                final List<Entity> finalEntities = sanitizedEntities;
                final boolean finalForce = this.force;

                ${JavaModName}Mod.queueServerWork(this.delay, () -> {
                    try {
                        var removePacket = new RemoveEntityEffectCommand();
                        removePacket.setEntities(finalEntities);
                        removePacket.setForce(finalForce);
                        if (finalLocation != null) {
                            removePacket.setLocation(finalLocation);
                        }
                        PacketDistributor.sendToAllPlayers(removePacket);
                    } catch (Exception e) {
                        LOGGER.error("[RemoveFXEntityServer] Critical error while sending removal packet{}: {}",
                                describeEffect(), e.getMessage(), e);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("[RemoveFXEntityServer] Failed to process removal request{}. Cause: {}",
                        describeEffect(), e.getMessage(), e);
            }
        }

        private String describeEffect() {
            return (this.effectName != null && !this.effectName.isEmpty())
                    ? " for effect '" + this.effectName + "'"
                    : " (all effects)";
        }
    }
}