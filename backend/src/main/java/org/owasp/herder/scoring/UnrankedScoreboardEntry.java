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

import java.io.Serializable;
import java.util.ArrayList;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.UserEntity;

@Value
@Builder
@With
public class UnrankedScoreboardEntry implements Serializable {
  private static final long serialVersionUID = 902640084501001329L;

  @NonNull UserEntity user;

  TeamEntity team;

  @NonNull String displayName;

  @NonNull Long score;

  @NonNull Long scoreAdjustment;

  @NonNull Long bonusScore;

  @NonNull Long baseScore;

  @NonNull Long goldMedals;

  @NonNull Long silverMedals;

  @NonNull Long bronzeMedals;

  @Builder.Default
  ArrayList<ScoreAdjustment> adjustments = new ArrayList<>();
}
