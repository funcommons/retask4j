package fun.commons.retask4j.core.exception;

public class FuTaskRetryExhaustedException extends RuntimeException {
    public FuTaskRetryExhaustedException(String message) {
        super(message);
    }
}
