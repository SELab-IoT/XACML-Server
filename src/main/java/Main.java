import httpServer.PDPServer;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            PDPServer.getInstance().startServer();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
