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
package org.owasp.herder.test.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.application.StartupRunner;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.csrf.CsrfTutorial;
import org.owasp.herder.module.flag.FlagTutorial;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorial;
import org.owasp.herder.module.xss.XssTutorial;
import org.owasp.herder.scoring.ScoreboardService;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.TeamService;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@DisplayName("StartupRunner unit tests")
class StartupRunnerTest extends BaseTest {

  StartupRunner startupRunner;

  @Mock
  UserService userService;

  @Mock
  TeamService teamService;

  @Mock
  ModuleService moduleService;

  @Mock
  SubmissionService submissionService;

  @Mock
  XssTutorial xssTutorial;

  @Mock
  SqlInjectionTutorial sqlInjectionTutorial;

  @Mock
  CsrfTutorial csrfTutorial;

  @Mock
  FlagTutorial flagTutorial;

  @Mock
  FlagHandler flagHandler;

  @Mock
  ScoreboardService scoreboardService;

  @Test
  @DisplayName("Can initialize application from empty database")
  void run_DatabaseEmpty_Initializes() {
    when(
      userService.createPasswordUser(
        "Administrator",
        "admin",
        "$2y$08$WpfUVZLcXNNpmM2VwSWlbe25dae.eEC99AOAVUiU5RaJmfFsE9B5G"
      )
    )
      .thenReturn(Mono.just(TestConstants.TEST_USER_ID));

    when(userService.create(any(String.class))).thenReturn(Mono.just(TestConstants.TEST_USER_ID));

    when(userService.promote(TestConstants.TEST_USER_ID)).thenReturn(Mono.empty());

    when(userService.existsByLoginName(any(String.class))).thenReturn(Mono.just(false));
    when(userService.existsByDisplayName(any(String.class))).thenReturn(Mono.just(false));

    when(teamService.existsByDisplayName("Team 1")).thenReturn(Mono.just(false));

    when(moduleService.findByLocator(any(String.class)))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY.withId(TestConstants.TEST_MODULE_ID)));

    when(moduleService.refreshModuleLists()).thenReturn(Mono.empty());
    when(submissionService.refreshSubmissionRanks()).thenReturn(Mono.empty());
    when(submissionService.submitFlag(any(String.class), any(String.class), any(String.class)))
      .thenReturn(Mono.empty());

    when(scoreboardService.refreshScoreboard()).thenReturn(Mono.empty());

    when(flagTutorial.getFlag(any(String.class))).thenReturn(Mono.just(TestConstants.TEST_STATIC_FLAG));
    when(userService.addUserToTeam(any(), any())).thenReturn(Mono.empty());

    when(teamService.create(any(String.class))).thenReturn(Mono.just(TestConstants.TEST_TEAM_ID));

    when(userService.getById(any(String.class))).thenReturn(Mono.just(TestConstants.TEST_USER_ENTITY));
    when(teamService.addMember(any(String.class), any(UserEntity.class))).thenReturn(Mono.empty());

    when(submissionService.setTeamIdOfUserSubmissions(any(String.class), any(String.class))).thenReturn(Mono.empty());

    assertDoesNotThrow(() -> startupRunner.run(null));
  }

  @Test
  @DisplayName("Can initialize application with populated database")
  void run_DatabasePopulated_Initializes() {
    when(userService.existsByLoginName(any(String.class))).thenReturn(Mono.just(true));
    when(userService.existsByDisplayName(any(String.class))).thenReturn(Mono.just(true));
    when(teamService.existsByDisplayName("Team 1")).thenReturn(Mono.just(true));

    when(moduleService.refreshModuleLists()).thenReturn(Mono.empty());
    when(submissionService.refreshSubmissionRanks()).thenReturn(Mono.empty());

    when(scoreboardService.refreshScoreboard()).thenReturn(Mono.empty());

    assertDoesNotThrow(() -> startupRunner.run(null));
  }

  @BeforeEach
  void setup() {
    startupRunner =
      new StartupRunner(userService, teamService, moduleService, submissionService, scoreboardService, flagTutorial);
  }
}
