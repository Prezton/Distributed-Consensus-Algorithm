import java.util.concurrent.*;
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
            String userName = tmpString.split(":")[0];
            String fileName = tmpString.split(":")[1];
            if (userName.equals(clientID)) {
                // File has already been locked, refuse this commit
                if (lockedFile.containsKey(fileName)) {
                    return false;
                } else {
                    lockedFile.put(fileName,true);
                }
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