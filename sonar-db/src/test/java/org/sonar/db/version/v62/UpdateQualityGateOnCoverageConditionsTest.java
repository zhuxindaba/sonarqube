/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.db.version.v62;

import com.google.common.collect.ImmutableList;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.db.DbTester;
import org.sonar.db.version.MigrationStep;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

public class UpdateQualityGateOnCoverageConditionsTest {

  private static final String TABLE_QUALITY_GATES = "quality_gates";
  private static final String TABLE_QUALITY_GATE_CONDITIONS = "quality_gate_conditions";

  @Rule
  public DbTester dbTester = DbTester.createForSchema(System2.INSTANCE, UpdateQualityGateOnCoverageConditionsTest.class, "schema.sql");

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private MigrationStep underTest = new UpdateQualityGateOnCoverageConditions(dbTester.database());

  @Test
  public void migrate_condition_on_overall_coverage_to_coverage() throws SQLException {
    Map<String, Long> metricIdsByMetricKeys = insertMetrics();
    long qualityGateId = insertQualityGate("default");
    insertQualityGateCondition(qualityGateId, metricIdsByMetricKeys.get("overall_coverage"));

    underTest.execute();

    assertThat(dbTester.countRowsOfTable(TABLE_QUALITY_GATE_CONDITIONS)).isEqualTo(1);
    verifyConditions(qualityGateId, new QualityGateCondition("coverage", null, null, null, null));
  }

  private Map<String, Long> insertMetrics() {
    Map<String, Long> metricIdsByMetricKeys = new HashMap<>();
    for (String metricKey : ImmutableList.of("coverage", "new_coverage", "overall_coverage", "new_overall_coverage", "it_coverage", "new_it_coverage")) {
      metricIdsByMetricKeys.put(metricKey, insertMetric(metricKey));
    }
    return metricIdsByMetricKeys;
  }

  private long insertMetric(String key) {
    dbTester.executeInsert("metrics", "NAME", key);
    return (Long) dbTester.selectFirst(dbTester.getSession(), format("select id as \"id\" from metrics where name='%s'", key)).get("id");
  }

  private long insertQualityGate(String qualityGate) {
    dbTester.executeInsert(TABLE_QUALITY_GATES, "NAME", qualityGate);
    return (Long) dbTester.selectFirst(dbTester.getSession(), format("select id as \"id\" from %s where name='%s'", TABLE_QUALITY_GATES, qualityGate)).get("id");
  }

  private long insertQualityGateCondition(long qualityGateId, long metricId) {
    dbTester.executeInsert(TABLE_QUALITY_GATE_CONDITIONS,
      "QGATE_ID", qualityGateId,
      "METRIC_ID", metricId);
    return (Long) dbTester
      .selectFirst(dbTester.getSession(), format("select id as \"id\" from %s where qgate_id='%s' and metric_id='%s'", TABLE_QUALITY_GATE_CONDITIONS, qualityGateId, metricId))
      .get("id");
  }

  private void verifyConditions(long qualityGateId, QualityGateCondition... expectedConditions) {
    List<Map<String, Object>> results = dbTester.select(dbTester.getSession(),
      format("select m.name as \"metricKey\", qgc.period as \"period\", qgc.operator as \"operator\", qgc.value_error as \"error\", qgc.value_warning as \"warning\" from %s qgc " +
        "inner join metrics m on m.id=qgc.metric_id " +
        "where qgc.qgate_id = '%s'", TABLE_QUALITY_GATE_CONDITIONS, qualityGateId));
    List<QualityGateCondition> conditions = results.stream().map(QualityGateCondition::new).collect(Collectors.toList());
    assertThat(conditions).containsOnly(expectedConditions);
  }

  private static class QualityGateCondition {
    String metricKey;
    Long period;
    Long operator;
    String valueError;
    String valueWarning;

    public QualityGateCondition(String metricKey, @Nullable Long period, Long operator, @Nullable String valueError, @Nullable String valueWarning) {
      this.metricKey = metricKey;
      this.period = period;
      this.operator = operator;
      this.valueError = valueError;
      this.valueWarning = valueWarning;
    }

    QualityGateCondition(Map<String, Object> map) {
      this.metricKey = (String) map.get("metricKey");
      this.period = (Long) map.get("period");
      this.operator = (Long) map.get("operator");
      this.valueError = (String) map.get("error");
      this.valueWarning = (String) map.get("warning");
    }

    public String getMetricKey() {
      return metricKey;
    }

    @CheckForNull
    public Long getPeriod() {
      return period;
    }

    public Long getOperator() {
      return operator;
    }

    @CheckForNull
    public String getValueError() {
      return valueError;
    }

    @CheckForNull
    public String getValueWarning() {
      return valueWarning;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      QualityGateCondition that = (QualityGateCondition) o;
      return new EqualsBuilder()
        .append(metricKey, that.getMetricKey())
        .append(period, that.getPeriod())
        .append(operator, that.getOperator())
        .append(valueError, that.getValueError())
        .append(valueWarning, that.getValueWarning())
        .isEquals();
    }

    @Override
    public int hashCode() {
      return new HashCodeBuilder(15, 31)
        .append(metricKey)
        .append(period)
        .append(operator)
        .append(valueError)
        .append(valueWarning)
        .toHashCode();
    }

    @Override
    public String toString() {
      return "QualityGateCondition{" +
        "metricKey='" + metricKey + '\'' +
        ", period=" + period +
        ", operator=" + operator +
        ", valueError='" + valueError + '\'' +
        ", valueWarning='" + valueWarning + '\'' +
        '}';
    }
  }

}
