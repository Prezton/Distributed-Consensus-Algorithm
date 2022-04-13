import java.util.*;

public class CommitProcess {
    public String collageName;
    public byte[] collageContent;
    public String[] sources;
    // user id: filename map which composes the collage candidate
    public HashMap<String, ArrayList<String>> userMap;
    public HashMap<String, Boolean> voteResult;
    boolean succeeded = false;

    public CommitProcess(String collageName, byte[] collageContent, String[] sources) {
        this.collageName = collageName;
        this.collageContent = collageContent;
        this.sources = sources;
        userMap = new HashMap<String, ArrayList<String>>();
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
                voteResult.put(userID, false);
            }
        }
    }

    public boolean checkSucceeded() {
        assert(voteResult.size() == userMap.size());
        Set<String> clientSet = voteResult.keySet();
        for (String tmp: clientSet) {
            if (!voteResult.get(tmp)) {
                return false;
            }
        }
        return true;
    }
}