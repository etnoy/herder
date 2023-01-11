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
package org.owasp.herder.user;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.scoring.PrincipalType;
import org.owasp.herder.scoring.RankedSubmissionRepository;
import org.owasp.herder.scoring.ScoreboardEntry;
import org.owasp.herder.scoring.ScoreboardEntry.ScoreboardEntryBuilder;
import org.owasp.herder.scoring.ScoreboardRepository;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.UnrankedScoreboardEntry;
import org.owasp.herder.validation.ValidTeamId;
import org.owasp.herder.validation.ValidUserId;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Refreshes the database with information when embedded data must be updated */
@Slf4j
@RequiredArgsConstructor
@Validated
@Service
public class RefresherService {

  private final SubmissionRepository submissionRepository;

  private final ScoreboardRepository scoreboardRepository;

  private final RankedSubmissionRepository rankedSubmissionRepository;

  private final UserService userService;

  private final TeamRepository teamRepository;

  private final UserRepository userRepository;

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
      .zipWith(userService.findAllPrincipals().collectList())
      .map(tuple -> {
        final List<UnrankedScoreboardEntry> unrankedScoreboard = tuple.getT1();

        // The complete list of users and teams. We need this here because we want users
        // without submissions to be listed on the scoreboard with zero score and medals
        Stream<PrincipalEntity> principals = tuple.getT2().stream();

        long currentScore = 0;
        long currentGoldMedals = 0;
        long currentSilverMedals = 0;
        long currentBronzeMedals = 0;
        long currentRank = 1L;
        long entryRank = 1L;

        // The rank given to all users and teams without submissions
        long zeroScoreRank = 0;

        // At what position to insert all zero score users and teams
        long zeroScorePosition = 0;

        boolean negativeScoresFound = false;

        // Create the empty scoreboard
        final ArrayList<ScoreboardEntry> scoreboard = new ArrayList<>();

        final Iterator<UnrankedScoreboardEntry> scoreboardIterator = unrankedScoreboard.iterator();

        if (scoreboardIterator.hasNext()) {
          // Get initial values from first entry
          final UnrankedScoreboardEntry firstEntry = unrankedScoreboard.get(0);
          currentScore = firstEntry.getScore();
          currentGoldMedals = firstEntry.getGoldMedals();
          currentSilverMedals = firstEntry.getSilverMedals();
          currentBronzeMedals = firstEntry.getBronzeMedals();
        }

        while (scoreboardIterator.hasNext()) {
          // Iterate over all unranked scoreboard entries
          final UnrankedScoreboardEntry scoreboardEntry = scoreboardIterator.next();

          // Get the current score and medal count
          final Long entryScore = scoreboardEntry.getScore();
          final Long entryGoldMedals = scoreboardEntry.getGoldMedals();
          final Long entrySilverMedals = scoreboardEntry.getSilverMedals();
          final Long entryBronzeMedals = scoreboardEntry.getBronzeMedals();

          // Check if the current score is below zero for the first time
          if (!negativeScoresFound && (entryScore < 0)) {
            negativeScoresFound = true;
            if (currentScore == 0 && currentGoldMedals == 0 && currentSilverMedals == 0 && currentBronzeMedals == 0) {
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
            currentRank += tuple.getT2().size() - unrankedScoreboard.size();
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
          ScoreboardEntryBuilder scoreboardEntryBuilder = ScoreboardEntry.builder();
          scoreboardEntryBuilder.baseScore(scoreboardEntry.getBaseScore());
          scoreboardEntryBuilder.bonusScore(scoreboardEntry.getBonusScore());
          scoreboardEntryBuilder.score(entryScore);

          scoreboardEntryBuilder.goldMedals(entryGoldMedals);
          scoreboardEntryBuilder.silverMedals(entrySilverMedals);
          scoreboardEntryBuilder.bronzeMedals(entryBronzeMedals);

          scoreboardEntryBuilder.rank(entryRank);

          String principalId;
          PrincipalType principalType;

          if (scoreboardEntry.getTeam() != null) {
            principalId = scoreboardEntry.getTeam().getId();
            principalType = PrincipalType.TEAM;
            scoreboardEntryBuilder.displayName(scoreboardEntry.getTeam().getDisplayName());
          } else {
            principalId = scoreboardEntry.getUser().getId();
            principalType = PrincipalType.USER;
            scoreboardEntryBuilder.displayName(scoreboardEntry.getUser().getDisplayName());
          }
          scoreboardEntryBuilder.principalId(principalId);
          scoreboardEntryBuilder.principalType(principalType);

          // Remove the current scoreboard user from the list of zero score users and teams
          principals =
            principals.filter(principal ->
              !principal.getId().equals(principalId) || !principal.getPrincipalType().equals(principalType)
            );

          // Add the current scoreboard entry to the scoreboard
          scoreboard.add(scoreboardEntryBuilder.build());

          // Advance the current rank value
          currentRank++;

          // Update the previous state
          currentScore = entryScore;
          currentGoldMedals = entryGoldMedals;
          currentSilverMedals = entrySilverMedals;
          currentBronzeMedals = entryBronzeMedals;
        }

        // Were there users with negative scores? (This can happen due to a negative score
        // adjustment)
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

        // Create a stream of principals that will be inserted in the scoreboard
        final Stream<ScoreboardEntry> zeroScoreboardPrincipals = principals.map(principal -> {
          zeroScoreboardEntryBuilder.displayName(principal.getDisplayName());
          zeroScoreboardEntryBuilder.principalId(principal.getId());
          zeroScoreboardEntryBuilder.principalType(principal.getPrincipalType());
          return zeroScoreboardEntryBuilder.build();
        });

        // All scoreboard entries before the zero score principals
        final Stream<ScoreboardEntry> scoreboardAboveZeroStream = scoreboard.stream().limit(zeroScorePosition - 1);

        // All scoreboard entries after the zero score principals
        final Stream<ScoreboardEntry> scoreboardBelowZeroStream = scoreboard.stream().skip(zeroScorePosition - 1);

        // Concatenate the scoreboard and create an array list
        return Stream
          .concat(Stream.concat(scoreboardAboveZeroStream, zeroScoreboardPrincipals), scoreboardBelowZeroStream)
          .collect(Collectors.toCollection(ArrayList::new));
      })
      // Clear the scoreboard and save all scoreboard entries
      .flatMapMany(scoreboard -> scoreboardRepository.deleteAll().thenMany(scoreboardRepository.saveAll(scoreboard)))
      .then();
  }

  public Mono<Void> refreshSubmissionRanks() {
    return submissionRepository.refreshSubmissionRanks().then();
  }

  public Mono<Void> refreshModuleLists() {
    return userRepository.computeModuleLists().then();
  }

  /**
   * Propagate updated user information to relevant parts of the db
   *
   * @param userId the updated user id
   * @return a Mono<Void> signaling completion
   */
  public Mono<Void> afterUserUpdate(@ValidUserId final String userId) {
    Mono<UserEntity> updatedUser = userService.getById(userId);

    // First, update all teams related to the user

    // The team (if any) the user belongs to now
    Mono<TeamEntity> incomingTeam = updatedUser
      .filter(user -> user.getTeamId() != null)
      .map(UserEntity::getTeamId)
      .flatMap(userService::getTeamById)
      .zipWith(updatedUser)
      .map(tuple ->
        tuple
          .getT1()
          // Update the correct member entry in the team's member list
          .withMembers(
            tuple
              .getT1()
              .getMembers()
              .stream()
              .map(user -> {
                if (user.getId().equals(userId)) {
                  // Found the correct user id, replace this with the new user
                  // entity
                  return tuple.getT2();
                } else {
                  return user;
                }
              })
              // Collect all entries into an array list
              .collect(Collectors.toCollection(ArrayList::new))
          )
      )
      // Save the team to the db
      .flatMap(teamRepository::save);

    // The team (if any) the user belonged to before. This number can be greater than one
    final Flux<TeamEntity> outgoingTeams = userService
      .findAllTeams()
      .filter(team ->
        // Look through all teams and find the one that lists the updated user as member
        !team.getMembers().stream().filter(user -> user.getId().equals(userId)).findAny().isEmpty()
      )
      .zipWith(updatedUser.cache().repeat())
      // If the new team and old team are the same, don't remove the old team
      .filter(tuple -> {
        if (tuple.getT2().getTeamId() != null) {
          return !tuple.getT2().getTeamId().equals(tuple.getT1().getId());
        } else {
          return true;
        }
      })
      .flatMap(tuple -> {
        if (tuple.getT1().getMembers().size() == 1) {
          // The last user of the team was removed, therefore delete the entire team
          log.info("Deleting team with id " + tuple.getT1().getId() + " because last user left");
          return userService
            .deleteTeam(tuple.getT1().getId())
            .then(afterTeamDeletion(tuple.getT1().getId()))
            // Return an empty tuple
            .then(Mono.empty());
        } else {
          // Team not empty, do nothing here
          return Mono.just(tuple);
        }
      })
      .map(tuple ->
        tuple
          .getT1()
          // Update the members list to only contain remaining users
          .withMembers(
            tuple
              .getT1()
              .getMembers()
              .stream()
              .filter(user -> !user.getId().equals(userId))
              .collect(Collectors.toCollection(ArrayList::new))
          )
      );

    // Update submissions
    Flux<Submission> submissionsToUpdate = submissionRepository.findAllByUserId(userId);

    // Update team field in submissions
    Flux<Submission> addedTeamSubmissions = incomingTeam
      .flatMapMany(team -> submissionsToUpdate.map(submission -> submission.withTeamId(team.getId())))
      .switchIfEmpty(submissionsToUpdate);

    // Save all to db
    return teamRepository.saveAll(outgoingTeams).thenMany(submissionRepository.saveAll(addedTeamSubmissions)).then();
  }

  /**
   * Removes deleted teams from db. To be called after team deletion
   *
   * @param teamId
   * @return
   */
  public Mono<Void> afterTeamDeletion(@ValidTeamId final String teamId) {
    // Update submissions

    // Update team field in submissions
    final Flux<Submission> updatedTeamSubmissions = submissionRepository
      .findAllByTeamId(teamId)
      .map(submission -> submission.withTeamId(null));

    return submissionRepository.saveAll(updatedTeamSubmissions).then();
  }

  /**
   * To be called after user deletion
   *
   * @param userId
   * @return
   */
  public Mono<Void> afterUserDeletion(@ValidUserId final String userId) {
    // The team (if any) the user belonged to before
    final Flux<TeamEntity> outgoingTeams = userService
      .findAllTeams()
      .filter(team ->
        // Look through all teams and find the one that lists the updated user as member
        !team.getMembers().stream().filter(user -> user.getId().equals(userId)).findAny().isEmpty()
      )
      .flatMap(team -> {
        if (team.getMembers().size() == 1) {
          // The last user of the team was removed, therefore delete the entire team
          log.info("Deleting team with id " + team.getId() + " because last user left");
          return userService
            .deleteTeam(team.getId())
            .flatMap(u -> afterTeamDeletion(team.getId()))
            // Return an empty tuple
            .then(Mono.empty());
        } else {
          // Team not empty, do nothing here
          return Mono.just(team);
        }
      })
      .map(team ->
        team.withMembers(
          // Update the members list to only contain remaining users
          team
            .getMembers()
            .stream()
            .filter(user -> !user.getId().equals(userId))
            .collect(Collectors.toCollection(ArrayList::new))
        )
      );

    return teamRepository.saveAll(outgoingTeams).then();
  }
}
