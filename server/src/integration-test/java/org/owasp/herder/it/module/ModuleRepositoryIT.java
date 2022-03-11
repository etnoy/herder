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
package org.owasp.herder.it.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleListItem;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import io.github.bucket4j.Bucket;
import reactor.test.StepVerifier;

@DisplayName("ModuleRepository integration tests")
class ModuleRepositoryIT extends BaseIT {
  @Autowired IntegrationTestUtils integrationTestUtils;

  @Autowired ModuleService moduleService;

  @Autowired ModuleRepository moduleRepository;

  @Autowired UserService userService;

  @MockBean FlagSubmissionRateLimiter flagSubmissionRateLimiter;

  @MockBean InvalidFlagRateLimiter invalidFlagRateLimiter;

  @Autowired SubmissionService submissionService;

  @Test
  @DisplayName("Can show a module list without closed modules")
  void canFindAllOpenModulesWithClosedModules() {
    final String moduleId = integrationTestUtils.createStaticTestModule();

    moduleService.close(moduleId).block();

    StepVerifier.create(moduleRepository.findAllOpen()).verifyComplete();
  }

  @Test
  @DisplayName("Can show an empty module list")
  void canFindAllOpenModulesWithoutModules() {
    StepVerifier.create(moduleRepository.findAllOpen()).verifyComplete();
  }

  @Test
  @DisplayName("Can find a single open module")
  void canFindAllOpenWithSingleOpenModule() {
    final String moduleId = integrationTestUtils.createStaticTestModule();

    moduleService.open(moduleId).block();

    StepVerifier.create(moduleRepository.findAllOpen())
        .expectNextMatches(module -> module.getId().equals(moduleId))
        .verifyComplete();
  }

  @Test
  @DisplayName("Can find a module solved by another user")
  void canFindModuleSolvedByAnotherUser() {
    final String moduleId = integrationTestUtils.createStaticTestModule();
    final String userId = integrationTestUtils.createTestUser();
    final String anotherUserId = userService.create("Test User 2").block();

    integrationTestUtils.submitValidFlag(anotherUserId, moduleId);

    final ModuleListItem moduleListItem =
        ModuleListItem.builder()
            .id(moduleId)
            .locator(TestConstants.TEST_MODULE_LOCATOR)
            .name(TestConstants.TEST_MODULE_NAME)
            .isSolved(false)
            .build();

    StepVerifier.create(
            moduleRepository.findByLocatorWithSolutionStatus(
                userId, TestConstants.TEST_MODULE_LOCATOR))
        .expectNext(moduleListItem)
        .verifyComplete();
  }

  @Test
  @DisplayName("Can find a module with invalid submissions")
  void canFindModuleWithInvalidSolution() {
    final String moduleId = integrationTestUtils.createStaticTestModule();
    final String userId = integrationTestUtils.createTestUser();

    integrationTestUtils.submitInvalidFlag(userId, moduleId);
    integrationTestUtils.submitInvalidFlag(userId, moduleId);
    integrationTestUtils.submitInvalidFlag(userId, moduleId);
    integrationTestUtils.submitInvalidFlag(userId, moduleId);

    final ModuleListItem moduleListItem =
        ModuleListItem.builder()
            .id(moduleId)
            .locator(TestConstants.TEST_MODULE_LOCATOR)
            .name(TestConstants.TEST_MODULE_NAME)
            .isSolved(false)
            .build();

    StepVerifier.create(
            moduleRepository.findByLocatorWithSolutionStatus(
                userId, TestConstants.TEST_MODULE_LOCATOR))
        .expectNext(moduleListItem)
        .verifyComplete();
  }

  @Test
  @DisplayName("Can find open modules for user")
  void canFindOpenModulesForUser() {
    final String moduleId1 = moduleService.create("Test Module 1", "test-1").block();
    final String moduleId2 = moduleService.create("Test Module 2", "test-2").block();
    final String moduleId3 = moduleService.create("Test Module 3", "test-3").block();

    moduleService.close(moduleId2).block();

    final String userId = integrationTestUtils.createTestUser();

    integrationTestUtils.submitValidFlag(userId, moduleId1);
    integrationTestUtils.submitInvalidFlag(userId, moduleId3);

    final ModuleListItem moduleListItem1 =
        ModuleListItem.builder()
            .id(moduleId1)
            .locator("test-1")
            .name("Test Module 1")
            .isSolved(true)
            .build();

    final ModuleListItem moduleListItem3 =
        ModuleListItem.builder()
            .id(moduleId3)
            .locator("test-3")
            .name("Test Module 3")
            .isSolved(false)
            .build();

    final List<ModuleListItem> moduleItems = new ArrayList<>();

    moduleItems.add(moduleListItem1);
    moduleItems.add(moduleListItem3);

    StepVerifier.create(moduleRepository.findAllOpenWithSolutionStatus(userId))
        .recordWith(ArrayList::new)
        .expectNextCount(2)
        .consumeRecordedWith(l -> assertThat(l).containsExactlyElementsOf(moduleItems))
        .verifyComplete();
  }

  @Test
  @DisplayName("Can find a solved module")
  void canFindSolvedModule() {
    final String moduleId = integrationTestUtils.createStaticTestModule();
    final String userId = integrationTestUtils.createTestUser();

    integrationTestUtils.submitValidFlag(userId, moduleId);

    final ModuleListItem moduleListItem =
        ModuleListItem.builder()
            .id(moduleId)
            .locator(TestConstants.TEST_MODULE_LOCATOR)
            .name(TestConstants.TEST_MODULE_NAME)
            .isSolved(true)
            .build();

    StepVerifier.create(
            moduleRepository.findByLocatorWithSolutionStatus(
                userId, TestConstants.TEST_MODULE_LOCATOR))
        .expectNext(moduleListItem)
        .verifyComplete();
  }

  @Test
  @DisplayName("Can find an unsolved module")
  void canFindUnsolvedModule() {
    final String moduleId = integrationTestUtils.createStaticTestModule();
    final String userId = integrationTestUtils.createTestUser();

    final ModuleListItem moduleListItem =
        ModuleListItem.builder()
            .id(moduleId)
            .locator(TestConstants.TEST_MODULE_LOCATOR)
            .name(TestConstants.TEST_MODULE_NAME)
            .isSolved(false)
            .build();

    StepVerifier.create(
            moduleRepository.findByLocatorWithSolutionStatus(
                userId, TestConstants.TEST_MODULE_LOCATOR))
        .expectNext(moduleListItem)
        .verifyComplete();
  }

  @Test
  @DisplayName("Can return an empty result when module locator does not exist")
  void canFindZeroModulesWhenGivenInvalidLocator() {
    integrationTestUtils.createStaticTestModule();
    final String userId = integrationTestUtils.createTestUser();

    StepVerifier.create(
            moduleRepository.findByLocatorWithSolutionStatus(
                userId, "invalid-locator-does-not-exist"))
        .verifyComplete();
  }

  @BeforeEach
  private void setUp() {
    integrationTestUtils.resetState();

    // Bypass the rate limiter
    final Bucket mockBucket = mock(Bucket.class);
    when(mockBucket.tryConsume(1)).thenReturn(true);
    when(flagSubmissionRateLimiter.resolveBucket(any(String.class))).thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(any(String.class))).thenReturn(mockBucket);
  }
}
