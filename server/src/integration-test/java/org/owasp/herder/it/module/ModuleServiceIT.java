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

import java.util.HashSet;

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
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@DisplayName("ModuleService integration tests")
class ModuleServiceIT extends BaseIT {
  @Nested
  @DisplayName("Can list modules")
  class canListModules {
    String userId;
    String moduleId;
    ModuleListItem moduleListItem;

    @Test
    @DisplayName("with no solutions or tags")
    void canListModule() {
      StepVerifier.create(moduleService.findAllOpenWithSolutionStatus(userId))
          .expectNext(moduleListItem)
          .verifyComplete();
    }

    @Test
    @DisplayName("with invalid submissions")
    void canListModuleWithInvalidSubmissions() {
      // Set that module to have an exact flag
      submissionService.submit(userId, moduleId, "invalidflag").block();
      submissionService.submit(userId, moduleId, "invalidflag2").block();

      StepVerifier.create(moduleService.findAllOpenWithSolutionStatus(userId))
          .expectNext(moduleListItem)
          .verifyComplete();
    }

    @Test
    @DisplayName("with tags")
    void canListModuleWithTags() {
      final ModuleTag[] moduleTags = {
        ModuleTag.builder().moduleId(moduleId).name("key").value("value").build(),
        ModuleTag.builder().moduleId(moduleId).name("usage").value("test").build(),
        ModuleTag.builder().moduleId(moduleId).name("cow").value("moo").build()
      };

      moduleService.saveTags(Flux.fromArray(moduleTags)).blockLast();

      final HashSet<NameValueTag> tags = new HashSet<>();

      tags.add(NameValueTag.builder().name("key").value("value").build());
      tags.add(NameValueTag.builder().name("usage").value("test").build());
      tags.add(NameValueTag.builder().name("cow").value("moo").build());

      StepVerifier.create(moduleService.findAllOpenWithSolutionStatus(userId))
          .expectNext(moduleListItem.withTags(tags))
          .verifyComplete();
    }

    @Test
    @DisplayName("with solution")
    void canListSolvedModule() {
      submissionService.submit(userId, moduleId, "flag").block();

      StepVerifier.create(moduleService.findAllOpenWithSolutionStatus(userId))
          .expectNext(moduleListItem.withIsSolved(true))
          .verifyComplete();
    }

    @BeforeEach
    private void setUp() {
      userId = userService.create(TestConstants.TEST_USER_DISPLAY_NAME).block();

      // Create a module to submit to
      moduleId =
          moduleService
              .create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR)
              .block();

      moduleService.setStaticFlag(moduleId, "flag").block();
      moduleListItem =
          ModuleListItem.builder()
              .id(moduleId)
              .name(TestConstants.TEST_MODULE_NAME)
              .locator(TestConstants.TEST_MODULE_LOCATOR)
              .build();
    }
  }

  @Nested
  @DisplayName("Can get module")
  class canGetModules {
    String userId;
    String moduleId;
    ModuleListItem moduleListItem;

    @Test
    @DisplayName("with no solutions or tags")
    void canGetModule() {
      StepVerifier.create(
              moduleService.findByLocatorWithSolutionStatus(
                  userId, TestConstants.TEST_MODULE_LOCATOR))
          .expectNext(moduleListItem)
          .verifyComplete();
    }

    @Test
    @DisplayName("with invalid submissions")
    void canGetModuleWithInvalidSubmissions() {
      // Set that module to have an exact flag
      submissionService.submit(userId, moduleId, "invalidflag").block();
      submissionService.submit(userId, moduleId, "invalidflag2").block();

      StepVerifier.create(
              moduleService.findByLocatorWithSolutionStatus(
                  userId, TestConstants.TEST_MODULE_LOCATOR))
          .expectNext(moduleListItem)
          .verifyComplete();
    }

    @Test
    @DisplayName("with tags")
    void canGetModuleWithTags() {
      final ModuleTag[] moduleTags = {
        ModuleTag.builder().moduleId(moduleId).name("key").value("value").build(),
        ModuleTag.builder().moduleId(moduleId).name("usage").value("test").build(),
        ModuleTag.builder().moduleId(moduleId).name("cow").value("moo").build()
      };

      moduleService.saveTags(Flux.fromArray(moduleTags)).blockLast();

      HashSet<NameValueTag> tags = new HashSet<>();

      tags.add(NameValueTag.builder().name("key").value("value").build());
      tags.add(NameValueTag.builder().name("usage").value("test").build());
      tags.add(NameValueTag.builder().name("cow").value("moo").build());

      StepVerifier.create(
              moduleService.findByLocatorWithSolutionStatus(
                  userId, TestConstants.TEST_MODULE_LOCATOR))
          .expectNext(moduleListItem.withTags(tags))
          .verifyComplete();
    }

    @Test
    @DisplayName("with solution")
    void canGetSolvedModule() {
      submissionService.submit(userId, moduleId, "flag").block();

      StepVerifier.create(
              moduleService.findByLocatorWithSolutionStatus(
                  userId, TestConstants.TEST_MODULE_LOCATOR))
          .expectNext(moduleListItem.withIsSolved(true))
          .verifyComplete();
    }

    @BeforeEach
    private void setUp() {
      userId = userService.create(TestConstants.TEST_USER_DISPLAY_NAME).block();

      // Create a module to submit to
      moduleId =
          moduleService
              .create(TestConstants.TEST_MODULE_NAME, TestConstants.TEST_MODULE_LOCATOR)
              .block();

      moduleService.setStaticFlag(moduleId, "flag").block();
      moduleListItem =
          ModuleListItem.builder()
              .id(moduleId)
              .name(TestConstants.TEST_MODULE_NAME)
              .locator(TestConstants.TEST_MODULE_LOCATOR)
              .build();
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

  @Test
  @DisplayName("Can throw DuplicateModuleLocatorException when module locator isn't unique")
  void canReturnDuplicateModuleLocatorException() {
    moduleService.create("First module", TestConstants.TEST_MODULE_LOCATOR).block();
    StepVerifier.create(moduleService.create("Second module", TestConstants.TEST_MODULE_LOCATOR))
        .expectError(DuplicateModuleLocatorException.class)
        .verify();
  }

  @ParameterizedTest
  @MethodSource("org.owasp.herder.test.util.TestConstants#validModuleNameProvider")
  @DisplayName("Can throw DuplicateModuleNameException when module name isn't unique")
  void canReturnDuplicateModuleNameException(final String moduleName) {
    moduleService.create(moduleName, "first-module").block();
    StepVerifier.create(moduleService.create(moduleName, "second-module"))
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
