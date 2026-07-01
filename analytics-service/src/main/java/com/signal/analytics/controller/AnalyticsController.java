package com.signal.analytics.controller;

import com.signal.common.dto.ApiResponse;
import com.signal.common.exception.UnauthorizedException;
import com.signal.analytics.service.RecommendationService;
import com.signal.analytics.service.RollupService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final RollupService rollupService;
    private final RecommendationService recommendationService;

    public AnalyticsController(RollupService rollupService, RecommendationService recommendationService) {
        this.rollupService = rollupService;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/team-load")
    public ApiResponse<List<RollupService.TeamLoadRecord>> getTeamLoad(@RequestParam UUID managerId) {
        validateManagerAccess(managerId);
        List<RollupService.TeamLoadRecord> load = rollupService.getTeamLoad(managerId);
        return ApiResponse.success(load);
    }

    @GetMapping("/recommendations")
    public ApiResponse<List<String>> getRecommendations(@RequestParam UUID managerId) {
        validateManagerAccess(managerId);
        List<String> recommendations = recommendationService.getRecommendations(managerId);
        return ApiResponse.success(recommendations);
    }

    private void validateManagerAccess(UUID managerId) {
        String principal = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID authenticatedUserId = UUID.fromString(principal);
        if (!authenticatedUserId.equals(managerId)) {
            throw new UnauthorizedException("Unauthorized: A manager can only query their own team's metrics.");
        }
    }
}
