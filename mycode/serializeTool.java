import java.io.*;

public class serializeTool {
    public static byte[] serialize(Object obj) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;
        byte[] result = null;
        try {
            oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
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

    public static Object deserialize(byte[] bytes) {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = null;
        Object result = null;
        try {
            ois = new ObjectInputStream(bais);
            result = ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                bais.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
    
}
