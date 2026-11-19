package com.crmplatform.shared.events;

import java.util.function.Supplier;

/**
 * Tracks whether the current thread is executing an automation step, and how deep the trigger
 * chain is, so changes made by automation can be labelled and loops stopped (BR-107).
 */
public final class AutomationChain {

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private AutomationChain() {
    }

    /** Depth for events published now: 0 for user actions, parent depth + 1 inside automation. */
    public static int nextDepth() {
        Context c = CURRENT.get();
        return c == null ? 0 : c.depth() + 1;
    }

    public static boolean active() {
        return CURRENT.get() != null;
    }

    public static String runLabel() {
        Context c = CURRENT.get();
        return c == null ? null : "Automation (run " + c.runId() + ")";
    }

    public static <T> T within(String runId, int depth, Supplier<T> work) {
        Context previous = CURRENT.get();
        CURRENT.set(new Context(runId, depth));
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    private record Context(String runId, int depth) {
    }
}
