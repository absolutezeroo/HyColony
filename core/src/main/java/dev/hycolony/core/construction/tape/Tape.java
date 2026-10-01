package dev.hycolony.core.construction.tape;

import dev.hycolony.core.kernel.BlockPos;

/** A construction tape to place: its cell, its shape and its rotation in quarter turns (see {@link TapeShape}). */
public record Tape(BlockPos pos, TapeShape shape, int rotation) {}
