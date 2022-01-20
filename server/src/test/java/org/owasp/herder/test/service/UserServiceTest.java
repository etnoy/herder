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
import org.owasp.herder.authentication.UserAuth;
import org.owasp.herder.authentication.UserAuthRepository;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.exception.ClassIdNotFoundException;
import org.owasp.herder.exception.DuplicateUserDisplayNameException;
import org.owasp.herder.exception.DuplicateUserLoginNameException;
import org.owasp.herder.exception.InvalidClassIdException;
import org.owasp.herder.exception.InvalidUserIdException;
import org.owasp.herder.exception.UserIdNotFoundException;
import org.owasp.herder.service.ClassService;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.User;
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

  @Mock private UserAuthRepository userAuthRepository;

  @Mock private PasswordAuthRepository passwordAuthRepository;

  @Mock private ClassService classService;

  @Mock private KeyService keyService;

  @Mock private WebTokenKeyManager webTokenKeyManager;

  @Test
  void authenticate_EmptyPassword_ReturnsIllegalArgumentException() {
    StepVerifier.create(userService.authenticate("username", ""))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void authenticate_EmptyUsername_ReturnsIllegalArgumentException() {
    StepVerifier.create(userService.authenticate("", "password"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

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
  void authenticate_NullPassword_ReturnsNullPointerException() {
    StepVerifier.create(userService.authenticate("username", null))
        .expectError(NullPointerException.class)
        .verify();
  }

  @Test
  void authenticate_NullUsername_ReturnsNullPointerException() {
    StepVerifier.create(userService.authenticate(null, "password"))
        .expectError(NullPointerException.class)
        .verify();
  }

  @Test
  void authenticate_ValidUsernameAndPassword_Authenticates() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(16);
    final Long mockedUserId = 614L;
    final String mockedLoginName = "MockUser";
    final String mockedPassword = "MockPassword";
    final String mockedPasswordHash = encoder.encode(mockedPassword);
    final PasswordAuth mockedPasswordAuth = mock(PasswordAuth.class);
    final UserAuth mockedUserAuth = mock(UserAuth.class);

    when(passwordAuthRepository.findByLoginName(mockedLoginName))
        .thenReturn(Mono.just(mockedPasswordAuth));
    when(mockedPasswordAuth.getHashedPassword()).thenReturn(mockedPasswordHash);
    when(mockedPasswordAuth.getUserId()).thenReturn(mockedUserId);
    when(userAuthRepository.findByUserId(mockedUserId)).thenReturn(Mono.just(mockedUserAuth));
    when(mockedUserAuth.isEnabled()).thenReturn(true);

    when(mockedUserAuth.getSuspendedUntil()).thenReturn(null);

    StepVerifier.create(userService.authenticate(mockedLoginName, mockedPassword))
        .assertNext(
            authResponse -> {
              assertThat(authResponse.getUserId()).isEqualTo(mockedUserId);
              assertThat(authResponse.getUserName()).isEqualTo(mockedLoginName);
            })
        .expectComplete()
        .verify();
    verify(passwordAuthRepository, times(1)).findByLoginName(mockedLoginName);
  }

  @Test
  void authenticate_ValidUsernameAndPasswordButUserNotEnabled_DoesNotAuthenticates() {
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(16);
    final Long mockedUserId = 614L;
    final String mockedLoginName = "MockUser";
    final String mockedPassword = "MockPassword";
    final String mockedPasswordHash = encoder.encode(mockedPassword);
    final PasswordAuth mockedPasswordAuth = mock(PasswordAuth.class);
    final UserAuth mockedUserAuth = mock(UserAuth.class);

    when(passwordAuthRepository.findByLoginName(mockedLoginName))
        .thenReturn(Mono.just(mockedPasswordAuth));
    when(mockedPasswordAuth.getHashedPassword()).thenReturn(mockedPasswordHash);
    when(mockedPasswordAuth.getUserId()).thenReturn(mockedUserId);
    when(userAuthRepository.findByUserId(mockedUserId)).thenReturn(Mono.just(mockedUserAuth));
    when(mockedUserAuth.isEnabled()).thenReturn(false);

    LocalDateTime longAgo = LocalDateTime.MIN;
    when(mockedUserAuth.getSuspendedUntil()).thenReturn(longAgo);

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
    final Long mockedUserId = 614L;
    final String mockedLoginName = "MockUser";
    final String mockedPassword = "MockPassword";
    final String mockedPasswordHash = encoder.encode(mockedPassword);
    final PasswordAuth mockedPasswordAuth = mock(PasswordAuth.class);
    final UserAuth mockedUserAuth = mock(UserAuth.class);

    when(passwordAuthRepository.findByLoginName(mockedLoginName))
        .thenReturn(Mono.just(mockedPasswordAuth));
    when(mockedPasswordAuth.getHashedPassword()).thenReturn(mockedPasswordHash);
    when(mockedPasswordAuth.getUserId()).thenReturn(mockedUserId);
    when(userAuthRepository.findByUserId(mockedUserId)).thenReturn(Mono.just(mockedUserAuth));
    when(mockedUserAuth.isEnabled()).thenReturn(true);

    LocalDateTime futureDate = LocalDateTime.MAX;
    when(mockedUserAuth.getSuspendedUntil()).thenReturn(futureDate);

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
    StepVerifier.create(userService.count()).expectNext(mockedUserCount).expectComplete().verify();
    verify(userRepository, times(1)).count();
  }

  @Test
  @DisplayName("create() must return exception if display name already exists")
  void create_DisplayNameAlreadyExists_ReturnsDuplicateUserDisplayNameException() {
    final String displayName = "createPasswordUser_DuplicateDisplayName";

    final User mockUser = mock(User.class);

    when(userRepository.findByDisplayName(displayName)).thenReturn(Mono.just(mockUser));

    StepVerifier.create(userService.create(displayName))
        .expectError(DuplicateUserDisplayNameException.class)
        .verify();

    verify(userRepository, times(1)).findByDisplayName(displayName);
  }

  @Test
  @DisplayName("create() must return exception if display name already exists")
  void create_EmptyArgument_ReturnsIllegalArgumentException() {
    StepVerifier.create(userService.create(""))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void create_NullArgument_ReturnsNullPointerException() {
    StepVerifier.create(userService.create(null)).expectError(NullPointerException.class).verify();
  }

  @Test
  void create_ValidDisplayName_CreatesUser() {
    final String displayName = "TestUser";
    final Long mockUserId = 651L;

    when(userRepository.findByDisplayName(displayName)).thenReturn(Mono.empty());

    final byte[] testRandomBytes = {
      -108, 101, -7, -36, 17, -26, -24, 0, -32, -117, 75, -127, 22, 62, 9, 19
    };

    when(keyService.generateRandomBytes(16)).thenReturn(testRandomBytes);

    when(userRepository.save(any(User.class)))
        .thenAnswer(user -> Mono.just(user.getArgument(0, User.class).withId(mockUserId)));

    StepVerifier.create(userService.create(displayName))
        .expectNext(mockUserId)
        .expectComplete()
        .verify();

    verify(userRepository, times(1)).findByDisplayName(displayName);
    verify(userRepository, times(1)).save(any(User.class));

    ArgumentCaptor<User> argument = ArgumentCaptor.forClass(User.class);
    verify(userRepository, times(1)).save(argument.capture());
    assertThat(argument.getValue().getDisplayName()).isEqualTo(displayName);
  }

  @Test
  void createPasswordUser_DuplicateDisplayName_ReturnsDuplicateUserDisplayNameException() {
    final String displayName = "createPasswordUser_DuplicateDisplayName";
    final String loginName = "_createPasswordUser_DuplicateDisplayName_";

    final String hashedPassword = "aPasswordHash";

    final User mockUser = mock(User.class);

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
  void createPasswordUser_EmptyDisplayName_ReturnsIllegalArgumentException() {
    StepVerifier.create(userService.createPasswordUser("", "loginName", "passwordHash"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void createPasswordUser_EmptyLoginName_ReturnsIllegalArgumentException() {
    StepVerifier.create(userService.createPasswordUser("displayName", "", "passwordHash"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void createPasswordUser_EmptyPasswordHash_ReturnsIllegalArgumentException() {
    StepVerifier.create(userService.createPasswordUser("displayName", "loginName", ""))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void createPasswordUser_NullDisplayName_ReturnsNullPointerException() {
    StepVerifier.create(userService.createPasswordUser(null, "loginName", "passwordHash"))
        .expectError(NullPointerException.class)
        .verify();
  }

  @Test
  void createPasswordUser_NullLoginName_ReturnsNullPointerException() {
    StepVerifier.create(userService.createPasswordUser("displayName", null, "passwordHash"))
        .expectError(NullPointerException.class)
        .verify();
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

    final Long mockUserId = 199L;

    final byte[] testRandomBytes = {
      -108, 101, -7, -36, 17, -26, -24, 0, -32, -117, 75, -127, 22, 62, 9, 19
    };
    when(keyService.generateRandomBytes(16)).thenReturn(testRandomBytes);

    when(passwordAuthRepository.findByLoginName(loginName)).thenReturn(Mono.empty());
    when(userRepository.findByDisplayName(displayName)).thenReturn(Mono.empty());

    when(userRepository.save(any(User.class)))
        .thenAnswer(user -> Mono.just(user.getArgument(0, User.class).withId(mockUserId)));

    when(userAuthRepository.save(any(UserAuth.class)))
        .thenAnswer(user -> Mono.just(user.getArgument(0, UserAuth.class).withId(mockUserId)));

    when(passwordAuthRepository.save(any(PasswordAuth.class)))
        .thenAnswer(user -> Mono.just(user.getArgument(0, PasswordAuth.class).withId(mockUserId)));

    StepVerifier.create(userService.createPasswordUser(displayName, loginName, hashedPassword))
        .expectNext(mockUserId)
        .expectComplete()
        .verify();

    verify(passwordAuthRepository, times(1)).findByLoginName(loginName);
    verify(userRepository, times(1)).findByDisplayName(displayName);

    verify(userRepository, times(1)).save(any(User.class));
    verify(userAuthRepository, times(1)).save(any(UserAuth.class));
    verify(passwordAuthRepository, times(1)).save(any(PasswordAuth.class));

    ArgumentCaptor<User> userArgumentCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository, times(1)).save(userArgumentCaptor.capture());
    assertThat(userArgumentCaptor.getValue().getId()).isNull();
    assertThat(userArgumentCaptor.getValue().getDisplayName()).isEqualTo(displayName);

    ArgumentCaptor<UserAuth> userAuthArgumentCaptor = ArgumentCaptor.forClass(UserAuth.class);
    verify(userAuthRepository, times(1)).save(userAuthArgumentCaptor.capture());
    assertThat(userAuthArgumentCaptor.getValue().getUserId()).isEqualTo(mockUserId);

    ArgumentCaptor<PasswordAuth> passwordAuthArgumentCaptor =
        ArgumentCaptor.forClass(PasswordAuth.class);
    verify(passwordAuthRepository, times(1)).save(passwordAuthArgumentCaptor.capture());
    assertThat(passwordAuthArgumentCaptor.getValue().getUserId()).isEqualTo(mockUserId);
    assertThat(passwordAuthArgumentCaptor.getValue().getLoginName()).isEqualTo(loginName);
    assertThat(passwordAuthArgumentCaptor.getValue().getHashedPassword()).isEqualTo(hashedPassword);
  }

  @Test
  void deleteById_InvalidUserId_ReturnsInvalidUserIdException() {
    for (final Long userId : TestConstants.INVALID_IDS) {
      StepVerifier.create(userService.deleteById(userId))
          .expectError(InvalidUserIdException.class)
          .verify();
    }
  }

  @Test
  void deleteById_ValidUserId_CallsRepository() {
    final long mockUserId = 358L;
    when(passwordAuthRepository.deleteByUserId(mockUserId)).thenReturn(Mono.empty());
    when(userAuthRepository.deleteByUserId(mockUserId)).thenReturn(Mono.empty());
    when(userRepository.deleteById(mockUserId)).thenReturn(Mono.empty());

    StepVerifier.create(userService.deleteById(mockUserId)).expectComplete().verify();

    // Order of deletion is important due to RDBMS constraints
    final InOrder deletionOrder =
        inOrder(passwordAuthRepository, userAuthRepository, userRepository);
    deletionOrder.verify(passwordAuthRepository, times(1)).deleteByUserId(mockUserId);
    deletionOrder.verify(userAuthRepository, times(1)).deleteByUserId(mockUserId);
    deletionOrder.verify(userRepository, times(1)).deleteById(mockUserId);
  }

  @Test
  void demote_InvalidUserId_ReturnsInvalidUserIdException() {
    for (final Long userId : TestConstants.INVALID_IDS) {
      StepVerifier.create(userService.demote(userId))
          .expectError(InvalidUserIdException.class)
          .verify();
    }
  }

  @Test
  void demote_UserIsAdmin_Demoted() {
    final long mockUserId = 933;
    final long mockAuthId = 46;

    final UserAuth mockAuth = mock(UserAuth.class);
    final UserAuth mockDemotedAuth = mock(UserAuth.class);

    when(userAuthRepository.save(any(UserAuth.class)))
        .thenAnswer(
            auth -> {
              if (auth.getArgument(0, UserAuth.class) == mockDemotedAuth) {
                // We are saving the admin auth to db
                return Mono.just(auth.getArgument(0, UserAuth.class));
              } else {
                // We are saving the newly created auth to db
                return Mono.just(auth.getArgument(0, UserAuth.class).withId(mockAuthId));
              }
            });

    when(userAuthRepository.findByUserId(mockUserId)).thenReturn(Mono.just(mockAuth));

    when(mockAuth.withAdmin(false)).thenReturn(mockDemotedAuth);

    Mockito.doNothing().when(webTokenKeyManager).invalidateAccessToken(mockUserId);

    StepVerifier.create(userService.demote(mockUserId)).expectComplete().verify();

    verify(userRepository, never()).findById(any(Long.class));
    verify(userRepository, never()).save(any(User.class));
    verify(passwordAuthRepository, never()).findByUserId(any(Long.class));

    verify(userAuthRepository, times(1)).findByUserId(mockUserId);

    verify(mockAuth, times(1)).withAdmin(false);
    verify(userAuthRepository, never()).save(mockAuth);
    verify(userAuthRepository, times(1)).save(mockDemotedAuth);
    verify(webTokenKeyManager, times(1)).invalidateAccessToken(mockUserId);
  }

  @Test
  void demote_UserIsNotAdmin_StaysNotAdmin() {
    final long mockUserId = 933;
    final long mockAuthId = 80;

    final UserAuth mockAuth = mock(UserAuth.class);

    when(userAuthRepository.save(any(UserAuth.class)))
        .thenAnswer(
            auth -> {
              if (auth.getArgument(0, UserAuth.class) == mockAuth) {
                // We are saving the admin auth to db
                return Mono.just(auth.getArgument(0, UserAuth.class));
              } else {
                // We are saving the newly created auth to db
                return Mono.just(auth.getArgument(0, UserAuth.class).withId(mockAuthId));
              }
            });

    when(userAuthRepository.findByUserId(mockUserId)).thenReturn(Mono.just(mockAuth));

    when(mockAuth.withAdmin(false)).thenReturn(mockAuth);

    StepVerifier.create(userService.demote(mockUserId)).expectComplete().verify();

    verify(userRepository, never()).findById(any(Long.class));
    verify(userRepository, never()).save(any(User.class));
    verify(passwordAuthRepository, never()).findByUserId(any(Integer.class));
    verify(passwordAuthRepository, never()).save(any(PasswordAuth.class));

    verify(userAuthRepository, times(1)).save(mockAuth);

    verify(userRepository, never()).findById(any(Long.class));
    verify(userAuthRepository, times(1)).findByUserId(mockUserId);

    verify(mockAuth, times(1)).withAdmin(false);
  }

  @Test
  void findAll_NoUsersExist_ReturnsEmpty() {
    when(userRepository.findAll()).thenReturn(Flux.empty());
    StepVerifier.create(userService.findAll()).expectComplete().verify();
    verify(userRepository, times(1)).findAll();
  }

  @Test
  void findAll_UsersExist_ReturnsUsers() {
    final User mockUser1 = mock(User.class);
    final User mockUser2 = mock(User.class);
    final User mockUser3 = mock(User.class);

    when(userRepository.findAll()).thenReturn(Flux.just(mockUser1, mockUser2, mockUser3));

    StepVerifier.create(userService.findAll())
        .expectNext(mockUser1)
        .expectNext(mockUser2)
        .expectNext(mockUser3)
        .expectComplete()
        .verify();

    verify(userRepository, times(1)).findAll();
  }

  @Test
  void findById_ExistingUserId_ReturnsUserEntity() {
    final User mockUser = mock(User.class);

    final long mockUserId = 910;

    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUser));

    StepVerifier.create(userService.findById(mockUserId))
        .expectNext(mockUser)
        .expectComplete()
        .verify();

    verify(userRepository, times(1)).findById(mockUserId);
  }

  @Test
  void findById_InvalidUserId_ReturnsInvalidUserIdException() {
    for (final long userId : TestConstants.INVALID_IDS) {
      StepVerifier.create(userService.findById(userId))
          .expectError(InvalidUserIdException.class)
          .verify();
    }
  }

  @Test
  void findById_NonExistentUserId_ReturnsEmpty() {
    final long nonExistentUserId = 248;
    when(userRepository.findById(nonExistentUserId)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findById(nonExistentUserId)).expectComplete().verify();
    verify(userRepository, times(1)).findById(nonExistentUserId);
  }

  @Test
  void findByLoginName_EmptyLoginName_ReturnsIllegalArgumentException() {
    StepVerifier.create(userService.findUserIdByLoginName(""))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void findByLoginName_LoginNameDoesNotExist_ReturnsEmptyMono() {
    final String nonExistentLoginName = "NonExistentUser";
    when(passwordAuthRepository.findByLoginName(nonExistentLoginName)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findUserIdByLoginName(nonExistentLoginName))
        .expectComplete()
        .verify();
    verify(passwordAuthRepository, times(1)).findByLoginName(nonExistentLoginName);
  }

  @Test
  void findByLoginName_NullLoginName_ReturnsNullPointerException() {
    StepVerifier.create(userService.findUserIdByLoginName(null))
        .expectError(NullPointerException.class)
        .verify();
  }

  @Test
  void findByLoginName_UserExists_ReturnsUser() {
    final PasswordAuth mockPasswordAuth = mock(PasswordAuth.class);
    final String loginName = "MockUser";
    final long mockUserId = 117;

    when(passwordAuthRepository.findByLoginName(loginName)).thenReturn(Mono.just(mockPasswordAuth));
    when(mockPasswordAuth.getUserId()).thenReturn(mockUserId);

    StepVerifier.create(userService.findUserIdByLoginName(loginName))
        .expectNext(mockUserId)
        .expectComplete()
        .verify();
    verify(passwordAuthRepository, times(1)).findByLoginName(loginName);
  }

  @Test
  void findDisplayNameById_InvalidUserId_ReturnsInvalidUserIdExceptio() {
    for (final long userId : TestConstants.INVALID_IDS) {
      StepVerifier.create(userService.findDisplayNameById(userId))
          .expectError(InvalidUserIdException.class)
          .verify();
    }
  }

  @Test
  void findDisplayNameById_NoUserExists_ReturnsEmpty() {
    final long mockUserId = 294;
    when(userRepository.findById(mockUserId)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findDisplayNameById(mockUserId)).expectComplete().verify();
    verify(userRepository, times(1)).findById(mockUserId);
  }

  @Test
  void findDisplayNameById_UserExists_ReturnsDisplayName() {
    final User mockUser = mock(User.class);
    final long mockUserId = 490;
    final String mockDisplayName = "mockDisplayName";

    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn(mockDisplayName);

    StepVerifier.create(userService.findDisplayNameById(mockUserId))
        .expectNext(mockDisplayName)
        .expectComplete()
        .verify();

    verify(userRepository, times(1)).findById(mockUserId);
    verify(mockUser, times(1)).getDisplayName();
  }

  @Test
  void findPasswordAuthByLoginName_EmptyLoginName_ReturnsIllegalArgumentException() {
    StepVerifier.create(userService.findPasswordAuthByLoginName(""))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void findPasswordAuthByLoginName_ExistingLoginName_ReturnsPasswordAuth() {
    final PasswordAuth mockPasswordAuth = mock(PasswordAuth.class);

    final String mockLoginName = "loginName";
    when(passwordAuthRepository.findByLoginName(mockLoginName))
        .thenReturn(Mono.just(mockPasswordAuth));

    StepVerifier.create(userService.findPasswordAuthByLoginName(mockLoginName))
        .expectNext(mockPasswordAuth)
        .expectComplete()
        .verify();

    verify(passwordAuthRepository, times(1)).findByLoginName(mockLoginName);
  }

  @Test
  void findPasswordAuthByLoginName_NonExistentLoginName_ReturnsEmpty() {
    final String mockLoginName = "loginName";
    when(passwordAuthRepository.findByLoginName(mockLoginName)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findPasswordAuthByLoginName(mockLoginName))
        .expectComplete()
        .verify();
    verify(passwordAuthRepository, times(1)).findByLoginName(mockLoginName);
  }

  @Test
  void findPasswordAuthByLoginName_NullLoginName_ReturnsNullPointerException() {
    StepVerifier.create(userService.findPasswordAuthByLoginName(null))
        .expectError(NullPointerException.class)
        .verify();
  }

  @Test
  void findPasswordAuthByUserId_InvalidUserId_ReturnsInvalidUserIdException() {
    for (final long userId : TestConstants.INVALID_IDS) {
      StepVerifier.create(userService.findPasswordAuthByUserId(userId))
          .expectError(InvalidUserIdException.class)
          .verify();
    }
  }

  @Test
  void findPasswordAuthByUserId_NoPasswordAuthExists_ReturnsEmpty() {
    final long mockUserId = 999;

    when(passwordAuthRepository.findByUserId(mockUserId)).thenReturn(Mono.empty());

    StepVerifier.create(userService.findPasswordAuthByUserId(mockUserId)).expectComplete().verify();

    verify(passwordAuthRepository, times(1)).findByUserId(mockUserId);
  }

  @Test
  void findPasswordAuthByUserId_PasswordAuthExists_ReturnsPasswordAuth() {
    final PasswordAuth mockPasswordAuth = mock(PasswordAuth.class);
    final long mockUserId = 974;

    when(passwordAuthRepository.findByUserId(mockUserId)).thenReturn(Mono.just(mockPasswordAuth));

    StepVerifier.create(userService.findPasswordAuthByUserId(mockUserId))
        .expectNext(mockPasswordAuth)
        .expectComplete()
        .verify();

    verify(passwordAuthRepository, times(1)).findByUserId(mockUserId);
  }

  @Test
  void findUserAuthByUserId_ExistingUserId_ReturnsUserAuth() {
    final UserAuth mockUserAuth = mock(UserAuth.class);

    final long mockUserId = 841;

    when(userAuthRepository.findByUserId(mockUserId)).thenReturn(Mono.just(mockUserAuth));

    StepVerifier.create(userService.findUserAuthByUserId(mockUserId))
        .expectNext(mockUserAuth)
        .expectComplete()
        .verify();

    verify(userAuthRepository, times(1)).findByUserId(mockUserId);
    verify(userRepository, never()).findById(any(Long.class));
    verify(passwordAuthRepository, never()).findByUserId(any(Integer.class));
  }

  @Test
  void findUserAuthByUserId_InvalidUserId_ReturnsInvalidUserIdException() {
    for (final long userId : TestConstants.INVALID_IDS) {
      StepVerifier.create(userService.findUserAuthByUserId(userId))
          .expectError(InvalidUserIdException.class)
          .verify();
    }
  }

  @Test
  void findUserAuthByUserId_NonExistentUserId_ReturnsEmpty() {
    final long nonExistentUserId = 547;
    when(userAuthRepository.findByUserId(nonExistentUserId)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findUserAuthByUserId(nonExistentUserId))
        .expectComplete()
        .verify();
    verify(userAuthRepository, times(1)).findByUserId(nonExistentUserId);
  }

  @Test
  void getKeyById_InvalidUserId_ReturnsInvalidUserIdException() {
    for (final long userId : TestConstants.INVALID_IDS) {
      StepVerifier.create(userService.findKeyById(userId))
          .expectError(InvalidUserIdException.class)
          .verify();
    }
  }

  @Test
  void getKeyById_KeyExists_ReturnsKey() {
    // Establish a random key
    final byte[] testRandomBytes = {
      -108, 101, -7, -36, 17, -26, -24, 0, -32, -117, 75, -127, 22, 62, 9, 19
    };
    final long mockUserId = 17;

    // Mock a test user that has a key
    final User mockUserWithKey = mock(User.class);
    when(mockUserWithKey.getKey()).thenReturn(testRandomBytes);

    when(userRepository.existsById(mockUserId)).thenReturn(Mono.just(true));
    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUserWithKey));

    StepVerifier.create(userService.findKeyById(mockUserId))
        .expectNext(testRandomBytes)
        .expectComplete()
        .verify();

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
    final long mockUserId = 19;

    // This user does not have a key
    final User mockUserWithoutKey = mock(User.class);
    when(mockUserWithoutKey.getKey()).thenReturn(null);

    // When that user is given a key, return an entity that has the key
    final User mockUserWithKey = mock(User.class);
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
        .expectComplete()
        .verify();

    verify(userRepository, times(1)).findById(mockUserId);
    verify(mockUserWithoutKey, times(1)).getKey();
    verify(keyService, times(1)).generateRandomBytes(16);
    verify(mockUserWithoutKey, times(1)).withKey(testRandomBytes);
    verify(mockUserWithKey, times(1)).getKey();

    ArgumentCaptor<User> argument = ArgumentCaptor.forClass(User.class);

    verify(userRepository, times(1)).save(argument.capture());

    assertThat(argument.getValue()).isEqualTo(mockUserWithKey);
    assertThat(argument.getValue().getKey()).isEqualTo(testRandomBytes);
  }

  @Test
  void promote_InvalidUserId_ReturnsInvalidUserIdException() {
    for (final long userId : TestConstants.INVALID_IDS) {
      StepVerifier.create(userService.promote(userId))
          .expectError(InvalidUserIdException.class)
          .verify();
    }
  }

  @Test
  void promote_UserIsAdmin_StaysAdmin() {
    final long mockUserId = 899;
    final long mockAuthId = 551;

    final UserAuth mockAuth = mock(UserAuth.class);

    when(userAuthRepository.save(any(UserAuth.class)))
        .thenAnswer(
            auth -> {
              if (auth.getArgument(0, UserAuth.class).equals(mockAuth)) {
                // We are saving the admin auth to db
                return Mono.just(auth.getArgument(0, UserAuth.class));
              } else {
                // We are saving the newly created auth to db
                return Mono.just(auth.getArgument(0, UserAuth.class).withId(mockAuthId));
              }
            });

    when(userAuthRepository.findByUserId(mockUserId)).thenReturn(Mono.just(mockAuth));

    when(mockAuth.withAdmin(true)).thenReturn(mockAuth);

    StepVerifier.create(userService.promote(mockUserId)).expectComplete().verify();

    verify(userRepository, never()).findById(any(Long.class));
    verify(userRepository, never()).save(any(User.class));
    verify(passwordAuthRepository, never()).findByUserId(any(Integer.class));

    verify(userAuthRepository, times(1)).findByUserId(mockUserId);

    verify(mockAuth, times(1)).withAdmin(true);
    verify(userAuthRepository, times(1)).save(mockAuth);
  }

  @Test
  void setClassId_InvalidClassId_ReturnsInvalidClassIdException() {
    for (final long classId : TestConstants.INVALID_IDS) {
      StepVerifier.create(userService.setClassId(10L, classId))
          .expectError(InvalidClassIdException.class)
          .verify();
    }
  }

  @Test
  void setClassId_InvalidUserId_ReturnsInvalidUserIdException() {
    for (final long userId : TestConstants.INVALID_IDS) {
      StepVerifier.create(userService.setClassId(userId, 61))
          .expectError(InvalidUserIdException.class)
          .verify();
    }
  }

  @Test
  void setClassId_NonExistentClassId_ReturnsClassIdNotFoundException() {
    final long mockUserId = 16;
    final long mockClassId = 638;

    User mockUser = mock(User.class);

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
    final long mockUserId = 875;
    final long mockClassId = 213;

    final User mockUser = mock(User.class);
    final User mockUserWithClass = mock(User.class);

    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUser));
    when(classService.existsById(mockClassId)).thenReturn(Mono.just(true));

    when(mockUser.withClassId(mockClassId)).thenReturn(mockUserWithClass);
    when(userRepository.save(mockUserWithClass)).thenReturn(Mono.just(mockUserWithClass));
    when(mockUserWithClass.getClassId()).thenReturn(mockClassId);

    StepVerifier.create(userService.setClassId(mockUserId, mockClassId))
        .expectNextMatches(user -> user.getClassId() == mockClassId)
        .expectComplete()
        .verify();

    verify(userRepository, times(1)).findById(mockUserId);
    verify(classService, times(1)).existsById(mockClassId);

    verify(mockUser, times(1)).withClassId(mockClassId);
    verify(userRepository, times(1)).save(mockUserWithClass);
    verify(mockUserWithClass, times(1)).getClassId();
  }

  @Test
  void setDisplayName_EmptyDisplayName_ReturnsIllegalArgumentException() {
    StepVerifier.create(userService.setDisplayName(725, ""))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void setDisplayName_InvalidUserId_ReturnsInvalidUserIdException() {
    for (final long userId : TestConstants.INVALID_IDS) {
      StepVerifier.create(userService.setDisplayName(userId, "displayName"))
          .expectError(InvalidUserIdException.class)
          .verify();
    }
  }

  @Test
  void setDisplayName_NullDisplayName_ReturnsNullPointerException() {
    StepVerifier.create(userService.setDisplayName(480, null))
        .expectError(NullPointerException.class)
        .verify();
  }

  @Test
  void setDisplayName_UserIdDoesNotExist_ReturnsUserIdNotFoundException() {
    final String newDisplayName = "newName";

    final long mockUserId = 550;

    when(userRepository.existsById(mockUserId)).thenReturn(Mono.just(false));

    StepVerifier.create(userService.setDisplayName(mockUserId, newDisplayName))
        .expectError(UserIdNotFoundException.class)
        .verify();
    verify(userRepository, times(1)).existsById(mockUserId);
  }

  @Test
  void setDisplayName_ValidArguments_DisplayNameIsSet() {
    User mockUser = mock(User.class);
    String newDisplayName = "newDisplayName";

    final long mockUserId = 652;

    when(userRepository.existsById(mockUserId)).thenReturn(Mono.just(true));
    when(userRepository.findById(mockUserId)).thenReturn(Mono.just(mockUser));
    when(userRepository.findByDisplayName(newDisplayName)).thenReturn(Mono.empty());

    when(mockUser.withDisplayName(newDisplayName)).thenReturn(mockUser);
    when(userRepository.save(any(User.class))).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn(newDisplayName);

    StepVerifier.create(userService.setDisplayName(mockUserId, newDisplayName))
        .expectNextMatches(user -> user.getDisplayName().equals(newDisplayName))
        .as("Display name should change to supplied value")
        .expectComplete()
        .verify();

    verify(userRepository, times(1)).existsById(mockUserId);
    verify(userRepository, times(1)).findById(mockUserId);
    verify(userRepository, times(1)).findByDisplayName(newDisplayName);

    verify(mockUser, times(1)).withDisplayName(newDisplayName);
    verify(userRepository, times(1)).save(any(User.class));
    verify(mockUser, times(1)).getDisplayName();
  }

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    userService =
        new UserService(
            userRepository,
            userAuthRepository,
            passwordAuthRepository,
            classService,
            keyService,
            webTokenKeyManager);
  }
}
