/*******************************************************************************
 * Copyright (c) 2023, 2024 IBM Corporation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package io.openliberty.tools.intellij.it;

import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.*;

import java.nio.file.Paths;

/**
 * Tests that use a single module non Liberty Tools compliant REST Maven project.
 */
//@TestMethodOrder(MethodOrderer.MethodName.class)
public class MavenSingleModNLTRestProjectTest extends SingleModNLTRestProjectTestCommon {

    /**
     * The path to the folder containing the test projects.
     */
    private static final String PROJECTS_PATH = Paths.get("src", "test", "resources", "projects", "maven").toAbsolutePath().toString();

    /**
     * Single module REST project that lacks the configuration to be recognized by Liberty tools.
     */
    private static final String SM_NLT_REST_PROJECT_NAME = "singleModMavenRESTNoLTXmlCfg";

    /**
     * Prepares the environment for test execution.
     */
//    @Test
//    @Video
//    @Order(1)
    @BeforeAll
    public static void setup() {
        prepareEnv(PROJECTS_PATH, SM_NLT_REST_PROJECT_NAME);
    }

    MavenSingleModNLTRestProjectTest() {
        setProjectsDirPath(PROJECTS_PATH);
        setSmNLTRestProjectName(SM_NLT_REST_PROJECT_NAME);
        setBuildFileName("pom.xml");
        setHelperFilesDirPath(Paths.get("src", "test", "resources", "files", "smNLTRestProject", "maven").toAbsolutePath().toString());
    }
}
