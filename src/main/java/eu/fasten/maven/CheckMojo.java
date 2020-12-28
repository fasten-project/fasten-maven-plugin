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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import com.google.common.collect.BiMap;

import eu.fasten.analyzer.javacgopal.data.CallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.analyzer.javacgopal.data.exceptions.OPALException;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.merge.LocalMerger;

/**
 * Build a call graph of the module and its dependencies.
 *
 * @version $Id: 982ced7f89e6c39126d28b2f9e5fcac365250288 $
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, requiresDependencyResolution = ResolutionScope.RUNTIME, requiresProject = true, threadSafe = true)
public class CheckMojo extends AbstractMojo
{
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "target/call-graphs/")
    private File outputDirectory;

    @Parameter(defaultValue = "CHA")
    private String genAlgorithm;

    BiMap<Long, String> uirs;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        // Only JAR packages are supported right now
        if (!this.project.getPackaging().equals("jar")) {
            getLog().warn("Only project with packaging JAR are supported. Skipping.");

            return;
        }

        getLog().info("Generating local call graph of the project.");

        // Build project call graph
        File projectCallGraphFile = new File(this.outputDirectory, "project.json");
        ExtendedRevisionJavaCallGraph projectCG;
        try {
            projectCG =
                buildCallGraph(new File(this.project.getBuild().getOutputDirectory()), projectCallGraphFile, "project");
        } catch (OPALException e) {
            throw new MojoExecutionException(
                "Failed to build a call graph for directory [" + this.project.getBuild().getOutputDirectory() + "]", e);
        }

        // Build/Get dependencies call graphs
        List<MavenExtendedRevisionJavaCallGraph> dependencies = new ArrayList<>(this.project.getArtifacts().size());
        List<ExtendedRevisionJavaCallGraph> all = new ArrayList<>(this.project.getArtifacts().size() + 1);
        all.add(projectCG);
        for (Artifact artifact : this.project.getArtifacts()) {
            getLog().info("Generating call graphs for dependency [" + artifact + "].");
            try {
                MavenExtendedRevisionJavaCallGraph graph = getCallGraph(artifact);
                dependencies.add(graph);
                all.add(graph);
            } catch (Exception e) {
                getLog().warn("Failed to generate a call graph for artifact [" + artifact + "]: "
                    + ExceptionUtils.getRootCauseMessage(e) + "");
            }
        }

        // Produce resolved call graphs
        getLog().info("Produce resolved call graphs.");

        LocalMerger merger = new LocalMerger((List) dependencies);

        // Produce the resolved graph for the project
        writeLocalMerge(merger, projectCG, new File(outputDirectory, "project.resolved.json"));

        // Produce the resolved graph for each dependency
        for (MavenExtendedRevisionJavaCallGraph dependencyCG : dependencies) {
            File outputFile =
                new File(dependencyCG.getGraphFile().getParentFile(), dependencyCG.getGraphFile().getName().substring(0,
                    dependencyCG.getGraphFile().getName().length() - ".json".length()) + ".resolved.json");

            writeLocalMerge(merger, dependencyCG, outputFile);
        }

        // Produce stitched call grahs
        getLog().info("Produce stitched call grahs.");

        LocalMerger sticher = new LocalMerger(all);
        DirectedGraph directedGraph = sticher.mergeAllDeps();
        this.uirs = sticher.getAllUris();
    }

    private void writeLocalMerge(LocalMerger merger, ExtendedRevisionJavaCallGraph cg, File mergeCallGraphFile)
        throws MojoExecutionException
    {
        getLog().info("Generating resolved call graphs and serializing it on [" + mergeCallGraphFile + "].");

        ExtendedRevisionJavaCallGraph mergedCG = merger.mergeWithCHA(cg);

        try {
            FileUtils.write(mergeCallGraphFile, mergedCG.toJSON().toString(4), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to serialize the merged call graph", e);
        }
    }

    private MavenExtendedRevisionJavaCallGraph getCallGraph(Artifact artifact) throws IOException, OPALException
    {
        if (!artifact.getFile().getName().endsWith(".jar")) {
            throw new IOException("Unsupported type ([" + artifact.getType() + "]) for artifact [" + artifact + "]");
        }

        File outputFile = new File(this.outputDirectory,
            artifact.getGroupId() + '/' + artifact.getArtifactId() + '/' + artifact.getFile().getName() + ".json");

        // TODO: Try to find it on the FASTEN server first

        // Fallback on build it locally

        String productName = artifact.toString();

        return buildCallGraph(artifact.getFile(), outputFile, productName);
    }

    private MavenExtendedRevisionJavaCallGraph buildCallGraph(File file, File outputFile, String product)
        throws OPALException
    {
        PartialCallGraph input = new PartialCallGraph(new CallGraphConstructor(file, null, this.genAlgorithm));

        MavenExtendedRevisionJavaCallGraph cg = new MavenExtendedRevisionJavaCallGraph(outputFile,
            ExtendedRevisionJavaCallGraph.extendedBuilder().graph(input.getGraph()).product(product).version("")
                .timestamp(0).cgGenerator("").forge("").classHierarchy(input.getClassHierarchy())
                .nodeCount(input.getNodeCount()));

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
