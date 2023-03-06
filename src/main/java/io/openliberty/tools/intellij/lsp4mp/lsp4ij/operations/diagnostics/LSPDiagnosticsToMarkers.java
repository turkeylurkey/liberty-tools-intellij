package io.openliberty.tools.intellij.lsp4mp.lsp4ij.operations.diagnostics;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import io.openliberty.tools.intellij.lsp4mp.lsp4ij.LSPIJUtils;
import io.openliberty.tools.intellij.lsp4mp.lsp4ij.LanguageServiceAccessor;
import io.openliberty.tools.intellij.lsp4mp.lsp4ij.operations.quickfix.LSPQuickFix;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.awt.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class LSPDiagnosticsToMarkers implements Consumer<PublishDiagnosticsParams> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LSPDiagnosticsToMarkers.class);

    private static final Key<Map<String, RangeHighlighter[]>> LSP_MARKER_KEY_PREFIX = Key.create(LSPDiagnosticsToMarkers.class.getName() + ".markers");
    private static final Key<Map<Diagnostic, LocalQuickFix[]>> LSP_QUICKFIX_KEY_PREFIX = Key.create(LSPDiagnosticsToMarkers.class.getName() + ".quickfixes");

    private final String languageServerId;

    public LSPDiagnosticsToMarkers(@Nonnull String serverId) {
        this.languageServerId = serverId;
    }
    @Override
    public void accept(PublishDiagnosticsParams publishDiagnosticsParams) {
        ApplicationManager.getApplication().invokeLater(() -> {
            VirtualFile file = null;
            try {
                file = LSPIJUtils.findResourceFor(new URI(publishDiagnosticsParams.getUri()));
            } catch (URISyntaxException e) {
                LOGGER.warn(e.getLocalizedMessage(), e);
            }
            if (file != null) {
                Document document = FileDocumentManager.getInstance().getDocument(file);
                if (document != null) {
                    Editor[] editors  = LSPIJUtils.editorsForFile(file, document);
                    for(Editor editor : editors) {
                        cleanMarkers(editor);
                        createMarkers(editor, document, publishDiagnosticsParams.getDiagnostics());
                    }
                }
            }
        });
    }

    private void createMarkers(Editor editor, Document document, List<Diagnostic> diagnostics) {
        RangeHighlighter[] rangeHighlighters = new RangeHighlighter[diagnostics.size()];
        int index = 0;
        for(Diagnostic diagnostic : diagnostics) {
            // this will block waiting for response from Language Server
            // Note we are currently on a thread from ApplicationManager.getApplication().invokeLater()
            List<Either<Command, CodeAction>> fixes = getCodeActions(editor, document, diagnostic);
            LocalQuickFix[] quickFixes = new LocalQuickFix[fixes.size()];
            int jIndex = 0;
            for (var fix : fixes) {
                if (fix.isRight() && !LSPQuickFix.CodeActionKindQuickFix.equals(fix.getRight().getKind())) {
                    continue;
                }
                String uri = FileDocumentManager.getInstance().getFile(document).getUrl(); // full url ok
                quickFixes[jIndex++] =  new LSPQuickFix(languageServerId, uri, fix);
            }
            Map<Diagnostic, LocalQuickFix[]> allQuickFixes = getAllQuickFixes(editor);
            allQuickFixes.put(diagnostic, quickFixes);

            int startOffset = LSPIJUtils.toOffset(diagnostic.getRange().getStart(), document);
            int endOffset = LSPIJUtils.toOffset(diagnostic.getRange().getEnd(), document);
            if (endOffset > document.getLineEndOffset(document.getLineCount() - 1)) {
                endOffset = document.getLineEndOffset(document.getLineCount() - 1);
            }
            int layer = getLayer(diagnostic.getSeverity());
            EffectType effectType = getEffectType(diagnostic.getSeverity());
            Color color = getColor(diagnostic.getSeverity());
            RangeHighlighter rangeHighlighter = editor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer,
                    new TextAttributes(editor.getColorsScheme().getDefaultForeground(), editor.getColorsScheme().getDefaultBackground(), color, effectType, Font.PLAIN),
                    HighlighterTargetArea.EXACT_RANGE);
            rangeHighlighter.setErrorStripeTooltip(diagnostic);
            rangeHighlighters[index++] = rangeHighlighter;
        }
        Map<String, RangeHighlighter[]> allMarkers = getAllMarkers(editor);
        allMarkers.put(languageServerId, rangeHighlighters);
        // forces re-highlighting/refreshes inspections for the current file to fix https://github.com/OpenLiberty/liberty-tools-intellij/issues/85
        // triggers io.openliberty.tools.intellij.lsp4mp.lsp4ij.operations.diagnostics.LSPLocalInspectionTool#checkFile()
        Project project = editor.getProject();
        DaemonCodeAnalyzer.getInstance(project).restart(PsiManager.getInstance(project).findFile(FileDocumentManager.getInstance().getFile(document)));
    }

    @NotNull
    private Map<String, RangeHighlighter[]> getAllMarkers(Editor editor) {
        Map<String, RangeHighlighter[]> allMarkers = editor.getUserData(LSP_MARKER_KEY_PREFIX);
        if (allMarkers == null) {
            allMarkers = new HashMap<>();
            editor.putUserData(LSP_MARKER_KEY_PREFIX, allMarkers);
        }
        return allMarkers;
    }

    @NotNull
    private Map<Diagnostic, LocalQuickFix[]> getAllQuickFixes(Editor editor) {
        Map<Diagnostic, LocalQuickFix[]> allFixes = editor.getUserData(LSP_QUICKFIX_KEY_PREFIX);
        if (allFixes == null) {
            allFixes = new HashMap<>();
            editor.putUserData(LSP_QUICKFIX_KEY_PREFIX, allFixes);
        }
        return allFixes;
    }

    private EffectType getEffectType(DiagnosticSeverity severity) {
        return severity== DiagnosticSeverity.Hint?EffectType.BOLD_DOTTED_LINE:EffectType.WAVE_UNDERSCORE;
    }

    private int getLayer(DiagnosticSeverity severity) {
        return severity== DiagnosticSeverity.Error?HighlighterLayer.ERROR:HighlighterLayer.WARNING;
    }

    // Controls the underline colour of the diagnostic
    private Color getColor(DiagnosticSeverity severity) {
        if (severity == null) {
            // if not set, default to Error
            return Color.RED;
        }
        switch (severity) {
            case Hint:
                return Color.GRAY;
            case Error:
                return Color.RED;
            case Information:
                return Color.GRAY;
            case Warning:
                return Color.YELLOW;

        }
        return Color.GRAY;
    }

    private void cleanMarkers(Editor editor) {
        Map<String, RangeHighlighter[]> allMarkers = getAllMarkers(editor);
        RangeHighlighter[] highlighters = allMarkers.get(languageServerId);
        MarkupModel markupModel = editor.getMarkupModel();
        if (highlighters != null) {
            for (RangeHighlighter highlighter : highlighters) {
                markupModel.removeHighlighter(highlighter);
            }
        }
        allMarkers.remove(languageServerId);
    }

    /**
     * Initiate a codeAction request. If there are no diagnostics then there is no need for code actions.
     * If there is an existing request no need to send another.
     *
     * @param editor - the editor of the document needing code actions
     * @param document - user code containing diagnostics
     * @param diagnostic - the error message which needs further detail in the form of code actions.
     */
    private List<Either<Command, CodeAction>> getCodeActions(Editor editor, Document document, Diagnostic diagnostic) {
        // Implementation note, we don't need to get the codeActions as soon as the diagnostics arrive. We could get them
        // later when we convert the diagnostic to ProblemDescriptor. The only thing missing in LSPLocalinspectiontool
        // is the id of the language server that provided the diagnostic. If we associate the diagnostic to the LS id in
        // a table here then we could move getCodeActions to lsplocalinspectiontool.
        if (diagnostic == null) {
            return null;
        }
        CompletableFuture<List<LanguageServer>> futureList = LanguageServiceAccessor.getInstance(editor.getProject())
                .getLanguageServers(document, capabilities -> true);
        CompletableFuture<List<Either<Command, CodeAction>>> caLspRequest = futureList.thenApplyAsync(languageServers -> {
            // Async is very important here, otherwise the LS Client thread is in deadlock and doesn't read bytes from LS
            List<Either<Command, CodeAction>> allCodeActions = languageServers.stream()
                    .map(languageServer -> {
                        try {
                            Range r = diagnostic.getRange();
                            CodeActionParams params = LSPIJUtils.toCodeActionParams(r, document, Arrays.asList(diagnostic));
                            List<Either<Command, CodeAction>> codeActions = languageServer.getTextDocumentService().codeAction(params).get(); // block here?
                            LOGGER.warn("getCodeActions: List<Either<Command, CodeAction>> codeActions="+codeActions.toString().substring(0,127));
                            return codeActions;
                        } catch (ExecutionException e) {
                            LOGGER.warn(e.getLocalizedMessage(), e);
                            return null;
                        } catch (InterruptedException e) {
                            LOGGER.warn(e.getLocalizedMessage(), e);
                            Thread.currentThread().interrupt();
                            return null;
                        }})
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            return allCodeActions;
        });
        try {
            return caLspRequest.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.warn("Error reading codeActions, " + e.getLocalizedMessage(), e);
            Thread.currentThread().interrupt();
        } catch (TimeoutException e) {
            LOGGER.warn("Timeout reading codeActions, " + e.getLocalizedMessage());
        }
        return null;
    }

    public static LocalQuickFix[] getQuickFixes(Editor editor, Diagnostic d) {
        Map<Diagnostic, LocalQuickFix[]> allFixes = editor.getUserData(LSP_QUICKFIX_KEY_PREFIX);
        LOGGER.warn("getQuickFixes, return == null:"+((allFixes!=null?allFixes.get(d):null)==null));
        return allFixes!=null?allFixes.get(d):null;
    }

    public static RangeHighlighter[] getMarkers(Editor editor, String languageServerId) {
        Map<String, RangeHighlighter[]> allMarkers = editor.getUserData(LSP_MARKER_KEY_PREFIX);
        return allMarkers!=null?allMarkers.get(languageServerId):null;
    }
}
