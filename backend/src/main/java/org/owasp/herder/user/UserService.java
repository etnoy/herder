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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.authentication.AuthResponse;
import org.owasp.herder.authentication.AuthResponse.AuthResponseBuilder;
import org.owasp.herder.authentication.PasswordAuth;
import org.owasp.herder.authentication.PasswordAuth.PasswordAuthBuilder;
import org.owasp.herder.authentication.PasswordAuthRepository;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.exception.ClassIdNotFoundException;
import org.owasp.herder.exception.DuplicateTeamDisplayNameException;
import org.owasp.herder.exception.DuplicateUserDisplayNameException;
import org.owasp.herder.exception.DuplicateUserLoginNameException;
import org.owasp.herder.exception.TeamNotFoundException;
import org.owasp.herder.exception.UserNotFoundException;
import org.owasp.herder.scoring.PrincipalType;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.user.PrincipalEntity.PrincipalEntityBuilder;
import org.owasp.herder.validation.ValidClassId;
import org.owasp.herder.validation.ValidDisplayName;
import org.owasp.herder.validation.ValidDuration;
import org.owasp.herder.validation.ValidLoginName;
import org.owasp.herder.validation.ValidPassword;
import org.owasp.herder.validation.ValidTeamId;
import org.owasp.herder.validation.ValidUserId;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Class that manages user entities and interacts with the user repository */
@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class UserService {

  private static final String DISPLAY_NAME_ALREADY_EXISTS = "Display name \"%s\" already exists";

  private static final String LOGIN_NAME_ALREADY_EXISTS = "Login name \"%s\" already exists";

  private static final String TEAM_DISPLAY_NAME_ALREADY_EXISTS = "Team display name \"%s\" already exists";

  private final UserRepository userRepository;

  private final TeamRepository teamRepository;

  private final PasswordAuthRepository passwordAuthRepository;

  private final SubmissionRepository submissionRepository;

  private final ClassService classService;

  private final KeyService keyService;

  private final WebTokenKeyManager webTokenKeyManager;

  private final Clock clock;

  public Mono<Void> addUserToTeam(@ValidUserId final String userId, @ValidTeamId final String teamId) {
    log.debug("Adding user with id " + userId + " to team with id " + teamId);
    final Mono<UserEntity> userMono = getById(userId)
      .filter(user -> user.getTeamId() == null)
      .switchIfEmpty(Mono.error(new IllegalStateException("User already belongs to a team")));

    return Mono
      .zip(userMono, getTeamById(teamId))
      .flatMap(tuple -> {
        final UserEntity user = tuple.getT1().withTeamId(teamId);
        TeamEntity team = tuple.getT2();
        ArrayList<UserEntity> members = team.getMembers();
        members.add(user);
        team.withMembers(members);
        return userRepository.save(user).then(teamRepository.save(team));
      })
      .then();
  }

  /**
   * Verifies if the given usernames and password is valid
   *
   * @param loginName the supplied username used to log in
   * @param password the supplied password
   * @return an AuthResponse Mono that shows whether login was successful or not
   */
  public Mono<AuthResponse> authenticate(@ValidLoginName final String loginName, @ValidPassword final String password) {
    // Initialize the encoder
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(16);

    final Mono<PasswordAuth> passwordAuthMono = findPasswordAuthByLoginName( // Find the password auth
      loginName
    )
      .filter(passwordAuth -> encoder.matches(password, passwordAuth.getHashedPassword()))
      .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid username or password")));

    final Mono<UserEntity> userMono = passwordAuthMono.map(PasswordAuth::getUserId).flatMap(this::getById);

    final AuthResponseBuilder authResponseBuilder = AuthResponse.builder();

    return Mono
      .zip(passwordAuthMono, userMono)
      .map(tuple -> {
        final LocalDateTime suspendedUntil = tuple.getT2().getSuspendedUntil();
        boolean isSuspended = true;
        if (suspendedUntil == null) {
          isSuspended = false;
        } else {
          isSuspended = tuple.getT2().getSuspendedUntil().isAfter(LocalDateTime.now(clock));
        }

        if (!tuple.getT2().isEnabled()) {
          // Account is not enabled
          throw new DisabledException("Account disabled");
        } else if (isSuspended) {
          // Account is suspended until a given date
          final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

          throw new LockedException(
            String.format("Account suspended until %s", tuple.getT2().getSuspendedUntil().format(formatter))
          );
        } else {
          authResponseBuilder.displayName(tuple.getT2().getDisplayName());
          authResponseBuilder.userId(tuple.getT1().getUserId());
          authResponseBuilder.isAdmin(tuple.getT2().isAdmin());
        }
        return authResponseBuilder.build();
      });
  }

  /**
   * Removes the given user from its team. If the user id doesn't exist an exception will be
   * returned
   *
   * @param userId
   * @return the modified UserEntity Mono with team id set to null
   */
  public Mono<UserEntity> clearTeamForUser(@ValidUserId final String userId) {
    return getById(userId).map(user -> user.withTeamId(null)).flatMap(userRepository::save);
  }

  /**
   * Creates a new user
   *
   * @param displayName The display name of the user
   * @return The created user id
   */
  public Mono<String> create(@ValidDisplayName final String displayName) {
    log.info("Creating new user with display name " + displayName);

    final UserEntity userEntity = UserEntity
      .builder()
      .displayName(displayName)
      .key(keyService.generateRandomBytes(16))
      .isEnabled(true)
      .creationTime(LocalDateTime.now(clock))
      .build();

    return Mono
      .just(displayName)
      .filterWhen(this::doesNotExistByDisplayName)
      .switchIfEmpty(
        Mono.error(new DuplicateUserDisplayNameException(String.format(DISPLAY_NAME_ALREADY_EXISTS, displayName)))
      )
      .flatMap(u -> userRepository.save(userEntity))
      .map(UserEntity::getId);
  }

  /**
   * Creates a new user that can log in with username and password
   *
   * @param displayName the display name for the user
   * @param loginName the login name for the user
   * @param passwordHash the password hash for the user
   * @return The created user id
   */
  public Mono<String> createPasswordUser(
    @ValidDisplayName final String displayName,
    @ValidLoginName final String loginName,
    final String passwordHash
  ) {
    log.info("Creating new password login user with display name " + displayName + " and login name " + loginName);

    final Mono<String> loginNameMono = Mono
      .just(loginName)
      .filterWhen(this::doesNotExistByLoginName)
      .switchIfEmpty(loginNameAlreadyExists(loginName));

    final Mono<String> displayNameMono = Mono
      .just(displayName)
      .filterWhen(this::doesNotExistByDisplayName)
      .switchIfEmpty(
        Mono.error(new DuplicateUserDisplayNameException(String.format(DISPLAY_NAME_ALREADY_EXISTS, displayName)))
      );

    return Mono
      .zip(displayNameMono, loginNameMono)
      .flatMap(tuple -> {
        final UserEntity userEntity = UserEntity
          .builder()
          .displayName(tuple.getT1())
          .key(keyService.generateRandomBytes(16))
          .isEnabled(true)
          .creationTime(LocalDateTime.now(clock))
          .build();

        final Mono<String> userIdMono = userRepository.save(userEntity).map(UserEntity::getId);

        final PasswordAuthBuilder passwordAuthBuilder = PasswordAuth.builder();
        passwordAuthBuilder.loginName(tuple.getT2());
        passwordAuthBuilder.hashedPassword(passwordHash);

        return userIdMono.delayUntil(userId -> passwordAuthRepository.save(passwordAuthBuilder.userId(userId).build()));
      });
  }

  /**
   * Creates a new team
   *
   * @param displayName The display name of the team
   * @return The created team id
   */
  public Mono<String> createTeam(@ValidDisplayName final String displayName) {
    log.info("Creating new team with display name " + displayName);

    return Mono
      .just(displayName)
      .filterWhen(this::teamDoesNotExistByDisplayName)
      .switchIfEmpty(
        Mono.error(new DuplicateTeamDisplayNameException(String.format(TEAM_DISPLAY_NAME_ALREADY_EXISTS, displayName)))
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
   * Deletes a user. In the database, the corresponding document is set to deleted, and all fields
   * are cleared
   *
   * @param userId
   * @return
   */
  public Mono<Void> delete(@ValidUserId final String userId) {
    log.info("Deleting user with id " + userId);

    kick(userId);

    return getById(userId)
      .map(user ->
        user
          .withTeamId(null)
          .withEnabled(false)
          .withDeleted(true)
          .withDisplayName("")
          .withSuspensionMessage(null)
          .withAdmin(false)
      )
      .flatMap(userRepository::save)
      .flatMap(u -> passwordAuthRepository.deleteByUserId(userId))
      .then();
  }

  /**
   * Deletes a team
   *
   * @param teamId
   * @return
   */
  public Mono<Void> deleteTeam(@ValidTeamId final String teamId) {
    log.info("Deleting team with id " + teamId);

    return teamRepository.deleteById(teamId);
  }

  /**
   * Removes admin access from user
   *
   * @param userId
   * @return
   */
  public Mono<Void> demote(@ValidUserId final String userId) {
    log.info("Demoting user with id " + userId + " to user");

    return findById(userId)
      .map(user -> user.withAdmin(false))
      .flatMap(userRepository::save)
      .doOnSuccess(u -> kick(userId))
      .then();
  }

  /**
   * Disables a user
   *
   * @param userId
   * @return
   */
  public Mono<Void> disable(@ValidUserId final String userId) {
    log.info("Disabling user with id " + userId);

    return getById(userId)
      .map(user -> user.withEnabled(false))
      .flatMap(userRepository::save)
      .doOnNext(u -> kick(userId))
      .then();
  }

  private Mono<Boolean> doesNotExistByDisplayName(@ValidDisplayName final String displayName) {
    return userRepository.findByDisplayName(displayName).map(u -> false).defaultIfEmpty(true);
  }

  private Mono<Boolean> doesNotExistByLoginName(@ValidLoginName final String loginName) {
    return passwordAuthRepository.findByLoginName(loginName).map(u -> false).defaultIfEmpty(true);
  }

  /**
   * Enables a specific user
   *
   * @param userId
   * @return
   */
  public Mono<Void> enable(@ValidUserId final String userId) {
    log.info("Enabling user with id " + userId);

    return findById(userId)
      .map(user -> user.withEnabled(true))
      .flatMap(userRepository::save)
      .doOnSuccess(u -> kick(userId))
      .then();
  }

  /**
   * Checks whether a given display name exists
   *
   * @param displayName
   * @return
   */
  public Mono<Boolean> existsByDisplayName(@ValidDisplayName final String displayName) {
    return userRepository.findByDisplayName(displayName).map(u -> true).defaultIfEmpty(false);
  }

  /**
   * Checks whether a given user id exists
   *
   * @param userId
   * @return
   */
  public Mono<Boolean> existsById(@ValidUserId final String userId) {
    return userRepository.findById(userId).map(u -> true).defaultIfEmpty(false);
  }

  /**
   * Checks whether a given login name exists
   *
   * @param loginName
   * @return
   */
  public Mono<Boolean> existsByLoginName(@ValidLoginName final String loginName) {
    return passwordAuthRepository.findByLoginName(loginName).map(u -> true).defaultIfEmpty(false);
  }

  /**
   * Find all teams
   *
   * @return
   */
  public Flux<TeamEntity> findAllTeams() {
    return teamRepository.findAll();
  }

  /**
   * Find all users (ignores teams)
   *
   * @return
   */
  public Flux<UserEntity> findAllUsers() {
    return userRepository.findAll();
  }

  /**
   * Find all that do not belong to a team
   *
   * @return
   */
  public Flux<UserEntity> findAllUsersWithoutTeams() {
    return userRepository.findAllByTeamId(null);
  }

  /**
   * Find all users and teams
   *
   * @return
   */
  public Flux<PrincipalEntity> findAllPrincipals() {
    Flux<UserEntity> userFlux = findAllUsersWithoutTeams();

    final Flux<PrincipalEntity> teamFlux = teamRepository
      .findAll()
      .flatMap(team -> {
        final String teamId = team.getId();
        PrincipalEntityBuilder principalEntityBuilder = PrincipalEntity.builder();

        principalEntityBuilder.principalType(PrincipalType.TEAM);
        principalEntityBuilder.id(teamId);
        principalEntityBuilder.displayName(team.getDisplayName());
        principalEntityBuilder.creationTime(team.getCreationTime());

        return userRepository
          .findAllByTeamId(teamId)
          .collectList()
          .map(HashSet<UserEntity>::new)
          .map(principalEntityBuilder::members)
          .map(PrincipalEntityBuilder::build);
      });

    return Flux.concat(
      teamFlux,
      userFlux.map(user -> {
        final String userId = user.getId();
        PrincipalEntityBuilder principalEntityBuilder = PrincipalEntity.builder();

        principalEntityBuilder.principalType(PrincipalType.USER);
        principalEntityBuilder.id(userId);
        principalEntityBuilder.displayName(user.getDisplayName());
        principalEntityBuilder.creationTime(user.getCreationTime());

        return principalEntityBuilder.build();
      })
    );
  }

  /**
   * Find a user by id
   *
   * @param userId
   * @return The UserEntity if it exists, or an empty mono if it doesn't
   */
  public Mono<UserEntity> findById(@ValidUserId final String userId) {
    return userRepository.findByIdAndIsDeletedFalse(userId);
  }

  /**
   * Finds the key for a given user id
   *
   * @param userId
   * @return The key if the user exists, otherwise UserNotFoundException
   */
  public Mono<byte[]> findKeyById(@ValidUserId final String userId) {
    return getById(userId)
      .flatMap(user -> {
        final byte[] key = user.getKey();
        if (key == null) {
          return userRepository.save(user.withKey(keyService.generateRandomBytes(16))).map(UserEntity::getKey);
        }
        return Mono.just(key);
      });
  }

  public Mono<PasswordAuth> findPasswordAuthByLoginName(@ValidLoginName final String loginName) {
    return passwordAuthRepository.findByLoginName(loginName);
  }

  public Mono<PasswordAuth> findPasswordAuthByUserId(@ValidUserId final String userId) {
    return passwordAuthRepository.findByUserId(userId);
  }

  /**
   * Find a team by id
   *
   * @param teamId
   * @return The TeamEntity if it exists, or an empty mono if it doesn't
   */
  public Mono<TeamEntity> findTeamById(@ValidTeamId final String teamId) {
    return teamRepository.findById(teamId);
  }

  public Mono<String> findUserIdByLoginName(@ValidLoginName final String loginName) {
    return passwordAuthRepository.findByLoginName(loginName).map(PasswordAuth::getUserId);
  }

  /**
   * Finds a user for a given user id. Throws exception if user id is not found
   *
   * @param userId
   * @return the UserEntity if found. Throws a mono exception if not found
   */
  public Mono<UserEntity> getById(@ValidUserId final String userId) {
    return findById(userId)
      .switchIfEmpty(Mono.error(new UserNotFoundException("User id \"" + userId + "\" not found")));
  }

  public Mono<TeamEntity> getTeamById(@ValidTeamId final String teamId) {
    return teamRepository
      .findById(teamId)
      .switchIfEmpty(Mono.error(new TeamNotFoundException("Team id \"" + teamId + "\" not found")));
  }

  public Mono<TeamEntity> getTeamByUserId(@ValidUserId final String userId) {
    return getById(userId)
      .flatMap(user -> {
        final String teamId = user.getTeamId();
        if (teamId == null) {
          return Mono.empty();
        } else {
          return getTeamById(teamId);
        }
      });
  }

  public void kick(@ValidUserId final String userId) {
    log.info("Revoking all tokens for user with id " + userId);

    webTokenKeyManager.invalidateAccessToken(userId);
  }

  private Mono<String> loginNameAlreadyExists(@ValidLoginName final String loginName) {
    return Mono.error(new DuplicateUserLoginNameException(String.format(LOGIN_NAME_ALREADY_EXISTS, loginName)));
  }

  public Mono<Void> promote(@ValidUserId final String userId) {
    log.info("Promoting user with id " + userId + " to admin");

    return findById(userId)
      .map(user -> user.withAdmin(true))
      .flatMap(userRepository::save)
      .doOnSuccess(u -> kick(userId))
      .then();
  }

  public Mono<UserEntity> setClassId(@ValidUserId final String userId, @ValidClassId final String classId) {
    final Mono<String> classIdMono = Mono
      .just(classId)
      .filterWhen(classService::existsById)
      .switchIfEmpty(Mono.error(new ClassIdNotFoundException()));

    return Mono
      .just(userId)
      .flatMap(this::findById)
      .zipWith(classIdMono)
      .map(tuple -> tuple.getT1().withClassId(tuple.getT2()))
      .flatMap(userRepository::save)
      .doOnSuccess(u -> kick(userId));
  }

  public Mono<UserEntity> setDisplayName(@ValidUserId final String userId, @ValidDisplayName final String displayName) {
    log.info("Setting display name of user id " + userId + " to " + displayName);

    final Mono<String> displayNameMono = Mono
      .just(displayName)
      .filterWhen(this::doesNotExistByDisplayName)
      .switchIfEmpty(
        Mono.error(new DuplicateUserDisplayNameException(String.format(DISPLAY_NAME_ALREADY_EXISTS, displayName)))
      );

    return getById(userId)
      .zipWith(displayNameMono)
      .map(tuple -> tuple.getT1().withDisplayName(tuple.getT2()))
      .flatMap(userRepository::save);
  }

  public Mono<Void> suspendForDuration(@ValidUserId final String userId, @ValidDuration final Duration duration) {
    return suspendUntil(userId, LocalDateTime.now(clock).plus(duration), null);
  }

  public Mono<Void> suspendForDuration(
    @ValidUserId final String userId,
    @ValidDuration final Duration duration,
    final String suspensionMessage
  ) {
    return suspendUntil(userId, LocalDateTime.now(clock).plus(duration), suspensionMessage);
  }

  public Mono<Void> suspendUntil(@ValidUserId final String userId, final LocalDateTime suspensionDate) {
    return suspendUntil(userId, suspensionDate, null);
  }

  public Mono<Void> suspendUntil(
    @ValidUserId final String userId,
    final LocalDateTime suspensionDate,
    final String suspensionMessage
  ) {
    if (suspensionDate.isBefore(LocalDateTime.now(clock))) {
      return Mono.error(new IllegalArgumentException("Suspension date must be in the future"));
    }

    log.info("Suspending user with id " + userId + " until " + suspensionDate.toString());

    return getById(userId)
      .map(user -> user.withSuspendedUntil(suspensionDate).withSuspensionMessage(suspensionMessage))
      .flatMap(userRepository::save)
      .doOnSuccess(u -> kick(userId))
      .then();
  }

  private Mono<Boolean> teamDoesNotExistByDisplayName(@ValidDisplayName final String displayName) {
    return teamRepository.findByDisplayName(displayName).map(u -> false).defaultIfEmpty(true);
  }

  /**
   * Checks whether a given display name exists
   *
   * @param displayName
   * @return
   */
  public Mono<Boolean> teamExistsByDisplayName(@ValidDisplayName final String displayName) {
    return teamRepository.findByDisplayName(displayName).map(u -> true).defaultIfEmpty(false);
  }

  /**
   * Checks whether a given display name exists
   *
   * @param displayName
   * @return
   */
  public Mono<Boolean> teamExistsById(@ValidTeamId final String teamId) {
    return teamRepository.findById(teamId).map(u -> true).defaultIfEmpty(false);
  }

  /**
   * Propagate updated user information to relevant parts of the db
   *
   * @param userId the updated user id
   * @return a Mono<Void> signaling completion
   */
  public Mono<Void> afterUserUpdate(@ValidUserId final String userId) {
    Mono<UserEntity> updatedUser = getById(userId);

    // First, update all teams related to the user

    // The team (if any) the user belongs to now
    Mono<TeamEntity> incomingTeam = updatedUser
      .filter(user -> user.getTeamId() != null)
      .map(UserEntity::getTeamId)
      .flatMap(this::getTeamById)
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
    final Flux<TeamEntity> outgoingTeams = findAllTeams()
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
          return deleteTeam(tuple.getT1().getId())
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
    final Flux<TeamEntity> outgoingTeams = findAllTeams()
      .filter(team ->
        // Look through all teams and find the one that lists the updated user as member
        !team.getMembers().stream().filter(user -> user.getId().equals(userId)).findAny().isEmpty()
      )
      .flatMap(team -> {
        if (team.getMembers().size() == 1) {
          // The last user of the team was removed, therefore delete the entire team
          log.info("Deleting team with id " + team.getId() + " because last user left");
          return deleteTeam(team.getId())
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
