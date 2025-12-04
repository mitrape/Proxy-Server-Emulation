import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyServer {

    private static final int PROXY_PORT = 4000;
    private static final String FILE_SERVER_HOST = "127.0.0.1";
    private static final int FILE_SERVER_PORT = 5000;
    private static final Map<Integer, NatEntry> natTable = new ConcurrentHashMap<>();

    private static class NatEntry {
        final SocketAddress clientAddr;

        final InputStream clientIn;
        final OutputStream clientOut;

        final InputStream serverIn;
        final OutputStream serverOut;

        NatEntry(Socket clientSocket, Socket serverSocket) throws IOException {
            this.clientAddr = clientSocket.getRemoteSocketAddress();

            this.clientIn  = new BufferedInputStream(clientSocket.getInputStream());
            this.clientOut = new BufferedOutputStream(clientSocket.getOutputStream());

            this.serverIn  = new BufferedInputStream(serverSocket.getInputStream());
            this.serverOut = new BufferedOutputStream(serverSocket.getOutputStream());
        }
    }

    public static void main(String[] args) {
        try (ServerSocket proxyListen = new ServerSocket(PROXY_PORT)) {
            System.out.println("Proxy listening on port " + PROXY_PORT);
            while (true) {
                Socket client = proxyListen.accept();
                System.out.println("Client connected: " + client.getRemoteSocketAddress());
                new Thread(() -> {
                    try {
                        handleClient(client);
                    } catch (IOException e) {
                        System.out.println("Proxy handler error");
                        try { client.close(); } catch (IOException ignored) {}
                    }
                }).start();
            }
        } catch (IOException e) {
            System.out.println("An error occurred...");
        }
    }

    private static void handleClient(Socket clientSocket) throws IOException {
        Socket serverSocket = new Socket(FILE_SERVER_HOST, FILE_SERVER_PORT);
        int proxyPortNew = serverSocket.getLocalPort(); // Proxy_Port_New (OS chooses)
        NatEntry entry = new NatEntry(clientSocket, serverSocket);
        natTable.put(proxyPortNew, entry);

        try {
            while (true) {
                //read request from client
                String request = readLineFromStream(entry.clientIn);
                if (request == null) return; // client disconnected
                request = request.trim();
                if (request.isEmpty()) continue;
                //send request to server
                sendLineToServer(proxyPortNew, request);
                //read response from server and forward to client
                forwardServerResponse(proxyPortNew);
            }

        } finally {
            natTable.remove(proxyPortNew);
            try { serverSocket.close(); } catch (IOException ignored) {}
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    private static void forwardServerResponse(int proxyPortNew) throws IOException {
        NatEntry entry = natTable.get(proxyPortNew);
        if (entry == null) return;

        String firstLine = readLineFromStream(entry.serverIn);
        if (firstLine == null) return;

        sendLineToClient(proxyPortNew, firstLine);

        Long sizeMaybe = null;
        try {
            sizeMaybe = Long.parseLong(firstLine.trim());
        } catch (NumberFormatException ignored) {
            // not a number so it is LIST
        }

        if (sizeMaybe != null) {
            // DOWNLOAD response
            long size = sizeMaybe;
            if (size < 0) {
                // -1 is for file not found
                return;
            }
            forwardExactBytes(proxyPortNew, entry.serverIn, size);
        }
        else {
            // LIST response
            if (firstLine.equals("END") || firstLine.equals("-1")) return;
            while (true) {
                String line = readLineFromStream(entry.serverIn);
                if (line == null) return;
                sendLineToClient(proxyPortNew, line);
                if (line.equals("END")) break;
            }
        }
    }

    private static void sendLineToServer(int proxyPortNew, String line) throws IOException {
        NatEntry entry = natTable.get(proxyPortNew);
        if (entry == null) return;

        if (!line.endsWith("\n")) line = line + "\n";
        entry.serverOut.write(line.getBytes(StandardCharsets.UTF_8));
        entry.serverOut.flush();
    }

    private static void sendLineToClient(int proxyPortNew, String line) throws IOException {
        NatEntry entry = natTable.get(proxyPortNew);
        if (entry == null) return;

        if (!line.endsWith("\n")) line = line + "\n";
        entry.clientOut.write(line.getBytes(StandardCharsets.UTF_8));
        entry.clientOut.flush();
    }

    private static void forwardExactBytes(int proxyPortNew, InputStream serverIn, long size) throws IOException {
        NatEntry entry = natTable.get(proxyPortNew);
        if (entry == null) return;

        byte[] buffer = new byte[8192];
        long remaining = size;
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int read = serverIn.read(buffer, 0, toRead);
            if (read == -1) return;
            entry.clientOut.write(buffer, 0, read);
            remaining -= read;
        }
        entry.clientOut.flush();
    }

    private static String readLineFromStream(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b == -1) {
                if (baos.size() == 0) return null;
                break;
            }
            if (b == '\n') break;
            baos.write(b);
        }
        String s = baos.toString(StandardCharsets.UTF_8);
        if (s.endsWith("\r")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
