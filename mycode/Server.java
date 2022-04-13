public class Server implements ProjectLib.CommitServing {

    // Concurrency handled by threads?
    // After "prepare", does the server thread waits?
    // How does server thread listen for messages?

    private static PorjectLib pl;
    
    public Server() {
    }


    public void startCommit(String filename, byte[] img, String[] sources) {

    }

    public static void main(String[] args) {
        int port =  Integer.parseInt(args[0]);
        Server server = new Server();
        pl = new ProjectLib(port, server);
    }
}