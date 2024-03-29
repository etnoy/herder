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

  private final TeamRepository teamRepository;

  private final Clock clock;

  public Mono<Void> addMember(@ValidTeamId final String teamId, final UserEntity newMember) {
    if (newMember.getTeamId() == null || !newMember.getTeamId().equals(teamId)) {
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

        // Do some sanity checks on the number of matching users
        if (matchingUserCount == 0) {
          return Mono.error(new IllegalStateException(String.format("User \"%s\"  not found in team", expelledUserId)));
        } else if (matchingUserCount > 1) {
          // This is highly unlikely but we check for it anyway
          return Mono.error(
            new IllegalStateException(
              String.format(
                "Team with id \"%s\" contains the same user (id \"%s\") more than once",
                teamId,
                expelledUserId
              )
            )
          );
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
      .flatMap(team -> {
        if (team.getMembers().isEmpty()) {
          return teamRepository.deleteById(team.getId());
        } else {
          return teamRepository.save(team);
        }
      })
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
        Mono.error(
          new DuplicateTeamDisplayNameException(String.format("Team display name \"%s\" already exists", displayName))
        )
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
            new TeamNotFoundException(String.format("User id \"%s\" is not a member of any team", userId))
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
   * To be called after a user entity has changed
   *
   * @param updatedUserId the updated user id
   * @return a Mono<Void> signaling completion
   */
  public Mono<Void> updateTeamMember(final UserEntity updatedUser) {
    return getByMemberId(updatedUser.getId())
      .map(team ->
        team.withMembers(
          team
            .getMembers()
            .stream()
            .map(currentUser -> {
              if (currentUser.getId().equals(updatedUser.getId())) {
                // Found the correct user id, replace this with the new user entity
                return updatedUser;
              } else {
                return currentUser;
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
