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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.TeamRepository;
import org.owasp.herder.user.TeamService;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserRepository;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.test.StepVerifier;

@DisplayName("TeamService integration tests")
class TeamServiceIT extends BaseIT {

  @Autowired
  SubmissionService submissionService;

  @Autowired
  SubmissionRepository submissionRepository;

  @Autowired
  UserService userService;

  @Autowired
  TeamService teamService;

  @Autowired
  ModuleService moduleService;

  @Autowired
  TeamRepository teamRepository;

  @Autowired
  UserRepository userRepository;

  @Autowired
  FlagHandler flagHandler;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @MockBean
  FlagSubmissionRateLimiter flagSubmissionRateLimiter;

  @MockBean
  InvalidFlagRateLimiter invalidFlagRateLimiter;

  @Test
  @DisplayName("Can add user to team")
  void canAddUserToTeam() {
    final String userId = integrationTestUtils.createTestUser();
    final String teamId = integrationTestUtils.createTestTeam();

    userService.addUserToTeam(userId, teamId).block();
    final UserEntity user = userService.getById(userId).block();

    teamService.addMember(teamId, user).block();

    StepVerifier
      .create(teamService.getById(teamId))
      .assertNext(returnedTeam -> {
        assertThat(returnedTeam.getMembers()).containsExactly(user);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can update the embedded user entity in a team after user entity has changed")
  void canUpdateEmbeddedUserEntity() {
    final String userId = integrationTestUtils.createTestUser();
    final String teamId = integrationTestUtils.createTestTeam();
    userService.addUserToTeam(userId, teamId).block();

    final UserEntity user = userService.getById(userId).block();
    teamService.addMember(teamId, user).block();

    final UserEntity renamedUser = userService.setDisplayName(userId, "New name").block();
    teamService.updateTeamMember(renamedUser).block();

    StepVerifier
      .create(teamService.getById(teamId))
      .assertNext(returnedTeam -> {
        assertThat(returnedTeam.getMembers()).extracting(UserEntity::getDisplayName).containsExactly("New name");
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can remove user from team")
  void canRemoveUserFromTeam() {
    final String userId = integrationTestUtils.createTestUser();
    final String teamId = integrationTestUtils.createTestTeam();

    final String userId2 = userService
      .createPasswordUser("Test User 2", "testuser", TestConstants.HASHED_TEST_PASSWORD)
      .block();

    userService.addUserToTeam(userId, teamId).block();
    final UserEntity user = userService.getById(userId).block();
    teamService.addMember(teamId, user).block();

    userService.addUserToTeam(userId2, teamId).block();

    final UserEntity user2 = userService.getById(userId2).block();
    teamService.addMember(teamId, user2).block();

    userService.clearTeamForUser(userId).block();
    teamService.expel(teamId, userId).block();

    StepVerifier
      .create(teamService.getById(teamId))
      .assertNext(returnedTeam -> {
        assertThat(returnedTeam.getMembers()).containsExactly(user2);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can return error when removing user from team it is not a member of")
  void canReturnErrorWhenRemovingUserFromTeamItDoesntBelongTo() {
    final String userId = integrationTestUtils.createTestUser();
    final String teamId = integrationTestUtils.createTestTeam();

    StepVerifier
      .create(teamService.expel(teamId, userId))
      .expectErrorMatches(throwable ->
        throwable instanceof IllegalStateException &&
        throwable.getMessage().equals(String.format("User \"%s\"  not found in team", userId))
      )
      .verify();
  }

  @Test
  @DisplayName("Can update team memberships after user switches teams")
  void canUpdateMembershipsAfterTeamChange() {
    final String userId = integrationTestUtils.createTestUser();
    final String teamId1 = integrationTestUtils.createTestTeam();
    final String teamId2 = teamService.create(TestConstants.TEST_TEAM_DISPLAY_NAME + " 2").block();

    userService.addUserToTeam(userId, teamId1).block();
    final UserEntity user = userService.getById(userId).block();
    teamService.addMember(teamId1, user).block();

    userService.clearTeamForUser(userId).block();
    teamService.expel(teamId1, userId).block();
    userService.addUserToTeam(userId, teamId2).block();
    final UserEntity userWithNewTeam = userService.getById(userId).block();
    teamService.addMember(teamId2, userWithNewTeam).block();

    // The team should no longer exist because the last user left
    StepVerifier.create(teamService.existsById(teamId1)).expectNext(false).verifyComplete();

    StepVerifier
      .create(teamService.getById(teamId2))
      .assertNext(returnedTeam -> {
        assertThat(returnedTeam.getMembers()).containsExactly(userWithNewTeam);
      })
      .verifyComplete();
  }

  @BeforeEach
  void setup() {
    integrationTestUtils.resetState();

    // Bypass the rate limiter
    final Bucket mockBucket = mock(Bucket.class);
    when(mockBucket.tryConsume(1)).thenReturn(true);
    when(flagSubmissionRateLimiter.resolveBucket(any(String.class))).thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(any(String.class))).thenReturn(mockBucket);
  }
}
