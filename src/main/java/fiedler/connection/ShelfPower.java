package fiedler.connection;

import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Chiseled bookshelves (Trails &amp; Tales, 2023) → enchanting tables (1.0, 2011).
 *
 * <p>A chiseled bookshelf full of books looks like a bookshelf yet adds nothing to an enchanting table. Now each book
 * it holds is worth 1/6 of a bookshelf. A full shelf (6 books) equals one plain bookshelf, which costs only 3 books.
 * So the chiseled shelf is the expensive, flexible option and never replaces the plain one.
 * Glyph particles flow from shelves holding at least one book, because vanilla's particle check uses the same hook.
 */
public final class ShelfPower {
    public static final float BOOKS_PER_BOOKSHELF = 6.0F;

    private ShelfPower() {}

    public static float bonus(BlockState state) {
        if (!state.hasProperty(ChiseledBookShelfBlock.SLOT_0_OCCUPIED)) return 0.0F;
        int books = 0;
        for (BooleanProperty slot : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) {
            if (state.getValue(slot)) books++;
        }
        return books / BOOKS_PER_BOOKSHELF;
    }
}
