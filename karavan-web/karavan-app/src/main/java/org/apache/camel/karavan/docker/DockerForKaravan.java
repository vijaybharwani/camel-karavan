/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.karavan.docker;

import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HealthCheck;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.karavan.docker.model.DockerComposeService;
import org.apache.camel.karavan.infinispan.model.ContainerStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

import static org.apache.camel.karavan.shared.Constants.*;

@ApplicationScoped
public class DockerForKaravan {

    private static final Logger LOGGER = Logger.getLogger(DockerForKaravan.class.getName());

    @ConfigProperty(name = "karavan.devmode.image")
    String devmodeImage;

    @ConfigProperty(name = "karavan.infinispan.username")
    String infinispanUsername;
    @ConfigProperty(name = "karavan.infinispan.password")
    String infinispanPassword;

    @Inject
    DockerService dockerService;

    public void runProjectInDevMode(String projectId, String jBangOptions, Map<Integer, Integer> ports, Map<String, String> files) throws Exception {
        Container c = createDevmodeContainer(projectId, jBangOptions, ports);
        dockerService.runContainer(projectId);
        dockerService.copyFiles(c.getId(), "/code", files);
    }

    protected Container createDevmodeContainer(String projectId, String jBangOptions, Map<Integer, Integer> ports) throws InterruptedException {
        LOGGER.infof("DevMode starting for %s with JBANG_OPTIONS=%s", projectId, jBangOptions);

        HealthCheck healthCheck = new HealthCheck().withTest(List.of("CMD", "curl", "-f", "http://localhost:8080/q/dev/health"))
                .withInterval(10000000000L).withTimeout(10000000000L).withStartPeriod(10000000000L).withRetries(30);

        List<String> env = jBangOptions != null && !jBangOptions.trim().isEmpty()
                ? List.of(ENV_VAR_JBANG_OPTIONS + "=" + jBangOptions)
                : List.of();

        return dockerService.createContainer(projectId, devmodeImage,
                env, ports, healthCheck,
                Map.of(LABEL_TYPE, ContainerStatus.ContainerType.devmode.name(), LABEL_PROJECT_ID, projectId),
                Map.of());

    }

    public void runBuildProject(String projectId, String script, Map<String, String> files, List<String> env) throws Exception {
        Container c = createBuildContainer(projectId, env);
        dockerService.copyFiles(c.getId(), "/code", files);
        dockerService.copyExecFile(c.getId(), "/karavan", "build.sh", script);
        dockerService.runContainer(projectId);
    }

    protected Container createBuildContainer(String projectId, List<String> env) throws InterruptedException {
        LOGGER.infof("Starting Build Container for %s ", projectId);

        return dockerService.createContainer(projectId + BUILDER_SUFFIX, devmodeImage,
                env, Map.of(), new HealthCheck(),
                Map.of(LABEL_TYPE, ContainerStatus.ContainerType.build.name(), LABEL_PROJECT_ID, projectId),
                Map.of(), "/karavan/build.sh");
    }

    public void createDevserviceContainer(DockerComposeService dockerComposeService) throws InterruptedException {
        LOGGER.infof("DevService starting for ", dockerComposeService.getContainer_name());
        dockerService.createContainerFromCompose(dockerComposeService, ContainerStatus.ContainerType.devservice);
    }
}
