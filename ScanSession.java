package com.alfredmapper2;

final class ScanSession {
    private long generation;
    private boolean active;

    synchronized long start() {
        active = true;
        return ++generation;
    }

    synchronized void stop() {
        active = false;
        generation++;
    }

    synchronized boolean accepts(long token) {
        return active && generation == token;
    }
}
