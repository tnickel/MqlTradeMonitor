package de.trademonitor.controller.api;

import de.trademonitor.entity.AccountEntity;
import de.trademonitor.entity.CopierLinkEntity;
import de.trademonitor.repository.AccountRepository;
import de.trademonitor.repository.CopierLinkRepository;
import de.trademonitor.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/copier-map")
public class CopierMapApiController {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CopierLinkRepository copierLinkRepository;

    @Autowired
    private de.trademonitor.service.GlobalConfigService globalConfigService;

    @Autowired
    private de.trademonitor.service.CopierVerificationService copierVerificationService;

    @Autowired
    private de.trademonitor.service.AccountAccessService accountAccessService;

    private boolean isAdmin(CustomUserDetails userDetails) {
        return userDetails != null && "ROLE_ADMIN".equals(userDetails.getUserEntity().getRole());
    }

    /** Whether the caller may see the given account (admin, CSV, or explicitly allowed). */
    private boolean mayAccess(CustomUserDetails userDetails, AccountEntity acc) {
        if (userDetails == null || acc == null) return false;
        if (isAdmin(userDetails)) return true;
        if ("CSV".equalsIgnoreCase(acc.getType())) return true;
        return accountAccessService.canAccess(
                userDetails.getUserEntity(), acc.getAccountId(), acc.getRealAccountId());
    }

    @GetMapping("/data")
    public ResponseEntity<?> getMapData(@AuthenticationPrincipal CustomUserDetails userDetails) {
        List<AccountEntity> nodes = accountRepository.findAll().stream()
                .filter(acc -> !"CSV".equalsIgnoreCase(acc.getType()))
                .filter(acc -> acc.getMonitored() == null || acc.getMonitored())
                .filter(acc -> mayAccess(userDetails, acc))
                .collect(java.util.stream.Collectors.toList());
        List<CopierLinkEntity> edges = copierLinkRepository.findAll();
        java.util.Set<Long> nodeIds = nodes.stream()
                .map(AccountEntity::getAccountId)
                .collect(java.util.stream.Collectors.toSet());
        List<CopierLinkEntity> filteredEdges = edges.stream()
                .filter(edge -> nodeIds.contains(edge.getSourceAccountId()) && nodeIds.contains(edge.getTargetAccountId()))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(Map.of("nodes", nodes, "edges", filteredEdges));
    }

    @GetMapping("/verify/{accountId}")
    public ResponseEntity<?> getVerificationReport(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long accountId) {
        AccountEntity acc = accountRepository.findById(accountId).orElse(null);
        if (!mayAccess(userDetails, acc)) {
            return ResponseEntity.status(403).body(Map.of("error", "Access Denied"));
        }
        de.trademonitor.dto.CopierVerificationReportDto report = copierVerificationService.generateReport(accountId);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(report);
    }

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings() {
        return ResponseEntity.ok(Map.of(
            "toleranceSeconds", globalConfigService.getCopierToleranceSeconds(),
            "intervalMins", globalConfigService.getCopierIntervalMins(),
            "useStage1", globalConfigService.isCopierUseStage1(),
            "useStage2", globalConfigService.isCopierUseStage2(),
            "stage2Tolerance", globalConfigService.getCopierStage2Tolerance(),
            "useStage3", globalConfigService.isCopierUseStage3()
        ));
    }

    @PostMapping("/settings")
    public ResponseEntity<?> updateSettings(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> payload) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        int tol = payload.containsKey("toleranceSeconds") ? ((Number) payload.get("toleranceSeconds")).intValue() : globalConfigService.getCopierToleranceSeconds();
        int inter = payload.containsKey("intervalMins") ? ((Number) payload.get("intervalMins")).intValue() : globalConfigService.getCopierIntervalMins();
        
        boolean useStage1 = payload.containsKey("useStage1") ? (boolean) payload.get("useStage1") : globalConfigService.isCopierUseStage1();
        boolean useStage2 = payload.containsKey("useStage2") ? (boolean) payload.get("useStage2") : globalConfigService.isCopierUseStage2();
        double stage2Tol = payload.containsKey("stage2Tolerance") ? ((Number) payload.get("stage2Tolerance")).doubleValue() : globalConfigService.getCopierStage2Tolerance();
        boolean useStage3 = payload.containsKey("useStage3") ? (boolean) payload.get("useStage3") : globalConfigService.isCopierUseStage3();

        globalConfigService.saveCopierConfig(tol, inter);
        globalConfigService.saveCopierStageConfig(useStage1, useStage2, stage2Tol, useStage3);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/link")
    public ResponseEntity<?> createLink(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CopierLinkEntity link) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(403).body("Admin access required");
        }
        Optional<AccountEntity> srcOpt = accountRepository.findById(link.getSourceAccountId());
        Optional<AccountEntity> tgtOpt = accountRepository.findById(link.getTargetAccountId());
        if (srcOpt.isPresent()) {
            AccountEntity src = srcOpt.get();
            if ("CSV".equalsIgnoreCase(src.getType())) {
                return ResponseEntity.badRequest().body("CSV accounts cannot be linked in copier map");
            }
            if (Boolean.FALSE.equals(src.getMonitored())) {
                return ResponseEntity.badRequest().body("Unmonitored accounts cannot be linked in copier map");
            }
        }
        if (tgtOpt.isPresent()) {
            AccountEntity tgt = tgtOpt.get();
            if ("CSV".equalsIgnoreCase(tgt.getType())) {
                return ResponseEntity.badRequest().body("CSV accounts cannot be linked in copier map");
            }
            if (Boolean.FALSE.equals(tgt.getMonitored())) {
                return ResponseEntity.badRequest().body("Unmonitored accounts cannot be linked in copier map");
            }
        }
        CopierLinkEntity saved = copierLinkRepository.save(link);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/link/{id}")
    public ResponseEntity<?> deleteLink(@AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(403).body("Admin access required");
        }
        copierLinkRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/positions")
    public ResponseEntity<?> updatePositions(@AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody List<NodePositionDto> positions) {
        if (!isAdmin(userDetails)) {
            return ResponseEntity.status(403).body("Admin access required");
        }
        for (NodePositionDto pos : positions) {
            Optional<AccountEntity> accOpt = accountRepository.findById(pos.getAccountId());
            if (accOpt.isPresent()) {
                AccountEntity acc = accOpt.get();
                acc.setMapPosX(pos.getX());
                acc.setMapPosY(pos.getY());
                accountRepository.save(acc);
            }
        }
        return ResponseEntity.ok().build();
    }

    public static class NodePositionDto {
        private Long accountId;
        private Double x;
        private Double y;

        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        public Double getX() { return x; }
        public void setX(Double x) { this.x = x; }
        public Double getY() { return y; }
        public void setY(Double y) { this.y = y; }
    }
}
