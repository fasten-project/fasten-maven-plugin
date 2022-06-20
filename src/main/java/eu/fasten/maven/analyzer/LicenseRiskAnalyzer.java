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
import java.util.Collection;
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
import org.apache.maven.project.MavenProject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import eu.fasten.maven.MavenExtendedRevisionJavaCallGraph;
import eu.fasten.maven.license.LicenseResult;
import eu.fasten.maven.license.LicenseResult.LicenseResultType;

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

    @Override
    public Set<String> getMavenExtras()
    {
        return MAVEN_EXTRAS;
    }

    @Override
    public void analyze(RiskContext context, RiskReport report)
    {
        // Get the outbound licenses
        Set<String> outboundLicenses = getOutboundLicences(((MavenRiskContext) context).getMavenProject());

        // Validate each dependency with the outbound licenses
        for (String outbound : outboundLicenses) {
            for (MavenExtendedRevisionJavaCallGraph dependency : context.getGraph().getFullDependenciesCGs()) {
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

    private void validate(String outbound, MavenExtendedRevisionJavaCallGraph dependency, RiskReport report)
        throws IOException, URISyntaxException
    {
        List<LicenseResult> errors = null;
        List<LicenseResult> warnings = null;

        for (License license : dependency.getMavenLicenses()) {
            LicenseResult result = validate(outbound, license.getName());

            if (result.getStatus() == LicenseResultType.COMPATIBLE) {
                // The dependency is compatible if at least one of its licenses is
                return;
            } else if (result.getStatus() == LicenseResultType.NOT_COMPATIBLE) {
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
            errors.forEach(e -> report.error("{}: {}", dependency.getArtifact().toString(), e.getMessage()));
        }

        // Report warnings
        if (warnings != null) {
            warnings.forEach(w -> report.warn("{}: {}", dependency.getArtifact().toString(), w.getMessage()));
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

    public static Set<String> getOutboundLicences(MavenProject project)
    {
        return project.getLicenses().stream().map(License::getName).collect(Collectors.toSet());
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
                    return new LicenseResult((JSONObject) json.get(0));
                } else if (response.getCode() == 404) {
                    throw new IOException("License validation service not available (404)");
                } else {
                    throw new IOException("Unexpected code when response (" + response.getCode() + ")");
                }
            }
        }
    }

    public static List<LicenseResult> get(Collection<String> inbound, Collection<String> outbound)
        throws URISyntaxException, IOException
    {
        URIBuilder builder = new URIBuilder(LCVAPIURL);
        builder.addParameter(LCVAPI_INBOUND, String.join(";", inbound));
        builder.addParameter(LCVAPI_OUTBOUND, String.join(";", outbound));

        HttpGet httpGet = new HttpGet(builder.build());

        try (CloseableHttpClient httpclient = HttpClients.createSystem()) {
            try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
                if (response.getCode() == 200) {
                    JSONArray json = new JSONArray(new JSONTokener(response.getEntity().getContent()));
                    List<LicenseResult> results = new ArrayList<>(json.length());
                    for (Object obj : json) {
                        results.add(new LicenseResult((JSONObject) obj));
                    }
                    return results;
                } else if (response.getCode() == 404) {
                    throw new IOException("License validation service not available (404)");
                } else {
                    throw new IOException("Unexpected code when response (" + response.getCode() + ")");
                }
            }
        }
    }
}
