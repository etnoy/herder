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
package org.owasp.herder.application;

import lombok.RequiredArgsConstructor;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.flag.FlagTutorial;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.user.RefresherService;
import org.owasp.herder.user.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(
  prefix = "application.runner",
  value = "enabled",
  havingValue = "true",
  matchIfMissing = true
)
@Component
@RequiredArgsConstructor
public class StartupRunner implements ApplicationRunner {

  private final UserService userService;

  private final ModuleService moduleService;

  private final SubmissionService submissionService;

  private final RefresherService refresherService;

  private final FlagTutorial flagTutorial;

  @Override
  public void run(ApplicationArguments args) {
    if (Boolean.FALSE.equals(userService.existsByLoginName("admin").block())) {
      final String adminId = userService
        .createPasswordUser(
          "Administrator",
          "admin",
          "$2y$08$WpfUVZLcXNNpmM2VwSWlbe25dae.eEC99AOAVUiU5RaJmfFsE9B5G"
        )
        .block();
      userService.promote(adminId).block();
    }

    String userId1 = "";
    String userId2 = "";
    String userId3 = "";

    if (
      Boolean.FALSE.equals(userService.existsByDisplayName("Test user").block())
    ) {
      userId1 = userService.create("Test user").block();
    }

    if (
      Boolean.FALSE.equals(
        userService.existsByDisplayName("Test user 2").block()
      )
    ) {
      userId2 = userService.create("Test user 2").block();
    }

    if (
      Boolean.FALSE.equals(
        userService.existsByDisplayName("Test user 3").block()
      )
    ) {
      userId3 = userService.create("Test user 3").block();
    }

    if (
      Boolean.FALSE.equals(
        userService.teamExistsByDisplayName("Team 1").block()
      )
    ) {
      String teamId1 = userService.createTeam("Team 1").block();
      String teamId2 = userService.createTeam("Team 2").block();

      final ModuleEntity flagTutorialModule = moduleService
        .findByLocator("flag-tutorial")
        .block();
      if (flagTutorialModule != null) {
        final String flagTutorialId = flagTutorialModule.getId();

        submissionService
          .submitFlag(
            userId1,
            flagTutorialId,
            flagTutorial.getFlag(userId1).block()
          )
          .block();

        submissionService
          .submitFlag(
            userId2,
            flagTutorialId,
            flagTutorial.getFlag(userId2).block()
          )
          .block();

        submissionService
          .submitFlag(
            userId3,
            flagTutorialId,
            flagTutorial.getFlag(userId3).block()
          )
          .block();
      }

      userService.addUserToTeam(userId1, teamId1).block();
      userService.addUserToTeam(userId2, teamId1).block();
      userService.addUserToTeam(userId3, teamId2).block();

      refresherService.afterUserUpdate(userId1).block();
      refresherService.afterUserUpdate(userId2).block();
      refresherService.afterUserUpdate(userId3).block();
    }
    refresherService.refreshModuleLists().block();
    refresherService.refreshSubmissionRanks().block();
    refresherService.refreshScoreboard().block();
  }
}
