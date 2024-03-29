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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import org.apache.commons.collections4.SetUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import eu.fasten.core.data.JavaScope;
import eu.fasten.maven.analyzer.RiskAnalyzerConfiguration;
import eu.fasten.maven.analyzer.RiskReport;
import eu.fasten.maven.analyzer.RiskReport.Message;
import eu.fasten.maven.analyzer.SecurityRiskAnalyzer;

/**
 * Unit tests for {@link CheckMojo}.
 * 
 * @version $Id$
 */
class CheckMojoTest
{
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    private static final File A_CLASSFILE = new File("target/test-classes/eu/fasten/maven/a/A.class");

    private static final File B_CLASSFILE = new File("target/test-classes/eu/fasten/maven/b/B.class");

    private static final File C_CLASSFILE = new File("target/test-classes/eu/fasten/maven/c/C.class");

    private static final File BC_CLASSFILE = new File("target/test-classes/eu/fasten/maven/bc/BC.class");

    private CheckMojo mojo = new CheckMojo();

    private Log log = mock(Log.class);

    private File testWorkDir;

    private File projectWorkDir;

    private File projectArtifactFile;

    private MavenProject project = new MavenProject();

    private MavenSession session = mock(MavenSession.class);

    private <T> void assertEqualSet(Set<T> set1, Set<T> set2)
    {
        List<T> list1 = new ArrayList<>(set1);
        List<T> list2 = new ArrayList<>(set2);

        list1.sort(null);
        list2.sort(null);

        assertEquals(list1, list2);
    }

    @BeforeEach
    void beforeEach() throws IllegalAccessException
    {
        this.testWorkDir = new File("target/test-" + new Date().getTime()).getAbsoluteFile();
        this.testWorkDir.mkdirs();

        this.projectArtifactFile = new File(this.testWorkDir, "project.jar");

        this.mojo.setLog(this.log);

        Model model = new Model();
        model.setGroupId("pgroupid");
        model.setArtifactId("partifactid");
        model.setVersion("1.0-SNAPSHOT");
        this.project.setModel(model);
        FieldUtils.writeField(this.mojo, "project", this.project, true);

        this.project.setArtifact(artifact("pgroupid", "partifactid", "1.0-SNAPSHOT", this.projectArtifactFile, null));

        when(this.session.isOffline()).thenReturn(false);
        FieldUtils.writeField(this.mojo, "session", this.session, true);

        this.projectWorkDir = new File(this.testWorkDir, "PROJECT/");
        FieldUtils.writeField(this.mojo, "outputDirectory", new File(this.projectWorkDir, "target/call-graphs/"), true);
    }

