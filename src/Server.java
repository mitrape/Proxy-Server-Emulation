import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Server {

    private static final int PORT = 5000;


    public static void main(String[] args) {
        File dir = new File("files");
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("Folder files not found !");
            return;
        }
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server listening on port " + PORT);
            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("Client connected: " + client.getInetAddress());
                new Thread(() -> {
                    try {
                        Client(client);
                    } catch (IOException e) {
                        System.out.println("An error occurred...");
                    }
                }).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void Client(Socket client) throws IOException {
        try (Socket c = client;
             InputStream in = new BufferedInputStream(c.getInputStream());
             OutputStream out = new BufferedOutputStream(c.getOutputStream())) {
            while (true) {
                String Line = readLineFromStream(in);
                if (Line == null) return;
                Line = Line.trim();
                if (Line.isEmpty()) continue;
                if (Line.equals("LIST")) {
                    LIST(out);
                }
                else if (Line.startsWith("DOWNLOAD ")) {
                    String fileName = Line.substring("DOWNLOAD ".length()).trim();
                    DOWNLOAD(out, fileName);
                } else {
                    out.write("-1\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }

            }
        }
    }
    private static void LIST(OutputStream out) throws IOException {
        File dir = new File("files");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    out.write((f.getName() + "\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        out.write("END\n".getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
    private static void DOWNLOAD(OutputStream out, String fileName) throws IOException {
        File file = new File("files", fileName);
        if (!file.exists() || !file.isFile()) {
            out.write("-1\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            return;
        }
        long size = file.length();
        out.write((size + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        try (InputStream fileIn = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }

    // helper: read one UTF-8 line ending with '\n' (no BufferedReader, safe with binary)
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
