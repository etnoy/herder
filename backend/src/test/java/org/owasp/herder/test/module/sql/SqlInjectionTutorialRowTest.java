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
package org.owasp.herder.test.module.sql;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorialRow;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorialRow.SqlInjectionTutorialRowBuilder;

@DisplayName("SqlInjectionTutorialRow unit tests")
class SqlInjectionTutorialRowTest {

  final SqlInjectionTutorialRowBuilder sqlInjectionTutorialRowBuilder = SqlInjectionTutorialRow.builder();

  private static final String TEST_NAME = "name";

  private static final String TEST_COMMENT = "comment";

  private static final String TEST_ERROR = "error";

  SqlInjectionTutorialRowBuilder addName(SqlInjectionTutorialRowBuilder builder) {
    return builder.name(TEST_NAME);
  }

  SqlInjectionTutorialRowBuilder addComment(SqlInjectionTutorialRowBuilder builder) {
    return builder.comment(TEST_COMMENT);
  }

  SqlInjectionTutorialRowBuilder addError(SqlInjectionTutorialRowBuilder builder) {
    return builder.error(TEST_ERROR);
  }

  @Test
  @DisplayName("Can construct with name field set")
  void constructor_HasName_Success() {
    assertThatCode(() -> sqlInjectionTutorialRowBuilder.name(TEST_NAME).build()).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Can construct with name and comment fields set")
  void constructor_HasNameAndComment_Success() {
    assertThatCode(() -> sqlInjectionTutorialRowBuilder.name(TEST_NAME).comment(TEST_COMMENT).build())
      .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Can construct with name, comment, and error fields set")
  void constructor_HasNameAndCommentAndError_Success() {
    assertThatCode(() -> sqlInjectionTutorialRowBuilder.name(TEST_NAME).comment(TEST_COMMENT).error(TEST_ERROR).build())
      .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Can construct with comment field set")
  void constructor_HasComment_Success() {
    assertThatCode(() -> sqlInjectionTutorialRowBuilder.comment(TEST_COMMENT).build()).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Can construct with comment and error fields set")
  void constructor_HasCommentAndError_Success() {
    assertThatCode(() -> sqlInjectionTutorialRowBuilder.comment(TEST_COMMENT).error(TEST_ERROR).build())
      .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Can construct with error field set")
  void constructor_HasError_Success() {
    assertThatCode(() -> sqlInjectionTutorialRowBuilder.error(TEST_ERROR).build()).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Can error when building without any data")
  void constructor_AllNull_Errors() {
    assertThatThrownBy(() -> sqlInjectionTutorialRowBuilder.build())
      .isInstanceOf(NullPointerException.class)
      .hasMessage("Name, comment, and error can't all be null");
  }
}
