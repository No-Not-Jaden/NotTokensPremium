package me.jadenp.nottokenspremium;

import com.google.inject.Inject;
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
public class NotTokensPremium {


    private Logger logger;
    private final Path dataDirectory;
    private final ProxyServer server;

    @Inject
    public NotTokensPremium(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        logger.info("Hello from NotTokens!");

    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
    }
}
