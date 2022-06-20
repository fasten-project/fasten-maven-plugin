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
package eu.fasten.maven;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.License;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import eu.fasten.maven.analyzer.LicenseRiskAnalyzer;
import eu.fasten.maven.license.LicenseResult;

/**
 * Gather all the licenses from the dependency tree.
 *
 * @version $Id: 982ced7f89e6c39126d28b2f9e5fcac365250288 $
 */
@Mojo(name = "licenses", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresProject = true, threadSafe = true)
public class LicensesMojo extends AbstractFASTENMojo
{
    private class LicenseReport
    {
        final LicenseResult result;

        final List<MavenProject> projects;

        LicenseReport(LicenseResult result, List<MavenProject> projects)
        {
            this.result = result;
            this.projects = projects;
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        Set<String> outboundLicenses = LicenseRiskAnalyzer.getOutboundLicences(this.project);

        Set<String> licenses = new HashSet<>(outboundLicenses);

        Map<String, List<MavenProject>> inboundLicenses = new HashMap<>();
        for (Artifact dependencyArtifact : this.project.getArtifacts()) {
            MavenProject dependencyProject = getMavenProject(dependencyArtifact);

            for (License mavenLicense : dependencyProject.getLicenses()) {
                String licenseName = mavenLicense.getName();

                List<MavenProject> dependencies = inboundLicenses.computeIfAbsent(licenseName, k -> new ArrayList<>());
                dependencies.add(dependencyProject);

                licenses.add(licenseName);
            }
        }

        List<LicenseResult> results;
        try {
            results = LicenseRiskAnalyzer.get(inboundLicenses.keySet(), outboundLicenses);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve license names to SPDX", e);
        }

        Set<String> resolvedLicenses = new HashSet<>(licenses.size());

        Map<String, List<LicenseReport>> outboundLicensesReport = new HashMap<>();
        Map<String, List<LicenseReport>> inboundLicensesReport = new HashMap<>();
        for (LicenseResult result : results) {
            // Outbound licenses
            List<LicenseReport> outboundReports =
                outboundLicensesReport.computeIfAbsent(result.getSpdxOutbound(), k -> new ArrayList<>());
            outboundReports.add(new LicenseReport(result, List.of(this.project)));

            // Inbound licenses
            List<LicenseReport> inboundReports =
                inboundLicensesReport.computeIfAbsent(result.getSpdxInbound(), k -> new ArrayList<>());
            inboundReports.add(new LicenseReport(result, inboundLicenses.get(result.getInbound())));
        }
        Set<String> spdxLicenses = new HashSet<>();
        spdxLicenses.addAll(outboundLicensesReport.keySet());
        spdxLicenses.addAll(inboundLicensesReport.keySet());
        spdxLicenses.remove(null);

        // Report outbound licenses
        getLog().info("Outbound licenses:");
        for (Map.Entry<String, List<LicenseReport>> entry : outboundLicensesReport.entrySet()) {
            List<String> outboundLicenseNames =
                entry.getValue().stream().map(r -> r.result.getOutbound()).distinct().collect(Collectors.toList());

            String spdx;
            if (entry.getKey() == null) {
                spdx = "Unknown SPDX license";
            } else {
                spdx = entry.getKey();
                resolvedLicenses.addAll(outboundLicenseNames);
            }

            getLog().info("  * " + spdx + ": " + String.join(", ", outboundLicenseNames));
        }

        getLog().info("");

        // Report inbound licenses
        getLog().info("Inbound licenses:");
        for (Map.Entry<String, List<LicenseReport>> entry : inboundLicensesReport.entrySet()) {
            String spdx;
            if (entry.getKey() == null) {
                spdx = "Unknown SPDX license";
            } else {
                spdx = entry.getKey();
            }

            getLog().info("  * " + spdx + ": ");

            for (LicenseReport report : entry.getValue()) {
                if (entry.getKey() != null) {
                    resolvedLicenses.add(report.result.getInbound());
                }

                getLog().info("    * " + report.result.getInbound() + ":" + toString(report.projects));
            }
        }

        getLog().info("");

        getLog().info("Number of artifacts: " + (1 + this.project.getArtifacts().size()));
        getLog().info("Number of licenses: " + licenses.size());
        getLog()
            .info("Number of resolved SPDX licenses: " + (spdxLicenses.size() - (spdxLicenses.contains(null) ? 1 : 0))
                + " from " + resolvedLicenses.size() + " licenses");
        getLog().info("Number of unknown licenses: " + inboundLicensesReport.get(null).size());
    }

    private String toString(List<MavenProject> projects)
    {
        return projects.stream().map(p -> p.getGroupId() + ':' + p.getArtifactId()).collect(Collectors.joining(", "));
    }
}
