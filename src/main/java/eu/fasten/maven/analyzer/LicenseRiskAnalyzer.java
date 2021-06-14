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
package eu.fasten.maven.analyzer;

import java.util.Set;

import org.apache.commons.collections4.SetUtils;

/**
 * Find license incompatibilities in the stiched graph.
 * 
 * @version $Id$
 */
public class LicenseRiskAnalyzer extends AbstractRiskAnalyzer
{
    private static final Set<String> MAVEN_EXTRAS = SetUtils.hashSet("licenses");

    @Override
    public Set<String> getMavenExtras()
    {
        return MAVEN_EXTRAS;
    }

    @Override
    public void analyze(RiskContext context, RiskReport report)
    {
        // TODO
    }
}
