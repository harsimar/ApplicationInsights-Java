// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.applicationinsights.smoketest;

import static com.microsoft.applicationinsights.smoketest.EnvironmentValue.TOMCAT_8_JAVA_11;
import static com.microsoft.applicationinsights.smoketest.EnvironmentValue.TOMCAT_8_JAVA_17;
import static com.microsoft.applicationinsights.smoketest.EnvironmentValue.TOMCAT_8_JAVA_19;
import static com.microsoft.applicationinsights.smoketest.EnvironmentValue.TOMCAT_8_JAVA_20;
import static com.microsoft.applicationinsights.smoketest.EnvironmentValue.TOMCAT_8_JAVA_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import com.microsoft.applicationinsights.smoketest.schemav2.Data;
import com.microsoft.applicationinsights.smoketest.schemav2.DataPoint;
import com.microsoft.applicationinsights.smoketest.schemav2.Envelope;
import com.microsoft.applicationinsights.smoketest.schemav2.MetricData;
import io.opentelemetry.proto.metrics.v1.Metric;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.model.HttpRequest;

@UseAgent
abstract class JmxMetricTest {

  @RegisterExtension
  static final SmokeTestExtension testing = SmokeTestExtension.builder().useOtlpEndpoint().build();

  /**
   * Note about jmx metrics in this test suite: NameWithDot: An edge case where a dot in the mbean
   * is not a path separator in the attribute, specifically when using org.weakref.jmx package
   * DefaultJmxMetricNameOverride: If a customer overrides the default ThreadCount metric with their
   * own metric name, we should be collecting the metric with the name the customer specified
   * WildcardJmxMetric: This covers the case of ? and * in the objectName, with multiple matching
   * object names. The expected metric value here is the sum of all the CollectionCount attribute
   * values for each matching object name. The matching objectNames are: - Java 8:
   * java.lang:type=GarbageCollector,name=PS Scavenge, java.lang:type=GarbageCollector,name=PS
   * MarkSweep - the corresponding metric names are PSScavenge and PSMarksweep - Higher than Java 8:
   * G1 Young Generation,type=GarbageCollector and G1 Old Generation,type=GarbageCollector. - the
   * corrresponding metric names are GCYoung and GCOld Loaded_Class_Count: This covers the case of
   * collecting a default jmx metric that the customer did not specify in applicationInsights.json.
   * Also note that there are underscores instead of spaces, as we are emitting the metric via
   * OpenTelemetry now. We intend to implement a change in azure-sdk-for-java repo to have the
   * metrics still emit with spaces to Breeze, but the OTEL collector will still get the metric
   * names with _. When that change gets merged & incorporated, we will need to change
   * this/DetectUnexpectedOtelMetrics test so that the Breeze verification expects our default jmx
   * metrics with spaces. BooleanJmxMetric: This covers the case of a jmx metric attribute with a
   * boolean value. DotInAttributeNameAsPathSeparator: This covers the case of an attribute having a
   * dot in the name as a path separator.
   */
  private static final Set<String> jmxMetricsAllJavaVersionsBreeze =
      new HashSet<>(
          Arrays.asList(
              "NameWithDot",
              "DefaultJmxMetricNameOverride",
              "WildcardJmxMetric",
              "Loaded Class Count",
              "BooleanJmxMetric",
              "DotInAttributeNameAsPathSeparator"));

  private static final Set<String> jmxMetricsAllJavaVersionsOtlp =
      new HashSet<>(
          Arrays.asList(
              "NameWithDot",
              "DefaultJmxMetricNameOverride",
              "WildcardJmxMetric",
              "Loaded_Class_Count",
              "BooleanJmxMetric",
              "DotInAttributeNameAsPathSeparator"));

  static final Set<String> gcOptionalJmxMetrics =
      new HashSet<>(Arrays.asList("PSScavenge", "PSMarkSweep", "GCOld", "GCYoung"));

  @Test
  @TargetUri("/test")
  void doMostBasicTest() throws Exception {
    verifyJmxMetricsSentToBreeze();
    verifyJmxMetricsSentToOtlpEndpoint();
  }

