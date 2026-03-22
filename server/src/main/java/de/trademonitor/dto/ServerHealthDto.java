package de.trademonitor.dto;

public class ServerHealthDto {
    private String osName;
    private String cpuLoad;
    private String totalMemory;
    private String freeMemory;
    private String usedMemory;
    private String diskTotal;
    private String diskFree;
    private String diskUsed;
    private String dbFileSize;
    private String logFileSize;

    public String getOsName() { return osName; }
    public void setOsName(String osName) { this.osName = osName; }

    public String getCpuLoad() { return cpuLoad; }
    public void setCpuLoad(String cpuLoad) { this.cpuLoad = cpuLoad; }

    public String getTotalMemory() { return totalMemory; }
    public void setTotalMemory(String totalMemory) { this.totalMemory = totalMemory; }

    public String getFreeMemory() { return freeMemory; }
    public void setFreeMemory(String freeMemory) { this.freeMemory = freeMemory; }

    public String getUsedMemory() { return usedMemory; }
    public void setUsedMemory(String usedMemory) { this.usedMemory = usedMemory; }

    public String getDiskTotal() { return diskTotal; }
    public void setDiskTotal(String diskTotal) { this.diskTotal = diskTotal; }

    public String getDiskFree() { return diskFree; }
    public void setDiskFree(String diskFree) { this.diskFree = diskFree; }

    public String getDiskUsed() { return diskUsed; }
    public void setDiskUsed(String diskUsed) { this.diskUsed = diskUsed; }

    public String getDbFileSize() { return dbFileSize; }
    public void setDbFileSize(String dbFileSize) { this.dbFileSize = dbFileSize; }

    public String getLogFileSize() { return logFileSize; }
    public void setLogFileSize(String logFileSize) { this.logFileSize = logFileSize; }

    private String systemTotalMemory;
    private String systemFreeMemory;
    private String systemUsedMemory;
    private String aiTaskManagerWarSize;
    private String rootWarSize;

    public String getSystemTotalMemory() { return systemTotalMemory; }
    public void setSystemTotalMemory(String systemTotalMemory) { this.systemTotalMemory = systemTotalMemory; }

    public String getSystemFreeMemory() { return systemFreeMemory; }
    public void setSystemFreeMemory(String systemFreeMemory) { this.systemFreeMemory = systemFreeMemory; }

    public String getSystemUsedMemory() { return systemUsedMemory; }
    public void setSystemUsedMemory(String systemUsedMemory) { this.systemUsedMemory = systemUsedMemory; }

    public String getAiTaskManagerWarSize() { return aiTaskManagerWarSize; }
    public void setAiTaskManagerWarSize(String aiTaskManagerWarSize) { this.aiTaskManagerWarSize = aiTaskManagerWarSize; }

    public String getRootWarSize() { return rootWarSize; }
    public void setRootWarSize(String rootWarSize) { this.rootWarSize = rootWarSize; }
}
