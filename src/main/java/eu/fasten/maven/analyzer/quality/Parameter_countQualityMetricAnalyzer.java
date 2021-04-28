/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.fasten.maven.analyzer.quality;

import eu.fasten.maven.StitchedGraphNode;
import eu.fasten.maven.analyzer.RiskContext;
import eu.fasten.maven.analyzer.RiskReport;

/**
 * @version $Id$
 */
public class Parameter_countQualityMetricAnalyzer extends AbstractQualityMetricAnalyzer<Integer>
{
    @Override
    protected String getMetric()
    {
        return "parameter_count";
    }

    @Override
    protected void analyzeValue(RiskContext context, StitchedGraphNode node, Integer value, RiskReport report)
    {
        if (value > this.threshold) {
            report.error(node,
                "The number of parameters in the callable {} located in {} is greater than the maximum value {}.");
        }
    }
}
