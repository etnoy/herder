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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.authentication.ControllerAuthentication;
import org.owasp.herder.it.BaseIT;
import org.owasp.herder.it.util.IntegrationTestUtils;
import org.owasp.herder.module.ModuleController;
import org.owasp.herder.module.ModuleListItem;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.ModuleTag;
import org.owasp.herder.module.NameValueTag;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;

import io.github.bucket4j.Bucket;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DirtiesContext
@DisplayName("ModuleController integration tests")
class ModuleControllerIT extends BaseIT {
  @Autowired ModuleService moduleService;

  @Autowired UserService userService;

  @Autowired ModuleController moduleController;

  @Autowired IntegrationTestUtils integrationTestUtils;

  @MockBean ControllerAuthentication controllerAuthentication;

  @MockBean FlagSubmissionRateLimiter flagSubmissionRateLimiter;

  @MockBean InvalidFlagRateLimiter invalidFlagRateLimiter;

  @Autowired SubmissionService submissionService;

  @Test
  @DisplayName("Can list modules")
  @WithMockUser(
      username = "admin",
      roles = {"USER", "ADMIN"})
  void canListModules() {
    final String userId = userService.create("Test user").block();

    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(userId));

    // Create a module to submit to
    moduleService.create("id1").block();

    // Set that module to have an exact flag
    moduleService.setStaticFlag("id1", "flag").block();

    // Create a module to submit to
    moduleService.create("id2").block();

    // Set that module to have an exact flag
    moduleService.setStaticFlag("id2", "flag").block();

    final ModuleTag moduleTag =
        ModuleTag.builder().moduleName("id1").name("usage").value("test").build();

    moduleService.saveTags(List.of(moduleTag)).blockLast();

    submissionService.submit(userId, "id1", "flag").block();

    final NameValueTag[] tags = {NameValueTag.builder().name("usage").value("test").build()};

    StepVerifier.create(moduleController.findAllByUserId())
        .expectNext(ModuleListItem.builder().name("id1").isSolved(true).tags(tags).build())
        .expectNext(ModuleListItem.builder().name("id2").isSolved(false).build())
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

  @Test
  @DisplayName("Can get module information")
  @WithMockUser(
      username = "admin",
      roles = {"USER", "ADMIN"})
  void canGetModuleInformation() {
    final String userId = userService.create("Test user").block();

    when(controllerAuthentication.getUserId()).thenReturn(Mono.just(userId));

    // Create a module to submit to
    moduleService.create("id1").block();

    // Set that module to have an exact flag
    moduleService.setStaticFlag("id1", "flag").block();

    final ModuleTag moduleTag =
        ModuleTag.builder().moduleName("id1").name("usage").value("test").build();

    moduleService.saveTags(List.of(moduleTag)).blockLast();

    submissionService.submit(userId, "id1", "flag").block();

    final NameValueTag[] tags = {NameValueTag.builder().name("usage").value("test").build()};

    StepVerifier.create(moduleController.findByName("id1"))
        .expectNext(ModuleListItem.builder().name("id1").isSolved(true).tags(tags).build())
        .verifyComplete();
  }
}
