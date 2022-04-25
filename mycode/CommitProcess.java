import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CommitProcess implements Serializable {
    public String collageName;
    public byte[] collageContent;
    public String[] sources;
    public long timeStamp;

    // {user id: filenames} map which composes the collage candidate
    public ConcurrentHashMap<String, ArrayList<String>> userMap;

    // {user id: voteResult(boolean)} map which contributes to commit decision
    public ConcurrentHashMap<String, Boolean> voteResult;

    public ConcurrentHashMap<String, Boolean> ackMap;

    public boolean succeeded = false;
    public boolean aborted = false;

    // 0: PREPARE, 1: DECISION, 2: FIN
    public int stage = -1;

    public CommitProcess(String collageName, byte[] collageContent, String[] sources) {

        this.collageName = collageName;
        this.collageContent = collageContent;
        this.sources = sources;
        userMap = new ConcurrentHashMap<String, ArrayList<String>>();
        voteResult = new ConcurrentHashMap<String, Boolean>();
        ackMap = new ConcurrentHashMap<String, Boolean>();

        for (String tmp: sources) {
            String userID = tmp.split(":")[0];
            String filename = tmp.split(":")[1];
            if (userMap.containsKey(userID)) {
                ArrayList<String> tmpArray = userMap.get(userID);
                tmpArray.add(filename);
                userMap.put(userID, tmpArray);
            } else {
                ArrayList<String> tmpArray = new ArrayList<String>();
                tmpArray.add(filename);
                userMap.put(userID, tmpArray);
                ackMap.put(userID, false);
            }
        }
    }

    public CommitProcess(MyMessage myMessage) {
        this.collageName = myMessage.collageName;
        this.collageContent = myMessage.collageContent;
        this.sources = myMessage.sources;
        this.userMap = new ConcurrentHashMap<String, ArrayList<String>>();
    }

    public CommitProcess(CommitProcess currentProcess) {
        this.collageName = currentProcess.collageName;
        this.collageContent = null;
        this.sources = currentProcess.sources;
        userMap = new ConcurrentHashMap<String, ArrayList<String>>();
        voteResult = new ConcurrentHashMap<String, Boolean>();
        ackMap = new ConcurrentHashMap<String, Boolean>();

        for (String tmp: sources) {
            String userID = tmp.split(":")[0];
            String filename = tmp.split(":")[1];
            if (userMap.containsKey(userID)) {
                ArrayList<String> tmpArray = userMap.get(userID);
                tmpArray.add(filename);
                userMap.put(userID, tmpArray);
            } else {
                ArrayList<String> tmpArray = new ArrayList<String>();
                tmpArray.add(filename);
                userMap.put(userID, tmpArray);
                ackMap.put(userID, false);
            }
        }
    }

}