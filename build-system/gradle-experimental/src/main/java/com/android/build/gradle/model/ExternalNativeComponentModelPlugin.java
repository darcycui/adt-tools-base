/*
 * Copyright (C) 2015 The Android Open Source Project
 *
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
 */

package com.android.build.gradle.model;

import static com.android.build.gradle.model.ModelConstants.ARTIFACTS;
import static com.android.build.gradle.model.ModelConstants.EXTERNAL_BUILD_CONFIG;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.NativeBuildConfigGsonUtil;
import com.android.build.gradle.internal.NativeDependencyLinkage;
import com.android.build.gradle.internal.dependency.ArtifactContainer;
import com.android.build.gradle.internal.dependency.NativeLibraryArtifact;
import com.android.build.gradle.internal.gson.FileGsonTypeAdaptor;
import com.android.build.gradle.external.gson.NativeBuildConfigValue;
import com.android.build.gradle.managed.JsonConfigFile;
import com.android.build.gradle.managed.NativeBuildConfig;
import com.android.build.gradle.managed.NativeLibrary;
import com.android.build.gradle.model.internal.DefaultExternalNativeBinarySpec;
import com.android.build.gradle.model.internal.DefaultExternalNativeComponentSpec;
import com.android.utils.StringHelper;
import com.android.utils.NativeSourceFileExtensions;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.Exec;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.model.Defaults;
import org.gradle.model.Finalize;
import org.gradle.model.Model;
import org.gradle.model.ModelMap;
import org.gradle.model.ModelSet;
import org.gradle.model.Mutate;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.BinaryType;
import org.gradle.platform.base.BinaryTypeBuilder;
import org.gradle.platform.base.ComponentBinaries;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.ComponentTypeBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Plugin for importing projects built with external tools into Android Studio.
 */
public class ExternalNativeComponentModelPlugin implements Plugin<Project> {

    public static final String COMPONENT_NAME = "androidNative";

    @NonNull
    private final ToolingModelBuilderRegistry toolingRegistry;

    @NonNull
    private final ModelRegistry modelRegistry;

    @Inject
    private ExternalNativeComponentModelPlugin(
            @NonNull ToolingModelBuilderRegistry toolingRegistry,
            @NonNull ModelRegistry modelRegistry) {
        this.toolingRegistry = toolingRegistry;
        this.modelRegistry = modelRegistry;
    }

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(LifecycleBasePlugin.class);
        project.getPlugins().apply(ComponentModelBasePlugin.class);

