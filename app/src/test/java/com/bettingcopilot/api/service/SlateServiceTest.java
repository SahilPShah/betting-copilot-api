package com.bettingcopilot.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.bettingcopilot.api.entity.Recommendation;
import com.bettingcopilot.api.entity.SlateRun;
import com.bettingcopilot.api.repository.GameRepository;
import com.bettingcopilot.api.repository.RecommendationRepository;
import com.bettingcopilot.api.repository.SlateRunRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class SlateServiceTest {

    @Mock SlateRunRepository slateRunRepo;

    @Mock RecommendationRepository recRepo;

    @Mock GameRepository gameRepo;

    @Test
    void getSlate_returnsPicksAndNoBets() {
        SlateService slateService =
                new SlateService(slateRunRepo, recRepo, gameRepo, new ObjectMapper());
        LocalDate date = LocalDate.of(2026, 4, 29);
        UUID runId = UUID.randomUUID();

        SlateRun run = new SlateRun();
        run.setSlateRunId(runId);
        run.setRunDate(date);
        run.setModelVersion("v4");
        run.setRanAt(OffsetDateTime.now());

        Recommendation pick = new Recommendation();
        pick.setRecId(UUID.randomUUID());
        pick.setGameId("g1");
        pick.setDecision("medium");
        pick.setMarket("moneyline");
        pick.setSide("home");
        pick.setEdge(0.1);
        pick.setConfidence(7.0);

        Recommendation noBet = new Recommendation();
        noBet.setRecId(UUID.randomUUID());
        noBet.setGameId("g2");
        noBet.setDecision("no_bet");
        noBet.setNoBetReason("negative_edge");

        when(slateRunRepo.findByRunDate(date)).thenReturn(Optional.of(run));
        when(recRepo.findDeduplicatedBySlateRunId(runId)).thenReturn(List.of(pick, noBet));
        when(gameRepo.findAllById(org.mockito.ArgumentMatchers.anyIterable()))
                .thenReturn(List.of());

        var resp = slateService.getSlate(date);
        assertEquals(date, resp.getRunDate());
        assertEquals(1, resp.getPicks().size());
        assertEquals(1, resp.getNoBets().size());
        assertEquals("g2", resp.getNoBets().getFirst().getGameId());
        assertEquals("negative_edge", resp.getNoBets().getFirst().getReason());
    }

    @Test
    void getSlate_notFound_throws404() {
        SlateService slateService =
                new SlateService(slateRunRepo, recRepo, gameRepo, new ObjectMapper());
        LocalDate date = LocalDate.of(1999, 1, 1);
        when(slateRunRepo.findByRunDate(date)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> slateService.getSlate(date));
    }
}