    private void jar(File file, File... classFiles) throws FileNotFoundException, IOException
    {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            try (JarOutputStream stream = new JarOutputStream(fos, manifest)) {
                for (File classFile : classFiles) {
                    ZipEntry entry = new ZipEntry(classFile.getName());
                    stream.putNextEntry(entry);
                    FileUtils.copyFile(classFile, stream);
                }
            }
        }
    }

    private Artifact artifact(String groupId, String artifactId, String version, File file, String repositoryURL)
    {
        DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, version, null, "jar", "", null);
        artifact.setFile(file);

        if (repositoryURL != null) {
            ArtifactRepository repository = mock(ArtifactRepository.class);
            when(repository.getUrl()).thenReturn(repositoryURL);

            artifact.setRepository(repository);
        }

        return artifact;
    }

    @Test
    void testStitching() throws MojoExecutionException, MojoFailureException, IOException
    {
        jar(this.projectArtifactFile, A_CLASSFILE);

        File dependencyBDir = new File(this.testWorkDir, "B.jar");
        File dependencyCDir = new File(this.testWorkDir, "C.jar");
        jar(dependencyBDir, B_CLASSFILE, BC_CLASSFILE);
        jar(dependencyCDir, C_CLASSFILE, BC_CLASSFILE);

        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("b", "b", "1.0", dependencyBDir, null));
        artifacts.add(artifact("c", "c", "1.0", dependencyCDir, null));
        this.project.setArtifacts(artifacts);

        this.mojo.execute();

        List<MavenGraphNode> nodes = this.mojo.graph.getOptimizedNodes();

        // All stitched nodes
        assertEqualSet(SetUtils.hashSet(
            "fasten://mvn!pgroupid:partifactid$1.0-SNAPSHOT/eu.fasten.maven.a/A.%3Cinit%3E()%2Fjava.lang%2FVoidType",
            "fasten://mvn!pgroupid:partifactid$1.0-SNAPSHOT/eu.fasten.maven.a/A.m1()%2Fjava.lang%2FVoidType",
            "fasten://mvn!pgroupid:partifactid$1.0-SNAPSHOT/eu.fasten.maven.a/A.m2()%2Fjava.lang%2FVoidType",
            "fasten://mvn!b:b$1.0/eu.fasten.maven.b/B.mB1()%2Fjava.lang%2FVoidType",
            "fasten://mvn!b:b$1.0/eu.fasten.maven.b/B.mBi()%2Fjava.lang%2FVoidType",
            "fasten://mvn!b:b$1.0/eu.fasten.maven.bc/BC.mBC()%2Fjava.lang%2FVoidType",
            "fasten://mvn!c:c$1.0/eu.fasten.maven.c/C.mC1()%2Fjava.lang%2FVoidType",
            "fasten://mvn!c:c$1.0/eu.fasten.maven.bc/BC.mBC()%2Fjava.lang%2FVoidType",
            "/eu.fasten.maven.missing/Missing.mMissing()%2Fjava.lang%2FVoidType",
            "/java.lang/Object.%3Cinit%3E()VoidType"),
            nodes.stream().map(node -> node.getFullURI()).collect(Collectors.toSet()));

        // Resolved node URIs
        assertEqualSet(SetUtils.hashSet(
            "fasten://mvn!pgroupid:partifactid$1.0-SNAPSHOT/eu.fasten.maven.a/A.%3Cinit%3E()%2Fjava.lang%2FVoidType",
            "fasten://mvn!pgroupid:partifactid$1.0-SNAPSHOT/eu.fasten.maven.a/A.m1()%2Fjava.lang%2FVoidType",
            "fasten://mvn!pgroupid:partifactid$1.0-SNAPSHOT/eu.fasten.maven.a/A.m2()%2Fjava.lang%2FVoidType",
            "fasten://mvn!b:b$1.0/eu.fasten.maven.b/B.mB1()%2Fjava.lang%2FVoidType",
            "fasten://mvn!b:b$1.0/eu.fasten.maven.b/B.mBi()%2Fjava.lang%2FVoidType",
            "fasten://mvn!b:b$1.0/eu.fasten.maven.bc/BC.mBC()%2Fjava.lang%2FVoidType",
            "fasten://mvn!c:c$1.0/eu.fasten.maven.c/C.mC1()%2Fjava.lang%2FVoidType",
            "fasten://mvn!c:c$1.0/eu.fasten.maven.bc/BC.mBC()%2Fjava.lang%2FVoidType"),
            nodes.stream().filter(node -> node.getScope() != JavaScope.externalTypes).map(node -> node.getFullURI())
                .collect(Collectors.toSet()));
    }

    @Test
    // TODO: refactor the test based on Wiremock
    @Disabled
    void testMetadata() throws MojoExecutionException, MojoFailureException, IOException
    {
        jar(this.projectArtifactFile, new File("target/test-classes/eu/fasten/maven/metadata/ProjectClass.class"));

        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("org.ow2.asm", "asm", "7.0", new File("asm.jar"), MAVEN_CENTRAL));
        this.project.setArtifacts(artifacts);

        this.mojo.execute();

        List<MavenGraphNode> nodes = this.mojo.graph.getOptimizedNodes();

        // Resolved node URIs
        assertEqualSet(SetUtils.hashSet(
            "fasten://mvn!pgroupid:partifactid$1.0-SNAPSHOT/eu.fasten.maven.metadata/ProjectClass.%3Cinit%3E()%2Fjava.lang%2FVoidType",
            "fasten://mvn!pgroupid:partifactid$1.0-SNAPSHOT/eu.fasten.maven.metadata/ProjectClass.m()%2Fjava.lang%2FVoidType",
            "fasten://mvn!org.ow2.asm:asm$7.0/org.objectweb.asm/Label.%3Cinit%3E()%2Fjava.lang%2FVoidType"),
            nodes.stream().filter(node -> node.getScope() != JavaScope.externalTypes).map(node -> node.getFullURI())
                .collect(Collectors.toSet()));
    }

    @Test
    // TODO: refactor the test based on Wiremock
    @Disabled
    void testSecurity() throws IOException, IllegalAccessException
    {
        jar(this.projectArtifactFile, new File("target/test-classes/eu/fasten/maven/security/ProjectClass.class"));

        Set<Artifact> artifacts = new LinkedHashSet<>();

        artifacts.add(artifact("com.google.guava", "guava", "24.1.1-jre",
            new File(
                FileUtils.getUserDirectoryPath() + "/.m2/repository/com/google/guava/24.1.1-jre/guava-24.1.1-jre.jar"),
            MAVEN_CENTRAL));
        this.project.setArtifacts(artifacts);

        RiskAnalyzerConfiguration configuration = new RiskAnalyzerConfiguration();
        configuration.setType("fasten.security");
        FieldUtils.writeField(this.mojo, "risks", Arrays.asList(configuration), true);

        assertThrows(MojoFailureException.class, () -> this.mojo.execute());

        List<RiskReport> reports = this.mojo.reports;

        assertEquals(1, reports.size());

        RiskReport report = reports.get(0);

        assertSame(SecurityRiskAnalyzer.class, report.getAnalyzer().getClass());

        assertEquals(0, report.getWarnings().size());

        List<Message> errors = report.getErrors();

        assertEquals(1, errors.size());

        assertEquals(
            "The vulnerability CVE-2020-8908 affects dependency com.google.guava:guava:jar:24.1.1-jre",
            errors.get(0).getFormattedMessage());
    }

    @Test
    void testQuality() throws IOException, IllegalAccessException
    {
        // TODO
    }

    @Test
    void testBinary() throws MojoExecutionException, MojoFailureException, IOException, IllegalAccessException
    {
        jar(this.projectArtifactFile, A_CLASSFILE);

        File dependencyBDir = new File(this.testWorkDir, "B.jar");
        File dependencyCDir = new File(this.testWorkDir, "C.jar");
        jar(dependencyBDir, B_CLASSFILE, BC_CLASSFILE);
        jar(dependencyCDir, C_CLASSFILE, BC_CLASSFILE);

        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("b", "b", "1.0", dependencyBDir, null));
        artifacts.add(artifact("c", "c", "1.0", dependencyCDir, null));
        this.project.setArtifacts(artifacts);

        RiskAnalyzerConfiguration configuration = new RiskAnalyzerConfiguration();
        configuration.setType("fasten.binary");
        FieldUtils.writeField(this.mojo, "risks", Arrays.asList(configuration), true);

        assertThrows(MojoFailureException.class, () -> this.mojo.execute());

        assertEquals(1, this.mojo.reports.size());

        // Warnings
        assertTrue(this.mojo.reports.get(0).getWarnings().isEmpty());

        // Errors
        assertEqualSet(SetUtils.hashSet("eu.fasten.maven.missing.Missing.mMissing()%2Fjava.lang%2FVoidType"),
            this.mojo.reports.get(0).getErrors().stream().map(m -> m.getArgArray()[0]).collect(Collectors.toSet()));

        configuration.setIgnoredCallables(Arrays.asList("eu.fasten.maven.missing.Missing.mMissing.*"));

        this.mojo.execute();

        assertEquals(1, this.mojo.reports.size());

        // Warnings
        assertTrue(this.mojo.reports.get(0).getWarnings().isEmpty());

        // Errors
        assertTrue(this.mojo.reports.get(0).getErrors().isEmpty());
    }
}
