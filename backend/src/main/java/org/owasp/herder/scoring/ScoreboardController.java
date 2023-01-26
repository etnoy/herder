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
package org.owasp.herder.scoring;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.owasp.herder.exception.ModuleNotFoundException;
import org.owasp.herder.exception.TeamNotFoundException;
import org.owasp.herder.exception.UserNotFoundException;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.user.TeamService;
import org.owasp.herder.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/")
public class ScoreboardController {

  private final ScoreboardService scoreboardService;

  private final UserService userService;

  private final TeamService teamService;

  private final SubmissionService submissionService;

  private final ModuleService moduleService;

  @GetMapping(path = "scoreboard")
  @PreAuthorize("hasRole('ROLE_USER')")
  public Flux<ScoreboardEntry> getScoreboard() {
    return scoreboardService.getScoreboard();
  }

  @GetMapping(path = "scoreboard/user/{userId}")
  @PreAuthorize("hasRole('ROLE_USER')")
  public Flux<SanitizedRankedSubmission> getSubmissionsByUserId(@PathVariable final String userId) {
    try {
      return userService
        .existsById(userId)
        .filter(exists -> exists)
        .switchIfEmpty(Mono.error(new UserNotFoundException("User id " + userId + " not found.")))
        .flatMapMany(u -> submissionService.findAllRankedByUserId(userId));
    } catch (ConstraintViolationException e) {
      return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user id", e));
    }
  }

  @GetMapping(path = "scoreboard/module/{moduleLocator}")
  @PreAuthorize("hasRole('ROLE_USER')")
  public Flux<SanitizedRankedSubmission> getSubmissionsByModuleLocator(@PathVariable final String moduleLocator) {
    try {
      return moduleService
        .existsByLocator(moduleLocator)
        .filter(exists -> exists)
        .switchIfEmpty(Mono.error(new ModuleNotFoundException("No module with locator " + moduleLocator + " found.")))
        .flatMapMany(u -> submissionService.findAllRankedByModuleLocator(moduleLocator));
    } catch (ConstraintViolationException e) {
      return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid module locator", e));
    }
  }

  @GetMapping(path = "scoreboard/team/{teamId}")
  @PreAuthorize("hasRole('ROLE_USER')")
  public Flux<SanitizedRankedSubmission> getSubmissionsByTeamId(@PathVariable final String teamId) {
    try {
      return teamService
        .existsById(teamId)
        .filter(exists -> exists)
        .switchIfEmpty(Mono.error(new TeamNotFoundException("Team id " + teamId + " not found.")))
        .flatMapMany(u -> submissionService.findAllRankedByTeamId(teamId));
    } catch (ConstraintViolationException e) {
      return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid team id", e));
    }
  }
}
