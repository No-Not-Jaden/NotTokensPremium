package me.jadenp.nottokenspremium;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "nottokens",
        name = "NotTokens Premium",
        version = "1.0",
        authors = {"Not_Jaden"}
)
public class NotTokensPremiumVelocity {

    private Logger logger;
    private final Path dataDirectory;
    private final ProxyServer proxy;

    @Inject
    public NotTokensPremiumVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        logger.info("Hello from NotTokens!");

    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        CommandManager commandManager = proxy.getCommandManager();
        // Here you can add meta for the command, as aliases and the plugin to which it belongs (RECOMMENDED)
        CommandMeta commandMeta = commandManager.metaBuilder("test")
                // This will create a new alias for the command "/test"
                // with the same arguments and functionality
                .aliases("otherAlias", "anotherAlias")
                .plugin(this)
                .build();

        // You can replace this with "new EchoCommand()" or "new TestCommand()"
        // SimpleCommand simpleCommand = new TestCommand();
        // RawCommand rawCommand = new EchoCommand();
        // The registration is done in the same way, since all 3 interfaces implement "Command"
        SimpleCommand commandToRegister = new TokensCommand();

        // Finally, you can register the command
        commandManager.register(commandMeta, commandToRegister);
    }
}
