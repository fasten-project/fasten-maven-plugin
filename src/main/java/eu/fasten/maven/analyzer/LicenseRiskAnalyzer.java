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
import java.util.List;
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

        // Get the inbound licenses
        List<String> inbound = getInboundLicenses((MavenRiskContext) context);

        // Validate each outbound license with the inbound licenses
        for (String outbound : outboundLicenses) {
            try {
                JSONArray result = validate(inbound, outbound, report);

                for (Object obj : result) {
                    if (obj instanceof String && ((String) obj).contains("not compatible")) {
                        // TODO: indicate where that inbound license is coming from
                        report.error("Found a license incompatibility: {}", obj);
                    }
                }
            } catch (Exception e) {
                report.error("Failed to validate compatibility between inbound licenses {} and outbound license [{}]",
                    inbound, outbound, e);
            }
        }
    }

    private List<String> getOutboundLicences(MavenRiskContext context)
    {
        return context.getMavenProject().getLicenses().stream().map(License::getName).collect(Collectors.toList());
    }

    private List<String> getInboundLicenses(MavenRiskContext context)
    {
        List<String> licenses = new ArrayList<>();

        for (MavenResolvedCallGraph dependency : context.getGraph().getFullDependenciesRCGs()) {
            // TODO: adding all license at the same level is wrong, it should be a OR
            dependency.getLicenses().forEach(l -> licenses.add(l.getName()));
        }

        return licenses;
    }

    private JSONArray validate(List<String> inbound, String outbound, RiskReport report)
        throws IOException, URISyntaxException
    {
        URIBuilder builder = new URIBuilder(LCVAPIURL);
        builder.addParameter(LCVAPI_INBOUND, String.join(";", inbound));
        builder.addParameter(LCVAPI_OUTBOUND, outbound);

        HttpGet httpGet = new HttpGet(builder.build());

        try (CloseableHttpClient httpclient = HttpClients.createSystem()) {
            try (CloseableHttpResponse response = httpclient.execute(httpGet)) {
                if (response.getCode() == 200) {
                    return new JSONArray(new JSONTokener(response.getEntity().getContent()));
                } else if (response.getCode() == 404) {
                    throw new IOException("License validation service not available (404)");
                } else {
                    throw new IOException("Unexpected code when response (" + response.getCode() + ")");
                }
            }
        }
    }
}
