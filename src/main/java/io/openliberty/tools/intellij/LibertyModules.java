/*******************************************************************************
 * Copyright (c) 2022 IBM Corporation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 *  SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/

package io.openliberty.tools.intellij;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.nio.file.Paths;
import java.util.*;

/**
 * Singleton to save the Liberty modules in the open project
 */
public class LibertyModules {

    private static LibertyModules instance = null;

    // key is build file associated with the Liberty project
    Map<VirtualFile, LibertyModule> libertyModules;

    private LibertyModules() {
        libertyModules = Collections.synchronizedMap(new HashMap<>());
    }

    public synchronized static LibertyModules getInstance() {
        if (instance == null) {
            instance = new LibertyModules();
        }
        return instance;
    }

    /**
     * Add tracked Liberty project to workspace, update project,
     * projectType, name and validContainerVersion if already tracked.
     *
     * @param module LibertyModule
     */
    public LibertyModule addLibertyModule(LibertyModule module) {
        synchronized(libertyModules) {
            if (libertyModules.containsKey(module.getBuildFile())) {
                // Update existing Liberty project, projectType module, name and validContainerVersion
                // Do not update the build file (key), debugMode, shellWidget or customStartParams since
                // they may modify saved run configs.
                LibertyModule existing = libertyModules.get(module.getBuildFile());
                existing.setProject(module.getProject());
                existing.setProjectType(module.getProjectType());
                existing.setName(module.getName());
                existing.setValidContainerVersion(module.isValidContainerVersion());
            } else {
                libertyModules.put(module.getBuildFile(), module);
            }
            return libertyModules.get(module.getBuildFile());
        }
    }

    /**
     * Get a Liberty module associated with the corresponding build file
     *
     * @param buildFile build file
     * @return LibertyModule
     */
    public LibertyModule getLibertyModule(VirtualFile buildFile) {
        synchronized(libertyModules) {
            return libertyModules.get(buildFile);
        }
    }

    /**
     * Returns the Liberty project associated with a build file path string
     *
     * @param buildFile String, path to build file
     * @return LibertyModule
     */
    public LibertyModule getLibertyProjectFromString(String buildFile) {
        synchronized(libertyModules) {
            VirtualFile vBuildFile = VfsUtil.findFile(Paths.get(buildFile), true);
            return libertyModules.get(vBuildFile);
        }
    }

    /**
     * Returns all build files as a list of strings associated with the Liberty project.
     * Used for Liberty run configuration
     *
     * @param project
     * @return List<String> Liberty project build files as strings
     */
    public List<String> getLibertyBuildFilesAsString(Project project) {
        List<String> sBuildFiles = new ArrayList<>();
        synchronized (libertyModules) {
            libertyModules.values().forEach(libertyModule -> {
                if (project.equals(libertyModule.getProject())) {
                    // need to convert to NioPath for OS specific paths
                    sBuildFiles.add(libertyModule.getBuildFile().toNioPath().toString());
                }
            });
        }
        return sBuildFiles;
    }

    /**
     * Returns all Liberty modules with the supported project type(s) for the given project
     * ex. all Liberty Maven projects
     *
     * @param project
     * @param projectTypes
     * @return Liberty modules with the given project type(s)
     */
    public List<LibertyModule> getLibertyModules(Project project, List<String> projectTypes) {
        ArrayList<LibertyModule> supportedLibertyModules = new ArrayList<>();
        synchronized (libertyModules) {
            libertyModules.values().forEach(libertyModule -> {
                if (project.equals(libertyModule.getProject()) && projectTypes.contains(libertyModule.getProjectType())) {
                    supportedLibertyModules.add(libertyModule);
                }
            });
        }
        return supportedLibertyModules;
    }

    /**
     * Remove the given Liberty module
     *
     * @param libertyModule
     */
    public void removeLibertyModule(LibertyModule libertyModule) {
        synchronized(libertyModules) {
            libertyModules.remove(libertyModule.getBuildFile());
        }
    }

    /**
     * Remove all stored Liberty modules for the given project that
     * do not have active terminal widgets (running commands)
     *
     * @param project
     */
    public void removeForProject(Project project) {
        synchronized(libertyModules) {
            Iterator it = libertyModules.values().iterator();
            while (it.hasNext()) {
                LibertyModule libertyModule = (LibertyModule) it.next();
                // do not remove from list if the corresponding terminal widget has running commands
                if (project.equals(libertyModule.getProject()) && !(libertyModule.getShellWidget() != null && libertyModule.getShellWidget().hasRunningCommands())) {
                    it.remove();
                }
            }
        }
    }
}
