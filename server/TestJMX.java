import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;

public class TestJMX {
    public static void main(String[] args) throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = ObjectName.getInstance("java.lang:type=OperatingSystem");
        try {
            System.out.println("CpuLoad: " + mbs.getAttribute(name, "CpuLoad"));
        } catch (Exception e) {
            System.out.println("CpuLoad failed: " + e.getMessage());
        }
        try {
            System.out.println("ProcessCpuLoad: " + mbs.getAttribute(name, "ProcessCpuLoad"));
        } catch (Exception e) {
            System.out.println("ProcessCpuLoad failed: " + e.getMessage());
        }
        try {
            System.out.println("SystemCpuLoad: " + mbs.getAttribute(name, "SystemCpuLoad"));
        } catch (Exception e) {
            System.out.println("SystemCpuLoad failed: " + e.getMessage());
        }
    }
}
