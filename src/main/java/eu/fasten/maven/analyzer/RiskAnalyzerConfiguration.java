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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Configure the behavior of a {@link RiskAnalyzer}.
 * 
 * @version $Id$
 */
public class RiskAnalyzerConfiguration extends HashMap<String, Object>
{
    private String type;

    private Boolean failOnRisk;

    private List<Pattern> ignoredCallables = Collections.emptyList();

    /**
     * @return the identifier of the risk analyzer
     */
    public String getType()
    {
        return this.type;
    }

    /**
     * @param type the identifier of the risk analyzer
     */
    public void setType(String type)
    {
        this.type = type;
    }

    /**
     * @return true/false if the analyzer have a specific behavior, null to fallback on the general behavior
     */
    public Boolean getFailOnRisk()
    {
        return this.failOnRisk;
    }

    /**
     * @param failOnRisk true/false if the analyzer have a specific behavior, null to fallback on the general behavior
     */
    public void setFailOnRisk(Boolean failOnRisk)
    {
        this.failOnRisk = failOnRisk;
    }

    /**
     * @return the ignoredCallables
     */
    public List<Pattern> getIgnoredCallables()
    {
        return this.ignoredCallables;
    }

    /**
     * @param ignoredCallables the ignoredCallables to set
     */
    public void setIgnoredCallables(List<String> ignoredCallables)
    {
        this.ignoredCallables = ignoredCallables.stream().map(Pattern::compile).collect(Collectors.toList());
    }
}
