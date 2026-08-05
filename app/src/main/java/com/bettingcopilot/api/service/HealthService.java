package com.bettingcopilot.api.service;

import com.bettingcopilot.api.dto.HealthResponse;
import com.bettingcopilot.api.entity.Game;
import com.bettingcopilot.api.entity.SlateRun;
import com.bettingcopilot.api.repository.GameRepository;
import com.bettingcopilot.api.repository.SlateRunRepository;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final GameRepository gameRepo;
    private final SlateRunRepository slateRunRepo;

    public HealthService(GameRepository gameRepo, SlateRunRepository slateRunRepo) {
        this.gameRepo = gameRepo;
        this.slateRunRepo = slateRunRepo;
    }

    public HealthResponse getHealth() {
        HealthResponse resp = new HealthResponse();
        resp.setStatus("ok");
        gameRepo.findTopByOrderByGameDateDesc()
                .map(Game::getGameDate)
                .ifPresent(resp::setLatestGameDate);
        slateRunRepo
                .findTopByOrderByRanAtDesc()
                .map(SlateRun::getRunDate)
                .ifPresent(resp::setLatestSlateDate);
        return resp;
    }
}
