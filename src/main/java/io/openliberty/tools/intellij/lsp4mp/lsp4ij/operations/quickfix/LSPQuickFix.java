package io.openliberty.tools.intellij.lsp4mp.lsp4ij.operations.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.openliberty.tools.intellij.lsp4mp.lsp4ij.LanguageServerWrapper;
import io.openliberty.tools.intellij.lsp4mp.lsp4ij.LanguageServiceAccessor;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class LSPQuickFix implements LocalQuickFix {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPQuickFix.class);//$NON-NLS-1$
    public static final String CodeActionKindQuickFix = "quickfix";
    private String serverId; // the id corresponding to the LSP server wrapper
    private String textDocument;
    private Either<Command, CodeAction> fix; // A Command or CodeAction object recognised by the LSP server.

    public LSPQuickFix(String serverId, String file, Either<Command, CodeAction> fix) {
        this.serverId = serverId;
        this.textDocument = file;
        this.fix = fix;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
        if (fix.isLeft()) {
            return fix.getLeft().getTitle();
        } else {
            return fix.getRight().getTitle();
        }
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        Arrays.stream(ModuleManager.getInstance(project).getModules()).forEach(module -> {
            VirtualFile vFile = descriptor.getPsiElement().getContainingFile().getVirtualFile();
            try {
                Collection<LanguageServerWrapper> languageServerWrappers = LanguageServiceAccessor.getInstance(project)
                        .getLSWrappers(vFile, capabilities -> true);
                languageServerWrappers.stream()
                        .filter(wrapper -> wrapper.serverDefinition.id == serverId)
                        .forEach(wrapper -> {
                            try {
                                CompletableFuture<LanguageServer> serverFuture = wrapper.getInitializedServer();
                                LOGGER.warn("QuickFix apply waiting for LSP to start");
                                LanguageServer server = serverFuture.get(); // wait for server to start
                                if (fix.isLeft()) {
                                    executeCommand(server, fix.getLeft());
                                } else {
                                    executeCodeAction(server, fix.getRight());
                                }
                            } catch (ExecutionException e) {
                                throw new RuntimeException(e);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (IOException x) {
                LOGGER.warn("QuickFix apply get LSP wrappers exception", x);
            }
        });
    }

    private void executeCommand(LanguageServer server, Command command) throws ExecutionException, InterruptedException {
        var params = new ExecuteCommandParams(command.getCommand(), command.getArguments());
        CompletableFuture<Object> result = server.getWorkspaceService().executeCommand(params);
        LOGGER.warn("QuickFix apply 1, wait for result=" + result.get());
    }

    private void executeCodeAction(LanguageServer server, CodeAction action) throws ExecutionException, InterruptedException {
        var param = new CodeActionParams();
        param.setTextDocument(new TextDocumentIdentifier(textDocument));
        if (action.getDiagnostics() != null) {
            param.setRange(action.getDiagnostics().iterator().next().getRange());
        }
        var context = new CodeActionContext();
        context.setDiagnostics(action.getDiagnostics());
        context.setOnly(Arrays.asList(CodeActionKindQuickFix));
        context.setTriggerKind(CodeActionTriggerKind.Invoked);
        param.setContext(context);
        CompletableFuture<List<Either<Command, CodeAction>>> result = server.getTextDocumentService().codeAction(param);
        LOGGER.warn("QuickFix apply 2, wait for result=" + result.get());
    }
//
//    private String getCommand(Object param) {
//        if (param instanceof Command) {
//            return ((Command) param).getCommand();
//        } else if (param instanceof Either<?, ?>) {
//            var v = (Either<Command, CodeAction>) param;
//            if (v.isLeft()) {
//                return v.getLeft().getCommand();
//            } else {
//                if (v.getRight().getCommand() != null) {
//                    return v.getRight().getCommand().getCommand();
//                }
//                return v.getRight().getEdit().toString();
//            }
//        }
//        return null;
//    }

}
