package fun.commons.retask4j.core.exception;

public class FuTaskCallbackException extends RuntimeException {
    private final boolean retryable;

    public FuTaskCallbackException(String message) {
        super(message);
        this.retryable = true;
    }

    public FuTaskCallbackException(String message, Throwable cause) {
        super(message, cause);
        this.retryable = true;
    }

    public FuTaskCallbackException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public FuTaskCallbackException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
