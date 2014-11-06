package com.anadathur.intellij.fmtplugin;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.psi.PsiDocumentManager;
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
    private final CodeStyleManager codeStyleMgr;
    private final PsiDocumentManager psiDocumentMgr;
    private final Logger logger = com.intellij.openapi.diagnostic.Logger.getInstance(getClass());

    public FmtProjectPlugin(Project project) {
        this.project = project;
        editorManager = FileEditorManager.getInstance(project);
        psiManager = PsiManager.getInstance(project);
        codeStyleMgr = CodeStyleManager.getInstance(project);
        psiDocumentMgr = PsiDocumentManager.getInstance(project);
    }

    public void initComponent() {
        if (server == null) {
            server = new ApplicationServer(8999, new Formatter());
        }
    }

    private class Formatter implements ApplicationServer.RequestHandler {
        final FileDocumentManager fileDocMgr = FileDocumentManager.getInstance();
        final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();

        Formatter()
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
            final VirtualFile vFile = localFileSystem.refreshAndFindFileByIoFile(file);

            application.invokeLater(new Runnable() {
                public void run() {
                    try {
                        //editorManager.openFile(vFile, true);
                        final PsiFile psiFile = psiManager.findFile(vFile);
                        WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                            public void run() {
                                application.runWriteAction(new Runnable() {
                                    public void run() {
                                        codeStyleMgr.reformatText(psiFile, Arrays.asList(psiFile.getTextRange()));
                                        Document document = fileDocMgr.getDocument(vFile);
                                        psiDocumentMgr.doPostponedOperationsAndUnblockDocument(document);
                                        psiDocumentMgr.commitDocument(document);
                                        fileDocMgr.saveDocument(document);
                                        editorManager.closeFile(psiFile.getVirtualFile());
                                        synchronized (formattingSync){
                                            formattingSync.notify();
                                        }
                                    }
                                });
                            }
                        });
                    } catch (Exception e) {
                        logger.error(e);
                    }
                }
            }, ModalityState.any());

            synchronized (formattingSync){
                try {
                    formattingSync.wait(15000);
                } catch (InterruptedException e) {
                    logger.error(e);
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
            logger.info("Starting HTTP Server... ");
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    public void projectClosed() {
        logger.info("Stopping HTTP Server...");
        server.stop();
    }
}
