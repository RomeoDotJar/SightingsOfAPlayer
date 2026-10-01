package net.romeo.sightingsofaplayer;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class SOAPConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", SOAPConfig::validateItemName);

    // -------------------------------------------------------------------------------------------------
    // SOAP event system
    // -------------------------------------------------------------------------------------------------

    public static final ModConfigSpec.BooleanValue EVENTS_ENABLED = BUILDER
            .comment("Whether the soap_event system runs at all.")
            .define("eventsEnabled", true);

    public static final ModConfigSpec.IntValue EVENT_NO_REPEAT_WINDOW = BUILDER
            .comment("How many of the most recently run events may not be picked again.",
                    "The rule relaxes automatically when a pool is too small to satisfy it. 0 disables it.")
            .defineInRange("eventNoRepeatWindow", 4, 0, 64);

    public static final ModConfigSpec.IntValue INVESTIGATION_DURATION_DAYS = BUILDER
            .comment("The duration, in days, of a single myth investigation.")
            .defineInRange("investigationDurationDays", 5, 3, 15);

    public static final ModConfigSpec.IntValue PACE_PERCENTAGE = BUILDER
            .comment("A percentage of the original pace.",
                    "Dictates just how often all events will happen.",
                    "Does not change how long investigations will last.")
            .defineInRange("pacePercentage", 100, 1, 1_000);

    public static final ModConfigSpec.IntValue TEMPERATURE_PERCENTAGE = BUILDER
            .comment("A percentage of how much your experience can deviate from the average.",
                    "Dictates just how randomly the events will happen.")
            .defineInRange("temperaturePercentage", 30, 0, 100);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
}
