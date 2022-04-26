import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.*;
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
    private static LogOperations serverLog;

    public Server() {
        processMap = new ConcurrentHashMap<String, CommitProcess>();
    }


    public void startCommit(String filename, byte[] img, String[] sources) {

        new Thread(new TwoPhaseCommit(filename, img, sources)).start();

    }

    private static class TwoPhaseCommit implements Runnable {
        private String collageName;
        
        public TwoPhaseCommit(String filename, byte[] img, String[] sources) {
            CommitProcess currentProcess = new CommitProcess(filename, img, sources);
            this.collageName = filename;
            System.out.println("START COMMIT: " + filename);
            processMap.put(this.collageName, currentProcess);
            
        }
        public void run() {
            sendPrepare(collageName);
            // MAY NEED SOME TIMEOUT MECHANISMS
        }
    }



    public static void sendPrepare(String collageName) {

        System.out.println("sendPrepare(): " + collageName);
        CommitProcess currentProcess = processMap.get(collageName);
        ConcurrentHashMap<String, ArrayList<String>> userMap = currentProcess.userMap;
        Set<String> users = userMap.keySet();
        for (String addr: users) {
            String[] sourStrings = convertToSources(userMap, addr);
            MyMessage body = new MyMessage(1, currentProcess.collageName, currentProcess.collageContent, sourStrings);
            ProjectLib.Message prepareMsg = new ProjectLib.Message(addr, body.serialize());
            pl.sendMessage(prepareMsg);
        }
        currentProcess.timeStamp = System.currentTimeMillis();
        try {
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        voteTimeOutAbort(collageName);
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
        System.out.println("voteHandler: vote from " + srcAddr + " about " + collageName + " is "+ myMessage.boolResult);

        if (processMap.containsKey(collageName)) {
            CommitProcess currentProcess = processMap.get(collageName);
            boolean voteResult = myMessage.boolResult;
            if (voteResult == false) {
                // Abort
                boolean decision = false;
                if (!currentProcess.aborted) {
                    sendDecision(decision, currentProcess);
                }
                currentProcess.aborted = true;
                // SHOULD NOT REMOVE NOW, NOT DONE YET!!!
                // processMap.remove(collageName);
            }
            if (currentProcess.voteResult.containsKey(srcAddr)) {
                System.out.println("voteHandler(): " + srcAddr + " already voted, sth wrong!");
                assert(voteResult == currentProcess.voteResult.get(srcAddr));
            }
            currentProcess.voteResult.put(srcAddr, voteResult);
            if (checkVoteNum(currentProcess)) {
                // Basically, a Commit decision
                boolean decision = checkVoteResult(currentProcess);
                if (decision) {
                    sendDecision(decision, currentProcess);
                } else {
                    System.out.println("ABORTED ALREADY");
                }
            }
        } else {
            System.out.println("handleVote(): already removed collage " + collageName + "'s commit");
        }
    }

    private static boolean checkVoteResult(CommitProcess currentProcess) {
        ConcurrentHashMap<String, Boolean> voteResult = currentProcess.voteResult;
        Set<String> clientSet = voteResult.keySet();
        for (String tmp: clientSet) {
            if (!voteResult.get(tmp)) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkVoteNum(CommitProcess currentProcess) {
        ConcurrentHashMap<String, Boolean> voteResult = currentProcess.voteResult;
        ConcurrentHashMap<String, ArrayList<String>> userMap = currentProcess.userMap;
        if (voteResult.size() == userMap.size()) {
            return true;
        }
        return false;
    }


    public static void voteTimeOutAbort(String collageName) {
        CommitProcess currentProcess = null;
        if (processMap.containsKey(collageName)) {
            currentProcess = processMap.get(collageName);
        } else {
            System.out.println("voteTimeOutAbort(): " + collageName + " not in processMap, could have committed or aborted");
        }
        if (currentProcess != null) {
            ConcurrentHashMap<String, Boolean> voteMap = currentProcess.voteResult;
            ConcurrentHashMap<String, ArrayList<String>> userMap = currentProcess.userMap; 
            if (voteMap.size() != userMap.size()) {
                currentProcess.aborted = true;
                System.out.println("voteTimeOutAbort(): aborted! " + collageName);
                sendDecision(false, currentProcess);
            }
        }
    }

    public static void sendDecision(boolean decision, CommitProcess currentProcess) {
        System.out.println("SEND DECISION ABOUT: " + currentProcess.collageName + " is " + decision);

        currentProcess.succeeded = decision;

        if (decision) {
            // SAVE COLLAGE LOCALLY
            saveCollage(currentProcess.collageName, currentProcess.collageContent);
        }
        writeDecisionLog(decision, currentProcess);
        ConcurrentHashMap<String, ArrayList<String>> userMap = currentProcess.userMap;
        Set<String> destinations = userMap.keySet();

        if (decision) {
            // Succeeded
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

        currentProcess.timeStamp = System.currentTimeMillis();
        Timer ackChecker = new Timer();
        ackChecker.scheduleAtFixedRate(new CheckAckTimeOut(decision, currentProcess), 6000L, 6000L);
    }

    private static void writeDecisionLog(boolean decision, CommitProcess currentProcess) {
        currentProcess.succeeded = decision;
        int decisionInt;
        if (decision) {
            decisionInt = 1;
        } else {
            decisionInt = -1;
        }
        String collageName = currentProcess.collageName;
        String decisionToWrite = collageName + ":" + decisionInt;
        currentProcess.collageContent = null;
        serverLog.writeObjToLog(0, currentProcess);
        serverLog.writeLogs(0, decisionToWrite);
        System.out.println("WRITE COMMIT LOG ABOUT: " + collageName + " is " + decision);

    }

    public static void sendDecisionOnReboot(boolean decision, CommitProcess currentProcess) {
        System.out.println("SEND DECISION ABOUT: " + currentProcess.collageName + " is " + decision);

        currentProcess.succeeded = decision;

        ConcurrentHashMap<String, ArrayList<String>> userMap = currentProcess.userMap;
        Set<String> destinations = userMap.keySet();

        if (decision) {
            // Succeeded
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

        currentProcess.timeStamp = System.currentTimeMillis();
        Timer ackChecker = new Timer();
        ackChecker.scheduleAtFixedRate(new CheckAckTimeOut(decision, currentProcess), 3000L, 6000L);
    }

    public static void resendDecision(boolean decision, CommitProcess currentProcess) {
        ConcurrentHashMap<String, ArrayList<String>> userMap = currentProcess.userMap;
        Set<String> destinations = userMap.keySet();
        ConcurrentHashMap<String, Boolean> ackMap = currentProcess.ackMap;
        boolean sent = false;
        boolean notAllReceived = false;
        for (String destAddr: destinations) {
            if (!ackMap.get(destAddr)) {
                // if (currentProcess.timeStamp > 0 && System.currentTimeMillis() - currentProcess.timeStamp >= 3000) {
                    System.out.println("resendDecision() about: " + currentProcess.collageName + " to " + destAddr);
                    MyMessage commitMsg = new MyMessage(3, currentProcess.collageName, null, convertToSources(userMap, destAddr));
                    if (decision) {
                        commitMsg.boolResult = true;
                    } else {
                        commitMsg.boolResult = false;
                    }
                    pl.sendMessage(new ProjectLib.Message(destAddr, serializeTool.serialize(commitMsg)));
                    sent = true;
                    notAllReceived = true;
                // }
            }
        }
        if (sent) {
            currentProcess.timeStamp = System.currentTimeMillis();
        }
        if (!notAllReceived) {
            System.out.println("resendDecision(): ALL ACK RECEIVED! " + currentProcess.collageName);
            // Q: how to stop resend after all acks are received?
        }
    }

    private static class CheckAckTimeOut extends TimerTask {
        boolean decision;
        CommitProcess currentProcess;
        public CheckAckTimeOut(boolean decision, CommitProcess currentProcess) {
            this.decision = decision;
            this.currentProcess = currentProcess;
        }
        public void run() {
            resendDecision(decision, currentProcess);
        }
    }

    public static void saveCollage(String collageName, byte[] collageContent) {
        System.out.println("SAVE COLLAGE " + collageName);
        try {
            FileOutputStream fos = new FileOutputStream(collageName);
            fos.write(collageContent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void ackHandler(String srcAddr, MyMessage myMessage) {
        String collageName = myMessage.collageName;
        System.out.println("ackHandler: ack about " + collageName + " from " + srcAddr);
        CommitProcess currentProcess = null;
        if (processMap.containsKey(collageName)) {
            currentProcess = processMap.get(collageName);
        } else {
            System.out.println("ackHandler() not in processMap: " + collageName + "STH WRONG OR ABORTED ALREADY");
        }
        if (currentProcess != null) {
            ConcurrentHashMap<String, Boolean> ackMap = currentProcess.ackMap;
            if (!ackMap.containsKey(srcAddr)) {
                System.out.println("CLIENT NOT IN ACK MAP, STH WRONG! " + srcAddr + " " + currentProcess.collageName);
            } else {
                ackMap.put(srcAddr, true);
            }
            if (checkAckNum(ackMap)) {
                // COMMIT FINISHED
                System.out.println("commit about " + collageName + " finished!, result is: "+ currentProcess.succeeded);
                writeFinLog(collageName);
                processMap.remove(collageName);
            }
        } else {
            System.out.println("ackHandler(): null currentProcess about collage " + collageName);
        }

    }

    public static void writeFinLog(String collageName) {
        StringBuilder sb = new StringBuilder();
        sb.append(collageName).append(":").append(2);
        // serverLog.writeObjToLog(0, null);
        serverLog.writeLogs(0, sb.toString());
    }

    public static boolean reboot() {
        String rebootType = null;
        if ((new File("serverLog")).exists()) {
            String logContent = serverLog.readLogs(0);
            String[] tmp = logContent.split(":");
            int opcode = Integer.parseInt(tmp[1].strip());
            if (opcode == 1 || opcode == -1) {

                String collageName = tmp[0];
                CommitProcess rebootProcess = (CommitProcess) serverLog.readObjFromLog(0);
                boolean decision;
                decision = rebootProcess.succeeded;
                processMap.put(collageName, rebootProcess);
                rebootProcess.succeeded = decision;
                rebootProcess.aborted = !(decision);
                System.out.println("server reboot(): DECISION " + decision);

                sendDecisionOnReboot(decision, rebootProcess);
                return true;
            }
            System.out.println("log exists but not fit");
            return false;
        }
        return true;
    }

    private static boolean abortOnReboot(String[] rebootStrArr) {
        System.out.println("server reboot(): PREPARE");
        String collageName = rebootStrArr[1];
        String[] sources = (String[]) serverLog.readObjFromLog(0);
        CommitProcess rebootProcess = new CommitProcess(collageName, null, sources);
        processMap.put(collageName, rebootProcess);
        rebootProcess.succeeded = false;
        rebootProcess.aborted = true;
        sendDecisionOnReboot(false, rebootProcess);
        return true;
    }

    private static boolean checkAckNum(ConcurrentHashMap<String, Boolean> ackMap) {
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
        serverLog = new LogOperations(pl);
        boolean rebootResult = reboot();
        while (true) {
            ProjectLib.Message receivedMessage = pl.getMessage();
            messageHandle(receivedMessage);
        }
        

    }
}