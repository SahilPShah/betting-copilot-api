package com.bettingcopilot.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.bettingcopilot.api.entity.Game;
import com.bettingcopilot.api.entity.SlateRun;
import com.bettingcopilot.api.repository.GameRepository;
import com.bettingcopilot.api.repository.SlateRunRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Tag("unit")
class HealthServiceTest {

    @Mock GameRepository gameRepo;

    @Mock SlateRunRepository slateRunRepo;

    @Test
    void getHealth_returnsLatestDates() {
        Game game = new Game();
        game.setGameId("2026-04-29-CLE-TBR-1");
        game.setGameDate(LocalDate.of(2026, 4, 29));

        SlateRun run = new SlateRun();
        run.setRunDate(LocalDate.of(2026, 4, 28));

        when(gameRepo.findTopByOrderByGameDateDesc()).thenReturn(Optional.of(game));
        when(slateRunRepo.findTopByOrderByRanAtDesc()).thenReturn(Optional.of(run));

        var resp = new HealthService(gameRepo, slateRunRepo).getHealth();

        assertEquals("ok", resp.getStatus());
        assertEquals(LocalDate.of(2026, 4, 29), resp.getLatestGameDate());
        assertEquals(LocalDate.of(2026, 4, 28), resp.getLatestSlateDate());
    }

    @Test
    void getHealth_emptyDatabase_datesAreNull() {
        when(gameRepo.findTopByOrderByGameDateDesc()).thenReturn(Optional.empty());
        when(slateRunRepo.findTopByOrderByRanAtDesc()).thenReturn(Optional.empty());

        var resp = new HealthService(gameRepo, slateRunRepo).getHealth();

        assertEquals("ok", resp.getStatus());
        assertNull(resp.getLatestGameDate());
        assertNull(resp.getLatestSlateDate());
    }
}
