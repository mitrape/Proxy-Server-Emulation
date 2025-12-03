import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyServer {

    private static final int PROXY_PORT = 4000;
    private static final String FILE_SERVER_HOST = "127.0.0.1";
    private static final int FILE_SERVER_PORT = 5000;

    private static final Map<Integer, SocketAddress> natTable = new ConcurrentHashMap<>();

    public static void main(String[] args){
        try (ServerSocket proxyListen = new ServerSocket(PROXY_PORT)) {
            System.out.println("Proxy listening on port " + PROXY_PORT);
            while (true) {
                Socket clientSocket = proxyListen.accept();
                System.out.println("Client connected to proxy: " + clientSocket.getRemoteSocketAddress());
                new Thread(() -> {
                    try {
                        Client(clientSocket);
                    } catch (IOException e) {
                        System.out.println("An error occurred...");
                    }
                }).start();
            }
        } catch (IOException e) {
            System.out.println("An error occurred...");
        }
    }
    private static void Client(Socket clientSocket) throws IOException {
        int proxyPatPort = -1;
        try (Socket c = clientSocket;
             InputStream clientIn = new BufferedInputStream(c.getInputStream());
             OutputStream clientOut = new BufferedOutputStream(c.getOutputStream());
             Socket serverSocket = new Socket(FILE_SERVER_HOST, FILE_SERVER_PORT);
             InputStream serverIn = new BufferedInputStream(serverSocket.getInputStream());
             OutputStream serverOut = new BufferedOutputStream(serverSocket.getOutputStream())) {

            proxyPatPort = serverSocket.getLocalPort();  //Proxy_Port_New    OS tells this   for example : 61234
            SocketAddress clientAddr = c.getRemoteSocketAddress();  //(Client_IP, Client_Port)  for example : 192.168.1.10:55012
            natTable.put(proxyPatPort, clientAddr);
            System.out.println("NAT ADD: " + clientAddr + " <-> " + proxyPatPort + ")");

            while (true) {
                String line = readLineFromStream(clientIn);
                if (line == null) return;
                line = line.trim();
                if (line.isEmpty()) continue;
                serverOut.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                serverOut.flush();

                if (line.equals("LIST")) {
                    LIST(serverIn, clientOut);
                } else if (line.startsWith("DOWNLOAD ")) {
                    DOWNLOAD(serverIn, clientOut);
                } else {
                    String resp = readLineFromStream(serverIn);  //-1\n
                    if (resp == null) return;
                    clientOut.write((resp + "\n").getBytes(StandardCharsets.UTF_8));
                    clientOut.flush();
                }
            }

        } finally {
            if (proxyPatPort != -1) {
                natTable.remove(proxyPatPort);
            }
        }
    }

    private static void LIST(InputStream serverIn, OutputStream clientOut) throws IOException {
        while (true) {
            String line = readLineFromStream(serverIn);
            if (line == null) return;
            clientOut.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            clientOut.flush();
            if (line.equals("END")) break;
        }
    }


    private static void DOWNLOAD(InputStream serverIn, OutputStream clientOut) throws IOException {
        String firstLine = readLineFromStream(serverIn);
        if (firstLine == null) return;
        clientOut.write((firstLine + "\n").getBytes(StandardCharsets.UTF_8));
        clientOut.flush();
        long size;
        try {
            size = Long.parseLong(firstLine.trim());
        } catch (NumberFormatException e) {
            System.out.println("An error occurred...");
            return;
        }
        if (size < 0) return;
        byte[] buffer = new byte[8192];
        long remaining = size;
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int read = serverIn.read(buffer, 0, toRead);
            if (read == -1) return;
            clientOut.write(buffer, 0, read);
            remaining -= read;
        }
        clientOut.flush();
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
