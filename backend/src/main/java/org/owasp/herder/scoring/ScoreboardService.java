/*
 * Copyright Jonathan Jogenfors, jonathan@jogenfors.se
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.owasp.herder.scoring;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.owasp.herder.scoring.ScoreboardEntry.ScoreboardEntryBuilder;
import org.owasp.herder.user.SolverEntity;
import org.owasp.herder.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Validated
@Service
public class ScoreboardService {

  private final ScoreboardRepository scoreboardRepository;

  private final RankedSubmissionRepository rankedSubmissionRepository;

  private final UserService userService;

  @NoArgsConstructor
  @FieldDefaults(level = AccessLevel.PRIVATE)
  private final class ScoreboardEntryState {

    long currentScore = 0;
    long currentGoldMedals = 0;
    long currentSilverMedals = 0;
    long currentBronzeMedals = 0;
    long currentRank = 1L;
    long entryRank = 1L;

    // The rank given to all users and teams without submissions
    long zeroScoreRank = 0;

    // At what position to insert all zero score users and teams
    @Getter
    long zeroScorePosition = 0;

    boolean negativeScoresFound = false;

    Long entryScore;
    Long entryGoldMedals;
    Long entrySilverMedals;
    Long entryBronzeMedals;

    @Setter
    Long idleUserCount;

    public void readFirstScoreboardEntry(final UnrankedScoreboardEntry firstEntry) {
      currentScore = firstEntry.getScore();
      currentGoldMedals = firstEntry.getGoldMedals();
      currentSilverMedals = firstEntry.getSilverMedals();
      currentBronzeMedals = firstEntry.getBronzeMedals();
    }

    private boolean isCurrentlyZero() {
      return currentScore == 0 && currentGoldMedals == 0 && currentSilverMedals == 0 && currentBronzeMedals == 0;
    }

    private boolean hasCorrectSort(UnrankedScoreboardEntry nextEntry) {
      if (nextEntry.getScore() > currentScore) {
        // If the next score is greater than the current score then we have a bad sort
        return false;
      }
      if (nextEntry.getScore() < currentScore) {
        // If the next score is less than the current score then all is good
        return true;
      }

      // From now on, next score is equal to current score
      if (nextEntry.getGoldMedals() > currentGoldMedals) {
        // If the next gold medal count is greater than the current gold medal count then we have a bad sort
        return false;
      }
      if (nextEntry.getGoldMedals() < currentGoldMedals) {
        // If the next gold medal count is less than the current gold medal count then all is good
        return true;
      }

      // From now on, next gold medal count is equal to current gold medal count
      if (nextEntry.getSilverMedals() > currentSilverMedals) {
        // If the next silver medal count is greater than the current silver medal count then we have a bad sort
        return false;
      }
      if (nextEntry.getSilverMedals() < currentSilverMedals) {
        // If the next silver medal count is less than the current silver medal count then all is good
        return true;
      }

      // At this point we only need to check bronze medal sorting
      return nextEntry.getBronzeMedals() <= currentBronzeMedals;
    }

    public ScoreboardEntry processCurrentEntry(UnrankedScoreboardEntry currentEntry) {
      // Sanity check: The unranked scoreboard is assumed to be sorted
      if (!hasCorrectSort(currentEntry)) {
        throw new IllegalArgumentException("Unranked scoreboard is not correctly sorted");
      }

      entryScore = currentEntry.getScore();
      entryGoldMedals = currentEntry.getGoldMedals();
      entrySilverMedals = currentEntry.getSilverMedals();
      entryBronzeMedals = currentEntry.getBronzeMedals();

      // Check if the current score is below zero for the first time
      if ((entryScore < 0) && !negativeScoresFound) {
        negativeScoresFound = true;
        if (isCurrentlyZero()) {
          // Set the rank of all zero score users and teams to the current rank
          zeroScoreRank = entryRank;
        } else {
          zeroScoreRank = currentRank;
        }
        zeroScorePosition = currentRank;

        currentScore = 0;
        currentGoldMedals = 0;
        currentSilverMedals = 0;
        currentBronzeMedals = 0;

        // Increment the rank counter with the number of zero score users and teams
        currentRank += idleUserCount;
      }

      // Compare current score and medals with previous entry
      if (
        !entryScore.equals(currentScore) ||
        (!entryGoldMedals.equals(currentGoldMedals)) ||
        (!entrySilverMedals.equals(currentSilverMedals)) ||
        (!entryBronzeMedals.equals(currentBronzeMedals))
      ) {
        // Only advance the entry rank if score and medal count differs from previous
        // entry
        entryRank = currentRank;
      }

      // Construct the current scoreboard entry
      final ScoreboardEntryBuilder scoreboardEntryBuilder = ScoreboardEntry.builder();

      String principalId;
      SolverType solverType;

      if (currentEntry.getTeam() != null) {
        principalId = currentEntry.getTeam().getId();
        solverType = SolverType.TEAM;
        scoreboardEntryBuilder.displayName(currentEntry.getTeam().getDisplayName());
      } else {
        principalId = currentEntry.getUser().getId();
        solverType = SolverType.USER;
        scoreboardEntryBuilder.displayName(currentEntry.getUser().getDisplayName());
      }
      scoreboardEntryBuilder.principalId(principalId);
      scoreboardEntryBuilder.solverType(solverType);

      scoreboardEntryBuilder.baseScore(currentEntry.getBaseScore());
      scoreboardEntryBuilder.bonusScore(currentEntry.getBonusScore());
      scoreboardEntryBuilder.score(entryScore);

      scoreboardEntryBuilder.goldMedals(entryGoldMedals);
      scoreboardEntryBuilder.silverMedals(entrySilverMedals);
      scoreboardEntryBuilder.bronzeMedals(entryBronzeMedals);

      scoreboardEntryBuilder.rank(entryRank);

      // Advance the current rank value
      currentRank++;

      // Update the previous state
      currentScore = entryScore;
      currentGoldMedals = entryGoldMedals;
      currentSilverMedals = entrySilverMedals;
      currentBronzeMedals = entryBronzeMedals;

      return scoreboardEntryBuilder.build();
    }

    public Stream<ScoreboardEntry> loadRemainingPrincipals(final Stream<SolverEntity> remainingPrincipals) {
      if (!negativeScoresFound) {
        zeroScoreRank = currentRank;
        zeroScorePosition = currentRank;
      }
      // Create scoreboard entries for all zero score principals

      ScoreboardEntryBuilder zeroScoreboardEntryBuilder = ScoreboardEntry.builder();
      zeroScoreboardEntryBuilder.baseScore(0L);
      zeroScoreboardEntryBuilder.bonusScore(0L);
      zeroScoreboardEntryBuilder.score(0L);

      zeroScoreboardEntryBuilder.goldMedals(0L);
      zeroScoreboardEntryBuilder.silverMedals(0L);
      zeroScoreboardEntryBuilder.bronzeMedals(0L);

      // All zero score principals have the same rank
      zeroScoreboardEntryBuilder.rank(zeroScoreRank);

      // Build the scoreboard entries
      return remainingPrincipals.map(principal -> {
        zeroScoreboardEntryBuilder.displayName(principal.getDisplayName());
        zeroScoreboardEntryBuilder.principalId(principal.getId());
        zeroScoreboardEntryBuilder.solverType(principal.getSolverType());
        return zeroScoreboardEntryBuilder.build();
      });
    }
  }

  /**
   * Refresh the scoreboard. Reads all ranked submissions from db and stores a scoreboard with ranks
   * and medal counts of all users and teams in the db.
   *
   * <p>To be called whenever a scoreboard refresh is needed, for instance a valid flag submission,
   * user creation, team membership change, or score adjustment
   *
   * @return a Mono<Void> signaling completion
   */
  public Mono<Void> refreshScoreboard() {
    return rankedSubmissionRepository
      // Get unranked scoreboard entries from db. This list is sorted by score and medal count
      .getUnrankedScoreboard()
      // Transform the flux to a mono list
      .collectList()
      // Get the complete list of all users and teams
      .zipWith(userService.findAllSolvers().collectList())
      .map(tuple -> {
        final List<UnrankedScoreboardEntry> unrankedScoreboard = tuple.getT1();

        // The complete list of users and teams. We need this here because we want users
        // without submissions to be listed on the scoreboard with zero score and medals
        Stream<SolverEntity> remainingSolvers = tuple.getT2().stream();

        final ScoreboardEntryState scoreboardEntryState = new ScoreboardEntryState();

        scoreboardEntryState.setIdleUserCount(Long.valueOf(tuple.getT2().size()) - unrankedScoreboard.size());

        if (!unrankedScoreboard.isEmpty()) {
          scoreboardEntryState.readFirstScoreboardEntry(unrankedScoreboard.get(0));
        }

        // Create the empty scoreboard
        final ArrayList<ScoreboardEntry> scoreboard = new ArrayList<>();

        final Iterator<UnrankedScoreboardEntry> scoreboardIterator = unrankedScoreboard.iterator();

        while (scoreboardIterator.hasNext()) {
          // Get the current score and medal count
          final ScoreboardEntry newScoreboardEntry = scoreboardEntryState.processCurrentEntry(
            scoreboardIterator.next()
          );

          // Add the current scoreboard entry to the scoreboard
          scoreboard.add(newScoreboardEntry);

          // Remove the current scoreboard user from the list of zero score users and teams
          remainingSolvers =
            remainingSolvers.filter(principal ->
              !principal.getId().equals(newScoreboardEntry.getPrincipalId()) ||
              !principal.getSolverType().equals(newScoreboardEntry.getSolverType())
            );
        }

        // Were there principals with negative scores? (This can happen due to a negative score
        // adjustment)
        final Stream<ScoreboardEntry> zeroScoreboardPrincipals = scoreboardEntryState.loadRemainingPrincipals(
          remainingSolvers
        );

        // All scoreboard entries before the zero score principals
        final Stream<ScoreboardEntry> scoreboardAboveZeroStream = scoreboard
          .stream()
          .limit(scoreboardEntryState.getZeroScorePosition() - 1);

        // All scoreboard entries after the zero score principals
        final Stream<ScoreboardEntry> scoreboardBelowZeroStream = scoreboard
          .stream()
          .skip(scoreboardEntryState.getZeroScorePosition() - 1);
        // Concatenate the scoreboard and create an array list
        final List<ScoreboardEntry> rankedScoreboard = Stream
          .concat(Stream.concat(scoreboardAboveZeroStream, zeroScoreboardPrincipals), scoreboardBelowZeroStream)
          .collect(Collectors.toCollection(ArrayList::new));

        // Sanity check. The remaining principals should match the idle principal count
        if (
          rankedScoreboard.size() -
          unrankedScoreboard.size() !=
          Long.valueOf(tuple.getT2().size()) -
          unrankedScoreboard.size()
        ) {
          throw new IllegalStateException("Idle user count does not match remaining principal count!");
        }
        return rankedScoreboard;
      })
      // Clear the scoreboard and save all scoreboard entries
      .flatMapMany(scoreboard -> scoreboardRepository.deleteAll().thenMany(scoreboardRepository.saveAll(scoreboard)))
      .then();
  }

  public Flux<ScoreboardEntry> getScoreboard() {
    return scoreboardRepository.findAll();
  }
}
