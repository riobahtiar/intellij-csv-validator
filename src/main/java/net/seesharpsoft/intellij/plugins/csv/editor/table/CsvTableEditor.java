package net.seesharpsoft.intellij.plugins.csv.editor.table;

import com.google.common.primitives.Ints;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import net.seesharpsoft.intellij.plugins.csv.CsvColumnInfoMap;
import net.seesharpsoft.intellij.plugins.csv.CsvHelper;
import net.seesharpsoft.intellij.plugins.csv.editor.CsvEditorSettingsExternalizable;
import net.seesharpsoft.intellij.plugins.csv.editor.table.api.TableDataHandler;
import net.seesharpsoft.intellij.plugins.csv.psi.CsvFile;
import net.seesharpsoft.intellij.plugins.csv.settings.CsvCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.List;

public abstract class CsvTableEditor implements FileEditor, FileEditorLocation {

    public static final String EDITOR_NAME = "Table Editor";

    public static final int INITIAL_COLUMN_WIDTH = 100;

    protected final Project project;
    protected final VirtualFile file;
    protected final UserDataHolder userDataHolder;
    protected final PropertyChangeSupport changeSupport;
    protected final TableDataHandler dataManagement;

    protected Document document;
    protected PsiFile psiFile;
    protected String currentSeparator;

    private Object[][] initialState = null;
    private CsvTableEditorState storedState = null;

    protected boolean tableIsEditable = true;

    public CsvTableEditor(@NotNull Project projectArg, @NotNull VirtualFile fileArg) {
        this.project = projectArg;
        this.file = fileArg;
        this.userDataHolder = new UserDataHolderBase();
        this.changeSupport = new PropertyChangeSupport(this);
        this.dataManagement = new TableDataHandler(this, TableDataHandler.MAX_SIZE);
    }

    protected abstract boolean isInCellEditMode();

    protected abstract void updateUIComponents();

    protected abstract void updateInteractionElements();

    protected abstract void applyEditorState(CsvTableEditorState editorState);

    protected abstract void setTableComponentData(Object[][] values);

    protected abstract void beforeTableComponentUpdate();

    protected abstract void afterTableComponentUpdate(Object[][] values);

    public abstract int getPreferredRowHeight();

    public final void updateTableComponentData(Object[][] values) {
        beforeTableComponentUpdate();
        try {
            setTableComponentData(values);
            saveChanges();
        } finally {
            afterTableComponentUpdate(values);
        }
    }

    public void setEditable(boolean editable) {
        this.tableIsEditable = editable;
        this.updateInteractionElements();
    }

    public boolean isEditable() {
        return this.tableIsEditable && !this.hasErrors();
    }

    public CsvColumnInfoMap<PsiElement> getColumnInfoMap() {
        CsvFile csvFile = getCsvFile();
        return csvFile == null ? null : csvFile.getColumnInfoMap();
    }

    public boolean hasErrors() {
        CsvColumnInfoMap columnInfoMap = getColumnInfoMap();
        return !isValid() || (columnInfoMap != null && columnInfoMap.hasErrors());
    }

    protected Object[][] storeStateChange(Object[][] data) {
        Object[][] result = this.dataManagement.addState(data);
        saveChanges();
        return result;
    }

    public void saveChanges() {
        if (isModified() && !ApplicationManager.getApplication().isUnitTestMode()) {
            saveChanges(generateCsv(this.dataManagement.getCurrentState()));
        }
    }

