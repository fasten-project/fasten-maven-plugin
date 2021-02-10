/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package eu.fasten.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import eu.fasten.analyzer.javacgopal.data.CallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.analyzer.javacgopal.data.exceptions.OPALException;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.merge.LocalMerger;

/**
 * Build a call graph of the module and its dependencies.
 *
 * @version $Id: 982ced7f89e6c39126d28b2f9e5fcac365250288 $
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.RUNTIME, requiresProject = true, threadSafe = true)
public class CheckMojo extends AbstractMojo
{
    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "target/call-graphs/")
    private File outputDirectory;

    @Parameter(defaultValue = "CHA")
    private String genAlgorithm;

    private CloseableHttpClient httpclient;

    StitchedGraph graph;

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
            projectCG = buildCallGraph(new File(this.project.getBuild().getOutputDirectory()), projectCallGraphFile,
                this.project.getGroupId() + ':' + this.project.getArtifactId(), this.project.getVersion());
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

        // Produce resolved call graphs
        getLog().info("Produce resolved call graphs.");

        LocalMerger resolver = new LocalMerger(all);

        // Produce the resolved graph for the project
        MavenResolvedCallGraph projectRCG =
            resolveCG(resolver, projectCG, new File(this.outputDirectory, "project.resolved.json"));

        // Produce the resolved graph for each dependency
        List<MavenResolvedCallGraph> dependenciesRCGs = new ArrayList<>(dependenciesCGs.size());
        for (MavenExtendedRevisionJavaCallGraph dependencyCG : dependenciesCGs) {
            File outputFile =
                new File(dependencyCG.getGraphFile().getParentFile(), dependencyCG.getGraphFile().getName().substring(0,
                    dependencyCG.getGraphFile().getName().length() - ".json".length()) + ".resolved.json");

            dependenciesRCGs.add(resolveCG(resolver, dependencyCG, outputFile));
        }

        // Produce the stitched call graph
        getLog().info("Produce stitched call graphs.");

        this.graph = new StitchedGraph(projectRCG, dependenciesRCGs);

        // Enrich the stitched call graph
        try {
            enrich();
        } catch (IOException e) {
            getLog().warn("Failed to enrich the stitched graph", e);
        }

        // TODO: Analyze the stitched call graph

    }

    private void enrich() throws IOException
    {
        if (this.httpclient == null) {
            // Offline mode
            return;
        }

        List<StitchedGraphNode> nodes = this.graph.getStichedNodes();

        Map<String, StitchedGraphNode> map = new HashMap<>();
        JSONArray json = new JSONArray();
        for (StitchedGraphNode node : nodes) {
            if (node.getPackageRCG().isRemote() && node.getScope() == JavaScope.internalTypes) {
                String fullURI = node.getFullURI();
                json.put(fullURI);
                map.put(fullURI, node);
            }
        }

        HttpPost httpPost = new HttpPost("https://api.fasten-project.eu/api/metadata/callables?allAttributes=true");

        httpPost.setEntity(new StringEntity(json.toString(), ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = this.httpclient.execute(httpPost)) {
            if (response.getCode() == 200) {
                JSONObject responseData = new JSONObject(new JSONTokener(response.getEntity().getContent()));

                for (String uri : responseData.keySet()) {
                    StitchedGraphNode node = map.get(uri);

                    JSONObject metadata = (JSONObject) responseData.get(uri);

                    node.getLocalNode().getMetadata().putAll(metadata.toMap());
                }
            } else {
                getLog().warn("Unexpected code when resolving nodes metadata: " + response.getCode());
            }
        }
    }

    private MavenResolvedCallGraph resolveCG(LocalMerger merger, MavenExtendedRevisionJavaCallGraph cg,
        File mergeCallGraphFile) throws MojoExecutionException
    {
        getLog().info("Generating resolved call graphs and serializing it on [" + mergeCallGraphFile + "].");

        ExtendedRevisionJavaCallGraph mergedCG = merger.mergeWithCHA(cg);

        try {
            FileUtils.write(mergeCallGraphFile, mergedCG.toJSON().toString(4), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to serialize the merged call graph", e);
        }

        return new MavenResolvedCallGraph(cg.isRemote(), mergedCG);
    }

    private MavenExtendedRevisionJavaCallGraph getCallGraph(Artifact artifact) throws IOException, OPALException
    {
        if (!artifact.getFile().getName().endsWith(".jar")) {
            throw new IOException("Unsupported type ([" + artifact.getType() + "]) for artifact [" + artifact + "]");
        }

        File outputFile = new File(this.outputDirectory,
            artifact.getGroupId() + '/' + artifact.getArtifactId() + '/' + artifact.getFile().getName() + ".json");

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

            callGraph = buildCallGraph(artifact.getFile(), outputFile, productName, artifact.getVersion());
        }

        return callGraph;
    }

    private MavenExtendedRevisionJavaCallGraph downloadCallGraph(Artifact artifact, File outputFile) throws IOException
    {
        if (this.httpclient == null) {
            // Offline mode
            return null;
        }

        StringBuilder builder = new StringBuilder("https://api.fasten-project.eu/mvn/");

        builder.append(artifact.getArtifactId().charAt(0));
        builder.append('/');
        builder.append(artifact.getArtifactId());
        builder.append('/');
        builder.append(artifact.getArtifactId());
        builder.append('_');
        builder.append(artifact.getGroupId());
        builder.append('_');
        builder.append(artifact.getVersion());
        builder.append(".json");

        // TODO: add qualifier and type support

        String url = builder.toString();

        getLog().info("Downloading call graph for artifact " + artifact + " on " + url);

        HttpGet httpGet = new HttpGet(url);

        try (CloseableHttpResponse response = this.httpclient.execute(httpGet)) {
            if (response.getCode() == 200) {
                // Serialize the json
                FileUtils.copyInputStreamToFile(response.getEntity().getContent(), outputFile);

                // Parse the json
                try (InputStream stream = new FileInputStream(outputFile)) {
                    return new MavenExtendedRevisionJavaCallGraph(stream, outputFile);
                }
            } else {
                getLog().warn("Unexpected code when downloading the artifact call graph: " + response.getCode());
            }
        }

        return null;
    }

    private MavenExtendedRevisionJavaCallGraph buildCallGraph(File file, File outputFile, String product,
        String version) throws OPALException
    {
        PartialCallGraph input = new PartialCallGraph(new CallGraphConstructor(file, null, this.genAlgorithm));

        MavenExtendedRevisionJavaCallGraph cg = new MavenExtendedRevisionJavaCallGraph(ExtendedRevisionJavaCallGraph
            .extendedBuilder().graph(input.getGraph()).product(product).version(version).timestamp(0).cgGenerator("")
            .forge("").classHierarchy(input.getClassHierarchy()).nodeCount(input.getNodeCount()), outputFile);

        // Remember the call graph in a file

        // Make sure the parent folder exist
        outputFile.getParentFile().mkdirs();

        try {
            FileUtils.write(outputFile, cg.toJSON().toString(4), StandardCharsets.UTF_8);
        } catch (Exception e) {
            getLog().warn("Failed to serialize the call graph for artifact [" + file + "]: "
                + ExceptionUtils.getRootCauseMessage(e));
        }

        return cg;
    }
}
