package ${package}.photon_plugin;

import com.lowdragmc.photon.client.fx.BlockEffect;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.Collection;

public final class CheckFXBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CheckFXBlock() {
    }

    public static QueryBuilder check(BlockPos pos) {
        return new QueryBuilder(pos);
    }

    public static QueryBuilder check(double x, double y, double z) {
        return new QueryBuilder(BlockPos.containing(x, y, z));
    }

    public static class QueryBuilder {
        private final BlockPos pos;
        private String effectName;

        protected QueryBuilder(BlockPos pos) {
            this.pos = pos;
        }

        public QueryBuilder name(String name) {
            this.effectName = name;
            return this;
        }

        public boolean send() {
            if (!FMLEnvironment.dist.isClient()) {
                return false;
            }

            try {
                if (this.pos == null) {
                    LOGGER.warn("[CheckFXBlock] Null BlockPos provided for FX active check.");
                    return false;
                }
                return ClientDispatcher.isActive(this.pos, this.effectName);
            } catch (Exception e) {
                LOGGER.error("[CheckFXBlock] Failed to check active FX at position '{}' for effect '{}': {}", this.pos, this.effectName, e.getMessage(), e);
                return false;
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientDispatcher {

        private ClientDispatcher() {
        }

        static boolean isActive(BlockPos pos, String effectName) {
            Collection<BlockEffect> activeEffects = BlockEffect.CACHE.get(pos);
            if (activeEffects == null || activeEffects.isEmpty()) {
                return false;
            }

            boolean nameProvided = effectName != null && !effectName.isEmpty();
            if (!nameProvided) {
                return true;
            }

            ResourceLocation targetLocation = ResourceLocation.tryParse("photon:" + effectName);
            if (targetLocation == null) {
                LOGGER.warn("[CheckFXBlock] Invalid effect name '{}' could not be parsed into a ResourceLocation.", effectName);
                return false;
            }

            for (BlockEffect executor : activeEffects) {
                if (executor == null || executor.getFx() == null) {
                    continue;
                }

                ResourceLocation currentLocation = executor.getFx().getFxLocation();
                if (targetLocation.equals(currentLocation)) {
                    return true;
                }
            }

            return false;
        }
    }
}