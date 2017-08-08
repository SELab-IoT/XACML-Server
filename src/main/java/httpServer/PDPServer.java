package httpServer;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class PDPServer {

    private static PDPServer pdpServer;
    private static PDPInterface pdpInterface = PDPInterface.getInstance();

    public static PDPServer getInstance() {
        return pdpServer = Singleton.instance;
    }

    // Thread-safe singleton
    private PDPServer() {
    }

    private static class Singleton {
        private static final PDPServer instance = new PDPServer();
    }

    // Start HTTP Server
    public void startServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/evaluate", new EvaluateHandler());
        server.createContext("/reload", new ReloadHandler());
        server.start();
        System.out.println("Start PDPServer");
    }

    // Handler for http://~~~~/evaluate
    private class EvaluateHandler implements HttpHandler {

        Gson gson = new GsonBuilder().create();

        @Override
        public void handle(HttpExchange httpExchange) {

            InputStream inputStream = httpExchange.getRequestBody();
            String inputString;
            try {
                inputString = read(inputStream);
                JsonObject inputJson = gson.fromJson(inputString, JsonObject.class);

                // Debug Observer
                System.out.println("Request: " + inputString);

                @Deprecated
                /*LinkedList<String> attributeCategoryList = new LinkedList<>();
                if (inputJson.get("attributeList") != null) {
                    JsonArray attributeArray = inputJson.get("attributeList").getAsJsonArray();
                    StreamSupport.stream(attributeArray.spliterator(), false)
                                 .map(JsonElement::getAsString)
                                 .forEach(attributeCategoryList::add);
                }*/


                        // 1. JSON 에서 XACML request를 가져옴
                        String requestBody = inputJson.get("body").getAsString();

                // 2. JSON 에서 PEP ID 가져옴
                String pepId = null;
                if (inputJson.get("pepId") != null)
                    pepId = inputJson.get("pepId").getAsString();

                // 3. PEP ID를 통해 config.xml 에서 pdp 설정을 선택하고
                // 해당 설정의 PDP를 생성하고 XACML 리퀘스트를 보냄
                String response = evaluateRequest(requestBody, pepId);

                // 3.1. Error Handling
                if (response == null)
                    httpResponse(400, httpExchange, "Invalid Request");

                // 4. Return XACML Response
                httpResponse(200, httpExchange, response);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private String evaluateRequest(String request, String pepId) {
            return !(request.isEmpty() || request == null) ? pdpInterface.evaluate(request, pepId) : null;
        }

    }

    public class ReloadHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            Gson gson = new GsonBuilder().create();
            InputStream inputStream = httpExchange.getRequestBody();
            String inputString = read(inputStream);
            JsonObject inputJson = gson.fromJson(inputString, JsonObject.class);
            String pdpName = null;
            if (inputJson.get("pdpName") != null)
                pdpName = inputJson.get("pdpName").getAsString();
            else
                httpResponse(400, httpExchange, "Invalid Request");

            boolean isSuccess = PDPInterface.getInstance().reloadPDP(pdpName);
            if (isSuccess)
                httpResponse(200, httpExchange, "reload Success");
            else
                httpResponse(400, httpExchange, "Not Found PDP Name");

        }

    }

    private void httpResponse(int code, HttpExchange httpExchange, String response) {
        try {
            httpExchange.sendResponseHeaders(code, response.getBytes().length);
            OutputStream os = httpExchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String read(InputStream inputStream) throws IOException {
        StringBuffer sb = new StringBuffer();
        byte[] b = new byte[4096];
        while (inputStream.available() != 0) {
            int n = inputStream.read(b);
            sb.append(new String(b, 0, n));
        }
        return sb.toString();
    }

}
