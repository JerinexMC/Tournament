package com.casualsmp.tournament;

import java.util.*;

public class BracketManager {

    private final Map<Integer, List<Match>> rounds = new TreeMap<>();
    private int totalRounds = 0;

    public void clear() {
        rounds.clear();
        totalRounds = 0;
    }

    /**
     * Generates a single-elimination bracket for {@code teams} (size >= 2).
     * Non-power-of-two counts use BYEs in Round 1 so winners advance automatically.
     */
    public void generateBracket(List<Team> inputTeams) {
        clear();
        if (inputTeams == null || inputTeams.size() < 2) {
            return;
        }

        List<Team> teams = new ArrayList<>(inputTeams);
        Collections.shuffle(teams, new Random());

        int n = teams.size();
        int bracketSize = 1;
        while (bracketSize < n) {
            bracketSize <<= 1;
        }

        this.totalRounds = (int) (Math.log(bracketSize) / Math.log(2));
        int numByes = bracketSize - n;
        int totalRound1Slots = bracketSize / 2;

        // Distribute BYEs evenly across Round 1 slots so each BYE slot has 1 team + BYE
        List<Match> round1Matches = new ArrayList<>(totalRound1Slots);
        int teamIdx = 0;

        for (int i = 0; i < totalRound1Slots; i++) {
            String matchId = "R1-M" + (i + 1);
            boolean hasBye = i < numByes;
            if (hasBye) {
                Team soloAdvancer = teams.get(teamIdx++);
                Match byeMatch = new Match(matchId, 1, i, soloAdvancer, null, true);
                byeMatch.setWinner(soloAdvancer);
                byeMatch.setStatus(MatchStatus.FINISHED);
                byeMatch.setWinnerProcessed(true);
                byeMatch.setStartTimeMillis(System.currentTimeMillis());
                byeMatch.setEndTimeMillis(System.currentTimeMillis());
                round1Matches.add(byeMatch);
            } else {
                Team red = teams.get(teamIdx++);
                Team blue = teams.get(teamIdx++);
                Match match = new Match(matchId, 1, i, red, blue, false);
                round1Matches.add(match);
            }
        }

        rounds.put(1, round1Matches);

        // Create subsequent round slots with null teams until previous matches resolve
        int currentSlots = totalRound1Slots / 2;
        for (int r = 2; r <= totalRounds; r++) {
            List<Match> roundMatches = new ArrayList<>(currentSlots);
            for (int i = 0; i < currentSlots; i++) {
                String matchId = "R" + r + "-M" + (i + 1);
                Match m = new Match(matchId, r, i, null, null, false);
                roundMatches.add(m);
            }
            rounds.put(r, roundMatches);
            currentSlots = Math.max(1, currentSlots / 2);
        }

        // Immediately propagate BYE winners from Round 1 into Round 2
        if (totalRounds >= 2) {
            for (Match r1Match : round1Matches) {
                if (r1Match.isByeMatch() && r1Match.getWinner() != null) {
                    advanceWinnerToNextRound(r1Match, r1Match.getWinner());
                }
            }
        }
    }

    /**
     * Advances {@code winner} from {@code completedMatch} to the appropriate slot in {@code round + 1}.
     * Returns the target next-round Match if updated, or null if completedMatch was the Grand Final.
     */
    public Match advanceWinnerToNextRound(Match completedMatch, Team winner) {
        if (completedMatch == null || winner == null) return null;
        int nextRound = completedMatch.getRound() + 1;
        if (nextRound > totalRounds) {
            return null; // Grand Final completed
        }
        List<Match> nextRoundMatches = rounds.get(nextRound);
        if (nextRoundMatches == null || nextRoundMatches.isEmpty()) {
            return null;
        }
        int targetIndex = completedMatch.getIndexInRound() / 2;
        if (targetIndex < 0 || targetIndex >= nextRoundMatches.size()) {
            return null;
        }
        Match nextMatch = nextRoundMatches.get(targetIndex);
        if (completedMatch.getIndexInRound() % 2 == 0) {
            nextMatch.setTeamRed(winner);
        } else {
            nextMatch.setTeamBlue(winner);
        }
        return nextMatch;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public Map<Integer, List<Match>> getRounds() {
        return Collections.unmodifiableMap(rounds);
    }

    public List<Match> getRoundMatches(int round) {
        return rounds.getOrDefault(round, Collections.emptyList());
    }

    public Match getMatchById(String matchId) {
        if (matchId == null) return null;
        for (List<Match> list : rounds.values()) {
            for (Match m : list) {
                if (m.getMatchId().equalsIgnoreCase(matchId)) {
                    return m;
                }
            }
        }
        return null;
    }

    public Match getGrandFinalMatch() {
        if (totalRounds <= 0) return null;
        List<Match> finals = rounds.get(totalRounds);
        if (finals == null || finals.isEmpty()) return null;
        return finals.get(0);
    }

    public boolean isGrandFinal(Match match) {
        return match != null && match.getRound() == totalRounds;
    }
}
