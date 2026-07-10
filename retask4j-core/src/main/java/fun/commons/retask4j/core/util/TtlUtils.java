package fun.commons.retask4j.core.util;

import java.util.List;

public final class TtlUtils {

    private TtlUtils() {}

    public static int computeTtlBuffer(List<Integer> retryPlan, int executeExpire) {
        long ttlBuffer = 0;
        if (retryPlan != null) {
            for (int delay : retryPlan) {
                ttlBuffer += Math.max(delay, 1) + executeExpire;
            }
        }
        if (ttlBuffer > Integer.MAX_VALUE) {
            ttlBuffer = Integer.MAX_VALUE;
        }
        return (int) ttlBuffer;
    }
}
