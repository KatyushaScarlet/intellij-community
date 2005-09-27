package com.intellij.openapi.diff.impl.incrementalMerge.ui;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.diff.actions.NextDiffAction;
import com.intellij.openapi.diff.actions.PreviousDiffAction;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.diff.impl.EditingSides;
import com.intellij.openapi.diff.impl.mergeTool.MergeRequestImpl;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.incrementalMerge.ChangeCounter;
import com.intellij.openapi.diff.impl.incrementalMerge.ChangeList;
import com.intellij.openapi.diff.impl.incrementalMerge.MergeList;
import com.intellij.openapi.diff.impl.splitter.DiffDividerPaint;
import com.intellij.openapi.diff.impl.splitter.LineBlocks;
import com.intellij.openapi.diff.impl.util.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import gnu.trove.TIntHashSet;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

public class MergePanel2 implements DiffViewer {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.openapi.diff.impl.incrementalMerge.ui.MergePanel2");
  private static final EditorPlace.ViewProperty<EditorColorsScheme> EDITOR_SCHEME = new EditorPlace.ViewProperty<EditorColorsScheme>(
    null) {
    public void doUpdateEditor(EditorEx editorEx, EditorColorsScheme scheme, EditorPlace.ComponentState state) {
      if (scheme != null) editorEx.setColorsScheme(scheme);
    }
  };
  public static final EditorPlace.ViewProperty<Boolean> LINE_NUMBERS = new EditorPlace.ViewProperty<Boolean>(
    Boolean.TRUE) {
    public void doUpdateEditor(EditorEx editorEx, Boolean aBoolean, EditorPlace.ComponentState state) {
      editorEx.getSettings().setLineNumbersShown(aBoolean.booleanValue());
    }
  };
  public static final EditorPlace.ViewProperty<Boolean> LINE_MARKERS_AREA = new EditorPlace.ViewProperty<Boolean>(
    Boolean.TRUE) {
    public void doUpdateEditor(EditorEx editorEx, Boolean aBoolean, EditorPlace.ComponentState state) {
      editorEx.getSettings().setLineMarkerAreaShown(aBoolean.booleanValue());
    }
  };
  public static final EditorPlace.ViewProperty<Integer> ADDITIONAL_LINES = new EditorPlace.ViewProperty<Integer>(null) {
    public void doUpdateEditor(EditorEx editorEx, Integer integer, EditorPlace.ComponentState state) {
      if (integer != null) editorEx.getSettings().setAdditionalLinesCount(integer.intValue());
    }
  };
  public static final EditorPlace.ViewProperty<Integer> ADDITIONAL_COLUMNS = new EditorPlace.ViewProperty<Integer>(
    null) {
    public void doUpdateEditor(EditorEx editorEx, Integer integer, EditorPlace.ComponentState state) {
      if (integer != null) editorEx.getSettings().setAdditionalColumnsCount(integer.intValue());
    }
  };
  public static final EditorPlace.ViewProperty<EditorColorsScheme> HIGHLIGHTER_SETTINGS = new EditorPlace.ViewProperty<EditorColorsScheme>(
    null) {
    public void doUpdateEditor(EditorEx editorEx, EditorColorsScheme settings, EditorPlace.ComponentState state) {
      if (settings == null) settings = EditorColorsManager.getInstance().getGlobalScheme();
      DiffEditorState editorState = (DiffEditorState)state;
      editorEx.setHighlighter(
        HighlighterFactory.createHighlighter(editorState.getFileType(), settings, editorState.getProject()));
    }
  };
  private static final Collection<EditorPlace.ViewProperty> ALL_PROPERTIES = Arrays.asList(new EditorPlace.ViewProperty[]{
    ADDITIONAL_COLUMNS, ADDITIONAL_LINES, EDITOR_SCHEME, HIGHLIGHTER_SETTINGS,
    LINE_MARKERS_AREA, LINE_NUMBERS});

