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
package org.owasp.herder.test.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.PasswordAuth;
import org.owasp.herder.authentication.PasswordAuthRepository;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.exception.ClassIdNotFoundException;
import org.owasp.herder.exception.DuplicateUserDisplayNameException;
import org.owasp.herder.exception.DuplicateUserLoginNameException;
import org.owasp.herder.exception.UserNotFoundException;
import org.owasp.herder.user.ClassService;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserRepository;
import org.owasp.herder.user.UserService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService unit tests")
class UserServiceTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private UserService userService;

  @Mock private UserRepository userRepository;

  @Mock private PasswordAuthRepository passwordAuthRepository;

  @Mock private ClassService classService;

  @Mock private KeyService keyService;

  @Mock private WebTokenKeyManager webTokenKeyManager;

  @Test
  void authenticate_InvalidUsername_ReturnsFalse() {
    final String mockedLoginName = "MockUser";
    final String mockedPassword = "MockPassword";

    when(passwordAuthRepository.findByLoginName(mockedLoginName)).thenReturn(Mono.empty());
    StepVerifier.create(userService.authenticate(mockedLoginName, mockedPassword))
        .expectErrorMatches(
            throwable ->
                throwable instanceof AuthenticationException
                    && throwable.getMessage().equals("Invalid username or password"))
        .verify();
    verify(passwordAuthRepository, times(1)).findByLoginName(mockedLoginName);
  }

  @Test
  void authenticate_ValidUsernameAndPassword_Authenticates() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(16);
    final String mockedUserId = "id";
    final String mockedLoginName = "MockUser";
    final String mockedPassword = "MockPassword";
    final String mockedDisplayName = "Mocked user";
    final String mockedPasswordHash = encoder.encode(mockedPassword);
    final PasswordAuth mockedPasswordAuth = mock(PasswordAuth.class);
    final UserEntity mockedUser = mock(UserEntity.class);

    when(userRepository.findById(mockedUserId)).thenReturn(Mono.just(mockedUser));
    when(passwordAuthRepository.findByLoginName(mockedLoginName))
        .thenReturn(Mono.just(mockedPasswordAuth));
    when(mockedPasswordAuth.getHashedPassword()).thenReturn(mockedPasswordHash);
    when(mockedPasswordAuth.getUserId()).thenReturn(mockedUserId);
    when(userRepository.findById(mockedUserId)).thenReturn(Mono.just(mockedUser));
    when(mockedUser.isEnabled()).thenReturn(true);
    when(mockedUser.getDisplayName()).thenReturn(mockedDisplayName);
    when(mockedUser.getSuspendedUntil()).thenReturn(null);

    StepVerifier.create(userService.authenticate(mockedLoginName, mockedPassword))
        .assertNext(
            authResponse -> {
              assertThat(authResponse.getUserId()).isEqualTo(mockedUserId);
            })
        .verifyComplete();

    verify(passwordAuthRepository, times(1)).findByLoginName(mockedLoginName);
  }

  @Test
  void authenticate_ValidUsernameAndPasswordButUserNotEnabled_DoesNotAuthenticates() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(16);
    final String mockedUserId = "id";
    final String mockedLoginName = "MockUser";
    final String mockedPassword = "MockPassword";
    final String mockedPasswordHash = encoder.encode(mockedPassword);
    final PasswordAuth mockedPasswordAuth = mock(PasswordAuth.class);
    final UserEntity mockedUser = mock(UserEntity.class);

    when(passwordAuthRepository.findByLoginName(mockedLoginName))
        .thenReturn(Mono.just(mockedPasswordAuth));
    when(mockedPasswordAuth.getHashedPassword()).thenReturn(mockedPasswordHash);
    when(mockedPasswordAuth.getUserId()).thenReturn(mockedUserId);
    when(userRepository.findById(mockedUserId)).thenReturn(Mono.just(mockedUser));
    when(mockedUser.isEnabled()).thenReturn(false);

    LocalDateTime longAgo = LocalDateTime.MIN;
    when(mockedUser.getSuspendedUntil()).thenReturn(longAgo);

    StepVerifier.create(userService.authenticate(mockedLoginName, mockedPassword))
        .expectErrorMatches(
            throwable ->
                throwable instanceof DisabledException
                    && throwable.getMessage().equals("Account disabled"))
        .verify();

    verify(passwordAuthRepository, times(1)).findByLoginName(mockedLoginName);
  }

  @Test
  void authenticate_ValidUsernameAndPasswordButUserSuspended_DoesNotAuthenticates() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(16);
    final String mockedUserId = "id";
    final String mockedLoginName = "MockUser";
    final String mockedPassword = "MockPassword";
    final String mockedPasswordHash = encoder.encode(mockedPassword);
    final PasswordAuth mockedPasswordAuth = mock(PasswordAuth.class);
    final UserEntity mockedUser = mock(UserEntity.class);

    when(passwordAuthRepository.findByLoginName(mockedLoginName))
        .thenReturn(Mono.just(mockedPasswordAuth));
    when(mockedPasswordAuth.getHashedPassword()).thenReturn(mockedPasswordHash);
    when(mockedPasswordAuth.getUserId()).thenReturn(mockedUserId);
    when(userRepository.findById(mockedUserId)).thenReturn(Mono.just(mockedUser));
    when(mockedUser.isEnabled()).thenReturn(true);

    LocalDateTime futureDate = LocalDateTime.MAX;
    when(mockedUser.getSuspendedUntil()).thenReturn(futureDate);

    when(userRepository.findById(mockedUserId)).thenReturn(Mono.just(mockedUser));

    StepVerifier.create(userService.authenticate(mockedLoginName, mockedPassword))
        .expectErrorMatches(
            throwable ->
                throwable instanceof LockedException
                    && throwable
                        .getMessage()
                        .equals("Account suspended until +999999999-12-31T23:59:59.999999999"))
        .verify();
  }

  @Test
  void authenticate_ValidUsernameButInvalidPassword_DoesNotAuthenticate() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(16);
    final String mockedLoginName = "MockUser";
    final String wrongPassword = "WrongPassword";
    final String mockedPassword = "MockPassword";

    final String mockedPasswordHash = encoder.encode(mockedPassword);
    final PasswordAuth mockedPasswordAuth = mock(PasswordAuth.class);

    when(passwordAuthRepository.findByLoginName(mockedLoginName))
        .thenReturn(Mono.just(mockedPasswordAuth));
    when(mockedPasswordAuth.getHashedPassword()).thenReturn(mockedPasswordHash);

    when(passwordAuthRepository.findByLoginName(mockedLoginName))
        .thenReturn(Mono.just(mockedPasswordAuth));
    when(mockedPasswordAuth.getHashedPassword()).thenReturn(mockedPasswordHash);

    StepVerifier.create(userService.authenticate(mockedLoginName, wrongPassword))
        .expectErrorMatches(
            throwable ->
                throwable instanceof BadCredentialsException
                    && throwable.getMessage().equals("Invalid username or password"))
        .verify();

    verify(passwordAuthRepository, times(1)).findByLoginName(mockedLoginName);
  }

  @Test
  @DisplayName("count() should call repository and return user count")
  void count_NoArgument_ReturnsCount() {
    final long mockedUserCount = 11L;
    when(userRepository.count()).thenReturn(Mono.just(mockedUserCount));

    StepVerifier.create(userService.count()).expectNext(mockedUserCount).verifyComplete();

    verify(userRepository, times(1)).count();
  }

  @Test
  @DisplayName("create() must return exception if display name already exists")
  void create_DisplayNameAlreadyExists_ReturnsDuplicateUserDisplayNameException() {
    final String displayName = "createPasswordUser_DuplicateDisplayName";

    final UserEntity mockUser = mock(UserEntity.class);

    when(userRepository.findByDisplayName(displayName)).thenReturn(Mono.just(mockUser));

    StepVerifier.create(userService.create(displayName))
        .expectError(DuplicateUserDisplayNameException.class)
        .verify();

    verify(userRepository, times(1)).findByDisplayName(displayName);
  }

  @Test
  void create_ValidDisplayName_CreatesUser() {
    final String displayName = "TestUser";
    final String mockUserId = "id";

    when(userRepository.findByDisplayName(displayName)).thenReturn(Mono.empty());

    final byte[] testRandomBytes = {
      -108, 101, -7, -36, 17, -26, -24, 0, -32, -117, 75, -127, 22, 62, 9, 19
    };

    when(keyService.generateRandomBytes(16)).thenReturn(testRandomBytes);

    when(userRepository.save(any(UserEntity.class)))
        .thenAnswer(user -> Mono.just(user.getArgument(0, UserEntity.class).withId(mockUserId)));

    StepVerifier.create(userService.create(displayName)).expectNext(mockUserId).verifyComplete();

    verify(userRepository, times(1)).findByDisplayName(displayName);
    verify(userRepository, times(1)).save(any(UserEntity.class));

    ArgumentCaptor<UserEntity> argument = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository, times(1)).save(argument.capture());
    assertThat(argument.getValue().getDisplayName()).isEqualTo(displayName);
  }

  @Test
  void createPasswordUser_DuplicateDisplayName_ReturnsDuplicateUserDisplayNameException() {
    final String displayName = "createPasswordUser_DuplicateDisplayName";
    final String loginName = "_createPasswordUser_DuplicateDisplayName_";

    final String hashedPassword = "aPasswordHash";

    final UserEntity mockUser = mock(UserEntity.class);

    when(userRepository.findByDisplayName(displayName)).thenReturn(Mono.just(mockUser));

    StepVerifier.create(userService.createPasswordUser(displayName, loginName, hashedPassword))
        .expectError(DuplicateUserDisplayNameException.class)
        .verify();

    verify(userRepository, times(1)).findByDisplayName(displayName);
  }

  @Test
  void createPasswordUser_DuplicateLoginName_ReturnsDuplicateClassNameException() {
    final String displayName = "createPasswordUser_DuplicateLoginName";
    final String loginName = "_createPasswordUser_DuplicateLoginName_";

    final String password = "a_valid_password";

    final PasswordAuth mockPasswordAuth = mock(PasswordAuth.class);

    when(passwordAuthRepository.findByLoginName(loginName)).thenReturn(Mono.just(mockPasswordAuth));
    when(userRepository.findByDisplayName(displayName)).thenReturn(Mono.empty());

    StepVerifier.create(userService.createPasswordUser(displayName, loginName, password))
        .expectError(DuplicateUserLoginNameException.class)
        .verify();

    verify(passwordAuthRepository, times(1)).findByLoginName(loginName);
    verify(userRepository, times(1)).findByDisplayName(displayName);
  }

  @Test
  void createPasswordUser_NullPasswordHash_ReturnsNullPointerException() {
    StepVerifier.create(userService.createPasswordUser("displayName", "loginName", null))
        .expectError(NullPointerException.class)
        .verify();
  }

  @Test
  void createPasswordUser_ValidData_Succeeds() {
    final String displayName = "createPasswordUser_ValidData";
    final String loginName = "_createPasswordUser_ValidData_";

    final String hashedPassword = "a_valid_password";

    final String mockUserId = "id";

    final byte[] testRandomBytes = {
      -108, 101, -7, -36, 17, -26, -24, 0, -32, -117, 75, -127, 22, 62, 9, 19
    };
    when(keyService.generateRandomBytes(16)).thenReturn(testRandomBytes);
    when(passwordAuthRepository.findByLoginName(loginName)).thenReturn(Mono.empty());
    when(userRepository.findByDisplayName(displayName)).thenReturn(Mono.empty());
    when(userRepository.save(any(UserEntity.class)))
        .thenAnswer(user -> Mono.just(user.getArgument(0, UserEntity.class).withId(mockUserId)));
    when(passwordAuthRepository.save(any(PasswordAuth.class)))
        .thenAnswer(user -> Mono.just(user.getArgument(0, PasswordAuth.class).withId(mockUserId)));

    StepVerifier.create(userService.createPasswordUser(displayName, loginName, hashedPassword))
        .expectNext(mockUserId)
        .verifyComplete();

    verify(passwordAuthRepository, times(1)).findByLoginName(loginName);
    verify(userRepository, times(1)).findByDisplayName(displayName);
    verify(userRepository, times(1)).save(any(UserEntity.class));
    verify(userRepository, times(1)).save(any(UserEntity.class));
    verify(passwordAuthRepository, times(1)).save(any(PasswordAuth.class));

    ArgumentCaptor<UserEntity> userArgumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository, times(1)).save(userArgumentCaptor.capture());
    assertThat(userArgumentCaptor.getValue().getId()).isNull();
    assertThat(userArgumentCaptor.getValue().getDisplayName()).isEqualTo(displayName);

    ArgumentCaptor<PasswordAuth> passwordAuthArgumentCaptor =
        ArgumentCaptor.forClass(PasswordAuth.class);
    verify(passwordAuthRepository, times(1)).save(passwordAuthArgumentCaptor.capture());
    assertThat(passwordAuthArgumentCaptor.getValue().getUserId()).isEqualTo(mockUserId);
    assertThat(passwordAuthArgumentCaptor.getValue().getLoginName()).isEqualTo(loginName);
    assertThat(passwordAuthArgumentCaptor.getValue().getHashedPassword()).isEqualTo(hashedPassword);
  }

  @Test
  void deleteById_ValidUserId_CallsRepository() {
    final String mockUserId = "id";
    when(passwordAuthRepository.deleteByUserId(mockUserId)).thenReturn(Mono.empty());
    when(userRepository.deleteById(mockUserId)).thenReturn(Mono.empty());

    StepVerifier.create(userService.deleteById(mockUserId)).verifyComplete();
  }

  @Test
  void demote_UserIsAdmin_Demoted() {
    final String mockUserId = "id";
    final String mockAuthId = "id";

    final UserEntity mockUser = mock(UserEntity.class);
    final UserEntity mockDemotedUser = mock(UserEntity.class);

    when(userRepository.save(any(UserEntity.class)))
        .thenAnswer(
            auth -> {
              if (auth.getArgument(0, UserEntity.class) == mockDemotedUser) {
                // We are saving the admin auth to db
                return Mono.just(auth.getArgument(0, UserEntity.class));
              } else {
                // We are saving the newly created auth to db
                return Mono.just(auth.getArgument(0, UserEntity.class).withId(mockAuthId));
              }
            });

    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUser));

    when(mockUser.withAdmin(false)).thenReturn(mockDemotedUser);

    Mockito.doNothing().when(webTokenKeyManager).invalidateAccessToken(mockUserId);

    StepVerifier.create(userService.demote(mockUserId)).verifyComplete();

    verify(passwordAuthRepository, never()).findByUserId(any(String.class));

    verify(userRepository, times(1)).findById(mockUserId);

    verify(mockUser, times(1)).withAdmin(false);
    verify(userRepository, never()).save(mockUser);
    verify(userRepository, times(1)).save(mockDemotedUser);
    verify(webTokenKeyManager, times(1)).invalidateAccessToken(mockUserId);
  }

  @Test
  void demote_UserIsNotAdmin_StaysNotAdmin() {
    final String mockUserId = "id";

    final UserEntity mockUser = mock(UserEntity.class);

    when(userRepository.save(any(UserEntity.class)))
        .thenAnswer(
            savedUser -> {
              if (savedUser.getArgument(0, UserEntity.class).equals(mockUser)) {
                // We are saving the admin user to db
                return Mono.just(savedUser.getArgument(0, UserEntity.class));
              } else {
                // We are saving the newly created user to db
                return Mono.just(savedUser.getArgument(0, UserEntity.class).withId(mockUserId));
              }
            });

    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUser));
    when(mockUser.withAdmin(false)).thenReturn(mockUser);

    StepVerifier.create(userService.demote(mockUserId)).verifyComplete();

    verify(passwordAuthRepository, never()).findByUserId(any(String.class));
    verify(passwordAuthRepository, never()).save(any(PasswordAuth.class));
    verify(userRepository, times(1)).save(mockUser);
    verify(userRepository, times(1)).findById(mockUserId);
    verify(mockUser, times(1)).withAdmin(false);
  }

  @Test
  void findAll_NoUsersExist_ReturnsEmpty() {
    when(userRepository.findAll()).thenReturn(Flux.empty());
    StepVerifier.create(userService.findAll()).verifyComplete();
    verify(userRepository, times(1)).findAll();
  }

  @Test
  void findAll_UsersExist_ReturnsUsers() {
    final UserEntity mockUser1 = mock(UserEntity.class);
    final UserEntity mockUser2 = mock(UserEntity.class);
    final UserEntity mockUser3 = mock(UserEntity.class);

    when(userRepository.findAll()).thenReturn(Flux.just(mockUser1, mockUser2, mockUser3));

    StepVerifier.create(userService.findAll())
        .expectNext(mockUser1)
        .expectNext(mockUser2)
        .expectNext(mockUser3)
        .verifyComplete();

    verify(userRepository, times(1)).findAll();
  }

  @Test
  void findById_ExistingUserId_ReturnsUserEntity() {
    final UserEntity mockUser = mock(UserEntity.class);

    final String mockUserId = "id";

    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUser));

    StepVerifier.create(userService.findById(mockUserId)).expectNext(mockUser).verifyComplete();

    verify(userRepository, times(1)).findById(mockUserId);
  }

  @Test
  void findById_NonExistentUserId_ReturnsEmpty() {
    final String nonExistentUserId = "id";
    when(userRepository.findById(nonExistentUserId)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findById(nonExistentUserId)).verifyComplete();
    verify(userRepository, times(1)).findById(nonExistentUserId);
  }

  @Test
  void findByLoginName_LoginNameDoesNotExist_ReturnsEmptyMono() {
    final String nonExistentLoginName = "NonExistentUser";
    when(passwordAuthRepository.findByLoginName(nonExistentLoginName)).thenReturn(Mono.empty());

    StepVerifier.create(userService.findUserIdByLoginName(nonExistentLoginName)).verifyComplete();

    verify(passwordAuthRepository, times(1)).findByLoginName(nonExistentLoginName);
  }

  @Test
  void findByLoginName_UserExists_ReturnsUser() {
    final PasswordAuth mockPasswordAuth = mock(PasswordAuth.class);
    final String loginName = "MockUser";
    final String mockUserId = "id";

    when(passwordAuthRepository.findByLoginName(loginName)).thenReturn(Mono.just(mockPasswordAuth));
    when(mockPasswordAuth.getUserId()).thenReturn(mockUserId);

    StepVerifier.create(userService.findUserIdByLoginName(loginName))
        .expectNext(mockUserId)
        .verifyComplete();

    verify(passwordAuthRepository, times(1)).findByLoginName(loginName);
  }

  @Test
  void findDisplayNameById_NoUserExists_ReturnsEmpty() {
    final String mockUserId = "id";
    when(userRepository.findById(mockUserId)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findDisplayNameById(mockUserId)).verifyComplete();
    verify(userRepository, times(1)).findById(mockUserId);
  }

  @Test
  void findDisplayNameById_UserExists_ReturnsDisplayName() {
    final UserEntity mockUser = mock(UserEntity.class);
    final String mockUserId = "id";
    final String mockDisplayName = "mockDisplayName";

    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn(mockDisplayName);

    StepVerifier.create(userService.findDisplayNameById(mockUserId))
        .expectNext(mockDisplayName)
        .verifyComplete();

    verify(userRepository, times(1)).findById(mockUserId);
    verify(mockUser, times(1)).getDisplayName();
  }

  @Test
  void findPasswordAuthByLoginName_ExistingLoginName_ReturnsPasswordAuth() {
    final PasswordAuth mockPasswordAuth = mock(PasswordAuth.class);

    final String mockLoginName = "loginName";
    when(passwordAuthRepository.findByLoginName(mockLoginName))
        .thenReturn(Mono.just(mockPasswordAuth));

    StepVerifier.create(userService.findPasswordAuthByLoginName(mockLoginName))
        .expectNext(mockPasswordAuth)
        .verifyComplete();

    verify(passwordAuthRepository, times(1)).findByLoginName(mockLoginName);
  }

  @Test
  void findPasswordAuthByLoginName_NonExistentLoginName_ReturnsEmpty() {
    final String mockLoginName = "loginName";
    when(passwordAuthRepository.findByLoginName(mockLoginName)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findPasswordAuthByLoginName(mockLoginName)).verifyComplete();

    verify(passwordAuthRepository, times(1)).findByLoginName(mockLoginName);
  }

  @Test
  void findPasswordAuthByUserId_NoPasswordAuthExists_ReturnsEmpty() {
    final String mockUserId = "id";

    when(passwordAuthRepository.findByUserId(mockUserId)).thenReturn(Mono.empty());

    StepVerifier.create(userService.findPasswordAuthByUserId(mockUserId)).verifyComplete();

    verify(passwordAuthRepository, times(1)).findByUserId(mockUserId);
  }

  @Test
  void findPasswordAuthByUserId_PasswordAuthExists_ReturnsPasswordAuth() {
    final PasswordAuth mockPasswordAuth = mock(PasswordAuth.class);
    final String mockUserId = "id";

    when(passwordAuthRepository.findByUserId(mockUserId)).thenReturn(Mono.just(mockPasswordAuth));

    StepVerifier.create(userService.findPasswordAuthByUserId(mockUserId))
        .expectNext(mockPasswordAuth)
        .verifyComplete();

    verify(passwordAuthRepository, times(1)).findByUserId(mockUserId);
  }

  @Test
  void getKeyById_KeyExists_ReturnsKey() {
    // Establish a random key
    final byte[] testRandomBytes = {
      -108, 101, -7, -36, 17, -26, -24, 0, -32, -117, 75, -127, 22, 62, 9, 19
    };
    final String mockUserId = "id";

    // Mock a test user that has a key
    final UserEntity mockUserWithKey = mock(UserEntity.class);
    when(mockUserWithKey.getKey()).thenReturn(testRandomBytes);

    when(userRepository.existsById(mockUserId)).thenReturn(Mono.just(true));
    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUserWithKey));

    StepVerifier.create(userService.findKeyById(mockUserId))
        .expectNext(testRandomBytes)
        .verifyComplete();

    final InOrder order = inOrder(mockUserWithKey, userRepository);
    // userService should query the repository
    order.verify(userRepository, times(1)).findById(mockUserId);
    // and then extract the key
    order.verify(mockUserWithKey, times(1)).getKey();
  }

  @Test
  void getKeyById_NoKeyExists_GeneratesKey() {
    // Establish a random key
    final byte[] testRandomBytes = {
      -108, 101, -7, -36, 17, -26, -24, 0, -32, -117, 75, -127, 22, 62, 9, 19
    };
    final String mockUserId = "id";

    // This user does not have a key
    final UserEntity mockUserWithoutKey = mock(UserEntity.class);
    when(mockUserWithoutKey.getKey()).thenReturn(null);

    // When that user is given a key, return an entity that has the key
    final UserEntity mockUserWithKey = mock(UserEntity.class);
    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUserWithoutKey));
    when(mockUserWithoutKey.getKey()).thenReturn(null);

    when(mockUserWithoutKey.withKey(testRandomBytes)).thenReturn(mockUserWithKey);

    // Set up the mock repository
    when(userRepository.existsById(mockUserId)).thenReturn(Mono.just(true));
    when(userRepository.save(mockUserWithKey)).thenReturn(Mono.just(mockUserWithKey));

    // Set up the mock key service
    when(keyService.generateRandomBytes(16)).thenReturn(testRandomBytes);

    when(mockUserWithKey.getKey()).thenReturn(testRandomBytes);

    StepVerifier.create(userService.findKeyById(mockUserId))
        .expectNext(testRandomBytes)
        .verifyComplete();

    verify(userRepository, times(1)).findById(mockUserId);
    verify(mockUserWithoutKey, times(1)).getKey();
    verify(keyService, times(1)).generateRandomBytes(16);
    verify(mockUserWithoutKey, times(1)).withKey(testRandomBytes);
    verify(mockUserWithKey, times(1)).getKey();

    ArgumentCaptor<UserEntity> argument = ArgumentCaptor.forClass(UserEntity.class);

    verify(userRepository, times(1)).save(argument.capture());

    assertThat(argument.getValue()).isEqualTo(mockUserWithKey);
    assertThat(argument.getValue().getKey()).isEqualTo(testRandomBytes);
  }

  @Test
  void promote_UserIsAdmin_StaysAdmin() {
    final String mockUserId = "id";
    final String mockAuthId = "Authid";

    final UserEntity mockUser = mock(UserEntity.class);

    when(userRepository.save(any(UserEntity.class)))
        .thenAnswer(
            auth -> {
              if (auth.getArgument(0, UserEntity.class).equals(mockUser)) {
                // We are saving the admin auth to db
                return Mono.just(auth.getArgument(0, UserEntity.class));
              } else {
                // We are saving the newly created auth to db
                return Mono.just(auth.getArgument(0, UserEntity.class).withId(mockAuthId));
              }
            });

    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUser));
    when(mockUser.withAdmin(true)).thenReturn(mockUser);

    StepVerifier.create(userService.promote(mockUserId)).verifyComplete();

    verify(passwordAuthRepository, never()).findByUserId(any(String.class));
    verify(userRepository, times(1)).findById(mockUserId);
    verify(mockUser, times(1)).withAdmin(true);
    verify(userRepository, times(1)).save(mockUser);
  }

  @Test
  void setClassId_NonExistentClassId_ReturnsClassIdNotFoundException() {
    final String mockUserId = "id";
    final String mockClassId = "classid";

    UserEntity mockUser = mock(UserEntity.class);

    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUser));
    when(classService.existsById(mockClassId)).thenReturn(Mono.just(false));

    StepVerifier.create(userService.setClassId(mockUserId, mockClassId))
        .expectError(ClassIdNotFoundException.class)
        .verify();

    verify(userRepository, times(1)).findById(mockUserId);
    verify(classService, times(1)).existsById(mockClassId);
  }

  @Test
  void setClassId_ValidClassId_Succeeds() {
    final String mockUserId = "id";
    final String mockClassId = "classid";

    final UserEntity mockUser = mock(UserEntity.class);
    final UserEntity mockUserWithClass = mock(UserEntity.class);

    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUser));
    when(classService.existsById(mockClassId)).thenReturn(Mono.just(true));

    when(mockUser.withClassId(mockClassId)).thenReturn(mockUserWithClass);
    when(userRepository.save(mockUserWithClass)).thenReturn(Mono.just(mockUserWithClass));
    when(mockUserWithClass.getClassId()).thenReturn(mockClassId);

    StepVerifier.create(userService.setClassId(mockUserId, mockClassId))
        .expectNextMatches(user -> user.getClassId() == mockClassId)
        .verifyComplete();

    verify(userRepository, times(1)).findById(mockUserId);
    verify(classService, times(1)).existsById(mockClassId);

    verify(mockUser, times(1)).withClassId(mockClassId);
    verify(userRepository, times(1)).save(mockUserWithClass);
    verify(mockUserWithClass, times(1)).getClassId();
  }

  @Test
  void setDisplayName_UserIdDoesNotExist_ReturnsUserIdNotFoundException() {
    final String newDisplayName = "newName";

    final String mockUserId = "id";

    when(userRepository.existsById(mockUserId)).thenReturn(Mono.just(false));

    StepVerifier.create(userService.setDisplayName(mockUserId, newDisplayName))
        .expectError(UserNotFoundException.class)
        .verify();
    verify(userRepository, times(1)).existsById(mockUserId);
  }

  @Test
  void setDisplayName_ValidArguments_DisplayNameIsSet() {
    UserEntity mockUser = mock(UserEntity.class);
    String newDisplayName = "newDisplayName";

    final String mockUserId = "id";

    when(userRepository.existsById(mockUserId)).thenReturn(Mono.just(true));
    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUser));
    when(userRepository.findByDisplayName(newDisplayName)).thenReturn(Mono.empty());

    when(mockUser.withDisplayName(newDisplayName)).thenReturn(mockUser);
    when(userRepository.save(any(UserEntity.class))).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn(newDisplayName);

    StepVerifier.create(userService.setDisplayName(mockUserId, newDisplayName))
        .expectNextMatches(user -> user.getDisplayName().equals(newDisplayName))
        .as("Display name should change to supplied value")
        .verifyComplete();

    verify(userRepository, times(1)).existsById(mockUserId);
    verify(userRepository, times(1)).findById(mockUserId);
    verify(userRepository, times(1)).findByDisplayName(newDisplayName);

    verify(mockUser, times(1)).withDisplayName(newDisplayName);
    verify(userRepository, times(1)).save(any(UserEntity.class));
    verify(mockUser, times(1)).getDisplayName();
  }

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    userService =
        new UserService(
            userRepository, passwordAuthRepository, classService, keyService, webTokenKeyManager);
  }
}
