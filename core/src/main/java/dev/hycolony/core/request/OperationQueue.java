package dev.hycolony.core.request;

import java.util.ArrayDeque;

/**
 * Runs the request manager's operations first in, first out, never nested: an operation submitted while another
 * runs (a requester or resolver calling back) only waits its turn.
 */
final class OperationQueue {
    private static final System.Logger LOG = System.getLogger(RequestManager.class.getName());

    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private boolean processing;

    void submit(Runnable op) {
        queue.addLast(op);
        if (processing) {
            return;
        }
        processing = true;
        boolean ok = false;
        try {
            Runnable next;
            while ((next = queue.pollFirst()) != null) {
                next.run();
            }
            ok = true;
        } finally {
            processing = false;
            if (!ok && !queue.isEmpty()) {
                // Never replay them inside an unrelated later call.
                LOG.log(
                        System.Logger.Level.WARNING,
                        "An operation failed; dropping {0} queued request operation(s)",
                        queue.size());
                queue.clear();
            }
        }
    }
}
