public class UserNode {

    private static ProjectLib pl;

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        int id = Integer.parseInt(args[1);
        pl = new ProjectLib(port, id);
    }
}