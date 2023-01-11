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
package org.owasp.herder.it.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.github.bucket4j.Bucket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.owasp.herder.exception.DuplicateModuleLocatorException;
import org.owasp.herder.exception.DuplicateModuleNameException;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleList;
import org.owasp.herder.module.ModuleListItem;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.RefresherService;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import reactor.test.StepVerifier;

@DisplayName("ModuleService integration tests")
class ModuleServiceIT extends BaseIT {

  @Nested
  @DisplayName("Can get module list item")
  class canGetModuleListItem {

    String userId;
    String moduleId;
    ModuleListItem moduleListItem;

    @Test
    @DisplayName("for single module with no solutions or tags")
    void canGetModule() {
      StepVerifier
        .create(moduleService.findListItemByLocator(userId, TestConstants.TEST_MODULE_LOCATOR))
        .expectNext(moduleListItem)
        .verifyComplete();
    }

    @Test
    @DisplayName("for single module with invalid submissions")
    void canGetModuleWithInvalidSubmissions() {
      // Set that module to have an exact flag
      submissionService.submitFlag(userId, moduleId, "invalidflag").block();
      submissionService.submitFlag(userId, moduleId, "invalidflag2").block();

      StepVerifier
        .create(moduleService.findListItemByLocator(userId, TestConstants.TEST_MODULE_LOCATOR))
        .expectNext(moduleListItem)
        .verifyComplete();
    }

    @Test
    @DisplayName("for single module with tags")
    void canGetModuleWithTags() {
      Multimap<String, String> tags = ArrayListMultimap.create();

      tags.put("key", "value");
      tags.put("usage", "test");
      tags.put("cow", "moo");

      moduleService.setTags(moduleId, tags).block();

      refresherService.refreshModuleLists().block();

      StepVerifier
        .create(moduleService.findListItemByLocator(userId, TestConstants.TEST_MODULE_LOCATOR))
        .expectNext(moduleListItem.withTags(tags))
        .verifyComplete();
    }

    @Test
    @DisplayName("for single solved module")
    void canGetSolvedModule() {
      integrationTestUtils.submitValidFlag(userId, moduleId);

      refresherService.refreshModuleLists().block();

      StepVerifier
        .create(moduleService.findListItemByLocator(userId, TestConstants.TEST_MODULE_LOCATOR))
        .expectNext(moduleListItem.withIsSolved(true))
        .verifyComplete();
    }

    @BeforeEach
    void setup() {
      userId = integrationTestUtils.createTestUser();

      // Create a module to submit to
      moduleId = integrationTestUtils.createStaticTestModule();

      refresherService.refreshModuleLists().block();

      moduleListItem =
        ModuleListItem
          .builder()
          .id(moduleId)
          .name(TestConstants.TEST_MODULE_NAME)
          .locator(TestConstants.TEST_MODULE_LOCATOR)
          .build();
    }
  }

  @Nested
  @DisplayName("Can get module list")
  class canGetModuleList {

    String userId;
    String moduleId;
    ModuleList moduleList;

    @Test
    @DisplayName("for user with closed module")
    void canHideClosedModuleInList() {
      moduleService.close(moduleId).block();
      refresherService.refreshModuleLists().block();

      ArrayList<ModuleListItem> modules = moduleList.getModules();
      modules.clear();

      StepVerifier
        .create(moduleService.findModuleListByUserId(userId))
        .expectNext(moduleList.withModules(modules))
        .verifyComplete();
    }

    @Test
    @DisplayName("for user with closed and solved module")
    void canHideClosedSolvedModuleInList() {
      integrationTestUtils.submitValidFlag(userId, moduleId);

      moduleService.close(moduleId).block();

      refresherService.refreshModuleLists().block();

      ArrayList<ModuleListItem> modules = moduleList.getModules();
      modules.clear();

      StepVerifier
        .create(moduleService.findModuleListByUserId(userId))
        .expectNext(moduleList.withModules(modules))
        .verifyComplete();
    }

    @Test
    @DisplayName("for user with no module solutions or tags")
    void canListModule() {
      refresherService.refreshModuleLists().block();
      StepVerifier.create(moduleService.findModuleListByUserId(userId)).expectNext(moduleList).verifyComplete();
    }

    @Test
    @DisplayName("for user with invalid submissions")
    void canListModuleWithInvalidSubmissions() {
      submissionService.submitFlag(userId, moduleId, "invalidflag").block();
      submissionService.submitFlag(userId, moduleId, "invalidflag2").block();

      refresherService.refreshModuleLists().block();

      StepVerifier.create(moduleService.findModuleListByUserId(userId)).expectNext(moduleList).verifyComplete();
    }

    @Test
    @DisplayName("for user with module tags")
    void canListModuleWithTags() {
      Multimap<String, String> tags = ArrayListMultimap.create();

      tags.put("key", "value");
      tags.put("usage", "test");
      tags.put("cow", "moo");

      moduleService.setTags(moduleId, tags).block();

      refresherService.refreshModuleLists().block();

      StepVerifier
        .create(moduleService.findModuleListByUserId(userId))
        .assertNext(moduleList -> assertThat(moduleList.getModules().get(0).getTags()).isEqualTo(tags))
        .verifyComplete();
    }

