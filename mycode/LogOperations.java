import java.io.*;

public class LogOperations {
    private static ProjectLib pl;
    private static final String serverLog = "serverLog";
    private static final String userLog = "userLog";
    private static final String serverObjLog = "serverObjLog";
    private static final String userObjLog = "userObjLog";

    public LogOperations(ProjectLib pl) {
        this.pl = pl;
    }

    public void writeLogs(int type, String contents) {
        String logName = null;
        if (type == 1) {
            logName = userLog;
        } else {
            logName = serverLog;
        }

        try {
            FileWriter writer = new FileWriter(logName);
            writer.write(contents);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        pl.fsync();

    }

    public String readLogs(int type) {
        String logName = null;
        if (type == 1) {
            logName = userLog;
        } else {
            logName = serverLog;
        }
        StringBuilder sb = null;
        try {
            FileReader tmp = new FileReader(logName);
            BufferedReader reader = new BufferedReader(tmp);
            sb = new StringBuilder();
            String line = reader.readLine();
            sb.append(line).append("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (sb != null) {
            return sb.toString();
        }
        return null;
    }

    public void writeObjToLog(int type, Object obj) {
        String logName = null;
        if (type == 1) {
            logName = userObjLog;
        } else {
            logName = serverObjLog;
        }
        try {
            FileOutputStream fos = new FileOutputStream(logName);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(obj);
            oos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        pl.fsync();
    }

    public Object readObjFromLog(int type) {
        String logName = null;
        if (type == 1) {
            logName = userObjLog;
        } else {
            logName = serverObjLog;
        }
        try {
            FileInputStream fis = new FileInputStream(logName);
            ObjectInputStream ois = new ObjectInputStream(fis);
            Object obj = ois.readObject();
            ois.close();
            return obj;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}