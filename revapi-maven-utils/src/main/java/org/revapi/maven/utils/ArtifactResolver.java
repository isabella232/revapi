/*
 * Copyright 2014-2021 Lukas Krejci
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.maven.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.eclipse.aether.AbstractForwardingRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.LocalArtifactRegistration;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.LocalMetadataRegistration;
import org.eclipse.aether.repository.LocalMetadataRequest;
import org.eclipse.aether.repository.LocalMetadataResult;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.traverser.AndDependencyTraverser;
import org.eclipse.aether.util.graph.traverser.FatArtifactTraverser;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.eclipse.aether.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 * 
 * @since 0.3.0
 */
public class ArtifactResolver {
    private static final Logger LOG = LoggerFactory.getLogger(ArtifactResolver.class);

    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;
    private final List<RemoteRepository> repositories;

    /**
     * Constructs a dependency selector to be used on a repository session that will cause the maven resolution to
     * return artifacts using the rules fit for Revapi API checking.
     *
     * @param resolveProvidedDependencies
     *            if true, the provided dependencies of the top-level artifacts are considered when resolving the API
     *            (this should generally be true)
     * @param resolveTransitiveProvidedDependencies
     *            if true, the transitive provided dependencies are considered when resolving the API (this should
     *            generally be false)
     * 
     * @return a dependency selector for Maven's repository session
     */
    public static DependencySelector getRevapiDependencySelector(boolean resolveProvidedDependencies,
            boolean resolveTransitiveProvidedDependencies) {
        String[] topLevelScopes = resolveProvidedDependencies ? new String[] { "compile", "provided" }
                : new String[] { "compile" };
        String[] transitiveScopes = resolveTransitiveProvidedDependencies ? new String[] { "compile", "provided" }
                : new String[] { "compile" };

        // this is how the dependency selector is initiated in the DefaultRepositorySystemSession of Maven - the only
        // thing we're changing is that we're supplying a different implementation of the scope dependency selector
        // to handle our scope dependency requirements.
        return new AndDependencySelector(new ScopeDependencySelector(topLevelScopes, transitiveScopes),
                new OptionalDependencySelector(), new ExclusionDependencySelector());
    }

    /**
     * Constructs a dependency traverser to be used on a repository session that will cause the maven resolution to
     * return artifacts using the rules fit for Revapi API checking.
     *
     * @param resolveProvidedDependencies
     *            if true, the provided dependencies of the top-level artifacts are considered when resolving the API
     *            (this should generally be true)
     * @param resolveTransitiveProvidedDependencies
     *            if true, the transitive provided dependencies are considered when resolving the API (this should
     *            generally be false)
     * 
     * @return a dependency selector for Maven's repository session
     */
    public static DependencyTraverser getRevapiDependencyTraverser(boolean resolveProvidedDependencies,
            boolean resolveTransitiveProvidedDependencies) {
        String[] topLevelScopes = resolveProvidedDependencies ? new String[] { "compile", "provided" }
                : new String[] { "compile" };
        String[] transitiveScopes = resolveTransitiveProvidedDependencies ? new String[] { "compile", "provided" }
                : new String[] { "compile" };
        return new AndDependencyTraverser(new ScopeDependencyTraverser(topLevelScopes, transitiveScopes),
                new FatArtifactTraverser());
    }

    public ArtifactResolver(RepositorySystem repositorySystem, RepositorySystemSession session,
            List<RemoteRepository> repositories) {
        this.repositorySystem = repositorySystem;
        this.session = session;
        this.repositories = repositories;
    }

    public Artifact resolveArtifact(String gav) throws ArtifactResolutionException {
        return resolveArtifact(new DefaultArtifact(gav), session);
    }

    /**
     * Tries to find the newest version of the artifact that matches given regular expression. The found version will be
     * older than the {@code upToVersion} or newest available if {@code upToVersion} is null.
     *
     * @param gav
     *            the coordinates of the artifact. The version part is ignored
     * @param upToVersion
     *            the version up to which the versions will be matched
     * @param versionMatcher
     *            the matcher to match the version
     * @param remoteOnly
     *            true if only remotely available artifacts should be considered
     * @param upToInclusive
     *            whether the {@code upToVersion} should be considered inclusive or exclusive
     * 
     * @return the resolved artifact
     */
    public Artifact resolveNewestMatching(String gav, @Nullable String upToVersion, Pattern versionMatcher,
            boolean remoteOnly, boolean upToInclusive)
            throws VersionRangeResolutionException, ArtifactResolutionException {

        Artifact artifact = new DefaultArtifact(gav);
        artifact = artifact.setVersion(upToVersion == null ? "[,)" : "[," + upToVersion + (upToInclusive ? "]" : ")"));
        VersionRangeRequest rangeRequest = new VersionRangeRequest(artifact, repositories, null);

        RepositorySystemSession session = remoteOnly ? makeRemoteOnly(this.session) : this.session;

        VersionRangeResult result = repositorySystem.resolveVersionRange(session, rangeRequest);

        List<Version> versions = new ArrayList<>(result.getVersions());
        Collections.reverse(versions);

        for (Version v : versions) {
            if (versionMatcher.matcher(v.toString()).matches()) {
                return resolveArtifact(artifact.setVersion(v.toString()), session);
            }
        }

        throw new VersionRangeResolutionException(result) {
            @Override
            public String getMessage() {
                return "Failed to find a version of artifact '" + gav + "' that would correspond to an expression '"
                        + versionMatcher + "'. The versions found were: " + versions;
            }
        };
    }

