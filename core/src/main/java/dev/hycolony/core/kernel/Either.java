package dev.hycolony.core.kernel;

/** A success ({@link Left}) or a failure ({@link Right}). */
public sealed interface Either<L, R> {
    record Left<L, R>(L value) implements Either<L, R> {}

    record Right<L, R>(R value) implements Either<L, R> {}
}
