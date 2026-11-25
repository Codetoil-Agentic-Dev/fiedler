package fiedler.mixin;

import fiedler.connection.ShelfPower;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;
import org.spongepowered.asm.mixin.Mixin;

/**
 * NeoForge routes enchanting power through {@link IBlockExtension#getEnchantPowerBonus}. Vanilla blocks we don't own
 * can only take part by overriding it, so this mixin adds that one override to ChiseledBookShelfBlock.
 */
@Mixin(ChiseledBookShelfBlock.class)
public abstract class ChiseledBookShelfBlockMixin implements IBlockExtension {
    @Override
    public float getEnchantPowerBonus(BlockState state, BlockGetter level, BlockPos pos) {
        return ShelfPower.bonus(state);
    }
}
