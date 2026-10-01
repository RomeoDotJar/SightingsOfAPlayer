package net.romeo.sightingsofaplayer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.romeo.sightingsofaplayer.SOAPConfig;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.event.SOAPServerTimeEvents;
import net.romeo.sightingsofaplayer.myth.Myth;
import net.romeo.sightingsofaplayer.myth.MythRoster;
import net.romeo.sightingsofaplayer.saveddata.SOAPMythSavedData;
import net.romeo.sightingsofaplayer.saveddata.SOAPEventSavedData;
import net.romeo.sightingsofaplayer.saveddata.SOAPServerTimeData;
import net.romeo.sightingsofaplayer.soap_event.SoapEvent;
import net.romeo.sightingsofaplayer.soap_event.SoapEventContext;
import net.romeo.sightingsofaplayer.soap_event.SoapEventPool;
import net.romeo.sightingsofaplayer.soap_event.SoapEventSystem;

/**
 * The read-only window into this mod's records: {@code /soap time} for the world clock and
 * {@code /soap events} for the {@code soap_event} schedule.
 * <p>
 * Nothing here can move the world clock, which is the whole point of it. The one thing that can
 * bypass the event schedule - {@code /soap events force} - is restricted to operators, because it
 * exists for testing a new event, not for play.
 * <p>
 * Registered without a side restriction because the logical server also runs inside a singleplayer
 * client; a dedicated-server-only registration would leave the commands unavailable there.
 */
@EventBusSubscriber(modid = SOAP.MODID)
public class SOAPCommands {