    public CollectionResult collectTransitiveDeps(String... gavs) throws RepositoryException {

        Set<Artifact> artifacts = new HashSet<>();
        Set<Exception> failures = new HashSet<>();

        for (String gav : gavs) {
            LOG.debug("Artifact resolution for {}", gav);
            collectTransitiveDeps(gav, artifacts, failures);
        }

        return new CollectionResult(failures, artifacts);
    }

    protected void collectTransitiveDeps(String gav, Set<Artifact> resolvedArtifacts, Set<Exception> failures)
            throws RepositoryException {

        final Artifact rootArtifact = resolveArtifact(gav);

        CollectRequest collectRequest = new CollectRequest(new Dependency(rootArtifact, null), repositories);

        DependencyRequest request = new DependencyRequest(collectRequest, null);

        DependencyResult result;

        try {
            result = repositorySystem.resolveDependencies(session, request);
        } catch (DependencyResolutionException dre) {
            result = dre.getResult();
        }

        if (result.getRoot() == null) {
            failures.addAll(result.getCollectExceptions());
            return;
        }

        result.getRoot().accept(new TreeDependencyVisitor(new DependencyVisitor() {
            int depth = 0;

            @Override
            public boolean visitEnter(DependencyNode node) {
                if (LOG.isDebugEnabled()) {
                    StringBuilder msg = new StringBuilder();
                    for (int i = 0; i < depth; ++i) {
                        msg.append("    ");
                    }
                    msg.append(node);
                    LOG.debug(msg.toString());
                }

                depth++;
                return true;
            }

            @Override
            public boolean visitLeave(DependencyNode node) {
                depth--;

                Dependency dep = node.getDependency();
                if (dep == null || dep.getArtifact().equals(rootArtifact)) {
                    return true;
                }

                resolvedArtifacts.add(dep.getArtifact());

                return true;
            }
        }));

        failures.addAll(result.getCollectExceptions());
    }

    public static final class CollectionResult {
        private final Set<Artifact> resolvedArtifacts;
        private final Set<Exception> failures;

        private CollectionResult(Set<Exception> failures, Set<Artifact> resolvedArtifacts) {
            this.failures = failures;
            this.resolvedArtifacts = resolvedArtifacts;
        }

        public Set<Exception> getFailures() {
            return failures;
        }

        public Set<Artifact> getResolvedArtifacts() {
            return resolvedArtifacts;
        }
    }

    private Artifact resolveArtifact(Artifact artifact, RepositorySystemSession session)
            throws ArtifactResolutionException {
        ArtifactRequest request = new ArtifactRequest().setArtifact(artifact).setRepositories(repositories);

        ArtifactResult result = repositorySystem.resolveArtifact(session, request);

        if (!result.isResolved() || result.isMissing()) {
            throw new ArtifactResolutionException(Collections.singletonList(result),
                    "The artifact was not" + " resolved or is missing: '" + artifact.toString() + "'.");
        }

        return result.getArtifact();
    }

    private RepositorySystemSession makeRemoteOnly(RepositorySystemSession session) {
        return new AbstractForwardingRepositorySystemSession() {
            @Override
            protected RepositorySystemSession getSession() {
                return session;
            }

            @Override
            public WorkspaceReader getWorkspaceReader() {
                return null;
            }

            @Override
            public LocalRepositoryManager getLocalRepositoryManager() {
                LocalRepositoryManager wrapped = session.getLocalRepositoryManager();
                return new LocalRepositoryManager() {
                    @Override
                    public LocalRepository getRepository() {
                        return wrapped.getRepository();
                    }

                    @Override
                    public String getPathForLocalArtifact(Artifact artifact) {
                        return wrapped.getPathForLocalArtifact(artifact);
                    }

                    @Override
                    public String getPathForRemoteArtifact(Artifact artifact, RemoteRepository repository,
                            String context) {
                        return wrapped.getPathForRemoteArtifact(artifact, repository, context);
                    }

                    @Override
                    public String getPathForLocalMetadata(Metadata metadata) {
                        return wrapped.getPathForLocalMetadata(metadata);
                    }

                    @Override
                    public String getPathForRemoteMetadata(Metadata metadata, RemoteRepository repository,
                            String context) {
                        return wrapped.getPathForRemoteMetadata(metadata, repository, context);
                    }

                    @Override
                    public LocalArtifactResult find(RepositorySystemSession session, LocalArtifactRequest request) {
                        return wrapped.find(session, request);
                    }

                    @Override
                    public void add(RepositorySystemSession session, LocalArtifactRegistration request) {
                        wrapped.add(session, request);
                    }

                    @Override
                    public LocalMetadataResult find(RepositorySystemSession session, LocalMetadataRequest request) {
                        if (request.getRepository() == null) {
                            // local metadata request... the returned file must not be null but may not exist
                            // we exploit that to not include the locally built results
                            LocalMetadataResult ret = new LocalMetadataResult(request);
                            ret.setFile(new File("<faked-to-force-remote-only-resolution-of-artifacts>"));
                            return ret;
                        } else {
                            return wrapped.find(session, request);
                        }
                    }

                    @Override
                    public void add(RepositorySystemSession session, LocalMetadataRegistration request) {
                        wrapped.add(session, request);
                    }
                };
            }
        };
    }
}