  @SuppressWarnings("PreferJavaTimeOverload")
  private void verifyJmxMetricsSentToOtlpEndpoint() {
    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> {
              HttpRequest[] requests =
                  testing
                      .mockedOtlpIngestion
                      .getCollectorServer()
                      .retrieveRecordedRequests(request());

              // verify metrics
              List<Metric> metrics =
                  testing.mockedOtlpIngestion.extractMetricsFromRequests(requests);

              Map<String, Integer> occurrences = new HashMap<>();

              // counting all occurrences of the jmx metrics that are applicable to all java
              // versions
              for (Metric metric : metrics) {
                String metricName = metric.getName();
                if (jmxMetricsAllJavaVersionsOtlp.contains(metricName)) {
                  if (occurrences.containsKey(metricName)) {
                    occurrences.put(metricName, occurrences.get(metricName) + 1);
                  } else {
                    occurrences.put(metricName, 1);
                  }
                }
              }

              for (Map.Entry<String, Integer> entry : occurrences.entrySet()) {
                System.out.println(entry.toString());
              }
              // confirm that those metrics received once or twice
              // (the collector seems to run for 5-10 sec)
              assertThat(occurrences.keySet()).hasSize(6);
              for (int value : occurrences.values()) {
                assertThat(value).isBetween(1, 2);
              }
            });
  }

  private void verifyJmxMetricsSentToBreeze() throws Exception {
    List<Envelope> metricItems =
        testing.mockedIngestion.waitForItems(JmxMetricTest::isJmxMetric, 1, 10, TimeUnit.SECONDS);

    assertThat(metricItems).hasSizeBetween(7, 16);

    Set<String> metricNames = new HashSet<>();
    double wildcardValueSum = 0.0;
    double gcFirstMatch = 0.0;
    double gcSecondMatch = 0.0;
    for (Envelope envelope : metricItems) {
      MetricData metricData = (MetricData) ((Data<?>) envelope.getData()).getBaseData();
      List<DataPoint> points = metricData.getMetrics();
      assertThat(points).hasSize(1);
      String metricName = points.get(0).getName();
      metricNames.add(metricName);

      // verifying values of some metrics
      double value = points.get(0).getValue();
      if (metricName.equals("NameWithDot")) {
        assertThat(value).isEqualTo(5);
      }
      if (metricName.equals("GCOld") || metricName.equals("PSScavenge")) {
        gcFirstMatch += value;
      }
      if (metricName.equals("GCYoung") || metricName.equals("PSMarkSweep")) {
        gcSecondMatch += value;
      }
      if (metricName.equals("WildcardJmxMetric")) {
        wildcardValueSum += value;
      }
      if (metricName.equals("BooleanJmxMetric")) {
        assertThat(value).isEqualTo(1.0);
      }

      assertThat(verifyNoInternalAttributes(envelope)).isTrue();
    }

    // This will indirectly check the occurrences of the optional gc metrics
    // and confirm that the wildcard metric has the expected value
    assertThat(wildcardValueSum).isEqualTo(gcFirstMatch + gcSecondMatch);

    assertThat(metricNames).containsAll(jmxMetricsAllJavaVersionsBreeze);
  }

  private static boolean verifyNoInternalAttributes(Envelope envelope) {
    MetricData metricData = (MetricData) ((Data<?>) envelope.getData()).getBaseData();
    for (String key : metricData.getProperties().keySet()) {
      if (key.startsWith("applicationinsights.internal.")) {
        return false;
      }
    }
    return true;
  }

  private static boolean isJmxMetric(Envelope envelope) {
    if (!envelope.getData().getBaseType().equals("MetricData")) {
      return false;
    }
    MetricData md = SmokeTestExtension.getBaseData(envelope);
    String incomingMetricName = md.getMetrics().get(0).getName();
    return jmxMetricsAllJavaVersionsBreeze.contains(incomingMetricName)
        || gcOptionalJmxMetrics.contains(incomingMetricName);
  }

  @Environment(TOMCAT_8_JAVA_8)
  static class Tomcat8Java8Test extends JmxMetricTest {}

  @Environment(TOMCAT_8_JAVA_11)
  static class Tomcat8Java11Test extends JmxMetricTest {}

  @Environment(TOMCAT_8_JAVA_17)
  static class Tomcat8Java17Test extends JmxMetricTest {}

  @Environment(TOMCAT_8_JAVA_19)
  static class Tomcat8Java19Test extends JmxMetricTest {}

  @Environment(TOMCAT_8_JAVA_20)
  static class Tomcat8Java20Test extends JmxMetricTest {}
}
