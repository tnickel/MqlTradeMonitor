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
        List<AccountEntity> nodes = accountRepository.findAll();
        List<CopierLinkEntity> edges = copierLinkRepository.findAll();
        return ResponseEntity.ok(Map.of("nodes", nodes, "edges", edges));
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
            "intervalMins", globalConfigService.getCopierIntervalMins()
        ));
    }

    @PostMapping("/settings")
    public ResponseEntity<?> updateSettings(@RequestBody Map<String, Integer> payload) {
        int tol = payload.getOrDefault("toleranceSeconds", globalConfigService.getCopierToleranceSeconds());
        int inter = payload.getOrDefault("intervalMins", globalConfigService.getCopierIntervalMins());
        globalConfigService.saveCopierConfig(tol, inter);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/link")
    public ResponseEntity<?> createLink(@RequestBody CopierLinkEntity link) {
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
