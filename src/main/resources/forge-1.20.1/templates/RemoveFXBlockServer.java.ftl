package ${package}.photon_plugin;

import com.lowdragmc.photon.command.RemoveBlockEffectCommand;
import com.mojang.logging.LogUtils;
import ${package}.${JavaModName}Mod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import com.lowdragmc.photon.PhotonNetworking;
import org.slf4j.Logger;

import java.lang.reflect.Field;

public final class RemoveFXBlockServer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Field POS_FIELD;

    static {
        Field field = null;
        try {
            field = RemoveBlockEffectCommand.class.getDeclaredField("pos");
            field.setAccessible(true);
        } catch (Exception e) {
            LOGGER.error("[RemoveFXBlockServer] Could not locate/access 'pos' field in " +
                    "RemoveBlockEffectCommand via reflection. Effect removal by block position " +
                    "will be unavailable until this is fixed. Cause: {}", e.getMessage(), e);
        }
        POS_FIELD = field;
    }

    private RemoveFXBlockServer() {
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
            try {
                if (this.pos == null) {
                    LOGGER.warn("[RemoveFXBlockServer] Null BlockPos provided for effect removal{}.",
                            describeEffect());
                    return;
                }
                if (POS_FIELD == null) {
                    LOGGER.error("[RemoveFXBlockServer] Operation canceled: reflective access to 'pos' field " +
                            "in RemoveBlockEffectCommand is unavailable (static initialization failed).");
                    return;
                }

                ResourceLocation resolvedLocation = null;
                if (this.effectName != null && !this.effectName.isEmpty()) {
                    try {
                        resolvedLocation = ResourceLocation.fromNamespaceAndPath("photon", this.effectName);
                    } catch (Exception e) {
                        LOGGER.error("[RemoveFXBlockServer] Invalid effect name '{}': {}",
                                this.effectName, e.getMessage(), e);
                        return;
                    }
                }

                final ResourceLocation finalLocation = resolvedLocation;
                final BlockPos finalPos = this.pos;
                final boolean finalForce = this.force;

                ${JavaModName}Mod.queueServerWork(this.delay, () -> {
                    try {
                        var removePacket = new RemoveBlockEffectCommand();

                        try {
                            POS_FIELD.set(removePacket, finalPos);
                        } catch (Exception e) {
                            LOGGER.error("[RemoveFXBlockServer] Failed to set block position via reflection " +
                                    "for effect{} at {}: {}", describeEffect(), finalPos, e.getMessage(), e);
                            return;
                        }

                        removePacket.setForce(finalForce);
                        if (finalLocation != null) {
                            removePacket.setLocation(finalLocation);
                        }

                        PhotonNetworking.NETWORK.sendToAll(removePacket);
                    } catch (Exception e) {
                        LOGGER.error("[RemoveFXBlockServer] Critical error while sending removal packet{} at position {}: {}",
                                describeEffect(), finalPos, e.getMessage(), e);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("[RemoveFXBlockServer] Failed to process removal request{} at position {}. Cause: {}",
                        describeEffect(), this.pos, e.getMessage(), e);
            }
        }

        private String describeEffect() {
            return (this.effectName != null && !this.effectName.isEmpty())
                    ? " for effect '" + this.effectName + "'"
                    : " (all effects)";
        }
    }
}