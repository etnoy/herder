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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.exception.DuplicateTeamDisplayNameException;
import org.owasp.herder.exception.TeamNotFoundException;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.validation.ValidDisplayName;
import org.owasp.herder.validation.ValidTeamId;
import org.owasp.herder.validation.ValidUserId;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class TeamService {

  private static final String DISPLAY_NAME_ALREADY_EXISTS = "Team display name \"%s\" already exists";

  private final TeamRepository teamRepository;

  private final SubmissionRepository submissionRepository;

  private final Clock clock;

  public Mono<Void> addMember(@ValidTeamId final String teamId, final UserEntity newMember) {
    if (!newMember.getTeamId().equals(teamId)) {
      return Mono.error(new IllegalArgumentException("User has an incorrect team id"));
    }
    return getById(teamId)
      .flatMap(team -> {
        final ArrayList<UserEntity> members = team.getMembers();
        members.add(newMember);
        return teamRepository.save(team.withMembers(members));
      })
      .then();
  }

  public Mono<Void> expel(@ValidTeamId final String teamId, @ValidUserId final String expelledUserId) {
    return getById(teamId)
      .flatMap(team -> {
        final long matchingUserCount = team
          .getMembers()
          .stream()
          .filter(member -> member.getId().equals(expelledUserId))
          .count();
        if (matchingUserCount == 0) {
          return Mono.error(new IllegalStateException("User not found in team"));
        } else if (matchingUserCount > 1) {
          return Mono.error(new IllegalStateException("Team contains the same user more than once"));
        }
        return Mono.just(team);
      })
      .map(team -> {
        final ArrayList<UserEntity> newMembers = team
          .getMembers()
          .stream()
          .filter(member -> !member.getId().equals(expelledUserId))
          .collect(Collectors.toCollection(ArrayList::new));
        return team.withMembers(newMembers);
      })
      .flatMap(teamRepository::save)
      .then();
  }

  /**
   * Creates a new team
   *
   * @param displayName The display name of the team
   * @return The created team id
   */
  public Mono<String> create(@ValidDisplayName final String displayName) {
    log.info("Creating new team with display name " + displayName);

    return Mono
      .just(displayName)
      .filterWhen(this::doesNotExistByDisplayName)
      .switchIfEmpty(
        Mono.error(new DuplicateTeamDisplayNameException(String.format(DISPLAY_NAME_ALREADY_EXISTS, displayName)))
      )
      .flatMap(name ->
        teamRepository.save(
          TeamEntity
            .builder()
            .displayName(name)
            .creationTime(LocalDateTime.now(clock))
            .members(new ArrayList<>())
            .build()
        )
      )
      .map(TeamEntity::getId);
  }

  /**
   * Deletes a team
   *
   * @param teamId
   * @return
   */
  public Mono<Void> delete(@ValidTeamId final String teamId) {
    log.info("Deleting team with id " + teamId);

    return teamRepository.deleteById(teamId);
  }

  /**
   * Find all teams
   *
   * @return
   */
  public Flux<TeamEntity> findAll() {
    return teamRepository.findAll();
  }

  /**
   * Find a team by id
   *
   * @param teamId
   * @return The TeamEntity if it exists, or an empty mono if it doesn't
   */
  public Mono<TeamEntity> findById(@ValidTeamId final String teamId) {
    return teamRepository.findById(teamId);
  }

  public Mono<TeamEntity> getById(@ValidTeamId final String teamId) {
    return teamRepository
      .findById(teamId)
      .switchIfEmpty(Mono.error(new TeamNotFoundException("Team id \"" + teamId + "\" not found")));
  }

  public Mono<TeamEntity> getByMemberId(@ValidUserId final String userId) {
    return findAllByMemberId(userId)
      .collectList()
      .flatMap(teams -> {
        final int matchingTeamsCount = teams.size();
        if (matchingTeamsCount == 0) {
          return Mono.error(
            new IllegalStateException(String.format("User id \"%s\" is not a member of any team", userId))
          );
        } else if (matchingTeamsCount > 1) {
          return Mono.error(
            new IllegalStateException(String.format("User id \"%s\" is member of more than one team", userId))
          );
        }
        return Mono.just(teams.get(0));
      });
  }

  public Flux<TeamEntity> findAllByMemberId(@ValidUserId final String userId) {
    return teamRepository.findAllByMembersId(userId);
  }

  private Mono<Boolean> doesNotExistByDisplayName(@ValidDisplayName final String displayName) {
    return teamRepository.findByDisplayName(displayName).map(u -> false).defaultIfEmpty(true);
  }

  /**
   * Checks whether a given display name exists
   *
   * @param displayName
   * @return
   */
  public Mono<Boolean> existsByDisplayName(@ValidDisplayName final String displayName) {
    return teamRepository.findByDisplayName(displayName).map(u -> true).defaultIfEmpty(false);
  }

  /**
   * Checks whether a given display name exists
   *
   * @param displayName
   * @return
   */
  public Mono<Boolean> existsById(@ValidTeamId final String teamId) {
    return teamRepository.findById(teamId).map(u -> true).defaultIfEmpty(false);
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
   * To be called after a user entity has changed
   *
   * @param updatedUserId the updated user id
   * @return a Mono<Void> signaling completion
   */
  public Mono<Void> updateTeamMember(final UserEntity updatedUser) {
    final String currentTeamId = updatedUser.getTeamId();

    if (currentTeamId == null) {
      return Mono.empty();
    }

    // Remove the user from any previous teams it belonged to
    findAllByMemberId(updatedUser.getId());

    // Update the user entity in the team the user belongs to
    return getById(currentTeamId)
      .map(team ->
        team.withMembers(
          team
            .getMembers()
            .stream()
            .map(existingUser -> {
              if (existingUser.getId().equals(updatedUser.getId())) {
                // Found the correct user id, replace this with the new user entity
                return updatedUser;
              } else {
                return existingUser;
              }
            })
            // Collect all entries into an array list
            .collect(Collectors.toCollection(ArrayList::new))
        )
      )
      .flatMap(teamRepository::save)
      .then();
  }
}
