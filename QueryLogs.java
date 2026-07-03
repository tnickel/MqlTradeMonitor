import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class QueryLogs {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:h2:file:d:/AntiGravitySoftware/GitWorkspace/MqlTradeMonitor/trademonitor";
        Connection conn = DriverManager.getConnection(url, "sa", "");
        Statement stat = conn.createStatement();
        ResultSet rs = stat.executeQuery("SELECT timestamp, log_line FROM ea_log_entry WHERE account_id = 6167113 ORDER BY timestamp DESC LIMIT 1000");
        int count = 0;
        while (rs.next()) {
            String line = rs.getString("log_line");
            if (line.contains("5033598948") || line.contains("GBPUSD")) {
                System.out.println(rs.getString("timestamp") + " - " + line);
                count++;
            }
        }
        if (count == 0) System.out.println("No matching logs found.");
        conn.close();
    }
}
