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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
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

  private final UserService userService;

  private final TeamRepository teamRepository;

  private final UserRepository userRepository;

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
