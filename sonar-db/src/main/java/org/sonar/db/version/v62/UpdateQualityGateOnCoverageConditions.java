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

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.CheckForNull;
import org.sonar.db.Database;
import org.sonar.db.version.BaseDataChange;
import org.sonar.db.version.Select;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static org.sonar.core.util.stream.Collectors.uniqueIndex;

public class UpdateQualityGateOnCoverageConditions extends BaseDataChange {

  private static final String COVERAGE_METRIC_KEY = "coverage";
  private static final String OVERALL_COVERAGE_METRIC_KEY = "overall_coverage";
  private static final String IT_COVERAGE_METRIC_KEY = "it_coverage";
  private static final String NEW_PREFIX = "new_";

  public UpdateQualityGateOnCoverageConditions(Database db) {
    super(db);
  }

  @Override
  public void execute(Context context) throws SQLException {
    List<Metric> metrics = selectMetrics(context);
    List<Long> qualityGateIds = context.prepareSelect("select id from quality_gates").list(Select.LONG_READER);
    new Migration(context, metrics, qualityGateIds).execute();
  }

  private static class Migration {
    private final Context context;
    private final Map<String, Metric> metricsByMetricKeys;
    private final List<Long> qualityGateIds;

    Migration(Context context, List<Metric> metrics, List<Long> qualityGateIds) {
      this.context = context;
      this.metricsByMetricKeys = metrics.stream().collect(uniqueIndex(Metric::getKey, Function.identity()));
      this.qualityGateIds = qualityGateIds;
    }

    public void execute() {
      qualityGateIds.forEach(this::processQualityGate);
    }

    private void processQualityGate(long qualityGateId) {
      try {
        List<QualityGateCondition> qualityGateConditions = selectQualityGateConditions(qualityGateId);
        Map<Long, QualityGateCondition> qualityGateConditionsByMetricId = qualityGateConditions.stream()
          .collect(uniqueIndex(QualityGateCondition::getMetricId, Function.identity()));

        // TODO handle new_XXX metrics
        QualityGateCondition conditionOnCoverage = getConditionByMetricKey(COVERAGE_METRIC_KEY, qualityGateConditionsByMetricId);
        QualityGateCondition conditionOnOverallCoverage = getConditionByMetricKey(OVERALL_COVERAGE_METRIC_KEY, qualityGateConditionsByMetricId);
        if (conditionOnOverallCoverage != null) {
          if (conditionOnCoverage != null) {
            removeQualityGateCondition(conditionOnCoverage.getId());
          }
          updateQualityGateCondition(conditionOnOverallCoverage.getId(), COVERAGE_METRIC_KEY);
        }
      } catch (SQLException e) {
        throw new IllegalStateException(String.format("Fail to update quality gate condition of quality gate %s", qualityGateId), e);
      }
    }

    private QualityGateCondition getConditionByMetricKey(String metricKey, Map<Long, QualityGateCondition> qualityGateConditionsByMetricId) {
      return qualityGateConditionsByMetricId.get(metricsByMetricKeys.get(metricKey).getId());
    }

    private List<QualityGateCondition> selectQualityGateConditions(long qualityGateId) {
      try {
        return context.prepareSelect("select qgc.id, qgc.metric_id, qgc.period, qgc.operator, qgc.value_error, qgc.value_warning " +
          "from quality_gate_conditions qgc " +
          "inner join metrics m on m.id=qgc.metric_id and m.name in (?, ?, ?, ?, ?, ?) " +
          "where qgc.qgate_id=? ")
          .setString(1, COVERAGE_METRIC_KEY)
          .setString(2, NEW_PREFIX + COVERAGE_METRIC_KEY)
          .setString(3, OVERALL_COVERAGE_METRIC_KEY)
          .setString(4, NEW_PREFIX + OVERALL_COVERAGE_METRIC_KEY)
          .setString(5, IT_COVERAGE_METRIC_KEY)
          .setString(6, NEW_PREFIX + IT_COVERAGE_METRIC_KEY)
          .setLong(7, qualityGateId)
          .list(QualityGateCondition::new);
      } catch (SQLException e) {
        throw new IllegalStateException(String.format("Fail to select quality gate conditions of quality gate %s", qualityGateId), e);
      }
    }

    private void updateQualityGateCondition(long id, String metricKey) throws SQLException {
      context.prepareUpsert("update quality_gate_conditions set metric_id=? where id=?")
        .setLong(1, metricsByMetricKeys.get(metricKey).getId())
        .setLong(2, id)
        .execute()
        .commit();
    }

    private void removeQualityGateCondition(long id) throws SQLException {
      context.prepareUpsert("delete from quality_gate_conditions where id=?").setLong(1, id)
        .execute()
        .commit();
    }
  }

  private static List<Metric> selectMetrics(Context context) throws SQLException {
    List<Metric> metrics = context.prepareSelect("select id, name from metrics where name in (?, ?, ?, ?, ?, ?)")
      .setString(1, COVERAGE_METRIC_KEY)
      .setString(2, NEW_PREFIX + COVERAGE_METRIC_KEY)
      .setString(3, OVERALL_COVERAGE_METRIC_KEY)
      .setString(4, NEW_PREFIX + OVERALL_COVERAGE_METRIC_KEY)
      .setString(5, IT_COVERAGE_METRIC_KEY)
      .setString(6, NEW_PREFIX + IT_COVERAGE_METRIC_KEY)
      .list(Metric::new);
    checkArgument(metrics.size() == 6, "Invalid number of metrics about coverage, found %s but expected 6", metrics.size());
    return metrics;
  }

  private static class QualityGateCondition {
    private final long id;
    private final long metricId;
    private final Long period;
    private final String valueError;
    private final String valueWarning;

    QualityGateCondition(Select.Row row) throws SQLException {
      this.id = requireNonNull(row.getLong(1));
      this.metricId = requireNonNull(row.getLong(2));
      this.period = row.getNullableLong(3);
      this.valueError = row.getNullableString(4);
      this.valueWarning = row.getNullableString(5);
    }

    long getId() {
      return id;
    }

    long getMetricId() {
      return metricId;
    }

    @CheckForNull
    Long getPeriod() {
      return period;
    }

    @CheckForNull
    String getValueError() {
      return valueError;
    }

    @CheckForNull
    String getValueWarning() {
      return valueWarning;
    }
  }

  private static class Metric {
    private final long id;
    private final String key;

    Metric(Select.Row row) throws SQLException {
      this.id = requireNonNull(row.getLong(1));
      this.key = requireNonNull(row.getString(2));
    }

    long getId() {
      return id;
    }

    String getKey() {
      return key;
    }
  }
}
