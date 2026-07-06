package io.databridge.migration;

public class MigrationException extends RuntimeException {
    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