  private final DiffPanelOutterComponent myPanel;
  private DiffRequest myData;
  private MergeList myMergeList;
  private boolean myDuringCreation = false;
  private final SyncScrollSupport myScrollSupport = new SyncScrollSupport();
  private final DiffDivider[] myDividers = new DiffDivider[]{new DiffDivider(FragmentSide.SIDE2),
                                                             new DiffDivider(FragmentSide.SIDE1)};

  private final LabeledComponent[] myEditorsPanels = new LabeledComponent[EDITORS_COUNT];
  private final EditorPlace.EditorListener myPlaceListener = new EditorPlace.EditorListener() {
    public void onEditorCreated(EditorPlace place) {
      if (myDuringCreation) return;
      disposeMergeList();
      myDuringCreation = true;
      try {
        tryInitView();
      }
      finally {
        myDuringCreation = false;
      }
    }

    public void onEditorReleased(Editor releasedEditor) {
      LOG.assertTrue(!myDuringCreation);
      disposeMergeList();
    }
  };
  public static final int EDITORS_COUNT = 3;
  private static final DiffRequest.ToolbarAddons TOOLBAR = new DiffRequest.ToolbarAddons() {
    public void customize(DiffToolbar toolbar) {
      ActionManager actionManager = ActionManager.getInstance();
      toolbar.addAction(actionManager.getAction(IdeActions.ACTION_COPY));
      toolbar.addAction(actionManager.getAction(IdeActions.ACTION_FIND));
      toolbar.addAction(PreviousDiffAction.find());
      toolbar.addAction(NextDiffAction.find());
      toolbar.addSeparator();
      toolbar.addAction(new OpenPartialDiffAction(1, 0, IconLoader.getIcon("/diff/leftDiff.png")));
      toolbar.addAction(new OpenPartialDiffAction(1, 2, IconLoader.getIcon("/diff/rightDiff.png")));
      toolbar.addAction(new OpenPartialDiffAction(0, 2, IconLoader.getIcon("/diff/branchDiff.png")));
      toolbar.addSeparator();
      toolbar.addAction(new ApplyNonConflicts());
    }
  };
  private final DividersRepainter myDividersRepainter = new DividersRepainter();
  private StatusUpdater myStatusUpdater;
  private final DialogBuilder myBuilder;

  public MergePanel2(DialogBuilder builder) {
    ArrayList<EditorPlace> editorPlaces = new ArrayList<EditorPlace>();
    for (int i = 0; i < EDITORS_COUNT; i++) {
      EditorPlace editorPlace = new EditorPlace(new DiffEditorState(i));
      editorPlaces.add(editorPlace);
      editorPlace.addListener(myPlaceListener);
      myEditorsPanels[i] = new LabeledComponent();
      myEditorsPanels[i].setLabelLocation(BorderLayout.NORTH);
      myEditorsPanels[i].setComponent(editorPlace);
    }
    FontSizeSynchronizer.attachTo(editorPlaces);
    myPanel = new DiffPanelOutterComponent(TextDiffType.MERGE_TYPES, TOOLBAR);
    myPanel.insertDiffComponent(new ThreePanels(myEditorsPanels, myDividers), new MyScrollingPanel());
    myPanel.setDataProvider(new MyDataProvider());
    myBuilder = builder;
  }

  public Editor getEditor(int index) {
    return getEditorPlace(index).getEditor();
  }

  public FileType getContentType() {
    return myData == null ? StdFileTypes.PLAIN_TEXT : getContentType(myData);
  }

  public String getVersionTitle(int index) {
    return myEditorsPanels[index].getRawText();
  }

  public EditorPlace getEditorPlace(int index) {
    return (EditorPlace)myEditorsPanels[index].getComponent();
  }

  private void createMergeList() {
    if (myData == null) return;
    DiffContent[] contents = myData.getContents();
    for (int i = 0; i < EDITORS_COUNT; i++) {
      getEditorPlace(i).setDocument(contents[i].getDocument());
    }
    tryInitView();
  }

