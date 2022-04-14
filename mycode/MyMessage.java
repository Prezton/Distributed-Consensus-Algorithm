import java.io.*;
import java.util.*;

public class MyMessage implements Serializable {

    // TYPE Flag, 1 = Prepare, 2 = Vote, 3 = Decision, 4 = ACK
    public int type = 0;
    
    public String collageName;
    public byte[] collageContent;
    public String[] sources;

    // Used for voteResult or commitResult
    public boolean boolResult;

    public MyMessage(int type, String collageName, byte[] collageContent, String[] sources) {
        this.type = type;
        this.collageName = collageName;
        this.collageContent = collageContent;
        this.sources = sources;
    }


    public byte[] serialize() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        byte[] result = null;
        try {
            oos = new ObjectOutputStream(baos);
            oos.writeObject(this);
            oos.flush();
            result = baos.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                baos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }


}
