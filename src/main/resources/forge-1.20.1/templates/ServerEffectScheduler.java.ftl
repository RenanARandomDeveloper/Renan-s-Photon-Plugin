package ${package}.photon_plugin;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import net.minecraft.server.TickTask;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class ServerEffectScheduler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Queue<IntObjectPair<Runnable>> workToBeScheduled = new ConcurrentLinkedQueue<>();
    private static final PriorityQueue<TickTask> workQueue = new PriorityQueue<>(Comparator.comparingInt(TickTask::getTick));
    private static final AtomicLong currentTick = new AtomicLong(0);

    static {
        MinecraftForge.EVENT_BUS.register(ServerEffectScheduler.class);
    }

    private ServerEffectScheduler() {
    }

    public static void queueServerWork(int delayTicks, Runnable action) {
        if (action != null) {
            workToBeScheduled.add(new IntObjectImmutablePair<>(Math.max(0, delayTicks), action));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            executeScheduledWork();
        }
    }

    private static void executeScheduledWork() {
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
                LOGGER.error("[ServerEffectScheduler] Critical error while executing scheduled server work: {}", e.getMessage(), e);
            }
        }
    }
}