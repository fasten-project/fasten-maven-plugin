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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import eu.fasten.analyzer.javacgopal.data.CallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.opal.exceptions.OPALException;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.maven.analyzer.MavenRiskContext;
import eu.fasten.maven.analyzer.RiskAnalyzer;
import eu.fasten.maven.analyzer.RiskAnalyzerConfiguration;
import eu.fasten.maven.analyzer.RiskContext;
import eu.fasten.maven.analyzer.RiskReport;

/**
 * Build a call graph of the module and its dependencies.
 *
 * @version $Id: 982ced7f89e6c39126d28b2f9e5fcac365250288 $
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresProject = true, threadSafe = true)
public class CheckMojo extends AbstractMojo
{
    public enum MetadataDownload
    {
        // Only for dependencies found on the call graph server
        auto,

        // For all released dependencies
        releases,

        // For all dependencies
        all
    }

    @Component
    private ProjectBuilder projectBuilder;

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(property = "localRepository")
    private ArtifactRepository localRepository;

    @Parameter(defaultValue = "target/call-graphs/")
    private File outputDirectory;

    @Parameter(defaultValue = "CHA")
    private String genAlgorithm = "CHA";

    @Parameter(defaultValue = "true", property = "failOnRisk")
    private boolean failOnRisk = true;

    @Parameter
    private List<RiskAnalyzerConfiguration> risks;

    @Parameter(defaultValue = "https://api.fasten-project.eu/api", property = "fastenApiUrl")
    private String fastenApiUrl = "https://api.fasten-project.eu/api";

    @Parameter(defaultValue = "100", property = "fasten.metadataBatch")
    private int metadataBatch = 100;

    @Parameter(defaultValue = "auto", property = "fasten.metadataDownload")
    private MetadataDownload metadataDownload = MetadataDownload.auto;

    @Parameter(defaultValue = "true", property = "fasten.serialize")
    private boolean serialize = true;

    @Parameter(defaultValue = "true", property = "fasten.analyze")
    private boolean analyze = true;

    private List<RiskAnalyzer> analyzersCache;

    private Set<String> packageMetadataNames;

    private Set<String> mavenExtras;

    private CloseableHttpClient httpclient;

    StitchedGraph graph;

    List<RiskReport> reports;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        // Only JAR packages are supported right now
        if (!this.project.getPackaging().equals("jar")) {
            getLog().info("Only project with packaging JAR are supported. Skipping.");

            return;
        }

        // Switch off remote access when Maven is in offline mode
        if (!this.session.isOffline()) {
            this.httpclient = HttpClients.createSystem();
        }

        getLog().info("Generating local call graph of the project.");

        // Build project call graph
        File projectCallGraphFile = new File(this.outputDirectory, "project.json");
        MavenExtendedRevisionJavaCallGraph projectCG;
        try {
            projectCG =
                buildCallGraph(this.project.getArtifact(), new File(this.project.getBuild().getOutputDirectory()),
                    projectCallGraphFile, this.project.getGroupId() + ':' + this.project.getArtifactId());
        } catch (OPALException e) {
            throw new MojoExecutionException(
                "Failed to build a call graph for directory [" + this.project.getBuild().getOutputDirectory() + "]", e);
        }

        // Build/Get dependencies call graphs
        List<MavenExtendedRevisionJavaCallGraph> dependenciesCGs = new ArrayList<>(this.project.getArtifacts().size());
        List<ExtendedRevisionJavaCallGraph> all = new ArrayList<>(this.project.getArtifacts().size() + 1);
        all.add(projectCG);
        for (Artifact artifact : this.project.getArtifacts()) {
            getLog().info("Generating call graphs for dependency [" + artifact + "].");
            try {
                MavenExtendedRevisionJavaCallGraph mcg = getCallGraph(artifact);
                if (mcg != null) {
                    dependenciesCGs.add(mcg);
                    all.add(mcg);
                }
            } catch (Exception e) {
                getLog().warn("Failed to generate a call graph for artifact [" + artifact + "]: "
                    + ExceptionUtils.getRootCauseMessage(e));
            }
        }

        if (this.analyze) {
            // Produce resolved call graphs
            getLog().info("Produce resolved call graphs.");

            CGMerger merger = new CGMerger(all);

            // Produce the resolved graph for the project
            MavenResolvedCallGraph projectRCG =
                resolveCG(merger, projectCG, new File(this.outputDirectory, "project.resolved.json"));

            // Produce the resolved graph for each dependency
            List<MavenResolvedCallGraph> dependenciesRCGs = new ArrayList<>(dependenciesCGs.size());
            for (MavenExtendedRevisionJavaCallGraph dependencyCG : dependenciesCGs) {
                File outputFile = toOutputFile(dependencyCG.getArtifact(), ".resolved.json");

                dependenciesRCGs.add(resolveCG(merger, dependencyCG, outputFile));
            }

            // Produce the stitched call graph
            getLog().info("Produce stitched call graphs.");

            this.graph = new StitchedGraph(projectRCG, dependenciesRCGs);

            // Enrich the stitched call graph
            try {
                enrich();
            } catch (IOException | URISyntaxException e) {
                getLog().warn("Failed to enrich the stitched graph", e);
            }

            // Analyze the stitched call graph
            analyze();
        }
    }

    private File toOutputFile(Artifact artifact, String extension)
    {
        return new File(this.outputDirectory, artifact.getGroupId() + '/' + artifact.getArtifactId() + '/'
            + artifact.getArtifactId() + '-' + artifact.getVersion() + extension);
    }

    private List<RiskAnalyzer> getAnalyzers() throws MojoExecutionException
    {
        if (this.analyzersCache == null) {
            if (this.risks == null) {
                this.analyzersCache = Collections.emptyList();
            } else {
                this.analyzersCache = new ArrayList<>(this.risks.size());

                for (RiskAnalyzerConfiguration riskConfiguration : this.risks) {
                    RiskAnalyzer analyzer;
                    try {
                        analyzer = createAnalyzer(riskConfiguration.getType());
                    } catch (Exception e) {
                        throw new MojoExecutionException(
                            "Failed to create an analyzer for type " + riskConfiguration.getType(), e);
                    }

                    if (analyzer == null) {
                        throw new MojoExecutionException(
                            "Could not find any analyzer for type " + riskConfiguration.getType());
                    }

                    analyzer.initialize(riskConfiguration);

                    this.analyzersCache.add(analyzer);
                }
            }
        }

        return this.analyzersCache;
    }

    private RiskAnalyzer createAnalyzer(String type) throws InstantiationException, IllegalAccessException,
        IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException
    {
        // Try standard analyzers
        if (type.startsWith("fasten.")) {
            String className = "eu.fasten.maven.analyzer." + StringUtils.capitalize(type.substring("fasten.".length()))
                + "RiskAnalyzer";
            try {
                Class<RiskAnalyzer> clazz = (Class) Thread.currentThread().getContextClassLoader().loadClass(className);

                return clazz.getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                getLog().debug("Failed to find the class for name " + className, e);
            }
        }

        // Try custom analyzer
        try {
            Class<RiskAnalyzer> clazz = (Class) Thread.currentThread().getContextClassLoader().loadClass(type);

            return clazz.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            getLog().debug("Failed to find the class for name " + type, e);
        }

        return null;
    }

    private void analyze() throws MojoFailureException, MojoExecutionException
    {
        RiskContext context = new MavenRiskContext(this.graph, this.session, this.project);

        // Execute analyzers
        this.reports = new ArrayList<>();
        for (RiskAnalyzer riskAnalyzer : getAnalyzers()) {
            getLog().info("Executing analyzer " + riskAnalyzer + "");

            this.reports.add(riskAnalyzer.analyze(context));
        }

        boolean foundErrors = false;
        for (RiskReport report : this.reports) {
            foundErrors |= !report.getErrors().isEmpty();

            getLog().info(report.getAnalyzer() + ": ");
            report.getErrors().forEach(r -> getLog().error("  " + r.getFormattedMessage(), r.getThrowable()));
            report.getWarnings().forEach(r -> getLog().warn("  " + r.getFormattedMessage(), r.getThrowable()));
        }

        if (foundErrors && this.failOnRisk) {
            throw new MojoFailureException("Risk(s) have been found in the project dependencies");
        }
    }

    private void enrich() throws MojoExecutionException, URISyntaxException, IOException
    {
        if (this.httpclient == null) {
            // Offline mode
            return;
        }

        Set<MavenResolvedCallGraph> dependencies = enrichStitchedCallables();
        enrichDependencies(dependencies);
    }

    private void enrichDependencies(Set<MavenResolvedCallGraph> dependencies) throws MojoExecutionException, IOException
    {
        for (MavenResolvedCallGraph dependency : dependencies) {
            getLog().info("Requesting meta data for dependency " + dependency.getArtifact());

            JSONObject responseData = getMetadataPackage(dependency);

            if (responseData != null) {
                Set<String> metadataNames = getPackageMetadataNames();

                JSONObject metadata = (JSONObject) responseData.get("metadata");
                if (metadata != null) {
                    for (Map.Entry<String, Object> entry : metadata.toMap().entrySet()) {
                        if (metadataNames.contains(entry.getKey())) {
                            dependency.getMetadata().put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }

            // Resolve extra information
            if (getMavenExtras().contains("licenses")) {
                MavenProject artifactProject = getMavenProject(dependency.getArtifact());

                dependency.setLicenses(artifactProject.getLicenses());
            }
        }
    }

    public MavenProject getMavenProject(Artifact artifact) throws MojoExecutionException
    {
        try {
            ProjectBuildingRequest request = new DefaultProjectBuildingRequest(this.session.getProjectBuildingRequest())
                // We don't want to execute any plugin here
                .setProcessPlugins(false)
                // The local repository
                .setLocalRepository(this.localRepository)
                // It's not this plugin job to validate this pom.xml
                .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
                // Use the repositories configured for the built project instead of the default Maven ones
                .setRemoteRepositories(this.session.getCurrentProject().getRemoteArtifactRepositories());
            // Note: build() will automatically get the POM artifact corresponding to the passed artifact.
            ProjectBuildingResult result = this.projectBuilder.build(artifact, request);
            return result.getProject();
        } catch (ProjectBuildingException e) {
            throw new MojoExecutionException(String.format("Failed to build project for [%s]", artifact), e);
        }
    }

    private Set<MavenResolvedCallGraph> enrichStitchedCallables()
        throws MojoExecutionException, URISyntaxException, IOException
    {
        Set<MavenResolvedCallGraph> dependencies = new HashSet<>();

        List<StitchedGraphNode> nodes = this.graph.getStitchedNodes();
        getLog().info("Enriching stitched call graph with " + nodes.size() + " callable nodes.");
        Map<String, StitchedGraphNode> map = new HashMap<>();
        JSONArray json = new JSONArray();
        for (StitchedGraphNode node : nodes) {
            if (node.getPackageRCG().isRemote() && node.getScope() == JavaScope.internalTypes) {
                String fullURI = node.getFullURI();
                json.put(fullURI);
                map.put(fullURI, node);

                // Remember the dependency package
                dependencies.add(node.getPackageRCG());
            }
        }
        getLog().info("Requesting meta data for " + map.keySet().size() + " callable nodes.");

        if (!map.isEmpty()) {
            JSONObject responseData = getMetadataCallable(json);

            if (responseData != null) {
                getLog().info("Received meta data for " + responseData.keySet().size() + " callable nodes.");

                for (String uri : responseData.keySet()) {
                    StitchedGraphNode node = map.get(uri);

                    JSONObject metadata = (JSONObject) responseData.get(uri);

                    node.getLocalNode().getMetadata().putAll(metadata.toMap());
                }
            }
        }

        return dependencies;
    }

    private JSONObject getMetadataPackage(MavenResolvedCallGraph dependency) throws IOException
    {
        // Get the list of metadata to retrieve
        HttpGet httpGet = createMetadataPackageRequest(dependency);
        try (CloseableHttpResponse response = this.httpclient.execute(httpGet)) {
            if (response.getCode() == 200) {
                return new JSONObject(new JSONTokener(response.getEntity().getContent()));
            } else if (response.getCode() == 404) {
                getLog().warn("Package " + dependency.getArtifact() + " is not available on " + this.fastenApiUrl);
            } else {
                getLog().warn("Unexpected code when resolving metadata for dependency " + dependency.getArtifact()
                    + " on " + this.fastenApiUrl + ": " + response.getCode());
            }
        }

        return null;
    }

    private JSONObject getMetadataCallable(JSONArray input)
        throws MojoExecutionException, URISyntaxException, IOException
    {
        // Get the list of metadata to retrieve
        HttpPost httpPost = createMetadataCallableRequest(input);
        try (CloseableHttpResponse response = this.httpclient.execute(httpPost)) {
            if (response.getCode() == 200) {
                return new JSONObject(new JSONTokener(response.getEntity().getContent()));
            } else {
                getLog().warn("Unexpected code when resolving callables metadata: " + response.getCode());

                return null;
            }
        }
    }

    private Set<String> getPackageMetadataNames() throws MojoExecutionException
    {
        if (this.packageMetadataNames == null) {
            this.packageMetadataNames = new HashSet<>();
            for (RiskAnalyzer analyzers : getAnalyzers()) {
                this.packageMetadataNames.addAll(analyzers.getPackageMetadatas());
            }

            return this.packageMetadataNames;
        }

        return this.packageMetadataNames;
    }

    private Set<String> getMavenExtras() throws MojoExecutionException
    {
        if (this.mavenExtras == null) {
            this.mavenExtras = new HashSet<>();
            for (RiskAnalyzer analyzers : getAnalyzers()) {
                this.mavenExtras.addAll(analyzers.getMavenExtras());
            }
        }

        return this.mavenExtras;
    }

    private HttpGet createMetadataPackageRequest(MavenResolvedCallGraph dependency)
    {
        return new HttpGet(this.fastenApiUrl + URLEncodedUtils.formatSegments("mvn", "packages",
            dependency.getGraph().uri.getProduct(), dependency.getGraph().uri.getVersion(), "metadata"));
    }

    private HttpPost createMetadataCallableRequest(JSONArray json) throws URISyntaxException, MojoExecutionException
    {
        URIBuilder builder = new URIBuilder(this.fastenApiUrl + "/metadata/callables");
        Set<String> metadataNames = new HashSet<>();
        for (RiskAnalyzer analyzers : getAnalyzers()) {
            metadataNames.addAll(analyzers.getCallableMetadatas());
        }
        metadataNames.forEach(e -> builder.addParameter("attributes", e));

        HttpPost httpPost = new HttpPost(builder.build());

        httpPost.setEntity(new StringEntity(json.toString(), ContentType.APPLICATION_JSON));

        return httpPost;
    }

    private MavenResolvedCallGraph resolveCG(CGMerger merger, MavenExtendedRevisionJavaCallGraph cg,
        File mergeCallGraphFile) throws MojoExecutionException
    {
        getLog().info("Generating resolved call graphs and serializing it on [" + mergeCallGraphFile + "].");

        DirectedGraph mergedCG = merger.mergeWithCHA(cg);

        // TODO
        return new MavenResolvedCallGraph(cg.getArtifact(), cg.isRemote(), null);
    }

    private MavenExtendedRevisionJavaCallGraph getCallGraph(Artifact artifact) throws IOException, OPALException
    {
        if (!artifact.getFile().getName().endsWith(".jar")) {
            throw new IOException("Unsupported type ([" + artifact.getType() + "]) for artifact [" + artifact + "]");
        }

        File outputFile = toOutputFile(artifact, ".json");

        MavenExtendedRevisionJavaCallGraph callGraph = null;
        try {
            callGraph = downloadCallGraph(artifact, outputFile);
        } catch (Exception e) {
            getLog().warn("Unexpected error code when downloading the artifact call graph", e);
        }

        if (callGraph == null) {
            // Fallback on build it locally

            String productName = artifact.getGroupId() + ':' + artifact.getArtifactId();
            if (StringUtils.isNotEmpty(artifact.getClassifier())) {
                productName += artifact.getClassifier();
            }

            callGraph = buildCallGraph(artifact, outputFile, productName);
        }

        return callGraph;
    }

    private MavenExtendedRevisionJavaCallGraph downloadCallGraph(Artifact artifact, File outputFile)
        throws IOException, URISyntaxException
    {
        if (this.httpclient == null || StringUtils.isEmpty(this.fastenApiUrl)) {
            // Offline mode
            return null;
        }

        URIBuilder builder = new URIBuilder(this.fastenApiUrl + URLEncodedUtils.formatSegments("mvn", "packages",
            artifact.getGroupId() + ':' + artifact.getArtifactId(), artifact.getVersion(), "rcg"));

        // TODO: add qualifier and type support

        // Indicate where to find the artifact if it's not available on the FASTEN server yet
        if (artifact.getRepository() != null) {
            builder.addParameter("artifactRepository", artifact.getRepository().getUrl());
        }

        URI uri = builder.build();

        getLog().info("Downloading call graph for artifact " + artifact + " on " + uri);

        HttpGet httpGet = new HttpGet(uri);

        try (CloseableHttpResponse response = this.httpclient.execute(httpGet)) {
            if (response.getCode() == 200) {
                // Serialize the json
                FileUtils.copyInputStreamToFile(response.getEntity().getContent(), outputFile);

                // Parse the json
                try (InputStream stream = new FileInputStream(outputFile)) {
                    boolean remote = isRemote(artifact.getVersion(), true);

                    return new MavenExtendedRevisionJavaCallGraph(artifact, stream, remote);
                }
            } else if (response.getCode() == 201 || response.getCode() == 202) {
                getLog().warn("The artifact is not available yet on the server but an analysis was requested");
            } else {
                getLog().warn("Unexpected error code when downloading the artifact call graph: " + response.getCode());
            }
        }

        return null;
    }

    private MavenExtendedRevisionJavaCallGraph buildCallGraph(Artifact artifact, File outputFile, String product)
        throws OPALException
    {
        return buildCallGraph(artifact, artifact.getFile(), outputFile, product);
    }

    private boolean isRemote(String version, boolean auto)
    {
        boolean remote;
        if (this.metadataDownload == MetadataDownload.all) {
            remote = true;
        } else if (this.metadataDownload == MetadataDownload.releases) {
            remote = !version.endsWith("-SNAPSHOT");
        } else {
            remote = auto;
        }

        return remote;
    }

    private MavenExtendedRevisionJavaCallGraph buildCallGraph(Artifact artifact, File file, File outputFile,
        String product) throws OPALException
    {
        PartialCallGraph input = new PartialCallGraph(new CallGraphConstructor(file, null, this.genAlgorithm));

        boolean remote = isRemote(artifact.getVersion(), false);

        return new MavenExtendedRevisionJavaCallGraph(artifact,
            ExtendedRevisionJavaCallGraph.extendedBuilder().graph(input.getGraph()).product(product)
                .version(artifact.getVersion()).timestamp(0).cgGenerator("").forge("")
                .classHierarchy(input.getClassHierarchy()).nodeCount(input.getNodeCount()),
            remote);
    }
}
