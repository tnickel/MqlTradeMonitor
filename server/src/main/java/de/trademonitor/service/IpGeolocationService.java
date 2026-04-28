package de.trademonitor.service;

import de.trademonitor.entity.BannedIpEntity;
import de.trademonitor.repository.BannedIpRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class IpGeolocationService {

    @Autowired
    private BannedIpRepository bannedIpRepository;

    @Autowired
    private SecurityAuditService securityAuditService;

    private final RestTemplate restTemplate = new RestTemplate();

    // Cache of currently banned IPs to avoid incrementing attack count continuously
    private final Set<String> knownCurrentlyBannedIps = new HashSet<>();

    private volatile boolean syncRunning = false;
    private volatile int syncTotal = 0;
    private volatile int syncProcessed = 0;
    private volatile int lastF2bCount = 0;

    public Map<String, Object> getSyncStatus() {
        return Map.of(
            "running", syncRunning,
            "total", syncTotal,
            "processed", syncProcessed,
            "dbCount", bannedIpRepository.count(),
            "f2bCount", lastF2bCount
        );
    }

    @jakarta.annotation.PostConstruct
    public void injectTestData() {
        if (bannedIpRepository.count() == 0) {
            System.out.println("[IpGeolocationService] Injecting test IPs for local development...");
            new Thread(() -> {
                processBannedIps(java.util.Arrays.asList(
                    "8.8.8.8",         // Google DNS (USA)
                    "1.1.1.1",         // Cloudflare (Australia)
                    "9.9.9.9",         // Quad9 (USA/Global)
                    "185.199.108.153", // GitHub (Netherlands)
                    "142.250.184.206", // Google DE (Germany)
                    "82.165.8.211"     // 1&1 IONOS (Germany)
                ));
            }).start();
        }
    }

    public static class IpApiResponse {
        public String status;
        public String country;
        public String city;
        public Double lat;
        public Double lon;
        public String isp;
        public String org;
    }

    /**
     * Look up IP via ip-api.com
     */
    public IpApiResponse lookupIp(String ip) {
        try {
            String url = "http://ip-api.com/json/" + ip;
            return restTemplate.getForObject(url, IpApiResponse.class);
        } catch (Exception e) {
            System.err.println("[IpGeolocationService] Error looking up IP " + ip + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Process a list of currently banned IPs from Fail2Ban.
     * Rate-limited locally to respect ip-api limit (45 req / min) -> ~1.5s per new IP.
     */
    public void processBannedIps(List<String> currentBannedIps) {
        lastF2bCount = currentBannedIps.size();
        if (syncRunning) return; // Prevent concurrent syncs
        
        try {
            syncRunning = true;
            syncTotal = currentBannedIps.size();
            syncProcessed = 0;

            Set<String> newCurrentlyBanned = new HashSet<>(currentBannedIps);

            // Mark all as NOT currently banned in DB, we'll update the ones that are
            List<BannedIpEntity> allDbIps = bannedIpRepository.findAll();
            for (BannedIpEntity entity : allDbIps) {
                if (entity.isCurrentlyBanned() && !newCurrentlyBanned.contains(entity.getIpAddress())) {
                    entity.setCurrentlyBanned(false);
                    bannedIpRepository.save(entity);
                }
            }

            for (String ip : currentBannedIps) {
                if (ip == null || ip.isBlank()) {
                    syncProcessed++;
                    continue;
                }
                
                Optional<BannedIpEntity> existingOpt = bannedIpRepository.findByIpAddress(ip);

                if (existingOpt.isPresent()) {
                    BannedIpEntity existing = existingOpt.get();
                    existing.setLastSeen(LocalDateTime.now());
                    existing.setCurrentlyBanned(true);

                    // If it wasn't in our local "known currently banned" cache, it's a new ban event for this IP
                    if (!knownCurrentlyBannedIps.contains(ip)) {
                        existing.setAttackCount(existing.getAttackCount() + 1);
                    }
                    
                    bannedIpRepository.save(existing);
                } else {
                    // New IP, need to look up
                    IpApiResponse response = lookupIp(ip);
                    BannedIpEntity newEntity = new BannedIpEntity(ip);
                    
                    if (response != null && "success".equals(response.status)) {
                        newEntity.setCountry(response.country);
                        newEntity.setCity(response.city);
                        newEntity.setLatitude(response.lat);
                        newEntity.setLongitude(response.lon);
                        newEntity.setIsp(response.isp);
                        newEntity.setOrg(response.org);
                    }
                    
                    bannedIpRepository.save(newEntity);

                    // Sleep to respect rate limits (45 req / min => ~1.33s per req)
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                syncProcessed++;
            }

            // Update local cache
            knownCurrentlyBannedIps.clear();
            knownCurrentlyBannedIps.addAll(newCurrentlyBanned);
            
        } finally {
            syncRunning = false;
        }
    }

    /**
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000)
    public void scheduledUpdate() {
        System.out.println("[IpGeolocationService] Running scheduled fetch for new Banned IPs...");
        List<Map<String, Object>> fail2banDetails = securityAuditService.getFail2banLiveDetails();
        
        Set<String> allBannedIps = new HashSet<>();
        for (Map<String, Object> jail : fail2banDetails) {
            Object ipListObj = jail.get("bannedIpList");
            if (ipListObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> ips = (List<String>) ipListObj;
                allBannedIps.addAll(ips);
            }
        }
        
        lastF2bCount = allBannedIps.size();
        
        if (!allBannedIps.isEmpty()) {
            processBannedIps(List.copyOf(allBannedIps));
        }
    }
}
