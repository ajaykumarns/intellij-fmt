package com.anadathur.intellij.fmtplugin;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Arrays;

/**
 * Created by anadathur on 10/4/14.
 */
public class FmtProjectPlugin implements ProjectComponent {
    private final Project project;
    private ApplicationServer server;
    private final Application application = ApplicationManager.getApplication();
    private final FileEditorManager editorManager;
    private final PsiManager psiManager;
    private final CodeStyleManager codeStyleManager;

    public FmtProjectPlugin(Project project) {
        this.project = project;
        editorManager = FileEditorManager.getInstance(project);
        psiManager = PsiManager.getInstance(project);
        codeStyleManager = CodeStyleManager.getInstance(project);
    }

    public void initComponent() {
        if (server == null) {
            server = new ApplicationServer(8999, new Formatter());
        }
    }

    private class Formatter implements ApplicationServer.RequestHandler {
        {
            LocalFileSystem.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
                @Override
                public void contentsChanged(@NotNull VirtualFileEvent event) {
                    super.contentsChanged(event);
                }
            });
        }

        @Override
        public synchronized void format(File file) {
            if (!file.exists() || !file.canRead())
                return;
            final Object formattingSync = new Object();
            final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

            application.invokeLater(new Runnable() {
                public void run() {
                    try {
                        //editorManager.openFile(vFile, true);
                        final PsiFile psiFile = psiManager.findFile(vFile);
                        WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                            public void run() {
                                application.runWriteAction(new Runnable() {
                                    public void run() {
                                        codeStyleManager.reformatText(psiFile, Arrays.asList(psiFile.getTextRange()));
                                        FileDocumentManager.getInstance().saveAllDocuments();
                                        //editorManager.closeFile(psiFile.getVirtualFile());
                                        synchronized (formattingSync){
                                            formattingSync.notify();
                                        }
                                    }
                                });
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }, ModalityState.any());

            synchronized (formattingSync){
                try {
                    formattingSync.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void disposeComponent() {
        if (server != null) {
            server.stop();
        }
    }

    @NotNull
    public String getComponentName() {
        return "com.anadathur.intellij.fmtplugin.FmtProjectPlugin";
    }

    public void projectOpened() {
        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public void projectClosed() {
        System.out.println("Stopping HTTP Server...");
        server.stop();
    }
}
