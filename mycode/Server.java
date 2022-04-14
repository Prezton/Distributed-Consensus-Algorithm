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
        processMap = new ConcurrentHashMap<String, CommitProcess>();
    }


    public void startCommit(String filename, byte[] img, String[] sources) {

        new Thread(new TwoPhaseCommit(filename, img, sources)).start();

    }

    private static class TwoPhaseCommit implements Runnable {
        private String collageName;
        
        public TwoPhaseCommit(String filename, byte[] img, String[] sources) {
            this.collageName = filename;
            System.out.println("START COMMIT: " + filename);
            CommitProcess currentProcess = new CommitProcess(filename, img, sources);
            processMap.put(this.collageName, currentProcess);
        }
        public void run() {
            sendPrepare(collageName);
            // MAY NEED SOME TIMEOUT MECHANISMS
        }
    }

    public static void sendPrepare(String collageName) {
        CommitProcess currentProcess = processMap.get(collageName);
        ConcurrentHashMap<String, ArrayList<String>> userMap = currentProcess.userMap;
        Set<String> users = userMap.keySet();
        for (String addr: users) {
            String[] sourStrings = convertToSources(userMap, addr);
            MyMessage body = new MyMessage(1, currentProcess.collageName, currentProcess.collageContent, sourStrings);
            ProjectLib.Message prepareMsg = new ProjectLib.Message(addr, body.serialize());
            pl.sendMessage(prepareMsg);
        }
    }

    public static String[] convertToSources(ConcurrentHashMap<String, ArrayList<String>> userMap, String addr) {
        ArrayList<String> tmpArr = userMap.get(addr);
        int s = tmpArr.size();
        String[] sourStrings = new String[tmpArr.size()];
        for (int i = 0; i < s; i ++) {
            sourStrings[i] = tmpArr.get(i);
        }
        return sourStrings;
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
        System.out.println("voteHandler: " + collageName);

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

        if (decision) {
            // SAVE COLLAGE LOCALLY
            saveCollage(currentProcess.collageName, currentProcess.collageContent);
        }

        ConcurrentHashMap<String, ArrayList<String>> userMap = currentProcess.userMap;
        Set<String> destinations = userMap.keySet();

        if (decision) {
            for (String destAddr: destinations) {
                MyMessage commitMsg = new MyMessage(3, currentProcess.collageName, null, convertToSources(userMap, destAddr));
                commitMsg.boolResult = true;
                ProjectLib.Message messageToSend = new ProjectLib.Message(destAddr, serializeTool.serialize(commitMsg));
                pl.sendMessage(messageToSend);
            }
        } else {
            for (String destAddr: destinations) {
                MyMessage commitMsg = new MyMessage(3, currentProcess.collageName, null, convertToSources(userMap, destAddr));
                commitMsg.boolResult = false;
                ProjectLib.Message messageToSend = new ProjectLib.Message(destAddr, serializeTool.serialize(commitMsg));
                pl.sendMessage(messageToSend);
            }
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
        System.out.println("ackHandler: " + collageName);
        CommitProcess currentProcess = null;
        if (processMap.containsKey(collageName)) {
            currentProcess = processMap.get(collageName);
        } else {
            System.out.println("ackHandler() not in processMap: " + collageName);
        }
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
            messageHandle(receivedMessage);
        }
        

    }
}