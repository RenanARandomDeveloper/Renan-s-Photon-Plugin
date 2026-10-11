package ${package}.photon_plugin;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class ModifyFXBlockServer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ModifyFXBlockServer() {
    }

    private static Identifier resolveEffectLocation(String name) {
        if (name == null || name.isEmpty()) return null;
        return name.indexOf(':') >= 0 ? Identifier.tryParse(name) : Identifier.tryParse("photon:" + name);
    }

    public static ModifyBuilder modify(BlockPos pos) {
        return new ModifyBuilder(pos != null ? List.of(pos) : null);
    }

    public static ModifyBuilder modify(double x, double y, double z) {
        return new ModifyBuilder(List.of(BlockPos.containing(x, y, z)));
    }

    public static ModifyBuilder modify(List<BlockPos> positions) {
        return new ModifyBuilder(positions);
    }

    public static class ModifyBuilder {
        private final List<BlockPos> positions;
        private String effectName;
        private Double offsetX, offsetY, offsetZ;
        private Double rotationX, rotationY, rotationZ;
        private Double scaleX, scaleY, scaleZ;

        protected ModifyBuilder(List<BlockPos> positions) {
            this.positions = positions;
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
                if (this.positions == null || this.positions.isEmpty()) {
                    LOGGER.warn("[ModifyFXBlockServer] No valid positions provided for effect modification{}.", describeEffect());
                    return;
                }

                if (this.offsetX == null && this.rotationX == null && this.scaleX == null) {
                    LOGGER.warn("[ModifyFXBlockServer] No characteristic (offset/rotation/scale) was set to modify{}.", describeEffect());
                    return;
                }

                List<BlockPos> sanitizedPositions = new ArrayList<>();
                for (BlockPos pos : this.positions) {
                    if (pos != null) sanitizedPositions.add(pos);
                }

                if (sanitizedPositions.isEmpty()) {
                    LOGGER.warn("[ModifyFXBlockServer] All provided positions were null for effect modification{}.", describeEffect());
                    return;
                }

                if (this.effectName != null && !this.effectName.isEmpty()) {
                    Identifier targetLocation = resolveEffectLocation(this.effectName);
                    if (targetLocation == null) {
                        LOGGER.warn("[ModifyFXBlockServer] Invalid effect name '{}' could not be parsed into an Identifier.", this.effectName);
                        return;
                    }
                }

                ServerEffectScheduler.queueServerWork(1, () -> {
                    try {
                        var packet = new ModifyBlockEffectPacket();
                        packet.setPositions(sanitizedPositions);

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
                        LOGGER.error("[ModifyFXBlockServer] Critical error while sending modify packet{}: {}", describeEffect(), e.getMessage(), e);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("[ModifyFXBlockServer] Failed to send modify packet{}. Cause: {}", describeEffect(), e.getMessage(), e);
            }
        }

        private String describeEffect() {
            return (this.effectName != null && !this.effectName.isEmpty())
                    ? " for effect '" + this.effectName + "'"
                    : " (all effects)";
        }
    }
}
