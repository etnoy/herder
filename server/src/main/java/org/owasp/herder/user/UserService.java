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

import org.owasp.herder.authentication.AuthResponse;
import org.owasp.herder.authentication.AuthResponse.AuthResponseBuilder;
import org.owasp.herder.authentication.PasswordAuth;
import org.owasp.herder.authentication.PasswordAuth.PasswordAuthBuilder;
import org.owasp.herder.authentication.PasswordAuthRepository;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.exception.ClassIdNotFoundException;
import org.owasp.herder.exception.DuplicateUserDisplayNameException;
import org.owasp.herder.exception.DuplicateUserLoginNameException;
import org.owasp.herder.exception.InvalidClassIdException;
import org.owasp.herder.exception.InvalidUserIdException;
import org.owasp.herder.exception.UserIdNotFoundException;
import org.owasp.herder.service.ClassService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public final class UserService {

  private final UserRepository userRepository;

  private final PasswordAuthRepository passwordAuthRepository;

  private final ClassService classService;

  private final KeyService keyService;

  private final WebTokenKeyManager webTokenKeyManager;

  private Clock clock;

  public UserService(
      UserRepository userRepository,
      PasswordAuthRepository passwordAuthRepository,
      ClassService classService,
      KeyService keyService,
      WebTokenKeyManager webTokenKeyManager) {
    this.userRepository = userRepository;
    this.passwordAuthRepository = passwordAuthRepository;
    this.classService = classService;
    this.keyService = keyService;
    this.webTokenKeyManager = webTokenKeyManager;
    resetClock();
  }

  public Mono<AuthResponse> authenticate(final String loginName, final String password) {
    if ((loginName == null) || (password == null)) {
      return Mono.error(new NullPointerException());
    }
    if (loginName.isEmpty()) {
      return Mono.error(new IllegalArgumentException());
    }
    if (password.isEmpty()) {
      return Mono.error(new IllegalArgumentException());
    }
    // Initialize the encoder
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(16);

    final Mono<PasswordAuth> passwordAuthMono = // Find the password auth
        findPasswordAuthByLoginName(loginName)
            .filter(passwordAuth -> encoder.matches(password, passwordAuth.getHashedPassword()))
            .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid username or password")));

    final Mono<UserEntity> userMono =
        passwordAuthMono.map(PasswordAuth::getUserId).flatMap(this::findById);

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

  public Mono<Long> count() {
    return userRepository.count();
  }

  public Mono<String> create(final String displayName) {
    if (displayName == null) {
      return Mono.error(new NullPointerException());
    }

    if (displayName.isEmpty()) {
      return Mono.error(new IllegalArgumentException());
    }

    log.info("Creating new user with display name " + displayName);

    return Mono.just(displayName)
        .filterWhen(this::doesNotExistByDisplayName)
        .switchIfEmpty(displayNameAlreadyExists(displayName))
        .flatMap(
            name ->
                userRepository.save(
                    UserEntity.builder()
                        .displayName(name)
                        .key(keyService.generateRandomBytes(16))
                        .accountCreated(LocalDateTime.now())
                        .build()))
        .map(UserEntity::getId);
  }

  public Mono<String> createPasswordUser(
      final String displayName, final String loginName, final String passwordHash) {
    if (displayName == null) {
      return Mono.error(new NullPointerException("Display name cannot be null"));
    }

    if (loginName == null) {
      return Mono.error(new NullPointerException("Login name cannot be null"));
    }

    if (passwordHash == null) {
      return Mono.error(new NullPointerException("Password hash cannot be null"));
    }

    if (displayName.isEmpty() || loginName.isEmpty() || passwordHash.isEmpty()) {
      return Mono.error(new IllegalArgumentException());
    }

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
            .switchIfEmpty(displayNameAlreadyExists(displayName));

    return Mono.zip(displayNameMono, loginNameMono)
        .flatMap(
            tuple -> {
              final UserEntity newUser =
                  UserEntity.builder()
                      .displayName(tuple.getT1())
                      .key(keyService.generateRandomBytes(16))
                      .isEnabled(true)
                      .accountCreated(LocalDateTime.now())
                      .build();

              final Mono<String> userIdMono = userRepository.save(newUser).map(UserEntity::getId);

              final PasswordAuthBuilder passwordAuthBuilder = PasswordAuth.builder();
              passwordAuthBuilder.loginName(tuple.getT2());
              passwordAuthBuilder.hashedPassword(passwordHash);

              return userIdMono.delayUntil(
                  userId ->
                      passwordAuthRepository.save(passwordAuthBuilder.userId(userId).build()));
            });
  }

  public Mono<Void> deleteById(final String userId) {
    if (userId == null) {
      return Mono.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Mono.error(new InvalidUserIdException());
    }
    return passwordAuthRepository
        .deleteByUserId(userId)
        .then(userRepository.deleteById(userId))
        .doOnSuccess(u -> kick(userId));
  }

  public Mono<Void> demote(final String userId) {
    if (userId == null) {
      return Mono.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Mono.error(new InvalidUserIdException());
    }
    log.info("Demoting user with id " + userId + " to user");

    return findById(userId)
        .map(user -> user.withAdmin(false))
        .flatMap(userRepository::save)
        .doOnSuccess(u -> kick(userId))
        .then();
  }

  public Mono<Void> disable(final String userId) {
    log.info("Disabling user with id " + userId);

    return findById(userId)
        .map(user -> user.withEnabled(false))
        .flatMap(userRepository::save)
        .doOnSuccess(u -> kick(userId))
        .then();
  }

  private Mono<String> displayNameAlreadyExists(final String displayName) {
    return Mono.error(
        new DuplicateUserDisplayNameException("Display name " + displayName + " already exists"));
  }

  public Mono<Boolean> existsByDisplayName(final String displayName) {
    return userRepository.findByDisplayName(displayName).map(u -> true).defaultIfEmpty(false);
  }

  public Mono<Boolean> existsByLoginName(final String loginName) {
    return passwordAuthRepository.findByLoginName(loginName).map(u -> true).defaultIfEmpty(false);
  }

  private Mono<Boolean> doesNotExistByDisplayName(final String displayName) {
    return userRepository.findByDisplayName(displayName).map(u -> false).defaultIfEmpty(true);
  }

  private Mono<Boolean> doesNotExistByLoginName(final String loginName) {
    return passwordAuthRepository.findByLoginName(loginName).map(u -> false).defaultIfEmpty(true);
  }

  public Mono<Void> enable(final String userId) {
    log.info("Enabling user with id " + userId);

    return findById(userId)
        .map(user -> user.withEnabled(true))
        .flatMap(userRepository::save)
        .doOnSuccess(u -> kick(userId))
        .then();
  }

  public Flux<UserEntity> findAll() {
    return userRepository.findAll();
  }

  public Mono<UserEntity> findById(final String userId) {
    if (userId == null) {
      return Mono.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Mono.error(new InvalidUserIdException());
    }
    return userRepository.findById(userId);
  }

  public Mono<String> findDisplayNameById(final String userId) {
    if (userId == null) {
      return Mono.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Mono.error(new InvalidUserIdException());
    }
    return userRepository.findById(userId).map(UserEntity::getDisplayName);
  }

  public Mono<byte[]> findKeyById(final String userId) {
    if (userId == null) {
      return Mono.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Mono.error(new InvalidUserIdException());
    }
    return Mono.just(userId)
        .filterWhen(userRepository::existsById)
        .switchIfEmpty(Mono.error(new UserIdNotFoundException()))
        .flatMap(userRepository::findById)
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

  public Mono<PasswordAuth> findPasswordAuthByLoginName(final String loginName) {
    if (loginName == null) {
      return Mono.error(new NullPointerException());
    }
    if (loginName.isEmpty()) {
      return Mono.error(new IllegalArgumentException());
    }
    return passwordAuthRepository.findByLoginName(loginName);
  }

  public Mono<PasswordAuth> findPasswordAuthByUserId(final String userId) {
    if (userId == null) {
      return Mono.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Mono.error(new InvalidUserIdException());
    }
    return passwordAuthRepository.findByUserId(userId);
  }

  public Mono<String> findUserIdByLoginName(final String loginName) {
    if (loginName == null) {
      return Mono.error(new NullPointerException());
    }
    if (loginName.isEmpty()) {
      return Mono.error(new IllegalArgumentException());
    }

    return passwordAuthRepository.findByLoginName(loginName).map(PasswordAuth::getUserId);
  }

  public void kick(final String userId) {

    log.info("Revoking all tokens for user with id " + userId);

    webTokenKeyManager.invalidateAccessToken(userId);
  }

  private Mono<String> loginNameAlreadyExists(final String loginName) {
    return Mono.error(
        new DuplicateUserLoginNameException("Login name " + loginName + " already exists"));
  }

  public Mono<Void> promote(final String userId) {
    if (userId == null) {
      return Mono.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Mono.error(new InvalidUserIdException());
    }

    log.info("Promoting user with id " + userId + " to admin");

    return findById(userId)
        .map(user -> user.withAdmin(true))
        .flatMap(userRepository::save)
        .doOnSuccess(u -> kick(userId))
        .then();
  }

  public void resetClock() {
    this.clock = Clock.systemDefaultZone();
  }

  public Mono<UserEntity> setClassId(final String userId, final String classId) {
    if (userId == null) {
      return Mono.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Mono.error(new InvalidUserIdException());
    }
    if (classId == null) {
      return Mono.error(new NullPointerException());
    }
    if (classId.isEmpty()) {
      return Mono.error(new InvalidClassIdException());
    }
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

  public void setClock(Clock clock) {
    this.clock = clock;
  }

  public Mono<UserEntity> setDisplayName(final String userId, final String displayName) {
    if (userId == null) {
      return Mono.error(new NullPointerException());
    }
    if (userId.isEmpty()) {
      return Mono.error(new InvalidUserIdException());
    }
    if (displayName == null) {
      return Mono.error(new NullPointerException());
    }
    if (displayName.isEmpty()) {
      return Mono.error(new IllegalArgumentException());
    }

    log.info("Setting display name of user id " + userId + " to " + displayName);

    final Mono<String> displayNameMono =
        Mono.just(displayName)
            .filterWhen(this::doesNotExistByDisplayName)
            .switchIfEmpty(displayNameAlreadyExists(displayName));

    return Mono.just(userId)
        .filterWhen(userRepository::existsById)
        .switchIfEmpty(Mono.error(new UserIdNotFoundException()))
        .flatMap(this::findById)
        .zipWith(displayNameMono)
        .map(tuple -> tuple.getT1().withDisplayName(tuple.getT2()))
        .flatMap(userRepository::save);
  }

  public Mono<Void> suspendUntil(final String userId, final Duration duration) {
    return suspendUntil(userId, LocalDateTime.now().plus(duration), null);
  }

  public Mono<Void> suspendUntil(
      final String userId, final Duration duration, final String suspensionMessage) {
    return suspendUntil(userId, LocalDateTime.now().plus(duration), suspensionMessage);
  }

  public Mono<Void> suspendUntil(final String userId, final LocalDateTime suspensionDate) {
    return suspendUntil(userId, suspensionDate, null);
  }

  public Mono<Void> suspendUntil(
      final String userId, final LocalDateTime suspensionDate, final String suspensionMessage) {
    if (suspensionDate.isBefore(LocalDateTime.now(clock))) {
      return Mono.error(new IllegalArgumentException("Suspension date must be in the future"));
    }

    log.info("Suspending user with id " + userId + " until " + suspensionDate.toString());

    return findById(userId)
        .map(
            user ->
                user.withSuspendedUntil(suspensionDate).withSuspensionMessage(suspensionMessage))
        .flatMap(userRepository::save)
        .doOnSuccess(u -> kick(userId))
        .then();
  }
}
