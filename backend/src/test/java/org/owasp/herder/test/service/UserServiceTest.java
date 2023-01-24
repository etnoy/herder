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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.PasswordAuth;
import org.owasp.herder.authentication.PasswordAuthRepository;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.exception.ClassIdNotFoundException;
import org.owasp.herder.exception.DuplicateTeamDisplayNameException;
import org.owasp.herder.exception.DuplicateUserDisplayNameException;
import org.owasp.herder.exception.DuplicateUserLoginNameException;
import org.owasp.herder.exception.UserNotFoundException;
import org.owasp.herder.scoring.PrincipalType;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.ClassService;
import org.owasp.herder.user.PrincipalEntity;
import org.owasp.herder.user.TeamEntity;
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

  TeamEntity mockTeam;

  @Test
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
  void authenticate_UserSuspended_ReturnsLockedException() {
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .thenReturn(Mono.just(mockPasswordAuth));
    when(mockPasswordAuth.getHashedPassword()).thenReturn(TestConstants.HASHED_TEST_PASSWORD);
    when(mockPasswordAuth.getUserId()).thenReturn(TestConstants.TEST_USER_ID);
    when(mockUser.isEnabled()).thenReturn(true);

    LocalDateTime futureDate = LocalDateTime.MAX;
    when(mockUser.getSuspendedUntil()).thenReturn(futureDate);

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));

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
  void addUserToTeam_UserAlreadyInTeam_ThrowsException() {
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getTeamId()).thenReturn(TestConstants.TEST_TEAM_ID);
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(mockTeam));

    StepVerifier
      .create(userService.addUserToTeam(TestConstants.TEST_USER_ID, TestConstants.TEST_TEAM_ID))
      .expectErrorMatches(e ->
        e instanceof IllegalStateException && e.getMessage().equals("User already belongs to a team")
      )
      .verify();
  }

  @Test
  void addUserToTeam_UserNotInTeam_UserAddedToTeam() {
    UserEntity mockUserWithTeam = mock(UserEntity.class);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getTeamId()).thenReturn(null);
    when(mockUser.withTeamId(TestConstants.TEST_TEAM_ID)).thenReturn(mockUserWithTeam);
    when(mockUserWithTeam.getTeamId()).thenReturn(TestConstants.TEST_TEAM_ID);
    when(mockUserWithTeam.getId()).thenReturn(TestConstants.TEST_USER_ID);
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(mockTeam));
    when(mockTeam.getMembers()).thenReturn(new ArrayList<UserEntity>());
    when(userRepository.save(mockUserWithTeam)).thenReturn(Mono.just(mockUserWithTeam));
    when(teamRepository.save(mockTeam)).thenReturn(Mono.just(mockTeam));

    StepVerifier
      .create(userService.addUserToTeam(TestConstants.TEST_USER_ID, TestConstants.TEST_TEAM_ID))
      .verifyComplete();

    final ArgumentCaptor<UserEntity> userEntityArgument = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userEntityArgument.capture());
    final UserEntity savedUserEntity = userEntityArgument.getValue();

    final ArgumentCaptor<TeamEntity> teamEntityArgument = ArgumentCaptor.forClass(TeamEntity.class);
    verify(teamRepository).save(teamEntityArgument.capture());
    final TeamEntity savedTeamEntity = teamEntityArgument.getValue();

    assertThat(savedUserEntity.getTeamId()).isEqualTo(TestConstants.TEST_TEAM_ID);
    assertThat(savedTeamEntity.getMembers()).extracting(UserEntity::getId).containsExactly(TestConstants.TEST_USER_ID);
  }

  @Test
  void createTeam_TeamDisplayNameAlreadyExists_ThrowsException() {
    when(teamRepository.findByDisplayName(TestConstants.TEST_TEAM_DISPLAY_NAME)).thenReturn(Mono.just(mockTeam));

    StepVerifier
      .create(userService.createTeam(TestConstants.TEST_TEAM_DISPLAY_NAME))
      .expectErrorMatches(e ->
        e instanceof DuplicateTeamDisplayNameException &&
        e.getMessage().equals("Team display name \"" + TestConstants.TEST_TEAM_DISPLAY_NAME + "\" already exists")
      )
      .verify();
  }

  @Test
  void createTeam_TeamDisplayNameDoesNotExist_TeamCreated() {
    setClock(TestConstants.year2000Clock);

    when(teamRepository.findByDisplayName(TestConstants.TEST_TEAM_DISPLAY_NAME)).thenReturn(Mono.empty());

    TeamEntity createdTeam = TeamEntity
      .builder()
      .displayName(TestConstants.TEST_TEAM_DISPLAY_NAME)
      .creationTime(LocalDateTime.now(TestConstants.year2000Clock))
      .members(new ArrayList<>())
      .build();

    when(teamRepository.save(createdTeam)).thenReturn(Mono.just(createdTeam.withId(TestConstants.TEST_TEAM_ID)));

    StepVerifier
      .create(userService.createTeam(TestConstants.TEST_TEAM_DISPLAY_NAME))
      .expectNext(TestConstants.TEST_TEAM_ID)
      .verifyComplete();
  }

  @Test
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
  @DisplayName("findAllPrincipals can list a finite amount of users")
  void findAllPrincipals_OnlyUsers_ReturnsThem() {
    final UserEntity mockUser1 = mock(UserEntity.class);
    final UserEntity mockUser2 = mock(UserEntity.class);
    final UserEntity mockUser3 = mock(UserEntity.class);

    when(mockUser1.getDisplayName()).thenReturn("Test User 1");
    when(mockUser2.getDisplayName()).thenReturn("Test User 2");
    when(mockUser3.getDisplayName()).thenReturn("Test User 3");

    final PrincipalEntity principal1 = PrincipalEntity
      .builder()
      .displayName("Test User 1")
      .principalType(PrincipalType.USER)
      .build();

    final PrincipalEntity principal2 = PrincipalEntity
      .builder()
      .displayName("Test User 2")
      .principalType(PrincipalType.USER)
      .build();

    final PrincipalEntity principal3 = PrincipalEntity
      .builder()
      .displayName("Test User 3")
      .principalType(PrincipalType.USER)
      .build();

    final List<PrincipalEntity> expectedPrincipals = List.of(principal1, principal2, principal3);

    when(teamRepository.findAll()).thenReturn(Flux.empty());
    when(userRepository.findAllByTeamId(null)).thenReturn(Flux.just(mockUser1, mockUser2, mockUser3));

    StepVerifier
      .create(userService.findAllPrincipals())
      .thenConsumeWhile(principal -> expectedPrincipals.contains(principal))
      .verifyComplete();
  }

  @Test
  @DisplayName("findAllPrincipals can list a finite amount of users and teams")
  void findAllPrincipals_UsersAndTeams_ReturnsThem() {
    final UserEntity mockUser1 = mock(UserEntity.class);
    final UserEntity mockUser2 = mock(UserEntity.class);
    final UserEntity mockUser3 = mock(UserEntity.class);

    when(mockUser1.getDisplayName()).thenReturn("Test User 1");

    final TeamEntity mockTeam = mock(TeamEntity.class);
    when(mockTeam.getDisplayName()).thenReturn("Test Team");
    when(mockTeam.getId()).thenReturn(TestConstants.TEST_TEAM_ID);

    final PrincipalEntity userPrincipal = PrincipalEntity
      .builder()
      .displayName("Test User 1")
      .principalType(PrincipalType.USER)
      .build();

    final PrincipalEntity teamPrincipal = PrincipalEntity
      .builder()
      .id(TestConstants.TEST_TEAM_ID)
      .displayName("Test Team")
      .principalType(PrincipalType.TEAM)
      .members(new HashSet<>(Arrays.asList(mockUser2, mockUser3)))
      .build();

    final List<PrincipalEntity> expectedPrincipals = List.of(userPrincipal, teamPrincipal);

    when(userRepository.findAllByTeamId(null)).thenReturn(Flux.just(mockUser1));
    when(userRepository.findAllByTeamId(TestConstants.TEST_TEAM_ID)).thenReturn(Flux.just(mockUser2, mockUser3));
    when(teamRepository.findAll()).thenReturn(Flux.just(mockTeam));

    StepVerifier
      .create(userService.findAllPrincipals())
      .thenConsumeWhile(principal -> expectedPrincipals.contains(principal))
      .verifyComplete();
  }

  @Test
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
  @DisplayName("create() can return exception if display name already exists")
  void create_DisplayNameAlreadyExists_ReturnsDuplicateUserDisplayNameException() {
    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);

    when(userRepository.findByDisplayName(TestConstants.TEST_USER_DISPLAY_NAME)).thenReturn(Mono.just(mockUser));

    setClock(TestConstants.year2000Clock);

    StepVerifier
      .create(userService.create(TestConstants.TEST_USER_DISPLAY_NAME))
      .expectError(DuplicateUserDisplayNameException.class)
      .verify();
  }

  @Test
  @DisplayName("Can create a user")
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
  void createPasswordUser_DuplicateDisplayName_ReturnsDuplicateUserDisplayNameException() {
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
  void createPasswordUser_DuplicateLoginName_ReturnsDuplicateClassNameException() {
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
  void createPasswordUser_NullPasswordHash_ReturnsNullPointerException() {
    StepVerifier
      .create(userService.createPasswordUser("displayName", "loginName", null))
      .expectError(NullPointerException.class)
      .verify();
  }

  @Test
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
  @DisplayName("Can return error when deleting nonexistent user")
  void deleteById_NonexistentUserId_SavesDeletedPlaceholder() {
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());

    StepVerifier
      .create(userService.delete(TestConstants.TEST_USER_ID))
      .expectError(UserNotFoundException.class)
      .verify();
  }

  @Test
  @DisplayName("Can delete user")
  void deleteById_ValidUserId_SavesDeletedPlaceholder() {
    when(passwordAuthRepository.deleteByUserId(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(userRepository.save(mockUser)).thenReturn(Mono.just(mockUser));
    when(mockUser.withTeamId(null)).thenReturn(mockUser);
    when(mockUser.withEnabled(false)).thenReturn(mockUser);
    when(mockUser.withDeleted(true)).thenReturn(mockUser);
    when(mockUser.withDisplayName("")).thenReturn(mockUser);
    when(mockUser.withSuspensionMessage(null)).thenReturn(mockUser);
    when(mockUser.withAdmin(false)).thenReturn(mockUser);

    StepVerifier.create(userService.delete(TestConstants.TEST_USER_ID)).verifyComplete();
  }

  @Test
  @DisplayName("Can demote administrator to regular user")
  void demote_UserIsAdmin_Demoted() {
    final UserEntity mockDemotedUser = mock(UserEntity.class);

    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(auth -> {
        if (auth.getArgument(0, UserEntity.class) == mockDemotedUser) {
          // We are saving the admin auth to db
          return Mono.just(auth.getArgument(0, UserEntity.class));
        } else {
          // We are saving the newly created auth to db
          return Mono.just(auth.getArgument(0, UserEntity.class).withId(TestConstants.TEST_USER_ID));
        }
      });

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.withAdmin(false)).thenReturn(mockDemotedUser);
    Mockito.doNothing().when(webTokenKeyManager).invalidateAccessToken(TestConstants.TEST_USER_ID);
    StepVerifier.create(userService.demote(TestConstants.TEST_USER_ID)).verifyComplete();
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
          return Mono.just(savedUser.getArgument(0, UserEntity.class).withId(TestConstants.TEST_USER_ID));
        }
      });

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.withAdmin(false)).thenReturn(mockUser);

    StepVerifier.create(userService.demote(TestConstants.TEST_USER_ID)).verifyComplete();
  }

  @Test
  void findAll_NoUsersExist_ReturnsEmpty() {
    when(userRepository.findAll()).thenReturn(Flux.empty());
    StepVerifier.create(userService.findAllUsers()).verifyComplete();
  }

  @Test
  void findAll_UsersExist_ReturnsUsers() {
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
  void findById_ExistingUserId_ReturnsUserEntity() {
    final UserEntity mockUser = mock(UserEntity.class);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));

    StepVerifier.create(userService.findById(TestConstants.TEST_USER_ID)).expectNext(mockUser).verifyComplete();
  }

  @Test
  void findById_NonExistentUserId_ReturnsEmpty() {
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findById(TestConstants.TEST_USER_ID)).verifyComplete();
  }

  @Test
  void findByLoginName_LoginNameDoesNotExist_ReturnsEmptyMono() {
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findUserIdByLoginName(TestConstants.TEST_USER_LOGIN_NAME)).verifyComplete();
  }

  @Test
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
  void findPasswordAuthByLoginName_ExistingLoginName_ReturnsPasswordAuth() {
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .thenReturn(Mono.just(mockPasswordAuth));
    StepVerifier
      .create(userService.findPasswordAuthByLoginName(TestConstants.TEST_USER_LOGIN_NAME))
      .expectNext(mockPasswordAuth)
      .verifyComplete();
  }

  @Test
  void findPasswordAuthByLoginName_NonExistentLoginName_ReturnsEmpty() {
    when(passwordAuthRepository.findByLoginName(TestConstants.TEST_USER_LOGIN_NAME)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findPasswordAuthByLoginName(TestConstants.TEST_USER_LOGIN_NAME)).verifyComplete();
  }

  @Test
  void findPasswordAuthByUserId_NoPasswordAuthExists_ReturnsEmpty() {
    when(passwordAuthRepository.findByUserId(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());
    StepVerifier.create(userService.findPasswordAuthByUserId(TestConstants.TEST_USER_ID)).verifyComplete();
  }

  @Test
  void findPasswordAuthByUserId_PasswordAuthExists_ReturnsPasswordAuth() {
    when(passwordAuthRepository.findByUserId(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockPasswordAuth));
    StepVerifier
      .create(userService.findPasswordAuthByUserId(TestConstants.TEST_USER_ID))
      .expectNext(mockPasswordAuth)
      .verifyComplete();
  }

  @Test
  void getKeyById_KeyExists_ReturnsKey() {
    when(mockUser.getKey()).thenReturn(TestConstants.TEST_BYTE_ARRAY);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));

    StepVerifier
      .create(userService.findKeyById(TestConstants.TEST_USER_ID))
      .expectNext(TestConstants.TEST_BYTE_ARRAY)
      .verifyComplete();
  }

  @Test
  void getKeyById_NoKeyExists_GeneratesKey() {
    // This user does not have a key
    final UserEntity mockUserWithoutKey = mock(UserEntity.class);
    when(mockUserWithoutKey.getKey()).thenReturn(null);

    // When that user is given a key, return an entity that has the key
    final UserEntity mockUserWithKey = mock(UserEntity.class);
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID))
      .thenReturn(Mono.just(mockUserWithoutKey));
    when(mockUserWithoutKey.getKey()).thenReturn(null);

    when(mockUserWithoutKey.withKey(TestConstants.TEST_BYTE_ARRAY)).thenReturn(mockUserWithKey);

    // Set up the mock repository
    when(userRepository.save(mockUserWithKey)).thenReturn(Mono.just(mockUserWithKey));

    // Set up the mock key service
    when(keyService.generateRandomBytes(16)).thenReturn(TestConstants.TEST_BYTE_ARRAY);

    when(mockUserWithKey.getKey()).thenReturn(TestConstants.TEST_BYTE_ARRAY);

    StepVerifier
      .create(userService.findKeyById(TestConstants.TEST_USER_ID))
      .expectNext(TestConstants.TEST_BYTE_ARRAY)
      .verifyComplete();

    ArgumentCaptor<UserEntity> argument = ArgumentCaptor.forClass(UserEntity.class);

    verify(userRepository).save(argument.capture());

    assertThat(argument.getValue()).isEqualTo(mockUserWithKey);
    assertThat(argument.getValue().getKey()).isEqualTo(TestConstants.TEST_BYTE_ARRAY);
  }

  @Test
  @DisplayName("Can promote user that already is administrator")
  void promote_UserIsAdmin_StaysAdmin() {
    when(userRepository.save(any(UserEntity.class)))
      .thenAnswer(auth -> {
        if (auth.getArgument(0, UserEntity.class).equals(mockUser)) {
          // We are saving the admin auth to db
          return Mono.just(auth.getArgument(0, UserEntity.class));
        } else {
          // We are saving the newly created auth to db
          return Mono.just(auth.getArgument(0, UserEntity.class).withId(TestConstants.TEST_USER_ID));
        }
      });

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.withAdmin(true)).thenReturn(mockUser);

    StepVerifier.create(userService.promote(TestConstants.TEST_USER_ID)).verifyComplete();

    verify(passwordAuthRepository, never()).findByUserId(any(String.class));
  }

  @Test
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
  @DisplayName("Can return exception when setting display name of user id that does not exist")
  void setDisplayName_UserIdDoesNotExist_ReturnsUserIdNotFoundException() {
    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());

    StepVerifier
      .create(userService.setDisplayName(TestConstants.TEST_USER_ID, TestConstants.TEST_USER_DISPLAY_NAME))
      .expectError(UserNotFoundException.class)
      .verify();
  }

  @Test
  @DisplayName("Can change display name")
  void setDisplayName_ValidDisplayName_DisplayNameIsSet() {
    String newDisplayName = "newDisplayName";

    when(userRepository.findByIdAndIsDeletedFalse(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(userRepository.findByDisplayName(newDisplayName)).thenReturn(Mono.empty());

    when(mockUser.withDisplayName(newDisplayName)).thenReturn(mockUser);
    when(userRepository.save(any(UserEntity.class))).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn(newDisplayName);

    StepVerifier
      .create(userService.setDisplayName(TestConstants.TEST_USER_ID, newDisplayName))
      .expectNextMatches(user -> user.getDisplayName().equals(newDisplayName))
      .as("Display name should change to supplied value")
      .verifyComplete();
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
