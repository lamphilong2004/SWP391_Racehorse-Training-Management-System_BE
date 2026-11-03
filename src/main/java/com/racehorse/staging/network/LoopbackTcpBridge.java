package com.racehorse.staging.network;

import java.io.IOException;
import java.net.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opaque TCP forwarding. SQL Server TLS stays end-to-end between JDBC and SQL Server. */
final class LoopbackTcpBridge implements AutoCloseable {
    private final InetSocketAddress socks;
    private final InetSocketAddress destination;
    private final ServerSocket listener;
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final Semaphore connections = new Semaphore(12);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread acceptor;

    LoopbackTcpBridge(int port, InetSocketAddress socks, InetSocketAddress destination) throws IOException {
        if (socks.isUnresolved() || !socks.getAddress().isLoopbackAddress()) {
            throw new IllegalArgumentException("SOCKS proxy must be loopback.");
        }
        this.socks = socks;
        this.destination = destination;
        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 12);
        acceptor = Thread.ofVirtual().name("sql-bridge-accept").start(this::accept);
    }

    int port() { return listener.getLocalPort(); }
    InetAddress bindAddress() { return listener.getInetAddress(); }
    boolean isRunning() { return !closed.get() && acceptor.isAlive(); }

    Socket connect() throws IOException {
        Socket socket = new Socket(new Proxy(Proxy.Type.SOCKS, socks));
        try {
            socket.connect(destination, 5000);
            socket.setKeepAlive(true);
            return socket;
        } catch (IOException failure) {
            socket.close();
            throw failure;
        }
    }

    private void accept() {
        try {
            while (!closed.get()) {
                Socket client = listener.accept();
                if (!connections.tryAcquire()) { client.close(); continue; }
                sockets.add(client);
                Thread.startVirtualThread(() -> forward(client));
            }
        } catch (IOException failure) {
            if (!closed.get()) System.err.println("NETWORK_BRIDGE_LISTENER_FAILED");
        }
    }

    private void forward(Socket client) {
        Socket remote = null;
        try {
            remote = connect();
            sockets.add(remote);
            // close() may race a pending SOCKS connect; never leave the resulting socket alive.
            if (closed.get()) return;
            Socket upstream = remote;
            Thread incoming = Thread.startVirtualThread(() -> copy(upstream, client));
            copy(client, upstream);
            incoming.join();
        } catch (IOException failure) {
            if (!closed.get()) System.err.println("NETWORK_BRIDGE_CONNECTION_UNAVAILABLE");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
        } finally {
            closeSocket(client);
            if (remote != null) closeSocket(remote);
            connections.release();
        }
    }

    private static void copy(Socket from, Socket to) {
        try {
            from.getInputStream().transferTo(to.getOutputStream());
            to.shutdownOutput();
        } catch (IOException failure) {
            try { from.close(); } catch (IOException ignored) { }
            try { to.close(); } catch (IOException ignored) { }
        }
    }

    private void closeSocket(Socket socket) {
        sockets.remove(socket);
        try { socket.close(); } catch (IOException ignored) { }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { listener.close(); } catch (IOException ignored) { }
        sockets.forEach(this::closeSocket);
    }
}
