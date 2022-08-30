/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.smoketest;

import static com.microsoft.applicationinsights.smoketest.WarEnvironmentValue.TOMCAT_8_JAVA_11;
import static com.microsoft.applicationinsights.smoketest.WarEnvironmentValue.TOMCAT_8_JAVA_11_OPENJ9;
import static com.microsoft.applicationinsights.smoketest.WarEnvironmentValue.TOMCAT_8_JAVA_17;
import static com.microsoft.applicationinsights.smoketest.WarEnvironmentValue.TOMCAT_8_JAVA_18;
import static com.microsoft.applicationinsights.smoketest.WarEnvironmentValue.TOMCAT_8_JAVA_19;
import static com.microsoft.applicationinsights.smoketest.WarEnvironmentValue.TOMCAT_8_JAVA_8;
import static com.microsoft.applicationinsights.smoketest.WarEnvironmentValue.TOMCAT_8_JAVA_8_OPENJ9;
import static com.microsoft.applicationinsights.smoketest.WarEnvironmentValue.WILDFLY_13_JAVA_8;
import static com.microsoft.applicationinsights.smoketest.WarEnvironmentValue.WILDFLY_13_JAVA_8_OPENJ9;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@UseAgent
abstract class OpenTelemetryApiSupportInstrumentationKeyTest {

  @RegisterExtension
  static final SmokeTestExtension testing =
      SmokeTestExtension.builder().usesGlobalIngestionEndpoint().build();

  @Test
  @TargetUri("/test-overriding-ikey")
  void testOverridingIkey() throws Exception {
    Telemetry telemetry = testing.getTelemetry(0);

    assertThat(telemetry.rd.getName())
        .isEqualTo("GET /OpenTelemetryApiSupport/test-overriding-ikey");
    assertThat(telemetry.rd.getUrl())
        .matches("http://localhost:[0-9]+/OpenTelemetryApiSupport/test-overriding-ikey");
    assertThat(telemetry.rd.getResponseCode()).isEqualTo("200");
    assertThat(telemetry.rd.getSuccess()).isTrue();
    assertThat(telemetry.rd.getSource()).isNull();
    assertThat(telemetry.rd.getProperties())
        .containsExactly(entry("_MS.ProcessedByMetricExtractors", "True"));
    assertThat(telemetry.rd.getMeasurements()).isEmpty();

    assertThat(telemetry.rdEnvelope.getIKey()).isEqualTo("12341234-1234-1234-1234-123412341234");
  }

  @Environment(TOMCAT_8_JAVA_8)
  static class Tomcat8Java8Test extends OpenTelemetryApiSupportInstrumentationKeyTest {}

  @Environment(TOMCAT_8_JAVA_8_OPENJ9)
  static class Tomcat8Java8OpenJ9Test extends OpenTelemetryApiSupportInstrumentationKeyTest {}

  @Environment(TOMCAT_8_JAVA_11)
  static class Tomcat8Java11Test extends OpenTelemetryApiSupportInstrumentationKeyTest {}

  @Environment(TOMCAT_8_JAVA_11_OPENJ9)
  static class Tomcat8Java11OpenJ9Test extends OpenTelemetryApiSupportInstrumentationKeyTest {}

  @Environment(TOMCAT_8_JAVA_17)
  static class Tomcat8Java17Test extends OpenTelemetryApiSupportInstrumentationKeyTest {}

  @Environment(TOMCAT_8_JAVA_18)
  static class Tomcat8Java18Test extends OpenTelemetryApiSupportInstrumentationKeyTest {}

  @Environment(TOMCAT_8_JAVA_19)
  static class Tomcat8Java19Test extends OpenTelemetryApiSupportInstrumentationKeyTest {}

  @Environment(WILDFLY_13_JAVA_8)
  static class Wildfly13Java8Test extends OpenTelemetryApiSupportInstrumentationKeyTest {}

  @Environment(WILDFLY_13_JAVA_8_OPENJ9)
  static class Wildfly13Java8OpenJ9Test extends OpenTelemetryApiSupportInstrumentationKeyTest {}
}