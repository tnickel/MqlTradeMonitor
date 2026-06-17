public class TestCpuLoadLoop {
    public static void main(String[] args) throws Exception {
        javax.management.MBeanServer mbs = java.lang.management.ManagementFactory.getPlatformMBeanServer();
        javax.management.ObjectName name = javax.management.ObjectName.getInstance("java.lang:type=OperatingSystem");
        for(int i=0; i<5; i++) {
            try {
                System.out.println(i + " CpuLoad: " + mbs.getAttribute(name, "CpuLoad"));
            } catch(Exception e) {}
            try {
                System.out.println(i + " SystemCpuLoad: " + mbs.getAttribute(name, "SystemCpuLoad"));
            } catch(Exception e) {}
            Thread.sleep(1000);
        }
    }
}
