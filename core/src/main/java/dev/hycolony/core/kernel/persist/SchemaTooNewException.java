package dev.hycolony.core.kernel.persist;

public class SchemaTooNewException extends RuntimeException {
    public SchemaTooNewException(int found, int supported) {
        super("Save schema " + found + " is newer than supported " + supported);
    }
}
