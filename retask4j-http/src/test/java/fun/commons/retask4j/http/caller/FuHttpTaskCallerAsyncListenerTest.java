package fun.commons.retask4j.http.caller;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FuHttpTaskCallerAsyncListenerTest {

    private FuHttpTaskCallerAsyncListener createListener(CompletableFuture<?> future, AtomicBoolean responseClaimed) {
        return new FuHttpTaskCallerAsyncListener(future, responseClaimed);
    }

    @Nested
    @DisplayName("onComplete")
    class OnComplete {

        @Test
        @DisplayName("onComplete does not claim response, does not cancel future")
        void onCompleteNoOp() throws IOException {
            CompletableFuture<Object> future = new CompletableFuture<>();
            AtomicBoolean responseClaimed = new AtomicBoolean(false);
            FuHttpTaskCallerAsyncListener listener = createListener(future, responseClaimed);

            AsyncEvent event = mock(AsyncEvent.class);

            assertDoesNotThrow(() -> listener.onComplete(event));
            assertFalse(responseClaimed.get());
            assertFalse(future.isDone());
        }
    }

    @Nested
    @DisplayName("onTimeout")
    class OnTimeout {

        @Test
        @DisplayName("onTimeout claims response and sends 504 SC_GATEWAY_TIMEOUT")
        void onTimeoutSends504() throws IOException {
            CompletableFuture<Object> future = new CompletableFuture<>();
            AtomicBoolean responseClaimed = new AtomicBoolean(false);
            FuHttpTaskCallerAsyncListener listener = createListener(future, responseClaimed);

            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            when(mockResponse.isCommitted()).thenReturn(false);
            AsyncContext mockContext = mock(AsyncContext.class);
            when(mockContext.getResponse()).thenReturn(mockResponse);
            AsyncEvent event = mock(AsyncEvent.class);
            when(event.getAsyncContext()).thenReturn(mockContext);

            listener.onTimeout(event);
            assertTrue(responseClaimed.get());
            verify(mockResponse).sendError(HttpServletResponse.SC_GATEWAY_TIMEOUT, "Request timed out");
            verify(mockContext).complete();
        }

        @Test
        @DisplayName("onTimeout cancels the future")
        void onTimeoutCancelsFuture() throws IOException {
            CompletableFuture<Object> future = new CompletableFuture<>();
            AtomicBoolean responseClaimed = new AtomicBoolean(false);
            FuHttpTaskCallerAsyncListener listener = createListener(future, responseClaimed);

            AsyncEvent event = mock(AsyncEvent.class);
            when(event.getAsyncContext()).thenReturn(null);

            listener.onTimeout(event);
            assertTrue(future.isCompletedExceptionally());
        }

        @Test
        @DisplayName("onTimeout does not send error if response already claimed")
        void onTimeoutResponseAlreadyClaimed() throws IOException {
            CompletableFuture<Object> future = new CompletableFuture<>();
            AtomicBoolean responseClaimed = new AtomicBoolean(true);
            FuHttpTaskCallerAsyncListener listener = createListener(future, responseClaimed);

            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            AsyncContext mockContext = mock(AsyncContext.class);
            when(mockContext.getResponse()).thenReturn(mockResponse);
            AsyncEvent event = mock(AsyncEvent.class);
            when(event.getAsyncContext()).thenReturn(mockContext);

            listener.onTimeout(event);
            verify(mockResponse, never()).sendError(anyInt(), anyString());
        }
    }

    @Nested
    @DisplayName("onError")
    class OnError {

        @Test
        @DisplayName("onError claims response and sends 500")
        void onErrorSends500() throws IOException {
            CompletableFuture<Object> future = new CompletableFuture<>();
            AtomicBoolean responseClaimed = new AtomicBoolean(false);
            FuHttpTaskCallerAsyncListener listener = createListener(future, responseClaimed);

            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            when(mockResponse.isCommitted()).thenReturn(false);
            AsyncContext mockContext = mock(AsyncContext.class);
            when(mockContext.getResponse()).thenReturn(mockResponse);
            AsyncEvent event = mock(AsyncEvent.class);
            when(event.getAsyncContext()).thenReturn(mockContext);
            when(event.getThrowable()).thenReturn(new RuntimeException("something broke"));

            listener.onError(event);
            assertTrue(responseClaimed.get());
            verify(mockResponse).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Async operation failed");
        }

        @Test
        @DisplayName("onError cancels the future")
        void onErrorCancelsFuture() throws IOException {
            CompletableFuture<Object> future = new CompletableFuture<>();
            AtomicBoolean responseClaimed = new AtomicBoolean(false);
            FuHttpTaskCallerAsyncListener listener = createListener(future, responseClaimed);

            AsyncEvent event = mock(AsyncEvent.class);
            when(event.getAsyncContext()).thenReturn(null);
            when(event.getThrowable()).thenReturn(new RuntimeException("fail"));

            listener.onError(event);
            assertTrue(future.isCompletedExceptionally());
        }

        @Test
        @DisplayName("onError does not send error if response already claimed")
        void onErrorResponseAlreadyClaimed() throws IOException {
            CompletableFuture<Object> future = new CompletableFuture<>();
            AtomicBoolean responseClaimed = new AtomicBoolean(true);
            FuHttpTaskCallerAsyncListener listener = createListener(future, responseClaimed);

            HttpServletResponse mockResponse = mock(HttpServletResponse.class);
            AsyncContext mockContext = mock(AsyncContext.class);
            when(mockContext.getResponse()).thenReturn(mockResponse);
            AsyncEvent event = mock(AsyncEvent.class);
            when(event.getAsyncContext()).thenReturn(mockContext);
            when(event.getThrowable()).thenReturn(new RuntimeException("fail"));

            listener.onError(event);
            verify(mockResponse, never()).sendError(anyInt(), anyString());
        }

        @Test
        @DisplayName("onError null throwable does not send error")
        void onErrorNullThrowable() throws IOException {
            CompletableFuture<Object> future = new CompletableFuture<>();
            AtomicBoolean responseClaimed = new AtomicBoolean(false);
            FuHttpTaskCallerAsyncListener listener = createListener(future, responseClaimed);

            AsyncEvent event = mock(AsyncEvent.class);
            when(event.getThrowable()).thenReturn(null);
            when(event.getAsyncContext()).thenReturn(null);

            assertDoesNotThrow(() -> listener.onError(event));
            assertTrue(future.isCompletedExceptionally());
        }
    }

    @Nested
    @DisplayName("onStartAsync")
    class OnStartAsync {

        @Test
        @DisplayName("onStartAsync does not throw exception")
        void onStartAsyncNoException() throws IOException {
            CompletableFuture<Object> future = new CompletableFuture<>();
            AtomicBoolean responseClaimed = new AtomicBoolean(false);
            FuHttpTaskCallerAsyncListener listener = createListener(future, responseClaimed);

            AsyncEvent event = mock(AsyncEvent.class);
            assertDoesNotThrow(() -> listener.onStartAsync(event));
        }
    }
}