  private void tryInitView() {
    if (!hasAllEditors()) return;
    if (myMergeList != null) return;
    myMergeList = MergeList.create(myData);
    myMergeList.addListener(myDividersRepainter);
    myStatusUpdater = StatusUpdater.install(myMergeList, myPanel);
    Editor left = getEditor(0);
    Editor base = getEditor(1);
    Editor right = getEditor(2);
    myMergeList.setMarkups(left, base, right);
    EditingSides[] sides = new EditingSides[]{new MyEditingSides(FragmentSide.SIDE1),
                                              new MyEditingSides(FragmentSide.SIDE2)};
    myScrollSupport.install(sides);
    for (int i = 0; i < myDividers.length; i++) {
      myDividers[i].listenEditors(sides[i]);
    }
    myPanel.requestScrollEditors();
  }

  private void disposeMergeList() {
    if (myMergeList == null) return;
    if (myStatusUpdater != null) {
      myStatusUpdater.dispose(myMergeList);
      myStatusUpdater = null;
    }
    myMergeList.removeListener(myDividersRepainter);

    myMergeList = null;
    for (int i = 0; i < myDividers.length; i++) {
      myDividers[i].stopListenEditors();
    }
  }

  public void setDiffRequest(DiffRequest data) {
    setTitle(data.getWindowTitle());
    disposeMergeList();
    for (int i = 0; i < EDITORS_COUNT; i++) {
      getEditorPlace(i).setDocument(null);
    }
    LOG.assertTrue(!myDuringCreation);
    myDuringCreation = true;
    try {
      myData = data;
      String[] titles = myData.getContentTitles();
      for (int i = 0; i < myEditorsPanels.length; i++) {
        LabeledComponent editorsPanel = myEditorsPanels[i];
        editorsPanel.getLabel().setText(titles[i]);
      }
      createMergeList();
      data.customizeToolbar(myPanel.resetToolbar());
      myPanel.registerToolbarActions();
      if ( data instanceof MergeRequestImpl && myBuilder != null){
        ((MergeRequestImpl)data).setActions(myBuilder, this, false);
      }
    }
    finally {
      myDuringCreation = false;
    }
  }

  private void setTitle(String windowTitle) {
    JDialog parent = getDialogWrapperParent();
    if (parent == null) return;
    parent.setTitle(windowTitle);
  }

  private JDialog getDialogWrapperParent() {
    Component panel = myPanel;
    while (panel != null){
      if (panel instanceof JDialog) return (JDialog)panel;
      panel = panel.getParent();
    }
    return null;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return getEditorPlace(1).getContentComponent();
  }

  private boolean hasAllEditors() {
    for (int i = 0; i < EDITORS_COUNT; i++) {
      if (getEditor(i) == null) return false;
    }
    return true;
  }

  public DialogBuilder getBuilder() {
    return myBuilder;
  }

  public MergeRequestImpl getMergeRequest() {
    return (MergeRequestImpl)(myData instanceof MergeRequestImpl ? myData : null);
  }

  private class MyEditingSides implements EditingSides {
    private final FragmentSide mySide;

    public MyEditingSides(FragmentSide side) {
      mySide = side;
    }

    public Editor getEditor(FragmentSide side) {
      return MergePanel2.this.getEditor(mySide.getIndex() + side.getIndex());
    }

    public LineBlocks getLineBlocks() {
      return myMergeList.getChanges(mySide).getLineBlocks();
    }
  }

  private class MyScrollingPanel implements DiffPanelOutterComponent.ScrollingPanel {
    public void scrollEditors() {
      Editor centerEditor = getEditor(1);
      JComponent centerComponent = centerEditor.getContentComponent();
      if (centerComponent.isShowing()) centerComponent.requestFocus();
      int[] toLeft = getPrimaryBegginings(myDividers[0].getPaint());
      int[] toRight = getPrimaryBegginings(myDividers[1].getPaint());
      int line;
      if (toLeft.length > 0 && toRight.length > 0) {
        line = Math.min(toLeft[0], toRight[0]);
      }
      else if (toLeft.length > 0) {
        line = toLeft[0];
      }
      else if (toRight.length > 0) {
        line = toRight[0];
      }
      else {
        return;
      }
      SyncScrollSupport.scrollEditor(centerEditor, line);
    }

