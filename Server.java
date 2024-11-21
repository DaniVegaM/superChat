import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {
    private static final int SERVER_PORT = 4446;
    private static final int SERVER_PORTU = 4447;
    private static final int BUFFER_SIZE = 1024;
    private static final String MULTICAST_ADDRESS = "230.0.0.0"; //MultiCast Address
    private static List<String> userList = new ArrayList<>();
    private static Map<String, InetSocketAddress> userAddresses = new HashMap<>();
    private static Map<Integer, byte[]> receivedPackets = new HashMap<>(); //Store received packages
    private static  DatagramSocket fileSocket;

    private static Boolean fileReceived = false;
    private static String currentPath;
    private static String fileName;
    private static int fileLength;


    private static DatagramSocket unicastSocket; // Persistente para envíos unicast

    static {
        try {
            unicastSocket = new DatagramSocket(); // Inicializar un solo DatagramSocket
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String GREEN = "\033[1;32m";
    private static String RESET = "\033[0m";


    public static void main(String[] args) throws IOException{
        fileSocket = new DatagramSocket(SERVER_PORTU); //Socket
        MulticastSocket serverSocket = new MulticastSocket(SERVER_PORT);
        InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
        // serverSocket.joinGroup(group); //It seems to be deprecated

        System.out.println("SERVER ON! Waiting...");

        //Thread that will hear for instructions
        new Thread(() -> {
            try {
                while (true) {
                    byte[] receiveData = new byte[BUFFER_SIZE];
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    serverSocket.receive(receivePacket);

                    //New thread to handle the Client's message
                    new Thread(new ClientHandler(serverSocket, receivePacket, group)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    //Private Internal Class to handle the messagges for each client
    private static class ClientHandler implements Runnable {
        private MulticastSocket serverSocket;
        private DatagramPacket receivePacket;
        private InetAddress group;

        public ClientHandler(MulticastSocket serverSocket, DatagramPacket receivePacket, InetAddress group) {
            this.serverSocket = serverSocket;
            this.receivePacket = receivePacket;
            this.group = group;
        }

        @Override
        public void run() {
            try {
                String message = new String(receivePacket.getData(), 0, receivePacket.getLength());
                // System.out.println("Messagge received: " + message);
                InetAddress clientAddress = receivePacket.getAddress();
                int clientPort = receivePacket.getPort();

                handleClientMessage(serverSocket, message, clientAddress, clientPort, group);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void handleClientMessage(MulticastSocket serverSocket, String message, InetAddress clientAddress, int clientPort, InetAddress group) throws IOException {
        /*
         * Messagge's Structure: ACTION:Content
         */
        
        String[] parts = message.split(":", 5); //We want to separate the action and messagge's content
        String action = parts[0];
        InetSocketAddress actualClientAddress = new InetSocketAddress(clientAddress, clientPort);

        switch (action) {
            case "JOIN": //JOIN:Username
                String username = parts[1];
                userList.add(username); //Add user to the list
                
                System.out.println("Dir del cliente desde JOIN" + actualClientAddress);
                userAddresses.put(username, actualClientAddress);
                System.out.println(username + " has joined to the chat.");
                sendUserList(serverSocket, group); // Sending the updated user's list
            break;

            case "MSG": //MSG:UserName:Message
                String userName = parts[1];
                String chatMessage = parts[2];
                System.out.println("Mensaje de chat: " + chatMessage);
                
                broadcastMessage(serverSocket, "\r" + GREEN + userName +": " + RESET + chatMessage, group, actualClientAddress); //Sending message to everyone in the chat
            break;

            case "ASKUSERS": //ASKUSERS:UserName
                userName = parts[1];
                
                askUserList(serverSocket, group, userName);
            break;

            case "PRIVATE": // PRIVATE:Sender:Receiver:Message
                String sender = parts[1];
                String receiver = parts[2];
                String privateMessage = parts[3];
                sendPrivateMessage(serverSocket, sender, receiver, privateMessage, group); // Send private message
            break;

            case "FILE": // FILE:Sender:Receiver:FileName:FileSize
                fileName = parts[1];
                fileLength = Integer.parseInt(parts[2]);

                receiveFile(fileSocket, clientAddress, clientPort, fileName, fileLength);
            break;

            case "LEAVE": //LEAVE:UserName
                String userLeaving = parts[1];
                userList.remove(userLeaving);
                userAddresses.remove(userLeaving);
                System.out.println(userLeaving + " has leaved from the chat.");
                broadcastMessage(serverSocket, "\r" + GREEN + userLeaving + " has leaved from the chat.", group, actualClientAddress);
                // sendUserList(serverSocket, group); // Update the user's list
            break;

            default:
                // if(!fileReceived){
                //     receiveFile(serverSocket, clientAddress, clientPort, fileName, fileLength);
                //     break;
                // }
                System.out.println("WHAT!? Are you trying to hack this chat room?");
            break;
        }
    }

    private static void sendUserList(MulticastSocket serverSocket, InetAddress group) throws IOException {
        String message = "USERS: " + String.join(",", userList);
        byte[] buffer = message.getBytes();

        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, SERVER_PORT);
        
        serverSocket.send(packet);
    }

    private static void askUserList(MulticastSocket serverSocket, InetAddress group, String username) throws IOException {
        String message = "USERS: " + username + "=" + String.join(",", userList);
        byte[] buffer = message.getBytes();

        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, SERVER_PORT);
        
        serverSocket.send(packet);
    }

    private static void broadcastMessage(MulticastSocket serverSocket, String message, InetAddress group, InetSocketAddress actualClientAddress) throws IOException {
        byte[] buffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, SERVER_PORT);
        
        serverSocket.send(packet);
        System.out.println("Broadcast message sent to group: " + message);
    }

    private static void sendPrivateMessage(MulticastSocket serverSocket, String sender, String receiver, String message, InetAddress group) throws IOException {

        String privateMessage = "PRIVATE:" + sender + ":" + receiver + ":" + message;
        byte[] buffer = privateMessage.getBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, SERVER_PORT);
        serverSocket.send(packet);
        System.out.println("Private message sent to " + receiver);
    }

    public static void receiveFile(DatagramSocket serverSocket, InetAddress clientAddress, int clientPort, String fileName, int fileLength) throws IOException {
        byte[] receiveData = new byte[BUFFER_SIZE];
        // FileOutputStream fileOutputStream = new FileOutputStream(((currentPath != "" || currentPath != " ")? currentPath + "/" : currentPath) + fileName);
        FileOutputStream fileOutputStream = new FileOutputStream("./Server/" + fileName);

        boolean receiving = true;
        long totalBytesReceived = 0;
        int expectedSequenceNumber = 0; //Expected Sequence Number
        int numOfPackagesReceived = 0;

        while (receiving) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);

            //Reading the sequence number
            int sequenceNumber = byteArrayToInt(receivePacket.getData(), 0);
            System.out.println("SERVER: Package received: " + sequenceNumber);

            //Receiving package
            if (!receivedPackets.containsKey(sequenceNumber)) {
                receivedPackets.put(sequenceNumber, receivePacket.getData());
                System.out.println("SERVER: Receiving another package" + sequenceNumber);
            }
            
            if(sequenceNumber == expectedSequenceNumber){ //It's the expected package
                System.out.println("SERVER: It's the right package, saving package");
                fileOutputStream.write(receivePacket.getData(), 4, receivePacket.getLength() - 4);
                totalBytesReceived += receivePacket.getLength() - 4;

                //Send ACK to client
                if(numOfPackagesReceived == 5){
                    System.out.println("SERVER: 5 packages (A window) received, sending ACK!");
                    String ack = "ACK;" + sequenceNumber;
                    byte[] ackData = ack.getBytes();
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
                    serverSocket.send(ackPacket);

                    //Reset variables
                    numOfPackagesReceived = 0;

                }

                expectedSequenceNumber++;
                numOfPackagesReceived++;

                if (totalBytesReceived >= fileLength) { //Server received the entire file
                    receiving = false;
                }
            } else if(sequenceNumber < expectedSequenceNumber){ //If we receive a duplicated package
                System.out.println("SERVER: Package received duplicated, Im sending the expected sequence number as ACK");
                //Send ACK to client
                if(numOfPackagesReceived < 5){
                    System.out.println("SERVER: Sending ACK of the package expected to continue recivieng packages until fill the window");
                    String ack = "ACKR;" + expectedSequenceNumber;
                    byte[] ackData = ack.getBytes();
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
                    serverSocket.send(ackPacket);
                }
            } else{
                // System.out.println("SERVER: Package out of order :( \n Im sending the expected sequence number as ACK");
                if(numOfPackagesReceived < 5){
                    // System.out.println("SERVER: Sending ACK of the package expected to continue recivieng packages until fill the window");
                    String ack = "ACKR;" + expectedSequenceNumber;
                    byte[] ackData = ack.getBytes();
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
                    serverSocket.send(ackPacket);
                }
            }
        }

        fileOutputStream.close();
        System.out.println("SERVER: File recieved and saved!");
    }

    //Convert first 4 bytes of package received into an int sequence number
    private static int byteArrayToInt(byte[] arr, int offset) {
        return ((arr[offset] & 0xFF) << 24) | ((arr[offset + 1] & 0xFF) << 16) |
               ((arr[offset + 2] & 0xFF) << 8) | (arr[offset + 3] & 0xFF);
    }
}