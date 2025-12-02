import java.io.*;
import java.net.Socket;

public class Client {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 5000;
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in))) {

            System.out.println("Connected to server: " + host + ":" + port);

            /*System.out.print("Type a message to send: ");
            String msg = keyboard.readLine();

            out.println(msg);                 // send to server
            String reply = in.readLine();     // read server reply (one line)
            System.out.println("Server replied: " + reply);*/
            while (true) {
                System.out.println("menu:");
                System.out.println("1->LIST");
                System.out.println("2->DOWNLOAD");
                System.out.println("Please enter your command:");
                String reply = keyboard.readLine().trim();
                if (reply.equals("1")) {
                    out.println("LIST");
                    LIST(in, out);
                } else if (reply.equals("2")) {
                    System.out.println("Please enter file name:");
                    String fileName = keyboard.readLine().trim();
                    out.println("DOWNLOAD "+fileName);
                    DOWNLOAD();
                } else {
                    System.out.println("Unknown command, try again");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void LIST (BufferedReader in, PrintWriter out) throws IOException{
        String firstLine = in.readLine();
        if(firstLine.isEmpty()){
            System.out.println("An error occurred...");
        }
        else {
            System.out.println("Files:\n");
            System.out.println(firstLine+"\n");
            while (true){
                String line = in.readLine();
                System.out.println(line+"\n");
            }
        }
    }
}