        toolingRegistry.register(new NativeComponentModelBuilder(modelRegistry));
    }

    public static class Rules extends RuleSource {
        @ComponentType
        public static void defineComponentType(
                ComponentTypeBuilder<ExternalNativeComponentSpec> builder) {
            builder.defaultImplementation(DefaultExternalNativeComponentSpec.class);
        }

        @BinaryType
        public static void defineBinaryType(BinaryTypeBuilder<ExternalNativeBinarySpec> builder) {
            builder.defaultImplementation(DefaultExternalNativeBinarySpec.class);
        }

        @Model(EXTERNAL_BUILD_CONFIG)
        public static void createNativeBuildModel(NativeBuildConfig config) {
            config.getLibraries().afterEach(new Action<NativeLibrary>() {
                @Override
                public void execute(NativeLibrary nativeLibrary) {
                    nativeLibrary.setAssembleTaskName(getAssembleTaskName(nativeLibrary.getName()));
                }
            });
        }

        @Model(ModelConstants.EXTERNAL_CONFIG_FILES)
        public static void createConfigFilesModel(ModelSet<JsonConfigFile> configFiles) {
        }

        /**
         * Parses the JSON file to populate the NativeBuildConfig.
         */
        @Defaults
        public static void readJson(
                NativeBuildConfig config,
                ModelSet<JsonConfigFile> configFiles,
                ServiceRegistry registry) throws IOException {
            for (JsonConfigFile configFile : configFiles) {
                if (configFile.getConfig() == null) {
                    throw new InvalidUserDataException("Config file cannot be null");
                }

                // If JSON file does not exists, the plugin will not be configured.  But it's not
                // an error to allow the plugin to create tasks for generating the file.
                if (!configFile.getConfig().exists()) {
                    continue;
                }

                FileResolver fileResolver = registry.get(FileResolver.class);
                Gson gson = new GsonBuilder()
                        .registerTypeAdapter(File.class, new FileGsonTypeAdaptor(fileResolver))
                        .create();

                NativeBuildConfigValue jsonConfig = gson.fromJson(
                        new FileReader(configFile.getConfig()),
                        NativeBuildConfigValue.class);
                NativeBuildConfigGsonUtil.copyToNativeBuildConfig(jsonConfig, config);
            }
        }

        @Finalize
        static void finalizeNativeBuildConfig(NativeBuildConfig config) {
            // Configure default file extensions if not already set by user.
            if (config.getCFileExtensions().isEmpty()) {
                config.getCFileExtensions().addAll(NativeSourceFileExtensions.C_FILE_EXTENSIONS);
            }
            if (config.getCppFileExtensions().isEmpty()) {
                config.getCppFileExtensions().addAll(NativeSourceFileExtensions.CPP_FILE_EXTENSIONS);
            }
        }

        @Mutate
        public static void createExternalNativeComponent(
                ModelMap<ExternalNativeComponentSpec> components,
                final NativeBuildConfig config) {
            components.create(COMPONENT_NAME, new Action<ExternalNativeComponentSpec>() {
                @Override
                public void execute(ExternalNativeComponentSpec component) {
                    component.setConfig(config);
                }
            });
        }

        @ComponentBinaries
        public static void createExternalNativeBinary(
                final ModelMap<ExternalNativeBinarySpec> binaries,
                final ExternalNativeComponentSpec component) {
            for(final NativeLibrary lib : component.getConfig().getLibraries()) {
                binaries.create(lib.getName(), new Action<ExternalNativeBinarySpec>() {
                    @Override
                    public void execute(ExternalNativeBinarySpec binary) {
                        binary.setConfig(lib);
                    }
                });
            }
        }

        @BinaryTasks
        public static void createTasks(ModelMap<Task> tasks, final ExternalNativeBinarySpec binary) {
            tasks.create(
                    getAssembleTaskName(binary.getName()),
                    Exec.class,
                    new Action<Exec>() {
                        @Override
                        public void execute(Exec exec) {
                            if (!binary.getConfig().getBuildCommand().isEmpty()) {
                                //noinspection unchecked - Unavoidable due how Exec is implemented.
                                exec.setCommandLine(binary.getConfig().getBuildCommand());
                            } else {
                                //noinspection unchecked - Unavoidable due how Exec is implemented.
                                exec.setCommandLine(
                                        StringHelper.tokenizeString(
                                                binary.getConfig().getBuildCommandString()));
                            }
                        }
                    });
        }

        @Mutate
        public static void createGeneratorTasks(
                ModelMap<Task> tasks,
                ModelSet<JsonConfigFile> configFiles) {
            final List<String> generatorTasks = Lists.newLinkedList();
            for (final JsonConfigFile configFile : configFiles) {
                if (configFile.getCommand().isEmpty() && configFile.getCommandString() == null) {
                    continue;
                }
                if (!configFile.getCommand().isEmpty() && configFile.getCommandString() != null) {
                    throw new InvalidUserDataException(
                            "Cannot set both command and commandString for JSON config file: "
                                    + configFile.getConfig());
                }
                String taskName = "generate" + StringHelper.capitalize(
                        configFile.getConfig().getName());
                generatorTasks.add(taskName);
                tasks.create(
                        taskName,
                        Exec.class,
                        new Action<Exec>() {
                            @Override
                            public void execute(Exec task) {
                                if (!configFile.getCommand().isEmpty()) {
                                    //noinspection unchecked - Unavoidable due how Exec is implemented.
                                    task.commandLine(configFile.getCommand());
                                } else {
                                    //noinspection unchecked - Unavoidable due how Exec is implemented.
                                    task.commandLine(StringHelper.tokenizeString(
                                            configFile.getCommandString()));
                                }
                            }
                        });
            }
            tasks.create("generateConfigFile",
                    new Action<Task>() {
                        @Override
                        public void execute(Task task) {
                            task.setDescription("Create configuration files for the plugin.");
                            task.dependsOn(generatorTasks);
                        }
                    }
            );
        }

        @Mutate
        static void createCleanTask(
                final ModelMap<Task> task,
                final NativeBuildConfig config) {
            if (config.getCleanCommands().isEmpty()
                    && config.getCleanCommandStrings().isEmpty()) {
                return;
            }
            final String cleanNativeBuildTaskName = "cleanNativeBuild";
            int index = 0;
            if (!config.getCleanCommands().isEmpty()) {
                List<String> currentCleanCommand = new ArrayList<String>();
                for (final String token : config.getCleanCommands()) {
                    if (token == null) {
                        final List<String> cleanCommand = currentCleanCommand;
                        task.create(cleanNativeBuildTaskName + index++,
                                Exec.class,
                                new Action<Exec>() {
                                    @Override
                                    public void execute(Exec task) {
                                        //noinspection unchecked - Unavoidable due how Exec is implemented.
                                        task.commandLine(cleanCommand);
                                    }
                                });
                        currentCleanCommand = new ArrayList<String>();
                    } else {
                        currentCleanCommand.add(token);
                    }
                }
            } else {
                for (final String cleanCommandString : config.getCleanCommandStrings()) {
                    task.create(cleanNativeBuildTaskName + index++,
                            Exec.class,
                            new Action<Exec>() {
                                @Override
                                public void execute(Exec task) {
                                    //noinspection unchecked - Unavoidable due how Exec is implemented.
                                    task.commandLine(StringHelper.tokenizeString(
                                            cleanCommandString));
                                }
                            });
                }
            }
            final int count = index;
            task.named("clean", new Action<Task>() {
                @Override
                public void execute(Task task) {
                    for (int i = 0; i < count; ++i)
                        task.dependsOn(cleanNativeBuildTaskName + i);
                }
            });
        }

        @Model(ARTIFACTS)
        public static void createNativeLibraryArtifacts(
                ArtifactContainer artifactContainer,
                final NativeBuildConfig config,
                final ModelMap<Task> tasks) {
            for(final NativeLibrary lib : config.getLibraries()) {
                artifactContainer.getNativeArtifacts().create(lib.getName(),
                        new Action<NativeLibraryArtifact>() {
                            @Override
                            public void execute(NativeLibraryArtifact artifacts) {
                                artifacts.getLibraries().add(lib.getOutput());
                                artifacts.setAbi(lib.getAbi());
                                artifacts.setVariantName(lib.getName());
                                artifacts.setBuildType(lib.getBuildType());
                                if (lib.getOutput() != null) {
                                    artifacts.setLinkage(lib.getOutput().getName().endsWith(".so")
                                            ? NativeDependencyLinkage.SHARED
                                            : NativeDependencyLinkage.STATIC);
                                }
                                artifacts.setBuiltBy(
                                        Lists.<Object>newArrayList(
                                                tasks.get("create"
                                                        + StringHelper.capitalize(lib.getName()))));
                            }
                        });
            }
        }
    }
    private static String getAssembleTaskName(String libraryName) {
        return "create" + StringHelper.capitalize(libraryName);
    }
}
