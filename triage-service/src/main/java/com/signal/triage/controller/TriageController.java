package com.signal.triage.controller;

import com.signal.common.dto.ApiResponse;
import com.signal.common.dto.Urgency;
import com.signal.triage.entity.SenderUrgencyBias;
import com.signal.triage.entity.TriageItem;
import com.signal.triage.entity.UserPreference;
import com.signal.triage.repository.SenderUrgencyBiasRepository;
import com.signal.triage.repository.TriageItemRepository;
import com.signal.triage.repository.UserPreferenceRepository;
import com.signal.triage.service.DigestService;
import com.signal.triage.service.FocusSlotService;
import com.signal.triage.service.RankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/triage")
public class TriageController {

    private static final Logger log = LoggerFactory.getLogger(TriageController.class);

    private final RankingService rankingService;
    private final FocusSlotService focusSlotService;
    private final DigestService digestService;
    private final TriageItemRepository triageItemRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final SenderUrgencyBiasRepository senderUrgencyBiasRepository;

    public TriageController(RankingService rankingService,
                            FocusSlotService focusSlotService,
                            DigestService digestService,
                            TriageItemRepository triageItemRepository,
                            UserPreferenceRepository userPreferenceRepository,
                            SenderUrgencyBiasRepository senderUrgencyBiasRepository) {
        this.rankingService = rankingService;
        this.focusSlotService = focusSlotService;
        this.digestService = digestService;
        this.triageItemRepository = triageItemRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.senderUrgencyBiasRepository = senderUrgencyBiasRepository;
    }

    @GetMapping("/today")
    public ApiResponse<List<TriageItem>> getTodayFeed() {
        UUID userId = getAuthenticatedUserId();
        List<TriageItem> feed = rankingService.getRankedFeed(userId);
        return ApiResponse.success(feed);
    }

    @GetMapping("/focus-slots")
    public ApiResponse<List<FocusSlotService.FocusSlot>> getFocusSlots() {
        UUID userId = getAuthenticatedUserId();
        List<FocusSlotService.FocusSlot> slots = focusSlotService.getFocusSlots(userId);
        return ApiResponse.success(slots);
    }

    @PostMapping("/items/{id}/feedback")
    public ApiResponse<Void> submitFeedback(@PathVariable UUID id, @RequestBody FeedbackRequest request) {
        UUID userId = getAuthenticatedUserId();
        Optional<TriageItem> itemOpt = triageItemRepository.findById(id);
        
        if (itemOpt.isEmpty()) {
            throw new IllegalArgumentException("Triage item not found: " + id);
        }
        
        TriageItem item = itemOpt.get();
        if (!item.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized to feedback on this item");
        }

        // 1. Update status
        item.setStatus(request.wasUrgent() ? "FEEDBACK_WAS_URGENT" : "FEEDBACK_NOT_URGENT");
        triageItemRepository.save(item);

        // 2. Adjust urgency bias
        boolean scoredUrgent = item.getUrgency() == Urgency.NOW || item.getUrgency() == Urgency.TODAY;
        int biasAdjustment = 0;
        
        if (request.wasUrgent() && !scoredUrgent) {
            // User states urgent, but system scored as not urgent -> increase bias
            biasAdjustment = 1;
        } else if (!request.wasUrgent() && scoredUrgent) {
            // User states not urgent, but system scored as urgent -> decrease bias
            biasAdjustment = -1;
        }

        if (biasAdjustment != 0) {
            final int adjustment = biasAdjustment;
            SenderUrgencyBias bias = senderUrgencyBiasRepository.findByUserIdAndSenderId(userId, item.getSenderId())
                    .orElseGet(() -> new SenderUrgencyBias(userId, item.getSenderId(), 0));
            
            bias.setBiasDelta(bias.getBiasDelta() + adjustment);
            senderUrgencyBiasRepository.save(bias);
            log.info("Adjusted sender bias for user {} and sender {}: new biasDelta={}",
                    userId, item.getSenderId(), bias.getBiasDelta());
        }

        return ApiResponse.success(null);
    }

    @PutMapping("/preferences")
    public ApiResponse<UserPreference> updatePreferences(@RequestBody PreferencesRequest request) {
        UUID userId = getAuthenticatedUserId();
        
        UserPreference preference = userPreferenceRepository.findById(userId)
                .orElse(new UserPreference(userId, "", null, "NONE"));

        if (request.statedPriorities() != null) {
            preference.setStatedPriorities(request.statedPriorities());
        }
        if (request.digestTime() != null) {
            preference.setDigestTime(LocalTime.parse(request.digestTime()));
        }
        if (request.digestChannel() != null) {
            preference.setDigestChannel(request.digestChannel());
        }

        UserPreference saved = userPreferenceRepository.save(preference);
        return ApiResponse.success(saved);
    }

    @PostMapping("/digest/send-now")
    public ApiResponse<Void> sendDigestNow() {
        UUID userId = getAuthenticatedUserId();
        digestService.sendDigest(userId);
        return ApiResponse.success(null);
    }

    // Internal Sync Endpoints (Service-to-Service)

    @GetMapping("/feedback/bias/{userId}/{senderId}")
    public ApiResponse<BiasResponse> getSenderBias(@PathVariable UUID userId, @PathVariable String senderId) {
        Optional<SenderUrgencyBias> biasOpt = senderUrgencyBiasRepository.findByUserIdAndSenderId(userId, senderId);
        int delta = biasOpt.map(SenderUrgencyBias::getBiasDelta).orElse(0);
        return ApiResponse.success(new BiasResponse(delta));
    }

    @GetMapping("/preferences/{userId}")
    public ApiResponse<PreferenceResponse> getPreferences(@PathVariable UUID userId) {
        Optional<UserPreference> prefOpt = userPreferenceRepository.findById(userId);
        String priorities = prefOpt.map(UserPreference::getStatedPriorities).orElse("");
        return ApiResponse.success(new PreferenceResponse(priorities));
    }

    private UUID getAuthenticatedUserId() {
        String userIdStr = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return UUID.fromString(userIdStr);
    }

    public record FeedbackRequest(boolean wasUrgent) {}
    public record PreferencesRequest(String statedPriorities, String digestTime, String digestChannel) {}
    public record BiasResponse(int biasDelta) {}
    public record PreferenceResponse(String statedPriorities) {}
}
