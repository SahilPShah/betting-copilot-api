CREATE TABLE IF NOT EXISTS slate_runs (
    slate_run_id UUID PRIMARY KEY,
    run_date     DATE NOT NULL UNIQUE,
    model_version VARCHAR NOT NULL,
    games_count  INTEGER,
    picks_count  INTEGER,
    ran_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS games (
    game_id       VARCHAR PRIMARY KEY,
    game_date     DATE NOT NULL,
    first_pitch_utc TIMESTAMPTZ,
    home_team_id  VARCHAR,
    away_team_id  VARCHAR,
    status        VARCHAR DEFAULT 'scheduled',
    home_score    INTEGER,
    away_score    INTEGER
);

CREATE TABLE IF NOT EXISTS predictions (
    prediction_id  UUID PRIMARY KEY,
    game_id        VARCHAR UNIQUE,
    model_version  VARCHAR NOT NULL,
    home_win_prob  NUMERIC NOT NULL,
    away_win_prob  NUMERIC NOT NULL,
    predicted_margin NUMERIC,
    predicted_total  NUMERIC,
    home_cover_prob  NUMERIC,
    away_cover_prob  NUMERIC,
    elo_diff         DOUBLE PRECISION,
    created_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS odds_snapshots (
    snapshot_id    UUID PRIMARY KEY,
    game_id        VARCHAR,
    bookmaker      VARCHAR NOT NULL,
    market         VARCHAR NOT NULL,
    side           VARCHAR NOT NULL,
    american_odds  INTEGER NOT NULL,
    run_line_point NUMERIC,
    implied_prob   NUMERIC NOT NULL,
    captured_at    TIMESTAMPTZ NOT NULL,
    is_closing     BOOLEAN DEFAULT false
);

CREATE TABLE IF NOT EXISTS recommendations (
    rec_id           UUID PRIMARY KEY,
    slate_run_id     UUID REFERENCES slate_runs(slate_run_id),
    prediction_id    UUID,
    odds_snapshot_id UUID,
    game_id          VARCHAR,
    market           VARCHAR NOT NULL,
    side             VARCHAR NOT NULL,
    edge             NUMERIC NOT NULL,
    confidence       NUMERIC NOT NULL,
    decision         VARCHAR NOT NULL,
    no_bet_reason    VARCHAR,
    context_snapshot JSONB,
    llm_explanation  TEXT,
    created_at       TIMESTAMPTZ NOT NULL
);

