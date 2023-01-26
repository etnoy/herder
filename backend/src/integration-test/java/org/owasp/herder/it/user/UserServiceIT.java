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
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.TeamRepository;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserRepository;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.test.StepVerifier;

@DisplayName("UserService integration tests")
class UserServiceIT extends BaseIT {

  @Autowired
  SubmissionService submissionService;

  @Autowired
  SubmissionRepository submissionRepository;

  @Autowired
  UserService userService;

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
  @DisplayName("Can do nothing if nothing changed for a single user")
  void canBeIdempotentForTeams() {
    final String userId = integrationTestUtils.createTestUser();
    final String teamId = integrationTestUtils.createTestTeam();
    userService.addUserToTeam(userId, teamId).block();

    final UserEntity user = userService.getById(userId).block();
    final TeamEntity team = userService.getTeamById(teamId).block();

    userService.afterUserUpdate(userId).block();

    StepVerifier.create(userService.findAllUsers()).expectNext(user).verifyComplete();
    StepVerifier.create(userService.findAllTeams()).expectNext(team).verifyComplete();
  }

  @Test
  @DisplayName("Can delete a team when last member is deleted")
  void canDeleteTeamWhenLastMemberIsDeleted() {
    final String userId = integrationTestUtils.createTestUser();
    final String teamId = integrationTestUtils.createTestTeam();
    userService.addUserToTeam(userId, teamId).block();
    userService.delete(userId).block();
    userService.afterUserDeletion(userId).block();
    StepVerifier.create(teamRepository.findAll()).verifyComplete();
  }

  @Test
  @DisplayName("Can refresh a submission with last user removed from team")
  void canRefreshLastUserRemovedFromTeamInSubmissions() {
    final String userId = integrationTestUtils.createTestUser();
    final String moduleId = integrationTestUtils.createStaticTestModule();
    final String teamId = integrationTestUtils.createTestTeam();

    userService.addUserToTeam(userId, teamId).block();

    submissionService.submitFlag(userId, moduleId, "asdf").block();
    integrationTestUtils.submitValidFlag(userId, moduleId);

    userService.clearTeamForUser(userId).block();
    userService.afterUserUpdate(userId).block();

    Consumer<Submission> asserter = submission -> {
      assertThat(submission.getUserId()).isEqualTo(userId);
      assertThat(submission.getTeamId()).isNull();
    };

    StepVerifier
      .create(submissionService.findAllSubmissions())
      .assertNext(asserter)
      .assertNext(asserter)
      .verifyComplete();
  }

  @DisplayName("Can refresh a submission with user added to team")
  void canRefreshUserAddedToTeamInSubmissions() {
    final String userId = integrationTestUtils.createTestUser();
    final String moduleId = integrationTestUtils.createStaticTestModule();
    final String teamId = integrationTestUtils.createTestTeam();

    submissionService.submitFlag(userId, moduleId, "asdf").block();
    integrationTestUtils.submitValidFlag(userId, moduleId);

    userService.addUserToTeam(userId, teamId).block();
    userService.afterUserUpdate(userId).block();

    Consumer<Submission> asserter = submission -> {
      assertThat(submission.getUserId()).isEqualTo(userId);
      assertThat(submission.getTeamId()).isEqualTo(teamId);
    };

    StepVerifier
      .create(submissionService.findAllSubmissions())
      .assertNext(asserter)
      .assertNext(asserter)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can refresh an updated user in a team")
  void canRefreshUserInExistingTeam() {
    final String userId = integrationTestUtils.createTestUser();
    final String teamId = integrationTestUtils.createTestTeam();
    final String newDisplayName = "New displayName";
    userService.addUserToTeam(userId, teamId).block();
    userService.setDisplayName(userId, newDisplayName).block();
    userService.afterUserUpdate(userId).block();
    StepVerifier
      .create(teamRepository.findAll())
      .assertNext(team -> {
        assertThat(team.getMembers().get(0).getDisplayName()).isEqualTo(newDisplayName);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can delete team when last member is removed")
  void canRefreshUserRemovedFromTeam() {
    final String userId = integrationTestUtils.createTestUser();
    final String teamId = integrationTestUtils.createTestTeam();
    userService.addUserToTeam(userId, teamId).block();
    userService.clearTeamForUser(userId).block();
    userService.afterUserUpdate(userId).block();
    StepVerifier.create(userService.findAllTeams()).verifyComplete();
  }

  @Test
  @DisplayName("Can refresh a user removed from a team containing two users")
  void canRefreshUserRemovedFromTeamWithTwoUsers() {
    final String userId1 = userService.create("Test user 1").block();
    final String userId2 = userService.create("Test user 2").block();

    final String teamId = integrationTestUtils.createTestTeam();

    userService.addUserToTeam(userId1, teamId).block();
    userService.addUserToTeam(userId2, teamId).block();

    final UserEntity user2 = userService.getById(userId2).block();

    userService.clearTeamForUser(userId1).block();
    userService.afterUserUpdate(userId1).block();
    StepVerifier
      .create(userService.getTeamById(teamId))
      .assertNext(team -> {
        assertThat(team.getMembers()).containsExactly(user2);
      })
      .verifyComplete();
  }

  @Test
  @DisplayName("Can refresh a user removed from two previous teams")
  void canRefreshUserRemovedFromTwoTeams() {
    final String userId = integrationTestUtils.createTestUser();

    final String teamId1 = userService.createTeam("Test team 1").block();
    final String teamId2 = userService.createTeam("Test team 2").block();

    userService.addUserToTeam(userId, teamId1).block();
    userService.clearTeamForUser(userId).block();
    userService.addUserToTeam(userId, teamId2).block();
    userService.clearTeamForUser(userId).block();

    userService.afterUserUpdate(userId).block();
    StepVerifier.create(userService.getTeamByUserId(userId)).verifyComplete();
  }

  @Test
  @DisplayName("Can refresh a user that switched teams")
  void canRefreshUserThatSwitchedTeams() {
    final String userId = integrationTestUtils.createTestUser();

    final String teamId1 = userService.createTeam("Test team 1").block();
    final String teamId2 = userService.createTeam("Test team 2").block();

    userService.addUserToTeam(userId, teamId1).block();
    userService.clearTeamForUser(userId).block();
    userService.addUserToTeam(userId, teamId2).block();

    userService.afterUserUpdate(userId).block();

    StepVerifier
      .create(userService.getTeamByUserId(userId))
      .expectNextMatches(team -> team.getId().equals(teamId2))
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
