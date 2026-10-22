package com.mikeshaggy.backend.insight.api;

import com.mikeshaggy.backend.common.util.CurrentUserProvider;
import com.mikeshaggy.backend.insight.dto.InsightListResponse;
import com.mikeshaggy.backend.insight.service.InsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(InsightController.BASE_URL)
@RequiredArgsConstructor
public class InsightController {

    public static final String BASE_URL = "/api/insights";

    private final InsightService insightService;
    private final CurrentUserProvider currentUserProvider;

    /** Evaluates and lists the active insights of the wallet's current pay cycle (404 for a foreign wallet). */
    @GetMapping
    public ResponseEntity<InsightListResponse> getInsights(@RequestParam Integer walletId) {
        InsightListResponse insights = insightService.evaluate(
                walletId,
                currentUserProvider.getCurrentUserId()
        );
        return ResponseEntity.ok(insights);
    }

    /** Dismisses (never deletes) an insight; repeating it is a no-op, another user's id is 404. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> dismissInsight(@PathVariable Long id) {
        insightService.dismiss(id, currentUserProvider.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
