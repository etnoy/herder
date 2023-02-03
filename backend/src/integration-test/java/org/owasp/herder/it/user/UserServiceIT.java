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
package org.owasp.herder.it.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.owasp.herder.authentication.PasswordAuthRepository;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.scoring.SolverType;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.SolverEntity;
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.TeamService;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserRepository;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

@DisplayName("UserService integration tests")
class UserServiceIT extends BaseIT {

  @Autowired
  UserService userService;

  @Autowired
  UserRepository userRepository;

  @Autowired
  PasswordAuthRepository passwordAuthRepository;

  @Autowired
  SubmissionService submissionService;

  @Autowired
  TeamService teamService;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @Test
  @DisplayName("Can add user to team")
  void canAddUserToTeamMembers() {
    final String userId = integrationTestUtils.createTestUser();
    final String teamId = integrationTestUtils.createTestTeam();

    userService.addUserToTeam(userId, teamId).block();

    StepVerifier.create(userService.getById(userId).map(UserEntity::getTeamId)).expectNext(teamId).verifyComplete();
  }

  @Test
  @DisplayName("Can clear teams from user")
  void canClearTeamsFromUser() {
    final String userId = integrationTestUtils.createTestUser();
    final String teamId = integrationTestUtils.createTestTeam();

    userService.addUserToTeam(userId, teamId).block();

    userService.clearTeamForUser(userId).block();

    StepVerifier
      .create(userService.findById(userId))
      .assertNext(user -> assertThat(user.getTeamId()).isNull())
      .verifyComplete();
  }

  @ParameterizedTest
  @MethodSource("org.owasp.herder.test.util.TestConstants#validDisplayNameProvider")
  @DisplayName("Can create team with display name")
  void canCreateTeam(final String displayName) {
    StepVerifier
      .create(teamService.create(displayName).flatMap(userService::findTeamById).map(TeamEntity::getDisplayName))
      .expectNext(displayName)
      .verifyComplete();
  }

