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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.authentication.PasswordAuth;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.crypto.WebTokenKeyManager;
import org.owasp.herder.exception.DuplicateTeamDisplayNameException;
import org.owasp.herder.exception.TeamNotFoundException;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.ClassService;
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.TeamRepository;
import org.owasp.herder.user.TeamService;
import org.owasp.herder.user.UserEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamService unit tests")
class TeamServiceTest extends BaseTest {

  private TeamService teamService;

  @Mock
  TeamRepository teamRepository;

  @Mock
  SubmissionRepository submissionRepository;

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
  void create_TeamDisplayNameAlreadyExists_ThrowsException() {
    when(teamRepository.findByDisplayName(TestConstants.TEST_TEAM_DISPLAY_NAME)).thenReturn(Mono.just(mockTeam));

    StepVerifier
      .create(teamService.create(TestConstants.TEST_TEAM_DISPLAY_NAME))
      .expectErrorMatches(e ->
        e instanceof DuplicateTeamDisplayNameException &&
        e.getMessage().equals("Team display name \"" + TestConstants.TEST_TEAM_DISPLAY_NAME + "\" already exists")
      )
      .verify();
  }

  @Test
  void create_TeamDisplayNameDoesNotExist_TeamCreated() {
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
      .create(teamService.create(TestConstants.TEST_TEAM_DISPLAY_NAME))
      .expectNext(TestConstants.TEST_TEAM_ID)
      .verifyComplete();
  }

  @Test
  void delete_ValidTeamId_DeletesTeam() {
    when(teamRepository.deleteById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.empty());

    StepVerifier.create(teamService.delete(TestConstants.TEST_TEAM_ID)).verifyComplete();
  }

  @Test
  void existsByDisplayName_DisplayNameExists_ReturnsTrue() {
    when(teamRepository.findByDisplayName(TestConstants.TEST_TEAM_DISPLAY_NAME)).thenReturn(Mono.just(mockTeam));
    StepVerifier
      .create(teamService.existsByDisplayName(TestConstants.TEST_TEAM_DISPLAY_NAME))
      .expectNext(true)
      .verifyComplete();
  }

  @Test
  void existsByDisplayName_DisplayNameDoesNotExist_ReturnsFalse() {
    when(teamRepository.findByDisplayName(TestConstants.TEST_TEAM_DISPLAY_NAME)).thenReturn(Mono.empty());
    StepVerifier
      .create(teamService.existsByDisplayName(TestConstants.TEST_TEAM_DISPLAY_NAME))
      .expectNext(false)
      .verifyComplete();
  }

  @Test
  void existsById_DisplayNameExists_ReturnsTrue() {
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(mockTeam));
    StepVerifier.create(teamService.existsById(TestConstants.TEST_TEAM_ID)).expectNext(true).verifyComplete();
  }

  @Test
  void existsById_DisplayNameDoesNotExist_ReturnsFalse() {
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.empty());
    StepVerifier.create(teamService.existsById(TestConstants.TEST_TEAM_ID)).expectNext(false).verifyComplete();
  }

  @Test
  void findAll_TeamsExist_ReturnsTeams() {
    when(teamRepository.findAll()).thenReturn(Flux.just(mockTeam));
    StepVerifier.create(teamService.findAll()).expectNext(mockTeam).verifyComplete();
  }

  @Test
  void findById_TeamExists_ReturnsTeam() {
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(mockTeam));
    StepVerifier.create(teamService.findById(TestConstants.TEST_TEAM_ID)).expectNext(mockTeam).verifyComplete();
  }

  @Test
  void getTeamById_TeamExists_ReturnsUser() {
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(mockTeam));
    StepVerifier.create(teamService.getById(TestConstants.TEST_TEAM_ID)).expectNext(mockTeam).verifyComplete();
  }

  @Test
  void getTeamById_UserDoesNotExist_ReturnsError() {
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.empty());
    StepVerifier
      .create(teamService.getById(TestConstants.TEST_TEAM_ID))
      .expectErrorMatches(throwable ->
        throwable instanceof TeamNotFoundException &&
        throwable.getMessage().equals("Team id \"" + TestConstants.TEST_TEAM_ID + "\" not found")
      )
      .verify();
  }

  @Test
  void getByMemberId_UserIsInTeam_ReturnsTeam() {
    when(teamRepository.findAllByMembersId(TestConstants.TEST_USER_ID)).thenReturn(Flux.just(mockTeam));
    StepVerifier.create(teamService.getByMemberId(TestConstants.TEST_USER_ID)).expectNext(mockTeam).verifyComplete();
  }

  private void setClock(final Clock testClock) {
    when(clock.instant()).thenReturn(testClock.instant());
    when(clock.getZone()).thenReturn(testClock.getZone());
  }

  @BeforeEach
  void setup() {
    teamService = new TeamService(teamRepository, submissionRepository, clock);

    mockUser = mock(UserEntity.class);
    mockTeam = mock(TeamEntity.class);
  }
}
