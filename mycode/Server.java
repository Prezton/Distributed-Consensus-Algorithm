import java.lang.reflect.Constructor;
import java.util.Set;
import java.util.*;
import java.util.concurrent.*;


public class Server implements ProjectLib.CommitServing {

    // Concurrency handled by threads?
    // After "prepare", does the server thread waits?
    // How does server thread listen for messages?
    // CALLBACK would be called implicitly each time a new message arrives

    private static ProjectLib pl;
    private static ConcurrentHashMap<String, CommitProcess> processMap;


    public Server() {
    }


    public void startCommit(String filename, byte[] img, String[] sources) {
        CommitProcess currentProcess = new CommitProcess(filename, img, sources);
        processMap.put(filename, currentProcess);
        Thread twoPC = new Thread(new TwoPhaseCommit(filename));
        twoPC.start();

    }

    private static class TwoPhaseCommit implements Runnable {
        String collageName;
        public TwoPhaseCommit(String filename) {
            this.collageName = filename;
        }
        public void run() {
            sendPrepare(collageName);
            // MAY NEED SOME TIMEOUT MECHANISMS
        }
    }

    public static void sendPrepare(String collageName) {
        CommitProcess currentProcess = processMap.get(collageName);
        Set<String> users = currentProcess.userMap.keySet();
        for (String addr: users) {
            MyMessage body = new MyMessage(1, currentProcess.collageName, currentProcess.collageContent, currentProcess.sources);
            ProjectLib.Message prepareMsg = new ProjectLib.Message(addr, body.serialize());
            pl.sendMessage(prepareMsg);
        }
    }

    private static class messageHandler implements Runnable {
        private String srcAddr;
        private ProjectLib.Message clientMessage;

        public messageHandler(ProjectLib.Message clientMessage) {
            this.clientMessage = clientMessage;
        }

        public void run() {
            srcAddr = clientMessage.addr;
            MyMessage myMessage = (MyMessage) serializeTool.deserialize(clientMessage.body);
            if (myMessage.type == 2) {
                voteHandler(srcAddr, myMessage);
            } else if (myMessage.type == 4) {
                ackHandler(srcAddr, myMessage);
            }
        }
    }

    public static void voteHandler(String srcAddr, MyMessage myMessage) {
        String collageName = myMessage.collageName;
        if (processMap.containsKey(collageName)) {
            CommitProcess currentProcess = processMap.get(collageName);
            boolean voteResult = myMessage.boolResult;
            currentProcess.voteResult.put(srcAddr, voteResult);
            if (currentProcess.checkVoted()) {
                boolean decision = currentProcess.checkVoteResult();
                sendDecision();
            }
        } else {
            System.err.println("handleVote(): NON-EXISTED COMMIT RECORD, STH WRONG");
        }
    }

    public static void sendDecision() {
        
    }

    public static void ackHandler(String srcAddr, MyMessage myMessage) {

    }

    // public static void messageHandler(ProjectLib.Message clientMessage) {
        // private String srcAddr = clientMessage.addr;
        // MyMessage myMessage = (MyMessage) serializeTool.deserialize(clientMessage.body);
    // }

    public static void main(String[] args) {
        int port =  Integer.parseInt(args[0]);
        Server server = new Server();
        pl = new ProjectLib(port, server);

        while (true) {
            ProjectLib.Message receivedMessage = pl.getMessage();
            Thread handler = new Thread(new messageHandler(receivedMessage));
            handler.start();
        }
        

    }
}