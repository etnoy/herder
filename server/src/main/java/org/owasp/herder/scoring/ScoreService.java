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

import javax.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.owasp.herder.scoring.ModulePoint.ModulePointBuilder;
import org.owasp.herder.validation.ValidModuleId;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@Validated
@Service
public class ScoreService {
  private final ModulePointRepository modulePointRepository;

  private final SubmissionRepository submissionRepository;

  public Mono<ModulePoint> setModuleScore(
      @ValidModuleId final String moduleId, @Min(0) final long rank, @Min(0) final long points) {
    ModulePointBuilder builder = ModulePoint.builder().moduleId(moduleId).rank(rank).points(points);
    return modulePointRepository.save(builder.build());
  }

  public Flux<ModulePoint> getModuleScores(@ValidModuleId final String moduleId) {
    return modulePointRepository.findAllByModuleId(moduleId);
  }

  public Flux<ScoreboardEntry> getScoreboard() {
    return submissionRepository.getScoreboard();
  }
}
