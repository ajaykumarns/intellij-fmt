import com.anadathur.intellij.fmtplugin.ApplicationServer;
import com.intellij.formatting.FormatTextRanges;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Created by anadathur on 10/4/14.
 */
public class FmtProjectPlugin implements ProjectComponent {
    private final Project project;
    private ApplicationServer server;

    public FmtProjectPlugin(Project project) {
        this.project = project;
    }

    public void initComponent() {
        if (server == null) {
            server = new ApplicationServer(8999, new ApplicationServer.RequestHandler() {
                final Application application = ApplicationManager.getApplication();
                final FileEditorManager editorManager = FileEditorManager.getInstance(project);
                final PsiManager psiManager = PsiManager.getInstance(project);
                final CodeFormatterFacade codeFormatterFacade = getFormatterFacade();

                @NotNull
                private CodeFormatterFacade getFormatterFacade() {
                    final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(psiManager.getProject());
                    final CodeFormatterFacade codeFormatter = new CodeFormatterFacade(styleSettings);
                    return codeFormatter;
                }

                @Override
                public void format(File file) {
                    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
                    final OpenFileDescriptor fileDescriptor = new OpenFileDescriptor(project, vFile);
                    application.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            final ActionManager actionManager = ActionManager.getInstance();
                            AnAction action = actionManager.getAction("ReformatCode");
                            try {
                                fileDescriptor.navigate(true);
                                final PsiFile psiFile = psiManager.findFile(vFile);
                                WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                                    public void run() {
                                        application.runWriteAction(new Runnable() {
                                            public void run() {
                                                final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiManager.getProject());
                                                final Document document = psiFile.getViewProvider().getDocument();
                                                documentManager.commitDocument(document);
                                                codeFormatterFacade.processText(psiFile, new FormatTextRanges(psiFile.getTextRange(), true), false);
                                                editorManager.closeFile(vFile);
                                            }
                                        });
                                    }
                                });

/*                                final DataManager dataManager = DataManager.getInstance();
                                final DataContext dataContext = dataManager.getDataContext();
                                AnActionEvent event =
                                    new AnActionEvent(
                                        null, dataContext,
                                        ActionPlaces.UNKNOWN, new Presentation("ReformatCode"), actionManager, 0
                                    );
                                action.actionPerformed(event);*/
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }, ModalityState.any());
                }
            });
        }
    }

    public void disposeComponent() {
        if (server != null) {
            server.stop();
        }
    }

    @NotNull
    public String getComponentName() {
        return "FmtProjectPlugin";
    }

    public void projectOpened() {
        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

/*        Messages.showMessageDialog("New project opened: " + project.getBaseDir().getCanonicalPath(),
                "FmtPlugin", Messages.getInformationIcon());*/
    }

    public void projectClosed() {
        // called when project is being closed
    }
}
