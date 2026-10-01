package net.romeo.sightingsofaplayer.item;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.romeo.sightingsofaplayer.SOAP;
import net.romeo.sightingsofaplayer.saveddata.SOAPPlayerSavedData;

/**
 * The Amulet is a plain, non-wearable item. It is not equipment and is never
 * equipped: the player simply right-clicks while holding it.
 * <p>
 * Using it is a one time deal. A single use consumes exactly one Amulet and
 * permanently records the player as "has used an Amulet" in the {@link SOAPPlayerSavedData}
 * of the server (world) it was used on. A player can therefore only ever consume
 * one Amulet per server, and the server always knows both who used one and who did not.
 */
public class AmuletItem extends Item {
    public AmuletItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        // Always the server-wide record: the overworld's data storage is global, so this is
        // the same single instance for every dimension.
        SOAPPlayerSavedData data = SOAPPlayerSavedData.get(serverLevel.getServer());

        if (data.hasUsedAmulet(player.getUUID())) {
            // Already used one on this server: refuse without consuming the item.
            player.displayClientMessage(
                    Component.translatable("item.sightingsofaplayer.amulet.already_used"), true);
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
            return InteractionResultHolder.fail(stack);
        }

        // Remember the player, then consume exactly one Amulet.
        data.markAmuletUsed(player.getUUID());
        stack.shrink(1);

        player.displayClientMessage(
                Component.translatable("item.sightingsofaplayer.amulet.used"), true);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);

        SOAP.LOGGER.info("Player {} ({}) used an Amulet", player.getName().getString(), player.getUUID());

        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("item.sightingsofaplayer.amulet.tooltip"));
    }
}
