public class TestCpuLoad {
    public static void main(String[] args) throws Exception {
        javax.management.MBeanServer mbs = java.lang.management.ManagementFactory.getPlatformMBeanServer();
        javax.management.ObjectName name = javax.management.ObjectName.getInstance("java.lang:type=OperatingSystem");
        try { System.out.println("CpuLoad: " + mbs.getAttribute(name, "CpuLoad")); } catch(Exception e){ System.out.println("No CpuLoad"); }
        try { System.out.println("SystemCpuLoad: " + mbs.getAttribute(name, "SystemCpuLoad")); } catch(Exception e){ System.out.println("No SystemCpuLoad"); }
        try { System.out.println("ProcessCpuLoad: " + mbs.getAttribute(name, "ProcessCpuLoad")); } catch(Exception e){ System.out.println("No ProcessCpuLoad"); }
    }
}
