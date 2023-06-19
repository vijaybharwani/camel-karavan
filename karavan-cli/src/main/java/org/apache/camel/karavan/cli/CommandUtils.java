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
package org.apache.camel.karavan.cli;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.openshift.api.model.operatorhub.v1.Operator;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.tekton.pipeline.v1beta1.Pipeline;
import io.fabric8.tekton.pipeline.v1beta1.Task;
import org.apache.camel.karavan.cli.resources.*;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class CommandUtils {
    private static final Pipeline pipeline = new Pipeline();
    private static final Task task = new Task();

    public static void installKaravan(KaravanConfig config) {
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            OpenShiftClient oClient = client.adapt(OpenShiftClient.class);
            if (oClient.isSupported()) {
                System.out.println("⭕ Installing Karavan to OpenShift");
                config.setOpenShift(true);
            } else {
                System.out.println("\u2388 Installing Karavan to Kubernetes");
                config.setOpenShift(false);
            }
            install(config, client);
        }
    }

    private static void install(KaravanConfig config, KubernetesClient client) {
        // Check and install Tekton
        if (!isTektonInstalled(client)) {
            log("Tekton is not installed");
            if (isOpenShift(client)) {
                logPoint("Please install Tekton Operator first");
                System.exit(0);
            }
            installTekton(config, client);
            disableAffinityAssistant(client);
        }
        log("Tekton is installed");

        // Create namespace
        if (client.namespaces().withName(config.getNamespace()).get() == null) {
            Namespace ns = new NamespaceBuilder().withNewMetadata().withName(config.getNamespace()).endMetadata().build();
            ns = client.namespaces().resource(ns).create();
            log("Namespace " + ns.getMetadata().getName() + " created");
        } else {
            log("Namespace " + config.getNamespace() + " already exists");
        }

        // Check secrets
        if (!checkKaravanSecrets(config, client)) {
            logError("Karavan secrets not found");

            // try to create secrets
            if (!tryToCreateKaravanSecrets(config, client)) {
                logPoint("Apply secrets before installation");
                logPoint("Or provide Git, Auth and Image Registry options");
                System.exit(0);
            }

        } else {
            log("Karavan secrets found");
        }

        // Create service accounts
        createOrReplace(KaravanServiceAccount.getServiceAccount(config), client);
        createOrReplace(KaravanServiceAccount.getServiceAccountPipeline(config), client);
        // Create Roles and role bindings
        createOrReplace(KaravanRole.getRole(config), client);
        createOrReplace(KaravanRole.getRoleBinding(config), client);
        createOrReplace(KaravanRole.getRoleBindingView(config), client);
        createOrReplace(KaravanRole.getRoleDeployer(config), client);
        createOrReplace(KaravanRole.getRoleBindingPipeline(config), client);
        // Create PVC
        createOrReplace(KaravanPvc.getPvcData(config), client);
        createOrReplace(KaravanPvc.getPvcM2Cache(config), client);
        createOrReplace(KaravanPvc.getPvcJbangCache(config), client);
        // Create Tasks and Pipelines
        Arrays.stream(config.getRuntimes().split(",")).forEach(runtime -> {
            createOrReplace(KaravanTekton.getTask(config, runtime), client);
            createOrReplace(KaravanTekton.getPipeline(config, runtime), client);
        });
        // Create deployment
        createOrReplace(KaravanDeployment.getDeployment(config), client);
        // Create service
        createOrReplace(KaravanService.getService(config), client);
        if (config.isOpenShift()) {
            createOrReplace(KaravanService.getRoute(config), client);
        }
        log("Karavan is installed");
        System.out.print("\uD83D\uDC2B Karavan is starting ");
        while (!checkReady(config, client)) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {

            }
            System.out.print("\uD83D\uDC2B ");

        }
        System.out.println();
        log("Karavan is ready");
    }

    public static boolean checkKaravanSecrets(KaravanConfig config, KubernetesClient client) {
        Secret secret = client.secrets().inNamespace(config.getNamespace()).withName(Constants.NAME).get();
        return secret != null;
    }

    public static boolean tryToCreateKaravanSecrets(KaravanConfig config, KubernetesClient client) {
        if (config.gitConfigured()) {
            if (config.getImageRegistry() == null) {
                if (config.isOpenShift()) {
                    config.setImageRegistry(Constants.DEFAULT_IMAGE_REGISTRY_OPENSHIFT);
                } else {
                    Service registryService = client.services().inNamespace("kube-system").withName("registry").get();
                    config.setImageRegistry(registryService.getSpec().getClusterIP());
                }
            }
            if ((config.isAuthOidc() && config.oidcConfigured())
                    || (config.isAuthBasic() && config.getMasterPassword() != null && config.getMasterPassword().isEmpty())
                    || (config.getAuth().equals("public"))) {
                Secret secret = KaravanSecret.getSecret(config);
                client.resource(secret).createOrReplace();
                log("\uD83D\uDD11", "Karavan secret created");
                return true;
            }
        }
        return false;
    }

    public static boolean checkReady(KaravanConfig config, KubernetesClient client) {
        Deployment deployment = client.apps().deployments().inNamespace(config.getNamespace()).withName(Constants.NAME).get();
        Integer replicas = deployment.getStatus().getReplicas();
        Integer ready = deployment.getStatus().getReadyReplicas();
        Integer available = deployment.getStatus().getAvailableReplicas();
        Optional<DeploymentCondition> condition = deployment.getStatus().getConditions().stream()
                .filter(c -> c.getType().equals("Available") && c.getStatus().equals("True")).findFirst();
        return deployment.getStatus() != null
                && Objects.equals(replicas, ready)
                && Objects.equals(replicas, available)
                && condition.isPresent();
    }

    private static <T extends HasMetadata> void createOrReplace(T is, KubernetesClient client) {
        try {
            T result = client.resource(is).createOrReplace();
            log(result.getKind() + " " + result.getMetadata().getName() + " created");
        } catch (Exception e) {
            logError(e.getLocalizedMessage());
        }
    }

    private static void installTekton(KaravanConfig config, KubernetesClient client) {
        System.out.print("⏳ Installing Tekton");
        client.load(CommandUtils.class.getResourceAsStream("/pipelines.yaml")).create().forEach(hasMetadata -> {
            System.out.print(".");
        });
        client.load(CommandUtils.class.getResourceAsStream("/dashboard.yaml")).create().forEach(hasMetadata -> {
            System.out.print(".");
        });
        System.out.println();
        log("Tekton is installed");
    }

    private static void disableAffinityAssistant(KubernetesClient client) {
        log("⏳ Set disable-affinity-assistant equals 'true'");
        ConfigMap configMap = client.configMaps().inNamespace("tekton-pipelines").withName("feature-flags").get();
        Map<String, String> data = configMap.getData();
        data.put("disable-affinity-assistant", "true");
        configMap.setData(data);
        client.resource(configMap).createOrReplace();
    }

    private static boolean isTektonInstalled(KubernetesClient client) {
        APIResourceList kinds = client.getApiResources(pipeline.getApiVersion());
        if (kinds != null && kinds.getResources().stream().anyMatch(res -> res.getKind().equalsIgnoreCase(pipeline.getKind())) &&
                kinds.getResources().stream().anyMatch(res -> res.getKind().equalsIgnoreCase(task.getKind()))) {
            if (isOpenShift(client)) {
                Optional<Operator> oper = client.adapt(OpenShiftClient.class).operatorHub().operators().list().getItems().stream()
                        .filter(sub -> sub.getMetadata().getName().contains("openshift-pipelines-operator")).findFirst();
                return oper.isPresent();
            } else {
                return true;
            }
        }
        return false;
    }

    public static void log(String emoji, String message) {
        System.out.println(emoji + " " + message);
    }

    public static void log(String message) {
        System.out.println(getOkMessage(message));
    }

    public static void logPoint(String message) {
        System.out.println(getPointMessage(message));
    }

    public static void logError(String message) {
        System.out.println(getErrorMessage(message));
    }

    private static String getOkMessage(String message) {
        return "\uD83D\uDC4D " + message;
    }

    private static String getPointMessage(String message) {
        return "\uD83D\uDC49 " + message;
    }

    private static String getErrorMessage(String message) {
        return "‼\uFE0F " + message;
    }

    private static boolean isOpenShift(KubernetesClient client) {
        return client.adapt(OpenShiftClient.class).isSupported();
    }
}