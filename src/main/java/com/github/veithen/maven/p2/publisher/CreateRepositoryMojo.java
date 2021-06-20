/*-
 * #%L
 * p2-publisher-maven-plugin
 * %%
 * Copyright (C) 2018 - 2021 Andreas Veithen
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.veithen.maven.p2.publisher;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Repository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.p2.core.IProvisioningAgent;
import org.eclipse.equinox.p2.core.IProvisioningAgentProvider;
import org.eclipse.equinox.p2.publisher.IPublisherAction;
import org.eclipse.equinox.p2.publisher.IPublisherInfo;
import org.eclipse.equinox.p2.publisher.Publisher;
import org.eclipse.equinox.p2.publisher.PublisherInfo;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.IArtifactRepositoryManager;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepository;
import org.eclipse.equinox.p2.repository.metadata.IMetadataRepositoryManager;

import com.github.veithen.cosmos.osgi.runtime.CosmosRuntime;
import com.github.veithen.maven.shared.artifactset.ArtifactSet;
import com.github.veithen.maven.shared.artifactset.ArtifactSetResolver;

@Mojo(name = "create-repository", requiresDependencyResolution = ResolutionScope.TEST)
public class CreateRepositoryMojo extends AbstractMojo {
    @Parameter(property = "project", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "session", readonly = true, required = true)
    private MavenSession session;

    @Parameter(required = true)
    private ArtifactSet artifactSet;

    @Parameter private Repository[] repositories;

    @Parameter(defaultValue = "${project.build.directory}/p2-repository", required = true)
    private File outputDirectory;

    @Parameter(defaultValue = "${project.build.directory}/p2-agent", required = true)
    private File agentLocation;

    @Parameter private File[] bundles;

    @Parameter(defaultValue = "false")
    private boolean skip;

    @Component private ArtifactSetResolver artifactSetResolver;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();

        if (skip) {
            log.info("Skipping plugin execution");
        }

        try {
            List<Artifact> artifacts =
                    artifactSetResolver.resolveArtifactSet(
                            project, session, artifactSet, repositories);
            URI repoURI = outputDirectory.toURI();
            IProvisioningAgent agent =
                    CosmosRuntime.getInstance()
                            .getService(IProvisioningAgentProvider.class)
                            .createAgent(agentLocation.toURI());
            try {
                IArtifactRepositoryManager artifactRepositoryManager =
                        (IArtifactRepositoryManager)
                                agent.getService(IArtifactRepositoryManager.SERVICE_NAME);
                IMetadataRepositoryManager metadataRepositoryManager =
                        (IMetadataRepositoryManager)
                                agent.getService(IMetadataRepositoryManager.SERVICE_NAME);
                IArtifactRepository artifactRepository =
                        artifactRepositoryManager.createRepository(
                                repoURI,
                                "Artifact Repository",
                                IArtifactRepositoryManager.TYPE_SIMPLE_REPOSITORY,
                                Collections.<String, String>emptyMap());
                IMetadataRepository metadataRepository =
                        metadataRepositoryManager.createRepository(
                                repoURI,
                                "Metadata Repository",
                                IMetadataRepositoryManager.TYPE_SIMPLE_REPOSITORY,
                                Collections.<String, String>emptyMap());
                PublisherInfo publisherInfo = new PublisherInfo();
                publisherInfo.setArtifactRepository(artifactRepository);
                publisherInfo.setMetadataRepository(metadataRepository);
                publisherInfo.setArtifactOptions(IPublisherInfo.A_PUBLISH | IPublisherInfo.A_INDEX);
                Publisher publisher = new Publisher(publisherInfo);
                List<File> locations = new ArrayList<>();
                for (Artifact artifact : artifacts) {
                    locations.add(artifact.getFile());
                }
                if (bundles != null) {
                    locations.addAll(Arrays.asList(bundles));
                }
                IStatus status =
                        publisher.publish(
                                new IPublisherAction[] {
                                    new BundlesAction(locations.toArray(new File[locations.size()]))
                                },
                                null);
                if (!status.isOK()) {
                    throw new MojoExecutionException("Publishing failed: " + status.getMessage());
                }
            } finally {
                agent.stop();
            }
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        }
    }
}
