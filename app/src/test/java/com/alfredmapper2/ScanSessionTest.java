package com.alfredmapper2;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ScanSessionTest {
    @Test
    public void acceptsOnlyCurrentActiveSession() {
        ScanSession session = new ScanSession();
        long first = session.start();

        assertTrue(session.accepts(first));

        session.stop();
        assertFalse(session.accepts(first));
    }

    @Test
    public void restartingInvalidatesPreviouslyQueuedResults() {
        ScanSession session = new ScanSession();
        long first = session.start();
        session.stop();
        long second = session.start();

        assertFalse(session.accepts(first));
        assertTrue(session.accepts(second));
    }

    @Test
    public void repeatedStopIsSafeAndInvalidatesSession() {
        ScanSession session = new ScanSession();
        long token = session.start();
        session.stop();
        session.stop();

        assertFalse(session.accepts(token));
    }

    @Test
    public void stressTransitionsNeverRevalidateAnOldToken() {
        ScanSession session = new ScanSession();
        long previous = -1;
        for (int i = 0; i < 100_000; i++) {
            long current = session.start();
            assertTrue(session.accepts(current));
            assertFalse(session.accepts(previous));
            session.stop();
            assertFalse(session.accepts(current));
            previous = current;
        }
    }

    @Test
    public void concurrentReadersNeverAcceptThePublishedStaleToken() throws Exception {
        ScanSession session = new ScanSession();
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean staleTokenAccepted = new AtomicBoolean();
        AtomicLong publishedToken = new AtomicLong(-1);
        AtomicLong stoppedToken = new AtomicLong(-1);
        CountDownLatch readersReady = new CountDownLatch(4);
        Thread[] readers = new Thread[4];

        for (int i = 0; i < readers.length; i++) {
            readers[i] = new Thread(() -> {
                readersReady.countDown();
                while (running.get()) {
                    long token = publishedToken.get();
                    if (token >= 0 && token == stoppedToken.get() && session.accepts(token)) {
                        staleTokenAccepted.set(true);
                    }
                }
            });
            readers[i].start();
        }

        readersReady.await();
        long previous = -1;
        try {
            for (int i = 0; i < 250_000; i++) {
                long token = session.start();
                publishedToken.set(token);
                assertTrue(session.accepts(token));
                session.stop();
                stoppedToken.set(token);
                assertFalse(session.accepts(token));
                if (previous >= 0) assertFalse(session.accepts(previous));
                previous = token;
            }
        } finally {
            session.stop();
            running.set(false);
            for (Thread reader : readers) reader.join();
        }
        assertFalse(staleTokenAccepted.get());
    }
}
