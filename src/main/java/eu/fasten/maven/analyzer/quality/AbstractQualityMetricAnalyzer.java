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

import java.util.Map;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import eu.fasten.maven.MavenGraphNode;
import eu.fasten.maven.analyzer.RiskContext;
import eu.fasten.maven.analyzer.RiskReport;

/**
 * @version $Id$
 */
public abstract class AbstractQualityMetricAnalyzer<T> implements QualityMetricAnalyzer
{
    protected Class<T> thresholdClass;

    protected T threshold;

    protected abstract String getMetric();

    @Override
    public void initialize(Object configuration)
    {
        // Assume it's a class and not a more complex Type
        // TODO: support any parameterized types
        this.thresholdClass = (Class<T>) TypeUtils.getTypeArguments(getClass(), AbstractQualityMetricAnalyzer.class)
            .values().iterator().next();

        this.threshold = (T) ConvertUtils.convert(configuration, this.thresholdClass);
    }

    @Override
    public void analyze(RiskContext context, MavenGraphNode node, Map<String, Object> metrics, RiskReport report)
    {
        Object value = metrics.get(getMetric());

        analyzeValue(context, node, (T) ConvertUtils.convert(value, this.thresholdClass), report);
    }

    protected abstract void analyzeValue(RiskContext context, MavenGraphNode node, T value, RiskReport report);
}
