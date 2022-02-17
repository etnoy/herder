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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
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
import org.owasp.herder.module.ModuleListItem;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.ModuleTag;
import org.owasp.herder.module.NameValueTag;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import io.github.bucket4j.Bucket;
import reactor.test.StepVerifier;

@DisplayName("ModuleService integration tests")
class ModuleServiceIT extends BaseIT {
  @Nested
  @DisplayName("Can list modules")
  class canListModules {
    String userId;
    ModuleListItem item = ModuleListItem.builder().name("id1").build();

    @Test
    @DisplayName("with no solutions or tags")
    void canListModule() {
      StepVerifier.create(moduleService.findAllOpenWithSolutionStatus(userId))
          .expectNext(item)
          .verifyComplete();
    }

    @Test
    @DisplayName("with invalid submissions")
    void canListModuleWithInvalidSubmissions() {
      // Set that module to have an exact flag
      submissionService.submit(userId, "id1", "invalidflag").block();
      submissionService.submit(userId, "id1", "invalidflag2").block();

      StepVerifier.create(moduleService.findAllOpenWithSolutionStatus(userId))
          .expectNext(item)
          .verifyComplete();
    }

    @Test
    @DisplayName("with tags")
    void canListModuleWithTags() {
      final ModuleTag[] moduleTags = {
        ModuleTag.builder().moduleId("id1").name("key").value("value").build(),
        ModuleTag.builder().moduleId("id1").name("usage").value("test").build(),
        ModuleTag.builder().moduleId("id1").name("cow").value("moo").build()
      };

      moduleService.saveTags(Arrays.asList(moduleTags)).blockLast();

      final NameValueTag[] tags = {
        NameValueTag.builder().name("key").value("value").build(),
        NameValueTag.builder().name("usage").value("test").build(),
        NameValueTag.builder().name("cow").value("moo").build()
      };

      StepVerifier.create(moduleService.findAllOpenWithSolutionStatus(userId))
          .expectNext(item.withTags(tags))
          .verifyComplete();
    }

    @Test
    @DisplayName("with solution")
    void canListSolvedModule() {
      submissionService.submit(userId, "id1", "flag").block();

      StepVerifier.create(moduleService.findAllOpenWithSolutionStatus(userId))
          .expectNext(item.withIsSolved(true))
          .verifyComplete();
    }

    @BeforeEach
    private void setUp() {
      userId = userService.create("Test user").block();
      // Create a module to submit to
      moduleService.create("Test Module 1", "id1").block();
      moduleService.setStaticFlag("id1", "flag").block();
    }
  }

  @Autowired IntegrationTestUtils integrationTestUtils;

  @Autowired ModuleService moduleService;

  @Autowired ModuleRepository moduleRepository;

  @Autowired UserService userService;

  @MockBean FlagSubmissionRateLimiter flagSubmissionRateLimiter;

  @MockBean InvalidFlagRateLimiter invalidFlagRateLimiter;

  @Autowired SubmissionService submissionService;

  @Test
  @DisplayName("Can get module information")
  void canGetModuleInformation() {
    final String userId = userService.create("Test user").block();

    // Create a module to submit to
    final String moduleId =
        moduleService
            .create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR)
            .block();

    // Set that module to have an exact flag
    moduleService.setStaticFlag(moduleId, "flag").block();

    final ModuleTag moduleTag =
        ModuleTag.builder().moduleId(moduleId).name("usage").value("test").build();

    moduleService.saveTags(List.of(moduleTag)).blockLast();

    submissionService.submit(userId, "id1", "flag").block();

    final NameValueTag[] tags = {NameValueTag.builder().name("usage").value("test").build()};

    StepVerifier.create(moduleService.findByIdWithSolutionStatus(userId, "id1"))
        .expectNext(
            ModuleListItem.builder()
                .id(moduleId)
                .name(TestConstants.TEST_MODULE_NAME)
                .locator(TestConstants.TEST_MODULE_LOCATOR)
                .isSolved(true)
                .tags(tags)
                .build())
        .verifyComplete();
  }

  @Test
  @DisplayName("Can show empty module list")
  void canReturnEmptyModuleList() {
    final String userId = userService.create("Test user").block();

    StepVerifier.create(moduleService.findAllOpenWithSolutionStatus(userId)).verifyComplete();
  }

  @ParameterizedTest
  @MethodSource("org.owasp.herder.test.util.TestConstants#validModuleNameProvider")
  @DisplayName("Can create a module with valid name")
  void create_FreshModule_Success(final String moduleName) {
    final String moduleId =
        moduleService.create(moduleName, TestConstants.TEST_MODULE_LOCATOR).block();
    StepVerifier.create(moduleRepository.findById(moduleId))
        .expectNextMatches(module -> module.getName().equals(moduleName))
        .verifyComplete();
  }

  @ParameterizedTest
  @MethodSource("org.owasp.herder.test.util.TestConstants#validModuleNameProvider")
  @DisplayName("Can throw DuplicateModuleLocatorException when module locator isn't unique")
  void canReturnDuplicateModuleLocatorException(final String moduleLocator) {
    moduleService.create(TestConstants.TEST_MODULE_NAME, moduleLocator).block();
    StepVerifier.create(moduleService.create(TestConstants.TEST_MODULE_NAME, moduleLocator))
        .expectError(DuplicateModuleLocatorException.class)
        .verify();
  }

  @ParameterizedTest
  @MethodSource("org.owasp.herder.test.util.TestConstants#validModuleNameProvider")
  @DisplayName("Can throw DuplicateModuleNameException when module name isn't unique")
  void canReturnDuplicateModuleNameException(final String moduleName) {
    moduleService.create(moduleName, TestConstants.TEST_MODULE_LOCATOR).block();
    StepVerifier.create(moduleService.create(moduleName, TestConstants.TEST_MODULE_LOCATOR))
        .expectError(DuplicateModuleNameException.class)
        .verify();
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