  @ParameterizedTest
  @MethodSource("org.owasp.herder.test.util.TestConstants#validDisplayNameProvider")
  @DisplayName("Can create user with display name")
  void canCreateUser(final String displayName) {
    StepVerifier
      .create(userService.create(displayName).flatMap(userService::findById).map(UserEntity::getDisplayName))
      .expectNext(displayName)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can create a password user")
  void canCreateValidUserWithCreatePasswordUser() {
    final String displayName = "Test user";
    final String loginName = "testUser";
    final String passwordHash = "$2y$12$53B6QcsGwF3Os1GVFUFSQOhIPXnWFfuEkRJdbknFWnkXfUBMUKhaW";
    final String userId = userService.createPasswordUser(displayName, loginName, passwordHash).block();

    StepVerifier
      .create(userService.findById(userId))
      .assertNext(user -> {
        assertThat(user.getDisplayName()).isEqualTo(displayName);
        assertThat(user.getId()).isEqualTo(userId);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can delete password auth when deleting password user")
  void canDeletePasswordAuthWhenDeletingPasswordAuth() {
    final String userId = userService
      .createPasswordUser(
        TestConstants.TEST_USER_DISPLAY_NAME,
        TestConstants.TEST_USER_LOGIN_NAME,
        TestConstants.HASHED_TEST_PASSWORD
      )
      .block();

    userService.delete(userId).block();
    StepVerifier.create(passwordAuthRepository.findByUserId(userId)).verifyComplete();
  }

  @Test
  @DisplayName("Can delete password user")
  void canDeletePasswordUser() {
    final String userId = userService
      .createPasswordUser(
        TestConstants.TEST_USER_DISPLAY_NAME,
        TestConstants.TEST_USER_LOGIN_NAME,
        TestConstants.HASHED_TEST_PASSWORD
      )
      .block();

    userService.delete(userId).block();
    StepVerifier
      .create(userRepository.findById(userId))
      .assertNext(user -> {
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDisplayName()).isEmpty();
        assertThat(user.isEnabled()).isFalse();
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can delete user")
  void canDeleteUser() {
    final String userId = userService.create(TestConstants.TEST_USER_DISPLAY_NAME).block();
    userService.delete(userId).block();
    StepVerifier
      .create(userRepository.findById(userId))
      .assertNext(user -> {
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDisplayName()).isEmpty();
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getTeamId()).isNull();
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can error when adding user to new team")
  void canErrorWhenAddingUserToNewTeam() {
    final String userId = integrationTestUtils.createTestUser();
    final String teamId = integrationTestUtils.createTestTeam();

    userService.addUserToTeam(userId, teamId).block();

    final String teamId2 = teamService.create("Test team 2").block();

    StepVerifier.create(userService.addUserToTeam(userId, teamId2)).expectError(IllegalStateException.class).verify();
  }

  private SolverEntity getSolverById(
    final Collection<SolverEntity> solvers,
    final String solverId,
    final SolverType solverType
  ) {
    return solvers
      .stream()
      .filter(solver -> solver.getId().equals(solverId) && solver.getSolverType().equals(solverType))
      .findFirst()
      .orElseThrow();
  }

  @Test
  @DisplayName("Can list solvers consisting of teams and users")
  void canListSolversContainingUsersAndTeams() {
    final String userId1 = userService.create("Test 1").block();
    final String userId2 = userService.create("Test 2").block();
    final String userId3 = userService.create("Test 3").block();
    final String userId4 = userService.create("Test 4").block();

    final String teamId1 = teamService.create("Team 1").block();
    final String teamId2 = teamService.create("Team 2").block();
    final String teamId3 = teamService.create("Team 3").block();
    final String teamId4 = teamService.create("Team 4").block();

    userService.addUserToTeam(userId1, teamId1).block();
    userService.addUserToTeam(userId2, teamId1).block();
    userService.addUserToTeam(userId3, teamId2).block();

    StepVerifier
      .create(userService.findAllSolvers())
      .recordWith(HashSet::new)
      .expectNextCount(5)
      .consumeRecordedWith(solvers -> {
        assertThat(solvers)
          .extracting(SolverEntity::getId)
          .containsExactlyInAnyOrder(userId4, teamId1, teamId2, teamId3, teamId4);

        assertThat(getSolverById(solvers, teamId1, SolverType.TEAM).getMembers())
          .extracting(UserEntity::getId)
          .containsExactlyInAnyOrder(userId1, userId2);

        assertThat(getSolverById(solvers, teamId2, SolverType.TEAM).getMembers())
          .extracting(UserEntity::getId)
          .containsExactlyInAnyOrder(userId3);

        assertThat(getSolverById(solvers, teamId3, SolverType.TEAM).getMembers()).isEmpty();

        assertThat(getSolverById(solvers, teamId4, SolverType.TEAM).getMembers()).isEmpty();

        assertThat(getSolverById(solvers, userId4, SolverType.USER).getMembers()).isEmpty();
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can list principals consisting of a single team with a single user")
  void canListPrincipalsWithOneTeamAndUser() {
    final String userId = integrationTestUtils.createTestUser();
    final String teamId = integrationTestUtils.createTestTeam();
    userService.addUserToTeam(userId, teamId).block();
    StepVerifier
      .create(userService.findAllSolvers())
      .assertNext(team -> {
        assertThat(team.getMembers()).hasAtLeastOneElementOfType(UserEntity.class);
        assertThat(team.getMembers().iterator().next().getDisplayName())
          .isEqualTo(TestConstants.TEST_USER_DISPLAY_NAME);
        assertThat(team.getDisplayName()).isEqualTo(TestConstants.TEST_TEAM_DISPLAY_NAME);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can list principals consisting of a single user")
  void canListPrincipalsWithOneUser() {
    integrationTestUtils.createTestUser();
    StepVerifier
      .create(userService.findAllSolvers())
      .assertNext(user -> {
        assertThat(user.getDisplayName()).isEqualTo(TestConstants.TEST_USER_DISPLAY_NAME);
      })
      .verifyComplete();
  }

  @BeforeEach
  void setUp() {
    integrationTestUtils.resetState();
  }
}
