import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CommitProcess {
    public String collageName;
    public byte[] collageContent;
    public String[] sources;

    // {user id: filenames} map which composes the collage candidate
    public ConcurrentHashMap<String, ArrayList<String>> userMap;

    // {user id: voteResult(boolean)} map which contributes to commit decision
    public ConcurrentHashMap<String, Boolean> voteResult;

    public ConcurrentHashMap<String, Boolean> ackMap;

    boolean succeeded = false;

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

    public boolean checkVoteResult() {
        Set<String> clientSet = voteResult.keySet();
        for (String tmp: clientSet) {
            if (!voteResult.get(tmp)) {
                return false;
            }
        }
        return true;
    }

    public boolean checkVoted() {
        if (voteResult.size() == userMap.size()) {
            return true;
        }
        return false;
    }
}