package fun.commons.retask4j.http.caller;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class FuHttpTaskCallerAsyncListener implements AsyncListener {

    private final CompletableFuture<?> future;
    private final AtomicBoolean responseClaimed;

    public FuHttpTaskCallerAsyncListener(CompletableFuture<?> future, AtomicBoolean responseClaimed) {
        this.future = future;
        this.responseClaimed = responseClaimed;
    }

    @Override
    public void onComplete(AsyncEvent event) throws IOException {
        log.debug("Async operation completed");
    }

    @Override
    public void onTimeout(AsyncEvent event) throws IOException {
        log.warn("Async operation timed out");
        cancelFuture("Request timed out");
        if (responseClaimed != null && responseClaimed.compareAndSet(false, true)) {
            AsyncContext asyncContext = event.getAsyncContext();
            if (asyncContext != null) {
                HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
                if (response != null && !response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT, "Request timed out");
                }
                try {
                    asyncContext.complete();
                } catch (IllegalStateException e) {
                    log.debug("Async context already completed on timeout");
                }
            }
        }
    }

    @Override
    public void onError(AsyncEvent event) throws IOException {
        Throwable throwable = event.getThrowable();
        if (throwable != null) {
            log.error("Async operation failed", throwable);
        }
        cancelFuture("Async operation failed");
        if (responseClaimed != null && responseClaimed.compareAndSet(false, true)) {
            AsyncContext asyncContext = event.getAsyncContext();
            if (asyncContext != null) {
                HttpServletResponse response = (HttpServletResponse) asyncContext.getResponse();
                if (response != null && !response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Async operation failed");
                }
                try {
                    asyncContext.complete();
                } catch (IllegalStateException e) {
                    log.debug("Async context already completed on error");
                }
            }
        }
    }

    @Override
    public void onStartAsync(AsyncEvent event) throws IOException {
        log.debug("Async operation started");
    }

    private void cancelFuture(String reason) {
        if (future != null && !future.isDone()) {
            future.completeExceptionally(new RuntimeException(reason));
        }
    }
}