    /** How many past runs {@code /soap events} lists. The record itself keeps more. */
    private static final int RECENT_SHOWN = 5;

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("soap")
                .then(Commands.literal("time")
                        .executes(SOAPCommands::showWorldTime)
                        .then(Commands.literal("add")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("added_seconds", LongArgumentType.longArg())
                                        .executes(SOAPCommands::addWorldTime)
                                )
                        )
                )
                .then(Commands.literal("myths")
                        .executes(SOAPCommands::showMythStatus)
                )
                .then(Commands.literal("events")
                        .executes(SOAPCommands::showEventStatus)
                        .then(Commands.literal("list")
                                .executes(SOAPCommands::listEvents)
                        )
                        .then(Commands.literal("force")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(SOAPCommands::suggestEventIds)
                                        .executes(SOAPCommands::forceEvent)
                                )
                        )
                        .then(Commands.literal("reroll")
                                .executes(SOAPCommands::rerollEvent)
                        )
                        .then(Commands.literal("rate")
                                .executes(SOAPCommands::showEventRate)
                        )
                )
        );
    }

    private static int showWorldTime(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        SOAPServerTimeData data = SOAPServerTimeData.get(source.getServer());

        source.sendSuccess(() -> Component.translatable(
                "commands.sightingsofaplayer.time.total",
                data.getTotalTicks(), data.getTotalDays()), false);

        source.sendSuccess(() -> Component.translatable(
                "commands.sightingsofaplayer.time.day",
                data.getDayNumber(), data.getTicksIntoDay(), SOAPServerTimeData.TICKS_PER_DAY), false);

        return 1;
    }

    private static int addWorldTime(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        long ticks = LongArgumentType.getLong(context, "added_seconds") * SOAPServerTimeData.TICKS_PER_SECOND;

        SOAPServerTimeData.get(context.getSource().getServer()).tick(ticks);
        SoapEventSystem.tick(context.getSource().getServer(),ticks);

        showWorldTime(context);

        return 1;
    }

    /** {@code /soap myths} - current roster status and curse state of all myths. */
    private static int showMythStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        SOAPMythSavedData mythData = SOAPMythSavedData.get(source.getServer());

        source.sendSuccess(() -> Component.translatable("commands.sightingsofaplayer.myths.header"), false);
        for (Myth myth : Myth.values()) {
            MythRoster roster = mythData.getRoster(myth);
            boolean curse = mythData.isCurseActive(myth);
            source.sendSuccess(() -> Component.translatable("commands.sightingsofaplayer.myths.entry",
                    myth.getDisplayName(), roster.name(), curse ? "Active" : "Inactive"), false);
        }

        return 1;
    }

    /** {@code /soap events} - what is scheduled right now, and what has already happened. */
    private static int showEventStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!SOAPConfig.EVENTS_ENABLED.get()) {
            source.sendSuccess(() -> Component.translatable("commands.sightingsofaplayer.events.disabled"), false);
            return 0;
        }

        SOAPEventSavedData data = SOAPEventSavedData.get(source.getServer());

        source.sendSuccess(() -> Component.translatable("commands.sightingsofaplayer.events.status",
                data.getTotalRuns(), SoapEventPool.all().size()), false);

        SoapEvent next = SoapEventPool.byId(data.getNextEventId());
        if (next == null) {
            source.sendSuccess(() -> Component.translatable("commands.sightingsofaplayer.events.none"), false);
        } else {
            long seconds = data.getTicksUntilNext() / 20L;
            source.sendSuccess(() -> Component.translatable("commands.sightingsofaplayer.events.next",
                    eventName(next), seconds), false);
        }

        List<SOAPEventSavedData.Run> recent = data.getRecentRuns();
        if (recent.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.sightingsofaplayer.events.recent.empty"), false);
        } else {
            MutableComponent joined = Component.empty();
            for (int i = 0; i < Math.min(recent.size(), RECENT_SHOWN); i++) {
                SOAPEventSavedData.Run run = recent.get(i);
                if (i > 0) {
                    joined.append(Component.literal(", "));
                }
                joined.append(Component.translatable("commands.sightingsofaplayer.events.recent.entry",
                        byIdOrLiteral(run.eventId()),
                        run.atTick() / SOAPServerTimeData.TICKS_PER_DAY + 1L));
            }

            source.sendSuccess(() -> Component.translatable("commands.sightingsofaplayer.events.recent",
                    joined), false);
        }

        return 1;
    }

    private static int rerollEvent(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        SOAPEventSavedData data = SOAPEventSavedData.get(server);

        data.setRetryDelay(1L);

        return 1;
    }

    private static int showEventRate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        float rate = SoapEventSystem.averageEventsPerDay(new SoapEventContext(server,server.overworld().getRandom()));

        source.sendSuccess(() -> Component.translatable("commands.sightingsofaplayer.events.rate",
                ((int)(rate*10))/10f, (short)(100*rate/SoapEventSystem.BASE_EVENTS_PER_DAY), 1
                ), false
        );

        return 1;
    }

    /** {@code /soap events list} - every registered event and whether it may run right now. */
    private static int listEvents(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        SoapEventContext eventContext = new SoapEventContext(server, RandomSource.create());
        SOAPEventSavedData data = SOAPEventSavedData.get(server);

        source.sendSuccess(() -> Component.translatable("commands.sightingsofaplayer.events.list.header",
                SoapEventPool.all().size()), false);

        for (SoapEvent event : SoapEventPool.all()) {
            boolean available = event.canRun(eventContext);

            source.sendSuccess(() -> Component.translatable("commands.sightingsofaplayer.events.list.entry",
                    eventName(event),
                    Component.translatable(available
                            ? "commands.sightingsofaplayer.events.available"
                            : "commands.sightingsofaplayer.events.unavailable"),
                    data.getRunCount(event.id()),
                    data.getRunCount(event.id())>0 ? data.getRunTickAt(event.id())/1000+"k" : "NaN"), false);
        }

        return 1;
    }

    /** {@code /soap events force <id>} - runs one event right now, schedule or not. Operators only. */
    private static int forceEvent(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String id = StringArgumentType.getString(context, "id");

        SoapEvent event = SoapEventPool.byId(id);
        if (event == null) {
            source.sendFailure(Component.translatable("commands.sightingsofaplayer.events.unknown", id));
            return 0;
        }

        boolean happened = SoapEventSystem.triggerNow(source.getServer(), event);
        source.sendSuccess(() -> Component.translatable(happened
                        ? "commands.sightingsofaplayer.events.forced"
                        : "commands.sightingsofaplayer.events.force.failed",
                eventName(event)), true);

        return happened ? 1 : 0;
    }

    private static CompletableFuture<Suggestions> suggestEventIds(CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(SoapEventPool.ids(), builder);
    }

    /** Readable name of a registered event. */
    private static Component eventName(SoapEvent event) {
        return Component.translatable(event.nameKey());
    }

    /** Readable name for an id that may no longer belong to a registered event. */
    private static Component byIdOrLiteral(String eventId) {
        SoapEvent event = SoapEventPool.byId(eventId);
        return event != null ? eventName(event) : Component.literal(eventId);
    }
}
