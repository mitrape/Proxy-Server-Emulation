import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 4000;

        try (Socket socket = new Socket(host, port);
             InputStream in = new BufferedInputStream(socket.getInputStream());
             OutputStream outBytes = new BufferedOutputStream(socket.getOutputStream());
             BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to server: " + host + ":" + port);

            while (true) {
                System.out.println("menu:");
                System.out.println("1->LIST");
                System.out.println("2->DOWNLOAD");
                System.out.println("Please enter your command:");

                String reply = keyboard.readLine().trim();
                if (reply.equals("1")) {
                    outBytes.write("LIST\n".getBytes(StandardCharsets.UTF_8));
                    outBytes.flush();
                    LIST(in);
                } else if (reply.equals("2")) {
                    System.out.println("Please enter file name:");
                    String fileName = keyboard.readLine().trim();
                    outBytes.write(("DOWNLOAD " + fileName + "\n").getBytes(StandardCharsets.UTF_8));
                    outBytes.flush();
                    DOWNLOAD(in, fileName);
                } else {
                    System.out.println("Unknown command, try again");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void LIST(InputStream in) throws IOException {
        String firstLine = readLineFromStream(in);
        if (firstLine == null || firstLine.trim().isEmpty()) {
            System.out.println("An error occurred...");
            return;
        }
        if (firstLine.equals("-1")) {
            System.out.println("Unknown command.");
            return;
        }
        System.out.println("Files:\n");
        if (firstLine.equals("END")) {
            System.out.println("no files\n");
            return;
        }
        System.out.println(firstLine + "\n");
        while (true) {
            String line = readLineFromStream(in);
            if (line == null) {
                System.out.println("Stream ended early.");
                return;
            }
            if (line.equals("END")) break;
            System.out.println(line + "\n");
        }
    }

    public static void DOWNLOAD(InputStream in, String fileName) throws IOException {
        String firstLine = readLineFromStream(in);
        if (firstLine == null || firstLine.trim().isEmpty()) {
            System.out.println("An error occurred...");
            return;
        }
        long size;
        try {
            size = Long.parseLong(firstLine.trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid size received: " + firstLine);
            return;
        }
        if (size < 0) {
            System.out.println("File not found.");
            return;
        }
        File finalFile = new File(System.getProperty("user.dir"), fileName);
        try (OutputStream fileOut = new BufferedOutputStream(new FileOutputStream(finalFile))) {
            byte[] buffer = new byte[8192];
            long remaining = size;
            while (remaining > 0) {
                int toRead = (int) Math.min(buffer.length, remaining);
                int read = in.read(buffer, 0, toRead);
                if (read == -1) {
                    System.out.println("Stream ended early.");
                    return;
                }
                fileOut.write(buffer, 0, read);
                remaining -= read;
            }
            fileOut.flush();
        }
        System.out.println("File saved to: " + finalFile.getAbsolutePath());
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
