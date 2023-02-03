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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  @DisplayName("Can add user to team")
  void addMember_UserEntityIsMemberOfTeam_AddsUserToTeam() {
    final UserEntity newMember = TestConstants.TEST_USER_ENTITY.withTeamId(TestConstants.TEST_TEAM_ID);

    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(TestConstants.TEST_TEAM_ENTITY));
    when(teamRepository.save(any(TeamEntity.class))).thenAnswer(i -> Mono.just(i.getArguments()[0]));

    StepVerifier.create(teamService.addMember(TestConstants.TEST_TEAM_ID, newMember)).verifyComplete();

    final ArgumentCaptor<TeamEntity> teamEntityArgument = ArgumentCaptor.forClass(TeamEntity.class);
    verify(teamRepository).save(teamEntityArgument.capture());
    assertThat(teamEntityArgument.getValue().getMembers()).containsExactly(newMember);
  }

  @Test
  @DisplayName("Can error when adding user to team if user team id is null")
  void addMember_UserEntityIsNotMemberOfAnyTeam_Errors() {
    StepVerifier
      .create(teamService.addMember(TestConstants.TEST_TEAM_ID, TestConstants.TEST_USER_ENTITY))
      .expectErrorMatches(e ->
        e instanceof IllegalArgumentException && e.getMessage().equals("User has an incorrect team id")
      )
      .verify();
  }

  @Test
  @DisplayName("Can error if user is member of wrong team")
  void addMember_UserEntityIsNotMemberOfWrongTeam_Errors() {
    StepVerifier
      .create(teamService.addMember(TestConstants.TEST_TEAM_ID, TestConstants.TEST_USER_ENTITY.withTeamId("blargh")))
      .expectErrorMatches(e ->
        e instanceof IllegalArgumentException && e.getMessage().equals("User has an incorrect team id")
      )
      .verify();
  }

  @Test
  @DisplayName("Can expel user from team")
  void expel_UserIsNotLastMemberOfTeam_ExpelsUser() {
    final ArrayList<UserEntity> membersBeforeExpulsion = new ArrayList<>();
    membersBeforeExpulsion.add(mockUser);
    when(mockUser.getId()).thenReturn("blargh");

    membersBeforeExpulsion.add(TestConstants.TEST_USER_ENTITY.withId(TestConstants.TEST_USER_ID));
    final TeamEntity teamBeforeExpulsion = TestConstants.TEST_TEAM_ENTITY.withMembers(membersBeforeExpulsion);
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(teamBeforeExpulsion));
    when(teamRepository.save(any(TeamEntity.class))).thenAnswer(i -> Mono.just(i.getArguments()[0]));

    StepVerifier.create(teamService.expel(TestConstants.TEST_TEAM_ID, TestConstants.TEST_USER_ID)).verifyComplete();

    final ArgumentCaptor<TeamEntity> teamEntityArgument = ArgumentCaptor.forClass(TeamEntity.class);
    verify(teamRepository).save(teamEntityArgument.capture());
    assertThat(teamEntityArgument.getValue().getMembers()).containsExactly(mockUser);
  }

  @Test
  @DisplayName("Can expel user from team and deletes team if it was the last member")
  void expel_UserIsLastMemberOfTeam_ExpelsUserAndDeletesTeam() {
    final ArrayList<UserEntity> membersBeforeExpulsion = new ArrayList<>();

    membersBeforeExpulsion.add(TestConstants.TEST_USER_ENTITY.withId(TestConstants.TEST_USER_ID));
    final TeamEntity teamBeforeExpulsion = TestConstants.TEST_TEAM_ENTITY
      .withMembers(membersBeforeExpulsion)
      .withId(TestConstants.TEST_TEAM_ID);
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(teamBeforeExpulsion));
    when(teamRepository.deleteById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.empty());

    StepVerifier.create(teamService.expel(TestConstants.TEST_TEAM_ID, TestConstants.TEST_USER_ID)).verifyComplete();

    final ArgumentCaptor<String> teamEntityArgument = ArgumentCaptor.forClass(String.class);
    verify(teamRepository).deleteById(teamEntityArgument.capture());
    assertThat(teamEntityArgument.getValue()).isEqualTo(TestConstants.TEST_TEAM_ID);
  }

  @Test
  @DisplayName("Can error when expelling user if it is not a member of given team")
  void expel_UserNotInAnyTeam_ReturnsError() {
    final ArrayList<UserEntity> membersBeforeExpulsion = new ArrayList<>();

    final TeamEntity teamBeforeExpulsion = TestConstants.TEST_TEAM_ENTITY
      .withMembers(membersBeforeExpulsion)
      .withId(TestConstants.TEST_TEAM_ID);
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(teamBeforeExpulsion));

    StepVerifier
      .create(teamService.expel(TestConstants.TEST_TEAM_ID, TestConstants.TEST_USER_ID))
      .expectErrorMatches(e ->
        e instanceof IllegalStateException &&
        e.getMessage().equals(String.format("User \"%s\"  not found in team", TestConstants.TEST_USER_ID))
      )
      .verify();
  }

  @Test
  @DisplayName("Can return error when expelling user if it matches several users in given team")
  void expel_UserIdFoundInMultipleMembers_ReturnsError() {
    final ArrayList<UserEntity> membersBeforeExpulsion = new ArrayList<>();
    membersBeforeExpulsion.add(TestConstants.TEST_USER_ENTITY.withId(TestConstants.TEST_USER_ID));
    membersBeforeExpulsion.add(TestConstants.TEST_USER_ENTITY.withId(TestConstants.TEST_USER_ID));

    final TeamEntity teamBeforeExpulsion = TestConstants.TEST_TEAM_ENTITY
      .withMembers(membersBeforeExpulsion)
      .withId(TestConstants.TEST_TEAM_ID);
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(teamBeforeExpulsion));

    StepVerifier
      .create(teamService.expel(TestConstants.TEST_TEAM_ID, TestConstants.TEST_USER_ID))
      .expectErrorMatches(e ->
        e instanceof IllegalStateException &&
        e
          .getMessage()
          .equals(
            String.format(
              "Team with id \"%s\" contains the same user (id \"%s\") more than once",
              TestConstants.TEST_TEAM_ID,
              TestConstants.TEST_USER_ID
            )
          )
      )
      .verify();
  }

  @Test
  @DisplayName("Can error when creating team if team display name already exists")
  void create_TeamDisplayNameAlreadyExists_Errors() {
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
  @DisplayName("Can create team")
  void create_ValidDisplayName_TeamCreated() {
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
  @DisplayName("Can delete team")
  void delete_ValidTeamId_DeletesTeam() {
    when(teamRepository.deleteById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.empty());

    StepVerifier.create(teamService.delete(TestConstants.TEST_TEAM_ID)).verifyComplete();
  }

  @Test
  @DisplayName("Can check if team exists by display name")
  void existsByDisplayName_DisplayNameExists_ReturnsTrue() {
    when(teamRepository.findByDisplayName(TestConstants.TEST_TEAM_DISPLAY_NAME)).thenReturn(Mono.just(mockTeam));
    StepVerifier
      .create(teamService.existsByDisplayName(TestConstants.TEST_TEAM_DISPLAY_NAME))
      .expectNext(true)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can check if team does not exist by display name")
  void existsByDisplayName_DisplayNameDoesNotExist_ReturnsFalse() {
    when(teamRepository.findByDisplayName(TestConstants.TEST_TEAM_DISPLAY_NAME)).thenReturn(Mono.empty());
    StepVerifier
      .create(teamService.existsByDisplayName(TestConstants.TEST_TEAM_DISPLAY_NAME))
      .expectNext(false)
      .verifyComplete();
  }

  @Test
  @DisplayName("Can check if team exists by id")
  void existsById_IdExists_ReturnsTrue() {
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(mockTeam));
    StepVerifier.create(teamService.existsById(TestConstants.TEST_TEAM_ID)).expectNext(true).verifyComplete();
  }

  @Test
  @DisplayName("Can check if does not team exist by id")
  void existsById_IdDoesNotExist_ReturnsFalse() {
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.empty());
    StepVerifier.create(teamService.existsById(TestConstants.TEST_TEAM_ID)).expectNext(false).verifyComplete();
  }

  @Test
  @DisplayName("Can find all teams")
  void findAll_TeamsExist_ReturnsTeams() {
    when(teamRepository.findAll()).thenReturn(Flux.just(mockTeam));
    StepVerifier.create(teamService.findAll()).expectNext(mockTeam).verifyComplete();
  }

  @Test
  @DisplayName("Can find team by id")
  void findById_TeamExists_ReturnsTeam() {
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(mockTeam));
    StepVerifier.create(teamService.findById(TestConstants.TEST_TEAM_ID)).expectNext(mockTeam).verifyComplete();
  }

  @Test
  @DisplayName("Can return empty when finding nonexistent team by id")
  void findById_TeamDoesNotExist_ReturnsTeam() {
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.empty());
    StepVerifier.create(teamService.findById(TestConstants.TEST_TEAM_ID)).verifyComplete();
  }

  @Test
  @DisplayName("Can get existing team by id")
  void getById_TeamExists_ReturnsUser() {
    when(teamRepository.findById(TestConstants.TEST_TEAM_ID)).thenReturn(Mono.just(mockTeam));
    StepVerifier.create(teamService.getById(TestConstants.TEST_TEAM_ID)).expectNext(mockTeam).verifyComplete();
  }

  @Test
  @DisplayName("Can error when getting nonexistent team by id")
  void getById_UserDoesNotExist_Errors() {
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
  @DisplayName("Can get team by member id")
  void getByMemberId_UserIsInTeam_ReturnsTeam() {
    when(teamRepository.findAllByMembersId(TestConstants.TEST_USER_ID)).thenReturn(Flux.just(mockTeam));
    StepVerifier.create(teamService.getByMemberId(TestConstants.TEST_USER_ID)).expectNext(mockTeam).verifyComplete();
  }

  @Test
  @DisplayName("Can error when getting nonexistent team by member id")
  void getByMemberId_UserNotInTeam_Errors() {
    when(teamRepository.findAllByMembersId(TestConstants.TEST_USER_ID)).thenReturn(Flux.just());
    StepVerifier
      .create(teamService.getByMemberId(TestConstants.TEST_USER_ID))
      .expectErrorMatches(throwable ->
        throwable instanceof TeamNotFoundException &&
        throwable
          .getMessage()
          .equals(String.format("User id \"%s\" is not a member of any team", TestConstants.TEST_USER_ID))
      )
      .verify();
  }

  @Test
  @DisplayName("Can error when getting team by member id if user is member of more than one team")
  void getByMemberId_UserInMoreThanOneTeam_ReturnsError() {
    when(teamRepository.findAllByMembersId(TestConstants.TEST_USER_ID)).thenReturn(Flux.just(mockTeam, mockTeam));
    StepVerifier
      .create(teamService.getByMemberId(TestConstants.TEST_USER_ID))
      .expectErrorMatches(throwable ->
        throwable instanceof IllegalStateException &&
        throwable
          .getMessage()
          .equals(
            String.format(String.format("User id \"%s\" is member of more than one team", TestConstants.TEST_USER_ID))
          )
      )
      .verify();
  }

  @Test
  @DisplayName("Can update embedded user in team after user is updated")
  void updateTeamMember_UserInTeam_ReplacesUserEntity() {
    final ArrayList<UserEntity> membersBeforeUpdate = new ArrayList<>();
    final UserEntity oldUserEntity = TestConstants.TEST_USER_ENTITY.withId(TestConstants.TEST_USER_ID);
    membersBeforeUpdate.add(TestConstants.TEST_USER_ENTITY.withId(TestConstants.TEST_USER_ID));
    when(mockUser.getId()).thenReturn("blargh");
    membersBeforeUpdate.add(mockUser);

    final TeamEntity teamBeforeUpdate = TestConstants.TEST_TEAM_ENTITY.withMembers(membersBeforeUpdate);
    final UserEntity newUserEntity = oldUserEntity.withDisplayName("New Name");

    when(teamRepository.save(any(TeamEntity.class))).thenAnswer(i -> Mono.just(i.getArguments()[0]));

    when(teamRepository.findAllByMembersId(TestConstants.TEST_USER_ID)).thenReturn(Flux.just(teamBeforeUpdate));
    StepVerifier.create(teamService.updateTeamMember(newUserEntity)).verifyComplete();

    final ArgumentCaptor<TeamEntity> teamEntityArgument = ArgumentCaptor.forClass(TeamEntity.class);
    verify(teamRepository).save(teamEntityArgument.capture());
    assertThat(teamEntityArgument.getValue().getMembers()).containsExactly(newUserEntity, mockUser);
  }

  private void setClock(final Clock testClock) {
    when(clock.instant()).thenReturn(testClock.instant());
    when(clock.getZone()).thenReturn(testClock.getZone());
  }

  @BeforeEach
  void setup() {
    teamService = new TeamService(teamRepository, clock);

    mockUser = mock(UserEntity.class);
    mockTeam = mock(TeamEntity.class);
  }
}
