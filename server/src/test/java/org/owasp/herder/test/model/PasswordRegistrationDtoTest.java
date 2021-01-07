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
package org.owasp.herder.test.model;

import static org.assertj.core.api.Assertions.assertThat;

import javax.validation.constraints.NotNull;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.authentication.PasswordRegistrationDto;
import org.owasp.herder.test.util.TestUtils;

@DisplayName("PasswordRegistrationDto unit test")
class PasswordRegistrationDtoTest {
  @Test
  void buildComment_ValidComment_Builds() {
    for (final String displayName : TestUtils.STRINGS) {
      for (final String userName : TestUtils.STRINGS) {
        for (final String password : TestUtils.STRINGS) {
          final PasswordRegistrationDto passwordRegistrationDto =
              new PasswordRegistrationDto(displayName, userName, password);
          assertThat(passwordRegistrationDto.getDisplayName()).isEqualTo(displayName);
          assertThat(passwordRegistrationDto.getUserName()).isEqualTo(userName);
          assertThat(passwordRegistrationDto.getPassword()).isEqualTo(password);
        }
      }
    }
  }

  @Test
  void equals_EqualsVerifier_AsExpected() {
    EqualsVerifier.forClass(PasswordRegistrationDto.class)
        .withIgnoredAnnotations(NotNull.class)
        .verify();
  }

  @Test
  void toString_ValidData_AsExpected() {
    final PasswordRegistrationDto passwordRegistrationDto =
        new PasswordRegistrationDto("displayName", "loginName", "password");
    assertThat(passwordRegistrationDto)
        .hasToString(
            "PasswordRegistrationDto(displayName=displayName, userName=loginName, password=password)");
  }
}