    private int[] getPrimaryBegginings(DiffDividerPaint paint) {
      FragmentSide primarySide = paint.getLeftSide();
      LOG.assertTrue(getEditor(1) == paint.getSides().getEditor(primarySide));
      return paint.getSides().getLineBlocks().getBegginings(primarySide);
    }
  }

  private class DiffEditorState extends EditorPlace.ComponentState {
    private final HashMap<EditorPlace.ViewProperty, Object> myProperties = new HashMap<EditorPlace.ViewProperty, Object>();
    private final int myIndex;

    public DiffEditorState(int index) {
      myIndex = index;
    }

    public Editor createEditor() {
      Document document = getDocument();
      if (document == null) return null;
      Project project = myData.getProject();
      EditorEx editor;
      editor = DiffUtil.createEditor(document, project, myIndex != 1);

      if (editor == null) return editor;
      //FileType type = getFileType();
      //editor.setHighlighter(HighlighterFactory.createHighlighter(project, type));
      if (myIndex == 0) editor.setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT);
      if (myIndex != 1) ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeVisible(true);
      editor.getSettings().setFoldingOutlineShown(false);
      ((FoldingModelEx)editor.getFoldingModel()).setFoldingEnabled(false);
      HashSet<EditorPlace.ViewProperty> notProcessedDefaults = new HashSet<EditorPlace.ViewProperty>(ALL_PROPERTIES);
      for (Iterator<EditorPlace.ViewProperty> iterator = myProperties.keySet().iterator(); iterator.hasNext();) {
        EditorPlace.ViewProperty viewProperty = iterator.next();
        notProcessedDefaults.remove(viewProperty);
        viewProperty.updateEditor(editor, myProperties.get(viewProperty), this);
      }
      for (Iterator<EditorPlace.ViewProperty> iterator = notProcessedDefaults.iterator(); iterator.hasNext();) {
        EditorPlace.ViewProperty viewProperty = iterator.next();
        viewProperty.updateEditor(editor, null, this);
      }
      return editor;
    }

    public <T> void updateValue(Editor editor, EditorPlace.ViewProperty<T> property, T value) {
      myProperties.put(property, value);
      property.updateEditor(editor, value, this);
    }

    public FileType getFileType() {
      return getContentType();
    }

