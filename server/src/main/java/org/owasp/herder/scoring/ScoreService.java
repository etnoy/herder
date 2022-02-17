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
package org.owasp.herder.scoring;

import org.owasp.herder.exception.InvalidRankException;
import org.owasp.herder.scoring.ModulePoint.ModulePointBuilder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Service
public final class ScoreService {
  private final ModulePointRepository modulePointRepository;

  private final SubmissionRepository submissionRepository;

  public Mono<ModulePoint> setModuleScore(
      final String moduleId, final long rank, final long points) {
    if (rank < 0) {
      return Mono.error(new InvalidRankException("Rank must be zero or a positive integer"));
    }
    if (points == 0) {
      return Mono.empty();
    }
    ModulePointBuilder builder = ModulePoint.builder().moduleId(moduleId).rank(rank).points(points);
    return modulePointRepository.save(builder.build());
  }

  public Flux<ScoreboardEntry> getScoreboard() {
    return submissionRepository.getScoreboard();
  }
}
