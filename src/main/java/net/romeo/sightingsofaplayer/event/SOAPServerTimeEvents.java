package net.romeo.sightingsofaplayer.event;

import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.saveddata.SOAPServerTimeData;
import net.romeo.sightingsofaplayer.soap_event.SoapEventSystem;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Drives the server-wide clock kept in {@link SOAPServerTimeData}, and with it the
 * {@link SoapEventSystem}: the clock counts the world's ticks, and each of those ticks is one step
 * of the event schedule, so world time and the things that happen in it never drift apart.
 * <p>
 * Registered on <b>both</b> physical sides on purpose: {@link ServerTickEvent} is fired by the
 * logical server, which in singleplayer runs inside the client process. Restricting this to
 * {@link Dist#DEDICATED_SERVER} would silently stop counting in singleplayer worlds.
 * <p>
 * What one tick costs here: a call to {@link SOAPServerTimeData#get(MinecraftServer)}, which is a
 * hash map lookup because the record is already loaded and cached, plus the increment itself. No
 * I/O, no allocation, nothing that grows with uptime - which is what makes counting forever free.
 * The record is flagged dirty as it grows, but the actual disk write happens on Minecraft's own
 * level-save schedule, so no saving work is ever done from this hot path.
 */
@EventBusSubscriber(modid = SOAP.MODID, value = { Dist.CLIENT, Dist.DEDICATED_SERVER })
public class SOAPServerTimeEvents {
    private static final Collection<AbstractMap.SimpleEntry<Runnable, Integer>> workQueue = new ConcurrentLinkedQueue();

    /**
     *
     * @param ticksToWait - how many ticks to wait before running the queued action
     * @param action - the action to run
     */
    public static void queueWork(int ticksToWait, Runnable action) {
        workQueue.add(new AbstractMap.SimpleEntry(action, ticksToWait));
    }

    private static void tickWorkQueue() {
        List<AbstractMap.SimpleEntry<Runnable, Integer>> actions = new ArrayList();

        workQueue.forEach((work) -> {
            work.setValue(work.getValue() - 1);
            if (work.getValue() == 0) {
                actions.add(work);
            }
        });

        actions.forEach((e) -> e.getKey().run());
        workQueue.removeAll(actions);
    }

    /**
     * Runs and clears all remaining tasks in the queue regardless of their remaining tick count.
     */
    private static void flushWorkQueue() {
        while (!workQueue.isEmpty()) {
            List<AbstractMap.SimpleEntry<Runnable, Integer>> actions = new ArrayList<>(workQueue);
            workQueue.removeAll(actions);

            for (AbstractMap.SimpleEntry<Runnable, Integer> entry : actions) {
                try {
                    entry.getKey().run();
                } catch (Exception e) {
                    SOAP.LOGGER.error("Error executing queued task during server shutdown", e);
                }
            }
        }
    }

    /**
     * Counts one tick.
     * <p>
     * The count comes from the server's tick loop, not from the level's clock, and that is exactly
     * why {@code /time} cannot touch it: that command rewrites {@code dayTime} and never goes
     * anywhere near this record.
     * <p>
     * {@code tickServer} runs on every pass of the server loop, even while the world is frozen,
     * but a frozen world does not advance - so a frozen world must not advance our clock either.
     * {@link net.minecraft.world.TickRateManager#runsNormally()} is the same check vanilla uses
     * before ticking a level, which also makes {@code /tick step} count its single step and
     * {@code /tick rate} keep one world tick meaning one tick.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        if (!server.tickRateManager().runsNormally()) {
            return;
        }

        tickWorkQueue();

        SOAPServerTimeData.get(server).tick();
        SoapEventSystem.tick(server);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        SOAPServerTimeData data = SOAPServerTimeData.get(event.getServer());

        SOAP.LOGGER.info("World time so far: {} ticks ({} days)",
                data.getTotalTicks(), data.getTotalDays());
    }

    /**
     * Executes all remaining tasks in the workQueue before the server shuts down.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        flushWorkQueue();
    }
}

