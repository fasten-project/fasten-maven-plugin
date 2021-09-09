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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.SetUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.maven.model.License;
import org.json.JSONArray;
import org.json.JSONTokener;

import eu.fasten.maven.MavenResolvedCallGraph;

/**
 * Find license incompatibilities in the runtime dependencies.
 * 
 * @version $Id$
 */
// TODO: Get licenses from metadata
public class LicenseRiskAnalyzer extends AbstractRiskAnalyzer
{
    private static final String LCVAPIURL = "https://lima.ewi.tudelft.nl/lcv/LicensesInput";

    private static final String LCVAPI_INBOUND = "InboundLicenses";

    private static final String LCVAPI_OUTBOUND = "OutboundLicense";

    private static final String LICENSES_KEY = "licenses";

    private static final Set<String> MAVEN_EXTRAS = SetUtils.hashSet(LICENSES_KEY);

    private Map<String, Map<String, LicenseResult>> cache = new HashMap<>();

    enum LicenseResultType
    {
        COMPATIBLE,

        NOT_COMPATIBLE,

        UNKNOWN
    }

    class LicenseResult
    {
        String message;

        LicenseResultType type;

        public LicenseResult(String message)
        {
            if (message.contains("is compatible")) {
                this.type = LicenseResultType.COMPATIBLE;
            } else if (message.contains("not compatible")) {
                this.type = LicenseResultType.NOT_COMPATIBLE;
            } else {
                this.type = LicenseResultType.UNKNOWN;
            }
        }
    }

    @Override
    public Set<String> getMavenExtras()
    {
        return MAVEN_EXTRAS;
    }

    @Override
    public void analyze(RiskContext context, RiskReport report)
    {
        // Get the outbound licenses
        List<String> outboundLicenses = getOutboundLicences((MavenRiskContext) context);

        // Validate each dependency with the outbound licenses
        for (String outbound : outboundLicenses) {
            for (MavenResolvedCallGraph dependency : context.getGraph().getFullDependenciesRCGs()) {
                if (!report.getAnalyzer().isDependencyIgnored(dependency)) {
                    try {
                        validate(outbound, dependency, report);
                    } catch (Exception e) {
                        report.error("{}: Failed to validate compatibility of dependency with outbound license [{}]",
                            dependency.getArtifact().toString(), outbound, e);
                    }
                }
            }
        }
    }

    private void validate(String outbound, MavenResolvedCallGraph dependency, RiskReport report)
        throws IOException, URISyntaxException
    {
        List<LicenseResult> errors = null;
        List<LicenseResult> warnings = null;

        for (License license : dependency.getLicenses()) {
            LicenseResult result = validate(outbound, license.getName());

            if (result.type == LicenseResultType.COMPATIBLE) {
                // The dependency is compatible if at least one of its licenses is
                return;
            } else if (result.type == LicenseResultType.NOT_COMPATIBLE) {
                if (errors == null) {
                    errors = new ArrayList<>();
                }
                errors.add(result);
            } else {
                if (warnings == null) {
                    warnings = new ArrayList<>();
                }
                warnings.add(result);
            }
        }

        // Report errors
        if (errors != null) {
            errors.forEach(e -> report.error("{}: {}", dependency.getArtifact().toString(), e.message));
        }

        // Report warnings
        if (warnings != null) {
            warnings.forEach(w -> report.warn("{}: {}", dependency.getArtifact().toString(), w.message));
        }
    }

    private LicenseResult validate(String outbound, String inbound) throws IOException, URISyntaxException
    {
        LicenseResult result;

        // Try the cache
        Map<String, LicenseResult> inboundCache = this.cache.get(outbound);
        if (inboundCache != null) {
            result = inboundCache.get(inbound);
            if (result != null) {
                return result;
            }
        }

        // Ask the LCV service
        result = validateOnline(outbound, inbound);

        // Update the cache
        this.cache.computeIfAbsent(outbound, k -> new HashMap<>()).put(inbound, result);

        return result;
    }

    private List<String> getOutboundLicences(MavenRiskContext context)
    {
        return context.getMavenProject().getLicenses().stream().map(License::getName).collect(Collectors.toList());
    }

    private Map<String, List<MavenResolvedCallGraph>> getInboundLicenses(MavenRiskContext context)
    {
        Map<String, List<MavenResolvedCallGraph>> licenses = new HashMap<>();

        for (MavenResolvedCallGraph dependency : context.getGraph().getFullDependenciesRCGs()) {
            dependency.getLicenses()
                .forEach(l -> licenses.computeIfAbsent(l.getName(), k -> new ArrayList<>()).add(dependency));
        }

        return licenses;
    }

    private LicenseResult validateOnline(String outbound, String inbound) throws IOException, URISyntaxException
    {
        URIBuilder builder = new URIBuilder(LCVAPIURL);
        builder.addParameter(LCVAPI_INBOUND, String.join(";", inbound));
        builder.addParameter(LCVAPI_OUTBOUND, outbound);

        HttpGet httpGet = new HttpGet(builder.build());

        try (CloseableHttpClient httpclient = HttpClients.createSystem()) {
            try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
                if (response.getCode() == 200) {
                    JSONArray json = new JSONArray(new JSONTokener(response.getEntity().getContent()));
                    String message = (String) json.get(0);
                    return new LicenseResult(message);
                } else if (response.getCode() == 404) {
                    throw new IOException("License validation service not available (404)");
                } else {
                    throw new IOException("Unexpected code when response (" + response.getCode() + ")");
                }
            }
        }
    }
}
