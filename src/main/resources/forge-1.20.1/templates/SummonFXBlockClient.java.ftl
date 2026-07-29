package ${package}.photon_plugin;

import com.lowdragmc.photon.client.fx.BlockEffect;
import com.lowdragmc.photon.client.fx.FXHelper;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

public final class SummonFXBlockClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SummonFXBlockClient() {
    }

    public static EffectBuilder create(double x, double y, double z) {
        return new EffectBuilder(BlockPos.containing(x, y, z));
    }

    public static EffectBuilder create(BlockPos pos) {
        return new EffectBuilder(pos);
    }

    public static class EffectBuilder {
        private final BlockPos pos;
        private String effectName;
        private Vec3 offset = Vec3.ZERO;
        private Vec3 rotation = Vec3.ZERO;
        private Vec3 scale = new Vec3(1, 1, 1);
        private int delay = 0;
        private boolean forcedDeath = false;
        private boolean allowMulti = false;
        private boolean checkState = false;

        protected EffectBuilder(BlockPos pos) {
            this.pos = pos;
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

        public EffectBuilder checkState(boolean checkState) {
            this.checkState = checkState;
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
                    LOGGER.warn("[SummonFXBlockClient] Effect name not defined (use .name()). Continuing with an empty effect name.");
                }
                if (this.pos == null) {
                    LOGGER.warn("[SummonFXBlockClient] No valid position provided for effect '{}'.", this.effectName);
                    return;
                }

                ClientDispatcher.dispatch(this.pos, this.effectName, this.offset, this.rotation, this.scale,
                        this.delay, this.forcedDeath, this.allowMulti, this.checkState);
            } catch (Exception e) {
                LOGGER.error("[SummonFXBlockClient] Failed to start effect '{}'. Cause: {}", this.effectName, e.getMessage(), e);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientDispatcher {

        private ClientDispatcher() {
        }

        static void dispatch(BlockPos pos, String effectName, Vec3 offset, Vec3 rotation, Vec3 scale,
                             int delay, boolean forcedDeath, boolean allowMulti, boolean checkState) {
            try {
                var level = Minecraft.getInstance().level;
                if (level == null || !level.isLoaded(pos)) {
                    LOGGER.warn("[SummonFXBlockClient] Chunk not loaded at the provided position for effect '{}'.", effectName);
                    return;
                }

                var fx = FXHelper.getFX(ResourceLocation.fromNamespaceAndPath("photon", effectName));
                if (fx == null) {
                    LOGGER.warn("[SummonFXBlockClient] Effect '{}' not found by FXHelper.", effectName);
                    return;
                }

                var effect = new BlockEffect(fx, level, pos);
                effect.setOffset(offset.x, offset.y, offset.z);
                effect.setRotation(rotation.x, rotation.y, rotation.z);
                effect.setScale(scale.x, scale.y, scale.z);
                effect.setDelay(delay);
                effect.setForcedDeath(forcedDeath);
                effect.setAllowMulti(allowMulti);
                effect.setCheckState(checkState);
                effect.start();
            } catch (Exception e) {
                LOGGER.error("[SummonFXBlockClient] Failed to start effect '{}'. Cause: {}", effectName, e.getMessage(), e);
            }
        }
    }
}