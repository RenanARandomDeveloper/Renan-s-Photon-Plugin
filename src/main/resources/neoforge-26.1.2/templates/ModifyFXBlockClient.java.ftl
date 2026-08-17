package ${package}.photon_plugin;

import com.lowdragmc.photon.client.fx.BlockEffectExecutor;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLEnvironment;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public final class ModifyFXBlockClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ModifyFXBlockClient() {
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

        public int send() {
            if (!FMLEnvironment.getDist().isClient()) {
                return 0;
            }

            try {
                if (this.positions == null || this.positions.isEmpty()) {
                    LOGGER.warn("[ModifyFXBlockClient] No valid positions provided for effect modification{}.", describeEffect());
                    return 0;
                }

                if (this.offsetX == null && this.rotationX == null && this.scaleX == null) {
                    LOGGER.warn("[ModifyFXBlockClient] No characteristic (offset/rotation/scale) was set to modify{}.", describeEffect());
                    return 0;
                }

                List<BlockPos> sanitizedPositions = new ArrayList<>();
                for (BlockPos pos : this.positions) {
                    if (pos != null) sanitizedPositions.add(pos);
                }

                if (sanitizedPositions.isEmpty()) {
                    LOGGER.warn("[ModifyFXBlockClient] All provided positions were null for effect modification{}.", describeEffect());
                    return 0;
                }

                Identifier targetLocation = null;
                if (this.effectName != null && !this.effectName.isEmpty()) {
                    targetLocation = Identifier.tryParse("photon:" + this.effectName);
                    if (targetLocation == null) {
                        LOGGER.warn("[ModifyFXBlockClient] Invalid effect name '{}' could not be parsed into a Identifier.", this.effectName);
                        return 0;
                    }
                }

                int modifiedCount = 0;
                for (BlockPos pos : sanitizedPositions) {
                    modifiedCount += ClientDispatcher.applyToPos(pos, targetLocation,
                            this.offsetX, this.offsetY, this.offsetZ,
                            this.rotationX, this.rotationY, this.rotationZ,
                            this.scaleX, this.scaleY, this.scaleZ);
                }
                return modifiedCount;
            } catch (Exception e) {
                LOGGER.error("[ModifyFXBlockClient] Failed to process modification request{}. Cause: {}", describeEffect(), e.getMessage(), e);
                return 0;
            }
        }

        private String describeEffect() {
            return (this.effectName != null && !this.effectName.isEmpty())
                    ? " for effect '" + this.effectName + "'"
                    : " (all effects)";
        }
    }

    private static final class ClientDispatcher {

        private ClientDispatcher() {
        }

        static int applyToPos(BlockPos pos, Identifier targetLocation,
                               Double offsetX, Double offsetY, Double offsetZ,
                               Double rotationX, Double rotationY, Double rotationZ,
                               Double scaleX, Double scaleY, Double scaleZ) {
            if (pos == null) return 0;

            var activeEffects = BlockEffectExecutor.CACHE.get(pos);
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
                if (runtime == null || !runtime.isValid()) {
                    continue;
                }

                try {
                    if (offsetX != null) {
                        effect.setOffset(offsetX, offsetY, offsetZ);
                        Vector3f newPos = new Vector3f(pos.getX(), pos.getY(), pos.getZ())
                                .add((float) (offsetX + 0.5), (float) (offsetY + 0.5), (float) (offsetZ + 0.5));
                        runtime.getRoot().updatePos(newPos);
                    }

                    if (rotationX != null) {
                        effect.setRotation(rotationX, rotationY, rotationZ);
                        Quaternionf newRotation = new Quaternionf().rotationXYZ(
                                (float) Math.toRadians(rotationX),
                                (float) Math.toRadians(rotationY),
                                (float) Math.toRadians(rotationZ));
                        runtime.getRoot().updateRotation(newRotation);
                    }

                    if (scaleX != null) {
                        effect.setScale(scaleX, scaleY, scaleZ);
                        runtime.getRoot().updateScale(new Vector3f(
                                scaleX.floatValue(), scaleY.floatValue(), scaleZ.floatValue()));
                    }

                    modifiedCount++;
                } catch (Exception e) {
                    LOGGER.error("[ModifyFXBlockClient] Failed to modify active FX at position {}: {}", pos, e.getMessage(), e);
                }
            }

            return modifiedCount;
        }
    }
}
