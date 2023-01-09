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
package org.owasp.herder.test.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
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
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.ClassService;
import org.owasp.herder.user.TeamRepository;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserRepository;
import org.owasp.herder.user.UserService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService unit tests")
class UserServiceTest extends BaseTest {

  private UserService userService;

  @Mock
  TeamRepository teamRepository;

  @Mock
  UserRepository userRepository;

  @Mock
  PasswordAuthRepository passwordAuthRepository;

  @Mock
  ClassService classService;

  @Mock
  KeyService keyService;

  @Mock
  WebTokenKeyManager webTokenKeyManager;

  @Mock
  Clock clock;

  PasswordAuth mockPasswordAuth;

  UserEntity mockUser;

  @Test
  void authenticate_InvalidUsername_ReturnsFalse() {
    when(
      passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)
    )
      .thenReturn(Mono.empty());
    StepVerifier
      .create(
        userService.authenticate(
          TestConstants.TEST_USER_LOGIN_NAME,
          TestConstants.TEST_USER_PASSWORD
        )
      )
      .expectErrorMatches(throwable ->
        throwable instanceof AuthenticationException &&
        throwable.getMessage().equals("Invalid username or password")
      )
      .verify();
    verify(passwordAuthRepository, times(1))
      .findByLoginName(TestConstants.TEST_USER_LOGIN_NAME);
  }

  @Test
  void authenticate_UserSuspended_ReturnsLockedException() {
    when(
      passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)
    )
      .thenReturn(Mono.just(mockPasswordAuth));
    when(mockPasswordAuth.getHashedPassword())
      .thenReturn(TestConstants.HASHED_TEST_PASSWORD);
    when(mockPasswordAuth.getUserId()).thenReturn(TestConstants.TEST_USER_ID);
    when(mockUser.isEnabled()).thenReturn(true);

    LocalDateTime futureDate = LocalDateTime.MAX;
    when(mockUser.getSuspendedUntil()).thenReturn(futureDate);

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUser));

    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(
        userService.authenticate(
          TestConstants.TEST_USER_LOGIN_NAME,
          TestConstants.TEST_USER_PASSWORD
        )
      )
      .expectErrorMatches(throwable ->
        throwable instanceof LockedException &&
        throwable
          .getMessage()
          .equals("Account suspended until +999999999-12-31T23:59:59.999999999")
      )
      .verify();
  }

  @Test
  void authenticate_ValidUsernameAndPassword_Authenticates() {
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUser));
    when(
      passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)
    )
      .thenReturn(Mono.just(mockPasswordAuth));
    when(mockPasswordAuth.getHashedPassword())
      .thenReturn(TestConstants.HASHED_TEST_PASSWORD);
    when(mockPasswordAuth.getUserId()).thenReturn(TestConstants.TEST_USER_ID);
    when(mockUser.isEnabled()).thenReturn(true);
    when(mockUser.getDisplayName())
      .thenReturn(TestConstants.TEST_USER_DISPLAY_NAME);
    when(mockUser.getSuspendedUntil()).thenReturn(null);

    StepVerifier
      .create(
        userService.authenticate(
          TestConstants.TEST_USER_LOGIN_NAME,
          TestConstants.TEST_USER_PASSWORD
        )
      )
      .assertNext(authResponse -> {
        assertThat(authResponse.getUserId())
          .isEqualTo(TestConstants.TEST_USER_ID);
      })
      .verifyComplete();

    verify(passwordAuthRepository, times(1))
      .findByLoginName(TestConstants.TEST_USER_LOGIN_NAME);
  }

  @Test
  void authenticate_ValidUsernameAndPasswordButUserNotEnabled_ReturnsDisabledException() {
    when(
      passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)
    )
      .thenReturn(Mono.just(mockPasswordAuth));
    when(mockPasswordAuth.getHashedPassword())
      .thenReturn(TestConstants.HASHED_TEST_PASSWORD);
    when(mockPasswordAuth.getUserId()).thenReturn(TestConstants.TEST_USER_ID);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUser));
    when(mockUser.isEnabled()).thenReturn(false);

    LocalDateTime longAgo = LocalDateTime.MIN;
    when(mockUser.getSuspendedUntil()).thenReturn(longAgo);

    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(
        userService.authenticate(
          TestConstants.TEST_USER_LOGIN_NAME,
          TestConstants.TEST_USER_PASSWORD
        )
      )
      .expectErrorMatches(throwable ->
        throwable instanceof DisabledException &&
        throwable.getMessage().equals("Account disabled")
      )
      .verify();

    verify(passwordAuthRepository, times(1))
      .findByLoginName(TestConstants.TEST_USER_LOGIN_NAME);
  }

  @Test
  void authenticate_ValidUsernameButInvalidPassword_DoesNotAuthenticate() {
    when(
      passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)
    )
      .thenReturn(Mono.just(mockPasswordAuth));
    when(mockPasswordAuth.getHashedPassword())
      .thenReturn(TestConstants.HASHED_TEST_PASSWORD);

    StepVerifier
      .create(
        userService.authenticate(
          TestConstants.TEST_USER_LOGIN_NAME,
          "wrong password"
        )
      )
      .expectErrorMatches(throwable ->
        throwable instanceof BadCredentialsException &&
        throwable.getMessage().equals("Invalid username or password")
      )
      .verify();

    verify(passwordAuthRepository, times(1))
      .findByLoginName(TestConstants.TEST_USER_LOGIN_NAME);
  }

  @Test
  @DisplayName("create() can return exception if display name already exists")
  void create_DisplayNameAlreadyExists_ReturnsDuplicateUserDisplayNameException() {
    final byte[] testRandomBytes = {
      -108,
      101,
      -7,
      -36,
      17,
      -26,
      -24,
      0,
      -32,
      -117,
      75,
      -127,
      22,
      62,
      9,
      19,
    };

    when(keyService.generateRandomBytes(16)).thenReturn(testRandomBytes);

    when(userRepository.findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME))
      .thenReturn(Mono.just(mockUser));

    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(userService.create(TestConstants.TEST_USER_DISPLAY_NAME))
      .expectError(DuplicateUserDisplayNameException.class)
      .verify();

    verify(userRepository, times(1))
      .findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME);
  }

  @Test
  @DisplayName("Can create a user")
  void create_ValidDisplayName_CreatesUser() {
    when(userRepository.findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME))
      .thenReturn(Mono.empty());

    final byte[] testRandomBytes = {
      -108,
      101,
      -7,
      -36,
      17,
      -26,
      -24,
      0,
      -32,
      -117,
      75,
      -127,
      22,
      62,
      9,
      19,
    };

    when(keyService.generateRandomBytes(16)).thenReturn(testRandomBytes);

    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(user ->
        Mono.just(
          user
            .getArgument(0, UserEntity.class)
            .withId(TestConstants.TEST_USER_ID)
        )
      );

    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(userService.create(TestConstants.TEST_USER_DISPLAY_NAME))
      .expectNext(TestConstants.TEST_USER_ID)
      .verifyComplete();

    verify(userRepository, times(1))
      .findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME);
    verify(userRepository, times(1)).save(any(UserEntity.class));

    ArgumentCaptor<UserEntity> argument = ArgumentCaptor.forClass(
      UserEntity.class
    );
    verify(userRepository, times(1)).save(argument.capture());

    final UserEntity createdUser = argument.getValue();
    assertThat(createdUser.getDisplayName())
      .isEqualTo(TestConstants.TEST_USER_DISPLAY_NAME);
    assertThat(createdUser.getCreationTime())
      .isEqualTo(LocalDateTime.now(TestConstants.year2000Clock));
    assertThat(createdUser.getId()).isNull();
    assertThat(createdUser.getKey()).isEqualTo(testRandomBytes);
    assertThat(createdUser.isAdmin()).isFalse();
    assertThat(createdUser.isEnabled()).isTrue();
    assertThat(createdUser.getSuspendedUntil()).isNull();
    assertThat(createdUser.getSuspensionMessage()).isNull();
  }

  @Test
  void createPasswordUser_DuplicateDisplayName_ReturnsDuplicateUserDisplayNameException() {
    when(userRepository.findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME))
      .thenReturn(Mono.just(mockUser));

    StepVerifier
      .create(
        userService.createPasswordUser(
          TestConstants.TEST_USER_DISPLAY_NAME,
          TestConstants.TEST_USER_LOGIN_NAME,
          TestConstants.TEST_USER_PASSWORD
        )
      )
      .expectError(DuplicateUserDisplayNameException.class)
      .verify();

    verify(userRepository, times(1))
      .findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME);
  }

  @Test
  void createPasswordUser_DuplicateLoginName_ReturnsDuplicateClassNameException() {
    when(
      passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)
    )
      .thenReturn(Mono.just(mockPasswordAuth));
    when(userRepository.findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME))
      .thenReturn(Mono.empty());

    StepVerifier
      .create(
        userService.createPasswordUser(
          TestConstants.TEST_USER_DISPLAY_NAME,
          TestConstants.TEST_USER_LOGIN_NAME,
          TestConstants.TEST_USER_PASSWORD
        )
      )
      .expectError(DuplicateUserLoginNameException.class)
      .verify();

    verify(passwordAuthRepository, times(1))
      .findByLoginName(TestConstants.TEST_USER_LOGIN_NAME);
    verify(userRepository, times(1))
      .findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME);
  }

  @Test
  void createPasswordUser_NullPasswordHash_ReturnsNullPointerException() {
    StepVerifier
      .create(userService.createPasswordUser("displayName", "loginName", null))
      .expectError(NullPointerException.class)
      .verify();
  }

  @Test
  void createPasswordUser_ValidData_Succeeds() {
    final byte[] testRandomBytes = {
      -108,
      101,
      -7,
      -36,
      17,
      -26,
      -24,
      0,
      -32,
      -117,
      75,
      -127,
      22,
      62,
      9,
      19,
    };
    when(keyService.generateRandomBytes(16)).thenReturn(testRandomBytes);
    when(
      passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)
    )
      .thenReturn(Mono.empty());
    when(userRepository.findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME))
      .thenReturn(Mono.empty());
    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(user ->
        Mono.just(
          user
            .getArgument(0, UserEntity.class)
            .withId(TestConstants.TEST_USER_ID)
        )
      );
    when(passwordAuthRepository.save(any(PasswordAuth.class)))
      .thenAnswer(user ->
        Mono.just(
          user
            .getArgument(0, PasswordAuth.class)
            .withId(TestConstants.TEST_USER_ID)
        )
      );

    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(
        userService.createPasswordUser(
          TestConstants.TEST_USER_DISPLAY_NAME,
          TestConstants.TEST_USER_LOGIN_NAME,
          TestConstants.HASHED_TEST_PASSWORD
        )
      )
      .expectNext(TestConstants.TEST_USER_ID)
      .verifyComplete();

    verify(passwordAuthRepository, times(1))
      .findByLoginName(TestConstants.TEST_USER_LOGIN_NAME);
    verify(userRepository, times(1))
      .findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME);
    verify(userRepository, times(1)).save(any(UserEntity.class));
    verify(passwordAuthRepository, times(1)).save(any(PasswordAuth.class));

    ArgumentCaptor<UserEntity> userArgumentCaptor = ArgumentCaptor.forClass(
      UserEntity.class
    );
    verify(userRepository, times(1)).save(userArgumentCaptor.capture());
    assertThat(userArgumentCaptor.getValue().getId()).isNull();
    assertThat(userArgumentCaptor.getValue().getDisplayName())
      .isEqualTo(TestConstants.TEST_USER_DISPLAY_NAME);

    ArgumentCaptor<PasswordAuth> passwordAuthArgumentCaptor = ArgumentCaptor.forClass(
      PasswordAuth.class
    );
    verify(passwordAuthRepository, times(1))
      .save(passwordAuthArgumentCaptor.capture());
    assertThat(passwordAuthArgumentCaptor.getValue().getUserId())
      .isEqualTo(TestConstants.TEST_USER_ID);
    assertThat(passwordAuthArgumentCaptor.getValue().getLoginName())
      .isEqualTo(TestConstants.TEST_USER_LOGIN_NAME);
    assertThat(passwordAuthArgumentCaptor.getValue().getHashedPassword())
      .isEqualTo(TestConstants.HASHED_TEST_PASSWORD);
  }

  @Test
  @DisplayName("Can return error when deleting nonexistent user")
  void deleteById_NonexistentUserId_SavesDeletedPlaceholder() {
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.empty());

    StepVerifier
      .create(userService.delete(TestConstants.TEST_USER_ID))
      .expectError(UserNotFoundException.class)
      .verify();

    verify(userRepository, times(1))
      .findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can delete user")
  void deleteById_ValidUserId_SavesDeletedPlaceholder() {
    when(passwordAuthRepository.deleteByUserId(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.empty());
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUser));
    when(userRepository.save(mockUser)).thenReturn(Mono.just(mockUser));
    when(mockUser.withTeamId(null)).thenReturn(mockUser);
    when(mockUser.withEnabled(false)).thenReturn(mockUser);
    when(mockUser.withDeleted(true)).thenReturn(mockUser);
    when(mockUser.withDisplayName("")).thenReturn(mockUser);
    when(mockUser.withSuspensionMessage(null)).thenReturn(mockUser);
    when(mockUser.withAdmin(false)).thenReturn(mockUser);

    StepVerifier
      .create(userService.delete(TestConstants.TEST_USER_ID))
      .verifyComplete();

    verify(passwordAuthRepository, times(1))
      .deleteByUserId(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can demote administrator to regular user")
  void demote_UserIsAdmin_Demoted() {
    final String mockAuthId = "id";

    final UserEntity mockDemotedUser = mock(UserEntity.class);

    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(auth -> {
        if (auth.getArgument(0, UserEntity.class) == mockDemotedUser) {
          // We are saving the admin auth to db
          return Mono.just(auth.getArgument(0, UserEntity.class));
        } else {
          // We are saving the newly created auth to db
          return Mono.just(
            auth.getArgument(0, UserEntity.class).withId(mockAuthId)
          );
        }
      });

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUser));

    when(mockUser.withAdmin(false)).thenReturn(mockDemotedUser);

    Mockito
      .doNothing()
      .when(webTokenKeyManager)
      .invalidateAccessToken(TestConstants.TEST_USER_ID);

    StepVerifier
      .create(userService.demote(TestConstants.TEST_USER_ID))
      .verifyComplete();

    verify(userRepository, times(1))
      .findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID);

    verify(mockUser, times(1)).withAdmin(false);
    verify(userRepository, times(1)).save(mockDemotedUser);
    verify(webTokenKeyManager, times(1))
      .invalidateAccessToken(TestConstants.TEST_USER_ID);
  }

  @Test
  void demote_UserIsNotAdmin_StaysNotAdmin() {
    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(savedUser -> {
        if (savedUser.getArgument(0, UserEntity.class).equals(mockUser)) {
          // We are saving the admin user to db
          return Mono.just(savedUser.getArgument(0, UserEntity.class));
        } else {
          // We are saving the newly created user to db
          return Mono.just(
            savedUser
              .getArgument(0, UserEntity.class)
              .withId(TestConstants.TEST_USER_ID)
          );
        }
      });

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUser));
    when(mockUser.withAdmin(false)).thenReturn(mockUser);

    StepVerifier
      .create(userService.demote(TestConstants.TEST_USER_ID))
      .verifyComplete();

    verify(userRepository, times(1)).save(mockUser);
    verify(userRepository, times(1))
      .findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID);
    verify(mockUser, times(1)).withAdmin(false);
  }

  @Test
  void findAll_NoUsersExist_ReturnsEmpty() {
    when(userRepository.findAll()).thenReturn(Flux.empty());
    StepVerifier.create(userService.findAllUsers()).verifyComplete();
    verify(userRepository, times(1)).findAll();
  }

  @Test
  void findAll_UsersExist_ReturnsUsers() {
    final UserEntity mockUser1 = mock(UserEntity.class);
    final UserEntity mockUser2 = mock(UserEntity.class);
    final UserEntity mockUser3 = mock(UserEntity.class);

    when(userRepository.findAll())
      .thenReturn(Flux.just(mockUser1, mockUser2, mockUser3));

    StepVerifier
      .create(userService.findAllUsers())
      .expectNext(mockUser1)
      .expectNext(mockUser2)
      .expectNext(mockUser3)
      .verifyComplete();

    verify(userRepository, times(1)).findAll();
  }

  @Test
  void findById_ExistingUserId_ReturnsUserEntity() {
    final UserEntity mockUser = mock(UserEntity.class);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUser));

    StepVerifier
      .create(userService.findById(TestConstants.TEST_USER_ID))
      .expectNext(mockUser)
      .verifyComplete();

    verify(userRepository, times(1))
      .findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID);
  }

  @Test
  void findById_NonExistentUserId_ReturnsEmpty() {
    final String nonExistentUserId = "id";
    when(userRepository.findByIdAndIsDeletedFalse(nonExistentUserId))
      .thenReturn(Mono.empty());
    StepVerifier
      .create(userService.findById(nonExistentUserId))
      .verifyComplete();
    verify(userRepository, times(1))
      .findByIdAndIsDeletedFalse(nonExistentUserId);
  }

  @Test
  void findByLoginName_LoginNameDoesNotExist_ReturnsEmptyMono() {
    final String nonExistentLoginName = "NonExistentUser";
    when(passwordAuthRepository.findByLoginName(nonExistentLoginName))
      .thenReturn(Mono.empty());

    StepVerifier
      .create(userService.findUserIdByLoginName(nonExistentLoginName))
      .verifyComplete();

    verify(passwordAuthRepository, times(1))
      .findByLoginName(nonExistentLoginName);
  }

  @Test
  void findByLoginName_UserExists_ReturnsUser() {
    final PasswordAuth mockPasswordAuth = mock(PasswordAuth.class);

    when(
      passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)
    )
      .thenReturn(Mono.just(mockPasswordAuth));
    when(mockPasswordAuth.getUserId()).thenReturn(TestConstants.TEST_USER_ID);

    StepVerifier
      .create(
        userService.findUserIdByLoginName(TestConstants.TEST_USER_LOGIN_NAME)
      )
      .expectNext(TestConstants.TEST_USER_ID)
      .verifyComplete();

    verify(passwordAuthRepository, times(1))
      .findByLoginName(TestConstants.TEST_USER_LOGIN_NAME);
  }

  @Test
  void findPasswordAuthByLoginName_ExistingLoginName_ReturnsPasswordAuth() {
    when(
      passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)
    )
      .thenReturn(Mono.just(mockPasswordAuth));

    StepVerifier
      .create(
        userService.findPasswordAuthByLoginName(
          TestConstants.TEST_USER_LOGIN_NAME
        )
      )
      .expectNext(mockPasswordAuth)
      .verifyComplete();

    verify(passwordAuthRepository, times(1))
      .findByLoginName(TestConstants.TEST_USER_LOGIN_NAME);
  }

  @Test
  void findPasswordAuthByLoginName_NonExistentLoginName_ReturnsEmpty() {
    when(
      passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)
    )
      .thenReturn(Mono.empty());
    StepVerifier
      .create(
        userService.findPasswordAuthByLoginName(
          TestConstants.TEST_USER_LOGIN_NAME
        )
      )
      .verifyComplete();
    verify(passwordAuthRepository, times(1))
      .findByLoginName(TestConstants.TEST_USER_LOGIN_NAME);
  }

  @Test
  void findPasswordAuthByUserId_NoPasswordAuthExists_ReturnsEmpty() {
    when(passwordAuthRepository.findByUserId(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.empty());

    StepVerifier
      .create(userService.findPasswordAuthByUserId(TestConstants.TEST_USER_ID))
      .verifyComplete();

    verify(passwordAuthRepository, times(1))
      .findByUserId(TestConstants.TEST_USER_ID);
  }

  @Test
  void findPasswordAuthByUserId_PasswordAuthExists_ReturnsPasswordAuth() {
    when(passwordAuthRepository.findByUserId(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockPasswordAuth));

    StepVerifier
      .create(userService.findPasswordAuthByUserId(TestConstants.TEST_USER_ID))
      .expectNext(mockPasswordAuth)
      .verifyComplete();

    verify(passwordAuthRepository, times(1))
      .findByUserId(TestConstants.TEST_USER_ID);
  }

  @Test
  void getKeyById_KeyExists_ReturnsKey() {
    // Establish a random key
    final byte[] testRandomBytes = {
      -108,
      101,
      -7,
      -36,
      17,
      -26,
      -24,
      0,
      -32,
      -117,
      75,
      -127,
      22,
      62,
      9,
      19,
    };

    when(mockUser.getKey()).thenReturn(testRandomBytes);

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUser));

    StepVerifier
      .create(userService.findKeyById(TestConstants.TEST_USER_ID))
      .expectNext(testRandomBytes)
      .verifyComplete();

    final InOrder order = inOrder(mockUser, userRepository);
    // userService should query the repository
    order
      .verify(userRepository, times(1))
      .findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID);
    // and then extract the key
    order.verify(mockUser, times(1)).getKey();
  }

  @Test
  void getKeyById_NoKeyExists_GeneratesKey() {
    // Establish a random key
    final byte[] testRandomBytes = {
      -108,
      101,
      -7,
      -36,
      17,
      -26,
      -24,
      0,
      -32,
      -117,
      75,
      -127,
      22,
      62,
      9,
      19,
    };

    // This user does not have a key
    final UserEntity mockUserWithoutKey = mock(UserEntity.class);
    when(mockUserWithoutKey.getKey()).thenReturn(null);

    // When that user is given a key, return an entity that has the key
    final UserEntity mockUserWithKey = mock(UserEntity.class);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUserWithoutKey));
    when(mockUserWithoutKey.getKey()).thenReturn(null);

    when(mockUserWithoutKey.withKey(testRandomBytes))
      .thenReturn(mockUserWithKey);

    // Set up the mock repository
    when(userRepository.save(mockUserWithKey))
      .thenReturn(Mono.just(mockUserWithKey));

    // Set up the mock key service
    when(keyService.generateRandomBytes(16)).thenReturn(testRandomBytes);

    when(mockUserWithKey.getKey()).thenReturn(testRandomBytes);

    StepVerifier
      .create(userService.findKeyById(TestConstants.TEST_USER_ID))
      .expectNext(testRandomBytes)
      .verifyComplete();

    verify(userRepository, times(1))
      .findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID);
    verify(mockUserWithoutKey, times(1)).getKey();
    verify(keyService, times(1)).generateRandomBytes(16);
    verify(mockUserWithoutKey, times(1)).withKey(testRandomBytes);
    verify(mockUserWithKey, times(1)).getKey();

    ArgumentCaptor<UserEntity> argument = ArgumentCaptor.forClass(
      UserEntity.class
    );

    verify(userRepository, times(1)).save(argument.capture());

    assertThat(argument.getValue()).isEqualTo(mockUserWithKey);
    assertThat(argument.getValue().getKey()).isEqualTo(testRandomBytes);
  }

  @Test
  @DisplayName("Can promote user that already is administrator")
  void promote_UserIsAdmin_StaysAdmin() {
    final String mockAuthId = "Authid";

    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(auth -> {
        if (auth.getArgument(0, UserEntity.class).equals(mockUser)) {
          // We are saving the admin auth to db
          return Mono.just(auth.getArgument(0, UserEntity.class));
        } else {
          // We are saving the newly created auth to db
          return Mono.just(
            auth.getArgument(0, UserEntity.class).withId(mockAuthId)
          );
        }
      });

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUser));
    when(mockUser.withAdmin(true)).thenReturn(mockUser);

    StepVerifier
      .create(userService.promote(TestConstants.TEST_USER_ID))
      .verifyComplete();

    verify(passwordAuthRepository, never()).findByUserId(any(String.class));
    verify(userRepository, times(1))
      .findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID);
    verify(mockUser, times(1)).withAdmin(true);
    verify(userRepository, times(1)).save(mockUser);
  }

  @Test
  void setClassId_NonExistentClassId_ReturnsClassIdNotFoundException() {
    final String mockClassId = "classid";

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUser));
    when(classService.existsById(mockClassId)).thenReturn(Mono.just(false));

    StepVerifier
      .create(userService.setClassId(TestConstants.TEST_USER_ID, mockClassId))
      .expectError(ClassIdNotFoundException.class)
      .verify();

    verify(userRepository, times(1))
      .findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID);
    verify(classService, times(1)).existsById(mockClassId);
  }

  @Test
  void setClassId_ValidClassId_Succeeds() {
    final String mockClassId = "classid";
    final UserEntity mockUserWithClass = mock(UserEntity.class);

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUser));
    when(classService.existsById(mockClassId)).thenReturn(Mono.just(true));

    when(mockUser.withClassId(mockClassId)).thenReturn(mockUserWithClass);
    when(userRepository.save(mockUserWithClass))
      .thenReturn(Mono.just(mockUserWithClass));
    when(mockUserWithClass.getClassId()).thenReturn(mockClassId);

    StepVerifier
      .create(userService.setClassId(TestConstants.TEST_USER_ID, mockClassId))
      .expectNextMatches(user -> user.getClassId() == mockClassId)
      .verifyComplete();

    verify(userRepository, times(1))
      .findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID);
    verify(classService, times(1)).existsById(mockClassId);

    verify(mockUser, times(1)).withClassId(mockClassId);
    verify(userRepository, times(1)).save(mockUserWithClass);
    verify(mockUserWithClass, times(1)).getClassId();
  }

  private void setClock(final Clock testClock) {
    when(clock.instant()).thenReturn(testClock.instant());
    when(clock.getZone()).thenReturn(testClock.getZone());
  }

  @Test
  @DisplayName(
    "Can return exception when setting display name of user id that does not exist"
  )
  void setDisplayName_UserIdDoesNotExist_ReturnsUserIdNotFoundException() {
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.empty());

    StepVerifier
      .create(
        userService.setDisplayName(
          TestConstants.TEST_USER_ID,
          TestConstants.TEST_USER_DISPLAY_NAME
        )
      )
      .expectError(UserNotFoundException.class)
      .verify();
    verify(userRepository, times(1))
      .findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can change display name")
  void setDisplayName_ValidDisplayName_DisplayNameIsSet() {
    String newDisplayName = "newDisplayName";

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUser));
    when(userRepository.findByDisplayName(newDisplayName))
      .thenReturn(Mono.empty());

    when(mockUser.withDisplayName(newDisplayName)).thenReturn(mockUser);
    when(userRepository.save(any(UserEntity.class)))
      .thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn(newDisplayName);

    StepVerifier
      .create(
        userService.setDisplayName(TestConstants.TEST_USER_ID, newDisplayName)
      )
      .expectNextMatches(user -> user.getDisplayName().equals(newDisplayName))
      .as("Display name should change to supplied value")
      .verifyComplete();

    verify(userRepository, times(1))
      .findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID);
    verify(userRepository, times(1)).findByDisplayName(newDisplayName);

    verify(mockUser, times(1)).withDisplayName(newDisplayName);
    verify(userRepository, times(1)).save(any(UserEntity.class));
    verify(mockUser, times(1)).getDisplayName();
  }

  @BeforeEach
  void setup() {
    // Set up the system under test
    userService =
      new UserService(
        userRepository,
        teamRepository,
        passwordAuthRepository,
        classService,
        keyService,
        webTokenKeyManager,
        clock
      );

    mockPasswordAuth = mock(PasswordAuth.class);
    mockUser = mock(UserEntity.class);
  }
}
