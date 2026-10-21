package com.pocketledger.v2;

/** Rejects a delayed load after a newer request or activity destruction. */
final class UiRevision {
    private volatile int revision;
    int next() { return ++revision; }
    boolean current(int request) { return request == revision; }
}
