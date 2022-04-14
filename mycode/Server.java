import java.io.FileOutputStream;
import java.io.IOException;
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
            if (voteResult == false) {
                boolean decision = false;
                sendDecision(decision, currentProcess);
                processMap.remove(collageName);
                return;
            }
            currentProcess.voteResult.put(srcAddr, voteResult);
            if (currentProcess.checkVoted()) {
                boolean decision = currentProcess.checkVoteResult();
                sendDecision(decision, currentProcess);
            }
        } else {
            System.err.println("handleVote(): NON-EXISTED COMMIT RECORD, STH WRONG");
        }
    }

    public static void sendDecision(boolean decision, CommitProcess currentProcess) {
        MyMessage commitMsg = new MyMessage(3, currentProcess.collageName, null, currentProcess.sources);
        if (decision) {
            // SEND COMMIT
            commitMsg.boolResult = true;
            saveCollage(currentProcess.collageName, currentProcess.collageContent);
        } else {
            // SEND ABORT
            commitMsg.boolResult = false;
        }
        Set<String> destinations = currentProcess.userMap.keySet();
        for (String destAddr: destinations) {
            ProjectLib.Message messageToSend = new ProjectLib.Message(destAddr, serializeTool.serialize(commitMsg));
            pl.sendMessage(messageToSend);
        }
    }

    public static void saveCollage(String collageName, byte[] collageContent) {
        try {
            FileOutputStream fos = new FileOutputStream(collageName);
            fos.write(collageContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void ackHandler(String srcAddr, MyMessage myMessage) {
        String collageName = myMessage.collageName;
        CommitProcess currentProcess = processMap.get(collageName);
        ConcurrentHashMap<String, Boolean> ackMap = currentProcess.ackMap;
        if (!ackMap.containsKey(srcAddr)) {
            System.err.println("CLIENT NOT IN ACK MAP, STH WRONG");
        } else {
            ackMap.put(srcAddr, true);
        }
        if (checkACK(ackMap)) {
            // COMMIT FINISHED
            processMap.remove(collageName);
        }
    }

    private static boolean checkACK(ConcurrentHashMap<String, Boolean> ackMap) {
        Set<String> tmpSet = ackMap.keySet();
        for (String tmpString: tmpSet) {
            if (!ackMap.get(tmpString)) {
                return false;
            }
        }
        return true;
    }

    public static void messageHandle(ProjectLib.Message clientMessage) {
        String srcAddr = clientMessage.addr;
        MyMessage myMessage = (MyMessage) serializeTool.deserialize(clientMessage.body);
        if (myMessage.type == 2) {
            voteHandler(srcAddr, myMessage);
        } else if (myMessage.type == 4) {
            ackHandler(srcAddr, myMessage);
        }
    }

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