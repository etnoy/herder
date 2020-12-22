/* 
 * Copyright 2018-2020 Jonathan Jogenfors, jonathan@jogenfors.se
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
package org.owasp.herder.test.model;

import static org.assertj.core.api.Assertions.assertThat;

import javax.validation.constraints.NotNull;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.scoring.SubmissionDto;
import org.owasp.herder.test.util.TestUtils;

@DisplayName("SubmissionDto unit test")
class SubmissionDtoTest {
  @Test
  void buildComment_ValidComment_Builds() {
    for (final long moduleName : TestUtils.LONGS) {
      for (final String flag : TestUtils.STRINGS) {
        final SubmissionDto submissionDto = new SubmissionDto(moduleName, flag);
        assertThat(submissionDto.getModuleName()).isEqualTo(moduleName);
        assertThat(submissionDto.getFlag()).isEqualTo(flag);
      }
    }
  }

  @Test
  void equals_EqualsVerifier_AsExpected() {
    EqualsVerifier.forClass(SubmissionDto.class).withIgnoredAnnotations(NotNull.class).verify();
  }

  @Test
  void toString_ValidData_AsExpected() {
    final SubmissionDto submissionDto = new SubmissionDto(16L, "flag");
    assertThat(submissionDto).hasToString("SubmissionDto(moduleName=16, flag=flag)");
  }
}
