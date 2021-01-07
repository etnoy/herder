/* 
 * Copyright 2018-2021 Jonathan Jogenfors, jonathan@jogenfors.se
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
package org.owasp.herder.test.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.exception.InvalidRankException;
import org.owasp.herder.module.ModulePointRepository;
import org.owasp.herder.scoring.ModulePoint;
import org.owasp.herder.scoring.ScoreService;
import org.owasp.herder.scoring.ScoreboardEntry;
import org.owasp.herder.scoring.ScoreboardRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreService unit test")
class ScoreServiceTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private ScoreService scoreService;

  @Mock ModulePointRepository modulePointRepository;

  @Mock ScoreboardRepository scoreboardRepository;

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    scoreService = new ScoreService(modulePointRepository, scoreboardRepository);
  }

  @Test
  void setModuleScore_ValidModuleNameAndRank_ReturnsScore() throws Exception {
    final String mockModuleName = "id";
    final int rank = 3;
    final int points = 1000;

    when(modulePointRepository.save(any(ModulePoint.class)))
        .thenAnswer(args -> Mono.just(args.getArgument(0, ModulePoint.class)));

    StepVerifier.create(scoreService.setModuleScore(mockModuleName, rank, points))
        .assertNext(
            modulePoint -> {
              assertThat(modulePoint.getModuleName()).isEqualTo(mockModuleName);
              assertThat(modulePoint.getRank()).isEqualTo(rank);
              assertThat(modulePoint.getPoints()).isEqualTo(points);
            })
        .expectComplete()
        .verify();
  }

  @Test
  void submit_InvalidRank_ReturnsRankException() {
    for (final int rank : new int[] {-1, -123, -999999}) {
      StepVerifier.create(scoreService.setModuleScore("id", rank, 1))
          .expectError(InvalidRankException.class)
          .verify();
    }
  }

  @Test
  void getScoreboard_NoArguments_CallsRepository() throws Exception {
    final ScoreboardEntry mockScoreboardEntry1 = mock(ScoreboardEntry.class);
    final ScoreboardEntry mockScoreboardEntry2 = mock(ScoreboardEntry.class);
    final ScoreboardEntry mockScoreboardEntry3 = mock(ScoreboardEntry.class);

    when(scoreboardRepository.findAll())
        .thenReturn(Flux.just(mockScoreboardEntry1, mockScoreboardEntry2, mockScoreboardEntry3));

    StepVerifier.create(scoreService.getScoreboard())
        .expectNext(mockScoreboardEntry1)
        .expectNext(mockScoreboardEntry2)
        .expectNext(mockScoreboardEntry3)
        .expectComplete()
        .verify();
  }
}