    public Project getProject() {
      return myData == null ? null : myData.getProject();
    }
  }

  private static FileType getContentType(DiffRequest diffData) {
    FileType contentType = diffData.getContents()[1].getContentType();
    if (contentType == null) contentType = StdFileTypes.PLAIN_TEXT;
    return contentType;
  }

  private class MyDataProvider implements DataProvider {
    public Object getData(String dataId) {
      if (FocusDiffSide.FOCUSED_DIFF_SIDE.equals(dataId)) {
        int index = getFocusedEditorIndex();
        if (index < 0) return null;
        switch (index) {
          case 0:
            return new BranchFocusedSide(FragmentSide.SIDE1);
          case 1:
            return new MergeFocusedSide();
          case 2:
            return new BranchFocusedSide(FragmentSide.SIDE2);
        }
      }
      else if (DataConstants.DIFF_VIEWER.equals(dataId)) return MergePanel2.this;
      return null;
    }

    private int getFocusedEditorIndex() {
      for (int i = 0; i < EDITORS_COUNT; i++) {
        Editor editor = getEditor(i);
        if (editor == null) continue;
        if (editor.getContentComponent().isFocusOwner()) return i;
      }
      return -1;
    }
  }

  private class BranchFocusedSide implements FocusDiffSide {
    private final FragmentSide mySide;

    public BranchFocusedSide(FragmentSide side) {
      mySide = side;
    }

    public Editor getEditor() {
      return MergePanel2.this.getEditor(mySide.getMergeIndex());
    }

    public int[] getFragmentStartingLines() {
      return myMergeList.getChanges(mySide).getLineBlocks().getBegginings(MergeList.BRANCH_SIDE);
    }
  }

  private class MergeFocusedSide implements FocusDiffSide {
    public Editor getEditor() {
      return MergePanel2.this.getEditor(1);
    }

    public int[] getFragmentStartingLines() {
      TIntHashSet beginnings = new TIntHashSet();
      for (int i = 0; i < 2; i++) {
        FragmentSide branchSide = FragmentSide.fromIndex(i);
        beginnings.addAll(myMergeList.getChanges(branchSide).getLineBlocks().getBegginings(MergeList.BASE_SIDE));
      }
      int[] result = beginnings.toArray();
      Arrays.sort(result);
      return result;
    }
  }

  public static MergePanel2 fromDataContext(DataContext dataContext) {
    DiffViewer diffComponent = (DiffViewer)dataContext.getData(DataConstants.DIFF_VIEWER);
    return diffComponent instanceof MergePanel2 ? (MergePanel2)diffComponent : null;
  }

  public MergeList getMergeList() {
    return myMergeList;
  }

  public <T> void setEditorProperty(EditorPlace.ViewProperty<T> property, T value) {
    for (int i = 0; i < EDITORS_COUNT; i++) {
      EditorPlace editorPlace = getEditorPlace(i);
      editorPlace.getState().updateValue(editorPlace.getEditor(), property, value);
    }
  }

  public void setColorScheme(EditorColorsScheme scheme) {
    setEditorProperty(MergePanel2.EDITOR_SCHEME, scheme);
    myPanel.setColorScheme(scheme);
  }

  private class DividersRepainter implements ChangeList.Listener {
    public void onChangeRemoved(ChangeList source) {
      FragmentSide side = myMergeList.getSideOf(source);
      myDividers[side.getIndex()].repaint();
    }
  }

  private static class StatusUpdater implements ChangeCounter.Listener {
    private final DiffPanelOutterComponent myPanel;

    private StatusUpdater(DiffPanelOutterComponent panel) {
      myPanel = panel;
    }

    public void onCountersChanged(ChangeCounter counter) {
      int changes = counter.getChangeCounter();
      int conflicts = counter.getConflictCounter();
      String text;
      if (changes == 0 && conflicts == 0) {
        text = "All conflicts resolved";
      }
      else {
        text = stringRepresentation(changes, "change", "changes") + ". " +
               stringRepresentation(conflicts, "conflict", "conflicts");
      }
      myPanel.setStatusBarText(text);
    }

    private String stringRepresentation(int number, String singular, String plural) {
      switch (number) {
        case 0:
          return "No " + plural;
        case 1:
          return "One " + singular;
        default:
          return number + " " + plural;
      }
    }

    public void dispose(MergeList mergeList) {
      LOG.assertTrue(mergeList != null);
      ChangeCounter.getOrCreate(mergeList).removeListener(this);
    }

    public static StatusUpdater install(MergeList mergeList, DiffPanelOutterComponent panel) {
      ChangeCounter counters = ChangeCounter.getOrCreate(mergeList);
      StatusUpdater updater = new StatusUpdater(panel);
      counters.addListener(updater);
      updater.onCountersChanged(counters);
      return updater;
    }
  }

  public static class AsComponent extends JPanel {
    private final MergePanel2 myMergePanel = new MergePanel2(null);

    public AsComponent() {
      super(new BorderLayout());
      add(myMergePanel.getComponent(), BorderLayout.CENTER);
    }

    public MergePanel2 getMergePanel() {
      return myMergePanel;
    }

    public boolean isToolbarEnabled() {
      return myMergePanel.myPanel.isToolbarEnabled();
    }

    public void setToolbarEnabled(boolean enabled) {
      myMergePanel.myPanel.disableToolbar(!enabled);
    }
  }
}
