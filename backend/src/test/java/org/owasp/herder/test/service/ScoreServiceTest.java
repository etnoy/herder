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
package org.owasp.herder.test.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.scoring.ScoreboardEntry;
import org.owasp.herder.scoring.ScoreboardRepository;
import org.owasp.herder.scoring.ScoreboardService;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.user.UserService;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("scoreboardService unit tests")
class ScoreServiceTest extends BaseTest {

    ScoreboardService scoreboardService;

    @Mock SubmissionService submissionService;

    @Mock ScoreboardRepository scoreboardRepository;

    @Mock UserService userService;

    @BeforeEach
    void setup() {
        // Set up the system under test
        scoreboardService = new ScoreboardService(scoreboardRepository);
    }

    @Test
    @DisplayName("Can rank users in the scoreboard")
    void getScoreboard_ThreeUsersToScore_CallsRepository() {
        final ScoreboardEntry mockScoreboardEntry1 = mock(ScoreboardEntry.class);
        final ScoreboardEntry mockScoreboardEntry2 = mock(ScoreboardEntry.class);
        final ScoreboardEntry mockScoreboardEntry3 = mock(ScoreboardEntry.class);

        when(scoreboardRepository.findAll())
                .thenReturn(
                        Flux.just(
                                mockScoreboardEntry1, mockScoreboardEntry2, mockScoreboardEntry3));

        StepVerifier.create(scoreboardService.getScoreboard())
                .expectNext(mockScoreboardEntry1)
                .expectNext(mockScoreboardEntry2)
                .expectNext(mockScoreboardEntry3)
                .verifyComplete();
    }
}
