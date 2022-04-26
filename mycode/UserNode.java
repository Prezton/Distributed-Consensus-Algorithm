import java.util.concurrent.*;
import java.io.File;
import java.io.FilenameFilter;
import java.text.CollationElementIterator;
import java.util.*;

public class UserNode {

    private static ProjectLib pl;
    private static final String serverAddr = "Server";
    private static ConcurrentHashMap<String, Boolean> lockedFile = new ConcurrentHashMap<String, Boolean>();
    private static String clientID;
    private static ConcurrentHashMap<String, CommitProcess> processMap = new ConcurrentHashMap<String, CommitProcess>();
    private static LogOperations userLog;

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
        System.err.println(clientID + "'s' prepare handler: " + myMessage.collageName);
        boolean voteResult;
        if (!lockFile(myMessage)) {
            // Locked already or does not exist
            voteResult = false;
        } else {
            voteResult = pl.askUser(myMessage.collageContent, myMessage.sources);
        }
        writeVoteLog(voteResult, myMessage);
        MyMessage voteMessage = new MyMessage(2, myMessage.collageName, null, myMessage.sources);
        assert (srcName == "Server");

        voteMessage.boolResult = voteResult;
        ProjectLib.Message messageToSend = new ProjectLib.Message(serverAddr, serializeTool.serialize(voteMessage));
        pl.sendMessage(messageToSend);
    }

    private static void writeVoteLog(boolean voteReulst, MyMessage myMessage) {
        StringBuilder sb = new StringBuilder();
        int intResult = 0;
        if (voteReulst) {
            intResult = 1;
        } else {
            intResult = -1;
        }
        String[] files = myMessage.sources;
        sb.append(myMessage.collageName).append(":").append(intResult);
        userLog.writeLogs(1, sb.toString());
        userLog.writeObjToLog(1, files);
        System.err.println(clientID + " write User vote log");
    }

    public static void decisionHandler(String srcName, MyMessage myMessage) {
        System.err.println(clientID + "'s decisionHandler: decision from " + srcName + " about " + myMessage.collageName + " is " + myMessage.boolResult);
        boolean commitDecision = myMessage.boolResult;
        if (commitDecision) {
            // If decision is commit, delete local file
            String[] sources = myMessage.sources;
            for (String tmpString: sources) {
                String fileName = tmpString;
                File subFile = new File(fileName);
                System.err.println(clientID + "'s DELETE: " + fileName);
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
        System.err.println(clientID + "'s sendACK()");
        String collageName = receivedMessage.collageName;
        MyMessage myMessage = new MyMessage(4, collageName, null, receivedMessage.sources);
        ProjectLib.Message messageToSend = new ProjectLib.Message(serverAddr, serializeTool.serialize(myMessage));
        pl.sendMessage(messageToSend);
    }

    public static void reboot() {
        String rebootType = null;
        if ((new File("userLog")).exists()) {
            String logContent = userLog.readLogs(1);
            String[] tmp = logContent.split(":");
            int opcode = Integer.parseInt(tmp[1].strip());
            if (opcode == 1) {
                System.err.println(clientID + " USER NODE REBOOT(): VOTE");
                String collageName = tmp[0];
                String[] sources = (String[]) userLog.readObjFromLog(1);
                MyMessage myMessage = new MyMessage(1, collageName, null, sources);
                lockFile(myMessage);
            }
        }
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        clientID = args[1];
        pl = new ProjectLib(port, clientID);
        userLog = new LogOperations(pl);
        reboot();
        while (true) {
            ProjectLib.Message receivedMessage = pl.getMessage();
            messageHandler(receivedMessage);
        }
    }
}