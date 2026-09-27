package net.romeo.sightingsofaplayer.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class NullBlock extends Block {
    public NullBlock() {
        super(Properties.of()
                .sound(SoundType.EMPTY)
                .strength(.25F, 3.0F)
                .lightLevel(state -> 2)
                .pushReaction(PushReaction.DESTROY));
    }

    @Override
    public void playerDestroy(@Nonnull Level level, @Nonnull Player player, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nullable BlockEntity blockEntity, @Nonnull ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);

        // Breaking this block by hand (not by a piston or any other means) hurts every
        // living entity within a 4 block radius of it.
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(4.0D))) {
            entity.hurt(level.damageSources().magic(), 6.0F);
        }
    }
}
