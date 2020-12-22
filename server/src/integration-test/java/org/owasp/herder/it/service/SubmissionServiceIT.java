/* 
 * Copyright 2018-2020 Jonathan Jogenfors, jonathan@jogenfors.se
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
package org.owasp.herder.it.service;

import java.time.Clock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.owasp.herder.exception.ModuleAlreadySolvedException;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.CorrectionRepository;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionRepository;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.util.TestUtils;
import org.owasp.herder.user.UserRepository;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {"application.runner.enabled=false"})
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("SubmissionService integration test")
class SubmissionServiceIT {
  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Autowired SubmissionService submissionService;

  @Autowired UserService userService;

  @Autowired ModuleService moduleService;

  @Autowired Clock clock;

  @Autowired ModuleRepository moduleRepository;

  @Autowired SubmissionRepository submissionRepository;

  @Autowired CorrectionRepository correctionRepository;

  @Autowired UserRepository userRepository;

  @Autowired TestUtils testService;

  @Autowired FlagHandler flagComponent;

  @BeforeEach
  private void clear() {
    testService.deleteAll().block();
  }

  @Test
  void submitFlag_DuplicateValidStaticFlag_ReturnModuleAlreadySolvedException() {
    final String flag = "thisisaflag";
    final String moduleName = "test-module";

    final Mono<Long> userIdMono = userService.create("TestUser");

    moduleService.create(moduleName).block();
    moduleService.setStaticFlag(moduleName, flag).block();

    StepVerifier.create(
            userIdMono.flatMapMany(
                userId ->
                    submissionService
                        .submit(userId, moduleName, flag)
                        .repeat(2)
                        .map(Submission::isValid)))
        .expectNext(true)
        .expectError(ModuleAlreadySolvedException.class)
        .verify();
  }

  @Test
  void submitFlag_ValidStaticFlag_Success() {
    final String flag = "thisisaflag";
    final String moduleName = "test-module";

    final Mono<Long> userIdMono = userService.create("TestUser");

    moduleService.create(moduleName).block();

    moduleService.setStaticFlag(moduleName, flag).block();

    StepVerifier.create(
            userIdMono.flatMap(
                userId ->
                    submissionService.submit(userId, moduleName, flag).map(Submission::isValid)))
        .expectNext(true)
        .expectComplete()
        .verify();
  }
}
