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
package org.owasp.herder.flag;

import lombok.RequiredArgsConstructor;
import org.owasp.herder.authentication.ControllerAuthentication;
import org.owasp.herder.exception.RateLimitException;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.user.RefresherService;
import org.owasp.herder.validation.ValidModuleLocator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/")
@Validated
public class FlagController {

  private final ControllerAuthentication controllerAuthentication;

  private final ModuleService moduleService;

  private final SubmissionService submissionService;

  private final RefresherService refresherService;

  @PostMapping(path = "flag/submit/{moduleLocator}")
  @PreAuthorize("hasRole('ROLE_USER')")
  public Mono<ResponseEntity<Submission>> submitFlag(
    @PathVariable("moduleLocator") @ValidModuleLocator final String moduleLocator,
    @RequestBody final String flag
  ) {
    return controllerAuthentication
      .getUserId()
      .zipWith(moduleService.findByLocator(moduleLocator).map(ModuleEntity::getId))
      .flatMap(tuple -> submissionService.submitFlag(tuple.getT1(), tuple.getT2(), flag))
      .flatMap(u ->
        refresherService
          .refreshModuleLists()
          .then(refresherService.refreshSubmissionRanks())
          .then(refresherService.refreshScoreboard())
          .then(Mono.just(u))
      )
      .map(submission -> new ResponseEntity<>(submission, HttpStatus.OK))
      .onErrorResume(
        RateLimitException.class,
        throwable -> Mono.just(new ResponseEntity<>(null, HttpStatus.TOO_MANY_REQUESTS))
      );
  }
}
