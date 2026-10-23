package ca;

public final class Rotation {

    public static volatile boolean spoof;
    public static volatile boolean moveFix;
    public static volatile boolean showPacketRotation;

    public static volatile float yaw;
    public static volatile float pitch;

    /** Rotation the server last saw. */
    public static volatile float yawSent;
    public static volatile float pitchSent;

    public static volatile boolean packetRotationKnown;

    private Rotation() {}
}