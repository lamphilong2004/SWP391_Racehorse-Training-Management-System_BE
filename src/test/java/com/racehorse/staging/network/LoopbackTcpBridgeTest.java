package com.racehorse.staging.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual local sockets and a SOCKS5 fixture, never the Leader's SQL Server. */
@Timeout(15)
class LoopbackTcpBridgeTest {
    @Test void forwardsOpaqueBidirectionalBytesThroughSocks5AndBindsOnlyIpv4Loopback() throws Exception {
        try (var socks = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            byte[] payload = new byte[32768]; new java.util.Random(391).nextBytes(payload);
            var result = new CompletableFuture<String>();
            Thread.startVirtualThread(() -> {
                try (Socket server = socks.accept()) {
                    server.setSoTimeout(5000);
                    DataInputStream in = new DataInputStream(server.getInputStream());
                    var out = server.getOutputStream();
                    assertEquals(5, in.readUnsignedByte());
                    byte[] methods = in.readNBytes(in.readUnsignedByte());
                    assertTrue(java.util.stream.IntStream.range(0, methods.length).anyMatch(i -> methods[i] == 0));
                    out.write(new byte[]{5, 0}); out.flush();
                    assertEquals(5, in.readUnsignedByte()); assertEquals(1, in.readUnsignedByte()); assertEquals(0, in.readUnsignedByte());
                    assertEquals(1, in.readUnsignedByte());
                    String address = InetAddress.getByAddress(in.readNBytes(4)).getHostAddress();
                    assertEquals(1433, in.readUnsignedShort());
                    out.write(new byte[]{5, 0, 0, 1, 127, 0, 0, 1, 0, 0}); out.flush();
                    assertArrayEquals(payload, in.readNBytes(payload.length));
                    assertEquals(-1, in.read()); // Client half-close reaches the remote unchanged.
                    out.write(payload); out.flush();
                    result.complete(address);
                } catch (Throwable failure) { result.completeExceptionally(failure); }
            });
            try (var bridge = new LoopbackTcpBridge(0, new InetSocketAddress(socks.getInetAddress(), socks.getLocalPort()), new InetSocketAddress("100.83.34.4", 1433));
                 var client = new Socket("127.0.0.1", bridge.port())) {
                assertEquals("127.0.0.1", bridge.bindAddress().getHostAddress());
                assertFalse(bridge.bindAddress().isAnyLocalAddress());
                client.setSoTimeout(5000);
                client.getOutputStream().write(payload); client.shutdownOutput();
                assertArrayEquals(payload, client.getInputStream().readAllBytes());
                assertEquals("100.83.34.4", result.get(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test void rejectsPublicProxyAndFailsClosedWhenThePrivateRouteIsUnreachable() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new LoopbackTcpBridge(0, new InetSocketAddress("8.8.8.8", 1055), new InetSocketAddress("100.83.34.4", 1433)));
        int unavailable;
        try (var socket = new ServerSocket(0)) { unavailable = socket.getLocalPort(); }
        try (var bridge = new LoopbackTcpBridge(0, new InetSocketAddress("127.0.0.1", unavailable), new InetSocketAddress("100.83.34.4", 1433))) {
            assertThrows(IOException.class, bridge::connect);
        }
    }

    @Test void closeIsIdempotentAndReleasesTheListener() throws Exception {
        var bridge = new LoopbackTcpBridge(0, new InetSocketAddress("127.0.0.1", 1055), new InetSocketAddress("100.83.34.4", 1433));
        int port = bridge.port();
        bridge.close(); bridge.close();
        assertFalse(bridge.isRunning());
        assertThrows(IOException.class, () -> new Socket("127.0.0.1", port));
    }
}
