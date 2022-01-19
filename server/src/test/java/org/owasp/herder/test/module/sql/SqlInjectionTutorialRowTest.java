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
package org.owasp.herder.test.module.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorialRow;
import org.owasp.herder.module.sqlinjection.SqlInjectionTutorialRow.SqlInjectionTutorialRowBuilder;
import org.owasp.herder.test.util.TestUtils;

@DisplayName("SqlInjectionTutorialRow unit tests")
class SqlInjectionTutorialRowTest {
  @Test
  void build_NullNameCommentAndError_ThrowsNullPointerException() {
    final SqlInjectionTutorialRowBuilder sqlInjectionTutorialRowBuilder =
        SqlInjectionTutorialRow.builder();
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> sqlInjectionTutorialRowBuilder.build())
        .withMessage("%s", "Name, comment, and error can't all be null");
  }

  @Test
  void buildComment_ValidComment_Builds() {
    final SqlInjectionTutorialRowBuilder sqlInjectionTutorialRowBuilder =
        SqlInjectionTutorialRow.builder();
    for (final String comment : TestUtils.STRINGS) {
      final SqlInjectionTutorialRow sqlInjectionTutorialRow =
          sqlInjectionTutorialRowBuilder.comment(comment).build();
      assertThat(sqlInjectionTutorialRow.getComment()).isEqualTo(comment);
    }
  }

  @Test
  void buildError_ValidError_Builds() {
    final SqlInjectionTutorialRowBuilder sqlInjectionTutorialRowBuilder =
        SqlInjectionTutorialRow.builder();
    for (final String error : TestUtils.STRINGS) {
      final SqlInjectionTutorialRow sqlInjectionTutorialRow =
          sqlInjectionTutorialRowBuilder.error(error).build();
      assertThat(sqlInjectionTutorialRow.getError()).isEqualTo(error);
    }
  }

  @Test
  void builderToString_ValidData_AsExpected() {
    final SqlInjectionTutorialRowBuilder testSqlInjectionTutorialRowBuilder =
        SqlInjectionTutorialRow.builder()
            .name("TestSqlInjectionTutorialRow")
            .comment("This is a user")
            .error("no error");
    assertThat(testSqlInjectionTutorialRowBuilder)
        .hasToString(
            "SqlInjectionTutorialRow.SqlInjectionTutorialRowBuilder(name=TestSqlInjectionTutorialRow, comment=This is a user, error=no error)");
  }

  @Test
  void buildName_ValidName_Builds() {
    final SqlInjectionTutorialRowBuilder sqlInjectionTutorialRowBuilder =
        SqlInjectionTutorialRow.builder();
    for (final String name : TestUtils.STRINGS) {
      final SqlInjectionTutorialRow sqlInjectionTutorialRow =
          sqlInjectionTutorialRowBuilder.name(name).build();
      assertThat(sqlInjectionTutorialRow.getName()).isEqualTo(name);
    }
  }

  @Test
  void buildName_ValidName_BuildsSqlInjectionTutorialRow() {
    final SqlInjectionTutorialRow sqlInjectionTutorialRow =
        SqlInjectionTutorialRow.builder().name("TestSqlInjectionTutorialRow").build();
    assertThat(sqlInjectionTutorialRow.getName()).isEqualTo("TestSqlInjectionTutorialRow");
  }

  @Test
  void equals_EqualsVerifier_AsExpected() {
    EqualsVerifier.forClass(SqlInjectionTutorialRow.class).verify();
  }

  @Test
  void toString_ValidData_AsExpected() {
    final SqlInjectionTutorialRow testSqlInjectionTutorialRow =
        SqlInjectionTutorialRow.builder().name("TestSqlInjectionTutorialRow").build();
    assertThat(testSqlInjectionTutorialRow)
        .hasToString(
            "SqlInjectionTutorialRow(name=TestSqlInjectionTutorialRow, comment=null, error=null)");
  }
}
