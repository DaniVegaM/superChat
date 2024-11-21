import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

public class Client {
    private static final int SERVER_PORT = 4446;
    private static final int BUFFER_SIZE = 100;
    private static final int WINDOW_SIZE = 5; 
    private static final String SERVER_ADDRESS = "localhost";
    private static final String MULTICAST_ADDRESS = "230.0.0.0"; //MultiCast Address
    private static String USERNAME = "User"; 

    private static final int SERVER_PORTU = 4447;
    
    private MulticastSocket clientSocket;
    private DatagramSocket fileSocket = new DatagramSocket();
    private InetAddress group;
    private InetAddress serverAddress;

    private static String GREEN = "\033[1;32m";

    public static void main(String[] args) throws IOException, InterruptedException {
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

    public void start() throws IOException, InterruptedException {
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
                // System.out.println("SI EMPIZA CON USERS: ");
                if(message.contains("=")){ //If it has the username
                    // System.out.println("TIENE EL EQUAL y es " + message.substring(7));
                    if((message.substring(7)).startsWith(USERNAME)){
                        // System.out.println("SI EMPIEZA !!");
                        message = message.substring(message.indexOf("=") + 1);
                        String[] userslist = message.split(",");
                        System.out.println("USER's LIST: " + Arrays.toString(userslist));
                        continue;
                    }
                }
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

    private void handleUserInput(String input) throws IOException, InterruptedException {
        if (input.startsWith("/msg")) {
            // Send private message
            sendPrivateMessage(input);
        } else if (input.startsWith("/file")) {
            // Send file
            sendFile(input);
        } else if (input.equals("/leave")) {
            // Leave the chat
            sendLeaveMessage();
        
        } else if (input.equals("/users")) {
            sendAskUsersList();
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
        System.exit(0);
    }

    private void sendAskUsersList() throws IOException{
        String askUsersList = "ASKUSERS:" + USERNAME;
        sendMessage(askUsersList);
    }

    private void sendMessage(String message) throws IOException {
        byte[] messageBytes = message.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(messageBytes, messageBytes.length, serverAddress, SERVER_PORT);
        clientSocket.send(sendPacket);
    }

    private void sendFile(String input) throws IOException, InterruptedException {
        String[] parts = input.split(" ", 2);
        if (parts.length < 2) {
            System.out.println("Follow this format: /file <filename>");
            return;
        }

        String fileName = parts[1];
        String destinationPath = "./" + USERNAME + "/";

        //Create User Folder
        File directory = new File(destinationPath);
        if (!directory.exists()) {
            // Si no existe, la crea
            boolean created = directory.mkdir();
        }

        sendFileWithMetadata(clientSocket, serverAddress, fileName, destinationPath);
    }

    public static void sendFileWithMetadata(DatagramSocket clientSocket, InetAddress serverAddress, String fileName, String destinationPath) throws IOException, InterruptedException {
        File file = new File(destinationPath + fileName);
        if (!file.exists()) {
            System.out.println("CLIENT ERROR: The file doesn't exist");
            return;
        }
        
        //Header
        String header = "FILE:" + file.getName() + ":" + file.length();
        byte[] headerBytes = header.getBytes();
        
        //Sending the header
        DatagramPacket headerPacket = new DatagramPacket(headerBytes, headerBytes.length, serverAddress, SERVER_PORT);
        clientSocket.send(headerPacket);

        System.out.println("Header enviado!");
        
        //Setting File
        FileInputStream fileInputStream = new FileInputStream(file);
        byte[] fileBuffer = new byte[BUFFER_SIZE - 4];
        byte[] sendData = new byte[BUFFER_SIZE];
        
        int bytesRead;
        int sequenceNumber = 0;
        int windowStart = 0; 
        IntWrapper lastAckReceived = new IntWrapper(-1);
        int attempts = 3; //Number of attempts to resend packages
    
        //Queue to remember which packets we sent
        Queue<Integer> sentPackets = new LinkedList<>();
    
        while ((bytesRead = fileInputStream.read(fileBuffer)) != -1 || !sentPackets.isEmpty()) { //SENDING PACKAGES
            System.out.println("LETS SEND");
            // If there is space in the window, keep sending packets
            if(sentPackets.isEmpty()){
                attempts = 3;
                while (windowStart - lastAckReceived.value < WINDOW_SIZE && bytesRead != -1) {
                    System.out.println("sending");
                    addSequenceNumberToPacket(sendData, sequenceNumber);
                    System.arraycopy(fileBuffer, 0, sendData, 4, bytesRead);
                    
                    // Send packet
                    DatagramPacket filePacket = new DatagramPacket(sendData, bytesRead + 4, serverAddress, SERVER_PORTU);
                    clientSocket.send(filePacket);
                    sentPackets.add(sequenceNumber);
        
                    sequenceNumber++;
                    bytesRead = fileInputStream.read(fileBuffer);

                    //Wait until send the next package
                    Thread.sleep(1); //PRETTY IMPORTANT!!!!!!!
                }
            }
    
            // Handling ACKs
            boolean ackReceived = false;
            while (!ackReceived && !sentPackets.isEmpty()) {
                try {
                    byte[] ackBuffer = new byte[BUFFER_SIZE];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                    clientSocket.setSoTimeout(1000); //Wait for 1 second
                    clientSocket.receive(ackPacket);
    
                    // Read ACK
                    System.out.println("CLIENT: ACK received for all packages in a window");
                    String ack = new String(ackPacket.getData(), 0, ackPacket.getLength());
                    String[] ackParts = ack.split(";");
                    if (ackParts[0].equals("ACK")){
                        int ackSequenceNumber = Integer.parseInt(ackParts[1]);
        
                        if (ackSequenceNumber > lastAckReceived.value) { 
                            lastAckReceived.value = ackSequenceNumber;
                            // Remove acknowledged packets
                            sentPackets.removeIf(seqNum -> seqNum <= lastAckReceived.value);
                            ackReceived = true;
                        }
                    } else{
                        // System.out.println("CLIENT: I received an strange Datagram :/");
                    }
                } catch (IOException e) {
                    // Resend if timeout occurs
                    if (attempts > 0) {
                        System.out.println("CLIENT: Timeout, resending unacknowledged packets");
                        for (int packetToResend : sentPackets) {
                            addSequenceNumberToPacket(sendData, packetToResend);
                            DatagramPacket resendPacket = new DatagramPacket(sendData, BUFFER_SIZE, serverAddress, SERVER_PORTU);
                            clientSocket.send(resendPacket);
                        }
                        attempts--;
                    } else {
                        System.out.println("CLIENT: ERROR, maximum number of resend attempts reached");
                        return;
                    }
                }
            }
        }
        
        fileInputStream.close();
        System.out.println("CLIENT: File sent successfully!");
    }   

    private static void addSequenceNumberToPacket(byte[] packet, int sequenceNumber) {
        packet[0] = (byte) (sequenceNumber >> 24);
        packet[1] = (byte) (sequenceNumber >> 16);
        packet[2] = (byte) (sequenceNumber >> 8);
        packet[3] = (byte) sequenceNumber;
    }

}

class IntWrapper {
    public int value;

    public IntWrapper(int initialValue) {
        this.value = initialValue;
    }
}
