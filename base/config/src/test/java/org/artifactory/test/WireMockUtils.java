package org.artifactory.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FatalStartupException;
import com.github.tomakehurst.wiremock.core.Options;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.artifactory.util.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.TestSocketUtils;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.net.BindException;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static java.lang.Thread.sleep;

public class WireMockUtils {
    private static final Logger log = LoggerFactory.getLogger(WireMockUtils.class);

    public static WireMockServer startWireMockServer(@Nullable WireMockConfiguration serverOptions) {
        int maxRetries = 3;
        int tryNumber = 0;
        WireMockServer wireMockServer;
        Optional<FatalStartupException> runtimeException = Optional.empty();
        while (tryNumber++ < maxRetries) {
            int port = getListeningPort(serverOptions);
            try {
                wireMockServer = new WireMockServer(getWireMockOptions(serverOptions, port));
                wireMockServer.start();
                configureFor(port);
                return wireMockServer;
            } catch (FatalStartupException e) {
                runtimeException = Optional.of(e);
                Throwable cause = ExceptionUtils.getRootCause(e);
                if (cause instanceof BindException) {
                    log.error("Port: {} already in use, retrying", port);
                    try {
                        sleep(2000);
                    } catch (InterruptedException interruptedException) {
                        throw new RuntimeException(interruptedException);
                    }
                    continue;
                }
                throw e;
            }
        }
        throw runtimeException.get();
    }

    private static int getListeningPort(WireMockConfiguration serverOptions) {
        return serverOptions != null ? serverOptions.portNumber() : TestSocketUtils.findAvailableTcpPort();
    }

    private static Options getWireMockOptions(WireMockConfiguration serverOptions, int port) {
        return serverOptions != null ? serverOptions : wireMockConfig().port(port);
    }

    public static WireMockServer startWireMockServer(int port) {
        var server = startWireMockServer(wireMockConfig().port(port));
        log.info("Starting wiremock server on selected port: {}", server.port());
        return server;
    }

    public static WireMockServer startWireMockServer() {
        var server = startWireMockServer(null);
        log.info("Starting wiremock server on found port: {}", server.port());
        return server;
    }

    public static void stopWireMockServer(@Nonnull WireMockServer wireMockServer) {
        try {
            wireMockServer.resetAll();
            wireMockServer.shutdownServer();
        } catch (Exception e) {
            System.err.println("Failed to stop wire mock server: " + e);
        }
    }
}
