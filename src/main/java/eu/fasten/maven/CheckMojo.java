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
import org.json.JSONException;

import eu.fasten.analyzer.javacgopal.core.RevisionCallGraph;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.analyzer.javacgopal.merge.CallGraphMerger;

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
    private String mergeAlgorithm;

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
        RevisionCallGraph projectCG =
            buildCallGraph(new File(this.project.getBuild().getOutputDirectory()), projectCallGraphFile, "project");

        // Build/Get dependencies call graphs
        List<RevisionCallGraph> dependencies = new ArrayList<>();
        for (Artifact artifact : this.project.getArtifacts()) {
            getLog().info("Generating call graphs for dependency [" + artifact + "].");
            try {
                dependencies.add(getCallGraph(artifact));
            } catch (Exception e) {
                getLog().warn("Failed to generate a call graph for artifact [" + artifact + "]: "
                    + ExceptionUtils.getRootCauseMessage(e) + "");
            }
        }

        // Merge call graphs
        getLog().info("Merging call graphs.");
        File mergeCallGraphFile = new File(outputDirectory, "merge.json");
        RevisionCallGraph mergeCG = CallGraphMerger.mergeCallGraph(projectCG, dependencies, this.mergeAlgorithm);

        try {
            FileUtils.write(mergeCallGraphFile, mergeCG.toJSON().toString(4), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to serialize the merged call graph", e);
        }
    }

    private RevisionCallGraph getCallGraph(Artifact artifact) throws JSONException, IOException
    {
        if (!artifact.getFile().getName().endsWith(".jar")) {
            throw new IOException("Unsupported type ([" + artifact.getType() + "]) for artifact [" + artifact + "]");
        }

        File outputFile = new File(this.outputDirectory,
            artifact.getGroupId() + '/' + artifact.getArtifactId() + '/' + artifact.getFile().getName() + ".json");

        // TODO: Try to find it on the FASTEN server

        // Build it locally

        return buildCallGraph(artifact.getFile(), outputFile, artifact.toString());
    }

    private RevisionCallGraph buildCallGraph(File file, File outputFile, String product) throws JSONException
    {
        PartialCallGraph cg = new PartialCallGraph(file);
        RevisionCallGraph revisionCallGraph =
            RevisionCallGraph.extendedBuilder().graph(cg.getGraph()).product(product).version("").timestamp(0)
                .cgGenerator("").depset(new ArrayList<>()).forge("").classHierarchy(cg.getClassHierarchy()).build();

        // Remember the call graph in a file

        // Make sure the parent folder exist
        outputFile.getParentFile().mkdirs();

        try {
            FileUtils.write(outputFile, revisionCallGraph.toJSON().toString(4), StandardCharsets.UTF_8);
        } catch (Exception e) {
            getLog().warn("Failed to serialize the call graph for artifact [" + file + "]: "
                + ExceptionUtils.getRootCauseMessage(e));
        }

        return revisionCallGraph;
    }
}
