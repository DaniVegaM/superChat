import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    private static final int SERVER_PORT = 4446;
    private static final int BUFFER_SIZE = 1024;
    private static final String SERVER_ADDRESS = "localhost";
    private static final String MULTICAST_ADDRESS = "230.0.0.0"; //MultiCast Address
    private static String USERNAME = "User"; 
    
    private MulticastSocket clientSocket;
    private InetAddress group;
    private InetAddress serverAddress;

    private static String GREEN = "\033[1;32m";

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter a userName to start:");
        USERNAME = scanner.nextLine();
        new Client().start();
    }

    public Client() throws IOException {
        // Initialize the socket and group
        this.clientSocket = new MulticastSocket(SERVER_PORT);
        this.group = InetAddress.getByName(MULTICAST_ADDRESS);
        this.serverAddress = InetAddress.getByName(SERVER_ADDRESS);

        // Join the group
        clientSocket.joinGroup(group);
    }

    public void start() throws IOException {
        //Start listening for incoming messages from the server
        new Thread(() -> {
            try {
                listenForMessages();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        //Asking to join to the chat
        sendJoinMessage();

        //Start a scanner to allow user to write an Instruction
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\r" + GREEN + "Me: ");
            String input = scanner.nextLine();
            handleUserInput(input);
        }
    }

    private void listenForMessages() throws IOException {
        byte[] receiveData = new byte[BUFFER_SIZE];
        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);

            // System.out.println("RECIBI ESTO: " + receivePacket);

            String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
            
            // Filtrar mensajes propios basándose en el nombre de usuario
            if (!message.startsWith("\r\033[1;32m" + USERNAME + ":") && !message.startsWith("USERS: ") && !message.startsWith("PRIVATE:")) {
                System.out.println(message);
            } else if(message.startsWith("USERS: ")){
                String copyMessage = message;
                //Get users list
                String userList = message.substring(7);//Delete prefix "USERS: "
                String[] users = userList.split(",");

                if (users[users.length - 1].equals(USERNAME)) {
                    System.out.println(copyMessage);
                }
            } else{
                String[] parts = message.split(":");

                if (parts[0].equals("PRIVATE")) {
                    String receiver = parts[2]; // El receiver está en la posición 2
                    if (receiver.equals(USERNAME)) {
                        message = "[" + parts[0] + "] " + parts[1] + ": " + parts[3]; // El mensaje está en la posición 3
                        System.out.println(message);
                    }
                }
            }
        }
    }

    private void sendJoinMessage() throws IOException {
        String joinMessage = "JOIN:" + USERNAME;
        sendMessage(joinMessage);
    }

    private void handleUserInput(String input) throws IOException {
        if (input.startsWith("/msg")) {
            // Send private message
            sendPrivateMessage(input);
        } else if (input.startsWith("/file")) {
            // Send file
            sendFile(input);
        } else if (input.equals("/leave")) {
            // Leave the chat
            sendLeaveMessage();
        } else {
            // Send regular message
            sendChatMessage(input);
        }
    }

    private void sendPrivateMessage(String input) throws IOException {
        String[] parts = input.split(" ", 3);
        if (parts.length < 3) {
            System.out.println("Follow this format: /msg <username> <message>");
            return;
        }

        String receiver = parts[1];
        String message = parts[2];
        String privateMessage = "PRIVATE:" + USERNAME + ":" + receiver + ":" + message;
        sendMessage(privateMessage);
    }

    private void sendChatMessage(String message) throws IOException {
        String chatMessage = "MSG:" + USERNAME + ":" + message;
        sendMessage(chatMessage);
    }

    private void sendLeaveMessage() throws IOException {
        String leaveMessage = "LEAVE:" + USERNAME;
        sendMessage(leaveMessage);
    }

    private void sendMessage(String message) throws IOException {
        byte[] messageBytes = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(messageBytes, messageBytes.length, serverAddress, SERVER_PORT);
        clientSocket.send(sendPacket);
    }

    private void sendFile(String input) throws IOException {
        String[] parts = input.split(" ", 2);
        if (parts.length < 2) {
            System.out.println("Follow this format: /file <filename>");
            return;
        }

        String filename = parts[1];
        File file = new File(filename);

        if (!file.exists()) {
            System.out.println("File not found.");
            return;
        }

        // Send file metadata
        String fileMetadata = "FILE:" + USERNAME + ":" + USERNAME + ":" + file.getName() + ":" + file.length();
        sendMessage(fileMetadata);

        // Send file content
        byte[] fileData = new byte[BUFFER_SIZE];
        try (FileInputStream fis = new FileInputStream(file)) {
            int bytesRead;
            while ((bytesRead = fis.read(fileData)) != -1) {
                DatagramPacket filePacket = new DatagramPacket(fileData, bytesRead, serverAddress, SERVER_PORT);
                clientSocket.send(filePacket);
            }
        }
        System.out.println("File sent: " + file.getName());
    }
}