    @Test
    @DisplayName("for user with multiple modules")
    void canListMultipleModules() {
      final String moduleId2 = moduleService.create("Test 2", "test-2").block();
      final String moduleId3 = moduleService.create("Test 3", "test-3").block();

      integrationTestUtils.submitValidFlag(userId, moduleId);

      refresherService.refreshModuleLists().block();

      final ModuleListItem moduleListItem1 = ModuleListItem
        .builder()
        .id(moduleId)
        .name(TestConstants.TEST_MODULE_NAME)
        .locator(TestConstants.TEST_MODULE_LOCATOR)
        .isSolved(true)
        .build();

      final ModuleListItem moduleListItem2 = ModuleListItem
        .builder()
        .id(moduleId2)
        .name("Test 2")
        .locator("test-2")
        .build();
      final ModuleListItem moduleListItem3 = ModuleListItem
        .builder()
        .id(moduleId3)
        .name("Test 3")
        .locator("test-3")
        .build();

      StepVerifier
        .create(moduleService.findModuleListByUserId(userId))
        .assertNext(moduleList ->
          assertThat(moduleList.getModules())
            .containsExactlyInAnyOrder(moduleListItem1, moduleListItem2, moduleListItem3)
        )
        .verifyComplete();
    }

    @Test
    @DisplayName("for user with solved module")
    void canListSolvedModule() {
      integrationTestUtils.submitValidFlag(userId, moduleId);

      refresherService.refreshModuleLists().block();

      StepVerifier
        .create(moduleService.findModuleListByUserId(userId))
        .assertNext(moduleList -> assertThat(moduleList.getModules().get(0).getIsSolved()).isTrue())
        .verifyComplete();
    }

    @Test
    @DisplayName("for user with module solved by another team member")
    void canListSolvedModuleForAnotherUserInTeam() {
      final String teamId = integrationTestUtils.createTestTeam();

      final String userId2 = userService.create("Test 2").block();

      userService.addUserToTeam(userId, teamId).block();
      userService.addUserToTeam(userId2, teamId).block();

      integrationTestUtils.submitValidFlag(userId2, moduleId);

      refresherService.refreshModuleLists().block();

      StepVerifier
        .create(moduleService.findModuleListByUserId(userId2))
        .assertNext(moduleList -> assertThat(moduleList.getModules().get(0).getIsSolved()).isTrue())
        .verifyComplete();
    }

    @Test
    @DisplayName("for team with solved module")
    void canListSolvedModuleForTeam() {
      final String teamId = integrationTestUtils.createTestTeam();

      userService.addUserToTeam(userId, teamId).block();

      integrationTestUtils.submitValidFlag(userId, moduleId);

      refresherService.refreshModuleLists().block();

      StepVerifier
        .create(moduleService.findModuleListByTeamId(teamId))
        .assertNext(moduleList -> assertThat(moduleList.getModules().get(0).getIsSolved()).isTrue())
        .verifyComplete();
    }

    @BeforeEach
    void setup() {
      userId = integrationTestUtils.createTestUser();

      // Create a module to submit to
      moduleId = integrationTestUtils.createStaticTestModule();

      final ModuleListItem moduleListItem = ModuleListItem
        .builder()
        .id(moduleId)
        .name(TestConstants.TEST_MODULE_NAME)
        .locator(TestConstants.TEST_MODULE_LOCATOR)
        .build();

      moduleList =
        ModuleList.builder().id(userId).modules(new ArrayList<ModuleListItem>(List.of(moduleListItem))).build();
    }
  }

  @Autowired
  IntegrationTestUtils integrationTestUtils;

  @Autowired
  ModuleService moduleService;

  @Autowired
  ModuleRepository moduleRepository;

  @Autowired
  UserService userService;

  @Autowired
  RefresherService refresherService;

  @MockBean
  FlagSubmissionRateLimiter flagSubmissionRateLimiter;

  @MockBean
  InvalidFlagRateLimiter invalidFlagRateLimiter;

  @Autowired
  SubmissionService submissionService;

  @Test
  @DisplayName("Can throw DuplicateModuleLocatorException when module locator isn't unique")
  void canReturnDuplicateModuleLocatorException() {
    moduleService.create("First module", TestConstants.TEST_MODULE_LOCATOR).block();
    StepVerifier
      .create(moduleService.create("Second module", TestConstants.TEST_MODULE_LOCATOR))
      .expectError(DuplicateModuleLocatorException.class)
      .verify();
  }

  @ParameterizedTest
  @MethodSource("org.owasp.herder.test.util.TestConstants#validModuleNameProvider")
  @DisplayName("Can throw DuplicateModuleNameException when module name isn't unique")
  void canReturnDuplicateModuleNameException(final String moduleName) {
    moduleService.create(moduleName, "first-module").block();
    StepVerifier
      .create(moduleService.create(moduleName, "second-module"))
      .expectError(DuplicateModuleNameException.class)
      .verify();
  }

  @ParameterizedTest
  @MethodSource("org.owasp.herder.test.util.TestConstants#validModuleNameProvider")
  @DisplayName("Can create a module with valid name")
  void create_FreshModule_Success(final String moduleName) {
    final String moduleId = moduleService.create(moduleName, TestConstants.TEST_MODULE_LOCATOR).block();
    StepVerifier
      .create(moduleRepository.findById(moduleId))
      .expectNextMatches(module -> module.getName().equals(moduleName))
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
