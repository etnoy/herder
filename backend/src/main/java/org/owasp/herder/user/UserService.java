/*
 * Copyright 2018-2022 Jonathan Jogenfors, jonathan@jogenfors.se
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
import org.owasp.herder.user.SolverEntity.SolverEntityBuilder;
import org.owasp.herder.validation.ValidClassId;
import org.owasp.herder.validation.ValidDisplayName;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Class that manages user entities and interacts with the user repository */
@Slf4j
@Service
@Validated
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;

  private final TeamRepository teamRepository;

  private final PasswordAuthRepository passwordAuthRepository;

  private final ClassService classService;

  private final KeyService keyService;

  private final WebTokenKeyManager webTokenKeyManager;

  private final Clock clock;

  public Mono<Void> addUserToTeam(
      @ValidUserId final String userId, @ValidTeamId final String teamId) {
    log.debug("Adding user with id " + userId + " to team with id " + teamId);
    final Mono<UserEntity> userMono =
        getById(userId)
            .filter(user -> user.getTeamId() == null)
            .switchIfEmpty(Mono.error(new IllegalStateException("User already belongs to a team")));

    return Mono.zip(userMono, getTeamById(teamId))
        .flatMap(
            tuple -> {
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
  public Mono<AuthResponse> authenticate(
      @ValidLoginName final String loginName, @ValidPassword final String password) {
    // Initialize the encoder
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(16);

    final Mono<PasswordAuth> passwordAuthMono = // Find the password auth
        findPasswordAuthByLoginName(loginName)
            .filter(passwordAuth -> encoder.matches(password, passwordAuth.getHashedPassword()))
            .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid username or password")));

    final Mono<UserEntity> userMono =
        passwordAuthMono.map(PasswordAuth::getUserId).flatMap(this::getById);

    final AuthResponseBuilder authResponseBuilder = AuthResponse.builder();

    return Mono.zip(passwordAuthMono, userMono)
        .map(
            tuple -> {
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
                    String.format(
                        "Account suspended until %s",
                        tuple.getT2().getSuspendedUntil().format(formatter)));
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
    return findById(userId)
        .switchIfEmpty(Mono.error(new UserNotFoundException()))
        .map(user -> user.withTeamId(null))
        .flatMap(userRepository::save);
  }

  /**
   * Count the number of users in the database
   *
   * @return number of users
   */
  public Mono<Long> count() {
    return userRepository.count();
  }

  /**
   * Creates a new user
   *
   * @param displayName The display name of the user
   * @return The created user id
   */
  public Mono<String> create(@ValidDisplayName final String displayName) {
    log.info("Creating new user with display name " + displayName);

    final UserEntity userEntity =
        UserEntity.builder()
            .displayName(displayName)
            .key(keyService.generateRandomBytes(16))
            .creationTime(LocalDateTime.now(clock))
            .build();

    return Mono.just(displayName)
        .filterWhen(this::doesNotExistByDisplayName)
        .switchIfEmpty(
            Mono.error(
                new DuplicateUserDisplayNameException(
                    "Display name " + displayName + " already exists")))
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
      final String passwordHash) {
    log.info(
        "Creating new password login user with display name "
            + displayName
            + " and login name "
            + loginName);

    final Mono<String> loginNameMono =
        Mono.just(loginName)
            .filterWhen(this::doesNotExistByLoginName)
            .switchIfEmpty(loginNameAlreadyExists(loginName));

    final Mono<String> displayNameMono =
        Mono.just(displayName)
            .filterWhen(this::doesNotExistByDisplayName)
            .switchIfEmpty(
                Mono.error(
                    new DuplicateUserDisplayNameException(
                        "Display name " + displayName + " already exists")));

    return Mono.zip(displayNameMono, loginNameMono)
        .flatMap(
            tuple -> {
              final UserEntity userEntity =
                  UserEntity.builder()
                      .displayName(tuple.getT1())
                      .key(keyService.generateRandomBytes(16))
                      .isEnabled(true)
                      .creationTime(LocalDateTime.now(clock))
                      .build();

              final Mono<String> userIdMono =
                  userRepository.save(userEntity).map(UserEntity::getId);

              final PasswordAuthBuilder passwordAuthBuilder = PasswordAuth.builder();
              passwordAuthBuilder.loginName(tuple.getT2());
              passwordAuthBuilder.hashedPassword(passwordHash);

              return userIdMono.delayUntil(
                  userId ->
                      passwordAuthRepository.save(passwordAuthBuilder.userId(userId).build()));
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

    return Mono.just(displayName)
        .filterWhen(this::teamDoesNotExistByDisplayName)
        .switchIfEmpty(
            Mono.error(
                new DuplicateTeamDisplayNameException(
                    "Team display name " + displayName + " already exists")))
        .flatMap(
            name ->
                teamRepository.save(
                    TeamEntity.builder()
                        .displayName(name)
                        .creationTime(LocalDateTime.now(clock))
                        .members(new ArrayList<>())
                        .build()))
        .map(TeamEntity::getId);
  }

  /**
   * Deletes a given user
   *
   * @param userId
   * @return
   */
  public Mono<Void> deleteById(@ValidUserId final String userId) {
    return passwordAuthRepository
        .deleteByUserId(userId)
        .then(userRepository.deleteById(userId))
        .doOnSuccess(u -> kick(userId));
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
        .map(
            user ->
                user.withTeamId(null)
                    .withEnabled(false)
                    .withDeleted(true)
                    .withDisplayName("")
                    .withSuspensionMessage("")
                    .withAdmin(false))
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

    return teamRepository.deleteById(teamId).then();
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
   * Find all users and teams
   *
   * @return
   */
  public Flux<SolverEntity> findAllWithTeams() {
    Flux<UserEntity> userFlux = userRepository.findAllByTeamId(null);

    final Flux<SolverEntity> teamFlux =
        teamRepository
            .findAll()
            .flatMap(
                team -> {
                  final String teamId = team.getId();
                  SolverEntityBuilder solverEntityBuilder = SolverEntity.builder();

                  solverEntityBuilder.principalType(PrincipalType.TEAM);
                  solverEntityBuilder.principalId(teamId);
                  solverEntityBuilder.displayName(team.getDisplayName());
                  solverEntityBuilder.creationTime(team.getCreationTime());

                  return userRepository
                      .findAllByTeamId(teamId)
                      .collectList()
                      .map(HashSet<UserEntity>::new)
                      .map(solverEntityBuilder::members)
                      .map(SolverEntityBuilder::build);
                });

    return Flux.concat(
        teamFlux,
        userFlux.map(
            user -> {
              final String userId = user.getId();
              SolverEntityBuilder solverEntityBuilder = SolverEntity.builder();

              solverEntityBuilder.principalType(PrincipalType.USER);
              solverEntityBuilder.principalId(userId);
              solverEntityBuilder.displayName(user.getDisplayName());
              solverEntityBuilder.creationTime(user.getCreationTime());

              return solverEntityBuilder.build();
            }));
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
        .flatMap(
            user -> {
              final byte[] key = user.getKey();
              if (key == null) {
                return userRepository
                    .save(user.withKey(keyService.generateRandomBytes(16)))
                    .map(UserEntity::getKey);
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
        .switchIfEmpty(Mono.error(new UserNotFoundException("User id " + userId + " not found")));
  }

  public Mono<TeamEntity> getTeamByUserId(@ValidUserId final String userId) {
    return getById(userId)
        .flatMap(
            user -> {
              final String teamId = user.getTeamId();
              if (teamId == null) {
                return Mono.empty();
              } else {
                return getTeamById(teamId);
              }
            });
  }

  public Mono<TeamEntity> getTeamById(@ValidTeamId final String teamId) {
    return teamRepository
        .findById(teamId)
        .switchIfEmpty(Mono.error(new TeamNotFoundException("Team id " + teamId + " not found")));
  }

  public void kick(@ValidUserId final String userId) {
    log.info("Revoking all tokens for user with id " + userId);

    webTokenKeyManager.invalidateAccessToken(userId);
  }

  private Mono<String> loginNameAlreadyExists(@ValidLoginName final String loginName) {
    return Mono.error(
        new DuplicateUserLoginNameException("Login name " + loginName + " already exists"));
  }

  public Mono<Void> promote(@ValidUserId final String userId) {
    log.info("Promoting user with id " + userId + " to admin");

    return findById(userId)
        .map(user -> user.withAdmin(true))
        .flatMap(userRepository::save)
        .doOnSuccess(u -> kick(userId))
        .then();
  }

  public Mono<UserEntity> setClassId(
      @ValidUserId final String userId, @ValidClassId final String classId) {
    final Mono<String> classIdMono =
        Mono.just(classId)
            .filterWhen(classService::existsById)
            .switchIfEmpty(Mono.error(new ClassIdNotFoundException()));

    return Mono.just(userId)
        .flatMap(this::findById)
        .zipWith(classIdMono)
        .map(tuple -> tuple.getT1().withClassId(tuple.getT2()))
        .flatMap(userRepository::save)
        .doOnSuccess(u -> kick(userId));
  }

  public Mono<UserEntity> setDisplayName(
      @ValidUserId final String userId, @ValidDisplayName final String displayName) {
    log.info("Setting display name of user id " + userId + " to " + displayName);

    final Mono<String> displayNameMono =
        Mono.just(displayName)
            .filterWhen(this::doesNotExistByDisplayName)
            .switchIfEmpty(
                Mono.error(
                    new DuplicateUserDisplayNameException(
                        "Display name " + displayName + " already exists")));

    return getById(userId)
        .zipWith(displayNameMono)
        .map(tuple -> tuple.getT1().withDisplayName(tuple.getT2()))
        .flatMap(userRepository::save);
  }

  // TODO: validate durations etc. for these functions
  public Mono<Void> suspendUntil(@ValidUserId final String userId, final Duration duration) {
    return suspendUntil(userId, LocalDateTime.now(clock).plus(duration), null);
  }

  public Mono<Void> suspendUntil(
      @ValidUserId final String userId, final Duration duration, final String suspensionMessage) {
    return suspendUntil(userId, LocalDateTime.now(clock).plus(duration), suspensionMessage);
  }

  public Mono<Void> suspendUntil(
      @ValidUserId final String userId, final LocalDateTime suspensionDate) {
    return suspendUntil(userId, suspensionDate, null);
  }

  public Mono<Void> suspendUntil(
      @ValidUserId final String userId,
      final LocalDateTime suspensionDate,
      final String suspensionMessage) {
    if (suspensionDate.isBefore(LocalDateTime.now(clock))) {
      return Mono.error(new IllegalArgumentException("Suspension date must be in the future"));
    }

    log.info("Suspending user with id " + userId + " until " + suspensionDate.toString());

    return getById(userId)
        .map(
            user ->
                user.withSuspendedUntil(suspensionDate).withSuspensionMessage(suspensionMessage))
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
}
