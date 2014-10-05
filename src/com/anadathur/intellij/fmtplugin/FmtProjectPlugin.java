package com.anadathur.intellij.fmtplugin;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
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

    @NotNull
    private CodeFormatterFacade createFormatter() {
        final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(psiManager.getProject());
        return new CodeFormatterFacade(styleSettings);
    }

    public void initComponent() {
        if (server == null) {
            server = new ApplicationServer(8999, new Formatter());
        }
    }

    private class Formatter implements ApplicationServer.RequestHandler {
        @Override
        public void format(File file) {
            final Object obj = new Object();
            final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);

            application.invokeLater(new Runnable() {
                public void run() {
                    try {
                        editorManager.openFile(vFile, true);
                        final PsiFile psiFile = psiManager.findFile(vFile);
                        WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                            public void run() {
                                application.runWriteAction(new Runnable() {
                                    public void run() {
                                        final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiManager.getProject());
                                        codeStyleManager.reformatText(psiFile, Arrays.asList(psiFile.getTextRange()));
                                        //createFormatter().processText(psiFile, new FormatTextRanges(psiFile.getTextRange(), true), false);
                                        editorManager.closeFile(psiFile.getVirtualFile());
                                        synchronized (obj){
                                            obj.notify();
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

            synchronized (obj){
                try {
                    obj.wait();
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
