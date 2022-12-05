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
package org.owasp.herder.it.module.csrf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleInitializer;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.csrf.CsrfAttackRepository;
import org.owasp.herder.module.csrf.CsrfService;
import org.owasp.herder.module.csrf.CsrfTutorial;
import org.owasp.herder.module.csrf.CsrfTutorialResult;
import org.owasp.herder.scoring.ScoreboardService;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

@DisplayName("CsrfTutorial integration tests")
class CsrfTutorialIT extends BaseIT {
  CsrfTutorial csrfTutorial;

  @Autowired
  CsrfService csrfService;

  @Autowired
  CsrfAttackRepository csrfAttackRespository;

  @Autowired
  UserService userService;

  @Autowired
  ModuleService moduleService;

  @Autowired
  SubmissionService submissionService;

  @Autowired
  ScoreboardService scoreboardService;

  @Autowired
  FlagHandler flagHandler;

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  ModuleInitializer moduleInitializer;

  @Test
  void activate_NonExistentPseudonym_ReturnsError() {
    final String userId = userService.create("Attacker").block();

    StepVerifier
      .create(csrfTutorial.attack(userId, "Unknown target ID"))
      .assertNext(
        result -> {
          assertThat(result.getMessage()).isNull();
          assertThat(result.getError()).isEqualTo("Unknown target ID");
        }
      )
      .verifyComplete();
  }

  @Test
  void getTutorial_CorrectAttack_Success() {
    final String userId1 = userService.create("TestUser1").block();
    final String userId2 = userService.create("TestUser2").block();

    final CsrfTutorialResult tutorialResult = csrfTutorial
      .getTutorial(userId1)
      .block();

    csrfTutorial.attack(userId2, tutorialResult.getPseudonym()).block();

    StepVerifier
      .create(csrfTutorial.getTutorial(userId1))
      .assertNext(
        result -> {
          assertThat(result.getFlag()).isNotNull();
          assertThat(result.getError()).isNull();
        }
      )
      .verifyComplete();
  }

  @BeforeEach
  void setup() {
    integrationTestUtils.resetState();

    moduleInitializer = new ModuleInitializer(null, moduleService);

    csrfTutorial = new CsrfTutorial(csrfService, flagHandler);

    moduleInitializer.initializeModule(csrfTutorial).block();
  }

  @Test
  void getTutorial_SelfActivation_NotAllowed() {
    final String userId = userService.create("TestUser").block();

    final CsrfTutorialResult tutorialResult = csrfTutorial
      .getTutorial(userId)
      .block();

    StepVerifier
      .create(csrfTutorial.attack(userId, tutorialResult.getPseudonym()))
      .assertNext(
        result -> {
          assertThat(result.getMessage()).isNull();
          assertThat(result.getError())
            .isEqualTo("You cannot activate yourself");
        }
      )
      .verifyComplete();
  }
}
