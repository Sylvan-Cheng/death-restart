package local.sylvan.deathrestart.client;

/** Exposes the vanilla connection cancellation path to the reconnect controller. */
public interface ConnectScreenAccess {
    void deathrestart$abortConnection();
}
