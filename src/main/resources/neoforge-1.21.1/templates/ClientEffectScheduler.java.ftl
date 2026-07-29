package ${package}.photon_plugin;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import net.minecraft.server.TickTask;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@OnlyIn(Dist.CLIENT)
public final class ClientEffectScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Queue<IntObjectPair<Runnable>> workToBeScheduled = new ConcurrentLinkedQueue<>();
    private static final PriorityQueue<TickTask> workQueue = new PriorityQueue<>(Comparator.comparingInt(TickTask::getTick));
    private static final AtomicLong currentTick = new AtomicLong(0);

    static {
        NeoForge.EVENT_BUS.register(ClientEffectScheduler.class);
    }

    private ClientEffectScheduler() {
    }

    public static void queueClientWork(int delayTicks, Runnable action) {
        workToBeScheduled.add(new IntObjectImmutablePair<>(Math.max(0, delayTicks), action));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        long tick = currentTick.incrementAndGet();

        IntObjectPair<Runnable> work;
        while ((work = workToBeScheduled.poll()) != null) {
            workQueue.add(new TickTask((int) (tick + work.leftInt()), work.right()));
        }

        while (!workQueue.isEmpty() && tick >= workQueue.peek().getTick()) {
            TickTask task = workQueue.poll();
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.error("[ClientEffectScheduler] Critical error while executing scheduled client work: {}", e.getMessage(), e);
            }
        }
    }
}