    public void saveChanges(final String content) {
        if (hasErrors()) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!this.document.isWritable() && ReadonlyStatusHandler.getInstance(this.project).ensureFilesWritable(this.file).hasReadonlyFiles()) {
                return;
            }
            ApplicationManager.getApplication().runWriteAction(() ->
                    CommandProcessor.getInstance().executeCommand(this.project, () -> {
                        this.document.setText(content);
                        this.initialState = dataManagement.getCurrentState();
                    }, "Csv Table Editor changes", null));
        });
    }

    protected String sanitizeFieldValue(Object value) {
        if (value == null) {
            return "";
        }
        return CsvHelper.quoteCsvField(value.toString(), this.currentSeparator, CsvEditorSettingsExternalizable.getInstance().isQuotingEnforced());
    }

    protected String generateCsv(Object[][] data) {
        StringBuilder result = new StringBuilder();
        for (int row = 0; row < data.length; ++row) {
            for (int column = 0; column < data[row].length; ++column) {
                Object value = data[row][column];
                result.append(sanitizeFieldValue(value));
                if (column < data[row].length - 1) {
                    result.append(this.currentSeparator);
                }
            }
            if (row < data.length - 1 ||
                    (CsvEditorSettingsExternalizable.getInstance().isFileEndLineBreak() && getColumnInfoMap().hasEmptyLastLine())) {
                result.append("\n");
            }
        }
        return result.toString();
    }

    @NotNull
    @Override
    public abstract JComponent getComponent();

    @Nullable
    @Override
    public abstract JComponent getPreferredFocusedComponent();

    @NotNull
    @Override
    public String getName() {
        return EDITOR_NAME;
    }

    protected <T extends CsvTableEditorState> T getFileEditorState() {
        if (storedState == null) {
            storedState = new CsvTableEditorState();
        }
        return (T) storedState;
    }

    @Override
    public FileEditorState getState(@NotNull FileEditorStateLevel level) {
        return getFileEditorState();
    }

    @Override
    public void setState(@NotNull FileEditorState fileEditorState) {
        CsvTableEditorState tableEditorState = fileEditorState instanceof CsvTableEditorState ? (CsvTableEditorState) fileEditorState : new CsvTableEditorState();
        this.storedState = tableEditorState;

        applyEditorState(getFileEditorState());
    }

    @Override
    public boolean isModified() {
        return this.dataManagement != null && initialState != null && !this.dataManagement.equalsCurrentState(initialState);
    }

    @Override
    public boolean isValid() {
        CsvFile csvFile = this.getCsvFile();
        return csvFile != null && csvFile.isValid();
    }

    @Override
    public void selectNotify() {
        this.initialState = null;
        updateUIComponents();
        this.initialState = dataManagement.getCurrentState();
    }

    @Override
    public void deselectNotify() {
        // auto save on change - nothing to do here
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener propertyChangeListener) {
        this.changeSupport.addPropertyChangeListener(propertyChangeListener);
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener propertyChangeListener) {
        this.changeSupport.removePropertyChangeListener(propertyChangeListener);
    }

    @Nullable
    @Override
    public BackgroundEditorHighlighter getBackgroundHighlighter() {
        return null;
    }

    @Nullable
    @Override
    public FileEditorLocation getCurrentLocation() {
        return this;
    }

    @Override
    public void dispose() {
        this.deselectNotify();
    }

    @Nullable
    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
        return userDataHolder.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T t) {
        userDataHolder.putUserData(key, t);
    }

    @NotNull
    @Override
    public FileEditor getEditor() {
        return this;
    }

    @Override
    public int compareTo(@NotNull FileEditorLocation o) {
        return 1;
    }

    @Nullable
    public StructureViewBuilder getStructureViewBuilder() {
        return file != null && file.isValid() ? StructureViewBuilder.PROVIDER.getStructureViewBuilder(file.getFileType(), file, this.project) : null;
    }

    @Nullable
    public VirtualFile getFile() {
        return this.file;
    }

    @Nullable
    public Project getProject() {
        return this.project;
    }

    @Nullable
    public CsvFile getCsvFile() {
        if (this.psiFile == null || !this.psiFile.isValid()) {
            this.document = FileDocumentManager.getInstance().getDocument(this.file);
            PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
            this.psiFile = documentManager.getPsiFile(this.document);
            this.currentSeparator = CsvCodeStyleSettings.getCurrentSeparator(this.getProject(), this.getFile());
        }
        return this.psiFile instanceof CsvFile ? (CsvFile) psiFile : null;
    }

    public TableDataHandler getDataHandler() {
        return this.dataManagement;
    }

    public int getRowCount() {
        return getDataHandler().getCurrentState().length;
    }

    public Font getFont() {
        return EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
    }

    public int getColumnCount() {
        Object[][] currentData = getDataHandler().getCurrentState();
        return currentData.length > 0 ? currentData[0].length : 0;
    }

    public Object[][] addRow(int focusedRowIndex, boolean before) {
        int index = (before ? (focusedRowIndex == -1 ? 0 : focusedRowIndex) : (focusedRowIndex == -1 ? getRowCount() : focusedRowIndex + 1)) +
                (getFileEditorState().getFixedHeaders() ? 1 : 0);
        TableDataHandler dataHandler = getDataHandler();
        Object[][] currentData = dataHandler.getCurrentState();
        Object[][] newData = ArrayUtil.insert(currentData, Math.min(index, currentData.length), new Object[getColumnCount()]);
        updateTableComponentData(dataHandler.addState(newData));
        return newData;
    }

    public Object[][] removeRows(int[] indices) {
        List<Integer> currentRows = Ints.asList(indices);
        currentRows.sort(Collections.reverseOrder());
        TableDataHandler dataHandler = getDataHandler();
        Object[][] currentData = dataHandler.getCurrentState();
        for (int currentRow : currentRows) {
            currentData = ArrayUtil.remove(currentData, currentRow + (getFileEditorState().getFixedHeaders() ? 1 : 0));
        }
        updateTableComponentData(dataHandler.addState(currentData));
        return currentData;
    }

    public Object[][] addColumn(int focusedColumnIndex, boolean before) {
        int index = before ? (focusedColumnIndex == -1 ? 0 : focusedColumnIndex) : (focusedColumnIndex == -1 ? getColumnCount() : focusedColumnIndex + 1);
        boolean fixedHeaders = getFileEditorState().getFixedHeaders();
        TableDataHandler dataHandler = getDataHandler();
        Object[][] currentData = dataHandler.getCurrentState();
        for (int i = 0; i < currentData.length; ++i) {
            currentData[i] = ArrayUtil.insert(currentData[i], index, fixedHeaders && i == 0 ? "" : null);
        }
        updateTableComponentData(dataHandler.addState(currentData));
        return currentData;
    }

    public Object[][] removeColumns(int[] indices) {
        List<Integer> currentColumns = Ints.asList(indices);
        currentColumns.sort(Collections.reverseOrder());

        TableDataHandler dataHandler = getDataHandler();
        Object[][] currentData = dataHandler.getCurrentState();

        for (int currentColumn : currentColumns) {
            for (int i = 0; i < currentData.length; ++i) {
                currentData[i] = ArrayUtil.remove(currentData[i], currentColumn);
            }
        }
        updateTableComponentData(dataHandler.addState(currentData));
        return currentData;
    }
}
