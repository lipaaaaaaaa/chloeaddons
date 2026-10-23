package ca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Diagnostics for silent rotation + move fix (CA-MF lines in latest.log).
 * Every call site is guarded by Rotation.DEBUG. Set it to false when done.
 */
public final class MoveFixDebug {

    public static final int AI_HEAD = 0;
    public static final int INPUT = 1;
    public static final int AI_RETURN = 2;
    public static final int SEND = 3;
    public static final int PACKET = 4;

    private static final String[] NAMES = {
            "aiStepHead", "inputRemap", "aiStepReturn", "sendPosition", "movePacket"
    };

    private static final Logger LOG = LoggerFactory.getLogger("CA-MF");
    private static final int[] COUNTS = new int[NAMES.length];
    private static int ticks;

    private MoveFixDebug() {}

    public static void hit(int id) {
        COUNTS[id]++;
    }

    public static int tick() {
        return ticks;
    }

    public static void log(String format, Object... args) {
        LOG.info(format, args);
    }

    /** Called at the start of every LocalPlayer.aiStep. Prints hook counters every 100 ticks. */
    public static void beginTick() {
        ticks++;
        hit(AI_HEAD);

        if (ticks % 100 != 0) {
            return;
        }

        StringBuilder sb = new StringBuilder("heartbeat ticks=").append(ticks);

        for (int i = 0; i < NAMES.length; i++) {
            sb.append(' ').append(NAMES[i]).append('=').append(COUNTS[i]);
        }

        LOG.info(sb.toString());
    }
}