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
package org.owasp.herder.user;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;
import org.owasp.herder.scoring.PrincipalType;
import org.owasp.herder.validation.ValidDisplayName;
import org.owasp.herder.validation.ValidPrincipalId;

@Value
@AllArgsConstructor
@Builder
@With
public final class PrincipalEntity implements Serializable {

  private static final long serialVersionUID = 8843939402741609352L;

  @ValidPrincipalId
  String id;

  @NonNull
  @ValidDisplayName
  String displayName;

  @NonNull
  PrincipalType principalType;

  LocalDateTime creationTime;

  @Builder.Default
  HashSet<UserEntity> members = new HashSet<>();
}
