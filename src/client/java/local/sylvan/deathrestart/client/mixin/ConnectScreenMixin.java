package local.sylvan.deathrestart.client.mixin;

import io.netty.channel.ChannelFuture;
import local.sylvan.deathrestart.client.ConnectScreenAccess;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin implements ConnectScreenAccess {
    @Shadow
    private volatile Connection connection;
    @Shadow
    private ChannelFuture channelFuture;
    @Shadow
    private volatile boolean aborted;

    @Override
    public void deathrestart$abortConnection() {
        synchronized (this) {
            this.aborted = true;
            if (this.channelFuture != null) {
                this.channelFuture.cancel(true);
                this.channelFuture = null;
            }
            if (this.connection != null) {
                this.connection.disconnect(ConnectScreen.ABORT_CONNECTION);
            }
        }
    }
}
