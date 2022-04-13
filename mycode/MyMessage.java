import java.io.*;

public class MyMessage implements Serializable {
    // TYPE Flag, 1 = Prepare, 2 = Vote, 3 = Decision, 4 = ACK
    int type = 0;
    String collageName;
    byte[] collageContent;
    String[] sources;
    public MyMessage(int type, String collageName, byte[] collageContent, String[] souces) {
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
