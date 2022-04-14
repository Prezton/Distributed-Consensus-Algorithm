import java.util.concurrent.*;
import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

public class UserNode {

    private static ProjectLib pl;
    private static final String serverAddr = "Server";
    private static ConcurrentHashMap<String, Boolean> lockedFile = new ConcurrentHashMap<String, Boolean>();
    private static String clientID;

    public static void messageHandler(ProjectLib.Message receivedMessage) {
        String srcName = receivedMessage.addr;
        byte[] receivedBytes = receivedMessage.body;
        MyMessage myMessage = (MyMessage) serializeTool.deserialize(receivedBytes);
        if (myMessage.type == 1) {
            prepareHandler(srcName, myMessage);
        } else if (myMessage.type == 3) {
            decisionHandler(srcName, myMessage);
        }

    }

    public static boolean lockFile(MyMessage myMessage) {
        for (String tmpString: myMessage.sources) {
            String fileName = tmpString;
                // File has already been locked, refuse this commit
            if (lockedFile.containsKey(fileName) || !((new File(fileName)).exists())) {
                return false;
            } else {
                lockedFile.put(fileName,true);
            }
            
        }
        return true;
    }

    public static void prepareHandler(String srcName, MyMessage myMessage) {
        boolean voteResult = pl.askUser(myMessage.collageContent, myMessage.sources);
        MyMessage voteMessage = new MyMessage(2, myMessage.collageName, null, myMessage.sources);
        assert (srcName == "Server");
        
        if (!lockFile(myMessage)) {
            voteResult = false;
        }

        voteMessage.boolResult = voteResult;
        ProjectLib.Message messageToSend = new ProjectLib.Message(serverAddr, serializeTool.serialize(voteMessage));
        pl.sendMessage(messageToSend);
    }

    public static void decisionHandler(String srcName, MyMessage myMessage) {
        System.out.println("decisionHandler: decision about " + srcName + " is " + myMessage.boolResult);
        boolean commitDecision = myMessage.boolResult;
        if (commitDecision) {
            // If decision is commit, delete local file
            String[] sources = myMessage.sources;
            for (String tmpString: sources) {
                String fileName = tmpString;
                File subFile = new File(fileName);
                System.out.println("DELETE: " + fileName);
                subFile.delete();
                
            }
        } else {
            // If decision is abort, unlock the files in invalid commit
            String[] sources = myMessage.sources;
            for (String tmpString: sources) {
                String fileName = tmpString;
                if (lockedFile.containsKey(fileName)) {
                    // POTENTIAL PROBLEM HERE!!! MAY REMOVE FILE LOCKED BY OTHER COMMITS!!!
                    // Could add a map to bind locked files with the corresponding commit!!!
                    lockedFile.remove(fileName);
                }
                
            }
        }
        sendACK(myMessage);
    }

    public static void sendACK(MyMessage receivedMessage) {
        String collageName = receivedMessage.collageName;
        MyMessage myMessage = new MyMessage(4, collageName, null, receivedMessage.sources);
        ProjectLib.Message messageToSend = new ProjectLib.Message(serverAddr, serializeTool.serialize(myMessage));
        pl.sendMessage(messageToSend);
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        clientID = args[1];
        pl = new ProjectLib(port, clientID);

        while (true) {
            ProjectLib.Message receivedMessage = pl.getMessage();
            messageHandler(receivedMessage);
        }
    }
}