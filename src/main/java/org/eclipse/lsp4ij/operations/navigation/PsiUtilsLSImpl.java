/*******************************************************************************
 * Copyright (c) 2019-2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.eclipse.lsp4ij.operations.navigation;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

/**
 *
 * @see <a href="https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/internal/core/ls/JDTUtilsLSImpl.java">https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/internal/core/ls/JDTUtilsLSImpl.java</a>
 */
public class PsiUtilsLSImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(PsiUtilsLSImpl.class);
    private final Project project;

    public PsiUtilsLSImpl(Project project) {
        this.project = project;
    }

    public static PsiUtilsLSImpl getInstance(Project project) {
        return new PsiUtilsLSImpl(project);
    }

    //@Override
    public static VirtualFile findFile(String uri) throws IOException {
        //return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(Paths.get(new URI(uri)).toFile());
        return VfsUtil.findFileByURL(new URL(uri));
    }
}
