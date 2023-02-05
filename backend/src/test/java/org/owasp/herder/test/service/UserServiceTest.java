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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.PasswordAuth;
import org.owasp.herder.authentication.PasswordAuthRepository;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.exception.ClassIdNotFoundException;
import org.owasp.herder.exception.DuplicateUserDisplayNameException;
import org.owasp.herder.exception.DuplicateUserLoginNameException;
import org.owasp.herder.exception.UserNotFoundException;
import org.owasp.herder.scoring.SolverType;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.ClassService;
import org.owasp.herder.user.SolverEntity;
import org.owasp.herder.user.SolverEntity.SolverEntityBuilder;
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.TeamRepository;
import org.owasp.herder.user.TeamService;
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
  SubmissionRepository submissionRepository;

  @Mock
  ClassService classService;

  @Mock
  KeyService keyService;

  @Mock
  TeamService teamService;

  @Mock
  WebTokenKeyManager webTokenKeyManager;

  @Mock
  Clock clock;

  PasswordAuth mockPasswordAuth;

  UserEntity mockUser;

  TeamEntity mockTeam;

  @Test
  @DisplayName("Can fail authentication if username is invalid")
  void authenticate_InvalidUsername_ReturnsFalse() {
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)).thenReturn(Mono.empty());
    StepVerifier
      .create(userService.authenticate(TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD))
      .expectErrorMatches(throwable ->
        throwable instanceof AuthenticationException && throwable.getMessage().equals("Invalid username or password")
      )
      .verify();
  }

  @Test
  @DisplayName("Can fail authentication if user is suspended")
  void authenticate_UserSuspended_ReturnsLockedException() {
    final UserEntity testUser = TestConstants.TEST_USER_ENTITY.withEnabled(true).withSuspendedUntil(LocalDateTime.MAX);
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .thenReturn(Mono.just(mockPasswordAuth));
    when(mockPasswordAuth.getHashedPassword()).thenReturn(TestConstants.HASHED_TEST_PASSWORD);
    when(mockPasswordAuth.getUserId()).thenReturn(TestConstants.TEST_USER_ID);

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(testUser));

    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(userService.authenticate(TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD))
      .expectErrorMatches(throwable ->
        throwable instanceof LockedException &&
        throwable.getMessage().equals("Account suspended until +999999999-12-31T23:59:59.999999999")
      )
      .verify();
  }

  @Test
  @DisplayName("Can authenticate user when given valid credentials")
  void authenticate_ValidUsernameAndPassword_Authenticates() {
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .thenReturn(Mono.just(mockPasswordAuth));
    when(mockPasswordAuth.getHashedPassword()).thenReturn(TestConstants.HASHED_TEST_PASSWORD);
    when(mockPasswordAuth.getUserId()).thenReturn(TestConstants.TEST_USER_ID);
    when(mockUser.isEnabled()).thenReturn(true);
    when(mockUser.getDisplayName()).thenReturn(TestConstants.TEST_USER_DISPLAY_NAME);
    when(mockUser.getSuspendedUntil()).thenReturn(null);

    StepVerifier
      .create(userService.authenticate(TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD))
      .assertNext(authResponse -> {
        assertThat(authResponse.getUserId()).isEqualTo(TestConstants.TEST_USER_ID);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can fail authentication if credentials are correct but user is disabled")
  void authenticate_ValidUsernameAndPasswordButUserNotEnabled_ReturnsDisabledException() {
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .thenReturn(Mono.just(mockPasswordAuth));
    when(mockPasswordAuth.getHashedPassword()).thenReturn(TestConstants.HASHED_TEST_PASSWORD);
    when(mockPasswordAuth.getUserId()).thenReturn(TestConstants.TEST_USER_ID);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.isEnabled()).thenReturn(false);

    LocalDateTime longAgo = LocalDateTime.MIN;
    when(mockUser.getSuspendedUntil()).thenReturn(longAgo);

    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(userService.authenticate(TestConstants.TEST_USER_LOGIN_NAME, TestConstants.TEST_USER_PASSWORD))
      .expectErrorMatches(throwable ->
        throwable instanceof DisabledException && throwable.getMessage().equals("Account disabled")
      )
      .verify();
  }

  @Test
  @DisplayName("Can fail to add user to team if user already has a team")
  void addUserToTeam_UserAlreadyInTeam_ThrowsException() {
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getTeamId()).thenReturn(TestConstants.TEST_TEAM_ID);

    StepVerifier
      .create(userService.addUserToTeam(TestConstants.TEST_USER_ID, TestConstants.TEST_TEAM_ID))
      .expectErrorMatches(e ->
        e instanceof IllegalStateException && e.getMessage().equals("User already belongs to a team")
      )
      .verify();
  }

  @Test
  @DisplayName("Can add user to team")
  void addUserToTeam_UserNotInTeam_UserAddedToTeam() {
    final UserEntity userBeingAdded = UserEntity
      .builder()
      .displayName(TestConstants.TEST_USER_DISPLAY_NAME)
      .key(TestConstants.TEST_BYTE_ARRAY)
      .build();

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(userBeingAdded));

    when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> Mono.just(i.getArguments()[0]));

    StepVerifier
      .create(userService.addUserToTeam(TestConstants.TEST_USER_ID, TestConstants.TEST_TEAM_ID))
      .verifyComplete();

    final ArgumentCaptor<UserEntity> userEntityArgument = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userEntityArgument.capture());

    assertThat(userEntityArgument.getValue().getTeamId()).isEqualTo(TestConstants.TEST_TEAM_ID);
  }

  @Test
  @DisplayName("Can clear team id for user")
  void clearTeamForUser_UserExists_TeamIdSetToNull() {
    UserEntity mockUserWithoutTeam = mock(UserEntity.class);
    when(mockUser.withTeamId(null)).thenReturn(mockUserWithoutTeam);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(userRepository.save(mockUserWithoutTeam)).thenReturn(Mono.just(mockUserWithoutTeam));

    StepVerifier
      .create(userService.clearTeamForUser(TestConstants.TEST_USER_ID))
      .expectNext(mockUserWithoutTeam)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can disable a user")
  void disable_ValidUserId_DisablesUser() {
    final UserEntity disabledMockUser = mock(UserEntity.class);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));

    when(mockUser.withEnabled(false)).thenReturn(disabledMockUser);
    when(userRepository.save(disabledMockUser)).thenReturn(Mono.just(disabledMockUser));
    StepVerifier.create(userService.disable(TestConstants.TEST_USER_ID)).verifyComplete();

    ArgumentCaptor<UserEntity> userArgumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userArgumentCaptor.capture());
    assertThat(userArgumentCaptor.getValue()).isEqualTo(disabledMockUser);

    verify(webTokenKeyManager).invalidateAccessToken(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can enable a user")
  void enable_ValidUserId_EnablesUser() {
    final UserEntity enabledMockUser = mock(UserEntity.class);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));

    when(mockUser.withEnabled(true)).thenReturn(enabledMockUser);
    when(userRepository.save(enabledMockUser)).thenReturn(Mono.just(enabledMockUser));
    StepVerifier.create(userService.enable(TestConstants.TEST_USER_ID)).verifyComplete();

    ArgumentCaptor<UserEntity> userArgumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userArgumentCaptor.capture());
    assertThat(userArgumentCaptor.getValue()).isEqualTo(enabledMockUser);

    verify(webTokenKeyManager).invalidateAccessToken(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can find all solvers if there are users but no teams")
  void findAllSolvers_OnlyUsers_ReturnsSolvers() {
    final UserEntity mockUser1 = mock(UserEntity.class);
    final UserEntity mockUser2 = mock(UserEntity.class);
    final UserEntity mockUser3 = mock(UserEntity.class);

    when(mockUser1.getDisplayName()).thenReturn("Test User 1");
    when(mockUser2.getDisplayName()).thenReturn("Test User 2");
    when(mockUser3.getDisplayName()).thenReturn("Test User 3");

    final SolverEntityBuilder solverEntityBuilder = SolverEntity.builder().solverType(SolverType.USER);

    final SolverEntity solver1 = solverEntityBuilder.displayName("Test User 1").build();
    final SolverEntity solver2 = solverEntityBuilder.displayName("Test User 2").build();
    final SolverEntity solver3 = solverEntityBuilder.displayName("Test User 3").build();

    when(teamRepository.findAll()).thenReturn(Flux.empty());
    when(userRepository.findAllByTeamId(null)).thenReturn(Flux.just(mockUser1, mockUser2, mockUser3));

    StepVerifier
      .create(userService.findAllSolvers())
      .recordWith(ArrayList::new)
      .thenConsumeWhile(x -> true)
      .consumeRecordedWith(solvers -> {
        assertThat(solvers).containsExactlyInAnyOrder(solver1, solver2, solver3);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can find all solvers if there are users and teams")
  void findAllSolvers_UsersAndTeams_ReturnsThem() {
    final UserEntity mockUser1 = mock(UserEntity.class);
    final UserEntity mockUser2 = mock(UserEntity.class);
    final UserEntity mockUser3 = mock(UserEntity.class);

    when(mockUser1.getDisplayName()).thenReturn("Test User 1");

    final TeamEntity mockTeam = mock(TeamEntity.class);
    when(mockTeam.getDisplayName()).thenReturn("Test Team");
    when(mockTeam.getId()).thenReturn(TestConstants.TEST_TEAM_ID);

    final SolverEntity user = SolverEntity.builder().displayName("Test User 1").solverType(SolverType.USER).build();

    final SolverEntity team = SolverEntity
      .builder()
      .id(TestConstants.TEST_TEAM_ID)
      .displayName("Test Team")
      .solverType(SolverType.TEAM)
      .members(new HashSet<>(Arrays.asList(mockUser2, mockUser3)))
      .build();

    when(userRepository.findAllByTeamId(null)).thenReturn(Flux.just(mockUser1));
    when(userRepository.findAllByTeamId(TestConstants.TEST_TEAM_ID)).thenReturn(Flux.just(mockUser2, mockUser3));
    when(teamRepository.findAll()).thenReturn(Flux.just(mockTeam));

    StepVerifier
      .create(userService.findAllSolvers())
      .recordWith(ArrayList::new)
      .thenConsumeWhile(x -> true)
      .consumeRecordedWith(solvers -> {
        assertThat(solvers).containsExactlyInAnyOrder(user, team);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can check if user exists by display name")
  void existsByDisplayName_DisplayNameExists_ReturnsTrue() {
    when(userRepository.findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME)).thenReturn(Mono.just(mockUser));
    StepVerifier
      .create(userService.existsByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME))
      .expectNext(true)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can check if user does not exist by display name")
  void existsByDisplayName_DisplayNameDoesNotExist_ReturnsFalse() {
    when(userRepository.findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME)).thenReturn(Mono.empty());
    StepVerifier
      .create(userService.existsByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME))
      .expectNext(false)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can check if user exists by id")
  void existsById_UserIdIxists_ReturnsTrue() {
    when(userRepository.findById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    StepVerifier.create(userService.existsById(TestConstants.TEST_USER_ID)).expectNext(true).verifyComplete();
  }

  @Test
  @DisplayName("Can check if user does not exist by id")
  void existsById_UserIdDoesNotExist_ReturnsFalse() {
    when(userRepository.findById(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());
    StepVerifier.create(userService.existsById(TestConstants.TEST_USER_ID)).expectNext(false).verifyComplete();
  }

  @Test
  @DisplayName("Can check if user exists by login name")
  void existsByLoginName_UserIdIxists_ReturnsTrue() {
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .thenReturn(Mono.just(mockPasswordAuth));
    StepVerifier
      .create(userService.existsByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .expectNext(true)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can check if user does not exist by login name")
  void existsByLoginName_UserIdDoesNotExist_ReturnsFalse() {
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)).thenReturn(Mono.empty());
    StepVerifier
      .create(userService.existsByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .expectNext(false)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can find team by id")
  void findTeamById_TeamExists_ReturnsTeam() {
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(mockTeam));
    StepVerifier.create(userService.findTeamById(TestConstants.TEST_TEAM_ID)).expectNext(mockTeam).verifyComplete();
  }

  @Test
  @DisplayName("Can get user by id")
  void getById_UserExists_ReturnsUser() {
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    StepVerifier.create(userService.getById(TestConstants.TEST_USER_ID)).expectNext(mockUser).verifyComplete();
  }

  @Test
  @DisplayName("Can error when getting user by id if id does not exist")
  void getById_UserDoesNotExist_Errors() {
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());
    StepVerifier
      .create(userService.getById(TestConstants.TEST_USER_ID))
      .expectErrorMatches(throwable ->
        throwable instanceof UserNotFoundException &&
        throwable.getMessage().equals("User id \"" + TestConstants.TEST_USER_ID + "\" not found")
      )
      .verify();
  }

  @Test
  @DisplayName("Can get password auth by user id")
  void getPasswordAuthByUserId_PasswordAuthExists_ReturnsUser() {
    when(passwordAuthRepository.findByUserId(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockPasswordAuth));
    StepVerifier
      .create(userService.getPasswordAuthByUserId(TestConstants.TEST_USER_ID))
      .expectNext(mockPasswordAuth)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can error when getting user by id if id does not exist")
  void getPasswordAuthByUserId_PasswordAuthUserDoesNotExist_Errors() {
    when(passwordAuthRepository.findByUserId(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());
    StepVerifier
      .create(userService.getPasswordAuthByUserId(TestConstants.TEST_USER_ID))
      .expectErrorMatches(throwable ->
        throwable instanceof UserNotFoundException &&
        throwable.getMessage().equals("Password Auth for user id \"" + TestConstants.TEST_USER_ID + "\" not found")
      )
      .verify();
  }

  @Test
  @DisplayName("Can suspend user until specific date and time with suspension message")
  void suspendUntil_SuspensionDateInFutureAndHasMessage_Success() {
    setClock(TestConstants.year2000Clock);

    final UserEntity testUser = TestConstants.TEST_USER_ENTITY;
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(testUser));

    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, UserEntity.class)));

    StepVerifier
      .create(userService.suspendUntil(TestConstants.TEST_USER_ID, LocalDateTime.MAX, "Banned"))
      .verifyComplete();

    ArgumentCaptor<UserEntity> userArgumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userArgumentCaptor.capture());
    assertThat(userArgumentCaptor.getValue().getSuspendedUntil()).isEqualTo(LocalDateTime.MAX);
    assertThat(userArgumentCaptor.getValue().getSuspensionMessage()).isEqualTo("Banned");

    verify(webTokenKeyManager).invalidateAccessToken(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can suspend user until specific date and time")
  void suspendUntil_SuspensionDateInFuture_Success() {
    setClock(TestConstants.year2000Clock);
    final UserEntity testUser = TestConstants.TEST_USER_ENTITY;
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(testUser));

    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, UserEntity.class)));

    StepVerifier.create(userService.suspendUntil(TestConstants.TEST_USER_ID, LocalDateTime.MAX)).verifyComplete();

    ArgumentCaptor<UserEntity> userArgumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userArgumentCaptor.capture());
    assertThat(userArgumentCaptor.getValue().getSuspendedUntil()).isEqualTo(LocalDateTime.MAX);
    assertThat(userArgumentCaptor.getValue().getSuspensionMessage()).isNull();

    verify(webTokenKeyManager).invalidateAccessToken(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can suspend user for duration with message")
  void suspendForDuration_ValidDurationAndHasMessage_Success() {
    setClock(TestConstants.year2000Clock);
    final UserEntity testUser = TestConstants.TEST_USER_ENTITY;
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(testUser));

    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, UserEntity.class)));

    StepVerifier
      .create(userService.suspendForDuration(TestConstants.TEST_USER_ID, Duration.ofDays(1), "Banned"))
      .verifyComplete();

    ArgumentCaptor<UserEntity> userArgumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userArgumentCaptor.capture());
    assertThat(userArgumentCaptor.getValue().getSuspendedUntil())
      .isEqualTo(LocalDateTime.now(TestConstants.year2000Clock).plusDays(1));
    assertThat(userArgumentCaptor.getValue().getSuspensionMessage()).isEqualTo("Banned");

    verify(webTokenKeyManager).invalidateAccessToken(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can suspend user for duration")
  void suspendForDuration_ValidDuration_Success() {
    setClock(TestConstants.year2000Clock);
    final UserEntity testUser = TestConstants.TEST_USER_ENTITY;
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(testUser));

    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, UserEntity.class)));

    StepVerifier
      .create(userService.suspendForDuration(TestConstants.TEST_USER_ID, Duration.ofDays(1)))
      .verifyComplete();

    ArgumentCaptor<UserEntity> userArgumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userArgumentCaptor.capture());
    assertThat(userArgumentCaptor.getValue().getSuspendedUntil())
      .isEqualTo(LocalDateTime.now(TestConstants.year2000Clock).plusDays(1));
    assertThat(userArgumentCaptor.getValue().getSuspensionMessage()).isNull();

    verify(webTokenKeyManager).invalidateAccessToken(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can error if suspending user until past date and time")
  void suspendUntil_SuspensionDateHasPassedAndHasMessage_Errors() {
    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(userService.suspendUntil(TestConstants.TEST_USER_ID, LocalDateTime.MIN, "Banned"))
      .expectErrorMatches(throwable ->
        throwable instanceof IllegalArgumentException &&
        throwable.getMessage().equals("Suspension date must be in the future")
      )
      .verify();
  }

  @Test
  @DisplayName("Can fail to authenticate user if correct password but wrong username is supplied")
  void authenticate_ValidUsernameButInvalidPassword_DoesNotAuthenticate() {
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .thenReturn(Mono.just(mockPasswordAuth));
    when(mockPasswordAuth.getHashedPassword()).thenReturn(TestConstants.HASHED_TEST_PASSWORD);

    StepVerifier
      .create(userService.authenticate(TestConstants.TEST_USER_LOGIN_NAME, "wrong password"))
      .expectErrorMatches(throwable ->
        throwable instanceof BadCredentialsException && throwable.getMessage().equals("Invalid username or password")
      )
      .verify();
  }

  @Test
  @DisplayName("Can fail to create user if display name already exists")
  void create_DisplayNameAlreadyExists_Errors() {
    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);

    when(userRepository.findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME)).thenReturn(Mono.just(mockUser));

    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(userService.create(TestConstants.TEST_USER_DISPLAY_NAME))
      .expectError(DuplicateUserDisplayNameException.class)
      .verify();
  }

  @Test
  @DisplayName("Can create a user with display name")
  void create_ValidDisplayName_CreatesUser() {
    when(userRepository.findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME)).thenReturn(Mono.empty());

    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);

    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, UserEntity.class).withId(TestConstants.TEST_USER_ID)));

    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(userService.create(TestConstants.TEST_USER_DISPLAY_NAME))
      .expectNext(TestConstants.TEST_USER_ID)
      .verifyComplete();

    ArgumentCaptor<UserEntity> argument = ArgumentCaptor.forClass(UserEntity.class);

    verify(userRepository).save(argument.capture());

    final UserEntity createdUser = argument.getValue();
    assertThat(createdUser.getDisplayName()).isEqualTo(TestConstants.TEST_USER_DISPLAY_NAME);
    assertThat(createdUser.getCreationTime()).isEqualTo(LocalDateTime.now(TestConstants.year2000Clock));
    assertThat(createdUser.getId()).isNull();
    assertThat(createdUser.getKey()).isEqualTo(TestConstants.TEST_BYTE_ARRAY);
    assertThat(createdUser.isAdmin()).isFalse();
    assertThat(createdUser.isEnabled()).isTrue();
    assertThat(createdUser.getSuspendedUntil()).isNull();
    assertThat(createdUser.getSuspensionMessage()).isNull();
  }

  @Test
  @DisplayName("Can fail to create password user if display name already exists")
  void createPasswordUser_DuplicateDisplayName_Errors() {
    when(userRepository.findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME)).thenReturn(Mono.just(mockUser));

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
  }

  @Test
  @DisplayName("Can fail to create password user if login name already exists")
  void createPasswordUser_DuplicateLoginName_Errors() {
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .thenReturn(Mono.just(mockPasswordAuth));
    when(userRepository.findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME)).thenReturn(Mono.empty());

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
  }

  @Test
  @DisplayName("Can create password user")
  void createPasswordUser_ValidData_Succeeds() {
    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)).thenReturn(Mono.empty());
    when(userRepository.findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME)).thenReturn(Mono.empty());
    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, UserEntity.class).withId(TestConstants.TEST_USER_ID)));
    when(passwordAuthRepository.save(any(PasswordAuth.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, PasswordAuth.class).withId(TestConstants.TEST_USER_ID)));

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

    ArgumentCaptor<UserEntity> userArgumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userArgumentCaptor.capture());
    assertThat(userArgumentCaptor.getValue().getId()).isNull();
    assertThat(userArgumentCaptor.getValue().getDisplayName()).isEqualTo(TestConstants.TEST_USER_DISPLAY_NAME);

    ArgumentCaptor<PasswordAuth> passwordAuthArgumentCaptor = ArgumentCaptor.forClass(PasswordAuth.class);
    verify(passwordAuthRepository).save(passwordAuthArgumentCaptor.capture());
    assertThat(passwordAuthArgumentCaptor.getValue().getUserId()).isEqualTo(TestConstants.TEST_USER_ID);
    assertThat(passwordAuthArgumentCaptor.getValue().getLoginName()).isEqualTo(TestConstants.TEST_USER_LOGIN_NAME);
    assertThat(passwordAuthArgumentCaptor.getValue().getHashedPassword()).isEqualTo(TestConstants.HASHED_TEST_PASSWORD);
  }

  @Test
  @DisplayName("Can return error when deleting nonexistent user by id")
  void deleteById_NonexistentUserId_SavesDeletedPlaceholder() {
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());

    StepVerifier
      .create(userService.delete(TestConstants.TEST_USER_ID))
      .expectError(UserNotFoundException.class)
      .verify();
  }

  @Test
  @DisplayName("Can error when deleting user belonging to team")
  void deleteById_UserInTeam_Errors() {
    final UserEntity testUser = TestConstants.TEST_USER_ENTITY
      .withTeamId(TestConstants.TEST_TEAM_ID)
      .withSuspendedUntil(LocalDateTime.now(TestConstants.year2100Clock))
      .withSuspensionMessage("Banned!")
      .withId(TestConstants.TEST_USER_ID)
      .withTeamId(TestConstants.TEST_TEAM_ID)
      .withAdmin(true);

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(testUser));

    StepVerifier
      .create(userService.delete(TestConstants.TEST_USER_ID))
      .expectError(IllegalStateException.class)
      .verify();
  }

  @Test
  @DisplayName("Can delete user by id")
  void deleteById_ValidUserId_SavesDeletedPlaceholder() {
    final UserEntity testUser = TestConstants.TEST_USER_ENTITY
      .withSuspendedUntil(LocalDateTime.now(TestConstants.year2100Clock))
      .withSuspensionMessage("Banned!")
      .withId(TestConstants.TEST_USER_ID)
      .withAdmin(true);

    when(passwordAuthRepository.deleteByUserId(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(testUser));
    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(user -> Mono.just(user.getArgument(0, UserEntity.class)));

    StepVerifier.create(userService.delete(TestConstants.TEST_USER_ID)).verifyComplete();

    final ArgumentCaptor<UserEntity> userArgumentCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userArgumentCaptor.capture());

    final UserEntity deletedUser = userArgumentCaptor.getValue();
    assertThat(deletedUser.getId()).isEqualTo(TestConstants.TEST_USER_ID);
    assertThat(deletedUser.getDisplayName()).isEmpty();
    assertThat(deletedUser.getSuspensionMessage()).isNull();
    assertThat(deletedUser.getSuspendedUntil()).isNull();
    assertThat(deletedUser.isAdmin()).isFalse();
    assertThat(deletedUser.isEnabled()).isFalse();
    assertThat(deletedUser.getTeamId()).isNull();
  }

  @Test
  @DisplayName("Can find all users")
  void findAllUsers_UsersExist_ReturnsUsers() {
    final UserEntity mockUser1 = mock(UserEntity.class);
    final UserEntity mockUser2 = mock(UserEntity.class);
    final UserEntity mockUser3 = mock(UserEntity.class);

    when(userRepository.findAll()).thenReturn(Flux.just(mockUser1, mockUser2, mockUser3));

    StepVerifier
      .create(userService.findAllUsers())
      .expectNext(mockUser1)
      .expectNext(mockUser2)
      .expectNext(mockUser3)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can find user by id")
  void findById_ExistingUserId_ReturnsUserEntity() {
    final UserEntity mockUser = mock(UserEntity.class);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));

    StepVerifier.create(userService.findById(TestConstants.TEST_USER_ID)).expectNext(mockUser).verifyComplete();
  }

  @Test
  @DisplayName("Can get empty result when finding user by id that does not exist")
  void findById_NonExistentUserId_ReturnsEmpty() {
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findById(TestConstants.TEST_USER_ID)).verifyComplete();
  }

  @Test
  @DisplayName("Can get empty result when finding user by login name that does not exist")
  void findByLoginName_LoginNameDoesNotExist_ReturnsEmpty() {
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findUserIdByLoginName(TestConstants.TEST_USER_LOGIN_NAME)).verifyComplete();
  }

  @Test
  @DisplayName("Can find user by login name")
  void findByLoginName_UserExists_ReturnsUser() {
    final PasswordAuth mockPasswordAuth = mock(PasswordAuth.class);

    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .thenReturn(Mono.just(mockPasswordAuth));
    when(mockPasswordAuth.getUserId()).thenReturn(TestConstants.TEST_USER_ID);

    StepVerifier
      .create(userService.findUserIdByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .expectNext(TestConstants.TEST_USER_ID)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can find password auth by login name")
  void findPasswordAuthByLoginName_ExistingLoginName_ReturnsPasswordAuth() {
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .thenReturn(Mono.just(mockPasswordAuth));
    StepVerifier
      .create(userService.findPasswordAuthByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .expectNext(mockPasswordAuth)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can get empty result when finding password auth by login name that does not exist")
  void findPasswordAuthByLoginName_NonExistentLoginName_ReturnsEmpty() {
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findPasswordAuthByLoginName(TestConstants.TEST_USER_LOGIN_NAME)).verifyComplete();
  }

  @Test
  @DisplayName("Can get empty result when finding password auth by user id that does not exist")
  void findPasswordAuthByUserId_NoPasswordAuthExists_ReturnsEmpty() {
    when(passwordAuthRepository.findByUserId(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findPasswordAuthByUserId(TestConstants.TEST_USER_ID)).verifyComplete();
  }

  @Test
  @DisplayName("Can find password auth by user id")
  void findPasswordAuthByUserId_PasswordAuthExists_ReturnsPasswordAuth() {
    when(passwordAuthRepository.findByUserId(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockPasswordAuth));
    StepVerifier
      .create(userService.findPasswordAuthByUserId(TestConstants.TEST_USER_ID))
      .expectNext(mockPasswordAuth)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can get user key by user id")
  void getKeyById_KeyExists_ReturnsKey() {
    when(mockUser.getKey()).thenReturn(TestConstants.TEST_BYTE_ARRAY);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));

    StepVerifier
      .create(userService.findKeyByUserId(TestConstants.TEST_USER_ID))
      .expectNext(TestConstants.TEST_BYTE_ARRAY)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can promote user to admin")
  void promote_RegularUser_UserBecomesAdmin() {
    final UserEntity testUser = TestConstants.TEST_USER_ENTITY.withAdmin(false);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(testUser));
    when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> Mono.just(i.getArguments()[0]));

    StepVerifier.create(userService.promote(TestConstants.TEST_USER_ID)).verifyComplete();

    final ArgumentCaptor<UserEntity> argument = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(argument.capture());
    assertThat(argument.getValue().isAdmin()).isTrue();

    verify(webTokenKeyManager).invalidateAccessToken(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can promote admin user with no effect")
  void promote_UserIsAdmin_NoEffect() {
    final UserEntity testUser = TestConstants.TEST_USER_ENTITY.withAdmin(true);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(testUser));

    StepVerifier.create(userService.promote(TestConstants.TEST_USER_ID)).verifyComplete();

    verify(userRepository, never()).save(any(UserEntity.class));
    verify(webTokenKeyManager, never()).invalidateAccessToken(any(String.class));
  }

  @Test
  @DisplayName("Can demote admin user")
  void demote_UserIsAdmin_Demoted() {
    final UserEntity testUser = TestConstants.TEST_USER_ENTITY.withAdmin(true);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(testUser));
    when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> Mono.just(i.getArguments()[0]));

    StepVerifier.create(userService.demote(TestConstants.TEST_USER_ID)).verifyComplete();

    final ArgumentCaptor<UserEntity> argument = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(argument.capture());
    assertThat(argument.getValue().isAdmin()).isFalse();

    verify(webTokenKeyManager).invalidateAccessToken(TestConstants.TEST_USER_ID);
  }

  @Test
  @DisplayName("Can demote non-admin user with no effect")
  void demote_UserIsNotAdmin_StaysNotAdmin() {
    final UserEntity testUser = TestConstants.TEST_USER_ENTITY.withAdmin(false);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(testUser));

    StepVerifier.create(userService.demote(TestConstants.TEST_USER_ID)).verifyComplete();

    verify(userRepository, never()).save(any(UserEntity.class));
    verify(webTokenKeyManager, never()).invalidateAccessToken(any(String.class));
  }

  @Test
  @DisplayName("Can error when setting class id of user to an id that does not exist")
  void setClassId_NonExistentClassId_ReturnsClassIdNotFoundException() {
    final String mockClassId = "classid";

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(classService.existsById(mockClassId)).thenReturn(Mono.just(false));

    StepVerifier
      .create(userService.setClassId(TestConstants.TEST_USER_ID, mockClassId))
      .expectError(ClassIdNotFoundException.class)
      .verify();
  }

  @Test
  @DisplayName("Can set class id of user")
  void setClassId_ValidClassId_Succeeds() {
    final String mockClassId = "classid";
    final UserEntity mockUserWithClass = mock(UserEntity.class);

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(classService.existsById(mockClassId)).thenReturn(Mono.just(true));

    when(mockUser.withClassId(mockClassId)).thenReturn(mockUserWithClass);
    when(userRepository.save(mockUserWithClass)).thenReturn(Mono.just(mockUserWithClass));
    when(mockUserWithClass.getClassId()).thenReturn(mockClassId);

    StepVerifier
      .create(userService.setClassId(TestConstants.TEST_USER_ID, mockClassId))
      .expectNextMatches(user -> user.getClassId() == mockClassId)
      .verifyComplete();
  }

  private void setClock(final Clock testClock) {
    when(clock.instant()).thenReturn(testClock.instant());
    when(clock.getZone()).thenReturn(testClock.getZone());
  }

  @Test
  @DisplayName("Can set display name of user")
  void setDisplayName_ValidDisplayName_DisplayNameIsSet() {
    final UserEntity testUser = TestConstants.TEST_USER_ENTITY;
    String newDisplayName = "newDisplayName";

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(testUser));
    when(userRepository.findByDisplayName(newDisplayName)).thenReturn(Mono.empty());

    when(userRepository.save(any(UserEntity.class))).thenAnswer(i -> Mono.just(i.getArguments()[0]));

    StepVerifier
      .create(userService.setDisplayName(TestConstants.TEST_USER_ID, newDisplayName))
      .expectNextMatches(user -> user.getDisplayName().equals(newDisplayName))
      .as("Display name should change to supplied value")
      .verifyComplete();

    final ArgumentCaptor<UserEntity> argument = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(argument.capture());
    assertThat(argument.getValue().getDisplayName()).isEqualTo(newDisplayName);
  }

  @BeforeEach
  void setup() {
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
    mockTeam = mock(TeamEntity.class);
  }
}
