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

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.user.TeamEntity;
import org.owasp.herder.user.UserEntity;
import org.springframework.data.mongodb.core.mapping.Document;

@Value
@Builder
@AllArgsConstructor
@With
@Document("submissionRank")
public class RankedSubmission implements Serializable {

  static final long serialVersionUID = -5485881248601955741L;

  UserEntity user;

  @NonNull
  ModuleEntity module;

  TeamEntity team;

  @NonNull
  Long rank;

  @NonNull
  LocalDateTime time;

  String flag;

  @NonNull
  Long baseScore;

  @NonNull
  Long bonusScore;

  @NonNull
  Long score;
}
