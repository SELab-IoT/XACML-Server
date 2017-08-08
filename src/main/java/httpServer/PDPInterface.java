package httpServer;

import org.wso2.balana.*;
import org.wso2.balana.attr.AttributeFactory;
import org.wso2.balana.combine.CombiningAlgFactory;
import org.wso2.balana.cond.FunctionFactoryProxy;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class PDPInterface {

    private static PDPInterface pdpInterface;
    private HashMap<String, PDP> pdpHashMap;
    private Balana balana;
    Connection conn;

    //Thread-safe singleton
    public static PDPInterface getInstance() {
        return pdpInterface = Singleton.instance;
    }
    private PDPInterface() {
        try {
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/pdp?autoReconnect=true&useSSL=false&" +
                    "user=finder&password=asdasd");
            pdpHashMap = new HashMap<>();
        } catch (SQLException e) {
            conn = null;
            e.printStackTrace();
        }

        initBalana();
    }
    private static class Singleton{
        private static final PDPInterface instance = new PDPInterface();
    }

    // API 1. evaluate
    public String evaluate(String request, String pepId) {
        // 매번 생성하는 로드를 줄이기 위한 방법을 검토해볼 것.
        // (예를들어, 현재 PDP와 pdpConfigName이 같다면 재활용 한다던지...
        // 단 같은 이름이어도 config.xml이 수정될수도 있으니 유의해야함)
        String pdpName = getPDPConfigName(pepId);
        if (pdpName != null) {
            PDP pdp = pdpHashMap.get(pdpName);
            return pdp.evaluate(request);
        } else {
            System.out.println("pdpName is null");
            return null;
        }
    }

    private String getPDPConfigName(String pepId) {

        Statement stmt = null;
        ResultSet rs = null;
        String query = "SELECT name FROM pdp JOIN pep on pep.pdp_id=pdp._id where pep_id='" + pepId + "'";
        String pdpName = null;
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
            while (rs.next()) {
                String name = rs.getString(1);
                pdpName = name;
            }

        } catch (SQLException ex) {
//                System.out.println("SQLException: " + ex.getMessage());
//                System.out.println("SQLState: " + ex.getSQLState());
//                System.out.println("VendorError: " + ex.getErrorCode());
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException sqlEx) {
                } // ignore

                rs = null;
            }

            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException sqlEx) {
                } // ignore

                stmt = null;
            }
        }
        return pdpName;
    }

    private PDP getPDPNewInstance(String pdpConfigName) {
        reloadBalana(pdpConfigName, null, null);
        PDPConfig pdpConfig = balana.getPdpConfig();
        return new PDP(pdpConfig);
    }

    // API 2 ? (이 부분 API로 따야하는지?)
    private boolean reloadBalana(String pdpConfigName, String attributeFactoryName, String functionFactoryName) {
        try {
            ConfigurationStore configurationStore = new ConfigurationStore();
            if (configurationStore != null) {
                PDPConfig pdpConfig = pdpConfigName != null
                        ? configurationStore.getPDPConfig(pdpConfigName)
                        : configurationStore.getDefaultPDPConfig();

                AttributeFactory attributeFactory = attributeFactoryName != null
                        ? configurationStore.getAttributeFactory(attributeFactoryName)
                        : configurationStore.getDefaultAttributeFactoryProxy().getFactory();

                FunctionFactoryProxy proxy = functionFactoryName != null
                        ? configurationStore.getFunctionFactoryProxy(functionFactoryName)
                        : configurationStore.getDefaultFunctionFactoryProxy();

                CombiningAlgFactory combiningAlgFactory = functionFactoryName != null
                        ? configurationStore.getCombiningAlgFactory(functionFactoryName)
                        : configurationStore.getDefaultCombiningFactoryProxy().getFactory();

                balana.setPdpConfig(pdpConfig);
                balana.setAttributeFactory(attributeFactory);
                balana.setFunctionTargetFactory(proxy.getTargetFactory());
                balana.setFunctionConditionFactory(proxy.getConditionFactory());
                balana.setFunctionGeneralFactory(proxy.getGeneralFactory());
                balana.setCombiningAlgFactory(combiningAlgFactory);
            }
            return true;
        } catch (ParsingException | UnknownIdentifierException e) {
            e.printStackTrace();
            return false;
        }
    }

    //TODO: rest api로 제공 필요
    public boolean reloadPDP(String pdpName) {
        try {
            PDP pdp = getPDPNewInstance(pdpName);
            pdpHashMap.put(pdpName, pdp);
            return true;
        } catch (Exception e){
            return false;
        }
    }

    private void initBalana(){
        // Set balana config file.
        String configLocation = "resources"+File.separator+"config.xml";
        System.setProperty(ConfigurationStore.PDP_CONFIG_PROPERTY, configLocation);

        // Create default instance of Balana
        balana = Balana.getInstance();

        List<String> pdpNameList = getPDPNameList();
        for (String pdpName :
                pdpNameList) {
            reloadPDP(pdpName);
        }
    }

    private List<String> getPDPNameList() {
        Statement stmt = null;
        ResultSet rs = null;
        String query = "SELECT name FROM pdp";
        List<String> pdpNameList = new LinkedList<>();
        try {
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
            while (rs.next()) {
                String fileLoc = rs.getString(1);
                pdpNameList.add(fileLoc);
            }

        } catch (SQLException ex) {
//                System.out.println("SQLException: " + ex.getMessage());
//                System.out.println("SQLState: " + ex.getSQLState());
//                System.out.println("VendorError: " + ex.getErrorCode());
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException sqlEx) {
                } // ignore

                rs = null;
            }

            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException sqlEx) {
                } // ignore

                stmt = null;
            }
        }

        return pdpNameList;

    }


}
