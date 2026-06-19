package de.trademonitor.controller.api;

import de.trademonitor.entity.AccountEntity;
import de.trademonitor.entity.CopierLinkEntity;
import de.trademonitor.repository.AccountRepository;
import de.trademonitor.repository.CopierLinkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/data")
    public ResponseEntity<?> getMapData() {
        List<AccountEntity> nodes = accountRepository.findAll().stream()
                .filter(acc -> !"CSV".equalsIgnoreCase(acc.getType()))
                .filter(acc -> acc.getMonitored() == null || acc.getMonitored())
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
    public ResponseEntity<?> getVerificationReport(@PathVariable Long accountId) {
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
    public ResponseEntity<?> updateSettings(@RequestBody Map<String, Object> payload) {
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
    public ResponseEntity<?> createLink(@RequestBody CopierLinkEntity link) {
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
    public ResponseEntity<?> deleteLink(@PathVariable Long id) {
        copierLinkRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/positions")
    public ResponseEntity<?> updatePositions(@RequestBody List<NodePositionDto> positions) {
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
