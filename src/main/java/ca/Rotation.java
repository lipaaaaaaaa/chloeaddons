package ca;

/**
 * Single source of truth for the rotation we send to the server.
 * Kill Aura owns the yaw, Scaffold owns the pitch (Kill Aura wins while it spoofs).
 * Modules WRITE these fields, mixins READ them, so movement packets, interactions and
 * client-side hit checks can never disagree about which rotation the server has on file.
 */
public final class Rotation {

    public static boolean spoof;            // true = sent rotation differs from the camera
    public static float yaw, pitch;         // the rotation every outgoing packet must carry
    public static float yawSent, pitchSent; // what we last actually put on the wire

    private Rotation() {}
}
