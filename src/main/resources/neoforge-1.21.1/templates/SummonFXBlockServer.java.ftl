package ${package}.photon_plugin;

import com.lowdragmc.photon.command.BlockEffectCommand;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

public final class SummonFXBlockServer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SummonFXBlockServer() {
    }

    private static ResourceLocation resolveEffectLocation(String name) {
        if (name == null || name.isEmpty()) return null;
        return name.indexOf(':') >= 0 ? ResourceLocation.tryParse(name) : ResourceLocation.tryParse("photon:" + name);
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
            try {
                if (this.effectName == null) {
                    this.effectName = "";
                }
                if (this.effectName.isEmpty()) {
                    LOGGER.warn("[SummonFXBlockServer] Effect name not defined (use .name()). Continuing with an empty effect name.");
                }
                if (this.pos == null) {
                    LOGGER.warn("[SummonFXBlockServer] No valid position provided for effect '{}'.", this.effectName);
                    return;
                }

                ServerEffectScheduler.queueServerWork(1, () -> {
                    try {
                        ResourceLocation fxLocation = resolveEffectLocation(this.effectName);
                        if (fxLocation == null) {
                            LOGGER.warn("[SummonFXBlockServer] Invalid effect name '{}' could not be parsed into a ResourceLocation.", this.effectName);
                            return;
                        }

                        var effectPacket = new BlockEffectCommand();
                        effectPacket.setLocation(fxLocation);
                        effectPacket.setPos(this.pos);
                        effectPacket.setOffset(this.offset);
                        effectPacket.setRotation(this.rotation);
                        effectPacket.setScale(this.scale);
                        effectPacket.setDelay(this.delay);
                        effectPacket.setForcedDeath(this.forcedDeath);
                        effectPacket.setAllowMulti(this.allowMulti);
                        effectPacket.setCheckState(this.checkState);
                        PacketDistributor.sendToAllPlayers(effectPacket);
                    } catch (Exception e) {
                        LOGGER.error("[SummonFXBlockServer] Critical error while sending packet for effect '{}': {}", this.effectName, e.getMessage(), e);
                    }
                });
            } catch (Exception e) {
                LOGGER.error("[SummonFXBlockServer] Failed to send packet for effect '{}'. Cause: {}", this.effectName, e.getMessage(), e);
            }
        }
    }
}